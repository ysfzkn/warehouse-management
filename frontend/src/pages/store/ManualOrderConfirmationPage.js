import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import axios from 'axios';

const money = (value) =>
  new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(value || 0);

export default function ManualOrderConfirmationPage() {
  const { token } = useParams();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [acceptances, setAcceptances] = useState({ distance: false, preliminary: false, kvkk: false });

  useEffect(() => {
    axios
      .get(`/api/store/public/orders/confirm/${token}`)
      .then((r) => {
        setData(r.data);
        setConfirmed(Boolean(r.data?.confirmedAt));
      })
      .catch((e) => setError(e.response?.data?.message || 'Onay bağlantısı geçersiz veya süresi dolmuş.'))
      .finally(() => setLoading(false));
  }, [token]);

  const confirm = async () => {
    if (!acceptances.distance || !acceptances.preliminary || !acceptances.kvkk) {
      setError('Devam etmek için üç metni de okuyup onaylayın.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      const legalHashes = Object.fromEntries(
        Object.entries(data?.legalDocuments || {}).map(([slug, doc]) => [slug, doc.sha256])
      );
      await axios.post(`/api/store/public/orders/confirm/${token}`, { legalHashes });
      setConfirmed(true);
    } catch (e) {
      setError(e.response?.data?.message || 'Sipariş onaylanamadı.');
    } finally {
      setSaving(false);
    }
  };

  if (loading)
    return (
      <div className="container py-5 text-center">
        <div className="spinner-border text-primary" />
      </div>
    );
  if (error && !data)
    return (
      <div className="container py-5">
        <div className="alert alert-danger mx-auto" style={{ maxWidth: 700 }}>
          {error}
        </div>
      </div>
    );

  return (
    <div className="container py-5" style={{ maxWidth: 820 }}>
      <div className="card border-0 shadow-sm">
        <div className="card-body p-4 p-md-5">
          {confirmed ? (
            <div className="text-center py-4">
              <i className="fas fa-check-circle text-success mb-3" style={{ fontSize: 64 }} />
              <h2>Siparişiniz onaylandı</h2>
              <p className="text-muted">{data?.orderNumber} numaralı siparişiniz için onayınız kaydedildi.</p>
            </div>
          ) : (
            <>
              <div className="text-center mb-4">
                <i className="fas fa-file-signature text-primary mb-3" style={{ fontSize: 48 }} />
                <h2>Sipariş Onayı</h2>
                <p className="text-muted">
                  Merhaba {data?.customerName}, sipariş bilgilerinizi kontrol ederek onaylayın.
                </p>
              </div>
              <div className="rounded bg-light p-3 mb-3">
                <div className="d-flex justify-content-between">
                  <span>Sipariş</span>
                  <strong>{data?.orderNumber}</strong>
                </div>
                <div className="d-flex justify-content-between mt-2">
                  <span>Toplam</span>
                  <strong className="text-primary">{money(data?.grandTotal)}</strong>
                </div>
              </div>
              <div className="table-responsive mb-4">
                <table className="table align-middle">
                  <thead>
                    <tr>
                      <th>Ürün</th>
                      <th className="text-center">Adet</th>
                      <th className="text-end">Tutar</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(data?.items || []).map((item, i) => (
                      <tr key={i}>
                        <td>{item.name}</td>
                        <td className="text-center">{item.quantity}</td>
                        <td className="text-end">{money(item.lineTotal)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="alert alert-info small">
                <i className="fas fa-info-circle me-2" />
                Onayladığınızda{' '}
                <a href="/sayfa/mesafeli-satis-sozlesmesi" target="_blank" rel="noreferrer">
                  mesafeli satış sözleşmesini
                </a>
                ,{' '}
                <a href="/sayfa/on-bilgilendirme-formu" target="_blank" rel="noreferrer">
                  ön bilgilendirme formunu
                </a>{' '}
                ve{' '}
                <a href="/sayfa/kvkk-aydinlatma-metni" target="_blank" rel="noreferrer">
                  KVKK aydınlatma metnini
                </a>{' '}
                okuyup kabul ettiğiniz tarih ve saatle kaydedilir.
              </div>
              <div className="border rounded p-3 mb-3">
                {[
                  ['distance', 'Mesafeli satış sözleşmesini okudum ve kabul ediyorum.'],
                  ['preliminary', 'Ön bilgilendirme formunu okudum ve kabul ediyorum.'],
                  ['kvkk', 'KVKK aydınlatma metnini okudum.'],
                ].map(([key, label]) => (
                  <div className="form-check mb-2" key={key}>
                    <input
                      className="form-check-input"
                      type="checkbox"
                      id={`accept-${key}`}
                      checked={acceptances[key]}
                      onChange={(e) => setAcceptances({ ...acceptances, [key]: e.target.checked })}
                    />
                    <label className="form-check-label" htmlFor={`accept-${key}`}>
                      {label}
                    </label>
                  </div>
                ))}
              </div>
              {error && <div className="alert alert-danger py-2">{error}</div>}
              <button
                className="btn btn-success btn-lg w-100"
                disabled={saving || !Object.values(acceptances).every(Boolean)}
                onClick={confirm}
              >
                {saving ? 'Kaydediliyor…' : 'Okudum, Siparişi Onaylıyorum'}
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
