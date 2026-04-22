-- Kargonomi ileri seviye entegrasyon ayarları
INSERT INTO site_settings (setting_key, setting_value, setting_type) VALUES
    -- Webhook HMAC-SHA256 imza doğrulama için paylaşılan secret.
    -- Kargonomi admin panelinde webhook kayıt sırasında aynı değeri girin.
    ('kargonomi_webhook_secret', '', 'STRING'),

    -- Kargonomi'de kaydedilmiş gönderici depo (warehouse) ID'si.
    -- Boş bırakılırsa her shipment'ta sender bilgileri inline gönderilir (site_settings.sender_*).
    ('kargonomi_warehouse_id', '', 'STRING')
ON CONFLICT (setting_key) DO NOTHING;
