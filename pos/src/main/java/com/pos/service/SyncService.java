package com.pos.service;

import com.pos.database.DatabaseManager;
import com.pos.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sync Service
 * Handles background synchronization with Supabase.
 * Implements Offline-First architecture: syncs sales, items, and activity_logs to cloud backend.
 */
public class SyncService {
    
    private static final Logger logger = LoggerFactory.getLogger(SyncService.class);
    private static SyncService instance;
    
    private final DatabaseManager databaseManager;
    private final ScheduledExecutorService scheduler;
    private String apiBaseUrl;
    private String apiKey;
    private final int syncIntervalMinutes;
    private final int connectionTimeoutMs;
    
    private boolean isRunning;
    private boolean isOnline;
    private long lastSyncTime;
    
    /**
     * Private constructor for singleton pattern
     */
    private SyncService() {
        this.databaseManager = DatabaseManager.getInstance();
        this.scheduler = Executors.newScheduledThreadPool(2);
        // Supabase configuration default fallback values
        this.apiBaseUrl = "https://gtjwctckznenodikmgye.supabase.co";
        this.apiKey = "sb_publishable_nenFH56WRAtYgBaXPjrywQ_ExtvVz3a";
        loadEnvLocal();
        this.syncIntervalMinutes = 5;
        this.connectionTimeoutMs = 10000;
        this.isRunning = false;
        this.isOnline = false;
        this.lastSyncTime = 0;
    }

    private void loadEnvLocal() {
        java.io.File envFile = new java.io.File(".env.local");
        if (envFile.exists()) {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(envFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int equalsIdx = line.indexOf('=');
                    if (equalsIdx > 0) {
                        String key = line.substring(0, equalsIdx).trim();
                        String value = line.substring(equalsIdx + 1).trim();
                        if ("NEXT_PUBLIC_SUPABASE_URL".equals(key)) {
                            this.apiBaseUrl = value;
                        } else if ("NEXT_PUBLIC_SUPABASE_ANON_KEY".equals(key)) {
                            this.apiKey = value;
                        }
                    }
                }
                logger.info("Loaded Supabase configuration from .env.local: {}", apiBaseUrl);
            } catch (Exception e) {
                logger.error("Error loading .env.local, using default hardcoded credentials", e);
            }
        } else {
            logger.warn(".env.local not found in current directory, using default hardcoded credentials");
        }
    }

    
    /**
     * Get singleton instance
     */
    public static synchronized SyncService getInstance() {
        if (instance == null) {
            instance = new SyncService();
        }
        return instance;
    }
    
    /**
     * Start the sync service
     */
    public void start() {
        if (isRunning) {
            logger.warn("Sync service is already running");
            return;
        }
        
        isRunning = true;
        
        scheduler.scheduleAtFixedRate(
            this::checkConnectivity,
            0,
            1,
            TimeUnit.MINUTES
        );
        
        scheduler.scheduleAtFixedRate(
            this::syncRecords,
            syncIntervalMinutes,
            syncIntervalMinutes,
            TimeUnit.MINUTES
        );
        
        logger.info("Sync service started. Interval: {} min", syncIntervalMinutes);
    }
    
    /**
     * Stop the sync service
     */
    public void stop() {
        if (!isRunning) return;
        
        isRunning = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Sync service stopped");
    }
    
    private void checkConnectivity() {
        boolean wasOnline = isOnline;
        isOnline = checkInternetConnection();
        if (isOnline != wasOnline && isOnline) {
            logger.info("Internet connection restored, syncing...");
            syncRecords();
        }
    }
    
    private boolean checkInternetConnection() {
        String[] hosts = {"8.8.8.8", "1.1.1.1", "google.com"};
        for (String host : hosts) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, 80), 2000);
                return true;
            } catch (Exception e) {}
        }
        return false;
    }
    
    private void syncRecords() {
        if (!isOnline) return;
        
        try {
            int totalSuccess = 0;
            int totalFailed = 0;
            
            // 1. Sync Sales
            List<Sale> unsyncedSales = databaseManager.getUnsyncedSales();
            for (Sale sale : unsyncedSales) {
                if (syncSaleToCloud(sale)) {
                    databaseManager.markSaleAsSynced(sale.getId());
                    totalSuccess++;
                    
                    // 2. Sync Sale Items for this sale
                    List<SaleItem> items = databaseManager.getSaleItems(sale.getId());
                    for (SaleItem item : items) {
                        if (syncSaleItemToCloud(item)) {
                            databaseManager.markSaleItemAsSynced(item.getId());
                        }
                    }
                } else {
                    totalFailed++;
                }
            }
            
            // 3. Sync Activity Logs
            List<ActivityLog> unsyncedLogs = databaseManager.getUnsyncedActivityLogs();
            for (ActivityLog log : unsyncedLogs) {
                if (syncActivityLogToCloud(log)) {
                    databaseManager.markActivityLogAsSynced(log.getId());
                    totalSuccess++;
                } else {
                    totalFailed++;
                }
            }

            // 4. Sync Debtors (Customers)
            List<com.pos.entity.Customer> unsyncedCustomers = databaseManager.getUnsyncedCustomers();
            for (com.pos.entity.Customer customer : unsyncedCustomers) {
                // To get live balance, we need a summary
                BigDecimal balance = databaseManager.getCustomerCurrentBalance(customer.getId());
                com.pos.entity.CustomerDebtSummary summary = new com.pos.entity.CustomerDebtSummary(customer, balance, 0);
                if (syncDebtorToCloud(summary)) {
                    databaseManager.markCustomerAsSynced(customer.getId());
                    totalSuccess++;
                } else {
                    totalFailed++;
                }
            }

            // 5. Sync Supplier Transactions
            List<com.pos.entity.SupplierTransaction> unsyncedTransactions = databaseManager.getUnsyncedSupplierTransactions();
            for (com.pos.entity.SupplierTransaction trans : unsyncedTransactions) {
                if (syncSupplierTransaction(trans)) {
                    databaseManager.markSupplierTransactionAsSynced(trans.getId());
                    totalSuccess++;
                } else {
                    totalFailed++;
                }
            }

            // 6. Sync Products (Stock updates, price changes, etc.)
            List<Product> unsyncedProducts = databaseManager.getUnsyncedProducts();
            for (Product p : unsyncedProducts) {
                if (syncProductToCloud(p)) {
                    databaseManager.markProductAsSynced(p.getId());
                    totalSuccess++;
                } else {
                    totalFailed++;
                }
            }
            
            if (totalSuccess > 0 || totalFailed > 0) {
                lastSyncTime = System.currentTimeMillis();
                logger.info("Sync completed. Success: {}, Failed: {}", totalSuccess, totalFailed);
            }
            
        } catch (Exception e) {
            logger.error("Error during sync operation", e);
        }
    }
    
    public boolean syncDebtorToCloud(com.pos.entity.CustomerDebtSummary summary) {
        if (!isOnline) return false;
        HttpURLConnection connection = null;
        try {
            String jsonPayload = createDebtorJsonPayload(summary);
            // UPSERT using on_conflict (Supabase specific header)
            java.net.URL url = new java.net.URL(apiBaseUrl + "/rest/v1/debtors");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("apikey", apiKey);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Prefer", "resolution=merge-duplicates");
            connection.setConnectTimeout(connectionTimeoutMs);
            connection.setDoOutput(true);
            
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }
            
            int code = connection.getResponseCode();
            if (code >= 200 && code < 300) {
                databaseManager.markCustomerAsSynced(summary.getCustomer().getId());
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error syncing debtor: {}", summary.getCustomer().getId(), e);
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public boolean syncSupplierTransaction(com.pos.entity.SupplierTransaction trans) {
        if (!isOnline) return false;
        HttpURLConnection connection = null;
        try {
            String jsonPayload = createSupplierTransactionJsonPayload(trans);
            java.net.URL url = new java.net.URL(apiBaseUrl + "/rest/v1/supplier_transactions");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("apikey", apiKey);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Prefer", "resolution=merge-duplicates");
            connection.setConnectTimeout(connectionTimeoutMs);
            connection.setDoOutput(true);
            
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }
            
            int code = connection.getResponseCode();
            if (code >= 200 && code < 300) {
                databaseManager.markSupplierTransactionAsSynced(trans.getId());
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error syncing supplier transaction: {}", trans.getId(), e);
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String createDebtorJsonPayload(com.pos.entity.CustomerDebtSummary summary) {
        com.pos.entity.Customer c = summary.getCustomer();
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"id\":\"").append(c.getId()).append("\",");
        json.append("\"name\":\"").append(escapeJson(c.getName())).append("\",");
        json.append("\"current_balance\":").append(summary.getTotalDebt().toPlainString()).append(",");
        json.append("\"credit_limit\":").append(c.getCreditLimit()).append(",");
        json.append("\"contact_info\":").append(c.getPhone() != null ? "\"" + escapeJson(c.getPhone()) + "\"" : "null").append(",");
        json.append("\"created_at\":\"").append(c.getCreatedAt()).append("\"");
        json.append("}");
        return json.toString();
    }

    private String createSupplierTransactionJsonPayload(com.pos.entity.SupplierTransaction trans) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"id\":\"").append(trans.getId()).append("\",");
        json.append("\"supplier_name\":\"").append(escapeJson(trans.getSupplierName())).append("\",");
        json.append("\"total_cost\":").append(trans.getTotalCost().toPlainString()).append(",");
        json.append("\"debtor_offset\":").append(trans.getDebtorOffset().toPlainString()).append(",");
        json.append("\"cash_paid\":").append(trans.getCashPaid().toPlainString()).append(",");
        json.append("\"debtor_id\":").append(trans.getDebtorId() != null ? "\"" + trans.getDebtorId() + "\"" : "null").append(",");
        json.append("\"transaction_date\":\"").append(trans.getCreatedAt()).append("\",");
        json.append("\"notes\":\"Payment Source: ").append(escapeJson(trans.getPaymentSource())).append("\"");
        json.append("}");
        return json.toString();
    }
    
    private boolean syncSaleToCloud(Sale sale) {
        HttpURLConnection connection = null;
        try {
            String jsonPayload = createSaleJsonPayload(sale);
            java.net.URL url = new java.net.URL(apiBaseUrl + "/rest/v1/cloud_sales");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("apikey", apiKey);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Prefer", "return=minimal");
            connection.setConnectTimeout(connectionTimeoutMs);
            connection.setDoOutput(true);
            
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }
            
            int code = connection.getResponseCode();
            if (code >= 200 && code < 300) return true;
            
            logger.warn("Failed to sync sale {}. Code: {}", sale.getId(), code);
            return false;
        } catch (Exception e) {
            logger.error("Error syncing sale: {}", sale.getId(), e);
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private boolean syncSaleItemToCloud(SaleItem item) {
        HttpURLConnection connection = null;
        try {
            String jsonPayload = createSaleItemJsonPayload(item);
            java.net.URL url = new java.net.URL(apiBaseUrl + "/rest/v1/cloud_sale_items");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("apikey", apiKey);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Prefer", "return=minimal");
            connection.setConnectTimeout(connectionTimeoutMs);
            connection.setDoOutput(true);
            
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }
            
            int code = connection.getResponseCode();
            return (code >= 200 && code < 300);
        } catch (Exception e) {
            logger.error("Error syncing sale item: {}", item.getId(), e);
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
    
    private boolean syncActivityLogToCloud(ActivityLog log) {
        HttpURLConnection connection = null;
        try {
            String jsonPayload = createActivityLogJsonPayload(log);
            java.net.URL url = new java.net.URL(apiBaseUrl + "/rest/v1/activity_logs");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("apikey", apiKey);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Prefer", "return=minimal");
            connection.setConnectTimeout(connectionTimeoutMs);
            connection.setDoOutput(true);
            
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }
            
            int code = connection.getResponseCode();
            if (code >= 200 && code < 300) return true;
            
            logger.warn("Failed to sync activity log {}. Code: {}", log.getId(), code);
            return false;
        } catch (Exception e) {
            logger.error("Error syncing activity log: {}", log.getId(), e);
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public boolean syncProductToCloud(Product product) {
        if (!isOnline) return false;
        HttpURLConnection connection = null;
        try {
            String jsonPayload = createProductJsonPayload(product);
            // UPSERT using on_conflict (Supabase specific header)
            java.net.URL url = new java.net.URL(apiBaseUrl + "/rest/v1/products");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("apikey", apiKey);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Prefer", "resolution=merge-duplicates");
            connection.setConnectTimeout(connectionTimeoutMs);
            connection.setDoOutput(true);
            
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }
            
            int code = connection.getResponseCode();
            if (code >= 200 && code < 300) {
                databaseManager.markProductAsSynced(product.getId());
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error syncing product: {}", product.getId(), e);
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public void pushAllPendingProducts() {
        if (!isOnline) return;
        try {
            List<Product> unsyncedProducts = databaseManager.getUnsyncedProducts();
            for (Product p : unsyncedProducts) {
                syncProductToCloud(p);
            }
        } catch (Exception e) {
            logger.error("Error pushing pending products", e);
        }
    }

    private String createProductJsonPayload(Product p) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"id\":\"").append(p.getId()).append("\",");
        json.append("\"barcode\":\"").append(p.getBarcode()).append("\",");
        json.append("\"name\":\"").append(escapeJson(p.getName())).append("\",");
        json.append("\"category\":\"").append(escapeJson(p.getCategory())).append("\",");
        json.append("\"retail_price\":").append(p.getRetailPrice().toPlainString()).append(",");
        json.append("\"wholesale_price\":").append(p.getWholesalePrice().toPlainString()).append(",");
        json.append("\"stock_quantity\":").append(p.getStockQuantity()).append(",");
        json.append("\"min_stock_level\":").append(p.getMinStockLevel()).append(",");
        json.append("\"unit_type\":").append(p.getUnitType() != null ? "\"" + escapeJson(p.getUnitType()) + "\"" : "null").append(",");
        json.append("\"is_active\":").append(p.isActive() ? "true" : "false").append(",");
        json.append("\"parent_barcode\":").append(p.getParentBarcode() != null ? "\"" + p.getParentBarcode() + "\"" : "null").append(",");
        json.append("\"parent_wholesale_barcode\":").append(p.getParentWholesaleBarcode() != null ? "\"" + p.getParentWholesaleBarcode() + "\"" : "null").append(",");
        json.append("\"conversion_yield\":").append(p.getConversionYield()).append(",");
        json.append("\"raw_piece_yield\":").append(p.getRawPieceYield()).append(",");
        json.append("\"deduction_ratio\":").append(p.getDeductionRatio()).append(",");
        json.append("\"created_at\":\"").append(p.getCreatedAt()).append("\",");
        json.append("\"updated_at\":\"").append(p.getUpdatedAt()).append("\"");
        json.append("}");
        return json.toString();
    }
    
    private String createSaleJsonPayload(Sale sale) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"id\":\"").append(sale.getId()).append("\",");
        json.append("\"user_id\":\"").append(sale.getUserId()).append("\",");
        json.append("\"subtotal\":\"").append(sale.getSubtotal()).append("\",");
        json.append("\"tax_amount\":\"").append(sale.getTaxAmount()).append("\",");
        json.append("\"discount_amount\":\"").append(sale.getDiscountAmount()).append("\",");
        json.append("\"total\":\"").append(sale.getTotal()).append("\",");
        json.append("\"amount_paid\":\"").append(sale.getAmountPaid()).append("\",");
        json.append("\"change_given\":\"").append(sale.getChangeGiven()).append("\",");
        json.append("\"payment_method\":\"").append(sale.getPaymentMethod().name()).append("\",");
        json.append("\"status\":\"").append(sale.getStatus().name()).append("\",");
        json.append("\"notes\":").append(sale.getNotes() != null ? "\"" + escapeJson(sale.getNotes()) + "\"" : "null").append(",");
        json.append("\"cash_amount\":\"").append(sale.getCashAmount()).append("\",");
        json.append("\"mpesa_amount\":\"").append(sale.getMpesaAmount()).append("\",");
        json.append("\"secondary_payment_method\":").append(sale.getSecondaryPaymentMethod() != null ? "\"" + sale.getSecondaryPaymentMethod().name() + "\"" : "null").append(",");
        json.append("\"created_at\":\"").append(sale.getCreatedAt()).append("\",");
        json.append("\"updated_at\":\"").append(sale.getUpdatedAt()).append("\"");
        json.append("}");
        return json.toString();
    }

    private String createSaleItemJsonPayload(SaleItem item) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"id\":\"").append(item.getId()).append("\",");
        json.append("\"sale_id\":\"").append(item.getSaleId()).append("\",");
        json.append("\"product_id\":\"").append(item.getProductId()).append("\",");
        json.append("\"product_name\":\"").append(escapeJson(item.getProductName())).append("\",");
        json.append("\"product_barcode\":\"").append(item.getProductBarcode()).append("\",");
        json.append("\"quantity\":").append((int) item.getQuantity()).append(",");
        json.append("\"unit_price\":\"").append(item.getUnitPrice()).append("\",");
        json.append("\"total_price\":\"").append(item.getTotalPrice()).append("\",");
        json.append("\"is_synced\":true,");
        json.append("\"created_at\":\"").append(item.getCreatedAt()).append("\"");
        json.append("}");
        return json.toString();
    }
    
    private String createActivityLogJsonPayload(ActivityLog log) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        // 'id' is generated by Supabase (UUID), we send local ID to 'pos_id'
        json.append("\"user_id\":\"").append(log.getUserId()).append("\",");
        json.append("\"user_name\":\"").append(escapeJson(log.getUserName())).append("\",");
        json.append("\"action_type\":\"").append(log.getActionType().name()).append("\",");
        json.append("\"target_description\":").append(log.getTargetDescription() != null ? "\"" + escapeJson(log.getTargetDescription()) + "\"" : "null").append(",");
        json.append("\"details\":").append(log.getDetails() != null ? "\"" + escapeJson(log.getDetails()) + "\"" : "null").append(",");
        json.append("\"pos_id\":\"").append(log.getId()).append("\",");
        json.append("\"created_at\":\"").append(log.getCreatedAt()).append("\"");
        json.append("}");
        return json.toString();
    }
    
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
    
    public void forceSync() {
        if (isOnline) syncRecords();
    }
    
    public boolean isOnline() { return isOnline; }
    public long getLastSyncTime() { return lastSyncTime; }

    /**
     * Get the current sync status
     */
    public SyncStatus getStatus() {
        return new SyncStatus(isRunning, isOnline, lastSyncTime, getUnsyncedCount());
    }

    private int getUnsyncedCount() {
        try {
            return databaseManager.getUnsyncedSales().size() + 
                   databaseManager.getUnsyncedActivityLogs().size() +
                   databaseManager.getUnsyncedCustomers().size() +
                   databaseManager.getUnsyncedSupplierTransactions().size();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Sync Status DTO
     */
    public static class SyncStatus {
        private final boolean running;
        private final boolean online;
        private final long lastSyncTime;
        private final int unsyncedCount;
        
        public SyncStatus(boolean running, boolean online, long lastSyncTime, int unsyncedCount) {
            this.running = running;
            this.online = online;
            this.lastSyncTime = lastSyncTime;
            this.unsyncedCount = unsyncedCount;
        }
        
        public boolean isRunning() { return running; }
        public boolean isOnline() { return online; }
        public long getLastSyncTime() { return lastSyncTime; }
        public int getUnsyncedCount() { return unsyncedCount; }
    }
}
