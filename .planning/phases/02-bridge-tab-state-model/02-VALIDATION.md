---
phase: 2
slug: bridge-tab-state-model
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-04
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Android Gradle build (no unit test infra in app module) |
| **Config file** | app/build.gradle.kts |
| **Quick run command** | `./gradlew :app:compileUniversalFossDebugKotlin` |
| **Full suite command** | `./gradlew assembleUniversalFossDebug --configuration-cache-problems=warn` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:compileUniversalFossDebugKotlin`
- **After every plan wave:** Run `./gradlew assembleUniversalFossDebug --configuration-cache-problems=warn`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 02-01-01 | 01 | 1 | BRDG-07 | build | `./gradlew :app:compileUniversalFossDebugKotlin` | ✅ | ⬜ pending |
| 02-01-02 | 01 | 1 | BRDG-07 | grep | `grep -r "BridgeUiState" app/src/` | ❌ W0 | ⬜ pending |
| 02-01-03 | 01 | 1 | BRDG-07 | grep | `grep -r "Bridge" app/src/main/kotlin/com/metrolist/music/ui/screens/Screens.kt` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements. No test framework install needed — validation is build-level compilation + grep.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Bridge tab visible in bottom nav | BRDG-07 | UI visual check | Install on device, confirm Bridge tab appears in bottom nav bar |
| Tapping Bridge navigates to BridgeScreen | BRDG-07 | UI interaction | Tap Bridge tab, verify screen loads without affecting other tabs |
| BridgeScreen responds to state changes | BRDG-07 | UI visual check | Verify placeholder content renders for each BridgeUiState |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
