import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { useAdminToast } from '../components/AdminToast';
import PaginationControls from '../components/PaginationControls';
import confirmDialog, { promptDialog } from '../utils/confirmDialog';

/** Store-host product URL from the current admin host (admin.x → x). */
const storeProductUrl = (slug) => {
  try {
    const { protocol, hostname, port } = window.location;
    const storeHost = hostname.replace(/^(admin|wms)\./, '');
    return `${protocol}//${storeHost}${port ? ':' + port : ''}/urun/${slug}`;
  } catch {
    return `/urun/${slug}`;
  }
};

const STATUS_META = {
  PENDING: { label: 'Beklemede', color: 'warning', icon: 'fa-hourglass-half' },
  APPROVED: { label: 'Onaylandı', color: 'info', icon: 'fa-check' },
  REJECTED: { label: 'Reddedildi', color: 'danger', icon: 'fa-times' },
  CARGO_WAITING: { label: 'Kargo Bekleniyor', color: 'secondary', icon: 'fa-truck' },
  RECEIVED: { label: 'Teslim Alındı', color: 'primary', icon: 'fa-box-open' },
  REFUND_PROCESSING: { label: 'İade İşleniyor', color: 'secondary', icon: 'fa-sync' },
  REFUNDED: { label: 'İade Edildi', color: 'success', icon: 'fa-money-bill-wave' },
};

const REASON_LABELS = {
  DEFECTIVE: 'Arızalı / Kusurlu',
  WRONG_PRODUCT: 'Yanlış ürün',
  NOT_AS_DESCRIBED: 'Açıklamaya uymuyor',
  CHANGED_MIND: 'Vazgeçti',
  DAMAGED_IN_SHIPPING: 'Kargoda hasar',
  OTHER: 'Diğer',
};

const fmtTRY = (v) =>
  new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(Number(v) || 0);
const fmtDate = (s) =>
  s
    ? new Date(s).toLocaleString('tr-TR', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '—';

const StatusBadge = ({ status }) => {
  const m = STATUS_META[status] || { label: status, color: 'secondary', icon: 'fa-circle' };
  return (
    <span className={`badge bg-${m.color}`}>
      <i className={`fas ${m.icon} me-1`} />
      {m.label}
    </span>
  );
};

export default function AdminReturns() {
  const toast = useAdminToast();
  const navigate = useNavigate();
  const [photoPreview, setPhotoPreview] = useState(null);
  const [returns, setReturns] = useState([]);
  const [pageInfo, setPageInfo] = useState({ number: 0, totalPages: 0, totalElements: 0 });
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState('');
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState(null);
  const [counts, setCounts] = useState({});
  const [acting, setActing] = useState(false);

  const fetchReturns = useCallback(() => {
    setLoading(true);
    const params = { page, size: 20 };
    if (statusFilter) params.status = statusFilter;
    axios
      .get('/api/admin/returns', { params })
      .then((r) => {
        setReturns(r.data?.content || []);
        setPageInfo({
          number: r.data?.page ?? 0,
          totalPages: r.data?.totalPages ?? 0,
          totalElements: r.data?.totalElements ?? 0,
        });
      })
      .catch(() => toast.error('İade talepleri yüklenemedi.'))
      .finally(() => setLoading(false));
    // `toast` from useAdminToast() is a fresh object each render; including it would
    // recreate fetchReturns every render and loop the effect. Only page/status matter.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, statusFilter]);

  const fetchCounts = useCallback(() => {
    axios
      .get('/api/admin/returns/counts')
      .then((r) => setCounts(r.data || {}))
      .catch(() => {});
  }, []);

  useEffect(() => {
    fetchReturns();
  }, [fetchReturns]);
  useEffect(() => {
    fetchCounts();
  }, [fetchCounts]);

  const refreshAll = () => {
    fetchReturns();
    fetchCounts();
  };

  const openDetail = (id) => {
    axios
      .get(`/api/admin/returns/${id}`)
      .then((r) => setSelected(r.data))
      .catch(() => toast.error('Detay yüklenemedi.'));
  };

  const doAction = async (rr, action) => {
    let body = {};
    if (action === 'reject') {
      const note = await promptDialog({
        title: 'İade Reddedilsin mi?',
        message: `${rr.returnNumber} numaralı iade talebi reddedilecek.`,
        inputLabel: 'Red sebebi (müşteriye iletilecek)',
        placeholder: 'Örn: İade süresi dolmuş, ürün kullanılmış...',
        confirmText: 'Reddet',
        variant: 'danger',
      });
      if (!note) return;
      body = { adminNote: note };
    } else if (action === 'refund') {
      const amount = await promptDialog({
        title: 'İade Tutarı Ödensin mi?',
        message: `${rr.returnNumber} için iade tutarı müşterinin ödeme yöntemine geri ödenecek.`,
        inputLabel: 'İade tutarı (TL)',
        placeholder: String(rr.refundAmount || ''),
        confirmText: 'İadeyi Başlat',
        variant: 'primary',
        icon: 'fa-money-bill-wave',
      });
      if (!amount) return;
      body = { amount: amount.replace(',', '.') };
    } else if (action === 'reply') {
      const message = await promptDialog({
        title: 'Müşteriye Yanıt',
        message: `${rr.returnNumber} için müşteriye bir mesaj gönderin (e-posta ile iletilir).`,
        inputLabel: 'Mesaj',
        placeholder: 'Örn: İadenizi aldık, kargo etiketini e-postanıza gönderdik...',
        confirmText: 'Gönder',
        variant: 'primary',
        icon: 'fa-reply',
      });
      if (!message) return;
      body = { message };
    } else {
      const labels = {
        approve: 'onaylanacak',
        receive: 'teslim alındı olarak işaretlenecek (stok geri eklenir)',
      };
      const ok = await confirmDialog({
        title: 'Onaylıyor musunuz?',
        message: `${rr.returnNumber} numaralı iade ${labels[action]}.`,
        confirmText: 'Evet',
        variant: 'primary',
        icon: 'fa-undo',
      });
      if (!ok) return;
    }
    setActing(true);
    try {
      const r = await axios.put(`/api/admin/returns/${rr.id}/${action}`, body);
      setSelected(r.data);
      refreshAll();
      toast.success('İşlem tamamlandı.');
    } catch (e) {
      toast.error(e?.response?.data?.message || 'İşlem başarısız.');
    } finally {
      setActing(false);
    }
  };

  const actionsFor = (rr) => {
    const a = [];
    if (rr.status === 'PENDING') {
      a.push({ key: 'approve', label: 'Onayla', cls: 'btn-info' });
      a.push({ key: 'reject', label: 'Reddet', cls: 'btn-outline-danger' });
    }
    if (rr.status === 'APPROVED' || rr.status === 'CARGO_WAITING') {
      a.push({ key: 'receive', label: 'Teslim Alındı', cls: 'btn-primary' });
    }
    if (rr.status === 'RECEIVED' || rr.status === 'REFUND_PROCESSING') {
      a.push({ key: 'refund', label: 'İadeyi Öde', cls: 'btn-success' });
    }
    return a;
  };

  return (
    <div className="container-fluid py-3">
      <div className="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
        <h2 className="mb-0">
          <i className="fas fa-undo me-2 text-warning" />
          İade Talepleri
        </h2>
        <div className="d-flex align-items-center gap-2">
          <select
            className="form-select form-select-sm"
            style={{ width: 'auto' }}
            value={statusFilter}
            onChange={(e) => {
              setPage(0);
              setStatusFilter(e.target.value);
            }}
          >
            <option value="">Tüm Durumlar</option>
            {Object.entries(STATUS_META).map(([k, v]) => (
              <option key={k} value={k}>
                {v.label}
              </option>
            ))}
          </select>
          <button className="btn btn-sm btn-outline-secondary" onClick={refreshAll}>
            <i className="fas fa-sync me-1" />
            Yenile
          </button>
        </div>
      </div>

      {/* Status summary chips */}
      <div className="d-flex flex-wrap gap-2 mb-3">
        {Object.entries(STATUS_META).map(([k, v]) =>
          counts[k] > 0 ? (
            <button
              key={k}
              className={`btn btn-sm ${statusFilter === k ? `btn-${v.color}` : `btn-outline-${v.color}`}`}
              onClick={() => {
                setPage(0);
                setStatusFilter(statusFilter === k ? '' : k);
              }}
            >
              {v.label}: <strong>{counts[k]}</strong>
            </button>
          ) : null
        )}
      </div>

      {loading ? (
        <div className="text-muted py-5 text-center">
          <span className="spinner-border spinner-border-sm me-2" />
          Yükleniyor…
        </div>
      ) : returns.length === 0 ? (
        <div className="text-muted py-5 text-center">
          <i className="fas fa-inbox fa-2x mb-2 d-block opacity-50" />
          İade talebi bulunamadı.
        </div>
      ) : (
        <div className="table-responsive">
          <table className="table table-hover align-middle">
            <thead className="table-light">
              <tr>
                <th>İade No</th>
                <th>Sipariş</th>
                <th>Müşteri</th>
                <th>Sebep</th>
                <th>Tutar</th>
                <th>Durum</th>
                <th>Tarih</th>
                <th className="text-end">İşlem</th>
              </tr>
            </thead>
            <tbody>
              {returns.map((rr) => (
                <tr key={rr.id} style={{ cursor: 'pointer' }} onClick={() => openDetail(rr.id)}>
                  <td className="fw-semibold">{rr.returnNumber}</td>
                  <td>{rr.orderNumber || '—'}</td>
                  <td>{rr.customerName || '—'}</td>
                  <td>
                    <span className="small">{REASON_LABELS[rr.reason] || rr.reason}</span>
                  </td>
                  <td>{fmtTRY(rr.refundAmount)}</td>
                  <td>
                    <StatusBadge status={rr.status} />
                  </td>
                  <td className="small text-muted">{fmtDate(rr.createdAt)}</td>
                  <td className="text-end" onClick={(e) => e.stopPropagation()}>
                    <div className="d-flex gap-1 justify-content-end">
                      {actionsFor(rr).map((act) => (
                        <button
                          key={act.key}
                          className={`btn btn-sm ${act.cls}`}
                          disabled={acting}
                          onClick={() => doAction(rr, act.key)}
                        >
                          {act.label}
                        </button>
                      ))}
                      <button className="btn btn-sm btn-outline-secondary" onClick={() => openDetail(rr.id)}>
                        Detay
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {pageInfo.totalPages > 1 && (
        <PaginationControls page={pageInfo.number} totalPages={pageInfo.totalPages} onPageChange={setPage} />
      )}

      {/* Detail modal */}
      {selected && (
        <div
          className="position-fixed top-0 start-0 w-100 h-100 d-flex align-items-center justify-content-center"
          style={{ background: 'rgba(0,0,0,0.5)', zIndex: 1080 }}
          onClick={() => setSelected(null)}
        >
          <div
            className="bg-white rounded-3 shadow-lg p-4 m-3"
            style={{ maxWidth: 640, width: '100%', maxHeight: '90vh', overflowY: 'auto' }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="d-flex justify-content-between align-items-start mb-3">
              <div>
                <h5 className="fw-bold mb-1">{selected.returnNumber}</h5>
                <StatusBadge status={selected.status} />
              </div>
              <button className="btn-close" onClick={() => setSelected(null)} />
            </div>

            <dl className="row small mb-3">
              <dt className="col-sm-4 text-muted">Sipariş</dt>
              <dd className="col-sm-8">
                {selected.orderNumber ? (
                  <button
                    className="btn btn-link btn-sm p-0 align-baseline"
                    onClick={() => navigate(`/admin/orders?orderNumber=${selected.orderNumber}`)}
                    title="Siparişi görüntüle"
                  >
                    {selected.orderNumber}{' '}
                    <i className="fas fa-arrow-up-right-from-square ms-1" style={{ fontSize: 10 }} />
                  </button>
                ) : (
                  '—'
                )}
              </dd>
              <dt className="col-sm-4 text-muted">Müşteri</dt>
              <dd className="col-sm-8">
                {selected.customerId ? (
                  <button
                    className="btn btn-link btn-sm p-0 align-baseline text-start"
                    onClick={() => navigate(`/admin/customers?customerId=${selected.customerId}`)}
                    title="Müşteri detayını görüntüle"
                  >
                    {selected.customerName} ({selected.customerEmail})
                  </button>
                ) : (
                  <>
                    {selected.customerName} ({selected.customerEmail})
                  </>
                )}
              </dd>
              <dt className="col-sm-4 text-muted">Sebep</dt>
              <dd className="col-sm-8">{REASON_LABELS[selected.reason] || selected.reason}</dd>
              <dt className="col-sm-4 text-muted">Tarih</dt>
              <dd className="col-sm-8">{fmtDate(selected.createdAt)}</dd>
              <dt className="col-sm-4 text-muted">İade Tutarı</dt>
              <dd className="col-sm-8 fw-semibold">{fmtTRY(selected.refundAmount)}</dd>
              {selected.customerNote && (
                <>
                  <dt className="col-sm-4 text-muted">Müşteri Notu</dt>
                  <dd className="col-sm-8">{selected.customerNote}</dd>
                </>
              )}
              {selected.adminNote && (
                <>
                  <dt className="col-sm-4 text-muted">Mağaza Yanıtı</dt>
                  <dd className="col-sm-8">{selected.adminNote}</dd>
                </>
              )}
            </dl>

            <h6 className="fw-bold small text-uppercase text-muted">İade Edilen Ürünler</h6>
            <ul className="list-group mb-3">
              {(selected.items || []).map((it) => (
                <li key={it.id} className="list-group-item d-flex align-items-center gap-2">
                  <div
                    className="border rounded flex-shrink-0 d-flex align-items-center justify-content-center"
                    style={{ width: 44, height: 44, overflow: 'hidden', background: '#f8f9fa' }}
                  >
                    {it.productImageUrl ? (
                      <img
                        src={it.productImageUrl}
                        alt={it.productName}
                        style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                      />
                    ) : (
                      <i className="fas fa-box text-muted" />
                    )}
                  </div>
                  <div className="flex-grow-1 min-w-0">
                    {it.productSlug ? (
                      <a
                        href={storeProductUrl(it.productSlug)}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-decoration-none"
                      >
                        {it.productName || `#${it.productId}`}
                      </a>
                    ) : (
                      <span>{it.productName || `#${it.productId}`}</span>
                    )}
                  </div>
                  <span className="badge bg-secondary">×{it.quantity}</span>
                </li>
              ))}
            </ul>

            {/* Customer-uploaded evidence photos */}
            {Array.isArray(selected.photos) && selected.photos.length > 0 && (
              <>
                <h6 className="fw-bold small text-uppercase text-muted">Müşteri Fotoğrafları</h6>
                <div className="d-flex flex-wrap gap-2 mb-3">
                  {selected.photos.map((ph) => (
                    <button
                      key={ph.id}
                      type="button"
                      className="border rounded p-0 bg-transparent"
                      style={{ width: 72, height: 72, overflow: 'hidden', cursor: 'zoom-in' }}
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
              </>
            )}

            <div className="d-flex gap-2 justify-content-end flex-wrap">
              <button
                className="btn btn-outline-primary me-auto"
                disabled={acting}
                onClick={() => doAction(selected, 'reply')}
              >
                <i className="fas fa-reply me-1" />
                Müşteriye Yanıt
              </button>
              {actionsFor(selected).map((act) => (
                <button
                  key={act.key}
                  className={`btn ${act.cls}`}
                  disabled={acting}
                  onClick={() => doAction(selected, act.key)}
                >
                  {act.label}
                </button>
              ))}
              <button className="btn btn-light border" onClick={() => setSelected(null)}>
                Kapat
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Photo lightbox */}
      {photoPreview && (
        <div
          className="position-fixed top-0 start-0 w-100 h-100 d-flex align-items-center justify-content-center"
          style={{ background: 'rgba(0,0,0,0.85)', zIndex: 1090, cursor: 'zoom-out' }}
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
    </div>
  );
}
