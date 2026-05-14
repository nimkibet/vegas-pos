package com.pos.entity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line on a supplier stock-in: product, quantity received, and buying (cost) price.
 */
public class SupplierTransactionItem {

    private String id;
    private String transactionId;
    private String productId;
    private int quantityReceived;
    private BigDecimal buyingPrice;

    public SupplierTransactionItem() {
        this.id = UUID.randomUUID().toString();
        this.buyingPrice = BigDecimal.ZERO;
    }

    public SupplierTransactionItem(String id, String transactionId, String productId, int quantityReceived, BigDecimal buyingPrice) {
        this.id = id;
        this.transactionId = transactionId;
        this.productId = productId;
        this.quantityReceived = quantityReceived;
        this.buyingPrice = buyingPrice != null ? buyingPrice : BigDecimal.ZERO;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getQuantityReceived() {
        return quantityReceived;
    }

    public void setQuantityReceived(int quantityReceived) {
        this.quantityReceived = quantityReceived;
    }

    public BigDecimal getBuyingPrice() {
        return buyingPrice;
    }

    public void setBuyingPrice(BigDecimal buyingPrice) {
        this.buyingPrice = buyingPrice != null ? buyingPrice : BigDecimal.ZERO;
    }
}
