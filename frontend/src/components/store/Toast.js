import React, { useState, useEffect, useCallback, useMemo, createContext, useContext } from 'react';
import { FiCheckCircle, FiXCircle, FiAlertTriangle, FiInfo, FiX } from 'react-icons/fi';

const ToastContext = createContext(null);

const CONFIG = {
  success: {
    icon: FiCheckCircle,
    bg: '#ecfdf5',
    text: '#065f46',
    accent: '#10b981',
    border: '#a7f3d0',
    label: 'Başarılı',
  },
  error: {
    icon: FiXCircle,
    bg: '#fef2f2',
    text: '#991b1b',
    accent: '#ef4444',
    border: '#fecaca',
    label: 'Hata',
  },
  warning: {
    icon: FiAlertTriangle,
    bg: '#fffbeb',
    text: '#92400e',
    accent: '#f59e0b',
    border: '#fde68a',
    label: 'Uyarı',
  },
  info: {
    icon: FiInfo,
    bg: '#eff6ff',
    text: '#1e40af',
    accent: '#3b82f6',
    border: '#bfdbfe',
    label: 'Bilgi',
  },
};

const DURATIONS = { success: 3500, error: 6000, warning: 4500, info: 3500 };

function ToastItem({ toast, onDismiss }) {
  const [exiting, setExiting] = useState(false);
  const [progress, setProgress] = useState(100);
  const cfg = CONFIG[toast.type] || CONFIG.info;
  const Icon = cfg.icon;
  const duration = DURATIONS[toast.type] || 3500;

  useEffect(() => {
    const start = Date.now();
    const interval = setInterval(() => {
      const elapsed = Date.now() - start;
      const remaining = Math.max(0, 100 - (elapsed / duration) * 100);
      setProgress(remaining);
      if (remaining <= 0) clearInterval(interval);
    }, 30);
    const timer = setTimeout(() => {
      setExiting(true);
      setTimeout(() => onDismiss(toast.id), 350);
    }, duration);
    return () => {
      clearTimeout(timer);
      clearInterval(interval);
    };
  }, [toast, onDismiss, duration]);

  const handleDismiss = () => {
    setExiting(true);
    setTimeout(() => onDismiss(toast.id), 350);
  };

  return (
    <div
      role="alert"
      aria-live="assertive"
      className={`store-toast ${exiting ? 'store-toast-exit' : 'store-toast-enter'}`}
      style={{
        '--toast-accent': cfg.accent,
        '--toast-bg': cfg.bg,
        '--toast-text': cfg.text,
        '--toast-border': cfg.border,
      }}
    >
      {/* Accent stripe */}
      <div className="store-toast-stripe" />

      <div className="store-toast-content">
        <div className="store-toast-icon">
          <Icon size={20} />
        </div>
        <div className="store-toast-body">
          <div className="store-toast-label">{cfg.label}</div>
          <div className="store-toast-message">{toast.message}</div>
        </div>
        <button className="store-toast-close" onClick={handleDismiss} aria-label="Kapat">
          <FiX size={16} />
        </button>
      </div>

      {/* Progress bar */}
      <div className="store-toast-progress-track">
        <div className="store-toast-progress-bar" style={{ width: `${progress}%` }} />
      </div>
    </div>
  );
}

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const addToast = useCallback((message, type = 'info') => {
    const id = Date.now() + Math.random();
    setToasts((prev) => {
      // Max 3 visible toasts
      const next = [...prev, { id, message, type }];
      return next.length > 3 ? next.slice(-3) : next;
    });
  }, []);

  const dismiss = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  return (
    <ToastContext.Provider value={addToast}>
      {children}
      <div className="store-toast-container">
        {toasts.map((toast) => (
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
  // Memoised so the object can safely sit in an effect's dependency array.
  return useMemo(
    () => ({
      success: (msg) => addToast?.(msg, 'success'),
      error: (msg) => addToast?.(msg, 'error'),
      warning: (msg) => addToast?.(msg, 'warning'),
      info: (msg) => addToast?.(msg, 'info'),
    }),
    [addToast]
  );
}
