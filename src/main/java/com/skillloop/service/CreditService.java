package com.skillloop.service;

import com.skillloop.dao.CreditTransactionDAO;
import com.skillloop.dao.StudentDAO;
import com.skillloop.dao.WalletDAO;
import com.skillloop.exception.AuthorizationException;
import com.skillloop.exception.BookingException;
import com.skillloop.exception.InsufficientCreditsException;
import com.skillloop.exception.NotFoundException;
import com.skillloop.exception.ValidationException;
import com.skillloop.model.CreditTransaction;
import com.skillloop.model.Student;
import com.skillloop.model.TransactionType;
import com.skillloop.model.Wallet;
import com.skillloop.util.CreditsPolicy;
import com.skillloop.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * The credit wallet engine - the heart of SkillLoop.
 *
 * <h2>Credit policy</h2>
 * <ul>
 *   <li>A new student starts at 0 credits and receives a <b>one-time starter grant</b>
 *       (20 credits by default). The grant is stored in the ledger with the reference
 *       {@code STARTER:<studentId>}, so it can never be claimed twice.</li>
 *   <li>When a learner requests a session, the cost is <b>reserved</b>: it moves from
 *       {@code balance} to {@code held_credits} (ledger type HOLD).</li>
 *   <li>When both participants confirm completion the reservation is released and the
 *       learner is charged (SPEND) while the teacher is rewarded (EARN) - inside one
 *       ACID transaction.</li>
 *   <li>Rejection or cancellation gives the reservation back (RELEASE).</li>
 *   <li>Balances can never become negative: the service checks the funds and the UPDATE
 *       statement itself contains a {@code balance + ? >= 0} guard, backed by a CHECK
 *       constraint in the schema.</li>
 * </ul>
 */
public class CreditService {

    private final WalletDAO walletDAO = new WalletDAO();
    private final CreditTransactionDAO transactionDAO = new CreditTransactionDAO();
    private final StudentDAO studentDAO = new StudentDAO();
    private final NotificationService notificationService = new NotificationService();

    // ------------------------------------------------------------------ queries

    public Optional<Wallet> getWallet(long studentId) {
        return walletDAO.loadSummary(studentId);
    }

    /** Spendable credits of a student (held credits are not spendable). */
    public int availableBalance(long studentId) {
        return walletDAO.loadSummary(studentId).map(Wallet::getBalance).orElse(0);
    }

    public List<CreditTransaction> history(long studentId, int limit) {
        return transactionDAO.listForStudent(studentId, limit);
    }

    public List<CreditTransaction> historyForSession(long sessionId) {
        try (Connection connection = DBConnection.getConnection()) {
            return transactionDAO.listForSession(connection, sessionId);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load the transaction history: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ starter grant

    /**
     * Grants the one-time starter credit. Idempotent: the unique ledger reference makes a
     * second call a no-op instead of creating free credits.
     *
     * @return true when the grant was applied, false when the student already received it
     */
    public boolean grantStarterGrant(Connection connection, long studentId) throws SQLException {
        int amount = CreditsPolicy.starterGrantAmount();
        String reference = CreditsPolicy.starterReference(studentId);
        if (amount <= 0 || transactionDAO.existsReference(connection, reference)) {
            return false;
        }
        Wallet wallet = walletDAO.lockForUpdate(connection, studentId);
        int newBalance = wallet.getBalance() + amount;
        walletDAO.applyDelta(connection, studentId, amount, 0);
        transactionDAO.insert(connection, entry(studentId, null, amount, 0, newBalance, wallet.getHeldCredits(),
                TransactionType.STARTER_GRANT, reference,
                "One-time starter credit for a new student", null));
        return true;
    }

    /** Convenience wrapper that runs in its own transaction (used by the wallet screen). */
    public boolean grantStarterGrant(long studentId) {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean granted = grantStarterGrant(connection, studentId);
                connection.commit();
                return granted;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not grant the starter credit: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ booking holds

    /**
     * Reserves the cost of a booking (balance -> held). Must run inside the booking
     * transaction; throws {@link InsufficientCreditsException} when the learner cannot pay.
     */
    public void placeHold(Connection connection, long learnerId, long sessionId, int cost) throws SQLException {
        Wallet wallet = walletDAO.lockForUpdate(connection, learnerId);
        if (wallet.getBalance() < cost) {
            throw new InsufficientCreditsException(cost, wallet.getBalance());
        }
        if (transactionDAO.existsReference(connection, CreditsPolicy.holdReference(sessionId))) {
            return; // idempotent: the reservation for this session already exists
        }
        walletDAO.applyDelta(connection, learnerId, -cost, cost);
        transactionDAO.insert(connection, entry(learnerId, sessionId, -cost, cost,
                wallet.getBalance() - cost, wallet.getHeldCredits() + cost,
                TransactionType.HOLD, CreditsPolicy.holdReference(sessionId),
                "Credits reserved for session #" + sessionId, null));
    }

    /**
     * Gives a reservation back (held -> balance) after a rejection, a cancellation or an
     * administrator refund. Idempotent: a second call does nothing.
     */
    public boolean releaseHold(Connection connection, long learnerId, long sessionId, int cost) throws SQLException {
        String reference = CreditsPolicy.releaseReference(sessionId);
        if (transactionDAO.existsReference(connection, reference)) {
            return false;
        }
        Wallet wallet = walletDAO.lockForUpdate(connection, learnerId);
        int releaseAmount = Math.min(cost, wallet.getHeldCredits());
        if (releaseAmount <= 0) {
            return false;
        }
        walletDAO.applyDelta(connection, learnerId, releaseAmount, -releaseAmount);
        transactionDAO.insert(connection, entry(learnerId, sessionId, releaseAmount, -releaseAmount,
                wallet.getBalance() + releaseAmount, wallet.getHeldCredits() - releaseAmount,
                TransactionType.RELEASE, reference,
                "Reservation released for session #" + sessionId, null));
        return true;
    }

    // ------------------------------------------------------------------ settlement

    /**
     * Transfers the credits of a completed session from the learner to the teacher.
     *
     * <p>The whole operation runs in one database transaction and follows these steps:</p>
     * <ol>
     *   <li>BEGIN (auto-commit switched off) and lock the session row (SELECT ... FOR UPDATE),
     *       which serialises concurrent settlement attempts.</li>
     *   <li>Verify that the session exists, is ACCEPTED and that both participants confirmed.</li>
     *   <li>Verify that both students exist and are ACTIVE.</li>
     *   <li>Check whether the SPEND/EARN entries already exist (idempotency guard).</li>
     *   <li>Lock both wallets in a deterministic order (lowest id first - avoids deadlocks).</li>
     *   <li>Validate that the learner's reservation covers the cost.</li>
     *   <li>Release the reservation, debit the learner (SPEND) and credit the teacher (EARN).</li>
     *   <li>Write both ledger rows with unique references.</li>
     *   <li>Mark the session COMPLETED and stamp {@code settled_at}.</li>
     *   <li>Evaluate achievements for both participants.</li>
     *   <li>Notify both students.</li>
     *   <li>COMMIT - any failure rolls everything back.</li>
     * </ol>
     *
     * @return true when the transfer was executed, false when it had already been done
     */
    public boolean settleSession(long sessionId) {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean transferred = settleSession(connection, sessionId);
                connection.commit();
                return transferred;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Credit transfer failed and was rolled back: " + e.getMessage(), e);
        }
    }

    /** Transactional core of {@link #settleSession(long)} - usable inside a larger transaction. */
    public boolean settleSession(Connection connection, long sessionId) throws SQLException {
        // 1. lock the session row so two concurrent confirmations cannot both settle
        var sessionDAO = new com.skillloop.dao.SessionDAO();
        var session = sessionDAO.findByIdForUpdate(connection, sessionId)
                .orElseThrow(() -> new NotFoundException("Session #" + sessionId + " does not exist."));

        // 2. verify the session state
        if (session.getStatus() == com.skillloop.model.SessionStatus.COMPLETED) {
            return false; // nothing to do, already settled
        }
        if (session.getStatus() != com.skillloop.model.SessionStatus.ACCEPTED
                && session.getStatus() != com.skillloop.model.SessionStatus.DISPUTED) {
            throw new BookingException("Only accepted (or disputed) sessions can be settled.");
        }
        if (!session.isFullyConfirmed()) {
            throw new BookingException("Both the teacher and the learner must confirm completion first.");
        }

        // 3. verify both participants
        Student teacher = studentDAO.findById(connection, session.getTeacherId())
                .orElseThrow(() -> new NotFoundException("The teacher of this session no longer exists."));
        Student learner = studentDAO.findById(connection, session.getLearnerId())
                .orElseThrow(() -> new NotFoundException("The learner of this session no longer exists."));
        if (!teacher.isActive() || !learner.isActive()) {
            throw new BookingException("Both participants must have an active account.");
        }

        // 4. idempotency guard based on the unique ledger references
        String spendReference = CreditsPolicy.spendReference(sessionId);
        String earnReference = CreditsPolicy.earnReference(sessionId);
        if (transactionDAO.existsReference(connection, spendReference)
                || transactionDAO.existsReference(connection, earnReference)) {
            return false;
        }

        int cost = session.getCreditCost();

        // 5. lock both wallets in a deterministic order to avoid deadlocks
        long firstId = Math.min(teacher.getStudentId(), learner.getStudentId());
        long secondId = Math.max(teacher.getStudentId(), learner.getStudentId());
        walletDAO.lockForUpdate(connection, firstId);
        walletDAO.lockForUpdate(connection, secondId);

        Wallet learnerWallet = walletDAO.lockForUpdate(connection, learner.getStudentId());
        Wallet teacherWallet = walletDAO.lockForUpdate(connection, teacher.getStudentId());

        // 6. the reservation must cover the cost
        if (learnerWallet.getHeldCredits() < cost) {
            throw new InsufficientCreditsException(cost, learnerWallet.getBalance());
        }

        // 7. release the reservation, then debit the learner
        releaseHold(connection, learner.getStudentId(), sessionId, cost);
        learnerWallet = walletDAO.lockForUpdate(connection, learner.getStudentId());
        walletDAO.applyDelta(connection, learner.getStudentId(), -cost, 0);
        transactionDAO.insert(connection, entry(learner.getStudentId(), sessionId, -cost, 0,
                learnerWallet.getBalance() - cost, learnerWallet.getHeldCredits(),
                TransactionType.SPEND, spendReference,
                "Paid for session #" + sessionId + " (" + session.getSkillName() + ")", null));

        // 8. credit the teacher
        walletDAO.applyDelta(connection, teacher.getStudentId(), cost, 0);
        transactionDAO.insert(connection, entry(teacher.getStudentId(), sessionId, cost, 0,
                teacherWallet.getBalance() + cost, teacherWallet.getHeldCredits(),
                TransactionType.EARN, earnReference,
                "Earned by teaching session #" + sessionId + " (" + session.getSkillName() + ")", null));

        // 9. mark the session completed
        session.setStatus(com.skillloop.model.SessionStatus.COMPLETED);
        session.setSettledAt(LocalDateTime.now());
        sessionDAO.update(connection, session);

        // 10. award achievements for the verified contribution (idempotent)
        new AchievementService().evaluate(connection, teacher.getStudentId(), learner.getStudentId(), cost);

        // 11. notify both participants
        notificationService.notify(connection, learner.getStudentId(), "Credits transferred",
                cost + " credits were transferred to " + teacher.getFullName()
                        + " for session #" + sessionId + ".",
                com.skillloop.model.NotificationType.CREDIT_TRANSFER);
        notificationService.notify(connection, teacher.getStudentId(), "Credits received",
                "You received " + cost + " credits for teaching session #" + sessionId + ".",
                com.skillloop.model.NotificationType.CREDIT_TRANSFER);

        return true;
    }

    /**
     * Refunds a learner after an administrator decided a dispute in their favour: the
     * reservation is released (or the payment reversed when it had already been made).
     */
    public boolean refundSession(Connection connection, long sessionId, long learnerId, int cost,
                                 long adminId, String reason) throws SQLException {
        boolean released = releaseHold(connection, learnerId, sessionId, cost);
        if (!released && transactionDAO.existsReference(connection, CreditsPolicy.spendReference(sessionId))) {
            // The learner had already paid: reverse the payment with a REFUND entry.
            Wallet wallet = walletDAO.lockForUpdate(connection, learnerId);
            walletDAO.applyDelta(connection, learnerId, cost, 0);
            transactionDAO.insert(connection, entry(learnerId, sessionId, cost, 0,
                    wallet.getBalance() + cost, wallet.getHeldCredits(),
                    TransactionType.REFUND, CreditsPolicy.refundReference(sessionId, learnerId),
                    "Refund ordered by administrator: " + reason, adminId));
            released = true;
        }
        return released;
    }

    /** Administrator credit correction (positive or negative), fully audited. */
    public void adminAdjustment(long studentId, int amount, String reason, long adminId) {
        if (amount == 0) {
            throw new ValidationException("The adjustment amount must not be zero.");
        }
        if (Math.abs(amount) > CreditsPolicy.maxAdminAdjustment()) {
            throw new ValidationException("A single adjustment may not exceed "
                    + CreditsPolicy.maxAdminAdjustment() + " credits.");
        }
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("Please document the reason for the adjustment.");
        }
        String reference = "ADJ:" + studentId + ":" + System.currentTimeMillis();
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Wallet wallet = walletDAO.lockForUpdate(connection, studentId);
                if (amount < 0 && wallet.getBalance() + amount < 0) {
                    throw new InsufficientCreditsException(-amount, wallet.getBalance());
                }
                walletDAO.applyDelta(connection, studentId, amount, 0);
                transactionDAO.insert(connection, entry(studentId, null, amount, 0,
                        wallet.getBalance() + amount, wallet.getHeldCredits(),
                        TransactionType.ADMIN_ADJUSTMENT, reference,
                        "Administrator adjustment: " + reason, adminId));
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Credit adjustment failed: " + e.getMessage(), e);
        }
    }

    /** Rewards a teacher for a completed group session (one entry per learner). */
    public void rewardGroupTeaching(Connection connection, long teacherId, long groupId, long learnerId, int amount)
            throws SQLException {
        Wallet wallet = walletDAO.lockForUpdate(connection, teacherId);
        walletDAO.applyDelta(connection, teacherId, amount, 0);
        transactionDAO.insert(connection, entry(teacherId, null, amount, 0,
                wallet.getBalance() + amount, wallet.getHeldCredits(),
                TransactionType.GROUP_EARNING, CreditsPolicy.groupEarnReference(groupId, learnerId),
                "Group session #" + groupId + ": teaching reward for " + learnerId, null));
    }

    /** Charges a learner joining a group session. */
    public void chargeGroupLearner(Connection connection, long learnerId, long groupId, int amount) throws SQLException {
        Wallet wallet = walletDAO.lockForUpdate(connection, learnerId);
        if (wallet.getBalance() < amount) {
            throw new InsufficientCreditsException(amount, wallet.getBalance());
        }
        walletDAO.applyDelta(connection, learnerId, -amount, 0);
        transactionDAO.insert(connection, entry(learnerId, null, -amount, 0,
                wallet.getBalance() - amount, wallet.getHeldCredits(),
                TransactionType.SPEND, CreditsPolicy.groupSpendReference(groupId, learnerId),
                "Group session #" + groupId + " enrolment fee", null));
    }

    /** Guard used by the booking flow: throws when the learner cannot afford the session. */
    public void requireFunds(long learnerId, int cost) {
        int available = availableBalance(learnerId);
        if (available < cost) {
            throw new InsufficientCreditsException(cost, available);
        }
    }

    /** Only an administrator may change somebody else's credits directly. */
    public void requireAdministrator(long studentId) {
        Student student = studentDAO.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student #" + studentId + " does not exist."));
        if (!student.isAdmin()) {
            throw new AuthorizationException("Only an administrator may perform this credit operation.");
        }
    }

    private static CreditTransaction entry(long studentId, Long sessionId, int amount, int heldAmount,
                                           int balanceAfter, int heldAfter, TransactionType type,
                                           String reference, String description, Long createdBy) {
        CreditTransaction transaction = new CreditTransaction();
        transaction.setStudentId(studentId);
        transaction.setSessionId(sessionId);
        transaction.setAmount(amount);
        transaction.setHeldAmount(heldAmount);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setHeldAfter(heldAfter);
        transaction.setType(type);
        transaction.setReference(reference);
        transaction.setDescription(description);
        transaction.setCreatedBy(createdBy);
        return transaction;
    }
}
