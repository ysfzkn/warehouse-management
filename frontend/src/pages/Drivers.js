import React, { useCallback, useEffect, useState } from 'react';
import axios from 'axios';
import { useAdminToast } from '../components/AdminToast';
import useSecurityCodePrompt from '../components/useSecurityCodePrompt';
import confirmDialog from '../utils/confirmDialog';
import { toTitleCaseTr } from '../utils/name';
import { formatPhoneInputValue, formatPhoneForSubmit, PHONE_PLACEHOLDER } from '../utils/phone';

/**
 * Driver directory.
 *
 * The list fills itself: every transfer files its driver away, ranked by how often they drive.
 * This screen is for the corrections that follow — a mistyped plate, a duplicate created before
 * the directory existed, a driver who no longer works here.
 */

const emptyForm = { name: '', tcId: '', phone: '', vehiclePlate: '', notes: '', active: true };

const formatDate = (value) =>
  value
    ? new Date(value).toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' })
    : '—';

export default function Drivers() {
  const toast = useAdminToast();
  const { askCode, SecurityCodePrompt } = useSecurityCodePrompt();

  const [drivers, setDrivers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [activeOnly, setActiveOnly] = useState(false);
  const [editing, setEditing] = useState(null); // null | { id?, ...form }
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState({});

  const fetchDrivers = useCallback(async () => {
    setLoading(true);
    try {
      const res = await axios.get('/api/admin/drivers', {
        params: { q: search.trim() || undefined, activeOnly },
      });
      setDrivers(res.data || []);
    } catch (e) {
      toast.error(e.response?.data?.message || 'Şoförler yüklenemedi.');
    } finally {
      setLoading(false);
    }
  }, [search, activeOnly, toast]);

  useEffect(() => {
    // Debounced so typing in the search box does not fire a request per keystroke.
    const timer = setTimeout(fetchDrivers, 250);
    return () => clearTimeout(timer);
  }, [fetchDrivers]);

  const validate = (form) => {
    const next = {};
    if (!form.name || form.name.trim().length < 3) next.name = 'Ad soyad en az 3 karakter olmalı';
    const digits = (form.tcId || '').replace(/\D/g, '');
    if (digits && digits.length !== 11) next.tcId = 'TC kimlik no 11 haneli olmalı';
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const save = async () => {
    if (!validate(editing)) return;
    setSaving(true);
    try {
      const payload = {
        name: editing.name.trim(),
        tcId: (editing.tcId || '').replace(/\D/g, '') || null,
        phone: formatPhoneForSubmit(editing.phone) || null,
        vehiclePlate: (editing.vehiclePlate || '').trim().toUpperCase() || null,
        notes: (editing.notes || '').trim() || null,
        active: editing.active !== false,
      };
      if (editing.id) {
        await axios.put(`/api/admin/drivers/${editing.id}`, payload);
        toast.success('Şoför güncellendi.');
      } else {
        await axios.post('/api/admin/drivers', payload);
        toast.success('Şoför eklendi.');
      }
      setEditing(null);
      fetchDrivers();
    } catch (e) {
      toast.error(e.response?.data?.message || 'Kaydedilemedi.');
    } finally {
      setSaving(false);
    }
  };

  const toggle = async (driver) => {
    try {
      await axios.put(`/api/admin/drivers/${driver.id}/toggle`);
      toast.success(driver.active ? 'Şoför pasife alındı.' : 'Şoför aktifleştirildi.');
      fetchDrivers();
    } catch (e) {
      toast.error(e.response?.data?.message || 'Durum değiştirilemedi.');
    }
  };

  const remove = async (driver) => {
    const ok = await confirmDialog({
      title: 'Şoför silinsin mi?',
      message: `${driver.name} rehberden kalıcı olarak silinecek. Geçmiş transferlerdeki şoför bilgileri korunur, sadece bir daha önerilmez.`,
      confirmText: 'Evet, Sil',
      variant: 'danger',
    });
    if (!ok) return;
    const code = await askCode({
      title: 'Yönetici Güvenlik Şifresi',
      description: 'Şoför silme işlemi için güvenlik şifresini girin.',
    });
    if (code === null) return;
    try {
      await axios.delete(`/api/admin/drivers/${driver.id}`, {
        headers: { 'X-ADMIN-SECURITY-CODE': code },
      });
      toast.success('Şoför silindi.');
      fetchDrivers();
    } catch (e) {
      toast.error(e.response?.data?.message || 'Silinemedi.');
    }
  };

  return (
    <div>
      {SecurityCodePrompt}

      <div className="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
        <div>
          <h4 className="fw-bold mb-1">
            <i className="fas fa-id-card text-primary me-2" />
            Şoförler
          </h4>
          <p className="text-muted small mb-0">
            Transfer kaydedildikçe rehber kendi kendine dolar; buradan düzeltebilir, pasife alabilir veya
            silebilirsiniz.
          </p>
        </div>
        <button
          className="btn btn-primary"
          onClick={() => {
            setErrors({});
            setEditing({ ...emptyForm });
          }}
        >
          <i className="fas fa-plus me-2" />
          Yeni Şoför
        </button>
      </div>

      <div className="card mb-3">
        <div className="card-body py-3">
          <div className="row g-2 align-items-end">
            <div className="col-md-8">
              <label className="form-label small fw-medium mb-1">Ara</label>
              <div className="input-group">
                <span className="input-group-text bg-white">
                  <i className="fas fa-magnifying-glass text-muted" />
                </span>
                <input
                  className="form-control"
                  placeholder="Ad, telefon, TC veya plaka…"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                />
                {search && (
                  <button className="btn btn-outline-secondary" onClick={() => setSearch('')}>
                    <i className="fas fa-times" />
                  </button>
                )}
              </div>
              <div className="form-text">
                Türkçe karakter farkı gözetilmez — “Ballı” araması “Balli” kaydını da bulur.
              </div>
            </div>
            <div className="col-md-4">
              <div className="form-check">
                <input
                  className="form-check-input"
                  type="checkbox"
                  id="activeOnly"
                  checked={activeOnly}
                  onChange={(e) => setActiveOnly(e.target.checked)}
                />
                <label className="form-check-label small" htmlFor="activeOnly">
                  Sadece aktif şoförler
                </label>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="table-responsive">
          <table className="table table-hover align-middle mb-0">
            <thead className="table-light">
              <tr>
                <th>Şoför</th>
                <th>Telefon</th>
                <th>TC Kimlik</th>
                <th>Plaka</th>
                <th className="text-center">Transfer</th>
                <th>Son Kullanım</th>
                <th>Durum</th>
                <th className="text-end">İşlemler</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan="8" className="text-center py-5">
                    <div className="spinner-border text-primary" />
                  </td>
                </tr>
              ) : drivers.length === 0 ? (
                <tr>
                  <td colSpan="8" className="text-center text-muted py-5">
                    <i className="fas fa-id-card d-block fs-2 mb-2 opacity-50" />
                    {search
                      ? 'Eşleşen şoför bulunamadı.'
                      : 'Henüz şoför kaydı yok. İlk transferde otomatik eklenecek.'}
                  </td>
                </tr>
              ) : (
                drivers.map((d) => (
                  <tr key={d.id} className={d.active ? '' : 'text-muted'}>
                    <td>
                      <div className="fw-semibold">{d.name}</div>
                      {d.notes && <small className="text-muted">{d.notes}</small>}
                    </td>
                    <td>{d.phone ? formatPhoneInputValue(d.phone) : '—'}</td>
                    <td>{d.tcId || '—'}</td>
                    <td>
                      {d.vehiclePlate ? <span className="badge bg-secondary">{d.vehiclePlate}</span> : '—'}
                    </td>
                    <td className="text-center">{d.transferCount || 0}</td>
                    <td className="small">{formatDate(d.lastUsedAt)}</td>
                    <td>
                      <span className={`badge bg-${d.active ? 'success' : 'secondary'}`}>
                        {d.active ? 'Aktif' : 'Pasif'}
                      </span>
                    </td>
                    <td className="text-end">
                      <div className="btn-group btn-group-sm">
                        <button
                          className="btn btn-outline-primary"
                          title="Düzenle"
                          onClick={() => {
                            setErrors({});
                            setEditing({ ...d, phone: formatPhoneInputValue(d.phone || '') });
                          }}
                        >
                          <i className="fas fa-pen" />
                        </button>
                        <button
                          className="btn btn-outline-secondary"
                          title={d.active ? 'Pasife al' : 'Aktifleştir'}
                          onClick={() => toggle(d)}
                        >
                          <i className={`fas fa-${d.active ? 'eye-slash' : 'eye'}`} />
                        </button>
                        <button className="btn btn-outline-danger" title="Sil" onClick={() => remove(d)}>
                          <i className="fas fa-trash-alt" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {editing && (
        <div className="modal show d-block" style={{ background: 'rgba(15,23,42,0.6)', zIndex: 3000 }}>
          {/* No backdrop dismissal — a stray click must not discard a half-filled form. */}
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content border-0 shadow" style={{ borderRadius: 14 }}>
              <div className="modal-header">
                <h5 className="modal-title fw-bold">
                  <i className="fas fa-id-card text-primary me-2" />
                  {editing.id ? 'Şoförü Düzenle' : 'Yeni Şoför'}
                </h5>
                <button className="btn-close" onClick={() => setEditing(null)} disabled={saving} />
              </div>
              <div className="modal-body">
                <div className="row g-3">
                  <div className="col-12">
                    <label className="form-label">
                      Ad Soyad <span className="text-danger">*</span>
                    </label>
                    <input
                      className={`form-control ${errors.name ? 'is-invalid' : ''}`}
                      value={editing.name}
                      onChange={(e) => setEditing({ ...editing, name: e.target.value })}
                      onBlur={(e) => setEditing((prev) => ({ ...prev, name: toTitleCaseTr(e.target.value) }))}
                      placeholder="Örn: Ahmet Yılmaz"
                    />
                    {errors.name && <div className="invalid-feedback">{errors.name}</div>}
                  </div>
                  <div className="col-md-6">
                    <label className="form-label">Telefon</label>
                    <div className="input-group">
                      <span className="input-group-text">+90</span>
                      <input
                        className="form-control"
                        value={formatPhoneInputValue(editing.phone || '')}
                        onChange={(e) => setEditing({ ...editing, phone: e.target.value })}
                        placeholder={PHONE_PLACEHOLDER}
                        maxLength="13"
                      />
                    </div>
                    <div className="form-text">Şoförü tanımlayan alan; aynı numara iki kez eklenemez.</div>
                  </div>
                  <div className="col-md-6">
                    <label className="form-label">TC Kimlik No</label>
                    <input
                      className={`form-control ${errors.tcId ? 'is-invalid' : ''}`}
                      value={editing.tcId || ''}
                      onChange={(e) =>
                        setEditing({ ...editing, tcId: e.target.value.replace(/\D/g, '').slice(0, 11) })
                      }
                      placeholder="11 haneli"
                      maxLength="11"
                    />
                    {errors.tcId && <div className="invalid-feedback">{errors.tcId}</div>}
                  </div>
                  <div className="col-md-6">
                    <label className="form-label">Plaka</label>
                    <input
                      className="form-control text-uppercase"
                      value={editing.vehiclePlate || ''}
                      onChange={(e) => setEditing({ ...editing, vehiclePlate: e.target.value })}
                      placeholder="34 ABC 123"
                      maxLength="20"
                    />
                  </div>
                  <div className="col-md-6 d-flex align-items-end">
                    <div className="form-check">
                      <input
                        className="form-check-input"
                        type="checkbox"
                        id="driverActive"
                        checked={editing.active !== false}
                        onChange={(e) => setEditing({ ...editing, active: e.target.checked })}
                      />
                      <label className="form-check-label" htmlFor="driverActive">
                        Aktif (transfer ekranında önerilsin)
                      </label>
                    </div>
                  </div>
                  <div className="col-12">
                    <label className="form-label">Not</label>
                    <textarea
                      className="form-control"
                      rows="2"
                      value={editing.notes || ''}
                      onChange={(e) => setEditing({ ...editing, notes: e.target.value })}
                      placeholder="Araç tipi, çalışma bölgesi…"
                      maxLength="500"
                    />
                  </div>
                </div>
              </div>
              <div className="modal-footer">
                <button
                  className="btn btn-outline-secondary"
                  onClick={() => setEditing(null)}
                  disabled={saving}
                >
                  Vazgeç
                </button>
                <button className="btn btn-primary px-4" onClick={save} disabled={saving}>
                  {saving ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-2" />
                      Kaydediliyor…
                    </>
                  ) : (
                    <>
                      <i className="fas fa-check me-2" />
                      Kaydet
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
