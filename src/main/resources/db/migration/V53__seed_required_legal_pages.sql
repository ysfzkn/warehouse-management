-- Seed the two legal pages required by the checkout flow but missing from the
-- original V20 seed:
--   /sayfa/kvkk                    ← checkbox "KVKK Aydınlatma Metni"
--   /sayfa/on-bilgilendirme-formu  ← checkbox "Ön Bilgilendirme Formu"
-- Also seed /sayfa/cerez-politikasi and /sayfa/iade-ve-degisim as commonly
-- linked legal pages.  Each row is a placeholder the admin will refine from
-- the CMS editor; ON CONFLICT DO NOTHING keeps existing content intact if
-- the slug was already created manually.

INSERT INTO cms_pages (slug, title, content, page_type, is_published, published_at)
VALUES
    ('kvkk',
     'KVKK Aydınlatma Metni',
     '<h2>KVKK Aydınlatma Metni</h2>'
     || '<p>Bu aydınlatma metni, 6698 sayılı Kişisel Verilerin Korunması Kanunu (''<b>KVKK</b>'') kapsamında '
     || 'veri sorumlusu sıfatıyla tarafımızca işlenen kişisel verilerinize ilişkin bilgilendirme amacıyla hazırlanmıştır.</p>'
     || '<h3>1. İşlenen Kişisel Veriler</h3>'
     || '<p>Ad-soyad, iletişim bilgileri, adres, ödeme bilgileri ve sipariş geçmişi dahil olmak üzere '
     || 'hizmet sunumu için gerekli kişisel verileriniz işlenmektedir.</p>'
     || '<h3>2. İşleme Amaçları</h3>'
     || '<ul><li>Sipariş, fatura ve teslimat süreçlerinin yürütülmesi</li>'
     || '<li>Müşteri iletişiminin sağlanması</li>'
     || '<li>Yasal yükümlülüklerin yerine getirilmesi</li></ul>'
     || '<h3>3. Haklarınız</h3>'
     || '<p>KVKK''nın 11. maddesi çerçevesinde sahip olduğunuz haklarınızı kullanmak için '
     || '<a href="/iletisim">iletişim sayfamız</a> üzerinden bizimle iletişime geçebilirsiniz.</p>'
     || '<p><em>Bu metin varsayılan şablondur; firma bilgilerinize göre düzenleyin.</em></p>',
     'LEGAL', TRUE, CURRENT_TIMESTAMP),

    ('on-bilgilendirme-formu',
     'Ön Bilgilendirme Formu',
     '<h2>Ön Bilgilendirme Formu</h2>'
     || '<p>6502 sayılı Tüketicinin Korunması Hakkında Kanun ve Mesafeli Sözleşmeler Yönetmeliği uyarınca, '
     || 'sipariş öncesi bilgilendirme amacıyla aşağıdaki hususlar tüketicimizin dikkatine sunulmaktadır.</p>'
     || '<h3>1. Satıcı Bilgileri</h3>'
     || '<p><b>Ünvan:</b> [Firma Ünvanı]<br>'
     || '<b>Adres:</b> [Adres]<br>'
     || '<b>Telefon:</b> [Telefon]<br>'
     || '<b>E-posta:</b> [E-posta]</p>'
     || '<h3>2. Sözleşme Konusu Ürün / Hizmet</h3>'
     || '<p>Sepetinizdeki ürünlerin temel nitelikleri, vergiler dahil toplam satış fiyatı ve kargo ücreti '
     || 'sipariş özetinde yer almaktadır.</p>'
     || '<h3>3. Ödeme & Teslimat</h3>'
     || '<p>Ödeme yönteminiz sipariş sırasında seçilecektir. Ürünler, siparişin onaylanmasını takiben '
     || 'taahhüt edilen süre içinde teslim edilir.</p>'
     || '<h3>4. Cayma Hakkı</h3>'
     || '<p>Tüketici, malın teslim alındığı tarihten itibaren <b>14 gün</b> içinde herhangi bir gerekçe '
     || 'göstermeksizin ve cezai şart ödemeksizin sözleşmeden cayma hakkına sahiptir.</p>'
     || '<h3>5. Şikayet ve İtiraz</h3>'
     || '<p>Şikayet ve itirazlarınızı Tüketici Hakem Heyetleri veya Tüketici Mahkemelerine iletebilirsiniz.</p>'
     || '<p><em>Bu metin varsayılan şablondur; firma bilgilerinize göre düzenleyin.</em></p>',
     'LEGAL', TRUE, CURRENT_TIMESTAMP),

    ('cerez-politikasi',
     'Çerez Politikası',
     '<h2>Çerez Politikası</h2>'
     || '<p>Sitemizde, kullanıcı deneyimini iyileştirmek ve hizmetlerimizi sunabilmek amacıyla çerezler kullanılmaktadır.</p>'
     || '<h3>Çerez Türleri</h3>'
     || '<ul><li><b>Zorunlu çerezler:</b> Site işleyişi için gereklidir.</li>'
     || '<li><b>Performans çerezleri:</b> Site kullanımını analiz eder.</li>'
     || '<li><b>Pazarlama çerezleri:</b> İlgili reklamları göstermek için kullanılır.</li></ul>'
     || '<p>Tarayıcı ayarlarınızdan çerezleri devre dışı bırakabilirsiniz.</p>'
     || '<p><em>Bu metin varsayılan şablondur; firma bilgilerinize göre düzenleyin.</em></p>',
     'LEGAL', TRUE, CURRENT_TIMESTAMP),

    ('iade-ve-degisim',
     'İade ve Değişim Koşulları',
     '<h2>İade ve Değişim Koşulları</h2>'
     || '<p>Ürünlerimizden herhangi birinde değişim veya iade talebinde bulunmak isterseniz, aşağıdaki koşullara tabidir.</p>'
     || '<h3>Cayma Hakkı</h3>'
     || '<p>Tüketici, malın teslim alındığı tarihten itibaren 14 gün içinde herhangi bir gerekçe göstermeksizin '
     || 've cezai şart ödemeksizin sözleşmeden cayma hakkına sahiptir.</p>'
     || '<h3>İade Koşulları</h3>'
     || '<ul><li>Ürün orijinal ambalajında ve kullanılmamış olmalıdır.</li>'
     || '<li>Fatura ile birlikte iade edilmelidir.</li>'
     || '<li>Hijyenik olmayan ürünler, yazılım lisansları gibi iade kapsamı dışındaki ürünler iade alınmaz.</li></ul>'
     || '<p><em>Bu metin varsayılan şablondur; firma bilgilerinize göre düzenleyin.</em></p>',
     'LEGAL', TRUE, CURRENT_TIMESTAMP)
ON CONFLICT (slug) DO NOTHING;
