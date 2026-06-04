import React, { useState } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { FiLock, FiEye, FiEyeOff, FiCheckCircle, FiXCircle, FiArrowLeft, FiUserCheck } from 'react-icons/fi';

/**
 * Page the customer uses to complete their account after guest checkout.
 * Sets a password using the token from the emailed link and logs in automatically.
 */
export default function CompleteAccountPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token');

  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState('');

  // Password strength checks
  const checks = {
    length: password.length >= 8,
    upper: /[A-Z]/.test(password),
    lower: /[a-z]/.test(password),
    digit: /\d/.test(password),
  };
  const strength = Object.values(checks).filter(Boolean).length;
  const strengthLabel = ['', 'Zayıf', 'Orta', 'İyi', 'Güçlü'][strength];
  const strengthColor = ['', '#ef4444', '#f59e0b', '#3b82f6', '#059669'][strength];

  if (!token) {
    return (
      <div className="container d-flex justify-content-center align-items-center" style={{ minHeight: '70vh' }}>
        <div className="text-center" style={{ maxWidth: 440 }}>
          <div style={{
            width: 72, height: 72, borderRadius: '50%',
            background: 'linear-gradient(135deg, #fef2f2, #fecaca)',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center', marginBottom: 20,
          }}>
            <FiXCircle size={36} color="#dc2626" />
          </div>
          <h2 className="fw-bold mb-3" style={{ color: '#dc2626' }}>Geçersiz Bağlantı</h2>
          <p className="text-muted mb-4">Hesap tamamlama bağlantısı geçersiz veya eksik. Yeni bir şifre sıfırlama talebi oluşturabilirsiniz.</p>
          <Link to="/sifremi-unuttum" className="btn btn-primary px-4">Şifre Sıfırlama Talebi</Link>
        </div>
      </div>
    );
  }

  if (success) {
    return (
      <div className="container d-flex justify-content-center align-items-center" style={{ minHeight: '70vh' }}>
        <div className="text-center" style={{ maxWidth: 460 }}>
          <div style={{
            width: 80, height: 80, borderRadius: '50%',
            background: 'linear-gradient(135deg, #ecfdf5, #d1fae5)',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center', marginBottom: 20,
          }}>
            <FiCheckCircle size={40} color="#059669" />
          </div>
          <h2 className="fw-bold mb-3" style={{ color: '#059669' }}>Hesabınız Aktif!</h2>
          <p className="text-muted mb-4">
            Hesabınız başarıyla tamamlandı ve doğrulandı. Artık siparişlerinizi takip edebilir,
            favori ürünlerinizi saklayabilir ve sonraki alışverişlerinizi hızla tamamlayabilirsiniz.
          </p>
          <div className="d-flex gap-2 justify-content-center">
            <button className="btn btn-primary px-4" onClick={() => navigate('/hesabim/siparisler')}>
              Siparişlerim
            </button>
            <button className="btn btn-outline-primary px-4" onClick={() => navigate('/')}>
              Alışverişe Devam Et
            </button>
          </div>
        </div>
      </div>
    );
  }

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!password) { setError('Şifre gereklidir.'); return; }
    if (password !== confirmPassword) { setError('Şifreler eşleşmiyor.'); return; }
    if (strength < 3) { setError('Şifre en az 8 karakter, büyük harf, küçük harf ve rakam içermelidir.'); return; }

    setLoading(true);
    try {
      const res = await axios.post('/api/store/auth/complete-account', { token, password });
      // Automatic login - store the tokens
      if (res.data?.token) {
        localStorage.setItem('customer_token', res.data.token);
      }
      if (res.data?.refreshToken) {
        localStorage.setItem('customer_refresh_token', res.data.refreshToken);
      }
      // Trigger the auth-changed event
      try { window.dispatchEvent(new Event('auth-changed')); } catch {}
      setSuccess(true);
    } catch (err) {
      setError(err.response?.data?.message || 'Hesap tamamlama başarısız. Bağlantının süresi dolmuş olabilir.');
    } finally { setLoading(false); }
  };

  return (
    <div className="container d-flex justify-content-center align-items-center" style={{ minHeight: '70vh' }}>
      <div style={{ width: '100%', maxWidth: 460 }}>
        <div className="text-center mb-4">
          <div style={{
            width: 64, height: 64, borderRadius: '50%',
            background: 'linear-gradient(135deg, #ecfdf5, #d1fae5)',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center', marginBottom: 16,
          }}>
            <FiUserCheck size={28} color="#059669" />
          </div>
          <h2 className="fw-bold mb-2">Hesabınızı Tamamlayın</h2>
          <p className="text-muted small">
            Sipariş verdiğiniz hesabınız için bir şifre belirleyin. Bu şifre ile giriş yapabilir ve siparişlerinizi takip edebilirsiniz.
          </p>
        </div>

        <div className="card border-0 shadow-sm" style={{ borderRadius: 16 }}>
          <div className="card-body p-4">
            <form onSubmit={handleSubmit}>
              {/* New Password */}
              <div className="mb-3">
                <label className="form-label small fw-medium">Şifre Oluştur</label>
                <div className="input-group">
                  <span className="input-group-text"><FiLock size={16} /></span>
                  <input
                    type={showPassword ? 'text' : 'password'}
                    className="form-control" placeholder="En az 8 karakter"
                    value={password} onChange={e => { setPassword(e.target.value); setError(''); }}
                    autoFocus autoComplete="new-password"
                  />
                  <button type="button" className="btn btn-outline-secondary" onClick={() => setShowPassword(!showPassword)}>
                    {showPassword ? <FiEyeOff size={16} /> : <FiEye size={16} />}
                  </button>
                </div>

                {/* Strength bar */}
                {password && (
                  <div className="mt-2">
                    <div className="d-flex gap-1 mb-1">
                      {[1, 2, 3, 4].map(i => (
                        <div key={i} style={{
                          flex: 1, height: 4, borderRadius: 2,
                          background: strength >= i ? strengthColor : '#e2e8f0',
                          transition: 'background 0.2s',
                        }} />
                      ))}
                    </div>
                    <div className="d-flex justify-content-between">
                      <small style={{ color: strengthColor, fontWeight: 600, fontSize: '0.7rem' }}>{strengthLabel}</small>
                    </div>
                    <div className="mt-1 d-flex flex-wrap gap-2">
                      {[
                        { key: 'length', label: 'Min. 8 karakter' },
                        { key: 'upper', label: 'Büyük harf' },
                        { key: 'lower', label: 'Küçük harf' },
                        { key: 'digit', label: 'Rakam' },
                      ].map(c => (
                        <span key={c.key} style={{
                          fontSize: '0.68rem', padding: '2px 8px', borderRadius: 10,
                          background: checks[c.key] ? '#ecfdf5' : '#f8fafc',
                          color: checks[c.key] ? '#059669' : '#94a3b8',
                          border: `1px solid ${checks[c.key] ? '#a7f3d0' : '#e2e8f0'}`,
                        }}>
                          {checks[c.key] ? '✓' : '○'} {c.label}
                        </span>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              {/* Confirm Password */}
              <div className="mb-3">
                <label className="form-label small fw-medium">Şifre Tekrar</label>
                <div className="input-group">
                  <span className="input-group-text"><FiLock size={16} /></span>
                  <input
                    type={showPassword ? 'text' : 'password'}
                    className="form-control" placeholder="Şifrenizi tekrar girin"
                    value={confirmPassword} onChange={e => { setConfirmPassword(e.target.value); setError(''); }}
                    autoComplete="new-password"
                  />
                </div>
                {confirmPassword && password !== confirmPassword && (
                  <small className="text-danger mt-1 d-block">Şifreler eşleşmiyor.</small>
                )}
              </div>

              {error && (
                <div className="alert alert-danger py-2 small mb-3">
                  <i className="fas fa-exclamation-circle me-1" />{error}
                </div>
              )}

              <button type="submit" className="btn btn-success w-100 py-2" disabled={loading || strength < 3 || password !== confirmPassword}>
                {loading ? (
                  <><span className="spinner-border spinner-border-sm me-2" />İşleniyor...</>
                ) : (
                  <><FiUserCheck className="me-2" />Hesabımı Aktive Et</>
                )}
              </button>
            </form>
          </div>
        </div>

        <div className="text-center mt-3">
          <Link to="/giris" className="text-muted small text-decoration-none">
            <FiArrowLeft size={14} className="me-1" />Giriş sayfasına dön
          </Link>
        </div>
      </div>
    </div>
  );
}
