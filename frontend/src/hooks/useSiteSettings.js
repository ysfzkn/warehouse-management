import { useState, useEffect } from 'react';
import axios from 'axios';

export function useSiteSettings() {
  const [settings, setSettings] = useState({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    axios.get('/api/store/settings')
      .then(res => setSettings(res.data || {}))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  const get = (key, fallback = '') => settings[key] || fallback;

  return { settings, loading, get };
}
