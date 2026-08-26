import React, { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import axios from 'axios';

const STATUS_CONFIG = {
  DRAFT: { label: 'Taslak', color: 'secondary', icon: 'fas fa-edit', desc: "Henüz GİB'e gönderilmedi" },
  PENDING: { label: 'Beklemede', color: 'info', icon: 'fas fa-hourglass-half', desc: 'GİB onayı bekleniyor' },
  APPROVED: {
    label: 'Onaylandı',
    color: 'success',
    icon: 'fas fa-check-circle',
    desc: 'GİB tarafından onaylandı',
  },
  REJECTED: {
    label: 'Reddedildi',
    color: 'danger',
    icon: 'fas fa-times-circle',
    desc: 'GİB tarafından reddedildi',
  },
  CANCELLED: { label: 'İptal', color: 'dark', icon: 'fas fa-ban', desc: 'Fatura iptal edildi' },
  ERROR: {
    label: 'Hata',
    color: 'danger',
    icon: 'fas fa-exclamation-triangle',
    desc: 'Gönderim sırasında hata',
  },
};

const TYPE_CONFIG = {
  E_ARSIV: { label: 'e-Arşiv', color: 'primary', icon: 'fas fa-archive' },
  E_FATURA: { label: 'e-Fatura', color: 'info', icon: 'fas fa-file-invoice' },
};

const formatPrice = (p) =>
  new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(p || 0);
const formatDate = (d) =>
  d
    ? new Date(d).toLocaleString('tr-TR', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '\u2014';
const formatDateShort = (d) =>
  d
    ? new Date(d).toLocaleDateString('tr-TR', { day: '2-digit', month: '2-digit', year: 'numeric' })
    : '\u2014';

// Relative time helper
const timeAgo = (d) => {
  if (!d) return '';
  const diff = Date.now() - new Date(d).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'az önce';
  if (mins < 60) return `${mins} dk önce`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours} sa önce`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days} gün önce`;
  return formatDateShort(d);
};

const copyToClipboard = (text, onDone) => {
  if (!text) return;
  navigator.clipboard.writeText(String(text)).then(() => onDone && onDone());
};

export default function AdminInvoices() {
  const [invoices, setInvoices] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [sortBy, setSortBy] = useState('createdAt');
  const [sortDir, setSortDir] = useState('desc');
  const [selectedInvoice, setSelectedInvoice] = useState(null);
  const [selectedIds, setSelectedIds] = useState(new Set());
  const [actionLoading, setActionLoading] = useState(false);
  const [toast, setToast] = useState({ show: false, message: '', type: 'success' });
  const [confirmAction, setConfirmAction] = useState(null); // { type, id, title, message }
  const [copiedId, setCopiedId] = useState(null);
  const searchInputRef = useRef(null);

  // Manual "Create invoice for order" modal state
  const [createOpen, setCreateOpen] = useState(false);
  const [createSearch, setCreateSearch] = useState('');
  const [createSearchResults, setCreateSearchResults] = useState([]);
  const [createSearchLoading, setCreateSearchLoading] = useState(false);
  const [createBusyOrderId, setCreateBusyOrderId] = useState(null);

  const showToast = (message, type = 'success') => {
    setToast({ show: true, message, type });
    setTimeout(() => setToast({ show: false, message: '', type: 'success' }), 3500);
  };

  // Debounce search input
  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(search), 350);
    return () => clearTimeout(t);
  }, [search]);

  const fetchInvoices = useCallback(
    async (p = 0) => {
      setLoading(true);
      try {
        const params = { page: p, size: 20, sort: `${sortBy},${sortDir}` };
        if (statusFilter) params.status = statusFilter;
        if (typeFilter) params.invoiceType = typeFilter;
        if (debouncedSearch.trim()) params.search = debouncedSearch.trim();
        if (dateFrom) params.dateFrom = dateFrom;
        if (dateTo) params.dateTo = dateTo;
        const res = await axios.get('/api/admin/invoices', { params });
        setInvoices(res.data.content || []);
        setTotalPages(res.data.totalPages || 0);
        setTotalElements(res.data.totalElements || 0);
        setPage(p);
        setSelectedIds(new Set());
      } catch {
        setInvoices([]);
      } finally {
        setLoading(false);
      }
    },
    [statusFilter, typeFilter, debouncedSearch, dateFrom, dateTo, sortBy, sortDir]
  );

  useEffect(() => {
    fetchInvoices(0);
  }, [fetchInvoices]);

  // Keyboard shortcuts
  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'Escape') {
        if (confirmAction) setConfirmAction(null);
        else if (selectedInvoice) setSelectedInvoice(null);
      }
      if (e.key === '/' && !['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName)) {
        e.preventDefault();
        searchInputRef.current?.focus();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [selectedInvoice, confirmAction]);

  const handleRegenerate = async (id) => {
    setActionLoading(true);
    try {
      const res = await axios.post(`/api/admin/invoices/${id}/regenerate`);
      showToast('Fatura yeniden oluşturuldu.');
      setSelectedInvoice(res.data);
      fetchInvoices(page);
    } catch (err) {
      showToast(err.response?.data?.message || 'Fatura yeniden oluşturulamadı.', 'danger');
    } finally {
      setActionLoading(false);
      setConfirmAction(null);
    }
  };

  const handleCancel = async (id) => {
    setActionLoading(true);
    try {
      const res = await axios.post(`/api/admin/invoices/${id}/cancel`);
      showToast('Fatura iptal edildi.');
      setSelectedInvoice(res.data);
      fetchInvoices(page);
    } catch (err) {
      showToast(err.response?.data?.message || 'Fatura iptal edilemedi.', 'danger');
    } finally {
      setActionLoading(false);
      setConfirmAction(null);
    }
  };

  const handleDownloadPdf = async (id, invoiceNumber) => {
    try {
      const res = await axios.get(`/api/admin/invoices/${id}/pdf`, { responseType: 'blob' });
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `fatura-${invoiceNumber || id}.pdf`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch {
      showToast('PDF indirilemedi.', 'danger');
    }
  };

  const handleBulkDownload = async () => {
    if (selectedIds.size === 0) return;
    const selected = invoices.filter((i) => selectedIds.has(i.id) && i.hasPdf);
    if (selected.length === 0) {
      showToast('Seçili faturalar için PDF bulunamadı.', 'warning');
      return;
    }
    showToast(`${selected.length} PDF indiriliyor...`, 'info');
    for (const inv of selected) {
      await handleDownloadPdf(inv.id, inv.invoiceNumber);
    }
  };

  const buildCsv = (rows) => {
    const header = [
      'Fatura No',
      'Sipariş No',
      'Alıcı',
      'Vergi No',
      'Tip',
      'Durum',
      'Ara Toplam',
      'KDV',
      'Toplam',
      'Oluşturulma',
    ];
    const all = [
      header,
      ...rows.map((i) => [
        i.invoiceNumber || '',
        i.orderNumber || '',
        i.recipientName || '',
        i.recipientTaxId || '',
        (TYPE_CONFIG[i.invoiceType] || {}).label || i.invoiceType,
        (STATUS_CONFIG[i.status] || {}).label || i.status,
        i.subtotal || 0,
        i.vatAmount || 0,
        i.totalAmount || 0,
        formatDate(i.createdAt),
      ]),
    ];
    return all.map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(';')).join('\n');
  };

  const downloadCsv = (csvContent, filename) => {
    const blob = new Blob(['\uFEFF' + csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  };

  // Export only current page
  const handleExportCsv = () => {
    if (invoices.length === 0) return;
    downloadCsv(
      buildCsv(invoices),
      `faturalar-sayfa-${page + 1}-${new Date().toISOString().slice(0, 10)}.csv`
    );
    showToast(`${invoices.length} fatura CSV olarak indirildi.`);
  };

  // Export all pages matching current filters
  const handleExportAllCsv = async () => {
    if (totalElements === 0) return;
    showToast(`${totalElements} fatura indiriliyor, lütfen bekleyin...`, 'info');
    try {
      const params = { page: 0, size: Math.min(totalElements, 5000), sort: `${sortBy},${sortDir}` };
      if (statusFilter) params.status = statusFilter;
      if (typeFilter) params.invoiceType = typeFilter;
      if (debouncedSearch.trim()) params.search = debouncedSearch.trim();
      if (dateFrom) params.dateFrom = dateFrom;
      if (dateTo) params.dateTo = dateTo;
      const res = await axios.get('/api/admin/invoices', { params });
      const all = res.data.content || [];
      downloadCsv(buildCsv(all), `faturalar-tumu-${new Date().toISOString().slice(0, 10)}.csv`);
      showToast(`${all.length} fatura CSV olarak indirildi.`);
    } catch {
      showToast('CSV indirilemedi.', 'danger');
    }
  };

  // ── Manual "Create invoice for order" ──

  // Debounced order search — by order number or customer name
  useEffect(() => {
    if (!createOpen) return;
    const q = createSearch.trim();
    if (!q) {
      setCreateSearchResults([]);
      return;
    }
    const t = setTimeout(async () => {
      setCreateSearchLoading(true);
      try {
        const res = await axios.get('/api/admin/orders', {
          params: { page: 0, size: 15, search: q, sortBy: 'createdAt', sortDir: 'desc' },
        });
        setCreateSearchResults(res.data?.content || res.data?.items || []);
      } catch {
        setCreateSearchResults([]);
      } finally {
        setCreateSearchLoading(false);
      }
    }, 300);
    return () => clearTimeout(t);
  }, [createSearch, createOpen]);

  const openCreateModal = () => {
    setCreateOpen(true);
    setCreateSearch('');
    setCreateSearchResults([]);
  };

  const handleCreateForOrder = async (orderId, orderNumber) => {
    setCreateBusyOrderId(orderId);
    try {
      const res = await axios.post(`/api/admin/invoices/auto/${orderId}`);
      const inv = res.data;
      showToast(`Fatura oluşturuldu: ${inv.invoiceNumber || '(numara atanıyor)'} — sipariş ${orderNumber}`);
      setCreateOpen(false);
      fetchInvoices(0); // refresh list
    } catch (e) {
      showToast(e?.response?.data?.message || 'Fatura oluşturulamadı.', 'danger');
    } finally {
      setCreateBusyOrderId(null);
    }
  };

  const toggleSelect = (id) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const toggleSelectAll = () => {
    if (selectedIds.size === invoices.length) setSelectedIds(new Set());
    else setSelectedIds(new Set(invoices.map((i) => i.id)));
  };

  const toggleSort = (field) => {
    if (sortBy === field) setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    else {
      setSortBy(field);
      setSortDir('desc');
    }
  };

  const clearAllFilters = () => {
    setStatusFilter('');
    setTypeFilter('');
    setSearch('');
    setDateFrom('');
    setDateTo('');
  };

  const handleCopy = (text, id) => {
    copyToClipboard(text, () => {
      setCopiedId(id);
      setTimeout(() => setCopiedId(null), 1200);
    });
  };

  // Summary counts
  const stats = useMemo(
    () => ({
      approved: invoices.filter((i) => i.status === 'APPROVED').length,
      pending: invoices.filter((i) => i.status === 'PENDING').length,
      error: invoices.filter((i) => i.status === 'ERROR' || i.status === 'REJECTED').length,
      draft: invoices.filter((i) => i.status === 'DRAFT').length,
      totalSum: invoices.reduce((s, i) => s + (Number(i.totalAmount) || 0), 0),
    }),
    [invoices]
  );

  const hasActiveFilters = statusFilter || typeFilter || search || dateFrom || dateTo;
  const activeFilterCount = [statusFilter, typeFilter, search, dateFrom, dateTo].filter(Boolean).length;

  const SortIcon = ({ field }) => {
    if (sortBy !== field)
      return <i className="fas fa-sort text-muted ms-1 opacity-50" style={{ fontSize: 10 }}></i>;
    return (
      <i
        className={`fas fa-sort-${sortDir === 'asc' ? 'up' : 'down'} text-primary ms-1`}
        style={{ fontSize: 10 }}
      ></i>
    );
  };

  return (
    <div>
      {/* Header */}
      <div className="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
        <div>
          <h2 className="mb-1 d-flex align-items-center gap-2">
            <i className="fas fa-file-invoice text-primary"></i>
            E-Fatura Yönetimi
          </h2>
          <p className="text-muted small mb-0">
            Toplam <strong>{totalElements}</strong> fatura
            {hasActiveFilters && (
              <span className="ms-2 badge bg-warning bg-opacity-25 text-warning-emphasis border border-warning-subtle">
                {activeFilterCount} filtre aktif
              </span>
            )}
          </p>
        </div>
        <div className="d-flex gap-2">
          <div className="dropdown">
            <button
              className="btn btn-sm btn-outline-secondary dropdown-toggle"
              type="button"
              data-bs-toggle="dropdown"
              disabled={totalElements === 0}
              title="Filtreli fatura listesini Excel'de açılabilen CSV olarak indir"
            >
              <i className="fas fa-file-csv me-1"></i>CSV İndir
            </button>
            <ul className="dropdown-menu dropdown-menu-end">
              <li>
                <button className="dropdown-item" onClick={handleExportCsv} disabled={invoices.length === 0}>
                  <i className="fas fa-file-alt me-2 text-muted"></i>
                  <span>
                    Bu sayfayı indir <span className="text-muted small">({invoices.length} fatura)</span>
                  </span>
                </button>
              </li>
              <li>
                <button className="dropdown-item" onClick={handleExportAllCsv} disabled={totalElements === 0}>
                  <i className="fas fa-download me-2 text-primary"></i>
                  <span>
                    Tüm filtreli sonuçları indir{' '}
                    <span className="text-muted small">({totalElements} fatura)</span>
                  </span>
                </button>
              </li>
              <li>
                <hr className="dropdown-divider" />
              </li>
              <li>
                <span className="dropdown-item-text small text-muted" style={{ maxWidth: 280 }}>
                  <i className="fas fa-info-circle me-1"></i>
                  Excel ile açılabilir. Türkçe karakter ve UTF-8 destekli.
                </span>
              </li>
            </ul>
          </div>
          <button
            className="btn btn-sm btn-outline-primary"
            onClick={() => fetchInvoices(page)}
            title="Listeyi yenile"
          >
            <i className="fas fa-sync-alt me-1"></i>Yenile
          </button>
          <button
            className="btn btn-sm btn-primary"
            onClick={openCreateModal}
            title="Mevcut bir sipariş için manuel fatura oluştur"
          >
            <i className="fas fa-plus me-1"></i>Sipariş İçin Fatura Oluştur
          </button>
        </div>
      </div>

      {/* Summary Cards (clickable quick filters) */}
      <div className="row g-3 mb-4">
        {[
          { key: '', label: 'Toplam', count: totalElements, color: 'primary', icon: 'fas fa-file-invoice' },
          {
            key: 'APPROVED',
            label: 'Onaylı',
            count: stats.approved,
            color: 'success',
            icon: 'fas fa-check-circle',
          },
          {
            key: 'PENDING',
            label: 'Beklemede',
            count: stats.pending,
            color: 'info',
            icon: 'fas fa-hourglass-half',
          },
          { key: 'DRAFT', label: 'Taslak', count: stats.draft, color: 'warning', icon: 'fas fa-edit' },
          {
            key: 'ERROR',
            label: 'Hatalı',
            count: stats.error,
            color: 'danger',
            icon: 'fas fa-exclamation-triangle',
          },
        ].map((card) => {
          const active = statusFilter === card.key;
          return (
            <div key={card.key || 'all'} className="col-6 col-md">
              <button
                type="button"
                onClick={() => setStatusFilter(card.key)}
                className={`card border-0 shadow-sm w-100 text-start ${active ? `border-${card.color}` : ''}`}
                style={{
                  cursor: 'pointer',
                  transition: 'transform .15s, box-shadow .15s',
                  transform: active ? 'translateY(-2px)' : 'none',
                  boxShadow: active ? `0 4px 12px rgba(0,0,0,.1)` : undefined,
                  borderTop: active ? `3px solid var(--bs-${card.color})` : '3px solid transparent',
                }}
                onMouseEnter={(e) => (e.currentTarget.style.transform = 'translateY(-2px)')}
                onMouseLeave={(e) => (e.currentTarget.style.transform = active ? 'translateY(-2px)' : 'none')}
              >
                <div className="card-body d-flex align-items-center gap-3">
                  <div
                    className={`rounded-circle bg-${card.color} bg-opacity-10 d-flex align-items-center justify-content-center`}
                    style={{ width: 44, height: 44 }}
                  >
                    <i className={`${card.icon} text-${card.color}`} />
                  </div>
                  <div>
                    <div className="fw-bold fs-5">{card.count}</div>
                    <div className="text-muted small">{card.label}</div>
                  </div>
                </div>
              </button>
            </div>
          );
        })}
      </div>

      {/* Filters */}
      <div className="card border-0 shadow-sm mb-3">
        <div className="card-body">
          <div className="row g-2 align-items-end">
            <div className="col-12 col-md-4">
              <label
                className="form-label small text-muted mb-1 d-flex align-items-center justify-content-between"
                style={{ minHeight: 20 }}
              >
                <span>
                  <i className="fas fa-search me-1"></i>Ara
                </span>
                <kbd
                  className="text-muted bg-light border"
                  style={{ fontSize: 10, padding: '1px 6px', borderRadius: 3 }}
                >
                  /
                </kbd>
              </label>
              <div className="input-group input-group-sm">
                <input
                  ref={searchInputRef}
                  type="text"
                  className="form-control"
                  placeholder="Fatura no, alıcı, sipariş no, vergi no..."
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                />
                {search && (
                  <button
                    className="btn btn-outline-secondary"
                    onClick={() => setSearch('')}
                    title="Aramayı temizle"
                    type="button"
                  >
                    <i className="fas fa-times"></i>
                  </button>
                )}
              </div>
            </div>
            <div className="col-6 col-md-2">
              <label className="form-label small text-muted mb-1 d-block" style={{ minHeight: 20 }}>
                Tip
              </label>
              <select
                className="form-select form-select-sm"
                value={typeFilter}
                onChange={(e) => setTypeFilter(e.target.value)}
              >
                <option value="">Tümü</option>
                {Object.entries(TYPE_CONFIG).map(([key, cfg]) => (
                  <option key={key} value={key}>
                    {cfg.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="col-6 col-md-2">
              <label className="form-label small text-muted mb-1 d-block" style={{ minHeight: 20 }}>
                Başlangıç
              </label>
              <input
                type="date"
                className="form-control form-control-sm"
                value={dateFrom}
                onChange={(e) => setDateFrom(e.target.value)}
              />
            </div>
            <div className="col-6 col-md-2">
              <label className="form-label small text-muted mb-1 d-block" style={{ minHeight: 20 }}>
                Bitiş
              </label>
              <input
                type="date"
                className="form-control form-control-sm"
                value={dateTo}
                onChange={(e) => setDateTo(e.target.value)}
              />
            </div>
            <div className="col-6 col-md-2">
              <label
                className="form-label small text-muted mb-1 d-block"
                style={{ minHeight: 20, visibility: hasActiveFilters ? 'visible' : 'hidden' }}
              >
                &nbsp;
              </label>
              {hasActiveFilters ? (
                <button className="btn btn-sm btn-outline-danger w-100" onClick={clearAllFilters}>
                  <i className="fas fa-filter-circle-xmark me-1"></i>Temizle
                </button>
              ) : (
                <div style={{ height: 'calc(1.5em + 0.5rem + 2px)' }}></div>
              )}
            </div>
          </div>

          {/* Active filter chips */}
          {hasActiveFilters && (
            <div className="d-flex flex-wrap gap-2 mt-3 pt-3 border-top">
              {statusFilter && (
                <span className="badge bg-light text-dark border d-flex align-items-center gap-1 py-2 px-2">
                  <span className="text-muted">Durum:</span>
                  <strong>{STATUS_CONFIG[statusFilter]?.label}</strong>
                  <button
                    className="btn-close btn-close-sm ms-1"
                    style={{ fontSize: 10 }}
                    onClick={() => setStatusFilter('')}
                  ></button>
                </span>
              )}
              {typeFilter && (
                <span className="badge bg-light text-dark border d-flex align-items-center gap-1 py-2 px-2">
                  <span className="text-muted">Tip:</span>
                  <strong>{TYPE_CONFIG[typeFilter]?.label}</strong>
                  <button
                    className="btn-close btn-close-sm ms-1"
                    style={{ fontSize: 10 }}
                    onClick={() => setTypeFilter('')}
                  ></button>
                </span>
              )}
              {search && (
                <span className="badge bg-light text-dark border d-flex align-items-center gap-1 py-2 px-2">
                  <span className="text-muted">Arama:</span>
                  <strong>"{search}"</strong>
                  <button
                    className="btn-close btn-close-sm ms-1"
                    style={{ fontSize: 10 }}
                    onClick={() => setSearch('')}
                  ></button>
                </span>
              )}
              {dateFrom && (
                <span className="badge bg-light text-dark border d-flex align-items-center gap-1 py-2 px-2">
                  <span className="text-muted">Başlangıç:</span>
                  <strong>{dateFrom}</strong>
                  <button
                    className="btn-close btn-close-sm ms-1"
                    style={{ fontSize: 10 }}
                    onClick={() => setDateFrom('')}
                  ></button>
                </span>
              )}
              {dateTo && (
                <span className="badge bg-light text-dark border d-flex align-items-center gap-1 py-2 px-2">
                  <span className="text-muted">Bitiş:</span>
                  <strong>{dateTo}</strong>
                  <button
                    className="btn-close btn-close-sm ms-1"
                    style={{ fontSize: 10 }}
                    onClick={() => setDateTo('')}
                  ></button>
                </span>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Bulk action bar */}
      {selectedIds.size > 0 && (
        <div className="alert alert-primary d-flex align-items-center justify-content-between py-2 mb-3">
          <div>
            <i className="fas fa-check-square me-2"></i>
            <strong>{selectedIds.size}</strong> fatura seçili
          </div>
          <div className="d-flex gap-2">
            <button className="btn btn-sm btn-primary" onClick={handleBulkDownload}>
              <i className="fas fa-download me-1"></i>Toplu PDF İndir
            </button>
            <button className="btn btn-sm btn-outline-secondary" onClick={() => setSelectedIds(new Set())}>
              Seçimi Temizle
            </button>
          </div>
        </div>
      )}

      {/* Invoice Table */}
      <div className="card border-0 shadow-sm">
        <div className="card-body p-0">
          {loading ? (
            <div className="p-3">
              {[...Array(6)].map((_, i) => (
                <div
                  key={i}
                  className="d-flex align-items-center gap-3 py-2 border-bottom"
                  style={{ opacity: 0.5 }}
                >
                  <div className="bg-light rounded" style={{ width: 20, height: 20 }}></div>
                  <div className="bg-light rounded flex-grow-1" style={{ height: 14, maxWidth: 120 }}></div>
                  <div className="bg-light rounded" style={{ height: 14, width: 80 }}></div>
                  <div className="bg-light rounded flex-grow-1" style={{ height: 14, maxWidth: 150 }}></div>
                  <div className="bg-light rounded" style={{ height: 20, width: 60 }}></div>
                  <div className="bg-light rounded" style={{ height: 14, width: 80 }}></div>
                  <div className="bg-light rounded" style={{ height: 20, width: 90 }}></div>
                  <div className="bg-light rounded" style={{ height: 14, width: 100 }}></div>
                </div>
              ))}
            </div>
          ) : invoices.length === 0 ? (
            <div className="text-center py-5 text-muted">
              <i className="fas fa-file-invoice fa-3x mb-3 opacity-25"></i>
              <p className="mb-2">Fatura bulunamadı</p>
              {hasActiveFilters && (
                <button className="btn btn-sm btn-outline-primary" onClick={clearAllFilters}>
                  Filtreleri temizle ve tekrar dene
                </button>
              )}
            </div>
          ) : (
            <div className="table-responsive">
              <table className="table table-hover mb-0 align-middle">
                <thead className="table-light" style={{ position: 'sticky', top: 0, zIndex: 1 }}>
                  <tr>
                    <th style={{ width: 40 }}>
                      <input
                        type="checkbox"
                        className="form-check-input"
                        checked={invoices.length > 0 && selectedIds.size === invoices.length}
                        ref={(el) => {
                          if (el)
                            el.indeterminate = selectedIds.size > 0 && selectedIds.size < invoices.length;
                        }}
                        onChange={toggleSelectAll}
                      />
                    </th>
                    <th
                      style={{ cursor: 'pointer', userSelect: 'none' }}
                      onClick={() => toggleSort('invoiceNumber')}
                    >
                      Fatura No <SortIcon field="invoiceNumber" />
                    </th>
                    <th
                      style={{ cursor: 'pointer', userSelect: 'none' }}
                      onClick={() => toggleSort('orderNumber')}
                    >
                      Sipariş <SortIcon field="orderNumber" />
                    </th>
                    <th>Alıcı</th>
                    <th>Tip</th>
                    <th
                      style={{ cursor: 'pointer', userSelect: 'none' }}
                      onClick={() => toggleSort('totalAmount')}
                    >
                      Tutar <SortIcon field="totalAmount" />
                    </th>
                    <th>Durum</th>
                    <th
                      style={{ cursor: 'pointer', userSelect: 'none' }}
                      onClick={() => toggleSort('createdAt')}
                    >
                      Tarih <SortIcon field="createdAt" />
                    </th>
                    <th className="text-end">İşlemler</th>
                  </tr>
                </thead>
                <tbody>
                  {invoices.map((inv) => {
                    const statusCfg = STATUS_CONFIG[inv.status] || STATUS_CONFIG.DRAFT;
                    const typeCfg = TYPE_CONFIG[inv.invoiceType] || TYPE_CONFIG.E_ARSIV;
                    const isSelected = selectedIds.has(inv.id);
                    return (
                      <tr
                        key={inv.id}
                        style={{
                          cursor: 'pointer',
                          backgroundColor: isSelected ? 'rgba(13,110,253,0.05)' : undefined,
                        }}
                        onClick={() => setSelectedInvoice(inv)}
                      >
                        <td onClick={(e) => e.stopPropagation()}>
                          <input
                            type="checkbox"
                            className="form-check-input"
                            checked={isSelected}
                            onChange={() => toggleSelect(inv.id)}
                          />
                        </td>
                        <td>
                          <div className="d-flex align-items-center gap-2">
                            <span className="fw-semibold">
                              {inv.invoiceNumber || <span className="text-muted fst-italic">Atanmadı</span>}
                            </span>
                            {inv.invoiceNumber && (
                              <button
                                type="button"
                                className="btn btn-sm btn-link p-0 text-muted"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  handleCopy(inv.invoiceNumber, `num-${inv.id}`);
                                }}
                                title="Kopyala"
                              >
                                <i
                                  className={`fas ${copiedId === `num-${inv.id}` ? 'fa-check text-success' : 'fa-copy'}`}
                                  style={{ fontSize: 11 }}
                                ></i>
                              </button>
                            )}
                          </div>
                        </td>
                        <td>
                          <span className="badge bg-light text-dark border font-monospace">
                            {inv.orderNumber}
                          </span>
                        </td>
                        <td className="text-truncate" style={{ maxWidth: 180 }} title={inv.recipientName}>
                          {inv.recipientName || <span className="text-muted">—</span>}
                          {inv.recipientTaxId && <div className="small text-muted">{inv.recipientTaxId}</div>}
                        </td>
                        <td>
                          <span
                            className={`badge bg-${typeCfg.color} bg-opacity-10 text-${typeCfg.color} border border-${typeCfg.color}-subtle`}
                          >
                            <i className={`${typeCfg.icon} me-1`}></i>
                            {typeCfg.label}
                          </span>
                        </td>
                        <td className="fw-semibold">{formatPrice(inv.totalAmount)}</td>
                        <td>
                          <span className={`badge bg-${statusCfg.color}`} title={statusCfg.desc}>
                            <i className={`${statusCfg.icon} me-1`}></i>
                            {statusCfg.label}
                          </span>
                        </td>
                        <td>
                          <div className="small">{formatDateShort(inv.issuedAt || inv.createdAt)}</div>
                          <div className="small text-muted">{timeAgo(inv.issuedAt || inv.createdAt)}</div>
                        </td>
                        <td className="text-end" onClick={(e) => e.stopPropagation()}>
                          <div className="btn-group btn-group-sm">
                            {inv.hasPdf && (
                              <button
                                className="btn btn-outline-primary"
                                title="PDF İndir"
                                onClick={() => handleDownloadPdf(inv.id, inv.invoiceNumber)}
                              >
                                <i className="fas fa-download"></i>
                              </button>
                            )}
                            {(inv.status === 'ERROR' ||
                              inv.status === 'DRAFT' ||
                              inv.status === 'REJECTED') && (
                              <button
                                className="btn btn-outline-warning"
                                title="Yeniden Oluştur"
                                disabled={actionLoading}
                                onClick={() =>
                                  setConfirmAction({
                                    type: 'regenerate',
                                    id: inv.id,
                                    title: 'Yeniden Oluştur',
                                    message: `${inv.invoiceNumber || 'Bu'} faturayı yeniden oluşturmak istiyor musunuz?`,
                                    btnClass: 'btn-warning',
                                    btnLabel: 'Yeniden Oluştur',
                                  })
                                }
                              >
                                <i className="fas fa-redo"></i>
                              </button>
                            )}
                            {inv.status !== 'CANCELLED' && (
                              <button
                                className="btn btn-outline-danger"
                                title="İptal Et"
                                disabled={actionLoading}
                                onClick={() =>
                                  setConfirmAction({
                                    type: 'cancel',
                                    id: inv.id,
                                    title: 'Faturayı İptal Et',
                                    message: `${inv.invoiceNumber || 'Bu'} faturayı iptal etmek istediğinize emin misiniz? Bu işlem geri alınamaz.`,
                                    btnClass: 'btn-danger',
                                    btnLabel: 'İptal Et',
                                  })
                                }
                              >
                                <i className="fas fa-ban"></i>
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
                {invoices.length > 0 && (
                  <tfoot className="table-light">
                    <tr>
                      <td colSpan="5" className="text-end fw-semibold">
                        Sayfa Toplamı:
                      </td>
                      <td className="fw-bold text-primary" colSpan="4">
                        {formatPrice(stats.totalSum)}
                      </td>
                    </tr>
                  </tfoot>
                )}
              </table>
            </div>
          )}
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="card-footer bg-white border-top d-flex flex-wrap justify-content-between align-items-center gap-2">
            <span className="text-muted small">
              Sayfa <strong>{page + 1}</strong> / {totalPages}{' '}
              <span className="text-muted">({totalElements} fatura)</span>
            </span>
            <div className="btn-group btn-group-sm">
              <button
                className="btn btn-outline-primary"
                disabled={page === 0}
                onClick={() => fetchInvoices(0)}
                title="İlk sayfa"
              >
                <i className="fas fa-angle-double-left"></i>
              </button>
              <button
                className="btn btn-outline-primary"
                disabled={page === 0}
                onClick={() => fetchInvoices(page - 1)}
              >
                <i className="fas fa-chevron-left"></i> Önceki
              </button>
              <button
                className="btn btn-outline-primary"
                disabled={page >= totalPages - 1}
                onClick={() => fetchInvoices(page + 1)}
              >
                Sonraki <i className="fas fa-chevron-right"></i>
              </button>
              <button
                className="btn btn-outline-primary"
                disabled={page >= totalPages - 1}
                onClick={() => fetchInvoices(totalPages - 1)}
                title="Son sayfa"
              >
                <i className="fas fa-angle-double-right"></i>
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Detail Modal */}
      {selectedInvoice && (
        <div
          className="modal show d-block"
          tabIndex="-1"
          style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}
          onClick={() => setSelectedInvoice(null)}
        >
          <div className="modal-dialog modal-lg modal-dialog-scrollable" onClick={(e) => e.stopPropagation()}>
            <div className="modal-content">
              <div
                className={`modal-header bg-${(STATUS_CONFIG[selectedInvoice.status] || STATUS_CONFIG.DRAFT).color} bg-opacity-10 border-bottom`}
              >
                <div>
                  <h5 className="modal-title d-flex align-items-center gap-2">
                    <i className="fas fa-file-invoice"></i>
                    Fatura Detayı
                    {selectedInvoice.invoiceNumber && (
                      <span className="badge bg-light text-dark border font-monospace">
                        {selectedInvoice.invoiceNumber}
                      </span>
                    )}
                  </h5>
                  <div className="small text-muted mt-1">
                    <i className="fas fa-shopping-cart me-1"></i>Sipariş: {selectedInvoice.orderNumber}
                  </div>
                </div>
                <button type="button" className="btn-close" onClick={() => setSelectedInvoice(null)}></button>
              </div>
              <div className="modal-body">
                {/* Status banner */}
                {(() => {
                  const cfg = STATUS_CONFIG[selectedInvoice.status] || STATUS_CONFIG.DRAFT;
                  return (
                    <div className={`alert alert-${cfg.color} d-flex align-items-center gap-3 mb-4`}>
                      <i className={`${cfg.icon} fa-2x`}></i>
                      <div>
                        <div className="fw-bold">{cfg.label}</div>
                        <div className="small">{cfg.desc}</div>
                      </div>
                    </div>
                  );
                })()}

                <div className="row g-3">
                  <div className="col-md-6">
                    <h6 className="text-muted text-uppercase small mb-3">
                      <i className="fas fa-info-circle me-1"></i>Fatura Bilgileri
                    </h6>

                    <div className="mb-3">
                      <label className="form-label text-muted small mb-0">Fatura Tipi</label>
                      <div>
                        {(() => {
                          const tc = TYPE_CONFIG[selectedInvoice.invoiceType] || TYPE_CONFIG.E_ARSIV;
                          return (
                            <span className={`badge bg-${tc.color}`}>
                              <i className={`${tc.icon} me-1`}></i>
                              {tc.label}
                            </span>
                          );
                        })()}
                      </div>
                    </div>
                    <div className="mb-3">
                      <label className="form-label text-muted small mb-0">Sağlayıcı</label>
                      <div>{selectedInvoice.providerName || '—'}</div>
                    </div>
                    {selectedInvoice.providerInvoiceId && (
                      <div className="mb-3">
                        <label className="form-label text-muted small mb-0">Sağlayıcı Fatura ID</label>
                        <div className="font-monospace small d-flex align-items-center gap-2">
                          {selectedInvoice.providerInvoiceId}
                          <button
                            className="btn btn-sm btn-link p-0"
                            onClick={() => handleCopy(selectedInvoice.providerInvoiceId, 'provid')}
                          >
                            <i
                              className={`fas ${copiedId === 'provid' ? 'fa-check text-success' : 'fa-copy text-muted'}`}
                              style={{ fontSize: 11 }}
                            ></i>
                          </button>
                        </div>
                      </div>
                    )}
                  </div>

                  <div className="col-md-6">
                    <h6 className="text-muted text-uppercase small mb-3">
                      <i className="fas fa-user me-1"></i>Alıcı Bilgileri
                    </h6>

                    <div className="mb-3">
                      <label className="form-label text-muted small mb-0">Ad / Ünvan</label>
                      <div className="fw-bold">{selectedInvoice.recipientName || '—'}</div>
                    </div>
                    {selectedInvoice.recipientTaxId && (
                      <div className="mb-3">
                        <label className="form-label text-muted small mb-0">Vergi No / TC</label>
                        <div className="d-flex align-items-center gap-2">
                          <span className="font-monospace">{selectedInvoice.recipientTaxId}</span>
                          <button
                            className="btn btn-sm btn-link p-0"
                            onClick={() => handleCopy(selectedInvoice.recipientTaxId, 'taxid')}
                          >
                            <i
                              className={`fas ${copiedId === 'taxid' ? 'fa-check text-success' : 'fa-copy text-muted'}`}
                              style={{ fontSize: 11 }}
                            ></i>
                          </button>
                        </div>
                      </div>
                    )}
                    {selectedInvoice.recipientTaxOffice && (
                      <div className="mb-3">
                        <label className="form-label text-muted small mb-0">Vergi Dairesi</label>
                        <div>{selectedInvoice.recipientTaxOffice}</div>
                      </div>
                    )}
                    <div className="mb-3">
                      <label className="form-label text-muted small mb-0">Adres</label>
                      <div className="small">{selectedInvoice.recipientAddress || '—'}</div>
                    </div>
                  </div>
                </div>

                <hr />

                <h6 className="text-muted text-uppercase small mb-3">
                  <i className="fas fa-calculator me-1"></i>Tutarlar
                </h6>
                <div className="bg-light rounded p-3">
                  <div className="d-flex justify-content-between mb-2">
                    <span className="text-muted">Ara Toplam:</span>
                    <span>{formatPrice(selectedInvoice.subtotal)}</span>
                  </div>
                  <div className="d-flex justify-content-between mb-2">
                    <span className="text-muted">KDV:</span>
                    <span>{formatPrice(selectedInvoice.vatAmount)}</span>
                  </div>
                  <hr className="my-2" />
                  <div className="d-flex justify-content-between fw-bold fs-5">
                    <span>Toplam:</span>
                    <span className="text-primary">{formatPrice(selectedInvoice.totalAmount)}</span>
                  </div>
                </div>

                {/* Error message */}
                {selectedInvoice.errorMessage && (
                  <div className="alert alert-danger mt-3">
                    <div className="d-flex align-items-start gap-2">
                      <i className="fas fa-exclamation-triangle mt-1"></i>
                      <div>
                        <strong>Hata Mesajı</strong>
                        <div className="small mt-1 font-monospace">{selectedInvoice.errorMessage}</div>
                      </div>
                    </div>
                  </div>
                )}

                {/* Timeline */}
                <hr />
                <h6 className="text-muted text-uppercase small mb-3">
                  <i className="fas fa-history me-1"></i>Zaman Çizelgesi
                </h6>
                <div className="timeline">
                  <div className="d-flex gap-3 mb-2">
                    <div
                      className="rounded-circle bg-secondary bg-opacity-25 d-flex align-items-center justify-content-center"
                      style={{ width: 32, height: 32, minWidth: 32 }}
                    >
                      <i className="fas fa-plus text-secondary" style={{ fontSize: 12 }}></i>
                    </div>
                    <div>
                      <div className="small fw-semibold">Oluşturuldu</div>
                      <div className="small text-muted">{formatDate(selectedInvoice.createdAt)}</div>
                    </div>
                  </div>
                  {selectedInvoice.issuedAt && (
                    <div className="d-flex gap-3 mb-2">
                      <div
                        className="rounded-circle bg-success bg-opacity-25 d-flex align-items-center justify-content-center"
                        style={{ width: 32, height: 32, minWidth: 32 }}
                      >
                        <i className="fas fa-check text-success" style={{ fontSize: 12 }}></i>
                      </div>
                      <div>
                        <div className="small fw-semibold">Düzenlendi</div>
                        <div className="small text-muted">{formatDate(selectedInvoice.issuedAt)}</div>
                      </div>
                    </div>
                  )}
                  {selectedInvoice.updatedAt && selectedInvoice.updatedAt !== selectedInvoice.createdAt && (
                    <div className="d-flex gap-3">
                      <div
                        className="rounded-circle bg-info bg-opacity-25 d-flex align-items-center justify-content-center"
                        style={{ width: 32, height: 32, minWidth: 32 }}
                      >
                        <i className="fas fa-pen text-info" style={{ fontSize: 12 }}></i>
                      </div>
                      <div>
                        <div className="small fw-semibold">Son Güncelleme</div>
                        <div className="small text-muted">{formatDate(selectedInvoice.updatedAt)}</div>
                      </div>
                    </div>
                  )}
                </div>
              </div>
              <div className="modal-footer">
                {selectedInvoice.hasPdf && (
                  <button
                    className="btn btn-primary"
                    onClick={() => handleDownloadPdf(selectedInvoice.id, selectedInvoice.invoiceNumber)}
                  >
                    <i className="fas fa-download me-1"></i>PDF İndir
                  </button>
                )}
                {(selectedInvoice.status === 'ERROR' ||
                  selectedInvoice.status === 'DRAFT' ||
                  selectedInvoice.status === 'REJECTED') && (
                  <button
                    className="btn btn-warning"
                    disabled={actionLoading}
                    onClick={() =>
                      setConfirmAction({
                        type: 'regenerate',
                        id: selectedInvoice.id,
                        title: 'Yeniden Oluştur',
                        message: `${selectedInvoice.invoiceNumber || 'Bu'} faturayı yeniden oluşturmak istiyor musunuz?`,
                        btnClass: 'btn-warning',
                        btnLabel: 'Yeniden Oluştur',
                      })
                    }
                  >
                    <i className="fas fa-redo me-1"></i>Yeniden Oluştur
                  </button>
                )}
                {selectedInvoice.status !== 'CANCELLED' && (
                  <button
                    className="btn btn-outline-danger"
                    disabled={actionLoading}
                    onClick={() =>
                      setConfirmAction({
                        type: 'cancel',
                        id: selectedInvoice.id,
                        title: 'Faturayı İptal Et',
                        message: `${selectedInvoice.invoiceNumber || 'Bu'} faturayı iptal etmek istediğinize emin misiniz? Bu işlem geri alınamaz.`,
                        btnClass: 'btn-danger',
                        btnLabel: 'İptal Et',
                      })
                    }
                  >
                    <i className="fas fa-ban me-1"></i>İptal Et
                  </button>
                )}
                <button className="btn btn-secondary" onClick={() => setSelectedInvoice(null)}>
                  Kapat
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Confirm Dialog */}
      {confirmAction && (
        <div
          className="modal show d-block"
          tabIndex="-1"
          style={{ backgroundColor: 'rgba(0,0,0,0.6)', zIndex: 1060 }}
          onClick={() => setConfirmAction(null)}
        >
          <div className="modal-dialog modal-dialog-centered modal-sm" onClick={(e) => e.stopPropagation()}>
            <div className="modal-content">
              <div className="modal-body text-center py-4">
                <div
                  className={`rounded-circle bg-${confirmAction.btnClass.replace('btn-', '')} bg-opacity-10 d-inline-flex align-items-center justify-content-center mb-3`}
                  style={{ width: 60, height: 60 }}
                >
                  <i
                    className={`fas fa-exclamation text-${confirmAction.btnClass.replace('btn-', '')} fa-2x`}
                  ></i>
                </div>
                <h5>{confirmAction.title}</h5>
                <p className="text-muted small mb-0">{confirmAction.message}</p>
              </div>
              <div className="modal-footer border-0 justify-content-center pt-0">
                <button
                  className="btn btn-outline-secondary"
                  onClick={() => setConfirmAction(null)}
                  disabled={actionLoading}
                >
                  Vazgeç
                </button>
                <button
                  className={`btn ${confirmAction.btnClass}`}
                  disabled={actionLoading}
                  onClick={() =>
                    confirmAction.type === 'regenerate'
                      ? handleRegenerate(confirmAction.id)
                      : handleCancel(confirmAction.id)
                  }
                >
                  {actionLoading ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-1"></span>İşleniyor...
                    </>
                  ) : (
                    confirmAction.btnLabel
                  )}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Manual "Create invoice for order" — modal */}
      {createOpen && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          {/* No backdrop dismissal: a stray click outside used to discard everything typed here. */}
          <div className="modal-dialog modal-lg">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  <i className="fas fa-plus-circle text-primary me-2"></i>
                  Sipariş İçin Fatura Oluştur
                </h5>
                <button type="button" className="btn-close" onClick={() => setCreateOpen(false)}></button>
              </div>
              <div className="modal-body">
                <div className="alert alert-info small py-2 mb-3">
                  <i className="fas fa-info-circle me-1"></i>
                  Sipariş numarası veya müşteri adıyla arayın, ilgili siparişin yanındaki butonla fatura
                  oluşturun. Tip (e-Arşiv / e-Fatura) müşterinin vergi bilgilerine göre otomatik belirlenir.
                </div>

                <div className="input-group input-group-sm mb-3">
                  <span className="input-group-text">
                    <i className="fas fa-search"></i>
                  </span>
                  <input
                    type="text"
                    className="form-control"
                    placeholder="Sipariş no (örn: ORD-2026-001) veya müşteri adı..."
                    value={createSearch}
                    onChange={(e) => setCreateSearch(e.target.value)}
                    autoFocus
                  />
                  {createSearchLoading && (
                    <span className="input-group-text">
                      <span className="spinner-border spinner-border-sm"></span>
                    </span>
                  )}
                </div>

                {!createSearch.trim() ? (
                  <div className="text-center text-muted py-4">
                    <i className="fas fa-keyboard fa-2x mb-2 opacity-25"></i>
                    <p className="small mb-0">Aramaya başlamak için yukarıya yazın.</p>
                  </div>
                ) : createSearchResults.length === 0 && !createSearchLoading ? (
                  <div className="text-center text-muted py-4">
                    <i className="fas fa-search fa-2x mb-2 opacity-25"></i>
                    <p className="small mb-0">Eşleşen sipariş bulunamadı.</p>
                  </div>
                ) : (
                  <div className="list-group" style={{ maxHeight: 420, overflowY: 'auto' }}>
                    {createSearchResults.map((o) => {
                      const busy = createBusyOrderId === o.id;
                      const hasInvoice = !!o.invoiceNumber;
                      return (
                        <div
                          key={o.id}
                          className="list-group-item d-flex justify-content-between align-items-center gap-3"
                        >
                          <div className="flex-grow-1 min-w-0">
                            <div className="d-flex flex-wrap align-items-center gap-2 mb-1">
                              <code className="text-primary">{o.orderNumber}</code>
                              <span className="badge bg-light text-dark border">{o.status}</span>
                              {hasInvoice && (
                                <span className="badge bg-success bg-opacity-25 text-success-emphasis">
                                  <i className="fas fa-check me-1"></i>Fatura: {o.invoiceNumber}
                                </span>
                              )}
                            </div>
                            <div className="small text-muted text-truncate">
                              <i className="fas fa-user me-1"></i>
                              {o.customerName || 'İsimsiz'}
                              {' · '}
                              <i className="fas fa-calendar me-1"></i>
                              {formatDate(o.createdAt)}
                              {' · '}
                              <strong className="text-dark">{formatPrice(o.grandTotal)}</strong>
                            </div>
                          </div>
                          <button
                            className={`btn btn-sm ${hasInvoice ? 'btn-outline-secondary' : 'btn-primary'} text-nowrap`}
                            disabled={busy}
                            onClick={() => handleCreateForOrder(o.id, o.orderNumber)}
                            title={
                              hasInvoice
                                ? 'Bu siparişin zaten bir faturası var — yeni deneme için tıklayın'
                                : 'Bu sipariş için fatura oluştur'
                            }
                          >
                            {busy ? (
                              <>
                                <span className="spinner-border spinner-border-sm me-1"></span>İşleniyor…
                              </>
                            ) : hasInvoice ? (
                              <>
                                <i className="fas fa-redo me-1"></i>Yeniden Dene
                              </>
                            ) : (
                              <>
                                <i className="fas fa-file-invoice me-1"></i>Fatura Oluştur
                              </>
                            )}
                          </button>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setCreateOpen(false)}>
                  Kapat
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Toast */}
      {toast.show && (
        <div className="position-fixed bottom-0 end-0 p-3" style={{ zIndex: 9999 }}>
          <div
            className={`toast show align-items-center text-white bg-${toast.type} border-0`}
            style={{ minWidth: 280 }}
          >
            <div className="d-flex">
              <div className="toast-body">
                <i
                  className={`fas ${toast.type === 'success' ? 'fa-check-circle' : toast.type === 'danger' ? 'fa-times-circle' : toast.type === 'warning' ? 'fa-exclamation-triangle' : 'fa-info-circle'} me-2`}
                ></i>
                {toast.message}
              </div>
              <button
                type="button"
                className="btn-close btn-close-white me-2 m-auto"
                onClick={() => setToast({ show: false, message: '', type: 'success' })}
              ></button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
