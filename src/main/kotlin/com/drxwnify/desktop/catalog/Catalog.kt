package com.drxwnify.desktop.catalog

import com.drxwnify.desktop.data.Log
import com.drxwnify.desktop.data.Prefs
import com.drxwnify.desktop.model.Collection
import com.drxwnify.desktop.model.CollectionPage
import com.drxwnify.desktop.model.SearchResults
import com.drxwnify.desktop.model.Track

/**
 * One door to both catalogs: an id says which one it belongs to ("spotify:…" — Spotify, anything
 * else — YouTube Music), and search follows the catalog chosen in the settings (Spotify by default,
 * as on the phone), falling back to YouTube when Spotify can't answer.
 */
object Catalog {
    private fun isSpotify(id: String) = id.startsWith(SpotifyCatalog.PREFIX)

    val spotifyFirst: Boolean get() = Prefs.settings.value.catalog == "spotify"

    suspend fun album(id: String): CollectionPage? = if (isSpotify(id)) SpotifyCatalog.album(id) else YouTubeCatalog.album(id)

    suspend fun artist(id: String): CollectionPage? = if (isSpotify(id)) SpotifyCatalog.artist(id) else YouTubeCatalog.artist(id)

    suspend fun playlist(id: String): CollectionPage? = if (isSpotify(id)) SpotifyCatalog.playlist(id) else YouTubeCatalog.playlist(id)

    suspend fun open(c: Collection): CollectionPage? = when (c.kind) {
        "Album" -> album(c.id)
        "Artist" -> artist(c.id)
        else -> playlist(c.id)
    }

    /** A shared track link (Drxwnify or Spotify): the track, found by id or by its title and artist. */
    suspend fun linkedTrack(t: AppLinks.Target): Track? {
        val id = AppLinks.appId(t)
        val direct = if (id.startsWith(SpotifyCatalog.PREFIX)) SpotifyCatalog.track(id) else YouTubeCatalog.search(listOfNotNull(t.by, t.title).joinToString(" ")).tracks.firstOrNull { it.id == id }
        return direct ?: listOfNotNull(t.by, t.title).joinToString(" ").takeIf { it.isNotBlank() }?.let { search(it).tracks.firstOrNull() }
    }

    suspend fun search(query: String): SearchResults {
        AppLinks.parse(query)?.takeIf { it.kind == AppLinks.Kind.TRACK }?.let { target ->
            linkedTrack(target)?.let { tr -> return SearchResults(top = tr, tracks = listOf(tr) + runCatching { radio(tr).take(20) }.getOrDefault(emptyList())) }
        }
        if (YouTubeCatalog.looksLikeLink(query)) YouTubeCatalog.fromLink(query)?.let { return it }
        if (spotifyFirst) runCatching { SpotifyCatalog.search(query) }.onFailure { Log.w("Catalog", "Spotify search failed", it) }.getOrNull()?.let { return it }
        return YouTubeCatalog.search(query)
    }

    /** What plays after the queue runs out: the seed's own catalog continues it. */
    suspend fun radio(seed: Track): List<Track> =
        if (seed.youtubeId != null) YouTubeCatalog.radio(seed)
        else SpotifyCatalog.radio(seed).ifEmpty {
            com.drxwnify.desktop.data.Matches.of(seed)?.yt?.let { YouTubeCatalog.radio(seed.copy(id = it)) }.orEmpty()
        }
}
