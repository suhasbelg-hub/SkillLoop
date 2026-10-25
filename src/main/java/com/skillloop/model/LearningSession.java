package com.skillloop.model;

import java.time.LocalDateTime;

/** A booking: one teacher, one learner, one skill, one time window. */
public class LearningSession {

    private long sessionId;
    private long teacherId;
    private long learnerId;
    private long skillId;
    private Long slotId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int durationMinutes;
    private int creditCost;
    private SessionStatus status = SessionStatus.REQUESTED;
    private boolean teacherConfirmation;
    private boolean learnerConfirmation;
    private String learnerNote;
    private String resolutionNote;
    private Long resolvedBy;
    private LocalDateTime settledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Denormalised join data used by the UI tables. */
    private String teacherName;
    private String learnerName;
    private String skillName;

    public LearningSession() {
        // required for DAO mapping
    }

    /** True when both participants confirmed completion (prerequisite for the payout). */
    public boolean isFullyConfirmed() {
        return teacherConfirmation && learnerConfirmation;
    }

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(long teacherId) {
        this.teacherId = teacherId;
    }

    public long getLearnerId() {
        return learnerId;
    }

    public void setLearnerId(long learnerId) {
        this.learnerId = learnerId;
    }

    public long getSkillId() {
        return skillId;
    }

    public void setSkillId(long skillId) {
        this.skillId = skillId;
    }

    public Long getSlotId() {
        return slotId;
    }

    public void setSlotId(Long slotId) {
        this.slotId = slotId;
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

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public int getCreditCost() {
        return creditCost;
    }

    public void setCreditCost(int creditCost) {
        this.creditCost = creditCost;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public boolean isTeacherConfirmation() {
        return teacherConfirmation;
    }

    public void setTeacherConfirmation(boolean teacherConfirmation) {
        this.teacherConfirmation = teacherConfirmation;
    }

    public boolean isLearnerConfirmation() {
        return learnerConfirmation;
    }

    public void setLearnerConfirmation(boolean learnerConfirmation) {
        this.learnerConfirmation = learnerConfirmation;
    }

    public String getLearnerNote() {
        return learnerNote;
    }

    public void setLearnerNote(String learnerNote) {
        this.learnerNote = learnerNote;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public void setResolutionNote(String resolutionNote) {
        this.resolutionNote = resolutionNote;
    }

    public Long getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(Long resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public LocalDateTime getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(LocalDateTime settledAt) {
        this.settledAt = settledAt;
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

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public String getLearnerName() {
        return learnerName;
    }

    public void setLearnerName(String learnerName) {
        this.learnerName = learnerName;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    @Override
    public String toString() {
        return "Session #" + sessionId + " " + status + " (" + skillName + ")";
    }
}
