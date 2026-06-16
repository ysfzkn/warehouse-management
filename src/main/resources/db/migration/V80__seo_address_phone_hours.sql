-- Real NAP (Name-Address-Phone) + opening hours for the ATS DTM store,
-- read from the Google Business Profile (maps.app.goo.gl/t4nFe5hFZVRhH9dp6).
--
-- These complete the LocalBusiness Schema.org JSON-LD: PostalAddress (street +
-- postal code), telephone, and openingHours. Combined with the geo coordinates
-- (V79) this is the full local-SEO signal Google needs for the map pack.
--
-- Guarded so we only replace the empty values or the V24 placeholder seeds
-- ('Ankara, Turkiye', '+90 (312) 000 00 00') — a real value already entered
-- from Admin → Site Ayarları is never overwritten.

-- Street address (shown in footer + PostalAddress.streetAddress)
UPDATE site_settings
   SET setting_value = 'Esenbey Paşakapı Caddesi No:28, Selçuk, Dr. Sami Yağız Cd. No:53 D:21, 51100 Niğde Merkez/Niğde'
 WHERE setting_key = 'contact_address'
   AND (setting_value IS NULL OR setting_value = '' OR setting_value = 'Ankara, Turkiye');

-- Postal code (PostalAddress.postalCode)
UPDATE site_settings
   SET setting_value = '51100'
 WHERE setting_key = 'seo_local_postal_code'
   AND (setting_value IS NULL OR setting_value = '');

-- Default callable phone (footer getDefaultPhone fallback + LocalBusiness.telephone)
UPDATE site_settings
   SET setting_value = '+90 553 999 33 03'
 WHERE setting_key = 'contact_phone'
   AND (setting_value IS NULL OR setting_value = '' OR setting_value = '+90 (312) 000 00 00');

-- Opening hours — the profile lists "24 saat açık" every day.
-- Schema.org day-spec for always-open: Mo-Su 00:00-24:00.
UPDATE site_settings
   SET setting_value = 'Mo-Su 00:00-24:00'
 WHERE setting_key = 'seo_local_opening_hours'
   AND (setting_value IS NULL OR setting_value = '');
