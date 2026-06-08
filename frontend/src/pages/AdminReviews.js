import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { useAdminToast } from '../components/AdminToast';
import ConfirmModal from '../components/ConfirmModal';

/**
 * Admin: product review moderation panel.
 *
 * - Pending reviews are the default filter
 * - Approve / Delete actions
 * - Filter: all / approved / pending
 * - Pagination
 */
export default function AdminReviews() {
  const toast = useAdminToast();
  const [reviews, setReviews] = useState([]);
  const [loading, setLoading] = useState(false);
  const [filter, setFilter] = useState('pending'); // 'pending' | 'approved' | 'all'
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [insights, setInsights] = useState(null);
  const [replyTarget, setReplyTarget] = useState(null);
  const [replyText, setReplyText] = useState('');
  const [replySaving, setReplySaving] = useState(false);
  const [lightbox, setLightbox] = useState(null);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    fetchReviews();
  }, [filter, page]);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    fetchInsights();
  }, []);

  const fetchInsights = async () => {
    try {
      const res = await axios.get('/api/admin/reviews/insights');
      setInsights(res.data);
    } catch {
      /* non-critical */
    }
  };

  const openReply = (r) => {
    setReplyTarget(r);
    setReplyText(r.adminReply || '');
  };

  const saveReply = async () => {
    if (!replyTarget) return;
    setReplySaving(true);
    try {
      await axios.put(`/api/admin/reviews/${replyTarget.id}/reply`, { reply: replyText });
      toast.success('Yanıt kaydedildi');
      setReplyTarget(null);
      fetchReviews();
    } catch (e) {
      toast.error(e.response?.data?.message || 'Yanıt kaydedilemedi');
    } finally {
      setReplySaving(false);
    }
  };

  const fetchReviews = async () => {
    setLoading(true);
    try {
      const params = { page, size: 20 };
      if (filter === 'approved') params.approved = true;
      if (filter === 'pending') params.approved = false;
      const res = await axios.get('/api/admin/reviews', { params });
      setReviews(res.data.items || []);
      setTotalPages(res.data.totalPages || 0);
      setTotalElements(res.data.totalElements || 0);
    } catch (e) {
      toast.error(e.response?.data?.message || 'Yorumlar yüklenemedi');
    } finally {
      setLoading(false);
    }
  };

  const approve = async (id) => {
    try {
      await axios.post(`/api/admin/reviews/${id}/approve`);
      toast.success('Yorum onaylandı');
      fetchReviews();
    } catch (e) {
      toast.error(e.response?.data?.message || 'Onaylama başarısız');
    }
  };

  const reject = async () => {
    if (!deleteTarget) return;
    try {
      await axios.delete(`/api/admin/reviews/${deleteTarget.id}`);
      toast.success('Yorum silindi');
      setDeleteTarget(null);
      fetchReviews();
    } catch (e) {
      toast.error(e.response?.data?.message || 'Silme başarısız');
    }
  };

  const stars = (n) => '★'.repeat(n) + '☆'.repeat(5 - n);

  return (
    <div className="container-fluid">
      <div className="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
        <div>
          <h2 className="mb-0">
            <i className="fas fa-star text-warning me-2" />
            Ürün Yorumları
          </h2>
          <p className="text-muted small mb-0">Toplam {totalElements} yorum</p>
        </div>
        <div className="btn-group" role="group" aria-label="Filtre">
          <button
            className={`btn btn-sm ${filter === 'pending' ? 'btn-primary' : 'btn-outline-primary'}`}
            onClick={() => {
              setFilter('pending');
              setPage(0);
            }}
          >
            <i className="fas fa-clock me-1" />
            Onay Bekleyen
          </button>
          <button
            className={`btn btn-sm ${filter === 'approved' ? 'btn-primary' : 'btn-outline-primary'}`}
            onClick={() => {
              setFilter('approved');
              setPage(0);
            }}
          >
            <i className="fas fa-check-circle me-1" />
            Onaylı
          </button>
          <button
            className={`btn btn-sm ${filter === 'all' ? 'btn-primary' : 'btn-outline-primary'}`}
            onClick={() => {
              setFilter('all');
              setPage(0);
            }}
          >
            Tümü
          </button>
        </div>
      </div>

      {/* Insight cards */}
      {insights && (
        <div className="row g-3 mb-3">
          {[
            {
              label: 'Toplam Yorum',
              value: insights.totalReviews ?? 0,
              icon: 'fa-comments',
              color: 'primary',
            },
            {
              label: 'Onay Bekleyen',
              value: insights.pendingReviews ?? 0,
              icon: 'fa-clock',
              color: 'warning',
            },
            {
              label: 'Onaylı',
              value: insights.approvedReviews ?? 0,
              icon: 'fa-check-circle',
              color: 'success',
            },
            {
              label: 'Ortalama Puan',
              value: insights.averageRating != null ? `${Number(insights.averageRating).toFixed(1)} ★` : '—',
              icon: 'fa-star',
              color: 'warning',
            },
          ].map((c) => (
            <div key={c.label} className="col-6 col-md-3">
              <div className="card border-0 shadow-sm h-100">
                <div className="card-body d-flex align-items-center gap-3">
                  <i className={`fas ${c.icon} fa-2x text-${c.color}`} />
                  <div>
                    <div className="h4 mb-0 fw-bold">{c.value}</div>
                    <div className="text-muted small">{c.label}</div>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {loading ? (
        <div className="text-center py-5">
          <div className="spinner-border text-primary" />
        </div>
      ) : reviews.length === 0 ? (
        <div className="alert alert-light text-center py-4">Bu filtreye uygun yorum bulunamadı.</div>
      ) : (
        <div className="table-responsive">
          <table className="table table-hover align-middle">
            <thead className="table-light">
              <tr>
                <th>Ürün</th>
                <th>Müşteri</th>
                <th>Puan</th>
                <th>Yorum</th>
                <th>Görsel</th>
                <th>Tarih</th>
                <th>Durum</th>
                <th style={{ width: 240 }}>İşlem</th>
              </tr>
            </thead>
            <tbody>
              {reviews.map((r) => (
                <tr key={r.id}>
                  <td>
                    {r.productSlug ? (
                      <a
                        href={`/urun/${r.productSlug}`}
                        target="_blank"
                        rel="noreferrer"
                        className="text-decoration-none"
                      >
                        {r.productName}
                      </a>
                    ) : (
                      r.productName
                    )}
                  </td>
                  <td>
                    <div className="small">{r.customerName}</div>
                    <div className="text-muted" style={{ fontSize: 11 }}>
                      {r.customerEmail}
                    </div>
                  </td>
                  <td className="text-warning text-nowrap">{stars(r.rating)}</td>
                  <td style={{ maxWidth: 320 }}>
                    {r.title && <div className="fw-semibold small">{r.title}</div>}
                    <div className="small text-truncate text-muted" title={r.comment}>
                      {r.comment}
                    </div>
                    {r.verifiedPurchase && (
                      <span className="badge bg-success-subtle text-success mt-1" style={{ fontSize: 10 }}>
                        <i className="fas fa-check-circle me-1" />
                        Doğrulanmış
                      </span>
                    )}
                    {r.adminReply && (
                      <div className="small text-primary mt-1" style={{ fontSize: 11 }}>
                        <i className="fas fa-reply me-1" />
                        Yanıtlandı
                      </div>
                    )}
                  </td>
                  <td>
                    {Array.isArray(r.images) && r.images.length > 0 ? (
                      <div className="d-flex gap-1">
                        {r.images.slice(0, 3).map((url) => (
                          <button
                            key={url}
                            type="button"
                            className="p-0 border rounded"
                            style={{ width: 38, height: 38, overflow: 'hidden', background: 'none' }}
                            onClick={() => setLightbox(url)}
                          >
                            <img
                              src={url}
                              alt=""
                              style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                            />
                          </button>
                        ))}
                        {r.images.length > 3 && (
                          <span className="text-muted small align-self-center">+{r.images.length - 3}</span>
                        )}
                      </div>
                    ) : (
                      <span className="text-muted small">—</span>
                    )}
                  </td>
                  <td className="text-muted small">
                    {r.createdAt ? new Date(r.createdAt).toLocaleString('tr-TR') : '—'}
                  </td>
                  <td>
                    {r.approved ? (
                      <span className="badge bg-success">Onaylı</span>
                    ) : (
                      <span className="badge bg-warning text-dark">Bekliyor</span>
                    )}
                  </td>
                  <td>
                    {!r.approved && (
                      <button className="btn btn-sm btn-success me-1 mb-1" onClick={() => approve(r.id)}>
                        <i className="fas fa-check" /> Onayla
                      </button>
                    )}
                    <button className="btn btn-sm btn-outline-primary me-1 mb-1" onClick={() => openReply(r)}>
                      <i className="fas fa-reply" /> Yanıtla
                    </button>
                    <button className="btn btn-sm btn-outline-danger mb-1" onClick={() => setDeleteTarget(r)}>
                      <i className="fas fa-trash" /> Sil
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {totalPages > 1 && (
        <nav className="d-flex justify-content-center mt-3">
          <ul className="pagination pagination-sm">
            <li className={`page-item ${page === 0 ? 'disabled' : ''}`}>
              <button className="page-link" onClick={() => setPage((p) => Math.max(0, p - 1))}>
                Önceki
              </button>
            </li>
            <li className="page-item active">
              <span className="page-link">
                {page + 1} / {totalPages}
              </span>
            </li>
            <li className={`page-item ${page >= totalPages - 1 ? 'disabled' : ''}`}>
              <button className="page-link" onClick={() => setPage((p) => p + 1)}>
                Sonraki
              </button>
            </li>
          </ul>
        </nav>
      )}

      <ConfirmModal
        show={!!deleteTarget}
        title="Yorumu Sil"
        message={`Bu yorum kalıcı olarak silinecek. Devam etmek istiyor musunuz?\n\n"${deleteTarget?.comment?.substring(0, 100)}${deleteTarget?.comment?.length > 100 ? '...' : ''}"`}
        confirmText="Sil"
        confirmVariant="danger"
        onConfirm={reject}
        onCancel={() => setDeleteTarget(null)}
      />

      {/* Reply modal */}
      {replyTarget && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">Yoruma Yanıt Ver</h5>
                <button type="button" className="btn-close" onClick={() => setReplyTarget(null)} />
              </div>
              <div className="modal-body">
                <p className="text-muted small mb-2">
                  <strong>{replyTarget.customerName}</strong> · {stars(replyTarget.rating)}
                  <br />
                  {replyTarget.comment}
                </p>
                <textarea
                  className="form-control"
                  rows="4"
                  placeholder="Müşteriye yanıtınız (boş bırakıp kaydederek yanıtı kaldırabilirsiniz)..."
                  value={replyText}
                  maxLength={1000}
                  onChange={(e) => setReplyText(e.target.value)}
                />
              </div>
              <div className="modal-footer">
                <button
                  className="btn btn-secondary"
                  onClick={() => setReplyTarget(null)}
                  disabled={replySaving}
                >
                  İptal
                </button>
                <button className="btn btn-primary" onClick={saveReply} disabled={replySaving}>
                  {replySaving ? 'Kaydediliyor...' : 'Yanıtı Kaydet'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Image lightbox */}
      {lightbox && (
        <div
          className="modal show d-block"
          tabIndex="-1"
          style={{ backgroundColor: 'rgba(0,0,0,0.85)' }}
          onClick={() => setLightbox(null)}
        >
          <div className="modal-dialog modal-dialog-centered modal-lg">
            <img
              src={lightbox}
              alt=""
              style={{ width: '100%', borderRadius: 8 }}
              onClick={(e) => e.stopPropagation()}
            />
          </div>
        </div>
      )}
    </div>
  );
}
