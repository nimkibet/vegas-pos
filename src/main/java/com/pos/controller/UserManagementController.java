package com.pos.controller;

import com.pos.database.DatabaseManager;
import com.pos.entity.User;
import com.pos.entity.ActivityLog;
import com.pos.service.AuthenticationService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class UserManagementController {
    
    private static final Logger logger = LoggerFactory.getLogger(UserManagementController.class);
    
    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, String> colUsername;
    @FXML private TableColumn<User, String> colFullName;
    @FXML private TableColumn<User, String> colRole;
    @FXML private TableColumn<User, Boolean> colActive;
    @FXML private TableColumn<User, String> colCreatedAt;
    
    @FXML private TextField searchField;
    
    // FXML components - Analytics
    @FXML private Label totalUsersLabel;
    @FXML private Label activeUsersLabel;
    @FXML private Label adminCountLabel;
    @FXML private Label attendantCountLabel;
    
    private final DatabaseManager dbManager;
    private final AuthenticationService authService;
    private final ObservableList<User> userList;
    private Stage primaryStage;
    
    public UserManagementController() {
        this.dbManager = DatabaseManager.getInstance();
        this.authService = AuthenticationService.getInstance();
        this.userList = FXCollections.observableArrayList();
    }
    
    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }
    
    @FXML
    public void initialize() {
        setupTable();
        loadUsers();
    }
    
    private void setupTable() {
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colFullName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colRole.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getRole().getDisplayName()));
        colActive.setCellValueFactory(new PropertyValueFactory<>("active"));
        colCreatedAt.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getCreatedAt() != null ? 
                    cell.getValue().getCreatedAt().toLocalDate().toString() : ""));
        
        usersTable.setItems(userList);
    }
    
    private void loadUsers() {
        try {
            List<User> users = dbManager.getAllUsers();
            userList.clear();
            userList.addAll(users);
            updateAnalytics();
        } catch (Exception e) {
            logger.error("Error loading users", e);
            showError("Error loading users: " + e.getMessage());
        }
    }
    
    private void updateAnalytics() {
        if (totalUsersLabel == null) return;
        
        long total = userList.size();
        long active = userList.stream().filter(User::isActive).count();
        long admins = userList.stream().filter(User::isAdmin).count();
        long attendants = userList.stream().filter(User::isAttendant).count();
        
        totalUsersLabel.setText(String.valueOf(total));
        activeUsersLabel.setText(String.valueOf(active));
        adminCountLabel.setText(String.valueOf(admins));
        attendantCountLabel.setText(String.valueOf(attendants));
    }
    
    @FXML
    private void handleSearch() {
        String searchTerm = searchField.getText().trim().toLowerCase();
        
        if (searchTerm.isEmpty()) {
            loadUsers();
            return;
        }
        
        try {
            List<User> allUsers = dbManager.getAllUsers();
            List<User> filtered = allUsers.stream()
                .filter(u -> u.getUsername().toLowerCase().contains(searchTerm) ||
                            (u.getFullName() != null && u.getFullName().toLowerCase().contains(searchTerm)))
                .toList();
            
            userList.clear();
            userList.addAll(filtered);
        } catch (Exception e) {
            logger.error("Error searching users", e);
        }
    }
    
    @FXML
    private void handleAddUser() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/user_form.fxml"));
            Parent root = loader.load();
            
            UserFormController formController = loader.getController();
            formController.setMode(false);
            
            Stage dialog = new Stage();
            dialog.setTitle("Add User");
            dialog.setScene(new Scene(root));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(primaryStage);
            dialog.setResizable(false);
            dialog.showAndWait();
            
            if (formController.isSaved()) {
                loadUsers();
                logActivity("Created user: " + formController.getUser().getUsername());
            }
        } catch (Exception e) {
            logger.error("Error opening add user dialog", e);
            showError("Error opening add user dialog");
        }
    }
    
    @FXML
    private void handleEditUser() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a user to edit");
            return;
        }
        
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/user_form.fxml"));
            Parent root = loader.load();
            
            UserFormController formController = loader.getController();
            formController.setMode(true);
            formController.setUser(selected);
            
            Stage dialog = new Stage();
            dialog.setTitle("Edit User");
            dialog.setScene(new Scene(root));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(primaryStage);
            dialog.setResizable(false);
            dialog.showAndWait();
            
            if (formController.isSaved()) {
                loadUsers();
                logActivity("Updated user: " + selected.getUsername());
            }
        } catch (Exception e) {
            logger.error("Error opening edit user dialog", e);
            showError("Error opening edit user dialog");
        }
    }
    
    @FXML
    private void handleDeleteUser() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a user to delete");
            return;
        }
        
        Optional<User> currentUser = authService.getCurrentUser();
        if (currentUser.isPresent() && currentUser.get().getId().equals(selected.getId())) {
            showError("You cannot delete your own account");
            return;
        }
        
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Delete User");
        confirmAlert.setHeaderText("Are you sure you want to delete this user?");
        confirmAlert.setContentText("Username: " + selected.getUsername());
        
        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        
        try {
            dbManager.deleteUser(selected.getId());
            logActivity("Deleted user: " + selected.getUsername());
            loadUsers();
            showSuccess("User deleted successfully");
        } catch (Exception e) {
            logger.error("Error deleting user", e);
            showError("Error deleting user: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleToggleActive() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a user");
            return;
        }
        
        try {
            selected.setActive(!selected.isActive());
            selected.setUpdatedAt(LocalDateTime.now());
            dbManager.updateUser(selected);
            
            logActivity((selected.isActive() ? "Activated" : "Deactivated") + " user: " + selected.getUsername());
            loadUsers();
            showSuccess("User status changed");
        } catch (Exception e) {
            logger.error("Error toggling user status", e);
            showError("Error changing user status");
        }
    }
    
    @FXML
    private void handleRefresh() {
        loadUsers();
    }
    
    @FXML
    private void handleResetPassword() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a user");
            return;
        }
        
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Reset Password");
        confirmAlert.setHeaderText("Reset password for this user?");
        confirmAlert.setContentText("A default password will be set");
        
        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        
        try {
            selected.setPasswordHash(authService.hashPassword("password123"));
            selected.setUpdatedAt(LocalDateTime.now());
            dbManager.updateUser(selected);
            
            logActivity("Reset password for user: " + selected.getUsername());
            showSuccess("Password reset to default (password123)");
        } catch (Exception e) {
            logger.error("Error resetting password", e);
            showError("Error resetting password");
        }
    }
    
    private void logActivity(String description) {
        try {
            Optional<User> currentUser = authService.getCurrentUser();
            if (currentUser.isPresent()) {
                ActivityLog log = new ActivityLog(
                    currentUser.get().getId(),
                    currentUser.get().getFullName(),
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
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
