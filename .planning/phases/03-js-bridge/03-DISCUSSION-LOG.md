# Phase 3: JS Bridge - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-04
**Phase:** 03-js-bridge
**Areas discussed:** JS invocation strategy, Result data contract, Thread safety approach, Progress relay
**Mode:** Auto (all areas auto-selected with recommended defaults)

---

## JS Invocation Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| evaluateJavascript with global wrapper | Inject/bundle a thin JS glue that exposes findBridge globally | ✓ |
| evaluateJavascript calling module directly | Call findBridge as ES module — won't work without glue | |
| WebMessageListener postMessage | Use newer WebMessageListener API instead of evaluateJavascript | |

**User's choice:** [auto] evaluateJavascript with global wrapper (recommended default)
**Notes:** findBridge is an ES module export, not globally accessible. A glue function is the standard approach for invoking module code from evaluateJavascript.

---

## Result Data Contract

| Option | Description | Selected |
|--------|-------------|----------|
| Match BridgeResult exactly | `{found, path}` — no transformation | ✓ |
| Extended result with metadata | Add timing, hop count, intermediate scores | |
| Kotlin-friendly shape | Flatten to separate success/error payloads | |

**User's choice:** [auto] Match BridgeResult exactly (recommended default)
**Notes:** BridgeResult is already minimal (`{found: boolean, path: string[]}`). No reason to transform — Kotlin can parse this trivially with kotlinx.serialization.

---

## Progress Relay

| Option | Description | Selected |
|--------|-------------|----------|
| Relay via second @JavascriptInterface method | onProgress(json) callback alongside createPlaylist | ✓ |
| No progress relay | Only send final result | |
| Poll from Kotlin side | Periodically evaluateJavascript to check status | |

**User's choice:** [auto] Relay via second @JavascriptInterface method (recommended default)
**Notes:** Enables the Searching UI state to show meaningful progress ("Found 3 of 6 hops..."). Matches BRDG-04 requirement for loading feedback (Phase 4 will consume this).

---

## Thread Safety Approach

| Option | Description | Selected |
|--------|-------------|----------|
| Handler(Looper.getMainLooper()).post | Standard Android pattern for dispatching to main thread | ✓ |
| withContext(Dispatchers.Main) | Coroutine-based main thread dispatch | |
| runOnUiThread | Activity method — requires Activity reference | |

**User's choice:** [auto] Handler(Looper.getMainLooper()).post (recommended default)
**Notes:** @JavascriptInterface methods run on WebView's background thread, not a coroutine context. Handler.post is the most direct and reliable dispatch mechanism. Concurrent bridge prevention via isRunning flag in ViewModel.

---

## Claude's Discretion

- JS glue implementation details (bundled asset vs injected script)
- MeldBridgeInterface class structure (standalone vs inner class)
- ProGuard keep rules for @JavascriptInterface
- Global wrapper function naming

## Deferred Ideas

None — discussion stayed within phase scope
