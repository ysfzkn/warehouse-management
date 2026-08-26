import React, { useEffect, useState } from 'react';
import axios from 'axios';

/**
 * Matches a delivery recipient with an e-commerce customer record.
 *
 * The match is always optional: walk-in recipients simply have no account, and one can be
 * attached later once it turns out they do. The component only reports the pick — the parent
 * decides whether that means "fill the form" (create flow) or "PATCH the transfer" (later match).
 */
export default function CustomerLinkPicker({ customer, onPick, disabled = false, hint }) {
  const [search, setSearch] = useState('');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (search.trim().length < 2) {
      setResults([]);
      setLoading(false);
      return undefined;
    }
    setLoading(true);
    let cancelled = false;
    const timer = setTimeout(() => {
      axios
        .get('/api/admin/customers', { params: { search: search.trim(), page: 0, size: 8 } })
        .then((r) => !cancelled && setResults(r.data?.content || []))
        .catch(() => !cancelled && setResults([]))
        .finally(() => !cancelled && setLoading(false));
    }, 300);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [search]);

  if (customer) {
    return (
      <div className="d-flex align-items-center justify-content-between gap-2 border rounded-3 p-2 bg-light">
        <div className="d-flex align-items-center gap-2 min-w-0">
          <span className="badge bg-success">
            <i className="fas fa-user-check me-1" />
            E-ticaret kaydı
          </span>
          <div className="min-w-0">
            <div className="fw-semibold text-truncate">
              {customer.firstName} {customer.lastName}
            </div>
            <small className="text-muted text-truncate d-block">
              {customer.email || 'E-posta yok'}
              {customer.phone ? ` · ${customer.phone}` : ''}
            </small>
          </div>
        </div>
        {!disabled && (
          <button
            type="button"
            className="btn btn-sm btn-outline-secondary flex-shrink-0"
            onClick={() => {
              onPick(null);
              setSearch('');
              setOpen(false);
            }}
          >
            <i className="fas fa-link-slash me-1" />
            Kaldır
          </button>
        )}
      </div>
    );
  }

  return (
    // Own stacking context while the list is open — Bootstrap positions every `.input-group`
    // child, so a later form field would otherwise paint over the dropdown.
    <div className="position-relative" style={{ zIndex: open ? 1080 : 'auto' }}>
      <div className="input-group">
        <span className="input-group-text bg-white">
          <i className="fas fa-magnifying-glass" />
        </span>
        <input
          className="form-control"
          placeholder="Ad, telefon veya e-posta ile kayıtlı müşteri arayın"
          value={search}
          disabled={disabled}
          autoComplete="off"
          onChange={(e) => {
            setSearch(e.target.value);
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
        {hint || 'Alıcının e-ticaret hesabı yoksa boş bırakın; sonradan da eşleştirebilirsiniz.'}
      </div>
      {open && search.trim().length >= 2 && (
        <div
          className="list-group shadow-sm position-absolute w-100 mt-1"
          style={{ maxHeight: 240, overflowY: 'auto' }}
        >
          {!loading && results.length === 0 && (
            <div className="list-group-item text-muted small">
              Eşleşen kayıtlı müşteri bulunamadı — bu alıcı kayıtsız olabilir.
            </div>
          )}
          {results.map((item) => (
            <button
              type="button"
              key={item.id}
              className="list-group-item list-group-item-action"
              onClick={() => {
                onPick(item);
                setSearch('');
                setResults([]);
                setOpen(false);
              }}
            >
              <div className="fw-semibold">
                {item.firstName} {item.lastName}
              </div>
              <small className="text-muted">
                {item.phone || 'Telefon yok'} · {item.email || 'E-posta yok'}
              </small>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
