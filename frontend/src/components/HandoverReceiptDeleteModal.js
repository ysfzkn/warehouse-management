import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';

/**
 * Mükerrer ya da hatalı düzenlenmiş bir depo çıkış makbuzunu siler.
 *
 * Silme makbuzla birlikte sevkiyatı da kaldırıyor ve sunucu stoğa bıraktığı izi geri
 * alıyor: teslim edilmiş çıkışta ürünler çıkış deposuna geri ekleniyor, planlı çıkışta
 * rezervasyon kalkıyor. Ekran bunu kullanıcı onaylamadan önce kalem kalem söylüyor —
 * "emin misiniz" stoğun ne olacağını anlatmaz.
 *
 * Kurallar sunucuda: iade kaydı olan ya da siparişin tek teslimatı olan çıkış silinmiyor,
 * sebebi buraya olduğu gibi yazılıyor.
 */
export default function HandoverReceiptDeleteModal({ receipt, onClose, onDeleted }) {
  const [code, setCode] = useState('');
  const [showCode, setShowCode] = useState(false);
  const [signedAck, setSignedAck] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const codeRef = useRef(null);

  const planned = Boolean(receipt.scheduledDeliveryAt) && !receipt.deliveredAt;
  const warehouse = receipt.sourceWarehouseName || 'çıkış deposu';
  const items = receipt.items || [];
  const needsSignedAck = Boolean(receipt.signedCopyOnFile);
  const canSubmit = !busy && code.trim() !== '' && (!needsSignedAck || signedAck);

  useEffect(() => {
    codeRef.current?.focus();
  }, []);

  useEffect(() => {
    const onKey = (event) => {
      if (event.key === 'Escape' && !busy) onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [busy, onClose]);

  const submit = async (event) => {
    event.preventDefault();
    if (!canSubmit) return;
    setBusy(true);
    setError('');
    try {
      const res = await axios.delete(`/api/stock-transfers/${receipt.transferId}`, {
        headers: { 'X-ADMIN-SECURITY-CODE': code.trim() },
      });
      // 202 = silme değil onay talebi. Bu ekran yöneticiye açık, yine de yanlış
      // bir "silindi" yazmamak için gövdeye bakılıyor.
      if (res?.status === 202) {
        setError(res.data?.message || 'Silme talebi onaya gönderildi; kayıt henüz silinmedi.');
        setBusy(false);
        return;
      }
      onDeleted(receipt, planned);
    } catch (e) {
      const data = e?.response?.data;
      setError(data?.message || data?.error || 'Makbuz silinemedi. Lütfen tekrar deneyin.');
      setBusy(false);
    }
  };

  return (
    <div
      className="modal show d-block"
      tabIndex="-1"
      role="dialog"
      aria-modal="true"
      aria-labelledby="receipt-delete-title"
      style={{ backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 2100 }}
    >
      <div className="modal-dialog modal-dialog-centered">
        <form className="modal-content border-0 rounded-4 shadow" onSubmit={submit}>
          <div className="modal-header">
            <div>
              <h5 className="modal-title mb-0 text-danger" id="receipt-delete-title">
                <i className="fas fa-trash-can me-2"></i>
                Makbuzu Sil
              </h5>
              <small className="text-muted">
                {receipt.receiptNo} · {receipt.customerFullName || 'Müşteri'}
              </small>
            </div>
            <button
              type="button"
              className="btn-close"
              aria-label="Kapat"
              onClick={onClose}
              disabled={busy}
            ></button>
          </div>

          <div className="modal-body">
            <p className="mb-2">
              Bu depo çıkışı ve makbuzu <strong>kalıcı olarak silinecek</strong>. Mükerrer düzenlenmiş bir
              makbuzu kaldırmak için kullanın; stok, bu çıkış hiç yapılmamış gibi geri alınır:
            </p>
            <div className="border rounded-3 p-2 mb-3 bg-light">
              <div className="small fw-semibold mb-1">
                {planned
                  ? `${warehouse} deposundaki rezervasyon kaldırılacak:`
                  : `${warehouse} deposuna geri eklenecek:`}
              </div>
              {items.length > 0 ? (
                <ul className="small mb-0 ps-3">
                  {items.map((item, index) => (
                    <li key={`${item.sku || item.name}-${index}`}>
                      <strong>{Number(item.quantity || 0).toLocaleString('tr-TR')} adet</strong> {item.name}
                      {item.sku ? <span className="text-muted"> ({item.sku})</span> : null}
                    </li>
                  ))}
                </ul>
              ) : (
                <div className="small text-muted">Makbuzda ürün satırı görünmüyor.</div>
              )}
            </div>

            {needsSignedAck && (
              <div className="form-check mb-3">
                <input
                  id="receipt-delete-signed-ack"
                  type="checkbox"
                  className="form-check-input"
                  checked={signedAck}
                  onChange={(e) => setSignedAck(e.target.checked)}
                  disabled={busy}
                />
                <label className="form-check-label small" htmlFor="receipt-delete-signed-ack">
                  Bu makbuzun imzalı nüshası arşivde. Nüsha dosyası da silinecek; bunu anladım.
                </label>
              </div>
            )}

            <label htmlFor="receipt-delete-code" className="form-label small fw-semibold">
              Yönetici güvenlik şifresi
            </label>
            <div className="input-group">
              <input
                id="receipt-delete-code"
                ref={codeRef}
                type={showCode ? 'text' : 'password'}
                className="form-control"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                autoComplete="off"
                disabled={busy}
              />
              <button
                type="button"
                className="btn btn-outline-secondary"
                onClick={() => setShowCode((v) => !v)}
                aria-label={showCode ? 'Şifreyi gizle' : 'Şifreyi göster'}
                disabled={busy}
              >
                <i className={`fas ${showCode ? 'fa-eye-slash' : 'fa-eye'}`}></i>
              </button>
            </div>

            {error && (
              <div className="alert alert-danger small mt-3 mb-0" role="alert">
                <i className="fas fa-circle-exclamation me-1"></i>
                {error}
              </div>
            )}
          </div>

          <div className="modal-footer">
            <button type="button" className="btn btn-light" onClick={onClose} disabled={busy}>
              Vazgeç
            </button>
            <button type="submit" className="btn btn-danger" disabled={!canSubmit}>
              {busy ? (
                <>
                  <span className="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>
                  Siliniyor…
                </>
              ) : (
                <>
                  <i className="fas fa-trash-can me-1"></i>
                  Makbuzu sil ve stoğu geri al
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
