package com.skillloop.model;

/** Whether a student offers a skill (teaches it) or wants to learn it. */
public enum SkillType {
    OFFER,
    LEARN;

    public static SkillType fromDb(String value) {
        return value == null ? LEARN : SkillType.valueOf(value.toUpperCase());
    }
}
