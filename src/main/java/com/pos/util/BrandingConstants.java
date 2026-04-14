package com.pos.util;

/**
 * Branding Constants
 * Centralized store branding configuration for Vegas Supermarket POS.
 */
public class BrandingConstants {
    
    // Store Information
    public static final String STORE_NAME = "VEGAS SUPERMARKET";
    public static final String STORE_ADDRESS = "123 Main Street, Las Vegas, NV 89101";
    public static final String STORE_PHONE = "(702) 555-0100";
    public static final String STORE_EMAIL = "contact@vegassupermarket.com";
    
    // Receipt Footer Messages
    public static final String THANK_YOU_MESSAGE = "Thank You for Shopping at Vegas Supermarket!";
    public static final String RETURN_POLICY = "Items can be returned within 7 days with receipt.";
    public static final String LOYALTY_MESSAGE = "Join our loyalty program and save 10%!";
    
    // Application Title
    public static final String APP_TITLE = "Vegas Supermarket POS";
    public static final String LOGIN_TITLE = "Vegas Supermarket - Login";
    
    // Tagline
    public static final String TAGLINE = "Fresh Groceries, Best Prices!";
    
    // Currency Symbol (Kenyan Shillings)
    public static final String CURRENCY_SYMBOL = "KSh";
    
    // Terminal Settings
    public static final String DEFAULT_TERMINAL_ID = "T01";
    
    // Database folder name
    public static final String DATABASE_FOLDER = "VegasSupermarket";
    public static final String DATABASE_FILE = "vegas_pos.db";
    
    // Private constructor to prevent instantiation
    private BrandingConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
