-- Fix product slugs that contain URL-unsafe characters.
--
-- Product names with characters like a double-quote (e.g. a TV named `82" QLED`)
-- produced slugs containing `"`, which break the URL path (the link collapsed to
-- `/urun/32?..."`). Slugs are now always sanitized on save (see ProductServiceImpl.slugify),
-- but existing rows must be cleaned up here.
--
-- Transliterate Turkish chars + drop the stray quotes, then replace any remaining
-- non [a-z0-9-] run with a single dash and trim leading/trailing dashes. The historical
-- V15 slugs already end with `-<id>`, so uniqueness is preserved.

UPDATE products
SET slug = trim(BOTH '-' FROM
            regexp_replace(
              translate(lower(slug), 'çğıöşü"''`', 'cgiosu'),
              '[^a-z0-9-]+', '-', 'g'))
WHERE slug ~ '[^a-z0-9-]';

-- Guard against any slug that became empty after cleanup.
UPDATE products SET slug = 'urun-' || id WHERE slug IS NULL OR slug = '' OR slug = '-';
