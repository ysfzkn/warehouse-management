import React, { useState, useEffect } from 'react';
import { useOutletContext, useNavigate } from 'react-router-dom';
import { useToast } from '../../components/store/Toast';
import CheckoutStepper from '../../components/store/CheckoutStepper';
import AddressForm from '../../components/store/AddressForm';
import IyzicoCheckoutForm from '../../components/store/IyzicoCheckoutForm';
import BankTransferInfo from '../../components/store/BankTransferInfo';
import LegalContractModal from '../../components/store/LegalContractModal';
import axios from 'axios';
import { v4 as uuidv4 } from 'uuid';

const STEPS = ['address', 'shipping', 'payment', 'confirm'];

function generateIdempotencyKey() {
  try {
    return uuidv4();
  } catch {
    return 'idem-' + Date.now() + '-' + Math.random().toString(36).substr(2, 12);
  }
}

export default function CheckoutPage() {
  const { cart, siteSettings } = useOutletContext();
  // Test mode — order intake is disabled when store_purchasing_enabled=false
  const purchasingEnabled = !siteSettings || siteSettings.get('store_purchasing_enabled', 'true') === 'true';
  const navigate = useNavigate();
  const toast = useToast();
  const [step, setStep] = useState('address');
  const [address, setAddress] = useState(null);
  const [cargoCompany, setCargoCompany] = useState('YURTICI');
  const [paymentMethod, setPaymentMethod] = useState('CREDIT_CARD');
  const [contractAccepted, setContractAccepted] = useState(false);
  const [contractAcceptedAt, setContractAcceptedAt] = useState(null);
  const [preliminaryInfoAccepted, setPreliminaryInfoAccepted] = useState(false);
  const [preliminaryInfoAcceptedAt, setPreliminaryInfoAcceptedAt] = useState(null);
  // Legal contract modals (Distance Sales / Preliminary Information)
  const [legalModal, setLegalModal] = useState(null); // { type: 'contract' | 'preInfo' }
  const [loading, setLoading] = useState(false);
  const [, setError] = useState(''); // kept for setError calls, display via toast

  // --- Guest checkout state ---
  const [isGuest] = useState(!localStorage.getItem('customer_token'));
  const [guestEmail, setGuestEmail] = useState('');
  const [guestKvkkConsent, setGuestKvkkConsent] = useState(false);
  const [guestErrors, setGuestErrors] = useState({});

  // Payment state
  const [paymentPhase, setPaymentPhase] = useState(null);
  const [savedAddresses, setSavedAddresses] = useState([]);
  const [showNewAddress, setShowNewAddress] = useState(false);
  const [iyzicoHtml, setIyzicoHtml] = useState(null);
  // Active gateway name — comes from the backend /api/store/payment/initialize response.
  // Used in the "processed by X secure infrastructure" text and payment labels in the UI.
  const [activeProviderName, setActiveProviderName] = useState('güvenli ödeme altyapısı');
  const [posHtml, setPosHtml] = useState(null);
  const [bankDetails, setBankDetails] = useState(null);
  const [cargoProviders, setCargoProviders] = useState([]);
  const [selectedCargoProvider, setSelectedCargoProvider] = useState(null);
  const [bankDeadline, setBankDeadline] = useState(null);

  // Dynamic payment methods from API
  const [paymentMethods, setPaymentMethods] = useState([]);

  // Load saved addresses (only for authenticated customers)
  useEffect(() => {
    if (isGuest) {
      setShowNewAddress(true);
      return;
    }
    axios
      .get('/api/store/addresses')
      .then((r) => {
        const addrs = r.data || [];
        setSavedAddresses(addrs);
        if (addrs.length === 0) setShowNewAddress(true);
        else {
          const def = addrs.find((a) => a.isDefault) || addrs[0];
          if (def) setAddress(def);
        }
      })
      .catch(() => setShowNewAddress(true));
  }, [isGuest]); // eslint-disable-line react-hooks/exhaustive-deps

  // Load cargo providers
  useEffect(() => {
    axios
      .get('/api/store/checkout/cargo-providers')
      .then((r) => {
        const providers = r.data || [];
        setCargoProviders(providers);
        if (providers.length > 0) {
          setSelectedCargoProvider(providers[0]);
          setCargoCompany(providers[0].code);
        }
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    axios
      .get('/api/store/payment/methods')
      .then((r) => {
        const methods = r.data || [];
        setPaymentMethods(methods);
        if (methods.length > 0 && methods[0].method) setPaymentMethod(methods[0].method);
      })
      .catch(() => {
        // Fallback: hardcoded methods if API fails
        setPaymentMethods([
          {
            method: 'CREDIT_CARD',
            label: 'Kredi / Banka Kartı',
            description: '3D Secure ile güvenli ödeme',
            icon: 'fas fa-credit-card',
            active: true,
          },
          {
            method: 'BANK_TRANSFER',
            label: 'Havale / EFT',
            description: 'Banka hesabımıza havale yapın',
            icon: 'fas fa-university',
            active: true,
          },
          {
            method: 'DOOR_CASH',
            label: 'Kapıda Ödeme',
            description: 'Teslimat sırasında nakit veya kart ile ödeyin',
            icon: 'fas fa-door-open',
            active: true,
          },
        ]);
      });
  }, []);

  const formatPrice = (p) =>
    new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(p || 0);
  const currentIndex = STEPS.indexOf(step);
  const next = () => setStep(STEPS[Math.min(currentIndex + 1, STEPS.length - 1)]);
  const prev = () => setStep(STEPS[Math.max(currentIndex - 1, 0)]);

  const getAuthHeaders = () => {
    const token = localStorage.getItem('customer_token');
    return token ? { Authorization: `Bearer ${token}` } : {};
  };

  const getMethodLabel = (method) => {
    const m = paymentMethods.find((pm) => pm.method === method);
    return m ? m.label : method;
  };

  const handlePlaceOrder = async () => {
    if (!preliminaryInfoAccepted) {
      toast.warning('Ön bilgilendirme formunu onaylayın.');
      setError('Ön bilgilendirme formunu onaylayın.');
      return;
    }
    if (!contractAccepted) {
      toast.warning('Mesafeli satış sözleşmesini onaylayın.');
      setError('Mesafeli satış sözleşmesini onaylayın.');
      return;
    }

    // Guest validation
    if (isGuest) {
      const errs = {};
      if (!guestEmail || !/^\S+@\S+\.\S+$/.test(guestEmail)) errs.email = 'Geçerli bir e-posta girin.';
      if (
        !address ||
        !address.firstName ||
        !address.lastName ||
        !address.phone ||
        !address.addressLine ||
        !address.city ||
        !address.district
      ) {
        errs.address = 'Teslimat adresi bilgilerini eksiksiz girin.';
      }
      if (!guestKvkkConsent) errs.kvkk = 'KVKK aydınlatma metnini onaylamalısınız.';
      if (Object.keys(errs).length > 0) {
        setGuestErrors(errs);
        toast.warning(Object.values(errs)[0]);
        return;
      }
      setGuestErrors({});
    }

    setLoading(true);
    setError('');
    toast.info('Siparişiniz oluşturuluyor...');

    try {
      let orderRes;

      // Idempotency-key against double-click — the same checkout submit creates a single order.
      // The frontend could generate it upfront and cache it for 24h; holding it in useState to avoid
      // regenerating on every render would be ideal — here we generate it at submit time because
      // the button is already disabled before submit.
      const checkoutIdempotencyKey = generateIdempotencyKey();
      const checkoutHeaders = { 'Idempotency-Key': checkoutIdempotencyKey };

      if (isGuest) {
        // Guest checkout - inline address + contact info
        const sessionId = localStorage.getItem('store_session_id');
        orderRes = await axios.post(
          '/api/store/checkout/guest-checkout',
          {
            email: guestEmail.trim(),
            firstName: address.firstName,
            lastName: address.lastName,
            phone: address.phone,
            shippingAddressLine: address.addressLine,
            shippingCity: address.city,
            shippingDistrict: address.district,
            shippingPostalCode: address.postalCode || '',
            billingSameAsShipping: true,
            cargoCompany,
            cargoProviderId: selectedCargoProvider?.id || null,
            paymentMethod,
            distanceSalesContractAccepted: true,
            distanceSalesContractAcceptedAt: contractAcceptedAt,
            preliminaryInfoAccepted: true,
            preliminaryInfoAcceptedAt: preliminaryInfoAcceptedAt,
            kvkkConsent: guestKvkkConsent,
            kvkkConsentAt: guestKvkkConsent ? new Date().toISOString() : null,
            sessionId,
          },
          { headers: checkoutHeaders }
        );
      } else {
        // Authenticated checkout
        if (!address?.id) {
          throw new Error(
            'Teslimat adresi seçilmedi veya kaydedilemedi. Lütfen adres adımına dönüp tekrar deneyin.'
          );
        }
        orderRes = await axios.post(
          '/api/store/checkout/place-order',
          {
            shippingAddressId: address.id,
            billingAddressId: address.id,
            cargoCompany,
            cargoProviderId: selectedCargoProvider?.id || null,
            paymentMethod,
            distanceSalesContractAccepted: true,
            distanceSalesContractAcceptedAt: contractAcceptedAt,
            preliminaryInfoAccepted: true,
            preliminaryInfoAcceptedAt: preliminaryInfoAcceptedAt,
          },
          { headers: { ...getAuthHeaders(), ...checkoutHeaders } }
        );
      }

      const orderId = orderRes.data.orderId;

      // Phase 2: Initialize payment
      const idempotencyKey = generateIdempotencyKey();
      const paymentRes = await axios.post(
        '/api/store/payment/initialize',
        {
          orderId,
          paymentMethod,
          installmentCount: 1,
          idempotencyKey,
        },
        { headers: getAuthHeaders() }
      );

      const result = paymentRes.data;

      if (paymentMethod === 'CREDIT_CARD' || paymentMethod === 'VIRTUAL_POS') {
        if (result.success && result.htmlContent) {
          const html = result.htmlContent;
          // The backend now returns providerName/providerDisplayName — this is authoritative.
          // The HTML content check remains only as a backward-compat fallback.
          if (result.providerDisplayName) setActiveProviderName(result.providerDisplayName);
          const providerName = (result.providerName || '').toUpperCase();
          const isIyzico = providerName === 'IYZICO' || html.includes('iyzico') || html.includes('iyziup');
          const isPayTR =
            providerName === 'PAYTR' || html.includes('paytr.com') || html.includes('paytriframe');
          if (isIyzico) {
            setIyzicoHtml(html);
            setPaymentPhase('iyzico');
            try {
              await cart.fetchCart();
            } catch {}
          } else if (isPayTR) {
            setPosHtml(html);
            setPaymentPhase('paytr');
            try {
              await cart.fetchCart();
            } catch {}
          } else {
            setPosHtml(html);
            setPaymentPhase('pos_3d');
            try {
              await cart.fetchCart();
            } catch {}
          }
        } else {
          const errMsg = result.errorMessage || 'Ödeme başlatma başarısız. Lütfen tekrar deneyin.';
          setError(errMsg);
          toast.error(errMsg);
        }
      } else if (paymentMethod === 'BANK_TRANSFER') {
        if (result.success && result.bankDetails) {
          setBankDetails(result.bankDetails);
          setBankDeadline(new Date(Date.now() + 48 * 3600 * 1000).toISOString());
          setPaymentPhase('bank_transfer');
          try {
            await cart.fetchCart();
          } catch {}
        } else {
          const errMsg2 = result.errorMessage || 'Havale bilgileri oluşturulamadı.';
          setError(errMsg2);
          toast.error(errMsg2);
        }
      } else {
        // DOOR_CASH / DOOR_CARD
        try {
          await cart.fetchCart();
        } catch {}
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
      setTimeout(() => {
        try {
          t.style.animation = 'toastSlideOut 0.3s ease forwards';
          setTimeout(() => {
            try {
              document.body.removeChild(t);
            } catch {}
          }, 300);
        } catch {}
      }, 6000);
      if (errorCode === 'PAY_006') {
        toast.warning('Stok yetersiz. Sepete yönlendiriliyorsunuz...');
        setTimeout(() => navigate('/sepet'), 3000);
      }
      // Refresh cart to sync badge
      try {
        await cart.fetchCart();
      } catch {}
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
          <div
            style={{
              width: 56,
              height: 56,
              borderRadius: '50%',
              background: 'linear-gradient(135deg, #eff6ff, #dbeafe)',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              marginBottom: 12,
            }}
          >
            <i className="fas fa-lock" style={{ fontSize: 22, color: '#2563eb' }} />
          </div>
          <h2 className="h4 fw-bold mb-1">Güvenli Ödeme</h2>
          <p className="text-muted small mb-0">
            Kart bilgileriniz <strong>{activeProviderName}</strong> güvenli altyapısında işlenir, sunucumuza
            ulaşmaz.
          </p>
        </div>

        {/* Trust badges */}
        <div className="d-flex justify-content-center gap-3 mb-4">
          {[
            { icon: 'fas fa-shield-alt', label: '3D Secure', color: '#2563eb' },
            { icon: 'fas fa-lock', label: 'SSL Şifreli', color: '#059669' },
            { icon: 'fas fa-credit-card', label: 'PCI Uyumlu', color: '#7c3aed' },
          ].map((b) => (
            <div
              key={b.label}
              className="d-flex align-items-center gap-1 px-3 py-1 rounded-pill"
              style={{
                background: '#f8fafc',
                border: '1px solid #e2e8f0',
                fontSize: '0.75rem',
                color: b.color,
                fontWeight: 500,
              }}
            >
              <i className={b.icon} style={{ fontSize: 11 }} />
              <span>{b.label}</span>
            </div>
          ))}
        </div>

        {/* iyzico form */}
        <div
          style={{
            background: '#fff',
            borderRadius: 16,
            border: '1px solid #e2e8f0',
            boxShadow: '0 4px 24px rgba(0,0,0,0.06)',
            overflow: 'hidden',
          }}
        >
          <IyzicoCheckoutForm htmlContent={iyzicoHtml} onComplete={handleIyzicoComplete} />
        </div>
      </div>
    );
  }

  if (paymentPhase === 'pos_3d') {
    return (
      <div className="container my-4" style={{ maxWidth: 800 }}>
        <h1 className="h3 fw-bold mb-4">Kredi Kartı ile Ödeme</h1>
        <div className="alert alert-info">
          <i className="fas fa-shield-alt me-2" />
          <strong>3D Secure:</strong> Kart bilgilerinizi <strong>{activeProviderName}</strong> üzerinden
          bankanın güvenli sayfasında gireceksiniz. Bilgileriniz sunucumuza ulaşmaz.
        </div>
        <div className="alert alert-warning small">
          <i className="fas fa-spinner fa-spin me-2" />
          Banka ödeme sayfasına yönlendiriliyorsunuz...
        </div>
        <VirtualPosRedirect htmlContent={posHtml} />
      </div>
    );
  }

  if (paymentPhase === 'paytr') {
    return (
      <div className="container my-4" style={{ maxWidth: 800 }}>
        <h1 className="h3 fw-bold mb-4">Kredi Kartı ile Ödeme</h1>
        <div className="alert alert-info">
          <i className="fas fa-shield-alt me-2" />
          <strong>Güvenli Ödeme:</strong> Kart bilgileriniz <strong>{activeProviderName}</strong> güvenli
          altyapısı üzerinden işlenir. Bilgileriniz sunucumuza ulaşmaz.
        </div>
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
          <button className="btn btn-outline-primary" onClick={() => navigate('/')}>
            Alışverişe Devam Et
          </button>
        </div>
      </div>
    );
  }

  // ===== Checkout steps =====
  // Mobile sticky bar — mirrors the current step's primary CTA + a live total.
  const stickySubtotal = cart.cart?.subtotal || 0;
  const stickyCp = selectedCargoProvider;
  const stickyFree =
    stickyCp && stickyCp.freeShippingThreshold && stickySubtotal >= stickyCp.freeShippingThreshold;
  const stickyShip = stickyFree ? 0 : stickyCp?.baseCost || cart.cart?.shippingCost || 0;
  const stickyShipVat = stickyShip > 0 && stickyCp?.vatRate ? (stickyShip * stickyCp.vatRate) / 100 : 0;
  const stickyTotal = stickySubtotal + stickyShip + stickyShipVat - (cart.cart?.discountAmount || 0);
  const stickyBar =
    step === 'confirm'
      ? {
          label: 'Genel Toplam',
          amount: stickyTotal,
          cta: 'Siparişi Onayla',
          onClick: handlePlaceOrder,
          disabled: loading || !contractAccepted || !preliminaryInfoAccepted || !purchasingEnabled,
        }
      : {
          label: 'Ara Toplam',
          amount: stickySubtotal,
          cta: 'Devam Et',
          onClick: next,
          disabled: step === 'address' && !address,
        };

  return (
    <div className="container my-4 store-checkout-has-sticky" style={{ maxWidth: 800 }}>
      <h1 className="h3 fw-bold mb-4">Ödeme</h1>
      <CheckoutStepper currentStep={step} />
      {/* Errors shown via toast notifications */}

      {step === 'address' && (
        <div>
          {/* Guest contact info */}
          {isGuest && (
            <div className="card mb-3 border-primary border-2">
              <div className="card-body">
                <div className="d-flex justify-content-between align-items-start mb-3">
                  <h5 className="mb-0">
                    <i className="fas fa-user me-2 text-primary"></i>Misafir Olarak Devam Ediyorsunuz
                  </h5>
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-primary"
                    onClick={() => navigate('/giris?redirect=/odeme')}
                  >
                    <i className="fas fa-sign-in-alt me-1"></i>Üye Girişi Yap
                  </button>
                </div>
                <p className="small text-muted mb-3">
                  Üye olmadan siparişi tamamlayabilirsiniz. E-postanıza "hesabımı tamamla" bağlantısı
                  gönderilecek; dilediğinizde şifre belirleyip hesabınızı aktif edebilirsiniz.
                </p>
                <div className="mb-3">
                  <label className="form-label small fw-semibold">
                    E-posta Adresi <span className="text-danger">*</span>
                  </label>
                  <input
                    type="email"
                    className={`form-control ${guestErrors.email ? 'is-invalid' : ''}`}
                    placeholder="ornek@eposta.com"
                    value={guestEmail}
                    onChange={(e) => {
                      setGuestEmail(e.target.value);
                      setGuestErrors((prev) => ({ ...prev, email: '' }));
                    }}
                  />
                  {guestErrors.email && <div className="invalid-feedback">{guestErrors.email}</div>}
                  <small className="text-muted">
                    Sipariş onayı ve hesap tamamlama bağlantısı bu adrese gönderilecektir.
                  </small>
                </div>
                <div className="form-check">
                  <input
                    className="form-check-input"
                    type="checkbox"
                    id="guestKvkk"
                    checked={guestKvkkConsent}
                    onChange={(e) => {
                      setGuestKvkkConsent(e.target.checked);
                      setGuestErrors((prev) => ({ ...prev, kvkk: '' }));
                    }}
                  />
                  <label className="form-check-label small" htmlFor="guestKvkk">
                    <a href="/sayfa/kvkk" target="_blank" rel="noopener noreferrer">
                      KVKK Aydınlatma Metni
                    </a>
                    'ni okudum ve onaylıyorum. <span className="text-danger">*</span>
                  </label>
                  {guestErrors.kvkk && <div className="text-danger small mt-1">{guestErrors.kvkk}</div>}
                </div>
              </div>
            </div>
          )}

          <h5 className="mb-3">Teslimat Adresi</h5>
          {/* Saved addresses */}
          {savedAddresses.length > 0 && !showNewAddress && (
            <div className="mb-3">
              <div className="row g-3">
                {savedAddresses.map((a) => (
                  <div key={a.id} className="col-md-6">
                    <div
                      className={`card h-100 border-2 ${address?.id === a.id ? 'border-primary bg-primary bg-opacity-10' : 'border-light'}`}
                      style={{ cursor: 'pointer' }}
                      onClick={() => setAddress(a)}
                    >
                      <div className="card-body small">
                        <div className="d-flex justify-content-between">
                          <span className="fw-semibold">{a.title || 'Adres'}</span>
                          {a.isDefault && (
                            <span className="badge bg-primary" style={{ fontSize: 10 }}>
                              Varsayılan
                            </span>
                          )}
                        </div>
                        <div className="text-muted mt-1">
                          {a.firstName} {a.lastName}
                        </div>
                        <div className="text-muted">{a.addressLine}</div>
                        <div className="text-muted">
                          {a.district} / {a.city}
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
              <div className="d-flex justify-content-between align-items-center mt-3">
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary"
                  onClick={() => setShowNewAddress(true)}
                >
                  <i className="fas fa-plus me-1" />
                  Yeni Adres Ekle
                </button>
                <button className="btn btn-primary" onClick={next} disabled={!address}>
                  Devam Et
                </button>
              </div>
            </div>
          )}
          {/* New address form */}
          {(showNewAddress || savedAddresses.length === 0) && (
            <div>
              {savedAddresses.length > 0 && (
                <button
                  type="button"
                  className="btn btn-sm btn-outline-primary mb-3"
                  onClick={() => setShowNewAddress(false)}
                >
                  <i className="fas fa-arrow-left me-1" />
                  Kayıtlı Adreslerime Dön
                </button>
              )}
              <AddressForm
                onSubmit={async (data) => {
                  try {
                    if (isGuest) {
                      // Guest checkout — the address is not saved to a separate table,
                      // the form fields go directly into the place-guest-order payload.
                      setAddress(data);
                    } else {
                      // Authenticated — save the address to the DB, get back the object with an id
                      const res = await axios.post('/api/store/addresses', data, {
                        headers: getAuthHeaders(),
                      });
                      const saved = res.data;
                      setSavedAddresses((prev) => [...prev, saved]);
                      setAddress(saved);
                    }
                    setShowNewAddress(false);
                    next();
                  } catch (err) {
                    alert('Adres kaydedilemedi: ' + (err.response?.data?.message || err.message));
                  }
                }}
              />
            </div>
          )}
        </div>
      )}

      {step === 'shipping' && (
        <div>
          <h5 className="mb-3">Kargo Seçimi</h5>
          {cargoProviders.length > 0 ? (
            <div className="d-flex flex-column gap-2">
              {cargoProviders.map((cp) => {
                const isSelected = selectedCargoProvider?.id === cp.id;
                const subtotal = cart.cart?.subtotal || 0;
                const isFree = cp.freeShippingThreshold && subtotal >= cp.freeShippingThreshold;
                const cost = isFree ? 0 : cp.baseCost;
                const vat = cost > 0 ? (cost * (cp.vatRate || 0)) / 100 : 0;
                return (
                  <div
                    key={cp.id}
                    className={`p-3 border rounded-3 d-flex align-items-center gap-3 ${isSelected ? 'border-primary bg-primary bg-opacity-10' : 'border-light'}`}
                    style={{ cursor: 'pointer', transition: 'all 0.15s' }}
                    onClick={() => {
                      setSelectedCargoProvider(cp);
                      setCargoCompany(cp.code);
                    }}
                  >
                    <input
                      type="radio"
                      className="form-check-input m-0 flex-shrink-0"
                      checked={isSelected}
                      readOnly
                    />
                    <div className="flex-grow-1">
                      <div className="d-flex justify-content-between align-items-start">
                        <div>
                          <div className="fw-semibold">{cp.name}</div>
                          <small className="text-muted">
                            Tahmini teslimat: {cp.estimatedDeliveryDays} iş günü
                          </small>
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
                          <small className="text-success">
                            <i className="fas fa-info-circle me-1" />
                            {formatPrice(cp.freeShippingThreshold)} üzeri ücretsiz kargo
                          </small>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="alert alert-light small">
              <i className="fas fa-truck me-2" />
              Standart kargo ile gönderilecektir.
            </div>
          )}
          <div className="d-flex gap-2 mt-3">
            <button className="btn btn-outline-secondary" onClick={prev}>
              Geri
            </button>
            <button className="btn btn-primary" onClick={next}>
              Devam
            </button>
          </div>
        </div>
      )}

      {step === 'payment' && (
        <div>
          <h5 className="mb-3">Ödeme Yöntemi</h5>
          {paymentMethods
            .filter((m) => m.active)
            .map((m) => (
              <div
                key={m.method}
                className={`form-check mb-3 p-3 border rounded ${paymentMethod === m.method ? 'border-primary' : ''}`}
              >
                <input
                  className="form-check-input"
                  type="radio"
                  name="payment"
                  checked={paymentMethod === m.method}
                  onChange={() => setPaymentMethod(m.method)}
                  id={`pm-${m.method}`}
                />
                <label className="form-check-label" htmlFor={`pm-${m.method}`}>
                  {m.icon && <i className={`${m.icon} me-2 text-primary`} />}
                  <strong>{m.label}</strong>
                  <small className="d-block text-muted">{m.description}</small>
                </label>
              </div>
            ))}
          <div className="d-flex gap-2 mt-3">
            <button className="btn btn-outline-secondary" onClick={prev}>
              Geri
            </button>
            <button className="btn btn-primary" onClick={next}>
              Devam
            </button>
          </div>
        </div>
      )}

      {step === 'confirm' &&
        (() => {
          const subtotal = cart.cart?.subtotal || 0;
          const cp = selectedCargoProvider;
          const isFreeShipping = cp && cp.freeShippingThreshold && subtotal >= cp.freeShippingThreshold;
          const shippingCost = isFreeShipping ? 0 : cp?.baseCost || cart.cart?.shippingCost || 0;
          const shippingVat = shippingCost > 0 && cp?.vatRate ? (shippingCost * cp.vatRate) / 100 : 0;
          const discount = cart.cart?.discountAmount || 0;
          const total = subtotal + shippingCost + shippingVat - discount;
          return (
            <div>
              <h5 className="mb-3">Sipariş Onay</h5>
              <div className="store-cart-summary mb-3">
                <div className="store-cart-summary-row">
                  <span>
                    Ürün Toplamı <small className="text-muted">(KDV dahil)</small>
                  </span>
                  <strong>{formatPrice(subtotal)}</strong>
                </div>
                {/* VAT breakdown — VAT itemization required by Turkish e-invoice regulations */}
                {cart.cart?.vatBreakdown &&
                  Object.keys(cart.cart.vatBreakdown).length > 0 &&
                  Object.entries(cart.cart.vatBreakdown).map(([rate, amount]) => (
                    <div key={rate} className="store-cart-summary-row text-muted small">
                      <span>↳ İçindeki KDV (%{rate})</span>
                      <span>{formatPrice(Number(amount))}</span>
                    </div>
                  ))}
                <div className="store-cart-summary-row">
                  <span>Kargo ({cp?.name || cargoCompany})</span>
                  <span>
                    {isFreeShipping ? (
                      <span className="badge bg-success">Ücretsiz</span>
                    ) : (
                      formatPrice(shippingCost)
                    )}
                  </span>
                </div>
                {shippingVat > 0 && (
                  <div className="store-cart-summary-row text-muted small">
                    <span>↳ Kargo KDV (%{cp?.vatRate})</span>
                    <span>{formatPrice(shippingVat)}</span>
                  </div>
                )}
                {discount > 0 && (
                  <div className="store-cart-summary-row text-success">
                    <span>İndirim</span>
                    <span>-{formatPrice(discount)}</span>
                  </div>
                )}
                <div className="store-cart-summary-row store-cart-summary-total">
                  <span>Genel Toplam</span>
                  <strong>{formatPrice(total)}</strong>
                </div>
                <div className="store-cart-summary-row text-muted small">
                  <span>Kargo Firması</span>
                  <span>{cp?.name || cargoCompany}</span>
                </div>
                {cp?.estimatedDeliveryDays && (
                  <div className="store-cart-summary-row text-muted small">
                    <span>Tahmini Teslimat</span>
                    <span>{cp.estimatedDeliveryDays} iş günü</span>
                  </div>
                )}
                <div className="store-cart-summary-row text-muted small">
                  <span>Ödeme Yöntemi</span>
                  <span>{getMethodLabel(paymentMethod)}</span>
                </div>
              </div>
              {/* Legal contracts — shown via inline modal (Consumer Protection Law no. 6502) */}
              <div className="form-check mb-2">
                <input
                  className="form-check-input"
                  type="checkbox"
                  id="preliminaryInfo"
                  checked={preliminaryInfoAccepted}
                  onChange={(e) => {
                    if (e.target.checked) {
                      // The modal opens automatically when the checkbox is clicked (mandatory reading)
                      setLegalModal({ type: 'preInfo' });
                    } else {
                      setPreliminaryInfoAccepted(false);
                      setPreliminaryInfoAcceptedAt(null);
                    }
                  }}
                />
                <label className="form-check-label small" htmlFor="preliminaryInfo">
                  <button
                    type="button"
                    className="btn btn-link btn-sm p-0 align-baseline"
                    onClick={(e) => {
                      e.preventDefault();
                      setLegalModal({ type: 'preInfo' });
                    }}
                  >
                    Ön Bilgilendirme Formu
                  </button>
                  'nu okudum ve kabul ediyorum.
                </label>
              </div>
              <div className="form-check mb-3">
                <input
                  className="form-check-input"
                  type="checkbox"
                  id="contract"
                  checked={contractAccepted}
                  onChange={(e) => {
                    if (e.target.checked) {
                      setLegalModal({ type: 'contract' });
                    } else {
                      setContractAccepted(false);
                      setContractAcceptedAt(null);
                    }
                  }}
                />
                <label className="form-check-label small" htmlFor="contract">
                  <button
                    type="button"
                    className="btn btn-link btn-sm p-0 align-baseline"
                    onClick={(e) => {
                      e.preventDefault();
                      setLegalModal({ type: 'contract' });
                    }}
                  >
                    Mesafeli Satış Sözleşmesi
                  </button>
                  'ni okudum ve kabul ediyorum.
                </label>
              </div>
              {(!preliminaryInfoAccepted || !contractAccepted) && (
                <div className="alert alert-warning py-2 small mb-3">
                  <i className="fas fa-exclamation-triangle me-2" />
                  Devam etmek için her iki sözleşmeyi de onaylamanız gerekmektedir.
                </div>
              )}
              {!purchasingEnabled && (
                <div className="alert alert-warning small mb-2">
                  <i className="fas fa-flask me-1" />
                  Site test modunda — sipariş alımı geçici olarak kapalıdır.
                </div>
              )}
              <div className="d-flex gap-2">
                <button className="btn btn-outline-secondary" onClick={prev}>
                  Geri
                </button>
                <button
                  className="btn btn-primary btn-lg flex-grow-1"
                  onClick={handlePlaceOrder}
                  disabled={loading || !contractAccepted || !preliminaryInfoAccepted || !purchasingEnabled}
                >
                  {loading ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-2" />
                      İşleniyor...
                    </>
                  ) : (
                    <>
                      <i className="fas fa-lock me-2" />
                      Siparişi Onayla ve Öde
                    </>
                  )}
                </button>
              </div>
            </div>
          );
        })()}

      {/* Legal contract modal (Distance Sales / Preliminary Information) */}
      <LegalContractModal
        open={!!legalModal}
        slug={legalModal?.type === 'contract' ? 'mesafeli-satis-sozlesmesi' : 'on-bilgilendirme-formu'}
        title={legalModal?.type === 'contract' ? 'Mesafeli Satış Sözleşmesi' : 'Ön Bilgilendirme Formu'}
        onClose={() => setLegalModal(null)}
        onConfirm={(timestamp) => {
          if (legalModal?.type === 'contract') {
            setContractAccepted(true);
            setContractAcceptedAt(timestamp);
          } else if (legalModal?.type === 'preInfo') {
            setPreliminaryInfoAccepted(true);
            setPreliminaryInfoAcceptedAt(timestamp);
          }
          setLegalModal(null);
        }}
      />

      {/* Mobil sticky alt bar — adımın birincil aksiyonu + canlı toplam */}
      <div className="store-checkout-sticky-bar d-md-none">
        <div className="store-checkout-sticky-info">
          <small>{stickyBar.label}</small>
          <strong>{formatPrice(stickyBar.amount)}</strong>
        </div>
        <button className="btn btn-primary" onClick={stickyBar.onClick} disabled={stickyBar.disabled}>
          {loading && step === 'confirm' ? (
            <>
              <span className="spinner-border spinner-border-sm me-2" />
              İşleniyor…
            </>
          ) : (
            stickyBar.cta
          )}
        </button>
      </div>
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
