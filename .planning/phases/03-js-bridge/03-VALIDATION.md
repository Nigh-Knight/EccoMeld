---
phase: 03
slug: js-bridge
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-04
---

# Phase 03 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 (configured in `app/build.gradle.kts` via `testInstrumentationRunner`) |
| **Config file** | `app/build.gradle.kts` — `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` |
| **Quick run command** | `./gradlew app:testFossDebugUnitTest --tests "com.metrolist.music.bridge.*" -x lint` |
| **Full suite command** | `./gradlew app:testFossDebugUnitTest` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew app:testFossDebugUnitTest --tests "com.metrolist.music.bridge.*" -x lint`
- **After every plan wave:** Run `./gradlew app:testFossDebugUnitTest`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 03-01-01 | 01 | 0 | BRDG-02 | unit | `./gradlew app:testFossDebugUnitTest --tests "*.BridgeViewModelTest.startBridge_rejects_when_running"` | ❌ W0 | ⬜ pending |
| 03-01-02 | 01 | 0 | BRDG-02 | unit | `./gradlew app:testFossDebugUnitTest --tests "*.BridgeViewModelTest.startBridge_calls_evaluateJavascript"` | ❌ W0 | ⬜ pending |
| 03-01-03 | 01 | 0 | BRDG-03 | unit | `./gradlew app:testFossDebugUnitTest --tests "*.MeldBridgeInterfaceTest.createPlaylist_valid_json_emits_PathFound"` | ❌ W0 | ⬜ pending |
| 03-01-04 | 01 | 0 | BRDG-03 | unit | `./gradlew app:testFossDebugUnitTest --tests "*.MeldBridgeInterfaceTest.createPlaylist_not_found_emits_Error"` | ❌ W0 | ⬜ pending |
| 03-01-05 | 01 | 0 | BRDG-03 | unit | `./gradlew app:testFossDebugUnitTest --tests "*.MeldBridgeInterfaceTest.onProgress_emits_Searching"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `app/src/test/kotlin/com/metrolist/music/bridge/` — create test source directory
- [ ] `app/src/test/kotlin/com/metrolist/music/bridge/BridgeViewModelTest.kt` — stubs for BRDG-02
- [ ] `app/src/test/kotlin/com/metrolist/music/bridge/MeldBridgeInterfaceTest.kt` — stubs for BRDG-03
- [ ] Confirm `testImplementation(libs.junit)` is wired in `app/build.gradle.kts`

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Thread dispatch: callbacks reach main thread | BRDG-03 | Requires actual WebView runtime on device | Run app, trigger bridge with known artist pair, verify Timber logs in Logcat show result on main thread |
| Bridge result JSON visible in Logcat | BRDG-03 | Requires device/emulator runtime | Connect Logcat, trigger bridge, verify `BridgeResult` JSON appears |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
