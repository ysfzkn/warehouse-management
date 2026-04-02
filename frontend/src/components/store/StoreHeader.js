import React, { useState, useEffect, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { FiSearch, FiUser, FiShoppingCart, FiMenu, FiX, FiPhone, FiMail, FiLogOut, FiHeart, FiPackage, FiMapPin, FiChevronDown } from 'react-icons/fi';

export default function StoreHeader({ cart, settings }) {
  const [categories, setCategories] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const userMenuRef = useRef(null);
  const navigate = useNavigate();

  useEffect(() => {
    axios.get('/api/store/categories/tree').then(r => setCategories(r.data || [])).catch(() => {});
  }, []);

  // Close user menu on outside click
  useEffect(() => {
    const handler = (e) => { if (userMenuRef.current && !userMenuRef.current.contains(e.target)) setUserMenuOpen(false); };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const handleSearch = (e) => {
    e.preventDefault();
    if (searchTerm.trim()) {
      navigate(`/store/kategori/arama?q=${encodeURIComponent(searchTerm)}`);
      setSearchTerm('');
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('customer_token');
    localStorage.removeItem('customer_refresh_token');
    localStorage.removeItem('customer_data');
    setUserMenuOpen(false);
    navigate('/store');
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

  return (
    <header className="store-header">
      {/* Announcement Bar */}
      {announcement && (
        <div className="store-announcement-bar">
          <div className="container"><span>{announcement}</span></div>
        </div>
      )}

      {/* Top Bar */}
      <div className="store-header-top">
        <div className="container d-flex justify-content-between align-items-center">
          <div className="d-flex gap-3 align-items-center">
            {settings.get('contact_phone') && (
              <a href={`tel:${settings.get('contact_phone')}`} className="store-top-link">
                <FiPhone size={12} /> {settings.get('contact_phone')}
              </a>
            )}
            {settings.get('contact_email') && (
              <a href={`mailto:${settings.get('contact_email')}`} className="store-top-link d-none d-md-flex">
                <FiMail size={12} /> {settings.get('contact_email')}
              </a>
            )}
          </div>
          <div className="d-flex gap-3 align-items-center">
            {!isLoggedIn && (
              <>
                <Link to="/store/giris" className="store-top-link">Giriş Yap</Link>
                <span className="store-top-divider">|</span>
                <Link to="/store/kayit" className="store-top-link fw-semibold">Üye Ol</Link>
              </>
            )}
            {isLoggedIn && (
              <span className="store-top-link" style={{cursor:'default', opacity:0.8}}>
                <FiUser size={12} /> Merhaba, <strong>{customerName}</strong>
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Main Header */}
      <div className="store-header-main">
        <div className="container d-flex align-items-center gap-3">
          <button className="store-mobile-menu-btn d-md-none" onClick={() => setMobileMenuOpen(!mobileMenuOpen)} aria-label="Menü">
            {mobileMenuOpen ? <FiX size={22} /> : <FiMenu size={22} />}
          </button>

          <Link to="/store" className="store-logo-link">
            {settings.get('site_logo_url') && settings.get('site_logo_url').length > 2 ? (
              <img src={settings.get('site_logo_url')} alt={settings.get('site_name', 'Mağaza')} className="store-logo" />
            ) : (
              <span className="store-logo-text">{settings.get('site_name', 'Mağaza')}</span>
            )}
          </Link>

          <form onSubmit={handleSearch} role="search" className="store-search-form d-none d-md-flex">
            <div className="store-search-wrapper">
              <FiSearch className="store-search-icon" />
              <input type="text" className="store-search-input" placeholder="Ürün, kategori veya marka ara..."
                value={searchTerm} onChange={e => setSearchTerm(e.target.value)} aria-label="Ürün arama" />
            </div>
          </form>

          {/* Actions */}
          <div className="store-header-actions">
            {/* User Menu */}
            <div className="position-relative" ref={userMenuRef}>
              <button className="store-action-btn" onClick={() => isLoggedIn ? setUserMenuOpen(!userMenuOpen) : navigate('/store/giris')} aria-label="Hesabım">
                <FiUser size={20} />
                <span className="store-action-label d-none d-lg-block">
                  {isLoggedIn ? customerName : 'Giriş'}
                </span>
                {isLoggedIn && <FiChevronDown size={14} className="d-none d-lg-block" style={{marginLeft:2, transition:'transform 0.2s', transform: userMenuOpen ? 'rotate(180deg)' : 'none'}} />}
              </button>

              {/* Dropdown */}
              {userMenuOpen && isLoggedIn && (
                <div className="store-user-dropdown" style={{
                  position:'absolute', right:0, top:'calc(100% + 8px)', width:260, background:'#fff',
                  borderRadius:12, boxShadow:'0 8px 30px rgba(0,0,0,0.12)', zIndex:100, overflow:'hidden',
                  animation:'fadeInDown 0.15s ease'
                }}>
                  {/* User Info */}
                  <div className="p-3 border-bottom" style={{background:'linear-gradient(135deg,#f0f7ff,#e8f0fe)'}}>
                    <div className="d-flex align-items-center gap-2">
                      <div style={{width:38,height:38,borderRadius:'50%',background:'#2563eb',display:'flex',alignItems:'center',justifyContent:'center',color:'#fff',fontWeight:700,fontSize:15}}>
                        {customerName?.charAt(0)?.toUpperCase()}
                      </div>
                      <div className="min-w-0">
                        <div className="fw-semibold small text-truncate">{customerName}</div>
                        <div className="text-muted small text-truncate" style={{fontSize:11}}>{customerEmail}</div>
                      </div>
                    </div>
                  </div>
                  {/* Menu Items */}
                  <div className="py-1">
                    <Link to="/store/siparislerim" className="d-flex align-items-center gap-2 px-3 py-2 text-dark text-decoration-none store-user-menu-item" onClick={() => setUserMenuOpen(false)}>
                      <FiPackage size={16} className="text-muted" /><span className="small">Siparişlerim</span>
                    </Link>
                    <Link to="/store/favorilerim" className="d-flex align-items-center gap-2 px-3 py-2 text-dark text-decoration-none store-user-menu-item" onClick={() => setUserMenuOpen(false)}>
                      <FiHeart size={16} className="text-muted" /><span className="small">Favorilerim</span>
                    </Link>
                    <Link to="/store/adreslerim" className="d-flex align-items-center gap-2 px-3 py-2 text-dark text-decoration-none store-user-menu-item" onClick={() => setUserMenuOpen(false)}>
                      <FiMapPin size={16} className="text-muted" /><span className="small">Adreslerim</span>
                    </Link>
                  </div>
                  {/* Logout */}
                  <div className="border-top p-2">
                    <button className="btn btn-sm btn-outline-danger w-100 d-flex align-items-center justify-content-center gap-2" onClick={handleLogout}>
                      <FiLogOut size={14} />Çıkış Yap
                    </button>
                  </div>
                </div>
              )}
            </div>

            {/* Cart */}
            <button className="store-action-btn" onClick={() => cart.setSidebarOpen(true)} aria-label={`Sepet (${cart.itemCount} ürün)`}>
              <FiShoppingCart size={20} />
              {cart.itemCount > 0 && <span className="store-cart-badge">{cart.itemCount}</span>}
              <span className="store-action-label d-none d-lg-block">Sepetim</span>
            </button>
          </div>
        </div>
      </div>

      {/* Category Navigation */}
      <nav className="store-mega-menu" aria-label="Kategori menüsü">
        <div className="container">
          <ul className="store-cat-list">
            <li className="store-cat-item store-cat-all">
              <Link to="/store/kategori/tumu" className="store-cat-link"><FiMenu size={14} /> Tüm Kategoriler</Link>
            </li>
            {categories.slice(0, 8).map(cat => (
              <li key={cat.id} className="store-cat-item">
                <Link to={`/store/kategori/${cat.slug}`} className="store-cat-link">{cat.name}</Link>
                {cat.children && cat.children.length > 0 && (
                  <div className="store-cat-dropdown">
                    {cat.children.map(sub => (
                      <Link key={sub.id} to={`/store/kategori/${sub.slug}`} className="store-cat-dropdown-item">{sub.name}</Link>
                    ))}
                  </div>
                )}
              </li>
            ))}
          </ul>
        </div>
      </nav>

      {/* Mobile Menu */}
      {mobileMenuOpen && (
        <>
          <div className="store-mobile-overlay" onClick={() => setMobileMenuOpen(false)} />
          <div className="store-mobile-menu">
            {isLoggedIn && (
              <div className="p-3 border-bottom" style={{background:'#f0f7ff'}}>
                <div className="d-flex align-items-center gap-2">
                  <div style={{width:32,height:32,borderRadius:'50%',background:'#2563eb',display:'flex',alignItems:'center',justifyContent:'center',color:'#fff',fontWeight:700,fontSize:13}}>
                    {customerName?.charAt(0)?.toUpperCase()}
                  </div>
                  <div className="fw-semibold small">{customerName}</div>
                </div>
              </div>
            )}
            <form onSubmit={handleSearch} className="p-3">
              <div className="store-search-wrapper">
                <FiSearch className="store-search-icon" />
                <input type="text" className="store-search-input" placeholder="Ürün ara..." value={searchTerm} onChange={e => setSearchTerm(e.target.value)} />
              </div>
            </form>
            <ul className="store-mobile-cat-list">
              {categories.map(cat => (
                <li key={cat.id}>
                  <Link to={`/store/kategori/${cat.slug}`} className="store-mobile-cat-link" onClick={() => setMobileMenuOpen(false)}>{cat.name}</Link>
                </li>
              ))}
            </ul>
            {isLoggedIn && (
              <div className="p-3 border-top">
                <button className="btn btn-sm btn-outline-danger w-100" onClick={handleLogout}><FiLogOut size={14} className="me-1" />Çıkış Yap</button>
              </div>
            )}
          </div>
        </>
      )}
    </header>
  );
}
