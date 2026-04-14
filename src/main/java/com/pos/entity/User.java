package com.pos.entity;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User Entity
 * Represents a user (Admin or Attendant) in the POS system.
 * Uses UUID as primary key.
 */
public class User {
    
    /**
     * User Roles
     */
    public enum Role {
        ADMIN("Admin"),
        ATTENDANT("Attendant");
        
        private final String displayName;
        
        Role(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }

    private String id;
    private String username;
    private String passwordHash;
    private String fullName;
    private Role role;
    private boolean isActive;
    private boolean isSynced;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Default constructor - generates new UUID
     */
    public User() {
        this.id = UUID.randomUUID().toString();
        this.isActive = true;
        this.isSynced = false;
        this.role = Role.ATTENDANT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Constructor with required fields
     */
    public User(String username, String passwordHash, String fullName, Role role) {
        this();
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
    }

    /**
     * Full constructor
     */
    public User(String id, String username, String passwordHash, String fullName, 
                Role role, boolean isActive, boolean isSynced,
                LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.isActive = isActive;
        this.isSynced = isSynced;
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
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

    /**
     * Check if user is an admin
     */
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /**
     * Check if user is an attendant
     */
    public boolean isAttendant() {
        return role == Role.ATTENDANT;
    }

    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", username='" + username + '\'' +
                ", fullName='" + fullName + '\'' +
                ", role=" + role +
                ", isActive=" + isActive +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id != null && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
