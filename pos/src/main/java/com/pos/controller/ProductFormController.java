package com.pos.controller;

import com.pos.database.DatabaseManager;
import com.pos.entity.Product;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public class ProductFormController {

    private static final Logger logger = LoggerFactory.getLogger(ProductFormController.class);

    private final DatabaseManager dbManager;
    private InventoryController parentController;
    private Product editingProduct;
    private String lastSavedBarcode = null;

    @FXML private TextField nameField;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private ComboBox<String> unitTypeComboBox;

    // Tier 1 (Retail)
    @FXML private TextField barcodeField;
    @FXML private TextField retailPriceField;
    @FXML private TextField tier1WholesalePriceField;
    @FXML private TextField loosePiecesField;
    @FXML private RadioButton radioSingle;
    @FXML private RadioButton radioBundle;
    @FXML private VBox bundleMathContainer;
    @FXML private TextField bundleSizeInput;

    // Tier 2 (Packet)
    @FXML private CheckBox packetEnabledCheck;
    @FXML private VBox packetFieldsContainer;
    @FXML private TextField packetBarcodeField;
    @FXML private TextField piecesPerPacketField;
    @FXML private TextField packetPriceField;
    @FXML private TextField packetStockField;

    // Tier 3 (Box)
    @FXML private CheckBox boxEnabledCheck;
    @FXML private VBox boxFieldsContainer;
    @FXML private Label packetsPerBoxLabel;
    @FXML private TextField boxBarcodeField;
    @FXML private TextField packetsPerBoxField;
    @FXML private TextField wholesalePriceField;
    @FXML private TextField boxStockField;

    // Volume Pricing
    @FXML private RadioButton radioStandardSingle;
    @FXML private RadioButton radioVolumePromo;
    @FXML private VBox volumePricingContainer;
    @FXML private TextField volumeQtyInput;
    @FXML private TextField volumePriceInput;

    // Tier 4 (Carton)
    @FXML private CheckBox tier4Enabled;
    @FXML private VBox tier4FieldsContainer;
    @FXML private TextField tier4Barcode;
    @FXML private TextField tier4YieldInput;
    @FXML private TextField tier4WholesaleInput;
    @FXML private TextField tier4StockInput;

    @FXML private CheckBox chkHalf;
    @FXML private CheckBox chkQuarter;

    @FXML private Button openBulkBtn;
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

        unitTypeComboBox.setItems(FXCollections.observableArrayList(
                "Pieces", "Kg", "Grams", "Liters", "Boxes", "Bags"
        ));
        unitTypeComboBox.setValue("Pieces");

        packetEnabledCheck.selectedProperty().addListener((obs, oldVal, selected) -> {
            packetFieldsContainer.setVisible(selected);
            packetFieldsContainer.setManaged(selected);
            updatePacketsPerBoxLabel(selected);
        });

        boxEnabledCheck.selectedProperty().addListener((obs, oldVal, selected) -> {
            boxFieldsContainer.setVisible(selected);
            boxFieldsContainer.setManaged(selected);
        });

        radioVolumePromo.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
            volumePricingContainer.setVisible(isNowSelected);
            volumePricingContainer.setManaged(isNowSelected);
            if (!isNowSelected) {
                volumeQtyInput.clear();
                volumePriceInput.clear();
            }
        });

        tier4Enabled.selectedProperty().addListener((obs, oldVal, selected) -> {
            tier4FieldsContainer.setVisible(selected);
            tier4FieldsContainer.setManaged(selected);
        });

        radioBundle.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
            bundleMathContainer.setVisible(isNowSelected);
            bundleMathContainer.setManaged(isNowSelected);

            if (!isNowSelected) {
                // If they switch back to Single, force the math back to 1
                bundleSizeInput.setText("1");
            } else {
                // If they switch to Bundle, clear it so they can type the bundle size
                bundleSizeInput.setText("");
            }
        });

        mergeCategoriesFromDatabase();
    }

    private void updatePacketsPerBoxLabel(boolean packetEnabled) {
        if (packetEnabled) {
            packetsPerBoxLabel.setText("Packets in ONE Box:");
            packetsPerBoxField.setPromptText("e.g., 10");
        } else {
            packetsPerBoxLabel.setText("Pieces in ONE Box:");
            packetsPerBoxField.setPromptText("e.g., 800");
        }
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
        if (tier1WholesalePriceField != null) {
            tier1WholesalePriceField.clear();
        }
        loosePiecesField.clear();
        if (radioSingle != null) {
            radioSingle.setSelected(true);
        }
        if (bundleSizeInput != null) {
            bundleSizeInput.setText("1");
        }
        unitTypeComboBox.setValue("Pieces");

        packetEnabledCheck.setSelected(false);
        packetBarcodeField.clear();
        piecesPerPacketField.clear();
        packetPriceField.clear();
        packetStockField.clear();

        boxEnabledCheck.setSelected(false);
        boxBarcodeField.clear();
        packetsPerBoxField.clear();
        wholesalePriceField.clear();
        boxStockField.clear();

        radioStandardSingle.setSelected(true);
        volumeQtyInput.clear();
        volumePriceInput.clear();

        tier4Enabled.setSelected(false);
        tier4Barcode.clear();
        tier4YieldInput.clear();
        tier4WholesaleInput.clear();
        tier4StockInput.clear();
        if (chkHalf != null) {
            chkHalf.setSelected(false);
        }
        if (chkQuarter != null) {
            chkQuarter.setSelected(false);
        }
    }

    public void loadProductForEdit(Product product) {
        if (product == null) {
            prepareNewProduct();
            return;
        }
        errorLabel.setText("");
        
        // Redirect portions (halves/quarters) to their parent (Single piece)
        if (product.getParentBarcode() != null && product.getDeductionRatio() < 1.0) {
            try {
                Optional<Product> parentOpt = dbManager.findProductByBarcode(product.getParentBarcode());
                if (parentOpt.isPresent()) {
                    product = parentOpt.get();
                }
            } catch (Exception e) {
                logger.error("Error finding parent product for editing redirect in ProductForm", e);
            }
        }
        
        // Tracing down the hierarchy to the base retail item (Tier 1)
        Product baseItem = dbManager.findBaseRetailItem(product.getBarcode());
        if (baseItem == null) {
            baseItem = product;
        }
        editingProduct = baseItem;

        barcodeField.setText(baseItem.getBarcode() != null ? baseItem.getBarcode() : "");
        barcodeField.setDisable(true);
        // Stripping packaging suffix for core name display
        String displayName = baseItem.getName() != null ? baseItem.getName() : "";
        if (displayName.endsWith(" (Single)")) {
            displayName = displayName.substring(0, displayName.length() - " (Single)".length());
        }
        nameField.setText(displayName);

        String cat = baseItem.getCategory();
        if (cat != null && !cat.isBlank()) {
            if (!categoryCombo.getItems().contains(cat)) {
                categoryCombo.getItems().add(cat);
            }
            categoryCombo.setValue(cat);
        } else {
            categoryCombo.setValue(null);
        }
        retailPriceField.setText(baseItem.getRetailPrice() != null ? baseItem.getRetailPrice().toPlainString() : "");
        if (tier1WholesalePriceField != null) {
            tier1WholesalePriceField.setText(baseItem.getWholesalePrice() != null ? baseItem.getWholesalePrice().toPlainString() : "");
        }
        loosePiecesField.setText(String.valueOf(baseItem.getStockQuantity()));
        if (baseItem.getBundleSize() > 1) {
            if (radioBundle != null) {
                radioBundle.setSelected(true);
            }
        } else {
            if (radioSingle != null) {
                radioSingle.setSelected(true);
            }
        }
        if (bundleSizeInput != null) {
            bundleSizeInput.setText(String.valueOf(baseItem.getBundleSize() > 0 ? baseItem.getBundleSize() : 1));
        }
        unitTypeComboBox.setValue(baseItem.getUnitType() != null ? baseItem.getUnitType() : "Pieces");

        // Load hierarchy by looking UP the tree from baseItem
        try {
            packetEnabledCheck.setSelected(false);
            boxEnabledCheck.setSelected(false);
            tier4Enabled.setSelected(false);
            radioStandardSingle.setSelected(true);
            volumePricingContainer.setVisible(false);
            volumePricingContainer.setManaged(false);
            if (chkHalf != null) {
                chkHalf.setSelected(false);
            }
            if (chkQuarter != null) {
                chkQuarter.setSelected(false);
            }
            if (baseItem.getBarcode() != null) {
                Product halfPiece = dbManager.getProduct(baseItem.getBarcode() + "-05");
                if (halfPiece != null && halfPiece.isActive()) {
                    if (chkHalf != null) {
                        chkHalf.setSelected(true);
                    }
                }
                Product quarterPiece = dbManager.getProduct(baseItem.getBarcode() + "-025");
                if (quarterPiece != null && quarterPiece.isActive()) {
                    if (chkQuarter != null) {
                        chkQuarter.setSelected(true);
                    }
                }
            }

            // Load Volume Pricing
            if (baseItem.getVolumeQty() > 0) {
                radioVolumePromo.setSelected(true);
                volumePricingContainer.setVisible(true);
                volumePricingContainer.setManaged(true);
                volumeQtyInput.setText(String.valueOf(baseItem.getVolumeQty()));
                volumePriceInput.setText(String.format("%.2f", baseItem.getVolumePrice()));
            }

            if (baseItem.getParentWholesaleBarcode() != null && !baseItem.getParentWholesaleBarcode().isEmpty()) {
                Optional<Product> parentOpt = dbManager.findProductByBarcode(baseItem.getParentWholesaleBarcode());
                if (parentOpt.isPresent()) {
                    Product parent = parentOpt.get();
                    
                    // Trace up to see how many ancestors there are
                    Optional<Product> grandparentOpt = Optional.empty();
                    if (parent.getParentWholesaleBarcode() != null && !parent.getParentWholesaleBarcode().isEmpty()) {
                        grandparentOpt = dbManager.findProductByBarcode(parent.getParentWholesaleBarcode());
                    }
                    
                    Optional<Product> greatGrandparentOpt = Optional.empty();
                    if (grandparentOpt.isPresent()) {
                        Product gp = grandparentOpt.get();
                        if (gp.getParentWholesaleBarcode() != null && !gp.getParentWholesaleBarcode().isEmpty()) {
                            greatGrandparentOpt = dbManager.findProductByBarcode(gp.getParentWholesaleBarcode());
                        }
                    }
                    
                    if (greatGrandparentOpt.isPresent()) {
                        // 4-Tier Hierarchy: baseItem -> parent (Packet) -> gp (Box) -> ggp (Carton)
                        Product gp = grandparentOpt.get();
                        Product ggp = greatGrandparentOpt.get();
                        
                        packetEnabledCheck.setSelected(true);
                        packetBarcodeField.setText(parent.getBarcode());
                        piecesPerPacketField.setText(String.valueOf(baseItem.getConversionYield()));
                        packetPriceField.setText(parent.getRetailPrice().toPlainString());
                        packetStockField.setText(String.valueOf(parent.getStockQuantity()));
                        
                        boxEnabledCheck.setSelected(true);
                        boxBarcodeField.setText(gp.getBarcode());
                        packetsPerBoxField.setText(String.valueOf(parent.getConversionYield()));
                        wholesalePriceField.setText(gp.getWholesalePrice().toPlainString());
                        boxStockField.setText(String.valueOf(gp.getStockQuantity()));
                        
                        tier4Enabled.setSelected(true);
                        tier4Barcode.setText(ggp.getBarcode());
                        tier4YieldInput.setText(String.valueOf(gp.getConversionYield()));
                        tier4WholesaleInput.setText(ggp.getWholesalePrice().toPlainString());
                        tier4StockInput.setText(String.valueOf(ggp.getStockQuantity()));
                    } else if (grandparentOpt.isPresent()) {
                        // 3-Tier Hierarchy: parent is Packet, gp is Box
                        Product gp = grandparentOpt.get();
                        packetEnabledCheck.setSelected(true);
                        packetBarcodeField.setText(parent.getBarcode());
                        piecesPerPacketField.setText(String.valueOf(baseItem.getConversionYield()));
                        packetPriceField.setText(parent.getRetailPrice().toPlainString());
                        packetStockField.setText(String.valueOf(parent.getStockQuantity()));

                        boxEnabledCheck.setSelected(true);
                        boxBarcodeField.setText(gp.getBarcode());
                        packetsPerBoxField.setText(String.valueOf(parent.getConversionYield()));
                        wholesalePriceField.setText(gp.getWholesalePrice().toPlainString());
                        boxStockField.setText(String.valueOf(gp.getStockQuantity()));
                    } else {
                        // 2-Tier Hierarchy: parent is either Packet or Box
                        if (parent.getName() != null && parent.getName().contains("(Packet)")) {
                            packetEnabledCheck.setSelected(true);
                            packetBarcodeField.setText(parent.getBarcode());
                            piecesPerPacketField.setText(String.valueOf(baseItem.getConversionYield()));
                            packetPriceField.setText(parent.getRetailPrice().toPlainString());
                            packetStockField.setText(String.valueOf(parent.getStockQuantity()));
                        } else {
                            boxEnabledCheck.setSelected(true);
                            boxBarcodeField.setText(parent.getBarcode());
                            packetsPerBoxField.setText(String.valueOf(baseItem.getConversionYield()));
                            wholesalePriceField.setText(parent.getWholesalePrice().toPlainString());
                            boxStockField.setText(String.valueOf(parent.getStockQuantity()));
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error loading hierarchy for edit", e);
        }
    }

    @FXML
    private void handleGenerateBarcode() {
        long n = Math.abs(java.util.concurrent.ThreadLocalRandom.current().nextLong() % 1_000_000_000_000L);
        barcodeField.setText("2" + String.format("%012d", n));
    }

    private String generateSemanticBarcode(String tierPrefix) {
        String baseName = nameField.getText().trim();
        
        // Fallback if they click generate before typing a name
        if (baseName.isEmpty()) {
            baseName = "product"; 
        } else {
            // Make lowercase and replace all spaces/special characters with hyphens
            baseName = baseName.toLowerCase().replaceAll("[^a-z0-9]+", "-");
            // Clean up leading/trailing hyphens if any
            baseName = baseName.replaceAll("^-+|-+$", "");
            if (baseName.isEmpty()) {
                baseName = "product";
            }
        }
        
        // Generate a random 4-digit number
        int randomSuffix = 1000 + new java.util.Random().nextInt(9000);
        
        return tierPrefix + "-" + baseName + "-" + randomSuffix;
    }

    @FXML
    private void generateTier2Barcode() {
        packetBarcodeField.setText(generateSemanticBarcode("packet"));
    }

    @FXML
    private void generateTier3Barcode() {
        boxBarcodeField.setText(generateSemanticBarcode("box"));
    }

    @FXML
    private void generateTier4Barcode() {
        tier4Barcode.setText(generateSemanticBarcode("carton"));
    }

    @FXML
    private void handleClear() {
        prepareNewProduct();
    }

    @FXML
    private void handleSave() {
        errorLabel.setText("");
        try {
            com.pos.service.SyncService syncService = com.pos.service.SyncService.getInstance();
            String name = nameField.getText().trim();
            String category = categoryCombo.getEditor().getText().trim();
            if (category.isEmpty() && categoryCombo.getValue() != null) {
                category = categoryCombo.getValue().trim();
            }
            if (category.isEmpty()) {
                category = "General";
            }
            String unitType = unitTypeComboBox.getValue();

            if (name.isEmpty()) {
                errorLabel.setText("Product name is required.");
                return;
            }

            boolean boxEnabled = boxEnabledCheck.isSelected();
            boolean packetEnabled = packetEnabledCheck.isSelected();
            boolean cartonEnabled = tier4Enabled.isSelected();

            // Validate Tier 1 (Retail Piece)
            String retailBarcode = barcodeField.getText().trim();
            if (retailBarcode.isEmpty()) {
                errorLabel.setText("Retail barcode is required.");
                return;
            }
            BigDecimal retailPrice = parseMoney(retailPriceField.getText().trim(), "Retail price");
            if (retailPrice.compareTo(BigDecimal.ZERO) <= 0) {
                errorLabel.setText("Retail selling price must be greater than 0.00.");
                return;
            }
            BigDecimal wholesalePrice = parseMoney(tier1WholesalePriceField.getText().trim(), "Wholesale price");
            if (wholesalePrice.compareTo(BigDecimal.ZERO) <= 0) {
                errorLabel.setText("Wholesale Price must be greater than 0.00.");
                return;
            }
            double retailStock = parseNonNegativeDouble(loosePiecesField.getText().trim(), "Retail stock");
            int bundleSize = 1;
            if (radioBundle != null && radioBundle.isSelected()) {
                if (bundleSizeInput != null && !bundleSizeInput.getText().trim().isEmpty()) {
                    bundleSize = parsePositiveInt(bundleSizeInput.getText().trim(), "Items per Retail Unit");
                }
            }

            // Validate Tier 2 (Packet)
            String packetBarcode = null;
            int piecesPerPacket = 0;
            BigDecimal packetPrice = BigDecimal.ZERO;
            double packetStock = 0;
            if (packetEnabled) {
                packetBarcode = packetBarcodeField.getText().trim();
                if (packetBarcode.isEmpty()) {
                    errorLabel.setText("Packet barcode is required when Packet tier is enabled.");
                    return;
                }
                piecesPerPacket = parsePositiveInt(piecesPerPacketField.getText().trim(), "Pieces per Packet");
                packetPrice = parseMoney(packetPriceField.getText().trim(), "Packet price");
                packetStock = parseNonNegativeDouble(packetStockField.getText().trim(), "Packet stock");
            }

            // Validate Tier 3 (Box)
            String boxBarcode = null;
            int packetsPerBox = 0;
            BigDecimal boxCost = BigDecimal.ZERO;
            double boxStock = 0;
            if (boxEnabled) {
                boxBarcode = boxBarcodeField.getText().trim();
                if (boxBarcode.isEmpty()) {
                    errorLabel.setText("Box barcode is required when Box tier is enabled.");
                    return;
                }
                packetsPerBox = parsePositiveInt(packetsPerBoxField.getText().trim(), packetEnabled ? "Packets per Box" : "Pieces per Box");
                boxCost = parseMoney(wholesalePriceField.getText().trim(), "Wholesale price");
                boxStock = parseNonNegativeDouble(boxStockField.getText().trim(), "Box stock");
            }

            // Validate Tier 4 (Carton)
            String cartonBarcode = null;
            int boxesPerCarton = 0;
            BigDecimal cartonWholesale = BigDecimal.ZERO;
            double cartonStock = 0;
            if (cartonEnabled) {
                cartonBarcode = tier4Barcode.getText().trim();
                if (cartonBarcode.isEmpty()) {
                    errorLabel.setText("Carton barcode is required when Carton tier is enabled.");
                    return;
                }
                boxesPerCarton = parsePositiveInt(tier4YieldInput.getText().trim(), "Boxes in ONE Carton");
                cartonWholesale = parseMoney(tier4WholesaleInput.getText().trim(), "Carton Wholesale price");
                cartonStock = parseNonNegativeDouble(tier4StockInput.getText().trim(), "Carton stock");
            }

            // Unique Barcode checks
            if (packetEnabled && retailBarcode.equalsIgnoreCase(packetBarcode)) {
                errorLabel.setText("Retail barcode and Packet barcode cannot be the same.");
                return;
            }
            if (boxEnabled && retailBarcode.equalsIgnoreCase(boxBarcode)) {
                errorLabel.setText("Retail barcode and Box barcode cannot be the same.");
                return;
            }
            if (boxEnabled && packetEnabled && packetBarcode.equalsIgnoreCase(boxBarcode)) {
                errorLabel.setText("Packet barcode and Box barcode cannot be the same.");
                return;
            }
            if (cartonEnabled) {
                if (retailBarcode.equalsIgnoreCase(cartonBarcode)) {
                    errorLabel.setText("Retail barcode and Carton barcode cannot be the same.");
                    return;
                }
                if (packetEnabled && packetBarcode.equalsIgnoreCase(cartonBarcode)) {
                    errorLabel.setText("Packet barcode and Carton barcode cannot be the same.");
                    return;
                }
                if (boxEnabled && boxBarcode.equalsIgnoreCase(cartonBarcode)) {
                    errorLabel.setText("Box barcode and Carton barcode cannot be the same.");
                    return;
                }
            }

            // Validate Volume Pricing
            int volumeQty = 0;
            double volumePrice = 0.0;
            if (radioVolumePromo.isSelected()) {
                volumeQty = parsePositiveInt(volumeQtyInput.getText().trim(), "Volume Quantity");
                BigDecimal vPrice = parseMoney(volumePriceInput.getText().trim(), "Volume Promo Price");
                if (vPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    errorLabel.setText("Volume promo price must be greater than 0.00.");
                    return;
                }
                volumePrice = vPrice.doubleValue();
            }

            String masterBarcode = null;
            int yield = 0;

            // Step 1: Handle Master Carton (Tier 4)
            if (cartonEnabled) {
                Product carton = dbManager.getProduct(cartonBarcode);
                boolean isNew = (carton == null);
                if (isNew) {
                    carton = new Product();
                    carton.setBarcode(cartonBarcode);
                }
                carton.setName(name + " (Master Carton)");
                carton.setCategory(category);
                carton.setUnitType(unitType);
                
                BigDecimal cartonCost = cartonWholesale;
                if (cartonCost.compareTo(BigDecimal.ZERO) == 0) {
                    if (boxEnabled) {
                        BigDecimal boxPrice = boxCost;
                        if (boxPrice.compareTo(BigDecimal.ZERO) == 0) {
                            if (packetEnabled) {
                                boxPrice = packetPrice.compareTo(BigDecimal.ZERO) == 0 ? retailPrice.multiply(BigDecimal.valueOf(piecesPerPacket)) : packetPrice;
                                boxPrice = boxPrice.multiply(BigDecimal.valueOf(packetsPerBox));
                            } else {
                                boxPrice = retailPrice.multiply(BigDecimal.valueOf(packetsPerBox));
                            }
                        }
                        cartonCost = boxPrice.multiply(BigDecimal.valueOf(boxesPerCarton));
                    } else if (packetEnabled) {
                        BigDecimal packPrice = packetPrice.compareTo(BigDecimal.ZERO) == 0 ? retailPrice.multiply(BigDecimal.valueOf(piecesPerPacket)) : packetPrice;
                        cartonCost = packPrice.multiply(BigDecimal.valueOf(boxesPerCarton));
                    } else {
                        cartonCost = retailPrice.multiply(BigDecimal.valueOf(boxesPerCarton));
                    }
                }
                carton.setWholesalePrice(cartonCost);
                carton.setRetailPrice(cartonCost);
                carton.setStockQuantity(cartonStock);
                carton.setMinStockLevel(0);
                carton.setStatus("APPROVED");
                carton.setRawPieceYield(boxesPerCarton);
                
                carton.setParentBarcode(null); // Absolute top
                carton.setParentWholesaleBarcode(null);
                carton.setConversionYield(0);

                if (isNew) {
                    dbManager.insertProduct(carton);
                    logger.info("Master Carton created: {}", carton.getName());
                } else {
                    carton.setUpdatedAt(java.time.LocalDateTime.now());
                    dbManager.updateProduct(carton);
                    logger.info("Master Carton updated: {}", carton.getName());
                }
                syncService.syncProductToCloud(carton);
                masterBarcode = carton.getBarcode();
                yield = boxesPerCarton;
            }

            // Step 2: Handle Master Box (Tier 3)
            if (boxEnabled) {
                Product box = dbManager.getProduct(boxBarcode);
                boolean isNew = (box == null);
                if (isNew) {
                    box = new Product();
                    box.setBarcode(boxBarcode);
                }
                box.setName(name + " (Master Box)");
                box.setCategory(category);
                box.setUnitType(unitType);
                
                BigDecimal calculatedBoxRetail = boxCost;
                if (calculatedBoxRetail.compareTo(BigDecimal.ZERO) == 0) {
                    if (packetEnabled) {
                        BigDecimal calculatedPacketRetail = packetPrice;
                        if (calculatedPacketRetail.compareTo(BigDecimal.ZERO) == 0) {
                            calculatedPacketRetail = retailPrice.multiply(BigDecimal.valueOf(piecesPerPacket));
                        }
                        calculatedBoxRetail = BigDecimal.valueOf(packetsPerBox).multiply(calculatedPacketRetail);
                    } else {
                        calculatedBoxRetail = BigDecimal.valueOf(packetsPerBox).multiply(retailPrice);
                    }
                }
                box.setRetailPrice(calculatedBoxRetail);
                box.setWholesalePrice(calculatedBoxRetail);
                box.setStockQuantity(boxStock);
                box.setMinStockLevel(0);
                box.setStatus("APPROVED");
                box.setRawPieceYield(packetsPerBox);

                box.setParentBarcode(masterBarcode);
                box.setParentWholesaleBarcode(masterBarcode);
                box.setConversionYield(yield);

                if (isNew) {
                    dbManager.insertProduct(box);
                    logger.info("Master Box created: {}", box.getName());
                } else {
                    box.setUpdatedAt(java.time.LocalDateTime.now());
                    dbManager.updateProduct(box);
                    logger.info("Master Box updated: {}", box.getName());
                }
                syncService.syncProductToCloud(box);
                masterBarcode = box.getBarcode();
                yield = packetsPerBox;
            }

            // Step 3: Handle Packet (Tier 2)
            if (packetEnabled) {
                Product packet = dbManager.getProduct(packetBarcode);
                boolean isNew = (packet == null);
                if (isNew) {
                    packet = new Product();
                    packet.setBarcode(packetBarcode);
                }
                packet.setName(name + " (Packet)");
                packet.setCategory(category);
                packet.setUnitType(unitType);
                
                BigDecimal calculatedPacketRetail = packetPrice;
                if (calculatedPacketRetail.compareTo(BigDecimal.ZERO) == 0) {
                    calculatedPacketRetail = retailPrice.multiply(BigDecimal.valueOf(piecesPerPacket));
                }
                packet.setRetailPrice(calculatedPacketRetail);
                packet.setWholesalePrice(calculatedPacketRetail);
                packet.setStockQuantity(packetStock);
                packet.setMinStockLevel(0);
                packet.setStatus("APPROVED");
                packet.setRawPieceYield(piecesPerPacket);

                packet.setParentBarcode(masterBarcode);
                packet.setParentWholesaleBarcode(masterBarcode);
                packet.setConversionYield(yield);

                if (isNew) {
                    dbManager.insertProduct(packet);
                    logger.info("Packet created: {}", packet.getName());
                } else {
                    packet.setUpdatedAt(java.time.LocalDateTime.now());
                    dbManager.updateProduct(packet);
                    logger.info("Packet updated: {}", packet.getName());
                }
                syncService.syncProductToCloud(packet);
                masterBarcode = packet.getBarcode();
                yield = piecesPerPacket;
            }

            // Step 4: Handle Retail Piece (Tier 1)
            Product piece;
            boolean isNewPiece = (editingProduct == null);
            if (!isNewPiece) {
                piece = editingProduct;
                piece.setBarcode(retailBarcode);
            } else {
                piece = new Product();
                piece.setBarcode(retailBarcode);
            }
            piece.setName(name + " (Single)");
            piece.setCategory(category);
            piece.setUnitType(unitType);
            piece.setRetailPrice(retailPrice);
            piece.setWholesalePrice(wholesalePrice);
            piece.setStockQuantity(retailStock);
            piece.setMinStockLevel(0);
            piece.setStatus("APPROVED");
            piece.setBundleSize(bundleSize);

            piece.setParentBarcode(masterBarcode);
            piece.setParentWholesaleBarcode(masterBarcode);
            piece.setConversionYield(yield);

            if (radioVolumePromo.isSelected()) {
                piece.setVolumeQty(volumeQty);
                piece.setVolumePrice(volumePrice);
            } else {
                piece.setVolumeQty(0);
                piece.setVolumePrice(0.0);
            }

            if (isNewPiece) {
                dbManager.insertProduct(piece);
                logger.info("Retail Piece created: {}", piece.getName());
            } else {
                piece.setUpdatedAt(java.time.LocalDateTime.now());
                dbManager.updateProduct(piece);
                logger.info("Retail Piece updated: {}", piece.getName());
            }
            syncService.syncProductToCloud(piece);

            // Auto-generate Half Portion
            if (chkHalf != null && chkHalf.isSelected()) {
                String halfBarcode = piece.getBarcode() + "-05";
                Product halfPiece = dbManager.getProduct(halfBarcode);
                boolean isNewHalf = (halfPiece == null);
                if (isNewHalf) {
                    halfPiece = new Product();
                    halfPiece.setBarcode(halfBarcode);
                }
                
                String baseName = piece.getName();
                if (baseName.endsWith(" (Single)")) {
                    baseName = baseName.substring(0, baseName.length() - " (Single)".length());
                }
                halfPiece.setName(baseName + " (Half)");
                halfPiece.setParentBarcode(piece.getBarcode()); // Links to full Tier 1 item
                halfPiece.setParentWholesaleBarcode(piece.getBarcode());
                halfPiece.setRetailPrice(piece.getRetailPrice().divide(BigDecimal.valueOf(2.0), 2, RoundingMode.HALF_UP));
                halfPiece.setWholesalePrice(piece.getWholesalePrice().divide(BigDecimal.valueOf(2.0), 2, RoundingMode.HALF_UP));
                halfPiece.setDeductionRatio(0.5); // Ensures selling 1 deducts 0.5 from parent
                halfPiece.setCategory(piece.getCategory());
                halfPiece.setUnitType(piece.getUnitType());
                halfPiece.setStockQuantity(0.0);
                halfPiece.setStatus("APPROVED");

                if (isNewHalf) {
                    dbManager.insertProduct(halfPiece);
                    logger.info("Half portion created: {}", halfPiece.getName());
                } else {
                    halfPiece.setUpdatedAt(java.time.LocalDateTime.now());
                    dbManager.updateProduct(halfPiece);
                    logger.info("Half portion updated: {}", halfPiece.getName());
                }
                syncService.syncProductToCloud(halfPiece);
            }

            // Auto-generate Quarter Portion
            if (chkQuarter != null && chkQuarter.isSelected()) {
                String quarterBarcode = piece.getBarcode() + "-025";
                Product quarterPiece = dbManager.getProduct(quarterBarcode);
                boolean isNewQuarter = (quarterPiece == null);
                if (isNewQuarter) {
                    quarterPiece = new Product();
                    quarterPiece.setBarcode(quarterBarcode);
                }
                
                String baseName = piece.getName();
                if (baseName.endsWith(" (Single)")) {
                    baseName = baseName.substring(0, baseName.length() - " (Single)".length());
                }
                quarterPiece.setName(baseName + " (Quarter)");
                quarterPiece.setParentBarcode(piece.getBarcode());
                quarterPiece.setParentWholesaleBarcode(piece.getBarcode());
                quarterPiece.setRetailPrice(piece.getRetailPrice().divide(BigDecimal.valueOf(4.0), 2, RoundingMode.HALF_UP));
                quarterPiece.setWholesalePrice(piece.getWholesalePrice().divide(BigDecimal.valueOf(4.0), 2, RoundingMode.HALF_UP));
                quarterPiece.setDeductionRatio(0.25); // Ensures selling 1 deducts 0.25 from parent
                quarterPiece.setCategory(piece.getCategory());
                quarterPiece.setUnitType(piece.getUnitType());
                quarterPiece.setStockQuantity(0.0);
                quarterPiece.setStatus("APPROVED");

                if (isNewQuarter) {
                    dbManager.insertProduct(quarterPiece);
                    logger.info("Quarter portion created: {}", quarterPiece.getName());
                } else {
                    quarterPiece.setUpdatedAt(java.time.LocalDateTime.now());
                    dbManager.updateProduct(quarterPiece);
                    logger.info("Quarter portion updated: {}", quarterPiece.getName());
                }
                syncService.syncProductToCloud(quarterPiece);
            }

            lastSavedBarcode = retailBarcode;

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
        if (raw == null || raw.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid " + label + ".");
        }
    }

    private double parseNonNegativeDouble(String raw, String label) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0.0;
        }
        try {
            double v = Double.parseDouble(raw.trim());
            if (v < 0) {
                throw new NumberFormatException(label + " cannot be negative.");
            }
            return v;
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid " + label + ".");
        }
    }

    private int parsePositiveInt(String raw, String label) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new NumberFormatException(label + " is required.");
        }
        try {
            int v = Integer.parseInt(raw.trim());
            if (v <= 0) {
                throw new NumberFormatException(label + " must be greater than zero.");
            }
            return v;
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid " + label + ".");
        }
    }

    public void prefillBarcode(String barcode) {
        if (barcodeField != null) {
            barcodeField.setText(barcode);
        }
    }

    public String getLastSavedBarcode() {
        return lastSavedBarcode;
    }
}
