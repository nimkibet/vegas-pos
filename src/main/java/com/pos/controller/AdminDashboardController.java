package com.pos.controller;

import com.pos.database.DatabaseManager;
import com.pos.entity.Product;
import com.pos.entity.User;
import com.pos.entity.ActivityLog;
import com.pos.service.AnalyticsService;
import com.pos.service.AuthenticationService;
import com.pos.controller.LoginController;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class AdminDashboardController {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminDashboardController.class);
    
    private final DatabaseManager dbManager;
    private final AuthenticationService authService;
    private final AnalyticsService analyticsService;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private ObservableList<Product> productsList;
    private ObservableList<Product> stockList;
    private ObservableList<Product> approvalList;
    
    @FXML private Label userLabel;
    @FXML private Label totalProductsLabel;
    @FXML private Label pendingApprovalsLabel;
    @FXML private Label lowStockLabel;
    
    @FXML private StackPane contentArea;
    @FXML private VBox productMgmtView;
    @FXML private VBox stockVerifyView;
    @FXML private VBox approvalQueueView;
    @FXML private VBox analyticsView;
    
    @FXML private TextField productSearchField;
    @FXML private TextField stockSearchField;
    @FXML private TextField newStockField;
    @FXML private Label selectedProductLabel;
    
    @FXML private TableView<Product> productsTable;
    @FXML private TableColumn<Product, String> colProductBarcode;
    @FXML private TableColumn<Product, String> colProductName;
    @FXML private TableColumn<Product, String> colProductCategory;
    @FXML private TableColumn<Product, String> colProductRetail;
    @FXML private TableColumn<Product, String> colProductWholesale;
    @FXML private TableColumn<Product, Integer> colProductStock;
    @FXML private TableColumn<Product, String> colProductSupplier;
    @FXML private TableColumn<Product, String> colProductStatus;
    
    @FXML private TableView<Product> stockTable;
    @FXML private TableColumn<Product, String> colStockBarcode;
    @FXML private TableColumn<Product, String> colStockName;
    @FXML private TableColumn<Product, Integer> colCurrentStock;
    @FXML private TableColumn<Product, Integer> colMinLevel;
    @FXML private TableColumn<Product, String> colStockRetail;
    @FXML private TableColumn<Product, String> colStockCategory;
    
    @FXML private TableView<Product> approvalTable;
    @FXML private TableColumn<Product, String> colApprovalBarcode;
    @FXML private TableColumn<Product, String> colApprovalName;
    @FXML private TableColumn<Product, String> colApprovalCategory;
    @FXML private TableColumn<Product, String> colApprovalRetail;
    @FXML private TableColumn<Product, String> colApprovalWholesale;
    @FXML private TableColumn<Product, Integer> colApprovalStock;
    @FXML private TableColumn<Product, String> colApprovalDate;
    
    @FXML private Label todayRevenueLabel;
    @FXML private Label todayTransactionsLabel;
    @FXML private Label analyticsProductsLabel;
    
    @FXML private javafx.scene.chart.LineChart<String, Number> revenueChart;
    @FXML private javafx.scene.chart.PieChart topItemsChart;
    
    public AdminDashboardController() {
        this.dbManager = DatabaseManager.getInstance();
        this.authService = AuthenticationService.getInstance();
        this.analyticsService = AnalyticsService.getInstance();
        this.productsList = FXCollections.observableArrayList();
        this.stockList = FXCollections.observableArrayList();
        this.approvalList = FXCollections.observableArrayList();
    }
    
    @FXML
    public void initialize() {
        setupProductsTable();
        setupStockTable();
        setupApprovalTable();
        loadQuickStats();
        loadAllProducts();
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
        colProductSupplier.setCellValueFactory(new PropertyValueFactory<>("supplier"));
        colProductStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        
        productsTable.setItems(productsList);
    }
    
    private void setupStockTable() {
        colStockBarcode.setCellValueFactory(new PropertyValueFactory<>("barcode"));
        colStockName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCurrentStock.setCellValueFactory(new PropertyValueFactory<>("stockQuantity"));
        colMinLevel.setCellValueFactory(new PropertyValueFactory<>("minStockLevel"));
        colStockRetail.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty("KSh" + cell.getValue().getRetailPrice().toPlainString()));
        colStockCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        
        stockTable.setItems(stockList);
        
        stockTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                selectedProductLabel.setText(newSelection.getName() + " (Current: " + newSelection.getStockQuantity() + ")");
            } else {
                selectedProductLabel.setText("None");
            }
        });
    }
    
    private void setupApprovalTable() {
        colApprovalBarcode.setCellValueFactory(new PropertyValueFactory<>("barcode"));
        colApprovalName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colApprovalCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colApprovalRetail.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty("KSh" + cell.getValue().getRetailPrice().toPlainString()));
        colApprovalWholesale.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty("KSh" + cell.getValue().getWholesalePrice().toPlainString()));
        colApprovalStock.setCellValueFactory(new PropertyValueFactory<>("stockQuantity"));
        colApprovalDate.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getCreatedAt().format(dateFormatter)));
        
        approvalTable.setItems(approvalList);
    }
    
    private void loadQuickStats() {
        try {
            Optional<User> currentUser = authService.getCurrentUser();
            if (currentUser.isPresent()) {
                userLabel.setText("User: " + currentUser.get().getFullName() + " (" + currentUser.get().getRole().getDisplayName() + ")");
            }
            
            int totalProducts = dbManager.getAllProducts().size();
            int pendingCount = dbManager.getPendingProducts().size();
            int lowStockCount = dbManager.getLowStockProducts().size();
            
            totalProductsLabel.setText("Products: " + totalProducts);
            pendingApprovalsLabel.setText("Pending: " + pendingCount);
            lowStockLabel.setText("Low Stock: " + lowStockCount);
            
        } catch (Exception e) {
            logger.error("Error loading quick stats", e);
        }
    }
    
    @FXML
    public void showProductManagement() {
        hideAllViews();
        productMgmtView.setVisible(true);
    }
    
    @FXML
    public void showStockVerification() {
        hideAllViews();
        stockVerifyView.setVisible(true);
        loadAllStock();
    }
    
    @FXML
    public void showApprovalQueue() {
        hideAllViews();
        approvalQueueView.setVisible(true);
        loadPendingApprovals();
    }
    
    @FXML
    public void showAnalytics() {
        hideAllViews();
        analyticsView.setVisible(true);
        loadAnalyticsData();
    }
    
    private void hideAllViews() {
        productMgmtView.setVisible(false);
        stockVerifyView.setVisible(false);
        approvalQueueView.setVisible(false);
        analyticsView.setVisible(false);
    }
    
    @FXML
    public void onNavHover(javafx.scene.input.MouseEvent event) {
        if (event.getSource() instanceof Button) {
            Button btn = (Button) event.getSource();
            btn.setStyle("-fx-font-size: 14; -fx-text-fill: white; -fx-background-color: #4a5568; -fx-padding: 15 20; -fx-alignment: CENTER_LEFT;");
        }
    }
    
    @FXML
    public void onNavExit(javafx.scene.input.MouseEvent event) {
        if (event.getSource() instanceof Button) {
            Button btn = (Button) event.getSource();
            btn.setStyle("-fx-font-size: 14; -fx-text-fill: white; -fx-background-color: transparent; -fx-padding: 15 20; -fx-alignment: CENTER_LEFT;");
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
            e.printStackTrace();
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
    private void handleAddProduct() {
        showProductDialog(null);
    }
    
    @FXML
    private void handleEditProduct() {
        Product selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a product to edit");
            return;
        }
        showProductDialog(selected);
    }
    
    private void showProductDialog(Product product) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/product_form.fxml"));
            Parent root = loader.load();
            
            ProductFormController controller = loader.getController();
            controller.setProduct(product);
            
            Stage dialog = new Stage();
            dialog.setTitle(product == null ? "Add New Product" : "Edit Product");
            dialog.setScene(new Scene(root));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setResizable(false);
            dialog.showAndWait();
            
            loadAllProducts();
            loadQuickStats();
            
        } catch (Exception e) {
            logger.error("Error showing product dialog", e);
            showError("Error opening product form");
        }
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
                loadAllProducts();
                loadQuickStats();
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
            int newStock = Integer.parseInt(result.get());
            if (newStock < 0) {
                showError("Stock count cannot be negative");
                return;
            }
            
            int oldStock = selected.getStockQuantity();
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
            
            loadAllProducts();
            loadQuickStats();
            
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
    
    @FXML
    private void loadAllStock() {
        try {
            stockList.clear();
            List<Product> products = dbManager.getAllProducts();
            stockList.addAll(products);
        } catch (Exception e) {
            logger.error("Error loading stock", e);
            showError("Error loading stock data");
        }
    }
    
    @FXML
    private void showLowStock() {
        try {
            stockList.clear();
            List<Product> products = dbManager.getLowStockProducts();
            stockList.addAll(products);
        } catch (Exception e) {
            logger.error("Error loading low stock", e);
        }
    }
    
    @FXML
    private void showAllStock() {
        loadAllStock();
    }
    
    @FXML
    private void handleStockSearch() {
        String searchTerm = stockSearchField.getText().trim();
        try {
            stockList.clear();
            List<Product> products;
            if (searchTerm.isEmpty()) {
                products = dbManager.getAllProducts();
            } else {
                products = dbManager.searchProducts(searchTerm);
            }
            stockList.addAll(products);
        } catch (Exception e) {
            logger.error("Error searching stock", e);
        }
    }
    
    @FXML
    private void handleStockAdjustment() {
        Product selected = stockTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a product from the table");
            return;
        }
        
        String newStockStr = newStockField.getText().trim();
        if (newStockStr.isEmpty()) {
            showError("Please enter a new stock count");
            return;
        }
        
        try {
            int newStock = Integer.parseInt(newStockStr);
            if (newStock < 0) {
                showError("Stock count cannot be negative");
                return;
            }
            
            dbManager.updateProductStock(selected.getId(), newStock);
            
            Optional<User> currentUser = authService.getCurrentUser();
            if (currentUser.isPresent()) {
                ActivityLog log = new ActivityLog(
                    currentUser.get().getId(),
                    currentUser.get().getFullName(),
                    ActivityLog.ActionType.CLEAR_CART,
                    "Stock Adjustment: " + selected.getName(),
                    "Changed from " + selected.getStockQuantity() + " to " + newStock
                );
                dbManager.insertActivityLog(log);
            }
            
            newStockField.clear();
            loadAllStock();
            loadQuickStats();
            showSuccess("Stock updated: " + selected.getName() + " = " + newStock);
            
        } catch (NumberFormatException e) {
            showError("Please enter a valid number");
        } catch (Exception e) {
            logger.error("Error updating stock", e);
            showError("Error updating stock: " + e.getMessage());
        }
    }
    
    @FXML
    private void loadPendingApprovals() {
        try {
            approvalList.clear();
            List<Product> pending = dbManager.getPendingProducts();
            approvalList.addAll(pending);
            logger.info("Loaded {} pending approvals", pending.size());
        } catch (Exception e) {
            logger.error("Error loading pending approvals", e);
            e.printStackTrace();
            showError("Error loading pending approvals: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleApproveItem() {
        Product selected = approvalTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a product to approve");
            return;
        }
        
        showApprovalEditDialog(selected, true);
    }
    
    @FXML
    private void handleRejectItem() {
        Product selected = approvalTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a product to reject");
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Reject Product");
        confirm.setHeaderText("Are you sure you want to reject this product?");
        confirm.setContentText("Product: " + selected.getName());
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                dbManager.updateProductStatus(selected.getId(), "REJECTED");
                
                Optional<User> currentUser = authService.getCurrentUser();
                if (currentUser.isPresent()) {
                    ActivityLog log = new ActivityLog(
                        currentUser.get().getId(),
                        currentUser.get().getFullName(),
                        ActivityLog.ActionType.REJECT_PRODUCT,
                        selected.getName(),
                        "Retail: " + selected.getRetailPrice().toPlainString()
                    );
                    dbManager.insertActivityLog(log);
                }
                
                approvalList.remove(selected);
                loadQuickStats();
                showSuccess("Product rejected: " + selected.getName());
                
            } catch (Exception e) {
                logger.error("Error rejecting product", e);
                showError("Error rejecting product");
            }
        }
    }
    
    private void showApprovalEditDialog(Product product, boolean isApproval) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/approval_form.fxml"));
            Parent root = loader.load();
            
            ApprovalFormController controller = loader.getController();
            controller.setProduct(product);
            
            Stage dialog = new Stage();
            dialog.setTitle(isApproval ? "Approve Product" : "Edit Product");
            dialog.setScene(new Scene(root));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setResizable(false);
            dialog.showAndWait();
            
            if (controller.isApproved()) {
                approvalList.remove(product);
                loadQuickStats();
            }
            
        } catch (Exception e) {
            logger.error("Error showing approval dialog", e);
            showError("Error opening approval form");
        }
    }
    
    private void loadAnalyticsData() {
        try {
            double todayRevenue = analyticsService.getTodayRevenue();
            int todayTransactions = analyticsService.getTodayTransactionCount();
            int totalProducts = dbManager.getAllProducts().size();
            
            todayRevenueLabel.setText("KSh" + String.format("%,.2f", todayRevenue));
            todayTransactionsLabel.setText(String.valueOf(todayTransactions));
            analyticsProductsLabel.setText(String.valueOf(totalProducts));
            
        } catch (Exception e) {
            logger.error("Error loading analytics", e);
        }
    }
    
    @FXML
    private void handleBackToPOS() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pos.fxml"));
            Parent root = loader.load();
            
            Stage stage = (Stage) contentArea.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Vegas POS - Point of Sale");
            stage.setMaximized(true);
            
        } catch (Exception e) {
            logger.error("Error loading POS screen", e);
            showError("Error returning to POS");
        }
    }
    
    @FXML
    private void handleLogout() {
        try {
            Stage currentStage = (Stage) contentArea.getScene().getWindow();
            currentStage.close();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            
            LoginController loginController = loader.getController();
            loginController.clearFields();
            
            Stage newStage = new Stage();
            newStage.setTitle("Vegas POS - Login");
            newStage.setScene(new Scene(root));
            newStage.setWidth(420);
            newStage.setHeight(520);
            newStage.setResizable(false);
            newStage.centerOnScreen();
            newStage.show();

            authService.logout();

            logger.info("User logged out - stage completely recreated");
        } catch (Exception e) {
            logger.error("Error loading login screen", e);
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