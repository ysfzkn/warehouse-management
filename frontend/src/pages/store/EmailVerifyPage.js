import React, { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import axios from 'axios';

export default function EmailVerifyPage() {
  const [searchParams] = useSearchParams();
  const [status, setStatus] = useState('loading'); // loading | success | error
  const [message, setMessage] = useState('');

  useEffect(() => {
    const token = searchParams.get('token');
    if (!token) {
      setStatus('error');
      setMessage('Doğrulama bağlantısı geçersiz.');
      return;
    }

    axios.post('/api/store/auth/verify-email', { token })
      .then(() => setStatus('success'))
      .catch(e => {
        setStatus('error');
        setMessage(e.response?.data?.message || 'Doğrulama başarısız oldu. Bağlantının süresi dolmuş olabilir.');
      });
  }, [searchParams]);

  return (
    <div className="container py-5" style={{ maxWidth: 480 }}>
      <div className="card border-0 shadow-lg">
        <div className="card-body text-center p-5">
          {status === 'loading' && (
            <>
              <div className="spinner-border text-primary mb-4" style={{ width: 48, height: 48 }} />
              <h5 className="fw-bold">Hesabınız Doğrulanıyor...</h5>
              <p className="text-muted">Lütfen bekleyin.</p>
            </>
          )}

          {status === 'success' && (
            <>
              <div className="mb-4">
                <div style={{ width: 80, height: 80, borderRadius: '50%', background: 'linear-gradient(135deg, #10b981, #059669)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>
                  <i className="fas fa-check text-white fa-2x" />
                </div>
              </div>
              <h4 className="fw-bold text-success mb-3">Hesabınız Doğrulandı!</h4>
              <p className="text-muted mb-4">
                E-posta adresiniz başarıyla doğrulandı. Artık hesabınızla giriş yapabilir ve alışverişe başlayabilirsiniz.
              </p>
              <div className="d-grid gap-2">
                <Link to="/giris" className="btn btn-primary btn-lg">
                  <i className="fas fa-sign-in-alt me-2" />Giriş Yap
                </Link>
                <Link to="/" className="btn btn-outline-secondary">
                  Mağazaya Git
                </Link>
              </div>
            </>
          )}

          {status === 'error' && (
            <>
              <div className="mb-4">
                <div style={{ width: 80, height: 80, borderRadius: '50%', background: 'linear-gradient(135deg, #ef4444, #dc2626)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>
                  <i className="fas fa-times text-white fa-2x" />
                </div>
              </div>
              <h4 className="fw-bold text-danger mb-3">Doğrulama Başarısız</h4>
              <p className="text-muted mb-4">{message}</p>
              <div className="d-grid gap-2">
                <Link to="/kayit" className="btn btn-primary">
                  Tekrar Üye Ol
                </Link>
                <Link to="/" className="btn btn-outline-secondary">
                  Mağazaya Git
                </Link>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
