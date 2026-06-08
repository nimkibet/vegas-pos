package com.pos.service;

import com.pos.util.BrandingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Network Health Service
 * Monitors local (Master) and cloud connectivity every 15 seconds.
 * Provides status updates via callback consumers.
 */
public class NetworkHealthService {
    
    private static final Logger logger = LoggerFactory.getLogger(NetworkHealthService.class);
    private static NetworkHealthService instance;
    
    private final ScheduledExecutorService scheduler;
    private final Properties config;
    
    // Connectivity status
    private boolean isMasterConnected;
    private boolean isCloudConnected;
    private String masterIp;
    private String cloudApiUrl;
    private int masterPort;
    private int checkIntervalSeconds;
    
    // Callbacks for UI updates
    private Consumer<NetworkStatus> statusCallback;
    
    // Connection test settings
    private final int connectionTimeoutMs = 3000;
    
    /**
     * Private constructor for singleton pattern
     */
    private NetworkHealthService() {
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.config = loadConfig();
        
        // Load configuration
        this.masterIp = config.getProperty("master.ip", "localhost");
        this.masterPort = Integer.parseInt(config.getProperty("master.port", "9100"));
        this.cloudApiUrl = config.getProperty("cloud.api.url", "https://api.example.com");
        this.checkIntervalSeconds = 15;
        
        this.isMasterConnected = false;
        this.isCloudConnected = false;
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized NetworkHealthService getInstance() {
        if (instance == null) {
            instance = new NetworkHealthService();
        }
        return instance;
    }
    
    /**
     * Load configuration from config.properties
     */
    private Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (Exception e) {
            logger.warn("Could not load config.properties, using defaults", e);
        }
        return props;
    }
    
    /**
     * Set status callback for UI updates
     */
    public void setStatusCallback(Consumer<NetworkStatus> callback) {
        this.statusCallback = callback;
    }
    
    /**
     * Start network health monitoring
     */
    public void start() {
        logger.info("Starting network health monitoring (interval: {}s)", checkIntervalSeconds);
        
        // Initial check
        checkConnectivity();
        
        // Schedule periodic checks
        scheduler.scheduleAtFixedRate(
            this::checkConnectivity,
            checkIntervalSeconds,
            checkIntervalSeconds,
            TimeUnit.SECONDS
        );
    }
    
    /**
     * Stop network health monitoring
     */
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Network health monitoring stopped");
    }
    
    /**
     * Check both master and cloud connectivity
     */
    private void checkConnectivity() {
        // Check master connection
        isMasterConnected = checkMasterConnection();
        
        // Check cloud connection
        isCloudConnected = checkCloudConnection();
        
        // Log status
        logger.debug("Network Status - Master: {}, Cloud: {}", isMasterConnected, isCloudConnected);
        
        // Notify callback
        if (statusCallback != null) {
            NetworkStatus status = new NetworkStatus(isMasterConnected, isCloudConnected);
            statusCallback.accept(status);
        }
    }
    
    /**
     * Check connection to Master terminal
     */
    private boolean checkMasterConnection() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(masterIp, masterPort), connectionTimeoutMs);
            return socket.isConnected();
        } catch (Exception e) {
            logger.debug("Master connection failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check connection to Cloud API
     */
    private boolean checkCloudConnection() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(cloudApiUrl + "/health");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(connectionTimeoutMs);
            connection.setReadTimeout(connectionTimeoutMs);
            
            int responseCode = connection.getResponseCode();
            return responseCode >= 200 && responseCode < 300;
            
        } catch (Exception e) {
            logger.debug("Cloud connection failed: {}", e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Get current master connection status
     */
    public boolean isMasterConnected() {
        return isMasterConnected;
    }
    
    /**
     * Get current cloud connection status
     */
    public boolean isCloudConnected() {
        return isCloudConnected;
    }
    
    /**
     * Get current network status
     */
    public NetworkStatus getStatus() {
        return new NetworkStatus(isMasterConnected, isCloudConnected);
    }
    
    /**
     * Force immediate connectivity check
     */
    public void forceCheck() {
        checkConnectivity();
    }
    
    /**
     * Network status data class
     */
    public static class NetworkStatus {
        private final boolean masterConnected;
        private final boolean cloudConnected;
        
        public NetworkStatus(boolean masterConnected, boolean cloudConnected) {
            this.masterConnected = masterConnected;
            this.cloudConnected = cloudConnected;
        }
        
        public boolean isMasterConnected() { return masterConnected; }
        public boolean isCloudConnected() { return cloudConnected; }
        
        public boolean isFullyConnected() { return masterConnected && cloudConnected; }
        public boolean isOffline() { return !masterConnected && !cloudConnected; }
        public boolean isPartialConnection() { return masterConnected != cloudConnected; }
        
        @Override
        public String toString() {
            if (isFullyConnected()) return "Fully Connected";
            if (isOffline()) return "Offline";
            if (masterConnected) return "Master Only";
            return "Cloud Only";
        }
    }
}
