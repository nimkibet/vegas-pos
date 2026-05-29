package com.pos.controller;

import com.pos.database.DatabaseManager;
import com.pos.entity.ActivityLog;
import com.pos.entity.Customer;
import com.pos.entity.Sale;
import com.pos.service.AuthenticationService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CustomerLedgerController {
    private static final Logger logger = LoggerFactory.getLogger(CustomerLedgerController.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML private ComboBox<Customer> customerDropdown;
    @FXML private TableView<Sale> unpaidSalesTable;
    @FXML private TableColumn<Sale, String> saleIdColumn;
    @FXML private TableColumn<Sale, String> dateColumn;
    @FXML private TableColumn<Sale, String> totalAmountColumn;
    @FXML private TableColumn<Sale, String> balanceDueColumn;
    @FXML private TextField paymentAmountField;
    @FXML private Label totalDebtLabel;

    private final DatabaseManager dbManager = DatabaseManager.getInstance();
    private final AuthenticationService authService = AuthenticationService.getInstance();
    private final ObservableList<Sale> unpaidSales = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTable();
        loadCustomers();
        
        customerDropdown.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadUnpaidSales(newVal.getId());
            } else {
                unpaidSales.clear();
                totalDebtLabel.setText("Total Debt: KSh 0.00");
            }
        });
    }

    private void setupTable() {
        saleIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        dateColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getCreatedAt().format(DATE_FORMATTER)));
        totalAmountColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(String.format("%.2f", cellData.getValue().getTotal())));
        balanceDueColumn.setCellValueFactory(cellData -> {
            Sale sale = cellData.getValue();
            BigDecimal balance = calculateBalance(sale);
            return new SimpleStringProperty(String.format("%.2f", balance));
        });
        
        unpaidSalesTable.setItems(unpaidSales);
    }

    private BigDecimal calculateBalance(Sale sale) {
        try {
            BigDecimal totalPaid = dbManager.getTotalPaidForSale(sale.getId());
            BigDecimal balance = sale.getTotal().subtract(totalPaid);
            // Ensure we don't show negative balances due to precision
            return balance.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : balance;
        } catch (SQLException e) {
            logger.error("Error calculating balance for sale {}", sale.getId(), e);
            return sale.getTotal();
        }
    }

    private void loadCustomers() {
        try {
            List<Customer> customers = dbManager.getAllCustomers();
            customerDropdown.setItems(FXCollections.observableArrayList(customers));
        } catch (SQLException e) {
            logger.error("Error loading customers", e);
            showAlert("Database Error", "Could not load customers: " + e.getMessage());
        }
    }

    private void loadUnpaidSales(String customerId) {
        try {
            List<Sale> sales = dbManager.getUnpaidSalesForCustomer(customerId);
            unpaidSales.setAll(sales);
            
            BigDecimal totalDebt = BigDecimal.ZERO;
            for (Sale s : sales) {
                totalDebt = totalDebt.add(calculateBalance(s));
            }
            totalDebtLabel.setText(String.format("Total Debt: KSh %.2f", totalDebt));
            
            unpaidSalesTable.refresh();
        } catch (SQLException e) {
            logger.error("Error loading unpaid sales", e);
            showAlert("Database Error", "Could not load unpaid sales: " + e.getMessage());
        }
    }

    @FXML
    private void handleApplyPayment() {
        Customer selectedCustomer = customerDropdown.getValue();
        if (selectedCustomer == null) {
            showAlert("Selection Error", "Please select a customer first.");
            return;
        }

        String amountText = paymentAmountField.getText();
        if (amountText == null || amountText.trim().isEmpty()) {
            showAlert("Input Error", "Please enter a payment amount.");
            return;
        }

        try {
            BigDecimal totalInputAmount = new BigDecimal(amountText.trim());
            if (totalInputAmount.compareTo(BigDecimal.ZERO) <= 0) {
                showAlert("Input Error", "Payment amount must be greater than zero.");
                return;
            }

            // 1. Fetch CURRENT data from DB to ensure we have fresh balances
            List<Sale> unpaidSalesList = dbManager.getUnpaidSalesForCustomer(selectedCustomer.getId());
            if (unpaidSalesList.isEmpty()) {
                showAlert("Payment Info", "No unpaid sales found for this customer.");
                return;
            }

            BigDecimal remainingPayment = totalInputAmount;
            int paymentsMade = 0;
            BigDecimal actualAmountApplied = BigDecimal.ZERO;

            // 2. Waterfall Loop
            for (Sale sale : unpaidSalesList) {
                if (remainingPayment.compareTo(BigDecimal.ZERO) <= 0) break;

                // Always re-calculate balance to be safe
                BigDecimal balanceDue = calculateBalance(sale);

                // If balance is already 0 or negative for some reason, skip
                if (balanceDue.compareTo(BigDecimal.ZERO) <= 0) continue;

                BigDecimal amountToApply;
                if (remainingPayment.compareTo(balanceDue) >= 0) {
                    amountToApply = balanceDue; // Pay off this full balance
                } else {
                    amountToApply = remainingPayment; // Partial payment
                }

                logger.info("Applying waterfall payment: Sale={}, Balance={}, Applying={}", 
                        sale.getId(), balanceDue, amountToApply);

                boolean success = dbManager.recordCustomerPayment(sale.getId(), amountToApply);
                if (!success) {
                    showAlert("Database Error", "Failed to save payment for Sale " + sale.getId().substring(0, 8));
                    loadUnpaidSales(selectedCustomer.getId());
                    return; 
                }
                
                remainingPayment = remainingPayment.subtract(amountToApply);
                actualAmountApplied = actualAmountApplied.add(amountToApply);
                paymentsMade++;
            }

            if (paymentsMade == 0) {
                showAlert("Payment Info", "No balances were due to be paid. Please check the customer's records.");
                return;
            }

            // 3. Log activity
            String details = String.format("Waterfall Payment: %s - Total Input: %s - Total Applied: %s", 
                    selectedCustomer.getName(), totalInputAmount.toPlainString(), actualAmountApplied.toPlainString());
            
            com.pos.entity.User currentUser = authService.getCurrentUser().orElse(null);
            String userId = currentUser != null ? currentUser.getId() : "system";
            String userName = currentUser != null ? currentUser.getFullName() : "System";

            ActivityLog log = new ActivityLog(userId, userName, ActivityLog.ActionType.DEBT_PAYMENT, "Waterfall Payment", details);
            dbManager.insertActivityLog(log);

            // 4. Final Hard UI Refresh
            paymentAmountField.clear();
            loadUnpaidSales(selectedCustomer.getId());
            
            showInfo("Payment Success", String.format("Payment of KSh %s distributed across %d debts.", 
                    actualAmountApplied.toPlainString(), paymentsMade));
            
        } catch (NumberFormatException e) {
            showAlert("Input Error", "Invalid payment amount format.");
        } catch (SQLException e) {
            logger.error("Error logging waterfall payment activity", e);
            showAlert("Database Error", "Payment saved but failed to log activity: " + e.getMessage());
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
