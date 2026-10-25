package com.skillloop.model;

/** Enrolment state of a student in a group session. */
public enum ParticipantStatus {
    JOINED,
    CANCELLED,
    COMPLETED;

    public static ParticipantStatus fromDb(String value) {
        return value == null ? JOINED : ParticipantStatus.valueOf(value.toUpperCase());
    }
}
