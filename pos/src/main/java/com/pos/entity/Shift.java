package com.pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Shift Entity
 * Represents a work shift in the POS system.
 */
public class Shift {
    
    public enum Status {
        OPEN("Open"),
        CLOSED("Closed");
        
        private final String displayName;
        
        Status(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }

    private String id;
    private String userId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal startingFloat;
    private BigDecimal expectedCash;
    private BigDecimal actualCash;
    private BigDecimal mpesaTotal;
    private BigDecimal cardTotal;
    private int transactionCount;
    private Status status;
    private String notes;
    private boolean isSynced;
    
    private User user;

    public Shift() {
        this.id = UUID.randomUUID().toString();
        this.startingFloat = BigDecimal.ZERO;
        this.expectedCash = BigDecimal.ZERO;
        this.actualCash = BigDecimal.ZERO;
        this.mpesaTotal = BigDecimal.ZERO;
        this.cardTotal = BigDecimal.ZERO;
        this.transactionCount = 0;
        this.status = Status.OPEN;
        this.isSynced = false;
        this.startTime = LocalDateTime.now();
    }

    public Shift(String userId) {
        this();
        this.userId = userId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public BigDecimal getStartingFloat() {
        return startingFloat;
    }

    public void setStartingFloat(BigDecimal startingFloat) {
        this.startingFloat = startingFloat;
    }

    public BigDecimal getExpectedCash() {
        return expectedCash;
    }

    public void setExpectedCash(BigDecimal expectedCash) {
        this.expectedCash = expectedCash;
    }

    public BigDecimal getActualCash() {
        return actualCash;
    }

    public void setActualCash(BigDecimal actualCash) {
        this.actualCash = actualCash;
    }

    public BigDecimal getMpesaTotal() {
        return mpesaTotal;
    }

    public void setMpesaTotal(BigDecimal mpesaTotal) {
        this.mpesaTotal = mpesaTotal;
    }

    public BigDecimal getCardTotal() {
        return cardTotal;
    }

    public void setCardTotal(BigDecimal cardTotal) {
        this.cardTotal = cardTotal;
    }

    public int getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(int transactionCount) {
        this.transactionCount = transactionCount;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isSynced() {
        return isSynced;
    }

    public void setSynced(boolean synced) {
        isSynced = synced;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
    
    public BigDecimal getVariance() {
        if (actualCash == null || expectedCash == null) {
            return BigDecimal.ZERO;
        }
        return actualCash.subtract(expectedCash);
    }
    
    public BigDecimal getTotalSales() {
        BigDecimal total = BigDecimal.ZERO;
        if (expectedCash != null) total = total.add(expectedCash);
        if (mpesaTotal != null) total = total.add(mpesaTotal);
        if (cardTotal != null) total = total.add(cardTotal);
        return total;
    }

    public void close() {
        this.status = Status.CLOSED;
        this.endTime = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "Shift{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", startTime=" + startTime +
                ", status=" + status +
                '}';
    }
}