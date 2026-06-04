import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';

/**
 * Search-based stock removal entry point ("Stok Çıkar" toolbar button).
 *
 * Mirrors the "Yeni Stok / Stok Ekle" flow but for decreasing stock: the user
 * searches a product/stock, picks one, and we hand the selected stock to the
 * existing QuickStockAdjustModal (type="remove") — which already enforces the
 * admin security code on submit. No new backend endpoint needed.
 */
const StockRemoveSearchModal = ({ onSelect, onClose }) => {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const debounceRef = useRef(null);
  const inputRef = useRef(null);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    const q = query.trim();
    if (q.length < 2) {
      setResults([]);
      setSearched(false);
      return undefined;
    }
    debounceRef.current = setTimeout(async () => {
      setLoading(true);
      try {
        const res = await axios.get('/api/stocks', {
          params: { search: q, size: 20, page: 0, status: 'all', sortBy: 'warehouse', sortDir: 'asc' },
        });
        const content = Array.isArray(res.data?.content) ? res.data.content : [];
        setResults(content);
      } catch {
        setResults([]);
      } finally {
        setLoading(false);
        setSearched(true);
      }
    }, 350);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [query]);

  return (
    <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
      <div className="modal-dialog modal-dialog-centered modal-lg">
        <div className="modal-content">
          <div className="modal-header bg-danger text-white">
            <h5 className="modal-title">
              <i className="fas fa-search-minus me-2" />
              Stok Çıkar — Ürün Ara
            </h5>
            <button
              type="button"
              className="btn-close btn-close-white"
              onClick={onClose}
              aria-label="Kapat"
            />
          </div>
          <div className="modal-body">
            <div className="input-group input-group-lg mb-3">
              <span className="input-group-text bg-transparent">
                <i className="fas fa-search text-secondary" />
              </span>
              <input
                ref={inputRef}
                type="text"
                className="form-control"
                placeholder="Ürün adı, SKU veya barkod ile ara..."
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
            </div>

            {loading && (
              <div className="text-center py-4">
                <span className="spinner-border text-danger" />
              </div>
            )}

            {!loading && searched && results.length === 0 && (
              <div className="alert alert-warning mb-0">
                <i className="fas fa-info-circle me-2" />"{query.trim()}" için stok bulunamadı.
              </div>
            )}

            {!loading && results.length > 0 && (
              <div className="list-group" style={{ maxHeight: 400, overflowY: 'auto' }}>
                {results.map((stock) => {
                  const available = stock.availableQuantity ?? 0;
                  const disabled = available <= 0;
                  return (
                    <button
                      key={stock.id}
                      type="button"
                      className="list-group-item list-group-item-action d-flex justify-content-between align-items-center"
                      onClick={() => !disabled && onSelect(stock)}
                      disabled={disabled}
                      title={disabled ? 'Kullanılabilir stok yok' : 'Stok çıkarmak için seç'}
                    >
                      <div className="text-start">
                        <div className="fw-bold">{stock.product?.name || '—'}</div>
                        <small className="text-muted">
                          <i className="fas fa-warehouse me-1" />
                          {stock.warehouse?.name || '—'}
                          {stock.product?.sku && <span className="ms-2">SKU: {stock.product.sku}</span>}
                        </small>
                      </div>
                      <div className="text-end">
                        <span className={`badge ${available > 0 ? 'bg-success' : 'bg-secondary'}`}>
                          Kullanılabilir: {available}
                        </span>
                        <div>
                          <small className="text-muted">Toplam: {stock.quantity ?? 0}</small>
                        </div>
                      </div>
                    </button>
                  );
                })}
              </div>
            )}

            {!searched && !loading && (
              <p className="text-muted text-center py-4 mb-0">
                <i className="fas fa-keyboard me-2" />
                Aramak için en az 2 karakter yazın.
              </p>
            )}
          </div>
          <div className="modal-footer">
            <button type="button" className="btn btn-secondary" onClick={onClose}>
              <i className="fas fa-times me-1" />
              Kapat
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default StockRemoveSearchModal;
