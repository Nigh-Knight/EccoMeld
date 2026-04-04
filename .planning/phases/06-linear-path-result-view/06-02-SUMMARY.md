---
phase: 06-linear-path-result-view
plan: "02"
subsystem: bridge-ui
tags: [compose, bottom-sheet, path-visualization, now-playing, animation]
dependency_graph:
  requires: [06-01]
  provides: [path-sheet-ui, now-playing-highlight, sheet-dismiss-reopen]
  affects: [BridgeScreen, BridgeViewModel]
tech_stack:
  added: []
  patterns: [BoxWithConstraints, rememberBottomSheetState, animateColorAsState, LaunchedEffect, FlowRow]
key_files:
  created:
    - app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/PathSheet.kt
  modified:
    - app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/BridgeScreen.kt
    - app/src/main/res/values/metrolist_strings.xml
decisions:
  - "Used Box fallback for GenreTagChip instead of SuggestionChip — SuggestionChip not in codebase, Box gives precise 24dp height control per Research Pitfall 6"
  - "Used R.drawable.volume_up for now-playing icon — confirmed present in drawable/ resources, material-icons-extended not in project dependencies"
  - "FlowRow imported from androidx.compose.foundation.layout.FlowRow (no ExperimentalLayoutApi needed) — confirmed from Dialog.kt usage pattern"
metrics:
  duration: "4 minutes"
  completed: "2026-04-04"
  tasks_completed: 2
  files_changed: 3
---

# Phase 06 Plan 02: PathSheet + BridgeScreen BottomSheet Integration Summary

**One-liner:** Bottom sheet path visualization with genre tag chips, listener counts, and animated now-playing highlight using BoxWithConstraints + project-native BottomSheet composable.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | PathSheet composable + BridgeScreen BottomSheet integration + now-playing observation | 0f78b865 | PathSheet.kt (created), BridgeScreen.kt (refactored), metrolist_strings.xml |
| 2 | Visual verification of path result view | ⚡ Auto-approved | — |

## What Was Built

### PathSheet.kt
New composable file providing the full path visualization UI:
- `PathSheet` — top-level composable; drag handle + title + `LazyColumn` of path nodes
- `PathNodeRow` — single artist card with `animateColorAsState` for left border (4dp) and background tint; animated `AnimatedVisibility` for volume_up icon when now-playing
- `GenreTagChip` — 24dp tall Box with primary color at 12% alpha; `FlowRow` renders up to 3 per node
- `VerticalConnectorLine` — 4×12dp connector between nodes at `outline` color 38% alpha

### BridgeScreen.kt
Refactored to use `BoxWithConstraints` as root layout:
- `rememberBottomSheetState(dismissedBound=0.dp, expandedBound=maxHeight, collapsedBound=220.dp, initialAnchor=dismissedAnchor)`
- `LaunchedEffect(uiState)` — auto-calls `collapseSoft()` on PathFound/PlaylistReady, `dismiss()` on Idle/Searching
- `LaunchedEffect(mediaMetadata)` — observes `PlayerConnection.mediaMetadata` and routes to `viewModel.onNowPlayingArtistChanged()`
- `artistMetadata` collected from ViewModel and passed to `PathSheet`
- "Show Path" `TextButton` rendered when path exists but sheet is dismissed
- `BottomSheet` overlay with collapsed drag-handle peek content at 220dp

### String Resources
Three new strings added after existing bridge_ entries:
- `bridge_path_sheet_title` — "Bridge Path"
- `bridge_path_show_button` — "Show Path"
- `bridge_path_listener_count` — passthrough format string `%1$s`

## Deviations from Plan

### Auto-fixed Issues

None — plan executed exactly as written, with two informed substitutions:

**1. [Rule 2 - Pattern] Box fallback for GenreTagChip**
- **Found during:** Task 1
- **Issue:** Plan specified `SuggestionChip` as primary with Box as fallback; `SuggestionChip` has no existing usage in codebase (confirmed by grep)
- **Fix:** Used Box fallback directly per Research Pitfall 6 guidance in the plan itself
- **Files modified:** PathSheet.kt

**2. [Rule 2 - Pattern] volume_up drawable for now-playing icon**
- **Found during:** Task 1
- **Issue:** Plan noted to check if material-icons-extended is available; it is not in project dependencies
- **Fix:** Used `R.drawable.volume_up` (confirmed present in drawable/), which is semantically correct for a playing indicator
- **Files modified:** PathSheet.kt

## Build Verification

Kotlin compilation verified clean via `./gradlew :app:compileUniversalFossDebugKotlin --no-configuration-cache` — BUILD SUCCESSFUL.

The full assemble task shows a pre-existing configuration cache error in `buildEccoPath` and `downloadProtoc` tasks (unrelated to this plan's changes) but the APK packages successfully.

## Known Stubs

None — all data flows are wired:
- `artistMetadata` flows from `BridgeViewModel._artistMetadata` (populated by `fetchArtistMetadata` on PathFound)
- `nowPlayingIndex` flows from `BridgeUiState.PlaylistReady.nowPlayingIndex` (updated by `onNowPlayingArtistChanged`)
- Path list flows from `uiState` on PathFound/PlaylistReady

## Self-Check: PASSED
