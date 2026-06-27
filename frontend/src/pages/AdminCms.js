import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import useSecurityCodePrompt from '../components/useSecurityCodePrompt';
import RichTextEditor from '../components/RichTextEditor';
import confirmDialog from '../utils/confirmDialog';

const PAGE_TYPES = { CONTENT: 'İçerik Sayfası', LEGAL: 'Yasal Sayfa', FAQ: 'SSS', BANNER: 'Banner / Slayt' };
const BANNER_POSITIONS = { HERO: 'Ana Sayfa (Hero)', SIDEBAR: 'Yan Panel', FOOTER: 'Alt Kısım' };
const BANNER_SIZE_HINTS = {
  HERO: '1920x600 piksel önerilir. Ana sayfa slayt alanında tam genişlikte gösterilir.',
  SIDEBAR: '400x300 piksel önerilir. Yan panelde küçük banner olarak gösterilir.',
  FOOTER: '1200x200 piksel önerilir. Alt kısımda geniş bir banner olarak gösterilir.',
};

export default function AdminCms() {
  const [pages, setPages] = useState([]);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState({
    title: '',
    slug: '',
    content: '',
    pageType: 'CONTENT',
    active: true,
    bannerImageUrl: '',
    bannerLink: '',
    bannerPosition: '',
    sortOrder: 0,
  });
  const [showForm, setShowForm] = useState(false);
  const [linkOptions, setLinkOptions] = useState([]);
  const [customLink, setCustomLink] = useState(false);
  const [bannerUploading, setBannerUploading] = useState(false);
  const bannerFileRef = React.useRef(null);
  const { askCode, SecurityCodePrompt } = useSecurityCodePrompt();

  const fetchPages = useCallback(() => {
    axios
      .get('/api/admin/cms', { params: { size: 100 } })
      .then((r) => setPages(r.data?.content || []))
      .catch(() => {});
  }, []);

  useEffect(() => {
    fetchPages();
  }, [fetchPages]);

  // Load link options (categories, CMS pages) for banner link dropdown
  useEffect(() => {
    const opts = [
      {
        group: 'Genel',
        items: [
          { value: '/', label: 'Ana Sayfa' },
          { value: '/sepet', label: 'Sepet' },
        ],
      },
    ];
    // Fetch categories
    const catPromise = axios
      .get('/api/categories')
      .then((r) => {
        const cats = (r.data || []).filter((c) => c.slug);
        if (cats.length > 0) {
          opts.push({
            group: 'Kategoriler',
            items: cats.map((c) => ({ value: `/kategori/${c.slug}`, label: c.name })),
          });
        }
      })
      .catch(() => {});
    // Fetch CMS pages
    const cmsPromise = axios
      .get('/api/admin/cms', { params: { size: 100 } })
      .then((r) => {
        const cmsPages = (r.data?.content || []).filter((p) => p.pageType !== 'BANNER' && p.slug);
        if (cmsPages.length > 0) {
          opts.push({
            group: 'Sayfalar',
            items: cmsPages.map((p) => ({ value: `/sayfa/${p.slug}`, label: p.title })),
          });
        }
      })
      .catch(() => {});
    Promise.all([catPromise, cmsPromise]).then(() => setLinkOptions(opts));
  }, []);

  const handleBannerImageUpload = async (file) => {
    if (!file || !file.type.startsWith('image/')) return;
    if (file.size > 10 * 1024 * 1024) {
      alert("Dosya boyutu 10MB'ı aşamaz.");
      return;
    }
    setBannerUploading(true);
    try {
      const fd = new FormData();
      fd.append('file', file);
      fd.append('name', 'banner');
      const res = await axios.post('/api/admin/settings/site/upload', fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      setForm((f) => ({ ...f, bannerImageUrl: res.data.url || '' }));
    } catch (e) {
      alert(e.response?.data?.message || 'Görsel yüklenemedi.');
    } finally {
      setBannerUploading(false);
    }
  };

  const handleSave = async () => {
    if (!form.title || !form.slug) {
      alert('Başlık ve slug zorunludur');
      return;
    }
    if (form.pageType === 'BANNER' && !form.bannerPosition) {
      alert('Banner pozisyonu seçiniz');
      return;
    }
    try {
      if (editing) {
        await axios.put(`/api/admin/cms/${editing}`, form);
      } else {
        await axios.post('/api/admin/cms', form);
      }
      resetForm();
      fetchPages();
    } catch (e) {
      alert(e.response?.data?.message || 'Hata oluştu');
    }
  };

  const handleDelete = async (id, title) => {
    const ok = await confirmDialog({
      title: 'Sayfa Silinsin mi?',
      message: `"${title}" sayfası kalıcı olarak silinecek.`,
      confirmText: 'Evet, Sil',
    });
    if (!ok) return;
    const code = await askCode({ description: `"${title}" silmek için güvenlik şifresini girin.` });
    if (!code) return;
    try {
      await axios.delete(`/api/admin/cms/${id}`, { headers: { 'X-ADMIN-SECURITY-CODE': code } });
      fetchPages();
    } catch (e) {
      alert(e.response?.status === 403 ? 'Güvenlik şifresi hatalı.' : 'Silinemedi');
    }
  };

  const startEdit = (p) => {
    setEditing(p.id);
    setForm({
      title: p.title,
      slug: p.slug,
      content: p.content || '',
      pageType: p.pageType,
      active: p.active,
      bannerImageUrl: p.bannerImageUrl || '',
      bannerLink: p.bannerLink || '',
      bannerPosition: p.bannerPosition || '',
      sortOrder: p.sortOrder || 0,
    });
    // Check if bannerLink exists in predefined options, otherwise show custom input
    const allValues = linkOptions.flatMap((g) => g.items.map((i) => i.value));
    setCustomLink(p.bannerLink && !allValues.includes(p.bannerLink) && p.bannerLink !== '');
    setShowForm(true);
  };

  const resetForm = () => {
    setEditing(null);
    setForm({
      title: '',
      slug: '',
      content: '',
      pageType: 'CONTENT',
      active: true,
      bannerImageUrl: '',
      bannerLink: '',
      bannerPosition: '',
      sortOrder: 0,
    });
    setCustomLink(false);
    setShowForm(false);
  };

  const startNewBanner = () => {
    resetForm();
    setForm((f) => ({ ...f, pageType: 'BANNER' }));
    setShowForm(true);
  };

  const startNewPage = () => {
    resetForm();
    setShowForm(true);
  };

  const autoSlug = (title) =>
    title
      .toLowerCase()
      .replace(/ı/g, 'i')
      .replace(/ğ/g, 'g')
      .replace(/ü/g, 'u')
      .replace(/ş/g, 's')
      .replace(/ö/g, 'o')
      .replace(/ç/g, 'c')
      .replace(/İ/g, 'i')
      .replace(/Ğ/g, 'g')
      .replace(/Ü/g, 'u')
      .replace(/Ş/g, 's')
      .replace(/Ö/g, 'o')
      .replace(/Ç/g, 'c')
      .replace(/[^a-z0-9\s-]/g, '')
      .replace(/\s+/g, '-')
      .replace(/-+/g, '-')
      .replace(/^-|-$/g, '');

  const isBanner = form.pageType === 'BANNER';
  const banners = pages.filter((p) => p.pageType === 'BANNER');
  const contentPages = pages.filter((p) => p.pageType !== 'BANNER');

  // Slugs of required legal pages linked in checkout + footer
  const REQUIRED_LEGAL = [
    { slug: 'kvkk', title: 'KVKK Aydınlatma Metni', reason: 'Checkout KVKK onay kutusu', template: 'kvkk' },
    {
      slug: 'on-bilgilendirme-formu',
      title: 'Ön Bilgilendirme Formu',
      reason: 'Checkout ön bilgilendirme onayı',
      template: 'on-bilgilendirme',
    },
    {
      slug: 'mesafeli-satis-sozlesmesi',
      title: 'Mesafeli Satış Sözleşmesi',
      reason: 'Checkout sözleşme onayı',
      template: 'mesafeli',
    },
    {
      slug: 'iade-ve-degisim',
      title: 'İade ve Değişim Koşulları',
      reason: 'İade politikası linki',
      template: 'iade',
    },
    { slug: 'cerez-politikasi', title: 'Çerez Politikası', reason: 'Çerez banner onayı', template: 'cerez' },
  ];
  const existingSlugs = new Set(pages.map((p) => p.slug));
  const missingLegal = REQUIRED_LEGAL.filter((r) => !existingSlugs.has(r.slug));

  const LEGAL_TEMPLATES = {
    kvkk: '<h2>KVKK Aydınlatma Metni</h2><p>Bu aydınlatma metni 6698 sayılı Kanun kapsamında hazırlanmıştır...</p><h3>İşlenen Veriler</h3><p>Ad-soyad, iletişim, adres, sipariş bilgileriniz işlenir.</p><p><em>Bu varsayılan şablonu düzenleyin.</em></p>',
    'on-bilgilendirme':
      '<h2>Ön Bilgilendirme Formu</h2><p>6502 sayılı Tüketici Kanunu uyarınca sipariş öncesi bilgilendirme amacıyla hazırlanmıştır.</p><h3>Satıcı Bilgileri</h3><p>Ünvan / Adres / Telefon / E-posta</p><h3>Cayma Hakkı</h3><p>14 gün içinde gerekçe göstermeden iade hakkınız vardır.</p><p><em>Bu varsayılan şablonu düzenleyin.</em></p>',
    mesafeli:
      '<h2>Mesafeli Satış Sözleşmesi</h2><p>İşbu sözleşme, alıcı ile satıcı arasında akdedilmiştir.</p><h3>Konu</h3><p>Sözleşmenin konusu, alıcının sipariş verdiği ürünlerin satışı ve tesliminin koşullarıdır.</p><p><em>Bu varsayılan şablonu düzenleyin.</em></p>',
    iade: '<h2>İade ve Değişim Koşulları</h2><p>Cayma hakkınızı malın teslim tarihinden itibaren 14 gün içinde kullanabilirsiniz.</p><p><em>Bu varsayılan şablonu düzenleyin.</em></p>',
    cerez:
      '<h2>Çerez Politikası</h2><p>Sitemizde kullanıcı deneyimini iyileştirmek için çerezler kullanılmaktadır.</p><p><em>Bu varsayılan şablonu düzenleyin.</em></p>',
  };

  const createMissingLegal = async (item) => {
    try {
      await axios.post('/api/admin/cms', {
        slug: item.slug,
        title: item.title,
        content: LEGAL_TEMPLATES[item.template] || '<p>İçerik hazırlanacak</p>',
        pageType: 'LEGAL',
        active: true,
        sortOrder: 0,
      });
      fetchPages();
    } catch (e) {
      alert(e.response?.data?.message || 'Sayfa oluşturulamadı');
    }
  };

  const createAllMissing = async () => {
    for (const item of missingLegal) {
      // eslint-disable-next-line no-await-in-loop
      await createMissingLegal(item);
    }
  };

  return (
    <div>
      {SecurityCodePrompt}
      <div className="d-flex justify-content-between align-items-center mb-4">
        <h2>İçerik & Banner Yönetimi</h2>
        <div className="d-flex gap-2">
          <button className="btn btn-outline-primary" onClick={startNewPage}>
            <i className="fas fa-file-alt me-1" />
            Yeni Sayfa
          </button>
          <button className="btn btn-primary" onClick={startNewBanner}>
            <i className="fas fa-images me-1" />
            Yeni Banner
          </button>
        </div>
      </div>

      {/* Required Legal Pages check-list */}
      <div
        className={`card border-0 shadow-sm mb-4 ${missingLegal.length ? 'border-start border-warning border-4' : ''}`}
      >
        <div
          className={`card-header d-flex justify-content-between align-items-center ${missingLegal.length ? 'bg-warning bg-opacity-10' : 'bg-success bg-opacity-10'}`}
        >
          <div>
            <strong>
              <i
                className={`fas ${missingLegal.length ? 'fa-exclamation-triangle text-warning' : 'fa-check-circle text-success'} me-2`}
              ></i>
              Zorunlu Yasal Sayfalar
            </strong>
            <span className="text-muted small ms-2">
              {missingLegal.length === 0
                ? 'Tüm zorunlu sayfalar mevcut 👍'
                : `${missingLegal.length} sayfa eksik — checkout/footer linkleri kırık.`}
            </span>
          </div>
          {missingLegal.length > 1 && (
            <button className="btn btn-sm btn-warning" onClick={createAllMissing}>
              <i className="fas fa-magic me-1"></i>Tümünü Şablonla Oluştur
            </button>
          )}
        </div>
        <div className="card-body py-2">
          <div className="row g-2">
            {REQUIRED_LEGAL.map((item) => {
              const exists = existingSlugs.has(item.slug);
              const page = pages.find((p) => p.slug === item.slug);
              return (
                <div className="col-md-6" key={item.slug}>
                  <div
                    className={`p-2 rounded d-flex align-items-center gap-2 ${exists ? 'bg-success bg-opacity-10' : 'bg-danger bg-opacity-10'}`}
                  >
                    <i
                      className={`fas ${exists ? 'fa-check-circle text-success' : 'fa-times-circle text-danger'}`}
                    ></i>
                    <div className="flex-grow-1 min-w-0">
                      <div className="small fw-semibold text-truncate">{item.title}</div>
                      <div className="small text-muted text-truncate">
                        <code>/sayfa/{item.slug}</code> · {item.reason}
                      </div>
                    </div>
                    {exists ? (
                      <button className="btn btn-sm btn-outline-primary" onClick={() => startEdit(page)}>
                        <i className="fas fa-pen me-1"></i>Düzenle
                      </button>
                    ) : (
                      <button className="btn btn-sm btn-warning" onClick={() => createMissingLegal(item)}>
                        <i className="fas fa-plus me-1"></i>Oluştur
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      <div className="row g-4">
        {/* Form */}
        {showForm && (
          <div className="col-lg-5">
            <div className="card border-0 shadow-sm">
              <div className="card-header bg-transparent d-flex justify-content-between align-items-center">
                <h6 className="mb-0">
                  {editing ? 'Düzenle' : isBanner ? 'Yeni Banner Ekle' : 'Yeni Sayfa Ekle'}
                </h6>
                <button className="btn-close" onClick={resetForm} />
              </div>
              <div className="card-body">
                <div className="mb-3">
                  <label className="form-label small fw-medium">Sayfa Tipi</label>
                  <select
                    className="form-select"
                    value={form.pageType}
                    onChange={(e) => setForm({ ...form, pageType: e.target.value })}
                  >
                    {Object.entries(PAGE_TYPES).map(([k, v]) => (
                      <option key={k} value={k}>
                        {v}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="mb-3">
                  <label className="form-label small fw-medium">Başlık</label>
                  <input
                    className="form-control"
                    value={form.title}
                    onChange={(e) => {
                      setForm({ ...form, title: e.target.value });
                      if (!editing) setForm((f) => ({ ...f, slug: autoSlug(e.target.value) }));
                    }}
                    placeholder={isBanner ? 'Banner başlığı (ör: Yaz İndirimi)' : 'Sayfa başlığı'}
                  />
                </div>

                <div className="mb-3">
                  <label className="form-label small fw-medium">Slug (URL)</label>
                  <div className="input-group">
                    <span className="input-group-text small">{isBanner ? 'banner/' : '/sayfa/'}</span>
                    <input
                      className="form-control"
                      value={form.slug}
                      onChange={(e) => setForm({ ...form, slug: e.target.value })}
                      placeholder="sayfa-url"
                    />
                  </div>
                </div>

                {isBanner && (
                  <>
                    <div className="mb-3">
                      <label className="form-label small fw-medium">
                        Pozisyon <span className="text-danger">*</span>
                      </label>
                      <select
                        className="form-select"
                        value={form.bannerPosition}
                        onChange={(e) => setForm({ ...form, bannerPosition: e.target.value })}
                      >
                        <option value="">Pozisyon seçiniz...</option>
                        {Object.entries(BANNER_POSITIONS).map(([k, v]) => (
                          <option key={k} value={k}>
                            {v}
                          </option>
                        ))}
                      </select>
                      {form.bannerPosition && BANNER_SIZE_HINTS[form.bannerPosition] && (
                        <div className="alert alert-info small mt-2 mb-0 py-2">
                          <i className="fas fa-ruler-combined me-1" />
                          {BANNER_SIZE_HINTS[form.bannerPosition]}
                        </div>
                      )}
                    </div>

                    <div className="mb-3">
                      <label className="form-label small fw-medium">Banner Görseli</label>
                      <input
                        type="file"
                        ref={bannerFileRef}
                        className="d-none"
                        accept="image/*"
                        onChange={(e) => {
                          handleBannerImageUpload(e.target.files[0]);
                          e.target.value = '';
                        }}
                      />
                      {form.bannerImageUrl ? (
                        <div className="border rounded overflow-hidden">
                          <div className="bg-light">
                            <img
                              src={
                                form.bannerImageUrl +
                                (form.bannerImageUrl.includes('?') ? '&' : '?') +
                                'v=' +
                                Date.now()
                              }
                              alt="Banner Önizleme"
                              style={{ width: '100%', display: 'block' }}
                              onError={(e) => {
                                e.target.style.display = 'none';
                              }}
                            />
                          </div>
                          <div className="d-flex justify-content-between align-items-center p-2 border-top bg-white">
                            <small className="text-muted text-truncate" style={{ maxWidth: 250 }}>
                              <i className="fas fa-check-circle text-success me-1" />
                              Görsel yüklendi
                            </small>
                            <button
                              type="button"
                              className="btn btn-outline-primary btn-sm"
                              onClick={() => bannerFileRef.current?.click()}
                            >
                              <i className="fas fa-sync-alt me-1" />
                              Değiştir
                            </button>
                          </div>
                        </div>
                      ) : (
                        <div
                          className="border rounded p-3 text-center"
                          style={{ cursor: 'pointer' }}
                          onClick={() => bannerFileRef.current?.click()}
                          onDragOver={(e) => e.preventDefault()}
                          onDrop={(e) => {
                            e.preventDefault();
                            handleBannerImageUpload(e.dataTransfer.files[0]);
                          }}
                        >
                          {bannerUploading ? (
                            <span>
                              <span className="spinner-border spinner-border-sm me-2" />
                              Yükleniyor...
                            </span>
                          ) : (
                            <div>
                              <i className="fas fa-image text-muted d-block mb-1" />
                              <span className="small text-muted">
                                Görsel sürükleyin veya <span className="text-primary fw-medium">seçin</span>
                              </span>
                              <div className="text-muted small">
                                {form.bannerPosition
                                  ? BANNER_SIZE_HINTS[form.bannerPosition]?.split('.')[0] || 'PNG, JPG, WebP'
                                  : 'PNG, JPG, WebP'}
                              </div>
                            </div>
                          )}
                        </div>
                      )}
                    </div>

                    <div className="mb-3">
                      <label className="form-label small fw-medium">Banner Link</label>
                      {!customLink ? (
                        <>
                          <select
                            className="form-select"
                            value={form.bannerLink}
                            onChange={(e) => {
                              if (e.target.value === '__custom__') {
                                setCustomLink(true);
                                setForm({ ...form, bannerLink: '' });
                              } else setForm({ ...form, bannerLink: e.target.value });
                            }}
                          >
                            <option value="">Link yok (sadece görsel)</option>
                            {linkOptions.map((group) => (
                              <optgroup key={group.group} label={group.group}>
                                {group.items.map((item) => (
                                  <option key={item.value} value={item.value}>
                                    {item.label}
                                  </option>
                                ))}
                              </optgroup>
                            ))}
                            <optgroup label="Diğer">
                              <option value="__custom__">Özel URL gir...</option>
                            </optgroup>
                          </select>
                          {form.bannerLink && (
                            <small className="text-muted mt-1 d-block">
                              <i className="fas fa-link me-1" />
                              {form.bannerLink}
                            </small>
                          )}
                        </>
                      ) : (
                        <div className="input-group">
                          <input
                            className="form-control"
                            value={form.bannerLink}
                            onChange={(e) => setForm({ ...form, bannerLink: e.target.value })}
                            placeholder="https://... veya /store/..."
                          />
                          <button
                            className="btn btn-outline-secondary"
                            type="button"
                            onClick={() => setCustomLink(false)}
                            title="Listeden seç"
                          >
                            <i className="fas fa-list" />
                          </button>
                        </div>
                      )}
                      <small className="text-muted">Tıklandığında yönlendirilecek sayfa.</small>
                    </div>
                  </>
                )}

                {!isBanner && (
                  <div className="mb-3">
                    <label className="form-label small fw-medium d-flex justify-content-between align-items-center">
                      <span>İçerik</span>
                      <small className="text-muted fw-normal">
                        <i className="fas fa-keyboard me-1"></i>
                        <kbd>Ctrl+B</kbd> kalın · <kbd>Ctrl+I</kbd> italik · <kbd>Ctrl+K</kbd> link
                      </small>
                    </label>
                    <RichTextEditor
                      value={form.content}
                      onChange={(html) => setForm({ ...form, content: html })}
                      placeholder="Sayfa içeriğini buraya yazın. Üst çubuktan başlık, kalın, liste, link seçebilirsiniz…"
                      minHeight={340}
                    />
                    <small className="text-muted d-block mt-1">
                      <i className="fas fa-info-circle me-1"></i>
                      Word veya Google Docs'tan yapıştırırsanız stiller otomatik temizlenir. Metin zaten
                      yayında, arka planda HTML olarak kaydedilir.
                    </small>
                  </div>
                )}

                <div className="mb-3">
                  <label className="form-label small fw-medium">Sıra</label>
                  <input
                    type="number"
                    className="form-control"
                    value={form.sortOrder}
                    onChange={(e) => setForm({ ...form, sortOrder: parseInt(e.target.value) || 0 })}
                  />
                  <small className="text-muted">Küçük sayılar önce gösterilir.</small>
                </div>

                <div className="form-check mb-3">
                  <input
                    className="form-check-input"
                    type="checkbox"
                    checked={form.active}
                    onChange={(e) => setForm({ ...form, active: e.target.checked })}
                    id="cmsActive"
                  />
                  <label className="form-check-label" htmlFor="cmsActive">
                    Aktif (yayında)
                  </label>
                </div>

                <div className="d-flex gap-2">
                  <button className="btn btn-primary" onClick={handleSave}>
                    <i className="fas fa-save me-1" />
                    {editing ? 'Güncelle' : 'Oluştur'}
                  </button>
                  <button className="btn btn-outline-secondary" onClick={resetForm}>
                    İptal
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Lists */}
        <div className={showForm ? 'col-lg-7' : 'col-12'}>
          {/* Banners */}
          <div className="card border-0 shadow-sm mb-4">
            <div className="card-header bg-transparent d-flex justify-content-between align-items-center">
              <h6 className="mb-0">
                <i className="fas fa-images me-2 text-primary" />
                Bannerlar ({banners.length})
              </h6>
              {!showForm && (
                <button className="btn btn-sm btn-primary" onClick={startNewBanner}>
                  <i className="fas fa-plus me-1" />
                  Yeni Banner
                </button>
              )}
            </div>
            <div className="card-body p-0">
              {banners.length === 0 ? (
                <div className="text-center py-5">
                  <i className="fas fa-images text-muted fa-3x mb-3 d-block opacity-25" />
                  <p className="text-muted mb-2">Henüz banner eklenmemiş.</p>
                  <p className="text-muted small mb-3">
                    Banner ekleyerek mağaza ana sayfasında kampanya ve duyurularınızı öne çıkarabilirsiniz.
                  </p>
                  <button className="btn btn-sm btn-outline-primary" onClick={startNewBanner}>
                    <i className="fas fa-plus me-1" />
                    İlk Banner'ınızı Ekleyin
                  </button>
                </div>
              ) : (
                <div className="list-group list-group-flush">
                  {banners
                    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
                    .map((b) => (
                      <div key={b.id} className="list-group-item d-flex align-items-center gap-3">
                        {b.bannerImageUrl ? (
                          <img
                            src={b.bannerImageUrl}
                            alt=""
                            style={{ width: 100, height: 56, objectFit: 'cover', borderRadius: 6 }}
                            onError={(e) => {
                              e.target.style.display = 'none';
                            }}
                          />
                        ) : (
                          <div
                            className="bg-light rounded d-flex align-items-center justify-content-center"
                            style={{ width: 100, height: 56 }}
                          >
                            <i className="fas fa-image text-muted" />
                          </div>
                        )}
                        <div className="flex-grow-1">
                          <div className="fw-medium small">{b.title}</div>
                          <div className="d-flex gap-2">
                            <small className="text-muted">
                              {BANNER_POSITIONS[b.bannerPosition] || b.bannerPosition}
                            </small>
                            {b.bannerLink && <small className="text-muted">→ {b.bannerLink}</small>}
                          </div>
                        </div>
                        <span className="badge bg-secondary bg-opacity-25 text-dark small">
                          #{b.sortOrder || 0}
                        </span>
                        <span className={`badge bg-${b.active ? 'success' : 'danger'}`}>
                          {b.active ? 'Aktif' : 'Pasif'}
                        </span>
                        <div className="btn-group btn-group-sm">
                          <button
                            className="btn btn-outline-primary"
                            onClick={() => startEdit(b)}
                            title="Düzenle"
                          >
                            <i className="fas fa-edit" />
                          </button>
                          <button
                            className="btn btn-outline-danger"
                            onClick={() => handleDelete(b.id, b.title)}
                            title="Sil"
                          >
                            <i className="fas fa-trash" />
                          </button>
                        </div>
                      </div>
                    ))}
                </div>
              )}
            </div>
          </div>

          {/* Content Pages */}
          <div className="card border-0 shadow-sm">
            <div className="card-header bg-transparent d-flex justify-content-between align-items-center">
              <h6 className="mb-0">
                <i className="fas fa-file-alt me-2 text-info" />
                Sayfalar ({contentPages.length})
              </h6>
              {!showForm && (
                <button className="btn btn-sm btn-outline-primary" onClick={startNewPage}>
                  <i className="fas fa-plus me-1" />
                  Yeni Sayfa
                </button>
              )}
            </div>
            <div className="card-body p-0">
              {contentPages.length === 0 ? (
                <div className="text-center py-5">
                  <i className="fas fa-file-alt text-muted fa-3x mb-3 d-block opacity-25" />
                  <p className="text-muted mb-3">Henüz sayfa oluşturulmamış.</p>
                  <button className="btn btn-sm btn-outline-primary" onClick={startNewPage}>
                    <i className="fas fa-plus me-1" />
                    Sayfa Oluştur
                  </button>
                </div>
              ) : (
                <table className="table table-hover mb-0">
                  <thead className="table-light">
                    <tr>
                      <th>Başlık</th>
                      <th>Tip</th>
                      <th>Durum</th>
                      <th style={{ width: 100 }}></th>
                    </tr>
                  </thead>
                  <tbody>
                    {contentPages.map((p) => (
                      <tr key={p.id}>
                        <td>
                          <div className="fw-medium small">{p.title}</div>
                          <small className="text-muted">/sayfa/{p.slug}</small>
                        </td>
                        <td>
                          <span className="badge bg-light text-dark">
                            {PAGE_TYPES[p.pageType] || p.pageType}
                          </span>
                        </td>
                        <td>
                          <span className={`badge bg-${p.active ? 'success' : 'danger'}`}>
                            {p.active ? 'Aktif' : 'Pasif'}
                          </span>
                        </td>
                        <td>
                          <div className="btn-group btn-group-sm">
                            <button className="btn btn-outline-primary" onClick={() => startEdit(p)}>
                              <i className="fas fa-edit" />
                            </button>
                            <button
                              className="btn btn-outline-danger"
                              onClick={() => handleDelete(p.id, p.title)}
                            >
                              <i className="fas fa-trash" />
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
