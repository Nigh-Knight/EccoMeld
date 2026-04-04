# EccoMeld — Product Requirements Document

## Context

EccoPath discovers artists through graph exploration and bridge connections. Meld plays music by matching Spotify data to YouTube Music streams. EccoMeld fuses both: **bridge two artists → get a genre-transitioning playlist that plays through YouTube Music**. The graph isn't just visualization — it's the interface for building playlists.

Built for personal use. Standalone Android app, not a Meld fork or PR.

---

## Core Promise

> "Start anywhere. Walk anywhere. Discover music you didn't know you wanted — through meaningful, human-like bridges instead of algorithms pushing the same popular songs."

EccoMeld is not another recommendation app. It is a **musical exploration engine** that treats taste as a navigable space rather than a list. It supports active listening, niche artist discovery, and gives the user control over how far they drift from their current taste.

---

## Core Concept

**Bridge Playlists:** Pick two artists from different corners of music. EccoMeld finds a 5-7 hop path through niche midpoint artists, then builds a unified playlist that smoothly transitions between genres — 5-7 tracks per bridge artist so you have time to actually enjoy each one, all playable via YouTube Music.

Death Grips → Taylor Swift becomes a ~45-minute playlist that walks you from experimental hip-hop through hyperpop through art pop into mainstream pop. Bridge artists are clearly labeled, with emphasis on niche discoveries.

---

## Architecture

```
EccoMeld (Kotlin Android)
├── UI Layer (Jetpack Compose)
│   ├── Bridge Screen      — search two artists, see loading, view result path
│   ├── Graph Screen       — WebView running EccoPath (eccomuse.com/path)
│   ├── Queue Screen       — current playlist with graph visualization
│   └── Player Bar         — persistent bottom player (mini + expanded)
│
├── Discovery Engine
│   ├── WebView + JS Bridge — EccoPath handles all graph/bridge logic
│   │   ├── MeldBridge.queueArtists(json)     — path walker → play queue
│   │   ├── MeldBridge.createPlaylist(json)   — bridge → saved playlist
│   │   └── MeldBridge.autoBridge(json)       — queue add → auto-bridge between artists
│   ├── Playlist Builder   — takes bridge path, fetches top tracks per artist
│   └── Auto-Bridge Queue  — on manual add, silently bridges to queue tail
│
├── Playback Layer (from Meld/InnerTune lineage)
│   ├── YT Music matching  — fuzzy match artist+track → YT Music video ID
│   ├── Audio streaming     — YT Music stream extraction + ExoPlayer
│   └── Media session       — Android Auto, Bluetooth, lock screen controls
│
├── Data Layer
│   ├── Spotify Auth        — WebView OAuth, session tokens (data only, no streaming)
│   ├── Last.fm API         — artist.getSimilar, artist.getInfo (bridge algorithm)
│   └── Local cache         — Room DB for matched tracks, bridge history, IndexedDB sync
│
└── Platform
    ├── Notifications       — "Daily Bridge" suggestion
    └── Android Auto        — bridge playlists in car media browser
```

---

## Features (Priority Order)

### P0 — Ship it (MVP)

**1. Bridge → Unified Playlist**
- Two search inputs: "From" and "To" artist
- Runs EccoPath beam search (via WebView or ported to Kotlin)
- **Result: linear path view** — vertical journey layout:
  ```
  ● Death Grips (SEED)
  │  experimental hip-hop
  │
  ├─ 1 800 PAIN (BRIDGE)
  │  industrial rap · 45K listeners
  │
  ├─ Alice Longyu Gao (BRIDGE)
  │  hyperpop · 12K listeners
  │
  ● Taylor Swift (TARGET)
     pop
  ```
- Bridge artists clearly labeled with genre tags and listener count
- **Auto-plays immediately** when bridge is found — linear path view shows while first track loads
- Builds a single unified playlist (not separate artist blocks)
- **Track selection per bridge artist: 2 popular + 3-5 deep cuts** — hooks with something recognizable then pulls deeper into the artist's catalog
- Playlist order preserves smooth genre transitions
- Total: ~30-45 tracks for a 5-hop bridge
- **YT Music matching failures: skip silently.** Don't break the flow for unavailable tracks.

**2. YT Music Playback**
- ExoPlayer streaming from YT Music (Meld/InnerTune pattern)
- Persistent player bar (bottom sheet, expand for full player)
- Background playback, media notification, Bluetooth/lock screen controls
- No YT Music account required (public streams)

**3. Spotify Auth (Data Only)**
- WebView Spotify login (same as Meld)
- Pull user's top artists, liked artists for seed suggestions
- "Bridge from your taste" — pick two random artists from your Spotify library

**4. Random Bridge Button**
- Picks two artists from your Spotify liked songs with **lowest Tag Jaccard overlap** (most genre-distant)
- Automatically bridges them with mostly artists you haven't heard of
- One-tap discovery: "Surprise me"

### P1 — Make it good

**5. 4-5 Card Staging Suggestions (Path Walker)**
- Never overwhelm the user. Always show exactly 4-5 staging artists as cards: **artist photo + name + top 2 genre tags**
- "Refresh suggestions" button to reshuffle
- Staging artists can be connected to the **seed** (not just the target) — if you queued LDR but want more DG-adjacent exploration first, the staging shows DG-connected frontiers too
- Mix of direct bridges toward target + interesting detours that are close but not exact

**6. Graph Queue (Guided Path Walker with Destination)**
- The queue is path walker with a target. You set a destination artist, then manually walk toward them through staging frontiers.
- **Adding an artist sets a destination.** You're listening to Death Grips, you add Lana Del Rey. EccoMeld shows 4-5 staging artists:
  ```
  Death Grips (playing) ──→ target: Lana Del Rey

  Staging options:
    ○ M.I.A.           (genre bridge — connects toward LDR)
    ○ Crystal Castles   (genre bridge — connects toward LDR)
    ○ Cities Aviv       (close detour — niche, DG-adjacent)
    ○ HEALTH            (close detour — niche, different angle)
    ○ Grimes            (genre bridge — connects toward LDR)
  ```
- **Tap a staging artist → it plays next, new staging options appear.** Repeat until destination.
- **The graph visualizes your journey.** Now-playing highlighted, staging as frontier nodes branching outward. The graph is necessary because you're navigating.
- **Multiple destinations.** Queue DG → LDR → Bach. After reaching LDR, staging pivots toward Bach.

**7. Unknown Artist Priority**
- On Spotify login, pull top artists + recently played + saved artists → build a "known" set
- All staging options, bridge paths, and frontiers deprioritize known artists in scoring
- Not hidden — known artists can still appear as waypoints — but unknown artists rank higher
- Effect: the app actively surfaces genuinely new-to-you artists

**8. Bridge Radio (Extended Playlist)**
- After bridge is found, expand each hop: fetch 2-3 similar artists per midpoint
- Creates a 40-60 track playlist that fills in the gaps between bridge hops
- Smooth genre gradient instead of discrete jumps

**9. Bridge History**
- All bridge paths saved locally (Room DB) automatically
- "Play again" replays the exact same playlist
- "Discover new route" re-runs bridge with randomness for a different path
- Stats: total bridges, unique artists discovered, most common bridge artists

### P2 — Make it great

**10. Walk & Listen Mode**
- Tapping any node in the graph or staging card immediately plays that artist's top track
- Navigation IS playback. No separate "play" action.

**11. Daily Bridge**
- Pick two random genre-opposite artists from Spotify listening history
- Compute bridge overnight (or on first open)
- Push notification: "Today's bridge: Death Grips → Taylor Swift — 6 hops through artists you've never heard"

**12. Taste Drift Analytics**
- Track which bridges you complete, which artists you skip vs replay
- Build a personal taste map over time
- "Your taste drifted from hip-hop toward ambient this month"

---

## Design

- **System:** Material You (Material 3) — follows Android's design system, dynamic colors from album art
- **Theme:** Dark mode only — matches EccoMuse ecosystem
- **Color extraction:** Album art of currently playing track feeds Material You's dynamic color system
- **Typography:** Material 3 type scale
- **Layout:** Single activity, bottom nav (Bridge / Graph / History), persistent bottom player bar
- **Player bar:** Mini bar at bottom (track name + artist + play/pause), swipe up for full player with album art + controls
- **Bridge result:** Linear vertical path view — seed at top, target at bottom, bridge artists between with connecting lines, genre tags, listener counts

---

## Technical Decisions

### Bridge Algorithm: WebView vs Native Kotlin Port

| Approach | Pros | Cons |
|---|---|---|
| **WebView (use EccoPath as-is)** | Zero rewrite, shares code with web app, IndexedDB cache shared | WebView startup cost, JS bridge latency, can't run in background |
| **Native Kotlin port** | Faster, runs in background, Room DB caching, no WebView | Must rewrite beam search + BFS + scoring in Kotlin, divergent codebases |

**Recommendation: Start with WebView for MVP, port to Kotlin for P1.** The bridge algorithm is ~200 lines of TypeScript. Porting to Kotlin is straightforward once the product is validated. WebView lets you ship fast.

### Playback Engine

Use the InnerTune/Meld playback stack:
- **NewPipe Extractor** or equivalent for YT Music stream URL extraction
- **ExoPlayer (Media3)** for audio playback
- **Fuzzy matching:** artist name + track title + duration → best YT Music match
- Cache matched track IDs in Room DB (same pattern as Meld)

### Licensing

Meld and InnerTune are likely GPL-licensed. If using their playback code, EccoMeld must also be GPL. This is fine for a personal project. Check the LICENSE file in Meld's repo before starting.

---

## MVP Scope (What to Build First)

1. Android app shell (Compose, single activity, bottom nav)
2. Bridge screen: two search inputs → loading → result path with labeled bridge artists
3. WebView running EccoPath for the bridge algorithm
4. JS bridge: `MeldBridge.createPlaylist(json)` → Kotlin receives artist list
5. Unified playlist generation: 5-7 tracks per bridge artist, genre-transition order preserved
6. For each track: search YT Music → fuzzy match → add to ExoPlayer queue
7. Player bar: play/pause, skip, current track info, artist name
8. Background playback + media notification
9. Spotify login (data only) → pull liked artists for seed suggestions
10. Random bridge button: pick two genre-opposite liked artists → auto-bridge

**Not in MVP:** Graph view, staging frontiers, bridge history, analytics.

---

## Verification

- [ ] Bridge two distant artists (Death Grips → Taylor Swift) → unified playlist of 30-45 tracks
- [ ] 5-7 tracks per bridge artist, not just 1
- [ ] Bridge artists clearly labeled with "Bridge Artist" tag
- [ ] Each track correctly matched to the right artist on YT Music
- [ ] Playback continues in background when app is minimized
- [ ] Lock screen / Bluetooth controls work
- [ ] Bridge path shows niche midpoint artists (not just popular hubs)
- [ ] Random bridge picks genre-opposite artists from Spotify liked songs
- [ ] (P1) 4-5 staging cards with photo, name, vibe tag, similarity score
- [ ] (P1) Staging includes both target-directed bridges AND seed-adjacent detours
- [ ] (P1) Tap staging artist → plays, new staging appears heading toward destination
- [ ] (P1) Staging options prioritize artists NOT in Spotify listening history
- [ ] (P1) Multiple destinations chain: after reaching first, staging pivots toward second

---

## Open Questions

1. **Meld's license** — need to verify GPL vs MIT before using playback code
2. **YT Music API stability** — NewPipe Extractor breaks when YT changes endpoints. Maintenance burden?
3. **Distribution** — GitHub releases + APK sideload? F-Droid? Google Play would reject YT Music stream extraction.
4. **Bridge algorithm location** — WebView for MVP, but when to port to Kotlin? After first working version or before?

Sources:
- [Meld - GitHub](https://github.com/FrancescoGrazioso/Meld)
- [InnerTune - GitHub](https://github.com/z-huang/InnerTune)
- [OuterTune - GitHub](https://github.com/OuterTune/OuterTune)
