package com.skillloop.model;

import java.time.LocalDateTime;

/** An in-app message delivered to a student. */
public class Notification {

    private long notificationId;
    private long studentId;
    private String title;
    private String message;
    private NotificationType type = NotificationType.ADMIN_MESSAGE;
    private boolean read;
    private LocalDateTime createdAt;

    public Notification() {
        // required for DAO mapping
    }

    public Notification(long studentId, String title, String message, NotificationType type) {
        this.studentId = studentId;
        this.title = title;
        this.message = message;
        this.type = type;
    }

    public long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(long notificationId) {
        this.notificationId = notificationId;
    }

    public long getStudentId() {
        return studentId;
    }

    public void setStudentId(long studentId) {
        this.studentId = studentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
