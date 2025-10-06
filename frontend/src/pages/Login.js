import React, { useState } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';

const Login = () => {
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const res = await axios.post('/api/auth/login', { username, password });
      localStorage.setItem('auth_token', res.data.token);
      localStorage.setItem('auth_user', res.data.username);
      navigate('/');
    } catch (err) {
      setError('Kullanıcı adı veya şifre hatalı');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      className="d-flex align-items-center justify-content-center"
      style={{ minHeight: '100vh', background: 'linear-gradient(135deg, #0d6efd 0%, #0a4275 100%)' }}
    >
      <div className="card border-0 shadow-lg" style={{ maxWidth: 440, width: '100%', borderRadius: 16 }}>
        <div className="card-body p-4 p-md-5">
          <div className="text-center mb-4">
            <img
              src="/sahinler-logo.png"
              onError={(e) => { e.currentTarget.onerror = null; e.currentTarget.src = '/sahinler-logo.png'; }}
              alt="Şahinler DTM"
              style={{ maxHeight: 72, width: 'auto' }}
            />
            <div className="mt-2 fw-semibold text-primary" style={{ letterSpacing: 1 }}>
              <span className="text-dark">Şahinler</span> DTM
            </div>
          </div>

          <h5 className="mb-3 text-center">
            <i className="fas fa-lock me-2 text-primary"></i>
            Yönetim Paneli Girişi
          </h5>

          {error && (
            <div className="alert alert-danger" role="alert">{error}</div>
          )}

          <form onSubmit={handleSubmit}>
            <div className="mb-3">
              <label className="form-label">Kullanıcı Adı</label>
              <div className="input-group">
                <span className="input-group-text"><i className="fas fa-user"></i></span>
                <input
                  type="text"
                  className="form-control"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  autoComplete="username"
                  required
                  autoFocus
                />
              </div>
            </div>

            <div className="mb-4">
              <label className="form-label">Şifre</label>
              <div className="input-group">
                <span className="input-group-text"><i className="fas fa-key"></i></span>
                <input
                  type="password"
                  className="form-control"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="current-password"
                  required
                />
              </div>
            </div>

            <button type="submit" className="btn btn-primary w-100" disabled={loading}>
              {loading ? (
                <>
                  <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                  Giriş yapılıyor...
                </>
              ) : (
                <>
                  <i className="fas fa-sign-in-alt me-2"></i>
                  Giriş Yap
                </>
              )}
            </button>
          </form>

        </div>
      </div>
    </div>
  );
};

export default Login;


