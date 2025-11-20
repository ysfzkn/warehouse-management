import React, { useState, useEffect, useMemo } from 'react';
import axios from 'axios';

const INITIAL_VISIBLE_PRODUCTS = 12;

const StockForm = ({ products, warehouses, onSuccess, onCancel }) => {
  const [formData, setFormData] = useState({
    productId: '',
    warehouseId: '',
    quantity: '',
    minStockLevel: '',
    reservedQuantity: '0',
    consignedQuantity: '0'
  });
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});
  const [productSearchTerm, setProductSearchTerm] = useState('');
  const [visibleProductCount, setVisibleProductCount] = useState(INITIAL_VISIBLE_PRODUCTS);

  useEffect(() => {
    // Set default warehouse if available
    if (warehouses.length > 0) {
      setFormData(prev => ({ ...prev, warehouseId: warehouses[0].id }));
    }
  }, [warehouses]);

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

  const handleProductSelect = (productId) => {
    setFormData(prev => ({ ...prev, productId }));
    if (errors.productId) {
      setErrors(prev => ({ ...prev, productId: '' }));
    }
  };

  const filteredProducts = useMemo(() => {
    if (!productSearchTerm.trim()) return products;
    const query = productSearchTerm.trim().toLowerCase();
    return products.filter(product => {
      const haystack = [
        product.name,
        product.sku,
        product.barcode,
        product.brand?.name
      ]
        .filter(Boolean)
        .map(text => text.toLowerCase());
      return haystack.some(text => text.includes(query));
    });
  }, [products, productSearchTerm]);

  const productOptions = useMemo(() => {
    if (!formData.productId) return filteredProducts;
    const hasSelected = filteredProducts.some(product => String(product.id) === String(formData.productId));
    if (hasSelected) {
      return filteredProducts;
    }
    const selectedProduct = products.find(product => String(product.id) === String(formData.productId));
    return selectedProduct ? [selectedProduct, ...filteredProducts] : filteredProducts;
  }, [filteredProducts, formData.productId, products]);

  useEffect(() => {
    setVisibleProductCount(INITIAL_VISIBLE_PRODUCTS);
  }, [productSearchTerm, filteredProducts.length]);

  const limitedProductOptions = useMemo(() => productOptions.slice(0, visibleProductCount), [productOptions, visibleProductCount]);
  const hasMoreProducts = productOptions.length > visibleProductCount;

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
        reservedQuantity: parseInt(formData.reservedQuantity || 0),
        consignedQuantity: parseInt(formData.consignedQuantity || 0)
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
            <div className="input-group mb-2">
              <span className="input-group-text bg-light">
                <i className="fas fa-search text-muted"></i>
              </span>
              <input
                type="text"
                className="form-control"
                placeholder="Ürün adı, SKU veya marka ara..."
                value={productSearchTerm}
                onChange={(e) => setProductSearchTerm(e.target.value)}
                disabled={products.length === 0}
              />
              {productSearchTerm && (
                <button
                  type="button"
                  className="btn btn-outline-secondary"
                  onClick={() => setProductSearchTerm('')}
                >
                  Temizle
                </button>
              )}
            </div>
            <div className="d-flex justify-content-between small text-muted mb-2">
              <span>{products.length} ürün</span>
              {productSearchTerm && (
                <span>{productOptions.length} sonuç</span>
              )}
            </div>
            {productOptions.length === 0 ? (
              <div className="alert alert-light border mt-2 py-3 mb-0">
                <i className="fas fa-info-circle me-2 text-primary"></i>
                {productSearchTerm
                  ? 'Arama kriterlerine uygun ürün bulunamadı. Farklı anahtar kelimeler deneyin.'
                  : 'Sistemde tanımlı ürün bulunamadı.'}
              </div>
            ) : (
              <div className="stock-option-list border rounded-4 shadow-sm bg-white">
                <div className="stock-option-grid">
                  {limitedProductOptions.map(product => {
                    const productId = String(product.id);
                    const isSelected = String(formData.productId) === productId;
                    return (
                      <button
                        type="button"
                        key={product.id}
                        className={`stock-option-button ${isSelected ? 'active' : ''}`}
                        onClick={() => handleProductSelect(productId)}
                      >
                        <div className="fw-semibold text-dark">{product.name}</div>
                        <div className="text-muted small">SKU: {product.sku}</div>
                        {product.brand?.name && (
                          <div className="badge bg-light text-dark mt-2">{product.brand.name}</div>
                        )}
                      </button>
                    );
                  })}
                </div>
              </div>
            )}
            {hasMoreProducts && (
              <button
                type="button"
                className="btn btn-light btn-sm w-100 mt-2 stock-load-more"
                onClick={() => setVisibleProductCount(prev => prev + INITIAL_VISIBLE_PRODUCTS)}
              >
                Daha fazla göster ({productOptions.length - limitedProductOptions.length} ürün)
              </button>
            )}
            {!formData.productId && productOptions.length > 0 && (
              <small className="text-muted d-block mt-2">
                Ürünü seçmek için kartın üzerine tıklayın.
              </small>
            )}
            {errors.productId && <div className="invalid-feedback d-block">{errors.productId}</div>}
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

        <div className="col-md-4">
          <div className="mb-3">
            <label htmlFor="consignedQuantity" className="form-label">
              Emanet
            </label>
            <input
              type="number"
              className="form-control"
              id="consignedQuantity"
              name="consignedQuantity"
              value={formData.consignedQuantity}
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
