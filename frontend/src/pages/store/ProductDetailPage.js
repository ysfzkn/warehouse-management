import React, { useState, useEffect } from 'react';
import { useParams, useOutletContext, Link } from 'react-router-dom';
import { useToast } from '../../components/store/Toast';
import axios from 'axios';
import ProductGallery from '../../components/store/ProductGallery';
import Breadcrumb from '../../components/store/Breadcrumb';
import PriceDisplay from '../../components/store/PriceDisplay';
import StockBadge from '../../components/store/StockBadge';
import ProductCard from '../../components/store/ProductCard';
import SeoHead from '../../components/store/SeoHead';
import { SkeletonProductDetail } from '../../components/store/Skeleton';
import { FiHeart, FiShoppingCart, FiTruck, FiShield, FiRefreshCw, FiPackage, FiStar, FiBell, FiMail, FiX } from 'react-icons/fi';
import { useWishlist } from '../../components/store/WishlistContext';
import { useSiteSettings } from '../../hooks/useSiteSettings';
import { buildProductSchema, buildBreadcrumbSchema } from '../../utils/seo';

export default function ProductDetailPage() {
  const { slug } = useParams();
  const { cart } = useOutletContext();
  const toast = useToast();
  const wishlist = useWishlist();
  const { settings } = useSiteSettings();
  const [product, setProduct] = useState(null);
  const [quantity, setQuantity] = useState(1);
  const [related, setRelated] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('description');

  // Back-in-stock subscription
  const [notifyModalOpen, setNotifyModalOpen] = useState(false);
  const [notifyEmail, setNotifyEmail] = useState('');
  const [notifyLoading, setNotifyLoading] = useState(false);
  const [notifySubmitted, setNotifySubmitted] = useState(false);

  const wishlisted = product ? wishlist.has(product.id) : false;

  useEffect(() => {
    setLoading(true); setQuantity(1); setActiveTab('description');
    axios.get(`/api/store/products/${slug}`)
      .then(r => {
        setProduct(r.data);
        if (r.data?.categorySlug) {
          axios.get(`/api/store/products?size=8&categoryId=${r.data.id ? '' : ''}&sortBy=viewCount&sortDir=desc`)
            .then(rel => setRelated((rel.data?.content || []).filter(p => p.slug !== slug).slice(0, 4)))
            .catch(() => {});
        }
      })
      .catch(() => {}).finally(() => setLoading(false));
  }, [slug]);

  const toggleWishlist = async () => {
    const token = localStorage.getItem('customer_token');
    if (!token) { toast.warning('Favorilere eklemek için giriş yapın.'); return; }
    try {
      const nowWished = await wishlist.toggle(product.id);
      if (nowWished) toast.success('Favorilere eklendi!');
      else toast.info('Favorilerden çıkarıldı.');
    } catch {}
  };

  if (loading) return <div className="container my-3"><SkeletonProductDetail /></div>;
  if (!product) return <div className="container my-5 text-center"><h3>Ürün bulunamadı</h3></div>;

  const hasDiscount = !!(product.salePrice && product.salePrice > 0 && product.salePrice < product.price);
  const discountPercent = hasDiscount ? Math.round((1 - product.salePrice / product.price) * 100) : 0;
  const breadcrumbs = [
    ...(product.categorySlug ? [{ label: product.categoryName, href: `/kategori/${product.categorySlug}` }] : []),
    { label: product.name },
  ];

  // SEO: Schema.org JSON-LD + meta tags
  const productSchema = buildProductSchema(product, settings);
  const breadcrumbSchema = buildBreadcrumbSchema([
    { name: 'Ana Sayfa', url: '/' },
    ...(product.categorySlug ? [{ name: product.categoryName, url: `/kategori/${product.categorySlug}` }] : []),
    { name: product.name, url: `/urun/${product.slug}` }
  ], settings);

  const seoTitle = product.metaTitle
    || `${product.name}${product.brandName ? ` | ${product.brandName}` : ''}`;
  const seoDescription = product.metaDescription
    || product.shortDescription
    || (product.description ? product.description.substring(0, 160) : '');
  const ogImage = product.images?.length > 0 ? product.images[0].url : product.primaryImageUrl;

  return (
    <div className="container my-3">
      <SeoHead
        title={seoTitle}
        description={seoDescription}
        path={`/urun/${product.slug}`}
        image={ogImage}
        type="product"
        jsonLd={[productSchema, breadcrumbSchema]}
      >
        {/* Product-specific OG tags (extend SeoHead) */}
        <meta property="product:price:amount" content={String(hasDiscount ? product.salePrice : product.price)} />
        <meta property="product:price:currency" content="TRY" />
        {product.sku && <meta property="product:retailer_item_id" content={product.sku} />}
        {product.brandName && <meta property="product:brand" content={product.brandName} />}
        <meta property="product:availability" content={product.stockStatus === 'OUT_OF_STOCK' ? 'out of stock' : 'in stock'} />
      </SeoHead>
      <Breadcrumb items={breadcrumbs} />

      <div className="row g-4">
        {/* Gallery */}
        <div className="col-lg-6">
          <div className="position-relative">
            <ProductGallery images={product.images} productName={product.name} />
            {/* Discount badge on gallery */}
            {hasDiscount && (
              <div className="position-absolute top-0 start-0 m-3" style={{zIndex:5}}>
                <span className="badge bg-danger px-3 py-2 fs-6">%{discountPercent} İndirim</span>
              </div>
            )}
            {product.isNew && (
              <div className="position-absolute top-0 end-0 m-3" style={{zIndex:5}}>
                <span className="badge bg-success px-3 py-2">Yeni Ürün</span>
              </div>
            )}
          </div>
        </div>

        {/* Product Info */}
        <div className="col-lg-6">
          {/* Brand */}
          {product.brandName && (
            <Link to={`/kategori/tumu?brand=${product.brandName}`} className="text-muted text-decoration-none small text-uppercase fw-semibold" style={{letterSpacing:'0.05em'}}>
              {product.brandName}
            </Link>
          )}

          {/* Title */}
          <h1 className="h3 fw-bold mt-1 mb-2">{product.name}</h1>

          {/* SKU + Category */}
          <div className="d-flex gap-3 text-muted small mb-3">
            {product.sku && <span>SKU: <strong>{product.sku}</strong></span>}
            {product.categoryName && <span>Kategori: <Link to={`/kategori/${product.categorySlug}`} className="text-primary">{product.categoryName}</Link></span>}
          </div>

          {/* Price Section */}
          <div className="p-3 rounded-3 mb-3" style={{
            background: hasDiscount
              ? 'linear-gradient(135deg, #fef2f2 0%, #fff1f2 100%)'
              : 'linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%)',
            border: hasDiscount ? '1px solid #fecaca' : '1px solid #e2e8f0',
          }}>
            <div className="d-flex align-items-center gap-3 flex-wrap">
              <PriceDisplay price={product.price} salePrice={product.salePrice} size="large" />
              {hasDiscount && (
                <span className="badge px-3 py-2" style={{
                  background: 'linear-gradient(135deg, #dc2626 0%, #ef4444 100%)',
                  color: '#fff', fontSize: '0.85rem', fontWeight: 600, borderRadius: 8,
                }}>
                  %{discountPercent} tasarruf
                </span>
              )}
            </div>
            {hasDiscount && (
              <div className="d-flex align-items-center gap-2 mt-2" style={{fontSize:'0.85rem',color:'#059669'}}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>
                <strong>{new Intl.NumberFormat('tr-TR', {style:'currency',currency:'TRY'}).format(product.price - product.salePrice)}</strong> kazanıyorsunuz
              </div>
            )}
          </div>

          {/* Stock */}
          <div className="mb-3"><StockBadge status={product.stockStatus} /></div>

          {/* Short Description */}
          {product.shortDescription && (
            <p className="text-muted mb-3" style={{lineHeight:1.6}}>{product.shortDescription}</p>
          )}

          {/* Quantity + Add to Cart */}
          <div className="d-flex align-items-center gap-3 mb-3">
            <div className="store-qty-selector">
              <button onClick={() => setQuantity(Math.max(1, quantity - 1))}>−</button>
              <input type="number" value={quantity} onChange={e => setQuantity(Math.max(1, parseInt(e.target.value) || 1))} min="1" />
              <button onClick={() => setQuantity(quantity + 1)}>+</button>
            </div>
            {product.stockStatus === 'OUT_OF_STOCK' ? (
              <button
                className="btn btn-warning btn-lg flex-grow-1 d-flex align-items-center justify-content-center gap-2"
                onClick={() => {
                  setNotifySubmitted(false);
                  // Auto-fill email from customer token if logged in
                  const token = localStorage.getItem('customer_token');
                  if (token) {
                    // Token payload'dan email'i çıkartamayız güvenli şekilde, boş bırakıyoruz
                  }
                  setNotifyModalOpen(true);
                }}
              >
                <FiBell size={18} />
                Stoklara Gelince Haber Ver
              </button>
            ) : (
              <button className="btn btn-primary btn-lg flex-grow-1 d-flex align-items-center justify-content-center gap-2"
                onClick={async () => {
                  try { await cart.addItem(product.id, quantity); toast.success(`${product.name} sepete eklendi`); }
                  catch (e) { toast.error(e?.response?.data?.message || 'Sepete eklenemedi'); }
                }}>
                <FiShoppingCart size={18} />
                Sepete Ekle
              </button>
            )}
            <button className={`btn btn-lg ${wishlisted ? 'btn-danger' : 'btn-outline-danger'}`} onClick={toggleWishlist} title="Favorilere ekle">
              <FiHeart size={18} style={wishlisted ? {fill:'currentColor'} : {}} />
            </button>
          </div>

          {/* Trust Badges */}
          <div className="d-flex gap-2 mb-3 flex-wrap">
            {[
              { icon: FiTruck, label: 'Hızlı Kargo', color: '#3b82f6', bg: '#eff6ff' },
              { icon: FiShield, label: 'Güvenli Ödeme', color: '#10b981', bg: '#ecfdf5' },
              { icon: FiRefreshCw, label: '14 Gün İade', color: '#f59e0b', bg: '#fffbeb' },
              { icon: FiPackage, label: 'Orijinal Ürün', color: '#6366f1', bg: '#eef2ff' },
            ].map(({ icon: Icon, label, color, bg }) => (
              <div key={label} className="d-flex align-items-center gap-2 px-3 py-2 rounded-pill" style={{
                background: bg, border: `1px solid ${color}20`, fontSize: '0.78rem', fontWeight: 500, color,
              }}>
                <Icon size={14} />
                <span>{label}</span>
              </div>
            ))}
          </div>

          {/* Quick Specs */}
          {(product.weight || product.lengthCm || product.colorName || product.brandName) && (
            <div className="border-top pt-3">
              <div className="d-flex flex-wrap gap-x-4 gap-y-2" style={{gap:'12px'}}>
                {product.brandName && (
                  <div className="d-flex align-items-center gap-2 small">
                    <span className="text-muted">Marka:</span>
                    <span className="fw-semibold">{product.brandName}</span>
                  </div>
                )}
                {product.colorName && (
                  <div className="d-flex align-items-center gap-2 small">
                    <span className="text-muted">Renk:</span>
                    <span className="fw-semibold">{product.colorName}</span>
                  </div>
                )}
                {product.weight > 0 && (
                  <div className="d-flex align-items-center gap-2 small">
                    <span className="text-muted">Ağırlık:</span>
                    <span className="fw-semibold">{product.weight} kg</span>
                  </div>
                )}
                {product.lengthCm > 0 && (
                  <div className="d-flex align-items-center gap-2 small">
                    <span className="text-muted">Boyut:</span>
                    <span className="fw-semibold">{product.lengthCm}×{product.widthCm}×{product.heightCm} cm</span>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Tabs: Description + Specs + Reviews */}
      <div className="mt-5">
        <div className="d-flex gap-1 border-bottom mb-0" style={{overflowX:'auto'}}>
          {[
            { key: 'description', label: 'Ürün Açıklaması' },
            ...((product.weight || product.vatRate) ? [{ key: 'specs', label: 'Teknik Özellikler' }] : []),
            { key: 'shipping', label: 'Kargo & İade' },
          ].map(tab => (
            <button key={tab.key}
              className="px-4 py-3 border-0 bg-transparent position-relative"
              style={{
                fontWeight: activeTab === tab.key ? 600 : 400,
                color: activeTab === tab.key ? 'var(--store-primary, #2563eb)' : '#64748b',
                whiteSpace: 'nowrap', fontSize: '0.9rem',
                transition: 'color 0.2s ease',
              }}
              onClick={() => setActiveTab(tab.key)}
            >
              {tab.label}
              {activeTab === tab.key && (
                <span style={{
                  position: 'absolute', bottom: -1, left: 16, right: 16, height: 3,
                  background: 'var(--store-primary, #2563eb)', borderRadius: '3px 3px 0 0',
                }} />
              )}
            </button>
          ))}
        </div>
        <div className="p-4" style={{background:'#fafbfc',borderRadius:'0 0 12px 12px',border:'1px solid #e2e8f0',borderTop:'none'}}>
          {activeTab === 'description' && (
            <div>
              {product.description ? (
                <div className="text-muted" style={{lineHeight:1.8, whiteSpace:'pre-wrap'}}>{product.description}</div>
              ) : (
                <p className="text-muted">Bu ürün için henüz detaylı açıklama eklenmemiştir.</p>
              )}
            </div>
          )}
          {activeTab === 'specs' && (
            <table className="table table-sm table-striped mb-0">
              <tbody>
                {product.sku && <tr><td className="text-muted" style={{width:'40%'}}>Stok Kodu</td><td className="fw-medium">{product.sku}</td></tr>}
                {product.brandName && <tr><td className="text-muted">Marka</td><td className="fw-medium">{product.brandName}</td></tr>}
                {product.categoryName && <tr><td className="text-muted">Kategori</td><td className="fw-medium">{product.categoryName}</td></tr>}
                {product.colorName && <tr><td className="text-muted">Renk</td><td className="fw-medium">{product.colorName}</td></tr>}
                {product.weight && <tr><td className="text-muted">Ağırlık</td><td className="fw-medium">{product.weight} kg</td></tr>}
                {product.lengthCm && <tr><td className="text-muted">Boyutlar (E×G×Y)</td><td className="fw-medium">{product.lengthCm} × {product.widthCm} × {product.heightCm} cm</td></tr>}
                {product.vatRate && <tr><td className="text-muted">KDV Oranı</td><td className="fw-medium">%{product.vatRate}</td></tr>}
              </tbody>
            </table>
          )}
          {activeTab === 'shipping' && (
            <div className="text-muted" style={{lineHeight:1.8}}>
              <p><strong>Kargo:</strong> 500₺ ve üzeri siparişlerde ücretsiz kargo. Altında standart kargo ücreti uygulanır.</p>
              <p><strong>Teslimat Süresi:</strong> Siparişiniz 1-3 iş günü içerisinde kargoya verilir.</p>
              <p><strong>İade:</strong> Teslim aldığınız ürünü 14 gün içerisinde iade edebilirsiniz. Ürün kullanılmamış ve orijinal ambalajında olmalıdır.</p>
              <p><strong>Değişim:</strong> Beden veya renk değişimi için müşteri hizmetleri ile iletişime geçebilirsiniz.</p>
            </div>
          )}
        </div>
      </div>

      {/* Related Products */}
      {related.length > 0 && (
        <section className="my-5">
          <h5 className="fw-bold mb-3"><FiStar className="text-warning me-2" />Benzer Ürünler</h5>
          <div className="row g-3">{related.map(p => (
            <div key={p.id} className="col-6 col-md-3">
              <ProductCard product={p} onAddToCart={async (id) => { try { await cart.addItem(id); toast.success('Ürün sepete eklendi'); } catch (e) { toast.error(e?.response?.data?.message || 'Sepete eklenemedi'); }}} />
            </div>
          ))}</div>
        </section>
      )}

      {/* Back-in-stock notification modal */}
      {notifyModalOpen && (
        <div
          className="position-fixed top-0 start-0 w-100 h-100 d-flex align-items-center justify-content-center"
          style={{ background: 'rgba(0,0,0,0.5)', zIndex: 2000 }}
          onClick={() => setNotifyModalOpen(false)}
        >
          <div
            className="bg-white rounded-3 shadow-lg p-4 mx-3"
            style={{ maxWidth: 440, width: '100%' }}
            onClick={e => e.stopPropagation()}
          >
            <div className="d-flex justify-content-between align-items-start mb-3">
              <div className="d-flex align-items-center gap-3">
                <div style={{
                  width: 48, height: 48, borderRadius: 12,
                  background: 'linear-gradient(135deg, #fffbeb, #fde68a)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center'
                }}>
                  <FiBell size={22} color="#d97706" />
                </div>
                <div>
                  <h5 className="mb-0 fw-bold">Stoklara Gelince Haber Ver</h5>
                  <small className="text-muted">{product.name}</small>
                </div>
              </div>
              <button type="button" className="btn-close" onClick={() => setNotifyModalOpen(false)} aria-label="Kapat"></button>
            </div>

            {notifySubmitted ? (
              <div className="text-center py-3">
                <div style={{
                  width: 64, height: 64, borderRadius: '50%',
                  background: '#ecfdf5', display: 'inline-flex',
                  alignItems: 'center', justifyContent: 'center', marginBottom: 12
                }}>
                  <FiBell size={28} color="#059669" />
                </div>
                <h6 className="fw-bold mb-2" style={{color:'#059669'}}>Aboneliğiniz Alındı!</h6>
                <p className="text-muted small mb-3">
                  Bu ürün stoklarımıza geri geldiğinde <strong>{notifyEmail}</strong> adresinize e-posta göndereceğiz.
                </p>
                <button className="btn btn-outline-secondary" onClick={() => setNotifyModalOpen(false)}>
                  Kapat
                </button>
              </div>
            ) : (
              <form
                onSubmit={async (e) => {
                  e.preventDefault();
                  if (!notifyEmail || !/^\S+@\S+\.\S+$/.test(notifyEmail)) {
                    toast.error('Geçerli bir e-posta giriniz.');
                    return;
                  }
                  setNotifyLoading(true);
                  try {
                    const token = localStorage.getItem('customer_token');
                    const headers = token ? { Authorization: `Bearer ${token}` } : {};
                    const res = await axios.post(
                      `/api/store/products/${product.id}/notify-me`,
                      { email: notifyEmail },
                      { headers }
                    );
                    if (res.data?.alreadySubscribed) {
                      toast.info('Bu ürün için zaten bir aboneliğiniz var.');
                    }
                    setNotifySubmitted(true);
                  } catch (err) {
                    toast.error(err.response?.data?.error || 'Abonelik oluşturulamadı.');
                  } finally {
                    setNotifyLoading(false);
                  }
                }}
              >
                <p className="text-muted small mb-3">
                  E-posta adresinizi girin, ürün stoklarımıza geri geldiği anda size bildireceğiz. İstediğiniz zaman abonelikten çıkabilirsiniz.
                </p>
                <div className="mb-3">
                  <label className="form-label small fw-semibold">
                    <FiMail className="me-1" />E-posta Adresi
                  </label>
                  <input
                    type="email"
                    className="form-control"
                    placeholder="ornek@eposta.com"
                    value={notifyEmail}
                    onChange={e => setNotifyEmail(e.target.value)}
                    required
                    autoFocus
                  />
                </div>
                <div className="d-flex gap-2">
                  <button
                    type="button"
                    className="btn btn-outline-secondary flex-shrink-0"
                    onClick={() => setNotifyModalOpen(false)}
                  >
                    İptal
                  </button>
                  <button
                    type="submit"
                    className="btn btn-warning flex-grow-1"
                    disabled={notifyLoading}
                  >
                    {notifyLoading ? (
                      <><span className="spinner-border spinner-border-sm me-2" />Gönderiliyor...</>
                    ) : (
                      <><FiBell className="me-2" />Bildirim Aboneliği</>
                    )}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}

      {/*
        Mobile sticky "Sepete Ekle" — yalnızca <768px ekrana sahip cihazlarda görünür.
        PDP uzun olduğunda kullanıcı scroll edip butonu kaybetmesin diye altta sabit kalır.
        Trust badge'lerden sonra geliyor; CSS @media ile mobile-only.
      */}
      {product.stockStatus !== 'OUT_OF_STOCK' && (
        <div className="store-pdp-sticky-cta d-md-none" role="region" aria-label="Hızlı satın alma">
          <div className="store-pdp-sticky-price">
            <small className="text-muted d-block" style={{ fontSize: 11, lineHeight: 1 }}>Fiyat</small>
            <strong style={{ fontSize: '1.05rem' }}>
              {hasDiscount
                ? new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(product.salePrice)
                : new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(product.price)}
            </strong>
          </div>
          <button
            className="btn btn-primary flex-grow-1 d-flex align-items-center justify-content-center gap-2"
            onClick={async () => {
              try { await cart.addItem(product.id, 1); toast.success(`${product.name} sepete eklendi`); }
              catch (e) { toast.error(e?.response?.data?.message || 'Sepete eklenemedi'); }
            }}
            aria-label="Sepete ekle"
            style={{ minHeight: 48, fontWeight: 600 }}
          >
            <i className="fas fa-shopping-cart" aria-hidden="true" />
            Sepete Ekle
          </button>
        </div>
      )}
    </div>
  );
}
