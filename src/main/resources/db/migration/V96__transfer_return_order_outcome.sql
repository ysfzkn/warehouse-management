-- Siparişe bağlı sevkiyatların iadesi.
--
-- V95'te siparişe bağlı sevkiyatlar iade dışında bırakılmıştı: sevkiyatın tamamlanması
-- siparişi DELIVERED yapıyor ve sessizce stok eklemek, malı raflarımızda duran "teslim
-- edilmiş" bir sipariş bırakırdı. Artık bırakmıyor — ama karar kullanıcıya ait, çünkü
-- depoda aynı görünen iki durum sipariş defterinde bambaşka:
--
--   KEEP_ORDER   teslimat denemesi tutmadı, sipariş açık, yeniden gönderilecek
--   RETURN_ORDER sipariş iade edildi
--
-- Seçim rezervasyonu da belirliyor. Siparişe ayrılan stok sevkiyat tamamlanırken
-- tüketiliyor; sipariş yaşamaya devam ediyorsa dönen adetlerin yeniden o siparişe
-- ayrılması gerekiyor, yoksa aynı mal bir başkasına satılabilir hâle geliyor.
--
-- NULL: siparişe bağlı olmayan sevkiyatların iadesi. Orada verilecek bir karar yok.
ALTER TABLE transfer_returns
    ADD COLUMN IF NOT EXISTS order_outcome VARCHAR(30);

COMMENT ON COLUMN transfer_returns.order_outcome IS
    'Siparişe bağlı iadelerde siparişin akıbeti: KEEP_ORDER | RETURN_ORDER. Bağlantısız iadelerde NULL.';
