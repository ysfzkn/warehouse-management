import React from 'react';

const FilterChips = ({ chips = [], onClearAll, className = '' }) => {
  if (!chips.length) return null;
  return (
    <div className={`d-flex flex-wrap align-items-center gap-2 ${className}`}>
      <span className="text-muted me-1">Aktif filtreler:</span>
      {chips.map((chip, idx) => (
        <span key={idx} className="badge text-bg-light border d-flex align-items-center">
          {chip.icon && (<i className={`${chip.icon} me-1`}></i>)}
          {chip.label}
          {chip.onClear && (
            <button type="button" className="btn btn-sm btn-link ms-2 p-0" onClick={chip.onClear}>
              <i className="fas fa-times"></i>
            </button>
          )}
        </span>
      ))}
      {onClearAll && (
        <button type="button" className="btn btn-sm btn-outline-secondary ms-1" onClick={onClearAll}>
          Tümünü Temizle
        </button>
      )}
    </div>
  );
};

export default FilterChips;


