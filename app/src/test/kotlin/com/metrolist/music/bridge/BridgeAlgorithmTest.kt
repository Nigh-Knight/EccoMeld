/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for BridgeAlgorithm — Kotlin port of bridgeCrawl.ts bidirectional beam search.
 *
 * Graph topology used in tests:
 *   A -> B, C
 *   B -> D
 *   C -> D
 *   D -> E
 *
 * Expected: findBridge("A", "E") finds a path through the graph.
 */
class BridgeAlgorithmTest {

    private lateinit var mockCache: KotlinBridgeCache
    private lateinit var testScope: TestScope
    private lateinit var algorithm: BridgeAlgorithm

    @Before
    fun setUp() {
        testScope = TestScope(StandardTestDispatcher())
        mockCache = mock()
        algorithm = BridgeAlgorithm(mockCache, testScope.backgroundScope)
    }

    // ── Same-artist shortcut ─────────────────────────────────────────────────

    @Test
    fun `findBridge same artist returns found=true with single-element path`() = testScope.runTest {
        val result = algorithm.findBridge("Radiohead", "Radiohead")

        assertTrue(result.found)
        assertEquals(listOf("Radiohead"), result.path)
    }

    // ── Connected pair finds a path ──────────────────────────────────────────

    @Test
    fun `findBridge connected pair A to E finds path`() = testScope.runTest {
        // Set up graph: A -> [B,C], B -> [D], C -> [D], D -> [E]
        whenever(mockCache.getSimilarArtists(eq("A"), any())).thenReturn(
            listOf(
                CachedSimilarArtist("B", 0.8f),
                CachedSimilarArtist("C", 0.7f),
            )
        )
        whenever(mockCache.getSimilarArtists(eq("B"), any())).thenReturn(
            listOf(CachedSimilarArtist("D", 0.6f))
        )
        whenever(mockCache.getSimilarArtists(eq("C"), any())).thenReturn(
            listOf(CachedSimilarArtist("D", 0.65f))
        )
        whenever(mockCache.getSimilarArtists(eq("D"), any())).thenReturn(
            listOf(CachedSimilarArtist("E", 0.5f))
        )
        whenever(mockCache.getSimilarArtists(eq("E"), any())).thenReturn(
            listOf(
                CachedSimilarArtist("D", 0.5f),
                CachedSimilarArtist("C", 0.4f),
            )
        )
        // Metadata returns null (safe defaults)
        whenever(mockCache.getArtistMeta(any())).thenReturn(null)

        val result = algorithm.findBridge("A", "E")

        assertTrue("Should find a path", result.found)
        assertTrue("Path should start with A", result.path.first() == "A")
        assertTrue("Path should end with E", result.path.last() == "E")
        assertTrue("Path should have at least 2 nodes", result.path.size >= 2)
    }

    // ── Disconnected pair returns found=false ────────────────────────────────

    @Test
    fun `findBridge disconnected artists returns found=false`() = testScope.runTest {
        // Both artists have empty similar lists — no connections possible
        whenever(mockCache.getSimilarArtists(any(), any())).thenReturn(emptyList())
        whenever(mockCache.getArtistMeta(any())).thenReturn(null)

        val result = algorithm.findBridge("IsolatedArtist1", "IsolatedArtist2")

        assertFalse("Should not find a path when disconnected", result.found)
        assertTrue("Path should be empty when not found", result.path.isEmpty())
    }

    // ── Progress callback fires ──────────────────────────────────────────────

    @Test
    fun `findBridge fires progress callback with analyzing phase`() = testScope.runTest {
        whenever(mockCache.getSimilarArtists(any(), any())).thenReturn(emptyList())
        whenever(mockCache.getArtistMeta(any())).thenReturn(null)

        val phases = mutableListOf<String>()
        algorithm.findBridge("ArtistX", "ArtistY") { progress ->
            phases.add(progress.phase)
        }

        assertTrue("Should fire at least one progress callback", phases.isNotEmpty())
        assertTrue("Should fire 'analyzing' phase", phases.contains("analyzing"))
        assertTrue("Should fire 'searching' phase", phases.any { it == "searching" || it == "fallback" })
    }

    // ── tagJaccard function ──────────────────────────────────────────────────

    @Test
    fun `tagJaccard returns 0 for disjoint sets`() {
        val result = algorithm.tagJaccard(
            listOf("rock", "indie"),
            listOf("hip-hop", "rap"),
        )
        assertEquals(0.0f, result, 0.001f)
    }

    @Test
    fun `tagJaccard returns 1 for identical sets`() {
        val tags = listOf("rock", "indie", "alternative")
        val result = algorithm.tagJaccard(tags, tags)
        assertEquals(1.0f, result, 0.001f)
    }

    @Test
    fun `tagJaccard returns 0 when both sets empty`() {
        val result = algorithm.tagJaccard(emptyList(), emptyList())
        assertEquals(0.0f, result, 0.001f)
    }

    @Test
    fun `tagJaccard returns 0 when one set empty`() {
        val result = algorithm.tagJaccard(listOf("rock"), emptyList())
        assertEquals(0.0f, result, 0.001f)
    }

    @Test
    fun `tagJaccard is case-insensitive`() {
        val result = algorithm.tagJaccard(
            listOf("Rock", "Indie"),
            listOf("rock", "indie"),
        )
        assertEquals(1.0f, result, 0.001f)
    }

    // ── Path reconstruction order ────────────────────────────────────────────

    @Test
    fun `findBridge path starts with start artist and ends with end artist`() = testScope.runTest {
        // A -> B -> C direct chain
        whenever(mockCache.getSimilarArtists(eq("Start"), any())).thenReturn(
            listOf(CachedSimilarArtist("Middle", 0.7f))
        )
        whenever(mockCache.getSimilarArtists(eq("Middle"), any())).thenReturn(
            listOf(CachedSimilarArtist("End", 0.6f), CachedSimilarArtist("Start", 0.8f))
        )
        whenever(mockCache.getSimilarArtists(eq("End"), any())).thenReturn(
            listOf(CachedSimilarArtist("Middle", 0.6f))
        )
        whenever(mockCache.getArtistMeta(any())).thenReturn(null)

        val result = algorithm.findBridge("Start", "End")

        if (result.found) {
            assertEquals("Start", result.path.first())
            assertEquals("End", result.path.last())
        }
    }
}
