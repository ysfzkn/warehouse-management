import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import axios from 'axios';
import WarehouseStockPicker from './WarehouseStockPicker';
import ConfirmModal from './ConfirmModal';

/**
 * Depo çıkış makbuzunun deposunu ve kalemlerini düzeltme.
 *
 * İki adımlı, bilerek: kaydetmek geçmişe dönük stok hareketi yaratıyor, bu yüzden form
 * doğrudan sunucuya gitmiyor. Önce "Gözden Geçir" adımı, her kalemin stoğa etkisini tek tek
 * yazıyor — "2 adet Merkez Depo'ya geri döner", "3 adet Bor Depo'dan düşer". Kullanıcının
 * onayladığı şey bir "Emin misiniz?" değil, göreceği stok hareketinin kendisi.
 *
 * Kullanılabilir adet hesabında bu çıkışın kendi adedi geri sayılıyor: mal stoktan zaten
 * düşmüş (ya da rezervede), 5'ten 6'ya çıkarırken raftaki sıfır değil, düşülen beş de
 * hesaba katılmalı. Bu kredi yalnızca orijinal depoda geçerli.
 */

const lineKey = () => `n-${Math.random().toString(36).slice(2, 10)}`;

const availableOf = (stock) =>
  Math.max(0, (stock?.quantity || 0) - (stock?.reservedQuantity || 0) - (stock?.consignedQuantity || 0));

/** Sevkiyatın kalemlerini formun satırlarına çevirir; eski tek ürünlü kayıtları da kapsar. */
const initialLines = (transfer) => {
  const items =
    Array.isArray(transfer?.items) && transfer.items.length > 0
      ? transfer.items
      : transfer?.product
        ? [{ id: 'legacy', stockId: null, product: transfer.product, quantity: transfer.quantity }]
        : [];
  return items.map((item) => ({
    key: `o-${item.id}`,
    original: true,
    originalStockId: item.stockId ?? null,
    stockId: item.stockId ?? null,
    productId: item.product?.id,
    name: item.product?.name || '-',
    sku: item.product?.sku || '',
    originalQuantity: item.quantity || 0,
    quantity: item.quantity || 0,
    removed: false,
    missing: false,
  }));
};

function ContentsEditForm({ transfer, receipt, onClose, onSaved }) {
  const originalWarehouseId = String(transfer?.sourceWarehouse?.id || '');
  const originalWarehouseName = transfer?.sourceWarehouse?.name || '-';
  const reservedFlow = transfer?.status === 'IN_TRANSIT';

  const [warehouses, setWarehouses] = useState([]);
  const [warehouseId, setWarehouseId] = useState(originalWarehouseId);
  const [stocks, setStocks] = useState([]);
  // Listenin hangi depoya ait olduğu. Depo seçimi değiştiği an liste hâlâ eski deponun;
  // eşleme bu alana bakıyor ki satırlar bir anlığına yanlış depoya eşlenmesin.
  const [stocksFor, setStocksFor] = useState(null);
  const [stocksLoading, setStocksLoading] = useState(true);
  const [stocksError, setStocksError] = useState('');
  const [lines, setLines] = useState(() => initialLines(transfer));
  // Orijinal depodaki stok satırı → bu çıkışın o satırdan aldığı adet. Satır kimliği
  // eski kayıtlarda boş olabildiği için stoklar ilk geldiğinde ürün üzerinden çözülüyor.
  const [credit, setCredit] = useState(null);
  const [reason, setReason] = useState('');
  const [reasonTouched, setReasonTouched] = useState(false);
  const [step, setStep] = useState('edit');
  const [acknowledged, setAcknowledged] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [confirmDiscard, setConfirmDiscard] = useState(false);
  const [remapNotice, setRemapNotice] = useState('');
  const dialogRef = useRef(null);
  const errorRef = useRef(null);

  useEffect(() => {
    (async () => {
      try {
        const res = await axios.get('/api/warehouses');
        setWarehouses(Array.isArray(res.data) ? res.data : []);
      } catch {
        setError('Depolar yüklenemedi.');
      }
    })();
  }, []);

  const loadStocks = useCallback(async (id) => {
    if (!id) {
      setStocks([]);
      setStocksLoading(false);
      return;
    }
    setStocksLoading(true);
    setStocksError('');
    try {
      const res = await axios.get(`/api/stocks/warehouse/${id}`);
      setStocks(Array.isArray(res.data) ? res.data : []);
      setStocksFor(String(id));
    } catch (e) {
      setStocks([]);
      setStocksFor(null);
      setStocksError(e?.response?.data?.message || 'Depo stokları yüklenemedi.');
    } finally {
      setStocksLoading(false);
    }
  }, []);

  useEffect(() => {
    loadStocks(warehouseId);
  }, [warehouseId, loadStocks]);

  // Stoklar her geldiğinde satırlar bu depoya eşleniyor: önce aynı stok satırı, yoksa aynı
  // ürün. Depo değişince adetler korunuyor; bulunamayan ürün işaretleniyor ki kullanıcı
  // sessizce kaybolan bir kalemle karşılaşmasın.
  useEffect(() => {
    if (stocksFor === null) return;
    const byId = new Map(stocks.map((s) => [String(s.id), s]));
    const byProduct = new Map();
    stocks.forEach((s) => {
      const pid = String(s.product?.id);
      if (!byProduct.has(pid)) byProduct.set(pid, s);
    });
    const resolve = (line, preferredStockId) =>
      (preferredStockId != null && byId.get(String(preferredStockId))) ||
      byProduct.get(String(line.productId));
    const inOriginal = stocksFor === originalWarehouseId;

    setLines((prev) => {
      if (inOriginal) {
        // Kredi bir kez, orijinal deponun stokları ilk geldiğinde kuruluyor.
        setCredit((current) => {
          if (current !== null) return current;
          const map = new Map();
          prev.forEach((line) => {
            if (!line.original) return;
            const row = resolve(line, line.originalStockId);
            if (row) map.set(String(row.id), (map.get(String(row.id)) || 0) + line.originalQuantity);
          });
          return map;
        });
      }
      const next = prev.map((line) => {
        const preferred = inOriginal && line.original ? (line.originalStockId ?? line.stockId) : line.stockId;
        const row = resolve(line, preferred);
        return { ...line, stockId: row ? row.id : null, missing: !row };
      });
      const missingCount = next.filter((line) => line.missing && !line.removed).length;
      setRemapNotice(
        !inOriginal && missingCount > 0
          ? `${missingCount} ürün bu depoda bulunmuyor. Kaldırın ya da orijinal depoya dönün.`
          : ''
      );
      return next;
    });
  }, [stocks, stocksFor, originalWarehouseId]);

  const stockById = useMemo(() => new Map(stocks.map((s) => [String(s.id), s])), [stocks]);
  const inOriginalWarehouse = String(warehouseId) === originalWarehouseId;
  const stocksReady = !stocksLoading && stocksFor === String(warehouseId);

  /** Bu satırdan en fazla kaç adet çıkılabilir — kendi eski adedi dahil, diğer satırlar hariç. */
  const maxFor = useCallback(
    (stockId, excludeKey) => {
      const row = stockById.get(String(stockId));
      if (!row) return 0;
      const own = stocksFor === originalWarehouseId && credit ? credit.get(String(stockId)) || 0 : 0;
      const others = lines.reduce(
        (sum, line) =>
          line.key !== excludeKey && !line.removed && String(line.stockId) === String(stockId)
            ? sum + (Number(line.quantity) || 0)
            : sum,
        0
      );
      return Math.max(0, availableOf(row) + own - others);
    },
    [stockById, stocksFor, originalWarehouseId, credit, lines]
  );

  const activeLines = lines.filter((line) => !line.removed);

  const lineError = (line) => {
    // Depo stokları yüklenirken eski deponun listesine göre sınır göstermek yanıltır.
    if (line.removed || !stocksReady) return '';
    if (line.missing) return 'Bu ürün seçili depoda yok.';
    const qty = Number(line.quantity);
    if (!Number.isInteger(qty) || qty < 1) return 'Adet en az 1 olmalı.';
    const max = maxFor(line.stockId, line.key);
    if (qty > max) return `En fazla ${max} adet çıkılabilir.`;
    return '';
  };

  const lineErrors = activeLines.map(lineError).filter(Boolean);
  const reasonError = reason.trim().length < 5 ? 'Sebebi en az birkaç kelimeyle yazın.' : '';

  const warehouseChanged = !inOriginalWarehouse;
  const linesChanged = lines.some(
    (line) =>
      (line.original && (line.removed || Number(line.quantity) !== line.originalQuantity)) ||
      (!line.original && !line.removed)
  );
  const dirty = warehouseChanged || linesChanged;
  const formDirty = dirty || reason.trim().length > 0;
  const canReview = dirty && activeLines.length > 0 && lineErrors.length === 0 && !reasonError && stocksReady;

  const setQuantity = (key, value) => {
    const parsed = value === '' ? '' : Math.max(0, parseInt(value, 10) || 0);
    setLines((prev) => prev.map((line) => (line.key === key ? { ...line, quantity: parsed } : line)));
  };

  const step1 = (key, by) =>
    setLines((prev) =>
      prev.map((line) =>
        line.key === key ? { ...line, quantity: Math.max(1, (Number(line.quantity) || 0) + by) } : line
      )
    );

  const toggleRemoved = (key) =>
    setLines((prev) =>
      prev
        .map((line) => (line.key === key ? { ...line, removed: !line.removed } : line))
        // Sonradan eklenen satır kaldırılınca iz bırakmasına gerek yok; geri alınacak bir
        // orijinal değeri yok.
        .filter((line) => line.original || !line.removed)
    );

  const addLine = (stock, quantity) => {
    setError('');
    setLines((prev) => {
      const existing = prev.find((line) => String(line.stockId) === String(stock.id));
      if (existing) {
        return prev.map((line) =>
          line.key === existing.key
            ? {
                ...line,
                removed: false,
                quantity: (line.removed ? 0 : Number(line.quantity) || 0) + quantity,
              }
            : line
        );
      }
      return [
        ...prev,
        {
          key: lineKey(),
          original: false,
          originalStockId: null,
          stockId: stock.id,
          productId: stock.product?.id,
          name: stock.product?.name || '-',
          sku: stock.product?.sku || '',
          originalQuantity: 0,
          quantity,
          removed: false,
          missing: false,
        },
      ];
    });
  };

  const requestClose = useCallback(() => {
    if (busy) return;
    if (formDirty) {
      setConfirmDiscard(true);
      return;
    }
    onClose();
  }, [busy, formDirty, onClose]);

  useEffect(() => {
    const onKey = (event) => {
      if (event.key !== 'Escape' || confirmDiscard) return;
      if (step === 'review' && !busy) {
        setStep('edit');
        return;
      }
      requestClose();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [requestClose, step, busy, confirmDiscard]);

  useEffect(() => {
    dialogRef.current?.focus();
  }, [step]);

  // Form uzun; sebep alanının yanındaki düğmeye basan kişi tepedeki uyarıyı görmez.
  useEffect(() => {
    if (error) errorRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }, [error]);

  const newWarehouseName =
    warehouses.find((w) => String(w.id) === String(warehouseId))?.name || originalWarehouseName;

  /**
   * Gözden geçirme tablosu: her ürün için önce/sonra ve stoğa etkisi. Aynı depoda yalnızca
   * fark, depo değişince eski depoya dönüş ve yeni depodan çıkış ayrı ayrı yazılıyor —
   * kaydetmenin gerçekten yapacağı hareketler bunlar.
   */
  const changes = useMemo(() => {
    const byProduct = new Map();
    lines.forEach((line) => {
      const pid = String(line.productId);
      const entry = byProduct.get(pid) || { name: line.name, sku: line.sku, before: 0, after: 0 };
      if (line.original) entry.before += line.originalQuantity;
      if (!line.removed) entry.after += Number(line.quantity) || 0;
      byProduct.set(pid, entry);
    });
    return Array.from(byProduct.entries()).map(([pid, entry]) => ({ pid, ...entry }));
  }, [lines]);

  const totalBefore = changes.reduce((sum, c) => sum + c.before, 0);
  const totalAfter = changes.reduce((sum, c) => sum + c.after, 0);
  const signedCopyOnFile = Boolean(receipt?.signedCopyOnFile);
  const nextRevision = (receipt?.revision || 1) + 1;

  const effectText = (change) => {
    const back = reservedFlow ? 'rezervden çıkar' : 'stoğa geri döner';
    const out = reservedFlow ? 'rezerve edilir' : 'stoktan düşer';
    if (warehouseChanged) {
      const parts = [];
      if (change.before > 0)
        parts.push({ tone: 'success', text: `+${change.before} ${originalWarehouseName} — ${back}` });
      if (change.after > 0)
        parts.push({ tone: 'danger', text: `−${change.after} ${newWarehouseName} — ${out}` });
      return parts;
    }
    const diff = change.before - change.after;
    if (diff > 0) return [{ tone: 'success', text: `+${diff} ${back}` }];
    if (diff < 0) return [{ tone: 'danger', text: `−${Math.abs(diff)} ${out}` }];
    return [{ tone: 'secondary', text: 'Değişmez' }];
  };

  const goReview = (event) => {
    event.preventDefault();
    setReasonTouched(true);
    if (!canReview) {
      if (!dirty) setError('Henüz bir değişiklik yapmadınız.');
      else if (activeLines.length === 0) setError('Makbuzda en az bir ürün kalmalı.');
      else setError('İşaretli alanları düzeltin.');
      return;
    }
    setError('');
    setAcknowledged(false);
    setStep('review');
  };

  const save = async () => {
    if (busy) return;
    if (signedCopyOnFile && !acknowledged) {
      setError('Devam etmek için imzalı nüshayla ilgili onayı işaretleyin.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const res = await axios.put(`/api/admin/stock-transfers/${transfer.id}/handover-contents`, {
        sourceWarehouseId: Number(warehouseId),
        items: activeLines.map((line) => ({
          stockId: line.stockId,
          productId: line.productId,
          quantity: Number(line.quantity),
        })),
        reason: reason.trim(),
        expectedUpdatedAt: transfer.updatedAt || null,
      });
      onSaved(res.data);
    } catch (e) {
      // Stok yetersizliği ya da araya giren bir değişiklik: kullanıcı forma dönüp düzeltebilmeli.
      setError(e?.response?.data?.message || 'Değişiklikler kaydedilemedi.');
      setStep('edit');
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <div
        className="modal show d-block"
        tabIndex="-1"
        role="dialog"
        aria-modal="true"
        aria-labelledby="handover-edit-title"
        style={{ backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 2100 }}
      >
        <div className="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable">
          <form
            className="modal-content border-0 rounded-4 shadow"
            onSubmit={goReview}
            ref={dialogRef}
            tabIndex={-1}
          >
            <div className="modal-header bg-warning-subtle rounded-top-4">
              <div>
                <h5 className="modal-title mb-0" id="handover-edit-title">
                  <i className="fas fa-pen-to-square me-2"></i>
                  {step === 'edit' ? 'Çıkış İçeriğini Düzenle' : 'Değişiklikleri Onaylayın'}
                </h5>
                <small className="text-muted">
                  {receipt?.receiptNo} · {transfer?.customerFullName || 'Müşteri'}
                  {' · '}
                  Adım {step === 'edit' ? '1' : '2'} / 2
                </small>
              </div>
              <button
                type="button"
                className="btn-close"
                aria-label="Kapat"
                onClick={requestClose}
                disabled={busy}
              ></button>
            </div>

            <div className="modal-body">
              {error && (
                <div className="alert alert-danger py-2 px-3 small" role="alert" ref={errorRef}>
                  <i className="fas fa-triangle-exclamation me-1"></i>
                  {error}
                </div>
              )}

              {step === 'edit' ? (
                <>
                  <div className="alert alert-warning py-2 px-3 small">
                    <i className="fas fa-boxes-stacked me-1"></i>
                    {reservedFlow ? (
                      <>
                        Bu planlı bir çıkış; ürünler rezervede. Kaydettiğinizde{' '}
                        <strong>rezervasyon farkı</strong> düzeltilir, stok miktarı değişmez.
                      </>
                    ) : (
                      <>
                        Ürünler stoktan düşülmüştü. Kaydettiğinizde yalnızca <strong>fark</strong> stoğa
                        yansır: azaltılan adet geri eklenir, eklenen adet düşülür.
                      </>
                    )}
                  </div>
                  {signedCopyOnFile && (
                    <div className="alert alert-danger py-2 px-3 small">
                      <i className="fas fa-file-signature me-1"></i>
                      Bu makbuzun <strong>imzalı nüshası arşivde</strong>. Düzenlemeden sonra imzalı kâğıt
                      sistemle uyuşmayacak; yeni nüshayı yazdırıp yeniden imzalatmanız gerekir.
                    </div>
                  )}
                  {transfer?.orderNumber && (
                    <div className="alert alert-info py-2 px-3 small">
                      <i className="fas fa-receipt me-1"></i>
                      Bu çıkış {transfer.orderNumber} numaralı siparişe bağlı. Siparişin kendi kalemleri
                      değişmez.
                    </div>
                  )}

                  {/* ── Çıkış deposu ── */}
                  <h6 className="text-uppercase text-muted small fw-bold mt-3 mb-2">Çıkış Deposu</h6>
                  <div className="row g-2 align-items-end">
                    <div className="col-md-7">
                      <label className="form-label small mb-1" htmlFor="handover-edit-warehouse">
                        Depo
                      </label>
                      <select
                        id="handover-edit-warehouse"
                        className="form-select"
                        value={warehouseId}
                        onChange={(e) => setWarehouseId(e.target.value)}
                        disabled={busy}
                      >
                        {warehouses.length === 0 && (
                          <option value={originalWarehouseId}>{originalWarehouseName}</option>
                        )}
                        {warehouses.map((w) => (
                          <option key={w.id} value={w.id}>
                            {w.name}
                            {String(w.id) === originalWarehouseId ? ' (şu anki)' : ''}
                          </option>
                        ))}
                      </select>
                    </div>
                    {warehouseChanged && (
                      <div className="col-md-5">
                        <button
                          type="button"
                          className="btn btn-sm btn-link px-0"
                          onClick={() => setWarehouseId(originalWarehouseId)}
                        >
                          <i className="fas fa-rotate-left me-1"></i>
                          {originalWarehouseName} deposuna geri dön
                        </button>
                      </div>
                    )}
                  </div>
                  {warehouseChanged && (
                    <div className="form-text">
                      <i className="fas fa-right-left me-1"></i>
                      Ürünler {originalWarehouseName} deposuna geri döner, {newWarehouseName} deposundan
                      çıkar.
                    </div>
                  )}
                  {remapNotice && (
                    <div className="alert alert-warning py-2 px-3 small mt-2 mb-0">
                      <i className="fas fa-circle-exclamation me-1"></i>
                      {remapNotice}
                    </div>
                  )}

                  {/* ── Kalemler ── */}
                  <h6 className="text-uppercase text-muted small fw-bold mt-4 mb-2">Ürünler</h6>
                  {stocksError ? (
                    <div className="alert alert-danger py-2 px-3 small d-flex align-items-center justify-content-between gap-2">
                      <span>
                        <i className="fas fa-triangle-exclamation me-1"></i>
                        {stocksError}
                      </span>
                      <button
                        type="button"
                        className="btn btn-sm btn-outline-danger"
                        onClick={() => loadStocks(warehouseId)}
                      >
                        Tekrar Dene
                      </button>
                    </div>
                  ) : (
                    <div className="table-responsive">
                      <table className="table table-sm align-middle mb-0">
                        <thead className="table-light">
                          <tr>
                            <th>Ürün</th>
                            <th className="text-center" style={{ width: 170 }}>
                              Adet
                            </th>
                            <th className="text-end" style={{ width: 110 }}>
                              <span className="visually-hidden">İşlem</span>
                            </th>
                          </tr>
                        </thead>
                        <tbody>
                          {lines.map((line) => {
                            const problem = lineError(line);
                            const changed =
                              line.original &&
                              !line.removed &&
                              Number(line.quantity) !== line.originalQuantity;
                            const max = line.removed || line.missing ? 0 : maxFor(line.stockId, line.key);
                            return (
                              <tr key={line.key} className={line.removed ? 'table-light' : undefined}>
                                <td>
                                  <div
                                    className={`fw-semibold ${line.removed ? 'text-decoration-line-through text-muted' : ''}`}
                                  >
                                    {line.name}
                                  </div>
                                  <div className="d-flex flex-wrap gap-1 align-items-center small">
                                    {line.sku && <span className="text-muted">{line.sku}</span>}
                                    {!line.original && (
                                      <span className="badge rounded-pill bg-primary-subtle text-primary-emphasis border border-primary-subtle">
                                        Yeni
                                      </span>
                                    )}
                                    {changed && (
                                      <span className="badge rounded-pill bg-warning-subtle text-warning-emphasis border border-warning-subtle">
                                        Önce: {line.originalQuantity}
                                      </span>
                                    )}
                                    {line.removed && (
                                      <span className="badge rounded-pill bg-danger-subtle text-danger-emphasis border border-danger-subtle">
                                        Kaldırılacak ({line.originalQuantity} adet)
                                      </span>
                                    )}
                                  </div>
                                  {problem && (
                                    <div className="small text-danger mt-1" role="alert">
                                      <i className="fas fa-circle-exclamation me-1"></i>
                                      {problem}
                                    </div>
                                  )}
                                </td>
                                <td className="text-center">
                                  {line.removed ? (
                                    <span className="text-muted">—</span>
                                  ) : (
                                    <>
                                      <div className="input-group input-group-sm justify-content-center flex-nowrap">
                                        <button
                                          type="button"
                                          className="btn btn-outline-secondary"
                                          aria-label={`${line.name} adedini azalt`}
                                          disabled={busy || line.missing || Number(line.quantity) <= 1}
                                          onClick={() => step1(line.key, -1)}
                                        >
                                          <i className="fas fa-minus"></i>
                                        </button>
                                        <input
                                          type="number"
                                          min={1}
                                          max={max || undefined}
                                          className={`form-control text-center ${problem ? 'is-invalid' : ''}`}
                                          style={{
                                            maxWidth: 70,
                                            backgroundImage: 'none',
                                            paddingRight: '0.5rem',
                                          }}
                                          aria-label={`${line.name} adedi`}
                                          value={line.quantity}
                                          disabled={busy || line.missing}
                                          onChange={(e) => setQuantity(line.key, e.target.value)}
                                        />
                                        <button
                                          type="button"
                                          className="btn btn-outline-secondary"
                                          aria-label={`${line.name} adedini artır`}
                                          disabled={busy || line.missing || Number(line.quantity) >= max}
                                          onClick={() => step1(line.key, 1)}
                                        >
                                          <i className="fas fa-plus"></i>
                                        </button>
                                      </div>
                                      {!line.missing && stocksReady && (
                                        <div className="text-muted" style={{ fontSize: '0.72rem' }}>
                                          en fazla {max}
                                        </div>
                                      )}
                                    </>
                                  )}
                                </td>
                                <td className="text-end">
                                  {line.removed ? (
                                    <button
                                      type="button"
                                      className="btn btn-sm btn-outline-secondary"
                                      onClick={() => toggleRemoved(line.key)}
                                      disabled={busy}
                                    >
                                      <i className="fas fa-rotate-left me-1"></i>
                                      Geri Al
                                    </button>
                                  ) : (
                                    <button
                                      type="button"
                                      className="btn btn-sm btn-outline-danger border-0"
                                      aria-label={`${line.name} kalemini kaldır`}
                                      title="Kalemi kaldır"
                                      onClick={() => toggleRemoved(line.key)}
                                      disabled={busy}
                                    >
                                      <i className="fas fa-trash"></i>
                                    </button>
                                  )}
                                </td>
                              </tr>
                            );
                          })}
                        </tbody>
                        <tfoot className="table-light">
                          <tr>
                            <th className="text-end">Toplam</th>
                            <th className="text-center">
                              {totalAfter}
                              {totalAfter !== totalBefore && (
                                <small className="text-muted fw-normal ms-1">(önce {totalBefore})</small>
                              )}
                            </th>
                            <th></th>
                          </tr>
                        </tfoot>
                      </table>
                    </div>
                  )}

                  <div className="mt-3">
                    <div className="small text-muted mb-1">
                      <i className="fas fa-plus me-1"></i>
                      Ürün ekle — {newWarehouseName}
                    </div>
                    <WarehouseStockPicker
                      stocks={stocks}
                      loading={stocksLoading}
                      disabled={!warehouseId}
                      availableFor={(stock) => maxFor(stock.id, null)}
                      onAdd={addLine}
                    />
                  </div>

                  {/* ── Sebep ── */}
                  <div className="mt-4">
                    <label className="form-label small mb-1" htmlFor="handover-edit-reason">
                      Düzenleme Sebebi <span className="text-danger">*</span>
                    </label>
                    <textarea
                      id="handover-edit-reason"
                      className={`form-control ${reasonTouched && reasonError ? 'is-invalid' : ''}`}
                      rows="2"
                      maxLength={300}
                      value={reason}
                      disabled={busy}
                      placeholder="Örn. Tezgâhta 5 yerine 3 adet verildi; ürün yanlış depodan seçilmiş…"
                      onChange={(e) => setReason(e.target.value)}
                      onBlur={() => setReasonTouched(true)}
                    />
                    <div className="invalid-feedback">{reasonError}</div>
                    <div className="form-text">Denetim kaydına yazılır. {reason.length}/300</div>
                  </div>
                </>
              ) : (
                <>
                  <p className="mb-3">
                    Kaydettiğinizde aşağıdaki stok hareketleri <strong>hemen</strong> uygulanacak.
                  </p>

                  {warehouseChanged && (
                    <div className="border rounded-3 p-2 mb-3 small">
                      <i className="fas fa-warehouse me-2 text-muted"></i>
                      Çıkış deposu: <strong>{originalWarehouseName}</strong>
                      <i className="fas fa-arrow-right mx-2 text-muted"></i>
                      <strong>{newWarehouseName}</strong>
                    </div>
                  )}

                  <div className="table-responsive">
                    <table className="table table-sm align-middle">
                      <thead className="table-light">
                        <tr>
                          <th>Ürün</th>
                          <th className="text-center" style={{ width: 70 }}>
                            Önce
                          </th>
                          <th className="text-center" style={{ width: 70 }}>
                            Sonra
                          </th>
                          <th>Stoğa Etkisi</th>
                        </tr>
                      </thead>
                      <tbody>
                        {changes.map((change) => (
                          <tr key={change.pid}>
                            <td>
                              <div className="fw-semibold small">{change.name}</div>
                              {change.sku && <small className="text-muted">{change.sku}</small>}
                            </td>
                            <td className="text-center">{change.before || '—'}</td>
                            <td className="text-center fw-semibold">{change.after || '—'}</td>
                            <td>
                              {effectText(change).map((effect) => (
                                <div key={effect.text} className={`small text-${effect.tone}-emphasis`}>
                                  {effect.text}
                                </div>
                              ))}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                      <tfoot className="table-light">
                        <tr>
                          <th className="text-end">Toplam</th>
                          <th className="text-center">{totalBefore}</th>
                          <th className="text-center">{totalAfter}</th>
                          <th></th>
                        </tr>
                      </tfoot>
                    </table>
                  </div>

                  <ul className="small mb-3 ps-3">
                    <li>
                      Makbuz numarası değişmez ({receipt?.receiptNo}); kayıt{' '}
                      <strong>{nextRevision}. basım</strong> olarak işaretlenir.
                    </li>
                    <li>Daha önce basılmış kâğıt artık geçersiz olur; yeni nüshayı yazdırmanız gerekir.</li>
                    <li>Taraflar, müşteri ve tarih bilgileri değişmez.</li>
                    <li>
                      Sebep denetim kaydına yazılır: <em>“{reason.trim()}”</em>
                    </li>
                  </ul>

                  {signedCopyOnFile && (
                    <div className="form-check border border-danger-subtle bg-danger-subtle rounded-3 p-3 ps-5">
                      <input
                        className="form-check-input"
                        type="checkbox"
                        id="handover-edit-ack"
                        checked={acknowledged}
                        onChange={(e) => setAcknowledged(e.target.checked)}
                        disabled={busy}
                      />
                      <label className="form-check-label small" htmlFor="handover-edit-ack">
                        Arşivdeki imzalı nüshanın artık bu kayıtla uyuşmayacağını ve yeni nüshayı yeniden
                        imzalatacağımı anladım.
                      </label>
                    </div>
                  )}
                </>
              )}
            </div>

            <div className="modal-footer">
              {step === 'edit' ? (
                <>
                  <button type="button" className="btn btn-light" onClick={requestClose} disabled={busy}>
                    Vazgeç
                  </button>
                  <button type="submit" className="btn btn-warning" disabled={busy || !stocksReady}>
                    <i className="fas fa-list-check me-2"></i>
                    Değişiklikleri Gözden Geçir
                  </button>
                </>
              ) : (
                <>
                  <button
                    type="button"
                    className="btn btn-light"
                    onClick={() => setStep('edit')}
                    disabled={busy}
                  >
                    <i className="fas fa-arrow-left me-2"></i>
                    Düzenlemeye Dön
                  </button>
                  <button
                    type="button"
                    className="btn btn-danger"
                    onClick={save}
                    disabled={busy || (signedCopyOnFile && !acknowledged)}
                  >
                    <i className={`fas ${busy ? 'fa-spinner fa-spin' : 'fa-check'} me-2`}></i>
                    {reservedFlow ? 'Onayla ve Rezervasyonu Düzelt' : 'Onayla ve Stoğu Düzelt'}
                  </button>
                </>
              )}
            </div>
          </form>
        </div>
      </div>

      <ConfirmModal
        show={confirmDiscard}
        title="Değişiklikler kaydedilmedi"
        message="Yaptığınız düzenlemeler kaybolacak. Stok ve makbuz değişmeden pencere kapanacak."
        confirmText="Değişiklikleri At"
        cancelText="Düzenlemeye Dön"
        confirmVariant="danger"
        icon="exclamation-triangle"
        onConfirm={() => {
          setConfirmDiscard(false);
          onClose();
        }}
        onCancel={() => setConfirmDiscard(false)}
      />
    </>
  );
}

/**
 * Sevkiyatı açılışta sunucudan tazeler. Paneldeki kopya listeden geliyor ve modal açıkken
 * yenilenmiyor; eski kalemler üzerinden hesaplanan bir fark yanlış stok hareketi yazardı,
 * sürüm kontrolü de kullanıcıyı çözemeyeceği bir "kaydı yenileyin" hatasıyla bırakırdı.
 */
export default function HandoverContentsEditModal({ transfer, receipt, onClose, onSaved }) {
  const [fresh, setFresh] = useState(null);
  const [loadError, setLoadError] = useState('');

  const load = useCallback(async () => {
    setLoadError('');
    try {
      const res = await axios.get(`/api/admin/stock-transfers/${transfer.id}`);
      setFresh(res.data);
    } catch (e) {
      setLoadError(e?.response?.data?.message || 'Çıkış kaydı yüklenemedi.');
    }
  }, [transfer.id]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (fresh) return undefined;
    const onKey = (event) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [fresh, onClose]);

  if (fresh) {
    return <ContentsEditForm transfer={fresh} receipt={receipt} onClose={onClose} onSaved={onSaved} />;
  }

  return (
    <div
      className="modal show d-block"
      tabIndex="-1"
      role="dialog"
      aria-modal="true"
      style={{ backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 2100 }}
    >
      <div className="modal-dialog modal-dialog-centered">
        <div className="modal-content border-0 rounded-4 shadow">
          <div className="modal-body text-center py-4">
            {loadError ? (
              <>
                <div className="text-danger mb-3">
                  <i className="fas fa-triangle-exclamation me-1"></i>
                  {loadError}
                </div>
                <div className="d-flex justify-content-center gap-2">
                  <button type="button" className="btn btn-light" onClick={onClose}>
                    Kapat
                  </button>
                  <button type="button" className="btn btn-primary" onClick={load}>
                    Tekrar Dene
                  </button>
                </div>
              </>
            ) : (
              <div className="text-muted">
                <span className="spinner-border spinner-border-sm me-2"></span>
                Çıkış kaydı yükleniyor…
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
