-- Add the full Google Business Profile name as a brand alias (alternateName).
--
-- The GBP listing is keyword-stuffed:
--   "ATS DTM Profilo Simfer Ferre Fakir Philips Karcher Hoover"
-- We keep the *primary* schema name clean ("ATS DTM") — keyword-stuffed names
-- violate Google's guidelines and dilute the brand. The individual brands are
-- already separate signals via seo_local_primary_brands (Schema.org `brand` +
-- `knowsAbout`). But listing the exact GBP string as an alternateName means a
-- direct search for that full name still resolves to this business and keeps
-- the site ↔ GBP names linked.
--
-- Idempotent: only appends the alias if it is not already present.

UPDATE site_settings
   SET setting_value = setting_value || ', ATS DTM Profilo Simfer Ferre Fakir Philips Karcher Hoover'
 WHERE setting_key = 'seo_brand_aliases'
   AND setting_value NOT LIKE '%ATS DTM Profilo Simfer Ferre Fakir Philips Karcher Hoover%';
