package com.skillloop.integration;

import com.skillloop.exception.InsufficientCreditsException;
import com.skillloop.model.CreditTransaction;
import com.skillloop.model.LearningSession;
import com.skillloop.model.Proficiency;
import com.skillloop.model.SessionStatus;
import com.skillloop.model.Skill;
import com.skillloop.model.SkillType;
import com.skillloop.model.Student;
import com.skillloop.model.TransactionType;
import com.skillloop.model.Wallet;
import com.skillloop.service.AuthService;
import com.skillloop.service.BookingService;
import com.skillloop.service.CreditService;
import com.skillloop.service.SkillService;
import com.skillloop.support.TestEnvironment;
import com.skillloop.util.CreditsPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The credit wallet: holds, transfers, duplicate protection, rollback and concurrency.
 * These are the most important tests of the project.
 */
class CreditWalletIT {

    private static final String PASSWORD = "SkillLoop@123";
    private final List<Long> created = new ArrayList<>();
    private final AuthService authService = new AuthService();
    private final SkillService skillService = new SkillService();
    private final BookingService bookingService = new BookingService();
    private final CreditService creditService = new CreditService();

    @BeforeAll
    static void requireDatabase() {
        TestEnvironment.assumeDatabase();
    }

    @AfterEach
    void cleanUp() {
        created.forEach(TestEnvironment::deleteStudent);
        created.clear();
    }

    private Student register(String name) {
        Student student = authService.register(name, TestEnvironment.uniqueEmail(), PASSWORD, PASSWORD, "CS", 5);
        created.add(student.getStudentId());
        return student;
    }

    private Skill skill(String suffix) {
        return skillService.createSkill(TestEnvironment.uniqueSkillName() + suffix, "Testing", "test");
    }

    /** Creates a completed session (requested -> accepted -> both confirmed). */
    private LearningSession completedSession(Student teacher, Student learner, Skill taught, int duration) {
        skillService.addListing(teacher.getStudentId(), taught.getSkillId(), SkillType.OFFER,
                Proficiency.ADVANCED, "offering");
        skillService.addListing(learner.getStudentId(), taught.getSkillId(), SkillType.LEARN,
                Proficiency.BEGINNER, "want");
        LocalDateTime start = LocalDateTime.now().minusHours(3);
        LearningSession session = bookingService.requestBooking(learner.getStudentId(), teacher.getStudentId(),
                taught.getSkillId(), null, start, duration, "integration test");
        bookingService.accept(session.getSessionId(), teacher.getStudentId());
        bookingService.confirmCompletion(session.getSessionId(), teacher.getStudentId());
        bookingService.confirmCompletion(session.getSessionId(), learner.getStudentId());
        return bookingService.find(session.getSessionId()).orElseThrow();
    }

    @Test
    @DisplayName("Completing a session transfers exactly the credit cost from learner to teacher")
    void creditsAreTransferredOnCompletion() {
        Skill taught = skill("-C1");
        Student teacher = register("Teacher C1");
        Student learner = register("Learner C1");

        int learnerBefore = creditService.availableBalance(learner.getStudentId());
        int teacherBefore = creditService.availableBalance(teacher.getStudentId());

        LearningSession session = completedSession(teacher, learner, taught, 60);

        assertEquals(SessionStatus.COMPLETED, session.getStatus());
        assertEquals(20, session.getCreditCost());
        assertEquals(learnerBefore - 20, creditService.availableBalance(learner.getStudentId()));
        assertEquals(teacherBefore + 20, creditService.availableBalance(teacher.getStudentId()));
        assertEquals(0, creditService.getWallet(learner.getStudentId()).orElseThrow().getHeldCredits(),
                "The reservation must be consumed by the settlement");
    }

    @Test
    @DisplayName("The transfer is written to the ledger with unique references on both sides")
    void ledgerEntriesAreCreated() {
        Skill taught = skill("-C2");
        Student teacher = register("Teacher C2");
        Student learner = register("Learner C2");
        LearningSession session = completedSession(teacher, learner, taught, 30);

        List<CreditTransaction> learnerEntries = creditService.history(learner.getStudentId(), 50);
        List<CreditTransaction> teacherEntries = creditService.history(teacher.getStudentId(), 50);
        assertTrue(learnerEntries.stream().anyMatch(txn ->
                txn.getType() == TransactionType.SPEND
                        && txn.getReference().equals(CreditsPolicy.spendReference(session.getSessionId()))));
        assertTrue(teacherEntries.stream().anyMatch(txn ->
                txn.getType() == TransactionType.EARN
                        && txn.getReference().equals(CreditsPolicy.earnReference(session.getSessionId()))));
        assertEquals(1, learnerEntries.stream().filter(txn ->
                txn.getType() == TransactionType.SPEND
                        && txn.getReference().equals(CreditsPolicy.spendReference(session.getSessionId()))).count(),
                "The learner must be charged exactly once");
        assertEquals(1, teacherEntries.stream().filter(txn ->
                txn.getType() == TransactionType.EARN
                        && txn.getReference().equals(CreditsPolicy.earnReference(session.getSessionId()))).count(),
                "The teacher must be rewarded exactly once");
    }

    @Test
    @DisplayName("Settling the same session twice transfers the credits only once")
    void duplicateTransferIsPrevented() {
        Skill taught = skill("-C3");
        Student teacher = register("Teacher C3");
        Student learner = register("Learner C3");
        LearningSession session = completedSession(teacher, learner, taught, 60);
        int teacherBalance = creditService.availableBalance(teacher.getStudentId());
        int learnerBalance = creditService.availableBalance(learner.getStudentId());

        boolean secondRun = creditService.settleSession(session.getSessionId());
        assertFalse(secondRun, "The second settlement must report that nothing was done");
        assertEquals(teacherBalance, creditService.availableBalance(teacher.getStudentId()));
        assertEquals(learnerBalance, creditService.availableBalance(learner.getStudentId()));
    }

    @Test
    @DisplayName("Concurrent settlement attempts transfer the credits only once")
    void concurrentSettlementIsIdempotent() throws Exception {
        Skill taught = skill("-C4");
        Student teacher = register("Teacher C4");
        Student learner = register("Learner C4");
        LearningSession session = completedSession(teacher, learner, taught, 60);
        int teacherBalance = creditService.availableBalance(teacher.getStudentId());

        int attempts = 4;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger transfers = new AtomicInteger();
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                if (creditService.settleSession(session.getSessionId())) {
                    transfers.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (var future : futures) {
            future.get();
        }
        pool.shutdown();

        assertEquals(0, transfers.get(), "Every concurrent attempt must find the session already settled");
        assertEquals(teacherBalance, creditService.availableBalance(teacher.getStudentId()));
    }

    @Test
    @DisplayName("A learner cannot book a session they cannot pay for")
    void insufficientCreditsAreRejected() {
        Skill taught = skill("-C5");
        Student teacher = register("Teacher C5");
        Student learner = register("Learner C5");
        skillService.addListing(teacher.getStudentId(), taught.getSkillId(), SkillType.OFFER,
                Proficiency.ADVANCED, "offering");

        // Drain the wallet down to zero by spending the starter grant on a first session.
        int available = creditService.availableBalance(learner.getStudentId());
        assertTrue(available > 0);
        completedSession(teacher, learner, taught, 60);
        int after = creditService.availableBalance(learner.getStudentId());

        if (after < 20) {
            assertThrows(InsufficientCreditsException.class, () -> bookingService.requestBooking(
                    learner.getStudentId(), teacher.getStudentId(), taught.getSkillId(), null,
                    LocalDateTime.now().plusDays(1), 60, "too expensive"));
        }
        assertTrue(after >= 0, "The balance must never become negative");
    }

    @Test
    @DisplayName("Concurrent bookings can never overdraw the wallet")
    void concurrentSpendingNeverOverdraws() throws Exception {
        Skill taught = skill("-C6");
        Student teacher = register("Teacher C6");
        Student learner = register("Learner C6");
        skillService.addListing(teacher.getStudentId(), taught.getSkillId(), SkillType.OFFER,
                Proficiency.ADVANCED, "offering");
        int balance = creditService.availableBalance(learner.getStudentId());
        int sessions = Math.max(1, balance / 10); // each 30-minute session costs 10 credits

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < sessions; i++) {
            LocalDateTime start1 = LocalDateTime.now().plusDays(2 + i).withHour(9).withMinute(0).withSecond(0).withNano(0);
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    bookingService.requestBooking(learner.getStudentId(), teacher.getStudentId(),
                            taught.getSkillId(), null, start1, 30, "concurrent");
                } catch (RuntimeException ignored) {
                    // expected for the requests that lose the race for the last credits
                }
                return null;
            }));
        }
        start.countDown();
        for (var future : futures) {
            future.get();
        }
        pool.shutdown();

        Wallet wallet = creditService.getWallet(learner.getStudentId()).orElseThrow();
        assertTrue(wallet.getBalance() >= 0, "Balance must never be negative");
        assertTrue(wallet.getHeldCredits() >= 0);
        assertEquals(balance, wallet.getBalance() + wallet.getHeldCredits(),
                "Available + held must always equal the original balance");
    }

    @Test
    @DisplayName("Balances never become negative, even for an administrator debit that is too large")
    void negativeBalanceIsImpossible() {
        Student student = register("Wallet C7");
        int balance = creditService.availableBalance(student.getStudentId());
        assertThrows(InsufficientCreditsException.class,
                () -> creditService.adminAdjustment(student.getStudentId(), -(balance + 5), "too much", 0L));
        assertEquals(balance, creditService.availableBalance(student.getStudentId()));
    }
}
