---
phase: 1
slug: webview-foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-04
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | None — no test infrastructure exists in the project |
| **Config file** | None |
| **Quick run command** | `./gradlew assembleFossUniversalDebug` |
| **Full suite command** | `./gradlew assembleFossUniversalDebug` + manual device verification |
| **Estimated runtime** | ~120 seconds (build) + manual verification |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew assembleFossUniversalDebug` — confirm no compilation errors
- **After every plan wave:** Install on device/emulator and verify each success criterion manually
- **Before `/gsd:verify-work`:** All four INFRA success criteria confirmed on device
- **Max feedback latency:** 120 seconds (build time)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 01-01-01 | 01 | 1 | INFRA-01 | manual-only | Verify `app/src/main/assets/eccopath/index.html` exists post-build | N/A | ⬜ pending |
| 01-02-01 | 02 | 1 | INFRA-02 | manual-only | Install APK, check logcat for 404s on `/_next/` chunks | N/A | ⬜ pending |
| 01-03-01 | 03 | 1 | INFRA-03 | manual-only | Perform bridge search, force-close app, verify IndexedDB cache survives | N/A | ⬜ pending |
| 01-04-01 | 04 | 1 | INFRA-04 | manual-only | Visual inspection of launcher label + app bar | N/A | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `eccopath/` — git submodule added
- [ ] `eccopath/next.config.android.ts` — Android-specific Next.js config
- [ ] `eccopath/scripts/strip-crossorigin.mjs` — post-build crossorigin attribute stripping
- [ ] `androidx.webkit` added to `gradle/libs.versions.toml` and `app/build.gradle.kts`
- [ ] `app/src/main/kotlin/com/metrolist/music/di/Qualifiers.kt` — add `@BridgeWebView` qualifier annotation

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| EccoPath renders in WebView without 404s | INFRA-02 | Requires running Android device/emulator with WebView | Install APK, open app, check logcat for WebView resource loading |
| IndexedDB cache survives app restart | INFRA-03 | Requires stateful device interaction across app sessions | Open app → trigger Last.fm cache write → force-close → reopen → verify cache hit in logcat |
| App name "EccoMeld" in launcher/app bar | INFRA-04 | Visual UI verification | Check launcher icon label and app bar text |
| EccoPath static export bundles correctly | INFRA-01 | Build artifact verification | Run Gradle build task, inspect `app/src/main/assets/eccopath/` contents |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
