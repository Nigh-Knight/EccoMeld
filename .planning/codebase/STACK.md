# Technology Stack

**Analysis Date:** 2026-04-03

## Languages

**Primary:**
- Kotlin 2.3.10 - All application and library module code
- JVM Target: 21 (Java 21 compatibility)

**Secondary:**
- Kotlin DSL - All Gradle build scripts (`build.gradle.kts`, `settings.gradle.kts`)

## Runtime

**Environment:**
- Android SDK 36 (compileSdk / targetSdk)
- Minimum SDK 26 (Android 8.0 Oreo)
- JVM Toolchain: 21

**Package Manager:**
- Gradle 9.3.1 (wrapper in `gradle/wrapper/gradle-wrapper.properties`)
- Lockfile: Not used (dependency locking not configured)
- Version catalog: `gradle/libs.versions.toml` (centralized dependency management)

## Build System

**Gradle Plugins:**
- Android Gradle Plugin 9.0.0
- Kotlin Compose Compiler (`org.jetbrains.kotlin.plugin.compose`)
- Kotlin Serialization (`org.jetbrains.kotlin.plugin.serialization`)
- KSP (Kotlin Symbol Processing) 2.3.5 - for Room and Hilt annotation processing
- Dagger Hilt 2.59.1

**Build Variants:**
- Flavor dimensions: `abi` (universal, arm64, armeabi, x86, x86_64) + `variant` (foss, gms)
- `foss` (default): F-Droid compatible, no Google Play Services
- `gms`: Includes Google Cast support via Play Services
- ProGuard/R8 minification enabled for release builds
- Core library desugaring enabled (`desugar_jdk_libs_nio 2.1.5`)

**Build Configuration:**
- `gradle.properties`: 4GB JVM heap, parallel builds, configuration cache enabled
- Room schema export: `app/schemas/` directory
- Compose compiler metrics available via `enableComposeCompilerReports` property

## Frameworks

**Core:**
- Jetpack Compose 1.10.2 - UI framework (declarative)
- Material 3 1.5.0-alpha09 - Design system
- AndroidX Activity Compose 1.12.3 - Activity integration

**Media Playback:**
- AndroidX Media3 (ExoPlayer) 1.7.1 - Audio/video playback engine
  - `media3-exoplayer` - Core player
  - `media3-session` - Media session management
  - `media3-datasource-okhttp` - OkHttp data source
  - `media3-cast` (GMS flavor only) - Google Cast support

**Database:**
- Room 2.8.4 - SQLite ORM with KSP annotation processing
  - Database class: `app/src/main/kotlin/com/metrolist/music/db/MusicDatabase.kt`
  - DAO: `app/src/main/kotlin/com/metrolist/music/db/DatabaseDao.kt`
  - Schema migrations tracked in `app/schemas/`

**Dependency Injection:**
- Dagger Hilt 2.59.1 - DI framework
  - Modules in `app/src/main/kotlin/com/metrolist/music/di/`
  - `AppModule.kt` - Database, cache, ListenTogether providers
  - `NetworkModule.kt` - Network-related providers
  - `WrappedModule.kt` - Additional providers

**Networking:**
- Ktor 3.4.0 - Primary HTTP client across all modules
  - Engines: OkHttp (innertube, spotify, lastfm, kugou, kizzy), CIO (lrclib, shazamkit, betterlyrics, simpmusic)
  - Content negotiation with kotlinx.serialization JSON
  - Content encoding (gzip, deflate, brotli)
  - WebSocket support (kizzy module for Discord gateway)
- OkHttp - Used directly for ListenTogether WebSocket connections

**Serialization:**
- kotlinx.serialization JSON - Used in all modules for JSON parsing
- Protocol Buffers (protobuf-javalite 4.33.5, protobuf-kotlin-lite) - Message serialization

**Image Loading:**
- Coil 3.3.0 (coil-compose + coil-network-okhttp) - Async image loading

**Testing:**
- JUnit 4.13.2 - Unit testing (present in library modules)

**Build/Dev:**
- Renovate (`renovate.json`) - Automated dependency updates
- Crowdin (`crowdin.yml`) - Translation management
- Fastlane (`fastlane/`) - Release metadata (F-Droid)

## Key Dependencies

**Critical:**
- `NewPipeExtractor v0.26.0` (via JitPack) - YouTube content extraction in innertube module
- `Ktor 3.4.0` - All API communication across every module
- `Room 2.8.4` - Local music database (songs, playlists, albums, artists, lyrics, play counts)
- `Media3/ExoPlayer 1.7.1` - Core audio playback engine
- `Dagger Hilt 2.59.1` - Dependency injection throughout the app

**UI:**
- `MaterialKolor 4.1.1` - Dynamic Material You color theming
- `compose-shimmer 1.3.3` - Loading placeholder animations
- `compose-reorderable 3.0.0` - Drag-and-drop list reordering
- `uCrop 2.2.11` - Image cropping
- `palette-ktx 1.0.0` - Color extraction from album art

**Infrastructure:**
- `Timber 5.0.1` - Logging (used in app and innertube modules)
- `Guava 33.5.0-jre` - Utilities (ListenableFuture bridging)
- `kotlinx-coroutines-guava 1.10.2` - Coroutine-ListenableFuture bridge
- `Brotli 0.1.2` - Brotli decompression for YouTube responses
- `Jsoup 1.22.1` - HTML parsing

**Text Processing:**
- `kuromoji-ipadic 0.9.0` - Japanese text tokenization (for search/sorting)
- `TinyPinyin 2.0.3` - Chinese pinyin conversion (for search/sorting)
- `Apache Commons Lang3 3.20.0` - String utilities

**Google Cast (GMS flavor only):**
- `play-services-cast-framework 22.2.0`
- `mediarouter 1.8.1`

## Module Structure

The project uses a multi-module Gradle architecture with the app module depending on all library modules:

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

**Module dependency graph:**
```
app -> innertube, spotify, lastfm, shazamkit, lrclib, kugou, betterlyrics, simpmusic, kizzy
```
All library modules are independent of each other. Only the app module has cross-cutting dependencies.

## Configuration

**Environment:**
- `local.properties` - Local developer config (API keys: `LASTFM_API_KEY`, `LASTFM_SECRET`)
- Environment variables used in CI: `LASTFM_API_KEY`, `LASTFM_SECRET`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
- Spotify auth uses `sp_dc` cookie from WebView login (no developer API key needed)

**Build:**
- `build.gradle.kts` (root) - Plugin declarations, subproject Kotlin compiler config
- `settings.gradle.kts` - Module includes, repository configuration
- `gradle/libs.versions.toml` - Centralized version catalog (~95 entries)
- `gradle.properties` - JVM args, Android settings, performance flags
- `lint.xml` - Lint configuration

## Platform Requirements

**Development:**
- JDK 21
- Android SDK 36
- Gradle 9.3.1 (via wrapper)

**Production:**
- Android 8.0+ (API 26+)
- Target: Android 16 (API 36)
- Architectures: arm64-v8a, armeabi-v7a, x86_64, x86

## CI/CD

**GitHub Actions Workflows (`.github/workflows/`):**
- `build.yml` - Build APKs on push (foss + gms variants)
- `build_pr.yml` - PR builds
- `build_quick.yml` - Quick build workflow
- `release.yml` - Release publishing
- `spotify-hash-check.yml` - Automated Spotify GQL hash rotation detection
- `pr_title_prefix.yml` - PR title validation

---

*Stack analysis: 2026-04-03*
