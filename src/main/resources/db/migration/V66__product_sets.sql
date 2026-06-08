-- Product Sets / Bundles (e.g. "Ankastre Set").
--
-- A set is sold as a single storefront item but is composed of multiple existing
-- products. We model a set as a normal `products` row with product_type = 'BUNDLE',
-- so it reuses the entire product infrastructure (slug, images, price, category,
-- cart, order). Member products are linked via the `bundle_items` table.
--
-- A bundle has NO stock rows of its own; its availability is derived from its
-- members, and on checkout each member's stock is reserved.

ALTER TABLE products ADD COLUMN IF NOT EXISTS product_type VARCHAR(20) NOT NULL DEFAULT 'SIMPLE';

CREATE TABLE IF NOT EXISTS bundle_items (
    id          BIGSERIAL PRIMARY KEY,
    bundle_id   BIGINT  NOT NULL,
    product_id  BIGINT  NOT NULL,
    quantity    INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    sort_order  INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bundle_items_bundle  FOREIGN KEY (bundle_id)  REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_bundle_items_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT,
    CONSTRAINT uq_bundle_items UNIQUE (bundle_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_bundle_items_bundle  ON bundle_items (bundle_id);
CREATE INDEX IF NOT EXISTS idx_bundle_items_product ON bundle_items (product_id);
