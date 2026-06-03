/* eslint-disable */
/**
 * Store Service Worker — minimal PWA + offline shell.
 *
 * Strategies:
 *   - Static assets (.js, .css, font, image): cache-first
 *   - API calls (/api/): network-first, no cache fallback (fresh data is critical)
 *   - HTML navigations: network-first, app-shell fallback when offline
 *
 * To enable, navigator.serviceWorker.register('/service-worker.js') must be called
 * in index.js (wrapped by the registerSW.js helper).
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

// ── Activate: clean up old caches ──
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

  // Cache GET requests only; POST/PUT/DELETE are never cached
  if (req.method !== 'GET') return;

  const url = new URL(req.url);

  // API: network-first, no cache (for KVKK compliance + up-to-date data)
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

  // HTML navigations: network-first, fall back to index.html when offline
  if (req.mode === 'navigate' || (req.destination === 'document')) {
    event.respondWith(
      fetch(req).catch(() => caches.match('/index.html'))
    );
    return;
  }
});
