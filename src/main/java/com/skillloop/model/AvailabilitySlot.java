package com.skillloop.model;

import java.time.LocalDateTime;

/** A time window published by a student during which they are willing to teach. */
public class AvailabilitySlot {

    private long slotId;
    private long studentId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private SlotStatus status = SlotStatus.AVAILABLE;
    private Long sessionId;
    private LocalDateTime createdAt;

    public AvailabilitySlot() {
        // required for DAO mapping
    }

    public AvailabilitySlot(long studentId, LocalDateTime startTime, LocalDateTime endTime) {
        this.studentId = studentId;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /** Length of the slot in minutes. */
    public int lengthMinutes() {
        if (startTime == null || endTime == null) {
            return 0;
        }
        return (int) java.time.Duration.between(startTime, endTime).toMinutes();
    }

    /** True when the given window fits completely inside this slot. */
    public boolean covers(LocalDateTime from, LocalDateTime to) {
        return startTime != null && endTime != null
                && !from.isBefore(startTime) && !to.isAfter(endTime);
    }

    public long getSlotId() {
        return slotId;
    }

    public void setSlotId(long slotId) {
        this.slotId = slotId;
    }

    public long getStudentId() {
        return studentId;
    }

    public void setStudentId(long studentId) {
        this.studentId = studentId;
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

    public SlotStatus getStatus() {
        return status;
    }

    public void setStatus(SlotStatus status) {
        this.status = status;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return com.skillloop.util.DateTimeUtil.format(startTime) + " - "
                + com.skillloop.util.DateTimeUtil.TIME.format(endTime);
    }
}
