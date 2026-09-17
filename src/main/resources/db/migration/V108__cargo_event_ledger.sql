-- Kargo olay defteri.
--
-- 1) cargo_shipment_events: kargonun hareket geçmişi. Webhook ve yoklama işi sipariş başına
--    buraya yazar; hem admin panelindeki zaman çizelgesinin hem de "hangi kargo nerede takıldı"
--    raporunun kaynağıdır. Daha önce hareketler parse edilip atılıyordu.
--
-- 2) cargo_webhook_deliveries: gelen webhook'un ham kaydı. idempotency_key üzerindeki
--    benzersiz kısıt, aynı olayın ikinci kez işlenmesini veritabanı seviyesinde engeller —
--    önceki RAM içi çözüm yeniden başlatmada ve ikinci instance'ta duplicate işliyordu.

CREATE TABLE IF NOT EXISTS cargo_shipment_events (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT       NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    order_number    VARCHAR(50),
    tracking_no     VARCHAR(100),
    status_code     VARCHAR(60),
    status_label    VARCHAR(120),
    mapped_status   VARCHAR(30),
    description     VARCHAR(500),
    location        VARCHAR(200),
    occurred_at     TIMESTAMP,
    source          VARCHAR(40)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cargo_events_order ON cargo_shipment_events(order_id, occurred_at);

-- Aynı hareketin her sorguda yeniden yazılmasını engeller: sipariş + durum + zaman üçlüsü tektir.
CREATE UNIQUE INDEX IF NOT EXISTS uq_cargo_events_dedupe
    ON cargo_shipment_events(order_id, status_code, occurred_at)
    WHERE occurred_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS cargo_webhook_deliveries (
    id                BIGSERIAL PRIMARY KEY,
    idempotency_key   VARCHAR(200) NOT NULL UNIQUE,
    event_type        VARCHAR(60),
    shipment_id       VARCHAR(100),
    order_id          BIGINT,
    order_number      VARCHAR(50),
    attempt_number    INTEGER,
    status            VARCHAR(30)  NOT NULL,
    error_message     VARCHAR(500),
    payload           TEXT,
    received_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cargo_webhook_received ON cargo_webhook_deliveries(received_at);
