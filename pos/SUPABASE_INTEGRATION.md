# Supabase Integration Guide: Java POS ↔ Next.js Web App

This document outlines the necessary updates to the Supabase database schema to ensure the Java POS and the Next.js Web App can share data seamlessly.

## Current State Analysis

The Web App (`vegas/`) currently expects the following tables in Supabase:
- `products`: `id`, `name`, `stock_quantity`, `min_stock_level`, `category`
- `cloud_sales`: All columns from sales
- `cloud_sale_items`: `id`, `product_name`

The Java POS uses a richer schema in its local SQLite database. To bridge these, Supabase must be updated.

## Required Schema Updates in Supabase

Run these SQL commands in your Supabase SQL Editor:

### 1. Update `products` Table
The Java POS requires more detailed product information.

```sql
-- Add missing columns to products
ALTER TABLE products ADD COLUMN IF NOT EXISTS barcode TEXT UNIQUE;
ALTER TABLE products ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE products ADD COLUMN IF NOT EXISTS retail_price DECIMAL(12,2) DEFAULT 0.00;
ALTER TABLE products ADD COLUMN IF NOT EXISTS wholesale_price DECIMAL(12,2) DEFAULT 0.00;
ALTER TABLE products ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'APPROVED';
ALTER TABLE products ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT true;
ALTER TABLE products ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT now();
ALTER TABLE products ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT now();

-- Ensure indexes for performance
CREATE INDEX IF NOT EXISTS idx_products_barcode ON products(barcode);
CREATE INDEX IF NOT EXISTS idx_products_category ON products(category);
```

### 2. Create `cloud_sales` Table
This will store the finalized sales pushed from the Java POS.

```sql
CREATE TABLE IF NOT EXISTS cloud_sales (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID,
    total DECIMAL(12,2) NOT NULL,
    payment_method TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    notes TEXT,
    pos_id TEXT -- Original ID from the local Java POS
);
```

### 3. Create `cloud_sale_items` Table

```sql
CREATE TABLE IF NOT EXISTS cloud_sale_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sale_id UUID REFERENCES cloud_sales(id) ON DELETE CASCADE,
    product_name TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    total_price DECIMAL(12,2) NOT NULL
);
```

## Connection Strategy

1. **Java POS**: Will use a `SyncService` (to be implemented) that connects to Supabase via its REST API or a JDBC PostgreSQL wrapper.
2. **Web App**: Already configured to use the `supabase-js` client. It will instantly show changes made by the POS.

## Next Steps for Integration
- Implement the `SyncService` in Java to push local changes to Supabase.
- Update `vegas/app/page.tsx` to display the new fields (e.g., Retail Price, Barcode).
- Set up Realtime listeners in the Web App for instant inventory updates.
