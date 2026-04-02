import React from 'react';

export function SkeletonBlock({ width, height, className = '' }) {
  return (
    <div
      className={`skeleton ${className}`}
      style={{ width: width || '100%', height: height || '16px' }}
      aria-hidden="true"
    />
  );
}

export function SkeletonProductCard() {
  return (
    <div className="store-product-card" aria-hidden="true">
      <div className="card-img-wrapper">
        <div className="skeleton" style={{ width: '100%', height: '100%' }} />
      </div>
      <div className="card-body">
        <SkeletonBlock height="14px" className="mb-2" />
        <SkeletonBlock width="60%" height="14px" className="mb-3" />
        <SkeletonBlock width="40%" height="20px" className="mb-2" />
        <SkeletonBlock height="10px" width="50px" className="mb-3" />
        <SkeletonBlock height="36px" style={{ borderRadius: 'var(--radius-md)' }} />
      </div>
    </div>
  );
}

export function SkeletonProductGrid({ count = 8 }) {
  const colClass = 'col-6 col-md-4 col-lg-3';
  return (
    <div className="row g-3" aria-busy="true" aria-label="Yükleniyor">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className={colClass}>
          <SkeletonProductCard />
        </div>
      ))}
    </div>
  );
}

export function SkeletonProductDetail() {
  return (
    <div className="row g-4" aria-busy="true" aria-label="Yükleniyor">
      <div className="col-md-6">
        <div className="skeleton" style={{ aspectRatio: '1', borderRadius: 'var(--radius-lg)' }} />
        <div className="d-flex gap-2 mt-3">
          {[1, 2, 3, 4].map(i => (
            <div key={i} className="skeleton" style={{ width: 64, height: 64, borderRadius: 'var(--radius-md)' }} />
          ))}
        </div>
      </div>
      <div className="col-md-6">
        <SkeletonBlock height="28px" className="mb-2" />
        <SkeletonBlock width="40%" height="16px" className="mb-4" />
        <SkeletonBlock width="50%" height="32px" className="mb-3" />
        <SkeletonBlock width="80px" height="24px" className="mb-4" />
        <SkeletonBlock height="16px" className="mb-2" />
        <SkeletonBlock height="16px" className="mb-2" />
        <SkeletonBlock width="70%" height="16px" className="mb-4" />
        <SkeletonBlock height="48px" style={{ borderRadius: 'var(--radius-md)' }} />
      </div>
    </div>
  );
}
