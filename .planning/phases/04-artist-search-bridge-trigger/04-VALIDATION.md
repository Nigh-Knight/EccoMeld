---
phase: 04
slug: artist-search-bridge-trigger
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-04
---

# Phase 04 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 |
| **Config file** | none (default Android Gradle test runner) |
| **Quick run command** | `./gradlew :app:testFossDebugUnitTest` |
| **Full suite command** | `./gradlew :app:testFossDebugUnitTest :lastfm:test` |
| **Estimated runtime** | ~20 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:testFossDebugUnitTest`
- **After every plan wave:** Run `./gradlew :app:testFossDebugUnitTest :lastfm:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 20 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 04-00-01 | 00 | 0 | BRDG-01 | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgeViewModelTest.onFromQueryChanged_emits_ghostSuffix"` | ❌ W0 | ⬜ pending |
| 04-00-02 | 00 | 0 | BRDG-01 | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgeViewModelTest.confirmFrom_stores_artist"` | ❌ W0 | ⬜ pending |
| 04-00-03 | 00 | 0 | BRDG-01 | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgeViewModelTest.ghostSuffix_only_shown_on_prefix_match"` | ❌ W0 | ⬜ pending |
| 04-00-04 | 00 | 0 | BRDG-04 | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgeScreenTest.progress_fraction_computed_correctly"` | ❌ W0 | ⬜ pending |
| 04-00-05 | 00 | 0 | BRDG-06 | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgeViewModelTest.error_state_does_not_set_isRunning"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `gradle/libs.versions.toml` — add `mockitoKotlin` and `coroutinesTest` version entries
- [ ] `app/build.gradle.kts` — add `testImplementation(libs.mockito.kotlin)` and `testImplementation(libs.coroutines.test)`
- [ ] `app/src/test/kotlin/com/metrolist/music/bridge/BridgeViewModelTest.kt` — add new @Ignore test methods for autocomplete (BRDG-01, BRDG-06)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Error message displayed as inline text | BRDG-06 | Visual verification on device | Trigger bridge with impossible artist pair, verify error text appears inline (not dialog) |
| Ghost text visually renders as faded suffix | BRDG-01 | Visual rendering on device | Type partial artist name, verify ghost suffix appears at 40% opacity |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 20s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
