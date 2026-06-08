package com.pos.service;

import com.pos.database.DatabaseManager;
import com.pos.entity.Sale;
import com.pos.entity.SaleItem;
import com.pos.entity.ActivityLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
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
 * Handles background synchronization with the central API.
 * Uses ScheduledExecutorService for periodic sync operations.
 * Implements Offline-First architecture: syncs sales, sale_items, and activity_logs to cloud backend.
 */
public class SyncService {
    
    private static final Logger logger = LoggerFactory.getLogger(SyncService.class);
    private static SyncService instance;
    
    private final DatabaseManager databaseManager;
    private final ScheduledExecutorService scheduler;
    private String apiBaseUrl;
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
        this.apiBaseUrl = "https://api.example.com";
        this.syncIntervalMinutes = 5;
        this.connectionTimeoutMs = 10000;
        this.isRunning = false;
        this.isOnline = false;
        this.lastSyncTime = 0;
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
     * Set the API base URL (can be configured from settings)
     */
    public void setApiBaseUrl(String url) {
        this.apiBaseUrl = url;
        logger.info("Sync API URL set to: {}", url);
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
        
        logger.info("Sync service started");
    }
    
    /**
     * Stop the sync service
     */
    public void stop() {
        if (!isRunning) {
            logger.warn("Sync service is not running");
            return;
        }
        
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
    
    /**
     * Check internet connectivity
     */
    private void checkConnectivity() {
        boolean wasOnline = isOnline;
        isOnline = checkInternetConnection();
        
        if (isOnline != wasOnline) {
            if (isOnline) {
                logger.info("Internet connection restored");
                syncRecords();
            } else {
                logger.warn("Internet connection lost");
            }
        }
    }
    
    /**
     * Check if there's an internet connection
     */
    private boolean checkInternetConnection() {
        String[] hosts = {"8.8.8.8", "1.1.1.1", "google.com"};
        int[] ports = {53, 443, 80};
        
        for (int i = 0; i < hosts.length; i++) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(hosts[i], ports[i % ports.length]), connectionTimeoutMs);
                return true;
            } catch (Exception e) {
            }
        }
        
        return false;
    }
    
    /**
     * Sync unsynced records with the central API
     */
    private void syncRecords() {
        if (!isOnline) {
            logger.debug("Skipping sync - offline");
            return;
        }
        
        try {
            logger.info("Starting sync operation");
            
            int totalSuccess = 0;
            int totalFailed = 0;
            
            List<Sale> unsyncedSales = databaseManager.getUnsyncedSales();
            logger.info("Found {} unsynced sales to sync", unsyncedSales.size());
            
            for (Sale sale : unsyncedSales) {
                try {
                    if (syncSaleToCloud(sale)) {
                        databaseManager.markSaleAsSynced(sale.getId());
                        
                        List<SaleItem> items = databaseManager.getSaleItems(sale.getId());
                        for (SaleItem item : items) {
                            databaseManager.markSaleItemAsSynced(item.getId());
                        }
                        
                        totalSuccess++;
                    } else {
                        totalFailed++;
                    }
                } catch (Exception e) {
                    logger.error("Error syncing sale: {}", sale.getId(), e);
                    totalFailed++;
                }
            }
            
            List<ActivityLog> unsyncedLogs = databaseManager.getUnsyncedActivityLogs();
            logger.info("Found {} unsynced activity logs to sync", unsyncedLogs.size());
            
            for (ActivityLog log : unsyncedLogs) {
                try {
                    if (syncActivityLogToCloud(log)) {
                        databaseManager.markActivityLogAsSynced(log.getId());
                        totalSuccess++;
                    } else {
                        totalFailed++;
                    }
                } catch (Exception e) {
                    logger.error("Error syncing activity log: {}", log.getId(), e);
                    totalFailed++;
                }
            }
            
            lastSyncTime = System.currentTimeMillis();
            logger.info("Sync completed. Success: {}, Failed: {}", totalSuccess, totalFailed);
            
        } catch (Exception e) {
            logger.error("Error during sync operation", e);
        }
    }
    
    /**
     * Sync a single sale to the cloud API
     */
    private boolean syncSaleToCloud(Sale sale) {
        HttpURLConnection connection = null;
        try {
            String jsonPayload = createSaleJsonPayload(sale);
            
            java.net.URL url = new java.net.URL(apiBaseUrl + "/api/sales");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setConnectTimeout(connectionTimeoutMs);
            connection.setReadTimeout(connectionTimeoutMs);
            connection.setDoOutput(true);
            
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                logger.debug("Sale synced to cloud: {}", sale.getId());
                return true;
            } else {
                logger.warn("Failed to sync sale. Response code: {}", responseCode);
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Failed to sync sale to cloud: {}", sale.getId(), e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Sync a single activity log to the cloud API
     */
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
            connection.setReadTimeout(connectionTimeoutMs);
            connection.setDoOutput(true);
            
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                logger.debug("Activity log synced to cloud: {}", log.getId());
                return true;
            } else {
                logger.warn("Failed to sync activity log. Response code: {}", responseCode);
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Failed to sync activity log to cloud: {}", log.getId(), e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * POST JSON payload to remote endpoint (generic method)
     */
    public boolean postToRemoteEndpoint(String endpoint, String jsonPayload) {
        HttpURLConnection connection = null;
        try {
            java.net.URL url = new java.net.URL(apiBaseUrl + endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setConnectTimeout(connectionTimeoutMs);
            connection.setReadTimeout(connectionTimeoutMs);
            connection.setDoOutput(true);
            
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED;
            
        } catch (Exception e) {
            logger.error("Failed to POST to endpoint: {}", endpoint, e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Create JSON payload for a sale
     */
    private String createSaleJsonPayload(Sale sale) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"id\":\"").append(sale.getId()).append("\",");
        json.append("\"userId\":\"").append(sale.getUserId()).append("\",");
        json.append("\"subtotal\":\"").append(sale.getSubtotal()).append("\",");
        json.append("\"taxAmount\":\"").append(sale.getTaxAmount()).append("\",");
        json.append("\"discountAmount\":\"").append(sale.getDiscountAmount()).append("\",");
        json.append("\"total\":\"").append(sale.getTotal()).append("\",");
        json.append("\"paymentMethod\":\"").append(sale.getPaymentMethod()).append("\",");
        json.append("\"amountPaid\":\"").append(sale.getAmountPaid()).append("\",");
        json.append("\"changeGiven\":\"").append(sale.getChangeGiven()).append("\",");
        json.append("\"status\":\"").append(sale.getStatus()).append("\",");
        json.append("\"createdAt\":\"").append(sale.getCreatedAt()).append("\"");
        
        List<SaleItem> items = sale.getItems();
        if (items != null && !items.isEmpty()) {
            json.append(",\"items\":[");
            for (int i = 0; i < items.size(); i++) {
                SaleItem item = items.get(i);
                json.append("{");
                json.append("\"id\":\"").append(item.getId()).append("\",");
                json.append("\"productId\":\"").append(item.getProductId()).append("\",");
                json.append("\"productName\":\"").append(escapeJson(item.getProductName())).append("\",");
                json.append("\"quantity\":").append(item.getQuantity()).append(",");
                json.append("\"unitPrice\":\"").append(item.getUnitPrice()).append("\",");
                json.append("\"totalPrice\":\"").append(item.getTotalPrice()).append("\"");
                json.append("}");
                if (i < items.size() - 1) json.append(",");
            }
            json.append("]");
        }
        
        json.append("}");
        return json.toString();
    }
    
    /**
     * Create JSON payload for an activity log
     */
    private String createActivityLogJsonPayload(ActivityLog log) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"id\":\"").append(log.getId()).append("\",");
        json.append("\"userId\":\"").append(log.getUserId()).append("\",");
        json.append("\"userName\":\"").append(escapeJson(log.getUserName())).append("\",");
        json.append("\"actionType\":\"").append(log.getActionType().name()).append("\",");
        json.append("\"targetDescription\":\"").append(escapeJson(log.getTargetDescription())).append("\",");
        json.append("\"details\":\"").append(escapeJson(log.getDetails())).append("\",");
        json.append("\"createdAt\":\"").append(log.getCreatedAt()).append("\"");
        json.append("}");
        return json.toString();
    }
    
    /**
     * Escape special characters for JSON
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
    
    /**
     * Force an immediate sync
     */
    public void forceSync() {
        if (!isOnline) {
            logger.warn("Cannot force sync - offline");
            return;
        }
        
        logger.info("Forcing immediate sync");
        syncRecords();
    }
    
    /**
     * Get the sync status
     */
    public SyncStatus getStatus() {
        return new SyncStatus(isRunning, isOnline, lastSyncTime, unsyncedCount());
    }
    
    /**
     * Get count of unsynced records
     */
    private int unsyncedCount() {
        try {
            int salesCount = databaseManager.getUnsyncedSales().size();
            int logsCount = databaseManager.getUnsyncedActivityLogs().size();
            return salesCount + logsCount;
        } catch (Exception e) {
            logger.error("Error getting unsynced count", e);
            return 0;
        }
    }
    
    /**
     * Check if service is running
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Check if online
     */
    public boolean isOnline() {
        return isOnline;
    }
    
    /**
     * Get last sync time
     */
    public long getLastSyncTime() {
        return lastSyncTime;
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
lastSyncTime; }
        public int getUnsyncedCount() { return unsyncedCount; }
    }
}
