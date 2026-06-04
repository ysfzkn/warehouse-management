import React from 'react';
import ProductCard from './ProductCard';

export default function ProductGrid({ products, onAddToCart, viewMode = 'grid' }) {
  if (!products || products.length === 0) {
    return <div className="text-center py-5 text-muted"><p>Bu kriterlere uygun ürün bulunamadı.</p></div>;
  }
  const colClass = viewMode === 'list' ? 'col-12' : 'col-6 col-md-4 col-lg-3';
  return (
    <div className="row g-3">
      {products.map(product => (
        <div key={product.id} className={colClass}>
          <ProductCard product={product} onAddToCart={onAddToCart} />
        </div>
      ))}
    </div>
  );
}
