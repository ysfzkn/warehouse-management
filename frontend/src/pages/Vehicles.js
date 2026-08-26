import React, { useCallback, useEffect, useState } from 'react';
import axios from 'axios';
import confirmDialog from '../utils/confirmDialog';

/**
 * Vehicle tab of the fleet screen.
 *
 * Vehicles arrive by themselves — every transfer files its plate away — so this is mostly for
 * corrections and for registering a vehicle before its first run. Assignments to drivers are
 * edited from the driver side, where the question is actually asked ("what can this driver take?").
 */

const emptyForm = { plate: '', brandModel: '', notes: '', active: true };

const formatDate = (value) =>
  value
    ? new Date(value).toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' })
    : '—';

export default function Vehicles({ toast, askCode, canManage }) {
  const [vehicles, setVehicles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [activeOnly, setActiveOnly] = useState(false);
  const [editing, setEditing] = useState(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const fetchVehicles = useCallback(async () => {
    setLoading(true);
    try {
      const res = await axios.get('/api/admin/vehicles', {
        params: { q: search.trim() || undefined, activeOnly },
      });
      setVehicles(res.data || []);
    } catch (e) {
      toast.error(e.response?.data?.message || 'Araçlar yüklenemedi.');
    } finally {
      setLoading(false);
    }
  }, [search, activeOnly, toast]);

  useEffect(() => {
    const timer = setTimeout(fetchVehicles, 250);
    return () => clearTimeout(timer);
  }, [fetchVehicles]);

  const save = async () => {
    const plateKey = (editing.plate || '').replace(/[^A-Za-z0-9]/g, '');
    if (plateKey.length < 4) {
      setError('Geçerli bir plaka girin (en az 4 karakter).');
      return;
    }
    setSaving(true);
    setError('');
    try {
      const payload = {
        plate: editing.plate.trim(),
        brandModel: (editing.brandModel || '').trim() || null,
        notes: (editing.notes || '').trim() || null,
        active: editing.active !== false,
      };
      if (editing.id) {
        await axios.put(`/api/admin/vehicles/${editing.id}`, payload);
        toast.success('Araç güncellendi.');
      } else {
        await axios.post('/api/admin/vehicles', payload);
        toast.success('Araç eklendi.');
      }
      setEditing(null);
      fetchVehicles();
    } catch (e) {
      setError(e.response?.data?.message || 'Kaydedilemedi.');
    } finally {
      setSaving(false);
    }
  };

  const toggle = async (vehicle) => {
    try {
      await axios.put(`/api/admin/vehicles/${vehicle.id}/toggle`);
      toast.success(vehicle.active ? 'Araç pasife alındı.' : 'Araç aktifleştirildi.');
      fetchVehicles();
    } catch (e) {
      toast.error(e.response?.data?.message || 'Durum değiştirilemedi.');
    }
  };

  const remove = async (vehicle) => {
    const ok = await confirmDialog({
      title: 'Araç silinsin mi?',
      message: `${vehicle.plate} rehberden kalıcı olarak silinecek. Geçmiş transferlerdeki plaka bilgisi korunur, sadece bir daha önerilmez.`,
      confirmText: 'Evet, Sil',
      variant: 'danger',
    });
    if (!ok) return;
    const code = await askCode({
      title: 'Yönetici Güvenlik Şifresi',
      description: 'Araç silme işlemi için güvenlik şifresini girin.',
    });
    if (code === null) return;
    try {
      await axios.delete(`/api/admin/vehicles/${vehicle.id}`, {
        headers: { 'X-ADMIN-SECURITY-CODE': code },
      });
      toast.success('Araç silindi.');
      fetchVehicles();
    } catch (e) {
      toast.error(e.response?.data?.message || 'Silinemedi.');
    }
  };

  return (
    <>
      <div className="card mb-3">
        <div className="card-body py-3">
          <div className="row g-2 align-items-end">
            <div className="col-md-7">
              <label className="form-label small fw-medium mb-1">Ara</label>
              <div className="input-group">
                <span className="input-group-text bg-white">
                  <i className="fas fa-magnifying-glass text-muted" />
                </span>
                <input
                  className="form-control"
                  placeholder="Plaka veya marka/model…"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                />
                {search && (
                  <button className="btn btn-outline-secondary" onClick={() => setSearch('')}>
                    <i className="fas fa-times" />
                  </button>
                )}
              </div>
            </div>
            <div className="col-md-3">
              <div className="form-check">
                <input
                  className="form-check-input"
                  type="checkbox"
                  id="vehicleActiveOnly"
                  checked={activeOnly}
                  onChange={(e) => setActiveOnly(e.target.checked)}
                />
                <label className="form-check-label small" htmlFor="vehicleActiveOnly">
                  Sadece aktif araçlar
                </label>
              </div>
            </div>
            <div className="col-md-2 text-md-end">
              <button
                className="btn btn-primary w-100"
                onClick={() => {
                  setError('');
                  setEditing({ ...emptyForm });
                }}
              >
                <i className="fas fa-plus me-2" />
                Yeni Araç
              </button>
            </div>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="table-responsive">
          <table className="table table-hover align-middle mb-0">
            <thead className="table-light">
              <tr>
                <th>Plaka</th>
                <th>Marka / Model</th>
                <th className="text-center">Transfer</th>
                <th>Son Kullanım</th>
                <th>Durum</th>
                <th className="text-end">İşlemler</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan="6" className="text-center py-5">
                    <div className="spinner-border text-primary" />
                  </td>
                </tr>
              ) : vehicles.length === 0 ? (
                <tr>
                  <td colSpan="6" className="text-center text-muted py-5">
                    <i className="fas fa-truck-front d-block fs-2 mb-2 opacity-50" />
                    {search
                      ? 'Eşleşen araç bulunamadı.'
                      : 'Henüz araç kaydı yok. İlk transferde otomatik eklenecek.'}
                  </td>
                </tr>
              ) : (
                vehicles.map((v) => (
                  <tr key={v.id} className={v.active ? '' : 'text-muted'}>
                    <td>
                      <span className="badge bg-secondary fs-6">{v.plate}</span>
                    </td>
                    <td>
                      {v.brandModel || <span className="text-muted">—</span>}
                      {v.notes && <div className="small text-muted">{v.notes}</div>}
                    </td>
                    <td className="text-center">{v.transferCount || 0}</td>
                    <td className="small">{formatDate(v.lastUsedAt)}</td>
                    <td>
                      <span className={`badge bg-${v.active ? 'success' : 'secondary'}`}>
                        {v.active ? 'Aktif' : 'Pasif'}
                      </span>
                    </td>
                    <td className="text-end">
                      {canManage ? (
                        <div className="btn-group btn-group-sm">
                          <button
                            className="btn btn-outline-primary"
                            title="Düzenle"
                            onClick={() => {
                              setError('');
                              setEditing({ ...v });
                            }}
                          >
                            <i className="fas fa-pen" />
                          </button>
                          <button
                            className="btn btn-outline-secondary"
                            title={v.active ? 'Pasife al' : 'Aktifleştir'}
                            onClick={() => toggle(v)}
                          >
                            <i className={`fas fa-${v.active ? 'eye-slash' : 'eye'}`} />
                          </button>
                          <button className="btn btn-outline-danger" title="Sil" onClick={() => remove(v)}>
                            <i className="fas fa-trash-alt" />
                          </button>
                        </div>
                      ) : (
                        <span className="text-muted small">—</span>
                      )}
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
                  <i className="fas fa-truck-front text-primary me-2" />
                  {editing.id ? 'Aracı Düzenle' : 'Yeni Araç'}
                </h5>
                <button className="btn-close" onClick={() => setEditing(null)} disabled={saving} />
              </div>
              <div className="modal-body">
                {error && <div className="alert alert-danger py-2 small">{error}</div>}
                <div className="row g-3">
                  <div className="col-12">
                    <label className="form-label">
                      Plaka <span className="text-danger">*</span>
                    </label>
                    <input
                      className="form-control text-uppercase"
                      value={editing.plate}
                      onChange={(e) => setEditing({ ...editing, plate: e.target.value })}
                      placeholder="34 ABC 123"
                      maxLength="20"
                    />
                    <div className="form-text">
                      Boşluk ve tire dikkate alınmaz; “34 ABC 123” ile “34ABC123” aynı araçtır.
                    </div>
                  </div>
                  <div className="col-12">
                    <label className="form-label">Marka / Model</label>
                    <input
                      className="form-control"
                      value={editing.brandModel || ''}
                      onChange={(e) => setEditing({ ...editing, brandModel: e.target.value })}
                      placeholder="Örn: Ford Transit"
                      maxLength="100"
                    />
                  </div>
                  <div className="col-12">
                    <label className="form-label">Not</label>
                    <textarea
                      className="form-control"
                      rows="2"
                      value={editing.notes || ''}
                      onChange={(e) => setEditing({ ...editing, notes: e.target.value })}
                      placeholder="Kapasite, muayene tarihi…"
                      maxLength="500"
                    />
                  </div>
                  <div className="col-12">
                    <div className="form-check">
                      <input
                        className="form-check-input"
                        type="checkbox"
                        id="vehicleActive"
                        checked={editing.active !== false}
                        onChange={(e) => setEditing({ ...editing, active: e.target.checked })}
                      />
                      <label className="form-check-label" htmlFor="vehicleActive">
                        Aktif (transfer ekranında önerilsin)
                      </label>
                    </div>
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
    </>
  );
}
