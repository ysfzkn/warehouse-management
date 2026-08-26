import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { formatPhoneForDisplay } from '../utils/phone';

/**
 * Type-ahead pickers for the two things that get retyped on every single transfer: the driver
 * and the recipient.
 *
 * Both search the server's normalised columns, so "ballı", "BALLI" and "balli" find the same
 * record — matching how the operator actually remembers a name rather than how it was stored.
 * Picking fills the form; the fields stay editable afterwards, since a driver may be in a
 * different vehicle today.
 */

const usePeopleSearch = (endpoint, minChars) => {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!open) return undefined;
    if (query.trim().length > 0 && query.trim().length < minChars) {
      setResults([]);
      return undefined;
    }
    setLoading(true);
    let cancelled = false;
    const timer = setTimeout(() => {
      axios
        .get(endpoint, { params: { q: query.trim() || undefined } })
        .then((r) => !cancelled && setResults(Array.isArray(r.data) ? r.data : []))
        .catch(() => !cancelled && setResults([]))
        .finally(() => !cancelled && setLoading(false));
    }, 250);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [query, open, endpoint, minChars]);

  return { query, setQuery, results, loading, open, setOpen };
};

/** Closes the dropdown when the click lands outside the picker. */
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

/**
 * Wrapper that owns the stacking context. Bootstrap positions every `.input-group` child, so a
 * form field further down the page paints over an absolutely positioned dropdown that only
 * raises its own z-index — the phone field was landing in the middle of the driver list. While
 * the list is open the whole picker is lifted into a layer above the rest of the form.
 */
function PickerShell({ open, children, innerRef }) {
  return (
    <div className="position-relative" ref={innerRef} style={{ zIndex: open ? 1080 : 'auto' }}>
      {children}
    </div>
  );
}

function Dropdown({ children }) {
  return (
    <div
      className="list-group shadow position-absolute w-100 mt-1"
      style={{ maxHeight: 280, overflowY: 'auto' }}
    >
      {children}
    </div>
  );
}

/**
 * Driver picker. Selecting one hands back {name, tcId, phone, vehiclePlate} so the caller can
 * fill its own form fields.
 */
export function DriverPicker({ onPick, disabled = false }) {
  const { query, setQuery, results, loading, open, setOpen } = usePeopleSearch(
    '/api/admin/drivers/suggest',
    2
  );
  const ref = useOutsideClose(() => setOpen(false));

  return (
    <PickerShell open={open} innerRef={ref}>
      <div className="input-group input-group-lg">
        <span className="input-group-text bg-white">
          <i className="fas fa-id-card text-primary" />
        </span>
        <input
          className="form-control"
          placeholder="Kayıtlı şoför ara — ad, telefon veya plaka"
          value={query}
          disabled={disabled}
          autoComplete="off"
          onChange={(e) => {
            setQuery(e.target.value);
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
      <div className="form-text">
        Daha önce kullandığınız şoförler; seçince bilgiler otomatik dolar. Yeni şoförü aşağıya elle yazmanız
        yeterli, kayıt sonrası rehbere eklenir.
      </div>
      {open && (
        <Dropdown>
          {!loading && results.length === 0 && (
            <div className="list-group-item text-muted small">
              {query.trim().length >= 2
                ? 'Eşleşen şoför yok — aşağıya elle yazabilirsiniz.'
                : 'Henüz kayıtlı şoför yok.'}
            </div>
          )}
          {results.map((d) => (
            <button
              type="button"
              key={d.id}
              className="list-group-item list-group-item-action"
              onClick={() => {
                onPick(d);
                setQuery('');
                setOpen(false);
              }}
            >
              <div className="d-flex justify-content-between align-items-start gap-2">
                <div className="min-w-0">
                  <div className="fw-semibold">{d.name}</div>
                  <small className="text-muted">
                    {d.phone ? formatPhoneForDisplay(d.phone) : 'Telefon yok'}
                    {d.vehiclePlate ? ` · ${d.vehiclePlate}` : ''}
                  </small>
                </div>
                {d.transferCount > 0 && (
                  <span className="badge bg-light text-dark border flex-shrink-0">
                    {d.transferCount} transfer
                  </span>
                )}
              </div>
            </button>
          ))}
        </Dropdown>
      )}
    </PickerShell>
  );
}

const formatDate = (value) =>
  value
    ? new Date(value).toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' })
    : '';

/**
 * Recipient picker fed by past customer deliveries, so a repeat customer is entered once and
 * reused — which also stops the same person being stored under three spellings.
 */
export function PastCustomerPicker({ onPick, disabled = false }) {
  const { query, setQuery, results, loading, open, setOpen } = usePeopleSearch(
    '/api/admin/stock-transfers/customers',
    2
  );
  const ref = useOutsideClose(() => setOpen(false));

  return (
    <PickerShell open={open} innerRef={ref}>
      <div className="input-group input-group-lg">
        <span className="input-group-text bg-white">
          <i className="fas fa-clock-rotate-left text-info" />
        </span>
        <input
          className="form-control"
          placeholder="Önceki sevkiyatlardan müşteri ara — ad veya telefon"
          value={query}
          disabled={disabled}
          autoComplete="off"
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
        />
        {loading && (
          <span className="input-group-text bg-white">
            <span className="spinner-border spinner-border-sm text-info" />
          </span>
        )}
      </div>
      {open && (
        <Dropdown>
          {!loading && results.length === 0 && (
            <div className="list-group-item text-muted small">
              {query.trim().length >= 2
                ? 'Eşleşen müşteri yok — aşağıya elle yazabilirsiniz.'
                : 'Henüz sevkiyat yapılmış müşteri yok.'}
            </div>
          )}
          {results.map((c, i) => (
            <button
              type="button"
              key={`${c.name}-${c.phone || i}`}
              className="list-group-item list-group-item-action"
              onClick={() => {
                onPick(c);
                setQuery('');
                setOpen(false);
              }}
            >
              <div className="d-flex justify-content-between align-items-start gap-2">
                <div className="min-w-0">
                  <div className="fw-semibold">{c.name}</div>
                  <small className="text-muted d-block">
                    {c.phone ? formatPhoneForDisplay(c.phone) : 'Telefon yok'}
                  </small>
                  {c.address && (
                    <small className="text-muted d-block text-truncate" title={c.address}>
                      {c.address}
                    </small>
                  )}
                </div>
                <div className="text-end flex-shrink-0">
                  <span className="badge bg-light text-dark border">{c.deliveryCount} sevkiyat</span>
                  <div className="text-muted" style={{ fontSize: 11 }}>
                    {formatDate(c.lastDeliveryAt)}
                  </div>
                </div>
              </div>
            </button>
          ))}
        </Dropdown>
      )}
    </PickerShell>
  );
}
