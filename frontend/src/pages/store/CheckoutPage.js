import React, { useState, useEffect } from 'react';
import { useOutletContext, useNavigate } from 'react-router-dom';
import { useToast } from '../../components/store/Toast';
import CheckoutStepper from '../../components/store/CheckoutStepper';
import AddressForm from '../../components/store/AddressForm';
import IyzicoCheckoutForm from '../../components/store/IyzicoCheckoutForm';
import BankTransferInfo from '../../components/store/BankTransferInfo';
import axios from 'axios';
import { v4 as uuidv4 } from 'uuid';

const STEPS = ['address', 'shipping', 'payment', 'confirm'];

function generateIdempotencyKey() {
  try { return uuidv4(); } catch { return 'idem-' + Date.now() + '-' + Math.random().toString(36).substr(2, 12); }
}

export default function CheckoutPage() {
  const { cart } = useOutletContext();
  const navigate = useNavigate();
  const toast = useToast();
  const [step, setStep] = useState('address');
  const [address, setAddress] = useState(null);
  const [cargoCompany, setCargoCompany] = useState('YURTICI');
  const [paymentMethod, setPaymentMethod] = useState('CREDIT_CARD');
  const [contractAccepted, setContractAccepted] = useState(false);
  const [loading, setLoading] = useState(false);
  const [, setError] = useState(''); // kept for setError calls, display via toast

  // Payment state
  const [paymentPhase, setPaymentPhase] = useState(null);
  const [savedAddresses, setSavedAddresses] = useState([]);
  const [showNewAddress, setShowNewAddress] = useState(false);
  const [iyzicoHtml, setIyzicoHtml] = useState(null);
  const [posHtml, setPosHtml] = useState(null);
  const [bankDetails, setBankDetails] = useState(null);
  const [cargoProviders, setCargoProviders] = useState([]);
  const [selectedCargoProvider, setSelectedCargoProvider] = useState(null);
  const [bankDeadline, setBankDeadline] = useState(null);

  // Dynamic payment methods from API
  const [paymentMethods, setPaymentMethods] = useState([]);

  // Load saved addresses
  useEffect(() => {
    axios.get('/api/store/addresses').then(r => {
      const addrs = r.data || [];
      setSavedAddresses(addrs);
      if (addrs.length === 0) setShowNewAddress(true);
      else {
        const def = addrs.find(a => a.isDefault) || addrs[0];
        if (def) setAddress(def);
      }
    }).catch(() => setShowNewAddress(true));
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Load cargo providers
  useEffect(() => {
    axios.get('/api/store/checkout/cargo-providers').then(r => {
      const providers = r.data || [];
      setCargoProviders(providers);
      if (providers.length > 0) {
        setSelectedCargoProvider(providers[0]);
        setCargoCompany(providers[0].code);
      }
    }).catch(() => {});
  }, []);

  useEffect(() => {
    axios.get('/api/store/payment/methods').then(r => {
      const methods = r.data || [];
      setPaymentMethods(methods);
      if (methods.length > 0 && methods[0].method) setPaymentMethod(methods[0].method);
    }).catch(() => {
      // Fallback: hardcoded methods if API fails
      setPaymentMethods([
        { method: 'CREDIT_CARD', label: 'Kredi / Banka Kartı', description: '3D Secure ile güvenli ödeme', icon: 'fas fa-credit-card', active: true },
        { method: 'BANK_TRANSFER', label: 'Havale / EFT', description: 'Banka hesabımıza havale yapın', icon: 'fas fa-university', active: true },
        { method: 'DOOR_CASH', label: 'Kapıda Ödeme', description: 'Teslimat sırasında nakit veya kart ile ödeyin', icon: 'fas fa-door-open', active: true },
      ]);
    });
  }, []);

  const formatPrice = (p) => new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(p || 0);
  const currentIndex = STEPS.indexOf(step);
  const next = () => setStep(STEPS[Math.min(currentIndex + 1, STEPS.length - 1)]);
  const prev = () => setStep(STEPS[Math.max(currentIndex - 1, 0)]);

  const getAuthHeaders = () => {
    const token = localStorage.getItem('customer_token');
    return token ? { Authorization: `Bearer ${token}` } : {};
  };

  const getMethodLabel = (method) => {
    const m = paymentMethods.find(pm => pm.method === method);
    return m ? m.label : method;
  };

  const handlePlaceOrder = async () => {
    if (!contractAccepted) { toast.warning('Mesafeli satış sözleşmesini onaylayın.'); setError('Mesafeli satış sözleşmesini onaylayın.'); return; }
    setLoading(true); setError('');
    toast.info('Siparişiniz oluşturuluyor...');

    try {
      // Phase 1: Create order
      const orderRes = await axios.post('/api/store/checkout/place-order', {
        shippingAddressId: address?.id || 1,
        billingAddressId: address?.id || 1,
        cargoCompany,
        cargoProviderId: selectedCargoProvider?.id || null,
        paymentMethod,
        distanceSalesContractAccepted: true,
      }, { headers: getAuthHeaders() });

      const orderId = orderRes.data.orderId;

      // Phase 2: Initialize payment
      const idempotencyKey = generateIdempotencyKey();
      const paymentRes = await axios.post('/api/store/payment/initialize', {
        orderId,
        paymentMethod,
        installmentCount: 1,
        idempotencyKey,
      }, { headers: getAuthHeaders() });

      const result = paymentRes.data;

      if (paymentMethod === 'CREDIT_CARD' || paymentMethod === 'VIRTUAL_POS') {
        if (result.success && result.htmlContent) {
          const html = result.htmlContent;
          const isIyzico = html.includes('iyzico') || html.includes('iyziup');
          const isPayTR = html.includes('paytr.com') || html.includes('paytriframe');
          if (isIyzico) {
            setIyzicoHtml(html);
            setPaymentPhase('iyzico');
            try { await cart.fetchCart(); } catch {}
          } else if (isPayTR) {
            setPosHtml(html);
            setPaymentPhase('paytr');
            try { await cart.fetchCart(); } catch {}
          } else {
            setPosHtml(html);
            setPaymentPhase('pos_3d');
            try { await cart.fetchCart(); } catch {}
          }
        } else {
          const errMsg = result.errorMessage || 'Ödeme başlatma başarısız. Lütfen tekrar deneyin.';
          setError(errMsg); toast.error(errMsg);
        }
      } else if (paymentMethod === 'BANK_TRANSFER') {
        if (result.success && result.bankDetails) {
          setBankDetails(result.bankDetails);
          setBankDeadline(new Date(Date.now() + 48 * 3600 * 1000).toISOString());
          setPaymentPhase('bank_transfer');
          try { await cart.fetchCart(); } catch {}
        } else {
          const errMsg2 = result.errorMessage || 'Havale bilgileri oluşturulamadı.';
          setError(errMsg2); toast.error(errMsg2);
        }
      } else {
        // DOOR_CASH / DOOR_CARD
        try { await cart.fetchCart(); } catch {}
        toast.success('Siparişiniz alındı! Teslimat sırasında ödeme yapılacaktır.');
        navigate('/odeme/sonuc?success=true');
      }
    } catch (e) {
      const msg = e.response?.data?.message || 'Sipariş oluşturulamadı.';
      const errorCode = e.response?.data?.errorCode || e.response?.data?.code || '';
      // Guaranteed toast via DOM (React context may not be ready during state transitions)
      const t = document.createElement('div');
      t.style.cssText = `
        position:fixed; bottom:90px; left:50%; transform:translateX(-50%);
        z-index:9999; min-width:340px; max-width:calc(100vw - 32px);
        background:#fef2f2; color:#991b1b; border:1px solid #fecaca;
        border-radius:14px; overflow:hidden; box-shadow:0 12px 40px rgba(0,0,0,0.18);
        animation:toastSlideIn 0.35s cubic-bezier(0.21,1.02,0.73,1) forwards;
      `;
      t.innerHTML = `
        <div style="height:4px;background:#ef4444;"></div>
        <div style="display:flex;align-items:flex-start;gap:12px;padding:14px 16px;">
          <div style="width:36px;height:36px;border-radius:10px;background:#ef4444;color:#fff;display:flex;align-items:center;justify-content:center;flex-shrink:0;">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
          </div>
          <div style="flex:1;min-width:0;">
            <div style="font-size:0.7rem;font-weight:700;text-transform:uppercase;letter-spacing:0.05em;opacity:0.7;margin-bottom:2px;">Hata</div>
            <div style="font-size:0.88rem;font-weight:500;line-height:1.45;">${msg}</div>
          </div>
          <button onclick="this.closest('div[style]').remove()" style="background:none;border:none;cursor:pointer;color:#991b1b;opacity:0.4;padding:4px;">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
      `;
      document.body.appendChild(t);
      setTimeout(() => { try { t.style.animation = 'toastSlideOut 0.3s ease forwards'; setTimeout(() => { try { document.body.removeChild(t); } catch {} }, 300); } catch {} }, 6000);
      if (errorCode === 'PAY_006') {
        toast.warning('Stok yetersiz. Sepete yönlendiriliyorsunuz...');
        setTimeout(() => navigate('/sepet'), 3000);
      }
      // Refresh cart to sync badge
      try { await cart.fetchCart(); } catch {}
    } finally {
      setLoading(false);
    }
  };

  const handleIyzicoComplete = (success) => {
    navigate('/odeme/sonuc?success=' + success);
  };

  // ===== Payment sub-phases =====

  if (paymentPhase === 'iyzico') {
    return (
      <div className="container my-4" style={{ maxWidth: 640 }}>
        {/* Header */}
        <div className="text-center mb-4">
          <div style={{ width: 56, height: 56, borderRadius: '50%', background: 'linear-gradient(135deg, #eff6ff, #dbeafe)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', marginBottom: 12 }}>
            <i className="fas fa-lock" style={{ fontSize: 22, color: '#2563eb' }} />
          </div>
          <h2 className="h4 fw-bold mb-1">Güvenli Ödeme</h2>
          <p className="text-muted small mb-0">Kart bilgileriniz iyzico güvenli altyapısında işlenir, sunucumuza ulaşmaz.</p>
        </div>

        {/* Trust badges */}
        <div className="d-flex justify-content-center gap-3 mb-4">
          {[
            { icon: 'fas fa-shield-alt', label: '3D Secure', color: '#2563eb' },
            { icon: 'fas fa-lock', label: 'SSL Şifreli', color: '#059669' },
            { icon: 'fas fa-credit-card', label: 'PCI Uyumlu', color: '#7c3aed' },
          ].map(b => (
            <div key={b.label} className="d-flex align-items-center gap-1 px-3 py-1 rounded-pill" style={{ background: '#f8fafc', border: '1px solid #e2e8f0', fontSize: '0.75rem', color: b.color, fontWeight: 500 }}>
              <i className={b.icon} style={{ fontSize: 11 }} />
              <span>{b.label}</span>
            </div>
          ))}
        </div>

        {/* iyzico form */}
        <div style={{ background: '#fff', borderRadius: 16, border: '1px solid #e2e8f0', boxShadow: '0 4px 24px rgba(0,0,0,0.06)', overflow: 'hidden' }}>
          <IyzicoCheckoutForm htmlContent={iyzicoHtml} onComplete={handleIyzicoComplete} />
        </div>
      </div>
    );
  }

  if (paymentPhase === 'pos_3d') {
    return (
      <div className="container my-4" style={{ maxWidth: 800 }}>
        <h1 className="h3 fw-bold mb-4">Kredi Kartı ile Ödeme</h1>
        <div className="alert alert-info"><i className="fas fa-shield-alt me-2" /><strong>3D Secure:</strong> Kart bilgilerinizi banka sayfasında güvenle gireceksiniz. Bilgileriniz sunucumuza ulaşmaz.</div>
        <div className="alert alert-warning small"><i className="fas fa-spinner fa-spin me-2" />Banka ödeme sayfasına yönlendiriliyorsunuz...</div>
        <VirtualPosRedirect htmlContent={posHtml} />
      </div>
    );
  }

  if (paymentPhase === 'paytr') {
    return (
      <div className="container my-4" style={{ maxWidth: 800 }}>
        <h1 className="h3 fw-bold mb-4">Kredi Kartı ile Ödeme</h1>
        <div className="alert alert-info"><i className="fas fa-shield-alt me-2" /><strong>Güvenli Ödeme:</strong> Kart bilgileriniz PayTR güvenli altyapısı üzerinden işlenir. Bilgileriniz sunucumuza ulaşmaz.</div>
        <VirtualPosRedirect htmlContent={posHtml} />
      </div>
    );
  }

  if (paymentPhase === 'bank_transfer') {
    return (
      <div className="container my-4" style={{ maxWidth: 700 }}>
        <h1 className="h3 fw-bold mb-4">Havale / EFT ile Ödeme</h1>
        <BankTransferInfo bankDetails={bankDetails} deadline={bankDeadline} />
        <div className="text-center mt-4">
          <button className="btn btn-outline-primary" onClick={() => navigate('/')}>Alışverişe Devam Et</button>
        </div>
      </div>
    );
  }

  // ===== Checkout steps =====
  return (
    <div className="container my-4" style={{ maxWidth: 800 }}>
      <h1 className="h3 fw-bold mb-4">Ödeme</h1>
      <CheckoutStepper currentStep={step} />
      {/* Errors shown via toast notifications */}

      {step === 'address' && (
        <div>
          <h5 className="mb-3">Teslimat Adresi</h5>
          {/* Saved addresses */}
          {savedAddresses.length > 0 && !showNewAddress && (
            <div className="mb-3">
              <div className="row g-3">
                {savedAddresses.map(a => (
                  <div key={a.id} className="col-md-6">
                    <div className={`card h-100 border-2 ${address?.id === a.id ? 'border-primary bg-primary bg-opacity-10' : 'border-light'}`}
                      style={{cursor:'pointer'}} onClick={() => setAddress(a)}>
                      <div className="card-body small">
                        <div className="d-flex justify-content-between">
                          <span className="fw-semibold">{a.title || 'Adres'}</span>
                          {a.isDefault && <span className="badge bg-primary" style={{fontSize:10}}>Varsayılan</span>}
                        </div>
                        <div className="text-muted mt-1">{a.firstName} {a.lastName}</div>
                        <div className="text-muted">{a.addressLine}</div>
                        <div className="text-muted">{a.district} / {a.city}</div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
              <div className="d-flex justify-content-between align-items-center mt-3">
                <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => setShowNewAddress(true)}>
                  <i className="fas fa-plus me-1" />Yeni Adres Ekle
                </button>
                <button className="btn btn-primary" onClick={next} disabled={!address}>Devam Et</button>
              </div>
            </div>
          )}
          {/* New address form */}
          {(showNewAddress || savedAddresses.length === 0) && (
            <div>
              {savedAddresses.length > 0 && (
                <button type="button" className="btn btn-sm btn-outline-primary mb-3" onClick={() => setShowNewAddress(false)}>
                  <i className="fas fa-arrow-left me-1" />Kayıtlı Adreslerime Dön
                </button>
              )}
              <AddressForm onSubmit={(data) => { setAddress(data); setShowNewAddress(false); next(); }} />
            </div>
          )}
        </div>
      )}

      {step === 'shipping' && (
        <div>
          <h5 className="mb-3">Kargo Seçimi</h5>
          {cargoProviders.length > 0 ? (
            <div className="d-flex flex-column gap-2">
              {cargoProviders.map(cp => {
                const isSelected = selectedCargoProvider?.id === cp.id;
                const subtotal = cart.cart?.subtotal || 0;
                const isFree = cp.freeShippingThreshold && subtotal >= cp.freeShippingThreshold;
                const cost = isFree ? 0 : cp.baseCost;
                const vat = cost > 0 ? (cost * (cp.vatRate || 0) / 100) : 0;
                return (
                  <div key={cp.id}
                    className={`p-3 border rounded-3 d-flex align-items-center gap-3 ${isSelected ? 'border-primary bg-primary bg-opacity-10' : 'border-light'}`}
                    style={{ cursor: 'pointer', transition: 'all 0.15s' }}
                    onClick={() => { setSelectedCargoProvider(cp); setCargoCompany(cp.code); }}>
                    <input type="radio" className="form-check-input m-0 flex-shrink-0" checked={isSelected} readOnly />
                    <div className="flex-grow-1">
                      <div className="d-flex justify-content-between align-items-start">
                        <div>
                          <div className="fw-semibold">{cp.name}</div>
                          <small className="text-muted">Tahmini teslimat: {cp.estimatedDeliveryDays} iş günü</small>
                        </div>
                        <div className="text-end">
                          {isFree ? (
                            <div>
                              <span className="badge bg-success px-2 py-1">Ücretsiz Kargo</span>
                            </div>
                          ) : (
                            <div>
                              <div className="fw-bold text-primary">{formatPrice(cost)}</div>
                              {vat > 0 && <small className="text-muted">+{formatPrice(vat)} KDV</small>}
                            </div>
                          )}
                        </div>
                      </div>
                      {!isFree && cp.freeShippingThreshold > 0 && (
                        <div className="mt-1">
                          <small className="text-success"><i className="fas fa-info-circle me-1" />{formatPrice(cp.freeShippingThreshold)} üzeri ücretsiz kargo</small>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="alert alert-light small">
              <i className="fas fa-truck me-2" />Standart kargo ile gönderilecektir.
            </div>
          )}
          <div className="d-flex gap-2 mt-3">
            <button className="btn btn-outline-secondary" onClick={prev}>Geri</button>
            <button className="btn btn-primary" onClick={next}>Devam</button>
          </div>
        </div>
      )}

      {step === 'payment' && (
        <div>
          <h5 className="mb-3">Ödeme Yöntemi</h5>
          {paymentMethods.filter(m => m.active).map(m => (
            <div key={m.method} className={`form-check mb-3 p-3 border rounded ${paymentMethod === m.method ? 'border-primary' : ''}`}>
              <input className="form-check-input" type="radio" name="payment" checked={paymentMethod === m.method} onChange={() => setPaymentMethod(m.method)} id={`pm-${m.method}`} />
              <label className="form-check-label" htmlFor={`pm-${m.method}`}>
                {m.icon && <i className={`${m.icon} me-2 text-primary`} />}
                <strong>{m.label}</strong>
                <small className="d-block text-muted">{m.description}</small>
              </label>
            </div>
          ))}
          <div className="d-flex gap-2 mt-3">
            <button className="btn btn-outline-secondary" onClick={prev}>Geri</button>
            <button className="btn btn-primary" onClick={next}>Devam</button>
          </div>
        </div>
      )}

      {step === 'confirm' && (() => {
        const subtotal = cart.cart?.subtotal || 0;
        const cp = selectedCargoProvider;
        const isFreeShipping = cp && cp.freeShippingThreshold && subtotal >= cp.freeShippingThreshold;
        const shippingCost = isFreeShipping ? 0 : (cp?.baseCost || cart.cart?.shippingCost || 0);
        const shippingVat = shippingCost > 0 && cp?.vatRate ? shippingCost * cp.vatRate / 100 : 0;
        const discount = cart.cart?.discountAmount || 0;
        const total = subtotal + shippingCost + shippingVat - discount;
        return (
        <div>
          <h5 className="mb-3">Sipariş Onay</h5>
          <div className="store-cart-summary mb-3">
            <div className="store-cart-summary-row"><span>Ürün Toplamı</span><strong>{formatPrice(subtotal)}</strong></div>
            <div className="store-cart-summary-row">
              <span>Kargo ({cp?.name || cargoCompany})</span>
              <span>{isFreeShipping ? <span className="badge bg-success">Ücretsiz</span> : formatPrice(shippingCost)}</span>
            </div>
            {shippingVat > 0 && (
              <div className="store-cart-summary-row text-muted small"><span>Kargo KDV (%{cp?.vatRate})</span><span>{formatPrice(shippingVat)}</span></div>
            )}
            {discount > 0 && <div className="store-cart-summary-row text-success"><span>İndirim</span><span>-{formatPrice(discount)}</span></div>}
            <div className="store-cart-summary-row store-cart-summary-total">
              <span>Genel Toplam</span><strong>{formatPrice(total)}</strong>
            </div>
            <div className="store-cart-summary-row text-muted small"><span>Kargo Firması</span><span>{cp?.name || cargoCompany}</span></div>
            {cp?.estimatedDeliveryDays && <div className="store-cart-summary-row text-muted small"><span>Tahmini Teslimat</span><span>{cp.estimatedDeliveryDays} iş günü</span></div>}
            <div className="store-cart-summary-row text-muted small"><span>Ödeme Yöntemi</span><span>{getMethodLabel(paymentMethod)}</span></div>
          </div>
          <div className="form-check mb-3">
            <input className="form-check-input" type="checkbox" id="contract" checked={contractAccepted} onChange={e => setContractAccepted(e.target.checked)} />
            <label className="form-check-label small" htmlFor="contract">
              <a href="/sayfa/mesafeli-satis-sozlesmesi" target="_blank" rel="noopener noreferrer">Mesafeli Satış Sözleşmesi</a>'ni okudum ve kabul ediyorum.
            </label>
          </div>
          {!contractAccepted && (
            <div className="alert alert-warning py-2 small mb-3">
              <i className="fas fa-exclamation-triangle me-2" />Devam etmek için mesafeli satış sözleşmesini onaylamanız gerekmektedir.
            </div>
          )}
          <div className="d-flex gap-2">
            <button className="btn btn-outline-secondary" onClick={prev}>Geri</button>
            <button className="btn btn-primary btn-lg flex-grow-1" onClick={handlePlaceOrder} disabled={loading || !contractAccepted}>
              {loading ? <><span className="spinner-border spinner-border-sm me-2" />İşleniyor...</> : <><i className="fas fa-lock me-2" />Siparişi Onayla ve Öde</>}
            </button>
          </div>
        </div>
        );
      })()}
    </div>
  );
}

/** Renders bank POS 3D redirect form — auto-submits to bank's page */
function VirtualPosRedirect({ htmlContent }) {
  const iframeRef = React.useRef(null);

  useEffect(() => {
    if (htmlContent && iframeRef.current) {
      const doc = iframeRef.current.contentDocument || iframeRef.current.contentWindow.document;
      doc.open();
      doc.write(htmlContent);
      doc.close();
    }
  }, [htmlContent]);

  if (!htmlContent) return null;

  return (
    <iframe
      ref={iframeRef}
      title="3D Secure Ödeme"
      style={{ width: '100%', height: 500, border: '1px solid #dee2e6', borderRadius: 8 }}
    />
  );
}
