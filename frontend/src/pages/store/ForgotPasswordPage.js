import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';
import { FiMail, FiArrowLeft, FiCheckCircle } from 'react-icons/fi';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email.trim()) { setError('E-posta adresi gereklidir.'); return; }
    setLoading(true); setError('');
    try {
      await axios.post('/api/store/auth/forgot-password', { email: email.trim() });
      setSent(true);
    } catch (err) {
      setError(err.response?.data?.message || 'Bir hata oluştu. Lütfen tekrar deneyin.');
    } finally { setLoading(false); }
  };

  if (sent) {
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
          <h2 className="fw-bold mb-3" style={{ color: '#059669' }}>E-posta Gönderildi</h2>
          <p className="text-muted mb-1" style={{ lineHeight: 1.7 }}>
            <strong>{email}</strong> adresine şifre sıfırlama bağlantısı gönderildi.
          </p>
          <p className="text-muted small mb-4" style={{ lineHeight: 1.7 }}>
            E-postanızı kontrol edin ve bağlantıya tıklayarak yeni şifrenizi belirleyin.
            Bağlantı 1 saat geçerlidir. Spam klasörünü de kontrol etmeyi unutmayın.
          </p>
          <div className="d-flex flex-column gap-2 align-items-center">
            <Link to="/giris" className="btn btn-primary px-4">
              <FiArrowLeft className="me-2" />Giriş Sayfasına Dön
            </Link>
            <button className="btn btn-link btn-sm text-muted" onClick={() => { setSent(false); setEmail(''); }}>
              Farklı bir e-posta adresi dene
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="container d-flex justify-content-center align-items-center" style={{ minHeight: '70vh' }}>
      <div style={{ width: '100%', maxWidth: 420 }}>
        <div className="text-center mb-4">
          <div style={{
            width: 64, height: 64, borderRadius: '50%',
            background: 'linear-gradient(135deg, #eff6ff, #dbeafe)',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center', marginBottom: 16,
          }}>
            <FiMail size={28} color="#2563eb" />
          </div>
          <h2 className="fw-bold mb-2">Şifremi Unuttum</h2>
          <p className="text-muted small">Kayıtlı e-posta adresinizi girin, şifre sıfırlama bağlantısı göndereceğiz.</p>
        </div>

        <div className="card border-0 shadow-sm" style={{ borderRadius: 16 }}>
          <div className="card-body p-4">
            <form onSubmit={handleSubmit}>
              <div className="mb-3">
                <label className="form-label small fw-medium">E-posta Adresi</label>
                <div className="input-group">
                  <span className="input-group-text"><FiMail size={16} /></span>
                  <input
                    type="email" className="form-control" placeholder="ornek@email.com"
                    value={email} onChange={e => { setEmail(e.target.value); setError(''); }}
                    autoFocus autoComplete="email"
                  />
                </div>
              </div>

              {error && (
                <div className="alert alert-danger py-2 small mb-3">
                  <i className="fas fa-exclamation-circle me-1" />{error}
                </div>
              )}

              <button type="submit" className="btn btn-primary w-100 py-2" disabled={loading}>
                {loading ? (
                  <><span className="spinner-border spinner-border-sm me-2" />Gönderiliyor...</>
                ) : (
                  <><FiMail className="me-2" />Sıfırlama Bağlantısı Gönder</>
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
