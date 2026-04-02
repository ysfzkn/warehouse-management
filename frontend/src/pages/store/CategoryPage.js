import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useOutletContext, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import ProductCard from '../../components/store/ProductCard';
import Breadcrumb from '../../components/store/Breadcrumb';
import { useToast } from '../../components/store/Toast';
import { FiFilter, FiGrid, FiList, FiX, FiSearch } from 'react-icons/fi';
import { SkeletonProductGrid } from '../../components/store/Skeleton';

const SORT_OPTIONS = [
  { value: 'createdAt-desc', label: 'En Yeni' },
  { value: 'price-asc', label: 'Fiyat: Düşükten Yükseğe' },
  { value: 'price-desc', label: 'Fiyat: Yüksekten Düşüğe' },
  { value: 'name-asc', label: 'A-Z' },
  { value: 'name-desc', label: 'Z-A' },
  { value: 'viewCount-desc', label: 'En Popüler' },
];

export default function CategoryPage() {
  const { slug } = useParams();
  const { cart } = useOutletContext();
  const [searchParams] = useSearchParams();
  const toast = useToast();
  const [products, setProducts] = useState([]);
  const [category, setCategory] = useState(null);
  const [categories, setCategories] = useState([]);
  const [brands, setBrands] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [categoryId, setCategoryId] = useState(null);
  const [brandId, setBrandId] = useState(null);
  const [sortBy, setSortBy] = useState('createdAt');
  const [sortDir, setSortDir] = useState('desc');
  const [viewMode, setViewMode] = useState('grid');
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [categoryReady, setCategoryReady] = useState(false);
  const prevSlugRef = useRef(null);

  const searchQuery = searchParams.get('q');

  // Load category by slug — wait for this before fetching products
  useEffect(() => {
    setCategoryReady(false);
    if (slug && slug !== 'arama' && slug !== 'tumu') {
      axios.get(`/api/store/categories/${slug}`)
        .then(r => {
          setCategory(r.data);
          setCategoryId(r.data?.id || null);
          setCategoryReady(true);
        })
        .catch(() => {
          setCategory(null);
          setCategoryId(null);
          setCategoryReady(true);
        });
    } else {
      setCategory(null);
      setCategoryId(null);
      setCategoryReady(true);
    }
    // Reset page when slug changes
    if (prevSlugRef.current !== slug) {
      setPage(0);
      setBrandId(null);
      prevSlugRef.current = slug;
    }
  }, [slug]);

  // Load sidebar data (categories + brands)
  useEffect(() => {
    axios.get('/api/store/categories/tree').then(r => setCategories(r.data || [])).catch(() => {});
    axios.get('/api/store/brands').then(r => setBrands((r.data || []).filter(b => b.name))).catch(() => {});
  }, []);

  // Fetch products — only after category is resolved
  const fetchProducts = useCallback(() => {
    if (!categoryReady) return;
    setLoading(true);
    const params = { page, size: 24, sortBy, sortDir };
    if (categoryId) params.categoryId = categoryId;
    if (brandId) params.brandId = brandId;
    if (searchQuery) params.search = searchQuery;
    axios.get('/api/store/products', { params })
      .then(r => {
        setProducts(r.data?.content || []);
        setTotalPages(r.data?.totalPages || 0);
        setTotalElements(r.data?.totalElements || 0);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [page, sortBy, sortDir, categoryId, brandId, searchQuery, categoryReady]);

  useEffect(() => { fetchProducts(); }, [fetchProducts]);

  const handleAddToCart = async (id) => {
    try { await cart.addItem(id); toast.success('Ürün sepete eklendi'); }
    catch (e) { toast.error(e?.response?.data?.message || 'Sepete eklenemedi'); }
  };

  const breadcrumbs = [
    ...(category ? [{ label: category.name }] : []),
    ...(searchQuery ? [{ label: `"${searchQuery}" araması` }] : []),
  ];

  const activeFilterCount = (categoryId ? 1 : 0) + (brandId ? 1 : 0);

  return (
    <div className="container my-3">
      <Breadcrumb items={breadcrumbs.length > 0 ? breadcrumbs : [{ label: 'Tüm Ürünler' }]} />

      <div className="row g-4">
        {/* Sidebar Filters — Desktop */}
        <div className="col-lg-3 d-none d-lg-block">
          <div className="card border-0 shadow-sm sticky-top" style={{ top: 80 }}>
            <div className="card-body p-0">
              {/* Categories */}
              <div className="p-3 border-bottom">
                <h6 className="fw-semibold mb-3"><FiFilter className="me-2" />Kategoriler</h6>
                <div className="d-flex flex-column gap-1" style={{ maxHeight: 250, overflowY: 'auto' }}>
                  <button className={`btn btn-sm text-start ${!categoryId ? 'btn-primary' : 'btn-light'}`}
                    onClick={() => { setCategoryId(null); setCategory(null); setPage(0); }}>
                    Tümü
                  </button>
                  {categories.map(cat => (
                    <div key={cat.id}>
                      <button className={`btn btn-sm text-start w-100 ${categoryId === cat.id ? 'btn-primary' : 'btn-light'}`}
                        onClick={() => { setCategoryId(cat.id); setCategory(cat); setPage(0); }}>
                        {cat.name}
                      </button>
                      {cat.children?.map(sub => (
                        <button key={sub.id} className={`btn btn-sm text-start w-100 ps-4 ${categoryId === sub.id ? 'btn-primary' : 'btn-light'}`}
                          onClick={() => { setCategoryId(sub.id); setCategory(sub); setPage(0); }}>
                          {sub.name}
                        </button>
                      ))}
                    </div>
                  ))}
                </div>
              </div>

              {/* Brands */}
              {brands.length > 0 && (
                <div className="p-3">
                  <h6 className="fw-semibold mb-3">Markalar</h6>
                  <div className="d-flex flex-column gap-1" style={{ maxHeight: 200, overflowY: 'auto' }}>
                    <button className={`btn btn-sm text-start ${!brandId ? 'btn-outline-secondary' : 'btn-light'}`}
                      onClick={() => { setBrandId(null); setPage(0); }}>
                      Tümü
                    </button>
                    {brands.map(b => (
                      <button key={b.id} className={`btn btn-sm text-start ${brandId === b.id ? 'btn-primary' : 'btn-light'}`}
                        onClick={() => { setBrandId(b.id); setPage(0); }}>
                        {b.name}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Product Grid */}
        <div className="col-lg-9">
          {/* Toolbar */}
          <div className="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
            <div className="d-flex align-items-center gap-2">
              <button className="btn btn-outline-secondary btn-sm d-lg-none" onClick={() => setFiltersOpen(true)}>
                <FiFilter className="me-1" />Filtre {activeFilterCount > 0 && <span className="badge bg-primary ms-1">{activeFilterCount}</span>}
              </button>
              <span className="text-muted small">{totalElements} ürün</span>
            </div>
            <div className="d-flex align-items-center gap-2">
              <select className="form-select form-select-sm" style={{ width: 'auto' }}
                value={`${sortBy}-${sortDir}`}
                onChange={e => { const [s, d] = e.target.value.split('-'); setSortBy(s); setSortDir(d); setPage(0); }}>
                {SORT_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
              </select>
              <div className="btn-group btn-group-sm">
                <button className={`btn ${viewMode === 'grid' ? 'btn-primary' : 'btn-outline-secondary'}`} onClick={() => setViewMode('grid')}><FiGrid /></button>
                <button className={`btn ${viewMode === 'list' ? 'btn-primary' : 'btn-outline-secondary'}`} onClick={() => setViewMode('list')}><FiList /></button>
              </div>
            </div>
          </div>

          {/* Active filters chips */}
          {(category || brandId) && (
            <div className="d-flex flex-wrap gap-2 mb-3">
              {category && (
                <span className="badge bg-primary d-flex align-items-center gap-1 px-3 py-2">
                  {category.name}
                  <FiX style={{ cursor: 'pointer' }} onClick={() => { setCategoryId(null); setCategory(null); setPage(0); }} />
                </span>
              )}
              {brandId && (
                <span className="badge bg-info d-flex align-items-center gap-1 px-3 py-2">
                  {brands.find(b => b.id === brandId)?.name || 'Marka'}
                  <FiX style={{ cursor: 'pointer' }} onClick={() => { setBrandId(null); setPage(0); }} />
                </span>
              )}
              <button className="btn btn-sm btn-outline-danger" onClick={() => { setCategoryId(null); setCategory(null); setBrandId(null); setPage(0); }}>
                Filtreleri Temizle
              </button>
            </div>
          )}

          {/* Products */}
          {loading ? <SkeletonProductGrid count={8} />
          : products.length === 0 ? (
            <div className="text-center py-5">
              <FiSearch size={48} className="text-muted mb-3 opacity-25" />
              <h5 className="text-muted">Ürün Bulunamadı</h5>
              <p className="text-muted small">Arama kriterlerinize uygun ürün bulunamadı. Filtreleri değiştirmeyi deneyin.</p>
              {activeFilterCount > 0 && (
                <button className="btn btn-outline-primary btn-sm" onClick={() => { setCategoryId(null); setCategory(null); setBrandId(null); setPage(0); }}>
                  Filtreleri Temizle
                </button>
              )}
            </div>
          ) : (
            <div className={`row g-3 ${viewMode === 'list' ? 'row-cols-1' : 'row-cols-2 row-cols-md-3 row-cols-lg-3'}`}>
              {products.map(p => (
                <div key={p.id} className="col">
                  <ProductCard product={p} onAddToCart={handleAddToCart} />
                </div>
              ))}
            </div>
          )}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="d-flex justify-content-center mt-4 gap-2">
              <button className="btn btn-outline-primary btn-sm" disabled={page === 0} onClick={() => setPage(p => p - 1)}>
                <i className="fas fa-chevron-left me-1" />Önceki
              </button>
              <span className="align-self-center text-muted small">Sayfa {page + 1} / {totalPages}</span>
              <button className="btn btn-outline-primary btn-sm" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>
                Sonraki<i className="fas fa-chevron-right ms-1" />
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Mobile Filter Drawer */}
      {filtersOpen && (
        <>
          <div className="store-cart-overlay d-lg-none" onClick={() => setFiltersOpen(false)} />
          <div className="store-mobile-menu d-lg-none" style={{ width: 300 }}>
            <div className="d-flex justify-content-between align-items-center p-3 border-bottom">
              <h6 className="fw-bold mb-0">Filtreler</h6>
              <button className="btn btn-sm" onClick={() => setFiltersOpen(false)}><FiX size={20} /></button>
            </div>
            <div className="p-3">
              <h6 className="fw-semibold mb-2 small">Kategoriler</h6>
              <div className="d-flex flex-column gap-1 mb-3">
                <button className={`btn btn-sm text-start ${!categoryId ? 'btn-primary' : 'btn-light'}`}
                  onClick={() => { setCategoryId(null); setCategory(null); setPage(0); setFiltersOpen(false); }}>Tümü</button>
                {categories.map(cat => (
                  <button key={cat.id} className={`btn btn-sm text-start ${categoryId === cat.id ? 'btn-primary' : 'btn-light'}`}
                    onClick={() => { setCategoryId(cat.id); setCategory(cat); setPage(0); setFiltersOpen(false); }}>{cat.name}</button>
                ))}
              </div>
              {brands.length > 0 && (
                <>
                  <h6 className="fw-semibold mb-2 small">Markalar</h6>
                  <div className="d-flex flex-column gap-1">
                    <button className={`btn btn-sm text-start ${!brandId ? 'btn-outline-secondary' : 'btn-light'}`}
                      onClick={() => { setBrandId(null); setPage(0); setFiltersOpen(false); }}>Tümü</button>
                    {brands.map(b => (
                      <button key={b.id} className={`btn btn-sm text-start ${brandId === b.id ? 'btn-primary' : 'btn-light'}`}
                        onClick={() => { setBrandId(b.id); setPage(0); setFiltersOpen(false); }}>{b.name}</button>
                    ))}
                  </div>
                </>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
