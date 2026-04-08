import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';

const STATUS_CONFIG = {
  INITIATED: { label: 'Başlatıldı', color: 'secondary', icon: 'fas fa-hourglass-start' },
  PROCESSING: { label: 'İşleniyor', color: 'info', icon: 'fas fa-spinner' },
  SUCCESS: { label: 'Başarılı', color: 'success', icon: 'fas fa-check-circle' },
  FAILED: { label: 'Başarısız', color: 'danger', icon: 'fas fa-times-circle' },
  TIMEOUT: { label: 'Zaman Aşımı', color: 'warning', icon: 'fas fa-clock' },
  REFUND_PENDING: { label: 'İade Bekliyor', color: 'warning', icon: 'fas fa-undo' },
  REFUNDED: { label: 'İade Edildi', color: 'dark', icon: 'fas fa-undo-alt' },
  PARTIAL_REFUNDED: { label: 'Kısmi İade', color: 'info', icon: 'fas fa-undo' },
};

const PROVIDER_LABELS = {
  IYZICO: { label: 'iyzico', icon: 'fas fa-credit-card', color: 'primary' },
  BANK_TRANSFER: { label: 'Havale/EFT', icon: 'fas fa-university', color: 'success' },
  DOOR_PAYMENT: { label: 'Kapıda Ödeme', icon: 'fas fa-door-open', color: 'warning' },
  PAYTR: { label: 'PayTR', icon: 'fas fa-credit-card', color: 'info' },
};

const formatPrice = (p) => new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(p || 0);
const formatDate = (d) => d ? new Date(d).toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—';

export default function AdminPayments() {
  const [payments, setPayments] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState('');
  const [selectedPayment, setSelectedPayment] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const fetchPayments = useCallback(async (p = 0) => {
    setLoading(true);
    try {
      const params = { page: p, size: 20, sortBy: 'createdAt', sortDir: 'desc' };
      if (statusFilter) params.status = statusFilter;
      const res = await axios.get('/api/admin/payments', { params });
      setPayments(res.data.content || []);
      setTotalPages(res.data.totalPages || 0);
      setTotalElements(res.data.totalElements || 0);
      setPage(p);
    } catch { setPayments([]); }
    finally { setLoading(false); }
  }, [statusFilter]);

  useEffect(() => { fetchPayments(0); }, [fetchPayments]);

  const openDetail = async (id) => {
    setDetailLoading(true);
    try {
      const res = await axios.get(`/api/admin/payments/${id}`);
      setSelectedPayment(res.data);
    } catch { /* ignore */ }
    finally { setDetailLoading(false); }
  };

  // Summary stats
  const successCount = payments.filter(p => p.status === 'SUCCESS').length;
  const pendingCount = payments.filter(p => ['INITIATED', 'PROCESSING'].includes(p.status)).length;
  const failedCount = payments.filter(p => ['FAILED', 'TIMEOUT'].includes(p.status)).length;

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="mb-1">Ödemeler ve Faturalar</h2>
          <p className="text-muted small mb-0">Toplam {totalElements} ödeme işlemi</p>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="row g-3 mb-4">
        <div className="col-6 col-md-3">
          <div className="card border-0 shadow-sm">
            <div className="card-body d-flex align-items-center gap-3">
              <div className="rounded-circle bg-primary bg-opacity-10 d-flex align-items-center justify-content-center" style={{width:44,height:44}}>
                <i className="fas fa-receipt text-primary" />
              </div>
              <div><div className="small text-muted">Toplam</div><div className="h5 mb-0">{totalElements}</div></div>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card border-0 shadow-sm">
            <div className="card-body d-flex align-items-center gap-3">
              <div className="rounded-circle bg-success bg-opacity-10 d-flex align-items-center justify-content-center" style={{width:44,height:44}}>
                <i className="fas fa-check-circle text-success" />
              </div>
              <div><div className="small text-muted">Başarılı</div><div className="h5 mb-0 text-success">{successCount}</div></div>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card border-0 shadow-sm">
            <div className="card-body d-flex align-items-center gap-3">
              <div className="rounded-circle bg-warning bg-opacity-10 d-flex align-items-center justify-content-center" style={{width:44,height:44}}>
                <i className="fas fa-hourglass-half text-warning" />
              </div>
              <div><div className="small text-muted">Bekleyen</div><div className="h5 mb-0 text-warning">{pendingCount}</div></div>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card border-0 shadow-sm">
            <div className="card-body d-flex align-items-center gap-3">
              <div className="rounded-circle bg-danger bg-opacity-10 d-flex align-items-center justify-content-center" style={{width:44,height:44}}>
                <i className="fas fa-times-circle text-danger" />
              </div>
              <div><div className="small text-muted">Başarısız</div><div className="h5 mb-0 text-danger">{failedCount}</div></div>
            </div>
          </div>
        </div>
      </div>

      {/* Filters */}
      <div className="card border-0 shadow-sm mb-4">
        <div className="card-body py-2">
          <div className="d-flex align-items-center gap-2 flex-wrap">
            <span className="small text-muted me-2">Durum:</span>
            <button className={`btn btn-sm ${!statusFilter ? 'btn-primary' : 'btn-outline-secondary'}`} onClick={() => setStatusFilter('')}>Tümü</button>
            {Object.entries(STATUS_CONFIG).map(([key, cfg]) => (
              <button key={key} className={`btn btn-sm ${statusFilter === key ? `btn-${cfg.color}` : 'btn-outline-secondary'}`} onClick={() => setStatusFilter(key)}>
                <i className={`${cfg.icon} me-1`} />{cfg.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="card border-0 shadow-sm">
        <div className="table-responsive">
          <table className="table table-hover mb-0">
            <thead className="table-light">
              <tr>
                <th style={{width:60}}>#</th>
                <th>Sipariş</th>
                <th>Müşteri</th>
                <th>Yöntem</th>
                <th>Tutar</th>
                <th>Durum</th>
                <th>Tarih</th>
                <th style={{width:60}}></th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan="8" className="text-center py-4"><span className="spinner-border spinner-border-sm" /> Yükleniyor...</td></tr>
              ) : payments.length === 0 ? (
                <tr><td colSpan="8" className="text-center py-4 text-muted">Ödeme işlemi bulunamadı.</td></tr>
              ) : payments.map(p => {
                const sc = STATUS_CONFIG[p.status] || { label: p.status, color: 'secondary', icon: 'fas fa-question' };
                const prov = PROVIDER_LABELS[p.paymentProvider] || { label: p.paymentProvider, icon: 'fas fa-money-bill', color: 'secondary' };
                return (
                  <tr key={p.id} style={{cursor:'pointer'}} onClick={() => openDetail(p.id)}>
                    <td className="small text-muted">{p.id}</td>
                    <td>
                      <div className="fw-medium small">{p.orderNumber || '—'}</div>
                    </td>
                    <td>
                      <div className="small">{p.customerName || '—'}</div>
                      <div className="text-muted small">{p.customerEmail || ''}</div>
                    </td>
                    <td><span className={`badge bg-${prov.color} bg-opacity-10 text-${prov.color}`}><i className={`${prov.icon} me-1`} />{prov.label}</span></td>
                    <td className="fw-medium">{formatPrice(p.amount)}</td>
                    <td><span className={`badge bg-${sc.color}`}><i className={`${sc.icon} me-1`} />{sc.label}</span></td>
                    <td className="small text-muted">{formatDate(p.createdAt)}</td>
                    <td><button className="btn btn-sm btn-outline-primary" onClick={e => { e.stopPropagation(); openDetail(p.id); }}><i className="fas fa-eye" /></button></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {/* Pagination */}
        {totalPages > 1 && (
          <div className="card-footer bg-transparent d-flex justify-content-between align-items-center">
            <small className="text-muted">Sayfa {page + 1} / {totalPages}</small>
            <div className="btn-group btn-group-sm">
              <button className="btn btn-outline-primary" disabled={page === 0} onClick={() => fetchPayments(page - 1)}><i className="fas fa-chevron-left" /></button>
              <button className="btn btn-outline-primary" disabled={page >= totalPages - 1} onClick={() => fetchPayments(page + 1)}><i className="fas fa-chevron-right" /></button>
            </div>
          </div>
        )}
      </div>

      {/* Detail Modal */}
      {selectedPayment && (
        <div className="modal show d-block" style={{backgroundColor:'rgba(0,0,0,0.5)'}} onClick={() => setSelectedPayment(null)}>
          <div className="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable" onClick={e => e.stopPropagation()}>
            <div className="modal-content border-0 shadow">
              <div className="modal-header">
                <h5 className="modal-title"><i className="fas fa-receipt me-2" />Ödeme Detayı #{selectedPayment.id}</h5>
                <button className="btn-close" onClick={() => setSelectedPayment(null)} />
              </div>
              <div className="modal-body">
                {detailLoading ? (
                  <div className="text-center py-4"><span className="spinner-border" /></div>
                ) : (
                  <div className="row g-3">
                    {/* Left: Genel */}
                    <div className="col-md-6">
                      <h6 className="fw-bold mb-3"><i className="fas fa-info-circle me-2 text-primary" />Genel Bilgiler</h6>
                      <table className="table table-sm">
                        <tbody>
                          <tr><td className="text-muted">Sipariş No</td><td className="fw-medium">{selectedPayment.orderNumber || '—'}</td></tr>
                          <tr><td className="text-muted">Müşteri</td><td>{selectedPayment.customerName || '—'}</td></tr>
                          <tr><td className="text-muted">E-posta</td><td>{selectedPayment.customerEmail || '—'}</td></tr>
                          <tr><td className="text-muted">Tutar</td><td className="fw-bold">{formatPrice(selectedPayment.amount)}</td></tr>
                          <tr><td className="text-muted">Ödenen</td><td>{selectedPayment.paidAmount ? formatPrice(selectedPayment.paidAmount) : '—'}</td></tr>
                          <tr><td className="text-muted">Durum</td><td><span className={`badge bg-${(STATUS_CONFIG[selectedPayment.status] || {}).color || 'secondary'}`}>{(STATUS_CONFIG[selectedPayment.status] || {}).label || selectedPayment.status}</span></td></tr>
                          <tr><td className="text-muted">Tarih</td><td>{formatDate(selectedPayment.createdAt)}</td></tr>
                          <tr><td className="text-muted">Ödeme Tarihi</td><td>{formatDate(selectedPayment.paidAt)}</td></tr>
                          <tr><td className="text-muted">IP Adresi</td><td className="small">{selectedPayment.ipAddress || '—'}</td></tr>
                        </tbody>
                      </table>
                    </div>

                    {/* Right: Provider Details */}
                    <div className="col-md-6">
                      <h6 className="fw-bold mb-3"><i className="fas fa-credit-card me-2 text-success" />Ödeme Detayları</h6>
                      <table className="table table-sm">
                        <tbody>
                          <tr><td className="text-muted">Yöntem</td><td><span className={`badge bg-${(PROVIDER_LABELS[selectedPayment.paymentProvider] || {}).color || 'secondary'}`}>{(PROVIDER_LABELS[selectedPayment.paymentProvider] || {}).label || selectedPayment.paymentProvider}</span></td></tr>
                          {selectedPayment.providerPaymentId && <tr><td className="text-muted">İşlem ID</td><td><code className="small">{selectedPayment.providerPaymentId}</code></td></tr>}
                          {selectedPayment.conversationId && <tr><td className="text-muted">Konuşma ID</td><td><code className="small">{selectedPayment.conversationId}</code></td></tr>}
                          {selectedPayment.cardLastFour && <tr><td className="text-muted">Kart</td><td>**** **** **** {selectedPayment.cardLastFour}</td></tr>}
                          {selectedPayment.cardType && <tr><td className="text-muted">Kart Tipi</td><td>{({CREDIT_CARD:'Kredi Kartı',DEBIT_CARD:'Banka Kartı',PREPAID_CARD:'Ön Ödemeli Kart'})[selectedPayment.cardType] || selectedPayment.cardType}</td></tr>}
                          {selectedPayment.cardAssociation && <tr><td className="text-muted">Kart Ağı</td><td>{({MASTER_CARD:'Mastercard',VISA:'Visa',AMERICAN_EXPRESS:'Amex',TROY:'Troy'})[selectedPayment.cardAssociation] || selectedPayment.cardAssociation}</td></tr>}
                          {selectedPayment.cardBankName && <tr><td className="text-muted">Banka</td><td>{selectedPayment.cardBankName}</td></tr>}
                          {selectedPayment.installmentCount > 1 && <tr><td className="text-muted">Taksit</td><td>{selectedPayment.installmentCount}x {selectedPayment.installmentPrice ? formatPrice(selectedPayment.installmentPrice) : ''}</td></tr>}
                          {selectedPayment.threeDSecure && <tr><td className="text-muted">3D Secure</td><td><span className="badge bg-success">Evet</span></td></tr>}
                          {selectedPayment.bankTransferRef && <tr><td className="text-muted">Havale Ref</td><td><code>{selectedPayment.bankTransferRef}</code></td></tr>}
                        </tbody>
                      </table>

                      {/* Error Info */}
                      {selectedPayment.errorMessage && (
                        <div className="alert alert-danger small mt-3 mb-0">
                          <i className="fas fa-exclamation-triangle me-1" />
                          <strong>Hata:</strong> {selectedPayment.errorMessage}
                          {selectedPayment.errorCode && <span className="ms-2">({selectedPayment.errorCode})</span>}
                        </div>
                      )}

                      {/* Refund Info */}
                      {selectedPayment.refundAmount && (
                        <div className="alert alert-info small mt-3 mb-0">
                          <i className="fas fa-undo me-1" />
                          <strong>İade:</strong> {formatPrice(selectedPayment.refundAmount)}
                          {selectedPayment.refundedAt && <span className="ms-2">({formatDate(selectedPayment.refundedAt)})</span>}
                        </div>
                      )}
                    </div>
                  </div>
                )}
              </div>
              <div className="modal-footer">
                <button className="btn btn-outline-secondary" onClick={() => setSelectedPayment(null)}>Kapat</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
