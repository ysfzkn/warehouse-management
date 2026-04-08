-- =================================================================
-- V22: Customer status management (ACTIVE/INACTIVE/BLACKLISTED) + admin notes
-- Rollback: ALTER TABLE customers DROP COLUMN status, status_note, status_changed_at, status_changed_by;
-- =================================================================

ALTER TABLE customers ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE customers ADD COLUMN status_note VARCHAR(500);
ALTER TABLE customers ADD COLUMN status_changed_at TIMESTAMP;
ALTER TABLE customers ADD COLUMN status_changed_by VARCHAR(100);

-- Sync existing is_active field with new status column
UPDATE customers SET status = CASE WHEN is_active = true THEN 'ACTIVE' ELSE 'INACTIVE' END;

CREATE INDEX idx_customers_status ON customers (status);

ALTER TABLE customers ADD CONSTRAINT chk_customer_status
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLACKLISTED'));
