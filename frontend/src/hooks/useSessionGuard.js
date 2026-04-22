import { useEffect, useRef, useState, useCallback } from 'react';

/**
 * Session guard — proaktif oturum yönetimi.
 *
 * Üç ayrı mekanizma tek bir yerde:
 *   1. JWT expiry takibi — token'ın `exp` alanını okur, süresi dolmadan önce uyarı gösterir,
 *      süre biter bitmez cross-tab logout tetikler.
 *   2. Idle timeout — kullanıcı N dakika hiçbir aktivite göstermediyse zorla çıkış yapar
 *      (boşta bırakılmış sekmeler için güvenlik katmanı).
 *   3. Cross-tab senkronizasyon — bir sekmede çıkış yapınca diğer sekmeler de anında
 *      logout olur (storage event).
 *
 * Kullanım:
 *   const { warningOpen, secondsLeft, dismiss, logoutNow } = useSessionGuard({
 *     tokenKey: 'auth_token',
 *     loginPath: '/login',
 *     idleMinutes: 30,       // inactivity timeout
 *     warnBeforeSeconds: 120 // show modal 2 min before JWT expiry
 *   });
 *
 * Dönen değerlerle bir <SessionWarningModal /> render edilir.
 */

function decodeJwtExp(token) {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')));
    return typeof payload.exp === 'number' ? payload.exp * 1000 : null;
  } catch {
    return null;
  }
}

export function useSessionGuard({
  tokenKey = 'auth_token',
  loginPath = '/login',
  idleMinutes = 30,
  warnBeforeSeconds = 120,
  extraKeysToClear = [],
} = {}) {
  const [warningOpen, setWarningOpen] = useState(false);
  const [secondsLeft, setSecondsLeft] = useState(0);

  const lastActivityRef = useRef(Date.now());
  const expiryMsRef = useRef(null);
  const tickRef = useRef(null);
  const loggedOutRef = useRef(false);

  const logoutNow = useCallback((reason = 'manual') => {
    if (loggedOutRef.current) return;
    loggedOutRef.current = true;

    // Tek kaynak, çok sekme: localStorage'ı temizle → storage event diğer sekmeleri uyarır
    try {
      localStorage.removeItem(tokenKey);
      localStorage.removeItem(`${tokenKey}_refresh_token`);
      extraKeysToClear.forEach(k => localStorage.removeItem(k));
      // Logout signal — diğer sekmeler dinliyor
      localStorage.setItem('__session_logout_signal', String(Date.now()));
    } catch { /* storage disabled */ }

    // Eğer zaten login sayfasındaysa yönlendirme
    if (window.location.pathname !== loginPath) {
      const next = encodeURIComponent(window.location.pathname + window.location.search);
      window.location.href = `${loginPath}?reason=${reason}&next=${next}`;
    }
  }, [tokenKey, loginPath, extraKeysToClear]);

  const dismiss = useCallback(() => {
    // Kullanıcı "Devam Et" dedi — aktiviteyi güncelle, modalı kapat
    lastActivityRef.current = Date.now();
    setWarningOpen(false);
  }, []);

  // Activity tracker — sadece timer'ı sıfırlar, render tetiklemez
  useEffect(() => {
    const onActivity = () => { lastActivityRef.current = Date.now(); };
    const opts = { passive: true, capture: true };
    const events = ['mousemove', 'keydown', 'click', 'scroll', 'touchstart'];
    events.forEach(e => window.addEventListener(e, onActivity, opts));
    return () => events.forEach(e => window.removeEventListener(e, onActivity, opts));
  }, []);

  // Cross-tab logout signal
  useEffect(() => {
    const onStorage = (ev) => {
      if (ev.key === '__session_logout_signal') {
        loggedOutRef.current = true;
        if (window.location.pathname !== loginPath) {
          window.location.href = `${loginPath}?reason=cross-tab`;
        }
      }
      // Token silindiyse (manuel, uzantı, vs) → logout
      if (ev.key === tokenKey && ev.newValue === null && ev.oldValue) {
        logoutNow('token-removed');
      }
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, [tokenKey, loginPath, logoutNow]);

  // Ana döngü: her saniye expiry + idle kontrol
  useEffect(() => {
    const refreshExpiry = () => {
      const token = localStorage.getItem(tokenKey);
      expiryMsRef.current = token ? decodeJwtExp(token) : null;
    };
    refreshExpiry();

    const tick = () => {
      // Token yoksa (login sayfası) hiçbir şey yapma
      const token = localStorage.getItem(tokenKey);
      if (!token) { setWarningOpen(false); return; }

      // Exp değişti mi? (yeni login veya refresh)
      const currentExp = decodeJwtExp(token);
      if (currentExp && currentExp !== expiryMsRef.current) {
        expiryMsRef.current = currentExp;
        loggedOutRef.current = false;
      }

      const now = Date.now();
      const exp = expiryMsRef.current;
      const idleMs = idleMinutes * 60 * 1000;
      const idleFor = now - lastActivityRef.current;

      // 1) Idle timeout
      if (idleFor > idleMs) {
        logoutNow('idle');
        return;
      }

      // 2) JWT exp takibi
      if (exp) {
        const msLeft = exp - now;
        if (msLeft <= 0) {
          logoutNow('expired');
          return;
        }
        if (msLeft <= warnBeforeSeconds * 1000) {
          setWarningOpen(true);
          setSecondsLeft(Math.ceil(msLeft / 1000));
        } else if (warningOpen) {
          setWarningOpen(false);
        }
      }
    };

    tickRef.current = setInterval(tick, 1000);
    tick(); // ilk run
    return () => clearInterval(tickRef.current);
  }, [tokenKey, idleMinutes, warnBeforeSeconds, logoutNow, warningOpen]);

  return { warningOpen, secondsLeft, dismiss, logoutNow };
}
