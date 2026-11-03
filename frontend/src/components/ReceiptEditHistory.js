import React, { useCallback, useEffect, useState } from 'react';
import axios from 'axios';

/**
 * Bir makbuzun içerik düzenlemeleri: kim, ne zaman, neden, neyi neye çevirdi.
 *
 * Genel denetim zaman çizelgesi aynı olayı serbest metin olarak da gösteriyor; burası
 * sorulan soruya doğrudan cevap veren görünüm — "bu kâğıt neden 3. basım, elimdeki eskisinde
 * ne yazıyordu?". Her kayıt kalem kalem önce/sonra tablosuyla geliyor; değişmeyen kalem
 * soluk, değişen kalem farkıyla birlikte vurgulu.
 *
 * Makbuz panelinde ve makbuz arşivinde aynı bileşen; iki ekranın aynı düzenlemeyi farklı
 * anlatması mümkün olmasın diye.
 */

const formatDateTime = (value) => {
  if (!value) return '—';
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

const lineKey = (line) => line.sku || line.name || '-';

/** Önce ve sonra kalemlerini ürün bazında yan yana dizer; sıra önce eski kâğıdınki. */
const diffLines = (before = [], after = []) => {
  const rows = new Map();
  before.forEach((line) => {
    const key = lineKey(line);
    const row = rows.get(key) || { key, name: line.name, sku: line.sku, before: 0, after: 0 };
    row.before += line.quantity || 0;
    rows.set(key, row);
  });
  after.forEach((line) => {
    const key = lineKey(line);
    const row = rows.get(key) || { key, name: line.name, sku: line.sku, before: 0, after: 0 };
    row.after += line.quantity || 0;
    rows.set(key, row);
  });
  return Array.from(rows.values());
};

export function RevisionEntry({ entry }) {
  const rows = diffLines(entry.itemsBefore, entry.itemsAfter);
  const totalBefore = rows.reduce((sum, row) => sum + row.before, 0);
  const totalAfter = rows.reduce((sum, row) => sum + row.after, 0);
  const warehouseChanged = (entry.sourceWarehouseBefore || '') !== (entry.sourceWarehouseAfter || '');

  return (
    <li className="border rounded-3 p-3 mb-2 bg-white">
      <div className="d-flex flex-wrap align-items-center gap-2 small">
        <span className="badge rounded-pill bg-warning-subtle text-warning-emphasis border border-warning-subtle">
          {entry.revision}. basım
        </span>
        <span className="fw-semibold">
          <i className="fas fa-user me-1 text-muted" aria-hidden="true"></i>
          {entry.editedBy}
        </span>
        <span className="text-muted">
          <i className="fas fa-clock me-1" aria-hidden="true"></i>
          <time dateTime={entry.editedAt}>{formatDateTime(entry.editedAt)}</time>
        </span>
      </div>

      {entry.reason && (
        <div className="small mt-2">
          <span className="text-muted">Sebep: </span>
          <span className="fst-italic">“{entry.reason}”</span>
        </div>
      )}

      {warehouseChanged && (
        <div className="small mt-2">
          <i className="fas fa-warehouse me-1 text-muted" aria-hidden="true"></i>
          Çıkış deposu:{' '}
          <span className="text-decoration-line-through text-muted">
            {entry.sourceWarehouseBefore || '—'}
          </span>
          <i className="fas fa-arrow-right mx-2 text-muted" aria-hidden="true"></i>
          <strong>{entry.sourceWarehouseAfter || '—'}</strong>
        </div>
      )}

      <div className="table-responsive mt-2">
        <table className="table table-sm align-middle mb-0 small">
          <caption className="visually-hidden">
            {entry.revision}. basımda kalemlerin önceki ve sonraki adetleri
          </caption>
          <thead className="table-light">
            <tr>
              <th scope="col">Ürün</th>
              <th scope="col" className="text-center" style={{ width: 64 }}>
                Önce
              </th>
              <th scope="col" className="text-center" style={{ width: 64 }}>
                Sonra
              </th>
              <th scope="col" className="text-center" style={{ width: 72 }}>
                Fark
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => {
              const diff = row.after - row.before;
              const changed = diff !== 0 || warehouseChanged;
              return (
                <tr key={row.key} className={changed ? undefined : 'text-muted'}>
                  <td>
                    <div className={changed ? 'fw-semibold' : undefined}>{row.name || '—'}</div>
                    {row.sku && (
                      <div className="text-muted" style={{ fontSize: '0.72rem' }}>
                        {row.sku}
                      </div>
                    )}
                  </td>
                  <td className="text-center">{row.before || '—'}</td>
                  <td className="text-center">{row.after || '—'}</td>
                  <td className="text-center">
                    {diff === 0 ? (
                      <span className="text-muted">—</span>
                    ) : (
                      <span
                        className={
                          diff > 0 ? 'text-danger-emphasis fw-semibold' : 'text-success-emphasis fw-semibold'
                        }
                      >
                        {diff > 0 ? `+${diff}` : `−${Math.abs(diff)}`}
                        <span className="visually-hidden">
                          {diff > 0 ? ' adet eklendi' : ' adet azaltıldı'}
                        </span>
                      </span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
          <tfoot className="table-light">
            <tr>
              <th scope="row" className="text-end">
                Toplam
              </th>
              <th className="text-center">{totalBefore}</th>
              <th className="text-center">{totalAfter}</th>
              <th></th>
            </tr>
          </tfoot>
        </table>
      </div>
    </li>
  );
}

/**
 * Geçmişi sunucudan çeker. `refreshKey` değişince yeniden yüklenir — düzenleme kaydedildiği
 * an yeni kayıt listenin başında görünmeli.
 */
export default function ReceiptEditHistory({ transferId, refreshKey = 0 }) {
  const [entries, setEntries] = useState(null);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setError('');
    try {
      const res = await axios.get(`/api/admin/stock-transfers/${transferId}/receipt/revisions`);
      setEntries(Array.isArray(res.data) ? res.data : []);
    } catch (e) {
      setEntries(null);
      setError(e?.response?.data?.message || 'Düzeltme geçmişi yüklenemedi.');
    }
  }, [transferId]);

  useEffect(() => {
    load();
  }, [load, refreshKey]);

  if (error) {
    return (
      <div className="alert alert-danger py-2 px-3 small d-flex align-items-center justify-content-between gap-2 mb-0">
        <span>
          <i className="fas fa-triangle-exclamation me-1"></i>
          {error}
        </span>
        <button type="button" className="btn btn-sm btn-outline-danger" onClick={load}>
          Tekrar Dene
        </button>
      </div>
    );
  }
  if (entries === null) {
    return (
      <div className="small text-muted">
        <span className="spinner-border spinner-border-sm me-2"></span>
        Düzeltme geçmişi yükleniyor…
      </div>
    );
  }
  if (entries.length === 0) {
    return (
      <div className="small text-muted">
        <i className="fas fa-circle-info me-1"></i>
        Bu makbuzun içeriği hiç düzeltilmedi; basıldığı hâliyle duruyor.
      </div>
    );
  }
  return (
    <ol className="list-unstyled mb-0" aria-label="Düzeltme geçmişi, en yenisi başta">
      {entries.map((entry) => (
        <RevisionEntry key={entry.id} entry={entry} />
      ))}
    </ol>
  );
}
