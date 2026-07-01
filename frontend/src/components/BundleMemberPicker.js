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
      setLoading(false);
      return undefined;
    }
    // Enter the loading state immediately (before the debounce) so the panel opens
    // at full height with skeleton rows instead of popping open from a single line.
    setLoading(true);
    debounceRef.current = setTimeout(async () => {
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

  // Reset the highlight to the top whenever a new result set arrives.
  useEffect(() => {
    setActiveIndex(0);
  }, [results]);

  // Scroll a row into view. Called ONLY from keyboard navigation — never from mouse
  // hover — so hovering can't trigger a scroll (which caused the list to jitter).
  const scrollRowIntoView = (idx) => {
    const el = listRef.current?.querySelector(`[data-idx="${idx}"]`);
    if (el) el.scrollIntoView({ block: 'nearest' });
  };

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
      const next = Math.min(activeIndex + 1, visible.length - 1);
      setActiveIndex(next);
      scrollRowIntoView(next);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      const next = Math.max(activeIndex - 1, 0);
      setActiveIndex(next);
      scrollRowIntoView(next);
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
              id="bundle-member-results"
              role="listbox"
              className="position-absolute w-100 bg-white border rounded-3 shadow-lg overflow-hidden"
              style={{ zIndex: 1056, top: 'calc(100% + 6px)' }}
            >
              {/* During a REFETCH (old results still shown) a slim top bar signals
                  loading without nudging the rows. The first search uses skeletons
                  below instead, so the panel never pops open from a single line. */}
              {loading && visible.length > 0 && (
                <div
                  className="progress position-absolute top-0 start-0 end-0"
                  style={{ height: 2, borderRadius: 0, zIndex: 2 }}
                >
                  <div className="progress-bar progress-bar-striped progress-bar-animated w-100" />
                </div>
              )}
              {/* Fixed row height (56) × 5 rows + container borders → whole rows only,
                  never a half-cut item at the bottom. */}
              <div ref={listRef} style={{ maxHeight: 282, overflowY: 'auto' }}>
                {loading && visible.length === 0 ? (
                  // Skeleton rows: open at a stable height instead of growing on load.
                  Array.from({ length: 5 }).map((_, i) => (
                    <div
                      key={`skeleton-${i}`}
                      className="d-flex align-items-center justify-content-between px-3"
                      style={{ height: 56, borderBottom: i < 4 ? '1px solid #f1f3f5' : undefined }}
                    >
                      <span className="flex-grow-1 me-3 placeholder-glow">
                        <span className="placeholder col-6 rounded d-block" style={{ height: 12 }} />
                        <span className="placeholder col-3 rounded d-block mt-2" style={{ height: 9 }} />
                      </span>
                      <span className="placeholder-glow">
                        <span className="placeholder rounded d-block" style={{ height: 12, width: 68 }} />
                      </span>
                    </div>
                  ))
                ) : visible.length === 0 ? (
                  <div className="d-flex align-items-center text-muted small px-3" style={{ height: 56 }}>
                    Sonuç bulunamadı.
                  </div>
                ) : (
                  visible.map((p, idx) => {
                    const active = idx === activeIndex;
                    return (
                      <button
                        key={p.id}
                        type="button"
                        role="option"
                        aria-selected={active}
                        data-idx={idx}
                        className="w-100 d-flex justify-content-between align-items-center gap-3 text-start border-0 bg-transparent px-3"
                        style={{
                          height: 56,
                          cursor: 'pointer',
                          // Subtle light-blue highlight with a left accent — always
                          // dark text, so it can never wash out to white-on-white
                          // (a store theme variable was doing that before).
                          background: active ? '#eef4ff' : undefined,
                          boxShadow: active ? 'inset 3px 0 0 #2563eb' : undefined,
                          borderBottom: idx < visible.length - 1 ? '1px solid #f1f3f5' : undefined,
                          transition: 'background-color 120ms ease',
                        }}
                        onMouseEnter={() => setActiveIndex(idx)}
                        onClick={() => addMember(p)}
                      >
                        <span className="min-w-0 flex-grow-1">
                          <span className="d-block fw-semibold text-truncate text-dark">{p.name}</span>
                          <small className="text-muted" style={{ fontSize: 12 }}>
                            SKU: {p.sku}
                          </small>
                        </span>
                        <span className="fw-bold flex-shrink-0 text-primary" style={{ whiteSpace: 'nowrap' }}>
                          {fmt(effective(p))}
                        </span>
                      </button>
                    );
                  })
                )}
              </div>
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
