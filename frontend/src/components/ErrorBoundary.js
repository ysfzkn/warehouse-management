import React from 'react';

/**
 * Top-level React error boundary. Runtime exception'larda beyaz ekran yerine
 * brand-tutarlı bir "bir hata oluştu" sayfası gösterir; ileride Sentry'ye
 * componentDidCatch içinden raporlayabiliriz.
 *
 * Production'da error details kullanıcıya gösterilmez; sadece dev'de.
 */
export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null, info: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, info) {
    // eslint-disable-next-line no-console
    console.error('[ErrorBoundary]', error, info);
    this.setState({ info });
    // Sentry / GlitchTip entegrasyonu burada (Faz 1 batch 20):
    // if (window.Sentry) window.Sentry.captureException(error, { extra: info });
  }

  handleReload = () => {
    try { window.location.reload(); } catch { /* ignore */ }
  };

  handleGoHome = () => {
    try { window.location.assign('/'); } catch { /* ignore */ }
  };

  render() {
    if (!this.state.hasError) return this.props.children;

    const isDev = process.env.NODE_ENV !== 'production';
    return (
      <div style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '2rem',
        backgroundColor: '#f8f9fa',
        fontFamily: 'system-ui, -apple-system, sans-serif',
      }}>
        <div style={{
          maxWidth: 560,
          textAlign: 'center',
          background: 'white',
          padding: '2.5rem',
          borderRadius: 12,
          boxShadow: '0 4px 20px rgba(0,0,0,0.08)',
        }}>
          <div style={{ fontSize: '4rem', marginBottom: '1rem' }}>🛠️</div>
          <h1 style={{ fontSize: '1.5rem', fontWeight: 600, marginBottom: '0.5rem' }}>
            Beklenmedik bir sorun oluştu
          </h1>
          <p style={{ color: '#6b7280', marginBottom: '1.5rem', lineHeight: 1.6 }}>
            Sayfayı yüklerken bir hata ile karşılaştık. Lütfen sayfayı yenileyin
            veya ana sayfaya dönün. Sorun devam ederse bizimle iletişime geçin.
          </p>

          <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'center' }}>
            <button
              onClick={this.handleReload}
              style={{
                background: '#2563eb', color: 'white', border: 'none',
                padding: '0.6rem 1.2rem', borderRadius: 8, fontWeight: 500, cursor: 'pointer',
              }}
            >
              Sayfayı Yenile
            </button>
            <button
              onClick={this.handleGoHome}
              style={{
                background: 'white', color: '#374151', border: '1px solid #d1d5db',
                padding: '0.6rem 1.2rem', borderRadius: 8, fontWeight: 500, cursor: 'pointer',
              }}
            >
              Ana Sayfaya Dön
            </button>
          </div>

          {isDev && this.state.error && (
            <details style={{ marginTop: '1.5rem', textAlign: 'left', fontSize: '0.8rem' }}>
              <summary style={{ cursor: 'pointer', color: '#dc2626' }}>
                Geliştirici detayları (sadece dev)
              </summary>
              <pre style={{
                background: '#fef2f2', padding: '0.75rem', borderRadius: 6,
                overflow: 'auto', marginTop: '0.5rem', fontSize: '0.75rem',
              }}>
                {String(this.state.error?.stack || this.state.error)}
              </pre>
            </details>
          )}
        </div>
      </div>
    );
  }
}
