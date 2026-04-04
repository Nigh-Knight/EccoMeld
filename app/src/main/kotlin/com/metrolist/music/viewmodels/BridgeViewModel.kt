/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import com.metrolist.music.bridge.MeldBridgeInterface
import com.metrolist.music.di.BridgeWebView
import com.metrolist.music.ui.screens.bridge.BridgeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class BridgeViewModel @Inject constructor(
    @BridgeWebView private val webView: WebView,
    private val meldBridgeInterface: MeldBridgeInterface,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BridgeUiState>(BridgeUiState.Idle)
    val uiState: StateFlow<BridgeUiState> = _uiState.asStateFlow()

    /**
     * True when a bridge search is in progress. Derived from uiState so it stays in sync
     * with MeldBridgeInterface callbacks — no separate flag needed (Research open question 3).
     */
    val isRunning: Boolean
        get() = _uiState.value is BridgeUiState.Searching

    init {
        // Wire MeldBridgeInterface callbacks to update _uiState.
        // onStateChange is a mutable var property (not constructor param) — assigned post-construction
        // because Hilt creates the singleton MeldBridgeInterface before this ViewModel exists.
        meldBridgeInterface.onStateChange = { newState ->
            _uiState.value = newState
        }
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
        Handler(Looper.getMainLooper()).post {
            webView.evaluateJavascript(script, null)
        }
    }

    /**
     * Reset UI state to Idle. No-op when a bridge is running to avoid interrupting in-flight searches.
     */
    fun resetState() {
        if (!isRunning) {
            _uiState.value = BridgeUiState.Idle
        }
    }
}
