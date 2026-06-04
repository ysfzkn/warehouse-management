import React, { useState, useEffect, useCallback, createContext, useContext } from 'react';

const AdminToastContext = createContext(null);

const ICONS = { success: 'fa-check-circle', error: 'fa-times-circle', warning: 'fa-exclamation-triangle', info: 'fa-info-circle' };
const COLORS = { success: '#059669', error: '#dc2626', warning: '#d97706', info: '#2563eb' };
const BGS = { success: '#ecfdf5', error: '#fef2f2', warning: '#fffbeb', info: '#eff6ff' };
const BORDERS = { success: '#a7f3d0', error: '#fecaca', warning: '#fde68a', info: '#bfdbfe' };
const DURATIONS = { success: 3000, error: 5000, warning: 4000, info: 3000 };

function AdminToastItem({ toast, onDismiss }) {
  const [exiting, setExiting] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => {
      setExiting(true);
      setTimeout(() => onDismiss(toast.id), 300);
    }, DURATIONS[toast.type] || 3000);
    return () => clearTimeout(timer);
  }, [toast, onDismiss]);

  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 10, padding: '12px 16px',
      background: BGS[toast.type], color: COLORS[toast.type],
      border: `1px solid ${BORDERS[toast.type]}`,
      borderLeft: `4px solid ${COLORS[toast.type]}`,
      borderRadius: 10, minWidth: 300, maxWidth: 420,
      boxShadow: '0 8px 24px rgba(0,0,0,0.12)',
      animation: exiting ? 'adminToastOut 0.3s ease forwards' : 'adminToastIn 0.3s ease',
      pointerEvents: 'auto',
    }}>
      <i className={`fas ${ICONS[toast.type]}`} style={{ fontSize: 16, flexShrink: 0 }} />
      <span style={{ flex: 1, fontSize: 13, fontWeight: 500, lineHeight: 1.4, color: '#1e293b' }}>{toast.message}</span>
      <button onClick={() => { setExiting(true); setTimeout(() => onDismiss(toast.id), 300); }}
        style={{ background: 'none', border: 'none', cursor: 'pointer', color: COLORS[toast.type], opacity: 0.5, padding: 2 }}>
        <i className="fas fa-times" style={{ fontSize: 12 }} />
      </button>
    </div>
  );
}

export function AdminToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const addToast = useCallback((message, type = 'info') => {
    const id = Date.now() + Math.random();
    setToasts(prev => {
      const next = [...prev, { id, message, type }];
      return next.length > 4 ? next.slice(-4) : next;
    });
  }, []);

  const dismiss = useCallback((id) => setToasts(prev => prev.filter(t => t.id !== id)), []);

  const api = useCallback((msg, type) => addToast(msg, type), [addToast]);
  api.success = (msg) => addToast(msg, 'success');
  api.error = (msg) => addToast(msg, 'error');
  api.warning = (msg) => addToast(msg, 'warning');
  api.info = (msg) => addToast(msg, 'info');

  return (
    <AdminToastContext.Provider value={api}>
      {children}
      <div style={{
        position: 'fixed', top: 70, right: 16, zIndex: 9999,
        display: 'flex', flexDirection: 'column', gap: 8, pointerEvents: 'none',
      }}>
        {toasts.map(t => <AdminToastItem key={t.id} toast={t} onDismiss={dismiss} />)}
      </div>
      <style>{`
        @keyframes adminToastIn { from { opacity:0; transform:translateX(30px); } to { opacity:1; transform:translateX(0); } }
        @keyframes adminToastOut { from { opacity:1; transform:translateX(0); } to { opacity:0; transform:translateX(30px); } }
      `}</style>
    </AdminToastContext.Provider>
  );
}

export function useAdminToast() {
  const ctx = useContext(AdminToastContext);
  if (!ctx) return { success: () => {}, error: () => {}, warning: () => {}, info: () => {} };
  return { success: ctx.success, error: ctx.error, warning: ctx.warning, info: ctx.info };
}
