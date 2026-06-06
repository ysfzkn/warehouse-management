-- Multi-phone directory + manual WhatsApp order settings.
--
-- phone_directory: JSON string (array) of phone entries, managed from the admin
--   Site Settings → İletişim → Telefon Rehberi editor. Shape:
--   [{ "category":"business"|"mobile", "subType":"merkez"|"sube"|null,
--      "label":"", "number":"+90...", "isDefault":true|false }]
--   Empty by default → display surfaces fall back to contact_phone.
--
-- whatsapp_order_number / whatsapp_order_template power the product page
--   "WhatsApp ile Sipariş" button. Template placeholders: {urun} {fiyat} {sku}
--   {link} ({marka} {kategori} also supported). Real newlines (E'...\n...') —
--   the frontend URL-encodes the final message, so do NOT pre-encode here.

INSERT INTO site_settings (setting_key, setting_value, setting_type) VALUES
    ('phone_directory',       '',  'STRING'),
    ('whatsapp_order_number', '',  'STRING'),
    ('whatsapp_order_template',
     E'Merhaba, şu ürünle ilgileniyorum:\n\n{urun}\nFiyat: {fiyat}\nSKU: {sku}\n\n{link}',
     'STRING')
ON CONFLICT (setting_key) DO NOTHING;
