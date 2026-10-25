package com.skillloop.model;

import java.time.LocalDateTime;
import java.util.Objects;

/** An entry of the shared skill catalogue. */
public class Skill {

    private long skillId;
    private String skillName;
    private String category;
    private String description;
    private LocalDateTime createdAt;

    /** Aggregated demand information (filled by {@code v_skill_demand} for the explore screen). */
    private int teachersOffering;
    private int learnersWanting;
    private int completedSessions;

    public Skill() {
        // required for DAO mapping
    }

    public Skill(long skillId, String skillName, String category, String description) {
        this.skillId = skillId;
        this.skillName = skillName;
        this.category = category;
        this.description = description;
    }

    public long getSkillId() {
        return skillId;
    }

    public void setSkillId(long skillId) {
        this.skillId = skillId;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public int getTeachersOffering() {
        return teachersOffering;
    }

    public void setTeachersOffering(int teachersOffering) {
        this.teachersOffering = teachersOffering;
    }

    public int getLearnersWanting() {
        return learnersWanting;
    }

    public void setLearnersWanting(int learnersWanting) {
        this.learnersWanting = learnersWanting;
    }

    public int getCompletedSessions() {
        return completedSessions;
    }

    public void setCompletedSessions(int completedSessions) {
        this.completedSessions = completedSessions;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Skill that)) {
            return false;
        }
        return skillId != 0 && skillId == that.skillId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(skillId);
    }

    @Override
    public String toString() {
        return skillName;
    }
}
