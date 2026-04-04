# Project Research Summary

**Project:** EccoMeld — Bridge Discovery Milestone
**Domain:** Android music discovery app with WebView-embedded Next.js bridge algorithm + YT Music playback
**Researched:** 2026-04-03
**Confidence:** HIGH (stack and architecture verified against official docs and existing codebase; features verified against competitive products; pitfalls cross-confirmed from primary sources)

---

## Executive Summary

EccoMeld's Bridge Discovery milestone is a focused additive feature on top of a mature Android music player (Meld/InnerTune). The goal is to embed EccoPath's JavaScript beam-search algorithm — which finds niche-weighted paths between artists via the Last.fm similarity graph — inside an Android WebView, then pipe the resulting bridge path into a YT Music playlist that auto-plays through the genre transition. The engineering challenge is not building a new app; it is safely bridging two runtimes (Kotlin and JavaScript), correctly bundling a Next.js app as a static WebView asset, and fuzzy-matching bridge artist tracks against YT Music's catalog.

The recommended approach is deliberately minimal: two new dependencies (`androidx.webkit:webkit:1.15.0` for the JS bridge and `commons-text:1.13.0` for fuzzy matching), a single new `BridgeViewModel` + `BridgeScreen`, and a thin `MeldBridgeInterface` that receives EccoPath's JSON output and hands it off to the existing `playerConnection.playQueue()` path. No existing module needs restructuring; every new component slots into the established MVVM layers. The most technically novel piece — the JS-to-Kotlin bridge — is fully covered by AndroidX WebKit's `addWebMessageListener` or `addJavascriptInterface` APIs, both well-documented.

The key risks concentrate around three areas: WebView asset serving (IndexedDB silently breaks under `file://` origins — must use `WebViewAssetLoader` from day one), thread safety (the JS bridge fires on a background thread; every callback must dispatch to main immediately), and the Last.fm rate limiter (beam search makes ~82 API calls; overlapping bridge runs can exhaust the limit). All three are well-understood problems with clear mitigations. None require rearchitecting — they require discipline during the bridge integration phase.

---

## Key Findings

### Recommended Stack

The existing stack (Kotlin 2.3.10, Compose, Material 3, Media3/ExoPlayer, Room, Hilt, Ktor) is unchanged. Only two new dependencies are added. `androidx.webkit:webkit:1.15.0` provides `WebViewAssetLoader` (serves bundled Next.js assets from a stable virtual HTTPS origin, enabling IndexedDB) and `addWebMessageListener` / `addJavascriptInterface` (the JS-to-Kotlin message channel). `org.apache.commons:commons-text:1.13.0` provides `JaroWinklerSimilarity` and `LevenshteinDistance` for YT Music track matching; it is consistent with the existing `commons-lang3` dependency already in the project. Tag Jaccard similarity for the Random Bridge feature requires no library — it is a 10-line pure Kotlin implementation.

EccoPath (the Next.js 16.2.2 app) is bundled as a static export into `app/src/main/assets/eccopath/`, built via a Gradle task before `assembleRelease`. The Next.js config must set `output: "export"`, `basePath: ""`, `assetPrefix: ""`, and `images: { unoptimized: true }` for the Android build variant. The `out/` directory is a build artifact and must be gitignored. A raw `AndroidView { WebView }` composable is used — not Accompanist (deprecated) or any third-party Compose WebView wrapper.

**Core technologies:**
- `androidx.webkit:webkit:1.15.0` — JS bridge + asset loader — only safe way to serve assets with a stable HTTPS origin that enables IndexedDB
- `org.apache.commons:commons-text:1.13.0` — fuzzy track matching — consistent with existing Commons Lang3 dependency, 15-year track record, Apache 2.0 license
- `WebViewAssetLoader` + `AssetsPathHandler` — serves `https://appassets.androidplatform.net/` — required to prevent IndexedDB failure on `file://`
- `addJavascriptInterface` (or `WebMessageListener`) — JS-to-Kotlin message channel — `@JavascriptInterface` is simpler for first-party bundled code; `WebMessageListener` adds origin enforcement with no benefit for bundled assets
- `AndroidView { WebView }` (raw Compose) — ~50-line wrapper — no third-party lib needed; full control over asset loader and bridge wiring

### Expected Features

The competitive landscape (Boil the Frog, WhoSampled Six Degrees, Six Degrees of Spotify, MusicLynx) validates the table stakes. EccoMeld is the only product combining Last.fm niche-weighted path finding + real playback + Android native UX — the market gap is real.

**Must have (table stakes):**
- Two artist search inputs with Last.fm autocomplete — blocks all bridge functionality; must support niche/obscure artists
- Meaningful loading feedback with step progress ("Found 3 of 6 hops…") — 5-30 second waits read as broken without it
- Linear path result view with intermediate artists, genre tags, and listener counts — the product's face; every competitor shows the chain
- Optimistic queue population + auto-play on bridge completion — a post-completion delay reads as a second load failure
- Silent skip on YT Music track match failures — error dialogs per failed track destroy playback experience
- Clear "no path found" error state — silent failure is worse than an explicit message
- Random Bridge from Spotify liked artists (lowest Tag Jaccard overlap) — one-tap discovery; strong differentiator

**Should have (competitive):**
- Currently-playing artist highlighted in path view — contextualizes each song within the journey
- Known vs. unknown artist visual distinction (using Spotify top/saved artists as "known" set)
- Seed artist suggestions from Spotify library below search inputs — reduces cold-start friction
- 2 popular + 3-5 deep cuts track selection per bridge artist — Boil the Frog's one-track model is the baseline; this is EccoMeld's clear UX improvement

**Defer (P1+):**
- Staging cards / path walker — explicitly scoped to P1 in PRD
- Graph visualization (force-directed) — P1 Graph Screen; adds interaction complexity without core value
- Social sharing — requires deep links, no backend; P2
- Bridge History in Room — useful but not required for MVP playback loop
- Configurable hop count / path depth slider — confusion-inducing power-user feature; auto-selection is correct default

**Explicit anti-features (never build):**
- Pre-play track editing — breaks journey coherence; skip during playback is sufficient
- AI-generated bridge narration — latency, cost, and genre tags already do the explanatory work
- Real-time community stats — requires backend and user accounts; out of scope for GPL sideload app

### Architecture Approach

Bridge Discovery is entirely additive to the existing five-layer MVVM architecture. One new screen (`BridgeScreen`), one new ViewModel (`BridgeViewModel`), one UI helper (`BridgeWebViewManager` wrapping `AndroidView`), one bridge interface class (`MeldBridgeInterface`), and one playlist builder (`BridgePlaylistBuilder`) are all the new components needed. All new code uses existing infrastructure: `PlayerConnection.playQueue()` for handoff to ExoPlayer, `YouTube.search()` for track lookup, the Spotify singleton for liked artists, and `ListQueue` (already exists) as the queue type. `MusicService` receives a fully-built queue and is not modified.

**Major components:**
1. `BridgeScreen` (Compose) — two artist inputs, loading states, linear path result view; observes `BridgeViewModel.uiState: StateFlow<BridgeUiState>`
2. `BridgeWebViewManager` — `AndroidView(WebView)` wrapper; loads EccoPath static assets via `WebViewAssetLoader`; registers `MeldBridgeInterface` before `loadUrl`
3. `MeldBridgeInterface` — `@JavascriptInterface`-annotated; receives `createPlaylist(json)` from EccoPath JS on background thread; dispatches to ViewModel via lambda callback
4. `BridgeViewModel` (HiltViewModel) — owns all bridge state via sealed `BridgeUiState` StateFlow; launches `BridgePlaylistBuilder` in `viewModelScope`; calls `playerConnection.playQueue()` when playlist is ready
5. `BridgePlaylistBuilder` — stateless; takes `List<BridgeArtist>`, calls `YouTube.search()` concurrently per artist via `async/awaitAll`, returns `List<MediaItem>` in genre-transition order
6. EccoPath bundled assets (`app/src/main/assets/eccopath/`) — built Next.js static export from submodule; served via `WebViewAssetLoader`

The data flow is: user inputs → `BridgeViewModel.startBridge()` → `evaluateJavascript("EccoPath.startBridge(...)")` → EccoPath beam search in WebView → `MeldBridgeInterface.createPlaylist(json)` → ViewModel parses path + launches playlist builder → `playerConnection.playQueue()` → existing ExoPlayer pipeline. All new code follows the exact patterns established by `SpotifyLoginScreen.kt` (AndroidView WebView pattern) and existing ViewModels (StateFlow + HiltViewModel).

### Critical Pitfalls

1. **IndexedDB fails silently under `file://` origin** — use `WebViewAssetLoader` from the very first WebView setup task; never use `file:///android_asset/` URLs; verify with Chrome Remote Debugging before proceeding with any bridge testing
2. **JS bridge callback fires on a background thread** — every `@JavascriptInterface` method must dispatch to main thread immediately (`Handler(Looper.getMainLooper()).post { }` or `withContext(Dispatchers.Main)`); bridge methods must stay thin; no direct ExoPlayer or StateFlow mutation in the bridge method body
3. **Next.js static export generates absolute `/_next/` paths and `crossorigin` attributes** — spike `next build` output inside a minimal WebView harness before integrating into EccoMeld; configure `WebViewAssetLoader` to intercept all `/_next/` prefixes; strip or patch `crossorigin` attributes post-build
4. **Last.fm rate limit explosion from concurrent bridge runs** — disable bridge UI during active computation; expose `cancelBridge()` JS interface; always call cancel before starting a new bridge; add exponential backoff on Last.fm error 29
5. **YT Music fuzzy match returns wrong artist's track** — require a separate high-confidence artist-name fuzzy score in addition to title score; use duration delta as tiebreaker; prefer silent skip over low-confidence match for niche artists (< 5K listeners); calibrate thresholds empirically during development
6. **Spotify TOTP Gist is a single point of failure** — mirror Gist content before shipping any feature that depends on Spotify liked artists; add graceful fallback messaging; ensure manual artist entry always works without Spotify

---

## Implications for Roadmap

Based on the dependency graph in FEATURES.md and the build order in ARCHITECTURE.md, the natural phase structure follows the technical dependency chain. The WebView integration must precede the bridge, which must precede the playlist builder, which must precede auto-play. Each phase produces a verifiable artifact.

### Phase 1: Foundation — WebView Shell + Asset Bundling

**Rationale:** Everything downstream depends on EccoPath loading correctly in a WebView. The IndexedDB pitfall (Pitfall 1), the Next.js path pitfall (Pitfall 3), and the cold start latency pitfall (Pitfall 9) must all be resolved before any bridge logic is attempted. This phase has no UI value to ship — it is infrastructure — but a bad foundation here breaks all subsequent phases.
**Delivers:** EccoPath renders correctly in a WebView served from `https://appassets.androidplatform.net/`; IndexedDB cache verifiably persists across WebView reloads; Next.js chunks load without 404s
**Addresses:** Setup for all bridge features
**Avoids:** Pitfalls 1, 3, 9, 13 — IndexedDB, absolute paths, cold start, image optimization

### Phase 2: Navigation + ViewModel Skeleton

**Rationale:** Establishing the Bridge tab and sealed state model before any feature work means every subsequent phase has a clear integration target. State-first development (define `BridgeUiState` fully before wiring features) prevents retrofitting state mid-implementation.
**Delivers:** Bridge tab in bottom navigation; `BridgeScreen` composable observing `BridgeUiState`; all state transitions defined (Idle, SearchingBridge, PathFound, PlaylistReady, Error) even if stubbed
**Uses:** Kotlin StateFlow, HiltViewModel, `collectAsStateWithLifecycle()`
**Avoids:** Architectural debt from ad-hoc state management

### Phase 3: JS Bridge — EccoPath to Kotlin Message Channel

**Rationale:** This is the highest-risk, most novel technical element. Isolating it to its own phase with explicit thread-safety verification (Pitfall 2) before adding playlist logic prevents debugging two complex systems simultaneously.
**Delivers:** `MeldBridgeInterface` registered via `addJavascriptInterface`; `createPlaylist(json)` fires and logs correctly to Timber; thread dispatch to main verified; `evaluateJavascript("EccoPath.startBridge(...)")` triggers beam search end-to-end
**Implements:** `BridgeWebViewManager`, `MeldBridgeInterface`
**Avoids:** Pitfalls 2, 12 — background thread violation, null `evaluateJavascript` callbacks

### Phase 4: Artist Search Inputs + Bridge Trigger

**Rationale:** Two artist search inputs with Last.fm autocomplete are the entry point for all bridge use cases. This phase builds the user-facing trigger while the backend (playlist building) is still to come — which is fine because the bridge output can be observed in logs.
**Delivers:** Two artist text inputs with Last.fm autocomplete (niche artist coverage); "Find Bridge" button; loading states with step progress copy ("Crawling Last.fm graph…", "Found N of 6 hops…"); error state when no path found
**Addresses:** Table stakes — artist search, loading feedback, error state
**Avoids:** Pitfall 4 — UI is disabled during active bridge computation, preventing overlapping runs

### Phase 5: Playlist Builder + Auto-Play

**Rationale:** With a verified bridge path flowing from JS to Kotlin (Phase 3), this phase adds the translation layer from bridge artist list to `List<MediaItem>`. Parallel YT Music searches (`async/awaitAll`) are required here to meet the ~2-3 second target vs. ~10 seconds sequential. Silent-skip on match failures must be baked in, not retrofitted.
**Delivers:** `BridgePlaylistBuilder` — given a bridge path, produces a `List<MediaItem>`; fuzzy artist+track matching with dual score (artist name + track title) and duration tiebreaker; silent skip on failures; `playerConnection.playQueue()` call triggers auto-play
**Implements:** `BridgePlaylistBuilder`, fuzzy matching with `JaroWinklerSimilarity` from `commons-text`
**Avoids:** Pitfalls 5, 7 — wrong-artist fuzzy match, ExoPlayer thread safety

### Phase 6: Linear Path Result View

**Rationale:** The path view is the product's face — the UI that makes the niche-discovery value proposition visible. It depends on real path data (Phase 4) and can be designed and polished once end-to-end playback is verified. Shipping the path view after the playback loop is stable prevents rework caused by changing the data model.
**Delivers:** Vertical journey layout with seed artist at top, target at bottom, bridge artists between; genre tags + Last.fm listener counts on each node; "NEW" or known/unknown badge (if Spotify liked artists available); currently-playing artist highlighted in path view
**Addresses:** Table stakes (path display, listener counts, genre tags); differentiators (known/unknown distinction, playing-artist highlight)

### Phase 7: Random Bridge + Spotify Integration

**Rationale:** "Random Bridge" is the highest-value differentiator but depends on Spotify liked artists (which has a pre-existing Gist fragility) and Tag Jaccard computation. Shipping it after the core bridge loop is stable (Phases 3-6) means a Spotify failure doesn't block the core feature. The Gist mirror must be in place before this phase ships.
**Delivers:** Random Bridge button; Tag Jaccard distance computation from Spotify liked artists; genre-opposite pair selection; graceful fallback if Spotify auth unavailable; Gist content mirrored to a controlled location
**Addresses:** Table stakes (Random Bridge); differentiators (seed suggestions from library)
**Avoids:** Pitfall 8 — Spotify TOTP Gist single point of failure

### Phase Ordering Rationale

- **Phases 1-3 before any user-facing features:** The WebView asset serving and JS bridge are load-bearing infrastructure. Getting them wrong means rebuilding on a broken foundation.
- **Search inputs (Phase 4) before playlist (Phase 5):** The bridge JSON output format from EccoPath must be empirically verified against real data before the playlist builder can be designed against it. Phase 4 produces the real JSON; Phase 5 consumes it.
- **Path view (Phase 6) after playback loop (Phase 5):** The path view needs real data to design against. Ship working playback first, then make it look right.
- **Random Bridge (Phase 7) last:** Depends on Spotify auth stability and adds user-invisible infrastructure (Jaccard computation). If it slips, the core bridge feature ships without it.
- **Pitfall mitigations are phase-gating:** Pitfall 1 (IndexedDB) must be resolved before Phase 3. Pitfall 4 (rate limit) must be resolved before Phase 7. Pitfall 8 (Gist) must be resolved before Phase 7. These are not optional polish items.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 1 (WebView asset bundling):** The `assetPrefix: "" + output: "export"` combination for WebView specifically is a community pattern without an official Android guide. A standalone spike (build EccoPath static export, load in minimal WebView, verify IndexedDB and chunk loading) should precede implementation estimates.
- **Phase 5 (Fuzzy matching thresholds):** The 0.4/0.4/0.2 weighting formula for artist/title/duration scoring is a recommendation, not a verified standard. Empirical calibration against real YT Music search results for known bridge artists is required before settling on thresholds.
- **Phase 7 (Spotify TOTP stability):** The TOTP Gist fragility is pre-existing. Before Phase 7 can be planned, the mitigation strategy (mirror location, fallback behavior) needs a concrete decision.

Phases with standard patterns (skip research-phase):
- **Phase 2 (ViewModel skeleton):** Sealed class StateFlow + HiltViewModel is the established pattern for every existing ViewModel in the codebase. No novel decisions required.
- **Phase 6 (Path view UI):** Vertical list with Compose is straightforward. The design decision (what to show per node) is already resolved by FEATURES.md.
- **Phase 3 (JS bridge):** `addJavascriptInterface` is well-documented. Thread dispatch pattern is established. Only implementation discipline is required, not research.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All new dependencies verified against official docs; Accompanist deprecation confirmed; Commons Text license compatibility confirmed; WebViewAssetLoader IndexedDB behavior confirmed via Chromium docs |
| Features | HIGH (table stakes) / MEDIUM (differentiators) | Table stakes verified against 6 existing competing products. Differentiators are novel claims based on gap analysis — no empirical user testing to confirm they land as expected |
| Architecture | HIGH | Verified against existing codebase (`SpotifyLoginScreen.kt`, `Queue.kt`, `PlayerConnection`); all patterns are established; no novel architectural decisions |
| Pitfalls | MEDIUM-HIGH | Critical pitfalls (1, 2, 3) verified against Chromium source docs and official Android docs. Rate limit behavior (Pitfall 4) confirmed via real-world GitHub issues. Fuzzy match thresholds (Pitfall 5) require empirical validation |

**Overall confidence:** HIGH

### Gaps to Address

- **EccoPath JSON output schema:** The exact JSON structure emitted by `EccoPath.startBridge()` → `MeldBridge.createPlaylist(json)` must be confirmed by reading `bridgeCrawl.ts` before `BridgePlaylistBuilder` can be implemented. This is a P0 implementation prerequisite for Phase 5.
- **Fuzzy match thresholds:** The 0.4/0.4/0.2 weighting for artist/title/duration is an educated starting point. Real calibration against YT Music results for niche artists (< 10K Last.fm listeners) is required during Phase 5 development.
- **Next.js bundle size for Android:** EccoPath's full Next.js bundle includes graph visualization components (`hyperbolicLayout.ts`, Poincaré disk rendering) that are explicitly not needed for the WebView-only bridge use case. Bundle analysis and tree-shaking/exclusion of unused UI code should happen in Phase 1 to avoid serving a larger bundle than necessary.
- **WebView cold start on target device profile:** The 300-800ms cold start estimate is for mid-range devices. The actual device profile for EccoMeld's target users (enthusiast Android users, likely higher-end devices) may change the priority of WebView pre-initialization.
- **`addJavascriptInterface` vs `addWebMessageListener` decision:** STACK.md recommends `addWebMessageListener` (modern, origin-restricted); ARCHITECTURE.md recommends `addJavascriptInterface` (simpler, no API level gating for first-party code). Both are safe for bundled first-party assets. The implementation phase should pick one explicitly and document the rationale. Recommendation: use `addJavascriptInterface` for MVP simplicity; migrate to `addWebMessageListener` if bridge is ever exposed to non-bundled content.

---

## Sources

### Primary (HIGH confidence)
- Official Android docs — JS bridge (`addWebMessageListener` vs `addJavascriptInterface`): https://developer.android.com/develop/ui/views/layout/webapps/native-api-access-jsbridge
- Official Android docs — WebViewAssetLoader: https://developer.android.com/reference/androidx/webkit/WebViewAssetLoader
- AndroidX WebKit 1.15.0 release notes: https://developer.android.com/jetpack/androidx/releases/webkit
- Chromium WebView docs — CORS and storage: https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/cors-and-webview-api.md
- Chromium WebView docs — threading: https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/threading.md
- Next.js static export docs: https://nextjs.org/docs/pages/guides/static-exports
- Apache Commons Text: https://commons.apache.org/proper/commons-text/
- EccoPath source (`lib/rateLimiter.ts`, `lib/bridgeCrawl.ts`, `lib/lastfm.ts`) — direct code inspection
- EccoMeld `CONCERNS.md` — pre-existing fragilities documented

### Secondary (MEDIUM confidence)
- Boil the Frog (Music Machinery): https://musicmachinery.com/2013/01/02/boil-the-frog-2/
- WhoSampled Six Degrees: https://www.whosampled.com/six-degrees/
- MusicLynx ACM paper: https://dl.acm.org/doi/fullHtml/10.1145/3184558.3186970
- Next.js issue #61210 — `crossorigin=""` static export bug: https://github.com/vercel/next.js/issues/61210
- Navidrome issue #2421 — Last.fm error 29 reproduction: https://github.com/navidrome/navidrome/issues/2421
- Google ExoPlayer blog — dynamic playlists: https://medium.com/google-exoplayer/dynamic-playlists-with-exoplayer-6f53e54a56c0
- Droidcon 2024 — AndroidView lifecycle in Compose: https://www.droidcon.com/2024/09/17/managing-lifecycle-and-performance-challenges-when-combining-traditional-views-with-jetpack-compose/

### Tertiary (LOW confidence)
- Fuzzy match weighting formula (0.4/0.4/0.2) — derived recommendation, not a verified standard; requires empirical tuning

---
*Research completed: 2026-04-03*
*Ready for roadmap: yes*
