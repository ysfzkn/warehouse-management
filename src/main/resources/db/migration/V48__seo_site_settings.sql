-- SEO için site-wide ayarlar
INSERT INTO site_settings (setting_key, setting_value, setting_type) VALUES
    ('seo_default_og_image', '', 'STRING'),
    ('seo_twitter_handle', '', 'STRING'),
    ('seo_default_meta_description', '', 'STRING'),
    ('seo_organization_name', '', 'STRING'),
    ('seo_canonical_domain', '', 'STRING'),
    ('analytics_google_id', '', 'STRING'),
    ('analytics_facebook_pixel_id', '', 'STRING')
ON CONFLICT (setting_key) DO NOTHING;
