package com.drxwnify.desktop.catalog

import com.drxwnify.desktop.data.Library
import com.drxwnify.desktop.data.Log
import com.drxwnify.desktop.data.Prefs
import com.drxwnify.desktop.data.ioScope
import com.drxwnify.desktop.model.Track
import com.metrolist.spotify.Spotify
import kotlinx.coroutines.launch

/**
 * Likes in step with the Spotify account, like the phone's "sync likes": a like here is saved to the
 * account, and the account's likes (from the phone or anywhere) come back into «Любимые».
 */
object LikesSync {
    private fun enabled() = SpotifyCatalog.linked && Prefs.settings.value.spotifySyncLikes

    suspend fun pull() {
        if (!enabled()) return
        val remote = SpotifyCatalog.likedTracks() ?: return
        Library.mergeSpotifyLikes(remote)
        Log.i("Spotify", "likes synced: ${remote.size} from the account")
    }

    fun pushed(track: Track, liked: Boolean) {
        if (!enabled() || !track.id.startsWith("mfb:")) return
        val id = track.id.removePrefix("mfb:")
        ioScope.launch {
            (if (liked) Spotify.saveTrack(id) else Spotify.removeTrack(id))
                .onFailure { Log.w("Spotify", "like of ${track.title} not saved to the account", it) }
        }
    }
}
