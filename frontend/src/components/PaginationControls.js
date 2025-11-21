import React from 'react';
import { buildPageList } from '../utils/pagination';

const PaginationControls = ({ page, totalPages, onPageChange, className = '' }) => {
  if (!totalPages || totalPages <= 1) {
    return null;
  }

  const pages = buildPageList(page, totalPages);

  const changePage = (target) => {
    if (typeof onPageChange !== 'function') {
      return;
    }
    if (target === page || target < 0 || target >= totalPages) {
      return;
    }
    onPageChange(target);
  };

  return (
    <nav className={className} aria-label="Sayfalama">
      <ul className="pagination pagination-sm mb-0">
        <li className={`page-item ${page <= 0 ? 'disabled' : ''}`}>
          <button
            type="button"
            className="page-link"
            onClick={() => changePage(page - 1)}
            aria-label="Önceki sayfa"
          >
            &laquo;
          </button>
        </li>
        {pages.map((p) => (
          <li key={`pager-${p}`} className={`page-item ${p === page ? 'active' : ''}`}>
            <button
              type="button"
              className="page-link"
              onClick={() => changePage(p)}
              aria-current={p === page ? 'page' : undefined}
            >
              {p + 1}
            </button>
          </li>
        ))}
        <li className={`page-item ${page >= totalPages - 1 ? 'disabled' : ''}`}>
          <button
            type="button"
            className="page-link"
            onClick={() => changePage(page + 1)}
            aria-label="Sonraki sayfa"
          >
            &raquo;
          </button>
        </li>
      </ul>
    </nav>
  );
};

export default PaginationControls;

