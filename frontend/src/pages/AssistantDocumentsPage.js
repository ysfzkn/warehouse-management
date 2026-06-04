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

  // Multi-phase progress: upload bytes → backend indexing → done
  const [uploadPct, setUploadPct] = useState(0);            // 0-100 during HTTP upload
  const [phase, setPhase] = useState('idle');               // idle | uploading | indexing | done | failed
  const [phaseDoc, setPhaseDoc] = useState(null);           // last doc object from polling

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
    setUploadPct(0);
    setPhase('uploading');
    setPhaseDoc(null);

    try {
      const form = new FormData();
      form.append('file', file);
      if (title.trim()) form.append('title', title.trim());
      form.append('scope', scope);

      // Phase 1: bytes transferring to backend
      const resp = await axios.post('/api/admin/assistant/documents', form, {
        headers: { Authorization: `Bearer ${token()}`, 'Content-Type': 'multipart/form-data' },
        onUploadProgress: (ev) => {
          if (!ev.total) return;
          const pct = Math.round((ev.loaded * 100) / ev.total);
          setUploadPct(pct);
        },
      });

      // Phase 2: backend is now extracting/chunking/embedding async.
      // Poll the document list until this document is READY or FAILED.
      const createdId = resp?.data?.id;
      if (!createdId) {
        setPhase('done');
        await loadDocs();
      } else {
        setPhase('indexing');
        await pollUntilReady(createdId);
      }

      setFile(null);
      setTitle('');
      await loadDocs();
    } catch (e) {
      setError('Yükleme başarısız: ' + (e?.response?.data?.message || e.message));
      setPhase('failed');
    } finally {
      setUploading(false);
      // Keep the final progress visible for a moment; auto-clear after 4s.
      setTimeout(() => { setPhase('idle'); setUploadPct(0); setPhaseDoc(null); }, 4000);
    }
  };

  // Poll the doc list every 1.5s until target doc is READY or FAILED (max 120s).
  const pollUntilReady = async (docId) => {
    const started = Date.now();
    const timeoutMs = 120_000;
    while (Date.now() - started < timeoutMs) {
      try {
        const resp = await axios.get('/api/admin/assistant/documents?size=100', authHeader());
        const list = Array.isArray(resp.data) ? resp.data : [];
        const target = list.find(d => d.id === docId);
        if (target) {
          setPhaseDoc(target);
          if (target.status === 'READY') { setPhase('done'); return; }
          if (target.status === 'FAILED') {
            setPhase('failed');
            setError('İndeksleme başarısız: ' + (target.errorMessage || 'bilinmeyen hata'));
            return;
          }
        }
      } catch { /* ignore transient errors during polling */ }
      await new Promise(r => setTimeout(r, 1500));
    }
    // Timed out — still show progress, list will refresh
    setPhase('done');
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
              {uploading ? 'İşleniyor…' : 'Yükle ve İndeksle'}
            </button>

            {/* Multi-phase progress indicator */}
            {phase !== 'idle' && (
              <div className="mt-3">
                <UploadProgress phase={phase} uploadPct={uploadPct} doc={phaseDoc} />
              </div>
            )}
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

/**
 * Two-phase progress bar:
 *   uploading → exact byte percentage (0-100)
 *   indexing  → indeterminate striped bar while backend extracts/chunks/embeds
 *   done      → green 100%, shows chunk count
 *   failed    → red bar with error
 */
function UploadProgress({ phase, uploadPct, doc }) {
  const cfg = {
    uploading: {
      label: `Dosya yükleniyor — ${uploadPct}%`,
      pct: uploadPct,
      color: 'bg-primary',
      striped: true,
      animated: true,
    },
    indexing: {
      label: doc
        ? `İndeksleniyor… (durum: ${doc.status}${doc.chunkCount ? `, ${doc.chunkCount} chunk` : ''})`
        : 'İndeksleniyor… (metin çıkarma → chunking → embedding)',
      pct: 100,
      color: 'bg-warning',
      striped: true,
      animated: true,
    },
    done: {
      label: doc
        ? `Tamamlandı — ${doc.chunkCount || 0} chunk oluşturuldu ve embed edildi.`
        : 'Tamamlandı.',
      pct: 100,
      color: 'bg-success',
      striped: false,
      animated: false,
    },
    failed: {
      label: 'Başarısız. Hata mesajını aşağıdan kontrol edin.',
      pct: 100,
      color: 'bg-danger',
      striped: false,
      animated: false,
    },
  }[phase] || { label: '', pct: 0, color: 'bg-secondary' };

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-1">
        <small className="text-muted">
          {phase === 'uploading' && <><i className="fas fa-upload me-1"></i></>}
          {phase === 'indexing' && <><i className="fas fa-cog fa-spin me-1"></i></>}
          {phase === 'done'     && <><i className="fas fa-check-circle text-success me-1"></i></>}
          {phase === 'failed'   && <><i className="fas fa-times-circle text-danger me-1"></i></>}
          {cfg.label}
        </small>
        {phase === 'uploading' && <small className="text-muted"><strong>{uploadPct}%</strong></small>}
      </div>
      <div className="progress" style={{ height: 10 }}>
        <div
          className={`progress-bar ${cfg.color}${cfg.striped ? ' progress-bar-striped' : ''}${cfg.animated ? ' progress-bar-animated' : ''}`}
          role="progressbar"
          style={{ width: `${cfg.pct}%`, transition: 'width .25s ease' }}
          aria-valuenow={cfg.pct} aria-valuemin="0" aria-valuemax="100"
        ></div>
      </div>
      {phase === 'indexing' && (
        <small className="text-muted d-block mt-1">
          Büyük dokümanlarda 10-60 saniye sürebilir. Bu pencereyi kapatmanız güvenli — işlem arka planda devam eder.
        </small>
      )}
    </div>
  );
}
