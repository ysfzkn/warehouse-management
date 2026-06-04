import React, { useState, useEffect, useCallback, useRef } from 'react';
import axios from 'axios';
import { useAdminToast } from '../components/AdminToast';

const STATUS_MAP = {
  NEW:      { label: 'Yeni',     color: 'warning',   icon: 'fa-envelope' },
  READ:     { label: 'Okundu',   color: 'info',      icon: 'fa-envelope-open' },
  ARCHIVED: { label: 'Arşiv',    color: 'secondary', icon: 'fa-archive' },
};

const fmtDate = (d) => d ? new Date(d).toLocaleString('tr-TR', {
  day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'
}) : '—';

export default function AdminContactMessages() {
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [statusFilter, setStatusFilter] = useState('');
  const [loading, setLoading] = useState(true);
  const [counts, setCounts] = useState({});
  const [selected, setSelected] = useState(null);
  const toast = useAdminToast();
  // Stable ref so fetch callbacks don't rebuild on every render
  const toastRef = useRef(toast);
  useEffect(() => { toastRef.current = toast; }, [toast]);

  const fetchItems = useCallback(() => {
    setLoading(true);
    const params = { page, size: 20 };
    if (statusFilter) params.status = statusFilter;
    axios.get('/api/admin/contact-messages', { params })
      .then(r => {
        setItems(r.data?.content || []);
        setTotalPages(r.data?.totalPages || 0);
        setTotalElements(r.data?.totalElements || 0);
      })
      .catch(() => toastRef.current?.error('Mesajlar yüklenemedi.'))
      .finally(() => setLoading(false));
  }, [page, statusFilter]);

  const fetchCounts = useCallback(() => {
    axios.get('/api/admin/contact-messages/counts')
      .then(r => setCounts(r.data || {}))
      .catch(() => {});
  }, []);

  useEffect(() => { fetchItems(); fetchCounts(); }, [fetchItems, fetchCounts]);

  const handleStatusChange = async (id, status) => {
    try {
      await axios.put(`/api/admin/contact-messages/${id}/status`, { status });
      toast.success('Durum güncellendi.');
      fetchItems();
      fetchCounts();
      if (selected?.id === id) setSelected(prev => ({ ...prev, status }));
    } catch {
      toast.error('Durum güncellenemedi.');
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Bu mesajı kalıcı olarak silmek istediğinize emin misiniz?')) return;
    try {
      await axios.delete(`/api/admin/contact-messages/${id}`);
      toast.success('Mesaj silindi.');
      if (selected?.id === id) setSelected(null);
      fetchItems();
      fetchCounts();
    } catch {
      toast.error('Mesaj silinemedi.');
    }
  };

  const openDetail = (item) => {
    setSelected(item);
    // Auto-mark NEW as READ when opened
    if (item.status === 'NEW') {
      handleStatusChange(item.id, 'READ');
    }
  };

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="mb-1">İletişim Mesajları</h2>
          <p className="text-muted small mb-0">Müşterilerin iletişim formundan gönderdiği mesajlar</p>
        </div>
        <span className="badge bg-primary fs-6">{totalElements} mesaj</span>
      </div>

      {/* Status counts */}
      <div className="row g-3 mb-4">
        {Object.entries(STATUS_MAP).map(([key, s]) => (
          <div key={key} className="col-12 col-md-4">
            <div className={`card border-0 shadow-sm h-100 ${statusFilter === key ? 'border-start border-4 border-' + s.color : ''}`}
              style={{ cursor: 'pointer', borderRadius: 12 }}
              onClick={() => { setStatusFilter(statusFilter === key ? '' : key); setPage(0); }}>
              <div className="card-body py-3 d-flex align-items-center gap-3">
                <div className={`bg-${s.color} bg-opacity-10 rounded-circle d-flex align-items-center justify-content-center`}
                  style={{ width: 40, height: 40 }}>
                  <i className={`fas ${s.icon} text-${s.color}`} />
                </div>
                <div>
                  <div className="fw-bold fs-5">{counts[key] || 0}</div>
                  <div className="small text-muted">{s.label}</div>
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="row g-4">
        {/* List */}
        <div className={selected ? 'col-lg-5' : 'col-12'}>
          <div className="card border-0 shadow-sm">
            <div className="card-body p-0">
              {loading ? (
                <div className="text-center py-5"><span className="spinner-border spinner-border-sm" /></div>
              ) : items.length === 0 ? (
                <div className="text-center py-5 text-muted">
                  <i className="fas fa-envelope-open fa-3x mb-3 opacity-25 d-block" />
                  <p>İletişim mesajı bulunamadı.</p>
                </div>
              ) : (
                <div className="list-group list-group-flush">
                  {items.map(m => {
                    const s = STATUS_MAP[m.status] || STATUS_MAP.NEW;
                    return (
                      <div key={m.id}
                        className={`list-group-item list-group-item-action ${selected?.id === m.id ? 'active' : ''}`}
                        style={{ cursor: 'pointer', borderLeft: `3px solid var(--bs-${s.color})` }}
                        onClick={() => openDetail(m)}>
                        <div className="d-flex justify-content-between align-items-start">
                          <div className="min-w-0">
                            <div className="fw-semibold small text-truncate">{m.subject}</div>
                            <div className="text-muted" style={{ fontSize: 11 }}>{m.name} · {m.email}</div>
                          </div>
                          <span className={`badge bg-${s.color} ms-2 flex-shrink-0`} style={{ fontSize: 10 }}>{s.label}</span>
                        </div>
                        <div className="text-muted mt-1" style={{ fontSize: 11 }}>{fmtDate(m.createdAt)}</div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>

          {totalPages > 1 && (
            <div className="d-flex justify-content-center gap-2 mt-3">
              <button className="btn btn-sm btn-outline-primary" disabled={page === 0} onClick={() => setPage(p => p - 1)}>Önceki</button>
              <span className="small align-self-center text-muted">{page + 1} / {totalPages}</span>
              <button className="btn btn-sm btn-outline-primary" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>Sonraki</button>
            </div>
          )}
        </div>

        {/* Detail */}
        {selected && (
          <div className="col-lg-7">
            <div className="card border-0 shadow-sm" style={{ borderRadius: 14 }}>
              <div className="card-header bg-transparent d-flex justify-content-between align-items-start py-3">
                <div>
                  <h6 className="fw-bold mb-1">{selected.subject}</h6>
                  <div className="small text-muted">
                    <strong>{selected.name}</strong> ·{' '}
                    <a href={`mailto:${selected.email}`}>{selected.email}</a>
                    {selected.phone && <> · <a href={`tel:${selected.phone}`}><i className="fas fa-phone me-1" style={{fontSize:10}} />{selected.phone}</a></>}
                  </div>
                  <div className="text-muted" style={{ fontSize: 11 }}>
                    {fmtDate(selected.createdAt)} {selected.ipAddress && `· IP: ${selected.ipAddress}`}
                    {' · '}
                    {selected.emailSent
                      ? <span className="text-success"><i className="fas fa-check me-1" />E-posta iletildi</span>
                      : <span className="text-warning"><i className="fas fa-exclamation-triangle me-1" />E-posta gönderilmedi</span>}
                  </div>
                </div>
                <button className="btn-close" onClick={() => setSelected(null)} />
              </div>
              <div className="card-body">
                <div className="p-3 rounded-3"
                  style={{ background: '#f8fafc', border: '1px solid #e2e8f0', whiteSpace: 'pre-wrap', fontSize: '0.9rem', lineHeight: 1.7 }}>
                  {selected.message}
                </div>

                {selected.readBy && (
                  <div className="text-muted mt-3" style={{ fontSize: 11 }}>
                    Okundu olarak işaretleyen: <strong>{selected.readBy}</strong> · {fmtDate(selected.readAt)}
                  </div>
                )}

                <div className="border-top pt-3 mt-3 d-flex justify-content-between align-items-center flex-wrap gap-2">
                  <div className="d-flex gap-1 flex-wrap">
                    {Object.entries(STATUS_MAP).map(([key, s]) => (
                      <button key={key}
                        className={`btn btn-sm ${selected.status === key ? `btn-${s.color}` : `btn-outline-${s.color}`}`}
                        onClick={() => handleStatusChange(selected.id, key)}
                        style={{ fontSize: 11 }}>
                        <i className={`fas ${s.icon} me-1`} />{s.label}
                      </button>
                    ))}
                  </div>
                  <div className="d-flex gap-2">
                    <a href={`mailto:${selected.email}?subject=Re:%20${encodeURIComponent(selected.subject)}`}
                       className="btn btn-primary btn-sm">
                      <i className="fas fa-reply me-1" />Yanıtla
                    </a>
                    <button className="btn btn-outline-danger btn-sm" onClick={() => handleDelete(selected.id)}>
                      <i className="fas fa-trash me-1" />Sil
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
