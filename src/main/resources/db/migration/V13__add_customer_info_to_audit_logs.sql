-- Add customer information and transfer reference to audit_logs for better tracking
ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS customer_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS customer_phone VARCHAR(20),
    ADD COLUMN IF NOT EXISTS transfer_id BIGINT;

-- Add index for transfer_id for faster queries
CREATE INDEX IF NOT EXISTS idx_audit_transfer_id ON audit_logs(transfer_id);
