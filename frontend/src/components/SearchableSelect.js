import React, { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import axios from 'axios';

const SearchableSelect = ({ label, value, onChange, searchEndpoint, placeholder = 'Ara...', disabled = false, renderOption, wrapperClassName = 'mb-3', allowClear = false, clearText = 'Temizle' }) => {
  const [query, setQuery] = useState('');
  const [options, setOptions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [selectedLabel, setSelectedLabel] = useState('');
  const containerRef = useRef(null);
  const inputRef = useRef(null);
  const [dropdownRect, setDropdownRect] = useState(null);

  // Fetch initial value's label when component mounts with a value
  useEffect(() => {
    if (value) {
      // Check if we already have this option in our list
      const existing = options.find(o => o.id === value);
      if (existing) {
        setSelectedLabel(existing.name || '');
      } else {
        // Fetch from API
        const fetchInitialValue = async () => {
          try {
            const endpoint = searchEndpoint.includes('/search') ? searchEndpoint.replace('/search', `/${value}`) : `${searchEndpoint}/${value}`;
            const res = await axios.get(endpoint);
            if (res.data) {
              setSelectedLabel(res.data.name || '');
              setOptions(prev => {
                // Add if not already in list
                if (!prev.find(o => o.id === res.data.id)) {
                  return [res.data, ...prev];
                }
                return prev;
              });
            }
          } catch (e) {
            console.error('Error fetching initial value:', e);
          }
        };
        fetchInitialValue();
      }
    } else {
      // Value is null, clear label
      setSelectedLabel('');
    }
  }, [value, searchEndpoint]);

  useEffect(() => {
    const handler = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener('click', handler);
    return () => document.removeEventListener('click', handler);
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

  return (
    <div className={wrapperClassName} ref={containerRef}>
      {label && (
        <div className="d-flex justify-content-between align-items-center mb-1">
          <label className="form-label mb-0">
            {label}
          </label>
          {allowClear && (
            <button
              type="button"
              className="btn btn-sm btn-link p-0"
              onClick={() => onChange(null, null)}
              disabled={disabled}
            >
              {clearText}
            </button>
          )}
        </div>
      )}
      <div style={{position: 'relative', zIndex: open ? 1 : 1}}>
        <input
          type="text"
          className="form-control"
          placeholder={placeholder}
          value={open ? query : selectedLabel}
          onChange={(e) => setQuery(e.target.value)}
          onFocus={() => setOpen(true)}
          disabled={disabled}
          ref={inputRef}
        />
        {open && dropdownRect && createPortal(
          (
            <div 
              className="list-group shadow-lg border"
              style={{
                position: 'fixed',
                zIndex: 100000,
                maxHeight: 240,
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
              {!loading && options.map(opt => (
                <button
                  type="button"
                  key={opt.id}
                  className={`list-group-item list-group-item-action ${value === opt.id ? 'active' : ''}`}
                  onMouseDown={() => {
                    onChange(opt.id, opt);
                    setSelectedLabel(opt.name || '');
                    setQuery('');
                    setOpen(false);
                  }}
                  onClick={(e) => {
                    e.preventDefault();
                  }}
                >
                  {renderOption ? renderOption(opt) : (opt.name || '')}
                </button>
              ))}
            </div>
          ),
          document.body
        )}
      </div>
    </div>
  );
};

export default SearchableSelect;


