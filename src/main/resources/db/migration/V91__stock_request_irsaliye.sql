-- İrsaliye, onay bekleyen stok taleplerinde de taşınmalı.
--
-- V90 irsaliyeyi stok satırına ekledi, ama depo rolündeki kullanıcı stoğu doğrudan
-- eklemiyor: talep açıyor, yönetici onaylıyor. Talep kaydında irsaliye alanı olmayınca
-- bilgi ya notun içine serbest metin olarak sıkışıyor ya da tamamen kayboluyordu — yani
-- aranabilir irsaliye yalnızca yöneticinin girdiği stoklarda oluyordu.
--
-- Talep onaylandığında bu alanlar stok satırına geçer (StockRequestServiceImpl.approveRequest).

ALTER TABLE stock_requests ADD COLUMN IF NOT EXISTS irsaliye_no   VARCHAR(50);
ALTER TABLE stock_requests ADD COLUMN IF NOT EXISTS irsaliye_date DATE;

COMMENT ON COLUMN stock_requests.irsaliye_no IS
    'Talebin dayandığı irsaliye numarası; onayda stok satırına işlenir.';
COMMENT ON COLUMN stock_requests.irsaliye_date IS
    'İrsaliyenin üzerindeki tarih; onayda stok satırına işlenir.';
