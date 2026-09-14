-- Kargo sağlayıcısının ham durumu (Kargonomi'nin 13 statüsünden biri) sipariş üzerinde saklanır.
-- İki işe yarıyor:
--   1) Admin panelinde "teslim edilemedi / kayıp / geri geliyor" gibi durumlar görünür hale gelir.
--   2) Aynı durum tekrar tekrar geldiğinde (webhook yeniden denemeleri, yoklama işi)
--      uyarı ve bildirim üretmemek için karşılaştırma noktası olur.
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS cargo_status VARCHAR(60),
    ADD COLUMN IF NOT EXISTS cargo_status_updated_at TIMESTAMP;

-- Yoklama işi "kargoda + takip no'su olan + en son şu tarihten önce sorgulanmış" siparişleri çeker.
CREATE INDEX IF NOT EXISTS idx_orders_cargo_polling
    ON orders (status, cargo_last_tracked_at)
    WHERE cargo_tracking_no IS NOT NULL;
