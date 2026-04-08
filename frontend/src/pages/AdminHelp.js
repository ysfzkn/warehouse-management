import React, { useState } from 'react';
import { FiSearch, FiChevronDown, FiChevronUp, FiBookOpen, FiBox, FiShoppingCart, FiCreditCard, FiTruck, FiUsers, FiSettings, FiAlertTriangle, FiHelpCircle } from 'react-icons/fi';

const SECTIONS = [
  {
    id: 'start', icon: FiBookOpen, color: '#2563eb', title: 'Başlangıç',
    items: [
      { q: 'Admin panele nasıl giriş yaparım?', a: 'Tarayıcınızda sitenizin admin adresine gidin (örn: siteniz.com/login). Size verilen kullanıcı adı ve şifreyi girin. İlk girişten sonra şifrenizi değiştirmeniz önerilir.' },
      { q: 'Güvenlik kodu nedir?', a: 'Silme, ödeme onaylama, gateway ekleme gibi kritik işlemlerde sizden ek bir güvenlik kodu istenir. Bu kod, yetkisiz erişimi önlemek için kullanılır. Güvenlik kodunuzu "Ayarlar → Yönetici Ayarları" bölümünden değiştirebilirsiniz.' },
      { q: 'Admin panelin genel yapısı nasıl?', a: 'Üst menüde 5 ana bölüm bulunur:\n\n• Envanter — Ürünler, kategoriler, markalar, renkler\n• Depolar — Depo yönetimi\n• Stok Yönetimi — Stok takibi ve düşük stok uyarıları\n• E-Ticaret — Siparişler, ödemeler, müşteriler, destek talepleri\n• Ayarlar — Ödeme, kargo, site ayarları, CMS' },
      { q: 'Stok uyarı rozeti ne anlama geliyor?', a: '"Stok Yönetimi" menüsündeki kırmızı rakam, minimum stok seviyesinin altına düşmüş ürün sayısını gösterir. Bu ürünleri stok yönetimi sayfasından görebilir ve stok ekleyebilirsiniz.' },
    ],
  },
  {
    id: 'products', icon: FiBox, color: '#059669', title: 'Ürün Yönetimi',
    items: [
      { q: 'Yeni ürün nasıl eklerim?', a: '1. Üst menüden "Envanter → Ürünler" sayfasına gidin\n2. "Yeni Ürün" butonuna tıklayın\n3. Ürün adı, SKU (stok kodu), fiyat bilgilerini girin\n4. Kategori, marka ve renk seçin\n5. Fotoğraf yükleyin (birden fazla yükleyebilirsiniz)\n6. "Kaydet" butonuna tıklayın' },
      { q: 'Ürün fotoğrafı nasıl yüklerim?', a: 'Ürün düzenleme formunun alt kısmında "Görseller" bölümü bulunur. "Dosya seçin" alanına tıklayarak veya dosyaları sürükleyerek fotoğraf yükleyebilirsiniz. PNG, JPG ve WebP formatları desteklenir. Birden fazla fotoğraf yükleyebilirsiniz.' },
      { q: 'İndirim/kampanya nasıl oluştururum?', a: 'Ürün düzenleme formunda:\n• İndirimli Fiyat alanına indirimli tutarı girin\n• Yüzde hesaplama otomatik yapılır\n• İsteğe bağlı: Başlangıç ve bitiş tarihi belirleyin\n\nToplu indirim için: "Kuponlar" sayfasından indirim kuponu oluşturabilirsiniz (yüzde, sabit tutar veya ücretsiz kargo).' },
      { q: 'SEO ayarları nedir, nasıl düzenlerim?', a: 'Ürün formunun alt kısmındaki "SEO Ayarları" bölümünü açın:\n• Meta Başlık — Google arama sonuçlarında görünen başlık (max 200 karakter)\n• Meta Açıklama — Arama sonuçlarındaki açıklama metni (max 500 karakter)\n• Slug — URL\'deki ürün adı (otomatik oluşturulabilir)\n\nBu alanları doldurmanız Google sıralamanızı iyileştirir.' },
      { q: 'Kategori nasıl eklerim?', a: '"Envanter → Kategoriler" sayfasından yeni kategori ekleyebilirsiniz. Alt kategori oluşturmak için önce ana kategoriyi seçin, ardından "Alt Kategori Ekle" butonunu kullanın.' },
      { q: '"Öne Çıkan" ve "Yeni Ürün" etiketleri ne işe yarar?', a: 'Ürün formundaki bu checkbox\'lar:\n• Öne Çıkan — Ana sayfada "Öne Çıkan Ürünler" bölümünde gösterilir\n• Yeni Ürün — Ürün kartında "Yeni" rozeti gösterilir\n\nHer iki etiket de müşterilerin dikkatini çekmek için kullanılır.' },
      { q: 'Toplu fiyat güncelleme nasıl yaparım?', a: '"Ürünler" sayfasında birden fazla ürünü seçin, ardından "Toplu İşlemler" butonundan "Fiyat Güncelle" seçeneğini kullanın. Yüzde artış/azalış veya sabit tutar ekleyebilirsiniz.' },
    ],
  },
  {
    id: 'orders', icon: FiShoppingCart, color: '#7c3aed', title: 'Sipariş Yönetimi',
    items: [
      { q: 'Sipariş durumları ne anlama geliyor?', a: '• Ödeme Bekliyor — Müşteri henüz ödeme yapmadı\n• Ödendi — Ödeme alındı, hazırlanmayı bekliyor\n• Hazırlanıyor — Sipariş depoda hazırlanıyor\n• Kargoda — Kargoya verildi\n• Teslim Edildi — Müşteriye ulaştı\n• İptal Edildi — Sipariş iptal edildi\n• İade Talebi — Müşteri iade talep etti' },
      { q: 'Sipariş durumunu nasıl değiştiririm?', a: '1. "E-Ticaret → Siparişler" sayfasına gidin\n2. Siparişe tıklayarak detayını açın\n3. "Durum Güncelle" butonuna tıklayın\n4. Geçiş yapılabilecek durumlar listede görünür\n5. Yeni durumu seçin ve not ekleyin (isteğe bağlı)\n6. Durum güncellendiğinde müşteriye otomatik e-posta gönderilir' },
      { q: 'Kargo bilgisi nasıl girerim?', a: 'Sipariş detayında "Kargo" butonuna tıklayın:\n• Kargo firmasını seçin\n• Takip numarasını girin\n• Kaydet\n\nMüşteri, siparişlerim sayfasından takip numarasına tıklayarak kargo firmasının sitesinde gönderiyi takip edebilir.' },
      { q: 'Fatura nasıl yüklerim?', a: 'Sipariş detayındaki "Fatura Bilgileri" bölümünde:\n1. "Fatura Yükle" butonuna tıklayın\n2. Fatura numarasını girin (isteğe bağlı)\n3. PDF veya görsel dosyasını seçin/sürükleyin\n4. "Yükle" butonuna tıklayın\n\nYüklenen faturayı "Görüntüle" veya "İndir" butonlarıyla kontrol edebilirsiniz. Müşteri de kendi siparişlerinden faturayı indirebilir.' },
      { q: 'Havale/EFT ödemesini nasıl onaylarım?', a: 'Ödeme yöntemi "Havale/EFT" olan siparişlerde, müşteri havaleyi yaptıktan sonra:\n1. Banka hesabınızdan havaleyi kontrol edin\n2. Sipariş detayında "Havale Onayla" butonuna tıklayın\n3. Güvenlik kodunuzu girin\n4. Sipariş otomatik olarak "Ödendi" durumuna geçer' },
      { q: 'İade talebi nasıl değerlendiririm?', a: 'Müşteri iade talebi oluşturduğunda sipariş durumu "İade Talebi" olur.\n\n1. Sipariş detayından iade nedenini inceleyin\n2. Uygunsa: Durumu "İade Edildi" → "İade Ödemesi" olarak güncelleyin\n3. Uygun değilse: Durumu "Teslim Edildi" olarak geri alın ve müşteriye not bırakın' },
      { q: 'Sipariş listesini Excel olarak nasıl indiririm?', a: '"Siparişler" sayfasının sağ üst köşesindeki "Excel İndir" butonuna tıklayın. Aktif filtrelerinize göre (durum, tarih, ödeme yöntemi) filtrelenmiş sipariş listesi indirilir.' },
    ],
  },
  {
    id: 'payments', icon: FiCreditCard, color: '#dc2626', title: 'Ödeme Ayarları',
    items: [
      { q: 'Ödeme yöntemlerini nasıl açıp kapatabilirim?', a: '"Ayarlar → Ödeme Ayarları" sayfasının üst kısmında 3 toggle bulunur:\n• Kredi Kartı ile Ödeme\n• Havale / EFT ile Ödeme\n• Kapıda Ödeme\n\nToggle\'ı kapatırsanız o yöntem müşterilere gösterilmez.' },
      { q: 'iyzico gateway nasıl eklerim?', a: '1. "Ödeme Ayarları" sayfasında "Yeni Gateway Ekle" butonuna tıklayın\n2. Protokol: "iyzico" seçin\n3. Kod: "IYZICO_1" yazın\n4. API Key ve Secret Key: iyzico panelinden alın\n5. Base URL: sandbox için "https://sandbox-api.iyzipay.com"\n6. Callback URL: "https://siteniz.com/api/store/payment/callback"\n7. Ortam: Test için "Sandbox", canlı için "Production"\n8. Güvenlik kodu girip "Oluştur"a tıklayın\n9. Gateway\'i aktifleştirin ve varsayılan yapın' },
      { q: 'iyzico sandbox test nasıl yaparım?', a: '1. sandbox-merchant.iyzipay.com adresinden hesap oluşturun\n2. API anahtarlarını alıp gateway\'e girin\n3. Sandbox modunda test kartlarıyla deneme yapın:\n   • Kart No: 5528790000000008\n   • SKT: 12/2030\n   • CVV: 123\n   • 3D SMS kodu: 123456\n4. Gerçek para çekilmez' },
      { q: 'Havale bilgilerini nasıl ayarlarım?', a: '"Ödeme Ayarları" sayfasında "Havale / EFT Bilgileri" bölümünden:\n• Banka adı\n• IBAN numarası\n• Hesap sahibi adı\n• Ödeme süresi (saat)\ndeğerlerini girin ve "Kaydet"e tıklayın.' },
      { q: '"Test" butonu ne işe yarar?', a: 'Gateway listesindeki "Test" (fiş ikonu) butonu, girdiğiniz API anahtarlarının geçerli olup olmadığını kontrol eder. Gerçek ödeme yapmaz, sadece bağlantı doğrulaması yapar.' },
    ],
  },
  {
    id: 'cargo', icon: FiTruck, color: '#2563eb', title: 'Kargo Ayarları',
    items: [
      { q: 'Kargo firması nasıl eklerim?', a: '"Ayarlar → Kargo Ayarları" sayfasından "Yeni Kargo Firması" butonuna tıklayın:\n• Firma adı ve kodu\n• Temel kargo ücreti (₺)\n• Desi başına ek ücret (₺)\n• Ücretsiz kargo alt limiti (bu tutarın üzerinde kargo ücretsiz olur)\n• Tahmini teslimat süresi (gün)\n• KDV oranı (%)\n• Kargo takip URL şablonu' },
      { q: 'Kargo fiyatı nasıl hesaplanıyor?', a: 'Formül: Temel Ücret + (Desi × Desi Ücreti)\n\nDesi hesabı: Ürünün fiziksel ağırlığı ile hacimsel ağırlığından (En×Boy×Yükseklik / 3000) büyük olanı alınır.\n\nSepet toplamı ücretsiz kargo limitini aşarsa kargo ücretsiz olur.' },
      { q: 'Takip URL şablonu nedir?', a: 'Kargo firmasının gönderi sorgulama sayfası URL\'sidir. {trackingNo} kısmı otomatik olarak takip numarasıyla değiştirilir.\n\nÖrnek: https://www.yurticikargo.com/tr/online-servisler/gonderi-sorgula?code={trackingNo}' },
    ],
  },
  {
    id: 'customers', icon: FiUsers, color: '#d97706', title: 'Müşteri ve Destek',
    items: [
      { q: 'Müşteri listesini nasıl görüntülerim?', a: '"E-Ticaret → Müşteriler" sayfasında tüm kayıtlı müşterileri görebilirsiniz. Ad, e-posta, telefon ve kayıt tarihine göre arama yapabilirsiniz.' },
      { q: 'Destek taleplerini nasıl yanıtlarım?', a: '1. "E-Ticaret → Destek Talepleri" sayfasına gidin\n2. Listeden talebi seçin (sol panel)\n3. Sağ panelde müşterinin mesajını okuyun\n4. Yanıtınızı yazın ve "Yanıtla" butonuna tıklayın\n5. Durumu güncelleyin (İşlemde → Yanıtlandı → Kapatıldı)\n\nMüşteri yanıtınızı "Destek Taleplerim" sayfasından görebilir.' },
      { q: 'Müşteri hesabını nasıl deaktif ederim?', a: '"Müşteriler" sayfasında müşteri detayını açın, "Durum Değiştir" butonuyla hesabı pasif hale getirebilirsiniz. Pasif hesaplar giriş yapamaz.' },
    ],
  },
  {
    id: 'content', icon: FiSettings, color: '#059669', title: 'İçerik ve Site Ayarları',
    items: [
      { q: 'Logo nasıl değiştiririm?', a: '"Ayarlar → Site Ayarları → Marka & Görünüm" bölümünden:\n1. Mevcut logo önizlemesinin altındaki "Değiştir" butonuna tıklayın\n2. Yeni logo dosyasını seçin (PNG, SVG, WebP)\n3. Güvenlik kodunuzu girin\n4. Logo otomatik güncellenir' },
      { q: 'Banner nasıl eklerim?', a: '"Ayarlar → İçerik Yönetimi" sayfasından "Banner" tipinde yeni içerik ekleyebilirsiniz:\n• Görsel yükleyin\n• Link atayın (kategori, ürün veya dış URL)\n• Sıralama numarası verin\n\nBannerlar ana sayfada slayt gösterisi olarak görünür.' },
      { q: 'Duyuru çubuğu nasıl düzenlerim?', a: '"Site Ayarları → Genel" bölümünde "Üst Banner Duyuru Mesajı" alanına istediğiniz metni yazın. Bu metin sitenin en üstünde sabit bir çubukta gösterilir. Boş bırakırsanız çubuk gizlenir.' },
      { q: 'Site renklerini nasıl değiştiririm?', a: '"Site Ayarları → Marka & Görünüm" bölümünde:\n• Ana Renk — Butonlar, linkler ve vurgularda kullanılır\n• İkincil Renk — İkincil butonlar ve arka plan tonlarında\n\nRenk seçiciden istediğiniz rengi seçin veya HEX kodunu girin (#2563eb gibi).' },
      { q: 'Sabit sayfa nasıl oluştururum? (Hakkımızda, SSS vb.)', a: '"İçerik Yönetimi" sayfasından "Sayfa" tipinde yeni içerik ekleyebilirsiniz. Slug alanına URL\'de görünecek adı yazın (ör: hakkimizda). İçerik HTML editörüyle düzenlenebilir.' },
    ],
  },
  {
    id: 'troubleshoot', icon: FiAlertTriangle, color: '#ef4444', title: 'Sorun Giderme',
    items: [
      { q: 'Ödeme başarısız oldu, ne yapmalıyım?', a: 'Olası nedenler:\n• Müşterinin kartında yeterli bakiye yok\n• 3D Secure doğrulama başarısız (yanlış SMS kodu)\n• Gateway bağlantı sorunu\n\nÇözüm: Sipariş detayından hata mesajını kontrol edin. Gateway test butonuyla bağlantıyı doğrulayın. Sorun devam ederse gateway ayarlarını kontrol edin.' },
      { q: 'Ürün fotoğrafları mağazada görünmüyor', a: 'Kontrol edin:\n1. Ürünü düzenleyin, "Görseller" bölümünde fotoğraf yüklü mü?\n2. Fotoğraf formatı destekleniyor mu? (PNG, JPG, WebP)\n3. Dosya boyutu 20MB\'dan küçük mü?\n4. Backend sunucusu çalışıyor mu? (uploads/ klasörü erişilebilir olmalı)' },
      { q: 'Müşteri kayıt olmuyor veya giriş yapamıyor', a: 'Kontrol edin:\n1. E-posta doğrulama maili gidiyor mu? (Mail ayarlarını kontrol edin)\n2. SMTP ayarları doğru mu? (Site Ayarları → mail yapılandırması)\n3. Müşteri hesabı aktif mi? (Müşteriler sayfasından kontrol edin)' },
      { q: 'Sipariş durumu güncellenmiyor', a: 'Sipariş durum geçişleri belirli kurallara tabidir:\n• Ödeme Bekliyor → sadece Ödendi, Hazırlanıyor veya İptal\n• Ödendi → Hazırlanıyor veya İptal\n• Hazırlanıyor → Kargoda veya İptal\n• Kargoda → Teslim Edildi veya İade Talebi\n\nİzin verilmeyen geçişlerde hata alırsınız.' },
      { q: 'Excel export çalışmıyor', a: '"Excel İndir" butonuna tıkladığınızda dosya inmiyor veya hata alıyorsanız:\n1. Tarayıcınızın pop-up engelleyicisini kontrol edin\n2. Admin oturumunuzun açık olduğundan emin olun\n3. Sayfa yenileyip tekrar deneyin' },
      { q: 'Kargo takip linki çalışmıyor', a: 'Kargo Ayarları sayfasında ilgili firmanın "Takip URL Şablonu" alanını kontrol edin. URL\'de {trackingNo} kısmının olduğundan emin olun. Bazı kargo firmaları URL formatını değiştirebilir.' },
      { q: 'Site yavaş yükleniyor', a: 'Olası çözümler:\n• Ürün fotoğraflarını optimize edin (2MB altında tutun)\n• Çok fazla banner kullanmaktan kaçının\n• Sunucu kaynaklarını kontrol edin (RAM, CPU)\n• Veritabanı bağlantı havuzu ayarlarını kontrol edin' },
      { q: 'Teknik destek için nasıl iletişim kurabilirim?', a: 'Yazılım geliştiricisiyle iletişime geçin:\n• Hata mesajının ekran görüntüsünü alın\n• Hangi sayfada, hangi işlemi yaparken hata aldığınızı belirtin\n• Tarayıcı konsol hatalarını gönderin (F12 → Console sekmesi)' },
    ],
  },
];

function HelpSection({ section, searchTerm, defaultOpen }) {
  const [open, setOpen] = useState(defaultOpen);
  const Icon = section.icon;

  const filtered = searchTerm
    ? section.items.filter(i => i.q.toLowerCase().includes(searchTerm.toLowerCase()) || i.a.toLowerCase().includes(searchTerm.toLowerCase()))
    : section.items;

  if (searchTerm && filtered.length === 0) return null;

  return (
    <div className="mb-3" style={{ borderRadius: 16, overflow: 'hidden', background: '#fff', boxShadow: '0 2px 16px rgba(0,0,0,0.06)', transition: 'box-shadow 0.2s' }}>
      <div className="d-flex align-items-center gap-3 px-4 py-3" style={{ cursor: 'pointer', borderLeft: `4px solid ${section.color}` }} onClick={() => setOpen(!open)}>
        <div style={{ width: 42, height: 42, borderRadius: 12, background: `linear-gradient(135deg, ${section.color}15, ${section.color}25)`, display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: `0 2px 8px ${section.color}20` }}>
          <Icon size={18} style={{ color: section.color }} />
        </div>
        <div className="flex-grow-1">
          <h6 className="mb-0 fw-bold" style={{ color: '#1e293b' }}>{section.title}</h6>
          <small className="text-muted">{filtered.length} konu</small>
        </div>
        <div style={{ width: 28, height: 28, borderRadius: 8, background: '#f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          {open ? <FiChevronUp size={14} className="text-muted" /> : <FiChevronDown size={14} className="text-muted" />}
        </div>
      </div>
      {open && (
        <div className="px-4 pb-3">
          <div className="d-flex flex-column gap-2">
            {filtered.map((item, i) => (
              <HelpItem key={i} item={item} searchTerm={searchTerm} />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function HelpItem({ item, searchTerm }) {
  const [open, setOpen] = useState(!!searchTerm);

  return (
    <div style={{ borderRadius: 12, overflow: 'hidden', border: open ? '1px solid #dbeafe' : '1px solid #e8ecf1', transition: 'all 0.2s', boxShadow: open ? '0 2px 12px rgba(37,99,235,0.08)' : 'none' }}>
      <div className="d-flex align-items-center gap-2 px-3 py-2" style={{ cursor: 'pointer', background: open ? '#eff6ff' : '#fafbfc', transition: 'background 0.15s' }} onClick={() => setOpen(!open)}
        onMouseEnter={e => { if (!open) e.currentTarget.style.background = '#f1f5f9'; }}
        onMouseLeave={e => { if (!open) e.currentTarget.style.background = '#fafbfc'; }}>
        <FiHelpCircle size={14} style={{ color: open ? '#2563eb' : '#94a3b8' }} className="flex-shrink-0" />
        <span className="small fw-medium flex-grow-1" style={{ color: open ? '#1e40af' : '#334155' }}>{item.q}</span>
        <div style={{ width: 22, height: 22, borderRadius: 6, background: open ? '#dbeafe' : '#e8ecf1', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
          {open ? <FiChevronUp size={12} style={{ color: '#2563eb' }} /> : <FiChevronDown size={12} className="text-muted" />}
        </div>
      </div>
      {open && (
        <div className="px-3 pb-3 pt-2" style={{ background: '#fff', borderTop: '1px solid #eff6ff' }}>
          <div className="small" style={{ whiteSpace: 'pre-line', lineHeight: 1.8, color: '#475569' }}>{item.a}</div>
        </div>
      )}
    </div>
  );
}

export default function AdminHelp() {
  const [search, setSearch] = useState('');

  const totalQuestions = SECTIONS.reduce((sum, s) => sum + s.items.length, 0);
  const matchCount = search ? SECTIONS.reduce((sum, s) => sum + s.items.filter(i => i.q.toLowerCase().includes(search.toLowerCase()) || i.a.toLowerCase().includes(search.toLowerCase())).length, 0) : totalQuestions;

  return (
    <div style={{ minHeight: '100vh', background: 'linear-gradient(180deg, #f0f4f8 0%, #e8ecf1 100%)', margin: '-1rem -1.5rem', padding: '2rem 1.5rem' }}>
      <div style={{ maxWidth: 900, margin: '0 auto' }}>

        {/* Hero Header */}
        <div className="text-center mb-5 py-4 px-3 rounded-4" style={{
          background: 'linear-gradient(135deg, #1e3a5f 0%, #2563eb 50%, #3b82f6 100%)',
          boxShadow: '0 8px 32px rgba(37,99,235,0.25)',
        }}>
          <div style={{ width: 64, height: 64, borderRadius: 16, background: 'rgba(255,255,255,0.15)', backdropFilter: 'blur(8px)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', marginBottom: 16 }}>
            <FiBookOpen size={28} color="#fff" />
          </div>
          <h2 className="fw-bold mb-2" style={{ color: '#fff' }}>Yardım ve Dokümantasyon</h2>
          <p className="mb-0" style={{ color: 'rgba(255,255,255,0.7)', fontSize: 14 }}>Admin panel kullanım kılavuzu — {totalQuestions} konu</p>
        </div>

        {/* Search */}
        <div className="mb-4" style={{ marginTop: -28, position: 'relative', zIndex: 1 }}>
          <div className="mx-auto" style={{ maxWidth: 640 }}>
            <div className="d-flex align-items-center" style={{
              background: '#fff', borderRadius: 16, boxShadow: '0 4px 24px rgba(0,0,0,0.1)', padding: '4px 4px 4px 20px',
              border: '2px solid transparent', transition: 'border-color 0.2s',
            }}>
              <FiSearch size={20} className="text-muted flex-shrink-0" />
              <input className="form-control border-0 shadow-none" placeholder="Ne yapmak istiyorsunuz? Arayın..."
                value={search} onChange={e => setSearch(e.target.value)}
                style={{ fontSize: 15, padding: '14px 12px', background: 'transparent' }} />
              {search && <button className="btn btn-sm text-muted" onClick={() => setSearch('')} style={{ marginRight: 8 }}><i className="fas fa-times" /></button>}
            </div>
          </div>
          {search && <small className="text-muted d-block mt-2 text-center">{matchCount} sonuç bulundu</small>}
        </div>

        {/* Quick links */}
        {!search && (
          <div className="d-flex flex-wrap gap-2 justify-content-center mb-4">
            {SECTIONS.map(s => (
              <button key={s.id} className="d-flex align-items-center gap-2 px-3 py-2"
                style={{
                  borderRadius: 12, border: 'none', background: '#fff', cursor: 'pointer',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.06)', fontSize: 13, fontWeight: 500, color: '#475569',
                  transition: 'all 0.15s',
                }}
                onMouseEnter={e => { e.currentTarget.style.boxShadow = '0 4px 16px rgba(0,0,0,0.12)'; e.currentTarget.style.transform = 'translateY(-1px)'; }}
                onMouseLeave={e => { e.currentTarget.style.boxShadow = '0 2px 8px rgba(0,0,0,0.06)'; e.currentTarget.style.transform = 'translateY(0)'; }}
                onClick={() => document.getElementById(`section-${s.id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })}>
                <s.icon size={14} style={{ color: s.color }} />{s.title}
              </button>
            ))}
          </div>
        )}

        {/* Sections */}
        {SECTIONS.map(s => (
          <div key={s.id} id={`section-${s.id}`}>
            <HelpSection section={s} searchTerm={search} defaultOpen={!!search} />
          </div>
        ))}

        {/* No results */}
        {search && matchCount === 0 && (
          <div className="text-center py-5 rounded-4" style={{ background: '#fff', boxShadow: '0 2px 12px rgba(0,0,0,0.06)' }}>
            <FiSearch size={36} style={{ opacity: 0.15 }} className="mb-3 d-block mx-auto" />
            <p className="text-muted">"{search}" ile eşleşen sonuç bulunamadı.</p>
            <button className="btn btn-sm btn-outline-primary" onClick={() => setSearch('')}>Aramayı Temizle</button>
          </div>
        )}

        {/* Footer */}
        <div className="text-center py-4 mt-4 rounded-4" style={{ background: '#fff', boxShadow: '0 2px 12px rgba(0,0,0,0.04)' }}>
          <FiHelpCircle size={20} className="text-muted mb-2 d-block mx-auto" style={{ opacity: 0.4 }} />
          <p className="text-muted small mb-1">Aradığınız cevabı bulamadınız mı?</p>
          <p className="text-muted small mb-0">Yazılım geliştiricinizle iletişime geçebilirsiniz.</p>
        </div>
      </div>
    </div>
  );
}
