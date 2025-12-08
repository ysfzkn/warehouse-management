import React, { useState, useEffect, useCallback, useRef } from 'react';
import { createPortal } from 'react-dom';
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
  const [userDropdownCoords, setUserDropdownCoords] = useState({ top: null, right: 16 });
  const [notifPage, setNotifPage] = useState(0);
  const [notifHasMore, setNotifHasMore] = useState(true);
  const [notifLoading, setNotifLoading] = useState(false);
  const [notifRefreshing, setNotifRefreshing] = useState(false);
  const role = (typeof window !== 'undefined' && localStorage.getItem('auth_role')) || 'ADMIN';
  const userBadgeRef = useRef(null);
  const notifLoadingRef = useRef(false);
  const notifRefreshingRef = useRef(false);
  const NOTIFICATION_BATCH_SIZE = 10;

  const loadNotifications = useCallback(async ({ page = 0, reset = false, silent = false } = {}) => {
    if (role !== 'ADMIN') return;
    if (!silent && notifLoadingRef.current && !reset) return;
    if (silent && notifRefreshingRef.current && !reset) return;
    if (silent) {
      notifRefreshingRef.current = true;
      setNotifRefreshing(true);
    } else {
      notifLoadingRef.current = true;
      setNotifLoading(true);
    }
    try {
      const res = await axios.get('/api/notifications', { params: { size: NOTIFICATION_BATCH_SIZE, page } });
      const list = Array.isArray(res.data) ? res.data : [];
      setNotifications(prev => {
        if (reset || page === 0) {
          const next = list;
          setUnreadCount(next.filter(n => !n.read).length);
          return next;
        }
        const existingIds = new Set(prev.map(item => item.id));
        const appended = list.filter(item => !existingIds.has(item.id));
        const next = [...prev, ...appended];
        setUnreadCount(next.filter(n => !n.read).length);
        return next;
      });
      setNotifHasMore(list.length === NOTIFICATION_BATCH_SIZE);
      setNotifPage(page);
    } catch {
      if (reset || page === 0) {
        setNotifications([]);
        setUnreadCount(0);
        setNotifHasMore(false);
        setNotifPage(0);
      }
    } finally {
      if (silent) {
        notifRefreshingRef.current = false;
        setNotifRefreshing(false);
      } else {
        notifLoadingRef.current = false;
        setNotifLoading(false);
      }
    }
  }, [role, NOTIFICATION_BATCH_SIZE]);

  useEffect(() => {
    let ignore = false;
    const hydrate = async () => {
      try {
        if (role === 'ADMIN') {
          await loadNotifications({ page: 0, reset: true });
          if (ignore) return;
        } else if (!ignore) {
          setNotifications([]);
          setUnreadCount(0);
          setNotifHasMore(false);
          setNotifPage(0);
        }
      } catch {}
      try {
        const lowRes = await axios.get('/api/stocks/low-stock/count');
        if (!ignore) {
          const data = lowRes.data;
          const count = typeof data === 'number'
            ? data
            : (Array.isArray(data) ? data.length : 0);
          setLowStockCount(count);
        }
      } catch {}
    };
    hydrate();
    return () => {
      ignore = true;
    };
  }, [role, loadNotifications]);

  useEffect(() => {
    const token = localStorage.getItem('auth_token');
    if (!token) return;
    const es = new EventSource(`/api/stream?token=${encodeURIComponent(token)}`);
    const onMessage = async (ev) => {
      try {
        const data = typeof ev.data === 'string' ? JSON.parse(ev.data) : ev.data;
        const nextUnread = typeof data.unread === 'number' ? data.unread : null;
        const nextLow = typeof data.lowStock === 'number' ? data.lowStock : null;
        if (nextUnread != null) {
          setUnreadCount(prev => {
            if (nextUnread !== prev) {
              loadNotifications({ page: 0, reset: true, silent: true });
            }
            return nextUnread;
          });
        }
        if (nextLow != null) {
          setLowStockCount(nextLow);
        }
      } catch {}
    };
    es.addEventListener('snapshot', onMessage);
    es.addEventListener('update', onMessage);
    es.onerror = () => {
      // Let the browser attempt reconnects automatically
    };
    return () => {
      try { es.close(); } catch {}
    };
  }, [role, loadNotifications]);

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
    transition: 'all 0.3s ease',
    minWidth: '52px',
    minHeight: '48px'
  };

  const formatDateTime = useCallback((value) => {
    if (!value) return '-';
    try {
      return new Date(value).toLocaleString('tr-TR', { timeZone: 'Europe/Istanbul' });
    } catch {
      return value;
    }
  }, []);

  const updateUserDropdownCoords = useCallback(() => {
    if (!userBadgeRef.current) return;
    const rect = userBadgeRef.current.getBoundingClientRect();
    setUserDropdownCoords({
      top: rect.bottom + 12 + window.scrollY,
      right: Math.max(12, window.innerWidth - rect.right - 12)
    });
  }, []);

  useEffect(() => {
    if (!showUserDropdown) return;
    const handlePositionChange = () => updateUserDropdownCoords();
    handlePositionChange();
    window.addEventListener('resize', handlePositionChange);
    window.addEventListener('scroll', handlePositionChange, true);
    return () => {
      window.removeEventListener('resize', handlePositionChange);
      window.removeEventListener('scroll', handlePositionChange, true);
    };
  }, [showUserDropdown, updateUserDropdownCoords]);

  const renderNotificationPortal = () => {
    if (!showNotif) return null;
    return createPortal(
      <>
        <div
          className="notification-backdrop"
          onClick={() => setShowNotif(false)}
        />
        <div className="notification-panel" role="dialog" aria-label="Bildirimler">
          <div className="notification-panel-header">
            <div>
              <div className="fw-bold">Bildirimler</div>
              <small className="text-muted">
                Gösterilen {notifications.length}
                {unreadCount > 0 && ` • Okunmamış ${unreadCount}`}
              </small>
            </div>
            <button
              type="button"
              className="btn btn-sm btn-outline-secondary rounded-circle d-flex align-items-center justify-content-center"
              onClick={() => setShowNotif(false)}
              aria-label="Kapat"
            >
              <i className="fas fa-times"></i>
            </button>
          </div>
          <div className="notification-list">
            {notifications.length === 0 && (
              <div className="p-3 text-muted">Bildirim yok.</div>
            )}
            {notifications.map(n => (
              <div key={n.id} className="dropdown-item-custom" style={{
                alignItems: 'flex-start',
                padding: '0.75rem',
                flexWrap: 'wrap'
              }}>
                <div className="me-2 mt-1 flex-shrink-0" style={{color: n.read ? '#9ca3af' : '#10b981'}}>
                  <i className={`fas ${n.read ? 'fa-circle' : 'fa-dot-circle'}`} style={{fontSize: '0.9rem'}}></i>
                </div>
                <div className="flex-grow-1" style={{minWidth: 0}}>
                  <div className="fw-semibold" style={{fontSize: '0.95rem', lineHeight: '1.3'}}>{n.title}</div>
                  <div className="text-muted" style={{fontSize: '0.85rem', lineHeight: '1.4', wordBreak: 'break-word'}}>{n.message}</div>
                  <div className="text-muted small mt-1 d-flex flex-wrap align-items-center gap-2">
                    <span><i className="far fa-clock me-1"></i>{formatDateTime(n.createdAt)}</span>
                    {n.actor && <span className="badge bg-primary">{n.actor}</span>}
                    {n.warehouseName && (
                      <span className="badge bg-light text-dark"><i className="fas fa-warehouse me-1"></i>{n.warehouseName}</span>
                    )}
                    {n.sourceWarehouseName && (
                      <span className="badge bg-light text-dark"><i className="fas fa-arrow-right me-1"></i>{n.sourceWarehouseName}</span>
                    )}
                    {n.destinationWarehouseName && (
                      <span className="badge bg-light text-dark"><i className="fas fa-arrow-right me-1"></i>{n.destinationWarehouseName}</span>
                    )}
                    {n.productSku && <span className="badge bg-secondary">SKU: {n.productSku}</span>}
                    {typeof n.quantity === 'number' && <span className="badge bg-info text-dark">Adet: {n.quantity}</span>}
                  </div>
                </div>
                <div className="d-flex gap-1 w-100 mt-2" style={{flexWrap: 'wrap'}}>
                  {n.entityType && n.entityId && (
                    <button
                      className="btn btn-sm flex-fill btn-primary"
                      style={{minHeight: '38px'}}
                      onClick={async () => {
                        const title = (n.title || '').toLowerCase();
                        const isTransfer = n.entityType === 'StockTransfer' || title.includes('transfer');
                        const isStockRequest = n.entityType === 'StockRequest';
                        const isTransferApprovalRequest = title.includes('onay') || title.includes('approval') || title.includes('talep');
                        
                        if (isStockRequest) {
                          if (location.pathname === '/stock') {
                            try {
                              window.dispatchEvent(new CustomEvent('open-stock-approval'));
                            } catch {}
                          } else {
                            navigate('/stock?openApproval=true');
                          }
                        } else if (isTransfer && isTransferApprovalRequest) {
                          if (location.pathname === '/stock') {
                            try {
                              window.dispatchEvent(new CustomEvent('open-stock-approval', { detail: { tab: 'transfer' } }));
                            } catch {}
                          } else {
                            navigate('/stock?openApproval=true&tab=transfer');
                          }
                        } else if (isTransfer) {
                          if (location.pathname === '/stock') {
                            try {
                              window.dispatchEvent(new CustomEvent('open-audit', { detail: { entityType: 'StockTransfer', entityId: Number(n.entityId) } }));
                            } catch {}
                          } else {
                            const params = new URLSearchParams();
                            params.set('auditTransferId', n.entityId);
                            navigate(`/stock?${params.toString()}`);
                          }
                        } else {
                          if (location.pathname === '/stock') {
                            try {
                              window.dispatchEvent(new CustomEvent('open-audit', { detail: { entityType: 'Stock', entityId: Number(n.entityId) } }));
                            } catch {}
                          } else {
                            const params = new URLSearchParams();
                            params.set('auditStockId', n.entityId);
                            navigate(`/stock?${params.toString()}`);
                          }
                        }
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
                      className="btn btn-sm flex-fill btn-outline-primary"
                      style={{minHeight: '38px'}}
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
          <div className="notification-footer border-top p-3">
            {notifLoading && (
              <div className="text-center text-muted small">
                Yükleniyor...
              </div>
            )}
            {!notifLoading && notifHasMore && (
              <button
                className="btn btn-outline-secondary w-100"
                onClick={() => loadNotifications({ page: notifPage + 1 })}
              >
                Daha Fazla Yükle
              </button>
            )}
            {!notifLoading && !notifHasMore && notifications.length > 0 && (
              <small className="text-muted d-block text-center">
                Tüm bildirimler yüklendi
              </small>
            )}
          </div>
        </div>
      </>,
      document.body
    );
  };

  const renderUserDropdownPortal = () => {
    if (!showUserDropdown) return null;
    const fallbackTop = typeof window !== 'undefined' ? window.scrollY + 80 : 80;
    const fallbackRight = typeof window !== 'undefined' ? 16 : 16;
    return createPortal(
      <>
        <div
          className="notification-backdrop"
          onClick={() => setShowUserDropdown(false)}
          style={{ backdropFilter: 'blur(2px)', background: 'rgba(15,23,42,0.3)', zIndex: 1100 }}
        />
        <div
          className="user-dropdown-panel"
          style={{
            position: 'fixed',
            top: userDropdownCoords.top ?? fallbackTop,
            right: userDropdownCoords.right ?? fallbackRight,
            left: 'auto'
          }}
        >
          <div className="user-dropdown-header">
            <div>
              <div className="fw-bold text-dark">{localStorage.getItem('auth_user') || 'Admin'}</div>
              <small className="text-muted">
                {role === 'ADMIN' && 'Yönetici'}
                {role === 'STOCK_IN' && 'Stok Giriş Sorumlusu'}
                {role === 'STOCK_OUT' && 'Stok Çıkış Sorumlusu'}
              </small>
            </div>
            <button
              type="button"
              className="btn btn-sm btn-outline-secondary rounded-circle d-flex align-items-center justify-content-center"
              onClick={() => setShowUserDropdown(false)}
            >
              <i className="fas fa-times"></i>
            </button>
          </div>
          <div className="user-dropdown-actions">
            <button className="btn btn-outline-danger w-100" onClick={handleLogout}>
              <i className="fas fa-sign-out-alt me-2"></i>
              Çıkış Yap
            </button>
          </div>
        </div>
      </>,
      document.body
    );
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
          top: -6px;
          right: 0;
          transform: translate(30%, -30%);
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

        .user-dropdown-panel {
          width: min(280px, calc(100vw - 32px));
          background: #fff;
          border-radius: 20px;
          box-shadow: 0 20px 50px rgba(15, 23, 42, 0.25);
          border: 1px solid rgba(15,23,42,0.08);
          z-index: 1300;
          padding-bottom: 1rem;
        }

        .user-dropdown-header {
          padding: 1rem 1.25rem;
          border-bottom: 1px solid #f1f5f9;
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 0.5rem;
        }

        .user-dropdown-actions {
          padding: 1rem 1.25rem 0;
        }

        @media (max-width: 600px) {
          .user-dropdown-panel {
            width: calc(100vw - 24px);
            right: 12px !important;
            left: 12px !important;
          }
        }

        .notification-panel {
          position: fixed;
          top: 72px;
          right: 16px;
          background: #fff;
          border-radius: 16px;
          box-shadow: 0 20px 45px rgba(15, 23, 42, 0.25);
          width: min(360px, calc(100vw - 64px));
          max-height: min(75vh, 520px);
          display: flex;
          flex-direction: column;
          overflow: hidden;
          z-index: 1200;
          animation: slideDown 0.25s ease;
        }

        .notification-panel-header {
          padding: 1rem 1.25rem;
          border-bottom: 1px solid #f1f5f9;
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 0.5rem;
        }

        .notification-list {
          padding: 0.5rem 0.25rem 0.5rem 0.75rem;
          overflow-y: auto;
        }

        .notification-backdrop {
          position: fixed;
          inset: 0;
          background: rgba(15, 23, 42, 0.35);
          backdrop-filter: blur(2px);
          z-index: 1050;
        }

        @media (max-width: 1024px) {
          .notification-panel {
            width: min(420px, calc(100vw - 32px));
            right: 12px;
            left: auto;
          }
        }

        @media (max-width: 768px) {
          .notification-panel {
            width: calc(100vw - 24px);
            right: 12px;
            left: 12px;
            top: 64px;
          }
        }

        @media (max-width: 576px) {
          .notification-panel {
            position: fixed;
            left: 0;
            right: 0;
            bottom: 0;
            top: auto;
            width: 100%;
            max-height: min(85vh, 520px);
            border-radius: 24px 24px 0 0;
            margin: 0;
            padding-bottom: env(safe-area-inset-bottom, 16px);
            animation: slideUp 0.3s ease;
          }

          .notification-panel-header {
            position: sticky;
            top: 0;
            background: #fff;
            border-bottom: 1px solid #f1f5f9;
            z-index: 1;
          }
        }

        @keyframes slideUp {
          from {
            transform: translateY(20px);
            opacity: 0;
          }
          to {
            transform: translateY(0);
            opacity: 1;
          }
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

        @media (max-width: 1199.98px) {
          .navbar .navbar-collapse {
            background: rgba(12, 18, 34, 0.9);
            border-radius: 24px;
            padding: 1rem;
            margin-top: 0.75rem;
            box-shadow: 0 20px 40px rgba(15, 23, 42, 0.35);
          }
          .navbar .nav-link-custom {
            width: 100%;
            margin: 0.25rem 0;
            border-radius: 14px;
            padding: 0.75rem 1rem;
            text-align: left;
          }
          .navbar .dropdown-menu {
            position: static !important;
            transform: none !important;
            width: 100%;
            margin-top: 0.5rem;
            border-radius: 18px;
            box-shadow: inset 0 0 0 1px rgba(255,255,255,0.05);
          }
          .navbar-toggler {
            border-radius: 12px;
            padding: 0.35rem 0.65rem;
          }
        }

        .mobile-user-actions {
          margin-left: auto;
          gap: 0.75rem;
        }

        @media (max-width: 1199.98px) {
          .mobile-user-actions {
            width: 100%;
            justify-content: flex-end;
            margin-top: 1rem;
          }
          .mobile-user-actions .btn {
            padding: 0.35rem 0.45rem;
          }
        }
      `}</style>
      
      <nav className="navbar navbar-expand-xl navbar-dark" style={navbarStyle}>
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
            <span className="d-none d-xl-inline">Depo Yönetim Sistemi</span>
            <span className="d-xl-none">DYS</span>
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
                <button 
                  className="nav-link nav-link-custom dropdown-toggle text-white border-0 bg-transparent w-100 text-start" 
                  type="button"
                  data-bs-toggle="dropdown" 
                  style={{
                    ...navLinkStyle(null),
                    background: ['/products', '/categories', '/admin-settings'].includes(location.pathname) 
                      ? 'rgba(255,255,255,0.2)' 
                      : 'transparent'
                  }}
                >
                  <i className="fas fa-warehouse me-2"></i>
                  Envanter
                </button>
                <ul className="dropdown-menu border-0 shadow-lg" style={{borderRadius: '12px', marginTop: '0.5rem'}}>
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
                  <li><hr className="dropdown-divider" /></li>
                  <li>
                    <Link className="dropdown-item" to="/admin/brands">
                      <i className="fas fa-industry me-2 text-primary"></i>
                      Markalar
                    </Link>
                  </li>
                  <li>
                    <Link className="dropdown-item" to="/admin/colors">
                      <i className="fas fa-palette me-2 text-info"></i>
                      Renkler
                    </Link>
                  </li>
                  <li><hr className="dropdown-divider" /></li>
                  <li>
                    <Link className="dropdown-item" to="/stock-imports">
                      <i className="fas fa-file-excel me-2 text-success"></i>
                      Excel Stok Aktarım Geçmişi
                    </Link>
                  </li>
                </ul>
              </li>
              )}
              
              {role === 'ADMIN' && (
              <li className="nav-item">
                <Link 
                  className="nav-link nav-link-custom text-white" 
                  to="/warehouses" 
                  style={navLinkStyle('/warehouses')}
                >
                  <i className="fas fa-building me-2"></i>
                  Depolar
                </Link>
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
                <button 
                  className="nav-link nav-link-custom dropdown-toggle text-white border-0 bg-transparent w-100 text-start" 
                  type="button"
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
                </button>
                <ul className="dropdown-menu border-0 shadow-lg" style={{borderRadius: '12px', marginTop: '0.5rem'}}>
                  <li>
                    <Link className="dropdown-item" to="/desi">
                      <i className="fas fa-calculator me-2 text-info"></i>
                      Desi Hesaplama
                    </Link>
                  </li>
                  <li><hr className="dropdown-divider" /></li>
                  <li>
                    <Link className="dropdown-item" to="/admin/notifications">
                      <i className="fas fa-bell me-2 text-warning"></i>
                      Bildirimler
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

            <div className="d-flex align-items-center position-relative mobile-user-actions ms-lg-3">
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
                </div>
              )}
              <div 
              className="text-white d-flex align-items-center"
                style={userBadgeStyle}
                ref={userBadgeRef}
              onClick={() => setShowUserDropdown(!showUserDropdown)}
              aria-haspopup="true"
              aria-expanded={showUserDropdown}
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
              
            </div>
          </div>
        </div>
      </nav>

      {renderNotificationPortal()}
      {renderUserDropdownPortal()}

    </>
  );
};

export default Navbar;
