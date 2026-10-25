package com.skillloop.integration;

import com.skillloop.exception.ValidationException;
import com.skillloop.model.LearningResource;
import com.skillloop.model.Skill;
import com.skillloop.model.Student;
import com.skillloop.service.AuthService;
import com.skillloop.service.ResourceService;
import com.skillloop.service.SkillService;
import com.skillloop.support.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Learning resources: sharing, listing, filtering by skill and removal. */
class ResourceIT {

    private static final String PASSWORD = "SkillLoop@123";
    private final List<Long> created = new ArrayList<>();
    private final AuthService authService = new AuthService();
    private final SkillService skillService = new SkillService();
    private final ResourceService resourceService = new ResourceService();

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
    @DisplayName("A shared resource is stored, listed and filtered by skill")
    void shareAndList() {
        Student author = register("Resource Author");
        Skill skill = skillService.createSkill(TestEnvironment.uniqueSkillName() + "-RES1", "Testing", "test");

        LearningResource resource = resourceService.share(author.getStudentId(), skill.getSkillId(),
                "Official documentation", "Start here", "https://example.test/docs");
        assertTrue(resource.getResourceId() > 0);

        assertTrue(resourceService.all(50).stream()
                .anyMatch(candidate -> candidate.getResourceId() == resource.getResourceId()));
        assertTrue(resourceService.forSkill(skill.getSkillId()).stream()
                .anyMatch(candidate -> candidate.getResourceId() == resource.getResourceId()));
        assertEquals(1, resourceService.mine(author.getStudentId()).stream()
                .filter(candidate -> candidate.getResourceId() == resource.getResourceId()).count());
        assertTrue(resourceService.all(50).stream()
                .anyMatch(candidate -> candidate.getSkillName() != null && !candidate.getSkillName().isBlank()));
    }

    @Test
    @DisplayName("Only well formed URLs and titles are accepted")
    void validationRules() {
        Student author = register("Resource Author 2");
        Skill skill = skillService.createSkill(TestEnvironment.uniqueSkillName() + "-RES2", "Testing", "test");
        assertThrows(ValidationException.class, () -> resourceService.share(
                author.getStudentId(), skill.getSkillId(), "", "no title", "https://example.test"));
        assertThrows(ValidationException.class, () -> resourceService.share(
                author.getStudentId(), skill.getSkillId(), "No link", "bad url", "not-a-url"));
    }

    @Test
    @DisplayName("A student can only remove their own resource")
    void deletion() {
        Student author = register("Resource Author 3");
        Student stranger = register("Resource Stranger");
        Skill skill = skillService.createSkill(TestEnvironment.uniqueSkillName() + "-RES3", "Testing", "test");
        LearningResource resource = resourceService.share(author.getStudentId(), skill.getSkillId(),
                "Notes", "summary", "https://example.test/notes");
        assertThrows(com.skillloop.exception.AuthorizationException.class,
                () -> resourceService.delete(stranger.getStudentId(), resource.getResourceId()));
        resourceService.delete(author.getStudentId(), resource.getResourceId());
        assertTrue(resourceService.mine(author.getStudentId()).stream()
                .noneMatch(candidate -> candidate.getResourceId() == resource.getResourceId()));
    }
}
