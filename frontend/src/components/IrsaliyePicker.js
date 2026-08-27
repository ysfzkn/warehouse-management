import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { formatIsoDateTr } from '../utils/date';

/**
 * Waybill number field for the stock entry screens.
 *
 * One irsaliye covers a whole delivery — sixty products off one truck share one number — so the
 * field is not really "type a value", it is "say which delivery this is". Two consequences shape
 * this component:
 *
 * - Numbers already in use are offered back. Retyping a twenty-character number sixty times is
 *   how a delivery quietly splits into two under a typo.
 * - Whatever is typed is weighed immediately: if the number is already carrying rows, the operator
 *   is told how many. That is the difference between "yes, this is the delivery I am entering" and
 *   "I have just invented a second one".
 */

/** Same folding the server stores: letters and digits only, upper-cased. */
const foldKey = (raw) => (raw || '').replace(/[^A-Za-z0-9]/g, '').toUpperCase();

const failureMessage = (error) => {
  const status = error?.response?.status;
  if (status === 401 || status === 403) return 'Önceki irsaliyeler listelenemedi (yetki yok).';
  return 'Önceki irsaliyeler listelenemedi.';
};

/** Closes the dropdown when the click lands outside the field. */
const useOutsideClose = (onClose) => {
  const ref = useRef(null);
  useEffect(() => {
    const handler = (e) => {
      if (ref.current && !ref.current.contains(e.target)) onClose();
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [onClose]);
  return ref;
};

const IrsaliyePicker = ({
  value,
  onChange,
  onPick,
  id = 'irsaliyeNo',
  disabled = false,
  invalid = false,
  placeholder = 'Örn: ABC2026000000123',
}) => {
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [open, setOpen] = useState(false);
  const ref = useOutsideClose(() => setOpen(false));

  const trimmed = (value || '').trim();

  useEffect(() => {
    // Fetched while the list is open (to suggest) and also while it is shut but a number is
    // present (to weigh it) — an already-filled field should report its delivery on sight,
    // without the operator having to click into it first.
    if (!open && !trimmed) {
      setResults([]);
      setError(null);
      return undefined;
    }
    setLoading(true);
    let cancelled = false;
    const timer = setTimeout(() => {
      axios
        .get('/api/stocks/irsaliye/suggest', { params: { q: trimmed || undefined } })
        .then((r) => {
          if (cancelled) return;
          setResults(Array.isArray(r.data) ? r.data : []);
          setError(null);
        })
        .catch((e) => {
          if (cancelled) return;
          setResults([]);
          setError(failureMessage(e));
        })
        .finally(() => !cancelled && setLoading(false));
    }, 250);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [trimmed, open]);

  const typedKey = foldKey(trimmed);
  const exact = typedKey ? results.find((r) => foldKey(r.irsaliyeNo) === typedKey) : null;

  const choose = (row) => {
    onPick?.({ irsaliyeNo: row.irsaliyeNo, irsaliyeDate: row.irsaliyeDate || '' });
    setOpen(false);
  };

  return (
    <div className="position-relative" ref={ref} style={{ zIndex: open ? 1080 : 'auto' }}>
      <div className="input-group">
        <span className="input-group-text bg-white">
          <i className="fas fa-hashtag text-primary"></i>
        </span>
        <input
          id={id}
          type="text"
          className={`form-control ${invalid ? 'is-invalid' : trimmed ? 'is-valid' : ''}`}
          value={value || ''}
          disabled={disabled}
          autoComplete="off"
          maxLength="50"
          placeholder={placeholder}
          onChange={(e) => {
            onChange(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
        />
        {loading && (
          <span className="input-group-text bg-white">
            <span className="spinner-border spinner-border-sm text-primary" />
          </span>
        )}
      </div>

      {/* What the typed number already carries. Shown whether or not the list is open, because
          it is the answer to "am I still on the same delivery?". */}
      {exact && (
        <small className="d-block mt-1 text-primary">
          <i className="fas fa-layer-group me-1"></i>
          Bu irsaliyede <strong>{exact.stockCount}</strong> ürün, toplam{' '}
          <strong>{exact.totalQuantity}</strong> adet kayıtlı
          {exact.irsaliyeDate ? ` · ${formatIsoDateTr(exact.irsaliyeDate)}` : ''}
        </small>
      )}
      {!exact && trimmed && !loading && !error && (
        <small className="d-block mt-1 text-muted">
          <i className="fas fa-circle-plus me-1"></i>
          Yeni irsaliye — bu numarayla kayıtlı ürün yok.
        </small>
      )}

      {open && (
        <div
          className="list-group shadow position-absolute w-100 mt-1"
          style={{ maxHeight: 260, overflowY: 'auto' }}
        >
          {error && <div className="list-group-item text-danger small">{error}</div>}
          {!loading && !error && results.length === 0 && (
            <div className="list-group-item text-muted small">
              {trimmed
                ? 'Eşleşen irsaliye yok — yeni numara olarak yazabilirsiniz.'
                : 'Henüz irsaliye girilmemiş.'}
            </div>
          )}
          {results.map((row) => (
            <button
              type="button"
              key={foldKey(row.irsaliyeNo)}
              className="list-group-item list-group-item-action"
              onClick={() => choose(row)}
            >
              <div className="d-flex justify-content-between align-items-start gap-2">
                <div className="min-w-0">
                  <div className="fw-semibold text-truncate">{row.irsaliyeNo}</div>
                  <small className="text-muted">
                    {row.irsaliyeDate ? formatIsoDateTr(row.irsaliyeDate) : 'Tarih yok'}
                  </small>
                </div>
                <span className="badge bg-light text-dark border flex-shrink-0">
                  {row.stockCount} ürün · {row.totalQuantity} adet
                </span>
              </div>
            </button>
          ))}
        </div>
      )}
    </div>
  );
};

export default IrsaliyePicker;
