-- cargo_providers tablosuna Kargonomi slug eşlemesi.
-- Müşteri checkout'ta bir kargo firması seçtiğinde (Order.cargoCompany = "YURTICI"),
-- KargonomiCargoProvider bu slug'ı /shipment-price-comparison'dan gelen provider
-- listesinde arar ve spesifik olarak o carrier ile gönderim yapar.
-- Slug boşsa, Kargonomi otomatik (en ucuz) carrier seçer.

ALTER TABLE cargo_providers
    ADD COLUMN IF NOT EXISTS kargonomi_slug VARCHAR(40);

-- Standart carrier'lar için default mapping — Kargonomi'nin resmi slug'ları.
UPDATE cargo_providers SET kargonomi_slug = 'yurtici' WHERE UPPER(code) = 'YURTICI' AND kargonomi_slug IS NULL;
UPDATE cargo_providers SET kargonomi_slug = 'aras'    WHERE UPPER(code) = 'ARAS'    AND kargonomi_slug IS NULL;
UPDATE cargo_providers SET kargonomi_slug = 'mng'     WHERE UPPER(code) = 'MNG'     AND kargonomi_slug IS NULL;
UPDATE cargo_providers SET kargonomi_slug = 'ptt'     WHERE UPPER(code) = 'PTT'     AND kargonomi_slug IS NULL;
UPDATE cargo_providers SET kargonomi_slug = 'surat'   WHERE UPPER(code) = 'SURAT'   AND kargonomi_slug IS NULL;
UPDATE cargo_providers SET kargonomi_slug = 'ups'     WHERE UPPER(code) = 'UPS'     AND kargonomi_slug IS NULL;
UPDATE cargo_providers SET kargonomi_slug = 'sendeo'  WHERE UPPER(code) = 'SENDEO'  AND kargonomi_slug IS NULL;
UPDATE cargo_providers SET kargonomi_slug = 'bolt'    WHERE UPPER(code) = 'BOLT'    AND kargonomi_slug IS NULL;
