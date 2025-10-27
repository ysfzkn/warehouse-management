import React from 'react';

const NotesModal = ({ show, onClose, notes, title = 'Transfer Notları', transferId }) => {
  if (!show) return null;
  
  const isCancellationReason = title === 'İptal Nedeni';
  const headerBg = isCancellationReason 
    ? 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)'
    : 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)';
  const icon = isCancellationReason ? 'fa-exclamation-circle' : 'fa-sticky-note';
  const bgColor = isCancellationReason ? 'danger' : 'primary';

  return (
    <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
      <div className="modal-dialog modal-dialog-centered modal-lg">
        <div className="modal-content shadow-lg border-0">
          <div className="modal-header bg-gradient text-white" style={{ background: headerBg }}>
            <div>
              <h5 className="modal-title mb-1">
                <i className={`fas ${icon} me-2`}></i>
                {title}
              </h5>
              {transferId && (
                <small className="opacity-75">Transfer #{transferId}</small>
              )}
            </div>
            <button
              type="button"
              className="btn-close btn-close-white"
              onClick={onClose}
            ></button>
          </div>
          <div className="modal-body p-4">
            {notes ? (
              <div className="card border-0 bg-light">
                <div className="card-body">
                  <div className="d-flex align-items-start">
                    <div className="flex-shrink-0">
                      <div className={`bg-${bgColor} bg-opacity-10 rounded-circle p-3`}>
                        <i className={`fas fa-quote-left text-${bgColor} fa-lg`}></i>
                      </div>
                    </div>
                    <div className="flex-grow-1 ms-3">
                      <p className="mb-0" style={{ whiteSpace: 'pre-wrap', lineHeight: '1.8' }}>
                        {notes}
                      </p>
                    </div>
                  </div>
                </div>
              </div>
            ) : (
              <div className="text-center py-4">
                <i className="fas fa-inbox fa-3x text-muted mb-3"></i>
                <p className="text-muted mb-0">
                  {isCancellationReason ? 'İptal nedeni belirtilmemiş.' : 'Bu transfer için not bulunmamaktadır.'}
                </p>
              </div>
            )}
          </div>
          <div className="modal-footer border-0 bg-light">
            <button
              type="button"
              className="btn btn-primary px-4"
              onClick={onClose}
            >
              <i className="fas fa-check me-2"></i>
              Tamam
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default NotesModal;

