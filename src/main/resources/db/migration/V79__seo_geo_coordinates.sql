-- Real geo coordinates of the ATS DTM store in Niğde, read from the Google
-- Business Profile / Maps listing (maps.app.goo.gl/t4nFe5hFZVRhH9dp6).
--
-- Populates the LocalBusiness Schema.org `geo` (GeoCoordinates) + `hasMap`
-- so Google can pin the store on the map and strengthen local-pack ranking
-- for "Niğde {brand}" / "Niğde beyaz eşya" queries.
--
-- Only fills the values when still empty, so coordinates fine-tuned later from
-- Admin → Site Ayarları → Yerel SEO are never overwritten.

UPDATE site_settings
   SET setting_value = '37.964528'
 WHERE setting_key = 'seo_geo_lat'
   AND (setting_value IS NULL OR setting_value = '');

UPDATE site_settings
   SET setting_value = '34.6708774'
 WHERE setting_key = 'seo_geo_lng'
   AND (setting_value IS NULL OR setting_value = '');
