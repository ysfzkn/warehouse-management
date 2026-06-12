import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';

/**
 * Promise-based confirmation modal — a drop-in replacement for window.confirm
 * with proper in-app UX (styled modal, backdrop, Esc/backdrop cancel, focus on
 * the confirm button).
 *
 * Usage:
 *   const ok = await confirmDialog({
 *     title: 'Kupon Silinsin mi?',
 *     message: '"YAZ2026" kuponu kalıcı olarak silinecek.',
 *     confirmText: 'Evet, Sil',
 *     variant: 'danger', // 'danger' (destructive, default) | 'primary' (neutral)
 *   });
 *   if (!ok) return;
 *
 * Renders into its own DOM node, so it works from any component (or plain
 * handler) without a context provider.
 */

const ICONS = {
  danger: 'fa-trash-alt',
  warning: 'fa-exclamation-triangle',
  primary: 'fa-question-circle',
};

function ConfirmModal({ title, message, confirmText, cancelText, variant, icon, onResolve }) {
  const confirmRef = useRef(null);

  useEffect(() => {
    confirmRef.current?.focus();
    const onKey = (e) => {
      if (e.key === 'Escape') onResolve(false);
    };
    document.addEventListener('keydown', onKey);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = prevOverflow;
    };
  }, [onResolve]);

  const accent = variant === 'primary' ? '#2563eb' : variant === 'warning' ? '#d97706' : '#dc2626';
  const accentBg = variant === 'primary' ? '#dbeafe' : variant === 'warning' ? '#fef3c7' : '#fee2e2';

  return (
    <div
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onResolve(false);
      }}
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 3000,
        background: 'rgba(15, 23, 42, 0.55)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 16,
        animation: 'cfdlg-fade 0.15s ease-out',
      }}
    >
      <style>
        {`@keyframes cfdlg-fade { from { opacity: 0; } to { opacity: 1; } }
          @keyframes cfdlg-pop { from { opacity: 0; transform: translateY(10px) scale(0.97); } to { opacity: 1; transform: none; } }`}
      </style>
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="bg-white shadow-lg"
        style={{
          width: '100%',
          maxWidth: 440,
          borderRadius: 14,
          padding: '24px 24px 20px',
          animation: 'cfdlg-pop 0.18s ease-out',
        }}
      >
        <div className="d-flex align-items-start gap-3">
          <div
            className="d-flex align-items-center justify-content-center flex-shrink-0"
            style={{ width: 44, height: 44, borderRadius: 12, background: accentBg, color: accent }}
          >
            <i className={`fas ${icon || ICONS[variant] || ICONS.danger}`} style={{ fontSize: 18 }} />
          </div>
          <div className="flex-grow-1 min-w-0">
            <h6 className="fw-bold mb-1" style={{ fontSize: 16 }}>
              {title}
            </h6>
            <div className="text-muted" style={{ fontSize: 13.5, whiteSpace: 'pre-line', lineHeight: 1.5 }}>
              {message}
            </div>
          </div>
        </div>
        <div className="d-flex justify-content-end gap-2 mt-4">
          <button
            type="button"
            className="btn btn-light border"
            style={{ minWidth: 96, borderRadius: 8 }}
            onClick={() => onResolve(false)}
          >
            {cancelText}
          </button>
          <button
            type="button"
            ref={confirmRef}
            className="btn text-white"
            style={{ minWidth: 96, borderRadius: 8, background: accent }}
            onClick={() => onResolve(true)}
          >
            {confirmText}
          </button>
        </div>
      </div>
    </div>
  );
}

function PromptModal({
  title,
  message,
  placeholder,
  confirmText,
  cancelText,
  variant,
  icon,
  inputLabel,
  helpText,
  onResolve,
}) {
  const [value, setValue] = useState('');
  const inputRef = useRef(null);
  const valid = value.trim().length > 0;

  useEffect(() => {
    inputRef.current?.focus();
    const onKey = (e) => {
      if (e.key === 'Escape') onResolve(null);
    };
    document.addEventListener('keydown', onKey);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = prevOverflow;
    };
  }, [onResolve]);

  const accent = variant === 'primary' ? '#2563eb' : variant === 'warning' ? '#d97706' : '#dc2626';
  const accentBg = variant === 'primary' ? '#dbeafe' : variant === 'warning' ? '#fef3c7' : '#fee2e2';
  const submit = () => valid && onResolve(value.trim());

  return (
    <div
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onResolve(null);
      }}
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 3000,
        background: 'rgba(15, 23, 42, 0.55)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 16,
        animation: 'cfdlg-fade 0.15s ease-out',
      }}
    >
      <style>
        {`@keyframes cfdlg-fade { from { opacity: 0; } to { opacity: 1; } }
          @keyframes cfdlg-pop { from { opacity: 0; transform: translateY(10px) scale(0.97); } to { opacity: 1; transform: none; } }`}
      </style>
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="bg-white shadow-lg"
        style={{
          width: '100%',
          maxWidth: 480,
          borderRadius: 14,
          padding: '24px 24px 20px',
          animation: 'cfdlg-pop 0.18s ease-out',
        }}
      >
        <div className="d-flex align-items-start gap-3">
          <div
            className="d-flex align-items-center justify-content-center flex-shrink-0"
            style={{ width: 44, height: 44, borderRadius: 12, background: accentBg, color: accent }}
          >
            <i className={`fas ${icon || 'fa-pen'}`} style={{ fontSize: 18 }} />
          </div>
          <div className="flex-grow-1 min-w-0">
            <h6 className="fw-bold mb-1" style={{ fontSize: 16 }}>
              {title}
            </h6>
            {message && (
              <div className="text-muted" style={{ fontSize: 13.5, whiteSpace: 'pre-line', lineHeight: 1.5 }}>
                {message}
              </div>
            )}
          </div>
        </div>
        <div className="mt-3">
          {inputLabel && (
            <label className="form-label fw-semibold mb-1" style={{ fontSize: 13 }}>
              {inputLabel} <span className="text-danger">*</span>
            </label>
          )}
          <textarea
            ref={inputRef}
            className="form-control"
            rows={3}
            placeholder={placeholder}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) submit();
            }}
            style={{ fontSize: 13.5, borderRadius: 8 }}
          />
          {helpText && (
            <small className="text-muted d-block mt-1" style={{ fontSize: 12 }}>
              {helpText}
            </small>
          )}
        </div>
        <div className="d-flex justify-content-end gap-2 mt-3">
          <button
            type="button"
            className="btn btn-light border"
            style={{ minWidth: 96, borderRadius: 8 }}
            onClick={() => onResolve(null)}
          >
            {cancelText}
          </button>
          <button
            type="button"
            className="btn text-white"
            disabled={!valid}
            style={{ minWidth: 96, borderRadius: 8, background: accent, opacity: valid ? 1 : 0.5 }}
            onClick={submit}
          >
            {confirmText}
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * Prompt modal with a required text input — replacement for window.prompt.
 * Resolves with the trimmed text, or null when cancelled.
 *
 * @param {Object} options
 * @param {string} options.title
 * @param {string} [options.message] context/warning shown above the input
 * @param {string} [options.inputLabel]
 * @param {string} [options.placeholder]
 * @param {string} [options.helpText]
 * @param {string} [options.confirmText='Onayla']
 * @param {string} [options.cancelText='Vazgeç']
 * @param {'danger'|'warning'|'primary'} [options.variant='primary']
 * @param {string} [options.icon]
 * @returns {Promise<string|null>}
 */
export function promptDialog({
  title,
  message,
  inputLabel,
  placeholder,
  helpText,
  confirmText = 'Onayla',
  cancelText = 'Vazgeç',
  variant = 'primary',
  icon,
} = {}) {
  return new Promise((resolve) => {
    const host = document.createElement('div');
    document.body.appendChild(host);
    const root = createRoot(host);

    const onResolve = (answer) => {
      root.unmount();
      host.remove();
      resolve(answer);
    };

    root.render(
      <PromptModal
        title={title}
        message={message}
        inputLabel={inputLabel}
        placeholder={placeholder}
        helpText={helpText}
        confirmText={confirmText}
        cancelText={cancelText}
        variant={variant}
        icon={icon}
        onResolve={onResolve}
      />
    );
  });
}

/**
 * @param {Object} options
 * @param {string} [options.title='Emin misiniz?']
 * @param {string} options.message
 * @param {string} [options.confirmText='Evet']
 * @param {string} [options.cancelText='Vazgeç']
 * @param {'danger'|'warning'|'primary'} [options.variant='danger']
 * @param {string} [options.icon] FontAwesome class override (e.g. 'fa-magic')
 * @returns {Promise<boolean>}
 */
export default function confirmDialog({
  title = 'Emin misiniz?',
  message,
  confirmText = 'Evet',
  cancelText = 'Vazgeç',
  variant = 'danger',
  icon,
} = {}) {
  return new Promise((resolve) => {
    const host = document.createElement('div');
    document.body.appendChild(host);
    const root = createRoot(host);

    const onResolve = (answer) => {
      root.unmount();
      host.remove();
      resolve(answer);
    };

    root.render(
      <ConfirmModal
        title={title}
        message={message}
        confirmText={confirmText}
        cancelText={cancelText}
        variant={variant}
        icon={icon}
        onResolve={onResolve}
      />
    );
  });
}
