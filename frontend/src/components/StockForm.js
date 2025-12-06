import React, { useState, useEffect, useMemo } from 'react';
import axios from 'axios';
import ProductForm from './ProductForm';
import {
  extractPhoneDigits,
  formatPhoneForSubmit,
  formatPhoneInputValue,
  isPhoneComplete,
  PHONE_PLACEHOLDER
} from '../utils/phone';

const INITIAL_VISIBLE_PRODUCTS = 12;

const buildItemFromProduct = (product) => ({
  productId: product.id,
  name: product.name,
  sku: product.sku,
  brand: product.brand?.name || null,
  quantity: '',
  minStockLevel: '',
  reservedQuantity: '0',
  consignedQuantity: '0'
});

const StockForm = ({ products, warehouses, onSuccess, onCancel }) => {
  const [warehouseId, setWarehouseId] = useState('');
  const [items, setItems] = useState([]);
  const [productSearchTerm, setProductSearchTerm] = useState('');
  const [visibleProductCount, setVisibleProductCount] = useState(INITIAL_VISIBLE_PRODUCTS);
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({
    general: null,
    warehouseId: null,
    items: null,
    additionNote: null,
    customerName: null,
    customerPhone: null,
    perItem: {}
  });
  const [additionNote, setAdditionNote] = useState('');
  const [customerName, setCustomerName] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [feedback, setFeedback] = useState(null);
  const [showProductModal, setShowProductModal] = useState(false);
  const [localProducts, setLocalProducts] = useState(products || []);
  const [selectedSearchTerm, setSelectedSearchTerm] = useState('');

  useEffect(() => {
    if (!warehouseId && warehouses.length > 0) {
      setWarehouseId(String(warehouses[0].id));
    }
  }, [warehouses, warehouseId]);

  useEffect(() => {
    setLocalProducts(products || []);
  }, [products]);

  useEffect(() => {
    setVisibleProductCount(INITIAL_VISIBLE_PRODUCTS);
  }, [productSearchTerm, localProducts.length]);

  const filteredProducts = useMemo(() => {
    if (!productSearchTerm.trim()) return localProducts;
    const query = productSearchTerm.trim().toLocaleLowerCase('tr-TR');
    return localProducts.filter(product => {
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

  const productOptions = useMemo(() => filteredProducts.slice(0), [filteredProducts]);
  // Arama yapıldığında tüm sonuçları göster, aksi halde pagination uygula
  const limitedProductOptions = useMemo(
    () => {
      if (productSearchTerm.trim()) {
        // Arama yapıldığında tüm sonuçları göster
        return productOptions;
      }
      // Arama yoksa pagination uygula
      return productOptions.slice(0, visibleProductCount);
    },
    [productOptions, visibleProductCount, productSearchTerm]
  );
  const hasMoreProducts = !productSearchTerm.trim() && productOptions.length > visibleProductCount;

  const selectedProductMap = useMemo(() => {
    const map = new Map();
    items.forEach(item => map.set(String(item.productId), true));
    return map;
  }, [items]);

  const filteredSelectedItems = useMemo(() => {
    const query = selectedSearchTerm.trim().toLocaleLowerCase('tr-TR');
    if (!query) return items;
    return items.filter(item => {
      const haystack = [item.name, item.sku, item.brand]
        .filter(Boolean)
        .map(text => text.toLocaleLowerCase('tr-TR'));
      return haystack.some(text => text.includes(query));
    });
  }, [items, selectedSearchTerm]);

  const showToast = (message, type = 'success') => {
    const toast = document.createElement('div');
    const bgClass =
      type === 'success'
        ? 'text-bg-success'
        : type === 'warning'
          ? 'text-bg-warning'
          : 'text-bg-danger';
    const icon =
      type === 'success'
        ? 'fa-check-circle'
        : type === 'warning'
          ? 'fa-exclamation-triangle'
          : 'fa-times-circle';
    toast.className = `toast align-items-center ${bgClass} border-0 position-fixed top-0 end-0 m-3 show`;
    toast.setAttribute('role', 'alert');
    toast.style.zIndex = '9999';
    toast.innerHTML = `
      <div class="d-flex align-items-center">
        <div class="toast-body d-flex align-items-start">
          <i class="fas ${icon} me-2 mt-1"></i>
          <div class="flex-grow-1">${message}</div>
        </div>
        <button type="button" class="btn-close ${type === 'success' ? 'btn-close-white' : ''} me-2 m-auto" data-bs-dismiss="toast" aria-label="Kapat"></button>
      </div>
    `;
    document.body.appendChild(toast);
    setTimeout(() => {
      try {
        toast.classList.remove('show');
        setTimeout(() => {
          try {
            document.body.removeChild(toast);
          } catch { }
        }, 300);
      } catch { }
    }, type === 'success' ? 4000 : 7000);
  };

  const handleProductCreateSuccess = (created) => {
    if (!created) return;
    if (created.error) {
      showToast(created.message || 'Ürün kaydedilemedi', 'danger');
      return;
    }
    const newProduct = Array.isArray(created) ? created[0] : created;
    setLocalProducts(prev => [newProduct, ...prev]);
    setShowProductModal(false);
    showToast('Ürün eklendi ve listeye alındı.', 'success');
    // Otomatik seç ve formu koru
    if (newProduct?.id) {
      handleAddProduct(String(newProduct.id));
      // form alanları korunuyor, sadece liste güncellendi
    }
  };

  const selectedWarehouse = useMemo(() => {
    return warehouses.find(w => String(w.id) === warehouseId);
  }, [warehouses, warehouseId]);

  const isEmanetDepo = selectedWarehouse?.warehouseType === 'EMANET_DEPO';

  const clearFieldError = (field) => {
    if (!errors[field]) return;
    setErrors(prev => ({ ...prev, [field]: null }));
  };

  const clearItemFieldError = (productId, field) => {
    setErrors(prev => {
      if (!prev.perItem?.[productId]?.[field]) {
        return prev;
      }
      const nextPerItem = { ...prev.perItem };
      const fieldErrors = { ...nextPerItem[productId] };
      delete fieldErrors[field];
      if (Object.keys(fieldErrors).length === 0) {
        delete nextPerItem[productId];
      } else {
        nextPerItem[productId] = fieldErrors;
      }
      return { ...prev, perItem: nextPerItem };
    });
  };

  const handleWarehouseChange = (event) => {
    setWarehouseId(event.target.value);
    clearFieldError('warehouseId');
  };

  const handleAdditionNoteChange = (event) => {
    setAdditionNote(event.target.value);
    clearFieldError('additionNote');
  };

  const handleCustomerNameChange = (event) => {
    setCustomerName(event.target.value);
    clearFieldError('customerName');
  };

  const handleCustomerPhoneChange = (event) => {
    const digits = extractPhoneDigits(event.target.value);
    setCustomerPhone(digits);
    clearFieldError('customerPhone');
  };

  const handleAddProduct = (productId) => {
    if (!warehouseId) {
      setErrors(prev => ({ ...prev, warehouseId: 'Depo seçiniz' }));
      return;
    }
    const product = localProducts.find(p => String(p.id) === productId);
    if (!product) return;
    setItems(prev => {
      if (prev.some(item => String(item.productId) === productId)) {
        return prev;
      }
      return [buildItemFromProduct(product), ...prev];
    });
    setErrors(prev => ({ ...prev, items: null }));
  };

  const handleAddAllProducts = () => {
    if (!warehouseId) {
      setErrors(prev => ({ ...prev, warehouseId: 'Depo seçiniz' }));
      return;
    }

    if (productOptions.length === 0) return;

    setItems(prev => {
      const existing = new Set(prev.map(item => String(item.productId)));
      const newItems = productOptions
        .filter(product => !existing.has(String(product.id)))
        .map(product => buildItemFromProduct(product));

      if (newItems.length === 0) {
        return prev;
      }

      return [...newItems, ...prev];
    });
    setErrors(prev => ({ ...prev, items: null }));
  };

  const handleClearSelectedItems = () => {
    setItems([]);
    setErrors(prev => ({ ...prev, perItem: {}, items: null }));
  };

  const handleRemoveItem = (productId) => {
    setItems(prev => prev.filter(item => String(item.productId) !== String(productId)));
    setErrors(prev => {
      if (!prev.perItem?.[productId]) return prev;
      const nextPerItem = { ...prev.perItem };
      delete nextPerItem[productId];
      return { ...prev, perItem: nextPerItem };
    });
  };

  const updateItemField = (productId, field, value) => {
    setItems(prev =>
      prev.map(item =>
        String(item.productId) === String(productId)
          ? { ...item, [field]: value }
          : item
      )
    );
    clearItemFieldError(productId, field);
  };

  const parseNumber = (value) => {
    if (value === '' || value === null || value === undefined) {
      return null;
    }
    const parsed = Number(value);
    return Number.isNaN(parsed) ? null : parsed;
  };

  const validateForm = () => {
    const nextErrors = { general: null, warehouseId: null, items: null, customerName: null, customerPhone: null, perItem: {} };

    if (!warehouseId) {
      nextErrors.warehouseId = 'Depo seçiniz';
    }

    if (items.length === 0) {
      nextErrors.items = 'En az bir ürün seçerek miktar giriniz';
    }

    if (additionNote && additionNote.length > 500) {
      nextErrors.additionNote = 'Not en fazla 500 karakter olabilir';
    }

    if (isEmanetDepo && (!customerName || !customerName.trim())) {
      nextErrors.customerName = 'Müşteri adı gereklidir';
    }

    if (isEmanetDepo && (!customerPhone || !customerPhone.trim())) {
      nextErrors.customerPhone = 'Müşteri telefon numarası gereklidir';
    }

    items.forEach(item => {
      const itemErrors = {};
      const quantity = parseNumber(item.quantity);
      const minStockLevel = parseNumber(item.minStockLevel);
      const reserved = parseNumber(item.reservedQuantity);
      const consigned = parseNumber(item.consignedQuantity);

      if (quantity === null || quantity <= 0) {
        itemErrors.quantity = 'Geçerli bir miktar giriniz';
      }
      if (minStockLevel !== null && minStockLevel < 0) {
        itemErrors.minStockLevel = 'Negatif olamaz';
      }
      if (reserved !== null && reserved < 0) {
        itemErrors.reservedQuantity = 'Negatif olamaz';
      }
      if (consigned !== null && consigned < 0) {
        itemErrors.consignedQuantity = 'Negatif olamaz';
      }
      if (Object.keys(itemErrors).length > 0) {
        nextErrors.perItem[item.productId] = itemErrors;
      }
    });

    const hasErrors =
      Boolean(nextErrors.general) ||
      Boolean(nextErrors.warehouseId) ||
      Boolean(nextErrors.items) ||
      Boolean(nextErrors.customerName) ||
      Boolean(nextErrors.customerPhone) ||
      Object.keys(nextErrors.perItem).length > 0;

    setErrors(nextErrors);
    return !hasErrors;
  };

  const buildPayload = () => {
    const note = additionNote?.trim() || null;
    const customer = isEmanetDepo && customerName?.trim() ? customerName.trim() : null;
    const phone = isEmanetDepo && customerPhone?.trim() ? formatPhoneForSubmit(customerPhone) : null;
    return items.map(item => ({
      product: { id: Number(item.productId) },
      warehouse: { id: Number(warehouseId) },
      quantity: Number(item.quantity),
      minStockLevel: item.minStockLevel === '' ? null : Number(item.minStockLevel),
      reservedQuantity: item.reservedQuantity === '' ? 0 : Number(item.reservedQuantity),
      consignedQuantity: item.consignedQuantity === '' ? 0 : Number(item.consignedQuantity),
      additionNote: note,
      customerName: customer,
      customerPhone: phone
    }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setFeedback(null);

    if (!validateForm()) {
      return;
    }

    setLoading(true);
    try {
      const payload = buildPayload();
      const response = await axios.post('/api/stocks/bulk', payload);
      const createdCount = Array.isArray(response.data) ? response.data.length : payload.length;
      setFeedback({
        type: 'success',
        message: `${createdCount} stok kaydı başarıyla oluşturuldu.`
      });
      setItems([]);
      setProductSearchTerm('');
      setAdditionNote('');
      setCustomerName('');
      setCustomerPhone('');
      if (onSuccess) {
        onSuccess({ close: false, message: 'Stok kayıtları başarıyla oluşturuldu.' });
      }
    } catch (error) {
      const message =
        error?.response?.data?.message ||
        error?.response?.data?.error ||
        (typeof error?.response?.data === 'string' ? error.response.data : null) ||
        'Stok kaydedilirken hata oluştu';
      setErrors(prev => ({ ...prev, general: message }));
      showToast(message, 'danger');
    } finally {
      setLoading(false);
    }
  };

  const getItemError = (productId, field) =>
    errors.perItem?.[productId]?.[field] || null;

  return (
    <form onSubmit={handleSubmit} className="stock-form">
      {feedback && (
        <div className={`alert alert-${feedback.type}`} role="alert">
          {feedback.message}
        </div>
      )}

      {showProductModal && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-lg">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">Yeni Ürün Ekle</h5>
                <button type="button" className="btn-close" onClick={() => setShowProductModal(false)}></button>
              </div>
              <div className="modal-body">
                <ProductForm
                  product={null}
                  onSuccess={handleProductCreateSuccess}
                  onCancel={() => setShowProductModal(false)}
                />
              </div>
            </div>
          </div>
        </div>
      )}

      <div className="row g-3 mb-4">
        <div className="col-lg-4">
          <label className="form-label fw-semibold">
            Depo Seçimi <span className="text-danger">*</span>
          </label>
          <select
            className={`form-select ${errors.warehouseId ? 'is-invalid' : ''}`}
            value={warehouseId}
            onChange={handleWarehouseChange}
          >
            <option value="">Depo seçiniz</option>
            {warehouses.map(warehouse => (
              <option key={warehouse.id} value={warehouse.id}>
                {warehouse.name} - {warehouse.location}
              </option>
            ))}
          </select>
          {errors.warehouseId && (
            <div className="invalid-feedback">{errors.warehouseId}</div>
          )}
          <small className="text-muted d-block mt-2">
            Bu depoya ait yeni stok kayıtları oluşturulur.
          </small>
        </div>
        <div className="col-lg-8">
          <div className="card border-0 shadow-sm h-100">
            <div className="card-body d-flex flex-column justify-content-center">
              <div className="d-flex flex-wrap align-items-center gap-3">
                <span className="badge bg-primary bg-opacity-10 text-primary px-3 py-2">
                  <i className="fas fa-boxes me-2"></i>
                  {items.length} ürün seçildi
                </span>
                <span className="badge bg-secondary bg-opacity-10 text-secondary px-3 py-2">
                  <i className="fas fa-layer-group me-2"></i>
                  {localProducts.length} ürün içinde arama
                </span>
                {feedback && (
                  <span className="badge bg-success bg-opacity-10 text-success px-3 py-2">
                    <i className="fas fa-check me-2"></i>
                    Liste güncellendi
                  </span>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="mb-4">
        <label className="form-label fw-semibold">
          Stok Ekleme Notu <small className="text-muted">(opsiyonel)</small>
        </label>
        <textarea
          className={`form-control ${errors.additionNote ? 'is-invalid' : ''}`}
          rows="3"
          maxLength="500"
          value={additionNote}
          onChange={handleAdditionNoteChange}
          placeholder="Bu stok ekleme işlemi için genel not girin..."
        ></textarea>
        <div className="d-flex justify-content-between mt-1">
          {errors.additionNote ? (
            <div className="invalid-feedback d-block">{errors.additionNote}</div>
          ) : (
            <small className="text-muted">Bu not listede oluşturulan tüm stoklara işlenecek.</small>
          )}
          <small className="text-muted">{additionNote.length}/500</small>
        </div>
      </div>

      {isEmanetDepo && (
        <>
          <div className="mb-4">
            <label className="form-label fw-semibold">
              Müşteri Adı <span className="text-danger">*</span>
            </label>
            <input
              type="text"
              className={`form-control ${errors.customerName ? 'is-invalid' : ''}`}
              value={customerName}
              onChange={handleCustomerNameChange}
              placeholder="Müşteri adını giriniz"
              maxLength="255"
            />
            <div className="d-flex justify-content-between mt-1">
              {errors.customerName ? (
                <div className="invalid-feedback d-block">{errors.customerName}</div>
              ) : (
                <small className="text-muted">Bu müşteri adı listede oluşturulan tüm stoklara işlenecek.</small>
              )}
              <small className="text-muted">{customerName.length}/255</small>
            </div>
          </div>
          <div className="mb-4">
            <label className="form-label fw-semibold">
              Müşteri Telefon Numarası <span className="text-danger">*</span>
            </label>
            <div className="input-group phone-input-group">
              <span className="input-group-text">+90</span>
              <input
                type="tel"
                className={`form-control ${errors.customerPhone ? 'is-invalid' : (customerPhone ? (isPhoneComplete(customerPhone) ? 'is-valid' : '') : '')}`}
                value={formatPhoneInputValue(customerPhone)}
                onChange={handleCustomerPhoneChange}
                placeholder={PHONE_PLACEHOLDER}
                maxLength="13"
                inputMode="numeric"
              />
            </div>
            <div className="d-flex justify-content-between mt-1">
              {errors.customerPhone ? (
                <div className="invalid-feedback d-block">{errors.customerPhone}</div>
              ) : (
                <>
                  {customerPhone && !isPhoneComplete(customerPhone) && (
                    <small className="text-muted">Telefon 10 haneli olmalıdır</small>
                  )}
                  {(!customerPhone || isPhoneComplete(customerPhone)) && (
                    <small className="text-muted">Bu telefon numarası listede oluşturulan tüm stoklara işlenecek.</small>
                  )}
                </>
              )}
            </div>
          </div>
        </>
      )}

      <div className="row g-4">
        <div className="col-lg-8">
          <div className="card border-0 shadow-sm h-100">
            <div className="card-header bg-white border-0 pb-0">
              <h5 className="card-title mb-1">
                <i className="fas fa-search me-2 text-muted"></i>
                Ürün Seçimi
              </h5>
              <div className="d-flex flex-column flex-lg-row align-items-lg-center justify-content-lg-between gap-2">
                <p className="text-muted small mb-0">
                  Kartlara tıklayarak ürünleri listeye ekleyin.
                </p>
                <button
                  type="button"
                  className="btn btn-outline-primary btn-sm"
                  onClick={() => setShowProductModal(true)}
                >
                  <i className="fas fa-plus me-2"></i>
                  Yeni Ürün Ekle
                </button>
              </div>
            </div>
            <div className="card-body">
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
              <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
                <div className="d-flex small text-muted gap-3">
                  <span>{products.length} ürün</span>
                  {productSearchTerm && (
                    <span>{productOptions.length} sonuç</span>
                  )}
                </div>
                <div className="d-flex gap-2">
                  <button
                    type="button"
                    className="btn btn-outline-success btn-sm"
                    onClick={handleAddAllProducts}
                    disabled={productOptions.length === 0}
                  >
                    <i className="fas fa-angle-double-right me-2"></i>
                    Tümünü Ekle
                  </button>
                  <button
                    type="button"
                    className="btn btn-outline-secondary btn-sm"
                    onClick={handleClearSelectedItems}
                    disabled={items.length === 0}
                  >
                    <i className="fas fa-undo me-2"></i>
                    Seçilileri Temizle
                  </button>
                </div>
              </div>

              {productOptions.length === 0 ? (
                <div className="alert alert-light border">
                  <i className="fas fa-info-circle me-2 text-primary"></i>
                  {productSearchTerm
                    ? 'Arama kriterlerine uygun ürün bulunamadı.'
                    : 'Sistemde tanımlı ürün bulunamadı.'}
                </div>
              ) : (
                <>
                  {/* Wide table view similar to transfer selection */}
                  <div className="d-none d-md-block table-responsive border rounded shadow-sm bg-white" style={{ maxHeight: '65vh', overflowY: 'auto' }}>
                    <table className="table table-hover table-sm mb-0">
                      <thead className="table-light">
                        <tr>
                          <th style={{ width: 40 }}></th>
                          <th>Ürün Adı</th>
                          <th>Stok Kodu</th>
                          <th>Marka</th>
                          <th>Kategori</th>
                          <th>Renk</th>
                          <th>Açıklama</th>
                        </tr>
                      </thead>
                      <tbody>
                        {limitedProductOptions.map(product => {
                          const productId = String(product.id);
                          const isSelected = selectedProductMap.has(productId);
                          return (
                            <tr
                              key={product.id}
                              className={isSelected ? 'table-success' : ''}
                              style={{ cursor: isSelected ? 'default' : 'pointer' }}
                              onClick={() => !isSelected && handleAddProduct(productId)}
                            >
                              <td className="text-center">
                                {isSelected && <i className="fas fa-check text-success"></i>}
                              </td>
                              <td>
                                <div className="fw-semibold">{product.name}</div>
                              </td>
                              <td>
                                <span className="badge text-bg-light border">{product.sku}</span>
                              </td>
                              <td>{product.brand?.name || '-'}</td>
                              <td>
                                {product.category?.name ? (
                                  <span className="badge bg-info bg-opacity-10 text-info border border-info">
                                    {product.category.parentName ? `${product.category.parentName} > ` : ''}{product.category.name}
                                  </span>
                                ) : '-'}
                              </td>
                              <td>
                                {product.color?.name ? (
                                  <span className="badge bg-light text-dark border">
                                    <i className="fas fa-palette me-1"></i>
                                    {product.color.name}
                                  </span>
                                ) : '-'}
                              </td>
                              <td className="text-muted small">
                                {product.description
                                  ? (product.description.length > 90
                                    ? `${product.description.substring(0, 90)}...`
                                    : product.description)
                                  : '-'}
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>

                  {/* Mobile: stacked list */}
                  <div className="d-md-none d-flex flex-column gap-2" style={{ maxHeight: '65vh', overflowY: 'auto' }}>
                    {limitedProductOptions.map(product => {
                      const productId = String(product.id);
                      const isSelected = selectedProductMap.has(productId);
                      return (
                        <div
                          key={product.id}
                          className={`card border ${isSelected ? 'border-success bg-success bg-opacity-10' : 'border-light'}`}
                          style={{ cursor: isSelected ? 'default' : 'pointer' }}
                          onClick={() => !isSelected && handleAddProduct(productId)}
                        >
                          <div className="card-body p-3">
                            <div className="d-flex justify-content-between align-items-start">
                              <div className="flex-grow-1">
                                <div className="fw-bold">{product.name}</div>
                                <div className="d-flex flex-wrap gap-2 mt-1">
                                  <span className="badge bg-light text-dark border">
                                    <i className="fas fa-barcode me-1"></i>{product.sku}
                                  </span>
                                  {product.brand?.name && (
                                    <span className="badge bg-light text-dark border">
                                      <i className="fas fa-copyright me-1"></i>{product.brand.name}
                                    </span>
                                  )}
                                  {product.category?.name && (
                                    <span className="badge bg-info bg-opacity-10 text-info border border-info">
                                      <i className="fas fa-tag me-1"></i>
                                      {product.category.parentName ? `${product.category.parentName} > ` : ''}{product.category.name}
                                    </span>
                                  )}
                                  {product.color?.name && (
                                    <span className="badge bg-light text-dark border">
                                      <i className="fas fa-palette me-1"></i>
                                      {product.color.name}
                                    </span>
                                  )}
                                </div>
                                {product.description && (
                                  <div className="text-muted small mt-1">
                                    {product.description.length > 90 ? `${product.description.substring(0, 90)}...` : product.description}
                                  </div>
                                )}
                              </div>
                              <div className="ms-2">
                                {isSelected ? (
                                  <span className="badge bg-success text-white">
                                    <i className="fas fa-check me-1"></i>Seçildi
                                  </span>
                                ) : (
                                  <button
                                    type="button"
                                    className="btn btn-primary btn-sm"
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      handleAddProduct(productId);
                                    }}
                                  >
                                    <i className="fas fa-plus me-1"></i>Ekle
                                  </button>
                                )}
                              </div>
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>

                  {hasMoreProducts && (
                    <button
                      type="button"
                      className="btn btn-light btn-sm w-100 mt-2"
                      onClick={() =>
                        setVisibleProductCount(prev => prev + INITIAL_VISIBLE_PRODUCTS)
                      }
                    >
                      <i className="fas fa-chevron-down me-2"></i>
                      Daha fazla göster ({productOptions.length - limitedProductOptions.length} ürün)
                    </button>
                  )}
                </>
              )}
            </div>
          </div>
        </div>

        <div className="col-lg-4">
          <div className="card border-0 shadow-sm h-100">
            <div className="card-header bg-white border-0 pb-0 d-flex justify-content-between align-items-center">
              <div>
                <h5 className="card-title mb-1">
                  <i className="fas fa-list me-2 text-muted"></i>
                  Seçilen Ürünler
                </h5>
                <p className="text-muted small mb-0">
                  Her ürün için miktar ve isteğe bağlı not girebilirsiniz.
                </p>
              </div>
              {items.length > 0 && (
                <button
                  type="button"
                  className="btn btn-link text-danger text-decoration-none"
                  onClick={handleClearSelectedItems}
                >
                  <i className="fas fa-trash me-1"></i>
                  Seçilileri Temizle
                </button>
              )}
            </div>
            <div className="card-body">
              {items.length > 0 && (
                <div className="input-group input-group-sm mb-3">
                  <span className="input-group-text bg-light">
                    <i className="fas fa-search text-muted"></i>
                  </span>
                  <input
                    type="text"
                    className="form-control"
                    placeholder="Seçili ürünlerde ara..."
                    value={selectedSearchTerm}
                    onChange={(e) => setSelectedSearchTerm(e.target.value)}
                  />
                  {selectedSearchTerm && (
                    <button
                      type="button"
                      className="btn btn-outline-secondary"
                      onClick={() => setSelectedSearchTerm('')}
                    >
                      Temizle
                    </button>
                  )}
                </div>
              )}

              {items.length === 0 ? (
                <div className="alert alert-light border mb-0">
                  <i className="fas fa-box-open me-2"></i>
                  Henüz ürün seçmediniz. Sol taraftan ürün ekleyin.
                </div>
              ) : filteredSelectedItems.length === 0 ? (
                <div className="alert alert-warning border mb-0">
                  <i className="fas fa-search me-2"></i>
                  Aramanızla eşleşen seçili ürün bulunamadı.
                </div>
              ) : (
                <div className="d-flex flex-column gap-3" style={{ maxHeight: '60vh', overflowY: 'auto' }}>
                  {filteredSelectedItems.map(item => (
                    <div className="card border shadow-sm" key={item.productId}>
                      <div className="card-body">
                        <div className="d-flex justify-content-between align-items-start flex-wrap gap-2">
                          <div>
                            <div className="fw-semibold">{item.name}</div>
                            <small className="text-muted">SKU: {item.sku || '—'}</small>
                            {item.brand && (
                              <span className="badge bg-light text-dark ms-2">{item.brand}</span>
                            )}
                          </div>
                          <button
                            type="button"
                            className="btn btn-link text-danger text-decoration-none"
                            onClick={() => handleRemoveItem(item.productId)}
                          >
                            <i className="fas fa-times me-1"></i>
                            Kaldır
                          </button>
                        </div>
                        <div className="row g-3 mt-3">
                          <div className="col-md-3">
                            <label className="form-label fw-semibold small text-muted">
                              Miktar <span className="text-danger">*</span>
                            </label>
                            <input
                              type="number"
                              min="1"
                              className={`form-control form-control-sm ${getItemError(item.productId, 'quantity') ? 'is-invalid' : ''
                                }`}
                              value={item.quantity}
                              onChange={(e) =>
                                updateItemField(item.productId, 'quantity', e.target.value)
                              }
                            />
                            {getItemError(item.productId, 'quantity') && (
                              <div className="invalid-feedback">
                                {getItemError(item.productId, 'quantity')}
                              </div>
                            )}
                          </div>
                          <div className="col-md-3">
                            <label className="form-label fw-semibold small text-muted">
                              Min. Stok
                            </label>
                            <input
                              type="number"
                              min="0"
                              className={`form-control form-control-sm ${getItemError(item.productId, 'minStockLevel') ? 'is-invalid' : ''
                                }`}
                              placeholder="Opsiyonel"
                              value={item.minStockLevel}
                              onChange={(e) =>
                                updateItemField(item.productId, 'minStockLevel', e.target.value)
                              }
                            />
                            {getItemError(item.productId, 'minStockLevel') && (
                              <div className="invalid-feedback">
                                {getItemError(item.productId, 'minStockLevel')}
                              </div>
                            )}
                          </div>
                          <div className="col-md-3">
                            <label className="form-label fw-semibold small text-muted">
                              Rezerve
                            </label>
                            <input
                              type="number"
                              min="0"
                              className={`form-control form-control-sm ${getItemError(item.productId, 'reservedQuantity') ? 'is-invalid' : ''
                                }`}
                              placeholder="0"
                              value={item.reservedQuantity}
                              onChange={(e) =>
                                updateItemField(item.productId, 'reservedQuantity', e.target.value)
                              }
                            />
                            {getItemError(item.productId, 'reservedQuantity') && (
                              <div className="invalid-feedback">
                                {getItemError(item.productId, 'reservedQuantity')}
                              </div>
                            )}
                          </div>
                          <div className="col-md-3">
                            <label className="form-label fw-semibold small text-muted">
                              Emanet
                            </label>
                            <input
                              type="number"
                              min="0"
                              className={`form-control form-control-sm ${getItemError(item.productId, 'consignedQuantity') ? 'is-invalid' : ''
                                }`}
                              placeholder="0"
                              value={item.consignedQuantity}
                              onChange={(e) =>
                                updateItemField(item.productId, 'consignedQuantity', e.target.value)
                              }
                            />
                            {getItemError(item.productId, 'consignedQuantity') && (
                              <div className="invalid-feedback">
                                {getItemError(item.productId, 'consignedQuantity')}
                              </div>
                            )}
                          </div>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
              {errors.items && (
                <div className="text-danger small mt-2">
                  <i className="fas fa-exclamation-circle me-1"></i>
                  {errors.items}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      <div className="d-flex justify-content-between align-items-center flex-wrap gap-2 mt-4">
        <div className="text-muted small">
          {items.length > 0
            ? `${items.length} ürün kayda hazır`
            : 'Kayda hazır ürün bulunmuyor'}
        </div>
        <div className="d-flex gap-2">
          <button
            type="button"
            className="btn btn-outline-secondary"
            onClick={onCancel}
            disabled={loading}
          >
            <i className="fas fa-times me-2"></i>
            Kapat
          </button>
          <button
            type="submit"
            className="btn btn-primary"
            disabled={loading || items.length === 0}
          >
            {loading ? (
              <>
                <span
                  className="spinner-border spinner-border-sm me-2"
                  role="status"
                  aria-hidden="true"
                ></span>
                Kaydediliyor...
              </>
            ) : (
              <>
                <i className="fas fa-save me-2"></i>
                {items.length > 1 ? 'Toplu Stok Kaydı Oluştur' : 'Stok Kaydı Oluştur'}
              </>
            )}
          </button>
        </div>
      </div>
    </form>
  );
};

export default StockForm;
