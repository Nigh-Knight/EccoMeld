---
phase: 8
slug: native-kotlin-bridge-algorithm
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-05
---

# Phase 8 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 + mockito-kotlin 5.4.0 |
| **Config file** | None — standard Android Gradle test discovery |
| **Quick run command** | `./gradlew :app:testDebugUnitTest --tests "*.bridge.*"` |
| **Full suite command** | `./gradlew :app:testDebugUnitTest` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:testDebugUnitTest --tests "*.bridge.*"`
- **After every plan wave:** Run `./gradlew :app:testDebugUnitTest`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 08-01-01 | 01 | 0 | BRDG-02 | unit | `./gradlew :app:testDebugUnitTest --tests "*BridgeAlgorithmTest*"` | ❌ W0 | ⬜ pending |
| 08-01-02 | 01 | 0 | BRDG-02 | unit | `./gradlew :app:testDebugUnitTest --tests "*KotlinBridgeCacheTest*"` | ❌ W0 | ⬜ pending |
| 08-01-03 | 01 | 0 | BRDG-02 | unit | `./gradlew :app:testDebugUnitTest --tests "*LastFmRateLimiterTest*"` | ❌ W0 | ⬜ pending |
| 08-02-01 | 02 | 1 | BRDG-02 | unit | `./gradlew :app:testDebugUnitTest --tests "*BridgeAlgorithmTest*"` | ❌ W0 | ⬜ pending |
| 08-02-02 | 02 | 1 | BRDG-02 | unit | `./gradlew :app:testDebugUnitTest --tests "*KotlinBridgeCacheTest*"` | ❌ W0 | ⬜ pending |
| 08-03-01 | 03 | 2 | BRDG-03 | unit | `./gradlew :app:testDebugUnitTest --tests "*BridgeViewModelTest*"` | ✅ | ⬜ pending |
| 08-03-02 | 03 | 2 | INFRA-01 | migration | manual-only (instrumented test) | ❌ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `app/src/test/kotlin/com/metrolist/music/bridge/BridgeAlgorithmTest.kt` — stubs for BRDG-02 (mocked cache + LastFM, verifies path shape and rate limiter)
- [ ] `app/src/test/kotlin/com/metrolist/music/bridge/KotlinBridgeCacheTest.kt` — stubs for BRDG-02 caching (L1 hit skips DB, DB hit skips network)
- [ ] `app/src/test/kotlin/com/metrolist/music/bridge/LastFmRateLimiterTest.kt` — stubs for BRDG-02 rate limiting
- [ ] Extend `app/src/test/kotlin/com/metrolist/music/bridge/BridgeViewModelTest.kt` — add `findBridge_calls_algorithm_not_webview` for BRDG-03

*Existing infrastructure covers framework installation (JUnit + mockito-kotlin already in libs.versions.toml).*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Room migration 36→37 preserves data | INFRA-01 | Requires instrumented test with real SQLite | 1. Install app with schema v36 2. Add songs/playlists 3. Update to v37 build 4. Verify data intact |
| Bridge completes on device for real artist pair | BRDG-02 | Requires real Last.fm API + network | 1. Open Bridge tab 2. Enter "Radiohead" → "Kendrick Lamar" 3. Verify 5-7 hop path returned 4. Check Timber logs show no WebView calls |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
