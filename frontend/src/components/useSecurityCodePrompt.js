import { useCallback, useMemo, useRef, useState } from 'react';
import ReactDOM from 'react-dom';

/**
 * Professional-looking security code prompt to replace window.prompt usage.
 * Usage:
 *   const { askCode, SecurityCodePrompt } = useSecurityCodePrompt();
 *   const code = await askCode();
 *   if (code === null) return; // cancelled
 *   ... use code ...
 *   return (
 *     <>
 *       {SecurityCodePrompt}
 *       ... rest of page ...
 *     </>
 *   );
 */
export default function useSecurityCodePrompt() {
  const [visible, setVisible] = useState(false);
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [show, setShow] = useState(false);
  const resolverRef = useRef(null);
  const optionsRef = useRef({});

  const close = useCallback(() => {
    setVisible(false);
    setCode('');
    setError('');
    setShow(false);
    resolverRef.current = null;
    optionsRef.current = {};
  }, []);

  const askCode = useCallback((options = {}) => {
    const {
      prefill = '',
      errorMessage = '',
      persistOnResolve = false,
      title = 'Yönetici Güvenlik Şifresi',
      description = 'İşlemi tamamlamak için güvenlik şifresini girin.',
      confirmText = 'Onayla',
      cancelText = 'İptal',
      inputPlaceholder = 'Güvenlik şifresi',
    } = options;
    optionsRef.current = { persistOnResolve, title, description, confirmText, cancelText, inputPlaceholder };
    return new Promise((resolve) => {
      resolverRef.current = resolve;
      setVisible(true);
      setCode(prefill);
      setError(errorMessage);
      setShow(false);
    });
  }, []);

  const handleConfirm = useCallback(() => {
    const trimmed = (code || '').trim();
    if (!trimmed) {
      setError('Güvenlik şifresi zorunludur.');
      return;
    }
    if (resolverRef.current) {
      resolverRef.current(trimmed);
    }
    if (!optionsRef.current.persistOnResolve) {
      close();
    }
  }, [code, close]);

  const handleCancel = useCallback(() => {
    if (resolverRef.current) {
      resolverRef.current(null);
    }
    close();
  }, [close]);

  const modal = useMemo(() => {
    if (!visible) return null;
    const { title, description, confirmText, cancelText, inputPlaceholder } = optionsRef.current || {};
    const content = (
      <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 4000 }}>
        <div className="modal-dialog">
          <div className="modal-content shadow-lg border-0">
            <div className="modal-header">
              <h5 className="modal-title">{title || 'Yönetici Güvenlik Şifresi'}</h5>
              <button type="button" className="btn-close" onClick={handleCancel}></button>
            </div>
            <div className="modal-body">
              <p className="text-muted mb-3">
                {description || 'İşlemi tamamlamak için güvenlik şifresini girin.'}
              </p>
              <div className="input-group">
                <input
                  type={show ? 'text' : 'password'}
                  className="form-control"
                  value={code}
                  onChange={(e) => { setCode(e.target.value); setError(''); }}
                  autoComplete="one-time-code"
                  name="admin-security-code"
                  placeholder={inputPlaceholder || 'Güvenlik şifresi'}
                />
                <button
                  type="button"
                  className="btn btn-outline-secondary"
                  onClick={() => setShow((prev) => !prev)}
                  aria-label={show ? 'Şifreyi gizle' : 'Şifreyi göster'}
                >
                  <i className={`fas fa-eye${show ? '-slash' : ''}`}></i>
                </button>
              </div>
              {error && <div className="text-danger small mt-2">{error}</div>}
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={handleCancel}>{cancelText || 'İptal'}</button>
              <button className="btn btn-primary" onClick={handleConfirm}>{confirmText || 'Onayla'}</button>
            </div>
          </div>
        </div>
      </div>
    );
    return ReactDOM.createPortal(content, document.body);
  }, [visible, code, show, error, handleCancel, handleConfirm]);

  return {
    askCode,
    SecurityCodePrompt: modal,
    closePrompt: close,
  };
}

