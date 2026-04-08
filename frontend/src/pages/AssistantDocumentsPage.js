import React, { useEffect, useState, useCallback } from 'react';
import axios from 'axios';

/**
 * Admin page for uploading and managing Cezeri reference documents
 * (FAQ, return policy, manuals). Minimal UI — the interesting parts are
 * the backend ingestion pipeline and the Tika/pgvector indexing.
 */
export default function AssistantDocumentsPage() {
  const [docs, setDocs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [file, setFile] = useState(null);
  const [title, setTitle] = useState('');
  const [scope, setScope] = useState('STORE');
  const [error, setError] = useState(null);

  const token = () => localStorage.getItem('auth_token');
  const authHeader = () => ({ headers: { Authorization: `Bearer ${token()}` } });

  const loadDocs = useCallback(async () => {
    setLoading(true);
    try {
      const resp = await axios.get('/api/admin/assistant/documents?size=100', authHeader());
      setDocs(Array.isArray(resp.data) ? resp.data : []);
      setError(null);
    } catch (e) {
      setError('Doküman listesi alınamadı.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadDocs(); }, [loadDocs]);

  const onUpload = async (e) => {
    e.preventDefault();
    if (!file) return;
    setUploading(true);
    setError(null);
    try {
      const form = new FormData();
      form.append('file', file);
      if (title.trim()) form.append('title', title.trim());
      form.append('scope', scope);
      await axios.post('/api/admin/assistant/documents', form, {
        headers: { Authorization: `Bearer ${token()}`, 'Content-Type': 'multipart/form-data' },
      });
      setFile(null);
      setTitle('');
      await loadDocs();
    } catch (e) {
      setError('Yükleme başarısız: ' + (e?.response?.data?.message || e.message));
    } finally {
      setUploading(false);
    }
  };

  const onReindex = async (id) => {
    try {
      await axios.post(`/api/admin/assistant/documents/${id}/reindex`, {}, authHeader());
      setTimeout(loadDocs, 1000);
    } catch (e) {
      setError('Yeniden indeksleme başlatılamadı.');
    }
  };

  const onDelete = async (id) => {
    if (!window.confirm('Dokümanı silmek istediğinize emin misiniz?')) return;
    try {
      await axios.delete(`/api/admin/assistant/documents/${id}`, authHeader());
      await loadDocs();
    } catch (e) {
      setError('Silme başarısız.');
    }
  };

  const statusBadge = (status) => {
    const colors = {
      PENDING: '#94a3b8',
      INDEXING: '#eab308',
      READY: '#10b981',
      FAILED: '#ef4444',
    };
    const labels = {
      PENDING: 'Bekliyor',
      INDEXING: 'İşleniyor',
      READY: 'Hazır',
      FAILED: 'Başarısız',
    };
    return (
      <span style={{
        background: colors[status] || '#64748b',
        color: 'white',
        padding: '2px 10px',
        borderRadius: 12,
        fontSize: 12,
        fontWeight: 600,
      }}>{labels[status] || status}</span>
    );
  };

  return (
    <div className="container-fluid">
      <h2 className="mb-3">Asistan Dokümanları</h2>
      <p className="text-muted">
        Mağaza politikaları, SSS, kullanım kılavuzları gibi dokümanları buradan yükleyin.
        Yüklenen dosyalar Cezeri asistanı tarafından müşteri sorularında kaynak olarak kullanılır.
      </p>

      <div className="card mb-4">
        <div className="card-header">Yeni Doküman Yükle</div>
        <div className="card-body">
          <form onSubmit={onUpload}>
            <div className="row g-3">
              <div className="col-md-5">
                <label className="form-label">Dosya (.pdf, .docx, .txt, .md)</label>
                <input
                  type="file"
                  className="form-control"
                  accept=".pdf,.docx,.txt,.md"
                  onChange={(e) => setFile(e.target.files?.[0] || null)}
                  required
                />
              </div>
              <div className="col-md-4">
                <label className="form-label">Başlık (opsiyonel)</label>
                <input
                  type="text"
                  className="form-control"
                  placeholder="Dosya adı kullanılır"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                />
              </div>
              <div className="col-md-3">
                <label className="form-label">Kapsam</label>
                <select className="form-control" value={scope} onChange={(e) => setScope(e.target.value)}>
                  <option value="STORE">Mağaza (E-ticaret)</option>
                  <option value="WMS">Depo Yönetimi</option>
                  <option value="BOTH">Her İkisi</option>
                </select>
              </div>
            </div>
            <button type="submit" className="btn btn-primary mt-3" disabled={!file || uploading}>
              {uploading ? 'Yükleniyor…' : 'Yükle ve İndeksle'}
            </button>
          </form>
        </div>
      </div>

      {error && <div className="alert alert-danger">{error}</div>}

      <div className="card">
        <div className="card-header d-flex justify-content-between align-items-center">
          <span>Mevcut Dokümanlar ({docs.length})</span>
          <button className="btn btn-sm btn-outline-secondary" onClick={loadDocs} disabled={loading}>
            Yenile
          </button>
        </div>
        <div className="card-body p-0">
          {loading ? (
            <div className="p-3 text-center text-muted">Yükleniyor…</div>
          ) : docs.length === 0 ? (
            <div className="p-3 text-center text-muted">Henüz doküman yok.</div>
          ) : (
            <table className="table table-hover mb-0">
              <thead>
                <tr>
                  <th>Başlık</th>
                  <th>Kapsam</th>
                  <th>Durum</th>
                  <th>Chunk</th>
                  <th>Boyut</th>
                  <th>Yükleyen</th>
                  <th>Tarih</th>
                  <th style={{ width: 200 }}>İşlemler</th>
                </tr>
              </thead>
              <tbody>
                {docs.map((d) => (
                  <tr key={d.id}>
                    <td><strong>{d.title}</strong><br /><small className="text-muted">{d.fileName}</small></td>
                    <td>{d.scope}</td>
                    <td>{statusBadge(d.status)}{d.errorMessage && (
                      <div style={{ fontSize: 11, color: '#ef4444', marginTop: 4 }}>{d.errorMessage}</div>
                    )}</td>
                    <td>{d.chunkCount}</td>
                    <td>{Math.round(d.sizeBytes / 1024)} KB</td>
                    <td>{d.uploadedBy || '-'}</td>
                    <td>{d.createdAt ? new Date(d.createdAt).toLocaleString('tr-TR') : ''}</td>
                    <td>
                      <button className="btn btn-sm btn-outline-primary me-2" onClick={() => onReindex(d.id)}>
                        Yeniden İndeksle
                      </button>
                      <button className="btn btn-sm btn-outline-danger" onClick={() => onDelete(d.id)}>
                        Sil
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
}
