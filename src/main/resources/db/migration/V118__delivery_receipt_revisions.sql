-- Depo çıkış makbuzu içerik düzenlemelerinin geçmişi.
--
-- Düzenleme geçmişe dönük stok hareketi yaratıyor ve imzalı kâğıdı geçersiz kılıyor. Genel
-- denetim kaydı (audit_logs) bunu tek satırlık serbest metin olarak tutuyor: hangi kalemin
-- kaçtan kaça indiği ancak metni okuyarak çıkarılabiliyor ve 2000 karakterde kesiliyor.
-- Burada her düzenleme ayrı satır: kim, ne zaman, neden, depo önce/sonra ve kalemler
-- önce/sonra. "Önce" makbuzun o ana kadarki hâli — yani elde dolaşan kâğıtta yazan.

CREATE TABLE IF NOT EXISTS delivery_receipt_revisions (
    id                        BIGSERIAL PRIMARY KEY,
    receipt_id                BIGINT       NOT NULL REFERENCES delivery_receipts (id) ON DELETE CASCADE,
    revision                  INTEGER      NOT NULL,
    edited_at                 TIMESTAMP    NOT NULL,
    edited_by                 VARCHAR(100) NOT NULL,
    reason                    VARCHAR(300) NOT NULL,
    source_warehouse_before   VARCHAR(150),
    source_warehouse_after    VARCHAR(150),
    items_before_json         TEXT         NOT NULL,
    items_after_json          TEXT         NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_delivery_receipt_revisions_receipt
    ON delivery_receipt_revisions (receipt_id, edited_at DESC);

-- Arşiv listesi her satırda "düzenlendi" bilgisini gösteriyor. Satır başına geçmiş
-- tablosuna gitmek yerine makbuzda tutuluyor; yazan tek yol içerik düzenlemesi.
ALTER TABLE delivery_receipts ADD COLUMN IF NOT EXISTS edit_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE delivery_receipts ADD COLUMN IF NOT EXISTS last_edited_at TIMESTAMP;
ALTER TABLE delivery_receipts ADD COLUMN IF NOT EXISTS last_edited_by VARCHAR(100);
