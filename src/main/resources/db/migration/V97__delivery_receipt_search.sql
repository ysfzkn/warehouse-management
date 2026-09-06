-- Makbuz araması Türkçe harflere takılmasın.
--
-- Arama şimdiye kadar sütunları LIKE ile tarıyordu ve büyük harfle yazılan Türkçe isimler
-- hiç bulunamıyordu. Sebebi küçültmenin geri döndürülemez olması: "I" hem "ı" hem "i"nin
-- büyüğü, "İ" küçültülünce birleşik noktalı bir karaktere dönüşüyor. "IŞIK" araması
-- kayıttaki "Işık"ı, "ŞAHİN" araması "Şahin"i bulamıyordu.
--
-- Çözüm stock_transfers.customer_search ile aynı: hem kaydı hem aramayı ASCII'ye katlanmış
-- tek bir sütun üzerinden karşılaştırmak. Böylece "AYSE", "ayşe" ve "Ayşe" aynı satıra
-- düşüyor. Katlamayı yapan wm_normalize_search V87'de tanımlı ve uygulama tarafındaki
-- TurkishText.normalize ile birebir aynı sonucu üretiyor.

ALTER TABLE delivery_receipts ADD COLUMN IF NOT EXISTS search_text VARCHAR(500);

-- Bir parçanın yalnızca rakamları, yedi haneden azsa boş. TurkishText.normalizeForSearch
-- içindeki kuralın aynısı; sadece bu geri doldurma için var, sonunda kaldırılıyor.
CREATE OR REPLACE FUNCTION wm_search_digits(input TEXT) RETURNS TEXT AS $$
    SELECT CASE
        WHEN LENGTH(REGEXP_REPLACE(COALESCE(input, ''), '\D', '', 'g')) >= 7
        THEN REGEXP_REPLACE(COALESCE(input, ''), '\D', '', 'g')
        ELSE ''
    END;
$$ LANGUAGE SQL IMMUTABLE;

-- Mevcut makbuzların geri doldurulması. Uygulama bu sütunu her yazışta kendisi tazeliyor
-- (DeliveryReceipt @PrePersist/@PreUpdate), ama eski satırlar bir kez burada doldurulmalı;
-- yoksa hiç düzenlenmemiş bir makbuz aramada görünmez olurdu.
UPDATE delivery_receipts
   SET search_text = wm_normalize_search(
           COALESCE(receipt_no, '')          || ' ' ||
           COALESCE(customer_full_name, '')  || ' ' ||
           COALESCE(customer_phone, '')      || ' ' ||
           COALESCE(order_number, '')        || ' ' ||
           COALESCE(driver_name, '')         || ' ' ||
           COALESCE(vehicle_plate, '')       || ' ' ||
           COALESCE(handover_to_name, '')    || ' ' ||
           COALESCE(handover_to_phone, '')   || ' ' ||
           COALESCE(received_by_name, '')    || ' ' ||
           -- Rakamların bitişik hâli de giriyor: "0553 999 33 03" normalleştirilince
           -- gruplara ayrılıyor ve bitişik yazan biri bulamıyordu.
           --
           -- Yedi haneli eşiği uydurma değil: TurkishText.normalizeForSearch tam olarak bunu
           -- yapıyor ve iki taraf birebir aynı dizeyi üretmek zorunda. Aksi hâlde burada geri
           -- doldurulan eski bir makbuz ile uygulamanın sonradan yazdığı yeni bir makbuz
           -- farklı içerikte olurdu ve aynı arama birini bulup diğerini bulamazdı.
           wm_search_digits(receipt_no)       || ' ' ||
           wm_search_digits(customer_phone)   || ' ' ||
           wm_search_digits(order_number)     || ' ' ||
           wm_search_digits(handover_to_phone));

DROP FUNCTION IF EXISTS wm_search_digits(TEXT);

CREATE INDEX IF NOT EXISTS idx_delivery_receipts_search
    ON delivery_receipts (search_text);

COMMENT ON COLUMN delivery_receipts.search_text IS
    'Makbuz no, müşteri, sipariş, şoför, plaka ve teslim alan bilgilerinin Türkçe harflerden arındırılmış hâli; ekrandaki arama bunun üzerinden yapılır.';
