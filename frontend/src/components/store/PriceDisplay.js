import React from 'react';

/**
 * Displays a product price. The price is shown both at full and discounted
 * (struck-through) rates.
 *
 * Edge cases:
 *   - price null/undefined/0: "Contact us for the price" message (explanatory instead of a long dash)
 *   - salePrice >= price: no discount shown
 *
 * Props:
 *   price        — regular price (number)
 *   salePrice    — discount price (number, optional)
 *   size         — 'normal' | 'large'
 *   showVat      — show the "VAT Included" note
 */
export default function PriceDisplay({ price, salePrice, size = 'normal', showVat = false }) {
  const isLarge = size === 'large';

  // Show an explanatory message when there is no price — a long dash is meaningless to the user.
  if (price == null || price <= 0) {
    return (
      <div className="store-price-display">
        <span style={{
          fontSize: isLarge ? '0.95rem' : '0.85rem',
          color: '#6b7280',
          fontStyle: 'italic',
          fontWeight: 500,
        }}>
          <i className="fas fa-info-circle me-1" aria-hidden="true" />
          Fiyat için iletişime geçin
        </span>
      </div>
    );
  }

  const formatPrice = (p) =>
    new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY', minimumFractionDigits: 2 }).format(p);

  const hasDiscount = !!(salePrice && salePrice > 0 && salePrice < price);

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
