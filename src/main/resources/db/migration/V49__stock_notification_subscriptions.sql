-- Stokta yoksa bildir: müşteri abonelikleri
CREATE TABLE stock_notification_subscriptions (
    id                      BIGSERIAL PRIMARY KEY,
    product_id              BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    customer_id             BIGINT REFERENCES customers(id) ON DELETE SET NULL,
    email                   VARCHAR(255) NOT NULL,
    notified                BOOLEAN NOT NULL DEFAULT FALSE,
    notified_at             TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stock_notif_product ON stock_notification_subscriptions(product_id);
CREATE INDEX idx_stock_notif_email ON stock_notification_subscriptions(email);
CREATE INDEX idx_stock_notif_pending ON stock_notification_subscriptions(product_id, notified) WHERE notified = FALSE;

-- Aynı kullanıcı aynı ürün için birden fazla abonelik oluşturmasın
CREATE UNIQUE INDEX idx_stock_notif_unique_pending
    ON stock_notification_subscriptions(product_id, email)
    WHERE notified = FALSE;
