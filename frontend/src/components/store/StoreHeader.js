import React, { useState, useEffect, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import {
  FiSearch,
  FiUser,
  FiShoppingCart,
  FiMenu,
  FiX,
  FiPhone,
  FiMail,
  FiLogOut,
  FiHeart,
  FiPackage,
  FiMapPin,
  FiChevronDown,
  FiBell,
} from 'react-icons/fi';
import { getDefaultPhone, telHref } from '../../utils/phones';

export default function StoreHeader({ cart, settings }) {
  const [categories, setCategories] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [searchFocused, setSearchFocused] = useState(false);
  const [searchLoading, setSearchLoading] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [mobileSearchOpen, setMobileSearchOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [cartBounce, setCartBounce] = useState(false);
  const prevItemCount = useRef(cart.itemCount);
  const userMenuRef = useRef(null);
  const searchRef = useRef(null);
  const mobileSearchRef = useRef(null);
  const searchTimerRef = useRef(null);
  const navigate = useNavigate();

  // Animate cart badge when count changes
  useEffect(() => {
    if (cart.itemCount !== prevItemCount.current && cart.itemCount > 0) {
      setCartBounce(true);
      setTimeout(() => setCartBounce(false), 400);
    }
    prevItemCount.current = cart.itemCount;
  }, [cart.itemCount]);

  useEffect(() => {
    axios
      .get('/api/store/categories/tree')
      .then((r) => setCategories(r.data || []))
      .catch(() => {});
  }, []);

  // Close menus on outside click
  useEffect(() => {
    const handler = (e) => {
      if (userMenuRef.current && !userMenuRef.current.contains(e.target)) setUserMenuOpen(false);
      if (searchRef.current && !searchRef.current.contains(e.target)) setSearchFocused(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  // Mobile menu: lock background scroll + close on Escape (and keep things tidy)
  useEffect(() => {
    if (!mobileMenuOpen) return undefined;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const onKey = (e) => {
      if (e.key === 'Escape') setMobileMenuOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => {
      document.body.style.overflow = prev;
      window.removeEventListener('keydown', onKey);
    };
  }, [mobileMenuOpen]);

  // Debounced live search
  // Turkish character normalization for search matching
  const normalize = (str) =>
    (str || '')
      .toLowerCase()
      .replace(/ş/g, 's')
      .replace(/ç/g, 'c')
      .replace(/ğ/g, 'g')
      .replace(/ü/g, 'u')
      .replace(/ö/g, 'o')
      .replace(/ı/g, 'i')
      .replace(/Ş/g, 's')
      .replace(/Ç/g, 'c')
      .replace(/Ğ/g, 'g')
      .replace(/Ü/g, 'u')
      .replace(/Ö/g, 'o')
      .replace(/İ/g, 'i');

  const handleSearchInput = (value) => {
    setSearchTerm(value);
    if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
    if (value.trim().length < 2) {
      setSearchResults([]);
      return;
    }
    setSearchLoading(true);
    searchTimerRef.current = setTimeout(() => {
      const q = value.trim();
      const qNorm = normalize(q);

      axios
        .get('/api/store/products', { params: { search: q, size: 8 } })
        .then((r) => {
          const products = (r.data?.content || []).map((p) => ({
            type: 'product',
            id: p.id,
            name: p.name,
            slug: p.slug,
            price: p.price,
            salePrice: p.salePrice,
            image: p.primaryImageUrl,
            brand: p.brandName,
            category: p.categoryName,
            stockStatus: p.stockStatus,
          }));

          // Match categories — including subcategories, with Turkish normalization
          const matchedCats = [];
          const seen = new Set();
          categories.forEach((cat) => {
            if (normalize(cat.name).includes(qNorm) && !seen.has(cat.id)) {
              matchedCats.push({
                type: 'category',
                id: cat.id,
                name: cat.name,
                slug: cat.slug,
                parent: null,
              });
              seen.add(cat.id);
            }
            // Check subcategories
            (cat.children || []).forEach((sub) => {
              if (normalize(sub.name).includes(qNorm) && !seen.has(sub.id)) {
                matchedCats.push({
                  type: 'category',
                  id: sub.id,
                  name: sub.name,
                  slug: sub.slug,
                  parent: cat.name,
                });
                seen.add(sub.id);
              }
            });
          });

          // Match brands from product results
          const matchedBrands = [];
          const brandSeen = new Set();
          products.forEach((p) => {
            if (p.brand && normalize(p.brand).includes(qNorm) && !brandSeen.has(p.brand)) {
              matchedBrands.push({ type: 'brand', name: p.brand });
              brandSeen.add(p.brand);
            }
          });

          setSearchResults([
            ...matchedCats.slice(0, 4),
            ...matchedBrands.slice(0, 2),
            ...products.slice(0, 6),
          ]);
        })
        .catch(() => setSearchResults([]))
        .finally(() => setSearchLoading(false));
    }, 300);
  };

  const handleSearch = (e) => {
    e.preventDefault();
    if (searchTerm.trim()) {
      navigate(`/kategori/arama?q=${encodeURIComponent(searchTerm)}`);
      setSearchTerm('');
      setSearchResults([]);
      setSearchFocused(false);
      setMobileSearchOpen(false);
    }
  };

  // Shared close — used by both desktop dropdown and the mobile search sheet.
  const closeSearchOverlays = () => {
    setSearchFocused(false);
    setSearchTerm('');
    setSearchResults([]);
    setMobileSearchOpen(false);
  };

  const fmtTRY = (v) => new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(v || 0);

  const handleLogout = () => {
    localStorage.removeItem('customer_token');
    localStorage.removeItem('customer_refresh_token');
    localStorage.removeItem('customer_data');
    setUserMenuOpen(false);
    navigate('/');
    window.location.reload();
  };

  const announcement = settings.get('header_announcement', '');
  const customerToken = localStorage.getItem('customer_token');
  let customerName = null;
  let customerEmail = null;
  try {
    const d = JSON.parse(localStorage.getItem('customer_data') || '{}');
    customerName = d.firstName;
    customerEmail = d.email;
  } catch {}
  const isLoggedIn = !!customerToken && !!customerName;

  // Sticky-core scroll behavior.
  //
  // The jitter is gone at the architectural level: only the main header + category
  // nav are sticky and their height NEVER changes, so scrolling can't alter the
  // document height and there is no feedback loop to oscillate. Here we only drive
  // two purely-visual, reflow-free flags:
  //   • `scrolled` → adds a drop shadow once the page has moved at all.
  //   • `hidden`   → slides the sticky core up via transform when scrolling down
  //     (past HIDE_AT) and back down when scrolling up. Transform only — no reflow.
  // rAF throttling sets state at most once per frame; a small delta ignores
  // trackpad/kinetic sub-pixel residue.
  const [scrolled, setScrolled] = useState(false);
  const [hidden, setHidden] = useState(false);
  const lastScrollY = useRef(0);
  const ticking = useRef(false);

  useEffect(() => {
    const HIDE_AT = 240; // don't hide the core until scrolled past this
    const MIN_DELTA = 6; // ignore sub-pixel / kinetic residue

    const apply = () => {
      const y = window.scrollY;
      setScrolled(y > 4);

      const delta = y - lastScrollY.current;
      if (Math.abs(delta) >= MIN_DELTA) {
        setHidden(y >= HIDE_AT && delta > 0);
        lastScrollY.current = y;
      }
      ticking.current = false;
    };

    const onScroll = () => {
      if (!ticking.current) {
        ticking.current = true;
        window.requestAnimationFrame(apply);
      }
    };

    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  return (
    <header className="store-header">
      {/* Top band — announcement + top bar. NOT sticky: it scrolls away naturally
          with the page, so its height never affects the sticky core below. */}
      <div className="store-header-topband">
        {announcement && (
          <div className="store-announcement-bar">
            <div className="container">
              <span>{announcement}</span>
            </div>
          </div>
        )}

        {/* Top Bar */}
        <div>
          <div className="store-header-top">
            <div className="container d-flex justify-content-between align-items-center">
              <div className="d-flex gap-3 align-items-center">
                {getDefaultPhone(settings.settings) && (
                  <a href={telHref(getDefaultPhone(settings.settings))} className="store-top-link">
                    <FiPhone size={12} /> {getDefaultPhone(settings.settings)}
                  </a>
                )}
                {settings.get('contact_email') && (
                  <a
                    href={`mailto:${settings.get('contact_email')}`}
                    className="store-top-link d-none d-md-flex"
                  >
                    <FiMail size={12} /> {settings.get('contact_email')}
                  </a>
                )}
              </div>
              <div className="d-flex gap-3 align-items-center">
                {!isLoggedIn && (
                  <>
                    <Link to="/siparis-takip" className="store-top-link">
                      Sipariş Takip
                    </Link>
                    <span className="store-top-divider">|</span>
                    <Link to="/giris" className="store-top-link">
                      Giriş Yap
                    </Link>
                    <span className="store-top-divider">|</span>
                    <Link to="/kayit" className="store-top-link fw-semibold">
                      Üye Ol
                    </Link>
                  </>
                )}
                {isLoggedIn && (
                  <span className="store-top-link" style={{ cursor: 'default', opacity: 0.8 }}>
                    <FiUser size={12} /> Merhaba, <strong>{customerName}</strong>
                  </span>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
      {/* /store-header-topband */}

      {/* Sticky core — main header + category nav. This is the ONLY sticky part and
          its height is constant, so scrolling never changes the document height and
          the header can't oscillate. Hide/show is transform-only (no reflow). */}
      <div
        className={`store-header-sticky ${scrolled ? 'store-header-sticky--scrolled' : ''} ${
          hidden ? 'store-header-sticky--hidden' : ''
        }`}
      >
        {/* Main Header */}
        <div className="store-header-main">
          <div className="container d-flex align-items-center gap-3">
            <button
              className="store-mobile-menu-btn d-md-none"
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              aria-label="Menü"
            >
              {mobileMenuOpen ? <FiX size={22} /> : <FiMenu size={22} />}
            </button>

            <Link to="/" className="store-logo-link">
              {settings.get('site_logo_url') && settings.get('site_logo_url').length > 2 ? (
                <img
                  src={settings.get('site_logo_url')}
                  alt={settings.get('site_name', 'Mağaza')}
                  className="store-logo"
                />
              ) : (
                <span className="store-logo-text">{settings.get('site_name', 'Mağaza')}</span>
              )}
            </Link>

            <form
              onSubmit={handleSearch}
              role="search"
              className="store-search-form d-none d-md-flex"
              ref={searchRef}
            >
              <div className="store-search-wrapper">
                <FiSearch className="store-search-icon" />
                <input
                  type="text"
                  className="store-search-input"
                  placeholder="Ürün, kategori veya marka ara..."
                  value={searchTerm}
                  onChange={(e) => handleSearchInput(e.target.value)}
                  onFocus={() => setSearchFocused(true)}
                  aria-label="Ürün arama"
                  autoComplete="off"
                />
                {/* Live Search Dropdown */}
                {searchFocused && searchTerm.length >= 2 && (
                  <div
                    className="store-search-dropdown"
                    style={{
                      position: 'absolute',
                      top: '100%',
                      left: 0,
                      right: 0,
                      marginTop: 4,
                      background: '#fff',
                      borderRadius: 12,
                      boxShadow: '0 12px 40px rgba(0,0,0,0.15)',
                      zIndex: 200,
                      overflow: 'hidden',
                      animation: 'fadeInDown 0.15s ease',
                    }}
                  >
                    {searchLoading ? (
                      <div className="p-3 text-center">
                        <span className="spinner-border spinner-border-sm text-primary" />
                      </div>
                    ) : searchResults.length === 0 ? (
                      <div className="p-4 text-center">
                        <FiSearch
                          size={24}
                          className="text-muted mb-2 d-block mx-auto"
                          style={{ opacity: 0.3 }}
                        />
                        <div className="text-muted small">"{searchTerm}" ile eşleşen sonuç bulunamadı.</div>
                        <div className="text-muted" style={{ fontSize: 11 }}>
                          Farklı kelimeler deneyin veya Türkçe karakterlerle arayın.
                        </div>
                      </div>
                    ) : (
                      <>
                        {/* Categories */}
                        {searchResults.filter((r) => r.type === 'category').length > 0 && (
                          <>
                            <div className="px-3 pt-2 pb-1">
                              <small
                                className="text-muted fw-semibold text-uppercase"
                                style={{ fontSize: 10, letterSpacing: '0.05em' }}
                              >
                                Kategoriler
                              </small>
                            </div>
                            {searchResults
                              .filter((r) => r.type === 'category')
                              .map((r) => (
                                <Link
                                  key={`cat-${r.id}`}
                                  to={`/kategori/${r.slug}`}
                                  className="d-flex align-items-center gap-2 px-3 py-2 text-dark text-decoration-none store-search-item"
                                  onClick={() => {
                                    setSearchFocused(false);
                                    setSearchTerm('');
                                    setSearchResults([]);
                                  }}
                                >
                                  <div
                                    style={{
                                      width: 28,
                                      height: 28,
                                      borderRadius: 8,
                                      background: '#eff6ff',
                                      display: 'flex',
                                      alignItems: 'center',
                                      justifyContent: 'center',
                                    }}
                                  >
                                    <i className="fas fa-th-large text-primary" style={{ fontSize: 11 }} />
                                  </div>
                                  <div>
                                    <span className="small fw-medium">{r.name}</span>
                                    {r.parent && (
                                      <span className="text-muted" style={{ fontSize: 10 }}>
                                        {' '}
                                        — {r.parent}
                                      </span>
                                    )}
                                  </div>
                                  <i
                                    className="fas fa-chevron-right ms-auto text-muted"
                                    style={{ fontSize: 9 }}
                                  />
                                </Link>
                              ))}
                          </>
                        )}

                        {/* Brands */}
                        {searchResults.filter((r) => r.type === 'brand').length > 0 && (
                          <>
                            <div className="px-3 pt-2 pb-1 border-top">
                              <small
                                className="text-muted fw-semibold text-uppercase"
                                style={{ fontSize: 10, letterSpacing: '0.05em' }}
                              >
                                Markalar
                              </small>
                            </div>
                            {searchResults
                              .filter((r) => r.type === 'brand')
                              .map((r) => (
                                <Link
                                  key={`brand-${r.name}`}
                                  to={`/kategori/tumu?brand=${r.name}`}
                                  className="d-flex align-items-center gap-2 px-3 py-2 text-dark text-decoration-none store-search-item"
                                  onClick={() => {
                                    setSearchFocused(false);
                                    setSearchTerm('');
                                    setSearchResults([]);
                                  }}
                                >
                                  <div
                                    style={{
                                      width: 28,
                                      height: 28,
                                      borderRadius: 8,
                                      background: '#f0fdf4',
                                      display: 'flex',
                                      alignItems: 'center',
                                      justifyContent: 'center',
                                    }}
                                  >
                                    <i className="fas fa-tag text-success" style={{ fontSize: 11 }} />
                                  </div>
                                  <span className="small fw-medium">{r.name}</span>
                                  <i
                                    className="fas fa-chevron-right ms-auto text-muted"
                                    style={{ fontSize: 9 }}
                                  />
                                </Link>
                              ))}
                          </>
                        )}

                        {/* Products */}
                        {searchResults.filter((r) => r.type === 'product').length > 0 && (
                          <>
                            <div className="px-3 pt-2 pb-1 border-top">
                              <small
                                className="text-muted fw-semibold text-uppercase"
                                style={{ fontSize: 10, letterSpacing: '0.05em' }}
                              >
                                Ürünler
                              </small>
                            </div>
                            {searchResults
                              .filter((r) => r.type === 'product')
                              .map((r) => (
                                <Link
                                  key={`prod-${r.id}`}
                                  to={`/urun/${r.slug}`}
                                  className="d-flex align-items-center gap-3 px-3 py-2 text-dark text-decoration-none store-search-item"
                                  onClick={() => {
                                    setSearchFocused(false);
                                    setSearchTerm('');
                                    setSearchResults([]);
                                  }}
                                >
                                  {r.image ? (
                                    <img
                                      src={r.image}
                                      alt={r.name || 'Ürün görseli'}
                                      style={{
                                        width: 40,
                                        height: 40,
                                        objectFit: 'contain',
                                        borderRadius: 8,
                                        background: '#f8f9fa',
                                        border: '1px solid #f1f5f9',
                                      }}
                                    />
                                  ) : (
                                    <div
                                      style={{
                                        width: 40,
                                        height: 40,
                                        borderRadius: 8,
                                        background: '#f1f5f9',
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                      }}
                                    >
                                      <FiSearch size={14} className="text-muted" />
                                    </div>
                                  )}
                                  <div className="flex-grow-1 min-w-0">
                                    <div className="small fw-medium text-truncate">{r.name}</div>
                                    <div className="d-flex align-items-center gap-2">
                                      {r.brand && (
                                        <span className="text-muted" style={{ fontSize: 10 }}>
                                          {r.brand}
                                        </span>
                                      )}
                                      {r.category && (
                                        <span className="text-muted" style={{ fontSize: 10 }}>
                                          · {r.category}
                                        </span>
                                      )}
                                      {r.stockStatus === 'OUT_OF_STOCK' && (
                                        <span className="badge bg-danger" style={{ fontSize: 8 }}>
                                          Tükendi
                                        </span>
                                      )}
                                    </div>
                                  </div>
                                  <div className="text-end flex-shrink-0">
                                    {r.salePrice && r.salePrice > 0 && r.salePrice < r.price ? (
                                      <>
                                        <div className="fw-bold text-danger" style={{ fontSize: 12 }}>
                                          {new Intl.NumberFormat('tr-TR', {
                                            style: 'currency',
                                            currency: 'TRY',
                                          }).format(r.salePrice)}
                                        </div>
                                        <del className="text-muted" style={{ fontSize: 10 }}>
                                          {new Intl.NumberFormat('tr-TR', {
                                            style: 'currency',
                                            currency: 'TRY',
                                          }).format(r.price)}
                                        </del>
                                      </>
                                    ) : (
                                      <div className="fw-bold" style={{ fontSize: 12 }}>
                                        {new Intl.NumberFormat('tr-TR', {
                                          style: 'currency',
                                          currency: 'TRY',
                                        }).format(r.price)}
                                      </div>
                                    )}
                                  </div>
                                </Link>
                              ))}
                          </>
                        )}

                        {/* All results */}
                        <Link
                          to={`/kategori/arama?q=${encodeURIComponent(searchTerm)}`}
                          className="d-flex align-items-center justify-content-center gap-2 py-3 border-top text-primary small fw-semibold text-decoration-none store-search-item"
                          onClick={() => {
                            setSearchFocused(false);
                            setSearchTerm('');
                            setSearchResults([]);
                          }}
                        >
                          <FiSearch size={13} />"{searchTerm}" için tüm sonuçları gör
                        </Link>
                      </>
                    )}
                  </div>
                )}
              </div>
            </form>

            {/* Actions */}
            <div className="store-header-actions">
              {/* Mobile search toggle — opens full-width search sheet */}
              <button
                className="store-mobile-search-toggle d-md-none"
                onClick={() => {
                  setMobileMenuOpen(false);
                  setMobileSearchOpen((v) => !v);
                }}
                aria-label="Ara"
                aria-expanded={mobileSearchOpen}
              >
                {mobileSearchOpen ? <FiX size={22} /> : <FiSearch size={22} />}
              </button>

              {/* User Menu */}
              <div className="position-relative" ref={userMenuRef}>
                <button
                  className="store-action-btn"
                  onClick={() => (isLoggedIn ? setUserMenuOpen(!userMenuOpen) : navigate('/giris'))}
                  aria-label="Hesabım"
                >
                  <FiUser size={20} />
                  <span className="store-action-label d-none d-lg-block">
                    {isLoggedIn ? customerName : 'Giriş'}
                  </span>
                  {isLoggedIn && (
                    <FiChevronDown
                      size={14}
                      className="d-none d-lg-block"
                      style={{
                        marginLeft: 2,
                        transition: 'transform 0.2s',
                        transform: userMenuOpen ? 'rotate(180deg)' : 'none',
                      }}
                    />
                  )}
                </button>

                {/* Dropdown */}
                {userMenuOpen && isLoggedIn && (
                  <div
                    className="store-user-dropdown"
                    style={{
                      position: 'absolute',
                      right: 0,
                      top: 'calc(100% + 8px)',
                      width: 260,
                      background: '#fff',
                      borderRadius: 12,
                      boxShadow: '0 8px 30px rgba(0,0,0,0.12)',
                      zIndex: 100,
                      overflow: 'hidden',
                      animation: 'fadeInDown 0.15s ease',
                    }}
                  >
                    {/* User Info */}
                    <div
                      className="p-3 border-bottom"
                      style={{ background: 'linear-gradient(135deg,#f0f7ff,#e8f0fe)' }}
                    >
                      <div className="d-flex align-items-center gap-2">
                        <div
                          style={{
                            width: 38,
                            height: 38,
                            borderRadius: '50%',
                            background: '#2563eb',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            color: '#fff',
                            fontWeight: 700,
                            fontSize: 15,
                          }}
                        >
                          {customerName?.charAt(0)?.toUpperCase()}
                        </div>
                        <div className="min-w-0">
                          <div className="fw-semibold small text-truncate">{customerName}</div>
                          <div className="text-muted small text-truncate" style={{ fontSize: 11 }}>
                            {customerEmail}
                          </div>
                        </div>
                      </div>
                    </div>
                    {/* Menu Items */}
                    <div className="py-1">
                      <Link
                        to="/siparislerim"
                        className="d-flex align-items-center gap-2 px-3 py-2 text-dark text-decoration-none store-user-menu-item"
                        onClick={() => setUserMenuOpen(false)}
                      >
                        <FiPackage size={16} className="text-muted" />
                        <span className="small">Siparişlerim</span>
                      </Link>
                      <Link
                        to="/favorilerim"
                        className="d-flex align-items-center gap-2 px-3 py-2 text-dark text-decoration-none store-user-menu-item"
                        onClick={() => setUserMenuOpen(false)}
                      >
                        <FiHeart size={16} className="text-muted" />
                        <span className="small">Favorilerim</span>
                      </Link>
                      <Link
                        to="/destek"
                        className="d-flex align-items-center gap-2 px-3 py-2 text-dark text-decoration-none store-user-menu-item"
                        onClick={() => setUserMenuOpen(false)}
                      >
                        <FiPackage size={16} className="text-muted" />
                        <span className="small">Destek Taleplerim</span>
                      </Link>
                      <Link
                        to="/adreslerim"
                        className="d-flex align-items-center gap-2 px-3 py-2 text-dark text-decoration-none store-user-menu-item"
                        onClick={() => setUserMenuOpen(false)}
                      >
                        <FiMapPin size={16} className="text-muted" />
                        <span className="small">Adreslerim</span>
                      </Link>
                      <Link
                        to="/hesabim/bildirimler"
                        className="d-flex align-items-center gap-2 px-3 py-2 text-dark text-decoration-none store-user-menu-item"
                        onClick={() => setUserMenuOpen(false)}
                      >
                        <FiBell size={16} className="text-muted" />
                        <span className="small">Bildirim Tercihleri</span>
                      </Link>
                    </div>
                    {/* Logout */}
                    <div className="border-top p-2">
                      <button
                        className="btn btn-sm btn-outline-danger w-100 d-flex align-items-center justify-content-center gap-2"
                        onClick={handleLogout}
                      >
                        <FiLogOut size={14} />
                        Çıkış Yap
                      </button>
                    </div>
                  </div>
                )}
              </div>

              {/* Cart */}
              <button
                className="store-action-btn"
                onClick={() => cart.setSidebarOpen(true)}
                aria-label={`Sepet (${cart.itemCount} ürün)`}
              >
                <FiShoppingCart size={20} />
                {cart.itemCount > 0 && (
                  <span className={`store-cart-badge ${cartBounce ? 'bounce' : ''}`}>{cart.itemCount}</span>
                )}
                <span className="store-action-label d-none d-lg-block">Sepetim</span>
              </button>
            </div>
          </div>
        </div>

        {/* Mobile Search Sheet — reuses live search state/results */}
        {mobileSearchOpen && (
          <div className="store-mobile-search-sheet d-md-none" ref={mobileSearchRef}>
            <div className="container">
              <form onSubmit={handleSearch} role="search" className="store-search-form">
                <div className="store-search-wrapper">
                  <FiSearch className="store-search-icon" />
                  <input
                    type="search"
                    className="store-search-input"
                    placeholder="Ürün, kategori veya marka ara..."
                    value={searchTerm}
                    onChange={(e) => handleSearchInput(e.target.value)}
                    aria-label="Ürün arama"
                    autoComplete="off"
                    // eslint-disable-next-line jsx-a11y/no-autofocus
                    autoFocus
                  />
                </div>
              </form>
              {searchTerm.trim().length >= 2 && (
                <div className="store-mobile-search-results">
                  {searchLoading ? (
                    <div className="p-3 text-center">
                      <span className="spinner-border spinner-border-sm text-primary" />
                    </div>
                  ) : searchResults.length === 0 ? (
                    <div className="p-3 text-center text-muted small">
                      "{searchTerm}" ile eşleşen sonuç bulunamadı.
                    </div>
                  ) : (
                    <>
                      {searchResults.map((r) => {
                        if (r.type === 'category') {
                          return (
                            <Link
                              key={`m-cat-${r.id}`}
                              to={`/kategori/${r.slug}`}
                              className="store-mobile-search-row"
                              onClick={closeSearchOverlays}
                            >
                              <span className="store-mobile-search-tag tag-cat">Kategori</span>
                              <span className="flex-grow-1 text-truncate">{r.name}</span>
                            </Link>
                          );
                        }
                        if (r.type === 'brand') {
                          return (
                            <Link
                              key={`m-brand-${r.name}`}
                              to={`/kategori/tumu?brand=${r.name}`}
                              className="store-mobile-search-row"
                              onClick={closeSearchOverlays}
                            >
                              <span className="store-mobile-search-tag tag-brand">Marka</span>
                              <span className="flex-grow-1 text-truncate">{r.name}</span>
                            </Link>
                          );
                        }
                        const showSale = r.salePrice && r.salePrice > 0 && r.salePrice < r.price;
                        return (
                          <Link
                            key={`m-prod-${r.id}`}
                            to={`/urun/${r.slug}`}
                            className="store-mobile-search-row"
                            onClick={closeSearchOverlays}
                          >
                            {r.image ? (
                              <img
                                src={r.image}
                                alt={r.name || 'Ürün görseli'}
                                className="store-mobile-search-thumb"
                              />
                            ) : (
                              <span className="store-mobile-search-thumb d-flex align-items-center justify-content-center">
                                <FiSearch size={14} className="text-muted" />
                              </span>
                            )}
                            <span className="flex-grow-1 min-w-0">
                              <span className="d-block text-truncate small fw-medium">{r.name}</span>
                              {r.brand && (
                                <span className="text-muted" style={{ fontSize: 11 }}>
                                  {r.brand}
                                </span>
                              )}
                            </span>
                            <span className="fw-bold small text-nowrap text-primary">
                              {fmtTRY(showSale ? r.salePrice : r.price)}
                            </span>
                          </Link>
                        );
                      })}
                      <Link
                        to={`/kategori/arama?q=${encodeURIComponent(searchTerm)}`}
                        className="store-mobile-search-all"
                        onClick={closeSearchOverlays}
                      >
                        <FiSearch size={13} className="me-1" />"{searchTerm}" için tümünü gör
                      </Link>
                    </>
                  )}
                </div>
              )}
            </div>
          </div>
        )}

        {/* Category Navigation */}
        <nav className="store-mega-menu" aria-label="Kategori menüsü">
          <div className="container">
            <ul className="store-cat-list">
              <li className="store-cat-item store-cat-all">
                <Link to="/kategori/tumu" className="store-cat-link">
                  <FiMenu size={14} /> Tüm Kategoriler
                </Link>
              </li>
              {categories.slice(0, 8).map((cat) => (
                <li key={cat.id} className="store-cat-item">
                  <Link to={`/kategori/${cat.slug}`} className="store-cat-link">
                    {cat.name}
                  </Link>
                  {cat.children && cat.children.length > 0 && (
                    <div className="store-cat-dropdown">
                      {cat.children.map((sub) => (
                        <Link key={sub.id} to={`/kategori/${sub.slug}`} className="store-cat-dropdown-item">
                          {sub.name}
                        </Link>
                      ))}
                    </div>
                  )}
                </li>
              ))}
            </ul>
          </div>
        </nav>
      </div>
      {/* /store-header-sticky */}

      {/* Mobile Menu */}
      {mobileMenuOpen && (
        <>
          <div className="store-mobile-overlay" onClick={() => setMobileMenuOpen(false)} />
          <div className="store-mobile-menu">
            <div className="store-mobile-menu-head">
              <span className="fw-bold text-truncate">{settings.get('site_name', 'Menü')}</span>
              <button
                type="button"
                className="store-mobile-menu-close"
                onClick={() => setMobileMenuOpen(false)}
                aria-label="Menüyü kapat"
              >
                <FiX size={22} />
              </button>
            </div>
            {isLoggedIn && (
              <div className="p-3 border-bottom" style={{ background: '#f0f7ff' }}>
                <div className="d-flex align-items-center gap-2">
                  <div
                    style={{
                      width: 32,
                      height: 32,
                      borderRadius: '50%',
                      background: '#2563eb',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      color: '#fff',
                      fontWeight: 700,
                      fontSize: 13,
                    }}
                  >
                    {customerName?.charAt(0)?.toUpperCase()}
                  </div>
                  <div className="fw-semibold small">{customerName}</div>
                </div>
              </div>
            )}
            <form onSubmit={handleSearch} className="p-3">
              <div className="store-search-wrapper">
                <FiSearch className="store-search-icon" />
                <input
                  type="text"
                  className="store-search-input"
                  placeholder="Ürün ara..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                />
              </div>
            </form>
            <ul className="store-mobile-cat-list">
              {categories.map((cat) => (
                <li key={cat.id}>
                  <Link
                    to={`/kategori/${cat.slug}`}
                    className="store-mobile-cat-link"
                    onClick={() => setMobileMenuOpen(false)}
                  >
                    {cat.name}
                  </Link>
                </li>
              ))}
            </ul>
            {isLoggedIn && (
              <div className="p-3 border-top">
                <button className="btn btn-sm btn-outline-danger w-100" onClick={handleLogout}>
                  <FiLogOut size={14} className="me-1" />
                  Çıkış Yap
                </button>
              </div>
            )}
          </div>
        </>
      )}
    </header>
  );
}
