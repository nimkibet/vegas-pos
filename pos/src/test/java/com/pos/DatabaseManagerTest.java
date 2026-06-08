package com.pos;

import com.pos.database.DatabaseManager;
import com.pos.entity.*;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Database Manager Test
 * Tests the database operations for the POS system.
 */
public class DatabaseManagerTest {
    
    private static DatabaseManager dbManager;
    private static String uniqueTestBarcode;
    
    @BeforeAll
    public static void setup() throws Exception {
        dbManager = DatabaseManager.getInstance();
        dbManager.initialize();
        // Generate unique barcode for each test run to avoid conflicts
        uniqueTestBarcode = "TEST" + UUID.randomUUID().toString().replace("-", "").substring(0, 13);
    }
    
    @Test
    public void testInitialize() {
        assertNotNull(dbManager);
    }
    
    @Test
    public void testUserOperations() throws Exception {
        // Test finding user by username
        Optional<User> adminOpt = dbManager.findUserByUsername("admin");
        assertTrue(adminOpt.isPresent(), "Admin user should exist");
        
        // Test finding user by ID
        User admin = adminOpt.get();
        Optional<User> byId = dbManager.findUserById(admin.getId());
        assertTrue(byId.isPresent(), "Should find user by ID");
        
        // Test getting all users
        List<User> users = dbManager.getAllUsers();
        assertTrue(users.size() >= 2, "Should have at least 2 default users");
    }
    
    @Test
    public void testProductOperations() throws Exception {
        // Create a test product with unique barcode
        Product product = new Product(
            "TEST-" + UUID.randomUUID().toString(),
            uniqueTestBarcode,
            "Test Product",
            "A test product for unit testing",
            "Test Category",
            new BigDecimal("100.00"),
            new BigDecimal("80.00"),
            50,
            10,
            null,
            "Test Supplier",
            "APPROVED",
            true,
            false,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
        
        // Insert product
        dbManager.insertProduct(product);
        
        // Find by ID
        Optional<Product> found = dbManager.findProductById(product.getId());
        assertTrue(found.isPresent(), "Product should be found after insert");
        
        // Find by barcode
        Optional<Product> byBarcode = dbManager.findProductByBarcode(uniqueTestBarcode);
        assertTrue(byBarcode.isPresent(), "Product should be found by barcode");
        
        // Update product
        product.setStockQuantity(75);
        dbManager.updateProduct(product);
        
        // Verify update
        Optional<Product> updated = dbManager.findProductById(product.getId());
        assertTrue(updated.isPresent());
        assertEquals(75, updated.get().getStockQuantity());
    }
    
    @Test
    public void testSaleOperations() throws Exception {
        // Get a user for sale
        Optional<User> userOpt = dbManager.findUserByUsername("admin");
        assertTrue(userOpt.isPresent());
        User user = userOpt.get();
        
        // Get a product
        List<Product> products = dbManager.getAllProducts();
        assertTrue(!products.isEmpty(), "Should have products for sale test");
        Product product = products.get(0);
        
        // Create a sale
        Sale sale = new Sale(user.getId());
        sale.setPaymentMethod(Sale.PaymentMethod.CASH);
        sale.setAmountPaid(new BigDecimal("110.00"));
        sale.setChangeGiven(new BigDecimal("10.00"));
        
        // Add sale item
        SaleItem item = new SaleItem(product, 1, product.getRetailPrice());
        sale.addItem(item);
        
        sale.recalculateTotal();
        sale.setStatus(Sale.Status.COMPLETED);
        
        // Insert sale
        dbManager.insertSale(sale);
        
        // Find sale
        Optional<Sale> found = dbManager.findSaleById(sale.getId());
        assertTrue(found.isPresent(), "Sale should be found after insert");
        
        // Verify items
        List<SaleItem> items = dbManager.getSaleItems(sale.getId());
        assertEquals(1, items.size(), "Sale should have 1 item");
    }
    
    @Test
    public void testActivityLogOperations() throws Exception {
        // Get current user
        Optional<User> userOpt = dbManager.findUserByUsername("admin");
        assertTrue(userOpt.isPresent());
        User user = userOpt.get();
        
        // Create activity log
        ActivityLog log = new ActivityLog(
            user.getId(),
            user.getFullName(),
            ActivityLog.ActionType.LOGIN,
            "Test Login",
            "Test details"
        );
        
        // Insert log
        dbManager.insertActivityLog(log);
        
        // Get recent logs
        List<ActivityLog> logs = dbManager.getRecentActivityLogs(10);
        assertTrue(logs.size() > 0, "Should have activity logs");
    }
    
    @Test
    public void testLowStockProducts() throws Exception {
        List<Product> lowStock = dbManager.getLowStockProducts();
        assertNotNull(lowStock);
    }
    
    @Test
    public void testGetTodaySales() throws Exception {
        List<Sale> sales = dbManager.getTodaySales();
        assertNotNull(sales);
    }

    @Test
    public void testSupplierStockInUpdatesInventory() throws Exception {
        String barcode = "STKIN" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Product product = new Product(
            UUID.randomUUID().toString(),
            barcode,
            "Stock-In Test Product",
            "desc",
            "Cat",
            new BigDecimal("10.00"),
            new BigDecimal("8.00"),
            20,
            1,
            null,
            "Test Supplier Co",
            "APPROVED",
            true,
            false,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
        dbManager.insertProduct(product);

        SupplierTransaction trans = new SupplierTransaction();
        trans.setId(UUID.randomUUID().toString());
        trans.setSupplierName("Test Supplier Co");
        trans.setStatus(SupplierTransaction.STATUS_PAID);

        SupplierTransactionItem line = new SupplierTransactionItem();
        line.setProductId(product.getId());
        line.setQuantityReceived(7);
        line.setBuyingPrice(new BigDecimal("5.50"));

        dbManager.processStockIn(trans, List.of(line), null);

        Optional<Product> after = dbManager.findProductById(product.getId());
        assertTrue(after.isPresent());
        assertEquals(27, after.get().getStockQuantity());

        List<SupplierTransaction> txs = dbManager.getAllSupplierTransactions();
        assertTrue(txs.stream().anyMatch(t -> t.getId().equals(trans.getId())));

        SupplierTransaction credit = new SupplierTransaction();
        credit.setId(UUID.randomUUID().toString());
        credit.setSupplierName("Creditor");
        credit.setStatus(SupplierTransaction.STATUS_CREDIT);
        SupplierTransactionItem line2 = new SupplierTransactionItem();
        line2.setProductId(product.getId());
        line2.setQuantityReceived(1);
        line2.setBuyingPrice(BigDecimal.ONE);
        dbManager.processStockIn(credit, List.of(line2), null);
        dbManager.clearSupplierPayment(credit.getId());
        List<SupplierTransaction> txs2 = dbManager.getAllSupplierTransactions();
        Optional<SupplierTransaction> creditRow = txs2.stream().filter(t -> t.getId().equals(credit.getId())).findFirst();
        assertTrue(creditRow.isPresent());
        assertEquals(SupplierTransaction.STATUS_PAID, creditRow.get().getStatus());
    }
}