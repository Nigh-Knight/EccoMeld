# Phase 6: Linear Path Result View - Context

**Gathered:** 2026-04-04
**Status:** Ready for planning

<domain>
## Phase Boundary

After a bridge completes, show a bottom sheet with a vertical list from seed artist (top) to target artist (bottom), with bridge artists in between. Each node shows genre tags and Last.fm listener counts. The currently-playing artist is visually highlighted. The path view persists while the playlist plays. Starting a new bridge replaces the old path (no history — deferred to P1).

</domain>

<decisions>
## Implementation Decisions

### Path View Location
- **D-01:** Bottom sheet that slides up over the Bridge tab content. The search inputs remain accessible above the sheet. The sheet can be expanded/collapsed.
- **D-02:** Bottom sheet appears automatically when bridge completes (transition from `Searching` → `PathFound`/`PlaylistReady`). Can be dismissed and re-opened.

### Artist Node Content
- **D-03:** Each bridge artist node shows genre tags and Last.fm listener count only. Clean and minimal — no artist thumbnails.
- **D-04:** Genre tags come from Last.fm artist info API (tags field). Listener count is the `listeners` field from the same API response.

### Now Playing Indicator
- **D-05:** The currently-playing bridge artist is visually highlighted in the path view. This requires observing `PlayerConnection`'s current media item and matching it to a bridge artist.

### Path Persistence
- **D-06:** Only one bridge path exists at a time. Starting a new bridge replaces the old path entirely. No bridge history — that's deferred to P1/next milestone.
- **D-07:** Path data persists in `BridgeViewModel` state (in-memory). Navigating away from Bridge tab and back preserves the path (ViewModel survives tab switches). But killing the app loses it — no database persistence for MVP.

### Claude's Discretion
- Bottom sheet implementation (Material 3 `BottomSheetScaffold` vs `ModalBottomSheet`)
- Peek height for the bottom sheet
- Visual styling of artist nodes (cards, dividers, spacing)
- How genre tags are displayed (chips, inline text, etc.)
- Animation for the "now playing" highlight
- How to fetch genre tags and listener counts (batch vs. on-demand)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Bridge State
- `app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/BridgeScreen.kt` — `BridgeUiState.PathFound(path)` and `PlaylistReady(path, nowPlayingIndex)` states that trigger the bottom sheet.
- `app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt` — Holds the bridge path state.

### Playback Connection
- `app/src/main/kotlin/com/metrolist/music/playback/PlayerConnection.kt` — Exposes current playing media item as StateFlow. Needed to highlight the current artist.

### Last.fm API
- `eccopath/lib/lastfm.ts` — `getArtistInfo()` returns tags and listener counts. Reference for the API shape.
- `lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt` — Will need `artist.getInfo` method added for tags + listener count.

### Existing Bottom Sheet Patterns
- `app/src/main/kotlin/com/metrolist/music/ui/menu/` — Context menus implemented as bottom sheets. Reference for bottom sheet conventions in this codebase.
- `app/src/main/kotlin/com/metrolist/music/ui/player/` — Player UI with bottom sheet patterns.

### Requirements
- `.planning/REQUIREMENTS.md` — BRDG-05 (linear path view), PLAY-06 (path persists during playback), PLAY-07 (now playing highlight)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `PlayerConnection` — already exposes current media metadata as StateFlow. Can derive current artist name.
- Material 3 bottom sheet components available in the project
- Existing menu bottom sheets in `ui/menu/` for pattern reference

### Established Patterns
- Bottom sheets used for context menus throughout the app
- `collectAsState()` for observing playback state in Composables
- `LaunchedEffect` for side effects when state changes

### Integration Points
- `BridgeScreen.kt` — wrap in `BottomSheetScaffold` or add `ModalBottomSheet`
- `BridgeViewModel.kt` — add artist metadata fetching (tags, listeners)
- `LastFM` module — add `artist.getInfo` method

</code_context>

<specifics>
## Specific Ideas

- The vertical path list should read top-to-bottom as the musical journey: seed at top, target at bottom, bridge artists in between
- "Now playing" highlight could be a colored left border or background tint on the current artist's card
- Genre tags as small Material 3 chips or simple text pills below the artist name

</specifics>

<deferred>
## Deferred Ideas

- Bridge history / replay — P1, deferred to next milestone
- Tapping a bridge artist to see more info — future enhancement

</deferred>

---

*Phase: 06-linear-path-result-view*
*Context gathered: 2026-04-04*
