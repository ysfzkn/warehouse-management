import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import ProductSetForm from '../components/ProductSetForm';
import useSecurityCodePrompt from '../components/useSecurityCodePrompt';

/**
 * Admin page for "Ürün Setleri" (product sets / bundles).
 *
 * A set is a Product with productType = BUNDLE. This page lists only bundles
 * (productType=BUNDLE filter) and creates/edits them through ProductForm in
 * `setMode`, which adds the member-product picker.
 */
const fmtPrice = (v) =>
  v == null ? '—' : new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(Number(v));

const PAGE_SIZE = 20;

export default function ProductSets() {
  const [sets, setSets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [showForm, setShowForm] = useState(false);
  const [editingSet, setEditingSet] = useState(null);
  const [expandedId, setExpandedId] = useState(null);
  const [toast, setToast] = useState(null);

  const navigate = useNavigate();
  const { askCode: askSecurityCode, SecurityCodePrompt } = useSecurityCodePrompt();

  const flash = (message, variant = 'success') => {
    setToast({ message, variant });
    setTimeout(() => setToast(null), 3000);
  };

  const fetchSets = useCallback(() => {
    setLoading(true);
    axios
      .get('/api/products', {
        params: {
          productType: 'BUNDLE',
          search: search.trim() || undefined,
          page,
          size: PAGE_SIZE,
          sortBy: 'updatedAt',
          sortDir: 'desc',
        },
      })
      .then((r) => {
        const data = r.data;
        setSets(Array.isArray(data?.content) ? data.content : Array.isArray(data) ? data : []);
        setTotalPages(data?.totalPages || 0);
      })
      .catch(() => setSets([]))
      .finally(() => setLoading(false));
  }, [search, page]);

  useEffect(() => {
    fetchSets();
  }, [fetchSets]);

  const handleCreate = () => {
    setEditingSet(null);
    setShowForm(true);
  };

  const handleEdit = (set) => {
    setEditingSet(set);
    setShowForm(true);
  };

  const handleFormSuccess = () => {
    setShowForm(false);
    setEditingSet(null);
    flash(editingSet ? 'Set güncellendi.' : 'Set oluşturuldu.');
    fetchSets();
  };

  const handleDelete = async (id) => {
    try {
      const code = await askSecurityCode({
        title: 'Set Sil',
        message:
          'Bu seti silmek için güvenlik kodunu girin. (Üye ürünler silinmez, yalnızca set kaldırılır.)',
      });
      if (!code) return;
      await axios.delete(`/api/products/${id}`, { headers: { 'X-ADMIN-SECURITY-CODE': code } });
      flash('Set silindi.');
      fetchSets();
    } catch (e) {
      flash(e?.response?.data?.message || 'Set silinemedi.', 'danger');
    }
  };

  return (
    <div className="container-fluid py-4">
      <style>{`
        .set-member-card { transition: background 0.15s ease, border-color 0.15s ease; cursor: pointer; color: #1f2937; }
        .set-member-card .fw-medium { color: #1f2937; }
        .set-member-card:hover { background: #eef2ff !important; border-color: #c7d2fe !important; color: #1f2937; }
      `}</style>
      <div className="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
        <div>
          <h3 className="fw-bold mb-1">
            <i className="fas fa-layer-group me-2 text-info" />
            Ürün Setleri
          </h3>
          <p className="text-muted mb-0 small">
            Birden fazla ürünü tek pakette satışa sunun (ör. Ankastre Set). Stok, üyelerden hesaplanır.
          </p>
        </div>
        <button className="btn btn-primary" onClick={handleCreate}>
          <i className="fas fa-plus me-2" />
          Yeni Set
        </button>
      </div>

      <div className="card border-0 shadow-sm mb-3">
        <div className="card-body">
          <div className="input-group" style={{ maxWidth: 420 }}>
            <span className="input-group-text bg-transparent">
              <i className="fas fa-search text-secondary" />
            </span>
            <input
              type="text"
              className="form-control"
              placeholder="Set adı veya SKU ile ara..."
              value={search}
              onChange={(e) => {
                setPage(0);
                setSearch(e.target.value);
              }}
            />
          </div>
        </div>
      </div>

      <div className="card border-0 shadow-sm">
        <div className="table-responsive">
          <table className="table table-hover align-middle mb-0">
            <thead className="table-light">
              <tr>
                <th style={{ width: 56 }}>Görsel</th>
                <th>Set Adı</th>
                <th>SKU</th>
                <th className="text-center">İçerik</th>
                <th className="text-end">Fiyat</th>
                <th className="text-center">Durum</th>
                <th className="text-end">İşlemler</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan="7" className="text-center py-5">
                    <span className="spinner-border text-primary" />
                  </td>
                </tr>
              ) : sets.length === 0 ? (
                <tr>
                  <td colSpan="7" className="text-center text-muted py-5">
                    <i className="fas fa-layer-group fa-2x mb-2 d-block opacity-25" />
                    Henüz ürün seti yok. "Yeni Set" ile oluşturun.
                  </td>
                </tr>
              ) : (
                sets.map((s) => {
                  const open = expandedId === s.id;
                  const toggle = () => setExpandedId(open ? null : s.id);
                  return (
                    <React.Fragment key={s.id}>
                      <tr style={{ cursor: 'pointer' }} onClick={toggle}>
                        <td>
                          <div
                            style={{
                              width: 44,
                              height: 44,
                              borderRadius: 8,
                              background: '#f8fafc',
                              border: '1px solid #e2e8f0',
                              overflow: 'hidden',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                            }}
                          >
                            {s.primaryImageUrl ? (
                              <img
                                src={s.primaryImageUrl}
                                alt={s.name}
                                style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                              />
                            ) : (
                              <i className="fas fa-layer-group text-muted" />
                            )}
                          </div>
                        </td>
                        <td className="fw-medium">
                          <i className={`fas fa-chevron-${open ? 'down' : 'right'} text-muted me-2 small`} />
                          {s.name}
                        </td>
                        <td className="text-muted">{s.sku}</td>
                        <td className="text-center">
                          <span className="badge bg-info-subtle text-info">
                            {s.bundleItemCount != null ? `${s.bundleItemCount} ürün` : '—'}
                          </span>
                        </td>
                        <td className="text-end">
                          {s.salePrice && Number(s.salePrice) > 0 ? (
                            <>
                              <span className="fw-bold text-danger">{fmtPrice(s.salePrice)}</span>
                              <del className="text-muted ms-1 small">{fmtPrice(s.price)}</del>
                            </>
                          ) : (
                            <span className="fw-bold">{fmtPrice(s.price)}</span>
                          )}
                        </td>
                        <td className="text-center">
                          {s.active ? (
                            <span className="badge bg-success-subtle text-success">Aktif</span>
                          ) : (
                            <span className="badge bg-secondary-subtle text-secondary">Pasif</span>
                          )}
                        </td>
                        <td className="text-end" onClick={(e) => e.stopPropagation()}>
                          <button
                            className="btn btn-sm btn-outline-primary me-1"
                            onClick={() => handleEdit(s)}
                            title="Düzenle"
                          >
                            <i className="fas fa-edit" />
                          </button>
                          <button
                            className="btn btn-sm btn-outline-danger"
                            onClick={() => handleDelete(s.id)}
                            title="Sil"
                          >
                            <i className="fas fa-trash" />
                          </button>
                        </td>
                      </tr>
                      {open && (
                        <tr>
                          <td colSpan="7" className="p-0">
                            <div className="p-3" style={{ background: '#f8fafc' }}>
                              <div className="fw-semibold small text-muted mb-2">
                                <i className="fas fa-box-open me-2" />
                                Set İçindeki Ürünler
                              </div>
                              {Array.isArray(s.bundleItems) && s.bundleItems.length > 0 ? (
                                <div className="row g-2">
                                  {s.bundleItems.map((m) => (
                                    <div key={m.productId} className="col-md-6">
                                      <button
                                        type="button"
                                        className="set-member-card d-flex align-items-center justify-content-between border rounded px-3 py-2 bg-white w-100 text-start"
                                        onClick={() => navigate(`/products?editId=${m.productId}`)}
                                        title="Ürüne git (düzenle)"
                                      >
                                        <div className="min-w-0">
                                          <div className="fw-medium text-truncate">
                                            {m.name}
                                            <i className="fas fa-arrow-up-right-from-square text-muted ms-2 small" />
                                          </div>
                                          <small className="text-muted">
                                            SKU: {m.sku}
                                            {(m.salePrice || m.price) != null && (
                                              <>
                                                {' '}
                                                ·{' '}
                                                {fmtPrice(
                                                  m.salePrice && Number(m.salePrice) > 0
                                                    ? m.salePrice
                                                    : m.price
                                                )}
                                              </>
                                            )}
                                          </small>
                                        </div>
                                        <span className="badge bg-light text-dark border ms-2">
                                          ×{m.quantity}
                                        </span>
                                      </button>
                                    </div>
                                  ))}
                                </div>
                              ) : (
                                <div className="text-muted small">Üye bilgisi yüklenemedi.</div>
                              )}
                            </div>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {totalPages > 1 && (
        <div className="d-flex justify-content-center gap-2 mt-3">
          <button
            className="btn btn-sm btn-outline-secondary"
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
          >
            ← Önceki
          </button>
          <span className="align-self-center small text-muted">
            {page + 1} / {totalPages}
          </span>
          <button
            className="btn btn-sm btn-outline-secondary"
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
          >
            Sonraki →
          </button>
        </div>
      )}

      {showForm && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-lg my-4">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">{editingSet ? 'Set Düzenle' : 'Yeni Set'}</h5>
                <button type="button" className="btn-close" onClick={() => setShowForm(false)}></button>
              </div>
              <div className="modal-body">
                <ProductSetForm
                  product={editingSet}
                  onSuccess={handleFormSuccess}
                  onCancel={() => setShowForm(false)}
                />
              </div>
            </div>
          </div>
        </div>
      )}

      {toast && (
        <div
          className={`toast align-items-center text-bg-${toast.variant} border-0 position-fixed top-0 end-0 m-3 show`}
          style={{ zIndex: 1100 }}
          role="alert"
        >
          <div className="d-flex">
            <div className="toast-body">{toast.message}</div>
          </div>
        </div>
      )}

      {SecurityCodePrompt}
    </div>
  );
}
