package com.pos.controller;

import com.pos.database.DatabaseManager;
import com.pos.entity.Product;
import com.pos.entity.User;
import com.pos.entity.ActivityLog;
import com.pos.service.AnalyticsService;
import com.pos.service.AuthenticationService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AdminDashboardController {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminDashboardController.class);
    
    private final DatabaseManager dbManager;
    private final AuthenticationService authService;
    private final AnalyticsService analyticsService;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private ObservableList<Product> stockList;
    private ObservableList<Product> approvalList;
    
    @FXML private Label userLabel;
    @FXML private Label totalProductsLabel;
    @FXML private Label pendingApprovalsLabel;
    @FXML private Label lowStockLabel;
    
    @FXML private StackPane contentArea;
    @FXML private Parent inventoryManagement;
    @FXML private InventoryController inventoryManagementController;
    @FXML private Parent stockInForm;
    @FXML private StockInFormController stockInFormController;
    @FXML private Parent userManagementView;
    @FXML private UserManagementController userManagementViewController;
    @FXML private Parent debtorsView;
    @FXML private AdminDebtorsController debtorsViewController;
    @FXML private VBox stockVerifyView;
    @FXML private VBox approvalQueueView;
    @FXML private VBox analyticsView;
    
    @FXML private TextField stockSearchField;
    @FXML private TextField newStockField;
    @FXML private Label selectedProductLabel;
    
    @FXML private TableView<Product> stockTable;
    @FXML private TableColumn<Product, String> colStockBarcode;
    @FXML private TableColumn<Product, String> colStockName;
    @FXML private TableColumn<Product, Double> colCurrentStock;
    @FXML private TableColumn<Product, Double> colMinLevel;
    @FXML private TableColumn<Product, String> colStockRetail;
    @FXML private TableColumn<Product, String> colStockCategory;
    
    @FXML private TableView<Product> approvalTable;
    @FXML private TableColumn<Product, String> colApprovalBarcode;
    @FXML private TableColumn<Product, String> colApprovalName;
    @FXML private TableColumn<Product, String> colApprovalCategory;
    @FXML private TableColumn<Product, String> colApprovalRetail;
    @FXML private TableColumn<Product, String> colApprovalWholesale;
    @FXML private TableColumn<Product, Double> colApprovalStock;
    @FXML private TableColumn<Product, String> colApprovalDate;
    
    @FXML private Label todayRevenueLabel;
    @FXML private Label todayTransactionsLabel;
    @FXML private Label analyticsProductsLabel;
    @FXML private Label netProfitLabel;
    @FXML private ListView<String> topCategoriesList;
    @FXML private ComboBox<String> timeframeSelector;
    
    @FXML private javafx.scene.chart.LineChart<String, Number> revenueChart;
    @FXML private javafx.scene.chart.PieChart topItemsChart;
    @FXML private Button themeToggleButton;

    private Stage primaryStage;
    private User currentUser;
    private boolean isLargeText = false;
    
    public AdminDashboardController() {
        this.dbManager = DatabaseManager.getInstance();
        this.authService = AuthenticationService.getInstance();
        this.analyticsService = AnalyticsService.getInstance();
        this.stockList = FXCollections.observableArrayList();
        this.approvalList = FXCollections.observableArrayList();
    }
    
    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
        if (userManagementViewController != null) {
            userManagementViewController.setPrimaryStage(stage);
        }
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        loadQuickStats();
    }
    
    @FXML
    public void initialize() {
        stockTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        approvalTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        setupStockTable();
        setupApprovalTable();
        loadQuickStats();
        if (inventoryManagementController != null) {
            inventoryManagementController.setOnInventoryChanged(this::loadQuickStats);
        }
        if (stockInFormController != null) {
            stockInFormController.setOnStockChanged(() -> {
                loadQuickStats();
                if (inventoryManagementController != null) {
                    inventoryManagementController.refreshTable();
                }
            });
        }
        if (userManagementViewController != null) {
            userManagementViewController.setPrimaryStage(primaryStage);
        }
        if (timeframeSelector != null) {
            timeframeSelector.setItems(FXCollections.observableArrayList("Today", "Last 7 Days", "This Month", "This Year"));
            timeframeSelector.setValue("Last 7 Days");
            timeframeSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    refreshAnalytics(newVal);
                }
            });
        }
        showProductManagement();
        javafx.application.Platform.runLater(this::applyTheme);
    }
    
    private void setupStockTable() {
        colStockBarcode.setCellValueFactory(new PropertyValueFactory<>("barcode"));
        colStockName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCurrentStock.setCellValueFactory(new PropertyValueFactory<>("stockQuantity"));
        
        colCurrentStock.setCellFactory(column -> new TableCell<Product, Double>() {
            @Override
            protected void updateItem(Double stock, boolean empty) {
                super.updateItem(stock, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText("");
                    setStyle("");
                } else {
                    Product product = getTableRow().getItem();
                    if (product.getParentBarcode() != null) {
                        setText("Dynamic");
                        setStyle("-fx-text-fill: #666666;"); // Neutral color
                    } else {
                        setText(String.format("%.2f", stock));
                        if (stock < 5) {
                            setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                        } else {
                            setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                        }
                    }
                }
            }
        });

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

        colApprovalStock.setCellFactory(column -> new TableCell<Product, Double>() {
            @Override
            protected void updateItem(Double stock, boolean empty) {
                super.updateItem(stock, empty);
                if (empty || stock == null) {
                    setText("");
                } else {
                    setText(String.format("%.2f", stock));
                }
            }
        });

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
    public void showStockIn() {
        hideAllViews();
        stockInForm.setVisible(true);
        stockInForm.setManaged(true);
        if (stockInFormController != null) {
            stockInFormController.refreshAll();
        }
    }
    
    @FXML
    public void showProductManagement() {
        hideAllViews();
        inventoryManagement.setVisible(true);
        inventoryManagement.setManaged(true);
        if (inventoryManagementController != null) {
            inventoryManagementController.refreshTable();
        }
    }
    
    @FXML
    public void showStockVerification() {
        hideAllViews();
        stockVerifyView.setVisible(true);
        stockVerifyView.setManaged(true);
        loadAllStock();
    }
    
    @FXML
    public void showApprovalQueue() {
        hideAllViews();
        approvalQueueView.setVisible(true);
        approvalQueueView.setManaged(true);
        loadPendingApprovals();
    }
    
    @FXML
    public void showUserManagement() {
        hideAllViews();
        userManagementView.setVisible(true);
        userManagementView.setManaged(true);
    }
    
    @FXML
    public void showAnalytics() {
        hideAllViews();
        analyticsView.setVisible(true);
        analyticsView.setManaged(true);
        loadAnalyticsData();
    }

    @FXML
    public void showDebtors() {
        hideAllViews();
        if (debtorsView != null) {
            debtorsView.setVisible(true);
            debtorsView.setManaged(true);
        }
        if (debtorsViewController != null) {
            debtorsViewController.loadDebtorsData();
        }
    }
    
    private void hideAllViews() {
        if (inventoryManagement != null) {
            inventoryManagement.setVisible(false);
            inventoryManagement.setManaged(false);
        }
        if (stockInForm != null) {
            stockInForm.setVisible(false);
            stockInForm.setManaged(false);
        }
        if (userManagementView != null) {
            userManagementView.setVisible(false);
            userManagementView.setManaged(false);
        }
        if (debtorsView != null) {
            debtorsView.setVisible(false);
            debtorsView.setManaged(false);
        }
        if (stockVerifyView != null) {
            stockVerifyView.setVisible(false);
            stockVerifyView.setManaged(false);
        }
        if (approvalQueueView != null) {
            approvalQueueView.setVisible(false);
            approvalQueueView.setManaged(false);
        }
        if (analyticsView != null) {
            analyticsView.setVisible(false);
            analyticsView.setManaged(false);
        }
    }
    
    @FXML
    public void onNavHover(javafx.scene.input.MouseEvent event) {
        // Handled cleanly via CSS .nav-button:hover
    }
    
    @FXML
    public void onNavExit(javafx.scene.input.MouseEvent event) {
        // Handled cleanly via CSS .nav-button
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
            dialog.sizeToScene();
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
        String timeframe = "Last 7 Days";
        if (timeframeSelector != null && timeframeSelector.getValue() != null) {
            timeframe = timeframeSelector.getValue();
        }
        refreshAnalytics(timeframe);
    }

    private void refreshAnalytics(String timeframe) {
        try {
            java.time.LocalDate startDate = java.time.LocalDate.now();
            java.time.LocalDate endDate = java.time.LocalDate.now();
            boolean groupByMonth = false;
            
            switch (timeframe) {
                case "Today" -> {
                    startDate = java.time.LocalDate.now();
                    endDate = java.time.LocalDate.now();
                }
                case "Last 7 Days" -> {
                    startDate = java.time.LocalDate.now().minusDays(6);
                    endDate = java.time.LocalDate.now();
                }
                case "This Month" -> {
                    startDate = java.time.LocalDate.now().withDayOfMonth(1);
                    endDate = java.time.LocalDate.now();
                }
                case "This Year" -> {
                    startDate = java.time.LocalDate.now().withDayOfYear(1);
                    endDate = java.time.LocalDate.now();
                    groupByMonth = true;
                }
            }
            
            double revenue = analyticsService.getRevenue(startDate, endDate);
            int transactions = analyticsService.getTransactionCount(startDate, endDate);
            int totalProductsCount = dbManager.getAllProducts().size();
            double netProfit = analyticsService.calculateNetProfit(startDate, endDate);
            
            todayRevenueLabel.setText("KSh" + String.format("%,.2f", revenue));
            todayTransactionsLabel.setText(String.valueOf(transactions));
            if (analyticsProductsLabel != null) {
                analyticsProductsLabel.setText(String.valueOf(totalProductsCount));
            }
            netProfitLabel.setText("KSh" + String.format("%,.2f", netProfit));
            
            // Update Top Categories list
            List<Map<String, Object>> topCategories = analyticsService.getTopSellingCategories();
            topCategoriesList.getItems().clear();
            for (Map<String, Object> cat : topCategories) {
                topCategoriesList.getItems().add(cat.get("category") + " (" + cat.get("totalSold") + " items)");
            }
            
            // Update LineChart (Revenue)
            List<Map<String, Object>> revenueData = analyticsService.getRevenueChartData(startDate, endDate, groupByMonth);
            javafx.scene.chart.XYChart.Series<String, Number> series = new javafx.scene.chart.XYChart.Series<>();
            series.setName("Revenue (" + timeframe + ")");
            
            // Clear categories if X-axis is CategoryAxis to prevent scrambled/clumped labels
            if (revenueChart.getXAxis() instanceof javafx.scene.chart.CategoryAxis) {
                ((javafx.scene.chart.CategoryAxis) revenueChart.getXAxis()).getCategories().clear();
            }
            
            for (Map<String, Object> data : revenueData) {
                String dateString = (String) data.get("date");
                Number revVal = (Number) data.get("revenue");
                
                String formattedLabel = dateString; // Fallback
                try {
                    switch (timeframe) { // Use ComboBox value variable
                        case "This Year":
                            // Assuming DB returns "YYYY-MM"
                            YearMonth ym = YearMonth.parse(dateString);
                            formattedLabel = ym.format(DateTimeFormatter.ofPattern("MMM")); // "Jan", "Feb"
                            break;
                        case "Last 7 Days":
                            // Assuming DB returns "YYYY-MM-DD"
                            LocalDate ld7 = LocalDate.parse(dateString);
                            formattedLabel = ld7.format(DateTimeFormatter.ofPattern("EEE")); // "Mon", "Tue"
                            break;
                        case "This Month":
                            // Assuming DB returns "YYYY-MM-DD"
                            LocalDate ldMonth = LocalDate.parse(dateString);
                            formattedLabel = ldMonth.format(DateTimeFormatter.ofPattern("d MMM")); // "12 Jun"
                            break;
                        case "Today":
                            // Assuming DB returns hour as "HH" or "YYYY-MM-DD HH"
                            if (dateString.contains("-") && !dateString.contains(" ")) {
                                LocalDate ldToday = LocalDate.parse(dateString);
                                formattedLabel = ldToday.format(DateTimeFormatter.ofPattern("d MMM"));
                            } else {
                                int hour = Integer.parseInt(dateString.contains(" ") ? dateString.split(" ")[1] : dateString);
                                LocalTime time = LocalTime.of(hour, 0);
                                formattedLabel = time.format(DateTimeFormatter.ofPattern("h a")); // "2 PM"
                            }
                            break;
                    }
                } catch (Exception e) {
                    System.err.println("Date parsing error for label: " + dateString);
                    // Keep the fallback raw string if parsing fails
                }
                
                series.getData().add(new javafx.scene.chart.XYChart.Data<>(formattedLabel, revVal));
            }
            revenueChart.getData().clear();
            revenueChart.getData().add(series);
            
            // Update PieChart (Top Items)
            List<Map<String, Object>> topItems = analyticsService.getTop5SellingItems();
            ObservableList<javafx.scene.chart.PieChart.Data> pieData = FXCollections.observableArrayList();
            for (Map<String, Object> item : topItems) {
                pieData.add(new javafx.scene.chart.PieChart.Data((String)item.get("productName"), (Integer)item.get("quantity")));
            }
            topItemsChart.setData(pieData);
            
        } catch (Exception e) {
            logger.error("Error refreshing analytics for timeframe: " + timeframe, e);
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
            stage.setResizable(true);
            stage.show();
            stage.setMaximized(true);
            
        } catch (Exception e) {
            logger.error("Error loading POS screen", e);
            showError("Error returning to POS");
        }
    }

    @FXML
    private void switchToPOS(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pos.fxml"));
            Parent root = loader.load();
            POSController posController = loader.getController();
            authService.getCurrentUser().ifPresent(posController::setCurrentUser);
            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Vegas POS - Cashier Terminal");
            stage.setResizable(true);
            stage.show();
            stage.setMaximized(true);
        } catch (Exception e) {
            logger.error("Error loading POS terminal", e);
            showError("Error opening POS terminal");
        }
    }
    
    @FXML
    private void handleLogout() {
        try {
            // 1. Get the existing stage (Do NOT close it)
            Stage stage = (Stage) contentArea.getScene().getWindow();

            // 2. Load the login screen
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();

            // 3. CRITICAL: Pass the stage back to the new LoginController!
            LoginController loginController = loader.getController();
            loginController.setPrimaryStage(stage);
            loginController.clearFields();

            // 4. Swap the scene on the existing stage (NO new Stage())
            stage.setScene(new Scene(root));
            stage.setTitle("Vegas POS - Login");
            stage.setResizable(false);
            stage.sizeToScene();
            stage.centerOnScreen();
            stage.show();

            // 5. Clear auth state
            authService.logout();

            logger.info("User logged out - scene swapped on existing stage");
        } catch (Exception e) {
            logger.error("Error loading login screen", e);
        }
    }

    @FXML
    private void toggleTheme() {
        com.pos.util.BrandingConstants.isDarkMode = !com.pos.util.BrandingConstants.isDarkMode;
        applyTheme();
    }

    private void applyTheme() {
        if (themeToggleButton != null && themeToggleButton.getScene() != null) {
            javafx.scene.Parent root = themeToggleButton.getScene().getRoot();
            if (com.pos.util.BrandingConstants.isDarkMode) {
                if (!root.getStyleClass().contains("dark-theme")) {
                    root.getStyleClass().add("dark-theme");
                }
                if (!root.getStyleClass().contains("dark-mode")) {
                    root.getStyleClass().add("dark-mode");
                }
                themeToggleButton.setText("☀️ Light Mode");
            } else {
                root.getStyleClass().remove("dark-theme");
                root.getStyleClass().remove("dark-mode");
                themeToggleButton.setText("🌙 Dark Mode");
            }
        }
    }

    @FXML
    private void toggleTextSize() {
        isLargeText = !isLargeText;
        if (contentArea != null && contentArea.getScene() != null) {
            javafx.scene.Scene scene = contentArea.getScene();
            if (isLargeText) {
                if (!scene.getRoot().getStyleClass().contains("large-text")) {
                    scene.getRoot().getStyleClass().add("large-text");
                }
            } else {
                scene.getRoot().getStyleClass().remove("large-text");
            }
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