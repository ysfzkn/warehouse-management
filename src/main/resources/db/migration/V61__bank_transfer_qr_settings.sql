-- Havale/EFT için QR kod (FAST) desteği. Müşteri checkout'ta IBAN yazmak
-- yerine bankacılık app'ına QR'ı okutarak hızlı transfer yapabilir.
--
-- Static QR: admin bankasının panelinden FAST QR'ını alıp asset olarak yükler;
-- her sipariş aynı QR'ı görür. Müşteri uygulamada tutarı + açıklamayı (referans)
-- manuel girer (banka FAST QR formatı standardize değilse).
--
-- Future: dynamic per-order QR generation (EMV TR FAST format) için
-- bank_transfer_qr_dynamic_enabled toggle kullanılır.

INSERT INTO site_settings (setting_key, setting_value, setting_type) VALUES
    -- QR kodu kullanıcıya görünsün mü?
    ('bank_transfer_qr_enabled', 'false', 'BOOLEAN'),
    -- QR image storage key (S3/local) — AdminSiteSettings → upload üzerinden set edilir
    ('bank_transfer_qr_image', '', 'STRING'),
    -- Banka adı (QR ile uyumlu — IBAN ile aynı banka olmalı)
    ('bank_transfer_qr_bank_name', '', 'STRING'),
    -- Müşteriye gösterilecek açıklama metni
    ('bank_transfer_qr_description',
     'Bankanızın mobil uygulamasında "QR ile İşlem" / "Karekod ile Para Transferi" menüsünden okutun. Tutar ve açıklama (referans) alanlarını lütfen aşağıdaki bilgiler ile doldurun.',
     'STRING'),
    -- Gelecek için: dinamik per-order QR üretimi (FAST EMV format)
    ('bank_transfer_qr_dynamic_enabled', 'false', 'BOOLEAN')
ON CONFLICT (setting_key) DO NOTHING;
