---
phase: 05-playlist-builder-auto-play
plan: "02"
subsystem: viewmodels/bridge-ui
tags: [bridge, playlist-builder, queue-dialog, playerconnection, compose, alertdialog]
dependency_graph:
  requires:
    - "BridgePlaylistBuilder.buildForPath() (Plan 05-01)"
    - "PlayerConnection.playQueue() + playNext() (existing playback)"
    - "ListQueue (existing queues/ListQueue.kt)"
    - "BridgeUiState.PathFound (Phase 02 sealed class)"
    - "LocalPlayerConnection CompositionLocal (MainActivity)"
  provides:
    - "BridgeViewModel.buildPlaylist() — auto-triggered on PathFound, resolves tracks"
    - "BridgeViewModel.isBuilding StateFlow — building progress indicator signal"
    - "BridgeViewModel.showQueueDialog StateFlow — triggers AlertDialog in BridgeScreen"
    - "BridgeViewModel.buildFailed StateFlow — zero-track error signal"
    - "BridgeViewModel.onConfirmReplaceQueue() — loads ListQueue, triggers playback"
    - "BridgeViewModel.onConfirmPlayNext() — inserts bridge tracks after current"
    - "BridgeViewModel.dismissQueueDialog() — dismisses without playback"
    - "BridgeScreen AlertDialog — Replace queue / Play next queue options"
    - "BridgeScreen PathFound building indicator — CircularProgressIndicator + text"
    - "6 new string resources for dialog and building state"
  affects:
    - "Phase 06: bridge path visualization (builds on BridgeScreen PathFound branch)"
    - "Phase 07: end-to-end user flow (uses complete playback pipeline)"
tech_stack:
  added: []
  patterns:
    - "PlayerConnection passed from composable to ViewModel methods (no Context leak)"
    - "StateFlow trio: isBuilding + showQueueDialog + buildFailed for building lifecycle"
    - "AlertDialog outside when-block but inside Column — overlays on any uiState"
key_files:
  created: []
  modified:
    - app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt
    - app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/BridgeScreen.kt
    - app/src/main/res/values/metrolist_strings.xml
decisions:
  - "PlayerConnection passed as parameter to onConfirmReplaceQueue/onConfirmPlayNext — not stored in ViewModel — follows Android anti-pattern guidance for Context-like objects"
  - "buildFailed StateFlow used instead of nullable items check — cleaner separation of building lifecycle from pending items state"
  - "AlertDialog placed before the when(state) block so it can appear over any BridgeUiState"
metrics:
  duration: "15min"
  completed: "2026-04-04T14:37:33Z"
  tasks: 2
  files_modified: 3
---

# Phase 05 Plan 02: BridgeScreen Queue Dialog + Auto-Play Integration Summary

**One-liner:** BridgeViewModel intercepts PathFound to auto-build playlist via BridgePlaylistBuilder, shows AlertDialog with Replace/Play-Next options, and starts playback through PlayerConnection.playQueue/playNext with ListQueue.

## What Was Built

### Task 1: BridgeViewModel playlist building + queue dialog state

Updated `BridgeViewModel.kt` to:
- Add `BridgePlaylistBuilder` to HiltViewModel constructor via `@Inject`
- Extend `meldBridgeInterface.onStateChange` to intercept `BridgeUiState.PathFound` and launch `buildPlaylist()` in `viewModelScope`
- Add `_isBuilding`, `_showQueueDialog`, `_buildFailed` MutableStateFlows
- `buildPlaylist()`: calls `playlistBuilder.buildForPath(path)`, sets `_showQueueDialog = true` on success or `_buildFailed = true` on empty result
- `onConfirmReplaceQueue(playerConnection)`: wraps items in `ListQueue("Bridge: from → to", items)` and calls `playerConnection.playQueue()`
- `onConfirmPlayNext(playerConnection)`: calls `playerConnection.playNext(items)`
- `dismissQueueDialog()`: collapses dialog without starting playback
- `pendingTrackCount` property for dialog message text

### Task 2: BridgeScreen queue dialog + building indicator

Updated `BridgeScreen.kt` to:
- Import `AlertDialog`, `TextButton`, `LocalPlayerConnection`
- Collect `isBuilding`, `showQueueDialog`, `buildFailed` from ViewModel
- Add `AlertDialog` block before `when(state)` — shown when `showQueueDialog && playerConnection != null`
  - Confirm: "Replace queue" → `onConfirmReplaceQueue(playerConnection)`
  - Dismiss: "Play next" → `onConfirmPlayNext(playerConnection)`
- Update `PathFound` branch: shows `CircularProgressIndicator + "Building playlist..."` when `isBuilding`, shows `"bridge_no_tracks_found"` when `buildFailed`, otherwise shows existing path found hint
- Added 6 string resources to `metrolist_strings.xml`

## Deviations from Plan

### Auto-fixed Issues

None.

### Scope Notes

The plan's Task 1 specified including 05-01 dependencies (BridgePlaylistBuilder, ArtistTopTracksResponse, LastFM.getArtistTopTracks) since the 05-01 worktree hadn't merged into this worktree. These were cherry-picked and merged as part of the same Task 1 commit to satisfy the build dependency. The LastFM.kt merge conflict (both worktrees added different imports) was resolved by including both `ArtistSearchResponse` and `ArtistTopTracksResponse` imports.

## Success Criteria Verification

- Bridge path completion triggers automatic playlist building with no user action (PLAY-01): `is BridgeUiState.PathFound -> viewModelScope.launch { buildPlaylist(...) }` in init
- User sees "Building playlist..." during track resolution (not frozen screen): `isBuilding` StateFlow drives `CircularProgressIndicator` in PathFound branch
- Queue dialog asks "Replace queue" vs "Play next" (D-06): `AlertDialog` with confirm/dismiss buttons
- After confirmation, playback starts immediately (D-08) via existing player (PLAY-05): `playerConnection.playQueue(ListQueue(...))` / `playerConnection.playNext(items)`
- If zero tracks resolve, user sees informative message: `buildFailed` StateFlow drives `bridge_no_tracks_found` text
- All new UI text is in string resources: 6 strings added to `metrolist_strings.xml`

## Self-Check: PASSED
