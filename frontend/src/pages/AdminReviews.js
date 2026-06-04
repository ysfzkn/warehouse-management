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

  useEffect(() => { fetchReviews(); /* eslint-disable-next-line */ }, [filter, page]);

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
          <h2 className="mb-0"><i className="fas fa-star text-warning me-2" />Ürün Yorumları</h2>
          <p className="text-muted small mb-0">Toplam {totalElements} yorum</p>
        </div>
        <div className="btn-group" role="group" aria-label="Filtre">
          <button className={`btn btn-sm ${filter === 'pending' ? 'btn-primary' : 'btn-outline-primary'}`} onClick={() => { setFilter('pending'); setPage(0); }}>
            <i className="fas fa-clock me-1" />Onay Bekleyen
          </button>
          <button className={`btn btn-sm ${filter === 'approved' ? 'btn-primary' : 'btn-outline-primary'}`} onClick={() => { setFilter('approved'); setPage(0); }}>
            <i className="fas fa-check-circle me-1" />Onaylı
          </button>
          <button className={`btn btn-sm ${filter === 'all' ? 'btn-primary' : 'btn-outline-primary'}`} onClick={() => { setFilter('all'); setPage(0); }}>
            Tümü
          </button>
        </div>
      </div>

      {loading ? (
        <div className="text-center py-5"><div className="spinner-border text-primary" /></div>
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
                <th>Tarih</th>
                <th>Durum</th>
                <th style={{ width: 180 }}>İşlem</th>
              </tr>
            </thead>
            <tbody>
              {reviews.map(r => (
                <tr key={r.id}>
                  <td>
                    {r.productSlug ? (
                      <a href={`/urun/${r.productSlug}`} target="_blank" rel="noreferrer" className="text-decoration-none">{r.productName}</a>
                    ) : r.productName}
                  </td>
                  <td>
                    <div className="small">{r.customerName}</div>
                    <div className="text-muted" style={{ fontSize: 11 }}>{r.customerEmail}</div>
                  </td>
                  <td className="text-warning">{stars(r.rating)}</td>
                  <td style={{ maxWidth: 320 }}>
                    <div className="small text-truncate" title={r.comment}>{r.comment}</div>
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
                      <button className="btn btn-sm btn-success me-1" onClick={() => approve(r.id)}>
                        <i className="fas fa-check" /> Onayla
                      </button>
                    )}
                    <button className="btn btn-sm btn-outline-danger" onClick={() => setDeleteTarget(r)}>
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
              <button className="page-link" onClick={() => setPage(p => Math.max(0, p - 1))}>Önceki</button>
            </li>
            <li className="page-item active"><span className="page-link">{page + 1} / {totalPages}</span></li>
            <li className={`page-item ${page >= totalPages - 1 ? 'disabled' : ''}`}>
              <button className="page-link" onClick={() => setPage(p => p + 1)}>Sonraki</button>
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
    </div>
  );
}
