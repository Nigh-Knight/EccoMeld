# Phase 5: Playlist Builder + Auto-Play - Context

**Gathered:** 2026-04-04
**Status:** Ready for planning

<domain>
## Phase Boundary

When a bridge path completes, automatically build a playlist by resolving each bridge artist's tracks (Last.fm top tracks → YT Music fuzzy match) and feed the playlist into the existing Meld queue for immediate playback. No manual "play" button — bridge completion triggers auto-play. Failed YT Music matches are silently skipped. The playlist plays through the existing Meld player bar with full controls.

</domain>

<decisions>
## Implementation Decisions

### Track Selection Strategy
- **D-01:** Use BOTH Last.fm and YT Music. Last.fm `artist.getTopTracks` API provides track names ranked by play count. Top 2 = "popular tracks." Tracks ranked lower (positions 10-50) = "deep cuts" — pick 3-5 randomly from this range.
- **D-02:** For each track name from Last.fm, fuzzy match against YT Music search results (via existing InnerTube `search` API) to get a playable video ID. Match on artist name + track title similarity.
- **D-03:** Genre-transition ordering is preserved: tracks are ordered by bridge artist position (seed → bridge1 → bridge2 → ... → target), not shuffled.

### YT Music Matching
- **D-04:** Fuzzy matching uses the existing InnerTube search: search `"artist name track title"`, take the best `SongItem` result. If no results or confidence is too low, silently skip the track (PLAY-04).
- **D-05:** Match failures are logged via Timber but never shown to the user. The playlist continues with whatever tracks resolved successfully.

### Queue Behavior
- **D-06:** When bridge playlist is ready, show a dialog asking the user: "Replace current queue?" vs "Play next". This respects the user's currently-playing music rather than silently replacing it.
- **D-07:** Playlist is fed into the existing Meld queue system via `ListQueue` (which accepts a pre-built list of `MediaItem`s). This integrates with the existing `MusicService` player, mini player, and expanded player with no custom playback code.

### Auto-Play
- **D-08:** After queue confirmation, playback starts immediately — no additional "play" button. The UI transitions from `PathFound` to `PlaylistReady` state.

### Claude's Discretion
- Fuzzy match threshold/algorithm details (Levenshtein, Jaccard, or simple contains)
- How many deep cuts per artist (3-5 range — pick based on what's available)
- `ListQueue` vs creating a new `BridgeQueue` class — whichever fits the existing queue pattern better
- Dialog styling (Material 3 AlertDialog or custom)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### EccoPath Bridge Output
- `eccopath/lib/bridgeCrawl.ts` — `BridgeResult { found: boolean, path: string[] }`. The `path` array is artist names that Phase 5 must resolve to tracks.
- `eccopath/lib/types.ts` — Type definitions for graph data structures

### Existing Playback System
- `app/src/main/kotlin/com/metrolist/music/playback/queues/ListQueue.kt` — Pre-built queue from a list of MediaItems. Likely the integration point for bridge playlist.
- `app/src/main/kotlin/com/metrolist/music/playback/queues/YouTubeQueue.kt` — Reference for how queues resolve YT Music tracks.
- `app/src/main/kotlin/com/metrolist/music/playback/queues/SpotifyPlaylistQueue.kt` — Reference for batch track resolution with silent skip on failures.
- `app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt` — The 3460-line playback service. Need to understand how queues are loaded.
- `app/src/main/kotlin/com/metrolist/music/playback/PlayerConnection.kt` — Bridge between UI and MusicService.

### InnerTube Search
- `innertube/src/main/kotlin/com/metrolist/innertube/YouTube.kt` — `search()` method for finding YT Music tracks by query.
- `innertube/src/main/kotlin/com/metrolist/innertube/models/YTItem.kt` — `SongItem` with `id` (video ID) needed for playback.

### Last.fm API
- `lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt` — Will need `artist.getTopTracks` added (similar to Phase 4's `artist.search` addition).
- `eccopath/lib/lastfm.ts` — Reference for Last.fm API call patterns.

### Bridge State
- `app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/BridgeScreen.kt` — `BridgeUiState.PathFound(path)` triggers playlist building. `PlaylistReady` is the target state.
- `app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt` — Orchestrates the flow.

### Requirements
- `.planning/REQUIREMENTS.md` — PLAY-01 through PLAY-05

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ListQueue` — accepts pre-built `List<MediaItem>`, perfect for bridge playlist
- `SpotifyPlaylistQueue` — shows the batch resolution + silent skip pattern that bridge playlist needs
- `YouTube.search()` — existing InnerTube search for fuzzy matching tracks
- `MediaMetadata` / `toMediaItem()` — conversion functions for building playable items
- `PlayerConnection` — existing bridge between UI and MusicService

### Established Patterns
- Queues implement a common interface with `getInitialStatus()` and `getMoreSongs()`
- Track resolution happens in coroutines with `Dispatchers.IO`
- Failed tracks are logged and skipped (see `SpotifyPlaylistQueue`)

### Integration Points
- `BridgeViewModel` — add playlist building logic after bridge completes
- `MusicService` — load bridge queue via existing `playQueue()` mechanism
- `LastFM` module — add `artist.getTopTracks` method

</code_context>

<specifics>
## Specific Ideas

- The bridge path is just artist names — each needs track resolution, which is async and potentially slow (5-7 artists × 5-7 tracks = 25-49 YT Music searches). Consider batching or showing incremental progress.
- `SpotifyPlaylistQueue.kt` is the best reference for the "resolve batch of tracks, skip failures" pattern.
- The dialog for queue replacement should be simple Material 3 AlertDialog with two buttons.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 05-playlist-builder-auto-play*
*Context gathered: 2026-04-04*
