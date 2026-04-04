# Phase 3: JS Bridge - Context

**Gathered:** 2026-04-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Wire the Kotlin ↔ EccoPath JavaScript bridge so that: (1) Kotlin can invoke `findBridge(start, end)` in the WebView, (2) EccoPath can send bridge results and progress updates back to Kotlin via `@JavascriptInterface` methods, and (3) the round-trip works end-to-end with correct thread safety. This phase does NOT build UI for artist input or results display — it only establishes the communication channel that Phase 4+ will consume.

</domain>

<decisions>
## Implementation Decisions

### JS Invocation Strategy
- **D-01:** Kotlin invokes EccoPath via `WebView.evaluateJavascript()` calling a global wrapper function. Since `findBridge` is an ES module export (not globally accessible), a thin JS glue function must be injected or bundled that exposes `findBridge` to the global `window` scope so `evaluateJavascript` can call it.
- **D-02:** The glue function wires `onProgress` to call back into the Kotlin-registered `@JavascriptInterface` object, and calls `createPlaylist` with the final `BridgeResult` JSON when complete.

### Result Data Contract
- **D-03:** `MeldBridgeInterface.createPlaylist(json)` receives JSON matching EccoPath's `BridgeResult` shape: `{"found": true, "path": ["Artist A", "Artist B", ..., "Artist Z"]}`. The `path` array contains artist name strings — the same format EccoPath's `findBridge` already returns. No transformation needed on the JS side.
- **D-04:** Error case: `{"found": false, "path": []}` — Kotlin maps this to `BridgeUiState.Error`.

### Progress Relay
- **D-05:** A second `@JavascriptInterface` method (e.g., `MeldBridgeInterface.onProgress(json)`) relays `BridgeProgressInfo` during computation. JSON shape: `{"phase": "searching", "message": "Searching from Radiohead...", "progress": 0.5, "depth": 2, "maxDepth": 4}`. Kotlin maps this to `BridgeUiState.Searching` with user-visible progress text.
- **D-06:** Progress is best-effort — if some updates are missed due to rapid firing, the final result callback is authoritative.

### Thread Safety
- **D-07:** `@JavascriptInterface` methods execute on WebView's background thread. All callbacks must dispatch to the main thread via `Handler(Looper.getMainLooper()).post { }` before updating `BridgeViewModel`'s `MutableStateFlow`. This is the standard Android pattern for JS interface callbacks.
- **D-08:** Concurrent bridge prevention: `BridgeViewModel` tracks an `isRunning` flag. If a bridge is already in progress, new invocations are rejected (no queueing). This prevents duplicate WebView evaluations.

### Interface Registration
- **D-09:** `MeldBridgeInterface` is a Kotlin class registered on the singleton `@BridgeWebView` WebView via `addJavascriptInterface(interface, "MeldBridge")`. Registration happens once at WebView creation time in `BridgeModule` (or a dedicated setup step).
- **D-10:** The interface name `"MeldBridge"` is the JS-side global — EccoPath glue code calls `window.MeldBridge.createPlaylist(json)` and `window.MeldBridge.onProgress(json)`.

### Claude's Discretion
- Exact naming of the global JS wrapper function (e.g., `window.__eccoMeldBridge.startBridge(...)` or injecting into existing EccoPath scope)
- Whether the JS glue is a separate file bundled into assets or injected via `evaluateJavascript` at page load
- ProGuard keep rules for `@JavascriptInterface` methods (check if existing `proguard-rules.pro` already handles this)
- Whether `MeldBridgeInterface` is a standalone class or inner class of `BridgeViewModel`

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### EccoPath Bridge Algorithm
- `eccopath/lib/bridgeCrawl.ts` — `findBridge(start, end, options)` function signature, `BridgeResult` and `BridgeProgressInfo` interfaces. This is the JS function Kotlin must invoke.
- `eccopath/lib/types.ts` — `GraphNode`, `GraphLink` type definitions (context for understanding result data)
- `eccopath/components/search/BridgeSearch.tsx` — Reference implementation showing how `findBridge` is called from React: progress callback wiring, result handling, error path

### Existing Bridge Infrastructure (Phase 1)
- `app/src/main/kotlin/com/metrolist/music/di/BridgeModule.kt` — Singleton `@BridgeWebView` WebView with `WebViewAssetLoader`, loads EccoPath at `https://appassets.androidplatform.net/assets/eccopath/index.html`
- `app/src/main/kotlin/com/metrolist/music/di/Qualifiers.kt` — `@BridgeWebView` qualifier annotation

### Existing JS Interface Pattern
- `app/src/main/kotlin/com/metrolist/music/ui/screens/LoginScreen.kt` — Existing `@JavascriptInterface` + `addJavascriptInterface` usage pattern in this codebase
- `app/proguard-rules.pro` — Check for existing `@JavascriptInterface` keep rules

### Phase 2 UI Stubs
- `app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/BridgeScreen.kt` — `BridgeUiState` sealed class (Idle, Searching, PathFound, PlaylistReady, Error) that this phase's callbacks will drive
- `app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt` — `StateFlow<BridgeUiState>` that JS bridge callbacks update

### Project Constraints
- `.planning/PROJECT.md` — WebView-first approach, `addJavascriptInterface` decision
- `.planning/REQUIREMENTS.md` — BRDG-02 (bridge computation via WebView), BRDG-03 (JS bridge sends result to Kotlin)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `BridgeModule.kt` — Singleton WebView already created and loading EccoPath. JS interface registration can be added here or in a setup step that receives the WebView.
- `BridgeViewModel.kt` — Already has `MutableStateFlow<BridgeUiState>` initialized to `Idle`. Ready to receive state transitions from JS callbacks.
- `BridgeScreen.kt` — Already handles all 5 `BridgeUiState` variants with `when` dispatch. Progress and result display will "just work" once ViewModel state updates.
- `LoginScreen.kt` — Shows the `@JavascriptInterface` + `WebView.addJavascriptInterface()` pattern used in this codebase.

### Established Patterns
- Hilt `@Singleton` providers in `di/` modules for long-lived objects
- `@BridgeWebView` qualifier for WebView injection
- `Dispatchers.Main` for UI thread operations, `Handler(Looper.getMainLooper())` for posting from non-coroutine contexts
- `Timber.d()` / `Timber.tag()` for structured logging

### Integration Points
- `BridgeModule.kt` — Where WebView is created; JS interface registration goes here or in a post-creation hook
- `BridgeViewModel.kt` — Where bridge invocation is triggered and state updates land
- `BridgeScreen.kt` — Consumes ViewModel state (no changes needed in this phase unless adding a test trigger button)
- `app/src/main/assets/eccopath/` — Where the JS glue file would be placed if bundled as an asset

</code_context>

<specifics>
## Specific Ideas

- EccoPath's `findBridge` is an ES module export — it cannot be called directly via `evaluateJavascript`. A thin JS glue function is needed to bridge the module system gap. This glue must import/access `findBridge` and expose it globally.
- The `BridgeProgressInfo.phase` field ('analyzing', 'searching', 'deepening', 'pathfinding', 'fallback') maps naturally to user-visible status text in the `Searching` UI state.
- `BridgeResult.path` is just `string[]` of artist names — lightweight JSON, no complex nested objects. Serialization/deserialization is trivial.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 03-js-bridge*
*Context gathered: 2026-04-04*
