-- Security hardening.
--
-- 1) orders.payment_access_token
--    POST /api/store/payment/initialize is necessarily public (guests pay without an
--    account) but it performed no ownership check on the orderId it was given. Order
--    ids are sequential, so anyone could walk them and, for example, re-initialise a
--    stranger's pending order as "cash on delivery" — moving it to PREPARING and having
--    it shipped without payment. Checkout now issues a short-lived, high-entropy token
--    bound to the order; payment initialisation requires either that token or an
--    authenticated session that owns the order.
--
-- 2) tc_kimlik_no widening
--    National identity numbers are now encrypted at rest (AES-256-GCM), and ciphertext
--    does not fit in the original 11-character column.

ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_access_token VARCHAR(64);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_access_token_expires_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_orders_payment_access_token
    ON orders (payment_access_token);

ALTER TABLE customers      ALTER COLUMN tc_kimlik_no TYPE VARCHAR(255);
ALTER TABLE customer_addresses ALTER COLUMN tc_kimlik_no TYPE VARCHAR(255);
