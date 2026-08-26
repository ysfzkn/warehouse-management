import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import useSecurityCodePrompt from '../components/useSecurityCodePrompt';
import { useAdminToast } from '../components/AdminToast';
import confirmDialog from '../utils/confirmDialog';

const DISCOUNT_TYPES = {
  PERCENTAGE: 'Yüzde (%)',
  FIXED_AMOUNT: 'Sabit Tutar (₺)',
  FREE_SHIPPING: 'Ücretsiz Kargo',
};
const formatDate = (d) =>
  d
    ? new Date(d).toLocaleString('tr-TR', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '—';

const EMPTY_FORM = {
  code: '',
  description: '',
  discountType: 'PERCENTAGE',
  discountValue: '',
  minOrderAmount: '',
  maxDiscountAmount: '',
  usageLimit: '',
  usageLimitPerCustomer: '',
  startsAt: '',
  expiresAt: '',
  active: true,
};

export default function AdminCoupons() {
  const [coupons, setCoupons] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState({ ...EMPTY_FORM });
  const { askCode, SecurityCodePrompt } = useSecurityCodePrompt();
  const toast = useAdminToast();

  const fetch = useCallback(() => {
    setLoading(true);
    axios
      .get('/api/admin/coupons', { params: { size: 100 } })
      .then((r) => setCoupons(r.data?.content || []))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);
  useEffect(() => {
    fetch();
  }, [fetch]);

  const generateCode = () => {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    let code = '';
    for (let i = 0; i < 8; i++) code += chars.charAt(Math.floor(Math.random() * chars.length));
    setForm((f) => ({ ...f, code }));
  };

  const handleSave = async () => {
    if (!form.code || !form.discountValue) {
      toast.error('Kupon kodu ve indirim değeri zorunludur.');
      return;
    }
    const code = await askCode({ description: 'Kupon kaydetmek için güvenlik şifresini girin.' });
    if (!code) return;
    try {
      const data = {
        ...form,
        discountValue: parseFloat(form.discountValue) || 0,
        minOrderAmount: form.minOrderAmount ? parseFloat(form.minOrderAmount) : null,
        maxDiscountAmount: form.maxDiscountAmount ? parseFloat(form.maxDiscountAmount) : null,
        usageLimit: form.usageLimit ? parseInt(form.usageLimit) : null,
        usageLimitPerCustomer: form.usageLimitPerCustomer ? parseInt(form.usageLimitPerCustomer) : null,
        startsAt: form.startsAt ? form.startsAt + ':00' : null,
        expiresAt: form.expiresAt ? form.expiresAt + ':00' : null,
      };
      const headers = { 'X-ADMIN-SECURITY-CODE': code };
      if (editing) await axios.put(`/api/admin/coupons/${editing}`, data, { headers });
      else await axios.post('/api/admin/coupons', data, { headers });
      setShowForm(false);
      setEditing(null);
      setForm({ ...EMPTY_FORM });
      fetch();
      toast.success('Kupon başarıyla kaydedildi.');
    } catch (e) {
      if (e.response?.status === 403) toast.error('Güvenlik şifresi hatalı.');
      else toast.error('İşlem sırasında hata oluştu.');
    }
  };

  const handleDelete = async (c) => {
    const ok = await confirmDialog({
      title: 'Kupon Silinsin mi?',
      message: `"${c.code}" kuponu kalıcı olarak silinecek.`,
      confirmText: 'Evet, Sil',
    });
    if (!ok) return;
    const code = await askCode({ description: 'Kupon silmek için güvenlik şifresini girin.' });
    if (!code) return;
    try {
      await axios.delete(`/api/admin/coupons/${c.id}`, { headers: { 'X-ADMIN-SECURITY-CODE': code } });
      fetch();
    } catch {
      toast.error('İşlem sırasında hata oluştu.');
    }
  };

  const startEdit = (c) => {
    setEditing(c.id);
    setForm({
      code: c.code,
      description: c.description || '',
      discountType: c.discountType,
      discountValue: c.discountValue || '',
      minOrderAmount: c.minOrderAmount || '',
      maxDiscountAmount: c.maxDiscountAmount || '',
      usageLimit: c.usageLimit || '',
      usageLimitPerCustomer: c.usageLimitPerCustomer || '',
      startsAt: c.startsAt ? c.startsAt.substring(0, 16) : '',
      expiresAt: c.expiresAt ? c.expiresAt.substring(0, 16) : '',
      active: c.active,
    });
    setShowForm(true);
  };

  return (
    <div>
      {SecurityCodePrompt}
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="mb-0">Kupon Yönetimi</h2>
          <p className="text-muted small mb-0">İndirim kuponları oluşturun ve yönetin</p>
        </div>
        <button
          className="btn btn-primary"
          onClick={() => {
            setEditing(null);
            setForm({ ...EMPTY_FORM });
            setShowForm(true);
          }}
        >
          <i className="fas fa-plus me-2" />
          Yeni Kupon
        </button>
      </div>

      <div className="row g-4">
        {showForm && (
          <div className="col-lg-5">
            <div className="card border-0 shadow-sm">
              <div className="card-header bg-transparent d-flex justify-content-between">
                <h6 className="mb-0">{editing ? 'Kupon Düzenle' : 'Yeni Kupon'}</h6>
                <button className="btn-close" onClick={() => setShowForm(false)} />
              </div>
              <div className="card-body">
                <div className="row g-3">
                  <div className="col-8">
                    <label className="form-label small fw-semibold">Kupon Kodu</label>
                    <input
                      className="form-control font-monospace text-uppercase"
                      value={form.code}
                      onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })}
                      placeholder="SUMMER20"
                      maxLength={50}
                    />
                  </div>
                  <div className="col-4 d-flex align-items-end">
                    <button className="btn btn-outline-secondary w-100" onClick={generateCode} type="button">
                      <i className="fas fa-random me-1" />
                      Oluştur
                    </button>
                  </div>
                  <div className="col-12">
                    <label className="form-label small fw-semibold">Açıklama</label>
                    <input
                      className="form-control"
                      value={form.description}
                      onChange={(e) => setForm({ ...form, description: e.target.value })}
                      placeholder="Yaz kampanyası %20 indirim"
                    />
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small fw-semibold">İndirim Tipi</label>
                    <select
                      className="form-select"
                      value={form.discountType}
                      onChange={(e) => setForm({ ...form, discountType: e.target.value })}
                    >
                      {Object.entries(DISCOUNT_TYPES).map(([k, v]) => (
                        <option key={k} value={k}>
                          {v}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small fw-semibold">İndirim Değeri</label>
                    <div className="input-group">
                      <input
                        type="number"
                        step="0.01"
                        className="form-control"
                        value={form.discountValue}
                        onChange={(e) => setForm({ ...form, discountValue: e.target.value })}
                        placeholder={form.discountType === 'PERCENTAGE' ? '20' : '50.00'}
                      />
                      <span className="input-group-text">
                        {form.discountType === 'PERCENTAGE' ? '%' : '₺'}
                      </span>
                    </div>
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small fw-semibold">Min. Sipariş Tutarı</label>
                    <div className="input-group">
                      <input
                        type="number"
                        step="0.01"
                        className="form-control"
                        value={form.minOrderAmount}
                        onChange={(e) => setForm({ ...form, minOrderAmount: e.target.value })}
                        placeholder="100"
                      />
                      <span className="input-group-text">₺</span>
                    </div>
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small fw-semibold">Max. İndirim Tutarı</label>
                    <div className="input-group">
                      <input
                        type="number"
                        step="0.01"
                        className="form-control"
                        value={form.maxDiscountAmount}
                        onChange={(e) => setForm({ ...form, maxDiscountAmount: e.target.value })}
                        placeholder="200"
                      />
                      <span className="input-group-text">₺</span>
                    </div>
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small fw-semibold">Toplam Kullanım Limiti</label>
                    <input
                      type="number"
                      className="form-control"
                      value={form.usageLimit}
                      onChange={(e) => setForm({ ...form, usageLimit: e.target.value })}
                      placeholder="Sınırsız"
                    />
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small fw-semibold">Kişi Başı Limit</label>
                    <input
                      type="number"
                      className="form-control"
                      value={form.usageLimitPerCustomer}
                      onChange={(e) => setForm({ ...form, usageLimitPerCustomer: e.target.value })}
                      placeholder="1"
                    />
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small fw-semibold">Başlangıç</label>
                    <input
                      type="datetime-local"
                      className="form-control"
                      value={form.startsAt}
                      onChange={(e) => setForm({ ...form, startsAt: e.target.value })}
                    />
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small fw-semibold">Bitiş</label>
                    <input
                      type="datetime-local"
                      className="form-control"
                      value={form.expiresAt}
                      onChange={(e) => setForm({ ...form, expiresAt: e.target.value })}
                    />
                  </div>
                  <div className="col-12">
                    <div className="form-check form-switch">
                      <input
                        className="form-check-input"
                        type="checkbox"
                        checked={form.active}
                        onChange={(e) => setForm({ ...form, active: e.target.checked })}
                        id="couponActive"
                      />
                      <label className="form-check-label" htmlFor="couponActive">
                        Aktif
                      </label>
                    </div>
                  </div>
                  <div className="col-12 d-flex gap-2">
                    <button className="btn btn-primary" onClick={handleSave}>
                      <i className="fas fa-save me-1" />
                      {editing ? 'Güncelle' : 'Oluştur'}
                    </button>
                    <button className="btn btn-outline-secondary" onClick={() => setShowForm(false)}>
                      İptal
                    </button>
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
                <div className="text-center py-5">
                  <span className="spinner-border spinner-border-sm" />
                </div>
              ) : coupons.length === 0 ? (
                <div className="text-center py-5">
                  <i className="fas fa-ticket-alt text-muted fa-3x mb-3 d-block opacity-25" />
                  <p className="text-muted mb-3">Henüz kupon oluşturulmamış.</p>
                  <button
                    className="btn btn-sm btn-primary"
                    onClick={() => {
                      setForm({ ...EMPTY_FORM });
                      setShowForm(true);
                    }}
                  >
                    <i className="fas fa-plus me-1" />
                    İlk Kuponu Oluşturun
                  </button>
                </div>
              ) : (
                <div className="table-responsive">
                  <table className="table table-hover align-middle mb-0">
                    <thead className="table-light">
                      <tr>
                        <th>Kod</th>
                        <th>İndirim</th>
                        <th>Kullanım</th>
                        <th>Geçerlilik</th>
                        <th>Durum</th>
                        <th style={{ width: 100 }}></th>
                      </tr>
                    </thead>
                    <tbody>
                      {coupons.map((c) => (
                        <tr key={c.id}>
                          <td>
                            <code className="fw-bold">{c.code}</code>
                            {c.description && <div className="text-muted small">{c.description}</div>}
                          </td>
                          <td>
                            <span className="badge bg-primary bg-opacity-10 text-primary">
                              {c.discountType === 'PERCENTAGE'
                                ? `%${c.discountValue}`
                                : c.discountType === 'FREE_SHIPPING'
                                  ? 'Ücretsiz Kargo'
                                  : `${c.discountValue}₺`}
                            </span>
                            {c.minOrderAmount > 0 && (
                              <div className="text-muted small">Min: {c.minOrderAmount}₺</div>
                            )}
                          </td>
                          <td>
                            <span className="small">
                              {c.usedCount || 0}
                              {c.usageLimit ? `/${c.usageLimit}` : ''}
                            </span>
                            {c.usageLimit && (c.usedCount || 0) >= c.usageLimit && (
                              <span className="badge bg-danger ms-2">Tükendi</span>
                            )}
                            {c.usageLimitPerCustomer && (
                              <div className="text-muted" style={{ fontSize: 11 }}>
                                Kişi başı {c.usageLimitPerCustomer}
                              </div>
                            )}
                          </td>
                          <td>
                            <div className="small">{formatDate(c.startsAt)}</div>
                            <div className="small text-muted">{formatDate(c.expiresAt)}</div>
                          </td>
                          <td>
                            <span className={`badge bg-${c.active ? 'success' : 'secondary'}`}>
                              {c.active ? 'Aktif' : 'Pasif'}
                            </span>
                          </td>
                          <td>
                            <div className="btn-group btn-group-sm">
                              <button className="btn btn-outline-primary" onClick={() => startEdit(c)}>
                                <i className="fas fa-edit" />
                              </button>
                              <button className="btn btn-outline-danger" onClick={() => handleDelete(c)}>
                                <i className="fas fa-trash" />
                              </button>
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
        </div>
      </div>
    </div>
  );
}
