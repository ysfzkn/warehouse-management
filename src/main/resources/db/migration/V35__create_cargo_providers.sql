-- Cargo Providers table for admin-managed shipping companies
CREATE TABLE IF NOT EXISTS cargo_providers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(30) NOT NULL UNIQUE,
    logo_url VARCHAR(500),
    base_cost NUMERIC(10, 2) NOT NULL DEFAULT 29.99,
    cost_per_desi NUMERIC(10, 2) DEFAULT 0.00,
    free_shipping_threshold NUMERIC(10, 2) DEFAULT 500.00,
    estimated_delivery_days INTEGER DEFAULT 3,
    vat_rate NUMERIC(5, 2) DEFAULT 20.00,
    tracking_url_template VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT true,
    sort_order INTEGER DEFAULT 100,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed default Turkish cargo companies
INSERT INTO cargo_providers (name, code, base_cost, cost_per_desi, free_shipping_threshold, estimated_delivery_days, vat_rate, tracking_url_template, sort_order) VALUES
('Yurtici Kargo', 'YURTICI', 34.99, 2.50, 500.00, 2, 20.00, 'https://www.yurticikargo.com/tr/online-servisler/gonderi-sorgula?code={trackingNo}', 1),
('Aras Kargo', 'ARAS', 29.99, 2.00, 500.00, 3, 20.00, 'https://www.araskargo.com.tr/taki.php?kession={trackingNo}', 2),
('MNG Kargo', 'MNG', 32.99, 2.20, 500.00, 3, 20.00, 'https://www.mngkargo.com.tr/gonderi-takip?ref={trackingNo}', 3),
('PTT Kargo', 'PTT', 24.99, 1.50, 500.00, 5, 20.00, 'https://gonderitakip.ptt.gov.tr/Track/Verify?q={trackingNo}', 4),
('Surat Kargo', 'SURAT', 27.99, 1.80, 500.00, 3, 20.00, 'https://www.suratkargo.com.tr/gonderi-takip?takipNo={trackingNo}', 5),
('UPS', 'UPS', 49.99, 4.00, 750.00, 2, 20.00, 'https://www.ups.com/track?tracknum={trackingNo}', 6);

-- Add cargo_provider columns to orders table
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cargo_provider_id BIGINT REFERENCES cargo_providers(id);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cargo_provider_name VARCHAR(100);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_vat NUMERIC(10, 2) DEFAULT 0.00;
