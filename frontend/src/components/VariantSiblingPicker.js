import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';

/**
 * Color-variant picker — links the same product sold in different colors.
 *
 * `siblings` is an array of { productId, name, sku, colorName, colorHexCode }. Calls
 * `onChange` with the next array on every mutation. Only SIMPLE products are searchable
 * (a set has no single color), and the product being edited (`excludeProductId`) plus
 * already-added siblings are hidden. Selected products are shown as chips with a color dot.
 *
 * The grouping is symmetric on the backend: linking A→B also links B→A, so editing
 * either product shows the same group.
 */
const colorDot = (hex, size = 14) => (
  <span
    style={{
      display: 'inline-block',
      width: size,
      height: size,
      borderRadius: '50%',
      backgroundColor: hex || '#e2e8f0',
      border: '1px solid rgba(0,0,0,0.2)',
      flexShrink: 0,
    }}
  />
);

const VariantSiblingPicker = ({ siblings = [], onChange, excludeProductId }) => {
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

  const addSibling = (p) => {
    if (!p || p.id === excludeProductId) return;
    if (siblings.some((s) => s.productId === p.id)) return;
    onChange([
      ...siblings,
      {
        productId: p.id,
        name: p.name,
        sku: p.sku,
        colorName: p.colorName || p.color?.name || null,
        colorHexCode: p.color?.hexCode || p.colorHexCode || null,
      },
    ]);
    setQuery('');
    setResults([]);
    setOpen(false);
  };

  const removeSibling = (productId) => onChange(siblings.filter((s) => s.productId !== productId));

  return (
    <div className="card border-0 shadow-sm mb-3" style={{ borderRadius: 12 }}>
      <div className="card-body">
        <h6 className="fw-bold mb-1">
          <i className="fas fa-palette me-2 text-primary" />
          Renk Varyantları
        </h6>
        <p className="text-muted small mb-3">
          Bu ürünün farklı renklerini buraya ekleyin. Eklenen ürünler, ürün sayfasında renk seçeneği olarak
          gösterilir ve müşteri renkler arasında geçiş yapabilir.
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
              placeholder="Aynı ürünün başka rengini ara (en az 2 karakter)..."
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
                  .filter((p) => p.id !== excludeProductId && !siblings.some((s) => s.productId === p.id))
                  .map((p) => (
                    <button
                      key={p.id}
                      type="button"
                      className="list-group-item list-group-item-action d-flex justify-content-between align-items-center"
                      onClick={() => addSibling(p)}
                    >
                      <span className="text-start d-flex align-items-center gap-2">
                        {colorDot(p.color?.hexCode || p.colorHexCode)}
                        <span>
                          <span className="d-block fw-medium">{p.name}</span>
                          <small className="text-muted">
                            SKU: {p.sku}
                            {p.colorName || p.color?.name ? ` · ${p.colorName || p.color?.name}` : ''}
                          </small>
                        </span>
                      </span>
                    </button>
                  ))
              )}
            </div>
          )}
        </div>

        {/* Selected siblings */}
        {siblings.length === 0 ? (
          <div className="text-muted small">
            <i className="fas fa-info-circle me-2" />
            Henüz renk varyantı eklenmedi.
          </div>
        ) : (
          <div className="d-flex flex-wrap gap-2">
            {siblings.map((s) => (
              <span
                key={s.productId}
                className="badge bg-light text-dark border d-flex align-items-center gap-2 py-2 px-2"
                style={{ fontSize: 13, fontWeight: 500 }}
              >
                {colorDot(s.colorHexCode)}
                <span className="text-truncate" style={{ maxWidth: 180 }}>
                  {s.name}
                  {s.colorName ? ` · ${s.colorName}` : ''}
                </span>
                <button
                  type="button"
                  className="btn btn-sm btn-link text-danger p-0 ms-1"
                  onClick={() => removeSibling(s.productId)}
                  title="Çıkar"
                >
                  <i className="fas fa-times" />
                </button>
              </span>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default VariantSiblingPicker;
