-- V31: Add missing CMS pages + new site settings + update placeholder content

-- Add iletisim (contact) page
INSERT INTO cms_pages (slug, title, content, page_type, is_published, is_active, sort_order, published_at, created_at, updated_at)
VALUES ('iletisim', 'İletişim', '<h2>Bize Ulaşın</h2><p>Sorularınız, önerileriniz veya şikayetleriniz için bizimle iletişime geçebilirsiniz.</p>', 'CONTENT', TRUE, TRUE, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (slug) DO NOTHING;

-- Update existing placeholder content with better defaults
UPDATE cms_pages SET title = 'Mesafeli Satış Sözleşmesi', content = '<h2>Mesafeli Satış Sözleşmesi</h2><p>Bu sözleşme, 6502 sayılı Tüketicinin Korunması Hakkında Kanun ve Mesafeli Sözleşmeler Yönetmeliği kapsamında düzenlenmiştir.</p><p>Sözleşme içeriği admin panelden düzenlenebilir.</p>' WHERE slug = 'mesafeli-satis-sozlesmesi' AND content = '<p>Icerik hazirlanacak</p>';

UPDATE cms_pages SET title = 'Gizlilik ve Güvenlik Politikası', content = '<h2>Gizlilik ve Güvenlik Politikası</h2><p>Kişisel verilerinizin korunması bizim için önemlidir. Bu politika, toplanan verilerin nasıl işlendiğini açıklar.</p><p>Politika içeriği admin panelden düzenlenebilir.</p>' WHERE slug = 'gizlilik-ve-guvenlik' AND content = '<p>Icerik hazirlanacak</p>';

UPDATE cms_pages SET title = 'İptal ve İade Şartları', content = '<h2>İptal ve İade Şartları</h2><p>Satın aldığınız ürünleri, teslim tarihinden itibaren 14 gün içinde herhangi bir gerekçe göstermeksizin iade edebilirsiniz.</p><p>İade koşulları admin panelden düzenlenebilir.</p>' WHERE slug = 'iptal-ve-iade-sartlari' AND content = '<p>Icerik hazirlanacak</p>';

UPDATE cms_pages SET title = 'KVKK Aydınlatma Metni', content = '<h2>KVKK Aydınlatma Metni</h2><p>6698 sayılı Kişisel Verilerin Korunması Kanunu kapsamında kişisel verileriniz işlenmektedir.</p><p>Aydınlatma metni admin panelden düzenlenebilir.</p>' WHERE slug = 'kvkk-aydinlatma-metni' AND content = '<p>Icerik hazirlanacak</p>';

UPDATE cms_pages SET title = 'Hakkımızda', content = '<h2>Hakkımızda</h2><p>Müşterilerimize en kaliteli ürünleri en uygun fiyatlarla sunmayı hedefliyoruz.</p><p>Hakkımızda içeriği admin panelden düzenlenebilir.</p>' WHERE slug = 'hakkimizda' AND content = '<p>Icerik hazirlanacak</p>';

-- New site settings
INSERT INTO site_settings (setting_key, setting_value, setting_type, updated_at) VALUES
    ('contact_map_embed', '', 'STRING', CURRENT_TIMESTAMP),
    ('contact_form_email', '', 'STRING', CURRENT_TIMESTAMP),
    ('header_announcement', '500 TL ve üzeri siparişlerde ücretsiz kargo!', 'STRING', CURRENT_TIMESTAMP)
ON CONFLICT (setting_key) DO NOTHING;

-- Update footer text with proper Turkish
UPDATE site_settings SET setting_value = '© 2026 Tüm hakları saklıdır.' WHERE setting_key = 'footer_text' AND setting_value = '© 2026 Tum haklari saklidir.';

-- Update contact address with proper Turkish
UPDATE site_settings SET setting_value = 'Ankara, Türkiye' WHERE setting_key = 'contact_address' AND setting_value = 'Ankara, Turkiye';
