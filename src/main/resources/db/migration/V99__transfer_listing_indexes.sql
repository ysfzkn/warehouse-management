-- Sevkiyat listesinin sıralama ve filtre sütunlarına indeks.
--
-- Liste her zaman "ORDER BY transfer_date DESC" ile geliyor ama bu sütunda indeks yoktu:
-- PostgreSQL her sayfa isteğinde tablonun tamamını okuyup sıralamak zorunda kalıyordu.
-- Otuz bin satırda bu tek başına yüzlerce milisaniye, veri büyüdükçe daha da fazla.
-- Aynı şekilde durum sekmeleri (status), depo filtreleri ve "benim sevkiyatlarım"
-- ekranının createdBy filtresi de indekssizdi.
--
-- DESC yazılması şart değil (PostgreSQL indeksi iki yönde de tarayabilir) ama sıralamanın
-- yönünü belgeye geçiriyor ve NULL'ların yeri sorgunun beklediği yerde kalıyor.
--
-- CONCURRENTLY kullanılmadı: Flyway her migration'ı bir işlem içinde çalıştırıyor ve
-- CONCURRENTLY işlem içinde çalışmıyor. Bu tablolar için indeks kurma süresi kısa;
-- milyonlarca satıra çıkıldığında indeksleri elle CONCURRENTLY kurmak gerekir.

CREATE INDEX IF NOT EXISTS idx_stock_transfers_transfer_date
    ON stock_transfers (transfer_date DESC);

CREATE INDEX IF NOT EXISTS idx_stock_transfers_status
    ON stock_transfers (status);

CREATE INDEX IF NOT EXISTS idx_stock_transfers_source_warehouse
    ON stock_transfers (source_warehouse_id);

CREATE INDEX IF NOT EXISTS idx_stock_transfers_destination_warehouse
    ON stock_transfers (destination_warehouse_id);

CREATE INDEX IF NOT EXISTS idx_stock_transfers_created_by
    ON stock_transfers (created_by);

-- Onay kuyruğu ekranı yalnızca PENDING satırları okuyor ve bunlar toplamın çok küçük bir
-- kısmı; kısmi indeks hem küçük kalıyor hem de yazma maliyetini yalnızca o satırlara
-- bindiriyor.
CREATE INDEX IF NOT EXISTS idx_stock_transfers_approval_pending
    ON stock_transfers (approval_status)
    WHERE approval_status = 'PENDING';
