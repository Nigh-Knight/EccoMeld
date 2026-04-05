/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.bridge

import com.metrolist.lastfm.LastFM
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.BridgeArtistMetaEntity
import com.metrolist.music.db.entities.BridgeSimilarArtistEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cached similar artist entry for in-memory L1 cache.
 */
@Serializable
data class CachedSimilarArtist(
    val name: String,
    val match: Float,
)

/**
 * Cached artist metadata for in-memory L1 cache.
 */
data class CachedArtistMeta(
    val displayName: String,
    val tags: List<String>,
    val listeners: Long,
)

/**
 * Two-level cache for bridge algorithm data.
 * L1: ConcurrentHashMap (in-memory, per-process lifetime)
 * L2: Room DB (persistent across app restarts)
 *
 * Port of eccopath/lib/lastfm.ts memCache + IndexedDB pattern.
 */
@Singleton
class KotlinBridgeCache @Inject constructor(
    private val database: MusicDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // L1 caches
    private val similarL1 = ConcurrentHashMap<String, List<CachedSimilarArtist>>()
    private val metaL1 = ConcurrentHashMap<String, CachedArtistMeta>()

    private fun normalizeKey(artist: String): String = artist.trim().lowercase()

    /**
     * Get similar artists for [artist]. Checks L1, then L2 (Room), then network (LastFM).
     * Returns empty list on any failure — never throws.
     */
    suspend fun getSimilarArtists(artist: String, limit: Int = 100): List<CachedSimilarArtist> {
        val key = normalizeKey(artist)

        // L1 hit
        similarL1[key]?.let { return it }

        // L2 hit (Room)
        try {
            database.getBridgeSimilarArtist(key)?.let { entity ->
                val cached = json.decodeFromString<List<CachedSimilarArtist>>(entity.similarJson)
                similarL1[key] = cached
                return cached
            }
        } catch (e: Exception) {
            Timber.tag("BridgeCache").d("L2 decode error for %s: %s", key, e.message)
        }

        // Network fetch
        return try {
            val response = LastFM.getSimilarArtists(artist, limit).getOrThrow()
            val cached = response.similarartists.artist.map { a ->
                CachedSimilarArtist(
                    name = a.name,
                    match = a.match.toFloatOrNull() ?: 0.5f,
                )
            }
            // Store in L1
            similarL1[key] = cached
            // Store in L2
            try {
                database.upsertBridgeSimilarArtist(
                    BridgeSimilarArtistEntity(
                        artistKey = key,
                        similarJson = json.encodeToString(cached),
                    )
                )
            } catch (e: Exception) {
                Timber.tag("BridgeCache").e(e, "L2 write error for similar %s", key)
            }
            cached
        } catch (e: Exception) {
            Timber.tag("BridgeCache").d("Network error for similar %s: %s", key, e.message)
            emptyList()
        }
    }

    /**
     * Get artist metadata (tags + listeners). Same L1/L2/network pattern.
     * Returns null on failure.
     */
    suspend fun getArtistMeta(artist: String): CachedArtistMeta? {
        val key = normalizeKey(artist)

        // L1 hit
        metaL1[key]?.let { return it }

        // L2 hit (Room)
        try {
            database.getBridgeArtistMeta(key)?.let { entity ->
                val tags = json.decodeFromString<List<String>>(entity.tagsJson)
                val meta = CachedArtistMeta(
                    displayName = entity.displayName,
                    tags = tags,
                    listeners = entity.listeners,
                )
                metaL1[key] = meta
                return meta
            }
        } catch (e: Exception) {
            Timber.tag("BridgeCache").d("L2 decode error for meta %s: %s", key, e.message)
        }

        // Network fetch via getArtistInfo
        return try {
            val response = LastFM.getArtistInfo(artist).getOrThrow()
            val meta = CachedArtistMeta(
                displayName = response.artist.name.ifEmpty { artist },
                tags = response.artist.tags.tag.map { it.name.lowercase() },
                listeners = response.artist.stats.listeners.toLongOrNull() ?: 0L,
            )
            metaL1[key] = meta
            try {
                database.upsertBridgeArtistMeta(
                    BridgeArtistMetaEntity(
                        artistKey = key,
                        displayName = meta.displayName,
                        tagsJson = json.encodeToString(meta.tags),
                        listeners = meta.listeners,
                    )
                )
            } catch (e: Exception) {
                Timber.tag("BridgeCache").e(e, "L2 write error for meta %s", key)
            }
            meta
        } catch (e: Exception) {
            Timber.tag("BridgeCache").d("Network error for meta %s: %s", key, e.message)
            null
        }
    }
}
