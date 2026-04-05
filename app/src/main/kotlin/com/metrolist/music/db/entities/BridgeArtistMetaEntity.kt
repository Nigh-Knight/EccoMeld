/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Caches artist metadata (display name, genre tags, listener count) from Last.fm.
 * Used as L2 (persistent) cache by KotlinBridgeCache.
 *
 * [artistKey] is the normalized key: artist.trim().lowercase()
 * [tagsJson] is a JSON-encoded List<String> of genre tag names
 */
@Immutable
@Entity(tableName = "bridge_artist_meta")
data class BridgeArtistMetaEntity(
    @PrimaryKey val artistKey: String,
    val displayName: String,
    val tagsJson: String,
    val listeners: Long,
    val cachedAt: Long = System.currentTimeMillis()
)
