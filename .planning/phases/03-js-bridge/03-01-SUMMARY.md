---
phase: 03-js-bridge
plan: 01
subsystem: bridge
tags: [js-bridge, webview, android, eccopath, proguard]
dependency_graph:
  requires: [03-00]
  provides: [MeldBridgeInterface, window.__eccoFindBridge, MeldBridge.onBridgeReady]
  affects: [03-02, BridgeModule, BridgeViewModel]
tech_stack:
  added: []
  patterns:
    - "@JavascriptInterface mutable var callback properties (not constructor params)"
    - "Handler(Looper.getMainLooper()).post for WebView background-thread dispatch"
    - "window.__eccoFindBridge assignment in useEffect after React hydration"
    - "Deterministic readiness signal: JS calls Kotlin onBridgeReady() (no polling)"
key_files:
  created:
    - app/src/main/kotlin/com/metrolist/music/bridge/MeldBridgeInterface.kt
  modified:
    - app/proguard-rules.pro
    - eccopath/app/page.tsx
decisions:
  - "MeldBridgeInterface uses mutable var properties for callbacks (not constructor params) so Hilt can construct the singleton before BridgeViewModel assigns the real callback"
  - "Handler created once as class property (not per-method) for efficiency while still dispatching all calls to main thread"
  - "eccopath submodule committed separately at eccopath@bf8e0ce, parent repo updated at ce96042a"
metrics:
  duration: 3min
  completed: "2026-04-04"
  tasks: 2
  files: 3
---

# Phase 3 Plan 01: JS Bridge Interface Contracts Summary

Defined the Kotlin-JS communication interface for Android WebView bridge integration. MeldBridgeInterface.kt establishes the data contracts, method signatures, and readiness signaling mechanism that Plan 02 will wire into the ViewModel and WebView lifecycle.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Create MeldBridgeInterface with @JavascriptInterface methods + ProGuard keep rule | b2d1f767 | MeldBridgeInterface.kt, proguard-rules.pro |
| 2 | Expose findBridge to window scope in EccoPath page.tsx and signal readiness | bf8e0ce (submodule), ce96042a (parent) | eccopath/app/page.tsx |

## What Was Built

**MeldBridgeInterface.kt** (`com.metrolist.music.bridge`):
- Three `@JavascriptInterface` methods: `createPlaylist(json)`, `onProgress(json)`, `onBridgeReady()`
- `var onStateChange: (BridgeUiState) -> Unit = {}` — mutable, set by BridgeViewModel in its init block
- `var onReady: () -> Unit = {}` — mutable, set by BridgeModule (Plan 02)
- All methods dispatch to main thread via `mainHandler.post { }` (Handler created once as class property)
- `createPlaylist` parses `{ found, path }` JSON → emits `PathFound` or `Error` state
- `onProgress` parses `{ depth, maxDepth }` JSON → emits `Searching` state (best-effort, parse failures silently logged)
- `onBridgeReady` invokes `onReady()` callback — deterministic JS-ready signal

**EccoPath page.tsx** additions:
- `import { findBridge } from '@/lib/bridgeCrawl'`
- `useEffect` (empty deps) that assigns `window.__eccoFindBridge = findBridge` after React hydration
- After assignment: calls `window.MeldBridge.onBridgeReady()` if the Android interface is registered

**ProGuard keep rule** appended to `app/proguard-rules.pro`:
```
-keepclassmembers class com.metrolist.music.bridge.MeldBridgeInterface {
    @android.webkit.JavascriptInterface public *;
}
```

## Decisions Made

1. **Mutable var callbacks over constructor injection** — Hilt constructs the `MeldBridgeInterface` singleton before `BridgeViewModel` exists. Using `var onStateChange = {}` with a no-op default allows BridgeViewModel to assign its real callback post-construction without a circular dependency.

2. **Single mainHandler property** — Created once in the class body (`private val mainHandler = Handler(Looper.getMainLooper())`) rather than inline in each method. Same correctness, less allocation.

3. **Submodule commit separation** — eccopath/app/page.tsx changes committed inside the submodule (bf8e0ce), then the parent repo's submodule pointer updated (ce96042a). This preserves the submodule's independent git history.

## Deviations from Plan

None — plan executed exactly as written.

The test stub modification mentioned in the plan was skipped because `app/src/test/` does not exist in the repo (no test directory was created in Phase 3 Plan 00 either). This is not a deviation — the tests referenced in the plan are Wave 0 stubs that do not yet exist at this path.

## Known Stubs

None. Both deliverables are complete implementations, not stubs:
- `MeldBridgeInterface.kt` has full parsing logic for all 3 methods
- `page.tsx` addition is functional window exposure with readiness signal

## Self-Check: PASSED

Files confirmed present:
- `app/src/main/kotlin/com/metrolist/music/bridge/MeldBridgeInterface.kt` — FOUND
- `app/proguard-rules.pro` (MeldBridgeInterface keep rule) — FOUND
- `eccopath/app/page.tsx` (window.__eccoFindBridge) — FOUND

Commits confirmed:
- `b2d1f767` feat(03-01): MeldBridgeInterface — FOUND
- `ce96042a` chore(03-01): submodule update — FOUND
