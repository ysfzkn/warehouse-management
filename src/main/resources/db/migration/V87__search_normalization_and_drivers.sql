-- Türkçe duyarsız arama + şoför rehberi.
--
-- Sorun: arama "LOWER(sütun) LIKE '%ballı%'" ile yapılıyordu. LOWER büyük/küçük harfi
-- çözüyor ama "ı/i", "ş/s", "ğ/g" farkını çözmüyor; kayıtta "Fehmi Balli" yazınca
-- "Ballı" araması hiçbir şey bulamıyordu. Çözüm: yazarken doldurulan, Türkçe harfleri
-- ASCII karşılığına katlanmış bir arama sütunu.

-- Aynı katlamayı yapan tek seferlik geri doldurma ifadesi (uygulama tarafında
-- TurkishText.normalize ile birebir aynı sonucu üretir).
CREATE OR REPLACE FUNCTION wm_normalize_search(input TEXT) RETURNS TEXT AS $$
    SELECT NULLIF(
        BTRIM(
            REGEXP_REPLACE(
                LOWER(TRANSLATE(COALESCE(input, ''),
                    'ıİIŞşĞğÜüÖöÇçÂâÎîÛû',
                    'iiissgguuooccaaiiuu')),
                '[^a-z0-9]+', ' ', 'g')
        ), '');
$$ LANGUAGE SQL IMMUTABLE;

-- ─── Arama sütunları ────────────────────────────────────────────────────────
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS customer_search VARCHAR(400);
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS driver_search VARCHAR(400);
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS customer_search VARCHAR(400);

UPDATE stock_transfers
   SET customer_search = wm_normalize_search(
           COALESCE(customer_full_name, '') || ' ' || COALESCE(customer_phone, '') || ' '
           || REGEXP_REPLACE(COALESCE(customer_phone, ''), '\D', '', 'g')),
       driver_search = wm_normalize_search(
           COALESCE(driver_name, '') || ' ' || COALESCE(driver_phone, '') || ' '
           || COALESCE(driver_tc_id, '') || ' ' || COALESCE(vehicle_plate, '') || ' '
           || REGEXP_REPLACE(COALESCE(driver_phone, ''), '\D', '', 'g'));

UPDATE stocks
   SET customer_search = wm_normalize_search(
           COALESCE(customer_name, '') || ' ' || COALESCE(customer_phone, '') || ' '
           || REGEXP_REPLACE(COALESCE(customer_phone, ''), '\D', '', 'g'));

CREATE INDEX IF NOT EXISTS idx_stock_transfers_customer_search ON stock_transfers(customer_search);
CREATE INDEX IF NOT EXISTS idx_stock_transfers_driver_search ON stock_transfers(driver_search);
CREATE INDEX IF NOT EXISTS idx_stocks_customer_search ON stocks(customer_search);

COMMENT ON COLUMN stock_transfers.customer_search IS
    'Müşteri adı + telefonun Türkçe harflerden arındırılmış hali; arama bunun üzerinden yapılır.';

-- ─── Şoför rehberi ──────────────────────────────────────────────────────────
-- Şoför bilgileri şimdiye kadar her transferde elden yeniden yazılıyordu.
CREATE TABLE IF NOT EXISTS drivers (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    tc_id           VARCHAR(11),
    phone           VARCHAR(20),
    vehicle_plate   VARCHAR(20),
    notes           VARCHAR(500),
    search_text     VARCHAR(400),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    transfer_count  INTEGER NOT NULL DEFAULT 0,
    last_used_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_drivers_search ON drivers(search_text);
CREATE INDEX IF NOT EXISTS idx_drivers_active ON drivers(active);
-- Telefon kimliği belirler: aynı şoför iki kez eklenmesin.
CREATE UNIQUE INDEX IF NOT EXISTS idx_drivers_phone_unique
    ON drivers(phone) WHERE phone IS NOT NULL AND phone <> '';

-- Mevcut transferlerden şoförleri devral: telefon başına en son kullanılan kayıt kazanır.
INSERT INTO drivers (name, tc_id, phone, vehicle_plate, search_text, active,
                     transfer_count, last_used_at, created_at, updated_at)
SELECT latest.driver_name,
       latest.driver_tc_id,
       latest.driver_phone,
       latest.vehicle_plate,
       wm_normalize_search(latest.driver_name || ' ' || COALESCE(latest.driver_phone, '') || ' '
                           || COALESCE(latest.driver_tc_id, '') || ' ' || COALESCE(latest.vehicle_plate, '') || ' '
                           || REGEXP_REPLACE(COALESCE(latest.driver_phone, ''), '\D', '', 'g')),
       TRUE,
       latest.usage_count,
       latest.last_used_at,
       NOW(),
       NOW()
  FROM (
        SELECT DISTINCT ON (driver_phone)
               driver_phone,
               driver_name,
               driver_tc_id,
               vehicle_plate,
               COUNT(*) OVER (PARTITION BY driver_phone)::INT AS usage_count,
               MAX(transfer_date) OVER (PARTITION BY driver_phone) AS last_used_at
          FROM stock_transfers
         WHERE driver_phone IS NOT NULL AND driver_phone <> ''
           AND driver_name IS NOT NULL AND driver_name <> ''
         ORDER BY driver_phone, transfer_date DESC
       ) AS latest
ON CONFLICT DO NOTHING;

COMMENT ON TABLE drivers IS
    'Transferlerde kullanılan şoförler. Her transferde otomatik güncellenir, ekrandan da yönetilebilir.';
