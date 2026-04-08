import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import axios from 'axios';

export default function GoogleAuthCallback() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [error, setError] = useState('');

  useEffect(() => {
    const code = searchParams.get('code');
    if (!code) {
      setError('Google doğrulama kodu alınamadı.');
      return;
    }

    const redirectUri = window.location.origin + '/auth/google/callback';

    axios.post('/api/store/auth/google', { code, redirectUri })
      .then(res => {
        localStorage.setItem('customer_token', res.data.token);
        localStorage.setItem('customer_refresh_token', res.data.refreshToken);
        localStorage.setItem('customer_data', JSON.stringify(res.data));
        navigate('/');
      })
      .catch(e => {
        setError(e.response?.data?.message || 'Google ile giriş başarısız oldu.');
      });
  }, [searchParams, navigate]);

  if (error) {
    return (
      <div className="container py-5" style={{ maxWidth: 440 }}>
        <div className="card border-0 shadow-lg">
          <div className="card-body text-center p-5">
            <div className="text-danger mb-3"><i className="fas fa-exclamation-circle fa-3x" /></div>
            <h5 className="fw-bold mb-3">Giriş Başarısız</h5>
            <p className="text-muted mb-4">{error}</p>
            <a href="/giris" className="btn btn-primary">Giriş Sayfasına Dön</a>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="container py-5 text-center">
      <div className="spinner-border text-primary mb-3" />
      <p className="text-muted">Google hesabınız doğrulanıyor...</p>
    </div>
  );
}
