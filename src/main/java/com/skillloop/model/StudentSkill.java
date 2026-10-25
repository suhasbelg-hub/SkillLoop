package com.skillloop.model;

import java.time.LocalDateTime;

/** A student's listing: a skill they offer (teach) or want to learn. */
public class StudentSkill {

    private long studentSkillId;
    private long studentId;
    private long skillId;
    private SkillType skillType = SkillType.OFFER;
    private Proficiency proficiency = Proficiency.BEGINNER;
    private String description;
    private boolean active = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Denormalised join data used by the UI tables. */
    private String skillName;
    private String category;
    private String studentName;
    private String department;

    public StudentSkill() {
        // required for DAO mapping
    }

    public long getStudentSkillId() {
        return studentSkillId;
    }

    public void setStudentSkillId(long studentSkillId) {
        this.studentSkillId = studentSkillId;
    }

    public long getStudentId() {
        return studentId;
    }

    public void setStudentId(long studentId) {
        this.studentId = studentId;
    }

    public long getSkillId() {
        return skillId;
    }

    public void setSkillId(long skillId) {
        this.skillId = skillId;
    }

    public SkillType getSkillType() {
        return skillType;
    }

    public void setSkillType(SkillType skillType) {
        this.skillType = skillType;
    }

    public Proficiency getProficiency() {
        return proficiency;
    }

    public void setProficiency(Proficiency proficiency) {
        this.proficiency = proficiency;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
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

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    @Override
    public String toString() {
        return (skillName == null ? "skill #" + skillId : skillName) + " - " + skillType
                + " (" + proficiency + ")";
    }
}
