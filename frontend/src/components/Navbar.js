import React, { useState, useEffect } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import axios from 'axios';

const Navbar = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [lowStockCount, setLowStockCount] = useState(0);
  const [unreadCount, setUnreadCount] = useState(0);
  const [notifications, setNotifications] = useState([]);
  const [showNotif, setShowNotif] = useState(false);
  const [showUserDropdown, setShowUserDropdown] = useState(false);
  const role = (typeof window !== 'undefined' && localStorage.getItem('auth_role')) || 'ADMIN';

  useEffect(() => {
    // Fetch low stock count for notification badge
    const fetchLowStock = async () => {
      try {
        const response = await axios.get('/api/stocks/low-stock');
        setLowStockCount(response.data?.length || 0);
      } catch (error) {
        // Silently fail
      }
    };
    const fetchNotifications = async () => {
      try {
        const listRes = await axios.get('/api/notifications', { params: { size: 500, page: 0 } });
        const list = Array.isArray(listRes.data) ? listRes.data : [];
        setNotifications(list);
        setUnreadCount(list.filter(n => !n.read).length);
      } catch (e) {
        // ignore
      }
    };
    fetchLowStock();
    fetchNotifications();
    // Refresh every 5 minutes
    const interval1 = setInterval(fetchLowStock, 300000);
    const interval2 = setInterval(fetchNotifications, 15000);
    return () => { clearInterval(interval1); clearInterval(interval2); };
  }, []);

  const isActive = (path) => {
    return location.pathname === path;
  };

  const handleLogout = () => {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_user');
    localStorage.removeItem('auth_role');
    window.dispatchEvent(new Event('auth-changed'));
    navigate('/login', { replace: true });
  };

  const navbarStyle = {
    background: 'linear-gradient(135deg, #1e3c72 0%, #2a5298 50%, #1e3c72 100%)',
    boxShadow: '0 4px 20px rgba(0,0,0,0.15)',
    borderBottom: '2px solid rgba(255,255,255,0.1)',
    padding: '0.5rem 0'
  };

  const brandStyle = {
    fontSize: '1.3rem',
    fontWeight: 'bold',
    padding: '0.5rem 1rem',
    borderRadius: '8px',
    transition: 'all 0.3s ease',
    background: 'rgba(255,255,255,0.05)'
  };

  const navLinkStyle = (path) => ({
    padding: '0.6rem 1.2rem',
    margin: '0 0.2rem',
    borderRadius: '8px',
    transition: 'all 0.3s ease',
    background: isActive(path) ? 'rgba(255,255,255,0.2)' : 'transparent',
    color: 'white',
    fontWeight: isActive(path) ? '600' : '400',
    transform: isActive(path) ? 'translateY(-2px)' : 'none',
    boxShadow: isActive(path) ? '0 4px 12px rgba(0,0,0,0.2)' : 'none'
  });

  const userBadgeStyle = {
    background: 'rgba(255,255,255,0.15)',
    backdropFilter: 'blur(10px)',
    padding: '0.5rem 1rem',
    borderRadius: '25px',
    border: '1px solid rgba(255,255,255,0.2)',
    cursor: 'pointer',
    transition: 'all 0.3s ease'
  };

  return (
    <>
      <style>{`
        .nav-link-custom:hover {
          background: rgba(255,255,255,0.15) !important;
          transform: translateY(-2px) !important;
          box-shadow: 0 4px 12px rgba(0,0,0,0.2) !important;
        }
        
        .navbar-brand-custom:hover {
          background: rgba(255,255,255,0.1) !important;
          transform: scale(1.05) !important;
        }
        
        .notification-badge {
          position: absolute;
          top: -4px;
          right: -8px;
          background: #ef4444;
          color: white;
          border-radius: 50%;
          width: 18px;
          height: 18px;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 0.6rem;
          font-weight: bold;
          animation: pulse 2s infinite;
          box-shadow: 0 2px 8px rgba(239, 68, 68, 0.4);
          border: 2px solid #1e3c72;
        }
        
        @keyframes pulse {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.7; }
        }
        
        .user-dropdown {
          position: absolute;
          top: 100%;
          right: 0;
          margin-top: 0.5rem;
          background: white;
          border-radius: 12px;
          box-shadow: 0 8px 24px rgba(0,0,0,0.2);
          min-width: 200px;
          z-index: 1000;
          animation: slideDown 0.3s ease;
        }
        
        @keyframes slideDown {
          from {
            opacity: 0;
            transform: translateY(-10px);
          }
          to {
            opacity: 1;
            transform: translateY(0);
          }
        }
        
        .dropdown-item-custom {
          padding: 0.75rem 1.25rem;
          color: #333;
          text-decoration: none;
          display: flex;
          align-items: center;
          transition: all 0.2s ease;
          cursor: pointer;
        }
        
        .dropdown-item-custom:hover {
          background: #f3f4f6;
          color: #1e3c72;
          padding-left: 1.5rem;
        }
        
        .dropdown-divider-custom {
          height: 1px;
          background: #e5e7eb;
          margin: 0.5rem 0;
        }
      `}</style>
      
      <nav className="navbar navbar-expand-lg navbar-dark" style={navbarStyle}>
        <div className="container-fluid">
          <Link 
            className="navbar-brand navbar-brand-custom d-flex align-items-center text-white" 
            to="/" 
            style={brandStyle}
          >
            <img 
              src="/company-logo.png" 
              onError={(e)=>{e.currentTarget.onerror=null; e.currentTarget.src='/company-logo.png';}} 
              alt="Logo" 
              style={{ height: 32, marginRight: '0.75rem' }} 
            />
            <span className="d-none d-lg-inline">Depo Yönetim Sistemi</span>
            <span className="d-lg-none">DYS</span>
          </Link>

          <button
            className="navbar-toggler border-0"
            type="button"
            data-bs-toggle="collapse"
            data-bs-target="#navbarNav"
            aria-controls="navbarNav"
            aria-expanded="false"
            aria-label="Toggle navigation"
            style={{background: 'rgba(255,255,255,0.1)'}}
          >
            <span className="navbar-toggler-icon"></span>
          </button>

          <div className="collapse navbar-collapse" id="navbarNav">
            <ul className="navbar-nav me-auto mb-2 mb-lg-0">
              {role === 'ADMIN' && (
                <li className="nav-item">
                  <Link 
                    className="nav-link nav-link-custom text-white" 
                    to="/" 
                    style={navLinkStyle('/')}
                  >
                    <i className="fas fa-chart-line me-2"></i>
                    Panel
                  </Link>
                </li>
              )}
              
              {role === 'ADMIN' && (
              <li className="nav-item dropdown">
                <a 
                  className="nav-link nav-link-custom dropdown-toggle text-white" 
                  href="#" 
                  role="button" 
                  data-bs-toggle="dropdown" 
                  style={{
                    ...navLinkStyle(null),
                    background: ['/warehouses', '/products', '/categories'].includes(location.pathname) 
                      ? 'rgba(255,255,255,0.2)' 
                      : 'transparent'
                  }}
                >
                  <i className="fas fa-warehouse me-2"></i>
                  Envanter
                </a>
                <ul className="dropdown-menu border-0 shadow-lg" style={{borderRadius: '12px', marginTop: '0.5rem'}}>
                  <li>
                    <Link className="dropdown-item" to="/warehouses">
                      <i className="fas fa-building me-2 text-primary"></i>
                      Depolar
                    </Link>
                  </li>
                  <li>
                    <Link className="dropdown-item" to="/products">
                      <i className="fas fa-box me-2 text-success"></i>
                      Ürünler
                    </Link>
                  </li>
                  <li>
                    <Link className="dropdown-item" to="/categories">
                      <i className="fas fa-tags me-2 text-warning"></i>
                      Kategoriler
                    </Link>
                  </li>
                </ul>
              </li>
              )}
              
              <li className="nav-item">
                <Link 
                  className="nav-link nav-link-custom text-white position-relative d-inline-flex align-items-center" 
                  to="/stock" 
                  style={navLinkStyle('/stock')}
                >
                  <i className="fas fa-cubes me-2"></i>
                  <span>Stok Yönetimi</span>
                  {lowStockCount > 0 && (
                    <span className="notification-badge">{lowStockCount}</span>
                  )}
                </Link>
              </li>
              
              {role === 'ADMIN' && (
              <li className="nav-item dropdown">
                <a 
                  className="nav-link nav-link-custom dropdown-toggle text-white" 
                  href="#" 
                  role="button" 
                  data-bs-toggle="dropdown" 
                  style={{
                    ...navLinkStyle(null),
                    background: ['/admin-settings', '/desi'].includes(location.pathname) 
                      ? 'rgba(255,255,255,0.2)' 
                      : 'transparent'
                  }}
                >
                  <i className="fas fa-cog me-2"></i>
                  Araçlar
                </a>
                <ul className="dropdown-menu border-0 shadow-lg" style={{borderRadius: '12px', marginTop: '0.5rem'}}>
                  <li>
                    <Link className="dropdown-item" to="/desi">
                      <i className="fas fa-calculator me-2 text-info"></i>
                      Desi Hesaplama
                    </Link>
                  </li>
                  <li><hr className="dropdown-divider" /></li>
                  <li>
                    <Link className="dropdown-item" to="/admin-settings">
                      <i className="fas fa-tools me-2 text-danger"></i>
                      Yönetici Ayarları
                    </Link>
                  </li>
                </ul>
              </li>
              )}
            </ul>

            <div className="d-flex align-items-center position-relative">
              {role === 'ADMIN' && (
                <div className="position-relative me-3">
                  <button
                    className="btn btn-link text-white position-relative"
                    onClick={() => setShowNotif(!showNotif)}
                    style={{textDecoration: 'none'}}
                  >
                    <i className="fas fa-bell fa-lg"></i>
                    {unreadCount > 0 && (
                      <span className="notification-badge" style={{right: -4}}>{unreadCount}</span>
                    )}
                  </button>
                  {showNotif && (
                    <div className="user-dropdown" style={{minWidth: 360}}>
                      <div className="p-3 border-bottom d-flex justify-content-between align-items-center">
                        <div className="fw-bold">Bildirimler</div>
                        <small className="text-muted">Toplam {notifications.length}</small>
                      </div>
                      <div style={{maxHeight: 420, overflowY: 'auto'}}>
                        {notifications.length === 0 && (
                          <div className="p-3 text-muted">Bildirim yok.</div>
                        )}
                        {notifications.map(n => (
                          <div key={n.id} className="dropdown-item-custom" style={{alignItems: 'flex-start'}}>
                            <div className="me-2 mt-1" style={{color: n.read ? '#9ca3af' : '#10b981'}}>
                              <i className={`fas ${n.read ? 'fa-circle' : 'fa-dot-circle'}`}></i>
                            </div>
                            <div>
                              <div className="fw-semibold">{n.title}</div>
                              <div className="text-muted" style={{fontSize: '0.85rem'}}>{n.message}</div>
                            </div>
                            <div className="ms-auto d-flex gap-2">
                              {n.entityType && n.entityId && (
                                <button
                                  className="btn btn-sm btn-primary"
                                  onClick={async () => {
                                    const params = new URLSearchParams();
                                    if (n.entityType === 'Stock') {
                                      params.set('stockId', n.entityId);
                                    } else if (n.entityType === 'StockTransfer') {
                                      params.set('transferId', n.entityId);
                                    }
                                    navigate(`/stock?${params.toString()}`);
                                    setShowNotif(false);
                                    try { await axios.post(`/api/notifications/${n.id}/read`); } catch {}
                                    setNotifications(prev => prev.map(x => x.id === n.id ? {...x, read: true} : x));
                                    setUnreadCount(c => Math.max(0, c - 1));
                                  }}
                                >
                                  Görüntüle
                                </button>
                              )}
                              {!n.read && (
                                <button
                                  className="btn btn-sm btn-outline-primary"
                                  onClick={async () => {
                                    try {
                                      await axios.post(`/api/notifications/${n.id}/read`);
                                      setNotifications(prev => prev.map(x => x.id === n.id ? {...x, read: true} : x));
                                      setUnreadCount(c => Math.max(0, c - 1));
                                    } catch (e) {}
                                  }}
                                >
                                  Okundu
                                </button>
                              )}
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}
              <div 
                className="text-white d-flex align-items-center"
                style={userBadgeStyle}
                onClick={() => setShowUserDropdown(!showUserDropdown)}
              >
                <div 
                  className="rounded-circle d-flex align-items-center justify-content-center me-2" 
                  style={{
                    width: '32px', 
                    height: '32px', 
                    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                    fontWeight: 'bold',
                    fontSize: '0.9rem'
                  }}
                >
                  {(localStorage.getItem('auth_user') || 'A').charAt(0).toUpperCase()}
                </div>
                <span className="me-2 d-none d-md-inline">
                  {localStorage.getItem('auth_user') || 'Admin'}
                </span>
                <i className={`fas fa-chevron-${showUserDropdown ? 'up' : 'down'} small`}></i>
              </div>
              
              {showUserDropdown && (
                <div className="user-dropdown">
                  <div className="p-3 border-bottom">
                    <div className="fw-bold text-dark">{localStorage.getItem('auth_user') || 'Admin'}</div>
                    <small className="text-muted">{role === 'ADMIN' ? 'Yönetici' : 'Standart'}</small>
                  </div>
                  <div className="dropdown-item-custom text-danger" onClick={handleLogout}>
                    <i className="fas fa-sign-out-alt me-2"></i>
                    Çıkış Yap
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </nav>
      
      {/* Backdrop for user dropdown */}
      {showUserDropdown && (
        <div 
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            zIndex: 999
          }}
          onClick={() => setShowUserDropdown(false)}
        />
      )}
    </>
  );
};

export default Navbar;
