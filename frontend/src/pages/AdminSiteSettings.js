import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import axios from 'axios';
import useSecurityCodePrompt from '../components/useSecurityCodePrompt';
import { useAdminToast } from '../components/AdminToast';

// ─────────────────────────────────────────────────────────────
// Setting groups organized into sections
// ─────────────────────────────────────────────────────────────
const SETTING_GROUPS = [
  { id: 'storestatus', section: 'Mağaza',        title: 'Satış Durumu (Test Modu)', icon: 'fas fa-store-alt-slash',      keys: ['store_purchasing_enabled', 'store_test_mode_banner'],
    tooltip: 'Mağazanın satışa açık olup olmadığını kontrol eder. Test/bakım aşamasında kapatın — site görünür kalır ama sipariş alınmaz.' },
  { id: 'brand',       section: 'Mağaza',        title: 'Marka & Görünüm',         icon: 'fas fa-palette',               keys: ['site_name', 'site_logo_url', 'site_favicon_url', 'primary_color', 'secondary_color'],
    tooltip: 'Mağaza header\'ı, tarayıcı sekmesi ve genel renk temasında kullanılır.' },
  { id: 'contact',     section: 'Mağaza',        title: 'İletişim Bilgileri',      icon: 'fas fa-phone',                 keys: ['contact_phone', 'contact_email', 'contact_address', 'contact_map_embed'],
    tooltip: 'Footer, iletişim sayfası ve e-posta bildirimlerinde gösterilir.' },
  { id: 'social',      section: 'Mağaza',        title: 'Sosyal Medya',            icon: 'fas fa-share-alt',             keys: ['social_instagram', 'social_facebook', 'social_whatsapp'],
    tooltip: 'Footer ve iletişim sayfasında ikon olarak görünür.' },
  { id: 'legal',       section: 'Mağaza',        title: 'Kurumsal / Yasal',        icon: 'fas fa-building',              keys: ['company_legal_name', 'mersis_number', 'kep_address', 'tax_office', 'tax_number', 'trade_registry_number', 'chamber_of_commerce', 'etbis_qr_url'],
    tooltip: 'Footer\'da ve yasal sayfalarda gösterilir. Canlıya almadan önce doldurulmalıdır.' },
  { id: 'notices',     section: 'Mağaza',        title: 'Beyaz Eşya Uyarıları',    icon: 'fas fa-exclamation-triangle',  keys: ['warranty_notice_text', 'bulky_shipping_notice_text'],
    tooltip: 'Ürün detay ve ödeme sayfalarında gösterilecek yasal uyarı metinleri.' },
  { id: 'currency',    section: 'Mağaza',        title: 'Para Birimi',             icon: 'fas fa-coins',                 keys: ['currency_symbol'],
    tooltip: 'Mağazada ve faturalarda kullanılacak para birimi.' },
  { id: 'general',     section: 'Mağaza',        title: 'Genel',                   icon: 'fas fa-cog',                   keys: ['footer_text', 'header_announcement', 'contact_form_email'],
    tooltip: 'Footer alt bilgisi, üst bar duyuru mesajı ve form yönlendirme.' },
  { id: 'shipping',    section: 'Mağaza',        title: 'Kargo Ücretlendirme',     icon: 'fas fa-truck-loading',         keys: ['free_shipping_threshold', 'default_shipping_cost'],
    tooltip: 'Ücretsiz kargo eşiği ve varsayılan kargo ücreti. Provider tabanlı detaylar Kargo Sağlayıcıları sayfasında.' },
  { id: 'abandon',     section: 'Pazarlama',     title: 'Sepet Kurtarma',          icon: 'fas fa-shopping-cart',         keys: ['abandoned_cart_enabled', 'abandoned_cart_delay_hours', 'abandoned_cart_reminder_window_hours'],
    tooltip: 'Terk edilmiş sepetler için otomatik hatırlatma e-postası ayarları.' },
  { id: 'seo',         section: 'Pazarlama',     title: 'SEO & Analytics',         icon: 'fas fa-search',                keys: ['seo_canonical_domain', 'seo_organization_name', 'seo_default_meta_description', 'seo_default_og_image', 'seo_twitter_handle', 'analytics_google_id', 'analytics_facebook_pixel_id', 'analytics_hotjar_id', 'analytics_clarity_id'],
    tooltip: 'Arama motoru optimizasyonu ve web analytics ayarları. Ürün/kategori sayfalarında kullanılır.' },
  { id: 'sms',         section: 'Entegrasyonlar',title: 'SMS Bildirimleri',        icon: 'fas fa-sms',                   keys: ['sms_enabled', 'sms_provider', 'netgsm_username', 'netgsm_password', 'netgsm_sender'],
    tooltip: 'SMS sağlayıcı yapılandırması. Sipariş ve kargo bildirimleri için kullanılır.',
    enabledKey: 'sms_enabled' },
  { id: 'cargo',       section: 'Entegrasyonlar',title: 'Kargo API',               icon: 'fas fa-truck',                 keys: ['cargo_api_enabled', 'cargo_api_provider', 'cargo_api_auto_create', 'kargonomi_api_token', 'kargonomi_app_key', 'kargonomi_api_base_url', 'kargonomi_webhook_secret', 'kargonomi_warehouse_id'],
    tooltip: 'Kargo gönderi oluşturma ve takip API entegrasyonu. Aktif edince sipariş SHIPPED olunca otomatik kargo oluşturulur.',
    enabledKey: 'cargo_api_enabled' },
  { id: 'sender',      section: 'Entegrasyonlar',title: 'Gönderici Bilgileri',     icon: 'fas fa-store',                 keys: ['sender_name', 'sender_phone', 'sender_address', 'sender_city', 'sender_district', 'sender_postal_code'],
    tooltip: 'Kargo gönderisinde "gönderici" olarak gösterilecek mağaza bilgileri.' },
  { id: 'invoice',     section: 'E-Fatura',      title: 'E-Fatura (Logo)',         icon: 'fas fa-file-invoice',          keys: ['invoice_provider', 'invoice_auto_generate', 'logo_efatura_endpoint', 'logo_efatura_username', 'logo_efatura_password', 'logo_efatura_test_mode', 'logo_customer_alias', 'logo_earsiv_design_file', 'logo_efatura_design_file', 'invoice_admin_digest_email'],
    tooltip: 'Logo eLogo SOAP API ile e-Fatura / e-Arşiv entegrasyonu. Sipariş PAID olunca otomatik fatura oluşturulur.',
    enabledKey: 'invoice_auto_generate' },
  { id: 'inv-company', section: 'E-Fatura',      title: 'Satıcı Firma',            icon: 'fas fa-building',              keys: ['logo_company_vkn', 'logo_company_title', 'logo_company_tax_office', 'logo_company_mersis_no', 'logo_company_trade_registry', 'logo_company_address', 'logo_company_city', 'logo_company_district', 'logo_company_postal_code', 'logo_company_email', 'logo_company_phone', 'logo_company_website', 'logo_company_bank_name', 'logo_company_bank_iban'],
    tooltip: 'UBL-TR faturalara yazılacak satıcı firma bilgileri. MERSİS, vergi no, adres vb.' },
];

const LABELS = {
  site_name: 'Site Adı', site_logo_url: 'Logo', site_favicon_url: 'Favicon',
  primary_color: 'Ana Renk', secondary_color: 'İkincil Renk',
  contact_phone: 'Telefon', contact_email: 'E-posta', contact_address: 'Adres',
  contact_map_embed: 'Google Maps Embed URL', social_instagram: 'Instagram Profil URL', social_facebook: 'Facebook Sayfa URL', social_whatsapp: 'WhatsApp Numarası',
  company_legal_name: 'Ticari Unvan', mersis_number: 'MERSİS Numarası', kep_address: 'KEP Adresi',
  tax_office: 'Vergi Dairesi', tax_number: 'Vergi No', trade_registry_number: 'Ticaret Sicil No',
  chamber_of_commerce: 'Ticaret/Sanayi Odası', etbis_qr_url: 'ETBİS QR Kod URL',
  warranty_notice_text: 'Garanti Uyarı Metni', bulky_shipping_notice_text: 'Hacimli Ürün Kargo Uyarısı',
  footer_text: 'Alt Bilgi Metni', currency_symbol: 'Para Birimi',
  store_purchasing_enabled: 'Satışa Açık', store_test_mode_banner: 'Test Modu Banner Metni',
  free_shipping_threshold: 'Ücretsiz Kargo Limiti (TL)', default_shipping_cost: 'Varsayılan Kargo Ücreti (TL)',
  header_announcement: 'Üst Banner Duyuru Mesajı', contact_form_email: 'İletişim Formu Hedef E-posta',
  payment_method_credit_card_enabled: 'Kredi Kartı ile Ödeme', payment_method_bank_transfer_enabled: 'Havale / EFT ile Ödeme', payment_method_door_cash_enabled: 'Kapıda Ödeme',
  abandoned_cart_enabled: 'Terk Edilmiş Sepet E-postası', abandoned_cart_delay_hours: 'Bekleme Süresi (saat)', abandoned_cart_reminder_window_hours: 'Hatırlatma Penceresi (saat)',
  sms_enabled: 'SMS Gönderimi', sms_provider: 'SMS Sağlayıcı', netgsm_username: 'Netgsm Kullanıcı Adı', netgsm_password: 'Netgsm Şifre', netgsm_sender: 'Netgsm Gönderici Başlık',
  seo_canonical_domain: 'Canonical Domain', seo_organization_name: 'Kuruluş Adı', seo_default_meta_description: 'Varsayılan Meta Açıklama',
  seo_default_og_image: 'Varsayılan Paylaşım Görseli (OG Image)', seo_twitter_handle: 'Twitter / X Hesabı',
  analytics_google_id: 'Google Analytics 4 ID', analytics_facebook_pixel_id: 'Facebook Pixel ID',
  analytics_hotjar_id: 'Hotjar Site ID', analytics_clarity_id: 'Microsoft Clarity Project ID',
  cargo_api_enabled: 'Kargo API Entegrasyonu', cargo_api_provider: 'Kargo API Sağlayıcı',
  cargo_api_auto_create: 'Otomatik Kargo Oluştur', kargonomi_api_token: 'Kargonomi API Token (Bearer)',
  kargonomi_app_key: 'Kargonomi APP KEY', kargonomi_api_base_url: 'Kargonomi API URL',
  kargonomi_webhook_secret: 'Kargonomi Webhook Secret',
  kargonomi_warehouse_id: 'Kargonomi Warehouse ID',
  sender_name: 'Gönderici Adı / Firma', sender_phone: 'Gönderici Telefon', sender_address: 'Gönderici Adresi',
  sender_city: 'Gönderici Şehir', sender_district: 'Gönderici İlçe', sender_postal_code: 'Gönderici Posta Kodu',
  invoice_provider: 'E-Fatura Sağlayıcı', invoice_auto_generate: 'Otomatik Fatura Oluştur',
  logo_efatura_endpoint: 'Logo SOAP Endpoint URL', logo_efatura_username: 'Logo Kullanıcı Adı',
  logo_efatura_password: 'Logo Şifre', logo_efatura_test_mode: 'Test Modu',
  logo_customer_alias: 'Müşteri Alias (tüzel)',
  logo_earsiv_design_file: 'e-Arşiv Tasarım Dosyası', logo_efatura_design_file: 'e-Fatura Tasarım Dosyası',
  invoice_admin_digest_email: 'Günlük Fatura Özeti E-posta',
  logo_company_vkn: 'Vergi / TC Kimlik No', logo_company_title: 'Ticari Unvan', logo_company_tax_office: 'Vergi Dairesi',
  logo_company_mersis_no: 'MERSİS No', logo_company_trade_registry: 'Ticaret Sicil No',
  logo_company_address: 'Firma Adresi', logo_company_city: 'Şehir', logo_company_district: 'İlçe',
  logo_company_postal_code: 'Posta Kodu', logo_company_email: 'Firma E-posta',
  logo_company_phone: 'Firma Telefon', logo_company_website: 'Web Sitesi',
  logo_company_bank_name: 'Banka Adı', logo_company_bank_iban: 'IBAN',
};

const FIELD_TOOLTIPS = {
  site_name: 'Tarayıcı sekmesinde ve mağaza başlığında görünür',
  site_logo_url: 'Mağaza header\'ında ve faturalarda kullanılır',
  site_favicon_url: 'Tarayıcı sekmesinde site adının yanında görünen küçük ikon',
  primary_color: 'Butonlar, linkler ve vurgularda kullanılır',
  secondary_color: 'İkincil butonlar ve arka plan tonlarında',
  contact_map_embed: 'Google Maps → Paylaş → Yerleştir → src URL\'sini yapıştırın',
  header_announcement: 'Mağaza üst barında kayan kampanya mesajı olarak gösterilir',
  social_whatsapp: 'Ülke kodu ile (ör: 905551234567)',
  free_shipping_threshold: 'Bu tutarın üzerindeki siparişlerde kargo ücretsiz olur',
  default_shipping_cost: 'Ücretsiz kargo limitinin altındaki siparişlere uygulanır',
  contact_form_email: 'İletişim formundan gelen mesajlar bu adrese yönlendirilir',
  company_legal_name: 'Şirketin tam resmi ticari unvanı',
  mersis_number: 'Ticaret Sicil Gazetesi\'nden edinilebilir',
  kep_address: 'Yasal tebligatlar için kayıtlı elektronik posta',
  tax_office: 'Bağlı bulunulan vergi dairesi',
  tax_number: 'Vergi kimlik numarası',
  trade_registry_number: 'Ticaret sicil numarası',
  chamber_of_commerce: 'Kayıtlı olduğunuz ticaret veya sanayi odası',
  etbis_qr_url: 'ETBİS karekodunun public erişilebilir URL\'si (e-Devlet üzerinden alınır)',
  warranty_notice_text: 'Beyaz eşya ürünlerinde garanti koşulları uyarısı (ürün detay sayfasında gösterilir)',
  bulky_shipping_notice_text: 'Hacimli ürünlerin kargo/teslimat süresi uyarısı (checkout\'ta gösterilir)',
  currency_symbol: 'Mağazada ve faturalarda kullanılacak para birimi',
  store_purchasing_enabled: 'AÇIK = müşteriler sipariş verebilir (canlı satış). KAPALI = test/bakım modu: site açık kalır, ürünler görünür ama "Sepete Ekle/Sipariş" engellenir ve üstte test banner gösterilir. Yeni sürüm testinde veya bakımda kapatın.',
  store_test_mode_banner: 'Test modunda (satış kapalıyken) sitenin üstünde gösterilecek uyarı metni. Boş bırakılırsa varsayılan metin kullanılır.',
  payment_method_credit_card_enabled: 'Kapatırsanız kredi kartı seçeneği müşterilere gösterilmez',
  payment_method_bank_transfer_enabled: 'Kapatırsanız havale/EFT seçeneği gösterilmez. IBAN yapılandırılmamışsa otomatik gizlenir.',
  payment_method_door_cash_enabled: 'Kapatırsanız kapıda ödeme seçeneği gösterilmez',
  abandoned_cart_enabled: 'Kapattığınızda terk edilmiş sepetler için hatırlatma e-postası gönderilmez. Sadece marketing onayı olan müşterilere gönderilir.',
  abandoned_cart_delay_hours: 'Sepet son güncellendikten kaç saat sonra hatırlatma gönderilsin (önerilen: 2)',
  abandoned_cart_reminder_window_hours: 'Bu süreden eski sepetlere hatırlatma gönderilmez (önerilen: 72 saat)',
  sms_enabled: 'Sipariş onay, kargo bilgileri gibi bildirimler SMS olarak da gönderilir. Aktif etmek için bir SMS sağlayıcısı seçin ve bilgilerini girin.',
  sms_provider: 'Kullanılacak SMS API sağlayıcısı',
  netgsm_username: 'Netgsm kullanıcı kodunuz',
  netgsm_password: 'Netgsm API şifreniz',
  netgsm_sender: 'Netgsm panelinde onaylanmış gönderici başlık (örn: MAGAZAM, BILGI)',
  seo_canonical_domain: 'Canonical URL\'lerde kullanılacak ana domain (örn: https://magazam.com). Boşsa istek geldiği domain kullanılır.',
  seo_organization_name: 'Schema.org Organization ismi. Boşsa site adı kullanılır.',
  seo_default_meta_description: 'Özel açıklaması olmayan sayfalarda kullanılacak varsayılan meta açıklaması (150-160 karakter)',
  seo_default_og_image: 'Sosyal medya paylaşımlarında kullanılacak varsayılan görsel (1200x630px önerilir)',
  seo_twitter_handle: '@magazam formatında. Twitter Card\'da site sahibi olarak görünür',
  analytics_google_id: 'Google Analytics 4 ölçüm kimliği (G-XXXXXXXX formatında). Boşsa izleme devre dışı.',
  analytics_facebook_pixel_id: 'Facebook Pixel kimliği (sayısal). Boşsa izleme devre dışı.',
  cargo_api_enabled: 'Kapattığınızda manuel kargo takip kodu girişi ile devam eder. Aktifse sipariş SHIPPED olunca otomatik kargo oluşturulur.',
  cargo_api_provider: 'Kullanılacak kargo API entegrasyonu',
  cargo_api_auto_create: 'SHIPPED durumuna geçen siparişler için otomatik kargo oluşturulsun mu?',
  kargonomi_api_token: 'Kargonomi hesabınızdan alınan Bearer token',
  kargonomi_app_key: 'Kargonomi APP KEY (partner uygulaması için)',
  kargonomi_api_base_url: 'Kargonomi API URL (varsayılan: https://app.kargonomi.com.tr/api/v1)',
  kargonomi_webhook_secret: 'Kargonomi webhook POST\'larının HMAC-SHA256 imzasını doğrulamak için paylaşılan secret. Kargonomi panelinde webhook oluştururken aynı değeri girin.',
  kargonomi_warehouse_id: 'Kargonomi\'de oluşturulan gönderici depo ID\'si. Boşsa her shipment\'ta "Gönderici Bilgileri" kullanılır. Admin → Kargo API → "Webhook Kaydet" butonu ile de senkronize edebilirsiniz.',
  sender_name: 'Faturada ve kargoda "gönderici" olarak gözükecek firma adı',
  sender_phone: 'Kargo iletişimi için telefon',
  sender_address: 'Kargo çıkış adresi',
  sender_city: 'Kargo çıkış şehri',
  sender_district: 'Kargo çıkış ilçesi',
  sender_postal_code: 'Kargo çıkış posta kodu',
  invoice_provider: 'Kullanılacak e-Fatura entegrasyon sağlayıcısı',
  invoice_auto_generate: 'Sipariş PAID olunca otomatik olarak fatura oluşturulup GİB\'e gönderilsin mi?',
  logo_efatura_endpoint: 'Logo eLogo SOAP servis URL\'i. Boş bırakırsanız test/prod moduna göre default kullanılır (pb-demo.elogo.com.tr veya pb.elogo.com.tr).',
  logo_efatura_username: 'Logo eLogo kullanıcı adınız',
  logo_efatura_password: 'Logo eLogo API şifreniz',
  logo_efatura_test_mode: 'Açık: https://pb-demo.elogo.com.tr/PostboxService.svc kullanılır. Kapalı: production endpoint (pb.elogo.com.tr).',
  logo_customer_alias: 'Tüzel müşterilerin e-Fatura alias\'ı (opsiyonel). Boş bırakılırsa Logo panelindeki varsayılan kullanılır.',
  logo_earsiv_design_file: 'e-Arşiv tasarım dosyası adı (Logo panelinden alınır, bireysel müşteri faturalarında kullanılır)',
  logo_efatura_design_file: 'e-Fatura tasarım dosyası adı (Logo panelinden alınır, tüzel müşteri faturalarında kullanılır)',
  invoice_admin_digest_email: 'Her gün 08:00\'da hatalı ve 24h+ beklemede kalan faturaların özeti bu adrese gönderilir. Boşsa "İletişim Formu Hedef E-posta" kullanılır.',
  logo_company_vkn: 'Şahıs işletmesi ise 11 haneli TC Kimlik No; tüzel kişi ise 10 haneli Vergi No',
  logo_company_title: 'Firmanın resmi ticari unvanı (Vergi levhanızdaki tam isim)',
  logo_company_tax_office: 'Kayıtlı olduğunuz vergi dairesi (örn: Ankara Kurumlar Vergi Dairesi)',
  logo_company_mersis_no: 'MERSİS (Merkezi Sicil Kayıt Sistemi) numaranız - 16 haneli',
  logo_company_trade_registry: 'Ticaret sicil numaranız',
  logo_company_address: 'Vergi levhanızdaki adres',
  logo_company_city: 'İl',
  logo_company_district: 'İlçe',
  logo_company_postal_code: '5 haneli posta kodu',
  logo_company_email: 'Fatura iletişimi için firma e-postası',
  logo_company_phone: 'Firma iletişim telefonu',
  logo_company_website: 'Firma web sitesi (opsiyonel)',
  logo_company_bank_name: 'Havale/EFT alacağınız bankanın adı',
  logo_company_bank_iban: 'TR ile başlayan 26 haneli IBAN',
};

const CURRENCY_OPTIONS = [
  { value: '₺', label: 'TL (₺)' },
  { value: '$', label: 'USD ($)' },
  { value: '€', label: 'EUR (€)' },
  { value: '£', label: 'GBP (£)' },
];

// ─────────────────────────────────────────────────────────────
// Light-weight validation helpers
// ─────────────────────────────────────────────────────────────
const validateField = (key, value) => {
  if (!value) return null;
  const v = String(value).trim();
  if (key === 'logo_company_vkn') {
    if (!/^\d+$/.test(v)) return { type: 'warning', msg: 'Sadece rakam olmalı' };
    if (v.length !== 10 && v.length !== 11) return { type: 'warning', msg: 'VKN 10, TC Kimlik No 11 haneli olmalı' };
  }
  if (key === 'logo_company_mersis_no' || key === 'mersis_number') {
    if (!/^\d+$/.test(v)) return { type: 'warning', msg: 'Sadece rakam olmalı' };
    if (v.length !== 16) return { type: 'warning', msg: 'MERSİS 16 haneli olmalı' };
  }
  if (key === 'logo_company_bank_iban') {
    const ibanClean = v.replace(/\s/g, '').toUpperCase();
    if (!/^TR\d{24}$/.test(ibanClean)) return { type: 'warning', msg: 'Geçerli TR IBAN formatı: TR + 24 rakam' };
  }
  if (key === 'logo_company_postal_code' || key === 'sender_postal_code') {
    if (!/^\d{5}$/.test(v)) return { type: 'warning', msg: '5 haneli posta kodu' };
  }
  if (key.includes('email') && !key.includes('embed')) {
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v)) return { type: 'warning', msg: 'Geçerli e-posta değil' };
  }
  if (key === 'analytics_google_id' && !/^G-[A-Z0-9]+$/i.test(v)) {
    return { type: 'warning', msg: 'G-XXXXXXXX formatında olmalı' };
  }
  if (key === 'seo_twitter_handle' && !v.startsWith('@')) {
    return { type: 'info', msg: '@ ile başlamalı' };
  }
  if (key === 'social_whatsapp' && !/^\d{10,15}$/.test(v.replace(/\D/g, ''))) {
    return { type: 'warning', msg: 'Ülke kodu ile (ör: 905551234567)' };
  }
  return null;
};

export default function AdminSiteSettings() {
  const [settings, setSettings] = useState({});
  const [originalSettings, setOriginalSettings] = useState({});
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(true);
  const [activeGroupId, setActiveGroupId] = useState(SETTING_GROUPS[0].id);
  const [logoUploading, setLogoUploading] = useState(false);
  const [faviconUploading, setFaviconUploading] = useState(false);
  const [dragOver, setDragOver] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [visiblePasswords, setVisiblePasswords] = useState({});
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const fileInputRef = useRef(null);
  const faviconInputRef = useRef(null);
  const { askCode, SecurityCodePrompt } = useSecurityCodePrompt();
  const toast = useAdminToast();

  // Load settings
  useEffect(() => {
    setLoading(true);
    axios.get('/api/admin/settings/site').then(r => {
      const map = {}; (r.data || []).forEach(s => { map[s.settingKey] = s.settingValue; });
      setSettings(map);
      setOriginalSettings(map);
    }).catch(() => {}).finally(() => setLoading(false));
  }, []);

  // Dirty tracking
  const dirtyKeys = useMemo(() => {
    const keys = new Set();
    const all = new Set([...Object.keys(settings), ...Object.keys(originalSettings)]);
    all.forEach(k => {
      if ((settings[k] || '') !== (originalSettings[k] || '')) keys.add(k);
    });
    return keys;
  }, [settings, originalSettings]);
  const isDirty = dirtyKeys.size > 0;

  // Beforeunload warning
  useEffect(() => {
    if (!isDirty) return;
    const handler = (e) => { e.preventDefault(); e.returnValue = ''; };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [isDirty]);

  const handleChange = (key, value) => setSettings(s => ({ ...s, [key]: value }));

  const withSecurityCode = async (desc, fn) => {
    const code = await askCode({ description: desc });
    if (!code) return;
    try { await fn(code); } catch (e) {
      const status = e.response?.status;
      const backendMsg = e.response?.data?.message
        || e.response?.data?.error
        || (typeof e.response?.data === 'string' ? e.response.data : null);

      // 403: security code, 400: validation, 401: session, 500: server, no response: network
      if (status === 403) {
        toast.error('Güvenlik şifresi hatalı.');
      } else if (status === 401) {
        toast.error('Oturumunuz sona ermiş. Lütfen tekrar giriş yapın.');
      } else if (status === 400 || status === 422) {
        toast.error(backendMsg || 'Geçersiz değer. Lütfen alanları kontrol edin.');
      } else if (status >= 500) {
        toast.error(backendMsg || `Sunucu hatası (${status}). Loglara bakın.`);
      } else if (!e.response) {
        // Network error — server did not respond
        toast.error('Sunucuya ulaşılamadı. Bağlantınızı kontrol edin.');
      } else {
        toast.error(backendMsg || `Hata (${status || 'unknown'})`);
      }
      // Log the full error to the console for developers
      // eslint-disable-next-line no-console
      console.error('[AdminSiteSettings] Save failed:', { status, message: backendMsg, error: e });
    }
  };

  const handleSave = useCallback(() => withSecurityCode('Site ayarlarını kaydetmek için güvenlik şifresini girin.', async (code) => {
    setSaving(true);
    try {
      await axios.put('/api/admin/settings/site', settings, { headers: { 'X-ADMIN-SECURITY-CODE': code } });
      setOriginalSettings(settings);
      toast.success('Ayarlar kaydedildi.');
    } catch (e) { throw e; }
    finally { setSaving(false); }
  }), [settings]); // eslint-disable-line

  const handleDiscard = () => {
    if (!window.confirm('Kaydedilmemiş değişiklikler silinecek. Emin misiniz?')) return;
    setSettings(originalSettings);
    toast.info('Değişiklikler geri alındı.');
  };

  // Ctrl+S to save
  useEffect(() => {
    const onKey = (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault();
        if (isDirty && !saving) handleSave();
      }
      if (e.key === 'Escape' && mobileMenuOpen) setMobileMenuOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [isDirty, saving, handleSave, mobileMenuOpen]);

  const handleFileUpload = async (type, file) => {
    if (!file) return;
    const isLogo = type === 'logo';
    const maxSize = isLogo ? 10 : 5;
    if (!file.type.startsWith('image/') && file.type !== 'application/octet-stream') { toast.error('Lütfen bir görsel dosyası seçin.'); return; }
    if (file.size > maxSize * 1024 * 1024) { toast.error('Dosya boyutu çok büyük.'); return; }
    const code = await askCode({ description: `${isLogo ? 'Logo' : 'Favicon'} yüklemek için güvenlik şifresini girin.` });
    if (!code) return;
    isLogo ? setLogoUploading(true) : setFaviconUploading(true);
    try {
      const fd = new FormData(); fd.append('file', file);
      const res = await axios.post(`/api/admin/settings/site/${type}`, fd, {
        headers: { 'Content-Type': 'multipart/form-data', 'X-ADMIN-SECURITY-CODE': code }
      });
      const newUrl = res.data.url;
      const settingKey = isLogo ? 'site_logo_url' : 'site_favicon_url';
      setSettings(s => ({ ...s, [settingKey]: newUrl }));
      setOriginalSettings(s => ({ ...s, [settingKey]: newUrl }));
      if (!isLogo && newUrl) {
        document.querySelectorAll("link[rel*='icon']").forEach(el => el.remove());
        const link = document.createElement('link'); link.rel = 'icon';
        link.href = newUrl + '?v=' + Date.now();
        document.head.appendChild(link);
      }
      toast.success(`${isLogo ? 'Logo' : 'Favicon'} başarıyla yüklendi!`);
    } catch (e) {
      if (e.response?.status === 403) toast.error('Güvenlik şifresi hatalı.');
      else toast.error('İşlem başarısız.');
    } finally { isLogo ? setLogoUploading(false) : setFaviconUploading(false); }
  };

  // Computed: per-group completion & dirty stats
  const groupStats = useMemo(() => {
    const stats = {};
    SETTING_GROUPS.forEach(g => {
      const total = g.keys.length;
      const filled = g.keys.filter(k => {
        const v = settings[k];
        return v !== undefined && v !== null && String(v).trim() !== '';
      }).length;
      const dirty = g.keys.filter(k => dirtyKeys.has(k)).length;
      const enabled = g.enabledKey ? settings[g.enabledKey] === 'true' : null;
      stats[g.id] = { total, filled, dirty, enabled };
    });
    return stats;
  }, [settings, dirtyKeys]);

  // Total completion
  const totalStats = useMemo(() => {
    const allKeys = SETTING_GROUPS.flatMap(g => g.keys);
    const filled = allKeys.filter(k => {
      const v = settings[k];
      return v !== undefined && v !== null && String(v).trim() !== '';
    }).length;
    return { total: allKeys.length, filled, percent: Math.round((filled / allKeys.length) * 100) };
  }, [settings]);

  // Search filter — matches labels, keys, group titles
  const searchResults = useMemo(() => {
    const q = searchTerm.trim().toLowerCase();
    if (!q) return null;
    return SETTING_GROUPS.map(g => {
      const matchedKeys = g.keys.filter(k => {
        const label = (LABELS[k] || '').toLowerCase();
        const tooltip = (FIELD_TOOLTIPS[k] || '').toLowerCase();
        return k.toLowerCase().includes(q) || label.includes(q) || tooltip.includes(q) || g.title.toLowerCase().includes(q);
      });
      return { group: g, matchedKeys };
    }).filter(r => r.matchedKeys.length > 0);
  }, [searchTerm]);

  // Group by section for sidebar
  const sectionedGroups = useMemo(() => {
    const sections = {};
    SETTING_GROUPS.forEach(g => {
      if (!sections[g.section]) sections[g.section] = [];
      sections[g.section].push(g);
    });
    return sections;
  }, []);

  const activeGroup = SETTING_GROUPS.find(g => g.id === activeGroupId) || SETTING_GROUPS[0];

  // ===== Reusable upload zone component =====
  const UploadZone = ({ type, label, currentUrl, uploading, inputRef, formats, sizeHint }) => {
    const hasFile = currentUrl && currentUrl.length > 1;
    const isLogo = type === 'logo';
    return (
      <div className="col-12" key={`upload-${type}`}>
        <FieldLabel label={label} tooltip={FIELD_TOOLTIPS[isLogo ? 'site_logo_url' : 'site_favicon_url']} />
        <div className="border rounded overflow-hidden">
          {hasFile && (
            <div className="d-flex align-items-center gap-3 p-3 bg-light border-bottom">
              <div className={`bg-white border rounded d-flex align-items-center justify-content-center ${isLogo ? 'p-2' : 'p-1'}`}
                style={isLogo ? {minWidth:80, minHeight:48} : {width:44, height:44}}>
                <img src={currentUrl.startsWith('/') ? currentUrl + '?t=' + Date.now() : currentUrl}
                  alt={label} style={isLogo ? {maxHeight:40, maxWidth:100} : {width:28, height:28}}
                  onError={e => { e.target.style.display='none'; }} />
              </div>
              <div className="flex-grow-1 min-w-0">
                <div className="small fw-semibold text-dark">Mevcut {label}</div>
                <div className="text-muted small text-truncate">{currentUrl}</div>
              </div>
              <button className="btn btn-outline-primary btn-sm text-nowrap" onClick={() => inputRef.current?.click()}>
                <i className="fas fa-pen me-1" />Değiştir
              </button>
            </div>
          )}
          <div
            className={`p-3 text-center ${dragOver === type ? 'bg-primary bg-opacity-10 border-primary' : ''}`}
            onDragOver={e => { e.preventDefault(); setDragOver(type); }}
            onDragLeave={() => setDragOver('')}
            onDrop={e => { e.preventDefault(); setDragOver(''); handleFileUpload(type, e.dataTransfer.files[0]); }}
            style={{ cursor: 'pointer', transition: 'all .15s' }}
            onClick={() => inputRef.current?.click()}
          >
            <input type="file" ref={inputRef} className="d-none" accept="image/*,.ico,.svg"
              onChange={e => handleFileUpload(type, e.target.files[0])} />
            {uploading ? (
              <div className="py-2"><span className="spinner-border spinner-border-sm text-primary me-2" />Yükleniyor...</div>
            ) : (
              <div className="py-1">
                <div className="d-flex align-items-center justify-content-center gap-2 text-muted">
                  <i className={`fas ${isLogo ? 'fa-image' : 'fa-globe'}`} />
                  <span className="small">Dosya sürükleyin veya <span className="text-primary fw-medium">seçin</span></span>
                </div>
                <div className="text-muted small mt-1">{formats} — {sizeHint}</div>
              </div>
            )}
          </div>
        </div>
      </div>
    );
  };

  // ===== Field renderer =====
  const renderField = (key) => {
    const isDirtyField = dirtyKeys.has(key);
    const validation = validateField(key, settings[key]);

    if (key === 'site_logo_url') {
      return <UploadZone key={key} type="logo" label="Logo" currentUrl={settings[key]} uploading={logoUploading}
        inputRef={fileInputRef} formats="PNG, JPG, SVG, WebP" sizeHint="Maks. 10MB" />;
    }
    if (key === 'site_favicon_url') {
      return <UploadZone key={key} type="favicon" label="Favicon" currentUrl={settings[key]} uploading={faviconUploading}
        inputRef={faviconInputRef} formats="ICO, PNG, SVG" sizeHint="32×32px önerilir" />;
    }

    // Dropdowns
    if (key === 'sms_provider') return renderSelect(key, [{v:'MOCK',l:'Mock (Test / Geliştirme)'},{v:'NETGSM',l:'Netgsm'}], 'MOCK', isDirtyField, validation);
    if (key === 'cargo_api_provider') return renderSelect(key, [{v:'MOCK',l:'Mock (Test / Geliştirme)'},{v:'KARGONOMI',l:'Kargonomi (multi-carrier)'}], 'MOCK', isDirtyField, validation);
    if (key === 'invoice_provider') return renderSelect(key, [{v:'MOCK',l:'Mock (Test / Geliştirme)'},{v:'LOGO',l:'Logo eLogo (e-Fatura / e-Arşiv)'}], 'MOCK', isDirtyField, validation);
    if (key === 'currency_symbol') return renderSelect(key, CURRENCY_OPTIONS.map(c => ({v:c.value, l:c.label})), '₺', isDirtyField, validation);

    // Password-style fields
    if (['kargonomi_api_token','kargonomi_app_key','kargonomi_webhook_secret','logo_efatura_password','netgsm_password'].includes(key)) {
      const visible = !!visiblePasswords[key];
      return (
        <div key={key} className="col-md-6">
          <FieldLabel label={LABELS[key]} tooltip={FIELD_TOOLTIPS[key]} dirty={isDirtyField} />
          <div className="input-group">
            <input type={visible ? 'text' : 'password'} className="form-control"
              value={settings[key] || ''} onChange={e => handleChange(key, e.target.value)}
              placeholder="••••••••" autoComplete="new-password" />
            <button type="button" className="btn btn-outline-secondary" tabIndex={-1}
              onClick={() => setVisiblePasswords(p => ({ ...p, [key]: !p[key] }))}
              title={visible ? 'Gizle' : 'Göster'}>
              <i className={`fas ${visible ? 'fa-eye-slash' : 'fa-eye'}`}></i>
            </button>
          </div>
        </div>
      );
    }

    // Toggles
    if ((key.startsWith('payment_method_') && key.endsWith('_enabled')) || ['store_purchasing_enabled','abandoned_cart_enabled','sms_enabled','cargo_api_enabled','cargo_api_auto_create','invoice_auto_generate','logo_efatura_test_mode'].includes(key)) {
      const defaultFalse = ['sms_enabled','cargo_api_enabled','cargo_api_auto_create','invoice_auto_generate'].includes(key);
      const isOn = defaultFalse ? settings[key] === 'true' : settings[key] !== 'false';
      const colSize = key.startsWith('payment_method_') ? 'col-md-4' : 'col-md-12';
      return (
        <div key={key} className={colSize}>
          <div className={`border rounded p-3 h-100 d-flex align-items-start gap-3 ${isOn ? 'border-success bg-success bg-opacity-10' : ''}`}>
            <div className="form-check form-switch mb-0 mt-1">
              <input className="form-check-input" type="checkbox" checked={isOn}
                onChange={e => handleChange(key, e.target.checked ? 'true' : 'false')} id={key} style={{cursor:'pointer'}} />
            </div>
            <div className="flex-grow-1">
              <label className="form-check-label small fw-semibold d-block" htmlFor={key} style={{cursor:'pointer'}}>
                {LABELS[key]}
                {isDirtyField && <span className="badge bg-warning text-dark ms-2" style={{fontSize:9}}>değişti</span>}
              </label>
              {FIELD_TOOLTIPS[key] && <small className="text-muted d-block mt-1">{FIELD_TOOLTIPS[key]}</small>}
            </div>
          </div>
        </div>
      );
    }

    // Numeric hour settings
    if (key === 'abandoned_cart_delay_hours' || key === 'abandoned_cart_reminder_window_hours') {
      return (
        <div key={key} className="col-md-6">
          <FieldLabel label={LABELS[key]} tooltip={FIELD_TOOLTIPS[key]} dirty={isDirtyField} />
          <div className="input-group">
            <input type="number" min="1" max="720" className="form-control"
              value={settings[key] || ''} onChange={e => handleChange(key, e.target.value)}
              placeholder={key === 'abandoned_cart_delay_hours' ? '2' : '72'} />
            <span className="input-group-text">saat</span>
          </div>
        </div>
      );
    }

    // Color picker
    if (key.includes('color')) {
      return (
        <div key={key} className="col-md-6">
          <FieldLabel label={LABELS[key]} tooltip={FIELD_TOOLTIPS[key]} dirty={isDirtyField} />
          <div className="input-group">
            <input type="color" className="form-control form-control-color border-end-0"
              value={settings[key]||'#000000'} onChange={e => handleChange(key, e.target.value)} style={{width:46, height:38}} />
            <input className="form-control" value={settings[key]||''}
              onChange={e => handleChange(key, e.target.value)} placeholder="#000000" />
          </div>
        </div>
      );
    }

    // Map embed
    if (key === 'contact_map_embed') {
      return (
        <div key={key} className="col-12">
          <FieldLabel label={LABELS[key]} tooltip={FIELD_TOOLTIPS[key]} dirty={isDirtyField} />
          <textarea className="form-control" rows={2} value={settings[key]||''}
            onChange={e => handleChange(key, e.target.value)} placeholder="https://www.google.com/maps/embed?..." />
          {settings[key] && <div className="mt-2 border rounded overflow-hidden" style={{height:180}}>
            <iframe src={settings[key]} style={{width:'100%',height:'100%',border:0}} title="Harita" loading="lazy" /></div>}
        </div>
      );
    }

    // SEO meta description
    if (key === 'seo_default_meta_description') {
      const val = settings[key] || '';
      const len = val.length;
      const optimal = len >= 120 && len <= 160;
      return (
        <div key={key} className="col-12">
          <FieldLabel label={LABELS[key]} tooltip={FIELD_TOOLTIPS[key]} dirty={isDirtyField} />
          <textarea className="form-control" rows={2} maxLength={200} value={val}
            onChange={e => handleChange(key, e.target.value)}
            placeholder="Türkiye'nin en geniş ürün yelpazesine sahip online mağaza..." />
          <div className="d-flex justify-content-between align-items-center mt-1">
            <small className={optimal ? 'text-success' : 'text-muted'}>
              {len} / 160 karakter {optimal && '✓ İdeal'}
            </small>
            <div className="progress" style={{width:120, height:4}}>
              <div className={`progress-bar ${len === 0 ? 'bg-secondary' : len < 120 ? 'bg-warning' : len <= 160 ? 'bg-success' : 'bg-danger'}`}
                style={{width: `${Math.min(100, (len/160)*100)}%`}}></div>
            </div>
          </div>
        </div>
      );
    }

    // OG image
    if (key === 'seo_default_og_image') {
      return (
        <div key={key} className="col-12">
          <FieldLabel label={LABELS[key]} tooltip={FIELD_TOOLTIPS[key]} dirty={isDirtyField} />
          <input className="form-control" value={settings[key] || ''}
            onChange={e => handleChange(key, e.target.value)} placeholder="https://example.com/og-image.jpg" />
          {settings[key] && (
            <div className="mt-2 border rounded overflow-hidden" style={{ maxWidth: 400 }}>
              <img src={settings[key]} alt="OG Preview" style={{ width: '100%', display: 'block' }} onError={(e) => { e.target.style.display = 'none'; }} />
            </div>
          )}
        </div>
      );
    }

    // Textarea fields
    if (key.includes('text') || key.includes('announcement')) {
      return (
        <div key={key} className="col-12">
          <FieldLabel label={LABELS[key]} tooltip={FIELD_TOOLTIPS[key]} dirty={isDirtyField} />
          <textarea className="form-control" rows={2} value={settings[key]||''} onChange={e => handleChange(key, e.target.value)} />
        </div>
      );
    }

    // Default
    const isShort = ['contact_phone', 'contact_email', 'free_shipping_threshold', 'default_shipping_cost', 'social_whatsapp', 'contact_form_email'].includes(key);
    return (
      <div key={key} className={isShort ? 'col-md-6' : 'col-12'}>
        <FieldLabel label={LABELS[key] || key} tooltip={FIELD_TOOLTIPS[key]} dirty={isDirtyField} />
        <input
          className={`form-control ${validation?.type === 'warning' ? 'border-warning' : ''}`}
          value={settings[key]||''}
          onChange={e => handleChange(key, e.target.value)}
        />
        {validation && (
          <small className={`d-block mt-1 ${validation.type === 'warning' ? 'text-warning' : 'text-info'}`}>
            <i className={`fas ${validation.type === 'warning' ? 'fa-exclamation-circle' : 'fa-info-circle'} me-1`}></i>{validation.msg}
          </small>
        )}
      </div>
    );
  };

  // Render select helper
  const renderSelect = (key, options, defaultValue, isDirtyField) => (
    <div key={key} className="col-md-6">
      <FieldLabel label={LABELS[key]} tooltip={FIELD_TOOLTIPS[key]} dirty={isDirtyField} />
      <select className="form-select" value={settings[key] || defaultValue} onChange={e => handleChange(key, e.target.value)}>
        {options.map(o => <option key={o.v} value={o.v}>{o.l}</option>)}
      </select>
    </div>
  );

  // ============================================================
  return (
    <div>
      {SecurityCodePrompt}

      {/* ── Header ── */}
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <div>
          <h2 className="mb-0 d-flex align-items-center gap-2">
            <i className="fas fa-cog text-primary"></i>
            Site Ayarları
            {isDirty && <span className="badge bg-warning text-dark ms-2" style={{fontSize:12}}>
              <i className="fas fa-circle me-1" style={{fontSize:8}}></i>{dirtyKeys.size} değişiklik
            </span>}
          </h2>
          <p className="text-muted small mb-0">Mağaza görünümü, iletişim, ödeme ve entegrasyon yapılandırması</p>
        </div>
        <div className="d-flex gap-2">
          {isDirty && (
            <button className="btn btn-outline-secondary" onClick={handleDiscard} disabled={saving}>
              <i className="fas fa-undo me-1"></i>Geri Al
            </button>
          )}
          <button className="btn btn-primary px-4" onClick={handleSave} disabled={saving || !isDirty} title="Ctrl+S">
            <i className={`fas ${saving ? 'fa-spinner fa-spin' : 'fa-save'} me-2`} />
            {saving ? 'Kaydediliyor...' : 'Kaydet'}
            {!saving && <span className="badge bg-white text-primary ms-2" style={{fontSize:10}}>Ctrl+S</span>}
          </button>
        </div>
      </div>

      {/* ── Legal Compliance Warning (Phase 0 BLOCKER) ──
          Red banner when critical fields like ETBİS QR, KEP, MERSİS, tax number, etc. are empty.
          Must be filled before launch. */}
      {(() => {
        const CRITICAL_LEGAL_KEYS = [
          'company_legal_name', 'mersis_number', 'kep_address',
          'tax_office', 'tax_number', 'etbis_qr_url',
          'contact_phone', 'contact_email', 'contact_address',
        ];
        const missing = CRITICAL_LEGAL_KEYS.filter(k => {
          const v = settings[k];
          return !v || (typeof v === 'string' && !v.trim());
        });
        if (missing.length === 0) return null;
        const LABEL_MAP = {
          company_legal_name: 'Ticari Unvan', mersis_number: 'MERSİS', kep_address: 'KEP Adresi',
          tax_office: 'Vergi Dairesi', tax_number: 'Vergi No', etbis_qr_url: 'ETBİS QR',
          contact_phone: 'Telefon', contact_email: 'E-posta', contact_address: 'Adres',
        };
        return (
          <div className="alert alert-danger d-flex align-items-start gap-2 mb-3" role="alert">
            <i className="fas fa-shield-alt fs-4 mt-1" />
            <div className="flex-grow-1">
              <strong>Yasal uyumluluk eksikliği:</strong> Aşağıdaki alanlar Türkiye e-ticaret mevzuatı (6563 sayılı kanun, ETBİS, KVKK) gereği zorunludur ve henüz doldurulmamıştır.
              Lansman öncesi mutlaka doldurun:
              <div className="mt-2">
                {missing.map(k => (
                  <span key={k} className="badge bg-danger me-1 mb-1">{LABEL_MAP[k] || k}</span>
                ))}
              </div>
              <small className="text-muted d-block mt-2">
                Bu bilgiler footer'da ve müşteri faturalarında otomatik olarak görüntülenir.
              </small>
            </div>
          </div>
        );
      })()}

      {/* ── Overall Completion Bar ── */}
      <div className="card border-0 shadow-sm mb-3">
        <div className="card-body py-2 px-3">
          <div className="d-flex align-items-center justify-content-between mb-1">
            <span className="small">
              <i className="fas fa-chart-line text-primary me-2"></i>
              <strong>Genel Tamamlanma:</strong> <span className="text-primary fw-semibold">{totalStats.percent}%</span>
              <span className="text-muted ms-2">({totalStats.filled}/{totalStats.total} alan doldu)</span>
            </span>
            <span className="small text-muted">
              {totalStats.percent >= 90 ? '🎯 Çok iyi' : totalStats.percent >= 60 ? '👍 İyi gidiyor' : '⚠️ Eksikler var'}
            </span>
          </div>
          <div className="progress" style={{height:6}}>
            <div className={`progress-bar ${totalStats.percent >= 90 ? 'bg-success' : totalStats.percent >= 60 ? 'bg-primary' : 'bg-warning'}`}
              style={{width: `${totalStats.percent}%`, transition: 'width .5s'}}></div>
          </div>
        </div>
      </div>

      {/* ── Search Bar ── */}
      <div className="card border-0 shadow-sm mb-3">
        <div className="card-body py-2 px-3">
          <div className="input-group input-group-sm">
            <span className="input-group-text bg-transparent border-end-0">
              <i className="fas fa-search text-muted"></i>
            </span>
            <input type="text" className="form-control border-start-0" placeholder="Ayar ara (örn: vergi, sms, renk, analytics)..."
              value={searchTerm} onChange={e => setSearchTerm(e.target.value)} />
            {searchTerm && (
              <button className="btn btn-outline-secondary" onClick={() => setSearchTerm('')}>
                <i className="fas fa-times"></i>
              </button>
            )}
          </div>
        </div>
      </div>

      {/* ── Yasal Uyumluluk Durumu ── */}
      <div className="card border-0 shadow-sm mb-4">
        <div className="card-header bg-warning bg-opacity-10 d-flex align-items-center justify-content-between">
          <div><strong><i className="fas fa-gavel me-2"></i>Yasal Uyumluluk Durumu</strong>
            <span className="text-muted small ms-2 d-none d-md-inline">— Canlıya almadan önce tamamlayın</span></div>
          {(() => {
            const checks = [!!settings.company_legal_name, !!settings.mersis_number, !!settings.kep_address, !!settings.tax_office && !!settings.tax_number, !!settings.etbis_qr_url];
            const done = checks.filter(Boolean).length;
            return <span className={`badge bg-${done === checks.length ? 'success' : 'warning'} text-dark`}>
              {done}/{checks.length} tamam
            </span>;
          })()}
        </div>
        <div className="card-body">
          <div className="row g-2">
            {[
              { ok: !!settings.company_legal_name, label: 'Ticari Unvan girildi' },
              { ok: !!settings.mersis_number, label: 'MERSİS numarası girildi' },
              { ok: !!settings.kep_address, label: 'KEP adresi girildi' },
              { ok: !!settings.tax_office && !!settings.tax_number, label: 'Vergi bilgileri girildi' },
              { ok: !!settings.etbis_qr_url, label: 'ETBİS QR kodu eklendi' },
            ].map((item, i) => (
              <div className="col-md-6" key={i}>
                <div className={`d-flex align-items-center gap-2 p-2 rounded ${item.ok ? 'bg-success bg-opacity-10' : 'bg-danger bg-opacity-10'}`}>
                  <i className={`fas ${item.ok ? 'fa-check-circle text-success' : 'fa-times-circle text-danger'}`}></i>
                  <span className="small">{item.label}</span>
                </div>
              </div>
            ))}
          </div>
          <p className="text-muted small mb-0 mt-2">
            <i className="fas fa-info-circle me-1"></i>
            Ayrıca CMS sayfalarında şu yasal içeriklerin oluşturulduğundan emin olun:
            <strong> Mesafeli Satış Sözleşmesi</strong>, <strong>Ön Bilgilendirme Formu</strong>,
            <strong> KVKK Aydınlatma</strong>, <strong>Çerez Politikası</strong>, <strong>İade Koşulları</strong>
          </p>
        </div>
      </div>

      {/* ── Mobile Menu Toggle ── */}
      <button className="btn btn-outline-primary w-100 mb-3 d-md-none" onClick={() => setMobileMenuOpen(!mobileMenuOpen)}>
        <i className={`fas fa-${mobileMenuOpen ? 'times' : 'bars'} me-2`}></i>
        {mobileMenuOpen ? 'Menüyü Kapat' : `${activeGroup.title} — Menüyü Aç`}
      </button>

      {/* ── Search Results View ── */}
      {searchResults !== null ? (
        <div className="card border-0 shadow-sm">
          <div className="card-header bg-transparent">
            <strong><i className="fas fa-search me-2"></i>Arama Sonuçları</strong>
            <span className="text-muted small ms-2">
              {searchResults.reduce((s, r) => s + r.matchedKeys.length, 0)} alan, {searchResults.length} grupta
            </span>
          </div>
          <div className="card-body">
            {searchResults.length === 0 ? (
              <div className="text-center text-muted py-4">
                <i className="fas fa-search fa-2x mb-2 opacity-25"></i>
                <p className="mb-0">Sonuç bulunamadı</p>
              </div>
            ) : searchResults.map(({ group, matchedKeys }) => (
              <div key={group.id} className="mb-4">
                <h6 className="text-muted text-uppercase small mb-3 d-flex align-items-center gap-2">
                  <i className={group.icon}></i>{group.title}
                  <button className="btn btn-sm btn-link p-0 ms-auto" onClick={() => { setSearchTerm(''); setActiveGroupId(group.id); }}>
                    Gruba git <i className="fas fa-arrow-right"></i>
                  </button>
                </h6>
                <div className="row g-3">{matchedKeys.map(k => renderField(k))}</div>
              </div>
            ))}
          </div>
        </div>
      ) : (
        <div className="row g-4">
          {/* ── Sidebar ── */}
          <div className={`col-lg-3 col-md-4 ${mobileMenuOpen ? '' : 'd-none d-md-block'}`}>
            <div className="card border-0 shadow-sm sticky-top" style={{top:16}}>
              <div className="card-body p-2">
                {Object.entries(sectionedGroups).map(([section, groups]) => (
                  <div key={section} className="mb-2">
                    <div className="text-uppercase text-muted small fw-bold px-2 pt-2 pb-1" style={{fontSize:10, letterSpacing:0.5}}>
                      {section}
                    </div>
                    {groups.map(g => {
                      const stats = groupStats[g.id];
                      const isActive = g.id === activeGroupId;
                      const hasEnabled = g.enabledKey !== undefined;
                      return (
                        <button key={g.id}
                          className={`btn w-100 text-start d-flex align-items-center gap-2 py-2 px-2 mb-1 border-0 ${isActive ? 'bg-primary text-white' : 'text-dark'}`}
                          onClick={() => { setActiveGroupId(g.id); setMobileMenuOpen(false); }}
                          style={{fontSize:13, borderRadius:6, transition: 'background .12s'}}
                          onMouseEnter={e => { if (!isActive) e.currentTarget.style.backgroundColor = 'rgba(0,0,0,.04)'; }}
                          onMouseLeave={e => { if (!isActive) e.currentTarget.style.backgroundColor = 'transparent'; }}
                        >
                          <i className={g.icon} style={{width:18, textAlign:'center', opacity: isActive ? 1 : 0.6}} />
                          <span className="flex-grow-1 fw-medium">{g.title}</span>
                          {stats.dirty > 0 && (
                            <span className="badge bg-warning text-dark" style={{fontSize:9}} title={`${stats.dirty} değiştirildi`}>
                              {stats.dirty}
                            </span>
                          )}
                          {hasEnabled && (
                            <span className={`badge ${stats.enabled ? 'bg-success' : 'bg-secondary'} bg-opacity-75`} style={{fontSize:9}}
                              title={stats.enabled ? 'Aktif' : 'Pasif'}>
                              <i className={`fas fa-${stats.enabled ? 'check' : 'times'}`} style={{fontSize:8}}></i>
                            </span>
                          )}
                        </button>
                      );
                    })}
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* ── Content ── */}
          <div className="col-lg-9 col-md-8">
            <div className="card border-0 shadow-sm">
              <div className="card-header bg-transparent border-bottom d-flex flex-wrap justify-content-between align-items-center gap-2" style={{padding:'16px 20px'}}>
                <div className="d-flex align-items-center gap-2">
                  <div className="rounded-circle bg-primary bg-opacity-10 d-flex align-items-center justify-content-center" style={{width:36, height:36}}>
                    <i className={`${activeGroup.icon} text-primary`} />
                  </div>
                  <div>
                    <h6 className="mb-0 fw-semibold">{activeGroup.title}</h6>
                    <div className="small text-muted">
                      {groupStats[activeGroup.id].filled}/{groupStats[activeGroup.id].total} alan doldu
                      {activeGroup.enabledKey && (
                        <span className={`badge ms-2 ${groupStats[activeGroup.id].enabled ? 'bg-success' : 'bg-secondary'}`} style={{fontSize:9}}>
                          {groupStats[activeGroup.id].enabled ? 'Aktif' : 'Pasif'}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
                {activeGroup.tooltip && (
                  <span className="text-muted small" style={{maxWidth:'60%', textAlign:'right'}}>
                    <i className="fas fa-info-circle me-1" />{activeGroup.tooltip}
                  </span>
                )}
              </div>
              <div className="card-body" style={{padding:'20px'}}>
                {loading ? (
                  <div className="py-5 text-center">
                    <div className="spinner-border text-primary"></div>
                  </div>
                ) : (
                  <div className="row g-3">{activeGroup.keys.map(key => renderField(key))}</div>
                )}
              </div>

              {/* Group footer — quick save */}
              {isDirty && (
                <div className="card-footer bg-light border-top d-flex justify-content-between align-items-center">
                  <span className="small text-muted">
                    <i className="fas fa-circle text-warning me-1" style={{fontSize:8}}></i>
                    {dirtyKeys.size} kaydedilmemiş değişiklik
                  </span>
                  <div className="d-flex gap-2">
                    <button className="btn btn-sm btn-outline-secondary" onClick={handleDiscard} disabled={saving}>
                      Geri Al
                    </button>
                    <button className="btn btn-sm btn-primary" onClick={handleSave} disabled={saving}>
                      <i className={`fas ${saving ? 'fa-spinner fa-spin' : 'fa-save'} me-1`}></i>
                      {saving ? 'Kaydediliyor...' : 'Kaydet'}
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Sticky save-bar when dirty (mobile-friendly) */}
      {isDirty && (
        <div className="position-fixed bottom-0 start-0 end-0 bg-white border-top shadow-lg d-md-none" style={{zIndex:1020, padding:12}}>
          <div className="d-flex gap-2">
            <button className="btn btn-outline-secondary flex-shrink-0" onClick={handleDiscard} disabled={saving}>
              <i className="fas fa-undo"></i>
            </button>
            <button className="btn btn-primary flex-grow-1" onClick={handleSave} disabled={saving}>
              <i className={`fas ${saving ? 'fa-spinner fa-spin' : 'fa-save'} me-2`} />
              {saving ? 'Kaydediliyor...' : `Kaydet (${dirtyKeys.size} değişiklik)`}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function FieldLabel({ label, tooltip, dirty }) {
  return (
    <div className="d-flex align-items-center gap-2 mb-1">
      <label className="form-label small fw-semibold mb-0">{label}</label>
      {dirty && <span className="badge bg-warning text-dark" style={{fontSize:9}} title="Değiştirildi">
        <i className="fas fa-circle" style={{fontSize:6}}></i>
      </span>}
      {tooltip && <span className="text-muted" title={tooltip} style={{cursor:'help', fontSize:12}}><i className="fas fa-info-circle" /></span>}
    </div>
  );
}
