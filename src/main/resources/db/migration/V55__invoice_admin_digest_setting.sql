-- Günlük e-Fatura admin özeti için opsiyonel alıcı e-postası.
-- Boşsa contact_form_email fallback olarak kullanılır.
INSERT INTO site_settings (setting_key, setting_value, setting_type) VALUES
    ('invoice_admin_digest_email', '', 'STRING')
ON CONFLICT (setting_key) DO NOTHING;
