import React, { useEffect, useState } from 'react';
import axios from 'axios';

/**
 * RAG diagnostics — evaluate retrieval quality and verify that products /
 * documents have actually been embedded.
 *
 * Three panels:
 *   1. Coverage stats    → is everything embedded? dim sanity?
 *   2. Live retrieve     → run a query, see top-K hits with distances
 *   3. Bulk eval         → recall@K, MRR over a list of {query, expectedId}
 */
export default function AssistantDiagnosticsPage() {
  const [stats, setStats] = useState(null);
  const [statsLoading, setStatsLoading] = useState(false);

  // Retrieve panel
  const [query, setQuery] = useState('');
  const [kind, setKind] = useState('PRODUCT');
  const [scope, setScope] = useState('STORE');
  const [ignoreThreshold, setIgnoreThreshold] = useState(false);
  const [retrieveTopK, setRetrieveTopK] = useState(10);
  const [retrieveResult, setRetrieveResult] = useState(null);
  const [retrieveLoading, setRetrieveLoading] = useState(false);

  // Eval panel
  const [evalCasesText, setEvalCasesText] = useState('');
  const [topK, setTopK] = useState(10);
  const [evalKind, setEvalKind] = useState('PRODUCT');
  const [evalScope, setEvalScope] = useState('STORE');
  const [evalResult, setEvalResult] = useState(null);
  const [evalLoading, setEvalLoading] = useState(false);

  // Browse panel (embedded chunks / products)
  const [browseTab, setBrowseTab] = useState('CHUNKS'); // CHUNKS | PRODUCTS
  const [documents, setDocuments] = useState([]);
  const [chunkDocFilter, setChunkDocFilter] = useState('');
  const [chunkEmbeddedOnly, setChunkEmbeddedOnly] = useState(false);
  const [chunkPage, setChunkPage] = useState(0);
  const [chunkData, setChunkData] = useState(null);
  const [chunkLoading, setChunkLoading] = useState(false);
  const [expandedChunk, setExpandedChunk] = useState(null);

  const [productSearch, setProductSearch] = useState('');
  const [productMissing, setProductMissing] = useState(false);
  const [productPage, setProductPage] = useState(0);
  const [productData, setProductData] = useState(null);
  const [productLoading, setProductLoading] = useState(false);

  const loadStats = async () => {
    setStatsLoading(true);
    try {
      const r = await axios.get('/api/admin/assistant/diagnostics/stats');
      setStats(r.data);
    } catch (e) {
      setStats({ error: e?.response?.data?.message || e.message });
    } finally {
      setStatsLoading(false);
    }
  };

  useEffect(() => {
    loadStats();
  }, []);

  // Load documents list for filter dropdown
  useEffect(() => {
    axios
      .get('/api/admin/assistant/diagnostics/documents')
      .then((r) => setDocuments(r.data || []))
      .catch(() => setDocuments([]));
  }, []);

  const loadChunks = async (p = 0) => {
    setChunkLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', p);
      params.set('size', '20');
      if (chunkDocFilter) params.set('documentId', chunkDocFilter);
      if (chunkEmbeddedOnly) params.set('embeddedOnly', 'true');
      const r = await axios.get(`/api/admin/assistant/diagnostics/chunks?${params}`);
      setChunkData(r.data);
      setChunkPage(p);
    } catch (e) {
      setChunkData({ error: e?.response?.data?.message || e.message });
    } finally {
      setChunkLoading(false);
    }
  };

  const loadProducts = async (p = 0) => {
    setProductLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', p);
      params.set('size', '20');
      if (productSearch.trim()) params.set('search', productSearch.trim());
      if (productMissing) params.set('missing', 'true');
      const r = await axios.get(`/api/admin/assistant/diagnostics/products?${params}`);
      setProductData(r.data);
      setProductPage(p);
    } catch (e) {
      setProductData({ error: e?.response?.data?.message || e.message });
    } finally {
      setProductLoading(false);
    }
  };

  // Reload when filters change
  useEffect(() => {
    if (browseTab === 'CHUNKS') loadChunks(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [browseTab, chunkDocFilter, chunkEmbeddedOnly]);

  useEffect(() => {
    if (browseTab === 'PRODUCTS') loadProducts(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [browseTab, productMissing]);

  const runRetrieve = async (e) => {
    e?.preventDefault?.();
    if (!query.trim()) return;
    setRetrieveLoading(true);
    setRetrieveResult(null);
    try {
      const r = await axios.post('/api/admin/assistant/diagnostics/retrieve', {
        query: query.trim(),
        kind,
        scope,
        ignoreThreshold,
        topK: retrieveTopK,
      });
      setRetrieveResult(r.data);
    } catch (e) {
      setRetrieveResult({ error: e?.response?.data?.message || e.message });
    } finally {
      setRetrieveLoading(false);
    }
  };

  const runEval = async (e) => {
    e?.preventDefault?.();
    // Parse lines: "query<TAB>expectedId"  OR  "query=expectedId"
    const cases = evalCasesText
      .split('\n')
      .map((l) => l.trim())
      .filter(Boolean)
      .map((l) => {
        const m = l.match(/^(.+?)[\t=|]+\s*(\d+)\s*$/);
        if (!m) return null;
        return { query: m[1].trim(), expectedId: parseInt(m[2], 10) };
      })
      .filter(Boolean);
    if (cases.length === 0) {
      setEvalResult({ error: 'En az bir satır girin — format: "sorgu | expectedId"' });
      return;
    }
    setEvalLoading(true);
    setEvalResult(null);
    try {
      const r = await axios.post(`/api/admin/assistant/diagnostics/eval?topK=${topK}`, {
        kind: evalKind,
        scope: evalScope,
        cases,
      });
      setEvalResult(r.data);
    } catch (e) {
      setEvalResult({ error: e?.response?.data?.message || e.message });
    } finally {
      setEvalLoading(false);
    }
  };

  const colorForDistance = (d) => {
    if (d < 0.2) return 'success'; // very similar
    if (d < 0.35) return 'primary'; // good
    if (d < 0.5) return 'warning'; // borderline
    return 'danger'; // far
  };

  return (
    <div className="container-fluid">
      <div className="d-flex align-items-center mb-3 gap-2">
        <h2 className="mb-0">
          <i className="fas fa-microscope text-primary me-2"></i>RAG Tanılama
        </h2>
        <span className="text-muted small">
          Retrieval kalitesini değerlendir, embedding kapsamını doğrula.
        </span>
      </div>

      {/* ── 1. Coverage Stats ── */}
      <div className="card border-0 shadow-sm mb-4">
        <div className="card-header bg-primary bg-opacity-10 d-flex justify-content-between align-items-center">
          <strong>
            <i className="fas fa-chart-pie me-2"></i>Kapsam İstatistikleri
          </strong>
          <button className="btn btn-sm btn-outline-primary" onClick={loadStats} disabled={statsLoading}>
            <i className={`fas ${statsLoading ? 'fa-spinner fa-spin' : 'fa-sync-alt'} me-1`}></i>Yenile
          </button>
        </div>
        <div className="card-body">
          {!stats && statsLoading && <div className="text-muted">Yükleniyor…</div>}
          {stats?.error && <div className="alert alert-danger">{stats.error}</div>}
          {stats && !stats.error && (
            <div className="row g-3">
              <div className="col-md-6">
                <StatusRow label="RAG kullanılabilir mi?" ok={stats.ragAvailable} />
                <StatusRow label="Embedding servisi aktif mi?" ok={stats.embeddingServiceAvailable} />
                <div className="small text-muted mt-2">
                  Ürün embedding boyutu: <strong>{stats.embeddingDims?.product ?? '—'}</strong>, Chunk boyutu:{' '}
                  <strong>{stats.embeddingDims?.chunk ?? '—'}</strong>
                </div>
                {stats.embeddingDims?.product &&
                  stats.embeddingDims?.chunk &&
                  stats.embeddingDims.product !== stats.embeddingDims.chunk && (
                    <div className="alert alert-warning small mt-2 mb-0">
                      <i className="fas fa-exclamation-triangle me-1"></i>
                      Ürün ve chunk embedding boyutları farklı — büyük ihtimalle farklı modellerle
                      oluşturuldular. Reindeks gerekebilir.
                    </div>
                  )}
              </div>

              <div className="col-md-3">
                <h6 className="text-muted small mb-2">ÜRÜNLER</h6>
                <CoverageBar
                  label="Embed edilmiş"
                  value={stats.products?.embeddedActive}
                  total={stats.products?.activeTotal}
                />
                <div className="small text-muted">
                  Toplam aktif: {stats.products?.activeTotal} —
                  <span className={stats.products?.missing > 0 ? 'text-danger fw-bold' : 'text-success'}>
                    {' '}
                    eksik: {stats.products?.missing}
                  </span>
                </div>
              </div>

              <div className="col-md-3">
                <h6 className="text-muted small mb-2">DOKÜMANLAR</h6>
                <div className="small">
                  Toplam: <strong>{stats.documents?.total}</strong>
                </div>
                <div className="small">
                  Hazır: <span className="text-success">{stats.documents?.readyDocuments}</span>
                </div>
                <div className="small">
                  Başarısız: <span className="text-danger">{stats.documents?.failedDocuments}</span>
                </div>
                <div className="small mt-1">
                  Chunk: <strong>{stats.documents?.embeddedChunks}</strong>/{stats.documents?.totalChunks}
                </div>
                {stats.documents?.byStatus && (
                  <div className="d-flex flex-wrap gap-1 mt-2">
                    {Object.entries(stats.documents.byStatus).map(([k, v]) => (
                      <span key={k} className="badge bg-light text-dark border" style={{ fontSize: 10 }}>
                        {k}: {v}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* ── 2. Live Retrieve ── */}
      <div className="card border-0 shadow-sm mb-4">
        <div className="card-header bg-success bg-opacity-10">
          <strong>
            <i className="fas fa-search me-2"></i>Canlı Retrieval
          </strong>
          <span className="text-muted small ms-2">LLM araya girmeden top-K sonucu gör.</span>
        </div>
        <div className="card-body">
          <form onSubmit={runRetrieve} className="row g-2 align-items-end">
            <div className="col-md-5">
              <label className="form-label small text-muted mb-1">Sorgu</label>
              <input
                type="text"
                className="form-control"
                placeholder="örn: 3 kişilik deri kanepe"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
            </div>
            <div className="col-md-2">
              <label className="form-label small text-muted mb-1">Kaynak</label>
              <select className="form-select" value={kind} onChange={(e) => setKind(e.target.value)}>
                <option value="PRODUCT">Ürünler</option>
                <option value="DOCUMENT">Dokümanlar (FAQ)</option>
              </select>
            </div>
            {kind === 'DOCUMENT' && (
              <div className="col-md-2">
                <label className="form-label small text-muted mb-1">Scope</label>
                <select className="form-select" value={scope} onChange={(e) => setScope(e.target.value)}>
                  <option value="STORE">STORE</option>
                  <option value="WMS">WMS</option>
                  <option value="BOTH">BOTH</option>
                </select>
              </div>
            )}
            <div className="col-md-1">
              <label className="form-label small text-muted mb-1">Top-K</label>
              <input
                type="number"
                min="1"
                max="50"
                className="form-control"
                value={retrieveTopK}
                onChange={(e) => setRetrieveTopK(Math.min(50, Math.max(1, parseInt(e.target.value || 10))))}
              />
            </div>
            <div className="col-md-2">
              <button
                className="btn btn-primary w-100"
                disabled={retrieveLoading || !query.trim()}
                type="submit"
              >
                {retrieveLoading ? (
                  <>
                    <span className="spinner-border spinner-border-sm me-1" />
                    Aranıyor…
                  </>
                ) : (
                  <>
                    <i className="fas fa-play me-1"></i>Çalıştır
                  </>
                )}
              </button>
            </div>
            <div className="col-12">
              <div className="form-check form-switch">
                <input
                  type="checkbox"
                  className="form-check-input"
                  id="ignoreThreshold"
                  checked={ignoreThreshold}
                  onChange={(e) => setIgnoreThreshold(e.target.checked)}
                />
                <label className="form-check-label small" htmlFor="ignoreThreshold">
                  <strong>Eşiği yok say</strong> — benzerlik eşiğini atla, ham top-K'yı göster.
                  <span className="text-muted ms-1">
                    "Chunk var ama sonuç gelmiyor" sorununu teşhis için: hedef chunk kaçıncı sırada, mesafesi
                    ne? Eşiğin üstündeyse AI Ayarları → RAG → <code>vector-distance-threshold</code>'ü
                    yükseltin.
                  </span>
                </label>
              </div>
            </div>
          </form>

          {retrieveResult && (
            <div className="mt-3">
              {retrieveResult.error && <div className="alert alert-danger">{retrieveResult.error}</div>}
              {!retrieveResult.error && (
                <>
                  {retrieveResult.ignoreThreshold && (
                    <div className="alert alert-info py-2 mb-2 small">
                      <i className="fas fa-info-circle me-1"></i>
                      <strong>Eşik yok sayıldı</strong> — aşağıdaki sonuçlar normal RAG akışında LLM'ye
                      <em>iletilmez</em>. Eşik üstünde (<code>0.35</code>'in üstü varsayılan) kalan satırlar
                      kırmızı badge'li.
                    </div>
                  )}
                  <div className="small text-muted mb-2">
                    Embedding: <strong>{retrieveResult.embedding?.dims}</strong> boyut,
                    <span> {retrieveResult.embedding?.elapsedMs}ms</span>
                    {' · '}Arama: <strong>{retrieveResult.searchElapsedMs}ms</strong>
                    {' · '}
                    {retrieveResult.hitCount} sonuç
                    {retrieveResult.distanceSummary && (
                      <>
                        {' '}
                        · mesafe min/ort/maks:
                        <code className="ms-1">
                          {retrieveResult.distanceSummary.min} /{retrieveResult.distanceSummary.mean} /
                          {retrieveResult.distanceSummary.max}
                        </code>
                      </>
                    )}
                  </div>
                  {retrieveResult.hits?.length === 0 && (
                    <div className="alert alert-warning">
                      <i className="fas fa-exclamation-triangle me-1"></i>
                      Eşik altında sonuç yok. Bu sorgu için RAG bir şey dönmüyor — LLM'ye bağlam iletmiyor.
                      <div className="small mt-1">
                        İpucu: AI Ayarları → RAG → "Benzerlik eşiği"ni biraz arttırın (0.35 → 0.5).
                      </div>
                    </div>
                  )}
                  <div className="table-responsive">
                    <table className="table table-sm align-middle">
                      <thead>
                        <tr>
                          <th style={{ width: 40 }}>#</th>
                          <th>ID</th>
                          <th>Mesafe</th>
                          <th>Benzerlik</th>
                          <th>İçerik</th>
                        </tr>
                      </thead>
                      <tbody>
                        {retrieveResult.hits?.map((h, i) => (
                          <tr key={h.id}>
                            <td className="text-muted">{i + 1}</td>
                            <td>
                              <code>{h.id}</code>
                            </td>
                            <td>
                              <span className={`badge bg-${colorForDistance(h.distance)}`}>
                                {h.distance?.toFixed(4)}
                              </span>
                            </td>
                            <td className="small">{(h.similarity * 100)?.toFixed(1)}%</td>
                            <td className="small">{h.content}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </>
              )}
            </div>
          )}
        </div>
      </div>

      {/* ── 3. Bulk Eval ── */}
      <div className="card border-0 shadow-sm mb-4">
        <div className="card-header bg-warning bg-opacity-10">
          <strong>
            <i className="fas fa-tasks me-2"></i>Toplu Değerlendirme (Recall@K / MRR)
          </strong>
          <span className="text-muted small ms-2">Golden set ile retrieval kalitesini ölç.</span>
        </div>
        <div className="card-body">
          <form onSubmit={runEval}>
            <div className="row g-2 mb-3">
              <div className="col-md-2">
                <label className="form-label small text-muted mb-1">Kaynak</label>
                <select
                  className="form-select"
                  value={evalKind}
                  onChange={(e) => setEvalKind(e.target.value)}
                >
                  <option value="PRODUCT">Ürünler</option>
                  <option value="DOCUMENT">Dokümanlar</option>
                </select>
              </div>
              {evalKind === 'DOCUMENT' && (
                <div className="col-md-2">
                  <label className="form-label small text-muted mb-1">Scope</label>
                  <select
                    className="form-select"
                    value={evalScope}
                    onChange={(e) => setEvalScope(e.target.value)}
                  >
                    <option value="STORE">STORE</option>
                    <option value="WMS">WMS</option>
                    <option value="BOTH">BOTH</option>
                  </select>
                </div>
              )}
              <div className="col-md-2">
                <label className="form-label small text-muted mb-1">Top-K</label>
                <input
                  type="number"
                  min="1"
                  max="50"
                  className="form-control"
                  value={topK}
                  onChange={(e) => setTopK(Math.min(50, Math.max(1, parseInt(e.target.value || 10))))}
                />
              </div>
            </div>

            <label className="form-label small text-muted mb-1">
              Test senaryoları — her satır: <code>sorgu | expectedId</code>
            </label>
            <textarea
              className="form-control font-monospace"
              rows="8"
              placeholder={'3 kişilik deri kanepe | 124\nmikser küçük | 87\nLG buzdolabı | 42'}
              value={evalCasesText}
              onChange={(e) => setEvalCasesText(e.target.value)}
            />
            <div className="small text-muted mt-1">
              Format: <code>sorgu | ID</code> (ayırıcı <code>|</code>, <code>=</code> veya tab olabilir). Her
              satır bir test vakası.
            </div>

            <button
              className="btn btn-warning mt-3"
              type="submit"
              disabled={evalLoading || !evalCasesText.trim()}
            >
              {evalLoading ? (
                <>
                  <span className="spinner-border spinner-border-sm me-1" />
                  Çalışıyor…
                </>
              ) : (
                <>
                  <i className="fas fa-play me-1"></i>Değerlendir
                </>
              )}
            </button>
          </form>

          {evalResult && (
            <div className="mt-4">
              {evalResult.error && <div className="alert alert-danger">{evalResult.error}</div>}
              {evalResult.summary && (
                <>
                  <div className="row g-2 mb-3">
                    <ScoreBox label="Recall@1" value={evalResult.summary['recall@1']} />
                    <ScoreBox label="Recall@3" value={evalResult.summary['recall@3']} />
                    <ScoreBox label={`Recall@${topK}`} value={evalResult.summary[`recall@${topK}`]} />
                    <ScoreBox label="MRR" value={evalResult.summary.mrr} />
                  </div>
                  <div className="table-responsive">
                    <table className="table table-sm">
                      <thead>
                        <tr>
                          <th>Sorgu</th>
                          <th>Beklenen ID</th>
                          <th>Rank</th>
                          <th>Top ID'ler</th>
                        </tr>
                      </thead>
                      <tbody>
                        {evalResult.details?.map((d, i) => (
                          <tr
                            key={i}
                            className={d.rank === 1 ? 'table-success' : d.rank ? '' : 'table-danger'}
                          >
                            <td className="small">{d.query}</td>
                            <td>
                              <code>{d.expectedId}</code>
                            </td>
                            <td>
                              {d.rank ? (
                                <span
                                  className={`badge bg-${d.rank === 1 ? 'success' : d.rank <= 3 ? 'primary' : 'warning'}`}
                                >
                                  #{d.rank}
                                </span>
                              ) : (
                                <span className="badge bg-danger">Bulunamadı</span>
                              )}
                            </td>
                            <td className="small text-muted font-monospace">
                              [{d.topIds?.slice(0, 5).join(', ')}
                              {d.topIds?.length > 5 ? '…' : ''}]
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </>
              )}
            </div>
          )}
        </div>
      </div>

      {/* ── 4. Browse Embedded Content ── */}
      <div className="card border-0 shadow-sm mb-4">
        <div className="card-header bg-info bg-opacity-10 d-flex justify-content-between align-items-center">
          <strong>
            <i className="fas fa-database me-2"></i>Embed Edilmiş İçeriği Gözat
          </strong>
          <div className="btn-group btn-group-sm">
            <button
              type="button"
              className={`btn btn-outline-primary ${browseTab === 'CHUNKS' ? 'active' : ''}`}
              onClick={() => setBrowseTab('CHUNKS')}
            >
              <i className="fas fa-file-alt me-1"></i>Chunk'lar
            </button>
            <button
              type="button"
              className={`btn btn-outline-primary ${browseTab === 'PRODUCTS' ? 'active' : ''}`}
              onClick={() => setBrowseTab('PRODUCTS')}
            >
              <i className="fas fa-box me-1"></i>Ürünler
            </button>
          </div>
        </div>
        <div className="card-body">
          {/* CHUNKS TAB */}
          {browseTab === 'CHUNKS' && (
            <>
              <div className="row g-2 align-items-end mb-3">
                <div className="col-md-5">
                  <label className="form-label small text-muted mb-1">Doküman</label>
                  <select
                    className="form-select form-select-sm"
                    value={chunkDocFilter}
                    onChange={(e) => setChunkDocFilter(e.target.value)}
                  >
                    <option value="">Tüm dokümanlar</option>
                    {documents.map((d) => (
                      <option key={d.id} value={d.id}>
                        #{d.id} — {d.title} [{d.scope}] ({d.embeddedChunks}/{d.totalChunks} chunk
                        {d.status !== 'READY' ? ` · ${d.status}` : ''})
                      </option>
                    ))}
                  </select>
                </div>
                <div className="col-md-3">
                  <div className="form-check mt-4">
                    <input
                      type="checkbox"
                      className="form-check-input"
                      id="embeddedOnly"
                      checked={chunkEmbeddedOnly}
                      onChange={(e) => setChunkEmbeddedOnly(e.target.checked)}
                    />
                    <label className="form-check-label small" htmlFor="embeddedOnly">
                      Sadece embed edilmiş olanlar
                    </label>
                  </div>
                </div>
                <div className="col-md-2">
                  <button
                    className="btn btn-sm btn-outline-primary w-100"
                    onClick={() => loadChunks(chunkPage)}
                  >
                    <i className={`fas ${chunkLoading ? 'fa-spinner fa-spin' : 'fa-sync-alt'} me-1`}></i>
                    Yenile
                  </button>
                </div>
                <div className="col-md-2 text-end">
                  {chunkData && !chunkData.error && (
                    <span className="small text-muted">
                      Toplam: <strong>{chunkData.total}</strong>
                    </span>
                  )}
                </div>
              </div>

              {chunkData?.error && <div className="alert alert-danger">{chunkData.error}</div>}
              {chunkData && !chunkData.error && chunkData.chunks?.length === 0 && (
                <div className="text-center text-muted py-4">
                  <i className="fas fa-inbox fa-2x mb-2 opacity-25"></i>
                  <p className="mb-0">Chunk bulunamadı</p>
                </div>
              )}

              {chunkData?.chunks?.length > 0 && (
                <>
                  <div className="list-group">
                    {chunkData.chunks.map((c) => {
                      const isOpen = expandedChunk === c.id;
                      return (
                        <div key={c.id} className="list-group-item">
                          <div className="d-flex justify-content-between align-items-start gap-3">
                            <div className="flex-grow-1 min-w-0">
                              <div className="d-flex flex-wrap align-items-center gap-2 mb-1">
                                <code className="text-primary">#{c.id}</code>
                                <span className="badge bg-light text-dark border">
                                  Doc #{c.documentId}: {c.documentTitle}
                                </span>
                                <span className="badge bg-secondary bg-opacity-25 text-dark">
                                  chunk {c.chunkIndex}
                                </span>
                                <span className="badge bg-info bg-opacity-25 text-info-emphasis">
                                  {c.documentScope}
                                </span>
                                {c.hasEmbedding ? (
                                  <span className="badge bg-success">
                                    <i className="fas fa-check me-1"></i>
                                    {c.dims} dims
                                  </span>
                                ) : (
                                  <span className="badge bg-danger">
                                    <i className="fas fa-times me-1"></i>embed yok
                                  </span>
                                )}
                                <span className="text-muted small ms-auto">{c.content?.length} karakter</span>
                              </div>
                              <div
                                className="small"
                                style={{
                                  whiteSpace: isOpen ? 'pre-wrap' : 'nowrap',
                                  overflow: 'hidden',
                                  textOverflow: 'ellipsis',
                                  fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                                  fontSize: 12,
                                  background: '#f8f9fa',
                                  padding: '8px 10px',
                                  borderRadius: 4,
                                  border: '1px solid #e9ecef',
                                  maxHeight: isOpen ? 'none' : 44,
                                  cursor: 'pointer',
                                }}
                                onClick={() => setExpandedChunk(isOpen ? null : c.id)}
                              >
                                {c.content}
                              </div>
                            </div>
                            <button
                              className="btn btn-sm btn-link p-0 text-nowrap"
                              onClick={() => setExpandedChunk(isOpen ? null : c.id)}
                            >
                              {isOpen ? 'Küçült' : 'Genişlet'}
                            </button>
                          </div>
                        </div>
                      );
                    })}
                  </div>

                  {/* Pagination */}
                  <div className="d-flex justify-content-between align-items-center mt-3">
                    <span className="small text-muted">
                      Sayfa {chunkPage + 1} / {Math.max(1, Math.ceil(chunkData.total / chunkData.size))}
                    </span>
                    <div className="btn-group btn-group-sm">
                      <button
                        className="btn btn-outline-primary"
                        disabled={chunkPage === 0}
                        onClick={() => loadChunks(chunkPage - 1)}
                      >
                        <i className="fas fa-chevron-left"></i> Önceki
                      </button>
                      <button
                        className="btn btn-outline-primary"
                        disabled={(chunkPage + 1) * chunkData.size >= chunkData.total}
                        onClick={() => loadChunks(chunkPage + 1)}
                      >
                        Sonraki <i className="fas fa-chevron-right"></i>
                      </button>
                    </div>
                  </div>
                </>
              )}
            </>
          )}

          {/* PRODUCTS TAB */}
          {browseTab === 'PRODUCTS' && (
            <>
              <div className="row g-2 align-items-end mb-3">
                <div className="col-md-5">
                  <label className="form-label small text-muted mb-1">Ürün ara (isim / SKU)</label>
                  <div className="input-group input-group-sm">
                    <input
                      type="text"
                      className="form-control"
                      value={productSearch}
                      onChange={(e) => setProductSearch(e.target.value)}
                      onKeyDown={(e) => e.key === 'Enter' && loadProducts(0)}
                      placeholder="örn: kanepe, ABC-12..."
                    />
                    <button
                      className="btn btn-outline-primary"
                      onClick={() => loadProducts(0)}
                      disabled={productLoading}
                    >
                      <i className={`fas ${productLoading ? 'fa-spinner fa-spin' : 'fa-search'}`}></i>
                    </button>
                  </div>
                </div>
                <div className="col-md-4">
                  <div className="btn-group btn-group-sm w-100">
                    <input
                      type="radio"
                      className="btn-check"
                      id="prod-embedded"
                      name="prodmode"
                      checked={!productMissing}
                      onChange={() => setProductMissing(false)}
                    />
                    <label className="btn btn-outline-success" htmlFor="prod-embedded">
                      <i className="fas fa-check me-1"></i>Embed Edilmiş
                    </label>
                    <input
                      type="radio"
                      className="btn-check"
                      id="prod-missing"
                      name="prodmode"
                      checked={productMissing}
                      onChange={() => setProductMissing(true)}
                    />
                    <label className="btn btn-outline-danger" htmlFor="prod-missing">
                      <i className="fas fa-times me-1"></i>Eksik
                    </label>
                  </div>
                </div>
                <div className="col-md-3 text-end">
                  {productData && !productData.error && (
                    <span className="small text-muted">
                      Toplam: <strong>{productData.total}</strong>
                    </span>
                  )}
                </div>
              </div>

              {productData?.error && <div className="alert alert-danger">{productData.error}</div>}
              {productData?.products?.length === 0 && (
                <div className="text-center text-muted py-4">
                  <i className="fas fa-inbox fa-2x mb-2 opacity-25"></i>
                  <p className="mb-0">
                    {productMissing ? 'Tüm aktif ürünler embed edilmiş 👍' : 'Ürün bulunamadı'}
                  </p>
                </div>
              )}

              {productData?.products?.length > 0 && (
                <>
                  <div className="table-responsive">
                    <table className="table table-sm align-middle">
                      <thead>
                        <tr>
                          <th>ID</th>
                          <th>SKU</th>
                          <th>İsim</th>
                          <th>Hash</th>
                          <th>Güncellenme</th>
                          <th>Durum</th>
                        </tr>
                      </thead>
                      <tbody>
                        {productData.products.map((p) => (
                          <tr key={p.id}>
                            <td>
                              <code>{p.id}</code>
                            </td>
                            <td>
                              <code className="small">{p.sku || '—'}</code>
                            </td>
                            <td className="small">{p.name}</td>
                            <td className="small text-muted font-monospace">
                              {p.contentHash ? p.contentHash.substring(0, 12) + '…' : '—'}
                            </td>
                            <td className="small text-muted">
                              {p.updatedAt ? new Date(p.updatedAt).toLocaleString('tr-TR') : '—'}
                            </td>
                            <td>
                              {p.hasEmbedding ? (
                                <span className="badge bg-success">
                                  <i className="fas fa-check"></i>
                                </span>
                              ) : (
                                <span className="badge bg-danger">EKSİK</span>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>

                  {/* Pagination */}
                  <div className="d-flex justify-content-between align-items-center mt-3">
                    <span className="small text-muted">
                      Sayfa {productPage + 1} / {Math.max(1, Math.ceil(productData.total / productData.size))}
                    </span>
                    <div className="btn-group btn-group-sm">
                      <button
                        className="btn btn-outline-primary"
                        disabled={productPage === 0}
                        onClick={() => loadProducts(productPage - 1)}
                      >
                        <i className="fas fa-chevron-left"></i> Önceki
                      </button>
                      <button
                        className="btn btn-outline-primary"
                        disabled={(productPage + 1) * productData.size >= productData.total}
                        onClick={() => loadProducts(productPage + 1)}
                      >
                        Sonraki <i className="fas fa-chevron-right"></i>
                      </button>
                    </div>
                  </div>
                </>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function StatusRow({ label, ok }) {
  return (
    <div className="d-flex align-items-center gap-2 mb-1">
      <i className={`fas ${ok ? 'fa-check-circle text-success' : 'fa-times-circle text-danger'}`}></i>
      <span>{label}</span>
      <span
        className={`badge bg-${ok ? 'success' : 'danger'} bg-opacity-25 text-${ok ? 'success' : 'danger'}-emphasis ms-auto`}
      >
        {ok ? 'OK' : 'KAPALI'}
      </span>
    </div>
  );
}

function CoverageBar({ label, value, total }) {
  const pct = total > 0 ? Math.round((value / total) * 100) : 0;
  const color = pct >= 99 ? 'success' : pct >= 80 ? 'primary' : pct >= 50 ? 'warning' : 'danger';
  return (
    <div className="mb-2">
      <div className="d-flex justify-content-between small">
        <span>{label}</span>
        <strong>
          {value} / {total} ({pct}%)
        </strong>
      </div>
      <div className="progress" style={{ height: 6 }}>
        <div className={`progress-bar bg-${color}`} style={{ width: `${pct}%` }}></div>
      </div>
    </div>
  );
}

function ScoreBox({ label, value }) {
  const pct = Math.round((value || 0) * 100);
  const color = pct >= 80 ? 'success' : pct >= 50 ? 'primary' : pct >= 30 ? 'warning' : 'danger';
  return (
    <div className="col-md-3">
      <div className="card border-0 shadow-sm">
        <div className="card-body text-center py-2">
          <div className="text-muted small">{label}</div>
          <div className={`fs-3 fw-bold text-${color}`}>{pct}%</div>
          <div className="text-muted small">{value?.toFixed?.(4) ?? value}</div>
        </div>
      </div>
    </div>
  );
}
