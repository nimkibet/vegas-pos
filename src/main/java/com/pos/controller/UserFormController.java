package com.pos.controller;

import com.pos.database.DatabaseManager;
import com.pos.entity.User;
import com.pos.service.AuthenticationService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;

public class UserFormController {
    
    private static final Logger logger = LoggerFactory.getLogger(UserFormController.class);
    
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private TextField fullNameField;
    @FXML private ComboBox<String> roleComboBox;
    @FXML private CheckBox activeCheckBox;
    @FXML private Button saveButton;
    @FXML private Label errorLabel;
    
    private final DatabaseManager dbManager;
    private final AuthenticationService authService;
    private User user;
    private boolean isEditMode;
    private boolean saved;
    
    public UserFormController() {
        this.dbManager = DatabaseManager.getInstance();
        this.authService = AuthenticationService.getInstance();
        this.isEditMode = false;
    }
    
    @FXML
    public void initialize() {
        roleComboBox.getItems().addAll("ADMIN", "ATTENDANT");
        roleComboBox.getSelectionModel().select("ATTENDANT");
        activeCheckBox.setSelected(true);
        saved = false;
        
        if (!isEditMode && user == null) {
            usernameField.setDisable(false);
            passwordField.setDisable(false);
            confirmPasswordField.setDisable(false);
            fullNameField.setDisable(false);
            roleComboBox.setDisable(false);
            activeCheckBox.setDisable(false);
            saveButton.setText("Create User");
        }
    }
    
    public void setMode(boolean editMode) {
        this.isEditMode = editMode;
    }
    
    public void setUser(User user) {
        this.user = user;
        usernameField.setText(user.getUsername());
        fullNameField.setText(user.getFullName());
        roleComboBox.getSelectionModel().select(user.getRole().name());
        activeCheckBox.setSelected(user.isActive());
        
        passwordField.setPromptText("Leave blank to keep current");
        confirmPasswordField.setPromptText("Leave blank to keep current");
        saveButton.setText("Update");
    }
    
    public boolean isSaved() {
        return saved;
    }
    
    public User getUser() {
        return user;
    }
    
    @FXML
    private void handleSave() {
        errorLabel.setText("");
        
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        String fullName = fullNameField.getText().trim();
        String roleStr = roleComboBox.getSelectionModel().getSelectedItem();
        
        if (username.isEmpty()) {
            showError("Username is required");
            return;
        }
        
        if (fullName.isEmpty()) {
            showError("Full name is required");
            return;
        }
        
        if (roleStr == null) {
            showError("Please select a role");
            return;
        }
        
        try {
            boolean usernameExists;
            if (isEditMode && user != null) {
                usernameExists = dbManager.usernameExistsExcluding(username, user.getId());
            } else {
                usernameExists = dbManager.usernameExists(username);
            }
            
            if (usernameExists) {
                showError("Username already exists");
                return;
            }
        } catch (Exception e) {
            logger.error("Error checking username", e);
            showError("Error validating username");
            return;
        }
        
        if (!isEditMode || !password.isEmpty()) {
            if (password.isEmpty()) {
                showError("Password is required");
                return;
            }
            if (password.length() < 6) {
                showError("Password must be at least 6 characters");
                return;
            }
            if (!password.equals(confirmPassword)) {
                showError("Passwords do not match");
                return;
            }
        }
        
        try {
            if (isEditMode && user != null) {
                user.setUsername(username);
                user.setFullName(fullName);
                user.setRole(User.Role.valueOf(roleStr));
                user.setActive(activeCheckBox.isSelected());
                user.setSynced(false);
                
                if (!password.isEmpty()) {
                    user.setPasswordHash(authService.hashPassword(password));
                }
                
                user.setUpdatedAt(LocalDateTime.now());
                dbManager.updateUser(user);
            } else {
                User newUser = new User();
                newUser.setUsername(username);
                newUser.setFullName(fullName);
                newUser.setRole(User.Role.valueOf(roleStr));
                newUser.setActive(activeCheckBox.isSelected());
                newUser.setPasswordHash(authService.hashPassword(password));
                newUser.setSynced(false);
                
                dbManager.insertUser(newUser);
                user = newUser;
            }
            
            saved = true;
            closeDialog();
            
        } catch (Exception e) {
            logger.error("Error saving user", e);
            showError("Error saving user: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleCancel() {
        closeDialog();
    }
    
    private void closeDialog() {
        Stage stage = (Stage) usernameField.getScene().getWindow();
        stage.close();
    }
    
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: red;");
    }
}
