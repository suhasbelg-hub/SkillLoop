package com.skillloop.integration;

import com.skillloop.exception.AuthorizationException;
import com.skillloop.exception.DuplicateResourceException;
import com.skillloop.exception.ValidationException;
import com.skillloop.model.LearningSession;
import com.skillloop.model.Proficiency;
import com.skillloop.model.Review;
import com.skillloop.model.Skill;
import com.skillloop.model.SkillType;
import com.skillloop.model.Student;
import com.skillloop.service.AuthService;
import com.skillloop.service.BookingService;
import com.skillloop.service.ReviewService;
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

/** Review eligibility, duplicate prevention and rating aggregation. */
class ReviewIT {

    private static final String PASSWORD = "SkillLoop@123";
    private final List<Long> created = new ArrayList<>();
    private final AuthService authService = new AuthService();
    private final SkillService skillService = new SkillService();
    private final BookingService bookingService = new BookingService();
    private final ReviewService reviewService = new ReviewService();

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

    private LearningSession completedSession(Student teacher, Student learner, Skill taught) {
        skillService.addListing(teacher.getStudentId(), taught.getSkillId(), SkillType.OFFER,
                Proficiency.ADVANCED, "offering");
        skillService.addListing(learner.getStudentId(), taught.getSkillId(), SkillType.LEARN,
                Proficiency.BEGINNER, "want");
        LearningSession session = TestEnvironment.acceptedPastSession(bookingService, learner.getStudentId(),
                teacher.getStudentId(), taught.getSkillId(), 60);
        bookingService.confirmCompletion(session.getSessionId(), teacher.getStudentId());
        return bookingService.confirmCompletion(session.getSessionId(), learner.getStudentId());
    }

    @Test
    @DisplayName("Only completed sessions can be reviewed, and only by the participants")
    void eligibilityRules() {
        Skill taught = skillService.createSkill(TestEnvironment.uniqueSkillName() + "-R1", "Testing", "test");
        Student teacher = register("Teacher R1");
        Student learner = register("Learner R1");
        Student stranger = register("Stranger R1");
        LearningSession session = completedSession(teacher, learner, taught);

        assertThrows(AuthorizationException.class, () ->
                reviewService.submit(stranger.getStudentId(), session.getSessionId(), 5, "not my session"));
        assertThrows(ValidationException.class, () ->
                reviewService.submit(learner.getStudentId(), session.getSessionId(), 6, "out of range"));
        assertThrows(ValidationException.class, () ->
                reviewService.submit(learner.getStudentId(), session.getSessionId(), 0, "out of range"));
    }

    @Test
    @DisplayName("Both participants may review each other exactly once")
    void bothSidesMayReviewOnce() {
        Skill taught = skillService.createSkill(TestEnvironment.uniqueSkillName() + "-R2", "Testing", "test");
        Student teacher = register("Teacher R2");
        Student learner = register("Learner R2");
        LearningSession session = completedSession(teacher, learner, taught);

        Review fromLearner = reviewService.submit(learner.getStudentId(), session.getSessionId(), 5, "Great teacher");
        Review fromTeacher = reviewService.submit(teacher.getStudentId(), session.getSessionId(), 4, "Good learner");
        assertTrue(fromLearner.getReviewId() > 0);
        assertTrue(fromTeacher.getReviewId() > 0);
        assertEquals(teacher.getStudentId(), fromLearner.getRevieweeId());
        assertEquals(learner.getStudentId(), fromTeacher.getRevieweeId());

        assertThrows(DuplicateResourceException.class, () ->
                reviewService.submit(learner.getStudentId(), session.getSessionId(), 3, "twice"));
    }

    @Test
    @DisplayName("Ratings are aggregated into the teacher average")
    void averageRatingIsCalculated() {
        Skill taught = skillService.createSkill(TestEnvironment.uniqueSkillName() + "-R3", "Testing", "test");
        Student teacher = register("Teacher R3");
        Student first = register("Learner R3a");
        Student second = register("Learner R3b");

        LearningSession firstSession = completedSession(teacher, first, taught);
        LearningSession secondSession = completedSession(teacher, second, taught);

        reviewService.submit(first.getStudentId(), firstSession.getSessionId(), 5, "excellent");
        reviewService.submit(second.getStudentId(), secondSession.getSessionId(), 3, "okay");

        assertEquals(4.0, reviewService.averageRating(teacher.getStudentId()), 0.001);
        assertEquals(2, reviewService.reviewCount(teacher.getStudentId()));
        assertEquals(2, reviewService.reviewsFor(teacher.getStudentId(), 10).size());
    }

    @Test
    @DisplayName("Reviewable sessions disappear from the pending list after reviewing")
    void reviewableSessions() {
        Skill taught = skillService.createSkill(TestEnvironment.uniqueSkillName() + "-R4", "Testing", "test");
        Student teacher = register("Teacher R4");
        Student learner = register("Learner R4");
        LearningSession session = completedSession(teacher, learner, taught);

        assertTrue(reviewService.reviewableSessions(learner.getStudentId()).stream()
                .anyMatch(candidate -> candidate.getSessionId() == session.getSessionId()));
        reviewService.submit(learner.getStudentId(), session.getSessionId(), 5, "thanks");
        assertTrue(reviewService.reviewableSessions(learner.getStudentId()).stream()
                .noneMatch(candidate -> candidate.getSessionId() == session.getSessionId()));
    }
}
