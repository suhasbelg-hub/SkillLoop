package com.skillloop.service;

import com.skillloop.dao.AvailabilityDAO;
import com.skillloop.dao.ReviewDAO;
import com.skillloop.dao.SessionDAO;
import com.skillloop.dao.SkillDAO;
import com.skillloop.dao.StudentDAO;
import com.skillloop.model.MatchResult;
import com.skillloop.model.Proficiency;
import com.skillloop.model.Student;
import com.skillloop.model.StudentSkill;
import com.skillloop.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Smart skill matching.
 *
 * <h2>Ranking algorithm (transparent, no AI/ML)</h2>
 * <pre>
 *   score = reciprocalBonus   (0..30)  candidate wants to learn a skill the learner offers
 *         + proficiencyPoints (8/14/20) BEGINNER / INTERMEDIATE / ADVANCED
 *         + availabilityPoints(0..15)  bookable slot within the next 14 days
 *         + ratingPoints      (0..15)  average rating / 5 * 15 (unrated teachers: neutral 7.5)
 *         + experiencePoints  (0..10)  min(completed teaching sessions, 10)
 *         + interestPoints    (0..10)  the candidate teaches a skill the learner wants
 *   ------------------------------------------------------------------
 *   maximum                   = 100
 * </pre>
 * The SQL side pre-filters candidates (skill, category, proficiency, active accounts);
 * the scoring runs in Java so that every component can be shown to the user.
 */
public class MatchingService {

    /** Points awarded for a fully reciprocal (swap) relationship. */
    public static final int MAX_RECIPROCAL = 30;
    /** Points awarded when the teacher has a bookable slot in the next two weeks. */
    public static final int MAX_AVAILABILITY = 15;
    /** Points awarded for the teacher's rating. */
    public static final int MAX_RATING = 15;
    /** Points awarded for verified teaching experience. */
    public static final int MAX_EXPERIENCE = 10;
    /** Points awarded when the teacher covers a skill the learner wants to learn. */
    public static final int MAX_INTEREST = 10;
    /** Neutral rating score for teachers without reviews. */
    public static final double NEUTRAL_RATING_POINTS = 7.5;

    private final SkillDAO skillDAO = new SkillDAO();
    private final StudentDAO studentDAO = new StudentDAO();
    private final ReviewDAO reviewDAO = new ReviewDAO();
    private final SessionDAO sessionDAO = new SessionDAO();
    private final AvailabilityDAO availabilityDAO = new AvailabilityDAO();

    /**
     * Recommends teachers for a skill.
     *
     * @param learnerId      student who is looking for a teacher (excluded from the results)
     * @param skillId        requested skill, or null to search across the learner's wanted skills
     * @param minProficiency minimum proficiency, may be null
     */
    public List<MatchResult> recommendTeachers(long learnerId, Long skillId, Proficiency minProficiency, int limit) {
        List<MatchResult> results = new ArrayList<>();
        Set<Long> wantedSkillIds = wantedSkillIds(learnerId);
        Set<Long> offeredSkillIds = offeredSkillIds(learnerId);

        List<Long> skillIds = new ArrayList<>();
        if (skillId != null) {
            skillIds.add(skillId);
        } else {
            skillIds.addAll(wantedSkillIds);
            if (skillIds.isEmpty()) {
                skillIds.addAll(offeredSkillIds);
            }
        }

        for (Long candidateSkillId : skillIds) {
            for (StudentSkill listing : skillDAO.findTeachers(candidateSkillId, minProficiency)) {
                if (listing.getStudentId() == learnerId) {
                    continue; // never recommend yourself
                }
                MatchResult result = score(learnerId, listing, wantedSkillIds, offeredSkillIds);
                if (result != null) {
                    results.add(result);
                }
            }
        }
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (results.size() > limit) {
            return new ArrayList<>(results.subList(0, limit));
        }
        return results;
    }

    /**
     * Reciprocal ("swap") partners: candidates who teach a skill the student wants <b>and</b>
     * want to learn a skill the student teaches. Implemented with correlated EXISTS subqueries.
     */
    public List<MatchResult> findReciprocalPartners(long studentId, int limit) {
        String sql = "SELECT DISTINCT c.student_id "
                + "FROM students c "
                + "WHERE c.student_id <> ? AND c.account_status = 'ACTIVE' "
                + "  AND EXISTS (SELECT 1 FROM student_skills co "
                + "              WHERE co.student_id = c.student_id AND co.skill_type = 'OFFER' AND co.is_active = TRUE "
                + "                AND co.skill_id IN (SELECT l.skill_id FROM student_skills l "
                + "                                    WHERE l.student_id = ? AND l.skill_type = 'LEARN' AND l.is_active = TRUE)) "
                + "  AND EXISTS (SELECT 1 FROM student_skills cw "
                + "              WHERE cw.student_id = c.student_id AND cw.skill_type = 'LEARN' AND cw.is_active = TRUE "
                + "                AND cw.skill_id IN (SELECT o.skill_id FROM student_skills o "
                + "                                    WHERE o.student_id = ? AND o.skill_type = 'OFFER' AND o.is_active = TRUE))";
        List<Long> partnerIds = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setLong(2, studentId);
            ps.setLong(3, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    partnerIds.add(rs.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not search for reciprocal partners: " + e.getMessage(), e);
        }

        Set<Long> wantedSkillIds = wantedSkillIds(studentId);
        Set<Long> offeredSkillIds = offeredSkillIds(studentId);
        List<MatchResult> results = new ArrayList<>();
        for (Long partnerId : partnerIds) {
            List<StudentSkill> offers = skillDAO.listByStudent(partnerId, com.skillloop.model.SkillType.OFFER);
            for (StudentSkill offer : offers) {
                if (!offer.isActive() || !wantedSkillIds.contains(offer.getSkillId())) {
                    continue;
                }
                MatchResult result = score(studentId, offer, wantedSkillIds, offeredSkillIds);
                if (result != null) {
                    results.add(result);
                }
            }
        }
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (results.size() > limit) {
            return new ArrayList<>(results.subList(0, limit));
        }
        return results;
    }

    /** Computes the transparent score of one candidate listing. */
    private MatchResult score(long learnerId, StudentSkill candidateListing, Set<Long> wantedSkillIds,
                              Set<Long> offeredSkillIds) {
        long teacherId = candidateListing.getStudentId();
        Optional<Student> teacher = studentDAO.findById(teacherId);
        if (teacher.isEmpty() || !teacher.get().isActive()) {
            return null;
        }
        MatchResult result = new MatchResult();
        result.setTeacherId(teacherId);
        result.setTeacherName(candidateListing.getStudentName());
        result.setDepartment(candidateListing.getDepartment());
        result.setSkillId(candidateListing.getSkillId());
        result.setSkillName(candidateListing.getSkillName());
        result.setProficiency(candidateListing.getProficiency());

        // --- rating -------------------------------------------------------------------
        double rating = reviewDAO.averageRating(teacherId);
        int reviews = reviewDAO.countFor(teacherId);
        result.setAvgRating(rating);
        result.setReviewCount(reviews);
        if (reviews == 0) {
            result.setRatingPoints(NEUTRAL_RATING_POINTS);
            result.addReason("not rated yet (neutral score)");
        } else {
            result.setRatingPoints(rating / 5.0 * MAX_RATING);
            result.addReason(String.format("%.2f/5 from %d reviews", rating, reviews));
        }

        // --- proficiency --------------------------------------------------------------
        result.setProficiencyPoints(candidateListing.getProficiency().weight());
        result.addReason(candidateListing.getProficiency().name().toLowerCase() + " level");

        // --- availability -------------------------------------------------------------
        boolean hasSlot = !availabilityDAO.listBookableForTeacher(teacherId).isEmpty();
        result.setUpcomingAvailability(hasSlot);
        result.setAvailabilityPoints(hasSlot ? MAX_AVAILABILITY : 0);
        result.addReason(hasSlot ? "has bookable slots" : "no open slots");

        // --- experience ---------------------------------------------------------------
        long completed = sessionDAO.countCompletedForTeacher(teacherId);
        result.setCompletedTeaching((int) completed);
        result.setExperiencePoints(Math.min(completed, MAX_EXPERIENCE));
        result.addReason(completed + " verified sessions taught");

        // --- reciprocity: the candidate wants something the learner can teach ----------
        int reciprocalPoints = 0;
        for (StudentSkill theirWish : skillDAO.listByStudent(teacherId, com.skillloop.model.SkillType.LEARN)) {
            if (theirWish.isActive() && offeredSkillIds.contains(theirWish.getSkillId())) {
                reciprocalPoints = MAX_RECIPROCAL;
                result.setReciprocal(true);
                result.addReason("wants to learn " + theirWish.getSkillName() + " from you (reciprocal)");
                break;
            }
        }
        result.setReciprocalBonus(reciprocalPoints);

        // --- interest: the candidate teaches something the learner wants ---------------
        int interestPoints = wantedSkillIds.contains(candidateListing.getSkillId()) ? MAX_INTEREST : 0;
        if (interestPoints > 0) {
            result.addReason("teaches a skill on your wish list");
        }

        result.setScore(result.getReciprocalBonus() + result.getProficiencyPoints()
                + result.getAvailabilityPoints() + result.getRatingPoints()
                + result.getExperiencePoints() + interestPoints);
        return result;
    }

    private Set<Long> wantedSkillIds(long studentId) {
        Set<Long> ids = new HashSet<>();
        for (StudentSkill listing : skillDAO.listByStudent(studentId, com.skillloop.model.SkillType.LEARN)) {
            if (listing.isActive()) {
                ids.add(listing.getSkillId());
            }
        }
        return ids;
    }

    private Set<Long> offeredSkillIds(long studentId) {
        Set<Long> ids = new HashSet<>();
        for (StudentSkill listing : skillDAO.listByStudent(studentId, com.skillloop.model.SkillType.OFFER)) {
            if (listing.isActive()) {
                ids.add(listing.getSkillId());
            }
        }
        return ids;
    }
}
