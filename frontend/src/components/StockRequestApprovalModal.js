import React, { useState, useEffect } from 'react';
import axios from 'axios';
import useSecurityCodePrompt from './useSecurityCodePrompt';

const StockRequestApprovalModal = ({ onClose, onApprove, initialTab = 'stock' }) => {
  const [activeTab, setActiveTab] = useState(initialTab);

  useEffect(() => {
    if (initialTab) {
      setActiveTab(initialTab);
    }
  }, [initialTab]);
  const [allRequests, setAllRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [processing, setProcessing] = useState(null);
  const [rejectionModal, setRejectionModal] = useState({ show: false, id: null, reason: '', type: 'stock' });
  const [notesModal, setNotesModal] = useState({ show: false, title: '', content: '', type: 'info' });
  const [requestDetailModal, setRequestDetailModal] = useState({ show: false, payload: null, context: 'stock' });
  const [transferDetailPhotos, setTransferDetailPhotos] = useState({});
  const [transferLightbox, setTransferLightbox] = useState({ show: false, images: [], index: 0 });

  const openTransferLightbox = (transfer, items, photos, activeItemId) => {
    const images = (items || [])
      .map((item) => {
        const meta = photos[item.id];
        if (!meta) return null;
        const src = meta.viewUrl || meta.thumbnailUrl;
        if (!src) return null;
        return {
          src,
          title: `${item.product?.name || 'Ürün'} • ${item.product?.sku || ''}`.trim()
        };
      })
      .filter(Boolean);

    if (images.length === 0) return;

    const activeMeta = photos[activeItemId];
    const activeSrc = activeMeta?.viewUrl || activeMeta?.thumbnailUrl;
    const startIndex = Math.max(
      0,
      images.findIndex((img) => img.src === activeSrc)
    );

    setTransferLightbox({
      show: true,
      images,
      index: startIndex === -1 ? 0 : startIndex
    });
  };
  const [filter, setFilter] = useState('PENDING');
  const [allTransferApprovals, setAllTransferApprovals] = useState([]);
  const [transferApprovals, setTransferApprovals] = useState([]);
  const [transferLoading, setTransferLoading] = useState(true);
  const [transferProcessing, setTransferProcessing] = useState(null);
  const [transferFilter, setTransferFilter] = useState('PENDING');
  const { askCode: askSecurityCode, SecurityCodePrompt } = useSecurityCodePrompt();

  useEffect(() => {
    fetchStockRequests();
  }, []);

  useEffect(() => {
    fetchTransferApprovals();
  }, []);

  useEffect(() => {
    setTransferApprovals(
      allTransferApprovals.filter(approval => matchesFilter(approval.approvalStatus, transferFilter))
    );
  }, [transferFilter, allTransferApprovals]);

  const fetchStockRequests = async () => {
    try {
      setLoading(true);
      // Fetch ALL requests to calculate counts correctly
      const response = await axios.get('/api/stock-requests');
      console.log('📊 Stock Requests Response:', response.data);
      console.log('📊 Total requests:', response.data.length);
      console.log('📊 PENDING:', response.data.filter(r => r.status === 'PENDING').length);
      console.log('📊 APPROVED:', response.data.filter(r => r.status === 'APPROVED').length);
      console.log('📊 REJECTED:', response.data.filter(r => r.status === 'REJECTED').length);
      setAllRequests(response.data);
    } catch (error) {
      console.error('❌ Error fetching requests:', error);
      console.error('❌ Error response:', error.response);
    } finally {
      setLoading(false);
    }
  };

  const fetchTransferApprovals = async () => {
    try {
      setTransferLoading(true);
      const statuses = ['PENDING', 'APPROVED', 'REJECTED'];

      const responses = await Promise.all(
        statuses.map(async (status) => {
          try {
            const { data } = await axios.get('/api/stock-transfers/approvals', {
              params: { status }
            });
            const list = Array.isArray(data) ? data : [];
            return list.map((item) => ({
              ...item,
              approvalStatus: item.approvalStatus || status
            }));
          } catch (error) {
            console.error(`❌ Error fetching transfer approvals for ${status}:`, error);
            return [];
          }
        })
      );

      const mergedList = responses.flat();
      setAllTransferApprovals(mergedList);
      setTransferApprovals(mergedList.filter((approval) => matchesFilter(approval.approvalStatus, transferFilter)));
    } catch (error) {
      console.error('❌ Error fetching transfer approvals:', error);
    } finally {
      setTransferLoading(false);
    }
  };

  const handleApprove = async (requestId) => {
    try {
      setProcessing(requestId);
      await axios.post(`/api/stock-requests/${requestId}/approve`);
      await fetchStockRequests();
      if (onApprove) onApprove();
    } catch (error) {
      alert(error.response?.data?.message || 'Onaylama hatası');
    } finally {
      setProcessing(null);
    }
  };

  const handleApproveTransfer = async (transferId) => {
    try {
      setTransferProcessing(transferId);
      const code = await askSecurityCode();
      if (code === null) return;
      await axios.post(`/api/stock-transfers/${transferId}/approve-start`, {}, { headers: { 'X-ADMIN-SECURITY-CODE': code } });
      await fetchTransferApprovals();
      if (onApprove) onApprove();
    } catch (error) {
      alert(error.response?.data?.message || 'Onaylama hatası');
    } finally {
      setTransferProcessing(null);
    }
  };

  const handleReject = async () => {
    if (rejectionModal.type === 'transfer') {
      try {
        setTransferProcessing(rejectionModal.id);
        await axios.post(`/api/stock-transfers/${rejectionModal.id}/reject-start`, {
          reason: rejectionModal.reason
        });
        setRejectionModal({ show: false, id: null, reason: '', type: 'transfer' });
        await fetchTransferApprovals();
        if (onApprove) onApprove();
      } catch (error) {
        alert(error.response?.data?.message || 'Reddetme hatası');
      } finally {
        setTransferProcessing(null);
      }
    } else {
      try {
        setProcessing(rejectionModal.id);
        await axios.post(`/api/stock-requests/${rejectionModal.id}/reject`, {
          rejectionReason: rejectionModal.reason
        });
        setRejectionModal({ show: false, id: null, reason: '', type: 'stock' });
        await fetchStockRequests();
        if (onApprove) onApprove();
      } catch (error) {
        alert(error.response?.data?.message || 'Reddetme hatası');
      } finally {
        setProcessing(null);
      }
    }
  };

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleString('tr-TR', {
      timeZone: 'Europe/Istanbul',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  const normalizeStatus = (value) => (value || '').toUpperCase();

  const matchesFilter = (statusValue, filterValue) => {
    if (!filterValue || filterValue === 'ALL') return true;
    const normalizedFilter = normalizeStatus(filterValue);
    const normalizedStatus = normalizeStatus(statusValue);

    const startsWithFilter = () => normalizedStatus.startsWith(normalizedFilter);

    switch (normalizedFilter) {
      case 'PENDING':
      case 'APPROVED':
      case 'REJECTED':
        return startsWithFilter();
      default:
        return normalizedStatus === normalizedFilter;
    }
  };

  const getStatusMeta = (statusValue) => {
    const status = normalizeStatus(statusValue);
    if (status.startsWith('PENDING')) {
      return { label: 'Bekliyor', className: 'warning', icon: 'fa-clock' };
    }
    if (status === 'APPROVED') {
      return { label: 'Onaylandı', className: 'success', icon: 'fa-check' };
    }
    if (status === 'REJECTED') {
      return { label: 'Reddedildi', className: 'danger', icon: 'fa-times' };
    }
    return { label: 'İşlendi', className: 'secondary', icon: 'fa-info-circle' };
  };

  // Calculate counts from all requests
  const pendingCount = allRequests.filter(r => matchesFilter(r.status, 'PENDING')).length;
  const approvedCount = allRequests.filter(r => matchesFilter(r.status, 'APPROVED')).length;
  const rejectedCount = allRequests.filter(r => matchesFilter(r.status, 'REJECTED')).length;

  // Filter requests based on selected filter
  const requests = allRequests.filter(r => matchesFilter(r.status, filter));

  const transferPendingCount = allTransferApprovals.filter(a => matchesFilter(a.approvalStatus, 'PENDING')).length;
  const transferApprovedCount = allTransferApprovals.filter(a => matchesFilter(a.approvalStatus, 'APPROVED')).length;
  const transferRejectedCount = allTransferApprovals.filter(a => matchesFilter(a.approvalStatus, 'REJECTED')).length;

  return (
    <>
      {SecurityCodePrompt}
      <style>{`
        .approval-tab-toggle,
        .approval-filter-stack {
          display: flex;
          flex-wrap: wrap;
          gap: 0.75rem;
        }
        .approval-tab-toggle .toggle-option,
        .approval-filter-stack .toggle-option {
          flex: 1 1 200px;
        }
        .approval-tab-toggle .btn,
        .approval-filter-stack .btn {
          width: 100%;
          border-radius: 16px;
          padding: 0.6rem 1rem;
          font-weight: 600;
          box-shadow: inset 0 0 0 1px rgba(15,23,42,0.05);
        }
        @media (max-width: 1155px) {
          .approval-filter-group {
            flex-direction: column;
          }
          .approval-filter-group .btn {
            flex: 1 1 auto;
          }
        }
        .table-responsive {
          overflow-x: auto;
          -webkit-overflow-scrolling: touch;
          width: 100%;
        }
        .table-responsive table {
          width: 100%;
          table-layout: auto;
          min-width: 100%;
        }
        @media (max-width: 1155px) {
          .table-responsive th,
          .table-responsive td {
            padding: 0.5rem 0.4rem;
            font-size: 0.85rem;
          }
          .table-responsive .btn-sm {
            padding: 0.25rem 0.4rem;
            font-size: 0.75rem;
          }
        }
        @media (max-width: 1155px) {
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
      <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 1050, padding: '1rem' }}>
        <div className="modal-dialog modal-xl modal-dialog-scrollable" style={{ maxWidth: 'min(1100px, 95vw)' }}>
          <div className="modal-content rounded-4 border-0 shadow-lg" style={{ maxHeight: '90vh' }}>
            <div className="modal-header bg-primary text-white">
              <h5 className="modal-title">
                <i className="fas fa-tasks me-2"></i>
                Stok Talep Onay Sistemi
              </h5>
              <button type="button" className="btn-close btn-close-white" onClick={onClose}></button>
            </div>

            <div className="modal-body p-0">
              <div className="p-3 border-bottom bg-white">
                <div className="approval-tab-toggle" role="group">
                  <div className="toggle-option">
                    <input
                      type="radio"
                      className="btn-check"
                      name="approvalTab"
                      id="tab-stock"
                      value="stock"
                      checked={activeTab === 'stock'}
                      onChange={() => setActiveTab('stock')}
                    />
                    <label className="btn btn-outline-primary" htmlFor="tab-stock">
                      <i className="fas fa-boxes me-2"></i>
                      Stok Talepleri
                    </label>
                  </div>
                  <div className="toggle-option">
                    <input
                      type="radio"
                      className="btn-check"
                      name="approvalTab"
                      id="tab-transfer"
                      value="transfer"
                      checked={activeTab === 'transfer'}
                      onChange={() => setActiveTab('transfer')}
                    />
                    <label className="btn btn-outline-info" htmlFor="tab-transfer">
                      <i className="fas fa-exchange-alt me-2"></i>
                      Transfer Talepleri
                    </label>
                  </div>
                </div>
              </div>

              {activeTab === 'stock' ? (
                <>
                  {/* Statistics Cards */}
                  <div className="row g-3 p-3 bg-light">
                    <div className="col-12 col-md-4">
                      <div className="card border-warning shadow-sm h-100">
                        <div className="card-body text-center">
                          <i className="fas fa-clock fa-2x text-warning mb-2"></i>
                          <h3 className="mb-0">{pendingCount}</h3>
                          <p className="text-muted mb-0 small">Bekleyen Talepler</p>
                        </div>
                      </div>
                    </div>
                    <div className="col-12 col-md-4">
                      <div className="card border-success shadow-sm h-100">
                        <div className="card-body text-center">
                          <i className="fas fa-check-circle fa-2x text-success mb-2"></i>
                          <h3 className="mb-0">{approvedCount}</h3>
                          <p className="text-muted mb-0 small">Onaylanan</p>
                        </div>
                      </div>
                    </div>
                    <div className="col-12 col-md-4">
                      <div className="card border-danger shadow-sm h-100">
                        <div className="card-body text-center">
                          <i className="fas fa-times-circle fa-2x text-danger mb-2"></i>
                          <h3 className="mb-0">{rejectedCount}</h3>
                          <p className="text-muted mb-0 small">Reddedilen</p>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Filter Buttons */}
                  <div className="p-3 border-bottom bg-white">
                    <div className="approval-filter-stack" role="group">
                      <div className="toggle-option">
                        <input
                          type="radio"
                          className="btn-check"
                          name="requestFilter"
                          id="filter-pending"
                          value="PENDING"
                          checked={filter === 'PENDING'}
                          onChange={(e) => setFilter(e.target.value)}
                        />
                        <label className="btn btn-outline-warning" htmlFor="filter-pending">
                          <i className="fas fa-clock me-1"></i>
                          Bekleyen ({pendingCount})
                        </label>
                      </div>
                      <div className="toggle-option">
                        <input
                          type="radio"
                          className="btn-check"
                          name="requestFilter"
                          id="filter-approved"
                          value="APPROVED"
                          checked={filter === 'APPROVED'}
                          onChange={(e) => setFilter(e.target.value)}
                        />
                        <label className="btn btn-outline-success" htmlFor="filter-approved">
                          <i className="fas fa-check me-1"></i>
                          Onaylanan ({approvedCount})
                        </label>
                      </div>
                      <div className="toggle-option">
                        <input
                          type="radio"
                          className="btn-check"
                          name="requestFilter"
                          id="filter-rejected"
                          value="REJECTED"
                          checked={filter === 'REJECTED'}
                          onChange={(e) => setFilter(e.target.value)}
                        />
                        <label className="btn btn-outline-danger" htmlFor="filter-rejected">
                          <i className="fas fa-times me-1"></i>
                          Reddedilen ({rejectedCount})
                        </label>
                      </div>
                      <div className="toggle-option">
                        <input
                          type="radio"
                          className="btn-check"
                          name="requestFilter"
                          id="filter-all"
                          value="ALL"
                          checked={filter === 'ALL'}
                          onChange={(e) => setFilter(e.target.value)}
                        />
                        <label className="btn btn-outline-secondary" htmlFor="filter-all">
                          <i className="fas fa-list me-1"></i>
                          Tümü ({allRequests.length})
                        </label>
                      </div>
                    </div>
                  </div>

                  {/* Requests Table */}
                  <div className="breakpoint-1155-desktop">
                    {loading ? (
                      <div className="text-center py-5">
                        <div className="spinner-border text-primary" role="status">
                          <span className="visually-hidden">Yükleniyor...</span>
                        </div>
                      </div>
                    ) : requests.length === 0 ? (
                      <div className="text-center py-5">
                        <i className="fas fa-inbox fa-4x text-muted mb-3 d-block"></i>
                        <h5 className="text-muted">
                          {filter === 'PENDING' && 'Bekleyen talep bulunmuyor'}
                          {filter === 'APPROVED' && 'Onaylanmış talep bulunmuyor'}
                          {filter === 'REJECTED' && 'Reddedilmiş talep bulunmuyor'}
                          {filter === 'ALL' && 'Hiç talep bulunmuyor'}
                        </h5>
                      </div>
                    ) : (
                      <div className="table-responsive" style={{ overflowX: 'auto' }}>
                        <table className="table table-hover align-middle mb-0" style={{ width: '100%', tableLayout: 'auto' }}>
                          <thead className="table-light sticky-top" style={{ top: 0 }}>
                            <tr>
                              <th className="text-center" style={{ width: 'auto', minWidth: '60px' }}>ID</th>
                              <th style={{ width: 'auto', minWidth: '120px' }}>Ürün</th>
                              <th style={{ width: 'auto', minWidth: '100px' }}>Depo</th>
                              <th className="text-center" style={{ width: 'auto', minWidth: '90px' }}>İşlem</th>
                              <th className="text-center" style={{ width: 'auto', minWidth: '70px' }}>Miktar</th>
                              <th style={{ width: 'auto', minWidth: '100px' }}>Mevcut Stok</th>
                              <th style={{ width: 'auto', minWidth: '100px' }}>Talep Eden</th>
                              <th style={{ width: 'auto', minWidth: '120px' }}>Talep Tarihi</th>
                              <th className="text-center" style={{ width: 'auto', minWidth: '90px' }}>Durum</th>
                              <th className="text-center" style={{ width: 'auto', minWidth: '150px' }}>İşlemler</th>
                            </tr>
                          </thead>
                          <tbody>
                            {requests.map((request) => (
                              <tr key={request.id} className={request.status === 'PENDING' ? 'table-warning' : ''}>
                                <td className="text-center">
                                  <span className="badge bg-dark">#{request.id}</span>
                                </td>
                                <td>
                                  <div className="fw-bold text-truncate" style={{ maxWidth: '200px' }} title={request.productName}>{request.productName}</div>
                                  <small className="text-muted text-truncate d-block" style={{ maxWidth: '200px' }} title={request.productSku}>SKU: {request.productSku}</small>
                                </td>
                                <td className="text-truncate" style={{ maxWidth: '150px' }} title={request.warehouseName}>{request.warehouseName}</td>
                                <td className="text-center">
                                  <span className={`badge ${request.type === 'ADD' ? 'bg-success' : 'bg-danger'}`}>
                                    <i className={`fas fa-${request.type === 'ADD' ? 'plus' : 'minus'} me-1`}></i>
                                    {request.type === 'ADD' ? 'Ekleme' : 'Çıkarma'}
                                  </span>
                                </td>
                                <td className="text-center">
                                  <span className="badge bg-primary fs-6">{request.quantity}</span>
                                </td>
                                <td>
                                  <div className="small">
                                    <div>Toplam: <strong>{request.currentStockQuantity}</strong></div>
                                    <div className="text-muted">Kullanılabilir: {request.availableQuantity}</div>
                                  </div>
                                </td>
                                <td>
                                  <i className="fas fa-user me-1 text-muted"></i>
                                  {request.requestedBy}
                                </td>
                                <td>
                                  <small>{formatDate(request.requestedAt)}</small>
                                </td>
                                <td className="text-center">
                                  {request.status === 'PENDING' && (
                                    <span className="badge bg-warning">
                                      <i className="fas fa-clock me-1"></i>Bekliyor
                                    </span>
                                  )}
                                  {request.status === 'APPROVED' && (
                                    <div>
                                      <span className="badge bg-success d-block mb-1">
                                        <i className="fas fa-check me-1"></i>Onaylandı
                                      </span>
                                      <small className="text-muted d-block">{request.reviewedBy}</small>
                                      <small className="text-muted d-block">{formatDate(request.reviewedAt)}</small>
                                    </div>
                                  )}
                                  {request.status === 'REJECTED' && (
                                    <div>
                                      <span className="badge bg-danger d-block mb-1">
                                        <i className="fas fa-times me-1"></i>Reddedildi
                                      </span>
                                      <small className="text-muted d-block">{request.reviewedBy}</small>
                                      <small className="text-muted d-block">{formatDate(request.reviewedAt)}</small>
                                    </div>
                                  )}
                                </td>
                                <td className="text-center">
                                  {request.status === 'PENDING' ? (
                                    <div className="d-flex gap-1 justify-content-center">
                                      <button
                                        className="btn btn-sm btn-success"
                                        onClick={() => handleApprove(request.id)}
                                        disabled={processing === request.id}
                                        title="Talebi onayla"
                                      >
                                        {processing === request.id ? (
                                          <span className="spinner-border spinner-border-sm"></span>
                                        ) : (
                                          <>
                                            <i className="fas fa-check me-1"></i>
                                            Onayla
                                          </>
                                        )}
                                      </button>
                                      <button
                                        className="btn btn-sm btn-danger"
                                        onClick={() => setRejectionModal({ show: true, id: request.id, reason: '', type: 'stock' })}
                                        disabled={processing === request.id}
                                        title="Talebi reddet"
                                      >
                                        <i className="fas fa-times me-1"></i>
                                        Reddet
                                      </button>
                                    </div>
                                  ) : (
                                    <div className="d-flex gap-1 justify-content-center">
                                      {request.status === 'REJECTED' && request.rejectionReason && (
                                        <button
                                          className="btn btn-sm btn-outline-danger"
                                          onClick={() => setNotesModal({
                                            show: true,
                                            title: 'Reddetme Nedeni',
                                            content: request.rejectionReason,
                                            type: 'danger',
                                            icon: 'fa-times-circle',
                                            productName: request.productName,
                                            requestType: request.type
                                          })}
                                          title="Ret nedenini gör"
                                        >
                                          <i className="fas fa-comment-alt"></i>
                                        </button>
                                      )}
                                      {request.notes && (
                                        <button
                                          className="btn btn-sm btn-outline-info"
                                          onClick={() => setNotesModal({
                                            show: true,
                                            title: 'Talep Notları',
                                            content: request.notes,
                                            type: 'info',
                                            icon: 'fa-sticky-note',
                                            productName: request.productName,
                                            requestType: request.type
                                          })}
                                          title="Notları gör"
                                        >
                                          <i className="fas fa-sticky-note"></i>
                                        </button>
                                      )}
                                      {!request.rejectionReason && !request.notes && (
                                        <span className="text-muted small">-</span>
                                      )}
                                    </div>
                                  )}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}
                  </div>
                  <div className="breakpoint-1155-mobile px-3 pb-3">
                    {loading ? (
                      <div className="text-center py-5">
                        <div className="spinner-border text-primary" role="status">
                          <span className="visually-hidden">Yükleniyor...</span>
                        </div>
                      </div>
                    ) : requests.length === 0 ? (
                      <div className="text-center py-5 text-muted">
                        <i className="fas fa-inbox fa-3x mb-2"></i>
                        <p className="mb-0">Seçilen filtreye uygun talep bulunamadı.</p>
                      </div>
                    ) : (
                      <div className="my-requests-mobile-list mt-3">
                        {requests.map((request) => {
                          const statusMeta = getStatusMeta(request.status);
                          return (
                            <div key={request.id} className="my-requests-card card border-0">
                              <div className="card-body">
                                <div className="my-requests-card__header mb-2">
                                  <div>
                                    <div className="my-requests-card__title">Talep #{request.id}</div>
                                    <small className="my-requests-card__meta">
                                      {request.requestType === 'ADD' ? 'Stok Ekleme' : 'Stok Çıkarma'}
                                    </small>
                                  </div>
                                  <span className={`my-requests-pill badge bg-${statusMeta.className}`}>
                                    <i className={`fas ${statusMeta.icon} me-1`}></i>
                                    {statusMeta.label}
                                  </span>
                                </div>
                                <div className="my-requests-card__details mb-3">
                                  <div className="my-requests-card__meta-row fw-semibold text-dark">
                                    <i className="fas fa-box text-primary"></i>
                                    {request.stock?.product?.name || request.productName || '-'}
                                  </div>
                                  <div className="my-requests-card__meta-row">
                                    <i className="fas fa-barcode"></i>
                                    {request.stock?.product?.sku || request.productSku || '-'}
                                  </div>
                                  <div className="my-requests-card__meta-row">
                                    <i className="fas fa-warehouse"></i>
                                    {request.stock?.warehouse?.name || request.warehouseName || '-'}
                                  </div>
                                  <div className="my-requests-card__meta-row">
                                    <i className="fas fa-user"></i>
                                    {request.requestedBy || '-'}
                                  </div>
                                  <div className="my-requests-card__meta-row">
                                    <i className="fas fa-calendar"></i>
                                    {formatDate(request.createdAt || request.requestedAt)}
                                  </div>
                                </div>
                                <div className="d-flex flex-wrap gap-2 mb-3">
                                  <span className="badge bg-primary">
                                    <i className="fas fa-hashtag me-1"></i>
                                    {request.quantity}
                                  </span>
                                  <span className="badge bg-light text-dark">
                                    <i className="fas fa-layer-group me-1"></i>
                                    Toplam {request.currentStockQuantity ?? request.stock?.quantity ?? '-'}
                                  </span>
                                  <span className={`badge ${request.requestType === 'ADD' ? 'bg-success' : 'bg-danger'}`}>
                                    <i className={`fas fa-${request.requestType === 'ADD' ? 'plus' : 'minus'} me-1`}></i>
                                    {request.requestType === 'ADD' ? 'Ekleme' : 'Çıkarma'}
                                  </span>
                                </div>
                                {request.notes && (
                                  <button
                                    className="btn btn-outline-secondary btn-sm w-100 mb-3"
                                    onClick={() =>
                                      setNotesModal({ show: true, title: 'Talep Notu', content: request.notes, type: 'info' })
                                    }
                                  >
                                    <i className="fas fa-sticky-note me-1"></i>
                                    Talep notunu gör
                                  </button>
                                )}
                                <button
                                  className="btn btn-outline-primary btn-sm w-100 mb-2"
                                  onClick={() => setRequestDetailModal({ show: true, payload: request, context: 'stock' })}
                                >
                                  <i className="fas fa-eye me-1"></i>
                                  Detay
                                </button>
                                <div className="my-requests-actions d-flex flex-wrap gap-2 justify-content-center">
                                  {matchesFilter(request.status, 'PENDING') ? (
                                    <>
                                      <button
                                        className="btn btn-success btn-sm flex-fill"
                                        onClick={() => handleApprove(request.id)}
                                        disabled={processing === request.id}
                                      >
                                        {processing === request.id ? (
                                          <span className="spinner-border spinner-border-sm"></span>
                                        ) : (
                                          <>
                                            <i className="fas fa-check me-1"></i>
                                            Onayla
                                          </>
                                        )}
                                      </button>
                                      <button
                                        className="btn btn-outline-danger btn-sm flex-fill"
                                        onClick={() => setRejectionModal({ show: true, id: request.id, reason: '', type: 'stock' })}
                                        disabled={processing === request.id}
                                      >
                                        <i className="fas fa-times me-1"></i>
                                        Reddet
                                      </button>
                                    </>
                                  ) : request.rejectionReason ? (
                                    <button
                                      className="btn btn-outline-danger btn-sm flex-fill"
                                      onClick={() =>
                                        setNotesModal({
                                          show: true,
                                          title: 'Reddetme Nedeni',
                                          content: request.rejectionReason,
                                          type: 'danger'
                                        })
                                      }
                                    >
                                      <i className="fas fa-comment-alt me-1"></i>
                                      Ret Notu
                                    </button>
                                  ) : request.notes ? (
                                    <button
                                      className="btn btn-outline-secondary btn-sm flex-fill"
                                      onClick={() =>
                                        setNotesModal({
                                          show: true,
                                          title: 'Talep Notları',
                                          content: request.notes,
                                          type: 'info'
                                        })
                                      }
                                    >
                                      <i className="fas fa-sticky-note me-1"></i>
                                      Notu Gör
                                    </button>
                                  ) : (
                                    <span className="text-muted small">Tamamlandı</span>
                                  )}
                                </div>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                </>
              ) : (
                <>
                  <div className="row g-3 p-3 bg-light border-bottom">
                    <div className="col-12 col-md-4">
                      <div className="card border-warning shadow-sm h-100">
                        <div className="card-body text-center">
                          <i className="fas fa-truck-loading fa-2x text-warning mb-2"></i>
                          <h3 className="mb-0">{transferPendingCount}</h3>
                          <p className="text-muted mb-0 small">Onay Bekleyen Transferler</p>
                        </div>
                      </div>
                    </div>
                    <div className="col-12 col-md-4">
                      <div className="card border-success shadow-sm h-100">
                        <div className="card-body text-center">
                          <i className="fas fa-check-circle fa-2x text-success mb-2"></i>
                          <h3 className="mb-0">{transferApprovedCount}</h3>
                          <p className="text-muted mb-0 small">Onaylanan Transferler</p>
                        </div>
                      </div>
                    </div>
                    <div className="col-12 col-md-4">
                      <div className="card border-danger shadow-sm h-100">
                        <div className="card-body text-center">
                          <i className="fas fa-times-circle fa-2x text-danger mb-2"></i>
                          <h3 className="mb-0">{transferRejectedCount}</h3>
                          <p className="text-muted mb-0 small">Reddedilen Transferler</p>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="p-3 bg-light border-bottom">
                    <div className="approval-filter-stack" role="group">
                      {['PENDING', 'APPROVED', 'REJECTED', 'ALL'].map(status => (
                        <div className="toggle-option" key={status}>
                          <input
                            type="radio"
                            className="btn-check"
                            name="transferFilter"
                            id={`transfer-${status.toLowerCase()}`}
                            value={status}
                            checked={transferFilter === status}
                            onChange={(e) => setTransferFilter(e.target.value)}
                          />
                          <label
                            className={`btn ${status === 'PENDING'
                              ? 'btn-outline-warning'
                              : status === 'APPROVED'
                                ? 'btn-outline-success'
                                : status === 'REJECTED'
                                  ? 'btn-outline-danger'
                                  : 'btn-outline-secondary'
                              }`}
                            htmlFor={`transfer-${status.toLowerCase()}`}
                          >
                            <i className={`fas ${status === 'PENDING'
                              ? 'fa-clock'
                              : status === 'APPROVED'
                                ? 'fa-check'
                                : status === 'REJECTED'
                                  ? 'fa-times'
                                  : 'fa-list'
                              } me-1`}></i>
                            {status === 'PENDING' && 'Bekleyen'}
                            {status === 'APPROVED' && 'Onaylanan'}
                            {status === 'REJECTED' && 'Reddedilen'}
                            {status === 'ALL' && 'Tümü'}
                          </label>
                        </div>
                      ))}
                    </div>
                  </div>
                  <div className="breakpoint-1155-desktop">
                    {transferLoading ? (
                      <div className="text-center py-5">
                        <div className="spinner-border text-info" role="status">
                          <span className="visually-hidden">Yükleniyor...</span>
                        </div>
                      </div>
                    ) : transferApprovals.length === 0 ? (
                      <div className="text-center py-5">
                        <i className="fas fa-inbox fa-4x text-muted mb-3 d-block"></i>
                        <h5 className="text-muted">
                          {transferFilter === 'PENDING' && 'Onay bekleyen transfer bulunmuyor'}
                          {transferFilter === 'APPROVED' && 'Onaylanmış transfer onayı bulunmuyor'}
                          {transferFilter === 'REJECTED' && 'Reddedilmiş transfer onayı bulunmuyor'}
                          {transferFilter === 'ALL' && 'Herhangi bir transfer onayı kaydı bulunmuyor'}
                        </h5>
                      </div>
                    ) : (
                      <div className="table-responsive" style={{ overflowX: 'auto' }}>
                        <table className="table table-hover align-middle mb-0" style={{ width: '100%', tableLayout: 'auto' }}>
                          <thead className="table-light sticky-top" style={{ top: 0 }}>
                            <tr>
                              <th style={{ width: 'auto', minWidth: '70px' }}>Transfer</th>
                              <th style={{ width: 'auto', minWidth: '150px' }}>Rota</th>
                              <th style={{ width: 'auto', minWidth: '150px' }}>Ürünler</th>
                              <th style={{ width: 'auto', minWidth: '120px' }}>Talep Eden</th>
                              <th style={{ width: 'auto', minWidth: '120px' }}>Talep Tarihi</th>
                              <th style={{ width: 'auto', minWidth: '100px' }}>Durum</th>
                              <th style={{ width: 'auto', minWidth: '140px' }}>İşlemler</th>
                            </tr>
                          </thead>
                          <tbody>
                            {transferApprovals.map((transfer) => (
                              <tr key={transfer.id}>
                                <td>
                                  <span className="badge bg-dark">#{transfer.id}</span>
                                  <small className="text-muted text-uppercase">
                                    {transfer.transferType === 'CUSTOMER_DELIVERY' ? 'Müşteri' : 'Depo'}
                                  </small>
                                </td>
                                <td>
                                  {transfer.transferType === 'CUSTOMER_DELIVERY' ? (
                                    <>
                                      <div className="fw-semibold text-truncate" style={{ maxWidth: '200px' }} title={transfer.customerFullName || '-'}>{transfer.customerFullName || '-'}</div>
                                      <small className="text-muted d-block text-truncate" style={{ maxWidth: '200px' }}>
                                        {transfer.sourceWarehouse?.name || '-'} → {transfer.customerFullName || 'Müşteri'}
                                      </small>
                                    </>
                                  ) : (
                                    <>
                                      <div className="fw-semibold text-truncate" style={{ maxWidth: '200px' }} title={transfer.sourceWarehouse?.name || '-'}>{transfer.sourceWarehouse?.name || '-'}</div>
                                      <small className="text-muted d-block text-truncate" style={{ maxWidth: '200px' }} title={`${transfer.sourceWarehouse?.name || '-'} → ${transfer.destinationWarehouse?.name || '-'}`}>
                                        {transfer.sourceWarehouse?.name || '-'} → {transfer.destinationWarehouse?.name || '-'}
                                      </small>
                                    </>
                                  )}
                                </td>
                                <td>
                                  {transfer.items && transfer.items.length > 0 ? (
                                    <>
                                      {transfer.items.slice(0, 3).map(item => (
                                        <div key={`${transfer.id}-${item.id}`} className="d-flex justify-content-between small">
                                          <span className="text-truncate me-2">{item.product?.name || 'Ürün'}</span>
                                          <span className="badge bg-dark text-light d-inline-flex align-items-center justify-content-center mb-1" style={{ minWidth: '3.25rem' }}>{item.quantity}</span>
                                        </div>
                                      ))}
                                      {transfer.items.length > 3 && (
                                        <small className="text-muted">
                                          + {transfer.items.length - 3} ürün daha
                                        </small>
                                      )}
                                    </>
                                  ) : (
                                    <span className="text-muted">Ürün bilgisi yok</span>
                                  )}
                                </td>
                                <td>
                                  <div className="fw-semibold">{transfer.approvalRequestedBy || '-'}</div>
                                  <small className="text-muted">{transfer.createdBy && `Oluşturan: ${transfer.createdBy}`}</small>
                                </td>
                                <td>
                                  <small>{formatDate(transfer.approvalRequestedAt || transfer.transferDate)}</small>
                                </td>
                                <td>
                                  <span className={`badge ${transfer.approvalStatus === 'PENDING'
                                    ? 'bg-warning'
                                    : transfer.approvalStatus === 'APPROVED'
                                      ? 'bg-success'
                                      : transfer.approvalStatus === 'REJECTED'
                                        ? 'bg-danger'
                                        : 'bg-secondary'
                                    }`}>
                                    {transfer.approvalStatus === 'PENDING' && 'Onay Bekliyor'}
                                    {transfer.approvalStatus === 'APPROVED' && 'Onaylandı'}
                                    {transfer.approvalStatus === 'REJECTED' && 'Reddedildi'}
                                    {transfer.approvalStatus === 'NONE' && '—'}
                                  </span>
                                  {transfer.approvalDecisionBy && (
                                    <small className="d-block text-muted mt-1">
                                      {transfer.approvalDecisionBy}
                                    </small>
                                  )}
                                </td>
                                <td>
                                  {transfer.approvalStatus === 'PENDING' ? (
                                    <div className="d-flex gap-1 flex-wrap">
                                      <button
                                        className="btn btn-sm btn-success"
                                        onClick={() => handleApproveTransfer(transfer.id)}
                                        disabled={transferProcessing === transfer.id}
                                      >
                                        {transferProcessing === transfer.id ? (
                                          <span className="spinner-border spinner-border-sm"></span>
                                        ) : (
                                          <>
                                            <i className="fas fa-check me-1"></i>
                                            Onayla
                                          </>
                                        )}
                                      </button>
                                      <button
                                        className="btn btn-sm btn-danger"
                                        onClick={() => setRejectionModal({ show: true, id: transfer.id, reason: '', type: 'transfer' })}
                                        disabled={transferProcessing === transfer.id}
                                      >
                                        <i className="fas fa-times me-1"></i>
                                        Reddet
                                      </button>
                                      <button
                                        className="btn btn-sm btn-outline-primary"
                                        onClick={async () => {
                                          setRequestDetailModal({ show: true, payload: transfer, context: 'transfer' });
                                          try {
                                            const photos = {};
                                            if (Array.isArray(transfer.items)) {
                                              await Promise.all(
                                                transfer.items.map(async (item) => {
                                                  if (!item.id) return;
                                                  try {
                                                    const resp = await axios.get(`/api/stock-transfer-items/${item.id}/photo`);
                                                    photos[item.id] = resp.data;
                                                  } catch {
                                                    // ignore missing
                                                  }
                                                })
                                              );
                                            }
                                            setTransferDetailPhotos(photos);
                                          } catch (e) {
                                            console.error('Error loading transfer photos for approval detail', e);
                                            setTransferDetailPhotos({});
                                          }
                                        }}
                                      >
                                        <i className="fas fa-eye me-1"></i>
                                        Detay
                                      </button>
                                    </div>
                                  ) : (
                                    <div className="d-flex gap-1 flex-wrap">
                                      {transfer.approvalNote && (
                                        <button
                                          className="btn btn-sm btn-outline-info"
                                          onClick={() => setNotesModal({
                                            show: true,
                                            title: transfer.approvalStatus === 'REJECTED' ? 'Ret Notu' : 'Onay Notu',
                                            content: transfer.approvalNote,
                                            type: transfer.approvalStatus === 'REJECTED' ? 'danger' : 'info',
                                            icon: transfer.approvalStatus === 'REJECTED' ? 'fa-times-circle' : 'fa-sticky-note',
                                            transferId: transfer.id
                                          })}
                                        >
                                          <i className="fas fa-sticky-note"></i>
                                        </button>
                                      )}
                                      <button
                                        className="btn btn-sm btn-outline-primary"
                                        onClick={async () => {
                                          setRequestDetailModal({ show: true, payload: transfer, context: 'transfer' });
                                          try {
                                            const photos = {};
                                            if (Array.isArray(transfer.items)) {
                                              await Promise.all(
                                                transfer.items.map(async (item) => {
                                                  if (!item.id) return;
                                                  try {
                                                    const resp = await axios.get(`/api/stock-transfer-items/${item.id}/photo`);
                                                    photos[item.id] = resp.data;
                                                  } catch {
                                                    // ignore
                                                  }
                                                })
                                              );
                                            }
                                            setTransferDetailPhotos(photos);
                                          } catch (e) {
                                            console.error('Error loading transfer photos for approval detail', e);
                                            setTransferDetailPhotos({});
                                          }
                                        }}
                                      >
                                        <i className="fas fa-eye me-1"></i>
                                        Detay
                                      </button>
                                      {!transfer.approvalNote && (
                                        <span className="text-muted small">-</span>
                                      )}
                                    </div>
                                  )}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}
                  </div>
                  <div className="breakpoint-1155-mobile px-3 pb-3">
                    {transferLoading ? (
                      <div className="text-center py-4">
                        <div className="spinner-border text-info" role="status">
                          <span className="visually-hidden">Yükleniyor...</span>
                        </div>
                      </div>
                    ) : transferApprovals.length === 0 ? (
                      <div className="text-center py-4">
                        <i className="fas fa-inbox fa-3x text-muted mb-2"></i>
                        <p className="text-muted mb-0">Seçilen filtreye uygun transfer onayı yok.</p>
                      </div>
                    ) : (
                      <div className="my-requests-mobile-list mt-3">
                        {transferApprovals.map((transfer) => {
                          const routeLabel =
                            transfer.transferType === 'CUSTOMER_DELIVERY'
                              ? `${transfer.sourceWarehouse?.name || '-'} → ${transfer.customerFullName || 'Müşteri'}`
                              : `${transfer.sourceWarehouse?.name || '-'} → ${transfer.destinationWarehouse?.name || '-'}`;
                          const statusMeta = getStatusMeta(transfer.approvalStatus);
                          return (
                            <div key={transfer.id} className="my-requests-card card border-0">
                              <div className="card-body">
                                <div className="my-requests-card__header mb-2">
                                  <div>
                                    <div className="my-requests-card__title">Transfer #{transfer.id}</div>
                                    <small className="my-requests-card__meta">
                                      {transfer.transferType === 'CUSTOMER_DELIVERY' ? 'Müşteri Sevkiyatı' : 'Depo - Depo'}
                                    </small>
                                  </div>
                                  <span className={`my-requests-pill badge bg-${statusMeta.className}`}>
                                    <i className={`fas ${statusMeta.icon} me-1`}></i>
                                    {statusMeta.label}
                                  </span>
                                </div>
                                <div className="my-requests-card__details mb-3">
                                  <div className="my-requests-card__meta-row fw-semibold text-dark">
                                    <i className="fas fa-route text-primary"></i>
                                    {routeLabel}
                                  </div>
                                  <div className="my-requests-card__meta-row">
                                    <i className="fas fa-user"></i>
                                    {transfer.approvalRequestedBy || transfer.createdBy || '-'}
                                  </div>
                                  <div className="my-requests-card__meta-row">
                                    <i className="fas fa-calendar"></i>
                                    {formatDate(transfer.approvalRequestedAt || transfer.transferDate)}
                                  </div>
                                  {transfer.transferType === 'CUSTOMER_DELIVERY' && transfer.customerPhone && (
                                    <div className="my-requests-card__meta-row">
                                      <i className="fas fa-phone"></i>
                                      {transfer.customerPhone}
                                    </div>
                                  )}
                                </div>
                                {transfer.items && transfer.items.length > 0 && (
                                  <div className="mb-3">
                                    <div className="small text-muted mb-1">Ürünler</div>
                                    {transfer.items.slice(0, 2).map(item => (
                                      <div
                                        key={`${transfer.id}-${item.id}`}
                                        className="d-flex justify-content-between align-items-center small bg-light rounded-pill px-3 py-1 mb-1"
                                      >
                                        <span className="text-truncate me-2">{item.product?.name || 'Ürün'}</span>
                                        <span className="badge bg-primary d-inline-flex align-items-center justify-content-center" style={{ minWidth: '3.25rem' }}>
                                          {item.quantity}
                                        </span>
                                      </div>
                                    ))}
                                    {transfer.items.length > 2 && (
                                      <small className="text-muted">+ {transfer.items.length - 2} ürün daha</small>
                                    )}
                                  </div>
                                )}
                                <button
                                  className="btn btn-outline-primary btn-sm w-100 mb-2"
                                  onClick={async () => {
                                    setRequestDetailModal({ show: true, payload: transfer, context: 'transfer' });
                                    try {
                                      const photos = {};
                                      if (Array.isArray(transfer.items)) {
                                        await Promise.all(
                                          transfer.items.map(async (item) => {
                                            if (!item.id) return;
                                            try {
                                              const resp = await axios.get(`/api/stock-transfer-items/${item.id}/photo`);
                                              photos[item.id] = resp.data;
                                            } catch {
                                              // ignore missing
                                            }
                                          })
                                        );
                                      }
                                      setTransferDetailPhotos(photos);
                                    } catch (e) {
                                      console.error('Error loading transfer photos for mobile approval detail', e);
                                      setTransferDetailPhotos({});
                                    }
                                  }}
                                >
                                  <i className="fas fa-eye me-1"></i>
                                  Detay
                                </button>
                                <div className="my-requests-actions d-flex flex-wrap gap-2 justify-content-center">
                                  {matchesFilter(transfer.approvalStatus, 'PENDING') ? (
                                    <>
                                      <button
                                        className="btn btn-success btn-sm flex-fill"
                                        onClick={() => handleApproveTransfer(transfer.id)}
                                        disabled={transferProcessing === transfer.id}
                                      >
                                        {transferProcessing === transfer.id ? (
                                          <span className="spinner-border spinner-border-sm"></span>
                                        ) : (
                                          <>
                                            <i className="fas fa-check me-1"></i>
                                            Onayla
                                          </>
                                        )}
                                      </button>
                                      <button
                                        className="btn btn-outline-danger btn-sm flex-fill"
                                        onClick={() => setRejectionModal({ show: true, id: transfer.id, reason: '', type: 'transfer' })}
                                        disabled={transferProcessing === transfer.id}
                                      >
                                        <i className="fas fa-times me-1"></i>
                                        Reddet
                                      </button>
                                    </>
                                  ) : (
                                    <>
                                      {transfer.approvalNote && (
                                        <button
                                          className="btn btn-outline-info btn-sm flex-fill"
                                          onClick={() =>
                                            setNotesModal({
                                              show: true,
                                              title: transfer.approvalStatus === 'REJECTED' ? 'Reddetme Nedeni' : 'Onay Notu',
                                              content: transfer.approvalNote,
                                              type: transfer.approvalStatus === 'REJECTED' ? 'danger' : 'info'
                                            })
                                          }
                                        >
                                          <i className="fas fa-sticky-note me-1"></i>
                                          Notu Gör
                                        </button>
                                      )}
                                      <button
                                        className="btn btn-outline-secondary btn-sm flex-fill"
                                        onClick={() =>
                                          setNotesModal({
                                            show: true,
                                            title: 'Transfer Detayı',
                                            content: `Toplam miktar: ${transfer.totalQuantity || '-'}\nOluşturan: ${transfer.createdBy || '-'}`,
                                            type: 'info'
                                          })
                                        }
                                      >
                                        <i className="fas fa-info-circle me-1"></i>
                                        Detay
                                      </button>
                                    </>
                                  )}
                                </div>
                              </div>
                            </div>
                          );
                        })}
                      </div>
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

      {requestDetailModal.show && requestDetailModal.payload && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 1065 }}>
          <div className="modal-dialog modal-dialog-centered modal-dialog-scrollable">
            <div className="modal-content border-0 shadow-lg rounded-4">
              <div className="modal-header bg-primary text-white">
                <h5 className="modal-title">
                  <i className="fas fa-eye me-2"></i>
                  {requestDetailModal.context === 'transfer' ? 'Transfer Talebi Detayı' : 'Stok Talebi Detayı'}
                </h5>
                <button type="button" className="btn-close btn-close-white" onClick={() => setRequestDetailModal({ show: false, payload: null, context: 'stock' })}></button>
              </div>
              <div className="modal-body">
                {requestDetailModal.context === 'transfer' ? (
                  (() => {
                    const t = requestDetailModal.payload;
                    const items = Array.isArray(t.items) ? t.items : [];
                    const routeLabel =
                      t.transferType === 'CUSTOMER_DELIVERY'
                        ? `${t.sourceWarehouse?.name || '-'} → ${t.customerFullName || 'Müşteri'}`
                        : `${t.sourceWarehouse?.name || '-'} → ${t.destinationWarehouse?.name || '-'}`;
                    const statusMeta = getStatusMeta(t.approvalStatus || t.status);
                    return (
                      <>
                        <div className="mb-3">
                          <small className="text-muted text-uppercase d-block">Transfer</small>
                          <div className="fw-semibold">Transfer {t.id} • {routeLabel}</div>
                          <small className="text-muted">{formatDate(t.approvalRequestedAt || t.transferDate)}</small>
                        </div>
                        {t.transferType === 'CUSTOMER_DELIVERY' && t.customerAddress && (
                          <div className="mb-3">
                            <small className="text-muted text-uppercase d-block">Adres</small>
                            <div className="fw-semibold">
                              <i className="fas fa-map-marker-alt me-1 text-muted"></i>
                              {t.customerAddress}
                            </div>
                          </div>
                        )}
                        <div className="mb-3">
                          <small className="text-muted text-uppercase d-block">Durum</small>
                          <span className={`badge bg-${statusMeta.className}`}>
                            <i className={`fas ${statusMeta.icon} me-1`}></i>
                            {statusMeta.label}
                          </span>
                        </div>
                        <div className="mb-3">
                          <small className="text-muted text-uppercase d-block mb-1">Ürünler</small>
                          {items.length === 0 ? (
                            <span className="text-muted small">Ürün bilgisi bulunamadı</span>
                          ) : (
                            <ul className="list-group list-group-flush">
                              {items.map((item, idx) => {
                                const photoMeta = transferDetailPhotos[item.id];
                                const thumbUrl = photoMeta?.thumbnailUrl || photoMeta?.viewUrl;
                                const hasPhoto = !!thumbUrl;
                                return (
                                  <li
                                    key={`${t.id}-detail-${item.id || idx}`}
                                    className="list-group-item px-0 d-flex justify-content-between align-items-center"
                                  >
                                    <div className="d-flex align-items-center gap-2">
                                      <div className="flex-shrink-0">
                                        {hasPhoto ? (
                                          <div
                                            className="border rounded bg-white shadow-sm"
                                            style={{
                                              width: 40,
                                              height: 40,
                                              overflow: 'hidden',
                                              cursor: 'pointer'
                                            }}
                                            title="Fotoğrafı büyüt"
                                            onClick={() => openTransferLightbox(t, items, transferDetailPhotos, item.id)}
                                          >
                                            <img
                                              src={thumbUrl}
                                              alt="Ürün fotoğrafı"
                                              style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                                            />
                                          </div>
                                        ) : (
                                          <div
                                            className="border rounded bg-light d-flex align-items-center justify-content-center text-muted"
                                            style={{ width: 40, height: 40, fontSize: '0.65rem' }}
                                          >
                                            Yok
                                          </div>
                                        )}
                                      </div>
                                      <div>
                                        <div className="fw-semibold small">{item.product?.name || '-'}</div>
                                        <small className="text-muted">{item.product?.sku || '-'}</small>
                                      </div>
                                    </div>
                                    <span
                                      className="badge bg-primary d-inline-flex align-items-center justify-content-center"
                                      style={{ minWidth: '3.25rem' }}
                                    >
                                      {item.quantity}
                                    </span>
                                  </li>
                                );
                              })}
                            </ul>
                          )}
                        </div>
                        {t.approvalNote && (
                          <div className="alert alert-info small mb-0">
                            <i className="fas fa-sticky-note me-1"></i>
                            {t.approvalNote}
                          </div>
                        )}
                        {t.rejectionReason && (
                          <div className="alert alert-danger small mt-2 mb-0">
                            <i className="fas fa-exclamation-circle me-1"></i>
                            {t.rejectionReason}
                          </div>
                        )}
                      </>
                    );
                  })()
                ) : (
                  (() => {
                    const r = requestDetailModal.payload;
                    const statusMeta = getStatusMeta(r.status);
                    return (
                      <>
                        <div className="mb-3">
                          <small className="text-muted text-uppercase d-block">Ürün</small>
                          <div className="fw-semibold">{r.stock?.product?.name || r.productName || '-'}</div>
                          <small className="text-muted">{r.stock?.product?.sku || r.productSku || '-'}</small>
                        </div>
                        <div className="mb-3">
                          <small className="text-muted text-uppercase d-block">Depo</small>
                          <div className="fw-semibold">{r.stock?.warehouse?.name || r.warehouseName || '-'}</div>
                        </div>
                        <div className="row g-2 mb-3">
                          <div className="col-6">
                            <small className="text-muted text-uppercase d-block">Miktar</small>
                            <div className="fw-semibold">{r.quantity}</div>
                          </div>
                          <div className="col-6">
                            <small className="text-muted text-uppercase d-block">Durum</small>
                            <span className={`badge bg-${statusMeta.className}`}>
                              <i className={`fas ${statusMeta.icon} me-1`}></i>
                              {statusMeta.label}
                            </span>
                          </div>
                        </div>
                        <div className="mb-3">
                          <small className="text-muted text-uppercase d-block">Talep Tarihi</small>
                          <div className="fw-semibold">{formatDate(r.requestedAt || r.createdAt)}</div>
                          {r.reviewedAt && (
                            <small className="text-muted">Güncelleme: {formatDate(r.reviewedAt)}</small>
                          )}
                        </div>
                        {r.notes && (
                          <div className="alert alert-info small mb-0">
                            <i className="fas fa-sticky-note me-1"></i>
                            {r.notes}
                          </div>
                        )}
                        {r.rejectionReason && (
                          <div className="alert alert-danger small mt-2 mb-0">
                            <i className="fas fa-times-circle me-1"></i>
                            {r.rejectionReason}
                          </div>
                        )}
                      </>
                    );
                  })()
                )}
              </div>
              <div className="modal-footer bg-light">
                <button type="button" className="btn btn-secondary" onClick={() => setRequestDetailModal({ show: false, payload: null, context: 'stock' })}>
                  <i className="fas fa-times me-2"></i>
                  Kapat
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {transferLightbox.show && transferLightbox.images.length > 0 && (
        <div
          className="position-fixed top-0 start-0 w-100 h-100"
          style={{ backgroundColor: 'rgba(0,0,0,0.85)', zIndex: 3000 }}
          onClick={() => setTransferLightbox({ show: false, images: [], index: 0 })}
        >
          <div
            className="d-flex flex-column justify-content-center align-items-center h-100"
            onClick={(e) => e.stopPropagation()}
          >
            <button
              type="button"
              className="btn btn-sm btn-light position-absolute top-0 end-0 m-3"
              onClick={() => setTransferLightbox({ show: false, images: [], index: 0 })}
            >
              <i className="fas fa-times me-1"></i>
              Kapat
            </button>
            <div className="text-white mb-2 small">
              {transferLightbox.images[transferLightbox.index].title}
            </div>
            <div className="d-flex align-items-center justify-content-center w-100 px-3">
              <button
                type="button"
                className="btn btn-outline-light me-3 d-none d-sm-inline-flex"
                onClick={() =>
                  setTransferLightbox((prev) => ({
                    ...prev,
                    index:
                      (prev.index - 1 + prev.images.length) % prev.images.length
                  }))
                }
              >
                <i className="fas fa-chevron-left"></i>
              </button>
              <div
                className="bg-black rounded-3 shadow-lg d-flex justify-content-center align-items-center"
                style={{
                  maxWidth: '90vw',
                  maxHeight: '80vh',
                  overflow: 'hidden'
                }}
              >
                <img
                  src={transferLightbox.images[transferLightbox.index].src}
                  alt={transferLightbox.images[transferLightbox.index].title}
                  style={{
                    maxWidth: '100%',
                    maxHeight: '80vh',
                    objectFit: 'contain'
                  }}
                />
              </div>
              <button
                type="button"
                className="btn btn-outline-light ms-3 d-none d-sm-inline-flex"
                onClick={() =>
                  setTransferLightbox((prev) => ({
                    ...prev,
                    index: (prev.index + 1) % prev.images.length
                  }))
                }
              >
                <i className="fas fa-chevron-right"></i>
              </button>
            </div>
            {transferLightbox.images.length > 1 && (
              <div className="mt-2 text-white-50 small">
                {transferLightbox.index + 1} / {transferLightbox.images.length}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Rejection Reason Modal */}
      {rejectionModal.show && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.7)', zIndex: 1060 }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header bg-danger text-white">
                <h5 className="modal-title">
                  <i className="fas fa-ban me-2"></i>
                  {rejectionModal.type === 'transfer' ? 'Transferi Reddet' : 'Talebi Reddet'}
                </h5>
                <button
                  type="button"
                  className="btn-close btn-close-white"
                  onClick={() => setRejectionModal({ show: false, id: null, reason: '', type: 'stock' })}
                ></button>
              </div>
              <div className="modal-body">
                <div className="alert alert-warning">
                  <i className="fas fa-exclamation-triangle me-2"></i>
                  {rejectionModal.type === 'transfer'
                    ? 'Bu transfer onayı reddedilecek. Lütfen bir neden belirtin.'
                    : 'Bu talep reddedilecek. Lütfen bir neden belirtin.'}
                </div>
                <div className="mb-3">
                  <label htmlFor="rejectionReason" className="form-label">
                    <i className="fas fa-comment-alt me-1"></i>
                    Reddetme Nedeni
                  </label>
                  <textarea
                    id="rejectionReason"
                    className="form-control"
                    rows="4"
                    value={rejectionModal.reason}
                    onChange={(e) => setRejectionModal({ ...rejectionModal, reason: e.target.value })}
                    placeholder="Reddetme nedenini buraya yazın..."
                    maxLength="500"
                  />
                  <small className="text-muted">
                    {rejectionModal.reason.length}/500 karakter
                  </small>
                </div>
              </div>
              <div className="modal-footer">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setRejectionModal({ show: false, id: null, reason: '', type: 'stock' })}
                >
                  <i className="fas fa-times me-2"></i>
                  Vazgeç
                </button>
                <button
                  type="button"
                  className="btn btn-danger"
                  onClick={handleReject}
                  disabled={!rejectionModal.reason.trim() || processing}
                >
                  {processing ? (
                    <span className="spinner-border spinner-border-sm me-2"></span>
                  ) : (
                    <i className="fas fa-ban me-2"></i>
                  )}
                  Reddet
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Notes/Rejection Reason Display Modal */}
      {notesModal.show && (
        <div
          className="modal show d-block"
          tabIndex="-1"
          style={{ backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 1065 }}
          onClick={() => setNotesModal({ show: false, title: '', content: '', type: 'info' })}
        >
          <div
            className="modal-dialog modal-dialog-centered modal-dialog-scrollable"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-content shadow-lg border-0" style={{ overflow: 'hidden' }}>
              <div
                className={`modal-header text-white border-0`}
                style={{
                  background: notesModal.type === 'danger'
                    ? 'linear-gradient(135deg, #dc3545 0%, #c82333 100%)'
                    : 'linear-gradient(135deg, #0dcaf0 0%, #0891b2 100%)'
                }}
              >
                <h5 className="modal-title d-flex align-items-center">
                  <div
                    className="rounded-circle d-flex align-items-center justify-content-center me-3"
                    style={{
                      width: '40px',
                      height: '40px',
                      background: 'rgba(255,255,255,0.2)',
                      backdropFilter: 'blur(10px)'
                    }}
                  >
                    <i className={`fas ${notesModal.icon}`}></i>
                  </div>
                  <div>
                    <div>{notesModal.title}</div>
                    {notesModal.productName && (
                      <small className="opacity-75 d-block" style={{ fontSize: '0.85rem' }}>
                        {notesModal.productName} - {notesModal.requestType === 'ADD' ? 'Ekleme' : 'Çıkarma'}
                      </small>
                    )}
                  </div>
                </h5>
                <button
                  type="button"
                  className="btn-close btn-close-white"
                  onClick={() => setNotesModal({ show: false, title: '', content: '', type: 'info' })}
                ></button>
              </div>
              <div className="modal-body p-4">
                <div
                  className={`alert border-0 mb-0`}
                  style={{
                    background: notesModal.type === 'danger'
                      ? 'linear-gradient(135deg, #fee2e2 0%, #fecaca 100%)'
                      : 'linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%)',
                    borderLeft: `4px solid ${notesModal.type === 'danger' ? '#dc3545' : '#0dcaf0'}`
                  }}
                >
                  <div className="d-flex">
                    <div
                      className="flex-shrink-0 me-3"
                      style={{ fontSize: '1.5rem', opacity: 0.7 }}
                    >
                      <i className={`fas ${notesModal.icon}`}></i>
                    </div>
                    <div className="flex-grow-1">
                      <p className="mb-0" style={{
                        whiteSpace: 'pre-wrap',
                        wordBreak: 'break-word',
                        lineHeight: '1.6',
                        color: '#1f2937'
                      }}>
                        {notesModal.content}
                      </p>
                    </div>
                  </div>
                </div>
              </div>
              <div className="modal-footer border-0 bg-light">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setNotesModal({ show: false, title: '', content: '', type: 'info' })}
                >
                  <i className="fas fa-times me-2"></i>
                  Kapat
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default StockRequestApprovalModal;

