package com.pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sale Entity
 * Represents a sale transaction in the POS system.
 * Uses UUID as primary key.
 */
public class Sale {
    
    /**
     * Payment Methods
     */
    public enum PaymentMethod {
        CASH("Cash"),
        CARD("Card"),
        MOBILE_MONEY("Mobile Money"),
        CREDIT("Credit");
        
        private final String displayName;
        
        PaymentMethod(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Sale Status
     */
    public enum Status {
        PENDING("Pending"),
        COMPLETED("Completed"),
        VOIDED("Voided"),
        REFUNDED("Refunded");
        
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
    private String customerId;
    private String paymentStatus;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal total;
    private PaymentMethod paymentMethod;
    private BigDecimal amountPaid;
    private BigDecimal changeGiven;
    private Status status;
    private String notes;
    private boolean isSynced;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Split payment fields
    private BigDecimal cashAmount;
    private BigDecimal mpesaAmount;
    private PaymentMethod secondaryPaymentMethod;
    
    // Related entities
    private List<SaleItem> items;
    private User user;

    /**
     * Default constructor - generates new UUID
     */
    public Sale() {
        this.id = UUID.randomUUID().toString();
        this.subtotal = BigDecimal.ZERO;
        this.taxAmount = BigDecimal.ZERO;
        this.discountAmount = BigDecimal.ZERO;
        this.total = BigDecimal.ZERO;
        this.amountPaid = BigDecimal.ZERO;
        this.changeGiven = BigDecimal.ZERO;
        this.status = Status.PENDING;
        this.paymentMethod = PaymentMethod.CASH;
        this.paymentStatus = "PAID";
        this.isSynced = false;
        this.items = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.cashAmount = BigDecimal.ZERO;
        this.mpesaAmount = BigDecimal.ZERO;
    }

    /**
     * Constructor with user ID
     */
    public Sale(String userId) {
        this();
        this.userId = userId;
    }

    /**
     * Full constructor
     */
    public Sale(String id, String userId, BigDecimal subtotal, BigDecimal taxAmount, 
                BigDecimal discountAmount, BigDecimal total, PaymentMethod paymentMethod,
                BigDecimal amountPaid, BigDecimal changeGiven, Status status, 
                String notes, boolean isSynced, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.subtotal = subtotal;
        this.taxAmount = taxAmount;
        this.discountAmount = discountAmount;
        this.total = total;
        this.paymentMethod = paymentMethod;
        this.amountPaid = amountPaid;
        this.changeGiven = changeGiven;
        this.status = status;
        this.notes = notes;
        this.isSynced = isSynced;
        this.items = new ArrayList<>();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters
    
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
    
    public String getCustomerId() {
        return customerId;
    }
    
    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
    
    public String getPaymentStatus() {
        return paymentStatus;
    }
    
    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = amountPaid;
    }

    public BigDecimal getChangeGiven() {
        return changeGiven;
    }

    public void setChangeGiven(BigDecimal changeGiven) {
        this.changeGiven = changeGiven;
    }

    public Status getStatus() {
        return status;
    }

public void setStatus(Status status) {
        this.status = status;
    }
    
    public String getStatusDisplay() {
        return status != null ? status.getDisplayName() : "";
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<SaleItem> getItems() {
        return items;
    }

    public void setItems(List<SaleItem> items) {
        this.items = items;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
    
    public BigDecimal getCashAmount() {
        return cashAmount;
    }
    
    public void setCashAmount(BigDecimal cashAmount) {
        this.cashAmount = cashAmount;
    }
    
    public BigDecimal getMpesaAmount() {
        return mpesaAmount;
    }
    
    public void setMpesaAmount(BigDecimal mpesaAmount) {
        this.mpesaAmount = mpesaAmount;
    }
    
    public PaymentMethod getSecondaryPaymentMethod() {
        return secondaryPaymentMethod;
    }
    
    public void setSecondaryPaymentMethod(PaymentMethod secondaryPaymentMethod) {
        this.secondaryPaymentMethod = secondaryPaymentMethod;
    }
    
    public boolean isSplitPayment() {
        return cashAmount.compareTo(BigDecimal.ZERO) > 0 && mpesaAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Add item to sale
     */
    public void addItem(SaleItem item) {
        items.add(item);
        recalculateTotal();
    }

    /**
     * Remove item from sale
     */
    public void removeItem(SaleItem item) {
        items.remove(item);
        recalculateTotal();
    }

    /**
     * Recalculate sale totals
     */
    public void recalculateTotal() {
        // Calculate subtotal from items
        subtotal = items.stream()
                .map(SaleItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Calculate total (subtotal + tax - discount)
        total = subtotal.add(taxAmount).subtract(discountAmount);
        
        // Calculate change
        if (amountPaid.compareTo(BigDecimal.ZERO) > 0) {
            changeGiven = amountPaid.subtract(total);
            if (changeGiven.compareTo(BigDecimal.ZERO) < 0) {
                changeGiven = BigDecimal.ZERO;
            }
        }
        
        updatedAt = LocalDateTime.now();
    }

    /**
     * Complete the sale
     */
    public void complete() {
        this.status = Status.COMPLETED;
        this.recalculateTotal();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Void the sale
     */
    public void voidSale() {
        this.status = Status.VOIDED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Get item count
     */
    public double getItemCount() {
        return items.stream()
                .mapToDouble(SaleItem::getQuantity)
                .sum();
    }

    @Override
    public String toString() {
        return "Sale{" +
                "id='" + id + '\'' +
                ", total=" + total +
                ", status=" + status +
                ", paymentMethod=" + paymentMethod +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Sale sale = (Sale) o;
        return id != null && id.equals(sale.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
