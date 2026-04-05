/** Metrolist Project (C) 2026 / Licensed under GPL-3.0 | See git history for contributors */
package com.metrolist.lastfm.models

import kotlinx.serialization.Serializable

@Serializable
data class SimilarArtistsResponse(
    val similarartists: SimilarArtists = SimilarArtists()
) {
    @Serializable
    data class SimilarArtists(
        val artist: List<SimilarArtist> = emptyList()
    )

    @Serializable
    data class SimilarArtist(
        val name: String = "",
        val match: String = "0.5",
        val mbid: String = "",
        val url: String = ""
    )
}
