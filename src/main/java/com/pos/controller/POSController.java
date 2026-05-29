package com.pos.controller;

import com.pos.database.DatabaseManager;
import com.pos.entity.Product;
import com.pos.entity.ProductBarcodeMatch;
import com.pos.util.BrandingConstants;
import com.pos.entity.Sale;
import com.pos.entity.SaleItem;
import com.pos.entity.User;
import com.pos.entity.ActivityLog;
import com.pos.service.AuthenticationService;
import com.pos.service.PrinterService;
import com.pos.service.SyncService;
import com.pos.service.pricing.PricingContext;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.Callback;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Predicate;

/**
 * POS Controller
 * Main POS screen controller with wholesale toggle, global barcode scanner support, and live search.
 */
public class POSController {
    
    private static final Logger logger = LoggerFactory.getLogger(POSController.class);
    
    // Barcode scanner detection settings
    private static final int SCANNER_KEY_TIMEOUT_MS = 50;
    private static final int BUFFER_RESET_TIME_MS = 500;
    private static final int LIVE_SEARCH_DEBOUNCE_MS = 200;
    private static final int LARGE_LIST_THRESHOLD = 500;
    
    // Services
    private final DatabaseManager dbManager;
    private final AuthenticationService authService;
    private final PricingContext pricingContext;
    private final PrinterService printerService;
    private final SyncService syncService;
    
    // Current state
    private Sale currentSale;
    private User currentUser;
    private StringBuilder barcodeBuffer;
    private Timer barcodeTimer;
    private boolean isScannerInput;
    private Timeline searchDebounceTimeline;
    private boolean isDatabaseSearchMode;
    
    // Observable lists
    private ObservableList<Product> productList;
    private FilteredList<Product> filteredProductList;
    private ObservableList<SaleItem> cartList;
    
    // Hold Cart storage
    private Map<String, Sale> heldCarts;
    private int holdCartCounter;
    
    // FXML components - Products Table
    @FXML
    private TableView<Product> productTable;
    @FXML
    private MenuItem contextMenuAddBoxItem;
    @FXML
    private Button addBoxButton;
    @FXML
    private TableColumn<Product, String> colBarcode;
    @FXML
    private TableColumn<Product, String> colProductName;
    @FXML
    private TableColumn<Product, String> colRetailPrice;
    @FXML
    private TableColumn<Product, String> colWholesalePrice;
    @FXML
    private TableColumn<Product, Double> colStock;
    
    // FXML components - Cart Table
    @FXML
    private TableView<SaleItem> cartTable;
    @FXML
    private TableColumn<SaleItem, String> colItemName;
    @FXML
    private TableColumn<SaleItem, String> colQuantity;
    @FXML
    private TableColumn<SaleItem, String> colUnitPrice;
    @FXML
    private TableColumn<SaleItem, String> colTotalPrice;
    
    // FXML components - Controls
    @FXML
    private TextField searchField;
    @FXML
    private TextField barcodeField;
    @FXML
    private TextField quantityField;
    @FXML
    private ToggleButton wholesaleToggle;
    @FXML
    private Label userLabel;
    @FXML
    private Label statusLabel;
    
    // FXML components - Totals
    @FXML
    private Label subtotalLabel;
    @FXML
    private Label taxLabel;
    @FXML
    private Label discountLabel;
    @FXML
    private Label totalLabel;
    @FXML
    private Label amountPaidField;
    @FXML
    private Label changeLabel;
    
    // FXML components - Buttons
    @FXML
    private Button addToCartButton;
    @FXML
    private Button removeFromCartButton;
    @FXML
    private Button clearCartButton;
    @FXML
    private Button checkoutButton;
    @FXML
    private Button printReceiptButton;
    @FXML
    private Button openDrawerButton;
    @FXML
    private Button printLabelButton;
    @FXML
    private Button logoutButton;
    @FXML
    private Button refreshButton;
    @FXML
    private Button backofficeButton;
    @FXML
    private Button profileButton;
    
    public POSController() {
        this.dbManager = DatabaseManager.getInstance();
        this.authService = AuthenticationService.getInstance();
        this.pricingContext = new PricingContext();
        this.printerService = PrinterService.getInstance();
        this.syncService = SyncService.getInstance();
        this.productList = FXCollections.observableArrayList();
        this.filteredProductList = new FilteredList<>(productList, p -> true);
        this.cartList = FXCollections.observableArrayList();
        this.barcodeBuffer = new StringBuilder();
        this.barcodeTimer = new Timer();
        this.isScannerInput = false;
        this.isDatabaseSearchMode = false;
        this.heldCarts = new HashMap<>();
        this.holdCartCounter = 0;
    }
    
    /**
     * Set current user - called AFTER FXML load completes
     * This is where ALL user-role dependent UI logic belongs
     */
    public void setCurrentUser(User user) {
        this.currentUser = user;
        
        // Apply user data to UI - @FXML elements are guaranteed to be loaded now
        if (user != null) {
            if (userLabel != null) {
                userLabel.setText("User: " + user.getFullName() + " (" + user.getRole().getDisplayName() + ")");
            }
            if (backofficeButton != null) {
                backofficeButton.setVisible(user.getRole() == User.Role.ADMIN);
            }
        } else {
            // No user - hide admin features
            if (backofficeButton != null) {
                backofficeButton.setVisible(false);
            }
        }
    }
    
    /**
     * Initialize the controller - ONLY set up table columns and static listeners
     * NO user-role dependent logic here - it goes in setCurrentUser()
     */
    @FXML
    public void initialize() {
        // Setup product table columns
        colBarcode.setCellValueFactory(new PropertyValueFactory<>("barcode"));
        colProductName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colRetailPrice.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(
                String.format("%.2f", cellData.getValue().getRetailPrice())));
        colWholesalePrice.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(
                String.format("%.2f", cellData.getValue().getWholesalePrice())));
        colStock.setCellValueFactory(new PropertyValueFactory<>("stockQuantity"));
        
        colStock.setCellFactory(column -> new TableCell<Product, Double>() {
            @Override
            protected void updateItem(Double stock, boolean empty) {
                super.updateItem(stock, empty);
                if (empty || stock == null) {
                    setText("");
                    setStyle("");
                } else {
                    setText(String.format("%.2f", stock));
                    if (stock <= 5) {
                        setStyle("-fx-background-color: #fed7d7; -fx-text-fill: #c53030; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        
        // Setup cart table columns
        colItemName.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colQuantity.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getDisplayQuantity()));
        colUnitPrice.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(
                String.format("%.2f", cellData.getValue().getUnitPrice())));
        colTotalPrice.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(
                String.format("%.2f", cellData.getValue().getTotalPrice())));

        // Make cart table editable
        setupCartTableEditing();
        
        // Use FilteredList for the table
        productTable.setItems(filteredProductList);
        cartTable.setItems(cartList);
        
        productTable.setRowFactory(tv -> {
            TableRow<Product> row = new TableRow<>();
            row.setOnMouseClicked(event -> handleProductTableRowMouseClicked(event, row));
            return row;
        });
        
        productTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) ->
            updateProductTableBoxSaleActions(n));
        ContextMenu productCm = productTable.getContextMenu();
        if (productCm != null) {
            productCm.setOnShowing(e -> updateProductTableBoxSaleActions(
                productTable.getSelectionModel().getSelectedItem()));
        }
        updateProductTableBoxSaleActions(productTable.getSelectionModel().getSelectedItem());
        
        // Add keyboard listeners
        searchField.setOnKeyPressed(this::handleSearchKeyPress);
        productTable.setOnKeyPressed(this::handleProductTableKeyPress);
        
        // Setup live search with ChangeListener
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            handleSearchTextChanged(newValue);
        });
        
        // Initialize new sale
        startNewSale();

        // Load products
        loadProducts();
        
        // Update UI
        updatePriceMode();
        updateStatus();
        
        // Auto-focus barcode field for scanner
        requestBarcodeFocus();
    }

    private void setupCartTableEditing() {
        colQuantity.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
        colQuantity.setOnEditCommit(event -> {
            try {
                // Try to parse the numeric part of the input
                String input = event.getNewValue().trim();
                // If it ends with the unit, try to remove it
                SaleItem item = event.getRowValue();
                String unit = (item.getProduct() != null && item.getProduct().getUnitType() != null) 
                             ? item.getProduct().getUnitType() : "Pieces";
                
                if (input.endsWith(unit)) {
                    input = input.substring(0, input.length() - unit.length()).trim();
                }
                
                double newQty = Double.parseDouble(input);
                item.setQuantity(newQty);
                refreshCartTotals();
                cartTable.refresh();
            } catch (NumberFormatException e) {
                showError("Invalid quantity format.");
                cartTable.refresh();
            }
        });

        colUnitPrice.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
        colUnitPrice.setOnEditCommit(event -> {
            try {
                BigDecimal newPrice = new BigDecimal(event.getNewValue());
                SaleItem item = event.getRowValue();
                item.setUnitPrice(newPrice);
                item.recalculateTotal();
                refreshCartTotals();
                cartTable.refresh();
            } catch (NumberFormatException e) {
                showError("Invalid price format.");
                cartTable.refresh();
            }
        });
    }
    
    /**
     * Request focus on barcode field for scanner input
     */
    private void requestBarcodeFocus() {
        javafx.application.Platform.runLater(() -> {
            if (barcodeField != null) {
                barcodeField.requestFocus();
            }
        });
    }
    
    /**
     * Handle search text changes with debouncing
     * For small lists (<500): use in-memory FilteredList predicate
     * For large lists (>=500): use database LIKE with debounce
     */
    private void handleSearchTextChanged(String searchTerm) {
        // Cancel any existing debounce timer
        if (searchDebounceTimeline != null) {
            searchDebounceTimeline.stop();
        }
        
        // Determine search mode based on list size
        if (productList.size() >= LARGE_LIST_THRESHOLD) {
            // Database search mode with debounce
            isDatabaseSearchMode = true;
            searchDebounceTimeline = new Timeline(
                new KeyFrame(Duration.millis(LIVE_SEARCH_DEBOUNCE_MS), event -> {
                    performDatabaseSearch(searchTerm);
                })
            );
            searchDebounceTimeline.setCycleCount(1);
            searchDebounceTimeline.play();
        } else {
            // In-memory search (instant)
            isDatabaseSearchMode = false;
            performInMemorySearch(searchTerm);
        }
    }
    
    /**
     * Perform in-memory search using FilteredList predicate
     */
    private void performInMemorySearch(String searchTerm) {
        Predicate<Product> predicate;
        
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            predicate = p -> true;
        } else {
            String lowerSearchTerm = searchTerm.toLowerCase().trim();
            predicate = p -> {
                String n = p.getName() != null ? p.getName().toLowerCase() : "";
                String bc = p.getBarcode() != null ? p.getBarcode().toLowerCase() : "";
                String bbc = p.getBulkBarcode() != null ? p.getBulkBarcode().toLowerCase() : "";
                return n.contains(lowerSearchTerm)
                        || bc.contains(lowerSearchTerm)
                        || bbc.contains(lowerSearchTerm);
            };
        }
        
        filteredProductList.setPredicate(predicate);
    }
    
    /**
     * Perform database search with LIKE
     */
    private void performDatabaseSearch(String searchTerm) {
        try {
            List<Product> results;
            if (searchTerm == null || searchTerm.trim().isEmpty()) {
                results = dbManager.getAllProducts();
            } else {
                results = dbManager.searchProducts(searchTerm.trim());
            }
            
            // Update the filtered list on JavaFX thread
            javafx.application.Platform.runLater(() -> {
                productList.clear();
                productList.addAll(results);
            });
        } catch (Exception e) {
            logger.error("Error performing database search", e);
        }
    }
    
    /**
     * Handle global key events for barcode scanner detection
     */
    @FXML
    private void handleGlobalKeyEvent(KeyEvent event) {
        String key = event.getText();
        
        // Cancel existing timer
        barcodeTimer.cancel();
        barcodeTimer = new Timer();
        
        // Check if it's a digit
        if (key != null && key.matches("\\d")) {
            barcodeBuffer.append(key);
            isScannerInput = true;
            
            // Set timer to process potential barcode
            barcodeTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (barcodeBuffer.length() >= 4) {
                        processBarcode(barcodeBuffer.toString());
                    }
                    barcodeBuffer.setLength(0);
                    isScannerInput = false;
                }
            }, SCANNER_KEY_TIMEOUT_MS);
            
        } else if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
            if (barcodeBuffer.length() > 0 && isScannerInput) {
                processBarcode(barcodeBuffer.toString());
                barcodeBuffer.setLength(0);
                isScannerInput = false;
            } else {
                String barcode = barcodeField.getText().trim();
                if (!barcode.isEmpty()) {
                    handleAddToCartByBarcode();
                }
            }
        } else {
            barcodeBuffer.setLength(0);
            isScannerInput = false;
        }
    }
    
    /**
     * Process a scanned barcode
     */
    private void processBarcode(String barcode) {
        javafx.application.Platform.runLater(() -> {
            try {
                Optional<ProductBarcodeMatch> matchOpt = dbManager.findProductByAnyBarcode(barcode);
                
                if (matchOpt.isPresent()) {
                    ProductBarcodeMatch match = matchOpt.get();
                    Product p = match.getProduct();
                    if (match.isBulk()) {
                        addBoxLineToCart(p);
                        showSuccess("Added: Box of " + p.getName());
                    } else {
                        addToCart(p, 1);
                        showSuccess("Added: " + p.getName());
                    }
                    barcodeField.clear();
                } else {
                    showQuickAddDialog(barcode);
                }
            } catch (Exception e) {
                logger.error("Error processing barcode: {}", barcode, e);
                showError("Error processing barcode");
            }
        });
    }
    
    /**
     * Show Quick Add dialog for new product registration
     */
    private void showQuickAddDialog(String barcode) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/quickadd.fxml"));
            javafx.scene.Parent root = loader.load();
            
            QuickAddController controller = loader.getController();
            controller.setBarcode(barcode);
            
            Stage dialog = new Stage();
            dialog.setTitle("Quick Add Product");
            dialog.setScene(new Scene(root));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(productTable.getScene().getWindow());
            dialog.setResizable(false);
            dialog.showAndWait();
            
            Product addedProduct = controller.getAddedProduct();
            if (addedProduct != null) {
                // Only add to cart if barcode was provided (scanned unknown barcode)
                // When using Quick Add button, just refresh the table
                if (barcode != null && !barcode.isEmpty()) {
                    addToCart(addedProduct, 1);
                    showSuccess("Added: " + addedProduct.getName());
                    barcodeField.clear();
                } else {
                    showSuccess("Product added: " + addedProduct.getName());
                }
                loadProducts();
            }
            
        } catch (Exception e) {
            logger.error("Error showing quick add dialog", e);
            showError("Error opening quick add dialog");
        }
    }
    
    /**
     * Load products from database
     */
    private void loadProducts() {
        try {
            List<Product> products = dbManager.getAllProducts();
            if (products == null) {
                products = new ArrayList<>();
            }
            productList.clear();
            productList.addAll(products);
            
            // Reset search
            if (searchField != null) {
                searchField.clear();
            }
            filteredProductList.setPredicate(p -> true);
            
            logger.info("Loaded {} products", products.size());
            
        } catch (Exception e) {
            logger.error("Error loading products", e);
            productList.clear();
            filteredProductList.setPredicate(p -> false);
        }
    }
    
    /**
     * Start a new sale
     */
    private void startNewSale() {
        Optional<User> user = authService.getCurrentUser();
        if (user.isPresent()) {
            currentSale = new Sale(user.get().getId());
            currentSale.setPaymentMethod(Sale.PaymentMethod.CASH);
        } else {
            currentSale = new Sale("default");
            currentSale.setPaymentMethod(Sale.PaymentMethod.CASH);
        }
        cartList.clear();
        updateTotals();
    }
    
    /**
     * Handle search button (for explicit search trigger)
     */
    @FXML
    private void handleSearch() {
        String searchTerm = searchField.getText().trim();
        
        if (isDatabaseSearchMode) {
            performDatabaseSearch(searchTerm);
        } else {
            performInMemorySearch(searchTerm);
        }
    }
    
    /**
     * Handle keyboard navigation from search field
     * UP/DOWN transfers focus to table
     * ENTER adds selected product to cart
     */
    @FXML
    private void handleSearchKeyPress(KeyEvent event) {
        if (event.getCode() == KeyCode.UP) {
            javafx.application.Platform.runLater(() -> productTable.requestFocus());
            event.consume();
        } else if (event.getCode() == KeyCode.DOWN) {
            javafx.application.Platform.runLater(() -> {
                if (productTable.getItems().size() > 0) {
                    productTable.getSelectionModel().selectFirst();
                    productTable.requestFocus();
                }
            });
            event.consume();
        } else if (event.getCode() == KeyCode.ENTER) {
            handleSearch();
        }
    }
    
    /**
     * Double-click a search row: add as a single (unit) line using quantity from {@link #quantityField}.
     */
    private void handleProductTableRowMouseClicked(MouseEvent event, TableRow<Product> row) {
        if (event.getClickCount() != 2 || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        if (row.isEmpty()) {
            return;
        }
        handleAddToCartFromTable(row.getItem());
        event.consume();
    }
    
    private boolean productSupportsManualBoxSale(Product product) {
        if (product == null) {
            return false;
        }
        String bulk = product.getBulkBarcode();
        if (bulk == null || bulk.isBlank()) {
            return false;
        }
        return product.getPiecesPerBulk() >= 2;
    }
    
    private void updateProductTableBoxSaleActions(Product selected) {
        boolean can = productSupportsManualBoxSale(selected);
        if (contextMenuAddBoxItem != null) {
            contextMenuAddBoxItem.setDisable(!can);
            if (can && selected != null) {
                contextMenuAddBoxItem.setText("Add box — " + ellipsize(selected.getName(), 42));
            } else {
                contextMenuAddBoxItem.setText("Add Box to Cart");
            }
        }
        if (addBoxButton != null) {
            addBoxButton.setDisable(selected == null || !can);
            if (can && selected != null) {
                addBoxButton.setText("Box: " + ellipsize(selected.getName(), 20));
                addBoxButton.setTooltip(new Tooltip(
                        "Add one full case (box of \"" + selected.getName() + "\"). "
                                + "If the case price is not set yet, you will be asked once and it will be saved."));
            } else {
                addBoxButton.setText("Add Box");
                addBoxButton.setTooltip(null);
            }
        }
    }
    
    /**
     * Handle product table keyboard input
     * ENTER adds selected product to cart
     */
    @FXML
    private void handleProductTableKeyPress(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            Product selected = productTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                handleAddToCartFromTable(selected);
            }
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE) {
            javafx.application.Platform.runLater(() -> barcodeField.requestFocus());
            event.consume();
        }
    }
    
    /**
     * Add product to cart from table selection
     */
    private void handleAddToCartFromTable(Product selected) {
        if (selected == null) {
            showError("Please select a product");
            return;
        }
        
        int qty = 1;
        try {
            qty = Integer.parseInt(quantityField.getText());
            if (qty <= 0) {
                qty = 1;
            }
        } catch (NumberFormatException e) {
            qty = 1;
        }
        
        addToCart(selected, qty);
        requestBarcodeFocus();
    }
    
    /**
     * Handle Quick Add Product button - opens the quick add dialog
     */
    @FXML
    private void handleQuickAddProduct() {
        showQuickAddDialog(null);
    }
    
    /**
     * Handle add to cart by barcode
     */
    @FXML
    private void handleAddToCartByBarcode() {
        String barcode = barcodeField.getText().trim();
        if (barcode.isEmpty()) {
            return;
        }
        
        try {
            Optional<ProductBarcodeMatch> matchOpt = dbManager.findProductByAnyBarcode(barcode);
            if (matchOpt.isPresent()) {
                ProductBarcodeMatch match = matchOpt.get();
                if (match.isBulk()) {
                    addBoxLineToCart(match.getProduct());
                } else {
                    addToCart(match.getProduct(), 1);
                }
                barcodeField.clear();
            } else {
                showQuickAddDialog(barcode);
            }
        } catch (Exception e) {
            logger.error("Error adding product to cart", e);
            showError("Error adding product to cart");
        }
    }
    
    /**
     * Handle key press in barcode field - Enter key adds to cart
     */
    @FXML
    private void handleBarcodeKeyPress(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            handleAddToCartByBarcode();
        }
    }
    
    /**
     * Handle add to cart button
     */
    @FXML
    private void handleAddToCart() {
        Product selected = productTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a product");
            return;
        }

        double qty = 1.0;
        try {
            qty = Double.parseDouble(quantityField.getText());
            if (qty <= 0) {
                qty = 1.0;
            }
        } catch (NumberFormatException e) {
            qty = 1.0;
        }

        addToCart(selected, qty);
    }    
    /**
     * Context menu: add selected row as single (unit) lines.
     */
    @FXML
    private void handleContextAddSingleToCart() {
        Product selected = productTable.getSelectionModel().getSelectedItem();
        handleAddToCartFromTable(selected);
    }
    
    /**
     * Add selected product as box sale(s). Quantity field = number of boxes.
     * Also used by the Quick Add "Add Box" button and context menu.
     */
    @FXML
    private void handleAddBoxFromTable() {
        Product selected = productTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a product");
            return;
        }
        if (!productSupportsManualBoxSale(selected)) {
            showError("This product has no box barcode or is not configured for box sales.");
            return;
        }
        int per = selected.getPiecesPerBulk();
        double boxes = 1.0;
        try {
            boxes = Double.parseDouble(quantityField.getText().trim());
            if (boxes <= 0) {
                boxes = 1.0;
            }
        } catch (NumberFormatException e) {
            boxes = 1.0;
        }

        double stockToCheck = selected.getStockQuantity();
        double unitsNeeded = boxes * per;

        if (selected.getParentBarcode() != null && !selected.getParentBarcode().isEmpty()) {
            try {
                Optional<Product> parentOpt = dbManager.findProductByBarcode(selected.getParentBarcode());
                if (parentOpt.isPresent()) {
                    stockToCheck = parentOpt.get().getStockQuantity();
                    unitsNeeded = (boxes * per) * selected.getDeductionRatio();
                }
            } catch (SQLException e) {
                logger.error("Error fetching parent product stock", e);
            }
        }

        if (stockToCheck < unitsNeeded) {
            showError("Insufficient stock for " + boxes + " box(es) (need " + unitsNeeded + " units).");
            return;
        }
        
        addBoxLineToCart(selected);
        if (boxes != 1.0) {
            Optional<SaleItem> item = cartList.stream()
                .filter(i -> i.getProductId().equals(selected.getId()) && i.isBoxSale())
                .findFirst();
            if (item.isPresent()) {
                item.get().setQuantity(boxes);
                item.get().setProductName(boxes + " Boxes - " + selected.getName());
                refreshCartTotals();
                cartTable.refresh();
            }
        }
        requestBarcodeFocus();
    }
    
    /**
     * Add product to cart (unit / single lines only). Box scans use {@link #addBoxLineToCart(Product)}.
     */
    private void addToCart(Product product, double quantity) {
        double stockToCheck = product.getStockQuantity();
        Product stockOwner = product;
        double quantityToDeduct = quantity;

        if (product.getParentBarcode() != null && !product.getParentBarcode().isEmpty()) {
            try {
                Optional<Product> parentOpt = dbManager.findProductByBarcode(product.getParentBarcode());
                if (parentOpt.isPresent()) {
                    stockOwner = parentOpt.get();
                    stockToCheck = stockOwner.getStockQuantity();
                    quantityToDeduct = quantity * product.getDeductionRatio();
                }
            } catch (SQLException e) {
                logger.error("Error fetching parent product stock", e);
            }
        }

        if (stockToCheck < quantityToDeduct) {
            showError("Insufficient stock");
            return;
        }

        Optional<SaleItem> existingItem = cartList.stream()
            .filter(item -> item.getProductId().equals(product.getId()) && !item.isBoxSale())
            .findFirst();

        if (existingItem.isPresent()) {
            SaleItem item = existingItem.get();
            double newQty = item.getQuantity() + quantity;
            double newQuantityToDeduct = newQty;
            if (product.getParentBarcode() != null && !product.getParentBarcode().isEmpty()) {
                newQuantityToDeduct = newQty * product.getDeductionRatio();
            }

            if (newQuantityToDeduct > stockToCheck) {
                showError("Insufficient stock");
                return;
            }
            BigDecimal unit = pricingContext.getPrice(product);
            item.setUnitPrice(unit);
            item.setQuantity(newQty);
            cartTable.refresh();
        } else {
            BigDecimal price = pricingContext.getPrice(product);
            SaleItem newItem = new SaleItem(product, quantity, price);
            newItem.setBoxSale(false);
            cartList.add(newItem);
        }

        refreshCartTotals();
    }
    
    /**
     * Add one box sale: {@code piecesPerBulk} units at {@code bulkPrice} total for the line.
     */
    private void addBoxLineToCart(Product product) {
        if (product == null) {
            return;
        }
        String bulkBc = product.getBulkBarcode();
        if (bulkBc == null || bulkBc.isBlank()) {
            showError("This product has no box barcode configured.");
            return;
        }
        int per = product.getPiecesPerBulk();
        if (per < 2) {
            showError("This product needs at least 2 pieces per box to sell by the case.");
            return;
        }
        BigDecimal bulkPrice = product.getBulkPrice() != null ? product.getBulkPrice() : BigDecimal.ZERO;
        if (bulkPrice.compareTo(BigDecimal.ZERO) <= 0) {
            if (!promptAndPersistBoxSellingPrice(product)) {
                return;
            }
            bulkPrice = product.getBulkPrice() != null ? product.getBulkPrice() : BigDecimal.ZERO;
            if (bulkPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }
        }
        if (product.getStockQuantity() < per) {
            showError("Insufficient stock");
            return;
        }
        
        Optional<SaleItem> existingItem = cartList.stream()
            .filter(item -> item.getProductId().equals(product.getId()) && item.isBoxSale())
            .findFirst();
        
        if (existingItem.isPresent()) {
            SaleItem item = existingItem.get();
            double newQty = item.getQuantity() + 1.0;
            // Stock check: newQty * piecesPerBulk
            if ((newQty * per) > product.getStockQuantity()) {
                showError("Insufficient stock");
                return;
            }
            item.setQuantity(newQty);
            item.setProductName(newQty + " Boxes - " + product.getName());
            cartTable.refresh();
        } else {
            SaleItem newItem = new SaleItem();
            newItem.setProductId(product.getId());
            newItem.setProductName("1.0 Box of " + product.getName());
            newItem.setProductBarcode(bulkBc);
            newItem.setUnitPrice(bulkPrice);
            newItem.setQuantity(1.0);
            newItem.setProduct(product);
            newItem.setBoxSale(true);
            cartList.add(newItem);
        }
        
        refreshCartTotals();
    }
    
    /**
     * Ask cashier for one-case selling price, save on the product, and refresh the in-memory catalog.
     */
    private boolean promptAndPersistBoxSellingPrice(Product product) {
        try {
            Product p = dbManager.findProductById(product.getId()).orElse(product);
            BigDecimal current = p.getBulkPrice() != null ? p.getBulkPrice() : BigDecimal.ZERO;
            if (current.compareTo(BigDecimal.ZERO) > 0) {
                product.setBulkPrice(current);
                return true;
            }
            int per = p.getPiecesPerBulk();
            if (per < 2) {
                showError("Configure at least 2 pieces per box on this product before selling by the case.");
                return false;
            }
            BigDecimal suggest = BigDecimal.ZERO;
            if (p.getRetailPrice() != null) {
                suggest = p.getRetailPrice().multiply(BigDecimal.valueOf(per));
            }
            TextInputDialog dlg = new TextInputDialog(
                    suggest.compareTo(BigDecimal.ZERO) > 0 ? suggest.toPlainString() : "");
            if (productTable != null && productTable.getScene() != null && productTable.getScene().getWindow() != null) {
                dlg.initOwner(productTable.getScene().getWindow());
            }
            dlg.setTitle("Box selling price");
            dlg.setHeaderText("Box of \"" + p.getName() + "\"\n"
                    + "Enter the selling price for one full case (KSh). It is saved on this product for next time.");
            dlg.setContentText("Price for one box:");
            Optional<String> result = dlg.showAndWait();
            if (result.isEmpty()) {
                return false;
            }
            String raw = result.get().trim();
            if (raw.isEmpty()) {
                showError("Enter a price for the box.");
                return false;
            }
            BigDecimal price = new BigDecimal(raw);
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                showError("Box price must be greater than zero.");
                return false;
            }
            p.setBulkPrice(price);
            dbManager.updateProduct(p);
            Optional<Product> fresh = dbManager.findProductById(p.getId());
            if (fresh.isPresent()) {
                syncProductInMemoryLists(fresh.get());
                product.setBulkPrice(fresh.get().getBulkPrice());
            } else {
                product.setBulkPrice(price);
            }
            return true;
        } catch (NumberFormatException e) {
            showError("Enter a valid number for the box price.");
            return false;
        } catch (SQLException e) {
            logger.error("Could not save box selling price", e);
            showError("Could not save box price: " + e.getMessage());
            return false;
        }
    }
    
    private void syncProductInMemoryLists(Product updated) {
        if (updated == null || productList == null) {
            return;
        }
        for (int i = 0; i < productList.size(); i++) {
            if (productList.get(i).getId().equals(updated.getId())) {
                productList.set(i, updated);
                return;
            }
        }
    }
    
    private static String ellipsize(String name, int maxChars) {
        if (name == null) {
            return "";
        }
        if (name.length() <= maxChars) {
            return name;
        }
        if (maxChars <= 3) {
            return "...";
        }
        return name.substring(0, maxChars - 3) + "...";
    }
    
    /**
     * Handle remove from cart
     */
    @FXML
    private void handleRemoveFromCart() {
        SaleItem selected = cartTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            String itemName = selected.getProductName();
            cartList.remove(selected);
            refreshCartTotals();
            
            Optional<User> currentUser = authService.getCurrentUser();
            if (currentUser.isPresent()) {
                logActivity(ActivityLog.ActionType.REMOVE_ITEM, 
                    itemName, 
                    "Qty: " + selected.getQuantity() + ", Price: " + formatCurrency(selected.getTotalPrice()));
            }
            
            showSuccess("Item removed: " + itemName);
        }
    }
    
    /**
     * Handle clear cart
     */
    @FXML
    private void handleClearCart() {
        int itemCount = cartList.size();
        cartList.clear();
        refreshCartTotals();
        
        Optional<User> currentUser = authService.getCurrentUser();
        if (currentUser.isPresent()) {
            logActivity(ActivityLog.ActionType.CLEAR_CART, 
                itemCount + " items", 
                "Total value: " + formatCurrency(currentSale.getSubtotal()));
        }
        
        showSuccess("Cart cleared (" + itemCount + " items)");
        requestBarcodeFocus();
    }
    
    private void logActivity(ActivityLog.ActionType actionType, String targetDescription, String details) {
        Optional<User> currentUser = authService.getCurrentUser();
        if (currentUser.isPresent()) {
            try {
                ActivityLog log = new ActivityLog(
                    currentUser.get().getId(),
                    currentUser.get().getFullName(),
                    actionType,
                    targetDescription,
                    details
                );
                dbManager.insertActivityLog(log);
            } catch (Exception e) {
                logger.error("Failed to log activity: {}", actionType, e);
            }
        }
    }
    
    /**
     * Handle hold cart - saves current sale for later
     */
    @FXML
    private void handleHoldCart() {
        if (cartList.isEmpty()) {
            showError("Cart is empty, nothing to hold");
            return;
        }
        
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Hold Cart");
        dialog.setHeaderText("Enter Cart ID (e.g., customer name or number)");
        dialog.setContentText("Cart ID:");
        
        Optional<String> result = dialog.showAndWait();
        
        String holdId = result.filter(s -> !s.trim().isEmpty()).orElse(null);
        
        if (holdId == null || holdId.trim().isEmpty()) {
            showError("Cart ID is required");
            return;
        }
        
        holdId = holdId.trim();
        
        if (heldCarts.containsKey(holdId)) {
            showError("Cart ID '" + holdId + "' already exists. Use a different ID.");
            return;
        }
        
        Sale heldSale = new Sale(currentSale.getUserId());
        heldSale.setSubtotal(currentSale.getSubtotal());
        heldSale.setTotal(currentSale.getTotal());
        heldSale.setTaxAmount(currentSale.getTaxAmount());
        heldSale.setDiscountAmount(currentSale.getDiscountAmount());
        
        for (SaleItem item : cartList) {
            SaleItem newItem = new SaleItem();
            newItem.setProductId(item.getProductId());
            newItem.setProductName(item.getProductName());
            newItem.setProductBarcode(item.getProductBarcode());
            newItem.setQuantity(item.getQuantity());
            newItem.setUnitPrice(item.getUnitPrice());
            newItem.setTotalPrice(item.getTotalPrice());
            newItem.setBoxSale(item.isBoxSale());
            newItem.setProduct(item.getProduct());
            heldSale.addItem(newItem);
        }
        
        heldCarts.put(holdId, heldSale);
        
        cartList.clear();
        refreshCartTotals();
        
        showSuccess("Cart held as '" + holdId + "'. Use Recall Cart to restore.");
    }
    
    /**
     * Handle recall cart - restores a held sale
     */
    @FXML
    private void handleRecallCart() {
        if (heldCarts.isEmpty()) {
            showError("No held carts available");
            return;
        }
        
        if (!cartList.isEmpty()) {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Recall Cart");
            confirmAlert.setHeaderText("Current cart will be replaced");
            confirmAlert.setContentText("Do you want to replace the current cart with a held cart?");
            
            Optional<ButtonType> result = confirmAlert.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }
        }
        
        if (heldCarts.size() == 1) {
            String holdId = heldCarts.keySet().iterator().next();
            restoreCart(holdId, heldCarts.get(holdId));
        } else {
            ChoiceDialog<String> dialog = new ChoiceDialog<>();
            dialog.setTitle("Recall Cart");
            dialog.setHeaderText("Select a cart to restore");
            dialog.setContentText("Available carts:");
            
            dialog.getItems().addAll(heldCarts.keySet());
            dialog.setSelectedItem(heldCarts.keySet().iterator().next());
            
            Optional<String> result = dialog.showAndWait();
            
            result.ifPresent(holdId -> {
                Sale heldSale = heldCarts.get(holdId);
                if (heldSale != null) {
                    restoreCart(holdId, heldSale);
                }
            });
        }
    }
    
    /**
     * Restore a held cart
     */
    private void showHoldCartSelectionDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Recall Cart");
        dialog.setHeaderText("Select a held cart to restore");
        
        ButtonType restoreButton = new ButtonType("Restore", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(restoreButton, ButtonType.CANCEL);
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        
        ToggleGroup toggleGroup = new ToggleGroup();
        
        for (Map.Entry<String, Sale> entry : heldCarts.entrySet()) {
            Sale sale = entry.getValue();
            int itemCount = sale.getItems().size();
            BigDecimal total = sale.getTotal();
            
            RadioButton rb = new RadioButton(entry.getKey() + " - " + itemCount + " items - " + formatCurrency(total));
            rb.setToggleGroup(toggleGroup);
            rb.setUserData(entry.getKey());
            content.getChildren().add(rb);
        }
        
        dialog.getDialogPane().setContent(content);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == restoreButton) {
                RadioButton selected = (RadioButton) toggleGroup.getSelectedToggle();
                if (selected != null) {
                    return (String) selected.getUserData();
                }
            }
            return null;
        });
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(holdId -> {
            Sale heldSale = heldCarts.get(holdId);
            if (heldSale != null) {
                restoreCart(holdId, heldSale);
            }
        });
    }
    
    /**
     * Restore a held cart
     */
    private void restoreCart(String holdId, Sale heldSale) {
        cartList.clear();
        
        for (SaleItem item : heldSale.getItems()) {
            cartList.add(item);
        }
        
        currentSale.setSubtotal(heldSale.getSubtotal());
        currentSale.setTotal(heldSale.getTotal());
        currentSale.setTaxAmount(heldSale.getTaxAmount());
        currentSale.setDiscountAmount(heldSale.getDiscountAmount());
        
        refreshCartTotals();
        heldCarts.remove(holdId);
        
        showSuccess("Cart restored from " + holdId);
    }
    
    /**
     * Update cart totals
     */
    private void refreshCartTotals() {
        BigDecimal subtotal = cartList.stream()
            .map(SaleItem::getTotalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        currentSale.setSubtotal(subtotal);
        currentSale.setTotal(subtotal);

        updateTotals();
    }

    /**
     * Update display totals
     */
    private void updateTotals() {
        subtotalLabel.setText(BrandingConstants.CURRENCY_SYMBOL + String.format("%.2f", currentSale.getSubtotal()));
        taxLabel.setText(BrandingConstants.CURRENCY_SYMBOL + String.format("%.2f", currentSale.getTaxAmount()));
        discountLabel.setText(BrandingConstants.CURRENCY_SYMBOL + String.format("%.2f", currentSale.getDiscountAmount()));
        totalLabel.setText(BrandingConstants.CURRENCY_SYMBOL + String.format("%.2f", currentSale.getTotal()));

        BigDecimal paid = currentSale.getAmountPaid();
        if (paid == null || paid.compareTo(BigDecimal.ZERO) == 0) {
            amountPaidField.setText(BrandingConstants.CURRENCY_SYMBOL + "0.00");
            changeLabel.setText(BrandingConstants.CURRENCY_SYMBOL + "0.00");
        } else {
            amountPaidField.setText(BrandingConstants.CURRENCY_SYMBOL + String.format("%.2f", paid));
            changeLabel.setText(BrandingConstants.CURRENCY_SYMBOL + String.format("%.2f", currentSale.getChangeGiven()));
        }
    }    
    /**
     * Handle checkout
     */
    @FXML
    private void handleCheckout() {
        if (cartList.isEmpty()) {
            showError("Cart is empty");
            return;
        }
        
        try {
            // First, show checkout dialog to get payment details
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/checkout.fxml"));
            javafx.scene.Parent root = loader.load();
            
            CheckoutController controller = loader.getController();
            controller.setSale(currentSale);
            
            Stage dialog = new Stage();
            dialog.setTitle("Checkout");
            dialog.setScene(new Scene(root));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(productTable.getScene().getWindow());
            dialog.setResizable(false);
            dialog.showAndWait();
            
            // Check if user confirmed the sale
            if (!controller.isConfirmed()) {
                return; // User cancelled
            }
            
            // Complete the sale (checkout dialog already set payment status / customer for credit)
            currentSale.getItems().addAll(cartList);
            currentSale.recalculateTotal();
            if (controller.getPaymentMethod() != null) {
                currentSale.setPaymentMethod(controller.getPaymentMethod());
            }
            if (controller.getAmountPaid() != null) {
                currentSale.setAmountPaid(controller.getAmountPaid());
            }
            if (controller.getChange() != null) {
                currentSale.setChangeGiven(controller.getChange());
            }
            if (controller.isSplitPayment()) {
                currentSale.setCashAmount(controller.getCashAmount());
                currentSale.setMpesaAmount(controller.getMpesaAmount());
                currentSale.setSecondaryPaymentMethod(Sale.PaymentMethod.MOBILE_MONEY);
            }
            currentSale.complete();
            
            dbManager.insertSale(currentSale);
            printerService.printTransaction(currentSale);
            
            if (!controller.isCreditSale()) {
                printerService.openCashDrawer();
            }
            
            showSuccess("Sale completed! Total: " + formatCurrency(currentSale.getTotal()));
            startNewSale();
            requestBarcodeFocus();
            
        } catch (Exception e) {
            logger.error("Error during checkout", e);
            showError("Error during checkout: " + e.getMessage());
        }
    }
    
    /**
     * Handle print receipt
     */
    @FXML
    private void handlePrintReceipt() {
        if (currentSale == null || cartList.isEmpty()) {
            showError("No sale to print");
            return;
        }
        
        boolean success = printerService.printTransaction(currentSale);
        if (success) {
            showSuccess("Receipt printed");
        } else {
            showError("Failed to print receipt");
        }
    }
    
    /**
     * Handle open cash drawer
     */
    @FXML
    private void handleOpenDrawer() {
        if (!checkManagerAuthorization("Open Cash Drawer")) {
            return;
        }
        
        boolean success = printerService.openCashDrawer();
        if (success) {
            Optional<User> currentUser = authService.getCurrentUser();
            if (currentUser.isPresent()) {
                logActivity(ActivityLog.ActionType.OPEN_DRAWER, "Cash Drawer", "Opened without sale");
            }
            showSuccess("Cash drawer opened");
        } else {
            showError("Failed to open cash drawer");
        }
    }
    
    /**
     * Handle print label
     */
    @FXML
    private void handlePrintLabel() {
        Product selected = productTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a product");
            return;
        }
        
        boolean success = printerService.printShelfTag(selected);
        if (success) {
            showSuccess("Label printed");
        } else {
            showError("Failed to print label");
        }
    }
    
    /**
     * Handle logout - completely recreate login screen fresh
     * IMPORTANT: authService.logout() is called AFTER UI operations to ensure fail-safe behavior.
     * If stage creation fails, the user stays logged in.
     */
    @FXML
    private void handleLogout() {
        try {
            // 1. Get the existing stage (Do NOT close it)
            Stage stage = (Stage) backofficeButton.getScene().getWindow();

            // 2. Load the login screen
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();

            // 3. CRITICAL: Pass the stage back to the new LoginController!
            LoginController loginController = loader.getController();
            loginController.setPrimaryStage(stage);
            loginController.clearFields();

            // 4. Swap the scene on the existing stage (NO new Stage())
            stage.setScene(new Scene(root));
            stage.setTitle("Vegas Supermarket POS");
            stage.setWidth(420);
            stage.setHeight(520);
            stage.setResizable(false);
            stage.centerOnScreen();
            
            // 5. Clear auth state
            authService.logout();
            
            logger.info("User logged out - scene swapped on existing stage");
        } catch (Exception e) {
            logger.error("Error returning to login screen", e);
            showError("Error returning to login: " + e.getMessage());
        }
    }
    
    /**
     * Handle refresh
     */
    @FXML
    private void handleRefresh() {
        loadProducts();
        showSuccess("Products refreshed");
    }
    
    /**
     * Handle backoffice button — return to the same tabbed admin screen admins see after login.
     */
    @FXML
    private void handleBackoffice() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/admin_dashboard.fxml"));
            Parent root = loader.load();
            AdminDashboardController adminDashboardController = loader.getController();
            Stage stage = (Stage) backofficeButton.getScene().getWindow();
            adminDashboardController.setPrimaryStage(stage);
            adminDashboardController.setCurrentUser(authService.getCurrentUser().orElse(null));
            stage.setScene(new Scene(root));
            stage.setTitle("POS System - Admin Dashboard");
            stage.setMaximized(true);
            logger.info("Navigated to Admin Dashboard");
        } catch (Exception e) {
            logger.error("Error loading admin dashboard", e);
            showError("Error loading backoffice: " + e.getMessage());
        }
    }
    
    /**
     * Handle profile button - open user profile dialog
     */
    @FXML
    private void handleProfile() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/user_profile.fxml"));
            Parent root = loader.load();
            
            UserProfileController profileController = loader.getController();
            
            Stage dialog = new Stage();
            dialog.setTitle("My Profile");
            dialog.setScene(new Scene(root));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(backofficeButton.getScene().getWindow());
            dialog.setResizable(false);
            dialog.showAndWait();
            
        } catch (Exception e) {
            logger.error("Error loading profile", e);
            showError("Error loading profile: " + e.getMessage());
        }
    }
    
    /**
     * Handle wholesale toggle
     */
    @FXML
    private void handleWholesaleToggle() {
        if (wholesaleToggle.isSelected()) {
            pricingContext.useWholesalePricing();
        } else {
            pricingContext.useRetailPricing();
        }
        updatePriceMode();
        showSuccess("Price mode: " + pricingContext.getCurrentStrategyName());
    }
    
    private void updatePriceMode() {
        if (pricingContext.isWholesaleMode()) {
            wholesaleToggle.setText("📦 Mode: WHOLESALE (Click for Retail)");
            wholesaleToggle.setStyle("-fx-background-color: #f59e0b; -fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; -fx-cursor: hand;");
            colRetailPrice.setStyle("");
            colWholesalePrice.setStyle("-fx-background-color: #90EE90;");
        } else {
            wholesaleToggle.setText("🛒 Mode: RETAIL (Click for Wholesale)");
            wholesaleToggle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-cursor: hand;");
            colRetailPrice.setStyle("-fx-background-color: #90EE90;");
            colWholesalePrice.setStyle("");
        }
    }
    /**
     * Update status display
     */
    private void updateStatus() {
        SyncService.SyncStatus status = syncService.getStatus();
        String statusText = "Offline";
        if (status.isOnline()) {
            statusText = "Online - " + status.getUnsyncedCount() + " pending";
        }
        statusLabel.setText("Status: " + statusText);
    }
    
    /**
     * Increase cart line quantity: one unit for singles, one box for box lines.
     */
    private void incrementCartLineQuantity(SaleItem item) {
        Product p = item.getProduct();
        if (p == null) {
            showError("Cannot adjust this line. Remove it and add the product again.");
            return;
        }
        if (item.isBoxSale()) {
            double newQty = item.getQuantity() + 1.0;
            int per = Math.max(1, p.getPiecesPerBulk());
            if ((newQty * per) > p.getStockQuantity()) {
                showError("Insufficient stock");
                return;
            }
            item.setQuantity(newQty);
            item.setProductName(newQty + " Boxes - " + p.getName());
        } else {
            double newQty = item.getQuantity() + 1.0;
            if (newQty > p.getStockQuantity()) {
                showError("Insufficient stock");
                return;
            }
            BigDecimal unit = pricingContext.getPrice(p);
            item.setUnitPrice(unit);
            item.setQuantity(newQty);
        }
        cartTable.refresh();
        refreshCartTotals();
    }
    
    /**
     * Decrease cart line quantity: one unit or one box; remove row when line would be empty.
     */
    private void decrementCartLineQuantity(SaleItem item, TableView<SaleItem> table) {
        Product p = item.getProduct();
        if (p == null) {
            table.getItems().remove(item);
            refreshCartTotals();
            return;
        }
        if (item.isBoxSale()) {
            if (item.getQuantity() <= 1.0) {
                table.getItems().remove(item);
            } else {
                double newQty = item.getQuantity() - 1.0;
                item.setQuantity(newQty);
                item.setProductName((newQty <= 1.0 ? "1.0 Box" : newQty + " Boxes") + " - " + p.getName());
            }
        } else {
            if (item.getQuantity() > 1.0) {
                double newQty = item.getQuantity() - 1.0;
                BigDecimal unit = pricingContext.getPrice(p);
                item.setUnitPrice(unit);
                item.setQuantity(newQty);
            } else {
                table.getItems().remove(item);
            }
        }
        cartTable.refresh();
        refreshCartTotals();
    }
    
    /**
     * Format currency
     */
    private String formatCurrency(BigDecimal amount) {
        return BrandingConstants.CURRENCY_SYMBOL + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    
    /**
     * Show error message
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Show success message
     */
    private void showSuccess(String message) {
        logger.info(message);
    }
    
    /**
     * Check if manager authorization is required
     * If current user is Attendant, prompt for manager authentication
     * @param actionName The name of the action being performed
     * @return true if authorization is granted or not required, false otherwise
     */
    private boolean checkManagerAuthorization(String actionName) {
        Optional<User> currentUser = authService.getCurrentUser();
        
        if (currentUser.isPresent() && currentUser.get().getRole() == User.Role.ATTENDANT) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/manager_auth.fxml"));
                javafx.scene.Parent root = loader.load();
                
                ManagerAuthController authController = loader.getController();
                
                Stage dialog = new Stage();
                dialog.setTitle("Manager Authorization");
                dialog.setScene(new Scene(root));
                dialog.initModality(Modality.APPLICATION_MODAL);
                dialog.initOwner(productTable.getScene().getWindow());
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
        
        return true;
    }
    
    @FXML
    private void handleOpenLedger() {
        try {
            java.net.URL resource = getClass().getResource("/fxml/customer_ledger.fxml");
            if (resource == null) {
                logger.error("customer_ledger.fxml not found in resources!");
                showError("System Error: Cannot find the Customer Ledger UI file. Please rebuild the application.");
                return;
            }
            
            FXMLLoader loader = new FXMLLoader(resource);
            javafx.scene.Parent root = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle("Customer Ledger - Collect Debts");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setResizable(false);
            stage.showAndWait();
            
            // Refresh products in case some were updated (though ledger doesn't update products)
            loadProducts();
        } catch (Exception e) {
            logger.error("Failed to load Customer Ledger UI", e);
            showError("UI Error: Failed to open ledger. " + e.getMessage());
        }
    }

    /**
     * Handle X-Report (print current shift summary)
     */
    @FXML
    private void handleXReport() {
        if (!checkManagerAuthorization("X-Report")) {
            return;
        }
        
        ShiftController shiftController = ShiftController.getInstance();
        shiftController.printXReport();
    }
    
    /**
     * Handle Close Register
     */
    @FXML
    private void handleCloseRegister() {
        if (!checkManagerAuthorization("Close Register")) {
            return;
        }
        
        ShiftController shiftController = ShiftController.getInstance();
        boolean closed = shiftController.promptCloseRegister();
        
        if (closed) {
            showSuccess("Register closed successfully");
        }
    }
}
