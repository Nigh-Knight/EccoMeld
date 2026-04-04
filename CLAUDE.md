<!-- GSD:project-start source:PROJECT.md -->
## Project

**EccoMeld**

EccoMeld is a music discovery and playback app for Android that fuses the bridge-finding algorithm from EccoPath with the full-featured YouTube Music playback stack from Meld. Users pick two artists from different corners of music, and EccoMeld finds a 5-7 hop path through niche midpoint artists, then builds a genre-transitioning playlist that plays via YouTube Music streams. The existing Meld music client (home feed, search, library, player, lyrics, etc.) stays fully intact — Bridge is added as a new tab.

**Core Value:** Discover music you didn't know you wanted through meaningful, human-like genre bridges between any two artists — not algorithmic recommendations pushing the same popular songs.

### Constraints

- **Platform**: Android only (SDK 26+, targeting SDK 36)
- **Playback**: Must use existing Media3/ExoPlayer stack — no rewrite
- **Bridge algorithm**: WebView + JS bridge for MVP — no native Kotlin port yet
- **EccoPath bundling**: Git submodule, built assets packaged in APK — works offline
- **Distribution**: GitHub releases + sideload APK only
- **Branding**: App name "EccoMeld" but keep existing Meld UI theming/icon for now
- **License**: GPL (inherited from Meld/InnerTune)
- **Package name**: Keep `com.metrolist.music` — renaming deferred
<!-- GSD:project-end -->

<!-- GSD:stack-start source:codebase/STACK.md -->
## Technology Stack

## Languages
- Kotlin 2.3.10 - All application and library module code
- JVM Target: 21 (Java 21 compatibility)
- Kotlin DSL - All Gradle build scripts (`build.gradle.kts`, `settings.gradle.kts`)
## Runtime
- Android SDK 36 (compileSdk / targetSdk)
- Minimum SDK 26 (Android 8.0 Oreo)
- JVM Toolchain: 21
- Gradle 9.3.1 (wrapper in `gradle/wrapper/gradle-wrapper.properties`)
- Lockfile: Not used (dependency locking not configured)
- Version catalog: `gradle/libs.versions.toml` (centralized dependency management)
## Build System
- Android Gradle Plugin 9.0.0
- Kotlin Compose Compiler (`org.jetbrains.kotlin.plugin.compose`)
- Kotlin Serialization (`org.jetbrains.kotlin.plugin.serialization`)
- KSP (Kotlin Symbol Processing) 2.3.5 - for Room and Hilt annotation processing
- Dagger Hilt 2.59.1
- Flavor dimensions: `abi` (universal, arm64, armeabi, x86, x86_64) + `variant` (foss, gms)
- `foss` (default): F-Droid compatible, no Google Play Services
- `gms`: Includes Google Cast support via Play Services
- ProGuard/R8 minification enabled for release builds
- Core library desugaring enabled (`desugar_jdk_libs_nio 2.1.5`)
- `gradle.properties`: 4GB JVM heap, parallel builds, configuration cache enabled
- Room schema export: `app/schemas/` directory
- Compose compiler metrics available via `enableComposeCompilerReports` property
## Frameworks
- Jetpack Compose 1.10.2 - UI framework (declarative)
- Material 3 1.5.0-alpha09 - Design system
- AndroidX Activity Compose 1.12.3 - Activity integration
- AndroidX Media3 (ExoPlayer) 1.7.1 - Audio/video playback engine
- Room 2.8.4 - SQLite ORM with KSP annotation processing
- Dagger Hilt 2.59.1 - DI framework
- Ktor 3.4.0 - Primary HTTP client across all modules
- OkHttp - Used directly for ListenTogether WebSocket connections
- kotlinx.serialization JSON - Used in all modules for JSON parsing
- Protocol Buffers (protobuf-javalite 4.33.5, protobuf-kotlin-lite) - Message serialization
- Coil 3.3.0 (coil-compose + coil-network-okhttp) - Async image loading
- JUnit 4.13.2 - Unit testing (present in library modules)
- Renovate (`renovate.json`) - Automated dependency updates
- Crowdin (`crowdin.yml`) - Translation management
- Fastlane (`fastlane/`) - Release metadata (F-Droid)
## Key Dependencies
- `NewPipeExtractor v0.26.0` (via JitPack) - YouTube content extraction in innertube module
- `Ktor 3.4.0` - All API communication across every module
- `Room 2.8.4` - Local music database (songs, playlists, albums, artists, lyrics, play counts)
- `Media3/ExoPlayer 1.7.1` - Core audio playback engine
- `Dagger Hilt 2.59.1` - Dependency injection throughout the app
- `MaterialKolor 4.1.1` - Dynamic Material You color theming
- `compose-shimmer 1.3.3` - Loading placeholder animations
- `compose-reorderable 3.0.0` - Drag-and-drop list reordering
- `uCrop 2.2.11` - Image cropping
- `palette-ktx 1.0.0` - Color extraction from album art
- `Timber 5.0.1` - Logging (used in app and innertube modules)
- `Guava 33.5.0-jre` - Utilities (ListenableFuture bridging)
- `kotlinx-coroutines-guava 1.10.2` - Coroutine-ListenableFuture bridge
- `Brotli 0.1.2` - Brotli decompression for YouTube responses
- `Jsoup 1.22.1` - HTML parsing
- `kuromoji-ipadic 0.9.0` - Japanese text tokenization (for search/sorting)
- `TinyPinyin 2.0.3` - Chinese pinyin conversion (for search/sorting)
- `Apache Commons Lang3 3.20.0` - String utilities
- `play-services-cast-framework 22.2.0`
- `mediarouter 1.8.1`
## Module Structure
| Module | Type | Purpose |
|--------|------|---------|
| `app` | Android Application | Main app with UI, database, DI, playback service |
| `innertube` | Android Library | YouTube Music InnerTube API client (103 Kotlin files) |
| `spotify` | JVM Library (pure Kotlin) | Spotify GraphQL + REST API client |
| `lastfm` | Android Library | Last.fm scrobbling API client |
| `shazamkit` | Android Library | Shazam music recognition API client |
| `lrclib` | Android Library | LrcLib synchronized lyrics API client |
| `kugou` | Android Library | KuGou lyrics search API client |
| `betterlyrics` | Android Library | BetterLyrics TTML lyrics API client |
| `simpmusic` | Android Library | SimpMusic lyrics API client |
| `kizzy` | Android Library | Discord Rich Presence via WebSocket gateway |
| `metroproto` | (empty) | Placeholder module (no build file found) |
## Configuration
- `local.properties` - Local developer config (API keys: `LASTFM_API_KEY`, `LASTFM_SECRET`)
- Environment variables used in CI: `LASTFM_API_KEY`, `LASTFM_SECRET`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
- Spotify auth uses `sp_dc` cookie from WebView login (no developer API key needed)
- `build.gradle.kts` (root) - Plugin declarations, subproject Kotlin compiler config
- `settings.gradle.kts` - Module includes, repository configuration
- `gradle/libs.versions.toml` - Centralized version catalog (~95 entries)
- `gradle.properties` - JVM args, Android settings, performance flags
- `lint.xml` - Lint configuration
## Platform Requirements
- JDK 21
- Android SDK 36
- Gradle 9.3.1 (via wrapper)
- Android 8.0+ (API 26+)
- Target: Android 16 (API 36)
- Architectures: arm64-v8a, armeabi-v7a, x86_64, x86
## CI/CD
- `build.yml` - Build APKs on push (foss + gms variants)
- `build_pr.yml` - PR builds
- `build_quick.yml` - Quick build workflow
- `release.yml` - Release publishing
- `spotify-hash-check.yml` - Automated Spotify GQL hash rotation detection
- `pr_title_prefix.yml` - PR title validation
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

## Naming Patterns
- Root package: `com.metrolist.music` (app module)
- Library modules: `com.metrolist.<module>` (e.g., `com.metrolist.innertube`, `com.metrolist.spotify`, `com.metrolist.kugou`)
- Feature-based sub-packages under app: `ui`, `db`, `viewmodels`, `models`, `playback`, `utils`, `di`, `constants`, `extensions`, `recognition`, `eq`, `api`, `widget`
- UI sub-packages: `ui.screens`, `ui.component`, `ui.menu`, `ui.player`, `ui.theme`, `ui.utils`
- Screen sub-packages by feature: `ui.screens.settings`, `ui.screens.playlist`, `ui.screens.album`, `ui.screens.artist`, `ui.screens.search`, `ui.screens.wrapped`, `ui.screens.library`, `ui.screens.equalizer`, `ui.screens.podcast`, `ui.screens.recognition`
- Kotlin source files use PascalCase: `HomeScreen.kt`, `MusicService.kt`, `AlbumViewModel.kt`
- Extension files use PascalCase with `Ext` suffix: `CoroutineExt.kt`, `StringExt.kt`, `MediaItemExt.kt`, `ContextExt.kt`
- Utility files use PascalCase: `DataStore.kt`, `SyncUtils.kt`, `NetworkUtils.kt`
- Constants in dedicated file: `PreferenceKeys.kt` in `constants/` package
- Entity files: separate `<Name>Entity.kt` (Room entity) and `<Name>.kt` (composite with relations)
- ViewModels: PascalCase with `ViewModel` suffix: `HomeViewModel`, `AlbumViewModel`, `SpotifyPlaylistViewModel`
- Entities: PascalCase with `Entity` suffix for Room entities: `SongEntity`, `AlbumEntity`, `PlaylistEntity`
- Composables: PascalCase function names matching file names: `HomeScreen()`, `PreferenceEntry()`, `MiniPlayer()`
- Sealed classes for hierarchies: `sealed class YTItem`, `sealed class LocalItem`
- Data classes for models: `data class MediaMetadata`, `data class DailyDiscoverItem`
- Object singletons for API clients: `object Spotify`, `object YouTube`, `object KuGou`, `object LastFM`
- camelCase for all functions
- Composable functions use PascalCase (Compose convention): `HomeScreen()`, `PreferenceEntry()`, `MetrolistTheme()`
- Extension functions: `fun Song.toMediaMetadata()`, `fun SongItem.toMediaMetadata()`
- Conversion functions: `to<Target>()` pattern: `toSongEntity()`, `toMediaMetadata()`, `asPlaylistItem()`, `asSongItem()`
- Toggle functions: `toggleLike()`, `toggleLibrary()`, `toggleUploaded()`, `localToggleLike()`
- Prefix `local` for operations that skip remote sync: `localToggleLike()` vs `toggleLike()`
- camelCase for all variables and properties
- MutableStateFlow fields: descriptive camelCase: `isRefreshing`, `quickPicks`, `homePage`
- Preference keys: PascalCase with `Key` suffix as top-level vals: `DynamicThemeKey`, `PureBlackKey`, `MaxSongCacheSizeKey`
- Constants: UPPER_SNAKE_CASE for companion object constants: `LIKED_PLAYLIST_ID`, `DB_NAME`
- Enums: PascalCase class name, UPPER_SNAKE_CASE entries: `enum class SliderStyle { DEFAULT, WAVY, SLIM }`
- Exception: some enums use PascalCase entries: `enum class DensityScale(val value: Float, val label: String)`
- Custom annotations: PascalCase: `@PlayerCache`, `@DownloadCache`, `@ApplicationScope`
## Code Style
- No dedicated formatter configured (no ktlint, detekt, or .editorconfig detected)
- Android Lint is configured in `lint.xml` at root level, referenced in `app/build.gradle.kts`
- Lint settings: `warningsAsErrors = false`, `abortOnError = false`, `checkDependencies = false`
- Lint suppresses `UnsafeOptInUsageError` for Media3 unstable APIs
- Kotlin compiler: `suppressWarnings = false`, opt-in for `kotlin.RequiresOptIn`
- Android Lint only, run as part of CI builds alongside `assemble` tasks
- No static analysis tools (detekt, ktlint, spotless) configured
- Compose compiler reports/metrics available via `enableComposeCompilerReports` property
## File Headers
## Import Organization
- Used occasionally for constants: `import com.metrolist.music.constants.*`
- Avoid wildcard imports elsewhere
- No path aliases. Modules are referenced by project dependency: `implementation(project(":innertube"))`
## Coroutine Patterns
- Application-wide scope via Hilt: `@ApplicationScope CoroutineScope` with `SupervisorJob() + Dispatchers.Default` (`app/src/main/kotlin/com/metrolist/music/di/AppModule.kt`)
- ViewModel scope: `viewModelScope` from AndroidX Lifecycle
- Composable scope: `rememberCoroutineScope()` for UI-triggered operations
- `Dispatchers.IO` for network calls, database operations, and file I/O
- `Dispatchers.Main` for UI-thread operations (Toast, WebView)
- `Dispatchers.Default` for application scope (CPU-bound work)
- Always specify dispatcher explicitly with `launch(Dispatchers.IO) { ... }`
- `MutableStateFlow` for ViewModel state: `val quickPicks = MutableStateFlow<List<Song>?>(null)`
- `stateIn()` to convert database Flow to StateFlow: `database.albumWithSongs(id).stateIn(viewModelScope, SharingStarted.Eagerly, null)`
- `distinctUntilChanged()` on DataStore flows to avoid redundant emissions
- `collectAsState()` in Composables to observe StateFlows
- `combine()` for merging multiple flows
- Extension functions in `CoroutineExt.kt` for scoped collection: `flow.collect(scope) { ... }`, `flow.collectLatest(scope) { ... }`
- YouTube API returns `Result<T>` from Kotlin stdlib
- Use `.onSuccess { }` and `.onFailure { }` chaining pattern:
- `reportException()` in `app/src/main/kotlin/com/metrolist/music/utils/Utils.kt` is a simple `printStackTrace()` wrapper
## Compose Conventions
- Screen composables are top-level functions named `<Feature>Screen`
- Screens receive `navController: NavController` as parameter
- Screens receive `scrollBehavior: TopAppBarScrollBehavior` when they have scrollable content
- ViewModels obtained via `hiltViewModel<T>()` inside the composable
- State observed via `val state by viewModel.stateFlow.collectAsState()`
- Preferences via custom `rememberPreference(key, defaultValue)` returning `MutableState<T>` (`app/src/main/kotlin/com/metrolist/music/utils/DataStore.kt`)
- Enum preferences via `rememberEnumPreference(key, defaultValue)`
- Local UI state via `remember { mutableStateOf(...) }` and `rememberSaveable { ... }`
- Side effects via `LaunchedEffect`, `DisposableEffect`
- Custom theme wrapper: `MetrolistTheme()` in `app/src/main/kotlin/com/metrolist/music/ui/theme/Theme.kt`
- Supports dynamic colors (Android 12+ wallpaper), custom seed color, pure black mode
- Uses materialKolor library for non-system dynamic color generation
- Default theme color: `val DefaultThemeColor = Color(0xFFED5564)`
- Access colors via `MaterialTheme.colorScheme`
- Reusable components in `ui/component/`: `PreferenceEntry`, `IconButton`, `NavigationTitle`, `Items`
- Shimmer placeholders in `ui/component/shimmer/`: `GridItemPlaceholder`, `ListItemPlaceholder`
- Menu composables in `ui/menu/`: `SongMenu`, `AlbumMenu`, `PlayerMenu` - context menus as bottom sheets
- Player UI in `ui/player/`: `Player`, `MiniPlayer`, `Queue`, `Thumbnail`
- Navigation routes defined in a `Screens` sealed class/enum
- Route-based navigation with string arguments: `composable(Screens.Home.route) { ... }`
- Navigation builder as extension function: `NavGraphBuilder.navigationBuilder()` in `app/src/main/kotlin/com/metrolist/music/ui/screens/NavigationBuilder.kt`
- Arguments extracted via `SavedStateHandle` in ViewModels: `savedStateHandle.get<String>("albumId")!!`
- `LocalPlayerAwareWindowInsets` for insets that account for mini player/bottom sheet
- Standard `LocalContext`, `LocalDensity`, `LocalHapticFeedback`
## Data Class Patterns
- Located in `app/src/main/kotlin/com/metrolist/music/db/entities/`
- Annotated with `@Entity(tableName = "...")`, `@Immutable`
- Use `@PrimaryKey val id: String` (YouTube video/playlist/album IDs)
- Use `@ColumnInfo(defaultValue = "...")` for migration-safe defaults
- Include business logic methods: `toggleLike()`, `toggleLibrary()`
- Use `java.time.LocalDateTime` for timestamps (with core library desugaring)
- Wrap entity + relations: `data class Song(@Embedded val song: SongEntity, @Relation ... val artists: List<ArtistEntity>)`
- Extend `sealed class LocalItem` for polymorphism
- Located alongside entities in `db/entities/`
- `MediaMetadata` in `app/src/main/kotlin/com/metrolist/music/models/MediaMetadata.kt` - intermediate between API and DB
- Include conversion extension functions: `fun Song.toMediaMetadata()`, `fun SongItem.toMediaMetadata()`
- Annotated with `@Immutable` for Compose stability
- Sealed class hierarchy: `sealed class YTItem` with `SongItem`, `AlbumItem`, `PlaylistItem`, `ArtistItem`, `PodcastItem`, `EpisodeItem` in `innertube/src/main/kotlin/com/metrolist/innertube/models/YTItem.kt`
- Request bodies in `models/body/` subdirectory: `BrowseBody`, `SearchBody`, `PlayerBody`
- Response models in `models/response/` subdirectory: `BrowseResponse`, `PlayerResponse`
- Spotify models in `spotify/src/main/kotlin/com/metrolist/spotify/models/`
- Use `kotlinx.serialization` for JSON serialization
- Top-level vals in `app/src/main/kotlin/com/metrolist/music/constants/PreferenceKeys.kt`
- Pattern: `val <Name>Key = <type>PreferencesKey("<camelCaseName>")`
- Related enums defined in same file or nearby constant files
## Dependency Injection
- `@HiltAndroidApp` on `App` class
- `@HiltViewModel` with `@Inject constructor` on ViewModels
- Modules as `object` with `@Module @InstallIn(SingletonComponent::class)`
- Custom qualifiers as annotations: `@PlayerCache`, `@DownloadCache`, `@ApplicationScope` in `app/src/main/kotlin/com/metrolist/music/di/Qualifiers.kt`
- Singleton scope: `@Singleton @Provides` for database, cache, network observers
- Modules: `AppModule.kt` (database, cache, coroutine scope), `NetworkModule.kt` (connectivity), `WrappedModule.kt`
## Error Handling
- Kotlin `Result<T>` for API calls: `YouTube.album(id)` returns `Result<AlbumPage>`
- `.onSuccess { }.onFailure { reportException(it) }` chain pattern
- `reportException()` is a thin wrapper around `printStackTrace()` - no crash reporting service
- `SilentHandler` coroutine exception handler in `app/src/main/kotlin/com/metrolist/music/extensions/CoroutineExt.kt` for silently swallowing exceptions
- Custom `CrashHandler` installed in `App.onCreate()` for fatal crashes
- Try-catch for specific operations (proxy setup, cookie parsing) with Timber logging
## Logging
- Initialize `Timber.plant(Timber.DebugTree())` in `App.onCreate()`
- Use `Timber.d()`, `Timber.e()`, `Timber.w()` with format strings
- Use `Timber.tag("TagName").d(message)` for categorized logging
- Tags: `"SpotifyAPI"`, `"HashSync"`, `"forgetAccount"`, etc.
- Prefer Timber over `Log.d()` or `println()`
## API Client Pattern
- API clients are Kotlin `object` singletons: `YouTube`, `Spotify`, `KuGou`, `LastFM`
- Configured globally via mutable properties: `YouTube.cookie = ...`, `YouTube.locale = ...`, `Spotify.accessToken = ...`
- Use `@Volatile` for thread-safe mutable properties
- HTTP client: Ktor with OkHttp engine
- JSON: `kotlinx.serialization` with `Json { ignoreUnknownKeys = true }`
- Return `Result<T>` for failable operations
- External modules do NOT use Hilt - they are plain Kotlin with `object` pattern
## Flavor/Build Variant Pattern
- `abi`: `universal`, `arm64`, `armeabi`, `x86`, `x86_64`
- `variant`: `foss` (default, F-Droid compatible), `gms` (Google Cast support)
- `app/src/foss/kotlin/` - FOSS-only implementations (no-op Cast stubs)
- `app/src/gms/kotlin/` - GMS-only implementations (real Cast support)
- `app/src/main/kotlin/` - Shared code
- Flavor-specific dependencies: `"gmsImplementation"(libs.media3.cast)`
## Database Patterns
- Single `InternalDatabase` (Room) wrapped by `MusicDatabase` which delegates to `DatabaseDao` interface
- `DatabaseDao.kt` is a massive 1747-line `@Dao` interface with queries, transactions, and helper methods
- `MusicDatabase` delegates via `DatabaseDao by delegate.dao`
- Transaction/query helpers: `database.transaction { ... }`, `database.query { ... }`
- Schema export: `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`
- Migrations: Mix of auto-migrations and manual `Migration` objects
## Resource Conventions
- Localized via Crowdin (see `crowdin.yml`)
- String resources in `values/strings.xml` and `values-<locale>/`
- Reference via `stringResource(R.string.<name>)` in Compose
- Use `pluralStringResource()` for count-dependent strings
- Vector drawables preferred
- Night variants in `drawable-night/`
- API-level variants: `drawable-v31/`
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

## Pattern Overview
- Single-Activity Jetpack Compose UI with NavHost-based navigation
- Hilt dependency injection throughout the app module
- Multi-module design: app module depends on standalone library modules for external API clients
- Media3 (ExoPlayer) `MediaLibraryService` for background audio playback
- DataStore Preferences for settings/configuration (not SharedPreferences)
- Room database for local persistence with a single monolithic DAO
- Library modules are pure API clients with no Android framework dependencies (except Android library plugin)
- Product flavors separate FOSS (F-Droid) and GMS (Google Cast) builds
## Layers
- Purpose: Render UI and handle user interactions
- Location: `app/src/main/kotlin/com/metrolist/music/ui/` and `app/src/main/kotlin/com/metrolist/music/viewmodels/`
- Contains: Composable screens, reusable components, menus, player UI, theme definitions
- Depends on: ViewModels, `PlayerConnection`, `MusicDatabase`
- Used by: `MainActivity` via NavHost
- Purpose: Hold UI state, orchestrate data loading from database and network
- Location: `app/src/main/kotlin/com/metrolist/music/viewmodels/`
- Contains: 36 HiltViewModels, each for a specific screen or feature
- Depends on: `MusicDatabase`, `YouTube` (innertube singleton), `Spotify` singleton, DataStore
- Used by: Compose screens via `hiltViewModel()`
- Purpose: Local persistence of songs, artists, albums, playlists, events, lyrics
- Location: `app/src/main/kotlin/com/metrolist/music/db/`
- Contains: Room database (`InternalDatabase`), single DAO (`DatabaseDao` - 1747 lines), entity classes, `MusicDatabase` wrapper
- Depends on: Room, entity models
- Used by: ViewModels, `MusicService`, `PlayerConnection`
- Purpose: Network communication with YouTube Music, Spotify, lyrics providers, etc.
- Location: Separate Gradle modules: `innertube/`, `spotify/`, `kugou/`, `lrclib/`, `betterlyrics/`, `simpmusic/`, `lastfm/`, `shazamkit/`, `kizzy/`
- Contains: Singleton API client objects, request/response models, page parsers
- Depends on: Ktor HTTP client, kotlinx.serialization
- Used by: ViewModels, `MusicService`, `LyricsHelper`
- Purpose: Audio playback, queue management, media session, notifications
- Location: `app/src/main/kotlin/com/metrolist/music/playback/`
- Contains: `MusicService` (3460 lines), `PlayerConnection`, queue implementations, download utilities
- Depends on: Media3/ExoPlayer, Room database, YouTube/Spotify API clients, DataStore
- Used by: `MainActivity` (via bound service), UI layer (via `PlayerConnection`)
- Purpose: Fetch synchronized/plain lyrics from multiple providers
- Location: `app/src/main/kotlin/com/metrolist/music/lyrics/`
- Contains: `LyricsHelper` (orchestrator), provider implementations, provider registry
- Depends on: External API modules (kugou, lrclib, betterlyrics, simpmusic), innertube
- Used by: `MusicService`, lyrics UI components
## Data Flow
- **UI state:** `MutableStateFlow` in ViewModels, collected via `collectAsState()` in Compose
- **Playback state:** `MutableStateFlow` in `PlayerConnection`, provided via `CompositionLocalProvider`
- **Settings/Preferences:** DataStore Preferences, accessed via `rememberPreference()` composable utility and `dataStore.get()` extension
- **Navigation state:** Jetpack Navigation Compose (`NavHostController`)
## Key Abstractions
- Purpose: Represents a playable sequence of media items with pagination support
- Examples: `app/src/main/kotlin/com/metrolist/music/playback/queues/YouTubeQueue.kt`, `SpotifyPlaylistQueue.kt`, `ListQueue.kt`, `YouTubeAlbumRadio.kt`
- Pattern: Strategy pattern - each queue type knows how to fetch its items and load more pages
- Purpose: Abstract lyrics fetching from multiple sources
- Examples: `app/src/main/kotlin/com/metrolist/music/lyrics/LrcLibLyricsProvider.kt`, `KuGouLyricsProvider.kt`, `BetterLyricsProvider.kt`
- Pattern: Strategy pattern with configurable provider ordering via `LyricsProviderRegistry`
- Purpose: Wraps Room's `InternalDatabase` to provide transaction helpers and delegate DAO methods
- Examples: `app/src/main/kotlin/com/metrolist/music/db/MusicDatabase.kt`
- Pattern: Delegation pattern - `MusicDatabase` implements `DatabaseDao by delegate.dao`
- Purpose: Unified metadata model bridging innertube/Spotify models with local database entities
- Examples: `app/src/main/kotlin/com/metrolist/music/models/MediaMetadata.kt`
- Pattern: Data class with conversion functions (`toSongEntity()`, `toMediaItem()`)
- Purpose: Bridge between UI and `MusicService`, exposes reactive player state
- Examples: `app/src/main/kotlin/com/metrolist/music/playback/PlayerConnection.kt`
- Pattern: Observer pattern via `Player.Listener` + StateFlow emissions
## Entry Points
- Location: `app/src/main/kotlin/com/metrolist/music/App.kt`
- Triggers: Application startup
- Responsibilities: Initialize Hilt, configure YouTube/Spotify/LastFM singletons, set up image loader, create notification channels, observe settings changes
- Location: `app/src/main/kotlin/com/metrolist/music/MainActivity.kt` (1302 lines)
- Triggers: App launch, deep links, intents
- Responsibilities: Set up Compose UI tree, bind to `MusicService`, manage navigation, handle deep links, provide `PlayerConnection` via CompositionLocal
- Location: `app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt` (3460 lines)
- Triggers: Bound by `MainActivity`, media session commands
- Responsibilities: ExoPlayer management, queue processing, stream URL resolution, media session, notifications, scrobbling, Discord RPC, download management, equalizer, crossfade, sleep timer
- Location: `app/src/main/kotlin/com/metrolist/music/ui/screens/NavigationBuilder.kt`
- Triggers: NavHost route changes
- Responsibilities: Define all routes and map them to Composable screens. Uses `NavGraphBuilder.navigationBuilder()` extension function
- Location: `app/src/main/kotlin/com/metrolist/music/ui/screens/Screens.kt`
- Triggers: Bottom navigation bar
- Responsibilities: Define main tabs: Home, Search, ListenTogether, Library
## Error Handling
- API calls return `Result<T>` (e.g., `YouTube.album()` returns `Result<AlbumPage>`)
- ViewModels call `.onSuccess { ... }` to update database and `.onFailure { reportException(it) }` for error logging
- `reportException()` utility in `app/src/main/kotlin/com/metrolist/music/utils/Utils.kt` for centralized error reporting
- `CrashHandler` in `app/src/main/kotlin/com/metrolist/music/utils/CrashHandler.kt` catches unhandled exceptions and shows a crash activity (`CrashActivity`)
- `PlayerConnection` has safe player accessor with `getPlayerSafe()` that handles `UninitializedPropertyAccessException`
## Dependency Injection
- `app/src/main/kotlin/com/metrolist/music/di/AppModule.kt` - Provides `MusicDatabase`, `InternalDatabase`, `DatabaseDao`, `SimpleCache` (player + download), `ListenTogetherClient/Manager`, `CoroutineScope`
- `app/src/main/kotlin/com/metrolist/music/di/NetworkModule.kt` - Provides `NetworkConnectivityObserver`
- `app/src/main/kotlin/com/metrolist/music/di/WrappedModule.kt` - Provides `WrappedManager`, `WrappedAudioService`
- `app/src/main/kotlin/com/metrolist/music/di/Qualifiers.kt` - Custom qualifiers: `@PlayerCache`, `@DownloadCache`, `@ApplicationScope`
- `app/src/main/kotlin/com/metrolist/music/di/LyricsHelperEntryPoint.kt` - Hilt EntryPoint for `LyricsHelper` (used outside DI-managed classes)
## Cross-Cutting Concerns
- Timber throughout the app (`Timber.d()`, `Timber.e()`, etc.)
- `DebugTree` planted in `App.onCreate()`
- Spotify API logging wired to Timber via `Spotify.logger` callback
- DataStore Preferences accessed via `dataStore` extension property on `Context`
- Preference keys defined in `app/src/main/kotlin/com/metrolist/music/constants/PreferenceKeys.kt`
- Compose screens use `rememberPreference()` and `rememberEnumPreference()` utilities from `app/src/main/kotlin/com/metrolist/music/utils/DataStore.kt`
- Coil 3 with custom `ImageLoader` (memory cache 25%, configurable disk cache)
- Configured in `App.newImageLoader()`
- YouTube: Cookie-based auth stored in DataStore, set on `YouTube.cookie`
- Spotify: OAuth token managed by `SpotifyTokenManager` in `app/src/main/kotlin/com/metrolist/music/utils/SpotifyTokenManager.kt`
- LastFM: Session key stored in DataStore
- Discord: Token stored in DataStore for RPC
- `foss` (default): No Google Play Services, cast stubs in `app/src/foss/`
- `gms`: Google Cast support via `CastManager` in `app/src/gms/`
- Both flavors provide `CastConnectionHandler`, `CastOptionsProvider`, and `CastButton` with different implementations
<!-- GSD:architecture-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd:quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd:debug` for investigation and bug fixing
- `/gsd:execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->



<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd:profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
