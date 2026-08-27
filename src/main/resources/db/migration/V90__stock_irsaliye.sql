-- Stok girişinin dayandığı irsaliye.
--
-- Mal girişi bir irsaliye ile geliyor; bugüne kadar bu bilgi ya hiç tutulmuyor ya da stok
-- ekleme notunun içine serbest metin olarak yazılıyordu. Notun içinde tutulunca "hangi
-- irsaliyeyle geldi", "şu tarihteki sevkiyatta neler vardı" soruları aranabilir değil.
-- Numara ve tarih kendi sütunlarına alınıyor.

ALTER TABLE stocks ADD COLUMN IF NOT EXISTS irsaliye_no   VARCHAR(50);
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS irsaliye_date DATE;

-- Numara operatörün yazdığı gibi saklanıyor, karşılaştırma ise noktalama gözetmeyen bir
-- anahtar üzerinden yapılıyor: "ABC 2026-14", "abc202614" ve "ABC-2026-14" aynı irsaliyedir.
-- Araç plakalarındaki plate_key ile aynı yaklaşım; sütunu uygulama her yazmada dolduruyor.
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS irsaliye_key  VARCHAR(50);

UPDATE stocks
   SET irsaliye_key = NULLIF(UPPER(REGEXP_REPLACE(COALESCE(irsaliye_no, ''), '[^A-Za-z0-9]', '', 'g')), '')
 WHERE irsaliye_no IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_stocks_irsaliye_key ON stocks(irsaliye_key);
CREATE INDEX IF NOT EXISTS idx_stocks_irsaliye_date ON stocks(irsaliye_date);

COMMENT ON COLUMN stocks.irsaliye_no IS
    'Bu stoğun girdiği irsaliyenin numarası, operatörün yazdığı biçimde.';
COMMENT ON COLUMN stocks.irsaliye_key IS
    'irsaliye_no''nun noktalamasız/büyük harfli hâli; arama bunun üzerinden yapılır. Elle doldurulmaz.';
COMMENT ON COLUMN stocks.irsaliye_date IS
    'İrsaliyenin üzerindeki tarih. Kaydın oluşturulma zamanı değil — mal girişi sonradan da işlenebilir.';
