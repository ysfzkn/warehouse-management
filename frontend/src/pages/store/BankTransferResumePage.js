import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import BankTransferInfo from '../../components/store/BankTransferInfo';

/**
 * Reached when the customer clicks "My Orders → Continue Payment".
 * Re-fetches the IBAN/QR/reference details of the pending bank transfer
 * order from the backend and renders them via the BankTransferInfo component.
 *
 * Flow:
 *   GET /api/store/payment/bank-transfer-details/:orderId
 *   → 200: { bankName, iban, accountHolder, reference, amount, deadline, qrEnabled?, qrImage?, ... }
 *   → 400: { message: "Order is no longer awaiting payment" } — approved by admin / cancelled
 *   → 404: { message: "Order not found" } — either missing or belongs to another customer
 */
export default function BankTransferResumePage() {
  const { orderId } = useParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [details, setDetails] = useState(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    const token = localStorage.getItem('customer_token');
    if (!token) {
      setError({ type: 'auth', message: 'Bu sayfayı görüntülemek için giriş yapın.' });
      setLoading(false);
      return;
    }

    axios.get(`/api/store/payment/bank-transfer-details/${orderId}`, {
      headers: { Authorization: `Bearer ${token}` }
    })
      .then(r => {
        if (!cancelled) {
          setDetails(r.data);
          setLoading(false);
        }
      })
      .catch(e => {
        if (cancelled) return;
        const status = e.response?.status;
        const msg = e.response?.data?.message || 'Ödeme bilgileri alınamadı.';
        if (status === 400) {
          setError({ type: 'expired', message: msg, currentStatus: e.response?.data?.currentStatus });
        } else if (status === 404) {
          setError({ type: 'notfound', message: msg });
        } else if (status === 401) {
          setError({ type: 'auth', message: 'Oturumunuzun süresi dolmuş, tekrar giriş yapın.' });
        } else {
          setError({ type: 'generic', message: msg });
        }
        setLoading(false);
      });

    return () => { cancelled = true; };
  }, [orderId]);

  if (loading) {
    return (
      <div className="container my-5 text-center" style={{ maxWidth: 600 }}>
        <div className="spinner-border text-primary" role="status" />
        <p className="text-muted mt-3">Ödeme bilgileri yükleniyor...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="container my-5" style={{ maxWidth: 600 }}>
        <div className={`card border-0 shadow-sm`} style={{ borderRadius: 16 }}>
          <div className="card-body p-4 text-center">
            <div style={{
              width: 64, height: 64, borderRadius: '50%',
              background: error.type === 'expired' ? '#fef3c7' : '#fee2e2',
              display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
              marginBottom: 16
            }}>
              <i className={`fas ${error.type === 'expired' ? 'fa-clock' : 'fa-exclamation-circle'}`}
                 style={{ fontSize: 28, color: error.type === 'expired' ? '#d97706' : '#dc2626' }} />
            </div>
            <h4 className="fw-bold mb-2">
              {error.type === 'expired' && 'Sipariş Durumu Değişmiş'}
              {error.type === 'notfound' && 'Sipariş Bulunamadı'}
              {error.type === 'auth' && 'Giriş Gerekli'}
              {error.type === 'generic' && 'Bir Hata Oluştu'}
            </h4>
            <p className="text-muted mb-3">{error.message}</p>
            {error.currentStatus && (
              <p className="small text-muted">
                Güncel durum: <strong>{error.currentStatus}</strong>
              </p>
            )}
            <div className="d-flex gap-2 justify-content-center">
              {error.type === 'auth' && (
                <button className="btn btn-primary" onClick={() => navigate('/giris')}>
                  Giriş Yap
                </button>
              )}
              <button className="btn btn-outline-secondary" onClick={() => navigate('/siparislerim')}>
                Siparişlerime Dön
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="container my-4" style={{ maxWidth: 700 }}>
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h3 fw-bold mb-0">Ödemeye Devam Et</h1>
        <button className="btn btn-sm btn-outline-secondary" onClick={() => navigate('/siparislerim')}>
          <i className="fas fa-arrow-left me-1" />Siparişlerime Dön
        </button>
      </div>
      <div className="alert alert-info small mb-3">
        <i className="fas fa-info-circle me-2" />
        Aşağıdaki bilgilerle bankanızdan havale/EFT yapın. Ödemeniz alındıktan sonra
        siparişiniz "Hazırlanıyor" durumuna geçecek ve email bildirimi alacaksınız.
      </div>
      <BankTransferInfo bankDetails={details} deadline={details.deadline} />
    </div>
  );
}
