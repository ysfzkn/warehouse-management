import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import { BrowserRouter } from 'react-router-dom';
import axios from 'axios';

// attach Basic auth header from localStorage token
axios.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth_token');
  if (token) {
    config.headers = config.headers || {};
    config.headers['Authorization'] = `Basic ${token}`;
  }
  return config;
});

// Global error interceptor to ensure Turkish messages
axios.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error && error.response) {
      const data = error.response.data;
      const message = typeof data === 'string' ? data : (data?.message || '');
      let tr = '';
      const m = message;
      if (!m) {
        tr = 'Bir hata oluştu';
      } else {
        // Backend-known messages mapping
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
      error.response.data = tr || 'Beklenmeyen bir hata oluştu';
    } else if (error && error.message === 'Network Error') {
      error.message = 'Ağ hatası: Sunucuya ulaşılamıyor';
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
