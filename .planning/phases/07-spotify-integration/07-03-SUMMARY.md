---
phase: 07-spotify-integration
plan: "03"
subsystem: ui/screens/bridge
tags: [familiarity-badges, pathsheet, compose, accessibility, spotify-integration]
dependency_graph:
  requires: [ArtistFamiliarity-enum, artistFamiliarity-flow]
  provides: [ArtistFamiliarityBadge, PathSheet-familiarityMap, PathNodeRow-familiarity]
  affects: [BridgeScreen, PathSheet, PathNodeRow]
tech_stack:
  added: []
  patterns: [optional-param-no-placeholder, null-means-loading, Box-pill-badge]
key_files:
  created: []
  modified:
    - app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/PathSheet.kt
    - app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/BridgeScreen.kt
decisions:
  - "Badge inserted into existing name+icon Row via null-guard — no layout shift during loading (familiarity=null shows nothing)"
  - "ArtistFamiliarityBadge is private composable — not part of public PathSheet API surface"
  - "familiarityMap defaults to emptyMap() in PathSheet — all existing callers remain valid without changes"
metrics:
  duration: "3min"
  completed: "2026-04-04T16:29:37Z"
  tasks: 2
  files: 2
---

# Phase 07 Plan 03: Artist Familiarity Badges Summary

**One-liner:** Inline NEW/known pill badges in PathNodeRow using ArtistFamiliarity state from BridgeViewModel, threaded through PathSheet familiarityMap parameter.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | ArtistFamiliarityBadge composable + PathSheet/PathNodeRow signature extension + BridgeScreen wiring | 191ea9db | PathSheet.kt, BridgeScreen.kt |
| 2 | Visual verification of seed chips, FAB, and badges | (auto-approved) | — |

## What Was Built

**`ArtistFamiliarityBadge` composable** (private, in PathSheet.kt):
- "NEW" badge: `colorScheme.primary` fill + `colorScheme.onPrimary` text, `labelSmall` typography
- "known" badge: `colorScheme.secondary.copy(alpha = 0.7f)` fill + `colorScheme.onSecondary` text
- 16dp height, 8dp horizontal padding, 8dp corner radius (fully rounded pill)
- Accessibility: `contentDescription = "New artist"` / `"Familiar artist"` via `semantics { }`

**Extended `PathNodeRow`** signature:
- Added `familiarity: ArtistFamiliarity? = null` — null means resolution pending, no badge shown (no layout shift)
- Badge inserted inline in the name+icon Row, after the volume_up icon AnimatedVisibility, with a 6dp Spacer

**Extended `PathSheet`** signature:
- Added `familiarityMap: Map<String, ArtistFamiliarity> = emptyMap()` — backward compatible
- Threads `familiarityMap[artistName]` to each `PathNodeRow` in the `itemsIndexed` block

**Updated `BridgeScreen`**:
- Collects `val artistFamiliarity by viewModel.artistFamiliarity.collectAsState()`
- Passes `familiarityMap = artistFamiliarity` to PathSheet at the BottomSheet call site

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — familiarity data is fully wired from `BridgeViewModel.resolveFamiliarity()` (implemented in Plan 01) through `artistFamiliarity` StateFlow to the badge composable.

## Self-Check: PASSED
