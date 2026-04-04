/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.bridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.viewmodels.BridgeViewModel

sealed class BridgeUiState {
    @Immutable object Idle : BridgeUiState()
    @Immutable data class Searching(val foundHops: Int = 0, val totalHops: Int = 0) : BridgeUiState()
    @Immutable data class PathFound(val path: List<String>) : BridgeUiState()
    @Immutable data class PlaylistReady(val path: List<String>, val nowPlayingIndex: Int = 0) : BridgeUiState()
    @Immutable data class Error(val message: String) : BridgeUiState()
}

@Composable
fun BridgeScreen(navController: NavController) {
    val viewModel = hiltViewModel<BridgeViewModel>()
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val state = uiState) {
            is BridgeUiState.Idle -> Text(
                text = stringResource(R.string.bridge_idle_hint),
                style = MaterialTheme.typography.bodyLarge,
            )
            is BridgeUiState.Searching -> Text(
                text = stringResource(R.string.bridge_searching_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
            is BridgeUiState.PathFound -> Text(
                text = stringResource(R.string.bridge_path_found_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
            is BridgeUiState.PlaylistReady -> Text(
                text = stringResource(R.string.bridge_playlist_ready_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
            is BridgeUiState.Error -> Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
