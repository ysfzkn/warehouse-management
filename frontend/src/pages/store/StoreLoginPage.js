import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { useToast } from '../../components/store/Toast';

const GOOGLE_CLIENT_ID = process.env.REACT_APP_GOOGLE_CLIENT_ID || '';

export default function StoreLoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [, setError] = useState(''); // display via toast
  const [loading, setLoading] = useState(false);
  const [showPwd, setShowPwd] = useState(false);
  const navigate = useNavigate();
  const toast = useToast();

  const saveAuth = (data) => {
    localStorage.setItem('customer_token', data.token);
    localStorage.setItem('customer_refresh_token', data.refreshToken);
    localStorage.setItem('customer_data', JSON.stringify(data));
    navigate('/');
  };

  const handleSubmit = async (e) => {
    e.preventDefault(); setError(''); setLoading(true);
    try {
      const res = await axios.post('/api/store/auth/login', { email, password });
      saveAuth(res.data);
    } catch (e) {
      const msg = e.response?.data?.message || 'Giriş başarısız. Lütfen bilgilerinizi kontrol edin.';
      setError(msg);
      toast.error(msg);
    } finally { setLoading(false); }
  };

  const handleGoogleLogin = () => {
    if (!GOOGLE_CLIENT_ID) { toast.warning('Google ile giriş şu an kullanılamıyor.'); return; }
    const redirectUri = window.location.origin + '/auth/google/callback';
    const url = `https://accounts.google.com/o/oauth2/v2/auth?client_id=${GOOGLE_CLIENT_ID}&redirect_uri=${encodeURIComponent(redirectUri)}&response_type=code&scope=openid%20email%20profile&access_type=offline&prompt=consent`;
    window.location.href = url;
  };

  return (
    <div className="container py-5" style={{ maxWidth: 460 }}>
      <div className="card border-0 shadow-lg overflow-hidden">
        {/* Header */}
        <div className="text-center text-white py-4" style={{ background: 'linear-gradient(135deg, #2563eb, #1e40af)' }}>
          <i className="fas fa-sign-in-alt fa-2x mb-2" />
          <h4 className="fw-bold mb-1">Giriş Yap</h4>
          <p className="mb-0 small opacity-75">Hesabınıza giriş yaparak alışverişe devam edin</p>
        </div>

        <div className="card-body p-4">
          {/* Google Login */}
          <button type="button" className="btn btn-outline-dark w-100 py-2 mb-3 d-flex align-items-center justify-content-center gap-2" onClick={handleGoogleLogin}>
            <svg width="18" height="18" viewBox="0 0 24 24"><path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" fill="#4285F4"/><path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/><path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/><path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/></svg>
            Google ile Giriş Yap
          </button>

          <div className="d-flex align-items-center gap-3 mb-3">
            <hr className="flex-grow-1" /><span className="text-muted small">veya</span><hr className="flex-grow-1" />
          </div>

          {/* Email Login */}
          <form onSubmit={handleSubmit}>
            <div className="mb-3">
              <label className="form-label small fw-semibold">E-posta Adresi</label>
              <div className="input-group">
                <span className="input-group-text"><i className="fas fa-envelope text-muted" /></span>
                <input type="email" className="form-control" value={email} onChange={e => { setEmail(e.target.value); setError(''); }} required autoFocus placeholder="ornek@email.com" />
              </div>
            </div>
            <div className="mb-4">
              <label className="form-label small fw-semibold">Şifre</label>
              <div className="input-group">
                <span className="input-group-text"><i className="fas fa-lock text-muted" /></span>
                <input type={showPwd ? 'text' : 'password'} className="form-control" value={password} onChange={e => { setPassword(e.target.value); setError(''); }} required placeholder="Şifrenizi girin" />
                <button type="button" className="btn btn-outline-secondary" onClick={() => setShowPwd(!showPwd)}>
                  <i className={`fas fa-eye${showPwd ? '-slash' : ''}`} />
                </button>
              </div>
            </div>
            <div className="d-flex justify-content-end mb-3">
              <Link to="/sifremi-unuttum" className="small text-muted text-decoration-none" style={{ fontSize: '0.8rem' }}>
                Şifremi Unuttum
              </Link>
            </div>
            <button type="submit" className="btn btn-primary w-100 py-2 fw-semibold" disabled={loading}>
              {loading ? <><span className="spinner-border spinner-border-sm me-2" />Giriş yapılıyor...</> : <><i className="fas fa-sign-in-alt me-2" />Giriş Yap</>}
            </button>
          </form>

          <div className="text-center mt-4 pt-3 border-top">
            <span className="text-muted small">Hesabınız yok mu?</span>{' '}
            <Link to="/kayit" className="fw-semibold small">Üye Olun</Link>
          </div>
        </div>
      </div>
    </div>
  );
}
