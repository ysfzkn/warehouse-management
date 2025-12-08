import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import { BrowserRouter } from 'react-router-dom';
import axios from 'axios';

// attach Bearer auth header from localStorage token
axios.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth_token');
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
    // --- Oturum süresi kontrolü ---
    if (error && error.response) {
      const status = error.response.status;
      const reqUrl = error?.config?.url ?? '';
      const isAuthEndpoint = reqUrl.includes('/auth/login');
      const isNotifications = reqUrl.includes('/api/notifications');
      const errCode = error?.response?.data?.code || error?.response?.data?.errorCode;
      const isAdminSecurityError = ['AUTH_002','AUTH_003','AUTH_004','AUTH_005','AUTH_006','AUTH_007'].includes(errCode);

      // Session expired or unauthorized (skip admin security code errors)
      if ((status === 401 || status === 403) && !isAuthEndpoint && !isNotifications && !isAdminSecurityError) {
        if (!window.__sessionExpiredHandling) {
          window.__sessionExpiredHandling = true;

          const modalId = 'session-expired-modal';
          if (!document.getElementById(modalId)) {
            const wrapper = document.createElement('div');
            wrapper.id = modalId;
            wrapper.innerHTML = `
              <div class="modal show d-block" tabindex="-1" style="background-color: rgba(0,0,0,0.5); z-index: 2000;">
                <div class="modal-dialog modal-dialog-centered">
                  <div class="modal-content shadow-lg border-0">
                    <div class="modal-header border-0 pb-0">
                      <div class="w-100 text-center pt-3">
                        <div class="text-info mb-3">
                          <i class="fas fa-info-circle fa-3x"></i>
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
              try { document.body.removeChild(wrapper); } catch {}
              window.__sessionExpiredHandling = false;
            };

            const onOk = () => {
              try {
                localStorage.removeItem('auth_token');
                localStorage.removeItem('auth_user');
                localStorage.removeItem('auth_role');
                window.dispatchEvent(new Event('auth-changed'));
              } catch {}
              cleanup();
              window.location.replace('/login');
            };

            document.getElementById('session-expired-ok')?.addEventListener('click', onOk);
          }
        }
      }
    }

    // --- Hata mesajı çeviri / normalize ---
    if (error && error.response) {
      const res = error.response;
      const data = res.data;

      // Orijinal backend mesajını çıkart
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

      // *** ÖNEMLİ KISIM: data'yı stringe çevirmiyoruz, objeyi koruyoruz ***
      if (data && typeof data === 'object') {
        // errorCode, details vs. korunuyor
        res.data = {
          ...data,
          message: finalMessage
        };
      } else {
        // Backend düz string yolladıysa en azından objeye sar
        res.data = {
          message: finalMessage
        };
      }
    } else if (error && error.message === 'Network Error') {
      // Network hataları için genel mesaj
      error.message = 'Ağ hatası: Sunucuya ulaşılamıyor';
      // İstersen:
      // error.response = { data: { message: error.message } };
    }

    return Promise.reject(error);
  }
);

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);
