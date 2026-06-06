-- Local SEO + GEO (generative-engine optimization) settings.
--
-- Powers LocalBusiness Schema.org JSON-LD + automatic "{city} {brand} {category}"
-- keyword/title combinations so the store ranks for searches like
-- "Niğde Profilo", "Niğde çamaşır makinesi", and the brand name "ATS DTM".
--
-- All values are editable from Admin → Site Ayarları → Yerel SEO (Konum).
-- The merchant should fill in the real geo coordinates (from Google Maps).

INSERT INTO site_settings (setting_key, setting_value, setting_type) VALUES
    ('seo_local_city',               'Niğde',          'STRING'),
    ('seo_local_region',             'Niğde',          'STRING'),
    ('seo_local_postal_code',        '',               'STRING'),
    ('seo_geo_lat',                  '',               'STRING'),
    ('seo_geo_lng',                  '',               'STRING'),
    ('seo_local_business_type',      'HomeGoodsStore', 'STRING'),
    ('seo_local_price_range',        '₺₺',             'STRING'),
    ('seo_local_opening_hours',      '',               'STRING'),
    ('seo_local_founding_year',      '1980',           'STRING'),
    ('seo_local_slogan',             '1980''den beri güvenilir beyaz eşya ve ev aletleri', 'STRING'),
    ('seo_brand_aliases',            'ATS DTM, ATSDTM, ATS DTM E-Ticaret, ATS DTM Mağaza', 'STRING'),
    ('seo_local_primary_brands',     'Profilo, Simfer, Ferre, Fakir, Philips, Hoover, Karcher', 'STRING'),
    ('seo_local_primary_categories', 'Çamaşır Makinesi, Buzdolabı, Bulaşık Makinesi, Fırın, Davlumbaz, Küçük Ev Aletleri, Ankastre', 'STRING'),
    ('seo_local_keywords_extra',     '', 'STRING'),
    ('seo_local_description',
     'ATS DTM — 1980''den beri Niğde''de Profilo, Simfer, Ferre, Fakir, Philips, Hoover ve Karcher yetkili satıcısı. Beyaz eşya, ankastre ve küçük ev aletlerinde uygun fiyat ve güvenli alışveriş.',
     'STRING'),
    -- Seed org name + default meta description for the storefront (kept if already set).
    ('seo_organization_name',        'ATS DTM',        'STRING'),
    ('seo_default_meta_description',
     'ATS DTM — 1980''den beri Niğde''de Profilo, Simfer, Ferre, Fakir, Philips, Hoover ve Karcher yetkili satıcısı. Beyaz eşya, ankastre ve küçük ev aletleri.',
     'STRING')
ON CONFLICT (setting_key) DO NOTHING;
