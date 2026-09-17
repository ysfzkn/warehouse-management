import React, { useCallback, useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import { PastCustomerPicker } from './TransferPeoplePicker';
import WarehouseStockPicker from './WarehouseStockPicker';
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

/** Yarın sabah 10:00 — planlı teslimatta en sık girilen değer, form onunla açılıyor. */
const defaultScheduledAt = () => {
  const date = new Date();
  date.setDate(date.getDate() + 1);
  date.setHours(10, 0, 0, 0);
  return toLocalInput(date);
};

const formatScheduled = (value) => {
  if (!value) return '';
  try {
    return new Date(value).toLocaleString('tr-TR', {
      weekday: 'long',
      day: '2-digit',
      month: 'long',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return value;
  }
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
  // Planlı teslimat, kâğıdın kesildiği an ile malın gideceği günü ayırmak için var.
  // Boş bırakılırsa (mod = now) akış eskisi gibi: mal çıkar, stok o an düşer.
  deliveryMode: 'now',
  scheduledDeliveryAt: defaultScheduledAt(),
  notes: '',
});

export default function ServiceHandoverModal({ onClose }) {
  const [form, setForm] = useState(emptyForm);
  const [warehouses, setWarehouses] = useState([]);
  const [stocks, setStocks] = useState([]);
  const [stockLoading, setStockLoading] = useState(false);
  const [items, setItems] = useState([]);
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

  /** Seçici doğrulamayı kendi yapıyor; burada sadece sepete katılıyor. */
  const addItem = (stock, quantity) => {
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
  };

  const removeItem = (index) => setItems((prev) => prev.filter((_, i) => i !== index));

  const totalQuantity = items.reduce((sum, item) => sum + item.quantity, 0);

  const scheduled = form.deliveryMode === 'scheduled';

  const validate = () => {
    const errors = {};
    if (!form.sourceWarehouseId) errors.sourceWarehouseId = 'Çıkış deposu seçin.';
    if (scheduled) {
      if (!form.scheduledDeliveryAt) {
        errors.scheduledDeliveryAt = 'Planlanan teslim tarihini girin.';
      } else if (new Date(form.scheduledDeliveryAt).getTime() <= Date.now()) {
        // Sunucu da aynı kuralı uyguluyor; burada erken durmak, kullanıcıyı ürün listesi
        // dolu bir formu gönderip hata almaktan kurtarıyor.
        errors.scheduledDeliveryAt = 'Teslim tarihi gelecekte olmalı.';
      }
    }
    // Teslim eden ve teslim alan bilerek zorunlu değil: kâğıt tezgâhta, kurye beklerken
    // basılıyor ve iki imza bloğu çoğu zaman elle dolduruluyor. Boş bırakılırsa makbuz
    // o alanlara yazmak için çizgi basıyor.
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
        // Boş alanlar null gidiyor, boş metin değil: makbuz "değer yok" ile "boş metin"i
        // ayırt ediyor — ilkinde imza satırı çizgili basılıyor, ikincisi veriymiş gibi
        // kaydediliyor ve yeniden basımda o alanı boş ama "dolu" gösteriyordu.
        handoverToName: form.handoverToName.trim() || null,
        handoverToPhone: form.handoverToPhone ? formatPhoneForSubmit(form.handoverToPhone) : null,
        handedOverBy: form.handedOverBy.trim() || null,
        customerFullName: form.customerFullName.trim(),
        customerPhone: formatPhoneForSubmit(form.customerPhone),
        customerAddress: form.customerAddress.trim(),
        handedOverAt: form.handedOverAt ? `${form.handedOverAt}:00` : null,
        // Alanın dolu olması akışı değiştiriyor: stok düşmez, rezerve edilir ve teslim
        // gününde düşer. Mod "şimdi" iken null gitmeli — boş metin gönderilse sunucu
        // planlı çıkış kurardı.
        scheduledDeliveryAt: scheduled && form.scheduledDeliveryAt ? `${form.scheduledDeliveryAt}:00` : null,
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
    const plannedAt = result.receipt?.scheduledDeliveryAt || result.transfer?.scheduledDeliveryAt;
    return (
      <div
        className="modal show d-block"
        tabIndex="-1"
        style={{ backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 2000 }}
      >
        <div className="modal-dialog modal-dialog-centered">
          <div className="modal-content border-0 rounded-4 shadow">
            <div
              className={`modal-header text-white rounded-top-4 ${plannedAt ? '' : 'bg-success'}`}
              style={
                plannedAt ? { background: 'linear-gradient(135deg, #b45309 0%, #92400e 100%)' } : undefined
              }
            >
              <h5 className="modal-title">
                <i className={`fas ${plannedAt ? 'fa-calendar-check' : 'fa-circle-check'} me-2`}></i>
                {plannedAt ? 'Teslimat Planlandı' : 'Depo Çıkışı Kaydedildi'}
              </h5>
              <button type="button" className="btn-close btn-close-white" onClick={onClose}></button>
            </div>
            <div className="modal-body">
              <div className="text-center mb-3">
                <div className="text-muted small text-uppercase">Makbuz No</div>
                <div className="fs-4 fw-bold">{result.receipt?.receiptNo}</div>
              </div>

              {/* Planlı çıkışta ekranın söylemesi gereken tek kritik şey stoğun HENÜZ
                  düşmediği. Aksi hâlde kullanıcı işi bitmiş sayar, teslim günü kimse
                  teslimatı kapatmaz ve mal hem rafta hem rezervede asılı kalır. */}
              {plannedAt ? (
                <>
                  <div className="border rounded-3 p-3 mb-3 text-center bg-warning-subtle border-warning-subtle">
                    <div className="text-uppercase small text-warning-emphasis fw-semibold">
                      Planlanan Teslim Tarihi
                    </div>
                    <div className="fw-bold mt-1">{formatScheduled(plannedAt)}</div>
                  </div>
                  <div className="alert alert-warning py-2 px-3 small mb-2">
                    <i className="fas fa-boxes-stacked me-1"></i>
                    Ürünler <strong>rezerve edildi, stoktan düşmedi</strong>. Teslimat yapıldığında sevkiyat
                    detayından <strong>Teslimatı Tamamla</strong> ile kapatın; stok o adımda düşer.
                  </div>
                  <div className="alert alert-info py-2 px-3 small">
                    <i className="fas fa-bell me-1"></i>
                    Teslimden <strong>1 gün önce</strong> ve <strong>teslim günü</strong> bildirim ve e-posta
                    hatırlatması gönderilir.
                  </div>
                </>
              ) : (
                <div className="alert alert-info py-2 px-3 small">
                  <i className="fas fa-info-circle me-1"></i>
                  Ürünler stoktan düşüldü. Taşıyıcı belli olduğunda sevkiyat detayından{' '}
                  <strong>Taşıyıcı Bilgisi Gir</strong> ile kaydedin —{' '}
                  <strong>yeni bir sevkiyat oluşturmayın</strong>, stok ikinci kez düşer.
                </div>
              )}
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
            style={{
              background: scheduled
                ? 'linear-gradient(135deg, #b45309 0%, #92400e 100%)'
                : 'linear-gradient(135deg, #0f766e 0%, #115e59 100%)',
            }}
          >
            <div>
              <h5 className="modal-title mb-0">
                <i className="fas fa-file-export me-2"></i>
                Depo Çıkış Makbuzu
              </h5>
              <small className="opacity-75">
                {scheduled
                  ? 'Makbuz bugün kesiliyor, mal planlanan tarihte teslim edilecek'
                  : 'Mal servise teslim ediliyor, taşıyıcı henüz belli değil'}
              </small>
            </div>
            <button type="button" className="btn-close btn-close-white" onClick={onClose}></button>
          </div>

          <div className="modal-body">
            {/* ── Teslimat zamanı ──
                Formun en üstünde, çünkü seçilen mod alttaki her şeyin anlamını değiştiriyor:
                stok ne zaman düşecek, kâğıtta hangi tarih yazacak, hatırlatma gidecek mi.
                Aşağıya gömülü bir onay kutusu olsaydı, kullanıcı formu doldurduktan sonra
                fark edip baştan düşünmek zorunda kalırdı. */}
            <div className="row g-2 mb-3">
              {[
                {
                  key: 'now',
                  icon: 'fa-dolly',
                  title: 'Şimdi teslim ediliyor',
                  desc: 'Mal şu anda çıkıyor, stok hemen düşer',
                  accent: '#0f766e',
                },
                {
                  key: 'scheduled',
                  icon: 'fa-calendar-day',
                  title: 'İleri tarihli teslimat',
                  desc: 'Makbuz bugün, teslim ileri bir tarihte',
                  accent: '#b45309',
                },
              ].map((option) => {
                const active = form.deliveryMode === option.key;
                return (
                  <div className="col-sm-6" key={option.key}>
                    <button
                      type="button"
                      className={`btn w-100 h-100 text-start border rounded-3 p-3 ${
                        active ? 'shadow-sm' : 'bg-white'
                      }`}
                      style={{
                        borderColor: active ? option.accent : '#dee2e6',
                        borderWidth: active ? 2 : 1,
                        backgroundColor: active ? `${option.accent}12` : undefined,
                      }}
                      aria-pressed={active}
                      onClick={() => set('deliveryMode', option.key)}
                    >
                      <div
                        className="fw-semibold d-flex align-items-center gap-2"
                        style={{ color: active ? option.accent : undefined }}
                      >
                        <i className={`fas ${option.icon}`}></i>
                        {option.title}
                        {active && <i className="fas fa-circle-check ms-auto"></i>}
                      </div>
                      <div className="small text-muted mt-1">{option.desc}</div>
                    </button>
                  </div>
                );
              })}
            </div>

            {scheduled ? (
              <div className="alert alert-warning py-2 px-3 small d-flex align-items-start gap-2">
                <i className="fas fa-calendar-check mt-1"></i>
                <span>
                  Ürünler <strong>rezerve edilir, stoktan düşmez</strong>. Düşüm teslimat onaylandığında olur.
                  Teslimden 1 gün önce ve teslim günü bildirim ve e-posta hatırlatması gönderilir.
                </span>
              </div>
            ) : (
              <div className="alert alert-warning py-2 px-3 small d-flex align-items-start gap-2">
                <i className="fas fa-triangle-exclamation mt-1"></i>
                <span>
                  Bu işlem ürünleri <strong>hemen stoktan düşer</strong> ve tek nüshalık bir makbuz basar.
                  Şoför ve plaka sonradan aynı kayda işlenir; ikinci bir sevkiyat açmayın.
                </span>
              </div>
            )}

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
                    {/* Planlı çıkışta bu alan malın çıktığı an değil, kâğıdın kesildiği an.
                        Etiketin değişmesi şart: "Çıkış Tarihi" yazarken bugünü göstermesi,
                        mal hâlâ depodayken çıkmış gibi okunuyordu. */}
                    <label className="form-label small mb-1">
                      {scheduled ? 'Belge Tarihi' : 'Çıkış Tarihi'}
                    </label>
                    <input
                      type="datetime-local"
                      className="form-control"
                      max={toLocalInput(new Date())}
                      value={form.handedOverAt}
                      onChange={(e) => set('handedOverAt', e.target.value)}
                    />
                  </div>
                  {scheduled && (
                    <div className="col-12">
                      <label className="form-label small mb-1">
                        Planlanan Teslim Tarihi <span className="text-danger">*</span>
                      </label>
                      <input
                        type="datetime-local"
                        className={`form-control ${invalid('scheduledDeliveryAt')}`}
                        min={toLocalInput(new Date())}
                        value={form.scheduledDeliveryAt}
                        onChange={(e) => set('scheduledDeliveryAt', e.target.value)}
                      />
                      <div className="invalid-feedback">{fieldErrors.scheduledDeliveryAt}</div>
                      {form.scheduledDeliveryAt && !fieldErrors.scheduledDeliveryAt && (
                        <div className="form-text">
                          <i className="fas fa-bell me-1"></i>
                          {formatScheduled(form.scheduledDeliveryAt)} — makbuza bu tarih basılır.
                        </div>
                      )}
                    </div>
                  )}
                </div>

                {/* ── Taraflar ── */}
                <h6 className="text-uppercase text-muted small fw-bold mt-4 mb-2">
                  Teslim Eden / Teslim Alan
                </h6>
                <div className="small text-muted mb-2">
                  <i className="fas fa-pen me-1"></i>
                  Boş bırakırsanız makbuzda imza alanları çizgili olarak basılır, elle doldurulabilir.
                </div>
                <div className="row g-3">
                  <div className="col-md-6">
                    <label className="form-label small mb-1">
                      Teslim Eden (depo görevlisi)
                      <span className="text-muted fw-normal"> — opsiyonel</span>
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
                      Teslim Alan Servis / Kişi
                      <span className="text-muted fw-normal"> — opsiyonel</span>
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
                <>
                  <WarehouseStockPicker
                    stocks={stocks}
                    loading={stockLoading}
                    disabled={!form.sourceWarehouseId}
                    availableFor={(stock) => availableFor(stock.id)}
                    onAdd={addItem}
                  />

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
              className={`btn ${scheduled ? 'btn-warning' : 'btn-success'}`}
              disabled={loading || loadingData || items.length === 0}
            >
              <i
                className={`fas ${
                  loading ? 'fa-spinner fa-spin' : scheduled ? 'fa-calendar-check' : 'fa-file-export'
                } me-2`}
              ></i>
              {scheduled ? 'Teslimatı Planla ve Makbuz Bas' : 'Çıkışı Kaydet ve Makbuz Bas'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
