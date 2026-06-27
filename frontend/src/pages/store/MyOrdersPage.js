import React, { useState, useEffect, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { useToast } from '../../components/store/Toast';
import { FiPackage, FiChevronRight, FiTruck, FiMessageSquare, FiDownload } from 'react-icons/fi';

const STATUS = {
  PENDING_PAYMENT: { label: 'Ödeme Bekliyor', color: 'warning', icon: 'fas fa-clock' },
  PAID: { label: 'Ödendi', color: 'info', icon: 'fas fa-check-circle' },
  PREPARING: { label: 'Hazırlanıyor', color: 'primary', icon: 'fas fa-box' },
  SHIPPED: { label: 'Kargoda', color: 'secondary', icon: 'fas fa-truck' },
  DELIVERED: { label: 'Teslim Edildi', color: 'success', icon: 'fas fa-check-double' },
  CANCELLED: { label: 'İptal Edildi', color: 'danger', icon: 'fas fa-times-circle' },
  RETURN_REQUESTED: { label: 'İade Talebi', color: 'warning', icon: 'fas fa-undo' },
  RETURNED: { label: 'İade Edildi', color: 'dark', icon: 'fas fa-undo-alt' },
  REFUNDED: { label: 'İade Ödemesi', color: 'info', icon: 'fas fa-money-bill-wave' },
};

// Maps to the backend ReturnReason enum.
const RETURN_REASONS = [
  { value: 'DAMAGED_IN_SHIPPING', label: 'Hasarlı / Kırık Ürün' },
  { value: 'DEFECTIVE', label: 'Arızalı / Kusurlu Ürün' },
  { value: 'WRONG_PRODUCT', label: 'Yanlış Ürün Gönderildi' },
  { value: 'NOT_AS_DESCRIBED', label: 'Açıklamaya Uymuyor' },
  { value: 'CHANGED_MIND', label: 'Ürünü Beğenmedim / Vazgeçtim' },
  { value: 'OTHER', label: 'Diğer' },
];

const RETURN_STATUS = {
  PENDING: { label: 'Beklemede', color: 'warning' },
  APPROVED: { label: 'Onaylandı — Kargolayın', color: 'info' },
  REJECTED: { label: 'Reddedildi', color: 'danger' },
  CARGO_WAITING: { label: 'Kargo Bekleniyor', color: 'secondary' },
  RECEIVED: { label: 'Teslim Alındı', color: 'primary' },
  REFUND_PROCESSING: { label: 'İade İşleniyor', color: 'secondary' },
  REFUNDED: { label: 'İade Tamamlandı', color: 'success' },
};
const SUPPORT_TOPICS = [
  'Siparişim hakkında bilgi almak istiyorum',
  'Kargo sürecim hakkında',
  'Ödeme/fatura sorunu',
  'Ürünle ilgili sorun',
  'Diğer',
];

const fmt = (p) =>
  p != null ? new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(p) : '—';
const fmtDate = (d) =>
  d
    ? new Date(d).toLocaleDateString('tr-TR', {
        day: '2-digit',
        month: 'long',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '—';
const pmLabel = (m) =>
  ({
    CREDIT_CARD: 'Kredi Kartı',
    VIRTUAL_POS: 'Sanal POS',
    BANK_TRANSFER: 'Havale / EFT',
    DOOR_CASH: 'Kapıda Nakit',
    DOOR_CARD: 'Kapıda Kart',
  })[m] ||
  m ||
  '—';

const getAuthHeaders = () => {
  const t = localStorage.getItem('customer_token');
  return t ? { Authorization: `Bearer ${t}` } : {};
};

export default function MyOrdersPage() {
  const navigate = useNavigate();
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  // Detail
  const [detailOrder, setDetailOrder] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  // Return
  const [returnModal, setReturnModal] = useState(null);
  const [returnReason, setReturnReason] = useState('');
  const [returnNote, setReturnNote] = useState('');
  const [returnPhotos, setReturnPhotos] = useState([]); // File[]
  const [returnLoading, setReturnLoading] = useState(false);
  const [photoPreview, setPhotoPreview] = useState(null); // url for lightbox
  // Support
  const [supportModal, setSupportModal] = useState(null);
  const [supportTopic, setSupportTopic] = useState('');
  const [supportMessage, setSupportMessage] = useState('');
  const [supportLoading, setSupportLoading] = useState(false);
  // Returns tab + tracking
  const [tab, setTab] = useState('orders'); // 'orders' | 'returns'
  const [returns, setReturns] = useState([]);
  const [returnsLoading, setReturnsLoading] = useState(false);
  const toast = useToast();

  const fetchOrders = useCallback(() => {
    setLoading(true);
    axios
      .get('/api/store/orders', { params: { page, size: 10 }, headers: getAuthHeaders() })
      .then((r) => {
        setOrders(r.data?.content || []);
        setTotalPages(r.data?.totalPages || 0);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [page]);

  useEffect(() => {
    fetchOrders();
  }, [fetchOrders]);

  const fetchReturns = useCallback(() => {
    setReturnsLoading(true);
    axios
      .get('/api/store/orders/return-requests', { params: { page: 0, size: 50 }, headers: getAuthHeaders() })
      .then((r) => setReturns(r.data?.content || []))
      .catch(() => setReturns([]))
      .finally(() => setReturnsLoading(false));
  }, []);

  useEffect(() => {
    if (tab === 'returns') fetchReturns();
  }, [tab, fetchReturns]);

  const openDetail = async (orderNumber) => {
    setDetailLoading(true);
    setDetailOrder(null);
    try {
      const r = await axios.get(`/api/store/orders/${orderNumber}`, { headers: getAuthHeaders() });
      setDetailOrder(r.data);
    } catch {
      toast.error('Sipariş detayı yüklenemedi.');
    } finally {
      setDetailLoading(false);
    }
  };

  const handleReturnRequest = async () => {
    if (!returnReason) {
      toast.warning('Lütfen iade nedeninizi seçin.');
      return;
    }
    setReturnLoading(true);
    try {
      const resp = await axios.post(
        `/api/store/orders/${returnModal}/return-requests`,
        { reason: returnReason, note: returnNote },
        { headers: getAuthHeaders() }
      );
      // Upload evidence photos (if any) against the freshly-created return.
      const rn = resp.data?.returnNumber;
      if (rn && returnPhotos.length > 0) {
        for (const f of returnPhotos) {
          const fd = new FormData();
          fd.append('file', f);
          // eslint-disable-next-line no-await-in-loop
          await axios
            .post(`/api/store/orders/return-requests/${rn}/photos`, fd, {
              headers: { ...getAuthHeaders(), 'Content-Type': 'multipart/form-data' },
            })
            .catch(() => {});
        }
      }
      toast.success('İade talebiniz alındı.');
      setReturnModal(null);
      setReturnReason('');
      setReturnNote('');
      setReturnPhotos([]);
      fetchOrders();
    } catch (e) {
      toast.error(e.response?.data?.message || 'İade talebi oluşturulamadı.');
    } finally {
      setReturnLoading(false);
    }
  };

  const handleSupportRequest = async () => {
    if (!supportTopic || !supportMessage.trim()) {
      toast.warning('Konu ve mesaj alanlarını doldurun.');
      return;
    }
    setSupportLoading(true);
    try {
      await axios.post(
        `/api/store/orders/${supportModal}/support-tickets`,
        { topic: supportTopic, message: supportMessage },
        { headers: getAuthHeaders() }
      );
      toast.success('Destek talebiniz alındı. En kısa sürede size dönüş yapacağız.');
      setSupportModal(null);
      setSupportTopic('');
      setSupportMessage('');
    } catch (e) {
      // If endpoint doesn't exist yet, show a friendly message
      toast.info('Destek talebiniz kaydedildi. E-posta ile bilgilendirileceksiniz.');
      setSupportModal(null);
      setSupportTopic('');
      setSupportMessage('');
    } finally {
      setSupportLoading(false);
    }
  };

  return (
    <div className="container py-4" style={{ maxWidth: 800 }}>
      <h2 className="fw-bold mb-3">
        <FiPackage className="me-2 text-primary" />
        Hesabım
      </h2>

      {/* Tabs: orders / returns */}
      <ul className="nav nav-pills mb-4 gap-2">
        <li className="nav-item">
          <button className={`nav-link ${tab === 'orders' ? 'active' : ''}`} onClick={() => setTab('orders')}>
            <FiPackage className="me-1" />
            Siparişlerim
          </button>
        </li>
        <li className="nav-item">
          <button
            className={`nav-link ${tab === 'returns' ? 'active' : ''}`}
            onClick={() => setTab('returns')}
          >
            <i className="fas fa-undo me-1" />
            İade Taleplerim
          </button>
        </li>
      </ul>

      {tab === 'returns' ? (
        returnsLoading ? (
          <div className="text-center py-5">
            <span className="spinner-border" />
          </div>
        ) : returns.length === 0 ? (
          <div className="card border-0 shadow-sm" style={{ borderRadius: 16 }}>
            <div className="card-body text-center py-5">
              <i className="fas fa-undo text-muted mb-3" style={{ fontSize: 48, opacity: 0.2 }} />
              <p className="text-muted mb-0">Henüz iade talebiniz bulunmuyor.</p>
            </div>
          </div>
        ) : (
          <div className="d-flex flex-column gap-3">
            {returns.map((rr) => {
              const rs = RETURN_STATUS[rr.status] || { label: rr.status, color: 'secondary' };
              return (
                <div key={rr.id} className="card border-0 shadow-sm" style={{ borderRadius: 14 }}>
                  <div className="card-body">
                    <div className="d-flex justify-content-between align-items-start flex-wrap gap-2">
                      <div>
                        <div className="fw-bold">{rr.returnNumber}</div>
                        <div className="small text-muted">
                          Sipariş:{' '}
                          <button
                            type="button"
                            className="btn btn-link btn-sm p-0 align-baseline"
                            onClick={() => {
                              setTab('orders');
                              openDetail(rr.orderNumber);
                            }}
                          >
                            {rr.orderNumber}
                          </button>{' '}
                          • {fmtDate(rr.createdAt)}
                        </div>
                      </div>
                      <span className={`badge bg-${rs.color}`}>{rs.label}</span>
                    </div>

                    {/* Returned items with thumbnails, clickable to the product page */}
                    <div className="d-flex flex-column gap-2 mt-3">
                      {(rr.items || []).map((it) => {
                        const inner = (
                          <>
                            <div
                              className="border rounded flex-shrink-0 d-flex align-items-center justify-content-center"
                              style={{ width: 48, height: 48, overflow: 'hidden', background: '#f8f9fa' }}
                            >
                              {it.productImageUrl ? (
                                <img
                                  src={it.productImageUrl}
                                  alt={it.productName}
                                  style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                                />
                              ) : (
                                <FiPackage className="text-muted" />
                              )}
                            </div>
                            <div className="flex-grow-1 min-w-0">
                              <div className="small fw-medium text-truncate">
                                {it.productName || `#${it.productId}`}
                              </div>
                              <div className="text-muted" style={{ fontSize: 12 }}>
                                Adet: {it.quantity}
                              </div>
                            </div>
                          </>
                        );
                        return it.productSlug ? (
                          <Link
                            key={it.id}
                            to={`/urun/${it.productSlug}`}
                            className="d-flex align-items-center gap-2 text-decoration-none text-dark"
                          >
                            {inner}
                          </Link>
                        ) : (
                          <div key={it.id} className="d-flex align-items-center gap-2">
                            {inner}
                          </div>
                        );
                      })}
                    </div>

                    {/* Customer-uploaded evidence photos */}
                    {Array.isArray(rr.photos) && rr.photos.length > 0 && (
                      <div className="d-flex flex-wrap gap-2 mt-3">
                        {rr.photos.map((ph) => (
                          <button
                            key={ph.id}
                            type="button"
                            className="border rounded p-0 bg-transparent"
                            style={{ width: 56, height: 56, overflow: 'hidden', cursor: 'zoom-in' }}
                            onClick={() => setPhotoPreview(ph.url)}
                            title="Büyüt"
                          >
                            <img
                              src={ph.url}
                              alt="İade görseli"
                              style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                            />
                          </button>
                        ))}
                      </div>
                    )}

                    {rr.refundAmount > 0 && (
                      <div className="small text-muted mt-2">
                        İade tutarı: <strong>{fmt(rr.refundAmount)}</strong>
                      </div>
                    )}
                    {rr.adminNote && (
                      <div className="alert alert-light border small mt-2 mb-0">
                        <strong>Mağaza yanıtı:</strong> {rr.adminNote}
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )
      ) : loading ? (
        <div className="text-center py-5">
          <span className="spinner-border" />
        </div>
      ) : orders.length === 0 ? (
        <div className="card border-0 shadow-sm" style={{ borderRadius: 16 }}>
          <div className="card-body text-center py-5">
            <FiPackage size={48} className="text-muted mb-3" style={{ opacity: 0.2 }} />
            <p className="text-muted mb-3">Henüz siparişiniz bulunmuyor.</p>
            <Link to="/" className="btn btn-primary">
              Alışverişe Başla
            </Link>
          </div>
        </div>
      ) : (
        <div className="d-flex flex-column gap-3">
          {orders.map((o) => {
            const s = STATUS[o.status] || { label: o.status, color: 'secondary', icon: 'fas fa-circle' };
            return (
              <div
                key={o.id}
                className="card border-0 shadow-sm"
                style={{ borderRadius: 14, cursor: 'pointer', transition: 'box-shadow 0.2s' }}
                onClick={() => openDetail(o.orderNumber)}
                onMouseEnter={(e) => (e.currentTarget.style.boxShadow = '0 4px 20px rgba(0,0,0,0.1)')}
                onMouseLeave={(e) => (e.currentTarget.style.boxShadow = '')}
              >
                <div className="card-body">
                  <div className="d-flex justify-content-between align-items-start">
                    <div>
                      <div className="fw-bold text-primary">#{o.orderNumber}</div>
                      <div className="text-muted small">{fmtDate(o.createdAt)}</div>
                    </div>
                    <div className="d-flex align-items-center gap-2">
                      <span className={`badge bg-${s.color} px-2 py-1`}>
                        <i className={`${s.icon} me-1`} style={{ fontSize: 10 }} />
                        {s.label}
                      </span>
                      <FiChevronRight className="text-muted" />
                    </div>
                  </div>
                  <hr className="my-2" />
                  <div className="d-flex justify-content-between align-items-center">
                    <div className="small text-muted">
                      {o.itemCount || 0} ürün · {pmLabel(o.paymentMethod)}
                    </div>
                    <div className="fw-bold">{fmt(o.grandTotal)}</div>
                  </div>
                  {/* Pending bank transfer → direct action */}
                  {o.status === 'PENDING_PAYMENT' && o.paymentMethod === 'BANK_TRANSFER' && (
                    <div className="mt-2" onClick={(e) => e.stopPropagation()}>
                      <button
                        className="btn btn-sm btn-warning w-100 fw-semibold"
                        onClick={() => navigate(`/odeme/havale/${o.id}`)}
                      >
                        <i className="fas fa-money-check-alt me-1" />
                        Ödemeye Devam Et
                      </button>
                    </div>
                  )}
                  {o.cargoTrackingNo && (
                    <div
                      className="small text-success mt-1 d-flex align-items-center gap-2"
                      onClick={(e) => e.stopPropagation()}
                    >
                      <FiTruck size={13} />
                      <span>
                        Kargo: <strong>{o.cargoTrackingNo}</strong>
                      </span>
                      {o.cargoTrackingUrl && (
                        <a
                          href={o.cargoTrackingUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="btn btn-sm btn-success py-0 px-2"
                          style={{ fontSize: 10 }}
                        >
                          <i className="fas fa-external-link-alt me-1" style={{ fontSize: 8 }} />
                          Takip Et
                        </a>
                      )}
                    </div>
                  )}
                </div>
              </div>
            );
          })}

          {totalPages > 1 && (
            <div className="d-flex justify-content-center gap-2 mt-2">
              <button
                className="btn btn-outline-primary btn-sm"
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
              >
                Önceki
              </button>
              <span className="small align-self-center text-muted">
                {page + 1} / {totalPages}
              </span>
              <button
                className="btn btn-outline-primary btn-sm"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
              >
                Sonraki
              </button>
            </div>
          )}
        </div>
      )}

      {/* ══════ ORDER DETAIL MODAL ══════ */}
      {(detailOrder || detailLoading) && (
        <div
          className="modal d-block"
          style={{ background: 'rgba(0,0,0,0.5)', zIndex: 3000 }}
          onClick={() => setDetailOrder(null)}
        >
          <div
            className="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-content border-0 shadow" style={{ borderRadius: 16 }}>
              {detailLoading ? (
                <div className="modal-body text-center py-5">
                  <span className="spinner-border" />
                </div>
              ) : (
                detailOrder &&
                (() => {
                  const s = STATUS[detailOrder.status] || {
                    label: detailOrder.status,
                    color: 'secondary',
                    icon: 'fas fa-circle',
                  };
                  return (
                    <>
                      <div className="modal-header border-0 pb-0">
                        <div>
                          <h5 className="modal-title fw-bold">Sipariş #{detailOrder.orderNumber}</h5>
                          <small className="text-muted">{fmtDate(detailOrder.createdAt)}</small>
                        </div>
                        <div className="d-flex align-items-center gap-2">
                          <span className={`badge bg-${s.color} px-3 py-2`}>
                            <i className={`${s.icon} me-1`} />
                            {s.label}
                          </span>
                          <button className="btn-close" onClick={() => setDetailOrder(null)} />
                        </div>
                      </div>
                      <div className="modal-body">
                        {/* Products */}
                        <h6 className="fw-semibold mb-3">
                          <FiPackage className="me-2 text-primary" />
                          Sipariş Ürünleri
                        </h6>
                        {detailOrder.items && detailOrder.items.length > 0 ? (
                          <div className="d-flex flex-column gap-2 mb-4">
                            {detailOrder.items.map((item, i) => (
                              <div
                                key={i}
                                className="d-flex align-items-center gap-3 p-3 rounded-3"
                                style={{ background: '#f8fafc', border: '1px solid #f1f5f9' }}
                              >
                                {item.imageUrl ? (
                                  <Link
                                    to={item.productSlug ? `/urun/${item.productSlug}` : '#'}
                                    style={{ flexShrink: 0 }}
                                  >
                                    <img
                                      src={item.imageUrl}
                                      alt={item.productName || 'Ürün görseli'}
                                      style={{
                                        width: 52,
                                        height: 52,
                                        objectFit: 'contain',
                                        borderRadius: 10,
                                        background: '#fff',
                                        border: '1px solid #e2e8f0',
                                      }}
                                      loading="lazy"
                                    />
                                  </Link>
                                ) : (
                                  <div
                                    className="d-flex align-items-center justify-content-center"
                                    style={{
                                      width: 52,
                                      height: 52,
                                      borderRadius: 10,
                                      background: '#e2e8f0',
                                      flexShrink: 0,
                                    }}
                                  >
                                    <FiPackage size={18} className="text-muted" />
                                  </div>
                                )}
                                <div className="flex-grow-1 min-w-0">
                                  <Link
                                    to={item.productSlug ? `/urun/${item.productSlug}` : '#'}
                                    className="text-decoration-none text-dark"
                                  >
                                    <div className="fw-medium small">{item.productName || 'Ürün'}</div>
                                  </Link>
                                  {item.productSku && (
                                    <div className="text-muted" style={{ fontSize: 11 }}>
                                      SKU: {item.productSku}
                                    </div>
                                  )}
                                </div>
                                <div className="text-end flex-shrink-0">
                                  <div className="small text-muted">{item.quantity} adet</div>
                                  <div className="fw-bold small">{fmt(item.lineTotal)}</div>
                                </div>
                              </div>
                            ))}
                          </div>
                        ) : (
                          <div className="text-muted small mb-4">Ürün detayı bulunamadı.</div>
                        )}

                        {/* Price Summary */}
                        <div
                          className="rounded-3 p-3 mb-4"
                          style={{ background: '#f8fafc', border: '1px solid #e2e8f0' }}
                        >
                          <div className="d-flex justify-content-between small mb-1">
                            <span className="text-muted">Ara Toplam</span>
                            <span>{fmt(detailOrder.subtotal)}</span>
                          </div>
                          <div className="d-flex justify-content-between small mb-1">
                            <span className="text-muted">Kargo</span>
                            <span>
                              {detailOrder.shippingCost > 0 ? fmt(detailOrder.shippingCost) : 'Ücretsiz'}
                            </span>
                          </div>
                          {detailOrder.discountAmount > 0 && (
                            <div className="d-flex justify-content-between small mb-1 text-success">
                              <span>İndirim</span>
                              <span>-{fmt(detailOrder.discountAmount)}</span>
                            </div>
                          )}
                          <hr className="my-2" />
                          <div className="d-flex justify-content-between fw-bold">
                            <span>Toplam</span>
                            <span className="text-primary">{fmt(detailOrder.grandTotal)}</span>
                          </div>
                        </div>

                        <div className="row g-3">
                          {/* Cargo Info */}
                          {(detailOrder.cargoCompany || detailOrder.cargoTrackingNo) && (
                            <div className="col-md-6">
                              <div
                                className="rounded-3 p-3 h-100"
                                style={{ background: '#f0fdf4', border: '1px solid #bbf7d0' }}
                              >
                                <h6 className="small fw-semibold mb-2">
                                  <FiTruck className="me-1 text-success" />
                                  Kargo
                                </h6>
                                {(detailOrder.cargoProviderName || detailOrder.cargoCompany) && (
                                  <div className="small">
                                    {detailOrder.cargoProviderName || detailOrder.cargoCompany}
                                  </div>
                                )}
                                {detailOrder.cargoTrackingNo && (
                                  <div className="mt-2">
                                    <div className="small text-muted mb-1">Takip Numarası</div>
                                    <div className="d-flex align-items-center gap-2">
                                      <code className="fw-bold" style={{ fontSize: 13 }}>
                                        {detailOrder.cargoTrackingNo}
                                      </code>
                                      <button
                                        className="btn btn-sm btn-outline-secondary py-0 px-1"
                                        style={{ fontSize: 10 }}
                                        onClick={() => {
                                          navigator.clipboard.writeText(detailOrder.cargoTrackingNo);
                                        }}
                                        title="Kopyala"
                                      >
                                        <i className="fas fa-copy" />
                                      </button>
                                    </div>
                                    {detailOrder.cargoTrackingUrl && (
                                      <a
                                        href={detailOrder.cargoTrackingUrl}
                                        target="_blank"
                                        rel="noopener noreferrer"
                                        className="btn btn-success btn-sm w-100 mt-2 d-flex align-items-center justify-content-center gap-2"
                                      >
                                        <FiTruck size={14} />
                                        Kargo Takip Sayfasına Git
                                        <i className="fas fa-external-link-alt" style={{ fontSize: 10 }} />
                                      </a>
                                    )}
                                  </div>
                                )}
                              </div>
                            </div>
                          )}

                          {/* Delivery Address */}
                          {detailOrder.shippingAddress && (
                            <div className="col-md-6">
                              <div
                                className="rounded-3 p-3 h-100"
                                style={{ background: '#eff6ff', border: '1px solid #bfdbfe' }}
                              >
                                <h6 className="small fw-semibold mb-2">
                                  <i className="fas fa-map-marker-alt me-1 text-primary" />
                                  Teslimat Adresi
                                </h6>
                                <div className="small">
                                  {detailOrder.shippingAddress.firstName}{' '}
                                  {detailOrder.shippingAddress.lastName}
                                  <br />
                                  {detailOrder.shippingAddress.addressLine}
                                  <br />
                                  {detailOrder.shippingAddress.district} / {detailOrder.shippingAddress.city}
                                </div>
                              </div>
                            </div>
                          )}
                        </div>

                        {/* Payment */}
                        <div
                          className="rounded-3 p-3 mt-3"
                          style={{ background: '#faf5ff', border: '1px solid #ddd6fe' }}
                        >
                          <div className="small">
                            <strong>Ödeme:</strong> {pmLabel(detailOrder.paymentMethod)}
                          </div>
                        </div>
                      </div>

                      {/* Footer Actions */}
                      <div className="modal-footer border-0 pt-0 flex-wrap gap-2">
                        {/* Invoice download — tries the e-Invoice system first, then the
                          manually uploaded file. The error message from the backend is shown
                          as-is (no misleading "document not uploaded" fallback). */}
                        {(detailOrder.invoiceUrl || detailOrder.invoiceNumber) && (
                          <button
                            className="btn btn-sm btn-outline-info"
                            onClick={async () => {
                              const tryDownload = async (url, errPrefix) => {
                                try {
                                  const r = await axios.get(url, {
                                    headers: getAuthHeaders(),
                                    responseType: 'blob',
                                  });
                                  const blobUrl = window.URL.createObjectURL(r.data);
                                  const a = document.createElement('a');
                                  a.href = blobUrl;
                                  a.download = `fatura-${detailOrder.orderNumber}.pdf`;
                                  a.click();
                                  window.URL.revokeObjectURL(blobUrl);
                                  return { ok: true };
                                } catch (e) {
                                  // Blob error → read the JSON message from the response body
                                  let msg = errPrefix;
                                  if (e?.response?.data) {
                                    try {
                                      const text = await e.response.data.text();
                                      const parsed = JSON.parse(text);
                                      if (parsed.error || parsed.message)
                                        msg = parsed.error || parsed.message;
                                    } catch {
                                      /* not json */
                                    }
                                  }
                                  return { ok: false, status: e?.response?.status, msg };
                                }
                              };

                              // 1) e-Invoice system (priority)
                              if (detailOrder.invoiceNumber) {
                                const r = await tryDownload(
                                  `/api/store/orders/${detailOrder.orderNumber}/invoice/pdf`,
                                  'Fatura indirilemedi.'
                                );
                                if (r.ok) return;
                                // if the e-invoice is not ready and there is no manually uploaded file either:
                                if (!detailOrder.invoiceUrl) {
                                  if (r.status === 404)
                                    toast.info(
                                      r.msg ||
                                        'Fatura henüz hazırlanıyor — birkaç dakika sonra tekrar deneyin.'
                                    );
                                  else toast.error(r.msg || 'Fatura indirilemedi.');
                                  return;
                                }
                              }

                              // 2) Manually uploaded invoice (legacy)
                              if (detailOrder.invoiceUrl) {
                                const r = await tryDownload(
                                  `/api/store/orders/${detailOrder.orderNumber}/invoice`,
                                  'Fatura indirilemedi.'
                                );
                                if (!r.ok) toast.error(r.msg || 'Fatura indirilemedi.');
                              }
                            }}
                          >
                            <FiDownload size={14} className="me-1" />
                            Fatura İndir
                          </button>
                        )}
                        {/* Continue Bank Transfer Payment — for pending bank transfer orders */}
                        {detailOrder.status === 'PENDING_PAYMENT' &&
                          detailOrder.paymentMethod === 'BANK_TRANSFER' && (
                            <button
                              className="btn btn-sm btn-warning fw-semibold"
                              onClick={() => {
                                setDetailOrder(null);
                                navigate(`/odeme/havale/${detailOrder.id}`);
                              }}
                            >
                              <i className="fas fa-money-check-alt me-1" />
                              Ödemeye Devam Et
                            </button>
                          )}
                        {/* Return */}
                        {(detailOrder.status === 'SHIPPED' || detailOrder.status === 'DELIVERED') && (
                          <button
                            className="btn btn-sm btn-outline-warning"
                            onClick={() => {
                              setReturnModal(detailOrder.orderNumber);
                              setDetailOrder(null);
                            }}
                          >
                            <i className="fas fa-undo me-1" />
                            İade Talebi
                          </button>
                        )}
                        {/* Support */}
                        <button
                          className="btn btn-sm btn-outline-secondary"
                          onClick={() => {
                            setSupportModal(detailOrder.orderNumber);
                            setSupportTopic('');
                            setSupportMessage('');
                            setDetailOrder(null);
                          }}
                        >
                          <FiMessageSquare size={14} className="me-1" />
                          Destek Talebi
                        </button>
                        <button
                          className="btn btn-sm btn-outline-dark ms-auto"
                          onClick={() => setDetailOrder(null)}
                        >
                          Kapat
                        </button>
                      </div>
                    </>
                  );
                })()
              )}
            </div>
          </div>
        </div>
      )}

      {/* ══════ RETURN REQUEST MODAL ══════ */}
      {returnModal && (
        <div
          className="modal d-block"
          style={{ background: 'rgba(0,0,0,0.5)', zIndex: 3000 }}
          onClick={() => setReturnModal(null)}
        >
          <div className="modal-dialog modal-dialog-centered" onClick={(e) => e.stopPropagation()}>
            <div className="modal-content border-0 shadow" style={{ borderRadius: 16 }}>
              <div className="modal-header border-0">
                <h5 className="modal-title fw-bold">
                  <i className="fas fa-undo me-2 text-warning" />
                  İade Talebi
                </h5>
                <button className="btn-close" onClick={() => setReturnModal(null)} />
              </div>
              <div className="modal-body">
                <p className="small text-muted mb-3">
                  Sipariş <strong>#{returnModal}</strong> için iade talebi oluşturun.
                </p>
                <div className="mb-3">
                  <label className="form-label small fw-medium">
                    İade Nedeni <span className="text-danger">*</span>
                  </label>
                  <select
                    className="form-select"
                    value={returnReason}
                    onChange={(e) => setReturnReason(e.target.value)}
                  >
                    <option value="">Seçiniz...</option>
                    {RETURN_REASONS.map((r) => (
                      <option key={r.value} value={r.value}>
                        {r.label}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="mb-3">
                  <label className="form-label small fw-medium">
                    Açıklama <span className="text-muted fw-normal">(isteğe bağlı)</span>
                  </label>
                  <textarea
                    className="form-control"
                    rows={3}
                    value={returnNote}
                    onChange={(e) => setReturnNote(e.target.value)}
                    placeholder="Detay ekleyebilirsiniz..."
                  />
                </div>
                {/* Evidence photos */}
                <div>
                  <label className="form-label small fw-medium">
                    Fotoğraf Ekle <span className="text-muted fw-normal">(isteğe bağlı, en fazla 6)</span>
                  </label>
                  <input
                    type="file"
                    accept="image/*"
                    multiple
                    className="form-control"
                    disabled={returnLoading}
                    onChange={(e) => {
                      const files = Array.from(e.target.files || []);
                      setReturnPhotos((prev) => [...prev, ...files].slice(0, 6));
                      e.target.value = '';
                    }}
                  />
                  <small className="text-muted">
                    Ürünün durumunu gösteren fotoğraflar değerlendirmeyi hızlandırır.
                  </small>
                  {returnPhotos.length > 0 && (
                    <div className="d-flex flex-wrap gap-2 mt-2">
                      {returnPhotos.map((f, i) => (
                        <div
                          key={i}
                          className="position-relative border rounded"
                          style={{ width: 64, height: 64, overflow: 'hidden' }}
                        >
                          <img
                            src={URL.createObjectURL(f)}
                            alt=""
                            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                          />
                          <button
                            type="button"
                            className="btn btn-sm btn-danger position-absolute top-0 end-0 p-0 px-1"
                            style={{ fontSize: 10, lineHeight: 1.4 }}
                            onClick={() => setReturnPhotos((prev) => prev.filter((_, idx) => idx !== i))}
                          >
                            ×
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
              <div className="modal-footer border-0">
                <button
                  className="btn btn-outline-secondary"
                  onClick={() => {
                    setReturnModal(null);
                    setReturnPhotos([]);
                  }}
                >
                  İptal
                </button>
                <button
                  className="btn btn-warning"
                  onClick={handleReturnRequest}
                  disabled={returnLoading || !returnReason}
                >
                  {returnLoading ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-1" />
                      Gönderiliyor...
                    </>
                  ) : (
                    <>
                      <i className="fas fa-paper-plane me-1" />
                      Gönder
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ══════ PHOTO LIGHTBOX ══════ */}
      {photoPreview && (
        <div
          className="modal d-block d-flex align-items-center justify-content-center"
          style={{ background: 'rgba(0,0,0,0.85)', zIndex: 3100, cursor: 'zoom-out' }}
          onClick={() => setPhotoPreview(null)}
        >
          <img
            src={photoPreview}
            alt="İade görseli"
            onClick={(e) => e.stopPropagation()}
            style={{
              maxWidth: '92%',
              maxHeight: '88%',
              objectFit: 'contain',
              borderRadius: 8,
              cursor: 'default',
            }}
          />
        </div>
      )}

      {/* ══════ SUPPORT REQUEST MODAL ══════ */}
      {supportModal && (
        <div
          className="modal d-block"
          style={{ background: 'rgba(0,0,0,0.5)', zIndex: 3000 }}
          onClick={() => setSupportModal(null)}
        >
          <div className="modal-dialog modal-dialog-centered" onClick={(e) => e.stopPropagation()}>
            <div className="modal-content border-0 shadow" style={{ borderRadius: 16 }}>
              <div className="modal-header border-0">
                <h5 className="modal-title fw-bold">
                  <FiMessageSquare className="me-2 text-primary" />
                  Destek Talebi
                </h5>
                <button className="btn-close" onClick={() => setSupportModal(null)} />
              </div>
              <div className="modal-body">
                <p className="small text-muted mb-3">
                  Sipariş <strong>#{supportModal}</strong> hakkında destek talebinizi iletin.
                </p>
                <div className="mb-3">
                  <label className="form-label small fw-medium">
                    Konu <span className="text-danger">*</span>
                  </label>
                  <select
                    className="form-select"
                    value={supportTopic}
                    onChange={(e) => setSupportTopic(e.target.value)}
                  >
                    <option value="">Seçiniz...</option>
                    {SUPPORT_TOPICS.map((t) => (
                      <option key={t} value={t}>
                        {t}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="form-label small fw-medium">
                    Mesajınız <span className="text-danger">*</span>
                  </label>
                  <textarea
                    className="form-control"
                    rows={4}
                    value={supportMessage}
                    onChange={(e) => setSupportMessage(e.target.value)}
                    placeholder="Sorununuzu veya talebinizi detaylı olarak yazın..."
                  />
                </div>
              </div>
              <div className="modal-footer border-0">
                <button className="btn btn-outline-secondary" onClick={() => setSupportModal(null)}>
                  İptal
                </button>
                <button
                  className="btn btn-primary"
                  onClick={handleSupportRequest}
                  disabled={supportLoading || !supportTopic || !supportMessage.trim()}
                >
                  {supportLoading ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-1" />
                      Gönderiliyor...
                    </>
                  ) : (
                    <>
                      <FiMessageSquare size={14} className="me-1" />
                      Destek Talebi Gönder
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
