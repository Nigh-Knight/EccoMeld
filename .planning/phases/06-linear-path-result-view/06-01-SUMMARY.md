---
phase: 06-linear-path-result-view
plan: "01"
subsystem: data-layer
tags: [lastfm, bridge, viewmodel, metadata, listeners, now-playing]
dependency_graph:
  requires: [05-02-PLAN]
  provides: [LastFM.getArtistInfo, BridgeArtistInfo, formatListeners, onNowPlayingArtistChanged, artistMetadata]
  affects: [BridgeScreen, Phase 06 UI plans]
tech_stack:
  added: []
  patterns: [parallel-async-coroutines, top-level-function, MutableStateFlow, TDD-red-green]
key_files:
  created:
    - lastfm/src/main/kotlin/com/metrolist/lastfm/models/ArtistInfoResponse.kt
    - lastfm/src/test/kotlin/com/metrolist/lastfm/ArtistInfoResponseTest.kt
  modified:
    - lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt
    - app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt
    - app/src/test/kotlin/com/metrolist/music/bridge/BridgeViewModelTest.kt
decisions:
  - "BridgeArtistInfo and formatListeners placed as top-level declarations before BridgeViewModel class — matches project style (PreferenceKeys pattern) and makes them easily importable in tests"
  - "fetchArtistMetadata uses viewModelScope.async not coroutineScope.async — ViewModel scope is already Dispatchers.Default, and Dispatchers.IO specified per launch for network I/O"
  - "onNowPlayingArtistChanged is no-op when state is not PlaylistReady — avoids spurious state copies outside the expected flow"
metrics:
  duration: "6min"
  completed_date: "2026-04-04"
  tasks_completed: 2
  files_changed: 5
---

# Phase 06 Plan 01: Last.fm Artist Info + BridgeViewModel Metadata Layer Summary

**One-liner:** Last.fm artist.getInfo unauthenticated GET with parallel metadata fetch, formatListeners() K/M formatter, and case-insensitive now-playing index tracking in BridgeViewModel.

## What Was Built

### ArtistInfoResponse model (lastfm module)

`ArtistInfoResponse` is a `@Serializable` data class mirroring the Last.fm `artist.getInfo` JSON response. All nested classes use default values for robustness against missing fields. The `listeners` field is `String` (not `Long`) per the established Last.fm pattern in `ArtistMatch` — the API returns numeric values as JSON strings.

Nested structure: `ArtistInfoResponse > ArtistDetail > Stats + Tags > Tag`

### LastFM.getArtistInfo() (lastfm module)

Unauthenticated GET following the exact `getArtistTopTracks()` pattern. Uses `autocorrect=1` to handle artist name variations. Does not use `lastfmParams()` (which computes API signatures — not needed for public endpoints).

### BridgeArtistInfo + formatListeners() (app module)

`BridgeArtistInfo` is a top-level data class capturing the per-artist data needed by the Phase 06 path sheet UI: `name`, `tags: List<String>`, `listenerCount: Long`, `formattedListeners: String`.

`formatListeners(count: Long)` is a top-level function:
- `< 1,000` → raw ("847 listeners")
- `1,000–999,999` → K with 1 decimal ("142.3K listeners")
- `>= 1,000,000` → M with 1 decimal ("1.2M listeners")

### BridgeViewModel additions (app module)

- `_artistMetadata: MutableStateFlow<Map<String, BridgeArtistInfo>>` — populated after bridge path found
- `fetchArtistMetadata(path)` — private suspend function, parallel `async` per artist on `Dispatchers.IO`, skips failed/null entries silently
- Wired into `onStateChange` PathFound handler alongside existing `buildPlaylist()` launch
- `buildPlaylist()` clears `_artistMetadata` at start to prevent stale data across bridge runs
- `onNowPlayingArtistChanged(artistName)` — public function for PlayerConnection integration; case-insensitive trimmed match against `_currentPath`; no-op when artist not found (preserves last highlight per D-05)

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | ArtistInfoResponse + getArtistInfo() + deserialization tests | b8b82052 | ArtistInfoResponse.kt, LastFM.kt, ArtistInfoResponseTest.kt |
| 2 (RED) | Failing tests for formatListeners + onNowPlayingArtistChanged | fe7512cc | BridgeViewModelTest.kt |
| 2 (GREEN) | BridgeArtistInfo + formatListeners + fetchArtistMetadata + onNowPlayingArtistChanged | c279eed1 | BridgeViewModel.kt |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed pre-existing BridgeViewModelTest compilation break**
- **Found during:** Task 2 RED setup
- **Issue:** `BridgeViewModel` constructor requires `playlistBuilder: BridgePlaylistBuilder` (3 args) but `buildViewModel()` helper in the test only passed 2 args (WebView, MeldBridgeInterface). Tests would not compile.
- **Fix:** Added `mock<BridgePlaylistBuilder>()` as third arg in both `buildViewModel()` call sites. `mock-maker-inline` extension was already configured in `app/src/test/resources/mockito-extensions/`, enabling mocking of the final class.
- **Files modified:** `app/src/test/kotlin/com/metrolist/music/bridge/BridgeViewModelTest.kt`
- **Commit:** fe7512cc

## Known Stubs

None — all logic is fully implemented and wired. `_artistMetadata` is populated from real LastFM.getArtistInfo() calls (guarded by API_KEY check). `onNowPlayingArtistChanged()` is callable from PlayerConnection integration in Phase 06 UI plans.

## Self-Check: PASSED

All created/modified files exist on disk. All task commits confirmed in git log.
- lastfm/src/main/kotlin/com/metrolist/lastfm/models/ArtistInfoResponse.kt: FOUND
- lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt: FOUND
- app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt: FOUND
- lastfm/src/test/kotlin/com/metrolist/lastfm/ArtistInfoResponseTest.kt: FOUND
- app/src/test/kotlin/com/metrolist/music/bridge/BridgeViewModelTest.kt: FOUND
- b8b82052 (Task 1): FOUND
- fe7512cc (Task 2 RED): FOUND
- c279eed1 (Task 2 GREEN): FOUND
