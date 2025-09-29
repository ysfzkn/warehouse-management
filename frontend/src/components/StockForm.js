import React, { useState, useEffect } from 'react';
import axios from 'axios';

const StockForm = ({ products, warehouses, onSuccess, onCancel }) => {
  const [formData, setFormData] = useState({
    productId: '',
    warehouseId: '',
    quantity: '',
    minStockLevel: '',
    reservedQuantity: '0'
  });
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});

  useEffect(() => {
    // Set default values if available
    if (products.length > 0) {
      setFormData(prev => ({ ...prev, productId: products[0].id }));
    }
    if (warehouses.length > 0) {
      setFormData(prev => ({ ...prev, warehouseId: warehouses[0].id }));
    }
  }, [products, warehouses]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));

    // Clear error when user starts typing
    if (errors[name]) {
      setErrors(prev => ({
        ...prev,
        [name]: ''
      }));
    }
  };

  const validateForm = () => {
    const newErrors = {};

    if (!formData.productId) {
      newErrors.productId = 'Ürün seçiniz';
    }

    if (!formData.warehouseId) {
      newErrors.warehouseId = 'Depo seçiniz';
    }

    if (!formData.quantity || parseInt(formData.quantity) < 0) {
      newErrors.quantity = 'Geçerli bir miktar giriniz';
    }

    if (!formData.minStockLevel || parseInt(formData.minStockLevel) < 0) {
      newErrors.minStockLevel = 'Geçerli bir minimum stok seviyesi giriniz';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    setLoading(true);

    try {
      const dataToSend = {
        product: { id: parseInt(formData.productId) },
        warehouse: { id: parseInt(formData.warehouseId) },
        quantity: parseInt(formData.quantity),
        minStockLevel: parseInt(formData.minStockLevel),
        reservedQuantity: parseInt(formData.reservedQuantity || 0)
      };

      await axios.post('/api/stocks', dataToSend);
      onSuccess();
    } catch (error) {
      console.error('Error saving stock:', error);
      if (error.response?.data) {
        setErrors({ general: error.response.data });
      } else {
        setErrors({ general: 'Stok kaydedilirken hata oluştu' });
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      {errors.general && (
        <div className="alert alert-danger" role="alert">
          {errors.general}
        </div>
      )}

      <div className="row">
        <div className="col-md-6">
          <div className="mb-3">
            <label htmlFor="productId" className="form-label">
              Ürün <span className="text-danger">*</span>
            </label>
            <select
              className={`form-select ${errors.productId ? 'is-invalid' : ''}`}
              id="productId"
              name="productId"
              value={formData.productId}
              onChange={handleChange}
              required
            >
              <option value="">Ürün seçin</option>
              {products.map((product) => (
                <option key={product.id} value={product.id}>
                  {product.name} ({product.sku})
                </option>
              ))}
            </select>
            {errors.productId && <div className="invalid-feedback">{errors.productId}</div>}
          </div>
        </div>

        <div className="col-md-6">
          <div className="mb-3">
            <label htmlFor="warehouseId" className="form-label">
              Depo <span className="text-danger">*</span>
            </label>
            <select
              className={`form-select ${errors.warehouseId ? 'is-invalid' : ''}`}
              id="warehouseId"
              name="warehouseId"
              value={formData.warehouseId}
              onChange={handleChange}
              required
            >
              <option value="">Depo seçin</option>
              {warehouses.map((warehouse) => (
                <option key={warehouse.id} value={warehouse.id}>
                  {warehouse.name} - {warehouse.location}
                </option>
              ))}
            </select>
            {errors.warehouseId && <div className="invalid-feedback">{errors.warehouseId}</div>}
          </div>
        </div>
      </div>

      <div className="row">
        <div className="col-md-4">
          <div className="mb-3">
            <label htmlFor="quantity" className="form-label">
              Miktar <span className="text-danger">*</span>
            </label>
            <input
              type="number"
              min="0"
              className={`form-control ${errors.quantity ? 'is-invalid' : ''}`}
              id="quantity"
              name="quantity"
              value={formData.quantity}
              onChange={handleChange}
              placeholder="100"
              required
            />
            {errors.quantity && <div className="invalid-feedback">{errors.quantity}</div>}
          </div>
        </div>

        <div className="col-md-4">
          <div className="mb-3">
            <label htmlFor="minStockLevel" className="form-label">
              Min. Stok Seviyesi <span className="text-danger">*</span>
            </label>
            <input
              type="number"
              min="0"
              className={`form-control ${errors.minStockLevel ? 'is-invalid' : ''}`}
              id="minStockLevel"
              name="minStockLevel"
              value={formData.minStockLevel}
              onChange={handleChange}
              placeholder="10"
              required
            />
            {errors.minStockLevel && <div className="invalid-feedback">{errors.minStockLevel}</div>}
          </div>
        </div>

        <div className="col-md-4">
          <div className="mb-3">
            <label htmlFor="reservedQuantity" className="form-label">
              Rezerve Miktar
            </label>
            <input
              type="number"
              min="0"
              className="form-control"
              id="reservedQuantity"
              name="reservedQuantity"
              value={formData.reservedQuantity}
              onChange={handleChange}
              placeholder="0"
            />
          </div>
        </div>
      </div>

      <div className="d-flex justify-content-end gap-2">
        <button
          type="button"
          className="btn btn-secondary"
          onClick={onCancel}
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
              Kaydediliyor...
            </>
          ) : (
            <>
              <i className="fas fa-save me-2"></i>
              Stok Kaydı Oluştur
            </>
          )}
        </button>
      </div>
    </form>
  );
};

export default StockForm;
