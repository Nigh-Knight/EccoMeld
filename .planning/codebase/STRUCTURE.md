# Codebase Structure

**Analysis Date:** 2026-04-03

## Directory Layout

```
EccoMeld/
├── app/                    # Main Android application module
├── innertube/              # YouTube Music InnerTube API client (103 Kotlin files)
├── spotify/                # Spotify Web API + GQL client (pure JVM module)
├── kugou/                  # KuGou lyrics API client
├── lrclib/                 # LRCLIB lyrics API client
├── betterlyrics/           # BetterLyrics API client + TTML parser
├── simpmusic/              # SimpMusic lyrics API client
├── lastfm/                 # Last.fm scrobbling/auth API client
├── shazamkit/              # Shazam song recognition API client
├── kizzy/                  # Discord Rich Presence (Kizzy RPC) client
├── metroproto/             # Protobuf definitions (currently empty/placeholder)
├── assets/                 # Static assets (icons, screenshots)
├── docs/                   # Documentation
├── notes/                  # Development notes
├── fastlane/               # Fastlane metadata for app stores
├── .github/                # GitHub Actions workflows, issue templates
├── .planning/              # GSD planning documents
├── gradle/                 # Gradle wrapper
├── build.gradle.kts        # Root build config (Hilt, KSP, serialization plugins)
├── settings.gradle.kts     # Module includes, repository config
├── gradle.properties       # Gradle JVM args, Android settings
├── PRD.md                  # Product Requirements Document (EccoMeld rebrand)
├── AGENTS.md               # Claude Code agent instructions
└── README.md               # Project documentation
```

## Module Dependency Graph

```
app (Android Application)
 ├── innertube       (YouTube Music API - Android Library)
 ├── spotify         (Spotify API - Pure JVM/Kotlin)
 ├── kugou           (KuGou lyrics - Android Library)
 ├── lrclib          (LRCLIB lyrics - Android Library)
 ├── betterlyrics    (BetterLyrics - Android Library)
 ├── simpmusic       (SimpMusic lyrics - Android Library)
 ├── lastfm          (Last.fm - Android Library)
 ├── shazamkit       (Shazam recognition - Android Library)
 └── kizzy           (Discord RPC - Android Library)

All library modules are independent (no inter-module dependencies).
All use Ktor HTTP client + kotlinx.serialization.
spotify is the only pure JVM module (kotlin("jvm")); all others use com.android.library.
```

## App Module Package Structure

```
app/src/main/kotlin/com/metrolist/music/
├── App.kt                          # Application class (@HiltAndroidApp)
├── MainActivity.kt                 # Single Activity (1302 lines)
├── api/                            # External API service wrappers
│   ├── DeepLService.kt             #   Translation API
│   ├── OpenRouterService.kt        #   AI/LLM API
│   └── OpenRouterStreamingService.kt
├── constants/                      # Preference keys, enums, dimensions
│   ├── PreferenceKeys.kt           #   DataStore preference key definitions
│   ├── Dimensions.kt               #   UI dimension constants
│   ├── HistorySource.kt            #   History source enum
│   ├── LibraryFilter.kt            #   Library filter enum
│   ├── MediaSessionConstants.kt    #   Media session command constants
│   └── StatPeriod.kt               #   Statistics period enum
├── db/                             # Room database layer
│   ├── MusicDatabase.kt            #   Database wrapper with transaction helpers
│   ├── DatabaseDao.kt              #   Single DAO interface (1747 lines)
│   ├── Converters.kt               #   Room type converters
│   ├── entities/                    #   31 entity/model classes
│   │   ├── SongEntity.kt
│   │   ├── ArtistEntity.kt
│   │   ├── AlbumEntity.kt
│   │   ├── PlaylistEntity.kt
│   │   ├── SpotifyMatchEntity.kt
│   │   ├── SpeedDialItem.kt
│   │   └── ... (26 more)
│   └── daos/
│       └── SpeedDialDao.kt         #   Secondary DAO for speed dial
├── di/                             # Hilt dependency injection
│   ├── AppModule.kt                #   Core singletons (DB, cache, scope)
│   ├── NetworkModule.kt            #   Network connectivity observer
│   ├── WrappedModule.kt            #   Wrapped/year-in-review feature
│   ├── Qualifiers.kt               #   @PlayerCache, @DownloadCache, @ApplicationScope
│   └── LyricsHelperEntryPoint.kt   #   EntryPoint for non-DI access to LyricsHelper
├── eq/                             # Audio equalizer system
│   ├── EqualizerService.kt
│   ├── audio/                      #   Custom audio processors
│   │   ├── BiquadFilter.kt
│   │   ├── CustomEqualizerAudioProcessor.kt
│   │   ├── FilterType.kt
│   │   ├── ParametricEQ.kt
│   │   └── ParametricEQParser.kt
│   └── data/
│       └── EQProfileRepository.kt
├── extensions/                     # Kotlin extension functions
│   ├── ContextExt.kt
│   ├── CoroutineExt.kt
│   ├── FileExt.kt
│   ├── ListExt.kt
│   ├── MediaItemExt.kt
│   ├── PlayerExt.kt
│   ├── QueueExt.kt
│   ├── StringExt.kt
│   └── UtilExt.kt
├── listentogether/                 # Listen Together (multiplayer sync)
│   ├── ListenTogetherClient.kt
│   ├── ListenTogetherManager.kt
│   ├── ListenTogetherServers.kt
│   ├── ListenTogetherActionReceiver.kt
│   ├── MessageCodec.kt
│   └── Protocol.kt
├── lyrics/                         # Multi-provider lyrics system
│   ├── LyricsHelper.kt            #   Orchestrator - tries providers in order
│   ├── LyricsProvider.kt          #   Provider interface
│   ├── LyricsProviderRegistry.kt  #   Configurable provider ordering
│   ├── LyricsEntry.kt
│   ├── LyricsUtils.kt
│   ├── LyricsTranslationHelper.kt
│   ├── BetterLyricsProvider.kt
│   ├── KuGouLyricsProvider.kt
│   ├── LrcLibLyricsProvider.kt
│   ├── LyricsPlusProvider.kt
│   ├── SimpMusicLyricsProvider.kt
│   ├── YouTubeLyricsProvider.kt
│   └── YouTubeSubtitleLyricsProvider.kt
├── models/                         # Domain/UI models
│   ├── MediaMetadata.kt           #   Central metadata model
│   ├── PersistQueue.kt            #   Queue serialization for persistence
│   ├── PersistPlayerState.kt
│   ├── ItemsPage.kt
│   ├── NewReleaseItem.kt
│   ├── SimilarRecommendation.kt
│   └── SpotifyHomeSection.kt
├── playback/                       # Media playback engine
│   ├── MusicService.kt            #   MediaLibraryService (3460 lines)
│   ├── PlayerConnection.kt        #   UI-service bridge with reactive state
│   ├── MediaLibrarySessionCallback.kt
│   ├── DownloadUtil.kt
│   ├── ExoDownloadService.kt
│   ├── SleepTimer.kt
│   ├── SpotifyProfileCache.kt
│   ├── SpotifyRecommendationEngine.kt
│   ├── SpotifyYouTubeMapper.kt
│   ├── audio/
│   │   └── SilenceDetectorAudioProcessor.kt
│   └── queues/                     #   Queue strategy implementations
│       ├── Queue.kt               #     Queue interface
│       ├── EmptyQueue.kt
│       ├── ListQueue.kt
│       ├── YouTubeQueue.kt
│       ├── YouTubePlaylistQueue.kt
│       ├── YouTubeAlbumRadio.kt
│       ├── LocalAlbumRadio.kt
│       ├── SpotifyQueue.kt
│       ├── SpotifyPlaylistQueue.kt
│       └── SpotifyLikedSongsQueue.kt
├── recognition/                    # Song recognition (Shazam-like)
│   ├── MusicRecognitionService.kt
│   ├── ShazamSignatureGenerator.kt
│   ├── AudioResampler.kt
│   └── VibraSignature.kt
├── ui/                             # Jetpack Compose UI
│   ├── component/                  #   Reusable UI components (40+ files)
│   │   ├── BottomSheet.kt
│   │   ├── BottomSheetMenu.kt
│   │   ├── Items.kt
│   │   ├── Lyrics.kt
│   │   ├── SearchBar.kt
│   │   ├── PlayerSlider.kt
│   │   ├── SpotifyHomeSectionRow.kt
│   │   ├── shimmer/               #     Loading shimmer effects
│   │   └── ... (30+ more)
│   ├── menu/                       #   Context menus (23 files)
│   │   ├── SongMenu.kt
│   │   ├── AlbumMenu.kt
│   │   ├── PlayerMenu.kt
│   │   ├── SpotifyTrackMenu.kt
│   │   └── ... (19 more)
│   ├── player/                     #   Player UI
│   │   ├── Player.kt             #     Full-screen player
│   │   ├── MiniPlayer.kt         #     Mini player bar
│   │   ├── Queue.kt              #     Queue display
│   │   ├── Thumbnail.kt
│   │   ├── ThumbnailSnapUtils.kt
│   │   └── PlaybackError.kt
│   ├── screens/                    #   Screen composables
│   │   ├── Screens.kt            #     Main tab definitions
│   │   ├── NavigationBuilder.kt  #     Route-to-screen mapping
│   │   ├── HomeScreen.kt
│   │   ├── ExploreScreen.kt
│   │   ├── BrowseScreen.kt
│   │   ├── ChartsScreen.kt
│   │   ├── HistoryScreen.kt
│   │   ├── StatsScreen.kt
│   │   ├── AlbumScreen.kt
│   │   ├── LoginScreen.kt
│   │   ├── SpotifyLoginScreen.kt
│   │   ├── ListenTogetherScreen.kt
│   │   ├── MoodAndGenresScreen.kt
│   │   ├── NewReleaseScreen.kt
│   │   ├── YouTubeBrowseScreen.kt
│   │   ├── CrashActivity.kt
│   │   ├── album/                #     Album sub-screens
│   │   ├── artist/               #     Artist sub-screens
│   │   ├── playlist/             #     Playlist sub-screens
│   │   ├── podcast/              #     Podcast sub-screens
│   │   ├── search/               #     Search screens
│   │   ├── library/              #     Library tab screens
│   │   │   ├── LibraryScreen.kt
│   │   │   ├── LibrarySongsScreen.kt
│   │   │   ├── LibraryAlbumsScreen.kt
│   │   │   ├── LibraryArtistsScreen.kt
│   │   │   ├── LibraryPlaylistsScreen.kt
│   │   │   ├── LibraryPodcastsScreen.kt
│   │   │   ├── LibraryMixScreen.kt
│   │   │   └── local/           #      Local file browser
│   │   ├── settings/             #     Settings screens
│   │   │   ├── SettingsScreen.kt
│   │   │   ├── AppearanceSettings.kt
│   │   │   ├── PlayerSettings.kt
│   │   │   ├── ContentSettings.kt
│   │   │   ├── PrivacySettings.kt
│   │   │   ├── StorageSettings.kt
│   │   │   ├── BackupAndRestore.kt
│   │   │   ├── AiSettings.kt
│   │   │   ├── ThemeScreen.kt
│   │   │   ├── integrations/    #      Third-party integration settings
│   │   │   │   ├── IntegrationScreen.kt
│   │   │   │   ├── SpotifySettings.kt
│   │   │   │   ├── LastFMSettings.kt
│   │   │   │   ├── DiscordSettings.kt
│   │   │   │   ├── ListenTogetherSettings.kt
│   │   │   │   └── SpotifyPreloadScreen.kt
│   │   │   └── ...
│   │   ├── recognition/          #     Song recognition screens
│   │   ├── equalizer/            #     Equalizer screen
│   │   └── wrapped/              #     Year-in-review feature
│   ├── theme/                      #   Material 3 theming
│   │   ├── Theme.kt
│   │   ├── Type.kt
│   │   ├── Font.kt
│   │   ├── PlayerColorExtractor.kt
│   │   ├── PlayerSliderColors.kt
│   │   └── bbh_bartle.kt
│   └── utils/                      #   UI utility functions
├── utils/                          # General utilities
│   ├── DataStore.kt               #   DataStore helpers, rememberPreference()
│   ├── SpotifyTokenManager.kt    #   OAuth token lifecycle
│   ├── SpotifyHashSync.kt        #   GQL hash synchronization
│   ├── SpotifyItemConverter.kt   #   Spotify-to-local model conversion
│   ├── SyncUtils.kt              #   YouTube Music library sync
│   ├── DiscordRPC.kt             #   Discord Rich Presence
│   ├── ScrobbleManager.kt        #   Last.fm scrobbling logic
│   ├── NetworkConnectivityObserver.kt
│   ├── Updater.kt                #   In-app update checker
│   ├── CrashHandler.kt           #   Custom crash handler
│   ├── YTPlayerUtils.kt          #   YouTube player URL resolution
│   ├── cipher/                    #   YouTube cipher/signature handling
│   │   ├── CipherManager.kt
│   │   ├── SignatureDeobfuscator.kt
│   │   ├── NTransformSolver.kt
│   │   ├── PlayerJsFetcher.kt
│   │   └── FunctionNameExtractor.kt
│   └── ... (more utilities)
├── viewmodels/                     # All ViewModels (36 files)
│   ├── HomeViewModel.kt
│   ├── AlbumViewModel.kt
│   ├── ArtistViewModel.kt
│   ├── SpotifyViewModel.kt
│   ├── OnlineSearchViewModel.kt
│   ├── LibraryViewModels.kt      #   Multiple library-related VMs in one file
│   ├── LocalFilesViewModel.kt
│   └── ... (29 more)
└── widget/                         # Android App Widgets
    ├── MusicWidgetReceiver.kt
    ├── MetrolistWidgetManager.kt
    ├── TurntableWidgetReceiver.kt
    ├── MusicRecognizerWidgetReceiver.kt
    └── MusicRecognizerWidgetService.kt
```

## Product Flavor Source Sets

```
app/src/foss/kotlin/com/metrolist/music/    # FOSS flavor (F-Droid, no GMS)
├── cast/CastOptionsProvider.kt              #   No-op cast provider
├── playback/CastConnectionHandler.kt        #   No-op cast handler
└── ui/component/CastButton.kt              #   Hidden/no-op cast button

app/src/gms/kotlin/com/metrolist/music/     # GMS flavor (Google Cast)
├── cast/CastOptionsProvider.kt              #   Real Google Cast options
├── cast/CastManager.kt                     #   Cast session management
├── playback/CastConnectionHandler.kt        #   Real cast connection handling
└── ui/component/CastButton.kt              #   Functional cast button
    ui/component/CastPickerSheet.kt          #   Cast device picker UI
```

## Library Module Structures

**innertube/** (largest library module, 103 files):
```
innertube/src/main/kotlin/com/metrolist/innertube/
├── YouTube.kt              # Main singleton API client object
├── models/                 # InnerTube data models
│   ├── body/               #   Request body models
│   └── response/           #   Response models
├── pages/                  # Page parsers (HomePage, AlbumPage, ArtistPage, etc.)
└── utils/                  # Parsing utilities
```

**spotify/** (pure JVM):
```
spotify/src/main/kotlin/com/metrolist/spotify/
├── Spotify.kt              # Main singleton API client (GQL + REST)
├── SpotifyAuth.kt          # OAuth authentication
├── SpotifyMapper.kt        # Response mapping
├── SpotifyHashProvider.kt  # GQL operation hash management
└── models/                 # Data models (track, album, playlist, etc.)
```

**Other library modules** follow the same pattern: a main singleton object + models directory.

## Key File Locations

**Entry Points:**
- `app/src/main/kotlin/com/metrolist/music/App.kt`: Application initialization
- `app/src/main/kotlin/com/metrolist/music/MainActivity.kt`: UI entry point
- `app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt`: Background service
- `app/src/main/AndroidManifest.xml`: Android manifest

**Configuration:**
- `app/build.gradle.kts`: App module build config (versions, flavors, dependencies)
- `build.gradle.kts`: Root build config
- `settings.gradle.kts`: Module includes
- `gradle/libs.versions.toml`: Version catalog (assumed, standard Gradle convention)
- `app/src/main/kotlin/com/metrolist/music/constants/PreferenceKeys.kt`: All DataStore preference keys

**Core Logic:**
- `app/src/main/kotlin/com/metrolist/music/db/DatabaseDao.kt`: All database queries (1747 lines)
- `app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt`: Playback engine (3460 lines)
- `innertube/src/main/kotlin/com/metrolist/innertube/YouTube.kt`: YouTube Music API
- `spotify/src/main/kotlin/com/metrolist/spotify/Spotify.kt`: Spotify API

**Resources:**
- `app/src/main/res/values/strings.xml`: Default strings
- `app/src/main/res/values-*/`: Translations (50+ locales)
- `app/src/main/res/drawable/`: Icons and drawables
- `app/src/main/res/xml/`: Widget configs, network security, shortcuts
- `app/schemas/`: Room database schema exports (for migration testing)

## Naming Conventions

**Files:**
- Screens: `{Feature}Screen.kt` (e.g., `HomeScreen.kt`, `AlbumScreen.kt`)
- ViewModels: `{Feature}ViewModel.kt` (e.g., `HomeViewModel.kt`, `AlbumViewModel.kt`)
- Entities: `{Name}Entity.kt` for Room entities, `{Name}.kt` for domain models with relations
- Menus: `{Type}Menu.kt` (e.g., `SongMenu.kt`, `AlbumMenu.kt`)
- Extensions: `{Type}Ext.kt` (e.g., `PlayerExt.kt`, `StringExt.kt`)
- Queue implementations: `{Source}Queue.kt` (e.g., `YouTubeQueue.kt`, `SpotifyPlaylistQueue.kt`)

**Directories:**
- Feature-based grouping under `ui/screens/` (e.g., `album/`, `artist/`, `settings/`)
- Layer-based grouping at top level (`db/`, `di/`, `playback/`, `viewmodels/`)

## Where to Add New Code

**New Screen:**
- Screen composable: `app/src/main/kotlin/com/metrolist/music/ui/screens/{FeatureName}Screen.kt`
- ViewModel: `app/src/main/kotlin/com/metrolist/music/viewmodels/{FeatureName}ViewModel.kt`
- Navigation route: Add `composable(...)` in `app/src/main/kotlin/com/metrolist/music/ui/screens/NavigationBuilder.kt`
- If it's a main tab: Update `Screens.kt` and `Screens.MainScreens`

**New Reusable UI Component:**
- `app/src/main/kotlin/com/metrolist/music/ui/component/{ComponentName}.kt`

**New Context Menu:**
- `app/src/main/kotlin/com/metrolist/music/ui/menu/{Type}Menu.kt`

**New Database Entity:**
- Entity class: `app/src/main/kotlin/com/metrolist/music/db/entities/{Name}Entity.kt`
- Add to `@Database(entities = [...])` in `app/src/main/kotlin/com/metrolist/music/db/MusicDatabase.kt`
- Add queries to `app/src/main/kotlin/com/metrolist/music/db/DatabaseDao.kt`
- Create Room migration in `MusicDatabase.kt`

**New Lyrics Provider:**
- Provider: `app/src/main/kotlin/com/metrolist/music/lyrics/{Name}LyricsProvider.kt` (implement `LyricsProvider`)
- Register in `app/src/main/kotlin/com/metrolist/music/lyrics/LyricsProviderRegistry.kt`
- Add to default list in `app/src/main/kotlin/com/metrolist/music/lyrics/LyricsHelper.kt`

**New Queue Type:**
- `app/src/main/kotlin/com/metrolist/music/playback/queues/{Source}Queue.kt` (implement `Queue` interface)

**New External API Client:**
- Create new Gradle module directory at project root
- Add `build.gradle.kts` with `com.android.library` or `kotlin("jvm")` plugin
- Add `include(":modulename")` to `settings.gradle.kts`
- Add `implementation(project(":modulename"))` to `app/build.gradle.kts`

**New Hilt Provider:**
- Add to existing module in `app/src/main/kotlin/com/metrolist/music/di/` or create new `@Module`

**New Preference/Setting:**
- Key: `app/src/main/kotlin/com/metrolist/music/constants/PreferenceKeys.kt`
- UI: Appropriate settings screen in `app/src/main/kotlin/com/metrolist/music/ui/screens/settings/`

**New Extension Functions:**
- `app/src/main/kotlin/com/metrolist/music/extensions/{Type}Ext.kt`

**New Utility:**
- `app/src/main/kotlin/com/metrolist/music/utils/{UtilityName}.kt`

## Special Directories

**`app/schemas/`:**
- Purpose: Exported Room database schemas (JSON) for migration testing
- Generated: Yes, by Room KSP processor (`room.schemaLocation`)
- Committed: Yes

**`metroproto/`:**
- Purpose: Protobuf definitions (appears to be a placeholder/future module)
- Generated: N/A
- Committed: Yes, but no source files present

**`fastlane/metadata/`:**
- Purpose: App store metadata for automated releases
- Generated: No
- Committed: Yes

**`.github/`:**
- Purpose: CI workflows, issue templates, PR templates
- Generated: No
- Committed: Yes

---

*Structure analysis: 2026-04-03*
