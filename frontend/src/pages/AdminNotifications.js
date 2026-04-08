import React, { useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import SearchableSelect from '../components/SearchableSelect';
import PaginationControls from '../components/PaginationControls';

const formatDateTime = (value) => {
  if (!value) return '-';
  try {
    return new Date(value).toLocaleString('tr-TR', { timeZone: 'Europe/Istanbul' });
  } catch {
    return value;
  }
};

const AdminNotifications = () => {
  const [notifications, setNotifications] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [totalElements, setTotalElements] = useState(0);
  const [filters, setFilters] = useState({
    warehouseId: null,
    search: '',
    start: '',
    end: '',
    entityType: ''
  });

  useEffect(() => {
    const fetchWarehouses = async () => {
      try {
        const res = await axios.get('/api/warehouses/active');
        setWarehouses(res.data || []);
      } catch {
        setWarehouses([]);
      }
    };
    fetchWarehouses();
  }, []);

  const fetchNotifications = async () => {
    setLoading(true);
    setError('');
    try {
      const params = {
        page,
        size,
        search: filters.search || undefined,
        warehouseId: filters.warehouseId || undefined,
        entityType: filters.entityType || undefined
      };
      if (filters.start) params.startDate = new Date(filters.start).toISOString();
      if (filters.end) params.endDate = new Date(filters.end).toISOString();
      const res = await axios.get('/api/notifications/search', { params });
      const data = res.data;
      const list = data?.content || (Array.isArray(data) ? data : []);
      setNotifications(list);
      setTotalElements(data?.totalElements ?? list.length);
    } catch (e) {
      setError('Bildirimler yüklenirken hata oluştu');
      setNotifications([]);
      setTotalElements(0);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchNotifications();
  }, [page, size, filters]);

  const totalPages = useMemo(() => {
    if (size <= 0) return 0;
    return Math.max(1, Math.ceil((totalElements || 0) / size));
  }, [size, totalElements]);

  const clearFilters = () => {
    setFilters({ warehouseId: null, search: '', start: '', end: '', entityType: '' });
    setPage(0);
  };

  return (
    <div className="container py-3">
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-3 mb-3">
        <div>
          <h3 className="mb-0">Bildirim Yönetimi</h3>
          <div className="text-muted small">Depo, tarih ve arama kriterleri ile filtrele</div>
        </div>
        <div className="d-flex align-items-center gap-2">
          <select
            className="form-select form-select-sm"
            style={{ width: '120px' }}
            value={size}
            onChange={e => { setSize(Number(e.target.value)); setPage(0); }}
          >
            {[10, 20, 50, 100].map(opt => (
              <option key={opt} value={opt}>{opt}/sayfa</option>
            ))}
          </select>
          <button className="btn btn-outline-secondary btn-sm" onClick={clearFilters}>
            <i className="fas fa-eraser me-1"></i>
            Temizle
          </button>
        </div>
      </div>

      <div className="card mb-3">
        <div className="card-body">
          <div className="row g-3">
            <div className="col-lg-4">
              <label className="form-label fw-semibold">Depo</label>
              <SearchableSelect
                options={warehouses.map(w => ({ value: w.id, label: w.name }))}
                value={filters.warehouseId}
                onChange={val => { setFilters(prev => ({ ...prev, warehouseId: val })); setPage(0); }}
                placeholder="Depo seçin"
              />
            </div>
            <div className="col-lg-4">
              <label className="form-label fw-semibold">Arama</label>
              <input
                type="text"
                className="form-control"
                placeholder="Stok adı/kodu, kullanıcı, mesaj..."
                value={filters.search}
                onChange={e => { setFilters(prev => ({ ...prev, search: e.target.value })); setPage(0); }}
              />
            </div>
            <div className="col-lg-2">
              <label className="form-label fw-semibold">Tür</label>
              <select
                className="form-select"
                value={filters.entityType}
                onChange={e => { setFilters(prev => ({ ...prev, entityType: e.target.value })); setPage(0); }}
              >
                <option value="">Hepsi</option>
                <option value="Stock">Stok</option>
                <option value="StockTransfer">Transfer</option>
                <option value="StockRequest">Stok Talebi</option>
              </select>
            </div>
           
            <div className="col-lg-3">
              <label className="form-label fw-semibold">Başlangıç</label>
              <input
                type="datetime-local"
                className="form-control"
                value={filters.start}
                onChange={e => { setFilters(prev => ({ ...prev, start: e.target.value })); setPage(0); }}
              />
            </div>
            <div className="col-lg-3">
              <label className="form-label fw-semibold">Bitiş</label>
              <input
                type="datetime-local"
                className="form-control"
                value={filters.end}
                onChange={e => { setFilters(prev => ({ ...prev, end: e.target.value })); setPage(0); }}
              />
            </div>
             <div className="col-lg-2 d-flex align-items-end justify-content-end">
              <button className="btn btn-primary w-100 w-lg-auto ms-lg-auto" onClick={() => { setPage(0); fetchNotifications(); }}>
                <i className="fas fa-search me-2"></i>
                Filtrele
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
              {notifications.length === 0 ? (
                <div className="text-muted">Kayıt bulunamadı.</div>
              ) : (
                <div className="table-responsive">
                  <table className="table table-hover align-middle">
                    <thead>
                      <tr>
                        <th>Tarih</th>
                        <th>Başlık</th>
                        <th>Mesaj</th>
                        <th>Kullanıcı</th>
                      </tr>
                    </thead>
                    <tbody>
                      {notifications.map(n => (
                        <tr key={n.id}>
                          <td className="text-nowrap">{formatDateTime(n.createdAt)}</td>
                          <td className="fw-semibold">{n.title}</td>
                          <td style={{ minWidth: '260px', whiteSpace: 'pre-wrap' }}>
                            <div>{n.message}</div>
                            <div className="text-muted small mt-2 d-flex flex-wrap gap-2">
                              {n.warehouseName && (
                                <span className="badge bg-light text-dark">
                                  <i className="fas fa-warehouse me-1"></i>{n.warehouseName}
                                </span>
                              )}
                              {n.sourceWarehouseName && (
                                <span className="badge bg-light text-dark">
                                  <i className="fas fa-arrow-right me-1"></i>{n.sourceWarehouseName}
                                </span>
                              )}
                              {n.destinationWarehouseName && (
                                <span className="badge bg-light text-dark">
                                  <i className="fas fa-arrow-right me-1"></i>{n.destinationWarehouseName}
                                </span>
                              )}
                              {n.productSku && (
                                <span className="badge bg-secondary">SKU: {n.productSku}</span>
                              )}
                              {n.productName && (
                                <span className="badge bg-light text-dark">
                                  <i className="fas fa-box me-1"></i>{n.productName}
                                </span>
                              )}
                              {typeof n.quantity === 'number' && (
                                <span className="badge bg-info text-dark">Adet: {n.quantity}</span>
                              )}
                            </div>
                            {n.note && (
                              <div style={{
                                marginTop: '0.5rem',
                                padding: '0.5rem 0.75rem',
                                backgroundColor: '#fefce8',
                                borderLeft: '3px solid #eab308',
                                borderRadius: '0 6px 6px 0',
                                fontSize: '0.82rem',
                                color: '#713f12',
                                display: 'flex',
                                alignItems: 'flex-start',
                                gap: '0.4rem'
                              }}>
                                <i className="fas fa-sticky-note" style={{ marginTop: '2px', flexShrink: 0, color: '#ca8a04' }}></i>
                                <span style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{n.note}</span>
                              </div>
                            )}
                          </td>
                          <td className="text-nowrap">
                            <div className="d-flex flex-column gap-1 align-items-start">
                              {n.actor && n.actor.trim() ? (
                                <span className="badge bg-primary">{n.actor}</span>
                              ) : (
                                <span className="text-muted">—</span>
                              )}
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <div className="d-flex justify-content-between align-items-center mt-3 flex-wrap gap-2">
                <div className="text-muted small">
                  Toplam {totalElements} kayıt • Sayfa {page + 1} / {totalPages}
                </div>
                <PaginationControls page={page} totalPages={totalPages} onPageChange={setPage} />
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default AdminNotifications;

