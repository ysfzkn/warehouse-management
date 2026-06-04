-- Logo e-Fatura / e-Arşiv entegrasyonu için site_settings
INSERT INTO site_settings (setting_key, setting_value, setting_type) VALUES
    -- SOAP endpoint ve auth
    -- Endpoint boş bırakılırsa test_mode'a göre default kullanılır:
    --   test: https://pb-demo.elogo.com.tr/PostboxService.svc
    --   prod: https://pb.elogo.com.tr/PostboxService.svc
    ('logo_efatura_endpoint', '', 'STRING'),
    ('logo_efatura_username', '', 'STRING'),
    ('logo_efatura_password', '', 'STRING'),
    ('logo_efatura_test_mode', 'true', 'BOOLEAN'),
    -- E-Fatura müşterisi alias'ı (Logo panelinden alınır, tüzel müşteriler için)
    ('logo_customer_alias', '', 'STRING'),

    -- Mağaza / gönderici firma bilgileri (UBL-TR zorunlu alanlar)
    ('logo_company_vkn', '', 'STRING'),              -- 10 haneli vergi no (tüzel) veya 11 haneli TC (şahıs)
    ('logo_company_title', '', 'STRING'),            -- Resmi ticari unvan
    ('logo_company_tax_office', '', 'STRING'),       -- Vergi dairesi
    ('logo_company_mersis_no', '', 'STRING'),        -- MERSİS numarası
    ('logo_company_trade_registry', '', 'STRING'),   -- Ticaret sicil no
    ('logo_company_address', '', 'STRING'),
    ('logo_company_city', '', 'STRING'),
    ('logo_company_district', '', 'STRING'),
    ('logo_company_postal_code', '', 'STRING'),
    ('logo_company_country', 'TR', 'STRING'),
    ('logo_company_email', '', 'STRING'),
    ('logo_company_phone', '', 'STRING'),
    ('logo_company_website', '', 'STRING'),
    ('logo_company_bank_name', '', 'STRING'),
    ('logo_company_bank_iban', '', 'STRING'),

    -- Fatura tasarım şablonu (Logo panelinden alınır)
    ('logo_earsiv_design_file', '', 'STRING'),
    ('logo_efatura_design_file', '', 'STRING')
ON CONFLICT (setting_key) DO NOTHING;

-- Invoice tablosuna eksik alanlar (alıcı bilgileri detayı için)
ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS recipient_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS recipient_phone VARCHAR(20),
    ADD COLUMN IF NOT EXISTS recipient_city VARCHAR(100),
    ADD COLUMN IF NOT EXISTS recipient_district VARCHAR(100),
    ADD COLUMN IF NOT EXISTS recipient_postal_code VARCHAR(10),
    ADD COLUMN IF NOT EXISTS ettn VARCHAR(50),           -- e-fatura takip numarası (GUID)
    ADD COLUMN IF NOT EXISTS is_individual BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_invoices_ettn ON invoices(ettn) WHERE ettn IS NOT NULL;
