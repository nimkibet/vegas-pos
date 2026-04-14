package com.pos.controller;

import com.pos.database.DatabaseManager;
import com.pos.entity.Product;
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
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.Callback;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private TableColumn<Product, String> colBarcode;
    @FXML
    private TableColumn<Product, String> colProductName;
    @FXML
    private TableColumn<Product, String> colRetailPrice;
    @FXML
    private TableColumn<Product, String> colWholesalePrice;
    @FXML
    private TableColumn<Product, Integer> colStock;
    
    // FXML components - Cart Table
    @FXML
    private TableView<SaleItem> cartTable;
    @FXML
    private TableColumn<SaleItem, String> colItemName;
    @FXML
    private TableColumn<SaleItem, Integer> colQuantity;
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
    private Label priceModeLabel;
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
                formatCurrency(cellData.getValue().getRetailPrice())));
        colWholesalePrice.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(
                formatCurrency(cellData.getValue().getWholesalePrice())));
        colStock.setCellValueFactory(new PropertyValueFactory<>("stockQuantity"));
        
        colStock.setCellFactory(column -> new TableCell<Product, Integer>() {
            @Override
            protected void updateItem(Integer stock, boolean empty) {
                super.updateItem(stock, empty);
                if (empty || stock == null) {
                    setText("");
                    setStyle("");
                } else {
                    setText(String.valueOf(stock));
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
        colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colUnitPrice.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(
                formatCurrency(cellData.getValue().getUnitPrice())));
        colTotalPrice.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(
                formatCurrency(cellData.getValue().getTotalPrice())));
        
        // Use FilteredList for the table
        productTable.setItems(filteredProductList);
        cartTable.setItems(cartList);
        
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
            predicate = p -> p.getName().toLowerCase().contains(lowerSearchTerm);
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
                Optional<Product> productOpt = dbManager.findProductByBarcode(barcode);
                
                if (productOpt.isPresent()) {
                    addToCart(productOpt.get(), 1);
                    showSuccess("Added: " + productOpt.get().getName());
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
            Optional<Product> productOpt = dbManager.findProductByBarcode(barcode);
            if (productOpt.isPresent()) {
                addToCart(productOpt.get(), 1);
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
    }
    
    /**
     * Add product to cart
     */
    private void addToCart(Product product, int quantity) {
        if (product.getStockQuantity() < quantity) {
            showError("Insufficient stock");
            return;
        }
        
        Optional<SaleItem> existingItem = cartList.stream()
            .filter(item -> item.getProductId().equals(product.getId()))
            .findFirst();
        
        if (existingItem.isPresent()) {
            SaleItem item = existingItem.get();
            int newQty = item.getQuantity() + quantity;
            if (newQty > product.getStockQuantity()) {
                showError("Insufficient stock");
                return;
            }
            item.setQuantity(newQty);
            item.recalculateTotal();
            cartTable.refresh();
        } else {
            BigDecimal price = pricingContext.getPrice(product);
            SaleItem newItem = new SaleItem(product, quantity, price);
            cartList.add(newItem);
        }
        
        updateCartTotals();
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
            updateCartTotals();
            
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
        updateCartTotals();
        
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
            heldSale.addItem(newItem);
        }
        
        heldCarts.put(holdId, heldSale);
        
        cartList.clear();
        updateCartTotals();
        
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
        
        updateCartTotals();
        heldCarts.remove(holdId);
        
        showSuccess("Cart restored from " + holdId);
    }
    
    /**
     * Update cart totals
     */
    private void updateCartTotals() {
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
        subtotalLabel.setText(formatCurrency(currentSale.getSubtotal()));
        taxLabel.setText(formatCurrency(currentSale.getTaxAmount()));
        discountLabel.setText(formatCurrency(currentSale.getDiscountAmount()));
        totalLabel.setText(formatCurrency(currentSale.getTotal()));
        
        BigDecimal paid = currentSale.getAmountPaid();
        if (paid == null || paid.compareTo(BigDecimal.ZERO) == 0) {
            amountPaidField.setText(BrandingConstants.CURRENCY_SYMBOL + "0.00");
            changeLabel.setText(BrandingConstants.CURRENCY_SYMBOL + "0.00");
        } else {
            amountPaidField.setText(formatCurrency(paid));
            changeLabel.setText(formatCurrency(currentSale.getChangeGiven()));
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
            
            // Complete the sale
            currentSale.getItems().addAll(cartList);
            currentSale.recalculateTotal();
            currentSale.setPaymentMethod(controller.getPaymentMethod());
            currentSale.setAmountPaid(controller.getAmountPaid());
            currentSale.setChangeGiven(controller.getChange());
            currentSale.complete();
            
            dbManager.insertSale(currentSale);
            printerService.printTransaction(currentSale);
            
            printerService.openCashDrawer();
            
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
     * Handle backoffice button
     */
    @FXML
    private void handleBackoffice() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/admin_dashboard.fxml"));
            Parent root = loader.load();
            
            Stage stage = (Stage) backofficeButton.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Backoffice Dashboard");
            stage.setMaximized(true);
            
            logger.info("Navigated to Backoffice Dashboard");
            
        } catch (Exception e) {
            logger.error("Error loading backoffice dashboard", e);
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
    
    /**
     * Update price mode display
     */
    private void updatePriceMode() {
        priceModeLabel.setText("Mode: " + pricingContext.getCurrentStrategyName());
        
        if (pricingContext.isWholesaleMode()) {
            colRetailPrice.setStyle("");
            colWholesalePrice.setStyle("-fx-background-color: #90EE90;");
        } else {
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
