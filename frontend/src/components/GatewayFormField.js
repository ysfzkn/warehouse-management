import React, { useState } from 'react';

/**
 * Reusable gateway field renderer.
 *
 * Props:
 *   def: { label, type, required, placeholder, help, monospace?, min?, max? }
 *   value: current value
 *   onChange: (newValue) => void
 *   editingExistingSecret: true if this is an edit and the secret was previously set
 *                          (we show a "(değiştirmek için yeni değer girin)" placeholder)
 */
export default function GatewayFormField({ def, value, onChange, editingExistingSecret }) {
  const [show, setShow] = useState(false);
  const isSecret = def.type === 'password';
  const inputType = isSecret ? (show ? 'text' : 'password')
                   : (def.type === 'number' ? 'number' : 'text');

  // Edit modunda secret zaten varsa, placeholder'ı değiştir
  const placeholder = editingExistingSecret && isSecret
    ? '(önceki değer korunuyor — değiştirmek için yeni değer girin)'
    : def.placeholder || '';

  return (
    <div>
      <label className="form-label small fw-semibold d-flex align-items-center gap-1">
        <span>{def.label}</span>
        {def.required && <span className="text-danger" aria-hidden="true">*</span>}
        {def.required && <span className="visually-hidden">(zorunlu)</span>}
        {def.help && (
          <span className="text-muted ms-auto" title={def.help} style={{ cursor: 'help', fontSize: 12 }}>
            <i className="fas fa-info-circle" />
          </span>
        )}
      </label>
      <div className="input-group">
        <input
          type={inputType}
          className={`form-control ${def.monospace ? 'font-monospace' : ''}`}
          value={value || ''}
          onChange={e => onChange(e.target.value)}
          placeholder={placeholder}
          autoComplete={isSecret ? 'new-password' : 'off'}
          min={def.min}
          max={def.max}
          aria-required={def.required}
        />
        {isSecret && (
          <button type="button" className="btn btn-outline-secondary"
            onClick={() => setShow(s => !s)}
            aria-label={show ? 'Şifreyi gizle' : 'Şifreyi göster'}
            title={show ? 'Gizle' : 'Göster'}>
            <i className={`fas fa-eye${show ? '-slash' : ''}`} />
          </button>
        )}
      </div>
      {def.help && (
        <small className="text-muted d-block mt-1" style={{ fontSize: 11.5, lineHeight: 1.4 }}>
          {def.help}
        </small>
      )}
    </div>
  );
}
