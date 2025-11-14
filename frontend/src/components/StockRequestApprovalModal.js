import React, { useState, useEffect } from 'react';
import axios from 'axios';

const StockRequestApprovalModal = ({ onClose, onApprove }) => {
  const [allRequests, setAllRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [processing, setProcessing] = useState(null);
  const [rejectionModal, setRejectionModal] = useState({ show: false, requestId: null, reason: '' });
  const [notesModal, setNotesModal] = useState({ show: false, title: '', content: '', type: 'info' });
  const [filter, setFilter] = useState('PENDING');

  useEffect(() => {
    fetchRequests();
  }, []);

  const fetchRequests = async () => {
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

  const handleApprove = async (requestId) => {
    try {
      setProcessing(requestId);
      await axios.post(`/api/stock-requests/${requestId}/approve`);
      await fetchRequests();
      if (onApprove) onApprove();
    } catch (error) {
      alert(error.response?.data?.message || 'Onaylama hatası');
    } finally {
      setProcessing(null);
    }
  };

  const handleReject = async () => {
    try {
      setProcessing(rejectionModal.requestId);
      await axios.post(`/api/stock-requests/${rejectionModal.requestId}/reject`, {
        rejectionReason: rejectionModal.reason
      });
      setRejectionModal({ show: false, requestId: null, reason: '' });
      await fetchRequests();
      if (onApprove) onApprove();
    } catch (error) {
      alert(error.response?.data?.message || 'Reddetme hatası');
    } finally {
      setProcessing(null);
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

  // Calculate counts from all requests
  const pendingCount = allRequests.filter(r => r.status === 'PENDING').length;
  const approvedCount = allRequests.filter(r => r.status === 'APPROVED').length;
  const rejectedCount = allRequests.filter(r => r.status === 'REJECTED').length;

  // Filter requests based on selected filter
  const requests = filter === 'ALL' 
    ? allRequests 
    : allRequests.filter(r => r.status === filter);

  return (
    <>
      <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 1050 }}>
        <div className="modal-dialog modal-xl modal-dialog-scrollable">
          <div className="modal-content" style={{ maxHeight: '90vh' }}>
            <div className="modal-header bg-primary text-white">
              <h5 className="modal-title">
                <i className="fas fa-tasks me-2"></i>
                Stok Talep Onay Sistemi
              </h5>
              <button type="button" className="btn-close btn-close-white" onClick={onClose}></button>
            </div>

            <div className="modal-body p-0">
              {/* Statistics Cards */}
              <div className="row g-3 p-3 bg-light">
                <div className="col-md-4">
                  <div className="card border-warning shadow-sm h-100">
                    <div className="card-body text-center">
                      <i className="fas fa-clock fa-2x text-warning mb-2"></i>
                      <h3 className="mb-0">{pendingCount}</h3>
                      <p className="text-muted mb-0 small">Bekleyen Talepler</p>
                    </div>
                  </div>
                </div>
                <div className="col-md-4">
                  <div className="card border-success shadow-sm h-100">
                    <div className="card-body text-center">
                      <i className="fas fa-check-circle fa-2x text-success mb-2"></i>
                      <h3 className="mb-0">{approvedCount}</h3>
                      <p className="text-muted mb-0 small">Onaylanan</p>
                    </div>
                  </div>
                </div>
                <div className="col-md-4">
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
                <div className="btn-group w-100" role="group">
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

              {/* Requests Table */}
              <div className="table-responsive">
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
                  <table className="table table-hover align-middle mb-0">
                    <thead className="table-light sticky-top" style={{ top: 0 }}>
                      <tr>
                        <th className="text-center" style={{ width: '60px' }}>ID</th>
                        <th style={{ minWidth: '150px' }}>Ürün</th>
                        <th style={{ minWidth: '120px' }}>Depo</th>
                        <th className="text-center" style={{ width: '100px' }}>İşlem</th>
                        <th className="text-center" style={{ width: '80px' }}>Miktar</th>
                        <th style={{ minWidth: '100px' }}>Mevcut Stok</th>
                        <th style={{ minWidth: '120px' }}>Talep Eden</th>
                        <th style={{ minWidth: '130px' }}>Talep Tarihi</th>
                        <th className="text-center" style={{ width: '100px' }}>Durum</th>
                        <th className="text-center" style={{ minWidth: '180px' }}>İşlemler</th>
                      </tr>
                    </thead>
                    <tbody>
                      {requests.map((request) => (
                        <tr key={request.id} className={request.status === 'PENDING' ? 'table-warning' : ''}>
                          <td className="text-center">
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
                                  onClick={() => setRejectionModal({ show: true, requestId: request.id, reason: '' })}
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
                )}
              </div>
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

      {/* Rejection Reason Modal */}
      {rejectionModal.show && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.7)', zIndex: 1060 }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header bg-danger text-white">
                <h5 className="modal-title">
                  <i className="fas fa-ban me-2"></i>
                  Talebi Reddet
                </h5>
                <button
                  type="button"
                  className="btn-close btn-close-white"
                  onClick={() => setRejectionModal({ show: false, requestId: null, reason: '' })}
                ></button>
              </div>
              <div className="modal-body">
                <div className="alert alert-warning">
                  <i className="fas fa-exclamation-triangle me-2"></i>
                  Bu talep reddedilecek. Lütfen bir neden belirtin.
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
                  onClick={() => setRejectionModal({ show: false, requestId: null, reason: '' })}
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

