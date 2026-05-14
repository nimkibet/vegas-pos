package com.pos.controller;

import com.pos.database.DatabaseManager;
import com.pos.entity.Product;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

public class ProductFormController {

    private static final Logger logger = LoggerFactory.getLogger(ProductFormController.class);

    private final DatabaseManager dbManager;
    private InventoryController parentController;
    private Product editingProduct;

    @FXML private TextField barcodeField;
    @FXML private TextField nameField;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private TextField retailPriceField;
    @FXML private TextField wholesalePriceField;
    @FXML private CheckBox hasBulkCheckbox;
    @FXML private VBox bulkFieldsContainer;
    @FXML private HBox looseStockContainer;
    @FXML private TextField bulkBarcodeField;
    @FXML private TextField bulkPriceField;
    @FXML private TextField piecesPerBoxField;
    @FXML private TextField boxesReceivedField;
    @FXML private TextField loosePiecesField;
    @FXML private Label errorLabel;

    public ProductFormController() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public void setParentController(InventoryController parentController) {
        this.parentController = parentController;
    }

    @FXML
    public void initialize() {
        categoryCombo.setItems(FXCollections.observableArrayList(
                "General", "Bakery", "Dairy", "Beverages", "Cooking", "Baking",
                "Staples", "Household", "Personal Care", "Produce", "Frozen", "Snacks"
        ));

        hasBulkCheckbox.selectedProperty().addListener((obs, oldVal, selected) -> {
            bulkFieldsContainer.setVisible(selected);
            bulkFieldsContainer.setManaged(selected);
            // Loose row stays visible: when bulk is off it carries total piece count; when on, extra loose pieces.
            looseStockContainer.setVisible(true);
            looseStockContainer.setManaged(true);
        });

        mergeCategoriesFromDatabase();
    }

    private void mergeCategoriesFromDatabase() {
        try {
            Set<String> names = new LinkedHashSet<>(categoryCombo.getItems());
            for (Product p : dbManager.getAllProducts()) {
                if (p.getCategory() != null && !p.getCategory().isBlank()) {
                    names.add(p.getCategory());
                }
            }
            categoryCombo.setItems(FXCollections.observableArrayList(names));
        } catch (Exception e) {
            logger.debug("Could not merge categories from DB", e);
        }
    }

    public void prepareNewProduct() {
        editingProduct = null;
        errorLabel.setText("");
        barcodeField.clear();
        barcodeField.setDisable(false);
        nameField.clear();
        categoryCombo.getSelectionModel().clearSelection();
        categoryCombo.setValue(null);
        retailPriceField.clear();
        wholesalePriceField.clear();
        hasBulkCheckbox.setSelected(false);
        bulkBarcodeField.clear();
        bulkPriceField.clear();
        piecesPerBoxField.clear();
        boxesReceivedField.clear();
        loosePiecesField.clear();
        bulkFieldsContainer.setVisible(false);
        bulkFieldsContainer.setManaged(false);
    }

    public void loadProductForEdit(Product product) {
        if (product == null) {
            prepareNewProduct();
            return;
        }
        editingProduct = product;
        errorLabel.setText("");
        barcodeField.setText(product.getBarcode() != null ? product.getBarcode() : "");
        barcodeField.setDisable(true);
        nameField.setText(product.getName() != null ? product.getName() : "");
        String cat = product.getCategory();
        if (cat != null && !cat.isBlank()) {
            if (!categoryCombo.getItems().contains(cat)) {
                categoryCombo.getItems().add(cat);
            }
            categoryCombo.setValue(cat);
        } else {
            categoryCombo.setValue(null);
        }
        retailPriceField.setText(product.getRetailPrice() != null ? product.getRetailPrice().toPlainString() : "");
        wholesalePriceField.setText(product.getWholesalePrice() != null ? product.getWholesalePrice().toPlainString() : "");

        boolean hasBulk = product.getBulkBarcode() != null && !product.getBulkBarcode().isBlank()
                || product.getPiecesPerBulk() > 1;
        hasBulkCheckbox.setSelected(hasBulk);
        bulkFieldsContainer.setVisible(hasBulk);
        bulkFieldsContainer.setManaged(hasBulk);

        bulkBarcodeField.setText(product.getBulkBarcode() != null ? product.getBulkBarcode() : "");
        bulkPriceField.setText(product.getBulkPrice() != null ? product.getBulkPrice().toPlainString() : "");
        piecesPerBoxField.setText(product.getPiecesPerBulk() > 0 ? String.valueOf(product.getPiecesPerBulk()) : "1");
        boxesReceivedField.clear();
        loosePiecesField.setText(String.valueOf(product.getStockQuantity()));
    }

    @FXML
    private void handleGenerateBarcode() {
        long n = Math.abs(java.util.concurrent.ThreadLocalRandom.current().nextLong() % 1_000_000_000_000L);
        barcodeField.setText("2" + String.format("%012d", n));
    }

    @FXML
    private void handleClear() {
        prepareNewProduct();
    }

    @FXML
    private void handleSave() {
        errorLabel.setText("");
        try {
            String barcode = barcodeField.getText().trim();
            String name = nameField.getText().trim();
            String category = categoryCombo.getEditor().getText().trim();
            if (category.isEmpty() && categoryCombo.getValue() != null) {
                category = categoryCombo.getValue().trim();
            }

            if (barcode.isEmpty() || name.isEmpty()) {
                errorLabel.setText("Barcode and product name are required.");
                return;
            }

            BigDecimal retail = parseMoney(retailPriceField.getText().trim(), "Retail price");
            BigDecimal wholesale = parseMoney(wholesalePriceField.getText().trim(), "Wholesale price");

            int totalStock = computeTotalStockPieces();
            if (totalStock < 0) {
                errorLabel.setText("Stock quantities cannot be negative.");
                return;
            }

            boolean bulk = hasBulkCheckbox.isSelected();
            if (bulk) {
                String bb = bulkBarcodeField.getText().trim();
                if (bb.isEmpty()) {
                    errorLabel.setText("Box barcode is required when bulk option is enabled.");
                    return;
                }
                int piecesPer = parsePositiveInt(piecesPerBoxField.getText().trim(), "Pieces per box", 1);
                if (piecesPer <= 0) {
                    errorLabel.setText("Pieces per box must be at least 1.");
                    return;
                }
            }

            if (editingProduct != null) {
                applyCommonFields(editingProduct, barcode, name, category, retail, wholesale, totalStock, bulk);
                dbManager.updateProduct(editingProduct);
                logger.info("Product updated: {}", name);
            } else {
                Product p = new Product();
                applyCommonFields(p, barcode, name, category, retail, wholesale, totalStock, bulk);
                dbManager.insertProduct(p);
                logger.info("New product created: {}", name);
            }

            if (parentController != null) {
                parentController.refreshTable();
                parentController.switchToTableTab();
            }
            prepareNewProduct();

        } catch (NumberFormatException e) {
            errorLabel.setText(e.getMessage() != null ? e.getMessage() : "Invalid number.");
        } catch (Exception e) {
            logger.error("Error saving product", e);
            errorLabel.setText("Error saving product: " + e.getMessage());
        }
    }

    private BigDecimal parseMoney(String raw, String label) {
        if (raw.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid " + label + ".");
        }
    }

    private int parsePositiveInt(String raw, String label, int defaultIfEmpty) {
        if (raw == null || raw.isEmpty()) {
            return defaultIfEmpty;
        }
        return Integer.parseInt(raw);
    }

    private int computeTotalStockPieces() {
        boolean bulk = hasBulkCheckbox.isSelected();
        int loose = parseNonNegativeInt(loosePiecesField.getText().trim());
        if (!bulk) {
            return loose;
        }
        int boxes = parseNonNegativeInt(boxesReceivedField.getText().trim());
        int perBox = parseNonNegativeInt(piecesPerBoxField.getText().trim());
        if (perBox <= 0) {
            perBox = 1;
        }
        return boxes * perBox + loose;
    }

    private int parseNonNegativeInt(String raw) {
        if (raw == null || raw.isEmpty()) {
            return 0;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return Math.max(0, v);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Enter valid whole numbers for stock.");
        }
    }

    private void applyCommonFields(Product p, String barcode, String name, String category,
                                   BigDecimal retail, BigDecimal wholesale, int totalStock, boolean bulk) {
        if (editingProduct == null) {
            p.setBarcode(barcode);
        }
        p.setName(name);
        p.setCategory(category.isEmpty() ? "General" : category);
        p.setRetailPrice(retail);
        p.setWholesalePrice(wholesale);
        p.setStockQuantity(totalStock);
        p.setMinStockLevel(0);
        p.setDescription("");
        p.setSupplier("");
        p.setImagePath(null);
        p.setStatus("APPROVED");
        p.setActive(true);
        p.setSynced(false);
        p.setUpdatedAt(java.time.LocalDateTime.now());

        if (bulk) {
            p.setBulkBarcode(bulkBarcodeField.getText().trim());
            p.setBulkPrice(parseMoney(bulkPriceField.getText().trim(), "Box price"));
            int per = parseNonNegativeInt(piecesPerBoxField.getText().trim());
            p.setPiecesPerBulk(per <= 0 ? 1 : per);
        } else {
            p.setBulkBarcode(null);
            p.setBulkPrice(BigDecimal.ZERO);
            p.setPiecesPerBulk(1);
        }
    }
}
