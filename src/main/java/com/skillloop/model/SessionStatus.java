package com.skillloop.model;

/** Lifecycle of a learning session. */
public enum SessionStatus {
    /** Learner requested the booking; credits are held but not yet transferred. */
    REQUESTED,
    /** Teacher accepted the booking. */
    ACCEPTED,
    /** Teacher rejected the booking; held credits were released. */
    REJECTED,
    /** Cancelled by either side before completion; held credits were released. */
    CANCELLED,
    /** Both participants confirmed; credits were transferred. */
    COMPLETED,
    /** A participant raised a dispute; an administrator must resolve it. */
    DISPUTED;

    public static SessionStatus fromDb(String value) {
        return value == null ? REQUESTED : SessionStatus.valueOf(value.toUpperCase());
    }

    /** Statuses that still block a teacher's time slot. */
    public boolean blocksCalendar() {
        return this == REQUESTED || this == ACCEPTED || this == DISPUTED;
    }
}
