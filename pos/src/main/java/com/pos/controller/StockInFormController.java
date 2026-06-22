package com.pos.controller;

import com.pos.database.DatabaseManager;
import com.pos.entity.ActivityLog;
import com.pos.entity.Product;
import com.pos.entity.SupplierTransaction;
import com.pos.entity.SupplierTransactionItem;
import com.pos.entity.User;
import com.pos.entity.ProductBarcodeMatch;
import com.pos.service.AuthenticationService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.fxml.FXMLLoader;
import javafx.event.ActionEvent;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class StockInFormController {

    private static final Logger logger = LoggerFactory.getLogger(StockInFormController.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DatabaseManager dbManager = DatabaseManager.getInstance();
    private final AuthenticationService authService = AuthenticationService.getInstance();

    private Runnable onStockChanged;
    private Product selectedProduct;
    private ObservableList<Product> productSearchResults = FXCollections.observableArrayList();
    private ObservableList<StockInDraftLine> draftLines = FXCollections.observableArrayList();
    private ObservableList<SupplierTransaction> transactions = FXCollections.observableArrayList();

    @FXML private ComboBox<String> supplierComboBox;
    @FXML private TextField productSearchField;
    @FXML private TextField buyingPriceField;

    private final ContextMenu searchContextMenu = new ContextMenu();
    @FXML private TextField quantityField;
    @FXML private Label tierFeedbackLabel;
    @FXML private TableView<StockInDraftLine> draftTable;
    @FXML private TableColumn<StockInDraftLine, String> colDraftProduct;
    @FXML private TableColumn<StockInDraftLine, String> colDraftBarcode;
    @FXML private TableColumn<StockInDraftLine, String> colDraftTier;
    @FXML private TableColumn<StockInDraftLine, Double> colDraftQty;
    @FXML private TableColumn<StockInDraftLine, BigDecimal> colDraftBuying;
    @FXML private TableColumn<StockInDraftLine, BigDecimal> colDraftLineTotal;
    // Payment source controls (replaces old radio buttons)
    @FXML private ComboBox<String> paymentSourceDropdown;
    @FXML private javafx.scene.layout.VBox debtorBox;
    @FXML private ComboBox<String> debtorDropdown;
    @FXML private TextField debtorOffsetAmount;
    @FXML private Label remainingCashLabel;
    // Debtor id map: display name -> customer id
    private final java.util.Map<String, String> debtorIdMap = new java.util.LinkedHashMap<>();
    @FXML private TableView<SupplierTransaction> transactionsTable;
    @FXML private TableColumn<SupplierTransaction, String> colTxDate;
    @FXML private TableColumn<SupplierTransaction, String> colTxSupplier;
    @FXML private TableColumn<SupplierTransaction, String> colTxTotal;
    @FXML private TableColumn<SupplierTransaction, String> colTxStatus;

    @FXML
    public void initialize() {
        colDraftProduct.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colDraftBarcode.setCellValueFactory(new PropertyValueFactory<>("barcode"));
        colDraftTier.setCellValueFactory(new PropertyValueFactory<>("tierName"));
        colDraftQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));

        colDraftBuying.setCellValueFactory(new PropertyValueFactory<>("buyingPrice"));
        colDraftBuying.setCellFactory(tc -> new TableCell<StockInDraftLine, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText(null);
                } else {
                    setText(String.format("KSh %.2f", price));
                }
            }
        });

        colDraftLineTotal.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getLineTotal()));
        colDraftLineTotal.setCellFactory(tc -> new TableCell<StockInDraftLine, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText(null);
                } else {
                    setText(String.format("KSh %.2f", price));
                }
            }
        });

        draftTable.setItems(draftLines);

        colTxDate.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().getCreatedAt() != null
                        ? c.getValue().getCreatedAt().format(TS) : ""));
        colTxSupplier.setCellValueFactory(new PropertyValueFactory<>("supplierName"));
        colTxTotal.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(String.format("%.2f", c.getValue().getTotalCost())));
        colTxStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        transactionsTable.setItems(transactions);



        // Listeners for selection and focus on productSearchField
        productSearchField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                searchContextMenu.hide();
            }
        });

        productSearchField.textProperty().addListener((observable, oldValue, newValue) -> {
            selectedProduct = null; 
            
            if (newValue == null || newValue.trim().isEmpty()) {
                searchContextMenu.hide();
                if (tierFeedbackLabel != null) {
                    tierFeedbackLabel.setText("Scan a barcode to begin...");
                }
                return;
            }
            
            // Fetch results from DB
            handleLiveSearch(newValue.trim());
            searchContextMenu.getItems().clear(); // Clear old results
            
            if (productSearchResults.isEmpty()) {
                searchContextMenu.hide();
                if (tierFeedbackLabel != null) {
                    tierFeedbackLabel.setText("No product found. Register a new product.");
                }
            } else {
                if (tierFeedbackLabel != null) {
                    tierFeedbackLabel.setText("Searching...");
                }
                
                // Build menu items for each result
                for (Product p : productSearchResults) {
                    MenuItem item = new MenuItem(p.getName() + "  (" + p.getBarcode() + ")  stock: " + p.getStockQuantity());
                    
                    // When the user clicks an item in the dropdown:
                    item.setOnAction(event -> {
                        selectedProduct = p;
                        productSearchField.setText(p.getName()); // Fill the bar
                        searchContextMenu.hide();
                        
                        // Trigger the UI updates
                        if (tierFeedbackLabel != null) {
                            String tierName = dbManager.determineTierLabel(p);
                            tierFeedbackLabel.setText("Identified: " + p.getName() + " [" + tierName + "]");
                        }
                        if (buyingPriceField != null) {
                            Double lastCost = dbManager.getLastRestockCost(p.getBarcode());
                            if (lastCost != null && lastCost > 0) {
                                buyingPriceField.setText(String.valueOf(lastCost));
                            } else {
                                buyingPriceField.clear();
                                buyingPriceField.setPromptText("Invoice Cost");
                            }
                        }
                        if (quantityField != null) {
                            quantityField.requestFocus();
                        }
                    });
                    
                    searchContextMenu.getItems().add(item);
                }
                
                // Show the menu exactly beneath the search input, letting JavaFX handle the math
                if (!searchContextMenu.isShowing()) {
                    searchContextMenu.show(productSearchField, javafx.geometry.Side.BOTTOM, 0, 0);
                }
            }
        });

        updateReceiveQuantityPrompt();

        // ----- Payment source dropdown -----
        ObservableList<String> paymentOptions = FXCollections.observableArrayList(
                "Cash (From Shelf)",
                "Owner Out-of-Pocket",
                "M-PESA",
                "Debt (Offset Account)"
        );
        paymentSourceDropdown.setItems(paymentOptions);
        paymentSourceDropdown.getSelectionModel().selectFirst();

        paymentSourceDropdown.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean isDebt = "Debt (Offset Account)".equals(newVal);
            debtorBox.setVisible(isDebt);
            debtorBox.setManaged(isDebt);
            if (isDebt) {
                loadDebtors();
                recalcRemainingCash(); // reset label
            } else {
                if (remainingCashLabel != null) remainingCashLabel.setText("KSh 0.00");
                if (debtorOffsetAmount != null) debtorOffsetAmount.clear();
            }
        });

        // Auto-calculate remaining cash as the manager types the offset amount
        if (debtorOffsetAmount != null) {
            debtorOffsetAmount.textProperty().addListener((obs, o, n) -> recalcRemainingCash());
        }

        loadSuppliers();
        loadTransactions();
    }

    public void setOnStockChanged(Runnable onStockChanged) {
        this.onStockChanged = onStockChanged;
    }

    public void refreshAll() {
        loadSuppliers();
        loadTransactions();
    }

    private void updateReceiveQuantityPrompt() {
        if (quantityField == null) {
            return;
        }
        quantityField.setPromptText("Qty");
    }

    private void loadSuppliers() {
        try {
            List<String> suppliers = dbManager.getAllSupplierNames();
            supplierComboBox.setItems(FXCollections.observableArrayList(suppliers));
        } catch (Exception e) {
            logger.error("Failed loading supplier names", e);
        }
    }

    /** Recomputes Remaining Cash = shipment total - debt offset and updates the label. */
    private void recalcRemainingCash() {
        if (remainingCashLabel == null) return;
        BigDecimal total = draftLines.stream()
                .map(StockInDraftLine::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal offset = BigDecimal.ZERO;
        if (debtorOffsetAmount != null && !debtorOffsetAmount.getText().isBlank()) {
            try { offset = new BigDecimal(debtorOffsetAmount.getText().trim()); } catch (NumberFormatException ignored) {}
        }
        BigDecimal remaining = total.subtract(offset);
        String colour = remaining.compareTo(BigDecimal.ZERO) < 0 ? "#dc2626" : "#0f172a";
        remainingCashLabel.setText(String.format("KSh %.2f", remaining));
        remainingCashLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: " + colour + ";");
    }

    private void loadDebtors() {
        debtorIdMap.clear();
        try {
            java.util.List<com.pos.entity.Customer> customers = dbManager.getAllCustomers();
            for (com.pos.entity.Customer c : customers) {
                debtorIdMap.put(c.getName() + (c.getPhone() != null && !c.getPhone().isBlank() ? " (" + c.getPhone() + ")" : ""), c.getId());
            }
            debtorDropdown.setItems(FXCollections.observableArrayList(debtorIdMap.keySet()));
            if (!debtorIdMap.isEmpty()) {
                debtorDropdown.getSelectionModel().selectFirst();
            }
        } catch (Exception e) {
            logger.error("Failed loading debtors", e);
        }
    }

    @FXML
    private void handleProductSearch() {
        String term = productSearchField.getText() == null ? "" : productSearchField.getText().trim();
        try {
            productSearchResults.clear();
            searchContextMenu.getItems().clear();
            if (term.isEmpty()) {
                productSearchResults.addAll(dbManager.getAllProducts().stream().limit(30).toList());
            } else {
                Optional<Product> opt = dbManager.findProductByBarcode(term);
                if (opt.isPresent()) {
                    Product p = opt.get();
                    selectedProduct = p;
                    productSearchField.setText(p.getName());
                    String tierName = dbManager.determineTierLabel(p);
                    tierFeedbackLabel.setText("Identified: " + p.getName() + " [" + tierName + "]");
                    
                    TextField unitCostInput = buyingPriceField;
                    Double lastCost = dbManager.getLastRestockCost(p.getBarcode());
                    if (lastCost != null && lastCost > 0) {
                        unitCostInput.setText(String.valueOf(lastCost));
                    } else {
                        unitCostInput.clear();
                        unitCostInput.setPromptText("Invoice Cost");
                    }
                    
                    quantityField.requestFocus();
                    searchContextMenu.hide();
                    return;
                }
                
                List<Product> searchResults = dbManager.searchProducts(term);
                productSearchResults.addAll(searchResults);
                
                if (searchResults.size() == 1) {
                    Product p = searchResults.get(0);
                    selectedProduct = p;
                    productSearchField.setText(p.getName());
                    String tierName = dbManager.determineTierLabel(p);
                    tierFeedbackLabel.setText("Identified: " + p.getName() + " [" + tierName + "]");
                    
                    TextField unitCostInput = buyingPriceField;
                    Double lastCost = dbManager.getLastRestockCost(p.getBarcode());
                    if (lastCost != null && lastCost > 0) {
                        unitCostInput.setText(String.valueOf(lastCost));
                    } else {
                        unitCostInput.clear();
                        unitCostInput.setPromptText("Invoice Cost");
                    }
                    
                    quantityField.requestFocus();
                }
            }

            if (!productSearchResults.isEmpty() && selectedProduct == null) {
                // Populate searchContextMenu with products
                for (Product p : productSearchResults) {
                    MenuItem item = new MenuItem(p.getName() + "  (" + p.getBarcode() + ")  stock: " + p.getStockQuantity());
                    item.setOnAction(e -> {
                        selectedProduct = p;
                        productSearchField.setText(p.getName());
                        searchContextMenu.hide();
                        
                        if (tierFeedbackLabel != null) {
                            String tierName = dbManager.determineTierLabel(p);
                            tierFeedbackLabel.setText("Identified: " + p.getName() + " [" + tierName + "]");
                        }
                        if (buyingPriceField != null) {
                            Double lastCost = dbManager.getLastRestockCost(p.getBarcode());
                            if (lastCost != null && lastCost > 0) {
                                buyingPriceField.setText(String.valueOf(lastCost));
                            } else {
                                buyingPriceField.clear();
                                buyingPriceField.setPromptText("Invoice Cost");
                            }
                        }
                        if (quantityField != null) {
                            quantityField.requestFocus();
                        }
                    });
                    searchContextMenu.getItems().add(item);
                }
                if (!searchContextMenu.isShowing()) {
                    searchContextMenu.show(productSearchField, javafx.geometry.Side.BOTTOM, 0, 0);
                }
            } else {
                searchContextMenu.hide();
                if (selectedProduct == null && tierFeedbackLabel != null) {
                    tierFeedbackLabel.setText("No product found. Register a new product.");
                }
            }
        } catch (Exception e) {
            logger.error("Product search failed", e);
            showError("Could not search products: " + e.getMessage());
        }
    }

    private void handleLiveSearch(String query) {
        try {
            List<Product> products = dbManager.searchProducts(query);
            productSearchResults.setAll(products.stream().limit(10).toList());
        } catch (Exception e) {
            logger.error("Failed to perform live search", e);
            productSearchResults.clear();
        }
    }

    @FXML
    private void handleSaveSupplier() {
        String name = supplierComboBox.getEditor().getText().trim();
        if (name.isEmpty()) {
            showError("Please enter a supplier name to save.");
            return;
        }
        try {
            boolean added = dbManager.addSupplier(name);
            if (added) {
                showInfo("Supplier [" + name + "] saved successfully!");
                loadSuppliers(); // Refresh the dropdown
                supplierComboBox.setValue(name);
            } else {
                showInfo("This supplier already exists in the database.");
            }
        } catch (Exception e) {
            logger.error("Failed to save supplier", e);
            showError("Could not save supplier: " + e.getMessage());
        }
    }

    @FXML
    private void handleAddLine() {
        Product p = selectedProduct;

        if (p == null) {
            showError("Select a product from the search results.");
            return;
        }
        String bp = buyingPriceField.getText() == null ? "" : buyingPriceField.getText().trim();
        String qStr = quantityField.getText() == null ? "" : quantityField.getText().trim();
        if (bp.isEmpty() || qStr.isEmpty()) {
            if (bp.isEmpty() && tierFeedbackLabel != null) {
                tierFeedbackLabel.setText("Error: You must enter the supplier invoice cost.");
            }
            showError("Enter buying price and received quantity.");
            return;
        }
        try {
            BigDecimal buying = new BigDecimal(bp);
            if (buying.compareTo(BigDecimal.ZERO) <= 0) {
                if (tierFeedbackLabel != null) {
                    tierFeedbackLabel.setText("Error: You must enter the supplier invoice cost.");
                }
                showError("Error: You must enter the supplier invoice cost.");
                return;
            }
            double qty = Double.parseDouble(qStr);
            if (qty <= 0) {
                showError("Quantity must be greater than zero.");
                return;
            }
            if (buying.compareTo(BigDecimal.ZERO) < 0) {
                showError("Buying price cannot be negative.");
                return;
            }
            String tierName = dbManager.determineTierLabel(p);
            draftLines.add(new StockInDraftLine(p.getId(), p.getName(), p.getBarcode(), tierName, qty, buying));
            quantityField.clear();
        } catch (NumberFormatException ex) {
            showError("Invalid number for price or quantity.");
        }
    }

    @FXML
    private void handleRegisterNewProduct(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/product_form.fxml"));
            Parent root = loader.load();
            
            Scene scene = new Scene(root);
            Stage wizardStage = new Stage();
            wizardStage.setTitle("Register New Product Hierarchy");
            wizardStage.initModality(Modality.APPLICATION_MODAL); // Blocks interaction with Restock page
            wizardStage.setScene(scene);
            wizardStage.setMaximized(true); // Open in full screen / maximized
            
            // Pass the scanned barcode if one was already typed in, so they don't have to re-type it
            ProductFormController wizardController = loader.getController();
            String query = productSearchField.getText() == null ? "" : productSearchField.getText().trim();
            if (!query.isEmpty()) {
                wizardController.prefillBarcode(query);
            }
            
            // Pause execution here until the wizard is closed
            wizardStage.showAndWait();
            
            String newBarcode = wizardController.getLastSavedBarcode();
            if (newBarcode != null && !newBarcode.isEmpty()) {
                // Instantly drop the new barcode into the search field
                productSearchField.setText(newBarcode);
                
                // Automatically trigger the search so the UI updates with the new product name and cost
                handleProductSearch(); 
                
                // Focus the quantity box so the manager can just type the amount and hit enter
                quantityField.requestFocus();
            }
        } catch (Exception e) {
            logger.error("Error loading Product Wizard modal", e);
            showError("Error loading Product Wizard: " + e.getMessage());
        }
    }

    @FXML
    private void handleRemoveDraftLine() {
        StockInDraftLine row = draftTable.getSelectionModel().getSelectedItem();
        if (row != null) {
            draftLines.remove(row);
        }
    }

    @FXML
    private void handleRecordStockIn() {
        String supplier = supplierComboBox.getEditor().getText().trim();
        if (supplier.isEmpty()) {
            showError("Enter a supplier name.");
            return;
        }
        if (draftLines.isEmpty()) {
            showError("Add at least one product line.");
            return;
        }

        // Auto-save supplier
        dbManager.ensureSupplierExists(supplier);

        String selectedSource = paymentSourceDropdown.getValue();
        if (selectedSource == null || selectedSource.isBlank()) {
            showError("Please select a payment source.");
            return;
        }

        boolean isDebt = "Debt (Offset Account)".equals(selectedSource);
        String selectedDebtorDisplay = isDebt ? debtorDropdown.getValue() : null;
        if (isDebt && (selectedDebtorDisplay == null || selectedDebtorDisplay.isBlank())) {
            showError("Please select the debtor account to offset.");
            return;
        }
        String debtorId = isDebt ? debtorIdMap.get(selectedDebtorDisplay) : null;

        // Map payment source to PAID/CREDIT status
        String status = isDebt ? SupplierTransaction.STATUS_CREDIT : SupplierTransaction.STATUS_PAID;

        SupplierTransaction trans = new SupplierTransaction();
        trans.setId(UUID.randomUUID().toString());
        trans.setSupplierName(supplier);
        trans.setStatus(status);
        trans.setPaymentSource(selectedSource);
        trans.setDebtorId(debtorId);

        List<SupplierTransactionItem> items = draftLines.stream().map(d -> {
            SupplierTransactionItem it = new SupplierTransactionItem();
            it.setProductId(d.getProductId());
            it.setQuantityReceived(d.getQuantity());
            it.setBuyingPrice(d.getBuyingPrice());
            return it;
        }).toList();

        ActivityLog log = null;
        Optional<User> userOpt = authService.getCurrentUser();
        if (userOpt.isPresent()) {
            User u = userOpt.get();
            BigDecimal total = items.stream()
                    .map(i -> i.getBuyingPrice().multiply(BigDecimal.valueOf(i.getQuantityReceived())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String amountStr = String.format("%.2f", total);
            log = new ActivityLog(
                    u.getId(),
                    u.getFullName(),
                    ActivityLog.ActionType.STOCK_IN,
                    "Stock In: " + supplier + " - KSh " + amountStr,
                    "Lines: " + items.size() + ", payment: " + status
            );
        }

        try {
            dbManager.processStockIn(trans, items, log);

            // Sync updated products to cloud
            com.pos.service.SyncService syncService = com.pos.service.SyncService.getInstance();
            for (SupplierTransactionItem it : items) {
                Optional<Product> pOpt = dbManager.findProductById(it.getProductId());
                pOpt.ifPresent(syncService::syncProductToCloud);
            }

            // Default success message (overridden below if debt offset is used)
            String successMsg = isDebt
                ? "Stock recorded. Debt offset applied. Inventory updated."
                : "Stock in recorded. Inventory updated.";

            // --- DEBT OFFSET SPLIT ---
            if (isDebt && debtorId != null) {
                BigDecimal totalCost = items.stream()
                        .map(i -> i.getBuyingPrice().multiply(BigDecimal.valueOf(i.getQuantityReceived())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Resolve offset amount: blank / 0 = full offset
                BigDecimal offsetAmt = totalCost;
                String offsetText = debtorOffsetAmount != null ? debtorOffsetAmount.getText().trim() : "";
                if (!offsetText.isBlank()) {
                    try { offsetAmt = new BigDecimal(offsetText); } catch (NumberFormatException ex) { /* keep full */ }
                }

                // Safety: offset cannot exceed total
                if (offsetAmt.compareTo(totalCost) > 0) {
                    showError("Debt offset (KSh " + String.format("%.2f", offsetAmt)
                            + ") cannot exceed the total shipment cost (KSh "
                            + String.format("%.2f", totalCost) + ").");
                    return;
                }

                BigDecimal cashDue = totalCost.subtract(offsetAmt);
                try {
                    dbManager.processPartialDebtOffset(trans.getId(), debtorId, offsetAmt, totalCost);
                    logger.info("Partial debt offset: KSh {} from {}, cash due KSh {}",
                            offsetAmt, selectedDebtorDisplay, cashDue);
                } catch (Exception ex) {
                    logger.warn("Could not process debt offset: {}", ex.getMessage());
                }

                successMsg = String.format(
                    "Shipment saved!\n" +
                    "  Debt offset:   KSh %.2f (from %s)\n" +
                    "  Cash due:      KSh %.2f\n" +
                    "Inventory updated.",
                    offsetAmt, selectedDebtorDisplay, cashDue);
            }

            draftLines.clear();
            buyingPriceField.clear();
            quantityField.clear();
            if (debtorOffsetAmount != null) debtorOffsetAmount.clear();
            if (remainingCashLabel != null) remainingCashLabel.setText("KSh 0.00");
            if (tierFeedbackLabel != null) {
                tierFeedbackLabel.setText("Scan a barcode to begin...");
            }
            loadTransactions();
            loadSuppliers();
            if (onStockChanged != null) {
                onStockChanged.run();
            }
            showInfo(successMsg);
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        } catch (Exception ex) {
            logger.error("processStockIn failed", ex);
            showError("Could not save stock in: " + ex.getMessage());
        }
    }

    @FXML
    private void handleClearPayment() {
        SupplierTransaction sel = transactionsTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showError("Select a credit transaction to clear.");
            return;
        }
        if (!SupplierTransaction.STATUS_CREDIT.equals(sel.getStatus())) {
            showError("Only CREDIT transactions can be cleared.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Clear payment");
        confirm.setHeaderText("Mark this supplier bill as PAID?");
        confirm.setContentText(sel.getSupplierName() + " — " + sel.getTotalCost().toPlainString());
        Optional<ButtonType> ans = confirm.showAndWait();
        if (ans.isEmpty() || ans.get() != ButtonType.OK) {
            return;
        }
        try {
            dbManager.clearSupplierPayment(sel.getId());
            loadTransactions();
            showInfo("Payment cleared.");
        } catch (Exception e) {
            logger.error("clearSupplierPayment failed", e);
            showError(e.getMessage());
        }
    }

    private void loadTransactions() {
        try {
            transactions.setAll(dbManager.getAllSupplierTransactions());
        } catch (Exception e) {
            logger.error("Failed loading supplier transactions", e);
        }
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Stock In");
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Stock In");
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    /** Editable line before posting to the database. */
    public static class StockInDraftLine {
        private final javafx.beans.property.StringProperty productId = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty productName = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty barcode = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty tierName = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.DoubleProperty quantity = new javafx.beans.property.SimpleDoubleProperty();
        private final javafx.beans.property.ObjectProperty<BigDecimal> buyingPrice = new javafx.beans.property.SimpleObjectProperty<>(BigDecimal.ZERO);

        public StockInDraftLine(String productId, String productName, String barcode, String tierName, double quantity, BigDecimal buyingPrice) {
            this.productId.set(productId);
            this.productName.set(productName);
            this.barcode.set(barcode);
            this.tierName.set(tierName);
            this.quantity.set(quantity);
            this.buyingPrice.set(buyingPrice);
        }

        public String getTierName() {
            return tierName.get();
        }

        public javafx.beans.property.StringProperty tierNameProperty() {
            return tierName;
        }

        public String getProductId() {
            return productId.get();
        }

        public String getProductName() {
            return productName.get();
        }

        public String getBarcode() {
            return barcode.get();
        }

        public double getQuantity() {
            return quantity.get();
        }

        public BigDecimal getBuyingPrice() {
            return buyingPrice.get();
        }

        public javafx.beans.property.ObjectProperty<BigDecimal> buyingPriceProperty() {
            return buyingPrice;
        }

        public javafx.beans.property.DoubleProperty quantityProperty() {
            return quantity;
        }

        public BigDecimal getLineTotal() {
            return buyingPrice.get().multiply(BigDecimal.valueOf(quantity.get()));
        }
    }
}
