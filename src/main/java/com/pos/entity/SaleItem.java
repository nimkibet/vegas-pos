package com.pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * SaleItem Entity
 * Represents an item in a sale transaction.
 * Uses UUID as primary key.
 */
public class SaleItem {
    
    private String id;
    private String saleId;
    private String productId;
    private String productName;
    private String productBarcode;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private boolean isSynced;
    private LocalDateTime createdAt;
    
    // Related entity (not stored in DB, populated on retrieval)
    private Product product;

    /**
     * Default constructor - generates new UUID
     */
    public SaleItem() {
        this.id = UUID.randomUUID().toString();
        this.quantity = 0;
        this.unitPrice = BigDecimal.ZERO;
        this.totalPrice = BigDecimal.ZERO;
        this.isSynced = false;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Constructor with product info
     */
    public SaleItem(Product product, int quantity, BigDecimal unitPrice) {
        this();
        this.productId = product.getId();
        this.productName = product.getName();
        this.productBarcode = product.getBarcode();
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.product = product;
        recalculateTotal();
    }

    /**
     * Full constructor
     */
    public SaleItem(String id, String saleId, String productId, String productName,
                    String productBarcode, int quantity, BigDecimal unitPrice,
                    BigDecimal totalPrice, boolean isSynced, LocalDateTime createdAt) {
        this.id = id;
        this.saleId = saleId;
        this.productId = productId;
        this.productName = productName;
        this.productBarcode = productBarcode;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
        this.isSynced = isSynced;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSaleId() {
        return saleId;
    }

    public void setSaleId(String saleId) {
        this.saleId = saleId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductBarcode() {
        return productBarcode;
    }

    public void setProductBarcode(String productBarcode) {
        this.productBarcode = productBarcode;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
        recalculateTotal();
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
        recalculateTotal();
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
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

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    /**
     * Recalculate total price based on quantity and unit price
     */
    public void recalculateTotal() {
        if (unitPrice != null && quantity > 0) {
            this.totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
        } else {
            this.totalPrice = BigDecimal.ZERO;
        }
    }

    /**
     * Increase quantity
     */
    public void increaseQuantity(int amount) {
        this.quantity += amount;
        recalculateTotal();
    }

    /**
     * Decrease quantity
     */
    public void decreaseQuantity(int amount) {
        this.quantity -= amount;
        if (this.quantity < 0) {
            this.quantity = 0;
        }
        recalculateTotal();
    }

    @Override
    public String toString() {
        return "SaleItem{" +
                "id='" + id + '\'' +
                ", productName='" + productName + '\'' +
                ", quantity=" + quantity +
                ", unitPrice=" + unitPrice +
                ", totalPrice=" + totalPrice +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SaleItem saleItem = (SaleItem) o;
        return id != null && id.equals(saleItem.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
