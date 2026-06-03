-- Global satın alma açma/kapama anahtarı.
--
-- Test ortamında "vitrine bakılır ama alınamaz" modu için: false yapıldığında
-- checkout ve ödeme başlatma endpoint'leri reddeder, frontend "Sepete Ekle"
-- butonlarını kapatır ve test banner'ı gösterir.
--
-- Production'da:
--   - Test fazı:   false (kimse alamaz)
--   - Closed beta: false (sadece davetli — uygulama seviyesinde başka kontrol)
--   - Public:      true
--
-- storeEnabled (assistant_store_enabled) farklı bir flag'tir — o asistan
-- widget'ını kontrol eder. Bu flag spesifik olarak satın almayı kontrol eder.

INSERT INTO site_settings (setting_key, setting_value, setting_type) VALUES
    ('store_purchasing_enabled', 'true', 'BOOLEAN'),
    -- Test modunda müşteriye gösterilecek banner metni (boşsa default kullanılır)
    ('store_test_mode_banner',
     '🧪 Bu site şu anda test aşamasındadır. Gerçek satış yapılmamaktadır.',
     'STRING')
ON CONFLICT (setting_key) DO NOTHING;
