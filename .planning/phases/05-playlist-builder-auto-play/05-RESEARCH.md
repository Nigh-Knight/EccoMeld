# Phase 05: Playlist Builder + Auto-Play - Research

**Researched:** 2026-04-04
**Domain:** Last.fm track resolution, InnerTube fuzzy matching, Meld queue integration, Compose dialog
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Use BOTH Last.fm and YT Music. Last.fm `artist.getTopTracks` API provides track names ranked by play count. Top 2 = "popular tracks." Tracks ranked lower (positions 10-50) = "deep cuts" — pick 3-5 randomly from this range.
- **D-02:** For each track name from Last.fm, fuzzy match against YT Music search results (via existing InnerTube `search` API) to get a playable video ID. Match on artist name + track title similarity.
- **D-03:** Genre-transition ordering is preserved: tracks are ordered by bridge artist position (seed → bridge1 → bridge2 → ... → target), not shuffled.
- **D-04:** Fuzzy matching uses the existing InnerTube search: search `"artist name track title"`, take the best `SongItem` result. If no results or confidence is too low, silently skip the track (PLAY-04).
- **D-05:** Match failures are logged via Timber but never shown to the user. The playlist continues with whatever tracks resolved successfully.
- **D-06:** When bridge playlist is ready, show a dialog asking the user: "Replace current queue?" vs "Play next". This respects the user's currently-playing music rather than silently replacing it.
- **D-07:** Playlist is fed into the existing Meld queue system via `ListQueue` (which accepts a pre-built list of `MediaItem`s). This integrates with the existing `MusicService` player, mini player, and expanded player with no custom playback code.
- **D-08:** After queue confirmation, playback starts immediately — no additional "play" button. The UI transitions from `PathFound` to `PlaylistReady` state.

### Claude's Discretion

- Fuzzy match threshold/algorithm details (Levenshtein, Jaccard, or simple contains)
- How many deep cuts per artist (3-5 range — pick based on what's available)
- `ListQueue` vs creating a new `BridgeQueue` class — whichever fits the existing queue pattern better
- Dialog styling (Material 3 AlertDialog or custom)

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope

</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PLAY-01 | Unified playlist auto-plays immediately when bridge is found — no manual "play" action needed | D-08: `PlaylistReady` state triggers auto-play via `PlayerConnection.playQueue()`. D-06 queue dialog is the only user gate. |
| PLAY-02 | Track selection per bridge artist: 2 popular tracks + 3-5 deep cuts, genre-transition order preserved | D-01: Last.fm `artist.getTopTracks` rank 1-2 = popular, rank 10-50 = deep cuts. D-03: artist order from `BridgeResult.path` preserved. |
| PLAY-03 | YT Music fuzzy matching resolves each bridge artist's tracks to playable YT Music video IDs | D-02/D-04: `YouTube.search(query, FILTER_SONG)` + Dice-coefficient scoring reusing `SpotifyMapper.matchScore` pattern. |
| PLAY-04 | YT Music match failures skip silently — unavailable tracks don't break playlist flow | D-04/D-05: `null` return from resolver + `filterNotNull()` in the coroutine batch. Timber.w only. |
| PLAY-05 | Bridge playlist feeds into existing Meld queue and plays via existing player bar | D-07: `ListQueue(items = resolvedItems)` passed to `PlayerConnection.playQueue()`. No new playback code. |

</phase_requirements>

---

## Summary

Phase 5 builds the pipeline from a completed bridge path (a `List<String>` of artist names from `BridgeUiState.PathFound`) to a playing queue in the Meld player. The pipeline has three stages: (1) fetch top tracks per artist from Last.fm using `artist.getTopTracks`, (2) resolve each track name to a YT Music video ID via `YouTube.search()` with fuzzy matching, (3) wrap the resolved `List<MediaItem>` in a `ListQueue` and hand it to `PlayerConnection.playQueue()`.

The Last.fm `artist.getTopTracks` API is unauthenticated (same pattern as `artist.search` already in the codebase) and returns tracks ranked by play count. The InnerTube `YouTube.search(query, FILTER_SONG)` is already used in the app for search screens and requires no new plumbing — the same Dice-coefficient bigram scoring used in `SpotifyMapper` is directly reusable (threshold: 0.35, title 45%, artist 35%, no duration since Last.fm doesn't return duration). Track resolution is inherently slow (5-7 artists × 5-7 tracks = 25-49 searches), so async parallel batching with `async`/`awaitAll` is mandatory, mirroring `SpotifyPlaylistQueue`.

Queue integration is simple: `ListQueue` accepts a pre-built `List<MediaItem>` and `PlayerConnection.playQueue()` handles the rest. The user sees a Material 3 `AlertDialog` with "Replace queue" / "Play next" choices before playback starts. After confirmation, `BridgeUiState` transitions to `PlaylistReady`.

**Primary recommendation:** Add `getArtistTopTracks()` to `LastFM.kt`, build a `BridgePlaylistBuilder` class in `app/playback/` that resolves tracks in parallel using the existing `SpotifyMapper` scoring pattern, then wire the result into `BridgeViewModel` which shows the queue dialog and calls `PlayerConnection.playQueue(ListQueue(...))`.

---

## Standard Stack

### Core (already in project)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Last.fm API (lastfm module) | existing | `artist.getTopTracks` — ranked track names per artist | Unauthenticated GET, already used for `artist.search` |
| InnerTube `YouTube.search()` | existing | Resolve artist+title → YT Music video ID | `FILTER_SONG` returns `SongItem` with `id` field |
| `SpotifyMapper.matchScore()` | existing | Dice-coefficient bigram fuzzy scoring | Title 45%, artist 35%, duration 20% — reuse as-is without duration |
| `ListQueue` | existing | Pre-built `List<MediaItem>` queue | Exactly what `D-07` specified |
| `PlayerConnection.playQueue()` | existing | Start playback from a `Queue` | Delegates to `MusicService.playQueue()` |
| `SongItem.toMediaItem()` | existing | Convert `SongItem` → `MediaItem` | Already in `MediaItemExt.kt` |
| Material 3 `AlertDialog` | existing via compose-material3 | Queue replacement confirmation dialog | D-06 |
| Timber | existing | Logging match failures | D-05 |
| kotlinx.coroutines `async`/`awaitAll` | existing | Parallel track resolution | Matches `SpotifyPlaylistQueue` pattern |

### New additions

| Addition | Location | Purpose |
|----------|----------|---------|
| `LastFM.getArtistTopTracks()` | `lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt` | Fetch ranked track names from Last.fm |
| `ArtistTopTracksResponse` | `lastfm/src/main/kotlin/com/metrolist/lastfm/models/` | Deserialize `artist.getTopTracks` JSON |
| `BridgePlaylistBuilder` | `app/src/main/kotlin/com/metrolist/music/playback/BridgePlaylistBuilder.kt` | Orchestrate multi-artist track resolution |
| `BridgeViewModel.buildPlaylist()` | `BridgeViewModel.kt` | Trigger builder on `PathFound`, show dialog |
| Queue dialog state | `BridgeScreen.kt` | Show `AlertDialog` with replace/play-next choice |

**Installation:** No new dependencies — all required libraries are already in the project.

---

## Architecture Patterns

### Recommended Project Structure (new files only)

```
lastfm/src/main/kotlin/com/metrolist/lastfm/
├── models/
│   └── ArtistTopTracksResponse.kt     # NEW: Last.fm toptracks response model
└── LastFM.kt                          # ADD: getArtistTopTracks()

app/src/main/kotlin/com/metrolist/music/
├── playback/
│   └── BridgePlaylistBuilder.kt       # NEW: resolve path → List<MediaItem>
├── viewmodels/
│   └── BridgeViewModel.kt             # ADD: buildPlaylist(), dialog state
└── ui/screens/bridge/
    └── BridgeScreen.kt                # ADD: AlertDialog + PlaylistReady UI stub
```

### Pattern 1: Last.fm `artist.getTopTracks` — Unauthenticated GET

The API is unauthenticated (same pattern as `searchArtists`). No `api_sig` needed.

**Response shape (confirmed by live API test):**
```json
{
  "toptracks": {
    "track": [
      {
        "name": "Creep",
        "playcount": "59406298",
        "listeners": "4046636",
        "@attr": { "rank": "1" },
        "artist": { "name": "Radiohead" }
      }
    ]
  }
}
```

**Kotlin model:**
```kotlin
// lastfm/src/main/kotlin/com/metrolist/lastfm/models/ArtistTopTracksResponse.kt
@Serializable
data class ArtistTopTracksResponse(
    val toptracks: TopTracks
) {
    @Serializable
    data class TopTracks(
        val track: List<TopTrack> = emptyList()
    )

    @Serializable
    data class TopTrack(
        val name: String,
        val playcount: String = "",
        val listeners: String = "",
        @SerialName("@attr") val attr: TrackAttr = TrackAttr()
    ) {
        @Serializable
        data class TrackAttr(val rank: String = "")
        val rankInt: Int get() = attr.rank.toIntOrNull() ?: 999
    }
}
```

**LastFM.kt addition:**
```kotlin
// Unauthenticated GET — no api_sig (same as searchArtists)
suspend fun getArtistTopTracks(
    artist: String,
    limit: Int = 50
): Result<ArtistTopTracksResponse> {
    if (API_KEY.isEmpty()) return Result.failure(IllegalStateException("LastFM not initialized"))
    return runCatching {
        client.get("https://ws.audioscrobbler.com/2.0/") {
            parameter("method", "artist.getTopTracks")
            parameter("artist", artist)
            parameter("limit", limit.toString())
            parameter("api_key", API_KEY)
            parameter("format", "json")
        }.body<ArtistTopTracksResponse>()
    }
}
```

### Pattern 2: Track Selection — Popular vs Deep Cuts (D-01)

```kotlin
// BridgePlaylistBuilder
fun selectTracks(tracks: List<ArtistTopTracksResponse.TopTrack>): List<String> {
    val popular = tracks.filter { it.rankInt <= 2 }.take(2).map { it.name }
    val deepCutPool = tracks.filter { it.rankInt in 10..50 }
    val deepCuts = deepCutPool.shuffled().take(deepCutPool.size.coerceIn(3, 5)).map { it.name }
    return popular + deepCuts  // order: popular first, deep cuts after (per artist)
}
```

### Pattern 3: Parallel Track Resolution (mirrors SpotifyPlaylistQueue)

```kotlin
// BridgePlaylistBuilder.resolveArtist()
suspend fun resolveArtistTracks(
    artistName: String,
    trackNames: List<String>
): List<MediaItem> = coroutineScope {
    trackNames.map { trackName ->
        async(Dispatchers.IO) {
            resolveOneTrack(artistName, trackName)  // returns MediaItem? — null on failure
        }
    }.awaitAll().filterNotNull()
}

private suspend fun resolveOneTrack(artistName: String, trackName: String): MediaItem? {
    val query = "$artistName $trackName"
    val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
        ?: run {
            Timber.w("BridgePlaylist: search failed for '$query'")
            return null
        }
    val best = result.items
        .filterIsInstance<SongItem>()
        .maxByOrNull { song ->
            SpotifyMapper.matchScore(
                spotifyTitle = trackName,
                spotifyArtist = artistName,
                spotifyDurationMs = 0,  // no duration from Last.fm — neutral 0.5
                candidateTitle = song.title,
                candidateArtist = song.artists.firstOrNull()?.name ?: "",
                candidateDurationSec = null,  // triggers neutral 0.5 branch
            )
        } ?: return null
    val score = SpotifyMapper.matchScore(
        spotifyTitle = trackName, spotifyArtist = artistName,
        spotifyDurationMs = 0,
        candidateTitle = best.title,
        candidateArtist = best.artists.firstOrNull()?.name ?: "",
        candidateDurationSec = null,
    )
    if (score < MIN_MATCH_THRESHOLD) {
        Timber.w("BridgePlaylist: low confidence match for '$trackName' by $artistName (score=$score)")
        return null
    }
    Timber.d("BridgePlaylist: matched '$trackName' by $artistName -> ${best.id} (score=$score)")
    return best.toMediaItem()
}

companion object { private const val MIN_MATCH_THRESHOLD = 0.35 }  // matches SpotifyYouTubeMapper
```

### Pattern 4: Queue Loading via PlayerConnection (D-07)

```kotlin
// BridgeViewModel
fun onConfirmReplaceQueue(playerConnection: PlayerConnection) {
    val items = _pendingPlaylistItems ?: return
    playerConnection.playQueue(ListQueue(
        title = "Bridge: ${_fromArtist} → ${_toArtist}",
        items = items,
        startIndex = 0,
    ))
    _uiState.value = BridgeUiState.PlaylistReady(path = _currentPath, nowPlayingIndex = 0)
}

fun onConfirmPlayNext(playerConnection: PlayerConnection) {
    val items = _pendingPlaylistItems ?: return
    playerConnection.playNext(items)
    _uiState.value = BridgeUiState.PlaylistReady(path = _currentPath, nowPlayingIndex = 0)
}
```

### Pattern 5: ViewModel-to-Composable Dialog Flow

`BridgeViewModel` exposes a `MutableStateFlow<Boolean>` called `showQueueDialog`. `BridgeScreen.kt` observes it and renders a Material 3 `AlertDialog`. The dialog's confirm buttons call ViewModel methods that accept `PlayerConnection` (obtained from `LocalPlayerConnection.current` in the composable) — same pattern as `OnlinePlaylistScreen.kt`.

```kotlin
// BridgeScreen.kt — inside BridgeUiState.PathFound branch
val playerConnection = LocalPlayerConnection.current ?: return@BridgeScreen
val showDialog by viewModel.showQueueDialog.collectAsState()

if (showDialog) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissQueueDialog() },
        title = { Text(stringResource(R.string.bridge_queue_dialog_title)) },
        text = { Text(stringResource(R.string.bridge_queue_dialog_message)) },
        confirmButton = {
            TextButton(onClick = { viewModel.onConfirmReplaceQueue(playerConnection) }) {
                Text(stringResource(R.string.bridge_queue_replace))
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.onConfirmPlayNext(playerConnection) }) {
                Text(stringResource(R.string.bridge_queue_play_next))
            }
        },
    )
}
```

### Pattern 6: BridgeViewModel State Flow on PathFound

When `MeldBridgeInterface.createPlaylist()` delivers `PathFound(path)`, `BridgeViewModel.onStateChange` fires. Phase 5 must intercept this transition and launch `buildPlaylist(path)`:

```kotlin
// BridgeViewModel init block — extend existing onStateChange wiring
meldBridgeInterface.onStateChange = { newState ->
    _uiState.value = newState
    if (newState is BridgeUiState.PathFound) {
        viewModelScope.launch { buildPlaylist(newState.path) }
    }
}

private suspend fun buildPlaylist(path: List<String>) {
    // _uiState stays PathFound during build — no intermediate state shown
    val items = builder.buildForPath(path)  // BridgePlaylistBuilder
    _pendingPlaylistItems = items
    if (items.isNotEmpty()) {
        _showQueueDialog.value = true   // triggers AlertDialog in composable
    }
    // If items is empty (all matches failed), stay on PathFound with no dialog
    // Could emit an error — left to discretion
}
```

### Anti-Patterns to Avoid

- **Blocking the main thread with sequential searches:** Each `YouTube.search()` is a network call. Sequential execution for 49 tracks could take 30-60 seconds. Use `async`/`awaitAll` within each artist batch, then process artists sequentially to avoid overwhelming the API.
- **Creating a new Queue subclass:** `ListQueue` already accepts a pre-built list — no subclass needed. The bridge playlist is fully resolved before playback starts, so lazy pagination is unnecessary.
- **Calling `playerConnection.playQueue()` from ViewModel constructor scope:** Must happen in response to a user action (dialog button tap) on the main thread to avoid race conditions with the player initialization guard in `MusicService.playQueue()`.
- **Storing `PlayerConnection` in ViewModel:** `PlayerConnection` is tied to the Activity lifecycle. Pass it as a parameter to ViewModel functions from the composable, not via injection.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Fuzzy track matching | Custom Levenshtein | `SpotifyMapper.matchScore()` | Already tuned: bigram Dice coefficient + title/artist weighting. Threshold 0.35 matches existing Spotify→YT mapper. |
| MediaItem construction | Custom builder | `SongItem.toMediaItem()` in `MediaItemExt.kt` | Sets correct media ID, URI, cache key, and tag (MediaMetadata) for ExoPlayer's stream resolution. |
| Queue loading | Custom `player.setMediaItems()` | `PlayerConnection.playQueue(ListQueue(...))` | `MusicService.playQueue()` handles shuffle check, explicit filter, HideVideoSongs filter, player initialization guard, and `SilentHandler` error wrapping. Direct ExoPlayer calls skip all of this. |
| Parallel coroutine batching | `Thread` pool | `coroutineScope { trackNames.map { async { } }.awaitAll() }` | Structured concurrency, automatic cancellation on ViewModel scope cancel. |

**Key insight:** The `SpotifyYouTubeMapper` already solves the exact problem (batch resolve track names to YT video IDs with silent skip). `BridgePlaylistBuilder` is ~80 lines because it reuses all existing primitives.

---

## Common Pitfalls

### Pitfall 1: Last.fm Returns Empty Track List for Niche Artists

**What goes wrong:** Very obscure artists (< 10k listeners) may have fewer than 10 tracks in Last.fm's index, making the deep-cut pool (rank 10-50) empty.
**Why it happens:** Last.fm's top-tracks endpoint only returns tracks that have been scrobbled — unknown artists have sparse data.
**How to avoid:** Clamp deep cut count to `deepCutPool.size.coerceIn(0, 5)`. If fewer than 2 popular tracks exist, use all available tracks. If the artist returns zero tracks, skip the artist entirely (don't fail the whole playlist).
**Warning signs:** Empty `toptracks.track` list in API response.

### Pitfall 2: YT Music Search Returns No SongItems for Deep Cuts

**What goes wrong:** Very niche or regional tracks may yield only `VideoItem` or `ArtistItem` results from `YouTube.search()` when `FILTER_SONG` is applied. `filterIsInstance<SongItem>()` returns empty.
**Why it happens:** Some tracks exist on YouTube as music videos (not "official audio" type), which are filtered by `FILTER_SONG`. The `FILTER_VIDEO` filter would find them but would return non-music content too.
**How to avoid:** If `FILTER_SONG` returns zero `SongItem`s, silently skip (D-04/D-05) — do not retry with `FILTER_VIDEO`. The playlist continues with other tracks. This is the specified behavior.
**Warning signs:** High skip rate in Timber logs. Test with a deliberately obscure artist.

### Pitfall 3: Calling `playQueue` Before Player Is Ready

**What goes wrong:** If `MusicService` hasn't finished initializing when the dialog confirm is tapped, `player` is uninitialized and throws.
**Why it happens:** App launch + immediate bridge completion (unlikely but possible in integration tests).
**How to avoid:** `MusicService.playQueue()` already has a guard: if `!playerInitialized.value`, it queues the call via `playerInitialized.first { it }` (verified in `MusicService.kt` lines 1286-1293). No extra guard needed in `BridgeViewModel`. Pass the queue to `PlayerConnection.playQueue()` and let the service handle it.
**Warning signs:** Logcat `"playQueue called before player initialization, queuing request"` — safe, not a crash.

### Pitfall 4: Track Resolution Duration (25-49 network requests)

**What goes wrong:** Sequential resolution takes 30-60 seconds — longer than a bridge search itself. User sees "PathFound" state with no feedback.
**Why it happens:** Each `YouTube.search()` is an HTTP round-trip with no batch API.
**How to avoid:** Two levels of parallelism: (1) within each artist, resolve all their tracks in parallel using `async`/`awaitAll`. (2) Consider showing a resolution progress indicator during `PathFound` state (e.g., "Building playlist…"). Even with parallelism, 5-7 artists × parallel resolution = 5-7 sequential batches × ~2-3s each = 10-21s total. This is acceptable but the UI must not appear frozen.
**Warning signs:** No visible feedback during playlist building. Add a `_isBuilding: MutableStateFlow<Boolean>` state.

### Pitfall 5: `SpotifyMapper.matchScore` Without Duration

**What goes wrong:** Passing `spotifyDurationMs = 0` and `candidateDurationSec = null` to `matchScore` causes the duration component to return the neutral score 0.5 (not 0.0). This is the correct behavior — Last.fm `getTopTracks` does not return durations.
**Why it happens:** The scoring function handles `null` duration: `candidateDurationSec == null` → returns `0.5` (neutral). The effective weight becomes `title × 0.45 + artist × 0.35 + 0.5 × 0.20 = title × 0.45 + artist × 0.35 + 0.10`.
**How to avoid:** Pass `spotifyDurationMs = 0` and `candidateDurationSec = null` — confirmed correct by reading the function. Do NOT pass `-1` for `spotifyDurationMs` (would trigger `spotifyDurationMs > 0 == false` → neutral anyway, but 0 is cleaner).
**Warning signs:** N/A — this is the intended behavior.

### Pitfall 6: Artist Rank Index Off-By-One

**What goes wrong:** Last.fm `@attr.rank` is 1-based. "Top 2" = rank 1 and rank 2. "Deep cuts" (rank 10-50) start at rank 10, not index 9.
**Why it happens:** Developer confuses `@attr.rank` (1-based string) with list index (0-based).
**How to avoid:** Parse `attr.rank.toIntOrNull()` and compare to integers 1-2 for popular, 10-50 for deep cuts. The `rankInt` property on `TopTrack` handles the parse.
**Warning signs:** Popular tracks including rank 3-9 tracks, or deep cuts starting from position 9 in the list.

---

## Code Examples

### Last.fm artist.getTopTracks — Confirmed Response Shape

```
// Source: Live API test 2026-04-04 against ws.audioscrobbler.com
// GET https://ws.audioscrobbler.com/2.0/?method=artist.getTopTracks&artist=radiohead
//   &api_key=...&limit=5&format=json
{
  "toptracks": {
    "track": [
      {
        "name": "Creep",
        "playcount": "59406298",
        "@attr": { "rank": "1" },
        "artist": { "name": "Radiohead" }
      },
      ...
    ]
  }
}
```

### Full BridgePlaylistBuilder Sketch

```kotlin
// app/src/main/kotlin/com/metrolist/music/playback/BridgePlaylistBuilder.kt
class BridgePlaylistBuilder @Inject constructor() {

    suspend fun buildForPath(path: List<String>): List<MediaItem> =
        withContext(Dispatchers.IO) {
            path.flatMap { artistName ->
                val topTracks = LastFM.getArtistTopTracks(artistName, limit = 50)
                    .getOrNull()?.toptracks?.track ?: emptyList()
                val selected = selectTracks(topTracks)
                coroutineScope {
                    selected.map { trackName ->
                        async { resolveOneTrack(artistName, trackName) }
                    }.awaitAll().filterNotNull()
                }
            }
        }

    private fun selectTracks(tracks: List<ArtistTopTracksResponse.TopTrack>): List<String> {
        val popular = tracks.filter { it.rankInt <= 2 }.map { it.name }
        val deepPool = tracks.filter { it.rankInt in 10..50 }.shuffled()
        val deepCount = deepPool.size.coerceIn(0, 5)
        return popular + deepPool.take(deepCount).map { it.name }
    }

    private suspend fun resolveOneTrack(artist: String, title: String): MediaItem? {
        val query = "$artist $title"
        val songs = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
            .getOrNull()?.items?.filterIsInstance<SongItem>() ?: return null
        val best = songs.maxByOrNull { song ->
            SpotifyMapper.matchScore(title, artist, 0, song.title,
                song.artists.firstOrNull()?.name ?: "", null)
        } ?: return null
        val score = SpotifyMapper.matchScore(title, artist, 0, best.title,
            best.artists.firstOrNull()?.name ?: "", null)
        if (score < MIN_MATCH_THRESHOLD) {
            Timber.w("BridgePlaylist: skip '$title' by $artist (score=$score)")
            return null
        }
        return best.toMediaItem()
    }

    companion object { private const val MIN_MATCH_THRESHOLD = 0.35 }
}
```

### ListQueue Integration

```kotlin
// From PlayerConnection.kt — already exists, no changes needed:
fun playQueue(queue: Queue) { service.playQueue(queue) }

// BridgeViewModel usage:
playerConnection.playQueue(
    ListQueue(
        title = "Bridge: $fromArtist → $toArtist",
        items = resolvedItems,
        startIndex = 0,
    )
)
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Direct `player.setMediaItems()` | `PlayerConnection.playQueue(Queue)` | Phase 1 (Meld upstream) | All playback must go through the queue abstraction for shuffle, explicit filter, and service initialization guards |
| Custom fuzzy matching | `SpotifyMapper.matchScore()` Dice coefficient | Phase 4 Spotify work | Reuse the same algorithm — no new matching code |

---

## Open Questions

1. **What happens if the entire playlist resolves to zero tracks?**
   - What we know: All matches could fail for a completely niche artist (all tracks too obscure for YT Music).
   - What's unclear: Should the UI show an error, or silently stay on `PathFound` (bridge succeeded, playlist just couldn't be built)?
   - Recommendation: Show a short snackbar "Couldn't find playable tracks" and stay on `PathFound`. Leave exact copy to Claude's discretion.

2. **Progress UI during playlist building (10-21s delay)**
   - What we know: Parallel resolution per artist reduces time to ~3-4s per artist × 5-7 artists = 15-28s worst case.
   - What's unclear: Whether to add a spinner or just let `PathFound` state sit quietly.
   - Recommendation: Add `_isBuilding: MutableStateFlow<Boolean>` in `BridgeViewModel`, show a `CircularProgressIndicator` or "Building playlist…" text in the `PathFound` branch of `BridgeScreen`. Very small implementation cost, prevents the UI appearing frozen.

3. **`PlaylistReady` state usage in this phase vs Phase 6**
   - What we know: `BridgeUiState.PlaylistReady(path, nowPlayingIndex)` is already defined in `BridgeScreen.kt`. Phase 6 will use `nowPlayingIndex` for highlighting.
   - What's unclear: Phase 5 only needs to transition to `PlaylistReady` — the full path-view rendering is Phase 6.
   - Recommendation: Transition to `PlaylistReady(path, nowPlayingIndex = 0)` after dialog confirm. The `PlaylistReady` branch in `BridgeScreen` currently shows a placeholder stub — leave it as stub for now. Phase 6 replaces the stub with the path view.

---

## Environment Availability

Step 2.6: SKIPPED (no external tools or CLIs beyond the project's own modules — all dependencies are Kotlin libraries already in the Gradle build graph).

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 |
| Config file | none — standard Android test runner config in `app/build.gradle.kts` |
| Quick run command | `./gradlew :app:testFossDebugUnitTest --tests "com.metrolist.music.bridge.*" -x lint` |
| Full suite command | `./gradlew :app:testFossDebugUnitTest -x lint` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| PLAY-01 | Auto-play triggers after dialog confirm (no extra button) | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgePlaylistBuilderTest" -x lint` | ❌ Wave 0 |
| PLAY-02 | selectTracks: rank 1-2 = popular, rank 10-50 = deep cuts, order preserved | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgePlaylistBuilderTest.selectTracks_*" -x lint` | ❌ Wave 0 |
| PLAY-03 | resolveOneTrack returns MediaItem on high-confidence match | unit (mock YouTube) | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgePlaylistBuilderTest.resolveOneTrack_*" -x lint` | ❌ Wave 0 |
| PLAY-04 | resolveOneTrack returns null on low confidence, list continues | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgePlaylistBuilderTest.resolveOneTrack_lowScore_returnsNull" -x lint` | ❌ Wave 0 |
| PLAY-05 | buildForPath produces MediaItems in bridge-artist order | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgePlaylistBuilderTest.buildForPath_preservesArtistOrder" -x lint` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew :app:testFossDebugUnitTest --tests "com.metrolist.music.bridge.*" -x lint`
- **Per wave merge:** `./gradlew :app:testFossDebugUnitTest -x lint`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `app/src/test/kotlin/com/metrolist/music/bridge/BridgePlaylistBuilderTest.kt` — covers PLAY-01 through PLAY-05
- [ ] `lastfm/src/test/kotlin/com/metrolist/lastfm/LastFMTopTracksTest.kt` — covers `getArtistTopTracks` response parsing (if lastfm module has test source set)

---

## Sources

### Primary (HIGH confidence)

- Live Last.fm API test (2026-04-04) — confirmed `artist.getTopTracks` JSON shape, field names, rank numbering
- `app/src/main/kotlin/com/metrolist/music/playback/SpotifyYouTubeMapper.kt` — fuzzy matching algorithm, `MIN_MATCH_THRESHOLD = 0.35`, `buildMediaMetadata` pattern
- `spotify/src/main/kotlin/com/metrolist/spotify/SpotifyMapper.kt` — `matchScore()` signature, Dice-coefficient scoring weights (title 45%, artist 35%, duration 20%)
- `app/src/main/kotlin/com/metrolist/music/playback/queues/ListQueue.kt` — confirmed `List<MediaItem>` constructor pattern
- `app/src/main/kotlin/com/metrolist/music/playback/queues/SpotifyPlaylistQueue.kt` — confirmed `async`/`awaitAll`/`filterNotNull()` batch resolution pattern
- `app/src/main/kotlin/com/metrolist/music/playback/PlayerConnection.kt` — confirmed `playQueue(Queue)` and `playNext(List<MediaItem>)` signatures
- `app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt` (lines 1279-1393) — confirmed `playQueue()` handles init guard and `playWhenReady`
- `innertube/src/main/kotlin/com/metrolist/innertube/YouTube.kt` (lines 237-249, 1811-1819) — confirmed `search(query, SearchFilter)` signature and `FILTER_SONG` constant
- `innertube/src/main/kotlin/com/metrolist/innertube/models/YTItem.kt` — confirmed `SongItem.id`, `SongItem.title`, `SongItem.artists` fields
- `app/src/main/kotlin/com/metrolist/music/extensions/MediaItemExt.kt` — confirmed `SongItem.toMediaItem()` extension
- `app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/BridgeScreen.kt` — confirmed `BridgeUiState.PathFound` and `PlaylistReady` sealed classes; confirmed `LocalPlayerConnection` access pattern
- `lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt` — confirmed `searchArtists` unauthenticated GET pattern to replicate for `getArtistTopTracks`

### Secondary (MEDIUM confidence)

- `app/src/main/kotlin/com/metrolist/music/bridge/MeldBridgeInterface.kt` — bridge state wiring; `PathFound(path)` delivery confirmed
- `app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt` — existing ViewModel structure; `onStateChange` wiring pattern

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all reused from existing verified code
- Architecture: HIGH — all integration points read directly from source
- Pitfalls: HIGH — confirmed against actual API responses and source code
- Track selection algorithm: HIGH — confirmed Last.fm API structure from live test
- Fuzzy matching: HIGH — `SpotifyMapper.matchScore` read directly, threshold confirmed

**Research date:** 2026-04-04
**Valid until:** 2026-05-04 (Last.fm API response structure is stable; InnerTube may change sooner but `FILTER_SONG` constant is long-lived)
