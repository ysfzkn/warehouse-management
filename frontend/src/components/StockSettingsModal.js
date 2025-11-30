import React, { useState, useEffect, useMemo } from 'react';
import axios from 'axios';
import {
  extractPhoneDigits,
  formatPhoneForSubmit,
  formatPhoneInputValue,
  isPhoneComplete,
  PHONE_PLACEHOLDER
} from '../utils/phone';

/**
 * Stock settings modal - for managing consigned, reserved, and min stock levels
 */
const StockSettingsModal = ({ stock, products = [], onSuccess, onClose }) => {
  const role = (typeof window !== 'undefined' && localStorage.getItem('auth_role')) || 'ADMIN';
  const [settings, setSettings] = useState({
    productId: null,
    consignedQuantity: 0,
    minStockLevel: 10,
    customerName: '',
    customerPhone: ''
  });
  const [productSearchTerm, setProductSearchTerm] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [errors, setErrors] = useState({});

  // Check if warehouse is EMANET_DEPO type
  // Support both string and enum formats
  const warehouseType = stock?.warehouse?.warehouseType;
  const isEmanetDepo = warehouseType === 'EMANET_DEPO' || warehouseType === 'EmanetDepo' || warehouseType === 'emanetDepo';

  useEffect(() => {
    if (stock) {
      setSettings({
        productId: stock.product?.id || null,
        consignedQuantity: stock.consignedQuantity || 0,
        minStockLevel: stock.minStockLevel || 10,
        customerName: stock.customerName || '',
        customerPhone: stock.customerPhone ? extractPhoneDigits(stock.customerPhone) : ''
      });
    }
  }, [stock]);

  // Filter products for search
  const filteredProducts = useMemo(() => {
    if (!productSearchTerm.trim()) return products;
    const query = productSearchTerm.trim().toLocaleLowerCase('tr-TR');
    return products.filter(product => {
      const haystack = [
        product.name,
        product.sku,
        product.barcode,
        product.brand?.name
      ]
        .filter(Boolean)
        .map(text => text.toLocaleLowerCase('tr-TR'));
      return haystack.some(text => text.includes(query));
    });
  }, [products, productSearchTerm]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    if (name === 'customerPhone') {
      const digits = extractPhoneDigits(value);
      setSettings(prev => ({
        ...prev,
        [name]: digits
      }));
      if (errors.customerPhone) {
        setErrors(prev => ({ ...prev, customerPhone: null }));
      }
    } else if (name === 'customerName') {
      setSettings(prev => ({
        ...prev,
        [name]: value
      }));
      if (errors.customerName) {
        setErrors(prev => ({ ...prev, customerName: null }));
      }
    } else if (name === 'productId') {
      setSettings(prev => ({
        ...prev,
        [name]: value ? parseInt(value) : null
      }));
      if (errors.productId) {
        setErrors(prev => ({ ...prev, productId: null }));
      }
    } else {
      setSettings(prev => ({
        ...prev,
        [name]: parseInt(value) || 0
      }));
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (role !== 'ADMIN') {
      setError('Bu işlem için yönetici yetkisi gereklidir');
      return;
    }

    // Validate EMANET_DEPO fields
    if (isEmanetDepo) {
      const newErrors = {};
      if (!settings.customerName || !settings.customerName.trim()) {
        newErrors.customerName = 'Müşteri adı gereklidir';
      }
      if (!settings.customerPhone || !settings.customerPhone.trim()) {
        newErrors.customerPhone = 'Müşteri telefon numarası gereklidir';
      } else if (!isPhoneComplete(settings.customerPhone)) {
        newErrors.customerPhone = 'Telefon numarası 10 haneli olmalıdır';
      }
      if (Object.keys(newErrors).length > 0) {
        setErrors(newErrors);
        setError('Lütfen tüm alanları doldurun');
        return;
      }
    }

    // Check if any change was made
    const originalCustomerName = stock.customerName || '';
    const originalCustomerPhone = stock.customerPhone ? extractPhoneDigits(stock.customerPhone) : '';
    const originalProductId = stock.product?.id || null;
    const hasChanges = 
      settings.productId !== originalProductId ||
      settings.consignedQuantity !== (stock.consignedQuantity || 0) ||
      settings.minStockLevel !== (stock.minStockLevel || 10) ||
      (isEmanetDepo && (
        settings.customerName.trim() !== originalCustomerName ||
        settings.customerPhone.trim() !== originalCustomerPhone
      ));

    if (!hasChanges) {
      setError('Herhangi bir değişiklik yapılmadı');
      return;
    }

    setLoading(true);
    setError(null);
    setSuccess(null);
    setErrors({});

    try {
      // CRITICAL: Never send quantity field in settings update
      // Quantity can ONLY be changed via add/remove endpoints
      const updateData = {
        consignedQuantity: settings.consignedQuantity,
        minStockLevel: settings.minStockLevel
        // Explicitly NOT including quantity field for security
      };
      
      // Include product if it changed
      if (settings.productId && settings.productId !== (stock.product?.id || null)) {
        updateData.product = { id: settings.productId };
      }
      
      if (isEmanetDepo) {
        updateData.customerName = settings.customerName.trim();
        updateData.customerPhone = formatPhoneForSubmit(settings.customerPhone);
      } else {
        updateData.customerName = null;
        updateData.customerPhone = null;
      }

      // Ensure quantity is never sent (defensive programming)
      delete updateData.quantity;

      await axios.put(`/api/stocks/${stock.id}`, updateData);

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
                  <label htmlFor="productId" className="form-label fw-bold">
                    <i className="fas fa-box me-1 text-primary"></i>
                    Ürün
                  </label>
                  <div className="position-relative">
                    <input
                      type="text"
                      className="form-control form-control-lg"
                      placeholder="Ürün ara..."
                      value={productSearchTerm}
                      onChange={(e) => setProductSearchTerm(e.target.value)}
                      onFocus={(e) => e.target.select()}
                    />
                    {productSearchTerm && (
                      <button
                        type="button"
                        className="btn btn-sm btn-link position-absolute end-0 top-50 translate-middle-y me-2"
                        onClick={() => setProductSearchTerm('')}
                        style={{ zIndex: 10 }}
                      >
                        <i className="fas fa-times"></i>
                      </button>
                    )}
                  </div>
                  <select
                    className={`form-select form-select-lg mt-2 ${errors.productId ? 'is-invalid' : ''}`}
                    id="productId"
                    name="productId"
                    value={settings.productId || ''}
                    onChange={handleChange}
                    style={{ maxHeight: '200px', overflowY: 'auto' }}
                  >
                    <option value="">-- Ürün Seçiniz --</option>
                    {filteredProducts.slice(0, 100).map(product => (
                      <option key={product.id} value={product.id}>
                        {product.name} {product.sku ? `(${product.sku})` : ''} {product.brand?.name ? `- ${product.brand.name}` : ''}
                      </option>
                    ))}
                  </select>
                  {filteredProducts.length > 100 && (
                    <small className="text-muted d-block mt-1">
                      <i className="fas fa-info-circle me-1"></i>
                      İlk 100 sonuç gösteriliyor. Daha fazla sonuç için arama yapın.
                    </small>
                  )}
                  {errors.productId && (
                    <div className="invalid-feedback">{errors.productId}</div>
                  )}
                  <small className="text-muted d-block mt-1">
                    Stok kaydının bağlı olduğu ürünü değiştirebilirsiniz
                  </small>
                </div>

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

                {isEmanetDepo && (
                  <>
                    <hr className="my-4" />
                    <h6 className="mb-3">
                      <i className="fas fa-user-tag me-2 text-info"></i>
                      Müşteri Bilgileri
                    </h6>
                    <div className="mb-3">
                      <label htmlFor="customerName" className="form-label fw-bold">
                        Müşteri Adı <span className="text-danger">*</span>
                      </label>
                      <input
                        type="text"
                        className={`form-control form-control-lg ${errors.customerName ? 'is-invalid' : ''}`}
                        id="customerName"
                        name="customerName"
                        value={settings.customerName}
                        onChange={handleChange}
                        placeholder="Müşteri adını giriniz"
                        maxLength="255"
                      />
                      {errors.customerName && (
                        <div className="invalid-feedback">{errors.customerName}</div>
                      )}
                    </div>
                    <div className="mb-3">
                      <label htmlFor="customerPhone" className="form-label fw-bold">
                        Müşteri Telefon Numarası <span className="text-danger">*</span>
                      </label>
                      <div className="input-group phone-input-group">
                        <span className="input-group-text">+90</span>
                        <input
                          type="tel"
                          className={`form-control form-control-lg ${errors.customerPhone ? 'is-invalid' : (settings.customerPhone ? (isPhoneComplete(settings.customerPhone) ? 'is-valid' : '') : '')}`}
                          id="customerPhone"
                          name="customerPhone"
                          value={formatPhoneInputValue(settings.customerPhone)}
                          onChange={handleChange}
                          placeholder={PHONE_PLACEHOLDER}
                          maxLength="13"
                          inputMode="numeric"
                        />
                      </div>
                      {errors.customerPhone && (
                        <div className="invalid-feedback d-block">{errors.customerPhone}</div>
                      )}
                      {!errors.customerPhone && settings.customerPhone && !isPhoneComplete(settings.customerPhone) && (
                        <small className="text-muted">Telefon 10 haneli olmalıdır</small>
                      )}
                    </div>
                  </>
                )}

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





