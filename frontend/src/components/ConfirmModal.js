import React from 'react';

const ConfirmModal = ({ show, onConfirm, onCancel, title, message, confirmText = 'Onayla', cancelText = 'İptal', confirmVariant = 'primary', icon = 'question-circle' }) => {
  if (!show) return null;

  const iconColors = {
    'question-circle': 'text-primary',
    'exclamation-triangle': 'text-warning',
    'trash': 'text-danger',
    'check-circle': 'text-success',
    'info-circle': 'text-info'
  };

  return (
    <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
      <div className="modal-dialog modal-dialog-centered">
        <div className="modal-content shadow-lg border-0">
          <div className="modal-header border-0 pb-0">
            <div className="w-100 text-center pt-3">
              <div className={`${iconColors[icon] || 'text-primary'} mb-3`}>
                <i className={`fas fa-${icon} fa-3x`}></i>
              </div>
              <h5 className="modal-title fw-bold">{title}</h5>
            </div>
            <button
              type="button"
              className="btn-close position-absolute top-0 end-0 m-3"
              onClick={onCancel}
            ></button>
          </div>
          <div className="modal-body text-center px-4 py-3">
            <p className="text-muted mb-0">{message}</p>
          </div>
          <div className="modal-footer border-0 justify-content-center gap-2 pb-4">
            <button
              type="button"
              className="btn btn-outline-secondary px-4"
              onClick={onCancel}
            >
              <i className="fas fa-times me-2"></i>
              {cancelText}
            </button>
            <button
              type="button"
              className={`btn btn-${confirmVariant} px-4`}
              onClick={onConfirm}
            >
              <i className="fas fa-check me-2"></i>
              {confirmText}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ConfirmModal;

