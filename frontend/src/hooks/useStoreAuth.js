import { useState, useCallback, useEffect } from 'react';
import axios from 'axios';

export function useStoreAuth() {
  const [customer, setCustomer] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('customer_token');
    const data = localStorage.getItem('customer_data');
    if (token && data) {
      try {
        setCustomer(JSON.parse(data));
      } catch {}
    }
    setLoading(false);
  }, []);

  const login = useCallback(async (email, password) => {
    const res = await axios.post('/api/store/auth/login', { email, password });
    localStorage.setItem('customer_token', res.data.token);
    localStorage.setItem('customer_refresh_token', res.data.refreshToken);
    localStorage.setItem('customer_data', JSON.stringify(res.data));
    setCustomer(res.data);
    return res.data;
  }, []);

  const register = useCallback(async (data) => {
    const res = await axios.post('/api/store/auth/register', data);
    localStorage.setItem('customer_token', res.data.token);
    localStorage.setItem('customer_refresh_token', res.data.refreshToken);
    localStorage.setItem('customer_data', JSON.stringify(res.data));
    setCustomer(res.data);
    return res.data;
  }, []);

  const logout = useCallback(() => {
    // Revoke server-side first: the access token goes on the denylist and the refresh
    // token is marked revoked in the database. Clearing localStorage alone left a
    // 30-day refresh token usable by anyone who had copied it.
    axios.post('/api/store/auth/logout').catch(() => {});
    localStorage.removeItem('customer_token');
    localStorage.removeItem('customer_refresh_token');
    localStorage.removeItem('customer_data');
    setCustomer(null);
  }, []);

  return { customer, loading, isAuthenticated: !!customer, login, register, logout };
}
