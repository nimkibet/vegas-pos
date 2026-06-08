package com.pos.entity;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Customer Entity
 * Represents a customer in the POS system for credit sales.
 * Uses UUID as primary key.
 */
public class Customer {
    
    private String id;
    private String name;
    private String phone;
    private LocalDateTime createdAt;
    private boolean isSynced;
    
    /**
     * Default constructor - generates new UUID
     */
    public Customer() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.isSynced = false;
    }
    
    /**
     * Full constructor
     */
    public Customer(String id, String name, String phone, LocalDateTime createdAt, boolean isSynced) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.createdAt = createdAt;
        this.isSynced = isSynced;
    }
    
    // Getters and Setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getPhone() {
        return phone;
    }
    
    public void setPhone(String phone) {
        this.phone = phone;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public boolean isSynced() {
        return isSynced;
    }
    
    public void setSynced(boolean synced) {
        isSynced = synced;
    }
    
    @Override
    public String toString() {
        return name != null ? name : "";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Customer customer = (Customer) o;
        return id != null && id.equals(customer.id);
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}