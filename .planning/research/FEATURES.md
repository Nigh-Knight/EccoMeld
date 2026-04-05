# Feature Landscape: Path Walker & Discovery Mode

**Domain:** Interactive graph-based music exploration (open-ended walk, no destination required)
**Researched:** 2026-04-05
**Scope:** v2.0 milestone additions — Path Walker, Hyperbolic Graph, Bridge History, mode toggle.
  Bridge Discovery features (v1.0) are already shipped and documented separately.

---

## Research Basis

Primary evidence source is the EccoPath codebase at `/home/kepler/Projects/EccoPath/`, specifically:

- `stores/graphStore.ts` — NodeState machine, child node fan-out, active path marking
- `stores/pathStore.ts` — activePath array with extendPath / truncateToNode operations
- `stores/uiStore.ts` — AppMode (`pathwalker` vs `fullpath`), selectedNodeId, isPathPanelExpanded
- `lib/types.ts` — NodeState enum: `seed | active | explored | frontier | current | loading | error`
- `lib/hyperbolicLayout.ts` — Poincaré disk math, descendant-weighted arc allocation
- `components/graph/ForceGraph.tsx` — tap dispatch logic, expandNode(), radial vs force modes
- `components/graph/NodeRenderer.ts` — visual treatment per state, bloom animation
- `components/layout/ArtistCard.tsx` — card content: image, similarity %, listener count, genre chips
- `components/layout/PathPanel.tsx` — breadcrumb list, truncate-to-node on tap, MiniGraph
- `components/layout/MiniGraph.tsx` — minimap canvas for path panel

Secondary evidence: existing EccoMeld v1.0 implementation (BridgeViewModel.kt, BridgeScreen.kt,
BridgeAlgorithm.kt, BridgeArtistMetaEntity.kt, BridgeSimilarArtistEntity.kt).

Web research found no directly analogous Android music app with a hyperbolic path walker. EccoPath
itself is the only direct reference implementation. Confidence notes are per-section below.

---

## What Path Walker Mode Actually Is

Bridge mode (v1.0) requires two endpoints: pick From + To, algorithm finds the path.

Path Walker is open-ended: pick ONE seed artist, tap a FALA node to expand it, and keep walking in
any direction — building a personal trail with no destination. Music plays at each expansion.

This maps directly to EccoPath's `pathwalker` mode vs its `fullpath` mode. The EccoPath graph uses
a Poincaré disk layout for Path Walker (radial positions pinned via `fx`/`fy`) and force-directed
layout for Full Path (bridge result display). In EccoMeld, these become two sub-modes within the
same Bridge tab.

---

## Table Stakes

Features users need for Path Walker to feel like a complete, working mode.

| Feature | Why Expected | Complexity | Dependencies |
|---------|--------------|------------|--------------|
| **Single artist seed input with Last.fm autocomplete** | Path Walker starts with one artist. Without clear entry, the mode is unusable. Same ghost-text autocomplete as existing Bridge inputs — users already know the pattern. | Low | Reuse existing ArtistInput + BridgeViewModel ghost-text logic. One input instead of two. |
| **5 FALA nodes displayed after seed entry** | EccoPath confirms: fetch 15 similar artists from Last.fm, shuffle, take 5 to show variety on each expansion. This is the core action surface — without visible frontier nodes, there's nothing to tap. | Low-Med | Last.fm artist.getSimilar already called in BridgeAlgorithm. Reuse BridgeSimilarArtistEntity L2 cache. |
| **Tap a frontier node to expand it (fetch its FALA children)** | The fundamental interaction. Tap = explore. A node that can't be tapped is a dead end and breaks the walk. State transitions: frontier → loading → explored (with 5 new frontier children). | Med | EccoPath expandNode() is the reference. Needs Kotlin port of the fetch + state update cycle. |
| **Loading state per node while FALA fetch is in-flight** | Tapping a node fires a Last.fm request. Without visual feedback, users tap again (double expansion) or think the app is broken. EccoPath shows a pulsing ring on loading nodes. | Low | Manage NodeState.LOADING in PathWalkerViewModel. Compose Canvas renders the ring. |
| **Error state with retry on failed FALA fetch** | Last.fm returns errors (429 rate limit, 404 not found, network offline). EccoPath recovers: state transitions to error for 300ms then back to frontier (retryable). Silent failure is worse than visible error. | Low | Rate limit is already handled by LastFmRateLimiter.kt. Node state machine handles the rest. |
| **Active path breadcrumb trail** | As the user walks, they need to know where they've been. EccoPath maintains `activePath: string[]` updated on every expansion. Without this, the graph grows unnavigably. | Med | pathStore.ts -> PathWalkerViewModel state, same ordered-array pattern. |
| **Truncate path by tapping a breadcrumb node** | Users need to backtrack without rebuilding the entire walk. Tapping a past node in the breadcrumb rewinds to that point: `truncateToNode()` prunes everything after it from activePath and resets node states. | Med | Depends on breadcrumb trail. EccoPath PathPanel.tsx is the reference. |
| **Music plays when a node is expanded** | Path Walker without playback is just a graph browser — that already exists in EccoPath. The Android-native value is that music starts. EccoMeld must play tracks for each expanded artist. | Med-High | Depends on BridgePlaylistBuilder track resolution. Append to queue on each expansion, or replace with current artist's tracks. |
| **Hyperbolic graph as primary view** | EccoPath uses the Poincaré disk as the primary Path Walker view. It places seed at center, frontier nodes at the periphery, and the current exploration focus stays large and centered. This is a deliberate layout choice — force-directed layout for a growing open-ended graph creates a tangled mess. | High | Requires porting `computeHyperbolicLayout()` from TypeScript to Kotlin and rendering on Compose Canvas. |

Confidence: HIGH for all of the above — derived directly from running EccoPath source code.

---

## Differentiators

Features that separate EccoMeld's Path Walker from a simple "click similar artists" list.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Similarity color gradient on frontier nodes** | EccoPath interpolates node color from warm-red (low match) to teal (high match) based on Last.fm similarity score. Users can visually identify which frontier artists are closer or further from their current position — a non-verbal guide for their walk. | Low | Node color already computed in NodeRenderer.ts via `interpolateSimilarityColor(t)`. Compose Canvas equivalent is straightforward. |
| **Genre tags + listener count shown on node tap** | Tapping an explored or seed node opens an ArtistCard with: image, "N% match", listener count, up to 4 genre chips. In EccoPath this is a bottom sheet on mobile, sidebar panel on desktop. On Android: bottom sheet using existing Meld sheet infrastructure. Gives users enough signal to decide whether to continue from that node. | Med | Requires Last.fm artist.getInfo fetch on node tap. ArtistCard data maps directly to BridgeArtistInfo already in BridgeViewModel. |
| **Particle flow on active path edges** | EccoPath animates directional particles along links that are on the active path (teal, 4 particles, speed 0.005). Purely visual, zero functional value — but communicates "this is the direction you walked" in a way a static highlight cannot. On Android Canvas: animatable dot sweep along the link arc. | Med | Compose Canvas with frame-by-frame animation. The path particle effect requires a draw loop, which must be driven by a LaunchedEffect or similar mechanism. |
| **Seed bloom animation on artist entry** | EccoPath animates the seed node from 1.5x radius down to 1.3x with glow fading over 400ms. Signals "the walk started". Small polish detail, high memorability. | Low | Single animation block on first render of the seed node. |
| **Auto-fit zoom after each expansion** | After adding 5 new frontier children, EccoPath calls `graphRef.zoomToFit(600, 50)` with a 300ms delay (radial mode). Users always see all nodes without manually panning. An `autoFitEnabled` flag turns off auto-fit if the user manually zooms, which respects deliberate exploration choices. | Low-Med | Compose Canvas with a transform state. Auto-fit equivalent: compute bounding box of all node positions and set scale + offset to contain them. |
| **MiniGraph (thumbnail) in path panel** | Alongside the breadcrumb list, EccoPath shows a miniature canvas of the full hyperbolic graph with active path highlighted. Tapping a node in the minimap navigates to it. Functions as both a map and a backtrack shortcut. | Med | Compose Canvas, smaller instance of the main graph renderer. Same layout computation, smaller radius. |
| **Familiar / NEW badge on expanded artists** | Already implemented in Bridge mode — BridgeViewModel.resolveFamiliarity() compares path artists against YT Music + Spotify libraries. Apply the same familiarity check to each newly expanded node in Path Walker. Users can walk deliberately toward unfamiliar territory. | Low | Familiarity resolution already exists. Extend to Path Walker node expansion events. |
| **Walk session persistence (Bridge History)** | Saving the full walk (seed, activePath, node graph) to Room DB so users can replay or resume a previous session. EccoPath has no persistence — it's ephemeral. EccoMeld's Android context makes persistence natural: users re-open the app and their last walk is right there. | High | New Room entities: `WalkSessionEntity` (id, createdAt, seedArtist, serialized path), `WalkNodeEntity` (per-node state snapshot). See Architecture section. |

Confidence: MEDIUM-HIGH — differentiators come from EccoPath source plus EccoMeld v1.0 existing infrastructure analysis.

---

## Question-Specific Answers

### Q1: FALA card presentation — grid, carousel, list? How many shown?

**Answer: Not cards. Nodes on a Poincaré disk canvas. 5 per expansion.**

EccoPath does not use grid, carousel, or list for FALA presentation. Frontier nodes appear as
circles on the graph canvas positioned in a 180-degree fan around the expanded parent node. The
parent's direction from its grandparent defines the base angle; children fan ±90 degrees from that
direction. 15 artists are fetched from Last.fm getSimilar, shuffled, and 5 are selected — giving
variety on each expansion while keeping the graph readable.

The `ArtistCard` (image, similarity %, listener count, genre chips) is a DETAIL panel, not the
primary interaction surface. It appears as a bottom sheet when any node is tapped (regardless of
state). The 5 frontier circles are the primary interaction surface.

For the Android port: this means no `LazyVerticalGrid` or horizontal carousel for FALA. The entire
interaction is a Compose Canvas with `pointerInput` tap detection. An artist detail bottom sheet
appears on node tap using the existing Meld `ModalBottomSheet` infrastructure.

**If building a fallback / degraded mode** (e.g. before the graph view is complete): a horizontal
`LazyRow` of 5 tappable artist chips is a valid intermediate. But this should be treated as a
scaffold, not a shipped feature — the graph canvas is the intended primary UI.

### Q2: Breadcrumb/trail UI — rewind, branch, truncate

**Answer: Ordered list, tap-to-truncate, no branching support.**

EccoPath's `activePath` is a `string[]` — an ordered array from seed to current node. The PathPanel
renders this as a vertical list of `PathEntry` rows, each showing step number, artist name, and
similarity %. The current (last) node gets a teal left-border highlight.

Truncation: tapping any past entry calls `truncateToNode(nodeId)` which slices `activePath` to
`[0..idx]` inclusive, then calls `updateActivePath()` on the graph store which re-marks node states
(seed stays `seed`, intermediate nodes go back to `active`, last becomes `current`). The frontier
nodes from branches after the truncation point become `explored` — they remain visible in the graph
as "roads not taken" but are no longer on the active path.

EccoPath does NOT support branching (walking two paths simultaneously). One active path at a time.
The visual record of explored-but-not-active nodes in the graph IS the branch artifact — users can
see they explored those directions, but the active journey is linear.

For Android: the PathPanel becomes a bottom sheet that slides up from the bottom of the Bridge tab,
similar to how EccoPath's MobilePathPanel works. It shows the step list + the minimap canvas. Height:
collapsed to 48dp handle strip, expanded to 40% screen height (matching EccoPath's `40vh`).

### Q3: Node states — transitions and visual treatment

**Answer: 7 states from the EccoPath NodeState type. All visually distinct.**

From `lib/types.ts` and `NodeRenderer.ts`:

| State | Color | Visual Treatment | Meaning |
|-------|-------|------------------|---------|
| `seed` | `#ff8f7b` (warm red) | 1.3x radius, glow, bloom animation on first render | Walk origin |
| `frontier` | `rgba(115,248,222,0.15)` teal dim + stroke | Dim circle with border | Available to tap and expand |
| `loading` | `#73f8de` teal | Filled circle + pulsing outer ring | Expansion in-flight |
| `active` | `#73f8de` teal | Filled, with glow | On the active path, already expanded |
| `current` | `#73f8de` teal | Filled + pulsing ring | Last node on active path (where the walk is "at") |
| `explored` | `#1f1f1f` dark | Small, dark fill, no glow | Expanded but not on active path (branched away from) |
| `error` | `#df2d16` red | Filled red | Expansion failed — tapping retries |

Transition rules (from `expandNode` and `updateActivePath`):
- `frontier` → tap → `loading` → success → `explored` (parent), 5 new `frontier` children added
- `loading` → error → `error` → 300ms → back to `frontier` (retryable)
- `seed` / `active` / `current` → tap → open ArtistCard detail sheet (no state change)
- `truncateToNode(id)` → nodes before id: `active`, id: `current`, nodes after: remain as their current state (typically `explored` or `frontier`)

The pulsing ring on `loading` and `current` requires an animation loop in Compose Canvas. Use
`rememberInfiniteTransition` to drive ring opacity oscillation, or a `LaunchedEffect` that updates
a float state at ~60fps.

### Q4: Hyperbolic graph as primary view vs card-based exploration

**Answer: Hyperbolic graph is the primary interaction. No card list for Path Walker.**

EccoPath's design is unambiguous: the Poincaré disk canvas IS the Path Walker UI. There is no
alternative card-stack or list view for the same content. The ArtistCard is a detail overlay, not
the primary surface.

Why hyperbolic layout instead of force-directed:

1. Force-directed layout with growing nodes creates unmanageable overlap and constant re-layout
2. Poincaré disk allocates proportionally more space at deeper levels via `tanh(hyperbolicRadius/2)` mapping
3. High-match artists are closer to the parent (shorter hyperbolic step), low-match further — similarity is encoded in spatial distance
4. The seed always stays centered and large; ancestors compress toward the edge as you explore deeper

For Android implementation: Compose `Canvas` composable with `pointerInput` for tap handling. The
hyperbolicLayout computation is ~170 lines of pure math (no Android APIs), so the TypeScript-to-Kotlin
port is mechanical.

The graph is zoomable (pinch-to-zoom) and pannable via `transformable` modifier. Double-tap on
background resets zoom to fit all nodes.

A card-based fallback (5-artist horizontal strip) is acceptable only as a Phase 1 scaffold while the
Canvas is being built. It should not ship as the final design.

### Q5: Bridge History persistence — what gets saved, how surfaced, replay UX

**Answer: EccoPath has NO persistence. This is a new EccoMeld feature built on Room.**

EccoPath walks are ephemeral — navigating away loses everything. EccoMeld has a Room DB and the
infrastructure to persist history. The v2.0 milestone targets this.

What to save:
- **WalkSessionEntity**: id, createdAt (timestamp), sessionType (BRIDGE or WALK), seedArtist,
  destinationArtist (null for walks), completedPath (JSON-encoded `List<String>` of artist names
  in order), durationMs (how long the session ran), mediaItems (JSON-encoded list of YT video IDs
  for replay)
- **BridgeArtistMetaEntity** (already exists): tags, listener count — these are already persisted
  from v1.0 and can be reused for history display without re-fetching

How to surface: a "History" sub-section at the bottom of the Bridge tab, below the current search
inputs. Shows a scrollable list of past sessions ordered by `createdAt DESC`. Each row: session type
icon (bridge or walk), seed → destination or seed → "Walk (N steps)", relative time ("3 days ago"),
replay icon button.

Replay UX:
- **Bridge replay**: already implemented in v1.0 via `replayCard()` in BridgeViewModel. The stored
  `mediaItems` (MediaItem list) are re-queued directly — no re-running the algorithm.
- **Walk replay**: re-queue the stored media items in path order. Optionally offer "Resume" (rebuild
  the graph state from the saved path, set current node to the last node on the path).

Replay from Bridge History is architecturally simpler than live replay because no algorithm execution
is needed — it's a stored media item list re-queued via `playQueue(ListQueue(...))`.

### Q6: Toggle between Bridge mode and Path Walker mode

**Answer: Segmented control (tab-style toggle) at the top of the Bridge tab.**

EccoPath uses a mode flag in `uiStore.ts` (`mode: AppMode = 'pathwalker' | 'fullpath'`) toggled via
`setMode()`. In EccoPath, the two modes share the same canvas — the graph switches between radial
(Path Walker) and force-directed (Full Path) layout when mode changes.

For EccoMeld, the toggle is a two-item segmented control (Material 3 `SingleChoiceSegmentedButtonRow`
or a custom pill toggle) at the top of the Bridge tab, above the search inputs. Labels: "Bridge" and
"Walk". On toggle:
- The input section changes (two inputs for Bridge, one for Walk)
- The results area changes (stacked bridge cards for Bridge, graph canvas for Walk)
- The mode is persisted to DataStore so it survives tab re-entry

Swipe-to-switch between modes is an anti-feature here: the two modes have very different input
requirements, and a horizontal swipe on the graph canvas would conflict with panning the graph.
Avoid swipe gestures for mode switching.

The Bridge History section should appear in both modes (it contains both bridge sessions and walks).

### Q7: Music playback during walking — when to start, how to transition

**Answer: Start immediately on first expansion. Append on each subsequent expansion.**

EccoPath has no playback — this is the key Android-native addition. The pattern follows what
BridgePlaylistBuilder already does for progressive bridge streaming:

1. **Seed entry**: no playback yet. Fetch seed's FALA list, display frontier nodes.
2. **First expansion (tap frontier node)**: fetch tracks for the newly explored artist via existing
   BridgePlaylistBuilder logic. Start playback immediately when first tracks resolve. This mirrors
   Bridge mode's progressive streaming — music within seconds, not after the whole path resolves.
3. **Subsequent expansions**: append tracks for each newly explored artist to the current queue.
   Do NOT replace the queue. Walking should feel like a continuous stream, not a queue replacement.
4. **Truncate/rewind**: when the user truncates to a past node, do NOT modify the queue. The music
   keeps playing. The walk history is visual context; playback is independent. This avoids jarring
   interruptions and is consistent with Meld's existing player contract (the player owns the queue,
   not the UI).

The transition-between-artists moment happens naturally via the existing player: when the last track
for artist N finishes, the queue moves to artist N+1's tracks. No special transition logic needed.

One open question: should expanding a NEW branch (tapping a frontier node that is NOT the current
node's child) reset the queue to that branch's tracks, or append after the current queue? Recommend
APPEND for MVP — simpler, less jarring. "Reset" can be a gesture (long-press to expand and reset
queue to that branch) in a future version.

---

## Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| **Card-based FALA layout as final design** | Horizontal chip strips or grid cards for frontier nodes are a common fallback but undermine the graph metaphor. Users in a card layout don't understand spatial position, similarity distance, or the branching structure. | Use the hyperbolic Canvas as the shipped design. A card strip is acceptable only as a scaffolded placeholder during development. |
| **Force-directed layout for Path Walker** | Force layout constantly re-organizes nodes as new children are added, causing disorienting jumps. EccoPath explicitly disables all d3-force forces in Path Walker mode and pins nodes deterministically via `fx`/`fy`. | Use pinned Poincaré disk positions. Nodes never move once placed. |
| **Replacing the queue on each expansion** | Interrupting playback every time the user taps a new node creates frustration. The music should feel like a continuous stream; the walk is a way of navigating it, not restarting it. | Append to queue. Offer a "restart from here" affordance (long-press) as a separate explicit action. |
| **Separate graph screen / new tab for Path Walker** | Adding a 5th bottom nav tab for the graph breaks the "Bridge is one tab" architecture and increases navigation distance. Walk mode IS bridge mode with one input instead of two. | Keep Path Walker inside the existing Bridge tab, separated by a toggle. |
| **Branching active paths (two simultaneous walks)** | EccoPath confirmed: one active path at a time. Supporting two simultaneous walk branches would require forked audio streams, forked path state, and a radically more complex breadcrumb UI. | One active path only. Explored-but-abandoned branches are visible in the graph as context, not as co-equal paths. |
| **Preloading all FALA nodes at once on seed entry** | Fetching all 5 frontier artists' FALA lists eagerly means 5x more Last.fm API calls on walk entry. The lazy model (expand on tap) is correct and is what EccoPath uses. | Fetch FALA children only when the user taps to expand a node. The L2 cache (BridgeSimilarArtistEntity) already handles repeat visits efficiently. |
| **Walk session auto-save on every step** | Writing a database row on every single node expansion adds latency to the tap-to-expand interaction. History should be saved at logical checkpoints: session end, app background, or explicit "save" tap. | Save to Room DB on: (a) app background / process death, (b) user initiates a new walk, (c) explicit save gesture. Not on every expansion. |
| **Re-running the bridge algorithm to replay a walk** | Bridge History replays stored media items. There is no reason to re-run the 25-second beam search to replay a known path. | Store `List<MediaItem>` (serialized YT video IDs) in the WalkSessionEntity at walk-end. Replay reads from storage, not algorithm. |
| **Swipe-to-switch modes** | A horizontal swipe on the Bridge tab conflicts with graph pan gestures and LazyRow scrolling in Bridge mode. Gesture collision produces bugs and user confusion. | Explicit toggle control only (segmented button). |

---

## Feature Dependencies

```
Mode toggle (Bridge / Walk)
    ├── Bridge mode (v1.0, already built)
    └── Walk mode:
            ├── Single artist seed input
            │       └── Ghost-text autocomplete (reuse v1.0 autocomplete)
            ├── Poincaré disk Canvas renderer
            │       ├── computeHyperbolicLayout() Kotlin port (pure math, no Android deps)
            │       ├── NodeRenderer (Compose Canvas drawCircle + glow + pulse animation)
            │       └── LinkRenderer (straight line or geodesic arc)
            ├── PathWalkerViewModel
            │       ├── NodeState machine (7 states)
            │       ├── activePath: List<String>
            │       ├── expandNode() — Last.fm getSimilar + L2 cache hit
            │       └── truncatePath(nodeId) — rewind
            ├── FALA fetch on expansion
            │       └── LastFM.getSimilar (reuse existing LastFM module)
            │               └── BridgeSimilarArtistEntity L2 cache (already exists)
            ├── Music playback on expansion
            │       └── BridgePlaylistBuilder.buildProgressively() (reuse v1.0)
            │               └── ExoPlayer queue append (reuse v1.0 addToQueue)
            ├── ArtistCard bottom sheet (node tap)
            │       └── LastFM.getArtistInfo (reuse BridgeArtistMetaEntity L2 cache)
            ├── PathPanel / breadcrumb bottom sheet
            │       └── MiniGraph Compose Canvas (smaller instance of main graph)
            └── Bridge History
                    ├── WalkSessionEntity (new Room entity)
                    ├── Save on background / walk-end
                    └── Replay via stored MediaItem list (reuse v1.0 replayCard pattern)

Familiarity badges (KNOWN / NEW) on walk nodes
    └── resolveFamiliarity() (reuse v1.0, already in BridgeViewModel)
```

Critical path for MVP:
1. Mode toggle + PathWalkerViewModel skeleton
2. computeHyperbolicLayout() Kotlin port
3. Canvas renderer with 7-state NodeRenderer
4. expandNode() with L2 cache + state transitions
5. Music playback on expansion (append to queue)

Everything else (ArtistCard, PathPanel, History, MiniGraph, familiarity badges) is additive after
the core walk interaction is working.

---

## Complexity Assessment

| Feature | Complexity | Reason |
|---------|------------|--------|
| Poincaré disk layout port (TypeScript → Kotlin) | Med | ~170 lines pure math. No Android APIs. Mechanical translation. |
| Compose Canvas renderer (7 node states + pulse animation) | Med-High | Canvas draw calls are straightforward; the pulsing ring requires an animation driver. Touch hit-testing on canvas is non-trivial on Android (no built-in node hit detection like react-force-graph). |
| PathWalkerViewModel + NodeState machine | Med | State machine is well-defined in EccoPath. Kotlin data structures map directly. Coroutine scope for expansion jobs. |
| Music playback on walk expansion | Low-Med | BridgePlaylistBuilder.buildProgressively() already exists. Need to wire it to expansion events rather than path completion. |
| ArtistCard bottom sheet | Low | BridgeArtistInfo already populated from Last.fm. Compose ModalBottomSheet with existing shimmer pattern. |
| PathPanel / breadcrumb | Low-Med | Simple list + truncate. The MiniGraph inside it adds medium complexity (second Canvas instance). |
| Bridge History Room entities + DAO | Med | New entity design (WalkSessionEntity). Serialization of path + MediaItem list (JSON via kotlinx.serialization). New DAO queries. Room migration. |
| Mode toggle (segmented button + state persistence) | Low | SingleChoiceSegmentedButtonRow + DataStore key. |
| Familiarity badges on walk nodes | Low | resolveFamiliarity() already exists. Wire to walk expansion, display in ArtistCard and as node overlay. |

---

## Sources

Primary (HIGH confidence — direct source code inspection):
- EccoPath `stores/graphStore.ts` — node state machine, active path update logic
- EccoPath `stores/pathStore.ts` — activePath extend/truncate operations
- EccoPath `stores/uiStore.ts` — AppMode, selectedNodeId, pathPanel expansion
- EccoPath `lib/types.ts` — NodeState type definition
- EccoPath `lib/hyperbolicLayout.ts` — Poincaré disk algorithm, HYPER_STEP, matchToHyperDist
- EccoPath `components/graph/ForceGraph.tsx` — expandNode(), tap dispatch, radial vs force modes
- EccoPath `components/graph/NodeRenderer.ts` — visual state treatment, bloom animation timing
- EccoPath `components/layout/ArtistCard.tsx` — card content: similarity color, genre chips, listeners
- EccoPath `components/layout/PathPanel.tsx` — breadcrumb list, truncate-on-tap, MiniGraph embed
- EccoPath `components/layout/MiniGraph.tsx` — minimap canvas rendering
- EccoMeld `bridge/BridgeAlgorithm.kt` — existing bridge search infrastructure
- EccoMeld `viewmodels/BridgeViewModel.kt` — seed suggestions, familiarity, progressive playlist build
- EccoMeld `playback/BridgePlaylistBuilder.kt` (inferred from ViewModel usage) — progressive track resolution
- EccoMeld `db/entities/BridgeSimilarArtistEntity.kt` — L2 cache already exists for getSimilar
- EccoMeld `db/entities/BridgeArtistMetaEntity.kt` — L2 cache already exists for artist metadata

Secondary (MEDIUM confidence — web research, no direct verification):
- Spotify Fans Also Like: [Spotify support article](https://support.spotify.com/us/artists/article/fans-also-like/) — confirms fan-behavior-based recommendation, no UI layout specifics found
- Breadcrumb UX patterns: [NN/g breadcrumbs article](https://www.nngroup.com/articles/breadcrumbs/) — confirms path-based breadcrumbs for "rabbit hole" exploration, truncate-to-node is a known pattern
- Music graph exploration precedents: Music-Map, MusicLynx, Every Noise at Once — all confirm node-tap-to-expand as the dominant interaction model for graph-based music discovery
