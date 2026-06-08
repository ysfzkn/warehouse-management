import React, { useState, useEffect, useRef } from 'react';
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
  const debounceRef = useRef(null);
  const boxRef = useRef(null);

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

  const removeMember = (productId) => onChange(members.filter((m) => m.productId !== productId));

  const setQty = (productId, qty) =>
    onChange(members.map((m) => (m.productId === productId ? { ...m, quantity: Math.max(1, qty) } : m)));

  const move = (index, dir) => {
    const next = [...members];
    const target = index + dir;
    if (target < 0 || target >= next.length) return;
    [next[index], next[target]] = [next[target], next[index]];
    onChange(next);
  };

  const membersTotal = members.reduce((sum, m) => sum + effective(m) * (m.quantity || 1), 0);

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
              onChange={(e) => {
                setQuery(e.target.value);
                setOpen(true);
              }}
              onFocus={() => setOpen(true)}
            />
          </div>
          {open && query.trim().length >= 2 && (
            <div
              className="list-group position-absolute w-100 shadow"
              style={{ zIndex: 30, maxHeight: 280, overflowY: 'auto', top: '100%' }}
            >
              {loading ? (
                <div className="list-group-item text-center text-muted">
                  <span className="spinner-border spinner-border-sm me-2" />
                  Aranıyor...
                </div>
              ) : results.length === 0 ? (
                <div className="list-group-item text-muted small">Sonuç bulunamadı.</div>
              ) : (
                results
                  .filter((p) => p.id !== excludeProductId && !members.some((m) => m.productId === p.id))
                  .map((p) => (
                    <button
                      key={p.id}
                      type="button"
                      className="list-group-item list-group-item-action d-flex justify-content-between align-items-center"
                      onClick={() => addMember(p)}
                    >
                      <span className="text-start">
                        <span className="d-block fw-medium">{p.name}</span>
                        <small className="text-muted">SKU: {p.sku}</small>
                      </span>
                      <span className="fw-bold text-primary">{fmt(effective(p))}</span>
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
                    <div className="fw-medium text-truncate">{m.name}</div>
                    <small className="text-muted">
                      SKU: {m.sku} · {fmt(effective(m))}
                    </small>
                  </div>
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
