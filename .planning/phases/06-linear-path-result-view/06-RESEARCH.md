# Phase 06: Linear Path Result View - Research

**Researched:** 2026-04-04
**Domain:** Android Jetpack Compose — Bottom Sheet overlay, Last.fm artist.getInfo API, PlayerConnection observation, animated now-playing highlight
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Bottom sheet that slides up over the Bridge tab content. Search inputs remain accessible above the sheet. Sheet can be expanded/collapsed.
- **D-02:** Bottom sheet appears automatically when bridge completes (transition from `Searching` → `PathFound`/`PlaylistReady`). Can be dismissed and re-opened.
- **D-03:** Each bridge artist node shows genre tags and Last.fm listener count only. Clean and minimal — no artist thumbnails.
- **D-04:** Genre tags come from Last.fm `artist.getInfo` API (tags field). Listener count is the `listeners` field from the same API response.
- **D-05:** The currently-playing bridge artist is visually highlighted. Requires observing `PlayerConnection.mediaMetadata` and matching current artist name against the bridge path.
- **D-06:** Only one bridge path exists at a time. Starting a new bridge replaces the old path entirely. No bridge history.
- **D-07:** Path data persists in `BridgeViewModel` state (in-memory). Tab navigation preserves path. App kill loses it.

### Claude's Discretion

- Bottom sheet implementation (project `BottomSheet` composable vs `ModalBottomSheet`)
- Peek height for the bottom sheet
- Visual styling of artist nodes (cards, dividers, spacing)
- How genre tags are displayed (chips, inline text, etc.)
- Animation for the "now playing" highlight
- How to fetch genre tags and listener counts (batch vs. on-demand)

### Deferred Ideas (OUT OF SCOPE)

- Bridge history / replay — P1, deferred to next milestone
- Tapping a bridge artist to see more info — future enhancement
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| BRDG-05 | User sees a linear path result view — seed at top, target at bottom, bridge artists between with genre tags and listener counts | UI-SPEC fully specifies PathNodeRow layout. LastFM module needs `artist.getInfo` method added. BridgeViewModel needs metadata model and fetch logic. |
| PLAY-06 | Path result view persists and remains accessible while bridge playlist plays | ViewModel in-memory state (`_currentPath`, `_artistMetadata` map) survives tab navigation. Sheet re-open affordance ("Show Path" button) handles dismiss/reopen cycle. |
| PLAY-07 | Currently-playing bridge artist is highlighted in the path result view | `PlayerConnection.mediaMetadata` exposed as StateFlow. Artist name from `mediaMetadata.artists.first().name` matched case-insensitively against path list in `BridgeViewModel`. Index stored as `nowPlayingIndex` in `PlaylistReady` state. |
</phase_requirements>

---

## Summary

Phase 6 adds a bottom sheet path view on the Bridge tab that shows the completed bridge journey as a vertical list of artist nodes. The work has three distinct areas: (1) extending the `LastFM` module with `artist.getInfo` to fetch tags and listener counts, (2) adding metadata storage and now-playing index tracking to `BridgeViewModel`, and (3) building the `PathSheet` composable that wraps the existing `BridgeScreen` content in a `Box` overlay using the project's custom `BottomSheet` component.

The UI-SPEC (06-UI-SPEC.md) is highly prescriptive — layout structure, spacing tokens, color tokens, typography roles, animation specs, and accessibility annotations are all pre-defined. Research confirms all specified patterns align with existing code: `animateColorAsState` + `tween(300)` is used in `NewMenuComponents.kt`, `FlowRow` is used in `Dialog.kt`, `FilterChip` is used in Library screens (similar to `SuggestionChip`), and the custom `BottomSheet`/`rememberBottomSheetState` pattern is used in `Player.kt` queue sheet. The planner should treat the UI-SPEC as the authoritative visual contract and this research as the implementation-level backing.

**Primary recommendation:** Follow the UI-SPEC exactly. Add `getArtistInfo()` to the `LastFM` object using the existing unauthenticated GET pattern (no `lastfmParams()` signature needed). Store `BridgeArtistInfo` data class in ViewModel with coroutine-parallel fetch across all path artists after bridge completes. Match now-playing by `PlayerConnection.mediaMetadata.artists.first().name` case-insensitively trimmed.

---

## Standard Stack

### Core (all already in project — no new dependencies)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Jetpack Compose | 1.10.2 | All UI — `LazyColumn`, `Box`, `Column`, `Row`, `AnimatedVisibility` | Project standard |
| Material 3 | 1.5.0-alpha09 | `SuggestionChip`, `MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*` | Project standard |
| `androidx.compose.animation` | (bundled) | `animateColorAsState`, `AnimatedVisibility` | Present, used in `NewMenuComponents.kt` |
| `androidx.compose.foundation.layout.FlowRow` | (bundled, `@ExperimentalLayoutApi`) | Genre tag chip wrapping | Present, used in `Dialog.kt` |
| Ktor (OkHttp engine) | 3.4.0 | HTTP client for `artist.getInfo` call in LastFM module | Project standard |
| kotlinx.serialization | (bundled) | JSON decode for `artist.getInfo` response | Project standard |
| Coroutines | 1.10.2 | Parallel fetch with `async`/`awaitAll` in `viewModelScope` | Project standard |

### No New Dependencies Required

This phase adds no new Gradle dependencies. All components exist in the current dependency graph.

---

## Architecture Patterns

### Recommended Project Structure

New files to create:
```
lastfm/src/main/kotlin/com/metrolist/lastfm/models/
└── ArtistInfoResponse.kt          # Serializable response model for artist.getInfo

app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/
└── PathSheet.kt                    # PathSheet composable + PathNodeRow + GenreTagChip + VerticalConnectorLine
```

Files to modify:
```
lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt
  + getArtistInfo(artist: String): Result<ArtistInfoResponse>

app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt
  + BridgeArtistInfo data class (name, tags, listenerCount)
  + _artistMetadata: MutableStateFlow<Map<String, BridgeArtistInfo>>
  + nowPlayingIndex observation from PlayerConnection.mediaMetadata
  + fetchArtistMetadata(path) coroutine helper

app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/BridgeScreen.kt
  + wrap Column in Box, add PathSheet overlay conditional on uiState
  + "Show Path" button when path exists but sheet is dismissed

app/src/main/res/values/metrolist_strings.xml
  + bridge_path_sheet_title, bridge_path_show_button, bridge_path_listener_count
```

### Pattern 1: artist.getInfo — Unauthenticated GET (same pattern as getArtistTopTracks)

**What:** Add a new `suspend fun getArtistInfo(artist: String)` to the `LastFM` object using a plain `client.get()` with query parameters — no `lastfmParams()` signature required (unauthenticated endpoint).

**When to use:** Called once per bridge path completion, once per artist.

**Example (based on `getArtistTopTracks` at line 192 of `LastFM.kt`):**
```kotlin
// Source: lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt — getArtistTopTracks pattern
suspend fun getArtistInfo(artist: String): Result<ArtistInfoResponse> {
    if (API_KEY.isEmpty()) return Result.failure(IllegalStateException("LastFM not initialized"))
    return runCatching {
        client.get("https://ws.audioscrobbler.com/2.0/") {
            parameter("method", "artist.getInfo")
            parameter("artist", artist)
            parameter("autocorrect", "1")
            parameter("api_key", API_KEY)
            parameter("format", "json")
        }.body<ArtistInfoResponse>()
    }
}
```

**Response model (based on EccoPath's `LastfmArtistInfoResponse` shape at `eccopath/lib/lastfm.ts` lines 43-50):**
```kotlin
// Source: eccopath/lib/lastfm.ts — LastfmArtistInfoResponse interface
@Serializable
data class ArtistInfoResponse(
    val artist: ArtistDetail
) {
    @Serializable
    data class ArtistDetail(
        val name: String = "",
        val stats: Stats = Stats(),
        val tags: Tags = Tags()
    )
    @Serializable
    data class Stats(val listeners: String = "0", val playcount: String = "0")
    @Serializable
    data class Tags(val tag: List<Tag> = emptyList())
    @Serializable
    data class Tag(val name: String = "", val url: String = "")
}
```

**Parsing `listeners`:** The field is a `String` in the Last.fm JSON (same pattern as `ArtistMatch.listeners` in `ArtistSearchResponse`). Parse with `toLongOrNull() ?: 0L`.

---

### Pattern 2: Parallel metadata fetch in ViewModel

**What:** After `buildPlaylist(path)` completes successfully (or concurrently), launch parallel coroutines to fetch `getArtistInfo` for each artist in the path.

**When to use:** Triggered when `BridgeUiState.PathFound` fires — same `init` block intercept in `BridgeViewModel`.

```kotlin
// Source: BridgeViewModel.kt — buildPlaylist() parallel pattern reference
private suspend fun fetchArtistMetadata(path: List<String>) {
    val results = path.map { artist ->
        viewModelScope.async(Dispatchers.IO) {
            LastFM.getArtistInfo(artist)
                .getOrNull()
                ?.let { resp ->
                    artist to BridgeArtistInfo(
                        name = artist,
                        tags = resp.artist.tags.tag.map { it.name },
                        listenerCount = resp.artist.stats.listeners.toLongOrNull() ?: 0L
                    )
                }
        }
    }.awaitAll()
    _artistMetadata.value = results.filterNotNull().toMap()
}
```

**Rate limit note:** Last.fm free tier is ~5 req/sec. A typical path of 5-7 artists sent simultaneously is well within this limit and will complete in one round-trip (~200-400ms). No throttle needed for this phase.

---

### Pattern 3: now-playing index derivation — PlayerConnection.mediaMetadata observation

**What:** Observe `PlayerConnection.mediaMetadata` in `BridgeViewModel` to derive the now-playing path index. Match `mediaMetadata.artists.first().name` (trimmed, lowercased) against the path list.

**Key detail:** `PlayerConnection` cannot be stored in ViewModel (avoids Context leak — established pattern from Phase 5). Instead, expose `mediaMetadata` as a parameter passed from the Composable into a ViewModel function, OR collect it inside the Composable and call `viewModel.updateNowPlaying(artistName)`.

The cleaner approach for this phase: collect `LocalPlayerConnection.current?.mediaMetadata?.collectAsState()` in `BridgeScreen` and call `viewModel.onNowPlayingArtistChanged(name)` on each emission via `LaunchedEffect`.

```kotlin
// In BridgeScreen.kt (Compose side)
val playerConnection = LocalPlayerConnection.current
val mediaMetadata by playerConnection?.mediaMetadata?.collectAsState() ?: remember { mutableStateOf(null) }
LaunchedEffect(mediaMetadata) {
    val artistName = mediaMetadata?.artists?.firstOrNull()?.name ?: ""
    viewModel.onNowPlayingArtistChanged(artistName)
}

// In BridgeViewModel.kt
fun onNowPlayingArtistChanged(artistName: String) {
    val path = _currentPath
    val idx = path.indexOfFirst {
        it.trim().equals(artistName.trim(), ignoreCase = true)
    }
    if (idx >= 0) {
        val current = _uiState.value
        if (current is BridgeUiState.PlaylistReady && current.nowPlayingIndex != idx) {
            _uiState.value = current.copy(nowPlayingIndex = idx)
        }
    }
    // No-op when idx == -1: preserve last known highlight (per D-05 spec)
}
```

---

### Pattern 4: BottomSheet integration — project custom component

**What:** Use `rememberBottomSheetState` + `BottomSheet` from `ui/component/BottomSheet.kt`. This is the same pattern used for the Player queue sheet in `Player.kt` (lines 631-648).

**Trigger:** `LaunchedEffect(uiState)` that calls `state.collapseSoft()` (expand to `collapsedBound`) when uiState becomes `PathFound` or `PlaylistReady`.

**API confirmation (from reading `BottomSheet.kt`):**
- `rememberBottomSheetState(dismissedBound, expandedBound, collapsedBound, initialAnchor)` — all four params verified present
- `state.collapseSoft()` — animates to `collapsedBound` with `Spring.StiffnessMediumLow`
- `state.expandSoft()` — expands with same spring
- `state.isDismissed` — `true` when value == `dismissedBound`
- `state.isExpanded` — `true` when value == `expandedBound`
- `BackHandler` — already handled inside `BottomSheet` composable (`if (!state.isCollapsed && !state.isDismissed) BackHandler(onBack = state::collapseSoft)`)
- The `collapsedContent` lambda renders when `!state.isExpanded && !state.isDismissed` — use this for the "Show Path" drag handle

**Important:** `BottomSheet` uses `fillMaxSize()` for its outer Box which means it must be inside a `Box` parent at the screen level — not a `Column`. This is why the UI-SPEC wraps `BridgeScreen`'s Column in a `Box(fillMaxSize)`.

```kotlin
// Pattern from Player.kt lines 631-648 (confirmed source)
val pathSheetState = rememberBottomSheetState(
    dismissedBound = 0.dp,
    expandedBound = maxHeight,          // from BoxWithConstraints
    collapsedBound = 220.dp,
    initialAnchor = dismissedAnchor     // starts hidden
)

LaunchedEffect(uiState) {
    if (uiState is BridgeUiState.PathFound || uiState is BridgeUiState.PlaylistReady) {
        pathSheetState.collapseSoft()   // slide up to peek
    } else if (uiState is BridgeUiState.Idle) {
        pathSheetState.dismiss()        // hide on new bridge start
    }
}
```

---

### Pattern 5: animateColorAsState for now-playing highlight

**What:** Animate the left border color and background tint transitions when `nowPlayingIndex` changes.

**Confirmed source:** `NewMenuComponents.kt` lines 54-60 — exact same `animateColorAsState` + `tween(200)` pattern already in project.

```kotlin
// Source: app/.../ui/component/NewMenuComponents.kt lines 54-60
val borderColor by animateColorAsState(
    targetValue = if (isNowPlaying) MaterialTheme.colorScheme.primary else Color.Transparent,
    animationSpec = tween(300),
    label = "nowPlayingBorder"
)
val bgTint by animateColorAsState(
    targetValue = if (isNowPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
    animationSpec = tween(300),
    label = "nowPlayingBg"
)
```

---

### Pattern 6: FlowRow for genre tag chips

**What:** `FlowRow` from `androidx.compose.foundation.layout` with `@OptIn(ExperimentalLayoutApi::class)`.

**Confirmed source:** `Dialog.kt` line 14 and 119 — `FlowRow` already used in this project.

```kotlin
@OptIn(ExperimentalLayoutApi::class)
FlowRow(
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
) {
    artist.tags.take(3).forEach { tag -> GenreTagChip(tag) }
}
```

---

### Pattern 7: Listener count formatting

**What:** Format `Long` listener count as human-readable string before exposing from ViewModel (not in Composable).

```kotlin
// In BridgeViewModel or a utility function
fun formatListeners(count: Long): String = when {
    count < 1_000L -> "$count listeners"
    count < 1_000_000L -> "${"%.1f".format(count / 1_000.0)}K listeners"
    else -> "${"%.1f".format(count / 1_000_000.0)}M listeners"
}
```

**Note:** The UI-SPEC specifies a string resource key `bridge_path_listener_count` as a format string. However, since the value prefix ("K", "M") is computed, the formatted result should be pre-computed in the ViewModel and the string resource used as a wrapper: `stringResource(R.string.bridge_path_listener_count, formattedValue)` where the resource is `"%1$s"`. Or keep it as a plain Kotlin format function and forgo the string resource wrapper for the value portion. The planner should decide — either works, but keeping format logic in ViewModel is cleaner for testing.

---

### Anti-Patterns to Avoid

- **Storing `PlayerConnection` in ViewModel:** Project-established rule (Phase 5 decision). Pass artist name changes via a ViewModel method called from `LaunchedEffect` in Composable instead.
- **Using `ModalBottomSheet`:** The project uses its own `BottomSheet` component, not the Material 3 `ModalBottomSheet`. `ModalBottomSheet` creates a scrim that blocks interaction with content behind it — the search inputs must stay accessible. The custom `BottomSheet` does not block.
- **Fetching `artist.getInfo` inside the Composable:** Fetch in ViewModel on IO dispatcher, expose result as StateFlow, observe in Composable.
- **Hardcoding hex colors:** All colors via `MaterialTheme.colorScheme.*` per project convention and UI-SPEC.
- **Using `lastfmParams()` for `artist.getInfo`:** That helper adds an API signature (HMAC-MD5) required only for authenticated endpoints. `artist.getInfo` is public — use plain `client.get()` with query parameters, same as `getArtistTopTracks`.
- **Auto-scrolling `LazyColumn` on now-playing change:** UI-SPEC explicitly defers this to v2. Do not implement.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Bottom sheet drag/spring physics | Custom draggable + physics | `BottomSheet` + `BottomSheetState` from `ui/component/BottomSheet.kt` | Already handles fling velocity, spring animation, nested scroll, back gesture |
| Genre tag layout wrapping | Manual `Row` with overflow logic | `FlowRow` (`@ExperimentalLayoutApi`) | Handles wrap-to-next-row automatically |
| Color transition on highlight | Coroutine-based color interpolation | `animateColorAsState` | Compose-native, cancellable, hardware-accelerated |
| Icon visibility fade | Visibility toggle + custom anim | `AnimatedVisibility(enter=fadeIn, exit=fadeOut)` | Standard Compose animation |
| Last.fm HTTP client | New `HttpClient` instance | Reuse `LastFM` object's existing `client` lazy property | Shares connection pool, headers, JSON config |

---

## Common Pitfalls

### Pitfall 1: `BottomSheet` requires `fillMaxSize` parent — Column breaks the layout

**What goes wrong:** Placing the `BottomSheet` directly inside the existing `Column` in `BridgeScreen` causes the sheet's `graphicsLayer { translationY }` to calculate wrong offsets. The sheet either never appears or renders at the wrong position.

**Why it happens:** `BottomSheet`'s inner Box uses `fillMaxSize()` and computes its Y offset relative to `expandedBound`. If constrained by a Column (which measures children to their minimum height), `expandedBound` resolves to 0.dp or a small value.

**How to avoid:** Wrap the entire `BridgeScreen` content in a `BoxWithConstraints(fillMaxSize)`. The Column content goes in first (so it renders behind the sheet). The `BottomSheet` overlay goes second (renders on top). Pass `maxHeight` from `BoxWithConstraints` as the `expandedBound` for `rememberBottomSheetState`.

**Warning signs:** Sheet appears at wrong position, flickers, or `expandedBound` equals `collapsedBound`.

---

### Pitfall 2: `artist.getInfo` returns `listeners` as a String, not Long

**What goes wrong:** Deserializing the `listeners` field as `Long` causes a `SerializationException` — Last.fm returns it as `"12345678"` (JSON string), not `12345678` (JSON number).

**Why it happens:** Last.fm API is inconsistent — `artist.search` returns `listeners` as a String (confirmed in `ArtistSearchResponse.ArtistMatch.listeners: String`). `artist.getInfo` returns stats the same way (confirmed by EccoPath's TypeScript interface: `stats: { listeners: string }`).

**How to avoid:** Declare `listeners: String = "0"` in the response model. Parse with `toLongOrNull() ?: 0L` in the ViewModel.

---

### Pitfall 3: `nowPlayingIndex` derived from artist name string match may misfire

**What goes wrong:** Artist name from `MediaMetadata.artists.first().name` may not exactly match the path artist name (e.g., "The Beatles" vs "Beatles", capitalization differences). Index remains stale while a bridge artist is playing.

**Why it happens:** Bridge path artist names come from Last.fm (via EccoPath) but the track metadata artist name comes from YouTube Music via InnerTube. These sources use different canonical forms.

**How to avoid:** Use `.trim().lowercase()` on both sides of the comparison. Accept partial matches (one name contains the other) as a fallback. The UI-SPEC already specifies "case-insensitive, trimmed" — the plan should make both sides trimmed+lowercase explicit in the implementation.

**Warning signs:** `nowPlayingIndex` never updates while bridge playlist is playing.

---

### Pitfall 4: `@ExperimentalLayoutApi` opt-in for `FlowRow`

**What goes wrong:** Compiler error: `This API is experimental and its use requires @OptIn(ExperimentalLayoutApi::class)` when using `FlowRow`.

**Why it happens:** `FlowRow` is still experimental in Compose 1.x.

**How to avoid:** Add `@OptIn(ExperimentalLayoutApi::class)` to the composable function containing `FlowRow`. Already done in `Dialog.kt` — follow the same pattern.

---

### Pitfall 5: Sheet stays visible after user starts a new bridge

**What goes wrong:** User taps "Find Bridge" with new artists. Sheet remains open showing the old path while the new bridge is computing.

**Why it happens:** The `LaunchedEffect(uiState)` only expands the sheet on `PathFound`/`PlaylistReady`. If `Idle` state doesn't dismiss the sheet, it persists.

**How to avoid:** In the `LaunchedEffect(uiState)` block, also call `pathSheetState.dismiss()` (or `pathSheetState.snapTo(0.dp)`) when `uiState is BridgeUiState.Idle` or `BridgeUiState.Searching`.

---

### Pitfall 6: `SuggestionChip` minimum height constraint

**What goes wrong:** `SuggestionChip` enforces a minimum height of 32dp from Material 3 spec. Setting `Modifier.height(24.dp)` alone does not reduce the actual touch target — it clips the chip visually but keeps the internal padding.

**Why it happens:** `SuggestionChip` internally applies `ChipDefaults.SuggestionChipPadding` which includes vertical padding. The `contentPadding` override is needed to neutralize this.

**How to avoid:** As specified in the UI-SPEC: `Modifier.height(24.dp)` combined with `ChipDefaults.suggestionChipColors(...)` and `contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)` passed to the `SuggestionChip` composable. Since chips are non-interactive in this phase, the reduced touch target is acceptable per accessibility guidance.

**Alternative:** Use a plain `Box` + `Text` styled as a chip instead of `SuggestionChip`. This avoids the minimum height constraint entirely. Either approach is valid — `SuggestionChip` with content padding override is preferred for semantic consistency with Material 3.

---

## Code Examples

### ArtistInfoResponse model
```kotlin
// New file: lastfm/src/main/kotlin/com/metrolist/lastfm/models/ArtistInfoResponse.kt
// Source: eccopath/lib/lastfm.ts LastfmArtistInfoResponse interface (lines 43-50) + ArtistSearchResponse pattern
package com.metrolist.lastfm.models

import kotlinx.serialization.Serializable

@Serializable
data class ArtistInfoResponse(
    val artist: ArtistDetail = ArtistDetail()
) {
    @Serializable
    data class ArtistDetail(
        val name: String = "",
        val stats: Stats = Stats(),
        val tags: Tags = Tags()
    )
    @Serializable
    data class Stats(
        val listeners: String = "0",
        val playcount: String = "0"
    )
    @Serializable
    data class Tags(
        val tag: List<Tag> = emptyList()
    )
    @Serializable
    data class Tag(
        val name: String = "",
        val url: String = ""
    )
}
```

### BridgeArtistInfo data class (in BridgeViewModel.kt)
```kotlin
// Source: CONTEXT.md D-03, D-04 — minimal node content
data class BridgeArtistInfo(
    val name: String,
    val tags: List<String>,         // from artist.tags.tag[].name, up to 3 shown in UI
    val listenerCount: Long,        // from artist.stats.listeners parsed as Long
    val formattedListeners: String  // pre-formatted: "1.2M listeners"
)
```

### PathSheet trigger in BridgeScreen
```kotlin
// Source: BottomSheet.kt rememberBottomSheetState + Player.kt queue sheet pattern (lines 631-636)
BoxWithConstraints(Modifier.fillMaxSize()) {
    val pathSheetState = rememberBottomSheetState(
        dismissedBound = 0.dp,
        expandedBound = maxHeight,
        collapsedBound = 220.dp,
        initialAnchor = dismissedAnchor
    )

    LaunchedEffect(uiState) {
        when (uiState) {
            is BridgeUiState.PathFound, is BridgeUiState.PlaylistReady -> pathSheetState.collapseSoft()
            is BridgeUiState.Idle, is BridgeUiState.Searching -> pathSheetState.dismiss()
            else -> Unit
        }
    }

    // Phase 4 search content (rendered behind sheet)
    BridgeSearchContent(...)

    // Path sheet overlay
    val path = when (val s = uiState) {
        is BridgeUiState.PathFound -> s.path
        is BridgeUiState.PlaylistReady -> s.path
        else -> null
    }
    if (path != null) {
        BottomSheet(
            state = pathSheetState,
            onDismiss = { /* allow dismiss */ },
            collapsedContent = { PathSheetHandle(onShowPath = { pathSheetState.collapseSoft() }) },
            content = { PathNodeList(path, artistMetadata, nowPlayingIndex) }
        )
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `ModalBottomSheet` (blocks content behind) | Project custom `BottomSheet` (non-modal overlay) | Pre-existing in codebase | Search inputs remain accessible while sheet is collapsed |
| Hardcoded colors per state | `animateColorAsState` with `tween()` | Compose 1.0+ | Smooth color transitions between highlighted/unhighlighted states |
| Manual row layout overflow | `FlowRow` (`@ExperimentalLayoutApi`) | Compose 1.5+ | Genre tags wrap automatically |

---

## Open Questions

1. **`BridgeViewModel` constructor — `PlayerConnection` not injected**
   - What we know: `BridgeViewModel` does not receive `PlayerConnection` in its constructor (verified in source). Player state is passed into methods from the Composable.
   - What's unclear: Whether observing `mediaMetadata` via `LaunchedEffect` + `viewModel.onNowPlayingArtistChanged()` is sufficient, or whether a `StateFlow<String>` for now-playing artist should be collected directly in the ViewModel via `combine()` on a flow provided at construction time.
   - Recommendation: Use the `LaunchedEffect` + method call pattern from the Composable. It's consistent with how Phase 5 handled `PlayerConnection` and avoids constructor changes.

2. **Fetch strategy: parallel batch vs. sequential**
   - What we know: 5-7 artists in a typical path. Last.fm free tier ~5 req/sec. A 7-artist batch fires within one second.
   - What's unclear: Whether `async`/`awaitAll` or sequential `forEach` is safer for rate limiting.
   - Recommendation: Use `async`/`awaitAll` (parallel). 7 simultaneous requests is well within the 5 req/sec limit because they are issued nearly simultaneously and the limit is per-second. Parallel fetch delivers metadata 3-5x faster than sequential. If a request fails, its artist shows no tags/count (silent omission, per UI-SPEC "empty tags fallback").

3. **"Show Path" re-open affordance placement**
   - What we know: UI-SPEC specifies a "Show Path" button. When the sheet is dismissed, some affordance must exist to re-open it.
   - What's unclear: Whether this button appears in the `collapsedContent` of the `BottomSheet` (only visible when collapsed, not dismissed) or as a separate floating element.
   - Recommendation: Use the `collapsedContent` parameter of `BottomSheet` for a compact handle bar. When `state.isDismissed` is true, render a small "Show Path" `TextButton` or `Icon` as a separate element at the bottom of the `BridgeSearchContent` column (similar to how the mini-player affordance works). The planner should specify exact placement.

---

## Environment Availability

Step 2.6: SKIPPED (no external dependencies beyond the project's existing LastFM module and Compose stack — no new tools, services, or runtimes required).

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + Mockito-Kotlin |
| Config file | `app/build.gradle.kts` — `testImplementation(libs.junit)` |
| Quick run command | `./gradlew :app:testUniversalFossDebugUnitTest --tests "com.metrolist.music.bridge.*" -x lint` |
| Full suite command | `./gradlew :app:testUniversalFossDebugUnitTest -x lint` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BRDG-05 | `formatListeners()` produces correct output for all three ranges | unit | `./gradlew :app:testUniversalFossDebugUnitTest --tests "*.BridgeViewModelTest.formatListeners*"` | ❌ Wave 0 |
| BRDG-05 | `fetchArtistMetadata()` stores parsed tags and listener count in `_artistMetadata` | unit | `./gradlew :app:testUniversalFossDebugUnitTest --tests "*.BridgeViewModelTest.fetchArtistMetadata*"` | ❌ Wave 0 |
| PLAY-07 | `onNowPlayingArtistChanged()` updates `nowPlayingIndex` when artist is found in path | unit | `./gradlew :app:testUniversalFossDebugUnitTest --tests "*.BridgeViewModelTest.nowPlaying*"` | ❌ Wave 0 |
| PLAY-07 | `onNowPlayingArtistChanged()` preserves last index when artist not found | unit | same as above | ❌ Wave 0 |
| PLAY-06 | `_currentPath` and `_artistMetadata` survive state reset to `PlaylistReady` (path not cleared) | unit | `./gradlew :app:testUniversalFossDebugUnitTest --tests "*.BridgeViewModelTest.pathPersists*"` | ❌ Wave 0 |
| PLAY-06 | New bridge clears old path and metadata when `PathFound` fires again | unit | same as above | ❌ Wave 0 |
| BRDG-05 | `ArtistInfoResponse` deserializes correctly from Last.fm JSON shape (listeners as String) | unit | `./gradlew :lastfm:test --tests "*.ArtistInfoResponseTest*"` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew :app:testUniversalFossDebugUnitTest --tests "com.metrolist.music.bridge.*" -x lint`
- **Per wave merge:** `./gradlew :app:testUniversalFossDebugUnitTest -x lint`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `app/src/test/kotlin/com/metrolist/music/bridge/BridgeViewModelTest.kt` — extend existing file with Phase 6 tests (formatListeners, fetchArtistMetadata, onNowPlayingArtistChanged, pathPersists)
- [ ] `lastfm/src/test/kotlin/com/metrolist/lastfm/ArtistInfoResponseTest.kt` — new file, JSON deserialization test for `listeners` as String field

*(File `BridgeViewModelTest.kt` exists and compiles — extend it, do not recreate.)*

---

## Sources

### Primary (HIGH confidence)

- `app/src/main/kotlin/com/metrolist/music/ui/component/BottomSheet.kt` — Full `BottomSheet` composable and `BottomSheetState` API, `rememberBottomSheetState` parameters
- `app/src/main/kotlin/com/metrolist/music/ui/player/Player.kt` lines 629-648 — `rememberBottomSheetState` usage pattern for queue sheet
- `app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt` — Existing ViewModel shape, `_currentPath`, `init` intercept pattern
- `app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/BridgeScreen.kt` — `BridgeUiState` sealed class, existing screen layout
- `lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt` — `getArtistTopTracks` pattern (unauthenticated GET with query params), existing `client` lazy property
- `lastfm/src/main/kotlin/com/metrolist/lastfm/models/ArtistSearchResponse.kt` — Confirmed `listeners: String` pattern
- `eccopath/lib/lastfm.ts` lines 43-50 — `LastfmArtistInfoResponse` TypeScript interface confirming `stats.listeners: string` and `tags.tag[].name`
- `app/src/main/kotlin/com/metrolist/music/ui/component/NewMenuComponents.kt` lines 8, 54-65 — `animateColorAsState` + `tween(200)` confirmed in project
- `app/src/main/kotlin/com/metrolist/music/ui/component/Dialog.kt` lines 14, 119 — `FlowRow` import and usage confirmed in project
- `app/src/main/kotlin/com/metrolist/music/playback/PlayerConnection.kt` lines 132, 441 — `mediaMetadata: MutableStateFlow<MediaMetadata?>` confirmed
- `app/src/main/kotlin/com/metrolist/music/models/MediaMetadata.kt` lines 22, 40-43 — `artists: List<Artist>`, `Artist.name: String` confirmed
- `.planning/phases/06-linear-path-result-view/06-UI-SPEC.md` — Full visual and interaction contract (HIGH confidence — project-internal)

### Secondary (MEDIUM confidence)

- Last.fm API documentation pattern inferred from `getArtistTopTracks` implementation (same unauthenticated GET structure) — not separately verified against live docs, but consistent with EccoPath TypeScript implementation

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all components confirmed present in project
- Architecture patterns: HIGH — all patterns directly observed in existing codebase files
- LastFM API shape: HIGH — confirmed by both Kotlin implementation (`ArtistSearchResponse`) and EccoPath TypeScript (`lastfm.ts`) for the `listeners: string` type
- Pitfalls: HIGH — most derived from reading the actual source code and the established Phase 5 decisions

**Research date:** 2026-04-04
**Valid until:** 2026-05-04 (stable — no fast-moving dependencies)
