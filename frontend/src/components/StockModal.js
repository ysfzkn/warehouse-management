import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';

const StockModal = ({ warehouse, onClose }) => {
  const [stocks, setStocks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchStocks = useCallback(async () => {
    if (!warehouse?.id) return;
    try {
      setLoading(true);
      const response = await axios.get(`/api/stocks/warehouse/${warehouse.id}`);
      setStocks(response.data);
    } catch (error) {
      console.error('Error fetching stocks:', error);
      setError('Stok bilgileri yüklenirken hata oluştu');
    } finally {
      setLoading(false);
    }
  }, [warehouse?.id]);

  useEffect(() => {
    if (warehouse) {
      fetchStocks();
    }
  }, [warehouse?.id, fetchStocks]);

  const getStockStatus = (stock) => {
    if (stock.quantity === 0) return { status: 'out', label: 'Stok Dışı', class: 'danger' };
    if (stock.quantity <= stock.minStockLevel) return { status: 'low', label: 'Düşük Stok', class: 'warning' };
    return { status: 'normal', label: 'Normal', class: 'success' };
  };

  if (loading) {
    return (
      <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
        <div className="modal-dialog modal-xl">
          <div className="modal-content">
            <div className="modal-header">
              <h5 className="modal-title">{warehouse?.name} - Stok Bilgileri</h5>
              <button type="button" className="btn-close" onClick={onClose}></button>
            </div>
            <div className="modal-body text-center">
              <div className="spinner-border" role="status">
                <span className="visually-hidden">Yükleniyor...</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
        <div className="modal-dialog modal-xl">
          <div className="modal-content">
            <div className="modal-header">
              <h5 className="modal-title">{warehouse?.name} - Stok Bilgileri</h5>
              <button type="button" className="btn-close" onClick={onClose}></button>
            </div>
            <div className="modal-body">
              <div className="alert alert-danger" role="alert">
                {error}
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
      <div className="modal-dialog modal-xl">
        <div className="modal-content">
          <div className="modal-header">
            <h5 className="modal-title">{warehouse?.name} - Stok Bilgileri</h5>
            <button type="button" className="btn-close" onClick={onClose}></button>
          </div>
          <div className="modal-body">
            {stocks.length === 0 ? (
              <div className="text-center py-4">
                <i className="fas fa-cubes fa-3x text-muted mb-3"></i>
                <h5 className="text-muted">Bu depoda stok bulunmuyor</h5>
              </div>
            ) : (
              <div className="table-responsive">
                <table className="table table-striped table-hover">
                  <thead>
                    <tr>
                      <th>Ürün</th>
                      <th>SKU</th>
                      <th>Kategori</th>
                      <th>Miktar</th>
                      <th>Kullanılabilir</th>
                      <th>Min. Stok</th>
                      <th>Durum</th>
                      <th>Son Güncelleme</th>
                    </tr>
                  </thead>
                  <tbody>
                    {stocks.map((stock) => {
                      const stockStatus = getStockStatus(stock);
                      return (
                        <tr key={stock.id}>
                          <td>{stock.product.name}</td>
                          <td>{stock.product.sku}</td>
                          <td>{stock.product.category?.name}</td>
                          <td>
                            <span className="fw-bold">{stock.quantity}</span>
                          </td>
                          <td>
                            <span className={stock.availableQuantity < stock.minStockLevel ? 'text-danger' : 'text-success'}>
                              {stock.availableQuantity}
                            </span>
                          </td>
                          <td>{stock.minStockLevel}</td>
                          <td>
                            <span className={`badge bg-${stockStatus.class}`}>
                              {stockStatus.label}
                            </span>
                          </td>
                          <td>
                            {new Date(stock.lastUpdated).toLocaleDateString('tr-TR')}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
          <div className="modal-footer">
            <button type="button" className="btn btn-secondary" onClick={onClose}>
              Kapat
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default StockModal;
