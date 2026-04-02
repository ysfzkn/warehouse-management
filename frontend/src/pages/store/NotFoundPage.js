import React from 'react';
import { Link } from 'react-router-dom';
import { FiSearch, FiHome } from 'react-icons/fi';

export default function NotFoundPage() {
  return (
    <div className="container my-5 text-center" style={{ maxWidth: 500, padding: 'var(--sp-8) var(--sp-4)' }}>
      <div style={{ fontSize: '6rem', fontWeight: 'var(--font-bold)', color: 'var(--color-gray-200)', lineHeight: 1 }}>
        404
      </div>
      <h1 className="h4 fw-bold mt-3" style={{ color: 'var(--color-gray-800)' }}>Sayfa Bulunamadı</h1>
      <p style={{ color: 'var(--color-gray-500)', marginTop: 'var(--sp-3)', marginBottom: 'var(--sp-6)' }}>
        Aradığınız sayfa mevcut değil veya kaldırılmış olabilir.
      </p>
      <div className="d-flex gap-3 justify-content-center flex-wrap">
        <Link to="/store" className="btn btn-primary d-inline-flex align-items-center gap-2">
          <FiHome size={16} /> Ana Sayfa
        </Link>
        <Link to="/store/kategori/tumu" className="btn btn-outline-secondary d-inline-flex align-items-center gap-2">
          <FiSearch size={16} /> Ürün Ara
        </Link>
      </div>
    </div>
  );
}
