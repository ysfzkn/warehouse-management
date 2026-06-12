-- Color variants — linking the same product in different colors.
--
-- The system has no native variant concept: each color of a product is a fully
-- independent `products` row (own SKU, stock, images, reviews) carrying a single
-- color_id. To present "this product is also available in these colors" we link
-- such rows with a shared variant_group_id.
--
-- A small `product_variant_groups` table owns the group identity (so we can mint a
-- new id and keep referential integrity). Products sharing a variant_group_id are
-- color variants of each other. A group with fewer than 2 members is emptied by the
-- service layer. Each variant keeps its own color, stock and storefront page.

CREATE TABLE IF NOT EXISTS product_variant_groups (
    id         BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE products ADD COLUMN IF NOT EXISTS variant_group_id BIGINT NULL;

ALTER TABLE products ADD CONSTRAINT fk_products_variant_group
    FOREIGN KEY (variant_group_id) REFERENCES product_variant_groups (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_products_variant_group_id ON products (variant_group_id);
