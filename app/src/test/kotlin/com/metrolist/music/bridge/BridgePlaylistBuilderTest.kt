package com.metrolist.music.bridge

import com.metrolist.lastfm.models.ArtistTopTracksResponse
import com.metrolist.music.playback.BridgePlaylistBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * Unit tests for BridgePlaylistBuilder.selectTracks().
 *
 * resolveOneTrack() and buildForPath() require mocking the YouTube singleton and SpotifyMapper —
 * deferred to instrumented/integration tests (@Ignore stubs follow Wave 0 pattern from Phase 3).
 */
class BridgePlaylistBuilderTest {

    private val builder = BridgePlaylistBuilder()

    /** Creates a TopTrack with the given rank (name derived from rank for easy assertions). */
    private fun track(rank: Int): ArtistTopTracksResponse.TopTrack =
        ArtistTopTracksResponse.TopTrack(
            name = "Track$rank",
            attr = ArtistTopTracksResponse.TrackAttr(rank = rank.toString())
        )

    /** Creates 50 tracks ranked 1-50 (typical full Last.fm response). */
    private fun fullTrackList(): List<ArtistTopTracksResponse.TopTrack> =
        (1..50).map { track(it) }

    @Test
    fun selectTracks_withFullList_returns2PopularAnd5DeepCuts() {
        val selected = builder.selectTracks(fullTrackList())

        // Popular = ranks 1-2 (exactly 2 tracks)
        val popular = selected.filter { it.startsWith("Track") && it.removePrefix("Track").toIntOrNull() in 1..2 }
        assertEquals("Expected 2 popular tracks", 2, popular.size)

        // Deep cuts = ranks 10-50 (up to 5, shuffled)
        val deepCuts = selected.filter { name ->
            name.removePrefix("Track").toIntOrNull()?.let { it in 10..50 } == true
        }
        assertEquals("Expected 5 deep cut tracks", 5, deepCuts.size)

        assertEquals("Total should be 7 (2 popular + 5 deep cuts)", 7, selected.size)
    }

    @Test
    fun selectTracks_popularComeFirst() {
        val selected = builder.selectTracks(fullTrackList())

        // First 2 items must be popular (rank 1-2)
        val first = selected.take(2)
        for (name in first) {
            val rank = name.removePrefix("Track").toIntOrNull()
            assertTrue("Popular track rank must be 1 or 2, got rank=$rank", rank != null && rank in 1..2)
        }
    }

    @Test
    fun selectTracks_neverIncludesRanks3to9() {
        val selected = builder.selectTracks(fullTrackList())

        for (name in selected) {
            val rank = name.removePrefix("Track").toIntOrNull()
            assertTrue("Track rank $rank should not be in 3-9", rank == null || rank !in 3..9)
        }
    }

    @Test
    fun selectTracks_withFewerThan2Tracks_returnsAllAvailablePopular() {
        val singleTrack = listOf(track(1))
        val selected = builder.selectTracks(singleTrack)
        assertEquals("Should return the single available popular track", 1, selected.size)
        assertEquals("Track1", selected.first())
    }

    @Test
    fun selectTracks_withEmptyList_returnsEmpty() {
        val selected = builder.selectTracks(emptyList())
        assertTrue("Empty input should produce empty output", selected.isEmpty())
    }

    @Test
    fun selectTracks_withNoTracksInRanges_returnsEmpty() {
        // Only ranks 3-9 — both popular (1-2) and deep cuts (10-50) are absent
        val midTierOnly = (3..9).map { track(it) }
        val selected = builder.selectTracks(midTierOnly)
        assertTrue("Mid-tier only input should produce empty output", selected.isEmpty())
    }

    @Test
    fun selectTracks_deepCutsAreCappedAt5() {
        // 20 tracks all in the deep-cut range (10-50)
        val deepCutTracks = (10..29).map { track(it) }
        val selected = builder.selectTracks(deepCutTracks)
        assertTrue("Deep cuts must not exceed 5", selected.size <= 5)
    }

    @Ignore("Requires mocking YouTube singleton — deferred to instrumented integration test suite")
    @Test
    fun resolveOneTrack_returnsNull_whenNoSongItemsFound() {
        // Integration test: YouTube.search returns empty result
    }

    @Ignore("Requires mocking SpotifyMapper.matchScore — deferred to instrumented integration test suite")
    @Test
    fun resolveOneTrack_returnsNull_whenBestScoreBelowThreshold() {
        // Integration test: best match score < MIN_MATCH_THRESHOLD returns null
    }

    @Ignore("Requires mocking both LastFM and YouTube singletons — deferred to instrumented integration test suite")
    @Test
    fun buildForPath_preservesArtistOrder() {
        // PLAY-02, D-03: MediaItems in output must follow bridge path order
    }
}
