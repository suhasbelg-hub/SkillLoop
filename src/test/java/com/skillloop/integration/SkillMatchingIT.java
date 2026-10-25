package com.skillloop.integration;

import com.skillloop.exception.AuthorizationException;
import com.skillloop.exception.DuplicateResourceException;
import com.skillloop.exception.NotFoundException;
import com.skillloop.model.MatchResult;
import com.skillloop.model.Proficiency;
import com.skillloop.model.SkillType;
import com.skillloop.model.Student;
import com.skillloop.model.StudentSkill;
import com.skillloop.service.AuthService;
import com.skillloop.service.MatchingService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Skill catalogue, listings and the matching engine against a real MySQL database. */
class SkillMatchingIT {

    private static final String PASSWORD = "SkillLoop@123";
    private final List<Long> created = new ArrayList<>();
    private final SkillService skillService = new SkillService();
    private final MatchingService matchingService = new MatchingService();
    private final AuthService authService = new AuthService();

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

    private long catalogueSkill(String name, String category) {
        try {
            return skillService.createSkill(name, category, "created by an integration test").getSkillId();
        } catch (DuplicateResourceException alreadyExists) {
            return skillService.search(name, null).stream()
                    .filter(skill -> skill.getSkillName().equals(name))
                    .findFirst().orElseThrow().getSkillId();
        }
    }

    @Test
    @DisplayName("A skill can be created once and found again through the search")
    void createAndSearchSkill() {
        String name = TestEnvironment.uniqueSkillName();
        long skillId = catalogueSkill(name, "Testing");
        assertTrue(skillId > 0);
        assertTrue(skillService.search(name, null).stream()
                .anyMatch(skill -> skill.getSkillId() == skillId));
        assertThrows(DuplicateResourceException.class, () -> skillService.createSkill(name, "Testing", null));
    }

    @Test
    @DisplayName("Listings are stored as OFFER or LEARN and can be deactivated")
    void listingLifecycle() {
        Student student = register("Listing Student");
        long skillId = catalogueSkill(TestEnvironment.uniqueSkillName(), "Testing");
        StudentSkill listing = skillService.addListing(student.getStudentId(), skillId, SkillType.OFFER,
                Proficiency.ADVANCED, "I can teach this");
        assertTrue(skillService.myListings(student.getStudentId(), SkillType.OFFER).stream()
                .anyMatch(candidate -> candidate.getStudentSkillId() == listing.getStudentSkillId()));
        skillService.deactivateListing(student.getStudentId(), listing.getStudentSkillId());
        assertFalse(skillService.teachersFor(skillId, null).stream()
                .anyMatch(teacher -> teacher.getStudentSkillId() == listing.getStudentSkillId()));
    }

    @Test
    @DisplayName("A student cannot modify the listing of another student")
    void listingsAreProtected() {
        Student owner = register("Owner Student");
        Student stranger = register("Stranger Student");
        long skillId = catalogueSkill(TestEnvironment.uniqueSkillName(), "Testing");
        StudentSkill listing = skillService.addListing(owner.getStudentId(), skillId, SkillType.OFFER,
                Proficiency.BEGINNER, "mine");
        assertThrows(AuthorizationException.class,
                () -> skillService.deactivateListing(stranger.getStudentId(), listing.getStudentSkillId()));
        assertThrows(NotFoundException.class,
                () -> skillService.updateListing(stranger.getStudentId(), 999999L, Proficiency.ADVANCED, "x", true));
    }

    @Test
    @DisplayName("Reciprocal partners are recommended in both directions (Rahul / Priya scenario)")
    void reciprocalMatching() {
        long javaSkill = catalogueSkill(TestEnvironment.uniqueSkillName() + "-Java", "Testing");
        long designSkill = catalogueSkill(TestEnvironment.uniqueSkillName() + "-Design", "Testing");

        Student rahul = register("Rahul Test");
        Student priya = register("Priya Test");

        skillService.addListing(rahul.getStudentId(), javaSkill, SkillType.OFFER, Proficiency.ADVANCED, "Java");
        skillService.addListing(rahul.getStudentId(), designSkill, SkillType.LEARN, Proficiency.BEGINNER, "want");
        skillService.addListing(priya.getStudentId(), designSkill, SkillType.OFFER, Proficiency.ADVANCED, "Design");
        skillService.addListing(priya.getStudentId(), javaSkill, SkillType.LEARN, Proficiency.BEGINNER, "want");

        List<MatchResult> forRahul = matchingService.findReciprocalPartners(rahul.getStudentId(), 20);
        assertTrue(forRahul.stream().anyMatch(match -> match.getTeacherId() == priya.getStudentId()),
                "Priya must be recommended to Rahul as a reciprocal partner");
        List<MatchResult> forPriya = matchingService.findReciprocalPartners(priya.getStudentId(), 20);
        assertTrue(forPriya.stream().anyMatch(match -> match.getTeacherId() == rahul.getStudentId()),
                "Rahul must be recommended to Priya as a reciprocal partner");
        assertTrue(forRahul.stream().anyMatch(MatchResult::isReciprocal));
    }

    @Test
    @DisplayName("One-way credit learning works as well: teachers are found without reciprocity")
    void oneWayRecommendation() {
        long skillId = catalogueSkill(TestEnvironment.uniqueSkillName() + "-Solo", "Testing");
        Student learner = register("Learner Solo");
        Student teacher = register("Teacher Solo");
        skillService.addListing(learner.getStudentId(), skillId, SkillType.LEARN, Proficiency.BEGINNER, "want");
        skillService.addListing(teacher.getStudentId(), skillId, SkillType.OFFER, Proficiency.INTERMEDIATE, "offer");

        List<MatchResult> matches = matchingService.recommendTeachers(learner.getStudentId(), skillId, null, 20);
        assertTrue(matches.stream().anyMatch(match -> match.getTeacherId() == teacher.getStudentId()));
    }

    @Test
    @DisplayName("A student is never recommended to themselves")
    void noSelfRecommendation() {
        long skillId = catalogueSkill(TestEnvironment.uniqueSkillName() + "-Self", "Testing");
        Student student = register("Self Student");
        skillService.addListing(student.getStudentId(), skillId, SkillType.OFFER, Proficiency.ADVANCED, "offer");
        skillService.addListing(student.getStudentId(), skillId, SkillType.LEARN, Proficiency.BEGINNER, "want");
        assertTrue(matchingService.recommendTeachers(student.getStudentId(), skillId, null, 20).stream()
                .noneMatch(match -> match.getTeacherId() == student.getStudentId()));
        assertTrue(matchingService.findReciprocalPartners(student.getStudentId(), 20).stream()
                .noneMatch(match -> match.getTeacherId() == student.getStudentId()));
    }

    @Test
    @DisplayName("The proficiency filter removes weaker listings from the result")
    void proficiencyFilter() {
        long skillId = catalogueSkill(TestEnvironment.uniqueSkillName() + "-Level", "Testing");
        Student beginner = register("Beginner Teacher");
        Student advanced = register("Advanced Teacher");
        skillService.addListing(beginner.getStudentId(), skillId, SkillType.OFFER, Proficiency.BEGINNER, "b");
        skillService.addListing(advanced.getStudentId(), skillId, SkillType.OFFER, Proficiency.ADVANCED, "a");

        assertEquals(1, skillService.teachersFor(skillId, Proficiency.ADVANCED).stream()
                .filter(listing -> listing.getSkillId() == skillId).count());
    }
}
