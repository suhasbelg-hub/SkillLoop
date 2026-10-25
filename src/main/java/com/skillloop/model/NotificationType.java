package com.skillloop.model;

/** Categories used by the in-app notification centre. */
public enum NotificationType {
    BOOKING_REQUEST,
    BOOKING_ACCEPTED,
    BOOKING_REJECTED,
    SESSION_CANCELLED,
    SESSION_COMPLETED,
    CREDIT_TRANSFER,
    REVIEW_RECEIVED,
    ACHIEVEMENT,
    DISPUTE_RAISED,
    DISPUTE_RESOLVED,
    ADMIN_MESSAGE,
    GROUP_SESSION;

    public static NotificationType fromDb(String value) {
        return value == null ? ADMIN_MESSAGE : NotificationType.valueOf(value.toUpperCase());
    }
}
