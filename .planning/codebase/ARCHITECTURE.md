# Architecture

**Analysis Date:** 2026-04-03

## Pattern Overview

**Overall:** MVVM (Model-View-ViewModel) with a multi-module Gradle architecture

**Key Characteristics:**
- Single-Activity Jetpack Compose UI with NavHost-based navigation
- Hilt dependency injection throughout the app module
- Multi-module design: app module depends on standalone library modules for external API clients
- Media3 (ExoPlayer) `MediaLibraryService` for background audio playback
- DataStore Preferences for settings/configuration (not SharedPreferences)
- Room database for local persistence with a single monolithic DAO
- Library modules are pure API clients with no Android framework dependencies (except Android library plugin)
- Product flavors separate FOSS (F-Droid) and GMS (Google Cast) builds

## Layers

**UI Layer (Compose Screens + ViewModels):**
- Purpose: Render UI and handle user interactions
- Location: `app/src/main/kotlin/com/metrolist/music/ui/` and `app/src/main/kotlin/com/metrolist/music/viewmodels/`
- Contains: Composable screens, reusable components, menus, player UI, theme definitions
- Depends on: ViewModels, `PlayerConnection`, `MusicDatabase`
- Used by: `MainActivity` via NavHost

**ViewModel Layer:**
- Purpose: Hold UI state, orchestrate data loading from database and network
- Location: `app/src/main/kotlin/com/metrolist/music/viewmodels/`
- Contains: 36 HiltViewModels, each for a specific screen or feature
- Depends on: `MusicDatabase`, `YouTube` (innertube singleton), `Spotify` singleton, DataStore
- Used by: Compose screens via `hiltViewModel()`

**Data Layer (Database):**
- Purpose: Local persistence of songs, artists, albums, playlists, events, lyrics
- Location: `app/src/main/kotlin/com/metrolist/music/db/`
- Contains: Room database (`InternalDatabase`), single DAO (`DatabaseDao` - 1747 lines), entity classes, `MusicDatabase` wrapper
- Depends on: Room, entity models
- Used by: ViewModels, `MusicService`, `PlayerConnection`

**Data Layer (External API Clients):**
- Purpose: Network communication with YouTube Music, Spotify, lyrics providers, etc.
- Location: Separate Gradle modules: `innertube/`, `spotify/`, `kugou/`, `lrclib/`, `betterlyrics/`, `simpmusic/`, `lastfm/`, `shazamkit/`, `kizzy/`
- Contains: Singleton API client objects, request/response models, page parsers
- Depends on: Ktor HTTP client, kotlinx.serialization
- Used by: ViewModels, `MusicService`, `LyricsHelper`

**Playback Layer:**
- Purpose: Audio playback, queue management, media session, notifications
- Location: `app/src/main/kotlin/com/metrolist/music/playback/`
- Contains: `MusicService` (3460 lines), `PlayerConnection`, queue implementations, download utilities
- Depends on: Media3/ExoPlayer, Room database, YouTube/Spotify API clients, DataStore
- Used by: `MainActivity` (via bound service), UI layer (via `PlayerConnection`)

**Lyrics Layer:**
- Purpose: Fetch synchronized/plain lyrics from multiple providers
- Location: `app/src/main/kotlin/com/metrolist/music/lyrics/`
- Contains: `LyricsHelper` (orchestrator), provider implementations, provider registry
- Depends on: External API modules (kugou, lrclib, betterlyrics, simpmusic), innertube
- Used by: `MusicService`, lyrics UI components

## Data Flow

**Song Playback Flow:**

1. User taps a song in a Compose screen
2. Screen calls `playerConnection.playQueue(queue)` where queue is a `Queue` implementation (e.g., `YouTubeQueue`, `ListQueue`, `SpotifyPlaylistQueue`)
3. `MusicService` receives the queue, resolves media items via `YouTube.player()` or cache
4. ExoPlayer loads the resolved stream URL through `ResolvingDataSource` with `CacheDataSource`
5. `MusicService` updates media session, triggers scrobbling, Discord RPC, widget updates
6. `PlayerConnection` exposes reactive state (playback state, current metadata, queue) via `MutableStateFlow`
7. Compose UI recomposes based on collected StateFlows

**Data Loading Flow (e.g., Album Screen):**

1. `AlbumViewModel` is created with `albumId` from `SavedStateHandle` (navigation argument)
2. ViewModel observes `database.albumWithSongs(albumId)` as a `StateFlow`
3. ViewModel launches coroutine to fetch fresh data from `YouTube.album(albumId)`
4. On success, ViewModel writes updated data to Room database via `database.transaction { ... }`
5. Room Flow automatically emits updated data to the UI

**State Management:**
- **UI state:** `MutableStateFlow` in ViewModels, collected via `collectAsState()` in Compose
- **Playback state:** `MutableStateFlow` in `PlayerConnection`, provided via `CompositionLocalProvider`
- **Settings/Preferences:** DataStore Preferences, accessed via `rememberPreference()` composable utility and `dataStore.get()` extension
- **Navigation state:** Jetpack Navigation Compose (`NavHostController`)

## Key Abstractions

**Queue Interface:**
- Purpose: Represents a playable sequence of media items with pagination support
- Examples: `app/src/main/kotlin/com/metrolist/music/playback/queues/YouTubeQueue.kt`, `SpotifyPlaylistQueue.kt`, `ListQueue.kt`, `YouTubeAlbumRadio.kt`
- Pattern: Strategy pattern - each queue type knows how to fetch its items and load more pages

**LyricsProvider Interface:**
- Purpose: Abstract lyrics fetching from multiple sources
- Examples: `app/src/main/kotlin/com/metrolist/music/lyrics/LrcLibLyricsProvider.kt`, `KuGouLyricsProvider.kt`, `BetterLyricsProvider.kt`
- Pattern: Strategy pattern with configurable provider ordering via `LyricsProviderRegistry`

**MusicDatabase Wrapper:**
- Purpose: Wraps Room's `InternalDatabase` to provide transaction helpers and delegate DAO methods
- Examples: `app/src/main/kotlin/com/metrolist/music/db/MusicDatabase.kt`
- Pattern: Delegation pattern - `MusicDatabase` implements `DatabaseDao by delegate.dao`

**MediaMetadata:**
- Purpose: Unified metadata model bridging innertube/Spotify models with local database entities
- Examples: `app/src/main/kotlin/com/metrolist/music/models/MediaMetadata.kt`
- Pattern: Data class with conversion functions (`toSongEntity()`, `toMediaItem()`)

**PlayerConnection:**
- Purpose: Bridge between UI and `MusicService`, exposes reactive player state
- Examples: `app/src/main/kotlin/com/metrolist/music/playback/PlayerConnection.kt`
- Pattern: Observer pattern via `Player.Listener` + StateFlow emissions

## Entry Points

**Application:**
- Location: `app/src/main/kotlin/com/metrolist/music/App.kt`
- Triggers: Application startup
- Responsibilities: Initialize Hilt, configure YouTube/Spotify/LastFM singletons, set up image loader, create notification channels, observe settings changes

**MainActivity:**
- Location: `app/src/main/kotlin/com/metrolist/music/MainActivity.kt` (1302 lines)
- Triggers: App launch, deep links, intents
- Responsibilities: Set up Compose UI tree, bind to `MusicService`, manage navigation, handle deep links, provide `PlayerConnection` via CompositionLocal

**MusicService:**
- Location: `app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt` (3460 lines)
- Triggers: Bound by `MainActivity`, media session commands
- Responsibilities: ExoPlayer management, queue processing, stream URL resolution, media session, notifications, scrobbling, Discord RPC, download management, equalizer, crossfade, sleep timer

**Navigation:**
- Location: `app/src/main/kotlin/com/metrolist/music/ui/screens/NavigationBuilder.kt`
- Triggers: NavHost route changes
- Responsibilities: Define all routes and map them to Composable screens. Uses `NavGraphBuilder.navigationBuilder()` extension function

**Screens Definition:**
- Location: `app/src/main/kotlin/com/metrolist/music/ui/screens/Screens.kt`
- Triggers: Bottom navigation bar
- Responsibilities: Define main tabs: Home, Search, ListenTogether, Library

## Error Handling

**Strategy:** Result-based error handling with `onSuccess`/`onFailure` callbacks from Kotlin `Result` type

**Patterns:**
- API calls return `Result<T>` (e.g., `YouTube.album()` returns `Result<AlbumPage>`)
- ViewModels call `.onSuccess { ... }` to update database and `.onFailure { reportException(it) }` for error logging
- `reportException()` utility in `app/src/main/kotlin/com/metrolist/music/utils/Utils.kt` for centralized error reporting
- `CrashHandler` in `app/src/main/kotlin/com/metrolist/music/utils/CrashHandler.kt` catches unhandled exceptions and shows a crash activity (`CrashActivity`)
- `PlayerConnection` has safe player accessor with `getPlayerSafe()` that handles `UninitializedPropertyAccessException`

## Dependency Injection

**Framework:** Dagger Hilt

**Modules:**
- `app/src/main/kotlin/com/metrolist/music/di/AppModule.kt` - Provides `MusicDatabase`, `InternalDatabase`, `DatabaseDao`, `SimpleCache` (player + download), `ListenTogetherClient/Manager`, `CoroutineScope`
- `app/src/main/kotlin/com/metrolist/music/di/NetworkModule.kt` - Provides `NetworkConnectivityObserver`
- `app/src/main/kotlin/com/metrolist/music/di/WrappedModule.kt` - Provides `WrappedManager`, `WrappedAudioService`
- `app/src/main/kotlin/com/metrolist/music/di/Qualifiers.kt` - Custom qualifiers: `@PlayerCache`, `@DownloadCache`, `@ApplicationScope`
- `app/src/main/kotlin/com/metrolist/music/di/LyricsHelperEntryPoint.kt` - Hilt EntryPoint for `LyricsHelper` (used outside DI-managed classes)

**Scope:** All provided dependencies are `@Singleton` scoped via `SingletonComponent`

**Note:** External API clients (`YouTube`, `Spotify`, `LastFM`, `KuGou`) are NOT injected via Hilt. They are Kotlin `object` singletons accessed directly. Configuration is set on them imperatively in `App.kt`.

## Cross-Cutting Concerns

**Logging:**
- Timber throughout the app (`Timber.d()`, `Timber.e()`, etc.)
- `DebugTree` planted in `App.onCreate()`
- Spotify API logging wired to Timber via `Spotify.logger` callback

**Settings/Configuration:**
- DataStore Preferences accessed via `dataStore` extension property on `Context`
- Preference keys defined in `app/src/main/kotlin/com/metrolist/music/constants/PreferenceKeys.kt`
- Compose screens use `rememberPreference()` and `rememberEnumPreference()` utilities from `app/src/main/kotlin/com/metrolist/music/utils/DataStore.kt`

**Image Loading:**
- Coil 3 with custom `ImageLoader` (memory cache 25%, configurable disk cache)
- Configured in `App.newImageLoader()`

**Authentication:**
- YouTube: Cookie-based auth stored in DataStore, set on `YouTube.cookie`
- Spotify: OAuth token managed by `SpotifyTokenManager` in `app/src/main/kotlin/com/metrolist/music/utils/SpotifyTokenManager.kt`
- LastFM: Session key stored in DataStore
- Discord: Token stored in DataStore for RPC

**Product Flavors:**
- `foss` (default): No Google Play Services, cast stubs in `app/src/foss/`
- `gms`: Google Cast support via `CastManager` in `app/src/gms/`
- Both flavors provide `CastConnectionHandler`, `CastOptionsProvider`, and `CastButton` with different implementations

---

*Architecture analysis: 2026-04-03*
