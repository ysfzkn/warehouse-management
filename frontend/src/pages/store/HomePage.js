import React, { useState, useEffect } from 'react';
import { Link, useOutletContext } from 'react-router-dom';
import axios from 'axios';
import HeroBanner from '../../components/store/HeroBanner';
import ProductCard from '../../components/store/ProductCard';
import SeoHead from '../../components/store/SeoHead';
import { useToast } from '../../components/store/Toast';
import { useSiteSettings } from '../../hooks/useSiteSettings';
import { buildOrganizationSchema, buildWebSiteSchema } from '../../utils/seo';
import { FiShoppingBag, FiArrowRight, FiStar, FiZap, FiTrendingUp } from 'react-icons/fi';

export default function HomePage() {
  const { cart } = useOutletContext();
  const toast = useToast();
  const { settings } = useSiteSettings();
  const [featured, setFeatured] = useState([]);
  const [newProducts, setNewProducts] = useState([]);
  const [saleProducts, setSaleProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      axios.get('/api/store/products?size=8&sortBy=viewCount&sortDir=desc').catch(() => ({ data: { content: [] } })),
      axios.get('/api/store/products?size=8&sortBy=createdAt&sortDir=desc').catch(() => ({ data: { content: [] } })),
      axios.get('/api/store/categories/tree').catch(() => ({ data: [] })),
    ]).then(([featuredRes, newRes, catRes]) => {
      const allFeatured = (featuredRes.data?.content || []);
      const allNew = (newRes.data?.content || []);
      setFeatured(allFeatured.filter(p => p.featured));
      setNewProducts(allNew.filter(p => p.isNew));
      // Sale products — those with salePrice < price
      setSaleProducts(allFeatured.filter(p => p.salePrice && p.salePrice > 0 && p.salePrice < p.price).slice(0, 8));
      // If no featured flag, show by view count
      if (allFeatured.filter(p => p.featured).length === 0) setFeatured(allFeatured.slice(0, 8));
      if (allNew.filter(p => p.isNew).length === 0) setNewProducts(allNew.slice(0, 8));
      setCategories(catRes.data || []);
    }).finally(() => setLoading(false));
  }, []);

  const handleAddToCart = async (id) => {
    try { await cart.addItem(id); toast.success('Ürün sepete eklendi'); }
    catch (e) { toast.error(e?.response?.data?.message || 'Sepete eklenemedi'); }
  };

  const hasContent = featured.length > 0 || newProducts.length > 0 || categories.length > 0;

  const ProductSection = ({ title, icon, products, linkTo, linkLabel, bgClass }) => (
    <section className={`store-section ${bgClass || ''}`}>
      <div className="container">
        <div className="d-flex justify-content-between align-items-center mb-3">
          <h2 className="store-section-title mb-0">
            {icon} {title}
          </h2>
          {linkTo && (
            <Link to={linkTo} className="btn btn-sm btn-outline-primary d-inline-flex align-items-center gap-1">
              {linkLabel || 'Tümünü Gör'} <FiArrowRight size={14} />
            </Link>
          )}
        </div>
        <div className="row g-3 row-cols-2 row-cols-md-3 row-cols-lg-4">
          {products.map(p => (
            <div key={p.id} className="col">
              <ProductCard product={p} onAddToCart={handleAddToCart} />
            </div>
          ))}
        </div>
      </div>
    </section>
  );

  const orgSchema = buildOrganizationSchema(settings);
  const siteSchema = buildWebSiteSchema(settings);

  return (
    <div>
      <SeoHead
        title={settings?.seo_meta_title_home || null}
        description={settings?.seo_default_meta_description}
        path="/"
        type="website"
        jsonLd={[orgSchema, siteSchema].filter(Boolean)}
      />
      {/* Hero Banner */}
      <div className="container mt-3">
        <HeroBanner />
      </div>

      {/* Empty Store */}
      {!loading && !hasContent && (
        <section className="container my-5">
          <div className="text-center py-5">
            <FiShoppingBag size={64} className="text-muted mb-4" style={{ opacity: 0.3 }} />
            <h2 className="fw-bold mb-3">Mağazamız Hazırlanıyor</h2>
            <p className="text-muted mb-4" style={{ maxWidth: 500, margin: '0 auto' }}>
              Çok yakında harika ürünlerle sizlerle buluşacağız. Takipte kalın!
            </p>
          </div>
        </section>
      )}

      {/* Categories — Compact chips */}
      {categories.length > 0 && (
        <section className="store-section">
          <div className="container">
            <div className="d-flex flex-wrap gap-2 justify-content-center">
              <Link to="/kategori/tumu" className="store-cat-chip store-cat-chip-all">
                <FiShoppingBag size={15} /> Tüm Ürünler
              </Link>
              {categories.map(cat => (
                <Link key={cat.id} to={`/kategori/${cat.slug}`} className="store-cat-chip">
                  {cat.name}
                </Link>
              ))}
            </div>
          </div>
        </section>
      )}

      {/* Sale Products */}
      {saleProducts.length > 0 && (
        <ProductSection
          title="İndirimli Ürünler"
          icon={<FiZap className="text-danger me-2" />}
          products={saleProducts}
          linkTo="/kategori/tumu"
          linkLabel="Tüm İndirimler"
        />
      )}

      {/* Featured Products */}
      {featured.length > 0 && (
        <ProductSection
          title="Öne Çıkan Ürünler"
          icon={<FiStar className="text-warning me-2" />}
          products={featured}
          linkTo="/kategori/tumu"
        />
      )}

      {/* New Products */}
      {newProducts.length > 0 && (
        <ProductSection
          title="Yeni Ürünler"
          icon={<FiTrendingUp className="text-success me-2" />}
          products={newProducts}
          linkTo="/kategori/tumu"
        />
      )}
    </div>
  );
}
