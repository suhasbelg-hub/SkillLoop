package com.skillloop.service;

import com.skillloop.dao.AchievementDAO;
import com.skillloop.dao.ReviewDAO;
import com.skillloop.dao.SessionDAO;
import com.skillloop.model.Achievement;
import com.skillloop.model.NotificationType;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Awards badges after verified activity.
 *
 * <p>The award rules are deliberately simple and transparent, and every award is idempotent
 * (composite primary key on {@code student_achievements}).</p>
 */
public class AchievementService {

    /** Achievement codes (kept in sync with seed.sql). */
    public static final String FIRST_TEACHING_SESSION = "FIRST_TEACHING_SESSION";
    public static final String FIVE_SESSIONS_COMPLETED = "FIVE_SESSIONS_COMPLETED";
    public static final String HELPFUL_TEACHER = "HELPFUL_TEACHER";
    public static final String ACTIVE_LEARNER = "ACTIVE_LEARNER";
    public static final String CREDIT_MENTOR = "CREDIT_MENTOR";

    private static final int HELPFUL_TEACHER_MIN_REVIEWS = 5;
    private static final double HELPFUL_TEACHER_MIN_RATING = 4.0;
    private static final int MENTOR_MIN_CREDITS = 100;

    private final AchievementDAO achievementDAO = new AchievementDAO();
    private final SessionDAO sessionDAO = new SessionDAO();
    private final ReviewDAO reviewDAO = new ReviewDAO();
    private final NotificationService notificationService = new NotificationService();

    public List<Achievement> listAll() {
        return achievementDAO.findAll();
    }

    public List<Achievement> listForStudent(long studentId) {
        return achievementDAO.findForStudent(studentId);
    }

    /**
     * Evaluates and awards the badges of both participants of a completed session.
     * Must be called inside the settlement transaction.
     */
    public void evaluate(Connection connection, long teacherId, long learnerId, int creditsEarnedByTeacher)
            throws SQLException {
        if (exists(connection, FIRST_TEACHING_SESSION)) {
            long teachingCount = sessionDAO.countCompletedForTeacher(teacherId);
            awardIf(connection, teacherId, FIRST_TEACHING_SESSION, teachingCount >= 1);
            awardIf(connection, teacherId, FIVE_SESSIONS_COMPLETED, teachingCount >= 5);

            double rating = reviewDAO.averageRating(teacherId);
            int reviews = reviewDAO.countFor(teacherId);
            awardIf(connection, teacherId, HELPFUL_TEACHER,
                    reviews >= HELPFUL_TEACHER_MIN_REVIEWS && rating >= HELPFUL_TEACHER_MIN_RATING);
            awardIf(connection, teacherId, CREDIT_MENTOR, creditsEarnedByTeacher >= MENTOR_MIN_CREDITS);

            long learningCount = sessionDAO.countCompletedForLearner(learnerId);
            awardIf(connection, learnerId, ACTIVE_LEARNER, learningCount >= 5);
        }
    }

    private boolean exists(Connection connection, String code) throws SQLException {
        return achievementDAO.findIdByCode(connection, code) != null;
    }

    private void awardIf(Connection connection, long studentId, String code, boolean condition) throws SQLException {
        if (!condition) {
            return;
        }
        Long achievementId = achievementDAO.findIdByCode(connection, code);
        if (achievementId == null) {
            return;
        }
        if (achievementDAO.hasAchievement(studentId, achievementId)) {
            return;
        }
        achievementDAO.award(connection, studentId, achievementId);
        Achievement definition = achievementDAO.findAll().stream()
                .filter(a -> a.getAchievementId() == achievementId)
                .findFirst().orElse(null);
        String name = definition == null ? code : definition.getName();
        notificationService.notify(connection, studentId, "Achievement unlocked",
                "You earned the badge \"" + name + "\".", NotificationType.ACHIEVEMENT);
    }
}
