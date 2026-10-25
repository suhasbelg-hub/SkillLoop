package com.skillloop.model;

/**
 * One row of the contribution leaderboard.
 *
 * <p>Ranking is based on <b>verified learning contribution</b> (completed teaching sessions,
 * distinct learners, hours taught and rating) - never on the credit balance, which would
 * reward students for hoarding credits instead of teaching.</p>
 */
public class LeaderboardEntry {

    private long studentId;
    private String fullName;
    private String department;
    private int teachingSessions;
    private double teachingHours;
    private int distinctLearners;
    private double avgRating;
    private int reviewCount;
    private int creditsEarned;
    private int learningSessions;
    private double score;
    private int rank;

    public LeaderboardEntry() {
        // required for DAO mapping
    }

    public long getStudentId() {
        return studentId;
    }

    public void setStudentId(long studentId) {
        this.studentId = studentId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public int getTeachingSessions() {
        return teachingSessions;
    }

    public void setTeachingSessions(int teachingSessions) {
        this.teachingSessions = teachingSessions;
    }

    public double getTeachingHours() {
        return teachingHours;
    }

    public void setTeachingHours(double teachingHours) {
        this.teachingHours = teachingHours;
    }

    public int getDistinctLearners() {
        return distinctLearners;
    }

    public void setDistinctLearners(int distinctLearners) {
        this.distinctLearners = distinctLearners;
    }

    public double getAvgRating() {
        return avgRating;
    }

    public void setAvgRating(double avgRating) {
        this.avgRating = avgRating;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(int reviewCount) {
        this.reviewCount = reviewCount;
    }

    public int getCreditsEarned() {
        return creditsEarned;
    }

    public void setCreditsEarned(int creditsEarned) {
        this.creditsEarned = creditsEarned;
    }

    public int getLearningSessions() {
        return learningSessions;
    }

    public void setLearningSessions(int learningSessions) {
        this.learningSessions = learningSessions;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }
}
