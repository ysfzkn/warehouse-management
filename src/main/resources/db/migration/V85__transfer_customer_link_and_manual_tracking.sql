-- Müşteri sevkiyatlarındaki alıcı, e-ticaret tarafında kayıtlı bir müşteri olabilir de
-- olmayabilir de. Kayıtlıysa (şimdi veya sonradan) buradan eşleştirilir.
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS customer_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_stock_transfers_customer'
          AND table_name = 'stock_transfers'
    ) THEN
        ALTER TABLE stock_transfers
            ADD CONSTRAINT fk_stock_transfers_customer
            FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_stock_transfers_customer ON stock_transfers(customer_id);

COMMENT ON COLUMN stock_transfers.customer_id IS
    'Sevkiyat alıcısının e-ticaret müşteri kaydı. Boşsa alıcı yalnızca serbest metin olarak tutulur.';
