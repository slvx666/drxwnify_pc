package com.drxwnify.desktop.data

import com.drxwnify.desktop.model.Collection
import com.drxwnify.desktop.model.Track
import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val tracks: List<Track> = emptyList(),
    val cover: String? = null,
    val created: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
)

@Serializable
data class Played(val track: Track, val at: Long)

@Serializable
data class LibraryData(
    val liked: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val savedAlbums: List<Collection> = emptyList(),
    val followedArtists: List<Collection> = emptyList(),
    val history: List<Played> = emptyList(),
    /** Recently opened library entries, most recent first ("Недавние"). */
    val recentKeys: List<String> = emptyList(),
    val pinnedKeys: List<String> = listOf("liked"),
    /** When each liked track was liked (track id → epoch ms), to keep likes from all sources in one order. */
    val likedAt: Map<String, Long> = emptyMap(),
    /** Ids that were in the Spotify account's likes at the last sync: gone from there = unliked elsewhere. */
    val spotifyLikes: List<String> = emptyList(),
)

object Library {
    private val store = Store("library.json", LibraryData.serializer()) { LibraryData() }
    val state = store.state
    val value get() = store.value

    fun isLiked(id: String) = store.value.liked.any { it.id == id }

    fun toggleLike(track: Track) {
        val nowLiked = !isLiked(track.id)
        store.update { lib ->
            if (!nowLiked) lib.copy(liked = lib.liked.filterNot { it.id == track.id }, likedAt = lib.likedAt - track.id)
            else lib.copy(liked = listOf(track) + lib.liked, likedAt = lib.likedAt + (track.id to System.currentTimeMillis()))
        }
        com.drxwnify.desktop.catalog.LikesSync.pushed(track, nowLiked)
    }

    /**
     * The account's likes (with their dates) laid over the local ones: everything in one list,
     * newest first. A catalog track that was in the account last time and isn't now was unliked on
     * another device, so it goes here too.
     */
    fun mergeSpotifyLikes(remote: List<Pair<Track, Long>>) = store.update { lib ->
        val remoteIds = remote.mapTo(HashSet()) { it.first.id }
        val wasRemote = lib.spotifyLikes.toHashSet()
        val local = lib.liked.filter { it.id !in remoteIds && it.id !in wasRemote }
        val dates = lib.likedAt + remote.associate { (t, at) -> t.id to (at.takeIf { it > 0 } ?: lib.likedAt[t.id] ?: 0L) }
        val merged = (remote.map { it.first } + local).distinctBy { it.id }
            .withIndex().sortedWith(compareByDescending<IndexedValue<Track>> { dates[it.value.id] ?: 0L }.thenBy { it.index }).map { it.value }
        val keep = merged.mapTo(HashSet()) { it.id }
        lib.copy(liked = merged, likedAt = dates.filterKeys { it in keep }, spotifyLikes = remoteIds.toList())
    }

    fun played(track: Track) {
        if (Prefs.settings.value.pauseHistory) return
        store.update { lib ->
            lib.copy(history = (listOf(Played(track, System.currentTimeMillis())) + lib.history).take(2000))
        }
    }

    fun removeFromHistory(trackId: String) = store.update { it.copy(history = it.history.filterNot { p -> p.track.id == trackId }) }

    fun touch(key: String) = store.update { it.copy(recentKeys = (listOf(key) + it.recentKeys.filter { k -> k != key }).take(200)) }

    fun isSaved(c: Collection) = store.value.savedAlbums.any { it.id == c.id } || store.value.followedArtists.any { it.id == c.id } ||
        com.drxwnify.desktop.catalog.SpotifyCatalog.library.value.any { it.id == c.id }

    fun toggleSaved(c: Collection) {
        val save = !isSaved(c)
        store.update { lib ->
            if (c.kind == "Artist") lib.copy(followedArtists = if (save) listOf(c) + lib.followedArtists else lib.followedArtists.filterNot { it.id == c.id })
            else lib.copy(savedAlbums = if (save) listOf(c) + lib.savedAlbums else lib.savedAlbums.filterNot { it.id == c.id })
        }
        com.drxwnify.desktop.catalog.SpotifyCatalog.setSaved(c, save)
    }

    fun togglePin(key: String) = store.update { lib ->
        lib.copy(pinnedKeys = if (key in lib.pinnedKeys) lib.pinnedKeys - key else lib.pinnedKeys + key)
    }

    fun createPlaylist(name: String, first: Track? = null): Playlist {
        val p = Playlist(id = "pl" + System.currentTimeMillis().toString(36), name = name, tracks = listOfNotNull(first))
        store.update { it.copy(playlists = listOf(p) + it.playlists) }
        return p
    }

    fun addToPlaylist(id: String, track: Track) = store.update { lib ->
        lib.copy(playlists = lib.playlists.map { if (it.id == id && it.tracks.none { t -> t.id == track.id }) it.copy(tracks = it.tracks + track) else it })
    }

    fun removeFromPlaylist(id: String, trackId: String) = store.update { lib ->
        lib.copy(playlists = lib.playlists.map { if (it.id == id) it.copy(tracks = it.tracks.filterNot { t -> t.id == trackId }) else it })
    }

    fun deletePlaylist(id: String) = store.update { lib -> lib.copy(playlists = lib.playlists.filterNot { it.id == id }) }

    /**
     * What the phone's backup holds, laid over this PC's library: the phone's likes come first (its
     * order), its playlists replace earlier imports of the same ones, history is merged by time.
     */
    fun importPhone(phone: LibraryData) = store.update { lib ->
        val phonePlaylistIds = phone.playlists.map { it.id }.toSet()
        val dates = lib.likedAt + phone.likedAt
        lib.copy(
            liked = (phone.liked + lib.liked).distinctBy { it.id }.sortedByDescending { dates[it.id] ?: 0L },
            likedAt = dates,
            playlists = phone.playlists + lib.playlists.filterNot { it.id in phonePlaylistIds },
            savedAlbums = (phone.savedAlbums + lib.savedAlbums).distinctBy { it.id },
            followedArtists = (phone.followedArtists + lib.followedArtists).distinctBy { it.id },
            history = (phone.history + lib.history).distinctBy { it.track.id to it.at }.sortedByDescending { it.at }.take(2000),
            recentKeys = (phone.recentKeys + lib.recentKeys).distinct().take(200),
            pinnedKeys = (lib.pinnedKeys + phone.pinnedKeys).distinct(),
        )
    }
}
