# Architecture Patterns

**Domain:** Android MVVM — WebView JS bridge + playlist generation integrated into existing Media3 app
**Researched:** 2026-04-03
**Confidence:** HIGH (existing codebase verified, Android official docs verified)

---

## How the New Features Fit the Existing Layers

The existing architecture has five clearly separated layers: UI (Compose screens), ViewModel, Data (Room + external API clients), Playback (`MusicService` + `PlayerConnection`), and a Lyrics helper. The bridge feature adds work to every layer except Lyrics, and introduces one new component — the JS bridge object — that lives at the boundary between the WebView and the ViewModel.

No existing layer needs to be restructured. Bridge is additive.

---

## Component Map

### New Components Required

| Component | Layer | Responsibility |
|-----------|-------|----------------|
| `BridgeScreen` | UI | Two artist search inputs, loading states, linear path result view |
| `BridgeViewModel` | ViewModel | Owns all bridge state: search inputs, bridge status, path result, playlist build progress |
| `BridgeWebViewManager` | UI helper | Wraps `AndroidView(WebView)`, injects JS bridge object, loads bundled EccoPath assets |
| `MeldBridgeInterface` | JS bridge | `@JavascriptInterface`-annotated class or `WebMessageListener` that receives `createPlaylist(json)` from JS and dispatches to ViewModel |
| `BridgePlaylistBuilder` | Repository / ViewModel | Takes bridge path JSON, searches YT Music per artist, assembles ordered `List<MediaItem>` |
| `BridgeQueue` | Playback queue | Implements `Queue` interface wrapping a pre-resolved `List<MediaItem>` — essentially identical to `ListQueue` |
| EccoPath bundled assets | `app/src/main/assets/` | Built Next.js output from submodule, loaded via `WebViewAssetLoader` |

### Existing Components Used (No Modification)

| Component | How Used |
|-----------|----------|
| `ListQueue` | `BridgeQueue` can reuse this directly — it accepts a `List<MediaItem>` with a title |
| `PlayerConnection.playQueue(queue)` | Called from `BridgeViewModel` after playlist is built to hand off to `MusicService` |
| `YouTube.search(query, filter)` | Called from `BridgePlaylistBuilder` for each artist+track pair |
| `Spotify` singleton | Read liked artists for seed suggestions and Random Bridge Jaccard scoring |
| `MusicDatabase` | Existing `SongEntity` / `ArtistEntity` cache; optional — not required for MVP |
| `Screens.kt` + `NavigationBuilder.kt` | Add Bridge tab entry here |
| `AndroidView` pattern | Already established in `SpotifyLoginScreen` — exact same pattern for bridge WebView |

---

## Recommended Architecture

```
BridgeScreen (Compose)
  ├── observes BridgeViewModel.uiState: StateFlow<BridgeUiState>
  ├── dispatches user events to BridgeViewModel (search, random bridge)
  └── contains BridgeWebViewManager composable
        └── AndroidView(WebView)
              ├── loads: https://appassets.androidplatform.net/assets/eccopath/index.html
              ├── WebViewAssetLoader intercepts → serves from app/src/main/assets/eccopath/
              └── JS bridge registered before loadUrl()
                    └── MeldBridgeInterface
                          └── createPlaylist(json) → posts to BridgeViewModel via callback

BridgeViewModel (HiltViewModel)
  ├── MutableStateFlow<BridgeUiState>
  ├── receives bridge result via bridgeCallback (function ref or Channel)
  ├── launches BridgePlaylistBuilder in viewModelScope
  └── calls playerConnection.playQueue(BridgeQueue(...)) when playlist is ready

BridgePlaylistBuilder (plain class, no @Singleton)
  ├── takes List<BridgeArtist> from bridge JSON
  ├── for each artist: YouTube.search("$trackName $artistName", SONG_FILTER) → take best match
  ├── skips silently on match failure (no throw)
  └── returns List<MediaItem> in genre-transition order

BridgeQueue : Queue
  └── delegates to ListQueue("Bridge: $from → $to", items)
```

---

## Data Flow (Explicit Direction)

```
1. User types "From" + "To" in BridgeScreen
   → BridgeViewModel.setFromArtist(name) / setToArtist(name)

2. User taps "Find Bridge"
   → BridgeViewModel.startBridge()
   → BridgeViewModel emits BridgeUiState.Loading
   → calls webView.evaluateJavascript("EccoPath.startBridge('$from','$to')", null)
      (must be posted to main thread — WebView methods are main-thread only)

3. EccoPath beam search runs inside WebView (JS, async, uses Last.fm via IndexedDB cache)

4. EccoPath calls window.MeldBridge.createPlaylist(jsonString) when done
   → MeldBridgeInterface.createPlaylist(json) fires on JavaBridge background thread
   → MeldBridgeInterface posts result to BridgeViewModel via pre-registered callback lambda
      (callback is set by ViewModel, bridge object just calls it)

5. BridgeViewModel receives JSON string
   → parses to List<BridgeArtist>
   → emits BridgeUiState.PathFound(artists)   ← screen shows linear path view immediately
   → launches viewModelScope.launch(Dispatchers.IO) { buildPlaylist(artists) }

6. BridgePlaylistBuilder.build(artists)
   → for each artist: 2 popular + 3-5 deep cuts (separate searches or top-tracks endpoint)
   → YouTube.search("${track.title} ${track.artist}", SearchFilter.SONG) → first result
   → collect matched MediaItems, skip nulls
   → returns List<MediaItem>

7. BridgeViewModel receives playlist
   → emits BridgeUiState.PlaylistReady(path, itemCount)
   → calls playerConnection.playQueue(ListQueue("$from → $to", mediaItems))

8. MusicService receives queue via existing playQueue() path
   → ExoPlayer resolves stream URLs via ResolvingDataSource (existing)
   → Media session, notification, background playback — all existing, zero new code

9. BridgeScreen observes BridgeUiState.PlaylistReady
   → shows linear path view with labeled bridge artists
   → player bar begins playing (existing PlayerConnection StateFlow updates)
```

---

## JS Bridge: Which API to Use

**Recommendation: `addJavascriptInterface` with `@JavascriptInterface`.**

Rationale: EccoPath is a bundled, first-party asset loaded from `file:///android_asset/` or the `appassets.androidplatform.net` virtual origin. The security concern driving `WebMessageListener` is third-party origins injecting into frames. Since EccoPath is 100% controlled code bundled in the APK, origin filtering gives no additional protection. The `addJavascriptInterface` pattern is simpler, has no API level gating beyond SDK 17 (already met — minSdk 26), and is the pattern the existing codebase uses for its cipher WebViews (`CipherManager`, `SignatureDeobfuscator`).

The `WebMessageListener` pattern adds `WebViewFeature.isFeatureSupported()` gating complexity with no benefit here.

**Thread safety note:** `@JavascriptInterface` methods are invoked on the `JavaBridge` background thread, not the main thread. The bridge object must not touch the ViewModel directly via StateFlow — instead use a simple lambda callback or `Channel` that the ViewModel registers. All WebView methods (`evaluateJavascript`, `loadUrl`) must be called on the main thread — use `Handler(Looper.getMainLooper()).post { }` or `withContext(Dispatchers.Main)` when calling WebView from a coroutine.

```kotlin
class MeldBridgeInterface(
    private val onPlaylistReady: (String) -> Unit,  // called on JavaBridge thread
) {
    @JavascriptInterface
    fun createPlaylist(json: String) {
        onPlaylistReady(json)  // ViewModel registers this callback; it posts to StateFlow
    }
}
```

In `BridgeViewModel`:
```kotlin
val bridge = MeldBridgeInterface { json ->
    // called on JavaBridge thread — use postValue equivalent
    _uiState.value = BridgeUiState.PathReceived(json)
    viewModelScope.launch(Dispatchers.IO) { buildPlaylist(json) }
}
```

`MutableStateFlow.value = ...` is thread-safe and can be set from any thread. This makes the JavaBridge-thread callback safe to write directly.

---

## WebView Asset Loading

**Use `WebViewAssetLoader`** (not `file:///android_asset/`).

`file://` URIs are blocked for `fetch()` and `XMLHttpRequest` cross-origin requests in modern WebView versions. EccoPath's Next.js bundle uses dynamic imports and fetch calls that break under `file://`. `WebViewAssetLoader` serves assets from a virtual HTTPS-like origin (`https://appassets.androidplatform.net`) which passes the same-origin checks.

```
app/src/main/assets/eccopath/     ← built EccoPath Next.js output (git submodule built assets)
  index.html
  _next/
    static/
      ...
```

The `WebViewAssetLoader` maps `https://appassets.androidplatform.net/assets/eccopath/*` to `assets/eccopath/*` in the APK. The `WebViewClient.shouldInterceptRequest()` override handles routing.

**Important:** EccoPath's Last.fm calls go to `https://ws.audioscrobbler.com` — these are real network requests from inside the WebView. The WebView needs `settings.domStorageEnabled = true` (for IndexedDB cache) and internet permission (already present in the manifest from existing Meld functionality).

---

## Playlist Builder: Where the Logic Lives

**Location: inside `BridgeViewModel`, not a separate Repository or Service.**

Reasoning:
- The builder is stateless given its inputs. It does not need Room access (no caching in MVP). A `Repository` abstraction is warranted when multiple ViewModels share the data — only `BridgeViewModel` needs it.
- Running it in `viewModelScope.launch(Dispatchers.IO)` gives automatic cancellation if the user navigates away, progress emission via `StateFlow`, and access to coroutine operators for parallel per-artist fetching.
- `MusicService` is already overloaded (3460 lines). Do not add playlist-building logic there.
- A plain `BridgePlaylistBuilder` class (not a Hilt singleton) can be instantiated inside the ViewModel or injected via Hilt as a `@ViewModelScoped` dependency if testing requires mocking.

**Parallelism:** Use `async/await` or `map { async { } }.awaitAll()` per bridge artist within the `viewModelScope` to run YT Music searches concurrently. With 5-7 bridge artists, 5 tracks each, sequential search would be ~35 network calls in series. Parallel cuts this to ~7 parallel batches (one batch per artist).

```kotlin
val allTracks: List<MediaItem> = bridgeArtists
    .map { artist ->
        async(Dispatchers.IO) { builder.fetchTracksFor(artist) }
    }
    .awaitAll()
    .flatten()
```

---

## Compose State Model

`BridgeViewModel` exposes a single sealed `BridgeUiState` as a `StateFlow`:

```kotlin
sealed class BridgeUiState {
    object Idle : BridgeUiState()
    object SearchingBridge : BridgeUiState()           // WebView running EccoPath
    data class PathFound(                               // path visible, playlist building
        val path: List<BridgeArtist>,
        val playlistProgress: Int,                      // 0..100 for progress bar
    ) : BridgeUiState()
    data class PlaylistReady(                           // playing, path view stable
        val path: List<BridgeArtist>,
        val trackCount: Int,
    ) : BridgeUiState()
    data class Error(val message: String) : BridgeUiState()
}
```

Collected in `BridgeScreen` via `collectAsStateWithLifecycle()`. Compose recomposes on each transition. This matches the exact pattern used by every existing ViewModel in the codebase (StateFlow + `collectAsState()`).

---

## WebView Lifecycle in Compose

**Critical:** Compose recreates composables on recomposition. Wrap the `WebView` in `AndroidView` with the `remember { }` factory pattern — do not recreate it on recompose.

The existing `SpotifyLoginScreen` does this correctly: the `factory` lambda is passed to `AndroidView`, which only calls it once. The same pattern applies here.

`BridgeWebViewManager` should be a composable that holds the `WebView` instance in a `remember` block, receives the `BridgeViewModel` reference, and adds the bridge interface before calling `loadUrl`. Keep the `WebView` alive as long as the Bridge screen is in the back stack — this avoids re-running beam search if the user briefly navigates away.

If EccoPath computation takes 30+ seconds (realistic for cold start with no IndexedDB cache), the WebView must survive orientation changes. Hoist `WebView` state into the ViewModel or use `rememberSaveable` to restore position. The simpler MVP approach: use `configChanges="orientation|screenSize"` in `AndroidManifest` for `MainActivity` (already likely present given the existing Spotify WebView usage) to prevent recreation.

---

## Navigation Integration

Add `Bridge` to `Screens.kt` alongside `Home`, `Search`, `ListenTogether`, `Library`. Register the route in `NavigationBuilder.kt`. Add the tab to `MainActivity`'s bottom navigation bar list.

```kotlin
object Bridge : Screens(
    titleId = R.string.bridge,
    iconIdInactive = R.drawable.bridge_outlined,   // new drawable needed
    iconIdActive = R.drawable.bridge_filled,
    route = "bridge"
)
```

`MainScreens` in `Screens.kt` becomes: `listOf(Home, Search, Bridge, ListenTogether, Library)` — or replace `ListenTogether` if 5 tabs is too many for the design.

---

## Suggested Build Order (Phase Dependencies)

Each step depends on the previous being complete or mockable.

```
1. Navigation shell
   Add Bridge tab + empty BridgeScreen composable.
   Verifies: routing works, bottom nav shows Bridge.
   No dependencies.

2. BridgeViewModel + BridgeUiState
   Define state sealed class, wire up StateFlow, connect to empty screen.
   Verifies: state flows to UI.
   Depends on: (1).

3. WebView + asset loading
   Create app/src/main/assets/eccopath/, configure WebViewAssetLoader,
   load EccoPath index.html in BridgeWebViewManager.
   Verifies: EccoPath graph renders in WebView.
   Depends on: EccoPath submodule build output.

4. JS bridge (MeldBridgeInterface)
   Add addJavascriptInterface, stub createPlaylist() to log JSON.
   Trigger manually via evaluateJavascript from test button.
   Verifies: JSON flows from JS → Kotlin → Timber log.
   Depends on: (3).

5. BridgeScreen search inputs + trigger
   Two artist inputs, Find Bridge button calls evaluateJavascript("EccoPath.startBridge(...)").
   Verifies: EccoPath runs beam search end-to-end and calls MeldBridge.createPlaylist().
   Depends on: (4).

6. BridgePlaylistBuilder
   Parse bridge JSON, YouTube.search() per track, collect MediaItems.
   Can be unit-tested standalone with mock YouTube responses.
   Verifies: Given a known bridge path, returns non-empty List<MediaItem>.
   Depends on: Understanding of EccoPath JSON output schema.

7. Queue handoff + playback
   Call playerConnection.playQueue(ListQueue("$from → $to", items)).
   Verifies: Bridge playlist plays in existing player bar.
   Depends on: (5) and (6).

8. Linear path result view
   Render BridgeArtist list with genre tags, listener counts, bridge labels.
   Depends on: (5) (needs real path data to design against).

9. Random Bridge button
   Fetch Spotify liked artists, compute Tag Jaccard distance, pick two genre-opposite,
   auto-trigger bridge.
   Depends on: (5) + existing Spotify.likedArtists() working.
```

---

## Fitting Into Existing MVVM Layers

| Existing Layer | What Bridge Adds | What Stays Unchanged |
|----------------|-----------------|----------------------|
| UI (Compose screens) | `BridgeScreen.kt`, `BridgeWebViewManager.kt`, bridge-specific composables (path view, artist card) | All existing screens unmodified |
| ViewModel | `BridgeViewModel.kt` (new HiltViewModel) | All 36 existing ViewModels unmodified |
| Data (external APIs) | Uses `YouTube.search()` directly — no new module needed | `innertube/` module unchanged |
| Data (Room DB) | No new tables in MVP — song matching not cached | `MusicDatabase` / `DatabaseDao` unchanged |
| Playback | New `BridgeQueue` (can alias `ListQueue`) — no `MusicService` changes | `MusicService`, `PlayerConnection` unchanged |
| DI (Hilt) | `BridgeViewModel` gets `@Inject PlayerConnection` + optional `@Inject BridgePlaylistBuilder` | `AppModule`, `NetworkModule` unchanged |

`PlayerConnection` is provided as a `CompositionLocal` in `MainActivity` and accessed from Compose screens via `LocalPlayerConnection.current`. `BridgeViewModel` can receive it via Hilt constructor injection (same as existing ViewModels that access it).

---

## Anti-Patterns to Avoid

### Calling WebView methods from IO coroutine dispatcher
**What goes wrong:** `evaluateJavascript` and `loadUrl` called off the main thread cause `CalledFromWrongThreadException` at runtime.
**Prevention:** Always wrap WebView calls with `withContext(Dispatchers.Main)`.

### Holding WebView reference in ViewModel
**What goes wrong:** ViewModel outlives Activity; holding a `Context`-attached `WebView` causes memory leaks and crashes on configuration change.
**Prevention:** WebView lives in the Compose `remember { }` block in the UI layer only. Communication from ViewModel to WebView goes through a `StateFlow<WebViewCommand>` that the Compose layer observes and acts on. ViewModel never holds the WebView reference.

### Building playlist inside MusicService
**What goes wrong:** `MusicService` is already 3460 lines and handles ExoPlayer, queue, session, scrobbling, Discord RPC, download manager, equalizer, crossfade, sleep timer. Adding playlist building adds another async responsibility, making the service harder to reason about and test.
**Prevention:** All playlist building in `BridgeViewModel`/`BridgePlaylistBuilder`. Service receives only a fully-built `Queue`.

### Sequential YT Music searches
**What goes wrong:** 35 sequential network calls (7 artists × 5 tracks) at ~300ms each = ~10.5 seconds blocking the user.
**Prevention:** `async/awaitAll` per artist group. Results in ~2-3 seconds wall time.

### Recreating WebView on recomposition
**What goes wrong:** Compose recompositions are frequent. A new `WebView` each time loses the EccoPath JS state, re-runs beam search, discards IndexedDB cache.
**Prevention:** `val webView = remember { WebView(context).apply { ... } }` — create once, reuse. `AndroidView(factory = { webView })`.

---

## Scalability Considerations

The MVP architecture is intentionally simple — it does not need to scale beyond one active bridge computation at a time. P1 additions (Bridge History, Graph Queue) would require:

- **Bridge History:** Extend `Room` with a `BridgeHistoryEntity` table. `BridgeViewModel` writes path + playlist after successful build. Separate `BridgeHistoryViewModel` for the history screen.
- **Graph Queue (P1):** The `BridgeWebViewManager` WebView stays alive as the user walks the graph. The JS bridge gains additional methods (`queueArtists`, `autoBridge`). `BridgeViewModel` gains staging state. This is additive — no structural rework.
- **Native Kotlin port (post-P1):** The `BridgePlaylistBuilder` and `BridgeViewModel` interfaces remain unchanged. The WebView is replaced by a Kotlin coroutine calling a `BridgeCrawler` class. The JS bridge object becomes dead code and is removed. `BridgeScreen` loses `BridgeWebViewManager` and gains a Kotlin progress indicator.

---

## Sources

- [Android Developer Docs — JS bridge (`addWebMessageListener` vs `addJavascriptInterface`)](https://developer.android.com/develop/ui/views/layout/webapps/native-api-access-jsbridge) — HIGH confidence
- [Android Developer Docs — WebViewAssetLoader for local content](https://developer.android.com/reference/androidx/webkit/WebViewAssetLoader) — HIGH confidence
- [Android Developer Docs — Media3 playlist management](https://developer.android.com/media/media3/exoplayer/playlists) — HIGH confidence
- [Android Developer Docs — StateFlow and SharedFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow) — HIGH confidence
- [Android Developer Docs — Manage WebView state in Compose](https://developer.android.com/develop/ui/compose/quick-guides/content/manage-webview-state) — HIGH confidence
- Existing codebase: `SpotifyLoginScreen.kt` (WebView + `AndroidView` pattern), `Queue.kt` + `ListQueue.kt` (queue abstraction), `PlayerConnection.playQueue()`, `YouTube.search()` — HIGH confidence (direct code inspection)
