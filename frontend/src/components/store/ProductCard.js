import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import StockBadge from './StockBadge';
import PriceDisplay from './PriceDisplay';
import { FiShoppingCart, FiStar, FiHeart, FiPackage } from 'react-icons/fi';
import { useToast } from './Toast';
import { useWishlist } from './WishlistContext';

export default function ProductCard({ product, onAddToCart }) {
  const hasDiscount = !!(product.salePrice && product.salePrice > 0 && product.salePrice < product.price);
  const discountPercent = hasDiscount ? Math.round((1 - product.salePrice / product.price) * 100) : 0;
  const isBundle = product.productType === 'BUNDLE';
  const bundleEffective = product.salePrice && product.salePrice > 0 ? product.salePrice : product.price;
  const bundleSavings =
    isBundle && product.membersOriginalTotal ? product.membersOriginalTotal - bundleEffective : 0;
  const fmtTRY = (v) =>
    new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(Number(v) || 0);
  const toast = useToast();
  const navigate = useNavigate();
  const wishlist = useWishlist();
  const wishlisted = wishlist.has(product.id);

  const toggleWishlist = async (e) => {
    e.preventDefault();
    e.stopPropagation();
    const token = localStorage.getItem('customer_token');
    if (!token) {
      toast.warning('Favorilere eklemek için giriş yapın.');
      navigate('/giris');
      return;
    }
    try {
      const nowWished = await wishlist.toggle(product.id);
      if (nowWished) toast.success('Favorilere eklendi!');
      else toast.info('Favorilerden çıkarıldı.');
    } catch (err) {
      toast.error(err?.response?.data?.message || 'İşlem başarısız.');
    }
  };

  return (
    <div className="store-product-card">
      <Link to={`/urun/${product.slug}`} className="text-decoration-none">
        <div className="card-img-wrapper">
          {product.primaryImageUrl ? (
            // width/height aspect-ratio hint — prevents CLS; actual render is fixed by container CSS (1:1)
            <img
              src={product.primaryImageUrl}
              alt={product.name}
              loading="lazy"
              width="400"
              height="400"
              decoding="async"
            />
          ) : (
            <div className="card-img-placeholder">
              <FiShoppingCart size={32} aria-hidden="true" />
            </div>
          )}
          <div className="card-badges">
            {/* En fazla 2 rozet — öncelik sırası: set, indirim, yeni, öne çıkan.
                Böylece rozetler ürün görselinin büyük kısmını kaplamaz. */}
            {[
              product.productType === 'BUNDLE' && (
                <span key="set" className="card-badge card-badge-set">
                  <FiPackage size={11} className="me-1" />
                  {product.bundleItemCount ? `${product.bundleItemCount} Ürün Bir Arada` : 'SET'}
                </span>
              ),
              hasDiscount && (
                <span key="sale" className="card-badge card-badge-sale">
                  %{discountPercent}
                </span>
              ),
              product.isNew && (
                <span key="new" className="card-badge card-badge-new">
                  Yeni
                </span>
              ),
              product.featured && (
                <span key="featured" className="card-badge card-badge-featured">
                  <FiStar size={10} className="me-1" />
                  Öne Çıkan
                </span>
              ),
            ]
              .filter(Boolean)
              .slice(0, 2)}
          </div>
          {/* Wishlist heart */}
          <button
            className={`card-wishlist-btn ${wishlisted ? 'active' : ''}`}
            onClick={toggleWishlist}
            aria-label="Favorilere ekle"
          >
            <FiHeart size={18} />
          </button>
        </div>
      </Link>
      <div className="card-body">
        {product.brandName && <span className="card-brand">{product.brandName}</span>}
        <Link to={`/urun/${product.slug}`} className="text-decoration-none">
          <p className="product-name">{product.name}</p>
        </Link>
        {Array.isArray(product.colorVariants) && product.colorVariants.length > 1 && (
          <div
            className="card-color-variants d-flex align-items-center flex-wrap"
            style={{ gap: 6, margin: '4px 0 8px' }}
          >
            {product.colorVariants.slice(0, 5).map((v) => {
              const isCurrent = v.productId === product.id;
              const dot = (
                <span
                  style={{
                    display: 'inline-block',
                    width: 16,
                    height: 16,
                    borderRadius: '50%',
                    backgroundColor: v.colorHexCode || '#e2e8f0',
                    boxShadow: isCurrent
                      ? '0 0 0 1.5px var(--store-primary, #2563eb)'
                      : '0 0 0 1px rgba(0,0,0,0.18)',
                    opacity: v.inStock ? 1 : 0.4,
                  }}
                />
              );
              const title = `${v.colorName || 'Renk'}${v.inStock ? '' : ' — Tükendi'}`;
              if (isCurrent) {
                return (
                  <span key={v.productId} title={title} style={{ padding: 2, lineHeight: 0 }}>
                    {dot}
                  </span>
                );
              }
              return (
                <button
                  key={v.productId}
                  type="button"
                  title={title}
                  aria-label={title}
                  onClick={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    navigate(`/urun/${v.slug}`);
                  }}
                  style={{ border: 'none', background: 'none', padding: 2, lineHeight: 0, cursor: 'pointer' }}
                >
                  {dot}
                </button>
              );
            })}
            {product.colorVariants.length > 5 && (
              <span className="text-muted" style={{ fontSize: 11 }}>
                +{product.colorVariants.length - 5}
              </span>
            )}
          </div>
        )}
        <div className="card-price-area">
          <PriceDisplay price={product.price} salePrice={product.salePrice} />
          <StockBadge status={product.stockStatus} />
        </div>
        {isBundle && bundleSavings > 0 && (
          <div className="card-bundle-saving" title="Ayrı ayrı almaya göre avantaj">
            <FiPackage size={11} className="me-1" />
            {fmtTRY(bundleSavings)} avantaj
          </div>
        )}
        <button
          className="btn btn-add-cart w-100"
          onClick={(e) => {
            e.preventDefault();
            onAddToCart && onAddToCart(product.id);
          }}
          disabled={product.stockStatus === 'OUT_OF_STOCK'}
          aria-label={`${product.name} sepete ekle`}
        >
          <FiShoppingCart size={14} />
          <span>{product.stockStatus === 'OUT_OF_STOCK' ? 'Tükendi' : 'Sepete Ekle'}</span>
        </button>
      </div>
    </div>
  );
}
