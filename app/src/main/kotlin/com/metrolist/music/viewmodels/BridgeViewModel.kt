/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.metrolist.lastfm.LastFM
import com.metrolist.music.bridge.MeldBridgeInterface
import com.metrolist.music.di.BridgeWebView
import com.metrolist.music.playback.BridgePlaylistBuilder
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.screens.bridge.BridgeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

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
     * Handler for posting WebView calls to the main thread.
     * Lazy so Looper.getMainLooper() is only accessed at first call (not during construction),
     * enabling JVM unit tests to construct BridgeViewModel without Android mocks.
     */
    internal val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    init {
        // Wire MeldBridgeInterface callbacks to update _uiState.
        // onStateChange is a mutable var property (not constructor param) — assigned post-construction
        // because Hilt creates the singleton MeldBridgeInterface before this ViewModel exists.
        // Intercept PathFound to automatically trigger playlist building (PLAY-01).
        meldBridgeInterface.onStateChange = { newState ->
            _uiState.value = newState
            if (newState is BridgeUiState.PathFound) {
                viewModelScope.launch { buildPlaylist(newState.path) }
                viewModelScope.launch(Dispatchers.IO) { fetchArtistMetadata(newState.path) }
            }
        }
    }

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
     * Kick off a bridge search from [startArtist] to [endArtist].
     *
     * Rejects the request if a bridge is already running (D-08 concurrency guard).
     * Artist names are escaped via JSONObject.quote() before injection into JS (Research Pitfall 5).
     * evaluateJavascript must be called on the main thread (Research Pitfall 4).
     */
    fun startBridge(startArtist: String, endArtist: String) {
        if (isRunning) {
            Timber.tag("MeldBridge").d("Bridge already running, ignoring request")
            return
        }
        _uiState.value = BridgeUiState.Searching()
        Timber.tag("MeldBridge").d("Starting bridge: %s -> %s", startArtist, endArtist)

        // JSONObject.quote() returns the value surrounded by double-quotes, safe for any artist name
        val escapedStart = JSONObject.quote(startArtist)
        val escapedEnd = JSONObject.quote(endArtist)
        val script = "window.startBridge($escapedStart, $escapedEnd)"

        // evaluateJavascript must run on main thread per Research Pitfall 4
        mainHandler.post {
            webView.evaluateJavascript(script, null)
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
