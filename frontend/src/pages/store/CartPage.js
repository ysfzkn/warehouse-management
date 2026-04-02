import React, { useState } from 'react';
import { Link, useOutletContext } from 'react-router-dom';
import PriceDisplay from '../../components/store/PriceDisplay';
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
          {cart.cart.items.map(item => (
            <div key={item.id} className="store-cart-item">
              <div className="store-cart-item-img"><Link to={`/store/urun/${item.productSlug}`}>{item.imageUrl && <img src={item.imageUrl} alt={item.productName} />}</Link></div>
              <div className="flex-grow-1">
                <Link to={`/store/urun/${item.productSlug}`} className="text-decoration-none"><h6 className="mb-1">{item.productName}</h6></Link>
                <PriceDisplay price={item.unitPrice} salePrice={item.salePrice} />
                {item.availableStock < item.quantity && <small className="text-danger">Stokta sadece {item.availableStock} adet var</small>}
              </div>
              <div className="d-flex align-items-center gap-2">
                <div className="store-qty-selector">
                  <button onClick={() => cart.updateItem(item.id, Math.max(1, item.quantity - 1))} aria-label="Azalt"><FiMinus size={14} /></button>
                  <input type="number" value={item.quantity} readOnly style={{width:40}} aria-label="Miktar" />
                  <button onClick={() => cart.updateItem(item.id, item.quantity + 1)} aria-label="Artir"><FiPlus size={14} /></button>
                </div>
                <strong>{formatPrice(item.lineTotal)}</strong>
                <button className="btn btn-sm text-danger" onClick={async () => { try { await cart.removeItem(item.id); toast.info('Ürün sepetten çıkarıldı'); } catch { toast.error('Ürün çıkarılamadı'); }}} aria-label="Kaldir"><FiTrash2 /></button>
              </div>
            </div>
          ))}
        </div>
        <div className="col-lg-4">
          <div className="store-cart-summary">
            <h5 className="fw-bold mb-3">Sipariş Özeti</h5>
            <div className="store-cart-summary-row"><span>Ara Toplam</span><span>{formatPrice(cart.cart.subtotal)}</span></div>
            <div className="store-cart-summary-row"><span>Kargo</span><span>{cart.cart.shippingCost > 0 ? formatPrice(cart.cart.shippingCost) : 'Ücretsiz'}</span></div>
            {cart.cart.discountAmount > 0 && <div className="store-cart-summary-row text-success"><span>İndirim</span><span>-{formatPrice(cart.cart.discountAmount)}</span></div>}
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
