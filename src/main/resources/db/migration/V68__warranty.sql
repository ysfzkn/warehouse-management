-- Product & category warranties.
--
-- A product can carry its own warranty (warranty_months + free-text warranty_text,
-- e.g. "24 ay üretici garantisi"). If a product leaves these null, the effective
-- warranty falls back to its category, then the parent category. This lets an admin
-- set one warranty on a category and have it apply to every product underneath it.

ALTER TABLE products   ADD COLUMN IF NOT EXISTS warranty_months INTEGER;
ALTER TABLE products   ADD COLUMN IF NOT EXISTS warranty_text   VARCHAR(500);

ALTER TABLE categories ADD COLUMN IF NOT EXISTS warranty_months INTEGER;
ALTER TABLE categories ADD COLUMN IF NOT EXISTS warranty_text   VARCHAR(500);
