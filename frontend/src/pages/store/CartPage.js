import React, { useState } from 'react';
import { Link, useOutletContext } from 'react-router-dom';
import { useToast } from '../../components/store/Toast';
import { FiTrash2, FiMinus, FiPlus } from 'react-icons/fi';

export default function CartPage() {
  const { cart } = useOutletContext();
  const toast = useToast();
  const [couponCode, setCouponCode] = useState('');
  const [couponError, setCouponError] = useState('');

  const handleApplyCoupon = async () => {
    try { setCouponError(''); await cart.applyCoupon(couponCode); } catch (e) { setCouponError(e.response?.data?.message || 'Kupon uygulanamadı.'); }
  };

  const formatPrice = (p) => new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(p || 0);

  if (!cart.cart?.items?.length) {
    return (
      <div className="container my-5">
        <div className="store-empty-state">
          <div className="store-empty-state-icon">🛒</div>
          <h3>Sepetiniz Boş</h3>
          <p>Henüz sepetinize ürün eklemediniz.</p>
          <Link to="/store" className="btn btn-primary btn-lg mt-3">Alışverişe Başla</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="container my-4">
      <h1 className="h3 fw-bold mb-4">Sepetim</h1>
      <div className="row g-4">
        <div className="col-lg-8">
          {cart.cart.items.map(item => {
            const hasDiscount = item.salePrice && item.salePrice > 0 && item.salePrice < item.unitPrice;
            const discountPct = hasDiscount ? Math.round((1 - item.salePrice / item.unitPrice) * 100) : 0;
            const effectivePrice = hasDiscount ? item.salePrice : item.unitPrice;
            return (
            <div key={item.id} className="store-cart-item">
              <div className="store-cart-item-img position-relative">
                <Link to={`/store/urun/${item.productSlug}`}>{item.imageUrl && <img src={item.imageUrl} alt={item.productName} />}</Link>
                {hasDiscount && <span className="badge bg-danger position-absolute top-0 start-0" style={{fontSize:10}}>%{discountPct}</span>}
              </div>
              <div className="flex-grow-1">
                <Link to={`/store/urun/${item.productSlug}`} className="text-decoration-none"><h6 className="mb-1">{item.productName}</h6></Link>
                <div className="d-flex align-items-center gap-2 mb-1">
                  {hasDiscount ? (
                    <>
                      <span className="fw-bold text-danger">{formatPrice(item.salePrice)}</span>
                      <del className="text-muted small">{formatPrice(item.unitPrice)}</del>
                      <span className="badge bg-danger bg-opacity-10 text-danger" style={{fontSize:11}}>%{discountPct} indirim</span>
                    </>
                  ) : (
                    <span className="fw-bold">{formatPrice(item.unitPrice)}</span>
                  )}
                </div>
                {item.availableStock < item.quantity && <small className="text-danger"><i className="fas fa-exclamation-triangle me-1" />Stokta sadece {item.availableStock} adet var</small>}
              </div>
              <div className="d-flex align-items-center gap-2">
                <div className="store-qty-selector">
                  <button onClick={() => cart.updateItem(item.id, Math.max(1, item.quantity - 1))} aria-label="Azalt"><FiMinus size={14} /></button>
                  <input type="number" value={item.quantity} readOnly style={{width:40}} aria-label="Miktar" />
                  <button onClick={() => cart.updateItem(item.id, item.quantity + 1)} aria-label="Artır"><FiPlus size={14} /></button>
                </div>
                <div className="text-end" style={{minWidth:80}}>
                  <div className="fw-bold">{formatPrice(effectivePrice * item.quantity)}</div>
                  {item.quantity > 1 && <small className="text-muted">{item.quantity} × {formatPrice(effectivePrice)}</small>}
                </div>
                <button className="btn btn-sm text-danger" onClick={async () => { try { await cart.removeItem(item.id); toast.info('Ürün sepetten çıkarıldı'); } catch { toast.error('Ürün çıkarılamadı'); }}} aria-label="Kaldır"><FiTrash2 /></button>
              </div>
            </div>
            );
          })}
        </div>
        <div className="col-lg-4">
          <div className="store-cart-summary">
            <h5 className="fw-bold mb-3">Sipariş Özeti</h5>
            <div className="store-cart-summary-row"><span>Ara Toplam</span><span>{formatPrice(cart.cart.subtotal)}</span></div>
            <div className="store-cart-summary-row"><span>Kargo</span><span className={cart.cart.shippingCost > 0 ? '' : 'text-success fw-medium'}>{cart.cart.shippingCost > 0 ? formatPrice(cart.cart.shippingCost) : 'Ücretsiz'}</span></div>
            {cart.cart.discountAmount > 0 && <div className="store-cart-summary-row text-success"><span><i className="fas fa-tag me-1" />İndirim</span><span>-{formatPrice(cart.cart.discountAmount)}</span></div>}
            {/* Toplam indirim tasarrufu hesapla */}
            {(() => {
              const totalSaved = (cart.cart?.items || []).reduce((sum, i) => {
                if (i.salePrice && i.salePrice > 0 && i.salePrice < i.unitPrice) return sum + (i.unitPrice - i.salePrice) * i.quantity;
                return sum;
              }, 0);
              return totalSaved > 0 ? (
                <div className="store-cart-summary-row"><span className="text-danger"><i className="fas fa-fire me-1" />Tasarruf</span><span className="text-danger fw-bold">-{formatPrice(totalSaved)}</span></div>
              ) : null;
            })()}
            <div className="store-cart-summary-row store-cart-summary-total"><span>Toplam</span><span>{formatPrice(cart.cart.total)}</span></div>
            <div className="mt-3">
              <div className="input-group input-group-sm">
                <input className="form-control" placeholder="Kupon Kodu" value={couponCode} onChange={e => setCouponCode(e.target.value)} aria-label="Kupon Kodu" />
                <button className="btn btn-outline-primary" onClick={handleApplyCoupon}>Uygula</button>
              </div>
              {couponError && <small className="text-danger">{couponError}</small>}
              {cart.cart.couponCode && <small className="text-success d-block mt-1">Kupon: {cart.cart.couponCode}</small>}
            </div>
            <Link to="/store/odeme" className="btn btn-primary w-100 mt-3 btn-lg">Ödemeye Geç</Link>
          </div>
        </div>
      </div>
    </div>
  );
}
