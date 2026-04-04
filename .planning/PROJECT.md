# EccoMeld

## What This Is

EccoMeld is a music discovery and playback app for Android that fuses the bridge-finding algorithm from EccoPath with the full-featured YouTube Music playback stack from Meld. Users pick two artists from different corners of music, and EccoMeld finds a 5-7 hop path through niche midpoint artists, then builds a genre-transitioning playlist that plays via YouTube Music streams. The existing Meld music client (home feed, search, library, player, lyrics, etc.) stays fully intact — Bridge is added as a new tab.

## Core Value

Discover music you didn't know you wanted through meaningful, human-like genre bridges between any two artists — not algorithmic recommendations pushing the same popular songs.

## Requirements

### Validated

<!-- Existing capabilities from the Meld codebase -->

- ✓ YouTube Music playback via Media3/ExoPlayer — existing
- ✓ Background playback with media notification, lock screen, Bluetooth controls — existing
- ✓ Persistent player bar (mini + expanded full player) — existing
- ✓ Home feed with personalized content from YouTube Music — existing
- ✓ Search (songs, artists, albums, playlists) via InnerTube API — existing
- ✓ Library management (liked songs, playlists, albums, artists) — existing
- ✓ Playlist creation and management — existing
- ✓ Queue management with shuffle/repeat — existing
- ✓ Spotify authentication (WebView OAuth, data-only) — existing
- ✓ Spotify liked songs and top artists import — existing
- ✓ Lyrics display from multiple providers (LrcLib, Kugou, BetterLyrics) — existing
- ✓ Room database for local persistence (songs, artists, albums, playlists, events) — existing
- ✓ Material You (Material 3) theming with dynamic colors from album art — existing
- ✓ Download songs for offline playback — existing
- ✓ Last.fm scrobbling integration — existing
- ✓ Discord Rich Presence via Kizzy — existing
- ✓ Shazam-like song recognition via ShazamKit — existing
- ✓ Android Auto support — existing
- ✓ FOSS and GMS build variants — existing
- ✓ Single-activity Compose UI with bottom navigation — existing

### Active

<!-- New bridge discovery features — P0 MVP scope -->

- [ ] Bridge tab in bottom navigation — two search inputs (From/To artist)
- [ ] WebView bridge computation — bundled EccoPath (git submodule) runs beam search locally
- [ ] JS bridge interface — MeldBridge.createPlaylist(json) sends bridge path to Kotlin
- [ ] Linear path result view — seed at top, target at bottom, bridge artists between with genre tags and listener counts
- [ ] Unified playlist builder — 2 popular + 3-5 deep cuts per bridge artist, genre-transition order
- [ ] Auto-play on bridge completion — playlist feeds into existing Meld queue/player
- [ ] YT Music track matching — fuzzy match artist+track to YT Music video IDs for each bridge artist's tracks
- [ ] Random Bridge button — picks two genre-opposite artists from Spotify liked songs via Tag Jaccard distance
- [ ] Spotify liked artists as seed suggestions — "Bridge from your taste"
- [ ] Silent skip on YT Music match failures — don't break playlist flow for unavailable tracks

### Out of Scope

- Staging cards / path walker UI — P1, deferred to next milestone
- Graph queue with destinations — P1, deferred
- Unknown artist priority scoring — P1, deferred
- Bridge radio (expanded playlist) — P1, deferred
- Bridge history / replay — P1, deferred
- Walk & Listen mode — P2, deferred
- Daily Bridge notifications — P2, deferred
- Taste drift analytics — P2, deferred
- Native Kotlin port of bridge algorithm — post-MVP, WebView-first approach validated
- Removing or modifying existing Meld features — not in scope, everything stays
- Package renaming (com.metrolist.music) — deferred, keep existing for now
- Google Play distribution — YT Music stream extraction would be rejected

## Context

**Lineage:** EccoMeld builds on top of the SimplMusic/Metrolist/Meld codebase — a mature, full-featured YouTube Music client written in Kotlin with Jetpack Compose. The codebase has 1000+ files, 36 ViewModels, a 1747-line monolithic DAO, and a 3460-line MusicService.

**EccoPath:** A separate Next.js/TypeScript PWA at `/home/kepler/Projects/EccoPath` that handles all bridge-finding logic — bidirectional beam search with tag heuristics, Last.fm API integration, niche-weighted scoring. For MVP, EccoPath is bundled as a git submodule and loaded in a WebView. The bridge algorithm is ~200 lines of TypeScript.

**Key EccoPath files:**
- `lib/bridgeCrawl.ts` — beam search + fallback BFS + degree-weighted Dijkstra
- `lib/lastfm.ts` — Last.fm API client with IndexedDB persistent cache
- `lib/hyperbolicLayout.ts` — Poincaré disk graph layout (not needed for MVP)

**Integration points with existing Meld code:**
- `playback/` — feed bridge playlist into existing queue system
- `ui/player/` — bridge playlist uses existing player bar
- `db/` — extend Room DB with bridge history tables (P1)
- `spotify/` — use existing auth for liked artists + known artist detection
- `innertube/` — existing YT Music search for matching bridge artists to tracks
- `lastfm/` — module already exists, check capabilities before duplicating

**License:** Meld/InnerTune lineage is GPL-licensed. EccoMeld must also be GPL.

## Constraints

- **Platform**: Android only (SDK 26+, targeting SDK 36)
- **Playback**: Must use existing Media3/ExoPlayer stack — no rewrite
- **Bridge algorithm**: WebView + JS bridge for MVP — no native Kotlin port yet
- **EccoPath bundling**: Git submodule, built assets packaged in APK — works offline
- **Distribution**: GitHub releases + sideload APK only
- **Branding**: App name "EccoMeld" with EccoMuse logo (validated Phase 1)
- **License**: GPL (inherited from Meld/InnerTune)
- **Package name**: Keep `com.metrolist.music` — renaming deferred

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Keep all existing Meld features | Bridge is additive, not a replacement — full music client stays intact | — Pending |
| WebView for bridge algorithm (MVP) | Ship fast, validate product, port to Kotlin later (~200 lines TS) | Validated Phase 1 |
| Bundle EccoPath as git submodule | Offline-capable, no server dependency, reproducible builds | Validated Phase 1 |
| P0 MVP first, P1 in next milestone | Validate bridge concept before building staging/graph/history | — Pending |
| GitHub releases only | YT Music stream extraction incompatible with Google Play policies | — Pending |
| Keep com.metrolist.music package | Minimize diff with upstream, rename later if needed | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd:transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-04 after Phase 1 completion — WebView Foundation*
