import React from 'react';

export default function StockBadge({ status, className = '' }) {
  const config = {
    'IN_STOCK': { label: 'Stokta Var', cls: 'in-stock' },
    'LOW_STOCK': { label: 'Son Birkaç Ürün', cls: 'low-stock' },
    'OUT_OF_STOCK': { label: 'Tükendi', cls: 'out-of-stock' },
  };
  const c = config[status] || config['OUT_OF_STOCK'];
  return <span className={`stock-badge ${c.cls} ${className}`}>{c.label}</span>;
}
