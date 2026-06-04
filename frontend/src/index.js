import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import ErrorBoundary from './components/ErrorBoundary';
import { BrowserRouter } from 'react-router-dom';
import { HelmetProvider } from 'react-helmet-async';
import axios from 'axios';

// Rewrite legacy /api/* paths to /api/admin/* (matches nginx rewrite rule).
// Excluded:
//   /api/store/*        — storefront endpoints
//   /api/info           — public public info
//   /api/cezeri/*       — WMS assistant chat (physically at /api/cezeri on backend)
//   /api/assistant/*    — public assistant endpoints (flags) consumed by BOTH
//                          admin and store layouts — rewriting to /api/admin would
//                          require admin auth and fail 403 in the store tab.
axios.interceptors.request.use((config) => {
  if (config.url && config.url.startsWith('/api/') &&
      !config.url.startsWith('/api/admin/') &&
      !config.url.startsWith('/api/store/') &&
      !config.url.startsWith('/api/info') &&
      !config.url.startsWith('/api/cezeri/') &&
      !config.url.startsWith('/api/assistant/')) {
    config.url = config.url.replace('/api/', '/api/admin/');
  }
  // attach Bearer auth header — admin or customer token based on request path
  const isStoreApi = config.url && config.url.startsWith('/api/store/');
  const token = isStoreApi
    ? (localStorage.getItem('customer_token') || localStorage.getItem('auth_token'))
    : (localStorage.getItem('auth_token') || localStorage.getItem('customer_token'));
  if (token) {
    config.headers = config.headers || {};
    config.headers['Authorization'] = `Bearer ${token}`;
  }
  return config;
});

// Global error interceptor to ensure Turkish messages
axios.interceptors.response.use(
  (response) => response,
  (error) => {
    // --- Session expiry check ---
    if (error && error.response) {
      const status = error.response.status;
      const reqUrl = error?.config?.url ?? '';
      const isAuthEndpoint = reqUrl.includes('/auth/');
      const isNotifications = reqUrl.includes('/api/notifications');
      const errCode = error?.response?.data?.code || error?.response?.data?.errorCode;
      const isAdminSecurityError = ['AUTH_002','AUTH_003','AUTH_004','AUTH_005','AUTH_006','AUTH_007'].includes(errCode);

      // --- Session check: ONLY 401 (Unauthorized) + token present = a genuine session expiry ---
      // 403 (Forbidden) = insufficient permissions, session may still be active — DO NOT show modal
      const hasAdminToken = !!localStorage.getItem('auth_token');
      const hasCustomerToken = !!localStorage.getItem('customer_token');
      const isStoreRequest = reqUrl.includes('/api/store/');
      const isRealSessionExpiry = status === 401 && (hasAdminToken || hasCustomerToken)
          && !isAuthEndpoint && !isNotifications && !isAdminSecurityError;

      if (isRealSessionExpiry) {
        if (!window.__sessionExpiredHandling) {
          window.__sessionExpiredHandling = true;

          // Determine the correct login page: store request → store login, admin request → admin login
          const loginUrl = isStoreRequest ? '/giris' : '/login';
          const tokenKey = isStoreRequest ? 'customer_token' : 'auth_token';

          const modalId = 'session-expired-modal';

          // First clean up any old/orphan instance
          const existing = document.getElementById(modalId);
          if (existing) {
            try { existing.remove(); } catch {}
          }

          const wrapper = document.createElement('div');
          wrapper.id = modalId;
          wrapper.innerHTML = `
            <div class="modal show d-block" tabindex="-1" style="background-color: rgba(0,0,0,0.5); z-index: 2000;">
              <div class="modal-dialog modal-dialog-centered">
                <div class="modal-content shadow-lg border-0">
                  <div class="modal-header border-0 pb-0">
                    <div class="w-100 text-center pt-3">
                      <div class="text-warning mb-3">
                        <i class="fas fa-clock fa-3x"></i>
                      </div>
                      <h5 class="modal-title fw-bold">Oturum Süresi Doldu</h5>
                    </div>
                  </div>
                  <div class="modal-body text-center px-4 py-3">
                    <p class="text-muted mb-0">Güvenliğiniz için tekrar giriş yapmanız gerekiyor.</p>
                  </div>
                  <div class="modal-footer border-0 justify-content-center gap-2 pb-4">
                    <button type="button" id="session-expired-ok" class="btn btn-primary px-4">
                      <i class="fas fa-sign-in-alt me-2"></i>
                      Giriş Ekranına Git
                    </button>
                  </div>
                </div>
              </div>
            </div>`;
          document.body.appendChild(wrapper);

          const cleanup = () => {
            try {
              const el = document.getElementById(modalId);
              if (el) el.remove();
            } catch {}
            window.__sessionExpiredHandling = false;
          };

          const onOk = () => {
            try {
              localStorage.removeItem(tokenKey);
              if (!isStoreRequest) {
                localStorage.removeItem('auth_user');
                localStorage.removeItem('auth_role');
              }
              window.dispatchEvent(new Event('auth-changed'));
            } catch {}
            cleanup();
            window.location.replace(loginUrl);
          };

          // Event delegation: capture click via the wrapper (works even if the button is detached)
          wrapper.addEventListener('click', (e) => {
            if (e.target.closest('#session-expired-ok')) {
              onOk();
            }
          });

          // Allow closing with the Escape key too (and go to login)
          const onKey = (e) => {
            if (e.key === 'Escape' || e.key === 'Enter') {
              document.removeEventListener('keydown', onKey);
              onOk();
            }
          };
          document.addEventListener('keydown', onKey);

          // Failsafe: if still open after 60 seconds, force navigation to the login page
          setTimeout(() => {
            if (document.getElementById(modalId)) {
              console.warn('[session-expired] Modal 60 saniyedir açık, otomatik login\'e yönlendiriliyor');
              onOk();
            }
          }, 60000);
        }
      }
    }

    // --- Error message translation / normalization ---
    if (error && error.response) {
      const res = error.response;
      const data = res.data;

      // Extract the original backend message
      let message = '';
      if (typeof data === 'string') {
        message = data;
      } else if (data && typeof data === 'object' && typeof data.message === 'string') {
        message = data.message;
      }

      let tr = '';
      const m = message;

      if (!m) {
        tr = 'Bir hata oluştu';
      } else {
        tr = m
          .replace(/Quantity cannot be negative/gi, 'Miktar negatif olamaz')
          .replace(/Minimum stock level cannot be negative/gi, 'Minimum stok seviyesi negatif olamaz')
          .replace(/Reserved quantity cannot be negative/gi, 'Rezerve miktarı negatif olamaz')
          .replace(/Consigned quantity cannot be negative/gi, 'Emanet miktarı negatif olamaz')
          .replace(/Stock not found with id[:]?\s*\d+/gi, 'Stok bulunamadı')
          .replace(/Product not found with id[:]?\s*\d+/gi, 'Ürün bulunamadı')
          .replace(/Warehouse not found with id[:]?\s*\d+/gi, 'Depo bulunamadı')
          .replace(/Quantity to add must be positive/gi, 'Eklenecek miktar pozitif olmalı')
          .replace(/Quantity to remove must be positive/gi, 'Çıkarılacak miktar pozitif olmalı')
          .replace(/Quantity to reserve must be positive/gi, 'Rezerve edilecek miktar pozitif olmalı')
          .replace(/Quantity to release must be positive/gi, 'Kaldırılacak miktar pozitif olmalı')
          .replace(/Insufficient available stock\.[^]*/gi, (s) => {
            const nums = s.match(/Available:\s*(\d+).*Requested:\s*(\d+)/i);
            if (nums) return `Yetersiz kullanılabilir stok. Mevcut: ${nums[1]}, İstenen: ${nums[2]}`;
            return 'Yetersiz kullanılabilir stok';
          })
          .replace(/Cannot release more than reserved quantity\.[^]*/gi, (s) => {
            const nums = s.match(/Reserved:\s*(\d+).*Requested:\s*(\d+)/i);
            if (nums) return `Rezerve miktardan fazla bırakılamaz. Rezerve: ${nums[1]}, İstenen: ${nums[2]}`;
            return 'Rezerve miktardan fazla bırakılamaz';
          })
          .replace(/Stock already exists for this product in the selected warehouse/gi, 'Seçili depoda bu ürün için stok zaten mevcut')
          .replace(/Product is required/gi, 'Ürün zorunludur')
          .replace(/Warehouse is required/gi, 'Depo zorunludur');
      }

      const finalMessage = tr || message || 'Beklenmeyen bir hata oluştu';

      // *** IMPORTANT: we don't stringify data, we preserve the object ***
      if (data && typeof data === 'object') {
        // errorCode, details, etc. are preserved
        res.data = {
          ...data,
          message: finalMessage
        };
      } else {
        // If backend sent a plain string, at least wrap it in an object
        res.data = {
          message: finalMessage
        };
      }
    } else if (error && error.message === 'Network Error') {
      // Generic message for network errors
      error.message = 'Ağ hatası: Sunucuya ulaşılamıyor';
      // If desired:
      // error.response = { data: { message: error.message } };
    }

    return Promise.reject(error);
  }
);

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <ErrorBoundary>
      <HelmetProvider>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </HelmetProvider>
    </ErrorBoundary>
  </React.StrictMode>
);

// ── Service Worker (PWA) — production only, store domain only ──
// Can be disabled with REACT_APP_DISABLE_SW=true.
// SW is not loaded on admin/WMS hosts (real-time data required).
if ('serviceWorker' in navigator
    && process.env.NODE_ENV === 'production'
    && process.env.REACT_APP_DISABLE_SW !== 'true') {
  const host = window.location.hostname || '';
  const adminPrefixes = (process.env.REACT_APP_ADMIN_HOSTS || 'admin,wms').split(',').map(s => s.trim());
  const isAdmin = adminPrefixes.some(p => host.startsWith(p + '.'));
  if (!isAdmin) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('/service-worker.js').catch(err => {
        // eslint-disable-next-line no-console
        console.warn('Service worker registration failed:', err);
      });
    });
  }
}
