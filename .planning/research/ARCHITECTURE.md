# Architecture Patterns: Path Walker Integration

**Domain:** Android music discovery app — adding interactive FALA graph exploration to existing Bridge tab
**Researched:** 2026-04-05 (supersedes 2026-04-03 draft; focused on v2.0 Path Walker milestone)
**Overall confidence:** HIGH — all analysis is from direct inspection of the production codebase and EccoPath TypeScript source

---

## Source Map (files inspected for this research)

| File | Role |
|------|------|
| `BridgeViewModel.kt` | Existing ViewModel — 789 lines, owns all bridge search and card state |
| `BridgeScreen.kt` | Existing screen composable — single scrolling Column, no mode toggle yet |
| `PathSheet.kt` | `HorizontalPathStrip`, `PathNodeRow`, `PathSheet`, shared composables |
| `KotlinBridgeCache.kt` | L1 ConcurrentHashMap + L2 Room two-level cache, `@Singleton` |
| `BridgeAlgorithm.kt` | Native Kotlin bidirectional beam search, `@Singleton` |
| `BridgePlaylistBuilder.kt` | Last.fm top-tracks + YouTube Music track resolution |
| `BridgeArtistMetaEntity.kt` | Room entity: artist tags/listeners cache (purgeable) |
| `BridgeSimilarArtistEntity.kt` | Room entity: similar-artist list cache (purgeable) |
| `DatabaseDao.kt` (lines 1750-1769) | Bridge cache DAO methods — end of 1747-line monolithic DAO |
| `EccoPath/stores/graphStore.ts` | Zustand store: nodes/links/actions |
| `EccoPath/lib/types.ts` | `GraphNode`, `GraphLink`, `NodeState` type definitions |
| `EccoPath/lib/hyperbolicLayout.ts` | Poincaré disk layout: `computeHyperbolicLayout()` + `computeGeodesicArc()` |
| `EccoPath/lib/crawl.ts` | BFS crawler that streams batches to the graph store (FALA precursor) |

---

## Integration Decision Summary

| Question | Decision |
|----------|----------|
| Separate VM or extend BridgeViewModel? | Separate `PathWalkerViewModel` |
| Graph state pattern? | `StateFlow<PathWalkerGraphState>` in ViewModel — no Zustand equivalent needed |
| Hyperbolic layout where? | `Dispatchers.Default` coroutine, positions stored in node data — never in draw phase |
| Room schema for history? | Three new tables; do NOT extend existing cache tables |
| Playback integration? | Identical pattern to `BridgeViewModel.buildPlaylistProgressive()` |
| Navigation? | Same `BridgeScreen` with mode toggle — no new NavHost route |
| PathSheet/HorizontalPathStrip reuse? | `HorizontalPathStrip` and `PathNodeRow` directly reusable; `PathSheet` bottom-sheet wrapper not needed |
| Concurrent state handling? | Independent ViewModel lifecycles give natural isolation; no cross-ViewModel guards needed |

---

## Question 1: Separate ViewModel or extend BridgeViewModel?

**Decision: Separate `PathWalkerViewModel`.**

`BridgeViewModel` is already 789 lines managing: 8 autocomplete StateFlows, 5 seed suggestion StateFlows, bridge cards list, active path tracking, familiarity resolution, tag prefetching, and Jaccard pair selection. Adding Path Walker on top produces a 1,200+ line god-ViewModel that is untestable.

The state shapes are fundamentally different and should not share a container:

| Concern | Bridge | Path Walker |
|---------|--------|-------------|
| Inputs | 2 text fields (from/to) | 1 seed artist selection |
| Operation | One-shot search returning a result | Iterative step-by-step navigation |
| State shape | `List<BridgeCard>` (completed results) | `PathWalkerGraphState` (live growing graph) |
| Playback trigger | Search completion | Each user step |
| "Path" meaning | Fixed algorithm output | Grows per user decision |

`PathWalkerViewModel` receives `KotlinBridgeCache` and `BridgePlaylistBuilder` via `@Inject constructor` — the same singletons, zero duplication. Both ViewModels instantiate via `hiltViewModel<T>()` at the top of `BridgeScreen` and survive mode switches independently.

---

## Question 2: Graph state — new store pattern or StateFlow in ViewModel?

**Decision: `StateFlow<PathWalkerGraphState>` inside `PathWalkerViewModel`. No Zustand-equivalent abstraction.**

EccoPath uses Zustand because React has no built-in solution for fine-grained mutable store with typed actions. Kotlin/Compose does not have this gap. The existing codebase uses exactly this pattern for all complex state: a data class exposed as `StateFlow`, mutated inside the ViewModel via `.value = state.copy(...)`.

The Zustand `graphStore.ts` surface maps directly to Kotlin:

```kotlin
data class PathWalkerGraphState(
    val seedArtist: String = "",
    val nodes: List<WalkerNode> = emptyList(),
    val links: List<WalkerLink> = emptyList(),
    val activePath: List<String> = emptyList(),  // breadcrumb trail of user choices
    val currentNodeId: String = "",              // "current" in NodeState terms
    val isExpanding: Boolean = false,            // node expansion in-flight
    val expandError: String? = null,
)

enum class WalkerNodeState { SEED, ACTIVE, EXPLORED, FRONTIER, CURRENT, LOADING, ERROR }

data class WalkerNode(
    val id: String,           // artist name (normalized key, unique)
    val name: String,         // display name
    val state: WalkerNodeState,
    val match: Float = 0f,
    val parentId: String? = null,
    val listeners: Long = 0L,
    val tags: List<String> = emptyList(),
    val x: Float = 0f,       // pre-computed Poincare disk position
    val y: Float = 0f,
)

data class WalkerLink(
    val sourceId: String,
    val targetId: String,
    val isActivePath: Boolean = false,
)
```

ViewModel mutation methods mirror Zustand actions (`setSeedNode`, `addChildNodes`, `updateActivePath`, etc.) but are plain Kotlin functions that call `_graphState.value = _graphState.value.copy(...)`. For Path Walker scale (50-150 nodes), `List<T>` with immutable copy has negligible CPU cost and is simpler than any custom store abstraction.

---

## Question 3: Hyperbolic layout computation — where does it run?

**Decision: Pre-compute positions on `Dispatchers.Default` coroutine, triggered by graph state changes. Store `x`/`y` in `WalkerNode`. Canvas draw phase reads only pre-computed coordinates.**

`computeHyperbolicLayout()` in EccoPath is a pure function: BFS traversal + `tanh()` math, no I/O, no Android framework calls. For 50-150 nodes this runs in under 1ms on a modern device. It must not run inside `DrawScope` because:
- It would re-run on every frame
- `DrawScope` lambdas are not coroutine scopes; no structured concurrency is possible inside them
- It makes the Canvas recomposition-unsafe (cannot call `collectAsState()` during draw)

The correct integration:

```
graph state changes (nodes added/removed)
  → LaunchedEffect(graphState.nodes, screenRadius) in PathWalkerViewModel or PathWalkerScreen
  → withContext(Dispatchers.Default) { computeHyperbolicLayout(nodes, screenRadius) }
  → positions Map<String, Offset> returned
  → _graphState.value = state.copy(nodes = nodes.map { n ->
        n.copy(x = positions[n.id]?.x ?: n.x, y = positions[n.id]?.y ?: n.y)
    })
  → Canvas reads node.x, node.y — only pre-computed floats, no computation in draw phase
```

`screenRadius` comes from `BoxWithConstraints` in the Canvas composable — measured once on layout. It is stable and safe as a `LaunchedEffect` key.

For `computeGeodesicArc()` (link rendering): this function is O(1) per link and returns only `Float` arithmetic results. It is safe to call inline inside `DrawScope` during the draw pass. No pre-computation needed for links.

---

## Question 4: Room DB schema for walk history

**Decision: Three new entities in new tables. Do NOT extend existing cache tables.**

The existing bridge cache tables (`bridge_similar_artists`, `bridge_artist_meta`) are owned by `KotlinBridgeCache` and are explicitly purgeable — the `init {}` block in `KotlinBridgeCache` purges them entirely on every cold start. Extending these tables with history data would cause silent history loss on cache invalidation.

New entities:

```kotlin
// Completed walk session
@Entity(tableName = "walk_history")
data class WalkHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seedArtist: String,
    val createdAt: Long = System.currentTimeMillis(),
    val stepCount: Int,
    val finalArtist: String,
    val activePathJson: String,    // JSON List<String> — user's breadcrumb
    val playedMediaItemsJson: String, // JSON-encoded YT video IDs for replay
)

// Per-step record (user chose artist B at step N)
@Entity(
    tableName = "walk_step",
    foreignKeys = [ForeignKey(
        entity = WalkHistoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["walkId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("walkId")],
)
data class WalkStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val walkId: Long,
    val stepIndex: Int,
    val fromArtist: String,
    val toArtist: String,
    val chosenAt: Long = System.currentTimeMillis(),
)

// Completed A→B bridge run (separate from walk)
@Entity(tableName = "bridge_history")
data class BridgeHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromArtist: String,
    val toArtist: String,
    val pathJson: String,          // JSON List<String> — full algorithm path
    val mediaItemsJson: String,    // JSON-encoded YT video IDs for replay
    val hopCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
)
```

These three tables require a new Room migration (version increment). All DAO methods go into `DatabaseDao` at the end of the file, consistent with existing project conventions. `BridgeViewModel.startBridge()` persists to `BridgeHistoryEntity` on successful path found. `PathWalkerViewModel.stepTo()` persists `WalkStepEntity` on each choice, and writes `WalkHistoryEntity` on walk end.

---

## Question 5: Path Walker playback integration with PlayerConnection/queue system

**Decision: Identical pattern to `BridgeViewModel.buildPlaylistProgressive()`. No new playback infrastructure.**

`PlayerConnection` is passed from the composable to each playback-triggering ViewModel method as a parameter — never stored in the ViewModel. This is already the established pattern in `replayCard(cardId, playerConnection)`, `onConfirmReplaceQueue(playerConnection)`, `onConfirmPlayNext(playerConnection)`, and `startBridge(from, to, playerConnection)`.

Path Walker step flow:

```
User taps FALA frontier node (artist B)
  → PathWalkerViewModel.stepTo(artistB, playerConnection)
  → _graphState: artistA → EXPLORED, artistB → CURRENT, activePath += artistB
  → launch(IO) { BridgePlaylistBuilder.buildProgressively(listOf(artistB)) { _, _, tracks ->
        withContext(Main) {
            playerConnection.playQueue(ListQueue("Walking: $artistB", tracks))
        }
    }}
  → launch(IO) { KotlinBridgeCache.getSimilarArtists(artistB) → next frontier nodes }
  → layout recomputed → _graphState updated with new frontier nodes
  → launch(IO) { WalkStepEntity(walkId, stepIndex, artistA, artistB).persist() }
```

Music replaces on each step: when the user walks to artist B, artist B's tracks replace the queue. This matches user expectation — "I chose to walk here, play this artist now." The frontier FALA cards render while current music plays; there is no coupling between when a song ends and when the user chooses to walk.

`playerConnection.addToQueue()` is not used for step-to navigation (unlike bridge building which appends tracks progressively). Each step is a fresh queue replacement.

---

## Question 6: Navigation — separate screen or BridgeScreen mode toggle?

**Decision: Same `BridgeScreen` with a two-segment mode toggle at the top. No new NavHost route.**

The product requirement is explicit: "Bridge + Path Walker toggle on Bridge tab." Adding a new route to `Screens.kt` and `NavigationBuilder.kt` would require either a new bottom tab (no space) or a nested `NavGraph` (unnecessary complexity for a simple mode switch inside one tab).

Integration in `BridgeScreen`:

```kotlin
@Composable
fun BridgeScreen(navController: NavController) {
    val bridgeVm = hiltViewModel<BridgeViewModel>()
    val walkerVm = hiltViewModel<PathWalkerViewModel>()
    var mode by rememberSaveable { mutableStateOf(BridgeMode.BRIDGE) }

    Column(modifier = Modifier.fillMaxSize()) {
        BridgeModeToggle(mode = mode, onModeChange = { mode = it })
        when (mode) {
            BridgeMode.BRIDGE -> BridgeModeContent(viewModel = bridgeVm, navController = navController)
            BridgeMode.PATH_WALKER -> PathWalkerContent(viewModel = walkerVm)
        }
    }
}
```

`rememberSaveable` persists mode across back-stack navigation. Both ViewModels are instantiated at `BridgeScreen` entry and survive mode switches — a bridge search running in `bridgeVm.viewModelScope` continues while the user is on the Path Walker view. `BridgeModeToggle` uses `SingleChoiceSegmentedButtonRow` from Material 3 (already a dependency at 1.5.0-alpha09).

`BridgeModeContent` is a refactor of the existing `BridgeScreen` body into a private composable — a mechanical extraction with no logic changes.

---

## Question 7: Shared components from PathSheet.kt

**Decision: `HorizontalPathStrip` and `PathNodeRow` are directly reusable. `PathSheet` bottom-sheet wrapper is not needed in Path Walker.**

`HorizontalPathStrip` is the right component for the walk breadcrumb:

| Parameter | Walk breadcrumb usage |
|-----------|----------------------|
| `path` | `graphState.activePath` (the trail the user walked) |
| `artistMetadata` | Populated from `KotlinBridgeCache` via `PathWalkerViewModel` — same `BridgeArtistInfo` data class |
| `nowPlayingIndex` | Index of `graphState.currentNodeId` in `activePath` |
| `familiarityMap` | Same dual-source lookup from `BridgeViewModel.resolveFamiliarity()` — extracted to a shared utility function |
| `playableArtists` | Artists whose tracks have resolved and been queued |
| `isBuilding` | `PathWalkerViewModel.isExpanding` |
| `onArtistClick` | Jump back to an ancestor: replay that artist's cached `mediaItems` |

`PathNodeRow` is reusable for the FALA candidate cards — each frontier node is a `PathNodeRow` displaying the proposed next artist's name, tags, and listener count. Tapping calls `stepTo()`.

`GenreTagChip`, `ArtistFamiliarityBadge`, and `VerticalConnectorLine` are all reusable without modification.

`PathSheet` (the bottom-sheet wrapper with drag handle and `LazyColumn`) is not reused in Path Walker — that component was designed as a standalone bottom sheet triggered from the bridge path view. The breadcrumb in Path Walker is inline in the scrolling screen layout.

One refactor is warranted: `resolveFamiliarity()` logic currently lives inside `BridgeViewModel` as a private function. Extract it as an internal function in a new `BridgeFamiliarityResolver.kt` class (injected into both ViewModels) so `PathWalkerViewModel` does not duplicate it.

---

## Question 8: Concurrent state — bridge search running while Path Walker is active

**Decision: Independent ViewModel lifecycles provide natural isolation. No cross-ViewModel concurrency guard needed.**

When the user switches from Bridge mode to Path Walker mid-search:
- `BridgeViewModel.isRunning` remains true; the search continues in `viewModelScope`
- `PathWalkerViewModel` starts fresh with no knowledge of bridge state
- Switching back to Bridge mode shows the search as it completed (or still in progress)

This is correct behavior. The user should be able to explore Path Walker while waiting for a long bridge search.

Each ViewModel guards its own operations:

- `BridgeViewModel` already has: `if (isRunning) return` in `startBridge()`
- `PathWalkerViewModel` needs an equivalent: `if (_isExpanding.value) return` in `stepTo()` to prevent double-tap while a FALA expansion is in-flight

Playback is shared (single ExoPlayer via `PlayerConnection`), but this is not a conflict — Path Walker's `playQueue()` intentionally replaces Bridge's queue when the user walks to a new artist. This is the desired behavior.

Room writes from both ViewModels (bridge history + walk step persistence) use the same `DatabaseDao` on `Dispatchers.IO`. Room WAL mode (already configured in the project) handles concurrent writes safely.

---

## Complete Component Boundary Map

```
BridgeScreen (EXTENDED — mode toggle added)
├── BridgeModeToggle [NEW composable — SegmentedButton]
├── BridgeModeContent [REFACTOR — existing BridgeScreen body extracted]
│   └── BridgeViewModel [EXISTING — unchanged]
│       ├── BridgeAlgorithm [@Singleton, EXISTING]
│       ├── KotlinBridgeCache [@Singleton, EXISTING]
│       ├── BridgePlaylistBuilder [@Inject, EXISTING]
│       └── MusicDatabase [@Singleton, EXISTING]
└── PathWalkerContent [NEW composable]
    ├── PathWalkerGraphCanvas [NEW — Compose Canvas]
    ├── HorizontalPathStrip [REUSED from PathSheet.kt — walk breadcrumb]
    ├── PathNodeRow (frontier cards) [REUSED from PathSheet.kt]
    └── PathWalkerViewModel [NEW @HiltViewModel]
        ├── KotlinBridgeCache [@Singleton, SHARED with BridgeViewModel]
        ├── BridgePlaylistBuilder [@Inject, SHARED]
        ├── BridgeFamiliarityResolver [NEW utility, SHARED with BridgeViewModel]
        └── MusicDatabase [@Singleton, for history writes]

PathSheet.kt (UNCHANGED)
├── HorizontalPathStrip [also used in PathWalkerContent]
├── PathNodeRow [also used in PathWalkerContent]
├── GenreTagChip [reusable]
├── ArtistFamiliarityBadge [reusable]
└── VerticalConnectorLine [reusable]

PathWalkerGraphCanvas [NEW composable]
├── reads WalkerNode.x/y (pre-computed positions, never computed inside DrawScope)
├── draws nodes as circles colored by WalkerNodeState
├── draws links via computeGeodesicArc() (O(1) per link, safe in DrawScope)
└── pointerInput for tap-to-select frontier node

New Room entities (new DB migration, version bump required):
├── WalkHistoryEntity  → table "walk_history"
├── WalkStepEntity     → table "walk_step" (FK: walk_history.id, CASCADE DELETE)
└── BridgeHistoryEntity → table "bridge_history"
```

---

## Data Flow: Path Walker Step (end to end)

```
1. User selects seed artist
   PathWalkerViewModel.setSeed(artist)
   → KotlinBridgeCache.getSimilarArtists(artist) [Dispatchers.IO]
   → top N similar artists → frontier WalkerNodes created
   → computeHyperbolicLayout(nodes, screenRadius) [Dispatchers.Default]
   → _graphState.value = initial graph: seed + N frontier nodes with positions

2. Canvas renders (read-only)
   PathWalkerGraphCanvas observes graphState.nodes
   Reads node.x, node.y — no computation
   Calls computeGeodesicArc() per link inline (O(1), pure math)
   Frontier nodes rendered as tappable targets

3. User taps frontier node (artist B)
   PathWalkerViewModel.stepTo(artistB, playerConnection)
   if (_isExpanding.value) return  [double-tap guard]
   → _isExpanding.value = true
   → artistA state → EXPLORED
   → artistB state → CURRENT
   → activePath += artistB
   → launch(IO) {
       BridgePlaylistBuilder.buildProgressively([artistB]) { _, _, tracks ->
           withContext(Main) { playerConnection.playQueue(ListQueue("Walking: $artistB", tracks)) }
       }
     }
   → launch(IO) {
       similar = KotlinBridgeCache.getSimilarArtists(artistB)
       newFrontierNodes = similar.take(5).map { WalkerNode(state = FRONTIER, ...) }
       newLinks = similar.take(5).map { WalkerLink(sourceId = artistB, ...) }
       positions = computeHyperbolicLayout(state.nodes + newFrontierNodes) [Default]
       _graphState.value = state with artistB=CURRENT, new frontiers, updated positions
       _isExpanding.value = false
     }
   → launch(IO) {
       WalkStepEntity(walkId, stepIndex, artistA, artistB).persist via DatabaseDao
     }

4. HorizontalPathStrip renders breadcrumb
   path = graphState.activePath
   nowPlayingIndex = activePath.indexOf(graphState.currentNodeId)
   onArtistClick(index) → jump back in queue to that artist's stored mediaItems
```

---

## Suggested Build Order

### Phase 1: PathWalkerViewModel + FALA expansion (no UI)
**Why first:** All subsequent phases depend on ViewModel correctness. No UI means no visual polish decisions blocking core logic.

Deliverables:
- `PathWalkerGraphState`, `WalkerNode`, `WalkerLink`, `WalkerNodeState` data classes
- `PathWalkerViewModel` with `setSeed()`, `stepTo()`, `_graphState` StateFlow
- `KotlinBridgeCache` integration (shared singleton — no new code)
- Unit tests: `setSeed()` produces correct frontier nodes, `stepTo()` transitions node states

### Phase 2: BridgeScreen mode toggle + content split
**Why second:** BridgeScreen refactoring is a prerequisite for all Path Walker UI. Separating `BridgeModeContent` from `BridgeScreen` body must happen before any Path Walker content can be placed.

Deliverables:
- `BridgeMode` enum
- `BridgeModeToggle` composable (`SingleChoiceSegmentedButtonRow`)
- `BridgeScreen` refactored: `BridgeModeContent` wraps existing body unchanged
- `PathWalkerContent` composable stub (placeholder text)
- Acceptance criteria: existing Bridge tab behavior 100% unchanged

### Phase 3: Hyperbolic Canvas
**Why third:** Independent of the FALA card list UI. Can be developed and polished while Phase 4 is in progress. The hardest new visual component.

Deliverables:
- `computeHyperbolicLayout()` ported from `hyperbolicLayout.ts` to Kotlin (~150 lines, pure function)
- `computeGeodesicArc()` ported from `hyperbolicLayout.ts` to Kotlin (~60 lines, pure function)
- `PathWalkerGraphCanvas` composable: `BoxWithConstraints` + `Canvas`, node circles, link arcs, node state colors
- Layout triggered via `LaunchedEffect(graphState.nodes, diskRadius)` in `PathWalkerViewModel`
- Touch detection: `pointerInput` on Canvas, hit-test against node positions, calls `stepTo()`

### Phase 4: FALA card list + playback
**Why fourth:** Depends on Phase 1 (ViewModel) and Phase 2 (content shell). Not blocked by Phase 3 — can use a placeholder canvas or a simple list-only view initially.

Deliverables:
- FALA frontier node cards below the canvas (reusing `PathNodeRow`)
- `stepTo()` triggers `BridgePlaylistBuilder` + `playerConnection.playQueue()`
- `HorizontalPathStrip` breadcrumb (reusing existing component unchanged)
- Now-playing highlight via equivalent of `onNowPlayingArtistChanged()`

### Phase 5: Walk history + Bridge history persistence
**Why fifth:** Non-blocking. History can be added once the end-to-end walk experience is working.

Deliverables:
- `WalkHistoryEntity`, `WalkStepEntity`, `BridgeHistoryEntity` Room entities
- New Room migration (version increment)
- `DatabaseDao` additions: insert/query/delete for walk and bridge history
- `PathWalkerViewModel.endWalk()` persists `WalkHistoryEntity`
- `BridgeViewModel.startBridge()` persists `BridgeHistoryEntity` on path found
- History UI: list screen accessible from a "History" icon in BridgeScreen header

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Computing hyperbolic layout inside Canvas DrawScope
**What:** Calling `computeHyperbolicLayout()` inside `Canvas { ... }` lambda.
**Why bad:** Runs on every frame, blocks the render thread, causes dropped frames. `DrawScope` is not a coroutine scope — no structured concurrency possible inside it.
**Instead:** Trigger layout via `LaunchedEffect` on graph state changes, store positions in `WalkerNode.x`/`.y`.

### Anti-Pattern 2: Storing PlayerConnection in PathWalkerViewModel
**What:** `@Inject constructor(... playerConnection: PlayerConnection)`.
**Why bad:** `PlayerConnection` holds a reference to the Android `Player` object. Injecting it into a ViewModel-scoped component causes lifecycle mismatch and leaks.
**Instead:** Pass `playerConnection` as a parameter to each playback method, exactly as `BridgeViewModel.replayCard(cardId, playerConnection)` does.

### Anti-Pattern 3: Adding history columns to existing cache tables
**What:** Adding `walkId` or `historyFlag` to `BridgeSimilarArtistEntity` or `BridgeArtistMetaEntity`.
**Why bad:** `KotlinBridgeCache.init {}` purges both tables on every cold start (`clearAllBridgeSimilarArtists()`, `clearAllBridgeArtistMeta()`). History rows would be silently deleted as a cache side effect.
**Instead:** Dedicated history tables with no coupling to cache tables.

### Anti-Pattern 4: Cancelling BridgeViewModel search on mode switch
**What:** Observing the mode toggle in `BridgeViewModel` and cancelling `startBridge()` coroutine when switching to Path Walker mode.
**Why bad:** Silent data loss. User may explore Path Walker while waiting for a bridge, then want to switch back to see the result.
**Instead:** Independent ViewModel lifecycles. Mode is purely a UI concern inside `BridgeScreen`.

### Anti-Pattern 5: Separate StateFlow for node positions
**What:** A `MutableStateFlow<Map<String, Offset>>` for positions, separate from the main graph state.
**Why bad:** Two StateFlows that must be kept in sync. Canvas would need to `combine()` them, creating complexity and potential for temporary inconsistency mid-frame.
**Instead:** `x: Float, y: Float` stored directly on `WalkerNode`. Single source of truth. Layout computation updates nodes via `_graphState.value = state.copy(nodes = updatedNodes)`.

---

## Scalability Considerations

| Concern | At Path Walker scale (50-150 nodes) | Threshold where it matters |
|---------|-------------------------------------|---------------------------|
| Hyperbolic layout CPU | Negligible — pure math, <1ms on any device | >500 nodes |
| `List<WalkerNode>.copy()` | Negligible | >500 nodes |
| Canvas hit-testing (O(n) per touch) | Fine for 150 nodes | >1000 nodes |
| Room history queries | Negligible — few hundred rows | >10,000 rows |
| `KotlinBridgeCache` semaphore contention | Shared with `BridgeAlgorithm` — possible contention during simultaneous bridge search + walker expansion; existing semaphore(8) handles this | Already handled by existing semaphore |

---

## Sources

All findings are from direct code inspection. Confidence: HIGH throughout.

| File | Key findings extracted |
|------|----------------------|
| `BridgeViewModel.kt` | ViewModel size (789 lines), PlayerConnection pass-through pattern, `buildPlaylistProgressive()` flow |
| `BridgeScreen.kt` | Existing composable structure, no mode toggle yet, `hiltViewModel()` usage |
| `PathSheet.kt` | `HorizontalPathStrip` full signature, `PathNodeRow` reusability, `PathSheet` is a bottom-sheet wrapper |
| `KotlinBridgeCache.kt` | `init{}` purge behavior — critical for history table isolation decision |
| `DatabaseDao.kt` | Bridge cache methods at 1750-1769 — all new methods append to end of file |
| `BridgeSimilarArtistEntity.kt` / `BridgeArtistMetaEntity.kt` | Purgeable semantics confirmed |
| `graphStore.ts` | Zustand state shape — mapped to Kotlin `StateFlow<PathWalkerGraphState>` |
| `types.ts` | `NodeState`, `GraphNode`, `GraphLink` — mapped to Kotlin data classes |
| `hyperbolicLayout.ts` | `computeHyperbolicLayout()` is pure, ~150 lines, safe to port; `computeGeodesicArc()` is O(1), safe in DrawScope |
| `crawl.ts` | BFS streaming pattern — basis for `stepTo()` FALA expansion model |
