package com.skillloop.service;

import com.skillloop.dao.AdminDAO;
import com.skillloop.dao.CreditTransactionDAO;
import com.skillloop.dao.SessionDAO;
import com.skillloop.dao.SkillDAO;
import com.skillloop.dao.StudentDAO;
import com.skillloop.exception.AuthorizationException;
import com.skillloop.exception.NotFoundException;
import com.skillloop.exception.ValidationException;
import com.skillloop.model.AccountStatus;
import com.skillloop.model.AdminAuditEvent;
import com.skillloop.model.AdminStats;
import com.skillloop.model.CreditTransaction;
import com.skillloop.model.LeaderboardEntry;
import com.skillloop.model.LearningSession;
import com.skillloop.model.MonthlyVolume;
import com.skillloop.model.NotificationType;
import com.skillloop.model.SessionStatus;
import com.skillloop.model.Skill;
import com.skillloop.model.Student;
import com.skillloop.model.StudentSkill;
import com.skillloop.util.CreditsPolicy;
import com.skillloop.util.DBConnection;
import com.skillloop.util.SessionContext;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Administrator operations.
 *
 * <p><b>Authorisation is enforced here, in the service layer</b> - hiding a button in the UI is
 * only cosmetic. Every privileged call builds an audit event so the administrator console can
 * show who did what.</p>
 */
public class AdminService {

    /** How an administrator may resolve a disputed session. */
    public enum DisputeResolution {
        /** The teaching took place: pay the teacher (same as a normal completion). */
        PAY_TEACHER,
        /** The teaching did not take place: give the learner's credits back. */
        REFUND_LEARNER,
        /** No payment at all: the reservation is simply released. */
        CANCEL
    }

    private final AdminDAO adminDAO = new AdminDAO();
    private final StudentDAO studentDAO = new StudentDAO();
    private final SkillDAO skillDAO = new SkillDAO();
    private final SessionDAO sessionDAO = new SessionDAO();
    private final CreditService creditService = new CreditService();
    private final NotificationService notificationService = new NotificationService();

    /** Throws unless the given student is an administrator with an active account. */
    private Student requireAdmin(long adminId) {
        Student admin = studentDAO.findById(adminId)
                .orElseThrow(() -> new NotFoundException("Administrator #" + adminId + " does not exist."));
        if (!admin.isAdmin()) {
            throw new AuthorizationException("Administrator permissions are required for this action.");
        }
        if (!admin.isActive()) {
            throw new AuthorizationException("Your administrator account is not active.");
        }
        return admin;
    }

    private void audit(Connection connection, long adminId, String action, String targetType, Long targetId,
                       String details) throws SQLException {
        AdminAuditEvent event = new AdminAuditEvent();
        event.setAdminId(adminId);
        event.setAction(action);
        event.setTargetType(targetType);
        event.setTargetId(targetId);
        event.setDetails(details);
        adminDAO.logAudit(connection, event);
    }

    // ------------------------------------------------------------------ dashboard

    public AdminStats stats(long adminId) {
        requireAdmin(adminId);
        return adminDAO.loadStats();
    }

    public List<Skill> popularSkills(long adminId, int limit) {
        requireAdmin(adminId);
        return adminDAO.popularSkills(limit);
    }

    public List<LeaderboardEntry> mostActiveTeachers(long adminId, int limit) {
        requireAdmin(adminId);
        return adminDAO.topTeachers(limit);
    }

    public List<CreditTransaction> recentTransactions(long adminId, int limit) {
        requireAdmin(adminId);
        return adminDAO.recentTransactions(limit);
    }

    public List<MonthlyVolume> monthlyVolume(long adminId, int limit) {
        requireAdmin(adminId);
        return adminDAO.monthlyVolume(limit);
    }

    public List<String> departmentReport(long adminId) {
        requireAdmin(adminId);
        return adminDAO.departmentReport();
    }

    public List<AdminAuditEvent> auditLog(long adminId, int limit) {
        requireAdmin(adminId);
        return adminDAO.auditLog(limit);
    }

    // ------------------------------------------------------------------ students

    public List<Student> listStudents(long adminId, String term, AccountStatus status, int limit) {
        requireAdmin(adminId);
        return adminDAO.listStudents(term, status, limit);
    }

    /** Suspends or reactivates an account (audited). */
    public void setAccountStatus(long adminId, long studentId, AccountStatus status, String reason) {
        Student admin = requireAdmin(adminId);
        Student target = studentDAO.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student #" + studentId + " does not exist."));
        if (target.getStudentId() == adminId && status != AccountStatus.ACTIVE) {
            throw new ValidationException("You cannot suspend your own account.");
        }
        studentDAO.updateAccountStatus(studentId, status);
        adminDAO.logAudit(auditEvent(adminId, "SET_ACCOUNT_STATUS", "STUDENT", studentId,
                status.name() + (reason == null || reason.isBlank() ? "" : ": " + reason)));
        notificationService.notify(studentId, "Account status changed",
                "Your account is now " + status.name().toLowerCase() + " (" + admin.getFullName() + ").",
                NotificationType.ADMIN_MESSAGE);
    }

    /** Promotes a student to administrator (audited). */
    public void promote(long adminId, long studentId) {
        Student admin = requireAdmin(adminId);
        studentDAO.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student #" + studentId + " does not exist."));
        studentDAO.updateRole(studentId, com.skillloop.model.Role.ADMIN);
        adminDAO.logAudit(auditEvent(adminId, "PROMOTE_ADMIN", "STUDENT", studentId, "promoted by " + admin.getFullName()));
        notificationService.notify(studentId, "Role updated",
                "You were promoted to administrator by " + admin.getFullName() + ".",
                NotificationType.ADMIN_MESSAGE);
    }

    // ------------------------------------------------------------------ listings

    public List<StudentSkill> listListings(long adminId, String term, int limit) {
        requireAdmin(adminId);
        return adminDAO.listListings(term, limit);
    }

    /** Hides (or re-enables) a skill listing. */
    public void moderateListing(long adminId, long studentSkillId, boolean active, String reason) {
        requireAdmin(adminId);
        skillDAO.setListingActive(studentSkillId, active);
        adminDAO.logAudit(auditEvent(adminId, active ? "ENABLE_LISTING" : "DISABLE_LISTING",
                "STUDENT_SKILL", studentSkillId, reason));
    }

    // ------------------------------------------------------------------ disputes

    public List<LearningSession> disputes(long adminId) {
        requireAdmin(adminId);
        return adminDAO.listDisputes();
    }

    /**
     * Resolves a disputed session.
     *
     * <ul>
     *   <li>{@link DisputeResolution#PAY_TEACHER} - both confirmations are set and the normal
     *       settlement runs (release hold, SPEND, EARN).</li>
     *   <li>{@link DisputeResolution#REFUND_LEARNER} - the reservation is released, or the
     *       payment is reversed with a REFUND entry when it had already been made.</li>
     *   <li>{@link DisputeResolution#CANCEL} - the reservation is released, no payment.</li>
     * </ul>
     */
    public LearningSession resolveDispute(long adminId, long sessionId, DisputeResolution resolution, String note) {
        Student admin = requireAdmin(adminId);
        if (resolution == null) {
            throw new ValidationException("Please choose how the dispute should be resolved.");
        }
        String cleanNote = note == null || note.isBlank() ? "No note provided" : note.trim();

        if (resolution == DisputeResolution.PAY_TEACHER) {
            // Mark both sides as confirmed, then run the ordinary, idempotent settlement.
            try (Connection connection = DBConnection.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    LearningSession session = sessionDAO.findByIdForUpdate(connection, sessionId)
                            .orElseThrow(() -> new NotFoundException("Session #" + sessionId + " does not exist."));
                    session.setTeacherConfirmation(true);
                    session.setLearnerConfirmation(true);
                    session.setStatus(SessionStatus.ACCEPTED);
                    session.setResolvedBy(adminId);
                    session.setResolutionNote("Resolved by " + admin.getFullName() + ": " + cleanNote);
                    sessionDAO.update(connection, session);
                    audit(connection, adminId, "RESOLVE_DISPUTE", "SESSION", sessionId,
                            "PAY_TEACHER: " + cleanNote);
                    connection.commit();
                } catch (SQLException | RuntimeException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Dispute resolution failed: " + e.getMessage(), e);
            }
            creditService.settleSession(sessionId);
            LearningSession settled = sessionDAO.findById(sessionId)
                    .orElseThrow(() -> new NotFoundException("Session #" + sessionId + " does not exist."));
            notifyResolution(settled, admin, "resolved in favour of the teacher", cleanNote);
            return settled;
        }

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                LearningSession session = sessionDAO.findByIdForUpdate(connection, sessionId)
                        .orElseThrow(() -> new NotFoundException("Session #" + sessionId + " does not exist."));
                if (resolution == DisputeResolution.REFUND_LEARNER) {
                    creditService.refundSession(connection, sessionId, session.getLearnerId(),
                            session.getCreditCost(), adminId, cleanNote);
                } else {
                    creditService.releaseHold(connection, session.getLearnerId(), sessionId,
                            session.getCreditCost());
                }
                session.setStatus(SessionStatus.CANCELLED);
                session.setResolvedBy(adminId);
                session.setResolutionNote("Resolved by " + admin.getFullName() + ": " + cleanNote);
                sessionDAO.update(connection, session);
                new com.skillloop.dao.AvailabilityDAO().releaseSlot(connection, session.getSlotId());
                audit(connection, adminId, "RESOLVE_DISPUTE", "SESSION", sessionId,
                        resolution.name() + ": " + cleanNote);
                connection.commit();
                notifyResolution(session, admin, "resolved in favour of the learner", cleanNote);
                return session;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Dispute resolution failed: " + e.getMessage(), e);
        }
    }

    private void notifyResolution(LearningSession session, Student admin, String outcome, String note) {
        notificationService.notify(session.getLearnerId(), "Dispute resolved",
                "Session #" + session.getSessionId() + " was " + outcome + " by "
                        + admin.getFullName() + ". " + note, NotificationType.DISPUTE_RESOLVED);
        notificationService.notify(session.getTeacherId(), "Dispute resolved",
                "Session #" + session.getSessionId() + " was " + outcome + " by "
                        + admin.getFullName() + ". " + note, NotificationType.DISPUTE_RESOLVED);
    }

    // ------------------------------------------------------------------ credits

    /** Manual credit correction with hard limits and a full audit trail. */
    public void adjustCredits(long adminId, long studentId, int amount, String reason) {
        Student admin = requireAdmin(adminId);
        if (Math.abs(amount) > CreditsPolicy.maxAdminAdjustment()) {
            throw new ValidationException("A single adjustment may not exceed "
                    + CreditsPolicy.maxAdminAdjustment() + " credits.");
        }
        creditService.adminAdjustment(studentId, amount, reason, adminId);
        adminDAO.logAudit(auditEvent(adminId, "ADJUST_CREDITS", "STUDENT", studentId,
                amount + " credits: " + reason));
        notificationService.notify(studentId, "Credit adjustment",
                admin.getFullName() + " adjusted your wallet by " + amount + " credits: " + reason,
                NotificationType.CREDIT_TRANSFER);
    }

    /** Cancels any session (for example an abandoned pending request). */
    public void cancelSession(long adminId, long sessionId, String reason) {
        Student admin = requireAdmin(adminId);
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                LearningSession session = sessionDAO.findByIdForUpdate(connection, sessionId)
                        .orElseThrow(() -> new NotFoundException("Session #" + sessionId + " does not exist."));
                if (session.getStatus() == SessionStatus.COMPLETED) {
                    throw new ValidationException("Completed sessions cannot be cancelled.");
                }
                creditService.releaseHold(connection, session.getLearnerId(), sessionId, session.getCreditCost());
                session.setStatus(SessionStatus.CANCELLED);
                session.setResolvedBy(adminId);
                session.setResolutionNote("Cancelled by " + admin.getFullName() + ": " + reason);
                sessionDAO.update(connection, session);
                new com.skillloop.dao.AvailabilityDAO().releaseSlot(connection, session.getSlotId());
                audit(connection, adminId, "CANCEL_SESSION", "SESSION", sessionId, reason);
                connection.commit();
                notificationService.notify(session.getLearnerId(), "Session cancelled",
                        "An administrator cancelled session #" + sessionId + ".",
                        NotificationType.SESSION_CANCELLED);
                notificationService.notify(session.getTeacherId(), "Session cancelled",
                        "An administrator cancelled session #" + sessionId + ".",
                        NotificationType.SESSION_CANCELLED);
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not cancel the session: " + e.getMessage(), e);
        }
    }

    private static AdminAuditEvent auditEvent(long adminId, String action, String targetType, Long targetId,
                                              String details) {
        AdminAuditEvent event = new AdminAuditEvent();
        event.setAdminId(adminId);
        event.setAction(action);
        event.setTargetType(targetType);
        event.setTargetId(targetId);
        event.setDetails(details);
        return event;
    }

    /** Convenience for the UI: is the signed-in user allowed to open the admin console? */
    public boolean canOpenConsole() {
        return SessionContext.isAdmin();
    }

    /** Last audit entries (shortcut used by the console overview tab). */
    public List<AdminAuditEvent> recentAuditEvents(long adminId, int limit) {
        return auditLog(adminId, limit);
    }

    /** Total number of ledger entries (used by the monitoring tab). */
    public long transactionCount(long adminId) {
        requireAdmin(adminId);
        return new CreditTransactionDAO().listRecent(Integer.MAX_VALUE).size();
    }

    /** Current timestamp helper for reports. */
    public LocalDateTime now() {
        return LocalDateTime.now();
    }
}
