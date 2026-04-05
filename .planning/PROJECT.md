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

<!-- v2.0 features -->

- [ ] Path Walker mode — interactive FALA exploration with music playback
- [ ] Hyperbolic graph visualization — Poincare disk on Compose Canvas
- [ ] Bridge History — persist bridges and walks to Room DB with replay
- [ ] Bridge + Path Walker toggle on Bridge tab
- [ ] Track resolution improvements — reduce "no tracks found" rate

### Out of Scope

- Graph queue with destinations — deferred
- Unknown artist priority scoring — deferred
- Daily Bridge notifications — deferred
- Taste drift analytics — deferred
- Removing or modifying existing Meld features — not in scope
- Package renaming (com.metrolist.music) — deferred
- Google Play distribution — YT Music stream extraction incompatible with Play policies

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
- **Bridge algorithm**: Native Kotlin bidirectional beam search with Room DB caching (Phase 8); WebView retained but dormant
- **EccoPath bundling**: Git submodule, built assets packaged in APK — works offline
- **Distribution**: GitHub releases + sideload APK only
- **Branding**: App name "EccoMeld" with EccoMuse logo (validated Phase 1)
- **License**: GPL (inherited from Meld/InnerTune)
- **Package name**: Keep `com.metrolist.music` — renaming deferred

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Keep all existing Meld features | Bridge is additive, not a replacement — full music client stays intact | ✓ Good — all Meld features intact |
| WebView for bridge algorithm (MVP) | Ship fast, validate product, port to Kotlin later (~200 lines TS) | ✓ Validated Phase 1, superseded by native Kotlin in Phase 8 |
| Bundle EccoPath as git submodule | Offline-capable, no server dependency, reproducible builds | ✓ Good — works offline, but WebView now dormant |
| Native Kotlin bridge algorithm | WebView cold cache was too slow; native Kotlin with Room DB caching | ✓ Good — 5-10x faster, no WebView dependency |
| Progressive streaming | Auto-play first artist's tracks immediately, append rest as resolved | ✓ Good — music within seconds of path found |
| Stacked bridge cards | Multiple bridges as collapsible cards instead of single result | ✓ Good — enables bridge chaining |
| P0 MVP first, P1 in next milestone | Validate bridge concept before building staging/graph/history | ✓ Validated — bridge concept works |
| GitHub releases only | YT Music stream extraction incompatible with Google Play policies | — Ongoing |
| Keep com.metrolist.music package | Minimize diff with upstream, rename later if needed | — Deferred |

## Current State (v1.0 shipped 2026-04-05)

- 9 phases, 22 plans, 21 requirements — all complete
- Native Kotlin bidirectional beam search with Room DB two-level cache
- Progressive playlist streaming with auto-play
- Stacked collapsible bridge cards with replay
- Collab artist splitting, video fallback, Unicode track matching
- 42 files changed, ~6,300 lines added

## Current Milestone: v2.0 Path Walker & Discovery

**Goal:** Add interactive Path Walker exploration mode with hyperbolic graph visualization and persistent bridge history

**Target features:**
- Path Walker mode — pick 1 artist, see 5 FALA cards, choose direction, music plays as you walk
- Hyperbolic graph visualization — Poincare disk ported from EccoPath to Compose Canvas
- Bridge History — persist completed bridges and path walks to Room DB, replay from history
- Bridge + Path Walker toggle on the Bridge tab
- Track resolution improvements — reduce "no tracks found" rate

---
*Last updated: 2026-04-05 after v2.0 milestone start*
