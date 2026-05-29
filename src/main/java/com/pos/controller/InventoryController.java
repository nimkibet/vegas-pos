package com.pos.controller;

import com.pos.database.DatabaseManager;
import com.pos.entity.ActivityLog;
import com.pos.entity.Product;
import com.pos.entity.User;
import com.pos.service.AuthenticationService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class InventoryController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryController.class);

    private final DatabaseManager dbManager;
    private final AuthenticationService authService;
    private final ObservableList<Product> productsList = FXCollections.observableArrayList();
    private Runnable onInventoryChanged = () -> {};

    @FXML private TabPane inventoryTabPane;
    @FXML private ProductFormController productFormController;

    @FXML private TextField productSearchField;
    @FXML private TableView<Product> productsTable;
    @FXML private TableColumn<Product, String> colProductBarcode;
    @FXML private TableColumn<Product, String> colProductName;
    @FXML private TableColumn<Product, String> colProductCategory;
    @FXML private TableColumn<Product, String> colProductRetail;
    @FXML private TableColumn<Product, String> colProductWholesale;
    @FXML
    private TableColumn<Product, Double> colProductStock;
    @FXML
    private TableColumn<Product, String> colProductUnit;
    @FXML
    private TableColumn<Product, String> colProductSupplier;

    @FXML private TableColumn<Product, String> colProductStatus;

    public InventoryController() {
        this.dbManager = DatabaseManager.getInstance();
        this.authService = AuthenticationService.getInstance();
    }

    public void setOnInventoryChanged(Runnable onInventoryChanged) {
        if (onInventoryChanged != null) {
            this.onInventoryChanged = onInventoryChanged;
        }
    }

    @FXML
    public void initialize() {
        setupProductsTable();
        loadAllProducts();
        Platform.runLater(() -> {
            if (productFormController != null) {
                productFormController.setParentController(this);
            }
        });
    }

    private void setupProductsTable() {
        colProductBarcode.setCellValueFactory(new PropertyValueFactory<>("barcode"));
        colProductName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colProductCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colProductRetail.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty("KSh" + cell.getValue().getRetailPrice().toPlainString()));
        colProductWholesale.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty("KSh" + cell.getValue().getWholesalePrice().toPlainString()));
        colProductStock.setCellValueFactory(new PropertyValueFactory<>("stockQuantity"));
        colProductUnit.setCellValueFactory(new PropertyValueFactory<>("unitType"));
        colProductSupplier.setCellValueFactory(new PropertyValueFactory<>("supplier"));
        colProductStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        productsTable.setItems(productsList);
    }

    public void refreshTable() {
        loadAllProducts();
        onInventoryChanged.run();
    }

    public void switchToTableTab() {
        if (inventoryTabPane != null && !inventoryTabPane.getTabs().isEmpty()) {
            inventoryTabPane.getSelectionModel().select(0);
        }
    }

    @FXML
    private void loadAllProducts() {
        try {
            productsList.clear();
            List<Product> products = dbManager.getAllProducts();
            productsList.addAll(products);
            logger.info("Loaded {} products", products.size());
        } catch (Exception e) {
            logger.error("Error loading products", e);
            showError("Error loading products: " + e.getMessage());
        }
    }

    @FXML
    private void handleProductSearch() {
        String searchTerm = productSearchField.getText().trim();
        try {
            productsList.clear();
            List<Product> products;
            if (searchTerm.isEmpty()) {
                products = dbManager.getAllProducts();
            } else {
                products = dbManager.searchProducts(searchTerm);
            }
            productsList.addAll(products);
        } catch (Exception e) {
            logger.error("Error searching products", e);
            showError("Error searching products");
        }
    }

    @FXML
    private void handleAddNewProduct() {
        if (productFormController != null) {
            productFormController.prepareNewProduct();
        }
        inventoryTabPane.getSelectionModel().select(1);
    }

    @FXML
    private void handleEditProduct() {
        Product selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a product to edit");
            return;
        }
        if (productFormController != null) {
            productFormController.loadProductForEdit(selected);
        }
        inventoryTabPane.getSelectionModel().select(1);
    }

    @FXML
    private void handleDeleteProduct() {
        Product selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a product to delete");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Product");
        confirm.setHeaderText("Are you sure you want to delete this product?");
        confirm.setContentText("Product: " + selected.getName());

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                selected.setActive(false);
                dbManager.updateProduct(selected);
                refreshTable();
                showSuccess("Product deleted successfully");
            } catch (Exception e) {
                logger.error("Error deleting product", e);
                showError("Error deleting product: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleAdjustStock() {
        Product selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a product to adjust stock");
            return;
        }

        TextInputDialog stockDialog = new TextInputDialog(String.valueOf(selected.getStockQuantity()));
        stockDialog.setTitle("Adjust Stock");
        stockDialog.setHeaderText("Enter new physical stock count for: " + selected.getName());
        stockDialog.setContentText("Current stock: " + selected.getStockQuantity() + "\nNew stock count:");

        Optional<String> result = stockDialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        try {
            double newStock = Double.parseDouble(result.get());
            if (newStock < 0) {
                showError("Stock count cannot be negative");
                return;
            }

            double oldStock = selected.getStockQuantity();
            String shrinkageReason = null;

            if (newStock < oldStock) {
                ChoiceDialog<String> shrinkageDialog = new ChoiceDialog<>("Theft", "Theft", "Damaged", "Expired", "Supplier Error");
                shrinkageDialog.setTitle("Shrinkage Detected");
                shrinkageDialog.setHeaderText("New stock is lower than current stock. Select reason for shrinkage:");
                shrinkageDialog.setContentText("Reason:");

                Optional<String> reasonResult = shrinkageDialog.showAndWait();
                if (reasonResult.isEmpty()) {
                    return;
                }
                shrinkageReason = reasonResult.get();
            }

            dbManager.updateProductStock(selected.getId(), newStock);

            Optional<User> currentUser = authService.getCurrentUser();
            if (currentUser.isPresent()) {
                ActivityLog log;
                if (shrinkageReason != null) {
                    log = new ActivityLog(
                            currentUser.get().getId(),
                            currentUser.get().getFullName(),
                            ActivityLog.ActionType.SHRINKAGE,
                            "Stock adjusted for " + selected.getName() + " from " + oldStock + " to " + newStock,
                            "Reason: " + shrinkageReason
                    );
                } else {
                    log = new ActivityLog(
                            currentUser.get().getId(),
                            currentUser.get().getFullName(),
                            ActivityLog.ActionType.STOCK_ADJUSTMENT,
                            "Stock adjusted for " + selected.getName(),
                            "Changed from " + oldStock + " to " + newStock
                    );
                }
                dbManager.insertActivityLog(log);
            }

            refreshTable();

            String message = "Stock updated: " + selected.getName() + " from " + oldStock + " to " + newStock;
            if (shrinkageReason != null) {
                message += ". Reason: " + shrinkageReason;
            }
            showSuccess(message);

        } catch (NumberFormatException e) {
            showError("Please enter a valid number");
        } catch (Exception e) {
            logger.error("Error adjusting stock", e);
            showError("Error adjusting stock: " + e.getMessage());
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
