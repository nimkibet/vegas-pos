package com.pos.database;

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
        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            ensureSupplierStockInTables(stmt);
            conn.commit();
        }
        if (!isInitialized) {
            initializeSchema();
            // IMPORTANT: seedDummyData must be called AFTER initializeSchema() returns
            // to ensure the first connection is fully closed before seeding starts
            seedDummyData();
            isInitialized = true;
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
                    pieces_per_bulk INTEGER DEFAULT 1
                )
            """);
            
            ensureProductBulkColumns(stmt);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sales (
                    id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL,
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
    }
    
    /**
     * Adds bulk/box columns to products when upgrading an existing database
     * (CREATE TABLE IF NOT EXISTS does not add new columns to old tables).
     */
    private void ensureProductBulkColumns(Statement stmt) {
        String[] alters = {
            "ALTER TABLE products ADD COLUMN bulk_barcode TEXT",
            "ALTER TABLE products ADD COLUMN bulk_price REAL DEFAULT 0.0",
            "ALTER TABLE products ADD COLUMN pieces_per_bulk INTEGER DEFAULT 1"
        };
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
            
            // Check and seed products
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM products")) {
                rs.next();
                if (rs.getInt(1) == 0) {
                    logger.info("Seeding default products...");
                    
                    String[][] seedProducts = {
                        {"Bread", "Loaf Bread", "Bakery", "50.00", "45.00", "50"},
                        {"Milk", "Fresh Milk 500ml", "Dairy", "60.00", "55.00", "100"},
                        {"Soda", "Soft Drink 500ml", "Beverages", "40.00", "35.00", "200"},
                        {"Cooking Oil", "Cooking Oil 1L", "Cooking", "120.00", "110.00", "75"},
                        {"Sugar", "Sugar 1kg", "Baking", "100.00", "90.00", "60"},
                        {"Rice", "Rice 2kg", "Staples", "180.00", "165.00", "80"},
                        {"Tea Bags", "Tea Bags 50s", "Beverages", "80.00", "72.00", "120"},
                        {"Soap", "Bar Soap", "Household", "30.00", "25.00", "150"},
                        {"Toothpaste", "Toothpaste", "Personal Care", "70.00", "60.00", "90"},
                        {"Maize Flour", "Maize Flour 2kg", "Staples", "120.00", "108.00", "55"}
                    };
                    
                    try (PreparedStatement pstmt = conn.prepareStatement(
                            "INSERT INTO products (id, barcode, name, description, category, retail_price, wholesale_price, stock_quantity, min_stock_level, supplier, status, is_active, is_synced, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                        
                        for (int i = 0; i < seedProducts.length; i++) {
                            String[] p = seedProducts[i];
                            pstmt.setString(1, "550e8400-e29b-41d4-a716-4466554401" + String.format("%02d", i));
                            pstmt.setString(2, "5000000" + String.format("%02d", i + 1));
                            pstmt.setString(3, p[0]);
                            pstmt.setString(4, p[1]);
                            pstmt.setString(5, p[2]);
                            pstmt.setString(6, p[3]);
                            pstmt.setString(7, p[4]);
                            pstmt.setInt(8, Integer.parseInt(p[5]));
                            pstmt.setInt(9, 10);
                            pstmt.setString(10, "Default Supplier");
                            pstmt.setString(11, "APPROVED");
                            pstmt.setInt(12, 1);
                            pstmt.setInt(13, 0);
                            pstmt.setString(14, "2024-01-01 00:00:00");
                            pstmt.setString(15, "2024-01-01 00:00:00");
                            pstmt.executeUpdate();
                        }
                    }
                    
                    logger.info("Default products seeded");
                }
            }
            
            conn.commit();
            
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
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
            bulk_barcode, bulk_price, pieces_per_bulk)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            pstmt.setInt(8, product.getStockQuantity());
            pstmt.setInt(9, product.getMinStockLevel());
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
            
            pstmt.executeUpdate();
            conn.commit();
        }
    }

    public void updateProduct(Product product) throws SQLException {
        String sql = """
            UPDATE products SET barcode = ?, name = ?, description = ?, category = ?,
            retail_price = ?, wholesale_price = ?, stock_quantity = ?, min_stock_level = ?,
            image_path = ?, supplier = ?, status = ?, is_active = ?, is_synced = ?,
            bulk_barcode = ?, bulk_price = ?, pieces_per_bulk = ?, updated_at = ? WHERE id = ?
        """;
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, product.getBarcode());
            pstmt.setString(2, product.getName());
            pstmt.setString(3, product.getDescription());
            pstmt.setString(4, product.getCategory());
            pstmt.setString(5, product.getRetailPrice().toPlainString());
            pstmt.setString(6, product.getWholesalePrice().toPlainString());
            pstmt.setInt(7, product.getStockQuantity());
            pstmt.setInt(8, product.getMinStockLevel());
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
            pstmt.setString(17, LocalDateTime.now().format(DATE_TIME_FORMATTER));
            pstmt.setString(18, product.getId());
            
            pstmt.executeUpdate();
            conn.commit();
        }
    }

    public void updateProductStock(String productId, int newQuantity) throws SQLException {
        String sql = "UPDATE products SET stock_quantity = ?, updated_at = ? WHERE id = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, newQuantity);
            pstmt.setString(2, LocalDateTime.now().format(DATE_TIME_FORMATTER));
            pstmt.setString(3, productId);
            
            pstmt.executeUpdate();
            conn.commit();
        }
    }
    
    void updateProductStock(Connection conn, String productId, int quantityDelta) throws SQLException {
        String sql = "UPDATE products SET stock_quantity = stock_quantity + ?, updated_at = ? WHERE id = ?";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, quantityDelta);
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
        String sql = "INSERT INTO sales (id, user_id, subtotal, tax_amount, discount_amount, total, payment_method, amount_paid, change_given, status, notes, is_synced, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sale.getId());
            pstmt.setString(2, sale.getUserId());
            pstmt.setString(3, sale.getSubtotal().toPlainString());
            pstmt.setString(4, sale.getTaxAmount().toPlainString());
            pstmt.setString(5, sale.getDiscountAmount().toPlainString());
            pstmt.setString(6, sale.getTotal().toPlainString());
            pstmt.setString(7, sale.getPaymentMethod().name());
            pstmt.setString(8, sale.getAmountPaid().toPlainString());
            pstmt.setString(9, sale.getChangeGiven().toPlainString());
            pstmt.setString(10, sale.getStatus().name());
            pstmt.setString(11, sale.getNotes());
            pstmt.setInt(12, sale.isSynced() ? 1 : 0);
            pstmt.setString(13, sale.getCreatedAt().format(DATE_TIME_FORMATTER));
            pstmt.setString(14, sale.getUpdatedAt().format(DATE_TIME_FORMATTER));
            
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
            
            for (SaleItem item : sale.getItems()) {
                updateProductStock(conn, item.getProductId(), -item.getQuantity());
            }
            
            conn.commit();
        }
    }

    private void insertSaleItem(Connection conn, SaleItem item, String saleId) throws SQLException {
        String sql = """
            INSERT INTO sale_items (id, sale_id, product_id, product_name, product_barcode,
            quantity, unit_price, total_price, is_synced, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, item.getId());
            pstmt.setString(2, saleId);
            pstmt.setString(3, item.getProductId());
            pstmt.setString(4, item.getProductName());
            pstmt.setString(5, item.getProductBarcode());
            pstmt.setInt(6, item.getQuantity());
            pstmt.setString(7, item.getUnitPrice().toPlainString());
            pstmt.setString(8, item.getTotalPrice().toPlainString());
            pstmt.setInt(9, item.isSynced() ? 1 : 0);
            pstmt.setString(10, item.getCreatedAt().format(DATE_TIME_FORMATTER));
            
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
        
        p.setStockQuantity(rs.getInt("stock_quantity"));
        p.setMinStockLevel(rs.getInt("min_stock_level"));
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
        
        return p;
    }

    private Sale mapResultSetToSale(ResultSet rs) throws SQLException {
        Sale sale = new Sale();
        sale.setId(rs.getString("id"));
        sale.setUserId(rs.getString("user_id"));
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
            INSERT INTO supplier_transactions (id, supplier_name, total_cost, status, created_at)
            VALUES (?, ?, ?, ?, ?)
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
            pstmtTx.executeUpdate();
            
            for (SupplierTransactionItem it : items) {
                if (it.getId() == null || it.getId().isBlank()) {
                    it.setId(java.util.UUID.randomUUID().toString());
                }
                it.setTransactionId(trans.getId());
                pstmtLine.setString(1, it.getId());
                pstmtLine.setString(2, trans.getId());
                pstmtLine.setString(3, it.getProductId());
                pstmtLine.setInt(4, it.getQuantityReceived());
                pstmtLine.setDouble(5, it.getBuyingPrice().doubleValue());
                pstmtLine.executeUpdate();
            }
            
            processStockIn(conn, items);
            
            if (activityLog != null) {
                insertActivityLog(conn, activityLog);
            }
            
            conn.commit();
        }
    }
    
    /**
     * Increments {@code products.stock_quantity} for each stock-in line (must run inside an open transaction).
     */
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
    
    private SupplierTransaction mapResultSetToSupplierTransaction(ResultSet rs) throws SQLException {
        BigDecimal total = BigDecimal.valueOf(rs.getDouble("total_cost"));
        if (rs.wasNull()) {
            total = BigDecimal.ZERO;
        }
        return new SupplierTransaction(
            rs.getString("id"),
            rs.getString("supplier_name"),
            total,
            rs.getString("status"),
            LocalDateTime.parse(rs.getString("created_at"), DATE_TIME_FORMATTER)
        );
    }
    
    public void close() {
        logger.info("Database manager closed");
    }
}
