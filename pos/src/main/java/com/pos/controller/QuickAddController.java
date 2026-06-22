package com.pos.controller;

import com.pos.database.DatabaseManager;
import com.pos.entity.Product;
import com.pos.entity.ProductBarcodeMatch;
import com.pos.entity.User;
import com.pos.service.AuthenticationService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Quick Add Controller
 * Controller for the quick product registration dialog.
 */
public class QuickAddController {
    
    private static final Logger logger = LoggerFactory.getLogger(QuickAddController.class);
    
    private final DatabaseManager dbManager;
    private final AuthenticationService authService;
    private String scannedBarcode;
    private Product addedProduct;
    
    // FXML Components
    @FXML
    private TextField barcodeField;
    @FXML
    private TextField nameField;
    @FXML
    private TextField retailPriceField;
    @FXML
    private TextField wholesalePriceField;
    @FXML
    private TextField stockField;
    @FXML
    private ComboBox<String> categoryComboBox;
    @FXML
    private Button saveButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Label errorLabel;
    
    public QuickAddController() {
        this.dbManager = DatabaseManager.getInstance();
        this.authService = AuthenticationService.getInstance();
    }
    
    /**
     * Initialize controller
     */
    @FXML
    public void initialize() {
        errorLabel.setText("");
        
        try {
            java.util.List<String> categories = dbManager.getAllCategories();
            categoryComboBox.getItems().setAll(categories);
            categoryComboBox.getSelectionModel().select("General");
        } catch (Exception e) {
            logger.error("Error loading categories", e);
        }
        
        // Pre-fill barcode if provided
        if (scannedBarcode != null && !scannedBarcode.isEmpty()) {
            barcodeField.setText(scannedBarcode);
            nameField.requestFocus();
        }
    }
    
    /**
     * Set the scanned barcode
     */
    public void setBarcode(String barcode) {
        this.scannedBarcode = barcode;
        if (barcodeField != null) {
            barcodeField.setText(barcode);
        }
    }
    
    /**
     * Handle save button
     */
    @FXML
    private void handleSave() {
        try {
            // Validate inputs - safe null check
            String barcodeText = barcodeField.getText();
            String nameText = nameField.getText();
            String retailPriceStr = retailPriceField.getText();
            String wholesalePriceStr = wholesalePriceField.getText();
            String stockStr = stockField.getText();
            String categoryText = categoryComboBox.getValue();
            
            String barcode = (barcodeText != null) ? barcodeText.trim() : "";
            String name = (nameText != null) ? nameText.trim() : "";
            String retailPrice = (retailPriceStr != null) ? retailPriceStr.trim() : "";
            String wholesalePrice = (wholesalePriceStr != null) ? wholesalePriceStr.trim() : "";
            String stock = (stockStr != null) ? stockStr.trim() : "";
            String category = (categoryText != null) ? categoryText.trim() : "";
            
            if (barcode.isEmpty()) {
                showError("Please fill in all required fields (*)");
                return;
            }
            
            if (name.isEmpty()) {
                showError("Please fill in all required fields (*)");
                return;
            }
            
            // Parse prices safely
            BigDecimal retailPriceValue;
            BigDecimal wholesalePriceValue;
            double stockValue;
            
            try {
                retailPriceValue = retailPrice.isEmpty() ? BigDecimal.ZERO : new BigDecimal(retailPrice);
                wholesalePriceValue = wholesalePrice.isEmpty() ? BigDecimal.ZERO : new BigDecimal(wholesalePrice);
                stockValue = stock.isEmpty() ? 0 : Double.parseDouble(stock);
            } catch (NumberFormatException | ArithmeticException e) {
                showError("Invalid price or stock value. Please enter valid numbers.");
                return;
            }
            
            // Check if barcode already exists (unit or box)
            Optional<ProductBarcodeMatch> existingMatch = dbManager.findProductByAnyBarcode(barcode);
            if (existingMatch.isPresent()) {
                showError("A product with this barcode (unit or box) already exists.");
                return;
            }
            
            // Create product
            Product product = new Product(name, retailPriceValue, wholesalePriceValue);
            product.setBarcode(barcode);
            product.setStockQuantity(stockValue);
            product.setCategory(category.isEmpty() ? "General" : category);
            
            // Set product status based on user role
            Optional<User> currentUser = authService.getCurrentUser();
            if (currentUser.isPresent() && currentUser.get().getRole() == User.Role.ATTENDANT) {
                product.setStatus("PENDING");
            } else {
                product.setStatus("APPROVED");
            }
            
            // Save to database
            dbManager.insertProduct(product);
            
            // Sync to cloud
            com.pos.service.SyncService.getInstance().syncProductToCloud(product);
            
            this.addedProduct = product;
            logger.info("New product added: {}", name);
            
            // Close dialog
            closeDialog();
            
        } catch (Exception e) {
            logger.error("Error saving product", e);
            showError("Error saving product: " + e.getMessage());
        }
    }
    
    /**
     * Handle cancel button
     */
    @FXML
    private void handleCancel() {
        addedProduct = null;
        closeDialog();
    }
    
    /**
     * Close the dialog
     */
    private void closeDialog() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }
    
    /**
     * Show error message
     */
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: red;");
    }
    
    /**
     * Get the added product
     */
    public Product getAddedProduct() {
        return addedProduct;
    }
}
