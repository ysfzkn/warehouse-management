import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';
import { useToast } from '../../components/store/Toast';

const STATUS = {
  PENDING_PAYMENT: { label: 'Ödeme Bekliyor', color: 'warning', icon: 'fas fa-clock' },
  PAID: { label: 'Ödendi', color: 'info', icon: 'fas fa-check-circle' },
  PREPARING: { label: 'Hazırlanıyor', color: 'primary', icon: 'fas fa-box' },
  SHIPPED: { label: 'Kargoda', color: 'secondary', icon: 'fas fa-truck' },
  DELIVERED: { label: 'Teslim Edildi', color: 'success', icon: 'fas fa-check-double' },
  CANCELLED: { label: 'İptal Edildi', color: 'danger', icon: 'fas fa-times-circle' },
  RETURN_REQUESTED: { label: 'İade Talebi', color: 'warning', icon: 'fas fa-undo' },
  RETURNED: { label: 'İade Edildi', color: 'dark', icon: 'fas fa-undo-alt' },
  REFUNDED: { label: 'İade Ödemesi', color: 'info', icon: 'fas fa-money-bill-wave' },
};

const RETURN_REASONS = [
  'Hasarlı / Kırık Ürün',
  'Yanlış Ürün Gönderildi',
  'Ürünü Beğenmedim',
  'Eksik Ürün',
  'Diğer',
];

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
  const [returnModal, setReturnModal] = useState(null); // orderNumber
  const [returnReason, setReturnReason] = useState('');
  const [returnNote, setReturnNote] = useState('');
  const [returnLoading, setReturnLoading] = useState(false);
  const toast = useToast();

  const fetch = useCallback(() => {
    setLoading(true);
    axios.get('/api/store/orders', { params: { page, size: 10 }, headers: getAuthHeaders() })
      .then(r => { setOrders(r.data?.content || []); setTotalPages(r.data?.totalPages || 0); })
      .catch(() => {}).finally(() => setLoading(false));
  }, [page]);

  const handleReturnRequest = async () => {
    if (!returnReason) { toast.warning('Lütfen iade nedeninizi seçin.'); return; }
    setReturnLoading(true);
    try {
      await axios.post(`/api/store/orders/${returnModal}/return-request`, { reason: returnReason, note: returnNote }, { headers: getAuthHeaders() });
      toast.success('İade talebiniz alındı. En kısa sürede değerlendirilecektir.');
      setReturnModal(null); setReturnReason(''); setReturnNote('');
      fetch();
    } catch (e) {
      toast.error(e.response?.data?.message || 'İade talebi oluşturulamadı.');
    } finally { setReturnLoading(false); }
  };

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
                  {/* İade talebi butonu — sadece SHIPPED veya DELIVERED */}
                  {(o.status === 'SHIPPED' || o.status === 'DELIVERED') && (
                    <div className="mt-2">
                      <button className="btn btn-sm btn-outline-warning" onClick={() => { setReturnModal(o.orderNumber); setReturnReason(''); setReturnNote(''); }}>
                        <i className="fas fa-undo me-1" />İade Talebi Oluştur
                      </button>
                    </div>
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
      {/* İade Talebi Modal */}
      {returnModal && (
        <div className="modal show d-block" style={{background:'rgba(0,0,0,0.5)',zIndex:3000}} onClick={() => setReturnModal(null)}>
          <div className="modal-dialog modal-dialog-centered" onClick={e => e.stopPropagation()}>
            <div className="modal-content border-0 shadow">
              <div className="modal-header">
                <h5 className="modal-title"><i className="fas fa-undo me-2 text-warning" />İade Talebi — #{returnModal}</h5>
                <button className="btn-close" onClick={() => setReturnModal(null)} />
              </div>
              <div className="modal-body">
                <div className="mb-3">
                  <label className="form-label fw-semibold small">İade Nedeniniz <span className="text-danger">*</span></label>
                  <select className="form-select" value={returnReason} onChange={e => setReturnReason(e.target.value)}>
                    <option value="">Neden seçiniz...</option>
                    {RETURN_REASONS.map(r => <option key={r} value={r}>{r}</option>)}
                  </select>
                </div>
                <div className="mb-3">
                  <label className="form-label fw-semibold small">Açıklama (isteğe bağlı)</label>
                  <textarea className="form-control" rows={3} value={returnNote} onChange={e => setReturnNote(e.target.value)} placeholder="İade ile ilgili detayları yazabilirsiniz..." />
                </div>
              </div>
              <div className="modal-footer">
                <button className="btn btn-outline-secondary" onClick={() => setReturnModal(null)}>İptal</button>
                <button className="btn btn-warning" onClick={handleReturnRequest} disabled={returnLoading || !returnReason}>
                  {returnLoading ? <><span className="spinner-border spinner-border-sm me-1" />Gönderiliyor...</> : <><i className="fas fa-paper-plane me-1" />İade Talebi Gönder</>}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
