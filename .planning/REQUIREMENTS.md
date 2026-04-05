# Requirements: EccoMeld v2.0

**Defined:** 2026-04-05
**Core Value:** Discover music through meaningful, human-like genre bridges between any two artists

## v2.0 Requirements

Requirements for Path Walker & Discovery milestone. Each maps to roadmap phases.

### Path Walker Core

- [ ] **WALK-01**: Path Walker is a dedicated screen with its own bottom nav tab ("Walk")
- [ ] **WALK-02**: Artist search input at the top — type an artist name to seed the graph
- [ ] **WALK-03**: Hyperbolic graph renders as a Poincare disk with seed artist at center and 5 FALA frontier nodes fanning outward
- [ ] **WALK-04**: User can tap a frontier node to expand it — fetches 5 new FALA artists, extends the graph, and appends 1-2 top tracks from the expanded artist to the playback queue
- [ ] **WALK-05**: Nodes display 7 distinct visual states (seed, frontier, loading, current, active, explored, error) with animated transitions
- [ ] **WALK-06**: User can pan and pinch-to-zoom the graph with touch gestures
- [ ] **WALK-07**: Active path highlighted on the graph; explored-but-abandoned branches stay visible as dimmed nodes
- [ ] **WALK-08**: User can tap any explored node to re-enter the walk from that point (branching)

### Walk Entry Points

- [ ] **ENTRY-01**: "Path Walk" option in song three-dot context menu across the entire app — opens Walk tab pre-seeded with that song's artist
- [ ] **ENTRY-02**: "Path Walk from here" on artist pages — opens Walk tab pre-seeded
- [ ] **ENTRY-03**: Walk tab can be opened directly from bottom nav with empty state and search input

### Walk Detail

- [ ] **DETAIL-01**: Artist detail bottom sheet shows artist image, genre tags, listener count, and match score when tapping an explored/active node

### History

- [ ] **HIST-01**: Completed bridge searches are persisted to Room DB and can be replayed from a history screen
- [ ] **HIST-02**: Path Walker sessions are persisted with full graph state and track list for replay
- [ ] **HIST-03**: History screen lists past bridges and walks with date, artists, and hop count

### Bridge Improvements

- [ ] **BRDG-08**: Track resolution improvements reduce "no tracks found" rate for bridge and walk artists

## Future Requirements

Deferred to later milestones.

### Sonic Track Sequencing (v3.0)

- **SONIC-01**: GetSongBPM / TheAudioDB / Gemini Flash integration for track metadata (BPM, key, energy, mood)
- **SONIC-02**: Sonic-aware track selection — pick tracks that transition smoothly between adjacent bridge artists
- **SONIC-03**: Crossfade optimization — use BPM matching to set crossfade timing
- **SONIC-04**: Deep cut priority — prefer obscure tracks that sonically bridge genres over popular hits
- **SONIC-05**: Boil the Frog mode — entire playlist is one continuous sonic gradient

### Extended Features

- **EXTD-01**: Bridge Radio — expanded playlist with similar artists beyond the target
- **EXTD-02**: Unknown artist priority scoring in walk/bridge results
- **EXTD-03**: Configurable tracks-per-node (1 / 3 / 5) for walk pace control

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Walk & Listen (auto-pilot mode) | Too similar to existing Spotify/YT Music radio |
| Social sharing of bridges/walks | No backend, personal use only |
| Graph force-directed layout | Hyperbolic layout is superior for tree exploration |
| Walk breadcrumb trail | Graph itself serves as the trail — breadcrumb is redundant for branching paths |
| MiniGraph thumbnail | Pinch-to-zoom on the graph serves the same purpose |
| Daily Bridge notifications | Deferred |
| Taste drift analytics | Deferred |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| WALK-01 | — | Pending |
| WALK-02 | — | Pending |
| WALK-03 | — | Pending |
| WALK-04 | — | Pending |
| WALK-05 | — | Pending |
| WALK-06 | — | Pending |
| WALK-07 | — | Pending |
| WALK-08 | — | Pending |
| ENTRY-01 | — | Pending |
| ENTRY-02 | — | Pending |
| ENTRY-03 | — | Pending |
| DETAIL-01 | — | Pending |
| HIST-01 | — | Pending |
| HIST-02 | — | Pending |
| HIST-03 | — | Pending |
| BRDG-08 | — | Pending |

**Coverage:**
- v2.0 requirements: 16 total
- Mapped to phases: 0
- Unmapped: 16

---
*Requirements defined: 2026-04-05*
