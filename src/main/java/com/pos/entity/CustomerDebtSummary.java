package com.pos.entity;

import java.math.BigDecimal;

/**
 * Entity representing a summary of a customer's total debt.
 * Used in the Admin Accounts Receivable module.
 */
public class CustomerDebtSummary {
    private Customer customer;
    private BigDecimal totalDebt;
    private int pendingBatches;

    public CustomerDebtSummary(Customer customer, BigDecimal totalDebt, int pendingBatches) {
        this.customer = customer;
        this.totalDebt = totalDebt;
        this.pendingBatches = pendingBatches;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public BigDecimal getTotalDebt() {
        return totalDebt;
    }

    public void setTotalDebt(BigDecimal totalDebt) {
        this.totalDebt = totalDebt;
    }

    public int getPendingBatches() {
        return pendingBatches;
    }

    public void setPendingBatches(int pendingBatches) {
        this.pendingBatches = pendingBatches;
    }
}
