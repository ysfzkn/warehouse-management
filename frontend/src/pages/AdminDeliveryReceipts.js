import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';

/**
 * Delivery receipt archive.
 *
 * The operational question this screen answers is "which signed copies have not come
 * back yet" — the receipts that were printed, handed to a driver and never returned.
 * That filter is therefore the default entry point from the summary cards, not an
 * option buried in a dropdown.
 */

const STATUS_META = {
  ISSUED: { label: 'Düzenlendi', cls: 'bg-primary-subtle text-primary-emphasis' },
  DELIVERED: { label: 'Teslim Edildi', cls: 'bg-success-subtle text-success-emphasis' },
  CANCELLED: { label: 'İptal', cls: 'bg-danger-subtle text-danger-emphasis' },
};

const formatDateTime = (value) => {
  if (!value) return '—';
  try {
    return new Date(value).toLocaleString('tr-TR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return value;
  }
};

const EMPTY_FILTERS = { search: '', status: '', hasSignedCopy: '', from: '', to: '' };

export default function AdminDeliveryReceipts() {
  const [receipts, setReceipts] = useState([]);
  const [stats, setStats] = useState(null);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [downloading, setDownloading] = useState(null);

  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [applied, setApplied] = useState(EMPTY_FILTERS);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const params = { page, size: pageSize };
      if (applied.search.trim()) params.search = applied.search.trim();
      if (applied.status) params.status = applied.status;
      if (applied.hasSignedCopy !== '') params.hasSignedCopy = applied.hasSignedCopy;
      if (applied.from) params.from = `${applied.from}T00:00:00`;
      if (applied.to) params.to = `${applied.to}T23:59:59`;

      const res = await axios.get('/api/admin/delivery-receipts', { params });
      setReceipts(res.data?.content || []);
      setTotalPages(res.data?.totalPages || 0);
      setTotalElements(res.data?.totalElements || 0);
    } catch (e) {
      setError(e?.response?.data?.message || 'Makbuzlar yüklenemedi.');
      setReceipts([]);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, applied]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    axios
      .get('/api/admin/delivery-receipts/stats')
      .then((res) => setStats(res.data))
      .catch(() => setStats(null));
  }, [applied]);

  const applyFilters = (event) => {
    event.preventDefault();
    setPage(0);
    setApplied(filters);
  };

  const resetFilters = () => {
    setFilters(EMPTY_FILTERS);
    setApplied(EMPTY_FILTERS);
    setPage(0);
  };

  /** Jump straight to the pending pile — the reason this screen gets opened. */
  const showAwaitingSignedCopy = () => {
    const next = { ...EMPTY_FILTERS, hasSignedCopy: 'false' };
    setFilters(next);
    setApplied(next);
    setPage(0);
  };

  const downloadPdf = async (receipt) => {
    setDownloading(receipt.id);
    try {
      const res = await axios.get(`/api/admin/stock-transfers/${receipt.transferId}/receipt/pdf`, {
        responseType: 'blob',
      });
      const url = window.URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }));
      const link = document.createElement('a');
      link.href = url;
      link.download = `makbuz-${receipt.receiptNo}.pdf`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch {
      setError('Makbuz indirilemedi.');
    } finally {
      setDownloading(null);
    }
  };

  const hasActiveFilter = useMemo(() => JSON.stringify(applied) !== JSON.stringify(EMPTY_FILTERS), [applied]);

  return (
    <div className="container-fluid py-3">
      <div className="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
        <h4 className="mb-0">
          <i className="fas fa-file-invoice me-2 text-primary"></i>
          Teslimat Makbuzları
        </h4>
        <Link to="/stock" className="btn btn-sm btn-outline-secondary">
          <i className="fas fa-arrow-left me-1"></i>
          Sevkiyatlara Dön
        </Link>
      </div>

      {stats && (
        <div className="row g-2 mb-3">
          {[
            { key: 'total', label: 'Toplam Makbuz', icon: 'fa-file-lines', cls: 'text-secondary' },
            { key: 'issued', label: 'Yolda / Düzenlendi', icon: 'fa-truck', cls: 'text-primary' },
            { key: 'delivered', label: 'Teslim Edildi', icon: 'fa-circle-check', cls: 'text-success' },
          ].map((card) => (
            <div className="col-6 col-lg-3" key={card.key}>
              <div className="card border-0 shadow-sm h-100">
                <div className="card-body py-3">
                  <div className="small text-muted">{card.label}</div>
                  <div className="h4 mb-0">
                    <i className={`fas ${card.icon} me-2 ${card.cls}`}></i>
                    {stats[card.key] ?? 0}
                  </div>
                </div>
              </div>
            </div>
          ))}
          <div className="col-6 col-lg-3">
            <button
              type="button"
              className="card border-0 shadow-sm h-100 w-100 text-start"
              onClick={showAwaitingSignedCopy}
              title="Sadece imzalı nüshası gelmemiş makbuzları listele"
            >
              <div className="card-body py-3">
                <div className="small text-muted">İmzalı Nüsha Bekleyen</div>
                <div className="h4 mb-0 text-warning">
                  <i className="fas fa-hourglass-half me-2"></i>
                  {stats.awaitingSignedCopy ?? 0}
                </div>
              </div>
            </button>
          </div>
        </div>
      )}

      <form className="card border-0 shadow-sm mb-3" onSubmit={applyFilters}>
        <div className="card-body py-3">
          <div className="row g-2 align-items-end">
            <div className="col-md-4">
              <label className="form-label small mb-1">Ara</label>
              <input
                type="text"
                className="form-control form-control-sm"
                placeholder="Makbuz no, müşteri, şoför, plaka, sipariş no"
                value={filters.search}
                onChange={(e) => setFilters((p) => ({ ...p, search: e.target.value }))}
              />
            </div>
            <div className="col-md-2">
              <label className="form-label small mb-1">Durum</label>
              <select
                className="form-select form-select-sm"
                value={filters.status}
                onChange={(e) => setFilters((p) => ({ ...p, status: e.target.value }))}
              >
                <option value="">Tümü</option>
                <option value="ISSUED">Düzenlendi</option>
                <option value="DELIVERED">Teslim Edildi</option>
                <option value="CANCELLED">İptal</option>
              </select>
            </div>
            <div className="col-md-2">
              <label className="form-label small mb-1">İmzalı Nüsha</label>
              <select
                className="form-select form-select-sm"
                value={filters.hasSignedCopy}
                onChange={(e) => setFilters((p) => ({ ...p, hasSignedCopy: e.target.value }))}
              >
                <option value="">Tümü</option>
                <option value="true">Yüklendi</option>
                <option value="false">Bekleniyor</option>
              </select>
            </div>
            <div className="col-md-2">
              <label className="form-label small mb-1">Başlangıç</label>
              <input
                type="date"
                className="form-control form-control-sm"
                value={filters.from}
                onChange={(e) => setFilters((p) => ({ ...p, from: e.target.value }))}
              />
            </div>
            <div className="col-md-2">
              <label className="form-label small mb-1">Bitiş</label>
              <input
                type="date"
                className="form-control form-control-sm"
                value={filters.to}
                onChange={(e) => setFilters((p) => ({ ...p, to: e.target.value }))}
              />
            </div>
          </div>
          <div className="d-flex gap-2 mt-3">
            <button type="submit" className="btn btn-sm btn-primary">
              <i className="fas fa-magnifying-glass me-1"></i>
              Filtrele
            </button>
            {hasActiveFilter && (
              <button type="button" className="btn btn-sm btn-outline-secondary" onClick={resetFilters}>
                Temizle
              </button>
            )}
          </div>
        </div>
      </form>

      {error && (
        <div className="alert alert-danger py-2 px-3 small">
          <i className="fas fa-triangle-exclamation me-1"></i>
          {error}
        </div>
      )}

      <div className="card border-0 shadow-sm">
        <div className="table-responsive">
          <table className="table table-hover align-middle mb-0">
            <thead className="table-light">
              <tr>
                <th className="small">Makbuz No</th>
                <th className="small">Müşteri / Alıcı</th>
                <th className="small d-none d-lg-table-cell">Şoför / Plaka</th>
                <th className="small">Teslim Alan</th>
                <th className="small d-none d-md-table-cell">Teslim Tarihi</th>
                <th className="small text-center">Durum</th>
                <th className="small text-center">İmzalı Nüsha</th>
                <th className="small text-center" style={{ width: 150 }}>
                  İşlemler
                </th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr>
                  <td colSpan={8} className="text-center text-muted py-4">
                    <i className="fas fa-spinner fa-spin me-2"></i>
                    Yükleniyor…
                  </td>
                </tr>
              )}
              {!loading && receipts.length === 0 && (
                <tr>
                  <td colSpan={8} className="text-center text-muted py-4">
                    Kayıt bulunamadı.
                  </td>
                </tr>
              )}
              {!loading &&
                receipts.map((r) => {
                  const status = STATUS_META[r.status] || STATUS_META.ISSUED;
                  return (
                    <tr key={r.id}>
                      <td>
                        <div className="fw-semibold small">{r.receiptNo}</div>
                        <div className="text-muted" style={{ fontSize: '0.72rem' }}>
                          {formatDateTime(r.issuedAt)}
                          {r.revision > 1 && ` · ${r.revision}. basım`}
                        </div>
                      </td>
                      <td>
                        <div className="small fw-semibold">{r.customerFullName || '—'}</div>
                        <div className="text-muted" style={{ fontSize: '0.72rem' }}>
                          {r.customerPhone || ''}
                          {r.orderNumber ? ` · ${r.orderNumber}` : ''}
                        </div>
                      </td>
                      <td className="d-none d-lg-table-cell">
                        <div className="small">{r.driverName || '—'}</div>
                        <div className="text-muted" style={{ fontSize: '0.72rem' }}>
                          {r.vehiclePlate || ''}
                        </div>
                      </td>
                      <td className="small">{r.receivedByName || '—'}</td>
                      <td className="small d-none d-md-table-cell">{formatDateTime(r.deliveredAt)}</td>
                      <td className="text-center">
                        <span className={`badge rounded-pill ${status.cls}`}>{status.label}</span>
                      </td>
                      <td className="text-center">
                        {r.signedCopyOnFile ? (
                          <span className="badge rounded-pill bg-success-subtle text-success-emphasis">
                            <i className="fas fa-paperclip me-1"></i>
                            {r.attachments?.length || 1}
                          </span>
                        ) : (
                          <span className="badge rounded-pill bg-warning-subtle text-warning-emphasis">
                            Bekleniyor
                          </span>
                        )}
                      </td>
                      <td className="text-center">
                        <div className="d-flex gap-1 justify-content-center">
                          <button
                            type="button"
                            className="btn btn-sm btn-outline-primary"
                            disabled={downloading === r.id}
                            onClick={() => downloadPdf(r)}
                            title="PDF indir"
                          >
                            <i
                              className={`fas ${downloading === r.id ? 'fa-spinner fa-spin' : 'fa-file-pdf'}`}
                            ></i>
                          </button>
                          <Link
                            to={`/stock?highlightTransfer=${r.transferId}`}
                            className="btn btn-sm btn-outline-secondary"
                            title="Sevkiyat detayını aç"
                          >
                            <i className="fas fa-up-right-from-square"></i>
                          </Link>
                        </div>
                      </td>
                    </tr>
                  );
                })}
            </tbody>
          </table>
        </div>

        <div className="card-footer bg-white d-flex justify-content-between align-items-center flex-wrap gap-2">
          <div className="small text-muted">
            Toplam {totalElements} kayıt
            {totalPages > 1 && ` · Sayfa ${page + 1}/${totalPages}`}
          </div>
          <div className="d-flex align-items-center gap-2">
            <select
              className="form-select form-select-sm"
              style={{ width: 90 }}
              value={pageSize}
              onChange={(e) => {
                setPageSize(Number(e.target.value));
                setPage(0);
              }}
            >
              {[10, 20, 50, 100].map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </select>
            <button
              type="button"
              className="btn btn-sm btn-outline-secondary"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              <i className="fas fa-chevron-left"></i>
            </button>
            <button
              type="button"
              className="btn btn-sm btn-outline-secondary"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              <i className="fas fa-chevron-right"></i>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
