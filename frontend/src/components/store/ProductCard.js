import React from 'react';
import { Link } from 'react-router-dom';
import StockBadge from './StockBadge';
import PriceDisplay from './PriceDisplay';
import { FiShoppingCart } from 'react-icons/fi';

export default function ProductCard({ product, onAddToCart }) {
  const hasDiscount = product.salePrice && product.salePrice > 0 && product.salePrice < product.price;
  const discountPercent = hasDiscount ? Math.round((1 - product.salePrice / product.price) * 100) : 0;

  return (
    <div className="store-product-card">
      <Link to={`/store/urun/${product.slug}`} className="text-decoration-none">
        <div className="card-img-wrapper">
          {product.primaryImageUrl ? (
            <img src={product.primaryImageUrl} alt={product.name} loading="lazy" />
          ) : (
            <div className="card-img-placeholder">
              <FiShoppingCart size={32} />
            </div>
          )}
          {/* Badges */}
          <div className="card-badges">
            {product.isNew && <span className="card-badge card-badge-new" aria-label="Yeni ürün">Yeni</span>}
            {hasDiscount && <span className="card-badge card-badge-sale" aria-label="İndirimli">%{discountPercent}</span>}
          </div>
        </div>
      </Link>
      <div className="card-body">
        {product.brandName && <span className="card-brand">{product.brandName}</span>}
        <Link to={`/store/urun/${product.slug}`} className="text-decoration-none">
          <p className="product-name">{product.name}</p>
        </Link>
        <div className="card-price-area">
          <PriceDisplay price={product.price} salePrice={product.salePrice} />
          <StockBadge status={product.stockStatus} />
        </div>
        <button
          className="btn btn-add-cart w-100"
          onClick={(e) => { e.preventDefault(); onAddToCart && onAddToCart(product.id); }}
          disabled={product.stockStatus === 'OUT_OF_STOCK'}
          aria-disabled={product.stockStatus === 'OUT_OF_STOCK' ? 'true' : undefined}
          aria-label={`${product.name} sepete ekle`}
        >
          <FiShoppingCart size={14} />
          <span>{product.stockStatus === 'OUT_OF_STOCK' ? 'Tükendi' : 'Sepete Ekle'}</span>
        </button>
      </div>
    </div>
  );
}
