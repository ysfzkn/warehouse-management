import React from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { FiCheckCircle, FiXCircle } from 'react-icons/fi';

export default function PaymentResultPage() {
  const [searchParams] = useSearchParams();
  const success = searchParams.get('success') === 'true';
  if (success) {
    return (
      <div className="container my-5 text-center" style={{ maxWidth: 600 }}>
        <FiCheckCircle size={64} className="text-success mb-3" />
        <h2 className="fw-bold text-success">Ödeme Başarılı!</h2>
        <p className="text-muted mt-3">
          Siparişiniz başarıyla oluşturuldu. Sipariş detaylarını hesabınızdan takip edebilirsiniz.
        </p>
        <div className="d-flex gap-3 justify-content-center mt-4">
          <Link to="/store" className="btn btn-primary">Alışverişe Devam Et</Link>
          <Link to="/store/siparislerim" className="btn btn-outline-secondary">Siparişlerim</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="container my-5 text-center" style={{ maxWidth: 600 }}>
      <FiXCircle size={64} className="text-danger mb-3" />
      <h2 className="fw-bold text-danger">Ödeme Başarısız</h2>
      <p className="text-muted mt-3">
        Ödeme işleminiz tamamlanamadı. Lütfen tekrar deneyin veya farklı bir ödeme yöntemi seçin.
      </p>
      <div className="d-flex gap-3 justify-content-center mt-4">
        <Link to="/store/sepet" className="btn btn-primary">Sepete Dön</Link>
        <Link to="/store" className="btn btn-outline-secondary">Anasayfa</Link>
      </div>
    </div>
  );
}
