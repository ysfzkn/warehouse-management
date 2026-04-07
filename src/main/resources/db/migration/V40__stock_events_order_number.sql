-- Link stock events back to the order that caused them (denormalised for audit history).
-- order_number is preserved even if the order is later deleted.
ALTER TABLE stock_events
    ADD COLUMN IF NOT EXISTS order_number VARCHAR(30);

CREATE INDEX IF NOT EXISTS idx_stock_events_order_number
    ON stock_events(order_number)
    WHERE order_number IS NOT NULL;
