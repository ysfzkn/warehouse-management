import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { useAdminToast } from '../components/AdminToast';

const STATUS_MAP = {
  OPEN: { label: 'Açık', color: 'warning', icon: 'fa-envelope-open' },
  IN_PROGRESS: { label: 'İşlemde', color: 'info', icon: 'fa-spinner' },
  RESOLVED: { label: 'Yanıtlandı', color: 'success', icon: 'fa-check-circle' },
  CLOSED: { label: 'Kapatıldı', color: 'secondary', icon: 'fa-times-circle' },
};

const fmtDate = (d) => d ? new Date(d).toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—';

export default function AdminSupportTickets() {
  const [tickets, setTickets] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [statusFilter, setStatusFilter] = useState('');
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [counts, setCounts] = useState({});
  const [selected, setSelected] = useState(null);
  const [replyText, setReplyText] = useState('');
  const [replyLoading, setReplyLoading] = useState(false);
  const toast = useAdminToast();
  const navigate = useNavigate();

  const fetchTickets = useCallback(() => {
    setLoading(true);
    const params = { page, size: 20 };
    if (statusFilter) params.status = statusFilter;
    if (search) params.search = search;
    axios.get('/api/admin/support-tickets', { params })
      .then(r => { setTickets(r.data?.content || []); setTotalPages(r.data?.totalPages || 0); setTotalElements(r.data?.totalElements || 0); })
      .catch(() => {}).finally(() => setLoading(false));
  }, [page, statusFilter, search]);

  const fetchCounts = () => {
    axios.get('/api/admin/support-tickets/counts').then(r => setCounts(r.data || {})).catch(() => {});
  };

  useEffect(() => { fetchTickets(); fetchCounts(); }, [fetchTickets]);

  const handleReply = async () => {
    if (!replyText.trim()) { toast.warning('Yanıt boş olamaz.'); return; }
    setReplyLoading(true);
    try {
      await axios.put(`/api/admin/support-tickets/${selected.id}/reply`, { reply: replyText });
      toast.success('Yanıt gönderildi.');
      setSelected(null); setReplyText(''); fetchTickets(); fetchCounts();
    } catch (e) { toast.error(e.response?.data?.message || 'Yanıt gönderilemedi.'); }
    finally { setReplyLoading(false); }
  };

  const handleStatusChange = async (id, status) => {
    try {
      await axios.put(`/api/admin/support-tickets/${id}/status`, { status });
      toast.success('Durum güncellendi.');
      fetchTickets(); fetchCounts();
      if (selected?.id === id) setSelected(prev => ({ ...prev, status }));
    } catch { toast.error('Durum güncellenemedi.'); }
  };

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="mb-1">Destek Talepleri</h2>
          <p className="text-muted small mb-0">Müşteri destek taleplerini yönetin ve yanıtlayın</p>
        </div>
        <span className="badge bg-primary fs-6">{totalElements} talep</span>
      </div>

      {/* Status counts */}
      <div className="row g-3 mb-4">
        {Object.entries(STATUS_MAP).map(([key, s]) => (
          <div key={key} className="col-6 col-md-3">
            <div className={`card border-0 shadow-sm h-100 ${statusFilter === key ? 'border-start border-4 border-' + s.color : ''}`}
              style={{ cursor: 'pointer', borderRadius: 12 }} onClick={() => { setStatusFilter(statusFilter === key ? '' : key); setPage(0); }}>
              <div className="card-body py-3 d-flex align-items-center gap-3">
                <div className={`bg-${s.color} bg-opacity-10 rounded-circle d-flex align-items-center justify-content-center`} style={{ width: 40, height: 40 }}>
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

      {/* Search */}
      <div className="mb-3">
        <input className="form-control" placeholder="Sipariş no veya konu ara..." value={search}
          onChange={e => { setSearch(e.target.value); setPage(0); }} />
      </div>

      <div className="row g-4">
        {/* List */}
        <div className={selected ? 'col-lg-5' : 'col-12'}>
          <div className="card border-0 shadow-sm">
            <div className="card-body p-0">
              {loading ? <div className="text-center py-5"><span className="spinner-border spinner-border-sm" /></div>
              : tickets.length === 0 ? (
                <div className="text-center py-5 text-muted">
                  <i className="fas fa-headset fa-3x mb-3 opacity-25 d-block" />
                  <p>Destek talebi bulunamadı.</p>
                </div>
              ) : (
                <div className="list-group list-group-flush">
                  {tickets.map(t => {
                    const s = STATUS_MAP[t.status] || STATUS_MAP.OPEN;
                    return (
                      <div key={t.id}
                        className={`list-group-item list-group-item-action ${selected?.id === t.id ? 'active' : ''}`}
                        style={{ cursor: 'pointer', borderLeft: `3px solid var(--bs-${s.color})` }}
                        onClick={() => { setSelected(t); setReplyText(t.adminReply || ''); }}>
                        <div className="d-flex justify-content-between align-items-start">
                          <div className="min-w-0">
                            <div className="fw-semibold small text-truncate">{t.topic}</div>
                            <div className="text-muted" style={{ fontSize: 11 }}>{t.customerName} · #{t.orderNumber}</div>
                          </div>
                          <span className={`badge bg-${s.color} ms-2 flex-shrink-0`} style={{ fontSize: 10 }}>{s.label}</span>
                        </div>
                        <div className="text-muted mt-1" style={{ fontSize: 11 }}>{fmtDate(t.createdAt)}</div>
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
                  <h6 className="fw-bold mb-1">{selected.topic}</h6>
                  <div className="small text-muted">
                    {selected.customerName} · <a href={`mailto:${selected.customerEmail}`}>{selected.customerEmail}</a>
                    {selected.customerPhone && <> · <a href={`tel:${selected.customerPhone}`}><i className="fas fa-phone me-1" style={{fontSize:10}} />{selected.customerPhone}</a></>}
                  </div>
                  {selected.orderNumber && (
                    <div className="small">
                      Sipariş: <button className="btn btn-link btn-sm p-0 fw-bold" onClick={() => navigate('/admin/orders')} style={{fontSize:'inherit'}}>
                        #{selected.orderNumber} <i className="fas fa-external-link-alt ms-1" style={{fontSize:9}} />
                      </button>
                    </div>
                  )}
                </div>
                <button className="btn-close" onClick={() => setSelected(null)} />
              </div>
              <div className="card-body">
                {/* Müşteri mesajı */}
                <div className="d-flex gap-3 mb-3">
                  <div className="bg-primary bg-opacity-10 rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style={{ width: 36, height: 36 }}>
                    <i className="fas fa-user text-primary" style={{ fontSize: 14 }} />
                  </div>
                  <div className="flex-grow-1">
                    <div className="small fw-semibold text-primary mb-1">{selected.customerName} <span className="text-muted fw-normal">· {fmtDate(selected.createdAt)}</span></div>
                    <div className="p-3 rounded-3" style={{ background: '#eff6ff', border: '1px solid #bfdbfe', whiteSpace: 'pre-wrap', fontSize: '0.88rem', lineHeight: 1.6 }}>
                      {selected.message}
                    </div>
                  </div>
                </div>

                {/* Admin yanıtı (mevcut) */}
                {selected.adminReply && selected.status !== 'OPEN' && (
                  <div className="d-flex gap-3 mb-3">
                    <div className="bg-success bg-opacity-10 rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style={{ width: 36, height: 36 }}>
                      <i className="fas fa-headset text-success" style={{ fontSize: 14 }} />
                    </div>
                    <div className="flex-grow-1">
                      <div className="small fw-semibold text-success mb-1">{selected.repliedBy || 'Admin'} <span className="text-muted fw-normal">· {fmtDate(selected.repliedAt)}</span></div>
                      <div className="p-3 rounded-3" style={{ background: '#f0fdf4', border: '1px solid #bbf7d0', whiteSpace: 'pre-wrap', fontSize: '0.88rem', lineHeight: 1.6 }}>
                        {selected.adminReply}
                      </div>
                    </div>
                  </div>
                )}

                {/* Yanıt yazma */}
                <div className="border-top pt-3 mt-3">
                  <label className="form-label small fw-semibold"><i className="fas fa-reply me-1" />Yanıtınız</label>
                  <textarea className="form-control" rows={4} value={replyText} onChange={e => setReplyText(e.target.value)}
                    placeholder="Müşteriye yanıtınızı yazın..." />
                  <div className="d-flex justify-content-between align-items-center mt-3">
                    {/* Durum değiştirme */}
                    <div className="d-flex gap-1">
                      {Object.entries(STATUS_MAP).map(([key, s]) => (
                        <button key={key}
                          className={`btn btn-sm ${selected.status === key ? `btn-${s.color}` : `btn-outline-${s.color}`}`}
                          onClick={() => handleStatusChange(selected.id, key)}
                          style={{ fontSize: 11 }}>
                          {s.label}
                        </button>
                      ))}
                    </div>
                    <button className="btn btn-primary btn-sm px-4" onClick={handleReply} disabled={replyLoading || !replyText.trim()}>
                      {replyLoading ? <span className="spinner-border spinner-border-sm" /> : <><i className="fas fa-paper-plane me-1" />Yanıtla</>}
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
