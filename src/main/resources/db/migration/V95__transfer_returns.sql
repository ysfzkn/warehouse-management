-- Sevkiyat iadesi: depodan çıkan mal geri geldiğinde.
--
-- Servise teslim edilen mal müşteriye ulaşmayabiliyor — adres bulunamıyor, alıcı kabul
-- etmiyor, ürün hasarlı çıkıyor. Mal depoya geri dönüyor ama sistemde karşılığı yoktu:
-- COMPLETED bir sevkiyat iptal edilemiyor (CANNOT_CANCEL_COMPLETED) ve stok düşmüş
-- hâlde kalıyordu. Tek çare elle stok eklemekti, ki bu da hareketin neden yapıldığını
-- kaydın dışında bırakıyordu.
--
-- İade sevkiyatı geri almaz. Mal çıktı — bu bir olgu ve sevkiyat COMPLETED kalıyor.
-- İade, o çıkışın üzerine yazılan ayrı bir olay: ne zaman, ne kadarı, hangi sebeple
-- döndü. Kısmi ve tekrarlı olabilir, çünkü gerçekte öyle oluyor: üç kalemin biri bugün,
-- bir diğeri gelecek hafta dönebiliyor.

CREATE TABLE IF NOT EXISTS transfer_returns (
    id                  BIGSERIAL PRIMARY KEY,
    stock_transfer_id   BIGINT       NOT NULL,
    returned_at         TIMESTAMP    NOT NULL,
    reason              VARCHAR(30)  NOT NULL,
    note                VARCHAR(1000),
    total_quantity      INTEGER      NOT NULL,
    recorded_by         VARCHAR(100),
    created_at          TIMESTAMP    NOT NULL,

    CONSTRAINT fk_transfer_returns_transfer
        FOREIGN KEY (stock_transfer_id) REFERENCES stock_transfers (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_transfer_returns_transfer
    ON transfer_returns (stock_transfer_id);
CREATE INDEX IF NOT EXISTS idx_transfer_returns_returned_at
    ON transfer_returns (returned_at DESC);

CREATE TABLE IF NOT EXISTS transfer_return_items (
    id                      BIGSERIAL PRIMARY KEY,
    transfer_return_id      BIGINT  NOT NULL,
    stock_transfer_item_id  BIGINT  NOT NULL,
    product_id              BIGINT,
    quantity                INTEGER NOT NULL,

    CONSTRAINT fk_transfer_return_items_return
        FOREIGN KEY (transfer_return_id) REFERENCES transfer_returns (id) ON DELETE CASCADE,
    CONSTRAINT fk_transfer_return_items_transfer_item
        FOREIGN KEY (stock_transfer_item_id) REFERENCES stock_transfer_items (id)
);

CREATE INDEX IF NOT EXISTS idx_transfer_return_items_return
    ON transfer_return_items (transfer_return_id);

-- İade edilen miktarlar hem satır hem sevkiyat düzeyinde tutuluyor. transfer_returns'ü
-- toplayarak da bulunabilirdi; burada durmalarının sebebi bu iki değerin her okumada
-- gerekmesi: satırdaki "bu kalemden daha fazlası iade edilemez" kuralı ve listedeki
-- "kısmen iade / iade edildi" rozeti. Her ikisi de yazma anında hesaplanıyor.
ALTER TABLE stock_transfers
    ADD COLUMN IF NOT EXISTS returned_quantity INTEGER NOT NULL DEFAULT 0;
ALTER TABLE stock_transfer_items
    ADD COLUMN IF NOT EXISTS returned_quantity INTEGER NOT NULL DEFAULT 0;

COMMENT ON TABLE transfer_returns IS
    'Depodan çıkan malın geri dönüşü. Sevkiyatı iptal etmez; üzerine yazılan ayrı bir olaydır.';
COMMENT ON COLUMN stock_transfers.returned_quantity IS
    'transfer_returns toplamı. Listede rozet basmak için denormalize tutulur.';
COMMENT ON COLUMN stock_transfer_items.returned_quantity IS
    'Bu kalemden iade edilen toplam adet. Sevk edilenden fazlası iade edilemez.';
