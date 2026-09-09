import React, { useCallback, useEffect, useRef, useState } from 'react';
import axios from 'axios';

/**
 * Bulk supplier import: paste a list of product page links, let the server crawl and
 * match each one against the catalogue, confirm the matches, then write photos and copy
 * in one pass.
 *
 * The match phase is a server-side job rather than a request/response, because a link
 * cannot be matched until its page has been read and the fetches are paced. So this
 * starts a job and polls it — see ProductCrawlBatchService.
 */

const POLL_INTERVAL_MS = 1200;
const MAX_URLS = 50;
const MAX_MESSAGE_CHARS = 240;

/**
 * Anything shown to the admin passes through here first.
 *
 * A failing supplier — or our own proxy — can answer with a whole HTML error page, and
 * axios hands that body straight to the error handler. Rendering it dumped a wall of
 * "<!DOCTYPE html>… nginx" markup into the alert box. Tags are stripped, whitespace
 * collapsed and the result capped, so a message stays a sentence no matter what the
 * far end sent.
 */
function cleanMessage(value, fallback) {
  if (value == null) return fallback;
  const text = String(value)
    .replace(/<[^>]*>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  if (!text) return fallback;
  return text.length > MAX_MESSAGE_CHARS ? `${text.slice(0, MAX_MESSAGE_CHARS)}…` : text;
}

/** Pulls a readable sentence out of an axios failure, whatever shape it arrived in. */
function errorText(e, fallback) {
  return cleanMessage(e?.response?.data?.message ?? e?.message, fallback);
}

const STEP = {
  INPUT: 'input',
  MATCHING: 'matching',
  REVIEW: 'review',
  APPLYING: 'applying',
  DONE: 'done',
};

export default function BulkCrawlModal({ open, onClose, onApplied }) {
  const [step, setStep] = useState(STEP.INPUT);
  const [text, setText] = useState('');
  const [error, setError] = useState('');
  const [job, setJob] = useState(null);
  // url -> productId (null means "do not import this row")
  const [choices, setChoices] = useState({});
  const [summary, setSummary] = useState(null);
  const [hosts, setHosts] = useState([]);

  const pollTimer = useRef(null);
  const jobIdRef = useRef(null);

  const stopPolling = useCallback(() => {
    if (pollTimer.current) {
      clearTimeout(pollTimer.current);
      pollTimer.current = null;
    }
  }, []);

  // Polling is the only thing here that outlives a render, so it has to be torn down on
  // unmount as well as on close — otherwise closing mid-crawl leaves a timer setting
  // state on a component that no longer exists.
  useEffect(() => stopPolling, [stopPolling]);

  // The supported supplier list is shown once, next to the box the links go into. It
  // used to arrive as part of every rejected row's error text, which buried the actual
  // reason under twenty-five domains of red.
  useEffect(() => {
    if (!open || hosts.length > 0) return;
    let cancelled = false;
    axios
      .get('/api/admin/products/crawl-images/supported-hosts')
      .then((res) => {
        if (!cancelled) setHosts(res.data?.hosts || []);
      })
      .catch(() => {
        /* The list is a convenience; the server rejects unsupported hosts regardless. */
      });
    return () => {
      cancelled = true;
    };
  }, [open, hosts.length]);

  const reset = useCallback(() => {
    stopPolling();
    jobIdRef.current = null;
    setStep(STEP.INPUT);
    setText('');
    setError('');
    setJob(null);
    setChoices({});
    setSummary(null);
  }, [stopPolling]);

  const close = () => {
    reset();
    onClose();
  };

  const poll = useCallback(async () => {
    const jobId = jobIdRef.current;
    if (!jobId) return;
    try {
      const res = await axios.get(`/api/admin/products/crawl-images/batch/${jobId}`);
      setJob(res.data);
      if (res.data.state === 'RUNNING') {
        pollTimer.current = setTimeout(poll, POLL_INTERVAL_MS);
        return;
      }
      if (res.data.state === 'FAILED') {
        setError(cleanMessage(res.data.error, 'Toplu işlem başarısız oldu.'));
        setStep(STEP.INPUT);
        return;
      }
      // Pre-tick every row the server matched; a row with no match starts unticked and
      // cannot be imported until the admin picks a product for it.
      const picked = {};
      (res.data.items || []).forEach((item) => {
        picked[item.url] = item.productId ?? null;
      });
      setChoices(picked);
      setStep(STEP.REVIEW);
    } catch (e) {
      setError(errorText(e, 'Durum alınamadı.'));
      setStep(STEP.INPUT);
    }
  }, []);

  const startMatch = async () => {
    setError('');
    if (!text.trim()) {
      setError('Önce bağlantıları yapıştırın.');
      return;
    }
    setStep(STEP.MATCHING);
    setJob(null);
    try {
      const res = await axios.post('/api/admin/products/crawl-images/batch/match', {
        urls: [text],
      });
      jobIdRef.current = res.data.jobId;
      poll();
    } catch (e) {
      setError(errorText(e, 'Toplu işlem başlatılamadı.'));
      setStep(STEP.INPUT);
    }
  };

  const applySelected = async () => {
    const items = Object.entries(choices)
      .filter(([, productId]) => productId)
      .map(([url, productId]) => ({ url, productId }));
    if (items.length === 0) {
      setError('Aktarılacak satır seçilmedi.');
      return;
    }
    setError('');
    setStep(STEP.APPLYING);
    try {
      const res = await axios.post(`/api/admin/products/crawl-images/batch/${jobIdRef.current}/apply`, {
        items,
      });
      setSummary(res.data);
      setStep(STEP.DONE);
      if (onApplied) onApplied();
    } catch (e) {
      setError(errorText(e, 'Aktarım başarısız oldu.'));
      setStep(STEP.REVIEW);
    }
  };

  if (!open) return null;

  const items = job?.items || [];
  const selectedCount = Object.values(choices).filter(Boolean).length;
  const linkCount = (text.match(/https?:\/\//g) || []).length;

  return (
    <>
      <div className="modal-backdrop fade show" style={{ zIndex: 1050 }} />
      <div
        className="modal fade show d-block"
        style={{ zIndex: 1055 }}
        role="dialog"
        aria-modal="true"
        aria-label="Toplu link ile detay aktarma"
      >
        <div className="modal-dialog modal-xl modal-dialog-centered modal-dialog-scrollable">
          <div className="modal-content border-0 shadow-lg" style={{ borderRadius: 16 }}>
            <div className="modal-header border-0 pb-0">
              <div>
                <h5 className="modal-title fw-bold mb-1">
                  <i className="fas fa-link text-primary me-2" />
                  Toplu Link ile Detay Aktarma
                </h5>
                <p className="text-muted small mb-0">
                  Tedarikçi ürün sayfalarını yapıştırın; sistem her birini okuyup ürünlerinizle eşleştirsin,
                  onayınızdan sonra fotoğraf ve açıklamaları aktarsın.
                </p>
              </div>
              <button type="button" className="btn-close" onClick={close} aria-label="Kapat" />
            </div>

            <div className="modal-body">
              <StepBar step={step} />

              {error && (
                <div className="alert alert-danger d-flex align-items-center gap-2 py-2">
                  <i className="fas fa-exclamation-circle" />
                  <span className="small">{error}</span>
                </div>
              )}

              {step === STEP.INPUT && (
                <>
                  <label className="form-label small fw-semibold">Ürün sayfası bağlantıları</label>
                  <textarea
                    className="form-control font-monospace"
                    rows={9}
                    value={text}
                    onChange={(e) => setText(e.target.value)}
                    placeholder={
                      'Her satıra bir bağlantı yapıştırın:\n' +
                      'https://www.profilo.com/tr/tr/product/42PA300E\n' +
                      'https://www.hoover-home.com/tr_TR/bulasik-makineleri/32002504/hf-3e53e0w-17/'
                    }
                    style={{ fontSize: 13 }}
                  />
                  <div className="d-flex justify-content-between align-items-center mt-2">
                    <span className="text-muted small">
                      {linkCount > 0
                        ? `${linkCount} bağlantı algılandı`
                        : 'Satır, virgül veya boşlukla ayırabilirsiniz'}
                    </span>
                    <span
                      className={`small ${linkCount > MAX_URLS ? 'text-danger fw-semibold' : 'text-muted'}`}
                    >
                      En fazla {MAX_URLS}
                    </span>
                  </div>
                  {hosts.length > 0 && (
                    <details className="mt-3">
                      <summary className="small text-muted" style={{ cursor: 'pointer' }}>
                        Desteklenen siteler ({hosts.length})
                      </summary>
                      <div className="d-flex flex-wrap gap-1 mt-2">
                        {hosts.map((h) => (
                          <span key={h} className="badge bg-light text-dark border fw-normal">
                            {h}
                          </span>
                        ))}
                      </div>
                      <p className="text-muted mt-2 mb-0" style={{ fontSize: 11 }}>
                        Listede olmayan siteler otomatik okunamıyor; bu sayfaların detayını ürün ekranından
                        elle girmeniz gerekir.
                      </p>
                    </details>
                  )}
                </>
              )}

              {step === STEP.MATCHING && <Progress job={job} />}

              {(step === STEP.REVIEW || step === STEP.APPLYING) && (
                <ReviewTable
                  items={items}
                  choices={choices}
                  disabled={step === STEP.APPLYING}
                  onChange={(url, productId) => setChoices((prev) => ({ ...prev, [url]: productId }))}
                />
              )}

              {step === STEP.APPLYING && (
                <div className="d-flex align-items-center gap-2 text-muted small mt-3">
                  <span className="spinner-border spinner-border-sm" />
                  Fotoğraflar indiriliyor ve ürünlere yazılıyor…
                </div>
              )}

              {step === STEP.DONE && summary && <Summary summary={summary} />}
            </div>

            <div className="modal-footer border-0 pt-0">
              {step === STEP.INPUT && (
                <>
                  <button className="btn btn-light" onClick={close}>
                    Vazgeç
                  </button>
                  <button
                    className="btn btn-primary"
                    onClick={startMatch}
                    disabled={linkCount === 0 || linkCount > MAX_URLS}
                  >
                    <i className="fas fa-magnifying-glass me-2" />
                    Eşleştir
                  </button>
                </>
              )}

              {step === STEP.MATCHING && (
                <button className="btn btn-light" onClick={close}>
                  Arka planda bırak ve kapat
                </button>
              )}

              {step === STEP.REVIEW && (
                <>
                  <button className="btn btn-light" onClick={reset}>
                    Yeni liste
                  </button>
                  <button className="btn btn-success" onClick={applySelected} disabled={selectedCount === 0}>
                    <i className="fas fa-download me-2" />
                    {selectedCount} ürüne aktar
                  </button>
                </>
              )}

              {step === STEP.DONE && (
                <>
                  <button className="btn btn-light" onClick={reset}>
                    Yeni liste
                  </button>
                  <button className="btn btn-primary" onClick={close}>
                    Kapat
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}

/**
 * Row thumbnail. The image comes through our proxy because supplier CDNs check the
 * Referer, and it can still fail — a hotlink block, an expired signed URL, a page whose
 * first "image" was never really one. Without a fallback the browser painted its own
 * broken-image glyph, which reads as a bug in the table rather than one missing photo.
 */
function Thumb({ url, referer }) {
  const [broken, setBroken] = useState(false);
  const box = {
    width: 44,
    height: 44,
    objectFit: 'contain',
    background: '#f8fafc',
    borderRadius: 8,
  };
  if (!url || broken) {
    return (
      <div
        className="d-flex align-items-center justify-content-center text-muted"
        style={box}
        title={broken ? 'Görsel önizlenemedi' : 'Görsel yok'}
      >
        <i className="fas fa-image" />
      </div>
    );
  }
  return (
    <img
      src={`/api/admin/products/crawl-images/proxy?url=${encodeURIComponent(
        url
      )}&referer=${encodeURIComponent(referer)}`}
      alt=""
      style={box}
      onError={() => setBroken(true)}
    />
  );
}

function StepBar({ step }) {
  const steps = [
    { key: STEP.INPUT, label: 'Bağlantılar' },
    { key: STEP.MATCHING, label: 'Eşleştirme' },
    { key: STEP.REVIEW, label: 'Onay' },
    { key: STEP.DONE, label: 'Sonuç' },
  ];
  const order = [STEP.INPUT, STEP.MATCHING, STEP.REVIEW, STEP.APPLYING, STEP.DONE];
  const current = order.indexOf(step);
  return (
    <div className="d-flex align-items-center gap-2 mb-3 flex-wrap">
      {steps.map((s, i) => {
        const idx = order.indexOf(s.key);
        const done = current > idx;
        const active = current === idx || (s.key === STEP.REVIEW && step === STEP.APPLYING);
        return (
          <React.Fragment key={s.key}>
            <span
              className={`badge rounded-pill px-3 py-2 ${
                active ? 'bg-primary' : done ? 'bg-success' : 'bg-light text-muted'
              }`}
            >
              {done && <i className="fas fa-check me-1" />}
              {s.label}
            </span>
            {i < steps.length - 1 && <span className="text-muted small">→</span>}
          </React.Fragment>
        );
      })}
    </div>
  );
}

function Progress({ job }) {
  const total = job?.total || 0;
  const processed = job?.processed || 0;
  const pct = total ? Math.round((processed / total) * 100) : 0;
  return (
    <div className="py-3">
      <div className="d-flex justify-content-between small mb-2">
        <span className="fw-semibold">Sayfalar okunuyor…</span>
        <span className="text-muted">
          {processed} / {total}
        </span>
      </div>
      <div className="progress" style={{ height: 10, borderRadius: 999 }}>
        <div
          className="progress-bar progress-bar-striped progress-bar-animated"
          style={{ width: `${pct}%` }}
        />
      </div>
      <p className="text-muted small mt-3 mb-0">
        Tedarikçi sitelerini yormamak için istekler aralıklı gönderiliyor; uzun listeler birkaç dakika
        sürebilir.
      </p>
    </div>
  );
}

function ReviewTable({ items, choices, disabled, onChange }) {
  if (items.length === 0) {
    return <p className="text-muted small mb-0">Sonuç yok.</p>;
  }
  return (
    <div className="table-responsive">
      <table className="table table-sm align-middle">
        <thead className="table-light">
          <tr>
            <th style={{ width: 40 }} />
            <th style={{ width: 60 }}>Görsel</th>
            <th>Kaynak sayfa</th>
            <th style={{ width: 260 }}>Eşleşen ürün</th>
            <th style={{ width: 150 }}>Aktarılacak</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => {
            const failed = item.status === 'ERROR';
            const chosen = choices[item.url] ?? '';
            return (
              <tr key={item.url} className={failed ? 'table-danger' : undefined}>
                <td className="text-center">
                  <input
                    type="checkbox"
                    className="form-check-input"
                    checked={Boolean(chosen)}
                    disabled={disabled || failed || (item.candidates || []).length === 0}
                    onChange={(e) =>
                      onChange(item.url, e.target.checked ? (item.candidates?.[0]?.productId ?? null) : null)
                    }
                    aria-label="Bu satırı aktar"
                  />
                </td>
                <td>
                  <Thumb url={item.thumbnail} referer={item.url} />
                </td>
                <td style={{ minWidth: 220 }}>
                  <div className="fw-semibold small text-truncate" style={{ maxWidth: 320 }}>
                    {item.title || '(başlık okunamadı)'}
                  </div>
                  <a
                    href={item.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-muted text-decoration-none d-block text-truncate"
                    style={{ fontSize: 11, maxWidth: 320 }}
                  >
                    {item.url}
                  </a>
                  {item.message && (
                    <div className={`small ${failed ? 'text-danger' : 'text-warning'}`}>
                      {cleanMessage(item.message, '')}
                    </div>
                  )}
                </td>
                <td>
                  {(item.candidates || []).length === 0 ? (
                    <span className="text-muted small">—</span>
                  ) : (
                    <select
                      className="form-select form-select-sm"
                      value={chosen}
                      disabled={disabled}
                      onChange={(e) => onChange(item.url, e.target.value ? Number(e.target.value) : null)}
                    >
                      <option value="">Aktarma</option>
                      {item.candidates.map((c) => (
                        <option key={c.productId} value={c.productId}>
                          {c.productName} · {c.sku} ({c.reason})
                        </option>
                      ))}
                    </select>
                  )}
                </td>
                <td>
                  <div className="d-flex flex-wrap gap-1">
                    <span className="badge bg-primary-subtle text-primary-emphasis">
                      {item.imageCount} foto
                    </span>
                    {item.hasDescription && (
                      <span className="badge bg-success-subtle text-success-emphasis">açıklama</span>
                    )}
                    {item.specGroupCount > 0 && (
                      <span className="badge bg-secondary-subtle text-secondary-emphasis">
                        {item.specGroupCount} özellik grubu
                      </span>
                    )}
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function Summary({ summary }) {
  return (
    <div className="py-2">
      <div className="d-flex gap-3 flex-wrap mb-3">
        <div className="flex-grow-1 border rounded-3 p-3 text-center">
          <div className="fs-4 fw-bold text-success">{summary.applied}</div>
          <div className="text-muted small">ürün güncellendi</div>
        </div>
        <div className="flex-grow-1 border rounded-3 p-3 text-center">
          <div className="fs-4 fw-bold text-primary">{summary.photos}</div>
          <div className="text-muted small">fotoğraf aktarıldı</div>
        </div>
      </div>
      {summary.errors?.length > 0 && (
        <div className="alert alert-warning py-2">
          <div className="fw-semibold small mb-1">{summary.errors.length} satırda sorun oldu:</div>
          <ul className="mb-0 small">
            {summary.errors.slice(0, 10).map((e, i) => (
              <li key={i}>{e}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
