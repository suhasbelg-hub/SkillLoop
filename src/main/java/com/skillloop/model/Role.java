package com.skillloop.model;

/** Authorisation role of a user account. */
public enum Role {
    /** Ordinary student: may teach, learn, review and manage their own listings. */
    STUDENT,
    /** Administrator: additionally moderates content, resolves disputes and adjusts credits. */
    ADMIN;

    public static Role fromDb(String value) {
        return value == null ? STUDENT : Role.valueOf(value.toUpperCase());
    }
}
