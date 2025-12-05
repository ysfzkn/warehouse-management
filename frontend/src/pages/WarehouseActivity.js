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
                  {logs.map(log => (
                    <div key={log.id} className="list-group-item">
                      <div className="d-flex justify-content-between flex-wrap gap-2">
                        <div>
                          <span className={`badge bg-${actionMeta(log.action).variant}`}>
                            {actionMeta(log.action).label}
                          </span>
                          <div className="text-muted small mt-2">
                            <span style={dateBadgeStyle}>
                              <i className="far fa-clock"></i>
                              <span>{formatDateTime(log.createdAt)}</span>
                            </span>
                          </div>
                        </div>
                        <div className="d-flex flex-wrap align-items-center gap-2">
                          {log.username && <span className="badge bg-primary">{log.username}</span>}
                          {log.productSku && <span className="badge bg-secondary">SKU: {log.productSku}</span>}
                          {log.productName && (
                            <span className="badge bg-light text-dark">
                              <i className="fas fa-box me-1"></i>{log.productName}
                            </span>
                          )}
                          {typeof log.quantity === 'number' && (
                            <span className="badge bg-info text-dark">Adet: {log.quantity}</span>
                          )}
                          {log.sourceWarehouseName && (
                            <span className="badge bg-light text-dark">
                              <i className="fas fa-arrow-right me-1"></i>{log.sourceWarehouseName}
                            </span>
                          )}
                          {log.destinationWarehouseName && (
                            <span className="badge bg-light text-dark">
                              <i className="fas fa-arrow-right me-1"></i>{log.destinationWarehouseName}
                            </span>
                          )}
                        </div>
                      </div>
                      <div className="mt-2" style={{ whiteSpace: 'pre-wrap' }}>{log.details}</div>
                    </div>
                  ))}
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

