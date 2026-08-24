ALTER TABLE orders ADD COLUMN IF NOT EXISTS order_channel VARCHAR(30) NOT NULL DEFAULT 'ONLINE';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS channel_reference VARCHAR(100);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS created_by_admin VARCHAR(150);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS manual_payment_state VARCHAR(30);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_due_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_reminder_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_reminder_sent_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_received_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_orders_channel ON orders(order_channel);
CREATE INDEX IF NOT EXISTS idx_orders_payment_reminder ON orders(payment_reminder_at)
    WHERE payment_reminder_sent_at IS NULL;

ALTER TABLE products ADD COLUMN IF NOT EXISTS ecommerce_visible BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX IF NOT EXISTS idx_products_ecommerce_visible ON products(ecommerce_visible);
