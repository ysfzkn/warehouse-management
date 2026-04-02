import React, { useState, useEffect } from 'react';
import { useParams, useOutletContext } from 'react-router-dom';
import { useToast } from '../../components/store/Toast';
import axios from 'axios';
import ProductGallery from '../../components/store/ProductGallery';
import ProductSpecs from '../../components/store/ProductSpecs';
import Breadcrumb from '../../components/store/Breadcrumb';
import PriceDisplay from '../../components/store/PriceDisplay';
import StockBadge from '../../components/store/StockBadge';
import ProductCard from '../../components/store/ProductCard';
import { SkeletonProductDetail } from '../../components/store/Skeleton';

export default function ProductDetailPage() {
  const { slug } = useParams();
  const { cart } = useOutletContext();
  const toast = useToast();
  const [product, setProduct] = useState(null);
  const [quantity, setQuantity] = useState(1);
  const [related, setRelated] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    axios.get(`/api/store/products/${slug}`)
      .then(r => {
        setProduct(r.data);
        // Fetch related products from same category
        axios.get(`/api/store/products?size=8&sortBy=viewCount&sortDir=desc`)
          .then(rel => setRelated((rel.data?.content || []).filter(p => p.slug !== slug).slice(0, 4)))
          .catch(() => {});
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [slug]);

  if (loading) return <div className="container my-3"><SkeletonProductDetail /></div>;
  if (!product) return <div className="container my-5 text-center"><h3>Ürün bulunamadı</h3></div>;

  const breadcrumbs = [
    ...(product.categorySlug ? [{ label: product.categoryName, href: `/store/kategori/${product.categorySlug}` }] : []),
    { label: product.name },
  ];

  return (
    <div className="container my-3">
      <Breadcrumb items={breadcrumbs} />
      <div className="row g-4">
        <div className="col-md-6"><ProductGallery images={product.images} /></div>
        <div className="col-md-6 store-product-info">
          <h1 className="h3 fw-bold mb-2">{product.name}</h1>
          {product.brandName && <p className="text-muted mb-3">{product.brandName}</p>}
          <PriceDisplay price={product.price} salePrice={product.salePrice} size="large" showVat />
          <div className="my-3"><StockBadge status={product.stockStatus} /></div>
          {product.shortDescription && <p className="text-muted mb-3">{product.shortDescription}</p>}
          <div className="d-flex align-items-center gap-3 mb-4">
            <div className="store-qty-selector">
              <button onClick={() => setQuantity(Math.max(1, quantity - 1))} aria-label="Azalt">-</button>
              <input type="number" value={quantity} onChange={e => setQuantity(Math.max(1, parseInt(e.target.value) || 1))}
                min="1" max={product.availableQuantity || 99} aria-label="Miktar" />
              <button onClick={() => setQuantity(quantity + 1)} aria-label="Artir">+</button>
            </div>
            <button className="btn btn-primary btn-lg flex-grow-1" onClick={async () => {
                try { await cart.addItem(product.id, quantity); toast.success(`${product.name} sepete eklendi`); }
                catch (e) { toast.error(e?.response?.data?.message || 'Sepete eklenemedi'); }
              }} disabled={product.stockStatus === 'OUT_OF_STOCK'}>
              {product.stockStatus === 'OUT_OF_STOCK' ? 'Tükendi' : 'Sepete Ekle'}
            </button>
          </div>
          <hr />
          <h5 className="fw-bold mb-3">Ürün Özellikleri</h5>
          <ProductSpecs product={product} />
        </div>
      </div>
      {product.description && (
        <div className="mt-4"><h5 className="fw-bold mb-3">Ürün Açıklaması</h5><p className="text-muted">{product.description}</p></div>
      )}
      {related.length > 0 && (
        <section className="my-4">
          <h5 className="fw-bold mb-3">İlgili Ürünler</h5>
          <div className="row g-3">{related.map(p => (
            <div key={p.id} className="col-6 col-md-3">
              <ProductCard product={p} onAddToCart={async (id) => { try { await cart.addItem(id); toast.success('Ürün sepete eklendi'); } catch (e) { toast.error(e?.response?.data?.message || 'Sepete eklenemedi'); }}} />
            </div>
          ))}</div>
        </section>
      )}
    </div>
  );
}
