import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';

const STATUS = {
  PENDING_PAYMENT: { label: 'Ödeme Bekliyor', color: 'warning', icon: 'fas fa-clock' },
  PAID: { label: 'Ödendi', color: 'info', icon: 'fas fa-check-circle' },
  PREPARING: { label: 'Hazırlanıyor', color: 'primary', icon: 'fas fa-box' },
  SHIPPED: { label: 'Kargoda', color: 'secondary', icon: 'fas fa-truck' },
  DELIVERED: { label: 'Teslim Edildi', color: 'success', icon: 'fas fa-check-double' },
  CANCELLED: { label: 'İptal Edildi', color: 'danger', icon: 'fas fa-times-circle' },
};

const fmt = (p) => p != null ? new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(p) : '—';
const fmtDate = (d) => d ? new Date(d).toLocaleDateString('tr-TR', { day: '2-digit', month: 'long', year: 'numeric' }) : '—';
const pmLabel = (m) => ({ CREDIT_CARD:'Kredi Kartı', VIRTUAL_POS:'Sanal POS', BANK_TRANSFER:'Havale / EFT', DOOR_CASH:'Kapıda Nakit', DOOR_CARD:'Kapıda Kart' }[m] || m || '—');

const getAuthHeaders = () => {
  const t = localStorage.getItem('customer_token');
  return t ? { Authorization: `Bearer ${t}` } : {};
};

export default function MyOrdersPage() {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const fetch = useCallback(() => {
    setLoading(true);
    axios.get('/api/store/orders', { params: { page, size: 10 }, headers: getAuthHeaders() })
      .then(r => { setOrders(r.data?.content || []); setTotalPages(r.data?.totalPages || 0); })
      .catch(() => {}).finally(() => setLoading(false));
  }, [page]);

  useEffect(() => { fetch(); }, [fetch]);

  return (
    <div className="container py-4" style={{ maxWidth: 800 }}>
      <h2 className="fw-bold mb-4"><i className="fas fa-box me-2 text-primary" />Siparişlerim</h2>

      {loading ? <div className="text-center py-5"><span className="spinner-border" /></div>
      : orders.length === 0 ? (
        <div className="card border-0 shadow-sm">
          <div className="card-body text-center py-5">
            <i className="fas fa-shopping-bag text-muted fa-3x mb-3 opacity-25" />
            <p className="text-muted mb-3">Henüz siparişiniz bulunmuyor.</p>
            <Link to="/store" className="btn btn-primary">Alışverişe Başla</Link>
          </div>
        </div>
      ) : (
        <div className="d-flex flex-column gap-3">
          {orders.map(o => {
            const s = STATUS[o.status] || { label: o.status, color: 'secondary', icon: 'fas fa-circle' };
            return (
              <div key={o.id} className="card border-0 shadow-sm">
                <div className="card-body">
                  <div className="d-flex justify-content-between align-items-start flex-wrap gap-2">
                    <div>
                      <div className="fw-bold text-primary">#{o.orderNumber}</div>
                      <div className="text-muted small">{fmtDate(o.createdAt)}</div>
                    </div>
                    <span className={`badge bg-${s.color} px-3 py-2`}><i className={`${s.icon} me-1`} />{s.label}</span>
                  </div>
                  <hr className="my-2" />
                  <div className="d-flex justify-content-between align-items-center flex-wrap gap-1">
                    <div className="small text-muted">{o.itemCount || 0} ürün · {pmLabel(o.paymentMethod)}</div>
                    <div className="fw-bold">{fmt(o.grandTotal)}</div>
                  </div>
                  {(o.paymentMethod === 'DOOR_CASH' || o.paymentMethod === 'DOOR_CARD') && o.status !== 'DELIVERED' && o.status !== 'CANCELLED' && (
                    <div className="small text-warning mt-1"><i className="fas fa-info-circle me-1" />Teslimat sırasında ödeme yapılacaktır.</div>
                  )}
                  {o.cargoTrackingNo && (
                    <div className="small text-success mt-1"><i className="fas fa-truck me-1" />Kargo Takip: <strong>{o.cargoTrackingNo}</strong></div>
                  )}
                </div>
              </div>
            );
          })}
          {totalPages > 1 && (
            <div className="d-flex justify-content-center gap-2">
              <button className="btn btn-outline-primary btn-sm" disabled={page === 0} onClick={() => setPage(p => p - 1)}>Önceki</button>
              <span className="small align-self-center text-muted">{page + 1} / {totalPages}</span>
              <button className="btn btn-outline-primary btn-sm" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>Sonraki</button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
