ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_confirmation_token_hash VARCHAR(64);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_confirmation_expires_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_confirmed_at TIMESTAMP;
CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_confirmation_token_hash
    ON orders(customer_confirmation_token_hash)
    WHERE customer_confirmation_token_hash IS NOT NULL;
