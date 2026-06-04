import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import { FiBell, FiMail, FiSmartphone, FiCheckCircle, FiShoppingBag, FiTruck, FiHome, FiCreditCard, FiTag } from 'react-icons/fi';

const CHANNELS = [
  { key: 'EMAIL', label: 'E-posta', icon: FiMail, color: '#2563eb' },
  { key: 'SMS', label: 'SMS', icon: FiSmartphone, color: '#059669' },
];

const TYPES = [
  {
    key: 'ORDER_CONFIRMED',
    label: 'Sipariş Onayı',
    description: 'Siparişiniz alındığında gönderilir',
    icon: FiShoppingBag
  },
  {
    key: 'PAYMENT_RECEIVED',
    label: 'Ödeme Alındı',
    description: 'Ödemeniz başarıyla alındığında gönderilir',
    icon: FiCreditCard
  },
  {
    key: 'ORDER_STATUS_CHANGE',
    label: 'Sipariş Durumu Değişikliği',
    description: 'Siparişiniz hazırlanıyor, işleme alındı vb. bildirimler',
    icon: FiBell
  },
  {
    key: 'CARGO_SHIPPED',
    label: 'Kargoya Verildi',
    description: 'Siparişiniz kargoya verildiğinde takip numarası ile',
    icon: FiTruck
  },
  {
    key: 'ORDER_DELIVERED',
    label: 'Teslim Edildi',
    description: 'Siparişiniz teslim edildiğinde onay',
    icon: FiHome
  },
  {
    key: 'MARKETING',
    label: 'Kampanya ve Duyurular',
    description: 'İndirim, yeni ürün ve özel kampanyalardan haberdar olun',
    icon: FiTag
  },
];

const formatAuth = () => {
  const token = localStorage.getItem('customer_token');
  return token ? { Authorization: `Bearer ${token}` } : {};
};

export default function NotificationPreferencesPage() {
  const navigate = useNavigate();
  const [preferences, setPreferences] = useState({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saveMessage, setSaveMessage] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await axios.get('/api/store/account/notification-preferences', { headers: formatAuth() });
      setPreferences(res.data || {});
    } catch (err) {
      if (err.response?.status === 401) {
        navigate('/giris?redirect=/hesabim/bildirimler');
      }
    } finally {
      setLoading(false);
    }
  }, [navigate]);

  useEffect(() => { load(); }, [load]);

  const toggle = (channel, type) => {
    setPreferences(prev => ({
      ...prev,
      [channel]: {
        ...prev[channel],
        [type]: !prev[channel]?.[type]
      }
    }));
    setSaveMessage('');
  };

  const save = async () => {
    setSaving(true);
    setSaveMessage('');
    try {
      await axios.put('/api/store/account/notification-preferences', preferences, { headers: formatAuth() });
      setSaveMessage('Tercihleriniz kaydedildi.');
      setTimeout(() => setSaveMessage(''), 3000);
    } catch {
      setSaveMessage('Hata: Tercihler kaydedilemedi.');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="container my-5 text-center">
        <div className="spinner-border text-primary"></div>
      </div>
    );
  }

  return (
    <div className="container my-4" style={{ maxWidth: 820 }}>
      {/* Header */}
      <div className="mb-4">
        <div className="d-flex align-items-center gap-3 mb-2">
          <div style={{
            width: 48, height: 48, borderRadius: 12,
            background: 'linear-gradient(135deg, #eff6ff, #dbeafe)',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <FiBell size={22} color="#2563eb" />
          </div>
          <div>
            <h2 className="fw-bold mb-0">Bildirim Tercihleri</h2>
            <p className="text-muted small mb-0">
              Hangi durumlarda nasıl bilgilendirilmek istediğinizi seçin
            </p>
          </div>
        </div>
      </div>

      {/* Matrix Table */}
      <div className="card border-0 shadow-sm" style={{ borderRadius: 16 }}>
        <div className="card-body p-0">
          <div className="table-responsive">
            <table className="table mb-0 align-middle">
              <thead style={{ background: '#f8fafc' }}>
                <tr>
                  <th style={{ width: '55%', padding: '16px 20px' }}>Bildirim Tipi</th>
                  {CHANNELS.map(ch => (
                    <th key={ch.key} className="text-center" style={{ padding: '16px 12px' }}>
                      <div className="d-inline-flex flex-column align-items-center gap-1">
                        <div style={{
                          width: 36, height: 36, borderRadius: '50%',
                          background: `${ch.color}15`,
                          display: 'flex', alignItems: 'center', justifyContent: 'center',
                        }}>
                          <ch.icon size={18} color={ch.color} />
                        </div>
                        <small className="fw-semibold" style={{ color: ch.color }}>{ch.label}</small>
                      </div>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {TYPES.map(type => (
                  <tr key={type.key}>
                    <td style={{ padding: '16px 20px' }}>
                      <div className="d-flex align-items-start gap-3">
                        <div style={{
                          width: 36, height: 36, borderRadius: 10,
                          background: '#f1f5f9',
                          display: 'flex', alignItems: 'center', justifyContent: 'center',
                          flexShrink: 0
                        }}>
                          <type.icon size={16} color="#475569" />
                        </div>
                        <div>
                          <div className="fw-semibold">{type.label}</div>
                          <small className="text-muted">{type.description}</small>
                        </div>
                      </div>
                    </td>
                    {CHANNELS.map(ch => {
                      const checked = preferences[ch.key]?.[type.key] || false;
                      return (
                        <td key={ch.key} className="text-center" style={{ padding: '16px 12px' }}>
                          <div className="form-check form-switch d-inline-flex m-0" style={{ minHeight: 'unset' }}>
                            <input
                              className="form-check-input"
                              type="checkbox"
                              checked={checked}
                              onChange={() => toggle(ch.key, type.key)}
                              style={{ width: 42, height: 22, cursor: 'pointer' }}
                              id={`${ch.key}-${type.key}`}
                            />
                          </div>
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Info & Save */}
      <div className="d-flex justify-content-between align-items-center mt-3 flex-wrap gap-2">
        <small className="text-muted">
          <i className="fas fa-info-circle me-1"></i>
          Yasal bilgilendirmeler (sipariş teslim edilemedi, para iadesi vb.) tercihlerinizden bağımsız olarak gönderilir.
        </small>
        <div className="d-flex align-items-center gap-3">
          {saveMessage && (
            <span className={`small ${saveMessage.startsWith('Hata') ? 'text-danger' : 'text-success'}`}>
              <FiCheckCircle className="me-1" />{saveMessage}
            </span>
          )}
          <button className="btn btn-primary" onClick={save} disabled={saving}>
            {saving ? (
              <><span className="spinner-border spinner-border-sm me-2" />Kaydediliyor...</>
            ) : (
              <>Tercihleri Kaydet</>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
