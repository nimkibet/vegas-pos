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
 * Handles background synchronization with Supabase.
 * Implements Offline-First architecture: syncs sales and activity_logs to cloud backend.
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
        // Supabase configuration from .env.local
        this.apiBaseUrl = "https://gtjwctckznenodikmgye.supabase.co";
        this.apiKey = "sb_publishable_nenFH56WRAtYgBaXPjrywQ_ExtvVz3a";
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
            
            // Sync Sales
            List<Sale> unsyncedSales = databaseManager.getUnsyncedSales();
            for (Sale sale : unsyncedSales) {
                if (syncSaleToCloud(sale)) {
                    databaseManager.markSaleAsSynced(sale.getId());
                    totalSuccess++;
                } else {
                    totalFailed++;
                }
            }
            
            // Sync Activity Logs
            List<ActivityLog> unsyncedLogs = databaseManager.getUnsyncedActivityLogs();
            for (ActivityLog log : unsyncedLogs) {
                if (syncActivityLogToCloud(log)) {
                    databaseManager.markActivityLogAsSynced(log.getId());
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
    
    private String createSaleJsonPayload(Sale sale) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"user_id\":").append(sale.getUserId() != null ? "\"" + sale.getUserId() + "\"" : "null").append(",");
        json.append("\"total\":").append(sale.getTotal()).append(",");
        json.append("\"payment_method\":\"").append(sale.getPaymentMethod().name()).append("\",");
        json.append("\"notes\":").append(sale.getNotes() != null ? "\"" + escapeJson(sale.getNotes()) + "\"" : "null").append(",");
        json.append("\"pos_id\":\"").append(sale.getId()).append("\",");
        json.append("\"created_at\":\"").append(sale.getCreatedAt()).append("\"");
        json.append("}");
        return json.toString();
    }
    
    private String createActivityLogJsonPayload(ActivityLog log) {
        StringBuilder json = new StringBuilder();
        json.append("{");
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
                   databaseManager.getUnsyncedActivityLogs().size();
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
