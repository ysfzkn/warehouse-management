import React, { useState } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';

const CEZERI_LOGO_SRC = '/cezeri-logo.png'; // user will add to frontend/public

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
            {/* Company logo (top) */}
            <div style={styles.logoContainer}>
              <img
                src="/company-logo.png"
                onError={(e) => { e.currentTarget.onerror = null; e.currentTarget.src = '/company-logo.png'; }}
                alt="Şahinler DTM"
                className="brand-logo brand-logo--company"
                style={styles.logo}
              />
            </div>

            {/* Headline (left) + Cezeri logo (right, separate column) */}
            <div className="hero-top-grid">
              <div className="hero-left-col">
                <h1 className="hero-title" style={styles.heroTitle}>
                  Beyaz Eşya Stok
                  <br />
                  <span style={styles.heroTitleAccent}>Yönetim Sistemi</span>
                </h1>

                <p className="hero-subtitle" style={styles.heroSubtitle}>
                  Depo operasyonlarınızı dijitalleştirin, envanter yönetiminizi optimize edin.
                  <span className="hero-ai-line"> Cezeri ile yapay zekâ destekli hızlı arama ve yönlendirme.</span>
                </p>
              </div>

              <div className="hero-right-col" aria-hidden="true">
                <img
                  src={CEZERI_LOGO_SRC}
                  alt="Cezeri"
                  className="hero-cezeri-logo"
                  onError={(e) => { e.currentTarget.style.display = 'none'; }}
                />
              </div>
            </div>

            {/* Feature Cards */}
            <div className="features-grid" style={styles.featuresGrid}>
              <div className="feature-card" style={styles.featureCard}>
                <div className="feature-icon" style={styles.featureIcon}>
                  <i className="fas fa-boxes" style={{fontSize: '20px'}}></i>
                </div>
                <div style={styles.featureText}>
                  <div className="feature-title" style={styles.featureTitle}>Gerçek Zamanlı</div>
                  <div className="feature-subtitle" style={styles.featureSubtitle}>Stok Takibi</div>
                </div>
              </div>
              
              <div className="feature-card" style={styles.featureCard}>
                <div className="feature-icon" style={styles.featureIcon}>
                  <i className="fas fa-warehouse" style={{fontSize: '20px'}}></i>
                </div>
                <div style={styles.featureText}>
                  <div className="feature-title" style={styles.featureTitle}>Çoklu Depo</div>
                  <div className="feature-subtitle" style={styles.featureSubtitle}>Yönetimi</div>
                </div>
              </div>

              <div className="feature-card" style={styles.featureCard}>
                <div className="feature-icon" style={styles.featureIcon}>
                  <i className="fas fa-exchange-alt" style={{fontSize: '20px'}}></i>
                </div>
                <div style={styles.featureText}>
                  <div className="feature-title" style={styles.featureTitle}>Hızlı Transfer</div>
                  <div className="feature-subtitle" style={styles.featureSubtitle}>Operasyonları</div>
                </div>
              </div>

              <div className="feature-card" style={styles.featureCard}>
                <div className="feature-icon" style={styles.featureIcon}>
                  <i className="fas fa-chart-line" style={{fontSize: '20px'}}></i>
                </div>
                <div style={styles.featureText}>
                  <div className="feature-title" style={styles.featureTitle}>Detaylı</div>
                  <div className="feature-subtitle" style={styles.featureSubtitle}>Raporlama</div>
                </div>
              </div>
            </div>

            {/* Floating Icons Animation */}
            <div className="hero-float-layer" aria-hidden="true">
              {floatingIcons.map((item, index) => (
                <i
                  key={index}
                  className={`fas ${item.icon} floating-icon`}
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
        </div>

        {/* Right Section - Login Form */}
        <div className="right-section" style={styles.rightSection}>
          <div className="login-card" style={styles.loginCard}>
            <div className="login-header" style={styles.loginHeader}>
              {/* Icon container with responsive class */}
              <div className="login-icon-container">
                <i className="fas fa-lock login-icon"></i>
              </div>
              <h2 className="login-title" style={styles.loginTitle}>Hoş Geldiniz</h2>
              <p className="login-subtitle" style={styles.loginSubtitle}>Devam etmek için lütfen giriş yapın</p>
            </div>

            {error && (
              <div className="error-alert" style={styles.errorAlert}>
                <i className="fas fa-exclamation-circle me-2"></i>
                {error}
              </div>
            )}

            <form onSubmit={handleSubmit} className="login-form" style={styles.form}>
              <div className="input-group-custom" style={styles.inputGroup}>
                <label className="input-label" style={styles.label}>
                  <i className="fas fa-user me-2"></i>
                  Kullanıcı Adı
                </label>
                <div style={styles.inputWrapper}>
                  <input
                    type="text"
                    className="form-control login-input"
                    style={styles.input}
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    autoComplete="username"
                    placeholder="Kullanıcı adınızı giriniz"
                    required
                    autoFocus
                  />
                  <div className="input-icon-left" style={styles.inputIcon}>
                    <i className="fas fa-user"></i>
                  </div>
                </div>
              </div>

              <div className="input-group-custom" style={styles.inputGroup}>
                <label className="input-label" style={styles.label}>
                  <i className="fas fa-key me-2"></i>
                  Şifre
                </label>
                <div style={styles.inputWrapper}>
                  <input
                    type={showPassword ? 'text' : 'password'}
                    className="form-control login-input"
                    style={styles.input}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    autoComplete="current-password"
                    placeholder="Şifrenizi giriniz"
                    required
                  />
                  <div className="input-icon-left" style={styles.inputIcon}>
                    <i className="fas fa-key"></i>
                  </div>
                  <button
                    type="button"
                    className="password-toggle-btn"
                    style={styles.passwordToggle}
                    onClick={() => setShowPassword(!showPassword)}
                  >
                    <i className={`fas fa-eye${showPassword ? '-slash' : ''}`}></i>
                  </button>
                </div>
              </div>

              <button type="submit" className="submit-btn" style={styles.submitButton} disabled={loading}>
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
            <div className="security-badge" style={styles.securityBadge}>
              <i className="fas fa-shield-alt me-2"></i>
              Güvenli Bağlantı
            </div>
          </div>
        </div>
      </div>

      {/* Inline Styles with Animations */}
      <style>{`
        .hero-ai-line {
          display: inline;
          color: rgba(255,255,255,0.95);
          font-weight: 700;
        }

        /* Hero top grid: fixed left text column + independent right logo column */
        .hero-top-grid {
          display: grid;
          grid-template-columns: minmax(0, 1fr) minmax(240px, 320px);
          gap: 28px;
          align-items: start;
        }
        .hero-left-col {
          min-width: 0;
        }
        .hero-right-col {
          display: flex;
          align-items: flex-start;
          justify-content: flex-end;
          pointer-events: none;
        }
        .hero-cezeri-logo {
          width: 100%;
          height: auto;
          max-height: 220px;
          object-fit: contain;
          filter: drop-shadow(0 16px 34px rgba(0,210,255,0.32));
        }

        /* Clip only floating icons, not the logo */
        .hero-float-layer {
          position: absolute;
          inset: 0;
          overflow: hidden;
          pointer-events: none;
          z-index: 1;
        }
        .hero-top-grid,
        .features-grid {
          position: relative;
          z-index: 2;
        }
        .brand-logo--company {
          position: relative;
          z-index: 3;
        }
        .brand-logo {
          display: block;
          object-fit: contain;
        }
        .brand-logo--company {
          max-height: 56px;
          width: auto;
          filter: drop-shadow(0 4px 12px rgba(0,0,0,0.18));
        }

        @media (max-width: 1250px) {
          .hero-top-grid {
            grid-template-columns: minmax(0, 1fr) minmax(220px, 280px);
            gap: 22px;
          }
          .hero-cezeri-logo {
            max-height: 200px;
          }
        }

        @media (max-width: 1100px) {
          .hero-top-grid {
            grid-template-columns: minmax(0, 1fr) minmax(200px, 240px);
            gap: 18px;
          }
          .hero-cezeri-logo {
            max-height: 180px;
          }
        }

        /* Login Icon Container - Base Sizing */
        .login-icon-container {
          width: 64px;
          height: 64px;
          background: linear-gradient(135deg, #2196f3 0%, #1976d2 50%, #0d47a1 100%);
          border-radius: 16px;
          display: flex;
          align-items: center;
          justify-content: center;
          margin: 0 auto 18px;
          box-shadow: 0 6px 24px rgba(33,150,243,0.45);
        }

        .login-icon {
          font-size: 26px;
          color: #ffffff;
        }

        @keyframes gradientShift {
          0% { background-position: 0% 50%; }
          50% { background-position: 100% 50%; }
          100% { background-position: 0% 50%; }
        }

        @keyframes float {
          0%, 100% { transform: translateY(0px) rotate(0deg); opacity: 0.12; }
          50% { transform: translateY(-15px) rotate(5deg); opacity: 0.2; }
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
        .feature-card:hover {
          transform: translateY(-3px);
          background: linear-gradient(135deg, rgba(255,255,255,0.18) 0%, rgba(77,195,247,0.12) 100%) !important;
          box-shadow: 0 10px 28px rgba(0,210,255,0.25);
        }

        /* Input focus effects */
        .login-input:focus {
          border-color: #2196f3 !important;
          box-shadow: 0 0 0 3px rgba(33,150,243,0.12) !important;
        }

        /* Button hover effects */
        .submit-btn:not(:disabled):hover {
          transform: translateY(-2px);
          background: linear-gradient(135deg, #42a5f5 0%, #2196f3 50%, #1976d2 100%) !important;
          box-shadow: 0 10px 32px rgba(33,150,243,0.55) !important;
        }

        .submit-btn:not(:disabled):active {
          transform: translateY(0);
          box-shadow: 0 6px 24px rgba(33,150,243,0.45) !important;
        }

        .submit-btn:disabled {
          opacity: 0.7;
          cursor: not-allowed;
        }

        /* Password toggle hover */
        .password-toggle-btn:hover {
          color: #495057 !important;
        }

        /* ===== LAPTOP SCREENS (14" - 15.6" typically 1366x768 to 1920x1080) ===== */
        @media (min-width: 1001px) and (max-width: 1600px) {
          .hero-title {
            font-size: 38px !important;
            margin-bottom: 14px !important;
          }
          .hero-subtitle {
            font-size: 15px !important;
            margin-bottom: 32px !important;
            max-width: 420px !important;
          }
          .features-grid {
            gap: 14px !important;
            margin-top: 28px !important;
          }
          .feature-card {
            padding: 16px !important;
            border-radius: 12px !important;
          }
          .feature-icon {
            width: 42px !important;
            height: 42px !important;
            border-radius: 10px !important;
          }
          .feature-icon i {
            font-size: 17px !important;
          }
          .feature-title {
            font-size: 14px !important;
          }
          .feature-subtitle {
            font-size: 12px !important;
          }
          .floating-icon {
            font-size: 36px !important;
          }
          .left-section {
            padding: 40px !important;
          }
          .right-section {
            padding: 30px !important;
          }
          .login-card {
            padding: 36px !important;
            max-width: 380px !important;
            border-radius: 20px !important;
          }
          .login-icon-container {
            width: 56px !important;
            height: 56px !important;
            border-radius: 14px !important;
            margin-bottom: 16px !important;
          }
          .login-icon {
            font-size: 23px !important;
          }
          .login-title {
            font-size: 24px !important;
            margin-bottom: 6px !important;
          }
          .login-subtitle {
            font-size: 13px !important;
          }
          .login-header {
            margin-bottom: 28px !important;
          }
          .input-group-custom {
            margin-bottom: 18px !important;
          }
          .input-label {
            font-size: 12px !important;
            margin-bottom: 7px !important;
          }
          .login-input {
            padding: 11px 40px !important;
            font-size: 14px !important;
            border-radius: 10px !important;
          }
          .input-icon-left {
            left: 13px !important;
            font-size: 14px !important;
          }
          .password-toggle-btn {
            right: 12px !important;
            font-size: 14px !important;
          }
          .submit-btn {
            padding: 12px !important;
            font-size: 14px !important;
            border-radius: 10px !important;
            margin-top: 6px !important;
          }
          .security-badge {
            font-size: 11px !important;
            padding: 10px !important;
            margin-top: 18px !important;
            border-radius: 7px !important;
          }
          .error-alert {
            padding: 12px 14px !important;
            font-size: 12px !important;
            margin-bottom: 18px !important;
            border-radius: 10px !important;
          }
          .login-form {
            margin-bottom: 18px !important;
          }
        }

        /* ===== SMALL LAPTOP (13" - 14" at 1366x768) ===== */
        @media (min-width: 1001px) and (max-width: 1400px) {
          .hero-title {
            font-size: 34px !important;
            margin-bottom: 12px !important;
          }
          .hero-subtitle {
            font-size: 14px !important;
            margin-bottom: 28px !important;
            max-width: 380px !important;
          }
          .features-grid {
            gap: 12px !important;
            margin-top: 24px !important;
          }
          .feature-card {
            padding: 14px !important;
          }
          .feature-icon {
            width: 38px !important;
            height: 38px !important;
          }
          .feature-icon i {
            font-size: 15px !important;
          }
          .feature-title {
            font-size: 13px !important;
          }
          .feature-subtitle {
            font-size: 11px !important;
          }
          .floating-icon {
            font-size: 32px !important;
          }
          .left-section {
            padding: 32px !important;
          }
          .right-section {
            padding: 24px !important;
          }
          .login-card {
            padding: 32px !important;
            max-width: 360px !important;
            border-radius: 18px !important;
          }
          .login-icon-container {
            width: 50px !important;
            height: 50px !important;
            border-radius: 12px !important;
            margin-bottom: 14px !important;
          }
          .login-icon {
            font-size: 20px !important;
          }
          .login-title {
            font-size: 22px !important;
            margin-bottom: 5px !important;
          }
          .login-subtitle {
            font-size: 12px !important;
          }
          .login-header {
            margin-bottom: 24px !important;
          }
          .input-group-custom {
            margin-bottom: 16px !important;
          }
          .input-label {
            font-size: 11px !important;
            margin-bottom: 6px !important;
          }
          .login-input {
            padding: 10px 38px !important;
            font-size: 13px !important;
            border-radius: 9px !important;
          }
          .input-icon-left {
            left: 12px !important;
            font-size: 13px !important;
          }
          .password-toggle-btn {
            right: 10px !important;
            font-size: 13px !important;
          }
          .submit-btn {
            padding: 11px !important;
            font-size: 13px !important;
            border-radius: 9px !important;
            margin-top: 4px !important;
          }
          .security-badge {
            font-size: 10px !important;
            padding: 9px !important;
            margin-top: 16px !important;
          }
          .error-alert {
            padding: 10px 12px !important;
            font-size: 11px !important;
            margin-bottom: 16px !important;
          }
          .login-form {
            margin-bottom: 16px !important;
          }
        }

        /* ===== VERY SMALL LAPTOP / LOW RES (1280x720 - 1366x768) ===== */
        @media (min-width: 1001px) and (max-width: 1300px) and (max-height: 800px) {
          .hero-title {
            font-size: 30px !important;
          }
          .hero-subtitle {
            font-size: 13px !important;
            margin-bottom: 24px !important;
          }
          .features-grid {
            gap: 10px !important;
            margin-top: 20px !important;
          }
          .feature-card {
            padding: 12px !important;
          }
          .feature-icon {
            width: 34px !important;
            height: 34px !important;
          }
          .feature-icon i {
            font-size: 14px !important;
          }
          .feature-title {
            font-size: 12px !important;
          }
          .feature-subtitle {
            font-size: 10px !important;
          }
          .left-section {
            padding: 24px !important;
          }
          .right-section {
            padding: 20px !important;
          }
          .login-card {
            padding: 28px !important;
            max-width: 340px !important;
          }
          .login-icon-container {
            width: 46px !important;
            height: 46px !important;
            margin-bottom: 12px !important;
          }
          .login-icon {
            font-size: 18px !important;
          }
          .login-title {
            font-size: 20px !important;
          }
          .login-subtitle {
            font-size: 11px !important;
          }
          .login-header {
            margin-bottom: 20px !important;
          }
          .input-group-custom {
            margin-bottom: 14px !important;
          }
          .login-input {
            padding: 9px 36px !important;
            font-size: 13px !important;
          }
          .submit-btn {
            padding: 10px !important;
            font-size: 13px !important;
          }
          .security-badge {
            margin-top: 14px !important;
          }
        }

        /* ===== TABLET TO MOBILE TRANSITION ===== */
        @media (max-width: 1000px) {
          .left-section { 
            display: none !important; 
          }
          .brand-logo--company {
            max-height: 48px;
          }
          .brand-logo--cezeri {
            width: 124px;
            height: 124px;
          }
          .brand-divider {
            height: 52px;
            margin: 0 8px;
          }
          .right-section { 
            width: 100% !important; 
            max-width: 100% !important;
            padding: 30px 20px !important;
            flex: 1 1 100% !important;
          }
          .login-card {
            max-width: 440px !important;
            padding: 40px 32px !important;
          }
          .login-icon-container {
            width: 56px !important;
            height: 56px !important;
            margin-bottom: 18px !important;
            border-radius: 14px !important;
          }
          .login-icon {
            font-size: 24px !important;
          }
          .login-title {
            font-size: 26px !important;
          }
          .login-subtitle {
            font-size: 14px !important;
          }
        }

        /* ===== STANDARD MOBILE ===== */
        @media (max-width: 768px) {
          .right-section {
            padding: 24px 16px !important;
          }
          .login-card {
            padding: 32px 24px !important;
            max-width: 100% !important;
            border-radius: 18px !important;
          }
          .login-header {
            margin-bottom: 28px !important;
          }
          .login-title {
            font-size: 24px !important;
            margin-bottom: 6px !important;
          }
          .login-subtitle {
            font-size: 13px !important;
          }
          .login-icon-container {
            width: 52px !important;
            height: 52px !important;
            margin-bottom: 16px !important;
            border-radius: 13px !important;
          }
          .login-icon {
            font-size: 22px !important;
          }
          .login-input {
            padding: 12px 42px !important;
            font-size: 16px !important;
          }
          .input-icon-left {
            left: 14px !important;
            font-size: 14px !important;
          }
          .password-toggle-btn {
            right: 12px !important;
            font-size: 14px !important;
            padding: 8px !important;
            min-width: 36px !important;
            min-height: 36px !important;
          }
          .submit-btn {
            padding: 14px !important;
            font-size: 15px !important;
            min-height: 48px !important;
          }
          .input-label {
            font-size: 12px !important;
            margin-bottom: 7px !important;
          }
          .input-group-custom {
            margin-bottom: 20px !important;
          }
          .error-alert {
            padding: 11px 14px !important;
            font-size: 12px !important;
            margin-bottom: 20px !important;
          }
          .security-badge {
            font-size: 11px !important;
            padding: 10px !important;
            margin-top: 20px !important;
          }
        }

        /* ===== SMALL MOBILE ===== */
        @media (max-width: 576px) {
          .brand-divider {
            height: 48px;
            margin: 0 6px;
          }
          .brand-logo--company {
            max-height: 44px;
          }
          .brand-logo--cezeri {
            width: 112px;
            height: 112px;
          }
          .right-section {
            padding: 20px 14px !important;
          }
          .login-card {
            padding: 28px 22px !important;
            border-radius: 16px !important;
            box-shadow: 0 10px 40px rgba(0,0,0,0.25) !important;
          }
          .login-header {
            margin-bottom: 24px !important;
          }
          .login-title {
            font-size: 22px !important;
            margin-bottom: 5px !important;
          }
          .login-subtitle {
            font-size: 12px !important;
          }
          .login-icon-container {
            width: 48px !important;
            height: 48px !important;
            border-radius: 12px !important;
            margin-bottom: 14px !important;
          }
          .login-icon {
            font-size: 20px !important;
          }
          .login-input {
            padding: 12px 40px !important;
            font-size: 16px !important;
            border-radius: 10px !important;
          }
          .input-icon-left {
            left: 13px !important;
            font-size: 13px !important;
          }
          .password-toggle-btn {
            right: 10px !important;
            font-size: 14px !important;
          }
          .submit-btn {
            padding: 13px !important;
            font-size: 15px !important;
            min-height: 46px !important;
            border-radius: 10px !important;
          }
          .input-group-custom {
            margin-bottom: 18px !important;
          }
          .login-form {
            margin-bottom: 18px !important;
          }
          .error-alert {
            padding: 10px 12px !important;
            font-size: 12px !important;
            margin-bottom: 18px !important;
            border-radius: 10px !important;
          }
          .security-badge {
            font-size: 10px !important;
            padding: 9px !important;
            margin-top: 18px !important;
            border-radius: 8px !important;
          }
        }

        /* ===== EXTRA SMALL MOBILE (iPhone SE, etc.) ===== */
        @media (max-width: 400px) {
          .right-section {
            padding: 16px 12px !important;
          }
          .login-card {
            padding: 24px 18px !important;
            border-radius: 14px !important;
          }
          .login-header {
            margin-bottom: 20px !important;
          }
          .login-title {
            font-size: 20px !important;
            margin-bottom: 4px !important;
          }
          .login-subtitle {
            font-size: 11px !important;
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
          .login-input {
            padding: 11px 38px !important;
            font-size: 16px !important;
          }
          .input-icon-left {
            left: 12px !important;
            font-size: 13px !important;
          }
          .password-toggle-btn {
            right: 9px !important;
            font-size: 13px !important;
            padding: 7px !important;
            min-width: 34px !important;
            min-height: 34px !important;
          }
          .submit-btn {
            padding: 12px !important;
            font-size: 14px !important;
            min-height: 44px !important;
          }
          .input-group-custom {
            margin-bottom: 16px !important;
          }
          .error-alert {
            padding: 9px 11px !important;
            font-size: 11px !important;
            margin-bottom: 16px !important;
          }
          .security-badge {
            font-size: 10px !important;
            padding: 8px !important;
            margin-top: 16px !important;
          }
        }

        /* ===== VERY SMALL MOBILE (< 360px) ===== */
        @media (max-width: 360px) {
          .right-section {
            padding: 14px 10px !important;
          }
          .login-card {
            padding: 22px 16px !important;
          }
          .login-header {
            margin-bottom: 18px !important;
          }
          .login-title {
            font-size: 19px !important;
          }
          .login-subtitle {
            font-size: 11px !important;
          }
          .login-icon-container {
            width: 42px !important;
            height: 42px !important;
            border-radius: 10px !important;
            margin-bottom: 11px !important;
          }
          .login-icon {
            font-size: 17px !important;
          }
          .login-input {
            padding: 10px 36px !important;
          }
          .input-icon-left {
            left: 11px !important;
          }
          .password-toggle-btn {
            right: 8px !important;
            padding: 6px !important;
            min-width: 32px !important;
            min-height: 32px !important;
          }
          .submit-btn {
            padding: 11px !important;
            font-size: 14px !important;
            min-height: 42px !important;
          }
          .input-group-custom {
            margin-bottom: 14px !important;
          }
        }
      `}</style>
    </div>
  );
};

// Inline styles object - Base (for large screens 1600px+)
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
    maxWidth: '1300px',
    minHeight: '100vh',
    margin: '0 auto',
    padding: '16px 0',
    flexWrap: 'nowrap',
    alignItems: 'center',
  },
  leftSection: {
    flex: '0 0 55%',
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'center',
    padding: '48px',
    position: 'relative',
    overflow: 'hidden',
    animation: 'slideInLeft 0.8s ease-out',
  },
  heroContent: {
    position: 'relative',
    zIndex: 2,
  },
  logoContainer: {
    marginBottom: '32px',
    animation: 'fadeIn 1s ease-out',
  },
  logo: {
    maxHeight: '64px',
    width: 'auto',
    filter: 'drop-shadow(0 4px 12px rgba(0,0,0,0.2))',
  },
  heroTitle: {
    fontSize: '44px',
    fontWeight: '800',
    color: '#ffffff',
    marginBottom: '16px',
    lineHeight: '1.2',
    textShadow: '0 2px 16px rgba(0,0,0,0.18)',
    animation: 'fadeIn 1.2s ease-out',
  },
  heroTitleAccent: {
    background: 'linear-gradient(135deg, #ffffff 0%, #4fc3f7 50%, #29b6f6 100%)',
    WebkitBackgroundClip: 'text',
    WebkitTextFillColor: 'transparent',
    backgroundClip: 'text',
  },
  heroSubtitle: {
    fontSize: '16px',
    color: 'rgba(255,255,255,0.9)',
    marginBottom: '36px',
    maxWidth: '460px',
    lineHeight: '1.6',
    animation: 'fadeIn 1.4s ease-out',
  },
  cezeriBadge: {
    animation: 'fadeIn 1.1s ease-out',
  },
  featuresGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(2, 1fr)',
    gap: '16px',
    marginTop: '32px',
    animation: 'fadeIn 1.6s ease-out',
  },
  featureCard: {
    background: 'rgba(255,255,255,0.1)',
    backdropFilter: 'blur(12px)',
    borderRadius: '14px',
    padding: '18px',
    display: 'flex',
    alignItems: 'center',
    gap: '14px',
    border: '1px solid rgba(255,255,255,0.2)',
    boxShadow: '0 6px 24px rgba(0,0,0,0.08)',
    transition: 'all 0.3s ease',
    cursor: 'default',
  },
  featureIcon: {
    width: '46px',
    height: '46px',
    borderRadius: '10px',
    background: 'linear-gradient(135deg, rgba(255,255,255,0.25) 0%, rgba(77,195,247,0.25) 100%)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: '#ffffff',
    flexShrink: 0,
    boxShadow: '0 4px 10px rgba(0,0,0,0.12)',
  },
  featureText: {
    flex: 1,
  },
  featureTitle: {
    fontSize: '15px',
    fontWeight: '700',
    color: '#ffffff',
    marginBottom: '3px',
  },
  featureSubtitle: {
    fontSize: '12px',
    color: 'rgba(255,255,255,0.8)',
  },
  floatingIcon: {
    position: 'absolute',
    fontSize: '40px',
    color: 'rgba(255,255,255,0.12)',
    animation: 'float 3s ease-in-out infinite',
    pointerEvents: 'none',
  },
  rightSection: {
    flex: '0 0 45%',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '32px',
    animation: 'slideInRight 0.8s ease-out',
    width: '100%',
  },
  loginCard: {
    background: '#ffffff',
    borderRadius: '20px',
    padding: '40px',
    width: '100%',
    maxWidth: '400px',
    boxShadow: '0 16px 48px rgba(0,0,0,0.25)',
  },
  loginHeader: {
    textAlign: 'center',
    marginBottom: '32px',
  },
  loginTitle: {
    fontSize: '26px',
    fontWeight: '700',
    color: '#1a1a2e',
    marginBottom: '6px',
  },
  loginSubtitle: {
    fontSize: '14px',
    color: '#6c757d',
  },
  errorAlert: {
    background: 'linear-gradient(135deg, #f44336 0%, #e53935 50%, #d32f2f 100%)',
    color: '#ffffff',
    padding: '14px 16px',
    borderRadius: '10px',
    marginBottom: '20px',
    fontSize: '13px',
    fontWeight: '500',
    display: 'flex',
    alignItems: 'center',
    boxShadow: '0 4px 14px rgba(244,67,54,0.35)',
  },
  form: {
    marginBottom: '20px',
  },
  inputGroup: {
    marginBottom: '20px',
  },
  label: {
    display: 'block',
    fontSize: '13px',
    fontWeight: '600',
    color: '#495057',
    marginBottom: '8px',
  },
  inputWrapper: {
    position: 'relative',
  },
  input: {
    width: '100%',
    padding: '12px 42px',
    fontSize: '14px',
    borderRadius: '10px',
    border: '2px solid #e9ecef',
    transition: 'all 0.3s ease',
    outline: 'none',
  },
  inputIcon: {
    position: 'absolute',
    left: '14px',
    top: '50%',
    transform: 'translateY(-50%)',
    color: '#adb5bd',
    fontSize: '14px',
    pointerEvents: 'none',
  },
  passwordToggle: {
    position: 'absolute',
    right: '14px',
    top: '50%',
    transform: 'translateY(-50%)',
    background: 'none',
    border: 'none',
    color: '#6c757d',
    cursor: 'pointer',
    padding: '4px 8px',
    fontSize: '14px',
    transition: 'color 0.2s ease',
  },
  submitButton: {
    width: '100%',
    padding: '13px',
    fontSize: '14px',
    fontWeight: '600',
    color: '#ffffff',
    background: 'linear-gradient(135deg, #2196f3 0%, #1e88e5 50%, #1565c0 100%)',
    border: 'none',
    borderRadius: '10px',
    cursor: 'pointer',
    transition: 'all 0.3s ease',
    boxShadow: '0 6px 24px rgba(33,150,243,0.35)',
    marginTop: '6px',
  },
  securityBadge: {
    textAlign: 'center',
    fontSize: '12px',
    color: '#6c757d',
    padding: '10px',
    background: '#f8f9fa',
    borderRadius: '8px',
    marginTop: '20px',
  },
};

export default Login;