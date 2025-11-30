import React, { useEffect, useState } from 'react';
import axios from 'axios';
import ConfirmModal from '../components/ConfirmModal';

const StockImportHistory = () => {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showFailedRowsModal, setShowFailedRowsModal] = useState(false);
  const [failedRows, setFailedRows] = useState([]);
  const [selectedItems, setSelectedItems] = useState([]);
  const [confirmModal, setConfirmModal] = useState({ show: false, title: '', message: '', onConfirm: null });

  const fetchData = async () => {
    try {
      setLoading(true);
      const res = await axios.get('/api/stock-imports');
      const list = Array.isArray(res.data) ? res.data : [];
      setItems(list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)));
      setError(null);
    } catch (e) {
      setError('Stok aktarım geçmişi yüklenirken hata oluştu');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const badgeClass = (status) => {
    const s = (status || '').toUpperCase();
    if (s === 'BAŞARILI' || s === 'BASARILI' || s === 'SUCCESS') return 'success';
    if (s === 'BAŞARISIZ' || s === 'BASARISIZ' || s === 'FAILED') return 'danger';
    if (s === 'KISMEN' || s === 'PARTIAL') return 'warning';
    return 'secondary';
  };

  const trStatus = (status) => {
    const s = (status || '').toUpperCase();
    if (s === 'SUCCESS') return 'BAŞARILI';
    if (s === 'FAILED') return 'BAŞARISIZ';
    if (s === 'PARTIAL') return 'KISMEN';
    return status || '—';
  };

  const toggleItemSelection = (id) => {
    setSelectedItems(prev =>
      prev.includes(id) ? prev.filter(itemId => itemId !== id) : [...prev, id]
    );
  };

  const toggleSelectAll = () => {
    if (selectedItems.length === items.length) {
      setSelectedItems([]);
    } else {
      setSelectedItems(items.map(item => item.id));
    }
  };

  const clearSelection = () => {
    setSelectedItems([]);
  };

  const handleDelete = (id) => {
    setConfirmModal({
      show: true,
      title: 'Kaydı Sil',
      message: 'Bu aktarım geçmişi kaydını silmek istediğinizden emin misiniz?',
      onConfirm: async () => {
        try {
          await axios.delete(`/api/stock-imports/${id}`);
          await fetchData();
          setSelectedItems(prev => prev.filter(itemId => itemId !== id));
          setConfirmModal({ show: false, title: '', message: '', onConfirm: null });
        } catch (e) {
          setError('Kayıt silinirken hata oluştu: ' + (e.response?.data?.message || e.message));
          setConfirmModal({ show: false, title: '', message: '', onConfirm: null });
        }
      }
    });
  };

  const handleBatchDelete = () => {
    if (selectedItems.length === 0) return;
    
    setConfirmModal({
      show: true,
      title: 'Toplu Silme',
      message: `${selectedItems.length} adet aktarım geçmişi kaydını silmek istediğinizden emin misiniz?`,
      onConfirm: async () => {
        try {
          const response = await axios.delete('/api/stock-imports/bulk', { data: selectedItems });
          const result = response.data;
          if (result.errorCount > 0) {
            setError(`${result.successCount} kayıt silindi, ${result.errorCount} kayıt silinemedi.`);
          } else {
            setError(null);
          }
          await fetchData();
          setSelectedItems([]);
          setConfirmModal({ show: false, title: '', message: '', onConfirm: null });
        } catch (e) {
          setError('Kayıtlar silinirken hata oluştu: ' + (e.response?.data?.message || e.message));
          setConfirmModal({ show: false, title: '', message: '', onConfirm: null });
        }
      }
    });
  };

  const selectedCount = selectedItems.length;
  const areAllSelected = items.length > 0 && selectedItems.length === items.length;

  if (loading) {
    return (
      <div className="text-center">
        <div className="spinner-border" role="status">
          <span className="visually-hidden">Yükleniyor...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="alert alert-danger" role="alert">{error}</div>
    );
  }

  return (
    <div>
      <style>{`
        .import-history-card {
          border-radius: 18px;
        }
        .import-history-card .stat-pill {
          border-radius: 999px;
          padding: 0.35rem 0.85rem;
          font-size: 0.78rem;
        }
      `}</style>
      <div className="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-2">
        <h2>Excel Stok Aktarım Geçmişi</h2>
        {selectedCount > 0 && (
          <div className="d-flex gap-2 align-items-center">
            <span className="badge bg-primary">{selectedCount} seçili</span>
            <button
              className="btn btn-sm btn-outline-secondary"
              onClick={clearSelection}
            >
              Seçimi Temizle
            </button>
            <button
              className="btn btn-sm btn-danger"
              onClick={handleBatchDelete}
            >
              <i className="fas fa-trash me-1"></i>
              Seçilileri Sil
            </button>
          </div>
        )}
      </div>
      <div className="card">
        <div className="card-body">
          {/* Desktop / Large Tablet Table View */}
          <div className="table-responsive d-none d-lg-block">
            <table className="table table-hover align-middle">
              <thead>
                <tr>
                  <th style={{ width: '40px' }}>
                    <div className="form-check">
                      <input
                        className="form-check-input"
                        type="checkbox"
                        checked={areAllSelected}
                        onChange={toggleSelectAll}
                        disabled={items.length === 0}
                        aria-label="Tümünü seç"
                      />
                    </div>
                  </th>
                  <th>Tarih</th>
                  <th>Depo</th>
                  <th>Dosya</th>
                  <th>Satır</th>
                  <th>Ürün (Yeni)</th>
                  <th>Stok (Yeni/Güncellenen)</th>
                  <th>Durum</th>
                  <th className="text-end">İşlemler</th>
                </tr>
              </thead>
              <tbody>
                {items.map(item => {
                  const isSelected = selectedItems.includes(item.id);
                  return (
                  <tr key={item.id} className={isSelected ? 'table-active' : ''}>
                    <td>
                      <div className="form-check">
                        <input
                          className="form-check-input"
                          type="checkbox"
                          checked={isSelected}
                          onChange={() => toggleItemSelection(item.id)}
                          aria-label={`${item.originalFilename} seç`}
                        />
                      </div>
                    </td>
                    <td>{item.createdAt ? new Date(item.createdAt).toLocaleString('tr-TR') : '-'}</td>
                    <td>{item.warehouseName || item.warehouse?.name || '-'}</td>
                    <td>{item.originalFilename}</td>
                    <td>{item.totalRows ?? '-'}</td>
                    <td>
                      <span className="badge bg-success">{item.createdProducts ?? 0}</span>
                    </td>
                    <td>
                      <span className="badge bg-success me-1">{item.createdStocks ?? 0}</span>
                      <span className="badge bg-info">{item.updatedStocks ?? 0}</span>
                    </td>
                    <td>
                      <span className={`badge bg-${badgeClass(item.status)}`}>{trStatus(item.status)}</span>
                    </td>
                    <td className="text-end">
                      <div className="d-flex gap-2 justify-content-end">
                        {(item.status === 'KISMEN' || item.status === 'PARTIAL' || item.status === 'BAŞARISIZ' || item.status === 'FAILED') && item.failedRows && (
                          <button 
                            className="btn btn-sm btn-outline-warning" 
                            onClick={() => {
                              try {
                                const parsed = JSON.parse(item.failedRows);
                                setFailedRows(Array.isArray(parsed) ? parsed : []);
                                setShowFailedRowsModal(true);
                              } catch (e) {
                                console.error('Failed rows parse error:', e);
                              }
                            }}
                          >
                            <i className="fas fa-exclamation-triangle me-1"></i>
                            Başarısız Satırlar
                          </button>
                        )}
                        <button 
                          className="btn btn-sm btn-outline-secondary" 
                          onClick={async () => {
                            try {
                              const res = await axios.get(`/api/stock-imports/${item.id}/file`, { responseType: 'blob' });
                              const url = window.URL.createObjectURL(new Blob([res.data]));
                              const link = document.createElement('a');
                              link.href = url;
                              link.setAttribute('download', item.originalFilename || 'stok_import.xlsx');
                              document.body.appendChild(link);
                              link.click();
                              link.remove();
                              window.URL.revokeObjectURL(url);
                            } catch (e) {
                              console.error('Dosya indirme hatası:', e);
                            }
                          }}
                        >
                          <i className="fas fa-download me-1"></i>
                          İndir
                        </button>
                        <button 
                          className="btn btn-sm btn-outline-danger" 
                          onClick={() => handleDelete(item.id)}
                        >
                          <i className="fas fa-trash me-1"></i>
                          Sil
                        </button>
                      </div>
                    </td>
                  </tr>
                );
                })}
              </tbody>
            </table>
          </div>

          {/* Mobile / Small-Medium Tablet Card View */}
          <div className="d-lg-none">
            {items.length === 0 ? (
              <div className="text-center text-muted py-4">
                Kayıt bulunamadı.
              </div>
            ) : (
              <div className="d-flex flex-column gap-3">
                {items.map(item => {
                  const isSelected = selectedItems.includes(item.id);
                  return (
                  <div key={item.id} className={`card import-history-card border shadow-sm ${isSelected ? 'border-primary bg-primary bg-opacity-10' : ''}`}>
                    <div className="card-body p-3">
                      <div className="d-flex justify-content-between align-items-start mb-2">
                        <div>
                          <div className="fw-bold">
                            {item.warehouseName || item.warehouse?.name || '-'}
                          </div>
                          <small className="text-muted d-block">
                            {item.createdAt ? new Date(item.createdAt).toLocaleString('tr-TR') : '-'}
                          </small>
                        </div>
                        <div className="d-flex align-items-center gap-2">
                          <div className="form-check">
                            <input
                              className="form-check-input"
                              type="checkbox"
                              checked={isSelected}
                              onChange={() => toggleItemSelection(item.id)}
                              aria-label={`${item.originalFilename} seç`}
                            />
                          </div>
                          <span className={`badge bg-${badgeClass(item.status)}`}>
                            {trStatus(item.status)}
                          </span>
                        </div>
                      </div>
                      <div className="mb-2">
                        <div className="small text-muted">Dosya</div>
                        <div className="text-truncate">{item.originalFilename}</div>
                      </div>
                      <div className="row g-2 mb-2">
                        <div className="col-4 d-flex">
                          <div className="stat-pill bg-light border text-center w-100">
                            <div className="text-muted small text-uppercase">Satır</div>
                            <div className="fw-bold">{item.totalRows ?? '-'}</div>
                          </div>
                        </div>
                        <div className="col-4 d-flex">
                          <div className="stat-pill bg-success bg-opacity-10 text-success w-100 text-center">
                            <div className="small text-uppercase">Yeni Ürün</div>
                            <div className="fw-bold">{item.createdProducts ?? 0}</div>
                          </div>
                        </div>
                        <div className="col-4 d-flex">
                          <div className="stat-pill bg-info bg-opacity-10 text-info w-100 text-center">
                            <div className="small text-uppercase">Stok</div>
                            <div className="fw-bold">
                              {(item.createdStocks ?? 0)}/{item.updatedStocks ?? 0}
                            </div>
                          </div>
                        </div>
                      </div>
                      <div className="d-flex flex-wrap gap-2 mt-2">
                        {(item.status === 'KISMEN' || item.status === 'PARTIAL' || item.status === 'BAŞARISIZ' || item.status === 'FAILED') && item.failedRows && (
                          <button 
                            className="btn btn-sm btn-outline-warning flex-fill" 
                            onClick={() => {
                              try {
                                const parsed = JSON.parse(item.failedRows);
                                setFailedRows(Array.isArray(parsed) ? parsed : []);
                                setShowFailedRowsModal(true);
                              } catch (e) {
                                console.error('Failed rows parse error:', e);
                              }
                            }}
                          >
                            <i className="fas fa-exclamation-triangle me-1"></i>
                            Başarısız Satırlar
                          </button>
                        )}
                        <button 
                          className="btn btn-sm btn-outline-secondary flex-fill" 
                          onClick={async () => {
                            try {
                              const res = await axios.get(`/api/stock-imports/${item.id}/file`, { responseType: 'blob' });
                              const url = window.URL.createObjectURL(new Blob([res.data]));
                              const link = document.createElement('a');
                              link.href = url;
                              link.setAttribute('download', item.originalFilename || 'stok_import.xlsx');
                              document.body.appendChild(link);
                              link.click();
                              link.remove();
                              window.URL.revokeObjectURL(url);
                            } catch (e) {
                              console.error('Dosya indirme hatası:', e);
                            }
                          }}
                        >
                          <i className="fas fa-download me-1"></i>
                          İndir
                        </button>
                        <button 
                          className="btn btn-sm btn-outline-danger flex-fill" 
                          onClick={() => handleDelete(item.id)}
                        >
                          <i className="fas fa-trash me-1"></i>
                          Sil
                        </button>
                      </div>
                    </div>
                  </div>
                );
                })}
              </div>
            )}
          </div>
        </div>
      </div>

      {showFailedRowsModal && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-lg">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">Başarısız Satırlar</h5>
                <button type="button" className="btn-close" onClick={() => setShowFailedRowsModal(false)}></button>
              </div>
              <div className="modal-body">
                {failedRows.length === 0 ? (
                  <p className="text-muted">Başarısız satır bulunamadı.</p>
                ) : (
                  <div className="table-responsive">
                    <table className="table table-sm table-hover">
                      <thead>
                        <tr>
                          <th>Satır No</th>
                          <th>Ürün Adı</th>
                          <th>Stok Kodu</th>
                          <th>Hata Sebebi</th>
                        </tr>
                      </thead>
                      <tbody>
                        {failedRows.map((row, idx) => (
                          <tr key={idx}>
                            <td>{row.rowNumber}</td>
                            <td>{row.productName || <span className="text-muted">—</span>}</td>
                            <td>{row.sku || <span className="text-muted">—</span>}</td>
                            <td>
                              <span className="text-danger small">{row.reason || 'Bilinmeyen hata'}</span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setShowFailedRowsModal(false)}>
                  Kapat
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      <ConfirmModal
        show={confirmModal.show}
        title={confirmModal.title}
        message={confirmModal.message}
        onConfirm={confirmModal.onConfirm}
        onCancel={() => setConfirmModal({ show: false, title: '', message: '', onConfirm: null })}
      />
    </div>
  );
};

export default StockImportHistory;


