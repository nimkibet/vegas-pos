package com.pos.service;

import com.pos.database.DatabaseManager;
import com.pos.entity.User;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Authentication Service
 * Handles user login and authentication.
 */
public class AuthenticationService {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);
    private static AuthenticationService instance;
    
    private final DatabaseManager databaseManager;
    private User currentUser;
    
    /**
     * Private constructor for singleton pattern
     */
    private AuthenticationService() {
        this.databaseManager = DatabaseManager.getInstance();
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized AuthenticationService getInstance() {
        if (instance == null) {
            instance = new AuthenticationService();
        }
        return instance;
    }
    
    /**
     * Authenticate user with username and password
     * @param username The username
     * @param password The password (plain text)
     * @return Optional containing the user if authentication successful
     */
    public Optional<User> authenticate(String username, String password) {
        try {
            Optional<User> userOpt = databaseManager.findUserByUsername(username);
            
            if (userOpt.isEmpty()) {
                logger.warn("User not found: {}", username);
                return Optional.empty();
            }
            
            User user = userOpt.get();
            
            // Check if user is active
            if (!user.isActive()) {
                logger.warn("User account is disabled: {}", username);
                return Optional.empty();
            }
            
            // Verify password
            if (verifyPassword(password, user.getPasswordHash())) {
                this.currentUser = user;
                logger.info("User authenticated successfully: {}", username);
                return Optional.of(user);
            } else {
                logger.warn("Invalid password for user: {}", username);
                return Optional.empty();
            }
            
        } catch (Exception e) {
            logger.error("Error during authentication", e);
            return Optional.empty();
        }
    }
    
    /**
     * Get the currently logged in user
     * @return Optional containing the current user
     */
    public Optional<User> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }
    
    /**
     * Logout the current user
     */
    public void logout() {
        if (currentUser != null) {
            logger.info("User logged out: {}", currentUser.getUsername());
            currentUser = null;
        }
    }
    
    /**
     * Clear all authentication state including any cached references
     */
    public void clearState() {
        currentUser = null;
    }
    
    /**
     * Check if a user is currently logged in
     */
    public boolean isLoggedIn() {
        return currentUser != null;
    }
    
    /**
     * Check if the current user is an admin
     */
    public boolean isAdmin() {
        return currentUser != null && currentUser.isAdmin();
    }
    
    /**
     * Check if the current user is an attendant
     */
    public boolean isAttendant() {
        return currentUser != null && currentUser.isAttendant();
    }
    
    /**
     * Hash a password using BCrypt
     * @param password The plain text password
     * @return The hashed password
     */
    public String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(10));
    }
    
    /**
     * Verify a password against a stored hash
     * @param password The plain text password
     * @param storedHash The stored password hash
     * @return true if password matches
     */
    public boolean verifyPassword(String password, String storedHash) {
        if (password == null || storedHash == null) {
            return false;
        }
        
        try {
            if (storedHash.startsWith("$2")) {
                return BCrypt.checkpw(password, storedHash);
            }
            
            return false;
            
        } catch (Exception e) {
            logger.error("Error verifying password", e);
            return false;
        }
    }
    
    /**
     * Change user password
     * @param userId The user ID
     * @param oldPassword The old password
     * @param newPassword The new password
     * @return true if password changed successfully
     */
    public boolean changePassword(String userId, String oldPassword, String newPassword) {
        try {
            Optional<User> userOpt = databaseManager.findUserById(userId);
            
            if (userOpt.isEmpty()) {
                return false;
            }
            
            User user = userOpt.get();
            
            // Verify old password
            if (!verifyPassword(oldPassword, user.getPasswordHash())) {
                return false;
            }
            
            // Update password
            user.setPasswordHash(hashPassword(newPassword));
            databaseManager.updateUser(user);
            
            logger.info("Password changed for user: {}", user.getUsername());
            return true;
            
        } catch (Exception e) {
            logger.error("Error changing password", e);
            return false;
        }
    }
}
