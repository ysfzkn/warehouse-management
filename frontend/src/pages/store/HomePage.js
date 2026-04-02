import React, { useState, useEffect } from 'react';
import { Link, useOutletContext } from 'react-router-dom';
import axios from 'axios';
import HeroBanner from '../../components/store/HeroBanner';
import ProductGrid from '../../components/store/ProductGrid';
import { FiShoppingBag, FiArrowRight } from 'react-icons/fi';

export default function HomePage() {
  const { cart, siteSettings } = useOutletContext();
  const [featured, setFeatured] = useState([]);
  const [newProducts, setNewProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      axios.get('/api/store/products?size=8&sortBy=viewCount&sortDir=desc').catch(() => ({ data: { content: [] } })),
      axios.get('/api/store/products?size=8&sortBy=createdAt&sortDir=desc').catch(() => ({ data: { content: [] } })),
      axios.get('/api/store/categories/tree').catch(() => ({ data: [] })),
    ]).then(([featuredRes, newRes, catRes]) => {
      setFeatured(featuredRes.data?.content || []);
      setNewProducts(newRes.data?.content || []);
      setCategories(catRes.data || []);
    }).finally(() => setLoading(false));
  }, []);

  const hasContent = featured.length > 0 || newProducts.length > 0 || categories.length > 0;

  return (
    <div>
      {/* Hero Banner */}
      <div className="container mt-3">
        <HeroBanner />
      </div>

      {/* Empty Store Welcome */}
      {!loading && !hasContent && (
        <section className="container my-5">
          <div className="text-center py-5">
            <FiShoppingBag size={64} className="text-muted mb-4" style={{ opacity: 0.3 }} />
            <h2 className="fw-bold mb-3" style={{ color: 'var(--color-gray-700)' }}>
              Mağazamız Hazırlanıyor
            </h2>
            <p className="text-muted mb-4" style={{ maxWidth: 500, margin: '0 auto' }}>
              Çok yakında harika ürünlerle sizlerle buluşacağız. Takipte kalın!
            </p>
            <div className="d-flex gap-3 justify-content-center flex-wrap">
              {siteSettings.get('contact_phone') && (
                <a href={`tel:${siteSettings.get('contact_phone')}`} className="btn btn-outline-primary">
                  {siteSettings.get('contact_phone')}
                </a>
              )}
              <Link to="/store/sayfa/iletisim" className="btn btn-primary">
                İletişime Geçin
              </Link>
            </div>
          </div>
        </section>
      )}

      {/* Categories */}
      {categories.length > 0 && (
        <section className="container my-4">
          <div className="d-flex justify-content-between align-items-center mb-3">
            <h2 className="store-section-title mb-0">Kategoriler</h2>
            <Link to="/store/kategori/tumu" className="btn btn-sm btn-outline-primary d-none d-md-inline-flex align-items-center gap-1">
              Tümünü Gör <FiArrowRight size={14} />
            </Link>
          </div>
          <div className="row g-3">
            {categories.slice(0, 6).map(cat => (
              <div key={cat.id} className="col-6 col-md-4 col-lg-2">
                <Link to={`/store/kategori/${cat.slug}`} className="text-decoration-none">
                  <div className="store-category-card">
                    {cat.imageUrl ? (
                      <img src={cat.imageUrl} alt={cat.name} loading="lazy" />
                    ) : (
                      <div style={{ background: 'var(--color-gray-100)', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <FiShoppingBag size={32} style={{ color: 'var(--color-gray-400)' }} />
                      </div>
                    )}
                    <div className="store-category-card-overlay">
                      <h6 className="mb-0">{cat.name}</h6>
                    </div>
                  </div>
                </Link>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Featured Products */}
      {featured.length > 0 && (
        <section className="container my-4">
          <h2 className="store-section-title">Öne Çıkan Ürünler</h2>
          <ProductGrid products={featured} onAddToCart={(id) => cart.addItem(id)} />
        </section>
      )}

      {/* New Products */}
      {newProducts.length > 0 && (
        <section className="container my-4">
          <h2 className="store-section-title">Yeni Ürünler</h2>
          <ProductGrid products={newProducts} onAddToCart={(id) => cart.addItem(id)} />
        </section>
      )}
    </div>
  );
}
