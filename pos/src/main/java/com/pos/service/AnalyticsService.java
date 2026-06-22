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
        return getRevenueChartData(LocalDate.now().minusDays(6), LocalDate.now(), false);
    }

    /**
     * Get revenue data for a date range, grouped by Day or Month
     * @param startDate the start date of the range
     * @param endDate the end date of the range
     * @param groupByMonth true to group by month, false to group by day
     * @return List of maps with "date" and "revenue"
     */
    public List<Map<String, Object>> getRevenueChartData(LocalDate startDate, LocalDate endDate, boolean groupByMonth) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        // Prepare continuous periods to avoid empty gaps in the chart
        Map<String, Double> periodMap = new HashMap<>();
        List<String> periods = new ArrayList<>();
        
        if (groupByMonth) {
            LocalDate current = startDate.withDayOfMonth(1);
            LocalDate limit = endDate.withDayOfMonth(1);
            while (!current.isAfter(limit)) {
                String period = String.format("%04d-%02d", current.getYear(), current.getMonthValue());
                if (!periodMap.containsKey(period)) {
                    periodMap.put(period, 0.0);
                    periods.add(period);
                }
                current = current.plusMonths(1);
            }
        } else {
            LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                String period = current.toString();
                periodMap.put(period, 0.0);
                periods.add(period);
                current = current.plusDays(1);
            }
        }
        
        String sql;
        if (groupByMonth) {
            sql = """
                SELECT strftime('%Y-%m', created_at) as period, SUM(CAST(total AS REAL)) as revenue 
                FROM sales 
                WHERE DATE(created_at) BETWEEN ? AND ? 
                AND status = 'COMPLETED'
                GROUP BY period
                ORDER BY period ASC
                """;
        } else {
            sql = """
                SELECT DATE(created_at) as period, SUM(CAST(total AS REAL)) as revenue 
                FROM sales 
                WHERE DATE(created_at) BETWEEN ? AND ? 
                AND status = 'COMPLETED'
                GROUP BY DATE(created_at)
                ORDER BY period ASC
                """;
        }
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, startDate.toString());
            stmt.setString(2, endDate.toString());
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String period = rs.getString("period");
                    double revenue = rs.getDouble("revenue");
                    if (periodMap.containsKey(period)) {
                        periodMap.put(period, revenue);
                    } else {
                        periodMap.put(period, revenue);
                        periods.add(period);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching revenue chart data", e);
        }
        
        // Convert map to list in chronological order
        for (String period : periods) {
            Map<String, Object> periodData = new HashMap<>();
            periodData.put("date", period);
            periodData.put("revenue", periodMap.get(period));
            result.add(periodData);
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
        return getRevenue(LocalDate.now(), LocalDate.now());
    }

    /**
     * Get total revenue for a given date range
     */
    public double getRevenue(LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT COALESCE(SUM(CAST(total AS REAL)), 0) as total 
            FROM sales 
            WHERE DATE(created_at) BETWEEN ? AND ?
            AND status = 'COMPLETED'
            """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, startDate.toString());
            stmt.setString(2, endDate.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("total");
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching revenue for date range", e);
        }
        return 0.0;
    }
    
    /**
     * Get total number of transactions today
     */
    public int getTodayTransactionCount() {
        return getTransactionCount(LocalDate.now(), LocalDate.now());
    }

    /**
     * Get total number of transactions for a given date range
     */
    public int getTransactionCount(LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT COUNT(*) as count 
            FROM sales 
            WHERE DATE(created_at) BETWEEN ? AND ?
            AND status = 'COMPLETED'
            """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, startDate.toString());
            stmt.setString(2, endDate.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching transaction count for date range", e);
        }
        return 0;
    }

    /**
     * Calculate net profit for a given date range
     */
    public double calculateNetProfit(LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT SUM((CAST(si.unit_price AS REAL) - COALESCE(si.unit_cogs, 0.0)) * si.quantity) as net_profit
            FROM sale_items si
            JOIN sales s ON si.sale_id = s.id
            WHERE DATE(s.created_at) BETWEEN ? AND ?
            AND s.status = 'COMPLETED'
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, startDate.toString());
            stmt.setString(2, endDate.toString());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("net_profit");
                }
            }
        } catch (SQLException e) {
            logger.error("Error calculating net profit", e);
        }
        
        return 0.0;
    }

    /**
     * Get top selling categories
     */
    public List<Map<String, Object>> getTopSellingCategories() {
        List<Map<String, Object>> result = new ArrayList<>();
        
        String sql = """
            SELECT p.category, SUM(si.quantity) as total_sold
            FROM sale_items si
            JOIN products p ON si.product_id = p.id
            JOIN sales s ON si.sale_id = s.id
            WHERE s.status = 'COMPLETED'
            GROUP BY p.category
            ORDER BY total_sold DESC
            LIMIT 5
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> catData = new HashMap<>();
                catData.put("category", rs.getString("category"));
                catData.put("totalSold", rs.getInt("total_sold"));
                result.add(catData);
            }
        } catch (SQLException e) {
            logger.error("Error fetching top selling categories", e);
        }
        
        return result;
    }
}