-- =================================================================
-- V25: Payment layer enhancements - new providers, timeout status, bank transfer support
-- Rollback: Reverse ALTER statements, DROP new columns
-- =================================================================

-- Expand payment provider constraint
ALTER TABLE payment_transactions DROP CONSTRAINT IF EXISTS chk_payment_provider;
ALTER TABLE payment_transactions ADD CONSTRAINT chk_payment_provider
    CHECK (payment_provider IN ('IYZICO', 'DOOR_PAYMENT', 'BANK_TRANSFER', 'PAYTR'));

-- Expand payment status constraint (add TIMEOUT)
ALTER TABLE payment_transactions DROP CONSTRAINT IF EXISTS chk_payment_status;
ALTER TABLE payment_transactions ADD CONSTRAINT chk_payment_status
    CHECK (status IN ('INITIATED', 'PROCESSING', 'SUCCESS', 'FAILED', 'TIMEOUT',
                      'REFUND_PENDING', 'REFUNDED', 'PARTIAL_REFUNDED'));

-- New columns for bank transfer and gateway token
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS bank_transfer_ref VARCHAR(50);
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS token VARCHAR(500);
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

-- Index for timeout job queries
CREATE INDEX IF NOT EXISTS idx_payments_expires
    ON payment_transactions (expires_at)
    WHERE expires_at IS NOT NULL AND status IN ('PROCESSING', 'INITIATED');

CREATE INDEX IF NOT EXISTS idx_payments_token
    ON payment_transactions (token)
    WHERE token IS NOT NULL;

-- Expand order payment method constraint
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_order_payment;
ALTER TABLE orders ADD CONSTRAINT chk_order_payment
    CHECK (payment_method IS NULL OR payment_method IN (
        'CREDIT_CARD', 'DOOR_CASH', 'DOOR_CARD', 'BANK_TRANSFER'));

-- Bank transfer deadline on orders
ALTER TABLE orders ADD COLUMN IF NOT EXISTS bank_transfer_deadline TIMESTAMP;
