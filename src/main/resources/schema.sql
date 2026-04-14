-- POS System Database Schema
-- SQLite with UUID Primary Keys
-- Offline-First Architecture

-- =====================================================
-- USERS TABLE (with roles)
-- =====================================================
CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    full_name TEXT NOT NULL,
    role TEXT NOT NULL CHECK(role IN ('ADMIN', 'ATTENDANT')),
    is_active INTEGER NOT NULL DEFAULT 1,
    is_synced INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Index for faster username lookups
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

-- =====================================================
-- PRODUCTS TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS products (
    id TEXT PRIMARY KEY,
    barcode TEXT UNIQUE,
    name TEXT NOT NULL,
    description TEXT,
    category TEXT,
    retail_price TEXT NOT NULL,  -- Stored as string for BigDecimal
    wholesale_price TEXT NOT NULL, -- Stored as string for BigDecimal
    stock_quantity INTEGER NOT NULL DEFAULT 0,
    min_stock_level INTEGER NOT NULL DEFAULT 0,
    image_path TEXT,
    supplier TEXT,
    status TEXT NOT NULL DEFAULT 'APPROVED' CHECK(status IN ('APPROVED', 'PENDING', 'REJECTED')),
    is_active INTEGER NOT NULL DEFAULT 1,
    is_synced INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Indexes for product lookups
CREATE INDEX IF NOT EXISTS idx_products_barcode ON products(barcode);
CREATE INDEX IF NOT EXISTS idx_products_category ON products(category);
CREATE INDEX IF NOT EXISTS idx_products_is_active ON products(is_active);
CREATE INDEX IF NOT EXISTS idx_products_status ON products(status);

-- =====================================================
-- SALES TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS sales (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    subtotal TEXT NOT NULL,
    tax_amount TEXT NOT NULL DEFAULT '0.00',
    discount_amount TEXT NOT NULL DEFAULT '0.00',
    total TEXT NOT NULL,
    payment_method TEXT NOT NULL CHECK(payment_method IN ('CASH', 'CARD', 'MOBILE_MONEY')),
    amount_paid TEXT NOT NULL,
    change_given TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'COMPLETED' CHECK(status IN ('PENDING', 'COMPLETED', 'VOIDED', 'REFUNDED')),
    notes TEXT,
    is_synced INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    cash_amount TEXT DEFAULT '0.00',
    mpesa_amount TEXT DEFAULT '0.00',
    secondary_payment_method TEXT CHECK(secondary_payment_method IN ('CASH', 'CARD', 'MOBILE_MONEY')),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Indexes for sales
CREATE INDEX IF NOT EXISTS idx_sales_user_id ON sales(user_id);
CREATE INDEX IF NOT EXISTS idx_sales_created_at ON sales(created_at);
CREATE INDEX IF NOT EXISTS idx_sales_is_synced ON sales(is_synced);
CREATE INDEX IF NOT EXISTS idx_sales_status ON sales(status);

-- =====================================================
-- SALE_ITEMS TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS sale_items (
    id TEXT PRIMARY KEY,
    sale_id TEXT NOT NULL,
    product_id TEXT NOT NULL,
    product_name TEXT NOT NULL,
    product_barcode TEXT,
    quantity INTEGER NOT NULL,
    unit_price TEXT NOT NULL,    -- Price at time of sale
    total_price TEXT NOT NULL,
    is_synced INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id)
);

-- Indexes for sale items
CREATE INDEX IF NOT EXISTS idx_sale_items_sale_id ON sale_items(sale_id);
CREATE INDEX IF NOT EXISTS idx_sale_items_product_id ON sale_items(product_id);
CREATE INDEX IF NOT EXISTS idx_sale_items_is_synced ON sale_items(is_synced);

-- =====================================================
-- SYNC_LOG TABLE (for tracking sync operations)
-- =====================================================
CREATE TABLE IF NOT EXISTS sync_log (
    id TEXT PRIMARY KEY,
    table_name TEXT NOT NULL,
    record_id TEXT NOT NULL,
    operation TEXT NOT NULL CHECK(operation IN ('CREATE', 'UPDATE', 'DELETE')),
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING', 'SYNCED', 'FAILED')),
    error_message TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    synced_at TEXT
);

-- Indexes for sync log
CREATE INDEX IF NOT EXISTS idx_sync_log_status ON sync_log(status);
CREATE INDEX IF NOT EXISTS idx_sync_log_table_name ON sync_log(table_name);

-- =====================================================
-- ACTIVITY_LOG TABLE (for tracking user actions)
-- =====================================================
CREATE TABLE IF NOT EXISTS activity_logs (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    user_name TEXT NOT NULL,
    action_type TEXT NOT NULL,
    target_description TEXT,
    details TEXT,
    is_synced INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Indexes for activity log
CREATE INDEX IF NOT EXISTS idx_activity_logs_user_id ON activity_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_activity_logs_action_type ON activity_logs(action_type);
CREATE INDEX IF NOT EXISTS idx_activity_logs_created_at ON activity_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_activity_logs_is_synced ON activity_logs(is_synced);

-- =====================================================
-- SETTINGS TABLE (for system configuration)
-- =====================================================
CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    description TEXT,
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- =====================================================
-- DEFAULT DATA - Admin User (password: admin123)
-- =====================================================
-- Default admin user: admin / admin123
-- Default attendant: attendant / attendant123
INSERT OR IGNORE INTO users (id, username, password_hash, full_name, role, is_active, is_synced)
VALUES 
    ('550e8400-e29b-41d4-a716-446655440000', 'admin', '$2a$10$H7HS6v8JviOGvndL7N8KXOp.icjewy2DZfWTdqE9tnFSgZCkcaTVS', 'System Administrator', 'ADMIN', 1, 0),
    ('550e8400-e29b-41d4-a716-446655440001', 'attendant', '$2a$10$9Cf2WA9GRbXqhCxQ8tMfAu2sUYPcs4OYxpjcXVr3b.l4k3JL3TwU2', 'Default Attendant', 'ATTENDANT', 1, 0);

-- =====================================================
-- PRODUCT SEEDER - 10 Sample Products
-- =====================================================
INSERT OR IGNORE INTO products (id, barcode, name, description, category, retail_price, wholesale_price, stock_quantity, min_stock_level, is_active, is_synced)
VALUES 
    ('550e8400-e29b-41d4-a716-446655440100', '500000001', 'Bread (White Loaf)', 'Fresh white bread', 'Bakery', '50.00', '40.00', 50, 10, 1, 0),
    ('550e8400-e29b-41d4-a716-446655440101', '500000002', 'Bread (Brown Loaf)', 'Fresh brown bread', 'Bakery', '55.00', '45.00', 40, 10, 1, 0),
    ('550e8400-e29b-41d4-a716-446655440102', '500000003', 'Milk (1 Liter)', 'Fresh whole milk', 'Dairy', '120.00', '100.00', 100, 20, 1, 0),
    ('550e8400-e29b-41d4-a716-446655440103', '500000004', 'Milk (500ml)', 'Half liter milk', 'Dairy', '70.00', '55.00', 80, 15, 1, 0),
    ('550e8400-e29b-41d4-a716-446655440104', '500000005', 'Eggs (Tray)', '30 eggs tray', 'Dairy', '450.00', '400.00', 30, 10, 1, 0),
    ('550e8400-e29b-41d4-a716-446655440105', '500000006', 'Sugar (1kg)', 'White sugar', 'Groceries', '150.00', '130.00', 60, 15, 1, 0),
    ('550e8400-e29b-41d4-a716-446655440106', '500000007', 'Rice (1kg)', 'Basmati rice', 'Groceries', '180.00', '160.00', 45, 10, 1, 0),
    ('550e8400-e29b-41d4-a716-446655440107', '500000008', 'Rice (2kg)', 'Basmati rice 2kg', 'Groceries', '350.00', '320.00', 25, 8, 1, 0),
    ('550e8400-e29b-41d4-a716-446655440108', '500000009', 'Cooking Oil (1L)', 'Vegetable cooking oil', 'Groceries', '200.00', '180.00', 50, 12, 1, 0),
    ('550e8400-e29b-41d4-a716-446655440109', '500000010', 'Cooking Oil (500ml)', 'Vegetable cooking oil', 'Groceries', '110.00', '95.00', 40, 10, 1, 0);

-- =====================================================
-- DEFAULT SETTINGS
-- =====================================================
INSERT OR IGNORE INTO settings (key, value, description)
VALUES 
    ('tax_rate', '0.00', 'Tax rate as decimal (e.g., 0.16 for 16%)'),
    ('store_name', 'My POS Store', 'Name of the store'),
    ('store_address', '', 'Store address for receipts'),
    ('store_phone', '', 'Store contact number'),
    ('receipt_footer', 'Thank you for your business!', 'Footer message on receipts'),
    ('printer_name', 'default', 'Default ESC/POS printer name'),
    ('api_base_url', 'https://api.example.com', 'Base URL for sync API');
