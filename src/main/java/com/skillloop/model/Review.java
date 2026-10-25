package com.skillloop.model;

import java.time.LocalDateTime;

/** Rating and written feedback submitted after a completed session. */
public class Review {

    private long reviewId;
    private long sessionId;
    private long reviewerId;
    private long revieweeId;
    private int rating;
    private String comment;
    private LocalDateTime createdAt;

    /** Denormalised join data for the UI. */
    private String reviewerName;
    private String revieweeName;
    private String skillName;

    public Review() {
        // required for DAO mapping
    }

    public Review(long sessionId, long reviewerId, long revieweeId, int rating, String comment) {
        this.sessionId = sessionId;
        this.reviewerId = reviewerId;
        this.revieweeId = revieweeId;
        this.rating = rating;
        this.comment = comment;
    }

    public long getReviewId() {
        return reviewId;
    }

    public void setReviewId(long reviewId) {
        this.reviewId = reviewId;
    }

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(long reviewerId) {
        this.reviewerId = reviewerId;
    }

    public long getRevieweeId() {
        return revieweeId;
    }

    public void setRevieweeId(long revieweeId) {
        this.revieweeId = revieweeId;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public void setReviewerName(String reviewerName) {
        this.reviewerName = reviewerName;
    }

    public String getRevieweeName() {
        return revieweeName;
    }

    public void setRevieweeName(String revieweeName) {
        this.revieweeName = revieweeName;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    /** Star text used by the Swing tables ("*****"). */
    public String stars() {
        return "*".repeat(Math.max(0, Math.min(5, rating)));
    }
}
