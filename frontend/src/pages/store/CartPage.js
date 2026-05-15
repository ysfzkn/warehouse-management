import React, { useState } from 'react';
import { Link, useOutletContext } from 'react-router-dom';
import { useToast } from '../../components/store/Toast';
import { FiTrash2, FiMinus, FiPlus, FiShoppingCart } from 'react-icons/fi';

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
          <Link to="/" className="btn btn-primary btn-lg mt-3">Alışverişe Başla</Link>
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
            const hasDiscount = !!(item.salePrice && item.salePrice > 0 && item.salePrice < item.unitPrice);
            const discountPct = hasDiscount ? Math.round((1 - item.salePrice / item.unitPrice) * 100) : 0;
            const effectivePrice = hasDiscount ? item.salePrice : item.unitPrice;
            return (
            <div key={item.id} className="card border-0 shadow-sm mb-3" style={{borderRadius:14}}>
              <div className="card-body d-flex gap-3 align-items-center p-3">
                {/* Görsel */}
                <Link to={`/urun/${item.productSlug}`} className="flex-shrink-0 position-relative">
                  <div style={{width:80,height:80,borderRadius:12,background:'#f8fafc',border:'1px solid #f1f5f9',overflow:'hidden',display:'flex',alignItems:'center',justifyContent:'center'}}>
                    {item.imageUrl ? <img src={item.imageUrl} alt={item.productName || 'Ürün görseli'} style={{width:'100%',height:'100%',objectFit:'contain'}} /> : <FiShoppingCart size={24} className="text-muted" />}
                  </div>
                  {hasDiscount && <span className="badge bg-danger position-absolute" style={{top:-4,left:-4,fontSize:9}}>%{discountPct}</span>}
                </Link>

                {/* Ürün bilgisi */}
                <div className="flex-grow-1 min-w-0">
                  <Link to={`/urun/${item.productSlug}`} className="text-decoration-none">
                    <div className="fw-semibold text-truncate mb-1">{item.productName}</div>
                  </Link>
                  <div className="d-flex align-items-center gap-2 mb-2">
                    {hasDiscount ? (
                      <>
                        <span className="fw-bold text-danger">{formatPrice(item.salePrice)}</span>
                        <del className="text-muted" style={{fontSize:12}}>{formatPrice(item.unitPrice)}</del>
                      </>
                    ) : (
                      <span className="fw-bold">{formatPrice(item.unitPrice)}</span>
                    )}
                  </div>
                  {item.availableStock != null && item.availableStock < item.quantity && (
                    <small className="text-danger"><i className="fas fa-exclamation-triangle me-1" />Stokta sadece {item.availableStock} adet var</small>
                  )}
                </div>

                {/* Miktar kontrol — ortalanmış */}
                <div className="d-flex flex-column align-items-center flex-shrink-0" style={{minWidth:100}}>
                  <div className="d-flex align-items-center" style={{border:'1.5px solid #e2e8f0',borderRadius:10,overflow:'hidden'}}>
                    <button className="btn btn-sm d-flex align-items-center justify-content-center" style={{width:32,height:32,border:'none',background:'#f8fafc'}}
                      onClick={() => cart.updateItem(item.id, Math.max(1, item.quantity - 1))}>
                      <FiMinus size={13} />
                    </button>
                    <span className="d-flex align-items-center justify-content-center fw-bold" style={{width:36,height:32,fontSize:14,borderLeft:'1px solid #e2e8f0',borderRight:'1px solid #e2e8f0'}}>
                      {item.quantity}
                    </span>
                    <button className="btn btn-sm d-flex align-items-center justify-content-center" style={{width:32,height:32,border:'none',background:'#f8fafc'}}
                      onClick={() => cart.updateItem(item.id, item.quantity + 1)}>
                      <FiPlus size={13} />
                    </button>
                  </div>
                </div>

                {/* Toplam fiyat — sağa hizalı */}
                <div className="text-end flex-shrink-0" style={{minWidth:90}}>
                  <div className="fw-bold">{formatPrice(effectivePrice * item.quantity)}</div>
                  {item.quantity > 1 && <small className="text-muted">{item.quantity} × {formatPrice(effectivePrice)}</small>}
                </div>

                {/* Sil */}
                <button className="btn btn-sm text-danger flex-shrink-0" style={{width:32,height:32,padding:0}}
                  onClick={async () => { try { await cart.removeItem(item.id); toast.info('Ürün sepetten çıkarıldı'); } catch { toast.error('Ürün çıkarılamadı'); }}}
                  aria-label="Kaldır">
                  <FiTrash2 size={15} />
                </button>
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
            <Link to="/odeme" className="btn btn-primary w-100 mt-3 btn-lg">Ödemeye Geç</Link>
          </div>
        </div>
      </div>
    </div>
  );
}
