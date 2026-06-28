import React, { useState, useEffect, useRef, useMemo } from 'react';
import axios from 'axios';

/**
 * Member-product picker for a product set (bundle).
 *
 * `members` is an array of { productId, name, sku, price, salePrice, quantity }.
 * Calls `onChange` with the next array on every mutation. Only SIMPLE products are
 * searchable (the backend filters productType=SIMPLE) so sets can't be nested, and
 * the set being edited (`excludeProductId`) and already-added members are hidden.
 */
const fmt = (v) =>
  new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(Number(v) || 0);

const effective = (m) =>
  m.salePrice && Number(m.salePrice) > 0 ? Number(m.salePrice) : Number(m.price) || 0;

const BundleMemberPicker = ({ members = [], onChange, excludeProductId }) => {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const debounceRef = useRef(null);
  const boxRef = useRef(null);
  const listRef = useRef(null);

  useEffect(() => {
    const onDocClick = (e) => {
      if (boxRef.current && !boxRef.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, []);

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    const q = query.trim();
    if (q.length < 2) {
      setResults([]);
      return undefined;
    }
    debounceRef.current = setTimeout(async () => {
      setLoading(true);
      try {
        const res = await axios.get('/api/products', {
          params: { search: q, size: 10, page: 0, productType: 'SIMPLE', sortBy: 'name', sortDir: 'asc' },
        });
        setResults(Array.isArray(res.data?.content) ? res.data.content : []);
      } catch {
        setResults([]);
      } finally {
        setLoading(false);
      }
    }, 300);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [query]);

  // Products that can still be added: not the set itself, not an existing member.
  const visible = useMemo(
    () => results.filter((p) => p.id !== excludeProductId && !members.some((m) => m.productId === p.id)),
    [results, excludeProductId, members]
  );

  // Keep the highlighted row in range whenever the result list changes.
  useEffect(() => {
    setActiveIndex((i) => (i >= visible.length ? 0 : i));
  }, [visible.length]);

  // Scroll the highlighted row into view during keyboard navigation.
  useEffect(() => {
    const el = listRef.current?.querySelector(`[data-idx="${activeIndex}"]`);
    if (el) el.scrollIntoView({ block: 'nearest' });
  }, [activeIndex]);

  const addMember = (p) => {
    if (!p || p.id === excludeProductId) return;
    if (members.some((m) => m.productId === p.id)) return;
    onChange([
      ...members,
      { productId: p.id, name: p.name, sku: p.sku, price: p.price, salePrice: p.salePrice, quantity: 1 },
    ]);
    setQuery('');
    setResults([]);
    setOpen(false);
  };

  const onSearchKeyDown = (e) => {
    if (!open || visible.length === 0) {
      if (e.key === 'ArrowDown') setOpen(true);
      return;
    }
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveIndex((i) => Math.min(i + 1, visible.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveIndex((i) => Math.max(i - 1, 0));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      addMember(visible[activeIndex]);
    } else if (e.key === 'Escape') {
      setOpen(false);
    }
  };

  const removeMember = (productId) => onChange(members.filter((m) => m.productId !== productId));

  const setQty = (productId, qty) =>
    onChange(members.map((m) => (m.productId === productId ? { ...m, quantity: Math.max(1, qty) } : m)));

  const toggleGift = (productId) =>
    onChange(members.map((m) => (m.productId === productId ? { ...m, isGift: !m.isGift } : m)));

  const move = (index, dir) => {
    const next = [...members];
    const target = index + dir;
    if (target < 0 || target >= next.length) return;
    [next[index], next[target]] = [next[target], next[index]];
    onChange(next);
  };

  const membersTotal = members.reduce((sum, m) => sum + (m.isGift ? 0 : effective(m) * (m.quantity || 1)), 0);

  return (
    <div className="card border-0 shadow-sm mb-3" style={{ borderRadius: 12 }}>
      <div className="card-body">
        <h6 className="fw-bold mb-1">
          <i className="fas fa-layer-group me-2 text-info" />
          Set Üyeleri
        </h6>
        <p className="text-muted small mb-3">
          Bu setin içine girecek ürünleri arayıp ekleyin ve her biri için adet belirleyin.
        </p>

        {/* Search */}
        <div className="position-relative mb-3" ref={boxRef}>
          <div className="input-group">
            <span className="input-group-text bg-transparent">
              <i className="fas fa-search text-secondary" />
            </span>
            <input
              type="text"
              className="form-control"
              placeholder="Ürün adı, SKU ile ara (en az 2 karakter)..."
              value={query}
              role="combobox"
              aria-controls="bundle-member-results"
              aria-expanded={open && query.trim().length >= 2}
              aria-autocomplete="list"
              onChange={(e) => {
                setQuery(e.target.value);
                setOpen(true);
              }}
              onFocus={() => setOpen(true)}
              onKeyDown={onSearchKeyDown}
            />
          </div>
          {open && query.trim().length >= 2 && (
            <div
              ref={listRef}
              id="bundle-member-results"
              role="listbox"
              className="list-group position-absolute w-100 shadow border rounded bg-white"
              style={{ zIndex: 1056, maxHeight: 264, overflowY: 'auto', top: 'calc(100% + 4px)' }}
            >
              {/* Slim loading bar keeps the previous results in place instead of
                  flashing them away on every keystroke. */}
              {loading && (
                <div
                  className="progress position-sticky top-0"
                  style={{ height: 2, borderRadius: 0, zIndex: 1 }}
                >
                  <div className="progress-bar progress-bar-striped progress-bar-animated w-100" />
                </div>
              )}
              {visible.length === 0 ? (
                <div className="list-group-item text-muted small border-0">
                  {loading ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-2" />
                      Aranıyor...
                    </>
                  ) : (
                    'Sonuç bulunamadı.'
                  )}
                </div>
              ) : (
                visible.map((p, idx) => (
                  <button
                    key={p.id}
                    type="button"
                    data-idx={idx}
                    className={`list-group-item list-group-item-action d-flex justify-content-between align-items-center gap-2 border-0 ${
                      idx === activeIndex ? 'active' : ''
                    }`}
                    style={{ minHeight: 52 }}
                    onMouseEnter={() => setActiveIndex(idx)}
                    onClick={() => addMember(p)}
                  >
                    <span className="text-start min-w-0 flex-grow-1">
                      <span className="d-block fw-medium text-truncate">{p.name}</span>
                      <small className={idx === activeIndex ? 'text-white-50' : 'text-muted'}>
                        SKU: {p.sku}
                      </small>
                    </span>
                    <span
                      className={`fw-bold flex-shrink-0 ${idx === activeIndex ? 'text-white' : 'text-primary'}`}
                      style={{ whiteSpace: 'nowrap' }}
                    >
                      {fmt(effective(p))}
                    </span>
                  </button>
                ))
              )}
            </div>
          )}
        </div>

        {/* Selected members */}
        {members.length === 0 ? (
          <div className="alert alert-warning py-2 mb-0 small">
            <i className="fas fa-exclamation-triangle me-2" />
            Henüz üye ürün eklenmedi. Bir set en az bir ürün içermelidir.
          </div>
        ) : (
          <>
            <div className="list-group mb-2">
              {members.map((m, i) => (
                <div key={m.productId} className="list-group-item d-flex align-items-center gap-2">
                  <div className="btn-group-vertical" role="group">
                    <button
                      type="button"
                      className="btn btn-sm btn-light py-0"
                      disabled={i === 0}
                      onClick={() => move(i, -1)}
                      title="Yukarı taşı"
                    >
                      <i className="fas fa-chevron-up" />
                    </button>
                    <button
                      type="button"
                      className="btn btn-sm btn-light py-0"
                      disabled={i === members.length - 1}
                      onClick={() => move(i, 1)}
                      title="Aşağı taşı"
                    >
                      <i className="fas fa-chevron-down" />
                    </button>
                  </div>
                  <div className="flex-grow-1 min-w-0">
                    <div className="fw-medium text-truncate">
                      {m.name}
                      {m.isGift && (
                        <span className="badge bg-success ms-2" style={{ fontSize: 10 }}>
                          <i className="fas fa-gift me-1" />
                          Hediye
                        </span>
                      )}
                    </div>
                    <small className="text-muted">
                      SKU: {m.sku} · {m.isGift ? 'Ücretsiz' : fmt(effective(m))}
                    </small>
                  </div>
                  <button
                    type="button"
                    className={`btn btn-sm ${m.isGift ? 'btn-success' : 'btn-outline-success'}`}
                    onClick={() => toggleGift(m.productId)}
                    title={m.isGift ? 'Hediye işaretini kaldır' : 'Hediye olarak işaretle'}
                  >
                    <i className="fas fa-gift" />
                  </button>
                  <div className="d-flex align-items-center gap-1">
                    <label className="small text-muted mb-0 me-1">Adet</label>
                    <input
                      type="number"
                      min="1"
                      className="form-control form-control-sm"
                      style={{ width: 70 }}
                      value={m.quantity}
                      onChange={(e) => setQty(m.productId, parseInt(e.target.value, 10) || 1)}
                    />
                  </div>
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-danger"
                    onClick={() => removeMember(m.productId)}
                    title="Çıkar"
                  >
                    <i className="fas fa-trash" />
                  </button>
                </div>
              ))}
            </div>
            <div className="d-flex justify-content-between align-items-center px-1">
              <span className="text-muted small">{members.length} ürün</span>
              <span className="small">
                Üyelerin toplam fiyatı: <strong>{fmt(membersTotal)}</strong>
              </span>
            </div>
          </>
        )}
      </div>
    </div>
  );
};

export default BundleMemberPicker;
