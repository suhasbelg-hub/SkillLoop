package com.skillloop.model;

/** Aggregated counters shown on the administrator dashboard. */
public class AdminStats {

    private long totalStudents;
    private long activeStudents;
    private long suspendedStudents;
    private long administrators;
    private long totalSkills;
    private long totalListings;
    private long completedSessions;
    private long pendingBookings;
    private long disputedSessions;
    private long cancelledSessions;
    private long creditsTransferred;
    private long starterGrants;
    private long openGroupSessions;
    private double avgRating;

    public long getTotalStudents() {
        return totalStudents;
    }

    public void setTotalStudents(long totalStudents) {
        this.totalStudents = totalStudents;
    }

    public long getActiveStudents() {
        return activeStudents;
    }

    public void setActiveStudents(long activeStudents) {
        this.activeStudents = activeStudents;
    }

    public long getSuspendedStudents() {
        return suspendedStudents;
    }

    public void setSuspendedStudents(long suspendedStudents) {
        this.suspendedStudents = suspendedStudents;
    }

    public long getAdministrators() {
        return administrators;
    }

    public void setAdministrators(long administrators) {
        this.administrators = administrators;
    }

    public long getTotalSkills() {
        return totalSkills;
    }

    public void setTotalSkills(long totalSkills) {
        this.totalSkills = totalSkills;
    }

    public long getTotalListings() {
        return totalListings;
    }

    public void setTotalListings(long totalListings) {
        this.totalListings = totalListings;
    }

    public long getCompletedSessions() {
        return completedSessions;
    }

    public void setCompletedSessions(long completedSessions) {
        this.completedSessions = completedSessions;
    }

    public long getPendingBookings() {
        return pendingBookings;
    }

    public void setPendingBookings(long pendingBookings) {
        this.pendingBookings = pendingBookings;
    }

    public long getDisputedSessions() {
        return disputedSessions;
    }

    public void setDisputedSessions(long disputedSessions) {
        this.disputedSessions = disputedSessions;
    }

    public long getCancelledSessions() {
        return cancelledSessions;
    }

    public void setCancelledSessions(long cancelledSessions) {
        this.cancelledSessions = cancelledSessions;
    }

    public long getCreditsTransferred() {
        return creditsTransferred;
    }

    public void setCreditsTransferred(long creditsTransferred) {
        this.creditsTransferred = creditsTransferred;
    }

    public long getStarterGrants() {
        return starterGrants;
    }

    public void setStarterGrants(long starterGrants) {
        this.starterGrants = starterGrants;
    }

    public long getOpenGroupSessions() {
        return openGroupSessions;
    }

    public void setOpenGroupSessions(long openGroupSessions) {
        this.openGroupSessions = openGroupSessions;
    }

    public double getAvgRating() {
        return avgRating;
    }

    public void setAvgRating(double avgRating) {
        this.avgRating = avgRating;
    }
}
