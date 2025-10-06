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
    <div className="d-flex align-items-center justify-content-center" style={{ minHeight: '80vh' }}>
      <div className="card shadow" style={{ maxWidth: 420, width: '100%' }}>
        <div className="card-body p-4">
          <h3 className="mb-3 text-center">
            <i className="fas fa-lock me-2"></i>
            Giriş Yap
          </h3>
          {error && (
            <div className="alert alert-danger" role="alert">{error}</div>
          )}
          <form onSubmit={handleSubmit}>
            <div className="mb-3">
              <label className="form-label">Kullanıcı Adı</label>
              <input
                type="text"
                className="form-control"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                autoFocus
              />
            </div>
            <div className="mb-3">
              <label className="form-label">Şifre</label>
              <input
                type="password"
                className="form-control"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
            <button type="submit" className="btn btn-primary w-100" disabled={loading}>
              {loading ? 'Giriş yapılıyor...' : 'Giriş Yap'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};

export default Login;


