package com.skillloop.service;

import com.skillloop.dao.NotificationDAO;
import com.skillloop.model.Notification;
import com.skillloop.model.NotificationType;
import com.skillloop.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Creates and reads the in-app notifications of a student. */
public class NotificationService {

    private final NotificationDAO notificationDAO = new NotificationDAO();

    /** Inserts a notification inside an existing transaction. */
    public void notify(Connection connection, long studentId, String title, String message, NotificationType type)
            throws SQLException {
        notificationDAO.insert(connection, new Notification(studentId, title, message, type));
    }

    /** Inserts a notification in its own transaction (used outside booking flows). */
    public void notify(long studentId, String title, String message, NotificationType type) {
        try (Connection connection = DBConnection.getConnection()) {
            notify(connection, studentId, title, message, type);
        } catch (SQLException e) {
            // A failed notification must never break the main business operation.
            System.err.println("[SkillLoop] Could not store notification: " + e.getMessage());
        }
    }

    public List<Notification> listFor(long studentId, int limit) {
        return notificationDAO.listForStudent(studentId, limit);
    }

    public int countUnread(long studentId) {
        return notificationDAO.countUnread(studentId);
    }

    public void markRead(long notificationId, long studentId) {
        notificationDAO.markRead(notificationId, studentId);
    }

    public void markAllRead(long studentId) {
        notificationDAO.markAllRead(studentId);
    }
}
