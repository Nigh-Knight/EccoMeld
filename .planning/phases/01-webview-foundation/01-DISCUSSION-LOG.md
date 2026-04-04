# Phase 1: WebView Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-04
**Phase:** 01-webview-foundation
**Areas discussed:** WebView visibility, EccoPath build pipeline, Branding scope, Submodule location

---

## WebView Visibility

| Option | Description | Selected |
|--------|-------------|----------|
| Hidden (headless) | WebView runs invisibly — all UI is native Compose. EccoPath only does computation. | ✓ |
| Visible | User sees the actual EccoPath web UI in a WebView. | |
| Hybrid | Hidden for computation, optionally visible for debugging. | |

**User's choice:** Hidden (headless)
**Notes:** Consistent look & feel with existing Meld UI.

### WebView Initialization

| Option | Description | Selected |
|--------|-------------|----------|
| Lazy (on first Bridge use) | WebView only created when user first opens Bridge tab. | |
| Eager (at app startup) | WebView pre-created during app init. Bridge is instant on first use. | ✓ |

**User's choice:** Eager (at app startup)
**Notes:** None

### WebView Lifecycle

| Option | Description | Selected |
|--------|-------------|----------|
| Application-scoped singleton | Created once, survives config changes and tab switches. IndexedDB always warm. | ✓ |
| ViewModel-scoped | Tied to BridgeViewModel lifecycle. Destroyed if ViewModel is cleared. | |
| You decide | Let Claude pick based on existing singleton patterns. | |

**User's choice:** Application-scoped singleton
**Notes:** None

---

## EccoPath Build Pipeline

### Build Method

| Option | Description | Selected |
|--------|-------------|----------|
| Gradle task | Custom Gradle task runs next build + export, copies to assets. Automated. | ✓ |
| Manual pre-build | Developer runs script manually before APK build. | |
| CI-only | Only CI builds EccoPath. Local dev uses pre-built checked-in assets. | |

**User's choice:** Gradle task
**Notes:** None

### Config Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Separate android export config | EccoPath keeps original config for web dev. Gradle uses separate android config. | ✓ |
| Modify in-place during build | Gradle patches next.config.ts temporarily. | |
| You decide | Let Claude pick. | |

**User's choice:** Separate android export config
**Notes:** None

### Crossorigin Bug Handling

| Option | Description | Selected |
|--------|-------------|----------|
| Post-build script strips crossorigin | Script removes crossorigin attributes from exported HTML. | ✓ |
| Spike first | Investigate if bug affects WebViewAssetLoader before fixing. | |
| You decide | Let Claude determine approach. | |

**User's choice:** Post-build script strips crossorigin
**Notes:** None

---

## Branding Scope

### Branding Extent

| Option | Description | Selected |
|--------|-------------|----------|
| Launcher + app bar only | Change app_name string only. | |
| All user-visible text | Update launcher, app bar, notifications, about, crash handler, etc. | ✓ |
| Launcher only | Only launcher label changes. | |

**User's choice:** All user-visible text
**Notes:** None

### App Icon

| Option | Description | Selected |
|--------|-------------|----------|
| Keep existing icon | Per PROJECT.md constraint, keep Meld icon. | |
| New EccoMeld icon | Use icon from EccoMuse-Site project. | ✓ |

**User's choice:** Use icon from `~/Projects/EccoMuse-Site/src/assets/images/EccoMuse_new_logo.png`
**Notes:** User specified exact source file location.

---

## Submodule Location

### Directory

| Option | Description | Selected |
|--------|-------------|----------|
| Root-level eccopath/ | At repo root. Clean, visible, easy Gradle reference. | ✓ |
| Under app/ directory | At app/eccopath/. Closer to assets but unusual. | |
| Under vendor/ or libs/ | At vendor/eccopath/. Groups external deps. | |

**User's choice:** Root-level eccopath/
**Notes:** None

### Branch Tracking

| Option | Description | Selected |
|--------|-------------|----------|
| Track main branch | Submodule follows main. git submodule update --remote pulls latest. | ✓ |
| Pinned commit | Submodule pinned to specific hash. Requires manual bumps. | |

**User's choice:** Track main branch
**Notes:** Both repos evolving together during active development.

---

## Claude's Discretion

- WebViewAssetLoader URL path structure
- Specific Gradle task implementation details
- Android adaptive icon layer splitting
- crossorigin stripping implementation method

## Deferred Ideas

None — discussion stayed within phase scope
