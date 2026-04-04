---
phase: 05-playlist-builder-auto-play
plan: "01"
subsystem: playback/lastfm
tags: [lastfm, playlist-builder, youtube-search, fuzzy-match, tdd]
dependency_graph:
  requires:
    - "lastfm module with Ktor client (Phase 04)"
    - "SpotifyMapper.matchScore (existing)"
    - "YouTube.search with FILTER_SONG (existing innertube)"
    - "SongItem.toMediaItem() extension (existing app/extensions)"
  provides:
    - "LastFM.getArtistTopTracks() — ranked top tracks per artist"
    - "ArtistTopTracksResponse — deserialized Last.fm toptracks JSON"
    - "BridgePlaylistBuilder.buildForPath() — bridge path → ordered MediaItems"
    - "BridgePlaylistBuilder.selectTracks() — popular + deep cut selection logic"
    - "BridgePlaylistBuilder.resolveOneTrack() — YT Music fuzzy match with 0.35 threshold"
  affects:
    - "Phase 05-02: BridgeViewModel auto-play (consumes BridgePlaylistBuilder)"
tech_stack:
  added: []
  patterns:
    - "Unauthenticated Last.fm GET (same pattern as searchArtists — no api_sig)"
    - "Kotlin Hilt @Inject constructor on utility class"
    - "coroutineScope + async/awaitAll for parallel per-artist track resolution"
    - "SpotifyMapper.matchScore reused for non-Spotify fuzzy matching (title+artist only, duration=0/null)"
    - "Timber.w() for silent skip logging (PLAY-04)"
    - "@Ignore stubs for integration tests requiring singleton mocking (Wave 0 pattern)"
key_files:
  created:
    - lastfm/src/main/kotlin/com/metrolist/lastfm/models/ArtistTopTracksResponse.kt
    - app/src/main/kotlin/com/metrolist/music/playback/BridgePlaylistBuilder.kt
    - app/src/test/kotlin/com/metrolist/music/bridge/BridgePlaylistBuilderTest.kt
  modified:
    - lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt
decisions:
  - "SpotifyMapper.matchScore reused with spotifyDurationMs=0 and candidateDurationSec=null — neutral 0.5 duration score, title+artist carry full weight"
  - "MIN_MATCH_THRESHOLD = 0.35 (not 0.4) — lower than Spotify import threshold to account for artist name variations in bridge context"
  - "Ranks 3-9 excluded intentionally — avoids 'mid-tier' overlap between popular anthems and deep cuts"
  - "Deep cuts capped at 5 per artist (coerceIn not needed — take(5) on shuffled list is safe)"
  - "Pre-existing MessageCodec.kt compile error in worktree branch logged as out-of-scope; BridgePlaylistBuilder.kt and tests compile clean"
metrics:
  duration: "8 minutes"
  completed: "2026-04-04"
  tasks: 2
  files: 4
---

# Phase 05 Plan 01: Last.fm Top Tracks + BridgePlaylistBuilder Summary

**One-liner:** Last.fm `getArtistTopTracks()` + `BridgePlaylistBuilder` resolve bridge artist paths to ordered YT Music MediaItems using rank-based track selection (top-2 popular + up to 5 rank-10-50 deep cuts) and 0.35-threshold Dice-coefficient fuzzy matching.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Last.fm getArtistTopTracks API + response model | db891f67 | `ArtistTopTracksResponse.kt`, `LastFM.kt` |
| 2 | BridgePlaylistBuilder with unit tests | f8311c37 | `BridgePlaylistBuilder.kt`, `BridgePlaylistBuilderTest.kt` |

## What Was Built

### ArtistTopTracksResponse (Task 1)

Deserializes Last.fm `artist.getTopTracks` JSON response:
- `toptracks.track[].name` — track name
- `toptracks.track[].@attr.rank` — chart rank (as string, serialized via `@SerialName("@attr")`)
- `TopTrack.rankInt` — computed property, parses rank string to Int, defaults to 999 on failure
- Includes optional `playcount`, `listeners`, and nested `artist.name`

### LastFM.getArtistTopTracks (Task 1)

Unauthenticated GET following the same pattern as `searchArtists()`:
- Returns `Result.failure` when `API_KEY` is empty
- Parameters: `method=artist.getTopTracks`, `artist`, `limit` (default 50), `api_key`, `format=json`

### BridgePlaylistBuilder (Task 2)

Core resolution engine for Phase 5:

- `buildForPath(path)`: iterates artists in order (seed → bridges → target), fetches Last.fm top tracks, selects tracks, resolves to YT Music MediaItems in parallel per artist. Failed Last.fm fetches skip that artist without failing the whole playlist.
- `selectTracks(tracks)`: picks rank 1-2 as "popular", rank 10-50 as "deep cuts" (shuffled, up to 5). Ranks 3-9 intentionally excluded.
- `resolveOneTrack(artist, title)`: queries `YouTube.search("$artist $title", FILTER_SONG)`, scores all `SongItem` candidates via `SpotifyMapper.matchScore`, returns best as `MediaItem` if score ≥ 0.35, else null with `Timber.w` log.

### BridgePlaylistBuilderTest (Task 2)

Unit tests for `selectTracks` covering:
- 50-track input → 2 popular + 5 deep cuts (7 total)
- Popular always appear before deep cuts
- Ranks 3-9 never included
- Fewer than 2 tracks → all available returned
- Empty input → empty output
- Deep cuts capped at 5 even when more are available

`resolveOneTrack` and `buildForPath` tests are `@Ignore` stubs (same Wave 0 pattern as Phase 3 — require YouTube singleton mocking).

## Deviations from Plan

### Pre-existing Issue (Out of Scope)

**MessageCodec.kt compile errors** — the worktree branch is missing Phase 03/04 commits that resolved proto import issues. All errors are in `listentogether/MessageCodec.kt`, not in any files touched by this plan. `BridgePlaylistBuilder.kt` and all created files compile without errors. Logged to deferred-items.

### Auto-added (Rule 2)

None — implementation matched plan specification exactly.

## Known Stubs

- `resolveOneTrack` integration test stubs are intentional `@Ignore` entries (Wave 0 pattern). They will be activated in Phase 5 integration testing once YouTube singleton mocking strategy is established.

## Self-Check: PASSED

- `lastfm/src/main/kotlin/com/metrolist/lastfm/models/ArtistTopTracksResponse.kt` — FOUND
- `lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt` — FOUND (modified)
- `app/src/main/kotlin/com/metrolist/music/playback/BridgePlaylistBuilder.kt` — FOUND
- `app/src/test/kotlin/com/metrolist/music/bridge/BridgePlaylistBuilderTest.kt` — FOUND
- Commit `db891f67` — FOUND
- Commit `f8311c37` — FOUND
