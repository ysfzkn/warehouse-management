import React, { useMemo, useState } from 'react';
import axios from 'axios';
import useSecurityCodePrompt from './useSecurityCodePrompt';
import { useCustomerActivityCheck } from './CustomerActivityWarning';
import { toNoteCase } from '../utils/name';

/**
 * Quick stock adjustment modal - simplified for fast add/remove operations
 */
const QuickStockAdjustModal = ({ stock, type, onSuccess, onClose }) => {
  const role = (typeof window !== 'undefined' && localStorage.getItem('auth_role')) || 'ADMIN';
  const [quantity, setQuantity] = useState('');
  const [notes, setNotes] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const {
    askCode: askSecurityCode,
    SecurityCodePrompt,
    closePrompt: closeSecurityPrompt,
  } = useSecurityCodePrompt();
  const {
    confirm: confirmCustomerActivity,
    checking: activityChecking,
    dialog: activityDialog,
  } = useCustomerActivityCheck();

  const isAdd = type === 'add';
  const canPerform = isAdd
    ? role === 'ADMIN' || role === 'STOCK_IN'
    : role === 'ADMIN' || role === 'STOCK_OUT';

  const adminSecurityErrorCodes = useMemo(
    () =>
      new Set([
        'AUTH_002',
        'AUTH_003',
        'AUTH_004',
        'AUTH_005',
        'AUTH_006',
        'AUTH_007',
        'ADMIN_SECURITY_CODE_REQUIRED',
        'INVALID_ADMIN_SECURITY_CODE',
        'ADMIN_SECURITY_CODE_MISMATCH',
      ]),
    []
  );

  const parseSecurityError = (err) => {
    const data = err?.response?.data;
    const code = data?.code || data?.errorCode;
    const msg = data?.message || data?.error || err?.message || 'Beklenmeyen bir hata oluştu';
    return { code, msg };
  };

  const showToast = (message, type = 'success', duration = 6000) => {
    try {
      const bgClass =
        type === 'success'
          ? 'text-bg-success'
          : type === 'warning'
            ? 'text-bg-warning'
            : type === 'danger'
              ? 'text-bg-danger'
              : 'text-bg-secondary';

      const toast = document.createElement('div');
      toast.className = `toast align-items-center ${bgClass} border-0 position-fixed top-0 end-0 m-3 show`;
      toast.setAttribute('role', 'alert');
      toast.style.zIndex = '9999';
      toast.style.minWidth = '360px';
      toast.style.maxWidth = '600px';
      toast.innerHTML = `
        <div class="d-flex">
          <div class="toast-body">${message}</div>
          <button type="button" class="btn-close ${type === 'success' ? 'btn-close-white' : ''} me-2 m-auto" aria-label="Kapat"></button>
        </div>
      `;
      document.body.appendChild(toast);
      toast.querySelector('.btn-close')?.addEventListener('click', () => {
        try {
          document.body.removeChild(toast);
        } catch {}
      });
      setTimeout(() => {
        try {
          toast.classList.remove('show');
          setTimeout(() => {
            try {
              document.body.removeChild(toast);
            } catch {}
          }, 300);
        } catch {}
      }, duration);
    } catch {}
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!quantity || parseInt(quantity) <= 0) {
      setError('Geçerli bir miktar giriniz');
      showToast('Geçerli bir miktar giriniz', 'warning');
      return;
    }

    if (!canPerform) {
      setError(`Yetkiniz yok: ${isAdd ? 'Sadece stok ekleme' : 'Sadece stok çıkarma'} yapabilirsiniz`);
      showToast('Bu işlem için yetkiniz yok.', 'danger');
      return;
    }

    // Only removals can be a duplicate hand-over. The note is the usual place a customer name
    // ends up; consignment ("emanet") stock also carries one on the record itself.
    if (!isAdd) {
      const proceed = await confirmCustomerActivity({
        note: notes,
        name: stock?.customerName,
        phone: stock?.customerPhone,
      });
      if (!proceed) return;
    }

    setLoading(true);
    setError(null);
    setSuccess(null);

    try {
      const params = { quantity: parseInt(quantity) };
      if (notes) {
        params.notes = notes;
      }

      const endpoint = isAdd ? `/api/stocks/${stock.id}/add` : `/api/stocks/${stock.id}/remove`;
      const doRequest = async (headers = {}) => axios.put(endpoint, null, { params, headers });

      let response;
      if (role !== 'ADMIN') {
        response = await doRequest();
      } else {
        let lastCode = '';
        let lastErrorMsg = '';
        // eslint-disable-next-line no-constant-condition
        while (true) {
          const code = await askSecurityCode({
            prefill: lastCode,
            errorMessage: lastErrorMsg || (lastCode ? 'Güvenlik şifresi hatalı, tekrar deneyin.' : ''),
            persistOnResolve: true,
            title: 'Yönetici Güvenlik Şifresi',
            description: `Stok ${isAdd ? 'ekleme' : 'çıkarma'} işlemini tamamlamak için güvenlik şifresini girin.`,
          });
          if (code === null) {
            closeSecurityPrompt();
            setLoading(false);
            return;
          }
          lastCode = code;
          lastErrorMsg = '';
          try {
            response = await doRequest({ 'X-ADMIN-SECURITY-CODE': code });
            closeSecurityPrompt();
            break;
          } catch (err) {
            const { code: errCode, msg } = parseSecurityError(err);
            lastErrorMsg = msg;
            if (!adminSecurityErrorCodes.has(errCode)) {
              closeSecurityPrompt();
              throw err;
            }
            // security error: loop continues, prompt stays open
          }
        }
      }

      if (response.status === 202) {
        setSuccess('✓ Talebiniz oluşturuldu! Yönetici onayı bekleniyor.');
        showToast('Talebiniz oluşturuldu. Yönetici onayı bekleniyor.', 'success', 7000);
        setTimeout(() => {
          onSuccess();
        }, 1500);
      } else {
        setSuccess(`✓ Stok başarıyla ${isAdd ? 'eklendi' : 'çıkarıldı'}!`);
        showToast(`Stok başarıyla ${isAdd ? 'eklendi' : 'çıkarıldı'}.`, 'success', 6000);
        setTimeout(() => {
          onSuccess();
        }, 800);
      }
    } catch (error) {
      console.error('Error adjusting stock:', error);
      const msg =
        error.response?.data?.message ||
        error.response?.data?.error ||
        error.response?.data ||
        'İşlem sırasında hata oluştu';
      setError(msg);
      showToast(msg, 'danger', 8000);
    } finally {
      setLoading(false);
    }
  };

  const suggestedAmounts = [1, 5, 10, 20, 50, 100];

  return (
    <>
      {SecurityCodePrompt}
      {activityDialog}
      <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
        <div className="modal-dialog modal-dialog-centered">
          <div className="modal-content">
            <div className={`modal-header ${isAdd ? 'bg-success' : 'bg-danger'} text-white`}>
              <h5 className="modal-title">
                <i className={`fas fa-${isAdd ? 'plus' : 'minus'}-circle me-2`}></i>
                {isAdd ? 'Hızlı Stok Ekleme' : 'Hızlı Stok Çıkarma'}
              </h5>
              <button type="button" className="btn-close btn-close-white" onClick={onClose}></button>
            </div>

            <div className="modal-body">
              {/* Stock Info Card */}
              <div className="card mb-3 border-0 bg-light">
                <div className="card-body py-2">
                  <div className="row align-items-center">
                    <div className="col">
                      <div className="fw-bold">{stock.product.name}</div>
                      <small className="text-muted">
                        <i className="fas fa-warehouse me-1"></i>
                        {stock.warehouse.name}
                      </small>
                    </div>
                    <div className="col-auto text-end">
                      <div className="fs-4 fw-bold">{stock.quantity}</div>
                      <small className="text-muted">Mevcut</small>
                    </div>
                  </div>
                  {!isAdd && (
                    <div className="mt-2 pt-2 border-top">
                      <small className="text-muted">
                        Kullanılabilir:{' '}
                        <strong className={stock.availableQuantity < 10 ? 'text-danger' : 'text-success'}>
                          {stock.availableQuantity}
                        </strong>
                      </small>
                    </div>
                  )}
                </div>
              </div>

              <form onSubmit={handleSubmit}>
                {error && (
                  <div className="alert alert-danger py-2" role="alert">
                    <i className="fas fa-exclamation-triangle me-2"></i>
                    {error}
                  </div>
                )}

                {success && (
                  <div className="alert alert-success py-2" role="alert">
                    <i className="fas fa-check-circle me-2"></i>
                    {success}
                  </div>
                )}

                <div className="mb-3">
                  <label htmlFor="quantity" className="form-label fw-bold">
                    <i className={`fas fa-${isAdd ? 'plus' : 'minus'} me-1`}></i>
                    {isAdd ? 'Eklenecek' : 'Çıkarılacak'} Miktar
                    <span className="text-danger ms-1">*</span>
                  </label>
                  <input
                    type="number"
                    min="1"
                    max={!isAdd ? stock.availableQuantity : undefined}
                    className="form-control form-control-lg"
                    id="quantity"
                    value={quantity}
                    onChange={(e) => setQuantity(e.target.value)}
                    placeholder="0"
                    autoFocus
                    required
                    inputMode="numeric"
                    onKeyDown={(e) => {
                      if (['e', 'E', '+', '-', ',', '.'].includes(e.key)) {
                        e.preventDefault();
                      }
                    }}
                    onWheel={(e) => e.currentTarget.blur()}
                  />

                  {/* Quick amount buttons */}
                  <div className="mt-2">
                    <small className="text-muted d-block mb-1">Hızlı seçim:</small>
                    <div className="d-flex flex-wrap gap-1">
                      {suggestedAmounts
                        .filter((amount) => isAdd || amount <= stock.availableQuantity)
                        .map((amount) => (
                          <button
                            key={amount}
                            type="button"
                            className="btn btn-sm btn-outline-secondary"
                            onClick={() => setQuantity(amount.toString())}
                          >
                            {amount}
                          </button>
                        ))}
                    </div>
                  </div>
                </div>

                <div className="mb-3">
                  <label htmlFor="notes" className="form-label">
                    <i className="fas fa-sticky-note me-1"></i>
                    Not {role !== 'ADMIN' && <small className="text-muted">(Yönetici görecek)</small>}
                  </label>
                  <textarea
                    className="form-control"
                    id="notes"
                    rows="2"
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                    // A note that is only a customer name gets title cased; a sentence keeps its wording.
                    onBlur={(e) => setNotes(toNoteCase(e.target.value))}
                    placeholder={role === 'ADMIN' ? 'İsteğe bağlı not...' : 'Talep nedeninizi açıklayın...'}
                    maxLength="500"
                  />
                  <small className="text-muted">{notes.length}/500 karakter</small>
                </div>

                {role !== 'ADMIN' && (
                  <div className="alert alert-info py-2 mb-3">
                    <i className="fas fa-info-circle me-2"></i>
                    <small>Bu talep yönetici onayına gönderilecektir.</small>
                  </div>
                )}

                <div className="row g-2">
                  <div className="col-6">
                    <button
                      type="button"
                      className="btn btn-secondary w-100"
                      onClick={onClose}
                      disabled={loading}
                    >
                      <i className="fas fa-times me-1"></i>
                      İptal
                    </button>
                  </div>
                  <div className="col-6">
                    <button
                      type="submit"
                      className={`btn ${isAdd ? 'btn-success' : 'btn-danger'} w-100`}
                      disabled={
                        loading || activityChecking || !quantity || parseInt(quantity) <= 0 || success
                      }
                    >
                      {activityChecking ? (
                        <>
                          <span className="spinner-border spinner-border-sm me-1"></span>
                          Kontrol ediliyor...
                        </>
                      ) : loading ? (
                        <>
                          <span className="spinner-border spinner-border-sm me-1"></span>
                          İşleniyor...
                        </>
                      ) : (
                        <>
                          <i className={`fas fa-${isAdd ? 'plus' : 'minus'} me-1`}></i>
                          {role === 'ADMIN' ? (isAdd ? 'Ekle' : 'Çıkar') : 'Talep Gönder'}
                        </>
                      )}
                    </button>
                  </div>
                </div>
              </form>
            </div>
          </div>
        </div>
      </div>
    </>
  );
};

export default QuickStockAdjustModal;
