package com.pos.controller;

import com.pos.entity.Sale;
import com.pos.util.BrandingConstants;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Checkout Controller
 * Controller for the checkout/payment dialog.
 */
public class CheckoutController {
    
    private static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);
    
    // Sale being processed
    private Sale sale;
    
    // FXML Components
    @FXML
    private Label totalDueLabel;
    @FXML
    private RadioButton cashRadio;
    @FXML
    private RadioButton mpesaRadio;
    @FXML
    private RadioButton splitRadio;
    @FXML
    private TextField amountTenderedField;
    @FXML
    private TextField cashAmountField;
    @FXML
    private TextField mpesaAmountField;
    @FXML
    private javafx.scene.layout.VBox splitPaymentFields;
    @FXML
    private Label changeLabel;
    @FXML
    private Label errorLabel;
    @FXML
    private Button confirmButton;
    @FXML
    private Button cancelButton;
    
    // Result
    private boolean confirmed = false;
    private BigDecimal amountPaid;
    private BigDecimal change;
    private Sale.PaymentMethod paymentMethod;
    private BigDecimal cashAmount;
    private BigDecimal mpesaAmount;
    
    /**
     * Set the sale to process
     */
    public void setSale(Sale sale) {
        this.sale = sale;
        totalDueLabel.setText(formatCurrency(sale.getTotal()));
    }
    
    /**
     * Handle payment method changed
     */
    @FXML
    private void handlePaymentMethodChanged() {
        // Show/hide split payment fields
        if (splitRadio != null && splitPaymentFields != null) {
            splitPaymentFields.setVisible(splitRadio.isSelected());
            if (splitRadio.isSelected()) {
                amountTenderedField.setDisable(true);
                if (cashAmountField != null) {
                    cashAmountField.requestFocus();
                }
            } else {
                amountTenderedField.setDisable(false);
                amountTenderedField.requestFocus();
            }
        }
        // Recalculate change when payment method changes
        handleCalculateChange();
    }
    
    /**
     * Handle calculate change
     */
    @FXML
    private void handleCalculateChange() {
        BigDecimal tendered = BigDecimal.ZERO;
        BigDecimal total = sale.getTotal();
        
        // Handle split payment
        if (splitRadio != null && splitRadio.isSelected()) {
            BigDecimal cashAmt = BigDecimal.ZERO;
            BigDecimal mpesaAmt = BigDecimal.ZERO;
            
            try {
                if (cashAmountField != null && !cashAmountField.getText().trim().isEmpty()) {
                    cashAmt = new BigDecimal(cashAmountField.getText().trim());
                }
            } catch (NumberFormatException e) {
                // ignore
            }
            
            try {
                if (mpesaAmountField != null && !mpesaAmountField.getText().trim().isEmpty()) {
                    mpesaAmt = new BigDecimal(mpesaAmountField.getText().trim());
                }
            } catch (NumberFormatException e) {
                // ignore
            }
            
            tendered = cashAmt.add(mpesaAmt);
            
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
        
        // Handle regular payment
        String tenderedStr = amountTenderedField.getText().trim();
        
        if (tenderedStr.isEmpty()) {
            changeLabel.setText(formatCurrency(BigDecimal.ZERO));
            confirmButton.setDisable(true);
            errorLabel.setText("");
            return;
        }
        
        try {
            tendered = new BigDecimal(tenderedStr);
            
            BigDecimal changeAmount = tendered.subtract(total);
            
            if (changeAmount.compareTo(BigDecimal.ZERO) < 0) {
                // Not enough money
                changeLabel.setText(formatCurrency(changeAmount));
                changeLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: #dc3545;");
                confirmButton.setDisable(true);
                errorLabel.setText("Insufficient amount tendered");
            } else {
                // Enough money
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
    
    /**
     * Handle confirm sale
     */
    @FXML
    private void handleConfirmSale() {
        BigDecimal tendered = BigDecimal.ZERO;
        BigDecimal total = sale.getTotal();
        
        // Handle split payment
        if (splitRadio != null && splitRadio.isSelected()) {
            BigDecimal cashAmt = BigDecimal.ZERO;
            BigDecimal mpesaAmt = BigDecimal.ZERO;
            
            try {
                if (cashAmountField != null && !cashAmountField.getText().trim().isEmpty()) {
                    cashAmt = new BigDecimal(cashAmountField.getText().trim());
                }
            } catch (NumberFormatException e) {
                showError("Invalid cash amount");
                return;
            }
            
            try {
                if (mpesaAmountField != null && !mpesaAmountField.getText().trim().isEmpty()) {
                    mpesaAmt = new BigDecimal(mpesaAmountField.getText().trim());
                }
            } catch (NumberFormatException e) {
                showError("Invalid M-Pesa amount");
                return;
            }
            
            tendered = cashAmt.add(mpesaAmt);
            
            if (tendered.compareTo(total) < 0) {
                showError("Total payment must be at least " + formatCurrency(total));
                return;
            }
            
            // Set split payment details
            this.amountPaid = tendered;
            this.change = tendered.subtract(total);
            this.cashAmount = cashAmt;
            this.mpesaAmount = mpesaAmt;
            this.paymentMethod = Sale.PaymentMethod.CASH;
            
            // Set split payment fields on sale
            sale.setPaymentMethod(paymentMethod);
            sale.setAmountPaid(amountPaid);
            sale.setChangeGiven(change);
            sale.setCashAmount(cashAmt);
            sale.setMpesaAmount(mpesaAmt);
            sale.setSecondaryPaymentMethod(Sale.PaymentMethod.MOBILE_MONEY);
            
            this.confirmed = true;
            closeDialog();
            return;
        }
        
        // Handle regular payment
        String tenderedStr = amountTenderedField.getText().trim();
        
        if (tenderedStr.isEmpty()) {
            showError("Please enter amount tendered");
            return;
        }
        
        try {
            tendered = new BigDecimal(tenderedStr);
            
            if (tendered.compareTo(total) < 0) {
                showError("Amount tendered must be greater than or equal to total");
                return;
            }
            
            // Set payment details
            this.amountPaid = tendered;
            this.change = tendered.subtract(total);
            
            // Get payment method
            if (cashRadio.isSelected()) {
                this.paymentMethod = Sale.PaymentMethod.CASH;
            } else {
                this.paymentMethod = Sale.PaymentMethod.MOBILE_MONEY;
            }
            
            // Update sale with payment details
            sale.setPaymentMethod(paymentMethod);
            sale.setAmountPaid(amountPaid);
            sale.setChangeGiven(change);
            
            this.confirmed = true;
            closeDialog();
            
        } catch (NumberFormatException e) {
            showError("Invalid amount");
        }
    }
    
    /**
     * Handle cancel
     */
    @FXML
    private void handleCancel() {
        this.confirmed = false;
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
        errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 12;");
    }
    
    /**
     * Format currency
     */
    private String formatCurrency(BigDecimal amount) {
        return BrandingConstants.CURRENCY_SYMBOL + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    
    // Getters
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
}
