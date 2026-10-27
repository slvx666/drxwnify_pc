package com.drxwnify.desktop.data

import com.drxwnify.desktop.model.Track
import kotlinx.serialization.Serializable

/**
 * Where a catalog track's audio was found: a VK audio ("owner_id_access") and/or a YouTube video.
 * [chosen]: the version picked by hand ("vk" / "yt") — always played, never replaced by a search.
 */
@Serializable
data class Match(val vk: String? = null, val yt: String? = null, val chosen: String? = null)

@Serializable
data class MatchesData(val byCatalog: Map<String, Match> = emptyMap())

/**
 * The phone's matches (its audio_fallback_match and spotify_match tables) plus what this PC found
 * itself, keyed on the catalog track id — so a liked track plays the same upload as on the phone.
 */
object Matches {
    private val store = Store("matches.json", MatchesData.serializer()) { MatchesData() }

    /** The catalog id behind a track id: "mfb:X" / "spotify:X" / "qbzfb:X" → X; a YouTube id stays as is. */
    fun keyOf(trackId: String): String = when {
        trackId.startsWith("mfb:") -> trackId.removePrefix("mfb:")
        trackId.startsWith("spotify:") -> trackId.removePrefix("spotify:")
        trackId.startsWith("qbzfb:") -> trackId.removePrefix("qbzfb:")
        else -> trackId
    }

    private val videoId = Regex("^[A-Za-z0-9_-]{11}$")

    /** Only real YouTube video ids count: the phone's table also holds its own "mfb:" ids there. */
    private fun Match.clean() = if (yt == null || videoId.matches(yt)) this else copy(yt = null, chosen = chosen.takeIf { it != "yt" })

    fun of(track: Track): Match? = store.value.byCatalog[keyOf(track.id)]?.clean()

    fun putAll(found: Map<String, Match>) = store.update { d ->
        val merged = d.byCatalog.toMutableMap()
        found.forEach { (k, raw) ->
            val m = raw.clean()
            merged[k] = merged[k]?.let { old -> Match(m.vk ?: old.vk, m.yt ?: old.yt, m.chosen ?: old.chosen) } ?: m
        }
        d.copy(byCatalog = merged)
    }

    fun rememberVk(track: Track, vkId: String) {
        if (track.youtubeId != null) return
        putAll(mapOf(keyOf(track.id) to Match(vk = vkId)))
    }

    fun rememberYt(track: Track, videoId: String) {
        if (track.youtubeId != null) return
        putAll(mapOf(keyOf(track.id) to Match(yt = videoId)))
    }

    /** A version picked in "Другая версия": played from now on, here and in the cache. */
    fun choose(track: Track, vk: String? = null, yt: String? = null) = store.update { d ->
        val k = keyOf(track.id)
        val old = d.byCatalog[k] ?: Match()
        d.copy(byCatalog = d.byCatalog + (k to if (vk != null) old.copy(vk = vk, chosen = "vk") else old.copy(yt = yt, chosen = "yt")))
    }

    fun forgetVk(track: Track) = store.update { d ->
        val k = keyOf(track.id)
        val m = d.byCatalog[k] ?: return@update d
        d.copy(byCatalog = d.byCatalog + (k to m.copy(vk = null)))
    }

    val size: Int get() = store.value.byCatalog.size
}
