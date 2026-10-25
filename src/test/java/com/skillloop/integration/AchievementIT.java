package com.skillloop.integration;

import com.skillloop.model.Achievement;
import com.skillloop.model.LearningSession;
import com.skillloop.model.Proficiency;
import com.skillloop.model.Skill;
import com.skillloop.model.SkillType;
import com.skillloop.model.Student;
import com.skillloop.service.AchievementService;
import com.skillloop.service.AuthService;
import com.skillloop.service.BookingService;
import com.skillloop.service.SkillService;
import com.skillloop.support.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Achievements are awarded for verified contributions and never twice. */
class AchievementIT {

    private static final String PASSWORD = "SkillLoop@123";
    private final List<Long> created = new ArrayList<>();
    private final AuthService authService = new AuthService();
    private final SkillService skillService = new SkillService();
    private final BookingService bookingService = new BookingService();
    private final AchievementService achievementService = new AchievementService();

    @BeforeAll
    static void requireDatabase() {
        TestEnvironment.assumeDatabase();
    }

    @AfterEach
    void cleanUp() {
        TestEnvironment.deleteStudents(created);
        created.clear();
    }

    private Student register(String name) {
        Student student = authService.register(name, TestEnvironment.uniqueEmail(), PASSWORD, PASSWORD, "CS", 5);
        created.add(student.getStudentId());
        return student;
    }

    @Test
    @DisplayName("The badge catalogue is available")
    void catalogueExists() {
        List<Achievement> badges = achievementService.listAll();
        assertFalse(badges.isEmpty(), "The achievement definitions must be present");
        assertTrue(badges.stream().anyMatch(badge -> "FIRST_TEACHING_SESSION".equals(badge.getCode())));
    }

    @Test
    @DisplayName("Completing the first verified session awards the first teaching badge")
    void firstSessionAwardsBadge() {
        Skill taught = skillService.createSkill(TestEnvironment.uniqueSkillName() + "-ACH1", "Testing", "test");
        Student teacher = register("Achievement Teacher");
        Student learner = register("Achievement Learner");
        skillService.addListing(teacher.getStudentId(), taught.getSkillId(), SkillType.OFFER,
                Proficiency.ADVANCED, "offering");

        assertFalse(achievementService.listForStudent(teacher.getStudentId()).stream()
                .anyMatch(badge -> "FIRST_TEACHING_SESSION".equals(badge.getCode())));

        LearningSession session = TestEnvironment.completedSession(bookingService, learner.getStudentId(),
                teacher.getStudentId(), taught.getSkillId(), 60);

        assertTrue(session.getStatus() == com.skillloop.model.SessionStatus.COMPLETED, "The session is verified");
        assertTrue(achievementService.listForStudent(teacher.getStudentId()).stream()
                        .anyMatch(badge -> "FIRST_TEACHING_SESSION".equals(badge.getCode())),
                "The teacher earns the badge for the first verified session");

        // Idempotency: a second settled session must not duplicate the badge.
        Skill second = skillService.createSkill(TestEnvironment.uniqueSkillName() + "-ACH2", "Testing", "test");
        skillService.addListing(teacher.getStudentId(), second.getSkillId(), SkillType.OFFER,
                Proficiency.ADVANCED, "offering too");
        TestEnvironment.completedSession(bookingService, learner.getStudentId(), teacher.getStudentId(),
                second.getSkillId(), 30);
        assertEquals(1, achievementService.listForStudent(teacher.getStudentId()).stream()
                .filter(badge -> "FIRST_TEACHING_SESSION".equals(badge.getCode())).count());
    }
}
