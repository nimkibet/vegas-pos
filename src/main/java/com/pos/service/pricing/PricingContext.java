package com.pos.service.pricing;

import com.pos.entity.Product;
import java.math.BigDecimal;

/**
 * Pricing Context
 * Manages the current pricing strategy and allows switching between retail and wholesale.
 */
public class PricingContext {
    
    private PricingStrategy currentStrategy;
    
    /**
     * Default constructor - uses retail pricing by default
     */
    public PricingContext() {
        this.currentStrategy = new RetailPricingStrategy();
    }
    
    /**
     * Constructor with initial strategy
     */
    public PricingContext(PricingStrategy strategy) {
        this.currentStrategy = strategy;
    }
    
    /**
     * Set the pricing strategy
     */
    public void setStrategy(PricingStrategy strategy) {
        this.currentStrategy = strategy;
    }
    
    /**
     * Get the current pricing strategy
     */
    public PricingStrategy getStrategy() {
        return currentStrategy;
    }
    
    /**
     * Get the price for a product using the current strategy
     */
    public BigDecimal getPrice(Product product) {
        return currentStrategy.getPrice(product);
    }
    
    /**
     * Switch to retail pricing
     */
    public void useRetailPricing() {
        this.currentStrategy = new RetailPricingStrategy();
    }
    
    /**
     * Switch to wholesale pricing
     */
    public void useWholesalePricing() {
        this.currentStrategy = new WholesalePricingStrategy();
    }
    
    /**
     * Check if current strategy is wholesale
     */
    public boolean isWholesaleMode() {
        return currentStrategy instanceof WholesalePricingStrategy;
    }
    
    /**
     * Get the display name of the current strategy
     */
    public String getCurrentStrategyName() {
        return currentStrategy.getDisplayName();
    }
}
