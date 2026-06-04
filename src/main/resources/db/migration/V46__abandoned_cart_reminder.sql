-- Terk edilmiş sepet kurtarma için yeni kolon
ALTER TABLE carts
    ADD COLUMN abandoned_cart_reminder_sent_at TIMESTAMP;

CREATE INDEX idx_carts_abandoned_reminder
    ON carts (abandoned_cart_reminder_sent_at, updated_at);

-- Admin ayarları (site_settings tablosuna)
INSERT INTO site_settings (setting_key, setting_value, setting_type) VALUES
    ('abandoned_cart_enabled', 'true', 'BOOLEAN'),
    ('abandoned_cart_delay_hours', '2', 'NUMBER'),
    ('abandoned_cart_reminder_window_hours', '72', 'NUMBER')
ON CONFLICT (setting_key) DO NOTHING;
