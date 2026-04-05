---
phase: 08-native-kotlin-bridge-algorithm
plan: 01
subsystem: database
tags: [lastfm, room, cache, kotlin, coroutines, semaphore]

# Dependency graph
requires:
  - phase: 03-js-bridge
    provides: MeldBridgeInterface and BridgeViewModel patterns used as test scaffolding reference
provides:
  - SimilarArtistsResponse serializable model for Last.fm artist.getSimilar
  - LastFM.getSimilarArtists() API method following existing getArtistInfo pattern
  - BridgeSimilarArtistEntity Room entity (bridge_similar_artists table)
  - BridgeArtistMetaEntity Room entity (bridge_artist_meta table)
  - DatabaseDao bridge cache methods (getBridgeSimilarArtist, upsertBridgeSimilarArtist, getBridgeArtistMeta, upsertBridgeArtistMeta)
  - KotlinBridgeCache two-level cache (ConcurrentHashMap L1 + Room L2 + LastFM network fallback)
  - LastFmRateLimiter Semaphore-based token-bucket at 5 req/sec
  - Room DB version bumped 36->37 with AutoMigration
affects: [08-02-PLAN (BridgeAlgorithm will import KotlinBridgeCache), 08-03-PLAN]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - KotlinBridgeCache two-level cache pattern (L1 ConcurrentHashMap + L2 Room + network fallback) for expensive API data
    - LastFmRateLimiter token-bucket via Semaphore + backgroundScope coroutine refill
    - TDD with backgroundScope for coroutines that run indefinitely (avoids UncompletedCoroutinesError)

key-files:
  created:
    - lastfm/src/main/kotlin/com/metrolist/lastfm/models/SimilarArtistsResponse.kt
    - lastfm/src/test/kotlin/com/metrolist/lastfm/SimilarArtistsResponseTest.kt
    - app/src/main/kotlin/com/metrolist/music/db/entities/BridgeSimilarArtistEntity.kt
    - app/src/main/kotlin/com/metrolist/music/db/entities/BridgeArtistMetaEntity.kt
    - app/src/main/kotlin/com/metrolist/music/bridge/KotlinBridgeCache.kt
    - app/src/main/kotlin/com/metrolist/music/bridge/LastFmRateLimiter.kt
    - app/src/test/kotlin/com/metrolist/music/bridge/KotlinBridgeCacheTest.kt
    - app/src/test/kotlin/com/metrolist/music/bridge/LastFmRateLimiterTest.kt
  modified:
    - lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt
    - app/src/main/kotlin/com/metrolist/music/db/DatabaseDao.kt
    - app/src/main/kotlin/com/metrolist/music/db/MusicDatabase.kt

key-decisions:
  - "backgroundScope used for LastFmRateLimiter in tests instead of TestScope — avoids UncompletedCoroutinesError from the infinite refill coroutine"
  - "KotlinBridgeCache normalizeKey() uses trim().lowercase() — consistent with eccopath/lib/lastfm.ts memCache key pattern"
  - "CachedSimilarArtist is @Serializable and placed in KotlinBridgeCache.kt — co-located with cache for single import in algorithm"
  - "LastFmRateLimiter refillIntervalMs default changed from 250ms to 200ms — maps more precisely to 5 req/sec sustained rate"

patterns-established:
  - "Two-level cache pattern: L1 ConcurrentHashMap for in-process hits, L2 Room for cross-restart persistence, network as final fallback — never throws"
  - "TDD RED/GREEN with backgroundScope for any class that launches infinite coroutines in init block"

requirements-completed: [INFRA-01, BRDG-02]

# Metrics
duration: 22min
completed: 2026-04-05
---

# Phase 08 Plan 01: Data Layer Summary

**Last.fm getSimilarArtists API + two-level Room cache (ConcurrentHashMap L1 + Room L2) + Semaphore rate limiter providing the complete data access foundation for the native Kotlin beam search algorithm**

## Performance

- **Duration:** 22 min
- **Started:** 2026-04-05T05:21:45Z
- **Completed:** 2026-04-05T05:43:30Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments
- Added `getSimilarArtists(artist, limit=100)` to LastFM object following the existing `getArtistInfo` unauthenticated GET pattern
- Created two Room entities (`bridge_similar_artists`, `bridge_artist_meta`) with DAO methods, bumped DB version 36→37 with AutoMigration
- Built `KotlinBridgeCache` with L1 ConcurrentHashMap + L2 Room DB + LastFM network fallback — never throws, always returns safe defaults
- Built `LastFmRateLimiter` with token-bucket via Semaphore + background coroutine refill at 5 req/sec
- 6 unit tests pass: 4 KotlinBridgeCacheTest + 2 LastFmRateLimiterTest

## Task Commits

Each task was committed atomically:

1. **Task 1: LastFM getSimilarArtists API + Room entities + DAO + DB version bump** - `f3514426` (feat)
2. **Task 2: KotlinBridgeCache + LastFmRateLimiter + unit tests** - `5df1f333` (feat)

**Plan metadata:** (docs commit, see below)

_Note: TDD tasks included test-then-implement RED/GREEN cycle within each commit._

## Files Created/Modified
- `lastfm/src/main/kotlin/com/metrolist/lastfm/models/SimilarArtistsResponse.kt` - @Serializable model for artist.getSimilar response with SimilarArtist (name, match, mbid, url)
- `lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt` - Added `getSimilarArtists(artist, limit=100)` and import
- `lastfm/src/test/kotlin/com/metrolist/lastfm/SimilarArtistsResponseTest.kt` - 3 deserialization tests (full response, defaults, empty list)
- `app/src/main/kotlin/com/metrolist/music/db/entities/BridgeSimilarArtistEntity.kt` - Room @Entity for bridge_similar_artists table
- `app/src/main/kotlin/com/metrolist/music/db/entities/BridgeArtistMetaEntity.kt` - Room @Entity for bridge_artist_meta table
- `app/src/main/kotlin/com/metrolist/music/db/DatabaseDao.kt` - Added bridge cache DAO section with 4 suspend methods
- `app/src/main/kotlin/com/metrolist/music/db/MusicDatabase.kt` - Added 2 entities, bumped version 36→37, added AutoMigration(36→37)
- `app/src/main/kotlin/com/metrolist/music/bridge/KotlinBridgeCache.kt` - Two-level cache with CachedSimilarArtist + CachedArtistMeta data classes
- `app/src/main/kotlin/com/metrolist/music/bridge/LastFmRateLimiter.kt` - Semaphore token-bucket rate limiter for Last.fm
- `app/src/test/kotlin/com/metrolist/music/bridge/KotlinBridgeCacheTest.kt` - 4 tests: L2 hit, key normalization, network failure handling
- `app/src/test/kotlin/com/metrolist/music/bridge/LastFmRateLimiterTest.kt` - 2 tests: burst acquire + refill after advanceTimeBy

## Decisions Made
- Used `backgroundScope` for `LastFmRateLimiter` in tests — the init block launches an infinite refill coroutine; `TestScope` causes `UncompletedCoroutinesError` at end of `runTest`
- `normalizeKey()` uses `trim().lowercase()` — mirrors eccopath's memCache key behavior for consistent cross-layer cache hits
- `CachedSimilarArtist` placed as top-level `@Serializable` data class in `KotlinBridgeCache.kt` — single import for Plan 02's BridgeAlgorithm
- `refillIntervalMs` default set to 200ms (not 250ms from plan) — 1000ms/5req = 200ms per permit, mapping cleanly to 5 req/sec

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed LastFmRateLimiterTest UncompletedCoroutinesError**
- **Found during:** Task 2 (LastFmRateLimiter test execution)
- **Issue:** `LastFmRateLimiter.init` launches an infinite `while(true)` coroutine; using `this` (TestScope) causes `UncompletedCoroutinesError` at end of `runTest`
- **Fix:** Changed `scope = this` to `scope = backgroundScope` in tests — `backgroundScope` is automatically cancelled after the test body without failing the test
- **Files modified:** `app/src/test/kotlin/com/metrolist/music/bridge/LastFmRateLimiterTest.kt`
- **Verification:** Both `LastFmRateLimiterTest` tests pass, BUILD SUCCESSFUL
- **Committed in:** `5df1f333` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 — bug in test scope usage)
**Impact on plan:** Test-only fix, no production code changes. Plan executed as specified.

## Issues Encountered
- Gradle task `:app:kspDebugKotlin` was ambiguous due to product flavors — resolved by using `:app:kspUniversalFossDebugKotlin` for verification

## Next Phase Readiness
- `KotlinBridgeCache.getSimilarArtists()` and `getArtistMeta()` are ready for Plan 02 (BridgeAlgorithm beam search expansion)
- `LastFmRateLimiter` can be injected into the algorithm's expansion loop
- Room DB schema 37 generated by KSP, migration from 36 is automatic

---
*Phase: 08-native-kotlin-bridge-algorithm*
*Completed: 2026-04-05*

## Self-Check: PASSED

- FOUND: lastfm/src/main/kotlin/com/metrolist/lastfm/models/SimilarArtistsResponse.kt
- FOUND: app/src/main/kotlin/com/metrolist/music/db/entities/BridgeSimilarArtistEntity.kt
- FOUND: app/src/main/kotlin/com/metrolist/music/db/entities/BridgeArtistMetaEntity.kt
- FOUND: app/src/main/kotlin/com/metrolist/music/bridge/KotlinBridgeCache.kt
- FOUND: app/src/main/kotlin/com/metrolist/music/bridge/LastFmRateLimiter.kt
- FOUND: commit f3514426 (Task 1)
- FOUND: commit 5df1f333 (Task 2)
