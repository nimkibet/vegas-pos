package com.pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Product Entity
 * Represents a product in the POS system with retail and wholesale pricing.
 * Uses UUID as primary key for distributed database support.
 */
public class Product {
    
    private String id;
    private String barcode;
    private String name;
    private String description;
    private String category;
    private BigDecimal retailPrice;
    private BigDecimal wholesalePrice;
    private double stockQuantity;
    private double minStockLevel;
    private String imagePath;
    private String supplier;
    private String status;
    private boolean isActive;
    private boolean isSynced;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Bulk/Box fields
    private String bulkBarcode;
    private BigDecimal bulkPrice;
    private int piecesPerBulk = 1;

    // Parent-Child Variant fields
    private String parentBarcode;
    private double deductionRatio = 1.0;
    private String unitType = "Pieces";

    /**
     * Default constructor - generates new UUID
     */
    public Product() {
        this.id = UUID.randomUUID().toString();
        this.isActive = true;
        this.isSynced = false;
        this.stockQuantity = 0;
        this.minStockLevel = 0;
        this.retailPrice = BigDecimal.ZERO;
        this.wholesalePrice = BigDecimal.ZERO;
        this.bulkPrice = BigDecimal.ZERO;
        this.piecesPerBulk = 1;
        this.deductionRatio = 1.0;
        this.unitType = "Pieces";
        this.status = "APPROVED";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Constructor with required fields
     */
    public Product(String name, BigDecimal retailPrice, BigDecimal wholesalePrice) {
        this();
        this.name = name;
        this.retailPrice = retailPrice;
        this.wholesalePrice = wholesalePrice;
    }

    /**
     * Full constructor
     */
    public Product(String id, String barcode, String name, String description, 
                   String category, BigDecimal retailPrice, BigDecimal wholesalePrice,
                   double stockQuantity, double minStockLevel, String imagePath,
                   String supplier, String status, boolean isActive, boolean isSynced, 
                   LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.barcode = barcode;
        this.name = name;
        this.description = description;
        this.category = category;
        this.retailPrice = retailPrice;
        this.wholesalePrice = wholesalePrice;
        this.stockQuantity = stockQuantity;
        this.minStockLevel = minStockLevel;
        this.imagePath = imagePath;
        this.supplier = supplier;
        this.status = status;
        this.isActive = isActive;
        this.isSynced = isSynced;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deductionRatio = 1.0;
    }

    // Getters and Setters
    
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getRetailPrice() {
        return retailPrice;
    }

    public void setRetailPrice(BigDecimal retailPrice) {
        this.retailPrice = retailPrice;
    }

    public BigDecimal getWholesalePrice() {
        return wholesalePrice;
    }

    public void setWholesalePrice(BigDecimal wholesalePrice) {
        this.wholesalePrice = wholesalePrice;
    }

    public double getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(double stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public double getMinStockLevel() {
        return minStockLevel;
    }

    public void setMinStockLevel(double minStockLevel) {
        this.minStockLevel = minStockLevel;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }
    
    public String getSupplier() {
        return supplier;
    }
    
    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }
    
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
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
    
    // Bulk/Box field getters and setters
    
    public String getBulkBarcode() {
        return bulkBarcode;
    }

    public void setBulkBarcode(String bulkBarcode) {
        this.bulkBarcode = bulkBarcode;
    }

    public BigDecimal getBulkPrice() {
        return bulkPrice;
    }

    public void setBulkPrice(BigDecimal bulkPrice) {
        this.bulkPrice = bulkPrice;
    }

    public int getPiecesPerBulk() {
        return piecesPerBulk;
    }

    public void setPiecesPerBulk(int piecesPerBulk) {
        this.piecesPerBulk = piecesPerBulk;
    }

    public String getParentBarcode() {
        return parentBarcode;
    }

    public void setParentBarcode(String parentBarcode) {
        this.parentBarcode = parentBarcode;
    }

    public double getDeductionRatio() {
        return deductionRatio;
    }

    public void setDeductionRatio(double deductionRatio) {
        this.deductionRatio = deductionRatio;
    }

    public String getUnitType() {
        return unitType;
    }

    public void setUnitType(String unitType) {
        this.unitType = unitType;
    }

    /**
     * Check if product is low on stock
     */
    public boolean isLowStock() {
        return stockQuantity <= minStockLevel;
    }

    /**
     * Check if product is out of stock
     */
    public boolean isOutOfStock() {
        return stockQuantity <= 0;
    }

    /**
     * Decrease stock quantity
     */
    public void decreaseStock(int quantity) {
        this.stockQuantity -= quantity;
        if (this.stockQuantity < 0) {
            this.stockQuantity = 0;
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Increase stock quantity
     */
    public void increaseStock(int quantity) {
        this.stockQuantity += quantity;
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "Product{" +
                "id='" + id + '\'' +
                ", barcode='" + barcode + '\'' +
                ", name='" + name + '\'' +
                ", retailPrice=" + retailPrice +
                ", wholesalePrice=" + wholesalePrice +
                ", stockQuantity=" + stockQuantity +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return id != null && id.equals(product.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
