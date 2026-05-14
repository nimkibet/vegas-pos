package com.pos.entity;

/**
 * Result of resolving a scanned or keyed barcode against {@code products.barcode} and {@code products.bulk_barcode}.
 */
public final class ProductBarcodeMatch {

    public enum MatchType {
        /** Matched the unit (single) barcode. */
        SINGLE,
        /** Matched the box / bulk barcode. */
        BULK
    }

    private final Product product;
    private final MatchType matchType;

    public ProductBarcodeMatch(Product product, MatchType matchType) {
        this.product = product;
        this.matchType = matchType;
    }

    public Product getProduct() {
        return product;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public boolean isBulk() {
        return matchType == MatchType.BULK;
    }
}
