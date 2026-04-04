/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.bridge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
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
private fun GhostTextField(
    value: String,
    ghostSuffix: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    placeholder: String,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // Use shared text style for pixel-perfect ghost alignment (Research Pitfall 1)
    val inputTextStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface
    )

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)  // Accessibility touch target
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp)
                )
        ) {
            // Ghost text layer (behind real input)
            if (ghostSuffix.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Invisible spacer matching user-typed text width
                    Text(
                        text = value,
                        style = inputTextStyle,
                        color = Color.Transparent,
                        maxLines = 1,
                    )
                    // Ghost suffix at 38% opacity (UI-SPEC color contract)
                    Text(
                        text = ghostSuffix,
                        style = inputTextStyle,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        maxLines = 1,
                        modifier = Modifier.semantics { invisibleToUser() },
                    )
                }
            }
            // Real input field on top
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = inputTextStyle,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    autoCorrect = false  // Research Open Question 2 — suppress IME autocomplete
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onConfirm() }
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = inputTextStyle.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                ),
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        // Ghost confirmation hint (UI-SPEC: "Tab to confirm" when ghost visible)
        if (ghostSuffix.isNotEmpty() && enabled) {
            Text(
                text = stringResource(R.string.bridge_ghost_confirm_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
fun BridgeScreen(navController: NavController) {
    val viewModel = hiltViewModel<BridgeViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val fromQuery by viewModel.fromQuery.collectAsState()
    val toQuery by viewModel.toQuery.collectAsState()
    val fromGhostSuffix by viewModel.fromGhostSuffix.collectAsState()
    val toGhostSuffix by viewModel.toGhostSuffix.collectAsState()
    val fromConfirmed by viewModel.fromConfirmedArtist.collectAsState()
    val toConfirmed by viewModel.toConfirmedArtist.collectAsState()
    val isSearching = uiState is BridgeUiState.Searching

    val playerConnection = LocalPlayerConnection.current
    val isBuilding by viewModel.isBuilding.collectAsState()
    val showQueueDialog by viewModel.showQueueDialog.collectAsState()
    val buildFailed by viewModel.buildFailed.collectAsState()

    // Queue confirmation dialog — shown when playlist is ready (D-06)
    if (showQueueDialog && playerConnection != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissQueueDialog() },
            title = { Text(stringResource(R.string.bridge_queue_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.bridge_queue_dialog_message,
                        viewModel.pendingTrackCount
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onConfirmReplaceQueue(playerConnection) }) {
                    Text(stringResource(R.string.bridge_queue_replace))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onConfirmPlayNext(playerConnection) }) {
                    Text(stringResource(R.string.bridge_queue_play_next))
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))

        // Input section — two side-by-side fields (D-03)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GhostTextField(
                value = fromQuery,
                ghostSuffix = fromGhostSuffix,
                onValueChange = { viewModel.onFromQueryChanged(it) },
                onConfirm = { viewModel.confirmFrom() },
                placeholder = stringResource(R.string.bridge_from_placeholder),
                label = stringResource(R.string.bridge_from_label),
                enabled = !isSearching,
                modifier = Modifier.weight(1f),
            )
            GhostTextField(
                value = toQuery,
                ghostSuffix = toGhostSuffix,
                onValueChange = { viewModel.onToQueryChanged(it) },
                onConfirm = { viewModel.confirmTo() },
                placeholder = stringResource(R.string.bridge_to_placeholder),
                label = stringResource(R.string.bridge_to_label),
                enabled = !isSearching,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Find Bridge button (D-09: disabled when running or inputs empty)
        Button(
            onClick = { viewModel.findBridge() },
            enabled = fromConfirmed.isNotBlank() && toConfirmed.isNotBlank() && !isSearching,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(48.dp),
        ) {
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.bridge_find_button))
        }

        Spacer(Modifier.height(16.dp))

        // State-driven content section
        when (val state = uiState) {
            is BridgeUiState.Idle -> {
                Text(
                    text = stringResource(R.string.bridge_idle_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            is BridgeUiState.Searching -> {
                // Progress bar — determinate when hops known, indeterminate otherwise (D-06, D-07)
                val progress = if (state.totalHops > 0)
                    state.foundHops.toFloat() / state.totalHops.toFloat()
                else
                    null

                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Progress message text
                Text(
                    text = stringResource(R.string.bridge_searching_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                // Hop count (only when totalHops known)
                if (state.totalHops > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.bridge_progress_hops,
                            state.foundHops,
                            state.totalHops
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            is BridgeUiState.PathFound -> {
                if (isBuilding) {
                    // Show building progress while tracks are being resolved
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.bridge_building_playlist),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else if (buildFailed) {
                    // All tracks failed to resolve — show informative message
                    Text(
                        text = stringResource(R.string.bridge_no_tracks_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.bridge_path_found_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            is BridgeUiState.PlaylistReady -> {
                // Placeholder for Phase 5 playlist view
                Text(
                    text = stringResource(R.string.bridge_playlist_ready_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            is BridgeUiState.Error -> {
                // Inline error display (D-08) — no dialog
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.error),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.bridge_error_suggestion),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
