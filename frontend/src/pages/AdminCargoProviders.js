import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import useSecurityCodePrompt from '../components/useSecurityCodePrompt';
import { useAdminToast } from '../components/AdminToast';

const EMPTY_FORM = {
  name: '', code: '', logoUrl: '', baseCost: 29.99, costPerDesi: 2.00,
  freeShippingThreshold: 500, estimatedDeliveryDays: 3, vatRate: 20,
  trackingUrlTemplate: '', kargonomiSlug: '', active: true, sortOrder: 100,
};

const KARGONOMI_SLUG_HINTS = [
  { slug: 'yurtici', label: 'Yurtiçi Kargo' },
  { slug: 'aras',    label: 'Aras Kargo' },
  { slug: 'mng',     label: 'MNG Kargo' },
  { slug: 'ptt',     label: 'PTT Kargo' },
  { slug: 'surat',   label: 'Sürat Kargo' },
  { slug: 'ups',     label: 'UPS' },
  { slug: 'sendeo',  label: 'Sendeo' },
  { slug: 'bolt',    label: 'Bolt' },
];

const fmt = (v) => v != null ? new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(v) : '—';

export default function AdminCargoProviders() {
  const [providers, setProviders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState({ ...EMPTY_FORM });
  const { askCode, SecurityCodePrompt } = useSecurityCodePrompt();
  const toast = useAdminToast();

  const fetchProviders = useCallback(() => {
    setLoading(true);
    axios.get('/api/admin/cargo-providers').then(r => setProviders(r.data || []))
      .catch(() => {}).finally(() => setLoading(false));
  }, []);

  useEffect(() => { fetchProviders(); }, [fetchProviders]);

  const handleSave = async () => {
    try {
      if (editing) {
        await axios.put(`/api/admin/cargo-providers/${editing}`, form);
      } else {
        await axios.post('/api/admin/cargo-providers', form);
      }
      setShowForm(false); setEditing(null); setForm({ ...EMPTY_FORM }); fetchProviders();
      toast.success('İşlem başarılı.');
    } catch (e) { toast.error(e.response?.data?.message || 'İşlem sırasında hata oluştu.'); }
  };

  const handleToggle = async (p) => {
    try {
      await axios.put(`/api/admin/cargo-providers/${p.id}/toggle`);
      fetchProviders();
    } catch (e) { toast.error('İşlem başarısız.'); }
  };

  const handleDelete = async (p) => {
    if (!window.confirm(`"${p.name}" silinecek. Emin misiniz?`)) return;
    const code = await askCode({ description: `${p.name} silmek için güvenlik şifresini girin.` });
    if (!code) return;
    try {
      await axios.delete(`/api/admin/cargo-providers/${p.id}`, { headers: { 'X-ADMIN-SECURITY-CODE': code } });
      fetchProviders(); toast.success('İşlem başarılı.');
    } catch (e) { toast.error(e.response?.status === 403 ? 'Güvenlik şifresi hatalı.' : 'İşlem sırasında hata oluştu.'); }
  };

  const startEdit = (p) => { setEditing(p.id); setForm({ ...p }); setShowForm(true); };
  const startCreate = () => { setEditing(null); setForm({ ...EMPTY_FORM }); setShowForm(true); };

  const f = (key, val) => setForm(prev => ({ ...prev, [key]: val }));

  return (
    <div>
      {SecurityCodePrompt}
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="mb-1">Kargo Ayarları</h2>
          <p className="text-muted small mb-0">Anlaşmalı kargo firmalarını, fiyatlandırmayı ve kargo kurallarını yönetin</p>
        </div>
        <button className="btn btn-primary" onClick={startCreate}>
          <i className="fas fa-plus me-2" />Yeni Kargo Firması
        </button>
      </div>

      <div className="row g-4">
        {showForm && (
          <div className="col-lg-5">
            <div className="card border-0 shadow-sm">
              <div className="card-header bg-transparent d-flex justify-content-between">
                <h6 className="mb-0">{editing ? 'Kargo Firması Düzenle' : 'Yeni Kargo Firması'}</h6>
                <button className="btn-close" onClick={() => setShowForm(false)} />
              </div>
              <div className="card-body">
                <div className="row g-3">
                  <div className="col-md-8">
                    <label className="form-label small fw-medium">Firma Adı <span className="text-danger">*</span></label>
                    <input className="form-control" value={form.name} onChange={e => f('name', e.target.value)} placeholder="Yurtiçi Kargo" />
                  </div>
                  <div className="col-md-4">
                    <label className="form-label small fw-medium">Kod <span className="text-danger">*</span></label>
                    <input className="form-control font-monospace" value={form.code} onChange={e => f('code', e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, ''))} placeholder="YURTICI" disabled={!!editing} />
                  </div>

                  <div className="col-12">
                    <label className="form-label small fw-medium">Logo URL</label>
                    <input className="form-control small" value={form.logoUrl || ''} onChange={e => f('logoUrl', e.target.value)} placeholder="https://..." />
                  </div>

                  <div className="col-12"><hr className="my-1" /><h6 className="small fw-bold text-muted mt-2">Fiyatlandırma</h6></div>

                  <div className="col-md-4">
                    <label className="form-label small fw-medium">Temel Ücret (₺)</label>
                    <input type="number" step="0.01" className="form-control" value={form.baseCost} onChange={e => f('baseCost', parseFloat(e.target.value) || 0)} />
                    <small className="text-muted">Her gönderideki sabit ücret</small>
                  </div>
                  <div className="col-md-4">
                    <label className="form-label small fw-medium">Desi Ücreti (₺)</label>
                    <input type="number" step="0.01" className="form-control" value={form.costPerDesi} onChange={e => f('costPerDesi', parseFloat(e.target.value) || 0)} />
                    <small className="text-muted">Desi başına ek ücret</small>
                  </div>
                  <div className="col-md-4">
                    <label className="form-label small fw-medium">KDV Oranı (%)</label>
                    <input type="number" step="0.01" className="form-control" value={form.vatRate} onChange={e => f('vatRate', parseFloat(e.target.value) || 0)} />
                  </div>

                  <div className="col-md-6">
                    <label className="form-label small fw-medium">Ücretsiz Kargo Alt Limiti (₺)</label>
                    <input type="number" step="0.01" className="form-control" value={form.freeShippingThreshold} onChange={e => f('freeShippingThreshold', parseFloat(e.target.value) || 0)} />
                    <small className="text-muted">Bu tutarın üzerinde kargo ücretsiz</small>
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small fw-medium">Tahmini Teslimat (gün)</label>
                    <input type="number" className="form-control" value={form.estimatedDeliveryDays} onChange={e => f('estimatedDeliveryDays', parseInt(e.target.value) || 1)} min="1" max="30" />
                  </div>

                  <div className="col-12"><hr className="my-1" /><h6 className="small fw-bold text-muted mt-2">Takip ve Sıralama</h6></div>

                  <div className="col-12">
                    <label className="form-label small fw-medium">Takip URL Şablonu</label>
                    <input className="form-control small font-monospace" value={form.trackingUrlTemplate || ''} onChange={e => f('trackingUrlTemplate', e.target.value)}
                      placeholder="https://www.yurticikargo.com/...?code={trackingNo}" />
                    <small className="text-muted"><code>{'{trackingNo}'}</code> kısmı otomatik takip numarasıyla değiştirilir</small>
                  </div>

                  <div className="col-12">
                    <label className="form-label small fw-medium d-flex align-items-center justify-content-between">
                      <span>Kargonomi Slug <small className="text-muted fw-normal">(opsiyonel)</small></span>
                      <small className="text-muted fw-normal">
                        <i className="fas fa-info-circle me-1"></i>
                        Müşteri bu firmayı seçince Kargonomi'ye bu slug ile iletilir
                      </small>
                    </label>
                    <input className="form-control small font-monospace" value={form.kargonomiSlug || ''}
                      onChange={e => f('kargonomiSlug', e.target.value.toLowerCase().trim())}
                      placeholder="yurtici / aras / mng / ptt / ..." list="kargonomi-slug-list" />
                    <datalist id="kargonomi-slug-list">
                      {KARGONOMI_SLUG_HINTS.map(o => (
                        <option key={o.slug} value={o.slug}>{o.label}</option>
                      ))}
                    </datalist>
                    <small className="text-muted">
                      Boş bırakılırsa Kargonomi <strong>otomatik en ucuz</strong> taşıyıcıyı seçer.
                      Doluysa spesifik olarak bu taşıyıcı ile gönderilir. Kargonomi hesabınızda hangi
                      carrier'ların aktif olduğunu <a href="https://app.kargonomi.com.tr" target="_blank" rel="noreferrer">paneldeki Taşıyıcılar</a> sekmesinden kontrol edin.
                    </small>
                  </div>

                  <div className="col-md-6">
                    <label className="form-label small fw-medium">Sıralama</label>
                    <input type="number" className="form-control" value={form.sortOrder} onChange={e => f('sortOrder', parseInt(e.target.value) || 100)} min="1" />
                  </div>
                  <div className="col-md-6 d-flex align-items-end">
                    <div className="form-check form-switch">
                      <input className="form-check-input" type="checkbox" checked={form.active} onChange={e => f('active', e.target.checked)} id="activeSwitch" />
                      <label className="form-check-label small" htmlFor="activeSwitch">Aktif</label>
                    </div>
                  </div>

                  {/* Preview */}
                  <div className="col-12">
                    <div className="alert alert-info small mb-0">
                      <strong>Fiyat Önizleme:</strong> 1 desi gönderim = {fmt(form.baseCost + form.costPerDesi)} + KDV {fmt((form.baseCost + form.costPerDesi) * form.vatRate / 100)} = <strong>{fmt((form.baseCost + form.costPerDesi) * (1 + form.vatRate / 100))}</strong>
                      {form.freeShippingThreshold > 0 && <span> | {fmt(form.freeShippingThreshold)} üzeri <strong>ücretsiz</strong></span>}
                    </div>
                  </div>

                  <div className="col-12 d-flex gap-2">
                    <button className="btn btn-primary" onClick={handleSave}>
                      <i className="fas fa-save me-1" />{editing ? 'Güncelle' : 'Oluştur'}
                    </button>
                    <button className="btn btn-outline-secondary" onClick={() => setShowForm(false)}>İptal</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        <div className={showForm ? 'col-lg-7' : 'col-12'}>
          <div className="card border-0 shadow-sm">
            <div className="card-body p-0">
              {loading ? (
                <div className="text-center py-5"><span className="spinner-border spinner-border-sm" /></div>
              ) : providers.length === 0 ? (
                <div className="text-center py-5">
                  <i className="fas fa-truck text-muted fa-3x mb-3 d-block opacity-25" />
                  <p className="text-muted mb-2">Henüz kargo firması eklenmemiş.</p>
                  <button className="btn btn-sm btn-primary" onClick={startCreate}>
                    <i className="fas fa-plus me-1" />İlk Kargo Firmasını Ekleyin
                  </button>
                </div>
              ) : (
                <div className="table-responsive">
                  <table className="table table-hover align-middle mb-0">
                    <thead className="table-light">
                      <tr>
                        <th>Firma</th>
                        <th>Temel Ücret</th>
                        <th>Desi Ücreti</th>
                        <th>Ücretsiz Limit</th>
                        <th>Teslimat</th>
                        <th>Durum</th>
                        <th style={{ width: 140 }}>İşlemler</th>
                      </tr>
                    </thead>
                    <tbody>
                      {providers.map(p => (
                        <tr key={p.id}>
                          <td>
                            <div className="fw-medium">{p.name}</div>
                            <code className="small text-muted">{p.code}</code>
                          </td>
                          <td>{fmt(p.baseCost)}</td>
                          <td>{fmt(p.costPerDesi)}<small className="text-muted">/desi</small></td>
                          <td>{p.freeShippingThreshold > 0 ? fmt(p.freeShippingThreshold) : '—'}</td>
                          <td><span className="badge bg-light text-dark">{p.estimatedDeliveryDays} gün</span></td>
                          <td>
                            <span className={`badge bg-${p.active ? 'success' : 'secondary'}`}>
                              {p.active ? 'Aktif' : 'Pasif'}
                            </span>
                          </td>
                          <td>
                            <div className="btn-group btn-group-sm">
                              <button className="btn btn-outline-primary" onClick={() => startEdit(p)} title="Düzenle"><i className="fas fa-edit" /></button>
                              <button className={`btn btn-outline-${p.active ? 'warning' : 'success'}`} onClick={() => handleToggle(p)} title={p.active ? 'Deaktif' : 'Aktif'}>
                                <i className={`fas fa-${p.active ? 'pause' : 'play'}`} />
                              </button>
                              <button className="btn btn-outline-danger" onClick={() => handleDelete(p)} title="Sil"><i className="fas fa-trash" /></button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>

          {/* Info box */}
          <div className="card border-0 shadow-sm mt-4">
            <div className="card-body small">
              <h6 className="mb-3"><i className="fas fa-info-circle me-2 text-primary" />Kargo Fiyat Hesaplama</h6>
              <div className="row g-2">
                <div className="col-md-6"><i className="fas fa-calculator text-primary me-2" /><strong>Formül:</strong> Temel Ücret + (Desi x Desi Ücreti)</div>
                <div className="col-md-6"><i className="fas fa-box text-primary me-2" /><strong>Desi:</strong> max(Ağırlık, Hacimsel) — Hacimsel = (En x Boy x Yükseklik) / 3000</div>
                <div className="col-md-6"><i className="fas fa-percent text-primary me-2" /><strong>KDV:</strong> Kargo ücretine ayrıca KDV eklenir</div>
                <div className="col-md-6"><i className="fas fa-gift text-primary me-2" /><strong>Ücretsiz:</strong> Sepet toplamı limiti aşarsa kargo ücretsiz</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
