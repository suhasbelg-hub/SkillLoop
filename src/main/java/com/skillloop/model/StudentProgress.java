package com.skillloop.model;

/** Progress analytics of one student (teaching and learning side). */
public class StudentProgress {

    private long studentId;
    private int teachingSessions;
    private int learningSessions;
    private double teachingHours;
    private double learningHours;
    private int creditsEarned;
    private int creditsSpent;
    private double avgRating;
    private int reviewCount;
    private int distinctLearners;
    private int distinctTeachers;
    private int offeredSkills;
    private int wantedSkills;

    public long getStudentId() {
        return studentId;
    }

    public void setStudentId(long studentId) {
        this.studentId = studentId;
    }

    public int getTeachingSessions() {
        return teachingSessions;
    }

    public void setTeachingSessions(int teachingSessions) {
        this.teachingSessions = teachingSessions;
    }

    public int getLearningSessions() {
        return learningSessions;
    }

    public void setLearningSessions(int learningSessions) {
        this.learningSessions = learningSessions;
    }

    public double getTeachingHours() {
        return teachingHours;
    }

    public void setTeachingHours(double teachingHours) {
        this.teachingHours = teachingHours;
    }

    public double getLearningHours() {
        return learningHours;
    }

    public void setLearningHours(double learningHours) {
        this.learningHours = learningHours;
    }

    public int getCreditsEarned() {
        return creditsEarned;
    }

    public void setCreditsEarned(int creditsEarned) {
        this.creditsEarned = creditsEarned;
    }

    public int getCreditsSpent() {
        return creditsSpent;
    }

    public void setCreditsSpent(int creditsSpent) {
        this.creditsSpent = creditsSpent;
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

    public int getDistinctLearners() {
        return distinctLearners;
    }

    public void setDistinctLearners(int distinctLearners) {
        this.distinctLearners = distinctLearners;
    }

    public int getDistinctTeachers() {
        return distinctTeachers;
    }

    public void setDistinctTeachers(int distinctTeachers) {
        this.distinctTeachers = distinctTeachers;
    }

    public int getOfferedSkills() {
        return offeredSkills;
    }

    public void setOfferedSkills(int offeredSkills) {
        this.offeredSkills = offeredSkills;
    }

    public int getWantedSkills() {
        return wantedSkills;
    }

    public void setWantedSkills(int wantedSkills) {
        this.wantedSkills = wantedSkills;
    }

    /** Total hours spent learning and teaching on SkillLoop. */
    public double totalHours() {
        return teachingHours + learningHours;
    }
}
