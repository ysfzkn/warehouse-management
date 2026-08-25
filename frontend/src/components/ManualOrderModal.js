import React, { useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import './ManualOrderModal.css';

const emptyItem = { productId: '', quantity: 1, unitPrice: '', productName: '', sku: '', listPrice: 0 };
const channels = [
  ['WHATSAPP', 'fab fa-whatsapp', 'WhatsApp'],
  ['PHONE', 'fas fa-phone', 'Telefon'],
  ['IN_STORE', 'fas fa-store', 'Mağaza'],
  ['INSTAGRAM', 'fab fa-instagram', 'Instagram'],
  ['MARKETPLACE', 'fas fa-shopping-bag', 'Pazaryeri'],
  ['OTHER', 'fas fa-ellipsis-h', 'Diğer'],
];
const money = (value) => Number(value || 0).toLocaleString('tr-TR', { style: 'currency', currency: 'TRY' });

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
    shippingCost: 0,
    note: '',
    city: '',
    district: '',
    addressLine: '',
    items: [{ ...emptyItem }],
  });
  const [products, setProducts] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [customerSearch, setCustomerSearch] = useState('');
  const [search, setSearch] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    const timer = setTimeout(
      () =>
        axios
          .get('/api/products', {
            params: { page: 0, size: 30, search: search || undefined, productType: 'SIMPLE' },
          })
          .then((r) => setProducts(r.data?.content || r.data || []))
          .catch(() => setProducts([])),
      250
    );
    return () => clearTimeout(timer);
  }, [search]);
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
  const selectProduct = (index, productId) => {
    const product = products.find((p) => String(p.id) === String(productId));
    setItem(
      index,
      product
        ? {
            productId,
            productName: product.name,
            sku: product.sku,
            listPrice: Number(product.salePrice || product.price || 0),
          }
        : { ...emptyItem }
    );
  };
  const subtotal = useMemo(
    () =>
      form.items.reduce(
        (sum, item) =>
          sum +
          (item.unitPrice === '' ? item.listPrice : Number(item.unitPrice || 0)) * Number(item.quantity || 0),
        0
      ),
    [form.items]
  );
  const total = subtotal + Number(form.shippingCost || 0);
  const itemCount = form.items.reduce((sum, item) => sum + Number(item.quantity || 0), 0);
  const customerComplete = Boolean(form.firstName && form.lastName && form.phone);
  const addressComplete = Boolean(form.addressLine && form.city);
  const productsComplete =
    form.items.length > 0 && form.items.every((item) => item.productId && Number(item.quantity) > 0);
  const paymentComplete = form.paymentState !== 'SCHEDULED' || Boolean(form.paymentDueAt);
  const invalid = (value) => touched && !value;

  const submit = async () => {
    setTouched(true);
    setError('');
    if (!customerComplete || !form.addressLine || !productsComplete || !paymentComplete) {
      setError('Kırmızı işaretli zorunlu alanları tamamlayın.');
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
        shippingCost: Number(form.shippingCost || 0),
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
      onCreated(response.data);
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
                  description="Ürünleri, adetleri ve gerekiyorsa siparişe özel fiyatları belirleyin."
                  complete={productsComplete}
                >
                  <div className="input-group mb-3 manual-order-search">
                    <span className="input-group-text">
                      <i className="fas fa-search" />
                    </span>
                    <input
                      className="form-control"
                      placeholder="Ürün adı veya SKU ile listeyi filtreleyin"
                      value={search}
                      onChange={(e) => setSearch(e.target.value)}
                    />
                  </div>
                  <div className="d-flex flex-column gap-3">
                    {form.items.map((item, index) => (
                      <div className="manual-order-item" key={index}>
                        <div className="manual-order-item__index">{index + 1}</div>
                        <div className="row g-2 flex-grow-1 align-items-end">
                          <div className="col-md-6">
                            <label className="form-label">
                              Ürün <span className="text-danger">*</span>
                            </label>
                            <select
                              className={`form-select ${touched && !item.productId ? 'is-invalid' : ''}`}
                              value={item.productId}
                              onChange={(e) => selectProduct(index, e.target.value)}
                            >
                              <option value="">Ürün seçin</option>
                              {item.productId &&
                                !products.some((p) => String(p.id) === String(item.productId)) && (
                                  <option value={item.productId}>
                                    {item.productName} — {item.sku}
                                  </option>
                                )}
                              {products.map((p) => (
                                <option value={p.id} key={p.id}>
                                  {p.name} — {p.sku} · Stok {p.totalQuantity || 0}
                                </option>
                              ))}
                            </select>
                          </div>
                          <div className="col-5 col-md-2">
                            <label className="form-label">Adet</label>
                            <input
                              type="number"
                              min="1"
                              className="form-control"
                              value={item.quantity}
                              onChange={(e) => setItem(index, { quantity: e.target.value })}
                            />
                          </div>
                          <div className="col-7 col-md-3">
                            <label className="form-label">Birim fiyat</label>
                            <div className="input-group">
                              <span className="input-group-text">₺</span>
                              <input
                                type="number"
                                min="0"
                                step=".01"
                                className="form-control"
                                placeholder={item.productId ? String(item.listPrice) : 'Liste fiyatı'}
                                value={item.unitPrice}
                                onChange={(e) => setItem(index, { unitPrice: e.target.value })}
                              />
                            </div>
                          </div>
                          <div className="col-md-1">
                            <button
                              type="button"
                              className="btn btn-outline-danger w-100"
                              disabled={form.items.length === 1}
                              onClick={() =>
                                set(
                                  'items',
                                  form.items.filter((_, i) => i !== index)
                                )
                              }
                              aria-label={`${index + 1}. ürünü kaldır`}
                            >
                              <i className="fas fa-trash-alt" />
                            </button>
                          </div>
                          {item.productId && (
                            <div className="col-12 small text-muted">
                              Liste fiyatı: <strong>{money(item.listPrice)}</strong> · Satır toplamı:{' '}
                              <strong>
                                {money(
                                  (item.unitPrice === '' ? item.listPrice : item.unitPrice) * item.quantity
                                )}
                              </strong>
                            </div>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                  <button
                    type="button"
                    className="btn btn-outline-primary mt-3"
                    onClick={() => set('items', [...form.items, { ...emptyItem }])}
                  >
                    <i className="fas fa-plus me-2" />
                    Başka ürün ekle
                  </button>
                </Section>
                <Section
                  number="4"
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
                            onChange={(e) => set('paymentDueAt', e.target.value)}
                          />
                        </div>
                        <div className="col-md-6">
                          <label className="form-label">Admin hatırlatması</label>
                          <input
                            type="datetime-local"
                            className="form-control"
                            value={form.reminderAt}
                            onChange={(e) => set('reminderAt', e.target.value)}
                          />
                        </div>
                      </>
                    )}
                    <div className="col-md-4">
                      <label className="form-label">Kargo ücreti</label>
                      <div className="input-group">
                        <span className="input-group-text">₺</span>
                        <input
                          type="number"
                          min="0"
                          step=".01"
                          className="form-control"
                          value={form.shippingCost}
                          onChange={(e) => set('shippingCost', e.target.value)}
                        />
                      </div>
                    </div>
                    <div className="col-md-8">
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
                    {form.items.filter((item) => item.productId).length ? (
                      form.items
                        .filter((item) => item.productId)
                        .map((item, index) => (
                          <div
                            className="d-flex justify-content-between gap-3"
                            key={`${item.productId}-${index}`}
                          >
                            <span className="text-truncate">
                              {item.quantity} × {item.productName}
                            </span>
                            <strong className="text-nowrap">
                              {money(
                                (item.unitPrice === '' ? item.listPrice : item.unitPrice) * item.quantity
                              )}
                            </strong>
                          </div>
                        ))
                    ) : (
                      <div className="text-center text-muted py-3">
                        <i className="fas fa-box-open d-block fs-3 mb-2 opacity-50" />
                        Henüz ürün seçilmedi
                      </div>
                    )}
                  </div>
                  <div className="manual-order-summary__totals">
                    <div>
                      <span>Ara toplam</span>
                      <strong>{money(subtotal)}</strong>
                    </div>
                    <div>
                      <span>Kargo</span>
                      <strong>{Number(form.shippingCost) ? money(form.shippingCost) : 'Ücretsiz'}</strong>
                    </div>
                    <div className="manual-order-summary__grand">
                      <span>Genel toplam</span>
                      <strong>{money(total)}</strong>
                    </div>
                  </div>
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
