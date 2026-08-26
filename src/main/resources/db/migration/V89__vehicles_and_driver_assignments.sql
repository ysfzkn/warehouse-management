-- Araçlar artık şoför kaydının içine gömülü tek bir plaka alanı değil, kendi kaydı olan
-- varlıklar. Bir şoför farklı günlerde farklı araç kullanabildiği için ilişki çoka-çok.

-- Plakayı karşılaştırmak için boşluk/noktalama atılmış, büyük harfe çevrilmiş biçim:
-- "51 TV 51", "51tv51" ve "51-TV-51" aynı araçtır.
CREATE OR REPLACE FUNCTION wm_plate_key(input TEXT) RETURNS TEXT AS $$
    SELECT NULLIF(UPPER(REGEXP_REPLACE(COALESCE(input, ''), '[^A-Za-z0-9]', '', 'g')), '');
$$ LANGUAGE SQL IMMUTABLE;

CREATE TABLE IF NOT EXISTS vehicles (
    id              BIGSERIAL PRIMARY KEY,
    plate           VARCHAR(20) NOT NULL,
    plate_key       VARCHAR(20) NOT NULL,
    brand_model     VARCHAR(100),
    notes           VARCHAR(500),
    search_text     VARCHAR(400),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    transfer_count  INTEGER NOT NULL DEFAULT 0,
    last_used_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_vehicles_plate_key ON vehicles(plate_key);
CREATE INDEX IF NOT EXISTS idx_vehicles_search ON vehicles(search_text);
CREATE INDEX IF NOT EXISTS idx_vehicles_active ON vehicles(active);

-- Geçmiş transferlerdeki ve şoför kayıtlarındaki plakalardan araçları devral.
INSERT INTO vehicles (plate, plate_key, search_text, active, transfer_count, last_used_at, created_at, updated_at)
SELECT latest.plate,
       latest.plate_key,
       wm_normalize_search(latest.plate),
       TRUE,
       latest.usage_count,
       latest.last_used_at,
       NOW(),
       NOW()
  FROM (
        SELECT DISTINCT ON (wm_plate_key(vehicle_plate))
               UPPER(BTRIM(vehicle_plate))            AS plate,
               wm_plate_key(vehicle_plate)            AS plate_key,
               COUNT(*) OVER (PARTITION BY wm_plate_key(vehicle_plate))::INT AS usage_count,
               MAX(transfer_date) OVER (PARTITION BY wm_plate_key(vehicle_plate)) AS last_used_at
          FROM stock_transfers
         WHERE wm_plate_key(vehicle_plate) IS NOT NULL
         ORDER BY wm_plate_key(vehicle_plate), transfer_date DESC
       ) AS latest
ON CONFLICT (plate_key) DO NOTHING;

INSERT INTO vehicles (plate, plate_key, search_text, active, transfer_count, created_at, updated_at)
SELECT DISTINCT ON (wm_plate_key(d.vehicle_plate))
       UPPER(BTRIM(d.vehicle_plate)),
       wm_plate_key(d.vehicle_plate),
       wm_normalize_search(d.vehicle_plate),
       TRUE, 0, NOW(), NOW()
  FROM drivers d
 WHERE wm_plate_key(d.vehicle_plate) IS NOT NULL
ON CONFLICT (plate_key) DO NOTHING;

-- ─── Şoför ↔ Araç ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS driver_vehicles (
    driver_id    BIGINT NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    vehicle_id   BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    PRIMARY KEY (driver_id, vehicle_id)
);

CREATE INDEX IF NOT EXISTS idx_driver_vehicles_vehicle ON driver_vehicles(vehicle_id);

-- Kim hangi aracı kullanmış: geçmiş transferlerden çıkar.
INSERT INTO driver_vehicles (driver_id, vehicle_id)
SELECT DISTINCT st.driver_id, v.id
  FROM stock_transfers st
  JOIN vehicles v ON v.plate_key = wm_plate_key(st.vehicle_plate)
 WHERE st.driver_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- Şoför kaydındaki plakayı da bir atama say (transferi olmayan eski kayıtlar için).
INSERT INTO driver_vehicles (driver_id, vehicle_id)
SELECT d.id, v.id
  FROM drivers d
  JOIN vehicles v ON v.plate_key = wm_plate_key(d.vehicle_plate)
ON CONFLICT DO NOTHING;

-- ─── Transferin kullandığı araç ─────────────────────────────────────────────
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS vehicle_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_stock_transfers_vehicle'
          AND table_name = 'stock_transfers'
    ) THEN
        ALTER TABLE stock_transfers
            ADD CONSTRAINT fk_stock_transfers_vehicle
            FOREIGN KEY (vehicle_id) REFERENCES vehicles(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_stock_transfers_vehicle ON stock_transfers(vehicle_id);

UPDATE stock_transfers st
   SET vehicle_id = v.id
  FROM vehicles v
 WHERE st.vehicle_id IS NULL
   AND v.plate_key = wm_plate_key(st.vehicle_plate);

COMMENT ON TABLE vehicles IS
    'Transferlerde kullanılan araçlar. Şoförlerle çoka-çok ilişkilidir; bir şoför birden fazla araç kullanabilir.';
COMMENT ON COLUMN vehicles.plate_key IS
    'Plakanın boşluksuz, büyük harfli hali. Tekillik bunun üzerinden kurulur.';
