/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.bridge

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
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
import com.metrolist.music.ui.component.BottomSheet
import com.metrolist.music.ui.component.dismissedAnchor
import com.metrolist.music.ui.component.rememberBottomSheetState
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.viewmodels.BridgeViewModel

sealed class BridgeUiState {
    @Immutable object Idle : BridgeUiState()
    @Immutable data class Searching(
        val message: String = "",
        val progress: Float = -1f,
        val foundHops: Int = 0,
        val totalHops: Int = 0,
    ) : BridgeUiState()
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
    onFocusChanged: ((Boolean) -> Unit)? = null,
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
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (onFocusChanged != null) {
                            Modifier.onFocusChanged { onFocusChanged(it.isFocused) }
                        } else {
                            Modifier
                        }
                    ),
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

/**
 * Horizontal chip row showing seed artist suggestions (SPOT-01).
 * Shows shimmer placeholders while loading, hidden via AnimatedVisibility when empty.
 */
@Composable
private fun SeedSuggestionsRow(
    suggestions: List<String>,
    isLoading: Boolean,
    onChipClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = suggestions.isNotEmpty() || isLoading) {
        if (isLoading) {
            val loadingDesc = stringResource(R.string.bridge_seeds_loading_description)
            ShimmerHost(showGradient = false, modifier = modifier) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.semantics { contentDescription = loadingDesc },
                ) {
                    items(3) {
                        Box(
                            modifier = Modifier
                                .size(width = 80.dp, height = 32.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(16.dp),
                                )
                                .semantics { invisibleToUser() },
                        )
                    }
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = modifier,
            ) {
                items(suggestions, key = { it }) { artist ->
                    SuggestionChip(
                        onClick = { onChipClick(artist) },
                        label = {
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * Floating Action Button for random bridge discovery (SPOT-02).
 * Shows CircularProgressIndicator while loading, shuffle icon otherwise.
 * onClick guard implements disabled-while-running behavior — TalkBack still receives tap events
 * but no action fires. Standard FAB has no enabled parameter so guard is in onClick lambda.
 */
@Composable
private fun RandomBridgeFab(
    isLoading: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = { if (isEnabled) onClick() },
        modifier = modifier,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.shuffle),
                contentDescription = stringResource(R.string.bridge_random_fab_description),
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
    // Ghost suffix removed in Phase 9 Plan 01 — Plan 02 replaces this with suggestion dropdowns
    val fromGhostSuffix = ""
    val toGhostSuffix = ""
    val fromConfirmed by viewModel.fromConfirmedArtist.collectAsState()
    val toConfirmed by viewModel.toConfirmedArtist.collectAsState()
    val isSearching = uiState is BridgeUiState.Searching
    val artistMetadata by viewModel.artistMetadata.collectAsState()

    val artistFamiliarity by viewModel.artistFamiliarity.collectAsState()

    // Seed suggestions state (SPOT-01, SPOT-02)
    val seedSuggestions by viewModel.seedSuggestions.collectAsState()
    val isLoadingSeeds by viewModel.isLoadingSeeds.collectAsState()
    val isFabLoading by viewModel.isFabLoading.collectAsState()
    val randomBridgeToast by viewModel.randomBridgeToast.collectAsState()

    // Focus tracking for D-01 chip fill priority logic
    var fromHasFocus by remember { mutableStateOf(false) }
    var toHasFocus by remember { mutableStateOf(false) }

    val playerConnection = LocalPlayerConnection.current
    val isBuilding by viewModel.isBuilding.collectAsState()
    val showQueueDialog by viewModel.showQueueDialog.collectAsState()
    val buildFailed by viewModel.buildFailed.collectAsState()

    val context = LocalContext.current

    // Observe now-playing artist for highlight tracking (D-05, BRDG-05)
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsState()
        ?: remember { mutableStateOf(null) }
    LaunchedEffect(mediaMetadata) {
        val artistName = mediaMetadata?.artists?.firstOrNull()?.name ?: ""
        viewModel.onNowPlayingArtistChanged(artistName)
    }

    // Load seed suggestions eagerly on tab entry (idempotent guard inside ViewModel)
    LaunchedEffect(Unit) { viewModel.loadSeedSuggestions() }

    // Show toast when randomBridge cannot pick (fewer than 2 artists available)
    LaunchedEffect(randomBridgeToast) {
        randomBridgeToast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearRandomBridgeToast()
        }
    }

    // Derive current path from uiState (used by both BottomSheet and Show Path button)
    val path = when (val s = uiState) {
        is BridgeUiState.PathFound -> s.path
        is BridgeUiState.PlaylistReady -> s.path
        else -> null
    }
    val nowPlayingIndex = (uiState as? BridgeUiState.PlaylistReady)?.nowPlayingIndex ?: -1

    // Chip fill logic — D-01 priority rules (fills empty or focused input first)
    val onSeedChipClick: (String) -> Unit = { artistName ->
        when {
            fromConfirmed.isBlank() -> {
                viewModel.onFromQueryChanged(artistName)
                viewModel.confirmFrom()
            }
            toConfirmed.isBlank() -> {
                viewModel.onToQueryChanged(artistName)
                viewModel.confirmTo()
            }
            fromHasFocus -> {
                viewModel.onFromQueryChanged(artistName)
                viewModel.confirmFrom()
            }
            toHasFocus -> {
                viewModel.onToQueryChanged(artistName)
                viewModel.confirmTo()
            }
            else -> {  // both filled, neither focused — fallback to From
                viewModel.onFromQueryChanged(artistName)
                viewModel.confirmFrom()
            }
        }
    }

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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Fixed bounds so sheet doesn't jump when keyboard appears/disappears
        val pathSheetState = rememberBottomSheetState(
            dismissedBound = 0.dp,
            expandedBound = 350.dp,
            collapsedBound = 64.dp,
            initialAnchor = dismissedAnchor,
        )

        // Auto-show/hide sheet on uiState transitions (D-02)
        LaunchedEffect(uiState) {
            when (uiState) {
                is BridgeUiState.PathFound, is BridgeUiState.PlaylistReady -> pathSheetState.collapseSoft()
                is BridgeUiState.Idle, is BridgeUiState.Searching -> pathSheetState.dismiss()
                else -> Unit  // Error state: leave sheet as-is
            }
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
                    onFocusChanged = { focused ->
                        fromHasFocus = focused
                        if (!focused) viewModel.confirmFrom()
                    },
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
                    onFocusChanged = { focused ->
                        toHasFocus = focused
                        if (!focused) viewModel.confirmTo()
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Seed suggestion chips row (SPOT-01) — between inputs and Find Bridge button
            SeedSuggestionsRow(
                suggestions = seedSuggestions,
                isLoading = isLoadingSeeds,
                onChipClick = onSeedChipClick,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            // Find Bridge button (D-09: disabled when running or inputs empty)
            // Enable based on query text (not confirmed) — onClick confirms before bridging
            Button(
                onClick = {
                    viewModel.confirmFrom()
                    viewModel.confirmTo()
                    viewModel.findBridge()
                },
                enabled = fromQuery.isNotBlank() && toQuery.isNotBlank() && !isSearching,
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
                    // Progress bar — determinate when progress known, indeterminate otherwise
                    if (state.progress in 0f..1f) {
                        LinearProgressIndicator(
                            progress = { state.progress },
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
                    // Real progress message from EccoPath (e.g., "Analyzing Radiohead and Björk...", "Searching from Radiohead...")
                    Text(
                        text = state.message.ifBlank { stringResource(R.string.bridge_searching_hint) },
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

            // Show Path button — visible when a path exists but the sheet is dismissed
            if (path != null && pathSheetState.isDismissed) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { pathSheetState.collapseSoft() }) {
                    Text(stringResource(R.string.bridge_path_show_button))
                }
            }
        }

        // Path bottom sheet overlay — only rendered when a path exists
        if (path != null) {
            BottomSheet(
                state = pathSheetState,
                onDismiss = { /* allow dismiss — re-openable via Show Path button */ },
                collapsedContent = {
                    // Collapsed peek: drag handle + path summary
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(pathSheetState.collapsedBound)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainer,
                                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(2.dp),
                                ),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = path?.joinToString(" → ") ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                        )
                    }
                },
            ) {
                PathSheet(
                    path = path,
                    artistMetadata = artistMetadata,
                    nowPlayingIndex = nowPlayingIndex,
                    familiarityMap = artistFamiliarity,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
                )
            }
        }

        // Random Bridge FAB — last child in BoxWithConstraints so it sits above all content
        // in Z-order, including the bottom sheet (SPOT-02, Research Pattern 4)
        RandomBridgeFab(
            isLoading = isFabLoading,
            isEnabled = !viewModel.isRunning && !isFabLoading,
            onClick = {
                viewModel.randomBridge(context.getString(R.string.bridge_random_no_artists))
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                )
                .padding(16.dp),
        )
    }
}
