import React, { useCallback, useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import { PastCustomerPicker } from './TransferPeoplePicker';
import {
  extractPhoneDigits,
  formatPhoneForSubmit,
  formatPhoneInputValue,
  isPhoneComplete,
  PHONE_PLACEHOLDER,
} from '../utils/phone';
import { toTitleCaseTr } from '../utils/name';

/**
 * Depo çıkış makbuzu — mal servise teslim edilirken, taşıyıcı henüz belli değilken.
 *
 * Ayrı bir modal, çünkü sorduğu sorular transfer formununkilerin tersi: orada şoför,
 * TC, telefon ve plaka zorunlu; burada onların hiçbiri sorulmuyor, malı devralan servis
 * ile devreden görevli zorunlu. İki formu tek bileşende bir bayrakla toplamak, transfer
 * formunu şoför bilgisi olmadan kayıt açmaya bir adım uzaklıkta bırakırdı.
 *
 * Stok kaydı bu adımda düşer — mal fiziken depodan çıkıyor. Taşıyıcı sonradan sevkiyat
 * kaydına yazılır, ikinci bir çıkış oluşturulmaz.
 *
 * Kayıttan sonra modal kapanmaz: makbuz numarası ile yazdırma/PDF düğmeleri burada
 * kalır, çünkü kâğıt kurye daha tezgâhın başındayken imzalanıyor. Listeleri tazeleme işi
 * bu yüzden çağırana ve kapanış anına bırakıldı.
 */

const toLocalInput = (date) => {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

const emptyForm = () => ({
  sourceWarehouseId: '',
  handoverToName: '',
  handoverToPhone: '',
  handedOverBy: '',
  customerFullName: '',
  customerPhone: '',
  customerAddress: '',
  handedOverAt: toLocalInput(new Date()),
  notes: '',
});

export default function ServiceHandoverModal({ onClose }) {
  const [form, setForm] = useState(emptyForm);
  const [warehouses, setWarehouses] = useState([]);
  const [stocks, setStocks] = useState([]);
  const [stockLoading, setStockLoading] = useState(false);
  const [items, setItems] = useState([]);
  const [itemDraft, setItemDraft] = useState({ stockId: '', quantity: '' });
  const [loading, setLoading] = useState(false);
  const [loadingData, setLoadingData] = useState(true);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [result, setResult] = useState(null);
  const [printBusy, setPrintBusy] = useState('');

  const set = (field, value) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    setFieldErrors((prev) => (prev[field] ? { ...prev, [field]: undefined } : prev));
  };

  useEffect(() => {
    (async () => {
      try {
        const res = await axios.get('/api/warehouses');
        const list = Array.isArray(res.data) ? res.data : [];
        setWarehouses(list);
        if (list.length === 1) setForm((prev) => ({ ...prev, sourceWarehouseId: list[0].id }));
      } catch {
        setError('Depolar yüklenemedi.');
      } finally {
        setLoadingData(false);
      }
    })();
  }, []);

  const loadStocks = useCallback(async (warehouseId) => {
    if (!warehouseId) {
      setStocks([]);
      return;
    }
    setStockLoading(true);
    try {
      const res = await axios.get(`/api/stocks/warehouse/${warehouseId}`);
      setStocks(Array.isArray(res.data) ? res.data : []);
    } catch {
      setStocks([]);
    } finally {
      setStockLoading(false);
    }
  }, []);

  useEffect(() => {
    loadStocks(form.sourceWarehouseId);
    // Changing the warehouse invalidates every line: the same product in another depot is
    // a different stock row with a different quantity on hand.
    setItems([]);
    setItemDraft({ stockId: '', quantity: '' });
  }, [form.sourceWarehouseId, loadStocks]);

  const stockById = useMemo(() => {
    const map = new Map();
    stocks.forEach((s) => map.set(String(s.id), s));
    return map;
  }, [stocks]);

  /** What is still on the shelf for a row, after the lines already added to this receipt. */
  const availableFor = useCallback(
    (stockId, excludeIndex = -1) => {
      const stock = stockById.get(String(stockId));
      if (!stock) return 0;
      const onHand = (stock.quantity || 0) - (stock.reservedQuantity || 0);
      const alreadyAdded = items.reduce(
        (sum, item, index) =>
          index !== excludeIndex && String(item.stockId) === String(stockId) ? sum + item.quantity : sum,
        0
      );
      return Math.max(0, onHand - alreadyAdded);
    },
    [items, stockById]
  );

  const addItem = () => {
    const stock = stockById.get(String(itemDraft.stockId));
    const quantity = parseInt(itemDraft.quantity, 10);
    if (!stock) {
      setError('Ürün seçmelisiniz.');
      return;
    }
    if (!quantity || quantity < 1) {
      setError('Adet en az 1 olmalıdır.');
      return;
    }
    const free = availableFor(itemDraft.stockId);
    if (quantity > free) {
      setError(`Bu üründen çıkılabilecek en fazla adet: ${free}.`);
      return;
    }
    setError('');
    setItems((prev) => {
      const existing = prev.findIndex((i) => String(i.stockId) === String(stock.id));
      if (existing >= 0) {
        const next = [...prev];
        next[existing] = { ...next[existing], quantity: next[existing].quantity + quantity };
        return next;
      }
      return [
        ...prev,
        {
          stockId: stock.id,
          productId: stock.product?.id,
          name: stock.product?.name || '-',
          sku: stock.product?.sku || '',
          quantity,
        },
      ];
    });
    setItemDraft({ stockId: '', quantity: '' });
  };

  const removeItem = (index) => setItems((prev) => prev.filter((_, i) => i !== index));

  const totalQuantity = items.reduce((sum, item) => sum + item.quantity, 0);

  const validate = () => {
    const errors = {};
    if (!form.sourceWarehouseId) errors.sourceWarehouseId = 'Çıkış deposu seçin.';
    if (!form.handoverToName.trim()) errors.handoverToName = 'Teslim alan servis/kişi zorunlu.';
    if (!form.handedOverBy.trim()) errors.handedOverBy = 'Teslim eden kişi zorunlu.';
    if (!form.customerFullName.trim()) errors.customerFullName = 'Müşteri adı zorunlu.';
    if (!isPhoneComplete(form.customerPhone)) errors.customerPhone = 'Geçerli bir telefon girin.';
    if (!form.customerAddress.trim()) errors.customerAddress = 'Müşteri adresi zorunlu.';
    if (form.handoverToPhone && !isPhoneComplete(form.handoverToPhone)) {
      errors.handoverToPhone = 'Telefonu tam girin ya da boş bırakın.';
    }
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setError('Eksik alanlar var, lütfen kontrol edin.');
      return false;
    }
    if (items.length === 0) {
      setError('En az bir ürün eklemelisiniz.');
      return false;
    }
    return true;
  };

  const submit = async (event) => {
    event.preventDefault();
    if (loading || !validate()) return;
    setLoading(true);
    setError('');
    try {
      const res = await axios.post('/api/stock-transfers/service-handover', {
        sourceWarehouseId: form.sourceWarehouseId,
        handoverToName: form.handoverToName.trim(),
        handoverToPhone: form.handoverToPhone ? formatPhoneForSubmit(form.handoverToPhone) : null,
        handedOverBy: form.handedOverBy.trim(),
        customerFullName: form.customerFullName.trim(),
        customerPhone: formatPhoneForSubmit(form.customerPhone),
        customerAddress: form.customerAddress.trim(),
        handedOverAt: form.handedOverAt ? `${form.handedOverAt}:00` : null,
        notes: form.notes.trim() || null,
        items: items.map((item) => ({
          stockId: item.stockId,
          productId: item.productId,
          quantity: item.quantity,
        })),
      });
      setResult(res.data);
    } catch (e) {
      setError(e?.response?.data?.message || 'Depo çıkışı kaydedilemedi.');
    } finally {
      setLoading(false);
    }
  };

  /**
   * Opens the printable page. The window is opened before the request on purpose — a
   * window.open after an await counts as an unrequested popup and gets blocked.
   */
  const print = async () => {
    const transferId = result?.transfer?.id;
    if (!transferId) return;
    const win = window.open('', '_blank');
    if (!win) {
      setError('Yazdırma penceresi açılamadı. Açılır pencere engelini kontrol edin.');
      return;
    }
    win.document.write(
      '<!doctype html><html lang="tr"><head><meta charset="utf-8"><title>Makbuz hazırlanıyor…</title></head><body style="font-family:sans-serif;padding:24px">Makbuz hazırlanıyor…</body></html>'
    );
    setPrintBusy('print');
    try {
      const res = await axios.get(`/api/stock-transfers/${transferId}/receipt/print`, {
        responseType: 'text',
      });
      win.document.open();
      win.document.write(res.data);
      win.document.close();
      setTimeout(() => {
        try {
          win.focus();
          win.print();
        } catch {
          /* the page carries its own Yazdır button */
        }
      }, 500);
    } catch (e) {
      win.close();
      setError(e?.response?.data?.message || 'Makbuz açılamadı.');
    } finally {
      setPrintBusy('');
    }
  };

  const download = async () => {
    const transferId = result?.transfer?.id;
    if (!transferId) return;
    setPrintBusy('pdf');
    try {
      const res = await axios.get(`/api/stock-transfers/${transferId}/receipt/pdf`, {
        responseType: 'blob',
      });
      const url = window.URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }));
      const link = document.createElement('a');
      link.href = url;
      link.download = `depo-cikis-${result?.receipt?.receiptNo || transferId}.pdf`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (e) {
      setError(e?.response?.data?.message || 'PDF indirilemedi.');
    } finally {
      setPrintBusy('');
    }
  };

  const invalid = (field) => (fieldErrors[field] ? 'is-invalid' : '');

  // ── Kayıt tamamlandı ekranı ────────────────────────────────────────────────
  if (result) {
    return (
      <div
        className="modal show d-block"
        tabIndex="-1"
        style={{ backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 2000 }}
      >
        <div className="modal-dialog modal-dialog-centered">
          <div className="modal-content border-0 rounded-4 shadow">
            <div className="modal-header bg-success text-white rounded-top-4">
              <h5 className="modal-title">
                <i className="fas fa-circle-check me-2"></i>
                Depo Çıkışı Kaydedildi
              </h5>
              <button type="button" className="btn-close btn-close-white" onClick={onClose}></button>
            </div>
            <div className="modal-body">
              <div className="text-center mb-3">
                <div className="text-muted small text-uppercase">Makbuz No</div>
                <div className="fs-4 fw-bold">{result.receipt?.receiptNo}</div>
              </div>
              <div className="alert alert-info py-2 px-3 small">
                <i className="fas fa-info-circle me-1"></i>
                Ürünler stoktan düşüldü. Taşıyıcı belli olduğunda sevkiyat detayından{' '}
                <strong>Taşıyıcı Bilgisi Gir</strong> ile kaydedin —{' '}
                <strong>yeni bir sevkiyat oluşturmayın</strong>, stok ikinci kez düşer.
              </div>
              {error && <div className="alert alert-danger py-2 px-3 small">{error}</div>}
              <div className="d-grid gap-2">
                <button
                  type="button"
                  className="btn btn-primary"
                  disabled={Boolean(printBusy)}
                  onClick={print}
                >
                  <i className={`fas ${printBusy === 'print' ? 'fa-spinner fa-spin' : 'fa-print'} me-2`}></i>
                  Makbuzu Yazdır
                </button>
                <button
                  type="button"
                  className="btn btn-outline-primary"
                  disabled={Boolean(printBusy)}
                  onClick={download}
                >
                  <i className={`fas ${printBusy === 'pdf' ? 'fa-spinner fa-spin' : 'fa-file-pdf'} me-2`}></i>
                  PDF İndir
                </button>
              </div>
            </div>
            <div className="modal-footer">
              <button type="button" className="btn btn-light" onClick={onClose}>
                Kapat
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // ── Form ───────────────────────────────────────────────────────────────────
  return (
    <div
      className="modal show d-block"
      tabIndex="-1"
      style={{ backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 2000 }}
    >
      <div className="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable">
        <form className="modal-content border-0 rounded-4 shadow" onSubmit={submit}>
          <div
            className="modal-header text-white rounded-top-4"
            style={{ background: 'linear-gradient(135deg, #0f766e 0%, #115e59 100%)' }}
          >
            <div>
              <h5 className="modal-title mb-0">
                <i className="fas fa-file-export me-2"></i>
                Depo Çıkış Makbuzu
              </h5>
              <small className="opacity-75">Mal servise teslim ediliyor, taşıyıcı henüz belli değil</small>
            </div>
            <button type="button" className="btn-close btn-close-white" onClick={onClose}></button>
          </div>

          <div className="modal-body">
            <div className="alert alert-warning py-2 px-3 small d-flex align-items-start gap-2">
              <i className="fas fa-triangle-exclamation mt-1"></i>
              <span>
                Bu işlem ürünleri <strong>hemen stoktan düşer</strong> ve tek nüshalık bir makbuz basar. Şoför
                ve plaka sonradan aynı kayda işlenir; ikinci bir sevkiyat açmayın.
              </span>
            </div>

            {error && (
              <div className="alert alert-danger py-2 px-3 small">
                <i className="fas fa-circle-exclamation me-1"></i>
                {error}
              </div>
            )}

            {loadingData ? (
              <div className="text-center py-4 text-muted">
                <span className="spinner-border spinner-border-sm me-2"></span>
                Yükleniyor…
              </div>
            ) : (
              <>
                {/* ── Çıkış ── */}
                <h6 className="text-uppercase text-muted small fw-bold mt-1 mb-2">Çıkış</h6>
                <div className="row g-3">
                  <div className="col-md-6">
                    <label className="form-label small mb-1">
                      Çıkış Deposu <span className="text-danger">*</span>
                    </label>
                    <select
                      className={`form-select ${invalid('sourceWarehouseId')}`}
                      value={form.sourceWarehouseId}
                      onChange={(e) => set('sourceWarehouseId', e.target.value)}
                    >
                      <option value="">Seçiniz…</option>
                      {warehouses.map((w) => (
                        <option key={w.id} value={w.id}>
                          {w.name}
                          {w.location ? ` — ${w.location}` : ''}
                        </option>
                      ))}
                    </select>
                    <div className="invalid-feedback">{fieldErrors.sourceWarehouseId}</div>
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small mb-1">Çıkış Tarihi</label>
                    <input
                      type="datetime-local"
                      className="form-control"
                      max={toLocalInput(new Date())}
                      value={form.handedOverAt}
                      onChange={(e) => set('handedOverAt', e.target.value)}
                    />
                  </div>
                </div>

                {/* ── Taraflar ── */}
                <h6 className="text-uppercase text-muted small fw-bold mt-4 mb-2">
                  Teslim Eden / Teslim Alan
                </h6>
                <div className="row g-3">
                  <div className="col-md-6">
                    <label className="form-label small mb-1">
                      Teslim Eden (depo görevlisi) <span className="text-danger">*</span>
                    </label>
                    <input
                      type="text"
                      className={`form-control ${invalid('handedOverBy')}`}
                      placeholder="Adı Soyadı"
                      value={form.handedOverBy}
                      onChange={(e) => set('handedOverBy', e.target.value)}
                      onBlur={(e) => set('handedOverBy', toTitleCaseTr(e.target.value))}
                    />
                    <div className="invalid-feedback">{fieldErrors.handedOverBy}</div>
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small mb-1">
                      Teslim Alan Servis / Kişi <span className="text-danger">*</span>
                    </label>
                    <input
                      type="text"
                      className={`form-control ${invalid('handoverToName')}`}
                      placeholder="Nakliye firması ya da görevlinin adı"
                      value={form.handoverToName}
                      onChange={(e) => set('handoverToName', e.target.value)}
                    />
                    <div className="invalid-feedback">{fieldErrors.handoverToName}</div>
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small mb-1">Teslim Alan Telefonu</label>
                    <input
                      type="tel"
                      className={`form-control ${invalid('handoverToPhone')}`}
                      placeholder={PHONE_PLACEHOLDER}
                      value={formatPhoneInputValue(form.handoverToPhone)}
                      onChange={(e) => set('handoverToPhone', extractPhoneDigits(e.target.value))}
                    />
                    <div className="invalid-feedback">{fieldErrors.handoverToPhone}</div>
                  </div>
                </div>

                {/* ── Müşteri ── */}
                <h6 className="text-uppercase text-muted small fw-bold mt-4 mb-2">Malın Gideceği Müşteri</h6>
                <div className="mb-2">
                  <PastCustomerPicker
                    onPick={(customer) =>
                      setForm((prev) => ({
                        ...prev,
                        customerFullName: customer.name || prev.customerFullName,
                        customerPhone: customer.phone
                          ? extractPhoneDigits(customer.phone)
                          : prev.customerPhone,
                        customerAddress: customer.address || prev.customerAddress,
                      }))
                    }
                  />
                </div>
                <div className="row g-3">
                  <div className="col-md-6">
                    <label className="form-label small mb-1">
                      Adı Soyadı / Ünvan <span className="text-danger">*</span>
                    </label>
                    <input
                      type="text"
                      className={`form-control ${invalid('customerFullName')}`}
                      value={form.customerFullName}
                      onChange={(e) => set('customerFullName', e.target.value)}
                    />
                    <div className="invalid-feedback">{fieldErrors.customerFullName}</div>
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small mb-1">
                      Telefon <span className="text-danger">*</span>
                    </label>
                    <input
                      type="tel"
                      className={`form-control ${invalid('customerPhone')}`}
                      placeholder={PHONE_PLACEHOLDER}
                      value={formatPhoneInputValue(form.customerPhone)}
                      onChange={(e) => set('customerPhone', extractPhoneDigits(e.target.value))}
                    />
                    <div className="invalid-feedback">{fieldErrors.customerPhone}</div>
                  </div>
                  <div className="col-12">
                    <label className="form-label small mb-1">
                      Adres <span className="text-danger">*</span>
                    </label>
                    <textarea
                      className={`form-control ${invalid('customerAddress')}`}
                      rows="2"
                      value={form.customerAddress}
                      onChange={(e) => set('customerAddress', e.target.value)}
                    />
                    <div className="invalid-feedback">{fieldErrors.customerAddress}</div>
                  </div>
                </div>

                {/* ── Ürünler ── */}
                <h6 className="text-uppercase text-muted small fw-bold mt-4 mb-2">Ürünler</h6>
                {!form.sourceWarehouseId ? (
                  <div className="text-muted small">Önce çıkış deposunu seçin.</div>
                ) : (
                  <>
                    <div className="row g-2 align-items-end">
                      <div className="col-md-7">
                        <label className="form-label small mb-1">Ürün</label>
                        <select
                          className="form-select"
                          value={itemDraft.stockId}
                          disabled={stockLoading}
                          onChange={(e) => setItemDraft((prev) => ({ ...prev, stockId: e.target.value }))}
                        >
                          <option value="">{stockLoading ? 'Stoklar yükleniyor…' : 'Ürün seçiniz…'}</option>
                          {stocks.map((s) => {
                            const free = availableFor(s.id);
                            return (
                              <option key={s.id} value={s.id} disabled={free <= 0}>
                                {s.product?.name}
                                {s.product?.sku ? ` (${s.product.sku})` : ''} — kalan {free}
                              </option>
                            );
                          })}
                        </select>
                      </div>
                      <div className="col-md-3">
                        <label className="form-label small mb-1">Adet</label>
                        <input
                          type="number"
                          min="1"
                          className="form-control"
                          value={itemDraft.quantity}
                          onChange={(e) => setItemDraft((prev) => ({ ...prev, quantity: e.target.value }))}
                        />
                      </div>
                      <div className="col-md-2 d-grid">
                        <button type="button" className="btn btn-outline-primary" onClick={addItem}>
                          <i className="fas fa-plus me-1"></i>
                          Ekle
                        </button>
                      </div>
                    </div>

                    {items.length > 0 && (
                      <div className="table-responsive mt-3">
                        <table className="table table-sm align-middle mb-0">
                          <thead className="table-light">
                            <tr>
                              <th>Ürün</th>
                              <th className="text-center" style={{ width: 90 }}>
                                Adet
                              </th>
                              <th style={{ width: 50 }}></th>
                            </tr>
                          </thead>
                          <tbody>
                            {items.map((item, index) => (
                              <tr key={item.stockId}>
                                <td>
                                  <div className="fw-semibold">{item.name}</div>
                                  {item.sku && <small className="text-muted">{item.sku}</small>}
                                </td>
                                <td className="text-center">{item.quantity}</td>
                                <td className="text-end">
                                  <button
                                    type="button"
                                    className="btn btn-sm btn-outline-danger border-0"
                                    onClick={() => removeItem(index)}
                                    aria-label="Kaldır"
                                  >
                                    <i className="fas fa-trash"></i>
                                  </button>
                                </td>
                              </tr>
                            ))}
                          </tbody>
                          <tfoot className="table-light">
                            <tr>
                              <th className="text-end">Toplam</th>
                              <th className="text-center">{totalQuantity}</th>
                              <th></th>
                            </tr>
                          </tfoot>
                        </table>
                      </div>
                    )}
                  </>
                )}

                <div className="mt-3">
                  <label className="form-label small mb-1">Açıklama / Not</label>
                  <textarea
                    className="form-control"
                    rows="2"
                    maxLength={500}
                    value={form.notes}
                    onChange={(e) => set('notes', e.target.value)}
                  />
                </div>
              </>
            )}
          </div>

          <div className="modal-footer">
            <button type="button" className="btn btn-light" onClick={onClose} disabled={loading}>
              Vazgeç
            </button>
            <button
              type="submit"
              className="btn btn-success"
              disabled={loading || loadingData || items.length === 0}
            >
              <i className={`fas ${loading ? 'fa-spinner fa-spin' : 'fa-file-export'} me-2`}></i>
              Çıkışı Kaydet ve Makbuz Bas
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
