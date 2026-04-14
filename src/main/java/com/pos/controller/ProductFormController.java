package com.pos.controller;

import com.pos.database.DatabaseManager;
import com.pos.entity.Product;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

public class ProductFormController {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductFormController.class);
    
    private final DatabaseManager dbManager;
    private Product product;
    
    @FXML private Label formTitle;
    @FXML private TextField barcodeField;
    @FXML private TextField nameField;
    @FXML private TextField categoryField;
    @FXML private TextField retailPriceField;
    @FXML private TextField wholesalePriceField;
    @FXML private TextField stockField;
    @FXML private TextField minStockField;
    @FXML private TextField descriptionField;
    @FXML private TextField supplierField;
    @FXML private Label errorLabel;
    
    public ProductFormController() {
        this.dbManager = DatabaseManager.getInstance();
    }
    
    public void setProduct(Product product) {
        this.product = product;
        if (product != null) {
            formTitle.setText("Edit Product");
            populateFields();
        }
    }
    
    private void populateFields() {
        if (product != null) {
            barcodeField.setText(product.getBarcode());
            nameField.setText(product.getName());
            categoryField.setText(product.getCategory());
            retailPriceField.setText(product.getRetailPrice().toPlainString());
            wholesalePriceField.setText(product.getWholesalePrice().toPlainString());
            stockField.setText(String.valueOf(product.getStockQuantity()));
            minStockField.setText(String.valueOf(product.getMinStockLevel()));
            descriptionField.setText(product.getDescription());
            supplierField.setText(product.getSupplier());
            
            barcodeField.setDisable(true);
        }
    }
    
    @FXML
    private void handleSave() {
        errorLabel.setText("");
        
        try {
            String barcode = barcodeField.getText().trim();
            String name = nameField.getText().trim();
            String category = categoryField.getText().trim();
            String retailPriceStr = retailPriceField.getText().trim();
            String wholesalePriceStr = wholesalePriceField.getText().trim();
            String stockStr = stockField.getText().trim();
            String minStockStr = minStockField.getText().trim();
            String description = descriptionField.getText().trim();
            String supplier = supplierField.getText().trim();
            
            if (barcode.isEmpty() || name.isEmpty()) {
                errorLabel.setText("Barcode and Product Name are required");
                return;
            }
            
            BigDecimal retailPrice = new BigDecimal(retailPriceStr.isEmpty() ? "0" : retailPriceStr);
            BigDecimal wholesalePrice = new BigDecimal(wholesalePriceStr.isEmpty() ? "0" : wholesalePriceStr);
            int stock = stockStr.isEmpty() ? 0 : Integer.parseInt(stockStr);
            int minStock = minStockStr.isEmpty() ? 0 : Integer.parseInt(minStockStr);
            
            if (product == null) {
                Product newProduct = new Product(name, retailPrice, wholesalePrice);
                newProduct.setBarcode(barcode);
                newProduct.setCategory(category.isEmpty() ? "General" : category);
                newProduct.setStockQuantity(stock);
                newProduct.setMinStockLevel(minStock);
                newProduct.setDescription(description);
                newProduct.setSupplier(supplier);
                newProduct.setStatus("APPROVED");
                
                dbManager.insertProduct(newProduct);
                logger.info("New product created: {}", name);
            } else {
                product.setName(name);
                product.setCategory(category.isEmpty() ? "General" : category);
                product.setRetailPrice(retailPrice);
                product.setWholesalePrice(wholesalePrice);
                product.setStockQuantity(stock);
                product.setMinStockLevel(minStock);
                product.setDescription(description);
                product.setSupplier(supplier);
                
                dbManager.updateProduct(product);
                logger.info("Product updated: {}", name);
            }
            
            closeDialog();
            
        } catch (NumberFormatException e) {
            errorLabel.setText("Invalid price or stock value");
        } catch (Exception e) {
            logger.error("Error saving product", e);
            errorLabel.setText("Error saving product: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleCancel() {
        product = null;
        closeDialog();
    }
    
    private void closeDialog() {
        Stage stage = (Stage) barcodeField.getScene().getWindow();
        stage.close();
    }
}