import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';

const StockTransferModal = ({ stock, onSuccess, onClose }) => {
  const [currentStep, setCurrentStep] = useState(1);
  
  // Get current date/time in Turkey timezone (GMT+3)
  const getTurkeyDateTime = () => {
    const now = new Date();
    // Convert to Turkey time (GMT+3)
    const turkeyTime = new Date(now.toLocaleString('en-US', { timeZone: 'Europe/Istanbul' }));
    // Format as datetime-local input value (YYYY-MM-DDTHH:mm)
    const year = turkeyTime.getFullYear();
    const month = String(turkeyTime.getMonth() + 1).padStart(2, '0');
    const day = String(turkeyTime.getDate()).padStart(2, '0');
    const hours = String(turkeyTime.getHours()).padStart(2, '0');
    const minutes = String(turkeyTime.getMinutes()).padStart(2, '0');
    return `${year}-${month}-${day}T${hours}:${minutes}`;
  };
  
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
    transferDate: getTurkeyDateTime()
  });

  const [warehouses, setWarehouses] = useState([]);
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadingData, setLoadingData] = useState(true);
  const [error, setError] = useState(null);
  const [availableStock, setAvailableStock] = useState(null);
  const [validationErrors, setValidationErrors] = useState({});
  const [submitSuccess, setSubmitSuccess] = useState(false);
  const [createdTransferId, setCreatedTransferId] = useState(null);

  // Fetch available quantity for selected source warehouse and product
  const fetchAvailableStock = useCallback(async () => {
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
  }, [formData.sourceWarehouseId, formData.productId]);

useEffect(() => {
    console.log('StockTransferModal mounted, fetching data...');
    const fetchData = async () => {
      setLoadingData(true);
      await fetchWarehouses();
      if (!stock) {
        await fetchProducts();
      }
      setLoadingData(false);
    };
    fetchData();
}, [stock]);

  useEffect(() => {
    if (formData.sourceWarehouseId && formData.productId) {
      fetchAvailableStock();
    }
  }, [formData.sourceWarehouseId, formData.productId, fetchAvailableStock]);

  const fetchWarehouses = async () => {
    try {
      console.log('Fetching warehouses from /api/warehouses...');
      const response = await axios.get('/api/warehouses');
      console.log('Warehouses API response:', response.data);
      
      if (!response.data || !Array.isArray(response.data)) {
        console.error('Invalid warehouses response format:', response.data);
        setError('Depo verisi hatalı formatta');
        setWarehouses([]);
        return;
      }

      if (response.data.length === 0) {
        console.error('No warehouses found in database');
        setError('Sistemde hiç depo bulunamadı. Lütfen önce Depolar sayfasından depo ekleyin.');
        setWarehouses([]);
        return;
      }

      // Log all warehouses for debugging
      console.log('🏭 RAW API Response:');
      console.table(response.data.map(w => ({
        id: w.id,
        name: w.name,
        location: w.location,
        isActive: w.isActive,
        type: typeof w.isActive,
        raw: JSON.stringify(w.isActive)
      })));
      
      console.log('🔍 Full warehouse objects:', response.data);

      // TEMPORARY FIX: Show ALL warehouses for now (no filtering)
      // TODO: Fix isActive filtering logic after testing
      console.log('⚠️ TEMPORARILY SHOWING ALL WAREHOUSES (active filtering disabled for testing)');
      
      const activeWarehouses = response.data; // Show all warehouses temporarily
      
      /* ORIGINAL FILTERING (commented out for debugging):
      const activeWarehouses = response.data.filter(w => {
        const shouldExclude = w.isActive === false;
        const shouldInclude = !shouldExclude;
        console.log(`Warehouse ${w.id} (${w.name}): isActive=${JSON.stringify(w.isActive)}, shouldExclude=${shouldExclude}, INCLUDING=${shouldInclude}`);
        return shouldInclude;
      });
      */
      
      console.log(`✅ Total warehouses: ${response.data.length}, Showing: ${activeWarehouses.length}`);
      console.log('Warehouses to display:', activeWarehouses);
      setWarehouses(activeWarehouses);
      
      if (activeWarehouses.length === 0) {
        setError('Sistemde hiç depo bulunamadı. Lütfen Depolar sayfasından depo ekleyin.');
      }
    } catch (error) {
      console.error('❌ Error fetching warehouses:', error);
      console.error('Error details:', error.response?.data, error.response?.status, error.message);
      
      let errorMessage = 'Depolar yüklenirken hata oluştu';
      if (error.response) {
        errorMessage += `: ${error.response.status} - ${error.response.data || error.response.statusText}`;
      } else if (error.request) {
        errorMessage += ': Backend sunucusuna ulaşılamıyor. Lütfen backend\'in çalıştığından emin olun (http://localhost:8080)';
      } else {
        errorMessage += `: ${error.message}`;
      }
      
      setError(errorMessage);
      setWarehouses([]);
    }
  };

  const fetchProducts = async () => {
    try {
      console.log('Fetching products from /api/products...');
      const response = await axios.get('/api/products');
      console.log('Products API response:', response.data?.length, 'products');
      setProducts(response.data || []);
    } catch (error) {
      console.error('Error fetching products:', error);
      console.error('Error details:', error.response?.data, error.message);
      setProducts([]);
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

    // Live validation for quantity against availableStock
    if (name === 'quantity') {
      const qty = parseInt(value || '0', 10);
      if (!Number.isFinite(qty) || qty <= 0) {
        setValidationErrors(prev => ({ ...prev, quantity: 'Geçerli bir miktar giriniz' }));
      } else if (availableStock && qty > (availableStock.availableQuantity || 0)) {
        setValidationErrors(prev => ({ ...prev, quantity: `Maksimum ${availableStock.availableQuantity} adet transfer edilebilir` }));
      } else if (validationErrors.quantity) {
        setValidationErrors(prev => ({ ...prev, quantity: '' }));
      }
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

    // Show loading state for at least 1.5 seconds to let user see the summary
    await new Promise(resolve => setTimeout(resolve, 1500));

    try {
      // Parse the datetime-local value as Turkey time and convert to ISO
      const parseDateInTurkeyTimezone = (dateTimeString) => {
        // dateTimeString format: "2024-10-23T10:28"
        // This is already in Turkey local time from the datetime-local input
        
        // Split the datetime string
        const [datePart, timePart] = dateTimeString.split('T');
        const [year, month, day] = datePart.split('-');
        const [hours, minutes] = timePart.split(':');
        
        // Since backend timezone is set to Europe/Istanbul (GMT+3),
        // we send the datetime as-is (without timezone offset)
        // Backend will interpret this as Turkey local time
        const isoString = `${year}-${month}-${day}T${hours}:${minutes}:00`;
        
        console.log('🕐 Datetime Conversion:');
        console.log('  Input (Turkey local):', dateTimeString);
        console.log('  Output (ISO for backend):', isoString);
        
        return isoString;
      };
      
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
        transferDate: parseDateInTurkeyTimezone(formData.transferDate)
      };

      const response = await axios.post('/api/stock-transfers', transferData);
      setCreatedTransferId(response.data.id);
      
      // Wait another moment before transitioning to success (ensure smooth transition)
      await new Promise(resolve => setTimeout(resolve, 800));
      
      setSubmitSuccess(true);
      setCurrentStep(4); // Move to success step
      setLoading(false);
      // Don't call onSuccess() immediately - let user see success message
    } catch (error) {
      console.error('Error creating transfer:', error);
      setError(error.response?.data || 'Transfer oluşturulurken hata oluştu');
      setLoading(false);
    }
  };

  const handleClose = () => {
    if (submitSuccess) {
      onSuccess(); // Refresh data when closing after success
    }
    onClose();
  };

  const sourceWarehouse = warehouses.find(w => w.id === parseInt(formData.sourceWarehouseId));
  const destinationWarehouse = warehouses.find(w => w.id === parseInt(formData.destinationWarehouseId));
  const selectedProduct = stock?.product || products.find(p => p.id === parseInt(formData.productId));

  const steps = submitSuccess ? [
    { number: 1, title: 'Transfer Detayları', icon: 'fa-boxes' },
    { number: 2, title: 'Taşıma Bilgileri', icon: 'fa-truck' },
    { number: 3, title: 'Özet & Onay', icon: 'fa-check-circle' },
    { number: 4, title: 'Tamamlandı', icon: 'fa-check-double' }
  ] : [
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
              onClick={handleClose}
              disabled={loading && !submitSuccess}
            ></button>
          </div>

          {/* Progress Steps */}
          <div className="px-4 pt-4 pb-2">
            <div className="d-flex justify-content-between align-items-center position-relative mb-3">
              <div className="position-absolute w-100 top-50 start-0" style={{ height: '2px', background: '#e9ecef', zIndex: 0 }}>
                <div 
                  className={`h-100 transition-all ${submitSuccess ? 'bg-success' : 'bg-primary'}`}
                  style={{ width: `${((currentStep - 1) / (steps.length - 1)) * 100}%`, transition: 'width 0.3s ease' }}
                ></div>
              </div>
              {steps.map((step) => (
                <div key={step.number} className="text-center position-relative" style={{ zIndex: 1, flex: 1 }}>
                  <div 
                    className={`mx-auto rounded-circle d-flex align-items-center justify-content-center ${
                      currentStep >= step.number 
                        ? (submitSuccess && step.number === 4 ? 'bg-success text-white' : 'bg-primary text-white')
                        : 'bg-light text-muted'
                    }`}
                    style={{ width: '50px', height: '50px', transition: 'all 0.3s ease', border: '3px solid white' }}
                  >
                    <i className={`fas ${step.icon} fa-lg`}></i>
                  </div>
                  <div className={`mt-2 small fw-bold ${
                    currentStep >= step.number 
                      ? (submitSuccess && step.number === 4 ? 'text-success' : 'text-primary')
                      : 'text-muted'
                  }`}>
                    {step.title}
                  </div>
                </div>
              ))}
            </div>
          </div>

          <form onSubmit={handleSubmit}>
            <div className="modal-body" style={{ minHeight: '400px' }}>
              {loadingData && (
                <div className="alert alert-info" role="alert">
                  <div className="d-flex align-items-center">
                    <div className="spinner-border spinner-border-sm me-2" role="status">
                      <span className="visually-hidden">Yükleniyor...</span>
                    </div>
                    <strong>Depolar ve ürünler yükleniyor...</strong>
                  </div>
                </div>
              )}
              
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
                        className={`form-control form-control-lg ${validationErrors.quantity ? 'is-invalid' : ((formData.quantity) && ((!availableStock) || ((parseInt(formData.quantity, 10) > 0) && (parseInt(formData.quantity, 10) <= ((availableStock?.availableQuantity) || 0))))) ? 'is-valid' : ''}`}
                        name="quantity"
                        value={formData.quantity}
                        onChange={handleChange}
                        min="1"
                        max={availableStock?.availableQuantity || undefined}
                        required
                        placeholder="0"
                        inputMode="numeric"
                        onKeyDown={(e) => {
                          if (["e", "E", "+", "-", ",", "."].includes(e.key)) {
                            e.preventDefault();
                          }
                        }}
                        onWheel={(e) => e.currentTarget.blur()}
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
               {currentStep === 3 && !submitSuccess && (
                 <div style={{ animation: 'fadeIn 0.3s ease-in' }}>
                  {loading ? (
                    <div className="py-4">
                      <div className="text-center mb-4">
                        <div className="spinner-border text-primary mb-3" style={{ width: '3.5rem', height: '3.5rem' }} role="status">
                          <span className="visually-hidden">Yükleniyor...</span>
                        </div>
                        <h4 className="text-primary mb-2">
                          <i className="fas fa-cog fa-spin me-2"></i>
                          Transfer Oluşturuluyor...
                        </h4>
                        <p className="text-muted mb-3">
                          Transfer kaydı sisteme ekleniyor, lütfen bekleyin.
                        </p>
                        <div className="progress mx-auto" style={{ maxWidth: '500px', height: '8px' }}>
                          <div className="progress-bar progress-bar-striped progress-bar-animated bg-primary" 
                               role="progressbar" 
                               style={{ width: '100%' }}>
                          </div>
                        </div>
                      </div>

                      {/* Show summary while loading */}
                      <div className="row g-2 mt-3">
                        <div className="col-md-4">
                          <div className="card border-danger h-100">
                            <div className="card-body text-center py-3">
                              <i className="fas fa-warehouse text-danger fa-2x mb-2"></i>
                              <div className="small text-muted">Kaynak</div>
                              <div className="fw-bold">{sourceWarehouse?.name}</div>
                            </div>
                          </div>
                        </div>
                        <div className="col-md-4">
                          <div className="card border-success h-100">
                            <div className="card-body text-center py-3">
                              <i className="fas fa-warehouse text-success fa-2x mb-2"></i>
                              <div className="small text-muted">Hedef</div>
                              <div className="fw-bold">{destinationWarehouse?.name}</div>
                            </div>
                          </div>
                        </div>
                        <div className="col-md-4">
                          <div className="card border-primary h-100">
                            <div className="card-body text-center py-3">
                              <i className="fas fa-boxes text-primary fa-2x mb-2"></i>
                              <div className="small text-muted">Miktar</div>
                              <div className="fw-bold fs-5">{formData.quantity} Adet</div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  ) : (
                    <>
                      <div className="alert alert-primary d-flex align-items-center mb-4">
                        <div className="flex-shrink-0">
                          <i className="fas fa-info-circle fa-2x me-3"></i>
                        </div>
                        <div>
                          <h5 className="alert-heading mb-1">
                            <i className="fas fa-clipboard-check me-2"></i>
                            Transfer Özeti
                          </h5>
                          <p className="mb-0">
                            Lütfen transfer bilgilerini dikkatlice kontrol edin. Onayladığınızda transfer işlemi başlatılacaktır.
                          </p>
                        </div>
                      </div>
                  
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
                  </>
                  )}
                </div>
              )}

              {/* Step 4: Success */}
              {currentStep === 4 && submitSuccess && (
                <div style={{ animation: 'fadeIn 0.3s ease-in' }} className="text-center py-5">
                  <div className="mb-4">
                    <div className="mx-auto rounded-circle bg-success bg-opacity-10 d-inline-flex align-items-center justify-content-center" 
                         style={{ width: '120px', height: '120px' }}>
                      <i className="fas fa-check-circle text-success" style={{ fontSize: '4rem' }}></i>
                    </div>
                  </div>
                  
                  <h3 className="text-success mb-3">
                    <i className="fas fa-check-double me-2"></i>
                    Transfer Başarıyla Oluşturuldu!
                  </h3>
                  
                  <p className="text-muted mb-4">
                    Transfer kaydı <strong>#{createdTransferId}</strong> numarası ile sisteme başarıyla kaydedildi.
                  </p>

                  <div className="row g-3 mb-4">
                    <div className="col-md-4">
                      <div className="card border-primary">
                        <div className="card-body">
                          <div className="d-flex align-items-center">
                            <div className="bg-primary bg-opacity-10 rounded-circle p-3 me-3">
                              <i className="fas fa-warehouse text-primary fa-lg"></i>
                            </div>
                            <div className="text-start">
                              <small className="text-muted d-block">Kaynak</small>
                              <strong className="text-truncate d-block">{sourceWarehouse?.name}</strong>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                    <div className="col-md-4">
                      <div className="card border-success">
                        <div className="card-body">
                          <div className="d-flex align-items-center">
                            <div className="bg-success bg-opacity-10 rounded-circle p-3 me-3">
                              <i className="fas fa-warehouse text-success fa-lg"></i>
                            </div>
                            <div className="text-start">
                              <small className="text-muted d-block">Hedef</small>
                              <strong className="text-truncate d-block">{destinationWarehouse?.name}</strong>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                    <div className="col-md-4">
                      <div className="card border-info">
                        <div className="card-body">
                          <div className="d-flex align-items-center">
                            <div className="bg-info bg-opacity-10 rounded-circle p-3 me-3">
                              <i className="fas fa-boxes text-info fa-lg"></i>
                            </div>
                            <div className="text-start">
                              <small className="text-muted d-block">Miktar</small>
                              <strong className="fs-5">{formData.quantity} Adet</strong>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="alert alert-info d-flex align-items-start">
                    <i className="fas fa-info-circle fa-2x me-3 mt-1"></i>
                    <div className="text-start">
                      <strong>Sonraki Adımlar:</strong>
                      <ul className="mb-0 mt-2 text-start">
                        <li>Transfer "Transfer Geçmişi" bölümünden takip edilebilir</li>
                        <li>Şoför <strong>{formData.driverName}</strong> transferi teslim alabilir</li>
                        <li>Araç plakası: <strong>{formData.vehiclePlate}</strong></li>
                        <li>Transfer durumunu güncellemek için "Yola Çıkar" veya "Tamamla" butonlarını kullanabilirsiniz</li>
                      </ul>
                    </div>
                  </div>
                </div>
              )}
            </div>

            <div className="modal-footer bg-light">
              {submitSuccess ? (
                // Success step buttons
                <button
                  type="button"
                  className="btn btn-success px-5"
                  onClick={handleClose}
                >
                  <i className="fas fa-check me-2"></i>
                  Tamam, Kapat
                </button>
              ) : (
                // Normal flow buttons
                <>
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
                      className="btn btn-primary px-4"
                      onClick={handleNext}
                      disabled={loadingData}
                    >
                      İleri
                      <i className="fas fa-arrow-right ms-2"></i>
                    </button>
                  ) : (
                    <button
                      type="submit"
                      className="btn btn-success btn-lg px-5"
                      disabled={loading || loadingData || !availableStock || availableStock.availableQuantity === 0}
                    >
                      {loading ? (
                        <>
                          <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                          Lütfen Bekleyin...
                        </>
                      ) : (
                        <>
                          <i className="fas fa-check-double me-2"></i>
                          Transferi Onayla ve Oluştur
                        </>
                      )}
                    </button>
                  )}
                </>
              )}
            </div>
          </form>
        </div>
      </div>
    </div>
  );
};

export default StockTransferModal;
