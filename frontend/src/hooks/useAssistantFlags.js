import { useEffect, useState, useCallback } from 'react';
import axios from 'axios';

/**
 * Reads the assistant feature flags (WMS on/off, Store on/off) from the
 * public backend endpoint. Used by AdminLayout and StoreLayout to decide
 * whether to render the widget at all.
 *
 * Defaults to true (enabled) on network failure so a temporary outage
 * doesn't silently hide the assistant for everyone.
 */
export function useAssistantFlags() {
  const [flags, setFlags] = useState({ wmsEnabled: true, storeEnabled: true });
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
        // Fail-open: assume enabled. The admin can always toggle.
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { reload(); }, [reload]);

  // React to admin-side updates without a full page reload.
  useEffect(() => {
    const handler = () => reload();
    window.addEventListener('assistant-flags-changed', handler);
    return () => window.removeEventListener('assistant-flags-changed', handler);
  }, [reload]);

  return { flags, loading, reload };
}
