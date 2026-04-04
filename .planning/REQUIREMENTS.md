# Requirements: EccoMeld

**Defined:** 2026-04-03
**Core Value:** Discover music through meaningful, human-like genre bridges between any two artists

## v1 Requirements

Requirements for MVP (P0) release. Each maps to roadmap phases.

### Infrastructure

- [x] **INFRA-01**: EccoPath bundled as git submodule with static export packaged as APK assets
- [x] **INFRA-02**: WebView loads bundled EccoPath via WebViewAssetLoader (HTTPS origin, not file://)
- [x] **INFRA-03**: IndexedDB cache persists across WebView sessions (Last.fm cache survives app restart)
- [x] **INFRA-04**: App name displayed as "EccoMeld" (launcher label, app bar)

### Bridge Discovery

- [x] **BRDG-01**: User can enter a "From" artist and a "To" artist via search inputs with Last.fm autocomplete
- [x] **BRDG-02**: Bridge computation runs via EccoPath WebView beam search when user taps "Bridge"
- [x] **BRDG-03**: JS bridge interface (MeldBridge) sends bridge path result from EccoPath to Kotlin
- [x] **BRDG-04**: User sees meaningful loading feedback during bridge computation ("Found 3 of 6 hops...")
- [ ] **BRDG-05**: User sees a linear path result view — seed at top, target at bottom, bridge artists between with genre tags and listener counts
- [x] **BRDG-06**: User sees a clear error message when no path is found, with suggestion to try different artists
- [x] **BRDG-07**: Bridge tab appears in bottom navigation alongside existing Meld tabs

### Playback Integration

- [x] **PLAY-01**: Unified playlist auto-plays immediately when bridge is found — no manual "play" action needed
- [x] **PLAY-02**: Track selection per bridge artist: 2 popular tracks + 3-5 deep cuts, genre-transition order preserved
- [x] **PLAY-03**: YT Music fuzzy matching resolves each bridge artist's tracks to playable YT Music video IDs
- [x] **PLAY-04**: YT Music match failures skip silently — unavailable tracks don't break playlist flow
- [x] **PLAY-05**: Bridge playlist feeds into existing Meld queue and plays via existing player bar
- [ ] **PLAY-06**: Path result view persists and remains accessible while bridge playlist plays
- [ ] **PLAY-07**: Currently-playing bridge artist is highlighted in the path result view

### Spotify Integration

- [ ] **SPOT-01**: User's Spotify liked artists appear as quick-pick seed suggestions below From/To inputs
- [ ] **SPOT-02**: Random Bridge button picks two genre-opposite artists from Spotify liked songs via Tag Jaccard distance
- [ ] **SPOT-03**: Bridge artists in path view show "known" or "NEW" badge based on user's Spotify listening history

## v2 Requirements

Deferred to next milestone (P1). Tracked but not in current roadmap.

### Path Walker

- **WALK-01**: User sees 4-5 staging artist cards with photo, name, and genre tags
- **WALK-02**: Tapping a staging artist plays their tracks and shows new staging options toward destination
- **WALK-03**: User can set multiple destinations that chain sequentially

### Graph Queue

- **GRPH-01**: Graph visualization shows the user's journey with now-playing highlighted
- **GRPH-02**: Staging includes both target-directed bridges and seed-adjacent detours

### Extended Features

- **EXTD-01**: Bridge Radio — expanded playlist with 2-3 similar artists per midpoint
- **EXTD-02**: Bridge History — saved bridge paths with "play again" and "discover new route"
- **EXTD-03**: Unknown artist priority scoring in all staging/bridge results

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Graph visualization (force-directed) | High complexity, deferred to P1 Graph Screen |
| Social sharing of bridge paths | No backend, personal use app |
| Editable playlist / track removal before play | Breaks curated journey philosophy; existing skip controls suffice |
| Manual path editing / artist swap | High complexity; re-run bridge instead |
| Configurable hop count slider | Increases confusion; algorithm auto-selects good path length |
| AI-generated bridge narration | API cost + latency; genre tags + listener counts are sufficient |
| Track preview before committing | Undermines "just go" exploration philosophy |
| Daily Bridge notifications | P2, deferred |
| Taste drift analytics | P2, deferred |
| Walk & Listen mode | P2, deferred |
| Native Kotlin port of bridge algorithm | Post-MVP; WebView-first validates product |
| Google Play distribution | YT Music stream extraction incompatible with Play policies |
| Package rename (com.metrolist.music) | Minimize diff with upstream; rename later |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| INFRA-01 | Phase 1 | Complete |
| INFRA-02 | Phase 1 | Complete |
| INFRA-03 | Phase 1 | Complete |
| INFRA-04 | Phase 1 | Complete |
| BRDG-07 | Phase 2 | Complete |
| BRDG-02 | Phase 3 | Complete |
| BRDG-03 | Phase 3 | Complete |
| BRDG-01 | Phase 4 | Complete |
| BRDG-04 | Phase 4 | Complete |
| BRDG-06 | Phase 4 | Complete |
| PLAY-01 | Phase 5 | Complete |
| PLAY-02 | Phase 5 | Complete |
| PLAY-03 | Phase 5 | Complete |
| PLAY-04 | Phase 5 | Complete |
| PLAY-05 | Phase 5 | Complete |
| BRDG-05 | Phase 6 | Pending |
| PLAY-06 | Phase 6 | Pending |
| PLAY-07 | Phase 6 | Pending |
| SPOT-01 | Phase 7 | Pending |
| SPOT-02 | Phase 7 | Pending |
| SPOT-03 | Phase 7 | Pending |

**Coverage:**
- v1 requirements: 21 total
- Mapped to phases: 21
- Unmapped: 0 ✓

---
*Requirements defined: 2026-04-03*
*Last updated: 2026-04-03 after roadmap creation*
