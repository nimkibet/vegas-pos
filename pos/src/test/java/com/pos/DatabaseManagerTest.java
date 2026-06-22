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
        DatabaseManager.resetInstanceForTest("test_pos_system.db");
        dbManager = DatabaseManager.getInstance();
        dbManager.initialize();
        // Generate unique barcode for each test run to avoid conflicts
        uniqueTestBarcode = "TEST" + UUID.randomUUID().toString().replace("-", "").substring(0, 13);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        dbManager.close();
        // Force garbage collection to ensure SQLite driver releases file locks before deletion
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {}
        
        String userHome = System.getProperty("user.home");
        String testDbPath = userHome + java.io.File.separator + "AppData" + java.io.File.separator + 
                            "VegasSupermarket" + java.io.File.separator + "test_pos_system.db";
        java.io.File file = new java.io.File(testDbPath);
        if (file.exists()) {
            file.delete();
        }
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
        
        // Create a test product for sale
        Product product = new Product(
            "TEST-SALE-" + UUID.randomUUID().toString(),
            "SALEBAR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
            "Test Sale Product",
            "A test product for sale unit testing",
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
        dbManager.insertProduct(product);
        
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

    @Test
    public void testFindBaseRetailItem() throws Exception {
        // Create a 3-tier hierarchy: Box -> Packet -> Piece
        String boxBar = "BOX" + UUID.randomUUID().toString().substring(0, 8);
        String packBar = "PACK" + UUID.randomUUID().toString().substring(0, 8);
        String pieceBar = "PIECE" + UUID.randomUUID().toString().substring(0, 8);

        Product box = new Product();
        box.setBarcode(boxBar);
        box.setName("Box Name");
        box.setRetailPrice(new BigDecimal("1000.00"));
        box.setWholesalePrice(new BigDecimal("800.00"));
        dbManager.insertProduct(box);

        Product pack = new Product();
        pack.setBarcode(packBar);
        pack.setName("Packet Name");
        pack.setRetailPrice(new BigDecimal("100.00"));
        pack.setWholesalePrice(new BigDecimal("80.00"));
        pack.setParentWholesaleBarcode(boxBar);
        pack.setConversionYield(10);
        dbManager.insertProduct(pack);

        Product piece = new Product();
        piece.setBarcode(pieceBar);
        piece.setName("Piece Name");
        piece.setRetailPrice(new BigDecimal("10.00"));
        piece.setWholesalePrice(new BigDecimal("8.00"));
        piece.setParentWholesaleBarcode(packBar);
        piece.setConversionYield(10);
        dbManager.insertProduct(piece);

        // Trace down from Box
        Product baseFromBox = dbManager.findBaseRetailItem(boxBar);
        assertNotNull(baseFromBox);
        assertEquals(pieceBar, baseFromBox.getBarcode());

        // Trace down from Packet
        Product baseFromPack = dbManager.findBaseRetailItem(packBar);
        assertNotNull(baseFromPack);
        assertEquals(pieceBar, baseFromPack.getBarcode());

        // Trace down from Piece itself
        Product baseFromPiece = dbManager.findBaseRetailItem(pieceBar);
        assertNotNull(baseFromPiece);
        assertEquals(pieceBar, baseFromPiece.getBarcode());
    }
}