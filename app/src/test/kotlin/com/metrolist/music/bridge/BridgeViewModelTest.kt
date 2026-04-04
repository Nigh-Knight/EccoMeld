package com.metrolist.music.bridge

import android.os.Handler
import android.webkit.WebView
import com.metrolist.lastfm.LastFM
import com.metrolist.music.ui.screens.bridge.BridgeUiState
import com.metrolist.music.viewmodels.BridgeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.mockito.kotlin.mock

class BridgeViewModelTest {

    @Before
    fun setUp() {
        // Initialize LastFM with test credentials so searchArtists() doesn't crash on key-empty check.
        // Tests that actually call the network are marked @Ignore.
        LastFM.initialize("test_key", "test_secret")
    }

    /**
     * Build a ViewModel with a mocked MeldBridgeInterface.
     *
     * [Rule 1 - Bug] MeldBridgeInterface constructs Handler(Looper.getMainLooper()) in its
     * init block, which throws RuntimeException in JVM unit tests (Android not mocked).
     * Using mock<MeldBridgeInterface>() avoids real construction — Mockito creates a subclass proxy.
     */
    private fun buildViewModel(): Pair<BridgeViewModel, WebView> {
        val mockWebView = mock<WebView>()
        val mockBridgeInterface = mock<MeldBridgeInterface>()
        val viewModel = BridgeViewModel(mockWebView, mockBridgeInterface)
        return viewModel to mockWebView
    }

    @Ignore("startBridge calls Handler(Looper.getMainLooper()) which requires Android instrumented test — deferred to Espresso/instrumented suite")
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

    @Ignore("startBridge calls Handler(Looper.getMainLooper()) which requires Android instrumented test — deferred to Espresso/instrumented suite")
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

    // --- Autocomplete state tests (BRDG-01) ---

    @Test
    fun confirmFrom_stores_confirmed_artist() {
        // BRDG-01: confirmFrom() without a ghost (no debounce has fired) falls back to raw input.
        // The confirmed artist should equal whatever is currently in fromQuery.
        val (viewModel, _) = buildViewModel()

        viewModel.onFromQueryChanged("Radiohead")
        // Ghost has NOT been fetched yet (no coroutine advance) — confirmFrom uses raw input
        viewModel.confirmFrom()

        assertEquals(
            "Confirmed artist should equal raw query when no ghost is available",
            "Radiohead",
            viewModel.fromConfirmedArtist.value,
        )
        // Ghost suffix must be cleared after confirm
        assertEquals("Ghost suffix should be empty after confirm", "", viewModel.fromGhostSuffix.value)
    }

    @Ignore("Requires live LastFM network and coroutine scheduler to advance past 300ms debounce — integration test only")
    @Test
    fun ghostSuffix_only_shown_on_prefix_match() {
        // BRDG-01, Research Pitfall 2: ghost suffix must only appear when the top suggestion
        // starts with the user's query (case-insensitive). If the suggestion doesn't match,
        // ghostSuffix must remain empty.
        // This test is deferred to integration testing because it requires:
        //   1. A real LastFM response (or a test dispatcher advancing past 300ms delay)
        //   2. Mockito-based mocking of the LastFM object singleton (not straightforward)
    }

    @Test
    fun error_state_does_not_set_isRunning() {
        // BRDG-06, D-10: After an Error state, isRunning must be false so inputs remain editable.
        // Use a hand-written stub that overrides mainHandler with a no-op to avoid
        // Looper.getMainLooper() in JVM unit tests, and captures the onStateChange callback
        // so we can invoke it synchronously.
        val mockWebView = mock<WebView>()
        var capturedCallback: ((BridgeUiState) -> Unit)? = null
        val noopHandler = mock<Handler>()
        val bridgeInterfaceStub = object : MeldBridgeInterface() {
            override val mainHandler: Handler get() = noopHandler
            override var onStateChange: (BridgeUiState) -> Unit
                get() = capturedCallback ?: {}
                set(value) { capturedCallback = value }
        }
        val viewModel = BridgeViewModel(mockWebView, bridgeInterfaceStub)

        // Simulate bridge search starting by injecting Searching state directly via the callback
        // (avoids calling startBridge() which would trigger Handler(Looper.getMainLooper()))
        capturedCallback?.invoke(BridgeUiState.Searching())
        assertTrue("isRunning should be true during search", viewModel.isRunning)

        // Simulate EccoPath reporting an error via the captured bridge interface callback
        capturedCallback?.invoke(BridgeUiState.Error("No bridge path found"))

        assertFalse(
            "isRunning should be false after Error state",
            viewModel.isRunning,
        )
        assertTrue(
            "uiState should be Error",
            viewModel.uiState.value is BridgeUiState.Error,
        )
    }

    @Test
    fun blank_query_clears_ghost_suffix() {
        // Edge case: empty query must clear ghost suffix synchronously (no debounce needed).
        val (viewModel, _) = buildViewModel()

        // Simulate ghost suffix being set (directly via state — would normally come from debounce)
        viewModel.onFromQueryChanged("Rad") // sets query
        viewModel.onFromQueryChanged("") // clears to blank

        assertEquals(
            "Ghost suffix should be cleared immediately on blank query",
            "",
            viewModel.fromGhostSuffix.value,
        )
        assertEquals(
            "Confirmed artist should be cleared when query changes",
            "",
            viewModel.fromConfirmedArtist.value,
        )
    }

    @Test
    fun findBridge_requires_both_confirmed() {
        // D-09: findBridge() must be a no-op unless both fromConfirmedArtist and toConfirmedArtist
        // are non-blank. If called without confirming, state stays Idle.
        val (viewModel, _) = buildViewModel()

        // Only type queries — don't confirm either
        viewModel.onFromQueryChanged("Radiohead")
        viewModel.onToQueryChanged("Lil Kim")

        // findBridge without prior confirmation — both confirmed are still empty
        viewModel.findBridge()

        assertEquals(
            "State should remain Idle when confirmed artists are not set",
            BridgeUiState.Idle,
            viewModel.uiState.value,
        )
        assertFalse("isRunning should be false", viewModel.isRunning)
    }
}
