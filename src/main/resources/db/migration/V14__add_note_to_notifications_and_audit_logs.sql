-- Add user-provided note column to notifications and audit_logs
-- Used for optional notes during stock add/remove operations
ALTER TABLE notifications ADD COLUMN note VARCHAR(500);
ALTER TABLE audit_logs ADD COLUMN note VARCHAR(500);
