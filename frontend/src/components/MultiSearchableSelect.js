import React, { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import axios from 'axios';

/**
 * Multi-select searchable dropdown backed by an API.
 *
 * - Fetches options via GET searchEndpoint?name=<query>
 * - Fetches missing selected option labels via GET searchEndpoint/<id>
 * - Displays selected items as removable chips
 *
 * Props:
 * - values: number[] (selected ids)
 * - onChange: (nextValues: number[], selectedOptionsById: Record<number, any>) => void
 * - searchEndpoint: string
 * - placeholder?: string
 * - disabled?: boolean
 * - label?: string
 * - renderOption?: (opt) => ReactNode
 * - getOptionLabel?: (opt) => string
 */
const MultiSearchableSelect = ({
  label,
  values,
  onChange,
  searchEndpoint,
  placeholder = 'Ara...',
  disabled = false,
  renderOption,
  getOptionLabel = (opt) => opt?.name || '',
}) => {
  const normalizedValues = useMemo(() => Array.isArray(values) ? values.filter(v => v != null) : [], [values]);
  const valuesSet = useMemo(() => new Set(normalizedValues.map(v => Number(v))), [normalizedValues]);

  const [query, setQuery] = useState('');
  const [options, setOptions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [selectedById, setSelectedById] = useState({});

  const containerRef = useRef(null);
  const inputRef = useRef(null);
  const [dropdownRect, setDropdownRect] = useState(null);

  // Close when clicking outside
  useEffect(() => {
    const handler = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener('click', handler);
    return () => document.removeEventListener('click', handler);
  }, []);

  // Fetch missing selected labels (when values change)
  useEffect(() => {
    let cancelled = false;
    const missing = normalizedValues
      .map(v => Number(v))
      .filter(id => !Number.isNaN(id))
      .filter(id => !selectedById[id]);

    if (!missing.length) return;

    const fetchMissing = async () => {
      try {
        const results = await Promise.all(
          missing.map(async (id) => {
            try {
              const endpoint = searchEndpoint.includes('/search')
                ? searchEndpoint.replace('/search', `/${id}`)
                : `${searchEndpoint}/${id}`;
              const res = await axios.get(endpoint);
              return res.data || null;
            } catch {
              return null;
            }
          })
        );

        if (cancelled) return;
        setSelectedById(prev => {
          const next = { ...prev };
          for (const opt of results) {
            if (opt && opt.id != null) {
              next[Number(opt.id)] = opt;
            }
          }
          return next;
        });
      } catch {
        // noop
      }
    };

    fetchMissing();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchEndpoint, normalizedValues.join(',')]);

  // Fetch options when open and query changes
  useEffect(() => {
    if (!open) return;
    const controller = new AbortController();
    const fetch = async () => {
      try {
        setLoading(true);
        const res = await axios.get(searchEndpoint, { params: { name: query }, signal: controller.signal });
        setOptions(Array.isArray(res.data) ? res.data : []);
      } catch (e) {
        if (!axios.isCancel(e)) {
          // noop
        }
      } finally {
        setLoading(false);
      }
    };
    const timer = setTimeout(fetch, 250);
    return () => {
      controller.abort();
      clearTimeout(timer);
    };
  }, [query, open, searchEndpoint]);

  // Track input position for portal dropdown placement
  useEffect(() => {
    if (!open) return;
    const updateRect = () => {
      if (!inputRef.current) return;
      const rect = inputRef.current.getBoundingClientRect();
      setDropdownRect({
        top: rect.bottom + 2,
        left: rect.left,
        width: rect.width
      });
    };
    updateRect();
    window.addEventListener('scroll', updateRect, true);
    window.addEventListener('resize', updateRect);
    return () => {
      window.removeEventListener('scroll', updateRect, true);
      window.removeEventListener('resize', updateRect);
    };
  }, [open]);

  const commitChange = (nextValues, nextSelectedById) => {
    const deduped = Array.from(new Set((nextValues || []).map(v => Number(v)).filter(v => !Number.isNaN(v))));
    setSelectedById(nextSelectedById);
    onChange(deduped, nextSelectedById);
  };

  const toggle = (opt) => {
    const id = opt?.id != null ? Number(opt.id) : null;
    if (id == null || Number.isNaN(id)) return;
    const nextSelectedById = { ...selectedById, [id]: opt };

    if (valuesSet.has(id)) {
      const nextValues = normalizedValues.filter(v => Number(v) !== id);
      commitChange(nextValues, nextSelectedById);
    } else {
      const nextValues = [...normalizedValues, id];
      commitChange(nextValues, nextSelectedById);
    }
  };

  const clearAll = () => {
    commitChange([], selectedById);
  };

  const removeOne = (id) => {
    const idNum = Number(id);
    if (Number.isNaN(idNum)) return;
    const nextValues = normalizedValues.filter(v => Number(v) !== idNum);
    commitChange(nextValues, selectedById);
  };

  const selectedOptions = normalizedValues
    .map(id => selectedById[Number(id)] || { id, name: String(id) });

  return (
    <div className="mb-0" ref={containerRef}>
      {label && (
        <div className="d-flex justify-content-between align-items-center mb-1">
          <label className="form-label mb-0">{label}</label>
          {normalizedValues.length > 0 && (
            <button type="button" className="btn btn-sm btn-link p-0" onClick={clearAll} disabled={disabled}>
              Temizle
            </button>
          )}
        </div>
      )}

      <div style={{ position: 'relative', zIndex: 1 }}>
        <div className="input-group">
          <span className="input-group-text bg-transparent">
            <i className="fas fa-warehouse text-secondary"></i>
          </span>
          <input
            type="text"
            className="form-control"
            placeholder={placeholder}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onFocus={() => setOpen(true)}
            disabled={disabled}
            ref={inputRef}
          />
          <button
            type="button"
            className="btn btn-outline-secondary"
            onClick={() => setOpen(o => !o)}
            disabled={disabled}
            aria-label="Depo listesini aç/kapat"
          >
            <i className={`fas ${open ? 'fa-chevron-up' : 'fa-chevron-down'}`}></i>
          </button>
        </div>

        {normalizedValues.length > 0 && (
          <div className="d-flex flex-wrap gap-2 mt-2">
            {selectedOptions.map(opt => (
              <span
                key={String(opt.id)}
                className="badge rounded-pill text-bg-light border d-inline-flex align-items-center"
                title={getOptionLabel(opt)}
                style={{ maxWidth: '100%' }}
              >
                <span className="text-truncate" style={{ maxWidth: 220 }}>
                  {getOptionLabel(opt)}
                </span>
                <button
                  type="button"
                  className="btn btn-sm btn-link ms-1 p-0 text-decoration-none"
                  onClick={() => removeOne(opt.id)}
                  disabled={disabled}
                  aria-label="Seçimi kaldır"
                  style={{ lineHeight: 1 }}
                >
                  <i className="fas fa-times"></i>
                </button>
              </span>
            ))}
          </div>
        )}

        {open && dropdownRect && createPortal(
          (
            <div
              className="list-group shadow-lg border"
              style={{
                position: 'fixed',
                zIndex: 100000,
                maxHeight: 280,
                overflowY: 'auto',
                top: dropdownRect.top,
                left: dropdownRect.left,
                width: dropdownRect.width,
                backgroundColor: '#fff',
              }}
            >
              {loading && (
                <div className="list-group-item text-center">
                  <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                  <span className="ms-2">Yükleniyor...</span>
                </div>
              )}

              {!loading && options.length === 0 && (
                <div className="list-group-item text-muted">Sonuç bulunamadı</div>
              )}

              {!loading && options.map(opt => {
                const id = opt?.id != null ? Number(opt.id) : null;
                const checked = id != null && valuesSet.has(id);
                return (
                  <button
                    type="button"
                    key={String(opt.id)}
                    className={`list-group-item list-group-item-action d-flex align-items-center ${checked ? 'active' : ''}`}
                    onMouseDown={() => {
                      toggle(opt);
                    }}
                    onClick={(e) => e.preventDefault()}
                  >
                    <input
                      className="form-check-input me-2"
                      type="checkbox"
                      checked={checked}
                      readOnly
                      tabIndex={-1}
                    />
                    <div className="flex-grow-1 text-start">
                      {renderOption ? renderOption(opt) : getOptionLabel(opt)}
                    </div>
                  </button>
                );
              })}
            </div>
          ),
          document.body
        )}
      </div>
    </div>
  );
};

export default MultiSearchableSelect;

