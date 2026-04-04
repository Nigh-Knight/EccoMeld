---
status: partial
phase: 01-webview-foundation
source: [01-VERIFICATION.md]
started: 2026-04-04T05:30:00Z
updated: 2026-04-04T05:30:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. IndexedDB persistence across app restart
expected: Write to IndexedDB in one session, force-kill app, relaunch — data survives. Last.fm cache from EccoPath persists.
result: [pending]

### 2. No 404s for /_next/ chunks at runtime
expected: Logcat shows no 404 errors for /_next/ static chunks. onPageFinished log fires successfully.
result: [pending]

### 3. Launcher and app bar show "EccoMeld"
expected: Launcher icon label displays "EccoMeld". App bar displays "EccoMeld".
result: [pending]

## Summary

total: 3
passed: 0
issues: 0
pending: 3
skipped: 0
