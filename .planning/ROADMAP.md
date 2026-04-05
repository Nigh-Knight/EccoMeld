# Roadmap: EccoMeld

## Milestones

- ✅ **v1.0 Bridge Discovery MVP** - Phases 1-9 (shipped 2026-04-05)
- 🚧 **v2.0 Path Walker & Discovery** - Phases 10-14 (in progress)

## Phases

<details>
<summary>✅ v1.0 Bridge Discovery MVP (Phases 1-9) — SHIPPED 2026-04-05</summary>

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: WebView Foundation** - Bundle EccoPath as APK assets and verify IndexedDB-safe WebView serving (completed 2026-04-04)
- [x] **Phase 2: Bridge Tab + State Model** - Add Bridge tab to navigation and define all UI states (completed 2026-04-04)
- [x] **Phase 3: JS Bridge** - Wire MeldBridgeInterface so EccoPath can send bridge results to Kotlin (completed 2026-04-04)
- [x] **Phase 4: Artist Search + Bridge Trigger** - Two artist inputs with Last.fm autocomplete, loading feedback, error states (completed 2026-04-04)
- [x] **Phase 5: Playlist Builder + Auto-Play** - Translate bridge path to YT Music tracks and auto-play through existing queue (completed 2026-04-04)
- [x] **Phase 6: Linear Path Result View** - Visualize the bridge journey with genre tags, listener counts, and playback highlight (completed 2026-04-04)
- [x] **Phase 7: Spotify Integration** - Seed suggestions, Random Bridge, and known/unknown artist badges (completed 2026-04-04)
- [x] **Phase 8: Native Kotlin Bridge Algorithm** - Port beam search to native Kotlin coroutines with Room DB caching (completed 2026-04-05)
- [x] **Phase 9: Bridge UI Redesign** - Progressive disclosure search UX with dropdown autocomplete and animated transitions (completed 2026-04-05)

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
**Plans:** 1/1 plan complete

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
**Plans:** 2/2 plans complete

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
**Plans:** 3/3 plans complete
**UI hint**: yes

Plans:
- [x] 07-01-PLAN.md — BridgeViewModel data layer (seed suggestions, Jaccard random bridge, familiarity resolution, unit tests)
- [x] 07-02-PLAN.md — Seed suggestion chip row + Random Bridge FAB in BridgeScreen
- [x] 07-03-PLAN.md — Known/NEW familiarity badges in PathSheet + visual verification

### Phase 8: Native Kotlin Bridge Algorithm
**Goal**: Port the bridge beam search algorithm from TypeScript/WebView to native Kotlin coroutines with Room DB caching, eliminating the WebView dependency and cold cache performance problem
**Depends on**: Phase 4
**Requirements**: INFRA-01, BRDG-02, BRDG-03
**Success Criteria** (what must be TRUE):
  1. Bidirectional beam search runs as a Kotlin coroutine without any WebView involvement
  2. Last.fm similar-artist and tag data is cached in Room DB, persisting across app restarts
  3. Bridge search completes for a known artist pair (e.g., Radiohead → Kendrick Lamar) and returns a valid 5-7 hop path
  4. Rate limiting prevents Last.fm API throttling (<=5 req/sec)
  5. BridgeViewModel calls the Kotlin bridge directly — no evaluateJavascript
**Plans:** 3/3 plans complete

Plans:
- [x] 08-01-PLAN.md — LastFM getSimilarArtists API + Room entities + cache + rate limiter
- [x] 08-02-PLAN.md — BridgeAlgorithm (Kotlin port of bidirectional beam search)
- [x] 08-03-PLAN.md — BridgeViewModel rewiring + Hilt DI + updated tests

### Phase 9: Bridge UI Redesign
**Goal**: Replace the current side-by-side ghost text inputs with a progressive disclosure search UX — single input with dropdown suggestions, animated second input on artist confirmation, and polished state transitions
**Depends on**: Phase 8
**Requirements**: BRDG-01, BRDG-04, BRDG-06
**Success Criteria** (what must be TRUE):
  1. Single search input visible initially with standard dropdown autocomplete suggestions
  2. Selecting a suggestion confirms "From" artist and animates a second input into view
  3. Second input auto-focuses, full-width, with the same dropdown suggestion behavior
  4. Both artists confirmed triggers bridge search automatically
  5. State transitions (Idle → Searching → PathFound → PlaylistReady) are animated
**Plans:** 2/2 plans complete

Plans:
- [x] 09-01-PLAN.md — BridgeViewModel: replace ghost suffix with suggestion lists + clearFrom/clearTo + auto-trigger
- [x] 09-02-PLAN.md — BridgeScreen: progressive disclosure UI with dropdown autocomplete + animated transitions

</details>

---

### 🚧 v2.0 Path Walker & Discovery (In Progress)

**Milestone Goal:** Add interactive Path Walker exploration mode with hyperbolic graph visualization and persistent bridge history. Users pick one artist, see 5 FALA nodes fanning out on a Poincare disk, tap to expand and walk — music plays at every step. Completed bridges and walks persist to Room DB for replay.

- [ ] **Phase 10: PathWalker Foundation — ViewModel + Mode Toggle + Entry Points** - Refactor BridgeScreen with mode toggle, build PathWalkerViewModel with NodeState machine and FALA expansion, wire all entry points, improve track resolution
- [ ] **Phase 11: Hyperbolic Math + Canvas Renderer** - Port computeHyperbolicLayout() to Kotlin with unit tests, build HyperbolicGraphCanvas composable with 7-state node rendering and pan/zoom gestures
- [ ] **Phase 12: Interactive Graph — Expansion, Playback, and Active Path** - Wire tap-to-expand to FALA fetches + queue append, highlight the active path, support branching from explored nodes
- [ ] **Phase 13: Artist Detail Sheet** - Bottom sheet on node tap with artist image, genre tags, listener count, and match score
- [ ] **Phase 14: Bridge History Persistence** - Room migration with three new tables, history UI screen, bridge and walk replay

## Phase Details

### Phase 10: PathWalker Foundation — ViewModel + Mode Toggle + Entry Points
**Goal**: Users can switch between Bridge and Walk modes in the Bridge tab, seed a Walk from any artist entry point in the app, and Path Walker resolves tracks more reliably than before
**Depends on**: Phase 9
**Requirements**: WALK-01, WALK-02, ENTRY-01, ENTRY-02, ENTRY-03, BRDG-08
**Success Criteria** (what must be TRUE):
  1. A segmented toggle (Bridge / Walk) appears at the top of the Bridge tab — tapping it switches modes without losing the other mode's in-progress state
  2. User can type one artist name in Walk mode and see autocomplete suggestions, then confirm to seed the graph (which fetches and displays 5 FALA frontier nodes as a placeholder list)
  3. Tapping "Path Walk" in any song's three-dot menu opens the Bridge tab in Walk mode pre-seeded with that song's artist
  4. Tapping "Path Walk from here" on an artist page opens the Bridge tab in Walk mode pre-seeded with that artist
  5. Opening the Bridge tab directly with Walk selected shows the empty-state search input ready for use
  6. Track resolution for bridge and walk artists succeeds more often — "no tracks found" rate is visibly reduced compared to v1.0
**Plans**: TBD
**UI hint**: yes

### Phase 11: Hyperbolic Math + Canvas Renderer
**Goal**: A correct, performant Poincare disk renders on screen with all 7 node states visually distinct and pan/zoom gestures working accurately
**Depends on**: Phase 10
**Requirements**: WALK-03, WALK-05, WALK-06
**Success Criteria** (what must be TRUE):
  1. computeHyperbolicLayout() passes unit tests for known inputs — positions stay inside the disk boundary, deep-tree nodes do not produce NaN or Infinity coordinates
  2. The Poincare disk renders with the seed artist at center, frontier nodes fanning outward, connected by geodesic arcs
  3. All 7 node states (seed, frontier, loading, current, active, explored, error) are visually distinct — colored per state, animated transitions between states
  4. User can pan the graph by dragging and pinch-to-zoom the disk — tapping a node after zooming in still hits the correct node (coordinate inversion applied)
  5. At 100+ nodes, the canvas maintains smooth rendering — drawWithCache used for static layers, live layer for animated elements only
**Plans**: TBD
**UI hint**: yes

### Phase 12: Interactive Graph — Expansion, Playback, and Active Path
**Goal**: Tapping a frontier node fetches its FALA children, extends the graph, starts playing that artist's tracks, and the user's path through the graph is clearly visible and reenterable
**Depends on**: Phase 11
**Requirements**: WALK-04, WALK-07, WALK-08
**Success Criteria** (what must be TRUE):
  1. Tapping a frontier node transitions it to loading state, fetches 5 FALA children, transitions to explored, and adds children as new frontier nodes — all while the existing music keeps playing
  2. 1-2 top tracks from the tapped artist are appended to the playback queue — music starts playing when the queue reaches that artist's tracks
  3. The active path through the graph is visually highlighted (distinct edge color); explored-but-abandoned branches remain visible as dimmed nodes
  4. Tapping any explored or active node re-enters the walk from that point — frontier nodes are fetched for that node's children and the active path is updated to reflect the rewind
**Plans**: TBD
**UI hint**: yes

### Phase 13: Artist Detail Sheet
**Goal**: Users can learn about any artist on the graph without leaving the walk
**Depends on**: Phase 12
**Requirements**: DETAIL-01
**Success Criteria** (what must be TRUE):
  1. Tapping an explored, active, or seed node opens a bottom sheet showing that artist's image, genre tags, Last.fm listener count, and similarity match score
  2. The sheet dismisses without changing any node state — opening the sheet is non-destructive to the walk
  3. Data loads from the existing BridgeArtistMetaEntity cache where available, with a shimmer placeholder while fetching
**Plans**: TBD
**UI hint**: yes

### Phase 14: Bridge History Persistence
**Goal**: Completed bridges and path walks are saved to Room DB and can be replayed from a history screen, so users never lose a discovery session
**Depends on**: Phase 12
**Requirements**: HIST-01, HIST-02, HIST-03
**Success Criteria** (what must be TRUE):
  1. Completing a bridge search persists it to a history list — closing and reopening the app shows the entry in history
  2. Completing or abandoning a path walk session persists it with full graph state — closing and reopening shows the walk entry in history
  3. A history screen (accessible from the Bridge tab header) lists past bridges and walks with session type, date, artists involved, and hop/step count
  4. Tapping replay on a past bridge re-queues its stored tracks and begins playback immediately — no re-running the algorithm
  5. Tapping replay on a past walk re-queues its stored tracks in walk order and begins playback immediately
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 10 → 11 → 12 → 13 → 14

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. WebView Foundation | v1.0 | 3/3 | Complete | 2026-04-04 |
| 2. Bridge Tab + State Model | v1.0 | 1/1 | Complete | 2026-04-04 |
| 3. JS Bridge | v1.0 | 3/3 | Complete | 2026-04-04 |
| 4. Artist Search + Bridge Trigger | v1.0 | 3/3 | Complete | 2026-04-04 |
| 5. Playlist Builder + Auto-Play | v1.0 | 2/2 | Complete | 2026-04-04 |
| 6. Linear Path Result View | v1.0 | 2/2 | Complete | 2026-04-04 |
| 7. Spotify Integration | v1.0 | 3/3 | Complete | 2026-04-04 |
| 8. Native Kotlin Bridge Algorithm | v1.0 | 3/3 | Complete | 2026-04-05 |
| 9. Bridge UI Redesign | v1.0 | 2/2 | Complete | 2026-04-05 |
| 10. PathWalker Foundation | v2.0 | 0/? | Not started | - |
| 11. Hyperbolic Math + Canvas | v2.0 | 0/? | Not started | - |
| 12. Interactive Graph | v2.0 | 0/? | Not started | - |
| 13. Artist Detail Sheet | v2.0 | 0/? | Not started | - |
| 14. Bridge History Persistence | v2.0 | 0/? | Not started | - |
