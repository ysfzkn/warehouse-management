import { useState, useCallback, useEffect } from 'react';
import axios from 'axios';

const API_BASE = '/api/store';

const getSessionId = () => {
  let sid = localStorage.getItem('store_session_id');
  if (!sid) {
    sid = 'sess_' + Math.random().toString(36).substr(2, 16) + Date.now();
    localStorage.setItem('store_session_id', sid);
  }
  return sid;
};

const getHeaders = () => {
  const h = { 'X-Session-Id': getSessionId() };
  const token = localStorage.getItem('customer_token');
  if (token) h['Authorization'] = `Bearer ${token}`;
  return h;
};

export function useCart() {
  const [cart, setCart] = useState(null);
  const [loading, setLoading] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const fetchCart = useCallback(async () => {
    try {
      setLoading(true);
      const res = await axios.get(`${API_BASE}/cart`, { headers: getHeaders() });
      setCart(res.data);
    } catch (e) {
      console.error('Cart fetch error:', e);
    } finally {
      setLoading(false);
    }
  }, []);

  const addItem = useCallback(async (productId, quantity = 1) => {
    try {
      setLoading(true);
      const res = await axios.post(`${API_BASE}/cart/items`, { productId, quantity }, { headers: getHeaders() });
      setCart(res.data);
      setSidebarOpen(true);
    } catch (e) {
      console.error('Add to cart error:', e);
      throw e;
    } finally {
      setLoading(false);
    }
  }, []);

  const updateItem = useCallback(async (itemId, quantity) => {
    try {
      const res = await axios.put(`${API_BASE}/cart/items/${itemId}`, { quantity }, { headers: getHeaders() });
      setCart(res.data);
    } catch (e) {
      console.error('Update cart error:', e);
      throw e;
    }
  }, []);

  const removeItem = useCallback(async (itemId) => {
    try {
      const res = await axios.delete(`${API_BASE}/cart/items/${itemId}`, { headers: getHeaders() });
      setCart(res.data);
    } catch (e) {
      console.error('Remove cart error:', e);
    }
  }, []);

  /** Sepeti tamamen boşalt (tüm ürünleri kaldır) */
  const clearCart = useCallback(async () => {
    try {
      setLoading(true);
      const res = await axios.delete(`${API_BASE}/cart`, { headers: getHeaders() });
      setCart(res.data);
    } catch (e) {
      console.error('Clear cart error:', e);
      throw e;
    } finally {
      setLoading(false);
    }
  }, []);

  const applyCoupon = useCallback(async (code) => {
    const res = await axios.post(`${API_BASE}/cart/coupon`, { code }, { headers: getHeaders() });
    setCart(res.data);
  }, []);

  const removeCoupon = useCallback(async () => {
    try {
      const res = await axios.delete(`${API_BASE}/cart/coupon`, { headers: getHeaders() });
      setCart(res.data);
    } catch (e) {
      console.error('Remove coupon error:', e);
    }
  }, []);

  useEffect(() => { fetchCart(); }, [fetchCart]);

  return {
    cart, loading, sidebarOpen, setSidebarOpen,
    fetchCart, addItem, updateItem, removeItem, clearCart, applyCoupon, removeCoupon,
    itemCount: cart?.itemCount || 0
  };
}
