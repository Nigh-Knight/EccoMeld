# Technology Stack — EccoMeld Bridge Discovery Milestone

**Project:** EccoMeld (Bridge Discovery features atop Meld/InnerTune)
**Researched:** 2026-04-03
**Scope:** Additive stack for WebView ↔ Kotlin JS bridge, bundled web assets, fuzzy track matching, Tag Jaccard similarity

---

## Existing Stack (Do Not Change)

The following already exists and must not be replaced or duplicated:

| Technology | Version | Role |
|---|---|---|
| Kotlin | 2.3.10 | All app code |
| Jetpack Compose | 1.10.2 | UI framework |
| Material 3 | 1.5.0-alpha09 | Design system |
| Media3/ExoPlayer | 1.7.1 | Audio playback |
| Room | 2.8.4 | SQLite persistence |
| Hilt | 2.59.1 | Dependency injection |
| Ktor | 3.4.0 | HTTP client |
| kotlinx.serialization | (current) | JSON parsing |
| Apache Commons Lang3 | 3.20.0 | String utilities (already present) |

The new stack additions below are purely additive.

---

## New Stack Additions

### 1. AndroidX WebKit — JS Bridge Foundation

**Add:** `androidx.webkit:webkit:1.15.0`

```toml
# gradle/libs.versions.toml
[versions]
webkit = "1.15.0"

[libraries]
androidx-webkit = { group = "androidx.webkit", name = "webkit", version.ref = "webkit" }
```

```kotlin
// app/build.gradle.kts
implementation(libs.androidx.webkit)
```

**Why webkit over raw `android.webkit.WebView`:**
- `WebViewCompat.addWebMessageListener()` is the modern replacement for `addJavascriptInterface()`. It enforces origin-based access control — only your allowed origin can call Kotlin from JS.
- `WebViewAssetLoader` with `AssetsPathHandler` gives the bundled web app a real HTTPS-origin (`https://appassets.androidplatform.net/`) rather than a `file://` URL. This is required for IndexedDB to work in the WebView — IndexedDB is blocked on `file://` origins in Chromium-based WebViews. EccoPath's Last.fm client uses IndexedDB as its persistent L2 cache; if the origin is `file://`, that cache is silently broken.
- `WebViewCompat.addDocumentStartJavaScript()` injects the JS bridge object before any page script runs, eliminating the race condition where the page calls `window.MeldBridge` before Android has registered it.
- Minimum WebView version requirement for `addWebMessageListener`: WebView 82 (released 2020). Android 8.0 (API 26, minimum SDK for this project) ships with a WebView that is system-updatable; in practice all API 26+ devices in 2025 run WebView 82+. Guard with `WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)`.

**Confidence:** HIGH — verified against official Android docs and AndroidX WebKit 1.15.0 release notes.

---

### 2. Bridge JS Interface Pattern

**No additional library required.** Use `WebViewCompat` APIs from the `webkit` dependency above.

**Recommended pattern:**

```kotlin
// BridgeWebView.kt — set up once when the Composable is first created
val assetLoader = WebViewAssetLoader.Builder()
    .addPathHandler("/assets/", AssetsPathHandler(context))
    .build()

webView.webViewClient = object : WebViewClientCompat() {
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
}

// Inject MeldBridge object before any page script runs
if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
    WebViewCompat.addDocumentStartJavaScript(
        webView,
        // Expose a postMessage channel as window.MeldBridge
        "window.MeldBridge = window.MeldBridgeInternal;",
        setOf("https://appassets.androidplatform.net")
    )
}

// Register message listener — receives JSON from JS
if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
    WebViewCompat.addWebMessageListener(
        webView,
        "MeldBridgeInternal",                          // JS sees this as window.MeldBridgeInternal
        setOf("https://appassets.androidplatform.net"),
        object : WebViewCompat.WebMessageListener {
            override fun onPostMessage(
                view: WebView,
                message: WebMessageCompat,
                sourceOrigin: Uri,
                isMainFrame: Boolean,
                replyProxy: JavaScriptReplyProxy
            ) {
                // Parse message.data as JSON, dispatch to ViewModel
            }
        }
    )
}

webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
```

**JavaScript side (EccoPath):**
```javascript
// MeldBridge.createPlaylist(json) — call from EccoPath TypeScript
window.MeldBridgeInternal.postMessage(JSON.stringify({
    action: "createPlaylist",
    payload: bridgeResult
}))
```

**Do NOT use `addJavascriptInterface()`.** It exposes the bridge object to every frame including iframes, has no origin restriction, and requires `@JavascriptInterface` annotations plus ProGuard keep-rules that are easy to get wrong.

**Confidence:** HIGH — official Android JS bridge documentation, verified against androidx.webkit 1.15.0 API reference.

---

### 3. Jetpack Compose WebView Wrapper

**Options evaluated:**

| Option | Status | Verdict |
|---|---|---|
| Raw `AndroidView { WebView(...) }` | Always available | Use this |
| Accompanist Web | Deprecated, unmaintained | Do NOT use |
| `io.github.kevinnzou:compose-webview:0.33.6` | Last release March 2024, 196 stars | Viable but unnecessary |

**Recommendation: Use raw `AndroidView`.**

The compose-webview library adds a thin lifecycle-state wrapper but does not expose `WebViewAssetLoader` or `addWebMessageListener` configuration — you would need to reach through to the underlying `WebView` anyway. Writing a thin `BridgeWebView` composable with `AndroidView` directly is ~50 lines and keeps the bridge wiring explicit and testable. The Accompanist library is deprecated and must not be used.

```kotlin
@Composable
fun BridgeWebView(
    onBridgeMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    AndroidView(
        factory = { ctx -> setupBridgeWebView(ctx, onBridgeMessage) },
        modifier = modifier
    )
}
```

**Confidence:** MEDIUM — compose-webview version checked; Accompanist deprecation confirmed in official Accompanist docs.

---

### 4. Bundled EccoPath Web Assets

**Approach: Git submodule + Gradle task copies `out/` into `app/src/main/assets/eccopath/`**

EccoPath is a Next.js 16.2.2 app. It already has `basePath: "/path"` and `assetPrefix: "/path"` in `next.config.ts`. These must be overridden for Android bundling. The Android build needs:

```typescript
// next.config.ts — for Android build variant
const nextConfig: NextConfig = {
    output: "export",      // emit static files to out/
    basePath: "",          // no subdirectory prefix
    assetPrefix: "",       // relative asset paths
    trailingSlash: true,   // avoids 404s on direct asset paths
}
```

Build flow:
```bash
# In EccoPath submodule directory
next build           # generates out/ with index.html, _next/static/, public/

# Copy out/ to Android assets
cp -r out/* ../EccoMeld/app/src/main/assets/eccopath/
```

The Android `WebViewAssetLoader` with `AssetsPathHandler("/assets/")` then serves:
- `https://appassets.androidplatform.net/assets/eccopath/index.html`
- `https://appassets.androidplatform.net/assets/eccopath/_next/static/...`

**Critical constraint — IndexedDB origin persistence:** EccoPath's `lib/lastfm.ts` uses IndexedDB as its L2 cache. IndexedDB data is keyed by origin. The origin `https://appassets.androidplatform.net` is fixed and consistent across app reinstalls on the same device, so the cache survives app updates as long as the asset loader domain does not change. Do not alter the `WebViewAssetLoader` domain.

**Critical constraint — Last.fm API key:** EccoPath's `lib/lastfm.ts` hardcodes `const API_KEY = 'c02db6443f45b41cd57d8166c9f042c9'`. This key is already public in EccoPath. No changes needed — it will work from the bundled WebView. The API calls go to `https://ws.audioscrobbler.com/2.0/` directly from the WebView, not through Kotlin.

**What NOT to do:**
- Do NOT use `file:///android_asset/` URLs. IndexedDB is blocked on `file://` origins in Android WebView (Chromium security policy).
- Do NOT use `loadDataWithBaseURL()` — this works for single HTML strings, not multi-file bundles with `_next/static/` chunks.
- Do NOT set `webView.settings.allowFileAccessFromFileURLs = true` or `allowUniversalAccessFromFileURLs = true` — this is a security regression and still does not fix IndexedDB on `file://`.

**Automate in Gradle:** Add a Gradle task in `app/build.gradle.kts` that depends on the EccoPath `npm run build` before `assembleRelease`, and copies `eccopath/out/` to `app/src/main/assets/eccopath/`. The `out/` directory should be gitignored; assets are built artifacts.

**Confidence:** HIGH for WebViewAssetLoader approach; MEDIUM for Next.js config specifics (verified output structure from Next.js docs and `next.config.ts` in EccoPath, but the `assetPrefix: ""` + `output: "export"` combination for WebView specifically is a known community pattern without an official Android guide).

---

### 5. Fuzzy String Matching — YT Music Track Matching

**Recommendation: Apache Commons Text `LevenshteinDistance` (already a transitive dependency)**

The project already ships `org.apache.commons:commons-lang3:3.20.0`. Apache Commons Text (the sibling library with fuzzy matching) adds `LevenshteinDistance`, `JaroWinklerSimilarity`, and `FuzzyScore`. For YT Music track matching the algorithm is:

1. Normalize both strings: lowercase, strip punctuation, collapse whitespace
2. Compute Jaro-Winkler similarity on artist name (handles spelling variants better than Levenshtein for short strings)
3. Compute token-sort ratio on track title (handles word-order differences: "Title (feat. X)" vs "X - Title")
4. Combine: `score = 0.4 * artistSim + 0.4 * titleSim + 0.2 * durationSim`
5. Accept match if combined score > 0.85; skip silently if no match above threshold

**Two options for the library:**

**Option A: Apache Commons Text (recommended)**

```toml
# gradle/libs.versions.toml
[versions]
commons-text = "1.13.0"

[libraries]
apache-commons-text = { group = "org.apache.commons", name = "commons-text", version.ref = "commons-text" }
```

- Ships `LevenshteinDistance`, `JaroWinklerSimilarity`, `FuzzyScore`, `CosineSimilarity`
- Apache 2.0 license — compatible with GPL
- Well-maintained, Apache Foundation, no transitive dependencies
- Familiar to the codebase since Commons Lang3 is already used

**Option B: kt-fuzzy (pure Kotlin alternative)**

```toml
[versions]
kt-fuzzy = "0.1.0"

[libraries]
kt-fuzzy = { group = "ca.solo-studios", name = "kt-fuzzy", version.ref = "kt-fuzzy" }
```

- Kotlin Multiplatform, zero dependencies
- Includes Levenshtein, Jaro-Winkler, LCS, cosine similarity, and others
- MIT license
- Latest release: October 6, 2025
- Smaller community, less battle-tested than Apache Commons

**Decision: Use Apache Commons Text.** The codebase already uses Apache Commons Lang3; adding Commons Text is consistent with the existing dependency family, has a 15-year track record, and Apache 2.0 is unambiguously compatible with the project's GPL license. kt-fuzzy is a reasonable alternative only if you want to avoid Java library dependencies.

**Do NOT add:**
- `com.willowtreeapps:fuzzywuzzy-kotlin:0.1.1` — last meaningful release was 0.1.1 circa 2021, minimal maintenance since
- `github.com/jens-muenker/fuzzywuzzy-kotlin` — Android-specific wrapper, but the underlying algorithm (just Levenshtein + ratio) is simpler than what Commons Text provides

**Confidence:** HIGH for Apache Commons Text availability and license; MEDIUM for the specific scoring formula (the 0.4/0.4/0.2 weighting is a recommendation, not a verified industry standard — it should be tuned empirically against real YT Music search results).

---

### 6. Tag Jaccard Similarity — Random Bridge Pair Selection

**No new library required.** Implement directly in Kotlin.

EccoPath already implements `tagJaccard()` in TypeScript (`lib/bridgeCrawl.ts`, line 36-46). The algorithm is 10 lines:

```kotlin
// TagJaccard.kt — pure Kotlin, no dependencies
fun tagJaccard(tagsA: Set<String>, tagsB: Set<String>): Double {
    if (tagsA.isEmpty() || tagsB.isEmpty()) return 0.0
    val normalizedA = tagsA.map { it.lowercase() }.toSet()
    val normalizedB = tagsB.map { it.lowercase() }.toSet()
    val intersection = normalizedA.intersect(normalizedB).size
    val union = normalizedA.union(normalizedB).size
    return if (union > 0) intersection.toDouble() / union else 0.0
}

// Jaccard distance (for "most different" pair selection)
fun tagJaccardDistance(tagsA: Set<String>, tagsB: Set<String>): Double =
    1.0 - tagJaccard(tagsA, tagsB)
```

For the Random Bridge button: fetch tag sets for all liked artists from the existing `lastfm` module, compute pairwise Jaccard distances, pick the pair with maximum distance. For N liked artists, this is O(N²) comparisons — fine for the typical Spotify library size (tens to low hundreds of liked artists).

The artist tag data needed for this computation is already available from Last.fm's `artist.getInfo` endpoint, which the existing `lastfm` module calls. No new API calls required.

**Confidence:** HIGH — algorithm is trivial, verified against EccoPath's own implementation.

---

## New Gradle Module

**Recommendation: Create `eccopath` module as an Android Library**

Instead of loading the bridge WebView directly in the `app` module, encapsulate it:

```
eccopath/                   ← new Android Library module
├── BridgeWebView.kt        ← Compose composable wrapping WebView
├── BridgeViewModel.kt      ← state management for bridge computation
├── BridgeMessage.kt        ← sealed class for JS → Kotlin messages
└── TrackMatcher.kt         ← fuzzy match artist+track to YT Music IDs
```

This follows the existing module pattern (innertube, spotify, lastfm, etc.) and keeps bridge logic isolated from the app module's 36-ViewModel monolith.

**No new module is strictly required for MVP** — the code can live in `app` — but the module boundary is the right long-term structure, matching the project's existing architecture.

---

## ProGuard Rules

The following rules are needed in `app/proguard-rules.pro`:

```proguard
# WebView JS bridge via addWebMessageListener
-keep class androidx.webkit.** { *; }
-keepclassmembers class androidx.webkit.** { *; }

# Keep any @JavascriptInterface methods if addJavascriptInterface is used as fallback
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Apache Commons Text — used for fuzzy matching
-keep class org.apache.commons.text.** { *; }
```

**Confidence:** HIGH — ProGuard rules for WebView bridge from official Android docs.

---

## Summary of New Dependencies

| Dependency | Version | Purpose | License |
|---|---|---|---|
| `androidx.webkit:webkit` | 1.15.0 | WebViewAssetLoader, addWebMessageListener | Apache 2.0 |
| `org.apache.commons:commons-text` | 1.13.0 | JaroWinklerSimilarity, LevenshteinDistance | Apache 2.0 |

**Not added:**
- No Compose WebView wrapper library — raw `AndroidView` is sufficient
- No Jaccard library — 10-line pure Kotlin implementation
- No separate JS bridge library — AndroidX WebKit covers it

---

## What NOT to Add

| Avoid | Why |
|---|---|
| `addJavascriptInterface()` | No origin restriction; any iframe can call Kotlin native code |
| `file:///android_asset/` URL scheme | Breaks IndexedDB (Chromium blocks storage on opaque `file://` origins) |
| `allowUniversalAccessFromFileURLs = true` | Security regression, also does not fix IndexedDB |
| Accompanist `WebView` | Deprecated, officially unmaintained since 2023 |
| `fuzzywuzzy-kotlin` (willowtreeapps or jens-muenker) | Stale; Apache Commons Text is better maintained and already in the dependency family |
| Capacitor or Cordova | Full hybrid frameworks — overkill, heavy, conflict with Compose architecture |
| A custom WebSocket bridge | Not needed; `addWebMessageListener` provides reliable two-way messaging |

---

## Installation Summary

```bash
# gradle/libs.versions.toml additions
webkit = "1.15.0"
commons-text = "1.13.0"

# app/build.gradle.kts additions
implementation(libs.androidx.webkit)
implementation(libs.apache.commons.text)
```

EccoPath build pipeline (separate from Gradle — run before Android build):
```bash
cd eccopath/          # git submodule
npm ci
NEXT_OUTPUT=export npm run build    # outputs to eccopath/out/
```

---

## Sources

- AndroidX WebKit 1.15.0 release notes: https://developer.android.com/jetpack/androidx/releases/webkit
- Android JS bridge official docs: https://developer.android.com/develop/ui/views/layout/webapps/native-api-access-jsbridge
- WebViewAssetLoader official docs: https://developer.android.com/develop/ui/views/layout/webapps/load-local-content
- WebViewAssetLoader API reference: https://developer.android.com/reference/androidx/webkit/WebViewAssetLoader
- Next.js static export: https://nextjs.org/docs/pages/guides/static-exports
- Apache Commons Text: https://commons.apache.org/proper/commons-text/
- kt-fuzzy: https://github.com/solo-studios/kt-fuzzy
- Can I WebView IndexedDB: https://caniwebview.com/features/mdn-indexeddb/
- compose-webview (KevinnZou): https://github.com/KevinnZou/compose-webview
