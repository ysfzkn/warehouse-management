-- Dalga 3: çoklu depo eşlemesi ve sevk SLA'sı.

-- Her deponun Kargonomi tarafındaki karşılığı. Boşsa global
-- site_settings.kargonomi_warehouse_id kullanılır (tek depolu kurulumların davranışı değişmez).
ALTER TABLE warehouses
    ADD COLUMN IF NOT EXISTS kargonomi_warehouse_id VARCHAR(40);

COMMENT ON COLUMN warehouses.kargonomi_warehouse_id IS
    'Kargonomi POST /warehouses ile olusturulan deponun id si. Bos = global ayar kullanilir.';

INSERT INTO site_settings (setting_key, setting_value, setting_type) VALUES
    -- "İşleme hazır" durumunda bu kadar saat bekleyen gönderi için uyarı üretilir.
    ('cargo_dispatch_sla_hours', '24', 'STRING')
ON CONFLICT (setting_key) DO NOTHING;
