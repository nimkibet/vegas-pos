package com.pos.service.pricing;

import com.pos.entity.Product;
import java.math.BigDecimal;

/**
 * Pricing Strategy Interface
 * Implements the Strategy Pattern for Retail vs Wholesale pricing.
 */
public interface PricingStrategy {
    
    /**
     * Get the price for a product based on the pricing strategy
     * @param product The product to get price for
     * @return The price as BigDecimal
     */
    BigDecimal getPrice(Product product);
    
    /**
     * Get the display name of the pricing strategy
     * @return The display name
     */
    String getDisplayName();
}
