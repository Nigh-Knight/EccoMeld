---
phase: 08-native-kotlin-bridge-algorithm
plan: 03
subsystem: bridge-viewmodel
tags: [kotlin, hilt, viewmodel, coroutines, tdd, mockito]

# Dependency graph
requires:
  - phase: 08-02
    provides: BridgeAlgorithm.findBridge() ready for injection into BridgeViewModel
provides:
  - BridgeAlgorithmModule Hilt marker module
  - BridgeViewModel rewired to call BridgeAlgorithm.findBridge() directly
  - No evaluateJavascript calls remain in BridgeViewModel
  - BridgeViewModelTest updated with native algorithm tests (Dispatchers.setMain pattern)
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Dispatchers.setMain(StandardTestDispatcher()) in @Before for suspend coroutine tests that call withContext(Dispatchers.Main)
    - advanceUntilIdle() replaces delay(100) for coroutine advancement in runTest
    - buildForPath mock stubbed to emptyList() — Mockito default null causes NPE on List.isNotEmpty()

key-files:
  created:
    - app/src/main/kotlin/com/metrolist/music/di/BridgeAlgorithmModule.kt
  modified:
    - app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt
    - app/src/test/kotlin/com/metrolist/music/bridge/BridgeViewModelTest.kt

key-decisions:
  - "BridgeAlgorithmModule is a marker module — BridgeAlgorithm and KotlinBridgeCache use @Inject constructor + @Singleton so no explicit @Provides needed"
  - "init block onStateChange wiring removed — BridgeViewModel now drives state via coroutine result, not MeldBridgeInterface callbacks"
  - "mainHandler and Handler/Looper imports removed — withContext(Dispatchers.Main) handles main-thread state updates cleanly"
  - "Dispatchers.setMain(StandardTestDispatcher()) required in tests — viewModelScope.launch coroutines call withContext(Dispatchers.Main) for state updates"

# Metrics
duration: 15min
completed: 2026-04-05
---

# Phase 08 Plan 03: BridgeViewModel Rewiring Summary

**BridgeViewModel rewired from evaluateJavascript WebView path to direct bridgeAlgorithm.findBridge() coroutine call via Hilt injection, completing the Phase 8 native Kotlin bridge algorithm migration**

## Performance

- **Duration:** 15 min
- **Started:** 2026-04-05T05:39:00Z
- **Completed:** 2026-04-05T05:53:00Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- Created `BridgeAlgorithmModule` — Hilt marker module documenting that BridgeAlgorithm uses constructor injection
- Added `BridgeAlgorithm` to `BridgeViewModel` constructor; Hilt injects the singleton automatically
- Replaced `startBridge()` WebView evaluateJavascript path with `bridgeAlgorithm.findBridge()` coroutine call
- Removed `mainHandler`, `Handler`/`Looper` imports, `init` block `onStateChange` wiring, and `JSONObject` import
- Progress updates from `BridgeProgressInfo` now flow into `BridgeUiState.Searching` fields
- PathFound triggers `buildPlaylist`, `fetchArtistMetadata`, `resolveFamiliarity` via viewModelScope
- WebView and MeldBridgeInterface remain in codebase but are dormant for bridge search
- Updated `BridgeViewModelTest`: new `findBridge_calls_algorithm_not_webview` verify test, new `startBridge_rejects_when_already_searching`, deleted obsolete `startBridge_calls_evaluateJavascript`
- 18 active tests pass, 8 skipped (integration-only)

## Task Commits

1. **Task 1: BridgeAlgorithmModule + BridgeViewModel rewiring** — `d06db327` (feat)
2. **Task 2: BridgeViewModelTest update for Kotlin bridge path** — `1e4c5ca0` (test)

## Files Created/Modified

- `app/src/main/kotlin/com/metrolist/music/di/BridgeAlgorithmModule.kt` — Hilt marker module, `@Module @InstallIn(SingletonComponent::class)`
- `app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt` — Added BridgeAlgorithm constructor param, replaced startBridge() WebView path, removed mainHandler/init block/JSONObject
- `app/src/test/kotlin/com/metrolist/music/bridge/BridgeViewModelTest.kt` — buildViewModel() returns Pair<BridgeViewModel, BridgeAlgorithm>, new algorithm path tests, Dispatchers.setMain setup

## Decisions Made

- `BridgeAlgorithmModule` as marker only — Hilt's `@Singleton @Inject constructor` on `BridgeAlgorithm` and `KotlinBridgeCache` means no `@Provides` needed; module exists for discoverability and future extensibility
- `init` block removed entirely — the old pattern wired `meldBridgeInterface.onStateChange` to update `_uiState`; the new pattern updates `_uiState` directly from the coroutine result, eliminating the indirect callback chain
- `Dispatchers.setMain(StandardTestDispatcher())` in `@Before` — `viewModelScope.launch(Dispatchers.IO)` finishes and calls `withContext(Dispatchers.Main)` to update state; without `setMain`, JVM tests fail with `IllegalStateException: Dispatchers.Main was accessed when the platform dispatcher was absent`
- `buildForPath` stubbed to `emptyList()` in verify test — Mockito's default return for a `List<MediaItem>` suspend function is `null`, causing NPE when `buildPlaylist` calls `items.isNotEmpty()`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] withContext(Dispatchers.Main) fails in JVM unit tests without setMain**
- **Found during:** Task 2 (test execution)
- **Issue:** `startBridge()` now uses `withContext(Dispatchers.Main)` to update `_uiState` after the algorithm completes; JVM tests fail with `IllegalStateException: Dispatchers.Main was accessed when the platform dispatcher was absent`
- **Fix:** Added `Dispatchers.setMain(StandardTestDispatcher())` in `@Before` and `Dispatchers.resetMain()` in `@After`; replaced `delay(100)` with `advanceUntilIdle()` for coroutine advancement
- **Files modified:** `BridgeViewModelTest.kt`
- **Committed in:** `1e4c5ca0` (Task 2 commit)

**2. [Rule 1 - Bug] BridgePlaylistBuilder mock returns null causing NPE in buildPlaylist**
- **Found during:** Task 2 (test execution — `findBridge_calls_algorithm_not_webview`)
- **Issue:** When the algorithm mock returns `found=true`, `buildPlaylist()` is launched and calls `playlistBuilder.buildForPath(path)`; Mockito default for `List<MediaItem>` suspend return is `null`, causing NPE on `items.isNotEmpty()`
- **Fix:** Stubbed `mockPlaylistBuilder.buildForPath(any()).thenReturn(emptyList())` in the test
- **Files modified:** `BridgeViewModelTest.kt`
- **Committed in:** `1e4c5ca0` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (Rule 1 — test bugs from coroutine/mock environment)
**Impact on plan:** Test-only fixes. Production code implemented exactly as specified.

## Known Stubs

None — BridgeViewModel is fully wired to BridgeAlgorithm. No placeholders.

## Phase 8 Completion

This is the final plan in Phase 8. The complete migration from WebView evaluateJavascript to native Kotlin beam search is now complete:

- **Plan 01:** Last.fm getSimilarArtists API + Room cache entities + KotlinBridgeCache + LastFmRateLimiter
- **Plan 02:** BridgeAlgorithm — Kotlin port of eccopath/lib/bridgeCrawl.ts bidirectional beam search
- **Plan 03:** BridgeViewModel rewired to call BridgeAlgorithm.findBridge() directly via Hilt

---
*Phase: 08-native-kotlin-bridge-algorithm*
*Completed: 2026-04-05*

## Self-Check: PASSED

- FOUND: app/src/main/kotlin/com/metrolist/music/di/BridgeAlgorithmModule.kt
- FOUND: app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt
- FOUND: app/src/test/kotlin/com/metrolist/music/bridge/BridgeViewModelTest.kt
- FOUND: commit d06db327 (Task 1)
- FOUND: commit 1e4c5ca0 (Task 2)
