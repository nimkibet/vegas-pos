package com.pos.controller;

import com.pos.database.DatabaseManager;
import com.pos.entity.Customer;
import com.pos.entity.Sale;
import com.pos.util.BrandingConstants;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.List;

/**
 * Checkout Controller
 * Controller for the checkout/payment dialog.
 */
public class CheckoutController {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);

    public boolean isTransactionSuccessful = false;

    private Sale sale;
    private ObservableList<Customer> customerList = FXCollections.observableArrayList();

    @FXML
    private Label totalDueLabel;
    @FXML
    private ToggleButton cashRadio;
    @FXML
    private ToggleButton mpesaRadio;
    @FXML
    private ToggleButton splitRadio;
    @FXML
    private ToggleButton creditRadio;
    @FXML
    private ToggleGroup paymentGroup;
    @FXML
    private VBox customerSection;
    @FXML
    private ComboBox<Customer> customerDropdown;
    @FXML
    private TextField amountTenderedField;
    @FXML
    private VBox singlePaymentContainer;
    @FXML
    private VBox splitPaymentContainer;
    @FXML
    private TextField splitCashInput;
    @FXML
    private TextField splitMpesaInput;
    @FXML
    private Label changeLabel;
    @FXML
    private Label errorLabel;
    @FXML
    private Button confirmButton;
    @FXML
    private Button cancelButton;

    private boolean confirmed = false;
    private BigDecimal amountPaid;
    private BigDecimal change;
    private Sale.PaymentMethod paymentMethod;
    private BigDecimal cashAmount;
    private BigDecimal mpesaAmount;

    public void setSale(Sale sale) {
        this.sale = sale;
        totalDueLabel.setText(formatCurrency(sale.getTotal()));
    }

    @FXML
    private void initialize() {
        loadCustomers();
        setupCustomerDropdown();
        if (customerSection != null) {
            customerSection.setVisible(false);
            customerSection.setManaged(false);
        }
        if (customerDropdown != null) {
            customerDropdown.getSelectionModel().selectedItemProperty().addListener(
                    (obs, oldVal, newVal) -> handleCalculateChange());
        }
        if (paymentGroup != null) {
            paymentGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> handlePaymentMethodChanged());
        }
    }

    private void loadCustomers() {
        try {
            List<Customer> customers = DatabaseManager.getInstance().getAllCustomers();
            customerList = FXCollections.observableArrayList(customers);
        } catch (SQLException e) {
            logger.error("Error loading customers", e);
            customerList = FXCollections.observableArrayList();
        }
    }

    private void setupCustomerDropdown() {
        if (customerDropdown == null) {
            return;
        }
        customerDropdown.setItems(customerList);
        customerDropdown.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Customer customer, boolean empty) {
                super.updateItem(customer, empty);
                setText(empty || customer == null ? null : customer.getName());
            }
        });
        customerDropdown.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Customer customer, boolean empty) {
                super.updateItem(customer, empty);
                if (empty || customer == null) {
                    setText("Select Customer");
                } else {
                    setText(customer.getName());
                }
            }
        });
    }

    @FXML
    private void handlePaymentMethodChanged() {
        boolean splitSelected = splitRadio != null && splitRadio.isSelected();
        boolean creditSelected = creditRadio != null && creditRadio.isSelected();

        if (singlePaymentContainer != null) {
            singlePaymentContainer.setVisible(!splitSelected);
            singlePaymentContainer.setManaged(!splitSelected);
        }
        if (splitPaymentContainer != null) {
            splitPaymentContainer.setVisible(splitSelected);
            splitPaymentContainer.setManaged(splitSelected);
        }
        if (customerSection != null) {
            customerSection.setVisible(creditSelected);
            customerSection.setManaged(creditSelected);
        }

        if (splitSelected) {
            amountTenderedField.setDisable(true);
            if (splitCashInput != null) {
                splitCashInput.requestFocus();
            }
        } else {
            amountTenderedField.setDisable(false);
            if (!creditSelected) {
                amountTenderedField.requestFocus();
            } else if (customerDropdown != null) {
                customerDropdown.requestFocus();
            }
        }

        if (singlePaymentContainer != null && singlePaymentContainer.getScene() != null) {
            Stage stage = (Stage) singlePaymentContainer.getScene().getWindow();
            if (stage != null) {
                stage.sizeToScene();
            }
        }

        handleCalculateChange();
    }

    @FXML
    private void handleCalculateChange() {
        BigDecimal total = sale.getTotal();

        if (creditRadio != null && creditRadio.isSelected()) {
            changeLabel.setText("On account");
            changeLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: #718096;");
            boolean hasCustomer = customerDropdown != null
                    && customerDropdown.getSelectionModel().getSelectedItem() != null;
            confirmButton.setDisable(!hasCustomer);
            errorLabel.setText(hasCustomer ? "" : "Select a customer for credit sale");
            return;
        }

        if (splitRadio != null && splitRadio.isSelected()) {
            BigDecimal cashAmt = parseAmount(splitCashInput);
            BigDecimal mpesaAmt = parseAmount(splitMpesaInput);
            BigDecimal tendered = cashAmt.add(mpesaAmt);
            BigDecimal changeAmount = tendered.subtract(total);

            if (changeAmount.compareTo(BigDecimal.ZERO) < 0) {
                changeLabel.setText(formatCurrency(changeAmount));
                changeLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: #dc3545;");
                confirmButton.setDisable(true);
                errorLabel.setText("Total: " + formatCurrency(tendered) + " (insufficient)");
            } else {
                changeLabel.setText(formatCurrency(changeAmount));
                changeLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: #28a745;");
                confirmButton.setDisable(false);
                errorLabel.setText("");
            }
            return;
        }

        String tenderedStr = amountTenderedField.getText().trim();
        if (tenderedStr.isEmpty()) {
            changeLabel.setText(formatCurrency(BigDecimal.ZERO));
            confirmButton.setDisable(true);
            errorLabel.setText("");
            return;
        }

        try {
            BigDecimal tendered = new BigDecimal(tenderedStr);
            BigDecimal changeAmount = tendered.subtract(total);

            if (changeAmount.compareTo(BigDecimal.ZERO) < 0) {
                changeLabel.setText(formatCurrency(changeAmount));
                changeLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: #dc3545;");
                confirmButton.setDisable(true);
                errorLabel.setText("Insufficient amount tendered");
            } else {
                changeLabel.setText(formatCurrency(changeAmount));
                changeLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: #28a745;");
                confirmButton.setDisable(false);
                errorLabel.setText("");
            }
        } catch (NumberFormatException e) {
            changeLabel.setText(formatCurrency(BigDecimal.ZERO));
            confirmButton.setDisable(true);
            errorLabel.setText("Invalid amount");
        }
    }

    @FXML
    private void handleConfirmSale(ActionEvent event) {
        try {
            BigDecimal total = sale.getTotal();

            if (splitRadio != null && splitRadio.isSelected()) {
                BigDecimal cashAmt = parseAmount(splitCashInput);
                BigDecimal mpesaAmt = parseAmount(splitMpesaInput);
                BigDecimal tendered = cashAmt.add(mpesaAmt);

                if (tendered.compareTo(total) < 0) {
                    showError("Total payment must be at least " + formatCurrency(total));
                    return;
                }

                this.amountPaid = tendered;
                this.change = tendered.subtract(total);
                this.cashAmount = cashAmt;
                this.mpesaAmount = mpesaAmt;
                this.paymentMethod = Sale.PaymentMethod.CASH;

                sale.setPaymentMethod(paymentMethod);
                sale.setAmountPaid(amountPaid);
                sale.setChangeGiven(change);
                sale.setCashAmount(cashAmt);
                sale.setMpesaAmount(mpesaAmt);
                sale.setSecondaryPaymentMethod(Sale.PaymentMethod.MOBILE_MONEY);
                sale.setPaymentStatus("PAID");

            } else if (creditRadio != null && creditRadio.isSelected()) {
                Customer selectedCustomer = customerDropdown.getSelectionModel().getSelectedItem();
                if (selectedCustomer == null) {
                    showError("Please select a customer for credit sale");
                    return;
                }

                // --- Credit Limit Guard ---
                if (selectedCustomer.getCreditLimit() > 0) {
                    try {
                        BigDecimal currentBalance = DatabaseManager.getInstance()
                                .getCustomerCurrentBalance(selectedCustomer.getId());
                        BigDecimal newBalance = currentBalance.add(total);
                        BigDecimal limit = BigDecimal.valueOf(selectedCustomer.getCreditLimit());
                        if (newBalance.compareTo(limit) > 0) {
                            showError("Transaction Blocked: " + selectedCustomer.getName()
                                    + " has exceeded their credit limit!\n\n"
                                    + "Current balance: KSh " + String.format("%.2f", currentBalance)
                                    + "\nThis sale: KSh " + String.format("%.2f", total)
                                    + "\nCredit limit: KSh " + String.format("%.2f", limit));
                            return;
                        }
                    } catch (SQLException e) {
                        logger.error("Could not verify credit limit", e);
                    }
                }

                BigDecimal amountPaidDecimal = parseAmount(amountTenderedField);
                if (amountPaidDecimal.compareTo(total) > 0) {
                    showError("Down payment cannot exceed sale total");
                    return;
                }

                this.amountPaid = amountPaidDecimal;
                this.change = BigDecimal.ZERO;
                this.paymentMethod = Sale.PaymentMethod.CREDIT;

                sale.setPaymentMethod(paymentMethod);
                sale.setAmountPaid(amountPaidDecimal);
                sale.setChangeGiven(change);
                sale.setCustomerId(selectedCustomer.getId());
                sale.setPaymentStatus("CREDIT");

            } else {
                String tenderedStr = amountTenderedField.getText().trim();
                if (tenderedStr.isEmpty()) {
                    showError("Please enter amount tendered");
                    return;
                }

                BigDecimal tendered = new BigDecimal(tenderedStr);
                if (tendered.compareTo(total) < 0) {
                    showError("Amount tendered must be greater than or equal to total");
                    return;
                }

                this.amountPaid = tendered;
                this.change = tendered.subtract(total);
                this.paymentMethod = cashRadio.isSelected()
                        ? Sale.PaymentMethod.CASH
                        : Sale.PaymentMethod.MOBILE_MONEY;

                sale.setPaymentMethod(paymentMethod);
                sale.setAmountPaid(amountPaid);
                sale.setChangeGiven(change);
                sale.setPaymentStatus("PAID");
            }

            // Mark as successful
            isTransactionSuccessful = true;
            this.confirmed = true;

            // Close the modal window properly
            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.close();

        } catch (NumberFormatException e) {
            System.err.println("CHECKOUT ERROR: Invalid number format in payment fields.");
            showError("Invalid amount format entered.");
        } catch (Exception e) {
            System.err.println("CRITICAL CHECKOUT ERROR: " + e.getMessage());
            e.printStackTrace();
            showError("Critical checkout error: " + e.getMessage());
        }
    }

    @FXML
    private void handleQuickAddCustomer() {
        TextInputDialog nameDialog = new TextInputDialog();
        nameDialog.setTitle("Add New Customer");
        nameDialog.setHeaderText("Enter customer name:");
        nameDialog.setContentText("Name:");

        nameDialog.showAndWait().ifPresent(name -> {
            String customerName = name.trim();
            if (customerName.isEmpty()) {
                return;
            }

            TextInputDialog phoneDialog = new TextInputDialog();
            phoneDialog.setTitle("Add New Customer");
            phoneDialog.setHeaderText("Enter phone number for " + customerName + " (optional):");
            phoneDialog.setContentText("Phone:");
            String phoneNumber = phoneDialog.showAndWait().orElse("").trim();

            Customer newCustomer = new Customer();
            newCustomer.setName(customerName);
            newCustomer.setPhone(phoneNumber.isEmpty() ? null : phoneNumber);

            try {
                DatabaseManager.getInstance().insertCustomer(newCustomer);
                customerList.add(newCustomer);
                customerDropdown.getSelectionModel().select(newCustomer);
                handleCalculateChange();
                logger.info("Added new customer: {}", customerName);
            } catch (SQLException e) {
                logger.error("Failed to save customer", e);
                showError("Failed to save customer: " + e.getMessage());
            }
        });
    }

    @FXML
    private void handleCancel() {
        this.confirmed = false;
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: #e53e3e; -fx-font-size: 12;");
    }

    private BigDecimal parseAmount(TextField field) {
        if (field == null || field.getText().trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(field.getText().trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private String formatCurrency(BigDecimal amount) {
        return BrandingConstants.CURRENCY_SYMBOL + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public BigDecimal getChange() {
        return change;
    }

    public Sale.PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public BigDecimal getCashAmount() {
        return cashAmount;
    }

    public BigDecimal getMpesaAmount() {
        return mpesaAmount;
    }

    public boolean isSplitPayment() {
        return splitRadio != null && splitRadio.isSelected();
    }

    public boolean isCreditSale() {
        return creditRadio != null && creditRadio.isSelected();
    }
}
