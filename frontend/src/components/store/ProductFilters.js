import React, { useState, useEffect, useMemo } from 'react';
import axios from 'axios';
import { FiX, FiSearch } from 'react-icons/fi';

/**
 * Storefront product filter panel (Phase 2 UX improvement).
 *
 * Features:
 *  - Inline search input when the brand/color lists are long
 *  - "Show more" toggle to collapse long lists
 *  - Active filter badge in the heading
 *  - "Clear filters" button
 *  - Mobile responsive (drawer mode)
 *
 * Multi-brand selection requires expanding the backend query, so for now only
 * single selection is supported; it can later be extended via brandIds=[...].
 */
export default function ProductFilters({ filters, onFilterChange, isOpen, onClose }) {
  const [brands, setBrands] = useState([]);
  const [colors, setColors] = useState([]);
  const [brandSearch, setBrandSearch] = useState('');
  const [colorSearch, setColorSearch] = useState('');
  const [brandsExpanded, setBrandsExpanded] = useState(false);
  const [colorsExpanded, setColorsExpanded] = useState(false);

  useEffect(() => {
    axios.get('/api/store/brands').then(r => setBrands(r.data || [])).catch(() => {});
    axios.get('/api/store/colors').then(r => setColors(r.data || [])).catch(() => {});
  }, []);

  const activeFilterCount = useMemo(() => {
    let n = 0;
    if (filters.brandId) n++;
    if (filters.colorId) n++;
    if (filters.minPrice) n++;
    if (filters.maxPrice) n++;
    return n;
  }, [filters]);

  // Brand filter: search + collapse
  const filteredBrands = useMemo(() => {
    const q = brandSearch.trim().toLowerCase();
    const list = q ? brands.filter(b => (b.name || '').toLowerCase().includes(q)) : brands;
    return brandsExpanded || q ? list : list.slice(0, 8);
  }, [brands, brandSearch, brandsExpanded]);

  const filteredColors = useMemo(() => {
    const q = colorSearch.trim().toLowerCase();
    const list = q ? colors.filter(c => (c.name || '').toLowerCase().includes(q)) : colors;
    return colorsExpanded || q ? list : list.slice(0, 8);
  }, [colors, colorSearch, colorsExpanded]);

  return (
    <div className={`store-filters ${isOpen ? 'open' : ''}`} role="region" aria-label="Ürün filtreleri">
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h6 className="mb-0">
          Filtreler
          {activeFilterCount > 0 && (
            <span className="badge bg-primary ms-2" style={{ fontSize: 11 }}>{activeFilterCount}</span>
          )}
        </h6>
        <button className="btn btn-sm d-md-none" onClick={onClose} aria-label="Filtreleri kapat"><FiX /></button>
      </div>

      {/* ── Brand ── */}
      <div className="store-filter-group">
        <h6 className="d-flex justify-content-between align-items-center">
          <span>Marka</span>
          {filters.brandId && (
            <button type="button" className="btn btn-link btn-sm p-0 text-muted" style={{ fontSize: 11 }}
              onClick={() => onFilterChange({ ...filters, brandId: null })}>Temizle</button>
          )}
        </h6>
        {brands.length > 8 && (
          <div className="position-relative mb-2">
            <FiSearch size={12} className="position-absolute" style={{ left: 8, top: '50%', transform: 'translateY(-50%)', color: '#9ca3af' }} />
            <input type="text" className="form-control form-control-sm" placeholder="Marka ara..."
              value={brandSearch} onChange={e => setBrandSearch(e.target.value)}
              style={{ paddingLeft: 26 }} aria-label="Markalar içinde ara" />
          </div>
        )}
        <div style={{ maxHeight: brandsExpanded ? 320 : 'none', overflowY: brandsExpanded ? 'auto' : 'visible' }}>
          {filteredBrands.map(b => (
            <div key={b.id} className="form-check">
              <input className="form-check-input" type="radio" name="brand" id={`brand-${b.id}`}
                checked={filters.brandId === b.id}
                onChange={() => onFilterChange({ ...filters, brandId: filters.brandId === b.id ? null : b.id })} />
              <label className="form-check-label small" htmlFor={`brand-${b.id}`}>{b.name}</label>
            </div>
          ))}
        </div>
        {brands.length > 8 && !brandSearch && (
          <button type="button" className="btn btn-link btn-sm p-0 text-decoration-none"
            onClick={() => setBrandsExpanded(v => !v)}>
            {brandsExpanded ? '↑ Daha az göster' : `↓ Tümünü göster (${brands.length})`}
          </button>
        )}
      </div>

      {/* ── Color ── */}
      <div className="store-filter-group">
        <h6 className="d-flex justify-content-between align-items-center">
          <span>Renk</span>
          {filters.colorId && (
            <button type="button" className="btn btn-link btn-sm p-0 text-muted" style={{ fontSize: 11 }}
              onClick={() => onFilterChange({ ...filters, colorId: null })}>Temizle</button>
          )}
        </h6>
        {colors.length > 8 && (
          <div className="position-relative mb-2">
            <FiSearch size={12} className="position-absolute" style={{ left: 8, top: '50%', transform: 'translateY(-50%)', color: '#9ca3af' }} />
            <input type="text" className="form-control form-control-sm" placeholder="Renk ara..."
              value={colorSearch} onChange={e => setColorSearch(e.target.value)}
              style={{ paddingLeft: 26 }} aria-label="Renkler içinde ara" />
          </div>
        )}
        <div style={{ maxHeight: colorsExpanded ? 320 : 'none', overflowY: colorsExpanded ? 'auto' : 'visible' }}>
          {filteredColors.map(c => (
            <div key={c.id} className="form-check">
              <input className="form-check-input" type="radio" name="color" id={`color-${c.id}`}
                checked={filters.colorId === c.id}
                onChange={() => onFilterChange({ ...filters, colorId: filters.colorId === c.id ? null : c.id })} />
              <label className="form-check-label small" htmlFor={`color-${c.id}`}>{c.name}</label>
            </div>
          ))}
        </div>
        {colors.length > 8 && !colorSearch && (
          <button type="button" className="btn btn-link btn-sm p-0 text-decoration-none"
            onClick={() => setColorsExpanded(v => !v)}>
            {colorsExpanded ? '↑ Daha az göster' : `↓ Tümünü göster (${colors.length})`}
          </button>
        )}
      </div>

      {/* ── Price ── */}
      <div className="store-filter-group">
        <h6 className="d-flex justify-content-between align-items-center">
          <span>Fiyat Aralığı</span>
          {(filters.minPrice || filters.maxPrice) && (
            <button type="button" className="btn btn-link btn-sm p-0 text-muted" style={{ fontSize: 11 }}
              onClick={() => onFilterChange({ ...filters, minPrice: null, maxPrice: null })}>Temizle</button>
          )}
        </h6>
        <div className="store-price-range">
          <input type="number" min="0" className="form-control form-control-sm" placeholder="Min ₺"
            value={filters.minPrice || ''}
            onChange={e => onFilterChange({ ...filters, minPrice: e.target.value || null })}
            aria-label="Minimum fiyat" />
          <span aria-hidden="true">—</span>
          <input type="number" min="0" className="form-control form-control-sm" placeholder="Max ₺"
            value={filters.maxPrice || ''}
            onChange={e => onFilterChange({ ...filters, maxPrice: e.target.value || null })}
            aria-label="Maksimum fiyat" />
        </div>
        {/* Quick-select chips */}
        <div className="d-flex gap-1 flex-wrap mt-2">
          {[
            { label: '0-500', min: 0, max: 500 },
            { label: '500-2K', min: 500, max: 2000 },
            { label: '2-10K', min: 2000, max: 10000 },
            { label: '10K+', min: 10000, max: null },
          ].map(p => (
            <button key={p.label} type="button"
              className={`btn btn-sm ${
                Number(filters.minPrice) === p.min && (p.max ? Number(filters.maxPrice) === p.max : !filters.maxPrice)
                  ? 'btn-primary' : 'btn-outline-secondary'
              }`}
              style={{ fontSize: 11, padding: '2px 8px' }}
              onClick={() => onFilterChange({ ...filters, minPrice: p.min, maxPrice: p.max })}>
              ₺{p.label}
            </button>
          ))}
        </div>
      </div>

      {activeFilterCount > 0 && (
        <button className="btn btn-outline-secondary btn-sm w-100 mt-2" onClick={() => onFilterChange({})}>
          <FiX size={12} className="me-1" />Tüm Filtreleri Temizle ({activeFilterCount})
        </button>
      )}
    </div>
  );
}
