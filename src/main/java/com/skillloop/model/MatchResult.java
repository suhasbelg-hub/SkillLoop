package com.skillloop.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One recommendation produced by the matching engine.
 *
 * <p>The score is transparent and fully documented (see docs/PROJECT_REPORT.md): every
 * component is exposed so the UI can explain <em>why</em> a peer was recommended.</p>
 */
public class MatchResult {

    private long teacherId;
    private String teacherName;
    private String department;
    private long skillId;
    private String skillName;
    private Proficiency proficiency = Proficiency.BEGINNER;
    private double avgRating;
    private int reviewCount;
    private int completedTeaching;
    private boolean upcomingAvailability;
    private boolean reciprocal;
    private int reciprocalBonus;
    private int proficiencyPoints;
    private int availabilityPoints;
    private double ratingPoints;
    private double experiencePoints;
    private double score;
    private final List<String> reasons = new ArrayList<>();

    public long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(long teacherId) {
        this.teacherId = teacherId;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
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

    public Proficiency getProficiency() {
        return proficiency;
    }

    public void setProficiency(Proficiency proficiency) {
        this.proficiency = proficiency;
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

    public int getCompletedTeaching() {
        return completedTeaching;
    }

    public void setCompletedTeaching(int completedTeaching) {
        this.completedTeaching = completedTeaching;
    }

    public boolean isUpcomingAvailability() {
        return upcomingAvailability;
    }

    public void setUpcomingAvailability(boolean upcomingAvailability) {
        this.upcomingAvailability = upcomingAvailability;
    }

    public boolean isReciprocal() {
        return reciprocal;
    }

    public void setReciprocal(boolean reciprocal) {
        this.reciprocal = reciprocal;
    }

    public int getReciprocalBonus() {
        return reciprocalBonus;
    }

    public void setReciprocalBonus(int reciprocalBonus) {
        this.reciprocalBonus = reciprocalBonus;
    }

    public int getProficiencyPoints() {
        return proficiencyPoints;
    }

    public void setProficiencyPoints(int proficiencyPoints) {
        this.proficiencyPoints = proficiencyPoints;
    }

    public int getAvailabilityPoints() {
        return availabilityPoints;
    }

    public void setAvailabilityPoints(int availabilityPoints) {
        this.availabilityPoints = availabilityPoints;
    }

    public double getRatingPoints() {
        return ratingPoints;
    }

    public void setRatingPoints(double ratingPoints) {
        this.ratingPoints = ratingPoints;
    }

    public double getExperiencePoints() {
        return experiencePoints;
    }

    public void setExperiencePoints(double experiencePoints) {
        this.experiencePoints = experiencePoints;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void addReason(String reason) {
        reasons.add(reason);
    }

    /** Short explanation shown next to the score in the UI. */
    public String explain() {
        return String.join(" | ", reasons);
    }

    /** Score as a percentage of the theoretical maximum (100 points). */
    public int scorePercent() {
        return (int) Math.round(score);
    }
}
