import React, { useState, useEffect } from 'react';
import axios from 'axios';
import SearchableSelect from './SearchableSelect';

const ProductForm = ({ product, categories, onSuccess, onCancel }) => {
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    sku: '',
    price: '',
    weight: '',
    dimensions: '',
    lengthCm: '',
    widthCm: '',
    heightCm: '',
    shippingRate: '',
    categoryId: '',
    isActive: true
  });
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});
  const [brandId, setBrandId] = useState(null);
  const [colorId, setColorId] = useState(null);

  useEffect(() => {
    if (product) {
      setFormData({
        name: product.name || '',
        description: product.description || '',
        sku: product.sku || '',
        price: product.price || '',
        weight: product.weight || '',
        dimensions: product.dimensions || '',
        lengthCm: product.lengthCm || '',
        widthCm: product.widthCm || '',
        heightCm: product.heightCm || '',
        shippingRate: product.shippingRate || '',
        categoryId: product.category?.id || '',
        isActive: product.isActive !== false
      });
      setBrandId(product.brand?.id || null);
      setColorId(product.color?.id || null);
    }
  }, [product]);

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
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

    if (!formData.name.trim()) {
      newErrors.name = 'Ürün adı gereklidir';
    }

    if (!formData.sku.trim()) {
      newErrors.sku = 'Stok kodu gereklidir';
    }

    if (!formData.price || parseFloat(formData.price) < 0) {
      newErrors.price = 'Geçerli bir fiyat giriniz';
    }

    if (!formData.categoryId) {
      newErrors.categoryId = 'Kategori seçiniz';
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
        name: formData.name,
        description: formData.description,
        sku: formData.sku,
        price: parseFloat(formData.price),
        weight: formData.weight ? parseFloat(formData.weight) : null,
        dimensions: formData.dimensions,
        lengthCm: formData.lengthCm ? parseFloat(formData.lengthCm) : null,
        widthCm: formData.widthCm ? parseFloat(formData.widthCm) : null,
        heightCm: formData.heightCm ? parseFloat(formData.heightCm) : null,
        shippingRate: formData.shippingRate ? parseFloat(formData.shippingRate) : null,
        category: { id: parseInt(formData.categoryId) },
        brand: brandId ? { id: brandId } : null,
        color: colorId ? { id: colorId } : null,
        isActive: formData.isActive
      };

      if (product) {
        await axios.put(`/api/products/${product.id}`, dataToSend);
      } else {
        await axios.post('/api/products', dataToSend);
      }

      onSuccess();
    } catch (error) {
      console.error('Error saving product:', error);
      if (error.response?.data) {
        setErrors({ general: error.response.data });
      } else {
        setErrors({ general: 'Ürün kaydedilirken hata oluştu' });
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
            <label htmlFor="name" className="form-label">
              Ürün Adı <span className="text-danger">*</span>
            </label>
            <input
              type="text"
              className={`form-control ${errors.name ? 'is-invalid' : ''}`}
              id="name"
              name="name"
              value={formData.name}
              onChange={handleChange}
              placeholder="Samsung Buzdolabı"
              required
            />
            {errors.name && <div className="invalid-feedback">{errors.name}</div>}
          </div>
        </div>

        <div className="col-md-6">
          <div className="mb-3">
            <label htmlFor="sku" className="form-label">
              Stok Kodu <span className="text-danger">*</span>
            </label>
            <input
              type="text"
              className={`form-control ${errors.sku ? 'is-invalid' : ''}`}
              id="sku"
              name="sku"
              value={formData.sku}
              onChange={handleChange}
              placeholder="BD-001"
              required
            />
            {errors.sku && <div className="invalid-feedback">{errors.sku}</div>}
          </div>
        </div>
      </div>

      <div className="mb-3">
        <label htmlFor="description" className="form-label">
          Açıklama
        </label>
        <textarea
          className="form-control"
          id="description"
          name="description"
          rows="3"
          value={formData.description}
          onChange={handleChange}
          placeholder="Ürün açıklaması..."
        />
      </div>

      <div className="row">
        <div className="col-md-4">
          <div className="mb-3">
            <label htmlFor="price" className="form-label">
              Fiyat (₺) <span className="text-danger">*</span>
            </label>
            <input
              type="number"
              step="0.01"
              min="0"
              className={`form-control ${errors.price ? 'is-invalid' : ''}`}
              id="price"
              name="price"
              value={formData.price}
              onChange={handleChange}
              placeholder="15000.00"
              required
            />
            {errors.price && <div className="invalid-feedback">{errors.price}</div>}
          </div>
        </div>

        <div className="col-md-4">
          <div className="mb-3">
            <label htmlFor="weight" className="form-label">
              Ağırlık (kg)
            </label>
            <input
              type="number"
              step="0.01"
              min="0"
              className="form-control"
              id="weight"
              name="weight"
              value={formData.weight}
              onChange={handleChange}
              placeholder="50.5"
            />
          </div>
        </div>

        <div className="col-md-4">
          <div className="mb-3">
            <label className="form-label">
              Boyutlar (cm)
            </label>
            <div className="input-group">
              <input
                type="number"
                min="0"
                step="0.01"
                className="form-control"
                id="widthCm"
                name="widthCm"
                value={formData.widthCm}
                onChange={handleChange}
                placeholder="En"
              />
              <input
                type="number"
                min="0"
                step="0.01"
                className="form-control"
                id="lengthCm"
                name="lengthCm"
                value={formData.lengthCm}
                onChange={handleChange}
                placeholder="Boy"
              />
              <input
                type="number"
                min="0"
                step="0.01"
                className="form-control"
                id="heightCm"
                name="heightCm"
                value={formData.heightCm}
                onChange={handleChange}
                placeholder="Yükseklik"
              />
            </div>
          </div>
        </div>
      </div>

      <div className="row">
        <div className="col-md-6">
          <div className="mb-3">
            <label htmlFor="categoryId" className="form-label">
              Kategori <span className="text-danger">*</span>
            </label>
            <select
              className={`form-select ${errors.categoryId ? 'is-invalid' : ''}`}
              id="categoryId"
              name="categoryId"
              value={formData.categoryId}
              onChange={handleChange}
              required
            >
              <option value="">Kategori seçin</option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
            {errors.categoryId && <div className="invalid-feedback">{errors.categoryId}</div>}
          </div>
        </div>
        <div className="col-md-6">
          <SearchableSelect
            label="Marka"
            value={brandId}
            onChange={(id) => setBrandId(id)}
            searchEndpoint="/api/brands/search"
            placeholder="Marka ara..."
          />
        </div>
      </div>

      <div className="row">
        <div className="col-md-4">
          <div className="mb-3">
            <label htmlFor="shippingRate" className="form-label">
              Kargo Ücreti (Desi Başına)
            </label>
            <input
              type="number"
              step="0.01"
              min="0"
              className="form-control"
              id="shippingRate"
              name="shippingRate"
              value={formData.shippingRate}
              onChange={handleChange}
              placeholder="Örn: 12.50"
            />
          </div>
        </div>
        <div className="col-md-6">
          <SearchableSelect
            label="Renk"
            value={colorId}
            onChange={(id) => setColorId(id)}
            searchEndpoint="/api/colors/search"
            placeholder="Renk ara..."
            renderOption={(opt) => (
              <span>
                <span className="me-2" style={{ display: 'inline-block', width: 12, height: 12, backgroundColor: opt.hexCode || '#ccc', border: '1px solid #ccc' }}></span>
                {opt.name}
              </span>
            )}
          />
        </div>
        <div className="col-md-6">
          <div className="mb-3">
            <div className="form-check mt-4">
              <input
                type="checkbox"
                className="form-check-input"
                id="isActive"
                name="isActive"
                checked={formData.isActive}
                onChange={handleChange}
              />
              <label className="form-check-label" htmlFor="isActive">
                Aktif
              </label>
            </div>
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
              {product ? 'Güncelle' : 'Kaydet'}
            </>
          )}
        </button>
      </div>
    </form>
  );
};

export default ProductForm;
