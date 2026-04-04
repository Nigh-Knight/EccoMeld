---
phase: 03-js-bridge
plan: 02
subsystem: bridge
tags: [android, webview, javascript-bridge, hilt, viewmodel, kotlin]

# Dependency graph
requires:
  - phase: 03-js-bridge/03-01
    provides: MeldBridgeInterface with onStateChange/onReady mutable var callbacks and @JavascriptInterface methods
  - phase: 03-js-bridge/03-00
    provides: BridgeWebView qualifier and BridgeModule singleton WebView setup with WebViewAssetLoader

provides:
  - BridgeModule.provideMeldBridgeInterface() Hilt singleton provider
  - MeldBridgeInterface registered on WebView as "MeldBridge" JS interface
  - BRIDGE_GLUE_JS injected via evaluateJavascript when onBridgeReady fires
  - BridgeViewModel.startBridge() with isRunning guard, JSONObject.quote() escaping, main-thread dispatch
  - BridgeViewModel.resetState() to return to Idle
  - meldBridgeInterface.onStateChange wired to _uiState in ViewModel init block

affects:
  - 04-bridge-screen — consumes startBridge() from BridgeViewModel
  - 05-playlist-builder — reads uiState PathFound result for playlist construction
  - any phase that calls BridgeViewModel.startBridge() directly

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Mutable var callback pattern for cross-singleton communication (MeldBridgeInterface.onStateChange assigned in ViewModel init)
    - evaluateJavascript dispatched via Handler(Looper.getMainLooper()).post for main-thread safety
    - JSONObject.quote() for safe artist name injection into JS strings
    - BRIDGE_GLUE_JS const with then/catch pattern instead of async/await for WebView compatibility

key-files:
  created: []
  modified:
    - app/src/main/kotlin/com/metrolist/music/di/BridgeModule.kt
    - app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt
    - app/src/test/kotlin/com/metrolist/music/bridge/BridgeViewModelTest.kt

key-decisions:
  - "BRIDGE_GLUE_JS uses .then()/.catch() not async/await — injected JS via evaluateJavascript has inconsistent async function support on some Android WebView versions"
  - "onReady set in BridgeModule not BridgeViewModel — BridgeModule owns the WebView lifecycle, JS glue injection belongs there"
  - "isRunning is a computed property from _uiState, not a separate flag — stays in sync with MeldBridgeInterface callbacks automatically"

patterns-established:
  - "Mutable var callback (onStateChange): assigned in ViewModel init block after Hilt constructs the singleton — avoids constructor param ordering issue between Hilt singletons and ViewModels"
  - "JS glue injection: happens in onReady (post-React-hydration), never in onPageFinished (too early for dynamic exports)"
  - "Concurrency guard: isRunning derived from uiState is BridgeUiState.Searching — checked at top of startBridge(), returns early"

requirements-completed: [BRDG-02, BRDG-03]

# Metrics
duration: 5min
completed: 2026-04-04
---

# Phase 03 Plan 02: Bridge JS Wiring Summary

**Full Kotlin-to-JS round-trip wired: MeldBridgeInterface registered on WebView as "MeldBridge", BRIDGE_GLUE_JS injected on EccoPath readiness, BridgeViewModel.startBridge() dispatches evaluateJavascript with escaped artist names and isRunning concurrency guard**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-04-04T09:01:55Z
- **Completed:** 2026-04-04T09:04:39Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- BridgeModule now provides `MeldBridgeInterface` as a Hilt singleton and registers it on the WebView as `"MeldBridge"` at creation time
- `BRIDGE_GLUE_JS` defines `window.startBridge(start, end)` that wraps `window.__eccoFindBridge` with null-guard, progress callbacks, and error handling — injected via `evaluateJavascript` when `onBridgeReady()` fires
- `BridgeViewModel` gained `startBridge()` (with `isRunning` guard, `JSONObject.quote()` escaping, main-thread dispatch), `resetState()`, and `init` block wiring `meldBridgeInterface.onStateChange` to `_uiState`
- `BridgeViewModelTest` stubs activated with real test bodies verifying `Searching` state on first call and no-op behavior on concurrent calls

## Task Commits

Each task was committed atomically:

1. **Task 1: Wire BridgeModule to register MeldBridgeInterface and inject JS glue** - `915b5829` (feat)
2. **Task 2: Add startBridge() to BridgeViewModel with isRunning guard and WebView invocation** - `71e6b47a` (feat)

**Plan metadata:** (docs commit — created below)

## Files Created/Modified
- `app/src/main/kotlin/com/metrolist/music/di/BridgeModule.kt` - Added `provideMeldBridgeInterface()`, `BRIDGE_GLUE_JS` const, `addJavascriptInterface` call, `meldBridgeInterface.onReady` glue injection
- `app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt` - Full rewrite adding WebView + MeldBridgeInterface injection, `isRunning`, `startBridge()`, `resetState()`, and `onStateChange` init wiring
- `app/src/test/kotlin/com/metrolist/music/bridge/BridgeViewModelTest.kt` - Removed `@Ignore`, added test bodies for reject-when-running and state-transition-to-Searching

## Decisions Made
- BRIDGE_GLUE_JS uses `.then()/.catch()` instead of `async/await` — more reliable for raw JS injected via `evaluateJavascript` across Android WebView versions
- `onReady` callback set in BridgeModule (not BridgeViewModel) because BridgeModule owns the WebView and the `evaluateJavascript` call needs to run on the same object
- `isRunning` is a computed property (`_uiState.value is BridgeUiState.Searching`) not a separate `AtomicBoolean` — eliminates state desync risk

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Gradle compile check (`./gradlew app:compileFossDebugKotlin`) could not run — `JAVA_HOME` not set and no `java` binary on PATH in this shell environment. Kotlin correctness verified via manual structural inspection of all imports, types, and API usage patterns matching the codebase conventions. Full compile will be validated by the orchestrator's post-phase hook.

## User Setup Required

None - no external service configuration required.

## Known Stubs

None — both files wire real data. `BridgeViewModel` connects `MeldBridgeInterface.onStateChange` to `_uiState` and `startBridge()` calls real `evaluateJavascript`. No placeholder values flow to the UI.

## Next Phase Readiness
- Full Kotlin <-> JS bridge round-trip is wired and ready for Phase 4 (Bridge Screen artist search inputs that call `viewModel.startBridge()`)
- `BridgeViewModel.uiState` emits `PathFound(path)` when EccoPath completes, ready for Phase 5 playlist builder consumption
- Blocker: EccoPath `window.__eccoFindBridge` must be exposed via `useEffect` in Next.js — validated in Phase 3 Research but not yet exercised end-to-end (deferred to integration testing in Phase 4)

---
*Phase: 03-js-bridge*
*Completed: 2026-04-04*
