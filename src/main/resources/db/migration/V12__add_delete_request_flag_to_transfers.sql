-- Flag transfer deletion requests instead of using approval_note
ALTER TABLE stock_transfers
    ADD COLUMN IF NOT EXISTS delete_request BOOLEAN NOT NULL DEFAULT FALSE;
