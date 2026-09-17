-- Kargo firması seçim kuralları.
--
-- Otomatik "en ucuz" seçimi bazen en pahalı iadeyi getirir: firma o ilçeye gitmiyordur,
-- ya da o desinin üstünü kabul etmiyordur. Bu iki alan, checkout'ta gösterilen ve
-- gönderide kullanılan firmayı sınırlar.

ALTER TABLE cargo_providers
    ADD COLUMN IF NOT EXISTS max_desi NUMERIC(10,2),
    ADD COLUMN IF NOT EXISTS excluded_districts VARCHAR(2000);

COMMENT ON COLUMN cargo_providers.max_desi IS
    'Bu firmanin kabul ettigi azami desi. Bos = sinir yok.';
COMMENT ON COLUMN cargo_providers.excluded_districts IS
    'Bu firmanin gitmedigi il/ilce listesi, virgulle ayrilmis. Ornek: Hakkari, Sirnak/Cizre';
