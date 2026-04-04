/** Metrolist Project (C) 2026 / Licensed under GPL-3.0 | See git history for contributors */
package com.metrolist.lastfm

import com.metrolist.lastfm.models.ArtistInfoResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistInfoResponseTest {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Test
    fun deserialize_full_artist_info_response() {
        val jsonString = """{"artist":{"name":"Radiohead","stats":{"listeners":"5432100","playcount":"123456789"},"tags":{"tag":[{"name":"alternative","url":"https://last.fm/tag/alternative"},{"name":"rock","url":"https://last.fm/tag/rock"}]}}}"""
        val response = json.decodeFromString<ArtistInfoResponse>(jsonString)

        assertEquals("Radiohead", response.artist.name)
        assertEquals("5432100", response.artist.stats.listeners)
        assertEquals("123456789", response.artist.stats.playcount)
        assertEquals(2, response.artist.tags.tag.size)
        assertEquals("alternative", response.artist.tags.tag[0].name)
        assertEquals("rock", response.artist.tags.tag[1].name)
    }

    @Test
    fun deserialize_artist_with_empty_tags() {
        val jsonString = """{"artist":{"name":"Unknown","stats":{"listeners":"0","playcount":"0"},"tags":{"tag":[]}}}"""
        val response = json.decodeFromString<ArtistInfoResponse>(jsonString)

        assertEquals("Unknown", response.artist.name)
        assertEquals("0", response.artist.stats.listeners)
        assertEquals(0, response.artist.tags.tag.size)
    }

    @Test
    fun deserialize_artist_with_missing_stats_uses_defaults() {
        val jsonString = """{"artist":{"name":"Minimal"}}"""
        val response = json.decodeFromString<ArtistInfoResponse>(jsonString)

        assertEquals("Minimal", response.artist.name)
        assertEquals("0", response.artist.stats.listeners)
        assertEquals("0", response.artist.stats.playcount)
        assertEquals(0, response.artist.tags.tag.size)
    }
}
