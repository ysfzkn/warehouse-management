import React, { useState, useEffect } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import {
  FiPackage, FiMail, FiSearch, FiClock, FiCheckCircle, FiTruck,
  FiHome, FiXCircle, FiRotateCcw, FiAlertCircle, FiCreditCard, FiExternalLink
} from 'react-icons/fi';

const STATUS_CONFIG = {
  PENDING_PAYMENT: { icon: FiClock, color: '#f59e0b', bg: '#fffbeb', label: 'Ödeme Bekliyor' },
  PAID: { icon: FiCreditCard, color: '#2563eb', bg: '#eff6ff', label: 'Ödeme Alındı' },
  PREPARING: { icon: FiPackage, color: '#8b5cf6', bg: '#f5f3ff', label: 'Hazırlanıyor' },
  SHIPPED: { icon: FiTruck, color: '#0ea5e9', bg: '#f0f9ff', label: 'Kargoda' },
  DELIVERED: { icon: FiHome, color: '#059669', bg: '#ecfdf5', label: 'Teslim Edildi' },
  CANCELLED: { icon: FiXCircle, color: '#dc2626', bg: '#fef2f2', label: 'İptal Edildi' },
  RETURN_REQUESTED: { icon: FiRotateCcw, color: '#d97706', bg: '#fffbeb', label: 'İade Talep Edildi' },
  RETURNED: { icon: FiRotateCcw, color: '#dc2626', bg: '#fef2f2', label: 'İade Edildi' },
  REFUNDED: { icon: FiRotateCcw, color: '#059669', bg: '#ecfdf5', label: 'Para İadesi Yapıldı' },
};

const formatDate = (d) => d ? new Date(d).toLocaleString('tr-TR', {
  day: '2-digit', month: '2-digit', year: 'numeric',
  hour: '2-digit', minute: '2-digit'
}) : '\u2014';

const formatDateShort = (d) => d ? new Date(d).toLocaleDateString('tr-TR', {
  day: '2-digit', month: 'long', year: 'numeric'
}) : '\u2014';

const formatPrice = (p) => new Intl.NumberFormat('tr-TR', {
  style: 'currency', currency: 'TRY'
}).format(p || 0);

export default function OrderTrackingPage() {
  const [searchParams] = useSearchParams();
  const [orderNumber, setOrderNumber] = useState(searchParams.get('order') || '');
  const [email, setEmail] = useState(searchParams.get('email') || '');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [order, setOrder] = useState(null);

  const handleSubmit = async (e) => {
    e?.preventDefault();
    setError(''); setOrder(null);

    if (!orderNumber.trim()) { setError('Sipariş numarasını girin.'); return; }
    if (!email.trim() || !/^\S+@\S+\.\S+$/.test(email)) { setError('Geçerli bir e-posta girin.'); return; }

    setLoading(true);
    try {
      const res = await axios.post('/api/store/public/orders/track', {
        orderNumber: orderNumber.trim(),
        email: email.trim()
      });
      setOrder(res.data);
    } catch (err) {
      setError(err.response?.data?.error || 'Sipariş bulunamadı. Bilgilerinizi kontrol edin.');
    } finally {
      setLoading(false);
    }
  };

  // Auto-submit if params exist
  useEffect(() => {
    if (searchParams.get('order') && searchParams.get('email')) {
      handleSubmit();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const currentStatus = order ? STATUS_CONFIG[order.status] || STATUS_CONFIG.PENDING_PAYMENT : null;

  return (
    <div className="container my-5" style={{ maxWidth: 720 }}>
      {/* Header */}
      <div className="text-center mb-4">
        <div style={{
          width: 72, height: 72, borderRadius: '50%',
          background: 'linear-gradient(135deg, #eff6ff, #dbeafe)',
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          marginBottom: 16
        }}>
          <FiSearch size={32} color="#2563eb" />
        </div>
        <h1 className="fw-bold mb-2">Sipariş Takip</h1>
        <p className="text-muted">
          Üye olmadan siparişinizi takip edebilirsiniz. Sipariş numaranızı ve sipariş e-posta adresinizi girin.
        </p>
      </div>

      {/* Search Form */}
      <div className="card border-0 shadow-sm mb-4" style={{ borderRadius: 16 }}>
        <div className="card-body p-4">
          <form onSubmit={handleSubmit}>
            <div className="row g-3">
              <div className="col-12">
                <label className="form-label small fw-semibold">
                  <FiPackage className="me-1" />Sipariş Numarası
                </label>
                <input
                  type="text"
                  className="form-control"
                  placeholder="ORD-20260417-ABC123"
                  value={orderNumber}
                  onChange={(e) => setOrderNumber(e.target.value)}
                />
              </div>
              <div className="col-12">
                <label className="form-label small fw-semibold">
                  <FiMail className="me-1" />E-posta Adresi
                </label>
                <input
                  type="email"
                  className="form-control"
                  placeholder="siparis@eposta.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
                <small className="text-muted">Sipariş verirken kullandığınız e-posta adresi</small>
              </div>
              <div className="col-12">
                <button
                  type="submit"
                  className="btn btn-primary w-100 py-2"
                  disabled={loading}
                >
                  {loading ? (
                    <><span className="spinner-border spinner-border-sm me-2" />Sorgulanıyor...</>
                  ) : (
                    <><FiSearch className="me-2" />Siparişimi Sorgula</>
                  )}
                </button>
              </div>
            </div>
          </form>

          {error && (
            <div className="alert alert-danger mt-3 mb-0 py-2 small">
              <FiAlertCircle className="me-1" />{error}
            </div>
          )}
        </div>
      </div>

      {/* Order Result */}
      {order && (
        <div className="card border-0 shadow-sm" style={{ borderRadius: 16 }}>
          {/* Current Status Banner */}
          <div style={{
            background: currentStatus.bg,
            borderTopLeftRadius: 16,
            borderTopRightRadius: 16,
            padding: '20px 24px',
            borderBottom: `1px solid ${currentStatus.color}22`,
          }}>
            <div className="d-flex align-items-center gap-3">
              <div style={{
                width: 56, height: 56, borderRadius: '50%',
                background: '#fff',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                boxShadow: `0 2px 8px ${currentStatus.color}33`,
              }}>
                <currentStatus.icon size={26} color={currentStatus.color} />
              </div>
              <div>
                <div className="small text-muted">Güncel Durum</div>
                <div className="fs-4 fw-bold" style={{ color: currentStatus.color }}>
                  {currentStatus.label}
                </div>
              </div>
            </div>
          </div>

          <div className="card-body p-4">
            {/* Order Summary */}
            <div className="row g-3 mb-4">
              <div className="col-6">
                <div className="small text-muted">Sipariş No</div>
                <div className="fw-semibold" style={{ fontFamily: 'monospace', fontSize: '0.9rem' }}>
                  {order.orderNumber}
                </div>
              </div>
              <div className="col-6">
                <div className="small text-muted">Sipariş Tarihi</div>
                <div className="fw-semibold small">{formatDate(order.createdAt)}</div>
              </div>
              {order.maskedCustomerName && (
                <div className="col-6">
                  <div className="small text-muted">Müşteri</div>
                  <div className="fw-semibold small">{order.maskedCustomerName}</div>
                </div>
              )}
              {order.itemCount !== null && order.itemCount !== undefined && (
                <div className="col-6">
                  <div className="small text-muted">Ürün Sayısı</div>
                  <div className="fw-semibold small">{order.itemCount} adet</div>
                </div>
              )}
              <div className="col-6">
                <div className="small text-muted">Toplam Tutar</div>
                <div className="fw-bold text-primary">{formatPrice(order.grandTotal)}</div>
              </div>
              {order.estimatedDeliveryDate && !order.actualDeliveryDate && (
                <div className="col-6">
                  <div className="small text-muted">Tahmini Teslimat</div>
                  <div className="fw-semibold small">{formatDateShort(order.estimatedDeliveryDate)}</div>
                </div>
              )}
              {order.actualDeliveryDate && (
                <div className="col-6">
                  <div className="small text-muted">Teslimat Tarihi</div>
                  <div className="fw-semibold small text-success">
                    <FiCheckCircle className="me-1" />{formatDateShort(order.actualDeliveryDate)}
                  </div>
                </div>
              )}
            </div>

            {/* Cargo Tracking */}
            {order.cargoTrackingNo && (
              <div className="alert alert-info mb-4" style={{ borderRadius: 12 }}>
                <div className="d-flex justify-content-between align-items-center">
                  <div>
                    <div className="fw-semibold mb-1">
                      <FiTruck className="me-1" />{order.cargoProviderName || order.cargoCompany} Kargo
                    </div>
                    <div className="small">
                      Takip No: <strong style={{ fontFamily: 'monospace' }}>{order.cargoTrackingNo}</strong>
                    </div>
                  </div>
                  {order.cargoTrackingUrl && (
                    <a
                      href={order.cargoTrackingUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="btn btn-sm btn-primary"
                    >
                      <FiExternalLink className="me-1" />Kargoda Takip Et
                    </a>
                  )}
                </div>
              </div>
            )}

            {/* Status Timeline */}
            {order.statusHistory && order.statusHistory.length > 0 && (
              <div>
                <h6 className="fw-semibold mb-3">Sipariş Geçmişi</h6>
                <div className="position-relative">
                  {order.statusHistory.map((item, idx) => {
                    const cfg = STATUS_CONFIG[item.status] || STATUS_CONFIG.PENDING_PAYMENT;
                    const isLast = idx === order.statusHistory.length - 1;
                    return (
                      <div key={idx} className="d-flex gap-3 position-relative" style={{ paddingBottom: isLast ? 0 : 20 }}>
                        {/* Timeline dot + line */}
                        <div className="d-flex flex-column align-items-center" style={{ minWidth: 40 }}>
                          <div style={{
                            width: 36, height: 36, borderRadius: '50%',
                            background: cfg.bg, border: `2px solid ${cfg.color}`,
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                            flexShrink: 0, zIndex: 1
                          }}>
                            <cfg.icon size={16} color={cfg.color} />
                          </div>
                          {!isLast && (
                            <div style={{
                              width: 2, flex: 1,
                              background: '#e2e8f0', marginTop: 4
                            }} />
                          )}
                        </div>
                        {/* Content */}
                        <div className="flex-grow-1 pb-2">
                          <div className="d-flex justify-content-between align-items-start">
                            <div>
                              <div className="fw-semibold" style={{ color: cfg.color }}>
                                {item.statusLabel || cfg.label}
                              </div>
                              {item.note && (
                                <div className="text-muted small mt-1">{item.note}</div>
                              )}
                            </div>
                            <small className="text-muted" style={{ whiteSpace: 'nowrap' }}>
                              {formatDate(item.changedAt)}
                            </small>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {/* CTA */}
            <div className="text-center mt-4 pt-3 border-top">
              <p className="small text-muted mb-2">Tüm siparişlerinizi tek yerde görmek ister misiniz?</p>
              <Link to="/uye-ol" className="btn btn-outline-primary btn-sm me-2">
                Üye Ol
              </Link>
              <Link to="/giris" className="btn btn-outline-secondary btn-sm">
                Giriş Yap
              </Link>
            </div>
          </div>
        </div>
      )}

      {/* Info */}
      {!order && !loading && (
        <div className="text-center mt-4">
          <small className="text-muted">
            <FiAlertCircle className="me-1" />
            Sipariş numaranızı ve e-posta adresinizi sipariş onay e-postasında bulabilirsiniz.
          </small>
        </div>
      )}
    </div>
  );
}
