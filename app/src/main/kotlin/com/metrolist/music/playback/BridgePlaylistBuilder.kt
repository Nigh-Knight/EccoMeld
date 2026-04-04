/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import androidx.media3.common.MediaItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.lastfm.LastFM
import com.metrolist.lastfm.models.ArtistTopTracksResponse
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.spotify.SpotifyMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Resolves a bridge artist path (list of artist names) into a list of playable MediaItems.
 *
 * For each artist in the path:
 *  1. Fetch ranked top tracks from Last.fm (up to 50).
 *  2. Select 2 popular (rank 1-2) + up to 5 deep cuts (rank 10-50).
 *  3. Resolve each track to a YT Music SongItem via fuzzy match (PLAY-02, PLAY-04, D-01..D-05).
 *
 * Failed Last.fm fetches and low-confidence YT matches are silently skipped (PLAY-04).
 */
class BridgePlaylistBuilder @Inject constructor() {

    companion object {
        internal const val MIN_MATCH_THRESHOLD = 0.35
    }

    /**
     * Builds a playlist MediaItem list for the given bridge path.
     * Artists are processed in order (seed → bridge artists → target) per D-03.
     * Tracks per artist are resolved in parallel; failures are silently skipped.
     */
    suspend fun buildForPath(path: List<String>): List<MediaItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MediaItem>()
        for (artist in path) {
            val topTracksResult = LastFM.getArtistTopTracks(artist)
            if (topTracksResult.isFailure) {
                Timber.w("BridgePlaylist: skip artist '$artist' — Last.fm fetch failed: ${topTracksResult.exceptionOrNull()?.message}")
                continue
            }
            val trackNames = selectTracks(topTracksResult.getOrThrow().toptracks.track)
            if (trackNames.isEmpty()) continue

            val resolved: List<MediaItem> = coroutineScope {
                trackNames.map { title ->
                    async { resolveOneTrack(artist, title) }
                }.map { it.await() }.filterNotNull()
            }
            result.addAll(resolved)
        }
        result
    }

    /**
     * Selects tracks from a ranked top-tracks list per D-01:
     *  - Popular: rank 1-2 (top chart hits)
     *  - Deep cuts: rank 10-50 (shuffled, up to 5)
     * Ranks 3-9 are intentionally excluded to avoid the "mid-tier" overlap.
     */
    internal fun selectTracks(tracks: List<ArtistTopTracksResponse.TopTrack>): List<String> {
        val popular = tracks.filter { it.rankInt in 1..2 }.map { it.name }
        val deepCuts = tracks.filter { it.rankInt in 10..50 }.shuffled().take(5).map { it.name }
        return popular + deepCuts
    }

    /**
     * Resolves a single artist+title pair to a MediaItem via YouTube Music fuzzy search.
     *
     * Scoring uses SpotifyMapper.matchScore (Dice-coefficient on title+artist, duration neutral).
     * Returns null if:
     *  - No SongItems found in search results
     *  - Best candidate scores below MIN_MATCH_THRESHOLD (0.35)
     */
    internal suspend fun resolveOneTrack(artist: String, title: String): MediaItem? {
        val searchResult = YouTube.search("$artist $title", YouTube.SearchFilter.FILTER_SONG)
        if (searchResult.isFailure) {
            Timber.w("BridgePlaylist: skip '$title' by $artist — YouTube search failed")
            return null
        }

        val songs = searchResult.getOrThrow().items.filterIsInstance<SongItem>()
        if (songs.isEmpty()) {
            Timber.w("BridgePlaylist: skip '$title' by $artist — no SongItems in results")
            return null
        }

        val best = songs.maxByOrNull { song ->
            SpotifyMapper.matchScore(
                spotifyTitle = title,
                spotifyArtist = artist,
                spotifyDurationMs = 0,
                candidateTitle = song.title,
                candidateArtist = song.artists.firstOrNull()?.name ?: "",
                candidateDurationSec = null,
            )
        } ?: return null

        val score = SpotifyMapper.matchScore(
            spotifyTitle = title,
            spotifyArtist = artist,
            spotifyDurationMs = 0,
            candidateTitle = best.title,
            candidateArtist = best.artists.firstOrNull()?.name ?: "",
            candidateDurationSec = null,
        )

        if (score < MIN_MATCH_THRESHOLD) {
            Timber.w("BridgePlaylist: skip '$title' by $artist (score=$score)")
            return null
        }

        return best.toMediaItem()
    }
}
