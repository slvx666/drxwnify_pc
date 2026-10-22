package com.drxwnify.desktop.catalog

import com.drxwnify.desktop.data.Log
import com.drxwnify.desktop.model.AlbumRef
import com.drxwnify.desktop.model.ArtistRef
import com.drxwnify.desktop.model.Collection
import com.drxwnify.desktop.model.CollectionPage
import com.drxwnify.desktop.model.HomeRow
import com.drxwnify.desktop.model.SearchResults
import com.drxwnify.desktop.model.Track
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.YouTubeLocale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** The catalog without any account: YouTube Music, the same client the phone uses. */
object YouTubeCatalog {
    fun init() {
        YouTube.locale = YouTubeLocale(gl = "RU", hl = "ru")
        // Like the phone: a visitor id makes YouTube treat us as one steady client.
        com.drxwnify.desktop.data.ioScope.launch {
            YouTube.visitorData().onSuccess { YouTube.visitorData = it }.onFailure { Log.w("Catalog", "no visitor data", it) }
        }
    }

    private val sizeRx = Regex("=w\\d+-h\\d+[^&/]*$")
    fun big(url: String?): String? = url?.let {
        when {
            sizeRx.containsMatchIn(it) -> it.replace(sizeRx, "=w544-h544-l90-rj")
            it.contains("i.ytimg.com") -> it.replace(Regex("/(default|mqdefault|hqdefault|sddefault)\\.jpg"), "/hqdefault.jpg")
            else -> it
        }
    }

    fun SongItem.toTrack() = Track(
        id = id,
        title = title,
        artists = artists.map { ArtistRef(it.name, it.id) },
        album = album?.let { AlbumRef(it.name, it.id) },
        durationSec = duration,
        cover = big(thumbnail),
        explicit = explicit,
    )

    private fun AlbumItem.toCollection() = Collection(
        id = browseId, kind = "Album", title = title,
        subtitle = listOfNotNull(artists?.joinToString(", ") { it.name }, year?.toString()).joinToString(" · "),
        cover = big(thumbnail), year = year,
    )

    private fun ArtistItem.toCollection() = Collection(id = id, kind = "Artist", title = title, subtitle = "Исполнитель", cover = big(thumbnail))

    private fun PlaylistItem.toCollection() = Collection(
        id = id, kind = "Playlist", title = title,
        subtitle = listOfNotNull(author?.name, songCountText).joinToString(" · "), cover = big(thumbnail),
    )

    fun YTItem.toCollection(): Collection? = when (this) {
        is AlbumItem -> toCollection()
        is ArtistItem -> toCollection()
        is PlaylistItem -> toCollection()
        else -> null
    }

    suspend fun search(query: String): SearchResults = coroutineScope {
        val songs = async { YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items.orEmpty() }
        val albums = async { YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM).getOrNull()?.items.orEmpty() }
        val artists = async { YouTube.search(query, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()?.items.orEmpty() }
        val lists = async { YouTube.search(query, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST).getOrNull()?.items.orEmpty() }
        val tracks = songs.await().filterIsInstance<SongItem>().map { it.toTrack() }
        SearchResults(
            top = tracks.firstOrNull(),
            tracks = tracks,
            albums = albums.await().mapNotNull { it.toCollection() },
            artists = artists.await().mapNotNull { it.toCollection() },
            playlists = lists.await().mapNotNull { it.toCollection() },
        )
    }

    fun looksLikeLink(text: String) = Regex("""(https?://|youtu\.be/|youtube\.com/|spotify\.com/|vk\.com/)""", RegexOption.IGNORE_CASE).containsMatchIn(text)

    /** A pasted link: YouTube / YouTube Music opened directly, Spotify found by its title and artist. */
    suspend fun fromLink(text: String): SearchResults? {
        Regex("""(?:v=|youtu\.be/|/shorts/)([A-Za-z0-9_-]{11})""").find(text)?.groupValues?.get(1)?.let { id ->
            val songs = YouTube.queue(listOf(id)).getOrNull().orEmpty().map { it.toTrack() }
            if (songs.isNotEmpty()) return SearchResults(top = songs.first(), tracks = songs + radioSafe(songs.first()))
        }
        Regex("[?&]list=([A-Za-z0-9_-]+)").find(text)?.groupValues?.get(1)?.let { list ->
            playlist(list)?.let { p -> return SearchResults(top = p.tracks.firstOrNull(), tracks = p.tracks, playlists = listOf(p.head)) }
        }
        Regex("""open\.spotify\.com/(?:intl-[a-z]+/)?(track|album|playlist|artist)/([A-Za-z0-9]+)""").find(text)?.let { m ->
            // The embed page carries the item's JSON (name, artists); the normal page is a bare JS app.
            val page = runCatching {
                com.drxwnify.desktop.ui.Images.http.newCall(okhttp3.Request.Builder().url("https://open.spotify.com/embed/${m.groupValues[1]}/${m.groupValues[2]}")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0 Safari/537.36").build())
                    .execute().use { it.body?.string() }
            }.getOrNull() ?: return null
            val title = Regex(""""title":"([^"]+)"""").find(page)?.groupValues?.get(1)?.let(::unescape) ?: return null
            val artistsJson = Regex(""""artists":\[(.*?)]""").find(page)?.groupValues?.get(1).orEmpty()
            val artist = Regex(""""name":"([^"]+)"""").find(artistsJson)?.groupValues?.get(1)
                ?: Regex(""""subtitle":"([^"]+)"""").find(page)?.groupValues?.get(1).orEmpty()
            val r = search("$artist $title".trim())
            return if (m.groupValues[1] == "track") r else r.copy(top = null)
        }
        return null
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&#x27;", "'").replace("&#39;", "'").replace("&quot;", "\"")

    private suspend fun radioSafe(t: Track) = runCatching { radio(t).take(20) }.getOrDefault(emptyList())

    suspend fun suggestions(query: String): List<String> =
        YouTube.searchSuggestions(query).getOrNull()?.queries.orEmpty().take(8)

    /** Home in the phone's order, as far as an account-less catalog allows. */
    suspend fun home(): List<HomeRow> = coroutineScope {
        val home = async { YouTube.home().getOrNull() }
        val releases = async { YouTube.newReleaseAlbums().getOrNull().orEmpty() }
        val page = home.await()
        val sections = page?.sections.orEmpty()
        val songs = sections.flatMap { s -> s.items.filterIsInstance<SongItem>() }.distinctBy { it.id }.map { it.toTrack() }
        val rows = mutableListOf<HomeRow>()
        if (songs.isNotEmpty()) rows += HomeRow("Рекомендации", "без названий — нажми наугад", tracks = songs.take(40))
        val newAlbums = releases.await().map { it.toCollection() }
        if (newAlbums.isNotEmpty()) rows += HomeRow("Новинки", "свежие релизы", collections = newAlbums)
        sections.forEach { s ->
            val cols = s.items.mapNotNull { it.toCollection() }
            if (cols.size >= 3) rows += HomeRow(s.title, s.label, collections = cols)
        }
        if (rows.isEmpty()) Log.w("Catalog", "home came back empty")
        rows
    }

    suspend fun album(id: String): CollectionPage? {
        val page = YouTube.album(id).getOrElse { Log.w("Catalog", "album $id", it); return null }
        val head = page.album.toCollection()
        val tracks = page.songs.map { it.toTrack().let { t -> if (t.cover == null) t.copy(cover = head.cover) else t } }
            .map { it.copy(album = it.album ?: AlbumRef(head.title, head.id), cover = it.cover ?: head.cover) }
        return CollectionPage(head, tracks, if (page.otherVersions.isNotEmpty()) "Другие версии" else null, page.otherVersions.map { it.toCollection() })
    }

    suspend fun playlist(id: String): CollectionPage? {
        val page = YouTube.playlist(id).getOrElse { Log.w("Catalog", "playlist $id", it); return null }
        return CollectionPage(page.playlist.toCollection(), page.songs.map { it.toTrack() })
    }

    suspend fun artist(id: String): CollectionPage? {
        val page = YouTube.artist(id).getOrElse { Log.w("Catalog", "artist $id", it); return null }
        val top = page.sections.flatMap { s -> s.items.filterIsInstance<SongItem>() }.distinctBy { it.id }
            .distinctBy { it.title.lowercase().replace(Regex("""\s*\(.*?\)"""), "").trim() }.take(10).map { it.toTrack() }
        val head = page.artist.toCollection().let { c -> c.copy(subtitle = page.monthlyListenerCount ?: page.subscriberCountText ?: "Исполнитель", cover = c.cover ?: top.firstOrNull()?.cover) }
        val albums = page.sections.flatMap { s -> s.items.mapNotNull { (it as? AlbumItem)?.toCollection() } }.distinctBy { it.id }
        return CollectionPage(head, top, "Альбомы и синглы", albums, page.description)
    }

    /** The endless continuation of a track ("радио"), like the phone's queue. */
    suspend fun radio(seed: Track): List<Track> {
        val vid = seed.youtubeId ?: return emptyList()
        val res = YouTube.next(WatchEndpoint(videoId = vid, playlistId = "RDAMVM$vid")).getOrNull() ?: return emptyList()
        return res.items.map { it.toTrack() }.filter { it.id != vid }
    }
}
