-- Taşıyıcısı sonradan girilmiş makbuzlara şoför ve plakayı işle.
--
-- Depo çıkış makbuzu taşıyıcı belli olmadan basılıyor ve kâğıdın kapanış paragrafı bunu
-- açıkça taahhüt ediyor: "Taşıyıcı araç ve sürücü bilgisi belirlendiğinde bu belgenin
-- kaydına işlenir." Kod bunu yapmıyordu — taşıyıcı atandığında sevkiyat güncelleniyor,
-- makbuz kaydı boş kalıyordu. Sonuç olarak liste ekranı, şoför girilmiş sevkiyatlarda
-- bile kalıcı olarak "basımda yoktu" gösteriyordu.
--
-- Bundan sonrasını uygulama hallediyor (DeliveryReceiptService.noteCarrier); burada
-- yalnızca eski kayıtlar bir kez düzeltiliyor.
--
-- Yalnızca makbuzda şoför BOŞ ve sevkiyatta DOLU olan satırlara dokunuluyor. Makbuzda
-- zaten bir şoför yazıyorsa üzerine yazılmıyor: o değer kâğıdın basıldığı andaki anlık
-- görüntü ve sevkiyattaki taşıyıcı sonradan değiştirilmiş olabilir — hangisinin doğru
-- olduğuna karar vermek bu düzeltmenin işi değil.

UPDATE delivery_receipts r
   SET driver_name   = t.driver_name,
       driver_phone  = t.driver_phone,
       vehicle_plate = t.vehicle_plate
  FROM stock_transfers t
 WHERE r.stock_transfer_id = t.id
   AND r.driver_name IS NULL
   AND t.driver_name IS NOT NULL;

-- Arama sütunu şoför ve plakayı da kapsıyor; dokunduğumuz satırlarda tazelenmesi gerek,
-- yoksa yeni girilen plaka aramada bulunamazdı.
--
-- Formül V97'dekiyle birebir aynı olmak zorunda: uygulama bu sütunu her yazışta
-- TurkishText.normalizeForSearch ile üretiyor ve iki taraf farklı dize üretirse aynı arama
-- bir makbuzu bulup diğerini bulamaz. Yedi haneli eşik de oradan geliyor, uydurma değil.
CREATE OR REPLACE FUNCTION wm_search_digits(input TEXT) RETURNS TEXT AS $$
    SELECT CASE
        WHEN LENGTH(REGEXP_REPLACE(COALESCE(input, ''), '\D', '', 'g')) >= 7
        THEN REGEXP_REPLACE(COALESCE(input, ''), '\D', '', 'g')
        ELSE ''
    END;
$$ LANGUAGE SQL IMMUTABLE;

UPDATE delivery_receipts r
   SET search_text = wm_normalize_search(
           COALESCE(r.receipt_no, '')          || ' ' ||
           COALESCE(r.customer_full_name, '')  || ' ' ||
           COALESCE(r.customer_phone, '')      || ' ' ||
           COALESCE(r.order_number, '')        || ' ' ||
           COALESCE(r.driver_name, '')         || ' ' ||
           COALESCE(r.vehicle_plate, '')       || ' ' ||
           COALESCE(r.handover_to_name, '')    || ' ' ||
           COALESCE(r.handover_to_phone, '')   || ' ' ||
           COALESCE(r.received_by_name, '')    || ' ' ||
           wm_search_digits(r.receipt_no)       || ' ' ||
           wm_search_digits(r.customer_phone)   || ' ' ||
           wm_search_digits(r.order_number)     || ' ' ||
           wm_search_digits(r.handover_to_phone))
  FROM stock_transfers t
 WHERE r.stock_transfer_id = t.id
   AND r.driver_name IS NOT NULL
   AND r.driver_name = t.driver_name;

DROP FUNCTION IF EXISTS wm_search_digits(TEXT);
