package com.skillloop.integration;

import com.skillloop.model.Notification;
import com.skillloop.model.Proficiency;
import com.skillloop.model.Skill;
import com.skillloop.model.SkillType;
import com.skillloop.model.Student;
import com.skillloop.service.AuthService;
import com.skillloop.service.BookingService;
import com.skillloop.service.NotificationService;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Data persistence checks: everything that is written can be read back from MySQL. */
class PersistenceIT {

    private static final String PASSWORD = "SkillLoop@123";
    private final List<Long> created = new ArrayList<>();
    private final AuthService authService = new AuthService();
    private final SkillService skillService = new SkillService();
    private final BookingService bookingService = new BookingService();
    private final NotificationService notificationService = new NotificationService();

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

    @Test
    @DisplayName("Profile changes are persisted and can be read back")
    void profileIsPersisted() {
        Student student = register("Persistence Student");
        new com.skillloop.service.StudentService().updateProfile(student.getStudentId(), "Renamed Student",
                "Information Technology", 7, "Updated bio");
        Student reloaded = new com.skillloop.dao.StudentDAO().findById(student.getStudentId()).orElseThrow();
        assertEquals("Renamed Student", reloaded.getFullName());
        assertEquals("Information Technology", reloaded.getDepartment());
        assertEquals(7, reloaded.getSemester());
        assertEquals("Updated bio", reloaded.getBio());
    }

    @Test
    @DisplayName("Availability slots are persisted and future slots are bookable")
    void availabilityIsPersisted() {
        Student teacher = register("Availability Teacher");
        LocalDateTime start = LocalDateTime.now().plusDays(4).withHour(14).withMinute(0).withSecond(0).withNano(0);
        bookingService.addAvailability(teacher.getStudentId(), start, start.plusHours(2));
        var slots = bookingService.myAvailability(teacher.getStudentId());
        assertTrue(slots.stream().anyMatch(slot -> slot.getStartTime().equals(start)));
        assertEquals(2, slots.stream().filter(slot -> slot.getStartTime().equals(start))
                .findFirst().orElseThrow().lengthMinutes() / 60);
    }

    @Test
    @DisplayName("Booking requests notify the teacher and the notification is persisted")
    void notificationIsPersisted() {
        Skill taught = skillService.createSkill(TestEnvironment.uniqueSkillName() + "-P1", "Testing", "test");
        Student teacher = register("Notify Teacher");
        Student learner = register("Notify Learner");
        skillService.addListing(teacher.getStudentId(), taught.getSkillId(), SkillType.OFFER,
                Proficiency.ADVANCED, "offering");
        bookingService.requestBooking(learner.getStudentId(), teacher.getStudentId(), taught.getSkillId(),
                null, LocalDateTime.now().plusDays(5), 30, "hello");

        List<Notification> notifications = notificationService.listFor(teacher.getStudentId(), 10);
        assertFalse(notifications.isEmpty(), "The teacher must be notified about the booking request");
        assertTrue(notifications.stream().anyMatch(notification ->
                notification.getType() == com.skillloop.model.NotificationType.BOOKING_REQUEST));
        assertTrue(notificationService.countUnread(teacher.getStudentId()) > 0);

        notificationService.markAllRead(teacher.getStudentId());
        assertEquals(0, notificationService.countUnread(teacher.getStudentId()));
    }

    @Test
    @DisplayName("Sessions are listed for both participants with the correct role")
    void sessionsAreVisibleToBothSides() {
        Skill taught = skillService.createSkill(TestEnvironment.uniqueSkillName() + "-P2", "Testing", "test");
        Student teacher = register("Visible Teacher");
        Student learner = register("Visible Learner");
        skillService.addListing(teacher.getStudentId(), taught.getSkillId(), SkillType.OFFER,
                Proficiency.ADVANCED, "offering");
        var session = bookingService.requestBooking(learner.getStudentId(), teacher.getStudentId(),
                taught.getSkillId(), null, LocalDateTime.now().plusDays(6), 45, "see you");

        assertTrue(bookingService.sessionsFor(teacher.getStudentId(), "TEACHING", null, 20).stream()
                .anyMatch(candidate -> candidate.getSessionId() == session.getSessionId()));
        assertTrue(bookingService.sessionsFor(learner.getStudentId(), "LEARNING", null, 20).stream()
                .anyMatch(candidate -> candidate.getSessionId() == session.getSessionId()));
        assertTrue(bookingService.upcoming(teacher.getStudentId(), 20).stream()
                .anyMatch(candidate -> candidate.getSessionId() == session.getSessionId()));
    }
}
