package com.metrolist.music.bridge

import org.junit.Ignore
import org.junit.Test

class MeldBridgeInterfaceTest {

    @Ignore("Wave 0 stub — implementation in Plan 01")
    @Test
    fun createPlaylist_valid_json_emits_PathFound() {
        // BRDG-03: Calling createPlaylist('{"found":true,"path":["A","B","C"]}')
        // should invoke onStateChange with BridgeUiState.PathFound(["A","B","C"])
    }

    @Ignore("Wave 0 stub — implementation in Plan 01")
    @Test
    fun createPlaylist_not_found_emits_Error() {
        // BRDG-03: Calling createPlaylist('{"found":false,"path":[]}')
        // should invoke onStateChange with BridgeUiState.Error(...)
    }

    @Ignore("Wave 0 stub — implementation in Plan 01")
    @Test
    fun onProgress_emits_Searching() {
        // BRDG-03: Calling onProgress('{"depth":2,"maxDepth":4}')
        // should invoke onStateChange with BridgeUiState.Searching(foundHops=2, totalHops=4)
    }
}
