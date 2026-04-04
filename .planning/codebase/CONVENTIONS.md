# Coding Conventions

**Analysis Date:** 2026-04-03

## Naming Patterns

**Packages:**
- Root package: `com.metrolist.music` (app module)
- Library modules: `com.metrolist.<module>` (e.g., `com.metrolist.innertube`, `com.metrolist.spotify`, `com.metrolist.kugou`)
- Feature-based sub-packages under app: `ui`, `db`, `viewmodels`, `models`, `playback`, `utils`, `di`, `constants`, `extensions`, `recognition`, `eq`, `api`, `widget`
- UI sub-packages: `ui.screens`, `ui.component`, `ui.menu`, `ui.player`, `ui.theme`, `ui.utils`
- Screen sub-packages by feature: `ui.screens.settings`, `ui.screens.playlist`, `ui.screens.album`, `ui.screens.artist`, `ui.screens.search`, `ui.screens.wrapped`, `ui.screens.library`, `ui.screens.equalizer`, `ui.screens.podcast`, `ui.screens.recognition`

**Files:**
- Kotlin source files use PascalCase: `HomeScreen.kt`, `MusicService.kt`, `AlbumViewModel.kt`
- Extension files use PascalCase with `Ext` suffix: `CoroutineExt.kt`, `StringExt.kt`, `MediaItemExt.kt`, `ContextExt.kt`
- Utility files use PascalCase: `DataStore.kt`, `SyncUtils.kt`, `NetworkUtils.kt`
- Constants in dedicated file: `PreferenceKeys.kt` in `constants/` package
- Entity files: separate `<Name>Entity.kt` (Room entity) and `<Name>.kt` (composite with relations)

**Classes:**
- ViewModels: PascalCase with `ViewModel` suffix: `HomeViewModel`, `AlbumViewModel`, `SpotifyPlaylistViewModel`
- Entities: PascalCase with `Entity` suffix for Room entities: `SongEntity`, `AlbumEntity`, `PlaylistEntity`
- Composables: PascalCase function names matching file names: `HomeScreen()`, `PreferenceEntry()`, `MiniPlayer()`
- Sealed classes for hierarchies: `sealed class YTItem`, `sealed class LocalItem`
- Data classes for models: `data class MediaMetadata`, `data class DailyDiscoverItem`
- Object singletons for API clients: `object Spotify`, `object YouTube`, `object KuGou`, `object LastFM`

**Functions:**
- camelCase for all functions
- Composable functions use PascalCase (Compose convention): `HomeScreen()`, `PreferenceEntry()`, `MetrolistTheme()`
- Extension functions: `fun Song.toMediaMetadata()`, `fun SongItem.toMediaMetadata()`
- Conversion functions: `to<Target>()` pattern: `toSongEntity()`, `toMediaMetadata()`, `asPlaylistItem()`, `asSongItem()`
- Toggle functions: `toggleLike()`, `toggleLibrary()`, `toggleUploaded()`, `localToggleLike()`
- Prefix `local` for operations that skip remote sync: `localToggleLike()` vs `toggleLike()`

**Variables:**
- camelCase for all variables and properties
- MutableStateFlow fields: descriptive camelCase: `isRefreshing`, `quickPicks`, `homePage`
- Preference keys: PascalCase with `Key` suffix as top-level vals: `DynamicThemeKey`, `PureBlackKey`, `MaxSongCacheSizeKey`
- Constants: UPPER_SNAKE_CASE for companion object constants: `LIKED_PLAYLIST_ID`, `DB_NAME`

**Types:**
- Enums: PascalCase class name, UPPER_SNAKE_CASE entries: `enum class SliderStyle { DEFAULT, WAVY, SLIM }`
- Exception: some enums use PascalCase entries: `enum class DensityScale(val value: Float, val label: String)`
- Custom annotations: PascalCase: `@PlayerCache`, `@DownloadCache`, `@ApplicationScope`

## Code Style

**Formatting:**
- No dedicated formatter configured (no ktlint, detekt, or .editorconfig detected)
- Android Lint is configured in `lint.xml` at root level, referenced in `app/build.gradle.kts`
- Lint settings: `warningsAsErrors = false`, `abortOnError = false`, `checkDependencies = false`
- Lint suppresses `UnsafeOptInUsageError` for Media3 unstable APIs
- Kotlin compiler: `suppressWarnings = false`, opt-in for `kotlin.RequiresOptIn`

**Linting:**
- Android Lint only, run as part of CI builds alongside `assemble` tasks
- No static analysis tools (detekt, ktlint, spotless) configured
- Compose compiler reports/metrics available via `enableComposeCompilerReports` property

## File Headers

Use this header on all new Kotlin files in the app module:
```kotlin
/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
```
Library modules (innertube, spotify, etc.) do not consistently use this header.

## Import Organization

**Order:**
1. Android framework imports (`android.*`)
2. AndroidX imports (`androidx.*`)
3. Third-party library imports (`com.google.*`, `io.ktor.*`, `coil3.*`, etc.)
4. Project module imports (`com.metrolist.innertube.*`, `com.metrolist.spotify.*`)
5. App internal imports (`com.metrolist.music.*`)
6. Kotlin stdlib imports (`kotlinx.*`, `kotlin.*`)
7. Java stdlib imports (`java.*`, `javax.*`)

**Wildcard imports:**
- Used occasionally for constants: `import com.metrolist.music.constants.*`
- Avoid wildcard imports elsewhere

**Path Aliases:**
- No path aliases. Modules are referenced by project dependency: `implementation(project(":innertube"))`

## Coroutine Patterns

**Scope Management:**
- Application-wide scope via Hilt: `@ApplicationScope CoroutineScope` with `SupervisorJob() + Dispatchers.Default` (`app/src/main/kotlin/com/metrolist/music/di/AppModule.kt`)
- ViewModel scope: `viewModelScope` from AndroidX Lifecycle
- Composable scope: `rememberCoroutineScope()` for UI-triggered operations

**Dispatcher Usage:**
- `Dispatchers.IO` for network calls, database operations, and file I/O
- `Dispatchers.Main` for UI-thread operations (Toast, WebView)
- `Dispatchers.Default` for application scope (CPU-bound work)
- Always specify dispatcher explicitly with `launch(Dispatchers.IO) { ... }`

**Flow Patterns:**
- `MutableStateFlow` for ViewModel state: `val quickPicks = MutableStateFlow<List<Song>?>(null)`
- `stateIn()` to convert database Flow to StateFlow: `database.albumWithSongs(id).stateIn(viewModelScope, SharingStarted.Eagerly, null)`
- `distinctUntilChanged()` on DataStore flows to avoid redundant emissions
- `collectAsState()` in Composables to observe StateFlows
- `combine()` for merging multiple flows
- Extension functions in `CoroutineExt.kt` for scoped collection: `flow.collect(scope) { ... }`, `flow.collectLatest(scope) { ... }`

**Result Handling:**
- YouTube API returns `Result<T>` from Kotlin stdlib
- Use `.onSuccess { }` and `.onFailure { }` chaining pattern:
```kotlin
YouTube.album(albumId)
    .onSuccess { /* process */ }
    .onFailure { reportException(it) }
```
- `reportException()` in `app/src/main/kotlin/com/metrolist/music/utils/Utils.kt` is a simple `printStackTrace()` wrapper

## Compose Conventions

**Screen Components:**
- Screen composables are top-level functions named `<Feature>Screen`
- Screens receive `navController: NavController` as parameter
- Screens receive `scrollBehavior: TopAppBarScrollBehavior` when they have scrollable content
- ViewModels obtained via `hiltViewModel<T>()` inside the composable
- State observed via `val state by viewModel.stateFlow.collectAsState()`

**State Management:**
- Preferences via custom `rememberPreference(key, defaultValue)` returning `MutableState<T>` (`app/src/main/kotlin/com/metrolist/music/utils/DataStore.kt`)
- Enum preferences via `rememberEnumPreference(key, defaultValue)`
- Local UI state via `remember { mutableStateOf(...) }` and `rememberSaveable { ... }`
- Side effects via `LaunchedEffect`, `DisposableEffect`

**Theming:**
- Custom theme wrapper: `MetrolistTheme()` in `app/src/main/kotlin/com/metrolist/music/ui/theme/Theme.kt`
- Supports dynamic colors (Android 12+ wallpaper), custom seed color, pure black mode
- Uses materialKolor library for non-system dynamic color generation
- Default theme color: `val DefaultThemeColor = Color(0xFFED5564)`
- Access colors via `MaterialTheme.colorScheme`

**Component Patterns:**
- Reusable components in `ui/component/`: `PreferenceEntry`, `IconButton`, `NavigationTitle`, `Items`
- Shimmer placeholders in `ui/component/shimmer/`: `GridItemPlaceholder`, `ListItemPlaceholder`
- Menu composables in `ui/menu/`: `SongMenu`, `AlbumMenu`, `PlayerMenu` - context menus as bottom sheets
- Player UI in `ui/player/`: `Player`, `MiniPlayer`, `Queue`, `Thumbnail`

**Navigation:**
- Navigation routes defined in a `Screens` sealed class/enum
- Route-based navigation with string arguments: `composable(Screens.Home.route) { ... }`
- Navigation builder as extension function: `NavGraphBuilder.navigationBuilder()` in `app/src/main/kotlin/com/metrolist/music/ui/screens/NavigationBuilder.kt`
- Arguments extracted via `SavedStateHandle` in ViewModels: `savedStateHandle.get<String>("albumId")!!`

**CompositionLocal:**
- `LocalPlayerAwareWindowInsets` for insets that account for mini player/bottom sheet
- Standard `LocalContext`, `LocalDensity`, `LocalHapticFeedback`

## Data Class Patterns

**Room Entities (DB layer):**
- Located in `app/src/main/kotlin/com/metrolist/music/db/entities/`
- Annotated with `@Entity(tableName = "...")`, `@Immutable`
- Use `@PrimaryKey val id: String` (YouTube video/playlist/album IDs)
- Use `@ColumnInfo(defaultValue = "...")` for migration-safe defaults
- Include business logic methods: `toggleLike()`, `toggleLibrary()`
- Use `java.time.LocalDateTime` for timestamps (with core library desugaring)

**Composite Entities (with relations):**
- Wrap entity + relations: `data class Song(@Embedded val song: SongEntity, @Relation ... val artists: List<ArtistEntity>)`
- Extend `sealed class LocalItem` for polymorphism
- Located alongside entities in `db/entities/`

**Domain/Transfer Models:**
- `MediaMetadata` in `app/src/main/kotlin/com/metrolist/music/models/MediaMetadata.kt` - intermediate between API and DB
- Include conversion extension functions: `fun Song.toMediaMetadata()`, `fun SongItem.toMediaMetadata()`
- Annotated with `@Immutable` for Compose stability

**API Models (library modules):**
- Sealed class hierarchy: `sealed class YTItem` with `SongItem`, `AlbumItem`, `PlaylistItem`, `ArtistItem`, `PodcastItem`, `EpisodeItem` in `innertube/src/main/kotlin/com/metrolist/innertube/models/YTItem.kt`
- Request bodies in `models/body/` subdirectory: `BrowseBody`, `SearchBody`, `PlayerBody`
- Response models in `models/response/` subdirectory: `BrowseResponse`, `PlayerResponse`
- Spotify models in `spotify/src/main/kotlin/com/metrolist/spotify/models/`
- Use `kotlinx.serialization` for JSON serialization

**Preference Keys:**
- Top-level vals in `app/src/main/kotlin/com/metrolist/music/constants/PreferenceKeys.kt`
- Pattern: `val <Name>Key = <type>PreferencesKey("<camelCaseName>")`
- Related enums defined in same file or nearby constant files

## Dependency Injection

**Framework:** Hilt (Dagger)

**Patterns:**
- `@HiltAndroidApp` on `App` class
- `@HiltViewModel` with `@Inject constructor` on ViewModels
- Modules as `object` with `@Module @InstallIn(SingletonComponent::class)`
- Custom qualifiers as annotations: `@PlayerCache`, `@DownloadCache`, `@ApplicationScope` in `app/src/main/kotlin/com/metrolist/music/di/Qualifiers.kt`
- Singleton scope: `@Singleton @Provides` for database, cache, network observers
- Modules: `AppModule.kt` (database, cache, coroutine scope), `NetworkModule.kt` (connectivity), `WrappedModule.kt`

## Error Handling

**Patterns:**
- Kotlin `Result<T>` for API calls: `YouTube.album(id)` returns `Result<AlbumPage>`
- `.onSuccess { }.onFailure { reportException(it) }` chain pattern
- `reportException()` is a thin wrapper around `printStackTrace()` - no crash reporting service
- `SilentHandler` coroutine exception handler in `app/src/main/kotlin/com/metrolist/music/extensions/CoroutineExt.kt` for silently swallowing exceptions
- Custom `CrashHandler` installed in `App.onCreate()` for fatal crashes
- Try-catch for specific operations (proxy setup, cookie parsing) with Timber logging

## Logging

**Framework:** Timber (`com.jakewharton.timber:timber`)

**Patterns:**
- Initialize `Timber.plant(Timber.DebugTree())` in `App.onCreate()`
- Use `Timber.d()`, `Timber.e()`, `Timber.w()` with format strings
- Use `Timber.tag("TagName").d(message)` for categorized logging
- Tags: `"SpotifyAPI"`, `"HashSync"`, `"forgetAccount"`, etc.
- Prefer Timber over `Log.d()` or `println()`

## API Client Pattern

**Singleton Object Pattern:**
- API clients are Kotlin `object` singletons: `YouTube`, `Spotify`, `KuGou`, `LastFM`
- Configured globally via mutable properties: `YouTube.cookie = ...`, `YouTube.locale = ...`, `Spotify.accessToken = ...`
- Use `@Volatile` for thread-safe mutable properties
- HTTP client: Ktor with OkHttp engine
- JSON: `kotlinx.serialization` with `Json { ignoreUnknownKeys = true }`
- Return `Result<T>` for failable operations
- External modules do NOT use Hilt - they are plain Kotlin with `object` pattern

## Flavor/Build Variant Pattern

**Dimensions:**
- `abi`: `universal`, `arm64`, `armeabi`, `x86`, `x86_64`
- `variant`: `foss` (default, F-Droid compatible), `gms` (Google Cast support)

**Flavor Source Sets:**
- `app/src/foss/kotlin/` - FOSS-only implementations (no-op Cast stubs)
- `app/src/gms/kotlin/` - GMS-only implementations (real Cast support)
- `app/src/main/kotlin/` - Shared code
- Flavor-specific dependencies: `"gmsImplementation"(libs.media3.cast)`

## Database Patterns

**Room Setup:**
- Single `InternalDatabase` (Room) wrapped by `MusicDatabase` which delegates to `DatabaseDao` interface
- `DatabaseDao.kt` is a massive 1747-line `@Dao` interface with queries, transactions, and helper methods
- `MusicDatabase` delegates via `DatabaseDao by delegate.dao`
- Transaction/query helpers: `database.transaction { ... }`, `database.query { ... }`
- Schema export: `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`
- Migrations: Mix of auto-migrations and manual `Migration` objects

## Resource Conventions

**Strings:**
- Localized via Crowdin (see `crowdin.yml`)
- String resources in `values/strings.xml` and `values-<locale>/`
- Reference via `stringResource(R.string.<name>)` in Compose
- Use `pluralStringResource()` for count-dependent strings

**Drawables:**
- Vector drawables preferred
- Night variants in `drawable-night/`
- API-level variants: `drawable-v31/`

---

*Convention analysis: 2026-04-03*
