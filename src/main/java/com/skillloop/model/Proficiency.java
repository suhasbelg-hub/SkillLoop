package com.skillloop.model;

/** Self-declared proficiency level of a student in a skill. */
public enum Proficiency {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED;

    /** Matching weight of this proficiency level (used by the ranking algorithm). */
    public int weight() {
        return switch (this) {
            case BEGINNER -> 8;
            case INTERMEDIATE -> 14;
            case ADVANCED -> 20;
        };
    }

    public static Proficiency fromDb(String value) {
        return value == null ? BEGINNER : Proficiency.valueOf(value.toUpperCase());
    }

    public static Proficiency fromLabel(String label) {
        return fromDb(label);
    }
}
