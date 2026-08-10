import React, { useEffect, useState, useCallback } from 'react';
import axios from 'axios';

const getAuthToken = () => localStorage.getItem('auth_token');
const authHeader = () => ({ headers: { Authorization: `Bearer ${getAuthToken()}` } });

/**
 * Admin log viewer for Cezeri assistant conversations. Paginated list with
 * filters (profile, date range, username search). Clicking a row opens a
 * drawer with the full message transcript including token usage per turn.
 */
export default function AssistantLogsPage() {
  const [conversations, setConversations] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [profileFilter, setProfileFilter] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState(null);
  const [selectedDetail, setSelectedDetail] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.append('page', String(page));
      params.append('size', '25');
      if (profileFilter) params.append('profile', profileFilter);
      if (startDate) params.append('startDate', startDate);
      if (endDate) params.append('endDate', endDate);
      if (searchTerm.trim()) params.append('search', searchTerm.trim());
      const resp = await axios.get('/api/admin/assistant/conversations?' + params, authHeader());
      const data = resp.data || {};
      setConversations(data.content || []);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
    } catch (e) {
      setConversations([]);
    } finally {
      setLoading(false);
    }
  }, [page, profileFilter, startDate, endDate, searchTerm]);

  useEffect(() => {
    load();
  }, [load]);

  const resetFilters = () => {
    setProfileFilter('');
    setStartDate('');
    setEndDate('');
    setSearchTerm('');
    setPage(0);
  };

  const openDetail = async (conv) => {
    setSelected(conv);
    setSelectedDetail(null);
    try {
      const resp = await axios.get(`/api/admin/assistant/conversations/${conv.id}`, authHeader());
      setSelectedDetail(resp.data);
    } catch (e) {
      setSelectedDetail({ error: 'Detaylar yüklenemedi.' });
    }
  };

  const closeDetail = () => {
    setSelected(null);
    setSelectedDetail(null);
  };

  const hasActiveFilters = profileFilter || startDate || endDate || searchTerm.trim();

  return (
    <div className="container-fluid">
      <h2 className="mb-3">
        <i className="fas fa-comments me-2 text-primary"></i>
        Asistan Sohbet Logları
      </h2>

      {/* ── Filters ── */}
      <div className="card border-0 shadow-sm mb-3">
        <div className="card-body py-3">
          <div className="row g-2 align-items-end">
            <div className="col-md-2">
              <label className="form-label small fw-semibold mb-1">Profil</label>
              <select
                className="form-select form-select-sm"
                value={profileFilter}
                onChange={(e) => {
                  setProfileFilter(e.target.value);
                  setPage(0);
                }}
              >
                <option value="">Tümü</option>
                <option value="STORE">Mağaza (Store)</option>
                <option value="WMS">Depo (WMS)</option>
              </select>
            </div>
            <div className="col-md-2">
              <label className="form-label small fw-semibold mb-1">Başlangıç</label>
              <input
                type="date"
                className="form-control form-control-sm"
                value={startDate}
                onChange={(e) => {
                  setStartDate(e.target.value);
                  setPage(0);
                }}
              />
            </div>
            <div className="col-md-2">
              <label className="form-label small fw-semibold mb-1">Bitiş</label>
              <input
                type="date"
                className="form-control form-control-sm"
                value={endDate}
                onChange={(e) => {
                  setEndDate(e.target.value);
                  setPage(0);
                }}
              />
            </div>
            <div className="col-md-3">
              <label className="form-label small fw-semibold mb-1">Kullanıcı Ara</label>
              <input
                type="text"
                className="form-control form-control-sm"
                placeholder="Kullanıcı adı veya session id..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    setPage(0);
                    load();
                  }
                }}
              />
            </div>
            <div className="col-md-3 d-flex gap-2">
              <button
                className="btn btn-sm btn-primary"
                onClick={() => {
                  setPage(0);
                  load();
                }}
                disabled={loading}
              >
                <i className="fas fa-search me-1"></i> Ara
              </button>
              {hasActiveFilters && (
                <button className="btn btn-sm btn-outline-secondary" onClick={resetFilters}>
                  <i className="fas fa-times me-1"></i> Temizle
                </button>
              )}
              <button className="btn btn-sm btn-outline-secondary" onClick={load} disabled={loading}>
                <i className="fas fa-sync-alt"></i>
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* ── Result count ── */}
      <div className="text-muted small mb-2">
        {totalElements > 0 ? `${totalElements} sohbet bulundu` : ''}
      </div>

      {/* ── Table ── */}
      <div className="card border-0 shadow-sm">
        <div className="card-body p-0">
          {loading ? (
            <div className="p-3 text-center text-muted">Yükleniyor…</div>
          ) : conversations.length === 0 ? (
            <div className="p-3 text-center text-muted">Kayıt yok.</div>
          ) : (
            <div className="table-responsive">
              <table className="table table-hover mb-0">
                <thead className="table-light">
                  <tr>
                    <th style={{ minWidth: 150 }}>Tarih & Saat</th>
                    <th>Profil</th>
                    <th>Kullanıcı</th>
                    <th className="text-center">Mesaj</th>
                    <th>Token (P/C)</th>
                    <th>Maliyet</th>
                  </tr>
                </thead>
                <tbody>
                  {conversations.map((c) => (
                    <tr key={c.id} style={{ cursor: 'pointer' }} onClick={() => openDetail(c)}>
                      <td>
                        <div style={{ fontWeight: 500 }}>
                          {c.startedAt
                            ? new Date(c.startedAt).toLocaleDateString('tr-TR', {
                                day: 'numeric',
                                month: 'short',
                                year: 'numeric',
                              })
                            : ''}
                        </div>
                        <div className="text-muted small">
                          {c.startedAt
                            ? new Date(c.startedAt).toLocaleTimeString('tr-TR', {
                                hour: '2-digit',
                                minute: '2-digit',
                              })
                            : ''}
                          {c.lastActivityAt && c.lastActivityAt !== c.startedAt ? (
                            <>
                              {' '}
                              —{' '}
                              {new Date(c.lastActivityAt).toLocaleTimeString('tr-TR', {
                                hour: '2-digit',
                                minute: '2-digit',
                              })}
                            </>
                          ) : null}
                        </div>
                      </td>
                      <td>
                        <span
                          className={`badge ${c.profile === 'STORE' ? 'bg-success' : 'bg-info'} bg-opacity-75`}
                        >
                          {c.profile === 'STORE' ? 'Mağaza' : 'WMS'}
                        </span>
                      </td>
                      <td>
                        {c.username ||
                          (c.customerId ? `Müşteri #${c.customerId}` : c.guestSessionId ? 'Misafir' : '-')}
                      </td>
                      <td className="text-center">
                        <span className="badge bg-secondary bg-opacity-50">{c.messageCount}</span>
                      </td>
                      <td className="small">
                        {c.totalPromptTokens} / {c.totalCompletionTokens}
                      </td>
                      <td className="small">${formatCost(c.totalCostUsd)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      {/* ── Pagination ── */}
      {totalPages > 1 && (
        <div className="d-flex justify-content-center mt-3 gap-2">
          <button
            className="btn btn-sm btn-outline-secondary"
            disabled={page === 0}
            onClick={() => setPage(page - 1)}
          >
            ‹ Önceki
          </button>
          <span className="align-self-center text-muted small">
            Sayfa {page + 1} / {totalPages}
          </span>
          <button
            className="btn btn-sm btn-outline-secondary"
            disabled={page >= totalPages - 1}
            onClick={() => setPage(page + 1)}
          >
            Sonraki ›
          </button>
        </div>
      )}

      {/* ── Detail drawer ── */}
      {selected && (
        <div
          onClick={closeDetail}
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'rgba(0,0,0,0.5)',
            zIndex: 1050,
          }}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              position: 'absolute',
              right: 0,
              top: 0,
              bottom: 0,
              width: '600px',
              background: 'white',
              boxShadow: '-4px 0 20px rgba(0,0,0,0.2)',
              overflowY: 'auto',
              padding: 20,
            }}
          >
            <div className="d-flex justify-content-between align-items-center mb-3">
              <h5 className="mb-0">Sohbet #{selected.id}</h5>
              <button className="btn btn-sm btn-outline-secondary" onClick={closeDetail}>
                Kapat
              </button>
            </div>
            <div className="text-muted small mb-3">
              <span
                className={`badge ${selected.profile === 'STORE' ? 'bg-success' : 'bg-info'} bg-opacity-75 me-2`}
              >
                {selected.profile === 'STORE' ? 'Mağaza' : 'WMS'}
              </span>
              {selected.username || 'Misafir'}
              {' — '}
              {selected.startedAt ? new Date(selected.startedAt).toLocaleString('tr-TR') : ''}
              {' • '}
              {selected.messageCount} mesaj
              {' • $'}
              {formatCost(selected.totalCostUsd)}
            </div>
            {!selectedDetail ? (
              <div className="text-muted">Yükleniyor…</div>
            ) : selectedDetail.error ? (
              <div className="alert alert-danger">{selectedDetail.error}</div>
            ) : (
              <div>
                {(selectedDetail.messages || []).map((m) => (
                  <div key={m.id} className={`card mb-2 ${m.role === 'user' ? 'bg-light' : ''}`}>
                    <div className="card-body py-2 px-3">
                      <div className="d-flex justify-content-between">
                        <strong>{m.role === 'user' ? 'Kullanıcı' : 'Asistan'}</strong>
                        <small className="text-muted">
                          {m.createdAt
                            ? new Date(m.createdAt).toLocaleTimeString('tr-TR', {
                                hour: '2-digit',
                                minute: '2-digit',
                                second: '2-digit',
                              })
                            : ''}
                        </small>
                      </div>
                      <div
                        style={{
                          whiteSpace: 'pre-wrap',
                          marginTop: 4,
                          fontSize: 14,
                        }}
                      >
                        {m.content}
                      </div>
                      {m.role === 'assistant' && (m.promptTokens || m.completionTokens) ? (
                        <div className="text-muted small mt-2">
                          Token: {m.promptTokens || 0} / {m.completionTokens || 0}
                          {' • '}Gecikme: {m.latencyMs || 0} ms
                          {m.costUsd ? ` • Maliyet: $${formatCost(m.costUsd)}` : ''}
                        </div>
                      ) : null}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function formatCost(value) {
  if (value == null) return '0.0000';
  const n = typeof value === 'number' ? value : parseFloat(value);
  return isNaN(n) ? '0.0000' : n.toFixed(4);
}
