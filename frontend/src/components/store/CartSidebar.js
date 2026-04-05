import React from 'react';
import { Link } from 'react-router-dom';
import { FiX, FiTrash2, FiMinus, FiPlus, FiShoppingBag } from 'react-icons/fi';

const fmt = (v) => new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(v || 0);

export default function CartSidebar({ cart }) {
  if (!cart.sidebarOpen) return null;

  return (
    <>
      <div className="store-cart-overlay" onClick={() => cart.setSidebarOpen(false)} />
      <div className={`store-cart-sidebar ${cart.sidebarOpen ? 'open' : ''}`} role="dialog" aria-modal="true" aria-label="Sepet">
        <div className="d-flex justify-content-between align-items-center p-3 border-bottom">
          <h5 className="mb-0 fw-bold"><FiShoppingBag className="me-2" />Sepetim ({cart.itemCount})</h5>
          <button className="btn btn-sm" onClick={() => cart.setSidebarOpen(false)} aria-label="Kapat"><FiX size={20} /></button>
        </div>

        <div className="flex-grow-1 overflow-auto p-3">
          {(!cart.cart?.items || cart.cart.items.length === 0) ? (
            <div className="text-center py-5">
              <FiShoppingBag size={36} className="text-muted mb-3" style={{ opacity: 0.2 }} />
              <p className="text-muted small">Sepetiniz şu an boş.</p>
              <Link to="/store" className="btn btn-sm btn-outline-primary" onClick={() => cart.setSidebarOpen(false)}>Alışverişe Başla</Link>
            </div>
          ) : cart.cart.items.map(item => {
            const hasDiscount = item.salePrice && item.salePrice > 0 && item.salePrice < item.unitPrice;
            const discountPct = hasDiscount ? Math.round((1 - item.salePrice / item.unitPrice) * 100) : 0;
            const price = hasDiscount ? item.salePrice : item.unitPrice;
            return (
              <div key={item.id} className="d-flex gap-3 py-3 border-bottom align-items-center">
                {/* Ürün görseli */}
                <div style={{ width: 56, height: 56, borderRadius: 10, background: '#f8fafc', border: '1px solid #f1f5f9', overflow: 'hidden', flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  {item.imageUrl ? <img src={item.imageUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'contain' }} /> : <FiShoppingBag className="text-muted" />}
                </div>

                {/* Bilgi + miktar */}
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

                  {/* Miktar kontrol */}
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

                {/* Satır toplam */}
                <div className="text-end flex-shrink-0">
                  <div className="fw-bold small">{fmt(price * item.quantity)}</div>
                </div>
              </div>
            );
          })}
        </div>

        {/* Alt kısım — toplam + butonlar */}
        {cart.cart?.items?.length > 0 && (
          <div className="p-3 border-top" style={{ background: '#fafbfc' }}>
            <div className="d-flex justify-content-between mb-1 small text-muted">
              <span>{cart.itemCount} ürün</span>
              <span>Ara Toplam</span>
            </div>
            <div className="d-flex justify-content-between mb-3">
              <span className="fw-bold">Toplam</span>
              <strong className="text-primary" style={{ fontSize: 18 }}>{fmt(cart.cart?.total || 0)}</strong>
            </div>
            <div className="d-flex gap-2">
              <Link to="/store/sepet" className="btn btn-outline-primary flex-grow-1" onClick={() => cart.setSidebarOpen(false)}>
                Sepete Git
              </Link>
              <Link to="/store/odeme" className="btn btn-primary flex-grow-1" onClick={() => cart.setSidebarOpen(false)}>
                Ödemeye Geç
              </Link>
            </div>
          </div>
        )}
      </div>
    </>
  );
}
