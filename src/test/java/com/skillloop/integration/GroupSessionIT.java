package com.skillloop.integration;

import com.skillloop.exception.BookingException;
import com.skillloop.model.GroupSession;
import com.skillloop.model.Proficiency;
import com.skillloop.model.Skill;
import com.skillloop.model.SkillType;
import com.skillloop.model.Student;
import com.skillloop.service.AuthService;
import com.skillloop.service.CreditService;
import com.skillloop.service.GroupSessionService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Group sessions: creation, enrolment, capacity and credit distribution. */
class GroupSessionIT {

    private static final String PASSWORD = "SkillLoop@123";
    private final List<Long> created = new ArrayList<>();
    private final AuthService authService = new AuthService();
    private final SkillService skillService = new SkillService();
    private final GroupSessionService groupService = new GroupSessionService();
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

    @Test
    @DisplayName("A teacher can host a group session only for a skill they offer")
    void creationRules() {
        Student teacher = register("Group Teacher");
        Skill skill = skillService.createSkill(TestEnvironment.uniqueSkillName() + "-G1", "Testing", "test");
        assertThrows(com.skillloop.exception.ValidationException.class, () -> groupService.create(
                teacher.getStudentId(), skill.getSkillId(), "Illegal group",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5, 10));

        skillService.addListing(teacher.getStudentId(), skill.getSkillId(), SkillType.OFFER,
                Proficiency.ADVANCED, "offering");
        GroupSession group = groupService.create(teacher.getStudentId(), skill.getSkillId(), "Legal group",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 3, 15);
        assertTrue(group.getGroupId() > 0);
    }

    @Test
    @DisplayName("Learners are charged on enrolment and the teacher is rewarded on completion")
    void enrolmentAndCompletion() {
        Student teacher = register("Group Teacher 2");
        Student first = register("Group Learner 1");
        Student second = register("Group Learner 2");
        Skill skill = skillService.createSkill(TestEnvironment.uniqueSkillName() + "-G2", "Testing", "test");
        skillService.addListing(teacher.getStudentId(), skill.getSkillId(), SkillType.OFFER,
                Proficiency.ADVANCED, "offering");

        GroupSession group = groupService.create(teacher.getStudentId(), skill.getSkillId(), "Group class",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5, 10);

        int teacherBefore = creditService.availableBalance(teacher.getStudentId());
        int firstBefore = creditService.availableBalance(first.getStudentId());

        groupService.join(group.getGroupId(), first.getStudentId());
        groupService.join(group.getGroupId(), second.getStudentId());

        assertEquals(firstBefore - 10, creditService.availableBalance(first.getStudentId()),
                "The enrolment fee must be charged immediately");
        assertThrows(BookingException.class, () -> groupService.join(group.getGroupId(), first.getStudentId()),
                "Joining twice must not be possible");

        groupService.complete(group.getGroupId(), teacher.getStudentId());
        assertEquals(teacherBefore + 20, creditService.availableBalance(teacher.getStudentId()),
                "The teacher earns the fee for every participant");

        GroupSession completed = groupService.mySessions(teacher.getStudentId(), 10).stream()
                .filter(candidate -> candidate.getGroupId() == group.getGroupId())
                .findFirst().orElseThrow();
        assertEquals(com.skillloop.model.GroupStatus.COMPLETED, completed.getStatus());
        assertEquals(2, completed.getEnrolled());
    }

    @Test
    @DisplayName("The capacity of a group session is enforced")
    void capacityIsEnforced() {
        Student teacher = register("Group Teacher 3");
        Skill skill = skillService.createSkill(TestEnvironment.uniqueSkillName() + "-G3", "Testing", "test");
        skillService.addListing(teacher.getStudentId(), skill.getSkillId(), SkillType.OFFER,
                Proficiency.ADVANCED, "offering");
        GroupSession group = groupService.create(teacher.getStudentId(), skill.getSkillId(), "Small group",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1), 2, 10);

        Student first = register("Group Learner A");
        Student second = register("Group Learner B");
        Student third = register("Group Learner C");
        groupService.join(group.getGroupId(), first.getStudentId());
        groupService.join(group.getGroupId(), second.getStudentId());
        assertThrows(BookingException.class, () -> groupService.join(group.getGroupId(), third.getStudentId()));
        assertThrows(BookingException.class, () -> groupService.join(group.getGroupId(), teacher.getStudentId()),
                "The host cannot join their own group session");
    }
}
