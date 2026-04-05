/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.metrolist.lastfm.LastFM
import com.metrolist.music.bridge.BridgeAlgorithm
import com.metrolist.music.bridge.MeldBridgeInterface
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.di.BridgeWebView
import com.metrolist.music.playback.BridgePlaylistBuilder
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.screens.bridge.BridgeUiState
import com.metrolist.spotify.Spotify
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Whether an artist in the bridge path is known to the user (in their library) or new.
 * Resolved by [BridgeViewModel.resolveFamiliarity] via dual-source lookup (SPOT-03).
 */
enum class ArtistFamiliarity { KNOWN, NEW }

/**
 * Per-artist metadata fetched from Last.fm after a bridge path is found.
 * Populated by [BridgeViewModel.fetchArtistMetadata] (BRDG-05).
 */
data class BridgeArtistInfo(
    val name: String,
    val tags: List<String>,
    val listenerCount: Long,
    val formattedListeners: String,
)

/**
 * Format a raw listener count into a human-readable string.
 * - < 1,000: raw count ("847 listeners")
 * - 1,000–999,999: K with one decimal ("1.2K listeners")
 * - >= 1,000,000: M with one decimal ("1.2M listeners")
 */
fun formatListeners(count: Long): String = when {
    count < 1_000L -> "$count listeners"
    count < 1_000_000L -> "${"%.1f".format(count / 1_000.0)}K listeners"
    else -> "${"%.1f".format(count / 1_000_000.0)}M listeners"
}

@HiltViewModel
class BridgeViewModel @Inject constructor(
    @BridgeWebView private val webView: WebView,
    private val meldBridgeInterface: MeldBridgeInterface,
    private val playlistBuilder: BridgePlaylistBuilder,
    private val database: MusicDatabase,
    private val bridgeAlgorithm: BridgeAlgorithm,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BridgeUiState>(BridgeUiState.Idle)
    val uiState: StateFlow<BridgeUiState> = _uiState.asStateFlow()

    /**
     * True when a bridge search is in progress. Derived from uiState so it stays in sync
     * with MeldBridgeInterface callbacks — no separate flag needed (Research open question 3).
     */
    val isRunning: Boolean
        get() = _uiState.value is BridgeUiState.Searching

    // --- Playlist building state (PLAY-01, D-06, D-07, D-08) ---

    private val _isBuilding = MutableStateFlow(false)
    val isBuilding: StateFlow<Boolean> = _isBuilding.asStateFlow()

    private val _showQueueDialog = MutableStateFlow(false)
    val showQueueDialog: StateFlow<Boolean> = _showQueueDialog.asStateFlow()

    private val _buildFailed = MutableStateFlow(false)
    val buildFailed: StateFlow<Boolean> = _buildFailed.asStateFlow()

    private val _artistMetadata = MutableStateFlow<Map<String, BridgeArtistInfo>>(emptyMap())
    val artistMetadata: StateFlow<Map<String, BridgeArtistInfo>> = _artistMetadata.asStateFlow()

    // --- Spotify seed suggestions state (SPOT-01, SPOT-02) ---

    private val _seedSuggestions = MutableStateFlow<List<String>>(emptyList())
    val seedSuggestions: StateFlow<List<String>> = _seedSuggestions.asStateFlow()

    private val _isLoadingSeeds = MutableStateFlow(false)
    val isLoadingSeeds: StateFlow<Boolean> = _isLoadingSeeds.asStateFlow()

    private val _artistFamiliarity = MutableStateFlow<Map<String, ArtistFamiliarity>>(emptyMap())
    val artistFamiliarity: StateFlow<Map<String, ArtistFamiliarity>> = _artistFamiliarity.asStateFlow()

    private val _isFabLoading = MutableStateFlow(false)
    val isFabLoading: StateFlow<Boolean> = _isFabLoading.asStateFlow()

    /** Emits a one-shot toast message. UI calls [clearRandomBridgeToast] after showing it. */
    private val _randomBridgeToast = MutableStateFlow<String?>(null)
    val randomBridgeToast: StateFlow<String?> = _randomBridgeToast.asStateFlow()

    /** Idempotent guard — prevents re-fetching seed suggestions on tab re-entry. */
    private var _seedSuggestionsLoaded = false

    /** In-memory cache of Last.fm tags per artist name, used for Jaccard distance. */
    private val _artistTagCache = mutableMapOf<String, Set<String>>()

    private var _pendingPlaylistItems: List<MediaItem>? = null
    private var _currentPath: List<String> = emptyList()

    val pendingTrackCount: Int get() = _pendingPlaylistItems?.size ?: 0

    // --- Autocomplete state (BRDG-01, D-01, D-02, D-05) ---

    private val _fromQuery = MutableStateFlow("")
    val fromQuery: StateFlow<String> = _fromQuery.asStateFlow()

    private val _toQuery = MutableStateFlow("")
    val toQuery: StateFlow<String> = _toQuery.asStateFlow()

    private val _fromGhostSuffix = MutableStateFlow("")
    val fromGhostSuffix: StateFlow<String> = _fromGhostSuffix.asStateFlow()

    private val _toGhostSuffix = MutableStateFlow("")
    val toGhostSuffix: StateFlow<String> = _toGhostSuffix.asStateFlow()

    // Full suggestion name when ghost is showing (for confirm action)
    private var _fromGhostFull = ""
    private var _toGhostFull = ""

    // Confirmed artist names passed to startBridge()
    private val _fromConfirmedArtist = MutableStateFlow("")
    val fromConfirmedArtist: StateFlow<String> = _fromConfirmedArtist.asStateFlow()

    private val _toConfirmedArtist = MutableStateFlow("")
    val toConfirmedArtist: StateFlow<String> = _toConfirmedArtist.asStateFlow()

    // Debounce jobs (D-02: 300ms debounce matching EccoPath)
    private var fromSearchJob: Job? = null
    private var toSearchJob: Job? = null

    /**
     * Builds a playlist from the resolved bridge path. Called automatically on PathFound.
     * Shows queue dialog when tracks are ready (D-06). Sets buildFailed if no tracks resolve.
     */
    private suspend fun buildPlaylist(path: List<String>) {
        _currentPath = path
        _artistMetadata.value = emptyMap()
        _isBuilding.value = true
        _buildFailed.value = false
        try {
            val items = playlistBuilder.buildForPath(path)
            _pendingPlaylistItems = items
            if (items.isNotEmpty()) {
                _showQueueDialog.value = true  // per D-06: show dialog
            } else {
                // All matches failed — stay on PathFound, show error message
                _buildFailed.value = true
                Timber.w("BridgePlaylist: no playable tracks resolved for path")
            }
        } finally {
            _isBuilding.value = false
        }
    }

    /**
     * Replace the entire queue with the bridge playlist and start playback immediately (D-08).
     * PlayerConnection passed from composable — not stored in ViewModel (avoids Context leak).
     */
    fun onConfirmReplaceQueue(playerConnection: PlayerConnection) {
        val items = _pendingPlaylistItems ?: return
        _showQueueDialog.value = false
        val from = _fromConfirmedArtist.value
        val to = _toConfirmedArtist.value
        playerConnection.playQueue(
            ListQueue(
                title = "Bridge: $from \u2192 $to",
                items = items,
                startIndex = 0,
            )
        )
        _uiState.value = BridgeUiState.PlaylistReady(path = _currentPath, nowPlayingIndex = 0)
    }

    /**
     * Insert the bridge playlist immediately after the current track (D-07).
     * PlayerConnection passed from composable — not stored in ViewModel (avoids Context leak).
     */
    fun onConfirmPlayNext(playerConnection: PlayerConnection) {
        val items = _pendingPlaylistItems ?: return
        _showQueueDialog.value = false
        playerConnection.playNext(items)
        _uiState.value = BridgeUiState.PlaylistReady(path = _currentPath, nowPlayingIndex = 0)
    }

    /**
     * Dismiss the queue dialog without starting playback.
     */
    fun dismissQueueDialog() {
        _showQueueDialog.value = false
    }

    /**
     * Kick off a bridge search from [startArtist] to [endArtist] using the native Kotlin algorithm.
     *
     * Rejects the request if a bridge is already running (D-08 concurrency guard).
     * Calls [BridgeAlgorithm.findBridge] directly — no WebView or evaluateJavascript involved.
     * Progress updates flow into [BridgeUiState.Searching]. On success, triggers playlist building,
     * metadata fetch, and familiarity resolution automatically (PLAY-01).
     */
    fun startBridge(startArtist: String, endArtist: String) {
        if (isRunning) {
            Timber.tag("MeldBridge").d("Bridge already running, ignoring request")
            return
        }
        _uiState.value = BridgeUiState.Searching()
        Timber.tag("MeldBridge").d("Starting native bridge: %s -> %s", startArtist, endArtist)

        viewModelScope.launch(Dispatchers.IO) {
            val result = bridgeAlgorithm.findBridge(startArtist, endArtist) { progress ->
                _uiState.value = BridgeUiState.Searching(
                    message = progress.message,
                    progress = progress.progress,
                    foundHops = progress.depth,
                    totalHops = progress.maxDepth,
                )
            }
            withContext(Dispatchers.Main) {
                if (result.found) {
                    _uiState.value = BridgeUiState.PathFound(result.path)
                    viewModelScope.launch { buildPlaylist(result.path) }
                    viewModelScope.launch(Dispatchers.IO) { fetchArtistMetadata(result.path) }
                    viewModelScope.launch(Dispatchers.IO) { resolveFamiliarity(result.path) }
                } else {
                    _uiState.value = BridgeUiState.Error("No bridge path found. Try different artists.")
                }
            }
        }
    }

    /**
     * Fetch Last.fm artist.getInfo in parallel for all artists in [path].
     * Populates [_artistMetadata] with tags and formatted listener counts (BRDG-05).
     * Missing or failed artists are silently skipped — UI shows empty fallback.
     */
    private suspend fun fetchArtistMetadata(path: List<String>) {
        val results = path.map { artist ->
            viewModelScope.async(Dispatchers.IO) {
                LastFM.getArtistInfo(artist)
                    .getOrNull()
                    ?.let { resp ->
                        artist to BridgeArtistInfo(
                            name = resp.artist.name.ifEmpty { artist },
                            tags = resp.artist.tags.tag.map { it.name },
                            listenerCount = resp.artist.stats.listeners.toLongOrNull() ?: 0L,
                            formattedListeners = formatListeners(resp.artist.stats.listeners.toLongOrNull() ?: 0L),
                        )
                    }
            }
        }.awaitAll()
        _artistMetadata.value = results.filterNotNull().toMap()
    }

    /**
     * Load seed artist suggestions from YT Music library + Spotify followed artists.
     * Deduplicates by lowercase name, caps at 20. Falls back gracefully if Spotify auth fails.
     * Idempotent — subsequent calls are no-ops (SPOT-01, D-01, D-02, D-03, D-04, D-12, D-13).
     */
    fun loadSeedSuggestions() {
        if (_seedSuggestionsLoaded) return
        _isLoadingSeeds.value = true
        viewModelScope.launch(Dispatchers.IO) {
            // Fetch YT Music library artists
            val ytArtists: List<String> = try {
                database.artistsByCreateDateAsc().first().map { it.artist.name }
            } catch (e: Exception) {
                Timber.tag("BridgeSuggestions").e(e, "YT Music artist fetch failed")
                emptyList()
            }

            // Fetch Spotify followed artists — silent fallback on any failure (D-13)
            val spotifyArtists: List<String> = try {
                val auth = com.metrolist.music.utils.SpotifyTokenManager.ensureAuthenticated()
                if (auth) {
                    Spotify.myArtists(limit = 50).getOrNull()?.items?.map { it.name } ?: emptyList()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Timber.tag("BridgeSuggestions").e(e, "Spotify fetch failed")
                emptyList()
            }

            // Merge, deduplicate by lowercase-trimmed name, cap at 20
            val merged = (ytArtists + spotifyArtists)
                .distinctBy { it.trim().lowercase() }
                .take(20)

            _seedSuggestions.value = merged
            _isLoadingSeeds.value = false
            _seedSuggestionsLoaded = true

            // Warm tag cache for Jaccard distance computation
            prefetchTagsForJaccard(merged)
        }
    }

    /**
     * Pre-fetch Last.fm tags for all seed artists in parallel. Populates [_artistTagCache].
     */
    private suspend fun prefetchTagsForJaccard(artists: List<String>) {
        val results = artists.map { artist ->
            viewModelScope.async(Dispatchers.IO) {
                val tags = LastFM.getArtistInfo(artist)
                    .getOrNull()
                    ?.artist?.tags?.tag
                    ?.map { it.name.lowercase() }
                    ?.toSet()
                    ?: emptySet()
                artist to tags
            }
        }.awaitAll()
        synchronized(_artistTagCache) {
            results.forEach { (artist, tags) -> _artistTagCache[artist] = tags }
        }
        Timber.tag("BridgeSuggestions").d("Prefetched tags for %d artists", artists.size)
    }

    /**
     * Compute Jaccard similarity between two tag sets.
     * Returns 1.0 when both sets are empty (treat as identical / no signal for diversity).
     */
    internal fun jaccardSimilarity(tagsA: Set<String>, tagsB: Set<String>): Double {
        if (tagsA.isEmpty() && tagsB.isEmpty()) return 1.0
        val intersection = tagsA.intersect(tagsB).size
        val union = tagsA.union(tagsB).size
        return if (union == 0) 1.0 else intersection.toDouble() / union
    }

    /**
     * Pick the most genre-diverse pair from [tagMap] using pairwise Jaccard similarity.
     * Falls back to first 2 keys when fewer than 2 artists have tags.
     * Returns null when fewer than 2 artists are present.
     */
    internal fun pickMostDiversePair(tagMap: Map<String, Set<String>>): Pair<String, String>? {
        val keys = tagMap.keys.toList()
        if (keys.size < 2) return null

        // Prefer artists with at least 1 tag for diversity scoring
        val tagged = keys.filter { tagMap[it]?.isNotEmpty() == true }
        val candidates = if (tagged.size >= 2) tagged else keys

        var bestPair: Pair<String, String>? = null
        var minSimilarity = Double.MAX_VALUE

        for (i in candidates.indices) {
            for (j in i + 1 until candidates.size) {
                val a = candidates[i]
                val b = candidates[j]
                val sim = jaccardSimilarity(tagMap[a] ?: emptySet(), tagMap[b] ?: emptySet())
                if (sim < minSimilarity) {
                    minSimilarity = sim
                    bestPair = a to b
                }
            }
        }
        return bestPair
    }

    /**
     * Launch a Random Bridge using the most genre-diverse pair from seed suggestions.
     * [toastMessage] is the string resource value for the "not enough artists" toast.
     * Callers pass the string resource to avoid Context in ViewModel (SPOT-02, D-05 to D-08).
     */
    fun randomBridge(toastMessage: String) {
        if (isRunning || _isFabLoading.value) return
        if (_seedSuggestions.value.size < 2) {
            _randomBridgeToast.value = toastMessage
            return
        }
        _isFabLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            // Ensure tag cache is warm
            if (_artistTagCache.isEmpty()) {
                prefetchTagsForJaccard(_seedSuggestions.value)
            }

            val tagSnapshot = synchronized(_artistTagCache) { _artistTagCache.toMap() }
            val pair = pickMostDiversePair(tagSnapshot)
                ?: (_seedSuggestions.value[0] to _seedSuggestions.value[1])

            withContext(Dispatchers.Main) {
                _fromQuery.value = pair.first
                _toQuery.value = pair.second
                _fromConfirmedArtist.value = pair.first
                _toConfirmedArtist.value = pair.second
            }
            startBridge(pair.first, pair.second)
            _isFabLoading.value = false
        }
    }

    /**
     * Resolve familiarity (KNOWN/NEW) for each artist in [path] using dual-source lookup:
     * YT Music library artists + Spotify followed artists. Case-insensitive (SPOT-03, D-09, D-10).
     */
    private suspend fun resolveFamiliarity(path: List<String>) {
        // Fetch YT Music artist names
        val ytNames: Set<String> = try {
            database.artistsByCreateDateAsc().first()
                .map { it.artist.name.trim().lowercase() }
                .toSet()
        } catch (e: Exception) {
            Timber.tag("BridgeFamiliarity").e(e, "YT Music fetch failed")
            emptySet()
        }

        // Fetch Spotify artist names — silent fallback
        val spotifyNames: Set<String> = try {
            val auth = com.metrolist.music.utils.SpotifyTokenManager.ensureAuthenticated()
            if (auth) {
                Spotify.myArtists(limit = 50).getOrNull()
                    ?.items?.map { it.name.trim().lowercase() }?.toSet()
                    ?: emptySet()
            } else {
                emptySet()
            }
        } catch (e: Exception) {
            Timber.tag("BridgeFamiliarity").e(e, "Spotify fetch failed")
            emptySet()
        }

        val knownNames = ytNames + spotifyNames
        _artistFamiliarity.value = path.associateWith { artistName ->
            if (artistName.trim().lowercase() in knownNames) ArtistFamiliarity.KNOWN
            else ArtistFamiliarity.NEW
        }
    }

    /**
     * Reset the one-shot random bridge toast after the UI has shown it.
     */
    fun clearRandomBridgeToast() {
        _randomBridgeToast.value = null
    }

    /**
     * Called whenever the now-playing media item's artist name changes.
     * Finds the artist in the current path via case-insensitive, trimmed match and updates
     * [BridgeUiState.PlaylistReady.nowPlayingIndex] accordingly (BRDG-05, D-05).
     *
     * No-op if the artist is not found in the path — preserves the last known highlight.
     * No-op if the current state is not [BridgeUiState.PlaylistReady].
     */
    fun onNowPlayingArtistChanged(artistName: String) {
        val path = _currentPath
        if (path.isEmpty()) return
        val idx = path.indexOfFirst {
            it.trim().equals(artistName.trim(), ignoreCase = true)
        }
        if (idx >= 0) {
            val current = _uiState.value
            if (current is BridgeUiState.PlaylistReady && current.nowPlayingIndex != idx) {
                _uiState.value = current.copy(nowPlayingIndex = idx)
            }
        }
        // No-op when idx == -1: preserve last known highlight (per D-05)
    }

    /**
     * Reset UI state to Idle. No-op when a bridge is running to avoid interrupting in-flight searches.
     */
    fun resetState() {
        if (!isRunning) {
            _uiState.value = BridgeUiState.Idle
        }
    }

    /**
     * Called on every keystroke in the "From" input. Debounces 300ms before
     * querying LastFM.searchArtists() for ghost-text autocomplete (D-02).
     * Clears confirmed artist when user edits (prevents stale confirmed name).
     */
    fun onFromQueryChanged(query: String) {
        _fromQuery.value = query
        _fromConfirmedArtist.value = ""
        fromSearchJob?.cancel()
        if (query.isBlank()) {
            _fromGhostSuffix.value = ""
            _fromGhostFull = ""
            return
        }
        fromSearchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300L)
            LastFM.searchArtists(query, 1)
                .onSuccess { response ->
                    val suggestion = response.results.artistmatches.artist.firstOrNull()?.name ?: ""
                    if (suggestion.startsWith(query, ignoreCase = true)) {
                        _fromGhostSuffix.value = suggestion.drop(query.length)
                        _fromGhostFull = suggestion
                    } else {
                        _fromGhostSuffix.value = ""
                        _fromGhostFull = ""
                    }
                }
                .onFailure {
                    _fromGhostSuffix.value = ""
                    _fromGhostFull = ""
                }
        }
    }

    /**
     * Called on every keystroke in the "To" input. Same debounce pattern as onFromQueryChanged.
     */
    fun onToQueryChanged(query: String) {
        _toQuery.value = query
        _toConfirmedArtist.value = ""
        toSearchJob?.cancel()
        if (query.isBlank()) {
            _toGhostSuffix.value = ""
            _toGhostFull = ""
            return
        }
        toSearchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300L)
            LastFM.searchArtists(query, 1)
                .onSuccess { response ->
                    val suggestion = response.results.artistmatches.artist.firstOrNull()?.name ?: ""
                    if (suggestion.startsWith(query, ignoreCase = true)) {
                        _toGhostSuffix.value = suggestion.drop(query.length)
                        _toGhostFull = suggestion
                    } else {
                        _toGhostSuffix.value = ""
                        _toGhostFull = ""
                    }
                }
                .onFailure {
                    _toGhostSuffix.value = ""
                    _toGhostFull = ""
                }
        }
    }

    /**
     * Confirm the "From" ghost suggestion — fills input with full artist name (D-05).
     * If ghost is stale (doesn't match current input), falls back to raw input.
     */
    fun confirmFrom() {
        val current = _fromQuery.value
        val full = _fromGhostFull
        if (full.isNotEmpty() && full.startsWith(current, ignoreCase = true)) {
            _fromQuery.value = full
            _fromConfirmedArtist.value = full
        } else {
            _fromConfirmedArtist.value = current
        }
        _fromGhostSuffix.value = ""
        _fromGhostFull = ""
        fromSearchJob?.cancel()
    }

    /**
     * Confirm the "To" ghost suggestion — same pattern as confirmFrom().
     */
    fun confirmTo() {
        val current = _toQuery.value
        val full = _toGhostFull
        if (full.isNotEmpty() && full.startsWith(current, ignoreCase = true)) {
            _toQuery.value = full
            _toConfirmedArtist.value = full
        } else {
            _toConfirmedArtist.value = current
        }
        _toGhostSuffix.value = ""
        _toGhostFull = ""
        toSearchJob?.cancel()
    }

    /**
     * Trigger bridge search using confirmed artist names. Cancels any pending autocomplete.
     * Called by "Find Bridge" button (D-09 — button only enabled when both confirmed and not running).
     */
    fun findBridge() {
        val from = _fromConfirmedArtist.value
        val to = _toConfirmedArtist.value
        if (from.isBlank() || to.isBlank()) return
        // Cancel pending autocomplete to avoid stale ghost during search
        fromSearchJob?.cancel()
        toSearchJob?.cancel()
        _fromGhostSuffix.value = ""
        _toGhostSuffix.value = ""
        startBridge(from, to)
    }
}
