package com.skillloop.model;

/** Lifecycle state of a student account. */
public enum AccountStatus {
    ACTIVE,
    SUSPENDED,
    INACTIVE;

    public static AccountStatus fromDb(String value) {
        return value == null ? ACTIVE : AccountStatus.valueOf(value.toUpperCase());
    }
}
