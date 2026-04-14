package com.pos.service;

import com.pos.database.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analytics Service
 * Provides data for dashboard charts and reports.
 */
public class AnalyticsService {
    
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);
    private static AnalyticsService instance;
    private final DatabaseManager dbManager;
    
    private AnalyticsService() {
        this.dbManager = DatabaseManager.getInstance();
    }
    
    public static synchronized AnalyticsService getInstance() {
        if (instance == null) {
            instance = new AnalyticsService();
        }
        return instance;
    }
    
    /**
     * Get revenue data for the last 7 days
     * @return Map of date string to revenue amount
     */
    public List<Map<String, Object>> getLast7DaysRevenue() {
        List<Map<String, Object>> result = new ArrayList<>();
        
        String sql = """
            SELECT DATE(created_at) as sale_date, SUM(total) as daily_revenue 
            FROM sales 
            WHERE created_at >= DATE('now', '-7 days') 
            GROUP BY DATE(created_at) 
            ORDER BY sale_date ASC
            """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            Map<String, Double> revenueMap = new HashMap<>();
            
            // Initialize all 7 days with 0
            for (int i = 6; i >= 0; i--) {
                String date = LocalDate.now().minusDays(i).toString();
                revenueMap.put(date, 0.0);
            }
            
            // Fill in actual data
            while (rs.next()) {
                String date = rs.getString("sale_date");
                double revenue = rs.getDouble("daily_revenue");
                revenueMap.put(date, revenue);
            }
            
            // Convert to list
            for (Map.Entry<String, Double> entry : revenueMap.entrySet()) {
                Map<String, Object> dayData = new HashMap<>();
                dayData.put("date", entry.getKey());
                dayData.put("revenue", entry.getValue());
                result.add(dayData);
            }
            
            logger.info("Retrieved 7-day revenue data: {} days", result.size());
            
        } catch (SQLException e) {
            logger.error("Error fetching 7-day revenue data", e);
        }
        
        return result;
    }
    
    /**
     * Get top 5 selling items
     * @return List of maps with product_name and total_quantity
     */
    public List<Map<String, Object>> getTop5SellingItems() {
        List<Map<String, Object>> result = new ArrayList<>();
        
        String sql = """
            SELECT p.name as product_name, SUM(si.quantity) as total_quantity 
            FROM sale_items si 
            JOIN products p ON si.product_id = p.id 
            GROUP BY p.name 
            ORDER BY total_quantity DESC 
            LIMIT 5
            """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> itemData = new HashMap<>();
                itemData.put("productName", rs.getString("product_name"));
                itemData.put("quantity", rs.getInt("total_quantity"));
                result.add(itemData);
            }
            
            logger.info("Retrieved top 5 selling items: {} items", result.size());
            
        } catch (SQLException e) {
            logger.error("Error fetching top selling items", e);
        }
        
        return result;
    }
    
    /**
     * Get total revenue for today
     */
    public double getTodayRevenue() {
        String sql = "SELECT COALESCE(SUM(total), 0) as total FROM sales WHERE DATE(created_at) = DATE('now')";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getDouble("total");
            }
        } catch (SQLException e) {
            logger.error("Error fetching today's revenue", e);
        }
        
        return 0.0;
    }
    
    /**
     * Get total number of transactions today
     */
    public int getTodayTransactionCount() {
        String sql = "SELECT COUNT(*) as count FROM sales WHERE DATE(created_at) = DATE('now')";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            logger.error("Error fetching today's transaction count", e);
        }
        
        return 0;
    }
}