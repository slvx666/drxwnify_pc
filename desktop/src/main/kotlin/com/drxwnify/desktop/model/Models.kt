package com.drxwnify.desktop.model

import kotlinx.serialization.Serializable

@Serializable
data class ArtistRef(val name: String, val id: String? = null)

@Serializable
data class AlbumRef(val title: String, val id: String? = null)

/**
 * One track as the desktop app knows it. [id] is the YouTube video id for YouTube items,
 * otherwise "<source>:<id>" (the same prefixes the phone uses, e.g. "mfb:" for catalog tracks).
 */
@Serializable
data class Track(
    val id: String,
    val title: String,
    val artists: List<ArtistRef> = emptyList(),
    val album: AlbumRef? = null,
    val durationSec: Int? = null,
    val cover: String? = null,
    val explicit: Boolean = false,
) {
    val artistLine: String get() = artists.joinToString(", ") { it.name }
    val youtubeId: String? get() = id.takeIf { !it.contains(':') }
}

enum class CollectionKind { Album, Playlist, Artist, Mix }

/** An album, playlist, artist or mix shown as a card. */
@Serializable
data class Collection(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String = "",
    val cover: String? = null,
    val year: Int? = null,
    /** In an artist's discography: "album", "single" (singles and EPs) or "compilation". */
    val group: String? = null,
) {
    val type: CollectionKind get() = runCatching { CollectionKind.valueOf(kind) }.getOrDefault(CollectionKind.Playlist)
}

/** A full page: header + tracks (album / playlist / artist top tracks). */
data class CollectionPage(
    val head: Collection,
    val tracks: List<Track>,
    val extraTitle: String? = null,
    val extra: List<Collection> = emptyList(),
    val description: String? = null,
)

/** A row of the home feed, in the phone's order. */
@Serializable
data class HomeRow(
    val title: String,
    val subtitle: String? = null,
    val collections: List<Collection> = emptyList(),
    val tracks: List<Track> = emptyList(),
)

data class SearchResults(
    val top: Track? = null,
    val tracks: List<Track> = emptyList(),
    val albums: List<Collection> = emptyList(),
    val artists: List<Collection> = emptyList(),
    val playlists: List<Collection> = emptyList(),
)

fun Int.asDuration(): String = "%d:%02d".format(this / 60, this % 60)
fun Long.msAsDuration(): String = (this / 1000).toInt().asDuration()
