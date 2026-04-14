package com.pos.controller;

import com.pos.database.DatabaseManager;
import com.pos.entity.Product;
import com.pos.entity.Sale;
import com.pos.entity.SaleItem;
import com.pos.entity.User;
import com.pos.entity.ActivityLog;
import com.pos.service.AnalyticsService;
import com.pos.service.AuthenticationService;
import com.pos.service.PrinterService;
import com.pos.controller.LoginController;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Admin Controller
 * Handles the admin dashboard, transaction history, and refunds.
 */
public class AdminController {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);
    
    @FXML private Label todayRevenueLabel;
    @FXML private Label todayTransactionsLabel;
    @FXML private Label totalProductsLabel;
    @FXML private Label totalUsersLabel;
    
    @FXML private LineChart<String, Number> revenueChart;
    @FXML private PieChart topItemsChart;
    
    @FXML private TableView<Map<String, Object>> recentSalesTable;
    @FXML private TableColumn<Map, String> colSaleId;
    @FXML private TableColumn<Map, String> colSaleDate;
    @FXML private TableColumn<Map, String> colItemCount;
    @FXML private TableColumn<Map, String> colSaleTotal;
    @FXML private TableColumn<Map, String> colSyncStatus;
    
    @FXML private TextField historySearchField;
    @FXML private DatePicker historyDatePicker;
    @FXML private TableView<Sale> historyTable;
    @FXML private TableColumn<Sale, String> colHistoryId;
    @FXML private TableColumn<Sale, String> colHistoryDate;
    @FXML private TableColumn<Sale, String> colHistoryCashier;
    @FXML private TableColumn<Sale, Integer> colHistoryItems;
    @FXML private TableColumn<Sale, String> colHistoryTotal;
    @FXML private TableColumn<Sale, String> colHistoryPayment;
    @FXML private TableColumn<Sale, String> colHistoryStatus;
    
    @FXML private TableView<Product> lowStockTable;
    @FXML private TableColumn<Product, String> colLowStockBarcode;
    @FXML private TableColumn<Product, String> colLowStockName;
    @FXML private TableColumn<Product, Integer> colLowStockQty;
    @FXML private TableColumn<Product, Integer> colLowStockMin;
    @FXML private TableColumn<Product, String> colLowStockRetail;
    @FXML private TableColumn<Product, String> colLowStockCategory;
    
    @FXML private TableView<Product> pendingApprovalsTable;
    @FXML private TableColumn<Product, String> colPendingBarcode;
    @FXML private TableColumn<Product, String> colPendingName;
    @FXML private TableColumn<Product, String> colPendingCategory;
    @FXML private TableColumn<Product, String> colPendingRetail;
    @FXML private TableColumn<Product, String> colPendingWholesale;
    @FXML private TableColumn<Product, Integer> colPendingStock;
    @FXML private TableColumn<Product, String> colPendingAddedBy;
    
    @FXML private TextField userSearchField;
    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, String> colUserUsername;
    @FXML private TableColumn<User, String> colUserFullName;
    @FXML private TableColumn<User, String> colUserRole;
    @FXML private TableColumn<User, Boolean> colUserActive;
    @FXML private TableColumn<User, String> colUserCreatedAt;
    
    private final AnalyticsService analyticsService;
    private final DatabaseManager dbManager;
    private final AuthenticationService authService;
    private final PrinterService printerService;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    
    private ObservableList<Sale> historyList;
    private ObservableList<Product> lowStockList;
    private ObservableList<Product> pendingApprovalsList;
    private ObservableList<User> userList;
    private Stage primaryStage;
    private User currentUser;
    
    public AdminController() {
        this.analyticsService = AnalyticsService.getInstance();
        this.dbManager = DatabaseManager.getInstance();
        this.authService = AuthenticationService.getInstance();
        this.printerService = PrinterService.getInstance();
        this.historyList = FXCollections.observableArrayList();
        this.lowStockList = FXCollections.observableArrayList();
        this.pendingApprovalsList = FXCollections.observableArrayList();
        this.userList = FXCollections.observableArrayList();
    }
    
    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }
    
    /**
     * Set current user - called AFTER FXML load completes
     */
    public void setCurrentUser(User user) {
        this.currentUser = user;
    }
    
    @FXML
    public void initialize() {
        setupHistoryTable();
        setupLowStockTable();
        setupPendingApprovalsTable();
        setupUserTable();
        loadDashboardData();
    }
    
    private void setupUserTable() {
        colUserUsername.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getUsername()));
        colUserFullName.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getFullName()));
        colUserRole.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getRole().getDisplayName()));
        colUserActive.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleObjectProperty<>(cell.getValue().isActive()));
        colUserCreatedAt.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getCreatedAt() != null ? 
                    cell.getValue().getCreatedAt().toLocalDate().toString() : ""));
        
        usersTable.setItems(userList);
    }
    
    private void setupPendingApprovalsTable() {
        colPendingBarcode.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getBarcode()));
        colPendingName.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getName()));
        colPendingCategory.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getCategory()));
        colPendingRetail.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty("KSh" + cell.getValue().getRetailPrice().toPlainString()));
        colPendingWholesale.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty("KSh" + cell.getValue().getWholesalePrice().toPlainString()));
        colPendingStock.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleObjectProperty<>(cell.getValue().getStockQuantity()));
        colPendingAddedBy.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getCreatedAt() != null ? 
                cell.getValue().getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : ""));
        
        pendingApprovalsTable.setItems(pendingApprovalsList);
    }
    
    private void setupHistoryTable() {
        colHistoryId.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getId()));
        colHistoryDate.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getCreatedAt().format(dateFormatter)));
        colHistoryCashier.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getUserId() != null ? cell.getValue().getUserId().substring(0, 8) : "N/A"));
        colHistoryItems.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleObjectProperty<>(cell.getValue().getItemCount()));
        colHistoryTotal.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty("KSh" + cell.getValue().getTotal().toPlainString()));
        colHistoryPayment.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getPaymentMethod().getDisplayName()));
        colHistoryStatus.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getStatusDisplay()));
        
        historyTable.setItems(historyList);
    }
    
    private void setupLowStockTable() {
        colLowStockBarcode.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getBarcode()));
        colLowStockName.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getName()));
        colLowStockQty.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleObjectProperty<>(cell.getValue().getStockQuantity()));
        colLowStockMin.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleObjectProperty<>(cell.getValue().getMinStockLevel()));
        colLowStockRetail.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty("KSh" + cell.getValue().getRetailPrice().toPlainString()));
        colLowStockCategory.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getCategory()));
        
        lowStockTable.setItems(lowStockList);
    }
    
    private void loadDashboardData() {
        loadStatsCards();
        loadRevenueChart();
        loadTopItemsChart();
        loadRecentSales();
        loadHistory();
        loadLowStock();
        loadPendingApprovals();
        loadUsers();
    }
    
    private void loadStatsCards() {
        try {
            double todayRevenue = analyticsService.getTodayRevenue();
            int todayTransactions = analyticsService.getTodayTransactionCount();
            int totalProducts = 0;
            int totalUsers = 0;
            try {
                totalProducts = dbManager.getAllProducts().size();
                totalUsers = dbManager.getAllUsers().size();
            } catch (Exception e) {
                logger.warn("Could not load product/user counts", e);
            }
            
            todayRevenueLabel.setText(String.format("KSh%.2f", todayRevenue));
            todayTransactionsLabel.setText(String.valueOf(todayTransactions));
            totalProductsLabel.setText(String.valueOf(totalProducts));
            totalUsersLabel.setText(String.valueOf(totalUsers));
        } catch (Exception e) {
            logger.error("Error loading stats cards", e);
        }
    }
    
    private void loadRevenueChart() {
        List<Map<String, Object>> revenueData = analyticsService.getLast7DaysRevenue();
        
        var series = new javafx.scene.chart.XYChart.Series<String, Number>();
        series.setName("Revenue");
        
        for (Map<String, Object> dayData : revenueData) {
            String date = (String) dayData.get("date");
            double revenue = (Double) dayData.get("revenue");
            String displayDate = LocalDate.parse(date).toString();
            series.getData().add(new javafx.scene.chart.XYChart.Data<>(displayDate, revenue));
        }
        
        revenueChart.getData().clear();
        revenueChart.getData().add(series);
        
        if (revenueChart.getXAxis() instanceof CategoryAxis) {
            ((CategoryAxis) revenueChart.getXAxis()).setTickLabelRotation(45);
        }
    }
    
    private void loadTopItemsChart() {
        List<Map<String, Object>> topItems = analyticsService.getTop5SellingItems();
        
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        
        for (Map<String, Object> item : topItems) {
            String productName = (String) item.get("productName");
            int quantity = (Integer) item.get("quantity");
            pieData.add(new PieChart.Data(productName + " (" + quantity + ")", quantity));
        }
        
        topItemsChart.setData(pieData);
        topItemsChart.setClockwise(true);
    }
    
    private void loadRecentSales() {
        List<Map<String, Object>> recentSales = dbManager.getRecentSales(20);
        
        ObservableList<Map<String, Object>> salesData = FXCollections.observableArrayList(recentSales);
        
        colSaleId.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty((String) cellData.getValue().get("saleId")));
        colSaleDate.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty((String) cellData.getValue().get("date")));
        colItemCount.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(String.valueOf(cellData.getValue().get("itemCount"))));
        colSaleTotal.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty("KSh" + String.format("%.2f", (Double) cellData.getValue().get("total"))));
        colSyncStatus.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty((Boolean) cellData.getValue().get("synced") ? "Synced" : "Pending"));
        
        recentSalesTable.setItems(salesData);
    }
    
    private void loadHistory() {
        try {
            List<Sale> allSales = dbManager.getTodaySales();
            historyList.clear();
            historyList.addAll(allSales);
            logger.info("History loaded with {} sales", allSales.size());
        } catch (Exception e) {
            logger.error("Error loading history", e);
        }
    }
    
    private void loadLowStock() {
        try {
            List<Product> lowStockProducts = dbManager.getLowStockProducts();
            lowStockList.clear();
            lowStockList.addAll(lowStockProducts);
            
            for (Product product : lowStockProducts) {
                logger.warn("Low stock alert: {} has only {} units (min: {})", 
                    product.getName(), product.getStockQuantity(), product.getMinStockLevel());
            }
        } catch (Exception e) {
            logger.error("Error loading low stock", e);
        }
    }
    
    @FXML
    private void handleHistorySearch() {
        String searchTerm = historySearchField.getText().trim();
        LocalDate selectedDate = historyDatePicker.getValue();
        
        try {
            List<Sale> allSales = dbManager.getTodaySales();
            List<Sale> filtered = new ArrayList<>();
            
            for (Sale sale : allSales) {
                boolean matches = true;
                
                if (!searchTerm.isEmpty()) {
                    matches = sale.getId().toLowerCase().contains(searchTerm.toLowerCase());
                }
                
                if (selectedDate != null && matches) {
                    matches = sale.getCreatedAt().toLocalDate().equals(selectedDate);
                }
                
                if (matches) {
                    filtered.add(sale);
                }
            }
            
            historyList.clear();
            historyList.addAll(filtered);
            
        } catch (Exception e) {
            logger.error("Error searching history", e);
            showError("Error searching history: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleViewSaleDetails() {
        Sale selected = historyTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a sale to view details");
            return;
        }
        
        try {
            List<SaleItem> items = dbManager.getSaleItems(selected.getId());
            selected.setItems(items);
            
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Sale Details");
            dialog.setHeaderText("Sale ID: " + selected.getId().substring(0, 8));
            
            VBox content = new VBox(10);
            content.setPadding(new Insets(20));
            
            Label dateLabel = new Label("Date: " + selected.getCreatedAt().format(dateFormatter));
            Label totalLabel = new Label("Total: KSh" + selected.getTotal().toPlainString());
            Label paymentLabel = new Label("Payment: " + selected.getPaymentMethod().getDisplayName());
            Label statusLabel = new Label("Status: " + selected.getStatus().getDisplayName());
            
            Separator sep = new Separator();
            
            Label itemsHeader = new Label("Items:");
            itemsHeader.setStyle("-fx-font-weight: bold;");
            
            StringBuilder itemsText = new StringBuilder();
            for (SaleItem item : items) {
                itemsText.append(String.format("%s x%d - KSh%s\n", 
                    item.getProductName(), item.getQuantity(), item.getTotalPrice().toPlainString()));
            }
            Label itemsLabel = new Label(itemsText.toString());
            
            content.getChildren().addAll(dateLabel, totalLabel, paymentLabel, statusLabel, sep, itemsHeader, itemsLabel);
            dialog.getDialogPane().setContent(content);
            
            ButtonType closeButton = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
            dialog.getDialogPane().getButtonTypes().add(closeButton);
            
            dialog.showAndWait();
            
        } catch (Exception e) {
            logger.error("Error viewing sale details", e);
            showError("Error viewing sale details: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleProcessRefund() {
        Sale selected = historyTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a sale to refund");
            return;
        }
        
        if (selected.getStatus() == Sale.Status.REFUNDED || selected.getStatus() == Sale.Status.VOIDED) {
            showError("This sale has already been refunded or voided");
            return;
        }
        
        if (!checkManagerAuthorization("Process Refund")) {
            return;
        }
        
        try {
            List<SaleItem> saleItems = dbManager.getSaleItems(selected.getId());
            selected.setItems(saleItems);
            
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Process Refund");
            dialog.setHeaderText("Select item to refund");
            
            VBox content = new VBox(10);
            content.setPadding(new Insets(20));
            
            Label saleInfo = new Label("Sale Total: KSh" + selected.getTotal().toPlainString());
            
            ListView<String> itemListView = new ListView<>();
            for (SaleItem item : saleItems) {
                String itemStr = String.format("%s x%d = KSh%s", 
                    item.getProductName(), item.getQuantity(), item.getTotalPrice().toPlainString());
                itemListView.getItems().add(itemStr);
            }
            itemListView.setPrefHeight(150);
            
            Label refundTypeLabel = new Label("Refund Type:");
            ToggleGroup refundTypeGroup = new ToggleGroup();
            RadioButton fullRefund = new RadioButton("Full Sale Refund");
            fullRefund.setToggleGroup(refundTypeGroup);
            fullRefund.setSelected(true);
            RadioButton partialRefund = new RadioButton("Item Refund");
            partialRefund.setToggleGroup(refundTypeGroup);
            
            content.getChildren().addAll(saleInfo, new Label("Items:"), itemListView, 
                refundTypeLabel, fullRefund, partialRefund);
            dialog.getDialogPane().setContent(content);
            
            ButtonType refundButton = new ButtonType("Process Refund", ButtonBar.ButtonData.OK_DONE);
            ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            dialog.getDialogPane().getButtonTypes().addAll(refundButton, cancelButton);
            
            dialog.showAndWait();
            
            if (fullRefund.isSelected()) {
                processFullRefund(selected);
            } else {
                int selectedIndex = itemListView.getSelectionModel().getSelectedIndex();
                if (selectedIndex >= 0 && selectedIndex < saleItems.size()) {
                    processItemRefund(selected, saleItems.get(selectedIndex));
                } else {
                    showError("Please select an item to refund");
                }
            }
        } catch (Exception e) {
            logger.error("Error processing refund", e);
            showError("Error processing refund: " + e.getMessage());
        }
    }
    
    private void processFullRefund(Sale originalSale) {
        try {
            Sale refundSale = new Sale(originalSale.getUserId());
            refundSale.setPaymentMethod(originalSale.getPaymentMethod());
            
            for (SaleItem item : originalSale.getItems()) {
                SaleItem refundItem = new SaleItem();
                refundItem.setProductId(item.getProductId());
                refundItem.setProductName(item.getProductName());
                refundItem.setProductBarcode(item.getProductBarcode());
                refundItem.setQuantity(item.getQuantity());
                refundItem.setUnitPrice(item.getUnitPrice().negate());
                refundItem.setTotalPrice(item.getTotalPrice().negate());
                refundSale.addItem(refundItem);
                
                Optional<Product> productOpt = dbManager.findProductById(item.getProductId());
                if (productOpt.isPresent()) {
                    Product product = productOpt.get();
                    dbManager.updateProductStock(product.getId(), product.getStockQuantity() + item.getQuantity());
                }
            }
            
            refundSale.setSubtotal(originalSale.getSubtotal().negate());
            refundSale.setTotal(originalSale.getTotal().negate());
            refundSale.setAmountPaid(originalSale.getTotal());
            refundSale.setStatus(Sale.Status.REFUNDED);
            refundSale.setNotes("Refund for sale: " + originalSale.getId().substring(0, 8));
            
            dbManager.insertSale(refundSale);
            
            originalSale.setStatus(Sale.Status.REFUNDED);
            
            printerService.printTransaction(refundSale);
            
            Optional<User> currentUser = authService.getCurrentUser();
            if (currentUser.isPresent()) {
                ActivityLog log = new ActivityLog(
                    currentUser.get().getId(),
                    currentUser.get().getFullName(),
                    ActivityLog.ActionType.REFUND,
                    "Full refund - Sale: " + originalSale.getId().substring(0, 8),
                    "Amount: " + originalSale.getTotal().toPlainString()
                );
                dbManager.insertActivityLog(log);
            }
            
            showSuccess("Full refund processed successfully!");
            loadHistory();
            
        } catch (Exception e) {
            logger.error("Error processing full refund", e);
            showError("Error processing refund: " + e.getMessage());
        }
    }
    
    private void processItemRefund(Sale originalSale, SaleItem itemToRefund) {
        try {
            Sale refundSale = new Sale(originalSale.getUserId());
            refundSale.setPaymentMethod(originalSale.getPaymentMethod());
            
            SaleItem refundItem = new SaleItem();
            refundItem.setProductId(itemToRefund.getProductId());
            refundItem.setProductName(itemToRefund.getProductName());
            refundItem.setProductBarcode(itemToRefund.getProductBarcode());
            refundItem.setQuantity(itemToRefund.getQuantity());
            refundItem.setUnitPrice(itemToRefund.getUnitPrice().negate());
            refundItem.setTotalPrice(itemToRefund.getTotalPrice().negate());
            refundSale.addItem(refundItem);
            
            refundSale.setSubtotal(itemToRefund.getTotalPrice().negate());
            refundSale.setTotal(itemToRefund.getTotalPrice().negate());
            refundSale.setAmountPaid(itemToRefund.getTotalPrice());
            refundSale.setStatus(Sale.Status.REFUNDED);
            refundSale.setNotes("Partial refund for sale: " + originalSale.getId().substring(0, 8));
            
            dbManager.insertSale(refundSale);
            
            Optional<Product> productOpt = dbManager.findProductById(itemToRefund.getProductId());
            if (productOpt.isPresent()) {
                Product product = productOpt.get();
                dbManager.updateProductStock(product.getId(), product.getStockQuantity() + itemToRefund.getQuantity());
            }
            
            printerService.printTransaction(refundSale);
            
            Optional<User> currentUser = authService.getCurrentUser();
            if (currentUser.isPresent()) {
                ActivityLog log = new ActivityLog(
                    currentUser.get().getId(),
                    currentUser.get().getFullName(),
                    ActivityLog.ActionType.REFUND,
                    "Item refund - " + itemToRefund.getProductName(),
                    "Qty: " + itemToRefund.getQuantity() + ", Amount: " + itemToRefund.getTotalPrice().toPlainString()
                );
                dbManager.insertActivityLog(log);
            }
            
            showSuccess("Item refund processed successfully!");
            loadHistory();
            
        } catch (Exception e) {
            logger.error("Error processing item refund", e);
            showError("Error processing refund: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleExportHistory() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Export Transaction History");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
            fileChooser.setInitialFileName("transaction_history_" + 
                LocalDate.now().toString() + ".csv");
            
            File file = fileChooser.showSaveDialog(primaryStage);
            
            if (file != null) {
                FileWriter writer = new FileWriter(file);
                writer.write("Sale ID,Date,Cashier,Items,Total,Payment Method,Status\n");
                
                for (Sale sale : historyList) {
                    writer.write(String.format("%s,%s,%s,%d,KSh%s,%s,%s\n",
                        sale.getId(),
                        sale.getCreatedAt().format(dateFormatter),
                        sale.getUserId() != null ? sale.getUserId().substring(0, 8) : "N/A",
                        sale.getItemCount(),
                        sale.getTotal().toPlainString(),
                        sale.getPaymentMethod().getDisplayName(),
                        sale.getStatus().getDisplayName()));
                }
                
                writer.close();
                showSuccess("History exported to " + file.getName());
            }
        } catch (Exception e) {
            logger.error("Error exporting history", e);
            showError("Error exporting: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleRefreshLowStock() {
        loadLowStock();
        showSuccess("Low stock list refreshed");
    }
    
    @FXML
    private void handleRefreshPendingApprovals() {
        loadPendingApprovals();
    }
    
    private void loadPendingApprovals() {
        try {
            pendingApprovalsList.clear();
            List<Product> pending = dbManager.getPendingProducts();
            pendingApprovalsList.addAll(pending);
            
            logger.info("Loaded {} pending product approvals", pending.size());
        } catch (Exception e) {
            logger.error("Error loading pending approvals", e);
            showError("Error loading pending approvals");
        }
    }
    
    @FXML
    private void handleApproveProduct() {
        Product selected = pendingApprovalsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a product to approve");
            return;
        }
        
        try {
            dbManager.approveProduct(selected.getId());
            
            Optional<User> currentUser = authService.getCurrentUser();
            if (currentUser.isPresent()) {
                ActivityLog log = new ActivityLog(
                    currentUser.get().getId(),
                    currentUser.get().getFullName(),
                    ActivityLog.ActionType.APPROVE_PRODUCT,
                    selected.getName(),
                    "Retail: " + selected.getRetailPrice().toPlainString() + ", Wholesale: " + selected.getWholesalePrice().toPlainString()
                );
                dbManager.insertActivityLog(log);
            }
            
            pendingApprovalsList.remove(selected);
            showSuccess("Product approved: " + selected.getName());
            
            loadLowStock();
            
        } catch (Exception e) {
            logger.error("Error approving product", e);
            showError("Error approving product: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleRejectProduct() {
        Product selected = pendingApprovalsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a product to reject");
            return;
        }
        
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Reject Product");
        confirmAlert.setHeaderText("Are you sure you want to reject this product?");
        confirmAlert.setContentText("Product: " + selected.getName());
        
        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        
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
            
            pendingApprovalsList.remove(selected);
            showSuccess("Product rejected: " + selected.getName());
            
        } catch (Exception e) {
            logger.error("Error rejecting product", e);
            showError("Error rejecting product: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleGenerateReorderList() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Reorder List");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"));
            fileChooser.setInitialFileName("reorder_list_" + 
                LocalDate.now().toString() + ".txt");
            
            File file = fileChooser.showSaveDialog(primaryStage);
            
            if (file != null) {
                FileWriter writer = new FileWriter(file);
                
                writer.write("=".repeat(60) + "\n");
                writer.write("LOW STOCK REORDER LIST\n");
                writer.write("Generated: " + LocalDateTime.now().format(dateFormatter) + "\n");
                writer.write("=".repeat(60) + "\n\n");
                
                for (Product product : lowStockList) {
                    writer.write(String.format("Product: %s\n", product.getName()));
                    writer.write(String.format("  Barcode: %s\n", product.getBarcode()));
                    writer.write(String.format("  Current Stock: %d\n", product.getStockQuantity()));
                    writer.write(String.format("  Minimum Level: %d\n", product.getMinStockLevel()));
                    writer.write(String.format("  Suggested Reorder: %d units\n", 
                        product.getMinStockLevel() * 2 - product.getStockQuantity()));
                    writer.write(String.format("  Category: %s\n", product.getCategory() != null ? product.getCategory() : "N/A"));
                    writer.write("-\n");
                }
                
                writer.write("\nTotal items needing reorder: " + lowStockList.size() + "\n");
                
                writer.close();
                showSuccess("Reorder list saved to " + file.getName());
            }
        } catch (Exception e) {
            logger.error("Error generating reorder list", e);
            showError("Error generating reorder list: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleRefresh() {
        loadDashboardData();
    }
    
    @FXML
    private void handleLogout() {
        try {
            Stage currentStage = primaryStage;
            currentStage.close();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();

            LoginController loginController = loader.getController();
            loginController.clearFields();

            Stage newStage = new Stage();
            newStage.setTitle("POS System - Login");
            newStage.setScene(new Scene(root));
            newStage.setWidth(420);
            newStage.setHeight(520);
            newStage.setResizable(false);
            newStage.centerOnScreen();
            newStage.show();

            authService.logout();

            logger.info("User logged out - completely new stage created");
        } catch (Exception e) {
            logger.error("Failed to logout", e);
        }
    }
    
    private boolean checkManagerAuthorization(String actionName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/manager_auth.fxml"));
            Parent root = loader.load();
            
            ManagerAuthController authController = loader.getController();
            
            Stage dialog = new Stage();
            dialog.setTitle("Manager Authorization");
            dialog.setScene(new Scene(root));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(primaryStage);
            dialog.setResizable(false);
            dialog.showAndWait();
            
            if (authController.isAuthenticated()) {
                logger.info("Manager authorization granted for action: {}", actionName);
                return true;
            } else {
                logger.warn("Manager authorization denied for action: {}", actionName);
                return false;
            }
        } catch (Exception e) {
            logger.error("Error showing manager auth dialog", e);
            showError("Failed to open authorization dialog");
            return false;
        }
    }
    
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    @FXML
    private void handleUserSearch() {
        String searchTerm = userSearchField.getText().trim().toLowerCase();
        
        try {
            List<User> allUsers = dbManager.getAllUsers();
            if (searchTerm.isEmpty()) {
                userList.clear();
                userList.addAll(allUsers);
            } else {
                List<User> filtered = allUsers.stream()
                    .filter(u -> u.getUsername().toLowerCase().contains(searchTerm) ||
                                (u.getFullName() != null && u.getFullName().toLowerCase().contains(searchTerm)))
                    .toList();
                userList.clear();
                userList.addAll(filtered);
            }
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
            loadUsers();
            showSuccess("User status changed");
        } catch (Exception e) {
            logger.error("Error toggling user status", e);
            showError("Error changing user status");
        }
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
            
            showSuccess("Password reset to default (password123)");
        } catch (Exception e) {
            logger.error("Error resetting password", e);
            showError("Error resetting password");
        }
    }
    
    private void loadUsers() {
        try {
            List<User> users = dbManager.getAllUsers();
            userList.clear();
            userList.addAll(users);
        } catch (Exception e) {
            logger.error("Error loading users", e);
        }
    }
    
    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Handle tab switch - refresh data when navigating to admin view
     */
    @FXML
    private void handleTabSwitch() {
        loadDashboardData();
        logger.info("Admin dashboard refreshed on tab switch");
    }
}