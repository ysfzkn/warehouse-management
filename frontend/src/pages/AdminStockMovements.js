import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';

const SOURCE_LABELS = {
  ADMIN: 'Admin', TRANSFER: 'Transfer', ORDER: 'Sipariş', SYSTEM: 'Sistem', IMPORT: 'İçe Aktarım'
};
const TYPE_LABELS = {
  QUANTITY_CHANGED: 'Miktar Değişimi', RESERVED: 'Rezerve', RELEASED: 'Serbest Bırakıldı',
  LOW_STOCK: 'Düşük Stok', OUT_OF_STOCK: 'Stok Tükendi', BACK_IN_STOCK: 'Stok Geldi'
};
const SOURCE_COLORS = {
  ORDER: 'primary', TRANSFER: 'info', ADMIN: 'warning', SYSTEM: 'secondary', IMPORT: 'success'
};
const TYPE_COLORS = {
  QUANTITY_CHANGED: 'primary', RESERVED: 'warning', RELEASED: 'success',
  LOW_STOCK: 'danger', OUT_OF_STOCK: 'danger', BACK_IN_STOCK: 'success'
};

export default function AdminStockMovements() {
  const [events, setEvents] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [source, setSource] = useState('');
  const [eventType, setEventType] = useState('');
  const [search, setSearch] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');

  const fetchEvents = useCallback(() => {
    setLoading(true);
    const params = { page, size: 30 };
    if (source) params.source = source;
    if (eventType) params.eventType = eventType;
    if (search) params.search = search;
    if (startDate) params.startDate = startDate;
    if (endDate) params.endDate = endDate;
    axios.get('/api/admin/stock-events', { params })
      .then(r => {
        setEvents(r.data?.content || []);
        setTotalPages(r.data?.totalPages || 0);
        setTotalElements(r.data?.totalElements || 0);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [page, source, eventType, search, startDate, endDate]);

  useEffect(() => { fetchEvents(); }, [fetchEvents]);

  const clearFilters = () => {
    setSource(''); setEventType(''); setSearch(''); setStartDate(''); setEndDate(''); setPage(0);
  };

  const hasFilters = source || eventType || search || startDate || endDate;

  const fmtDate = (d) => d ? new Date(d).toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '';

  const diffBadge = (oldVal, newVal) => {
    if (oldVal == null || newVal == null) return null;
    const diff = newVal - oldVal;
    if (diff === 0) return <span className="badge bg-secondary">0</span>;
    if (diff > 0) return <span className="badge bg-success">+{diff}</span>;
    return <span className="badge bg-danger">{diff}</span>;
  };

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="mb-1">Stok Hareketleri</h2>
          <p className="text-muted small mb-0">Tüm stok değişikliklerini izleyin — sipariş, transfer, admin işlemleri</p>
        </div>
        <span className="badge bg-primary fs-6">{totalElements} kayıt</span>
      </div>

      {/* Filters */}
      <div className="card border-0 shadow-sm mb-4">
        <div className="card-body">
          <div className="row g-3 align-items-end">
            <div className="col-md-2">
              <label className="form-label small fw-medium">Kaynak</label>
              <select className="form-select form-select-sm" value={source} onChange={e => { setSource(e.target.value); setPage(0); }}>
                <option value="">Tümü</option>
                {Object.entries(SOURCE_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
            </div>
            <div className="col-md-2">
              <label className="form-label small fw-medium">Olay Tipi</label>
              <select className="form-select form-select-sm" value={eventType} onChange={e => { setEventType(e.target.value); setPage(0); }}>
                <option value="">Tümü</option>
                {Object.entries(TYPE_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
            </div>
            <div className="col-md-2">
              <label className="form-label small fw-medium">Başlangıç</label>
              <input type="date" className="form-control form-control-sm" value={startDate} onChange={e => { setStartDate(e.target.value); setPage(0); }} />
            </div>
            <div className="col-md-2">
              <label className="form-label small fw-medium">Bitiş</label>
              <input type="date" className="form-control form-control-sm" value={endDate} onChange={e => { setEndDate(e.target.value); setPage(0); }} />
            </div>
            <div className="col-md-3">
              <label className="form-label small fw-medium">Arama</label>
              <input type="text" className="form-control form-control-sm" placeholder="Sipariş no, açıklama..." value={search}
                onChange={e => { setSearch(e.target.value); setPage(0); }} />
            </div>
            <div className="col-md-1">
              {hasFilters && (
                <button className="btn btn-sm btn-outline-danger w-100" onClick={clearFilters}>
                  <i className="fas fa-times me-1" />Temizle
                </button>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Active filter chips */}
      {hasFilters && (
        <div className="d-flex gap-2 flex-wrap mb-3">
          {source && <span className="badge bg-primary bg-opacity-10 text-primary px-2 py-1">{SOURCE_LABELS[source]} <i className="fas fa-times ms-1" style={{cursor:'pointer'}} onClick={() => setSource('')} /></span>}
          {eventType && <span className="badge bg-info bg-opacity-10 text-info px-2 py-1">{TYPE_LABELS[eventType]} <i className="fas fa-times ms-1" style={{cursor:'pointer'}} onClick={() => setEventType('')} /></span>}
          {startDate && <span className="badge bg-light text-dark px-2 py-1">{startDate}'den <i className="fas fa-times ms-1" style={{cursor:'pointer'}} onClick={() => setStartDate('')} /></span>}
          {endDate && <span className="badge bg-light text-dark px-2 py-1">{endDate}'e kadar <i className="fas fa-times ms-1" style={{cursor:'pointer'}} onClick={() => setEndDate('')} /></span>}
          {search && <span className="badge bg-light text-dark px-2 py-1">"{search}" <i className="fas fa-times ms-1" style={{cursor:'pointer'}} onClick={() => setSearch('')} /></span>}
        </div>
      )}

      {/* Table */}
      <div className="card border-0 shadow-sm">
        <div className="card-body p-0">
          {loading ? (
            <div className="text-center py-5"><span className="spinner-border spinner-border-sm" /></div>
          ) : events.length === 0 ? (
            <div className="text-center py-5">
              <i className="fas fa-exchange-alt text-muted fa-3x mb-3 d-block opacity-25" />
              <p className="text-muted">Henüz stok hareketi kaydı yok.</p>
            </div>
          ) : (
            <div className="table-responsive">
              <table className="table table-hover align-middle mb-0 small">
                <thead className="table-light">
                  <tr>
                    <th>Tarih</th>
                    <th>Kaynak</th>
                    <th>Olay</th>
                    <th>Ürün</th>
                    <th className="text-center">Eski</th>
                    <th className="text-center">Yeni</th>
                    <th className="text-center">Fark</th>
                    <th>Açıklama</th>
                  </tr>
                </thead>
                <tbody>
                  {events.map(e => (
                    <tr key={e.id}>
                      <td className="text-nowrap">{fmtDate(e.createdAt)}</td>
                      <td>
                        <span className={`badge bg-${SOURCE_COLORS[e.source] || 'secondary'} bg-opacity-10 text-${SOURCE_COLORS[e.source] || 'secondary'}`}>
                          {SOURCE_LABELS[e.source] || e.source}
                        </span>
                      </td>
                      <td>
                        <span className={`badge bg-${TYPE_COLORS[e.eventType] || 'secondary'} bg-opacity-10 text-${TYPE_COLORS[e.eventType] || 'secondary'}`}>
                          {TYPE_LABELS[e.eventType] || e.eventType}
                        </span>
                      </td>
                      <td>
                        <div className="fw-medium">{e.productName || '-'}</div>
                        {e.stockId && <small className="text-muted">Stok #{e.stockId}</small>}
                      </td>
                      <td className="text-center">{e.oldValue ?? '-'}</td>
                      <td className="text-center fw-medium">{e.newValue ?? '-'}</td>
                      <td className="text-center">{diffBadge(e.oldValue, e.newValue)}</td>
                      <td className="text-muted" style={{maxWidth:300,overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap'}} title={e.sourceDetail}>
                        {e.sourceDetail || '-'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="d-flex justify-content-center mt-3 gap-2">
          <button className="btn btn-sm btn-outline-primary" disabled={page === 0} onClick={() => setPage(p => p - 1)}>
            <i className="fas fa-chevron-left me-1" />Önceki
          </button>
          <span className="align-self-center text-muted small">Sayfa {page + 1} / {totalPages}</span>
          <button className="btn btn-sm btn-outline-primary" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>
            Sonraki<i className="fas fa-chevron-right ms-1" />
          </button>
        </div>
      )}
    </div>
  );
}
