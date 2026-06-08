package com.pos.controller;

import com.pos.entity.User;
import com.pos.service.AuthenticationService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Manager Authorization Controller
 * Handles manager authentication for privileged actions.
 */
public class ManagerAuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(ManagerAuthController.class);
    
    private final AuthenticationService authService;
    private boolean isAuthenticated;
    private User authenticatedManager;
    
    @FXML
    private TextField usernameField;
    
    @FXML
    private PasswordField passwordField;
    
    @FXML
    private Label errorLabel;
    
    public ManagerAuthController() {
        this.authService = AuthenticationService.getInstance();
        this.isAuthenticated = false;
        this.authenticatedManager = null;
    }
    
    @FXML
    public void initialize() {
        errorLabel.setVisible(false);
    }
    
    /**
     * Get the authentication result
     */
    public boolean isAuthenticated() {
        return isAuthenticated;
    }
    
    /**
     * Get the authenticated manager user
     */
    public User getAuthenticatedManager() {
        return authenticatedManager;
    }
    
    /**
     * Attempt to authenticate the manager
     */
    public boolean authenticate() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password");
            return false;
        }
        
        try {
            Optional<User> userOpt = authService.authenticate(username, password);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (user.getRole() == User.Role.ADMIN) {
                    this.isAuthenticated = true;
                    this.authenticatedManager = user;
                    logger.info("Manager authentication successful: {}", username);
                    return true;
                } else {
                    showError("Insufficient permissions. Only admins can authorize.");
                    return false;
                }
            } else {
                showError("Invalid username or password");
                return false;
            }
        } catch (Exception e) {
            logger.error("Error during manager authentication", e);
            showError("Authentication error: " + e.getMessage());
            return false;
        }
    }
    
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}