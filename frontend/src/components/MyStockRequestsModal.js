import React, { useEffect, useMemo, useState, useCallback } from 'react';
import axios from 'axios';
import NotesModal from './NotesModal';

const statusConfig = {
  PENDING: { label: 'Beklemede', className: 'warning', icon: 'fa-clock' },
  APPROVED: { label: 'Onaylandı', className: 'success', icon: 'fa-check' },
  REJECTED: { label: 'Reddedildi', className: 'danger', icon: 'fa-times' }
};

const filterOptions = [
  { key: 'PENDING', label: 'Beklemede', variant: 'warning' },
  { key: 'APPROVED', label: 'Onaylanan', variant: 'success' },
  { key: 'REJECTED', label: 'Reddedilen', variant: 'danger' },
  { key: 'ALL', label: 'Tümü', variant: 'secondary' }
];

const formatDate = (value) => {
  if (!value) return '-';
  return new Date(value).toLocaleString('tr-TR', {
    timeZone: 'Europe/Istanbul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

const MyStockRequestsModal = ({ onClose }) => {
  const [allRequests, setAllRequests] = useState([]);
  const [filter, setFilter] = useState('PENDING');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [deletingId, setDeletingId] = useState(null);
  const [notesModal, setNotesModal] = useState({ show: false, title: '', notes: '', requestId: null });

  const [transferRequests, setTransferRequests] = useState([]);
  const [transferLoading, setTransferLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('stock'); // 'stock' or 'transfer'
  const [transferFilter, setTransferFilter] = useState('PENDING');

  const fetchRequests = useCallback(async () => {
    try {
      setLoading(true);
      const response = await axios.get('/api/stock-requests/current-user');
      setAllRequests(Array.isArray(response.data) ? response.data : []);
      setError(null);
    } catch (err) {
      setError(err.response?.data?.message || 'Talepler alınırken hata oluştu');
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchTransferRequests = useCallback(async () => {
    try {
      setTransferLoading(true);
      const response = await axios.get('/api/stock-transfers/current-user/requests');
      setTransferRequests(Array.isArray(response.data) ? response.data : []);
    } catch (err) {
      console.error('Error fetching transfer requests:', err);
    } finally {
      setTransferLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchRequests();
    fetchTransferRequests();
  }, [fetchRequests, fetchTransferRequests]);

  const handleDelete = async (requestId) => {
    if (!window.confirm(`#${requestId} numaralı talebi silmek istediğinize emin misiniz?`)) {
      return;
    }
    try {
      setDeletingId(requestId);
      await axios.delete(`/api/stock-requests/${requestId}`);
      await fetchRequests();
    } catch (err) {
      alert(err.response?.data?.message || 'Talep silinirken hata oluştu');
    } finally {
      setDeletingId(null);
    }
  };

  const stats = useMemo(() => ({
    pending: allRequests.filter(r => r.status === 'PENDING').length,
    approved: allRequests.filter(r => r.status === 'APPROVED').length,
    rejected: allRequests.filter(r => r.status === 'REJECTED').length
  }), [allRequests]);

  const transferStats = useMemo(() => ({
    pending: transferRequests.filter(r => r.approvalStatus === 'PENDING').length,
    approved: transferRequests.filter(r => r.approvalStatus === 'APPROVED').length,
    rejected: transferRequests.filter(r => r.approvalStatus === 'REJECTED').length
  }), [transferRequests]);

  const requests = useMemo(() => {
    if (filter === 'ALL') {
      return allRequests;
    }
    return allRequests.filter(r => r.status === filter);
  }, [allRequests, filter]);

  const filteredTransferRequests = useMemo(() => {
    if (transferFilter === 'ALL') {
      return transferRequests;
    }
    return transferRequests.filter(r => r.approvalStatus === transferFilter);
  }, [transferRequests, transferFilter]);

  const getTransferItemsDescription = (transfer) => {
    if (transfer.items && transfer.items.length > 0) {
      return transfer.items.map(item => `${item.quantity} x ${item.product?.name || 'Ürün'}`).join(', ');
    }
    if (transfer.product) {
      return `${transfer.quantity || 0} x ${transfer.product.name || 'Ürün'}`;
    }
    return 'Ürün bilgisi yok';
  };

  return (
    <>
      <style>{`
        @media (max-width: 767.98px) {
          .my-requests-mobile-list {
            display: flex;
            flex-direction: column;
            gap: 1rem;
          }
          .my-requests-card {
            border-radius: 20px;
            border: 1px solid rgba(15, 23, 42, 0.08);
            background: #fff;
            box-shadow: 0 18px 35px rgba(15, 23, 42, 0.12);
          }
          .my-requests-card__header {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            gap: 0.5rem;
          }
          .my-requests-card__title {
            font-size: 1.05rem;
            font-weight: 600;
            color: #0f172a;
          }
          .my-requests-card__meta {
            font-size: 0.82rem;
            color: #64748b;
          }
          .my-requests-card__details {
            font-size: 0.83rem;
            color: #475569;
            line-height: 1.4;
          }
          .my-requests-card__meta-row {
            display: flex;
            align-items: center;
            gap: 0.65rem;
            margin-bottom: 0.45rem;
          }
          .my-requests-card__meta-row:last-child {
            margin-bottom: 0;
          }
          .my-requests-pill {
            border-radius: 999px;
            padding: 0.25rem 0.75rem;
            font-size: 0.78rem;
            font-weight: 600;
          }
          .my-requests-actions .btn {
            border-radius: 14px;
            font-weight: 600;
          }
        }
      `}</style>
      <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
        <div className="modal-dialog modal-xl modal-dialog-scrollable">
          <div className="modal-content">
            <div className="modal-header bg-info text-white">
              <h5 className="modal-title">
                <i className="fas fa-list me-2"></i>
                Taleplerim
              </h5>
              <button type="button" className="btn-close btn-close-white" onClick={onClose}></button>
            </div>

            <div className="modal-body p-0">
              <div className="p-3 border-bottom bg-white">
                <div className="btn-group w-100" role="group">
                  <input
                    type="radio"
                    className="btn-check"
                    name="requestTab"
                    id="tab-stock-requests"
                    value="stock"
                    checked={activeTab === 'stock'}
                    onChange={() => setActiveTab('stock')}
                  />
                  <label className="btn btn-outline-primary" htmlFor="tab-stock-requests">
                    <i className="fas fa-boxes me-2"></i>
                    Stok Talepleri
                  </label>

                  <input
                    type="radio"
                    className="btn-check"
                    name="requestTab"
                    id="tab-transfer-requests"
                    value="transfer"
                    checked={activeTab === 'transfer'}
                    onChange={() => setActiveTab('transfer')}
                  />
                  <label className="btn btn-outline-info" htmlFor="tab-transfer-requests">
                    <i className="fas fa-exchange-alt me-2"></i>
                    Transfer Talepleri
                  </label>
                </div>
              </div>

              {activeTab === 'stock' ? (
                <>
                  <div className="row g-3 p-3 bg-light border-bottom">
                    <div className="col-md-4">
                      <div className="card border-warning shadow-sm h-100">
                        <div className="card-body text-center">
                          <i className="fas fa-clock fa-2x text-warning mb-2"></i>
                          <h3 className="mb-0">{stats.pending}</h3>
                          <p className="text-muted mb-0 small">Bekleyen Talepler</p>
                        </div>
                      </div>
                    </div>
                    <div className="col-md-4">
                      <div className="card border-success shadow-sm h-100">
                        <div className="card-body text-center">
                          <i className="fas fa-check-circle fa-2x text-success mb-2"></i>
                          <h3 className="mb-0">{stats.approved}</h3>
                          <p className="text-muted mb-0 small">Onaylananlar</p>
                        </div>
                      </div>
                    </div>
                    <div className="col-md-4">
                      <div className="card border-danger shadow-sm h-100">
                        <div className="card-body text-center">
                          <i className="fas fa-times-circle fa-2x text-danger mb-2"></i>
                          <h3 className="mb-0">{stats.rejected}</h3>
                          <p className="text-muted mb-0 small">Reddedilenler</p>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="p-3 border-bottom bg-white">
                    <div className="btn-group flex-wrap w-100" role="group">
                      {filterOptions.map(option => (
                        <React.Fragment key={option.key}>
                          <input
                            type="radio"
                            className="btn-check"
                            name="myRequestFilter"
                            id={`my-req-${option.key}`}
                            value={option.key}
                            checked={filter === option.key}
                            onChange={(e) => setFilter(e.target.value)}
                          />
                          <label className={`btn btn-outline-${option.variant}`} htmlFor={`my-req-${option.key}`}>
                            {option.label}
                          </label>
                        </React.Fragment>
                      ))}
                    </div>
                  </div>

                  <div className="p-3" style={{ minHeight: '300px' }}>
                    {loading ? (
                      <div className="text-center py-5">
                        <div className="spinner-border text-info" role="status">
                          <span className="visually-hidden">Yükleniyor...</span>
                        </div>
                      </div>
                    ) : error ? (
                      <div className="alert alert-danger m-0">
                        <i className="fas fa-exclamation-triangle me-2"></i>
                        {error}
                      </div>
                    ) : requests.length === 0 ? (
                      <div className="text-center py-5">
                        <i className="fas fa-inbox fa-3x text-muted mb-3"></i>
                        <p className="text-muted mb-0">
                          {filter === 'PENDING' ? 'Bekleyen talebiniz bulunmuyor' : 'Bu filtrede kayıt bulunamadı'}
                        </p>
                      </div>
                    ) : (
                      <>
                        <div className="d-none d-md-block table-responsive">
                          <table className="table table-hover align-middle mb-0">
                            <thead className="table-light">
                              <tr>
                                <th style={{ width: '70px' }}>ID</th>
                                <th>Ürün</th>
                                <th>Depo</th>
                                <th className="text-center">İşlem</th>
                                <th className="text-center">Miktar</th>
                                <th className="text-center">Durum</th>
                                <th style={{ width: '160px' }}>Tarih</th>
                                <th className="text-center" style={{ width: '160px' }}>İşlemler</th>
                              </tr>
                            </thead>
                            <tbody>
                              {requests.map(request => {
                                const status = statusConfig[request.status] || statusConfig.PENDING;
                                const canDelete = request.status === 'PENDING';
                                return (
                                  <tr key={request.id}>
                                    <td>
                                      <span className="badge bg-dark">#{request.id}</span>
                                    </td>
                                    <td>
                                      <div className="fw-bold">{request.productName}</div>
                                      <small className="text-muted">SKU: {request.productSku}</small>
                                    </td>
                                    <td>{request.warehouseName}</td>
                                    <td className="text-center">
                                      <span className={`badge ${request.type === 'ADD' ? 'bg-success' : 'bg-danger'}`}>
                                        <i className={`fas fa-${request.type === 'ADD' ? 'plus' : 'minus'} me-1`}></i>
                                        {request.type === 'ADD' ? 'Ekleme' : 'Çıkarma'}
                                      </span>
                                    </td>
                                    <td className="text-center">
                                      <span className="badge bg-primary">{request.quantity}</span>
                                    </td>
                                    <td className="text-center">
                                      <span className={`badge bg-${status.className}`}>
                                        <i className={`fas ${status.icon} me-1`}></i>
                                        {status.label}
                                      </span>
                                    </td>
                                    <td>
                                      <small className="text-muted d-block">{formatDate(request.requestedAt)}</small>
                                      {request.reviewedAt && (
                                        <small className="text-muted d-block">
                                          Güncelleme: {formatDate(request.reviewedAt)}
                                        </small>
                                      )}
                                    </td>
                                    <td className="text-center">
                                      <div className="d-flex gap-1 justify-content-center">
                                        {request.notes && (
                                          <button
                                            className="btn btn-sm btn-outline-secondary"
                                            title="Notları Gör"
                                            onClick={() => setNotesModal({
                                              show: true,
                                              title: 'Talep Notları',
                                              notes: request.notes,
                                              requestId: request.id
                                            })}
                                          >
                                            <i className="fas fa-sticky-note"></i>
                                          </button>
                                        )}
                                        {request.status === 'REJECTED' && request.rejectionReason && (
                                          <button
                                            className="btn btn-sm btn-outline-danger"
                                            title="Ret Nedenini Gör"
                                            onClick={() => setNotesModal({
                                              show: true,
                                              title: 'Reddetme Nedeni',
                                              notes: request.rejectionReason,
                                              requestId: request.id
                                            })}
                                          >
                                            <i className="fas fa-comment-slash"></i>
                                          </button>
                                        )}
                                        {canDelete && (
                                          <button
                                            className="btn btn-sm btn-outline-danger"
                                            onClick={() => handleDelete(request.id)}
                                            disabled={deletingId === request.id}
                                            title="Talebi Sil"
                                          >
                                            {deletingId === request.id ? (
                                              <span className="spinner-border spinner-border-sm"></span>
                                            ) : (
                                              <i className="fas fa-trash"></i>
                                            )}
                                          </button>
                                        )}
                                        {!request.notes && request.status !== 'REJECTED' && !canDelete && (
                                          <span className="text-muted small">-</span>
                                        )}
                                      </div>
                                    </td>
                                  </tr>
                                );
                              })}
                            </tbody>
                          </table>
                        </div>
                        <div className="d-md-none px-3 pb-3 my-requests-mobile-list">
                          {requests.map(request => {
                            const status = statusConfig[request.status] || statusConfig.PENDING;
                            const canDelete = request.status === 'PENDING';
                            return (
                              <div key={request.id} className="my-requests-card card border-0">
                                <div className="card-body">
                                  <div className="my-requests-card__header mb-2">
                                    <div>
                                      <div className="my-requests-card__title">Talep #{request.id}</div>
                                      <small className="my-requests-card__meta">
                                        {request.type === 'ADD' ? 'Stok Ekleme' : 'Stok Çıkarma'}
                                      </small>
                                    </div>
                                    <span className={`my-requests-pill badge bg-${status.className}`}>
                                      <i className={`fas ${status.icon} me-1`}></i>
                                      {status.label}
                                    </span>
                                  </div>
                                  <div className="my-requests-card__details mb-3">
                                    <div className="my-requests-card__meta-row fw-semibold text-dark">
                                      <i className="fas fa-box text-primary"></i>
                                      {request.productName}
                                    </div>
                                    <div className="my-requests-card__meta-row">
                                      <i className="fas fa-barcode"></i>
                                      {request.productSku || '-'}
                                    </div>
                                    <div className="my-requests-card__meta-row">
                                      <i className="fas fa-warehouse"></i>
                                      {request.warehouseName || '-'}
                                    </div>
                                    <div className="my-requests-card__meta-row">
                                      <i className="fas fa-calendar"></i>
                                      {formatDate(request.requestedAt)}
                                    </div>
                                    {request.reviewedAt && (
                                      <div className="my-requests-card__meta-row">
                                        <i className="fas fa-history"></i>
                                        Güncelleme: {formatDate(request.reviewedAt)}
                                      </div>
                                    )}
                                  </div>
                                  <div className="d-flex flex-wrap gap-2 mb-3">
                                    <span className="badge bg-primary">
                                      <i className="fas fa-hashtag me-1"></i>
                                      {request.quantity}
                                    </span>
                                    <span className={`badge ${request.type === 'ADD' ? 'bg-success' : 'bg-danger'}`}>
                                      <i className={`fas fa-${request.type === 'ADD' ? 'plus' : 'minus'} me-1`}></i>
                                      {request.type === 'ADD' ? 'Ekleme' : 'Çıkarma'}
                                    </span>
                                  </div>
                                  <div className="my-requests-actions d-flex flex-wrap gap-2">
                                    {request.notes && (
                                      <button
                                        className="btn btn-outline-secondary btn-sm flex-fill"
                                        onClick={() =>
                                          setNotesModal({
                                            show: true,
                                            title: 'Talep Notları',
                                            notes: request.notes,
                                            requestId: request.id
                                          })
                                        }
                                      >
                                        <i className="fas fa-sticky-note me-1"></i>
                                        Notu Gör
                                      </button>
                                    )}
                                    {request.status === 'REJECTED' && request.rejectionReason && (
                                      <button
                                        className="btn btn-outline-danger btn-sm flex-fill"
                                        onClick={() =>
                                          setNotesModal({
                                            show: true,
                                            title: 'Reddetme Nedeni',
                                            notes: request.rejectionReason,
                                            requestId: request.id
                                          })
                                        }
                                      >
                                        <i className="fas fa-comment-slash me-1"></i>
                                        Ret Detayı
                                      </button>
                                    )}
                                    {canDelete && (
                                      <button
                                        className="btn btn-outline-danger btn-sm flex-fill"
                                        onClick={() => handleDelete(request.id)}
                                        disabled={deletingId === request.id}
                                      >
                                        {deletingId === request.id ? (
                                          <span className="spinner-border spinner-border-sm"></span>
                                        ) : (
                                          <>
                                            <i className="fas fa-trash me-1"></i>
                                            Talebi Sil
                                          </>
                                        )}
                                      </button>
                                    )}
                                  </div>
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      </>
                    )}
                  </div>
                </>
              ) : (
                <>
                  <div className="row g-3 p-3 bg-light border-bottom">
                    <div className="col-md-4">
                      <div className="card border-warning shadow-sm h-100">
                        <div className="card-body text-center">
                          <i className="fas fa-clock fa-2x text-warning mb-2"></i>
                          <h3 className="mb-0">{transferStats.pending}</h3>
                          <p className="text-muted mb-0 small">Bekleyen Transferler</p>
                        </div>
                      </div>
                    </div>
                    <div className="col-md-4">
                      <div className="card border-success shadow-sm h-100">
                        <div className="card-body text-center">
                          <i className="fas fa-check-circle fa-2x text-success mb-2"></i>
                          <h3 className="mb-0">{transferStats.approved}</h3>
                          <p className="text-muted mb-0 small">Onaylananlar</p>
                        </div>
                      </div>
                    </div>
                    <div className="col-md-4">
                      <div className="card border-danger shadow-sm h-100">
                        <div className="card-body text-center">
                          <i className="fas fa-times-circle fa-2x text-danger mb-2"></i>
                          <h3 className="mb-0">{transferStats.rejected}</h3>
                          <p className="text-muted mb-0 small">Reddedilenler</p>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="p-3 border-bottom bg-white">
                    <div className="btn-group flex-wrap w-100" role="group">
                      {filterOptions.map(option => (
                        <React.Fragment key={option.key}>
                          <input
                            type="radio"
                            className="btn-check"
                            name="transferRequestFilter"
                            id={`transfer-req-${option.key}`}
                            value={option.key}
                            checked={transferFilter === option.key}
                            onChange={(e) => setTransferFilter(e.target.value)}
                          />
                          <label className={`btn btn-outline-${option.variant}`} htmlFor={`transfer-req-${option.key}`}>
                            {option.label}
                          </label>
                        </React.Fragment>
                      ))}
                    </div>
                  </div>

                  <div className="p-3" style={{ minHeight: '300px' }}>
                    {transferLoading ? (
                      <div className="text-center py-5">
                        <div className="spinner-border text-info" role="status">
                          <span className="visually-hidden">Yükleniyor...</span>
                        </div>
                      </div>
                    ) : filteredTransferRequests.length === 0 ? (
                      <div className="text-center py-5">
                        <i className="fas fa-inbox fa-3x text-muted mb-3"></i>
                        <p className="text-muted mb-0">
                          {transferFilter === 'PENDING' ? 'Bekleyen transfer talebiniz bulunmuyor' : 'Bu filtrede kayıt bulunamadı'}
                        </p>
                      </div>
                    ) : (
                      <>
                        <div className="d-none d-md-block table-responsive">
                          <table className="table table-hover align-middle mb-0">
                            <thead className="table-light">
                              <tr>
                                <th style={{ width: '70px' }}>ID</th>
                                <th>Rota</th>
                                <th>Ürünler</th>
                                <th className="text-center">Tip</th>
                                <th className="text-center">Durum</th>
                                <th style={{ width: '160px' }}>Tarih</th>
                                <th className="text-center" style={{ width: '160px' }}>İşlemler</th>
                              </tr>
                            </thead>
                            <tbody>
                              {filteredTransferRequests.map(transfer => {
                                const status = statusConfig[transfer.approvalStatus] || statusConfig.PENDING;
                                const sourceName = transfer.sourceWarehouse?.name || 'Bilinmiyor';
                                const destName =
                                  transfer.transferType === 'CUSTOMER_DELIVERY'
                                    ? (transfer.customerFullName || 'Müşteri')
                                    : (transfer.destinationWarehouse?.name || 'Bilinmiyor');
                                return (
                                  <tr key={transfer.id}>
                                    <td>
                                      <span className="badge bg-dark">#{transfer.id}</span>
                                    </td>
                                    <td>
                                      <div className="fw-bold">{sourceName} → {destName}</div>
                                      {transfer.transferType === 'CUSTOMER_DELIVERY' && transfer.customerPhone && (
                                        <small className="text-muted">{transfer.customerPhone}</small>
                                      )}
                                    </td>
                                    <td>
                                      <small>{getTransferItemsDescription(transfer)}</small>
                                    </td>
                                    <td className="text-center">
                                      <span className={`badge ${transfer.transferType === 'CUSTOMER_DELIVERY' ? 'bg-info' : 'bg-primary'}`}>
                                        {transfer.transferType === 'CUSTOMER_DELIVERY' ? 'Müşteri Sevkiyatı' : 'Depo Transferi'}
                                      </span>
                                    </td>
                                    <td className="text-center">
                                      <span className={`badge bg-${status.className}`}>
                                        <i className={`fas ${status.icon} me-1`}></i>
                                        {status.label}
                                      </span>
                                    </td>
                                    <td>
                                      <small className="text-muted d-block">{formatDate(transfer.transferDate)}</small>
                                      {transfer.approvalDecisionAt && (
                                        <small className="text-muted d-block">
                                          Karar: {formatDate(transfer.approvalDecisionAt)}
                                        </small>
                                      )}
                                    </td>
                                    <td className="text-center">
                                      <div className="d-flex gap-1 justify-content-center">
                                        {transfer.approvalNote && (
                                          <button
                                            className="btn btn-sm btn-outline-secondary"
                                            title="Notları Gör"
                                            onClick={() => setNotesModal({
                                              show: true,
                                              title: transfer.approvalStatus === 'REJECTED' ? 'Reddetme Nedeni' : 'Onay Notu',
                                              notes: transfer.approvalNote,
                                              requestId: transfer.id
                                            })}
                                          >
                                            <i className="fas fa-sticky-note"></i>
                                          </button>
                                        )}
                                        {!transfer.approvalNote && (
                                          <span className="text-muted small">-</span>
                                        )}
                                      </div>
                                    </td>
                                  </tr>
                                );
                              })}
                            </tbody>
                          </table>
                        </div>
                        <div className="d-md-none px-3 pb-3 my-requests-mobile-list">
                          {filteredTransferRequests.map(transfer => {
                            const status = statusConfig[transfer.approvalStatus] || statusConfig.PENDING;
                            const sourceName = transfer.sourceWarehouse?.name || 'Bilinmiyor';
                            const destName =
                              transfer.transferType === 'CUSTOMER_DELIVERY'
                                ? (transfer.customerFullName || 'Müşteri')
                                : (transfer.destinationWarehouse?.name || 'Bilinmiyor');
                            return (
                              <div key={transfer.id} className="my-requests-card card border-0">
                                <div className="card-body">
                                  <div className="my-requests-card__header mb-2">
                                    <div>
                                      <div className="my-requests-card__title">Transfer #{transfer.id}</div>
                                      <small className="my-requests-card__meta">
                                        {transfer.transferType === 'CUSTOMER_DELIVERY' ? 'Müşteri Sevkiyatı' : 'Depo Transferi'}
                                      </small>
                                    </div>
                                    <span className={`my-requests-pill badge bg-${status.className}`}>
                                      <i className={`fas ${status.icon} me-1`}></i>
                                      {status.label}
                                    </span>
                                  </div>
                                  <div className="my-requests-card__details mb-3">
                                    <div className="my-requests-card__meta-row fw-semibold text-dark">
                                      <i className="fas fa-route text-primary"></i>
                                      {sourceName} → {destName}
                                    </div>
                                    {transfer.transferType === 'CUSTOMER_DELIVERY' && transfer.customerPhone && (
                                      <div className="my-requests-card__meta-row">
                                        <i className="fas fa-phone"></i>
                                        {transfer.customerPhone}
                                      </div>
                                    )}
                                    <div className="my-requests-card__meta-row">
                                      <i className="fas fa-box"></i>
                                      {getTransferItemsDescription(transfer)}
                                    </div>
                                    <div className="my-requests-card__meta-row">
                                      <i className="fas fa-calendar"></i>
                                      {formatDate(transfer.transferDate)}
                                    </div>
                                    {transfer.approvalDecisionAt && (
                                      <div className="my-requests-card__meta-row">
                                        <i className="fas fa-history"></i>
                                        Karar: {formatDate(transfer.approvalDecisionAt)}
                                      </div>
                                    )}
                                  </div>
                                  <div className="my-requests-actions d-flex flex-wrap gap-2">
                                    {transfer.approvalNote ? (
                                      <button
                                        className="btn btn-outline-secondary btn-sm flex-fill"
                                        onClick={() =>
                                          setNotesModal({
                                            show: true,
                                            title: transfer.approvalStatus === 'REJECTED' ? 'Reddetme Nedeni' : 'Onay Notu',
                                            notes: transfer.approvalNote,
                                            requestId: transfer.id
                                          })
                                        }
                                      >
                                        <i className="fas fa-sticky-note me-1"></i>
                                        Notu Gör
                                      </button>
                                    ) : (
                                      <span className="text-muted small">Not bulunmuyor</span>
                                    )}
                                  </div>
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      </>
                    )}
                  </div>
                </>
              )}
            </div>

            <div className="modal-footer bg-light">
              <button type="button" className="btn btn-secondary" onClick={onClose}>
                <i className="fas fa-times me-2"></i>
                Kapat
              </button>
            </div>
          </div>
        </div>
      </div>

      <NotesModal
        show={notesModal.show}
        title={notesModal.title}
        notes={notesModal.notes}
        transferId={notesModal.requestId}
        onClose={() => setNotesModal({ show: false, title: '', notes: '', requestId: null })}
      />
    </>
  );
};

export default MyStockRequestsModal;
