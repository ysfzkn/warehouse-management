-- Kargo API entegrasyonu için site_settings
INSERT INTO site_settings (setting_key, setting_value, setting_type) VALUES
    ('cargo_api_enabled', 'false', 'BOOLEAN'),
    ('cargo_api_provider', 'MOCK', 'STRING'),
    ('cargo_api_auto_create', 'false', 'BOOLEAN'),

    -- Kargonomi credentials
    ('kargonomi_api_token', '', 'STRING'),
    ('kargonomi_app_key', '', 'STRING'),
    ('kargonomi_api_base_url', 'https://app.kargonomi.com.tr/api/v1', 'STRING'),

    -- Gönderici (mağaza) bilgileri - kargo oluştururken kullanılır
    ('sender_name', '', 'STRING'),
    ('sender_phone', '', 'STRING'),
    ('sender_address', '', 'STRING'),
    ('sender_city', '', 'STRING'),
    ('sender_district', '', 'STRING'),
    ('sender_postal_code', '', 'STRING')
ON CONFLICT (setting_key) DO NOTHING;

-- Order tablosuna kargo provider shipment ID eklenir (iptal/sorgulama için)
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS cargo_provider_shipment_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS cargo_label_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS cargo_last_tracked_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_orders_cargo_tracking_no ON orders(cargo_tracking_no) WHERE cargo_tracking_no IS NOT NULL;
