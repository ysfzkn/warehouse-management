import React, { useState, useEffect, useRef } from 'react'; // eslint-disable-line
import axios from 'axios';
import useSecurityCodePrompt from '../components/useSecurityCodePrompt';
import { useAdminToast } from '../components/AdminToast';

const SETTING_GROUPS = [
  { title: 'Marka & Görünüm', icon: 'fas fa-palette', keys: ['site_name', 'site_logo_url', 'site_favicon_url', 'primary_color', 'secondary_color'],
    tooltip: 'Mağaza header\'ı, tarayıcı sekmesi ve genel renk temasında kullanılır.' },
  { title: 'İletişim Bilgileri', icon: 'fas fa-phone', keys: ['contact_phone', 'contact_email', 'contact_address', 'contact_map_embed'],
    tooltip: 'Footer, iletişim sayfası ve e-posta bildirimlerinde gösterilir.' },
  { title: 'Sosyal Medya', icon: 'fas fa-share-alt', keys: ['social_instagram', 'social_facebook', 'social_whatsapp'],
    tooltip: 'Footer ve iletişim sayfasında ikon olarak görünür.' },
  { title: 'Kurumsal / Yasal Bilgiler', icon: 'fas fa-building', keys: ['company_legal_name', 'mersis_number', 'kep_address', 'tax_office', 'tax_number', 'trade_registry_number', 'chamber_of_commerce', 'etbis_qr_url'],
    tooltip: 'Footer\'da ve yasal sayfalarda gösterilir. Canlıya almadan önce doldurulmalıdır.' },
  { title: 'Beyaz Eşya Uyarıları', icon: 'fas fa-exclamation-triangle', keys: ['warranty_notice_text', 'bulky_shipping_notice_text'],
    tooltip: 'Ürün detay ve ödeme sayfalarında gösterilecek yasal uyarı metinleri.' },
  { title: 'Para Birimi', icon: 'fas fa-coins', keys: ['currency_symbol'],
    tooltip: 'Mağazada ve faturalarda kullanılacak para birimi.' },
  { title: 'Genel', icon: 'fas fa-cog', keys: ['footer_text', 'header_announcement', 'contact_form_email'],
    tooltip: 'Footer alt bilgisi, üst bar duyuru mesajı ve form yönlendirme.' },
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
  free_shipping_threshold: 'Ücretsiz Kargo Limiti (TL)', default_shipping_cost: 'Varsayılan Kargo Ücreti (TL)',
  header_announcement: 'Üst Banner Duyuru Mesajı', contact_form_email: 'İletişim Formu Hedef E-posta',
  payment_method_credit_card_enabled: 'Kredi Kartı ile Ödeme', payment_method_bank_transfer_enabled: 'Havale / EFT ile Ödeme', payment_method_door_cash_enabled: 'Kapıda Ödeme',
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
  payment_method_credit_card_enabled: 'Kapatırsanız kredi kartı seçeneği müşterilere gösterilmez',
  payment_method_bank_transfer_enabled: 'Kapatırsanız havale/EFT seçeneği gösterilmez. IBAN yapılandırılmamışsa otomatik gizlenir.',
  payment_method_door_cash_enabled: 'Kapatırsanız kapıda ödeme seçeneği gösterilmez',
};

const CURRENCY_OPTIONS = [
  { value: '₺', label: 'TL (₺)' },
  { value: '$', label: 'USD ($)' },
  { value: '€', label: 'EUR (€)' },
  { value: '£', label: 'GBP (£)' },
];

export default function AdminSiteSettings() {
  const [settings, setSettings] = useState({});
  const [saving, setSaving] = useState(false);
  const [activeGroup, setActiveGroup] = useState(0);
  const [logoUploading, setLogoUploading] = useState(false);
  const [faviconUploading, setFaviconUploading] = useState(false);
  const [dragOver, setDragOver] = useState('');
  const fileInputRef = useRef(null);
  const faviconInputRef = useRef(null);
  const { askCode, SecurityCodePrompt } = useSecurityCodePrompt();
  const toast = useAdminToast();

  useEffect(() => {
    axios.get('/api/admin/settings/site').then(r => {
      const map = {}; (r.data || []).forEach(s => { map[s.settingKey] = s.settingValue; }); setSettings(map);
    }).catch(() => {});
  }, []);

  const handleChange = (key, value) => setSettings(s => ({ ...s, [key]: value }));

  const withSecurityCode = async (desc, fn) => {
    const code = await askCode({ description: desc });
    if (!code) return;
    try { await fn(code); } catch (e) {
      if (e.response?.status === 403) toast.error('Güvenlik şifresi hatalı.');
      else toast.error('İşlem başarısız.');
    }
  };

  const handleSave = () => withSecurityCode('Site ayarlarını kaydetmek için güvenlik şifresini girin.', async (code) => {
    setSaving(true);
    try {
      await axios.put('/api/admin/settings/site', settings, { headers: { 'X-ADMIN-SECURITY-CODE': code } });
      toast.success('Ayarlar kaydedildi.');
    } catch (e) { throw e; }
    finally { setSaving(false); }
  });

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
      handleChange(isLogo ? 'site_logo_url' : 'site_favicon_url', newUrl);
      // Immediately update browser favicon if favicon was uploaded
      if (!isLogo && newUrl) {
        document.querySelectorAll("link[rel*='icon']").forEach(el => el.remove());
        const link = document.createElement('link');
        link.rel = 'icon';
        link.href = newUrl + '?v=' + Date.now();
        document.head.appendChild(link);
      }
      toast.success(`${isLogo ? 'Logo' : 'Favicon'} başarıyla yüklendi!`);
    } catch (e) {
      if (e.response?.status === 403) toast.error('Güvenlik şifresi hatalı.');
      else toast.error('İşlem başarısız.');
    } finally { isLogo ? setLogoUploading(false) : setFaviconUploading(false); }
  };

  const group = SETTING_GROUPS[activeGroup];

  // ===== Reusable upload zone component =====
  const UploadZone = ({ type, label, currentUrl, uploading, inputRef, formats, sizeHint }) => {
    const hasFile = currentUrl && currentUrl.length > 1;
    const isLogo = type === 'logo';
    return (
      <div className="col-12" key={`upload-${type}`}>
        <FieldLabel label={label} tooltip={FIELD_TOOLTIPS[isLogo ? 'site_logo_url' : 'site_favicon_url']} />
        <div className="border rounded overflow-hidden">
          {/* Current file preview */}
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
          {/* Drop zone */}
          <div
            className={`p-3 text-center ${dragOver === type ? 'bg-primary bg-opacity-10' : ''}`}
            onDragOver={e => { e.preventDefault(); setDragOver(type); }}
            onDragLeave={() => setDragOver('')}
            onDrop={e => { e.preventDefault(); setDragOver(''); handleFileUpload(type, e.dataTransfer.files[0]); }}
            style={{ cursor: 'pointer' }}
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

  const renderField = (key) => {
    if (key === 'site_logo_url') {
      return <UploadZone key={key} type="logo" label="Logo" currentUrl={settings[key]} uploading={logoUploading}
        inputRef={fileInputRef} formats="PNG, JPG, SVG, WebP" sizeHint="Maks. 10MB" />;
    }
    if (key === 'site_favicon_url') {
      return <UploadZone key={key} type="favicon" label="Favicon" currentUrl={settings[key]} uploading={faviconUploading}
        inputRef={faviconInputRef} formats="ICO, PNG, SVG" sizeHint="32×32px önerilir" />;
    }
    // Payment method toggles
    if (key.startsWith('payment_method_') && key.endsWith('_enabled')) {
      const isOn = settings[key] !== 'false';
      return (
        <div key={key} className="col-md-4">
          <div className={`border rounded p-3 h-100 ${isOn ? 'border-success bg-success bg-opacity-10' : 'border-light'}`}>
            <div className="form-check form-switch">
              <input className="form-check-input" type="checkbox" checked={isOn}
                onChange={e => handleChange(key, e.target.checked ? 'true' : 'false')} id={key} />
              <label className="form-check-label small fw-semibold" htmlFor={key}>{LABELS[key]}</label>
            </div>
            {FIELD_TOOLTIPS[key] && <small className="text-muted d-block mt-1">{FIELD_TOOLTIPS[key]}</small>}
          </div>
        </div>
      );
    }

    if (key === 'currency_symbol') {
      return (
        <div key={key} className="col-md-6">
          <FieldLabel label={LABELS[key]} tooltip={FIELD_TOOLTIPS[key]} />
          <select className="form-select" value={settings[key] || '₺'} onChange={e => handleChange(key, e.target.value)}>
            {CURRENCY_OPTIONS.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
          </select>
        </div>
      );
    }
    if (key.includes('color')) {
      return (
        <div key={key} className="col-md-6">
          <FieldLabel label={LABELS[key]} tooltip={FIELD_TOOLTIPS[key]} />
          <div className="input-group">
            <input type="color" className="form-control form-control-color border-end-0" value={settings[key]||'#000000'} onChange={e => handleChange(key, e.target.value)} style={{width:46, height:38}} />
            <input className="form-control" value={settings[key]||''} onChange={e => handleChange(key, e.target.value)} placeholder="#000000" />
          </div>
        </div>
      );
    }
    if (key === 'contact_map_embed') {
      return (
        <div key={key} className="col-12">
          <FieldLabel label={LABELS[key]} tooltip={FIELD_TOOLTIPS[key]} />
          <textarea className="form-control" rows={2} value={settings[key]||''} onChange={e => handleChange(key, e.target.value)} placeholder="https://www.google.com/maps/embed?..." />
          {settings[key] && <div className="mt-2 border rounded overflow-hidden" style={{height:180}}><iframe src={settings[key]} style={{width:'100%',height:'100%',border:0}} title="Harita" loading="lazy" /></div>}
        </div>
      );
    }
    if (key.includes('text') || key.includes('announcement')) {
      return (
        <div key={key} className="col-12">
          <FieldLabel label={LABELS[key]} tooltip={FIELD_TOOLTIPS[key]} />
          <textarea className="form-control" rows={2} value={settings[key]||''} onChange={e => handleChange(key, e.target.value)} />
        </div>
      );
    }
    // Default — half width for shorter fields
    const isShort = ['contact_phone', 'contact_email', 'free_shipping_threshold', 'default_shipping_cost', 'social_whatsapp', 'contact_form_email'].includes(key);
    return (
      <div key={key} className={isShort ? 'col-md-6' : 'col-12'}>
        <FieldLabel label={LABELS[key] || key} tooltip={FIELD_TOOLTIPS[key]} />
        <input className="form-control" value={settings[key]||''} onChange={e => handleChange(key, e.target.value)} />
      </div>
    );
  };

  return (
    <div>
      {SecurityCodePrompt}
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="mb-0">Site Ayarları</h2>
          <p className="text-muted small mb-0">Mağaza görünümü, iletişim ve ödeme yapılandırması</p>
        </div>
        <button className="btn btn-primary px-4" onClick={handleSave} disabled={saving}>
          <i className={`fas ${saving ? 'fa-spinner fa-spin' : 'fa-save'} me-2`} />{saving ? 'Kaydediliyor...' : 'Kaydet'}
        </button>
      </div>

      {/* ── Yasal Uyumluluk Durumu ── */}
      <div className="card border-0 shadow-sm mb-4">
        <div className="card-header bg-warning bg-opacity-10">
          <strong><i className="fas fa-gavel me-2"></i>Yasal Uyumluluk Durumu</strong>
          <span className="text-muted small ms-2">— Canlıya almadan önce tüm maddeleri tamamlayın</span>
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
            <div className="col-12 mt-2">
              <p className="text-muted small mb-0">
                <i className="fas fa-info-circle me-1"></i>
                Ayrıca CMS sayfalarında şu yasal içeriklerin oluşturulduğundan emin olun:
                <strong> Mesafeli Satış Sözleşmesi</strong>,
                <strong> Ön Bilgilendirme Formu</strong>,
                <strong> KVKK Aydınlatma Metni</strong>,
                <strong> Çerez Politikası</strong>,
                <strong> İade ve Değişim Koşulları</strong>
              </p>
            </div>
          </div>
        </div>
      </div>

      <div className="row g-4">
        {/* Sidebar */}
        <div className="col-lg-3 col-md-4">
          <div className="card border-0 shadow-sm sticky-top" style={{top:16}}>
            <div className="list-group list-group-flush rounded">
              {SETTING_GROUPS.map((g, i) => (
                <button key={i} className={`list-group-item list-group-item-action d-flex align-items-center gap-2 border-0 ${i === activeGroup ? 'active' : ''}`}
                  onClick={() => setActiveGroup(i)} style={{padding:'12px 16px'}}>
                  <i className={`${g.icon} ${i === activeGroup ? '' : 'text-muted'}`} style={{width:20, textAlign:'center'}} />
                  <span className="small fw-medium">{g.title}</span>
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Content */}
        <div className="col-lg-9 col-md-8">
          <div className="card border-0 shadow-sm">
            <div className="card-header bg-transparent border-bottom d-flex justify-content-between align-items-center" style={{padding:'16px 20px'}}>
              <div className="d-flex align-items-center gap-2">
                <i className={`${group.icon} text-primary`} />
                <h6 className="mb-0 fw-semibold">{group.title}</h6>
              </div>
              {group.tooltip && (
                <span className="text-muted small" title={group.tooltip} style={{cursor:'help'}}>
                  <i className="fas fa-info-circle me-1" />Bilgi
                </span>
              )}
            </div>
            <div className="card-body" style={{padding:'20px'}}>
              <div className="row g-3">{group.keys.map(key => renderField(key))}</div>
            </div>
          </div>

        </div>
      </div>
    </div>
  );
}

function FieldLabel({ label, tooltip }) {
  return (
    <div className="d-flex align-items-center gap-2 mb-1">
      <label className="form-label small fw-semibold mb-0">{label}</label>
      {tooltip && <span className="text-muted" title={tooltip} style={{cursor:'help', fontSize:12}}><i className="fas fa-info-circle" /></span>}
    </div>
  );
}

