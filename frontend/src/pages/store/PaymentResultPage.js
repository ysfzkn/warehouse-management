import React, { useState, useEffect } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { FiCheckCircle, FiXCircle, FiLoader, FiShoppingBag, FiArrowLeft } from 'react-icons/fi';
import axios from 'axios';

export default function PaymentResultPage() {
  const [searchParams] = useSearchParams();
  const success = searchParams.get('success') === 'true';
  const token = searchParams.get('token');
  const error = searchParams.get('error');
  const [paymentDetails, setPaymentDetails] = useState(null);
  const [checking, setChecking] = useState(false);

  // If we have a token, try to get payment details from backend
  useEffect(() => {
    if (token) {
      setChecking(true);
      const delay = success ? 500 : 2000;
      const timer = setTimeout(() => {
        axios.get(`/api/store/payment/status-by-token?token=${token}`)
          .then(r => { if (r.data) setPaymentDetails(r.data); })
          .catch(() => {})
          .finally(() => setChecking(false));
      }, delay);
      return () => clearTimeout(timer);
    }
  }, [token, success]);

  const isSuccess = success || paymentDetails?.status === 'SUCCESS';

  // Success state
  if (isSuccess) {
    return (
      <div className="container my-5" style={{ maxWidth: 560 }}>
        <div className="text-center">
          <div style={{
            width: 80, height: 80, borderRadius: '50%',
            background: 'linear-gradient(135deg, #ecfdf5, #d1fae5)',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            marginBottom: 20, animation: 'scaleIn 0.4s ease',
          }}>
            <FiCheckCircle size={40} color="#059669" />
          </div>
          <h2 className="fw-bold" style={{ color: '#059669' }}>Ödeme Başarılı!</h2>
          <p className="text-muted mt-3" style={{ fontSize: '0.95rem', lineHeight: 1.6 }}>
            Siparişiniz başarıyla oluşturuldu. Sipariş detaylarını hesabınızdan takip edebilirsiniz.
          </p>
          <div className="d-flex gap-3 justify-content-center mt-4">
            <Link to="/store/siparislerim" className="btn btn-primary px-4">
              <FiShoppingBag className="me-2" />Siparişlerim
            </Link>
            <Link to="/store" className="btn btn-outline-secondary px-4">Alışverişe Devam Et</Link>
          </div>
        </div>
      </div>
    );
  }

  // Checking state
  if (checking) {
    return (
      <div className="container my-5 text-center" style={{ maxWidth: 560 }}>
        <FiLoader size={48} className="text-primary mb-3" style={{ animation: 'spin 1s linear infinite' }} />
        <h4 className="text-muted">Ödeme durumu kontrol ediliyor...</h4>
        <style>{`@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }`}</style>
      </div>
    );
  }

  // Failure state
  const errorMessages = {
    'config_not_found': 'Ödeme yapılandırması bulunamadı.',
    'hash_failed': 'Güvenlik doğrulaması başarısız oldu.',
    'verification_error': 'Ödeme doğrulama sırasında hata oluştu.',
  };

  return (
    <div className="container my-5" style={{ maxWidth: 560 }}>
      <div className="text-center">
        <div style={{
          width: 80, height: 80, borderRadius: '50%',
          background: 'linear-gradient(135deg, #fef2f2, #fecaca)',
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          marginBottom: 20, animation: 'scaleIn 0.4s ease',
        }}>
          <FiXCircle size={40} color="#dc2626" />
        </div>
        <h2 className="fw-bold" style={{ color: '#dc2626' }}>Ödeme Başarısız</h2>
        <p className="text-muted mt-3" style={{ fontSize: '0.95rem', lineHeight: 1.6 }}>
          {error && errorMessages[error]
            ? errorMessages[error]
            : paymentDetails?.errorMessage
              ? paymentDetails.errorMessage
              : 'Ödeme işleminiz tamamlanamadı. Lütfen tekrar deneyin veya farklı bir ödeme yöntemi seçin.'}
        </p>

        {/* İşlem detayı */}
        {token && (
          <div className="alert alert-light small text-start mt-3" style={{ fontSize: '0.8rem', borderRadius: 10 }}>
            <strong>İşlem Detayı:</strong>
            <div className="mt-1">Token: <code>{token}</code></div>
            {error && <div>Hata Kodu: <code>{error}</code></div>}
            {paymentDetails?.errorCode && <div>Hata Kodu: <code>{paymentDetails.errorCode}</code></div>}
            {paymentDetails?.errorMessage && <div>Mesaj: {paymentDetails.errorMessage}</div>}
            {paymentDetails?.status && <div>Durum: <code>{paymentDetails.status}</code></div>}
          </div>
        )}

        <div className="d-flex gap-3 justify-content-center mt-4">
          <Link to="/store/sepet" className="btn btn-primary px-4">
            <FiArrowLeft className="me-2" />Sepete Dön
          </Link>
          <Link to="/store" className="btn btn-outline-secondary px-4">Anasayfa</Link>
        </div>
      </div>
    </div>
  );
}
