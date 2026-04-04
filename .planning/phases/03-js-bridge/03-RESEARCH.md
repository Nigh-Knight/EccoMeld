# Phase 03: JS Bridge - Research

**Researched:** 2026-04-04
**Domain:** Android WebView `@JavascriptInterface`, `evaluateJavascript`, JS module bridging, thread safety
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Kotlin invokes EccoPath via `WebView.evaluateJavascript()` calling a global wrapper function. Since `findBridge` is an ES module export (not globally accessible), a thin JS glue function must be injected or bundled that exposes `findBridge` to the global `window` scope so `evaluateJavascript` can call it.
- **D-02:** The glue function wires `onProgress` to call back into the Kotlin-registered `@JavascriptInterface` object, and calls `createPlaylist` with the final `BridgeResult` JSON when complete.
- **D-03:** `MeldBridgeInterface.createPlaylist(json)` receives JSON matching EccoPath's `BridgeResult` shape: `{"found": true, "path": ["Artist A", "Artist B", ..., "Artist Z"]}`. The `path` array contains artist name strings. No transformation needed on the JS side.
- **D-04:** Error case: `{"found": false, "path": []}` — Kotlin maps this to `BridgeUiState.Error`.
- **D-05:** A second `@JavascriptInterface` method `MeldBridgeInterface.onProgress(json)` relays `BridgeProgressInfo` during computation. JSON shape: `{"phase": "searching", "message": "Searching from Radiohead...", "progress": 0.5, "depth": 2, "maxDepth": 4}`. Kotlin maps this to `BridgeUiState.Searching`.
- **D-06:** Progress is best-effort — if some updates are missed due to rapid firing, the final result callback is authoritative.
- **D-07:** `@JavascriptInterface` methods execute on WebView's background thread. All callbacks must dispatch to the main thread via `Handler(Looper.getMainLooper()).post { }` before updating `BridgeViewModel`'s `MutableStateFlow`.
- **D-08:** `BridgeViewModel` tracks an `isRunning` flag. If a bridge is already in progress, new invocations are rejected (no queueing).
- **D-09:** `MeldBridgeInterface` is a Kotlin class registered on the singleton `@BridgeWebView` WebView via `addJavascriptInterface(interface, "MeldBridge")`. Registration happens once at WebView creation time in `BridgeModule` (or a dedicated setup step).
- **D-10:** The interface name `"MeldBridge"` is the JS-side global — EccoPath glue code calls `window.MeldBridge.createPlaylist(json)` and `window.MeldBridge.onProgress(json)`.

### Claude's Discretion

- Exact naming of the global JS wrapper function (e.g., `window.__eccoMeldBridge.startBridge(...)` or injecting into existing EccoPath scope)
- Whether the JS glue is a separate file bundled into assets or injected via `evaluateJavascript` at page load
- ProGuard keep rules for `@JavascriptInterface` methods (check if existing `proguard-rules.pro` already handles this)
- Whether `MeldBridgeInterface` is a standalone class or inner class of `BridgeViewModel`

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.

</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| BRDG-02 | Bridge computation runs via EccoPath WebView beam search when user taps "Bridge" | D-01, D-02, D-08: `evaluateJavascript` calling global glue, `isRunning` guard |
| BRDG-03 | JS bridge interface (MeldBridge) sends bridge path result from EccoPath to Kotlin | D-03, D-04, D-09, D-10: `@JavascriptInterface` registration, JSON contract, ProGuard keep rule |

</phase_requirements>

---

## Summary

Phase 3 wires the Kotlin ↔ JavaScript communication channel so that: (1) Kotlin can trigger EccoPath's `findBridge()` beam search via `WebView.evaluateJavascript()`, and (2) EccoPath can send `BridgeResult` JSON and `BridgeProgressInfo` JSON back to Kotlin via `@JavascriptInterface` methods. The round-trip must be thread-safe — all `@JavascriptInterface` callbacks arrive on a WebView background thread and must be re-dispatched to the main thread before touching `MutableStateFlow`.

The key technical challenge is that `findBridge` is an ES module export, not a global. The bundled `index.html` (a Next.js static export with `output: "export"`) renders client-side React. The glue layer must bridge the React/module world into `window` scope so `evaluateJavascript` can reach it. The NTransformSolver in this codebase provides an excellent second reference for the `@JavascriptInterface` + `evaluateJavascript` + `suspendCancellableCoroutine` pattern.

**Primary recommendation:** Implement `MeldBridgeInterface` as a standalone Kotlin class with `@JavascriptInterface` methods; inject a small JS glue string via `evaluateJavascript` after page load to wire `findBridge` into `window.MeldBridge`; add a single ProGuard `-keepclassmembers` rule for the new class.

---

## Standard Stack

### Core (all already in the project — no new dependencies)

| Component | What it is | Why it's the right choice |
|-----------|-----------|--------------------------|
| `android.webkit.JavascriptInterface` | Annotation that exposes Kotlin methods to JS | Required for `addJavascriptInterface`; already used in LoginScreen and NTransformSolver |
| `WebView.evaluateJavascript(script, callback)` | Kotlin → JS invocation with optional return value | API 19+; non-blocking; correct for calling into a loaded page |
| `Handler(Looper.getMainLooper()).post { }` | Thread dispatch from WebView JS thread to main thread | Canonical Android pattern for `@JavascriptInterface` callbacks; already used in this codebase (NTransformSolver uses `withContext(Dispatchers.Main)`) |
| `MutableStateFlow` | Reactive state in `BridgeViewModel` | Already in place from Phase 2; receives state transitions from JS callbacks |
| Hilt `@Singleton` + `@BridgeWebView` | DI qualifier for the singleton WebView | Already defined in `BridgeModule.kt` and `Qualifiers.kt` |
| `kotlinx.serialization` or `org.json.JSONObject` | JSON parsing | `JSONObject` is built into Android (no extra dep); sufficient for a flat `{"found": bool, "path": [...]}` structure |

### No new library dependencies needed

The entire JS bridge can be implemented with `android.webkit.*` APIs already on the platform and the existing Hilt/Kotlin/Coroutine stack.

---

## Architecture Patterns

### Recommended Project Structure (new files only)

```
app/src/main/kotlin/com/metrolist/music/
├── bridge/
│   └── MeldBridgeInterface.kt      # @JavascriptInterface class
├── viewmodels/
│   └── BridgeViewModel.kt          # Add startBridge(), isRunning, state transitions
├── di/
│   └── BridgeModule.kt             # Add addJavascriptInterface() call + onPageFinished glue injection
└── (no new screen files — Phase 4)

app/src/main/assets/eccopath/
└── (read-only — glue injected via evaluateJavascript, not as a separate asset file)

app/proguard-rules.pro              # Add -keepclassmembers for MeldBridgeInterface
```

### Pattern 1: `@JavascriptInterface` Registration (from existing codebase)

**What:** Register a Kotlin object as a named JS global on the WebView before or at page load time.

**When to use:** Always at WebView creation — once, not per-navigation.

**Existing pattern from `NTransformSolver.kt`:**
```kotlin
// Source: app/src/main/kotlin/com/metrolist/music/utils/cipher/NTransformSolver.kt
wv.addJavascriptInterface(this, "NTransformBridge")
```

**Existing pattern from `LoginScreen.kt`:**
```kotlin
// Source: app/src/main/kotlin/com/metrolist/music/ui/screens/LoginScreen.kt
addJavascriptInterface(object {
    @JavascriptInterface
    fun onRetrieveVisitorData(newVisitorData: String?) { ... }
}, "Android")
```

**For Phase 3:** `addJavascriptInterface(meldBridgeInterface, "MeldBridge")` in `BridgeModule.kt`.

### Pattern 2: JS Glue Injection via `evaluateJavascript` After Page Load

**What:** After the EccoPath page finishes loading, inject a small JS string that:
1. Imports or accesses `findBridge` from the React app's module system
2. Exposes a `window.startBridge(start, end)` function that calls `findBridge` and routes callbacks to `window.MeldBridge`

**When to use:** In `onPageFinished` callback of the `WebViewClient` already in `BridgeModule.kt`.

**The critical module-system problem:** EccoPath's `findBridge` is compiled into Next.js chunks as an ES module. It is NOT available as `window.findBridge`. The React app itself calls it from within its module closure (see `BridgeSearch.tsx` line 81: `const result = await findBridge(seedArtist, targetArtist, { onProgress })`).

**Two viable approaches for exposing `findBridge` globally (Claude's Discretion):**

| Approach | Mechanism | Complexity | Recommendation |
|----------|-----------|------------|----------------|
| **A: Inject glue via `evaluateJavascript` that accesses Next.js chunk registry** | Next.js Turbopack/webpack exposes modules via `self.__next_f` or module registry; glue script navigates the registry to find the `bridgeCrawl` module | HIGH — Next.js chunk registry format is internal and changes with builds | Not recommended |
| **B: Add a thin `window` export to EccoPath source** | Add a `useEffect` in `app/page.tsx` (already a `'use client'` component) that writes `window.__eccoFindBridge = findBridge` after the module loads | LOW — direct and explicit | Recommended |
| **C: Add a dedicated `bridge-api.ts` entry file** | A small TS file `lib/bridge-api.ts` that imports `findBridge` and writes to `window`; included via a `<script>` tag in `layout.tsx` | LOW-MEDIUM — clean separation | Also viable |

**Recommendation: Approach B** — add a `useEffect` in `eccopath/app/page.tsx` (already client component) that runs once on mount:

```typescript
// In eccopath/app/page.tsx useEffect (runs once after hydration)
useEffect(() => {
  // Expose findBridge to window for Android MeldBridge integration
  // @ts-ignore
  window.__eccoFindBridge = findBridge
}, [])
```

Then the Kotlin glue injected via `evaluateJavascript` after page load:

```javascript
// Injected by Kotlin in onPageFinished:
window.startBridge = async function(start, end) {
  if (!window.__eccoFindBridge) {
    window.MeldBridge.onProgress(JSON.stringify({
      phase: 'fallback',
      message: 'Bridge not ready',
      progress: 0
    }));
    return;
  }
  try {
    const result = await window.__eccoFindBridge(start, end, {
      onProgress: function(info) {
        window.MeldBridge.onProgress(JSON.stringify(info));
      }
    });
    window.MeldBridge.createPlaylist(JSON.stringify(result));
  } catch(e) {
    window.MeldBridge.createPlaylist(JSON.stringify({found: false, path: []}));
  }
};
```

### Pattern 3: Thread-Safe Callback Dispatch

**What:** `@JavascriptInterface` methods are called on a WebView internal thread (not main thread, not a coroutine). Must dispatch to main thread before mutating StateFlow.

**Existing pattern from `NTransformSolver.kt`:**
```kotlin
// Source: NTransformSolver.kt — uses withContext(Dispatchers.Main) for UI-thread work
// For @JavascriptInterface (non-coroutine context), use Handler directly:
```

**For Phase 3 — the standard pattern for `@JavascriptInterface` callbacks:**
```kotlin
// Source: Android docs — standard @JavascriptInterface thread dispatch
@JavascriptInterface
fun createPlaylist(json: String) {
    Timber.tag("MeldBridge").d("createPlaylist: $json")
    Handler(Looper.getMainLooper()).post {
        // safe to update MutableStateFlow here
        _uiState.value = parseBridgeResult(json)
    }
}
```

**Why not `viewModelScope.launch`:** `@JavascriptInterface` methods are plain Java methods called by reflection — they have no coroutine context. `Handler(Looper.getMainLooper())` is the correct, non-coroutine way to dispatch to main thread from any thread.

### Pattern 4: `evaluateJavascript` Invocation from ViewModel

**What:** `evaluateJavascript` must be called on the main thread. ViewModel calls it through the injected WebView.

```kotlin
// In BridgeViewModel:
fun startBridge(startArtist: String, endArtist: String) {
    if (_isRunning.value) return
    _isRunning.value = true
    _uiState.value = BridgeUiState.Searching()

    // evaluateJavascript must run on main thread
    // WebView is a singleton injected via @BridgeWebView
    val script = "window.startBridge(${escapeJson(startArtist)}, ${escapeJson(endArtist)})"
    webView.evaluateJavascript(script, null)
}
```

**Important:** `evaluateJavascript` is an async call — it returns immediately and the JS runs asynchronously. The callback parameter receives the JS return value (not needed here — results come back via `@JavascriptInterface`).

### Anti-Patterns to Avoid

- **Calling `evaluateJavascript` from a background thread:** It must be called from the main thread. If called from a non-main coroutine, wrap in `withContext(Dispatchers.Main)`.
- **Using `loadUrl("javascript:...")` instead of `evaluateJavascript`:** `loadUrl("javascript:...")` does not return values and has encoding issues with complex strings. `evaluateJavascript` is the correct API for SDK 19+.
- **Holding a reference to `BridgeViewModel` from `MeldBridgeInterface`:** Creates a retain cycle risk. Use a `WeakReference<BridgeViewModel>` or use a `Handler` callback lambda captured at construction time.
- **Forgetting ProGuard:** R8 will rename `@JavascriptInterface` methods in release builds unless kept. The existing `proguard-rules.pro` has a pattern for `NTransformSolver$SolverWebView` — the same pattern is needed for `MeldBridgeInterface`.
- **Injecting the glue script before page load:** The glue must be injected in `onPageFinished`, not at WebView creation. The React app needs to have mounted and called `useEffect` before `window.__eccoFindBridge` exists.
- **Assuming `window.__eccoFindBridge` is ready immediately after `onPageFinished`:** React's `useEffect` runs after the browser paint. Add a short retry or readiness check in the glue, or have the glue defer until `window.__eccoFindBridge` is truthy.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Thread dispatch from JS callback to main | Custom thread pool, coroutine scope | `Handler(Looper.getMainLooper()).post { }` | Standard Android pattern; one-liner; no lifecycle concerns |
| JSON parsing of `BridgeResult` | Manual string parsing | `org.json.JSONObject` (built-in) | `BridgeResult` is flat: `{"found": bool, "path": [...]}` — 3 lines of code with JSONObject, no extra dep |
| Concurrency guard | Semaphores, complex locking | `AtomicBoolean` or `MutableStateFlow<Boolean>` checked in `startBridge()` | The `isRunning` flag is a simple boolean guard — no concurrency primitives needed beyond a `StateFlow` |

**Key insight:** This phase is plumbing, not logic. The complexity budget should be spent on correctness (thread safety, ProGuard, page-load timing) not abstractions.

---

## Common Pitfalls

### Pitfall 1: ProGuard strips `@JavascriptInterface` methods in release builds

**What goes wrong:** Release APK works in debug but `createPlaylist` and `onProgress` methods are silently removed or renamed by R8. WebView calls them but nothing happens.

**Why it happens:** R8 does not know that `@JavascriptInterface` methods are called by reflection from the WebView JS engine. Without a keep rule, it optimizes them away.

**How to avoid:** Add to `proguard-rules.pro`:
```
-keepclassmembers class com.metrolist.music.bridge.MeldBridgeInterface {
    @android.webkit.JavascriptInterface public *;
}
```
The exact pattern is already present in `proguard-rules.pro` for `NTransformSolver$SolverWebView` — copy and adapt it.

**Warning signs:** Bridge works in debug builds but silently fails in release.

### Pitfall 2: `@JavascriptInterface` callback on wrong thread crashes StateFlow update

**What goes wrong:** `_uiState.value = ...` is set from the WebView JS thread. On some Android versions this causes `CalledFromWrongThreadException` or silent corruption.

**Why it happens:** `MutableStateFlow.value` setter is thread-safe for reads/writes but the ViewModel's lifecycle is bound to the main thread. Compose collection (`collectAsState`) may react from the main thread and expect updates to be coherent.

**How to avoid:** Always wrap `_uiState.value` assignments in `Handler(Looper.getMainLooper()).post { }` inside every `@JavascriptInterface` method. No exceptions.

**Warning signs:** Occasional crash in `MutableStateFlow` or Compose recomposition with `StrictMode` violations.

### Pitfall 3: `window.__eccoFindBridge` not yet defined when glue script runs

**What goes wrong:** `onPageFinished` fires when the HTML/JS has loaded, but React's `useEffect` (where `window.__eccoFindBridge = findBridge` is set) runs after the first browser paint. The injected glue script runs immediately in `onPageFinished` and `window.__eccoFindBridge` is still `undefined`.

**Why it happens:** Next.js static export hydrates the React tree client-side after the HTML shell loads. `onPageFinished` fires at HTML load, not after React hydration completes.

**How to avoid:** Two options:
1. The glue script checks for readiness: `if (typeof window.__eccoFindBridge === 'function') { ... } else { setTimeout(tryAgain, 100); }` — poll until ready.
2. Or inject the glue later, triggered by a readiness signal from EccoPath itself (e.g., EccoPath calls `window.MeldBridge.onReady()` from its `useEffect`). This is cleaner but requires a third `@JavascriptInterface` method.

**Recommendation:** Use option 2 — add `onBridgeReady()` to `MeldBridgeInterface`, have EccoPath's `useEffect` call `window.MeldBridge?.onBridgeReady?.()` after setting `window.__eccoFindBridge`. Kotlin then injects the glue in the `onBridgeReady` callback. This is race-condition-free.

**Warning signs:** `startBridge` silently does nothing; `createPlaylist` never fires.

### Pitfall 4: `evaluateJavascript` called from non-main thread

**What goes wrong:** `IllegalStateException: Only the original thread that created a view hierarchy can touch its views.`

**Why it happens:** If `BridgeViewModel.startBridge()` is called from a background coroutine dispatcher, `webView.evaluateJavascript(...)` throws.

**How to avoid:** In `startBridge()`, wrap the `evaluateJavascript` call in `withContext(Dispatchers.Main) { ... }` if called from a suspend function, or ensure the call site is always on the main thread.

### Pitfall 5: Artist name strings not escaped before injection into JS

**What goes wrong:** An artist name containing a single quote (e.g., `Lil' Kim`) or backslash breaks the injected `window.startBridge('Lil' Kim', ...)` call and causes a JS syntax error.

**Why it happens:** Artist names are free-form strings concatenated directly into a JS expression string.

**How to avoid:** Use `JSONObject.quote(artistName)` (built-in Android API) to produce a properly quoted JSON string, or manually escape `'`, `"`, `\`, and newlines. NTransformSolver has a reference `escapeJs()` private function for this exact purpose.

---

## Code Examples

Verified from existing codebase sources:

### `@JavascriptInterface` annotation + registration (from NTransformSolver.kt)
```kotlin
// Source: app/src/main/kotlin/com/metrolist/music/utils/cipher/NTransformSolver.kt
wv.addJavascriptInterface(this, "NTransformBridge")

@JavascriptInterface
fun onReady(status: String) {
    nFunctionAvailable = status.startsWith("ok")
    initCont.resume(this)
}

@JavascriptInterface
fun onResult(result: String) {
    transformCont?.resume(result)
    transformCont = null
}
```

### `evaluateJavascript` invocation (from NTransformSolver.kt)
```kotlin
// Source: app/src/main/kotlin/com/metrolist/music/utils/cipher/NTransformSolver.kt
wv.evaluateJavascript("_doTransform('$escaped')", null)
```

### `onPageFinished` hook (from BridgeModule.kt)
```kotlin
// Source: app/src/main/kotlin/com/metrolist/music/di/BridgeModule.kt
override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)
    Timber.d("BridgeModule: EccoPath loaded at %s", url)
    // Phase 3: inject glue here (or wait for onBridgeReady callback)
}
```

### `BridgeUiState` sealed class (from BridgeScreen.kt)
```kotlin
// Source: app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/BridgeScreen.kt
sealed class BridgeUiState {
    @Immutable object Idle : BridgeUiState()
    @Immutable data class Searching(val foundHops: Int = 0, val totalHops: Int = 0) : BridgeUiState()
    @Immutable data class PathFound(val path: List<String>) : BridgeUiState()
    @Immutable data class PlaylistReady(val path: List<String>, val nowPlayingIndex: Int = 0) : BridgeUiState()
    @Immutable data class Error(val message: String) : BridgeUiState()
}
```

### `BridgeResult` JSON contract (from bridgeCrawl.ts)
```typescript
// Source: eccopath/lib/bridgeCrawl.ts
export interface BridgeResult {
  found: boolean
  path: string[]   // artist name strings
}

export interface BridgeProgressInfo {
  phase: 'analyzing' | 'searching' | 'deepening' | 'pathfinding' | 'fallback'
  message: string
  progress: number
  depth?: number
  maxDepth?: number
}
```

### ProGuard keep rule pattern (from proguard-rules.pro)
```
# Source: app/proguard-rules.pro — existing pattern for NTransformSolver
-keepclassmembers class com.metrolist.music.utils.cipher.NTransformSolver$SolverWebView {
    @android.webkit.JavascriptInterface public *;
}

# Phase 3 — add for MeldBridgeInterface:
-keepclassmembers class com.metrolist.music.bridge.MeldBridgeInterface {
    @android.webkit.JavascriptInterface public *;
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `loadUrl("javascript:...")` | `evaluateJavascript(script, callback)` | API 19 (2013) | `evaluateJavascript` handles encoding correctly, returns JS result asynchronously |
| `addWebMessageListener` | `addJavascriptInterface` (chosen for MVP) | API 23 (2015) | Decision D-09: `addJavascriptInterface` chosen for simplicity; `addWebMessageListener` is the modern alternative but requires HTTPS origin and more setup |

**Deprecated/outdated:**
- `loadUrl("javascript:...")`: Still works but deprecated for data passing. Use `evaluateJavascript` for anything that carries data.
- `allowFileAccess = true` for JS interface in file:// origin: Not applicable here — WebViewAssetLoader already provides HTTPS origin (`https://appassets.androidplatform.net`).

---

## Open Questions

1. **`onBridgeReady` vs. polling for `window.__eccoFindBridge`**
   - What we know: React `useEffect` runs after paint, `onPageFinished` fires at HTML load — there is a race.
   - What's unclear: How long does Next.js hydration take on mid-range Android? Could be 200ms–2000ms.
   - Recommendation: Implement `onBridgeReady()` on `MeldBridgeInterface` and call it from EccoPath's `useEffect`. This is deterministic. Polling is a fallback if modifying EccoPath is blocked.

2. **EccoPath `page.tsx` modification scope**
   - What we know: `page.tsx` is a `'use client'` component. Adding `useEffect(() => { window.__eccoFindBridge = findBridge }, [])` and `window.MeldBridge?.onBridgeReady?.()` is a minimal, non-breaking change.
   - What's unclear: Is the EccoPath submodule's `page.tsx` considered in-scope for this phase? (It is — D-01 explicitly says a glue function must be added.)
   - Recommendation: Treat EccoPath `page.tsx` + `bridgeCrawl.ts` as in-scope for the glue export. The EccoPath rebuild is handled by the Gradle `buildEccoPath` task from Phase 1.

3. **`isRunning` state location**
   - What we know: D-08 says `BridgeViewModel` tracks `isRunning`. The existing `BridgeViewModel` has only `uiState: StateFlow<BridgeUiState>`.
   - What's unclear: Should `isRunning` be a separate `StateFlow<Boolean>` or derived from `uiState` (i.e., `uiState is Searching`)?
   - Recommendation: Derive from `uiState` — `val isRunning: Boolean get() = _uiState.value is BridgeUiState.Searching`. Avoids dual state that could diverge.

---

## Environment Availability

This phase is Kotlin + WebView + EccoPath source modifications. No external tools beyond the existing Android build environment.

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| Android SDK / WebView | `@JavascriptInterface`, `evaluateJavascript` | Yes | API 26+ (minSdk) | — |
| Hilt / KSP | `@BridgeWebView` injection into ViewModel | Yes | Hilt 2.59.1 | — |
| EccoPath submodule source | Adding `window.__eccoFindBridge` export | Yes | Present at `eccopath/` | — |
| Gradle `buildEccoPath` task | Rebuild bundled assets after EccoPath source edit | Yes (Phase 1) | — | — |
| `org.json.JSONObject` | Parse `BridgeResult` JSON | Yes (built-in Android) | SDK 26+ | — |

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 4 (configured in `app/build.gradle.kts` via `testInstrumentationRunner`) |
| Config file | `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` in `app/build.gradle.kts` |
| Quick run command | `./gradlew app:testFossDebugUnitTest --tests "com.metrolist.music.bridge.*"` |
| Full suite command | `./gradlew app:testFossDebugUnitTest` |

**Note:** No existing unit test files were found in `app/src/test/`. The project currently has zero local unit tests — test infrastructure exists at the framework/runner level only.

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BRDG-02 | `startBridge()` rejects when `isRunning = true` | unit | `./gradlew app:testFossDebugUnitTest --tests "*.BridgeViewModelTest.startBridge_rejects_when_running"` | ❌ Wave 0 |
| BRDG-02 | `evaluateJavascript` is called with correct artist names (escaped) | unit (mock WebView) | `./gradlew app:testFossDebugUnitTest --tests "*.BridgeViewModelTest.startBridge_calls_evaluateJavascript"` | ❌ Wave 0 |
| BRDG-03 | `createPlaylist(json)` with `found=true` transitions state to `PathFound` | unit | `./gradlew app:testFossDebugUnitTest --tests "*.MeldBridgeInterfaceTest.createPlaylist_valid_json_emits_PathFound"` | ❌ Wave 0 |
| BRDG-03 | `createPlaylist(json)` with `found=false` transitions state to `Error` | unit | `./gradlew app:testFossDebugUnitTest --tests "*.MeldBridgeInterfaceTest.createPlaylist_not_found_emits_Error"` | ❌ Wave 0 |
| BRDG-03 | `onProgress(json)` transitions state to `Searching` | unit | `./gradlew app:testFossDebugUnitTest --tests "*.MeldBridgeInterfaceTest.onProgress_emits_Searching"` | ❌ Wave 0 |
| BRDG-03 | Thread dispatch: callbacks reach main thread (Timber log + state visible) | manual smoke | Logcat during manual test with known artist pair | — |

### Sampling Rate

- **Per task commit:** `./gradlew app:testFossDebugUnitTest --tests "com.metrolist.music.bridge.*" -x lint`
- **Per wave merge:** `./gradlew app:testFossDebugUnitTest`
- **Phase gate:** Full unit test suite green + manual smoke test visible in Logcat before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `app/src/test/kotlin/com/metrolist/music/bridge/BridgeViewModelTest.kt` — covers BRDG-02 (startBridge guard, evaluateJavascript call)
- [ ] `app/src/test/kotlin/com/metrolist/music/bridge/MeldBridgeInterfaceTest.kt` — covers BRDG-03 (createPlaylist JSON parsing, onProgress, thread dispatch)
- [ ] Test source set directory: `app/src/test/kotlin/com/metrolist/music/bridge/` — create directory
- [ ] `testImplementation(libs.junit)` — confirm it's wired in `app/build.gradle.kts` (currently only instrumentation runner is configured; local unit test dep may be missing)

---

## Project Constraints (from CLAUDE.md)

| Constraint | Implication for this phase |
|------------|---------------------------|
| Platform: Android only, SDK 26+ | All WebView APIs used (addJavascriptInterface, evaluateJavascript) are available at API 19+ — well within minSdk 26 |
| Bridge algorithm: WebView + JS bridge for MVP | Confirmed — this is exactly what phase 3 implements |
| Playback: Must use existing Media3/ExoPlayer stack | Not touched in this phase |
| Package name: `com.metrolist.music` | New class goes in `com.metrolist.music.bridge.MeldBridgeInterface` |
| Kotlin 2.3.10, JVM target 21 | No impact on WebView/JavascriptInterface APIs |
| Dagger Hilt 2.59.1 | `MeldBridgeInterface` should be injectable (constructed by Hilt with WebView dependency) |
| Timber for logging | `Timber.tag("MeldBridge").d(...)` for all bridge callbacks |
| No ktlint/detekt | No formatter enforcement; follow existing code style |
| Dispatchers: `Handler(Looper.getMainLooper())` for non-coroutine main-thread dispatch | Confirmed — this is the required pattern for `@JavascriptInterface` callbacks |
| ProGuard/R8 minification enabled for release | ProGuard keep rule for `MeldBridgeInterface` is REQUIRED |

---

## Sources

### Primary (HIGH confidence — read directly from source)

- `eccopath/lib/bridgeCrawl.ts` — `BridgeResult` interface, `BridgeProgressInfo` interface, `findBridge` function signature verified
- `eccopath/components/search/BridgeSearch.tsx` — reference for how `findBridge` is called with `onProgress` callback
- `app/src/main/kotlin/com/metrolist/music/utils/cipher/NTransformSolver.kt` — verified `@JavascriptInterface` + `evaluateJavascript` + `addJavascriptInterface` pattern in this codebase
- `app/src/main/kotlin/com/metrolist/music/ui/screens/LoginScreen.kt` — verified `addJavascriptInterface(object { ... }, "Android")` pattern
- `app/src/main/kotlin/com/metrolist/music/di/BridgeModule.kt` — verified singleton WebView setup, `onPageFinished` hook location
- `app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/BridgeScreen.kt` — verified `BridgeUiState` sealed class definitions
- `app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt` — verified current state: only `MutableStateFlow<BridgeUiState>`, no `startBridge()` yet
- `app/proguard-rules.pro` — verified existing `@JavascriptInterface` keep pattern for `NTransformSolver$SolverWebView`
- `app/src/main/assets/eccopath/index.html` — confirmed Next.js static export, confirmed `window.__eccoFindBridge` does NOT currently exist

### Secondary (MEDIUM confidence)

- Android documentation on `@JavascriptInterface` thread behavior: callbacks run on the WebView's JS thread, not main thread — consistent with NTransformSolver's use of `withContext(Dispatchers.Main)` to re-dispatch

---

## Metadata

**Confidence breakdown:**

- Standard stack: HIGH — all verified from existing source files; no new dependencies
- Architecture patterns: HIGH — NTransformSolver and LoginScreen provide direct in-codebase precedents
- JS module exposure strategy: MEDIUM — Approach B (`useEffect` + `window.__eccoFindBridge`) is clean but requires EccoPath rebuild; the readiness timing issue (Pitfall 3) is the main uncertainty
- Pitfalls: HIGH — ProGuard and threading pitfalls verified from codebase evidence

**Research date:** 2026-04-04
**Valid until:** 2026-06-01 (WebView APIs are stable; Next.js chunk format may change on submodule update)
