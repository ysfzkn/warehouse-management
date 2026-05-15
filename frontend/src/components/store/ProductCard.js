import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import StockBadge from './StockBadge';
import PriceDisplay from './PriceDisplay';
import { FiShoppingCart, FiStar, FiHeart } from 'react-icons/fi';
import { useToast } from './Toast';
import { useWishlist } from './WishlistContext';

export default function ProductCard({ product, onAddToCart }) {
  const hasDiscount = !!(product.salePrice && product.salePrice > 0 && product.salePrice < product.price);
  const discountPercent = hasDiscount ? Math.round((1 - product.salePrice / product.price) * 100) : 0;
  const toast = useToast();
  const navigate = useNavigate();
  const wishlist = useWishlist();
  const wishlisted = wishlist.has(product.id);

  const toggleWishlist = async (e) => {
    e.preventDefault(); e.stopPropagation();
    const token = localStorage.getItem('customer_token');
    if (!token) { toast.warning('Favorilere eklemek için giriş yapın.'); navigate('/giris'); return; }
    try {
      const nowWished = await wishlist.toggle(product.id);
      if (nowWished) toast.success('Favorilere eklendi!');
      else toast.info('Favorilerden çıkarıldı.');
    } catch (err) { toast.error(err?.response?.data?.message || 'İşlem başarısız.'); }
  };

  return (
    <div className="store-product-card">
      <Link to={`/urun/${product.slug}`} className="text-decoration-none">
        <div className="card-img-wrapper">
          {product.primaryImageUrl ? (
            // width/height aspect ratio hint'i — CLS önler; gerçek render container CSS (1:1) ile sabitlenir
            <img src={product.primaryImageUrl} alt={product.name} loading="lazy"
                 width="400" height="400" decoding="async" />
          ) : (
            <div className="card-img-placeholder"><FiShoppingCart size={32} aria-hidden="true" /></div>
          )}
          <div className="card-badges">
            {product.featured && <span className="card-badge card-badge-featured"><FiStar size={10} className="me-1" />Öne Çıkan</span>}
            {product.isNew && <span className="card-badge card-badge-new">Yeni</span>}
            {hasDiscount && <span className="card-badge card-badge-sale">%{discountPercent}</span>}
          </div>
          {/* Wishlist heart */}
          <button className={`card-wishlist-btn ${wishlisted ? 'active' : ''}`} onClick={toggleWishlist} aria-label="Favorilere ekle">
            <FiHeart size={18} />
          </button>
        </div>
      </Link>
      <div className="card-body">
        {product.brandName && <span className="card-brand">{product.brandName}</span>}
        <Link to={`/urun/${product.slug}`} className="text-decoration-none">
          <p className="product-name">{product.name}</p>
        </Link>
        <div className="card-price-area">
          <PriceDisplay price={product.price} salePrice={product.salePrice} />
          <StockBadge status={product.stockStatus} />
        </div>
        <button className="btn btn-add-cart w-100"
          onClick={(e) => { e.preventDefault(); onAddToCart && onAddToCart(product.id); }}
          disabled={product.stockStatus === 'OUT_OF_STOCK'}
          aria-label={`${product.name} sepete ekle`}>
          <FiShoppingCart size={14} />
          <span>{product.stockStatus === 'OUT_OF_STOCK' ? 'Tükendi' : 'Sepete Ekle'}</span>
        </button>
      </div>
    </div>
  );
}
