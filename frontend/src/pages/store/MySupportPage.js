import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';
import { useToast } from '../../components/store/Toast';
import { FiMessageSquare, FiCheckCircle, FiClock, FiLoader, FiXCircle } from 'react-icons/fi';

const STATUS = {
  OPEN: { label: 'Açık', color: '#d97706', bg: '#fffbeb', border: '#fde68a', icon: FiClock },
  IN_PROGRESS: { label: 'İnceleniyor', color: '#2563eb', bg: '#eff6ff', border: '#bfdbfe', icon: FiLoader },
  RESOLVED: { label: 'Yanıtlandı', color: '#059669', bg: '#ecfdf5', border: '#a7f3d0', icon: FiCheckCircle },
  CLOSED: { label: 'Kapatıldı', color: '#64748b', bg: '#f8fafc', border: '#e2e8f0', icon: FiXCircle },
};

const fmtDate = (d) => d ? new Date(d).toLocaleString('tr-TR', { day: '2-digit', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '';

const getAuthHeaders = () => {
  const t = localStorage.getItem('customer_token');
  return t ? { Authorization: `Bearer ${t}` } : {};
};

export default function MySupportPage() {
  const [tickets, setTickets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState(null);
  const toast = useToast();

  useEffect(() => {
    setLoading(true);
    axios.get('/api/store/support-tickets', { headers: getAuthHeaders() })
      .then(r => setTickets(r.data || []))
      .catch(() => toast.error('Destek talepleri yüklenemedi.'))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="container py-4" style={{ maxWidth: 800 }}>
      <h2 className="fw-bold mb-4"><FiMessageSquare className="me-2 text-primary" />Destek Taleplerim</h2>

      {loading ? <div className="text-center py-5"><span className="spinner-border" /></div>
      : tickets.length === 0 ? (
        <div className="card border-0 shadow-sm" style={{ borderRadius: 16 }}>
          <div className="card-body text-center py-5">
            <FiMessageSquare size={48} className="text-muted mb-3" style={{ opacity: 0.2 }} />
            <p className="text-muted mb-3">Henüz destek talebiniz bulunmuyor.</p>
            <p className="text-muted small">Siparişleriniz üzerinden destek talebi oluşturabilirsiniz.</p>
            <Link to="/siparislerim" className="btn btn-outline-primary btn-sm">Siparişlerime Git</Link>
          </div>
        </div>
      ) : (
        <div className="d-flex flex-column gap-3">
          {tickets.map(t => {
            const s = STATUS[t.status] || STATUS.OPEN;
            const Icon = s.icon;
            const isOpen = selected === t.id;
            return (
              <div key={t.id} className="card border-0 shadow-sm" style={{ borderRadius: 14, overflow: 'hidden' }}>
                {/* Header — tıklanabilir */}
                <div className="card-body py-3" style={{ cursor: 'pointer', borderLeft: `4px solid ${s.color}` }}
                  onClick={() => setSelected(isOpen ? null : t.id)}>
                  <div className="d-flex justify-content-between align-items-start">
                    <div className="flex-grow-1 min-w-0">
                      <div className="fw-semibold">{t.topic}</div>
                      <div className="text-muted small mt-1">
                        {t.orderNumber && <>Sipariş #{t.orderNumber} · </>}
                        {fmtDate(t.createdAt)}
                      </div>
                    </div>
                    <div className="d-flex align-items-center gap-2 flex-shrink-0 ms-3">
                      <span className="d-inline-flex align-items-center gap-1 px-2 py-1 rounded-pill" style={{
                        background: s.bg, color: s.color, border: `1px solid ${s.border}`,
                        fontSize: '0.72rem', fontWeight: 600,
                      }}>
                        <Icon size={12} />{s.label}
                      </span>
                      <i className={`fas fa-chevron-${isOpen ? 'up' : 'down'} text-muted`} style={{ fontSize: 11 }} />
                    </div>
                  </div>
                </div>

                {/* Detail — açılır */}
                {isOpen && (
                  <div style={{ borderTop: '1px solid #f1f5f9' }}>
                    {/* Müşteri mesajı */}
                    <div className="px-4 pt-3">
                      <div className="d-flex gap-2 mb-1">
                        <span className="small fw-semibold text-primary">Siz</span>
                        <span className="small text-muted">{fmtDate(t.createdAt)}</span>
                      </div>
                      <div className="p-3 rounded-3 mb-3" style={{
                        background: '#eff6ff', border: '1px solid #bfdbfe',
                        whiteSpace: 'pre-wrap', fontSize: '0.88rem', lineHeight: 1.7,
                      }}>
                        {t.message}
                      </div>
                    </div>

                    {/* Admin yanıtı */}
                    {t.adminReply ? (
                      <div className="px-4 pb-3">
                        <div className="d-flex gap-2 mb-1">
                          <span className="small fw-semibold text-success"><i className="fas fa-headset me-1" />Destek Ekibi</span>
                          <span className="small text-muted">{fmtDate(t.repliedAt)}</span>
                        </div>
                        <div className="p-3 rounded-3" style={{
                          background: '#f0fdf4', border: '1px solid #bbf7d0',
                          whiteSpace: 'pre-wrap', fontSize: '0.88rem', lineHeight: 1.7,
                        }}>
                          {t.adminReply}
                        </div>
                      </div>
                    ) : (
                      <div className="px-4 pb-3">
                        <div className="d-flex align-items-center gap-2 p-3 rounded-3" style={{ background: '#fffbeb', border: '1px solid #fde68a' }}>
                          <FiClock size={16} className="text-warning flex-shrink-0" />
                          <span className="small text-warning">Talebiniz inceleniyor. En kısa sürede yanıtlanacaktır.</span>
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
