/** Metrolist Project (C) 2026 / Licensed under GPL-3.0 | See git history for contributors */
package com.metrolist.lastfm.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ArtistTopTracksResponse(
    val toptracks: TopTracks
) {
    @Serializable
    data class TopTracks(
        val track: List<TopTrack> = emptyList()
    )

    @Serializable
    data class TopTrack(
        val name: String,
        val playcount: String = "",
        val listeners: String = "",
        @SerialName("@attr") val attr: TrackAttr = TrackAttr(),
        val artist: TrackArtist = TrackArtist()
    ) {
        val rankInt: Int get() = attr.rank.toIntOrNull() ?: 999
    }

    @Serializable
    data class TrackAttr(
        val rank: String = ""
    )

    @Serializable
    data class TrackArtist(
        val name: String = ""
    )
}
