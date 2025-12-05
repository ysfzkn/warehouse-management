-- Enrich notifications with actor and warehouse/product context for advanced filtering
ALTER TABLE notifications
    ADD COLUMN actor VARCHAR(100),
    ADD COLUMN warehouse_id BIGINT,
    ADD COLUMN warehouse_name VARCHAR(150),
    ADD COLUMN source_warehouse_id BIGINT,
    ADD COLUMN source_warehouse_name VARCHAR(150),
    ADD COLUMN destination_warehouse_id BIGINT,
    ADD COLUMN destination_warehouse_name VARCHAR(150),
    ADD COLUMN product_id BIGINT,
    ADD COLUMN product_name VARCHAR(200),
    ADD COLUMN product_sku VARCHAR(100),
    ADD COLUMN quantity INTEGER;

CREATE INDEX idx_notif_actor ON notifications (actor);
CREATE INDEX idx_notif_warehouse_id ON notifications (warehouse_id);
CREATE INDEX idx_notif_source_warehouse_id ON notifications (source_warehouse_id);
CREATE INDEX idx_notif_destination_warehouse_id ON notifications (destination_warehouse_id);
CREATE INDEX idx_notif_product_sku ON notifications (product_sku);

-- Enrich audit logs to store structured warehouse/product metadata for warehouse activity views
ALTER TABLE audit_logs
    ADD COLUMN warehouse_id BIGINT,
    ADD COLUMN warehouse_name VARCHAR(150),
    ADD COLUMN source_warehouse_id BIGINT,
    ADD COLUMN source_warehouse_name VARCHAR(150),
    ADD COLUMN destination_warehouse_id BIGINT,
    ADD COLUMN destination_warehouse_name VARCHAR(150),
    ADD COLUMN product_id BIGINT,
    ADD COLUMN product_name VARCHAR(200),
    ADD COLUMN product_sku VARCHAR(100),
    ADD COLUMN quantity INTEGER;

CREATE INDEX idx_audit_warehouse_id ON audit_logs (warehouse_id);
CREATE INDEX idx_audit_source_warehouse_id ON audit_logs (source_warehouse_id);
CREATE INDEX idx_audit_destination_warehouse_id ON audit_logs (destination_warehouse_id);
CREATE INDEX idx_audit_product_sku ON audit_logs (product_sku);
CREATE INDEX idx_audit_created_at_warehouse ON audit_logs (created_at, warehouse_id);

