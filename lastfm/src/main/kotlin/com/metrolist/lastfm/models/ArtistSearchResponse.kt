/** Metrolist Project (C) 2026 / Licensed under GPL-3.0 | See git history for contributors */
package com.metrolist.lastfm.models

import kotlinx.serialization.Serializable

@Serializable
data class ArtistSearchResponse(
    val results: Results
) {
    @Serializable
    data class Results(
        val artistmatches: ArtistMatches
    ) {
        @Serializable
        data class ArtistMatches(
            val artist: List<ArtistMatch> = emptyList()
        )
    }

    @Serializable
    data class ArtistMatch(
        val name: String,
        val mbid: String = "",
        val url: String = "",
        val listeners: String = ""
    )
}
