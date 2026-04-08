-- V24: Site settings for white-label configuration
-- Rollback: DROP TABLE site_settings;

CREATE TABLE site_settings (
    id              BIGSERIAL PRIMARY KEY,
    setting_key     VARCHAR(100) NOT NULL UNIQUE,
    setting_value   TEXT NOT NULL,
    setting_type    VARCHAR(20) NOT NULL DEFAULT 'STRING',
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(100)
);

CREATE INDEX idx_site_settings_key ON site_settings (setting_key);

-- Default settings
INSERT INTO site_settings (setting_key, setting_value, setting_type) VALUES
    ('site_name', 'E-Ticaret', 'STRING'),
    ('site_logo_url', '/images/logo.png', 'STRING'),
    ('site_favicon_url', '/favicon.ico', 'STRING'),
    ('primary_color', '#2563eb', 'STRING'),
    ('secondary_color', '#1e40af', 'STRING'),
    ('contact_phone', '+90 (312) 000 00 00', 'STRING'),
    ('contact_email', 'info@domain.com', 'STRING'),
    ('contact_address', 'Ankara, Turkiye', 'STRING'),
    ('social_instagram', '', 'STRING'),
    ('social_whatsapp', '', 'STRING'),
    ('footer_text', '© 2026 Tum haklari saklidir.', 'STRING'),
    ('currency_symbol', 'TL', 'STRING'),
    ('free_shipping_threshold', '500', 'NUMBER'),
    ('default_shipping_cost', '29.99', 'NUMBER');
