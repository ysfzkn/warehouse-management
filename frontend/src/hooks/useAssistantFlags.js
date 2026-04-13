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
    axios.get('/api/assistant/flags')
      .then((resp) => {
        if (resp && resp.data) {
          setFlags({
            wmsEnabled: resp.data.wmsEnabled !== false,
            storeEnabled: resp.data.storeEnabled !== false,
          });
        }
      })
      .catch(() => {
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

  return { flags, loading, reload };
}
