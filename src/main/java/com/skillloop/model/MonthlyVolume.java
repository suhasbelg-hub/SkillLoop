package com.skillloop.model;

/** Monthly credit movement used by the administrator reports. */
public class MonthlyVolume {

    private String period;
    private String transactionType;
    private long transactionCount;
    private long creditsCredited;
    private long creditsDebited;

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public long getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(long transactionCount) {
        this.transactionCount = transactionCount;
    }

    public long getCreditsCredited() {
        return creditsCredited;
    }

    public void setCreditsCredited(long creditsCredited) {
        this.creditsCredited = creditsCredited;
    }

    public long getCreditsDebited() {
        return creditsDebited;
    }

    public void setCreditsDebited(long creditsDebited) {
        this.creditsDebited = creditsDebited;
    }
}
