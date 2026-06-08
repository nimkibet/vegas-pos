package com.pos.controller;

import com.pos.database.DatabaseManager;
import com.pos.entity.Product;
import com.pos.entity.User;
import com.pos.entity.ActivityLog;
import com.pos.service.AuthenticationService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Optional;

public class ApprovalFormController {
    
    private static final Logger logger = LoggerFactory.getLogger(ApprovalFormController.class);
    
    private final DatabaseManager dbManager;
    private final AuthenticationService authService;
    private Product product;
    private boolean approved = false;
    
    @FXML private TextField barcodeField;
    @FXML private TextField nameField;
    @FXML private TextField categoryField;
    @FXML private TextField retailPriceField;
    @FXML private TextField wholesalePriceField;
    @FXML private TextField stockField;
    @FXML private Label errorLabel;
    
    public ApprovalFormController() {
        this.dbManager = DatabaseManager.getInstance();
        this.authService = AuthenticationService.getInstance();
    }
    
    public void setProduct(Product product) {
        this.product = product;
        populateFields();
    }
    
    public boolean isApproved() {
        return approved;
    }
    
    private void populateFields() {
        if (product != null) {
            barcodeField.setText(product.getBarcode());
            nameField.setText(product.getName());
            categoryField.setText(product.getCategory());
            retailPriceField.setText(product.getRetailPrice().toPlainString());
            wholesalePriceField.setText(product.getWholesalePrice().toPlainString());
            stockField.setText(String.valueOf(product.getStockQuantity()));
            
            barcodeField.setDisable(true);
            nameField.setDisable(true);
            stockField.setDisable(true);
        }
    }
    
    @FXML
    private void handleApprove() {
        errorLabel.setText("");
        
        try {
            String category = categoryField.getText().trim();
            String retailPriceStr = retailPriceField.getText().trim();
            String wholesalePriceStr = wholesalePriceField.getText().trim();
            
            BigDecimal retailPrice = new BigDecimal(retailPriceStr.isEmpty() ? "0" : retailPriceStr);
            BigDecimal wholesalePrice = new BigDecimal(wholesalePriceStr.isEmpty() ? "0" : wholesalePriceStr);
            
            if (retailPrice.compareTo(BigDecimal.ZERO) <= 0 || wholesalePrice.compareTo(BigDecimal.ZERO) <= 0) {
                errorLabel.setText("Prices must be greater than zero");
                return;
            }
            
            product.setCategory(category.isEmpty() ? "General" : category);
            product.setRetailPrice(retailPrice);
            product.setWholesalePrice(wholesalePrice);
            product.setStatus("APPROVED");
            
            dbManager.updateProduct(product);
            
            Optional<User> currentUser = authService.getCurrentUser();
            if (currentUser.isPresent()) {
                ActivityLog log = new ActivityLog(
                    currentUser.get().getId(),
                    currentUser.get().getFullName(),
                    ActivityLog.ActionType.APPROVE_PRODUCT,
                    product.getName(),
                    "Retail: " + retailPrice.toPlainString() + ", Wholesale: " + wholesalePrice.toPlainString()
                );
                dbManager.insertActivityLog(log);
            }
            
            approved = true;
            logger.info("Product approved: {}", product.getName());
            closeDialog();
            
        } catch (NumberFormatException e) {
            errorLabel.setText("Invalid price value");
        } catch (Exception e) {
            logger.error("Error approving product", e);
            errorLabel.setText("Error approving product: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleReject() {
        try {
            dbManager.updateProductStatus(product.getId(), "REJECTED");
            
            Optional<User> currentUser = authService.getCurrentUser();
            if (currentUser.isPresent()) {
                ActivityLog log = new ActivityLog(
                    currentUser.get().getId(),
                    currentUser.get().getFullName(),
                    ActivityLog.ActionType.REJECT_PRODUCT,
                    product.getName(),
                    "Retail: " + product.getRetailPrice().toPlainString()
                );
                dbManager.insertActivityLog(log);
            }
            
            logger.info("Product rejected: {}", product.getName());
            approved = false;
            closeDialog();
            
        } catch (Exception e) {
            logger.error("Error rejecting product", e);
            errorLabel.setText("Error rejecting product: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleCancel() {
        approved = false;
        closeDialog();
    }
    
    private void closeDialog() {
        Stage stage = (Stage) barcodeField.getScene().getWindow();
        stage.close();
    }
}