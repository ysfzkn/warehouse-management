-- V28: Add missing created_at to return_request_items (entity expects it)
ALTER TABLE return_request_items ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
