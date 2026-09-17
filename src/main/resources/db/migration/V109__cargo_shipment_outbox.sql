-- Gönderi outbox'ı.
--
-- Kargo API'si yanıt vermediğinde createShipmentForOrder kullanıcıya "sipariş kuyruğa alındı"
-- diyordu ama ortada kuyruk yoktu; gönderi sadece log satırı bırakıp kayboluyordu.
-- Başarısız her gönderi artık buraya düşer, artan aralıklarla yeniden denenir,
-- deneme hakkı bitince admin'e bildirim gider.

CREATE TABLE IF NOT EXISTS cargo_shipment_outbox (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT       NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    order_number    VARCHAR(50),
    status          VARCHAR(20)  NOT NULL,   -- PENDING | SUCCEEDED | ABANDONED
    attempts        INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP    NOT NULL,
    last_error_code VARCHAR(60),
    last_error      VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- İşin her turda çektiği satırlar: sırası gelmiş bekleyenler.
CREATE INDEX IF NOT EXISTS idx_cargo_outbox_due
    ON cargo_shipment_outbox(status, next_attempt_at);
