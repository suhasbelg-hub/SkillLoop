package com.skillloop.model;

import java.time.LocalDateTime;

/** Credit wallet of a student: spendable balance plus reserved (held) credits. */
public class Wallet {

    private long walletId;
    private long studentId;
    private int balance;
    private int heldCredits;
    private LocalDateTime updatedAt;

    /** Lifetime totals loaded from the ledger (not stored in the wallets table). */
    private int totalEarned;
    private int totalSpent;

    public Wallet() {
        // required for DAO mapping
    }

    public Wallet(long studentId, int balance, int heldCredits) {
        this.studentId = studentId;
        this.balance = balance;
        this.heldCredits = heldCredits;
    }

    /** Spendable credits (balance already excludes everything that is held). */
    public int available() {
        return balance;
    }

    public long getWalletId() {
        return walletId;
    }

    public void setWalletId(long walletId) {
        this.walletId = walletId;
    }

    public long getStudentId() {
        return studentId;
    }

    public void setStudentId(long studentId) {
        this.studentId = studentId;
    }

    public int getBalance() {
        return balance;
    }

    public void setBalance(int balance) {
        this.balance = balance;
    }

    public int getHeldCredits() {
        return heldCredits;
    }

    public void setHeldCredits(int heldCredits) {
        this.heldCredits = heldCredits;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getTotalEarned() {
        return totalEarned;
    }

    public void setTotalEarned(int totalEarned) {
        this.totalEarned = totalEarned;
    }

    public int getTotalSpent() {
        return totalSpent;
    }

    public void setTotalSpent(int totalSpent) {
        this.totalSpent = totalSpent;
    }
}
