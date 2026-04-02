import React, { useState, useEffect, useCallback, createContext, useContext } from 'react';
import { FiCheck, FiX, FiAlertTriangle, FiInfo } from 'react-icons/fi';

const ToastContext = createContext(null);

const ICONS = {
  success: FiCheck,
  error: FiX,
  warning: FiAlertTriangle,
  info: FiInfo,
};

const COLORS = {
  success: { bg: 'var(--color-success-bg)', text: 'var(--color-success-text)', border: 'var(--color-success-border)' },
  error:   { bg: 'var(--color-error-bg)',   text: 'var(--color-error-text)',   border: 'var(--color-error-border)' },
  warning: { bg: 'var(--color-warning-bg)', text: 'var(--color-warning-text)', border: 'var(--color-warning-border)' },
  info:    { bg: 'var(--color-info-bg)',     text: 'var(--color-info-text)',    border: 'var(--color-info-border)' },
};

const DURATIONS = { success: 3000, error: 5000, warning: 4000, info: 3000 };

function ToastItem({ toast, onDismiss }) {
  const [exiting, setExiting] = useState(false);
  const Icon = ICONS[toast.type] || FiInfo;
  const colors = COLORS[toast.type] || COLORS.info;

  useEffect(() => {
    const timer = setTimeout(() => {
      setExiting(true);
      setTimeout(() => onDismiss(toast.id), 300);
    }, DURATIONS[toast.type] || 3000);
    return () => clearTimeout(timer);
  }, [toast, onDismiss]);

  return (
    <div
      role="alert"
      aria-live="polite"
      style={{
        display: 'flex', alignItems: 'center', gap: 'var(--sp-3)',
        padding: 'var(--sp-3) var(--sp-4)',
        background: colors.bg, color: colors.text,
        border: `1px solid ${colors.border}`,
        borderRadius: 'var(--radius-lg)',
        boxShadow: 'var(--shadow-lg)',
        animation: exiting ? 'toast-slide-out 0.3s ease forwards' : 'toast-slide-in 0.3s ease',
        minWidth: 280, maxWidth: 400,
      }}
    >
      <Icon size={18} style={{ flexShrink: 0 }} />
      <span style={{ flex: 1, fontSize: 'var(--text-sm)', fontWeight: 'var(--font-medium)' }}>{toast.message}</span>
      <button
        onClick={() => { setExiting(true); setTimeout(() => onDismiss(toast.id), 300); }}
        style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 'var(--sp-1)', color: colors.text, opacity: 0.6 }}
        aria-label="Kapat"
      >
        <FiX size={14} />
      </button>
    </div>
  );
}

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const addToast = useCallback((message, type = 'info') => {
    const id = Date.now() + Math.random();
    setToasts(prev => [...prev, { id, message, type }]);
  }, []);

  const dismiss = useCallback((id) => {
    setToasts(prev => prev.filter(t => t.id !== id));
  }, []);

  return (
    <ToastContext.Provider value={addToast}>
      {children}
      <div
        style={{
          position: 'fixed', top: 'var(--sp-4)', right: 'var(--sp-4)',
          display: 'flex', flexDirection: 'column', gap: 'var(--sp-2)',
          zIndex: 'var(--z-toast)', pointerEvents: 'none',
        }}
      >
        {toasts.map(toast => (
          <div key={toast.id} style={{ pointerEvents: 'auto' }}>
            <ToastItem toast={toast} onDismiss={dismiss} />
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const addToast = useContext(ToastContext);
  return {
    success: (msg) => addToast?.(msg, 'success'),
    error: (msg) => addToast?.(msg, 'error'),
    warning: (msg) => addToast?.(msg, 'warning'),
    info: (msg) => addToast?.(msg, 'info'),
  };
}
