import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import axios from 'axios';

const WishlistContext = createContext({ ids: new Set(), toggle: () => {}, has: () => false });

export function WishlistProvider({ children }) {
  const [ids, setIds] = useState(new Set());

  const load = useCallback(() => {
    const token = localStorage.getItem('customer_token');
    if (!token) { setIds(new Set()); return; }
    axios.get('/api/store/wishlist')
      .then(r => {
        const productIds = (r.data || []).map(w => w.productId).filter(Boolean);
        setIds(new Set(productIds));
      })
      .catch(() => setIds(new Set()));
  }, []);

  useEffect(() => { load(); }, [load]);

  // Listen for login/logout
  useEffect(() => {
    const handler = () => load();
    window.addEventListener('customer-auth-changed', handler);
    return () => window.removeEventListener('customer-auth-changed', handler);
  }, [load]);

  const toggle = useCallback(async (productId) => {
    const token = localStorage.getItem('customer_token');
    if (!token) return false;
    const isWished = ids.has(productId);
    try {
      if (isWished) {
        await axios.delete(`/api/store/wishlist/${productId}`);
        setIds(prev => { const next = new Set(prev); next.delete(productId); return next; });
        return false;
      } else {
        await axios.post(`/api/store/wishlist/${productId}`);
        setIds(prev => { const next = new Set(prev); next.add(productId); return next; });
        return true;
      }
    } catch {
      return isWished; // Return unchanged state on error
    }
  }, [ids]);

  const has = useCallback((productId) => ids.has(productId), [ids]);

  return (
    <WishlistContext.Provider value={{ ids, toggle, has, reload: load }}>
      {children}
    </WishlistContext.Provider>
  );
}

export function useWishlist() {
  return useContext(WishlistContext);
}
