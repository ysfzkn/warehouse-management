import React from 'react';

/**
 * Oturum süresi dolmadan ~2 dakika önce gösterilen uyarı diyaloğu.
 * Geri sayım + "Devam Et" / "Çıkış Yap" butonları.
 */
export default function SessionWarningModal({ open, secondsLeft, onContinue, onLogout }) {
  if (!open) return null;

  const mm = Math.floor(secondsLeft / 60);
  const ss = secondsLeft % 60;
  const display = `${String(mm).padStart(2, '0')}:${String(ss).padStart(2, '0')}`;

  // secondsLeft 30 altına düşünce kırmızı vurgu
  const urgent = secondsLeft <= 30;

  return (
    <div
      style={{
        position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.55)',
        zIndex: 9998, display: 'flex', alignItems: 'center', justifyContent: 'center',
        padding: 16,
      }}
      role="dialog" aria-modal="true" aria-labelledby="session-warning-title"
    >
      <div style={{
        background: '#fff', borderRadius: 12, maxWidth: 420, width: '100%',
        boxShadow: '0 20px 60px rgba(0,0,0,0.25)', overflow: 'hidden',
      }}>
        <div style={{
          padding: '20px 24px', borderBottom: '1px solid #e5e7eb',
          background: urgent ? '#fef2f2' : '#fffbeb',
          color: urgent ? '#991b1b' : '#854d0e',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <i className={`fas ${urgent ? 'fa-exclamation-circle' : 'fa-hourglass-half'}`}
               style={{ fontSize: 22 }}></i>
            <h5 id="session-warning-title" style={{ margin: 0, fontSize: 17, fontWeight: 600 }}>
              Oturum Süresi Dolmak Üzere
            </h5>
          </div>
        </div>

        <div style={{ padding: '20px 24px', textAlign: 'center' }}>
          <div style={{
            fontSize: 36, fontWeight: 700, letterSpacing: 1,
            color: urgent ? '#dc2626' : '#111827',
            fontVariantNumeric: 'tabular-nums',
            marginBottom: 8,
          }}>
            {display}
          </div>
          <p style={{ color: '#4b5563', margin: 0, fontSize: 14, lineHeight: 1.5 }}>
            Güvenliğiniz için yakında otomatik olarak çıkış yapılacak.
            <br />
            Çalışmaya devam etmek için oturumu uzatın.
          </p>
        </div>

        <div style={{
          display: 'flex', gap: 8, padding: '12px 16px',
          borderTop: '1px solid #e5e7eb', background: '#f9fafb',
        }}>
          <button
            type="button"
            onClick={onLogout}
            style={{
              flex: 1, padding: '10px 14px', borderRadius: 8,
              border: '1px solid #d1d5db', background: '#fff',
              color: '#374151', fontWeight: 500, cursor: 'pointer',
            }}
          >
            Çıkış Yap
          </button>
          <button
            type="button"
            onClick={onContinue}
            autoFocus
            style={{
              flex: 2, padding: '10px 14px', borderRadius: 8,
              border: '1px solid #2563eb', background: '#2563eb',
              color: '#fff', fontWeight: 600, cursor: 'pointer',
            }}
          >
            Oturuma Devam Et
          </button>
        </div>
      </div>
    </div>
  );
}
