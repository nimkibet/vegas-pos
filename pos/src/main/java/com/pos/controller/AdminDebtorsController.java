package com.pos.controller;

import com.pos.database.DatabaseManager;
import com.pos.entity.Customer;
import com.pos.entity.CustomerDebtSummary;
import com.pos.util.BrandingConstants;
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
import java.util.List;
import java.util.Optional;

public class AdminDebtorsController {
    private static final Logger logger = LoggerFactory.getLogger(AdminDebtorsController.class);

    @FXML private TableView<CustomerDebtSummary> debtorsTable;
    @FXML private TableColumn<CustomerDebtSummary, String>  colCustomerName;
    @FXML private TableColumn<CustomerDebtSummary, String>  colPhone;
    @FXML private TableColumn<CustomerDebtSummary, Integer> colPendingBatches;
    @FXML private TableColumn<CustomerDebtSummary, String>  colTotalDebt;
    @FXML private TableColumn<CustomerDebtSummary, String>  colCreditLimit;
    @FXML private TableColumn<CustomerDebtSummary, String>  colLimitStatus;
    @FXML private Label totalPortfolioLabel;

    private final DatabaseManager dbManager = DatabaseManager.getInstance();
    private final ObservableList<CustomerDebtSummary> debtorList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTable();
        loadDebtorsData();
    }

    private void setupTable() {
        debtorsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        colCustomerName.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getCustomer().getName()));

        colPhone.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getCustomer().getPhone() != null
                ? d.getValue().getCustomer().getPhone() : "—"));

        colPendingBatches.setCellValueFactory(new PropertyValueFactory<>("pendingBatches"));

        colTotalDebt.setCellValueFactory(d ->
            new SimpleStringProperty(BrandingConstants.CURRENCY_SYMBOL
                + String.format("%.2f", d.getValue().getTotalDebt())));

        // Credit Limit column: "Unlimited" when 0
        colCreditLimit.setCellValueFactory(d -> {
            double limit = d.getValue().getCustomer().getCreditLimit();
            return new SimpleStringProperty(limit <= 0 ? "Unlimited"
                : BrandingConstants.CURRENCY_SYMBOL + String.format("%.2f", limit));
        });

        // Status column with colour coding
        colLimitStatus.setCellValueFactory(d -> {
            Customer c = d.getValue().getCustomer();
            BigDecimal debt = d.getValue().getTotalDebt();
            double limit = c.getCreditLimit();
            if (limit <= 0) return new SimpleStringProperty("✓ No Limit");
            double ratio = debt.doubleValue() / limit;
            if (ratio >= 1.0) return new SimpleStringProperty("✗ Over Limit");
            if (ratio >= 0.8) return new SimpleStringProperty("⚠ Near Limit");
            return new SimpleStringProperty("✓ OK");
        });

        colLimitStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) { setText(null); setStyle(""); return; }
                setText(val);
                if (val.startsWith("✗"))      setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                else if (val.startsWith("⚠")) setStyle("-fx-text-fill: #d97706; -fx-font-weight: bold;");
                else                          setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
            }
        });

        debtorsTable.setItems(debtorList);
    }

    public void loadDebtorsData() {
        try {
            List<CustomerDebtSummary> debtors = dbManager.getAllDebtors();
            debtorList.setAll(debtors);

            BigDecimal total = debtors.stream()
                .map(CustomerDebtSummary::getTotalDebt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalPortfolioLabel != null) {
                totalPortfolioLabel.setText(BrandingConstants.CURRENCY_SYMBOL
                    + String.format("%.2f", total));
            }
            logger.info("Loaded {} debtors, total portfolio KSh {}", debtors.size(), total);
        } catch (SQLException e) {
            logger.error("Error loading debtors data", e);
            showError("Could not load debtors: " + e.getMessage());
        }
    }

    @FXML
    private void handleRefresh() {
        loadDebtorsData();
    }

    /**
     * Opens a simple TextInputDialog so the manager can type a KSh credit limit.
     * Saves immediately. Enter 0 (or leave blank) for unlimited.
     */
    @FXML
    private void handleEditCreditLimit() {
        CustomerDebtSummary selected = debtorsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Select a debtor row first.");
            return;
        }
        Customer customer = selected.getCustomer();
        double current = customer.getCreditLimit();

        TextInputDialog dlg = new TextInputDialog(current <= 0 ? "" : String.format("%.2f", current));
        dlg.setTitle("Edit Credit Limit");
        dlg.setHeaderText("Customer: " + customer.getName());
        dlg.setContentText("Maximum credit (KSh) — enter 0 or leave blank for unlimited:");

        Optional<String> result = dlg.showAndWait();
        result.ifPresent(input -> {
            try {
                double newLimit = input.trim().isEmpty() ? 0.0 : Double.parseDouble(input.trim());
                if (newLimit < 0) { showError("Credit limit cannot be negative."); return; }
                dbManager.updateCustomerCreditLimit(customer.getId(), newLimit);
                customer.setCreditLimit(newLimit);
                debtorsTable.refresh(); // update status badges immediately
                loadDebtorsData();      // recalculate portfolio total
                showInfo("Credit limit for " + customer.getName() + " updated to "
                    + (newLimit <= 0 ? "Unlimited" : BrandingConstants.CURRENCY_SYMBOL
                        + String.format("%.2f", newLimit)) + ".");
            } catch (NumberFormatException ex) {
                showError("Invalid number — enter a numeric amount (e.g. 5000).");
            } catch (SQLException ex) {
                logger.error("Failed to update credit limit for {}", customer.getId(), ex);
                showError("Could not save: " + ex.getMessage());
            }
        });
    }

    @FXML
    private void handleViewLedger() {
        showInfo("Select a customer and navigate to their full ledger.");
    }

    @FXML
    private void handleRecordPayment() {
        showInfo("Use the customer's individual ledger to record a payment.");
    }

    // ---- helpers ----

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setTitle("Debtors"); a.setHeaderText(null); a.showAndWait();
    }

    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setTitle("Debtors"); a.setHeaderText(null); a.showAndWait();
    }
}
