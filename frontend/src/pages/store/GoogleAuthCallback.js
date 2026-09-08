import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import { consumeGoogleState, googleRedirectUri } from '../../utils/googleAuth';

export default function GoogleAuthCallback() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [error, setError] = useState('');

  // StrictMode runs effects twice in development, and a Google authorization code is
  // single-use — the second exchange would always fail and paint an error over a
  // sign-in that actually succeeded. It would also burn the stored state.
  const exchanged = useRef(false);

  useEffect(() => {
    if (exchanged.current) return;
    exchanged.current = true;

    const code = searchParams.get('code');
    const returnedState = searchParams.get('state');
    const expectedState = consumeGoogleState();

    // Google reports a refused consent screen here rather than by failing the redirect.
    const denied = searchParams.get('error');
    if (denied) {
      setError(
        denied === 'access_denied' ? 'Google ile giriş iptal edildi.' : 'Google girişi tamamlanamadı.'
      );
      return;
    }
    if (!code) {
      setError('Google doğrulama kodu alınamadı.');
      return;
    }
    // No match means this callback was not started by this tab — a forged sign-in
    // attempt, a replayed URL, or a stale bookmark. None of them should mint a session.
    if (!expectedState || returnedState !== expectedState) {
      setError('Güvenlik doğrulaması başarısız. Lütfen giriş sayfasından tekrar deneyin.');
      return;
    }

    axios
      .post('/api/store/auth/google', { code, redirectUri: googleRedirectUri() })
      .then((res) => {
        localStorage.setItem('customer_token', res.data.token);
        localStorage.setItem('customer_refresh_token', res.data.refreshToken);
        localStorage.setItem('customer_data', JSON.stringify(res.data));
        navigate('/');
      })
      .catch((e) => {
        setError(e.response?.data?.message || 'Google ile giriş başarısız oldu.');
      });
  }, [searchParams, navigate]);

  if (error) {
    return (
      <div className="container py-5" style={{ maxWidth: 440 }}>
        <div className="card border-0 shadow-lg">
          <div className="card-body text-center p-5">
            <div className="text-danger mb-3">
              <i className="fas fa-exclamation-circle fa-3x" />
            </div>
            <h5 className="fw-bold mb-3">Giriş Başarısız</h5>
            <p className="text-muted mb-4">{error}</p>
            <a href="/giris" className="btn btn-primary">
              Giriş Sayfasına Dön
            </a>
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
