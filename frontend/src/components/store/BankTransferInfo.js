import React, { useState, useEffect } from 'react';
import { FiCopy, FiCheck, FiClock } from 'react-icons/fi';

export default function BankTransferInfo({ bankDetails, deadline }) {
  const [copied, setCopied] = useState(null);
  const [timeLeft, setTimeLeft] = useState('');

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

        <div className="alert alert-info mt-3 mb-0" role="alert">
          <strong>Önemli:</strong> Havale veya EFT yaparken açıklama kısmına yukarıdaki referans kodunu yazmayı unutmayın.
          Ödemeniz yönetici tarafından onaylandıktan sonra siparişiniz hazırlanma aşamasına geçecektir.
        </div>
      </div>
    </div>
  );
}
