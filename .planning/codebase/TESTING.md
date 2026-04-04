# Testing Patterns

**Analysis Date:** 2026-04-03

## Test Framework

**Runner:**
- JUnit 4 (version 4.13.2) declared as `testImplementation` in multiple module build files
- AndroidJUnitRunner configured in `app/build.gradle.kts`: `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`

**Assertion Library:**
- JUnit 4 assertions (default)

**Run Commands:**
```bash
./gradlew test                    # Run unit tests (if any existed)
./gradlew connectedAndroidTest    # Run instrumented tests (if any existed)
./gradlew :app:lintUniversalFossRelease  # Lint check (the only quality gate currently used)
```

## Test File Organization

**Location:**
- No test source directories exist anywhere in the project
- No `src/test/` directories in any module
- No `src/androidTest/` directories in any module
- No test files (`.kt` or `.java`) with `Test` or `Spec` naming exist

**Declared Test Dependencies:**
- `testImplementation(libs.junit)` declared in: `innertube/build.gradle.kts`, `spotify/build.gradle.kts`, `lrclib/build.gradle.kts`, `kugou/build.gradle.kts`, `lastfm/build.gradle.kts`, `shazamkit/build.gradle.kts`, `kizzy/build.gradle.kts`
- NOT declared in `app/build.gradle.kts` (the main application module)
- The JUnit dependency exists but zero test files have been written

## Test Structure

**No tests exist.** The JUnit dependency is declared in library modules but never used.

## CI/CD Testing Configuration

**GitHub Actions Workflows:**

**`build.yml` (push to all branches):**
- Builds release and debug APKs for both `foss` and `gms` variants
- Runs Android Lint alongside assembly: `./gradlew assembleUniversalFossRelease :app:lintUniversalFossRelease`
- Does NOT run any test tasks (`test`, `connectedAndroidTest`)
- Signs APKs and uploads as artifacts

**`build_pr.yml` (pull requests):**
- Builds debug FOSS APK only
- Runs Lint: `./gradlew assembleUniversalFossDebug :app:lintUniversalFossDebug`
- Does NOT run any test tasks
- Uploads debug APK as artifact

**`build_quick.yml`:**
- Quick build variant (not analyzed in detail)

**`release.yml`:**
- Release workflow for publishing

**Quality Gates:**
- Android Lint is the ONLY automated quality check
- Lint configured with `abortOnError = false` so it never fails the build
- No test execution in any CI workflow

## Coverage

**Requirements:** None enforced. No coverage tools configured.

**Coverage Tools:** None (no JaCoCo, Kover, or similar configured)

## Test Types

**Unit Tests:**
- None exist

**Integration Tests:**
- None exist

**UI Tests:**
- None exist
- No Compose testing dependencies declared (`compose-ui-test`, `compose-ui-test-junit4`)
- No Espresso dependencies declared

**E2E Tests:**
- None exist

## Mocking

**Framework:** None configured
- No Mockito, MockK, or similar mocking library in any `build.gradle.kts`

## What Should Be Tested

**High-Priority Testing Gaps:**

**Database Layer (`app/src/main/kotlin/com/metrolist/music/db/`):**
- `DatabaseDao.kt` (1747 lines) contains complex queries, transactions, and business logic
- `MusicDatabase.kt` wraps the DAO with additional migration logic
- Room entity conversion functions
- Risk: Data corruption, migration failures undetected

**API Client Modules:**
- `innertube/` - YouTube Music API parsing (response parsing is fragile due to unofficial API)
- `spotify/` - Spotify GraphQL API with hash-based persisted queries
- `lrclib/`, `kugou/`, `betterlyrics/`, `simpmusic/` - Lyrics provider clients
- `lastfm/` - Last.fm scrobbling API
- `shazamkit/` - Music recognition
- Risk: API response format changes break parsing silently

**ViewModel Logic:**
- ViewModels contain significant business logic (data fetching, transformation, caching)
- `HomeViewModel` combines multiple data sources with complex flow logic
- Risk: Regressions in data loading, filtering, and state management

**Conversion/Mapping Functions:**
- `MediaMetadata.toSongEntity()`, `Song.toMediaMetadata()`, `SongItem.toMediaMetadata()`
- `SpotifyItemConverter`, `SpotifyYouTubeMapper`
- Risk: Data loss during conversion

**Utility Functions:**
- Cipher/signature deobfuscation (`utils/cipher/`)
- DataStore preference helpers
- String utilities, network utilities
- Risk: Silent failures in critical paths

**Playback Engine:**
- `MusicService.kt` - ExoPlayer setup, cache management, audio processing
- `PlayerConnection.kt` - Service binding
- Queue management in `playback/queues/`
- Risk: Playback failures, cache corruption

## Recommended Testing Strategy

**Phase 1 - Unit Tests (Library Modules):**
- Add tests for `innertube/` response parsing (most critical - unofficial API)
- Add tests for `spotify/` API response parsing
- Add tests for lyrics provider modules
- These modules already declare `testImplementation(libs.junit)`

**Phase 2 - Unit Tests (App Module):**
- Add `testImplementation` dependencies to `app/build.gradle.kts`
- Add MockK or Mockito for mocking
- Test ViewModel logic
- Test DatabaseDao queries with Room's in-memory database
- Test conversion/mapping functions

**Phase 3 - UI Tests:**
- Add Compose testing dependencies
- Test critical user flows: search, playback, playlist management
- Test navigation

**Dependencies to Add:**
```kotlin
// app/build.gradle.kts
testImplementation(libs.junit)
testImplementation("io.mockk:mockk:1.13.x")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.x")
testImplementation("androidx.room:room-testing:2.8.x")
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
androidTestImplementation("androidx.test.ext:junit:1.2.x")
```

---

*Testing analysis: 2026-04-03*
