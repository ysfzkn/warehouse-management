import React from 'react';
import { Link } from 'react-router-dom';
import { FiX, FiTrash2 } from 'react-icons/fi';

export default function CartSidebar({ cart }) {
  if (!cart.sidebarOpen) return null;

  return (
    <>
      <div className="store-cart-overlay" onClick={() => cart.setSidebarOpen(false)} />
      <div className={`store-cart-sidebar ${cart.sidebarOpen ? 'open' : ''}`} role="dialog" aria-modal="true" aria-labelledby="cart-sidebar-title" aria-label="Sepet">
        <div className="d-flex justify-content-between align-items-center p-3 border-bottom">
          <h5 id="cart-sidebar-title" className="mb-0 fw-bold">Sepetim ({cart.itemCount})</h5>
          <button className="btn btn-sm" onClick={() => cart.setSidebarOpen(false)} aria-label="Kapat"><FiX size={20} /></button>
        </div>
        <div className="flex-grow-1 overflow-auto p-3">
          {(!cart.cart?.items || cart.cart.items.length === 0) ? (
            <div className="text-center py-5">
              <i className="fas fa-shopping-cart text-muted fa-2x mb-3 opacity-25" />
              <p className="text-muted small">Sepetiniz şu an boş.</p>
              <Link to="/store" className="btn btn-sm btn-outline-primary" onClick={() => cart.setSidebarOpen(false)}>Alışverişe Başla</Link>
            </div>
          ) : cart.cart.items.map(item => {
            const hasDiscount = item.salePrice && item.salePrice > 0 && item.salePrice < item.unitPrice;
            const discountPct = hasDiscount ? Math.round((1 - item.salePrice / item.unitPrice) * 100) : 0;
            return (
            <div key={item.id} className="store-cart-item">
              <div className="store-cart-item-img">
                {item.imageUrl && <img src={item.imageUrl} alt={item.productName} />}
              </div>
              <div className="flex-grow-1 min-w-0">
                <p className="mb-1 small fw-medium text-truncate">{item.productName}</p>
                <p className="mb-0 small text-muted">{item.quantity} adet</p>
                <div className="d-flex align-items-center gap-2 mt-1">
                  {hasDiscount ? (
                    <>
                      <span className="fw-bold small text-danger">{new Intl.NumberFormat('tr-TR',{style:'currency',currency:'TRY'}).format(item.salePrice)}</span>
                      <del className="text-muted" style={{fontSize:11}}>{new Intl.NumberFormat('tr-TR',{style:'currency',currency:'TRY'}).format(item.unitPrice)}</del>
                      <span className="badge bg-danger" style={{fontSize:9}}>%{discountPct}</span>
                    </>
                  ) : (
                    <span className="fw-bold small">{new Intl.NumberFormat('tr-TR',{style:'currency',currency:'TRY'}).format(item.unitPrice)}</span>
                  )}
                </div>
              </div>
              <button className="btn btn-sm text-danger" onClick={() => cart.removeItem(item.id)} aria-label="Ürünü kaldır"><FiTrash2 /></button>
            </div>
            );
          })}
        </div>
        <div className="p-3 border-top">
          <div className="d-flex justify-content-between mb-2"><span>Toplam</span>
            <strong>{new Intl.NumberFormat('tr-TR', {style:'currency',currency:'TRY'}).format(cart.cart?.total || 0)}</strong>
          </div>
          <Link to="/store/sepet" className="btn btn-primary w-100" onClick={() => cart.setSidebarOpen(false)}>Sepete Git</Link>
        </div>
      </div>
    </>
  );
}
