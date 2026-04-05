/** Metrolist Project (C) 2026 / Licensed under GPL-3.0 | See git history for contributors */
package com.metrolist.lastfm

import com.metrolist.lastfm.models.SimilarArtistsResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SimilarArtistsResponseTest {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Test
    fun deserialize_full_similar_artists_response() {
        val jsonString = """
            {"similarartists":{"artist":[
                {"name":"Thom Yorke","match":"0.7","mbid":"abc123","url":"https://last.fm/music/Thom+Yorke"},
                {"name":"Portishead","match":"0.6","mbid":"","url":"https://last.fm/music/Portishead"}
            ],"@attr":{"artist":"Radiohead"}}}
        """.trimIndent()
        val response = json.decodeFromString<SimilarArtistsResponse>(jsonString)

        assertEquals(2, response.similarartists.artist.size)
        assertEquals("Thom Yorke", response.similarartists.artist[0].name)
        assertEquals("0.7", response.similarartists.artist[0].match)
        assertEquals("Portishead", response.similarartists.artist[1].name)
        assertEquals("0.6", response.similarartists.artist[1].match)
    }

    @Test
    fun deserialize_similar_artist_has_name_match_fields_with_defaults() {
        val jsonString = """{"similarartists":{"artist":[{"name":"Test Artist"}]}}"""
        val response = json.decodeFromString<SimilarArtistsResponse>(jsonString)

        val artist = response.similarartists.artist[0]
        assertEquals("Test Artist", artist.name)
        assertEquals("0.5", artist.match)
        assertEquals("", artist.mbid)
        assertEquals("", artist.url)
    }

    @Test
    fun deserialize_empty_similar_artists_list() {
        val jsonString = """{"similarartists":{"artist":[]}}"""
        val response = json.decodeFromString<SimilarArtistsResponse>(jsonString)

        assertEquals(0, response.similarartists.artist.size)
    }
}
