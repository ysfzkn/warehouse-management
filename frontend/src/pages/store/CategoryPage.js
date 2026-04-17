import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useOutletContext, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import ProductCard from '../../components/store/ProductCard';
import Breadcrumb from '../../components/store/Breadcrumb';
import SeoHead from '../../components/store/SeoHead';
import { useSiteSettings } from '../../hooks/useSiteSettings';
import { buildCollectionPageSchema, buildBreadcrumbSchema } from '../../utils/seo';
import { useToast } from '../../components/store/Toast';
import { FiFilter, FiGrid, FiList, FiX, FiSearch, FiChevronDown, FiChevronUp, FiSliders, FiCheck } from 'react-icons/fi';
import { SkeletonProductGrid } from '../../components/store/Skeleton';

const SORT_OPTIONS = [
  { value: 'createdAt-desc', label: 'En Yeni' },
  { value: 'price-asc', label: 'Fiyat: Düşükten Yükseğe' },
  { value: 'price-desc', label: 'Fiyat: Yüksekten Düşüğe' },
  { value: 'name-asc', label: 'A-Z' },
  { value: 'name-desc', label: 'Z-A' },
  { value: 'viewCount-desc', label: 'En Popüler' },
];

/* ─── Collapsible Filter Section ─── */
function FilterSection({ title, defaultOpen = true, children, count }) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="store-filter-section">
      <button className="store-filter-header" onClick={() => setOpen(!open)}>
        <span className="store-filter-title">
          {title}
          {count > 0 && <span className="store-filter-count">{count}</span>}
        </span>
        {open ? <FiChevronUp size={16} /> : <FiChevronDown size={16} />}
      </button>
      {open && <div className="store-filter-body">{children}</div>}
    </div>
  );
}

/* ─── Find category in tree by slug ─── */
function findCategoryBySlug(cats, slug) {
  for (const cat of cats) {
    if (cat.slug === slug) return cat;
    if (cat.children) {
      const found = findCategoryBySlug(cat.children, slug);
      if (found) return found;
    }
  }
  return null;
}

/* ─── Multi-select toggle helper ─── */
function toggleSet(set, id) {
  const next = new Set(set);
  if (next.has(id)) next.delete(id); else next.add(id);
  return next;
}

export default function CategoryPage() {
  const { slug } = useParams();
  const { cart } = useOutletContext();
  const [searchParams] = useSearchParams();
  const toast = useToast();
  const { settings } = useSiteSettings();
  const [products, setProducts] = useState([]);
  const [category, setCategory] = useState(null);
  const [categories, setCategories] = useState([]);
  const [brands, setBrands] = useState([]);
  const [colors, setColors] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [categoryId, setCategoryId] = useState(null);
  // Multi-select: Set of IDs
  const [selectedBrands, setSelectedBrands] = useState(new Set());
  const [selectedColors, setSelectedColors] = useState(new Set());
  const [minPrice, setMinPrice] = useState('');
  const [maxPrice, setMaxPrice] = useState('');
  const [priceApplied, setPriceApplied] = useState({ min: '', max: '' });
  const [sortBy, setSortBy] = useState('createdAt');
  const [sortDir, setSortDir] = useState('desc');
  const [viewMode, setViewMode] = useState('grid');
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [categoryReady, setCategoryReady] = useState(false);
  const prevSlugRef = useRef(null);

  const searchQuery = searchParams.get('q');

  // Load category by slug
  useEffect(() => {
    setCategoryReady(false);
    if (slug && slug !== 'arama' && slug !== 'tumu') {
      axios.get(`/api/store/categories/${slug}`)
        .then(r => { setCategory(r.data); setCategoryId(r.data?.id || null); setCategoryReady(true); })
        .catch(() => {
          // Fallback: try to find category from tree data
          const found = findCategoryBySlug(categories, slug);
          if (found) { setCategory(found); setCategoryId(found.id); }
          else { setCategory(null); setCategoryId(null); }
          setCategoryReady(true);
        });
    } else {
      setCategory(null); setCategoryId(null); setCategoryReady(true);
    }
    if (prevSlugRef.current !== slug) {
      setPage(0); setSelectedBrands(new Set()); setSelectedColors(new Set());
      setMinPrice(''); setMaxPrice(''); setPriceApplied({ min: '', max: '' });
      prevSlugRef.current = slug;
    }
  }, [slug, categories]);

  // Load sidebar data
  useEffect(() => {
    axios.get('/api/store/categories/tree').then(r => setCategories(r.data || [])).catch(() => {});
    axios.get('/api/store/brands').then(r => setBrands((r.data || []).filter(b => b.name))).catch(() => {});
    axios.get('/api/store/colors').then(r => setColors((r.data || []).filter(c => c.name))).catch(() => {});
  }, []);

  // Fetch products
  const fetchProducts = useCallback(() => {
    if (!categoryReady) return;
    setLoading(true);
    const params = { page, size: 24, sortBy, sortDir };
    if (categoryId) params.categoryId = categoryId;
    if (selectedBrands.size > 0) params.brandIds = Array.from(selectedBrands).join(',');
    if (selectedColors.size > 0) params.colorIds = Array.from(selectedColors).join(',');
    if (priceApplied.min) params.minPrice = priceApplied.min;
    if (priceApplied.max) params.maxPrice = priceApplied.max;
    if (searchQuery) params.search = searchQuery;
    axios.get('/api/store/products', { params })
      .then(r => {
        setProducts(r.data?.content || []);
        setTotalPages(r.data?.totalPages || 0);
        setTotalElements(r.data?.totalElements || 0);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [page, sortBy, sortDir, categoryId, selectedBrands, selectedColors, priceApplied, searchQuery, categoryReady]);

  useEffect(() => { fetchProducts(); }, [fetchProducts]);

  const handleAddToCart = async (id) => {
    try { await cart.addItem(id); toast.success('Ürün sepete eklendi'); }
    catch (e) { toast.error(e?.response?.data?.message || 'Sepete eklenemedi'); }
  };

  const clearAllFilters = () => {
    setCategoryId(null); setCategory(null); setSelectedBrands(new Set()); setSelectedColors(new Set());
    setMinPrice(''); setMaxPrice(''); setPriceApplied({ min: '', max: '' }); setPage(0);
  };

  const applyPrice = () => { setPriceApplied({ min: minPrice, max: maxPrice }); setPage(0); };
  const clearPrice = () => { setMinPrice(''); setMaxPrice(''); setPriceApplied({ min: '', max: '' }); setPage(0); };

  const activeFilterCount = selectedBrands.size + selectedColors.size + (priceApplied.min || priceApplied.max ? 1 : 0);

  const breadcrumbs = [
    ...(category?.parentSlug ? [{ label: category.parentName, href: `/kategori/${category.parentSlug}` }] : []),
    ...(category ? [{ label: category.name }] : []),
    ...(searchQuery ? [{ label: `"${searchQuery}" araması` }] : []),
  ];

  /* ─── Shared Filter Content (used in both desktop & mobile) ─── */
  const FilterContent = ({ onClose }) => (
    <>
      {/* Categories */}
      <FilterSection title="Kategoriler" defaultOpen={true}>
        <div className="store-filter-list">
          <label className={`store-filter-item ${!categoryId ? 'active' : ''}`}
            onClick={() => { setCategoryId(null); setCategory(null); setPage(0); }}>
            <span className="store-filter-radio">{!categoryId && <span className="store-filter-radio-dot" />}</span>
            <span>Tüm Ürünler</span>
          </label>
          {categories.map(cat => (
            <React.Fragment key={cat.id}>
              <label className={`store-filter-item ${categoryId === cat.id ? 'active' : ''}`}
                onClick={() => { setCategoryId(cat.id); setCategory(cat); setPage(0); }}>
                <span className="store-filter-radio">{categoryId === cat.id && <span className="store-filter-radio-dot" />}</span>
                <span>{cat.name}</span>
              </label>
              {cat.children?.map(sub => (
                <label key={sub.id} className={`store-filter-item store-filter-sub ${categoryId === sub.id ? 'active' : ''}`}
                  onClick={() => { setCategoryId(sub.id); setCategory(sub); setPage(0); }}>
                  <span className="store-filter-radio small">{categoryId === sub.id && <span className="store-filter-radio-dot" />}</span>
                  <span>{sub.name}</span>
                </label>
              ))}
            </React.Fragment>
          ))}
        </div>
      </FilterSection>

      {/* Brands — Multi-select */}
      {brands.length > 0 && (
        <FilterSection title="Markalar" count={selectedBrands.size}>
          <div className="store-filter-list">
            {brands.map(b => (
              <label key={b.id} className={`store-filter-item ${selectedBrands.has(b.id) ? 'active' : ''}`}
                onClick={() => { setSelectedBrands(prev => toggleSet(prev, b.id)); setPage(0); }}>
                <span className={`store-filter-check ${selectedBrands.has(b.id) ? 'checked' : ''}`}>
                  {selectedBrands.has(b.id) && <FiCheck size={11} />}
                </span>
                <span>{b.name}</span>
              </label>
            ))}
          </div>
          {selectedBrands.size > 0 && (
            <button className="store-filter-clear" onClick={() => { setSelectedBrands(new Set()); setPage(0); }}>
              Seçimi temizle
            </button>
          )}
        </FilterSection>
      )}

      {/* Colors — Multi-select with swatches */}
      {colors.length > 0 && (
        <FilterSection title="Renkler" count={selectedColors.size}>
          <div className="store-color-grid">
            {colors.map(c => (
              <button key={c.id} title={c.name}
                className={`store-color-swatch ${selectedColors.has(c.id) ? 'active' : ''}`}
                style={{ '--swatch-color': c.hexCode || '#ccc' }}
                onClick={() => { setSelectedColors(prev => toggleSet(prev, c.id)); setPage(0); }}
              >
                {selectedColors.has(c.id) && <FiCheck size={13} />}
              </button>
            ))}
          </div>
          {/* Selected color names */}
          {selectedColors.size > 0 && (
            <div className="store-selected-colors">
              {Array.from(selectedColors).map(id => {
                const c = colors.find(x => x.id === id);
                return c ? (
                  <span key={id} className="store-selected-color-tag">
                    <span className="store-chip-dot" style={{ background: c.hexCode || '#ccc' }} />
                    {c.name}
                    <button onClick={() => { setSelectedColors(prev => toggleSet(prev, id)); setPage(0); }}><FiX size={10} /></button>
                  </span>
                ) : null;
              })}
            </div>
          )}
        </FilterSection>
      )}

      {/* Price Range */}
      <FilterSection title="Fiyat Aralığı" count={priceApplied.min || priceApplied.max ? 1 : 0}>
        <div className="store-price-filter">
          <div className="store-price-inputs">
            <div className="store-price-input-wrap">
              <span className="store-price-symbol">₺</span>
              <input type="number" placeholder="Min" value={minPrice}
                onChange={e => setMinPrice(e.target.value)} min="0" />
            </div>
            <span className="store-price-sep">—</span>
            <div className="store-price-input-wrap">
              <span className="store-price-symbol">₺</span>
              <input type="number" placeholder="Max" value={maxPrice}
                onChange={e => setMaxPrice(e.target.value)} min="0" />
            </div>
          </div>
          <button className="store-price-apply" onClick={applyPrice} disabled={!minPrice && !maxPrice}>
            Uygula
          </button>
          {(priceApplied.min || priceApplied.max) && (
            <button className="store-filter-clear mt-1" onClick={clearPrice}>Fiyat filtresini temizle</button>
          )}
        </div>
      </FilterSection>

      {/* Mobile: action buttons */}
      {onClose && (
        <div className="store-filter-actions">
          <button className="store-filter-action-clear" onClick={() => { clearAllFilters(); onClose(); }}>
            Tümünü Temizle
          </button>
          <button className="store-filter-action-apply" onClick={onClose}>
            {totalElements} Ürün Göster
          </button>
        </div>
      )}
    </>
  );

  // SEO: meta tags + structured data
  const seoTitle = category?.metaTitle || category?.name || (searchQuery ? `"${searchQuery}" araması` : 'Tüm Ürünler');
  const seoDescription = category?.metaDescription || (category?.description) ||
    (category ? `${category.name} kategorisindeki tüm ürünler` : 'Tüm ürün kategorilerimizi keşfedin');
  const seoPath = category?.slug ? `/kategori/${category.slug}` : '/kategori/tumu';

  const schemaBreadcrumbs = [
    { name: 'Ana Sayfa', url: '/' },
    ...(category?.parentSlug ? [{ name: category.parentName, url: `/kategori/${category.parentSlug}` }] : []),
    ...(category ? [{ name: category.name, url: `/kategori/${category.slug}` }] : [])
  ];

  const jsonLd = [
    category ? buildCollectionPageSchema(category, settings) : null,
    schemaBreadcrumbs.length > 1 ? buildBreadcrumbSchema(schemaBreadcrumbs, settings) : null
  ].filter(Boolean);

  return (
    <div className="container my-3">
      <SeoHead
        title={seoTitle}
        description={seoDescription}
        path={seoPath}
        image={category?.imageUrl}
        type="website"
        jsonLd={jsonLd}
      />
      <Breadcrumb items={breadcrumbs.length > 0 ? breadcrumbs : [{ label: 'Tüm Ürünler' }]} />

      <div className="row g-4">
        {/* Sidebar Filters — Desktop */}
        <div className="col-lg-3 d-none d-lg-block">
          <div className="store-sidebar-filters">
            <div className="store-sidebar-header">
              <FiSliders size={16} />
              <span>Filtreler</span>
              {activeFilterCount > 0 && (
                <button className="store-sidebar-clear" onClick={clearAllFilters}>
                  Temizle ({activeFilterCount})
                </button>
              )}
            </div>
            <FilterContent />
          </div>
        </div>

        {/* Product Grid */}
        <div className="col-lg-9">
          {/* Toolbar */}
          <div className="store-toolbar">
            <div className="d-flex align-items-center gap-2">
              <button className="store-filter-toggle d-lg-none" onClick={() => setFiltersOpen(true)}>
                <FiFilter size={16} />
                <span>Filtreler</span>
                {activeFilterCount > 0 && <span className="store-filter-badge">{activeFilterCount}</span>}
              </button>
              <span className="store-result-count">{totalElements} ürün</span>
            </div>
            <div className="d-flex align-items-center gap-2">
              <select className="store-sort-select"
                value={`${sortBy}-${sortDir}`}
                onChange={e => { const [s, d] = e.target.value.split('-'); setSortBy(s); setSortDir(d); setPage(0); }}>
                {SORT_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
              </select>
              <div className="store-view-toggle">
                <button className={viewMode === 'grid' ? 'active' : ''} onClick={() => setViewMode('grid')} aria-label="Grid görünüm"><FiGrid size={16} /></button>
                <button className={viewMode === 'list' ? 'active' : ''} onClick={() => setViewMode('list')} aria-label="Liste görünüm"><FiList size={16} /></button>
              </div>
            </div>
          </div>

          {/* Active filter chips */}
          {(activeFilterCount > 0 || (categoryId && !slug)) && (
            <div className="store-active-filters">
              {category && !slug && (
                <span className="store-chip store-chip-category">
                  {category.name}
                  <button onClick={() => { setCategoryId(null); setCategory(null); setPage(0); }}><FiX size={12} /></button>
                </span>
              )}
              {Array.from(selectedBrands).map(id => {
                const b = brands.find(x => x.id === id);
                return b ? (
                  <span key={`b-${id}`} className="store-chip store-chip-brand">
                    {b.name}
                    <button onClick={() => { setSelectedBrands(prev => toggleSet(prev, id)); setPage(0); }}><FiX size={12} /></button>
                  </span>
                ) : null;
              })}
              {Array.from(selectedColors).map(id => {
                const c = colors.find(x => x.id === id);
                return c ? (
                  <span key={`c-${id}`} className="store-chip store-chip-color">
                    <span className="store-chip-dot" style={{ background: c.hexCode || '#ccc' }} />
                    {c.name}
                    <button onClick={() => { setSelectedColors(prev => toggleSet(prev, id)); setPage(0); }}><FiX size={12} /></button>
                  </span>
                ) : null;
              })}
              {(priceApplied.min || priceApplied.max) && (
                <span className="store-chip store-chip-price">
                  {priceApplied.min ? `${priceApplied.min}₺` : '0₺'} — {priceApplied.max ? `${priceApplied.max}₺` : '∞'}
                  <button onClick={clearPrice}><FiX size={12} /></button>
                </span>
              )}
              {activeFilterCount > 1 && (
                <button className="store-chip-clear-all" onClick={clearAllFilters}>Tümünü Temizle</button>
              )}
            </div>
          )}

          {/* Products */}
          {loading ? <SkeletonProductGrid count={8} />
          : products.length === 0 ? (
            <div className="store-empty-state">
              <FiSearch size={48} />
              <h5>Ürün Bulunamadı</h5>
              <p>Arama kriterlerinize uygun ürün bulunamadı. Filtreleri değiştirmeyi deneyin.</p>
              {activeFilterCount > 0 && (
                <button className="btn btn-outline-primary btn-sm" onClick={clearAllFilters}>Filtreleri Temizle</button>
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
            <div className="store-pagination">
              <button disabled={page === 0} onClick={() => { setPage(p => p - 1); window.scrollTo(0, 0); }}>
                ← Önceki
              </button>
              {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => {
                const p = totalPages <= 5 ? i : Math.max(0, Math.min(page - 2, totalPages - 5)) + i;
                return (
                  <button key={p} className={p === page ? 'active' : ''} onClick={() => { setPage(p); window.scrollTo(0, 0); }}>
                    {p + 1}
                  </button>
                );
              })}
              <button disabled={page >= totalPages - 1} onClick={() => { setPage(p => p + 1); window.scrollTo(0, 0); }}>
                Sonraki →
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Mobile Filter Drawer */}
      {filtersOpen && (
        <>
          <div className="store-drawer-overlay" onClick={() => setFiltersOpen(false)} />
          <div className="store-drawer">
            <div className="store-drawer-header">
              <h6><FiSliders size={16} /> Filtreler</h6>
              <button onClick={() => setFiltersOpen(false)}><FiX size={22} /></button>
            </div>
            <div className="store-drawer-body">
              <FilterContent onClose={() => setFiltersOpen(false)} />
            </div>
          </div>
        </>
      )}
    </div>
  );
}
