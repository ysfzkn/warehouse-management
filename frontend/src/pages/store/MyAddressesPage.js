import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { useToast } from '../../components/store/Toast';
import AddressForm from '../../components/store/AddressForm';
import confirmDialog from '../../utils/confirmDialog';
import { FiMapPin, FiPlus, FiEdit2, FiTrash2, FiStar, FiPhone } from 'react-icons/fi';

const getAuthHeaders = () => {
  const t = localStorage.getItem('customer_token');
  return t ? { Authorization: `Bearer ${t}` } : {};
};

export default function MyAddressesPage() {
  const [addresses, setAddresses] = useState([]);
  const [loading, setLoading] = useState(true);
  const toast = useToast();
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [editData, setEditData] = useState(null);

  const fetchAddresses = useCallback(() => {
    setLoading(true);
    axios
      .get('/api/store/addresses', { headers: getAuthHeaders() })
      .then((r) => setAddresses(r.data || []))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    fetchAddresses();
  }, [fetchAddresses]);

  const handleSave = async (formData) => {
    try {
      if (editing)
        await axios.put(`/api/store/addresses/${editing}`, formData, { headers: getAuthHeaders() });
      else await axios.post('/api/store/addresses', formData, { headers: getAuthHeaders() });
      setShowForm(false);
      setEditing(null);
      setEditData(null);
      fetchAddresses();
      toast.success(editing ? 'Adres güncellendi.' : 'Adres kaydedildi.');
    } catch (e) {
      toast.error(e.response?.data?.message || 'Adres kaydedilemedi.');
    }
  };

  const handleDelete = async (id) => {
    const ok = await confirmDialog({
      title: 'Adres Silinsin mi?',
      message: 'Bu adres kalıcı olarak silinecek.',
      confirmText: 'Evet, Sil',
    });
    if (!ok) return;
    try {
      await axios.delete(`/api/store/addresses/${id}`, { headers: getAuthHeaders() });
      fetchAddresses();
      toast.success('Adres silindi.');
    } catch (e) {
      toast.error(e.response?.data?.message || 'Adres silinemedi.');
    }
  };

  const handleSetDefault = async (id) => {
    try {
      await axios.put(`/api/store/addresses/${id}/set-default`, {}, { headers: getAuthHeaders() });
      fetchAddresses();
      toast.success('Varsayılan adres güncellendi.');
    } catch {
      toast.error('İşlem başarısız.');
    }
  };

  const startEdit = (a) => {
    setEditing(a.id);
    setEditData(a);
    setShowForm(true);
  };

  const startCreate = () => {
    setEditing(null);
    setEditData(null);
    setShowForm(true);
  };

  return (
    <div className="container py-4" style={{ maxWidth: 800 }}>
      <div className="d-flex justify-content-between align-items-center mb-4">
        <h2 className="fw-bold mb-0">
          <FiMapPin className="me-2 text-primary" />
          Adreslerim
        </h2>
        {!showForm && (
          <button className="btn btn-primary btn-sm" onClick={startCreate}>
            <FiPlus size={14} className="me-1" />
            Yeni Adres
          </button>
        )}
      </div>

      {/* Form */}
      {showForm && (
        <div className="card border-0 shadow-sm mb-4" style={{ borderRadius: 16 }}>
          <div className="card-header bg-transparent border-0 d-flex justify-content-between align-items-center py-3">
            <h6 className="mb-0 fw-bold">{editing ? 'Adresi Düzenle' : 'Yeni Adres Ekle'}</h6>
            <button
              className="btn-close"
              onClick={() => {
                setShowForm(false);
                setEditing(null);
                setEditData(null);
              }}
            />
          </div>
          <div className="card-body pt-0">
            <AddressForm
              initialData={editData}
              onSubmit={handleSave}
              onCancel={() => {
                setShowForm(false);
                setEditing(null);
                setEditData(null);
              }}
              submitLabel={editing ? 'Güncelle' : 'Kaydet'}
            />
          </div>
        </div>
      )}

      {/* List */}
      {loading ? (
        <div className="text-center py-5">
          <span className="spinner-border" />
        </div>
      ) : addresses.length === 0 && !showForm ? (
        <div className="card border-0 shadow-sm" style={{ borderRadius: 16 }}>
          <div className="card-body text-center py-5">
            <FiMapPin size={48} className="text-muted mb-3" style={{ opacity: 0.2 }} />
            <p className="text-muted mb-3">Henüz kayıtlı adresiniz yok.</p>
            <button className="btn btn-primary btn-sm" onClick={startCreate}>
              <FiPlus size={14} className="me-1" />
              İlk Adresinizi Ekleyin
            </button>
          </div>
        </div>
      ) : (
        <div className="d-flex flex-column gap-3">
          {addresses.map((a) => (
            <div
              key={a.id}
              className={`card border-0 shadow-sm ${a.isDefault ? 'border-start border-3 border-primary' : ''}`}
              style={{ borderRadius: 14 }}
            >
              <div className="card-body">
                <div className="d-flex justify-content-between align-items-start">
                  <div className="d-flex align-items-center gap-2 mb-2">
                    <span className="fw-bold">{a.title || 'Adres'}</span>
                    {a.isDefault && (
                      <span className="badge bg-primary" style={{ fontSize: 10 }}>
                        Varsayılan
                      </span>
                    )}
                  </div>
                  <div className="d-flex gap-1">
                    <button
                      className="btn btn-sm btn-outline-primary"
                      onClick={() => startEdit(a)}
                      title="Düzenle"
                      style={{ width: 32, height: 32, padding: 0 }}
                    >
                      <FiEdit2 size={13} />
                    </button>
                    {!a.isDefault && (
                      <button
                        className="btn btn-sm btn-outline-success"
                        onClick={() => handleSetDefault(a.id)}
                        title="Varsayılan yap"
                        style={{ width: 32, height: 32, padding: 0 }}
                      >
                        <FiStar size={13} />
                      </button>
                    )}
                    <button
                      className="btn btn-sm btn-outline-danger"
                      onClick={() => handleDelete(a.id)}
                      title="Sil"
                      style={{ width: 32, height: 32, padding: 0 }}
                    >
                      <FiTrash2 size={13} />
                    </button>
                  </div>
                </div>

                <div className="small">
                  <div className="fw-medium">
                    {a.firstName} {a.lastName}
                  </div>
                  {a.phone && (
                    <div className="text-muted">
                      <FiPhone size={11} className="me-1" />
                      {a.phone}
                    </div>
                  )}
                  <div className="text-muted mt-1">{a.addressLine}</div>
                  <div className="text-muted">
                    {a.district} / {a.city} {a.postalCode && `- ${a.postalCode}`}
                  </div>
                </div>

                {/* Kurumsal bilgi */}
                {a.companyName && (
                  <div className="mt-2 pt-2 border-top small text-muted">
                    <FiCreditCard size={12} className="me-1" />
                    {a.companyName}
                    {a.taxOffice && <> · {a.taxOffice}</>}
                    {a.taxNumber && <> · VKN: {a.taxNumber}</>}
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// For the import in address cards
function FiCreditCard({ size, className }) {
  return <i className={`fas fa-building ${className || ''}`} style={{ fontSize: size }} />;
}
