/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.bridge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.viewmodels.ArtistFamiliarity
import com.metrolist.music.viewmodels.BridgeArtistInfo

/**
 * Bottom sheet content showing the full bridge path from seed to target artist.
 * Each artist node shows genre tags (up to 3) and a formatted listener count.
 * The currently-playing artist (identified by [nowPlayingIndex]) is highlighted
 * with a colored left border and background tint (BRDG-05, D-05).
 */
@Composable
fun PathSheet(
    path: List<String>,
    artistMetadata: Map<String, BridgeArtistInfo>,
    nowPlayingIndex: Int,
    familiarityMap: Map<String, ArtistFamiliarity> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        // Drag handle — centered, 32x4dp per Material 3 bottom sheet spec
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(32.dp)
                .height(4.dp)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    RoundedCornerShape(2.dp),
                )
                .semantics { contentDescription = "Bridge path sheet, drag to expand or collapse" },
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.bridge_path_sheet_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            itemsIndexed(path) { index, artistName ->
                val info = artistMetadata[artistName]
                val isNowPlaying = index == nowPlayingIndex
                PathNodeRow(
                    artistName = artistName,
                    info = info,
                    isNowPlaying = isNowPlaying,
                    familiarity = familiarityMap[artistName],
                )
                if (index < path.lastIndex) {
                    VerticalConnectorLine()
                }
            }
        }
    }
}

/**
 * A single artist node in the path. Animated highlight (left border + background tint)
 * activates when [isNowPlaying] is true.
 */
@Composable
fun PathNodeRow(
    artistName: String,
    info: BridgeArtistInfo?,
    isNowPlaying: Boolean,
    familiarity: ArtistFamiliarity? = null,
) {
    val borderColor by animateColorAsState(
        targetValue = if (isNowPlaying) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(300),
        label = "nowPlayingBorder",
    )
    val bgTint by animateColorAsState(
        targetValue = if (isNowPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(300),
        label = "nowPlayingBg",
    )

    // Build a11y description for the full node
    val nodeDescription = buildString {
        append(artistName)
        if (info != null) {
            append(", ")
            append(info.formattedListeners)
            if (info.tags.isNotEmpty()) {
                append(", genres: ")
                append(info.tags.take(3).joinToString())
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics { contentDescription = nodeDescription },
    ) {
        // Colored left border — 4dp wide, full node height
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(borderColor),
        )

        // Content area — artist name, listener count, genre chips
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgTint)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Name + now-playing icon row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                AnimatedVisibility(
                    visible = isNowPlaying,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.volume_up),
                        contentDescription = "Now playing",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (familiarity != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    ArtistFamiliarityBadge(familiarity)
                }
            }

            // Listener count
            if (info != null) {
                Text(
                    text = info.formattedListeners,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            // Genre tag chips (up to 3)
            if (info != null && info.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    info.tags.take(3).forEach { tag ->
                        GenreTagChip(tag)
                    }
                }
            }
        }
    }
}

/**
 * A compact genre tag chip styled with primary color at 12% alpha for the container.
 * Uses a simple Box instead of SuggestionChip to avoid minimum-height constraints
 * that make chips visually oversized (Research Pitfall 6).
 */
@Composable
fun GenreTagChip(tag: String) {
    Box(
        modifier = Modifier
            .height(24.dp)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * A small pill badge indicating artist familiarity.
 * "NEW" (primary color) for undiscovered artists; "known" (secondary at 0.7f alpha) for familiar ones.
 * Shown inline after the artist name in [PathNodeRow].
 * Content description expands the abbreviated text for accessibility (UI-SPEC Known/NEW Badges).
 */
@Composable
private fun ArtistFamiliarityBadge(familiarity: ArtistFamiliarity) {
    val isNew = familiarity == ArtistFamiliarity.NEW
    Box(
        modifier = Modifier
            .height(16.dp)
            .background(
                color = if (isNew)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 8.dp)
            .semantics {
                contentDescription = if (isNew) "New artist" else "Familiar artist"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(if (isNew) R.string.bridge_badge_new else R.string.bridge_badge_known),
            style = MaterialTheme.typography.labelSmall,
            color = if (isNew)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSecondary,
        )
    }
}

/**
 * A short vertical line segment connecting adjacent artist nodes in the path.
 * Aligns to the left edge (CenterStart) to connect with the left border strips on PathNodeRow.
 */
@Composable
fun VerticalConnectorLine() {
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(12.dp)
                .align(Alignment.CenterStart)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)),
        )
    }
}
