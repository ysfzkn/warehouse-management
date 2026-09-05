-- Depo çıkış makbuzu: taşıyıcı belli olmadan mal servise teslim edildiğinde.
--
-- Gerçek akış şöyle işliyor: mal depodan çıkıp bir servise/nakliyeciye veriliyor, ama o
-- malı hangi şoförün hangi araçla götüreceği o anda belli değil. Kâğıt yine de o anda
-- imzalanıyor — mal fiziken depodan çıktığı için.
--
-- Sistemde bu adım kurulamıyordu: stock_transfers şoför adı, TC, telefon ve plakayı
-- NOT NULL istiyordu, yani taşıyıcı bilinmeden hiçbir çıkış kaydı açılamıyordu.
--
-- Çözüm, çıkışı ayrı bir belge tipine bölmek DEĞİL. Mal bir kez çıkıyor; taşıyıcının
-- sonradan yazılması ikinci bir çıkış değil, aynı çıkışın eksik alanının tamamlanması.
-- Bu yüzden kayıt yine bir stock_transfers satırı: stok yeterlilik kontrolü, düşüm,
-- denetim izi (audit'te transfer_id) ve makbuz arşivi olduğu gibi çalışıyor, ve aynı mal
-- için ikinci bir transfer açılıp stokun iki kez düşmesi yapısal olarak imkânsız kalıyor.

-- Taşıyıcı alanları artık boş bırakılabilir. Normal transfer akışında hâlâ zorunlu —
-- zorunluluk DTO doğrulamasında ve StockTransferServiceImpl.validateTransferCreation
-- içinde duruyor, sadece depo çıkış makbuzu bundan muaf.
ALTER TABLE stock_transfers ALTER COLUMN driver_name  DROP NOT NULL;
ALTER TABLE stock_transfers ALTER COLUMN driver_tc_id DROP NOT NULL;
ALTER TABLE stock_transfers ALTER COLUMN driver_phone DROP NOT NULL;
ALTER TABLE stock_transfers ALTER COLUMN vehicle_plate DROP NOT NULL;

-- Taşıyıcısı henüz girilmemiş çıkışlar. Listede rozetle ve filtreyle görünür olması şart:
-- görünmezse bu kayıtlar sessizce birikir ve kimse taşıyıcıyı tamamlamaz.
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS carrier_pending BOOLEAN NOT NULL DEFAULT FALSE;

-- Malı fiziken teslim alan servis/nakliyeci ve teslim eden depo görevlisi. Makbuzun
-- imza bloklarına basılan taraflar bunlar; nihai müşteri ayrı alanlarda duruyor.
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS handover_to_name  VARCHAR(150);
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS handover_to_phone VARCHAR(30);
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS handed_over_by    VARCHAR(150);

CREATE INDEX IF NOT EXISTS idx_stock_transfers_carrier_pending
    ON stock_transfers (carrier_pending) WHERE carrier_pending = TRUE;

COMMENT ON COLUMN stock_transfers.carrier_pending IS
    'Mal servise teslim edildi, taşıyıcı (şoför/plaka) henüz girilmedi. Taşıyıcı girilince FALSE olur.';
COMMENT ON COLUMN stock_transfers.handover_to_name IS
    'Malı depodan fiziken teslim alan servis/nakliyeci. Nihai müşteri customer_full_name alanında.';

-- Makbuz tipi. Aynı tablo iki farklı kâğıt basıyor: müşteriye giden iki nüshalık
-- TESLİMAT MAKBUZU ve servise verilirken imzalanan tek nüshalık DEPO ÇIKIŞ MAKBUZU.
-- Numara serileri de ayrı (TM-* / DC-*), ikisi de id'den türediği için çakışmıyor.
ALTER TABLE delivery_receipts ADD COLUMN IF NOT EXISTS kind VARCHAR(30) NOT NULL DEFAULT 'DELIVERY';

-- Basım anındaki teslim eden / teslim alan tarafları. delivered_by_name ve
-- received_by_name'den ayrı tutuluyor: onlar müşteriye teslimin onaylandığı adıma ait ve
-- sonradan doldurulabiliyor. İkisini aynı sütuna yazmak, teslimat onaylandığında imzalı
-- kâğıtta duran servis görevlisinin adını yeniden basımda müşterininkiyle değiştirirdi.
ALTER TABLE delivery_receipts ADD COLUMN IF NOT EXISTS handover_to_name    VARCHAR(150);
ALTER TABLE delivery_receipts ADD COLUMN IF NOT EXISTS handover_to_phone   VARCHAR(30);
ALTER TABLE delivery_receipts ADD COLUMN IF NOT EXISTS handed_over_by_name VARCHAR(150);

COMMENT ON COLUMN delivery_receipts.kind IS
    'DELIVERY = müşteri teslimat makbuzu (iki nüsha), SERVICE_HANDOVER = depo çıkış makbuzu (tek nüsha).';
