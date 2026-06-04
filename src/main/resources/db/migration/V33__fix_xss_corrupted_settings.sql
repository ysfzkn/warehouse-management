-- V33: Fix settings values corrupted by old XSS sanitizer
-- The old sanitizer converted "/" to "&#x2F;", "&" to "&amp;", etc.
-- This broke URLs, embeds, and special characters in site_settings

UPDATE site_settings SET setting_value = REPLACE(setting_value, '&#x2F;', '/') WHERE setting_value LIKE '%&#x2F;%';
UPDATE site_settings SET setting_value = REPLACE(setting_value, '&amp;', '&') WHERE setting_value LIKE '%&amp;%';
UPDATE site_settings SET setting_value = REPLACE(setting_value, '&lt;', '<') WHERE setting_value LIKE '%&lt;%';
UPDATE site_settings SET setting_value = REPLACE(setting_value, '&gt;', '>') WHERE setting_value LIKE '%&gt;%';
UPDATE site_settings SET setting_value = REPLACE(setting_value, '&quot;', '"') WHERE setting_value LIKE '%&quot;%';
UPDATE site_settings SET setting_value = REPLACE(setting_value, '&#x27;', '''') WHERE setting_value LIKE '%&#x27;%';
