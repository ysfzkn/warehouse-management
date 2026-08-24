import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import axios from 'axios';
import PaginationControls from '../components/PaginationControls';
import useSecurityCodePrompt from '../components/useSecurityCodePrompt';
import { useAdminToast } from '../components/AdminToast';
import { promptDialog } from '../utils/confirmDialog';
import ManualOrderModal from '../components/ManualOrderModal';

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
  CREDIT_CARD: 'Kredi Kartı',
  VIRTUAL_POS: 'Sanal POS',
  BANK_TRANSFER: 'Havale / EFT',
  DOOR_CASH: 'Kapıda Nakit',
  DOOR_CARD: 'Kapıda Kart',
  IYZICO: 'iyzico',
};
const CHANNEL_LABELS = {
  ONLINE: 'E-Ticaret',
  PHONE: 'Telefon',
  WHATSAPP: 'WhatsApp',
  IN_STORE: 'Mağaza',
  INSTAGRAM: 'Instagram',
  MARKETPLACE: 'Pazaryeri',
  OTHER: 'Diğer',
};
const CHANGED_BY_LABELS = { system: 'Sistem', SYSTEM: 'Sistem', ADMIN: 'Yönetici', PAYMENT: 'Ödeme Sistemi' };

const paymentLabel = (m) => PAYMENT_LABELS[m] || m || '—';
const changedByLabel = (s) => CHANGED_BY_LABELS[s] || s || '';

const formatPrice = (p) =>
  p != null ? new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(p) : '—';
const formatDate = (d) =>
  d
    ? new Date(d).toLocaleDateString('tr-TR', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '—';

export default function AdminOrders() {
  const [orders, setOrders] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [statusFilter, setStatusFilter] = useState('');
  const [search, setSearch] = useState('');
  const [paymentFilter, setPaymentFilter] = useState('');
  const [cargoFilter, setCargoFilter] = useState('');
  const [channelFilter, setChannelFilter] = useState('');
  const [showManualOrder, setShowManualOrder] = useState(false);
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [showFilters, setShowFilters] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [orderDetail, setOrderDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [showStatusModal, setShowStatusModal] = useState(false);
  const [newStatus, setNewStatus] = useState('');
  const [statusNote, setStatusNote] = useState('');
  const [showCargoModal, setShowCargoModal] = useState(false);
  const [confirmModal, setConfirmModal] = useState(null); // { paidAmount, paidAt, receiptNote }
  const [cargoCompany, setCargoCompany] = useState('');
  const [cargoTrackingNo, setCargoTrackingNo] = useState('');
  const [allowedTransitions, setAllowedTransitions] = useState([]);
  const [showInvoiceModal, setShowInvoiceModal] = useState(false);
  const [invoiceFile, setInvoiceFile] = useState(null);
  const [invoiceNumber, setInvoiceNumber] = useState('');
  const [invoiceUploading, setInvoiceUploading] = useState(false);
  const [paymentPlanModal, setPaymentPlanModal] = useState(null);
  const { askCode, SecurityCodePrompt } = useSecurityCodePrompt();
  const toast = useAdminToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const autoOpenedRef = useRef(false);

  // Deep-link support: ?orderNumber=ORD-XXX prefills the search box
  // (e.g. coming from the stock movements page).
  useEffect(() => {
    const qpOrder = searchParams.get('orderNumber');
    if (qpOrder && qpOrder !== search) {
      setSearch(qpOrder);
      setPage(0);
      autoOpenedRef.current = false;
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  const fetchOrders = useCallback(() => {
    setLoading(true);
    const params = { page, size: 15, sortBy: 'createdAt', sortDir: 'desc' };
    if (statusFilter) params.status = statusFilter;
    if (search) params.search = search;
    if (paymentFilter) params.paymentMethod = paymentFilter;
    if (cargoFilter) params.cargoCompany = cargoFilter;
    if (channelFilter) params.channel = channelFilter;
    if (startDate) params.startDate = startDate;
    if (endDate) params.endDate = endDate;
    axios
      .get('/api/admin/orders', { params })
      .then((r) => {
        setOrders(r.data?.content || []);
        setTotalPages(r.data?.totalPages || 0);
        setTotalElements(r.data?.totalElements || 0);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [page, statusFilter, search, paymentFilter, cargoFilter, channelFilter, startDate, endDate]);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    fetchOrders();
  }, [fetchOrders]);

  // When arriving via ?orderNumber=..., auto-open the single matching order once
  useEffect(() => {
    const qpOrder = searchParams.get('orderNumber');
    if (!qpOrder || autoOpenedRef.current || loading) return;
    const match = orders.find((o) => o.orderNumber === qpOrder);
    if (match) {
      autoOpenedRef.current = true;
      openDetail(match.id);
      // Clean the URL so a refresh doesn't keep re-opening the modal
      const next = new URLSearchParams(searchParams);
      next.delete('orderNumber');
      setSearchParams(next, { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orders, loading]);

  const [detailError, setDetailError] = useState('');
  const openDetail = async (orderId) => {
    setSelectedOrder(orderId);
    setDetailLoading(true);
    setDetailError('');
    try {
      const res = await axios.get(`/api/admin/orders/${orderId}`);
      setOrderDetail(res.data);
    } catch (e) {
      setDetailError(e.response?.data?.message || 'Sipariş detayı yüklenemedi.');
    } finally {
      setDetailLoading(false);
    }
  };
  const closeDetail = () => {
    setSelectedOrder(null);
    setOrderDetail(null);
  };

  const fetchAllowedTransitions = async (orderId) => {
    try {
      const res = await axios.get(`/api/admin/orders/${orderId}/allowed-transitions`);
      setAllowedTransitions(res.data || []);
      if (res.data?.length > 0) setNewStatus(res.data[0].status);
    } catch {
      setAllowedTransitions([]);
    }
  };

  const updateStatus = async () => {
    try {
      await axios.put(`/api/admin/orders/${selectedOrder}/status`, { status: newStatus, note: statusNote });
      setShowStatusModal(false);
      setStatusNote('');
      openDetail(selectedOrder);
      fetchOrders();
      toast.success('Sipariş durumu güncellendi.');
    } catch (e) {
      toast.error(e.response?.data?.message || 'Hata oluştu');
    }
  };

  const updateCargo = async () => {
    try {
      await axios.put(`/api/admin/orders/${selectedOrder}/cargo`, { cargoCompany, cargoTrackingNo });
      setShowCargoModal(false);
      openDetail(selectedOrder);
      fetchOrders();
      toast.success('Kargo bilgisi güncellendi.');
    } catch (e) {
      toast.error(e.response?.data?.message || 'Hata oluştu');
    }
  };

  const confirmPayment = () => {
    // Open the modal — a nice form instead of a prompt
    setConfirmModal({
      paidAmount: orderDetail?.grandTotal?.toString() || '',
      paidAt: new Date().toISOString().slice(0, 16),
      receiptNote: '',
    });
  };

  // Modal submit
  const submitConfirmPayment = async () => {
    if (!confirmModal) return;
    const paidAmount = Number(String(confirmModal.paidAmount).replace(',', '.'));
    if (!isFinite(paidAmount) || paidAmount <= 0) {
      toast.error('Geçerli bir tutar girin.');
      return;
    }
    if (!confirmModal.paidAt) {
      toast.error('Ödeme tarihi zorunlu.');
      return;
    }
    const paidAt = confirmModal.paidAt.length === 16 ? confirmModal.paidAt + ':00' : confirmModal.paidAt;
    // Store the form data, close the modal, then open the security prompt
    // (avoid two overlays stacking — the security prompt is at the same z-index)
    const payload = { paidAmount, paidAt, receiptNote: confirmModal.receiptNote || null };
    const orderId = selectedOrder;
    setConfirmModal(null);
    const code = await askCode({
      description: 'Havale/EFT ödemesini onaylamak için güvenlik şifresini girin.',
    });
    if (!code) return;
    try {
      await axios.put(`/api/admin/orders/${orderId}/confirm-payment`, payload, {
        headers: { 'X-ADMIN-SECURITY-CODE': code },
      });
      openDetail(orderId);
      fetchOrders();
      toast.success('Ödeme onaylandı.');
    } catch (e) {
      toast.error(
        e.response?.status === 403 ? 'Güvenlik şifresi hatalı.' : e.response?.data?.message || 'Hata oluştu'
      );
    }
  };

  const rejectPayment = async () => {
    const reason = await promptDialog({
      title: 'Havale Reddedilsin mi?',
      message: 'Sipariş İPTAL edilecek ve stok serbest bırakılacak.',
      inputLabel: 'Red sebebi (müşteri emailinde görünecek)',
      placeholder: 'Örn: Yatırılan tutar siparişle uyuşmuyor, müşteri vazgeçti, yanlış referans...',
      helpText: 'Sebep girilmeden işlem onaylanamaz. Ctrl+Enter ile hızlı onaylayabilirsiniz.',
      confirmText: 'Reddet ve İptal Et',
      variant: 'danger',
      icon: 'fa-ban',
    });
    if (!reason) return;
    const code = await askCode({ description: 'Havale reddetmek için güvenlik şifresini girin.' });
    if (!code) return;
    try {
      await axios.put(
        `/api/admin/orders/${selectedOrder}/reject-payment`,
        { reason: reason.trim() },
        { headers: { 'X-ADMIN-SECURITY-CODE': code } }
      );
      openDetail(selectedOrder);
      fetchOrders();
      toast.success('Havale reddedildi, sipariş iptal edildi.');
    } catch (e) {
      toast.error(
        e.response?.status === 403 ? 'Güvenlik şifresi hatalı.' : e.response?.data?.message || 'Hata oluştu'
      );
    }
  };

  const StatusBadge = ({ status }) => {
    const cfg = STATUS_CONFIG[status] || { label: status, color: 'secondary', icon: '' };
    return (
      <span className={`badge bg-${cfg.color}`}>
        {cfg.icon && <i className={`${cfg.icon} me-1`} />}
        {cfg.label}
      </span>
    );
  };

  const copyConfirmationLink = async () => {
    try {
      const response = await axios.post(`/api/admin/orders/${orderDetail.id}/customer-confirmation-link`);
      const url = `${window.location.protocol}//${window.location.hostname.replace(/^admin\./, '')}${response.data.path}`;
      await navigator.clipboard.writeText(url);
      toast.success('Müşteri onay bağlantısı kopyalandı.');
      openDetail(orderDetail.id);
    } catch (e) {
      toast.error(e.response?.data?.message || 'Onay bağlantısı oluşturulamadı.');
    }
  };

  const markManualPaymentReceived = async () => {
    try {
      await axios.put(`/api/admin/orders/${orderDetail.id}/payment-received`);
      toast.success('Ödeme alındı olarak kaydedildi.');
      openDetail(orderDetail.id);
      fetchOrders();
    } catch (e) {
      toast.error(e.response?.data?.message || 'Ödeme güncellenemedi.');
    }
  };

  const savePaymentPlan = async () => {
    try {
      await axios.put(`/api/admin/orders/${orderDetail.id}/payment-plan`, paymentPlanModal);
      setPaymentPlanModal(null);
      toast.success('Ödeme planı güncellendi.');
      openDetail(orderDetail.id);
      fetchOrders();
    } catch (e) {
      toast.error(e.response?.data?.message || 'Ödeme planı güncellenemedi.');
    }
  };

  const handleInvoiceUpload = async () => {
    if (!invoiceFile) {
      toast.error('Lütfen bir fatura dosyası seçin.');
      return;
    }
    setInvoiceUploading(true);
    const fd = new FormData();
    fd.append('file', invoiceFile);
    try {
      await axios.post(
        `/api/admin/orders/${orderDetail.id}/invoice?invoiceNumber=${encodeURIComponent(invoiceNumber)}`,
        fd,
        { headers: { 'Content-Type': 'multipart/form-data' } }
      );
      toast.success('Fatura başarıyla yüklendi.');
      setShowInvoiceModal(false);
      axios.get(`/api/admin/orders/${orderDetail.id}`).then((r) => setOrderDetail(r.data));
    } catch (err) {
      toast.error(err.response?.data?.message || 'Fatura yüklenemedi.');
    } finally {
      setInvoiceUploading(false);
    }
  };

  return (
    <div>
      {SecurityCodePrompt}
      {showManualOrder && (
        <ManualOrderModal
          onClose={() => setShowManualOrder(false)}
          onCreated={(data) => {
            setShowManualOrder(false);
            fetchOrders();
            toast.success(`Sipariş oluşturuldu: ${data.orderNumber}`);
          }}
        />
      )}

      {/* Invoice Upload Modal */}
      {showInvoiceModal && (
        <div
          className="modal d-block"
          style={{ background: 'rgba(0,0,0,0.5)', zIndex: 5000 }}
          onClick={() => setShowInvoiceModal(false)}
        >
          <div className="modal-dialog modal-dialog-centered" onClick={(e) => e.stopPropagation()}>
            <div className="modal-content border-0 shadow" style={{ borderRadius: 16 }}>
              <div className="modal-header border-0 pb-0">
                <div>
                  <h5 className="modal-title fw-bold">
                    <i className="fas fa-file-invoice me-2 text-info" />
                    Fatura Yükle
                  </h5>
                  <small className="text-muted">Sipariş #{orderDetail?.orderNumber}</small>
                </div>
                <button className="btn-close" onClick={() => setShowInvoiceModal(false)} />
              </div>
              <div className="modal-body">
                {/* Invoice Number */}
                <div className="mb-3">
                  <label className="form-label small fw-medium">
                    Fatura Numarası <span className="text-muted fw-normal">(opsiyonel)</span>
                  </label>
                  <input
                    className="form-control"
                    value={invoiceNumber}
                    onChange={(e) => setInvoiceNumber(e.target.value)}
                    placeholder="FTR-2026-001234"
                  />
                </div>

                {/* File Selection */}
                <div className="mb-3">
                  <label className="form-label small fw-medium">
                    Fatura Dosyası <span className="text-danger">*</span>
                  </label>
                  <div
                    className={`border rounded-3 p-3 text-center ${invoiceFile ? 'border-success bg-success bg-opacity-10' : 'border-dashed'}`}
                    style={{ cursor: 'pointer', borderStyle: invoiceFile ? 'solid' : 'dashed' }}
                    onClick={() => document.getElementById('invoice-file-input').click()}
                    onDragOver={(e) => e.preventDefault()}
                    onDrop={(e) => {
                      e.preventDefault();
                      const f = e.dataTransfer.files?.[0];
                      if (f) setInvoiceFile(f);
                    }}
                  >
                    <input
                      type="file"
                      id="invoice-file-input"
                      className="d-none"
                      accept=".pdf,.png,.jpg,.jpeg"
                      onChange={(e) => {
                        if (e.target.files?.[0]) setInvoiceFile(e.target.files[0]);
                      }}
                    />
                    {invoiceFile ? (
                      <div>
                        <i className="fas fa-file-check text-success mb-2 d-block" style={{ fontSize: 28 }} />
                        <div className="fw-medium small">{invoiceFile.name}</div>
                        <div className="text-muted" style={{ fontSize: 11 }}>
                          {(invoiceFile.size / 1024).toFixed(0)} KB
                        </div>
                        <button
                          className="btn btn-sm btn-link text-danger mt-1 p-0"
                          onClick={(e) => {
                            e.stopPropagation();
                            setInvoiceFile(null);
                          }}
                        >
                          Dosyayı Kaldır
                        </button>
                      </div>
                    ) : (
                      <div>
                        <i
                          className="fas fa-cloud-upload-alt text-muted mb-2 d-block"
                          style={{ fontSize: 28 }}
                        />
                        <div className="small text-muted">
                          Dosya sürükleyin veya <span className="text-primary fw-medium">seçin</span>
                        </div>
                        <div className="text-muted mt-1" style={{ fontSize: 11 }}>
                          PDF, PNG, JPG — Maks. 10MB
                        </div>
                      </div>
                    )}
                  </div>
                </div>

                {/* Show existing invoice if present */}
                {orderDetail?.invoiceUrl && (
                  <div className="alert alert-light small py-2 mb-0">
                    <i className="fas fa-info-circle me-1 text-info" />
                    Mevcut fatura değiştirilecektir.
                    {orderDetail.invoiceNumber && <span> (#{orderDetail.invoiceNumber})</span>}
                  </div>
                )}
              </div>
              <div className="modal-footer border-0 pt-0">
                <button className="btn btn-outline-secondary" onClick={() => setShowInvoiceModal(false)}>
                  İptal
                </button>
                <button
                  className="btn btn-primary"
                  onClick={handleInvoiceUpload}
                  disabled={!invoiceFile || invoiceUploading}
                >
                  {invoiceUploading ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-2" />
                      Yükleniyor...
                    </>
                  ) : (
                    <>
                      <i className="fas fa-upload me-2" />
                      Yükle
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="mb-1">Siparişler</h2>
          <small className="text-muted">{totalElements} sipariş</small>
        </div>
        <div className="d-flex gap-2">
          <button className="btn btn-primary btn-sm" onClick={() => setShowManualOrder(true)}>
            <i className="fas fa-plus me-1" /> Sipariş Oluştur
          </button>
          <button
            className={`btn btn-sm ${showFilters ? 'btn-primary' : 'btn-outline-primary'}`}
            onClick={() => setShowFilters(!showFilters)}
          >
            <i className="fas fa-filter me-1" />
            Filtreler{' '}
            {(statusFilter || paymentFilter || cargoFilter || startDate || endDate) && (
              <span className="badge bg-white text-primary ms-1">
                {[statusFilter, paymentFilter, cargoFilter, startDate, endDate].filter(Boolean).length}
              </span>
            )}
          </button>
          <button
            className="btn btn-outline-success btn-sm"
            disabled={exporting}
            onClick={async () => {
              setExporting(true);
              try {
                const params = {};
                if (statusFilter) params.status = statusFilter;
                if (startDate) params.startDate = startDate;
                if (endDate) params.endDate = endDate;
                const res = await axios.get('/api/admin/orders/export', { params, responseType: 'blob' });
                const url = window.URL.createObjectURL(res.data);
                const a = document.createElement('a');
                a.href = url;
                a.download = `siparisler-${new Date().toISOString().split('T')[0]}.xlsx`;
                a.click();
                window.URL.revokeObjectURL(url);
                toast.success('Excel dosyası indirildi.');
              } catch {
                toast.error('Excel indirilemedi.');
              } finally {
                setExporting(false);
              }
            }}
          >
            {exporting ? (
              <span className="spinner-border spinner-border-sm me-1" />
            ) : (
              <i className="fas fa-file-excel me-1" />
            )}
            Excel İndir
          </button>
        </div>
      </div>

      {/* Search bar */}
      <div className="mb-3">
        <div className="input-group">
          <span className="input-group-text bg-white">
            <i className="fas fa-search text-muted" />
          </span>
          <input
            className="form-control border-start-0"
            placeholder="Sipariş no, müşteri adı, e-posta, telefon veya kargo takip no ara..."
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
          {search && (
            <button
              className="btn btn-outline-secondary"
              onClick={() => {
                setSearch('');
                setPage(0);
              }}
            >
              <i className="fas fa-times" />
            </button>
          )}
        </div>
      </div>

      {/* Filters */}
      <div className="card border-0 shadow-sm mb-4">
        <div className="card-body py-3">
          {/* Status chips */}
          <div className="d-flex gap-2 flex-wrap align-items-center">
            <button
              className={`btn btn-sm ${!statusFilter ? 'btn-primary' : 'btn-outline-secondary'}`}
              onClick={() => {
                setStatusFilter('');
                setPage(0);
              }}
            >
              Tümü
            </button>
            {Object.entries(STATUS_CONFIG).map(([key, cfg]) => (
              <button
                key={key}
                className={`btn btn-sm ${statusFilter === key ? `btn-${cfg.color}` : 'btn-outline-secondary'}`}
                onClick={() => {
                  setStatusFilter(statusFilter === key ? '' : key);
                  setPage(0);
                }}
              >
                {cfg.icon && <i className={`${cfg.icon} me-1`} style={{ fontSize: 10 }} />}
                {cfg.label}
              </button>
            ))}
          </div>

          {/* Extended filters */}
          {showFilters && (
            <div className="row g-2 mt-3 pt-3 border-top">
              <div className="col-md-3">
                <label className="form-label small fw-medium mb-1">Ödeme Yöntemi</label>
                <select
                  className="form-select form-select-sm"
                  value={paymentFilter}
                  onChange={(e) => {
                    setPaymentFilter(e.target.value);
                    setPage(0);
                  }}
                >
                  <option value="">Tümü</option>
                  <option value="CREDIT_CARD">Kredi Kartı</option>
                  <option value="BANK_TRANSFER">Havale/EFT</option>
                  <option value="DOOR_CASH">Kapıda Nakit</option>
                  <option value="DOOR_CARD">Kapıda Kart</option>
                </select>
              </div>
              <div className="col-md-3">
                <label className="form-label small fw-medium mb-1">Sipariş Kanalı</label>
                <select
                  className="form-select form-select-sm"
                  value={channelFilter}
                  onChange={(e) => {
                    setChannelFilter(e.target.value);
                    setPage(0);
                  }}
                >
                  <option value="">Tümü</option>
                  {Object.entries(CHANNEL_LABELS).map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
              </div>
              <div className="col-md-3">
                <label className="form-label small fw-medium mb-1">Kargo Firması</label>
                <select
                  className="form-select form-select-sm"
                  value={cargoFilter}
                  onChange={(e) => {
                    setCargoFilter(e.target.value);
                    setPage(0);
                  }}
                >
                  <option value="">Tümü</option>
                  <option value="YURTICI">Yurtiçi Kargo</option>
                  <option value="ARAS">Aras Kargo</option>
                  <option value="MNG">MNG Kargo</option>
                  <option value="PTT">PTT Kargo</option>
                  <option value="UPS">UPS</option>
                </select>
              </div>
              <div className="col-md-3">
                <label className="form-label small fw-medium mb-1">Başlangıç</label>
                <input
                  type="date"
                  className="form-control form-control-sm"
                  value={startDate}
                  onChange={(e) => {
                    setStartDate(e.target.value);
                    setPage(0);
                  }}
                />
              </div>
              <div className="col-md-3">
                <label className="form-label small fw-medium mb-1">Bitiş</label>
                <input
                  type="date"
                  className="form-control form-control-sm"
                  value={endDate}
                  onChange={(e) => {
                    setEndDate(e.target.value);
                    setPage(0);
                  }}
                />
              </div>
              {(paymentFilter || cargoFilter || startDate || endDate) && (
                <div className="col-12">
                  <button
                    className="btn btn-sm btn-outline-danger"
                    onClick={() => {
                      setPaymentFilter('');
                      setCargoFilter('');
                      setStartDate('');
                      setEndDate('');
                      setPage(0);
                    }}
                  >
                    <i className="fas fa-times me-1" />
                    Filtreleri Temizle
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Table */}
      <div className="card border-0 shadow-sm">
        <div className="table-responsive">
          <table className="table table-hover align-middle mb-0">
            <thead className="table-light">
              <tr>
                <th>Sipariş No</th>
                <th>Müşteri</th>
                <th>Durum</th>
                <th>Ödeme</th>
                <th className="text-end">Tutar</th>
                <th>Kargo</th>
                <th>Tarih</th>
                <th style={{ width: 80 }}></th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={8} className="text-center py-5">
                    <div className="spinner-border spinner-border-sm" />
                  </td>
                </tr>
              ) : orders.length === 0 ? (
                <tr>
                  <td colSpan={8} className="text-center py-5 text-muted">
                    Sipariş bulunamadı
                  </td>
                </tr>
              ) : (
                orders.map((o) => (
                  <tr key={o.id} style={{ cursor: 'pointer' }} onClick={() => openDetail(o.id)}>
                    <td>
                      <strong className="text-primary">{o.orderNumber}</strong>
                      <div>
                        <span
                          className={`badge mt-1 ${o.orderChannel === 'WHATSAPP' ? 'bg-success' : o.orderChannel === 'PHONE' ? 'bg-info text-dark' : 'bg-secondary'}`}
                        >
                          {CHANNEL_LABELS[o.orderChannel] || o.orderChannel || 'E-Ticaret'}
                        </span>
                        {o.channelReference && (
                          <small className="text-muted ms-1">{o.channelReference}</small>
                        )}
                      </div>
                    </td>
                    <td>
                      <div>{o.customerName}</div>
                      <small className="text-muted">{o.customerEmail}</small>
                    </td>
                    <td>
                      <StatusBadge status={o.status} />
                    </td>
                    <td>
                      <small className="text-muted">{paymentLabel(o.paymentMethod)}</small>
                      {o.paymentDueAt && (
                        <div
                          className={`small ${new Date(o.paymentDueAt) < new Date() && o.status === 'PENDING_PAYMENT' ? 'text-danger fw-bold' : 'text-muted'}`}
                        >
                          Vade: {formatDate(o.paymentDueAt)}
                        </div>
                      )}
                    </td>
                    <td className="text-end fw-bold">{formatPrice(o.grandTotal)}</td>
                    <td>
                      {o.cargoTrackingNo ? (
                        <small className="text-success">
                          <i className="fas fa-truck me-1" />
                          {o.cargoTrackingNo}
                        </small>
                      ) : (
                        <small className="text-muted">—</small>
                      )}
                    </td>
                    <td>
                      <small className="text-muted">{formatDate(o.createdAt)}</small>
                    </td>
                    <td>
                      <button
                        className="btn btn-sm btn-outline-primary"
                        onClick={(e) => {
                          e.stopPropagation();
                          openDetail(o.id);
                        }}
                      >
                        Detay
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
      {totalPages > 1 && (
        <div className="mt-3">
          <PaginationControls page={page} totalPages={totalPages} onPageChange={setPage} />
        </div>
      )}

      {/* Detail Modal */}
      {selectedOrder && (
        <div
          className="modal show d-block"
          style={{ background: 'rgba(0,0,0,0.5)', zIndex: 3000 }}
          onClick={closeDetail}
        >
          <div className="modal-dialog modal-xl modal-dialog-scrollable" onClick={(e) => e.stopPropagation()}>
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  Sipariş Detay{' '}
                  {orderDetail && <span className="text-primary">#{orderDetail.orderNumber}</span>}
                </h5>
                <button className="btn-close" onClick={closeDetail} />
              </div>
              <div className="modal-body">
                {detailLoading ? (
                  <div className="text-center py-5">
                    <div className="spinner-border" />
                  </div>
                ) : detailError ? (
                  <div className="alert alert-danger">{detailError}</div>
                ) : orderDetail ? (
                  <div className="row g-4">
                    <div className="col-lg-8">
                      {/* Actions */}
                      <div className="card mb-3">
                        <div className="card-body d-flex justify-content-between align-items-center flex-wrap gap-2">
                          <div>
                            <StatusBadge status={orderDetail.status} />
                            <span className="ms-2 text-muted small">{formatDate(orderDetail.createdAt)}</span>
                          </div>
                          <div className="d-flex gap-2">
                            <button
                              className="btn btn-sm btn-outline-primary"
                              onClick={() => {
                                fetchAllowedTransitions(orderDetail.id);
                                setShowStatusModal(true);
                              }}
                            >
                              <i className="fas fa-edit me-1" />
                              Durum Güncelle
                            </button>
                            <button
                              className="btn btn-sm btn-outline-secondary"
                              onClick={() => {
                                setCargoCompany(orderDetail.cargoCompany || '');
                                setCargoTrackingNo(orderDetail.cargoTrackingNo || '');
                                setShowCargoModal(true);
                              }}
                            >
                              <i className="fas fa-truck me-1" />
                              Kargo
                            </button>
                            {orderDetail.status === 'PENDING_PAYMENT' &&
                              orderDetail.paymentMethod === 'BANK_TRANSFER' && (
                                <>
                                  <button className="btn btn-sm btn-success" onClick={confirmPayment}>
                                    <i className="fas fa-check me-1" />
                                    Havale Onayla
                                  </button>
                                  <button className="btn btn-sm btn-outline-danger" onClick={rejectPayment}>
                                    <i className="fas fa-times me-1" />
                                    Havale Reddet
                                  </button>
                                </>
                              )}
                          </div>
                        </div>
                      </div>

                      {/* ══ Bank Transfer Matching Info — admin searches for this code in the statement ══ */}
                      {orderDetail.paymentMethod === 'BANK_TRANSFER' && orderDetail.bankTransferReference && (
                        <div className="card mb-3 border-warning">
                          <div className="card-header bg-warning bg-opacity-10 d-flex align-items-center gap-2">
                            <i className="fas fa-search-dollar text-warning" />
                            <h6 className="mb-0 fw-bold">Havale Eşleştirme</h6>
                            {orderDetail.bankTransferStatus && (
                              <span
                                className={`badge ms-auto ${
                                  orderDetail.bankTransferStatus === 'INITIATED'
                                    ? 'bg-warning text-dark'
                                    : orderDetail.bankTransferStatus === 'SUCCESS'
                                      ? 'bg-success'
                                      : orderDetail.bankTransferStatus === 'TIMEOUT'
                                        ? 'bg-secondary'
                                        : 'bg-danger'
                                }`}
                              >
                                {orderDetail.bankTransferStatus === 'INITIATED'
                                  ? 'Ödeme Bekliyor'
                                  : orderDetail.bankTransferStatus === 'SUCCESS'
                                    ? 'Onaylandı'
                                    : orderDetail.bankTransferStatus === 'TIMEOUT'
                                      ? 'Süresi Doldu'
                                      : 'İptal/Hata'}
                              </span>
                            )}
                          </div>
                          <div className="card-body">
                            <div className="row g-3">
                              <div className="col-md-7">
                                <label className="form-label small text-muted mb-1">
                                  Referans Kodu (banka açıklamasında bunu arayın)
                                </label>
                                <div className="d-flex align-items-center gap-2">
                                  <code className="fs-5 fw-bold text-dark px-3 py-2 bg-light rounded font-monospace flex-grow-1">
                                    {orderDetail.bankTransferReference}
                                  </code>
                                  <button
                                    className="btn btn-outline-secondary"
                                    onClick={() => {
                                      navigator.clipboard.writeText(orderDetail.bankTransferReference);
                                      toast.success('Referans kopyalandı');
                                    }}
                                    title="Kopyala"
                                  >
                                    <i className="fas fa-copy" />
                                  </button>
                                </div>
                              </div>
                              <div className="col-md-5">
                                <label className="form-label small text-muted mb-1">Beklenen Tutar</label>
                                <div className="fs-5 fw-bold text-primary">
                                  ₺
                                  {orderDetail.grandTotal?.toLocaleString('tr-TR', {
                                    minimumFractionDigits: 2,
                                  })}
                                </div>
                                <small className="text-muted">
                                  Müşteri Adı: <strong>{orderDetail.customerName}</strong>
                                </small>
                              </div>
                              {orderDetail.bankTransferDeadline && (
                                <div className="col-12">
                                  <div className="small text-muted">
                                    <i className="fas fa-clock me-1" />
                                    Son Ödeme:{' '}
                                    <strong
                                      className={
                                        new Date(orderDetail.bankTransferDeadline) < new Date()
                                          ? 'text-danger'
                                          : 'text-warning'
                                      }
                                    >
                                      {formatDate(orderDetail.bankTransferDeadline)}
                                    </strong>
                                    {new Date(orderDetail.bankTransferDeadline) < new Date() && (
                                      <span className="badge bg-danger ms-2">Süre Doldu</span>
                                    )}
                                  </div>
                                </div>
                              )}
                            </div>
                            <hr className="my-3" />
                            <div className="alert alert-info mb-0 small">
                              <strong>📋 Onay Adımları:</strong>
                              <ol className="mb-0 mt-1 ps-3">
                                <li>
                                  Banka ekstrende <code>{orderDetail.bankTransferReference}</code> yazan kaydı
                                  bulun
                                </li>
                                <li>
                                  Yatırılan tutarın{' '}
                                  <strong>
                                    ₺
                                    {orderDetail.grandTotal?.toLocaleString('tr-TR', {
                                      minimumFractionDigits: 2,
                                    })}
                                  </strong>{' '}
                                  ile eşleştiğini doğrulayın
                                </li>
                                <li>
                                  Gönderen kişi <strong>{orderDetail.customerName}</strong> mı? (farklıysa not
                                  düşün)
                                </li>
                                <li>"Havale Onayla" → ekstredeki tutar/tarih girilir, doğrulanır</li>
                              </ol>
                            </div>
                          </div>
                        </div>
                      )}

                      {/* Items */}
                      <div className="card mb-3">
                        <div className="card-header bg-transparent">
                          <h6 className="mb-0">
                            <i className="fas fa-box me-2 text-primary" />
                            Sipariş Kalemleri ({orderDetail.items?.length || 0})
                          </h6>
                        </div>
                        <div className="card-body p-0">
                          <div className="list-group list-group-flush">
                            {orderDetail.items?.map((item) => (
                              <div
                                key={item.id}
                                className="list-group-item d-flex align-items-center gap-3 py-3"
                              >
                                {/* Product image */}
                                <div
                                  className="flex-shrink-0"
                                  style={{
                                    width: 56,
                                    height: 56,
                                    borderRadius: 10,
                                    background: '#f8fafc',
                                    border: '1px solid #e2e8f0',
                                    overflow: 'hidden',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                  }}
                                >
                                  {item.imageUrl ? (
                                    <img
                                      src={item.imageUrl}
                                      alt=""
                                      style={{ width: '100%', height: '100%', objectFit: 'contain' }}
                                      onError={(e) => {
                                        e.target.style.display = 'none';
                                        e.target.parentElement.innerHTML =
                                          '<i class="fas fa-box text-muted"></i>';
                                      }}
                                    />
                                  ) : (
                                    <i className="fas fa-box text-muted" />
                                  )}
                                </div>
                                {/* Product info */}
                                <div className="flex-grow-1 min-w-0">
                                  <div className="fw-semibold small">{item.productName}</div>
                                  <div className="d-flex gap-2 mt-1 flex-wrap align-items-center">
                                    {item.productSku && (
                                      <span
                                        className="badge bg-light text-dark border"
                                        style={{ fontSize: 10 }}
                                      >
                                        SKU: {item.productSku}
                                      </span>
                                    )}
                                    {item.warehouseName && (
                                      <span
                                        className="badge bg-info bg-opacity-10 text-info border"
                                        style={{ fontSize: 10 }}
                                      >
                                        <i className="fas fa-warehouse me-1" />
                                        {item.warehouseName}
                                      </span>
                                    )}
                                  </div>
                                  <div className="d-flex gap-2 mt-1">
                                    {item.productId && (
                                      <button
                                        className="btn btn-link btn-sm p-0 text-primary"
                                        style={{ fontSize: 10 }}
                                        onClick={(e) => {
                                          e.stopPropagation();
                                          window.open(`/products?highlight=${item.productId}`, '_self');
                                        }}
                                      >
                                        Ürüne Git{' '}
                                        <i
                                          className="fas fa-external-link-alt ms-1"
                                          style={{ fontSize: 8 }}
                                        />
                                      </button>
                                    )}
                                    {item.stockId && (
                                      <button
                                        className="btn btn-link btn-sm p-0 text-info"
                                        style={{ fontSize: 10 }}
                                        onClick={(e) => {
                                          e.stopPropagation();
                                          window.open(`/stock?highlight=${item.stockId}`, '_self');
                                        }}
                                      >
                                        Stok Yönetimi{' '}
                                        <i
                                          className="fas fa-external-link-alt ms-1"
                                          style={{ fontSize: 8 }}
                                        />
                                      </button>
                                    )}
                                  </div>
                                </div>
                                {/* Quantity */}
                                <div className="text-center flex-shrink-0" style={{ minWidth: 50 }}>
                                  <div className="small text-muted">Adet</div>
                                  <div className="fw-bold">{item.quantity}</div>
                                </div>
                                {/* Unit price */}
                                <div className="text-end flex-shrink-0" style={{ minWidth: 90 }}>
                                  <div className="small text-muted">Birim</div>
                                  <div className="small">{formatPrice(item.unitPrice)}</div>
                                </div>
                                {/* Total */}
                                <div className="text-end flex-shrink-0" style={{ minWidth: 90 }}>
                                  <div className="small text-muted">Toplam</div>
                                  <div className="fw-bold text-primary">{formatPrice(item.lineTotal)}</div>
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      </div>

                      {/* Timeline */}
                      {orderDetail.statusHistory?.length > 0 && (
                        <div className="card">
                          <div className="card-header bg-transparent">
                            <h6 className="mb-0">
                              <i className="fas fa-history me-2 text-primary" />
                              Sipariş Zaman Çizelgesi
                            </h6>
                          </div>
                          <div className="card-body">
                            <div className="position-relative" style={{ paddingLeft: 24 }}>
                              <div
                                className="position-absolute"
                                style={{ left: 8, top: 0, bottom: 0, width: 2, background: '#dee2e6' }}
                              />
                              {orderDetail.statusHistory.map((h, i) => {
                                const cfg = STATUS_CONFIG[h.newStatus] || {
                                  color: 'secondary',
                                  icon: 'fas fa-circle',
                                };
                                return (
                                  <div key={i} className="d-flex mb-3 position-relative">
                                    <div
                                      className={`position-absolute bg-${cfg.color} rounded-circle d-flex align-items-center justify-content-center`}
                                      style={{ left: -24, top: 2, width: 18, height: 18, zIndex: 1 }}
                                    >
                                      <i className={`${cfg.icon} text-white`} style={{ fontSize: 8 }} />
                                    </div>
                                    <div className="flex-grow-1 ms-2">
                                      <div className="d-flex justify-content-between align-items-start">
                                        <div>
                                          <StatusBadge status={h.newStatus} />
                                          {h.oldStatus && (
                                            <small className="text-muted ms-2">
                                              <i className="fas fa-arrow-left me-1" />
                                              {(STATUS_CONFIG[h.oldStatus] || {}).label || h.oldStatus}
                                            </small>
                                          )}
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
                      <div className="card mb-3">
                        <div className="card-header bg-transparent">
                          <h6 className="mb-0">Fiyat Özeti</h6>
                        </div>
                        <div className="card-body">
                          <div className="d-flex justify-content-between mb-2">
                            <span className="text-muted">Ara Toplam</span>
                            <span>{formatPrice(orderDetail.subtotal)}</span>
                          </div>
                          <div className="d-flex justify-content-between mb-2">
                            <span className="text-muted">Kargo</span>
                            <span>{formatPrice(orderDetail.shippingCost)}</span>
                          </div>
                          {orderDetail.discountAmount > 0 && (
                            <div className="d-flex justify-content-between mb-2 text-success">
                              <span>İndirim</span>
                              <span>-{formatPrice(orderDetail.discountAmount)}</span>
                            </div>
                          )}
                          <hr />
                          <div className="d-flex justify-content-between fw-bold fs-5">
                            <span>Toplam</span>
                            <span className="text-primary">{formatPrice(orderDetail.grandTotal)}</span>
                          </div>
                        </div>
                      </div>

                      {/* Customer */}
                      <div className="card mb-3">
                        <div className="card-header bg-transparent">
                          <h6 className="mb-0">Müşteri</h6>
                        </div>
                        <div className="card-body">
                          <p className="mb-1 fw-medium">{orderDetail.customerName}</p>
                          <p className="mb-1 small text-muted">{orderDetail.customerEmail}</p>
                          {orderDetail.customerPhone && (
                            <p className="mb-0 small text-muted">{orderDetail.customerPhone}</p>
                          )}
                        </div>
                      </div>

                      {/* Address */}
                      <div className="card mb-3">
                        <div className="card-header bg-transparent">
                          <h6 className="mb-0">Teslimat Adresi</h6>
                        </div>
                        <div className="card-body small">
                          {orderDetail.shippingAddress && (
                            <>
                              <p className="mb-1 fw-medium">
                                {orderDetail.shippingAddress.firstName} {orderDetail.shippingAddress.lastName}
                              </p>
                              <p className="mb-1">{orderDetail.shippingAddress.addressLine}</p>
                              <p className="mb-0">
                                {orderDetail.shippingAddress.district} / {orderDetail.shippingAddress.city}
                              </p>
                            </>
                          )}
                        </div>
                      </div>

                      {/* Payment & Cargo */}
                      <div className="card mb-3">
                        <div className="card-header bg-transparent">
                          <h6 className="mb-0">
                            <i className="fas fa-credit-card me-2 text-success" />
                            Ödeme & Kargo
                          </h6>
                        </div>
                        <div className="card-body small">
                          <div className="d-flex justify-content-between mb-2">
                            <span className="text-muted">Ödeme Yöntemi</span>
                            <span className="fw-medium">{paymentLabel(orderDetail.paymentMethod)}</span>
                          </div>
                          {orderDetail.orderChannel && (
                            <>
                              <div className="d-flex justify-content-between mb-2">
                                <span className="text-muted">Sipariş Kanalı</span>
                                <span className="fw-medium">
                                  {CHANNEL_LABELS[orderDetail.orderChannel] || orderDetail.orderChannel}
                                </span>
                              </div>
                              {orderDetail.channelReference && (
                                <div className="d-flex justify-content-between mb-2">
                                  <span className="text-muted">Kanal Referansı</span>
                                  <code>{orderDetail.channelReference}</code>
                                </div>
                              )}
                              <div className="d-flex justify-content-between mb-2">
                                <span className="text-muted">Müşteri Onayı</span>
                                <span
                                  className={`badge ${orderDetail.customerConfirmedAt ? 'bg-success' : 'bg-warning text-dark'}`}
                                >
                                  {orderDetail.customerConfirmedAt
                                    ? `Onaylandı · ${formatDate(orderDetail.customerConfirmedAt)}`
                                    : 'Bekleniyor'}
                                </span>
                              </div>
                              {orderDetail.customerConfirmedAt && orderDetail.legalConsentSnapshot && (
                                <div className="alert alert-success py-2 px-2 small">
                                  <i className="fas fa-shield-alt me-1" />
                                  Hukuki metin içerikleri ve SHA-256 sürüm kanıtları siparişte saklandı.
                                </div>
                              )}
                              {orderDetail.paymentDueAt && (
                                <div className="d-flex justify-content-between mb-2">
                                  <span className="text-muted">Ödeme Vadesi</span>
                                  <span>{formatDate(orderDetail.paymentDueAt)}</span>
                                </div>
                              )}
                              {orderDetail.paymentReminderAt && (
                                <div className="d-flex justify-content-between mb-2">
                                  <span className="text-muted">Hatırlatma</span>
                                  <span>{formatDate(orderDetail.paymentReminderAt)}</span>
                                </div>
                              )}
                              {orderDetail.orderChannel !== 'ONLINE' && (
                                <div className="d-grid gap-2 mt-3">
                                  {!orderDetail.customerConfirmedAt && (
                                    <button
                                      className="btn btn-outline-primary btn-sm"
                                      onClick={copyConfirmationLink}
                                    >
                                      <i className="fas fa-link me-1" />
                                      Onay Bağlantısı Oluştur ve Kopyala
                                    </button>
                                  )}
                                  {orderDetail.status === 'PENDING_PAYMENT' && (
                                    <button
                                      className="btn btn-success btn-sm"
                                      onClick={markManualPaymentReceived}
                                    >
                                      <i className="fas fa-check me-1" />
                                      Ödeme Alındı
                                    </button>
                                  )}
                                  {orderDetail.status === 'PENDING_PAYMENT' && (
                                    <button
                                      className="btn btn-outline-secondary btn-sm"
                                      onClick={() =>
                                        setPaymentPlanModal({
                                          state: orderDetail.manualPaymentState || 'WAITING',
                                          dueAt: orderDetail.paymentDueAt
                                            ? orderDetail.paymentDueAt.slice(0, 16)
                                            : '',
                                          reminderAt: orderDetail.paymentReminderAt
                                            ? orderDetail.paymentReminderAt.slice(0, 16)
                                            : '',
                                        })
                                      }
                                    >
                                      <i className="fas fa-calendar-alt me-1" />
                                      Ödeme Planını Düzenle
                                    </button>
                                  )}
                                </div>
                              )}
                            </>
                          )}
                          <div className="d-flex justify-content-between mb-2">
                            <span className="text-muted">Kargo Firması</span>
                            <span>{orderDetail.cargoCompany || '—'}</span>
                          </div>
                          {orderDetail.cargoTrackingNo ? (
                            <>
                              <div className="d-flex justify-content-between align-items-center mb-2">
                                <span className="text-muted">Takip No</span>
                                <span className="d-flex align-items-center gap-1">
                                  <code className="fw-bold">{orderDetail.cargoTrackingNo}</code>
                                  <button
                                    className="btn btn-sm btn-link p-0"
                                    title="Kopyala"
                                    onClick={() => {
                                      navigator.clipboard.writeText(orderDetail.cargoTrackingNo);
                                      toast.success('Kopyalandı');
                                    }}
                                  >
                                    <i className="fas fa-copy text-muted" />
                                  </button>
                                </span>
                              </div>
                              {orderDetail.cargoCompany && (
                                <button
                                  className="btn btn-sm btn-success w-100 mb-2"
                                  onClick={() => {
                                    axios
                                      .get('/api/admin/cargo-providers')
                                      .then((r) => {
                                        const provider = (r.data || []).find(
                                          (p) =>
                                            p.code === orderDetail.cargoCompany ||
                                            p.name === orderDetail.cargoCompany
                                        );
                                        if (provider?.trackingUrlTemplate) {
                                          window.open(
                                            provider.trackingUrlTemplate.replace(
                                              '{trackingNo}',
                                              orderDetail.cargoTrackingNo
                                            ),
                                            '_blank'
                                          );
                                        } else {
                                          toast.info(
                                            'Bu kargo firması için takip URL tanımlı değil. Kargo Ayarlarından ekleyebilirsiniz.'
                                          );
                                        }
                                      })
                                      .catch(() => toast.error('Kargo bilgisi alınamadı.'));
                                  }}
                                >
                                  <i className="fas fa-external-link-alt me-2" />
                                  Kargo Takip Sayfasına Git
                                </button>
                              )}
                              {/* Kargonomi label PDF download — when cargoProviderShipmentId is present */}
                              <button
                                className="btn btn-sm btn-outline-primary w-100"
                                onClick={() => {
                                  axios
                                    .get(`/api/admin/cargo/orders/${orderDetail.id}/label`, {
                                      responseType: 'blob',
                                    })
                                    .then((r) => {
                                      const url = window.URL.createObjectURL(r.data);
                                      const a = document.createElement('a');
                                      a.href = url;
                                      a.download = `kargo-etiket-${orderDetail.orderNumber}.pdf`;
                                      a.click();
                                      window.URL.revokeObjectURL(url);
                                    })
                                    .catch(async (err) => {
                                      let msg = 'Etiket indirilemedi.';
                                      try {
                                        const body = await err.response?.data?.text?.();
                                        if (body) {
                                          const j = JSON.parse(body);
                                          if (j.message) msg = j.message;
                                        }
                                      } catch {
                                        /* ignore */
                                      }
                                      toast.error(msg);
                                    });
                                }}
                              >
                                <i className="fas fa-download me-2" />
                                Kargo Etiketi İndir (PDF)
                              </button>
                            </>
                          ) : (
                            <div className="d-flex justify-content-between">
                              <span className="text-muted">Takip No</span>
                              <span className="text-muted">—</span>
                            </div>
                          )}
                        </div>
                      </div>

                      {/* Invoice */}
                      <div className="card">
                        <div className="card-header bg-transparent">
                          <h6 className="mb-0">
                            <i className="fas fa-file-invoice me-2 text-info" />
                            Fatura Bilgileri
                          </h6>
                        </div>
                        <div className="card-body small">
                          {orderDetail.billingAddress ? (
                            <>
                              <p className="mb-1 fw-medium">
                                {orderDetail.billingAddress.firstName} {orderDetail.billingAddress.lastName}
                              </p>
                              {orderDetail.billingAddress.companyName && (
                                <p className="mb-1">{orderDetail.billingAddress.companyName}</p>
                              )}
                              {orderDetail.billingAddress.taxOffice && (
                                <p className="mb-1 text-muted">
                                  Vergi Dairesi: {orderDetail.billingAddress.taxOffice}
                                </p>
                              )}
                              {orderDetail.billingAddress.taxNumber && (
                                <p className="mb-1 text-muted">
                                  Vergi No: {orderDetail.billingAddress.taxNumber}
                                </p>
                              )}
                              <p className="mb-1">{orderDetail.billingAddress.addressLine}</p>
                              <p className="mb-0">
                                {orderDetail.billingAddress.district} / {orderDetail.billingAddress.city}
                              </p>
                            </>
                          ) : (
                            <p className="text-muted mb-0">Teslimat adresi ile aynı</p>
                          )}
                          <hr />
                          {/* Invoice Upload / View */}
                          {orderDetail.invoiceUrl ? (
                            (() => {
                              const isPdf = orderDetail.invoiceUrl.toLowerCase().includes('.pdf');
                              const apiUrl = `/api/admin/orders/${orderDetail.id}/invoice/download`;
                              const handleView = () => {
                                axios
                                  .get(apiUrl + '?inline=true', { responseType: 'blob' })
                                  .then((r) => {
                                    const blobUrl = window.URL.createObjectURL(r.data);
                                    window.open(blobUrl, '_blank');
                                  })
                                  .catch(() => toast.error('Fatura açılamadı.'));
                              };
                              const handleDownload = () => {
                                axios
                                  .get(apiUrl, { responseType: 'blob' })
                                  .then((r) => {
                                    const url = window.URL.createObjectURL(r.data);
                                    const a = document.createElement('a');
                                    a.href = url;
                                    a.download = `fatura-${orderDetail.orderNumber}${isPdf ? '.pdf' : '.jpg'}`;
                                    a.click();
                                    window.URL.revokeObjectURL(url);
                                  })
                                  .catch(() => toast.error('Fatura indirilemedi.'));
                              };
                              return (
                                <div className="mb-2">
                                  <div className="d-flex align-items-center gap-2 mb-2">
                                    <span className="badge bg-success">
                                      <i className="fas fa-check me-1" />
                                      Fatura Yüklü
                                    </span>
                                    {orderDetail.invoiceNumber && (
                                      <span className="badge bg-light text-dark border">
                                        #{orderDetail.invoiceNumber}
                                      </span>
                                    )}
                                  </div>
                                  {/* Actions */}
                                  <div className="d-flex gap-2">
                                    <button
                                      className="btn btn-sm btn-outline-primary flex-grow-1"
                                      onClick={handleView}
                                    >
                                      <i className="fas fa-eye me-1" />
                                      Görüntüle
                                    </button>
                                    <button
                                      className="btn btn-sm btn-outline-success flex-grow-1"
                                      onClick={handleDownload}
                                    >
                                      <i className="fas fa-download me-1" />
                                      İndir
                                    </button>
                                    <button
                                      className="btn btn-sm btn-outline-secondary"
                                      onClick={() => {
                                        setInvoiceFile(null);
                                        setInvoiceNumber(orderDetail.invoiceNumber || '');
                                        setShowInvoiceModal(true);
                                      }}
                                    >
                                      <i className="fas fa-sync me-1" />
                                      Değiştir
                                    </button>
                                  </div>
                                </div>
                              );
                            })()
                          ) : (
                            <button
                              className="btn btn-sm btn-outline-success w-100 mb-2"
                              onClick={() => {
                                setInvoiceFile(null);
                                setInvoiceNumber('');
                                setShowInvoiceModal(true);
                              }}
                            >
                              <i className="fas fa-upload me-1" />
                              Fatura Yükle
                            </button>
                          )}
                          <button
                            className="btn btn-sm btn-outline-info w-100"
                            onClick={() => window.print()}
                            title="Yazdır"
                          >
                            <i className="fas fa-print me-1" />
                            Yazdır
                          </button>
                        </div>
                      </div>
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
        <div className="modal show d-block" style={{ background: 'rgba(0,0,0,0.5)', zIndex: 4000 }}>
          <div className="modal-dialog">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">Sipariş Durumunu Güncelle</h5>
                <button className="btn-close" onClick={() => setShowStatusModal(false)} />
              </div>
              <div className="modal-body">
                {orderDetail && (
                  <div className="alert alert-light small mb-3">
                    <strong>Mevcut:</strong> <StatusBadge status={orderDetail.status} />
                    {orderDetail.paymentMethod &&
                      (orderDetail.paymentMethod === 'DOOR_CASH' ||
                        orderDetail.paymentMethod === 'DOOR_CARD') && (
                        <span className="badge bg-warning text-dark ms-2">
                          <i className="fas fa-door-open me-1" />
                          Kapıda Ödeme
                        </span>
                      )}
                  </div>
                )}
                <div className="mb-3">
                  <label className="form-label fw-semibold">Yeni Durum</label>
                  {allowedTransitions.length === 0 ? (
                    <div className="alert alert-info small">
                      Bu sipariş için yapılabilecek durum geçişi bulunmuyor.
                    </div>
                  ) : (
                    <div className="d-flex flex-column gap-2">
                      {allowedTransitions.map((t) => {
                        const cfg = STATUS_CONFIG[t.status] || { color: 'secondary', icon: 'fas fa-circle' };
                        return (
                          <div
                            key={t.status}
                            className={`border rounded p-3 d-flex align-items-center gap-2 ${newStatus === t.status ? 'border-primary bg-primary bg-opacity-10' : 'border-light'}`}
                            style={{ cursor: 'pointer' }}
                            onClick={() => setNewStatus(t.status)}
                          >
                            <input
                              type="radio"
                              className="form-check-input m-0"
                              checked={newStatus === t.status}
                              readOnly
                            />
                            <i className={`${cfg.icon} text-${cfg.color}`} />
                            <span className="fw-medium">{t.label}</span>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
                <div className="mb-3">
                  <label className="form-label">Not (isteğe bağlı)</label>
                  <textarea
                    className="form-control"
                    rows={2}
                    value={statusNote}
                    onChange={(e) => setStatusNote(e.target.value)}
                    placeholder="Durum değişikliği sebebi..."
                  />
                </div>
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setShowStatusModal(false)}>
                  İptal
                </button>
                <button
                  className="btn btn-primary"
                  onClick={updateStatus}
                  disabled={allowedTransitions.length === 0}
                >
                  Güncelle
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ══ Bank Transfer Confirmation Modal — reference + amount visible, verification flow ══ */}
      {confirmModal && orderDetail && (
        <div
          className="modal show d-block"
          style={{ background: 'rgba(0,0,0,0.5)', zIndex: 4500 }}
          onClick={() => setConfirmModal(null)}
        >
          <div className="modal-dialog modal-dialog-centered" onClick={(e) => e.stopPropagation()}>
            <div className="modal-content border-0 shadow-lg" style={{ borderRadius: 14 }}>
              <div className="modal-header bg-success bg-opacity-10 border-0">
                <h5 className="modal-title fw-bold text-success">
                  <i className="fas fa-check-circle me-2" />
                  Havale Onayı
                </h5>
                <button type="button" className="btn-close" onClick={() => setConfirmModal(null)} />
              </div>
              <div className="modal-body">
                {/* Matching summary — what the admin should keep in view */}
                <div className="card bg-light border-0 mb-3">
                  <div className="card-body p-3">
                    <div className="row g-2 small">
                      <div className="col-12 d-flex align-items-center gap-2">
                        <span className="text-muted">Referans:</span>
                        <code className="fw-bold flex-grow-1">{orderDetail.bankTransferReference}</code>
                        <button
                          className="btn btn-sm btn-outline-secondary py-0 px-2"
                          onClick={() => {
                            navigator.clipboard.writeText(orderDetail.bankTransferReference);
                            toast.success('Kopyalandı');
                          }}
                        >
                          <i className="fas fa-copy" />
                        </button>
                      </div>
                      <div className="col-6">
                        <span className="text-muted">Müşteri:</span>{' '}
                        <strong>{orderDetail.customerName}</strong>
                      </div>
                      <div className="col-6 text-end">
                        <span className="text-muted">Beklenen:</span>{' '}
                        <strong className="text-primary">
                          ₺{orderDetail.grandTotal?.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
                        </strong>
                      </div>
                    </div>
                  </div>
                </div>

                <div className="alert alert-warning small mb-3">
                  <i className="fas fa-exclamation-triangle me-1" />
                  Lütfen banka ekstresine bakarak <strong>gerçek</strong> yatırılan tutarı ve tarihi girin.
                  Sistemle ±1.00 TL'den fazla fark varsa onay reddedilir.
                </div>

                <div className="mb-3">
                  <label className="form-label fw-medium">
                    Yatırılan Tutar <span className="text-danger">*</span>
                  </label>
                  <div className="input-group">
                    <span className="input-group-text">₺</span>
                    <input
                      type="number"
                      step="0.01"
                      className="form-control form-control-lg fw-bold"
                      value={confirmModal.paidAmount}
                      onChange={(e) => setConfirmModal({ ...confirmModal, paidAmount: e.target.value })}
                      autoFocus
                    />
                  </div>
                  <small className="text-muted">Ekstrede görünen kesin tutar (TL)</small>
                </div>

                <div className="mb-3">
                  <label className="form-label fw-medium">
                    Ödeme Tarihi <span className="text-danger">*</span>
                  </label>
                  <input
                    type="datetime-local"
                    className="form-control"
                    value={confirmModal.paidAt}
                    onChange={(e) => setConfirmModal({ ...confirmModal, paidAt: e.target.value })}
                  />
                  <small className="text-muted">Bankadaki gerçek işlem tarihi (ekstreden)</small>
                </div>

                <div className="mb-2">
                  <label className="form-label fw-medium">
                    Banka Referans / Not <span className="text-muted small">(opsiyonel)</span>
                  </label>
                  <input
                    type="text"
                    className="form-control"
                    value={confirmModal.receiptNote}
                    onChange={(e) => setConfirmModal({ ...confirmModal, receiptNote: e.target.value })}
                    placeholder="Banka swift no, ekstre satır no vb."
                  />
                </div>
              </div>
              <div className="modal-footer border-0">
                <button className="btn btn-outline-secondary" onClick={() => setConfirmModal(null)}>
                  İptal
                </button>
                <button className="btn btn-success" onClick={submitConfirmPayment}>
                  <i className="fas fa-check me-1" />
                  Onayla
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {paymentPlanModal && (
        <div className="modal show d-block" style={{ background: 'rgba(0,0,0,.5)', zIndex: 6500 }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">Ödeme Planı</h5>
                <button className="btn-close" onClick={() => setPaymentPlanModal(null)} />
              </div>
              <div className="modal-body">
                <div className="mb-3">
                  <label className="form-label">Durum</label>
                  <select
                    className="form-select"
                    value={paymentPlanModal.state}
                    onChange={(e) => setPaymentPlanModal({ ...paymentPlanModal, state: e.target.value })}
                  >
                    <option value="WAITING">Ödeme bekleniyor</option>
                    <option value="SCHEDULED">Belirli tarihte ödenecek</option>
                  </select>
                </div>
                <div className="mb-3">
                  <label className="form-label">Son ödeme tarihi</label>
                  <input
                    type="datetime-local"
                    className="form-control"
                    value={paymentPlanModal.dueAt || ''}
                    onChange={(e) => setPaymentPlanModal({ ...paymentPlanModal, dueAt: e.target.value })}
                  />
                </div>
                <div>
                  <label className="form-label">Admin hatırlatma zamanı</label>
                  <input
                    type="datetime-local"
                    className="form-control"
                    value={paymentPlanModal.reminderAt || ''}
                    onChange={(e) => setPaymentPlanModal({ ...paymentPlanModal, reminderAt: e.target.value })}
                  />
                </div>
              </div>
              <div className="modal-footer">
                <button className="btn btn-outline-secondary" onClick={() => setPaymentPlanModal(null)}>
                  İptal
                </button>
                <button className="btn btn-primary" onClick={savePaymentPlan}>
                  Kaydet
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Cargo Modal */}
      {showCargoModal && (
        <div className="modal show d-block" style={{ background: 'rgba(0,0,0,0.5)', zIndex: 4000 }}>
          <div className="modal-dialog">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">Kargo Bilgisi</h5>
                <button className="btn-close" onClick={() => setShowCargoModal(false)} />
              </div>
              <div className="modal-body">
                <div className="mb-3">
                  <label className="form-label">Kargo Firması</label>
                  <select
                    className="form-select"
                    value={cargoCompany}
                    onChange={(e) => setCargoCompany(e.target.value)}
                  >
                    <option value="">Seçiniz</option>
                    <option value="YURTICI">Yurtiçi Kargo</option>
                    <option value="ARAS">Aras Kargo</option>
                    <option value="MNG">MNG Kargo</option>
                    <option value="PTT">PTT Kargo</option>
                    <option value="UPS">UPS</option>
                  </select>
                </div>
                <div className="mb-3">
                  <label className="form-label">Takip Numarası</label>
                  <input
                    className="form-control"
                    value={cargoTrackingNo}
                    onChange={(e) => setCargoTrackingNo(e.target.value)}
                    placeholder="Kargo takip no"
                  />
                </div>
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setShowCargoModal(false)}>
                  İptal
                </button>
                <button className="btn btn-primary" onClick={updateCargo}>
                  Kaydet
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
