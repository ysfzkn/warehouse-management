-- Teslimat makbuzu (müşteri sevkiyatı).
--
-- Müşteriye çıkan transferlerde şoför elinde imzalı bir kâğıt bırakıyor, ama o kâğıdın
-- sistemde hiçbir karşılığı yoktu: kim teslim aldı, hangi tarihte, hangi plakayla,
-- imzalı nüsha nerede — hepsi kâğıt klasöründeydi. Bu tablo makbuzu kayıt altına alır.
--
-- Alanların çoğu stock_transfers'ta zaten var ama burada **kopyalanıyor**. Sebebi
-- kasıtlı: makbuz basıldıktan sonra transferde şoför değişse veya müşteri adresi
-- düzeltilse, imzalanan kâğıtla sistemdeki kayıt birbirini tutmaz hâle gelirdi. Makbuz
-- basıldığı andaki gerçeği dondurur; yeniden basım gerekirse revision artırılır ve eski
-- değerler denetim kaydında kalır.

CREATE TABLE IF NOT EXISTS delivery_receipts (
    id                      BIGSERIAL PRIMARY KEY,
    stock_transfer_id       BIGINT NOT NULL,
    receipt_no              VARCHAR(30)  NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    revision                INTEGER      NOT NULL DEFAULT 1,

    -- Basım anındaki firma künyesi (site_settings'ten kopyalanır)
    company_name            VARCHAR(200),
    company_address         VARCHAR(500),
    company_phone           VARCHAR(100),

    -- Basım anındaki sevkiyat bilgileri
    source_warehouse_name   VARCHAR(150),
    customer_full_name      VARCHAR(150),
    customer_phone          VARCHAR(30),
    customer_address        VARCHAR(500),
    order_number            VARCHAR(50),
    driver_name             VARCHAR(100),
    driver_phone            VARCHAR(30),
    vehicle_plate           VARCHAR(20),
    transfer_date           TIMESTAMP,
    items_json              TEXT         NOT NULL,
    notes                   VARCHAR(1000),

    -- Teslimat tamamlandığında doldurulan alanlar
    delivered_at            TIMESTAMP,
    delivered_by_name       VARCHAR(150),
    received_by_name        VARCHAR(150),
    received_by_note        VARCHAR(500),
    confirmed_at            TIMESTAMP,
    confirmed_by            VARCHAR(100),

    issued_at               TIMESTAMP    NOT NULL,
    issued_by               VARCHAR(100),
    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,

    CONSTRAINT fk_delivery_receipts_transfer
        FOREIGN KEY (stock_transfer_id) REFERENCES stock_transfers (id) ON DELETE CASCADE
);

-- Bir transferin tek makbuzu olur; yeniden basım aynı satırı günceller ve revision'ı
-- artırır. Aksi hâlde aynı sevkiyat için farklı numaralı iki kâğıt dolaşıma girerdi.
CREATE UNIQUE INDEX IF NOT EXISTS uq_delivery_receipts_transfer
    ON delivery_receipts (stock_transfer_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_delivery_receipts_no
    ON delivery_receipts (receipt_no);
CREATE INDEX IF NOT EXISTS idx_delivery_receipts_status
    ON delivery_receipts (status);
CREATE INDEX IF NOT EXISTS idx_delivery_receipts_issued_at
    ON delivery_receipts (issued_at DESC);

-- İmzalanan nüshanın fotoğrafı / taranmış PDF'i. Birden fazla olabilir: makbuz iki
-- sayfaya taştığında ya da hem fotoğraf hem tarama yüklendiğinde.
CREATE TABLE IF NOT EXISTS delivery_receipt_attachments (
    id              BIGSERIAL PRIMARY KEY,
    receipt_id      BIGINT       NOT NULL,
    storage_key     VARCHAR(500) NOT NULL,
    file_name       VARCHAR(255),
    content_type    VARCHAR(100),
    size_bytes      BIGINT,
    uploaded_at     TIMESTAMP    NOT NULL,
    uploaded_by     VARCHAR(100),

    CONSTRAINT fk_receipt_attachments_receipt
        FOREIGN KEY (receipt_id) REFERENCES delivery_receipts (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_receipt_attachments_receipt
    ON delivery_receipt_attachments (receipt_id);

COMMENT ON TABLE delivery_receipts IS
    'Müşteri sevkiyatı teslimat makbuzu. Alanlar basım anında dondurulur.';
COMMENT ON COLUMN delivery_receipts.items_json IS
    'Basım anındaki kalem listesi (sku, ad, adet). Ürün adı sonradan değişse bile makbuz aynı kalır.';
COMMENT ON COLUMN delivery_receipts.revision IS
    'Yeniden basım sayacı. Numara sabit kalır, kaçıncı basım olduğu makbuzun üzerinde görünür.';
