import React from 'react';

/**
 * Ürün fiyatını gösterir. price hem indirimsiz hem indirimli (üstü çizili) görünür.
 *
 * Edge case'ler:
 *   - price null/undefined/0: "Fiyat için iletişime geçin" mesajı (büyük tire yerine açıklayıcı)
 *   - salePrice >= price: indirim göstermez
 *
 * Props:
 *   price        — normal fiyat (number)
 *   salePrice    — indirim fiyatı (number, opsiyonel)
 *   size         — 'normal' | 'large'
 *   showVat      — "KDV Dahil" notu göster
 */
export default function PriceDisplay({ price, salePrice, size = 'normal', showVat = false }) {
  const isLarge = size === 'large';

  // Price yoksa açıklayıcı mesaj göster — büyük tire kullanıcı için anlamsız.
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
