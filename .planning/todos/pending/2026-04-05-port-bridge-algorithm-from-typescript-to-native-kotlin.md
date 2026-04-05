---
created: 2026-04-05T02:43:40.854Z
title: Port bridge algorithm from TypeScript to native Kotlin
area: general
files:
  - eccopath/lib/bridgeCrawl.ts
  - eccopath/lib/lastfm.ts
  - eccopath/lib/rateLimiter.ts
  - app/src/main/kotlin/com/metrolist/music/di/BridgeModule.kt
  - app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt
---

## Problem

The bridge algorithm currently runs in a WebView via EccoPath's TypeScript code. This adds complexity (WebView singleton, JS bridge interface, AAPT _next/ workarounds, asset packaging) and performance issues (cold IndexedDB cache means every API call is live, rate-limited to 3.3 req/sec). The website is fast because it has cached data; the app starts cold every time.

A native Kotlin port would:
- Eliminate the WebView entirely (simpler architecture)
- Use the existing LastFM Kotlin module for API calls
- Cache results in Room database (persistent across sessions, shared with other features)
- Run as a coroutine with proper cancellation support
- Remove ~200 lines of WebView setup code (BridgeModule, asset loader, JS glue)

## Solution

- Port `bridgeCrawl.ts` (bidirectional beam search + fallback BFS) to Kotlin coroutines
- Port `rateLimiter.ts` (token bucket) to Kotlin — or use the existing rate limiting in the LastFM module
- Use Room database as the cache layer instead of IndexedDB
- BridgeViewModel calls the Kotlin bridge directly instead of `evaluateJavascript`
- Remove BridgeModule WebView provider, MeldBridgeInterface JS bridge, and eccopath asset packaging
- Keep eccopath submodule for the web version but decouple from Android build
