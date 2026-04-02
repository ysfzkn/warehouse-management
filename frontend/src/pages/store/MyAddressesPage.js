import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { useToast } from '../../components/store/Toast';

const getAuthHeaders = () => {
  const t = localStorage.getItem('customer_token');
  return t ? { Authorization: `Bearer ${t}` } : {};
};

const EMPTY = { title: '', firstName: '', lastName: '', phone: '', city: '', district: '', neighborhood: '', addressLine: '', postalCode: '', tcKimlikNo: '', companyName: '', taxOffice: '', taxNumber: '' };

export default function MyAddressesPage() {
  const [addresses, setAddresses] = useState([]);
  const [loading, setLoading] = useState(true);
  const toast = useToast();
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState({ ...EMPTY });
  const [saving, setSaving] = useState(false);

  const fetch = useCallback(() => {
    setLoading(true);
    axios.get('/api/store/addresses', { headers: getAuthHeaders() })
      .then(r => setAddresses(r.data || []))
      .catch(() => {}).finally(() => setLoading(false));
  }, []);

  useEffect(() => { fetch(); }, [fetch]);

  const handleSave = async () => {
    setSaving(true);
    try {
      if (editing) await axios.put(`/api/store/addresses/${editing}`, form, { headers: getAuthHeaders() });
      else await axios.post('/api/store/addresses', form, { headers: getAuthHeaders() });
      setShowForm(false); setEditing(null); setForm({ ...EMPTY }); fetch();
      toast.success(editing ? 'Adres güncellendi.' : 'Adres kaydedildi.');
    } catch (e) { toast.error(e.response?.data?.message || 'Adres kaydedilemedi.'); } finally { setSaving(false); }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Bu adresi silmek istediğinize emin misiniz?')) return;
    try { await axios.delete(`/api/store/addresses/${id}`, { headers: getAuthHeaders() }); fetch(); toast.success('Adres silindi.'); }
    catch (e) { toast.error(e.response?.data?.message || 'Adres silinemedi.'); }
  };

  const handleSetDefault = async (id) => {
    try { await axios.put(`/api/store/addresses/${id}/set-default`, {}, { headers: getAuthHeaders() }); fetch(); toast.success('Varsayılan adres güncellendi.'); } catch (e) { toast.error('İşlem başarısız.'); }
  };

  const startEdit = (a) => {
    setEditing(a.id);
    setForm({ title: a.title || '', firstName: a.firstName || '', lastName: a.lastName || '', phone: a.phone || '', city: a.city || '', district: a.district || '', neighborhood: a.neighborhood || '', addressLine: a.addressLine || '', postalCode: a.postalCode || '', tcKimlikNo: a.tcKimlikNo || '', companyName: a.companyName || '', taxOffice: a.taxOffice || '', taxNumber: a.taxNumber || '' });
    setShowForm(true);
  };

  return (
    <div className="container py-4" style={{ maxWidth: 800 }}>
      <div className="d-flex justify-content-between align-items-center mb-4">
        <h2 className="fw-bold mb-0"><i className="fas fa-map-marker-alt me-2 text-primary" />Adreslerim</h2>
        <button className="btn btn-primary btn-sm" onClick={() => { setEditing(null); setForm({ ...EMPTY }); setShowForm(true); }}>
          <i className="fas fa-plus me-1" />Yeni Adres
        </button>
      </div>

      {showForm && (
        <div className="card border-0 shadow-sm mb-4">
          <div className="card-header bg-transparent d-flex justify-content-between">
            <h6 className="mb-0">{editing ? 'Adresi Düzenle' : 'Yeni Adres'}</h6>
            <button className="btn-close" onClick={() => setShowForm(false)} />
          </div>
          <div className="card-body">
            <div className="row g-3">
              <div className="col-12"><input className="form-control" placeholder="Adres Başlığı (Ev, İş...)" value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} /></div>
              <div className="col-6"><input className="form-control" placeholder="Ad" value={form.firstName} onChange={e => setForm({ ...form, firstName: e.target.value })} /></div>
              <div className="col-6"><input className="form-control" placeholder="Soyad" value={form.lastName} onChange={e => setForm({ ...form, lastName: e.target.value })} /></div>
              <div className="col-6"><input className="form-control" placeholder="Telefon" value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} /></div>
              <div className="col-6"><input className="form-control" placeholder="TC Kimlik No" maxLength={11} value={form.tcKimlikNo} onChange={e => setForm({ ...form, tcKimlikNo: e.target.value })} /></div>
              <div className="col-4"><input className="form-control" placeholder="İl" value={form.city} onChange={e => setForm({ ...form, city: e.target.value })} /></div>
              <div className="col-4"><input className="form-control" placeholder="İlçe" value={form.district} onChange={e => setForm({ ...form, district: e.target.value })} /></div>
              <div className="col-4"><input className="form-control" placeholder="Posta Kodu" value={form.postalCode} onChange={e => setForm({ ...form, postalCode: e.target.value })} /></div>
              <div className="col-12"><textarea className="form-control" rows={2} placeholder="Açık Adres" value={form.addressLine} onChange={e => setForm({ ...form, addressLine: e.target.value })} /></div>
              <div className="col-12 d-flex gap-2">
                <button className="btn btn-primary" onClick={handleSave} disabled={saving}>
                  {saving ? <span className="spinner-border spinner-border-sm me-1" /> : <i className="fas fa-save me-1" />}{editing ? 'Güncelle' : 'Kaydet'}
                </button>
                <button className="btn btn-outline-secondary" onClick={() => setShowForm(false)}>İptal</button>
              </div>
            </div>
          </div>
        </div>
      )}

      {loading ? <div className="text-center py-5"><span className="spinner-border" /></div>
      : addresses.length === 0 && !showForm ? (
        <div className="card border-0 shadow-sm"><div className="card-body text-center py-5">
          <i className="fas fa-map-marker-alt text-muted fa-3x mb-3 opacity-25" />
          <p className="text-muted">Henüz kayıtlı adresiniz yok.</p>
        </div></div>
      ) : (
        <div className="row g-3">
          {addresses.map(a => (
            <div key={a.id} className="col-md-6">
              <div className={`card border-0 shadow-sm h-100 ${a.isDefault ? 'border-start border-primary border-3' : ''}`}>
                <div className="card-body">
                  <div className="d-flex justify-content-between align-items-start mb-2">
                    <div>
                      <span className="fw-semibold">{a.title || 'Adres'}</span>
                      {a.isDefault && <span className="badge bg-primary ms-2 small">Varsayılan</span>}
                    </div>
                    <div className="btn-group btn-group-sm">
                      <button className="btn btn-outline-primary" onClick={() => startEdit(a)} title="Düzenle"><i className="fas fa-pen" /></button>
                      {!a.isDefault && <button className="btn btn-outline-success" onClick={() => handleSetDefault(a.id)} title="Varsayılan yap"><i className="fas fa-star" /></button>}
                      <button className="btn btn-outline-danger" onClick={() => handleDelete(a.id)} title="Sil"><i className="fas fa-trash" /></button>
                    </div>
                  </div>
                  <div className="small">
                    <div className="fw-medium">{a.firstName} {a.lastName}</div>
                    <div className="text-muted">{a.addressLine}</div>
                    <div className="text-muted">{a.district} / {a.city} {a.postalCode && `- ${a.postalCode}`}</div>
                    {a.phone && <div className="text-muted mt-1"><i className="fas fa-phone me-1" />{a.phone}</div>}
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
