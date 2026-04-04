/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.bridge

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import com.metrolist.music.ui.screens.bridge.BridgeUiState

/**
 * Android WebView JS bridge interface for EccoPath integration.
 *
 * Registered on the WebView as "MeldBridge" (registration in BridgeModule, Phase 3 Plan 02).
 * JS calls these methods via `window.MeldBridge.createPlaylist(json)` etc.
 *
 * All @JavascriptInterface methods dispatch state updates to the main thread via
 * Handler(Looper.getMainLooper()).post { } before invoking onStateChange — required
 * because WebView callbacks arrive on a background thread (D-07).
 *
 * onStateChange and onReady are mutable var properties (NOT constructor params) so that
 * BridgeViewModel can assign the real callbacks after construction, and BridgeModule
 * can set onReady independently (WARNING 1 from plan: Hilt constructs the singleton
 * before BridgeViewModel exists).
 */
class MeldBridgeInterface {

    /** Callback invoked on the main thread when bridge state changes. Set by BridgeViewModel. */
    var onStateChange: (BridgeUiState) -> Unit = {}

    /** Callback invoked on the main thread when EccoPath JS signals it is ready. Set by BridgeModule (Plan 02). */
    var onReady: () -> Unit = {}

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Called by EccoPath JS when the bridge path search completes.
     *
     * Expected JSON shape (D-03/D-04):
     * ```json
     * { "found": true, "path": ["Artist A", "Artist B", "Artist C"] }
     * { "found": false }
     * ```
     */
    @JavascriptInterface
    fun createPlaylist(json: String) {
        Timber.tag("MeldBridge").d("createPlaylist: %s", json)
        try {
            val obj = JSONObject(json)
            val found = obj.optBoolean("found", false)
            if (found) {
                val pathArray: JSONArray = obj.getJSONArray("path")
                val path = mutableListOf<String>()
                for (i in 0 until pathArray.length()) {
                    path.add(pathArray.getString(i))
                }
                val state = BridgeUiState.PathFound(path)
                mainHandler.post { onStateChange(state) }
            } else {
                val state = BridgeUiState.Error("No bridge path found. Try different artists.")
                mainHandler.post { onStateChange(state) }
            }
        } catch (e: Exception) {
            Timber.tag("MeldBridge").e(e, "createPlaylist parse error: %s", e.message)
            val state = BridgeUiState.Error("Bridge result parse error")
            mainHandler.post { onStateChange(state) }
        }
    }

    /**
     * Called by EccoPath JS to report incremental progress during the beam search.
     *
     * Expected JSON shape (D-05/D-06):
     * ```json
     * { "phase": "searching", "message": "...", "progress": 0.4, "depth": 2, "maxDepth": 5 }
     * ```
     * Progress is best-effort — parse failures are silently logged and ignored.
     */
    @JavascriptInterface
    fun onProgress(json: String) {
        Timber.tag("MeldBridge").d("onProgress: %s", json)
        try {
            val obj = JSONObject(json)
            val depth = obj.optInt("depth", 0)
            val maxDepth = obj.optInt("maxDepth", 0)
            val state = BridgeUiState.Searching(foundHops = depth, totalHops = maxDepth)
            mainHandler.post { onStateChange(state) }
        } catch (e: Exception) {
            // Progress is best-effort per D-06 — silently log and continue
            Timber.tag("MeldBridge").d("onProgress parse error (ignored): %s", e.message)
        }
    }

    /**
     * Called by EccoPath JS after hydration to signal the bridge is ready to accept calls.
     *
     * This is the deterministic readiness signal per Research Pitfall 3, option 2 —
     * no polling needed. JS calls this from a useEffect after assigning window.__eccoFindBridge.
     */
    @JavascriptInterface
    fun onBridgeReady() {
        Timber.tag("MeldBridge").d("onBridgeReady called")
        mainHandler.post { onReady() }
    }
}
