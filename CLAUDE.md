## Project

**EccoMeld** — Music discovery app for Android. Fork of Meld that adds Bridge Discovery (genre-bridging paths between artists) and Path Walker (interactive FALA graph exploration). All existing Meld features stay intact.

**Core Value:** Discover music you didn't know you wanted through meaningful genre bridges — not algorithmic recommendations pushing the same popular songs.

## Build & Test Commands

```bash
# Compile check (fast, catches import/type errors)
JAVA_HOME=/nix/store/wrf2p3qb5sycka2y4nnl7pdm8h6zpcin-openjdk-21.0.10+7 ANDROID_HOME=$HOME/Android/Sdk ./gradlew compileUniversalFossDebugKotlin

# Unit tests
JAVA_HOME=/nix/store/wrf2p3qb5sycka2y4nnl7pdm8h6zpcin-openjdk-21.0.10+7 ANDROID_HOME=$HOME/Android/Sdk ./gradlew testUniversalFossDebugUnitTest

# Build + install on device
JAVA_HOME=/nix/store/wrf2p3qb5sycka2y4nnl7pdm8h6zpcin-openjdk-21.0.10+7 ANDROID_HOME=$HOME/Android/Sdk ./gradlew installUniversalFossDebug

# Launch app
~/Android/Sdk/platform-tools/adb shell am start -n com.meld.app.debug/com.metrolist.music.MainActivity

# Logcat (bridge-specific)
~/Android/Sdk/platform-tools/adb logcat --pid=$(~/Android/Sdk/platform-tools/adb shell pidof -s com.meld.app.debug) -v time -s "BridgeAlgorithm:D" "BridgeCache:D" "BridgePlaylistBuilder:D"
```

## Critical Gotchas

1. **Trim artist names** — Last.fm API returns 404 for trailing spaces. Always `.trim()` before any API call
2. **Room cache tables get purged** — `KotlinBridgeCache.init{}` wipes `bridge_similar_artists` and `bridge_artist_meta` on cold start. History tables must be separate
3. **Unicode in fuzzy matching** — Use `\p{L}\p{N}` not `[a-z0-9]` in normalization regexes. CJK characters must survive
4. **Compose Canvas hit-testing** — Touch coordinates must be inverted through pan/zoom transform before distance comparison to node positions
5. **`SuggestionChip` swallows gestures** — Use custom `Surface` + `combinedClickable` for chips that need `onLongClick`
6. **Dispatchers** — `IO` for network/DB, `Main` for UI/StateFlow, `Default` for CPU-bound (hyperbolic math). Always explicit
7. **PlayerConnection** — Pass as parameter to ViewModel methods, never store. Avoids Context leaks

## Constraints

- Android SDK 26+ targeting 36 | Kotlin 2.3.10 | Compose 1.10.2 | Material 3
- Must use existing Media3/ExoPlayer stack — no playback rewrite
- Package name stays `com.metrolist.music`
- GPL license (inherited from Meld/InnerTune)
- Distribution: GitHub releases + sideload APK only

## Module Map

| Module | Purpose |
|--------|---------|
| `app` | Main app — UI, DB, DI, playback, bridge, pathwalker |
| `innertube` | YouTube Music InnerTube API client |
| `spotify` | Spotify GraphQL + REST API client |
| `lastfm` | Last.fm API client (scrobbling + bridge data) |
| `kugou`, `lrclib`, `betterlyrics`, `simpmusic` | Lyrics providers |
| `shazamkit` | Music recognition |
| `kizzy` | Discord Rich Presence |

## Key Patterns

- **API clients** are `object` singletons (`YouTube`, `Spotify`, `LastFM`) returning `Result<T>`
- **ViewModels** use `@HiltViewModel` + `MutableStateFlow` + `viewModelScope`
- **Composables** use PascalCase, receive `navController`, observe via `collectAsState()`
- **Room** has single `DatabaseDao` (1747 lines), wrapped by `MusicDatabase` via delegation
- **Naming**: `*Entity.kt` for Room, `*Screen.kt` for UI, `*ViewModel.kt`, `*Ext.kt` for extensions

## Testing Protocol

After each implementation:
1. Write unit tests for new logic
2. Run compile check — must pass
3. Run unit tests — must pass
4. Build + install on device
5. Verify via logcat — no crashes
6. Commit only after all checks pass

## Error Recovery

After 2-3 failed attempts at the same problem:
1. Write problem + context to `.planning/debug/{name}.md`
2. `/clear` for fresh context
3. Read debug file, research the problem
4. Tackle again without accumulated wrong assumptions

## GSD Workflow

Use GSD commands for all work:
- `/gsd:quick` for small fixes
- `/gsd:debug` for investigation
- `/gsd:execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless explicitly asked.
