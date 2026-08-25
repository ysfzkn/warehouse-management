-- Manuel sipariş teslimat yöntemi: kargo ile mi gönderiliyor, kendi aracımızla mı?
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_method VARCHAR(30) NOT NULL DEFAULT 'CARGO';
CREATE INDEX IF NOT EXISTS idx_orders_delivery_method ON orders(delivery_method);

-- Kendi transferimizle giden siparişler için stok transferi ↔ sipariş bağı.
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS order_id BIGINT;
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS order_number VARCHAR(50);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_stock_transfers_order'
          AND table_name = 'stock_transfers'
    ) THEN
        ALTER TABLE stock_transfers
            ADD CONSTRAINT fk_stock_transfers_order
            FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_stock_transfers_order ON stock_transfers(order_id);

COMMENT ON COLUMN orders.delivery_method IS
    'CARGO = kargo firmasıyla gönderim, OWN_TRANSFER = kendi aracımızla sevkiyat (stock_transfers ile ilişkili).';
COMMENT ON COLUMN stock_transfers.order_id IS
    'Bu sevkiyatın karşıladığı sipariş (yalnızca CUSTOMER_DELIVERY tipinde dolar).';
