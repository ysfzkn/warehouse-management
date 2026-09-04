import React, { useCallback, useEffect, useRef, useState } from 'react';
import axios from 'axios';

/**
 * Delivery receipt panel, shown inside the transfer detail modal.
 *
 * Covers the whole paper trail: issue the receipt, print or download it, record who
 * took delivery, and file the photograph of the signed page that comes back with the
 * driver.
 */

const STATUS_META = {
  ISSUED: { label: 'Düzenlendi', cls: 'bg-primary-subtle text-primary-emphasis', icon: 'fa-file-invoice' },
  DELIVERED: {
    label: 'Teslim Edildi',
    cls: 'bg-success-subtle text-success-emphasis',
    icon: 'fa-circle-check',
  },
  CANCELLED: { label: 'İptal', cls: 'bg-danger-subtle text-danger-emphasis', icon: 'fa-ban' },
};

const formatDateTime = (value) => {
  if (!value) return '-';
  try {
    return new Date(value).toLocaleString('tr-TR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return value;
  }
};

const formatSize = (bytes) => {
  if (!bytes && bytes !== 0) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

/** Local datetime string the <input type="datetime-local"> control expects. */
const toLocalInput = (date) => {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

export default function DeliveryReceiptPanel({ transfer, isAdmin = false, onChanged }) {
  const transferId = transfer?.id;

  const [receipt, setReceipt] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [showConfirmForm, setShowConfirmForm] = useState(false);
  const [confirmForm, setConfirmForm] = useState({
    deliveredByName: '',
    receivedByName: '',
    deliveredAt: toLocalInput(new Date()),
    note: '',
  });
  const fileInputRef = useRef(null);

  const load = useCallback(async () => {
    if (!transferId) return;
    setLoading(true);
    setError('');
    try {
      const res = await axios.get(`/api/admin/stock-transfers/${transferId}/receipt`);
      // 204 means the shipment has no receipt yet — not an error state.
      setReceipt(res.status === 204 ? null : res.data);
    } catch (e) {
      setError(e?.response?.data?.message || 'Makbuz bilgisi alınamadı.');
    } finally {
      setLoading(false);
    }
  }, [transferId]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!notice) return undefined;
    const timer = setTimeout(() => setNotice(''), 4000);
    return () => clearTimeout(timer);
  }, [notice]);

  const runAction = async (key, fn, successMessage) => {
    setBusy(key);
    setError('');
    try {
      const result = await fn();
      if (successMessage) setNotice(successMessage);
      if (onChanged) onChanged();
      return result;
    } catch (e) {
      setError(e?.response?.data?.message || 'İşlem tamamlanamadı.');
      return null;
    } finally {
      setBusy('');
    }
  };

  const handleIssue = () =>
    runAction(
      'issue',
      async () => {
        const res = await axios.post(`/api/admin/stock-transfers/${transferId}/receipt`);
        setReceipt(res.data);
        return res.data;
      },
      receipt ? 'Makbuz yeniden basıldı.' : 'Makbuz düzenlendi.'
    );

  /**
   * Opens the printable page.
   *
   * The window is opened synchronously, before the request — a `window.open` after an
   * await is treated as an unrequested popup and blocked. The markup then arrives over
   * an authenticated request rather than a shareable link, so a page listing customer
   * names and addresses never lands in browser history.
   */
  const handlePrint = async () => {
    const printWindow = window.open('', '_blank');
    if (!printWindow) {
      setError('Yazdırma penceresi açılamadı. Tarayıcınızın açılır pencere engelini kontrol edin.');
      return;
    }
    printWindow.document.write(
      '<!doctype html><html lang="tr"><head><meta charset="utf-8"><title>Makbuz hazırlanıyor…</title></head><body style="font-family:sans-serif;padding:24px">Makbuz hazırlanıyor…</body></html>'
    );
    setBusy('print');
    setError('');
    try {
      const res = await axios.get(`/api/admin/stock-transfers/${transferId}/receipt/print`, {
        responseType: 'text',
      });
      printWindow.document.open();
      printWindow.document.write(res.data);
      printWindow.document.close();
      setTimeout(() => {
        try {
          printWindow.focus();
          printWindow.print();
        } catch {
          /* the page carries its own Yazdır button as a fallback */
        }
      }, 500);
    } catch (e) {
      printWindow.close();
      setError(e?.response?.data?.message || 'Makbuz açılamadı.');
    } finally {
      setBusy('');
    }
  };

  const handleDownload = () =>
    runAction('pdf', async () => {
      const res = await axios.get(`/api/admin/stock-transfers/${transferId}/receipt/pdf`, {
        responseType: 'blob',
      });
      const url = window.URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }));
      const link = document.createElement('a');
      link.href = url;
      link.download = `makbuz-${receipt?.receiptNo || transferId}.pdf`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    });

  const openConfirmForm = () => {
    // Geriye dönük makbuzlarda tarih önemli: sevkiyat geçen ay çıktıysa formun bugünü
    // önermesi, tam da malın ne zaman el değiştirdiğini kaydetmek için var olan belgeye
    // yanlış tarih yazdırır. Sırayla: daha önce girilmiş tarih, sevkiyatın tamamlanma
    // tarihi, en son bugün.
    const existing = receipt?.deliveredAt || transfer?.completedDate || null;
    setConfirmForm({
      deliveredByName: receipt?.deliveredByName || transfer?.driverName || '',
      receivedByName: receipt?.receivedByName || '',
      deliveredAt: toLocalInput(existing ? new Date(existing) : new Date()),
      note: receipt?.receivedByNote || '',
    });
    setShowConfirmForm(true);
  };

  const handleConfirm = async (event) => {
    event.preventDefault();
    if (!confirmForm.receivedByName.trim()) {
      setError('Teslim alan kişinin adı soyadı zorunludur.');
      return;
    }
    const saved = await runAction(
      'confirm',
      async () => {
        const res = await axios.post(`/api/admin/stock-transfers/${transferId}/receipt/confirm`, {
          deliveredByName: confirmForm.deliveredByName.trim() || null,
          receivedByName: confirmForm.receivedByName.trim(),
          deliveredAt: confirmForm.deliveredAt ? `${confirmForm.deliveredAt}:00` : null,
          note: confirmForm.note.trim() || null,
        });
        setReceipt(res.data);
        return res.data;
      },
      'Teslimat onaylandı.'
    );
    if (saved) setShowConfirmForm(false);
  };

  const handleUpload = (file) => {
    if (!file) return;
    const form = new FormData();
    form.append('file', file);
    runAction(
      'upload',
      async () => {
        const res = await axios.post(`/api/admin/stock-transfers/${transferId}/receipt/attachments`, form, {
          headers: { 'Content-Type': 'multipart/form-data' },
        });
        setReceipt(res.data);
      },
      'İmzalı nüsha yüklendi.'
    );
  };

  const handleDeleteAttachment = (attachmentId) => {
    if (!window.confirm('Bu dosya kalıcı olarak silinecek. Emin misiniz?')) return;
    runAction(
      `del-${attachmentId}`,
      async () => {
        await axios.delete(`/api/admin/delivery-receipts/attachments/${attachmentId}`);
        await load();
      },
      'Dosya silindi.'
    );
  };

  if (!transferId) return null;

  const status = STATUS_META[receipt?.status] || STATUS_META.ISSUED;
  const anyBusy = Boolean(busy);

  return (
    <div className="border rounded-3 p-3 mt-3">
      <div className="d-flex justify-content-between align-items-start flex-wrap gap-2 mb-2">
        <div>
          <small className="text-muted text-uppercase d-block">Teslimat Makbuzu</small>
          {receipt ? (
            <div className="d-flex align-items-center gap-2 flex-wrap mt-1">
              <span className="fw-bold">{receipt.receiptNo}</span>
              <span className={`badge rounded-pill ${status.cls}`}>
                <i className={`fas ${status.icon} me-1`}></i>
                {status.label}
              </span>
              {receipt.revision > 1 && (
                <span className="badge rounded-pill bg-warning-subtle text-warning-emphasis">
                  {receipt.revision}. basım
                </span>
              )}
              {receipt.signedCopyOnFile ? (
                <span className="badge rounded-pill bg-success-subtle text-success-emphasis">
                  <i className="fas fa-paperclip me-1"></i>
                  İmzalı nüsha var
                </span>
              ) : (
                <span className="badge rounded-pill bg-secondary-subtle text-secondary-emphasis">
                  <i className="fas fa-hourglass-half me-1"></i>
                  İmzalı nüsha bekleniyor
                </span>
              )}
            </div>
          ) : (
            <div className="small text-muted mt-1">Bu sevkiyat için henüz makbuz düzenlenmemiş.</div>
          )}
        </div>

        <div className="d-flex gap-2 flex-wrap">
          {!receipt && (
            <button
              type="button"
              className="btn btn-sm btn-primary"
              disabled={anyBusy || loading}
              onClick={handleIssue}
            >
              <i
                className={`fas ${busy === 'issue' ? 'fa-spinner fa-spin' : 'fa-file-circle-plus'} me-1`}
              ></i>
              Makbuz Düzenle
            </button>
          )}
          {receipt && (
            <>
              <button
                type="button"
                className="btn btn-sm btn-primary"
                disabled={anyBusy}
                onClick={handlePrint}
              >
                <i className={`fas ${busy === 'print' ? 'fa-spinner fa-spin' : 'fa-print'} me-1`}></i>
                Yazdır
              </button>
              <button
                type="button"
                className="btn btn-sm btn-outline-primary"
                disabled={anyBusy}
                onClick={handleDownload}
              >
                <i className={`fas ${busy === 'pdf' ? 'fa-spinner fa-spin' : 'fa-file-pdf'} me-1`}></i>
                PDF İndir
              </button>
              <button
                type="button"
                className="btn btn-sm btn-outline-secondary"
                disabled={anyBusy}
                title="Şoför veya adres değiştiyse makbuzu güncel bilgilerle yeniden basar. Numara değişmez."
                onClick={handleIssue}
              >
                <i className={`fas ${busy === 'issue' ? 'fa-spinner fa-spin' : 'fa-rotate'} me-1`}></i>
                Yeniden Bas
              </button>
            </>
          )}
        </div>
      </div>

      {loading && (
        <div className="small text-muted">
          <i className="fas fa-spinner fa-spin me-1"></i>
          Yükleniyor…
        </div>
      )}

      {error && (
        <div className="alert alert-danger py-2 px-3 small mb-2">
          <i className="fas fa-triangle-exclamation me-1"></i>
          {error}
        </div>
      )}
      {notice && (
        <div className="alert alert-success py-2 px-3 small mb-2">
          <i className="fas fa-circle-check me-1"></i>
          {notice}
        </div>
      )}

      {receipt && (
        <>
          <div className="row g-2 small mt-1">
            <div className="col-sm-6 col-lg-3">
              <div className="text-muted">Düzenleyen</div>
              <div className="fw-semibold">{receipt.issuedBy || '-'}</div>
              <div className="text-muted">{formatDateTime(receipt.issuedAt)}</div>
            </div>
            <div className="col-sm-6 col-lg-3">
              <div className="text-muted">Teslim Eden</div>
              <div className="fw-semibold">{receipt.deliveredByName || '-'}</div>
            </div>
            <div className="col-sm-6 col-lg-3">
              <div className="text-muted">Teslim Alan</div>
              <div className="fw-semibold">{receipt.receivedByName || '—'}</div>
            </div>
            <div className="col-sm-6 col-lg-3">
              <div className="text-muted">Teslim Tarihi</div>
              <div className="fw-semibold">{formatDateTime(receipt.deliveredAt)}</div>
            </div>
          </div>

          {receipt.receivedByNote && (
            <div className="small text-muted mt-2">
              <i className="fas fa-comment-dots me-1"></i>
              {receipt.receivedByNote}
            </div>
          )}

          {/* ── Teslim onayı ── */}
          <div className="mt-3">
            {!showConfirmForm ? (
              <button
                type="button"
                className={`btn btn-sm ${receipt.receivedByName ? 'btn-outline-secondary' : 'btn-success'}`}
                disabled={anyBusy}
                onClick={openConfirmForm}
              >
                <i className="fas fa-signature me-1"></i>
                {receipt.receivedByName ? 'Teslim Bilgisini Düzenle' : 'Teslimatı Onayla'}
              </button>
            ) : (
              <form className="border rounded-3 p-3 bg-light" onSubmit={handleConfirm}>
                <div className="row g-2">
                  <div className="col-md-6">
                    <label className="form-label small mb-1">Teslim Eden (Adı Soyadı)</label>
                    <input
                      type="text"
                      className="form-control form-control-sm"
                      value={confirmForm.deliveredByName}
                      onChange={(e) =>
                        setConfirmForm((prev) => ({ ...prev, deliveredByName: e.target.value }))
                      }
                      placeholder="Şoför veya teslim eden personel"
                    />
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small mb-1">
                      Teslim Alan (Adı Soyadı) <span className="text-danger">*</span>
                    </label>
                    <input
                      type="text"
                      className="form-control form-control-sm"
                      value={confirmForm.receivedByName}
                      required
                      onChange={(e) =>
                        setConfirmForm((prev) => ({ ...prev, receivedByName: e.target.value }))
                      }
                      placeholder="Makbuzu imzalayan kişi"
                    />
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small mb-1">Teslim Tarihi</label>
                    <input
                      type="datetime-local"
                      className="form-control form-control-sm"
                      value={confirmForm.deliveredAt}
                      max={toLocalInput(new Date())}
                      onChange={(e) => setConfirmForm((prev) => ({ ...prev, deliveredAt: e.target.value }))}
                    />
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small mb-1">Not</label>
                    <input
                      type="text"
                      className="form-control form-control-sm"
                      value={confirmForm.note}
                      maxLength={500}
                      onChange={(e) => setConfirmForm((prev) => ({ ...prev, note: e.target.value }))}
                      placeholder="İsteğe bağlı"
                    />
                  </div>
                </div>
                <div className="d-flex gap-2 mt-3">
                  <button type="submit" className="btn btn-sm btn-success" disabled={anyBusy}>
                    <i className={`fas ${busy === 'confirm' ? 'fa-spinner fa-spin' : 'fa-check'} me-1`}></i>
                    Kaydet
                  </button>
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-secondary"
                    onClick={() => setShowConfirmForm(false)}
                  >
                    Vazgeç
                  </button>
                </div>
              </form>
            )}
          </div>

          {/* ── İmzalı nüsha ── */}
          <div className="mt-3 pt-3 border-top">
            <div className="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-2">
              <small className="text-muted text-uppercase">
                İmzalı Nüsha ({receipt.attachments?.length || 0})
              </small>
              <label className={`btn btn-sm btn-outline-primary mb-0 ${anyBusy ? 'disabled' : ''}`}>
                <i
                  className={`fas ${busy === 'upload' ? 'fa-spinner fa-spin' : 'fa-cloud-arrow-up'} me-1`}
                ></i>
                Fotoğraf / PDF Yükle
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="image/jpeg,image/png,image/webp,application/pdf"
                  className="d-none"
                  disabled={anyBusy}
                  onChange={(e) => {
                    const file = e.target.files && e.target.files[0];
                    e.target.value = '';
                    handleUpload(file);
                  }}
                />
              </label>
            </div>

            {!receipt.attachments?.length ? (
              <div className="small text-muted">
                Şoför imzalı nüshayı getirdiğinde buraya yükleyin. Fotoğraf ya da taranmış PDF olabilir.
              </div>
            ) : (
              <div className="d-flex flex-wrap gap-2">
                {receipt.attachments.map((att) => {
                  const isPdf = att.contentType === 'application/pdf';
                  return (
                    <div key={att.id} className="border rounded-3 p-2 text-center" style={{ width: 140 }}>
                      <a
                        href={att.url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="d-block text-decoration-none"
                        title="Yeni sekmede aç"
                      >
                        {isPdf ? (
                          <div
                            className="d-flex align-items-center justify-content-center bg-light rounded-2"
                            style={{ height: 90 }}
                          >
                            <i className="fas fa-file-pdf fa-2x text-danger"></i>
                          </div>
                        ) : (
                          <img
                            src={att.url}
                            alt="İmzalı nüsha"
                            className="rounded-2"
                            style={{ width: '100%', height: 90, objectFit: 'cover' }}
                          />
                        )}
                      </a>
                      <div className="small text-muted mt-1 text-truncate" title={att.fileName}>
                        {att.fileName || 'nüsha'}
                      </div>
                      <div className="text-muted" style={{ fontSize: '0.7rem' }}>
                        {formatSize(att.sizeBytes)} · {formatDateTime(att.uploadedAt)}
                      </div>
                      {isAdmin && (
                        <button
                          type="button"
                          className="btn btn-link btn-sm text-danger p-0"
                          style={{ fontSize: '0.7rem' }}
                          disabled={anyBusy}
                          onClick={() => handleDeleteAttachment(att.id)}
                        >
                          <i className="fas fa-trash-alt me-1"></i>
                          Sil
                        </button>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
