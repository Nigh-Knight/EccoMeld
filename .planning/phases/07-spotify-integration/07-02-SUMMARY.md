---
phase: 07-spotify-integration
plan: "02"
subsystem: ui/screens/bridge
tags: [seed-suggestions, random-bridge, fab, shimmer, chip-fill, animated-visibility]
dependency_graph:
  requires: [07-01]
  provides: [SeedSuggestionsRow, RandomBridgeFab, chip-fill-D01-logic]
  affects: [BridgeScreen]
tech_stack:
  added: []
  patterns: [animated-visibility-hide, shimmer-loading, lazy-row-chips, fab-z-order, focus-tracking, launched-effect-toast]
key_files:
  created: []
  modified:
    - app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/BridgeScreen.kt
decisions:
  - "onClick guard pattern used for FAB disabled state — standard FloatingActionButton has no enabled parameter; onClick lambda checks isEnabled before delegating"
  - "confirmFrom()/confirmTo() called immediately after onFromQueryChanged(artistName) for chip fill — confirmFrom() falls back to raw _fromQuery.value when ghost is empty, which correctly confirms the chip-supplied name"
  - "FAB placed as last child inside BoxWithConstraints (not Scaffold slot) to match existing screen structure and ensure correct Z-order above BottomSheet"
metrics:
  duration: "6min"
  completed: "2026-04-04T16:40:00Z"
  tasks: 2
  files: 1
---

# Phase 07 Plan 02: Seed Chip Row + Random Bridge FAB Summary

**One-liner:** SeedSuggestionsRow with shimmer/AnimatedVisibility + RandomBridgeFab with spinner at BottomEnd, wired to Plan 01 ViewModel APIs.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | SeedSuggestionsRow composable + chip fill logic + LaunchedEffect trigger | e01ef2b1 | BridgeScreen.kt |
| 2 | RandomBridgeFab composable + Box overlay placement | e01ef2b1 | BridgeScreen.kt |

## What Was Built

Extended `BridgeScreen.kt` with two new private composables and full wiring to the Plan 01 ViewModel APIs:

**SeedSuggestionsRow:**
- `AnimatedVisibility(visible = suggestions.isNotEmpty() || isLoading)` — row hidden when empty after load
- Loading state: `ShimmerHost(showGradient = false)` wrapping `LazyRow` with 3 placeholder `Box` items (80dp x 32dp, `surfaceVariant` background, `RoundedCornerShape(16.dp)`); LazyRow has `semantics { contentDescription = loadingDesc }`; individual boxes get `invisibleToUser()`
- Loaded state: `LazyRow` with `contentPadding = PaddingValues(horizontal = 16.dp)`, `Arrangement.spacedBy(8.dp)`, `SuggestionChip` items with `labelLarge` typography
- Placed between inputs Row and Find Bridge Button per UI-SPEC layout

**Chip fill logic (D-01 priority rules):**
1. From is blank → fill From
2. From filled, To blank → fill To
3. Both filled + From focused → overwrite From
4. Both filled + To focused → overwrite To
5. Both filled, neither focused → fallback to From

**Focus tracking:** `fromHasFocus` / `toHasFocus` via `Modifier.onFocusChanged` added as optional parameter to `GhostTextField`

**New LaunchedEffects:**
- `LaunchedEffect(Unit)` → `viewModel.loadSeedSuggestions()` (idempotent)
- `LaunchedEffect(randomBridgeToast)` → `Toast.makeText` + `viewModel.clearRandomBridgeToast()`

**RandomBridgeFab:**
- `FloatingActionButton` at `Alignment.BottomEnd` with `windowInsetsPadding` + `padding(16.dp)`
- `isLoading = true`: `CircularProgressIndicator(24.dp, onPrimaryContainer, strokeWidth=2.dp)`
- `isLoading = false`: `Icon(R.drawable.shuffle, contentDescription = bridge_random_fab_description)`
- onClick guard: `if (isEnabled) onClick()` — standard FAB has no `enabled` parameter
- Placed as LAST child in `BoxWithConstraints` for correct Z-order above bottom sheet
- `isEnabled = !viewModel.isRunning && !isFabLoading`

## Deviations from Plan

**1. [Rule 1 - Decision] onClick guard instead of enabled parameter**
- **Found during:** Task 2
- **Issue:** Standard `FloatingActionButton` has no `enabled` parameter in Material 3
- **Fix:** Wrapped onClick in `if (isEnabled) onClick()` guard. This matches the plan's note: "If neither supports `enabled`, use the `onClick` guard approach."
- **Files modified:** BridgeScreen.kt
- **Commit:** e01ef2b1

**2. [Rule 1 - Decision] confirmFrom()/confirmTo() called post-onFromQueryChanged for chips**
- **Found during:** Task 1 — Plan noted to verify exact method names
- **Issue:** ViewModel's `confirmFrom()` takes no parameters; it reads `_fromGhostFull` which is empty after `onFromQueryChanged()`. However `confirmFrom()` correctly falls back to `_fromQuery.value = current` (the chip name just set), making this the correct approach.
- **Fix:** No code change needed — the sequence `onFromQueryChanged(artistName); confirmFrom()` is correct.
- **Commit:** e01ef2b1

## Known Stubs

None — all implemented functionality is fully wired. SeedSuggestionsRow reads live `seedSuggestions` StateFlow. RandomBridgeFab delegates to `viewModel.randomBridge()` which is fully implemented in Plan 01.

## Self-Check: PASSED
