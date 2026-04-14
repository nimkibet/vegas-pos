package com.pos.service.pricing;

import com.pos.entity.Product;
import java.math.BigDecimal;

/**
 * Retail Pricing Strategy
 * Returns the retail price of a product.
 */
public class RetailPricingStrategy implements PricingStrategy {
    
    @Override
    public BigDecimal getPrice(Product product) {
        if (product == null || product.getRetailPrice() == null) {
            return BigDecimal.ZERO;
        }
        return product.getRetailPrice();
    }
    
    @Override
    public String getDisplayName() {
        return "Retail Price";
    }
}
