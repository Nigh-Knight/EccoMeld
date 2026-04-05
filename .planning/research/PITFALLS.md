# Domain Pitfalls

**Domain:** Adding Path Walker with hyperbolic graph visualization to existing Kotlin/Compose Android music app
**Researched:** 2026-04-05
**Confidence:** HIGH for Compose/Android-specific items (official docs verified); MEDIUM for hyperbolic math (arxiv + source code analysis); MEDIUM for Last.fm behavior (real-world issue threads)

---

## Scope Note

This document supersedes the v1.0 WebView pitfalls file. The v2.0 milestone ports the
EccoPath `hyperbolicLayout.ts` + `ForceGraph.tsx` Canvas rendering to Jetpack Compose
Canvas and adds a Path Walker mode on top of the existing native Kotlin bridge algorithm.
The critical risks here are specific to that porting work and its integration with the
existing `BridgeViewModel`, `MusicService`, and `KotlinBridgeCache` stack.

---

## Critical Pitfalls

Mistakes that cause rewrites, broken interactions, or unacceptable performance.

---

### Pitfall 1: Compose Canvas `drawScope` State Reads Trigger Full Canvas Redraws

**What goes wrong:** Every read of a `State<T>` or `StateFlow` value inside a `Canvas {
}` block registers that state as an observer of the entire draw phase for that composable.
When the graph has 100+ nodes and any observed state changes — even a single node's
position update — Compose invalidates and redraws the entire canvas on every frame. This
is catastrophic for Path Walker where node positions, playback state, loading spinners,
and expansion animations all change at different cadences.

The specific failure mode is: you add a `MutableStateFlow<Long>` for the animation clock
(updated via `withFrameMillis` at 60fps) to drive pulsing rings on frontier nodes. Because
this clock is read inside the `Canvas { }` block, every frame redraws all 100+ nodes and
all geodesic arc links — roughly 200+ `drawPath` calls per frame.

**Why it happens:** Compose's phase model separates composition, layout, and drawing. A
state read in the draw phase (inside `Canvas`, `drawBehind`, `drawWithContent`) only
triggers a redraw, not recomposition — this is actually the right behavior. The problem is
that `Canvas` in Compose is a single, monolithic draw scope: there is no sub-canvas
invalidation. Anything that changes causes everything to redraw.

**Consequences:** Dropped frames (below 60fps) with 50+ nodes. On mid-range devices
(which are the EccoMeld target given APK sideloading), the Canvas may run at 20-30fps
during active expansion, making the graph feel janky precisely when it needs to feel alive.

**Prevention:**
1. Keep the animation clock state (`withFrameMillis`) local to the Canvas draw scope using
   `produceState` or `LaunchedEffect` that writes to a `State<Long>` read only inside the
   `Canvas` block. This correctly restricts invalidation to the draw phase only.
2. Cache `Path` objects for geodesic arcs using `remember { }` keyed on node ID pairs.
   The `computeGeodesicArc` circumcircle calculation is not cheap — pre-compute all arcs
   when the graph changes, store as `Path` objects, and replay them in the draw loop.
3. Use `drawWithCache` modifier to cache static graph elements (explored/inactive nodes)
   into an off-screen bitmap. Only redraw the "live" layer (frontier nodes, active path,
   loading spinners) per frame.
4. Place the animation clock read in a `derivedStateOf` that only fires when the value
   changes by more than one frame threshold, not on every millisecond tick.

**Warning signs:** Systrace or Android Studio Layout Inspector shows the `Canvas` composable
invalidating every frame even when no user interaction is happening. Frame render time
exceeds 16ms.

**Phase:** Must be addressed at the start of the Canvas rendering phase, before writing
any animation code. The architecture (cached paths vs live layer) must be decided first.

---

### Pitfall 2: Touch Hit Testing Uses Screen Coordinates But Canvas Draws in Transformed Space

**What goes wrong:** The graph supports pan and zoom via `Modifier.transformable`. When
the user has zoomed in 3x and panned the disk to the right, a node that visually appears
at position (400, 300) on screen is stored in graph state at its pre-transform position
(133, 100). A naive `pointerInput` hit test that computes distance from `offset.x` to
`node.x` misses every node because it is comparing screen coordinates against canvas
coordinates.

The EccoPath `ForceGraph.tsx` handles this by calling `fg.screen2GraphCoords(canvasX,
canvasY)` — a method on the `react-force-graph-2d` library's ref. In Compose Canvas,
there is no such helper. The developer must manually invert the transform matrix.

**Why it happens:** `Modifier.transformable` mutates a `TransformableState` (scale,
offset, rotation). The `Canvas` block then applies `scale(state.scale)` +
`translate(state.offset)` before drawing nodes. The `pointerInput` modifier receives raw
touch coordinates in the composable's local layout space, not in the post-transform canvas
space.

**Consequences:** Tapping a node does nothing. Tapping empty space accidentally triggers
a node. On a zoomed-in disk with closely spaced frontier nodes, the hit test lands on the
wrong node. This is the single most reported bug in custom Canvas graph libraries on
Android.

**Prevention:**
Convert touch coordinates to canvas space before hit testing:
```kotlin
// state = TransformableState(scale, offset)
val canvasX = (touchOffset.x - state.offset.x) / state.scale
val canvasY = (touchOffset.y - state.offset.y) / state.scale
```
Then compute Euclidean distance from `(canvasX, canvasY)` to each node's `(node.x, node.y)`.

The hit radius must also be adjusted for zoom:
```kotlin
// 22.dp minimum touch target (Android accessibility guideline = 44dp diameter)
val minHitRadiusPx = with(density) { 22.dp.toPx() }
val hitRadius = maxOf(nodeRadiusPx, minHitRadiusPx / state.scale)
```
Dividing by scale ensures the touch target is never smaller than 22dp regardless of zoom
level — matching the `nodePointerAreaPaint` logic in `NodeRenderer.ts` (line 135:
`Math.max(BASE_RADIUS * 1.5, 22 / globalScale)`).

**Warning signs:** Tapping nodes fails intermittently, especially after zooming in or
panning. Accuracy degrades more at higher zoom levels.

**Phase:** Implement in the same phase as pan/zoom gesture handling. Do not implement hit
testing and pan/zoom in separate phases — they are mathematically coupled.

---

### Pitfall 3: Hyperbolic Layout Math Breaks Near the Disk Boundary

**What goes wrong:** `computeHyperbolicLayout` maps tree levels to Poincaré disk positions
using `tanh(hyperR / 2)`. As `hyperR` grows (deeper tree levels), `tanh` approaches 1.0
asymptotically, mapping nodes to positions very close to the unit circle boundary. In
`computeGeodesicArc`, the circumcircle calculation involves `D = 2*(p1x*(p2y-p3y) + ...)`.
When two nodes are both near the boundary, `aDist = ax*ax + ay*ay` approaches 1.0, the
inversion point `A* = A / aDist` explodes to a very large value, and the resulting
circumcircle radius `r` becomes enormous — in some cases larger than `Float.MAX_VALUE`
when accumulated through double arithmetic.

The specific edge cases in the TypeScript source that can produce NaN or Infinity in
Kotlin:

1. `aDist < 0.001` check (line 198) uses `Double` in Kotlin. If `ax` and `ay` are both
   `Float` from a node position, converting to `Double` via `toDouble()` accumulates
   sufficient precision — but if kept as `Float`, the 0.001 threshold may pass when the
   actual distance is still effectively zero (Float epsilon issues).
2. `Math.abs(D) < 1e-8` collinearity check (line 214): in Kotlin `abs(D)` where `D` is
   computed from `Float` positions has only ~7 decimal digits of precision. Points that
   are mathematically collinear may not be detected as such, causing the arc to be drawn
   with an astronomically large radius rather than falling back to a straight line.
3. At `HYPER_STEP = 0.8` and 7 tree levels, the deepest node has `hyperR = 5.6`,
   mapping to `eucR = tanh(2.8) * screenRadius ≈ 0.993 * screenRadius`. This places the
   node 0.7% from the boundary. The inversion magnitude `1/aDist ≈ 143` — large but
   representable. At 10 levels this exceeds the stable range.

**Consequences:** Geodesic arcs render as giant circles cutting across the entire canvas,
or as NaN positions that Compose Canvas ignores silently (draws nothing), leaving nodes
visually disconnected. The `drawArc` call with an astronomically large radius also causes
platform-level overdraw artifacts on some GPU drivers.

**Prevention:**
1. Use `Double` precision throughout the entire `computeGeodesicArc` port — never `Float`.
   The TypeScript version uses JavaScript's `Number` (64-bit IEEE 754 double) implicitly.
2. Add a boundary clamp after `hyperToEuclidean`: cap the result at 0.97 (not 1.0) to
   keep all nodes 3% inside the boundary. This provides numeric headroom for inversion.
3. Add an explicit arc radius sanity check: if `screenR > screenRadius * 50`, fall back
   to a straight line. This catches the collinearity-miss case.
4. Test with artists that generate 6+ hop paths (genre distance > 0.85 → `maxDepth = 7`)
   to stress the deep-tree case.

**Warning signs:** Geodesic arcs appear as full-screen circles. Some links between nodes
are invisible while others look correct. The graph looks different on each render if NaN
positions are involved.

**Phase:** Port and validate `hyperbolicLayout.ts` in a standalone unit test before
integrating with the Canvas composable. The math functions are pure — test them in
isolation with known inputs.

---

### Pitfall 4: Recomposition Storm from Graph State + Playback State on the Same Screen

**What goes wrong:** The Path Walker screen needs to observe at least four independent
state streams simultaneously: graph node/link state (from a new `PathWalkerViewModel`),
the active path state, playback state (which song is playing, from `PlayerConnection` via
`LocalPlayerConnection`), and UI state (which node is selected, zoom level, tab toggle).

Each of these is a `StateFlow` collected via `collectAsState()`. In Compose, each
`collectAsState()` call registers the composable as a subscriber. If all four are collected
at the top-level `PathWalkerScreen` composable, any change in any stream causes the entire
screen to recompose — including the `Canvas` composable — even if only the "now playing
artist" changed.

The existing `BridgeScreen.kt` already demonstrates this pattern with `uiState`,
`fromQuery`, `toQuery`, `bridgeCards`, `seedSuggestions`, `isLoadingSeeds`, `isBuilding`,
`showQueueDialog`, and `mediaMetadata` — nine `collectAsState()` calls at the root level.
Adding graph state here makes it worse.

**Consequences:** Playback position changes (which fire every second from ExoPlayer's
`MediaMetadata` updates) cause the entire Canvas to recompose at 1Hz, on top of the
animation-driven invalidation. Composition takes CPU time even though the draw phase is
the bottleneck.

**Prevention:**
1. Collect playback state (`mediaMetadata`, `nowPlaying`) in a **separate child composable**
   below the Canvas, not at the screen root. Playback state changes then only recompose
   that child, not the Canvas.
2. Use `derivedStateOf` for any value derived from multiple sources. For example:
   ```kotlin
   val nowPlayingNodeId by remember {
       derivedStateOf { graphNodes.value.find { it.name == nowPlayingArtist.value }?.id }
   }
   ```
   This only recomputes when both the graph and the artist name actually change.
3. Mark all graph state data classes `@Immutable` and `@Stable`. Compose's compiler plugin
   skips recomposition of composables whose stable parameters have not changed.
4. Split the Path Walker screen into: `PathWalkerCanvas` (only observes graph + transform
   state), `PathPanel` (observes path + playback state), `TabBar` (observes UI toggle state).
   Each composable only recomposes when its own slice of state changes.

**Warning signs:** Android Studio's recomposition counter shows the `Canvas` composable
recomposing at 1Hz even when the user is not touching the screen. CPU usage stays elevated
while music plays with graph static.

**Phase:** Must be addressed in ViewModel and screen architecture design before writing
any Canvas code. The state split decision is load-bearing.

---

### Pitfall 5: Last.fm Burst Rate Limiting During Rapid FALA Node Expansion

**What goes wrong:** Path Walker's core interaction is "tap a frontier node → expand to 5
children → tap one of those → expand to 5 more." Each expansion calls
`LastFM.getSimilarArtists()` once. If the user taps rapidly through 3-4 nodes before the
first expansion completes, the app fires multiple simultaneous `getSimilarArtists` calls,
potentially on top of any ongoing `KotlinBridgeCache` prefetch from the seed suggestions
loaded on tab entry.

`KotlinBridgeCache` uses a `Semaphore(8)` for concurrency control — this allows up to 8
simultaneous network calls. But Last.fm's free-tier limit is 5 req/sec averaged over 5
minutes. A burst of 8 simultaneous calls each starting at the same moment will hit the
rate limit on the first few that complete, since all responses arrive within ~200ms of
each other.

The EccoPath `ForceGraph.tsx` `expandNode()` function (line 198-268) handles this by
setting node state to `'loading'` and disabling re-expansion while loading. In Kotlin, if
the node state is not locked against re-tapping during expansion, rapid taps on the same
node can fire multiple `getSimilarArtists` calls for the same artist.

**Consequences:** Last.fm returns HTTP 429 / error code 29. The expansion silently fails.
The node stays in `loading` state with no recovery path. The user sees a spinner that never
resolves.

**Prevention:**
1. Add a `Set<String>` of in-flight artist expansions to the ViewModel. Before firing a
   `getSimilarArtists` call, check if the artist is already in-flight. If so, drop the
   tap silently.
2. Reduce the semaphore permits for Path Walker expansion specifically to 3 (not 8).
   Path Walker expansions are user-triggered, not algorithm-internal — they should be
   more conservative than the beam search.
3. On 429 / error 29 response, reset the node state to `frontier` (not stuck in
   `loading`) and show a transient snackbar: "Too many requests — try again in a moment."
4. Implement exponential backoff with jitter in the LastFM client for error-29 responses.
   Start at 1 second, double on each retry, cap at 30 seconds, max 3 retries.

**Warning signs:** Nodes get stuck in the loading state when the user taps quickly.
Logcat shows Last.fm error 29. Recovery requires restarting the Path Walker session.

**Phase:** Address in the Path Walker ViewModel implementation, before any UI wiring.
The in-flight guard and backoff must exist before the tap handler is connected.

---

## Moderate Pitfalls

---

### Pitfall 6: Artist Profile Images Cannot Be Loaded Inside `Canvas { }` Directly

**What goes wrong:** The EccoPath `NodeRenderer.ts` draws nodes as colored circles with
text labels. EccoMeld's richer design may want to show circular artist thumbnail images
inside each node (similar to Spotify's graph views). The naive implementation loads images
via Coil inside the `Canvas` block — but `Canvas` in Compose is a `DrawScope`, not a
composable scope. You cannot call `AsyncImage()` or `rememberAsyncImagePainter()` inside
`Canvas { }`. Attempting to do so causes a compile error or runtime crash
(`CompositionLocal not found`).

**Why it happens:** Compose's `Canvas` composable uses `DrawScope` which has no access
to `CompositionLocal` values (including Coil's image loader). Image loading requires a
composable context.

**Consequences:** Artist images cannot be drawn inside Canvas nodes without a workaround.
Developers who attempt it get cryptic compiler errors and may restructure the composable
tree incorrectly.

**Prevention:**
Load images outside the Canvas, store as `ImageBitmap`, then draw inside Canvas:
```kotlin
// Outside Canvas, in composable scope:
val bitmaps = nodes.associate { node ->
    node.id to rememberAsyncImagePainter(node.imageUrl)
        .state
        .painter
        ?.let { painter ->
            // Convert Painter to ImageBitmap for use in DrawScope
            val bitmap = ImageBitmap(nodeSize, nodeSize)
            val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
            with(DrawScope equivalent) { painter.draw(...) }
            bitmap
        }
}
// Inside Canvas:
bitmaps[node.id]?.let { drawImage(it, ...) }
```
A cleaner pattern: use `SubcomposeLayout` to pre-render node image composables into
`Painter` objects, then use `Painter.draw()` inside `DrawScope`. Alternatively, keep
nodes as text-only circles (matching the EccoPath design) and defer image thumbnails to
a future milestone.

**Warning signs:** Compiler error "CompositionLocal ... not found" inside the Canvas block.
Runtime crash with "No NodeCoordinator" when trying to call composable functions from
DrawScope.

**Phase:** Decide at the start of the Canvas rendering phase whether nodes show images
or text-only. If images are wanted, design the loading pipeline before writing Canvas code.

---

### Pitfall 7: `ShadowBlur` (Glow Effects) Has No Direct Equivalent in Compose Canvas

**What goes wrong:** `NodeRenderer.ts` uses `ctx.shadowColor` + `ctx.shadowBlur` to
produce the glow effects on seed, active, and current nodes (lines 57-63). Compose's
`DrawScope` has no `shadowBlur` API. Developers who look for `drawCircle(shadow=...)`
will find nothing and assume glow is not possible.

The naive workaround — applying `Modifier.shadow()` to a composable node outside the
Canvas — breaks the single-Canvas architecture. Each node would become a separate
composable, eliminating the performance benefit of batch Canvas drawing.

**Why it happens:** Compose Canvas's `DrawScope` maps to Android's `Canvas` API, which
does not have built-in blur. Shadow blur requires `Paint.maskFilter = BlurMaskFilter`.

**Consequences:** Glow effects are absent from the first implementation. Developers spend
time investigating non-existent APIs before finding the correct approach.

**Prevention:**
Use `Paint.maskFilter` with `BlurMaskFilter` inside the Canvas:
```kotlin
val glowPaint = remember {
    Paint().apply {
        asFrameworkPaint().apply {
            isAntiAlias = true
            maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL)
        }
    }
}
// In draw scope:
drawIntoCanvas { canvas ->
    glowPaint.color = Color(0x26FF8F7B) // seed glow color at 15% alpha
    canvas.drawCircle(Offset(x, y), radius + 10f, glowPaint)
}
```
Note: `BlurMaskFilter` is expensive per draw call. Pre-create `Paint` objects via
`remember { }` — never allocate them inside the draw loop. Also be aware that
`BlurMaskFilter` may not render on hardware-accelerated canvases on some API levels.
Test on API 26 (minimum SDK) explicitly.

**Warning signs:** No glow visible on any nodes. Build succeeds but visual output differs
from the EccoPath web design. Developers searching `DrawScope` API for `shadowBlur`.

**Phase:** Address in the Canvas rendering phase, in the same pass as node drawing.
Do not defer glow to a "polish" phase — it affects the Paint object allocation design.

---

### Pitfall 8: Panning the Graph Scrolls the Parent Screen Instead

**What goes wrong:** The Path Walker canvas lives inside the Bridge tab screen, which is
currently a `verticalScroll(rememberScrollState())` column (line 373 of `BridgeScreen.kt`).
A drag gesture on the Canvas composable will be intercepted by the parent scroll container
unless gesture ownership is explicitly claimed.

`Modifier.transformable` uses `awaitPointerEventScope` internally. When both the parent
`verticalScroll` and the child Canvas `transformable` are in the gesture hierarchy,
Compose's gesture disambiguation runs. The parent scroll wins by default for vertical
drags — panning the graph up/down scrolls the screen instead of panning the canvas.

**Why it happens:** Compose's pointer input system resolves gesture conflicts via a
"first claimer wins" model within the same pointer input chain. `verticalScroll` claims
vertical drags eagerly.

**Consequences:** The canvas is not pannable vertically. Users can pan horizontally but
the screen scrolls when panning up/down. The graph feels broken.

**Prevention:**
1. Place the Canvas in a dedicated, full-screen composable that fills the tab area, not
   inside the scrollable column. The Bridge tab needs a two-mode layout: scroll mode (for
   the current bridge card list) and graph mode (for Path Walker). These should be
   rendered in separate branches, not nested.
2. Use `Modifier.nestedScroll` with a `NestedScrollConnection` that consumes all scroll
   events when the Canvas is in graph mode, preventing them from reaching the parent.
3. As a simpler alternative: the Canvas can use `pointerInput(Unit) { detectDragGestures
   { ... } }` with `consumeAllChanges()` to block propagation to parents.

**Warning signs:** Vertical pan of the graph scrolls the Bridge tab's card list. The
canvas can be zoomed but not panned vertically. `onGloballyPositioned` shows the Canvas
receiving events but parent scroll also fires.

**Phase:** Must be addressed in the screen layout architecture phase, before integrating
the Canvas. The tab mode split (bridge list vs graph) is a foundational layout decision.

---

### Pitfall 9: Growing Graph State in ViewModel Causes Unbounded Memory Growth

**What goes wrong:** Each FALA expansion adds 5 nodes and 5 links to the graph state.
After 20 expansions, the graph has 100+ nodes. The naive ViewModel implementation stores
all nodes as a `List<GraphNode>` in a `MutableStateFlow`. Each update creates a new list
copy (immutable state pattern), so the garbage collector sees the full list allocated and
freed on every expansion. With node metadata (name, state, parentId, match score, image
URL, bitmap thumbnail), each `GraphNode` is ~500 bytes of heap.

More critically: if the Path Walker session is long (30+ expansions, 150+ nodes), the
artist name strings, cached `ImageBitmap` objects, and `Path` objects for geodesic arcs
accumulate. On a 512MB RAM device (still common for sideloaded APK users), OOM kills are
possible.

**Why it happens:** Graph exploration is unbounded by design. EccoPath runs in a browser
tab with V8's garbage collector. Android's Dalvik/ART has a smaller managed heap and
more aggressive OOM kills, especially when audio playback (ExoPlayer) is running
concurrently in `MusicService`.

**Consequences:** App killed by OOM during an active Path Walker session. Memory pressure
causes ExoPlayer to lose its audio buffer, interrupting playback. `onLowMemory()` is
called but no cleanup happens because graph state is in a ViewModel, not a
system-visible cache.

**Prevention:**
1. Cap the in-memory graph at 200 nodes. When the limit is reached, prune nodes more
   than 3 hops from the current node (they are off-screen in the hyperbolic layout
   anyway). Store pruned nodes in Room with their position for potential re-expansion.
2. Use weak references for `ImageBitmap` node thumbnails. The bitmap cache should be
   bounded (e.g., `LruCache<String, ImageBitmap>(maxSize = 50)`), not a plain `Map`.
3. On `ComponentActivity.onTrimMemory(TRIM_MEMORY_MODERATE)`, clear the thumbnail cache
   and mark all non-current-path nodes as needing thumbnail reload.
4. The `Path` object cache for geodesic arcs should be a `LinkedHashMap` with a max size,
   evicting the oldest paths first (LRU pattern).

**Warning signs:** Memory usage reported in Android Studio grows monotonically during a
Path Walker session. `GC_FOR_ALLOC` messages in logcat. App killed after 30+ node
expansions on a low-RAM device.

**Phase:** Address in the Path Walker ViewModel design. The node cap and LRU caches must
be designed upfront — they cannot be retrofitted cleanly after the state shape is fixed.

---

### Pitfall 10: `withFrameMillis` Animation Loop Conflicts with Coroutine Cancellation

**What goes wrong:** The pulsing ring animation on `loading` and `current` state nodes
(replicating `NodeRenderer.ts` lines 85-103) requires a continuous animation loop running
at the display refresh rate. The standard pattern in Compose is:

```kotlin
LaunchedEffect(Unit) {
    while (true) {
        withFrameMillis { frameTime ->
            animationClock.value = frameTime
        }
    }
}
```

This loop runs indefinitely in the `LaunchedEffect` coroutine. If the composable leaves
composition (user switches tab), the coroutine is cancelled correctly. However, if the
Path Walker is nested inside a `NavHost` route and the route is re-entered (back stack
pop + push), the `LaunchedEffect` is restarted. If the old coroutine was not cancelled
cleanly — for example, if `withFrameMillis` was inside a `try { } catch (e:
CancellationException) { /* swallowed */ }` block — the old loop continues running as a
zombie, driving two animation clock updates per frame. This doubles the Canvas redraw
rate.

**Why it happens:** `CancellationException` must not be swallowed in Kotlin coroutines.
It is the mechanism by which structured concurrency cancels coroutines. Any `try/catch`
that catches `Exception` or `Throwable` without rethrowing `CancellationException`
prevents coroutine cancellation.

**Consequences:** Two animation loops driving 120 Canvas redraws per second instead of 60.
Frame drops visible to users. Battery drain. The issue is invisible in code review
because the bug only appears after navigation.

**Prevention:**
1. Never catch `CancellationException` without rethrowing. Use `catch (e: Exception)` only
   for specific, expected exceptions — not as a catch-all around animation loops.
2. In the existing codebase, check `BridgeViewModel` and `MeldBridgeInterface` for any
   `catch (e: Exception)` blocks that might swallow cancellation (these would also affect
   the beam search coroutines).
3. Prefer `InfiniteTransition` (Compose animation API) over manual `withFrameMillis` loops
   for simple periodic animations like pulsing rings. `InfiniteTransition` is
   automatically lifecycle-aware and does not require manual cancellation handling.

**Warning signs:** Frame rate climbs to 120fps in Systrace during a Path Walker session
that has been navigated in and out. CPU usage is higher on the second visit to the tab
than the first.

**Phase:** Address in Canvas animation implementation. Establish the `InfiniteTransition`
vs `withFrameMillis` policy before writing any animation code.

---

### Pitfall 11: Zoom/Pan State Survives Navigation but Graph Position Feels Wrong on Re-entry

**What goes wrong:** `TransformableState` (scale, offset) is typically held in a
`rememberSaveable` or `remember` in the composable. When the user navigates from the Bridge
tab to Home and back, if the state is `remember`-based, it is reset to the default
(scale=1, offset=zero). The graph re-appears zoomed-out even if the user had zoomed into a
specific area.

Conversely, if state is in the ViewModel and survives navigation, the graph re-appears in
the last-viewed transform. But if the user had panned the graph off-center and the ViewModel
triggered a `zoomToFit` (like EccoPath's `graphRef.current?.zoomToFit(600, 60)` on node
count changes), the ViewModel's stored transform is stale and the visible graph is
off-screen.

**Why it happens:** Pan/zoom state is inherently presentation-layer state (belongs in the
composable) but also needs to survive tab navigation (which usually requires ViewModel
storage). The two requirements conflict.

**Prevention:**
Store transform state in the ViewModel as simple `Float` + `Offset` values but reset them
explicitly when the graph data changes significantly (e.g., when a new seed is selected).
Provide a "re-center" button that snaps back to `scale=1, offset=center` to give users a
recovery path. Implement auto-fit on first node expansion only (not on every expansion)
to avoid fighting the user's manual pan.

**Phase:** Address in Pan/zoom implementation. The reset policy must be explicitly
decided — not left as undefined behavior.

---

### Pitfall 12: Integrating Path Walker Toggle with Existing Bridge Tab Breaks Existing Tests

**What goes wrong:** The Bridge tab (from v1.0) has 9 UAT plans and a working UI with
stacked bridge cards. Adding a mode toggle (Bridge mode vs Path Walker mode) changes the
root structure of `BridgeScreen.kt`. The existing stacked card list and the new Canvas
graph need to coexist in the same tab, controlled by a mode state. If implemented naively
as an `if (mode == BRIDGE) { ... } else { ... }` at the screen root, both branches are
composed but only one is visible — which means the Canvas composable draws invisible
frames in the background.

**Why it happens:** Compose's `if` branching removes composables from composition when
the condition is false. However, if the developer uses `AnimatedVisibility` (for a
fade transition between modes), both composables remain in composition simultaneously
during the transition and after if `visible=false` composables are not exited.
`AnimatedVisibility(visible = false)` keeps the composable in composition (for exit
animations) — meaning the Canvas is drawing at 60fps while hidden.

**Consequences:** Canvas draws 60fps to a composable that renders nothing visible.
ExoPlayer + hidden Canvas + visible bridge list = three simultaneous CPU consumers.

**Prevention:**
Use `if (mode == PATHWALKER)` not `AnimatedVisibility` for the Canvas — hard-switch with
no transition. Use `AnimatedVisibility` only for the tab bar toggle control itself, not
the content it switches. Alternatively, use `AnimatedContent` with a `ContentTransform`
that exits the old composable before entering the new one.

**Phase:** Bridge tab mode architecture. Decide the switching mechanism before writing
either the Canvas or the toggle UI.

---

## Minor Pitfalls

---

### Pitfall 13: `computeGeodesicArc` Returns `null` for Straight-Line Geodesics — Draw Path Must Handle Both

**What goes wrong:** `computeGeodesicArc` returns `null` when two nodes are collinear
through the disk center (the geodesic is a diameter). The TypeScript caller in
`LinkRenderer.ts` handles this with a `if (arc) { ctx.arc(...) } else { ctx.moveTo/lineTo }`
branch. In a Kotlin port, if the `null` return is not checked and `arc!!` is used, the
app crashes on any link that passes through the center — which always includes the seed
node's direct children.

**Prevention:** Pattern-match the return: `arc?.let { drawArc(...) } ?: drawLine(...)`.
Unit test `computeGeodesicArc(0f, 0f, 100f, 0f, 300f)` — this should return null
(seed-to-child link passes through origin).

**Phase:** Geodesic arc port unit test. Do not merge without this specific test.

---

### Pitfall 14: `ctx.letterSpacing` Canvas API Has No Kotlin/Android Equivalent

**What goes wrong:** `NodeRenderer.ts` applies `ctx.letterSpacing = '-0.02em'` to the
artist name label (line 114-115). This Canvas 2D API property is supported in Chromium
but not available on Android's `Canvas` or Compose's `DrawScope`. Attempting to set it
in Kotlin via `drawText` will not error but will silently have no effect. The label will
render without the tracking adjustment, making it look slightly wider than the design.

**Prevention:** Use `TextLayoutResult` with `letterSpacing` set in `SpanStyle` when
drawing text via `drawText(textLayoutResult, ...)`. This requires pre-computing the text
layout outside the Canvas draw call:
```kotlin
val textMeasurer = rememberTextMeasurer()
val textResult = remember(node.name) {
    textMeasurer.measure(
        AnnotatedString(node.name),
        TextStyle(letterSpacing = (-0.02).em)
    )
}
// In Canvas:
drawText(textResult, topLeft = Offset(x - textResult.size.width / 2f, labelY))
```
Pre-compute and cache `TextLayoutResult` per node outside the draw loop — measuring text
on every frame is expensive.

**Phase:** Node label rendering. A minor visual issue but foundational for the text
drawing architecture.

---

### Pitfall 15: Database Schema Must Be Extended for Path Walker Walk History

**What goes wrong:** Path Walker walk history (persisting explored nodes, current path,
expansion tree) requires new Room tables. The existing database is at schema version 37
(post v1.0 bridge tables). A new migration bump is required. The existing
`fallbackToDestructiveMigration(dropAllTables = true)` means a bad migration drops all
user data including the v1.0 bridge history tables.

**Prevention:** Follow the migration test protocol established in the v1.0 pitfalls:
write a `MigrationTestHelper` test from version N-1 to N before merging. For Path Walker
specifically, consider a separate database file (`pathwalker.db`) to isolate schema
evolution from the main library database.

**Phase:** Any phase that adds Path Walker persistence. Write the migration test first.

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| Canvas architecture | Recomposition storm from clock state (Pitfall 1) | Isolate animation clock to draw phase; cache arc `Path` objects |
| Canvas architecture | Hidden Canvas drawing at 60fps (Pitfall 12) | Use hard `if` not `AnimatedVisibility` for mode switching |
| Pan/zoom + hit test | Wrong coordinate space for touch (Pitfall 2) | Invert transform before hit testing; scale hit radius by zoom |
| Pan/zoom + hit test | Parent scroll intercepts Canvas pan (Pitfall 8) | Separate screen layout for graph mode vs bridge list mode |
| Hyperbolic math port | NaN/Infinity near disk boundary (Pitfall 3) | Use `Double` precision; clamp euclidean radius at 0.97; add arc sanity check |
| Hyperbolic math port | `null` geodesic not handled (Pitfall 13) | Pattern-match return; unit test seed-to-child link case |
| Node rendering | Glow effects need `BlurMaskFilter` (Pitfall 7) | Pre-allocate `Paint` with `maskFilter`; test on API 26 |
| Node rendering | Artist images cannot load in DrawScope (Pitfall 6) | Pre-load as `ImageBitmap` outside Canvas; or stay text-only |
| Node rendering | Letter spacing has no Canvas equivalent (Pitfall 14) | Use `TextMeasurer` with `SpanStyle(letterSpacing)`; cache results |
| State architecture | Multiple StateFlows cause Canvas recompositions (Pitfall 4) | Split screen into Canvas + Panel child composables; use `derivedStateOf` |
| State architecture | Growing graph causes OOM (Pitfall 9) | Cap at 200 nodes; LRU bitmap cache; prune off-screen nodes |
| FALA expansion | Burst API calls on rapid taps (Pitfall 5) | In-flight guard Set; semaphore limit 3; exponential backoff on 429 |
| Animation | `withFrameMillis` zombie after navigation (Pitfall 10) | Never swallow `CancellationException`; prefer `InfiniteTransition` |
| Pan/zoom UX | Transform state lost or stale on re-entry (Pitfall 11) | Store in ViewModel; reset on seed change; provide re-center button |
| DB schema | Migration drops user data (Pitfall 15) | MigrationTestHelper test required; consider separate `pathwalker.db` |

---

## Sources

- [Android Developers: Jetpack Compose performance best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices) — HIGH confidence (official)
- [Android Developers: Compose phases (draw phase state reads)](https://developer.android.com/develop/ui/compose/phases) — HIGH confidence (official)
- [Android Developers: Graphics modifiers (drawWithCache)](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers) — HIGH confidence (official)
- [Android Developers: Pointer input in Compose](https://developer.android.com/develop/ui/compose/touch-input/pointer-input) — HIGH confidence (official)
- [Android Developers: Multi-touch (transformable)](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/multi-touch) — HIGH confidence (official)
- [Android Developers: Tap and press gestures](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/tap-and-press) — HIGH confidence (official)
- [Android Developers: DrawScope API reference](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/drawscope/DrawScope) — HIGH confidence (official)
- [Android Medium: Custom Canvas Animations in Jetpack Compose](https://medium.com/androiddevelopers/custom-canvas-animations-in-jetpack-compose-e7767e349339) — HIGH confidence (Android Developer Relations author)
- [Numerical Aspects of Hyperbolic Geometry (arXiv 2404.09039)](https://arxiv.org/html/2404.09039v1) — MEDIUM confidence (academic paper; floating point precision analysis)
- [Last.fm API Terms of Service §4.4 (rate limit: 5 req/sec)](https://www.last.fm/api/tos) — HIGH confidence (official ToS)
- [Navidrome issue #2421: Last.fm error 29 real-world behavior](https://github.com/navidrome/navidrome/issues/2421) — MEDIUM confidence (real-world reproduction)
- [ProAndroidDev: Exploring Canvas in Jetpack Compose](https://proandroiddev.com/exploring-canvas-in-jetpack-compose-crafting-graphics-animations-and-game-experiences-b0aa31160bff) — MEDIUM confidence (verified against official docs)
- [EccoPath source: lib/hyperbolicLayout.ts, components/graph/ForceGraph.tsx, components/graph/NodeRenderer.ts, components/graph/LinkRenderer.ts](file:///home/kepler/Projects/EccoPath/) — HIGH confidence (primary source, read directly)
- [EccoMeld source: BridgeViewModel.kt, KotlinBridgeCache.kt, BridgeAlgorithm.kt, BridgeScreen.kt, LastFM.kt](file:///home/kepler/Projects/EccoMeld/) — HIGH confidence (primary source, read directly)
