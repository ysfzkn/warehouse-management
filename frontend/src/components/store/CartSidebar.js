import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { FiX, FiTrash2, FiMinus, FiPlus, FiShoppingBag, FiArrowLeft, FiTag, FiTruck, FiCheck } from 'react-icons/fi';
import { useSiteSettings } from '../../hooks/useSiteSettings';

const fmt = (v) => new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(v || 0);

export default function CartSidebar({ cart }) {
  const { get } = useSiteSettings();
  const [showClearConfirm, setShowClearConfirm] = useState(false);
  const [couponCode, setCouponCode] = useState('');
  const [couponLoading, setCouponLoading] = useState(false);
  const [couponError, setCouponError] = useState('');
  const [clearing, setClearing] = useState(false);

  if (!cart.sidebarOpen) return null;

  const subtotal = cart.cart?.subtotal || cart.cart?.total || 0;
  const freeShippingThreshold = Number(get('free_shipping_threshold', 0));
  const remaining = Math.max(0, freeShippingThreshold - subtotal);
  const progress = freeShippingThreshold > 0 ? Math.min(100, (subtotal / freeShippingThreshold) * 100) : 0;
  const qualifiesForFreeShipping = freeShippingThreshold > 0 && subtotal >= freeShippingThreshold;
  const hasItems = cart.cart?.items?.length > 0;
  const appliedCoupon = cart.cart?.couponCode;

  const handleClearCart = async () => {
    setClearing(true);
    try {
      await cart.clearCart();
      setShowClearConfirm(false);
    } catch (e) {
      // Error logged in useCart hook
    } finally {
      setClearing(false);
    }
  };

  const handleApplyCoupon = async (e) => {
    e.preventDefault();
    if (!couponCode.trim()) return;
    setCouponLoading(true);
    setCouponError('');
    try {
      await cart.applyCoupon(couponCode.trim().toUpperCase());
      setCouponCode('');
    } catch (err) {
      setCouponError(err?.response?.data?.message || 'Kupon kodu geçersiz veya süresi dolmuş.');
    } finally {
      setCouponLoading(false);
    }
  };

  const handleRemoveCoupon = async () => {
    try { await cart.removeCoupon(); } catch { /* ignore */ }
  };

  return (
    <>
      <div className="store-cart-overlay" onClick={() => cart.setSidebarOpen(false)} />
      <div className={`store-cart-sidebar ${cart.sidebarOpen ? 'open' : ''}`} role="dialog" aria-modal="true" aria-label="Sepet">
        {/* Header */}
        <div className="d-flex justify-content-between align-items-center p-3 border-bottom">
          <h5 className="mb-0 fw-bold"><FiShoppingBag className="me-2" />Sepetim ({cart.itemCount})</h5>
          <button className="btn btn-sm" onClick={() => cart.setSidebarOpen(false)} aria-label="Kapat">
            <FiX size={20} />
          </button>
        </div>

        {/* Free shipping progress bar */}
        {hasItems && freeShippingThreshold > 0 && (
          <div className="px-3 pt-3 pb-2" style={{ background: qualifiesForFreeShipping ? '#ecfdf5' : '#f0f9ff' }}>
            {qualifiesForFreeShipping ? (
              <div className="d-flex align-items-center gap-2" style={{ color: '#059669' }}>
                <FiCheck size={16} />
                <small className="fw-semibold">🎉 Ücretsiz kargo hakkı kazandınız!</small>
              </div>
            ) : (
              <>
                <div className="d-flex align-items-center gap-2 mb-1" style={{ color: '#0c4a6e' }}>
                  <FiTruck size={14} />
                  <small>
                    Ücretsiz kargo için <strong>{fmt(remaining)}</strong> daha ekleyin
                  </small>
                </div>
                <div className="progress" style={{ height: 6, background: '#e0f2fe' }}>
                  <div
                    className="progress-bar"
                    role="progressbar"
                    style={{ width: `${progress}%`, background: 'linear-gradient(90deg, #3b82f6, #2563eb)', transition: 'width 0.3s' }}
                    aria-valuenow={progress}
                    aria-valuemin="0"
                    aria-valuemax="100"
                  />
                </div>
              </>
            )}
          </div>
        )}

        {/* Items list */}
        <div className="flex-grow-1 overflow-auto p-3">
          {!hasItems ? (
            <div className="text-center py-5">
              <FiShoppingBag size={36} className="text-muted mb-3" style={{ opacity: 0.2 }} />
              <p className="text-muted small">Sepetiniz şu an boş.</p>
              <Link to="/" className="btn btn-sm btn-outline-primary" onClick={() => cart.setSidebarOpen(false)}>
                <FiArrowLeft size={14} className="me-1" />Alışverişe Başla
              </Link>
            </div>
          ) : cart.cart.items.map(item => {
            const hasDiscount = !!(item.salePrice && item.salePrice > 0 && item.salePrice < item.unitPrice);
            const discountPct = hasDiscount ? Math.round((1 - item.salePrice / item.unitPrice) * 100) : 0;
            const price = hasDiscount ? item.salePrice : item.unitPrice;
            return (
              <div key={item.id} className="d-flex gap-3 py-3 border-bottom align-items-center">
                {/* Product image */}
                <div style={{ width: 56, height: 56, borderRadius: 10, background: '#f8fafc', border: '1px solid #f1f5f9', overflow: 'hidden', flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  {item.imageUrl ? <img src={item.imageUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'contain' }} /> : <FiShoppingBag className="text-muted" />}
                </div>

                {/* Info + quantity */}
                <div className="flex-grow-1 min-w-0">
                  <p className="mb-1 small fw-medium text-truncate">{item.productName}</p>
                  <div className="d-flex align-items-center gap-1">
                    {hasDiscount ? (
                      <>
                        <span className="fw-bold small text-danger">{fmt(price)}</span>
                        <del className="text-muted" style={{ fontSize: 10 }}>{fmt(item.unitPrice)}</del>
                        <span className="badge bg-danger" style={{ fontSize: 8 }}>%{discountPct}</span>
                      </>
                    ) : (
                      <span className="fw-bold small">{fmt(price)}</span>
                    )}
                  </div>

                  {/* Quantity controls */}
                  <div className="d-flex align-items-center gap-0 mt-1">
                    <button className="btn btn-sm btn-outline-secondary d-flex align-items-center justify-content-center"
                      style={{ width: 26, height: 26, padding: 0, borderRadius: '6px 0 0 6px' }}
                      onClick={() => item.quantity > 1 ? cart.updateItem(item.id, item.quantity - 1) : cart.removeItem(item.id)}>
                      {item.quantity <= 1 ? <FiTrash2 size={11} className="text-danger" /> : <FiMinus size={11} />}
                    </button>
                    <span className="d-flex align-items-center justify-content-center border-top border-bottom"
                      style={{ width: 30, height: 26, fontSize: 12, fontWeight: 600 }}>
                      {item.quantity}
                    </span>
                    <button className="btn btn-sm btn-outline-secondary d-flex align-items-center justify-content-center"
                      style={{ width: 26, height: 26, padding: 0, borderRadius: '0 6px 6px 0' }}
                      onClick={() => cart.updateItem(item.id, item.quantity + 1)}>
                      <FiPlus size={11} />
                    </button>
                  </div>
                </div>

                {/* Line total + remove */}
                <div className="text-end flex-shrink-0">
                  <div className="fw-bold small">{fmt(price * item.quantity)}</div>
                  <button
                    className="btn btn-link p-0 text-danger mt-1"
                    style={{ fontSize: 11, textDecoration: 'none' }}
                    title="Ürünü çıkar"
                    onClick={() => cart.removeItem(item.id)}
                  >
                    <FiTrash2 size={12} /> Çıkar
                  </button>
                </div>
              </div>
            );
          })}
        </div>

        {/* Footer — quick actions + coupon + totals + CTA */}
        {hasItems && (
          <div className="border-top" style={{ background: '#fafbfc' }}>
            {/* Clear cart confirmation */}
            {showClearConfirm ? (
              <div className="p-3 border-bottom" style={{ background: '#fef2f2' }}>
                <div className="small mb-2" style={{ color: '#991b1b' }}>
                  <strong>Sepetinizi boşaltmak istediğinize emin misiniz?</strong>
                  <div className="mt-1" style={{ fontSize: 11, opacity: 0.85 }}>
                    Tüm ürünler sepetten kaldırılacak. Bu işlem geri alınamaz.
                  </div>
                </div>
                <div className="d-flex gap-2">
                  <button
                    className="btn btn-sm btn-outline-secondary flex-grow-1"
                    onClick={() => setShowClearConfirm(false)}
                    disabled={clearing}
                  >
                    Vazgeç
                  </button>
                  <button
                    className="btn btn-sm btn-danger flex-grow-1"
                    onClick={handleClearCart}
                    disabled={clearing}
                  >
                    {clearing ? (
                      <><span className="spinner-border spinner-border-sm me-1" style={{ width: 10, height: 10 }} />Siliniyor...</>
                    ) : (
                      <><FiTrash2 size={12} className="me-1" />Evet, Boşalt</>
                    )}
                  </button>
                </div>
              </div>
            ) : (
              /* Quick action row */
              <div className="px-3 pt-2 pb-0 d-flex justify-content-between align-items-center">
                <Link
                  to="/"
                  className="btn btn-sm btn-link text-muted p-0"
                  onClick={() => cart.setSidebarOpen(false)}
                  style={{ fontSize: 12, textDecoration: 'none' }}
                >
                  <FiArrowLeft size={12} className="me-1" />Alışverişe Devam
                </Link>
                <button
                  className="btn btn-sm btn-link text-danger p-0"
                  onClick={() => setShowClearConfirm(true)}
                  style={{ fontSize: 12, textDecoration: 'none' }}
                  title="Sepetteki tüm ürünleri kaldır"
                >
                  <FiTrash2 size={12} className="me-1" />Sepeti Boşalt
                </button>
              </div>
            )}

            {/* Coupon section */}
            {!showClearConfirm && (
              <div className="p-3 pt-2">
                {appliedCoupon ? (
                  <div className="d-flex align-items-center justify-content-between p-2 rounded mb-2"
                    style={{ background: '#ecfdf5', border: '1px solid #bbf7d0' }}>
                    <div className="d-flex align-items-center gap-2 small" style={{ color: '#047857' }}>
                      <FiTag size={14} />
                      <span>Kupon: <strong>{appliedCoupon}</strong></span>
                      {cart.cart?.discountAmount > 0 && (
                        <span className="badge" style={{ background: '#059669' }}>-{fmt(cart.cart.discountAmount)}</span>
                      )}
                    </div>
                    <button
                      className="btn btn-sm btn-link text-danger p-0"
                      onClick={handleRemoveCoupon}
                      style={{ fontSize: 11 }}
                      title="Kuponu kaldır"
                    >
                      <FiX size={14} />
                    </button>
                  </div>
                ) : (
                  <form onSubmit={handleApplyCoupon} className="mb-2">
                    <div className="input-group input-group-sm">
                      <span className="input-group-text bg-white">
                        <FiTag size={12} />
                      </span>
                      <input
                        type="text"
                        className={`form-control ${couponError ? 'is-invalid' : ''}`}
                        placeholder="Kupon kodunuz"
                        value={couponCode}
                        onChange={(e) => { setCouponCode(e.target.value); setCouponError(''); }}
                        style={{ textTransform: 'uppercase' }}
                        disabled={couponLoading}
                      />
                      <button
                        type="submit"
                        className="btn btn-outline-primary"
                        disabled={!couponCode.trim() || couponLoading}
                      >
                        {couponLoading ? (
                          <span className="spinner-border spinner-border-sm" style={{ width: 10, height: 10 }} />
                        ) : 'Uygula'}
                      </button>
                    </div>
                    {couponError && (
                      <small className="text-danger d-block mt-1" style={{ fontSize: 11 }}>{couponError}</small>
                    )}
                  </form>
                )}
              </div>
            )}

            {/* Totals + CTA */}
            <div className="px-3 pb-3">
              {cart.cart?.discountAmount > 0 && (
                <div className="d-flex justify-content-between mb-1 small text-success">
                  <span>İndirim</span>
                  <span>-{fmt(cart.cart.discountAmount)}</span>
                </div>
              )}
              <div className="d-flex justify-content-between mb-1 small text-muted">
                <span>{cart.itemCount} ürün</span>
                <span>Ara Toplam</span>
              </div>
              <div className="d-flex justify-content-between mb-3">
                <span className="fw-bold">Toplam</span>
                <strong className="text-primary" style={{ fontSize: 18 }}>{fmt(cart.cart?.total || 0)}</strong>
              </div>
              <div className="d-flex gap-2">
                <Link to="/sepet" className="btn btn-outline-primary flex-grow-1" onClick={() => cart.setSidebarOpen(false)}>
                  Sepete Git
                </Link>
                <Link to="/odeme" className="btn btn-primary flex-grow-1" onClick={() => cart.setSidebarOpen(false)}>
                  Ödemeye Geç
                </Link>
              </div>
            </div>
          </div>
        )}
      </div>
    </>
  );
}
