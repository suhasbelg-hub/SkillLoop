package com.skillloop.integration;

import com.skillloop.model.LeaderboardEntry;
import com.skillloop.model.Proficiency;
import com.skillloop.model.Skill;
import com.skillloop.model.SkillType;
import com.skillloop.model.Student;
import com.skillloop.service.AuthService;
import com.skillloop.service.BookingService;
import com.skillloop.service.CreditService;
import com.skillloop.service.SkillService;
import com.skillloop.service.StudentService;
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

/** The leaderboard ranks verified contributions, never the wallet balance. */
class LeaderboardIT {

    private static final String PASSWORD = "SkillLoop@123";
    private final List<Long> created = new ArrayList<>();
    private final AuthService authService = new AuthService();
    private final SkillService skillService = new SkillService();
    private final BookingService bookingService = new BookingService();
    private final StudentService studentService = new StudentService();
    private final CreditService creditService = new CreditService();

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
    @DisplayName("A teacher with verified sessions ranks above one without, whatever the balance is")
    void contributionBeatsBalance() {
        Skill taught = skillService.createSkill(TestEnvironment.uniqueSkillName() + "-L1", "Testing", "test");
        Student teacher = register("Leader Teacher");
        Student learner = register("Leader Learner");
        Student rich = register("Rich Student");
        skillService.addListing(teacher.getStudentId(), taught.getSkillId(), SkillType.OFFER,
                Proficiency.ADVANCED, "offering");

        // Give the idle student a large balance: it must not influence the ranking.
        creditService.adminAdjustment(rich.getStudentId(), 90, "test fixture", teacher.getStudentId());

        TestEnvironment.completedSession(bookingService, learner.getStudentId(), teacher.getStudentId(),
                taught.getSkillId(), 60);

        List<LeaderboardEntry> board = studentService.leaderboard(1000);
        assertFalse(board.isEmpty());
        int teacherRank = rank(board, teacher.getStudentId());
        int richRank = rank(board, rich.getStudentId());
        assertTrue(teacherRank > 0 && richRank > 0, "Both students appear on the board");
        assertTrue(teacherRank < richRank,
                "The teaching student ranks better than the student who only owns credits");
    }

    @Test
    @DisplayName("The leaderboard is sorted by score and capped at the requested size")
    void boardIsSortedAndLimited() {
        List<LeaderboardEntry> board = studentService.leaderboard(5);
        assertTrue(board.size() <= 5, "At most the requested number of entries is returned");
        for (int i = 1; i < board.size(); i++) {
            assertTrue(board.get(i - 1).getScore() >= board.get(i).getScore(), "The board is sorted by score");
        }
        for (int i = 0; i < board.size(); i++) {
            assertEquals(i + 1, board.get(i).getRank(), "The rank matches the position");
        }
    }

    @Test
    @DisplayName("Progress analytics are computed from the student's own sessions")
    void progressAnalytics() {
        Skill taught = skillService.createSkill(TestEnvironment.uniqueSkillName() + "-L2", "Testing", "test");
        Student teacher = register("Progress Teacher");
        Student learner = register("Progress Learner");
        skillService.addListing(teacher.getStudentId(), taught.getSkillId(), SkillType.OFFER,
                Proficiency.ADVANCED, "offering");
        TestEnvironment.completedSession(bookingService, learner.getStudentId(), teacher.getStudentId(),
                taught.getSkillId(), 60);

        var teacherProgress = studentService.getProgress(teacher.getStudentId());
        var learnerProgress = studentService.getProgress(learner.getStudentId());
        assertEquals(1, teacherProgress.getTeachingSessions());
        assertEquals(1.0, teacherProgress.getTeachingHours(), 0.001);
        assertEquals(1, learnerProgress.getLearningSessions());
        assertEquals(1.0, learnerProgress.getLearningHours(), 0.001);
    }

    private int rank(List<LeaderboardEntry> board, long studentId) {
        for (LeaderboardEntry entry : board) {
            if (entry.getStudentId() == studentId) {
                return entry.getRank();
            }
        }
        return -1;
    }
}
