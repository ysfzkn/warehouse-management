import React, { useState } from 'react';
import axios from 'axios';

const StockAdjustmentModal = ({ stock, onSuccess, onClose }) => {
  const [adjustment, setAdjustment] = useState({
    type: 'add', // 'add' or 'remove'
    quantity: '',
    reason: '',
    affectConsigned: false
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setAdjustment(prev => ({
      ...prev,
      [name]: value
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!adjustment.quantity || parseInt(adjustment.quantity) <= 0) {
      setError('Geçerli bir miktar giriniz');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const quantity = parseInt(adjustment.quantity);

      if (!stock?.id) {
        throw new Error('Geçersiz stok ID');
      }

      if (adjustment.affectConsigned) {
        const newConsigned = Math.max(0, (stock.consignedQuantity || 0) + (adjustment.type === 'add' ? quantity : -quantity));
        await axios.put(`/api/stocks/${stock.id}`, { consignedQuantity: newConsigned });
      } else {
        if (adjustment.type === 'add') {
          await axios.put(`/api/stocks/${stock.id}/add`, null, { params: { quantity } });
        } else {
          await axios.put(`/api/stocks/${stock.id}/remove`, null, { params: { quantity } });
        }
      }

      onSuccess();
    } catch (error) {
      console.error('Error adjusting stock:', error);
      setError(error.response?.data || 'Stok ayarlanırken hata oluştu');
    } finally {
      setLoading(false);
    }
  };

  const getStockStatus = (stock) => {
    const available = (stock.quantity || 0) - (stock.reservedQuantity || 0) - (stock.consignedQuantity || 0);
    if (available <= 0) return { status: 'out', label: 'Stok Dışı', class: 'danger' };
    if (available <= stock.minStockLevel) return { status: 'low', label: 'Düşük Stok', class: 'warning' };
    return { status: 'normal', label: 'Normal', class: 'success' };
  };

  const stockStatus = getStockStatus(stock);

  return (
    <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
      <div className="modal-dialog">
        <div className="modal-content">
          <div className="modal-header">
            <h5 className="modal-title">Stok Ayarlama</h5>
            <button type="button" className="btn-close" onClick={onClose}></button>
          </div>
          <div className="modal-body">
            {/* Stock Info */}
            <div className="card mb-3">
              <div className="card-body">
                <h6 className="card-title">Stok Bilgileri</h6>
                <div className="row">
                  <div className="col-sm-4">
                    <strong>Ürün:</strong><br />
                    {stock.product.name}
                  </div>
                  <div className="col-sm-4">
                    <strong>Depo:</strong><br />
                    {stock.warehouse.name}
                  </div>
                  <div className="col-sm-4">
                    <strong>Durum:</strong><br />
                    <span className={`badge bg-${stockStatus.class}`}>
                      {stockStatus.label}
                    </span>
                  </div>
                </div>
                <div className="row mt-2">
                  <div className="col-sm-4">
                    <strong>Mevcut Miktar:</strong><br />
                    {stock.quantity}
                  </div>
                  <div className="col-sm-4">
                    <strong>Kullanılabilir:</strong><br />
                    <span className={stock.availableQuantity < stock.minStockLevel ? 'text-danger' : 'text-success'}>
                      {(stock.quantity || 0) - (stock.reservedQuantity || 0) - (stock.consignedQuantity || 0)}
                    </span>
                  </div>
                  <div className="col-sm-4">
                    <strong>Min. Stok:</strong><br />
                    {stock.minStockLevel}
                  </div>
                </div>
                <div className="row mt-2">
                  <div className="col-sm-4">
                    <strong>Rezerve:</strong><br />
                    {stock.reservedQuantity || 0}
                  </div>
                  <div className="col-sm-4">
                    <strong>Emanet:</strong><br />
                    {stock.consignedQuantity || 0}
                  </div>
                </div>
              </div>
            </div>

            {/* Adjustment Form */}
            <form onSubmit={handleSubmit}>
              {error && (
                <div className="alert alert-danger" role="alert">
                  {error}
                </div>
              )}

              <div className="row">
                <div className="col-md-6">
                  <div className="mb-3">
                    <label className="form-label">İşlem Türü</label>
                    <div className="form-check">
                      <input
                        className="form-check-input"
                        type="radio"
                        name="type"
                        id="add"
                        value="add"
                        checked={adjustment.type === 'add'}
                        onChange={handleChange}
                      />
                      <label className="form-check-label" htmlFor="add">
                        Stok Ekle
                      </label>
                    </div>
                    <div className="form-check">
                      <input
                        className="form-check-input"
                        type="radio"
                        name="type"
                        id="remove"
                        value="remove"
                        checked={adjustment.type === 'remove'}
                        onChange={handleChange}
                      />
                      <label className="form-check-label" htmlFor="remove">
                        Stok Çıkar
                      </label>
                    </div>
                  </div>
                </div>

                <div className="col-md-6">
                  <div className="mb-3">
                    <label htmlFor="quantity" className="form-label">
                      Miktar <span className="text-danger">*</span>
                    </label>
                    <input
                      type="number"
                      min="1"
                      className="form-control"
                      id="quantity"
                      name="quantity"
                      value={adjustment.quantity}
                      onChange={handleChange}
                      placeholder="10"
                      required
                    />
                  </div>
                </div>
              </div>

              <div className="mb-3">
              <div className="form-check">
                <input
                  className="form-check-input"
                  type="checkbox"
                  id="affectConsigned"
                  checked={adjustment.affectConsigned}
                  onChange={(e) => setAdjustment(prev => ({ ...prev, affectConsigned: e.target.checked }))}
                />
                <label className="form-check-label" htmlFor="affectConsigned">
                  Emanet miktarı değiştir
                </label>
              </div>
            </div>

            <div className="mb-3">
                <label htmlFor="reason" className="form-label">
                  Açıklama (İsteğe bağlı)
                </label>
                <textarea
                  className="form-control"
                  id="reason"
                  name="reason"
                  rows="2"
                  value={adjustment.reason}
                  onChange={handleChange}
                  placeholder="Stok ayarlaması nedeni..."
                />
              </div>

              <div className="d-flex justify-content-end gap-2">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={onClose}
                  disabled={loading}
                >
                  İptal
                </button>
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={loading}
                >
                  {loading ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                      İşleniyor...
                    </>
                  ) : (
                    <>
                      <i className="fas fa-save me-2"></i>
                      Uygula
                    </>
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
};

export default StockAdjustmentModal;
