import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';

/**
 * Admin workspace ("çalışma alanı") split: WMS (warehouse ops) vs ECOM (e-commerce).
 * A single top-bar toggle switches the whole admin experience — menu set and the
 * notification feed — so an operator focuses on one domain at a time. The choice
 * persists in localStorage; stock-only roles are locked to WMS.
 */
const WorkspaceContext = createContext({
  workspace: 'WMS',
  setWorkspace: () => {},
  isWms: true,
  isEcom: false,
  locked: false,
});

const STORAGE_KEY = 'admin_workspace';

/** Which workspace a given admin path belongs to. null = shared (don't auto-switch). */
const ECOM_PREFIXES = [
  '/admin/sales-dashboard',
  '/admin/orders',
  '/admin/returns',
  '/admin/payments',
  '/admin/invoices',
  '/admin/customers',
  '/admin/support-tickets',
  '/admin/contact-messages',
  '/admin/coupons',
  '/admin/reviews',
];
const WMS_PREFIXES = ['/warehouses', '/stock', '/stock-imports', '/admin/stock-movements'];

export function domainForPath(pathname) {
  if (!pathname) return null;
  if (pathname === '/') return 'WMS';
  if (ECOM_PREFIXES.some((p) => pathname.startsWith(p))) return 'ECOM';
  if (WMS_PREFIXES.some((p) => pathname.startsWith(p))) return 'WMS';
  return null; // shared pages (settings, assistant, help, catalog) — keep current workspace
}

const readRole = () => {
  try {
    return localStorage.getItem('auth_role') || 'ADMIN';
  } catch {
    return 'ADMIN';
  }
};

export function WorkspaceProvider({ children }) {
  const [role, setRole] = useState(readRole);
  // Stock-only roles never touch e-commerce — lock them to WMS.
  const locked = role === 'STOCK_IN' || role === 'STOCK_OUT';

  const [workspace, setWorkspaceState] = useState(() => {
    if (locked) return 'WMS';
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      return saved === 'ECOM' || saved === 'WMS' ? saved : 'WMS';
    } catch {
      return 'WMS';
    }
  });

  // Keep role in sync with auth changes (login/logout) so the lock applies correctly.
  useEffect(() => {
    const onAuth = () => setRole(readRole());
    window.addEventListener('storage', onAuth);
    window.addEventListener('auth-changed', onAuth);
    return () => {
      window.removeEventListener('storage', onAuth);
      window.removeEventListener('auth-changed', onAuth);
    };
  }, []);

  useEffect(() => {
    if (locked && workspace !== 'WMS') setWorkspaceState('WMS');
  }, [locked, workspace]);

  const setWorkspace = useCallback((ws) => {
    if (ws !== 'WMS' && ws !== 'ECOM') return;
    setWorkspaceState(ws);
    try {
      localStorage.setItem(STORAGE_KEY, ws);
    } catch {
      /* ignore */
    }
    try {
      window.dispatchEvent(new CustomEvent('workspace-changed', { detail: ws }));
    } catch {
      /* ignore */
    }
  }, []);

  return (
    <WorkspaceContext.Provider
      value={{ workspace, setWorkspace, isWms: workspace === 'WMS', isEcom: workspace === 'ECOM', locked }}
    >
      {children}
    </WorkspaceContext.Provider>
  );
}

export function useWorkspace() {
  return useContext(WorkspaceContext);
}
