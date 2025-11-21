import React, { useState, useEffect, useCallback, useMemo } from 'react';
import axios from 'axios';
import {
  extractPhoneDigits,
  isPhoneComplete,
  formatPhoneForSubmit,
  formatPhoneForDisplay,
  formatPhoneInputValue,
  PHONE_PLACEHOLDER
} from '../utils/phone';

const StockTransferModal = ({ stock, onSuccess, onClose, lockToCustomerDelivery = false }) => {
  const [currentStep, setCurrentStep] = useState(1);
  const initialTransferType = lockToCustomerDelivery ? 'CUSTOMER_DELIVERY' : 'WAREHOUSE';
  
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
    driverName: '',
    driverTcId: '',
    driverPhone: '',
    vehiclePlate: '',
    notes: '',
    transferDate: getTurkeyDateTime(),
    transferType: initialTransferType,
    customerFullName: '',
    customerPhone: '',
    customerAddress: ''
  });

  const [warehouses, setWarehouses] = useState([]);
  const [products, setProducts] = useState([]);
  const [warehouseStocks, setWarehouseStocks] = useState([]);
  const [stockLoading, setStockLoading] = useState(false);
  const [transferItems, setTransferItems] = useState([]);
  const [itemForm, setItemForm] = useState({ productId: '', quantity: '' });
  const [loading, setLoading] = useState(false);
  const [loadingData, setLoadingData] = useState(true);
  const [error, setError] = useState(null);
  const [validationErrors, setValidationErrors] = useState({});
  const [submitSuccess, setSubmitSuccess] = useState(false);
  const [createdTransferId, setCreatedTransferId] = useState(null);
  const [stockSearchTerm, setStockSearchTerm] = useState('');
  const INITIAL_VISIBLE_STOCKS = 12;
  const [visibleStockCount, setVisibleStockCount] = useState(INITIAL_VISIBLE_STOCKS);
  const transferTypeOptions = [
    { key: 'WAREHOUSE', label: 'Depo Transferi', icon: 'fa-warehouse', accent: 'primary', hint: 'Şubeler arası stok taşıma' },
    { key: 'CUSTOMER_DELIVERY', label: 'Müşteri Sevkiyatı', icon: 'fa-shipping-fast', accent: 'info', hint: 'Depodan müşteriye sevk' }
  ];

  const getPhoneValidationClass = (value, error) => {
    if (error) return 'is-invalid';
    if (isPhoneComplete(value)) return 'is-valid';
    return '';
  };

  const handlePhoneInput = (field, rawValue) => {
    const digits = extractPhoneDigits(rawValue);
    setFormData(prev => ({
      ...prev,
      [field]: digits
    }));
    if (validationErrors[field]) {
      setValidationErrors(prev => ({ ...prev, [field]: '' }));
    }
  };

  useEffect(() => {
    if (lockToCustomerDelivery) {
      setFormData(prev => {
        if (prev.transferType === 'CUSTOMER_DELIVERY') {
          return prev;
        }
        return {
          ...prev,
          transferType: 'CUSTOMER_DELIVERY'
        };
      });
      setCurrentStep(1);
    }
  }, [lockToCustomerDelivery]);

  const calculateAvailableQuantity = (stockRecord) => {
    if (!stockRecord) return 0;
    if (typeof stockRecord.availableQuantity === 'number') {
      return stockRecord.availableQuantity;
    }
    const reserved = stockRecord.reservedQuantity || 0;
    const consigned = stockRecord.consignedQuantity || 0;
    return Math.max(0, (stockRecord.quantity || 0) - reserved - consigned);
  };

  const syncItemsWithStocks = useCallback((stocks) => {
    setTransferItems(prev =>
      prev.map(item => {
        const stockMatch = stocks.find(s => s.product?.id === item.productId);
        const available = calculateAvailableQuantity(stockMatch);
        return {
          ...item,
          availableQuantity: available,
          quantity: Math.min(item.quantity, available || item.quantity)
        };
      })
    );
  }, []);

  const fetchWarehouseStocks = useCallback(async (warehouseId) => {
    if (!warehouseId) {
      setWarehouseStocks([]);
      setTransferItems([]);
      return;
    }
    try {
      setStockLoading(true);
      const response = await axios.get(`/api/stocks/warehouse/${warehouseId}`);
      const stocks = Array.isArray(response.data) ? response.data : [];
      setWarehouseStocks(stocks);
      syncItemsWithStocks(stocks);
    } catch (err) {
      console.error('Error fetching warehouse stocks:', err);
      setWarehouseStocks([]);
      setTransferItems([]);
    } finally {
      setStockLoading(false);
    }
  }, [syncItemsWithStocks]);

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
    if (stock?.product?.id && stock?.warehouse?.id) {
      setFormData(prev => ({
        ...prev,
        sourceWarehouseId: stock.warehouse.id
      }));
      setTransferItems([{
        productId: stock.product.id,
        productName: stock.product.name,
        sku: stock.product.sku,
        quantity: 1,
        availableQuantity: calculateAvailableQuantity(stock)
      }]);
    }
  }, [stock]);

  useEffect(() => {
    if (formData.sourceWarehouseId) {
      fetchWarehouseStocks(formData.sourceWarehouseId);
    } else {
      setWarehouseStocks([]);
      setTransferItems([]);
    }
  }, [formData.sourceWarehouseId, fetchWarehouseStocks]);

  useEffect(() => {
    setStockSearchTerm('');
    setVisibleStockCount(INITIAL_VISIBLE_STOCKS);
  }, [formData.sourceWarehouseId]);

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

  const getProductDetails = useCallback((productId) => {
    const numericId = parseInt(productId);
    const stockRecord = warehouseStocks.find(s => s.product?.id === numericId);
    if (stockRecord) {
      return {
        name: stockRecord.product?.name,
        sku: stockRecord.product?.sku
      };
    }
    if (stock?.product?.id === numericId) {
      return {
        name: stock.product.name,
        sku: stock.product.sku
      };
    }
    const fallback = products.find(p => p.id === numericId);
    if (fallback) {
      return { name: fallback.name, sku: fallback.sku };
    }
    return { name: 'Ürün', sku: '' };
  }, [warehouseStocks, stock, products]);

  const getAvailableForProduct = useCallback((productId) => {
    const numericId = parseInt(productId);
    const stockRecord = warehouseStocks.find(s => s.product?.id === numericId);
    if (stockRecord) {
      return calculateAvailableQuantity(stockRecord);
    }
    if (stock?.product?.id === numericId && stock?.warehouse?.id === parseInt(formData.sourceWarehouseId)) {
      return calculateAvailableQuantity(stock);
    }
    return 0;
  }, [warehouseStocks, stock, formData.sourceWarehouseId]);

  const handleItemFormChange = (field, value) => {
    setItemForm(prev => ({ ...prev, [field]: value }));
    if (validationErrors[field]) {
      setValidationErrors(prev => ({ ...prev, [field]: '' }));
    }
    if (validationErrors.transferItems) {
      setValidationErrors(prev => ({ ...prev, transferItems: '' }));
    }
  };

  const handleAddItem = () => {
    const productId = itemForm.productId;
    const quantity = parseInt(itemForm.quantity, 10);
    const errors = {};

    if (!productId) {
      errors.itemProductId = 'Ürün seçiniz';
    }
    if (!quantity || quantity <= 0) {
      errors.itemQuantity = 'Geçerli bir miktar giriniz';
    }

    if (productId) {
      const available = getAvailableForProduct(productId);
      const existing = transferItems.find(item => item.productId === parseInt(productId));
      const alreadySelected = existing ? existing.quantity : 0;
      if (available <= 0) {
        errors.itemProductId = 'Bu üründe stok bulunmuyor';
      } else if (quantity && quantity + alreadySelected > available) {
        errors.itemQuantity = `Maksimum ${available - alreadySelected} adet eklenebilir`;
      }
    }

    if (Object.keys(errors).length > 0) {
      setValidationErrors(prev => ({ ...prev, ...errors }));
      return;
    }

    const numericId = parseInt(productId);
    const available = getAvailableForProduct(productId);
    const details = getProductDetails(productId);

    setTransferItems(prev => {
      const exists = prev.find(item => item.productId === numericId);
      if (exists) {
        return prev.map(item => item.productId === numericId
          ? { ...item, quantity: item.quantity + quantity, availableQuantity: available }
          : item
        );
      }
      return [
        ...prev,
        {
          productId: numericId,
          productName: details.name,
          sku: details.sku,
          quantity,
          availableQuantity: available
        }
      ];
    });
    setItemForm({ productId: '', quantity: '' });
    setValidationErrors(prev => ({ ...prev, itemProductId: '', itemQuantity: '' }));
  };

  const handleRemoveItem = (productId) => {
    setTransferItems(prev => prev.filter(item => item.productId !== productId));
  };

  const handleItemQuantityUpdate = (productId, value) => {
    const numericValue = parseInt(value, 10);
    setTransferItems(prev =>
      prev.map(item => {
        if (item.productId !== productId) {
          return item;
        }
        const available = item.availableQuantity ?? getAvailableForProduct(productId);
        const safeValue = Math.min(Math.max(isNaN(numericValue) ? 0 : numericValue, 0), available);
        return { ...item, quantity: safeValue };
      })
    );
    if (validationErrors.transferItems) {
      setValidationErrors(prev => ({ ...prev, transferItems: '' }));
    }
  };
  

  const handleChange = (e) => {
    const { name, value } = e.target;

    if (name === 'driverPhone' || name === 'customerPhone') {
      handlePhoneInput(name, value);
      return;
    }
    
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
      setTransferItems([]);
      setItemForm({ productId: '', quantity: '' });
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

  const handleTransferTypeChange = (type) => {
    if (lockToCustomerDelivery && type !== 'CUSTOMER_DELIVERY') {
      return;
    }
    if (formData.transferType === type) return;
    setFormData(prev => ({
      ...prev,
      transferType: type,
      destinationWarehouseId: type === 'CUSTOMER_DELIVERY' ? '' : prev.destinationWarehouseId,
      customerFullName: type === 'CUSTOMER_DELIVERY' ? prev.customerFullName : '',
      customerPhone: type === 'CUSTOMER_DELIVERY' ? prev.customerPhone : '',
      customerAddress: type === 'CUSTOMER_DELIVERY' ? prev.customerAddress : ''
    }));
    setValidationErrors({});
    setCurrentStep(1);
  };

  const validateStep = (step) => {
    const errors = {};
    const isCustomerTransfer = formData.transferType === 'CUSTOMER_DELIVERY';

    if (step === 1) {
      if (!formData.sourceWarehouseId) errors.sourceWarehouseId = 'Kaynak depo zorunludur';
      if (!isCustomerTransfer) {
        if (!formData.destinationWarehouseId) errors.destinationWarehouseId = 'Hedef depo zorunludur';
        if (String(formData.sourceWarehouseId) === String(formData.destinationWarehouseId)) {
          errors.destinationWarehouseId = 'Kaynak ve hedef depo farklı olmalıdır';
        }
      } else {
        if (!formData.customerFullName.trim()) errors.customerFullName = 'Müşteri adı zorunludur';
        if (!isPhoneComplete(formData.customerPhone)) {
          errors.customerPhone = 'Telefon numarası 10 haneli olmalıdır';
        }
        if (!formData.customerAddress.trim()) errors.customerAddress = 'Adres zorunludur';
      }
      if (transferItems.length === 0) {
        errors.transferItems = 'En az bir ürün eklemelisiniz';
      } else {
        transferItems.forEach(item => {
          if (!item.quantity || item.quantity <= 0) {
            errors.transferItems = 'Her ürün için geçerli miktar giriniz';
          }
          const available = item.availableQuantity ?? getAvailableForProduct(item.productId);
          if (available && item.quantity > available) {
            errors.transferItems = 'Ürün miktarları mevcut stoktan fazla olamaz';
          }
        });
      }
    } else if (step === 2) {
      if (!formData.driverName.trim()) errors.driverName = 'Şoför adı zorunludur';
      if (formData.driverName.trim().length < 3) errors.driverName = 'Şoför adı en az 3 karakter olmalıdır';
      if (!/^[0-9]{11}$/.test(formData.driverTcId)) errors.driverTcId = 'TC Kimlik No 11 haneli olmalıdır';
      if (!isPhoneComplete(formData.driverPhone)) errors.driverPhone = 'Telefon numarası 10 haneli olmalıdır';
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
        sourceWarehouseId: parseInt(formData.sourceWarehouseId),
        destinationWarehouseId: formData.transferType === 'WAREHOUSE' && formData.destinationWarehouseId
          ? parseInt(formData.destinationWarehouseId)
          : null,
        driverName: formData.driverName.trim(),
        driverTcId: formData.driverTcId.trim(),
        driverPhone: formatPhoneForSubmit(formData.driverPhone),
        vehiclePlate: formData.vehiclePlate.trim().toUpperCase(),
        notes: formData.notes.trim(),
        transferDate: parseDateInTurkeyTimezone(formData.transferDate),
        transferType: formData.transferType,
        items: transferItems.map(item => ({
          productId: item.productId,
          quantity: item.quantity
        }))
      };

      if (formData.transferType === 'CUSTOMER_DELIVERY') {
        transferData.customerFullName = formData.customerFullName.trim();
        transferData.customerPhone = formatPhoneForSubmit(formData.customerPhone);
        transferData.customerAddress = formData.customerAddress.trim();
      }

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

  const isCustomerTransfer = formData.transferType === 'CUSTOMER_DELIVERY';
  const canChangeTransferType = !lockToCustomerDelivery;
  const sourceWarehouse = warehouses.find(w => w.id === parseInt(formData.sourceWarehouseId));
  const destinationWarehouse = !isCustomerTransfer
    ? warehouses.find(w => w.id === parseInt(formData.destinationWarehouseId))
    : null;
  const formattedDriverPhone = formatPhoneForDisplay(formData.driverPhone);
  const formattedCustomerPhone = formatPhoneForDisplay(formData.customerPhone);
  const totalTransferQuantity = transferItems.reduce((sum, item) => sum + (item.quantity || 0), 0);
  const filteredWarehouseStocks = useMemo(() => {
    if (!stockSearchTerm.trim()) return warehouseStocks;
    const q = stockSearchTerm.trim().toLocaleLowerCase('tr-TR');
    return warehouseStocks.filter(stockItem => {
      const name = stockItem.product?.name ? stockItem.product.name.toLocaleLowerCase('tr-TR') : '';
      const sku = stockItem.product?.sku ? stockItem.product.sku.toLocaleLowerCase('tr-TR') : '';
      const barcode = stockItem.product?.barcode ? stockItem.product.barcode.toLocaleLowerCase('tr-TR') : '';
      return name.includes(q) || sku.includes(q) || barcode.includes(q);
    });
  }, [warehouseStocks, stockSearchTerm]);
  useEffect(() => {
    setVisibleStockCount(INITIAL_VISIBLE_STOCKS);
  }, [stockSearchTerm, filteredWarehouseStocks.length]);
  const limitedStockList = useMemo(
    () => filteredWarehouseStocks.slice(0, visibleStockCount),
    [filteredWarehouseStocks, visibleStockCount]
  );
  const hasMoreStocks = filteredWarehouseStocks.length > visibleStockCount;

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
                  <div className="row g-3 mb-4">
                    {transferTypeOptions.map(option => {
                      const isActive = formData.transferType === option.key;
                      const disabled = lockToCustomerDelivery && option.key !== 'CUSTOMER_DELIVERY';
                      return (
                        <div className="col-md-6" key={option.key}>
                          <button
                            type="button"
                            className={`transfer-type-toggle w-100 ${isActive ? 'active' : ''}`}
                            onClick={() => handleTransferTypeChange(option.key)}
                            disabled={disabled}
                          >
                            <div className="d-flex align-items-center">
                              <div className={`transfer-type-icon me-3 ${isActive ? 'active' : ''}`}>
                                <i className={`fas ${option.icon}`}></i>
                              </div>
                              <div>
                                <div className="fw-bold transfer-type-title">{option.label}</div>
                                <small className="transfer-type-hint">{option.hint}</small>
                              </div>
                            </div>
                          </button>
                        </div>
                      );
                    })}
                  </div>
                  {!canChangeTransferType && (
                    <div className="alert alert-info py-2 mb-3">
                      <i className="fas fa-info-circle me-2"></i>
                      Rolünüz gereği sadece müşteri sevkiyat transferi oluşturabilirsiniz.
                    </div>
                  )}
                  
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

                    {!isCustomerTransfer ? (
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
                          required={!isCustomerTransfer}
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
                    ) : (
                      <div className="col-12">
                        <div className="card border-info shadow-sm">
                          <div className="card-header bg-info text-white d-flex align-items-center">
                            <i className="fas fa-shipping-fast me-2"></i>
                            <div>
                              <strong>Müşteri İletişim Bilgileri</strong>
                              <div className="small opacity-75">Teslimat adresi ve iletişim detaylarını eksiksiz doldurun</div>
                            </div>
                          </div>
                          <div className="card-body">
                            <div className="row g-3">
                              <div className="col-md-6">
                                <label className="form-label fw-bold">
                                  <i className="fas fa-user-tag text-info me-1"></i>
                                  Müşteri Adı Soyadı *
                                </label>
                                <input
                                  type="text"
                                  className={`form-control form-control-lg ${validationErrors.customerFullName ? 'is-invalid' : formData.customerFullName ? 'is-valid' : ''}`}
                                  name="customerFullName"
                                  value={formData.customerFullName}
                                  onChange={handleChange}
                                  placeholder="Örn: Ayşe Koç"
                                  maxLength="150"
                                  required
                                  aria-describedby="customerNameHelp"
                                />
                                {validationErrors.customerFullName ? (
                                  <div className="invalid-feedback">{validationErrors.customerFullName}</div>
                                ) : (
                                  <small id="customerNameHelp" className="text-muted">
                                    Fatura veya sevk irsaliyesinde yer alan tam isim
                                  </small>
                                )}
                              </div>
                              <div className="col-md-6">
                                <label className="form-label fw-bold">
                                  <i className="fas fa-phone-alt text-info me-1"></i>
                                  Müşteri Telefonu *
                                </label>
                                <div className="input-group input-group-lg phone-input-group">
                                  <span className="input-group-text">+90</span>
                                  <input
                                    type="tel"
                                    className={`form-control ${getPhoneValidationClass(formData.customerPhone, validationErrors.customerPhone)}`}
                                    name="customerPhone"
                                    value={formatPhoneInputValue(formData.customerPhone)}
                                    onChange={(e) => handlePhoneInput('customerPhone', e.target.value)}
                                    placeholder={PHONE_PLACEHOLDER}
                                    maxLength="13"
                                    inputMode="numeric"
                                  />
                                </div>
                                {validationErrors.customerPhone ? (
                                  <div className="invalid-feedback d-block">{validationErrors.customerPhone}</div>
                                ) : (
                                  <small className="text-muted">Teslimat sırasında aranacak aktif numara</small>
                                )}
                              </div>
                              <div className="col-12">
                                <label className="form-label fw-bold">
                                  <i className="fas fa-map-marker-alt text-info me-1"></i>
                                  Teslimat Adresi *
                                </label>
                                <textarea
                                  className={`form-control ${validationErrors.customerAddress ? 'is-invalid' : formData.customerAddress ? 'is-valid' : ''}`}
                                  name="customerAddress"
                                  value={formData.customerAddress}
                                  onChange={handleChange}
                                  rows="4"
                                  placeholder="Mahalle, cadde, bina, daire, il / ilçe..."
                                  maxLength="500"
                                  required
                                  aria-describedby="customerAddressHelp"
                                ></textarea>
                                <div className="d-flex justify-content-between mt-1">
                                  {validationErrors.customerAddress ? (
                                    <div className="invalid-feedback d-block">{validationErrors.customerAddress}</div>
                                  ) : (
                                    <small id="customerAddressHelp" className="text-muted">
                                      Navigasyonun kolay bulabilmesi için il/ilçe ve referans noktalarını ekleyin
                                    </small>
                                  )}
                                  <small className="text-muted">{formData.customerAddress.length}/500</small>
                                </div>
                              </div>
                              <div className="col-12">
                                <div className="alert alert-light border d-flex align-items-start mb-0">
                                  <i className="fas fa-lightbulb text-warning fa-lg me-3 mt-1"></i>
                                  <div>
                                    <strong>İpucu:</strong> Teslim alacak kişinin adını, güvenlik kodu veya site giriş talimatı gibi bilgileri notlar alanında paylaşabilirsiniz.
                                  </div>
                                </div>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    )}

                    <div className="col-12">
                      <div className="card border-primary shadow-sm">
                        <div className="card-header bg-primary bg-gradient text-white d-flex align-items-center">
                          <i className="fas fa-boxes-stacked me-2"></i>
                          <div>
                            <strong>Ürün ve Miktar Seçimi</strong>
                            <div className="small opacity-75">Kaynak depodaki stoklardan birden fazla ürün ekleyin</div>
                          </div>
                        </div>
                        <div className="card-body">
                          {!formData.sourceWarehouseId ? (
                            <div className="alert alert-light border mb-0">
                              <i className="fas fa-info-circle me-2 text-primary"></i>
                              Önce kaynak depoyu seçerek stok listesini yükleyin.
                            </div>
                          ) : (
                            <>
                              <div className="row g-3 align-items-end">
                                <div className="col-md-7">
                                  <label className="form-label fw-bold">
                                    <i className="fas fa-box text-primary me-1"></i>
                                    Depo Stokları
                                  </label>
                                  <div className="input-group input-group-lg mb-2">
                                    <span className="input-group-text bg-light">
                                      <i className="fas fa-search text-muted"></i>
                                    </span>
                                    <input
                                      type="text"
                                      className="form-control"
                                      placeholder="Ürün adı, SKU veya barkod ile ara..."
                                      value={stockSearchTerm}
                                      onChange={(e) => setStockSearchTerm(e.target.value)}
                                      disabled={stockLoading || !warehouseStocks.length}
                                    />
                                    {stockSearchTerm && (
                                      <button
                                        type="button"
                                        className="btn btn-outline-secondary"
                                        onClick={() => setStockSearchTerm('')}
                                        disabled={stockLoading}
                                      >
                                        Temizle
                                      </button>
                                    )}
                                  </div>
                                  <div className="d-flex justify-content-between small text-muted mb-2">
                                    <span>
                                      {warehouseStocks.length > 0
                                        ? `${warehouseStocks.length} ürün`
                                        : 'Stok listesi hazır değil'}
                                    </span>
                                    {stockSearchTerm && (
                                      <span>
                                        {filteredWarehouseStocks.length} sonuç
                                      </span>
                                    )}
                                  </div>
                                  {stockLoading ? (
                                    <div className="d-flex align-items-center gap-2 py-3">
                                      <span className="spinner-border spinner-border-sm text-primary" role="status" aria-hidden="true"></span>
                                      <span className="text-muted">Stoklar yükleniyor...</span>
                                    </div>
                                  ) : (
                                    <>
                                      {filteredWarehouseStocks.length === 0 ? (
                                        <div className="alert alert-light border mt-2 py-3 mb-0">
                                          <i className="fas fa-info-circle me-2 text-primary"></i>
                                          {stockSearchTerm
                                            ? 'Arama kriterine uygun ürün bulunamadı. Farklı anahtar kelimeler deneyin.'
                                            : 'Bu depoda henüz stok bulunmuyor.'}
                                        </div>
                                      ) : (
                                        <div className="stock-option-list border rounded-4 shadow-sm bg-white">
                                          <div className="stock-option-grid">
                                          {limitedStockList.map(stockItem => {
                                            const optionProductId = String(stockItem.product.id);
                                            const isSelected = String(itemForm.productId) === optionProductId;
                                            const available = calculateAvailableQuantity(stockItem);
                                            return (
                                              <button
                                                type="button"
                                                key={stockItem.product.id}
                                                className={`stock-option-button ${isSelected ? 'active' : ''}`}
                                                onClick={() => handleItemFormChange('productId', optionProductId)}
                                              >
                                                <div className="d-flex justify-content-between align-items-start">
                                                  <div className="me-3">
                                                    <div className="fw-semibold text-dark">{stockItem.product.name}</div>
                                                    <div className="text-muted small">SKU: {stockItem.product.sku}</div>
                                                  </div>
                                                  <div className="text-end">
                                                    <span className={`badge ${available > 0 ? 'bg-success bg-opacity-10 text-success' : 'bg-danger bg-opacity-10 text-danger'}`}>
                                                      Mevcut {available}
                                                    </span>
                                                  </div>
                                                </div>
                                              </button>
                                            );
                                          })}
                                          </div>
                                        </div>
                                      )}
                                    </>
                                  )}
                                  {hasMoreStocks && (
                                    <button
                                      type="button"
                                      className="btn btn-light btn-sm w-100 mt-2 stock-load-more"
                                      onClick={() => setVisibleStockCount(prev => prev + INITIAL_VISIBLE_STOCKS)}
                                    >
                                      Daha fazla göster ({filteredWarehouseStocks.length - limitedStockList.length} ürün)
                                    </button>
                                  )}
                                  {validationErrors.itemProductId && (
                                    <div className="invalid-feedback d-block">{validationErrors.itemProductId}</div>
                                  )}
                                  {!itemForm.productId && filteredWarehouseStocks.length > 0 && (
                                    <small className="text-muted d-block mt-2">
                                      Bir ürünü seçmek için listeden üzerine tıklayın.
                                    </small>
                                  )}
                                </div>
                                <div className="col-md-3">
                                  <label className="form-label fw-bold">
                                    <i className="fas fa-sort-numeric-up text-info me-1"></i>
                                    Eklenilecek Miktar
                                  </label>
                                  <input
                                    type="number"
                                    className={`form-control form-control-lg ${validationErrors.itemQuantity ? 'is-invalid' : ''}`}
                                    value={itemForm.quantity}
                                    min="1"
                                    onChange={(e) => handleItemFormChange('quantity', e.target.value)}
                                    placeholder="0"
                                    disabled={!formData.sourceWarehouseId}
                                  />
                                  {validationErrors.itemQuantity && (
                                    <div className="invalid-feedback">{validationErrors.itemQuantity}</div>
                                  )}
                                </div>
                                <div className="col-md-2">
                                  <button
                                    type="button"
                                    className="btn btn-primary btn-lg w-100"
                                    onClick={handleAddItem}
                                    disabled={!formData.sourceWarehouseId || stockLoading}
                                  >
                                    <i className="fas fa-plus me-1"></i>
                                    Ekle
                                  </button>
                                </div>
                              </div>

                              <div className="mt-4">
                                {transferItems.length === 0 ? (
                                  <div className="alert alert-light border mb-0 d-flex align-items-center">
                                    <i className="fas fa-box-open text-muted fa-2x me-3"></i>
                                    <div>
                                      <strong>Henüz ürün eklemediniz.</strong>
                                      <div className="text-muted small">Stok listesinden ürün seçip "Ekle" butonuna basın.</div>
                                    </div>
                                  </div>
                                ) : (
                                  <>
                                    <div className="table-responsive">
                                      <table className="table table-sm align-middle">
                                        <thead className="table-light">
                                          <tr>
                                            <th>Ürün</th>
                                            <th>SKU</th>
                                            <th className="text-center">Mevcut</th>
                                            <th className="text-center">Miktar</th>
                                            <th className="text-center">İşlem</th>
                                          </tr>
                                        </thead>
                                        <tbody>
                                          {transferItems.map(item => (
                                            <tr key={item.productId}>
                                              <td>{item.productName}</td>
                                              <td><span className="badge bg-light text-dark">{item.sku}</span></td>
                                              <td className="text-center">
                                                <span className={`badge ${item.availableQuantity > 0 ? 'bg-success' : 'bg-danger'} bg-opacity-10 text-${item.availableQuantity > 0 ? 'success' : 'danger'}`}>
                                                  {item.availableQuantity ?? 0}
                                                </span>
                                              </td>
                                              <td className="text-center" style={{ maxWidth: '120px' }}>
                                                <input
                                                  type="number"
                                                  className="form-control form-control-sm text-center"
                                                  value={item.quantity}
                                                  min="0"
                                                  max={item.availableQuantity || undefined}
                                                  onChange={(e) => handleItemQuantityUpdate(item.productId, e.target.value)}
                                                />
                                              </td>
                                              <td className="text-center">
                                                <button
                                                  type="button"
                                                  className="btn btn-link text-danger btn-sm"
                                                  onClick={() => handleRemoveItem(item.productId)}
                                                >
                                                  <i className="fas fa-trash-alt"></i>
                                                </button>
                                              </td>
                                            </tr>
                                          ))}
                                        </tbody>
                                      </table>
                                    </div>
                                    <div className="d-flex justify-content-between flex-wrap gap-2 mt-2">
                                      <span className="badge bg-light text-dark">
                                        <i className="fas fa-layer-group me-1"></i>
                                        {transferItems.length} farklı ürün
                                      </span>
                                      <span className="badge bg-primary">
                                        <i className="fas fa-boxes me-1"></i>
                                        Toplam {totalTransferQuantity} adet
                                      </span>
                                    </div>
                                  </>
                                )}
                                {validationErrors.transferItems && (
                                  <div className="text-danger mt-2">
                                    <i className="fas fa-exclamation-circle me-1"></i>
                                    {validationErrors.transferItems}
                                  </div>
                                )}
                              </div>
                            </>
                          )}
                        </div>
                      </div>
                    </div>

                    <div className="col-12 mt-3">
                      <div className="alert alert-info d-flex align-items-start">
                        <i className="fas fa-lightbulb fa-2x me-3 mt-1"></i>
                        <div>
                          <strong>İpucu:</strong>{' '}
                          {isCustomerTransfer ? (
                            <>
                              Müşteri sevkiyatlarında stok yalnızca kaynak depodan düşer ve teslimat bilgileri kayıt altına alınır.
                              Şoför ve müşteri iletişim bilgilerini doğru girdiğinizden emin olun.
                            </>
                          ) : (
                            <>
                              Transfer işlemi, kaynak depodaki stoğu azaltıp hedef depodaki stoğu artıracaktır.
                              Stok, transfer yolda iken rezerve edilir.
                            </>
                          )}
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
                      <div className="input-group input-group-lg phone-input-group">
                        <span className="input-group-text">+90</span>
                        <input
                          type="tel"
                          className={`form-control ${getPhoneValidationClass(formData.driverPhone, validationErrors.driverPhone)}`}
                          name="driverPhone"
                          value={formatPhoneInputValue(formData.driverPhone)}
                          onChange={(e) => handlePhoneInput('driverPhone', e.target.value)}
                          required
                          placeholder={PHONE_PLACEHOLDER}
                          maxLength="13"
                          inputMode="numeric"
                        />
                      </div>
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
                          <div className="fw-bold fs-5">{totalTransferQuantity} Adet</div>
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
                      <div className={`card h-100 border-${isCustomerTransfer ? 'info' : 'success'}`}>
                        <div className={`card-header text-white bg-${isCustomerTransfer ? 'info' : 'success'}`}>
                          <i className={`fas ${isCustomerTransfer ? 'fa-user-tag' : 'fa-warehouse'} me-2`}></i>
                          {isCustomerTransfer ? 'Müşteri Bilgileri' : 'Hedef Depo'}
                        </div>
                        <div className="card-body">
                          {isCustomerTransfer ? (
                            <>
                              <h6 className="fw-bold">{formData.customerFullName}</h6>
                              <p className="mb-1 text-muted small">
                                <i className="fas fa-phone me-1"></i>
                                {formattedCustomerPhone || '-'}
                              </p>
                              <p className="mb-0 text-muted small">
                                <i className="fas fa-map-marker-alt me-1"></i>
                                {formData.customerAddress}
                              </p>
                            </>
                          ) : (
                            <>
                              <h6 className="fw-bold">{destinationWarehouse?.name}</h6>
                              <p className="mb-0 text-muted small">
                                <i className="fas fa-map-marker-alt me-1"></i>
                                {destinationWarehouse?.location}
                              </p>
                            </>
                          )}
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
                          {transferItems.length === 0 ? (
                            <div className="text-muted">
                              <i className="fas fa-box-open me-2"></i>
                              Ürün seçimi yapılmadı.
                            </div>
                          ) : (
                            <div className="table-responsive">
                              <table className="table table-sm align-middle mb-0">
                                <thead className="table-light">
                                  <tr>
                                    <th>Ürün</th>
                                    <th>SKU</th>
                                    <th className="text-center">Miktar</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {transferItems.map(item => (
                                    <tr key={item.productId}>
                                      <td>{item.productName}</td>
                                      <td><span className="badge bg-light text-dark">{item.sku}</span></td>
                                      <td className="text-center">
                                        <span className="badge bg-primary">{item.quantity}</span>
                                      </td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                              <div className="text-end mt-2">
                                <strong>Toplam: {totalTransferQuantity} adet</strong>
                              </div>
                            </div>
                          )}
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
                              <strong>Telefon:</strong> {formattedDriverPhone || '-'}
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
                              <i className={`fas ${isCustomerTransfer ? 'fa-user-tag' : 'fa-warehouse'} text-success fa-lg`}></i>
                            </div>
                            <div className="text-start">
                              <small className="text-muted d-block">{isCustomerTransfer ? 'Müşteri' : 'Hedef'}</small>
                              <strong className="text-truncate d-block">
                                {isCustomerTransfer ? formData.customerFullName : destinationWarehouse?.name}
                              </strong>
                              {isCustomerTransfer && (
                                <small className="text-muted d-block text-truncate">{formattedCustomerPhone}</small>
                              )}
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
                              <strong className="fs-5">{totalTransferQuantity} Adet</strong>
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
                        {isCustomerTransfer && (
                          <li>Müşteri: <strong>{formData.customerFullName}</strong> ({formattedCustomerPhone || '-'})</li>
                        )}
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
                      disabled={loading || loadingData || totalTransferQuantity === 0}
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
