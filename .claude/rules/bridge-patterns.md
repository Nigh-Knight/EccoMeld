---
globs: "app/src/main/kotlin/com/metrolist/music/bridge/**/*.kt,app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/**/*.kt,app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt"
---

# Bridge & Path Walker Patterns

- `BridgeAlgorithm` — bidirectional beam search on Last.fm similarity graph
- `KotlinBridgeCache` — L1 ConcurrentHashMap + L2 Room. `purgeComplete.await()` gates all reads
- `BridgePlaylistBuilder` — progressive streaming, 1-2 tracks per artist, parallel resolution
- Always `.trim()` artist names before ANY Last.fm API call
- `SpotifyMapper.matchScore` uses `\p{L}\p{N}` regex — preserves CJK/Unicode
- Video fallback: if FILTER_SONG returns empty, retry with FILTER_VIDEO
- Collab splitting: `splitCollabArtist()` handles "Artist A & Artist B" patterns
- Track name cleaning: `cleanTrackName()` strips artist prefix and [Explicit] tags
- `BridgeCard.isActive` tracks which card owns the current playback queue
- Path Walker will have separate `PathWalkerViewModel` — do NOT extend BridgeViewModel
