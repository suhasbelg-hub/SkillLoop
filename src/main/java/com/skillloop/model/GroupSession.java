package com.skillloop.model;

import java.time.LocalDateTime;

/** A group learning session: one teacher, several learners, a credit cost per learner. */
public class GroupSession {

    private long groupId;
    private long teacherId;
    private long skillId;
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int capacity = 5;
    private int creditCostPerLearner = 10;
    private GroupStatus status = GroupStatus.OPEN;
    private LocalDateTime createdAt;

    /** Counters loaded from {@code v_group_session_overview}. */
    private int enrolled;
    private String teacherName;
    private String skillName;

    public GroupSession() {
        // required for DAO mapping
    }

    public int freeSeats() {
        return Math.max(0, capacity - enrolled);
    }

    public long getGroupId() {
        return groupId;
    }

    public void setGroupId(long groupId) {
        this.groupId = groupId;
    }

    public long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(long teacherId) {
        this.teacherId = teacherId;
    }

    public long getSkillId() {
        return skillId;
    }

    public void setSkillId(long skillId) {
        this.skillId = skillId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getCreditCostPerLearner() {
        return creditCostPerLearner;
    }

    public void setCreditCostPerLearner(int creditCostPerLearner) {
        this.creditCostPerLearner = creditCostPerLearner;
    }

    public GroupStatus getStatus() {
        return status;
    }

    public void setStatus(GroupStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public int getEnrolled() {
        return enrolled;
    }

    public void setEnrolled(int enrolled) {
        this.enrolled = enrolled;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }
}
