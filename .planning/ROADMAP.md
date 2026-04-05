# Roadmap: EccoMeld — Bridge Discovery MVP

## Overview

EccoMeld adds Bridge Discovery to the existing Meld music client — a new tab where users pick two artists and the app finds a 5-7 hop genre path between them, then auto-plays a curated playlist through that journey. The work is entirely additive: all existing Meld functionality stays intact. Phases follow the technical dependency chain from the ground up: WebView infrastructure must be solid before the JS bridge, the JS bridge must work before the playlist builder, and playback must be verified before the UI that visualizes it. Spotify-dependent differentiators ship last so a Spotify auth fragility cannot block the core feature.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: WebView Foundation** - Bundle EccoPath as APK assets and verify IndexedDB-safe WebView serving (completed 2026-04-04)
- [ ] **Phase 2: Bridge Tab + State Model** - Add Bridge tab to navigation and define all UI states
- [x] **Phase 3: JS Bridge** - Wire MeldBridgeInterface so EccoPath can send bridge results to Kotlin (completed 2026-04-04)
- [x] **Phase 4: Artist Search + Bridge Trigger** - Two artist inputs with Last.fm autocomplete, loading feedback, error states (completed 2026-04-04)
- [ ] **Phase 5: Playlist Builder + Auto-Play** - Translate bridge path to YT Music tracks and auto-play through existing queue
- [ ] **Phase 6: Linear Path Result View** - Visualize the bridge journey with genre tags, listener counts, and playback highlight
- [ ] **Phase 7: Spotify Integration** - Seed suggestions, Random Bridge, and known/unknown artist badges

## Phase Details

### Phase 1: WebView Foundation
**Goal**: EccoPath loads correctly in a WebView served from a stable HTTPS origin, IndexedDB persists across restarts, and the app is branded as EccoMeld
**Depends on**: Nothing (first phase)
**Requirements**: INFRA-01, INFRA-02, INFRA-03, INFRA-04
**Success Criteria** (what must be TRUE):
  1. EccoPath renders in the WebView without 404s for any `/_next/` chunk
  2. IndexedDB cache written during one session is readable in a fresh app restart (Last.fm cache survives)
  3. The app name displays as "EccoMeld" in the launcher and app bar
  4. WebView is served from `https://appassets.androidplatform.net/` — never `file://`
**Plans:** 3/3 plans complete

Plans:
- [x] 01-01-PLAN.md — EccoPath submodule + Gradle build pipeline (static export to APK assets)
- [x] 01-02-PLAN.md — Rebrand text and icon to EccoMeld
- [x] 01-03-PLAN.md — Hilt singleton WebView with WebViewAssetLoader (HTTPS origin + IndexedDB)

### Phase 2: Bridge Tab + State Model
**Goal**: A Bridge tab exists in bottom navigation and the full BridgeUiState sealed class is defined with all transitions stubbed out
**Depends on**: Phase 1
**Requirements**: BRDG-07
**Success Criteria** (what must be TRUE):
  1. Bridge tab appears alongside existing Meld tabs (Home, Search, Library, etc.) and is tappable
  2. Tapping Bridge navigates to BridgeScreen without affecting any existing tab or its back stack
  3. All BridgeUiState transitions (Idle, Searching, PathFound, PlaylistReady, Error) exist in code and BridgeScreen responds to each — even if most show placeholder content
**Plans:** 1 plan

Plans:
- [x] 02-01-PLAN.md — Bridge tab navigation wiring + BridgeUiState sealed class + BridgeScreen placeholder

### Phase 3: JS Bridge
**Goal**: MeldBridgeInterface is registered and the round-trip from Kotlin → EccoPath JS → Kotlin callback works end-to-end with verified thread safety
**Depends on**: Phase 2
**Requirements**: BRDG-02, BRDG-03
**Success Criteria** (what must be TRUE):
  1. Calling `evaluateJavascript("EccoPath.startBridge(...)")` from Kotlin triggers beam search in the WebView
  2. `MeldBridgeInterface.createPlaylist(json)` fires with valid JSON when EccoPath completes a bridge
  3. The JS callback dispatches to the main thread without crashing — verified under concurrent execution
  4. Bridge result JSON is logged to Timber and visible in Logcat for a known artist pair
**Plans:** 3/3 plans complete

Plans:
- [x] 03-00-PLAN.md — Wave 0 test infrastructure (JUnit dependency + test stubs)
- [x] 03-01-PLAN.md — MeldBridgeInterface class + EccoPath window export + ProGuard keep rule
- [x] 03-02-PLAN.md — BridgeModule registration + BridgeViewModel startBridge() wiring

### Phase 4: Artist Search + Bridge Trigger
**Goal**: Users can enter two artists, start a bridge, see meaningful progress feedback, and see a clear error if no path is found
**Depends on**: Phase 3
**Requirements**: BRDG-01, BRDG-04, BRDG-06
**Success Criteria** (what must be TRUE):
  1. User can type a partial artist name and see Last.fm autocomplete suggestions (including niche/obscure artists)
  2. Tapping "Find Bridge" with two artists starts computation and shows step-by-step progress ("Found 3 of 6 hops…")
  3. Bridge UI is disabled while computation is running — a second tap cannot start a concurrent bridge run
  4. When no path exists, user sees a clear error message with a suggestion to try different artists
**Plans:** 3/3 plans complete

Plans:
- [x] 04-01-PLAN.md — Last.fm artist.search API + response model + test dependencies
- [x] 04-02-PLAN.md — BridgeViewModel autocomplete state + debounce + string resources + unit tests
- [x] 04-03-PLAN.md — BridgeScreen UI rewrite (ghost text, progress, error, button states)

### Phase 5: Playlist Builder + Auto-Play
**Goal**: A completed bridge path is automatically translated into a playable YT Music playlist and begins playing through the existing Meld player with no manual action required
**Depends on**: Phase 4
**Requirements**: PLAY-01, PLAY-02, PLAY-03, PLAY-04, PLAY-05
**Success Criteria** (what must be TRUE):
  1. When a bridge completes, a playlist starts playing immediately — user never taps a Play button
  2. Each bridge artist contributes 2 popular tracks + 3-5 deep cuts in genre-transition order
  3. Tracks are matched to playable YT Music video IDs via fuzzy artist+title matching
  4. Tracks that fail YT Music matching are silently skipped — the playlist continues without error dialogs
  5. Bridge playlist plays through the existing Meld player bar (mini and expanded) with full controls
**Plans:** 2/2 plans complete

Plans:
- [x] 05-01-PLAN.md — Last.fm getArtistTopTracks API + BridgePlaylistBuilder (track resolution pipeline)
- [x] 05-02-PLAN.md — BridgeViewModel wiring + queue dialog + auto-play integration

### Phase 6: Linear Path Result View
**Goal**: Users can see and follow their bridge journey in a persistent path view that highlights the currently-playing artist
**Depends on**: Phase 5
**Requirements**: BRDG-05, PLAY-06, PLAY-07
**Success Criteria** (what must be TRUE):
  1. After a bridge completes, user sees a vertical list from seed artist (top) to target artist (bottom) with bridge artists between
  2. Each bridge artist node shows genre tags and Last.fm listener counts
  3. The currently-playing bridge artist is visually highlighted in the path view
  4. Path view persists and remains accessible while the playlist is playing — navigating away and back does not lose it
**Plans:** 2 plans

Plans:
- [x] 06-01-PLAN.md — LastFM artist.getInfo API + BridgeViewModel metadata/now-playing logic
- [x] 06-02-PLAN.md — PathSheet composable + BridgeScreen BottomSheet integration + visual verification

### Phase 7: Spotify Integration
**Goal**: Users with Spotify connected get personalized seed suggestions, a one-tap Random Bridge from their library, and known/unknown badges on bridge artists
**Depends on**: Phase 6
**Requirements**: SPOT-01, SPOT-02, SPOT-03
**Success Criteria** (what must be TRUE):
  1. Users who have Spotify connected see their liked artists as quick-pick suggestions below the From/To search inputs
  2. Tapping "Random Bridge" picks two genre-opposite artists from Spotify liked songs and starts a bridge automatically
  3. Bridge artists in the path view show a "NEW" badge for artists not in the user's Spotify history and a "known" badge for familiar ones
  4. If Spotify is not connected or auth fails, all Bridge features still work — seed suggestions and Random Bridge are simply hidden
**Plans:** 2/3 plans executed
**UI hint**: yes

Plans:
- [x] 07-01-PLAN.md — BridgeViewModel data layer (seed suggestions, Jaccard random bridge, familiarity resolution, unit tests)
- [x] 07-02-PLAN.md — Seed suggestion chip row + Random Bridge FAB in BridgeScreen
- [x] 07-03-PLAN.md — Known/NEW familiarity badges in PathSheet + visual verification

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. WebView Foundation | 3/3 | Complete | 2026-04-04 |
| 2. Bridge Tab + State Model | 1/1 | Complete | 2026-04-04 |
| 3. JS Bridge | 3/3 | Complete | 2026-04-04 |
| 4. Artist Search + Bridge Trigger | 3/3 | Complete | 2026-04-04 |
| 5. Playlist Builder + Auto-Play | 2/2 | Complete | 2026-04-04 |
| 6. Linear Path Result View | 2/2 | Complete | 2026-04-04 |
| 7. Spotify Integration | 3/3 | Complete | 2026-04-04 |
| 8. Native Kotlin Bridge Algorithm | 1/3 | In Progress|  |
| 9. Bridge UI Redesign | 0/? | Not started | - |

### Phase 8: Native Kotlin Bridge Algorithm

**Goal:** Port the bridge beam search algorithm from TypeScript/WebView to native Kotlin coroutines with Room DB caching, eliminating the WebView dependency and cold cache performance problem
**Depends on:** Phase 4 (Last.fm API client)
**Requirements**: INFRA-01, BRDG-02, BRDG-03
**Success Criteria** (what must be TRUE):
  1. Bidirectional beam search runs as a Kotlin coroutine without any WebView involvement
  2. Last.fm similar-artist and tag data is cached in Room DB, persisting across app restarts
  3. Bridge search completes for a known artist pair (e.g., Radiohead → Kendrick Lamar) and returns a valid 5-7 hop path
  4. Rate limiting prevents Last.fm API throttling (<=5 req/sec)
  5. BridgeViewModel calls the Kotlin bridge directly — no evaluateJavascript
**Plans:** 1/3 plans executed

Plans:
- [x] 08-01-PLAN.md — LastFM getSimilarArtists API + Room entities + cache + rate limiter
- [ ] 08-02-PLAN.md — BridgeAlgorithm (Kotlin port of bidirectional beam search)
- [ ] 08-03-PLAN.md — BridgeViewModel rewiring + Hilt DI + updated tests

### Phase 9: Bridge UI Redesign

**Goal:** Replace the current side-by-side ghost text inputs with a progressive disclosure search UX — single input with dropdown suggestions, animated second input on artist confirmation, and polished state transitions
**Depends on:** Phase 8 (working native bridge)
**Requirements**: BRDG-01, BRDG-04, BRDG-06
**Success Criteria** (what must be TRUE):
  1. Single search input visible initially with standard dropdown autocomplete suggestions
  2. Selecting a suggestion confirms "From" artist and animates a second input into view
  3. Second input auto-focuses, full-width, with the same dropdown suggestion behavior
  4. Both artists confirmed triggers bridge search automatically
  5. State transitions (Idle → Searching → PathFound → PlaylistReady) are animated
**Plans:** 0 plans

Plans:
- [ ] TBD (run /gsd:plan-phase 9 to break down)
