import React, { useState, useEffect, useMemo } from 'react';
import axios from 'axios';
import {
  extractPhoneDigits,
  formatPhoneForSubmit,
  formatPhoneInputValue,
  isPhoneComplete,
  PHONE_PLACEHOLDER,
} from '../utils/phone';
import { todayIsoDate } from '../utils/date';
import IrsaliyePicker from './IrsaliyePicker';

const EMANET_TYPES = ['EMANET_DEPO', 'EmanetDepo', 'emanetDepo'];

const isEmanetWarehouse = (warehouse) => EMANET_TYPES.includes(warehouse?.warehouseType);

// Both search dropdowns in this modal float above the body; sharing one style keeps them from
// drifting apart as either is touched.
const SEARCH_DROPDOWN_STYLE = {
  zIndex: 1055,
  maxHeight: '280px',
  overflowY: 'auto',
  marginTop: '4px',
  backgroundColor: 'var(--surface-card)',
  border: '2px solid var(--color-primary-600)',
  borderRadius: '0.375rem',
  boxShadow: '0 0.5rem 1rem rgba(0, 0, 0, 0.15)',
  top: '100%',
  left: 0,
};

/**
 * Stock settings modal - for managing consigned, reserved, and min stock levels
 */
const StockSettingsModal = ({ stock, products = [], warehouses = [], onSuccess, onClose }) => {
  const role = (typeof window !== 'undefined' && localStorage.getItem('auth_role')) || 'ADMIN';
  const [settings, setSettings] = useState({
    productId: null,
    consignedQuantity: 0,
    minStockLevel: 2,
    customerName: '',
    customerPhone: '',
    additionNote: '',
    irsaliyeNo: '',
    irsaliyeDate: '',
  });
  const [productSearchTerm, setProductSearchTerm] = useState('');
  const [showProductDropdown, setShowProductDropdown] = useState(false);
  const [highlightedProductIndex, setHighlightedProductIndex] = useState(0);
  // The warehouse starts locked behind a button. Picking the wrong one on entry is exactly the
  // mistake this field exists to repair, and an always-open search would let it happen again here.
  const [warehouseId, setWarehouseId] = useState(stock?.warehouse?.id ?? null);
  const [warehousePickerOpen, setWarehousePickerOpen] = useState(false);
  const [warehouseSearchTerm, setWarehouseSearchTerm] = useState('');
  const [showWarehouseDropdown, setShowWarehouseDropdown] = useState(false);
  const [highlightedWarehouseIndex, setHighlightedWarehouseIndex] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [errors, setErrors] = useState({});
  const todayIso = useMemo(() => todayIsoDate(), []);

  const originalWarehouseId = stock?.warehouse?.id ?? null;
  // Reserved units are already promised to an order or a transfer that recorded this row, so the
  // backend refuses to move it. Saying so here spares the operator a rejected save.
  const reservedQuantity = stock?.reservedQuantity || 0;
  const warehouseMoveBlocked = reservedQuantity > 0;

  const selectableWarehouses = useMemo(() => {
    const list = Array.isArray(warehouses) ? warehouses : [];
    // A passive warehouse cannot receive stock. The one the row already sits in stays listed so
    // the field always has something to show, even if it was deactivated after the entry.
    return list.filter((item) => item.active !== false || item.id === originalWarehouseId);
  }, [warehouses, originalWarehouseId]);

  const selectedWarehouse = useMemo(() => {
    const match = selectableWarehouses.find((item) => item.id === warehouseId);
    if (match) return match;
    // Falls back to the row's own warehouse so the name still reads correctly while the warehouse
    // list is still loading.
    return warehouseId === originalWarehouseId ? stock?.warehouse || null : null;
  }, [selectableWarehouses, warehouseId, originalWarehouseId, stock]);

  const warehouseChanged = warehouseId !== null && warehouseId !== originalWarehouseId;

  // Which customer fields apply follows the warehouse the row is heading for, not the one it came
  // from: a move into a consignment warehouse has to collect the customer before it can be saved.
  const isEmanetDepo = isEmanetWarehouse(selectedWarehouse);

  useEffect(() => {
    if (stock) {
      setWarehouseId(stock.warehouse?.id ?? null);
      setWarehousePickerOpen(false);
      setWarehouseSearchTerm('');
      setShowWarehouseDropdown(false);
      setSettings({
        productId: stock.product?.id || null,
        consignedQuantity: stock.consignedQuantity || 0,
        minStockLevel: stock.minStockLevel || 2,
        customerName: stock.customerName || '',
        customerPhone: stock.customerPhone ? extractPhoneDigits(stock.customerPhone) : '',
        additionNote: stock.additionNote || '',
        irsaliyeNo: stock.irsaliyeNo || '',
        irsaliyeDate: stock.irsaliyeDate || '',
      });
      // Set initial product search term to current product name
      if (stock.product?.name) {
        setProductSearchTerm(stock.product.name);
      }
    }
  }, [stock]);

  // Reset highlighted index when search term changes
  useEffect(() => {
    setHighlightedProductIndex(0);
  }, [productSearchTerm]);

  useEffect(() => {
    setHighlightedWarehouseIndex(0);
  }, [warehouseSearchTerm]);

  // Filter products for search
  const filteredProducts = useMemo(() => {
    if (!productSearchTerm.trim()) return [];
    const query = productSearchTerm.trim().toLocaleLowerCase('tr-TR');
    return products
      .filter((product) => {
        const haystack = [product.name, product.sku, product.barcode, product.brand?.name]
          .filter(Boolean)
          .map((text) => text.toLocaleLowerCase('tr-TR'));
        return haystack.some((text) => text.includes(query));
      })
      .slice(0, 10); // Limit to 10 results for better UX
  }, [products, productSearchTerm]);

  // Get selected product name for display
  const selectedProduct = useMemo(() => {
    if (!settings.productId) return null;
    return products.find((p) => p.id === settings.productId);
  }, [products, settings.productId]);

  // Handle product selection
  const handleProductSelect = (product) => {
    setSettings((prev) => ({
      ...prev,
      productId: product.id,
    }));
    setProductSearchTerm(product.name);
    setShowProductDropdown(false);
    setHighlightedProductIndex(0);
    if (errors.productId) {
      setErrors((prev) => ({ ...prev, productId: null }));
    }
  };

  // Warehouses are few enough that an empty box can just list them; typing narrows by name,
  // location or manager, which is how operators tell two depots in the same city apart.
  const filteredWarehouses = useMemo(() => {
    const query = warehouseSearchTerm.trim().toLocaleLowerCase('tr-TR');
    if (!query) return selectableWarehouses.slice(0, 10);
    return selectableWarehouses
      .filter((item) => {
        const haystack = [item.name, item.location, item.manager]
          .filter(Boolean)
          .map((text) => text.toLocaleLowerCase('tr-TR'));
        return haystack.some((text) => text.includes(query));
      })
      .slice(0, 10);
  }, [selectableWarehouses, warehouseSearchTerm]);

  const handleWarehouseSelect = (warehouse) => {
    setWarehouseId(warehouse.id);
    setWarehouseSearchTerm('');
    setShowWarehouseDropdown(false);
    setHighlightedWarehouseIndex(0);
    setWarehousePickerOpen(false);
    setError(null);
  };

  const handleWarehouseSearchKeyDown = (e) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setHighlightedWarehouseIndex((prev) => Math.min(prev + 1, filteredWarehouses.length - 1));
      setShowWarehouseDropdown(true);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlightedWarehouseIndex((prev) => Math.max(prev - 1, 0));
      setShowWarehouseDropdown(true);
    } else if (e.key === 'Enter' && filteredWarehouses.length > 0) {
      e.preventDefault();
      handleWarehouseSelect(filteredWarehouses[highlightedWarehouseIndex]);
    } else if (e.key === 'Escape') {
      e.preventDefault();
      setShowWarehouseDropdown(false);
      setWarehousePickerOpen(false);
      setWarehouseSearchTerm('');
    }
  };

  // Handle keyboard navigation
  const handleProductSearchKeyDown = (e) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setHighlightedProductIndex((prev) => Math.min(prev + 1, filteredProducts.length - 1));
      setShowProductDropdown(true);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlightedProductIndex((prev) => Math.max(prev - 1, 0));
      setShowProductDropdown(true);
    } else if (e.key === 'Enter' && filteredProducts.length > 0) {
      e.preventDefault();
      handleProductSelect(filteredProducts[highlightedProductIndex]);
    } else if (e.key === 'Escape') {
      setShowProductDropdown(false);
    }
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    if (name === 'customerPhone') {
      const digits = extractPhoneDigits(value);
      setSettings((prev) => ({
        ...prev,
        [name]: digits,
      }));
      if (errors.customerPhone) {
        setErrors((prev) => ({ ...prev, customerPhone: null }));
      }
    } else if (name === 'irsaliyeNo') {
      // Emptying the number takes the date with it, so the form shows what will actually be
      // saved rather than a stale date sitting in a disabled field.
      setSettings((prev) => ({
        ...prev,
        irsaliyeNo: value,
        irsaliyeDate: value.trim() ? prev.irsaliyeDate : '',
      }));
      if (errors[name]) {
        setErrors((prev) => ({ ...prev, [name]: null }));
      }
    } else if (name === 'customerName' || name === 'additionNote' || name === 'irsaliyeDate') {
      setSettings((prev) => ({
        ...prev,
        [name]: value,
      }));
      if (errors[name]) {
        setErrors((prev) => ({ ...prev, [name]: null }));
      }
    } else {
      setSettings((prev) => ({
        ...prev,
        [name]: parseInt(value) || 0,
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
    const originalAdditionNote = stock.additionNote || '';
    const originalIrsaliyeNo = stock.irsaliyeNo || '';
    const originalIrsaliyeDate = stock.irsaliyeDate || '';
    const hasChanges =
      warehouseChanged ||
      settings.productId !== originalProductId ||
      settings.consignedQuantity !== (stock.consignedQuantity || 0) ||
      settings.minStockLevel !== (stock.minStockLevel || 2) ||
      settings.additionNote.trim() !== originalAdditionNote ||
      settings.irsaliyeNo.trim() !== originalIrsaliyeNo ||
      settings.irsaliyeDate !== originalIrsaliyeDate ||
      (isEmanetDepo &&
        (settings.customerName.trim() !== originalCustomerName ||
          settings.customerPhone.trim() !== originalCustomerPhone));

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
        minStockLevel: settings.minStockLevel,
        additionNote: settings.additionNote.trim() || null,
        // Sent as '' rather than null when cleared. The backend reads a present-but-empty number
        // as "this block was submitted and is now blank" and null as "leave it alone", so null
        // here would make a mistyped waybill impossible to remove.
        irsaliyeNo: settings.irsaliyeNo.trim(),
        irsaliyeDate: settings.irsaliyeNo.trim() && settings.irsaliyeDate ? settings.irsaliyeDate : null,
        // Explicitly NOT including quantity field for security
      };

      // Include product if it changed
      if (settings.productId && settings.productId !== (stock.product?.id || null)) {
        updateData.product = { id: settings.productId };
      }

      // Only sent when it actually moved: the backend leaves the warehouse alone when the field
      // is absent, which is what every other caller of this endpoint relies on.
      if (warehouseChanged) {
        updateData.warehouse = { id: warehouseId };
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

      setSuccess(
        warehouseChanged
          ? `✓ Stok kaydı ${selectedWarehouse?.name} deposuna taşındı.`
          : '✓ Ayarlar başarıyla güncellendi!'
      );
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
    if (available <= settings.minStockLevel)
      return { label: 'Düşük Stok', class: 'warning', icon: 'exclamation-triangle' };
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
                      {warehouseChanged && (
                        <>
                          <i className="fas fa-long-arrow-alt-right mx-2" aria-hidden="true"></i>
                          <span className="fw-bold text-warning">{selectedWarehouse?.name}</span>
                        </>
                      )}
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
                  {/* A plain label would point at an input that only exists while the picker is
                      open, so the field is named once and referenced from whichever control is
                      currently on screen. */}
                  <span className="form-label fw-bold d-block" id="warehouseFieldLabel">
                    <i className="fas fa-warehouse me-1 text-primary"></i>
                    Depo
                  </span>
                  {warehousePickerOpen ? (
                    <div
                      className="position-relative"
                      style={{ zIndex: showWarehouseDropdown ? 1055 : 'auto' }}
                    >
                      <input
                        type="text"
                        className="form-control form-control-lg border-primary"
                        id="warehouseSearch"
                        aria-labelledby="warehouseFieldLabel"
                        placeholder="Depo adı, konum veya sorumlu yazın..."
                        value={warehouseSearchTerm}
                        autoFocus
                        autoComplete="off"
                        onChange={(e) => {
                          setWarehouseSearchTerm(e.target.value);
                          setShowWarehouseDropdown(true);
                        }}
                        onFocus={() => setShowWarehouseDropdown(true)}
                        onBlur={() => {
                          // Delay to allow click events on dropdown items
                          setTimeout(() => setShowWarehouseDropdown(false), 200);
                        }}
                        onKeyDown={handleWarehouseSearchKeyDown}
                        style={{ borderWidth: '2px' }}
                      />
                      {showWarehouseDropdown && filteredWarehouses.length > 0 && (
                        <div className="list-group position-absolute w-100" style={SEARCH_DROPDOWN_STYLE}>
                          <div
                            className="list-group-item bg-light border-bottom fw-semibold small text-muted py-2 px-3"
                            style={{ position: 'sticky', top: 0, zIndex: 1 }}
                          >
                            <i className="fas fa-list me-2"></i>
                            {filteredWarehouses.length} depo listeleniyor
                          </div>
                          {filteredWarehouses.map((item, index) => (
                            <button
                              key={item.id}
                              type="button"
                              className={`list-group-item list-group-item-action border-0 ${
                                index === highlightedWarehouseIndex ? 'active bg-primary text-white' : ''
                              }`}
                              style={{ transition: 'all 0.15s ease', cursor: 'pointer' }}
                              onMouseDown={(e) => {
                                e.preventDefault();
                                handleWarehouseSelect(item);
                              }}
                              onMouseEnter={() => setHighlightedWarehouseIndex(index)}
                            >
                              <div className="d-flex justify-content-between align-items-center gap-2">
                                <div className="flex-grow-1 text-start">
                                  <div
                                    className={`fw-bold ${index === highlightedWarehouseIndex ? 'text-white' : ''}`}
                                  >
                                    {item.name}
                                    {isEmanetWarehouse(item) && (
                                      <span className="badge bg-info ms-2">Emanet</span>
                                    )}
                                  </div>
                                  <small
                                    className={
                                      index === highlightedWarehouseIndex
                                        ? 'text-white text-opacity-75'
                                        : 'text-muted'
                                    }
                                  >
                                    {item.location || 'Konum belirtilmemiş'}
                                  </small>
                                </div>
                                {item.id === warehouseId && (
                                  <i
                                    className={`fas fa-check-circle ${index === highlightedWarehouseIndex ? 'text-white' : 'text-success'}`}
                                  ></i>
                                )}
                              </div>
                            </button>
                          ))}
                        </div>
                      )}
                      {showWarehouseDropdown && filteredWarehouses.length === 0 && (
                        <div
                          className="position-absolute w-100 text-center text-muted p-3"
                          style={{ ...SEARCH_DROPDOWN_STYLE, borderColor: 'var(--color-error)' }}
                        >
                          {/* An unfiltered list that comes back empty is not the same problem as a
                              search that missed — the warehouse list simply has not arrived. */}
                          {warehouseSearchTerm.trim() ? (
                            <>
                              <i className="fas fa-search me-2 text-danger"></i>
                              <strong>Bu aramaya uyan depo yok.</strong>
                            </>
                          ) : (
                            <>
                              <i className="fas fa-warehouse me-2 text-danger"></i>
                              <strong>Seçilebilecek depo listesi yüklenemedi.</strong>
                              <div className="small mt-1">Sayfayı yenileyip tekrar deneyin.</div>
                            </>
                          )}
                        </div>
                      )}
                      <button
                        type="button"
                        className="btn btn-sm btn-link px-0 mt-1"
                        onClick={() => {
                          setWarehousePickerOpen(false);
                          setWarehouseSearchTerm('');
                          setShowWarehouseDropdown(false);
                        }}
                      >
                        Vazgeç
                      </button>
                    </div>
                  ) : (
                    <div className="d-flex align-items-center justify-content-between gap-2 border rounded-3 px-3 py-2 bg-light">
                      <div className="flex-grow-1" style={{ minWidth: 0 }}>
                        <div className="fw-semibold text-truncate">
                          {selectedWarehouse?.name || stock.warehouse?.name || '-'}
                          {isEmanetDepo && <span className="badge bg-info ms-2">Emanet</span>}
                        </div>
                        {selectedWarehouse?.location && (
                          <small className="text-muted d-block text-truncate">
                            {selectedWarehouse.location}
                          </small>
                        )}
                      </div>
                      <button
                        type="button"
                        className="btn btn-sm btn-outline-primary flex-shrink-0"
                        onClick={() => {
                          setWarehousePickerOpen(true);
                          setWarehouseSearchTerm('');
                          setShowWarehouseDropdown(true);
                        }}
                        disabled={warehouseMoveBlocked}
                      >
                        <i className="fas fa-exchange-alt me-1"></i>
                        Depoyu Değiştir
                      </button>
                    </div>
                  )}
                  {warehouseMoveBlocked ? (
                    <small className="text-muted d-block mt-1">
                      <i className="fas fa-lock me-1"></i>
                      Bu kayıtta {reservedQuantity} adet rezerve miktar var. Depo değişikliği için önce
                      siparişin ya da transferin tamamlanması gerekir.
                    </small>
                  ) : (
                    <small className="text-muted d-block mt-1">
                      Stok yanlış depoya girildiyse buradan düzeltebilirsiniz. Miktar değişmez, kayıt olduğu
                      gibi yeni depoya taşınır.
                    </small>
                  )}
                  {warehouseChanged && (
                    <div className="alert alert-warning py-2 mt-2 mb-0" role="alert">
                      <i className="fas fa-exclamation-triangle me-2"></i>
                      <strong>{stock.quantity} adet</strong> {stock.warehouse.name} deposundan{' '}
                      <strong>{selectedWarehouse?.name}</strong> deposuna taşınacak. Bu bir transfer kaydı
                      oluşturmaz; yanlış girilen depoyu düzeltir.
                      {!isEmanetWarehouse(stock.warehouse) && isEmanetDepo && (
                        <div className="mt-1">Emanet depo için müşteri bilgilerini doldurun.</div>
                      )}
                      {isEmanetWarehouse(stock.warehouse) && !isEmanetDepo && (
                        <div className="mt-1">Kayıttaki müşteri bilgileri silinecek.</div>
                      )}
                    </div>
                  )}
                </div>

                <div className="mb-3">
                  <label htmlFor="productId" className="form-label fw-bold">
                    <i className="fas fa-box me-1 text-primary"></i>
                    Ürün
                  </label>
                  <div className="position-relative" style={{ zIndex: showProductDropdown ? 1055 : 'auto' }}>
                    <input
                      type="text"
                      className={`form-control form-control-lg ${errors.productId ? 'is-invalid' : ''} ${showProductDropdown ? 'border-primary' : ''}`}
                      id="productId"
                      placeholder="Ürün adı veya stok kodu yazın..."
                      value={productSearchTerm}
                      onChange={(e) => {
                        setProductSearchTerm(e.target.value);
                        setShowProductDropdown(true);
                        if (e.target.value.trim() === '') {
                          setSettings((prev) => ({ ...prev, productId: null }));
                        }
                      }}
                      onFocus={() => {
                        if (productSearchTerm.trim()) {
                          setShowProductDropdown(true);
                        }
                      }}
                      onBlur={(e) => {
                        // Delay to allow click events on dropdown items
                        setTimeout(() => {
                          setShowProductDropdown(false);
                        }, 200);
                      }}
                      onKeyDown={handleProductSearchKeyDown}
                      style={{
                        borderWidth: showProductDropdown ? '2px' : '1px',
                      }}
                    />
                    {productSearchTerm && (
                      <button
                        type="button"
                        className="btn btn-sm btn-link position-absolute end-0 top-50 translate-middle-y me-2"
                        onClick={() => {
                          setProductSearchTerm('');
                          setSettings((prev) => ({ ...prev, productId: null }));
                          setShowProductDropdown(false);
                        }}
                        style={{ zIndex: 1056 }}
                      >
                        <i className="fas fa-times"></i>
                      </button>
                    )}
                    {showProductDropdown && filteredProducts.length > 0 && (
                      <div className="list-group position-absolute w-100" style={SEARCH_DROPDOWN_STYLE}>
                        <div
                          className="list-group-item bg-light border-bottom fw-semibold small text-muted py-2 px-3"
                          style={{ position: 'sticky', top: 0, zIndex: 1 }}
                        >
                          <i className="fas fa-list me-2"></i>
                          {filteredProducts.length} sonuç bulundu
                        </div>
                        {filteredProducts.map((product, index) => (
                          <button
                            key={product.id}
                            type="button"
                            className={`list-group-item list-group-item-action border-0 ${
                              index === highlightedProductIndex ? 'active bg-primary text-white' : ''
                            } ${settings.productId === product.id ? 'bg-success bg-opacity-10 border-start border-success border-3' : ''}`}
                            style={{
                              transition: 'all 0.15s ease',
                              cursor: 'pointer',
                            }}
                            onMouseDown={(e) => {
                              e.preventDefault();
                              handleProductSelect(product);
                            }}
                            onMouseEnter={() => setHighlightedProductIndex(index)}
                          >
                            <div className="d-flex justify-content-between align-items-center">
                              <div className="flex-grow-1">
                                <div
                                  className={`fw-bold ${index === highlightedProductIndex ? 'text-white' : ''}`}
                                >
                                  {product.name}
                                </div>
                                <small
                                  className={
                                    index === highlightedProductIndex
                                      ? 'text-white text-opacity-75'
                                      : 'text-muted'
                                  }
                                >
                                  {product.sku && <span>SKU: {product.sku}</span>}
                                  {product.sku && product.brand?.name && ' • '}
                                  {product.brand?.name && <span>Marka: {product.brand.name}</span>}
                                </small>
                              </div>
                              {settings.productId === product.id && (
                                <i
                                  className={`fas fa-check-circle ${index === highlightedProductIndex ? 'text-white' : 'text-success'} ms-2`}
                                ></i>
                              )}
                            </div>
                          </button>
                        ))}
                      </div>
                    )}
                    {showProductDropdown && productSearchTerm.trim() && filteredProducts.length === 0 && (
                      <div
                        className="position-absolute w-100 text-center text-muted p-3"
                        style={{ ...SEARCH_DROPDOWN_STYLE, borderColor: 'var(--color-error)' }}
                      >
                        <i className="fas fa-search me-2 text-danger"></i>
                        <strong>Arama kriterlerine uygun ürün bulunamadı.</strong>
                      </div>
                    )}
                  </div>
                  {selectedProduct && (
                    <div className="mt-2">
                      <div className="alert alert-light border d-flex justify-content-between align-items-center py-2">
                        <div>
                          <strong>Seçili Ürün:</strong> {selectedProduct.name}
                          {selectedProduct.sku && (
                            <span className="text-muted ms-2">({selectedProduct.sku})</span>
                          )}
                        </div>
                        <button
                          type="button"
                          className="btn btn-sm btn-outline-danger"
                          onClick={() => {
                            setProductSearchTerm('');
                            setSettings((prev) => ({ ...prev, productId: null }));
                          }}
                        >
                          <i className="fas fa-times me-1"></i>
                          Kaldır
                        </button>
                      </div>
                    </div>
                  )}
                  {errors.productId && <div className="invalid-feedback d-block">{errors.productId}</div>}
                  <small className="text-muted d-block mt-1">
                    Ürün adı veya stok kodunu yazarak arama yapın ve listeden seçin. Enter tuşu ile ilk sonucu
                    seçebilirsiniz.
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
                  <small className="text-muted">Emanet olarak verilen stok miktarı (kullanılamaz)</small>
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
                      {errors.customerName && <div className="invalid-feedback">{errors.customerName}</div>}
                    </div>
                    <div className="mb-3">
                      <label htmlFor="customerPhone" className="form-label fw-bold">
                        Müşteri Telefon Numarası <span className="text-danger">*</span>
                      </label>
                      <div className="input-group phone-input-group">
                        <span className="input-group-text">+90</span>
                        <input
                          type="tel"
                          className={`form-control form-control-lg ${errors.customerPhone ? 'is-invalid' : settings.customerPhone ? (isPhoneComplete(settings.customerPhone) ? 'is-valid' : '') : ''}`}
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
                      {!errors.customerPhone &&
                        settings.customerPhone &&
                        !isPhoneComplete(settings.customerPhone) && (
                          <small className="text-muted">Telefon 10 haneli olmalıdır</small>
                        )}
                    </div>
                  </>
                )}

                {/*
                  The waybill is recorded when the goods come in; this is where a mistyped number
                  gets corrected. Clearing the number clears the date with it — a date on its own
                  is unreachable by any waybill lookup.
                */}
                <div className="mb-3 p-3 rounded-3 border border-primary border-opacity-25 bg-primary bg-opacity-10">
                  <label className="form-label fw-bold mb-2">
                    <i className="fas fa-file-invoice me-1 text-primary"></i>
                    İrsaliye Bilgileri <small className="text-muted fw-normal">(opsiyonel)</small>
                  </label>
                  <div className="row g-2">
                    <div className="col-sm-7">
                      <label className="form-label small mb-1" htmlFor="irsaliyeNo">
                        İrsaliye Numarası
                      </label>
                      <IrsaliyePicker
                        id="irsaliyeNo"
                        value={settings.irsaliyeNo}
                        onChange={(next) => handleChange({ target: { name: 'irsaliyeNo', value: next } })}
                        onPick={({ irsaliyeNo: pickedNo, irsaliyeDate: pickedDate }) =>
                          setSettings((prev) => ({
                            ...prev,
                            irsaliyeNo: pickedNo,
                            irsaliyeDate: pickedDate || prev.irsaliyeDate,
                          }))
                        }
                      />
                    </div>
                    <div className="col-sm-5">
                      <label className="form-label small mb-1" htmlFor="irsaliyeDate">
                        İrsaliye Tarihi
                      </label>
                      <div className="input-group">
                        <span className="input-group-text bg-white">
                          <i className="fas fa-calendar-day text-primary"></i>
                        </span>
                        <input
                          type="date"
                          className="form-control"
                          id="irsaliyeDate"
                          name="irsaliyeDate"
                          value={settings.irsaliyeDate}
                          max={todayIso}
                          onChange={handleChange}
                          disabled={!settings.irsaliyeNo.trim()}
                          title={
                            settings.irsaliyeNo.trim() ? 'İrsaliye tarihi' : 'Önce irsaliye numarasını girin'
                          }
                        />
                      </div>
                    </div>
                  </div>
                  <small className="text-muted d-block mt-2">
                    Stok ekranından irsaliye numarası ve tarihine göre filtrelenebilir. Numarayı silerseniz
                    tarih de temizlenir.
                  </small>
                </div>

                <div className="mb-3">
                  <label htmlFor="additionNote" className="form-label fw-bold">
                    <i className="fas fa-sticky-note me-1 text-info"></i>
                    Stok Notu
                  </label>
                  <textarea
                    className="form-control"
                    id="additionNote"
                    name="additionNote"
                    rows="3"
                    value={settings.additionNote}
                    onChange={handleChange}
                    placeholder="Stok ile ilgili notlarınızı buraya yazabilirsiniz..."
                    style={{
                      resize: 'vertical',
                      minHeight: '80px',
                      maxHeight: '200px',
                    }}
                  />
                  <small className="text-muted">
                    Bu not stok listesinde görüntülenir ve stok'u oluşturan kişi veya adminler tarafından
                    düzenlenebilir.
                  </small>
                </div>

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
                      <strong
                        className={
                          stock.quantity - (stock.reservedQuantity || 0) - settings.consignedQuantity <= 0
                            ? 'text-danger'
                            : stock.quantity - (stock.reservedQuantity || 0) - settings.consignedQuantity <=
                                settings.minStockLevel
                              ? 'text-warning'
                              : 'text-success'
                        }
                      >
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
                    <button type="submit" className="btn btn-primary w-100" disabled={loading || success}>
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
