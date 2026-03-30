import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import axios from 'axios';
import PaginationControls from '../components/PaginationControls';

const formatDateTime = (value) => {
  if (!value) return '-';
  try {
    return new Date(value).toLocaleString('tr-TR', { timeZone: 'Europe/Istanbul' });
  } catch {
    return value;
  }
};

const dateBadgeStyle = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: '0.4rem',
  padding: '0.35rem 0.75rem',
  borderRadius: '999px',
  background: 'rgba(0,0,0,0.05)'
};

const actionMeta = (action) => {
  const map = {
    STOCK_CREATE: { label: 'Stok Oluşturma', variant: 'primary' },
    STOCK_UPDATE: { label: 'Stok Güncelleme', variant: 'info' },
    STOCK_ADD: { label: 'Stok Artırma', variant: 'success' },
    STOCK_REMOVE: { label: 'Stok Azaltma', variant: 'danger' },
    STOCK_DELETE: { label: 'Stok Silme', variant: 'dark' },
    STOCK_RESERVE: { label: 'Rezervasyon', variant: 'warning text-dark' },
    STOCK_RELEASE: { label: 'Rezervasyon Çözümü', variant: 'secondary' },
    TRANSFER_CREATE: { label: 'Transfer Oluşturma', variant: 'primary' },
    TRANSFER_START: { label: 'Transfer Yola Çıkarma', variant: 'info text-dark' },
    TRANSFER_COMPLETE: { label: 'Transfer Tamamlama', variant: 'success' },
    TRANSFER_CANCEL: { label: 'Transfer İptali', variant: 'danger' },
    TRANSFER_UPDATE: { label: 'Transfer Güncelleme', variant: 'secondary' },
    TRANSFER_DELETE: { label: 'Transfer Silme', variant: 'dark' },
    TRANSFER_APPROVE: { label: 'Transfer Onayı', variant: 'success' },
    TRANSFER_REJECT: { label: 'Transfer Reddi', variant: 'danger' }
  };
  return map[action] || { label: action || '-', variant: 'secondary' };
};

const extractNoteFromDetails = (details) => {
  if (!details || typeof details !== 'string') return '';
  const match = details.match(/\|\s*Not:\s*(.+)$/i);
  return match ? match[1].trim() : '';
};

const WarehouseActivity = () => {
  const { warehouseId } = useParams();
  const navigate = useNavigate();
  const [warehouse, setWarehouse] = useState(null);
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [totalElements, setTotalElements] = useState(0);
  const [filters, setFilters] = useState({ search: '', start: '', end: '' });

  useEffect(() => {
    const fetchWarehouse = async () => {
      try {
        const res = await axios.get(`/api/warehouses/${warehouseId}`);
        setWarehouse(res.data);
      } catch {
        setWarehouse(null);
      }
    };
    fetchWarehouse();
  }, [warehouseId]);

  const fetchLogs = async () => {
    setLoading(true);
    setError('');
    try {
      const params = { page, size, search: filters.search || undefined };
      if (filters.start) params.startDate = new Date(filters.start).toISOString();
      if (filters.end) params.endDate = new Date(filters.end).toISOString();
      const res = await axios.get(`/api/audit/warehouse/${warehouseId}`, { params });
      const data = res.data;
      const list = data?.content || (Array.isArray(data) ? data : []);
      setLogs(list);
      setTotalElements(data?.totalElements ?? list.length);
    } catch (e) {
      setError('Depo hareketleri yüklenirken hata oluştu');
      setLogs([]);
      setTotalElements(0);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLogs();
  }, [page, size, filters, warehouseId]);

  const totalPages = useMemo(() => {
    if (size <= 0) return 0;
    return Math.max(1, Math.ceil((totalElements || 0) / size));
  }, [size, totalElements]);

  return (
    <div className="container py-3">
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-3 mb-3">
        <div>
          <h3 className="mb-0">
            Depo Stok Hareketleri İzleme
            {warehouse && <span className="text-muted small ms-2">{warehouse.name}</span>}
          </h3>
          <div className="text-muted small">Stok giriş-çıkış ve transfer adımlarını detaylı inceleyin</div>
        </div>
        <div className="d-flex align-items-center gap-2">
          <button className="btn btn-outline-secondary btn-sm" onClick={() => navigate(-1)}>
            <i className="fas fa-arrow-left me-1"></i>
            Geri
          </button>
          <select
            className="form-select form-select-sm"
            style={{ width: '120px' }}
            value={size}
            onChange={e => { setSize(Number(e.target.value)); setPage(0); }}
          >
            {[10, 25, 50, 100].map(opt => (
              <option key={opt} value={opt}>{opt}/sayfa</option>
            ))}
          </select>
        </div>
      </div>

      <div className="card mb-3">
        <div className="card-body">
          <div className="row g-3">
            <div className="col-md-4">
              <label className="form-label fw-semibold">Arama</label>
              <input
                type="text"
                className="form-control"
                placeholder="Stok adı/kodu, kullanıcı, detay..."
                value={filters.search}
                onChange={e => { setFilters(prev => ({ ...prev, search: e.target.value })); setPage(0); }}
              />
            </div>
            <div className="col-md-3">
              <label className="form-label fw-semibold">Başlangıç</label>
              <input
                type="datetime-local"
                className="form-control"
                value={filters.start}
                onChange={e => { setFilters(prev => ({ ...prev, start: e.target.value })); setPage(0); }}
              />
            </div>
            <div className="col-md-3">
              <label className="form-label fw-semibold">Bitiş</label>
              <input
                type="datetime-local"
                className="form-control"
                value={filters.end}
                onChange={e => { setFilters(prev => ({ ...prev, end: e.target.value })); setPage(0); }}
              />
            </div>
            <div className="col-md-2 d-flex align-items-end">
              <button className="btn btn-primary w-100" onClick={() => { setPage(0); fetchLogs(); }}>
                <i className="fas fa-search me-2"></i>Filtrele
              </button>
            </div>
          </div>
        </div>
      </div>

      {error && <div className="alert alert-danger">{error}</div>}

      <div className="card">
        <div className="card-body">
          {loading ? (
            <div className="text-center py-4">
              <div className="spinner-border" role="status"></div>
            </div>
          ) : (
            <>
              {logs.length === 0 ? (
                <div className="text-muted">Kayıt bulunamadı.</div>
              ) : (
                <div className="list-group list-group-flush">
                  {logs.map(log => {
                    const movementNote = (log.note && String(log.note).trim()) || extractNoteFromDetails(log.details);
                    return (
                    <div key={log.id} className="list-group-item">
                      {/* Header with action and timestamp */}
                      <div className="d-flex justify-content-between align-items-start flex-wrap gap-2 mb-3">
                        <div>
                          <span className={`badge bg-${actionMeta(log.action).variant} fs-6`}>
                            {actionMeta(log.action).label}
                          </span>
                          <div className="text-muted small mt-2">
                            <span style={dateBadgeStyle}>
                              <i className="far fa-clock"></i>
                              <span>{formatDateTime(log.createdAt)}</span>
                            </span>
                          </div>
                        </div>
                        {log.username && (
                          <div className="d-flex align-items-center gap-2">
                            <div style={{
                              width: '32px',
                              height: '32px',
                              borderRadius: '8px',
                              backgroundColor: '#e0e7ff',
                              color: '#4f46e5',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              fontSize: '0.75rem',
                              fontWeight: '600'
                            }}>
                              {log.username.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2)}
                            </div>
                            <span className="fw-semibold">{log.username}</span>
                          </div>
                        )}
                      </div>

                      {/* Product Details Card */}
                      <div className="row g-2 mb-2">
                        {/* Product SKU and Name */}
                        {(log.productSku || log.productName) && (
                          <div className="col-12 col-lg-6">
                            <div style={{
                              backgroundColor: '#f8fafc',
                              borderRadius: '8px',
                              border: '1px solid #e2e8f0',
                              padding: '0.75rem',
                              height: '100%'
                            }}>
                              <div className="d-flex align-items-center gap-2">
                                <div style={{
                                  width: '36px',
                                  height: '36px',
                                  borderRadius: '8px',
                                  backgroundColor: '#dbeafe',
                                  color: '#1d4ed8',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  fontSize: '0.9rem',
                                  flexShrink: 0
                                }}>
                                  <i className="fas fa-box"></i>
                                </div>
                                <div style={{ flex: 1, minWidth: 0 }}>
                                  {log.productSku && (
                                    <div style={{
                                      fontSize: '0.75rem',
                                      color: '#1e293b',
                                      fontWeight: '700',
                                      marginBottom: '0.25rem',
                                      fontFamily: 'monospace',
                                      letterSpacing: '0.5px'
                                    }}>
                                      <i className="fas fa-barcode me-1"></i>
                                      {log.productSku}
                                    </div>
                                  )}
                                  {log.productName && (
                                    <div style={{
                                      fontSize: '0.9rem',
                                      fontWeight: '600',
                                      color: '#475569',
                                      overflow: 'hidden',
                                      whiteSpace: 'normal',
                                      wordBreak: 'break-word'
                                    }}>
                                      {log.productName}
                                    </div>
                                  )}
                                </div>
                              </div>
                            </div>
                          </div>
                        )}

                        {/* Quantity */}
                        {typeof log.quantity === 'number' && (
                          <div className="col-6 col-lg-3">
                            <div style={{
                              backgroundColor: '#f8fafc',
                              borderRadius: '8px',
                              border: '1px solid #e2e8f0',
                              padding: '0.75rem',
                              height: '100%'
                            }}>
                              <div className="d-flex align-items-center gap-2">
                                <div style={{
                                  width: '36px',
                                  height: '36px',
                                  borderRadius: '8px',
                                  backgroundColor: '#dcfce7',
                                  color: '#15803d',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  fontSize: '0.9rem',
                                  flexShrink: 0
                                }}>
                                  <i className="fas fa-cubes"></i>
                                </div>
                                <div>
                                  <div style={{
                                    fontSize: '0.7rem',
                                    color: '#64748b',
                                    marginBottom: '0.125rem'
                                  }}>Miktar</div>
                                  <div style={{
                                    fontSize: '0.9rem',
                                    fontWeight: '700',
                                    color: '#1e293b'
                                  }}>
                                    {log.quantity} adet
                                  </div>
                                </div>
                              </div>
                            </div>
                          </div>
                        )}

                        {/* Customer Info (for EMANET_DEPO) */}
                        {(log.customerName || log.customerPhone) && (
                          <div className="col-12 col-lg-6">
                            <div style={{
                              backgroundColor: '#fffbeb',
                              borderRadius: '8px',
                              border: '2px solid #fbbf24',
                              padding: '0.75rem',
                              height: '100%',
                              boxShadow: '0 2px 4px rgba(251, 191, 36, 0.1)'
                            }}>
                              <div className="d-flex align-items-center gap-2">
                                <div style={{
                                  width: '36px',
                                  height: '36px',
                                  borderRadius: '8px',
                                  backgroundColor: '#fbbf24',
                                  color: '#78350f',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  fontSize: '0.9rem',
                                  flexShrink: 0
                                }}>
                                  <i className="fas fa-user-tag"></i>
                                </div>
                                <div style={{ flex: 1, minWidth: 0 }}>
                                  <div style={{
                                    fontSize: '0.7rem',
                                    color: '#92400e',
                                    marginBottom: '0.25rem',
                                    fontWeight: '600'
                                  }}>
                                    <i className="fas fa-handshake me-1"></i>
                                    Emanet Müşteri
                                  </div>
                                  <div style={{
                                    fontSize: '0.9rem',
                                    fontWeight: '700',
                                    color: '#78350f',
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    whiteSpace: 'nowrap',
                                    marginBottom: log.customerPhone ? '0.25rem' : '0'
                                  }}>
                                    {log.customerName || 'Bilinmiyor'}
                                  </div>
                                  {log.customerPhone && (
                                    <div style={{
                                      fontSize: '0.8rem',
                                      color: '#92400e',
                                      display: 'flex',
                                      alignItems: 'center',
                                      gap: '0.25rem'
                                    }}>
                                      <i className="fas fa-phone" style={{ fontSize: '0.7rem' }}></i>
                                      {log.customerPhone}
                                    </div>
                                  )}
                                </div>
                              </div>
                            </div>
                          </div>
                        )}

                        {/* Transfer Info */}
                        {log.transferId && (
                          <div className="col-12 col-lg-6">
                            <div style={{
                              backgroundColor: '#eff6ff',
                              borderRadius: '8px',
                              border: '2px solid #0ea5e9',
                              padding: '0.75rem',
                              height: '100%',
                              boxShadow: '0 2px 4px rgba(14, 165, 233, 0.1)'
                            }}>
                              <div className="d-flex align-items-center gap-2">
                                <div style={{
                                  width: '36px',
                                  height: '36px',
                                  borderRadius: '8px',
                                  backgroundColor: '#0ea5e9',
                                  color: 'white',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  fontSize: '0.9rem',
                                  flexShrink: 0
                                }}>
                                  <i className="fas fa-truck"></i>
                                </div>
                                <div style={{ flex: 1, minWidth: 0 }}>
                                  <div style={{
                                    fontSize: '0.7rem',
                                    color: '#0369a1',
                                    marginBottom: '0.25rem',
                                    fontWeight: '600'
                                  }}>
                                    Transfer İşlemi
                                  </div>
                                  <button
                                    onClick={(e) => {
                                      e.preventDefault();
                                      navigate(`/stock?highlightTransfer=${log.transferId}`);
                                    }}
                                    style={{
                                      backgroundColor: '#dbeafe',
                                      color: '#0c4a6e',
                                      border: '2px solid #0ea5e9',
                                      padding: '0.375rem 0.75rem',
                                      borderRadius: '8px',
                                      fontFamily: 'monospace',
                                      letterSpacing: '0.5px',
                                      fontWeight: '700',
                                      fontSize: '0.9rem',
                                      cursor: 'pointer',
                                      display: 'inline-flex',
                                      alignItems: 'center',
                                      gap: '0.5rem',
                                      transition: 'all 0.2s ease',
                                      boxShadow: '0 2px 4px rgba(14, 165, 233, 0.2)'
                                    }}
                                    onMouseEnter={(e) => {
                                      e.currentTarget.style.backgroundColor = '#bfdbfe';
                                      e.currentTarget.style.borderColor = '#0284c7';
                                      e.currentTarget.style.transform = 'translateY(-1px)';
                                      e.currentTarget.style.boxShadow = '0 4px 8px rgba(14, 165, 233, 0.3)';
                                    }}
                                    onMouseLeave={(e) => {
                                      e.currentTarget.style.backgroundColor = '#dbeafe';
                                      e.currentTarget.style.borderColor = '#0ea5e9';
                                      e.currentTarget.style.transform = 'translateY(0)';
                                      e.currentTarget.style.boxShadow = '0 2px 4px rgba(14, 165, 233, 0.2)';
                                    }}
                                    title="Transferi görüntüle"
                                  >
                                    <i className="fas fa-external-link-alt" style={{ fontSize: '0.75rem' }}></i>
                                    Transferi Görüntüle
                                  </button>
                                </div>
                              </div>
                            </div>
                          </div>
                        )}

                        {/* Warehouse Transfer Info */}
                        {(log.sourceWarehouseName || log.destinationWarehouseName) && (
                          <div className="col-12 col-lg-6">
                            <div style={{
                              backgroundColor: '#f8fafc',
                              borderRadius: '8px',
                              border: '1px solid #e2e8f0',
                              padding: '0.75rem',
                              height: '100%'
                            }}>
                              <div className="d-flex align-items-center gap-2">
                                <div style={{
                                  width: '36px',
                                  height: '36px',
                                  borderRadius: '8px',
                                  backgroundColor: '#fef3c7',
                                  color: '#b45309',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  fontSize: '0.9rem',
                                  flexShrink: 0
                                }}>
                                  <i className="fas fa-exchange-alt"></i>
                                </div>
                                <div style={{ flex: 1, minWidth: 0 }}>
                                  <div style={{
                                    fontSize: '0.7rem',
                                    color: '#64748b',
                                    marginBottom: '0.25rem'
                                  }}>Transfer</div>
                                  <div className="d-flex align-items-center gap-2" style={{ fontSize: '0.85rem', fontWeight: '600' }}>
                                    {log.sourceWarehouseName && (
                                      <span style={{
                                        color: '#ef4444',
                                        overflow: 'hidden',
                                        textOverflow: 'ellipsis',
                                        whiteSpace: 'nowrap'
                                      }}>
                                        {log.sourceWarehouseName}
                                      </span>
                                    )}
                                    {log.sourceWarehouseName && log.destinationWarehouseName && (
                                      <i className="fas fa-long-arrow-alt-right" style={{ color: '#667eea', flexShrink: 0 }}></i>
                                    )}
                                    {log.destinationWarehouseName && (
                                      <span style={{
                                        color: '#22c55e',
                                        overflow: 'hidden',
                                        textOverflow: 'ellipsis',
                                        whiteSpace: 'nowrap'
                                      }}>
                                        {log.destinationWarehouseName}
                                      </span>
                                    )}
                                  </div>
                                </div>
                              </div>
                            </div>
                          </div>
                        )}
                      </div>

                      {/* User Note */}
                      {movementNote && (
                        <div style={{
                          marginTop: '0.75rem',
                          padding: '0.75rem 1rem',
                          backgroundColor: '#fefce8',
                          borderRadius: '8px',
                          border: '1px solid #fde68a',
                          borderLeft: '4px solid #eab308',
                          display: 'flex',
                          alignItems: 'flex-start',
                          gap: '0.6rem'
                        }}>
                          <div style={{
                            width: '28px',
                            height: '28px',
                            borderRadius: '6px',
                            backgroundColor: '#fde68a',
                            color: '#92400e',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            fontSize: '0.75rem',
                            flexShrink: 0,
                            marginTop: '1px'
                          }}>
                            <i className="fas fa-sticky-note"></i>
                          </div>
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <div style={{
                              fontSize: '0.7rem',
                              fontWeight: '600',
                              color: '#92400e',
                              marginBottom: '0.25rem',
                              textTransform: 'uppercase',
                              letterSpacing: '0.5px'
                            }}>
                              Kullanıcı Notu
                            </div>
                            <div style={{
                              fontSize: '0.85rem',
                              color: '#78350f',
                              fontWeight: '500',
                              whiteSpace: 'pre-wrap',
                              wordBreak: 'break-word'
                            }}>
                              {movementNote}
                            </div>
                          </div>
                        </div>
                      )}

                      {/* Details */}
                      {log.details && (
                        <div style={{
                          marginTop: '0.75rem',
                          padding: '0.75rem',
                          backgroundColor: 'white',
                          borderRadius: '8px',
                          border: '1px solid #e2e8f0',
                          fontSize: '0.85rem',
                          color: '#475569',
                          whiteSpace: 'pre-wrap'
                        }}>
                          {log.details}
                        </div>
                      )}
                    </div>
                  );
                  })}
                </div>
              )}
              <div className="d-flex justify-content-between align-items-center mt-3 flex-wrap gap-2">
                <div className="text-muted small">
                  Toplam {totalElements} kayıt • Sayfa {page + 1} / {totalPages}
                </div>
                <PaginationControls
                  page={page}
                  totalPages={totalPages}
                  onPageChange={setPage}
                />
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default WarehouseActivity;

