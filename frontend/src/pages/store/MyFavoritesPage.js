import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';
import { useToast } from '../../components/store/Toast';

const fmt = (p) => p != null ? new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(p) : '—';

const getAuthHeaders = () => {
  const t = localStorage.getItem('customer_token');
  return t ? { Authorization: `Bearer ${t}` } : {};
};

export default function MyFavoritesPage() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const toast = useToast();

  const fetch = useCallback(() => {
    setLoading(true);
    axios.get('/api/store/wishlist', { headers: getAuthHeaders() })
      .then(r => setItems(r.data || []))
      .catch(() => {}).finally(() => setLoading(false));
  }, []);

  useEffect(() => { fetch(); }, [fetch]);

  const handleRemove = async (productId) => {
    try {
      await axios.delete(`/api/store/wishlist/${productId}`, { headers: getAuthHeaders() });
      setItems(prev => prev.filter(i => i.productId !== productId));
      toast.success('Ürün favorilerden çıkarıldı.');
    } catch (e) { toast.error(e.response?.data?.message || 'İşlem başarısız.'); }
  };

  return (
    <div className="container py-4" style={{ maxWidth: 800 }}>
      <h2 className="fw-bold mb-4"><i className="fas fa-heart me-2 text-danger" />Favorilerim</h2>

      {loading ? <div className="text-center py-5"><span className="spinner-border" /></div>
      : items.length === 0 ? (
        <div className="card border-0 shadow-sm">
          <div className="card-body text-center py-5">
            <i className="fas fa-heart text-muted fa-3x mb-3 opacity-25" />
            <p className="text-muted mb-3">Favorilerinize henüz ürün eklemediniz.</p>
            <Link to="/" className="btn btn-primary">Ürünleri Keşfet</Link>
          </div>
        </div>
      ) : (
        <div className="row g-3">
          {items.map(item => (
            <div key={item.id} className="col-md-6">
              <div className="card border-0 shadow-sm h-100">
                <div className="card-body d-flex gap-3">
                  <Link to={`/urun/${item.productSlug}`} className="flex-shrink-0">
                    {item.imageUrl ? (
                      <img src={item.imageUrl} alt="" style={{ width: 80, height: 80, objectFit: 'contain', borderRadius: 8, background: '#f8f9fa' }} />
                    ) : (
                      <div style={{ width: 80, height: 80, borderRadius: 8, background: '#f8f9fa', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <i className="fas fa-image text-muted" />
                      </div>
                    )}
                  </Link>
                  <div className="flex-grow-1 min-w-0">
                    <Link to={`/urun/${item.productSlug}`} className="text-dark text-decoration-none">
                      <div className="fw-medium small text-truncate">{item.productName}</div>
                    </Link>
                    <div className="small text-muted">{item.productSku}</div>
                    <div className="mt-1">
                      {item.productSalePrice && item.productSalePrice < item.productPrice ? (
                        <><span className="text-danger fw-bold">{fmt(item.productSalePrice)}</span> <del className="text-muted small">{fmt(item.productPrice)}</del></>
                      ) : (
                        <span className="fw-bold">{fmt(item.productPrice)}</span>
                      )}
                    </div>
                  </div>
                  <div className="d-flex flex-column gap-1">
                    <Link to={`/urun/${item.productSlug}`} className="btn btn-sm btn-primary"><i className="fas fa-eye" /></Link>
                    <button className="btn btn-sm btn-outline-danger" onClick={() => handleRemove(item.productId)} title="Favorilerden çıkar"><i className="fas fa-heart-broken" /></button>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
