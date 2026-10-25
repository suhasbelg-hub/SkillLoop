package com.skillloop.model;

/** Lifecycle of a group learning session. */
public enum GroupStatus {
    OPEN,
    CLOSED,
    COMPLETED,
    CANCELLED;

    public static GroupStatus fromDb(String value) {
        return value == null ? OPEN : GroupStatus.valueOf(value.toUpperCase());
    }
}
