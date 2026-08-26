import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import axios from 'axios';
import { useAdminToast } from './AdminToast';
import './ManualOrderModal.css';
import { toTitleCaseTr } from '../utils/name';

const emptyItem = {
  productId: '',
  quantity: 1,
  unitPrice: '',
  productName: '',
  sku: '',
  listPrice: 0,
  stock: 0,
};
const channels = [
  ['WHATSAPP', 'fab fa-whatsapp', 'WhatsApp'],
  ['PHONE', 'fas fa-phone', 'Telefon'],
  ['IN_STORE', 'fas fa-store', 'Mağaza'],
  ['INSTAGRAM', 'fab fa-instagram', 'Instagram'],
  ['MARKETPLACE', 'fas fa-shopping-bag', 'Pazaryeri'],
  ['OTHER', 'fas fa-ellipsis-h', 'Diğer'],
];
const DAY_MS = 24 * 60 * 60 * 1000;
const money = (value) => Number(value || 0).toLocaleString('tr-TR', { style: 'currency', currency: 'TRY' });
const priceOf = (item) => Number(item.unitPrice === '' ? item.listPrice : item.unitPrice) || 0;
const lineTotal = (item) => priceOf(item) * (Number(item.quantity) || 0);

/** datetime-local expects local wall-clock time, which toISOString() would shift to UTC. */
const toLocalInput = (date) => {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
};
const formatDateTime = (value) =>
  value
    ? new Date(value).toLocaleString('tr-TR', {
        day: '2-digit',
        month: 'long',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '';

function Section({ number, icon, title, description, complete, children }) {
  return (
    <section className="manual-order-section">
      <div className="manual-order-section__header">
        <span className={`manual-order-section__number ${complete ? 'is-complete' : ''}`}>
          <i className={complete ? 'fas fa-check' : icon} />
        </span>
        <div>
          <div className="d-flex align-items-center gap-2">
            <h6 className="mb-0 fw-bold">
              {number}. {title}
            </h6>
            {complete && <span className="badge rounded-pill bg-success-subtle text-success">Tamam</span>}
          </div>
          <p className="text-muted small mb-0 mt-1">{description}</p>
        </div>
      </div>
      <div className="manual-order-section__body">{children}</div>
    </section>
  );
}

export default function ManualOrderModal({ onClose, onCreated }) {
  const toast = useAdminToast();
  const [form, setForm] = useState({
    firstName: '',
    lastName: '',
    phone: '',
    email: '',
    channel: 'WHATSAPP',
    channelReference: '',
    paymentMethod: 'BANK_TRANSFER',
    paymentState: 'WAITING',
    paymentDueAt: '',
    reminderAt: '',
    deliveryMethod: 'CARGO',
    cargoProviderId: '',
    cargoTrackingNo: '',
    shippingCost: 0,
    note: '',
    city: '',
    district: '',
    addressLine: '',
    items: [],
  });
  const [products, setProducts] = useState([]);
  const [productsLoading, setProductsLoading] = useState(false);
  const [productsError, setProductsError] = useState('');
  const [cargoProviders, setCargoProviders] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [customerSearch, setCustomerSearch] = useState('');
  const [search, setSearch] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [touched, setTouched] = useState(false);
  // Auto-filled fields stop being auto-filled once the user takes them over.
  const [reminderTouched, setReminderTouched] = useState(false);
  const [shippingTouched, setShippingTouched] = useState(false);
  const searchInputRef = useRef(null);

  useEffect(() => {
    let cancelled = false;
    setProductsLoading(true);
    const timer = setTimeout(() => {
      axios
        .get('/api/products', {
          params: { page: 0, size: 24, search: search.trim() || undefined, productType: 'SIMPLE' },
        })
        .then((r) => {
          if (cancelled) return;
          setProducts(r.data?.content || (Array.isArray(r.data) ? r.data : []));
          setProductsError('');
        })
        .catch(() => {
          if (cancelled) return;
          setProducts([]);
          setProductsError('Ürünler yüklenemedi. Aramayı tekrar deneyin.');
        })
        .finally(() => {
          if (!cancelled) setProductsLoading(false);
        });
    }, 300);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [search]);

  useEffect(() => {
    axios
      .get('/api/cargo-providers')
      .then((r) => setCargoProviders((r.data || []).filter((p) => p.active)))
      .catch(() => setCargoProviders([]));
  }, []);

  useEffect(() => {
    if (customerSearch.trim().length < 2) {
      setCustomers([]);
      return undefined;
    }
    const timer = setTimeout(
      () =>
        axios
          .get('/api/admin/customers', { params: { search: customerSearch, page: 0, size: 8 } })
          .then((r) => setCustomers(r.data?.content || []))
          .catch(() => setCustomers([])),
      250
    );
    return () => clearTimeout(timer);
  }, [customerSearch]);

  useEffect(() => {
    const close = (event) => {
      if (event.key === 'Escape' && !saving) onClose();
    };
    window.addEventListener('keydown', close);
    return () => window.removeEventListener('keydown', close);
  }, [onClose, saving]);

  const set = (key, value) => setForm((old) => ({ ...old, [key]: value }));
  const setItem = (index, patch) =>
    setForm((old) => ({
      ...old,
      items: old.items.map((item, i) => (i === index ? { ...item, ...patch } : item)),
    }));
  const removeItem = (index) =>
    setForm((old) => ({ ...old, items: old.items.filter((_, i) => i !== index) }));

  const selectCustomer = async (customer) => {
    setForm((old) => ({
      ...old,
      customerId: customer.id,
      firstName: customer.firstName || '',
      lastName: customer.lastName || '',
      phone: customer.phone || '',
      email: customer.email || '',
    }));
    setCustomerSearch(`${customer.firstName || ''} ${customer.lastName || ''}`.trim());
    setCustomers([]);
    try {
      const response = await axios.get(`/api/admin/customers/${customer.id}/addresses`);
      const address = (response.data || []).find((a) => a.default) || response.data?.[0];
      if (address)
        setForm((old) => ({
          ...old,
          addressLine: address.addressLine || '',
          district: address.district || '',
          city: address.city || '',
        }));
    } catch (_) {
      /* Address can be entered manually. */
    }
  };

  /** Adds the product, or bumps the quantity when it is already on the order. */
  const addProduct = useCallback(
    (product) => {
      setForm((old) => {
        const index = old.items.findIndex((item) => String(item.productId) === String(product.id));
        if (index >= 0) {
          return {
            ...old,
            items: old.items.map((item, i) =>
              i === index ? { ...item, quantity: Number(item.quantity || 0) + 1 } : item
            ),
          };
        }
        return {
          ...old,
          items: [
            ...old.items,
            {
              ...emptyItem,
              productId: product.id,
              productName: product.name,
              sku: product.sku,
              listPrice: Number(product.salePrice || product.price || 0),
              stock: Number(product.totalQuantity || 0),
            },
          ],
        };
      });
      toast.success(`${product.name} siparişe eklendi.`);
    },
    [toast]
  );

  /**
   * The backend rejects a reminder later than the due date, so a due date less than a day
   * out falls back to the due moment itself instead of "one day before".
   */
  const applyDefaultReminder = useCallback(
    (dueValue, manual) => {
      const due = new Date(dueValue);
      if (Number.isNaN(due.getTime())) return;
      const dayBefore = new Date(due.getTime() - DAY_MS);
      if (dayBefore > new Date()) {
        set('reminderAt', toLocalInput(dayBefore));
        toast.info(`Hatırlatma otomatik olarak 1 gün öncesine ayarlandı: ${formatDateTime(dayBefore)}`);
      } else {
        set('reminderAt', toLocalInput(due));
        toast.warning('Son ödeme tarihine 1 günden az kaldı; hatırlatma son ödeme anına ayarlandı.');
      }
      if (manual) setReminderTouched(false);
    },
    [toast]
  );

  const handleDueChange = (value) => {
    set('paymentDueAt', value);
    if (!value || reminderTouched) return;
    applyDefaultReminder(value, false);
  };

  const selectCargoProvider = (provider) => {
    setForm((old) => {
      const next = { ...old, cargoProviderId: provider.id };
      if (!shippingTouched) next.shippingCost = Number(provider.baseCost || 0);
      return next;
    });
  };

  const selectDeliveryMethod = (method) => {
    setForm((old) => ({
      ...old,
      deliveryMethod: method,
      cargoProviderId: method === 'CARGO' ? old.cargoProviderId : '',
      cargoTrackingNo: method === 'CARGO' ? old.cargoTrackingNo : '',
      shippingCost: method === 'OWN_TRANSFER' && !shippingTouched ? 0 : old.shippingCost,
    }));
  };

  const subtotal = useMemo(() => form.items.reduce((sum, item) => sum + lineTotal(item), 0), [form.items]);
  const selectedProvider = cargoProviders.find((p) => String(p.id) === String(form.cargoProviderId));
  const freeShipping =
    form.deliveryMethod === 'CARGO' &&
    Boolean(selectedProvider) &&
    Number(selectedProvider?.freeShippingThreshold) > 0 &&
    subtotal >= Number(selectedProvider?.freeShippingThreshold);
  const shippingCost = freeShipping && !shippingTouched ? 0 : Number(form.shippingCost || 0);
  const trackingUrl =
    selectedProvider?.trackingUrlTemplate && form.cargoTrackingNo.trim()
      ? selectedProvider.trackingUrlTemplate.replace('{trackingNo}', form.cargoTrackingNo.trim())
      : '';
  const estimatedDelivery = selectedProvider
    ? new Date(Date.now() + (selectedProvider.estimatedDeliveryDays || 3) * DAY_MS).toLocaleDateString(
        'tr-TR',
        { day: '2-digit', month: 'long', year: 'numeric' }
      )
    : '';
  const total = subtotal + shippingCost;
  const itemCount = form.items.reduce((sum, item) => sum + Number(item.quantity || 0), 0);
  const customerComplete = Boolean(form.firstName && form.lastName && form.phone);
  const addressComplete = Boolean(form.addressLine && form.city);
  const productsComplete =
    form.items.length > 0 && form.items.every((item) => item.productId && Number(item.quantity) > 0);
  const deliveryComplete = form.deliveryMethod === 'OWN_TRANSFER' || Boolean(form.cargoProviderId);
  const paymentComplete = form.paymentState !== 'SCHEDULED' || Boolean(form.paymentDueAt);
  const invalid = (value) => touched && !value;
  const addedIds = new Set(form.items.map((item) => String(item.productId)));
  const shortStock = form.items.filter((item) => Number(item.quantity) > Number(item.stock || 0));

  const submit = async () => {
    setTouched(true);
    setError('');
    if (!customerComplete || !form.addressLine || !form.city) {
      setError('Müşteri ve teslimat adresi alanlarını tamamlayın.');
      return;
    }
    if (!productsComplete) {
      setError('Siparişe en az bir ürün ekleyin ve adetlerin sıfırdan büyük olduğundan emin olun.');
      return;
    }
    if (!deliveryComplete) {
      setError('Kargo ile gönderim için bir kargo firması seçin.');
      return;
    }
    if (!paymentComplete) {
      setError('Planlı ödeme için son ödeme tarihi zorunludur.');
      return;
    }
    setSaving(true);
    try {
      const response = await axios.post('/api/admin/orders/manual', {
        customerId: form.customerId || null,
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        phone: form.phone.trim(),
        email: form.email?.trim() || null,
        channel: form.channel,
        channelReference: form.channelReference?.trim() || null,
        paymentMethod: form.paymentMethod,
        paymentState: form.paymentState,
        paymentDueAt: form.paymentDueAt || null,
        reminderAt: form.reminderAt || null,
        deliveryMethod: form.deliveryMethod,
        cargoProviderId: form.deliveryMethod === 'CARGO' ? Number(form.cargoProviderId) : null,
        cargoTrackingNo: form.deliveryMethod === 'CARGO' ? form.cargoTrackingNo.trim() || null : null,
        shippingCost,
        note: form.note?.trim() || null,
        shippingAddress: {
          addressLine: form.addressLine.trim(),
          district: form.district.trim(),
          city: form.city.trim(),
          firstName: form.firstName.trim(),
          lastName: form.lastName.trim(),
          phone: form.phone.trim(),
        },
        items: form.items.map((item) => ({
          productId: Number(item.productId),
          quantity: Number(item.quantity),
          unitPrice: item.unitPrice === '' ? null : Number(item.unitPrice),
        })),
      });
      onCreated({ ...response.data, deliveryMethod: form.deliveryMethod });
    } catch (e) {
      setError(e.response?.data?.message || 'Sipariş oluşturulamadı. Bilgileri kontrol edip tekrar deneyin.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div
      className="modal show d-block manual-order-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="new-order-title"
    >
      <div className="modal-dialog modal-xl modal-dialog-scrollable manual-order-dialog">
        <div className="modal-content border-0 manual-order-modal">
          <header className="modal-header manual-order-header">
            <div className="d-flex align-items-center gap-3">
              <span className="manual-order-header__icon">
                <i className="fas fa-receipt" />
              </span>
              <div>
                <h4 id="new-order-title" className="modal-title fw-bold">
                  Yeni Sipariş Kaydı
                </h4>
                <p className="mb-0 text-muted small">
                  Satış kanalından teslimata kadar sipariş bilgilerini kaydedin.
                </p>
              </div>
            </div>
            <button
              type="button"
              className="btn-close"
              onClick={onClose}
              disabled={saving}
              aria-label="Kapat"
            />
          </header>
          <div className="modal-body manual-order-body">
            {error && (
              <div className="alert alert-danger d-flex align-items-center gap-2" role="alert">
                <i className="fas fa-circle-exclamation" />
                <span>{error}</span>
              </div>
            )}
            <div className="row g-4">
              <div className="col-xl-8">
                <Section
                  number="1"
                  icon="fas fa-user"
                  title="Müşteri ve satış kanalı"
                  description="Kayıtlı müşteriyi bulun veya yeni müşteri bilgilerini girin."
                  complete={customerComplete}
                >
                  <div className="position-relative mb-4">
                    <label className="form-label fw-semibold">Kayıtlı müşteri ara</label>
                    <div className="input-group manual-order-search">
                      <span className="input-group-text">
                        <i className="fas fa-search" />
                      </span>
                      <input
                        className="form-control"
                        placeholder="Ad, telefon veya e-posta ile arayın"
                        value={customerSearch}
                        onChange={(e) => {
                          setCustomerSearch(e.target.value);
                          set('customerId', null);
                        }}
                        autoComplete="off"
                      />
                      {customerSearch && (
                        <button
                          type="button"
                          className="btn btn-outline-secondary"
                          onClick={() => {
                            setCustomerSearch('');
                            setCustomers([]);
                            set('customerId', null);
                          }}
                          aria-label="Aramayı temizle"
                        >
                          <i className="fas fa-times" />
                        </button>
                      )}
                    </div>
                    {customers.length > 0 && (
                      <div className="list-group manual-order-results shadow-sm">
                        {customers.map((customer) => (
                          <button
                            type="button"
                            className="list-group-item list-group-item-action d-flex align-items-center gap-3"
                            key={customer.id}
                            onClick={() => selectCustomer(customer)}
                          >
                            <span className="manual-order-avatar">
                              {customer.firstName?.[0]}
                              {customer.lastName?.[0]}
                            </span>
                            <span className="text-start">
                              <strong className="d-block">
                                {customer.firstName} {customer.lastName}
                              </strong>
                              <small className="text-muted">
                                {customer.phone || 'Telefon yok'} · {customer.email || 'E-posta yok'}
                              </small>
                            </span>
                          </button>
                        ))}
                      </div>
                    )}
                    {form.customerId && (
                      <div className="small text-success mt-2">
                        <i className="fas fa-check-circle me-1" /> Kayıtlı müşteri seçildi; adres bilgileri
                        otomatik getirildi.
                      </div>
                    )}
                  </div>
                  <div className="row g-3">
                    {[
                      ['firstName', 'Ad', 'text', 'Örn. Ayşe'],
                      ['lastName', 'Soyad', 'text', 'Örn. Yılmaz'],
                      ['phone', 'Telefon', 'tel', '05xx xxx xx xx'],
                      ['email', 'E-posta', 'email', 'ornek@firma.com'],
                    ].map(([key, label, type, placeholder], index) => (
                      <div className="col-md-6" key={key}>
                        <label className="form-label">
                          {label}
                          {index < 3 && <span className="text-danger ms-1">*</span>}
                        </label>
                        <input
                          type={type}
                          className={`form-control ${index < 3 && invalid(form[key]) ? 'is-invalid' : ''}`}
                          placeholder={placeholder}
                          value={form[key]}
                          onChange={(e) => set(key, e.target.value)}
                          // Ad/soyad blur'da düzgün büyük harfe çevrilir; diğer alanlara dokunulmaz.
                          onBlur={
                            key === 'firstName' || key === 'lastName'
                              ? (e) => set(key, toTitleCaseTr(e.target.value))
                              : undefined
                          }
                        />
                      </div>
                    ))}
                    <div className="col-12">
                      <label className="form-label">
                        Sipariş kanalı <span className="text-danger">*</span>
                      </label>
                      <div className="manual-order-channels">
                        {channels.map(([value, icon, label]) => (
                          <button
                            key={value}
                            type="button"
                            className={`manual-order-channel ${form.channel === value ? 'is-selected' : ''}`}
                            onClick={() => set('channel', value)}
                          >
                            <i className={icon} />
                            <span>{label}</span>
                          </button>
                        ))}
                      </div>
                    </div>
                    <div className="col-12">
                      <label className="form-label">
                        Kanal referansı <span className="text-muted small">(isteğe bağlı)</span>
                      </label>
                      <input
                        className="form-control"
                        placeholder={
                          form.channel === 'WHATSAPP'
                            ? 'WhatsApp konuşma veya talep kodu'
                            : 'Mesaj, fiş veya dış sistem referansı'
                        }
                        value={form.channelReference}
                        onChange={(e) => set('channelReference', e.target.value)}
                      />
                      <div className="form-text">
                        Siparişi daha sonra konuşma veya dış kayıtla eşleştirmek için kullanılır.
                      </div>
                    </div>
                  </div>
                </Section>
                <Section
                  number="2"
                  icon="fas fa-location-dot"
                  title="Teslimat bilgileri"
                  description="Siparişin gönderileceği açık ve doğrulanabilir adres."
                  complete={addressComplete}
                >
                  <div className="mb-3">
                    <label className="form-label">
                      Açık adres <span className="text-danger">*</span>
                    </label>
                    <textarea
                      className={`form-control ${invalid(form.addressLine) ? 'is-invalid' : ''}`}
                      rows="3"
                      placeholder="Mahalle, cadde/sokak, bina ve daire bilgisi"
                      value={form.addressLine}
                      onChange={(e) => set('addressLine', e.target.value)}
                    />
                  </div>
                  <div className="row g-3">
                    <div className="col-md-6">
                      <label className="form-label">İlçe</label>
                      <input
                        className="form-control"
                        placeholder="İlçe"
                        value={form.district}
                        onChange={(e) => set('district', e.target.value)}
                      />
                    </div>
                    <div className="col-md-6">
                      <label className="form-label">
                        İl <span className="text-danger">*</span>
                      </label>
                      <input
                        className={`form-control ${invalid(form.city) ? 'is-invalid' : ''}`}
                        placeholder="İl"
                        value={form.city}
                        onChange={(e) => set('city', e.target.value)}
                      />
                    </div>
                  </div>
                </Section>
                <Section
                  number="3"
                  icon="fas fa-box-open"
                  title="Sipariş ürünleri"
                  description="Ürünü arayıp listeden ekleyin; adet ve siparişe özel fiyatı satırda düzenleyin."
                  complete={productsComplete}
                >
                  <div className="manual-order-picker">
                    <div className="input-group manual-order-search">
                      <span className="input-group-text">
                        <i className="fas fa-search" />
                      </span>
                      <input
                        ref={searchInputRef}
                        className="form-control"
                        placeholder="Ürün adı, SKU, marka veya kategori ile arayın"
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') {
                            e.preventDefault();
                            if (products.length === 1) addProduct(products[0]);
                          }
                        }}
                        autoComplete="off"
                      />
                      {productsLoading && (
                        <span className="input-group-text bg-white border-start-0">
                          <span className="spinner-border spinner-border-sm text-primary" />
                        </span>
                      )}
                      {search && !productsLoading && (
                        <button
                          type="button"
                          className="btn btn-outline-secondary"
                          onClick={() => {
                            setSearch('');
                            searchInputRef.current?.focus();
                          }}
                          aria-label="Ürün aramasını temizle"
                        >
                          <i className="fas fa-times" />
                        </button>
                      )}
                    </div>
                    <div className="manual-order-picker__meta">
                      <span>
                        {search.trim()
                          ? `"${search.trim()}" için ${products.length} sonuç`
                          : `Son güncellenen ${products.length} ürün`}
                      </span>
                      <span className="text-muted">Eklemek için satıra tıklayın</span>
                    </div>
                    <div className="manual-order-picker__list">
                      {productsError && (
                        <div className="manual-order-picker__empty text-danger">
                          <i className="fas fa-triangle-exclamation d-block fs-4 mb-2" />
                          {productsError}
                        </div>
                      )}
                      {!productsError && !productsLoading && products.length === 0 && (
                        <div className="manual-order-picker__empty">
                          <i className="fas fa-magnifying-glass d-block fs-4 mb-2 opacity-50" />
                          Eşleşen ürün bulunamadı. Farklı bir ad veya SKU deneyin.
                        </div>
                      )}
                      {products.map((product) => {
                        const stock = Number(product.totalQuantity || 0);
                        const added = addedIds.has(String(product.id));
                        return (
                          <button
                            type="button"
                            key={product.id}
                            className={`manual-order-product ${added ? 'is-added' : ''}`}
                            onClick={() => addProduct(product)}
                          >
                            <span className="manual-order-product__info">
                              <span className="manual-order-product__name">{product.name}</span>
                              <span className="manual-order-product__meta">
                                <span className="manual-order-sku">{product.sku}</span>
                                {product.brandName && <span>{product.brandName}</span>}
                                <span className={stock > 0 ? 'text-success' : 'text-danger'}>
                                  <i className="fas fa-cubes me-1" />
                                  {stock > 0 ? `${stock} adet stok` : 'Stok yok'}
                                </span>
                              </span>
                            </span>
                            <span className="manual-order-product__price">
                              {money(product.salePrice || product.price)}
                            </span>
                            <span className="manual-order-product__action">
                              <i className="fas fa-plus" />
                              {added ? 'Adet +1' : 'Ekle'}
                            </span>
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  {form.items.length === 0 ? (
                    <div className={`manual-order-empty ${touched ? 'is-invalid' : ''}`}>
                      <i className="fas fa-cart-plus d-block fs-3 mb-2 opacity-50" />
                      Henüz ürün eklenmedi. Yukarıdaki listeden ürün seçin.
                    </div>
                  ) : (
                    <div className="d-flex flex-column gap-3 mt-4">
                      {form.items.map((item, index) => {
                        const short = Number(item.quantity) > Number(item.stock || 0);
                        return (
                          <div className="manual-order-item" key={item.productId}>
                            <div className="manual-order-item__index">{index + 1}</div>
                            <div className="flex-grow-1 min-w-0">
                              <div className="d-flex justify-content-between align-items-start gap-3">
                                <div className="min-w-0">
                                  <strong className="d-block text-truncate">{item.productName}</strong>
                                  <span className="manual-order-sku">{item.sku}</span>
                                  <span className={`ms-2 small ${short ? 'text-danger' : 'text-muted'}`}>
                                    <i className="fas fa-cubes me-1" />
                                    {item.stock} adet stok
                                  </span>
                                </div>
                                <button
                                  type="button"
                                  className="btn btn-sm btn-outline-danger"
                                  onClick={() => removeItem(index)}
                                  aria-label={`${item.productName} ürününü kaldır`}
                                >
                                  <i className="fas fa-trash-alt" />
                                </button>
                              </div>
                              <div className="row g-2 align-items-end mt-1">
                                <div className="col-6 col-md-4">
                                  <label className="form-label">Adet</label>
                                  <div className="input-group manual-order-stepper">
                                    <button
                                      type="button"
                                      className="btn btn-outline-secondary"
                                      onClick={() =>
                                        setItem(index, { quantity: Math.max(1, Number(item.quantity) - 1) })
                                      }
                                      aria-label="Adet azalt"
                                    >
                                      <i className="fas fa-minus" />
                                    </button>
                                    <input
                                      type="number"
                                      min="1"
                                      className={`form-control text-center ${short ? 'is-invalid' : ''}`}
                                      value={item.quantity}
                                      onChange={(e) => setItem(index, { quantity: e.target.value })}
                                    />
                                    <button
                                      type="button"
                                      className="btn btn-outline-secondary"
                                      onClick={() => setItem(index, { quantity: Number(item.quantity) + 1 })}
                                      aria-label="Adet artır"
                                    >
                                      <i className="fas fa-plus" />
                                    </button>
                                  </div>
                                </div>
                                <div className="col-6 col-md-4">
                                  <label className="form-label">Birim fiyat</label>
                                  <div className="input-group">
                                    <span className="input-group-text">₺</span>
                                    <input
                                      type="number"
                                      min="0"
                                      step=".01"
                                      className="form-control"
                                      placeholder={String(item.listPrice)}
                                      value={item.unitPrice}
                                      onChange={(e) => setItem(index, { unitPrice: e.target.value })}
                                    />
                                    {item.unitPrice !== '' && (
                                      <button
                                        type="button"
                                        className="btn btn-outline-secondary"
                                        title="Liste fiyatına dön"
                                        onClick={() => setItem(index, { unitPrice: '' })}
                                      >
                                        <i className="fas fa-rotate-left" />
                                      </button>
                                    )}
                                  </div>
                                </div>
                                <div className="col-12 col-md-4 text-md-end">
                                  <span className="text-muted small d-block">Satır toplamı</span>
                                  <strong className="fs-6">{money(lineTotal(item))}</strong>
                                </div>
                              </div>
                              {item.unitPrice !== '' && Number(item.unitPrice) !== Number(item.listPrice) && (
                                <div className="small text-muted mt-2">
                                  Liste fiyatı <strong>{money(item.listPrice)}</strong> yerine siparişe özel
                                  fiyat uygulanıyor.
                                </div>
                              )}
                              {short && (
                                <div className="small text-danger mt-2">
                                  <i className="fas fa-triangle-exclamation me-1" />
                                  Stok {item.stock} adet; bu adetle sipariş kaydedilemeyebilir.
                                </div>
                              )}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </Section>
                <Section
                  number="4"
                  icon="fas fa-truck"
                  title="Teslimat yöntemi"
                  description="Sipariş kargoya mı verilecek, kendi aracımızla mı sevk edilecek?"
                  complete={deliveryComplete}
                >
                  <div className="manual-order-delivery">
                    {[
                      [
                        'CARGO',
                        'fas fa-truck-fast',
                        'Kargo ile gönder',
                        'Seçilen kargo firmasına teslim edilir, takip numarası siparişe işlenir.',
                      ],
                      [
                        'OWN_TRANSFER',
                        'fas fa-truck-ramp-box',
                        'Kendi aracımızla',
                        'Sipariş, transfer ekranında müşteri sevkiyatı olarak açılır ve buradan izlenir.',
                      ],
                    ].map(([value, icon, label, hint]) => (
                      <button
                        key={value}
                        type="button"
                        className={`manual-order-delivery__option ${form.deliveryMethod === value ? 'is-selected' : ''}`}
                        onClick={() => selectDeliveryMethod(value)}
                      >
                        <i className={icon} />
                        <span className="min-w-0">
                          <strong className="d-block">{label}</strong>
                          <small className="text-muted">{hint}</small>
                        </span>
                      </button>
                    ))}
                  </div>

                  {form.deliveryMethod === 'CARGO' && (
                    <div className="mt-4">
                      <label className="form-label">
                        Kargo firması <span className="text-danger">*</span>
                      </label>
                      {cargoProviders.length === 0 ? (
                        <div className="alert alert-warning mb-0 d-flex align-items-center gap-2">
                          <i className="fas fa-triangle-exclamation" />
                          <span>
                            Tanımlı aktif kargo firması yok. Kargo Ayarları ekranından firma ekleyin veya
                            kendi aracınızla sevkiyatı seçin.
                          </span>
                        </div>
                      ) : (
                        <div className="manual-order-cargo">
                          {cargoProviders.map((provider) => (
                            <button
                              key={provider.id}
                              type="button"
                              className={`manual-order-cargo__option ${String(form.cargoProviderId) === String(provider.id) ? 'is-selected' : ''} ${touched && !form.cargoProviderId ? 'is-missing' : ''}`}
                              onClick={() => selectCargoProvider(provider)}
                            >
                              <strong className="d-block">{provider.name}</strong>
                              <span className="small text-muted d-block">
                                {money(provider.baseCost)} · {provider.estimatedDeliveryDays || 3} iş günü
                              </span>
                              {Number(provider.freeShippingThreshold) > 0 && (
                                <span className="small text-success d-block">
                                  {money(provider.freeShippingThreshold)} üzeri ücretsiz
                                </span>
                              )}
                            </button>
                          ))}
                        </div>
                      )}
                      {freeShipping && !shippingTouched && (
                        <div className="small text-success mt-2">
                          <i className="fas fa-gift me-1" />
                          Sepet tutarı ücretsiz kargo limitini geçtiği için kargo ücreti sıfırlandı.
                        </div>
                      )}
                      {selectedProvider && (
                        <div className="row g-3 mt-1">
                          <div className="col-md-7">
                            <label className="form-label">
                              Takip kodu <span className="text-muted small">(isteğe bağlı)</span>
                            </label>
                            <div className="input-group">
                              <span className="input-group-text">
                                <i className="fas fa-barcode" />
                              </span>
                              <input
                                className="form-control"
                                placeholder="Kargo fişindeki takip numarası"
                                value={form.cargoTrackingNo}
                                onChange={(e) => set('cargoTrackingNo', e.target.value)}
                                autoComplete="off"
                              />
                              {trackingUrl && (
                                <a
                                  className="btn btn-outline-primary"
                                  href={trackingUrl}
                                  target="_blank"
                                  rel="noreferrer"
                                  title="Takip sayfasını aç"
                                >
                                  <i className="fas fa-arrow-up-right-from-square" />
                                </a>
                              )}
                            </div>
                            <div className="form-text">
                              {form.cargoTrackingNo.trim() && !trackingUrl
                                ? `${selectedProvider.name} için takip URL tanımlı değil; Kargo Ayarlarından ekleyebilirsiniz.`
                                : 'Kodu şimdi girmezseniz sipariş detayından sonradan ekleyebilirsiniz.'}
                            </div>
                          </div>
                          <div className="col-md-5">
                            <label className="form-label">Tahmini teslim</label>
                            <div className="form-control-plaintext fw-semibold">
                              <i className="fas fa-calendar-check me-2 text-success" />
                              {estimatedDelivery}
                            </div>
                            <div className="form-text">
                              {selectedProvider.name} tarifesindeki{' '}
                              {selectedProvider.estimatedDeliveryDays || 3} iş gününe göre hesaplandı.
                            </div>
                          </div>
                        </div>
                      )}
                    </div>
                  )}

                  {form.deliveryMethod === 'OWN_TRANSFER' && (
                    <div className="alert alert-info d-flex align-items-start gap-2 mt-4 mb-0">
                      <i className="fas fa-circle-info mt-1" />
                      <span>
                        Sipariş kaydedildikten sonra sipariş detayından <strong>Sevkiyat oluştur</strong> ile
                        transfer kaydı açabilirsiniz. Sevkiyat yola çıktığında sipariş{' '}
                        <strong>Kargoda</strong>, tamamlandığında <strong>Teslim Edildi</strong> durumuna
                        geçer.
                      </span>
                    </div>
                  )}

                  <div className="row g-3 mt-1">
                    <div className="col-md-5">
                      <label className="form-label">
                        {form.deliveryMethod === 'CARGO' ? 'Kargo ücreti' : 'Sevkiyat ücreti'}
                      </label>
                      <div className="input-group">
                        <span className="input-group-text">₺</span>
                        <input
                          type="number"
                          min="0"
                          step=".01"
                          className="form-control"
                          value={shippingTouched ? form.shippingCost : shippingCost}
                          onChange={(e) => {
                            setShippingTouched(true);
                            set('shippingCost', e.target.value);
                          }}
                        />
                        {shippingTouched && (
                          <button
                            type="button"
                            className="btn btn-outline-secondary"
                            title="Firma tarifesine dön"
                            onClick={() => {
                              setShippingTouched(false);
                              set('shippingCost', Number(selectedProvider?.baseCost || 0));
                            }}
                          >
                            <i className="fas fa-rotate-left" />
                          </button>
                        )}
                      </div>
                      <div className="form-text">Müşteriye yansıtılacak tutar; genel toplama eklenir.</div>
                    </div>
                  </div>
                </Section>
                <Section
                  number="5"
                  icon="fas fa-wallet"
                  title="Ödeme ve sipariş notu"
                  description="Tahsilat durumunu ve takip edilecek tarihleri belirleyin."
                  complete={paymentComplete}
                >
                  <div className="row g-3">
                    <div className="col-md-6">
                      <label className="form-label">Ödeme yöntemi</label>
                      <select
                        className="form-select"
                        value={form.paymentMethod}
                        onChange={(e) => set('paymentMethod', e.target.value)}
                      >
                        <option value="BANK_TRANSFER">Havale / EFT</option>
                        <option value="DOOR_CASH">Kapıda nakit</option>
                        <option value="DOOR_CARD">Kapıda kart</option>
                      </select>
                    </div>
                    <div className="col-md-6">
                      <label className="form-label">Ödeme durumu</label>
                      <select
                        className="form-select"
                        value={form.paymentState}
                        onChange={(e) => set('paymentState', e.target.value)}
                      >
                        <option value="WAITING">Ödeme bekleniyor</option>
                        <option value="SCHEDULED">Belirli tarihte ödenecek</option>
                        <option value="RECEIVED">Ödeme alındı</option>
                        <option value="NOT_REQUIRED">Ödeme gerekmiyor</option>
                      </select>
                    </div>
                    {(form.paymentState === 'WAITING' || form.paymentState === 'SCHEDULED') && (
                      <>
                        <div className="col-md-6">
                          <label className="form-label">
                            Son ödeme tarihi{' '}
                            {form.paymentState === 'SCHEDULED' && <span className="text-danger">*</span>}
                          </label>
                          <input
                            type="datetime-local"
                            className={`form-control ${form.paymentState === 'SCHEDULED' && invalid(form.paymentDueAt) ? 'is-invalid' : ''}`}
                            value={form.paymentDueAt}
                            onChange={(e) => handleDueChange(e.target.value)}
                          />
                          <div className="form-text">
                            Tarih girildiğinde hatırlatma otomatik olarak 1 gün öncesine ayarlanır.
                          </div>
                        </div>
                        <div className="col-md-6">
                          <label className="form-label d-flex justify-content-between align-items-center">
                            <span>Admin hatırlatması</span>
                            {form.paymentDueAt && (
                              <button
                                type="button"
                                className="btn btn-link btn-sm p-0 text-decoration-none"
                                onClick={() => applyDefaultReminder(form.paymentDueAt, true)}
                              >
                                <i className="fas fa-wand-magic-sparkles me-1" />1 gün öncesine ayarla
                              </button>
                            )}
                          </label>
                          <input
                            type="datetime-local"
                            className="form-control"
                            value={form.reminderAt}
                            onChange={(e) => {
                              setReminderTouched(true);
                              set('reminderAt', e.target.value);
                            }}
                          />
                          {form.reminderAt && (
                            <div className="form-text text-primary">
                              <i className="fas fa-bell me-1" />
                              {formatDateTime(form.reminderAt)} tarihinde hatırlatılacak
                              {reminderTouched ? ' (elle ayarlandı)' : ''}.
                            </div>
                          )}
                        </div>
                      </>
                    )}
                    <div className="col-12">
                      <label className="form-label">
                        Sipariş notu <span className="text-muted small">(isteğe bağlı)</span>
                      </label>
                      <textarea
                        className="form-control"
                        rows="2"
                        placeholder="Paketleme, teslimat veya müşteri talebi…"
                        value={form.note}
                        onChange={(e) => set('note', e.target.value)}
                      />
                    </div>
                  </div>
                </Section>
              </div>
              <aside className="col-xl-4">
                <div className="manual-order-summary">
                  <div className="d-flex justify-content-between align-items-center mb-4">
                    <div>
                      <span className="text-muted small d-block">Sipariş özeti</span>
                      <strong>{itemCount} ürün/adet</strong>
                    </div>
                    <span className="manual-order-summary__channel">
                      <i className={channels.find(([v]) => v === form.channel)?.[1]} />{' '}
                      {channels.find(([v]) => v === form.channel)?.[2]}
                    </span>
                  </div>
                  <div className="manual-order-summary__customer">
                    <span className="manual-order-avatar">
                      <i className="fas fa-user" />
                    </span>
                    <div>
                      <strong className="d-block">
                        {customerComplete ? `${form.firstName} ${form.lastName}` : 'Müşteri bekleniyor'}
                      </strong>
                      <small className="text-muted">{form.phone || 'Telefon bilgisi girilmedi'}</small>
                    </div>
                  </div>
                  <div className="manual-order-summary__items">
                    {form.items.length ? (
                      form.items.map((item) => (
                        <div className="d-flex justify-content-between gap-3" key={item.productId}>
                          <span className="text-truncate">
                            {item.quantity} × {item.productName}
                          </span>
                          <strong className="text-nowrap">{money(lineTotal(item))}</strong>
                        </div>
                      ))
                    ) : (
                      <div className="text-center text-muted py-3">
                        <i className="fas fa-box-open d-block fs-3 mb-2 opacity-50" />
                        Henüz ürün seçilmedi
                      </div>
                    )}
                  </div>
                  <div className="manual-order-summary__delivery">
                    <i
                      className={
                        form.deliveryMethod === 'CARGO'
                          ? 'fas fa-truck-fast text-primary'
                          : 'fas fa-truck-ramp-box text-primary'
                      }
                    />
                    <div className="min-w-0">
                      <strong className="d-block">
                        {form.deliveryMethod === 'CARGO' ? 'Kargo ile gönderim' : 'Kendi aracımızla sevkiyat'}
                      </strong>
                      <small className="text-muted d-block text-truncate">
                        {form.deliveryMethod === 'CARGO'
                          ? [selectedProvider?.name || 'Kargo firması seçilmedi', form.cargoTrackingNo.trim()]
                              .filter(Boolean)
                              .join(' · ')
                          : 'Transfer ekranından takip edilir'}
                      </small>
                    </div>
                  </div>
                  <div className="manual-order-summary__totals">
                    <div>
                      <span>Ara toplam</span>
                      <strong>{money(subtotal)}</strong>
                    </div>
                    <div>
                      <span>{form.deliveryMethod === 'CARGO' ? 'Kargo' : 'Sevkiyat'}</span>
                      <strong>{shippingCost ? money(shippingCost) : 'Ücretsiz'}</strong>
                    </div>
                    <div className="manual-order-summary__grand">
                      <span>Genel toplam</span>
                      <strong>{money(total)}</strong>
                    </div>
                  </div>
                  {shortStock.length > 0 && (
                    <div className="small text-danger mt-3">
                      <i className="fas fa-triangle-exclamation me-2" />
                      {shortStock.length} üründe istenen adet mevcut stoğun üzerinde.
                    </div>
                  )}
                  <div className="small text-muted mt-3">
                    <i className="fas fa-shield-alt me-2 text-success" />
                    Sipariş kaydedildikten sonra stok rezerve edilir.
                  </div>
                </div>
              </aside>
            </div>
          </div>
          <footer className="modal-footer manual-order-footer">
            <span className="text-muted small me-auto d-none d-md-inline">
              <kbd>Esc</kbd> ile kapatabilirsiniz
            </span>
            <button
              type="button"
              className="btn btn-outline-secondary px-4"
              onClick={onClose}
              disabled={saving}
            >
              Vazgeç
            </button>
            <button type="button" className="btn btn-primary px-4" onClick={submit} disabled={saving}>
              {saving ? (
                <>
                  <span className="spinner-border spinner-border-sm me-2" />
                  Kaydediliyor…
                </>
              ) : (
                <>
                  <i className="fas fa-check me-2" />
                  Sipariş Kaydını Oluştur
                </>
              )}
            </button>
          </footer>
        </div>
      </div>
    </div>
  );
}
