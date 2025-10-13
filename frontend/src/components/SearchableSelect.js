import React, { useEffect, useMemo, useRef, useState } from 'react';
import axios from 'axios';

const SearchableSelect = ({ label, value, onChange, searchEndpoint, placeholder = 'Ara...', disabled = false, renderOption }) => {
  const [query, setQuery] = useState('');
  const [options, setOptions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const containerRef = useRef(null);

  useEffect(() => {
    const handler = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  useEffect(() => {
    if (!open) return;
    const controller = new AbortController();
    const fetch = async () => {
      try {
        setLoading(true);
        const res = await axios.get(searchEndpoint, { params: { name: query }, signal: controller.signal });
        setOptions(res.data || []);
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

  const selectedOption = useMemo(() => options.find(o => o.id === value) || null, [options, value]);

  return (
    <div className="mb-3" ref={containerRef}>
      {label && (
        <label className="form-label">
          {label}
        </label>
      )}
      <div className="position-relative">
        <input
          type="text"
          className="form-control"
          placeholder={placeholder}
          value={open ? query : (selectedOption ? (selectedOption.name || '') : '')}
          onChange={(e) => setQuery(e.target.value)}
          onFocus={() => setOpen(true)}
          disabled={disabled}
        />
        {open && (
          <div className="list-group position-absolute w-100 shadow-sm" style={{ zIndex: 1050, maxHeight: 240, overflowY: 'auto' }}>
            {loading && (
              <div className="list-group-item text-center">
                <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                <span className="ms-2">Yükleniyor...</span>
              </div>
            )}
            {!loading && options.length === 0 && (
              <div className="list-group-item text-muted">Sonuç bulunamadı</div>
            )}
            {!loading && options.map(opt => (
              <button
                type="button"
                key={opt.id}
                className={`list-group-item list-group-item-action ${value === opt.id ? 'active' : ''}`}
                onClick={() => {
                  onChange(opt.id, opt);
                  setOpen(false);
                }}
              >
                {renderOption ? renderOption(opt) : (opt.name || '')}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default SearchableSelect;


