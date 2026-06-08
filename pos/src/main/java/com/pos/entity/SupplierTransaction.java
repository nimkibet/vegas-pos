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

    public SupplierTransaction() {
        this.id = UUID.randomUUID().toString();
        this.totalCost = BigDecimal.ZERO;
        this.status = STATUS_PAID;
        this.createdAt = LocalDateTime.now();
    }

    public SupplierTransaction(String id, String supplierName, BigDecimal totalCost, String status, LocalDateTime createdAt) {
        this.id = id;
        this.supplierName = supplierName;
        this.totalCost = totalCost != null ? totalCost : BigDecimal.ZERO;
        this.status = status;
        this.createdAt = createdAt;
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
}
