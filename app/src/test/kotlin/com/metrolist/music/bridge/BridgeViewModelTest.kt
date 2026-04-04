package com.metrolist.music.bridge

import android.webkit.WebView
import com.metrolist.music.ui.screens.bridge.BridgeUiState
import com.metrolist.music.viewmodels.BridgeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class BridgeViewModelTest {

    private fun buildViewModel(): Pair<BridgeViewModel, WebView> {
        val mockWebView = mock<WebView>()
        val bridgeInterface = MeldBridgeInterface()
        val viewModel = BridgeViewModel(mockWebView, bridgeInterface)
        return viewModel to mockWebView
    }

    @Test
    fun startBridge_rejects_when_running() {
        // BRDG-02: When a bridge is already running (isRunning == true),
        // calling startBridge() should be a no-op — no additional state change.
        val (viewModel, _) = buildViewModel()

        // Put ViewModel into running state by calling startBridge once
        viewModel.startBridge("Radiohead", "Lil Kim")
        assertTrue("Expected isRunning to be true after first call", viewModel.isRunning)

        // Capture the state after first call
        val stateAfterFirst = viewModel.uiState.value

        // Second call should be rejected — state must not change
        viewModel.startBridge("Another Artist", "Yet Another Artist")
        assertEquals(
            "State should not change on second startBridge when running",
            stateAfterFirst,
            viewModel.uiState.value,
        )
        assertTrue("isRunning should remain true", viewModel.isRunning)
    }

    @Test
    fun startBridge_calls_evaluateJavascript() {
        // BRDG-02: Calling startBridge("Radiohead", "Lil Kim") should:
        // 1. Set uiState to BridgeUiState.Searching()
        // 2. Call evaluateJavascript with window.startBridge("Radiohead", "Lil Kim")
        // 3. Escape artist names via JSONObject.quote()
        val (viewModel, _) = buildViewModel()

        viewModel.startBridge("Radiohead", "Lil Kim")

        // State must transition to Searching immediately (before JS callback)
        assertTrue(
            "uiState should be Searching after startBridge",
            viewModel.uiState.value is BridgeUiState.Searching,
        )
        assertTrue("isRunning should be true", viewModel.isRunning)
    }
}
