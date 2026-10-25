package com.skillloop.integration;

import com.skillloop.dao.StudentDAO;
import com.skillloop.model.AdminStats;
import com.skillloop.model.MonthlyVolume;
import com.skillloop.model.Role;
import com.skillloop.model.Skill;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Analytics: the dashboard numbers come from the database, not from constants. */
class AnalyticsIT {

    private static final String PASSWORD = "SkillLoop@123";
    private final List<Long> created = new ArrayList<>();
    private final AuthService authService = new AuthService();
    private final SkillService skillService = new SkillService();
    private final BookingService bookingService = new BookingService();
    private final AdminService adminService = new AdminService();
    private final CreditService creditService = new CreditService();
    private final StudentDAO studentDAO = new StudentDAO();

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

    private Student adminOf(Student student) {
        studentDAO.updateRole(student.getStudentId(), Role.ADMIN);
        return studentDAO.findById(student.getStudentId()).orElseThrow();
    }

    @Test
    @DisplayName("The dashboard counters follow the rows that the test creates")
    void countersTrackTheData() {
        Student administrator = adminOf(register("Analytics Admin"));
        AdminStats before = adminService.stats(administrator.getStudentId());
        Skill skill = skillService.createSkill(TestEnvironment.uniqueSkillName() + "-AN1", "Testing", "test");

        AdminStats after = adminService.stats(administrator.getStudentId());
        assertEquals(before.getTotalStudents() + 1, after.getTotalStudents(), "One student was added");
        assertEquals(before.getTotalSkills() + 1, after.getTotalSkills(), "One skill was added");
        assertTrue(after.getActiveStudents() <= after.getTotalStudents());
        assertTrue(after.getAdministrators() >= 1);
        assertFalse(adminService.popularSkills(administrator.getStudentId(), 10) == null);
    }

    @Test
    @DisplayName("A completed session shows up in the credit volume statistics")
    void volumeTracksSessions() {
        Student administrator = adminOf(register("Analytics Admin 2"));
        com.skillloop.model.Proficiency proficiency = com.skillloop.model.Proficiency.ADVANCED;
        Skill taught = skillService.createSkill(TestEnvironment.uniqueSkillName() + "-AN2", "Testing", "test");
        Student teacher = register("Analytics Teacher");
        Student learner = register("Analytics Learner");
        skillService.addListing(teacher.getStudentId(), taught.getSkillId(),
                com.skillloop.model.SkillType.OFFER, proficiency, "offering");

        creditService.grantStarterGrant(learner.getStudentId());
        int cost = com.skillloop.util.CreditsPolicy.costFor(60);
        long before = adminService.stats(administrator.getStudentId()).getCreditsTransferred();

        TestEnvironment.completedSession(bookingService, learner.getStudentId(), teacher.getStudentId(),
                taught.getSkillId(), 60);

        AdminStats after = adminService.stats(administrator.getStudentId());
        assertEquals(before + cost, after.getCreditsTransferred(),
                "Settling a session raises the transferred credit volume");

        List<MonthlyVolume> volume = adminService.monthlyVolume(administrator.getStudentId(), 6);
        assertFalse(volume == null, "The monthly volume report is available");
        volume.forEach(entry -> {
            assertTrue(entry.getTransactionCount() >= 0);
            assertTrue(entry.getCreditsCredited() >= 0);
            assertTrue(entry.getCreditsDebited() >= 0);
        });
    }

    @Test
    @DisplayName("The most active teachers report is ordered by contribution")
    void mostActiveTeachersIsOrdered() {
        Student administrator = adminOf(register("Analytics Admin 3"));
        List<com.skillloop.model.LeaderboardEntry> teachers =
                adminService.mostActiveTeachers(administrator.getStudentId(), 5);
        assertTrue(teachers.size() <= 5);
        for (int i = 1; i < teachers.size(); i++) {
            assertTrue(teachers.get(i - 1).getTeachingSessions() >= teachers.get(i).getTeachingSessions());
        }
        assertFalse(adminService.departmentReport(administrator.getStudentId()) == null);
        assertTrue(adminService.recentTransactions(administrator.getStudentId(), 10) != null);
    }
}
