import React, { useCallback, useEffect, useMemo, useState } from 'react';
import axios from 'axios';

/**
 * Sevkiyat iadesi — depodan çıkan mal geri geldiğinde.
 *
 * Makbuz panelinden ayrı, çünkü iade kâğıdın değil sevkiyatın olayı: imzalanan makbuz malın
 * çıktığını söylüyor ve bu doğru kalmaya devam ediyor. İade onun üstüne yazılıyor.
 *
 * Kısmi ve tekrarlı olabiliyor — üç kalemin biri bugün, diğeri gelecek hafta dönebilir — bu
 * yüzden form kalem kalem çalışıyor ve her satır kalan adetle sınırlanıyor.
 */

const REASONS = [
  { value: 'UNDELIVERED', label: 'Teslim edilemedi (adres/alıcı bulunamadı)' },
  { value: 'REFUSED', label: 'Müşteri teslim almadı' },
  { value: 'DAMAGED', label: 'Ürün hasarlı' },
  { value: 'WRONG_ITEM', label: 'Yanlış ürün gönderilmiş' },
  { value: 'SURPLUS', label: 'Fazla gönderilmiş' },
  { value: 'OTHER', label: 'Diğer' },
];

const REASON_LABELS = REASONS.reduce((acc, r) => ({ ...acc, [r.value]: r.label }), {});

/**
 * Siparişe bağlı sevkiyatlarda malın yanında verilmesi gereken ikinci karar.
 *
 * Depoda ikisi de aynı görünüyor — mal geri geldi — ama sipariş defterinde tam tersi
 * şeyler: biri "tekrar deneyeceğiz", diğeri "bu iş bitti". Seçim rezervasyonu da
 * belirliyor, o yüzden varsayılan bırakılmıyor, sorulur.
 */
const ORDER_OUTCOMES = [
  {
    value: 'KEEP_ORDER',
    label: 'Sipariş açık kalsın',
    hint: 'Teslimat denemesi tutmadı, yeniden gönderilecek. Dönen adet sipariş için yeniden rezerve edilir.',
  },
  {
    value: 'RETURN_ORDER',
    label: 'Sipariş iade edildi',
    hint: 'Sipariş "İade Edildi" olur ve mal serbest kalır. Para iadesi ayrı bir adım. Sevkiyatın tamamı geri gelmiş olmalı.',
  },
];

const ORDER_OUTCOME_LABELS = ORDER_OUTCOMES.reduce((acc, o) => ({ ...acc, [o.value]: o.label }), {});

const formatDateTime = (value) => {
  if (!value) return '-';
  try {
    return new Date(value).toLocaleString('tr-TR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return value;
  }
};

const toLocalInput = (date) => {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

export default function TransferReturnPanel({ transfer, isAdmin = false, onTransferChanged }) {
  const transferId = transfer?.id;

  const [returns, setReturns] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({
    reason: 'UNDELIVERED',
    note: '',
    returnedAt: '',
    orderOutcome: 'KEEP_ORDER',
  });
  const [quantities, setQuantities] = useState({});

  const items = useMemo(() => (Array.isArray(transfer?.items) ? transfer.items : []), [transfer]);
  const orderLinked = Boolean(transfer?.orderId);

  /** What is still out for each line, after everything already returned against it. */
  const remainingFor = useCallback(
    (item) => Math.max(0, (item.quantity || 0) - (item.returnedQuantity || 0)),
    []
  );

  const shippedTotal = items.reduce((sum, item) => sum + (item.quantity || 0), 0);
  const returnedTotal = transfer?.returnedQuantity || 0;
  const anythingLeftToReturn = items.some((item) => remainingFor(item) > 0);

  const load = useCallback(async () => {
    if (!transferId) return;
    setLoading(true);
    try {
      const res = await axios.get(`/api/stock-transfers/${transferId}/returns`);
      setReturns(Array.isArray(res.data) ? res.data : []);
    } catch (e) {
      setError(e?.response?.data?.message || 'İade geçmişi alınamadı.');
    } finally {
      setLoading(false);
    }
  }, [transferId]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!notice) return undefined;
    const timer = setTimeout(() => setNotice(''), 5000);
    return () => clearTimeout(timer);
  }, [notice]);

  const openForm = () => {
    setForm({
      reason: 'UNDELIVERED',
      note: '',
      returnedAt: toLocalInput(new Date()),
      // Açık kalması varsayılan: geri gelen malın çoğu yeniden gönderiliyor, ve yanlış
      // seçilirse geri alması zor olan taraf siparişi kapatmak.
      orderOutcome: 'KEEP_ORDER',
    });
    // Boş başlıyor: hangi kalemin döndüğünü kullanıcı seçmeli. Kalan adetlerle ön doldurmak,
    // tek kalemi dönen bir sevkiyatta tamamını iade etmeyi bir tıka indirirdi.
    setQuantities({});
    setError('');
    setShowForm(true);
  };

  const setQuantity = (itemId, raw, max) => {
    const parsed = parseInt(raw, 10);
    setQuantities((prev) => {
      const next = { ...prev };
      if (!raw || Number.isNaN(parsed) || parsed <= 0) {
        delete next[itemId];
      } else {
        next[itemId] = Math.min(parsed, max);
      }
      return next;
    });
  };

  const draftTotal = Object.values(quantities).reduce((sum, q) => sum + q, 0);
  /** Sevkiyatta hâlâ dışarıda olan toplam; siparişin kapatılabilmesi için hepsi girilmeli. */
  const remainingTotal = items.reduce((sum, item) => sum + remainingFor(item), 0);

  const submit = async (event) => {
    event.preventDefault();
    const lines = Object.entries(quantities)
      .filter(([, quantity]) => quantity > 0)
      .map(([transferItemId, quantity]) => ({ transferItemId: Number(transferItemId), quantity }));

    if (lines.length === 0) {
      setError('En az bir kalem için iade adedi girin.');
      return;
    }
    // Adetler düşürülünce "sipariş iade edildi" seçeneği geçersizleşebiliyor; radyo devre dışı
    // kalıyor ama seçili kalıyordu ve sunucudan hata dönüyordu.
    const outcome =
      orderLinked && form.orderOutcome === 'RETURN_ORDER' && draftTotal < remainingTotal
        ? 'KEEP_ORDER'
        : form.orderOutcome;

    setBusy(true);
    setError('');
    try {
      await axios.post(`/api/stock-transfers/${transferId}/returns`, {
        returnedAt: form.returnedAt ? `${form.returnedAt}:00` : null,
        reason: form.reason,
        note: form.note.trim() || null,
        orderOutcome: orderLinked ? outcome : null,
        items: lines,
      });
      // Sevkiyatı yeniden çekiyoruz: kalem bazındaki returnedQuantity değerleri de değişti ve
      // form bir sonraki iadede kalan adetleri buradan hesaplıyor.
      const refreshed = await axios.get(`/api/stock-transfers/${transferId}`);
      setShowForm(false);
      setNotice(`${draftTotal} adet iade alındı ve stoğa geri eklendi.`);
      await load();
      if (onTransferChanged && refreshed?.data) onTransferChanged(refreshed.data);
    } catch (e) {
      setError(e?.response?.data?.message || 'İade kaydedilemedi.');
    } finally {
      setBusy(false);
    }
  };

  if (!transferId) return null;

  // İade yalnızca depodan gerçekten çıkmış mal için anlamlı. Bekleyen ya da yoldaki bir
  // sevkiyatın karşılığı iptaldir; iptal zaten rezerveyi serbest bırakıyor.
  const eligible =
    transfer?.status === 'COMPLETED' && (transfer?.transferType || 'WAREHOUSE') === 'CUSTOMER_DELIVERY';
  if (!eligible && returns.length === 0) return null;

  const fullyReturned = shippedTotal > 0 && returnedTotal >= shippedTotal;

  return (
    <div className="border rounded-3 p-3 mt-3">
      <div className="d-flex justify-content-between align-items-start flex-wrap gap-2 mb-2">
        <div>
          <small className="text-muted text-uppercase d-block">Sevkiyat İadesi</small>
          {returnedTotal > 0 ? (
            <div className="d-flex align-items-center gap-2 flex-wrap mt-1">
              <span
                className={`badge rounded-pill ${
                  fullyReturned
                    ? 'bg-danger-subtle text-danger-emphasis border border-danger-subtle'
                    : 'bg-warning-subtle text-warning-emphasis border border-warning-subtle'
                }`}
              >
                <i className="fas fa-rotate-left me-1"></i>
                {fullyReturned ? 'Tamamı iade edildi' : 'Kısmen iade edildi'}
              </span>
              <span className="small text-muted">
                {returnedTotal} / {shippedTotal} adet geri alındı
              </span>
            </div>
          ) : (
            <div className="small text-muted mt-1">Bu sevkiyattan iade alınmadı.</div>
          )}
        </div>

        {isAdmin && eligible && !showForm && (
          <button
            type="button"
            className="btn btn-sm btn-outline-danger"
            disabled={busy || loading || !anythingLeftToReturn}
            title={!anythingLeftToReturn ? 'Bu sevkiyatın tamamı zaten iade edildi.' : undefined}
            onClick={openForm}
          >
            <i className="fas fa-rotate-left me-1"></i>
            İade Kaydet
          </button>
        )}
      </div>

      {error && (
        <div className="alert alert-danger py-2 px-3 small mb-2">
          <i className="fas fa-triangle-exclamation me-1"></i>
          {error}
        </div>
      )}
      {notice && (
        <div className="alert alert-success py-2 px-3 small mb-2">
          <i className="fas fa-circle-check me-1"></i>
          {notice}
        </div>
      )}

      {showForm && (
        <form className="border rounded-3 p-3 bg-light mb-3" onSubmit={submit}>
          <div className="small text-muted mb-2">
            <i className="fas fa-circle-info me-1"></i>
            Girilen adetler <strong>çıkış deposuna geri eklenir</strong>. Hasarlı ürünler de stoğa döner;
            ıskartaya ayıracaksanız ardından &quot;Stok Çıkar&quot; ile düşün.
          </div>

          <div className="table-responsive mb-3">
            <table className="table table-sm align-middle mb-0">
              <thead className="table-light">
                <tr>
                  <th className="small">Ürün</th>
                  <th className="small text-center" style={{ width: 90 }}>
                    Sevk
                  </th>
                  <th className="small text-center" style={{ width: 90 }}>
                    Kalan
                  </th>
                  <th className="small text-center" style={{ width: 120 }}>
                    İade Adedi
                  </th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => {
                  const remaining = remainingFor(item);
                  return (
                    <tr key={item.id} className={remaining === 0 ? 'text-muted' : undefined}>
                      <td>
                        <div className="small fw-semibold">{item.product?.name || '-'}</div>
                        {item.product?.sku && <small className="text-muted">{item.product.sku}</small>}
                      </td>
                      <td className="text-center small">{item.quantity}</td>
                      <td className="text-center small">{remaining}</td>
                      <td className="text-center">
                        <input
                          type="number"
                          className="form-control form-control-sm text-center"
                          min="0"
                          max={remaining}
                          disabled={remaining === 0}
                          value={quantities[item.id] ?? ''}
                          placeholder={remaining === 0 ? '—' : '0'}
                          onChange={(e) => setQuantity(item.id, e.target.value, remaining)}
                        />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
              <tfoot className="table-light">
                <tr>
                  <th colSpan={3} className="text-end small">
                    İade toplamı
                  </th>
                  <th className="text-center small">{draftTotal}</th>
                </tr>
              </tfoot>
            </table>
          </div>

          {orderLinked && (
            <div className="border rounded-3 p-2 mb-3 bg-white">
              <div className="small fw-semibold mb-1">
                <i className="fas fa-receipt me-1 text-primary"></i>
                {transfer.orderNumber} siparişi ne olacak?
              </div>
              {/* Sorulmak zorunda: mal geri geldi diye sipariş bitmiş olmuyor, ve iki seçenek
                  rezervasyonu ters yönde etkiliyor. Sunucu da kararsız isteği reddediyor. */}
              {ORDER_OUTCOMES.map((option) => (
                <div className="form-check" key={option.value}>
                  <input
                    className="form-check-input"
                    type="radio"
                    name="orderOutcome"
                    id={`outcome-${option.value}`}
                    checked={form.orderOutcome === option.value}
                    disabled={option.value === 'RETURN_ORDER' && draftTotal < remainingTotal}
                    onChange={() => setForm((prev) => ({ ...prev, orderOutcome: option.value }))}
                  />
                  <label className="form-check-label" htmlFor={`outcome-${option.value}`}>
                    <span className="small fw-semibold">{option.label}</span>
                    <span className="d-block text-muted" style={{ fontSize: '0.72rem' }}>
                      {option.hint}
                    </span>
                  </label>
                </div>
              ))}
              {draftTotal < remainingTotal && (
                <div className="text-muted mt-1" style={{ fontSize: '0.72rem' }}>
                  <i className="fas fa-circle-info me-1"></i>
                  Sevkiyatın tamamı girilmediği için sipariş kapatılamaz.
                </div>
              )}
            </div>
          )}

          <div className="row g-2">
            <div className="col-md-6">
              <label className="form-label small mb-1">İade Sebebi</label>
              <select
                className="form-select form-select-sm"
                value={form.reason}
                onChange={(e) => setForm((prev) => ({ ...prev, reason: e.target.value }))}
              >
                {REASONS.map((r) => (
                  <option key={r.value} value={r.value}>
                    {r.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="col-md-6">
              <label className="form-label small mb-1">İade Tarihi</label>
              <input
                type="datetime-local"
                className="form-control form-control-sm"
                max={toLocalInput(new Date())}
                value={form.returnedAt}
                onChange={(e) => setForm((prev) => ({ ...prev, returnedAt: e.target.value }))}
              />
            </div>
            <div className="col-12">
              <label className="form-label small mb-1">Açıklama</label>
              <textarea
                className="form-control form-control-sm"
                rows="2"
                maxLength={1000}
                value={form.note}
                onChange={(e) => setForm((prev) => ({ ...prev, note: e.target.value }))}
              />
            </div>
          </div>

          <div className="d-flex gap-2 mt-3">
            <button type="submit" className="btn btn-sm btn-danger" disabled={busy || draftTotal === 0}>
              <i className={`fas ${busy ? 'fa-spinner fa-spin' : 'fa-rotate-left'} me-1`}></i>
              İadeyi Kaydet
            </button>
            <button
              type="button"
              className="btn btn-sm btn-light"
              disabled={busy}
              onClick={() => setShowForm(false)}
            >
              Vazgeç
            </button>
          </div>
        </form>
      )}

      {loading ? (
        <div className="small text-muted">
          <i className="fas fa-spinner fa-spin me-1"></i>
          Yükleniyor…
        </div>
      ) : (
        returns.length > 0 && (
          <div className="table-responsive">
            <table className="table table-sm align-middle mb-0">
              <thead className="table-light">
                <tr>
                  <th className="small">Tarih</th>
                  <th className="small">Sebep</th>
                  <th className="small">Kalemler</th>
                  <th className="small text-center" style={{ width: 80 }}>
                    Adet
                  </th>
                  <th className="small d-none d-md-table-cell">Kaydeden</th>
                </tr>
              </thead>
              <tbody>
                {returns.map((entry) => (
                  <tr key={entry.id}>
                    <td className="small">{formatDateTime(entry.returnedAt)}</td>
                    <td className="small">
                      {REASON_LABELS[entry.reason] || entry.reason}
                      {entry.orderOutcome && (
                        <div
                          className={`badge rounded-pill mt-1 ${
                            entry.orderOutcome === 'RETURN_ORDER'
                              ? 'bg-danger-subtle text-danger-emphasis border border-danger-subtle'
                              : 'bg-info-subtle text-info-emphasis border border-info-subtle'
                          }`}
                          style={{ fontSize: '0.66rem' }}
                        >
                          <i className="fas fa-receipt me-1"></i>
                          {ORDER_OUTCOME_LABELS[entry.orderOutcome] || entry.orderOutcome}
                        </div>
                      )}
                      {entry.note && (
                        <div className="text-muted" style={{ fontSize: '0.72rem' }}>
                          <i className="fas fa-comment-dots me-1"></i>
                          {entry.note}
                        </div>
                      )}
                    </td>
                    <td className="small">
                      {(entry.items || []).map((line) => (
                        <div key={`${entry.id}-${line.transferItemId}`}>
                          {line.productName || '-'}
                          <span className="text-muted"> × {line.quantity}</span>
                        </div>
                      ))}
                    </td>
                    <td className="text-center">
                      <span className="badge rounded-pill bg-danger-subtle text-danger-emphasis border border-danger-subtle">
                        {entry.totalQuantity}
                      </span>
                    </td>
                    <td className="small d-none d-md-table-cell">{entry.recordedBy || '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      )}
    </div>
  );
}
