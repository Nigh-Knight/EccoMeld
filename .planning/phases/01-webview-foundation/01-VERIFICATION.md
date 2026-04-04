---
phase: 01-webview-foundation
verified: 2026-04-04T04:59:07Z
status: human_needed
score: 3/4 must-haves verified (1 requires runtime check)
re_verification: false
human_verification:
  - test: "IndexedDB cache survives app restart"
    expected: "Data written to IndexedDB in one session is readable after killing and relaunching the app. Verify via Chrome DevTools: connect to WebView via chrome://inspect, run indexedDB.open('test') and write a key, restart app, confirm key is still present."
    why_human: "IndexedDB persistence is a runtime behavior that cannot be verified statically. The code correctly enables domStorageEnabled=true and serves from an HTTPS origin (prerequisites for IndexedDB). Whether data actually survives a full process kill requires device testing."
  - test: "EccoPath loads without 404s for /_next/ chunks in device Logcat"
    expected: "Logcat shows 'BridgeModule: EccoPath loaded at https://appassets.androidplatform.net/assets/eccopath/index.html' and NO net::ERR_FILE_NOT_FOUND or 404 errors for /_next/ chunks. Chrome DevTools console shows no errors."
    why_human: "While all 13 /_next/ chunk paths referenced in index.html exist in APK assets, confirming zero 404s at runtime (including dynamically-loaded chunks) requires running the app and checking Logcat or DevTools."
  - test: "App displays 'EccoMeld' in launcher and app bar"
    expected: "Launcher shows 'EccoMeld' label under the EccoMuse logo icon. Opening the app shows 'EccoMeld' in the app bar."
    why_human: "Visual appearance and launcher behavior requires a device or emulator. Cannot verify from static files alone."
---

# Phase 1: WebView Foundation Verification Report

**Phase Goal:** EccoPath loads correctly in a WebView served from a stable HTTPS origin, IndexedDB persists across restarts, and the app is branded as EccoMeld
**Verified:** 2026-04-04T04:59:07Z
**Status:** human_needed — automated checks pass; 3 items require runtime verification
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (from ROADMAP.md Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | EccoPath renders in the WebView without 404s for any `/_next/` chunk | ? UNCERTAIN | All 13 `/_next/` chunk references in index.html resolve to files in APK assets. Runtime Logcat needed to confirm zero 404s. |
| 2 | IndexedDB cache written during one session is readable in a fresh app restart | ? UNCERTAIN | `domStorageEnabled=true`, served from `https://appassets.androidplatform.net` (HTTPS origin required for IndexedDB). Cannot verify persistence without runtime test. |
| 3 | The app name displays as "EccoMeld" in the launcher and app bar | ? UNCERTAIN | `app_name.xml` contains `EccoMeld`, AndroidManifest wires `android:label="@string/app_name"`. Visual display requires device. |
| 4 | WebView is served from `https://appassets.androidplatform.net/` — never `file://` | ✓ VERIFIED | `BridgeModule.kt` calls `loadUrl("https://appassets.androidplatform.net/assets/eccopath/index.html")`, `allowFileAccess=false`. No file:// code paths exist. |

**Score:** 1/4 truths fully verified by static analysis; 3 require runtime confirmation. All static prerequisites pass.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `.gitmodules` | EccoPath submodule declaration | ✓ VERIFIED | Contains `[submodule "eccopath"]`, `path = eccopath`, `branch = master` |
| `eccopath/next.config.android.ts` | Android Next.js config with empty basePath | ✓ VERIFIED | Contains `output: "export"`, `basePath: ""`, `assetPrefix: ""` |
| `eccopath/scripts/strip-crossorigin.mjs` | Strip crossorigin HTML attributes | ✓ VERIFIED | Both regex patterns present; processes all `.html` files recursively |
| `app/build.gradle.kts` | Gradle tasks for EccoPath pipeline | ✓ VERIFIED | `buildEccoPath`, `copyEccoPathAssets`, `preBuild` dependency all present |
| `app/src/main/assets/eccopath/index.html` | Exported Next.js static HTML | ✓ VERIFIED | Exists; all 13 `/_next/` chunk references resolve to existing files |
| `gradle/libs.versions.toml` | androidx.webkit dependency declaration | ✓ VERIFIED | `webkit = "1.13.0"` in versions, `webkit = { module = "androidx.webkit:webkit" }` in libraries |
| `app/build.gradle.kts` | webkit implementation dependency | ✓ VERIFIED | `implementation(libs.webkit)` present |
| `app/src/main/kotlin/com/metrolist/music/di/Qualifiers.kt` | `@BridgeWebView` qualifier annotation | ✓ VERIFIED | `annotation class BridgeWebView` with correct `@Qualifier @Retention(AnnotationRetention.BINARY)` |
| `app/src/main/kotlin/com/metrolist/music/di/BridgeModule.kt` | Hilt singleton WebView with WebViewAssetLoader | ✓ VERIFIED | Full implementation: `@Singleton @Provides @BridgeWebView`, `AssetsPathHandler`, `domStorageEnabled=true`, `loadUrl("https://appassets.androidplatform.net/assets/eccopath/index.html")` |
| `app/src/main/kotlin/com/metrolist/music/App.kt` | Eager WebView initialization trigger | ✓ VERIFIED | `BridgeWebViewEntryPoint` interface + `EntryPointAccessors.fromApplication(...).bridgeWebView()` in `onCreate()` |
| `app/src/main/res/values/app_name.xml` | App name string resource | ✓ VERIFIED | `<string name="app_name">EccoMeld</string>` |
| `app/src/main/res/values/metrolist_strings.xml` | Branded string resources | ✓ VERIFIED | 14 occurrences of "EccoMeld"; no standalone `>Meld<` values remain |
| `app/src/main/kotlin/com/metrolist/music/utils/CrashHandler.kt` | Crash report subject | ✓ VERIFIED | Line 53: `appendLine("EccoMeld Crash Report")` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png` | Highest density launcher icon foreground | ✓ VERIFIED | 432x432 px PNG exists |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `app/build.gradle.kts` (buildEccoPath) | `eccopath/next.config.android.ts` | Config-file-swap (`doFirst` copies android config over `next.config.ts`) | ✓ VERIFIED | DEVIATION from plan: NEXT_CONFIG_FILE env var not supported by Next.js — correctly replaced with file-swap pattern |
| `app/build.gradle.kts` (copyEccoPathAssets) | `app/src/main/assets/eccopath/` | Copy task from `eccopath/out/` | ✓ VERIFIED | Assets directory populated; `copyEccoPathAssets` depends on `buildEccoPath` |
| `BridgeModule.kt` | WebViewAssetLoader + AssetsPathHandler | `.addPathHandler("/assets/", AssetsPathHandler(context))` | ✓ VERIFIED | Maps `https://appassets.androidplatform.net/assets/*` to APK assets |
| `BridgeModule.kt` | `https://appassets.androidplatform.net/assets/eccopath/index.html` | `loadUrl()` call | ✓ VERIFIED | Exact URL present |
| `App.kt` | `BridgeModule.kt @BridgeWebView` provider | `EntryPointAccessors.fromApplication(this, BridgeWebViewEntryPoint::class.java).bridgeWebView()` | ✓ VERIFIED | Eagerly creates singleton on main thread in `onCreate()` |
| `AndroidManifest.xml` | `app/src/main/res/values/app_name.xml` | `android:label="@string/app_name"` | ✓ VERIFIED | Both `<application>` and `<activity>` use `@string/app_name` |

### Data-Flow Trace (Level 4)

Not applicable for this phase. Artifacts are infrastructure (Gradle pipeline, Hilt DI module, string resources, icon PNGs) — not components that render dynamic data.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| All /_next/ chunk paths exist in APK assets | Python path existence check against index.html references | 0 of 13 missing | ✓ PASS |
| Icon dimensions match adaptive icon spec | PNG header byte parsing for all 5 densities | mdpi=108, hdpi=162, xhdpi=216, xxhdpi=324, xxxhdpi=432 | ✓ PASS |
| crossorigin HTML attributes stripped from index.html | Regex scan for tag-level crossorigin attrs | 0 found (JSON data in `<script>` inline content is not an HTML attribute — correct) | ✓ PASS |
| No /path/ prefix on /_next/ asset paths | Regex scan on script src + link href attrs | All 13 /_next/ refs use root-relative paths with no /path/ prefix | ✓ PASS |
| Git commits exist | `git log --oneline <hashes>` | All 7 claimed commits verified: 9e0a9b72, 9ff34554, 9e21e426, f2a0b062, 08a3987b, 12b5601c, 5f1f70d3 | ✓ PASS |
| EccoPath loads at runtime without 404s | Logcat inspection | SKIP — requires running app on device | ? SKIP |
| IndexedDB persists across restart | Chrome DevTools session test | SKIP — requires runtime testing | ? SKIP |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| INFRA-01 | 01-01-PLAN.md | EccoPath bundled as git submodule with static export packaged as APK assets | ✓ SATISFIED | `.gitmodules` has eccopath submodule; `app/src/main/assets/eccopath/index.html` exists; Gradle pipeline wired to preBuild |
| INFRA-02 | 01-03-PLAN.md | WebView loads bundled EccoPath via WebViewAssetLoader (HTTPS origin, not file://) | ✓ SATISFIED | `BridgeModule.kt` uses `WebViewAssetLoader.AssetsPathHandler`; loads `https://appassets.androidplatform.net/assets/eccopath/index.html`; `allowFileAccess=false` |
| INFRA-03 | 01-03-PLAN.md | IndexedDB cache persists across WebView sessions (Last.fm cache survives app restart) | ? NEEDS HUMAN | `domStorageEnabled=true` confirmed; HTTPS origin confirmed (both prerequisites). Actual persistence across process kill requires runtime test. |
| INFRA-04 | 01-02-PLAN.md | App name displayed as "EccoMeld" (launcher label, app bar) | ✓ SATISFIED (static) / ? NEEDS HUMAN (visual) | `app_name.xml` = "EccoMeld"; `AndroidManifest.xml` references `@string/app_name`; 14 branded strings updated; icons at correct dimensions. Visual confirmation requires device. |

No orphaned requirements — all 4 Phase 1 requirements (INFRA-01 through INFRA-04) are declared in plans and verified above.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `app/src/main/assets/eccopath/index.html` | inline `<link>` tags | `/path/icons/icon-192.png` and `/path/manifest.json` reference non-existent assets path | ⚠️ Warning | PWA apple-touch-icon and manifest will 404 in WebView. These are metadata resources — not `/_next/` JS chunks. EccoPath functionality is unaffected. |
| `app/src/main/assets/eccopath/index.html` | inline `<script>` | Service worker registration: `navigator.serviceWorker.register('/path/sw.js', {scope: '/path/'})` | ⚠️ Warning | Service worker won't register because `/path/sw.js` resolves outside `WebViewAssetLoader`'s `/assets/` handler. `sw.js` is at assets root (`/assets/eccopath/sw.js`) not `/path/sw.js`. If EccoPath's Last.fm cache relies on the service worker for offline caching, it may not persist. IndexedDB itself does NOT require a service worker — it persists based on origin alone. |

No TODO/FIXME/HACK/placeholder comments found in any phase-modified files. No empty return stubs.

**Important note on crossorigin:** The grep for "crossorigin" in index.html returns matches, but these are inside React server component flight data (JSON embedded in `<script>` tags as `"crossOrigin":""` or `"crossOrigin":"$undefined"`). These are React reconciliation keys, not HTML attributes. The strip-crossorigin.mjs script correctly targets tag-level `crossorigin` HTML attributes — there are zero such attributes in the output HTML. This is clean.

### Human Verification Required

#### 1. IndexedDB Persistence Across App Restart

**Test:** Install the APK on an Android device or emulator. Connect Chrome DevTools via `chrome://inspect/#devices`. In the EccoPath WebView console, run:
```javascript
const req = indexedDB.open("eccopath-test-db", 1);
req.onupgradeneeded = e => e.target.result.createObjectStore("kv");
req.onsuccess = e => {
  const tx = e.target.result.transaction("kv", "readwrite");
  tx.objectStore("kv").put("hello", "test-key");
  tx.oncomplete = () => console.log("Written");
};
```
Force-kill the app (swipe away from recents). Relaunch. In DevTools console run:
```javascript
const req = indexedDB.open("eccopath-test-db", 1);
req.onsuccess = e => {
  const tx = e.target.result.transaction("kv", "readonly");
  tx.objectStore("kv").get("test-key").onsuccess = e => console.log("Read:", e.target.result);
};
```
**Expected:** `Read: hello` — the value survives a full process kill and restart.
**Why human:** Runtime storage behavior cannot be verified statically.

#### 2. EccoPath Loads Without /_next/ 404s

**Test:** Install APK, open the app, filter Logcat for `BridgeModule` and `net::ERR`. Check for:
- `BridgeModule: Creating singleton WebView`
- `BridgeModule: EccoPath loaded at https://appassets.androidplatform.net/assets/eccopath/index.html`
- Zero `net::ERR_FILE_NOT_FOUND` or HTTP 404 for any `/_next/` URL.

Optionally, open `chrome://inspect/#devices` and verify the WebView console shows no errors. Check `window.location.origin === "https://appassets.androidplatform.net"`.
**Expected:** Page loads; no /_next/ chunk errors; `window.location.origin` is the HTTPS appassets origin.
**Why human:** Dynamic chunk loading (lazy-loaded routes, code splitting) cannot be predicted from static index.html analysis.

#### 3. EccoMeld Branding Visible in Launcher and App Bar

**Test:** Install APK. View the launcher — the icon should show EccoMuse logo (not old Meld icon) with "EccoMeld" label. Open the app and verify the app bar says "EccoMeld".
**Expected:** Launcher label = "EccoMeld", app bar title = "EccoMeld", icon = EccoMuse logo.
**Why human:** Visual rendering and launcher behavior require a device.

### Notable Deviations from Plan (Auto-Fixed)

Two deviations occurred during execution that changed the implementation from what PLAN frontmatter describes:

1. **NEXT_CONFIG_FILE env var not supported by Next.js** — Plan's key_link specified `NEXT_CONFIG_FILE` env var as the connection from `buildEccoPath` to `next.config.android.ts`. Actual implementation uses a config-file-swap pattern (`doFirst` copies android config over `next.config.ts`; `doLast` restores). The outcome is identical: android config is used for the build.

2. **EccoPath branch is `master` not `main`** — Plan's artifact check for `.gitmodules` specifies `branch = main`. Actual `.gitmodules` has `branch = master`. This is correct — EccoPath's default branch is `master`. PLAN frontmatter check for `branch = main` would falsely fail if run as-is.

These deviations do not affect goal achievement. Both are correct adaptations to real-world constraints documented in the summaries.

### Gaps Summary

No blocking gaps found. All artifacts exist, are substantive, and are correctly wired.

The three "human_needed" items are not gaps — the static code fully establishes the prerequisites for each success criterion. The runtime outcomes cannot be verified without a running device.

The `/path/sw.js` service worker issue is a warning worth monitoring: if EccoPath's offline Last.fm cache relies on the service worker strategy (cache-first via SW) rather than direct IndexedDB writes, the cache may not work as expected. However, the plan's stated requirement (INFRA-03) is that "IndexedDB cache persists" — not that the service worker runs. IndexedDB persistence is origin-based, not service-worker-based.

---

_Verified: 2026-04-04T04:59:07Z_
_Verifier: Claude (gsd-verifier)_
