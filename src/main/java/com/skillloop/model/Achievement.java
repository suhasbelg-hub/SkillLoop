package com.skillloop.model;

import java.time.LocalDateTime;

/** Definition of a badge a student can earn (master data). */
public class Achievement {

    private long achievementId;
    private String code;
    private String name;
    private String description;
    private String icon = "*";

    /** Only present when the achievement is loaded together with a student's awards. */
    private LocalDateTime awardedAt;

    public Achievement() {
        // required for DAO mapping
    }

    public Achievement(String code, String name, String description, String icon) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.icon = icon;
    }

    public long getAchievementId() {
        return achievementId;
    }

    public void setAchievementId(long achievementId) {
        this.achievementId = achievementId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public LocalDateTime getAwardedAt() {
        return awardedAt;
    }

    public void setAwardedAt(LocalDateTime awardedAt) {
        this.awardedAt = awardedAt;
    }

    @Override
    public String toString() {
        return icon + "  " + name;
    }
}
