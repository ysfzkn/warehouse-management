import React from 'react';

export default function PriceDisplay({ price, salePrice, size = 'normal', showVat = false }) {
  const formatPrice = (p) => {
    if (p == null) return '—';
    return new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY', minimumFractionDigits: 2 }).format(p);
  };
  const hasDiscount = salePrice && salePrice > 0 && salePrice < price;
  const mainClass = size === 'large' ? 'price-main' : 'product-price';
  const oldClass = size === 'large' ? 'price-old' : 'product-price-old';

  return (
    <div>
      <div className="d-flex align-items-baseline gap-2 flex-wrap">
        {hasDiscount && <span className={oldClass}>{formatPrice(price)}</span>}
        <span className={mainClass}>{formatPrice(hasDiscount ? salePrice : price)}</span>
      </div>
      {showVat && <span className="price-vat">KDV Dahil</span>}
    </div>
  );
}
