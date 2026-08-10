import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';
import { FiCheckCircle, FiCamera, FiX, FiEdit2, FiTrash2 } from 'react-icons/fi';
import RatingStars from './RatingStars';
import { useToast } from './Toast';
import confirmDialog from '../../utils/confirmDialog';

const fmtDate = (s) => {
  if (!s) return '';
  try {
    return new Date(s).toLocaleDateString('tr-TR', { day: 'numeric', month: 'long', year: 'numeric' });
  } catch {
    return '';
  }
};

export default function ProductReviews({ productId }) {
  const toast = useToast();
  const [data, setData] = useState(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [elig, setElig] = useState(null);

  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [rating, setRating] = useState(0);
  const [title, setTitle] = useState('');
  const [comment, setComment] = useState('');
  const [stagedFiles, setStagedFiles] = useState([]); // File[]
  const [existingImages, setExistingImages] = useState([]); // [{id,url}]
  const [submitting, setSubmitting] = useState(false);
  const [lightbox, setLightbox] = useState(null);
  const fileRef = useRef(null);

  const loggedIn = !!localStorage.getItem('customer_token');

  const fetchList = useCallback(
    (p = 0) => {
      setLoading(true);
      axios
        .get(`/api/store/products/${productId}/reviews`, { params: { page: p, size: 10 } })
        .then((r) => {
          setData(r.data);
          setPage(r.data?.page ?? p);
        })
        .catch(() => setData({ items: [], distribution: {}, average: null, count: 0, totalPages: 0 }))
        .finally(() => setLoading(false));
    },
    [productId]
  );

  const fetchEligibility = useCallback(() => {
    if (!loggedIn) {
      setElig({ loggedIn: false, canReview: false });
      return;
    }
    axios
      .get(`/api/store/products/${productId}/reviews/eligibility`)
      .then((r) => setElig(r.data))
      .catch(() => setElig({ loggedIn: true, canReview: false }));
  }, [productId, loggedIn]);

  useEffect(() => {
    fetchList(0);
    fetchEligibility();
  }, [fetchList, fetchEligibility]);

  const openNewForm = () => {
    setEditingId(null);
    setRating(0);
    setTitle('');
    setComment('');
    setStagedFiles([]);
    setExistingImages([]);
    setShowForm(true);
  };

  const openEditForm = () => {
    const r = elig?.myReview;
    if (!r) return;
    setEditingId(r.id);
    setRating(r.rating || 0);
    setTitle(r.title || '');
    setComment(r.comment || '');
    setStagedFiles([]);
    setExistingImages(Array.isArray(r.images) ? r.images : []);
    setShowForm(true);
  };

  const onPickFiles = (e) => {
    const files = Array.from(e.target.files || []);
    const room = 5 - (existingImages.length + stagedFiles.length);
    setStagedFiles((prev) => [...prev, ...files.slice(0, Math.max(0, room))]);
    if (fileRef.current) fileRef.current.value = '';
  };

  const uploadStaged = async (reviewId) => {
    for (let i = 0; i < stagedFiles.length; i += 1) {
      const fd = new FormData();
      fd.append('file', stagedFiles[i]);
      // eslint-disable-next-line no-await-in-loop
      await axios.post(`/api/store/reviews/${reviewId}/images`, fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
    }
  };

  const removeExisting = async (imageId) => {
    try {
      await axios.delete(`/api/store/reviews/images/${imageId}`);
      setExistingImages((prev) => prev.filter((im) => im.id !== imageId));
    } catch {
      toast.error('Görsel silinemedi.');
    }
  };

  const submit = async () => {
    if (rating < 1) {
      toast.warning('Lütfen bir puan verin.');
      return;
    }
    setSubmitting(true);
    try {
      let reviewId = editingId;
      if (editingId) {
        await axios.put(`/api/store/reviews/${editingId}`, { rating, title, comment });
      } else {
        const r = await axios.post(`/api/store/products/${productId}/reviews`, { rating, title, comment });
        reviewId = r.data?.id;
      }
      if (reviewId && stagedFiles.length > 0) await uploadStaged(reviewId);
      toast.success('Yorumunuz alındı. Onaylandıktan sonra yayınlanacaktır.');
      setShowForm(false);
      fetchEligibility();
      fetchList(0);
    } catch (e) {
      toast.error(e?.response?.data?.error || 'Yorum gönderilemedi.');
    } finally {
      setSubmitting(false);
    }
  };

  const deleteMyReview = async () => {
    const r = elig?.myReview;
    if (!r) return;
    const ok = await confirmDialog({
      title: 'Yorum Silinsin mi?',
      message: 'Yorumunuz kalıcı olarak silinecek.',
      confirmText: 'Evet, Sil',
    });
    if (!ok) return;
    try {
      await axios.delete(`/api/store/reviews/${r.id}`);
      toast.info('Yorumunuz silindi.');
      fetchEligibility();
      fetchList(0);
    } catch {
      toast.error('Yorum silinemedi.');
    }
  };

  const avg = data?.average ? Number(data.average) : 0;
  const count = data?.count || 0;
  const dist = data?.distribution || {};
  const previewUrls = stagedFiles.map((f) => URL.createObjectURL(f));

  return (
    <div className="store-reviews">
      {/* Summary */}
      <div className="row g-4 align-items-center mb-4">
        <div className="col-auto text-center">
          <div className="store-review-avg">{count > 0 ? avg.toFixed(1) : '—'}</div>
          <RatingStars value={avg} size={18} />
          <div className="text-muted small mt-1">{count} değerlendirme</div>
        </div>
        <div className="col">
          {[5, 4, 3, 2, 1].map((star) => {
            const c = Number(dist[star] || 0);
            const pct = count > 0 ? (c / count) * 100 : 0;
            return (
              <div key={star} className="d-flex align-items-center gap-2 mb-1">
                <span className="text-muted small" style={{ width: 28 }}>
                  {star}★
                </span>
                <div className="store-review-bar flex-grow-1">
                  <div className="store-review-bar-fill" style={{ width: `${pct}%` }} />
                </div>
                <span className="text-muted small" style={{ width: 28, textAlign: 'right' }}>
                  {c}
                </span>
              </div>
            );
          })}
        </div>
      </div>

      {/* Eligibility CTA */}
      {!showForm && (
        <div className="mb-4">
          {!loggedIn && (
            <div className="alert alert-light border d-flex flex-wrap align-items-center justify-content-between gap-2 mb-0">
              <span>Bu ürünü değerlendirmek için giriş yapın.</span>
              <Link to="/giris" className="btn btn-sm btn-outline-primary">
                Giriş Yap
              </Link>
            </div>
          )}
          {loggedIn && elig && !elig.hasPurchased && !elig.hasReviewed && (
            <div className="alert alert-light border mb-0 small text-muted">
              Yalnızca bu ürünü satın alan müşteriler değerlendirme yapabilir.
            </div>
          )}
          {loggedIn && elig?.canReview && (
            <button className="btn btn-primary" onClick={openNewForm}>
              <FiEdit2 size={16} className="me-2" />
              Değerlendirme Yap
            </button>
          )}
          {loggedIn && elig?.hasReviewed && elig?.myReview && (
            <div className="store-review-mine p-3 rounded border">
              <div className="d-flex justify-content-between align-items-start gap-2">
                <div>
                  <div className="d-flex align-items-center gap-2">
                    <RatingStars value={elig.myReview.rating} size={16} />
                    <span
                      className={`badge ${elig.myReview.approved ? 'bg-success' : 'bg-warning text-dark'}`}
                    >
                      {elig.myReview.approved ? 'Yayında' : 'Onay bekliyor'}
                    </span>
                  </div>
                  {elig.myReview.title && <div className="fw-semibold mt-1">{elig.myReview.title}</div>}
                  {elig.myReview.comment && <div className="text-muted small">{elig.myReview.comment}</div>}
                </div>
                <div className="d-flex gap-1 flex-shrink-0">
                  <button className="btn btn-sm btn-outline-secondary" onClick={openEditForm} title="Düzenle">
                    <FiEdit2 size={14} />
                  </button>
                  <button className="btn btn-sm btn-outline-danger" onClick={deleteMyReview} title="Sil">
                    <FiTrash2 size={14} />
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Form */}
      {showForm && (
        <div className="store-review-form p-3 rounded border mb-4">
          <h6 className="fw-bold mb-3">{editingId ? 'Yorumunuzu Düzenleyin' : 'Değerlendirmenizi Yazın'}</h6>
          <div className="mb-3">
            <label className="form-label d-block mb-1">Puanınız</label>
            <RatingStars value={rating} size={28} interactive onChange={setRating} />
          </div>
          <div className="mb-3">
            <input
              type="text"
              className="form-control"
              placeholder="Başlık (opsiyonel)"
              value={title}
              maxLength={200}
              onChange={(e) => setTitle(e.target.value)}
            />
          </div>
          <div className="mb-3">
            <textarea
              className="form-control"
              rows="3"
              placeholder="Ürün hakkındaki düşünceleriniz..."
              value={comment}
              maxLength={2000}
              onChange={(e) => setComment(e.target.value)}
            />
          </div>

          {/* Photos */}
          <div className="mb-3">
            <div className="d-flex flex-wrap gap-2 mb-2">
              {existingImages.map((im) => (
                <div key={im.id} className="store-review-thumb position-relative">
                  <img src={im.url} alt="" />
                  <button
                    type="button"
                    className="store-review-thumb-x"
                    onClick={() => removeExisting(im.id)}
                    aria-label="Fotoğrafı kaldır"
                  >
                    <FiX size={12} />
                  </button>
                </div>
              ))}
              {previewUrls.map((u, i) => (
                <div key={u} className="store-review-thumb position-relative">
                  <img src={u} alt="" />
                  <button
                    type="button"
                    className="store-review-thumb-x"
                    onClick={() => setStagedFiles((prev) => prev.filter((_, idx) => idx !== i))}
                    aria-label="Fotoğrafı kaldır"
                  >
                    <FiX size={12} />
                  </button>
                </div>
              ))}
              {existingImages.length + stagedFiles.length < 5 && (
                <button
                  type="button"
                  className="store-review-add-photo"
                  onClick={() => fileRef.current?.click()}
                >
                  <FiCamera size={18} />
                  <span>Foto Ekle</span>
                </button>
              )}
            </div>
            <input ref={fileRef} type="file" accept="image/*" multiple hidden onChange={onPickFiles} />
            <small className="text-muted">En fazla 5 görsel.</small>
          </div>

          <div className="d-flex gap-2">
            <button className="btn btn-secondary" onClick={() => setShowForm(false)} disabled={submitting}>
              İptal
            </button>
            <button className="btn btn-primary" onClick={submit} disabled={submitting}>
              {submitting ? 'Gönderiliyor...' : editingId ? 'Güncelle' : 'Gönder'}
            </button>
          </div>
        </div>
      )}

      {/* List */}
      {loading ? (
        <div className="text-center py-4">
          <span className="spinner-border text-primary" />
        </div>
      ) : (data?.items || []).length === 0 ? (
        <p className="text-muted text-center py-3">Henüz değerlendirme yok. İlk değerlendirmeyi siz yapın!</p>
      ) : (
        <div className="d-flex flex-column gap-3">
          {data.items.map((r) => (
            <div key={r.id} className="store-review-card p-3 rounded border">
              <div className="d-flex align-items-center gap-2 flex-wrap mb-1">
                <RatingStars value={r.rating} size={15} />
                {r.verifiedPurchase && (
                  <span className="store-review-verified">
                    <FiCheckCircle size={12} className="me-1" />
                    Doğrulanmış Alışveriş
                  </span>
                )}
              </div>
              {r.title && <div className="fw-semibold">{r.title}</div>}
              {r.comment && (
                <div className="text-muted mt-1" style={{ whiteSpace: 'pre-wrap' }}>
                  {r.comment}
                </div>
              )}
              {Array.isArray(r.images) && r.images.length > 0 && (
                <div className="d-flex flex-wrap gap-2 mt-2">
                  {r.images.map((url) => (
                    <button
                      key={url}
                      type="button"
                      className="store-review-thumb"
                      onClick={() => setLightbox(url)}
                    >
                      <img src={url} alt="" />
                    </button>
                  ))}
                </div>
              )}
              <div className="text-muted small mt-2">
                {r.authorName} · {fmtDate(r.createdAt)}
              </div>
              {r.adminReply && (
                <div className="store-review-reply mt-2 p-2 rounded">
                  <div className="fw-semibold small mb-1">Satıcı yanıtı</div>
                  <div className="small text-muted">{r.adminReply}</div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Pagination */}
      {data && data.totalPages > 1 && (
        <div className="d-flex justify-content-center gap-2 mt-3">
          <button
            className="btn btn-sm btn-outline-secondary"
            disabled={page === 0}
            onClick={() => fetchList(page - 1)}
          >
            ← Önceki
          </button>
          <span className="align-self-center small text-muted">
            {page + 1} / {data.totalPages}
          </span>
          <button
            className="btn btn-sm btn-outline-secondary"
            disabled={page >= data.totalPages - 1}
            onClick={() => fetchList(page + 1)}
          >
            Sonraki →
          </button>
        </div>
      )}

      {/* Lightbox */}
      {lightbox && (
        <div
          className="store-review-lightbox"
          onClick={() => setLightbox(null)}
          role="dialog"
          aria-modal="true"
        >
          <img src={lightbox} alt="" onClick={(e) => e.stopPropagation()} />
          <button className="store-review-lightbox-x" onClick={() => setLightbox(null)} aria-label="Kapat">
            <FiX size={26} />
          </button>
        </div>
      )}
    </div>
  );
}
