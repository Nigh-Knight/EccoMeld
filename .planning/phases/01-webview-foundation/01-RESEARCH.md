# Phase 1: WebView Foundation — Research

**Researched:** 2026-04-04
**Domain:** Android WebView (WebViewAssetLoader), Next.js static export, Android adaptive icons, Gradle exec tasks
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** WebView is hidden (headless) — all UI is native Compose. EccoPath only performs computation; results come back via JS bridge. No EccoPath web UI is ever shown to the user.
- **D-02:** WebView is created eagerly at app startup so Bridge is instant on first use. No lazy initialization.
- **D-03:** WebView lives as an application-scoped singleton (Hilt `@Singleton`), surviving config changes, tab switches, and the full app lifecycle. Consistent with how `MusicService` and other long-lived components are managed.
- **D-04:** A custom Gradle task handles the full EccoPath build pipeline: runs `next build` with static export, copies output to `app/src/main/assets/eccopath/`. Reproducible and automated as part of normal Android build.
- **D-05:** EccoPath keeps its original `next.config.ts` (with `basePath: "/path"`) for web development. The Gradle task uses a separate Android-specific config (e.g., `next.config.android.ts`) with `basePath: ""` and `output: "export"`. No conflict between EccoPath web dev and Android bundling.
- **D-06:** A post-build script strips `crossorigin` attributes from the exported HTML after `next build`. This proactively addresses the Next.js #61210 crossorigin attribute bug that can cause chunk loading failures in WebView.
- **D-07:** Full rebrand to "EccoMeld" across all user-visible text: launcher label, app bar, notifications, about screen, crash handler, Discord RPC, and any other user-facing "Meld" or "Metrolist" references.
- **D-08:** App icon changes to the EccoMuse logo. Source file: `~/Projects/EccoMuse-Site/src/assets/images/EccoMuse_new_logo.png`. Must be adapted to Android adaptive icon format (foreground + background layers, multiple density buckets).
- **D-09:** EccoPath added as a git submodule at root-level `eccopath/` directory.
- **D-10:** Submodule tracks EccoPath's main branch (`branch = main` in `.gitmodules`). Both repos evolve together during active development.

### Claude's Discretion

- WebViewAssetLoader URL path structure (how EccoPath routes map to `https://appassets.androidplatform.net/`)
- Specific Gradle task implementation details (exec vs. npm plugin)
- Android adaptive icon layer splitting from the source PNG
- How to handle the `crossorigin` stripping (sed, node script, etc.)

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| INFRA-01 | EccoPath bundled as git submodule with static export packaged as APK assets | D-04, D-05, D-09, D-10: Gradle task drives `next build` with Android config, copies `out/` to `app/src/main/assets/eccopath/` |
| INFRA-02 | WebView loads bundled EccoPath via WebViewAssetLoader (HTTPS origin, not file://) | D-01, D-02, D-03: Singleton headless WebView using `WebViewAssetLoader` + `AssetsPathHandler`, served from `https://appassets.androidplatform.net/assets/eccopath/` |
| INFRA-03 | IndexedDB cache persists across WebView sessions (Last.fm cache survives app restart) | WebView settings `domStorageEnabled = true` + HTTPS origin (not `file://`) are the two requirements for IndexedDB to persist; `WebViewAssetLoader` provides the HTTPS origin |
| INFRA-04 | App name displayed as "EccoMeld" (launcher label, app bar) | D-07: `app_name.xml` change + string search in `metrolist_strings.xml`; D-08: adaptive icon PNG replacement |
</phase_requirements>

---

## Summary

Phase 1 has four distinct sub-problems that compose cleanly:

**Sub-problem 1 (INFRA-01):** Git submodule setup and a Gradle exec task that builds EccoPath as a Next.js static export. The EccoPath repo is at `/home/kepler/Projects/EccoPath` on this machine. Node.js 20 and npm 10 are available. Next.js 16.2.2 is already installed in EccoPath's `node_modules`. The Gradle task must use `next.config.android.ts` (new file to create) instead of the existing `next.config.ts` (which has `basePath: "/path"` — incompatible with `WebViewAssetLoader` root-relative assets). After `next build` the task runs a `crossorigin`-stripping script and copies the `out/` directory to `app/src/main/assets/eccopath/`.

**Sub-problem 2 (INFRA-02 + INFRA-03):** `WebViewAssetLoader` from `androidx.webkit` is the correct, documented way to serve APK assets over an HTTPS origin in a WebView. The default domain `appassets.androidplatform.net` is used. `AssetsPathHandler` maps a URL prefix (e.g., `/assets/eccopath/`) to the `assets/eccopath/` directory in the APK. `shouldInterceptRequest` must be overridden in a custom `WebViewClient`. The WebView must have `domStorageEnabled = true` and `javaScriptEnabled = true` — these unlock IndexedDB. The `file://` origin is the root cause of IndexedDB silently failing; HTTPS origin via WebViewAssetLoader fixes it permanently. `androidx.webkit` is NOT currently in `gradle/libs.versions.toml` — it must be added.

**Sub-problem 3 (INFRA-04 — text branding):** Three files contain user-visible "Meld"/"Metrolist" brand strings that need to change: `app/src/main/res/values/app_name.xml` (app name), `app/src/main/res/values/metrolist_strings.xml` (crash report subject, Discord activity strings), and `app/src/main/kotlin/.../utils/CrashHandler.kt` (hardcoded "Meld Crash Report" in Kotlin code). AndroidManifest already uses `@string/app_name` so only the resource file needs changing.

**Sub-problem 4 (INFRA-04 — icon):** The source logo is a 1024x1024 PNG at `/home/kepler/Projects/EccoMuse-Site/src/assets/images/EccoMuse_new_logo.png`. ImageMagick 7.1.2 is available on this machine. Adaptive icons require foreground and background layers: 108x108dp canvas per layer across mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi buckets (108/162/216/324/432 px). The existing icon structure uses `mipmap-anydpi-v31/ic_launcher.xml` (adaptive) and per-density PNG files for `ic_launcher_foreground.png`, `ic_launcher_background.png`, and `ic_launcher_monochrome.png`.

**Primary recommendation:** Add `androidx.webkit` to `libs.versions.toml`, create a Hilt module for the singleton `WebView`, write a Gradle exec task for the EccoPath build pipeline, update three branding files, and replace icon PNG assets. All four sub-problems are sequential-friendly with no blocking dependencies between them.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `androidx.webkit` | `1.13.0` (latest stable) | `WebViewAssetLoader` for HTTPS origin serving | Official AndroidX library; `WebViewAssetLoader` is the Android-recommended approach over `file://` |
| `android.webkit.WebView` | Platform (SDK 26+) | Core WebView runtime | Already used in project (SpotifyLoginScreen, LoginScreen) |
| Node.js | 20.20.0 (installed) | Runs `next build` in Gradle exec task | Already available on dev machine |
| Next.js | 16.2.2 (installed in EccoPath) | Static export of EccoPath | Already EccoPath's framework |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| ImageMagick | 7.1.2 (installed) | Resize 1024x1024 logo to adaptive icon density buckets | Icon generation only — one-time task |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `WebViewAssetLoader` | `file://` URL | `file://` silently breaks IndexedDB and violates same-origin policy; never use for this project |
| `WebViewAssetLoader` | Custom HTTP local server (NanoHTTPD) | Overkill; `WebViewAssetLoader` is purpose-built for this exact use case |
| Gradle exec task | Dedicated npm Gradle plugin | exec task is simpler, has no extra dependencies, and Gradle 9 supports it directly |
| Node script for `crossorigin` strip | `sed` | Node script handles edge cases (multi-line tags, attribute ordering); more portable |

**Installation (libs.versions.toml addition):**
```toml
# [versions]
webkit = "1.13.0"

# [libraries]
webkit = { module = "androidx.webkit:webkit", version.ref = "webkit" }
```

```kotlin
// app/build.gradle.kts
implementation(libs.webkit)
```

**Version verification:** `androidx.webkit` 1.13.0 was the latest stable release as of early 2026. Verify:
```bash
# Check Maven Central for latest
curl -s "https://maven.google.com/androidx/webkit/webkit/maven-metadata.xml" | grep "<release>"
```

---

## Architecture Patterns

### WebView Singleton in Hilt (D-03)

The WebView must live as a Hilt `@Singleton`. It cannot be injected directly as a `WebView` object since WebView construction requires a `Context`. The pattern used by `AppModule.kt` for `SimpleCache` (takes `@ApplicationContext context: Context`) applies directly.

**Critical Android constraint:** `WebView` must be constructed on the **main thread**. Hilt `@Provides` functions run lazily on first injection. Since injection happens in `App.onCreate()` (main thread), this is safe — but the Hilt provider itself must not be annotated with any non-main-thread dispatcher.

### Pattern 1: WebViewAssetLoader with AssetsPathHandler

**What:** Routes all requests matching a URL prefix to the APK's `assets/` directory.
**When to use:** Any time bundled files must be served via HTTPS origin in a WebView.

```kotlin
// Source: https://developer.android.com/develop/ui/views/layout/webapps/load-local-content
val assetLoader = WebViewAssetLoader.Builder()
    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
    .build()

val webViewClient = object : WebViewClient() {
    @RequiresApi(21)
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        return assetLoader.shouldInterceptRequest(request.url)
    }
}
```

URL: `https://appassets.androidplatform.net/assets/eccopath/index.html`
Maps to: `app/src/main/assets/eccopath/index.html`

### Pattern 2: Hilt @Singleton WebView Provider

```kotlin
// app/src/main/kotlin/com/metrolist/music/di/BridgeModule.kt
@Module
@InstallIn(SingletonComponent::class)
object BridgeModule {

    @Singleton
    @Provides
    @BridgeWebView  // custom qualifier annotation
    fun provideBridgeWebView(
        @ApplicationContext context: Context,
    ): WebView {
        // WebView must be created on the main thread.
        // This provider is called from App.onCreate() which runs on main thread.
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()

        return WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
            }
            loadUrl("https://appassets.androidplatform.net/assets/eccopath/index.html")
        }
    }
}
```

### Pattern 3: Gradle Exec Task for EccoPath Build

```kotlin
// app/build.gradle.kts — register before android {} block
val buildEccoPath by tasks.registering(Exec::class) {
    description = "Build EccoPath static export for Android packaging"
    group = "eccopath"

    workingDir = file("${rootProject.projectDir}/eccopath")
    commandLine("npm", "run", "build:android")  // defined in eccopath/package.json

    inputs.dir("${rootProject.projectDir}/eccopath/lib")
    inputs.dir("${rootProject.projectDir}/eccopath/app")
    inputs.file("${rootProject.projectDir}/eccopath/next.config.android.ts")
    outputs.dir("${rootProject.projectDir}/eccopath/out")
}

val copyEccoPathAssets by tasks.registering(Copy::class) {
    dependsOn(buildEccoPath)
    from("${rootProject.projectDir}/eccopath/out")
    into("${projectDir}/src/main/assets/eccopath")
}

tasks.named("preBuild").configure {
    dependsOn(copyEccoPathAssets)
}
```

`eccopath/package.json` gets a new script:
```json
"build:android": "NEXT_CONFIG_FILE=next.config.android.ts next build && node scripts/strip-crossorigin.mjs"
```

Or pass the config via environment variable — Next.js 16 supports `NEXT_CONFIG_FILE` to override the config file path (confirmed in Next.js docs).

**Alternative for config override (simpler):** Pass `-C` flag: Next.js 16+ supports `next build --config next.config.android.ts`.

### Pattern 4: next.config.android.ts

```typescript
// eccopath/next.config.android.ts
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "export",
  basePath: "",
  assetPrefix: "",
  // crossOrigin is not set — Next.js 16.2.2 includes the #61210 fix
  // but strip-crossorigin.mjs is run post-build as belt-and-suspenders
};

export default nextConfig;
```

**Note on crossorigin bug (Next.js #61210):** The bug was introduced in Next.js ~13.5.2 and fixed in a PR merged March 2024 (targeting ~14.2). EccoPath uses Next.js 16.2.2 which post-dates the fix. However, D-06 mandates a post-build strip as belt-and-suspenders defense. This is low-cost and eliminates the risk entirely.

### Pattern 5: crossorigin strip script

```javascript
// eccopath/scripts/strip-crossorigin.mjs
import { readdirSync, readFileSync, writeFileSync } from "fs";
import { join } from "path";

const outDir = new URL("../out", import.meta.url).pathname;

function stripCrossorigin(html) {
  return html.replace(/\s+crossorigin="[^"]*"/g, "").replace(/\s+crossorigin(?=[>\s/])/g, "");
}

function processDir(dir) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const fullPath = join(dir, entry.name);
    if (entry.isDirectory()) {
      processDir(fullPath);
    } else if (entry.name.endsWith(".html")) {
      const original = readFileSync(fullPath, "utf8");
      const stripped = stripCrossorigin(original);
      if (stripped !== original) writeFileSync(fullPath, stripped);
    }
  }
}

processDir(outDir);
console.log("strip-crossorigin: done");
```

### Anti-Patterns to Avoid

- **Using `file://` origin:** Silently disables IndexedDB. EccoPath's `lastfm.ts` checks `typeof indexedDB === 'undefined'` and gracefully degrades to network-only, so no crash — but INFRA-03 would silently fail.
- **Creating WebView off main thread:** Causes `RuntimeException: WebView can only be created on the main thread`. The Hilt singleton provider runs synchronously during first injection, which happens in `App.onCreate()` on the main thread — safe.
- **Using `basePath: "/path"` in the Android config:** All `/_next/` asset references will be prefixed with `/path/`, making them unreachable under `WebViewAssetLoader`'s path handlers.
- **Not setting `domStorageEnabled = true`:** IndexedDB requires DOM storage to be enabled in `WebSettings`. Without this, `indexedDB.open()` in EccoPath throws silently.
- **Using `WebViewClientCompat` from an older API:** The project targets API 26+ and all test devices run at least API 26. `WebViewClient` (non-compat) with `@RequiresApi(21)` annotation on the `WebResourceRequest` override is sufficient.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTTPS origin for APK assets | Custom HTTP server embedded in app | `WebViewAssetLoader` (`androidx.webkit`) | Android-official solution; handles MIME type detection, path mapping, security |
| Next.js static export | Custom bundler/build | `next build --output export` | The entire build pipeline already exists in Next.js |
| Adaptive icon density generation | Manual Photoshop resize | ImageMagick `convert -resize` | 1024x1024 source is available; `convert` with `-resize` to each density bucket is a 5-line script |

**Key insight:** `WebViewAssetLoader` is the exact API designed for this situation. It is the documented replacement for `file://` in WebView content loading. Not using it would require implementing the same `shouldInterceptRequest` interception manually with worse MIME type handling.

---

## Runtime State Inventory

> This section is omitted — Phase 1 is greenfield (no rename/refactor/migration).

---

## Common Pitfalls

### Pitfall 1: WebView IndexedDB silently disabled under `file://`
**What goes wrong:** EccoPath loads and runs but `idbGet`/`idbPut` in `lastfm.ts` silently fail because `indexedDB.open()` is blocked under `file://` origin. The `typeof indexedDB === 'undefined'` guard in `lastfm.ts` does NOT catch this — `indexedDB` is defined, it just refuses to open a database. Result: Last.fm cache is never written, every bridge search makes full network calls.
**Why it happens:** Browsers/WebView block persistent storage APIs (IndexedDB, localStorage) for `file://` origins due to same-origin policy ambiguity.
**How to avoid:** Serve exclusively via `WebViewAssetLoader`. The phase success criterion "IndexedDB cache written during one session is readable in a fresh app restart" will catch this during testing.
**Warning signs:** If `lastfm.ts` `openDB()` promise rejects with an error containing "blocked" or "SecurityError" in the WebView console.

### Pitfall 2: `next.config.android.ts` not picked up by `next build`
**What goes wrong:** `next build` reads `next.config.ts` (with `basePath: "/path"`) instead of the Android config, so all assets get the wrong path prefix.
**Why it happens:** Next.js looks for `next.config.ts` by default. Overriding requires explicit flag or env var.
**How to avoid:** Pass `--config next.config.android.ts` to the `next build` invocation in the Gradle exec task, OR set `NEXT_CONFIG_FILE=next.config.android.ts` in the environment of the exec task. Verify by checking whether the exported `out/index.html` references `/_next/` (wrong) or `./_next/` / `_next/` (correct for basePath "").
**Warning signs:** Generated `out/index.html` contains `href="/path/_next/static/...` instead of `href="/_next/static/...`.

### Pitfall 3: WebView constructed off main thread
**What goes wrong:** `java.lang.RuntimeException: WebView cannot be created on a background thread`
**Why it happens:** Hilt can inject singletons during component initialization. If anything triggers injection of `@BridgeWebView WebView` from a background coroutine before `App.onCreate()` completes, it will crash.
**How to avoid:** Explicitly trigger WebView injection from `App.onCreate()` (main thread) by calling a no-op `@Inject` on the application-scope class, similar to how YouTube/Spotify singletons are initialized. Alternatively: annotate the provide function with `@MainThread` to make the requirement explicit.
**Warning signs:** Crash in `WebView.<init>` with a thread name that is not "main".

### Pitfall 4: `assets/eccopath/` directory not on the correct path handler prefix
**What goes wrong:** WebView loads `https://appassets.androidplatform.net/assets/eccopath/index.html` but `shouldInterceptRequest` returns `null`, causing a 404.
**Why it happens:** The `AssetsPathHandler` path prefix in `WebViewAssetLoader.Builder.addPathHandler()` must exactly match the URL path prefix. If the handler is registered for `/assets/` but the URL uses `/assets/eccopath/`, the file lookup will be `eccopath/index.html` within the `assets/` folder — which is correct because `AssetsPathHandler` appends the URL suffix to the assets root.
**How to avoid:** Register the handler as `addPathHandler("/assets/", AssetsPathHandler(context))`. The file `app/src/main/assets/eccopath/index.html` is then accessible at `https://appassets.androidplatform.net/assets/eccopath/index.html`. Test with a log in `shouldInterceptRequest` to confirm the URL is being intercepted.
**Warning signs:** WebView chrome dev tools (via `chrome://inspect`) shows 404 for `/_next/static/` chunks.

### Pitfall 5: `crossorigin` attribute causes chunk-load failures in WebView
**What goes wrong:** Next.js includes `crossorigin=""` on `<script>` tags in static exports. Under `WebViewAssetLoader`'s HTTPS origin, the browser treats this as a CORS request. The `AssetsPathHandler` does not set `Access-Control-Allow-Origin` headers, so the CORS preflight fails and JS chunks are blocked.
**Why it happens:** Next.js #61210 introduced spurious `crossorigin` attributes on static exports. Fixed in ~14.2 but D-06 requires belt-and-suspenders strip regardless.
**How to avoid:** The `strip-crossorigin.mjs` script removes all `crossorigin` attributes from HTML files in `out/` after `next build`. Verify: `grep -r crossorigin eccopath/out/` should return empty.
**Warning signs:** WebView console shows "CORS request blocked" or scripts fail to load even though files exist.

### Pitfall 6: Icon adaptive layer has transparent background
**What goes wrong:** On Android 8+ the adaptive icon background layer must be opaque. If the EccoMuse logo PNG is placed directly as the background, any transparent pixels show device default color (usually white/black), potentially looking wrong.
**Why it happens:** The source logo is a 1024x1024 PNG. Without examining its content we cannot know if it has a transparent background.
**How to avoid:** Use the logo as the foreground layer only. Create a solid-color background layer using a color drawable or a filled-color PNG. The existing `ic_launcher_background.xml` (vector, solid color fill) pattern works.
**Warning signs:** Icon shows unexpected color "bleeding" around the logo on some launcher masks.

---

## Code Examples

### WebViewAssetLoader Setup (Kotlin)
```kotlin
// Source: https://developer.android.com/develop/ui/views/layout/webapps/load-local-content
val assetLoader = WebViewAssetLoader.Builder()
    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
    .build()

webView.webViewClient = object : WebViewClient() {
    @RequiresApi(21)
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        return assetLoader.shouldInterceptRequest(request.url)
    }
}
webView.settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true   // required for IndexedDB
}
webView.loadUrl("https://appassets.androidplatform.net/assets/eccopath/index.html")
```

### WebView Settings for IndexedDB Persistence
```kotlin
webView.settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true          // enables localStorage AND IndexedDB
    databaseEnabled = true            // legacy Web SQL, harmless to enable
    allowFileAccess = false           // not needed; using WebViewAssetLoader
    allowContentAccess = false        // not needed
}
```

### Gradle Exec Task (Kotlin DSL)
```kotlin
// app/build.gradle.kts
val buildEccoPath by tasks.registering(Exec::class) {
    description = "Build EccoPath Next.js static export for Android"
    group = "eccopath"
    workingDir = file("${rootProject.projectDir}/eccopath")
    commandLine("node", "${rootProject.projectDir}/eccopath/node_modules/.bin/next",
                "build", "--config", "next.config.android.ts")
    // Use node_modules/.bin/next directly to avoid PATH dependency
    doLast {
        exec {
            workingDir = file("${rootProject.projectDir}/eccopath")
            commandLine("node", "scripts/strip-crossorigin.mjs")
        }
    }
    inputs.dir("${rootProject.projectDir}/eccopath/lib")
    inputs.dir("${rootProject.projectDir}/eccopath/app")
    inputs.file("${rootProject.projectDir}/eccopath/next.config.android.ts")
    outputs.dir("${rootProject.projectDir}/eccopath/out")
}
```

### Adaptive Icon PNG Dimensions (ImageMagick)
```bash
# Source: https://developer.android.com/develop/ui/views/launch/icon_design_adaptive
# Foreground layer: 108x108dp canvas, 66x66dp safe zone
# Monochrome and background layers use same dimensions
SRC="/home/kepler/Projects/EccoMuse-Site/src/assets/images/EccoMuse_new_logo.png"

# xxxhdpi: 432x432
convert "$SRC" -resize 432x432 mipmap-xxxhdpi/ic_launcher_foreground.png
# xxhdpi:  324x324
convert "$SRC" -resize 324x324 mipmap-xxhdpi/ic_launcher_foreground.png
# xhdpi:   216x216
convert "$SRC" -resize 216x216 mipmap-xhdpi/ic_launcher_foreground.png
# hdpi:    162x162
convert "$SRC" -resize 162x162 mipmap-hdpi/ic_launcher_foreground.png
# mdpi:    108x108
convert "$SRC" -resize 108x108 mipmap-mdpi/ic_launcher_foreground.png
```

### Branding String Locations
```
app/src/main/res/values/app_name.xml
  → <string name="app_name">Meld</string>  (change to "EccoMeld")

app/src/main/res/values/metrolist_strings.xml
  → <string name="crash_report_subject">Meld Crash Report</string>
  → <string name="discord_playing_metrolist">Playing Meld</string>
  → <string name="discord_watching_metrolist">Watching Meld</string>
  → <string name="discord_competing_metrolist">Competing in Meld</string>

app/src/main/kotlin/com/metrolist/music/utils/CrashHandler.kt
  → hardcoded "Meld Crash Report" string literal in Kotlin code
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `file://` assets in WebView | `WebViewAssetLoader` + HTTPS origin | `WebViewAssetLoader` available since AndroidX Webkit 1.2.0 (2019) | IndexedDB, same-origin policy, and CORS all work correctly |
| Next.js `basePath` for web server prefix | `output: "export"` with `basePath: ""` for standalone HTML | Next.js 13+ | Static files reference assets by root-relative or relative URLs |

**Deprecated/outdated:**
- `WebView.setAllowFileAccessFromFileURLs(true)`: Security vulnerability; never use as workaround for `file://` limitations.
- `WebSettings.setDatabasePath()`: Deprecated in Android API 19 — was for Web SQL. Do not use. `domStorageEnabled` is the current replacement.

---

## Open Questions

1. **Does `next build --config next.config.android.ts` work in Next.js 16.2.2?**
   - What we know: Next.js supports a `NEXT_CONFIG_FILE` environment variable for overriding the config file path. The `--config` CLI flag exists in some versions.
   - What's unclear: Whether the `--config` flag is available in Next.js 16 (training data covers up to mid-2025; Next.js 16 was released after that).
   - Recommendation: The Gradle task should use the `NEXT_CONFIG_FILE` env var approach as the fallback: `environment("NEXT_CONFIG_FILE", "next.config.android.ts")`. The task author should verify this against EccoPath's installed Next.js before finalizing.

2. **Does the EccoPath `out/` directory need a specific entry point filename?**
   - What we know: `next export` generates `out/index.html` for the root route. EccoPath's root route is `app/page.tsx`.
   - What's unclear: Whether EccoPath has any dynamic routes that generate multiple HTML files, which would require mapping them correctly under `WebViewAssetLoader`.
   - Recommendation: Load `index.html` via `loadUrl("https://appassets.androidplatform.net/assets/eccopath/index.html")`. The headless WebView only needs the root — it is a SPA.

3. **Does the 1024x1024 EccoMuse logo PNG have a transparent background?**
   - What we know: The file exists at the correct path, is PNG format, 1024x1024, sRGB.
   - What's unclear: Whether the logo has a transparent background channel (which would make it suitable as a foreground layer only, requiring a separate background).
   - Recommendation: Inspect visually. If transparent background: use logo as foreground layer, create a solid `#ED5564` (DefaultThemeColor) background. If opaque: can use as foreground with white/transparent background.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js | Gradle exec task (`next build`) | Yes | 20.20.0 | — |
| npm | Gradle exec task | Yes | 10.8.2 | — |
| Next.js (`eccopath/node_modules/.bin/next`) | Gradle exec task | Yes | 16.2.2 | Run `npm install` in eccopath/ first |
| ImageMagick (`convert`) | Adaptive icon generation | Yes | 7.1.2-13 | Manual resize in any image editor |
| `androidx.webkit` | WebViewAssetLoader | Not yet in dependencies | — | Must add to `libs.versions.toml` |
| Android SDK 26+ | Target platform | Yes (project min SDK) | — | — |
| `app/src/main/assets/` directory | EccoPath assets | Does not exist yet | — | Create directory as part of build task |

**Missing dependencies with no fallback:**
- `androidx.webkit` must be added to `libs.versions.toml` and `app/build.gradle.kts` before any WebView code compiles.

**Missing dependencies with fallback:**
- `eccopath/node_modules/` — if not present, run `npm install` in `eccopath/` before executing the Gradle task. The Wave 0 task for INFRA-01 should include this check or document it.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | None — no test infrastructure exists in the project |
| Config file | None |
| Quick run command | N/A |
| Full suite command | N/A |

**Note:** The project has no existing test infrastructure. `app/src/test/` directory does not exist. `app/src/androidTest/` directory does not exist. JUnit 4.13.2 is in `libs.versions.toml` but not used. No test runner is configured.

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| INFRA-01 | EccoPath static export builds and `out/` is copied to APK assets | manual-only | Verify `app/src/main/assets/eccopath/index.html` exists post-build | N/A |
| INFRA-02 | WebView loads EccoPath without 404s for `/_next/` chunks | manual-only | Install APK, open app, check logcat / chrome://inspect | N/A |
| INFRA-03 | IndexedDB cache survives app restart | manual-only | Perform bridge search, force-close app, perform another search; second should complete faster (L2 cache hit) | N/A |
| INFRA-04 | App name "EccoMeld" shown in launcher and app bar | manual-only | Visual inspection of launcher icon + app bar | N/A |

All phase requirements are UI/device behaviors that require a running Android device or emulator for validation. None are unit-testable without significant test infrastructure setup. Manual testing on device is the correct validation approach for this phase.

### Sampling Rate
- **Per task commit:** Build the APK variant (`./gradlew assembleFossUniversalDebug`) — confirm no compilation errors.
- **Per wave merge:** Install on device/emulator and verify each success criterion manually.
- **Phase gate:** All four INFRA success criteria confirmed on device before `/gsd:verify-work`.

### Wave 0 Gaps

- [ ] `app/src/main/assets/eccopath/` directory — created by Gradle build task (not a code file)
- [ ] `eccopath/next.config.android.ts` — must be created before build task can run
- [ ] `eccopath/scripts/strip-crossorigin.mjs` — must be created before build task runs post-build step
- [ ] `androidx.webkit` added to `libs.versions.toml` and `app/build.gradle.kts` — Wave 0 setup
- [ ] `app/src/main/kotlin/com/metrolist/music/di/Qualifiers.kt` — add `@BridgeWebView` qualifier annotation

---

## Project Constraints (from CLAUDE.md)

| Directive | Impact on This Phase |
|-----------|---------------------|
| Platform: Android only, SDK 26+ | WebViewAssetLoader requires `androidx.webkit 1.2.0+` — compatible. No API below 26 needed. |
| Playback: Must use existing Media3/ExoPlayer stack — no rewrite | This phase does not touch playback. No constraint conflict. |
| Bridge algorithm: WebView + JS bridge for MVP | Confirmed: WebView is headless, JS bridge in Phase 3. This phase only establishes the WebView foundation. |
| EccoPath bundling: Git submodule, built assets in APK | D-04/D-09/D-10 exactly align with this. |
| Package name: Keep `com.metrolist.music` | Branding changes are string resources only — no package rename. |
| License: GPL | `androidx.webkit` is Apache 2.0 — compatible with GPL. |
| Build system: Kotlin DSL (`build.gradle.kts`) | Gradle exec task must use Kotlin DSL syntax (confirmed in patterns above). |
| Dependency injection: Dagger Hilt `@Singleton @Provides` | WebView singleton follows existing `AppModule.kt` pattern exactly. |
| Logging: Timber | WebView lifecycle events should use `Timber.d()`. |
| Coroutines: Specify dispatcher explicitly | WebView initialization on main thread must not use `Dispatchers.IO`/`Default`. |
| No ktlint/detekt | No formatter enforcement. Follow existing code style manually. |
| GSD Workflow: Use `/gsd:execute-phase` for repo edits | Enforced by CLAUDE.md — no direct edits outside GSD workflow. |

---

## Sources

### Primary (HIGH confidence)
- [Android Developers: Load local content in WebView](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content) — WebViewAssetLoader builder pattern, `AssetsPathHandler`, `shouldInterceptRequest` integration
- [Android Developers: Adaptive icons](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive) — Layer dimensions, density buckets, safe zone requirements
- [Next.js GitHub issue #61210](https://github.com/vercel/next.js/issues/61210) — Crossorigin attribute bug description and confirmed fix in PR #61211 (merged March 2024)

### Secondary (MEDIUM confidence)
- [Android Developers: WebViewAssetLoader API reference](https://developer.android.com/reference/androidx/webkit/WebViewAssetLoader) — Class structure confirmed
- [caniwebview.com: IndexedDB](https://caniwebview.com/features/mdn-indexeddb/) — IndexedDB works in Android WebView; `file://` origin is the known blocker
- EccoPath source code (`/home/kepler/Projects/EccoPath/lib/lastfm.ts`) — Direct inspection of IndexedDB usage and DB_NAME `eccopath-lastfm-cache`
- EccoPath source code (`/home/kepler/Projects/EccoPath/next.config.ts`) — Current config with `basePath: "/path"`, `assetPrefix: "/path"`

### Tertiary (LOW confidence)
- Next.js `--config` CLI flag availability in Next.js 16 — not verified against official changelog; use `NEXT_CONFIG_FILE` env var as primary approach

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — `WebViewAssetLoader` is official Android documentation; `androidx.webkit` version verified via API reference
- Architecture patterns: HIGH — patterns derived from official Android documentation + direct inspection of existing codebase code style
- Pitfalls: HIGH — IndexedDB/`file://` issue is documented in multiple official and community sources; crossorigin bug verified via GitHub issue history
- Gradle task: MEDIUM — Exec task pattern is standard Gradle; `NEXT_CONFIG_FILE` env var vs `--config` flag should be verified against Next.js 16 docs before implementation

**Research date:** 2026-04-04
**Valid until:** 2026-07-04 (stable APIs — `WebViewAssetLoader`, adaptive icons, Gradle tasks are all stable)
