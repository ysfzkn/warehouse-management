-- İleri tarihli (planlı) depo çıkışı.
--
-- Bugüne kadar depo çıkış makbuzu yalnızca "mal şu anda çıkıyor" hâlini kurabiliyordu:
-- kâğıt basılıyor, stok aynı anda düşüyordu. Sahadaki akış çoğu zaman böyle değil —
-- müşteriye makbuz bugün veriliyor, mal önümüzdeki salı teslim ediliyor. O aralıkta mal
-- fiziken depoda duruyor; stoktan düşmüş göstermek sayımı bozar, hiç dokunmamak da aynı
-- malın bir başkasına satılmasına izin verir.
--
-- Çözüm mevcut rezervasyon makinesini kullanmak: planlı çıkışta mal rezerve edilir
-- (stock_transfers.status = IN_TRANSIT, stocks.reserved_quantity artar), teslimat
-- onaylandığında completeTransfer rezerveyi kapatıp stoktan düşer. Yani stokun çıktığı
-- tek bir kod yolu olmaya devam ediyor; planlı çıkış onu geciktirmekten ibaret.

ALTER TABLE stock_transfers
    ADD COLUMN IF NOT EXISTS scheduled_delivery_at TIMESTAMP;

-- Hatırlatma aşamaları tek tek damgalanıyor, tek bir "hatırlatıldı" bayrağıyla değil:
-- iş, "1 gün önce" ve "teslim günü" olmak üzere iki ayrı bildirim göndermek ve ikisinin
-- de tam bir kez gitmesi gerekiyor. Job her gün tüm açık planları tarıyor; damga yoksa
-- gönderir, varsa geçer. Böylece job iki kez koşsa da (dağıtımda iki instance, elle
-- tetikleme, saat değişimi) aynı mail ikinci kez gitmez.
--
-- Zamanı geçmiş bir aşama da damgalanır ama postalanmaz: teslim günü "yarın teslim
-- edilecek" maili göndermek hatırlatma değil, gürültüdür.
ALTER TABLE stock_transfers
    ADD COLUMN IF NOT EXISTS reminder_day_before_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS reminder_due_day_at    TIMESTAMP,
    ADD COLUMN IF NOT EXISTS reminder_overdue_at    TIMESTAMP;

-- Job'ın sorgusu: planı olan ve hâlâ açık olan sevkiyatlar. Tamamlanmış ve iptal
-- edilmişler çoğunluğu oluşturduğu için kısmi indeks — tablo büyüdükçe tarama sabit kalır.
CREATE INDEX IF NOT EXISTS idx_stock_transfers_scheduled_open
    ON stock_transfers (scheduled_delivery_at)
    WHERE scheduled_delivery_at IS NOT NULL AND status IN ('PENDING', 'IN_TRANSIT');

COMMENT ON COLUMN stock_transfers.scheduled_delivery_at IS
    'Planlanan teslim tarihi. NULL = mal çıkışta hemen teslim edildi (klasik akış).';
COMMENT ON COLUMN stock_transfers.reminder_day_before_at IS
    'Teslimden bir gün önceki hatırlatmanın işlendiği an. Zamanı geçtiyse postalanmadan damgalanır.';

-- Makbuzun üzerine basılan plan tarihi. Sevkiyattaki alandan ayrı: makbuz imzalandığı
-- andaki hâli donduruyor. Teslim ertelenirse kâğıttaki tarih ile sistemdeki tarih
-- ayrışır ve ayrışmalı — müşterinin elindeki nüshada hangi tarihin yazdığı, yeniden
-- basılana kadar değişmiyor.
ALTER TABLE delivery_receipts
    ADD COLUMN IF NOT EXISTS scheduled_delivery_at TIMESTAMP;

COMMENT ON COLUMN delivery_receipts.scheduled_delivery_at IS
    'Makbuz basıldığı andaki planlanan teslim tarihi. NULL = anında teslim edilen çıkış.';

-- Hatırlatmaların gideceği adres ve ana şalter.
--
-- VARSAYILAN AÇIK: özellik ancak planlı bir çıkış oluşturulduğunda devreye giriyor, yani
-- kapalıyken hiçbir şey göndermiyor zaten. Alıcı adresi boş bırakılırsa fatura özeti ve
-- iletişim formu adreslerine düşülüyor (bkz. ScheduledDeliveryReminderService), böylece
-- ayar hiç açılmamış bir kurulumda bile hatırlatma sessizce kaybolmuyor.
INSERT INTO site_settings (setting_key, setting_value, setting_type) VALUES
    ('delivery_reminder_enabled', 'true', 'BOOLEAN'),
    ('delivery_reminder_email', '', 'STRING')
ON CONFLICT (setting_key) DO NOTHING;
