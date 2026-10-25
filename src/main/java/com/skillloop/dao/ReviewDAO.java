package com.skillloop.dao;

import com.skillloop.exception.DuplicateResourceException;
import com.skillloop.model.Review;
import com.skillloop.util.DBConnection;
import com.skillloop.util.DaoUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Persistence for the {@code reviews} table. */
public class ReviewDAO {

    private static final String BASE =
            "SELECT r.review_id, r.session_id, r.reviewer_id, r.reviewee_id, r.rating, r.comment, r.created_at, "
            + "rv.full_name AS reviewer_name, re.full_name AS reviewee_name, s.skill_name "
            + "FROM reviews r "
            + "JOIN students rv ON rv.student_id = r.reviewer_id "
            + "JOIN students re ON re.student_id = r.reviewee_id "
            + "JOIN learning_sessions ls ON ls.session_id = r.session_id "
            + "JOIN skills s ON s.skill_id = ls.skill_id ";

    public long insert(Connection connection, Review review) throws SQLException {
        String sql = "INSERT INTO reviews (session_id, reviewer_id, reviewee_id, rating, comment) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, review.getSessionId());
            ps.setLong(2, review.getReviewerId());
            ps.setLong(3, review.getRevieweeId());
            ps.setInt(4, review.getRating());
            DaoUtils.setNullableString(ps, 5, review.getComment());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new SQLException("Inserting the review did not return a generated key.");
        }
    }

    public long insert(Review review) {
        try (Connection connection = DBConnection.getConnection()) {
            return insert(connection, review);
        } catch (SQLException e) {
            if (DaoUtils.isDuplicateKey(e)) {
                throw new DuplicateResourceException("You have already reviewed this session.", e);
            }
            throw DaoUtils.wrap("saving the review", e);
        }
    }

    public boolean exists(Connection connection, long sessionId, long reviewerId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM reviews WHERE session_id = ? AND reviewer_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            ps.setLong(2, reviewerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public Optional<Review> findBySessionAndReviewer(long sessionId, long reviewerId) {
        String sql = BASE + "WHERE r.session_id = ? AND r.reviewer_id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            ps.setLong(2, reviewerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading the review", e);
        }
    }

    /** All reviews received by a student (shown on their public profile). */
    public List<Review> listForReviewee(long revieweeId, int limit) {
        String sql = BASE + "WHERE r.reviewee_id = ? ORDER BY r.created_at DESC LIMIT ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, revieweeId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Review> reviews = new ArrayList<>();
                while (rs.next()) {
                    reviews.add(map(rs));
                }
                return reviews;
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading the reviews", e);
        }
    }

    /** Reviews written by a student (used for the "review pending" list). */
    public List<Review> listByReviewer(long reviewerId, int limit) {
        String sql = BASE + "WHERE r.reviewer_id = ? ORDER BY r.created_at DESC LIMIT ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, reviewerId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Review> reviews = new ArrayList<>();
                while (rs.next()) {
                    reviews.add(map(rs));
                }
                return reviews;
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading your reviews", e);
        }
    }

    public double averageRating(long studentId) {
        String sql = "SELECT COALESCE(ROUND(AVG(rating), 2), 0) FROM reviews WHERE reviewee_id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("computing the average rating", e);
        }
    }

    public int countFor(long studentId) {
        String sql = "SELECT COUNT(*) FROM reviews WHERE reviewee_id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("counting reviews", e);
        }
    }

    private static Review map(ResultSet rs) throws SQLException {
        Review review = new Review();
        review.setReviewId(rs.getLong("review_id"));
        review.setSessionId(rs.getLong("session_id"));
        review.setReviewerId(rs.getLong("reviewer_id"));
        review.setRevieweeId(rs.getLong("reviewee_id"));
        review.setRating(rs.getInt("rating"));
        review.setComment(rs.getString("comment"));
        review.setCreatedAt(DaoUtils.getDateTime(rs, "created_at"));
        review.setReviewerName(rs.getString("reviewer_name"));
        review.setRevieweeName(rs.getString("reviewee_name"));
        review.setSkillName(rs.getString("skill_name"));
        return review;
    }
}
