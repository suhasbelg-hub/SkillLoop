package com.skillloop.integration;

import com.skillloop.exception.BookingException;
import com.skillloop.model.LearningSession;
import com.skillloop.model.Proficiency;
import com.skillloop.model.SessionStatus;
import com.skillloop.model.Skill;
import com.skillloop.model.SkillType;
import com.skillloop.model.Student;
import com.skillloop.model.StudentSkill;
import com.skillloop.service.AuthService;
import com.skillloop.service.BookingService;
import com.skillloop.service.CreditService;
import com.skillloop.service.SkillService;
import com.skillloop.util.CreditsPolicy;
import com.skillloop.support.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Booking rules: validation, acceptance, cancellation and completion verification. */
class BookingIT {

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

    /** Registers a teacher that offers the skill and publishes one two-hour slot. */
    private Student teacherFor(Skill skillToTeach) {
        Student teacher = register("Teacher " + skillToTeach.getSkillName());
        skillService.addListing(teacher.getStudentId(), skillToTeach.getSkillId(), SkillType.OFFER,
                Proficiency.ADVANCED, "offering");
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        bookingService.addAvailability(teacher.getStudentId(), start, start.plusHours(2));
        return teacher;
    }

    private LearningSession book(Student learner, Student teacher, Skill skillToLearn, LocalDateTime start,
                                 int duration) {
        var slots = bookingService.bookableSlotsOf(teacher.getStudentId());
        return bookingService.requestBooking(learner.getStudentId(), teacher.getStudentId(),
                skillToLearn.getSkillId(), slots.isEmpty() ? null : slots.get(0).getSlotId(),
                start, duration, "please");
    }

    @Test
    @DisplayName("A booking request is stored as REQUESTED and reserves the credits")
    void requestBooking() {
        Skill skillToTeach = skill("-B1");
        Student teacher = teacherFor(skillToTeach);
        Student learner = register("Learner B1");
        skillService.addListing(learner.getStudentId(), skillToTeach.getSkillId(), SkillType.LEARN,
                Proficiency.BEGINNER, "want");

        int before = creditService.availableBalance(learner.getStudentId());
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LearningSession session = book(learner, teacher, skillToTeach, start, 60);

        assertEquals(SessionStatus.REQUESTED, session.getStatus());
        assertEquals(20, session.getCreditCost(), "A 60 minute session costs 20 credits");
        assertEquals(before - 20, creditService.availableBalance(learner.getStudentId()),
                "The cost must be reserved when the booking is requested");
        assertEquals(20, creditService.getWallet(learner.getStudentId()).orElseThrow().getHeldCredits());
    }

    @Test
    @DisplayName("Self-booking is impossible")
    void selfBookingIsRejected() {
        Skill skillToTeach = skill("-B2");
        Student teacher = teacherFor(skillToTeach);
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        assertThrows(BookingException.class,
                () -> book(teacher, teacher, skillToTeach, start, 30));
    }

    @Test
    @DisplayName("Sessions cannot be booked in the past")
    void pastDatesAreRejected() {
        Skill skillToTeach = skill("-B3");
        Student teacher = teacherFor(skillToTeach);
        Student learner = register("Learner B3");
        assertThrows(com.skillloop.exception.ValidationException.class,
                () -> book(learner, teacher, skillToTeach, LocalDateTime.now().minusDays(2), 30));
    }

    @Test
    @DisplayName("A teacher can only be booked for a skill they actually offer")
    void unknownSkillIsRejected() {
        Skill offered = skill("-B4a");
        Skill other = skill("-B4b");
        Student teacher = teacherFor(offered);
        Student learner = register("Learner B4");
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        assertThrows(BookingException.class, () -> book(learner, teacher, other, start, 30));
    }

    @Test
    @DisplayName("Two confirmed sessions of the same learner may not overlap")
    void overlappingSessionsAreRejected() {
        Skill skillToTeach = skill("-B5");
        Student teacher = teacherFor(skillToTeach);
        Student learner = register("Learner B5");
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        book(learner, teacher, skillToTeach, start, 60);
        assertThrows(BookingException.class, () -> book(learner, teacher, skillToTeach, start.plusMinutes(30), 60));
    }

    @Test
    @DisplayName("Only the teacher may accept or reject a booking")
    void onlyTeacherDecides() {
        Skill skillToTeach = skill("-B6");
        Student teacher = teacherFor(skillToTeach);
        Student learner = register("Learner B6");
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LearningSession session = book(learner, teacher, skillToTeach, start, 30);

        assertThrows(com.skillloop.exception.AuthorizationException.class,
                () -> bookingService.accept(session.getSessionId(), learner.getStudentId()));
        LearningSession accepted = bookingService.accept(session.getSessionId(), teacher.getStudentId());
        assertEquals(SessionStatus.ACCEPTED, accepted.getStatus());
        assertThrows(BookingException.class,
                () -> bookingService.accept(session.getSessionId(), teacher.getStudentId()),
                "A session must not be accepted twice");
    }

    @Test
    @DisplayName("Rejecting a booking releases the reserved credits")
    void rejectionReleasesHold() {
        Skill skillToTeach = skill("-B7");
        Student teacher = teacherFor(skillToTeach);
        Student learner = register("Learner B7");
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        int before = creditService.availableBalance(learner.getStudentId());
        LearningSession session = book(learner, teacher, skillToTeach, start, 30);

        LearningSession rejected = bookingService.reject(session.getSessionId(), teacher.getStudentId(), "busy");
        assertEquals(SessionStatus.REJECTED, rejected.getStatus());
        assertEquals(before, creditService.availableBalance(learner.getStudentId()),
                "The reservation must be released when the teacher rejects");
    }

    @Test
    @DisplayName("Cancelling releases the reservation and frees the slot again")
    void cancellationReleasesHold() {
        Skill skillToTeach = skill("-B8");
        Student teacher = teacherFor(skillToTeach);
        Student learner = register("Learner B8");
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        int before = creditService.availableBalance(learner.getStudentId());
        LearningSession session = book(learner, teacher, skillToTeach, start, 30);
        bookingService.cancel(session.getSessionId(), learner.getStudentId(), "changed plans");
        assertEquals(SessionStatus.CANCELLED,
                bookingService.find(session.getSessionId()).orElseThrow().getStatus());
        assertEquals(before, creditService.availableBalance(learner.getStudentId()));
    }

    @Test
    @DisplayName("Completion requires both confirmations and only works after the start time")
    void completionRequiresBothSides() {
        Skill skillToTeach = skill("-B9");
        Student teacher = teacherFor(skillToTeach);
        Student learner = register("Learner B9");
        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(11).withMinute(0).withSecond(0).withNano(0);
        LearningSession session = book(learner, teacher, skillToTeach, start, 60);
        bookingService.accept(session.getSessionId(), teacher.getStudentId());

        assertThrows(BookingException.class,
                () -> bookingService.confirmCompletion(session.getSessionId(), teacher.getStudentId()),
                "A session that has not started yet cannot be completed");

        LearningSession backdated = TestEnvironment.acceptedPastSession(bookingService, learner.getStudentId(),
                teacher.getStudentId(), skillToTeach.getSkillId(), 60);

        int teacherBefore = creditService.availableBalance(teacher.getStudentId());
        LearningSession oneSide = bookingService.confirmCompletion(backdated.getSessionId(), teacher.getStudentId());
        assertEquals(SessionStatus.ACCEPTED, oneSide.getStatus(),
                "One confirmation is not enough - the credits must stay on hold");
        assertThrows(BookingException.class,
                () -> bookingService.confirmCompletion(backdated.getSessionId(), teacher.getStudentId()),
                "Confirming twice must be rejected");

        LearningSession bothSides = bookingService.confirmCompletion(backdated.getSessionId(), learner.getStudentId());
        assertEquals(SessionStatus.COMPLETED, bothSides.getStatus());
        assertEquals(teacherBefore + CreditsPolicy.costFor(60), creditService.availableBalance(teacher.getStudentId()));
    }
}
