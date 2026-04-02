import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { FiX } from 'react-icons/fi';

export default function ProductFilters({ filters, onFilterChange, isOpen, onClose }) {
  const [brands, setBrands] = useState([]);
  const [colors, setColors] = useState([]);

  useEffect(() => {
    axios.get('/api/store/brands').then(r => setBrands(r.data || [])).catch(() => {});
    axios.get('/api/store/colors').then(r => setColors(r.data || [])).catch(() => {});
  }, []);

  return (
    <div className={`store-filters ${isOpen ? 'open' : ''}`}>
      <div className="d-flex justify-content-between align-items-center d-md-none mb-3">
        <h6 className="mb-0">Filtreler</h6>
        <button className="btn btn-sm" onClick={onClose} aria-label="Filtreleri kapat"><FiX /></button>
      </div>
      <div className="store-filter-group">
        <h6>Marka</h6>
        {brands.map(b => (
          <div key={b.id} className="form-check">
            <input className="form-check-input" type="radio" name="brand" id={`brand-${b.id}`}
              checked={filters.brandId === b.id} onChange={() => onFilterChange({ ...filters, brandId: filters.brandId === b.id ? null : b.id })} />
            <label className="form-check-label small" htmlFor={`brand-${b.id}`}>{b.name}</label>
          </div>
        ))}
      </div>
      <div className="store-filter-group">
        <h6>Renk</h6>
        {colors.map(c => (
          <div key={c.id} className="form-check">
            <input className="form-check-input" type="radio" name="color" id={`color-${c.id}`}
              checked={filters.colorId === c.id} onChange={() => onFilterChange({ ...filters, colorId: filters.colorId === c.id ? null : c.id })} />
            <label className="form-check-label small" htmlFor={`color-${c.id}`}>{c.name}</label>
          </div>
        ))}
      </div>
      <div className="store-filter-group">
        <h6>Fiyat Araligi</h6>
        <div className="store-price-range">
          <input type="number" className="form-control form-control-sm" placeholder="Min" value={filters.minPrice || ''}
            onChange={e => onFilterChange({ ...filters, minPrice: e.target.value || null })} aria-label="Minimum fiyat" />
          <span>-</span>
          <input type="number" className="form-control form-control-sm" placeholder="Max" value={filters.maxPrice || ''}
            onChange={e => onFilterChange({ ...filters, maxPrice: e.target.value || null })} aria-label="Maksimum fiyat" />
        </div>
      </div>
      {(filters.brandId || filters.colorId || filters.minPrice || filters.maxPrice) && (
        <button className="btn btn-outline-secondary btn-sm w-100 mt-2" onClick={() => onFilterChange({})}>Filtreleri Temizle</button>
      )}
    </div>
  );
}
