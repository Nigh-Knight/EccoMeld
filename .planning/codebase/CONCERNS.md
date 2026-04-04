# Codebase Concerns

**Analysis Date:** 2026-04-03

## Tech Debt

**Branding/Naming Inconsistency (Critical):**
- Issue: The entire codebase uses `com.metrolist` as the Java/Kotlin package namespace, `rootProject.name = "Metrolist"` in `settings.gradle.kts`, `Theme.Metrolist` in `app/src/main/res/values/styles.xml`, and copyright headers say "Metrolist Project". However the application ID is `com.meld.app`, the app display name is "Meld" (in `app/src/main/res/values/app_name.xml`), and the repository is named "EccoMeld". The PRD refers to the project as "EccoMeld".
- Files: `settings.gradle.kts` (line 19), `app/build.gradle.kts` (line 18 vs line 22), all files under `app/src/main/kotlin/com/metrolist/`, `innertube/src/main/kotlin/com/metrolist/`, `spotify/src/main/kotlin/com/metrolist/`, `lrclib/src/main/kotlin/com/metrolist/`, `kugou/src/main/kotlin/com/metrolist/`, `shazamkit/src/main/kotlin/com/metrolist/`, `simpmusic/src/main/kotlin/com/metrolist/`, `betterlyrics/src/main/kotlin/com/metrolist/`, `lastfm/src/main/kotlin/com/metrolist/`
- Impact: Three-way naming confusion (Metrolist package, Meld app name, EccoMeld repo/product). Makes the project confusing for contributors and creates maintenance burden. All 10 Gradle modules use `com.metrolist.*` namespaces.
- Fix approach: Decide on a single canonical name. Renaming packages from `com.metrolist` to `com.eccomeld` or `com.meld` requires moving hundreds of files and updating all imports. Consider doing this as a single bulk migration with IDE refactoring tools. The `applicationId` (`com.meld.app`) must remain stable for existing users.

**Massive God-Object Files:**
- Issue: Several files far exceed reasonable size limits, indicating insufficient decomposition.
- Files:
  - `app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt` (3,460 lines) - handles playback, caching, crossfade, audio normalization, Discord RPC, Last.fm scrobbling, persistent queue, downloads, Listen Together integration, and stream URL resolution all in one class
  - `app/src/main/kotlin/com/metrolist/music/ui/screens/HomeScreen.kt` (2,361 lines)
  - `innertube/src/main/kotlin/com/metrolist/innertube/YouTube.kt` (1,880 lines) - monolithic API client
  - `app/src/main/kotlin/com/metrolist/music/db/DatabaseDao.kt` (1,747 lines) - single DAO with every query
  - `app/src/main/kotlin/com/metrolist/music/listentogether/ListenTogetherManager.kt` (1,673 lines)
  - `app/src/main/kotlin/com/metrolist/music/listentogether/ListenTogetherClient.kt` (1,509 lines)
  - `app/src/main/kotlin/com/metrolist/music/constants/PreferenceKeys.kt` (681 lines) - all preference keys in one flat file
  - `app/src/main/kotlin/com/metrolist/music/MainActivity.kt` (1,302 lines)
- Impact: Hard to navigate, test, and modify without unintended side effects. High risk of merge conflicts.
- Fix approach: Extract `MusicService.kt` into focused classes: `PlaybackEngine`, `StreamResolver`, `QueueManager`, `ScrobbleHandler`, `CrossfadeController`. Split `DatabaseDao` into domain-specific DAOs (`SongDao`, `PlaylistDao`, `ArtistDao`). Break `YouTube.kt` into endpoint-specific classes.

**Excessive `runBlocking` Usage:**
- Issue: `runBlocking` is used in 15+ locations across the app, including in UI-adjacent code (`SelectionSongsMenu.kt`, `LyricsMenuViewModel.kt`), data access (`DataStore.kt`, `ContextExt.kt`), and the music service (`MusicService.kt` with 6+ usages). This blocks threads and can cause ANRs.
- Files: `app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt`, `app/src/main/kotlin/com/metrolist/music/utils/DataStore.kt`, `app/src/main/kotlin/com/metrolist/music/extensions/ContextExt.kt`, `app/src/main/kotlin/com/metrolist/music/ui/menu/SelectionSongsMenu.kt`, `app/src/main/kotlin/com/metrolist/music/viewmodels/BackupRestoreViewModel.kt`, `app/src/main/kotlin/com/metrolist/music/App.kt`, `innertube/src/main/kotlin/com/metrolist/innertube/YouTube.kt` (line 1556)
- Impact: Potential ANR (Application Not Responding) errors, thread starvation, deadlocks when called from coroutine contexts.
- Fix approach: Replace `runBlocking` with proper `suspend` functions. For DataStore reads in non-coroutine contexts, use `Flow.first()` within a coroutine scope rather than blocking.

**Database `withTransaction` Uses `runBlocking` Inside Coroutine:**
- Issue: `MusicDatabase.withTransaction()` uses `runBlocking` inside `withContext(Dispatchers.IO)`, which nests blocking calls inside coroutine contexts. This is an anti-pattern that wastes a thread.
- Files: `app/src/main/kotlin/com/metrolist/music/db/MusicDatabase.kt` (lines 79-88)
- Impact: Thread pool exhaustion under load; defeats the purpose of coroutines.
- Fix approach: Use Room's built-in `withTransaction` support from the KTX extensions, or restructure to avoid nested blocking.

**Open TODOs:**
- Issue: Multiple unfinished features tracked only via inline TODOs.
- Files:
  - `innertube/src/main/kotlin/com/metrolist/innertube/YouTube.kt` (lines 280, 305) - "Extract explicit badge for albums"
  - `app/src/main/kotlin/com/metrolist/music/db/DatabaseDao.kt` (lines 718, 730) - "add ui to filter by local or remote"
  - `app/src/main/kotlin/com/metrolist/music/lyrics/LyricsUtils.kt` (lines 589, 972) - language selection for lyrics
  - `app/src/main/kotlin/com/metrolist/music/ui/theme/Type.kt` (line 14) - M3 Expressive font definition
  - `kizzy/src/main/kotlin/com/my/kizzy/gateway/DiscordWebSocket.kt` (line 290) - waiting for socket connection
- Impact: Features remain incomplete; no tracking mechanism outside code comments.
- Fix approach: Convert to GitHub Issues for proper tracking and prioritization.

## Security Considerations

**Sensitive Tokens Stored in Plaintext DataStore:**
- Risk: Discord user tokens, Spotify sp_dc session cookies, Last.fm session keys, OpenRouter API keys, and DeepL API keys are stored as plain string preferences in Android DataStore without encryption.
- Files: `app/src/main/kotlin/com/metrolist/music/constants/PreferenceKeys.kt` (lines 124: `DiscordTokenKey`, 176-178: `SpotifySpDcKey`/`SpotifySpKeyKey`/`SpotifyAccessTokenKey`, 159: `LastFMSessionKey`, 438: `OpenRouterApiKey`, 444: `DeeplApiKey`)
- Current mitigation: DataStore files are in the app's private directory, protected by Android's sandboxing. However, rooted devices or backup extraction expose these values.
- Recommendations: Use `EncryptedSharedPreferences` or Android Keystore for sensitive tokens. At minimum, encrypt Discord tokens and Spotify cookies since they grant full account access.

**Discord User Token Handling:**
- Risk: The app accepts raw Discord user tokens to enable Rich Presence. Discord explicitly prohibits automated user account access ("self-botting"), and token compromise gives full account control.
- Files: `app/src/main/kotlin/com/metrolist/music/ui/screens/settings/integrations/DiscordSettings.kt` (line 226), `kizzy/src/main/kotlin/com/my/kizzy/gateway/DiscordWebSocket.kt` (line 53)
- Current mitigation: None. The token is passed directly to a WebSocket connection to Discord's gateway.
- Recommendations: Document the ToS risk to users. Consider using Discord's official Game SDK or OAuth2 RPC scope instead.

**Cleartext Traffic Enabled Globally:**
- Risk: `network_security_config.xml` sets `cleartextTrafficPermitted="true"` for the entire app, not just Listen Together local servers as the comment suggests.
- Files: `app/src/main/res/xml/network_security_config.xml` (line 5)
- Current mitigation: The comment says WSS is used for the production server, but the global setting allows any HTTP traffic.
- Recommendations: Use domain-specific cleartext exceptions for local/LAN addresses only (e.g., `<domain-config cleartextTrafficPermitted="true"><domain>10.0.0.0/8</domain></domain-config>`).

**Spotify Session Cookie Extraction:**
- Risk: The app uses WebView to capture Spotify `sp_dc` cookies, then uses a community-maintained GitHub Gist for TOTP secrets to generate access tokens. This bypasses Spotify's official API authentication.
- Files: `spotify/src/main/kotlin/com/metrolist/spotify/SpotifyAuth.kt`, `app/src/main/kotlin/com/metrolist/music/ui/screens/SpotifyLoginScreen.kt`, `app/src/main/kotlin/com/metrolist/music/utils/SpotifyTokenManager.kt`
- Current mitigation: Tokens are refreshed via mutex to prevent race conditions. The external Gist dependency could disappear at any time.
- Recommendations: Be aware that Spotify could change their internal token endpoint format at any time, breaking the integration. The dependency on a third-party Gist for TOTP secrets is a single point of failure.

## Performance Bottlenecks

**MusicService Complexity:**
- Problem: At 3,460 lines with 23 catch blocks, `MusicService` handles too many responsibilities in a single Android service, making it expensive to initialize and prone to memory pressure.
- Files: `app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt`
- Cause: Accumulated features (crossfade, Discord RPC, Last.fm, Listen Together, download management, cipher deobfuscation) all live in one class.
- Improvement path: Extract features into injected collaborators. Each concern (scrobbling, RPC, listen-together sync) should be a separate class that observes playback state changes.

**ListenTogether Subsystem (3,952 lines total):**
- Problem: The Listen Together feature spans nearly 4,000 lines across 6 files with 30+ catch blocks in the client alone, suggesting fragile error handling and complex state management.
- Files: `app/src/main/kotlin/com/metrolist/music/listentogether/ListenTogetherClient.kt` (1,509 lines), `app/src/main/kotlin/com/metrolist/music/listentogether/ListenTogetherManager.kt` (1,673 lines)
- Cause: Complex distributed state synchronization with many failure modes, each handled with broad `catch (e: Exception)` blocks.
- Improvement path: Introduce a state machine pattern for connection lifecycle. Replace broad exception catching with specific exception types.

**Hardcoded Server List:**
- Problem: Listen Together server URLs are hardcoded as JSON in `ListenTogetherServers.kt`.
- Files: `app/src/main/kotlin/com/metrolist/music/listentogether/ListenTogetherServers.kt`
- Cause: Quick implementation without dynamic server discovery.
- Improvement path: Fetch server list from a remote endpoint with the hardcoded list as fallback.

## Fragile Areas

**YouTube Cipher Deobfuscation:**
- Files: `app/src/main/kotlin/com/metrolist/music/utils/cipher/CipherManager.kt`, `app/src/main/kotlin/com/metrolist/music/utils/cipher/SignatureDeobfuscator.kt`, `app/src/main/kotlin/com/metrolist/music/utils/cipher/NTransformSolver.kt`, `app/src/main/kotlin/com/metrolist/music/utils/cipher/PlayerJsFetcher.kt`, `app/src/main/kotlin/com/metrolist/music/utils/cipher/FunctionNameExtractor.kt`
- Why fragile: These files parse YouTube's obfuscated JavaScript player code to extract signature functions. YouTube changes their player JS regularly, which can break the extraction regex patterns and deobfuscation logic at any time. The `NTransformSolver` uses a WebView to execute JavaScript.
- Safe modification: The retry-with-cache-invalidation pattern in `CipherManager.kt` provides some resilience. Always test with multiple video types (age-restricted, music videos, user uploads).
- Test coverage: No automated tests exist for cipher operations.

**Spotify GraphQL Hash Sync:**
- Files: `spotify/src/main/kotlin/com/metrolist/spotify/SpotifyHashProvider.kt`, `app/src/main/kotlin/com/metrolist/music/utils/SpotifyHashSync.kt`
- Why fragile: Spotify's internal GraphQL API uses operation hashes that change periodically. The automated workflow (`.github/workflows/`) updates these, but a missed update breaks Spotify features.
- Safe modification: The hash sync is automated via GitHub Actions; manual changes should follow the same pattern.
- Test coverage: None.

**Database Migration Chain:**
- Files: `app/src/main/kotlin/com/metrolist/music/db/MusicDatabase.kt` (lines 93-157)
- Why fragile: 36 schema versions with 30+ auto-migrations and 4 manual migrations. The migration chain from version 1 is long and complex. A single broken migration can cause data loss (note `fallbackToDestructiveMigration(dropAllTables = true)` on line 178).
- Safe modification: Always add new auto-migrations. Test upgrades from the previous version and from several major versions back. The destructive fallback means any migration failure silently wipes the database.
- Test coverage: No migration tests exist.

**Room `@SuppressWarnings(RoomWarnings.QUERY_MISMATCH)`:**
- Files: `app/src/main/kotlin/com/metrolist/music/db/DatabaseDao.kt` (30+ occurrences throughout the file)
- Why fragile: Over 30 query mismatch warnings are suppressed, meaning the compiler cannot verify that query result columns match the return type fields. Schema changes can silently produce incorrect results or runtime crashes.
- Safe modification: When changing database schema or DAO queries, manually verify column-to-field mappings. Consider creating dedicated result classes that match queries exactly.
- Test coverage: No DAO tests.

## Scaling Limits

**Preference Key Explosion:**
- Current capacity: 681 lines, 150+ preference keys in a single flat file.
- Limit: Increasingly difficult to manage, find related settings, or avoid key name collisions (e.g., `MixSortDescendingKey` reuses `albumSortDescending` key string on line 207, which is a bug).
- Scaling path: Group keys into sealed objects by feature area (e.g., `object DiscordPrefs`, `object SpotifyPrefs`, `object PlaybackPrefs`).

**Single DatabaseDao:**
- Current capacity: 1,747 lines with every query for every entity type.
- Limit: Compile times increase, merge conflicts are frequent, and finding the right query is difficult.
- Scaling path: Split into domain-specific DAOs: `SongDao`, `PlaylistDao`, `ArtistDao`, `AlbumDao`, `EventDao`. Room supports multiple DAO interfaces per database.

## Dependencies at Risk

**Kizzy (Discord RPC Library):**
- Risk: Third-party library (`com.my.kizzy`) vendored directly into the project under a different package namespace. Copyright headers indicate 2022. Uses Discord's undocumented gateway API for user-token-based Rich Presence.
- Impact: Discord could block this approach; the library appears unmaintained.
- Migration plan: Consider Discord's official RPC mechanisms or remove the feature.

**Community TOTP Gist for Spotify Auth:**
- Risk: `SpotifyAuth.kt` fetches TOTP secrets from a GitHub Gist (`https://api.github.com/gists/22ed9c6ba463899e933427f7de1f0eef`). If the Gist is deleted, made private, or its content format changes, all Spotify authentication breaks.
- Impact: Complete loss of Spotify integration for all users.
- Migration plan: Mirror the Gist content to a controlled endpoint. Add fallback mechanisms or cache the last known-good secret.

**NewPipe Extractor:**
- Risk: Used for YouTube data extraction via JitPack (`com.github.teamnewpipe:NewPipeExtractor`). JitPack builds can be unreliable, and NewPipe Extractor updates may introduce breaking API changes.
- Impact: YouTube browsing, search, and playback depend on this library.
- Migration plan: The `settings.gradle.kts` already has commented-out support for a local NewPipe Extractor build (lines 28-37).

**Alpha/Pre-release Dependencies:**
- Risk: `material3 = "1.5.0-alpha09"` is an alpha version with unstable APIs.
- Impact: API breakage on updates; no stability guarantees.
- Migration plan: Track stable releases and upgrade when available.

## Missing Critical Features

**Zero Test Coverage:**
- Problem: No test files exist anywhere in the project. No unit tests, no integration tests, no instrumented tests. The `app/build.gradle.kts` declares a `testInstrumentationRunner` but no tests use it.
- Blocks: Safe refactoring, confident CI/CD, contributor onboarding.

**No Lint/Static Analysis in CI:**
- Problem: The CI workflow explicitly skips lint (`-x lint -x lintUniversalFossRelease` in `.github/workflows/build_quick.yml`).
- Blocks: Catching code quality issues before merge.

**Mixed Storage Mechanisms:**
- Problem: The app uses both `SharedPreferences` (in `DensityScaler.kt`, `MusicRecognizerWidgetService.kt`, `MusicRecognizerWidgetReceiver.kt`) and `DataStore` (everywhere else) for persistence, without clear rationale documented for when to use which.
- Files: `app/src/main/kotlin/com/dpi/DensityScaler.kt`, `app/src/main/kotlin/com/metrolist/music/widget/MusicRecognizerWidgetService.kt`
- Blocks: Consistent data access patterns.

## Legal/Licensing Considerations

**GPL-3.0 License Compliance:**
- The project is licensed under GPL-3.0. All contributed code must be GPL-3.0 compatible.
- The `kizzy` module contains code with "Copyright (C) 2022" headers from a third-party project. Verify that the original Kizzy project's license is GPL-compatible.
- The `innertube/src/main/kotlin/com/metrolist/innertube/utils/PoTokenGenerator.kt` is ported from "ArchiveTune (koiverse/ArchiveTune)" and notes GPL-3.0, which is compatible.

**YouTube and Spotify ToS:**
- The app accesses YouTube's internal InnerTube API and Spotify's internal web player API, both of which are undocumented and likely prohibited by their respective Terms of Service.
- Impact: Either service could take action to block the app's access patterns.

## Test Coverage Gaps

**Entire Project:**
- What's not tested: Everything. There are zero test files in the repository.
- Files: All files under `app/src/main/kotlin/`, `innertube/src/main/kotlin/`, `spotify/src/main/kotlin/`, etc.
- Risk: Any refactoring, dependency upgrade, or feature addition has no safety net. The cipher deobfuscation, database migrations, and API response parsing are particularly high-risk areas without tests.
- Priority: High. Start with:
  1. Database migration tests (critical - data loss risk)
  2. Cipher/deobfuscation unit tests (fragile, breaks with YouTube changes)
  3. InnerTube response parsing tests (complex JSON parsing logic)
  4. Spotify token refresh flow tests (multiple failure modes)

---

*Concerns audit: 2026-04-03*
