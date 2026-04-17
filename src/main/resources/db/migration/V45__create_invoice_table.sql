-- E-Fatura / E-Arşiv fatura tablosu
CREATE TABLE invoices (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL REFERENCES orders(id),
    invoice_number  VARCHAR(50),
    invoice_type    VARCHAR(20) NOT NULL DEFAULT 'E_ARSIV',
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    -- Fatura alıcı bilgileri (sipariş anından snapshot)
    recipient_name      VARCHAR(255),
    recipient_tax_id    VARCHAR(20),
    recipient_tax_office VARCHAR(100),
    recipient_address   TEXT,

    -- Tutar bilgileri
    subtotal        NUMERIC(12,2) NOT NULL DEFAULT 0,
    vat_amount      NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_amount    NUMERIC(12,2) NOT NULL DEFAULT 0,

    -- Sağlayıcı (provider) bilgileri
    provider_name   VARCHAR(50),
    provider_invoice_id VARCHAR(100),
    gib_response    TEXT,
    error_message   TEXT,

    -- Dosya bilgileri
    pdf_url         VARCHAR(500),
    xml_content     TEXT,

    -- Notlar
    note            VARCHAR(500),

    -- Zaman damgaları
    issued_at       TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invoices_order_id ON invoices(order_id);
CREATE INDEX idx_invoices_invoice_number ON invoices(invoice_number);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_created_at ON invoices(created_at);

-- E-fatura sağlayıcı ayarları (site_settings tablosuna)
INSERT INTO site_settings (setting_key, setting_value, setting_type) VALUES
    ('invoice_provider', 'MOCK', 'STRING'),
    ('invoice_auto_generate', 'true', 'BOOLEAN'),
    ('invoice_company_name', '', 'STRING'),
    ('invoice_company_tax_id', '', 'STRING'),
    ('invoice_company_tax_office', '', 'STRING'),
    ('invoice_company_address', '', 'STRING')
ON CONFLICT (setting_key) DO NOTHING;
