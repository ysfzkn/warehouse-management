import React, { useState } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { FiLock, FiEye, FiEyeOff, FiCheckCircle, FiXCircle, FiArrowLeft } from 'react-icons/fi';

export default function ResetPasswordPage() {
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
          <p className="text-muted mb-4">Şifre sıfırlama bağlantısı geçersiz veya eksik. Lütfen yeni bir talep oluşturun.</p>
          <Link to="/sifremi-unuttum" className="btn btn-primary px-4">Yeni Sıfırlama Talebi</Link>
        </div>
      </div>
    );
  }

  if (success) {
    return (
      <div className="container d-flex justify-content-center align-items-center" style={{ minHeight: '70vh' }}>
        <div className="text-center" style={{ maxWidth: 440 }}>
          <div style={{
            width: 72, height: 72, borderRadius: '50%',
            background: 'linear-gradient(135deg, #ecfdf5, #d1fae5)',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center', marginBottom: 20,
          }}>
            <FiCheckCircle size={36} color="#059669" />
          </div>
          <h2 className="fw-bold mb-3" style={{ color: '#059669' }}>Şifreniz Değiştirildi!</h2>
          <p className="text-muted mb-4">Yeni şifreniz başarıyla ayarlandı. Artık yeni şifrenizle giriş yapabilirsiniz.</p>
          <button className="btn btn-primary px-4" onClick={() => navigate('/giris')}>
            <FiLock className="me-2" />Giriş Yap
          </button>
        </div>
      </div>
    );
  }

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!password) { setError('Yeni şifre gereklidir.'); return; }
    if (password !== confirmPassword) { setError('Şifreler eşleşmiyor.'); return; }
    if (strength < 3) { setError('Şifre en az 8 karakter, büyük harf, küçük harf ve rakam içermelidir.'); return; }

    setLoading(true);
    try {
      await axios.post('/api/store/auth/reset-password', { token, password });
      setSuccess(true);
    } catch (err) {
      setError(err.response?.data?.message || 'Şifre sıfırlama başarısız. Bağlantının süresi dolmuş olabilir.');
    } finally { setLoading(false); }
  };

  return (
    <div className="container d-flex justify-content-center align-items-center" style={{ minHeight: '70vh' }}>
      <div style={{ width: '100%', maxWidth: 420 }}>
        <div className="text-center mb-4">
          <div style={{
            width: 64, height: 64, borderRadius: '50%',
            background: 'linear-gradient(135deg, #eff6ff, #dbeafe)',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center', marginBottom: 16,
          }}>
            <FiLock size={28} color="#2563eb" />
          </div>
          <h2 className="fw-bold mb-2">Yeni Şifre Belirle</h2>
          <p className="text-muted small">Hesabınız için yeni bir şifre oluşturun.</p>
        </div>

        <div className="card border-0 shadow-sm" style={{ borderRadius: 16 }}>
          <div className="card-body p-4">
            <form onSubmit={handleSubmit}>
              {/* New Password */}
              <div className="mb-3">
                <label className="form-label small fw-medium">Yeni Şifre</label>
                <div className="input-group">
                  <span className="input-group-text"><FiLock size={16} /></span>
                  <input
                    type={showPassword ? 'text' : 'password'}
                    className="form-control" placeholder="Yeni şifreniz"
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

              <button type="submit" className="btn btn-primary w-100 py-2" disabled={loading || strength < 3 || password !== confirmPassword}>
                {loading ? (
                  <><span className="spinner-border spinner-border-sm me-2" />İşleniyor...</>
                ) : (
                  <><FiLock className="me-2" />Şifremi Değiştir</>
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
