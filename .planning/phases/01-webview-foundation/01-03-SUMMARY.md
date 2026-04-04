---
phase: 01-webview-foundation
plan: 03
subsystem: infra
tags: [android, webview, hilt, webkit, eccopath, indexeddb, webviewassetloader]

# Dependency graph
requires:
  - EccoPath static assets at app/src/main/assets/eccopath/ (from 01-01)
provides:
  - androidx.webkit 1.13.0 dependency in version catalog and app module
  - "@BridgeWebView qualifier annotation in Qualifiers.kt"
  - BridgeModule.kt Hilt singleton WebView with WebViewAssetLoader
  - Eager WebView initialization in App.onCreate() via EntryPoint pattern
affects: [bridge-tab, js-bridge, eccopath-integration, phase-02, phase-03]

# Tech tracking
tech-stack:
  added: [androidx.webkit 1.13.0, WebViewAssetLoader, WebView singleton via Hilt]
  patterns:
    - Hilt @Singleton WebView provider with @BridgeWebView qualifier
    - WebViewAssetLoader.AssetsPathHandler for HTTPS origin asset serving
    - EntryPoint pattern for eager singleton initialization in Application class
    - Headless WebView (never added to layout) for background computation

key-files:
  created:
    - app/src/main/kotlin/com/metrolist/music/di/BridgeModule.kt
  modified:
    - gradle/libs.versions.toml
    - app/build.gradle.kts
    - app/src/main/kotlin/com/metrolist/music/di/Qualifiers.kt
    - app/src/main/kotlin/com/metrolist/music/App.kt

key-decisions:
  - "EntryPoint pattern chosen over field injection in App — @HiltAndroidApp App cannot inject before super.onCreate() completes, EntryPointAccessors.fromApplication() is the correct pattern"
  - "WebView eager init placed after Timber.plant() but before coroutine launch — main thread required for WebView creation"
  - "allowFileAccess=false and allowContentAccess=false — WebViewAssetLoader handles all asset loading, no file:// access needed"
  - "databaseEnabled=true alongside domStorageEnabled=true — legacy Web SQL flag, safe and may benefit EccoPath IndexedDB cache"

patterns-established:
  - "Headless Hilt singleton WebView: @Singleton @BridgeWebView provider in @Module, eager init in App.onCreate() via EntryPoint — survives config changes, ready before first Bridge use"

requirements-completed: [INFRA-02, INFRA-03]

# Metrics
duration: 2min
completed: 2026-04-04
---

# Phase 01 Plan 03: Hilt Singleton WebView with WebViewAssetLoader Summary

**Hilt @Singleton headless WebView serving EccoPath from HTTPS origin via WebViewAssetLoader, with domStorageEnabled for IndexedDB persistence, eagerly initialized in App.onCreate()**

## Performance

- **Duration:** 2 min
- **Started:** 2026-04-04T05:12:46Z
- **Completed:** 2026-04-04T05:14:26Z
- **Tasks:** 3 (+ 1 checkpoint auto-approved)
- **Files modified:** 5

## Accomplishments

- Added `androidx.webkit 1.13.0` to `libs.versions.toml` and `app/build.gradle.kts`
- Added `@BridgeWebView` qualifier annotation to `Qualifiers.kt` following existing pattern
- Created `BridgeModule.kt` with `@Singleton @Provides @BridgeWebView` WebView provider
- `WebViewAssetLoader` maps `https://appassets.androidplatform.net/assets/` to bundled APK assets
- Loads `https://appassets.androidplatform.net/assets/eccopath/index.html` on creation
- `domStorageEnabled = true` enables IndexedDB persistence across sessions (INFRA-03)
- `allowFileAccess = false`, `allowContentAccess = false` — no file:// access needed
- Timber logging for lifecycle debugging: creation log + onPageFinished log
- `BridgeWebViewEntryPoint` interface + `EntryPointAccessors.fromApplication()` in `App.onCreate()` triggers eager singleton creation on the main thread (D-02)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add androidx.webkit dependency and @BridgeWebView qualifier** - `08a3987b` (feat)
2. **Task 2: Create BridgeModule with singleton WebView and WebViewAssetLoader** - `12b5601c` (feat)
3. **Task 3: Wire eager WebView initialization in App.kt** - `5f1f70d3` (feat)

## Files Created/Modified

- `gradle/libs.versions.toml` - webkit version + library entry added
- `app/build.gradle.kts` - `implementation(libs.webkit)` added to dependencies
- `app/src/main/kotlin/com/metrolist/music/di/Qualifiers.kt` - `@BridgeWebView` qualifier added
- `app/src/main/kotlin/com/metrolist/music/di/BridgeModule.kt` - NEW: Hilt module with singleton WebView provider
- `app/src/main/kotlin/com/metrolist/music/App.kt` - `BridgeWebViewEntryPoint` interface + eager init in `onCreate()`

## Decisions Made

- **EntryPoint pattern for App.kt injection:** `App` is the `@HiltAndroidApp` class and cannot use `@Inject` field injection before Hilt initializes. `EntryPointAccessors.fromApplication()` is the correct Hilt-supported way to access singletons from the Application class eagerly.
- **Eager init placement:** After `Timber.plant()` (so logging works) but before `applicationScope.launch {}` (so it runs on main thread synchronously, not on a coroutine dispatcher).
- **`databaseEnabled = true`:** Legacy Web SQL flag enabled alongside `domStorageEnabled` — harmless and may benefit any Web SQL fallback paths in EccoPath's IndexedDB implementation.

## Deviations from Plan

None — plan executed exactly as written. The EntryPoint import details (`dagger.hilt.EntryPoint`, `dagger.hilt.InstallIn`, `dagger.hilt.android.EntryPointAccessors`, `dagger.hilt.components.SingletonComponent`) matched plan expectations exactly.

## Known Stubs

None — BridgeModule creates a real WebView that loads EccoPath. Full IndexedDB persistence validation deferred to Phase 3+ when the JS bridge is functional (checkpoint gate in Task 4).

## Self-Check: PASSED

- `BridgeModule.kt` exists: FOUND
- `@BridgeWebView` in Qualifiers.kt: FOUND
- `webkit = "1.13.0"` in libs.versions.toml: FOUND
- `libs.webkit` in app/build.gradle.kts: FOUND
- `BridgeWebViewEntryPoint` in App.kt: FOUND
- Commits 08a3987b, 12b5601c, 5f1f70d3: FOUND in git log

---
*Phase: 01-webview-foundation*
*Completed: 2026-04-04*
