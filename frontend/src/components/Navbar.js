import React, { useState, useEffect, useCallback, useRef } from 'react';
import { createPortal } from 'react-dom';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { useWorkspace, domainForPath } from './WorkspaceContext';

const Navbar = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { workspace, setWorkspace, isWms, isEcom, locked } = useWorkspace();
  const [lowStockCount, setLowStockCount] = useState(0);
  const [unreadCount, setUnreadCount] = useState(0); // active workspace unread
  const [otherUnread, setOtherUnread] = useState(0); // the other workspace's unread
  const [notifications, setNotifications] = useState([]);
  const [showNotif, setShowNotif] = useState(false);
  const [showUserDropdown, setShowUserDropdown] = useState(false);
  const [userDropdownCoords, setUserDropdownCoords] = useState({ top: null, right: 16 });
  const [notifPage, setNotifPage] = useState(0);
  const [notifHasMore, setNotifHasMore] = useState(true);
  const [notifLoading, setNotifLoading] = useState(false);
  const [, setNotifRefreshing] = useState(false);
  const role = (typeof window !== 'undefined' && localStorage.getItem('auth_role')) || 'ADMIN';
  const userBadgeRef = useRef(null);
  const notifLoadingRef = useRef(false);
  const notifRefreshingRef = useRef(false);
  const sseRef = useRef(null);
  // Live, closure-free access to the current workspace + last per-domain counts,
  // so the SSE handler and the notification loader don't need to re-subscribe.
  const workspaceRef = useRef(workspace);
  const countsRef = useRef({ WMS: 0, ECOM: 0 });
  // Per-domain unread for the switcher badges (active = unreadCount, other = otherUnread).
  const wmsUnread = isWms ? unreadCount : otherUnread;
  const ecomUnread = isEcom ? unreadCount : otherUnread;

  const isJwtExpired = useCallback((token) => {
    try {
      const [, payload] = token.split('.');
      if (!payload) return false;
      const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
      const decoded = JSON.parse(atob(normalized));
      const exp = decoded?.exp;
      return typeof exp === 'number' && exp * 1000 < Date.now();
    } catch {
      return false;
    }
  }, []);
  const NOTIFICATION_BATCH_SIZE = 10;

  const loadNotifications = useCallback(
    async ({ page = 0, reset = false, silent = false, signal = null } = {}) => {
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
        const res = await axios.get('/api/notifications', {
          params: { size: NOTIFICATION_BATCH_SIZE, page, domain: workspaceRef.current },
          signal: signal,
        });
        const list = Array.isArray(res.data) ? res.data : [];
        setNotifications((prev) => {
          if (reset || page === 0) {
            const next = list;
            setUnreadCount(next.filter((n) => !n.read).length);
            return next;
          }
          const existingIds = new Set(prev.map((item) => item.id));
          const appended = list.filter((item) => !existingIds.has(item.id));
          const next = [...prev, ...appended];
          setUnreadCount(next.filter((n) => !n.read).length);
          return next;
        });
        setNotifHasMore(list.length === NOTIFICATION_BATCH_SIZE);
        setNotifPage(page);
      } catch (error) {
        // Ignore cancellation errors
        if (error.name === 'CanceledError' || error.message === 'canceled') return;

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
    },
    [role, NOTIFICATION_BATCH_SIZE]
  );

  useEffect(() => {
    let ignore = false;
    const abortController = new AbortController();

    const hydrate = async () => {
      try {
        if (role === 'ADMIN') {
          await loadNotifications({
            page: 0,
            reset: true,
            signal: abortController.signal,
          });
          if (ignore) return;
        } else if (!ignore) {
          setNotifications([]);
          setUnreadCount(0);
          setNotifHasMore(false);
          setNotifPage(0);
        }
      } catch (error) {
        if (error.name === 'CanceledError' || error.message === 'canceled') return;
      }
      // Note: lowStockCount is now populated via SSE (Server-Sent Events)
      // Initial value will come from SSE 'snapshot' event shortly after mount
      // This prevents duplicate /api/dashboard/stats requests when Dashboard page also loads
    };

    hydrate();

    return () => {
      ignore = true;
      abortController.abort();
    };
  }, [role, loadNotifications]);

  // When the workspace changes, reload the notification feed for that domain and
  // reflect the cached per-domain unread counts immediately.
  useEffect(() => {
    workspaceRef.current = workspace;
    if (role === 'ADMIN') {
      setUnreadCount(countsRef.current[workspace] || 0);
      setOtherUnread(countsRef.current[workspace === 'WMS' ? 'ECOM' : 'WMS'] || 0);
      loadNotifications({ page: 0, reset: true, silent: true }).catch(() => {});
    }
  }, [workspace, role, loadNotifications]);

  // Auto-switch the workspace to match the page being viewed (e.g. opening an
  // order from a deep link puts you in the E-Ticaret workspace). Shared pages
  // (settings, assistant, catalog) leave the current workspace untouched.
  useEffect(() => {
    if (locked) return;
    const d = domainForPath(location.pathname);
    if (d && d !== workspace) setWorkspace(d);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.pathname]);

  // Explicit workspace switch from the top-bar toggle — jump to that workspace's home.
  const switchWorkspace = useCallback(
    (ws) => {
      if (ws === workspace) return;
      setWorkspace(ws);
      navigate(ws === 'WMS' ? '/' : '/admin/sales-dashboard');
    },
    [workspace, setWorkspace, navigate]
  );

  const handleLogout = useCallback(() => {
    // Tell the server first: clearing localStorage only hides the token from this tab.
    // Any copy taken beforehand stayed valid for the token's full lifetime because
    // there was no way to revoke it. Fire-and-forget — a failed call must never trap
    // the user in a session they asked to leave.
    axios.post('/api/admin/auth/logout').catch(() => {});
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_user');
    localStorage.removeItem('auth_role');
    window.dispatchEvent(new Event('auth-changed'));
    navigate('/login', { replace: true });
  }, [navigate]);

  useEffect(() => {
    const token = localStorage.getItem('auth_token');
    if (!token) return;
    if (isJwtExpired(token)) {
      handleLogout();
      return;
    }

    let es = null;
    let closed = false;
    let cancelled = false;
    const onMessage = async (ev) => {
      try {
        const data = typeof ev.data === 'string' ? JSON.parse(ev.data) : ev.data;
        const nextLow = typeof data.lowStock === 'number' ? data.lowStock : null;
        const wms = typeof data.unreadWms === 'number' ? data.unreadWms : null;
        const ecom = typeof data.unreadEcom === 'number' ? data.unreadEcom : null;
        if (wms != null || ecom != null) {
          // Per-domain counts (new payload): track both, badge shows the active one.
          countsRef.current = {
            WMS: wms != null ? wms : countsRef.current.WMS,
            ECOM: ecom != null ? ecom : countsRef.current.ECOM,
          };
          const ws = workspaceRef.current;
          const active = countsRef.current[ws] || 0;
          const other = countsRef.current[ws === 'WMS' ? 'ECOM' : 'WMS'] || 0;
          setOtherUnread(other);
          setUnreadCount((prev) => {
            if (active !== prev) loadNotifications({ page: 0, reset: true, silent: true }).catch(() => {});
            return active;
          });
        } else if (typeof data.unread === 'number') {
          // Fallback for the old single-count payload.
          setUnreadCount((prev) => {
            if (data.unread !== prev)
              loadNotifications({ page: 0, reset: true, silent: true }).catch(() => {});
            return data.unread;
          });
        }
        if (nextLow != null) {
          setLowStockCount(nextLow);
        }
      } catch {}
    };
    // EventSource cannot send an Authorization header, so the stream used to be opened
    // with the admin JWT in the query string — where it lands in nginx access logs,
    // browser history and Referer headers. Exchange it for a one-minute, single-use
    // ticket over a normal authenticated POST instead.
    (async () => {
      try {
        const { data } = await axios.post('/api/admin/stream/ticket');
        if (cancelled || !data?.ticket) return;
        es = new EventSource(`/api/admin/stream?ticket=${encodeURIComponent(data.ticket)}`);
        sseRef.current = es;
        es.addEventListener('snapshot', onMessage);
        es.addEventListener('update', onMessage);
        es.onerror = () => {
          if (closed) return;
          closed = true;
          try {
            es.close();
          } catch {}
          sseRef.current = null;
          // Do not auto-logout on SSE errors to avoid interrupting active sessions
        };
      } catch {
        // Live counters are a convenience; polling elsewhere still refreshes them.
      }
    })();

    return () => {
      cancelled = true;
      closed = true;
      try {
        es?.close();
      } catch {}
      sseRef.current = null;
    };
  }, [role, loadNotifications, isJwtExpired, handleLogout]);

  const isActive = (path) => {
    return location.pathname === path;
  };

  const navbarStyle = {
    background: 'linear-gradient(135deg, #1e3c72 0%, #2a5298 50%, #1e3c72 100%)',
    boxShadow: '0 4px 20px rgba(0,0,0,0.15)',
    borderBottom: '2px solid rgba(255,255,255,0.1)',
    padding: '0.5rem 0',
  };

  const brandStyle = {
    fontSize: '1.3rem',
    fontWeight: 'bold',
    padding: '0.5rem 1rem',
    borderRadius: '8px',
    transition: 'all 0.3s ease',
    background: 'rgba(255,255,255,0.05)',
  };

  const navLinkStyle = (path) => ({
    padding: '0.5rem 0.9rem',
    margin: '0 0.1rem',
    borderRadius: '10px',
    transition: 'background 0.15s ease, color 0.15s ease',
    background: isActive(path) ? 'rgba(255,255,255,0.18)' : 'transparent',
    color: 'white',
    fontWeight: isActive(path) ? '600' : '500',
    display: 'inline-flex',
    alignItems: 'center',
    whiteSpace: 'nowrap',
    lineHeight: 1.2,
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
    minHeight: '48px',
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
      right: Math.max(12, window.innerWidth - rect.right - 12),
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
        <div className="notification-backdrop" onClick={() => setShowNotif(false)} />
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
            {notifications.length === 0 && <div className="p-3 text-muted">Bildirim yok.</div>}
            {notifications.map((n) => (
              <div
                key={n.id}
                className="dropdown-item-custom"
                style={{
                  alignItems: 'flex-start',
                  padding: '0.75rem',
                  flexWrap: 'wrap',
                }}
              >
                <div className="me-2 mt-1 flex-shrink-0" style={{ color: n.read ? '#9ca3af' : '#10b981' }}>
                  <i
                    className={`fas ${n.read ? 'fa-circle' : 'fa-dot-circle'}`}
                    style={{ fontSize: '0.9rem' }}
                  ></i>
                </div>
                <div className="flex-grow-1" style={{ minWidth: 0 }}>
                  <div className="fw-semibold" style={{ fontSize: '0.95rem', lineHeight: '1.3' }}>
                    {n.title}
                  </div>
                  <div
                    className="text-muted"
                    style={{ fontSize: '0.85rem', lineHeight: '1.4', wordBreak: 'break-word' }}
                  >
                    {n.message}
                  </div>
                  <div className="text-muted small mt-1 d-flex flex-wrap align-items-center gap-2">
                    <span>
                      <i className="far fa-clock me-1"></i>
                      {formatDateTime(n.createdAt)}
                    </span>
                    {n.actor && <span className="badge bg-primary">{n.actor}</span>}
                    {n.warehouseName && (
                      <span className="badge bg-light text-dark">
                        <i className="fas fa-warehouse me-1"></i>
                        {n.warehouseName}
                      </span>
                    )}
                    {n.sourceWarehouseName && (
                      <span className="badge bg-light text-dark">
                        <i className="fas fa-arrow-right me-1"></i>
                        {n.sourceWarehouseName}
                      </span>
                    )}
                    {n.destinationWarehouseName && (
                      <span className="badge bg-light text-dark">
                        <i className="fas fa-arrow-right me-1"></i>
                        {n.destinationWarehouseName}
                      </span>
                    )}
                    {n.productSku && <span className="badge bg-secondary">SKU: {n.productSku}</span>}
                    {typeof n.quantity === 'number' && (
                      <span className="badge bg-info text-dark">Adet: {n.quantity}</span>
                    )}
                  </div>
                </div>
                <div className="d-flex gap-1 w-100 mt-2" style={{ flexWrap: 'wrap' }}>
                  {n.entityType && n.entityId && (
                    <button
                      className="btn btn-sm flex-fill btn-primary"
                      style={{ minHeight: '44px' }}
                      onClick={async () => {
                        const title = (n.title || '').toLowerCase();
                        const isTransfer = n.entityType === 'StockTransfer' || title.includes('transfer');
                        const isStockRequest = n.entityType === 'StockRequest';
                        const isTransferApprovalRequest =
                          title.includes('onay') ||
                          title.includes('approval') ||
                          title.includes('talep') ||
                          title.includes('sil');

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
                              window.dispatchEvent(
                                new CustomEvent('open-stock-approval', { detail: { tab: 'transfer' } })
                              );
                            } catch {}
                          } else {
                            navigate('/stock?openApproval=true&tab=transfer');
                          }
                        } else if (isTransfer) {
                          if (location.pathname === '/stock') {
                            try {
                              window.dispatchEvent(
                                new CustomEvent('open-audit', {
                                  detail: { entityType: 'StockTransfer', entityId: Number(n.entityId) },
                                })
                              );
                            } catch {}
                          } else {
                            const params = new URLSearchParams();
                            params.set('auditTransferId', n.entityId);
                            navigate(`/stock?${params.toString()}`);
                          }
                        } else {
                          if (location.pathname === '/stock') {
                            try {
                              window.dispatchEvent(
                                new CustomEvent('open-audit', {
                                  detail: { entityType: 'Stock', entityId: Number(n.entityId) },
                                })
                              );
                            } catch {}
                          } else {
                            const params = new URLSearchParams();
                            params.set('auditStockId', n.entityId);
                            navigate(`/stock?${params.toString()}`);
                          }
                        }
                        setShowNotif(false);
                        try {
                          await axios.post(`/api/notifications/${n.id}/read`);
                        } catch {}
                        setNotifications((prev) =>
                          prev.map((x) => (x.id === n.id ? { ...x, read: true } : x))
                        );
                        setUnreadCount((c) => Math.max(0, c - 1));
                      }}
                    >
                      Görüntüle
                    </button>
                  )}
                  {!n.read && (
                    <button
                      className="btn btn-sm flex-fill btn-outline-primary"
                      style={{ minHeight: '44px' }}
                      onClick={async () => {
                        try {
                          await axios.post(`/api/notifications/${n.id}/read`);
                          setNotifications((prev) =>
                            prev.map((x) => (x.id === n.id ? { ...x, read: true } : x))
                          );
                          setUnreadCount((c) => Math.max(0, c - 1));
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
            {notifLoading && <div className="text-center text-muted small">Yükleniyor...</div>}
            {!notifLoading && notifHasMore && (
              <button
                className="btn btn-outline-secondary w-100"
                onClick={() => loadNotifications({ page: notifPage + 1 })}
              >
                Daha Fazla Yükle
              </button>
            )}
            {!notifLoading && !notifHasMore && notifications.length > 0 && (
              <small className="text-muted d-block text-center">Tüm bildirimler yüklendi</small>
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
            left: 'auto',
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
        }
        /* Desktop: keep all nav items on one centered baseline, no wrapping. */
        @media (min-width: 1200px) {
          .navbar-nav { align-items: center; }
          .navbar .nav-link-custom {
            white-space: nowrap;
            display: inline-flex;
            align-items: center;
          }
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

        /* Actions artık hamburger dışında (üst satırda her zaman görünür). Tüm
           boyutlarda kompakt, satır-içi bir grup olarak kalır. */
        .mobile-user-actions {
          gap: 0.5rem;
          flex-shrink: 0;
        }

        @media (max-width: 1199.98px) {
          .mobile-user-actions .btn {
            padding: 0.35rem 0.45rem;
          }
          /* Dar ekranda kullanıcı rozetini biraz sıkılaştır (yalnızca avatar). */
          .mobile-user-actions .me-3 {
            margin-right: 0.5rem !important;
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
              src="/api/store/settings/logo"
              onError={(e) => {
                e.currentTarget.onerror = null;
                e.currentTarget.src = '/company-logo.png';
              }}
              alt="Logo"
              style={{ height: 32, marginRight: '0.75rem' }}
            />
            <span className="d-none d-xl-inline">
              {role === 'ADMIN' && isEcom ? 'E-Ticaret Yönetimi' : 'Depo Yönetim Sistemi'}
            </span>
            <span className="d-xl-none">{role === 'ADMIN' && isEcom ? 'ETY' : 'DYS'}</span>
          </Link>

          {/* ── Workspace switcher (Depo ⇄ E-Ticaret) — compact segmented pill ── */}
          {role === 'ADMIN' && !locked && (
            <div
              className="d-inline-flex align-items-center ms-2 ms-xl-3 p-1"
              role="group"
              aria-label="Çalışma alanı"
              style={{ background: 'rgba(255,255,255,0.14)', borderRadius: 999, gap: 2 }}
            >
              {[
                {
                  key: 'WMS',
                  label: 'Depo',
                  icon: 'fa-warehouse',
                  active: isWms,
                  badge: !isWms ? wmsUnread : 0,
                },
                {
                  key: 'ECOM',
                  label: 'E-Ticaret',
                  icon: 'fa-store',
                  active: isEcom,
                  badge: !isEcom ? ecomUnread : 0,
                },
              ].map((w) => (
                <button
                  key={w.key}
                  type="button"
                  onClick={() => switchWorkspace(w.key)}
                  title={w.key === 'WMS' ? 'Depo Yönetimi' : 'E-Ticaret Yönetimi'}
                  className="btn btn-sm border-0 position-relative d-inline-flex align-items-center"
                  style={{
                    borderRadius: 999,
                    padding: '5px 14px',
                    whiteSpace: 'nowrap',
                    fontWeight: 600,
                    fontSize: 13,
                    lineHeight: 1.2,
                    transition: 'background 0.15s, color 0.15s',
                    background: w.active ? '#fff' : 'transparent',
                    color: w.active ? '#1e3a8a' : 'rgba(255,255,255,0.85)',
                    boxShadow: w.active ? '0 1px 4px rgba(0,0,0,0.18)' : 'none',
                  }}
                >
                  <i className={`fas ${w.icon} me-2`} style={{ fontSize: 12 }} />
                  {w.label}
                  {w.badge > 0 && (
                    <span
                      className="badge rounded-pill bg-danger"
                      style={{ fontSize: 9, marginLeft: 6, padding: '2px 5px' }}
                    >
                      {w.badge}
                    </span>
                  )}
                </button>
              ))}
            </div>
          )}

          {/* Bildirim + hesap: her zaman görünür (hamburger dışında). Masaüstünde
              order-xl-last ile en sağa; mobil/tablette toggler'ın hemen solunda. */}
          <div className="d-flex align-items-center position-relative mobile-user-actions order-xl-last ms-auto ms-xl-0">
            {role === 'ADMIN' && (
              <div className="position-relative me-3">
                <button
                  className="btn btn-link text-white position-relative"
                  onClick={() => setShowNotif(!showNotif)}
                  style={{ textDecoration: 'none' }}
                  aria-label={`Bildirimler${unreadCount > 0 ? `, ${unreadCount} okunmamış` : ''}`}
                  aria-haspopup="dialog"
                  aria-expanded={showNotif}
                >
                  <i className="fas fa-bell fa-lg" aria-hidden="true"></i>
                  {unreadCount > 0 && (
                    <span className="notification-badge" style={{ right: -4 }}>
                      {unreadCount}
                    </span>
                  )}
                </button>
              </div>
            )}
            <div
              className="text-white d-flex align-items-center"
              style={userBadgeStyle}
              ref={userBadgeRef}
              onClick={() => setShowUserDropdown(!showUserDropdown)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  setShowUserDropdown((v) => !v);
                }
              }}
              role="button"
              tabIndex={0}
              aria-label="Hesap menüsü"
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
                  fontSize: '0.9rem',
                }}
              >
                {(localStorage.getItem('auth_user') || 'A').charAt(0).toUpperCase()}
              </div>
              <span className="me-2 d-none d-md-inline">{localStorage.getItem('auth_user') || 'Admin'}</span>
              <i className={`fas fa-chevron-${showUserDropdown ? 'up' : 'down'} small`}></i>
            </div>
          </div>

          <button
            className="navbar-toggler border-0"
            type="button"
            data-bs-toggle="collapse"
            data-bs-target="#navbarNav"
            aria-controls="navbarNav"
            aria-expanded="false"
            aria-label="Toggle navigation"
            style={{ background: 'rgba(255,255,255,0.1)' }}
          >
            <span className="navbar-toggler-icon"></span>
          </button>

          <div className="collapse navbar-collapse" id="navbarNav">
            <ul className="navbar-nav me-auto mb-2 mb-lg-0 ms-xl-4">
              {role === 'ADMIN' && isWms && (
                <li className="nav-item">
                  <Link className="nav-link nav-link-custom text-white" to="/" style={navLinkStyle('/')}>
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
                      background: [
                        '/products',
                        '/admin/product-sets',
                        '/categories',
                        '/admin-settings',
                      ].includes(location.pathname)
                        ? 'rgba(255,255,255,0.2)'
                        : 'transparent',
                    }}
                  >
                    <i className="fas fa-warehouse me-2"></i>
                    Envanter
                  </button>
                  <ul
                    className="dropdown-menu border-0 shadow-lg"
                    style={{ borderRadius: '12px', marginTop: '0.5rem' }}
                  >
                    <li>
                      <Link className="dropdown-item" to="/products">
                        <i className="fas fa-box me-2 text-success"></i>
                        Ürünler
                      </Link>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin/product-sets">
                        <i className="fas fa-layer-group me-2 text-info"></i>
                        Ürün Setleri
                      </Link>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/categories">
                        <i className="fas fa-tags me-2 text-warning"></i>
                        Kategoriler
                      </Link>
                    </li>
                    <li>
                      <hr className="dropdown-divider" />
                    </li>
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
                    <li>
                      <hr className="dropdown-divider" />
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/stock-imports">
                        <i className="fas fa-file-excel me-2 text-success"></i>
                        Excel Stok Aktarım Geçmişi
                      </Link>
                    </li>
                  </ul>
                </li>
              )}

              {role === 'ADMIN' && isWms && (
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

              {/* Warehouse roles fill in transfers, so they need the driver directory too. */}
              {isWms && (
                <li className="nav-item">
                  <Link
                    className="nav-link nav-link-custom text-white"
                    to="/drivers"
                    style={navLinkStyle('/drivers')}
                  >
                    <i className="fas fa-truck-fast me-2"></i>
                    Şoförler ve Araçlar
                  </Link>
                </li>
              )}

              {(role !== 'ADMIN' || isWms) && (
                <li className="nav-item">
                  <Link
                    className="nav-link nav-link-custom text-white position-relative d-inline-flex align-items-center"
                    to="/stock"
                    style={navLinkStyle('/stock')}
                  >
                    <i className="fas fa-cubes me-2"></i>
                    <span>Stok Yönetimi</span>
                    {lowStockCount > 0 && <span className="notification-badge">{lowStockCount}</span>}
                  </Link>
                </li>
              )}

              {/* Teslimat makbuzu arşivi: imzalı nüshası dönmemiş sevkiyatların takibi
                  yöneticide, makbuzu basma/yükleme işi sevkiyat ekranında. */}
              {role === 'ADMIN' && (
                <li className="nav-item">
                  <Link
                    className="nav-link nav-link-custom text-white"
                    to="/admin/delivery-receipts"
                    style={navLinkStyle('/admin/delivery-receipts')}
                  >
                    <i className="fas fa-file-invoice me-2"></i>
                    {/* Kısa etiket: navbar 1385px viewport'ta zaten taşıyor ve
                        "Teslimat Makbuzları" tek başına 193px yer kaplıyordu.
                        Panelde başka makbuz türü yok, kısaltma belirsizlik yaratmıyor;
                        sayfanın kendi başlığı tam adı taşıyor. */}
                    Makbuzlar
                  </Link>
                </li>
              )}

              {/* ── E-COMMERCE DROPDOWN ── */}
              {role === 'ADMIN' && isEcom && (
                <li className="nav-item dropdown">
                  <button
                    className="nav-link nav-link-custom dropdown-toggle text-white border-0 bg-transparent w-100 text-start"
                    type="button"
                    data-bs-toggle="dropdown"
                    style={{
                      ...navLinkStyle(null),
                      background: [
                        '/admin/sales-dashboard',
                        '/admin/orders',
                        '/admin/returns',
                        '/admin/customers',
                        '/admin/payments',
                        '/admin/invoices',
                        '/admin/support-tickets',
                        '/admin/contact-messages',
                        '/admin/stock-movements',
                      ].some((p) => location.pathname.startsWith(p))
                        ? 'rgba(255,255,255,0.2)'
                        : 'transparent',
                    }}
                  >
                    <i className="fas fa-store me-2"></i>E-Ticaret
                  </button>
                  <ul
                    className="dropdown-menu border-0 shadow-lg"
                    style={{ borderRadius: '12px', marginTop: '0.5rem' }}
                  >
                    <li>
                      <small
                        className="dropdown-header text-uppercase fw-bold"
                        style={{ fontSize: 10, letterSpacing: '0.05em' }}
                      >
                        Satış
                      </small>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin/sales-dashboard">
                        <i className="fas fa-chart-line me-2 text-primary"></i>Satış Dashboard
                      </Link>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin/orders">
                        <i className="fas fa-shopping-cart me-2 text-success"></i>Siparişler
                      </Link>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin/returns">
                        <i className="fas fa-undo me-2 text-warning"></i>İadeler
                      </Link>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin/payments">
                        <i className="fas fa-credit-card me-2 text-danger"></i>Ödemeler
                      </Link>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin/invoices">
                        <i className="fas fa-file-invoice me-2 text-info"></i>E-Fatura
                      </Link>
                    </li>
                    <li>
                      <hr className="dropdown-divider" />
                    </li>
                    <li>
                      <small
                        className="dropdown-header text-uppercase fw-bold"
                        style={{ fontSize: 10, letterSpacing: '0.05em' }}
                      >
                        Müşteri
                      </small>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin/customers">
                        <i className="fas fa-users me-2 text-warning"></i>Müşteriler
                      </Link>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin/support-tickets">
                        <i className="fas fa-headset me-2 text-info"></i>Destek Talepleri
                      </Link>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin/contact-messages">
                        <i className="fas fa-envelope me-2 text-primary"></i>İletişim Mesajları
                      </Link>
                    </li>
                    <li>
                      <hr className="dropdown-divider" />
                    </li>
                    <li>
                      <small
                        className="dropdown-header text-uppercase fw-bold"
                        style={{ fontSize: 10, letterSpacing: '0.05em' }}
                      >
                        Operasyon
                      </small>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin/stock-movements">
                        <i className="fas fa-exchange-alt me-2 text-primary"></i>Stok Hareketleri
                      </Link>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin/coupons">
                        <i className="fas fa-ticket-alt me-2 text-success"></i>Kuponlar
                      </Link>
                    </li>
                  </ul>
                </li>
              )}

              {/* ── SETTINGS DROPDOWN ── */}
              {role === 'ADMIN' && (
                <li className="nav-item dropdown">
                  <button
                    className="nav-link nav-link-custom dropdown-toggle text-white border-0 bg-transparent w-100 text-start"
                    type="button"
                    data-bs-toggle="dropdown"
                    style={{
                      ...navLinkStyle(null),
                      background: [
                        '/admin/payment-gateways',
                        '/admin/cargo-providers',
                        '/admin/cms',
                        '/admin/site-settings',
                        '/admin-settings',
                        '/desi',
                        '/admin/notifications',
                      ].some((p) => location.pathname.startsWith(p))
                        ? 'rgba(255,255,255,0.2)'
                        : 'transparent',
                    }}
                  >
                    <i className="fas fa-cog me-2"></i>Ayarlar
                  </button>
                  <ul
                    className="dropdown-menu border-0 shadow-lg"
                    style={{ borderRadius: '12px', marginTop: '0.5rem' }}
                  >
                    <li>
                      <small
                        className="dropdown-header text-uppercase fw-bold"
                        style={{ fontSize: 10, letterSpacing: '0.05em' }}
                      >
                        Ödeme & Kargo
                      </small>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin/payment-gateways">
                        <i className="fas fa-credit-card me-2" style={{ color: '#6f42c1' }}></i>Ödeme Ayarları
                      </Link>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin/cargo-providers">
                        <i className="fas fa-truck me-2 text-primary"></i>Kargo Ayarları
                      </Link>
                    </li>
                    <li>
                      <hr className="dropdown-divider" />
                    </li>
                    <li>
                      <small
                        className="dropdown-header text-uppercase fw-bold"
                        style={{ fontSize: 10, letterSpacing: '0.05em' }}
                      >
                        İçerik & Görünüm
                      </small>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin/cms">
                        <i className="fas fa-file-alt me-2 text-info"></i>İçerik Yönetimi
                      </Link>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin/site-settings">
                        <i className="fas fa-globe me-2 text-primary"></i>Site Ayarları
                      </Link>
                    </li>
                    <li>
                      <hr className="dropdown-divider" />
                    </li>
                    <li>
                      <small
                        className="dropdown-header text-uppercase fw-bold"
                        style={{ fontSize: 10, letterSpacing: '0.05em' }}
                      >
                        Sistem
                      </small>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/desi">
                        <i className="fas fa-calculator me-2 text-info"></i>Desi Hesaplama
                      </Link>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin/notifications">
                        <i className="fas fa-bell me-2 text-warning"></i>Bildirimler
                      </Link>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin-settings">
                        <i className="fas fa-tools me-2 text-danger"></i>Yönetici Ayarları
                      </Link>
                    </li>
                    <li>
                      <hr className="dropdown-divider" />
                    </li>
                    <li>
                      <small
                        className="dropdown-header text-uppercase fw-bold"
                        style={{ fontSize: 10, letterSpacing: '0.05em' }}
                      >
                        Cezeri Asistan
                      </small>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin/assistant/dashboard">
                        <i className="fas fa-chart-pie me-2 text-info"></i>Asistan Dashboard
                      </Link>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin/assistant/documents">
                        <i className="fas fa-file-upload me-2 text-success"></i>Doküman Yönetimi
                      </Link>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin/assistant/logs">
                        <i className="fas fa-comments me-2 text-warning"></i>Sohbet Logları
                      </Link>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin/assistant/settings">
                        <i className="fas fa-cog me-2 text-danger"></i>AI Ayarları
                      </Link>
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin/assistant/diagnostics">
                        <i className="fas fa-microscope me-2 text-info"></i>RAG Tanılama
                      </Link>
                    </li>
                    <li>
                      <hr className="dropdown-divider" />
                    </li>
                    <li>
                      <Link className="dropdown-item" to="/admin/help">
                        <i className="fas fa-question-circle me-2 text-primary"></i>Yardım & Kılavuz
                      </Link>
                    </li>
                  </ul>
                </li>
              )}
            </ul>
          </div>
        </div>
      </nav>

      {renderNotificationPortal()}
      {renderUserDropdownPortal()}
    </>
  );
};

export default Navbar;
