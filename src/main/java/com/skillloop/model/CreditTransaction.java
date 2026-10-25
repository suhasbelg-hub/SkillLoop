package com.skillloop.model;

import java.time.LocalDateTime;

/** One immutable ledger entry describing a credit movement. */
public class CreditTransaction {

    private long transactionId;
    private long studentId;
    private Long sessionId;
    private int amount;
    private int heldAmount;
    private int balanceAfter;
    private int heldAfter;
    private TransactionType type = TransactionType.SPEND;
    private String reference;
    private String description;
    private Long createdBy;
    private LocalDateTime createdAt;

    /** Denormalised counterpart name for the transaction history table. */
    private String studentName;

    public CreditTransaction() {
        // required for DAO mapping
    }

    /** True when this entry increases the spendable balance of the student. */
    public boolean isCredit() {
        return amount > 0;
    }

    public long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(long transactionId) {
        this.transactionId = transactionId;
    }

    public long getStudentId() {
        return studentId;
    }

    public void setStudentId(long studentId) {
        this.studentId = studentId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public int getHeldAmount() {
        return heldAmount;
    }

    public void setHeldAmount(int heldAmount) {
        this.heldAmount = heldAmount;
    }

    public int getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(int balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public int getHeldAfter() {
        return heldAfter;
    }

    public void setHeldAfter(int heldAfter) {
        this.heldAfter = heldAfter;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }
}
