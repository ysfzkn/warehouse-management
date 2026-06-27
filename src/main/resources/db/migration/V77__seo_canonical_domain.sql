-- Set the canonical domain so all <link rel="canonical">, og:url and
-- Schema.org `url` fields point to the real production domain instead of
-- falling back to whatever origin the crawler happens to hit (e.g. an IP,
-- a staging host, or http://). A stable, https canonical is required for
-- Google to consolidate ranking signals onto a single URL.
--
-- Only fills the value when it is still empty, so a value set later from
-- Admin → Site Ayarları → SEO is never overwritten.

UPDATE site_settings
   SET setting_value = 'https://atsdtm.com.tr'
 WHERE setting_key = 'seo_canonical_domain'
   AND (setting_value IS NULL OR setting_value = '');

-- Insert it if the row does not exist at all (defensive — V48 seeds it).
INSERT INTO site_settings (setting_key, setting_value, setting_type)
SELECT 'seo_canonical_domain', 'https://atsdtm.com.tr', 'STRING'
 WHERE NOT EXISTS (
     SELECT 1 FROM site_settings WHERE setting_key = 'seo_canonical_domain'
 );
