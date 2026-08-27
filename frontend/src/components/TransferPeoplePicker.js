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

/**
 * A failed lookup must not read as "there is nothing here" — that is how a permission or network
 * problem spent a long time looking like an empty directory. The message is deliberately plain,
 * because the operator's next move is the same either way: type the details in by hand.
 */
const failureMessage = (error) => {
  const status = error?.response?.status;
  if (status === 401 || status === 403)
    return 'Bu listeyi görme yetkiniz yok — bilgileri elle yazabilirsiniz.';
  return 'Liste alınamadı. Bağlantınızı kontrol edin veya bilgileri elle yazın.';
};

const usePeopleSearch = (endpoint, minChars) => {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!open) return undefined;
    if (query.trim().length > 0 && query.trim().length < minChars) {
      // The pending request was just cancelled, so the spinner has to be cleared here too —
      // otherwise a query trimmed back to a single character spins forever over an empty list.
      setResults([]);
      setError(null);
      setLoading(false);
      return undefined;
    }
    setLoading(true);
    let cancelled = false;
    const timer = setTimeout(() => {
      axios
        .get(endpoint, { params: { q: query.trim() || undefined } })
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
  }, [query, open, endpoint, minChars]);

  return { query, setQuery, results, loading, error, open, setOpen };
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
  const { query, setQuery, results, loading, error, open, setOpen } = usePeopleSearch(
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
          {error && <div className="list-group-item text-danger small">{error}</div>}
          {!loading && !error && results.length === 0 && (
            <div className="list-group-item text-muted small">
              {query.trim().length === 0
                ? 'Henüz kayıtlı şoför yok.'
                : query.trim().length < 2
                  ? 'Aramak için en az 2 karakter yazın.'
                  : 'Eşleşen şoför yok — aşağıya elle yazabilirsiniz.'}
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

/**
 * Plate picker.
 *
 * A plate is no longer free text: it comes from the vehicle directory, with the vehicles already
 * assigned to the chosen driver offered first — that is what the operator wants nine times out of
 * ten. A plate that is genuinely new can still be registered inline, because a vehicle usually
 * turns up mid-transfer and sending the operator to another screen would push them back to typing
 * whatever they like.
 */
export function VehiclePicker({ value, driverId, onPick, invalid = false }) {
  const [assigned, setAssigned] = useState([]);
  const [all, setAll] = useState([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState(null);
  const [open, setOpen] = useState(false);
  const ref = useOutsideClose(() => setOpen(false));

  // The driver's own vehicles are a short, stable list — fetched once per driver.
  useEffect(() => {
    if (!driverId) {
      setAssigned([]);
      return undefined;
    }
    let cancelled = false;
    axios
      .get(`/api/admin/vehicles/by-driver/${driverId}`)
      .then((r) => !cancelled && setAssigned(Array.isArray(r.data) ? r.data : []))
      .catch(() => !cancelled && setAssigned([]));
    return () => {
      cancelled = true;
    };
  }, [driverId]);

  useEffect(() => {
    if (!open) return undefined;
    setLoading(true);
    let cancelled = false;
    const timer = setTimeout(() => {
      axios
        .get('/api/admin/vehicles/suggest', { params: { q: query.trim() || undefined } })
        .then((r) => {
          if (cancelled) return;
          setAll(Array.isArray(r.data) ? r.data : []);
          setError(null);
        })
        .catch((e) => {
          if (cancelled) return;
          setAll([]);
          setError(failureMessage(e));
        })
        .finally(() => !cancelled && setLoading(false));
    }, 250);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [query, open]);

  const normalize = (plate) => (plate || '').replace(/[^A-Za-z0-9]/g, '').toUpperCase();
  const assignedKeys = new Set(assigned.map((v) => normalize(v.plate)));
  const matches = (v) => !query.trim() || normalize(v.plate).includes(normalize(query));
  const visibleAssigned = assigned.filter(matches);
  const others = all.filter((v) => !assignedKeys.has(normalize(v.plate)));

  const typedKey = normalize(query);
  const alreadyKnown = [...assigned, ...all].some((v) => normalize(v.plate) === typedKey);
  const canCreate = typedKey.length >= 4 && !alreadyKnown;

  const choose = (vehicle) => {
    onPick(vehicle);
    setQuery('');
    setOpen(false);
  };

  const createVehicle = async () => {
    setCreating(true);
    try {
      const res = await axios.post('/api/admin/vehicles', { plate: query.trim() });
      setError(null);
      choose(res.data);
    } catch (e) {
      // Shown inside the dropdown: clicking "add this plate" and getting nothing back is
      // indistinguishable from a dead button.
      setError(e?.response?.data?.message || failureMessage(e));
      setOpen(true);
    } finally {
      setCreating(false);
    }
  };

  return (
    <PickerShell open={open} innerRef={ref}>
      <div className="input-group input-group-lg">
        <span className="input-group-text bg-white">
          <i className="fas fa-truck-front text-secondary" />
        </span>
        <input
          className={`form-control text-uppercase ${invalid ? 'is-invalid' : value ? 'is-valid' : ''}`}
          placeholder="Plaka seçin veya yazın"
          value={open ? query : value || ''}
          autoComplete="off"
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          onFocus={() => {
            setQuery('');
            setOpen(true);
          }}
        />
        {value && !open && (
          <button
            type="button"
            className="btn btn-outline-secondary"
            title="Plakayı temizle"
            onClick={() => onPick(null)}
          >
            <i className="fas fa-times" />
          </button>
        )}
        {loading && (
          <span className="input-group-text bg-white">
            <span className="spinner-border spinner-border-sm text-secondary" />
          </span>
        )}
      </div>
      {open && (
        <Dropdown>
          {error && <div className="list-group-item text-danger small">{error}</div>}
          {visibleAssigned.length > 0 && (
            <div className="list-group-item bg-light small fw-semibold text-muted py-1">
              Bu şoföre atanmış araçlar
            </div>
          )}
          {visibleAssigned.map((v) => (
            <button
              type="button"
              key={`assigned-${v.id}`}
              className="list-group-item list-group-item-action"
              onClick={() => choose(v)}
            >
              <div className="d-flex justify-content-between align-items-center gap-2">
                <span className="fw-semibold">{v.plate}</span>
                <span className="badge bg-primary-subtle text-primary border">Atanmış</span>
              </div>
              {v.brandModel && <small className="text-muted">{v.brandModel}</small>}
            </button>
          ))}

          {others.length > 0 && (
            <div className="list-group-item bg-light small fw-semibold text-muted py-1">Diğer araçlar</div>
          )}
          {others.map((v) => (
            <button
              type="button"
              key={`other-${v.id}`}
              className="list-group-item list-group-item-action"
              onClick={() => choose(v)}
            >
              <div className="d-flex justify-content-between align-items-center gap-2">
                <span className="fw-semibold">{v.plate}</span>
                {v.transferCount > 0 && (
                  <span className="badge bg-light text-dark border">{v.transferCount} transfer</span>
                )}
              </div>
              {v.brandModel && <small className="text-muted">{v.brandModel}</small>}
            </button>
          ))}

          {canCreate && (
            <button
              type="button"
              className="list-group-item list-group-item-action text-primary"
              disabled={creating}
              onClick={createVehicle}
            >
              {creating ? (
                <>
                  <span className="spinner-border spinner-border-sm me-2" />
                  Ekleniyor…
                </>
              ) : (
                <>
                  <i className="fas fa-plus me-2" />
                  <strong>{query.trim().toUpperCase()}</strong> plakasını yeni araç olarak ekle
                </>
              )}
            </button>
          )}

          {!loading && !error && visibleAssigned.length === 0 && others.length === 0 && !canCreate && (
            <div className="list-group-item text-muted small">
              Araç bulunamadı. Plakayı yazınca yeni araç olarak ekleyebilirsiniz.
            </div>
          )}
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
  const { query, setQuery, results, loading, error, open, setOpen } = usePeopleSearch(
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
          {error && <div className="list-group-item text-danger small">{error}</div>}
          {!loading && !error && results.length === 0 && (
            <div className="list-group-item text-muted small">
              {query.trim().length === 0
                ? 'Henüz sevkiyat yapılmış müşteri yok.'
                : query.trim().length < 2
                  ? 'Aramak için en az 2 karakter yazın.'
                  : 'Eşleşen müşteri yok — aşağıya elle yazabilirsiniz.'}
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
