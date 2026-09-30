// Daybook's service worker: after one visit the whole app — page, script, wasm, icons — loads with
// no network at all, which is the point of an app whose puzzles are generated on the device.
//
// Freshness after a deploy comes from the fetch rules rather than from this file changing:
//  - the page and anything without a content hash in its name (index.html, daybook.js, the
//    manifest) is network-first, falling back to the cache when offline or slow;
//  - files whose names carry a content hash (the build's .wasm) never change under that name, so
//    they are cache-first, and a deploy simply asks for new names.
// Every path is relative to this file, because the app is served from a subpath.

const CACHE = 'daybook-v1';
const NETWORK_TIMEOUT_MS = 4000;
const SHELL = [
  './',
  'daybook.js',
  'manifest.webmanifest',
  'icons/icon-180.png',
  'icons/icon-192.png',
  'icons/icon-512.png',
  'icons/icon-maskable-512.png',
];

const HASHED = /[0-9a-f]{16,}[^/]*$/;

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE)
      // One missing optional file must not stop the worker installing.
      .then(cache => Promise.all(SHELL.map(url => cache.add(url).catch(() => {}))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

function inScope(url) {
  return url.origin === self.location.origin && url.href.startsWith(self.registration.scope);
}

/** The page is stored once, under the scope URL, whatever query string it was opened with. */
function cacheKey(request) {
  const url = new URL(request.url);
  if (request.mode === 'navigate' || url.pathname.endsWith('/index.html')) {
    return self.registration.scope;
  }
  url.search = '';
  url.hash = '';
  return url.href;
}

async function networkFirst(request) {
  const cache = await caches.open(CACHE);
  const key = cacheKey(request);
  const network = fetch(request).then(response => {
    if (response.ok) cache.put(key, response.clone());
    return response;
  });
  network.catch(() => {});
  const timeout = new Promise(resolve => setTimeout(resolve, NETWORK_TIMEOUT_MS));
  try {
    const first = await Promise.race([network, timeout]);
    if (first) return first;
  } catch (e) {
    // Offline: fall through to the cache.
  }
  const cached = await cache.match(key);
  if (cached) return cached;
  return network;
}

async function cacheFirst(request) {
  const cache = await caches.open(CACHE);
  const key = cacheKey(request);
  const cached = await cache.match(key);
  if (cached) return cached;
  const response = await fetch(request);
  if (response.ok) cache.put(key, response.clone());
  return response;
}

self.addEventListener('fetch', event => {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (!inScope(url)) return;
  const hashed = request.mode !== 'navigate' && HASHED.test(url.pathname);
  event.respondWith(hashed ? cacheFirst(request) : networkFirst(request));
});

// The first visit loads the app before this worker controls the page, so the page sends the list of
// everything it loaded and the worker stores what it does not have. Hashed files a running page no
// longer uses are left over from an older deploy and are dropped.
self.addEventListener('message', event => {
  const data = event.data || {};
  if (data.type !== 'cache-urls' || !Array.isArray(data.urls)) return;
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE);
    const wanted = new Set();
    for (const raw of data.urls) {
      let url;
      try { url = new URL(raw, self.registration.scope); } catch (e) { continue; }
      // The worker's own script is the browser's to update, never the cache's.
      if (!inScope(url) || url.pathname.endsWith('.map') || url.pathname.endsWith('/sw.js')) continue;
      const request = new Request(url.href);
      const key = cacheKey(request);
      wanted.add(key);
      if (!(await cache.match(key))) {
        try {
          const response = await fetch(request);
          if (response.ok) await cache.put(key, response);
        } catch (e) {
          // Offline right now; the fetch handler will store it next time it is asked for.
        }
      }
    }
    // Only a list sent once the app is running names every file it needs; an early one may not.
    if (!data.prune || ![...wanted].some(key => key.endsWith('.wasm'))) return;
    for (const request of await cache.keys()) {
      if (HASHED.test(new URL(request.url).pathname) && !wanted.has(request.url)) {
        await cache.delete(request);
      }
    }
  })());
});
