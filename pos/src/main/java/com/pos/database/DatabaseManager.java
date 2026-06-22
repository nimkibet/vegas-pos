package com.pos.database;

import com.pos.entity.Customer;
import com.pos.entity.CustomerDebtSummary;
import com.pos.entity.Product;
import com.pos.entity.Sale;
import com.pos.entity.SaleItem;
import com.pos.entity.Shift;
import com.pos.entity.User;
import com.pos.entity.ActivityLog;
import com.pos.entity.ProductBarcodeMatch;
import com.pos.entity.SupplierTransaction;
import com.pos.entity.SupplierTransactionItem;
import com.pos.util.BrandingConstants;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public class DatabaseManager {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private static DatabaseManager instance;
    private final String dbPath;
    private final String jdbcUrl;
    private Properties config;
    private TerminalMode terminalMode;
    private final Object dbLock = new Object();
    
    public enum TerminalMode {
        MASTER("MASTER"),
        CLIENT("CLIENT");
        
        private final String value;
        TerminalMode(String value) { this.value = value; }
        public String getValue() { return value; }
        public static TerminalMode fromString(String s) {
            for (TerminalMode m : values()) {
                if (m.value.equalsIgnoreCase(s)) return m;
            }
            return MASTER;
        }
    }

    private DatabaseManager() {
        this.config = loadConfig();
        this.terminalMode = TerminalMode.fromString(config.getProperty("terminal.mode", "MASTER"));
        
        String dbFileName = config.getProperty("database.name", BrandingConstants.DATABASE_FILE);
        String dbPath = getAppDataPath() + File.separator + dbFileName;
        
        if (terminalMode == TerminalMode.CLIENT) {
            String masterIp = config.getProperty("master.ip", "192.168.1.100");
            this.jdbcUrl = "jdbc:sqlite://" + masterIp + "/" + dbPath;
        } else {
            this.jdbcUrl = "jdbc:sqlite:" + dbPath;
        }
        
        this.dbPath = dbPath;
        logger.info("Database initialized - Mode: {}, Path: {}", terminalMode, dbPath);
    }
    
    private String getAppDataPath() {
        String userHome = System.getProperty("user.home");
        String appDataPath = userHome + File.separator + "AppData" + File.separator + 
                            BrandingConstants.DATABASE_FOLDER;
        
        File dir = new File(appDataPath);
        if (!dir.exists()) {
            dir.mkdirs();
            logger.info("Created database directory: {}", appDataPath);
        }
        
        return appDataPath;
    }

    private static boolean isInitialized = false;
    
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public static synchronized void resetInstanceForTest(String testDbName) {
        instance = new DatabaseManager(testDbName);
        isInitialized = false;
    }

    private DatabaseManager(String dbFileName) {
        this.config = loadConfig();
        this.terminalMode = TerminalMode.MASTER;
        String dbPath = getAppDataPath() + File.separator + testDbNameHelper(dbFileName);
        this.jdbcUrl = "jdbc:sqlite:" + dbPath;
        this.dbPath = dbPath;
        logger.info("Database test instance initialized - Mode: {}, Path: {}", terminalMode, dbPath);
    }

    private static String testDbNameHelper(String dbFileName) {
        return dbFileName;
    }
    
    private Properties loadConfig() {
        Properties props = new Properties();
        try {
            java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("config.properties");
            if (is != null) {
                props.load(is);
                logger.info("Loaded config.properties");
            } else {
                logger.warn("config.properties not found, using defaults");
            }
        } catch (Exception e) {
            logger.warn("Error loading config.properties", e);
        }
        return props;
    }

    public TerminalMode getTerminalMode() {
        return terminalMode;
    }
    
    public boolean isMaster() {
        return terminalMode == TerminalMode.MASTER;
    }
    
    private Connection connect() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        }
        
        Connection conn = DriverManager.getConnection(jdbcUrl);
        conn.setAutoCommit(false);
        ((org.sqlite.SQLiteConnection)conn).setBusyTimeout(30000);
        return conn;
    }
    
    /**
     * Get a fresh database connection for external use.
     * Caller is responsible for closing the connection.
     * @return A new database connection
     * @throws SQLException if connection fails
     */
    public Connection getConnection() throws SQLException {
        return connect();
    }
    
    /**
     * Initialize the database - creates tables and seeds data.
     * This is the main entry point for database initialization.
     * @throws SQLException if initialization fails
     */
    public void initialize() throws SQLException {
        if (!isInitialized) {
            initializeSchema();
            // IMPORTANT: seedDummyData must be called AFTER initializeSchema() returns
            // to ensure the first connection is fully closed before seeding starts
            seedDummyData();
            isInitialized = true;
        }
        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            ensureSupplierStockInTables(stmt);
            ensureCustomerCreditSchema(stmt);
            conn.commit();
        }
    }
    
    private void initializeSchema() throws SQLException {
        try (Connection conn = connect()) {
            runSchemaScript(conn);
            conn.commit();
            logger.info("Database schema created successfully");
        }
    }
    
    private void runSchemaScript(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
              stmt.execute("""
                  CREATE TABLE IF NOT EXISTS users (
                      id TEXT PRIMARY KEY,
                      username TEXT NOT NULL UNIQUE,
                      password_hash TEXT NOT NULL,
                      full_name TEXT NOT NULL,
                      role TEXT NOT NULL CHECK(role IN ('ADMIN', 'ATTENDANT')),
                      is_active INTEGER NOT NULL DEFAULT 1,
                      is_synced INTEGER NOT NULL DEFAULT 0,
                      created_at TEXT NOT NULL,
                      updated_at TEXT NOT NULL
                  )
              """);
              
              stmt.execute("""
                  CREATE TABLE IF NOT EXISTS customers (
                      id TEXT PRIMARY KEY,
                      name TEXT NOT NULL,
                      phone TEXT,
                      created_at TEXT NOT NULL
                  )
              """);
              
              stmt.execute("""
                  CREATE TABLE IF NOT EXISTS customer_payments (
                      id TEXT PRIMARY KEY,
                      sale_id TEXT NOT NULL,
                      amount_paid REAL NOT NULL,
                      payment_date TEXT NOT NULL,
                      FOREIGN KEY (sale_id) REFERENCES sales(id)
                  )
              """);
              
              stmt.execute("""
                  CREATE TABLE IF NOT EXISTS products (
                      id TEXT PRIMARY KEY,
                      barcode TEXT UNIQUE,
                      name TEXT NOT NULL,
                      description TEXT,
                      category TEXT,
                      retail_price TEXT NOT NULL,
                      wholesale_price TEXT NOT NULL,
                      stock_quantity INTEGER NOT NULL DEFAULT 0,
                      min_stock_level INTEGER NOT NULL DEFAULT 0,
                      image_path TEXT,
                      supplier TEXT,
                      status TEXT NOT NULL DEFAULT 'APPROVED',
                      is_active INTEGER NOT NULL DEFAULT 1,
                      is_synced INTEGER NOT NULL DEFAULT 0,
                      created_at TEXT NOT NULL,
                      updated_at TEXT NOT NULL,
                      bulk_barcode TEXT,
                      bulk_price REAL DEFAULT 0.0,
                      pieces_per_bulk INTEGER DEFAULT 1,
                      parent_barcode TEXT DEFAULT NULL,
                      deduction_ratio REAL DEFAULT 1.0,
                      unit_type TEXT DEFAULT 'Pieces',
                      parent_wholesale_barcode TEXT DEFAULT NULL,
                      conversion_yield INTEGER DEFAULT 0,
                      loose_remainder_stock INTEGER DEFAULT 0,
                      bundle_size INTEGER DEFAULT 1,
                      raw_piece_yield INTEGER DEFAULT 0,
                      volume_qty INTEGER DEFAULT 0,
                      volume_price REAL DEFAULT 0.0
                  )
              """);
              
              ensureProductBulkColumns(stmt);
              ensureProductVariantColumns(stmt);
              ensureProductUnitTypeColumns(stmt);
              ensureAutoConversionColumns(stmt);
              ensureProductVolumeColumns(stmt);
              ensureSystemLogsTable(stmt);
              ensureSaleItemCogsColumn(stmt);
            
            stmt.execute("""
                 CREATE TABLE IF NOT EXISTS sales (
                     id TEXT PRIMARY KEY,
                     user_id TEXT NOT NULL,
                     customer_id TEXT,
                     payment_status TEXT DEFAULT 'PAID',
                     subtotal TEXT NOT NULL,
                     tax_amount TEXT NOT NULL DEFAULT '0.00',
                     discount_amount TEXT NOT NULL DEFAULT '0.00',
                     total TEXT NOT NULL,
                     payment_method TEXT NOT NULL,
                     amount_paid TEXT NOT NULL,
                     change_given TEXT NOT NULL,
                     status TEXT NOT NULL DEFAULT 'COMPLETED',
                     notes TEXT,
                     is_synced INTEGER NOT NULL DEFAULT 0,
                     created_at TEXT NOT NULL,
                     updated_at TEXT NOT NULL,
                     cash_amount TEXT DEFAULT '0.00',
                     mpesa_amount TEXT DEFAULT '0.00',
                     secondary_payment_method TEXT,
                     FOREIGN KEY (user_id) REFERENCES users(id)
                 )
             """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sale_items (
                    id TEXT PRIMARY KEY,
                    sale_id TEXT NOT NULL,
                    product_id TEXT NOT NULL,
                    product_name TEXT NOT NULL,
                    product_barcode TEXT,
                    quantity INTEGER NOT NULL,
                    unit_price TEXT NOT NULL,
                    total_price TEXT NOT NULL,
                    is_synced INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL,
                    unit_cogs REAL DEFAULT 0.0,
                    FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE,
                    FOREIGN KEY (product_id) REFERENCES products(id)
                )
            """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sync_log (
                    id TEXT PRIMARY KEY,
                    table_name TEXT NOT NULL,
                    record_id TEXT NOT NULL,
                    operation TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'PENDING',
                    error_message TEXT,
                    created_at TEXT NOT NULL,
                    synced_at TEXT
                )
            """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL,
                    description TEXT,
                    updated_at TEXT NOT NULL
                )
            """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS shifts (
                    id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    start_time TEXT NOT NULL,
                    end_time TEXT,
                    starting_float TEXT NOT NULL DEFAULT '0.00',
                    expected_cash TEXT NOT NULL DEFAULT '0.00',
                    actual_cash TEXT,
                    mpesa_total TEXT NOT NULL DEFAULT '0.00',
                    card_total TEXT NOT NULL DEFAULT '0.00',
                    transaction_count INTEGER NOT NULL DEFAULT 0,
                    status TEXT NOT NULL DEFAULT 'OPEN',
                    notes TEXT,
                    is_synced INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (user_id) REFERENCES users(id)
                )
            """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS activity_logs (
                    id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    user_name TEXT NOT NULL,
                    action_type TEXT NOT NULL,
                    target_description TEXT,
                    details TEXT,
                    is_synced INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
            """);
            
            ensureSupplierStockInTables(stmt);
            
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_username ON users(username)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_products_barcode ON products(barcode)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_products_status ON products(status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sales_user_id ON sales(user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sale_items_sale_id ON sale_items(sale_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_shifts_user_id ON shifts(user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_shifts_status ON shifts(status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_activity_logs_user_id ON activity_logs(user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_activity_logs_action_type ON activity_logs(action_type)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_activity_logs_created_at ON activity_logs(created_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_activity_logs_is_synced ON activity_logs(is_synced)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sales_customer_id ON sales(customer_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sales_payment_status ON sales(payment_status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_customer_payments_sale_id ON customer_payments(sale_id)");
            
            // Default users and products are now seeded via seedDummyData() which uses proper BCrypt hashing
            // This avoids holding the DB lock during seeding
            
            logger.info("Database schema created successfully");
        }
    }
    
    /**
     * Supplier stock-in tables (idempotent). Runs from full schema setup and on every {@link #initialize()}
     * so existing installs receive new tables without resetting {@code isInitialized}.
     */
    private void ensureSupplierStockInTables(Statement stmt) throws SQLException {
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS supplier_transactions (
                    id TEXT PRIMARY KEY,
                    supplier_name TEXT NOT NULL,
                    total_cost REAL NOT NULL,
                    status TEXT NOT NULL CHECK(status IN ('PAID', 'CREDIT')),
                    created_at TEXT NOT NULL
                )
            """);
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS supplier_transaction_items (
                    id TEXT PRIMARY KEY,
                    transaction_id TEXT NOT NULL,
                    product_id TEXT NOT NULL,
                    quantity_received INTEGER NOT NULL,
                    buying_price REAL NOT NULL,
                    FOREIGN KEY (transaction_id) REFERENCES supplier_transactions(id) ON DELETE CASCADE,
                    FOREIGN KEY (product_id) REFERENCES products(id)
                )
            """);
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_supplier_transactions_created_at ON supplier_transactions(created_at)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_supplier_transactions_status ON supplier_transactions(status)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_supplier_transaction_items_tx ON supplier_transaction_items(transaction_id)");

        // Schema upgrade for payment source + partial offset tracking (idempotent)
        applyAlterMigrations(stmt, new String[]{
            "ALTER TABLE supplier_transactions ADD COLUMN payment_source TEXT DEFAULT 'Cash (From Shelf)'",
            "ALTER TABLE supplier_transactions ADD COLUMN debtor_id TEXT DEFAULT NULL",
            "ALTER TABLE supplier_transactions ADD COLUMN debtor_offset REAL DEFAULT 0.0",
            "ALTER TABLE supplier_transactions ADD COLUMN cash_paid REAL DEFAULT 0.0",
            "ALTER TABLE supplier_transactions ADD COLUMN is_synced INTEGER NOT NULL DEFAULT 0"
        });
    }
    
    /**
     * Adds bulk/box columns to products when upgrading an existing database
     * (CREATE TABLE IF NOT EXISTS does not add new columns to old tables).
     */
    private void ensureProductBulkColumns(Statement stmt) {
        applyAlterMigrations(stmt, new String[]{
            "ALTER TABLE products ADD COLUMN bulk_barcode TEXT",
            "ALTER TABLE products ADD COLUMN bulk_price REAL DEFAULT 0.0",
            "ALTER TABLE products ADD COLUMN pieces_per_bulk INTEGER DEFAULT 1"
        });
    }

    private void ensureProductVariantColumns(Statement stmt) {
        applyAlterMigrations(stmt, new String[]{
            "ALTER TABLE products ADD COLUMN parent_barcode TEXT DEFAULT NULL",
            "ALTER TABLE products ADD COLUMN deduction_ratio REAL DEFAULT 1.0"
        });
    }

    private void ensureProductUnitTypeColumns(Statement stmt) {
        applyAlterMigrations(stmt, new String[]{
            "ALTER TABLE products ADD COLUMN unit_type TEXT DEFAULT 'Pieces'"
        });
    }

    private void ensureAutoConversionColumns(Statement stmt) {
        applyAlterMigrations(stmt, new String[]{
            "ALTER TABLE products ADD COLUMN parent_wholesale_barcode TEXT DEFAULT NULL",
            "ALTER TABLE products ADD COLUMN conversion_yield INTEGER DEFAULT 0",
            "ALTER TABLE products ADD COLUMN loose_remainder_stock INTEGER DEFAULT 0",
            "ALTER TABLE products ADD COLUMN bundle_size INTEGER DEFAULT 1",
            "ALTER TABLE products ADD COLUMN raw_piece_yield INTEGER DEFAULT 0"
        });
    }

    private void ensureProductVolumeColumns(Statement stmt) {
        applyAlterMigrations(stmt, new String[]{
            "ALTER TABLE products ADD COLUMN volume_qty INTEGER DEFAULT 0",
            "ALTER TABLE products ADD COLUMN volume_price REAL DEFAULT 0.0"
        });
    }

    private void ensureSystemLogsTable(Statement stmt) {
        try {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS system_logs (
                    id TEXT PRIMARY KEY,
                    barcode TEXT,
                    type TEXT,
                    message TEXT,
                    created_at TEXT NOT NULL
                )
            """);
        } catch (SQLException e) {
            logger.warn("Could not create system_logs table: {}", e.getMessage());
        }
    }

    private void ensureSaleItemCogsColumn(Statement stmt) {
        applyAlterMigrations(stmt, new String[]{
            "ALTER TABLE sale_items ADD COLUMN unit_cogs REAL DEFAULT 0.0"
        });
    }

    /**
     * Customer credit / A/R tables and sales columns (idempotent for existing databases).
     */
    private void ensureCustomerCreditSchema(Statement stmt) throws SQLException {
        // Explicit table creation for the new table
        stmt.execute("CREATE TABLE IF NOT EXISTS customer_payments (id TEXT PRIMARY KEY, sale_id TEXT, amount_paid REAL, payment_date TIMESTAMP)");
        
        // Suppliers table for auto-suggest
        stmt.execute("CREATE TABLE IF NOT EXISTS suppliers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)");
        
        // Safe ALTER TABLE blocks to upgrade existing databases
        try { stmt.execute("ALTER TABLE sales ADD COLUMN customer_id TEXT"); } catch (Exception e) {}
        try { stmt.execute("ALTER TABLE sales ADD COLUMN payment_status TEXT DEFAULT 'PAID'"); } catch (Exception e) {}

        // Credit limit column on customers (idempotent)
        applyAlterMigrations(stmt, new String[]{
            "ALTER TABLE customers ADD COLUMN credit_limit REAL DEFAULT 0.0",
            "ALTER TABLE customers ADD COLUMN is_synced INTEGER NOT NULL DEFAULT 0"
        });
        
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS customers (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    phone TEXT,
                    created_at TEXT NOT NULL
                )
            """);
        
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_sales_customer_id ON sales(customer_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_sales_payment_status ON sales(payment_status)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_customer_payments_sale_id ON customer_payments(sale_id)");
    }

    private void ensureSalesCreditColumns(Statement stmt) {
        applyAlterMigrations(stmt, new String[]{
            "ALTER TABLE sales ADD COLUMN customer_id TEXT",
            "ALTER TABLE sales ADD COLUMN payment_status TEXT DEFAULT 'PAID'"
        });
    }

    private void applyAlterMigrations(Statement stmt, String[] alters) {
        for (String sql : alters) {
            try {
                stmt.execute(sql);
            } catch (SQLException e) {
                String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                if (!msg.contains("duplicate column")) {
                    logger.warn("Could not apply migration: {}", e.getMessage());
                }
            }
        }
    }
    
    private void insertDefaultUsers(Connection conn, Statement stmt) throws SQLException {
        // Users are now seeded via seedDummyData() which uses proper BCrypt hashing
    }
    
    private void insertDefaultProducts(Connection conn, Statement stmt) throws SQLException {
        // Products are now seeded via seedDummyData() for consistency
    }

    private void seedDummyData() throws SQLException {
        Connection conn = null;
        try {
            conn = connect();
            
            // Check if users exist
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                rs.next();
                if (rs.getInt(1) == 0) {
                    logger.info("Seeding default users...");
                    
                    String adminHash = "$2a$04$X2O3Vpc79QEaodY.GGY8g.GhmfGWx4paDRErM9qN7suFmnSo1oQf.";
                    String attendantHash = "$2a$04$d7Fbjn9hdKAw1msnS/UkhOtYUg.On.fwcE2r07.3UEpFaqWMYDSGG";
                    
                    try (PreparedStatement pstmt = conn.prepareStatement(
                            "INSERT INTO users (id, username, password_hash, full_name, role, is_active, is_synced, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                        pstmt.setString(1, "550e8400-e29b-41d4-a716-446655440000");
                        pstmt.setString(2, "admin");
                        pstmt.setString(3, adminHash);
                        pstmt.setString(4, "System Administrator");
                        pstmt.setString(5, "ADMIN");
                        pstmt.setInt(6, 1);
                        pstmt.setInt(7, 0);
                        pstmt.setString(8, "2024-01-01 00:00:00");
                        pstmt.setString(9, "2024-01-01 00:00:00");
                        pstmt.executeUpdate();
                        
                        pstmt.setString(1, "550e8400-e29b-41d4-a716-446655440001");
                        pstmt.setString(2, "attendant");
                        pstmt.setString(3, attendantHash);
                        pstmt.setString(4, "Store Attendant");
                        pstmt.setString(5, "ATTENDANT");
                        pstmt.executeUpdate();
                    }
                    
                    logger.info("Default users seeded");
                }
            }
            
            // Check and seed products (removed dummy products seeding to allow real products database)
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM products")) {
                rs.next();
                if (rs.getInt(1) == 0) {
                    logger.info("Database is empty of products, ready for real products.");
                }
            }
            
            conn.commit();
            
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }

    // -------------------------------------------------------------------------
    // DATABASE RESET  (Clean Start)
    // -------------------------------------------------------------------------
    /**
     * Wipes all transactional and product data from the database while
     * preserving the user accounts so the admin can still log in.
     *
     * <p>Tables cleared (in dependency order to respect FK constraints):
     * <ul>
     *   <li>sale_items, customer_payments, sync_log (children first)</li>
     *   <li>sales, shifts, activity_logs, system_logs</li>
     *   <li>supplier_transaction_items → supplier_transactions</li>
     *   <li>products, customers, suppliers</li>
     * </ul>
     *
     * <p>The {@code users} table is intentionally left intact.
     *
     * @throws SQLException if any DELETE fails
     */
    public void resetDatabase() throws SQLException {
        logger.warn("DATABASE RESET requested – erasing all transactional data.");
        synchronized (dbLock) {
            try (Connection conn = connect();
                 Statement stmt = conn.createStatement()) {

                // Disable FK enforcement temporarily so we can delete in any order
                stmt.execute("PRAGMA foreign_keys = OFF");

                // --- Children first ---
                stmt.execute("DELETE FROM sale_items");
                stmt.execute("DELETE FROM customer_payments");
                stmt.execute("DELETE FROM sync_log");
                stmt.execute("DELETE FROM supplier_transaction_items");

                // --- Parents ---
                stmt.execute("DELETE FROM sales");
                stmt.execute("DELETE FROM shifts");
                stmt.execute("DELETE FROM activity_logs");
                stmt.execute("DELETE FROM system_logs");
                stmt.execute("DELETE FROM supplier_transactions");

                // --- Master data ---
                stmt.execute("DELETE FROM products");
                stmt.execute("DELETE FROM customers");
                stmt.execute("DELETE FROM suppliers");

                // Re-enable FK enforcement
                stmt.execute("PRAGMA foreign_keys = ON");

                // Reclaim freed pages
                stmt.execute("VACUUM");

                conn.commit();
                logger.info("Database reset complete – all data wiped.");
            }
        }

        // Reset the initialization flag so seedDummyData() can run again
        // if initialize() is ever called, but do NOT call it here – users
        // are preserved and the app is still running.
        logger.info("Database reset successful. Product catalogue is empty; users are intact.");
    }

    public List<Map<String, Object>> getRecentSales(int limit) {
        List<Map<String, Object>> sales = new ArrayList<>();
        String sql = """
            SELECT s.id, s.created_at, s.total_amount, s.is_synced,
                   (SELECT COUNT(*) FROM sale_items WHERE sale_id = s.id) as item_count
            FROM sales s
            ORDER BY s.created_at DESC
            LIMIT ?
            """;
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> sale = new HashMap<>();
                    sale.put("saleId", rs.getString("id"));
                    sale.put("date", rs.getString("created_at"));
                    sale.put("itemCount", rs.getInt("item_count"));
                    sale.put("total", rs.getDouble("total_amount"));
                    sale.put("synced", rs.getInt("is_synced") == 1);
                    sales.add(sale);
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching recent sales", e);
        }
        
        return sales;
    }

    public Optional<User> findUserByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
        }
        
        return Optional.empty();
    }

    public Optional<User> findUserById(String id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
        }
        
        return Optional.empty();
    }

    public List<User> getAllUsers() throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY full_name";
        
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                users.add(mapResultSetToUser(rs));
            }
        }
        
        return users;
    }

    public List<CustomerDebtSummary> getAllDebtors() throws SQLException {
        List<CustomerDebtSummary> debtorSummaries = new ArrayList<>();
        // Get all customers who have at least one CREDIT sale
        String sql = "SELECT DISTINCT customer_id FROM sales WHERE payment_status = 'CREDIT' AND customer_id IS NOT NULL";
        
        List<String> customerIds = new ArrayList<>();
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                customerIds.add(rs.getString("customer_id"));
            }
        }

        for (String customerId : customerIds) {
            Optional<Customer> customerOpt = findCustomerById(customerId);
            if (customerOpt.isPresent()) {
                List<Sale> unpaidSales = getUnpaidSalesForCustomer(customerId);
                BigDecimal totalDebt = BigDecimal.ZERO;
                int pendingBatches = 0;

                for (Sale sale : unpaidSales) {
                    BigDecimal totalPaid = getTotalPaidForSale(sale.getId());
                    BigDecimal balance = sale.getTotal().subtract(totalPaid);
                    if (balance.compareTo(BigDecimal.ZERO) > 0) {
                        totalDebt = totalDebt.add(balance);
                        pendingBatches++;
                    }
                }

                if (totalDebt.compareTo(BigDecimal.ZERO) > 0) {
                    debtorSummaries.add(new CustomerDebtSummary(customerOpt.get(), totalDebt, pendingBatches));
                }
            }
        }
        
        // Sort by total debt descending
        debtorSummaries.sort((a, b) -> b.getTotalDebt().compareTo(a.getTotalDebt()));
        
        return debtorSummaries;
    }

    public void insertUser(User user) throws SQLException {
        String sql = """
            INSERT INTO users (id, username, password_hash, full_name, role, is_active, is_synced, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user.getId());
            pstmt.setString(2, user.getUsername());
            pstmt.setString(3, user.getPasswordHash());
            pstmt.setString(4, user.getFullName());
            pstmt.setString(5, user.getRole().name());
            pstmt.setInt(6, user.isActive() ? 1 : 0);
            pstmt.setInt(7, user.isSynced() ? 1 : 0);
            pstmt.setString(8, user.getCreatedAt().format(DATE_TIME_FORMATTER));
            pstmt.setString(9, user.getUpdatedAt().format(DATE_TIME_FORMATTER));
            
            pstmt.executeUpdate();
            conn.commit();
        }
    }

    public void updateUser(User user) throws SQLException {
        String sql = """
            UPDATE users SET username = ?, password_hash = ?, full_name = ?, role = ?,
            is_active = ?, is_synced = ?, updated_at = ? WHERE id = ?
        """;
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPasswordHash());
            pstmt.setString(3, user.getFullName());
            pstmt.setString(4, user.getRole().name());
            pstmt.setInt(5, user.isActive() ? 1 : 0);
            pstmt.setInt(6, user.isSynced() ? 1 : 0);
            pstmt.setString(7, LocalDateTime.now().format(DATE_TIME_FORMATTER));
            pstmt.setString(8, user.getId());
            
            pstmt.executeUpdate();
            conn.commit();
        }
    }
    
    public void deleteUser(String userId) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.executeUpdate();
            conn.commit();
        }
    }
    
    public boolean usernameExists(String username) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        
        return false;
    }
    
    public boolean usernameExistsExcluding(String username, String excludeUserId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ? AND id != ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, excludeUserId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        
        return false;
    }

    private Optional<Product> findProductByIdInternal(Connection conn, String id) throws SQLException {
        String sql = "SELECT * FROM products WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToProduct(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Product> findProductById(String id) throws SQLException {
        String sql = "SELECT * FROM products WHERE id = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToProduct(rs));
                }
            }
        }
        
        return Optional.empty();
    }

    private Optional<Product> findProductByBarcodeInternal(Connection conn, String barcode) throws SQLException {
        String sql = "SELECT * FROM products WHERE barcode = ? AND is_active = 1";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, barcode);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToProduct(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Product> findProductByBarcode(String barcode) throws SQLException {
        String sql = "SELECT * FROM products WHERE barcode = ? AND is_active = 1";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, barcode);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToProduct(rs));
                }
            }
        }
        
        return Optional.empty();
    }

    /**
     * Resolves a barcode against unit {@code barcode} first, then {@code bulk_barcode}.
     */
    public Optional<ProductBarcodeMatch> findProductByAnyBarcode(String barcode) throws SQLException {
        if (barcode == null || barcode.isBlank()) {
            return Optional.empty();
        }
        Optional<Product> unit = findProductByBarcode(barcode);
        if (unit.isPresent()) {
            return Optional.of(new ProductBarcodeMatch(unit.get(), ProductBarcodeMatch.MatchType.SINGLE));
        }
        Optional<Product> box = findProductByBulkBarcode(barcode);
        if (box.isPresent()) {
            return Optional.of(new ProductBarcodeMatch(box.get(), ProductBarcodeMatch.MatchType.BULK));
        }
        return Optional.empty();
    }

    public Optional<Product> findProductByBulkBarcode(String barcode) throws SQLException {
        String sql = """
            SELECT * FROM products
            WHERE bulk_barcode = ? AND is_active = 1
              AND bulk_barcode IS NOT NULL AND TRIM(bulk_barcode) != ''
            """;
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, barcode);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToProduct(rs));
                }
            }
        }
        
        return Optional.empty();
    }

    public List<Product> getAllProducts() throws SQLException {
        List<Product> products = new ArrayList<>();
        String sql = "SELECT * FROM products WHERE is_active = 1 ORDER BY name";
        
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                products.add(mapResultSetToProduct(rs));
            }
        }
        
        return products;
    }

    public List<Product> searchProducts(String searchTerm) throws SQLException {
        List<Product> products = new ArrayList<>();
        String sql = """
            SELECT * FROM products 
            WHERE is_active = 1 AND (
                name LIKE ? OR barcode LIKE ? OR IFNULL(bulk_barcode, '') LIKE ?
            )
            ORDER BY name
        """;
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            String term = "%" + searchTerm + "%";
            pstmt.setString(1, term);
            pstmt.setString(2, term);
            pstmt.setString(3, term);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    products.add(mapResultSetToProduct(rs));
                }
            }
        }
        
        return products;
    }

    public List<Product> getProductsByCategory(String category) throws SQLException {
        List<Product> products = new ArrayList<>();
        String sql = "SELECT * FROM products WHERE category = ? AND is_active = 1 ORDER BY name";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, category);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    products.add(mapResultSetToProduct(rs));
                }
            }
        }
        
        return products;
    }

    public List<Product> getLowStockProducts() throws SQLException {
        List<Product> products = new ArrayList<>();
        String sql = "SELECT * FROM products WHERE stock_quantity <= min_stock_level AND is_active = 1 ORDER BY stock_quantity";
        
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                products.add(mapResultSetToProduct(rs));
            }
        }
        
        return products;
    }

    public void insertProduct(Product product) throws SQLException {
        String sql = """
            INSERT INTO products (id, barcode, name, description, category, retail_price,
            wholesale_price, stock_quantity, min_stock_level, image_path, supplier, status, is_active, is_synced, created_at, updated_at,
            bulk_barcode, bulk_price, pieces_per_bulk, parent_barcode, deduction_ratio, unit_type,
            parent_wholesale_barcode, conversion_yield, loose_remainder_stock, bundle_size, raw_piece_yield, volume_qty, volume_price)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, product.getId());
            pstmt.setString(2, product.getBarcode());
            pstmt.setString(3, product.getName());
            pstmt.setString(4, product.getDescription());
            pstmt.setString(5, product.getCategory());
            pstmt.setString(6, product.getRetailPrice().toPlainString());
            pstmt.setString(7, product.getWholesalePrice().toPlainString());
            pstmt.setDouble(8, product.getStockQuantity());
            pstmt.setDouble(9, product.getMinStockLevel());
            pstmt.setString(10, product.getImagePath());
            pstmt.setString(11, product.getSupplier());
            pstmt.setString(12, product.getStatus() != null ? product.getStatus() : "APPROVED");
            pstmt.setInt(13, product.isActive() ? 1 : 0);
            pstmt.setInt(14, product.isSynced() ? 1 : 0);
            pstmt.setString(15, product.getCreatedAt().format(DATE_TIME_FORMATTER));
            pstmt.setString(16, product.getUpdatedAt().format(DATE_TIME_FORMATTER));
            pstmt.setString(17, product.getBulkBarcode());
            BigDecimal bulkPrice = product.getBulkPrice() != null ? product.getBulkPrice() : BigDecimal.ZERO;
            pstmt.setBigDecimal(18, bulkPrice);
            int piecesPerBulk = product.getPiecesPerBulk() > 0 ? product.getPiecesPerBulk() : 1;
            pstmt.setInt(19, piecesPerBulk);
            pstmt.setString(20, product.getParentBarcode());
            pstmt.setDouble(21, product.getDeductionRatio());
            pstmt.setString(22, product.getUnitType() != null ? product.getUnitType() : "Pieces");
            pstmt.setString(23, product.getParentWholesaleBarcode());
            pstmt.setInt(24, product.getConversionYield());
            pstmt.setInt(25, product.getLooseRemainderStock());
            pstmt.setInt(26, product.getBundleSize());
            pstmt.setInt(27, product.getRawPieceYield());
            pstmt.setInt(28, product.getVolumeQty());
            pstmt.setDouble(29, product.getVolumePrice());
            
            pstmt.executeUpdate();
            conn.commit();
        }
    }

    public void updateProduct(Product product) throws SQLException {
        String sql = """
            UPDATE products SET barcode = ?, name = ?, description = ?, category = ?,
            retail_price = ?, wholesale_price = ?, stock_quantity = ?, min_stock_level = ?,
            image_path = ?, supplier = ?, status = ?, is_active = ?, is_synced = ?,
            bulk_barcode = ?, bulk_price = ?, pieces_per_bulk = ?, parent_barcode = ?, 
            deduction_ratio = ?, unit_type = ?, parent_wholesale_barcode = ?, 
            conversion_yield = ?, loose_remainder_stock = ?, bundle_size = ?, raw_piece_yield = ?,
            volume_qty = ?, volume_price = ?, updated_at = ? WHERE id = ?
        """;
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, product.getBarcode());
            pstmt.setString(2, product.getName());
            pstmt.setString(3, product.getDescription());
            pstmt.setString(4, product.getCategory());
            pstmt.setString(5, product.getRetailPrice().toPlainString());
            pstmt.setString(6, product.getWholesalePrice().toPlainString());
            pstmt.setDouble(7, product.getStockQuantity());
            pstmt.setDouble(8, product.getMinStockLevel());
            pstmt.setString(9, product.getImagePath());
            pstmt.setString(10, product.getSupplier());
            pstmt.setString(11, product.getStatus() != null ? product.getStatus() : "APPROVED");
            pstmt.setInt(12, product.isActive() ? 1 : 0);
            pstmt.setInt(13, product.isSynced() ? 1 : 0);
            pstmt.setString(14, product.getBulkBarcode());
            BigDecimal bulkPrice = product.getBulkPrice() != null ? product.getBulkPrice() : BigDecimal.ZERO;
            pstmt.setBigDecimal(15, bulkPrice);
            int piecesPerBulk = product.getPiecesPerBulk() > 0 ? product.getPiecesPerBulk() : 1;
            pstmt.setInt(16, piecesPerBulk);
            pstmt.setString(17, product.getParentBarcode());
            pstmt.setDouble(18, product.getDeductionRatio());
            pstmt.setString(19, product.getUnitType() != null ? product.getUnitType() : "Pieces");
            pstmt.setString(20, product.getParentWholesaleBarcode());
            pstmt.setInt(21, product.getConversionYield());
            pstmt.setInt(22, product.getLooseRemainderStock());
            pstmt.setInt(23, product.getBundleSize());
            pstmt.setInt(24, product.getRawPieceYield());
            pstmt.setInt(25, product.getVolumeQty());
            pstmt.setDouble(26, product.getVolumePrice());
            pstmt.setString(27, LocalDateTime.now().format(DATE_TIME_FORMATTER));
            pstmt.setString(28, product.getId());
            
            pstmt.executeUpdate();
            conn.commit();
        }
    }

    public void updateProductStock(String productId, double newQuantity) throws SQLException {
        String sql = "UPDATE products SET stock_quantity = ?, is_synced = 0, updated_at = ? WHERE id = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, newQuantity);
            pstmt.setString(2, LocalDateTime.now().format(DATE_TIME_FORMATTER));
            pstmt.setString(3, productId);
            
            pstmt.executeUpdate();
            conn.commit();
        }
    }
    
    void updateProductStock(Connection conn, String productId, double quantityDelta) throws SQLException {
        String sql = "UPDATE products SET stock_quantity = stock_quantity + ?, is_synced = 0, updated_at = ? WHERE id = ?";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, quantityDelta);
            pstmt.setString(2, LocalDateTime.now().format(DATE_TIME_FORMATTER));
            pstmt.setString(3, productId);
            
            pstmt.executeUpdate();
        }
    }
    
    public List<Product> getPendingProducts() throws SQLException {
        List<Product> products = new ArrayList<>();
        String sql = "SELECT * FROM products WHERE status = 'PENDING' AND is_active = 1 ORDER BY created_at DESC";
        
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                products.add(mapResultSetToProduct(rs));
            }
        }
        
        return products;
    }
    
    public void approveProduct(String productId) throws SQLException {
        String sql = "UPDATE products SET status = 'APPROVED', is_synced = 0, updated_at = ? WHERE id = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, LocalDateTime.now().format(DATE_TIME_FORMATTER));
            pstmt.setString(2, productId);
            
            pstmt.executeUpdate();
            conn.commit();
        }
    }
    
    public void updateProductStatus(String productId, String status) throws SQLException {
        String sql = "UPDATE products SET status = ?, is_synced = 0, updated_at = ? WHERE id = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setString(2, LocalDateTime.now().format(DATE_TIME_FORMATTER));
            pstmt.setString(3, productId);
            
            pstmt.executeUpdate();
            conn.commit();
        }
    }

    public void insertSale(Sale sale) throws SQLException {
        String sql = "INSERT INTO sales (id, user_id, customer_id, payment_status, subtotal, tax_amount, discount_amount, total, payment_method, amount_paid, change_given, status, notes, is_synced, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sale.getId());
            pstmt.setString(2, sale.getUserId());
            pstmt.setString(3, sale.getCustomerId());
            pstmt.setString(4, sale.getPaymentStatus() != null ? sale.getPaymentStatus() : "PAID");
            pstmt.setString(5, sale.getSubtotal().toPlainString());
            pstmt.setString(6, sale.getTaxAmount().toPlainString());
            pstmt.setString(7, sale.getDiscountAmount().toPlainString());
            pstmt.setString(8, sale.getTotal().toPlainString());
            pstmt.setString(9, sale.getPaymentMethod().name());
            pstmt.setString(10, sale.getAmountPaid().toPlainString());
            pstmt.setString(11, sale.getChangeGiven().toPlainString());
            pstmt.setString(12, sale.getStatus().name());
            pstmt.setString(13, sale.getNotes());
            pstmt.setInt(14, sale.isSynced() ? 1 : 0);
            pstmt.setString(15, sale.getCreatedAt().format(DATE_TIME_FORMATTER));
            pstmt.setString(16, sale.getUpdatedAt().format(DATE_TIME_FORMATTER));
            
            pstmt.executeUpdate();
            
            try {
                String updateSql = "UPDATE sales SET cash_amount = ?, mpesa_amount = ?, secondary_payment_method = ? WHERE id = ?";
                try (PreparedStatement updStmt = conn.prepareStatement(updateSql)) {
                    updStmt.setString(1, sale.getCashAmount() != null ? sale.getCashAmount().toPlainString() : "0");
                    updStmt.setString(2, sale.getMpesaAmount() != null ? sale.getMpesaAmount().toPlainString() : "0");
                    updStmt.setString(3, sale.getSecondaryPaymentMethod() != null ? sale.getSecondaryPaymentMethod().name() : null);
                    updStmt.setString(4, sale.getId());
                    updStmt.executeUpdate();
                }
            } catch (SQLException e) {
                logger.debug("Split payment columns may not exist in this schema version");
            }
            
            for (SaleItem item : sale.getItems()) {
                insertSaleItem(conn, item, sale.getId());
            }
            /*
            // Deduct stock (moved to POSController cart addition to support real-time negative sell-through and auto-conversion)
            for (SaleItem item : sale.getItems()) {
                Optional<Product> pOpt = findProductByIdInternal(conn, item.getProductId());
                if (pOpt.isPresent()) {
                    Product p = pOpt.get();
                    double delta = item.getQuantity();
                    
                    if (item.isBoxSale()) {
                        delta = item.getQuantity() * p.getPiecesPerBulk();
                    }
                    
                    // If this is a child variant, subtract from parent instead
                    if (p.getParentBarcode() != null && !p.getParentBarcode().isEmpty()) {
                        Optional<Product> parentOpt = findProductByBarcodeInternal(conn, p.getParentBarcode());
                        if (parentOpt.isPresent()) {
                            double parentDelta = delta * p.getDeductionRatio();
                            updateProductStock(conn, parentOpt.get().getId(), -parentDelta);
                        } else {
                            // Fallback if parent not found (shouldn't happen with proper setup)
                            updateProductStock(conn, p.getId(), -delta);
                        }
                    } else {
                        // Normal product
                        updateProductStock(conn, p.getId(), -delta);
                    }
                }
            }
            */
            
            // Initial credit payment is already stored in sales.amount_paid.
            // We do NOT insert it into customer_payments here to avoid double-counting.
            // customer_payments will ONLY hold subsequent ledger payments.
            
            conn.commit();

            if (sale.getCustomerId() != null && "CREDIT".equals(sale.getPaymentStatus())) {
                updateDebtorBalance(sale.getCustomerId());
            }
        }
    }

    private void insertCustomerPayment(Connection conn, String saleId, BigDecimal amount) throws SQLException {
        String sql = """
            INSERT INTO customer_payments (id, sale_id, amount_paid, payment_date)
            VALUES (?, ?, ?, ?)
            """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, java.util.UUID.randomUUID().toString());
            pstmt.setString(2, saleId);
            pstmt.setDouble(3, amount.doubleValue());
            pstmt.setString(4, LocalDateTime.now().format(DATE_TIME_FORMATTER));
            pstmt.executeUpdate();
        }
    }

    public void insertCustomerPayment(String saleId, BigDecimal amount) throws SQLException {
        try (Connection conn = connect()) {
            insertCustomerPayment(conn, saleId, amount);
            conn.commit();
        }
    }

    private void insertSaleItem(Connection conn, SaleItem item, String saleId) throws SQLException {
        String sql = """
            INSERT INTO sale_items (id, sale_id, product_id, product_name, product_barcode,
            quantity, unit_price, total_price, is_synced, created_at, unit_cogs)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, item.getId());
            pstmt.setString(2, saleId);
            pstmt.setString(3, item.getProductId());
            pstmt.setString(4, item.getProductName());
            pstmt.setString(5, item.getProductBarcode());
            pstmt.setDouble(6, item.getQuantity());
            pstmt.setString(7, item.getUnitPrice().toPlainString());
            pstmt.setString(8, item.getTotalPrice().toPlainString());
            pstmt.setInt(9, item.isSynced() ? 1 : 0);
            pstmt.setString(10, item.getCreatedAt().format(DATE_TIME_FORMATTER));
            pstmt.setDouble(11, item.getUnitCogs());
            
            pstmt.executeUpdate();
        }
    }

    public Optional<Sale> findSaleById(String id) throws SQLException {
        String sql = "SELECT * FROM sales WHERE id = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Sale sale = mapResultSetToSale(rs);
                    List<SaleItem> items = getSaleItems(sale.getId());
                    sale.setItems(items);
                    return Optional.of(sale);
                }
            }
        }
        
        return Optional.empty();
    }

    public List<SaleItem> getSaleItems(String saleId) throws SQLException {
        List<SaleItem> items = new ArrayList<>();
        String sql = "SELECT * FROM sale_items WHERE sale_id = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, saleId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    items.add(mapResultSetToSaleItem(rs));
                }
            }
        }
        
        return items;
    }

    public List<Sale> getTodaySales() throws SQLException {
        List<Sale> sales = new ArrayList<>();
        String sql = """
            SELECT * FROM sales 
            WHERE date(created_at) = date('now') 
            ORDER BY created_at DESC
        """;
        
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                Sale sale = mapResultSetToSale(rs);
                sale.setItems(getSaleItems(sale.getId()));
                sales.add(sale);
            }
        }
        
        return sales;
    }

    public List<Sale> getUnsyncedSales() throws SQLException {
        List<Sale> sales = new ArrayList<>();
        String sql = "SELECT * FROM sales WHERE is_synced = 0";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Sale sale = mapResultSetToSale(rs);
                sale.setItems(getSaleItems(sale.getId()));
                sales.add(sale);
            }
        }

        return sales;
    }

    public List<Customer> getUnsyncedCustomers() throws SQLException {
        List<Customer> customers = new ArrayList<>();
        String sql = "SELECT * FROM customers WHERE is_synced = 0";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                customers.add(mapResultSetToCustomer(rs));
            }
        }

        return customers;
    }

    public List<SupplierTransaction> getUnsyncedSupplierTransactions() throws SQLException {
        List<SupplierTransaction> list = new ArrayList<>();
        String sql = "SELECT * FROM supplier_transactions WHERE is_synced = 0";
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToSupplierTransaction(rs));
            }
        }
        return list;
    }

    public void markCustomerAsSynced(String id) throws SQLException {
        String sql = "UPDATE customers SET is_synced = 1 WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.executeUpdate();
            conn.commit();
        }
    }

    public void markSupplierTransactionAsSynced(String id) throws SQLException {
        String sql = "UPDATE supplier_transactions SET is_synced = 1 WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.executeUpdate();
            conn.commit();
        }
    }

    /**
     * Recalculates debtor balance and triggers cloud sync.
     */
    public void updateDebtorBalance(String customerId) {
        try {
            Optional<Customer> customerOpt = findCustomerById(customerId);
            if (customerOpt.isPresent()) {
                Customer customer = customerOpt.get();
                List<Sale> unpaidSales = getUnpaidSalesForCustomer(customerId);
                BigDecimal totalDebt = BigDecimal.ZERO;
                int pendingBatches = 0;

                for (Sale sale : unpaidSales) {
                    BigDecimal totalPaid = getTotalPaidForSale(sale.getId());
                    BigDecimal balance = sale.getTotal().subtract(totalPaid);
                    if (balance.compareTo(BigDecimal.ZERO) > 0) {
                        totalDebt = totalDebt.add(balance);
                        pendingBatches++;
                    }
                }

                CustomerDebtSummary summary = new CustomerDebtSummary(customer, totalDebt, pendingBatches);

                // Reset sync flag locally since data changed
                String sql = "UPDATE customers SET is_synced = 0 WHERE id = ?";
                try (Connection conn = connect();
                     PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, customerId);
                    pstmt.executeUpdate();
                    conn.commit();
                }

                // Trigger real-time sync
                com.pos.service.SyncService.getInstance().syncDebtorToCloud(summary);
            }
        } catch (SQLException e) {
            logger.error("Error updating debtor balance/sync for {}", customerId, e);
        }
    }
    
    public void markSaleAsSynced(String saleId) throws SQLException {
        String sql = "UPDATE sales SET is_synced = 1 WHERE id = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, saleId);
            pstmt.executeUpdate();
            conn.commit();
        }
    }
    
    public void markSaleItemAsSynced(String itemId) throws SQLException {
        String sql = "UPDATE sale_items SET is_synced = 1 WHERE id = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, itemId);
            pstmt.executeUpdate();
            conn.commit();
        }
    }
    
    public void insertShift(Shift shift) throws SQLException {
        String sql = """
            INSERT INTO shifts (id, user_id, start_time, starting_float, expected_cash,
            mpesa_total, card_total, transaction_count, status, is_synced)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, shift.getId());
            pstmt.setString(2, shift.getUserId());
            pstmt.setString(3, shift.getStartTime().format(DATE_TIME_FORMATTER));
            pstmt.setString(4, shift.getStartingFloat().toPlainString());
            pstmt.setString(5, shift.getExpectedCash().toPlainString());
            pstmt.setString(6, shift.getMpesaTotal().toPlainString());
            pstmt.setString(7, shift.getCardTotal().toPlainString());
            pstmt.setInt(8, shift.getTransactionCount());
            pstmt.setString(9, shift.getStatus().name());
            pstmt.setInt(10, shift.isSynced() ? 1 : 0);
            
            pstmt.executeUpdate();
            conn.commit();
        }
    }
    
    public void updateShift(Shift shift) throws SQLException {
        String sql = """
            UPDATE shifts SET user_id = ?, start_time = ?, end_time = ?, starting_float = ?,
            expected_cash = ?, actual_cash = ?, mpesa_total = ?, card_total = ?,
            transaction_count = ?, status = ?, notes = ?, is_synced = ? WHERE id = ?
        """;
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, shift.getUserId());
            pstmt.setString(2, shift.getStartTime().format(DATE_TIME_FORMATTER));
            pstmt.setString(3, shift.getEndTime() != null ? shift.getEndTime().format(DATE_TIME_FORMATTER) : null);
            pstmt.setString(4, shift.getStartingFloat().toPlainString());
            pstmt.setString(5, shift.getExpectedCash().toPlainString());
            pstmt.setString(6, shift.getActualCash() != null ? shift.getActualCash().toPlainString() : null);
            pstmt.setString(7, shift.getMpesaTotal().toPlainString());
            pstmt.setString(8, shift.getCardTotal().toPlainString());
            pstmt.setInt(9, shift.getTransactionCount());
            pstmt.setString(10, shift.getStatus().name());
            pstmt.setString(11, shift.getNotes());
            pstmt.setInt(12, shift.isSynced() ? 1 : 0);
            pstmt.setString(13, shift.getId());
            
            pstmt.executeUpdate();
            conn.commit();
        }
    }
    
    public Optional<Shift> getOpenShift(String userId) throws SQLException {
        String sql = "SELECT * FROM shifts WHERE user_id = ? AND status = 'OPEN' ORDER BY start_time DESC LIMIT 1";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToShift(rs));
                }
            }
        }
        
        return Optional.empty();
    }
    
    public List<Shift> getAllShifts() throws SQLException {
        List<Shift> shifts = new ArrayList<>();
        String sql = "SELECT * FROM shifts ORDER BY start_time DESC";
        
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                shifts.add(mapResultSetToShift(rs));
            }
        }
        
        return shifts;
    }

    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        return new User(
            rs.getString("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("full_name"),
            User.Role.valueOf(rs.getString("role")),
            rs.getInt("is_active") == 1,
            rs.getInt("is_synced") == 1,
            LocalDateTime.parse(rs.getString("created_at"), DATE_TIME_FORMATTER),
            LocalDateTime.parse(rs.getString("updated_at"), DATE_TIME_FORMATTER)
        );
    }

    private Product mapResultSetToProduct(ResultSet rs) throws SQLException {
        Product p = new Product();
        
        p.setId(rs.getString("id"));
        p.setBarcode(rs.getString("barcode"));
        p.setName(rs.getString("name"));
        p.setDescription(rs.getString("description"));
        p.setCategory(rs.getString("category"));
        
        String retailPriceStr = rs.getString("retail_price");
        String wholesalePriceStr = rs.getString("wholesale_price");
        p.setRetailPrice(retailPriceStr != null ? new BigDecimal(retailPriceStr) : BigDecimal.ZERO);
        p.setWholesalePrice(wholesalePriceStr != null ? new BigDecimal(wholesalePriceStr) : BigDecimal.ZERO);
        
        p.setStockQuantity(rs.getDouble("stock_quantity"));
        p.setMinStockLevel(rs.getDouble("min_stock_level"));
        p.setImagePath(rs.getString("image_path"));
        p.setSupplier(rs.getString("supplier"));
        
        String prodStatus = rs.getString("status");
        p.setStatus(prodStatus != null ? prodStatus : "APPROVED");
        p.setActive(rs.getInt("is_active") == 1);
        p.setSynced(rs.getInt("is_synced") == 1);
        p.setCreatedAt(LocalDateTime.parse(rs.getString("created_at"), DATE_TIME_FORMATTER));
        p.setUpdatedAt(LocalDateTime.parse(rs.getString("updated_at"), DATE_TIME_FORMATTER));
        
        p.setBulkBarcode(rs.getString("bulk_barcode"));
        BigDecimal bulkPriceBd = rs.getBigDecimal("bulk_price");
        if (bulkPriceBd == null || rs.wasNull()) {
            p.setBulkPrice(BigDecimal.ZERO);
        } else {
            p.setBulkPrice(bulkPriceBd);
        }
        int piecesPerBulk = rs.getInt("pieces_per_bulk");
        if (rs.wasNull() || piecesPerBulk <= 0) {
            p.setPiecesPerBulk(1);
        } else {
            p.setPiecesPerBulk(piecesPerBulk);
        }
        
        p.setParentBarcode(rs.getString("parent_barcode"));
        p.setDeductionRatio(rs.getDouble("deduction_ratio"));
        if (rs.wasNull()) {
            p.setDeductionRatio(1.0);
        }
        
        p.setUnitType(rs.getString("unit_type"));
        if (p.getUnitType() == null) {
            p.setUnitType("Pieces");
        }
        
        p.setParentWholesaleBarcode(rs.getString("parent_wholesale_barcode"));
        int yield = rs.getInt("conversion_yield");
        p.setConversionYield(rs.wasNull() ? 0 : yield);
        int looseStock = rs.getInt("loose_remainder_stock");
        p.setLooseRemainderStock(rs.wasNull() ? 0 : looseStock);
        int bundleSize = rs.getInt("bundle_size");
        p.setBundleSize(rs.wasNull() ? 1 : bundleSize);
        int rawYield = rs.getInt("raw_piece_yield");
        p.setRawPieceYield(rs.wasNull() ? 0 : rawYield);
        
        int volumeQty = rs.getInt("volume_qty");
        p.setVolumeQty(rs.wasNull() ? 0 : volumeQty);
        double volumePrice = rs.getDouble("volume_price");
        p.setVolumePrice(rs.wasNull() ? 0.0 : volumePrice);
        
        return p;
    }

    private Sale mapResultSetToSale(ResultSet rs) throws SQLException {
        Sale sale = new Sale();
        sale.setId(rs.getString("id"));
        sale.setUserId(rs.getString("user_id"));
        sale.setCustomerId(rs.getString("customer_id"));
        String paymentStatus = rs.getString("payment_status");
        sale.setPaymentStatus(paymentStatus != null ? paymentStatus : "PAID");
        sale.setSubtotal(new BigDecimal(rs.getString("subtotal")));
        sale.setTaxAmount(new BigDecimal(rs.getString("tax_amount")));
        sale.setDiscountAmount(new BigDecimal(rs.getString("discount_amount")));
        sale.setTotal(new BigDecimal(rs.getString("total")));
        sale.setPaymentMethod(Sale.PaymentMethod.valueOf(rs.getString("payment_method")));
        sale.setAmountPaid(new BigDecimal(rs.getString("amount_paid")));
        sale.setChangeGiven(new BigDecimal(rs.getString("change_given")));
        sale.setStatus(Sale.Status.valueOf(rs.getString("status")));
        sale.setNotes(rs.getString("notes"));
        sale.setSynced(rs.getInt("is_synced") == 1);
        sale.setCreatedAt(LocalDateTime.parse(rs.getString("created_at"), DATE_TIME_FORMATTER));
        sale.setUpdatedAt(LocalDateTime.parse(rs.getString("updated_at"), DATE_TIME_FORMATTER));
        return sale;
    }

    private SaleItem mapResultSetToSaleItem(ResultSet rs) throws SQLException {
        SaleItem item = new SaleItem();
        item.setId(rs.getString("id"));
        item.setSaleId(rs.getString("sale_id"));
        item.setProductId(rs.getString("product_id"));
        item.setProductName(rs.getString("product_name"));
        item.setProductBarcode(rs.getString("product_barcode"));
        item.setQuantity(rs.getInt("quantity"));
        item.setUnitPrice(new BigDecimal(rs.getString("unit_price")));
        item.setTotalPrice(new BigDecimal(rs.getString("total_price")));
        item.setSynced(rs.getInt("is_synced") == 1);
        item.setCreatedAt(LocalDateTime.parse(rs.getString("created_at"), DATE_TIME_FORMATTER));
        try {
            item.setUnitCogs(rs.getDouble("unit_cogs"));
        } catch (Exception e) {
            item.setUnitCogs(0.0);
        }
        return item;
    }
    
    private Shift mapResultSetToShift(ResultSet rs) throws SQLException {
        Shift shift = new Shift();
        shift.setId(rs.getString("id"));
        shift.setUserId(rs.getString("user_id"));
        
        String startTimeStr = rs.getString("start_time");
        if (startTimeStr != null) {
            shift.setStartTime(LocalDateTime.parse(startTimeStr, DATE_TIME_FORMATTER));
        }
        
        String endTimeStr = rs.getString("end_time");
        if (endTimeStr != null) {
            shift.setEndTime(LocalDateTime.parse(endTimeStr, DATE_TIME_FORMATTER));
        }
        
        shift.setStartingFloat(new BigDecimal(rs.getString("starting_float")));
        
        String expectedCash = rs.getString("expected_cash");
        shift.setExpectedCash(expectedCash != null ? new BigDecimal(expectedCash) : BigDecimal.ZERO);
        
        String actualCash = rs.getString("actual_cash");
        shift.setActualCash(actualCash != null ? new BigDecimal(actualCash) : null);
        
        String mpesaTotal = rs.getString("mpesa_total");
        shift.setMpesaTotal(mpesaTotal != null ? new BigDecimal(mpesaTotal) : BigDecimal.ZERO);
        
        String cardTotal = rs.getString("card_total");
        shift.setCardTotal(cardTotal != null ? new BigDecimal(cardTotal) : BigDecimal.ZERO);
        
        shift.setTransactionCount(rs.getInt("transaction_count"));
        shift.setStatus(Shift.Status.valueOf(rs.getString("status")));
        shift.setNotes(rs.getString("notes"));
        shift.setSynced(rs.getInt("is_synced") == 1);
        
        return shift;
    }
    
    private ActivityLog mapResultSetToActivityLog(ResultSet rs) throws SQLException {
        return new ActivityLog(
            rs.getString("id"),
            rs.getString("user_id"),
            rs.getString("user_name"),
            ActivityLog.ActionType.valueOf(rs.getString("action_type")),
            rs.getString("target_description"),
            rs.getString("details"),
            rs.getInt("is_synced") == 1,
            LocalDateTime.parse(rs.getString("created_at"), DATE_TIME_FORMATTER)
        );
    }
    
    public void insertActivityLog(ActivityLog log) throws SQLException {
        try (Connection conn = connect()) {
            insertActivityLog(conn, log);
            conn.commit();
        }
    }
    
    private void insertActivityLog(Connection conn, ActivityLog log) throws SQLException {
        String sql = """
            INSERT INTO activity_logs (id, user_id, user_name, action_type, target_description, details, is_synced, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, log.getId());
            pstmt.setString(2, log.getUserId());
            pstmt.setString(3, log.getUserName());
            pstmt.setString(4, log.getActionType().name());
            pstmt.setString(5, log.getTargetDescription());
            pstmt.setString(6, log.getDetails());
            pstmt.setInt(7, log.isSynced() ? 1 : 0);
            pstmt.setString(8, log.getCreatedAt().format(DATE_TIME_FORMATTER));
            
            pstmt.executeUpdate();
        }
    }
    
    public List<ActivityLog> getRecentActivityLogs(int limit) throws SQLException {
        List<ActivityLog> logs = new ArrayList<>();
        String sql = "SELECT * FROM activity_logs ORDER BY created_at DESC LIMIT ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapResultSetToActivityLog(rs));
                }
            }
        }
        
        return logs;
    }
    
    public List<ActivityLog> getUnsyncedActivityLogs() throws SQLException {
        List<ActivityLog> logs = new ArrayList<>();
        String sql = "SELECT * FROM activity_logs WHERE is_synced = 0";
        
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                logs.add(mapResultSetToActivityLog(rs));
            }
        }
        
        return logs;
    }
    
    public void markActivityLogAsSynced(String logId) throws SQLException {
        String sql = "UPDATE activity_logs SET is_synced = 1 WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, logId);
            pstmt.executeUpdate();
            conn.commit();
        }
    }

    public int getUnsyncedProductsCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM products WHERE is_synced = 0";
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    public List<Product> getUnsyncedProducts() throws SQLException {
        List<Product> products = new ArrayList<>();
        String sql = "SELECT * FROM products WHERE is_synced = 0";
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                products.add(mapResultSetToProduct(rs));
            }
        }
        return products;
    }

    public void markProductAsSynced(String id) throws SQLException {
        String sql = "UPDATE products SET is_synced = 1 WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.executeUpdate();
            conn.commit();
        }
    }

    
    /**
     * Persists a supplier stock-in: header, line items, increments {@code products.stock_quantity} per line,
     * and optionally writes an activity log row — all in one database transaction.
     */
    public void processStockIn(SupplierTransaction trans, List<SupplierTransactionItem> items, ActivityLog activityLog) throws SQLException {
        if (trans == null || trans.getId() == null || trans.getId().isBlank()) {
            throw new IllegalArgumentException("Supplier transaction id is required");
        }
        if (trans.getSupplierName() == null || trans.getSupplierName().isBlank()) {
            throw new IllegalArgumentException("Supplier name is required");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("At least one stock-in line item is required");
        }
        String st = trans.getStatus();
        if (!SupplierTransaction.STATUS_PAID.equals(st) && !SupplierTransaction.STATUS_CREDIT.equals(st)) {
            throw new IllegalArgumentException("status must be PAID or CREDIT");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (SupplierTransactionItem it : items) {
            if (it.getProductId() == null || it.getProductId().isBlank()) {
                throw new IllegalArgumentException("Each line must reference a product");
            }
            if (it.getQuantityReceived() <= 0) {
                throw new IllegalArgumentException("Quantity received must be positive");
            }
            if (it.getBuyingPrice() == null || it.getBuyingPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Buying price must be zero or positive");
            }
            total = total.add(it.getBuyingPrice().multiply(BigDecimal.valueOf(it.getQuantityReceived())));
        }
        trans.setTotalCost(total);
        if (trans.getCreatedAt() == null) {
            trans.setCreatedAt(LocalDateTime.now());
        }
        
        String insertTx = """
            INSERT INTO supplier_transactions (id, supplier_name, total_cost, status, created_at, payment_source, debtor_id, debtor_offset, cash_paid, is_synced)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        String insertLine = """
            INSERT INTO supplier_transaction_items (id, transaction_id, product_id, quantity_received, buying_price)
            VALUES (?, ?, ?, ?, ?)
            """;
        
        try (Connection conn = connect();
             PreparedStatement pstmtTx = conn.prepareStatement(insertTx);
             PreparedStatement pstmtLine = conn.prepareStatement(insertLine)) {
            
            pstmtTx.setString(1, trans.getId());
            pstmtTx.setString(2, trans.getSupplierName().trim());
            pstmtTx.setDouble(3, trans.getTotalCost().doubleValue());
            pstmtTx.setString(4, st);
            pstmtTx.setString(5, trans.getCreatedAt().format(DATE_TIME_FORMATTER));
            pstmtTx.setString(6, trans.getPaymentSource() != null ? trans.getPaymentSource() : "Cash (From Shelf)");
            pstmtTx.setString(7, trans.getDebtorId());
            pstmtTx.setDouble(8, trans.getDebtorOffset().doubleValue());
            pstmtTx.setDouble(9, trans.getCashPaid().doubleValue());
            pstmtTx.setInt(10, trans.isSynced() ? 1 : 0);
            pstmtTx.executeUpdate();
            
            for (SupplierTransactionItem it : items) {
                if (it.getId() == null || it.getId().isBlank()) {
                    it.setId(java.util.UUID.randomUUID().toString());
                }
                it.setTransactionId(trans.getId());
                pstmtLine.setString(1, it.getId());
                pstmtLine.setString(2, trans.getId());
                pstmtLine.setString(3, it.getProductId());
                pstmtLine.setDouble(4, it.getQuantityReceived());
                pstmtLine.setDouble(5, it.getBuyingPrice().doubleValue());
                pstmtLine.executeUpdate();
            }
            
            processStockIn(conn, items);
            
            if (activityLog != null) {
                insertActivityLog(conn, activityLog);
            }
            
            conn.commit();

            // Trigger real-time sync for the transaction
            com.pos.service.SyncService.getInstance().syncSupplierTransaction(trans);
        }
    }
    
    private void processStockIn(Connection conn, List<SupplierTransactionItem> items) throws SQLException {
        for (SupplierTransactionItem item : items) {
            updateProductStock(conn, item.getProductId(), item.getQuantityReceived());
        }
    }
    
    public void clearSupplierPayment(String transactionId) throws SQLException {
        String sql = "UPDATE supplier_transactions SET status = ? WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, SupplierTransaction.STATUS_PAID);
            pstmt.setString(2, transactionId);
            pstmt.executeUpdate();
            conn.commit();
        }
    }
    
    public List<SupplierTransaction> getAllSupplierTransactions() throws SQLException {
        List<SupplierTransaction> list = new ArrayList<>();
        String sql = "SELECT id, supplier_name, total_cost, status, created_at FROM supplier_transactions ORDER BY datetime(created_at) DESC";
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToSupplierTransaction(rs));
            }
        }
        return list;
    }
    
    public List<String> getDistinctSupplierNamesFromProducts() throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = """
            SELECT DISTINCT supplier FROM products
            WHERE supplier IS NOT NULL AND TRIM(supplier) != ''
            ORDER BY supplier COLLATE NOCASE
            """;
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }
    
    public List<String> getAllCategories() throws SQLException {
        List<String> categories = new ArrayList<>();
        categories.add("General");
        categories.add("Bakery");
        categories.add("Dairy");
        categories.add("Beverages");
        categories.add("Cooking");
        categories.add("Baking");
        categories.add("Staples");
        categories.add("Household");
        categories.add("Personal Care");
        categories.add("Produce");
        categories.add("Frozen");
        categories.add("Snacks");
        
        String sql = "SELECT DISTINCT category FROM products WHERE category IS NOT NULL AND TRIM(category) != '' ORDER BY category COLLATE NOCASE";
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String cat = rs.getString(1);
                if (!categories.contains(cat)) {
                    categories.add(cat);
                }
            }
        }
        return categories;
    }
    
    private SupplierTransaction mapResultSetToSupplierTransaction(ResultSet rs) throws SQLException {
        BigDecimal total = BigDecimal.valueOf(rs.getDouble("total_cost"));
        if (rs.wasNull()) {
            total = BigDecimal.ZERO;
        }
        SupplierTransaction st = new SupplierTransaction(
            rs.getString("id"),
            rs.getString("supplier_name"),
            total,
            rs.getString("status"),
            LocalDateTime.parse(rs.getString("created_at"), DATE_TIME_FORMATTER)
        );
        try { st.setPaymentSource(rs.getString("payment_source")); } catch (SQLException e) {}
        try { st.setDebtorId(rs.getString("debtor_id")); } catch (SQLException e) {}
        try { st.setDebtorOffset(BigDecimal.valueOf(rs.getDouble("debtor_offset"))); } catch (SQLException e) {}
        try { st.setCashPaid(BigDecimal.valueOf(rs.getDouble("cash_paid"))); } catch (SQLException e) {}
        try { st.setSynced(rs.getInt("is_synced") == 1); } catch (SQLException e) {}
        return st;
    }
    
    public Optional<Customer> findCustomerById(String id) throws SQLException {
        String sql = "SELECT * FROM customers WHERE id = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToCustomer(rs));
                }
            }
        }
        
        return Optional.empty();
    }

    public List<Customer> getAllCustomers() throws SQLException {
        List<Customer> customers = new ArrayList<>();
        String sql = "SELECT * FROM customers ORDER BY name";
        
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                customers.add(mapResultSetToCustomer(rs));
            }
        }
        
        return customers;
    }

    /**
     * Reduces a debtor's outstanding balance by recording a payment against their
     * oldest unpaid sale(s). Called when a stock-in is paid via "Debt (Offset Account)".
     *
     * @param debtorId   The customer ID whose balance is being offset
     * @param amount     The amount to reduce (the total cost of the shipment)
     * @throws SQLException if any DB operation fails
     */
    public void reduceDebtorBalance(String debtorId, BigDecimal amount) throws SQLException {
        if (debtorId == null || debtorId.isBlank() || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        // Fetch unpaid sales for this customer, ordered oldest first
        List<Sale> unpaidSales = getUnpaidSalesForCustomer(debtorId);
        BigDecimal remaining = amount;

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(
                 "INSERT INTO customer_payments (id, sale_id, amount_paid, payment_date) VALUES (?, ?, ?, ?)")) {

            for (Sale sale : unpaidSales) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

                BigDecimal totalPaid = getTotalPaidForSale(sale.getId());
                BigDecimal balance   = sale.getTotal().subtract(totalPaid);
                if (balance.compareTo(BigDecimal.ZERO) <= 0) continue;

                BigDecimal apply = remaining.min(balance);
                pstmt.setString(1, java.util.UUID.randomUUID().toString());
                pstmt.setString(2, sale.getId());
                pstmt.setDouble(3, apply.doubleValue());
                pstmt.setString(4, LocalDateTime.now().format(DATE_TIME_FORMATTER));
                pstmt.executeUpdate();
                remaining = remaining.subtract(apply);
            }
            conn.commit();
            logger.info("Reduced debtor {} balance by {} (KSh), {} remaining unallocated",
                    debtorId, amount, remaining);
            
            // Trigger sync
            updateDebtorBalance(debtorId);
        }
    }

    /**
     * Returns the customer's live outstanding balance — computed fresh from the DB.
     * Always accurate because it sums all unpaid sale amounts minus all recorded payments.
     */
    public BigDecimal getCustomerCurrentBalance(String customerId) throws SQLException {
        List<Sale> unpaid = getUnpaidSalesForCustomer(customerId);
        BigDecimal balance = BigDecimal.ZERO;
        for (Sale s : unpaid) {
            BigDecimal paid = getTotalPaidForSale(s.getId());
            BigDecimal due  = s.getTotal().subtract(paid);
            if (due.compareTo(BigDecimal.ZERO) > 0) balance = balance.add(due);
        }
        return balance;
    }

    /**
     * Persists an updated credit limit for a customer. 0 = unlimited.
     */
    public void updateCustomerCreditLimit(String customerId, double newLimit) throws SQLException {
        String sql = "UPDATE customers SET credit_limit = ? WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newLimit);
            ps.setString(2, customerId);
            ps.executeUpdate();
            conn.commit();
            logger.info("Credit limit for customer {} set to KSh {}", customerId, newLimit);
        }
    }

    // -------------------------------------------------------------------------
    // PARTIAL DEBT OFFSET  (split: some debt cleared, rest paid in cash)
    // -------------------------------------------------------------------------
    /**
     * Processes a split shipment payment:
     * <ul>
     *   <li>Reduces the debtor's outstanding balance by {@code debtorOffset}</li>
     *   <li>Records {@code cashPaid = totalCost - debtorOffset} as the cash portion</li>
     *   <li>Updates the supplier_transaction row with the split breakdown</li>
     * </ul>
     *
     * @param transactionId   the already-inserted supplier_transaction row id
     * @param debtorId        customer id whose balance is being reduced
     * @param debtorOffset    amount cleared against the debtor's balance
     * @param totalCost       full shipment value
     * @throws SQLException   if any DB operation fails
     */
    public void processPartialDebtOffset(String transactionId, String debtorId,
                                         BigDecimal debtorOffset, BigDecimal totalCost)
            throws SQLException {

        if (debtorOffset == null || debtorOffset.compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("processPartialDebtOffset called with zero/null offset — nothing to do");
            return;
        }
        if (debtorOffset.compareTo(totalCost) > 0) {
            throw new IllegalArgumentException(
                "Debt offset (" + debtorOffset + ") cannot exceed total shipment cost (" + totalCost + ")");
        }

        BigDecimal cashPaid = totalCost.subtract(debtorOffset);

        // 1. Reduce the debtor's unpaid sales balances
        reduceDebtorBalance(debtorId, debtorOffset);

        // 2. Stamp the split figures on the transaction row
        String sql = "UPDATE supplier_transactions SET debtor_offset = ?, cash_paid = ? WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, debtorOffset.doubleValue());
            ps.setDouble(2, cashPaid.doubleValue());
            ps.setString(3, transactionId);
            ps.executeUpdate();
            conn.commit();
        }

        logger.info("Partial debt offset applied: transactionId={}, debtorOffset=KSh {}, cashPaid=KSh {}",
                transactionId, debtorOffset, cashPaid);
    }

    public List<Sale> getUnpaidSalesForCustomer(String customerId) throws SQLException {
        List<Sale> sales = new ArrayList<>();
        // Join with customer_payments to get current total paid so far
        String sql = """
            SELECT s.*, 
                   (SELECT COALESCE(SUM(amount_paid), 0) FROM customer_payments WHERE sale_id = s.id) as ledger_paid
            FROM sales s 
            WHERE s.customer_id = ? AND s.payment_status = 'CREDIT' 
            ORDER BY s.created_at ASC
            """;
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, customerId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Sale sale = mapResultSetToSale(rs);
                    // Update sale.amountPaid to reflect TOTAL paid so far (initial + ledger)
                    BigDecimal ledgerPaid = BigDecimal.valueOf(rs.getDouble("ledger_paid"));
                    sale.setAmountPaid(sale.getAmountPaid().add(ledgerPaid));
                    
                    sale.setItems(getSaleItems(sale.getId()));
                    sales.add(sale);
                }
            }
        }
        
        return sales;
    }

    public BigDecimal getTotalPaidForSale(String saleId) throws SQLException {
        String sql = """
            SELECT (CAST(s.amount_paid AS REAL) + COALESCE((SELECT SUM(amount_paid) FROM customer_payments WHERE sale_id = s.id), 0)) as total_paid
            FROM sales s
            WHERE s.id = ?
            """;
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, saleId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return BigDecimal.valueOf(rs.getDouble(1));
                }
            }
        }
        return BigDecimal.ZERO;
    }

    public boolean recordCustomerPayment(String saleId, BigDecimal paymentAmount) {
        try (Connection conn = connect()) {
            // 1. Insert the payment record
            insertCustomerPayment(conn, saleId, paymentAmount);
            
            // 2. Query the database for the TRUE sum of all payments (initial + all ledger payments)
            BigDecimal totalPaid = getTotalPaidForSaleInternal(conn, saleId);
            
            // 3. Get the sale's total amount
            BigDecimal totalSaleAmount = BigDecimal.ZERO;
            String fetchSql = "SELECT total FROM sales WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(fetchSql)) {
                pstmt.setString(1, saleId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        totalSaleAmount = new BigDecimal(rs.getString("total"));
                    }
                }
            }
            
            // 4. Update status to PAID only if fully covered
            if (totalPaid.compareTo(totalSaleAmount) >= 0) {
                String updateSql = "UPDATE sales SET payment_status = 'PAID', updated_at = ? WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                    pstmt.setString(1, LocalDateTime.now().format(DATE_TIME_FORMATTER));
                    pstmt.setString(2, saleId);
                    pstmt.executeUpdate();
                }
            }
            
            conn.commit();
            return true;
        } catch (SQLException e) {
            logger.error("Database error recording customer payment for sale: {}", saleId, e);
            return false;
        }
    }

    private BigDecimal getTotalPaidForSaleInternal(Connection conn, String saleId) throws SQLException {
        String sql = """
            SELECT (CAST(s.amount_paid AS REAL) + COALESCE((SELECT SUM(amount_paid) FROM customer_payments WHERE sale_id = s.id), 0)) as total_paid
            FROM sales s
            WHERE s.id = ?
            """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, saleId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return BigDecimal.valueOf(rs.getDouble(1));
                }
            }
        }
        return BigDecimal.ZERO;
    }

    public void insertCustomer(Customer customer) throws SQLException {
        String sql = """
            INSERT INTO customers (id, name, phone, created_at, credit_limit, is_synced)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, customer.getId());
            pstmt.setString(2, customer.getName());
            pstmt.setString(3, customer.getPhone());
            pstmt.setString(4, customer.getCreatedAt().format(DATE_TIME_FORMATTER));
            pstmt.setDouble(5, customer.getCreditLimit());
            pstmt.setInt(6, customer.isSynced() ? 1 : 0);

            pstmt.executeUpdate();
            conn.commit();
        }
    }

    private Customer mapResultSetToCustomer(ResultSet rs) throws SQLException {
        Customer c = new Customer(
            rs.getString("id"),
            rs.getString("name"),
            rs.getString("phone"),
            LocalDateTime.parse(rs.getString("created_at"), DATE_TIME_FORMATTER),
            rs.getInt("is_synced") == 1
        );
        try { c.setCreditLimit(rs.getDouble("credit_limit")); } catch (SQLException ignored) {}
        return c;
    }

    public void ensureSupplierExists(String supplierName) {
        if (supplierName == null || supplierName.trim().isEmpty()) return;
        String sql = "INSERT OR IGNORE INTO suppliers (name) VALUES (?)";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, supplierName.trim());
            pstmt.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            logger.error("Failed to ensure supplier exists: {}", supplierName, e);
        }
    }

    public boolean addSupplier(String supplierName) throws SQLException {
        if (supplierName == null || supplierName.trim().isEmpty()) {
            return false;
        }
        String name = supplierName.trim();
        
        try (Connection conn = connect()) {
            // Case-insensitive check
            String checkSql = "SELECT COUNT(*) FROM suppliers WHERE name = ? COLLATE NOCASE";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, name);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return false; // Already exists
                    }
                }
            }
            
            String insertSql = "INSERT INTO suppliers (name) VALUES (?)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, name);
                insertStmt.executeUpdate();
            }
            
            conn.commit();
            return true;
        }
    }

    public List<String> getAllSupplierNames() throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = "SELECT name FROM suppliers ORDER BY name";
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        }
        return names;
    }

    public boolean attemptAutoConversion(String barcode) {
        try {
            Product child = this.getProduct(barcode);
            if (child == null) return false;

            // Portions JIT conversion logic (Half / Quarter)
            if (child.getParentBarcode() != null && !child.getParentBarcode().isEmpty() && child.getDeductionRatio() < 1.0) {
                Product parent = this.getProduct(child.getParentBarcode());
                if (parent == null) return false;

                // RECURSION: If the parent (e.g. Single Sugar) has less than 1.0 unit in stock, try to auto-convert the parent first!
                if (parent.getStock() < 1.0) {
                    boolean parentConverted = attemptAutoConversion(parent.getBarcode());
                    if (!parentConverted) {
                        return false; // The parent supply chain is dry
                    }
                    // Reload parent to get its newly converted stock
                    parent = this.getProduct(parent.getBarcode());
                }

                // Proceed with converting 1 unit of Parent into child Portions
                if (parent.getStock() >= 1.0) {
                    // Deduct exactly 1 unit from the parent
                    this.updateStock(parent.getBarcode(), parent.getStock() - 1.0);

                    // Yield is calculated based on deduction ratio
                    int yield = (int) Math.round(1.0 / child.getDeductionRatio());
                    if (yield <= 0) yield = 1;

                    // Apply the updates to the child (Portion item)
                    this.updateStock(child.getBarcode(), child.getStock() + yield);

                    // Log activity
                    this.logActivity("AUTO-RESTOCK (PORTION JIT)", "Opened 1 " + parent.getName() + ". Generated " + yield + " " + child.getName());
                    return true;
                }
                return false;
            }

            // Normal JIT packaging tier conversion logic (Carton -> Box -> Packet -> Single)
            String parentBarcode = child.getParentWholesaleBarcode();
            if (parentBarcode == null || parentBarcode.isEmpty()) {
                parentBarcode = child.getParentBarcode();
            }
            if (parentBarcode == null || parentBarcode.isEmpty()) {
                return false; // Not a linked JIT packaging item
            }

            Product parent = this.getProduct(parentBarcode);
            if (parent == null) return false;

            // RECURSION: If the parent has less than 1.0 unit in stock, try to auto-convert the parent first!
            if (parent.getStock() < 1.0) {
                boolean parentConverted = attemptAutoConversion(parent.getBarcode());
                if (!parentConverted) {
                    return false; // The entire supply chain is empty
                }
                // Reload the parent to get its newly converted stock
                parent = this.getProduct(parent.getBarcode());
            }

            // Proceed with converting the Parent into the Child
            if (parent.getStock() >= 1.0) {
                // 1. Deduct exactly 1 unit from the parent (e.g., open 1 Packet)
                this.updateStock(parent.getBarcode(), parent.getStock() - 1.0);

                // 2. Retrieve variables (Fallback to child's conversion yield, then to 1 to prevent division by zero)
                int bundleSize = child.getBundleSize() > 0 ? child.getBundleSize() : 1;
                int rawYield = parent.getRawPieceYield(); 
                if (rawYield <= 0) {
                    rawYield = child.getConversionYield();
                }
                if (rawYield <= 0) {
                    rawYield = 1;
                }
                int currentRemainder = child.getLooseRemainderStock();

                // 3. The Dynamic JIT Math (Combine new pieces with the existing jar leftovers)
                int totalAvailablePieces = rawYield + currentRemainder;

                int newBundles = totalAvailablePieces / bundleSize;     // e.g., 72 / 4 = 18
                int newRemainder = totalAvailablePieces % bundleSize;   // e.g., 72 % 4 = 0

                // 4. Apply the updates to the child (Retail Item)
                this.updateStock(child.getBarcode(), child.getStock() + newBundles);
                this.updateRemainder(child.getBarcode(), newRemainder);

                // 5. Log the intelligent restock
                this.logActivity("AUTO-RESTOCK (JIT)", "Opened 1 " + parent.getName() + ". Generated " + newBundles + " bundles. Remainder rolled over: " + newRemainder);
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error attempting auto conversion for barcode: " + barcode, e);
        }
        return false;
    }

    public Product findImmediateChildProduct(String parentBarcode) {
        if (parentBarcode == null || parentBarcode.trim().isEmpty()) {
            return null;
        }
        String sql = "SELECT * FROM products WHERE (parent_wholesale_barcode = ? OR parent_barcode = ?) AND is_active = 1 LIMIT 1";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, parentBarcode);
            pstmt.setString(2, parentBarcode);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToProduct(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding immediate child product for parent: " + parentBarcode, e);
        }
        return null;
    }

    public void logActivity(String type, String message) {
        logDiscrepancy("", type, message);
    }

    public void logDiscrepancy(String barcode, String type, String message) {
        String sql = "INSERT INTO system_logs (id, barcode, type, message, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, java.util.UUID.randomUUID().toString());
            pstmt.setString(2, barcode);
            pstmt.setString(3, type);
            pstmt.setString(4, message);
            pstmt.setString(5, LocalDateTime.now().format(DATE_TIME_FORMATTER));
            pstmt.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            logger.error("Error logging discrepancy: " + message, e);
        }
    }

    public Product getProduct(String barcode) throws SQLException {
        return findProductByBarcode(barcode).orElse(null);
    }

    /**
     * Traces down the hierarchy from a given barcode to find the base retail item (Tier 1).
     * If a Tier 3 barcode is passed, it traces to Tier 2 and then Tier 1.
     * If a Tier 2 barcode is passed, it traces to Tier 1.
     */
    public Product findBaseRetailItem(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            return null;
        }
        try {
            Product current = getProduct(barcode);
            if (current == null) {
                return null;
            }
            while (true) {
                // Find a product that has current.getBarcode() as its parent_wholesale_barcode
                String sql = "SELECT * FROM products WHERE parent_wholesale_barcode = ? AND is_active = 1 AND (deduction_ratio IS NULL OR deduction_ratio >= 1.0) LIMIT 1";
                try (Connection conn = connect();
                     PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, current.getBarcode());
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            current = mapResultSetToProduct(rs);
                        } else {
                            break;
                        }
                    }
                }
            }
            return current;
        } catch (SQLException e) {
            logger.error("Error finding base retail item for barcode: " + barcode, e);
            return null;
        }
    }

    public void updateStock(String barcode, double newStock) throws SQLException {
        Optional<Product> pOpt = findProductByBarcode(barcode);
        if (pOpt.isPresent()) {
            updateProductStock(pOpt.get().getId(), newStock);
        }
    }

    public void updateRemainder(String barcode, int newRemainder) throws SQLException {
        Optional<Product> pOpt = findProductByBarcode(barcode);
        if (pOpt.isPresent()) {
            String sql = "UPDATE products SET loose_remainder_stock = ?, updated_at = ? WHERE id = ?";
            try (Connection conn = connect();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, newRemainder);
                pstmt.setString(2, LocalDateTime.now().format(DATE_TIME_FORMATTER));
                pstmt.setString(3, pOpt.get().getId());
                pstmt.executeUpdate();
                conn.commit();
            }
        }
    }

    public void close() {
        logger.info("Database manager closed");
    }

    public String determineTierLabel(Product p) {
        if (p == null) return "Unknown";
        String name = p.getName();
        if (name != null) {
            if (name.contains("(Master Carton)")) return "Carton";
            if (name.contains("(Master Box)")) return "Box";
            if (name.contains("(Packet)")) return "Packet";
            if (name.contains("(Single)")) return "Piece";
        }
        if (p.getParentBarcode() == null || p.getParentBarcode().isEmpty()) {
            return "Master Top-Level Unit";
        }
        return "Piece (Retail)";
    }

    public void incrementStock(String barcode, double quantityReceived) throws SQLException {
        Optional<Product> pOpt = findProductByBarcode(barcode);
        if (pOpt.isPresent()) {
            updateProductStock(pOpt.get().getId(), pOpt.get().getStockQuantity() + quantityReceived);
        }
    }

    public Double getLastRestockCost(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return null;
        }
        String sql = """
            SELECT sti.buying_price 
            FROM supplier_transaction_items sti
            JOIN products p ON sti.product_id = p.id
            JOIN supplier_transactions st ON sti.transaction_id = st.id
            WHERE p.barcode = ?
            ORDER BY datetime(st.created_at) DESC
            LIMIT 1
            """;
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, barcode);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("buying_price");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting last restock cost for barcode: " + barcode, e);
        }
        return null;
    }

    public void performAutomatedBackup() {
        try {
            String installDir = System.getProperty("user.dir");
            File backupFolder = new File(installDir, "backups");
            if (!backupFolder.exists()) {
                boolean created = backupFolder.mkdir();
                if (created) {
                    System.out.println("Created backups directory: " + backupFolder.getAbsolutePath());
                }
            }
            
            if (this.dbPath == null) {
                System.err.println("Database path is null, cannot perform backup.");
                return;
            }
            
            File dbFile = new File(this.dbPath);
            if (!dbFile.exists()) {
                System.err.println("Database file does not exist at " + this.dbPath + ", cannot perform backup.");
                return;
            }
            
            String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String backupFileName = "vegas_pos_backup_" + timestamp + ".db";
            File backupFile = new File(backupFolder, backupFileName);
            
            java.nio.file.Files.copy(dbFile.toPath(), backupFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Automated database backup completed successfully: " + backupFile.getAbsolutePath());
            
            // Clean up backups older than 10 days
            cleanOldBackups(backupFolder, 10);
        } catch (Exception e) {
            System.err.println("Failed to perform automated database backup: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void cleanOldBackups(java.io.File backupDir, int retentionDays) {
        try {
            java.time.Instant cutoff = java.time.Instant.now().minus(retentionDays, java.time.temporal.ChronoUnit.DAYS);
            
            java.nio.file.Files.list(backupDir.toPath())
                .filter(path -> path.toString().endsWith(".db") || path.toString().endsWith(".sqlite"))
                .filter(path -> {
                    try {
                        return java.nio.file.Files.getLastModifiedTime(path).toInstant().isBefore(cutoff);
                    } catch (java.io.IOException e) { 
                        return false; 
                    }
                })
                .forEach(path -> {
                    try {
                        java.nio.file.Files.delete(path);
                        System.out.println("Cleaned up old backup: " + path.getFileName());
                    } catch (java.io.IOException e) {
                        System.err.println("Failed to delete old backup: " + path.getFileName());
                    }
                });
        } catch (java.io.IOException e) {
            System.err.println("Error reading backup directory for cleanup: " + e.getMessage());
        }
    }
}
