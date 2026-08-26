-- Sepete uygulanan kupon artık kalıcı: eskiden yalnızca tek bir yanıt içinde
-- taşınıyordu, sayfa yenilenince indirim kayboluyordu.
ALTER TABLE carts ADD COLUMN IF NOT EXISTS coupon_code VARCHAR(50);

COMMENT ON COLUMN carts.coupon_code IS
    'Sepete uygulanmış kupon kodu. Sipariş oluşurken yeniden doğrulanır.';

-- Kupon kullanım limitleri artık okunuyor; sayaç için indeksler.
CREATE INDEX IF NOT EXISTS idx_coupon_usages_coupon_customer
    ON coupon_usages(coupon_id, customer_id);
