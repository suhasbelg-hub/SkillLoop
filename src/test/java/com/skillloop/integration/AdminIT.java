package com.skillloop.integration;

import com.skillloop.exception.AuthorizationException;
import com.skillloop.exception.ValidationException;
import com.skillloop.model.AccountStatus;
import com.skillloop.model.AdminStats;
import com.skillloop.model.LearningSession;
import com.skillloop.model.Proficiency;
import com.skillloop.model.Skill;
import com.skillloop.model.SkillType;
import com.skillloop.model.Student;
import com.skillloop.service.AdminService;
import com.skillloop.service.AuthService;
import com.skillloop.service.BookingService;
import com.skillloop.service.CreditService;
import com.skillloop.service.SkillService;
import com.skillloop.support.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Administrator authorisation, statistics, dispute resolution and credit adjustments. */
class AdminIT {

    private static final String PASSWORD = "SkillLoop@123";
    private final List<Long> created = new ArrayList<>();
    private final AuthService authService = new AuthService();
    private final SkillService skillService = new SkillService();
    private final BookingService bookingService = new BookingService();
    private final AdminService adminService = new AdminService();
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

    private Student admin() {
        Student student = register("Admin Account");
        new com.skillloop.dao.StudentDAO().updateRole(student.getStudentId(), com.skillloop.model.Role.ADMIN);
        Student reloaded = new com.skillloop.dao.StudentDAO().findById(student.getStudentId()).orElseThrow();
        assertTrue(reloaded.isAdmin());
        return reloaded;
    }

    @Test
    @DisplayName("The service layer rejects administrator calls from ordinary students")
    void authorizationIsEnforcedInTheServiceLayer() {
        Student student = register("Plain Student");
        assertThrows(AuthorizationException.class, () -> adminService.stats(student.getStudentId()));
        assertThrows(AuthorizationException.class,
                () -> adminService.recentTransactions(student.getStudentId(), 10));
        assertThrows(AuthorizationException.class,
                () -> adminService.adjustCredits(student.getStudentId(), student.getStudentId(), 5, "test"));
        assertThrows(AuthorizationException.class,
                () -> adminService.disputes(student.getStudentId()));
    }

    @Test
    @DisplayName("The dashboard statistics are read from the database")
    void statisticsAreLive() {
        Student administrator = admin();
        AdminStats stats = adminService.stats(administrator.getStudentId());
        assertTrue(stats.getTotalStudents() > 0);
        assertTrue(stats.getTotalSkills() > 0);
        assertTrue(stats.getTotalStudents() >= stats.getActiveStudents());
        assertTrue(adminService.popularSkills(administrator.getStudentId(), 5) != null);
        assertTrue(adminService.mostActiveTeachers(administrator.getStudentId(), 5) != null);
        assertTrue(adminService.monthlyVolume(administrator.getStudentId(), 6) != null);
    }

    @Test
    @DisplayName("An administrator can suspend and reactivate an account (audited)")
    void accountModeration() {
        Student administrator = admin();
        Student target = register("Suspended Student");
        adminService.setAccountStatus(administrator.getStudentId(), target.getStudentId(),
                AccountStatus.SUSPENDED, "integration test");
        assertEquals(AccountStatus.SUSPENDED,
                new com.skillloop.dao.StudentDAO().findById(target.getStudentId()).orElseThrow().getAccountStatus());
        assertThrows(com.skillloop.exception.AuthenticationException.class,
                () -> authService.login(target.getEmail(), PASSWORD));

        adminService.setAccountStatus(administrator.getStudentId(), target.getStudentId(),
                AccountStatus.ACTIVE, "integration test");
        assertEquals(AccountStatus.ACTIVE,
                new com.skillloop.dao.StudentDAO().findById(target.getStudentId()).orElseThrow().getAccountStatus());

        assertTrue(adminService.auditLog(administrator.getStudentId(), 20).stream()
                .anyMatch(event -> "SET_ACCOUNT_STATUS".equals(event.getAction())
                        && event.getTargetId() != null
                        && event.getTargetId() == target.getStudentId()));
    }

    @Test
    @DisplayName("A dispute resolved in favour of the teacher pays the teacher")
    void disputeResolvedInFavourOfTeacher() {
        Student administrator = admin();
        Skill taught = skillService.createSkill(TestEnvironment.uniqueSkillName() + "-A1", "Testing", "test");
        Student teacher = register("Teacher A1");
        Student learner = register("Learner A1");
        skillService.addListing(teacher.getStudentId(), taught.getSkillId(), SkillType.OFFER,
                Proficiency.ADVANCED, "offering");
        skillService.addListing(learner.getStudentId(), taught.getSkillId(), SkillType.LEARN,
                Proficiency.BEGINNER, "want");

        LearningSession session = bookingService.requestBooking(learner.getStudentId(), teacher.getStudentId(),
                taught.getSkillId(), null, LocalDateTime.now().minusHours(2), 60, "test");
        bookingService.accept(session.getSessionId(), teacher.getStudentId());
        bookingService.raiseDispute(session.getSessionId(), learner.getStudentId(), "teacher did not show up");
        assertEquals(com.skillloop.model.SessionStatus.DISPUTED,
                bookingService.find(session.getSessionId()).orElseThrow().getStatus());

        int teacherBefore = creditService.availableBalance(teacher.getStudentId());
        adminService.resolveDispute(administrator.getStudentId(), session.getSessionId(),
                AdminService.DisputeResolution.PAY_TEACHER, "evidence shows the session happened");

        assertEquals(teacherBefore + 20, creditService.availableBalance(teacher.getStudentId()));
        assertEquals(com.skillloop.model.SessionStatus.COMPLETED,
                bookingService.find(session.getSessionId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("A dispute resolved in favour of the learner refunds the reserved credits")
    void disputeResolvedInFavourOfLearner() {
        Student administrator = admin();
        Skill taught = skillService.createSkill(TestEnvironment.uniqueSkillName() + "-A2", "Testing", "test");
        Student teacher = register("Teacher A2");
        Student learner = register("Learner A2");
        skillService.addListing(teacher.getStudentId(), taught.getSkillId(), SkillType.OFFER,
                Proficiency.ADVANCED, "offering");
        skillService.addListing(learner.getStudentId(), taught.getSkillId(), SkillType.LEARN,
                Proficiency.BEGINNER, "want");

        LearningSession session = bookingService.requestBooking(learner.getStudentId(), teacher.getStudentId(),
                taught.getSkillId(), null, LocalDateTime.now().plusDays(3), 60, "test");
        bookingService.accept(session.getSessionId(), teacher.getStudentId());
        bookingService.raiseDispute(session.getSessionId(), learner.getStudentId(), "cannot agree on a time");
        int before = creditService.availableBalance(learner.getStudentId());
        int held = creditService.getWallet(learner.getStudentId()).orElseThrow().getHeldCredits();
        assertEquals(20, held);

        adminService.resolveDispute(administrator.getStudentId(), session.getSessionId(),
                AdminService.DisputeResolution.REFUND_LEARNER, "no session took place");

        assertEquals(before + 20, creditService.availableBalance(learner.getStudentId()));
        assertEquals(0, creditService.getWallet(learner.getStudentId()).orElseThrow().getHeldCredits());
    }

    @Test
    @DisplayName("Credit adjustments are limited, audited and reversible in the ledger")
    void creditAdjustments() {
        Student administrator = admin();
        Student target = register("Adjusted Student");
        int before = creditService.availableBalance(target.getStudentId());

        assertThrows(ValidationException.class, () -> adminService.adjustCredits(
                administrator.getStudentId(), target.getStudentId(), 10_000, "too much"));
        assertThrows(ValidationException.class, () -> adminService.adjustCredits(
                administrator.getStudentId(), target.getStudentId(), 10, ""));

        adminService.adjustCredits(administrator.getStudentId(), target.getStudentId(), 15, "integration test bonus");
        assertEquals(before + 15, creditService.availableBalance(target.getStudentId()));
        assertTrue(creditService.history(target.getStudentId(), 10).stream().anyMatch(txn ->
                txn.getType() == com.skillloop.model.TransactionType.ADMIN_ADJUSTMENT
                        && txn.getAmount() == 15
                        && txn.getCreatedBy() != null
                        && txn.getCreatedBy() == administrator.getStudentId()));
    }

    @Test
    @DisplayName("Listing moderation hides a listing without deleting it")
    void listingModeration() {
        Student administrator = admin();
        Student owner = register("Listing Owner A4");
        Skill skill = skillService.createSkill(TestEnvironment.uniqueSkillName() + "-A4", "Testing", "test");
        var listing = skillService.addListing(owner.getStudentId(), skill.getSkillId(), SkillType.OFFER,
                Proficiency.ADVANCED, "to be moderated");

        adminService.moderateListing(administrator.getStudentId(), listing.getStudentSkillId(), false, "spam");
        assertNotEquals(true, skillService.myListings(owner.getStudentId(), SkillType.OFFER).stream()
                .filter(candidate -> candidate.getStudentSkillId() == listing.getStudentSkillId())
                .findFirst().orElseThrow().isActive());
        adminService.moderateListing(administrator.getStudentId(), listing.getStudentSkillId(), true, "re-checked");
    }

    @Test
    @DisplayName("Promoting a student to administrator is audited")
    void promotion() {
        Student administrator = admin();
        Student target = register("Promoted Student");
        adminService.promote(administrator.getStudentId(), target.getStudentId());
        assertTrue(new com.skillloop.dao.StudentDAO().findById(target.getStudentId()).orElseThrow().isAdmin());
        assertTrue(adminService.auditLog(administrator.getStudentId(), 20).stream()
                .anyMatch(event -> "PROMOTE_ADMIN".equals(event.getAction())));
    }
}
