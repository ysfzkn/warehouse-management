import React, { useEffect, useMemo, useState } from 'react';
import axios from 'axios';

const CrudTable = ({ title, columns, items, onCreate, onEdit, onDelete, loading }) => (
  <div className="card mb-4">
    <div className="card-header d-flex justify-content-between align-items-center">
      <h5 className="mb-0">{title}</h5>
      <button className="btn btn-primary" onClick={onCreate}>
        <i className="fas fa-plus me-2"></i>Yeni Ekle
      </button>
    </div>
    <div className="card-body p-0">
      <div className="table-responsive">
        <table className="table table-hover align-middle mb-0">
          <thead>
            <tr>
              {columns.map(c => <th key={c.key}>{c.title}</th>)}
              <th style={{ width: 120 }}>İşlemler</th>
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr>
                <td colSpan={columns.length + 1} className="text-center py-4">
                  <span className="spinner-border" role="status" aria-hidden="true"></span>
                </td>
              </tr>
            )}
            {!loading && items.length === 0 && (
              <tr>
                <td colSpan={columns.length + 1} className="text-center py-4 text-muted">Kayıt bulunamadı</td>
              </tr>
            )}
            {!loading && items.map(item => (
              <tr key={item.id}>
                {columns.map(c => (
                  <td key={c.key}>
                    {typeof c.render === 'function' ? c.render(item[c.key], item) : item[c.key]}
                  </td>
                ))}
                <td>
                  <div className="btn-group btn-group-sm">
                    <button className="btn btn-outline-secondary" onClick={() => onEdit(item)}>
                      <i className="fas fa-edit"></i>
                    </button>
                    <button className="btn btn-outline-danger" onClick={() => onDelete(item)}>
                      <i className="fas fa-trash"></i>
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  </div>
);

const EditModal = ({ title, fields, item, onClose, onSave, saving, error }) => {
  const [form, setForm] = useState({ name: '', description: '', isActive: true, hexCode: '' });

  useEffect(() => {
    if (item) {
      setForm({
        name: item.name || '',
        description: item.description || '',
        isActive: item.isActive !== false,
        hexCode: item.hexCode || ''
      });
    } else {
      setForm({ name: '', description: '', isActive: true, hexCode: '' });
    }
  }, [item]);

  return (
    <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
      <div className="modal-dialog">
        <div className="modal-content">
          <div className="modal-header">
            <h5 className="modal-title">{title}</h5>
            <button type="button" className="btn-close" onClick={onClose}></button>
          </div>
          <div className="modal-body">
            {error && <div className="alert alert-danger">{error}</div>}
            {fields.includes('name') && (
              <div className="mb-3">
                <label className="form-label">İsim</label>
                <input className="form-control" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} />
              </div>
            )}
            {fields.includes('description') && (
              <div className="mb-3">
                <label className="form-label">Açıklama</label>
                <input className="form-control" value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} />
              </div>
            )}
            {fields.includes('hexCode') && (
              <div className="mb-3">
                <label className="form-label">Renk Kodu</label>
                <input className="form-control" placeholder="#FFFFFF" value={form.hexCode} onChange={e => setForm({ ...form, hexCode: e.target.value })} />
              </div>
            )}
            {fields.includes('isActive') && (
              <div className="form-check">
                <input type="checkbox" className="form-check-input" id="isActive" checked={form.isActive} onChange={e => setForm({ ...form, isActive: e.target.checked })} />
                <label className="form-check-label" htmlFor="isActive">Aktif</label>
              </div>
            )}
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={onClose} disabled={saving}>İptal</button>
            <button className="btn btn-primary" onClick={() => onSave(form)} disabled={saving}>
              {saving ? <span className="spinner-border spinner-border-sm" /> : 'Kaydet'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

const AdminSettings = () => {
  const [activeTab, setActiveTab] = useState('brand');
  const [brands, setBrands] = useState([]);
  const [colors, setColors] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState(null);

  const load = async () => {
    try {
      setLoading(true);
      const [b, c] = await Promise.all([
        axios.get('/api/brands'),
        axios.get('/api/colors')
      ]);
      setBrands(b.data || []);
      setColors(c.data || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleSave = async (data) => {
    try {
      setSaving(true);
      setError('');
      if (activeTab === 'brand') {
        if (editing) await axios.put(`/api/brands/${editing.id}`, data);
        else await axios.post('/api/brands', data);
      } else {
        if (editing) await axios.put(`/api/colors/${editing.id}`, data);
        else await axios.post('/api/colors', data);
      }
      setEditing(null);
      await load();
    } catch (e) {
      setError(e.response?.data || 'Hata oluştu');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (item) => {
    if (!window.confirm('Silmek istediğinize emin misiniz?')) return;
    const url = activeTab === 'brand' ? `/api/brands/${item.id}` : `/api/colors/${item.id}`;
    try {
      await axios.delete(url);
      await load();
    } catch (e) {
      alert(e.response?.data || 'Silme hatası');
    }
  };

  return (
    <div>
      <div className="d-flex align-items-center justify-content-between mb-4">
        <h2>Yönetici Ayarları</h2>
        <ul className="nav nav-pills">
          <li className="nav-item">
            <button className={`nav-link ${activeTab === 'brand' ? 'active' : ''}`} onClick={() => setActiveTab('brand')}>Marka</button>
          </li>
          <li className="nav-item ms-2">
            <button className={`nav-link ${activeTab === 'color' ? 'active' : ''}`} onClick={() => setActiveTab('color')}>Renk</button>
          </li>
        </ul>
      </div>

      {activeTab === 'brand' && (
        <CrudTable
          title="Markalar"
          columns={[{ key: 'name', title: 'Ad' }, { key: 'description', title: 'Açıklama' }, { key: 'isActive', title: 'Durum', render: (v) => v ? 'Aktif' : 'Pasif' }]}
          items={brands}
          loading={loading}
          onCreate={() => setEditing(null) || setError('')}
          onEdit={(it) => setEditing(it) || setError('')}
          onDelete={handleDelete}
        />
      )}

      {activeTab === 'color' && (
        <CrudTable
          title="Renkler"
          columns={[{ key: 'name', title: 'Ad' }, { key: 'hexCode', title: 'Renk Kodu', render: (v) => v || '-' }, { key: 'isActive', title: 'Durum', render: (v) => v ? 'Aktif' : 'Pasif' }]}
          items={colors}
          loading={loading}
          onCreate={() => setEditing(null) || setError('')}
          onEdit={(it) => setEditing(it) || setError('')}
          onDelete={handleDelete}
        />
      )}

      {(activeTab === 'brand' || activeTab === 'color') && (editing !== undefined) && (editing === null || editing.id) && (
        <EditModal
          title={activeTab === 'brand' ? (editing ? 'Marka Düzenle' : 'Yeni Marka') : (editing ? 'Renk Düzenle' : 'Yeni Renk')}
          fields={activeTab === 'brand' ? ['name', 'description', 'isActive'] : ['name', 'hexCode', 'isActive']}
          item={editing || undefined}
          onClose={() => setEditing(undefined)}
          onSave={handleSave}
          saving={saving}
          error={error}
        />
      )}
    </div>
  );
};

export default AdminSettings;


