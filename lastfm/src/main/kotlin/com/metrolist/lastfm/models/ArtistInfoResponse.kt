/** Metrolist Project (C) 2026 / Licensed under GPL-3.0 | See git history for contributors */
package com.metrolist.lastfm.models

import kotlinx.serialization.Serializable

@Serializable
data class ArtistInfoResponse(
    val artist: ArtistDetail = ArtistDetail()
) {
    @Serializable
    data class ArtistDetail(
        val name: String = "",
        val stats: Stats = Stats(),
        val tags: Tags = Tags()
    )

    @Serializable
    data class Stats(
        val listeners: String = "0",
        val playcount: String = "0"
    )

    @Serializable
    data class Tags(
        val tag: List<Tag> = emptyList()
    )

    @Serializable
    data class Tag(
        val name: String = "",
        val url: String = ""
    )
}
