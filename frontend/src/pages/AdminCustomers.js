import React, { useState, useEffect, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import axios from 'axios';
import PaginationControls from '../components/PaginationControls';
import ConfirmModal from '../components/ConfirmModal';
import useSecurityCodePrompt from '../components/useSecurityCodePrompt';
import { useAdminToast } from '../components/AdminToast';

const STATUS_CONFIG = {
  ACTIVE: { label: 'Aktif', color: 'success' },
  INACTIVE: { label: 'Pasif', color: 'secondary' },
  BLACKLISTED: { label: 'Kara Liste', color: 'danger' },
};

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

export default function AdminCustomers() {
  const [customers, setCustomers] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [loading, setLoading] = useState(true);
  const [selectedCustomer, setSelectedCustomer] = useState(null);
  const [customerDetail, setCustomerDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [showStatusModal, setShowStatusModal] = useState(false);
  const [newStatus, setNewStatus] = useState('');
  const [statusNote, setStatusNote] = useState('');
  const [deleteTarget, setDeleteTarget] = useState(null);
  const { askCode, SecurityCodePrompt } = useSecurityCodePrompt();
  const toast = useAdminToast();
  const [searchParams, setSearchParams] = useSearchParams();

  const fetchCustomers = useCallback(() => {
    setLoading(true);
    const params = { page, size: 20, sortBy: 'createdAt', sortDir: 'desc' };
    if (search) params.search = search;
    if (statusFilter) params.status = statusFilter;
    axios
      .get('/api/admin/customers', { params })
      .then((r) => {
        setCustomers(r.data?.content || []);
        setTotalPages(r.data?.totalPages || 0);
        setTotalElements(r.data?.totalElements || 0);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [page, search, statusFilter]);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    fetchCustomers();
  }, [fetchCustomers]);

  const handleDeleteCustomer = async () => {
    if (!deleteTarget) return;
    const code = await askCode({
      description: `"${deleteTarget.name}" müşterisini silmek için güvenlik şifresini girin. Bu işlem geri alınamaz!`,
    });
    if (!code) {
      setDeleteTarget(null);
      return;
    }
    try {
      await axios.delete(`/api/admin/customers/${deleteTarget.id}`, {
        headers: { 'X-ADMIN-SECURITY-CODE': code },
      });
      setDeleteTarget(null);
      setSelectedCustomer(null);
      setCustomerDetail(null);
      fetchCustomers();
    } catch (e) {
      toast.error(e.response?.data?.message || 'Silinemedi.');
    }
  };

  const openDetail = async (id) => {
    setSelectedCustomer(id);
    setDetailLoading(true);
    try {
      const res = await axios.get(`/api/admin/customers/${id}`);
      setCustomerDetail(res.data);
    } catch (e) {
      toast.error('İşlem başarısız.');
    } finally {
      setDetailLoading(false);
    }
  };

  // Deep-link: /admin/customers?customerId=123 auto-opens that customer (e.g. from the Returns page).
  useEffect(() => {
    const cid = searchParams.get('customerId');
    if (cid) {
      openDetail(Number(cid));
      searchParams.delete('customerId');
      setSearchParams(searchParams, { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const updateCustomerStatus = async () => {
    try {
      await axios.put(`/api/admin/customers/${selectedCustomer}/status`, {
        status: newStatus,
        note: statusNote,
      });
      setShowStatusModal(false);
      setStatusNote('');
      openDetail(selectedCustomer);
      fetchCustomers();
    } catch (e) {
      toast.error(e.response?.data?.message || 'Hata oluştu');
    }
  };

  return (
    <div>
      {SecurityCodePrompt}
      <ConfirmModal
        show={!!deleteTarget}
        title="Müşteriyi Sil"
        message={`"${deleteTarget?.name}" müşterisini ve tüm verilerini (adresler, favoriler) kalıcı olarak silmek istediğinize emin misiniz? Bu işlem geri alınamaz!`}
        icon="trash"
        confirmText="Kalıcı Olarak Sil"
        confirmVariant="danger"
        onConfirm={handleDeleteCustomer}
        onCancel={() => setDeleteTarget(null)}
      />
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="mb-1">Müşteriler</h2>
          <small className="text-muted">{totalElements} müşteri</small>
        </div>
      </div>

      {/* Search & Filters */}
      <div className="card border-0 shadow-sm mb-4">
        <div className="card-body py-3">
          <div className="row g-3 align-items-center">
            <div className="col-md-4">
              <input
                className="form-control"
                placeholder="İsim, e-posta veya telefon ara..."
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  setPage(0);
                }}
              />
            </div>
            <div className="col-md-8">
              <div className="d-flex gap-2 flex-wrap">
                <span className="text-muted small fw-medium align-self-center">Durum:</span>
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
                      setStatusFilter(key);
                      setPage(0);
                    }}
                  >
                    {cfg.label}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="card border-0 shadow-sm">
        <div className="table-responsive">
          <table className="table table-hover align-middle mb-0">
            <thead className="table-light">
              <tr>
                <th>Müşteri</th>
                <th>Durum</th>
                <th>E-posta Doğrulama</th>
                <th>Sipariş</th>
                <th>Son Giriş</th>
                <th>Kayıt</th>
                <th style={{ width: 80 }}></th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={7} className="text-center py-5">
                    <div className="spinner-border spinner-border-sm" />
                  </td>
                </tr>
              ) : customers.length === 0 ? (
                <tr>
                  <td colSpan={7} className="text-center py-5 text-muted">
                    Müşteri bulunamadı
                  </td>
                </tr>
              ) : (
                customers.map((c) => (
                  <tr key={c.id} style={{ cursor: 'pointer' }} onClick={() => openDetail(c.id)}>
                    <td>
                      <div className="fw-medium">{c.fullName}</div>
                      <small className="text-muted">{c.email}</small>
                      {c.phone && <small className="text-muted d-block">{c.phone}</small>}
                    </td>
                    <td>
                      <span className={`badge bg-${STATUS_CONFIG[c.status]?.color || 'secondary'}`}>
                        {STATUS_CONFIG[c.status]?.label || c.status}
                      </span>
                    </td>
                    <td>
                      {c.emailVerified ? (
                        <i className="fas fa-check-circle text-success" />
                      ) : (
                        <i className="fas fa-times-circle text-danger" />
                      )}
                    </td>
                    <td>
                      <span className="badge bg-light text-dark">{c.orderCount || 0}</span>
                    </td>
                    <td>
                      <small className="text-muted">{formatDate(c.lastLoginAt)}</small>
                    </td>
                    <td>
                      <small className="text-muted">{formatDate(c.createdAt)}</small>
                    </td>
                    <td>
                      <button
                        className="btn btn-sm btn-outline-primary"
                        onClick={(e) => {
                          e.stopPropagation();
                          openDetail(c.id);
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
      {selectedCustomer && (
        <div
          className="modal show d-block"
          style={{ background: 'rgba(0,0,0,0.5)', zIndex: 3000 }}
          onClick={() => {
            setSelectedCustomer(null);
            setCustomerDetail(null);
          }}
        >
          <div className="modal-dialog modal-lg modal-dialog-scrollable" onClick={(e) => e.stopPropagation()}>
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">Müşteri Detay</h5>
                <button
                  className="btn-close"
                  onClick={() => {
                    setSelectedCustomer(null);
                    setCustomerDetail(null);
                  }}
                />
              </div>
              <div className="modal-body">
                {detailLoading ? (
                  <div className="text-center py-5">
                    <div className="spinner-border" />
                  </div>
                ) : customerDetail ? (
                  <div className="row g-4">
                    <div className="col-md-6">
                      <div className="card mb-3">
                        <div className="card-header bg-transparent d-flex justify-content-between align-items-center">
                          <h6 className="mb-0">Profil Bilgileri</h6>
                          <span className={`badge bg-${STATUS_CONFIG[customerDetail.status]?.color}`}>
                            {STATUS_CONFIG[customerDetail.status]?.label}
                          </span>
                        </div>
                        <div className="card-body">
                          <table className="table table-sm mb-0">
                            <tbody>
                              <tr>
                                <td className="text-muted">Ad Soyad</td>
                                <td className="fw-medium">{customerDetail.fullName}</td>
                              </tr>
                              <tr>
                                <td className="text-muted">E-posta</td>
                                <td>
                                  {customerDetail.email}{' '}
                                  {customerDetail.emailVerified ? (
                                    <i className="fas fa-check-circle text-success ms-1" />
                                  ) : (
                                    <i className="fas fa-times-circle text-danger ms-1" />
                                  )}
                                </td>
                              </tr>
                              <tr>
                                <td className="text-muted">Telefon</td>
                                <td>{customerDetail.phone || '—'}</td>
                              </tr>
                              <tr>
                                <td className="text-muted">TC Kimlik</td>
                                <td>{customerDetail.tcKimlikNo || '—'}</td>
                              </tr>
                              <tr>
                                <td className="text-muted">Sipariş Sayısı</td>
                                <td>
                                  <strong>{customerDetail.orderCount || 0}</strong>
                                </td>
                              </tr>
                              <tr>
                                <td className="text-muted">KVKK Onay</td>
                                <td>
                                  {customerDetail.kvkkConsent ? (
                                    <span className="text-success">Evet</span>
                                  ) : (
                                    <span className="text-danger">Hayır</span>
                                  )}
                                </td>
                              </tr>
                              <tr>
                                <td className="text-muted">Pazarlama</td>
                                <td>
                                  {customerDetail.marketingConsent ? (
                                    <span className="text-success">Kabul</span>
                                  ) : (
                                    <span className="text-muted">Red</span>
                                  )}
                                </td>
                              </tr>
                              <tr>
                                <td className="text-muted">Kayıt</td>
                                <td>{formatDate(customerDetail.createdAt)}</td>
                              </tr>
                              <tr>
                                <td className="text-muted">Son Giriş</td>
                                <td>{formatDate(customerDetail.lastLoginAt)}</td>
                              </tr>
                              <tr>
                                <td className="text-muted">Son IP</td>
                                <td>
                                  <code className="small">{customerDetail.lastLoginIp || '—'}</code>
                                </td>
                              </tr>
                            </tbody>
                          </table>
                        </div>
                      </div>

                      {/* Status Change */}
                      <div className="card">
                        <div className="card-header bg-transparent">
                          <h6 className="mb-0">Durum Yönetimi</h6>
                        </div>
                        <div className="card-body">
                          {customerDetail.statusNote && (
                            <div className="alert alert-light small">{customerDetail.statusNote}</div>
                          )}
                          <div className="d-flex gap-2">
                            <button
                              className="btn btn-outline-primary btn-sm"
                              onClick={() => {
                                setNewStatus(customerDetail.status);
                                setShowStatusModal(true);
                              }}
                            >
                              <i className="fas fa-edit me-1" />
                              Durumu Değiştir
                            </button>
                            <button
                              className="btn btn-outline-danger btn-sm"
                              onClick={() =>
                                setDeleteTarget({
                                  id: customerDetail.id,
                                  name: customerDetail.firstName + ' ' + customerDetail.lastName,
                                })
                              }
                            >
                              <i className="fas fa-trash me-1" />
                              Müşteriyi Sil
                            </button>
                          </div>
                        </div>
                      </div>
                    </div>

                    <div className="col-md-6">
                      {/* Addresses */}
                      <div className="card">
                        <div className="card-header bg-transparent">
                          <h6 className="mb-0">Adresler ({customerDetail.addresses?.length || 0})</h6>
                        </div>
                        <div className="card-body">
                          {customerDetail.addresses?.length > 0 ? (
                            customerDetail.addresses.map((addr, i) => (
                              <div key={i} className={`${i > 0 ? 'mt-3 pt-3 border-top' : ''}`}>
                                <div className="d-flex justify-content-between">
                                  <strong className="small">{addr.title}</strong>
                                  {addr.isDefault && <span className="badge bg-primary">Varsayılan</span>}
                                </div>
                                <small className="text-muted d-block">{addr.fullName}</small>
                                <small className="text-muted d-block">{addr.phone}</small>
                                <small className="text-muted d-block">{addr.addressLine}</small>
                                <small className="text-muted">
                                  {addr.district} / {addr.city}
                                </small>
                              </div>
                            ))
                          ) : (
                            <p className="text-muted small mb-0">Kayıtlı adres yok</p>
                          )}
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
                <h5 className="modal-title">Müşteri Durumu</h5>
                <button className="btn-close" onClick={() => setShowStatusModal(false)} />
              </div>
              <div className="modal-body">
                <div className="mb-3">
                  <label className="form-label">Yeni Durum</label>
                  <select
                    className="form-select"
                    value={newStatus}
                    onChange={(e) => setNewStatus(e.target.value)}
                  >
                    {Object.entries(STATUS_CONFIG).map(([k, v]) => (
                      <option key={k} value={k}>
                        {v.label}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="mb-3">
                  <label className="form-label">Not</label>
                  <textarea
                    className="form-control"
                    rows={3}
                    value={statusNote}
                    onChange={(e) => setStatusNote(e.target.value)}
                    placeholder="Durum değişikliği sebebi..."
                  />
                </div>
                {newStatus === 'BLACKLISTED' && (
                  <div className="alert alert-danger small">
                    <i className="fas fa-exclamation-triangle me-2" />
                    Kara listeye alınan müşterinin tüm oturumları sonlandırılacaktır.
                  </div>
                )}
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setShowStatusModal(false)}>
                  İptal
                </button>
                <button className="btn btn-primary" onClick={updateCustomerStatus}>
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
