import React, { useState, useEffect } from 'react';
import axios from 'axios';

/**
 * Stock settings modal - for managing consigned, reserved, and min stock levels
 */
const StockSettingsModal = ({ stock, onSuccess, onClose }) => {
  const role = (typeof window !== 'undefined' && localStorage.getItem('auth_role')) || 'ADMIN';
  const [settings, setSettings] = useState({
    consignedQuantity: 0,
    minStockLevel: 10
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  useEffect(() => {
    if (stock) {
      setSettings({
        consignedQuantity: stock.consignedQuantity || 0,
        minStockLevel: stock.minStockLevel || 10
      });
    }
  }, [stock]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setSettings(prev => ({
      ...prev,
      [name]: parseInt(value) || 0
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (role !== 'ADMIN') {
      setError('Bu işlem için yönetici yetkisi gereklidir');
      return;
    }

    // Check if any change was made
    const hasChanges = 
      settings.consignedQuantity !== (stock.consignedQuantity || 0) ||
      settings.minStockLevel !== (stock.minStockLevel || 10);

    if (!hasChanges) {
      setError('Herhangi bir değişiklik yapılmadı');
      return;
    }

    setLoading(true);
    setError(null);
    setSuccess(null);

    try {
      await axios.put(`/api/stocks/${stock.id}`, {
        consignedQuantity: settings.consignedQuantity,
        minStockLevel: settings.minStockLevel
      });

      setSuccess('✓ Ayarlar başarıyla güncellendi!');
      setTimeout(() => {
        onSuccess();
      }, 800);
    } catch (error) {
      console.error('Error updating stock settings:', error);
      const msg = error.response?.data?.message || error.response?.data || 'Güncelleme hatası';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const getStockStatus = () => {
    const available = (stock.quantity || 0) - (stock.reservedQuantity || 0) - settings.consignedQuantity;
    if (available <= 0) return { label: 'Stok Dışı', class: 'danger', icon: 'times-circle' };
    if (available <= settings.minStockLevel) return { label: 'Düşük Stok', class: 'warning', icon: 'exclamation-triangle' };
    return { label: 'Normal', class: 'success', icon: 'check-circle' };
  };

  const status = getStockStatus();

  return (
    <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
      <div className="modal-dialog modal-dialog-centered">
        <div className="modal-content">
          <div className="modal-header bg-primary text-white">
            <h5 className="modal-title">
              <i className="fas fa-cog me-2"></i>
              Stok Ayarları
            </h5>
            <button type="button" className="btn-close btn-close-white" onClick={onClose}></button>
          </div>

          <div className="modal-body">
            {/* Stock Info */}
            <div className="card mb-3 border-0 bg-light">
              <div className="card-body">
                <div className="d-flex justify-content-between align-items-start mb-2">
                  <div>
                    <div className="fw-bold fs-6">{stock.product.name}</div>
                    <small className="text-muted">
                      <i className="fas fa-warehouse me-1"></i>
                      {stock.warehouse.name}
                    </small>
                  </div>
                  <span className={`badge bg-${status.class}`}>
                    <i className={`fas fa-${status.icon} me-1`}></i>
                    {status.label}
                  </span>
                </div>
                
                <div className="row mt-3 text-center">
                  <div className="col-4">
                    <div className="fs-5 fw-bold">{stock.quantity}</div>
                    <small className="text-muted">Toplam</small>
                  </div>
                  <div className="col-4">
                    <div className="fs-5 fw-bold text-warning">{stock.reservedQuantity || 0}</div>
                    <small className="text-muted">Rezerve</small>
                  </div>
                  <div className="col-4">
                    <div className="fs-5 fw-bold text-success">
                      {(stock.quantity || 0) - (stock.reservedQuantity || 0) - settings.consignedQuantity}
                    </div>
                    <small className="text-muted">Kullanılabilir</small>
                  </div>
                </div>
              </div>
            </div>

            {role !== 'ADMIN' ? (
              <div className="alert alert-warning">
                <i className="fas fa-exclamation-triangle me-2"></i>
                Bu ayarları sadece yöneticiler değiştirebilir.
              </div>
            ) : (
              <form onSubmit={handleSubmit}>
                {error && (
                  <div className="alert alert-danger py-2" role="alert">
                    <i className="fas fa-exclamation-triangle me-2"></i>
                    {error}
                  </div>
                )}

                {success && (
                  <div className="alert alert-success py-2" role="alert">
                    <i className="fas fa-check-circle me-2"></i>
                    {success}
                  </div>
                )}

                <div className="mb-3">
                  <label htmlFor="consignedQuantity" className="form-label fw-bold">
                    <i className="fas fa-handshake me-1 text-info"></i>
                    Emanet Miktarı
                  </label>
                  <input
                    type="number"
                    min="0"
                    max={stock.quantity}
                    className="form-control form-control-lg"
                    id="consignedQuantity"
                    name="consignedQuantity"
                    value={settings.consignedQuantity}
                    onChange={handleChange}
                    inputMode="numeric"
                  />
                  <small className="text-muted">
                    Emanet olarak verilen stok miktarı (kullanılamaz)
                  </small>
                </div>

                <div className="mb-3">
                  <label htmlFor="minStockLevel" className="form-label fw-bold">
                    <i className="fas fa-exclamation-circle me-1 text-warning"></i>
                    Minimum Stok Seviyesi
                  </label>
                  <input
                    type="number"
                    min="0"
                    className="form-control form-control-lg"
                    id="minStockLevel"
                    name="minStockLevel"
                    value={settings.minStockLevel}
                    onChange={handleChange}
                    inputMode="numeric"
                  />
                  <small className="text-muted">
                    Bu seviyenin altına düşünce "Düşük Stok" uyarısı gösterilir
                  </small>
                </div>

                {/* Preview */}
                <div className="alert alert-light border">
                  <div className="fw-bold mb-2">
                    <i className="fas fa-eye me-1"></i>
                    Önizleme:
                  </div>
                  <div className="small">
                    <div className="d-flex justify-content-between mb-1">
                      <span>Toplam Stok:</span>
                      <strong>{stock.quantity}</strong>
                    </div>
                    <div className="d-flex justify-content-between mb-1">
                      <span>Rezerve:</span>
                      <strong className="text-warning">-{stock.reservedQuantity || 0}</strong>
                    </div>
                    <div className="d-flex justify-content-between mb-1">
                      <span>Emanet:</span>
                      <strong className="text-info">-{settings.consignedQuantity}</strong>
                    </div>
                    <hr className="my-2" />
                    <div className="d-flex justify-content-between">
                      <span className="fw-bold">Kullanılabilir:</span>
                      <strong className={
                        (stock.quantity - (stock.reservedQuantity || 0) - settings.consignedQuantity) <= 0 ? 'text-danger' :
                        (stock.quantity - (stock.reservedQuantity || 0) - settings.consignedQuantity) <= settings.minStockLevel ? 'text-warning' :
                        'text-success'
                      }>
                        {(stock.quantity || 0) - (stock.reservedQuantity || 0) - settings.consignedQuantity}
                      </strong>
                    </div>
                  </div>
                </div>

                <div className="row g-2">
                  <div className="col-6">
                    <button
                      type="button"
                      className="btn btn-secondary w-100"
                      onClick={onClose}
                      disabled={loading}
                    >
                      <i className="fas fa-times me-1"></i>
                      İptal
                    </button>
                  </div>
                  <div className="col-6">
                    <button
                      type="submit"
                      className="btn btn-primary w-100"
                      disabled={loading || success}
                    >
                      {loading ? (
                        <>
                          <span className="spinner-border spinner-border-sm me-1"></span>
                          Kaydediliyor...
                        </>
                      ) : (
                        <>
                          <i className="fas fa-save me-1"></i>
                          Kaydet
                        </>
                      )}
                    </button>
                  </div>
                </div>
              </form>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default StockSettingsModal;



