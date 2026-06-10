-- Product set "triple image set" — dedicated image slots (1,2,3) separate from the
-- normal drag-and-drop gallery. NULL = normal gallery image (existing behavior);
-- 1/2/3 = a fixed slot shown as a 3-column block on the storefront.
ALTER TABLE product_images ADD COLUMN slot SMALLINT NULL;
