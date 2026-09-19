-- Ücretsiz kargo limiti tek bir yerden yönetiliyor: Site Ayarları → Kargo Ücretlendirme.
--
-- Önceden her kargo firmasının kendi limiti vardı (Aras 500, UPS 750) ve bu, ayarlardaki genel
-- limiti eziyordu. İki yerde iki farklı sayı, hangisinin geçerli olduğu ise hiçbir ekranda
-- yazmıyordu. Mağazanın müşteriye verdiği söz tek: şu tutarın üstünde kargo bizden.
--
-- Firma bazlı sütun kaldırılmıyor, yalnızca fiyat kararında okunmuyor. Veriyi silmek, ileride
-- firmaya özel bir kampanya istendiğinde geri dönüşü olmayan bir kayıp olurdu.
UPDATE site_settings
   SET setting_value = '5000', updated_at = CURRENT_TIMESTAMP
 WHERE setting_key = 'free_shipping_threshold';

INSERT INTO site_settings (setting_key, setting_value, setting_type)
VALUES ('free_shipping_threshold', '5000', 'NUMBER')
ON CONFLICT (setting_key) DO NOTHING;
