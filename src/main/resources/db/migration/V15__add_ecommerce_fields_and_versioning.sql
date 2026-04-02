-- =================================================================
-- V15: Add e-commerce fields to existing tables + optimistic locking
-- Rollback: ALTER TABLE DROP COLUMN for each added column
-- =================================================================

-- -------------------------
-- PRODUCTS: Slug + SEO + Sale + Stats + Versioning
-- -------------------------
ALTER TABLE products ADD COLUMN slug VARCHAR(200);
ALTER TABLE products ADD COLUMN meta_title VARCHAR(200);
ALTER TABLE products ADD COLUMN meta_description VARCHAR(500);
ALTER TABLE products ADD COLUMN short_description VARCHAR(1000);
ALTER TABLE products ADD COLUMN is_featured BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE products ADD COLUMN is_new BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE products ADD COLUMN sale_price NUMERIC(10, 2);
ALTER TABLE products ADD COLUMN sale_start TIMESTAMP;
ALTER TABLE products ADD COLUMN sale_end TIMESTAMP;
ALTER TABLE products ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE products ADD COLUMN sold_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE products ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Generate slugs for existing products (Turkish transliteration + id suffix)
UPDATE products SET slug = LOWER(
    REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
        REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
            name,
            ' ', '-'), 'ı', 'i'), 'ğ', 'g'), 'ü', 'u'), 'ş', 's'), 'ö', 'o'), 'ç', 'c'),
            'İ', 'i'), 'Ğ', 'g'), 'Ü', 'u'), 'Ş', 's'), 'Ö', 'o')
) || '-' || id;

ALTER TABLE products ALTER COLUMN slug SET NOT NULL;

CREATE UNIQUE INDEX idx_products_slug ON products (slug);
CREATE INDEX idx_products_featured ON products (is_featured) WHERE is_featured = TRUE;
CREATE INDEX idx_products_new ON products (is_new) WHERE is_new = TRUE;
CREATE INDEX idx_products_sale ON products (sale_start, sale_end) WHERE sale_price IS NOT NULL;

ALTER TABLE products ADD CONSTRAINT chk_products_sale_price CHECK (sale_price IS NULL OR sale_price >= 0);

-- -------------------------
-- CATEGORIES: Slug + Menu settings
-- -------------------------
ALTER TABLE categories ADD COLUMN slug VARCHAR(100);
ALTER TABLE categories ADD COLUMN image_url VARCHAR(500);
ALTER TABLE categories ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;
ALTER TABLE categories ADD COLUMN show_in_menu BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE categories ADD COLUMN meta_title VARCHAR(200);
ALTER TABLE categories ADD COLUMN meta_description VARCHAR(500);

UPDATE categories SET slug = LOWER(
    REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
        REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
            name,
            ' ', '-'), 'ı', 'i'), 'ğ', 'g'), 'ü', 'u'), 'ş', 's'), 'ö', 'o'), 'ç', 'c'),
            'İ', 'i'), 'Ğ', 'g'), 'Ü', 'u'), 'Ş', 's'), 'Ö', 'o')
) || '-' || id;

ALTER TABLE categories ALTER COLUMN slug SET NOT NULL;
CREATE UNIQUE INDEX idx_categories_slug ON categories (slug);

-- -------------------------
-- BRANDS: Slug + Logo
-- -------------------------
ALTER TABLE brands ADD COLUMN slug VARCHAR(100);
ALTER TABLE brands ADD COLUMN logo_url VARCHAR(500);

UPDATE brands SET slug = LOWER(
    REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
        REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
            name,
            ' ', '-'), 'ı', 'i'), 'ğ', 'g'), 'ü', 'u'), 'ş', 's'), 'ö', 'o'), 'ç', 'c'),
            'İ', 'i'), 'Ğ', 'g'), 'Ü', 'u'), 'Ş', 's'), 'Ö', 'o')
) || '-' || id;

ALTER TABLE brands ALTER COLUMN slug SET NOT NULL;
CREATE UNIQUE INDEX idx_brands_slug ON brands (slug);

-- -------------------------
-- STOCKS: Optimistic locking version
-- -------------------------
ALTER TABLE stocks ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
