/* eslint-disable */
/**
 * Mağaza Service Worker — minimum PWA + offline shell.
 *
 * Stratejiler:
 *   - Static assets (.js, .css, font, image): cache-first
 *   - API çağrıları (/api/): network-first, cache fallback yok (taze veri kritik)
 *   - HTML navigations: network-first, offline'da app-shell fallback
 *
 * Aktif etmek için index.js'te navigator.serviceWorker.register('/service-worker.js')
 * çağrısı yapılmalı (registerSW.js helper'ı ile sarılı).
 */

const CACHE_VERSION = 'magaza-v1';
const STATIC_CACHE = `${CACHE_VERSION}-static`;
const RUNTIME_CACHE = `${CACHE_VERSION}-runtime`;

const APP_SHELL = [
  '/',
  '/index.html',
  '/manifest.json',
  '/favicon.ico',
  '/favicon.svg',
];

// ── Install: pre-cache app shell ──
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(STATIC_CACHE).then(cache => cache.addAll(APP_SHELL))
  );
  self.skipWaiting();
});

// ── Activate: eski cache temizliği ──
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then(keys => Promise.all(
      keys.filter(k => !k.startsWith(CACHE_VERSION)).map(k => caches.delete(k))
    )).then(() => self.clients.claim())
  );
});

// ── Fetch strategy ──
self.addEventListener('fetch', (event) => {
  const req = event.request;

  // Yalnız GET'leri cache'le; POST/PUT/DELETE asla cache'lenmez
  if (req.method !== 'GET') return;

  const url = new URL(req.url);

  // API: network-first, cache yok (KVKK + güncel veri için)
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(
      fetch(req).catch(() => new Response(JSON.stringify({
        error: 'OFFLINE',
        message: 'Çevrimdışı görünüyorsunuz. İnternet bağlantınızı kontrol edin.'
      }), { headers: { 'Content-Type': 'application/json' }, status: 503 }))
    );
    return;
  }

  // Static assets: cache-first
  if (req.destination === 'script' || req.destination === 'style' ||
      req.destination === 'font' || req.destination === 'image') {
    event.respondWith(
      caches.match(req).then(cached => cached || fetch(req).then(resp => {
        if (resp && resp.status === 200) {
          const clone = resp.clone();
          caches.open(RUNTIME_CACHE).then(c => c.put(req, clone));
        }
        return resp;
      }))
    );
    return;
  }

  // HTML navigations: network-first, offline'da index.html'e düş
  if (req.mode === 'navigate' || (req.destination === 'document')) {
    event.respondWith(
      fetch(req).catch(() => caches.match('/index.html'))
    );
    return;
  }
});
