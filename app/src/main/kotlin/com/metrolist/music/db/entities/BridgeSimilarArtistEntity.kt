/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Caches the similar-artist list returned by Last.fm artist.getSimilar for a given artist.
 * Used as L2 (persistent) cache by KotlinBridgeCache.
 *
 * [artistKey] is the normalized key: artist.trim().lowercase()
 * [similarJson] is a JSON-encoded List<CachedSimilarArtist> (see KotlinBridgeCache.kt)
 */
@Immutable
@Entity(tableName = "bridge_similar_artists")
data class BridgeSimilarArtistEntity(
    @PrimaryKey val artistKey: String,
    val similarJson: String,
    val cachedAt: Long = System.currentTimeMillis()
)
