import React, { useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import SearchableSelect from '../components/SearchableSelect';

const DesiCalculator = () => {
  const [selectedProductId, setSelectedProductId] = useState(null);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleFetch = async () => {
    if (!selectedProductId) return;
    try {
      setLoading(true);
      setError('');
      const res = await axios.get(`/api/products/${selectedProductId}/desi`);
      setResult(res.data);
    } catch (e) {
      setError(e.response?.data || 'Hesaplama sırasında hata oluştu');
      setResult(null);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-4">
        <h2>Desi Hesaplama</h2>
      </div>

      <div className="row mb-4">
        <div className="col-md-8">
          <SearchableSelect
            label="Ürün Seç"
            value={selectedProductId}
            onChange={(id) => setSelectedProductId(id)}
            searchEndpoint="/api/products/search"
            placeholder="Ürün adı ara..."
          />
        </div>
        <div className="col-md-4 d-flex align-items-end">
          <button className="btn btn-primary w-100" onClick={handleFetch} disabled={!selectedProductId || loading}>
            {loading ? (
              <>
                <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                Hesaplanıyor...
              </>
            ) : (
              <>
                <i className="fas fa-calculator me-2"></i>
                Hesapla
              </>
            )}
          </button>
        </div>
      </div>

      {error && (
        <div className="alert alert-danger">{error}</div>
      )}

      {result && (
        <div className="card">
          <div className="card-body">
            <h5 className="card-title mb-3">{result.name} <span className="text-muted">({result.sku})</span></h5>
            <div className="row">
              <div className="col-md-3 mb-3">
                <div className="text-muted small">En (cm)</div>
                <div className="fw-bold">{result.widthCm}</div>
              </div>
              <div className="col-md-3 mb-3">
                <div className="text-muted small">Boy (cm)</div>
                <div className="fw-bold">{result.lengthCm}</div>
              </div>
              <div className="col-md-3 mb-3">
                <div className="text-muted small">Yükseklik (cm)</div>
                <div className="fw-bold">{result.heightCm}</div>
              </div>
              <div className="col-md-3 mb-3">
                <div className="text-muted small">Desi</div>
                <div className="fw-bold">{Number(result.desi).toFixed(2)}</div>
              </div>
            </div>
            <div className="row">
              <div className="col-md-6 mb-3">
                <div className="text-muted small">Kargo Birim Ücreti</div>
                <div className="fw-bold">₺{Number(result.shippingRate).toFixed(2)}</div>
              </div>
              <div className="col-md-6 mb-3">
                <div className="text-muted small">Toplam Kargo Ücreti</div>
                <div className="fw-bold">₺{Number(result.shippingCost).toFixed(2)}</div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default DesiCalculator;


