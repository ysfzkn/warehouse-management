import React, { useEffect, useState, useCallback } from 'react';
import axios from 'axios';

/**
 * Admin log viewer for Cezeri assistant conversations. Shows a paginated
 * list of conversations filtered by profile; clicking a row opens a drawer
 * with the full message transcript including token usage per turn.
 */
export default function AssistantLogsPage() {
  const [conversations, setConversations] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [profileFilter, setProfileFilter] = useState('');
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState(null);
  const [selectedDetail, setSelectedDetail] = useState(null);

  const token = () => localStorage.getItem('auth_token');
  const authHeader = () => ({ headers: { Authorization: `Bearer ${token()}` } });

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.append('page', String(page));
      params.append('size', '25');
      if (profileFilter) params.append('profile', profileFilter);
      const resp = await axios.get('/api/admin/assistant/conversations?' + params, authHeader());
      const data = resp.data || {};
      setConversations(data.content || []);
      setTotalPages(data.totalPages || 0);
    } catch (e) {
      setConversations([]);
    } finally {
      setLoading(false);
    }
  }, [page, profileFilter]);

  useEffect(() => { load(); }, [load]);

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

  const closeDetail = () => { setSelected(null); setSelectedDetail(null); };

  return (
    <div className="container-fluid">
      <h2 className="mb-3">Asistan Sohbet Logları</h2>

      <div className="d-flex gap-2 mb-3">
        <select
          className="form-control"
          style={{ maxWidth: 220 }}
          value={profileFilter}
          onChange={(e) => { setProfileFilter(e.target.value); setPage(0); }}
        >
          <option value="">Tümü</option>
          <option value="STORE">Mağaza (Store)</option>
          <option value="WMS">Depo (WMS)</option>
        </select>
        <button className="btn btn-outline-secondary" onClick={load} disabled={loading}>
          Yenile
        </button>
      </div>

      <div className="card">
        <div className="card-body p-0">
          {loading ? (
            <div className="p-3 text-center text-muted">Yükleniyor…</div>
          ) : conversations.length === 0 ? (
            <div className="p-3 text-center text-muted">Kayıt yok.</div>
          ) : (
            <table className="table table-hover mb-0">
              <thead>
                <tr>
                  <th>Başlangıç</th>
                  <th>Profil</th>
                  <th>Kullanıcı</th>
                  <th>Mesaj</th>
                  <th>Token (Prompt/Tamamlama)</th>
                  <th>Maliyet</th>
                </tr>
              </thead>
              <tbody>
                {conversations.map((c) => (
                  <tr key={c.id} style={{ cursor: 'pointer' }} onClick={() => openDetail(c)}>
                    <td>{c.startedAt ? new Date(c.startedAt).toLocaleString('tr-TR') : ''}</td>
                    <td>{c.profile}</td>
                    <td>{c.username || (c.customerId ? `Müşteri #${c.customerId}` : (c.guestSessionId ? 'Misafir' : '-'))}</td>
                    <td>{c.messageCount}</td>
                    <td>{c.totalPromptTokens} / {c.totalCompletionTokens}</td>
                    <td>${formatCost(c.totalCostUsd)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {totalPages > 1 && (
        <div className="d-flex justify-content-center mt-3 gap-2">
          <button className="btn btn-sm btn-outline-secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>‹ Önceki</button>
          <span className="align-self-center">Sayfa {page + 1} / {totalPages}</span>
          <button className="btn btn-sm btn-outline-secondary" disabled={page >= totalPages - 1} onClick={() => setPage(page + 1)}>Sonraki ›</button>
        </div>
      )}

      {selected && (
        <div
          onClick={closeDetail}
          style={{
            position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
            background: 'rgba(0,0,0,0.5)', zIndex: 1050,
          }}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              position: 'absolute', right: 0, top: 0, bottom: 0, width: '600px',
              background: 'white', boxShadow: '-4px 0 20px rgba(0,0,0,0.2)',
              overflowY: 'auto', padding: 20,
            }}
          >
            <div className="d-flex justify-content-between align-items-center mb-3">
              <h4 className="mb-0">Sohbet #{selected.id}</h4>
              <button className="btn btn-sm btn-outline-secondary" onClick={closeDetail}>Kapat</button>
            </div>
            <div className="text-muted small mb-3">
              {selected.profile} — {selected.startedAt ? new Date(selected.startedAt).toLocaleString('tr-TR') : ''}
              {' '}• {selected.messageCount} mesaj • ${formatCost(selected.totalCostUsd)}
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
                        <small className="text-muted">{m.createdAt ? new Date(m.createdAt).toLocaleString('tr-TR') : ''}</small>
                      </div>
                      <div style={{ whiteSpace: 'pre-wrap', marginTop: 4 }}>{m.content}</div>
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
