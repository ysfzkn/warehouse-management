import { useEffect, useState, useCallback } from 'react';
import axios from 'axios';

/**
 * Reads the assistant feature flags (WMS on/off, Store on/off) from the
 * public backend endpoint. Used by AdminLayout and StoreLayout to decide
 * whether to render the widget at all.
 *
 * Initial state is `null` (unknown) — layouts should NOT render the widget
 * until the first successful fetch. On network failure, defaults to false
 * (fail-closed) so a disabled flag actually hides the widget even when
 * the API is unreachable.
 */
export function useAssistantFlags() {
  const [flags, setFlags] = useState({ wmsEnabled: null, storeEnabled: null });
  const [loading, setLoading] = useState(true);

  const reload = useCallback(() => {
    setLoading(true);
    const t0 = performance.now();
    // Cache-bust query param + explicit no-cache headers — some browsers
    // aggressively cache JSON GETs even when the server says no-store.
    axios.get('/api/assistant/flags', {
      params: { _t: Date.now() },
      headers: { 'Cache-Control': 'no-cache', 'Pragma': 'no-cache' },
    })
      .then((resp) => {
        const dt = Math.round(performance.now() - t0);
        if (resp && resp.data) {
          const next = {
            wmsEnabled: resp.data.wmsEnabled !== false,
            storeEnabled: resp.data.storeEnabled !== false,
          };
          console.log(`[AssistantFlags] fetched (${dt}ms):`, next, '— raw:', resp.data);
          setFlags(next);
        } else {
          console.warn('[AssistantFlags] empty response');
        }
      })
      .catch((err) => {
        console.error('[AssistantFlags] fetch failed:', err?.message || err);
        // Fail-closed: if API unreachable, hide widgets. Admin can always
        // re-enable from the dashboard once the backend is up.
        setFlags({ wmsEnabled: false, storeEnabled: false });
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { reload(); }, [reload]);

  useEffect(() => {
    const handler = () => reload();
    window.addEventListener('assistant-flags-changed', handler);
    return () => window.removeEventListener('assistant-flags-changed', handler);
  }, [reload]);

  // Cross-tab sync: refetch when tab regains focus / becomes visible.
  // Covers the case where admin toggles the flag on another tab/subdomain
  // (event dispatch only works within the same window).
  useEffect(() => {
    const onFocus = () => reload();
    const onVisibility = () => { if (document.visibilityState === 'visible') reload(); };
    window.addEventListener('focus', onFocus);
    document.addEventListener('visibilitychange', onVisibility);
    return () => {
      window.removeEventListener('focus', onFocus);
      document.removeEventListener('visibilitychange', onVisibility);
    };
  }, [reload]);

  // Periodic refresh every 60s as a safety net in case the tab never regains
  // focus (e.g. user keeps it open in background).
  useEffect(() => {
    const id = setInterval(reload, 60000);
    return () => clearInterval(id);
  }, [reload]);

  return { flags, loading, reload };
}
