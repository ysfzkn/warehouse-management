-- Transferler şoför bilgilerini serbest metin olarak taşıyor. Mükerrer şoförleri
-- birleştirebilmek için hangi transferin hangi rehber kaydına ait olduğunu bilmek gerekiyor;
-- metni değiştirmek geçmişi bozar, bu yüzden ayrı bir bağ ekleniyor.
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS driver_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_stock_transfers_driver'
          AND table_name = 'stock_transfers'
    ) THEN
        ALTER TABLE stock_transfers
            ADD CONSTRAINT fk_stock_transfers_driver
            FOREIGN KEY (driver_id) REFERENCES drivers(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_stock_transfers_driver ON stock_transfers(driver_id);

-- Mevcut transferleri telefon üzerinden rehberdeki şoförle eşle (rehber de bu telefondan
-- üretilmişti, dolayısıyla eşleşme birebir).
UPDATE stock_transfers st
   SET driver_id = d.id
  FROM drivers d
 WHERE st.driver_id IS NULL
   AND st.driver_phone IS NOT NULL
   AND st.driver_phone <> ''
   AND d.phone = st.driver_phone;

COMMENT ON COLUMN stock_transfers.driver_id IS
    'Rehberdeki şoför kaydı. Mükerrer şoförler birleştirilince bu bağ hedefe taşınır; '
    'transferin kendi şoför metni değişmez.';
