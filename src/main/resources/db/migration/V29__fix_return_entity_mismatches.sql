-- =================================================================
-- V29: Fix return_requests and return_request_items entity-DB mismatches
-- =================================================================

-- return_requests: add return_number
ALTER TABLE return_requests ADD COLUMN IF NOT EXISTS return_number VARCHAR(20);
UPDATE return_requests SET return_number = 'RET-' || LPAD(id::text, 6, '0') WHERE return_number IS NULL;
ALTER TABLE return_requests ALTER COLUMN return_number SET NOT NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'uq_return_requests_number') THEN
        CREATE UNIQUE INDEX uq_return_requests_number ON return_requests (return_number);
    END IF;
END $$;

-- return_requests: add customer_note
ALTER TABLE return_requests ADD COLUMN IF NOT EXISTS customer_note VARCHAR(1000);
UPDATE return_requests SET customer_note = description WHERE customer_note IS NULL AND description IS NOT NULL;

-- return_request_items: add product_id FK
ALTER TABLE return_request_items ADD COLUMN IF NOT EXISTS product_id BIGINT;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_return_items_product') THEN
        ALTER TABLE return_request_items ADD CONSTRAINT fk_return_items_product
            FOREIGN KEY (product_id) REFERENCES products(id);
    END IF;
END $$;
