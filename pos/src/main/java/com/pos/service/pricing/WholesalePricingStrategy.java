package com.pos.service.pricing;

import com.pos.entity.Product;
import java.math.BigDecimal;

/**
 * Wholesale Pricing Strategy
 * Returns the wholesale price of a product.
 */
public class WholesalePricingStrategy implements PricingStrategy {
    
    @Override
    public BigDecimal getPrice(Product product) {
        if (product == null || product.getWholesalePrice() == null) {
            return BigDecimal.ZERO;
        }
        return product.getWholesalePrice();
    }
    
    @Override
    public String getDisplayName() {
        return "Wholesale Price";
    }
}
