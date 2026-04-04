package com.metrolist.music.bridge

import org.junit.Ignore
import org.junit.Test

class BridgeViewModelTest {

    @Ignore("Wave 0 stub — implementation in Plan 02")
    @Test
    fun startBridge_rejects_when_running() {
        // BRDG-02: When a bridge is already running (isRunning == true),
        // calling startBridge() should be a no-op — no evaluateJavascript call.
    }

    @Ignore("Wave 0 stub — implementation in Plan 02")
    @Test
    fun startBridge_calls_evaluateJavascript() {
        // BRDG-02: Calling startBridge("Radiohead", "Lil Kim") should:
        // 1. Set uiState to BridgeUiState.Searching()
        // 2. Call evaluateJavascript with window.startBridge("Radiohead", "Lil Kim")
        // 3. Escape artist names via JSONObject.quote()
    }
}
