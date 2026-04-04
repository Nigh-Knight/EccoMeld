# Domain Pitfalls

**Domain:** Android music discovery app with WebView-embedded Next.js bridge algorithm
**Researched:** 2026-04-03
**Confidence:** MEDIUM-HIGH (most pitfalls verified against official docs or multiple sources)

---

## Critical Pitfalls

Mistakes that cause rewrites, data loss, or fundamentally broken features.

---

### Pitfall 1: IndexedDB Silently Fails Under `file://` Origin

**What goes wrong:** EccoPath relies on IndexedDB as its L2 cache for Last.fm API responses. Loading the bundled Next.js assets directly via `file:///android_asset/...` URLs causes IndexedDB to fail silently or throw `DOMException: The user denied permission to access the database`. The Same-Origin policy treats each `file://` path as a unique origin, so persistent storage is either denied or scoped incorrectly — meaning the cache is empty on every bridge run, which hammers the Last.fm API on every use.

**Why it happens:** Android WebView treats `file://` URLs as having a null origin. IndexedDB requires a stable, same-origin context to persist. This is a known WebView limitation documented in Chrome's WebView source and multiple Capacitor/Cordova issue threads.

**Consequences:** Every bridge computation starts cold with zero cached data. At ~50 API calls per bridge and Last.fm's 5 req/sec limit, this means a 10-15 second minimum wait every run. The cache — EccoPath's primary mechanism for making subsequent bridges fast — is completely bypassed.

**Prevention:** Use `WebViewAssetLoader` (AndroidX Webkit) to serve the bundled assets over a virtual `https://appassets.androidplatform.net/` origin instead of `file://`. This makes IndexedDB work correctly because the content has a stable HTTPS origin. The EccoPath static export must be configured so all asset paths are relative or match this base URL.

**Warning signs:** Bridge computation takes the same 10-15 seconds every run with no improvement after repeated use of the same artists. WebView console logs show IndexedDB open failures.

**Phase:** Must be addressed in the WebView integration phase (before any bridge testing).

---

### Pitfall 2: `addJavascriptInterface` Methods Run on a Private Background Thread

**What goes wrong:** When JavaScript calls a method on the Kotlin object exposed via `addJavascriptInterface` (e.g., `MeldBridge.createPlaylist(json)`), that method executes on a private WebView background thread — not the main thread. If the Kotlin implementation directly touches ExoPlayer, updates Compose state, or posts to any UI-related component, it will crash or silently corrupt state.

**Why it happens:** This is documented behavior in the Android WebView Java bridge: "The system calls methods on a background thread, requiring careful synchronization on the Kotlin side." The JS-to-Kotlin call is synchronous from JS's perspective but arrives on a non-main thread.

**Consequences:** Crash (`CalledFromWrongThreadException`) or silent state corruption when the bridge callback tries to feed the ExoPlayer queue. Given that `MusicService.kt` already has extensive `runBlocking` usage (a known issue in CONCERNS.md), a naive bridge implementation could cause deadlocks.

**Prevention:** In every `@JavascriptInterface` method, immediately dispatch work to the main thread via `Handler(Looper.getMainLooper()).post { }` or a coroutine scope bound to the main dispatcher. Never call ExoPlayer or update Compose state directly in the bridge method body. Keep bridge methods thin: receive JSON, validate, dispatch, return.

**Warning signs:** `CalledFromWrongThreadException` in logcat. Intermittent crashes on bridge completion that don't reproduce in every run.

**Phase:** Must be baked into the initial bridge interface implementation. Retrofitting thread safety is painful.

---

### Pitfall 3: Next.js Static Export Produces Absolute `/_next/` Paths That Break in WebView

**What goes wrong:** By default, `next build` with `output: 'export'` generates HTML that references `/_next/static/...` chunk paths. When loaded from `WebViewAssetLoader` at `https://appassets.androidplatform.net/`, these absolute paths resolve correctly only if the asset loader is configured to intercept the `/_next/` path prefix. If not configured, all JS chunks 404 and the app renders blank.

A related issue: Next.js static exports add `crossorigin=""` attributes to generated `<script>` and `<link>` tags (a known bug reported in next.js issue #61210), which forces CORS preflight checks. In a WebView with no actual server, these checks can silently block asset loading.

**Why it happens:** Next.js assumes a web server context. It does not know it will be loaded from a local virtual origin inside a WebView.

**Consequences:** Blank WebView with no error surfaced in the app. The bridge never loads.

**Prevention:**
1. Set `basePath` in `next.config.js` to match the WebViewAssetLoader path prefix, or ensure the loader handles all paths the exported build references.
2. Strip or patch the `crossorigin` attributes post-build, or configure the WebViewAssetLoader to serve with permissive CORS headers.
3. Build and test the static export inside a minimal Android WebView harness before integrating into EccoMeld proper.

**Warning signs:** WebView shows blank white screen. Chrome Remote Debugging shows 404s for `/_next/static/chunks/*.js`.

**Phase:** Must be validated in a standalone spike before the main WebView integration phase.

---

### Pitfall 4: Last.fm Rate Limit Explosion from Beam Search Fanout

**What goes wrong:** The beam search in `bridgeCrawl.ts` fetches up to 100 similar artists per node, then fetches `getArtistInfo` for each candidate to get tags. The code comment documents ~82 API calls in a worst-case search. EccoPath's `TokenBucket` rate limiter caps at ~3.3 req/sec. In a mobile context where the bridge is triggered interactively and potentially run multiple times in quick succession (e.g., "Random Bridge" button spammed), the rate limiter queue builds up across runs.

Last.fm returns error code 29 (Rate Limit Exceeded) when a single API key exceeds ~5 req/sec averaged over 5 minutes. A single bridge run is within limits. Two bridge runs overlapping are not.

**Why it happens:** The rate limiter is a single `TokenBucket` instance in the web app context, shared across a single page load. When the WebView is reloaded or a new bridge starts before the previous one completes, a second rate limiter instance may spin up — not sharing state with the first.

**Consequences:** Last.fm returns error 29. The bridge crawl fails mid-search. The user sees a failed bridge with no clear error message.

**Prevention:**
1. Expose a `MeldBridge.cancelBridge()` JS interface method. Always call it before starting a new bridge to allow the current crawl to abort and drain the rate limiter queue.
2. Disable the "Random Bridge" button and bridge submit until the current bridge completes.
3. Add exponential backoff on Last.fm error 29 responses (already partially handled by the rate limiter, but needs explicit retry logic on 429/error-29 responses).
4. Consider caching bridge results in the Kotlin/Room layer so repeat runs of the same artist pair skip the API entirely.

**Warning signs:** Bridge fails after a few seconds when user triggers it a second time quickly. Last.fm error code 29 in WebView console logs.

**Phase:** Rate limit resilience must be addressed before the "Random Bridge" button is implemented.

---

### Pitfall 5: YT Music Fuzzy Match Returns Wrong Artist's Track

**What goes wrong:** The fuzzy match for bridge artist tracks (artist name + track title → YT Music video ID) is vulnerable to disambiguation failures — especially for:
- Artists with common names (e.g., "The XX", "Joy", "Pain")
- Non-English artist names where transliteration differs
- Niche/underground artists whose YT Music presence is sparse (fan uploads, wrong metadata)
- Tracks with generic titles ("Intro", "Outro", "Untitled") that match many artists

A match with wrong artist plays the wrong song, which breaks the genre-transition the bridge was designed to create. This is worse than a skip — it actively misleads the user.

**Why it happens:** YT Music search returns results by relevance + popularity. A niche bridge artist's track may rank below a popular artist with a similar name or similar track title. Pure Levenshtein/fuzzy score does not penalize artist mismatch sufficiently.

**Consequences:** Bridge playlist includes tracks from wrong artists. Genre transitions are broken. User trust erodes. Silent wrong matches are harder to detect than silence.

**Prevention:**
1. Require that the matched result's artist name passes a high-confidence fuzzy match against the expected artist name (separate score from track title match). Reject if artist score is below threshold even if title matches.
2. Add duration matching as a tiebreaker — if duration differs by more than 15 seconds from expected, prefer another result.
3. For artists with very low Last.fm listener counts (< 5K), lower the acceptance threshold and prefer "skip silently" over a low-confidence match.
4. Log all fuzzy match decisions during development to calibrate thresholds empirically.

**Warning signs:** During testing, a bridge track plays a song by a completely different artist. Particularly common with niche artists at listener counts < 10K.

**Phase:** Fuzzy match logic must be validated during playlist builder implementation, with explicit test cases for disambiguation.

---

## Moderate Pitfalls

---

### Pitfall 6: WebView Memory Not Reclaimed When Bridge Tab Is Navigated Away

**What goes wrong:** In a Compose-based single-activity app, the Bridge screen hosts a WebView inside `AndroidView`. When the user navigates to another bottom nav tab, the Compose composition may or may not destroy the WebView — depending on how navigation is implemented. If the WebView survives in the backstack with its JS VM running, it continues consuming memory and CPU. EccoPath's beam search, if still in progress, continues firing Last.fm API calls in the background.

**Why it happens:** `AndroidView` does not automatically pool or destroy wrapped Views when a composable leaves the composition. WebView holds a reference to the Activity context (not Application context), which prevents garbage collection of the entire Activity.

**Consequences:** Memory pressure causes system to kill the app's process. Background API calls from an abandoned bridge run consume the Last.fm rate limit for the next run. OOM crash if user navigates away mid-bridge on a low-memory device.

**Prevention:**
1. Store the WebView in a `rememberSaveable` or a ViewModel scoped to the Bridge tab, not recreated on recomposition.
2. In `AndroidView`'s `onRelease` callback, call `webView.stopLoading()`, `webView.loadUrl("about:blank")`, then `webView.destroy()`.
3. Expose a `cancelBridge()` JS interface that the Kotlin side calls before destroying the WebView.
4. Use `DisposableEffect` or `LifecycleObserver` to hook into `ON_PAUSE` / `ON_STOP` to pause the WebView and on `ON_DESTROY` to clean it up.

**Warning signs:** Memory usage climbs steadily after several bridge runs. LeakCanary reports WebView context leaks referencing the Bridge Composable.

**Phase:** Address in WebView lifecycle management subtask of the bridge integration phase.

---

### Pitfall 7: ExoPlayer Queue Manipulation Must Happen on the Main Thread, From the Correct Service Context

**What goes wrong:** The bridge callback arrives from the WebView background thread. The Kotlin bridge handler must add ~30-45 tracks to the existing ExoPlayer queue in `MusicService`. If the bridge handler calls ExoPlayer methods from the wrong thread or the wrong coroutine context, it either crashes or silently does nothing.

An additional risk: if the user is already playing music when the bridge completes, calling `setMediaItems()` instead of `addMediaItems()` would wipe the current queue. The correct call is `addMediaItems(currentQueueSize, bridgeTracks)` or `setMediaItems(bridgeTracks, startIndex=0)` depending on desired behavior (replace vs append).

**Why it happens:** ExoPlayer's `Player` interface is not thread-safe in the general case, though most methods do have safe variants. `MusicService.kt` is already a 3,460-line god-object — adding queue manipulation logic inline will make it harder to reason about correct threading.

**Prevention:**
1. Create a dedicated `BridgeQueueHandler` class responsible only for receiving a bridge path and enqueuing tracks. This isolates the threading logic.
2. All ExoPlayer calls within `BridgeQueueHandler` must be dispatched to `Dispatchers.Main`.
3. Use `addMediaItems()` not `setMediaItems()` unless explicitly implementing "replace queue" behavior.
4. Test both the "queue is empty" path and the "queue is playing" path explicitly.

**Warning signs:** Playlist appears to not load after bridge completion. Current track is interrupted unexpectedly when bridge finishes.

**Phase:** Address during playlist builder and queue integration phase.

---

### Pitfall 8: Spotify TOTP Gist Is a Single Point of Failure for Auth

**What goes wrong:** `SpotifyAuth.kt` fetches TOTP secrets from an external GitHub Gist (`https://api.github.com/gists/22ed9c6ba463899e933427f7de1f0eef`). The Spotify "Random Bridge" and liked-artists features both depend on this auth working. If the Gist is deleted, made private, or the content format changes, all Spotify features break silently or with a non-obvious error.

This is a pre-existing fragility documented in CONCERNS.md, but the bridge milestone amplifies it: "Random Bridge" and "Bridge from your taste" are P0 MVP features that directly depend on Spotify liked artists. A broken Gist breaks two of the most visible MVP features.

**Why it happens:** The current implementation relies on a community-maintained reverse-engineered TOTP secret. It is not under the project's control.

**Consequences:** MVP features that require Spotify fail for all users if the Gist becomes unavailable.

**Prevention:**
1. Mirror the Gist content to a file within the repository or a controlled endpoint before shipping MVP.
2. Cache the last successfully fetched TOTP secret in the app (encrypted DataStore) and use it as fallback.
3. Add explicit error handling on the Gist fetch that surfaces a clear message: "Spotify connection unavailable — bridge from your taste is disabled, but you can still enter artists manually."
4. Design "Random Bridge" to work without Spotify as a fallback (pick from a small hardcoded set of genre-diverse seed pairs).

**Warning signs:** Spotify features stop working and the error traces back to a 404 on the Gist URL.

**Phase:** Mitigation must be in place before shipping any MVP feature that depends on Spotify liked artists.

---

### Pitfall 9: WebView Cold Start Latency Makes Bridge Feel Slow

**What goes wrong:** Android WebView initialization is not free. The first time a WebView is instantiated in a process, it loads the Chromium renderer. On mid-range devices, this can take 300-800ms before any URL is loaded. Add Next.js bundle parse time (the static export JS chunks), and the Bridge tab may feel unresponsive for 1-3 seconds on first open.

If the WebView is destroyed and recreated each time the Bridge tab is visited (a common mistake in Compose + AndroidView integration), users experience this cold start repeatedly.

**Why it happens:** WebView is a separate browser engine process. Its initial load is expensive. Compose's AndroidView does not automatically preserve Views across navigation.

**Prevention:**
1. Instantiate the WebView eagerly in the background when the app starts, before the user taps the Bridge tab.
2. Keep a single WebView instance alive for the app session — do not destroy and recreate it on tab navigation.
3. Show a "Loading bridge engine..." skeleton state when the WebView is initializing, so the delay feels intentional.
4. Minimize the Next.js bundle size: strip out all EccoPath UI components (graph visualization, Poincaré disk layout) from the bundled build since only the algorithm (`bridgeCrawl.ts` + `lastfm.ts`) is needed. The `hyperbolicLayout.ts` file is explicitly noted as not needed for MVP.

**Warning signs:** Bridge tab has a 1-3 second white flash before anything appears. Users report the Bridge tab "freezing" on first tap.

**Phase:** Address WebView pre-initialization in the Bridge tab setup phase. Bundle pruning should happen before the first APK build.

---

### Pitfall 10: Database Migration Will Break If Bridge Tables Are Added Carelessly

**What goes wrong:** The Room database is at schema version 36+ with `fallbackToDestructiveMigration(dropAllTables = true)`. Any new migration that has a bug silently wipes all user data — liked songs, playlists, bridge history — and replaces with an empty database. This is noted as a critical risk in CONCERNS.md.

The bridge milestone will add at least one new table (bridge history for P1, but possibly track-match cache for P0). An incorrect migration drops everything.

**Why it happens:** `dropAllTables = true` in the destructive migration config means Room nukes all tables if migration fails, rather than throwing an error. There are zero existing migration tests.

**Prevention:**
1. Write a migration test for every schema change using Room's `MigrationTestHelper` before committing any migration.
2. Bump the database version atomically: define the migration, write the test, and verify upgrade from version N-1 to N in the test before merging.
3. For bridge track-match cache (if added to Room): consider using a separate database file entirely (`Room.databaseBuilder` with a different name) so a migration failure in the bridge tables cannot affect the main music library database.
4. Set `fallbackToDestructiveMigration(dropAllTables = false)` once migration tests exist — this will at least preserve unaffected tables.

**Warning signs:** App database appears empty after updating to a new build during development. All library data is gone.

**Phase:** Any phase that touches the Room schema must include a migration test as part of the definition of done.

---

## Minor Pitfalls

---

### Pitfall 11: API Key Hard-Coded in EccoPath Source Is Now in an APK

**What goes wrong:** `lastfm.ts` contains a hard-coded Last.fm API key (`c02db6443f45b41cd57d8166c9f042c9`). This is common practice for open web apps where the key is visible in browser devtools anyway. However, bundling EccoPath into an APK means the key is extracted from the assets folder trivially with `apktool`. Anyone who decompiles the APK can extract and abuse the key — potentially exhausting its rate limit or getting it banned.

**Prevention:** This is a low-severity risk for a personal project distributed via GitHub sideload. Document it explicitly. As a mitigation, Last.fm API keys are free and project-specific — rotate the key and create a new one specifically for EccoMeld so abuse does not affect the web EccoPath app's key.

**Phase:** Before first public APK release.

---

### Pitfall 12: `evaluateJavascript` Null Callbacks on Destroyed WebView

**What goes wrong:** `WebView.evaluateJavascript(script, callback)` must be called on the UI thread. If called while the WebView is partially destroyed (e.g., during navigation away from the Bridge tab), the callback fires with `null` rather than throwing. If Kotlin code does not null-check the result, it will NPE.

**Prevention:** Always null-check the `ValueCallback` result. Wrap all `evaluateJavascript` calls with a guard that checks if the WebView is still attached before calling.

**Phase:** Bridge interface implementation phase.

---

### Pitfall 13: Next.js Image Optimization Breaks in Static Export

**What goes wrong:** Next.js `<Image>` component requires a server for optimization and is not compatible with `output: 'export'` unless `images: { unoptimized: true }` is set in `next.config.js`. If EccoPath uses `<Image>` anywhere (e.g., artist images in the graph UI), the static export will fail to build or produce broken image references.

**Prevention:** Add `images: { unoptimized: true }` to `next.config.js` for the EccoPath build. Since only the bridge algorithm is used (not the graph UI), confirm which EccoPath components are actually bundled and strip or skip image-using components.

**Phase:** Build configuration phase before first APK build.

---

### Pitfall 14: `runBlocking` in MusicService + Bridge Callback = Potential Deadlock

**What goes wrong:** `MusicService.kt` already contains 6+ `runBlocking` calls. If the bridge callback (arriving on the WebView background thread) acquires any lock or coroutine scope that is also held by one of those `runBlocking` calls on the main thread, a deadlock is possible — the service hangs, playback stops, ANR follows.

**Prevention:** Do not introduce any new `runBlocking` calls in bridge-related Kotlin code. The bridge handler should use `CoroutineScope(Dispatchers.Main).launch { }` or post to a Handler. Track the existing `runBlocking` locations in CONCERNS.md and avoid any code path that passes through them during bridge completion.

**Phase:** Bridge-to-queue handoff implementation. Flag as a known risk in code review.

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| WebView setup | IndexedDB fails under `file://` (Pitfall 1) | Use `WebViewAssetLoader` from day one |
| WebView setup | Cold start latency (Pitfall 9) | Pre-init WebView in background; strip unused EccoPath UI bundles |
| JS bridge implementation | Background thread violation (Pitfall 2) | All bridge methods dispatch to main thread immediately |
| JS bridge implementation | `evaluateJavascript` null callback (Pitfall 12) | Null-check all callbacks; guard on WebView attached state |
| Build / asset bundling | Next.js absolute paths break in WebView (Pitfall 3) | Spike static export + WebViewAssetLoader before integration |
| Build / asset bundling | Image optimization breaks export (Pitfall 13) | `images: { unoptimized: true }` in next.config.js |
| Bridge algorithm integration | Last.fm rate limit explosion (Pitfall 4) | Disable UI on active bridge; expose cancel interface; backoff on error 29 |
| Playlist builder | Wrong-artist fuzzy match (Pitfall 5) | Require artist score threshold; duration tiebreaker; empirical calibration |
| Queue integration | ExoPlayer thread / addMediaItems (Pitfall 7) | `BridgeQueueHandler` on `Dispatchers.Main`; use `addMediaItems` not `setMediaItems` |
| Spotify features | TOTP Gist single point of failure (Pitfall 8) | Mirror Gist before shipping; fallback messaging; manual artist entry always works |
| Tab lifecycle | WebView memory leak on navigation (Pitfall 6) | Single WebView instance; proper `onRelease` cleanup; `cancelBridge()` JS call |
| Any DB schema change | Destructive migration data loss (Pitfall 10) | Migration test required per schema change; consider separate bridge DB |
| Public APK release | API key in APK (Pitfall 11) | Rotate Last.fm key; document risk |
| Any bridge-adjacent Kotlin | `runBlocking` + bridge callback deadlock (Pitfall 14) | No new `runBlocking`; use coroutines/Handler for bridge dispatch |

---

## Sources

- [Android Developers: Access native APIs with JavaScript bridge](https://developer.android.com/develop/ui/views/layout/webapps/native-api-access-jsbridge) — MEDIUM confidence (official docs, thread behavior confirmed)
- [Android Developers: WebViewAssetLoader](https://developer.android.com/reference/androidx/webkit/WebViewAssetLoader) — HIGH confidence (official API docs)
- [Chromium WebView docs: CORS and WebView API](https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/cors-and-webview-api.md) — HIGH confidence (Chromium source docs)
- [Chromium WebView docs: Android WebView and the UI thread](https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/threading.md) — HIGH confidence (Chromium source docs)
- [Last.fm API Terms of Service §4.4](https://www.last.fm/api/tos) — HIGH confidence (official ToS)
- [Navidrome issue #2421: Last.fm error 29 rate limit](https://github.com/navidrome/navidrome/issues/2421) — MEDIUM confidence (real-world reproduction of rate limit behavior)
- [Google ExoPlayer: Dynamic playlists with ExoPlayer](https://medium.com/google-exoplayer/dynamic-playlists-with-exoplayer-6f53e54a56c0) — MEDIUM confidence (official ExoPlayer team blog)
- [Next.js issue #61210: crossorigin="" on static export](https://github.com/vercel/next.js/issues/61210) — MEDIUM confidence (confirmed bug in Next.js issue tracker)
- [Droidcon 2024: Lifecycle and Performance with Traditional Views + Compose](https://www.droidcon.com/2024/09/17/managing-lifecycle-and-performance-challenges-when-combining-traditional-views-with-jetpack-compose/) — MEDIUM confidence (conference talk, verified against AndroidView docs)
- [AndroidBugFix: evaluateJavascript null response](https://www.androidbugfix.com/2021/11/android-webview-evaluatejavascript.html) — LOW confidence (community article, behavior cross-checked against official docs)
- [EccoPath source: lib/rateLimiter.ts, lib/bridgeCrawl.ts, lib/lastfm.ts](file:///home/kepler/Projects/EccoPath/lib/) — HIGH confidence (primary source, read directly)
- [EccoMeld CONCERNS.md](file:///home/kepler/Projects/EccoMeld/.planning/codebase/CONCERNS.md) — HIGH confidence (primary source, read directly)
