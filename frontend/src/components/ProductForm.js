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
    vatRate: '20',
    sctRate: '',
    categoryId: '',
    isActive: true
  });
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});
  const [brandId, setBrandId] = useState(null);
  const [colorId, setColorId] = useState(null);
  
  // Calculate total price with taxes
  const calculateTotalPrice = () => {
    const basePrice = parseFloat(formData.price) || 0;
    const sctRate = parseFloat(formData.sctRate) || 0;
    const vatRate = parseFloat(formData.vatRate) || 0;
    
    const sctAmount = basePrice * (sctRate / 100);
    const priceWithSct = basePrice + sctAmount;
    const vatAmount = priceWithSct * (vatRate / 100);
    const totalPrice = priceWithSct + vatAmount;
    
    return {
      basePrice,
      sctAmount,
      priceWithSct,
      vatAmount,
      totalPrice
    };
  };

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
        vatRate: product.vatRate || '',
        sctRate: product.sctRate || '',
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
        vatRate: formData.vatRate ? parseFloat(formData.vatRate) : null,
        sctRate: formData.sctRate ? parseFloat(formData.sctRate) : null,
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

      {/* Price and Tax Section */}
      <div className="row mb-3">
        <div className="col-12">
          <h6 className="text-muted mb-3">
            <i className="fas fa-money-bill-wave me-2"></i>
            Fiyatlandırma ve Vergiler
          </h6>
        </div>
      </div>

      <div className="row">
        <div className="col-md-4">
          <div className="mb-4">
            <label htmlFor="price" className="form-label">
              <i className="fas fa-tag me-1"></i>
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
          <div className="mb-4">
            <label htmlFor="vatRate" className="form-label">
              <i className="fas fa-percentage me-1"></i>KDV Oranı (%)
              <span 
                className="ms-1 text-primary" 
                style={{ cursor: 'help' }}
                title="Katma Değer Vergisi oranı. Türkiye'de yaygın oranlar: %1, %10, %20"
              >
                <i className="fas fa-info-circle"></i>
              </span>
            </label>
            <div className="input-group">
              <input
                type="number"
                step="0.01"
                min="0"
                max="100"
                className="form-control"
                id="vatRate"
                name="vatRate"
                value={formData.vatRate}
                onChange={handleChange}
                placeholder="20"
              />
              <span className="input-group-text">%</span>
            </div>
            <div className="d-flex gap-1 mt-2">
              <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => setFormData({...formData, vatRate: '1'})}>%1</button>
              <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => setFormData({...formData, vatRate: '10'})}>%10</button>
              <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => setFormData({...formData, vatRate: '20'})}>%20</button>
              <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => setFormData({...formData, vatRate: '0'})}>Muaf</button>
            </div>
          </div>
        </div>

        <div className="col-md-4">
          <div className="mb-4">
            <label htmlFor="sctRate" className="form-label">
              <i className="fas fa-percentage me-1"></i>ÖTV Oranı (%)
              <span 
                className="ms-1 text-primary" 
                style={{ cursor: 'help' }}
                title="Özel Tüketim Vergisi. Alkol, tütün, akaryakıt, otomobil gibi ürünlerde uygulanır."
              >
                <i className="fas fa-info-circle"></i>
              </span>
            </label>
            <div className="input-group">
              <input
                type="number"
                step="0.01"
                min="0"
                max="200"
                className="form-control"
                id="sctRate"
                name="sctRate"
                value={formData.sctRate}
                onChange={handleChange}
                placeholder="0"
              />
              <span className="input-group-text">%</span>
            </div>
            <div className="d-flex gap-1 mt-2">
              <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => setFormData({...formData, sctRate: '0'})}>Yok</button>
              <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => setFormData({...formData, sctRate: '10'})}>%10</button>
              <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => setFormData({...formData, sctRate: '50'})}>%50</button>
            </div>
          </div>
        </div>
      </div>

      {/* Physical Properties Section */}
      <div className="row mb-3">
        <div className="col-12">
          <h6 className="text-muted mb-3">
            <i className="fas fa-cube me-2"></i>
            Fiziksel Özellikler
          </h6>
        </div>
      </div>

      <div className="row">
        <div className="col-md-6">
          <div className="mb-3">
            <label className="form-label">
              <i className="fas fa-ruler-combined me-1"></i>
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

        <div className="col-md-6">
          <div className="mb-3">
            <label htmlFor="weight" className="form-label">
              <i className="fas fa-weight-hanging me-1"></i>
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
      </div>

      {/* Category and Brand Section */}
      <div className="row mb-3">
        <div className="col-12">
          <h6 className="text-muted mb-3">
            <i className="fas fa-tags me-2"></i>
            Kategori ve Özellikler
          </h6>
        </div>
      </div>

      <div className="row">
        <div className="col-md-4">
          <div className="mb-3">
            <label htmlFor="categoryId" className="form-label">
              <i className="fas fa-folder me-1"></i>
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
        
        <div className="col-md-4">
          <SearchableSelect
            label={
              <span>
                <i className="fas fa-copyright me-1"></i>
                Marka
              </span>
            }
            value={brandId}
            onChange={(id) => setBrandId(id)}
            searchEndpoint="/api/brands/search"
            placeholder="Marka ara..."
          />
        </div>

        <div className="col-md-4">
          <SearchableSelect
            label={
              <span>
                <i className="fas fa-palette me-1"></i>
                Renk
              </span>
            }
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
      </div>

      {/* Shipping and Status Section */}
      <div className="row mb-3">
        <div className="col-12">
          <h6 className="text-muted mb-3">
            <i className="fas fa-shipping-fast me-2"></i>
            Kargo ve Durum
          </h6>
        </div>
      </div>

      <div className="row">
        <div className="col-md-6">
          <div className="mb-3">
            <label htmlFor="shippingRate" className="form-label">
              <i className="fas fa-truck me-1"></i>
              Kargo Ücreti (Desi Başına)
            </label>
            <div className="input-group">
              <span className="input-group-text">₺</span>
              <input
                type="number"
                step="0.01"
                min="0"
                className="form-control"
                id="shippingRate"
                name="shippingRate"
                value={formData.shippingRate}
                onChange={handleChange}
                placeholder="12.50"
              />
              <span className="input-group-text">/desi</span>
            </div>
            <small className="text-muted">Desi başına kargo ücreti tutarı</small>
          </div>
        </div>

        <div className="col-md-6">
          <div className="mb-3">
            <label className="form-label d-block">
              <i className="fas fa-toggle-on me-1"></i>
              Ürün Durumu
            </label>
            <div className="form-check form-switch mt-3">
              <input
                type="checkbox"
                className="form-check-input"
                id="isActive"
                name="isActive"
                checked={formData.isActive}
                onChange={handleChange}
                style={{ width: '48px', height: '24px', cursor: 'pointer' }}
              />
              <label className="form-check-label ms-2" htmlFor="isActive" style={{ cursor: 'pointer' }}>
                <strong className={formData.isActive ? 'text-success' : 'text-secondary'}>
                  {formData.isActive ? 'Aktif' : 'Pasif'}
                </strong>
              </label>
            </div>
          </div>
        </div>
      </div>

      {/* Live Price Calculation Preview */}
      {formData.price && parseFloat(formData.price) > 0 && (
        <div className="alert alert-info border-start border-primary border-4 mb-3">
          <h6 className="alert-heading mb-2">
            <i className="fas fa-calculator me-2"></i>
            Fiyat Hesaplama Önizlemesi
          </h6>
          <div className="row g-2">
            <div className="col-md-6">
              <div className="d-flex justify-content-between small">
                <span className="text-muted">Ana Fiyat:</span>
                <strong>₺{calculateTotalPrice().basePrice.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}</strong>
              </div>
              {calculateTotalPrice().sctAmount > 0 && (
                <>
                  <div className="d-flex justify-content-between small text-success">
                    <span>+ ÖTV (%{formData.sctRate}):</span>
                    <span>₺{calculateTotalPrice().sctAmount.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}</span>
                  </div>
                  <div className="d-flex justify-content-between small">
                    <span className="text-muted">ÖTV'li Fiyat:</span>
                    <span>₺{calculateTotalPrice().priceWithSct.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}</span>
                  </div>
                </>
              )}
              {calculateTotalPrice().vatAmount > 0 && (
                <div className="d-flex justify-content-between small text-success">
                  <span>+ KDV (%{formData.vatRate}):</span>
                  <span>₺{calculateTotalPrice().vatAmount.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}</span>
                </div>
              )}
              <hr className="my-1" />
              <div className="d-flex justify-content-between">
                <strong className="text-dark">Toplam Fiyat:</strong>
                <strong className="text-success fs-5">₺{calculateTotalPrice().totalPrice.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}</strong>
              </div>
            </div>
            <div className="col-md-6">
              <small className="text-muted d-block">
                <i className="fas fa-lightbulb me-1 text-warning"></i>
                <strong>Hesaplama Formülü:</strong>
              </small>
              <small className="text-muted d-block">1. ÖTV Tutarı = Ana Fiyat × ÖTV%</small>
              <small className="text-muted d-block">2. ÖTV'li Fiyat = Ana Fiyat + ÖTV</small>
              <small className="text-muted d-block">3. KDV Tutarı = ÖTV'li Fiyat × KDV%</small>
              <small className="text-muted d-block">4. <strong>Toplam = ÖTV'li Fiyat + KDV</strong></small>
            </div>
          </div>
        </div>
      )}

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
