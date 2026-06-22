package com.pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Header row for a supplier stock-in (shipment) with payment status.
 */
public class SupplierTransaction {

    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_CREDIT = "CREDIT";

    private String id;
    private String supplierName;
    private BigDecimal totalCost;
    private String status;
    private LocalDateTime createdAt;
    /** e.g. "Cash (From Shelf)", "Owner Out-of-Pocket", "M-PESA", "Debt (Offset Account)" */
    private String paymentSource;
    /** ID of the Customer record to offset when paymentSource == "Debt (Offset Account)" */
    private String debtorId;
    private boolean isSynced;
    private BigDecimal debtorOffset = BigDecimal.ZERO;
    private BigDecimal cashPaid = BigDecimal.ZERO;

    public SupplierTransaction() {
        this.id = UUID.randomUUID().toString();
        this.totalCost = BigDecimal.ZERO;
        this.status = STATUS_PAID;
        this.createdAt = LocalDateTime.now();
        this.isSynced = false;
    }

    public SupplierTransaction(String id, String supplierName, BigDecimal totalCost, String status, LocalDateTime createdAt) {
        this.id = id;
        this.supplierName = supplierName;
        this.totalCost = totalCost != null ? totalCost : BigDecimal.ZERO;
        this.status = status;
        this.createdAt = createdAt;
        this.isSynced = false;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost != null ? totalCost : BigDecimal.ZERO;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getPaymentSource() {
        return paymentSource;
    }

    public void setPaymentSource(String paymentSource) {
        this.paymentSource = paymentSource;
    }

    public String getDebtorId() {
        return debtorId;
    }

    public void setDebtorId(String debtorId) {
        this.debtorId = debtorId;
    }

    public boolean isSynced() {
        return isSynced;
    }

    public void setSynced(boolean synced) {
        isSynced = synced;
    }

    public BigDecimal getDebtorOffset() {
        return debtorOffset;
    }

    public void setDebtorOffset(BigDecimal debtorOffset) {
        this.debtorOffset = debtorOffset != null ? debtorOffset : BigDecimal.ZERO;
    }

    public BigDecimal getCashPaid() {
        return cashPaid;
    }

    public void setCashPaid(BigDecimal cashPaid) {
        this.cashPaid = cashPaid != null ? cashPaid : BigDecimal.ZERO;
    }
}
