package com.skillloop.service;

import com.skillloop.dao.ReviewDAO;
import com.skillloop.dao.SessionDAO;
import com.skillloop.dao.SkillDAO;
import com.skillloop.dao.StudentDAO;
import com.skillloop.model.LeaderboardEntry;
import com.skillloop.model.Review;
import com.skillloop.model.Student;
import com.skillloop.model.StudentProgress;
import com.skillloop.util.DBConnection;
import com.skillloop.util.InputValidator;
import com.skillloop.util.SessionContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Profile management, progress analytics and the contribution leaderboard. */
public class StudentService {

    private final StudentDAO studentDAO = new StudentDAO();
    private final SkillDAO skillDAO = new SkillDAO();
    private final SessionDAO sessionDAO = new SessionDAO();
    private final ReviewDAO reviewDAO = new ReviewDAO();

    /** Leaderboard weights - documented in docs/PROJECT_REPORT.md (ranking algorithm). */
    private static final double WEIGHT_SESSION = 8.0;
    private static final double WEIGHT_HOUR = 2.0;
    private static final double WEIGHT_LEARNER = 3.0;
    private static final double WEIGHT_RATING = 3.0;
    private static final double WEIGHT_CREDIT = 0.05;
    private static final double CAP_SESSIONS = 40.0;
    private static final double CAP_HOURS = 20.0;
    private static final double CAP_LEARNERS = 15.0;
    private static final double CAP_CREDITS = 10.0;

    public Optional<Student> findById(long studentId) {
        return studentDAO.findById(studentId);
    }

    /** Updates the profile of the logged-in student (name, department, semester, bio). */
    public Student updateProfile(long studentId, String fullName, String department, int semester, String bio) {
        Student student = studentDAO.findById(studentId)
                .orElseThrow(() -> new com.skillloop.exception.NotFoundException(
                        "Student #" + studentId + " does not exist."));
        student.setFullName(InputValidator.validateFullName(fullName));
        student.setDepartment(InputValidator.validateDepartment(department));
        student.setSemester(InputValidator.validateSemester(semester));
        student.setBio(InputValidator.validateBio(bio));
        studentDAO.updateProfile(student);
        if (SessionContext.isLoggedIn() && SessionContext.currentId() == studentId) {
            SessionContext.refresh(student);
        }
        return student;
    }

    /** Teaching and learning analytics of one student, read from the reporting views. */
    public StudentProgress getProgress(long studentId) {
        StudentProgress progress = new StudentProgress();
        progress.setStudentId(studentId);
        String sql = "SELECT COALESCE(t.completed_sessions, 0) AS teaching_sessions, "
                + "       COALESCE(t.teaching_hours, 0)        AS teaching_hours, "
                + "       COALESCE(t.credits_earned, 0)        AS credits_earned, "
                + "       COALESCE(t.distinct_learners, 0)     AS distinct_learners, "
                + "       COALESCE(l.completed_sessions, 0)    AS learning_sessions, "
                + "       COALESCE(l.learning_hours, 0)        AS learning_hours, "
                + "       COALESCE(l.credits_spent, 0)         AS credits_spent, "
                + "       COALESCE(l.distinct_teachers, 0)     AS distinct_teachers "
                + "FROM v_teaching_stats t JOIN v_learning_stats l ON l.student_id = t.student_id "
                + "WHERE t.student_id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    progress.setTeachingSessions(rs.getInt("teaching_sessions"));
                    progress.setTeachingHours(rs.getDouble("teaching_hours"));
                    progress.setCreditsEarned(rs.getInt("credits_earned"));
                    progress.setDistinctLearners(rs.getInt("distinct_learners"));
                    progress.setLearningSessions(rs.getInt("learning_sessions"));
                    progress.setLearningHours(rs.getDouble("learning_hours"));
                    progress.setCreditsSpent(rs.getInt("credits_spent"));
                    progress.setDistinctTeachers(rs.getInt("distinct_teachers"));
                }
            }
        } catch (SQLException e) {
            // The views are optional: fall back to queries on the base tables.
            return progressFallback(studentId, progress);
        }
        progress.setAvgRating(reviewDAO.averageRating(studentId));
        progress.setReviewCount(reviewDAO.countFor(studentId));
        progress.setOfferedSkills(skillDAO.listByStudent(studentId, com.skillloop.model.SkillType.OFFER).size());
        progress.setWantedSkills(skillDAO.listByStudent(studentId, com.skillloop.model.SkillType.LEARN).size());
        return progress;
    }

    private StudentProgress progressFallback(long studentId, StudentProgress progress) {
        progress.setTeachingSessions((int) sessionDAO.countCompletedForTeacher(studentId));
        progress.setLearningSessions((int) sessionDAO.countCompletedForLearner(studentId));
        progress.setAvgRating(reviewDAO.averageRating(studentId));
        progress.setReviewCount(reviewDAO.countFor(studentId));
        progress.setOfferedSkills(skillDAO.listByStudent(studentId, com.skillloop.model.SkillType.OFFER).size());
        progress.setWantedSkills(skillDAO.listByStudent(studentId, com.skillloop.model.SkillType.LEARN).size());
        return progress;
    }

    /**
     * Contribution leaderboard. The score rewards <b>verified teaching contribution</b>:
     * completed sessions, hours taught, distinct learners, rating and credits earned - and
     * never the current wallet balance.
     */
    public List<LeaderboardEntry> leaderboard(int limit) {
        List<LeaderboardEntry> entries = new com.skillloop.dao.AdminDAO().leaderboardData(limit);
        for (LeaderboardEntry entry : entries) {
            entry.setScore(score(entry));
        }
        entries.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).setRank(i + 1);
        }
        return entries;
    }

    /** Transparent ranking formula (see docs/PROJECT_REPORT.md). */
    public static double score(LeaderboardEntry entry) {
        double sessions = Math.min(entry.getTeachingSessions() * WEIGHT_SESSION, CAP_SESSIONS);
        double hours = Math.min(entry.getTeachingHours() * WEIGHT_HOUR, CAP_HOURS);
        double learners = Math.min(entry.getDistinctLearners() * WEIGHT_LEARNER, CAP_LEARNERS);
        double rating = entry.getAvgRating() * WEIGHT_RATING;
        double credits = Math.min(entry.getCreditsEarned() * WEIGHT_CREDIT, CAP_CREDITS);
        return Math.round((sessions + hours + learners + rating + credits) * 100.0) / 100.0;
    }

    /** Public profile of a student including rating and received reviews. */
    public StudentProfile profileOf(long studentId) {
        Student student = studentDAO.findById(studentId)
                .orElseThrow(() -> new com.skillloop.exception.NotFoundException(
                        "Student #" + studentId + " does not exist."));
        StudentProgress progress = getProgress(studentId);
        List<Review> reviews = reviewDAO.listForReviewee(studentId, 20);
        List<com.skillloop.model.StudentSkill> offered =
                skillDAO.listByStudent(studentId, com.skillloop.model.SkillType.OFFER);
        List<com.skillloop.model.StudentSkill> wanted =
                skillDAO.listByStudent(studentId, com.skillloop.model.SkillType.LEARN);
        return new StudentProfile(student, progress, reviews, offered, wanted);
    }

    /** Aggregated public profile data used by the profile dialogs. */
    public static final class StudentProfile {
        private final Student student;
        private final StudentProgress progress;
        private final List<Review> reviews;
        private final List<com.skillloop.model.StudentSkill> offered;
        private final List<com.skillloop.model.StudentSkill> wanted;

        public StudentProfile(Student student, StudentProgress progress, List<Review> reviews,
                              List<com.skillloop.model.StudentSkill> offered,
                              List<com.skillloop.model.StudentSkill> wanted) {
            this.student = student;
            this.progress = progress;
            this.reviews = reviews;
            this.offered = offered;
            this.wanted = wanted;
        }

        public Student getStudent() {
            return student;
        }

        public StudentProgress getProgress() {
            return progress;
        }

        public List<Review> getReviews() {
            return reviews;
        }

        public List<com.skillloop.model.StudentSkill> getOffered() {
            return offered;
        }

        public List<com.skillloop.model.StudentSkill> getWanted() {
            return wanted;
        }

        public List<String> summaryLines() {
            List<String> lines = new ArrayList<>();
            lines.add("Teaching sessions completed: " + progress.getTeachingSessions());
            lines.add("Learning sessions completed: " + progress.getLearningSessions());
            lines.add("Average rating: " + progress.getAvgRating() + " (" + progress.getReviewCount() + " reviews)");
            lines.add("Credits earned: " + progress.getCreditsEarned());
            lines.add("Credits spent: " + progress.getCreditsSpent());
            return lines;
        }
    }
}
