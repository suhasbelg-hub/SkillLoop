package com.skillloop.service;

import com.skillloop.dao.ReviewDAO;
import com.skillloop.dao.SessionDAO;
import com.skillloop.exception.AuthorizationException;
import com.skillloop.exception.DuplicateResourceException;
import com.skillloop.exception.NotFoundException;
import com.skillloop.exception.ValidationException;
import com.skillloop.model.LearningSession;
import com.skillloop.model.NotificationType;
import com.skillloop.model.Review;
import com.skillloop.model.SessionStatus;
import com.skillloop.util.DBConnection;
import com.skillloop.util.InputValidator;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Ratings and reviews.
 *
 * <p>A review can only be written for a COMPLETED session the student took part in, and only
 * once per (session, reviewer) - enforced by the service layer and by a unique constraint.</p>
 */
public class ReviewService {

    private final ReviewDAO reviewDAO = new ReviewDAO();
    private final SessionDAO sessionDAO = new SessionDAO();
    private final NotificationService notificationService = new NotificationService();

    /** Submits a rating (1..5) and optional feedback for a completed session. */
    public Review submit(long reviewerId, long sessionId, int rating, String comment) {
        InputValidator.validateRating(rating);
        String cleanComment = InputValidator.optionalText(comment, "Comment", 1000);
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                LearningSession session = sessionDAO.findByIdForUpdate(connection, sessionId)
                        .orElseThrow(() -> new NotFoundException("Session #" + sessionId + " does not exist."));
                if (session.getStatus() != SessionStatus.COMPLETED) {
                    throw new ValidationException("You can only review completed sessions.");
                }
                if (session.getTeacherId() != reviewerId && session.getLearnerId() != reviewerId) {
                    throw new AuthorizationException("You can only review sessions you took part in.");
                }
                if (reviewDAO.exists(connection, sessionId, reviewerId)) {
                    throw new DuplicateResourceException("You have already reviewed this session.");
                }
                long revieweeId = session.getTeacherId() == reviewerId
                        ? session.getLearnerId() : session.getTeacherId();
                Review review = new Review(sessionId, reviewerId, revieweeId, rating, cleanComment);
                review.setReviewId(reviewDAO.insert(connection, review));
                notificationService.notify(connection, revieweeId, "New review received",
                        "You received a " + rating + "-star review for session #" + sessionId + ".",
                        NotificationType.REVIEW_RECEIVED);
                connection.commit();
                return review;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save the review: " + e.getMessage(), e);
        }
    }

    /** Completed sessions of a student that were not reviewed by them yet. */
    public List<LearningSession> reviewableSessions(long studentId) {
        return sessionDAO.listForStudent(studentId, "ALL", SessionStatus.COMPLETED, 100).stream()
                .filter(session -> !reviewDAO.findBySessionAndReviewer(session.getSessionId(), studentId).isPresent())
                .toList();
    }

    public List<Review> reviewsFor(long studentId, int limit) {
        return reviewDAO.listForReviewee(studentId, limit);
    }

    public List<Review> reviewsBy(long studentId, int limit) {
        return reviewDAO.listByReviewer(studentId, limit);
    }

    public double averageRating(long studentId) {
        return reviewDAO.averageRating(studentId);
    }

    public int reviewCount(long studentId) {
        return reviewDAO.countFor(studentId);
    }
}
