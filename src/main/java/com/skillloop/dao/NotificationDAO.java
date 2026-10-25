package com.skillloop.dao;

import com.skillloop.model.Notification;
import com.skillloop.model.NotificationType;
import com.skillloop.util.DBConnection;
import com.skillloop.util.DaoUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Persistence for the {@code notifications} table. */
public class NotificationDAO {

    private static final String BASE =
            "SELECT notification_id, student_id, title, message, notification_type, is_read, created_at "
            + "FROM notifications ";

    public long insert(Connection connection, Notification notification) throws SQLException {
        String sql = "INSERT INTO notifications (student_id, title, message, notification_type) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, notification.getStudentId());
            ps.setString(2, notification.getTitle());
            ps.setString(3, notification.getMessage());
            ps.setString(4, notification.getType().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    public long insert(Notification notification) {
        try (Connection connection = DBConnection.getConnection()) {
            return insert(connection, notification);
        } catch (SQLException e) {
            throw DaoUtils.wrap("saving the notification", e);
        }
    }

    public List<Notification> listForStudent(long studentId, int limit) {
        String sql = BASE + "WHERE student_id = ? ORDER BY is_read ASC, created_at DESC LIMIT ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Notification> notifications = new ArrayList<>();
                while (rs.next()) {
                    notifications.add(map(rs));
                }
                return notifications;
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading notifications", e);
        }
    }

    public int countUnread(long studentId) {
        String sql = "SELECT COUNT(*) FROM notifications WHERE student_id = ? AND is_read = FALSE";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("counting unread notifications", e);
        }
    }

    public void markRead(long notificationId, long studentId) {
        String sql = "UPDATE notifications SET is_read = TRUE WHERE notification_id = ? AND student_id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, notificationId);
            ps.setLong(2, studentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw DaoUtils.wrap("marking the notification as read", e);
        }
    }

    public void markAllRead(long studentId) {
        String sql = "UPDATE notifications SET is_read = TRUE WHERE student_id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw DaoUtils.wrap("marking notifications as read", e);
        }
    }

    private static Notification map(ResultSet rs) throws SQLException {
        Notification notification = new Notification();
        notification.setNotificationId(rs.getLong("notification_id"));
        notification.setStudentId(rs.getLong("student_id"));
        notification.setTitle(rs.getString("title"));
        notification.setMessage(rs.getString("message"));
        notification.setType(NotificationType.fromDb(rs.getString("notification_type")));
        notification.setRead(rs.getBoolean("is_read"));
        notification.setCreatedAt(DaoUtils.getDateTime(rs, "created_at"));
        return notification;
    }
}
