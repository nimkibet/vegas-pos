package com.pos.controller;

import com.pos.Main;
import com.pos.entity.User;
import com.pos.service.AuthenticationService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Login Controller
 * Handles user authentication and navigation to main POS screen.
 */
public class LoginController {
    
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);
    
    @FXML
    private TextField usernameField;
    
    @FXML
    private PasswordField passwordField;
    
    @FXML
    private Button loginButton;
    
    @FXML
    private Label errorLabel;
    
    private final AuthenticationService authService;
    private Stage primaryStage;
    
    public LoginController() {
        this.authService = AuthenticationService.getInstance();
    }
    
    /**
     * Initialize the controller
     */
    @FXML
    public void initialize() {
        errorLabel.setText("");
        usernameField.requestFocus();
    }
    
    /**
     * Set the primary stage
     */
    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }
    
    /**
     * Handle login button click
     */
    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter username and password");
            return;
        }
        
        // Authenticate user
        Optional<User> userOpt = authService.authenticate(username, password);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            logger.info("Login successful for user: {}", username);
            
            // Show success message
            showSuccess("Login successful! Welcome " + user.getFullName());
            
            // Navigate based on user role
            if (user.getRole() == User.Role.ADMIN) {
                navigateToAdmin();
            } else {
                navigateToPOS();
            }
            
        } else {
            showError("Invalid username or password");
            passwordField.clear();
            passwordField.requestFocus();
        }
    }
    
    /**
     * Navigate to POS screen
     */
    private void navigateToPOS() {
        try {
            ShiftController shiftController = ShiftController.getInstance();
            
            if (!shiftController.hasActiveShift()) {
                boolean opened = shiftController.promptOpenRegister();
                if (!opened) {
                    showError("Must open register to start POS");
                    return;
                }
            }
            
            Optional<User> userOpt = authService.getCurrentUser();
            final User user = userOpt.orElse(null);
            
            // Standard JavaFX data-passing pattern:
            // 1. Load FXML (initialize() runs with no user data)
            // 2. Get controller (UI is now built)
            // 3. Set user (apply user data to existing UI)
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pos.fxml"));
            Parent root = loader.load();
            
            POSController posController = loader.getController();
            posController.setCurrentUser(user);
            
            // Swap scene on existing stage - no new stage, no memory leak
            primaryStage.setScene(new Scene(root));
            primaryStage.setTitle("POS System - Main");
            primaryStage.setWidth(1200);
            primaryStage.setHeight(800);
            primaryStage.setResizable(true);
            primaryStage.centerOnScreen();
            primaryStage.show();
            
        } catch (Exception e) {
            logger.error("Failed to load POS screen", e);
            showError("Failed to load POS screen");
        }
    }
    
    /**
     * Navigate to Admin dashboard
     */
    private void navigateToAdmin() {
        try {
            Optional<User> userOpt = authService.getCurrentUser();
            final User user = userOpt.orElse(null);
            
            // Standard JavaFX data-passing pattern:
            // 1. Load FXML (initialize() runs safely)
            // 2. Get controller (UI is now built)
            // 3. Set primary stage and user
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/admin.fxml"));
            Parent root = loader.load();
            
            AdminController adminController = loader.getController();
            adminController.setPrimaryStage(primaryStage);
            adminController.setCurrentUser(user);
            
            Scene scene = new Scene(root);
            primaryStage.setScene(scene);
            primaryStage.setTitle("POS System - Admin Dashboard");
            primaryStage.setWidth(1200);
            primaryStage.setHeight(800);
            primaryStage.setResizable(true);
            primaryStage.centerOnScreen();
            primaryStage.show();
            
        } catch (Exception e) {
            logger.error("Failed to load Admin screen", e);
            showError("Failed to load Admin screen");
        }
    }
    
    /**
     * Handle Enter key in password field
     */
    @FXML
    private void handleKeyPress(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            handleLogin();
        }
    }
    
    /**
     * Show error message
     */
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: red;");
    }
    
    /**
     * Show success message
     */
    private void showSuccess(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: green;");
    }
    
    /**
     * Clear all input fields - called on logout
     */
    public void clearFields() {
        usernameField.clear();
        passwordField.clear();
        errorLabel.setText("");
    }
}
