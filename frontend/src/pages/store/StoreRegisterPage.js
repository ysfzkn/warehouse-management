import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';
import { useToast } from '../../components/store/Toast';

const GOOGLE_CLIENT_ID = process.env.REACT_APP_GOOGLE_CLIENT_ID || '';

const PWD_RULES = [
  { key: 'length', label: 'En az 8 karakter', test: (p) => p.length >= 8 },
  { key: 'upper', label: 'En az 1 büyük harf (A-Z)', test: (p) => /[A-Z]/.test(p) },
  { key: 'lower', label: 'En az 1 küçük harf (a-z)', test: (p) => /[a-z]/.test(p) },
  { key: 'digit', label: 'En az 1 rakam (0-9)', test: (p) => /\d/.test(p) },
];

export default function StoreRegisterPage() {
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', password: '', phone: '', kvkkConsent: false, termsConsent: false, marketingConsent: false });
  const [loading, setLoading] = useState(false);
  const [, setError] = useState(''); // display via toast
  const [success, setSuccess] = useState(false);
  const [showPwd, setShowPwd] = useState(false);
  const toast = useToast();

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setForm(f => ({ ...f, [name]: type === 'checkbox' ? checked : value }));
    setError('');
  };

  const handlePhoneChange = (e) => {
    let val = e.target.value.replace(/[^\d]/g, '');
    if (val.length > 10) val = val.slice(0, 10);
    setForm(f => ({ ...f, phone: val }));
  };

  const allPwdValid = PWD_RULES.every(r => r.test(form.password));
  const pwdScore = PWD_RULES.filter(r => r.test(form.password)).length;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!allPwdValid) { toast.warning('Şifre gereksinimleri karşılanmıyor.'); return; }
    if (!form.termsConsent) { toast.warning('Üyelik sözleşmesini kabul etmeniz gerekmektedir.'); return; }
    if (!form.kvkkConsent) { toast.warning('Kişisel verilerin işlenmesine ilişkin aydınlatma metnini onaylamanız gerekmektedir.'); return; }
    setLoading(true); setError('');
    try {
      const phone = form.phone ? '+90' + form.phone : '';
      await axios.post('/api/store/auth/register', {
        firstName: form.firstName, lastName: form.lastName,
        email: form.email, password: form.password,
        phone, kvkkConsent: form.kvkkConsent, marketingConsent: form.marketingConsent,
      });
      setSuccess(true);
    } catch (e) {
      const msg = e.response?.data?.message || 'Kayıt sırasında bir hata oluştu.';
      setError(msg);
      toast.error(msg);
    } finally { setLoading(false); }
  };

  const handleGoogleRegister = () => {
    if (!GOOGLE_CLIENT_ID) { toast.warning('Google ile kayıt şu an kullanılamıyor.'); return; }
    const redirectUri = window.location.origin + '/auth/google/callback';
    const url = `https://accounts.google.com/o/oauth2/v2/auth?client_id=${GOOGLE_CLIENT_ID}&redirect_uri=${encodeURIComponent(redirectUri)}&response_type=code&scope=openid%20email%20profile&access_type=offline&prompt=consent`;
    window.location.href = url;
  };

  // Başarılı kayıt — aktivasyon ekranı
  if (success) {
    return (
      <div className="container py-5" style={{ maxWidth: 520 }}>
        <div className="card border-0 shadow-lg">
          <div className="card-body text-center p-5">
            <div className="mb-4">
              <div style={{ width: 80, height: 80, borderRadius: '50%', background: 'linear-gradient(135deg, #10b981, #059669)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>
                <i className="fas fa-envelope-open-text text-white fa-2x" />
              </div>
            </div>
            <h4 className="fw-bold mb-3">Hesabınız Oluşturuldu!</h4>
            <p className="text-muted mb-4">
              <strong>{form.email}</strong> adresine bir aktivasyon e-postası gönderdik.
              Hesabınızı etkinleştirmek için e-postanızdaki bağlantıya tıklayın.
            </p>
            <div className="alert alert-info small text-start">
              <i className="fas fa-info-circle me-2" />
              E-posta gelmedi mi? Lütfen spam veya gereksiz e-posta klasörünüzü kontrol edin. E-posta birkaç dakika içerisinde ulaşacaktır.
            </div>
            <div className="d-grid gap-2 mt-4">
              <Link to="/giris" className="btn btn-primary"><i className="fas fa-sign-in-alt me-2" />Giriş Yap</Link>
              <Link to="/" className="btn btn-outline-secondary">Mağazaya Dön</Link>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="container py-5" style={{ maxWidth: 520 }}>
      <div className="card border-0 shadow-lg overflow-hidden">
        {/* Başlık */}
        <div className="text-center text-white py-4" style={{ background: 'linear-gradient(135deg, #2563eb, #1e40af)' }}>
          <i className="fas fa-user-plus fa-2x mb-2" />
          <h4 className="fw-bold mb-1">Üye Ol</h4>
          <p className="mb-0 small opacity-75">Alışverişe başlamak için hemen hesap oluşturun</p>
        </div>

        <div className="card-body p-4">
          {/* Google ile Kayıt */}
          <button type="button" className="btn btn-outline-dark w-100 py-2 mb-3 d-flex align-items-center justify-content-center gap-2" onClick={handleGoogleRegister}>
            <svg width="18" height="18" viewBox="0 0 24 24"><path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" fill="#4285F4"/><path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/><path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/><path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/></svg>
            Google ile Kayıt Ol
          </button>

          <div className="d-flex align-items-center gap-3 mb-3">
            <hr className="flex-grow-1" /><span className="text-muted small">veya e-posta ile</span><hr className="flex-grow-1" />
          </div>

          <form onSubmit={handleSubmit}>
            {/* Ad Soyad */}
            <div className="row g-3 mb-3">
              <div className="col-6">
                <label className="form-label small fw-semibold">Adınız <span className="text-danger">*</span></label>
                <input className="form-control" name="firstName" value={form.firstName} onChange={handleChange} required minLength={2} placeholder="Adınız" />
              </div>
              <div className="col-6">
                <label className="form-label small fw-semibold">Soyadınız <span className="text-danger">*</span></label>
                <input className="form-control" name="lastName" value={form.lastName} onChange={handleChange} required minLength={2} placeholder="Soyadınız" />
              </div>
            </div>

            {/* E-posta */}
            <div className="mb-3">
              <label className="form-label small fw-semibold">E-posta Adresi <span className="text-danger">*</span></label>
              <div className="input-group">
                <span className="input-group-text"><i className="fas fa-envelope text-muted" /></span>
                <input type="email" className="form-control" name="email" value={form.email} onChange={handleChange} required placeholder="ornek@email.com" />
              </div>
            </div>

            {/* Telefon */}
            <div className="mb-3">
              <label className="form-label small fw-semibold">Telefon Numarası</label>
              <div className="input-group">
                <span className="input-group-text fw-medium" style={{ minWidth: 56 }}>+90</span>
                <input type="tel" className="form-control" value={form.phone} onChange={handlePhoneChange} placeholder="5XX XXX XX XX" maxLength={10} inputMode="numeric" />
              </div>
            </div>

            {/* Şifre */}
            <div className="mb-3">
              <label className="form-label small fw-semibold">Şifre <span className="text-danger">*</span></label>
              <div className="input-group">
                <span className="input-group-text"><i className="fas fa-lock text-muted" /></span>
                <input type={showPwd ? 'text' : 'password'} className="form-control" name="password" value={form.password} onChange={handleChange} required minLength={8} placeholder="Güçlü bir şifre belirleyin" />
                <button type="button" className="btn btn-outline-secondary" onClick={() => setShowPwd(!showPwd)}>
                  <i className={`fas fa-eye${showPwd ? '-slash' : ''}`} />
                </button>
              </div>
              {/* Şifre gücü göstergesi */}
              {form.password.length > 0 && (
                <div className="mt-2 p-2 bg-light rounded">
                  {PWD_RULES.map(rule => (
                    <div key={rule.key} className={`small d-flex align-items-center gap-2 ${rule.test(form.password) ? 'text-success' : 'text-muted'}`}>
                      <i className={`fas ${rule.test(form.password) ? 'fa-check-circle' : 'fa-circle'}`} style={{ fontSize: 10 }} />
                      {rule.label}
                    </div>
                  ))}
                  <div className="mt-2">
                    <div className="progress" style={{ height: 4 }}>
                      <div className={`progress-bar ${allPwdValid ? 'bg-success' : pwdScore >= 2 ? 'bg-warning' : 'bg-danger'}`}
                        style={{ width: `${(pwdScore / 4) * 100}%` }} />
                    </div>
                    <div className="text-end mt-1">
                      <small className={allPwdValid ? 'text-success' : pwdScore >= 2 ? 'text-warning' : 'text-danger'}>
                        {allPwdValid ? 'Güçlü şifre' : pwdScore >= 2 ? 'Orta güçte' : 'Zayıf şifre'}
                      </small>
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* Onaylar */}
            <div className="mb-3 p-3 bg-light rounded">
              <div className="form-check mb-2">
                <input className="form-check-input" type="checkbox" name="termsConsent" checked={form.termsConsent} onChange={handleChange} id="terms" />
                <label className="form-check-label small" htmlFor="terms">
                  <a href="/sayfa/uyelik-sozlesmesi" target="_blank" rel="noopener noreferrer" className="text-primary">Üyelik Sözleşmesi</a>'ni okudum ve kabul ediyorum. <span className="text-danger">*</span>
                </label>
              </div>
              <div className="form-check mb-2">
                <input className="form-check-input" type="checkbox" name="kvkkConsent" checked={form.kvkkConsent} onChange={handleChange} id="kvkk" />
                <label className="form-check-label small" htmlFor="kvkk">
                  Kişisel verilerin işlenmesine ilişkin <a href="/sayfa/kvkk" target="_blank" rel="noopener noreferrer" className="text-primary">Aydınlatma Metni</a>'ni okudum. <span className="text-danger">*</span>
                </label>
              </div>
              <div className="form-check">
                <input className="form-check-input" type="checkbox" name="marketingConsent" checked={form.marketingConsent} onChange={handleChange} id="marketing" />
                <label className="form-check-label small text-muted" htmlFor="marketing">
                  Kampanya ve fırsatlardan e-posta ile haberdar olmak istiyorum.
                </label>
              </div>
            </div>

            {/* Kayıt Butonu */}
            <button type="submit" className="btn btn-primary w-100 py-2 fw-semibold" disabled={loading || !allPwdValid || !form.kvkkConsent || !form.termsConsent}>
              {loading ? <><span className="spinner-border spinner-border-sm me-2" />Hesap oluşturuluyor...</> : <><i className="fas fa-user-plus me-2" />Üye Ol</>}
            </button>
          </form>

          <div className="text-center mt-4 pt-3 border-top">
            <span className="text-muted small">Zaten bir hesabınız var mı?</span>{' '}
            <Link to="/giris" className="fw-semibold small">Giriş Yapın</Link>
          </div>
        </div>
      </div>
    </div>
  );
}
