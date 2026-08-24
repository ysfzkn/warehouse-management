import React, { useEffect, useMemo, useState } from 'react';
import axios from 'axios';

const emptyItem = { productId: '', quantity: 1, unitPrice: '', label: '' };

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

  useEffect(() => {
    const timer = setTimeout(() => {
      axios
        .get('/api/products', {
          params: { page: 0, size: 30, search: search || undefined, productType: 'SIMPLE' },
        })
        .then((r) => setProducts(r.data?.content || r.data || []))
        .catch(() => setProducts([]));
    }, 250);
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
      const addresses = response.data || [];
      const address = addresses.find((a) => a.default) || addresses[0];
      if (address)
        setForm((old) => ({
          ...old,
          addressLine: address.addressLine || '',
          district: address.district || '',
          city: address.city || '',
        }));
    } catch (_) {}
  };

  const total = useMemo(
    () =>
      form.items.reduce((sum, item) => {
        const product = products.find((p) => String(p.id) === String(item.productId));
        const price = Number(item.unitPrice || product?.salePrice || product?.price || 0);
        return sum + price * Number(item.quantity || 0);
      }, 0) + Number(form.shippingCost || 0),
    [form.items, form.shippingCost, products]
  );

  const set = (key, value) => setForm((old) => ({ ...old, [key]: value }));
  const setItem = (index, patch) =>
    setForm((old) => ({ ...old, items: old.items.map((x, i) => (i === index ? { ...x, ...patch } : x)) }));

  const submit = async () => {
    setError('');
    if (
      !form.firstName ||
      !form.lastName ||
      !form.phone ||
      !form.addressLine ||
      form.items.some((i) => !i.productId)
    ) {
      setError('Müşteri, adres ve ürün alanlarını tamamlayın.');
      return;
    }
    setSaving(true);
    try {
      const payload = {
        customerId: form.customerId || null,
        firstName: form.firstName,
        lastName: form.lastName,
        phone: form.phone,
        email: form.email || null,
        channel: form.channel,
        channelReference: form.channelReference || null,
        paymentMethod: form.paymentMethod,
        paymentState: form.paymentState,
        paymentDueAt: form.paymentDueAt || null,
        reminderAt: form.reminderAt || null,
        shippingCost: Number(form.shippingCost || 0),
        note: form.note || null,
        shippingAddress: {
          addressLine: form.addressLine,
          district: form.district,
          city: form.city,
          firstName: form.firstName,
          lastName: form.lastName,
          phone: form.phone,
        },
        items: form.items.map((i) => ({
          productId: Number(i.productId),
          quantity: Number(i.quantity),
          unitPrice: i.unitPrice === '' ? null : Number(i.unitPrice),
        })),
      };
      const response = await axios.post('/api/admin/orders/manual', payload);
      onCreated(response.data);
    } catch (e) {
      setError(e.response?.data?.message || 'Sipariş oluşturulamadı.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="modal show d-block" style={{ background: 'rgba(15,23,42,.62)', zIndex: 6000 }}>
      <div className="modal-dialog modal-xl modal-dialog-scrollable">
        <div className="modal-content border-0 shadow">
          <div className="modal-header">
            <div>
              <h5 className="modal-title fw-bold">Yeni Manuel Sipariş</h5>
              <small className="text-muted">
                Telefon, WhatsApp, mağaza veya diğer kanallardan gelen sipariş
              </small>
            </div>
            <button className="btn-close" onClick={onClose} />
          </div>
          <div className="modal-body bg-light">
            {error && <div className="alert alert-danger py-2">{error}</div>}
            <div className="row g-3">
              <div className="col-lg-6">
                <div className="card border-0 shadow-sm h-100">
                  <div className="card-body">
                    <h6 className="fw-bold mb-3">
                      <i className="fas fa-user me-2 text-primary" />
                      Müşteri ve Kanal
                    </h6>
                    <div className="position-relative mb-3">
                      <label className="form-label small">Kayıtlı müşteri ara</label>
                      <input
                        className="form-control"
                        placeholder="Ad, e-posta veya telefon"
                        value={customerSearch}
                        onChange={(e) => {
                          setCustomerSearch(e.target.value);
                          set('customerId', null);
                        }}
                      />
                      {customers.length > 0 && (
                        <div
                          className="list-group position-absolute w-100 shadow"
                          style={{ zIndex: 20, maxHeight: 220, overflowY: 'auto' }}
                        >
                          {customers.map((c) => (
                            <button
                              type="button"
                              className="list-group-item list-group-item-action"
                              key={c.id}
                              onClick={() => selectCustomer(c)}
                            >
                              <strong>
                                {c.firstName} {c.lastName}
                              </strong>
                              <div className="small text-muted">
                                {c.phone || '—'} · {c.email}
                              </div>
                            </button>
                          ))}
                        </div>
                      )}
                      {form.customerId && (
                        <small className="text-success">
                          <i className="fas fa-check me-1" />
                          Kayıtlı müşteri seçildi
                        </small>
                      )}
                    </div>
                    <div className="row g-2">
                      <div className="col-6">
                        <label className="form-label small">Ad *</label>
                        <input
                          className="form-control"
                          value={form.firstName}
                          onChange={(e) => set('firstName', e.target.value)}
                        />
                      </div>
                      <div className="col-6">
                        <label className="form-label small">Soyad *</label>
                        <input
                          className="form-control"
                          value={form.lastName}
                          onChange={(e) => set('lastName', e.target.value)}
                        />
                      </div>
                      <div className="col-6">
                        <label className="form-label small">Telefon *</label>
                        <input
                          className="form-control"
                          value={form.phone}
                          onChange={(e) => set('phone', e.target.value)}
                        />
                      </div>
                      <div className="col-6">
                        <label className="form-label small">E-posta</label>
                        <input
                          type="email"
                          className="form-control"
                          value={form.email}
                          onChange={(e) => set('email', e.target.value)}
                        />
                      </div>
                      <div className="col-6">
                        <label className="form-label small">Sipariş Kanalı *</label>
                        <select
                          className="form-select"
                          value={form.channel}
                          onChange={(e) => set('channel', e.target.value)}
                        >
                          <option value="WHATSAPP">WhatsApp</option>
                          <option value="PHONE">Telefon</option>
                          <option value="IN_STORE">Mağaza</option>
                          <option value="INSTAGRAM">Instagram</option>
                          <option value="MARKETPLACE">Pazaryeri</option>
                          <option value="OTHER">Diğer</option>
                        </select>
                      </div>
                      <div className="col-6">
                        <label className="form-label small">Referans Kodu</label>
                        <input
                          className="form-control"
                          placeholder="WA-7K3M2"
                          value={form.channelReference}
                          onChange={(e) => set('channelReference', e.target.value)}
                        />
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div className="col-lg-6">
                <div className="card border-0 shadow-sm h-100">
                  <div className="card-body">
                    <h6 className="fw-bold mb-3">
                      <i className="fas fa-map-marker-alt me-2 text-danger" />
                      Teslimat
                    </h6>
                    <label className="form-label small">Açık adres *</label>
                    <textarea
                      className="form-control mb-2"
                      rows="2"
                      value={form.addressLine}
                      onChange={(e) => set('addressLine', e.target.value)}
                    />
                    <div className="row g-2">
                      <div className="col-6">
                        <input
                          className="form-control"
                          placeholder="İlçe"
                          value={form.district}
                          onChange={(e) => set('district', e.target.value)}
                        />
                      </div>
                      <div className="col-6">
                        <input
                          className="form-control"
                          placeholder="İl"
                          value={form.city}
                          onChange={(e) => set('city', e.target.value)}
                        />
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div className="col-12">
                <div className="card border-0 shadow-sm">
                  <div className="card-body">
                    <div className="d-flex justify-content-between align-items-center mb-2">
                      <h6 className="fw-bold mb-0">
                        <i className="fas fa-box me-2 text-warning" />
                        Ürünler
                      </h6>
                      <input
                        className="form-control form-control-sm"
                        style={{ maxWidth: 260 }}
                        placeholder="Ürün / SKU ara"
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                      />
                    </div>
                    {form.items.map((item, index) => (
                      <div className="row g-2 align-items-end mb-2" key={index}>
                        <div className="col-md-7">
                          <label className="form-label small">Ürün *</label>
                          <select
                            className="form-select"
                            value={item.productId}
                            onChange={(e) => setItem(index, { productId: e.target.value })}
                          >
                            <option value="">Seçiniz</option>
                            {products.map((p) => (
                              <option value={p.id} key={p.id}>
                                {p.name} — {p.sku} (stok: {p.totalQuantity || 0})
                              </option>
                            ))}
                          </select>
                        </div>
                        <div className="col-md-2">
                          <label className="form-label small">Adet</label>
                          <input
                            type="number"
                            min="1"
                            className="form-control"
                            value={item.quantity}
                            onChange={(e) => setItem(index, { quantity: e.target.value })}
                          />
                        </div>
                        <div className="col-md-2">
                          <label className="form-label small">Özel fiyat</label>
                          <input
                            type="number"
                            min="0"
                            step=".01"
                            className="form-control"
                            placeholder="Liste fiyatı"
                            value={item.unitPrice}
                            onChange={(e) => setItem(index, { unitPrice: e.target.value })}
                          />
                        </div>
                        <div className="col-md-1">
                          <button
                            className="btn btn-outline-danger w-100"
                            disabled={form.items.length === 1}
                            onClick={() =>
                              set(
                                'items',
                                form.items.filter((_, i) => i !== index)
                              )
                            }
                          >
                            <i className="fas fa-trash" />
                          </button>
                        </div>
                      </div>
                    ))}
                    <button
                      className="btn btn-sm btn-outline-primary"
                      onClick={() => set('items', [...form.items, { ...emptyItem }])}
                    >
                      <i className="fas fa-plus me-1" />
                      Ürün Ekle
                    </button>
                  </div>
                </div>
              </div>
              <div className="col-12">
                <div className="card border-0 shadow-sm">
                  <div className="card-body">
                    <h6 className="fw-bold mb-3">
                      <i className="fas fa-wallet me-2 text-success" />
                      Ödeme Planı
                    </h6>
                    <div className="row g-2">
                      <div className="col-md-3">
                        <label className="form-label small">Yöntem</label>
                        <select
                          className="form-select"
                          value={form.paymentMethod}
                          onChange={(e) => set('paymentMethod', e.target.value)}
                        >
                          <option value="BANK_TRANSFER">Havale / EFT</option>
                          <option value="DOOR_CASH">Kapıda Nakit</option>
                          <option value="DOOR_CARD">Kapıda Kart</option>
                        </select>
                      </div>
                      <div className="col-md-3">
                        <label className="form-label small">Durum</label>
                        <select
                          className="form-select"
                          value={form.paymentState}
                          onChange={(e) => set('paymentState', e.target.value)}
                        >
                          <option value="WAITING">Ödeme bekleniyor</option>
                          <option value="SCHEDULED">Şu tarihte ödenecek</option>
                          <option value="RECEIVED">Ödeme alındı</option>
                          <option value="NOT_REQUIRED">Ödeme gerekmiyor</option>
                        </select>
                      </div>
                      <div className="col-md-3">
                        <label className="form-label small">Son ödeme tarihi</label>
                        <input
                          type="datetime-local"
                          className="form-control"
                          value={form.paymentDueAt}
                          onChange={(e) => set('paymentDueAt', e.target.value)}
                        />
                      </div>
                      <div className="col-md-3">
                        <label className="form-label small">Hatırlatma zamanı</label>
                        <input
                          type="datetime-local"
                          className="form-control"
                          value={form.reminderAt}
                          onChange={(e) => set('reminderAt', e.target.value)}
                        />
                      </div>
                      <div className="col-md-3">
                        <label className="form-label small">Kargo ücreti</label>
                        <input
                          type="number"
                          min="0"
                          step=".01"
                          className="form-control"
                          value={form.shippingCost}
                          onChange={(e) => set('shippingCost', e.target.value)}
                        />
                      </div>
                      <div className="col-md-9">
                        <label className="form-label small">Sipariş notu</label>
                        <input
                          className="form-control"
                          value={form.note}
                          onChange={(e) => set('note', e.target.value)}
                        />
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div className="modal-footer">
            <div className="me-auto">
              <small className="text-muted">Tahmini toplam</small>
              <div className="h5 mb-0 fw-bold">
                {total.toLocaleString('tr-TR', { style: 'currency', currency: 'TRY' })}
              </div>
            </div>
            <button className="btn btn-outline-secondary" onClick={onClose}>
              İptal
            </button>
            <button className="btn btn-primary px-4" disabled={saving} onClick={submit}>
              {saving ? 'Oluşturuluyor…' : 'Siparişi Oluştur'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
