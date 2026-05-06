import React from 'react';

export default function PriceDisplay({ price, salePrice, size = 'normal', showVat = false }) {
  const formatPrice = (p) => {
    if (p == null) return '—';
    return new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY', minimumFractionDigits: 2 }).format(p);
  };
  const hasDiscount = !!(salePrice && salePrice > 0 && salePrice < price);
  const isLarge = size === 'large';

  return (
    <div className="store-price-display">
      <div className="d-flex align-items-baseline gap-2 flex-wrap">
        {hasDiscount && (
          <span style={{
            fontSize: isLarge ? '1.1rem' : '0.8rem',
            color: '#9ca3af',
            textDecoration: 'line-through',
            fontWeight: 400,
          }}>{formatPrice(price)}</span>
        )}
        <span style={{
          fontSize: isLarge ? '1.75rem' : '1rem',
          fontWeight: 700,
          color: hasDiscount ? '#dc2626' : 'var(--store-primary, #2563eb)',
        }}>{formatPrice(hasDiscount ? salePrice : price)}</span>
      </div>
      {showVat && <span style={{fontSize:'0.75rem',color:'#9ca3af'}}>KDV Dahil</span>}
    </div>
  );
}
