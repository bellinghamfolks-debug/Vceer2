/* Basir AI — service worker (offline app shell only).
 * API calls (/api/*) are never cached. */
const VERSION = 'basir-v1';
const SHELL = [
  './',
  'index.html',
  'styles.css',
  'manifest.webmanifest',
  'js/app.js',
  'js/i18n.js',
  'js/storage.js',
  'js/api.js',
  'js/speech.js',
  'js/views.js',
  'icons/icon.svg',
  'icons/icon-192.png',
  'icons/icon-512.png',
  'icons/apple-touch-icon.png',
  'icons/favicon-32.png'
];

self.addEventListener('install', (e) => {
  e.waitUntil((async () => {
    const cache = await caches.open(VERSION);
    await cache.addAll(SHELL).catch(() => {});
    self.skipWaiting();
  })());
});

self.addEventListener('activate', (e) => {
  e.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(keys.filter(k => k !== VERSION).map(k => caches.delete(k)));
    self.clients.claim();
  })());
});

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  // Never cache API calls.
  if (url.pathname.startsWith('/api/')) return;
  if (e.request.method !== 'GET') return;
  e.respondWith((async () => {
    const cache = await caches.open(VERSION);
    const cached = await cache.match(e.request, { ignoreSearch: true });
    if (cached) {
      // Background refresh
      fetch(e.request).then(r => { if (r && r.ok) cache.put(e.request, r.clone()); }).catch(() => {});
      return cached;
    }
    try {
      const res = await fetch(e.request);
      if (res && res.ok && res.type === 'basic') cache.put(e.request, res.clone());
      return res;
    } catch {
      // SPA fallback for navigations
      if (e.request.mode === 'navigate') {
        const fallback = await cache.match('index.html');
        if (fallback) return fallback;
      }
      throw new Error('offline');
    }
  })());
});
