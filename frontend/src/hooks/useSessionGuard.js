import { useEffect, useRef, useState, useCallback } from 'react';

/**
 * Session guard — proactive session management.
 *
 * Three separate mechanisms in one place:
 *   1. JWT expiry tracking — reads the token's `exp` field, shows a warning before it expires,
 *      and triggers cross-tab logout as soon as it does.
 *   2. Idle timeout — forces logout if the user shows no activity for N minutes
 *      (a security layer for tabs left idle).
 *   3. Cross-tab synchronization — logging out in one tab instantly logs out the others
 *      (storage event).
 *
 * Usage:
 *   const { warningOpen, secondsLeft, dismiss, logoutNow } = useSessionGuard({
 *     tokenKey: 'auth_token',
 *     loginPath: '/login',
 *     idleMinutes: 30,       // inactivity timeout
 *     warnBeforeSeconds: 120 // show modal 2 min before JWT expiry
 *   });
 *
 * The returned values are used to render a <SessionWarningModal />.
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

    // Single source, multi-tab: clear localStorage → storage event notifies other tabs
    try {
      localStorage.removeItem(tokenKey);
      localStorage.removeItem(`${tokenKey}_refresh_token`);
      extraKeysToClear.forEach(k => localStorage.removeItem(k));
      // Logout signal — other tabs are listening
      localStorage.setItem('__session_logout_signal', String(Date.now()));
    } catch { /* storage disabled */ }

    // Redirect only if not already on the login page
    if (window.location.pathname !== loginPath) {
      const next = encodeURIComponent(window.location.pathname + window.location.search);
      window.location.href = `${loginPath}?reason=${reason}&next=${next}`;
    }
  }, [tokenKey, loginPath, extraKeysToClear]);

  const dismiss = useCallback(() => {
    // User clicked "Continue" — update activity, close the modal
    lastActivityRef.current = Date.now();
    setWarningOpen(false);
  }, []);

  // Activity tracker — only resets the timer, does not trigger a render
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
      // If the token was removed (manual, extension, etc.) → logout
      if (ev.key === tokenKey && ev.newValue === null && ev.oldValue) {
        logoutNow('token-removed');
      }
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, [tokenKey, loginPath, logoutNow]);

  // Main loop: check expiry + idle every second
  useEffect(() => {
    const refreshExpiry = () => {
      const token = localStorage.getItem(tokenKey);
      expiryMsRef.current = token ? decodeJwtExp(token) : null;
    };
    refreshExpiry();

    const tick = () => {
      // If there's no token (login page) do nothing
      const token = localStorage.getItem(tokenKey);
      if (!token) { setWarningOpen(false); return; }

      // Did exp change? (new login or refresh)
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

      // 2) JWT exp tracking
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
    tick(); // initial run
    return () => clearInterval(tickRef.current);
  }, [tokenKey, idleMinutes, warnBeforeSeconds, logoutNow, warningOpen]);

  return { warningOpen, secondsLeft, dismiss, logoutNow };
}
