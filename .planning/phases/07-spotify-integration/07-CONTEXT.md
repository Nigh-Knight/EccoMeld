# Phase 7: Spotify Integration - Context

**Gathered:** 2026-04-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Add personalization features to the Bridge tab: seed suggestions from BOTH Spotify liked artists AND YT Music library artists (mixed together as horizontal scrollable chips), a Random Bridge FAB that picks two genre-opposite artists and auto-starts a bridge, and known/NEW badges on bridge artists in the path view. All features gracefully degrade — if neither Spotify nor YT Music library has enough artists, the features are simply hidden.

</domain>

<decisions>
## Implementation Decisions

### Seed Suggestions
- **D-01:** Horizontal scrollable row of artist name chips below the From/To search inputs. Tapping a chip fills the corresponding input (From or To, whichever is empty/focused).
- **D-02:** Sources are BOTH Spotify liked artists AND YT Music library artists, mixed together in a single row. Not separated into two rows.
- **D-03:** YT Music library artists come from the existing Room database (`ArtistEntity` table). Spotify liked artists come from the existing `Spotify` module.
- **D-04:** Chips are deduplicated by artist name (case-insensitive). If an artist exists in both sources, show once.

### Random Bridge
- **D-05:** Floating Action Button (FAB) on the Bridge screen. Always visible — not gated behind Spotify auth.
- **D-06:** Random Bridge works with YT Music library artists even without Spotify connected. If neither source has enough artists (minimum 2), the FAB shows a toast: "Add some artists to your library first."
- **D-07:** Genre-opposite selection uses Tag Jaccard distance: fetch Last.fm tags for candidate artists, pick the pair with the lowest Jaccard similarity (most genre-diverse).
- **D-08:** Tapping FAB immediately starts a bridge with the two selected artists — fills the inputs and triggers `startBridge()`.

### Known/NEW Badges
- **D-09:** Bridge artists in the path view (Phase 6 bottom sheet) show badges: "NEW" for artists not in user's listening history, "known" for familiar ones.
- **D-10:** "Known" = artist exists in the Room database `ArtistEntity` table OR in Spotify liked artists. "NEW" = not found in either source.
- **D-11:** Badge styling is Claude's discretion — whatever fits the Material 3 theme best. Small colored pills, icons, or subtle indicators are all fine.

### Graceful Degradation
- **D-12:** If Spotify is not connected, seed suggestions show only YT Music library artists. If YT Music library is also empty, the suggestions row is hidden entirely.
- **D-13:** If Spotify auth fails at runtime, catch the error silently and fall back to YT Music library artists only. Never show Spotify auth errors on the Bridge tab.

### Claude's Discretion
- Badge visual design (pill, icon, chip style — whatever fits Material 3)
- Chip row height and spacing
- How many seed suggestions to show (all vs. capped at N)
- Tag Jaccard implementation details
- FAB icon design
- How to handle the case where the random pair includes an artist the user just bridged

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Spotify Integration
- `spotify/src/main/kotlin/com/metrolist/spotify/` — Existing Spotify API client module
- `app/src/main/kotlin/com/metrolist/music/utils/SpotifyTokenManager.kt` — Token management and auth state
- `app/src/main/kotlin/com/metrolist/music/App.kt` — Where Spotify auth is initialized (look for `Spotify.accessToken`)

### YT Music Library
- `app/src/main/kotlin/com/metrolist/music/db/DatabaseDao.kt` — `ArtistEntity` queries for library artists
- `app/src/main/kotlin/com/metrolist/music/db/MusicDatabase.kt` — Database wrapper

### Bridge UI
- `app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/BridgeScreen.kt` — Where seed suggestions, FAB, and input chips go
- `app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt` — Add seed suggestion loading and random bridge logic

### Last.fm Tags
- `eccopath/lib/lastfm.ts` — `getArtistInfo()` returns tags needed for Jaccard distance
- `lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt` — Will need `artist.getTopTags` or `artist.getInfo` for tag data

### Requirements
- `.planning/REQUIREMENTS.md` — SPOT-01 (seed suggestions), SPOT-02 (random bridge), SPOT-03 (known/NEW badges)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Spotify` singleton — existing API client with liked artists endpoint
- `MusicDatabase` / `DatabaseDao` — existing artist queries from Room DB
- `SpotifyTokenManager` — handles auth state, can check if Spotify is connected
- `LastFM` module — will have `artist.search` and `artist.getInfo` from Phase 4 and 6

### Established Patterns
- `viewModelScope.launch(Dispatchers.IO)` for data fetching
- `MutableStateFlow` for exposing loaded data to UI
- Material 3 chips (`AssistChip`, `FilterChip`) available in the design system
- FAB patterns exist in Material 3 Compose

### Integration Points
- `BridgeScreen.kt` — add chip row, FAB, badges in path view nodes
- `BridgeViewModel.kt` — add seed loading, random bridge logic, known/new resolution
- `LastFM` module — tag fetching for Jaccard distance

</code_context>

<specifics>
## Specific Ideas

- Seed suggestions should load eagerly when Bridge tab is opened (not on-demand)
- Random Bridge is the "delight" feature — make the FAB visually inviting
- Tag Jaccard: fetch tags for a sample of library artists, cache them, pick the most genre-diverse pair

</specifics>

<deferred>
## Deferred Ideas

- Expanded seed suggestions with genre grouping — future enhancement
- "Bridge from your mood" feature — P2
- Smart random that avoids recently-bridged pairs — future enhancement

</deferred>

---

*Phase: 07-spotify-integration*
*Context gathered: 2026-04-04*
