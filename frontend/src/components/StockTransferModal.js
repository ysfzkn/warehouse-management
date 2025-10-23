import React, { useState, useEffect } from 'react';
import axios from 'axios';

const StockTransferModal = ({ stock, onSuccess, onClose }) => {
  const [currentStep, setCurrentStep] = useState(1);
  const [formData, setFormData] = useState({
    sourceWarehouseId: stock?.warehouse?.id || '',
    destinationWarehouseId: '',
    productId: stock?.product?.id || '',
    quantity: '',
    driverName: '',
    driverTcId: '',
    driverPhone: '',
    vehiclePlate: '',
    notes: '',
    transferDate: new Date().toISOString().slice(0, 16)
  });

  const [warehouses, setWarehouses] = useState([]);
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [availableStock, setAvailableStock] = useState(null);
  const [validationErrors, setValidationErrors] = useState({});

  useEffect(() => {
    fetchWarehouses();
    if (!stock) {
      fetchProducts();
    }
  }, [stock]);

  useEffect(() => {
    if (formData.sourceWarehouseId && formData.productId) {
      fetchAvailableStock();
    }
  }, [formData.sourceWarehouseId, formData.productId]);

  const fetchWarehouses = async () => {
    try {
      const response = await axios.get('/api/warehouses');
      // Filter active warehouses - check for both boolean and numeric values
      const activeWarehouses = response.data.filter(w => w.isActive === true || w.isActive === 1);
      console.log('Fetched warehouses:', activeWarehouses);
      setWarehouses(activeWarehouses);
    } catch (error) {
      console.error('Error fetching warehouses:', error);
      setError('Depolar yüklenirken hata oluştu');
    }
  };

  const fetchProducts = async () => {
    try {
      const response = await axios.get('/api/products');
      setProducts(response.data);
    } catch (error) {
      console.error('Error fetching products:', error);
    }
  };

  const fetchAvailableStock = async () => {
    try {
      const response = await axios.get('/api/stocks', {
        params: {
          warehouseId: formData.sourceWarehouseId
        }
      });
      const stockItem = response.data.find(
        s => s.product.id === parseInt(formData.productId) && 
             s.warehouse.id === parseInt(formData.sourceWarehouseId)
      );
      setAvailableStock(stockItem || null);
    } catch (error) {
      console.error('Error fetching stock:', error);
      setAvailableStock(null);
    }
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    
    // If source warehouse changes, clear destination warehouse if it's the same
    if (name === 'sourceWarehouseId') {
      setFormData(prev => {
        const newData = { ...prev, [name]: value };
        // Clear destination if it matches the new source
        if (String(prev.destinationWarehouseId) === String(value)) {
          newData.destinationWarehouseId = '';
        }
        return newData;
      });
    } else {
      setFormData(prev => ({
        ...prev,
        [name]: value
      }));
    }
    
    if (validationErrors[name]) {
      setValidationErrors(prev => ({ ...prev, [name]: '' }));
    }
  };

  const validateStep = (step) => {
    const errors = {};

    if (step === 1) {
      if (!formData.sourceWarehouseId) errors.sourceWarehouseId = 'Kaynak depo zorunludur';
      if (!formData.destinationWarehouseId) errors.destinationWarehouseId = 'Hedef depo zorunludur';
      if (String(formData.sourceWarehouseId) === String(formData.destinationWarehouseId)) {
        errors.destinationWarehouseId = 'Kaynak ve hedef depo farklı olmalıdır';
      }
      if (!formData.productId) errors.productId = 'Ürün seçimi zorunludur';
      if (!formData.quantity || formData.quantity <= 0) errors.quantity = 'Geçerli bir miktar giriniz';
      if (availableStock && formData.quantity > availableStock.availableQuantity) {
        errors.quantity = `Maksimum ${availableStock.availableQuantity} adet transfer edilebilir`;
      }
    } else if (step === 2) {
      if (!formData.driverName.trim()) errors.driverName = 'Şoför adı zorunludur';
      if (formData.driverName.trim().length < 3) errors.driverName = 'Şoför adı en az 3 karakter olmalıdır';
      if (!/^[0-9]{11}$/.test(formData.driverTcId)) errors.driverTcId = 'TC Kimlik No 11 haneli olmalıdır';
      if (!formData.driverPhone.trim()) errors.driverPhone = 'Telefon numarası zorunludur';
      if (!formData.vehiclePlate.trim()) errors.vehiclePlate = 'Araç plakası zorunludur';
    }

    setValidationErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleNext = () => {
    if (validateStep(currentStep)) {
      setCurrentStep(currentStep + 1);
    }
  };

  const handlePrevious = () => {
    setCurrentStep(currentStep - 1);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    if (!validateStep(2)) return;
    
    setError(null);
    setLoading(true);

    try {
      const transferData = {
        sourceWarehouse: { id: parseInt(formData.sourceWarehouseId) },
        destinationWarehouse: { id: parseInt(formData.destinationWarehouseId) },
        product: { id: parseInt(formData.productId) },
        quantity: parseInt(formData.quantity),
        driverName: formData.driverName.trim(),
        driverTcId: formData.driverTcId.trim(),
        driverPhone: formData.driverPhone.trim(),
        vehiclePlate: formData.vehiclePlate.trim().toUpperCase(),
        notes: formData.notes.trim(),
        transferDate: new Date(formData.transferDate).toISOString()
      };

      await axios.post('/api/stock-transfers', transferData);
      onSuccess();
    } catch (error) {
      console.error('Error creating transfer:', error);
      setError(error.response?.data || 'Transfer oluşturulurken hata oluştu');
    } finally {
      setLoading(false);
    }
  };

  const sourceWarehouse = warehouses.find(w => w.id === parseInt(formData.sourceWarehouseId));
  const destinationWarehouse = warehouses.find(w => w.id === parseInt(formData.destinationWarehouseId));
  const selectedProduct = stock?.product || products.find(p => p.id === parseInt(formData.productId));

  const steps = [
    { number: 1, title: 'Transfer Detayları', icon: 'fa-boxes' },
    { number: 2, title: 'Taşıma Bilgileri', icon: 'fa-truck' },
    { number: 3, title: 'Özet & Onay', icon: 'fa-check-circle' }
  ];

  return (
    <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.6)' }}>
      <div className="modal-dialog modal-xl modal-dialog-centered">
        <div className="modal-content shadow-lg">
          <div className="modal-header bg-gradient text-white" style={{ background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' }}>
            <div>
              <h5 className="modal-title mb-1">
                <i className="fas fa-exchange-alt me-2"></i>
                Şubeler Arası Ürün Transferi
              </h5>
              <small className="opacity-75">Güvenli ve hızlı stok transferi</small>
            </div>
            <button
              type="button"
              className="btn-close btn-close-white"
              onClick={onClose}
              disabled={loading}
            ></button>
          </div>

          {/* Progress Steps */}
          <div className="px-4 pt-4 pb-2">
            <div className="d-flex justify-content-between align-items-center position-relative mb-3">
              <div className="position-absolute w-100 top-50 start-0" style={{ height: '2px', background: '#e9ecef', zIndex: 0 }}>
                <div 
                  className="h-100 bg-primary transition-all" 
                  style={{ width: `${((currentStep - 1) / (steps.length - 1)) * 100}%`, transition: 'width 0.3s ease' }}
                ></div>
              </div>
              {steps.map((step) => (
                <div key={step.number} className="text-center position-relative" style={{ zIndex: 1, flex: 1 }}>
                  <div 
                    className={`mx-auto rounded-circle d-flex align-items-center justify-content-center ${
                      currentStep >= step.number ? 'bg-primary text-white' : 'bg-light text-muted'
                    }`}
                    style={{ width: '50px', height: '50px', transition: 'all 0.3s ease', border: '3px solid white' }}
                  >
                    <i className={`fas ${step.icon} fa-lg`}></i>
                  </div>
                  <div className={`mt-2 small fw-bold ${currentStep >= step.number ? 'text-primary' : 'text-muted'}`}>
                    {step.title}
                  </div>
                </div>
              ))}
            </div>
          </div>

          <form onSubmit={handleSubmit}>
            <div className="modal-body" style={{ minHeight: '400px' }}>
              {error && (
                <div className="alert alert-danger alert-dismissible fade show" role="alert">
                  <i className="fas fa-exclamation-triangle me-2"></i>
                  <strong>Hata!</strong> {error}
                  <button type="button" className="btn-close" onClick={() => setError(null)}></button>
                </div>
              )}

               {/* Step 1: Transfer Details */}
               {currentStep === 1 && (
                 <div style={{ animation: 'fadeIn 0.3s ease-in' }}>
                  <h5 className="mb-3 text-primary">
                    <i className="fas fa-boxes me-2"></i>
                    Transfer Detaylarını Belirleyin
                  </h5>
                  
                  <div className="row g-3">
                    <div className="col-md-6">
                      <label className="form-label fw-bold">
                        <i className="fas fa-warehouse text-danger me-1"></i>
                        Kaynak Depo *
                        <i className="fas fa-info-circle ms-1 text-muted" title="Stokun alınacağı depo"></i>
                      </label>
                      <select
                        className={`form-select form-select-lg ${validationErrors.sourceWarehouseId ? 'is-invalid' : formData.sourceWarehouseId ? 'is-valid' : ''}`}
                        name="sourceWarehouseId"
                        value={formData.sourceWarehouseId}
                        onChange={handleChange}
                        required
                        disabled={!!stock || warehouses.length === 0}
                      >
                        <option value="">
                          {warehouses.length === 0 ? '-- Depo yükleniyor... --' : '-- Kaynak depo seçiniz --'}
                        </option>
                        {warehouses.map(warehouse => (
                          <option key={warehouse.id} value={warehouse.id}>
                            📍 {warehouse.name} - {warehouse.location}
                          </option>
                        ))}
                      </select>
                      {validationErrors.sourceWarehouseId && (
                        <div className="invalid-feedback">{validationErrors.sourceWarehouseId}</div>
                      )}
                      {sourceWarehouse && !validationErrors.sourceWarehouseId && (
                        <small className="text-success d-block mt-1">
                          <i className="fas fa-check-circle me-1"></i>
                          {sourceWarehouse.location}
                        </small>
                      )}
                      {warehouses.length === 0 && !stock && (
                        <small className="text-danger d-block mt-1">
                          <i className="fas fa-exclamation-circle me-1"></i>
                          Aktif depo bulunamadı. Lütfen önce depo ekleyin.
                        </small>
                      )}
                    </div>

                    <div className="col-md-6">
                      <label className="form-label fw-bold">
                        <i className="fas fa-warehouse text-success me-1"></i>
                        Hedef Depo *
                        <i className="fas fa-info-circle ms-1 text-muted" title="Stokun gönderileceği depo"></i>
                      </label>
                      <select
                        className={`form-select form-select-lg ${validationErrors.destinationWarehouseId ? 'is-invalid' : formData.destinationWarehouseId ? 'is-valid' : ''}`}
                        name="destinationWarehouseId"
                        value={formData.destinationWarehouseId}
                        onChange={handleChange}
                        required
                        disabled={!formData.sourceWarehouseId || warehouses.length === 0}
                      >
                        <option value="">
                          {!formData.sourceWarehouseId 
                            ? '-- Önce kaynak depo seçiniz --' 
                            : warehouses.length === 0 
                              ? '-- Depo bulunamadı --'
                              : '-- Hedef depo seçiniz --'}
                        </option>
                        {warehouses
                          .filter(w => String(w.id) !== String(formData.sourceWarehouseId))
                          .map(warehouse => (
                            <option key={warehouse.id} value={warehouse.id}>
                              📍 {warehouse.name} - {warehouse.location}
                            </option>
                          ))}
                      </select>
                      {validationErrors.destinationWarehouseId && (
                        <div className="invalid-feedback">{validationErrors.destinationWarehouseId}</div>
                      )}
                      {destinationWarehouse && !validationErrors.destinationWarehouseId && (
                        <small className="text-success d-block mt-1">
                          <i className="fas fa-check-circle me-1"></i>
                          {destinationWarehouse.location}
                        </small>
                      )}
                      {warehouses.length === 0 && (
                        <small className="text-danger d-block mt-1">
                          <i className="fas fa-exclamation-circle me-1"></i>
                          Aktif depo bulunamadı. Lütfen önce depo ekleyin.
                        </small>
                      )}
                    </div>

                    <div className="col-md-8">
                      <label className="form-label fw-bold">
                        <i className="fas fa-box text-primary me-1"></i>
                        Ürün *
                      </label>
                      {stock ? (
                        <input
                          type="text"
                          className="form-control form-control-lg is-valid"
                          value={`${stock.product.name} (${stock.product.sku})`}
                          disabled
                        />
                      ) : (
                        <select
                          className={`form-select form-select-lg ${validationErrors.productId ? 'is-invalid' : formData.productId ? 'is-valid' : ''}`}
                          name="productId"
                          value={formData.productId}
                          onChange={handleChange}
                          required
                        >
                          <option value="">-- Ürün seçiniz --</option>
                          {products.map(product => (
                            <option key={product.id} value={product.id}>
                              📦 {product.name} - {product.sku}
                            </option>
                          ))}
                        </select>
                      )}
                      {validationErrors.productId && (
                        <div className="invalid-feedback">{validationErrors.productId}</div>
                      )}
                    </div>

                    <div className="col-md-4">
                      <label className="form-label fw-bold">
                        <i className="fas fa-sort-numeric-up text-info me-1"></i>
                        Transfer Miktarı *
                      </label>
                      <input
                        type="number"
                        className={`form-control form-control-lg ${validationErrors.quantity ? 'is-invalid' : formData.quantity > 0 ? 'is-valid' : ''}`}
                        name="quantity"
                        value={formData.quantity}
                        onChange={handleChange}
                        min="1"
                        max={availableStock?.availableQuantity || undefined}
                        required
                        placeholder="0"
                      />
                      {validationErrors.quantity && (
                        <div className="invalid-feedback">{validationErrors.quantity}</div>
                      )}
                      {availableStock && !validationErrors.quantity && (
                        <small className={`d-block mt-1 ${availableStock.availableQuantity > 0 ? 'text-success' : 'text-danger'}`}>
                          <i className={`fas fa-${availableStock.availableQuantity > 0 ? 'check-circle' : 'exclamation-circle'} me-1`}></i>
                          <strong>Kullanılabilir: {availableStock.availableQuantity}</strong> adet
                          <span className="text-muted"> (Toplam: {availableStock.quantity})</span>
                        </small>
                      )}
                    </div>

                    <div className="col-12">
                      <div className="alert alert-info d-flex align-items-start">
                        <i className="fas fa-lightbulb fa-2x me-3 mt-1"></i>
                        <div>
                          <strong>İpucu:</strong> Transfer işlemi, kaynak depodaki stoğu azaltıp hedef depodaki stoğu artıracaktır.
                          Stok, transfer yolda iken rezerve edilir.
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              )}

               {/* Step 2: Driver & Vehicle Info */}
               {currentStep === 2 && (
                 <div style={{ animation: 'fadeIn 0.3s ease-in' }}>
                  <h5 className="mb-3 text-primary">
                    <i className="fas fa-truck me-2"></i>
                    Taşıma Bilgilerini Girin
                  </h5>
                  
                  <div className="row g-3">
                    <div className="col-md-6">
                      <label className="form-label fw-bold">
                        <i className="fas fa-user me-1"></i>
                        Şoför Adı Soyadı *
                      </label>
                      <input
                        type="text"
                        className={`form-control form-control-lg ${validationErrors.driverName ? 'is-invalid' : formData.driverName.trim().length >= 3 ? 'is-valid' : ''}`}
                        name="driverName"
                        value={formData.driverName}
                        onChange={handleChange}
                        required
                        placeholder="Örn: Ahmet Yılmaz"
                        minLength="3"
                        maxLength="100"
                      />
                      {validationErrors.driverName && (
                        <div className="invalid-feedback">{validationErrors.driverName}</div>
                      )}
                    </div>

                    <div className="col-md-6">
                      <label className="form-label fw-bold">
                        <i className="fas fa-id-card me-1"></i>
                        TC Kimlik Numarası *
                      </label>
                      <input
                        type="text"
                        className={`form-control form-control-lg ${validationErrors.driverTcId ? 'is-invalid' : /^[0-9]{11}$/.test(formData.driverTcId) ? 'is-valid' : ''}`}
                        name="driverTcId"
                        value={formData.driverTcId}
                        onChange={handleChange}
                        required
                        placeholder="00000000000"
                        pattern="[0-9]{11}"
                        maxLength="11"
                      />
                      {validationErrors.driverTcId && (
                        <div className="invalid-feedback">{validationErrors.driverTcId}</div>
                      )}
                      {!validationErrors.driverTcId && formData.driverTcId && (
                        <small className="text-muted">{formData.driverTcId.length}/11 rakam</small>
                      )}
                    </div>

                    <div className="col-md-6">
                      <label className="form-label fw-bold">
                        <i className="fas fa-phone me-1"></i>
                        Şoför Telefonu *
                      </label>
                      <input
                        type="tel"
                        className={`form-control form-control-lg ${validationErrors.driverPhone ? 'is-invalid' : formData.driverPhone.trim().length >= 10 ? 'is-valid' : ''}`}
                        name="driverPhone"
                        value={formData.driverPhone}
                        onChange={handleChange}
                        required
                        placeholder="0555 555 55 55"
                        minLength="10"
                        maxLength="20"
                      />
                      {validationErrors.driverPhone && (
                        <div className="invalid-feedback">{validationErrors.driverPhone}</div>
                      )}
                    </div>

                    <div className="col-md-6">
                      <label className="form-label fw-bold">
                        <i className="fas fa-car me-1"></i>
                        Araç Plakası *
                      </label>
                      <input
                        type="text"
                        className={`form-control form-control-lg text-uppercase ${validationErrors.vehiclePlate ? 'is-invalid' : formData.vehiclePlate.trim().length >= 2 ? 'is-valid' : ''}`}
                        name="vehiclePlate"
                        value={formData.vehiclePlate}
                        onChange={handleChange}
                        required
                        placeholder="34 ABC 123"
                        minLength="2"
                        maxLength="20"
                      />
                      {validationErrors.vehiclePlate && (
                        <div className="invalid-feedback">{validationErrors.vehiclePlate}</div>
                      )}
                    </div>

                    <div className="col-md-6">
                      <label className="form-label fw-bold">
                        <i className="fas fa-calendar-alt me-1"></i>
                        Transfer Tarihi
                      </label>
                      <input
                        type="datetime-local"
                        className="form-control form-control-lg"
                        name="transferDate"
                        value={formData.transferDate}
                        onChange={handleChange}
                        required
                      />
                    </div>

                    <div className="col-12">
                      <label className="form-label fw-bold">
                        <i className="fas fa-sticky-note me-1"></i>
                        Notlar (Opsiyonel)
                      </label>
                      <textarea
                        className="form-control"
                        name="notes"
                        value={formData.notes}
                        onChange={handleChange}
                        rows="3"
                        placeholder="Transfer ile ilgili ek bilgiler..."
                        maxLength="500"
                      ></textarea>
                      <small className="text-muted float-end">{formData.notes.length}/500</small>
                    </div>
                  </div>
                </div>
              )}

               {/* Step 3: Summary & Confirm */}
               {currentStep === 3 && (
                 <div style={{ animation: 'fadeIn 0.3s ease-in' }}>
                  <h5 className="mb-3 text-primary">
                    <i className="fas fa-check-circle me-2"></i>
                    Transfer Özeti - Onaylayın
                  </h5>
                  
                  <div className="row g-3">
                    <div className="col-md-6">
                      <div className="card h-100 border-danger">
                        <div className="card-header bg-danger text-white">
                          <i className="fas fa-warehouse me-2"></i>
                          Kaynak Depo
                        </div>
                        <div className="card-body">
                          <h6 className="fw-bold">{sourceWarehouse?.name}</h6>
                          <p className="mb-0 text-muted small">
                            <i className="fas fa-map-marker-alt me-1"></i>
                            {sourceWarehouse?.location}
                          </p>
                        </div>
                      </div>
                    </div>

                    <div className="col-md-6">
                      <div className="card h-100 border-success">
                        <div className="card-header bg-success text-white">
                          <i className="fas fa-warehouse me-2"></i>
                          Hedef Depo
                        </div>
                        <div className="card-body">
                          <h6 className="fw-bold">{destinationWarehouse?.name}</h6>
                          <p className="mb-0 text-muted small">
                            <i className="fas fa-map-marker-alt me-1"></i>
                            {destinationWarehouse?.location}
                          </p>
                        </div>
                      </div>
                    </div>

                    <div className="col-12">
                      <div className="card border-primary">
                        <div className="card-header bg-primary text-white">
                          <i className="fas fa-box me-2"></i>
                          Ürün Detayları
                        </div>
                        <div className="card-body">
                          <div className="row">
                            <div className="col-md-6">
                              <strong>Ürün Adı:</strong>
                              <p className="mb-2">{selectedProduct?.name}</p>
                            </div>
                            <div className="col-md-3">
                              <strong>SKU:</strong>
                              <p className="mb-2">{selectedProduct?.sku}</p>
                            </div>
                            <div className="col-md-3">
                              <strong>Miktar:</strong>
                              <p className="mb-2">
                                <span className="badge bg-primary fs-5">{formData.quantity} adet</span>
                              </p>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>

                    <div className="col-12">
                      <div className="card border-info">
                        <div className="card-header bg-info text-white">
                          <i className="fas fa-truck me-2"></i>
                          Taşıma Bilgileri
                        </div>
                        <div className="card-body">
                          <div className="row">
                            <div className="col-md-6 mb-2">
                              <i className="fas fa-user me-2 text-muted"></i>
                              <strong>Şoför:</strong> {formData.driverName}
                            </div>
                            <div className="col-md-6 mb-2">
                              <i className="fas fa-id-card me-2 text-muted"></i>
                              <strong>TC:</strong> {formData.driverTcId}
                            </div>
                            <div className="col-md-6 mb-2">
                              <i className="fas fa-phone me-2 text-muted"></i>
                              <strong>Telefon:</strong> {formData.driverPhone}
                            </div>
                            <div className="col-md-6 mb-2">
                              <i className="fas fa-car me-2 text-muted"></i>
                              <strong>Plaka:</strong> {formData.vehiclePlate.toUpperCase()}
                            </div>
                            {formData.notes && (
                              <div className="col-12 mt-2">
                                <i className="fas fa-sticky-note me-2 text-muted"></i>
                                <strong>Notlar:</strong>
                                <p className="mb-0 text-muted">{formData.notes}</p>
                              </div>
                            )}
                          </div>
                        </div>
                      </div>
                    </div>

                    <div className="col-12">
                      <div className="alert alert-warning">
                        <i className="fas fa-exclamation-triangle me-2"></i>
                        <strong>Dikkat:</strong> Transfer onaylandığında, kaynak depodaki stok rezerve edilecektir.
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </div>

            <div className="modal-footer bg-light">
              {currentStep > 1 && (
                <button
                  type="button"
                  className="btn btn-outline-secondary"
                  onClick={handlePrevious}
                  disabled={loading}
                >
                  <i className="fas fa-arrow-left me-2"></i>
                  Geri
                </button>
              )}
              
              <button
                type="button"
                className="btn btn-secondary"
                onClick={onClose}
                disabled={loading}
              >
                <i className="fas fa-times me-2"></i>
                İptal
              </button>

              {currentStep < 3 ? (
                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={handleNext}
                >
                  İleri
                  <i className="fas fa-arrow-right ms-2"></i>
                </button>
              ) : (
                <button
                  type="submit"
                  className="btn btn-success"
                  disabled={loading || !availableStock || availableStock.availableQuantity === 0}
                >
                  {loading ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                      Transfer Oluşturuluyor...
                    </>
                  ) : (
                    <>
                      <i className="fas fa-check-double me-2"></i>
                      Transferi Onayla ve Oluştur
                    </>
                  )}
                </button>
              )}
            </div>
          </form>
        </div>
      </div>
    </div>
  );
};

export default StockTransferModal;
