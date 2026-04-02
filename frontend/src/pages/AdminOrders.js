import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import PaginationControls from '../components/PaginationControls';
import useSecurityCodePrompt from '../components/useSecurityCodePrompt';

const STATUS_CONFIG = {
  PENDING_PAYMENT: { label: 'Ödeme Bekliyor', color: 'warning', icon: 'fas fa-clock' },
  PAID: { label: 'Ödendi', color: 'info', icon: 'fas fa-check-circle' },
  PREPARING: { label: 'Hazırlanıyor', color: 'primary', icon: 'fas fa-box' },
  SHIPPED: { label: 'Kargoda', color: 'secondary', icon: 'fas fa-truck' },
  DELIVERED: { label: 'Teslim Edildi', color: 'success', icon: 'fas fa-check-double' },
  CANCELLED: { label: 'İptal', color: 'danger', icon: 'fas fa-times-circle' },
  RETURN_REQUESTED: { label: 'İade Talebi', color: 'warning', icon: 'fas fa-undo' },
  RETURNED: { label: 'İade Edildi', color: 'dark', icon: 'fas fa-undo-alt' },
  REFUNDED: { label: 'İade Ödendi', color: 'dark', icon: 'fas fa-money-bill-wave' },
};

const PAYMENT_LABELS = {
  CREDIT_CARD: 'Kredi Kartı', VIRTUAL_POS: 'Sanal POS', BANK_TRANSFER: 'Havale / EFT',
  DOOR_CASH: 'Kapıda Nakit', DOOR_CARD: 'Kapıda Kart', IYZICO: 'iyzico',
};
const CHANGED_BY_LABELS = { system: 'Sistem', SYSTEM: 'Sistem', ADMIN: 'Yönetici', PAYMENT: 'Ödeme Sistemi' };

const paymentLabel = (m) => PAYMENT_LABELS[m] || m || '—';
const changedByLabel = (s) => CHANGED_BY_LABELS[s] || s || '';

const formatPrice = (p) => p != null ? new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(p) : '—';
const formatDate = (d) => d ? new Date(d).toLocaleDateString('tr-TR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—';

export default function AdminOrders() {
  const [orders, setOrders] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [statusFilter, setStatusFilter] = useState('');
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [orderDetail, setOrderDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [showStatusModal, setShowStatusModal] = useState(false);
  const [newStatus, setNewStatus] = useState('');
  const [statusNote, setStatusNote] = useState('');
  const [showCargoModal, setShowCargoModal] = useState(false);
  const [cargoCompany, setCargoCompany] = useState('');
  const [cargoTrackingNo, setCargoTrackingNo] = useState('');
  const [allowedTransitions, setAllowedTransitions] = useState([]);
  const { askCode, SecurityCodePrompt } = useSecurityCodePrompt();

  const fetchOrders = useCallback(() => {
    setLoading(true);
    const params = { page, size: 15, sortBy: 'createdAt', sortDir: 'desc' };
    if (statusFilter) params.status = statusFilter;
    axios.get('/api/admin/orders', { params })
      .then(r => { setOrders(r.data?.content || []); setTotalPages(r.data?.totalPages || 0); setTotalElements(r.data?.totalElements || 0); })
      .catch(() => {}).finally(() => setLoading(false));
  }, [page, statusFilter]);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { fetchOrders(); }, [fetchOrders]);

  const [detailError, setDetailError] = useState('');
  const openDetail = async (orderId) => {
    setSelectedOrder(orderId); setDetailLoading(true); setDetailError('');
    try { const res = await axios.get(`/api/admin/orders/${orderId}`); setOrderDetail(res.data); }
    catch (e) { setDetailError(e.response?.data?.message || 'Sipariş detayı yüklenemedi.'); }
    finally { setDetailLoading(false); }
  };
  const closeDetail = () => { setSelectedOrder(null); setOrderDetail(null); };

  const fetchAllowedTransitions = async (orderId) => {
    try {
      const res = await axios.get(`/api/admin/orders/${orderId}/allowed-transitions`);
      setAllowedTransitions(res.data || []);
      if (res.data?.length > 0) setNewStatus(res.data[0].status);
    } catch { setAllowedTransitions([]); }
  };

  const updateStatus = async () => {
    try { await axios.put(`/api/admin/orders/${selectedOrder}/status`, { status: newStatus, note: statusNote }); setShowStatusModal(false); setStatusNote(''); openDetail(selectedOrder); fetchOrders(); }
    catch (e) { alert(e.response?.data?.message || 'Hata oluştu'); }
  };

  const updateCargo = async () => {
    try { await axios.put(`/api/admin/orders/${selectedOrder}/cargo`, { cargoCompany, cargoTrackingNo }); setShowCargoModal(false); openDetail(selectedOrder); fetchOrders(); }
    catch (e) { alert(e.response?.data?.message || 'Hata oluştu'); }
  };

  const confirmPayment = async () => {
    const code = await askCode({ description: 'Havale/EFT ödemesini onaylamak için güvenlik şifresini girin.' });
    if (!code) return;
    try {
      await axios.put(`/api/admin/orders/${selectedOrder}/confirm-payment`, {}, {
        headers: { 'X-ADMIN-SECURITY-CODE': code }
      });
      openDetail(selectedOrder); fetchOrders();
    } catch (e) {
      alert(e.response?.status === 403 ? 'Güvenlik şifresi hatalı.' : (e.response?.data?.message || 'Hata oluştu'));
    }
  };

  const StatusBadge = ({ status }) => {
    const cfg = STATUS_CONFIG[status] || { label: status, color: 'secondary', icon: '' };
    return <span className={`badge bg-${cfg.color}`}>{cfg.icon && <i className={`${cfg.icon} me-1`} />}{cfg.label}</span>;
  };

  return (
    <div>
      {SecurityCodePrompt}
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div><h2 className="mb-1">Siparişler</h2><small className="text-muted">{totalElements} sipariş</small></div>
      </div>

      {/* Filters */}
      <div className="card border-0 shadow-sm mb-4">
        <div className="card-body py-3">
          <div className="d-flex gap-2 flex-wrap align-items-center">
            <span className="text-muted small fw-medium">Durum:</span>
            <button className={`btn btn-sm ${!statusFilter ? 'btn-primary' : 'btn-outline-secondary'}`} onClick={() => { setStatusFilter(''); setPage(0); }}>Tümü</button>
            {Object.entries(STATUS_CONFIG).slice(0, 6).map(([key, cfg]) => (
              <button key={key} className={`btn btn-sm ${statusFilter === key ? `btn-${cfg.color}` : 'btn-outline-secondary'}`} onClick={() => { setStatusFilter(key); setPage(0); }}>{cfg.label}</button>
            ))}
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="card border-0 shadow-sm">
        <div className="table-responsive">
          <table className="table table-hover align-middle mb-0">
            <thead className="table-light"><tr><th>Sipariş No</th><th>Müşteri</th><th>Durum</th><th>Ödeme</th><th className="text-end">Tutar</th><th>Kargo</th><th>Tarih</th><th style={{width:80}}></th></tr></thead>
            <tbody>
              {loading ? <tr><td colSpan={8} className="text-center py-5"><div className="spinner-border spinner-border-sm" /></td></tr>
              : orders.length === 0 ? <tr><td colSpan={8} className="text-center py-5 text-muted">Sipariş bulunamadı</td></tr>
              : orders.map(o => (
                <tr key={o.id} style={{cursor:'pointer'}} onClick={() => openDetail(o.id)}>
                  <td><strong className="text-primary">{o.orderNumber}</strong></td>
                  <td><div>{o.customerName}</div><small className="text-muted">{o.customerEmail}</small></td>
                  <td><StatusBadge status={o.status} /></td>
                  <td><small className="text-muted">{paymentLabel(o.paymentMethod)}</small></td>
                  <td className="text-end fw-bold">{formatPrice(o.grandTotal)}</td>
                  <td>{o.cargoTrackingNo ? <small className="text-success"><i className="fas fa-truck me-1" />{o.cargoTrackingNo}</small> : <small className="text-muted">—</small>}</td>
                  <td><small className="text-muted">{formatDate(o.createdAt)}</small></td>
                  <td><button className="btn btn-sm btn-outline-primary" onClick={e => { e.stopPropagation(); openDetail(o.id); }}>Detay</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
      {totalPages > 1 && <div className="mt-3"><PaginationControls page={page} totalPages={totalPages} onPageChange={setPage} /></div>}

      {/* Detail Modal */}
      {selectedOrder && (
        <div className="modal show d-block" style={{background:'rgba(0,0,0,0.5)',zIndex:3000}} onClick={closeDetail}>
          <div className="modal-dialog modal-xl modal-dialog-scrollable" onClick={e => e.stopPropagation()}>
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">Sipariş Detay {orderDetail && <span className="text-primary">#{orderDetail.orderNumber}</span>}</h5>
                <button className="btn-close" onClick={closeDetail} />
              </div>
              <div className="modal-body">
                {detailLoading ? <div className="text-center py-5"><div className="spinner-border" /></div>
                : detailError ? <div className="alert alert-danger">{detailError}</div>
                : orderDetail ? (
                  <div className="row g-4">
                    <div className="col-lg-8">
                      {/* Actions */}
                      <div className="card mb-3"><div className="card-body d-flex justify-content-between align-items-center flex-wrap gap-2">
                        <div><StatusBadge status={orderDetail.status} /><span className="ms-2 text-muted small">{formatDate(orderDetail.createdAt)}</span></div>
                        <div className="d-flex gap-2">
                          <button className="btn btn-sm btn-outline-primary" onClick={() => { fetchAllowedTransitions(orderDetail.id); setShowStatusModal(true); }}><i className="fas fa-edit me-1" />Durum Güncelle</button>
                          <button className="btn btn-sm btn-outline-secondary" onClick={() => { setCargoCompany(orderDetail.cargoCompany||''); setCargoTrackingNo(orderDetail.cargoTrackingNo||''); setShowCargoModal(true); }}><i className="fas fa-truck me-1" />Kargo</button>
                          {orderDetail.status === 'PENDING_PAYMENT' && orderDetail.paymentMethod === 'BANK_TRANSFER' && (
                            <button className="btn btn-sm btn-success" onClick={confirmPayment}><i className="fas fa-check me-1" />Havale Onayla</button>
                          )}
                        </div>
                      </div></div>

                      {/* Items */}
                      <div className="card mb-3">
                        <div className="card-header bg-transparent"><h6 className="mb-0">Sipariş Kalemleri ({orderDetail.items?.length || 0})</h6></div>
                        <div className="card-body p-0">
                          <table className="table mb-0"><thead className="table-light"><tr><th>Ürün</th><th>SKU</th><th className="text-center">Adet</th><th className="text-end">Birim</th><th className="text-end">Toplam</th></tr></thead>
                          <tbody>{orderDetail.items?.map(item => (
                            <tr key={item.id}><td className="fw-medium">{item.productName}</td><td><code className="small">{item.productSku}</code></td><td className="text-center">{item.quantity}</td><td className="text-end">{formatPrice(item.unitPrice)}</td><td className="text-end fw-bold">{formatPrice(item.lineTotal)}</td></tr>
                          ))}</tbody></table>
                        </div>
                      </div>

                      {/* Timeline */}
                      {orderDetail.statusHistory?.length > 0 && (
                        <div className="card">
                          <div className="card-header bg-transparent"><h6 className="mb-0"><i className="fas fa-history me-2 text-primary" />Sipariş Zaman Çizelgesi</h6></div>
                          <div className="card-body">
                            <div className="position-relative" style={{paddingLeft:24}}>
                              <div className="position-absolute" style={{left:8,top:0,bottom:0,width:2,background:'#dee2e6'}} />
                              {orderDetail.statusHistory.map((h,i) => {
                                const cfg = STATUS_CONFIG[h.newStatus] || { color: 'secondary', icon: 'fas fa-circle' };
                                return (
                                  <div key={i} className="d-flex mb-3 position-relative">
                                    <div className={`position-absolute bg-${cfg.color} rounded-circle d-flex align-items-center justify-content-center`}
                                      style={{left:-24,top:2,width:18,height:18,zIndex:1}}>
                                      <i className={`${cfg.icon} text-white`} style={{fontSize:8}} />
                                    </div>
                                    <div className="flex-grow-1 ms-2">
                                      <div className="d-flex justify-content-between align-items-start">
                                        <div>
                                          <StatusBadge status={h.newStatus} />
                                          {h.oldStatus && <small className="text-muted ms-2"><i className="fas fa-arrow-left me-1" />{(STATUS_CONFIG[h.oldStatus]||{}).label || h.oldStatus}</small>}
                                        </div>
                                        <small className="text-muted">{formatDate(h.createdAt)}</small>
                                      </div>
                                      {h.note && <p className="small text-muted mt-1 mb-0">{h.note}</p>}
                                      <small className="text-muted">{changedByLabel(h.changedBy)}</small>
                                    </div>
                                  </div>
                                );
                              })}
                            </div>
                          </div>
                        </div>
                      )}
                    </div>

                    <div className="col-lg-4">
                      {/* Summary */}
                      <div className="card mb-3"><div className="card-header bg-transparent"><h6 className="mb-0">Fiyat Özeti</h6></div><div className="card-body">
                        <div className="d-flex justify-content-between mb-2"><span className="text-muted">Ara Toplam</span><span>{formatPrice(orderDetail.subtotal)}</span></div>
                        <div className="d-flex justify-content-between mb-2"><span className="text-muted">Kargo</span><span>{formatPrice(orderDetail.shippingCost)}</span></div>
                        {orderDetail.discountAmount > 0 && <div className="d-flex justify-content-between mb-2 text-success"><span>İndirim</span><span>-{formatPrice(orderDetail.discountAmount)}</span></div>}
                        <hr /><div className="d-flex justify-content-between fw-bold fs-5"><span>Toplam</span><span className="text-primary">{formatPrice(orderDetail.grandTotal)}</span></div>
                      </div></div>

                      {/* Customer */}
                      <div className="card mb-3"><div className="card-header bg-transparent"><h6 className="mb-0">Müşteri</h6></div><div className="card-body">
                        <p className="mb-1 fw-medium">{orderDetail.customerName}</p>
                        <p className="mb-1 small text-muted">{orderDetail.customerEmail}</p>
                        {orderDetail.customerPhone && <p className="mb-0 small text-muted">{orderDetail.customerPhone}</p>}
                      </div></div>

                      {/* Address */}
                      <div className="card mb-3"><div className="card-header bg-transparent"><h6 className="mb-0">Teslimat Adresi</h6></div><div className="card-body small">
                        {orderDetail.shippingAddress && (<>
                          <p className="mb-1 fw-medium">{orderDetail.shippingAddress.firstName} {orderDetail.shippingAddress.lastName}</p>
                          <p className="mb-1">{orderDetail.shippingAddress.addressLine}</p>
                          <p className="mb-0">{orderDetail.shippingAddress.district} / {orderDetail.shippingAddress.city}</p>
                        </>)}
                      </div></div>

                      {/* Payment & Cargo */}
                      <div className="card mb-3"><div className="card-header bg-transparent"><h6 className="mb-0"><i className="fas fa-credit-card me-2 text-success" />Ödeme & Kargo</h6></div><div className="card-body small">
                        <div className="d-flex justify-content-between mb-2"><span className="text-muted">Ödeme Yöntemi</span><span className="fw-medium">{paymentLabel(orderDetail.paymentMethod)}</span></div>
                        <div className="d-flex justify-content-between mb-2"><span className="text-muted">Kargo Firması</span><span>{orderDetail.cargoCompany || '—'}</span></div>
                        {orderDetail.cargoTrackingNo ? (
                          <div className="d-flex justify-content-between align-items-center">
                            <span className="text-muted">Takip No</span>
                            <span className="d-flex align-items-center gap-1">
                              <code>{orderDetail.cargoTrackingNo}</code>
                              <button className="btn btn-sm btn-link p-0" title="Kopyala" onClick={() => navigator.clipboard.writeText(orderDetail.cargoTrackingNo)}>
                                <i className="fas fa-copy text-muted" />
                              </button>
                            </span>
                          </div>
                        ) : (
                          <div className="d-flex justify-content-between"><span className="text-muted">Takip No</span><span className="text-muted">—</span></div>
                        )}
                      </div></div>

                      {/* Invoice / Fatura */}
                      <div className="card"><div className="card-header bg-transparent"><h6 className="mb-0"><i className="fas fa-file-invoice me-2 text-info" />Fatura Bilgileri</h6></div><div className="card-body small">
                        {orderDetail.billingAddress ? (<>
                          <p className="mb-1 fw-medium">{orderDetail.billingAddress.firstName} {orderDetail.billingAddress.lastName}</p>
                          {orderDetail.billingAddress.companyName && <p className="mb-1">{orderDetail.billingAddress.companyName}</p>}
                          {orderDetail.billingAddress.taxOffice && <p className="mb-1 text-muted">Vergi Dairesi: {orderDetail.billingAddress.taxOffice}</p>}
                          {orderDetail.billingAddress.taxNumber && <p className="mb-1 text-muted">Vergi No: {orderDetail.billingAddress.taxNumber}</p>}
                          <p className="mb-1">{orderDetail.billingAddress.addressLine}</p>
                          <p className="mb-0">{orderDetail.billingAddress.district} / {orderDetail.billingAddress.city}</p>
                        </>) : (
                          <p className="text-muted mb-0">Teslimat adresi ile aynı</p>
                        )}
                        <hr />
                        <button className="btn btn-sm btn-outline-info w-100" onClick={() => window.print()} title="Fatura/irsaliye yazdır">
                          <i className="fas fa-print me-1" />Yazdır
                        </button>
                      </div></div>
                    </div>
                  </div>
                ) : null}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Status Modal */}
      {showStatusModal && (
        <div className="modal show d-block" style={{background:'rgba(0,0,0,0.5)',zIndex:4000}}>
          <div className="modal-dialog"><div className="modal-content">
            <div className="modal-header"><h5 className="modal-title">Sipariş Durumunu Güncelle</h5><button className="btn-close" onClick={() => setShowStatusModal(false)} /></div>
            <div className="modal-body">
              {orderDetail && (
                <div className="alert alert-light small mb-3">
                  <strong>Mevcut:</strong> <StatusBadge status={orderDetail.status} />
                  {orderDetail.paymentMethod && (orderDetail.paymentMethod === 'DOOR_CASH' || orderDetail.paymentMethod === 'DOOR_CARD') && (
                    <span className="badge bg-warning text-dark ms-2"><i className="fas fa-door-open me-1" />Kapıda Ödeme</span>
                  )}
                </div>
              )}
              <div className="mb-3"><label className="form-label fw-semibold">Yeni Durum</label>
                {allowedTransitions.length === 0 ? (
                  <div className="alert alert-info small">Bu sipariş için yapılabilecek durum geçişi bulunmuyor.</div>
                ) : (
                  <div className="d-flex flex-column gap-2">
                    {allowedTransitions.map(t => {
                      const cfg = STATUS_CONFIG[t.status] || { color: 'secondary', icon: 'fas fa-circle' };
                      return (
                        <div key={t.status} className={`border rounded p-3 d-flex align-items-center gap-2 ${newStatus === t.status ? 'border-primary bg-primary bg-opacity-10' : 'border-light'}`}
                          style={{cursor:'pointer'}} onClick={() => setNewStatus(t.status)}>
                          <input type="radio" className="form-check-input m-0" checked={newStatus === t.status} readOnly />
                          <i className={`${cfg.icon} text-${cfg.color}`} />
                          <span className="fw-medium">{t.label}</span>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
              <div className="mb-3"><label className="form-label">Not (isteğe bağlı)</label><textarea className="form-control" rows={2} value={statusNote} onChange={e => setStatusNote(e.target.value)} placeholder="Durum değişikliği sebebi..." /></div>
            </div>
            <div className="modal-footer"><button className="btn btn-secondary" onClick={() => setShowStatusModal(false)}>İptal</button><button className="btn btn-primary" onClick={updateStatus} disabled={allowedTransitions.length === 0}>Güncelle</button></div>
          </div></div>
        </div>
      )}

      {/* Cargo Modal */}
      {showCargoModal && (
        <div className="modal show d-block" style={{background:'rgba(0,0,0,0.5)',zIndex:4000}}>
          <div className="modal-dialog"><div className="modal-content">
            <div className="modal-header"><h5 className="modal-title">Kargo Bilgisi</h5><button className="btn-close" onClick={() => setShowCargoModal(false)} /></div>
            <div className="modal-body">
              <div className="mb-3"><label className="form-label">Kargo Firması</label>
                <select className="form-select" value={cargoCompany} onChange={e => setCargoCompany(e.target.value)}>
                  <option value="">Seçiniz</option><option value="YURTICI">Yurtiçi Kargo</option><option value="ARAS">Aras Kargo</option><option value="MNG">MNG Kargo</option><option value="PTT">PTT Kargo</option><option value="UPS">UPS</option>
                </select>
              </div>
              <div className="mb-3"><label className="form-label">Takip Numarası</label><input className="form-control" value={cargoTrackingNo} onChange={e => setCargoTrackingNo(e.target.value)} placeholder="Kargo takip no" /></div>
            </div>
            <div className="modal-footer"><button className="btn btn-secondary" onClick={() => setShowCargoModal(false)}>İptal</button><button className="btn btn-primary" onClick={updateCargo}>Kaydet</button></div>
          </div></div>
        </div>
      )}
    </div>
  );
}
