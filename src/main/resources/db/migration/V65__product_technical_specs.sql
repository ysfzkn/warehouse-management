-- Structured technical specifications for products.
--
-- Stored as JSONB: an array of spec groups, each with a title and a list of
-- label/value rows. Example:
--   [ { "title": "Genel Özellikler",
--       "items": [ { "label": "Enerji Sınıfı", "value": "A++" },
--                  { "label": "Kapasite", "value": "8 kg" } ] },
--     { "title": "Boyutlar",
--       "items": [ { "label": "Genişlik", "value": "60 cm" } ] } ]
--
-- Populated from the admin product form (structured editor) and the web crawler.
-- Rendered in a clean "Teknik Özellikler" section on the storefront product page.

ALTER TABLE products ADD COLUMN IF NOT EXISTS technical_specs JSONB;
