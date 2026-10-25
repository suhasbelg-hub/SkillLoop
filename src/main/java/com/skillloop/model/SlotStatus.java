package com.skillloop.model;

/** State of an availability slot published by a student. */
public enum SlotStatus {
    AVAILABLE,
    BOOKED,
    BLOCKED;

    public static SlotStatus fromDb(String value) {
        return value == null ? AVAILABLE : SlotStatus.valueOf(value.toUpperCase());
    }
}
