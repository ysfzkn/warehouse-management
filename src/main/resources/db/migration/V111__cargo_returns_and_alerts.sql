-- Dalga 2 ayarları ve iade kargosu alanları.

-- İade gönderisi: onaylanan iadede müşteriye kargo kaydı açılır.
-- VARSAYILAN KAPALI: Kargonomi'nin iade gönderisini nasıl işaretlediği (is_return alanı)
-- resmî dokümanda geçmiyor. Kargonomi'den teyit alınmadan açılmamalı.
ALTER TABLE return_requests
    ADD COLUMN IF NOT EXISTS cargo_provider_shipment_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS cargo_provider_name VARCHAR(100);

INSERT INTO site_settings (setting_key, setting_value, setting_type) VALUES
    ('cargo_return_label_enabled', 'false', 'BOOLEAN'),

    -- Bakiye bu değerin altına inince admin'e bildirim gider (TL).
    ('cargo_balance_alert_threshold', '250', 'STRING'),

    -- Checkout'ta Kargonomi'nin gerçek fiyatlarını göster.
    -- VARSAYILAN KAPALI: her fiyat sorgusu Kargonomi'de taslak gönderi açar;
    -- kota/ücret durumu Kargonomi'ye sorulmadan açılmamalı.
    ('cargo_checkout_live_pricing', 'false', 'BOOLEAN'),

    -- Canlı fiyat açıkken aynı (il/ilçe + desi dilimi) için fiyatın saklanma süresi (dakika).
    ('cargo_price_cache_minutes', '720', 'STRING')
ON CONFLICT (setting_key) DO NOTHING;
