-- =============================================================================
-- V58 — Faz 0 Legal Compliance: Mesafeli Satış sözleşmesi seed,
-- KVKK consent timestamp on orders, ve yasal site setting anahtarlarının
-- varsayılan boş kayıtlarını ekler (admin admin paneli üzerinden doldurur).
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- 1)  orders.kvkk_consent_at: misafir ödemelerde KVKK rızasının verildiği an
--     (Customer entity'sinde authenticated kullanıcı için ayrıca kvkk_consent_at
--     zaten mevcut; burası order bazında snapshot tutar, sipariş anında verilen
--     rızanın hem ne zaman hem hangi context'te alındığını kanıtlar).
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS kvkk_consent_at TIMESTAMP;

COMMENT ON COLUMN orders.kvkk_consent_at IS
    'Sipariş anında müşterinin KVKK rızasını verdiği zaman damgası (misafir checkout için kritik kanıt).';

-- ─────────────────────────────────────────────────────────────────────────────
-- 2)  Mesafeli Satış Sözleşmesi CMS sayfası — V53 zaten "on-bilgilendirme-formu"
--     seed'liyor ama "mesafeli-satis-sozlesmesi" eksikti. Admin sonradan
--     düzenleyebilir; ON CONFLICT DO NOTHING mevcut içeriği bozmaz.
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO cms_pages (slug, title, content, page_type, is_published, published_at)
VALUES
    ('mesafeli-satis-sozlesmesi',
     'Mesafeli Satış Sözleşmesi',
     '<h2>Mesafeli Satış Sözleşmesi</h2>'
     || '<p>İşbu sözleşme, 6502 sayılı Tüketicinin Korunması Hakkında Kanun ve Mesafeli Sözleşmeler '
     || 'Yönetmeliği''ne uygun olarak satıcı ile alıcı arasındaki mesafeli satış ilişkisinin esaslarını düzenler.</p>'
     || '<h3>1. Taraflar</h3>'
     || '<p><b>Satıcı:</b> [Firma Ünvanı, Adres, Vergi No, Mersis No, Telefon, E-posta]</p>'
     || '<p><b>Alıcı:</b> Siparişi veren tüketici (sipariş özetindeki adres ve iletişim bilgileri esastır).</p>'
     || '<h3>2. Sözleşme Konusu ve Bedel</h3>'
     || '<p>Sözleşmenin konusu, alıcının sipariş özetinde belirtilen ürünleri sipariş özetindeki '
     || 'koşullarla satıcıdan satın almasıdır. Tüm vergiler dahil toplam bedel, kargo ücreti ve teslimat '
     || 'süresi sipariş özetinde yer alır.</p>'
     || '<h3>3. Cayma Hakkı</h3>'
     || '<p>Tüketici, malın teslim alındığı tarihten itibaren <b>14 gün</b> içinde herhangi bir gerekçe '
     || 'göstermeksizin ve cezai şart ödemeksizin sözleşmeden cayma hakkına sahiptir. Cayma hakkının '
     || 'kullanılması halinde mal, orijinal ambalajında ve kullanılmamış olarak iade edilmelidir.</p>'
     || '<h3>4. Cayma Hakkının İstisnaları</h3>'
     || '<p>Tüketicinin isteğine veya açıkça onun kişisel ihtiyaçlarına göre hazırlanan, hijyenik olmayan, '
     || 'tek kullanımlık veya niteliği itibariyle iade edilemeyecek ürünler cayma hakkı kapsamı dışındadır.</p>'
     || '<h3>5. Teslimat</h3>'
     || '<p>Ürünler, satıcının anlaşmalı olduğu kargo firması aracılığıyla, sipariş özetinde belirtilen '
     || 'adrese teslim edilir. Teslim süresi sipariş onayından itibaren 30 günü aşamaz.</p>'
     || '<h3>6. Ödeme</h3>'
     || '<p>Alıcı, sipariş sırasında seçtiği ödeme yöntemiyle bedeli öder. Kredi kartı ödemelerinde 3D '
     || 'Secure doğrulaması zorunludur. Havale/EFT ödemelerinde 24 saat içinde transferin tamamlanması '
     || 'gerekir; aksi halde sipariş otomatik olarak iptal edilir.</p>'
     || '<h3>7. Uyuşmazlıkların Çözümü</h3>'
     || '<p>Alıcının ikametgâhının bulunduğu yerdeki Tüketici Hakem Heyetleri ve Tüketici Mahkemeleri '
     || 'yetkilidir. Şikayet ve itirazlar için <a href="/iletisim">iletişim sayfası</a> üzerinden '
     || 'bizimle iletişime geçebilirsiniz.</p>'
     || '<p><em>Bu metin varsayılan şablondur; firma bilgilerinize göre admin panelinden düzenleyin.</em></p>',
     'LEGAL', TRUE, CURRENT_TIMESTAMP)
ON CONFLICT (slug) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3)  Yasal site setting anahtarlarının placeholder kayıtları.
--     StoreFooter bu anahtarları okuyor; admin paneli `legal` grubu altında
--     düzenleme imkânı veriyor. Boş seed kaydı eklemek admin'in panelde alanı
--     görmesini garantiler (UI mevcut anahtarlardan üretiyor).
--     ON CONFLICT DO NOTHING mevcut değerleri korur.
-- ─────────────────────────────────────────────────────────────────────────────
-- NOT: site_settings tablosunda created_at kolonu yok (V24'te tanımlı değil),
-- sadece updated_at var ve DEFAULT CURRENT_TIMESTAMP. Bu yüzden burada
-- sadece setting_key, setting_value, setting_type gönderiyoruz.
INSERT INTO site_settings (setting_key, setting_value, setting_type)
VALUES
    ('company_legal_name',       '',  'STRING'),
    ('mersis_number',            '',  'STRING'),
    ('kep_address',              '',  'STRING'),
    ('tax_office',               '',  'STRING'),
    ('tax_number',               '',  'STRING'),
    ('trade_registry_number',    '',  'STRING'),
    ('chamber_of_commerce',      '',  'STRING'),
    ('etbis_qr_url',             '',  'STRING'),
    ('company_address',          '',  'STRING'),
    ('company_phone',            '',  'STRING'),
    ('company_email',            '',  'STRING'),
    -- Kargo ücretlendirme (Faz 2 — admin panelden ayarlanabilir)
    ('free_shipping_threshold',  '500',   'STRING'),
    ('default_shipping_cost',    '29.99', 'STRING'),
    -- 3DS rule engine (Faz 2 — Türkiye BDDK için default: her zaman 3DS)
    ('threeds_always',           'true',  'STRING'),
    ('threeds_min_amount',       '50',    'STRING')
ON CONFLICT (setting_key) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4)  customers tablosuna data export & silme talep tarihleri (KVKK Madde 13)
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS data_export_requested_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS data_deletion_requested_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS anonymized_at TIMESTAMP;

COMMENT ON COLUMN customers.data_export_requested_at IS
    'KVKK Madde 11 (e) kapsamında müşterinin veri ihracı talep ettiği son tarih.';
COMMENT ON COLUMN customers.data_deletion_requested_at IS
    'KVKK Madde 11 (e) - silme talebinin alındığı tarih (henüz işlenmemiş olabilir).';
COMMENT ON COLUMN customers.anonymized_at IS
    'Hesap anonimleştirme tarihi — sipariş geçmişi yasal saklama gereği korunur, PII silinir.';
