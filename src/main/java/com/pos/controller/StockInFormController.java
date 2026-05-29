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
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
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
    private ObservableList<Product> productSearchResults = FXCollections.observableArrayList();
    private ObservableList<StockInDraftLine> draftLines = FXCollections.observableArrayList();
    private ObservableList<SupplierTransaction> transactions = FXCollections.observableArrayList();

    @FXML private ComboBox<String> supplierComboBox;
    @FXML private TextField productSearchField;
    @FXML private ListView<Product> productResultList;
    @FXML private TextField buyingPriceField;
    @FXML private TextField quantityField;
    @FXML private RadioButton radioReceiveSingle;
    @FXML private RadioButton radioReceiveBox;
    @FXML private TableView<StockInDraftLine> draftTable;
    @FXML private TableColumn<StockInDraftLine, String> colDraftProduct;
    @FXML private TableColumn<StockInDraftLine, String> colDraftBarcode;
    @FXML private TableColumn<StockInDraftLine, Double> colDraftQty;
    @FXML private TableColumn<StockInDraftLine, BigDecimal> colDraftBuying;
    @FXML private TableColumn<StockInDraftLine, BigDecimal> colDraftLineTotal;
    @FXML private RadioButton radioPaid;
    @FXML private RadioButton radioCredit;
    @FXML private TableView<SupplierTransaction> transactionsTable;
    @FXML private TableColumn<SupplierTransaction, String> colTxDate;
    @FXML private TableColumn<SupplierTransaction, String> colTxSupplier;
    @FXML private TableColumn<SupplierTransaction, String> colTxTotal;
    @FXML private TableColumn<SupplierTransaction, String> colTxStatus;

    @FXML
    public void initialize() {
        colDraftProduct.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colDraftBarcode.setCellValueFactory(new PropertyValueFactory<>("barcode"));
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

        productResultList.setItems(productSearchResults);
        productResultList.setPrefHeight(120);
        productResultList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Product item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getName() + "  (" + item.getBarcode() + ")  stock: " + item.getStockQuantity());
                }
            }
        });

        if (radioReceiveBox != null) {
            radioReceiveBox.selectedProperty().addListener((o, a, b) -> updateReceiveQuantityPrompt());
        }
        if (radioReceiveSingle != null) {
            radioReceiveSingle.selectedProperty().addListener((o, a, b) -> updateReceiveQuantityPrompt());
        }
        updateReceiveQuantityPrompt();

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
        boolean box = radioReceiveBox != null && radioReceiveBox.isSelected();
        quantityField.setPromptText(box ? "Number of bulk items" : "Total Units or Kgs");
    }

    private void loadSuppliers() {
        try {
            List<String> suppliers = dbManager.getAllSupplierNames();
            supplierComboBox.setItems(FXCollections.observableArrayList(suppliers));
        } catch (Exception e) {
            logger.error("Failed loading supplier names", e);
        }
    }

    @FXML
    private void handleProductSearch() {
        String term = productSearchField.getText() == null ? "" : productSearchField.getText().trim();
        try {
            productSearchResults.clear();
            if (term.isEmpty()) {
                productSearchResults.addAll(dbManager.getAllProducts().stream().limit(30).toList());
            } else {
                productSearchResults.addAll(dbManager.searchProducts(term));
            }
        } catch (Exception e) {
            logger.error("Product search failed", e);
            showError("Could not search products: " + e.getMessage());
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
        Product p = productResultList.getSelectionModel().getSelectedItem();
        if (p == null) {
            showError("Select a product from the search results.");
            return;
        }
        String bp = buyingPriceField.getText() == null ? "" : buyingPriceField.getText().trim();
        String qStr = quantityField.getText() == null ? "" : quantityField.getText().trim();
        if (bp.isEmpty() || qStr.isEmpty()) {
            showError("Enter buying price and received quantity.");
            return;
        }
        try {
            BigDecimal buying = new BigDecimal(bp);
            double qty = Double.parseDouble(qStr);
            if (qty <= 0) {
                showError("Quantity must be greater than zero.");
                return;
            }
            if (buying.compareTo(BigDecimal.ZERO) < 0) {
                showError("Buying price cannot be negative.");
                return;
            }
            boolean asBox = radioReceiveBox != null && radioReceiveBox.isSelected();
            if (asBox) {
                if (needsBulkConfiguration(p)) {
                    Optional<Product> updated = promptConfigureBulkAndPersist(p);
                    if (updated.isEmpty()) {
                        return;
                    }
                    p = updated.get();
                    refreshProductInSearchList(p);
                }
                int per = p.getPiecesPerBulk();
                if (per < 2) {
                    showError("Base units per bulk must be at least 2.");
                    return;
                }
                String bulkBc = p.getBulkBarcode();
                if (bulkBc == null || bulkBc.isBlank()) {
                    showError("Box barcode is still missing after setup.");
                    return;
                }
                double pieces = qty * per;
                BigDecimal buyingPerPiece = buying.divide(BigDecimal.valueOf(per), 6, RoundingMode.HALF_UP);
                String label = p.getName() + " (" + qty + " bulk item" + (qty == 1 ? "" : "s") + " → " + pieces + " units)";
                draftLines.add(new StockInDraftLine(p.getId(), label, p.getBarcode(), pieces, buyingPerPiece));
            } else {
                draftLines.add(new StockInDraftLine(p.getId(), p.getName(), p.getBarcode(), qty, buying));
            }
            quantityField.clear();
        } catch (NumberFormatException ex) {
            showError("Invalid number for price or quantity.");
        }
    }

    /**
     * Minimal product registration from Stock In, optionally with box/case barcodes for same-day receiving.
     */
    @FXML
    private void handleQuickRegisterProduct() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Quick register product");
        dialog.setHeaderText("Create a product record, then add shipment lines below.");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField barcodeField = new TextField();
        TextField nameField = new TextField();
        TextField retailField = new TextField();
        TextField wholesaleField = new TextField();
        TextField categoryField = new TextField();
        CheckBox shipInBoxes = new CheckBox("Supplier ships in boxes / cases");
        TextField piecesField = new TextField("12");
        TextField bulkBarcodeField = new TextField();
        TextField boxRetailField = new TextField();
        Label boxHint = new Label("Units per bulk, case barcode, and shelf price for one case (POS).");
        boxHint.setWrapText(true);
        boxHint.setStyle("-fx-text-fill: #718096; -fx-font-size: 11;");

        Product barcodeSuggestStub = new Product();

        Runnable refreshBoxBarcodeSuggestion = () -> {
            try {
                barcodeSuggestStub.setBarcode(barcodeField.getText() != null ? barcodeField.getText().trim() : "");
                bulkBarcodeField.setText(suggestUniqueBulkBarcode(barcodeSuggestStub));
            } catch (SQLException e) {
                bulkBarcodeField.setText("BOX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            }
        };

        shipInBoxes.selectedProperty().addListener((o, a, on) -> {
            piecesField.setDisable(!on);
            bulkBarcodeField.setDisable(!on);
            boxRetailField.setDisable(!on);
            boxHint.setVisible(on);
            boxHint.setManaged(on);
            if (on) {
                refreshBoxBarcodeSuggestion.run();
                updateDefaultBoxRetailFromPieces(retailField.getText(), piecesField.getText(), boxRetailField);
            }
        });
        barcodeField.textProperty().addListener((o, a, b) -> {
            if (shipInBoxes.isSelected()) {
                refreshBoxBarcodeSuggestion.run();
            }
        });
        piecesField.textProperty().addListener((o, a, b) -> {
            if (shipInBoxes.isSelected()) {
                updateDefaultBoxRetailFromPieces(retailField.getText(), piecesField.getText(), boxRetailField);
            }
        });
        retailField.textProperty().addListener((o, a, b) -> {
            if (shipInBoxes.isSelected()) {
                updateDefaultBoxRetailFromPieces(retailField.getText(), piecesField.getText(), boxRetailField);
            }
        });

        piecesField.setDisable(true);
        bulkBarcodeField.setDisable(true);
        boxRetailField.setDisable(true);
        boxHint.setVisible(false);
        boxHint.setManaged(false);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10, 0, 0, 0));
        int r = 0;
        grid.add(new Label("Unit barcode *"), 0, r);
        grid.add(barcodeField, 1, r++);
        grid.add(new Label("Name *"), 0, r);
        grid.add(nameField, 1, r++);
        grid.add(new Label("Retail (unit)"), 0, r);
        grid.add(retailField, 1, r++);
        grid.add(new Label("Wholesale (unit)"), 0, r);
        grid.add(wholesaleField, 1, r++);
        grid.add(new Label("Category"), 0, r);
        grid.add(categoryField, 1, r++);
        grid.add(shipInBoxes, 0, r++, 2, 1);
        grid.add(boxHint, 0, r++, 2, 1);
        grid.add(new Label("Units per bulk"), 0, r);
        grid.add(piecesField, 1, r++);
        grid.add(new Label("Case / box barcode"), 0, r);
        grid.add(bulkBarcodeField, 1, r++);
        grid.add(new Label("Retail price per box"), 0, r);
        grid.add(boxRetailField, 1, r++);
        GridPane.setHgrow(barcodeField, Priority.ALWAYS);
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(retailField, Priority.ALWAYS);
        GridPane.setHgrow(wholesaleField, Priority.ALWAYS);
        GridPane.setHgrow(categoryField, Priority.ALWAYS);
        GridPane.setHgrow(bulkBarcodeField, Priority.ALWAYS);
        GridPane.setHgrow(boxRetailField, Priority.ALWAYS);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(520);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.OK) {
            return;
        }

        String barcode = barcodeField.getText() != null ? barcodeField.getText().trim() : "";
        String name = nameField.getText() != null ? nameField.getText().trim() : "";
        if (barcode.isEmpty() || name.isEmpty()) {
            showError("Barcode and name are required.");
            return;
        }
        try {
            Optional<ProductBarcodeMatch> clash = dbManager.findProductByAnyBarcode(barcode);
            if (clash.isPresent()) {
                showError("That barcode is already used on another product.");
                return;
            }
            BigDecimal retail = parseMoneyOrZero(retailField.getText());
            BigDecimal wholesale = parseMoneyOrZero(wholesaleField.getText());
            String category = categoryField.getText() != null ? categoryField.getText().trim() : "";

            Product product = new Product(name, retail, wholesale);
            product.setBarcode(barcode);
            product.setStockQuantity(0);
            product.setCategory(category.isEmpty() ? "General" : category);
            String supplierName = supplierComboBox.getEditor().getText().trim();
            if (!supplierName.isEmpty()) {
                product.setSupplier(supplierName);
            }

            Optional<User> currentUser = authService.getCurrentUser();
            if (currentUser.isPresent() && currentUser.get().getRole() == User.Role.ATTENDANT) {
                product.setStatus("PENDING");
            } else {
                product.setStatus("APPROVED");
            }

            if (shipInBoxes.isSelected()) {
                int per = parsePositiveInt(piecesField.getText(), "Units per bulk");
                if (per < 2) {
                    showError("Units per bulk must be at least 2.");
                    return;
                }
                String bulkBc = bulkBarcodeField.getText() != null ? bulkBarcodeField.getText().trim() : "";
                if (bulkBc.isEmpty()) {
                    showError("Enter a case / box barcode.");
                    return;
                }
                if (bulkBc.equalsIgnoreCase(barcode)) {
                    showError("Case barcode must differ from the unit barcode.");
                    return;
                }
                Optional<ProductBarcodeMatch> bulkClash = dbManager.findProductByAnyBarcode(bulkBc);
                if (bulkClash.isPresent()) {
                    showError("That case barcode is already in use.");
                    return;
                }
                BigDecimal boxRetail = parseMoneyRequired(boxRetailField.getText(), "Retail price per box");
                if (boxRetail.compareTo(BigDecimal.ZERO) <= 0) {
                    showError("Retail price per box must be greater than zero.");
                    return;
                }
                product.setPiecesPerBulk(per);
                product.setBulkBarcode(bulkBc);
                product.setBulkPrice(boxRetail);
            }

            dbManager.insertProduct(product);
            productSearchResults.removeIf(x -> x.getId().equals(product.getId()));
            productSearchResults.add(0, product);
            productResultList.getSelectionModel().select(product);
            productSearchField.setText(product.getName());
            showInfo("Product saved. Select it and add lines, or search again.");
        } catch (NumberFormatException ex) {
            showError(ex.getMessage() != null ? ex.getMessage() : "Invalid number.");
        } catch (Exception ex) {
            logger.error("Quick register failed", ex);
            showError("Could not save product: " + ex.getMessage());
        }
    }

    private static boolean needsBulkConfiguration(Product p) {
        String bulk = p.getBulkBarcode();
        return bulk == null || bulk.isBlank() || p.getPiecesPerBulk() < 2;
    }

    /**
     * Saves {@code pieces_per_bulk}, {@code bulk_barcode}, and {@code bulk_price} on the product record.
     */
    private Optional<Product> promptConfigureBulkAndPersist(Product selected) {
        Product p;
        try {
            p = dbManager.findProductById(selected.getId()).orElse(selected);
        } catch (SQLException e) {
            logger.error("load product", e);
            showError("Could not load product: " + e.getMessage());
            return Optional.empty();
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Configure box receiving");
        dialog.setHeaderText("Enter how many single units are in one supplier case. "
                + "This is saved on the product so box receiving and POS box sales work.");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField piecesField = new TextField(p.getPiecesPerBulk() >= 2 ? String.valueOf(p.getPiecesPerBulk()) : "12");
        TextField bulkBarcodeField = new TextField();
        try {
            bulkBarcodeField.setText(suggestUniqueBulkBarcode(p));
        } catch (SQLException e) {
            bulkBarcodeField.setText("BOX-" + p.getId().substring(0, 8).toUpperCase());
        }
        TextField boxRetailField = new TextField();
        BigDecimal defaultBox = defaultBoxRetailPrice(p, piecesField.getText());
        if (defaultBox.compareTo(BigDecimal.ZERO) > 0) {
            boxRetailField.setText(defaultBox.toPlainString());
        }
        piecesField.textProperty().addListener((o, a, b) -> {
            BigDecimal d = defaultBoxRetailPrice(p, piecesField.getText());
            if (d.compareTo(BigDecimal.ZERO) > 0) {
                boxRetailField.setText(d.toPlainString());
            }
        });

        Label hint = new Label(
                "Buying price (above) is cost per case. \"Retail price per box\" is the shelf price for one case at checkout.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #718096; -fx-font-size: 11;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10, 0, 0, 0));
        int r = 0;
        grid.add(new Label("Units per bulk *"), 0, r);
        grid.add(piecesField, 1, r++);
        grid.add(new Label("Case / box barcode *"), 0, r);
        grid.add(bulkBarcodeField, 1, r++);
        grid.add(new Label("Retail price per box *"), 0, r);
        grid.add(boxRetailField, 1, r++);
        grid.add(hint, 0, r++, 2, 1);
        GridPane.setHgrow(piecesField, Priority.ALWAYS);
        GridPane.setHgrow(bulkBarcodeField, Priority.ALWAYS);
        GridPane.setHgrow(boxRetailField, Priority.ALWAYS);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(520);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.OK) {
            return Optional.empty();
        }

        try {
            int per = parsePositiveInt(piecesField.getText(), "Units per bulk");
            if (per < 2) {
                showError("Units per bulk must be at least 2.");
                return Optional.empty();
            }
            String bulkBc = bulkBarcodeField.getText() != null ? bulkBarcodeField.getText().trim() : "";
            if (bulkBc.isEmpty()) {
                showError("Case / box barcode is required.");
                return Optional.empty();
            }
            String unitBc = p.getBarcode() != null ? p.getBarcode().trim() : "";
            if (!unitBc.isEmpty() && bulkBc.equalsIgnoreCase(unitBc)) {
                showError("Case barcode must be different from the unit barcode.");
                return Optional.empty();
            }
            Optional<ProductBarcodeMatch> clash = dbManager.findProductByAnyBarcode(bulkBc);
            if (clash.isPresent() && !clash.get().getProduct().getId().equals(p.getId())) {
                showError("That case barcode is already used on another product.");
                return Optional.empty();
            }
            BigDecimal boxRetail = parseMoneyRequired(boxRetailField.getText(), "Retail price per box");
            if (boxRetail.compareTo(BigDecimal.ZERO) <= 0) {
                showError("Retail price per box must be greater than zero.");
                return Optional.empty();
            }

            p.setPiecesPerBulk(per);
            p.setBulkBarcode(bulkBc);
            p.setBulkPrice(boxRetail);
            dbManager.updateProduct(p);
            return dbManager.findProductById(p.getId());
        } catch (NumberFormatException ex) {
            showError(ex.getMessage() != null ? ex.getMessage() : "Invalid number.");
            return Optional.empty();
        } catch (SQLException ex) {
            logger.error("save bulk config", ex);
            showError("Could not save: " + ex.getMessage());
            return Optional.empty();
        }
    }

    private void refreshProductInSearchList(Product updated) {
        for (int i = 0; i < productSearchResults.size(); i++) {
            if (productSearchResults.get(i).getId().equals(updated.getId())) {
                productSearchResults.set(i, updated);
                break;
            }
        }
        productResultList.getSelectionModel().select(updated);
    }

    private String suggestUniqueBulkBarcode(Product p) throws SQLException {
        String u = p.getBarcode() == null ? "" : p.getBarcode().trim();
        String base = u.isEmpty() ? ("BOX-" + p.getId().substring(0, 8).toUpperCase()) : (u + "-BOX");
        for (int n = 0; n < 500; n++) {
            String candidate = n == 0 ? base : base + n;
            Optional<ProductBarcodeMatch> m = dbManager.findProductByAnyBarcode(candidate);
            if (m.isEmpty()) {
                return candidate;
            }
            if (m.get().getProduct().getId().equals(p.getId())) {
                return candidate;
            }
        }
        return base + System.currentTimeMillis();
    }

    private static BigDecimal defaultBoxRetailPrice(Product p, String piecesText) {
        int per = 12;
        try {
            per = Integer.parseInt(piecesText != null ? piecesText.trim() : "12");
        } catch (NumberFormatException ignored) {
            // keep default
        }
        if (per < 1) {
            per = 1;
        }
        if (p.getRetailPrice() == null) {
            return BigDecimal.ZERO;
        }
        return p.getRetailPrice().multiply(BigDecimal.valueOf(per));
    }

    private static void updateDefaultBoxRetailFromPieces(String retailUnitText, String piecesText, TextField boxRetailField) {
        BigDecimal unit = parseMoneyOrZero(retailUnitText);
        int per = 12;
        try {
            per = Integer.parseInt(piecesText != null ? piecesText.trim() : "12");
        } catch (NumberFormatException ignored) {
            // keep default
        }
        if (per < 1) {
            per = 1;
        }
        BigDecimal box = unit.multiply(BigDecimal.valueOf(per));
        if (box.compareTo(BigDecimal.ZERO) > 0) {
            boxRetailField.setText(box.toPlainString());
        }
    }

    private static BigDecimal parseMoneyOrZero(String raw) {
        if (raw == null) {
            return BigDecimal.ZERO;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(t);
    }

    private static BigDecimal parseMoneyRequired(String raw, String label) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new NumberFormatException(label + " is required.");
        }
        return new BigDecimal(raw.trim());
    }

    private static int parsePositiveInt(String raw, String label) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new NumberFormatException(label + " is required.");
        }
        int v = Integer.parseInt(raw.trim());
        if (v <= 0) {
            throw new NumberFormatException(label + " must be a positive whole number.");
        }
        return v;
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

        boolean credit = radioCredit != null && radioCredit.isSelected();
        String status = credit ? SupplierTransaction.STATUS_CREDIT : SupplierTransaction.STATUS_PAID;

        SupplierTransaction trans = new SupplierTransaction();
        trans.setId(UUID.randomUUID().toString());
        trans.setSupplierName(supplier);
        trans.setStatus(status);

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
            draftLines.clear();
            buyingPriceField.clear();
            quantityField.clear();
            loadTransactions();
            loadSuppliers();
            if (onStockChanged != null) {
                onStockChanged.run();
            }
            showInfo("Stock in recorded. Inventory was updated automatically.");
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
        private final javafx.beans.property.DoubleProperty quantity = new javafx.beans.property.SimpleDoubleProperty();
        private final javafx.beans.property.ObjectProperty<BigDecimal> buyingPrice = new javafx.beans.property.SimpleObjectProperty<>(BigDecimal.ZERO);

        public StockInDraftLine(String productId, String productName, String barcode, double quantity, BigDecimal buyingPrice) {
            this.productId.set(productId);
            this.productName.set(productName);
            this.barcode.set(barcode);
            this.quantity.set(quantity);
            this.buyingPrice.set(buyingPrice);
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
