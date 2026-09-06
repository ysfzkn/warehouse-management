import React, { useEffect, useMemo, useState } from 'react';

/**
 * Depo stoklarından ürün seçme — arama kutusu, sonuç listesi, adet ve ekle.
 *
 * Transfer ekranındaki seçicinin davranışını taşıyor: bir açılır listede yüzlerce ürün
 * arasında kaydırmak yerine yazarak daraltmak. Depoda üç ürün de olabilir üç yüz de, ve
 * ikinci durumda `<select>` kullanılamaz hâle geliyor.
 *
 * Liste bilerek kırpılıyor: eşleşen her satırı basmak, arama kutusuna her harf
 * yazıldığında yüzlerce satırı yeniden çizmek demek — kutu takılıyor. Aranan ürün zaten
 * ilk satırlarda çıkıyor, gerisi "daha fazla göster" ile geliyor.
 */

const INITIAL_VISIBLE = 25;
const PAGE_STEP = 25;

const lower = (value) => (value ? String(value).toLocaleLowerCase('tr-TR') : '');

export default function WarehouseStockPicker({
  stocks,
  loading = false,
  disabled = false,
  /** Bu satırdan kaç adet kaldığı — çağıran, sepete eklenenleri düşerek hesaplıyor. */
  availableFor,
  onAdd,
  emptyHint = 'Bu depoda stok bulunmuyor.',
}) {
  const [query, setQuery] = useState('');
  const [selectedId, setSelectedId] = useState('');
  const [quantity, setQuantity] = useState('');
  const [visible, setVisible] = useState(INITIAL_VISIBLE);
  const [error, setError] = useState('');

  const filtered = useMemo(() => {
    const q = query.trim();
    if (!q) return stocks;
    const needle = lower(q);
    return stocks.filter((s) => {
      const p = s.product || {};
      return (
        lower(p.name).includes(needle) || lower(p.sku).includes(needle) || lower(p.barcode).includes(needle)
      );
    });
  }, [stocks, query]);

  useEffect(() => {
    setVisible(INITIAL_VISIBLE);
  }, [query, stocks]);

  const shown = filtered.slice(0, visible);
  const selected = stocks.find((s) => String(s.id) === String(selectedId)) || null;
  const selectedRemaining = selected ? availableFor(selected) : 0;

  const add = () => {
    if (!selected) {
      setError('Önce listeden bir ürün seçin.');
      return;
    }
    const parsed = parseInt(quantity, 10);
    if (!parsed || parsed < 1) {
      setError('Adet en az 1 olmalıdır.');
      return;
    }
    if (parsed > selectedRemaining) {
      setError(`Bu üründen çıkılabilecek en fazla adet: ${selectedRemaining}.`);
      return;
    }
    setError('');
    onAdd(selected, parsed);
    setSelectedId('');
    setQuantity('');
    setQuery('');
  };

  if (disabled) {
    return <div className="text-muted small">Önce çıkış deposunu seçin.</div>;
  }

  return (
    <div>
      <div className="input-group mb-2">
        <span className="input-group-text bg-light">
          <i className="fas fa-search text-muted"></i>
        </span>
        <input
          type="text"
          className="form-control"
          placeholder="Ürün adı, stok kodu veya barkod ile ara…"
          value={query}
          disabled={loading || stocks.length === 0}
          onChange={(e) => setQuery(e.target.value)}
        />
        {query && (
          <button type="button" className="btn btn-outline-secondary" onClick={() => setQuery('')}>
            Temizle
          </button>
        )}
      </div>

      <div className="d-flex justify-content-between small text-muted mb-2">
        <span>{stocks.length > 0 ? `${stocks.length} ürün` : 'Stok listesi hazır değil'}</span>
        {query && <span>{filtered.length} sonuç</span>}
      </div>

      {loading ? (
        <div className="d-flex align-items-center gap-2 py-3 text-muted">
          <span className="spinner-border spinner-border-sm text-primary"></span>
          Stoklar yükleniyor…
        </div>
      ) : filtered.length === 0 ? (
        <div className="alert alert-light border py-3 mb-0 small">
          <i className="fas fa-info-circle me-2 text-primary"></i>
          {query ? 'Arama kriterine uygun ürün bulunamadı.' : emptyHint}
        </div>
      ) : (
        <>
          <div
            className="table-responsive border rounded bg-white"
            style={{ maxHeight: 260, overflowY: 'auto' }}
          >
            <table className="table table-hover table-sm mb-0 align-middle">
              <thead className="table-light" style={{ position: 'sticky', top: 0, zIndex: 1 }}>
                <tr>
                  <th style={{ width: 38 }}></th>
                  <th className="small">Ürün</th>
                  <th className="small d-none d-sm-table-cell" style={{ width: 130 }}>
                    Stok Kodu
                  </th>
                  <th className="small text-center" style={{ width: 70 }}>
                    Kalan
                  </th>
                </tr>
              </thead>
              <tbody>
                {shown.map((s) => {
                  const remaining = availableFor(s);
                  const isSelected = String(selectedId) === String(s.id);
                  return (
                    <tr
                      key={s.id}
                      className={isSelected ? 'table-primary' : undefined}
                      style={{ cursor: remaining > 0 ? 'pointer' : 'not-allowed' }}
                      onClick={() => {
                        if (remaining <= 0) return;
                        setSelectedId(s.id);
                        setError('');
                      }}
                    >
                      <td className="text-center">
                        <input
                          type="radio"
                          className="form-check-input"
                          name="stockPick"
                          checked={isSelected}
                          disabled={remaining <= 0}
                          onChange={() => {
                            setSelectedId(s.id);
                            setError('');
                          }}
                        />
                      </td>
                      <td>
                        <div className="small fw-semibold">{s.product?.name || '-'}</div>
                        <small className="text-muted d-sm-none">{s.product?.sku}</small>
                      </td>
                      <td className="small d-none d-sm-table-cell text-muted">{s.product?.sku || '-'}</td>
                      <td className="text-center">
                        <span
                          className={`badge rounded-pill ${
                            remaining > 0
                              ? 'bg-success-subtle text-success-emphasis border border-success-subtle'
                              : 'bg-secondary-subtle text-secondary-emphasis border border-secondary-subtle'
                          }`}
                        >
                          {remaining}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {filtered.length > visible && (
            <button
              type="button"
              className="btn btn-link btn-sm px-0 mt-1"
              onClick={() => setVisible((v) => v + PAGE_STEP)}
            >
              {filtered.length - visible} ürün daha göster
            </button>
          )}
        </>
      )}

      {error && (
        <div className="alert alert-warning py-2 px-3 small mt-2 mb-0">
          <i className="fas fa-triangle-exclamation me-1"></i>
          {error}
        </div>
      )}

      <div className="row g-2 align-items-end mt-2">
        <div className="col-sm">
          <label className="form-label small mb-1">Seçilen ürün</label>
          <div className="form-control form-control-sm bg-light text-truncate">
            {selected ? (
              <>
                {selected.product?.name}
                <span className="text-muted"> · kalan {selectedRemaining}</span>
              </>
            ) : (
              <span className="text-muted">Listeden seçin</span>
            )}
          </div>
        </div>
        <div className="col-6 col-sm-3">
          <label className="form-label small mb-1">Adet</label>
          <input
            type="number"
            min="1"
            max={selectedRemaining || undefined}
            className="form-control form-control-sm"
            value={quantity}
            disabled={!selected}
            onChange={(e) => setQuantity(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                // Formun kendi submit'ini tetiklemesin: burada Enter "kalemi ekle"
                // demek, "çıkışı kaydet" değil.
                e.preventDefault();
                add();
              }
            }}
          />
        </div>
        <div className="col-6 col-sm-auto d-grid">
          <label className="form-label small mb-1 d-none d-sm-block">&nbsp;</label>
          <button type="button" className="btn btn-sm btn-outline-primary" onClick={add}>
            <i className="fas fa-plus me-1"></i>
            Ekle
          </button>
        </div>
      </div>
    </div>
  );
}
