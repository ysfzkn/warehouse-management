import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';

const STORAGE_KEY = 'cookie_consent';

/**
 * KVKK & 6563 compliant cookie consent banner. Shows on first visit,
 * remembers preference in localStorage. Three categories:
 * - Zorunlu (always on, not toggleable)
 * - Analitik (opt-in)
 * - Pazarlama (opt-in)
 */
export default function CookieBanner() {
  const [visible, setVisible] = useState(false);
  const [showDetails, setShowDetails] = useState(false);
  const [prefs, setPrefs] = useState({ necessary: true, analytics: false, marketing: false });

  useEffect(() => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (!stored) {
        setVisible(true);
      }
    } catch { /* private browsing */ }
  }, []);

  const save = (consent) => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...consent, ts: Date.now() }));
      // Aynı sekme içinde AnalyticsScripts vb. abonelerin haberdar olması için
      // (storage event'i sadece DİĞER sekmelere yayılır).
      window.dispatchEvent(new CustomEvent('cookie-consent-changed', { detail: consent }));
    } catch { /* ignore */ }
    setVisible(false);
  };

  const acceptAll = () => save({ necessary: true, analytics: true, marketing: true });
  const acceptNecessary = () => save({ necessary: true, analytics: false, marketing: false });
  const acceptCustom = () => save(prefs);

  if (!visible) return null;

  return (
    <div style={{
      position: 'fixed', bottom: 0, left: 0, right: 0, zIndex: 9999,
      background: '#1a1a2e', color: '#e0e0e0', padding: '16px 20px',
      boxShadow: '0 -4px 20px rgba(0,0,0,0.3)', fontSize: 14,
    }}>
      <div className="container">
        <div className="d-flex flex-column flex-md-row align-items-start gap-3">
          <div style={{ flex: 1 }}>
            <strong style={{ fontSize: 15 }}>Çerez Kullanımı</strong>
            <p className="mb-2 mt-1" style={{ lineHeight: 1.5, opacity: 0.85 }}>
              Bu web sitesi, deneyiminizi iyileştirmek için çerezler kullanmaktadır.
              Zorunlu çerezler sitenin çalışması için gereklidir. Analitik ve pazarlama
              çerezleri yalnızca onayınızla etkinleştirilir.{' '}
              <Link to="/sayfa/cerez-politikasi" style={{ color: '#90caf9' }}>Çerez Politikası</Link>
            </p>

            {showDetails && (
              <div className="mt-2 mb-2" style={{ background: 'rgba(255,255,255,0.06)', borderRadius: 8, padding: '12px 16px' }}>
                <div className="d-flex align-items-center mb-2">
                  <input type="checkbox" checked disabled className="me-2" />
                  <div>
                    <strong>Zorunlu Çerezler</strong>
                    <div style={{ fontSize: 12, opacity: 0.7 }}>Oturum, sepet, güvenlik — devre dışı bırakılamaz</div>
                  </div>
                </div>
                <div className="d-flex align-items-center mb-2">
                  <input type="checkbox" checked={prefs.analytics} onChange={e => setPrefs(p => ({ ...p, analytics: e.target.checked }))} className="me-2" />
                  <div>
                    <strong>Analitik Çerezler</strong>
                    <div style={{ fontSize: 12, opacity: 0.7 }}>Ziyaretçi istatistikleri ve site performansı ölçümü</div>
                  </div>
                </div>
                <div className="d-flex align-items-center">
                  <input type="checkbox" checked={prefs.marketing} onChange={e => setPrefs(p => ({ ...p, marketing: e.target.checked }))} className="me-2" />
                  <div>
                    <strong>Pazarlama Çerezleri</strong>
                    <div style={{ fontSize: 12, opacity: 0.7 }}>Kişiselleştirilmiş reklamlar ve yeniden hedefleme</div>
                  </div>
                </div>
              </div>
            )}
          </div>

          <div className="d-flex flex-column gap-2" style={{ minWidth: 200 }}>
            <button onClick={acceptAll} className="btn btn-sm btn-light fw-semibold">
              Tümünü Kabul Et
            </button>
            <button onClick={acceptNecessary} className="btn btn-sm btn-outline-light">
              Sadece Zorunlu
            </button>
            {!showDetails ? (
              <button onClick={() => setShowDetails(true)} className="btn btn-sm btn-link text-light" style={{ fontSize: 12 }}>
                Tercihlerimi Ayarla
              </button>
            ) : (
              <button onClick={acceptCustom} className="btn btn-sm btn-outline-light">
                Seçimlerimi Kaydet
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
