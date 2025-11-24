import React, { useState } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';

const Login = () => {
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [showPassword, setShowPassword] = useState(false);

  // Animated floating icons
  const floatingIcons = [
    { icon: 'fa-refrigerator', delay: 0, duration: 3 },
    { icon: 'fa-blender', delay: 0.5, duration: 3.5 },
    { icon: 'fa-tv', delay: 1, duration: 4 },
    { icon: 'fa-washing-machine', delay: 1.5, duration: 3.2 },
    { icon: 'fa-fan', delay: 2, duration: 3.8 },
  ];

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const res = await axios.post('/api/auth/login', { username, password });
      localStorage.setItem('auth_token', res.data.token);
      localStorage.setItem('auth_user', res.data.username);
      if (res.data.role) localStorage.setItem('auth_role', res.data.role);
      window.dispatchEvent(new Event('auth-changed'));
      navigate('/');
    } catch (err) {
      setError('Kullanıcı adı veya şifre hatalı');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={styles.container}>
      {/* Animated Background Gradient */}
      <div style={styles.animatedBg}></div>

      {/* Main Content */}
      <div style={styles.content}>
        {/* Left Section - Hero with animated icons */}
        <div className="left-section" style={styles.leftSection}>
          <div style={styles.heroContent}>
            {/* Logo */}
            <div style={styles.logoContainer}>
              <img
                src="/company-logo.png"
                onError={(e) => { e.currentTarget.onerror = null; e.currentTarget.src = '/company-logo.png'; }}
                alt="Şahinler DTM"
                style={styles.logo}
              />
            </div>

            {/* Title */}
            <h1 style={styles.heroTitle}>
              Beyaz Eşya Stok
              <br />
              <span style={styles.heroTitleAccent}>Yönetim Sistemi</span>
            </h1>

            <p style={styles.heroSubtitle}>
              Depo operasyonlarınızı dijitalleştirin, envanter yönetiminizi optimize edin
            </p>

            {/* Feature Cards */}
            <div style={styles.featuresGrid}>
              <div style={styles.featureCard}>
                <div style={styles.featureIcon}>
                  <i className="fas fa-boxes" style={{fontSize: '24px'}}></i>
                </div>
                <div style={styles.featureText}>
                  <div style={styles.featureTitle}>Gerçek Zamanlı</div>
                  <div style={styles.featureSubtitle}>Stok Takibi</div>
                </div>
              </div>
              
              <div style={styles.featureCard}>
                <div style={styles.featureIcon}>
                  <i className="fas fa-warehouse" style={{fontSize: '24px'}}></i>
                </div>
                <div style={styles.featureText}>
                  <div style={styles.featureTitle}>Çoklu Depo</div>
                  <div style={styles.featureSubtitle}>Yönetimi</div>
                </div>
              </div>

              <div style={styles.featureCard}>
                <div style={styles.featureIcon}>
                  <i className="fas fa-exchange-alt" style={{fontSize: '24px'}}></i>
                </div>
                <div style={styles.featureText}>
                  <div style={styles.featureTitle}>Hızlı Transfer</div>
                  <div style={styles.featureSubtitle}>Operasyonları</div>
                </div>
              </div>

              <div style={styles.featureCard}>
                <div style={styles.featureIcon}>
                  <i className="fas fa-chart-line" style={{fontSize: '24px'}}></i>
                </div>
                <div style={styles.featureText}>
                  <div style={styles.featureTitle}>Detaylı</div>
                  <div style={styles.featureSubtitle}>Raporlama</div>
                </div>
              </div>
            </div>

            {/* Floating Icons Animation */}
            {floatingIcons.map((item, index) => (
              <i
                key={index}
                className={`fas ${item.icon}`}
                style={{
                  ...styles.floatingIcon,
                  left: `${15 + index * 18}%`,
                  top: `${20 + (index % 3) * 25}%`,
                  animationDelay: `${item.delay}s`,
                  animationDuration: `${item.duration}s`,
                }}
              ></i>
            ))}
          </div>
        </div>

        {/* Right Section - Login Form */}
        <div className="right-section" style={styles.rightSection}>
          <div style={styles.loginCard}>
            <div style={styles.loginHeader}>
              {/* Icon container with responsive class */}
              <div className="login-icon-container">
                <i className="fas fa-lock login-icon"></i>
              </div>
              <h2 style={styles.loginTitle}>Hoş Geldiniz</h2>
              <p style={styles.loginSubtitle}>Devam etmek için lütfen giriş yapın</p>
            </div>

            {error && (
              <div style={styles.errorAlert}>
                <i className="fas fa-exclamation-circle me-2"></i>
                {error}
              </div>
            )}

            <form onSubmit={handleSubmit} style={styles.form}>
              <div style={styles.inputGroup}>
                <label style={styles.label}>
                  <i className="fas fa-user me-2"></i>
                  Kullanıcı Adı
                </label>
                <div style={styles.inputWrapper}>
                  <input
                    type="text"
                    className="form-control"
                    style={styles.input}
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    autoComplete="username"
                    placeholder="Kullanıcı adınızı giriniz"
                    required
                    autoFocus
                  />
                  <div style={styles.inputIcon}>
                    <i className="fas fa-user"></i>
                  </div>
                </div>
              </div>

              <div style={styles.inputGroup}>
                <label style={styles.label}>
                  <i className="fas fa-key me-2"></i>
                  Şifre
                </label>
                <div style={styles.inputWrapper}>
                  <input
                    type={showPassword ? 'text' : 'password'}
                    className="form-control"
                    style={styles.input}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    autoComplete="current-password"
                    placeholder="Şifrenizi giriniz"
                    required
                  />
                  <div style={styles.inputIcon}>
                    <i className="fas fa-key"></i>
                  </div>
                  <button
                    type="button"
                    style={styles.passwordToggle}
                    onClick={() => setShowPassword(!showPassword)}
                  >
                    <i className={`fas fa-eye${showPassword ? '-slash' : ''}`}></i>
                  </button>
                </div>
              </div>

              <button type="submit" style={styles.submitButton} disabled={loading}>
                {loading ? (
                  <>
                    <span className="spinner-border spinner-border-sm me-2" role="status"></span>
                    Giriş Yapılıyor...
                  </>
                ) : (
                  <>
                    <i className="fas fa-sign-in-alt me-2"></i>
                    Giriş Yap
                  </>
                )}
              </button>
            </form>

            {/* Security Badge */}
            <div style={styles.securityBadge}>
              <i className="fas fa-shield-alt me-2"></i>
              Güvenli Bağlantı
            </div>
          </div>
        </div>
      </div>

      {/* Inline Styles with Animations */}
      <style>{`
        /* Login Icon Container - Responsive Sizing */
        .login-icon-container {
          width: 80px;
          height: 80px;
          background: linear-gradient(135deg, #2196f3 0%, #1976d2 50%, #0d47a1 100%);
          border-radius: 20px;
          display: flex;
          align-items: center;
          justify-content: center;
          margin: 0 auto 24px;
          box-shadow: 0 8px 32px rgba(33,150,243,0.5);
        }

        .login-icon {
          font-size: 32px;
          color: #ffffff;
        }

        @keyframes gradientShift {
          0% { background-position: 0% 50%; }
          50% { background-position: 100% 50%; }
          100% { background-position: 0% 50%; }
        }

        @keyframes float {
          0%, 100% { transform: translateY(0px) rotate(0deg); opacity: 0.15; }
          50% { transform: translateY(-20px) rotate(5deg); opacity: 0.25; }
        }

        @keyframes slideInLeft {
          from { opacity: 0; transform: translateX(-30px); }
          to { opacity: 1; transform: translateX(0); }
        }

        @keyframes slideInRight {
          from { opacity: 0; transform: translateX(30px); }
          to { opacity: 1; transform: translateX(0); }
        }

        @keyframes fadeIn {
          from { opacity: 0; }
          to { opacity: 1; }
        }

        /* Feature card hover effect */
        [style*="featureCard"]:hover {
          transform: translateY(-4px);
          background: linear-gradient(135deg, rgba(255,255,255,0.2) 0%, rgba(77,195,247,0.15) 100%) !important;
          box-shadow: 0 12px 32px rgba(0,210,255,0.3);
        }

        /* Input focus effects */
        input:focus {
          border-color: #2196f3 !important;
          box-shadow: 0 0 0 4px rgba(33,150,243,0.15) !important;
        }

        /* Button hover effects */
        button[type="submit"]:not(:disabled):hover {
          transform: translateY(-2px);
          background: linear-gradient(135deg, #42a5f5 0%, #2196f3 50%, #1976d2 100%) !important;
          box-shadow: 0 12px 40px rgba(33,150,243,0.6) !important;
        }

        button[type="submit"]:not(:disabled):active {
          transform: translateY(0);
          box-shadow: 0 8px 32px rgba(33,150,243,0.5) !important;
        }

        button[type="submit"]:disabled {
          opacity: 0.7;
          cursor: not-allowed;
        }

        /* Password toggle hover */
        button[style*="passwordToggle"]:hover {
          color: #495057 !important;
        }

        /* Tablet to Mobile transition */
        @media (max-width: 1000px) {
          .left-section { 
            display: none !important; 
          }
          .right-section { 
            width: 100% !important; 
            max-width: 100% !important;
            padding: 30px 20px !important;
            flex: 1 1 100% !important;
          }
          [style*="content"] {
            padding: 0 !important;
            min-height: 100vh !important;
            align-items: center !important;
            justify-content: center !important;
          }
          [style*="loginCard"] {
            max-width: 500px !important;
          }
          .login-icon-container {
            width: 60px !important;
            height: 60px !important;
            margin-bottom: 20px !important;
            border-radius: 15px !important;
          }
          .login-icon {
            font-size: 26px !important;
          }
        }

        /* Standard Mobile */
        @media (max-width: 768px) {
          .right-section {
            padding: 24px 16px !important;
          }
          [style*="loginCard"] {
            padding: 36px 28px !important;
            max-width: 100% !important;
            border-radius: 20px !important;
          }
          [style*="loginHeader"] {
            margin-bottom: 32px !important;
          }
          [style*="loginTitle"] {
            font-size: 26px !important;
            margin-bottom: 8px !important;
          }
          [style*="loginSubtitle"] {
            font-size: 14px !important;
          }
          .login-icon-container {
            width: 54px !important;
            height: 54px !important;
            margin-bottom: 18px !important;
            border-radius: 14px !important;
          }
          .login-icon {
            font-size: 23px !important;
          }
          [style*="input"] {
            padding: 13px 46px 13px 42px !important;
            font-size: 16px !important;
          }
          [style*="inputIcon"] {
            left: 14px !important;
            font-size: 15px !important;
          }
          [style*="passwordToggle"] {
            right: 14px !important;
            font-size: 15px !important;
            padding: 8px !important;
            min-width: 38px !important;
            min-height: 38px !important;
          }
          [style*="submitButton"] {
            padding: 15px !important;
            font-size: 16px !important;
            min-height: 50px !important;
          }
          [style*="label"] {
            font-size: 13px !important;
            margin-bottom: 8px !important;
          }
          [style*="inputGroup"] {
            margin-bottom: 22px !important;
          }
          [style*="errorAlert"] {
            padding: 12px 16px !important;
            font-size: 13px !important;
            margin-bottom: 22px !important;
          }
          [style*="securityBadge"] {
            font-size: 12px !important;
            padding: 10px !important;
            margin-top: 22px !important;
          }
        }

        /* Small Mobile */
        @media (max-width: 576px) {
          [style*="container"] {
            padding: 0 !important;
            overflow-x: hidden !important;
          }
          [style*="content"] {
            padding: 0 !important;
            margin: 0 !important;
          }
          .right-section {
            padding: 20px 14px !important;
          }
          [style*="loginCard"] {
            padding: 32px 24px !important;
            border-radius: 18px !important;
            box-shadow: 0 10px 40px rgba(0,0,0,0.25) !important;
          }
          [style*="loginHeader"] {
            margin-bottom: 28px !important;
          }
          [style*="loginTitle"] {
            font-size: 24px !important;
            margin-bottom: 6px !important;
          }
          [style*="loginSubtitle"] {
            font-size: 13px !important;
          }
          .login-icon-container {
            width: 50px !important;
            height: 50px !important;
            border-radius: 13px !important;
            margin-bottom: 16px !important;
          }
          .login-icon {
            font-size: 21px !important;
          }
          [style*="input"] {
            padding: 13px 46px 13px 40px !important;
            font-size: 16px !important;
            border-radius: 11px !important;
          }
          [style*="inputIcon"] {
            left: 13px !important;
            font-size: 14px !important;
          }
          [style*="passwordToggle"] {
            right: 12px !important;
            font-size: 15px !important;
            padding: 8px !important;
            min-width: 38px !important;
            min-height: 38px !important;
          }
          [style*="submitButton"] {
            padding: 15px !important;
            font-size: 16px !important;
            min-height: 50px !important;
            border-radius: 11px !important;
          }
          [style*="label"] {
            font-size: 13px !important;
            margin-bottom: 8px !important;
          }
          [style*="inputGroup"] {
            margin-bottom: 20px !important;
          }
          [style*="form"] {
            margin-bottom: 20px !important;
          }
          [style*="errorAlert"] {
            padding: 12px 14px !important;
            font-size: 13px !important;
            margin-bottom: 20px !important;
            border-radius: 10px !important;
          }
          [style*="securityBadge"] {
            font-size: 11px !important;
            padding: 10px !important;
            margin-top: 20px !important;
            border-radius: 9px !important;
          }
        }

        /* Extra Small Mobile (iPhone SE, etc.) */
        @media (max-width: 400px) {
          .right-section {
            padding: 16px 12px !important;
          }
          [style*="loginCard"] {
            padding: 28px 20px !important;
            border-radius: 16px !important;
          }
          [style*="loginHeader"] {
            margin-bottom: 24px !important;
          }
          [style*="loginTitle"] {
            font-size: 22px !important;
            margin-bottom: 6px !important;
          }
          [style*="loginSubtitle"] {
            font-size: 12px !important;
          }
          .login-icon-container {
            width: 46px !important;
            height: 46px !important;
            border-radius: 12px !important;
            margin-bottom: 14px !important;
          }
          .login-icon {
            font-size: 19px !important;
          }
          [style*="input"] {
            padding: 12px 44px 12px 38px !important;
            font-size: 16px !important;
          }
          [style*="inputIcon"] {
            left: 12px !important;
            font-size: 14px !important;
          }
          [style*="passwordToggle"] {
            right: 10px !important;
            font-size: 14px !important;
            padding: 7px !important;
            min-width: 36px !important;
            min-height: 36px !important;
          }
          [style*="submitButton"] {
            padding: 14px !important;
            font-size: 15px !important;
            min-height: 48px !important;
          }
          [style*="inputGroup"] {
            margin-bottom: 18px !important;
          }
          [style*="errorAlert"] {
            padding: 11px 13px !important;
            font-size: 12px !important;
            margin-bottom: 18px !important;
          }
          [style*="securityBadge"] {
            font-size: 11px !important;
            padding: 9px !important;
            margin-top: 18px !important;
          }
        }

        /* Very Small Mobile (< 360px) */
        @media (max-width: 360px) {
          .right-section {
            padding: 14px 10px !important;
          }
          [style*="loginCard"] {
            padding: 24px 16px !important;
          }
          [style*="loginHeader"] {
            margin-bottom: 20px !important;
          }
          [style*="loginTitle"] {
            font-size: 20px !important;
            margin-bottom: 5px !important;
          }
          [style*="loginSubtitle"] {
            font-size: 12px !important;
          }
          .login-icon-container {
            width: 44px !important;
            height: 44px !important;
            border-radius: 11px !important;
            margin-bottom: 12px !important;
          }
          .login-icon {
            font-size: 18px !important;
          }
          [style*="input"] {
            padding: 12px 42px 12px 36px !important;
          }
          [style*="inputIcon"] {
            left: 11px !important;
          }
          [style*="passwordToggle"] {
            right: 9px !important;
            padding: 6px !important;
            min-width: 34px !important;
            min-height: 34px !important;
          }
          [style*="submitButton"] {
            padding: 13px !important;
            font-size: 15px !important;
            min-height: 46px !important;
          }
          [style*="inputGroup"] {
            margin-bottom: 16px !important;
          }
        }
      `}</style>
    </div>
  );
};

// Inline styles object
const styles = {
  container: {
    position: 'relative',
    minHeight: '100vh',
    height: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'auto',
    padding: '0',
    width: '100%',
  },
  animatedBg: {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    width: '100%',
    height: '100%',
    background: 'linear-gradient(-45deg, #1e3c72, #2a5298, #3a7bd5, #00d2ff)',
    backgroundSize: '400% 400%',
    animation: 'gradientShift 15s ease infinite',
    zIndex: 0,
  },
  content: {
    position: 'relative',
    zIndex: 1,
    display: 'flex',
    width: '100%',
    maxWidth: '1400px',
    minHeight: '100vh',
    margin: '0 auto',
    padding: '20px 0',
    flexWrap: 'wrap',
  },
  leftSection: {
    flex: '0 0 55%',
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'center',
    padding: '60px',
    position: 'relative',
    overflow: 'hidden',
    animation: 'slideInLeft 0.8s ease-out',
  },
  heroContent: {
    position: 'relative',
    zIndex: 2,
  },
  logoContainer: {
    marginBottom: '40px',
    animation: 'fadeIn 1s ease-out',
  },
  logo: {
    maxHeight: '80px',
    width: 'auto',
    filter: 'drop-shadow(0 4px 12px rgba(0,0,0,0.2))',
  },
  heroTitle: {
    fontSize: '56px',
    fontWeight: '800',
    color: '#ffffff',
    marginBottom: '20px',
    lineHeight: '1.2',
    textShadow: '0 2px 20px rgba(0,0,0,0.2)',
    animation: 'fadeIn 1.2s ease-out',
  },
  heroTitleAccent: {
    background: 'linear-gradient(135deg, #ffffff 0%, #4fc3f7 50%, #29b6f6 100%)',
    WebkitBackgroundClip: 'text',
    WebkitTextFillColor: 'transparent',
    backgroundClip: 'text',
  },
  heroSubtitle: {
    fontSize: '20px',
    color: 'rgba(255,255,255,0.9)',
    marginBottom: '50px',
    maxWidth: '540px',
    lineHeight: '1.6',
    animation: 'fadeIn 1.4s ease-out',
  },
  featuresGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(2, 1fr)',
    gap: '20px',
    marginTop: '40px',
    animation: 'fadeIn 1.6s ease-out',
  },
  featureCard: {
    background: 'rgba(255,255,255,0.12)',
    backdropFilter: 'blur(12px)',
    borderRadius: '16px',
    padding: '24px',
    display: 'flex',
    alignItems: 'center',
    gap: '16px',
    border: '1px solid rgba(255,255,255,0.25)',
    boxShadow: '0 8px 32px rgba(0,0,0,0.1)',
    transition: 'all 0.3s ease',
    cursor: 'default',
  },
  featureIcon: {
    width: '56px',
    height: '56px',
    borderRadius: '12px',
    background: 'linear-gradient(135deg, rgba(255,255,255,0.3) 0%, rgba(77,195,247,0.3) 100%)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: '#ffffff',
    flexShrink: 0,
    boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
  },
  featureText: {
    flex: 1,
  },
  featureTitle: {
    fontSize: '18px',
    fontWeight: '700',
    color: '#ffffff',
    marginBottom: '4px',
  },
  featureSubtitle: {
    fontSize: '14px',
    color: 'rgba(255,255,255,0.8)',
  },
  floatingIcon: {
    position: 'absolute',
    fontSize: '48px',
    color: 'rgba(255,255,255,0.15)',
    animation: 'float 3s ease-in-out infinite',
    pointerEvents: 'none',
  },
  rightSection: {
    flex: '0 0 45%',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '40px',
    animation: 'slideInRight 0.8s ease-out',
    width: '100%',
  },
  loginCard: {
    background: '#ffffff',
    borderRadius: '24px',
    padding: '50px',
    width: '100%',
    maxWidth: '480px',
    boxShadow: '0 20px 60px rgba(0,0,0,0.3)',
  },
  loginHeader: {
    textAlign: 'center',
    marginBottom: '40px',
  },
  loginTitle: {
    fontSize: '32px',
    fontWeight: '700',
    color: '#1a1a2e',
    marginBottom: '8px',
  },
  loginSubtitle: {
    fontSize: '16px',
    color: '#6c757d',
  },
  errorAlert: {
    background: 'linear-gradient(135deg, #f44336 0%, #e53935 50%, #d32f2f 100%)',
    color: '#ffffff',
    padding: '16px 20px',
    borderRadius: '12px',
    marginBottom: '24px',
    fontSize: '14px',
    fontWeight: '500',
    display: 'flex',
    alignItems: 'center',
    boxShadow: '0 4px 16px rgba(244,67,54,0.4)',
  },
  form: {
    marginBottom: '24px',
  },
  inputGroup: {
    marginBottom: '24px',
  },
  label: {
    display: 'block',
    fontSize: '14px',
    fontWeight: '600',
    color: '#495057',
    marginBottom: '10px',
  },
  inputWrapper: {
    position: 'relative',
  },
  input: {
    width: '100%',
    padding: '14px 48px 14px 48px',
    fontSize: '15px',
    borderRadius: '12px',
    border: '2px solid #e9ecef',
    transition: 'all 0.3s ease',
    outline: 'none',
  },
  inputIcon: {
    position: 'absolute',
    left: '16px',
    top: '50%',
    transform: 'translateY(-50%)',
    color: '#adb5bd',
    fontSize: '16px',
    pointerEvents: 'none',
  },
  passwordToggle: {
    position: 'absolute',
    right: '16px',
    top: '50%',
    transform: 'translateY(-50%)',
    background: 'none',
    border: 'none',
    color: '#6c757d',
    cursor: 'pointer',
    padding: '4px 8px',
    fontSize: '16px',
    transition: 'color 0.2s ease',
  },
  submitButton: {
    width: '100%',
    padding: '16px',
    fontSize: '16px',
    fontWeight: '600',
    color: '#ffffff',
    background: 'linear-gradient(135deg, #2196f3 0%, #1e88e5 50%, #1565c0 100%)',
    border: 'none',
    borderRadius: '12px',
    cursor: 'pointer',
    transition: 'all 0.3s ease',
    boxShadow: '0 8px 32px rgba(33,150,243,0.4)',
    marginTop: '8px',
  },
  securityBadge: {
    textAlign: 'center',
    fontSize: '13px',
    color: '#6c757d',
    padding: '12px',
    background: '#f8f9fa',
    borderRadius: '8px',
    marginTop: '24px',
  },
};

export default Login;