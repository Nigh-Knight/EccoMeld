# Phase 8: Native Kotlin Bridge Algorithm - Research

**Researched:** 2026-04-05
**Domain:** Kotlin coroutines, Room DB caching, Last.fm API, bidirectional beam search port
**Confidence:** HIGH

---

## Summary

Phase 8 ports the bidirectional beam search algorithm from `eccopath/lib/bridgeCrawl.ts` into native Kotlin coroutines, eliminating the WebView dependency for bridge computation. The TypeScript source is well-documented and directly translatable: the core logic uses `Promise.all` for parallel expansion (maps to `async/await` in coroutineScope), a token-bucket rate limiter (maps to `Semaphore` or a custom `MutableSharedFlow`-based limiter), and two-layer caching (maps to in-memory `ConcurrentHashMap` + Room DB for persistence).

The existing `lastfm` module already has `getArtistInfo` and `searchArtists` but is **missing `getSimilarArtists`** — the most critical API call in the bridge algorithm. This must be added first. Two new Room entities are required: one for similar-artist relationships and one for artist metadata (tags + listener count), replacing IndexedDB. `BridgeViewModel` must be rewired to call the new Kotlin `BridgeAlgorithm` directly instead of delegating to `evaluateJavascript` on the WebView.

**Primary recommendation:** Port the algorithm as a single `BridgeAlgorithm` class injected via Hilt, with `KotlinBridgeCache` as a Room-backed repository. Keep `MeldBridgeInterface` and the WebView intact but dormant — do not delete them, as Phase 9 (bridge UI redesign) may reference them.

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| INFRA-01 | EccoPath bundled as git submodule with static export packaged as APK assets | Already complete — no action. WebView still loads EccoPath for Phase 9. |
| BRDG-02 | Bridge computation runs via beam search when user taps "Bridge" | Port from WebView evaluateJavascript to native Kotlin coroutine in BridgeViewModel.findBridge() |
| BRDG-03 | JS bridge interface (MeldBridge) sends bridge path result from EccoPath to Kotlin | Replaced: BridgeViewModel calls KotlinBridgeAlgorithm directly, emits to _uiState Flow — no JS involved |
</phase_requirements>

---

## Project Constraints (from CLAUDE.md)

- **Platform**: Android only, SDK 26+ target SDK 36
- **Language**: Kotlin 2.3.10, JVM 21
- **DI**: Dagger Hilt 2.59.1 — all injectable classes use `@HiltViewModel`, `@Singleton @Provides`
- **HTTP**: Ktor 3.4.0 with OkHttp engine — already used in `lastfm` module
- **DB**: Room 2.8.4, current version 36, KSP annotation processing
- **Coroutines**: `Dispatchers.IO` for network/DB, `viewModelScope` for ViewModel, explicit dispatcher specification
- **Logging**: Timber only — `Timber.tag("X").d(...)` pattern
- **Error handling**: `Result<T>` with `.onSuccess/.onFailure` chain; `runCatching` wrapping
- **Naming**: Entity files `<Name>Entity.kt`; DAO in `DatabaseDao.kt` (single monolithic DAO); `@Ignore` for JVM-incompatible test stubs
- **No WebView rewrite**: Bridge algorithm replaces the JS path; WebView + `MeldBridgeInterface` kept intact
- **GSD workflow**: All code changes must go through `/gsd:execute-phase`
- **No custom formatter**: Android Lint only; `warningsAsErrors = false`

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Room KTX | 2.8.4 | Persist similar-artist and tag cache in SQLite | Already in project; `room-ktx` gives suspend DAO functions |
| kotlinx-coroutines | 1.10.2 | Parallel beam expansion (`async/await`, `coroutineScope`) | Already in project; `Dispatchers.IO` for network calls |
| Ktor + OkHttp | 3.4.0 | Last.fm HTTP calls in `lastfm` module | Already in use; `getSimilarArtists` follows same pattern as `getArtistInfo` |
| Dagger Hilt | 2.59.1 | Inject `BridgeAlgorithm` into `BridgeViewModel` | Already in project; `@Singleton @Provides` in new `BridgeAlgorithmModule` |
| kotlinx.serialization | (via ktor) | Deserialize `SimilarArtistsResponse` | Already configured; `@Serializable` data classes |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `kotlinx-coroutines-test` | 1.10.2 | `runTest`, `TestDispatcher` for algorithm unit tests | Test-only; already in `testImplementation` |
| `mockito-kotlin` | 5.4.0 | Mock `MusicDatabase`, `LastFM` in unit tests | Already in project test deps |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Custom `Semaphore`-based rate limiter | `kotlinx.coroutines.sync.Semaphore` | `Semaphore` is the idiomatic Kotlin rate-limiting primitive; simpler than a custom token bucket |
| Room for similar-artist cache | DataStore | DataStore is not designed for keyed lookups; Room's indexed queries are required for cache hit checks |

**Installation:** No new dependencies needed. All required libraries already in `gradle/libs.versions.toml`. KSP already configured for Room.

---

## Architecture Patterns

### Recommended Project Structure
```
app/src/main/kotlin/com/metrolist/music/
├── bridge/
│   ├── MeldBridgeInterface.kt       # Kept intact (dormant WebView path)
│   ├── BridgeAlgorithm.kt           # NEW: Kotlin port of bridgeCrawl.ts
│   └── KotlinBridgeCache.kt         # NEW: Room-backed cache repository
├── db/
│   ├── entities/
│   │   ├── BridgeSimilarArtistEntity.kt  # NEW: similar artist edge cache
│   │   └── BridgeArtistMetaEntity.kt     # NEW: tags + listener count cache
│   └── MusicDatabase.kt             # Updated: +version bump, +new entities
├── di/
│   ├── BridgeModule.kt              # Kept intact (provides WebView, MeldBridgeInterface)
│   └── BridgeAlgorithmModule.kt     # NEW: provides BridgeAlgorithm, KotlinBridgeCache
└── viewmodels/
    └── BridgeViewModel.kt           # Updated: findBridge() calls BridgeAlgorithm directly
lastfm/src/main/kotlin/com/metrolist/lastfm/
├── LastFM.kt                        # Updated: +getSimilarArtists()
└── models/
    └── SimilarArtistsResponse.kt    # NEW: response model for artist.getSimilar
```

### Pattern 1: Kotlin Coroutine Port of Parallel Beam Expansion

The TypeScript `Promise.all(beam.map(async (name) => getSimilarArtists(name)))` maps directly to `coroutineScope { beam.map { name -> async(Dispatchers.IO) { LastFM.getSimilarArtists(name) } }.awaitAll() }`.

**What:** Each beam level expands all frontier nodes in parallel using `coroutineScope { ... async ... awaitAll }`, rate-limited by `Semaphore`.
**When to use:** In `BridgeAlgorithm.expandBeam()` — mirrors the TypeScript `expandBeam()` function exactly.

```kotlin
// Source: pattern from existing BridgeViewModel.fetchArtistMetadata (awaitAll pattern)
private suspend fun expandBeam(
    beam: List<String>,
    ownVisited: MutableSet<String>,
    ownParent: MutableMap<String, String>,
    oppositeVisited: Set<String>,
    oppositeTags: List<String>,
): BeamResult = coroutineScope {
    val expansions = beam.map { name ->
        async(Dispatchers.IO) {
            rateLimiter.acquire()
            val similar = cache.getSimilarArtists(name, limitPerNode)
            name to similar
        }
    }.awaitAll()
    // ... scoring logic ...
}
```

### Pattern 2: Semaphore-Based Rate Limiter

The TypeScript `TokenBucket` (4 tokens, 300ms refill) maps to `kotlinx.coroutines.sync.Semaphore` with a coroutine-based refill loop, or more simply a fixed-window approach that holds permits.

**What:** Kotlin `Semaphore(permits = 4)` with background refill every 300ms using a `CoroutineScope` launched in `BridgeAlgorithm` init.
**When to use:** Wrapping every Last.fm API call to stay at or below 5 req/sec.

```kotlin
// Source: idiomatic Kotlin coroutines rate limiting pattern
class LastFmRateLimiter(private val scope: CoroutineScope) {
    private val semaphore = Semaphore(4)

    init {
        scope.launch {
            while (true) {
                delay(300L)
                // release up to 4 permits without exceeding max
                repeat(4) { if (semaphore.availablePermits < 4) semaphore.release() }
            }
        }
    }

    suspend fun acquire() = semaphore.acquire()
}
```

Note: `kotlinx.coroutines.sync.Semaphore` has `availablePermits` and `release()` is idempotent only up to `permits`. A simpler alternative: use a `Channel<Unit>` as a token bucket or rely on sequential `delay`-based throttle in the expansion loop.

### Pattern 3: Two-Entity Room Cache Schema

Two entities replace IndexedDB. Both are keyed by artist name (lowercase, trimmed) for cache hits.

```kotlin
// Source: existing ArtistEntity.kt pattern
@Entity(tableName = "bridge_similar_artists")
data class BridgeSimilarArtistEntity(
    @PrimaryKey val artistName: String,       // lowercase normalized key
    val similarJson: String,                  // JSON array of SimilarArtist
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bridge_artist_meta")
data class BridgeArtistMetaEntity(
    @PrimaryKey val artistName: String,
    val tagsJson: String,                     // JSON array of tag strings
    val listeners: Long,
    val cachedAt: Long = System.currentTimeMillis()
)
```

**Cache key normalization:** `artist.trim().lowercase()` — matches the TypeScript `memCache` key pattern of JSON-stringified params (artist name is the only variable).

**TTL strategy:** Cache entries have no TTL for MVP — stale data is acceptable (same as IndexedDB behaviour in the TypeScript version). A `cachedAt` column is included for future TTL enforcement without a migration.

### Pattern 4: BridgeViewModel Rewiring

`BridgeViewModel.findBridge()` currently calls `startBridge(from, to)` which calls `mainHandler.post { webView.evaluateJavascript(script, null) }`. The Phase 8 change replaces this with a direct coroutine call.

```kotlin
// BEFORE (WebView path)
fun startBridge(startArtist: String, endArtist: String) {
    _uiState.value = BridgeUiState.Searching()
    mainHandler.post { webView.evaluateJavascript("window.startBridge(...)", null) }
}

// AFTER (Kotlin path)
fun startBridge(startArtist: String, endArtist: String) {
    if (isRunning) return
    _uiState.value = BridgeUiState.Searching()
    viewModelScope.launch(Dispatchers.IO) {
        bridgeAlgorithm.findBridge(startArtist, endArtist) { progress ->
            _uiState.value = BridgeUiState.Searching(
                message = progress.message,
                progress = progress.progress,
                foundHops = progress.depth,
                totalHops = progress.maxDepth,
            )
        }.let { result ->
            val newState = if (result.found) BridgeUiState.PathFound(result.path)
                           else BridgeUiState.Error("No bridge path found. Try different artists.")
            withContext(Dispatchers.Main) { _uiState.value = newState }
            if (result.found) {
                buildPlaylist(result.path)
                fetchArtistMetadata(result.path)
                resolveFamiliarity(result.path)
            }
        }
    }
}
```

The `onProgress` callback replaces `MeldBridgeInterface.onProgress` — same data contract, no thread hop needed (progress emitted on IO, but StateFlow assignment is thread-safe).

### Anti-Patterns to Avoid

- **`runBlocking` inside coroutines:** The existing `MusicDatabase.withTransaction` uses `runBlocking` as an implementation detail; do not use it in algorithm code — use `suspend` throughout.
- **Storing `PlayerConnection` or `WebView` in `BridgeAlgorithm`:** Per existing project decisions, pass them as parameters, never store them.
- **Deleting `MeldBridgeInterface` or `BridgeModule`:** Phase 9 (bridge UI redesign) likely still needs the WebView running. Keep both code paths compilable.
- **Using `Handler(Looper.getMainLooper())` in `BridgeAlgorithm`:** Not needed — progress is emitted via callback into `viewModelScope`, which handles threading. This was the source of JVM unit test pain in prior phases.
- **Single shared mutable map without synchronization:** Use `ConcurrentHashMap` for the in-memory meta cache accessed from parallel `async` blocks.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Parallel HTTP requests | Manual thread pool | `coroutineScope { async(IO) { }.awaitAll() }` | Structured concurrency handles cancellation, exceptions propagate correctly |
| Rate limiting | Busy-wait loop | `kotlinx.coroutines.sync.Semaphore` | Suspends cleanly without spinning; integrates with coroutine cancellation |
| JSON serialization for cache | Manual JSON string concat | `kotlinx.serialization.json.Json.encodeToString()` | Already in project; type-safe; handles escaping |
| Cache lookup | Full Room query on every call | Two-level cache: `ConcurrentHashMap` (L1) + Room (L2) | Same pattern as TypeScript L1/L2 cache; avoids DB roundtrip for hot artists |
| Priority queue in Dijkstra fallback | `java.util.PriorityQueue` | `sortedBy` on a mutable list | Path is short (< 20 nodes); sorting a small list is cleaner than a heap in Kotlin |

**Key insight:** The TypeScript algorithm's two-layer cache (memCache + IndexedDB) is the most important performance feature. A cold cache on first run will make 50-80 Last.fm API calls; a warm cache drops this to near zero. Replicating this with `ConcurrentHashMap` + Room is the primary performance win.

---

## Runtime State Inventory

> This phase replaces an in-browser caching mechanism (IndexedDB) with Room DB. The WebView's IndexedDB data is irrelevant to the Kotlin port — it stays in the WebView sandbox and is neither migrated nor deleted.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | WebView IndexedDB (`eccopath-lastfm-cache`) in Android's WebView data directory | None — Kotlin cache is a new Room table; no migration of IndexedDB data |
| Live service config | None | None |
| OS-registered state | None | None |
| Secrets/env vars | `LASTFM_API_KEY` / `LASTFM_SECRET` in `local.properties` and BuildConfig — unchanged | None; `LastFM.initialize()` call in `App.kt` already wires keys |
| Build artifacts | `app/schemas/` Room export — new version (37) schema will be generated by KSP | Run `./gradlew :app:kspDebugKotlin` to regenerate; commit schema JSON |

**Nothing found requiring data migration.** The Room schema bump (36 → 37) adds two new tables with `AutoMigration(from = 36, to = 37)` — no data loss, no manual migration needed.

---

## Common Pitfalls

### Pitfall 1: `Semaphore.release()` Exceeding Permit Count
**What goes wrong:** Calling `semaphore.release()` when `availablePermits == permits` throws `IllegalStateException` in `kotlinx.coroutines.sync.Semaphore`.
**Why it happens:** The TypeScript token bucket clamps tokens at `maxTokens` in `refill()`; Kotlin's Semaphore does not clamp automatically.
**How to avoid:** Check `availablePermits < maxPermits` before releasing, or use a `Channel`-based approach where excess tokens are simply dropped.
**Warning signs:** `IllegalStateException: Semaphore permit count is violated` in logs.

### Pitfall 2: Room Version Bump Without AutoMigration Declaration
**What goes wrong:** Adding new entities without incrementing `version` and declaring `AutoMigration(from = 36, to = 37)` causes `IllegalStateException: Room cannot verify the data integrity` at runtime.
**Why it happens:** Room validates schema fingerprint against the declared version.
**How to avoid:** In `InternalDatabase`, bump `version = 37`, add `AutoMigration(from = 36, to = 37)` to the list, and list both new entities in `entities = [...]`. KSP generates the `37.json` schema file automatically.
**Warning signs:** App crashes on launch with `IllegalStateException` after adding entities.

### Pitfall 3: `coroutineScope` vs `viewModelScope` in Algorithm
**What goes wrong:** Launching algorithm work directly in `viewModelScope.launch(IO)` without a structured `coroutineScope` block means exceptions in one `async` child don't cancel siblings.
**Why it happens:** `async` in `viewModelScope` uses `SupervisorJob` — child failures are silently swallowed if not awaited carefully.
**How to avoid:** Use `coroutineScope { ... async ... awaitAll() }` inside the algorithm for beam expansion. This creates a new structured scope where any child failure cancels all siblings and propagates. Outer `viewModelScope.launch` catches the overall exception.
**Warning signs:** Silent partial results — some beam nodes return empty lists with no error logged.

### Pitfall 4: JSON Serialization of Tag Lists in Room Entities
**What goes wrong:** Storing `List<String>` directly in Room without a `@TypeConverter` causes a compile error.
**Why it happens:** Room doesn't know how to serialize collections to SQLite columns.
**How to avoid:** Store tags as JSON string (`kotlinx.serialization.json.Json.encodeToString(tags)`) and decode on read. Or add a `@TypeConverter` for `List<String>`. The JSON string approach is simpler and already used by the TypeScript version (which stores raw JSON).
**Warning signs:** KSP compile error: `error: Cannot figure out how to save this field into database`.

### Pitfall 5: Main Thread Violation When Emitting Progress
**What goes wrong:** Calling `_uiState.value = BridgeUiState.Searching(...)` from a background coroutine while Compose is observing via `collectAsState()` can trigger `CalledFromWrongThreadException` in some Compose versions.
**Why it happens:** `MutableStateFlow.value = ...` is safe from any thread in Kotlin coroutines, but Compose `collectAsState()` requires the Flow to be collected on the main thread. The Flow itself handles dispatch — but explicit `withContext(Dispatchers.Main)` for final state assignment is safer and matches the existing `MeldBridgeInterface` pattern.
**How to avoid:** Emit progress updates directly to a `MutableSharedFlow<BridgeProgressInfo>` collected in `viewModelScope`, or use `withContext(Dispatchers.Main)` before each `_uiState.value` assignment that comes from a background coroutine.
**Warning signs:** Intermittent `CalledFromWrongThreadException` in Compose rendering.

### Pitfall 6: `getSimilarArtists` API Response Shape
**What goes wrong:** The Last.fm `artist.getSimilar` endpoint returns an empty array differently from `artist.getInfo` — if the artist is unknown, the `similarartists` key may be absent or contain an error object.
**Why it happens:** Last.fm API is inconsistent: some endpoints return `{"error": 6}` at the top level, others return an empty `similarartists.artist` array.
**How to avoid:** Use `ignoreUnknownKeys = true` (already in `LastFM.client`) and default `artist` list to `emptyList()` in `SimilarArtistsResponse`. Add a `runCatching` wrapper in `KotlinBridgeCache.getSimilarArtists` and return empty list on failure.
**Warning signs:** `kotlinx.serialization.json.JsonDecodingException` for unknown artists.

### Pitfall 7: Cache Key Normalization Mismatch
**What goes wrong:** Cache misses for artists where the caller passes `"Radiohead"` but the cache was written with `"radiohead"`.
**Why it happens:** TypeScript normalizes to lowercase in tag scoring (`t.toLowerCase()`) but uses the raw artist name as the cache key.
**How to avoid:** Normalize all cache keys consistently: `artistName.trim().lowercase()` for both reads and writes. Store the original display name in a separate field.
**Warning signs:** High cache miss rates even after warm-up; API call count doesn't drop on repeated searches.

---

## Code Examples

### getSimilarArtists Response Model (new file in `lastfm` module)

```kotlin
// File: lastfm/src/main/kotlin/com/metrolist/lastfm/models/SimilarArtistsResponse.kt
@Serializable
data class SimilarArtistsResponse(
    val similarartists: SimilarArtists = SimilarArtists()
) {
    @Serializable
    data class SimilarArtists(
        val artist: List<SimilarArtist> = emptyList()
    )

    @Serializable
    data class SimilarArtist(
        val name: String = "",
        val match: String = "0.5",   // similarity score as string, 0.0–1.0
        val mbid: String = "",
        val url: String = ""
    )
}
```

### getSimilarArtists Method (addition to `LastFM.kt`)

```kotlin
// Source: mirrors existing getArtistInfo() pattern in LastFM.kt
suspend fun getSimilarArtists(artist: String, limit: Int = 100): Result<SimilarArtistsResponse> {
    if (API_KEY.isEmpty()) return Result.failure(IllegalStateException("LastFM not initialized"))
    return runCatching {
        client.get("https://ws.audioscrobbler.com/2.0/") {
            parameter("method", "artist.getSimilar")
            parameter("artist", artist)
            parameter("limit", limit.toString())
            parameter("api_key", API_KEY)
            parameter("format", "json")
        }.body<SimilarArtistsResponse>()
    }
}
```

### Room Entity for Similar Artists Cache

```kotlin
// File: app/src/main/kotlin/com/metrolist/music/db/entities/BridgeSimilarArtistEntity.kt
@Immutable
@Entity(tableName = "bridge_similar_artists")
data class BridgeSimilarArtistEntity(
    @PrimaryKey val artistKey: String,     // artist.trim().lowercase()
    val similarJson: String,               // JSON-encoded list of {name, match}
    val cachedAt: Long = System.currentTimeMillis()
)
```

### Room Entity for Artist Meta Cache

```kotlin
// File: app/src/main/kotlin/com/metrolist/music/db/entities/BridgeArtistMetaEntity.kt
@Immutable
@Entity(tableName = "bridge_artist_meta")
data class BridgeArtistMetaEntity(
    @PrimaryKey val artistKey: String,     // artist.trim().lowercase()
    val displayName: String,              // original casing for display
    val tagsJson: String,                 // JSON-encoded List<String>
    val listeners: Long,
    val cachedAt: Long = System.currentTimeMillis()
)
```

### BridgeAlgorithm Public Interface

```kotlin
// File: app/src/main/kotlin/com/metrolist/music/bridge/BridgeAlgorithm.kt
data class BridgeProgressInfo(
    val phase: String,          // "analyzing", "searching", "deepening", "pathfinding", "fallback"
    val message: String,
    val progress: Float,        // 0.0–1.0
    val depth: Int = 0,
    val maxDepth: Int = 0,
)

data class BridgeResult(
    val found: Boolean,
    val path: List<String>,
)

@Singleton
class BridgeAlgorithm @Inject constructor(
    private val cache: KotlinBridgeCache,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    suspend fun findBridge(
        startArtist: String,
        endArtist: String,
        onProgress: (BridgeProgressInfo) -> Unit = {},
    ): BridgeResult { /* port of bridgeCrawl.ts findBridge() */ }
}
```

### InternalDatabase Version Bump (in MusicDatabase.kt)

```kotlin
// Bump version from 36 to 37; add two new entities and AutoMigration
@Database(
    entities = [
        // ... existing 19 entities ...
        BridgeSimilarArtistEntity::class,
        BridgeArtistMetaEntity::class,
    ],
    version = 37,
    autoMigrations = [
        // ... existing migrations ...
        AutoMigration(from = 36, to = 37),
    ],
)
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `Promise.all` in TypeScript | `coroutineScope { async(IO) { }.awaitAll() }` | Phase 8 | Structured cancellation; exceptions propagate correctly |
| IndexedDB two-layer cache | `ConcurrentHashMap` (L1) + Room entities (L2) | Phase 8 | Persists across restarts; queryable via DAO |
| `evaluateJavascript` → JS bridge | Direct `BridgeAlgorithm.findBridge()` coroutine | Phase 8 | No WebView cold-start delay; no JS deserialization overhead |
| `MeldBridgeInterface` callbacks | `onProgress` lambda parameter + StateFlow | Phase 8 | No thread-hopping; no `Handler(Looper.getMainLooper())` needed |

**The WebView path is NOT deleted** — it stays compilable so Phase 9 (bridge UI redesign) and the existing `BridgeModule` Hilt provider remain valid. The only behavioral change is that `BridgeViewModel.findBridge()` calls `bridgeAlgorithm.findBridge()` directly, bypassing `evaluateJavascript`.

---

## Open Questions

1. **Semaphore refill strategy**
   - What we know: TypeScript uses 4 tokens refilling every 300ms (≈3.3 req/s); Last.fm free tier allows ~5 req/s
   - What's unclear: Whether a simple `delay(200L)` between each API call in the expansion loop is sufficient, or whether a proper token bucket is needed for parallel expansion
   - Recommendation: Start with `Semaphore(5)` released on a `delay(200L)` background loop (≈5/sec ceiling). If rate limit errors appear (`429` from Last.fm), tighten to `Semaphore(3)` with `delay(300L)`.

2. **DAO placement — extend `DatabaseDao` vs separate DAO**
   - What we know: `DatabaseDao.kt` is already 1747 lines; project convention is a single monolithic DAO
   - What's unclear: Whether adding ~8 cache methods to `DatabaseDao` (acceptable per convention) is cleaner than a new `BridgeCacheDao`
   - Recommendation: Follow project convention — add methods to `DatabaseDao`. The new bridge methods are self-contained and won't conflict.

3. **TypeConverter for `List<String>` in Room entities**
   - What we know: The existing `Converters.kt` only converts `LocalDateTime ↔ Long`; Room does not natively support `List<String>`
   - What's unclear: Whether to add a new `@TypeConverter` for `List<String>` or store as raw JSON strings
   - Recommendation: Store as JSON strings and encode/decode in `KotlinBridgeCache`, not as a TypeConverter — avoids modifying `Converters.kt` and keeps encoding logic co-located with the cache.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Room 2.8.4 | DB caching | Yes (in libs.versions.toml) | 2.8.4 | — |
| kotlinx-coroutines | Beam search | Yes | 1.10.2 | — |
| Ktor + OkHttp | Last.fm HTTP | Yes | 3.4.0 | — |
| KSP | Room code gen | Yes | 2.3.5 | — |
| ADB | Build/deploy | Yes | 1.0.41 | — |
| Java/JDK | Build | Not verified in shell | 21 expected | — |

**Missing dependencies with no fallback:** None.

**Note:** Java was not found in shell PATH but the Gradle wrapper handles JDK resolution via `jvm.toolchain { languageVersion = JavaLanguageVersion.of(21) }` in `build.gradle.kts`. The build should work via `./gradlew`.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + mockito-kotlin 5.4.0 |
| Config file | None — standard Android Gradle test discovery |
| Quick run command | `./gradlew :app:testDebugUnitTest --tests "*.bridge.*"` |
| Full suite command | `./gradlew :app:testDebugUnitTest` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BRDG-02 | `BridgeAlgorithm.findBridge()` returns valid path for known pair | unit | `./gradlew :app:testDebugUnitTest --tests "*BridgeAlgorithmTest*"` | ❌ Wave 0 |
| BRDG-02 | Rate limiter does not exceed 5 req/sec | unit | `./gradlew :app:testDebugUnitTest --tests "*LastFmRateLimiterTest*"` | ❌ Wave 0 |
| BRDG-02 | Cache returns stored similar artists without network call | unit | `./gradlew :app:testDebugUnitTest --tests "*KotlinBridgeCacheTest*"` | ❌ Wave 0 |
| BRDG-03 | `BridgeViewModel.findBridge()` calls `BridgeAlgorithm.findBridge()` not `evaluateJavascript` | unit | `./gradlew :app:testDebugUnitTest --tests "*BridgeViewModelTest*"` | ✅ (extend existing) |
| BRDG-03 | PathFound state emitted when algorithm returns found=true | unit | `./gradlew :app:testDebugUnitTest --tests "*BridgeViewModelTest*"` | ✅ (extend existing) |
| INFRA-01 | Room migration 36→37 does not drop existing data | unit (Room migration test) | manual-only (requires instrumented test) | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :app:testDebugUnitTest --tests "*.bridge.*"`
- **Per wave merge:** `./gradlew :app:testDebugUnitTest`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `app/src/test/kotlin/com/metrolist/music/bridge/BridgeAlgorithmTest.kt` — covers BRDG-02 (mocked cache + LastFM, verifies path shape and rate limiter interactions)
- [ ] `app/src/test/kotlin/com/metrolist/music/bridge/KotlinBridgeCacheTest.kt` — covers BRDG-02 caching (L1 hit skips DB, DB hit skips network)
- [ ] `app/src/test/kotlin/com/metrolist/music/bridge/LastFmRateLimiterTest.kt` — covers BRDG-02 rate limiting (5 calls complete without error in 1 second window)
- [ ] Extend `BridgeViewModelTest.kt` — add `findBridge_calls_algorithm_not_webview` test to cover BRDG-03

---

## Sources

### Primary (HIGH confidence)
- `eccopath/lib/bridgeCrawl.ts` — Full source of the TypeScript algorithm being ported; line-by-line reference
- `eccopath/lib/rateLimiter.ts` — Token bucket implementation to translate
- `eccopath/lib/lastfm.ts` — Two-layer cache pattern (memCache + IndexedDB)
- `lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt` — Existing Last.fm Ktor client; `getSimilarArtists` follows `getArtistInfo` pattern
- `app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt` — Current WebView bridge wiring; existing `async/awaitAll` pattern in `fetchArtistMetadata`
- `app/src/main/kotlin/com/metrolist/music/db/MusicDatabase.kt` — Room migration pattern; current version 36
- `gradle/libs.versions.toml` — Confirmed: `room = "2.8.4"`, `coroutinesGuava = "1.10.2"`, `mockitoKotlin = "5.4.0"`

### Secondary (MEDIUM confidence)
- `kotlinx.coroutines.sync.Semaphore` documentation — permits-based suspension primitive; `release()` behavior verified from Kotlin coroutines source
- Room `AutoMigration` — additive migrations (new tables) documented as requiring no `AutoMigrationSpec` when no column renames/deletions are needed

### Tertiary (LOW confidence)
- Last.fm rate limit community consensus (~5 req/sec) — consistent with TypeScript comment in `rateLimiter.ts` referencing 3.3 req/s as conservative headroom

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already in project; no new dependencies
- Architecture: HIGH — TypeScript source is complete and directly translatable; Kotlin coroutine equivalents are well-established
- Pitfalls: HIGH — identified from direct source reading (Semaphore overflow, Room version bump, JSON TypeConverter)
- Rate limit value: MEDIUM — community-sourced 5 req/sec; TypeScript code uses 3.3 req/sec conservatively

**Research date:** 2026-04-05
**Valid until:** 2026-05-05 (stable API surface; Last.fm API has been unchanged for years)
