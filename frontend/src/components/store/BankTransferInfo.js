import React, { useState, useEffect } from 'react';
import { FiCopy, FiCheck, FiClock } from 'react-icons/fi';

export default function BankTransferInfo({ bankDetails, deadline }) {
  const [copied, setCopied] = useState(null);
  const [timeLeft, setTimeLeft] = useState('');
  // Current tab: 'iban' (default) or 'qr' (if enabled by admin)
  const qrEnabled = bankDetails?.qrEnabled === 'true' || bankDetails?.qrEnabled === true;
  const [tab, setTab] = useState(qrEnabled ? 'qr' : 'iban');

  useEffect(() => {
    if (!deadline) return;
    const update = () => {
      const now = new Date();
      const end = new Date(deadline);
      const diff = end - now;
      if (diff <= 0) { setTimeLeft('Süre doldu'); return; }
      const hours = Math.floor(diff / 3600000);
      const mins = Math.floor((diff % 3600000) / 60000);
      setTimeLeft(`${hours} saat ${mins} dakika`);
    };
    update();
    const timer = setInterval(update, 60000);
    return () => clearInterval(timer);
  }, [deadline]);

  const copyToClipboard = (text, field) => {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(field);
      setTimeout(() => setCopied(null), 2000);
    });
  };

  if (!bankDetails) return null;

  return (
    <div className="card border-primary">
      <div className="card-header bg-primary text-white">
        <h5 className="mb-0">Havale / EFT Bilgileri</h5>
      </div>
      <div className="card-body">
        <div className="alert alert-warning d-flex align-items-center" role="alert">
          <FiClock className="me-2" size={20} />
          <div>
            <strong>Ödeme süresi:</strong> {timeLeft}
            <br />
            <small>Bu süre içinde ödeme yapılmazsa siparişiniz otomatik olarak iptal edilir.</small>
          </div>
        </div>

        {/* Tabs — two options when QR is enabled, otherwise IBAN only */}
        {qrEnabled && (
          <ul className="nav nav-pills mb-3" role="tablist">
            <li className="nav-item">
              <button
                className={`nav-link ${tab === 'qr' ? 'active' : ''}`}
                onClick={() => setTab('qr')}
                type="button"
              >
                <i className="fas fa-qrcode me-2" />Karekod ile Öde
                <span className="badge bg-warning text-dark ms-2" style={{ fontSize: 10 }}>Hızlı</span>
              </button>
            </li>
            <li className="nav-item">
              <button
                className={`nav-link ${tab === 'iban' ? 'active' : ''}`}
                onClick={() => setTab('iban')}
                type="button"
              >
                <i className="fas fa-university me-2" />IBAN ile Öde
              </button>
            </li>
          </ul>
        )}

        {/* QR Tab */}
        {tab === 'qr' && qrEnabled && (
          <div className="text-center">
            <div className="mb-3">
              <img
                src="/api/store/settings/bank-transfer-qr"
                alt="FAST QR Kod"
                style={{
                  maxWidth: 260,
                  width: '100%',
                  border: '2px solid #e9ecef',
                  borderRadius: 12,
                  padding: 12,
                  background: '#fff'
                }}
                onError={(e) => {
                  e.currentTarget.style.display = 'none';
                  const fb = e.currentTarget.nextSibling;
                  if (fb) fb.style.display = 'block';
                }}
              />
              <div style={{ display: 'none' }} className="alert alert-warning small">
                QR kod yüklenemedi. IBAN ile ödeme seçeneğini kullanın.
              </div>
            </div>

            {bankDetails.qrBankName && (
              <div className="mb-2">
                <span className="badge bg-secondary px-3 py-2">
                  <i className="fas fa-university me-1" />{bankDetails.qrBankName}
                </span>
              </div>
            )}

            <div className="alert alert-info text-start small mb-3" role="alert">
              <i className="fas fa-info-circle me-2" />
              {bankDetails.qrDescription || 'Bankanızın uygulamasında "QR ile İşlem" menüsünden okutun.'}
            </div>

            {/* Even if the QR pre-fills amount/reference, keeping them visible for the customer is safe */}
            <div className="card bg-light mb-2">
              <div className="card-body p-3">
                <div className="row g-2 text-start small">
                  <div className="col-6">
                    <div className="text-muted">Tutar</div>
                    <div className="fw-bold text-primary fs-6">{bankDetails.amount || '—'}</div>
                  </div>
                  <div className="col-6">
                    <div className="text-muted">Referans</div>
                    <div className="d-flex align-items-center gap-1">
                      <code className="small">{bankDetails.reference || '—'}</code>
                      <button
                        className="btn btn-sm btn-outline-secondary py-0 px-1"
                        onClick={() => copyToClipboard(bankDetails.reference, 'ref')}
                        aria-label="Kopyala"
                      >
                        {copied === 'ref' ? <FiCheck className="text-success" /> : <FiCopy />}
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <small className="text-muted d-block">
              QR'ı okutunca uygulamanız IBAN'ı otomatik dolduracaktır. Tutar ve referansı yukarıdaki bilgilere göre girin.
            </small>
          </div>
        )}

        {/* IBAN Tab (default, or when QR is unavailable) */}
        {tab === 'iban' && (
        <table className="table table-borderless mb-0">
          <tbody>
            <tr>
              <td className="text-muted fw-medium" style={{width:'35%'}}>Banka</td>
              <td><strong>{bankDetails.bankName || '—'}</strong></td>
            </tr>
            <tr>
              <td className="text-muted fw-medium">Hesap Sahibi</td>
              <td><strong>{bankDetails.accountHolder || '—'}</strong></td>
            </tr>
            <tr>
              <td className="text-muted fw-medium">IBAN</td>
              <td>
                <div className="d-flex align-items-center gap-2">
                  <code className="fs-6">{bankDetails.iban || '—'}</code>
                  <button className="btn btn-sm btn-outline-secondary" onClick={() => copyToClipboard(bankDetails.iban, 'iban')} aria-label="IBAN kopyala">
                    {copied === 'iban' ? <FiCheck className="text-success" /> : <FiCopy />}
                  </button>
                </div>
              </td>
            </tr>
            <tr>
              <td className="text-muted fw-medium">Tutar</td>
              <td><strong className="text-primary fs-5">{bankDetails.amount || '—'}</strong></td>
            </tr>
            <tr>
              <td className="text-muted fw-medium">Referans Kodu</td>
              <td>
                <div className="d-flex align-items-center gap-2">
                  <span className="badge bg-dark fs-6 font-monospace px-3 py-2">{bankDetails.reference || '—'}</span>
                  <button className="btn btn-sm btn-outline-secondary" onClick={() => copyToClipboard(bankDetails.reference, 'ref')} aria-label="Referans kodu kopyala">
                    {copied === 'ref' ? <FiCheck className="text-success" /> : <FiCopy />}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        )}

        <div className="alert alert-info mt-3 mb-0" role="alert">
          <strong>Önemli:</strong> Havale veya EFT yaparken açıklama kısmına yukarıdaki referans kodunu yazmayı unutmayın.
          Ödemeniz yönetici tarafından onaylandıktan sonra siparişiniz hazırlanma aşamasına geçecektir.
        </div>
      </div>
    </div>
  );
}
