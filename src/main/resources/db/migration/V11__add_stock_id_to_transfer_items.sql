-- Add stock_id to stock_transfer_items to reference specific stock rows (emanet/müşteri bazlı)
ALTER TABLE stock_transfer_items
    ADD COLUMN IF NOT EXISTS stock_id BIGINT;

ALTER TABLE stock_transfer_items
    ADD CONSTRAINT fk_transfer_items_stock
    FOREIGN KEY (stock_id)
    REFERENCES stocks (id)
    ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_stock_transfer_items_stock ON stock_transfer_items (stock_id);

