package com.skillloop.dao;

import com.skillloop.model.Achievement;
import com.skillloop.util.DBConnection;
import com.skillloop.util.DaoUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Persistence for achievements and the badges a student has earned. */
public class AchievementDAO {

    public long insertDefinition(Connection connection, Achievement achievement) throws SQLException {
        String sql = "INSERT INTO achievements (code, name, description, icon) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), icon = VALUES(icon)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, achievement.getCode());
            ps.setString(2, achievement.getName());
            ps.setString(3, achievement.getDescription());
            ps.setString(4, achievement.getIcon());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    public List<Achievement> findAll() {
        String sql = "SELECT achievement_id, code, name, description, icon FROM achievements ORDER BY achievement_id";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Achievement> achievements = new ArrayList<>();
            while (rs.next()) {
                achievements.add(map(rs));
            }
            return achievements;
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading achievements", e);
        }
    }

    public Long findIdByCode(Connection connection, String code) throws SQLException {
        String sql = "SELECT achievement_id FROM achievements WHERE code = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    /** Badges earned by a student. */
    public List<Achievement> findForStudent(long studentId) {
        String sql = "SELECT a.achievement_id, a.code, a.name, a.description, a.icon, sa.awarded_at "
                + "FROM student_achievements sa JOIN achievements a ON a.achievement_id = sa.achievement_id "
                + "WHERE sa.student_id = ? ORDER BY sa.awarded_at DESC";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Achievement> achievements = new ArrayList<>();
                while (rs.next()) {
                    Achievement achievement = map(rs);
                    achievement.setAwardedAt(DaoUtils.getDateTime(rs, "awarded_at"));
                    achievements.add(achievement);
                }
                return achievements;
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading your achievements", e);
        }
    }

    /**
     * Awards a badge. The composite primary key makes the operation idempotent: awarding the
     * same badge twice is silently ignored instead of failing.
     */
    public void award(Connection connection, long studentId, long achievementId) throws SQLException {
        String sql = "INSERT IGNORE INTO student_achievements (student_id, achievement_id) VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setLong(2, achievementId);
            ps.executeUpdate();
        }
    }

    public boolean hasAchievement(long studentId, long achievementId) {
        String sql = "SELECT COUNT(*) FROM student_achievements WHERE student_id = ? AND achievement_id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setLong(2, achievementId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("checking the achievement", e);
        }
    }

    private static Achievement map(ResultSet rs) throws SQLException {
        Achievement achievement = new Achievement();
        achievement.setAchievementId(rs.getLong("achievement_id"));
        achievement.setCode(rs.getString("code"));
        achievement.setName(rs.getString("name"));
        achievement.setDescription(rs.getString("description"));
        achievement.setIcon(rs.getString("icon"));
        return achievement;
    }
}
