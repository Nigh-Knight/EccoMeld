# Technology Stack — EccoMeld Path Walker & Hyperbolic Graph Milestone

**Project:** EccoMeld v2.0 Path Walker & Discovery
**Researched:** 2026-04-05
**Scope:** Additive stack for hyperbolic graph Canvas rendering, multi-gesture touch, node animations, and bridge/walk history persistence

---

## Existing Stack (Do Not Change)

The following already exists. Do not duplicate or replace any of it.

| Technology | Version | Role |
|---|---|---|
| Kotlin | 2.3.10 | All app code |
| Jetpack Compose | 1.10.2 | UI framework — includes Canvas, animation, gesture APIs |
| Material 3 | 1.5.0-alpha09 | Design system |
| Media3/ExoPlayer | 1.7.1 | Audio playback |
| Room | 2.8.4 | SQLite persistence — already has `bridge_artist_meta` and `bridge_similar_artists` tables |
| Hilt | 2.59.1 | Dependency injection |
| kotlinx.serialization | (current) | JSON parsing |
| compose-animation | 1.10.2 | Already in `libs.versions.toml` as `compose-animation` |
| Last.fm module | existing | `getSimilarArtists`, `getArtistInfo` — do not re-implement |

---

## New Dependencies Required: Zero

The hyperbolic graph visualization, gesture handling, animation, and math for this milestone are all achievable with APIs already present in the existing Compose 1.10.2 + Room 2.8.4 stack.

**No new Gradle dependencies needed.**

This is the most important finding of this research. Every third-party Android graph visualization library found (`Composable-Graphs`, `Y-Charts`, `VicoChart`) is a charting library for bar/line/pie data — none supports force-directed or hyperbolic tree layouts. They would be dead weight. The correct approach is Compose Canvas with the math ported directly from `hyperbolicLayout.ts`.

---

## Detailed Stack Decisions Per Feature Area

### 1. Compose Canvas — Custom 2D Rendering

**API:** `androidx.compose.ui:ui` (already `compose-ui` in `libs.versions.toml`)

Everything needed for the Poincare disk is already available:

| Operation | Compose API | Notes |
|---|---|---|
| Draw filled node circle | `DrawScope.drawCircle(color, radius, center, style=Fill)` | Stable |
| Draw node stroke ring | `DrawScope.drawCircle(color, radius, center, style=Stroke(width=2f))` | Stable |
| Draw geodesic arc | `DrawScope.drawArc(color, startAngle, sweepAngle, useCenter=false, style=Stroke)` | Stable |
| Draw node label | `DrawScope.drawText(textMeasurer, text, topLeft, style)` | Requires `rememberTextMeasurer()` |
| Draw radial glow/bloom | `DrawScope.drawCircle(Brush.radialGradient(colors, center, radius))` | Stable |
| Transform/clip disk | `DrawScope.withTransform { translate(...); scale(...) }` + `clipPath` | Stable |
| Access raw Canvas | `DrawScope.drawIntoCanvas { it.nativeCanvas }` | For `Path.arcTo()` when needed |

**Key: Geodesic arcs** — the `computeGeodesicArc()` function in `hyperbolicLayout.ts` computes a circumscribed circle center + radius. In Compose, render this as `drawArc()` specifying the bounding box of that circle. The `startAngle`/`sweepAngle` pair from the TypeScript function maps directly to `drawArc`'s parameters. For the collinear case (null return from `computeGeodesicArc`), draw a straight line with `drawLine()`.

**Text on Canvas:** Requires `rememberTextMeasurer()` from `androidx.compose.ui.text` — this is stable in Compose 1.10.2. Create it at the composable level and pass into the Canvas `DrawScope`. Artist names on nodes should use `TextMeasurer.measure()` to get `TextLayoutResult`, then center the text on the node using the result's size.

```kotlin
val textMeasurer = rememberTextMeasurer()
Canvas(modifier = ...) {
    val layout = textMeasurer.measure(artistName, style = TextStyle(...))
    drawText(layout, topLeft = nodeCenter - Offset(layout.size.width / 2f, layout.size.height / 2f))
}
```

**Confidence:** HIGH — verified against official Android Developers Canvas docs and DrawScope API reference.

---

### 2. Touch Gesture Handling — Tap + Pan + Pinch-Zoom

**API:** `androidx.compose.foundation.gestures` (already `compose-foundation` in `libs.versions.toml`)

The graph needs three distinct gestures that must coexist:
- **Single tap** on a node — trigger FALA expansion or artist selection
- **Pan** — drag the whole graph
- **Pinch-to-zoom** — scale the disk in/out

**Recommended pattern: two stacked `pointerInput` modifiers**

Compose resolves multiple `pointerInput` blocks sequentially. Use one block for transform (pan + zoom) and a separate one for tap. The tap detector runs after the transform detector has consumed multi-touch events.

```kotlin
Modifier
    .pointerInput(Unit) {
        detectTransformGestures { centroid, pan, zoom, _ ->
            // Update panOffset and zoomScale state
            panOffset += pan
            zoomScale = (zoomScale * zoom).coerceIn(0.3f, 5f)
        }
    }
    .pointerInput(Unit) {
        detectTapGestures { tapOffset ->
            // Hit-test against node positions
            val hitNode = nodePositions.entries.firstOrNull { (_, pos) ->
                val dx = tapOffset.x - (pos.x * zoomScale + panOffset.x + diskCenter.x)
                val dy = tapOffset.y - (pos.y * zoomScale + panOffset.y + diskCenter.y)
                (dx * dx + dy * dy) <= (nodeRadius * zoomScale).let { it * it }
            }
            hitNode?.let { onNodeTap(it.key) }
        }
    }
```

**Why `detectTransformGestures` not `transformable` modifier:**
The `transformable` modifier does not consume the gesture from the pointer input pipeline in the same way — using `detectTransformGestures` inside `pointerInput` gives more predictable behavior when stacking with a tap detector. Both belong to `androidx.compose.foundation.gestures`.

**Hit testing:** Node positions from `computeHyperbolicLayout()` are in disk-relative coordinates (origin at disk center, unit = pixels). Apply the current pan offset and zoom scale to transform tap coordinates into disk space, then check Euclidean distance to each node center. Node tap radius should be at minimum `ViewConfiguration.minimumTouchTargetSize` (48dp) regardless of visual node size.

**Confidence:** HIGH — both `detectTransformGestures` and `detectTapGestures` are stable Compose APIs verified in official docs (updated February 2026). The stacking pattern is documented in the "Understand gestures" guide.

---

### 3. Animation APIs — Node State Transitions

**API:** `androidx.compose.animation.core` (already present via `compose-animation` in `libs.versions.toml`)

No new libraries needed. Use the following per node state:

| Animation Need | API | Pattern |
|---|---|---|
| Node bloom on expansion | `Animatable(0f)` + `animateTo(1f, tween(300))` | Launch in `LaunchedEffect(nodeState)` |
| Loading pulse on frontier nodes | `rememberInfiniteTransition()` + `animateFloat(0.4f, 1f, infiniteRepeatable(tween(900, easing=EaseInOutSine), RepeatMode.Reverse))` | Drives node alpha or scale |
| State change (frontier → active) | `animateColorAsState(targetColor, tween(200))` | For node fill color |
| Path highlight sweep | `animateFloatAsState(1f, tween(500))` | Drives arc draw progress |
| Node scale on select | `animateFloatAsState(if (selected) 1.2f else 1f, spring(dampingRatio=0.6f))` | Spring gives organic feel |

**Node state machine** matches `NodeState` from `types.ts` exactly:
`seed | active | explored | frontier | current | loading | error`

Map each state to a color from `MaterialTheme.colorScheme` at the call site — do not hardcode colors. Suggested mapping:

| NodeState | Color | Animation |
|---|---|---|
| `seed` | `primary` | Static, larger radius |
| `current` | `secondary` | Subtle scale pulse via spring |
| `active` | `onSurface` | Static |
| `frontier` | `tertiary` | Alpha pulse via InfiniteTransition |
| `loading` | `surfaceVariant` | Opacity pulse |
| `explored` | `outline` | Dimmed, no animation |
| `error` | `error` | Static |

**Important:** Keep per-node `Animatable` instances in a `remember { mutableStateMapOf() }` inside the composable — one entry per node ID. Create on node appearance, remove on node removal to avoid accumulating stale animators.

**Confidence:** HIGH — all APIs stable in Compose 1.10.2, verified against official animation docs and InfiniteTransition API reference.

---

### 4. Hyperbolic Layout Math — Poincare Disk

**No library needed.** Port `hyperbolicLayout.ts` directly to Kotlin using `kotlin.math`.

The TypeScript implementation in `/home/kepler/Projects/EccoPath/lib/hyperbolicLayout.ts` is self-contained pure math (166 lines). The Kotlin port is a mechanical translation.

**Math equivalence:**

| TypeScript | Kotlin |
|---|---|
| `Math.tanh(x)` | `kotlin.math.tanh(x)` (stdlib, no import) |
| `Math.PI` | `kotlin.math.PI` |
| `Math.cos(a) / Math.sin(a)` | `kotlin.math.cos(a) / kotlin.math.sin(a)` |
| `Math.atan2(y, x)` | `kotlin.math.atan2(y, x)` |
| `Math.sqrt(x)` | `kotlin.math.sqrt(x)` |
| `Map<string, Position>` | `Map<String, Offset>` (Compose `Offset`) |

**Use `Offset` as the position type** — Compose Canvas operates in `Offset` coordinates, and `computeHyperbolicLayout()` returns positions that feed directly into Canvas draw calls. No intermediate coordinate type needed.

**Geodesic arc rendering:**
`computeGeodesicArc()` in TypeScript returns `{ cx, cy, r, startAngle, endAngle, ccw }`. The Compose `drawArc()` function takes `(topLeft, size, startAngle, sweepAngle)` — a different convention. Convert:
```kotlin
// Given arc circle center (cx, cy) and radius r:
val topLeft = Offset(cx - r, cy - r)
val size = Size(r * 2, r * 2)
// sweepAngle = endAngle - startAngle, adjusted for ccw winding
val sweepAngle = if (ccw) -(positiveArcLength) else positiveArcLength
drawArc(color, startAngle.toDegrees(), sweepAngle.toDegrees(), useCenter=false, topLeft, size, style=Stroke(...))
```

**Do not add any math/geometry library.** Apache Commons Math, JTS Topology Suite, etc. are heavy JVM libraries with thousands of classes. `kotlin.math` already has every function the Poincare disk computation needs.

**Confidence:** HIGH — `kotlin.math.tanh` confirmed in Kotlin stdlib docs. The algorithm is deterministic math with no platform-specific behavior.

---

### 5. Graph Data Structure — Kotlin Port of `GraphNode` / `GraphData`

**No library needed.** Port `types.ts` directly:

```kotlin
// com/metrolist/music/bridge/graph/GraphNode.kt
enum class NodeState { SEED, ACTIVE, EXPLORED, FRONTIER, CURRENT, LOADING, ERROR }

@Immutable
data class GraphNode(
    val id: String,           // artist name (unique key)
    val name: String,         // display name
    val state: NodeState,
    val match: Float? = null, // similarity 0-1
    val parentId: String? = null,
    val listeners: Long? = null,
    val imageUrl: String? = null,
    val tags: List<String> = emptyList(),
)

@Immutable
data class GraphLink(
    val source: String,
    val target: String,
    val isActivePath: Boolean = false,
)

@Immutable
data class GraphData(
    val nodes: List<GraphNode>,
    val links: List<GraphLink>,
)
```

`@Immutable` on data classes is already the project convention (see `BridgeArtistMetaEntity`, `BridgeSimilarArtistEntity`). Use `persistentListOf()` from `kotlinx.collections.immutable` if recomposition performance becomes a concern — but only add that library if profiling demonstrates a need. Do not add it preemptively.

**Confidence:** HIGH — direct port of verified TypeScript types.

---

### 6. Room DB Schema — Bridge History Persistence

**API:** `androidx.room:room-runtime:2.8.4` (already present)

**New tables needed** (Room DB currently at version 37):

#### Table: `bridge_history`
Stores one row per completed bridge or path walk.

```kotlin
@Entity(tableName = "bridge_history")
data class BridgeHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromArtist: String,
    val toArtist: String?,             // null for path-walk (no destination)
    val pathJson: String,              // JSON-encoded List<String>
    val mode: String,                  // "bridge" | "pathwalk"
    val createdAt: Long = System.currentTimeMillis(),
)
```

#### Table: `bridge_walk_step`
Stores each node expansion step in a path walk session (for replay).

```kotlin
@Entity(tableName = "bridge_walk_step")
data class BridgeWalkStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val historyId: Long,               // FK to bridge_history.id
    val artistName: String,
    val stepIndex: Int,
    val nodeState: String,             // serialized NodeState name
    val matchScore: Float? = null,
    val parentArtist: String? = null,
)
```

**Migration:** Room 2.8.4 supports `@AutoMigration` for new table additions — no `AutoMigrationSpec` needed for a pure `CREATE TABLE`. Add:

```kotlin
// InternalDatabase.kt
@Database(
    version = 38,
    entities = [
        // ...existing entities...
        BridgeHistoryEntity::class,
        BridgeWalkStepEntity::class,
    ],
    autoMigrations = [
        AutoMigration(from = 37, to = 38)
    ],
    exportSchema = true,
)
```

**DAO additions** (in `DatabaseDao.kt`):
```kotlin
@Insert
suspend fun insertBridgeHistory(entity: BridgeHistoryEntity): Long

@Query("SELECT * FROM bridge_history ORDER BY createdAt DESC LIMIT 50")
fun recentBridgeHistory(): Flow<List<BridgeHistoryEntity>>

@Query("DELETE FROM bridge_history WHERE id = :id")
suspend fun deleteBridgeHistory(id: Long)

@Insert
suspend fun insertWalkStep(step: BridgeWalkStepEntity)

@Query("SELECT * FROM bridge_walk_step WHERE historyId = :historyId ORDER BY stepIndex")
suspend fun walkStepsForHistory(historyId: Long): List<BridgeWalkStepEntity>
```

**Why not reuse the existing `BridgeCard` as the persistence model:**
`BridgeCard` is a UI model with `MediaItem` lists that are not serializable to Room. The history tables are thin — store the path as a JSON array, replay by re-running the bridge/walk algorithm with the same inputs rather than storing full MediaItem metadata.

**Confidence:** HIGH — Room 2.8.4 AutoMigration for new tables confirmed in official docs (no AutoMigrationSpec needed for CREATE TABLE). Schema version 37 confirmed from `app/schemas/` directory.

---

## What NOT to Add

| Avoid | Why |
|---|---|
| Any Android graph visualization library | All Compose graph libs are charting libraries (bar/line/pie). None supports hyperbolic layout. Dead weight. |
| `kotlinx.collections.immutable` | Only add if profiling shows recomposition issues with `List<GraphNode>`. Not needed preemptively. |
| Any Apache Commons Math / JTS / geometry library | `kotlin.math` covers every function in `hyperbolicLayout.ts`. Heavy JVM libs with no benefit here. |
| react-force-graph-2d or any web-based graph renderer | The TypeScript `types.ts` references react-force-graph-2d for the web app. The Android port uses Compose Canvas. Do not load a JS graph library in a WebView. |
| `transformable` modifier for the graph | Use `detectTransformGestures` inside `pointerInput` instead — more predictable behavior when stacking with a tap detector. |
| Force simulation (d3-force or equivalent) | The TypeScript `GraphNode` has `x`, `y`, `fx`, `fy` d3 fields, but the Kotlin port uses `computeHyperbolicLayout()` for positioning. Static hyperbolic layout, no physics simulation needed. |
| Separate `eccopath` Gradle module for graph code | Graph code belongs in the existing `app` module, in `ui/screens/bridge/` and `bridge/graph/`. The module split recommendation from the v1.0 STACK.md was for the WebView wrapper, which is now dormant. |

---

## Integration Points With Existing Code

| New Feature | Integrates With | How |
|---|---|---|
| `computeHyperbolicLayout()` Kotlin port | `BridgeViewModel` | ViewModel holds `GraphData`, calls layout function reactively when nodes change |
| `HyperbolicGraphCanvas` composable | `BridgeScreen.kt` | Replaces or co-exists with stacked `BridgeCardView` depending on mode toggle |
| `BridgeHistoryEntity` | `DatabaseDao.kt` | Add new DAO methods to the existing 1747-line DAO interface |
| Node tap → FALA expansion | `BridgeAlgorithm.kt` | `onNodeTap(artistId)` triggers a new `findBridge()` call with tapped artist as one endpoint |
| Node tap → playback | `PlayerConnection` | Same `seekTo()` pattern already in `BridgeScreen.kt` `onArtistClick` handler |
| `BridgeHistoryEntity` | `BridgeViewModel` | ViewModel saves history on bridge completion, exposes `recentBridgeHistory()` Flow |

---

## Installation Summary

**No new Gradle dependencies.** Zero changes to `libs.versions.toml` or `build.gradle.kts`.

**Room schema change only:**
```kotlin
// InternalDatabase.kt — bump version from 37 to 38
@Database(version = 38, autoMigrations = [AutoMigration(from = 37, to = 38)])
```

New Kotlin files to create:
```
app/src/main/kotlin/com/metrolist/music/bridge/graph/
  GraphNode.kt          — data classes (port of types.ts NodeState, GraphNode, GraphLink, GraphData)
  HyperbolicLayout.kt   — computeHyperbolicLayout() + computeGeodesicArc() (port of hyperbolicLayout.ts)

app/src/main/kotlin/com/metrolist/music/db/entities/
  BridgeHistoryEntity.kt
  BridgeWalkStepEntity.kt

app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/
  HyperbolicGraphCanvas.kt  — Compose Canvas composable for graph rendering
```

---

## Sources

- [Compose Canvas DrawScope API](https://developer.android.com/develop/ui/compose/graphics/draw/overview) — verified drawCircle, drawArc, drawPath, drawText signatures
- [Brush.radialGradient docs](https://developer.android.com/develop/ui/compose/graphics/draw/brush) — verified radial gradient parameters
- [Multi-touch gestures (pan, zoom)](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/multi-touch) — verified detectTransformGestures, transformable, updated February 2026
- [Tap and press gestures](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/tap-and-press) — verified detectTapGestures + Offset position
- [Value-based animations](https://developer.android.com/develop/ui/compose/animation/value-based) — animateFloatAsState, Animatable, rememberInfiniteTransition
- [InfiniteTransition API reference](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/InfiniteTransition)
- [Room AutoMigration for new tables](https://developer.android.com/training/data-storage/room/migrating-db-versions) — confirmed new table = no AutoMigrationSpec needed
- [kotlin.math.tanh](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.math/tanh.html) — confirmed in Kotlin stdlib
- [TextMeasurer API reference](https://developer.android.com/reference/kotlin/androidx/compose/ui/text/TextMeasurer) — rememberTextMeasurer + DrawScope.drawText
- EccoPath source: `/home/kepler/Projects/EccoPath/lib/hyperbolicLayout.ts` — reference implementation verified
- EccoPath types: `/home/kepler/Projects/EccoPath/lib/types.ts` — NodeState, GraphNode, GraphLink type definitions
