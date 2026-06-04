-- =================================================================
-- V21: Stock events for event-driven cache invalidation
-- Rollback: DROP TABLE stock_events;
-- =================================================================

CREATE TABLE stock_events (
    id              BIGSERIAL PRIMARY KEY,
    stock_id        BIGINT REFERENCES stocks(id) ON DELETE SET NULL,
    product_id      BIGINT REFERENCES products(id) ON DELETE SET NULL,
    event_type      VARCHAR(30) NOT NULL,
    old_value       INTEGER,
    new_value       INTEGER,
    source          VARCHAR(30) NOT NULL DEFAULT 'SYSTEM',
    source_detail   VARCHAR(200),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_event_type CHECK (event_type IN (
        'QUANTITY_CHANGED', 'RESERVED', 'RELEASED',
        'LOW_STOCK', 'OUT_OF_STOCK', 'BACK_IN_STOCK'
    )),
    CONSTRAINT chk_event_source CHECK (source IN (
        'ADMIN', 'TRANSFER', 'ORDER', 'SYSTEM', 'IMPORT'
    ))
);

CREATE INDEX idx_stock_events_stock ON stock_events (stock_id);
CREATE INDEX idx_stock_events_product ON stock_events (product_id);
CREATE INDEX idx_stock_events_type ON stock_events (event_type);
CREATE INDEX idx_stock_events_created ON stock_events (created_at DESC);

-- Cleanup policy: auto-delete events older than 90 days (run via scheduled task)
COMMENT ON TABLE stock_events IS 'Event log for stock changes. Used for cache invalidation and audit. Prune entries older than 90 days.';
