-- Add optional note for stock creation records
ALTER TABLE stocks
    ADD COLUMN addition_note VARCHAR(500);

-- Extend stock transfers with approval tracking fields
ALTER TABLE stock_transfers
    ADD COLUMN approval_status VARCHAR(20) NOT NULL DEFAULT 'NONE';

ALTER TABLE stock_transfers
    ADD COLUMN approval_requested_by VARCHAR(100);

ALTER TABLE stock_transfers
    ADD COLUMN approval_requested_at TIMESTAMP;

ALTER TABLE stock_transfers
    ADD COLUMN approval_decision_by VARCHAR(100);

ALTER TABLE stock_transfers
    ADD COLUMN approval_decision_at TIMESTAMP;

ALTER TABLE stock_transfers
    ADD COLUMN approval_note VARCHAR(500);

