/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.bridge

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * Reusable search input with dropdown autocomplete for artist name entry.
 * Styled to match the app's surfaceVariant + RoundedCornerShape(8.dp) pattern.
 * Width-matched dropdown via onGloballyPositioned (Research Pitfall 1).
 * No confirm-on-focus-loss behaviour (Research Pitfall 4).
 */
@Composable
private fun ArtistSearchInput(
    query: String,
    suggestions: List<String>,
    onQueryChange: (String) -> Unit,
    onSuggestionSelected: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val inputTextStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface
    )

    var showDropdown by remember { mutableStateOf(false) }
    var boxWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords -> boxWidthPx = coords.size.width },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp)
                )
        ) {
            BasicTextField(
                value = query,
                onValueChange = { newValue ->
                    onQueryChange(newValue)
                    showDropdown = newValue.isNotBlank()
                },
                enabled = enabled,
                singleLine = true,
                textStyle = inputTextStyle,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    autoCorrect = false,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { /* no confirm-on-done — user must tap a suggestion */ }
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) {
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
                    .onFocusChanged { focusState ->
                        showDropdown = focusState.isFocused && query.isNotBlank() && suggestions.isNotEmpty()
                    },
            )
        }

        DropdownMenu(
            expanded = showDropdown && suggestions.isNotEmpty(),
            onDismissRequest = { showDropdown = false },
            modifier = Modifier.width(with(density) { boxWidthPx.toDp() }),
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onSuggestionSelected(suggestion)
                        showDropdown = false
                    },
                )
            }
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
    val fromSuggestions by viewModel.fromSuggestions.collectAsState()
    val toSuggestions by viewModel.toSuggestions.collectAsState()
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

    val playerConnection = LocalPlayerConnection.current
    val isBuilding by viewModel.isBuilding.collectAsState()
    val showQueueDialog by viewModel.showQueueDialog.collectAsState()
    val buildFailed by viewModel.buildFailed.collectAsState()

    val context = LocalContext.current

    // FocusRequester for second input auto-focus after From confirmation (D-04)
    val toFocusRequester = remember { FocusRequester() }

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

    // Auto-focus To input when From is confirmed and To is empty (D-04)
    LaunchedEffect(fromConfirmed) {
        if (fromConfirmed.isNotEmpty() && toConfirmed.isBlank()) {
            try { toFocusRequester.requestFocus() } catch (_: Exception) { /* not yet attached */ }
        }
    }

    // Derive current path from uiState (used by both BottomSheet and Show Path button)
    val path = when (val s = uiState) {
        is BridgeUiState.PathFound -> s.path
        is BridgeUiState.PlaylistReady -> s.path
        else -> null
    }
    val nowPlayingIndex = (uiState as? BridgeUiState.PlaylistReady)?.nowPlayingIndex ?: -1

    // Chip fill logic — D-01: fills From first, then To (progressive disclosure, no focus tracking)
    val onSeedChipClick: (String) -> Unit = { artistName ->
        if (fromConfirmed.isBlank()) {
            viewModel.onFromQueryChanged(artistName)
            viewModel.confirmFrom()
        } else {
            viewModel.onToQueryChanged(artistName)
            viewModel.confirmTo()  // auto-triggers findBridge via D-05
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

        val colorScheme = MaterialTheme.colorScheme
        val typography = MaterialTheme.typography

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))

            // AnimatedContent crossfades between input disclosure state and searching state (D-06, D-08)
            AnimatedContent(
                targetState = uiState is BridgeUiState.Searching,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "bridge_input_state",
            ) { isSearchingAnim ->
                if (isSearchingAnim) {
                    // Searching state — progress indicator centre stage (BRDG-04)
                    val state = uiState as? BridgeUiState.Searching ?: BridgeUiState.Searching()
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    ) {
                        if (state.progress in 0f..1f) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.message.ifBlank { stringResource(R.string.bridge_searching_hint) },
                            style = typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        if (state.totalHops > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(
                                    R.string.bridge_progress_hops,
                                    state.foundHops,
                                    state.totalHops
                                ),
                                style = typography.bodySmall,
                                color = colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    // Input disclosure state — progressive disclosure (D-03)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // "From" section: search input or confirmed artist chip
                        if (fromConfirmed.isBlank()) {
                            Text(
                                text = stringResource(R.string.bridge_from_label),
                                style = typography.labelLarge,
                                color = colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            ArtistSearchInput(
                                query = fromQuery,
                                suggestions = fromSuggestions,
                                onQueryChange = { viewModel.onFromQueryChanged(it) },
                                onSuggestionSelected = { name ->
                                    viewModel.onFromQueryChanged(name)
                                    viewModel.confirmFrom()
                                },
                                placeholder = stringResource(R.string.bridge_from_placeholder),
                                enabled = !isSearching,
                            )
                        } else {
                            AssistChip(
                                onClick = { viewModel.clearFrom() },
                                label = {
                                    Text(
                                        fromConfirmed,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = stringResource(R.string.bridge_clear_from_description),
                                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                                    )
                                },
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Seed suggestion chips — below From input (D-09)
                        SeedSuggestionsRow(
                            suggestions = seedSuggestions,
                            isLoading = isLoadingSeeds,
                            onChipClick = onSeedChipClick,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // "To" section — animated reveal after From confirmed (D-03)
                        AnimatedVisibility(
                            visible = fromConfirmed.isNotEmpty(),
                            enter = expandVertically(animationSpec = spring()) + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column {
                                Spacer(Modifier.height(8.dp))
                                if (toConfirmed.isBlank()) {
                                    Text(
                                        text = stringResource(R.string.bridge_to_label),
                                        style = typography.labelLarge,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    ArtistSearchInput(
                                        query = toQuery,
                                        suggestions = toSuggestions,
                                        onQueryChange = { viewModel.onToQueryChanged(it) },
                                        onSuggestionSelected = { name ->
                                            viewModel.onToQueryChanged(name)
                                            viewModel.confirmTo()  // auto-triggers bridge per D-05
                                        },
                                        placeholder = stringResource(R.string.bridge_to_placeholder),
                                        enabled = !isSearching,
                                        modifier = Modifier.focusRequester(toFocusRequester),
                                    )
                                } else {
                                    AssistChip(
                                        onClick = { viewModel.clearTo() },
                                        label = {
                                            Text(
                                                toConfirmed,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        trailingIcon = {
                                            Icon(
                                                painter = painterResource(R.drawable.close),
                                                contentDescription = stringResource(R.string.bridge_clear_to_description),
                                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        // Idle hint text — only when no artists confirmed
                        if (fromConfirmed.isBlank()) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.bridge_idle_hint),
                                style = typography.bodyLarge,
                                color = colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // State-driven content section — PathFound, PlaylistReady, Error only
            // (Idle and Searching are handled by AnimatedContent above)
            when (val state = uiState) {
                is BridgeUiState.PathFound -> {
                    if (isBuilding) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.bridge_building_playlist),
                                style = typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else if (buildFailed) {
                        Text(
                            text = stringResource(R.string.bridge_no_tracks_found),
                            style = typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.bridge_path_found_hint),
                            style = typography.bodyMedium,
                        )
                    }
                }
                is BridgeUiState.PlaylistReady -> {
                    Text(
                        text = stringResource(R.string.bridge_playlist_ready_hint),
                        style = typography.bodyMedium,
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
                            tint = colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            color = colorScheme.error,
                            style = typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.bridge_error_suggestion),
                            color = colorScheme.onSurface.copy(alpha = 0.6f),
                            style = typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> Unit
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
                                colorScheme.surfaceContainer,
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
                                    colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(2.dp),
                                ),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = path?.joinToString(" → ") ?: "",
                            style = typography.bodyMedium,
                            color = colorScheme.onSurface,
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
                    modifier = Modifier.background(colorScheme.surfaceContainer),
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
