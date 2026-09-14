-- Koli planı.
--
-- Önceden koli sayısı = sipariş kalemlerinin adet toplamıydı: 6 adetlik bir sipariş
-- Kargonomi'ye 6 ayrı koli olarak gidiyor, toplam desi de 6'ya bölünüyordu.
-- Kargonomi koli başına fiyatladığı için fatura gereksiz şişiyordu.
--
-- Yeni kural: aksi belirtilmedikçe kalemler tek kolide birleşir. Kendi başına taşınan
-- ürünler (mobilya, beyaz eşya) products.packages_per_unit ile işaretlenir.

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS packages_per_unit INTEGER;

COMMENT ON COLUMN products.packages_per_unit IS
    'Bu üründen 1 adet kaç koli eder? Bos/NULL = diger urunlerle ayni koliye konabilir.';

-- Sipariş bazında elle düzeltme: planlayıcının bulduğu sayı yanlışsa admin buradan ezer.
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS cargo_package_count INTEGER;

-- Birleştirilen kalemlerin tek bir koliye sığmayacağı sınır (desi).
INSERT INTO site_settings (setting_key, setting_value, setting_type) VALUES
    ('cargo_max_desi_per_package', '30', 'STRING')
ON CONFLICT (setting_key) DO NOTHING;
