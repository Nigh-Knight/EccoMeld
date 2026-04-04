# External Integrations

**Analysis Date:** 2026-04-03

## APIs & External Services

### YouTube Music / InnerTube (Primary Music Source)

- **Module:** `innertube/`
- **Client:** `innertube/src/main/kotlin/com/metrolist/innertube/InnerTube.kt` (HTTP layer)
- **Facade:** `innertube/src/main/kotlin/com/metrolist/innertube/YouTube.kt` (parsed responses)
- **Base URL:** `https://music.youtube.com/youtubei/v1/`
- **Protocol:** REST (YouTube's internal InnerTube API)
- **Auth:** Cookie-based YouTube login (optional), visitor data tracking
- **HTTP Engine:** Ktor with OkHttp engine + Brotli/gzip decompression

**Client Identity Spoofing:**
Multiple YouTube client identities are defined in `innertube/src/main/kotlin/com/metrolist/innertube/models/YouTubeClient.kt`:
- `WEB_REMIX` (clientId 67) - Primary client for YouTube Music browsing (login-supported, PoToken-enabled)
- `WEB` (clientId 1) - Standard YouTube web
- `WEB_CREATOR` (clientId 62) - Creator Studio client
- `TVHTML5` (clientId 7) - Smart TV client
- `TVHTML5_SIMPLY_EMBEDDED_PLAYER` (clientId 85) - Bypasses age restriction
- `IOS` (clientId 5) - iOS YouTube app
- `MOBILE/ANDROID` (clientId 3) - Android YouTube app
- `ANDROID_VR` (clientId 28) - Multiple versions (1.43, 1.61) for non-adaptive bitrate / fallback
- `ANDROID_CREATOR` (clientId 14) - YouTube Studio app
- `VISIONOS` (clientId 101) - Unreleased Apple Vision Pro client
- `IPADOS` (clientId 5) - iPad client

**Key Endpoints Used:**
- Browse (home, artist, album, playlist, charts, explore, library, mood/genres)
- Search (text search, suggestions, search summary)
- Player (stream URLs, media info)
- Next (queue, related content)
- Transcript
- Playlist management (create, edit, add/remove items)
- Image upload
- Account menu / user info
- Feedback (like/dislike)

**Additional YouTube Integration:**
- `NewPipeExtractor v0.26.0` - Used as fallback/alternative for content extraction
- `ReturnYouTubeDislike` API - Fetches dislike counts (referenced in `InnerTube.kt`)

**Response Models:** ~80+ Kotlin data classes in `innertube/src/main/kotlin/com/metrolist/innertube/models/`
**Page Parsers:** ~25 page parser files in `innertube/src/main/kotlin/com/metrolist/innertube/pages/`

---

### Spotify (Library Import & Browsing)

- **Module:** `spotify/` (pure JVM, no Android dependency)
- **Client:** `spotify/src/main/kotlin/com/metrolist/spotify/Spotify.kt`
- **Auth:** `spotify/src/main/kotlin/com/metrolist/spotify/SpotifyAuth.kt`

**Two API Layers:**

1. **GraphQL API** (primary):
   - URL: `https://api-partner.spotify.com/pathfinder/v2/query`
   - Uses persisted query hashes (hardcoded + remotely updated)
   - Hash management: `spotify/src/main/kotlin/com/metrolist/spotify/SpotifyHashProvider.kt`
   - Hash source: Community-maintained GitHub registry (automated CI check via `spotify-hash-check.yml`)
   - Operations: profile, library, playlists, albums, artists, search, what's new feed, playlist mutations

2. **REST API** (fallback):
   - URL: `https://api.spotify.com/v1/`
   - Used for: top tracks/artists, recommendations, related artists
   - HTTP Engine: Ktor with OkHttp

**Authentication Flow:**
1. User logs in via WebView at `https://accounts.spotify.com/login`
2. `sp_dc` cookie is extracted from the WebView session
3. TOTP secret fetched from community GitHub Gist (`https://api.github.com/gists/22ed9c6ba463899e933427f7de1f0eef`)
4. Server time fetched from `https://open.spotify.com/api/server-time`
5. 6-digit TOTP generated (HMAC-SHA1, RFC 6238, 30s interval)
6. Access token obtained from `https://open.spotify.com/api/token` with TOTP + cookie
7. No Spotify Developer Client ID required

**Models:** `spotify/src/main/kotlin/com/metrolist/spotify/models/` (SpotifyTrack, SpotifyPlaylist, SpotifyAlbum, SpotifyArtist, SpotifyUser, etc.)
**Mapper:** `spotify/src/main/kotlin/com/metrolist/spotify/SpotifyMapper.kt` - Converts Spotify GQL JSON to typed models

---

### Last.fm (Scrobbling & Social)

- **Module:** `lastfm/`
- **Client:** `lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt`
- **Base URL:** `https://ws.audioscrobbler.com/2.0/`
- **Protocol:** REST with form-encoded POST requests
- **Auth:** API key + secret (provided via `BuildConfig` from `local.properties` or GitHub Secrets env vars)
- **Session:** Mobile session auth (`auth.getMobileSession`) or OAuth token flow (`auth.getToken` + `auth.getSession`)
- **Signing:** MD5 API signature on sorted parameters + secret
- **HTTP Engine:** Ktor with OkHttp

**Operations:**
- `auth.getToken` / `auth.getSession` - OAuth authentication
- `auth.getMobileSession` - Username/password authentication
- `track.updateNowPlaying` - Now playing status
- `track.scrobble` - Submit listened tracks
- `track.love` / `track.unlove` - Like/unlike tracks

**Env Vars:**
- `LASTFM_API_KEY` - API key (build config)
- `LASTFM_SECRET` - API secret (build config)

---

### Shazam (Music Recognition)

- **Module:** `shazamkit/`
- **Client:** `shazamkit/src/main/kotlin/com/metrolist/shazamkit/Shazam.kt`
- **Endpoint:** `https://amp.shazam.com/discovery/v5/en/US/android/-/tag/{uuid1}/{uuid2}`
- **Protocol:** REST (POST with JSON body)
- **Auth:** None (public API with rate limiting)
- **HTTP Engine:** Ktor with CIO

**Features:**
- Built-in rate limiting (max 2 concurrent requests, 1s minimum interval)
- Exponential backoff retry (3 attempts, starting at 2s)
- Request queue (max 50 pending)
- Result caching (5-minute TTL, max 100 entries)
- Random user agent and timezone rotation for anti-detection

**Input/Output:**
- Input: Audio signature (DejaVu format) + sample duration
- Output: `RecognitionResult` with title, artist, album, cover art, ISRC, links to Apple Music/Spotify/YouTube
- Models: `shazamkit/src/main/kotlin/com/metrolist/shazamkit/models/ShazamModels.kt`

---

### LrcLib (Synchronized Lyrics)

- **Module:** `lrclib/`
- **Client:** `lrclib/src/main/kotlin/com/metrolist/lrclib/LrcLib.kt`
- **Base URL:** `https://lrclib.net`
- **Endpoint:** `/api/search`
- **Protocol:** REST (GET with query parameters)
- **Auth:** None (public API)
- **HTTP Engine:** Ktor with CIO

**Search Strategy (5-step fallback):**
1. Cleaned title + cleaned artist + album
2. Cleaned title only
3. Combined query (`artist title`) via `q` parameter
4. Title only via `q` parameter
5. Original (uncleaned) title + artist

**Features:**
- Title cleanup (removes "official video", "feat.", parenthetical metadata)
- Artist cleanup (extracts primary artist before separators)
- Duration-based matching (relaxed ±5s tolerance)
- String similarity scoring (Levenshtein distance)
- Returns synced lyrics (LRC format) or plain lyrics

---

### KuGou (Chinese Lyrics)

- **Module:** `kugou/`
- **Client:** `kugou/src/main/kotlin/com/metrolist/kugou/KuGou.kt`
- **Protocol:** REST (GET requests)
- **Auth:** None

**Endpoints:**
- `https://mobileservice.kugou.com/api/v3/search/song` - Song search
- `https://lyrics.kugou.com/search` - Lyrics search (by keyword or hash)
- `https://lyrics.kugou.com/download` - Lyrics download (Base64-encoded LRC)

**Features:**
- CJK text normalization (Chinese/Japanese bracket removal)
- Artist separator normalization
- Duration tolerance: ±8 seconds
- Traditional Chinese support flag
- HTTP Engine: Ktor with OkHttp + gzip/deflate

---

### BetterLyrics (TTML Lyrics)

- **Module:** `betterlyrics/`
- **Client:** `betterlyrics/src/main/kotlin/com/metrolist/music/betterlyrics/BetterLyrics.kt`
- **Base URL:** `https://lyrics-api.boidu.dev`
- **Endpoint:** `/getLyrics`
- **Protocol:** REST (GET with query parameters: `s`, `a`, `d`, `al`)
- **Auth:** None
- **HTTP Engine:** Ktor with CIO (15s timeout)

**Features:**
- Returns TTML (Timed Text Markup Language) lyrics
- TTML parser: `betterlyrics/src/main/kotlin/com/metrolist/music/betterlyrics/TTMLParser.kt`
- Converts TTML to LRC format for consistent display

---

### SimpMusic Lyrics

- **Module:** `simpmusic/`
- **Client:** `simpmusic/src/main/kotlin/com/metrolist/simpmusic/SimpMusicLyrics.kt`
- **Base URL:** `https://api-lyrics.simpmusic.org/v1/`
- **Endpoint:** `/{videoId}` (YouTube video ID)
- **Protocol:** REST (GET)
- **Auth:** None
- **HTTP Engine:** Ktor with CIO (15s timeout)

**Features:**
- Looks up lyrics by YouTube video ID (not by title/artist)
- Supports rich sync lyrics (word-by-word), synced lyrics (line-by-line), and plain lyrics
- Duration matching with ±10s tolerance
- Priority: richSyncLyrics > syncedLyrics > plainLyrics

---

### Discord Rich Presence (Kizzy)

- **Module:** `kizzy/`
- **Client:** `kizzy/src/main/kotlin/com/my/kizzy/rpc/KizzyRPC.kt`
- **Gateway:** `kizzy/src/main/kotlin/com/my/kizzy/gateway/DiscordWebSocket.kt`
- **Protocol:** WebSocket (Discord Gateway API v9)
- **REST API:** `https://discord.com/api/v9/users/@me` (user info)
- **Auth:** Discord user token (provided by user)
- **HTTP Engine:** Ktor with OkHttp + WebSocket

**Features:**
- Sets "Listening to" activity status on Discord
- Supports custom assets (large/small images via external asset upload)
- Activity buttons with URLs
- Timestamps (start/end)
- Multiple activity types: Playing, Streaming, Listening, Watching, Competing
- Heartbeat management, reconnection, session resumption
- External asset resolution via Discord API: `kizzy/src/main/kotlin/com/my/kizzy/rpc/ExternalAssets.kt`
- Artwork caching: `kizzy/src/main/kotlin/com/my/kizzy/rpc/ArtworkCache.kt`

---

### Listen Together (WebSocket P2P)

- **Location:** `app/src/main/kotlin/com/metrolist/music/listentogether/`
- **Protocol:** WebSocket (OkHttp-based, custom protocol)
- **Server:** Configurable (multiple community servers)
- **Auth:** Session token-based

**Key Files:**
- `ListenTogetherClient.kt` - WebSocket client with reconnection logic
- `ListenTogetherManager.kt` - Session management, host/guest logic
- `Protocol.kt` - Message type definitions
- `MessageCodec.kt` - Message serialization
- `ListenTogetherServers.kt` - Server URL configuration

**Protocol Messages:**
- Room management: create, join, leave, approve/reject, kick, transfer host
- Playback sync: playback actions, buffer ready signals, sync requests
- Social: chat messages, track suggestions with approval workflow
- Connection: ping, reconnect

---

### Return YouTube Dislike

- **Location:** Referenced in `innertube/src/main/kotlin/com/metrolist/innertube/InnerTube.kt`
- **Model:** `innertube/src/main/kotlin/com/metrolist/innertube/models/ReturnYouTubeDislikeResponse.kt`
- **Protocol:** REST
- **Purpose:** Fetches video dislike counts from community API

---

## Data Storage

**Databases:**
- Room/SQLite via `InternalDatabase` class
  - Database name: configured in `InternalDatabase.DB_NAME`
  - Entities: SongEntity, AlbumEntity, ArtistEntity, PlaylistEntity, LyricsEntity, Event, SearchHistory, FormatEntity, PlayCountEntity, RecognitionHistory, SpotifyMatchEntity, SpeedDialItem, PodcastEntity, and various mapping tables
  - DAO: `app/src/main/kotlin/com/metrolist/music/db/DatabaseDao.kt`
  - Additional DAO: `app/src/main/kotlin/com/metrolist/music/db/daos/SpeedDialDao.kt`
  - Schema migrations tracked in `app/schemas/`

**File Storage:**
- ExoPlayer cache: `{filesDir}/exoplayer/` (configurable size, LRU eviction)
- Download cache: `{filesDir}/download/` (no eviction)

**Preferences:**
- AndroidX DataStore Preferences (`datastore-preferences 1.2.0`)

**Caching:**
- In-memory: Shazam result cache (5-min TTL)
- In-memory: Spotify GQL hash cache (ConcurrentHashMap)
- Disk: ExoPlayer media cache (configurable max size)

## Authentication & Identity

**YouTube Music:**
- Cookie-based login (optional)
- Visitor data for anonymous tracking
- DataSync ID for logged-in user context
- Proxy support with optional auth

**Spotify:**
- WebView cookie-based (`sp_dc` + optional `sp_key`)
- TOTP-based token endpoint (no developer credentials needed)
- Token stored as volatile in-memory (`Spotify.accessToken`)

**Last.fm:**
- API key + secret (compile-time via BuildConfig)
- Session key obtained via mobile auth or OAuth
- Session key stored at runtime (`LastFM.sessionKey`)

**Discord:**
- User token provided directly by user
- Used for WebSocket gateway authentication and REST API calls

**Shazam / LrcLib / KuGou / BetterLyrics / SimpMusic:**
- No authentication required (public APIs)

## Monitoring & Observability

**Logging:**
- Timber 5.0.1 - Structured logging in app and innertube modules
- Java util logging in kizzy gateway module
- Custom logger callback in Spotify module (`Spotify.logger`)

**Error Tracking:**
- No external crash reporting service detected
- Kotlin `Result<T>` / `runCatching` pattern used extensively across all API modules

## Webhooks & Callbacks

**Incoming:**
- None detected

**Outgoing:**
- Last.fm scrobbling (fire-and-forget POST)
- Discord presence updates (WebSocket)
- Listen Together playback sync (WebSocket)

## API Client Patterns

All API modules follow a consistent pattern:

1. **Singleton object** with lazy-initialized `HttpClient`
2. **Ktor HttpClient** with `ContentNegotiation` (kotlinx.serialization JSON)
3. **`runCatching`** wrapper returning `Result<T>` for error handling
4. **`suspend` functions** for all network operations
5. **No dependency injection** in library modules (DI only in app module)

**HTTP Engine Selection:**
- OkHttp engine: Used when advanced features needed (proxying, WebSocket, connection pooling) - innertube, spotify, lastfm, kugou, kizzy
- CIO engine: Used for simpler HTTP-only clients - lrclib, shazamkit, betterlyrics, simpmusic

**Data Flow:**
```
app (UI/ViewModel)
  -> YouTube object (innertube module) -> InnerTube HTTP client -> YouTube Music API
  -> Spotify object (spotify module) -> Ktor clients -> Spotify GraphQL/REST APIs
  -> LastFM object (lastfm module) -> Ktor client -> Last.fm API
  -> Shazam object (shazamkit module) -> Ktor client -> Shazam API
  -> LrcLib / KuGou / BetterLyrics / SimpMusic -> respective lyrics APIs
  -> KizzyRPC (kizzy module) -> Discord WebSocket Gateway
  -> ListenTogetherClient (app module) -> Custom WebSocket server
```

---

*Integration audit: 2026-04-03*
