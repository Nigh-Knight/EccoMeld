---
phase: 09
slug: bridge-ui-redesign
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-05
---

# Phase 09 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 + kotlinx.coroutines.test |
| **Config file** | `app/build.gradle.kts` (testImplementation section) |
| **Quick run command** | `./gradlew :app:testDebugUnitTest --tests "*.bridge.*"` |
| **Full suite command** | `./gradlew :app:testDebugUnitTest --tests "*.bridge.*"` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:testDebugUnitTest --tests "*.bridge.*"`
- **After every plan wave:** Run `./gradlew :app:testDebugUnitTest --tests "*.bridge.*"`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 09-01-01 | 01 | 1 | BRDG-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*BridgeViewModelTest*"` | ✅ | ⬜ pending |
| 09-01-02 | 01 | 1 | BRDG-01 | compile | `./gradlew :app:compileDebugKotlin` | ✅ | ⬜ pending |
| 09-02-01 | 02 | 2 | BRDG-04, BRDG-06 | compile | `./gradlew :app:compileDebugKotlin` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Update `BridgeViewModelTest.kt` — add tests for fromSuggestions/toSuggestions StateFlows, clearFrom/clearTo methods, auto-trigger logic in confirmTo()

*Existing test infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Dropdown visually overlays input | BRDG-01 | Layout rendering | Run app, type artist name, verify dropdown appears below input |
| Second input animates into view | BRDG-01 | Animation visual | Select from suggestion, verify smooth slide-down animation |
| Auto-focus on second input | BRDG-01 | Focus behavior | Select "From" artist, verify keyboard appears for "To" input |
| State transition animations | BRDG-04 | Animation visual | Confirm both artists, verify crossfade to progress indicator |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
