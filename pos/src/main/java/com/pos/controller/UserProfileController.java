package com.pos.controller;

import com.pos.database.DatabaseManager;
import com.pos.entity.User;
import com.pos.entity.ActivityLog;
import com.pos.service.AuthenticationService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Optional;

public class UserProfileController {
    
    private static final Logger logger = LoggerFactory.getLogger(UserProfileController.class);
    
    @FXML private TextField userIdField;
    @FXML private TextField usernameField;
    @FXML private TextField fullNameField;
    @FXML private TextField roleField;
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label errorLabel;
    
    private final DatabaseManager dbManager;
    private final AuthenticationService authService;
    private User currentUser;
    private Stage primaryStage;
    
    public UserProfileController() {
        this.dbManager = DatabaseManager.getInstance();
        this.authService = AuthenticationService.getInstance();
    }
    
    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }
    
    @FXML
    public void initialize() {
        Optional<User> userOpt = authService.getCurrentUser();
        if (userOpt.isPresent()) {
            currentUser = userOpt.get();
            userIdField.setText(currentUser.getId());
            usernameField.setText(currentUser.getUsername());
            fullNameField.setText(currentUser.getFullName());
            roleField.setText(currentUser.getRole().getDisplayName());
        }
    }
    
    @FXML
    private void handleClose() {
        closeDialog();
    }
    
    private void closeDialog() {
        javafx.scene.Node node = usernameField;
        javafx.stage.Stage stage = (javafx.stage.Stage) node.getScene().getWindow();
        stage.close();
    }
    
    @FXML
    private void handleSaveProfile() {
        errorLabel.setText("");
        
        String fullName = fullNameField.getText().trim();
        
        if (fullName.isEmpty()) {
            showError("Full name is required");
            return;
        }
        
        try {
            currentUser.setFullName(fullName);
            currentUser.setUpdatedAt(LocalDateTime.now());
            dbManager.updateUser(currentUser);
            
            logActivity("Updated profile");
            showSuccess("Profile updated successfully");
        } catch (Exception e) {
            logger.error("Error saving profile", e);
            showError("Error saving profile: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleChangePassword() {
        errorLabel.setText("");
        
        String currentPassword = currentPasswordField.getText();
        String newPassword = newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        
        if (currentPassword.isEmpty()) {
            showError("Current password is required");
            return;
        }
        
        if (newPassword.isEmpty()) {
            showError("New password is required");
            return;
        }
        
        if (newPassword.length() < 6) {
            showError("New password must be at least 6 characters");
            return;
        }
        
        if (!newPassword.equals(confirmPassword)) {
            showError("Passwords do not match");
            return;
        }
        
        try {
            Optional<User> userOpt = dbManager.findUserById(currentUser.getId());
            if (userOpt.isEmpty()) {
                showError("User not found");
                return;
            }
            
            User user = userOpt.get();
            if (!authService.verifyPassword(currentPassword, user.getPasswordHash())) {
                showError("Current password is incorrect");
                return;
            }
            
            user.setPasswordHash(authService.hashPassword(newPassword));
            user.setUpdatedAt(LocalDateTime.now());
            dbManager.updateUser(user);
            
            currentPasswordField.clear();
            newPasswordField.clear();
            confirmPasswordField.clear();
            
            logActivity("Changed password");
            showSuccess("Password changed successfully");
        } catch (Exception e) {
            logger.error("Error changing password", e);
            showError("Error changing password: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleLogout() {
        try {
            Stage currentStage = (Stage) usernameField.getScene().getWindow();
            currentStage.close();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();

            LoginController loginController = loader.getController();
            loginController.clearFields();

            Stage newStage = new Stage();
            newStage.setTitle("POS System - Login");
            newStage.setScene(new Scene(root));
            newStage.setResizable(false);
            newStage.sizeToScene();
            newStage.centerOnScreen();
            newStage.show();

            authService.logout();

            logger.info("User logged out - stage completely recreated");
        } catch (Exception e) {
            logger.error("Error logging out", e);
        }
    }
    
    private void logActivity(String description) {
        try {
            Optional<User> user = authService.getCurrentUser();
            if (user.isPresent()) {
                ActivityLog log = new ActivityLog(
                    user.get().getId(),
                    user.get().getFullName(),
                    ActivityLog.ActionType.USER_MANAGEMENT,
                    description,
                    null
                );
                dbManager.insertActivityLog(log);
            }
        } catch (Exception e) {
            logger.warn("Could not log activity", e);
        }
    }
    
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: red;");
    }
    
    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
