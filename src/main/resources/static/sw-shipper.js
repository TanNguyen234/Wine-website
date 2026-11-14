/* ==========================================================================
   StrongWine Shipper PWA Service Worker
   Caches ONLY static shell assets. NEVER caches /api/**, HTML/PII, or POSTs.
   ========================================================================== */

var SW_CACHE_VERSION = "sw-shipper-v3-static-2";

var STATIC_SHELL_ASSETS = [
  "/css/tokens.css?v=20261114c",
  "/css/shipper.css?v=20261114c",
  "/js/theme-toggle.js",
  "/js/dialog-kit.js",
  "/js/shipper-mobile-actions.js",
  "/favicon.svg?v=20261114c",
  "/manifest.webmanifest"
];

var STATIC_PATH_PREFIXES = [
  "/css/",
  "/js/",
  "/images/"
];

var STATIC_EXACT_PATHS = {
  "/favicon.svg": true,
  "/manifest.webmanifest": true
};

function isCacheableStaticRequest(request) {
  if (!request || request.method !== "GET") {
    return false;
  }

  var url = new URL(request.url);
  if (url.origin !== self.location.origin) {
    return false;
  }

  if (request.mode === "navigate") {
    return false;
  }

  var accept = request.headers.get("Accept") || "";
  if (accept.indexOf("text/event-stream") !== -1 || accept.indexOf("text/html") !== -1) {
    return false;
  }

  var pathname = url.pathname;
  if (
    pathname.indexOf("/api/") === 0 ||
    pathname.indexOf("/shipper/") === 0 ||
    pathname.indexOf("/admin/") === 0
  ) {
    return false;
  }

  if (STATIC_EXACT_PATHS[pathname]) {
    return true;
  }

  for (var i = 0; i < STATIC_PATH_PREFIXES.length; i += 1) {
    if (pathname.indexOf(STATIC_PATH_PREFIXES[i]) === 0) {
      return true;
    }
  }

  return false;
}

self.addEventListener("install", function (event) {
  event.waitUntil(
    caches.open(SW_CACHE_VERSION).then(function (cache) {
      return Promise.allSettled(
        STATIC_SHELL_ASSETS.map(function (assetUrl) {
          return cache.add(assetUrl);
        })
      );
    }).then(function () {
      return self.skipWaiting();
    })
  );
});

self.addEventListener("activate", function (event) {
  event.waitUntil(
    caches.keys().then(function (cacheNames) {
      return Promise.all(
        cacheNames.map(function (cacheName) {
          if (cacheName !== SW_CACHE_VERSION && cacheName.indexOf("sw-shipper-") === 0) {
            return caches.delete(cacheName);
          }
          return Promise.resolve();
        })
      );
    }).then(function () {
      return self.clients.claim();
    })
  );
});

self.addEventListener("fetch", function (event) {
  if (!isCacheableStaticRequest(event.request)) {
    return;
  }

  event.respondWith(
    caches.open(SW_CACHE_VERSION).then(function (cache) {
      return fetch(event.request).then(function (networkResponse) {
        if (networkResponse && networkResponse.ok && networkResponse.type === "basic") {
          cache.put(event.request, networkResponse.clone());
        }
        return networkResponse;
      }).catch(function () {
        return cache.match(event.request);
      });
    })
  );
});
