# Phase 7: Spotify Integration — Research

**Researched:** 2026-04-04
**Domain:** Android Jetpack Compose, Kotlin coroutines, Spotify API (existing module), Room DB, Last.fm tag fetching, Material 3 FAB/Chip
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Horizontal scrollable row of artist name chips below the From/To search inputs. Tapping a chip fills the corresponding input (From or To, whichever is empty/focused).
- **D-02:** Sources are BOTH Spotify liked artists AND YT Music library artists, mixed together in a single row. Not separated into two rows.
- **D-03:** YT Music library artists come from the existing Room database (`ArtistEntity` table). Spotify liked artists come from the existing `Spotify` module.
- **D-04:** Chips are deduplicated by artist name (case-insensitive). If an artist exists in both sources, show once.
- **D-05:** Floating Action Button (FAB) on the Bridge screen. Always visible — not gated behind Spotify auth.
- **D-06:** Random Bridge works with YT Music library artists even without Spotify connected. If neither source has enough artists (minimum 2), the FAB shows a toast: "Add some artists to your library first."
- **D-07:** Genre-opposite selection uses Tag Jaccard distance: fetch Last.fm tags for candidate artists, pick the pair with the lowest Jaccard similarity (most genre-diverse).
- **D-08:** Tapping FAB immediately starts a bridge with the two selected artists — fills the inputs and triggers `startBridge()`.
- **D-09:** Bridge artists in the path view (Phase 6 bottom sheet) show badges: "NEW" for artists not in user's listening history, "known" for familiar ones.
- **D-10:** "Known" = artist exists in the Room database `ArtistEntity` table OR in Spotify liked artists. "NEW" = not found in either source.
- **D-11:** Badge styling is Claude's discretion — whatever fits the Material 3 theme best.
- **D-12:** If Spotify is not connected, seed suggestions show only YT Music library artists. If YT Music library is also empty, the suggestions row is hidden entirely.
- **D-13:** If Spotify auth fails at runtime, catch the error silently and fall back to YT Music library artists only. Never show Spotify auth errors on the Bridge tab.

### Claude's Discretion
- Badge visual design (pill, icon, chip style — whatever fits Material 3)
- Chip row height and spacing
- How many seed suggestions to show (all vs. capped at N)
- Tag Jaccard implementation details
- FAB icon design
- How to handle the case where the random pair includes an artist the user just bridged

### Deferred Ideas (OUT OF SCOPE)
- Expanded seed suggestions with genre grouping — future enhancement
- "Bridge from your mood" feature — P2
- Smart random that avoids recently-bridged pairs — future enhancement
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SPOT-01 | User's Spotify liked artists appear as quick-pick seed suggestions below From/To inputs | `Spotify.myArtists()` returns `SpotifyPaging<SpotifyArtist>` with name field; `DatabaseDao.artistsByCreateDateAsc()` / `allArtistsByPlayTime()` return `Artist` (wraps `ArtistEntity`) with name field; combine + deduplicate in ViewModel, render with `LazyRow` + `SuggestionChip` |
| SPOT-02 | Random Bridge button picks two genre-opposite artists from Spotify liked songs via Tag Jaccard distance | `LastFM.getArtistInfo()` already exists and returns tags (`ArtistInfoResponse.Tags`); Jaccard distance computable inline; FAB exists as `FloatingActionButton` pattern in `HideOnScrollFAB.kt`; `R.drawable.shuffle` already in resources |
| SPOT-03 | Bridge artists in path view show "known" or "NEW" badge based on user's Spotify listening history | `PathNodeRow` in `PathSheet.kt` is the insertion point; badge resolves via same dual-source lookup as seed suggestions; `ArtistFamiliarity` enum + `Map<String, ArtistFamiliarity>` emitted from ViewModel |
</phase_requirements>

---

## Summary

Phase 7 adds three personalization layers to the existing Bridge tab: seed suggestion chips (horizontal row), a Random Bridge FAB, and known/NEW familiarity badges on path view artists. All three features share a common data-loading foundation — a combined artist pool from two sources: (1) `Spotify.myArtists()` via the existing Spotify singleton, and (2) `DatabaseDao.artistsByCreateDateAsc()` or equivalent Room query for YT Music library artists. Both sources already exist and are tested; this phase wires them together in `BridgeViewModel` and surfaces the results in `BridgeScreen`/`PathSheet`.

The most design-sensitive piece is the Random Bridge FAB, which requires Tag Jaccard computation over Last.fm tags. `LastFM.getArtistInfo()` is already implemented (returns `ArtistInfoResponse` with tag list). Jaccard distance is straightforward set math — no external library needed. The challenge is latency: fetching tags for a pool of artists (up to ~20) in parallel before the user taps FAB means eager pre-fetching is required, with results cached in ViewModel memory.

The current `BridgeScreen` uses `BoxWithConstraints` (not `Scaffold`) because of the `BottomSheet` overlay pattern. The FAB cannot go in a `Scaffold` `floatingActionButton` slot without restructuring the layout. The existing `HideOnScrollFAB.kt` pattern shows the project's approach: FABs inside `BoxScope` with `Alignment.BottomEnd`. The correct approach is to add the FAB as a `Box` child with `Alignment.BottomEnd` + `windowInsetsPadding` — consistent with existing project patterns — rather than introducing `Scaffold`.

**Primary recommendation:** Load artist pool eagerly on Bridge tab open, cache tag metadata in ViewModel after first fetch, use `Box` overlay for FAB placement (not `Scaffold`), deduplicate by lowercase name in ViewModel, resolve familiarity map alongside existing `artistMetadata` map.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `Spotify` (object) | existing | Fetch liked artists via `myArtists()` | Already in module, authenticated via `SpotifyTokenManager` |
| `MusicDatabase` / `DatabaseDao` | existing Room 2.8.4 | Query `ArtistEntity` for YT Music library artists | Existing DAO queries for all library artists |
| `LastFM` (object) | existing | `getArtistInfo()` for tag fetching (Jaccard) | Already implemented, returns `ArtistInfoResponse` with tags |
| `SpotifyTokenManager` | existing | Auth-state check via `isAuthenticated()` / `ensureAuthenticated()` | Central auth guard; handles refresh + re-login signals |
| Jetpack Compose Material 3 1.5.0-alpha09 | existing | `LazyRow`, `SuggestionChip`, `FloatingActionButton`, `AnimatedVisibility` | Design system already in project |
| compose-shimmer 1.3.3 | existing | `ShimmerHost` for loading placeholder chips | Already used throughout the app |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `kotlinx.coroutines` | existing (via viewModelScope) | Parallel tag fetch, IO dispatch | All async ViewModel logic |
| `R.drawable.shuffle` | existing drawable | FAB icon | Already in `app/src/main/res/drawable/shuffle.xml` — no new asset needed |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `Box` overlay for FAB | `Scaffold` `floatingActionButton` slot | Scaffold requires restructuring the entire `BoxWithConstraints` + `BottomSheet` layout; `Box` overlay matches existing `HideOnScrollFAB.kt` pattern |
| Eager tag pre-fetch on tab open | Lazy fetch on FAB tap | Lazy fetch causes noticeable delay on tap; eager fetch with cache makes FAB feel instant after first load |
| Jaccard in ViewModel | Jaccard via JS in WebView | ViewModel Kotlin is simpler, already has tag data from `getArtistInfo()` which runs for metadata anyway |

**Installation:** No new dependencies. All required libraries are already in `gradle/libs.versions.toml`.

---

## Architecture Patterns

### Recommended Project Structure

Additions in this phase:

```
app/src/main/kotlin/com/metrolist/music/
├── viewmodels/
│   └── BridgeViewModel.kt          # Add: loadSeedSuggestions(), randomBridge(), familiarity map
├── ui/screens/bridge/
│   ├── BridgeScreen.kt             # Add: SeedSuggestionsRow, RandomBridgeFab composables
│   └── PathSheet.kt                # Add: ArtistFamiliarityBadge to PathNodeRow
app/src/main/res/values/
│   └── metrolist_strings.xml       # Add: 5 new bridge_* string resources
```

No new files needed except the string additions. All changes are additive to existing files.

### Pattern 1: Combined Artist Pool Loading

**What:** Eagerly load both sources in parallel on Bridge tab open. Merge, deduplicate by lowercase name, cap at 20.
**When to use:** `LaunchedEffect(Unit)` in `BridgeScreen` calls `viewModel.loadSeedSuggestions()`
**Example:**
```kotlin
// In BridgeViewModel — parallel load from both sources
fun loadSeedSuggestions() {
    if (_seedSuggestionsLoaded) return  // idempotent
    viewModelScope.launch(Dispatchers.IO) {
        _isLoadingSeeds.value = true
        val ytArtists = database.artistsByCreateDateAsc().first()
            .map { it.artist.name }

        val spotifyArtists = if (SpotifyTokenManager.ensureAuthenticated()) {
            Spotify.myArtists(limit = 50).getOrNull()?.items?.map { it.name } ?: emptyList()
        } else {
            emptyList()  // D-12, D-13: silent fallback
        }

        val merged = (ytArtists + spotifyArtists)
            .distinctBy { it.lowercase() }
            .take(20)
        _seedSuggestions.value = merged
        _isLoadingSeeds.value = false
        _seedSuggestionsLoaded = true

        // Pre-fetch tags for Jaccard while we have the artist list
        prefetchTagsForJaccard(merged)
    }
}
```

### Pattern 2: Tag Jaccard Distance

**What:** Fetch Last.fm tags for each artist, compute pairwise Jaccard similarity, return the pair with minimum similarity (most genre-diverse).
**When to use:** Called from `randomBridge()` in ViewModel; uses cached `_artistTagCache`
**Example:**
```kotlin
// Jaccard similarity: |A ∩ B| / |A ∪ B| — lower = more genre-diverse
private fun jaccardSimilarity(tagsA: Set<String>, tagsB: Set<String>): Double {
    if (tagsA.isEmpty() && tagsB.isEmpty()) return 1.0
    val intersection = tagsA.intersect(tagsB).size
    val union = tagsA.union(tagsB).size
    return if (union == 0) 1.0 else intersection.toDouble() / union
}

// Pick the pair with minimum Jaccard similarity (most diverse)
private fun pickMostDiversePair(tagMap: Map<String, Set<String>>): Pair<String, String>? {
    val artists = tagMap.keys.toList()
    if (artists.size < 2) return null
    var bestPair: Pair<String, String>? = null
    var minSimilarity = Double.MAX_VALUE
    for (i in 0 until artists.size - 1) {
        for (j in i + 1 until artists.size) {
            val sim = jaccardSimilarity(tagMap[artists[i]]!!, tagMap[artists[j]]!!)
            if (sim < minSimilarity) {
                minSimilarity = sim
                bestPair = artists[i] to artists[j]
            }
        }
    }
    return bestPair
}
```

Note: With 20 artists, the O(n²) pairwise comparison is 190 iterations — negligible cost.

### Pattern 3: Familiarity Map Resolution

**What:** After a bridge path is found, resolve each path artist against both YT Music library (Room) and Spotify liked artists. Emit as `Map<String, ArtistFamiliarity>`.
**When to use:** Triggered alongside existing `fetchArtistMetadata()` in ViewModel `init` on `PathFound`.
**Example:**
```kotlin
enum class ArtistFamiliarity { KNOWN, NEW }

private suspend fun resolveFamiliarity(path: List<String>) {
    val ytNames = database.artistsByCreateDateAsc().first()
        .map { it.artist.name.lowercase() }.toSet()
    val spotifyNames = if (SpotifyTokenManager.ensureAuthenticated()) {
        Spotify.myArtists(limit = 50).getOrNull()?.items
            ?.map { it.name.lowercase() }?.toSet() ?: emptySet()
    } else emptySet()

    val knownNames = ytNames + spotifyNames
    val result = path.associateWith { artistName ->
        if (artistName.lowercase() in knownNames) ArtistFamiliarity.KNOWN
        else ArtistFamiliarity.NEW
    }
    _artistFamiliarity.value = result
}
```

### Pattern 4: FAB in BoxWithConstraints (not Scaffold)

**What:** FAB placed as `Box` child with `Alignment.BottomEnd` — matches existing `HideOnScrollFAB.kt` approach.
**When to use:** `BridgeScreen` uses `BoxWithConstraints`, not `Scaffold`. Adding `Scaffold` would break the `BottomSheet` overlay.
**Example:**
```kotlin
// Inside BoxWithConstraints — add FAB as sibling to Column and BottomSheet
Box(
    modifier = Modifier
        .align(Alignment.BottomEnd)
        .windowInsetsPadding(
            LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
        )
        .padding(16.dp),
) {
    RandomBridgeFab(
        isLoading = isFabLoading,
        isEnabled = !isRunning,
        onClick = { viewModel.randomBridge(context) },
        contentDescription = stringResource(R.string.bridge_random_fab_description),
    )
}
```

### Pattern 5: Shimmer Placeholder Chips

**What:** While seeds are loading, show 3 placeholder chips using `ShimmerHost` + manual `Box` (not `SuggestionChip` — same lesson as Phase 6 `GenreTagChip`).
**When to use:** `isLoadingSeeds == true` branch in `AnimatedVisibility`
**Example:**
```kotlin
// In BridgeScreen seed row — shimmer placeholder pattern
ShimmerHost(showGradient = false) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics {
            contentDescription = stringResource(R.string.bridge_seeds_loading_description)
        }
    ) {
        items(3) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(32.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(16.dp)
                    )
                    .semantics { invisibleToUser() }
            )
        }
    }
}
```

### Pattern 6: Known/NEW Badge in PathNodeRow

**What:** Small pill badge appended in the artist name row, after the artist name `Text`. Non-interactive.
**When to use:** `familiarity != null` — no badge shown until resolved (no layout shift).
**Example:**
```kotlin
// In PathNodeRow — add badge to name+icon Row
val familiarity = familiarityMap[artistName]  // null = not yet resolved
if (familiarity != null) {
    Box(
        modifier = Modifier
            .height(16.dp)
            .background(
                if (familiarity == ArtistFamiliarity.NEW)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp)
            .semantics {
                contentDescription = if (familiarity == ArtistFamiliarity.NEW)
                    "New artist" else "Familiar artist"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                if (familiarity == ArtistFamiliarity.NEW) R.string.bridge_badge_new
                else R.string.bridge_badge_known
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (familiarity == ArtistFamiliarity.NEW)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSecondary,
        )
    }
}
```

### Anti-Patterns to Avoid

- **Scaffold for FAB:** `BridgeScreen` uses `BoxWithConstraints` + custom `BottomSheet`. Wrapping in `Scaffold` would require restructuring both and risks breaking bottom sheet behavior.
- **`SuggestionChip` with fixed height:** Phase 6 established that `SuggestionChip` enforces minimum height that makes chips visually oversized. Use plain `Box` + `background` + `padding` pattern (like `GenreTagChip`) OR verify `SuggestionChip` renders acceptably at 32dp in the `LazyRow` context before committing.
- **Blocking Jaccard on FAB tap:** Pre-fetch tags eagerly on tab open so FAB response is instantaneous after first load.
- **Fetching Spotify liked artists twice per session:** Cache the seed artist list and familiarity data in ViewModel memory — do not re-fetch on each path found.
- **Checking artist familiarity by ID:** Artist IDs differ between Spotify (`spotify:artist:...`) and Room (YouTube channel ID `UC...`). Match by normalized name (lowercase, trimmed) only.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Auth-guarded Spotify calls | Custom token check | `SpotifyTokenManager.ensureAuthenticated()` | Handles expiry, mutex, refresh, re-login signal atomically |
| YT Music artist list | Custom DAO query | `DatabaseDao.artistsByCreateDateAsc()` or `allArtistsByPlayTime()` | Already implemented, returns `Artist` with `artist.name` |
| Liked Spotify artist fetch | Custom GraphQL | `Spotify.myArtists(limit, offset)` | Already implemented, returns `SpotifyPaging<SpotifyArtist>` |
| Tag fetching for Jaccard | New Last.fm client method | `LastFM.getArtistInfo(artist)` | Already returns `ArtistInfoResponse` with `artist.tags.tag` list |
| Shimmer loading state | Custom animation | `ShimmerHost` from `ui/component/shimmer/ShimmerHost.kt` | Project's established shimmer pattern, already in use |
| FAB pattern | Custom floating button | `FloatingActionButton` from Material 3 | Already used in `CrashActivity`, `HideOnScrollFAB` |
| Shuffle icon | New drawable asset | `R.drawable.shuffle` | Already exists in `app/src/main/res/drawable/shuffle.xml` |

**Key insight:** The entire data layer for this phase is already built. `Spotify.myArtists()`, `LastFM.getArtistInfo()`, `DatabaseDao.artistsByCreateDateAsc()`, and `SpotifyTokenManager.ensureAuthenticated()` are all production-ready singletons. This phase is entirely ViewModel orchestration + UI composition.

---

## Common Pitfalls

### Pitfall 1: FAB Placement vs. BottomSheet Interaction
**What goes wrong:** FAB appears above the bottom sheet when sheet is expanded, or gets clipped by sheet overlay.
**Why it happens:** `BoxWithConstraints` renders all children in Z order; FAB must sit above the BottomSheet in stacking order to be interactive.
**How to avoid:** Add FAB as the last child in `BoxWithConstraints` (above both Column and BottomSheet in composition order). Apply `windowInsetsPadding` to account for player mini-bar.
**Warning signs:** FAB tap not registering when bottom sheet is collapsed.

### Pitfall 2: Artist Name Matching Across Sources
**What goes wrong:** "Radiohead" from Room and "Radiohead" from Spotify are treated as duplicates correctly, but "The National" from Room and "the national" from Spotify cause a duplicate chip to appear.
**Why it happens:** Case-sensitive `distinct()` or `Set` membership.
**How to avoid:** Always normalize: `name.trim().lowercase()` before deduplication. Use `distinctBy { it.trim().lowercase() }` for the chip list and `Set<String>` of lowercase names for familiarity lookup.

### Pitfall 3: `ensureAuthenticated()` Called on Main Thread
**What goes wrong:** `SpotifyTokenManager.ensureAuthenticated()` is `suspend` and does I/O (reads DataStore, may call network). Calling it from a composable or on the main thread causes ANR.
**Why it happens:** Forgetting the `Dispatchers.IO` context in ViewModel.
**How to avoid:** Always wrap Spotify calls in `viewModelScope.launch(Dispatchers.IO)`.

### Pitfall 4: `Spotify.myArtists()` Returns Only First 50
**What goes wrong:** User has 200 liked artists but only 50 appear as seed suggestions, missing genre diversity for Jaccard.
**Why it happens:** `myArtists(limit = 50, offset = 0)` — default single page.
**How to avoid:** For the seed suggestion row, 50 artists capped at 20 chips is fine. For Jaccard tag computation, only a sample of artists needs tags — fetching tags for all 50 in parallel is sufficient without pagination. If deeper coverage is wanted later, paginate via `SpotifyPaging.offset + limit`.

### Pitfall 5: Jaccard with Empty Tag Sets
**What goes wrong:** Artists with no Last.fm tags (new/obscure artists) produce `0/0 = NaN` or `infinity` in Jaccard.
**Why it happens:** Division by zero in `|A ∪ B| = 0` case.
**How to avoid:** Guard: `if (union == 0) return 1.0` (treat no-tags as identical/similar, not diverse). Filter out artists with empty tag sets from the candidate pool for Jaccard before comparison, or prefer artists with at least 1 tag.

### Pitfall 6: Tag Pre-fetch Latency Spikes
**What goes wrong:** FAB tap triggers 20 parallel Last.fm API calls, UI feels unresponsive, FAB spinner shows for 3+ seconds.
**Why it happens:** Eager pre-fetch not completed before user taps FAB; or cache not consulted before fetching.
**How to avoid:** Start tag pre-fetch immediately after seed list is loaded (`loadSeedSuggestions()` tail). Cache results in `_artistTagCache: MutableMap<String, Set<String>>`. On FAB tap, check cache first — only fetch missing artists. Log `Timber.d` when cache is used vs. fetched.

### Pitfall 7: Badge Layout Shift in PathNodeRow
**What goes wrong:** Path view snaps/jumps when familiarity badges appear after async resolution completes.
**Why it happens:** Badge changes the height of the name row from 0 to 16dp.
**How to avoid:** UI-SPEC decision: show no badge placeholder until resolution completes (no layout shift). Familiarity resolves in parallel with `fetchArtistMetadata()` — they finish at similar times. Acceptable UX since badges appear quickly after path is shown.

### Pitfall 8: `SuggestionChip` Minimum Height
**What goes wrong:** `SuggestionChip` in `LazyRow` is taller than expected (exceeds 32dp touch target).
**Why it happens:** Material 3 `SuggestionChip` enforces a minimum height of 32dp — may look fine — but has internal padding that can conflict with compact layouts. Phase 6 used `Box` for `GenreTagChip` to avoid this.
**How to avoid:** Test `SuggestionChip` in the actual layout before committing. If visually acceptable at 32dp in the horizontal chip row context, use it (it provides better a11y than a raw `Box`). If oversized, fall back to `Box` pattern as in `GenreTagChip`. The UI-SPEC explicitly uses `SuggestionChip` for this row, so try it first.

### Pitfall 9: `Spotify.isAuthenticated()` vs. `SpotifyTokenManager.ensureAuthenticated()`
**What goes wrong:** Calling `Spotify.isAuthenticated()` returns `true` because an access token is stored, but the token is expired. Spotify API calls then fail with 401.
**Why it happens:** `Spotify.isAuthenticated()` is a simple null check on `Spotify.accessToken` — it does not validate expiry.
**How to avoid:** Always use `SpotifyTokenManager.ensureAuthenticated()` (suspend, handles refresh). The return value `Boolean` indicates whether a valid token is now set. Check it before every Spotify API call.

---

## Code Examples

Verified patterns from existing codebase:

### Spotify.myArtists() — confirmed signature
```kotlin
// Source: spotify/src/main/kotlin/com/metrolist/spotify/Spotify.kt:549
suspend fun myArtists(limit: Int = 50, offset: Int = 0): Result<SpotifyPaging<SpotifyArtist>>
// SpotifyArtist.name: String — the only field needed for seed suggestions
```

### SpotifyArtist model
```kotlin
// Source: spotify/src/main/kotlin/com/metrolist/spotify/models/SpotifyArtist.kt
data class SpotifyArtist(
    val id: String = "",
    val name: String = "",
    val images: List<SpotifyImage> = emptyList(),
    val genres: List<String> = emptyList(),
    val popularity: Int? = null,
    val uri: String? = null,
)
```

### SpotifyTokenManager.ensureAuthenticated() — confirmed usage pattern
```kotlin
// Source: app/src/main/kotlin/com/metrolist/music/utils/SpotifyTokenManager.kt:43
// Returns true if valid token is set on Spotify.accessToken, false otherwise
suspend fun ensureAuthenticated(): Boolean

// Correct usage in ViewModel:
val isAuth = SpotifyTokenManager.ensureAuthenticated()
if (isAuth) {
    Spotify.myArtists().getOrNull()?.items ?: emptyList()
} else emptyList()  // silent fallback per D-12, D-13
```

### DatabaseDao artist queries — confirmed available queries
```kotlin
// Source: app/src/main/kotlin/com/metrolist/music/db/DatabaseDao.kt
// Artists with at least one library song, ordered by creation date
fun artistsByCreateDateAsc(): Flow<List<Artist>>  // Artist wraps ArtistEntity + songCount

// Artist name field access pattern:
artist.artist.name  // Artist.artist is @Embedded ArtistEntity
```

### LastFM.getArtistInfo() — confirmed signature and response model
```kotlin
// Source: lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt:212
suspend fun getArtistInfo(artist: String): Result<ArtistInfoResponse>

// Source: lastfm/src/main/kotlin/com/metrolist/lastfm/models/ArtistInfoResponse.kt
data class ArtistInfoResponse(
    val artist: ArtistDetail = ArtistDetail()
) {
    data class ArtistDetail(
        val name: String = "",
        val stats: Stats = Stats(),
        val tags: Tags = Tags()  // ArtistInfoResponse.Tags.tag: List<Tag>
    )
    data class Tag(val name: String = "", val url: String = "")
}
// Tag names are the genre strings (e.g., "rock", "indie", "hip-hop")
```

### ShimmerHost usage pattern
```kotlin
// Source: app/src/main/kotlin/com/metrolist/music/ui/component/shimmer/ShimmerHost.kt
// ShimmerHost wraps children in a Column with shimmer effect
ShimmerHost(showGradient = false) {
    // Place Box placeholders inside — not SuggestionChip (see Pitfall 8)
}
```

### FloatingActionButton existing usage
```kotlin
// Source: app/src/main/kotlin/com/metrolist/music/ui/component/HideOnScrollFAB.kt:77
// Standard FAB pattern in this codebase — uses painterResource, not Icons.*
FloatingActionButton(onClick = onClick) {
    Icon(
        painter = painterResource(icon),  // R.drawable.shuffle already exists
        contentDescription = null,
    )
}
// Note: The project does NOT use Icons.* (material-icons-extended not in deps)
// Always use painterResource(R.drawable.xxx)
```

### BridgeViewModel constructor — current signature (MUST update)
```kotlin
// Source: app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt:59
// Current constructor — must add MusicDatabase to this:
@HiltViewModel
class BridgeViewModel @Inject constructor(
    @BridgeWebView private val webView: WebView,
    private val meldBridgeInterface: MeldBridgeInterface,
    private val playlistBuilder: BridgePlaylistBuilder,
) : ViewModel()

// After Phase 7: add database parameter
@HiltViewModel
class BridgeViewModel @Inject constructor(
    @BridgeWebView private val webView: WebView,
    private val meldBridgeInterface: MeldBridgeInterface,
    private val playlistBuilder: BridgePlaylistBuilder,
    private val database: MusicDatabase,  // NEW — inject for artist queries
) : ViewModel()
```

### PathNodeRow signature — existing (MUST extend for badges)
```kotlin
// Source: app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/PathSheet.kt:107
@Composable
fun PathNodeRow(
    artistName: String,
    info: BridgeArtistInfo?,
    isNowPlaying: Boolean,
)
// Phase 7: add familiarityMap parameter to PathSheet and thread to PathNodeRow
```

### PathSheet signature — existing (MUST extend)
```kotlin
// Source: app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/PathSheet.kt:53
@Composable
fun PathSheet(
    path: List<String>,
    artistMetadata: Map<String, BridgeArtistInfo>,
    nowPlayingIndex: Int,
    modifier: Modifier = Modifier,
)
// Phase 7: add familiarityMap: Map<String, ArtistFamiliarity>
```

### Existing strings in metrolist_strings.xml — bridge section
```xml
<!-- Source: app/src/main/res/values/metrolist_strings.xml lines 957-980 -->
<!-- These exist; Phase 7 adds 5 new entries below them: -->
<!-- bridge_random_fab_description, bridge_random_no_artists, -->
<!-- bridge_badge_new, bridge_badge_known, bridge_seeds_loading_description -->
```

### Test file that must be updated
```kotlin
// Source: app/src/test/kotlin/com/metrolist/music/bridge/BridgeViewModelTest.kt
// buildViewModel() creates BridgeViewModel with mocks — must add mock MusicDatabase
private fun buildViewModel(): Pair<BridgeViewModel, WebView> {
    val mockDatabase = mock<MusicDatabase>()  // NEW — add this mock
    val viewModel = BridgeViewModel(mockWebView, mockBridgeInterface, mockPlaylistBuilder, mockDatabase)
    // ...
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `Spotify.isAuthenticated()` for auth guard | `SpotifyTokenManager.ensureAuthenticated()` | Phase 7 is first phase calling Spotify from BridgeViewModel | Must use the suspend manager, not the simple boolean check |
| No FAB in Bridge tab | Random Bridge FAB | Phase 7 | `BoxWithConstraints` overlay pattern, not `Scaffold` |
| No seed suggestions | Horizontal chip row | Phase 7 | `LazyRow` below inputs, before Find Bridge button |
| No familiarity badges | Known/NEW pills on PathNodeRow | Phase 7 | `PathSheet.kt` and `PathNodeRow` signature extension |

**Confirmed present (no rewrite needed):**
- `Spotify.myArtists()` — production ready
- `LastFM.getArtistInfo()` — production ready, used by existing `fetchArtistMetadata()`
- `SpotifyTokenManager.ensureAuthenticated()` — production ready with mutex
- `DatabaseDao.artistsByCreateDateAsc()` — production ready
- `R.drawable.shuffle` — asset exists
- `ShimmerHost` — production ready

---

## Open Questions

1. **`SuggestionChip` minimum height in LazyRow context**
   - What we know: Phase 6 found `SuggestionChip` has minimum height constraints that made `GenreTagChip` oversized. Phase 6 used `Box` instead.
   - What's unclear: Whether `SuggestionChip` at 32dp is visually acceptable in a horizontal chip row (different context from Phase 6 vertical tag chips). The UI-SPEC explicitly calls for `SuggestionChip`.
   - Recommendation: Plan Wave 0 to test `SuggestionChip` vs. plain `Box` in the `LazyRow`. If `SuggestionChip` looks right, use it (better accessibility). If it looks wrong, use `Box` pattern. Document the decision for STATE.md.

2. **Jaccard fallback when artist pool has < 5 tagged artists**
   - What we know: Jaccard requires at least 2 artists with tags. Artists with 0 tags produce uniform similarity.
   - What's unclear: What happens if the user's library has 10 artists but only 2 have Last.fm tags — Jaccard will pick the only possible pair, which may not be genre-diverse at all.
   - Recommendation: If fewer than 2 tagged artists are found, fall back to random selection from the full pool. Document this fallback in ViewModel comments.

3. **STATE.md blocker: Spotify TOTP Gist fragility**
   - What we know: STATE.md flags "Spotify TOTP Gist fragility — mirror strategy must be decided before Phase 7 planning." The existing `SpotifyAuth.fetchAccessToken()` uses `sp_dc` cookie auth, not TOTP.
   - What's unclear: Whether this concern applies to `myArtists()` (which uses standard GQL auth, not TOTP). Based on code inspection, `myArtists()` goes through `graphqlPost()` → `executeGqlWithRetries()` → standard access token header. TOTP appears to be a separate mechanism.
   - Recommendation: The TOTP concern likely does NOT affect `myArtists()` for this phase. The existing `SpotifyTokenManager` + `sp_dc` cookie flow is sufficient. The TOTP concern is probably about hash rotation for GQL operations (separate from liked artists fetch). Verify by checking if `Spotify.myArtists()` has ever worked in production before proceeding.

---

## Environment Availability

Step 2.6: SKIPPED — Phase 7 is pure Kotlin/Compose code changes to existing app module. All required Android SDK, Gradle, and JVM toolchain are already confirmed working from prior phases (Phases 1-6 all completed successfully).

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + Mockito-Kotlin |
| Config file | `app/build.gradle.kts` (testImplementation blocks) |
| Quick run command | `./gradlew :app:testFossDebugUnitTest --tests "com.metrolist.music.bridge.*" -x lint` |
| Full suite command | `./gradlew :app:testFossDebugUnitTest -x lint` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SPOT-01 | `loadSeedSuggestions()` deduplicates by lowercase name | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgeViewModelTest.loadSeedSuggestions_deduplicates_by_name" -x lint` | ❌ Wave 0 |
| SPOT-01 | Chip row hidden when both sources empty | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgeViewModelTest.seedSuggestions_empty_when_no_sources" -x lint` | ❌ Wave 0 |
| SPOT-01 | Spotify failure falls back to YT Music only (D-13) | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgeViewModelTest.seedSuggestions_falls_back_on_spotify_error" -x lint` | ❌ Wave 0 |
| SPOT-02 | `randomBridge()` fills From+To and calls `startBridge()` | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgeViewModelTest.randomBridge_fills_inputs_and_starts" -x lint` | ❌ Wave 0 |
| SPOT-02 | Toast shown when fewer than 2 artists available (D-06) | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgeViewModelTest.randomBridge_toast_when_insufficient_artists" -x lint` | ❌ Wave 0 |
| SPOT-02 | Jaccard distance picks most-diverse pair | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgeViewModelTest.jaccard_picks_most_diverse_pair" -x lint` | ❌ Wave 0 |
| SPOT-02 | Jaccard handles empty tag sets without crash | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgeViewModelTest.jaccard_handles_empty_tags" -x lint` | ❌ Wave 0 |
| SPOT-03 | `resolveFamiliarity()` marks known artists correctly | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgeViewModelTest.familiarity_marks_known_artists" -x lint` | ❌ Wave 0 |
| SPOT-03 | `resolveFamiliarity()` case-insensitive matching | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgeViewModelTest.familiarity_matching_is_case_insensitive" -x lint` | ❌ Wave 0 |
| SPOT-03 | `resolveFamiliarity()` marks unknown as NEW | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgeViewModelTest.familiarity_marks_unknown_as_new" -x lint` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :app:testFossDebugUnitTest --tests "com.metrolist.music.bridge.*" -x lint`
- **Per wave merge:** `./gradlew :app:testFossDebugUnitTest -x lint`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `app/src/test/kotlin/com/metrolist/music/bridge/BridgeViewModelTest.kt` — extend with 10 new test methods for SPOT-01, SPOT-02, SPOT-03
- [ ] Update `buildViewModel()` helper in existing test file to add `mockDatabase: MusicDatabase` parameter
- [ ] `ArtistFamiliarity` enum must exist before tests can import it — define in `BridgeViewModel.kt` as part of Wave 0 or Wave 1 Task 1

*(Note: The test file `BridgeViewModelTest.kt` already exists — no new file needed. Only new test methods + updated `buildViewModel()` helper required.)*

---

## Project Constraints (from CLAUDE.md)

| Directive | Impact on Phase 7 |
|-----------|-------------------|
| Platform Android only, SDK 26+ | No suspend-main-thread patterns; all I/O on `Dispatchers.IO` |
| Kotlin 2.3.10, JVM 21 | No Java interop needed for new code |
| Jetpack Compose + Material 3 | `LazyRow`, `SuggestionChip`, `FloatingActionButton`, `AnimatedVisibility` — all M3 |
| Hilt DI throughout app module | `MusicDatabase` injected via constructor (not `EntryPoint`) in `@HiltViewModel` |
| `viewModelScope.launch(Dispatchers.IO)` for network/DB | All `Spotify.myArtists()`, `LastFM.getArtistInfo()`, `database.*` calls |
| Return `Result<T>` for failable API calls, chain `.onSuccess/.onFailure` | Spotify and Last.fm calls already follow this pattern |
| `Timber.d/e/w` for logging, not `Log.*` | Tag all new ViewModel logging with `Timber.tag("BridgeSuggestions")` |
| No `Icons.*` from material-icons-extended | Use `painterResource(R.drawable.shuffle)` — asset confirmed present |
| `MutableStateFlow` for ViewModel state | `_seedSuggestions`, `_isLoadingSeeds`, `_artistFamiliarity`, `_isFabLoading` |
| `@HiltViewModel` + `@Inject constructor` pattern | Add `MusicDatabase` as constructor parameter |
| No formatter/linter configured | Follow existing code style by convention |
| `viewModelScope.async(Dispatchers.IO)` + `awaitAll()` for parallel work | Use for parallel tag fetching (same pattern as existing `fetchArtistMetadata()`) |
| GSD workflow enforcement — use `/gsd:execute-phase` | Not applicable (research phase) |

---

## Sources

### Primary (HIGH confidence)
- Codebase direct read — `spotify/src/main/kotlin/com/metrolist/spotify/Spotify.kt` — `myArtists()` signature, `graphqlPost()` auth flow
- Codebase direct read — `spotify/src/main/kotlin/com/metrolist/spotify/models/SpotifyArtist.kt` — `SpotifyArtist.name` field
- Codebase direct read — `app/src/main/kotlin/com/metrolist/music/utils/SpotifyTokenManager.kt` — `ensureAuthenticated()` API
- Codebase direct read — `app/src/main/kotlin/com/metrolist/music/db/DatabaseDao.kt` — `artistsByCreateDateAsc()`, `allArtistsByPlayTime()`, `artistByName()` queries
- Codebase direct read — `app/src/main/kotlin/com/metrolist/music/db/entities/ArtistEntity.kt` — entity schema
- Codebase direct read — `lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt` — `getArtistInfo()` signature
- Codebase direct read — `lastfm/src/main/kotlin/com/metrolist/lastfm/models/ArtistInfoResponse.kt` — tags model
- Codebase direct read — `app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt` — current constructor, existing patterns
- Codebase direct read — `app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/BridgeScreen.kt` — current layout (BoxWithConstraints, not Scaffold)
- Codebase direct read — `app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/PathSheet.kt` — `PathNodeRow` insertion point for badges
- Codebase direct read — `app/src/main/kotlin/com/metrolist/music/ui/component/HideOnScrollFAB.kt` — FAB placement pattern
- Codebase direct read — `app/src/main/kotlin/com/metrolist/music/ui/component/shimmer/ShimmerHost.kt` — shimmer API
- Codebase direct read — `app/src/main/res/values/metrolist_strings.xml` — existing bridge strings, gap analysis
- Codebase direct read — `app/src/main/res/drawable/shuffle.xml` (confirmed present via find) — FAB icon asset
- Codebase direct read — `gradle/libs.versions.toml` — shimmer 1.3.3 confirmed in dependencies
- Codebase direct read — `app/src/test/kotlin/com/metrolist/music/bridge/BridgeViewModelTest.kt` — existing test infrastructure

### Secondary (MEDIUM confidence)
- Jaccard distance formula: standard set-theory metric, well-established — implementation is straightforward inline Kotlin with no external library

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all APIs directly read from source
- Architecture: HIGH — FAB placement pattern directly observed in `HideOnScrollFAB.kt`; ViewModel constructor pattern directly observed across 6+ ViewModels
- Pitfalls: HIGH — Pitfalls 1-8 derived directly from codebase patterns (Phase 6 decisions in STATE.md, existing code structure)
- Jaccard implementation: MEDIUM — algorithm is well-known; implementation behavior under edge cases (empty tags, < 2 artists) requires validation

**Research date:** 2026-04-04
**Valid until:** 2026-05-04 (stable library stack; only Spotify GQL hash rotation could affect validity before then)
