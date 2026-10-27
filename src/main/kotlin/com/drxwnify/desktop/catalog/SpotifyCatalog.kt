package com.drxwnify.desktop.catalog

import com.drxwnify.desktop.data.Log
import com.drxwnify.desktop.data.Paths
import com.drxwnify.desktop.data.Prefs
import com.drxwnify.desktop.data.appJson
import com.drxwnify.desktop.data.ioScope
import com.drxwnify.desktop.model.AlbumRef
import com.drxwnify.desktop.model.ArtistRef
import com.drxwnify.desktop.model.Collection
import com.drxwnify.desktop.model.CollectionPage
import com.drxwnify.desktop.model.SearchResults
import com.drxwnify.desktop.model.Track
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.SpotifyAuth
import com.metrolist.spotify.SpotifyHashProvider
import com.metrolist.spotify.models.SpotifyAlbum
import com.metrolist.spotify.models.SpotifyArtist
import com.metrolist.spotify.models.SpotifyImage
import com.metrolist.spotify.models.SpotifyLibraryEntry
import com.metrolist.spotify.models.SpotifyPlaylist
import com.metrolist.spotify.models.SpotifySimpleAlbum
import com.metrolist.spotify.models.SpotifyTrack
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Spotify as the phone uses it: the account's sp_dc cookie (moved over from the phone's backup)
 * gives the web player's token — library, likes, the personal home; without an account Spotify's
 * anonymous token still opens the public catalog. Ids carry a "spotify:" prefix (albums, artists,
 * playlists) and tracks are "mfb:<id>", exactly as in the phone's database.
 */
object SpotifyCatalog {
    const val PREFIX = "spotify:"
    private const val HASHES_URL = "https://francescograzioso.github.io/Meld/spotify-gql-hashes.json"

    private val refresh = Mutex()
    @Volatile private var anonymous: Pair<String, Long>? = null
    @Volatile private var lastRefreshAt = 0L
    @Volatile private var failedAt = 0L

    private val _library = MutableStateFlow(readCached())
    /** The account's "Your Library" (playlists, albums, artists), Spotify's order. */
    val library: StateFlow<List<Collection>> = _library

    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired

    val linked: Boolean get() = !Prefs.accounts.value.spotifySpDc.isNullOrBlank()

    fun init() {
        Spotify.tokenSupplier = ::supply
        Spotify.logger = { level, msg -> if (level == "E" || level == "W") Log.w("Spotify", msg) else Log.d("Spotify", msg) }
        File(Paths.root, "spotify-hashes.json").takeIf { it.exists() }?.let { f -> runCatching { applyHashes(f.readText(), SpotifyHashProvider.HashSource.CACHED) } }
        ioScope.launch {
            runCatching {
                val body = com.drxwnify.desktop.ui.Images.http.newCall(okhttp3.Request.Builder().url(HASHES_URL).build()).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
                if (body != null && applyHashes(body, SpotifyHashProvider.HashSource.REMOTE) > 0) File(Paths.root, "spotify-hashes.json").writeText(body)
            }.onFailure { Log.d("Spotify", "hash registry unreachable: ${it.message}") }
        }
        if (linked) ioScope.launch { refreshAccount() }
    }

    /** After the phone's backup brought (or replaced) the account. */
    fun accountChanged() {
        anonymous = null
        failedAt = 0
        _sessionExpired.value = false
        Spotify.accessToken = null
        if (linked) ioScope.launch { refreshAccount() } else _library.value = emptyList()
    }

    fun logout() {
        Prefs.accounts.update { it.copy(spotifySpDc = null, spotifySpKey = null, spotifyAccessToken = null, spotifyTokenExpiry = 0, spotifyName = null, spotifyUserId = null) }
        accountChanged()
        writeCached(emptyList())
    }

    private suspend fun refreshAccount() {
        if (Prefs.accounts.value.spotifyName.isNullOrBlank()) {
            Spotify.me().onSuccess { me -> Prefs.accounts.update { it.copy(spotifyName = me.displayName ?: it.spotifyName, spotifyUserId = me.id) } }
        }
        loadLibrary()
        LikesSync.pull()
    }

    private fun applyHashes(json: String, source: SpotifyHashProvider.HashSource): Int {
        val ops = appJson.parseToJsonElement(json).jsonObject["operations"]?.jsonObject ?: return 0
        val parsed = ops.mapNotNull { (op, v) ->
            val o = v.jsonObject
            val hash = o["hash"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            op to SpotifyHashProvider.RemoteHashEntry(hash, o["previous_hash"]?.jsonPrimitive?.contentOrNull)
        }.toMap()
        return SpotifyHashProvider.updateHashes(parsed, source).updated
    }

    // ── token ──

    private suspend fun supply(force: Boolean): String? {
        val acc = Prefs.accounts.value
        val dc = acc.spotifySpDc
        if (dc.isNullOrBlank() || (System.currentTimeMillis() - failedAt < 5 * 60_000)) return anonymousToken(force)
        val now = System.currentTimeMillis()
        if (!force && acc.spotifyAccessToken != null && now < acc.spotifyTokenExpiry - 30_000) return acc.spotifyAccessToken
        return refresh.withLock {
            val cur = Prefs.accounts.value
            val t = System.currentTimeMillis()
            if (cur.spotifyAccessToken != null && t < cur.spotifyTokenExpiry - 30_000 && (!force || t - lastRefreshAt < 10_000)) return@withLock cur.spotifyAccessToken
            SpotifyAuth.fetchAccessToken(dc, cur.spotifySpKey.orEmpty()).fold(
                onSuccess = { tok ->
                    Prefs.accounts.update { it.copy(spotifyAccessToken = tok.accessToken, spotifyTokenExpiry = tok.accessTokenExpirationTimestampMs) }
                    lastRefreshAt = System.currentTimeMillis()
                    _sessionExpired.value = false
                    tok.accessToken
                },
                onFailure = { e ->
                    Log.w("Spotify", "token refresh failed", e)
                    failedAt = System.currentTimeMillis()
                    if (e.message?.contains("anonymous") == true || e.message?.contains("expired") == true) _sessionExpired.value = true
                    anonymousToken(false)
                },
            )
        }
    }

    private suspend fun anonymousToken(force: Boolean): String? {
        if (!force) anonymous?.takeIf { System.currentTimeMillis() < it.second - 30_000 }?.let { return it.first }
        return SpotifyAuth.fetchAnonymousToken().fold(
            onSuccess = { t -> anonymous = t.accessToken to t.accessTokenExpirationTimestampMs; t.accessToken },
            onFailure = { Log.w("Spotify", "no anonymous token", it); null },
        )
    }

    // ── mapping ──

    fun image(list: List<SpotifyImage>, want: Int = 300): String? =
        list.filter { it.url.isNotBlank() }.let { imgs ->
            imgs.sortedBy { it.width ?: 0 }.firstOrNull { (it.width ?: 0) >= want }?.url ?: imgs.maxByOrNull { it.width ?: 0 }?.url
        }

    fun SpotifyTrack.toTrack(fallbackAlbum: SpotifySimpleAlbum? = null): Track {
        val alb = album?.takeIf { it.id.isNotBlank() } ?: fallbackAlbum
        return Track(
            id = "mfb:$id",
            title = name,
            artists = artists.map { ArtistRef(it.name, it.id?.takeIf { s -> s.isNotBlank() }?.let { s -> PREFIX + s }) },
            album = alb?.let { AlbumRef(it.name, PREFIX + it.id) },
            durationSec = (durationMs / 1000).takeIf { it > 0 },
            cover = image(alb?.images.orEmpty()) ?: image(fallbackAlbum?.images.orEmpty()),
            explicit = explicit,
        )
    }

    private fun year(date: String?) = date?.take(4)?.toIntOrNull()

    fun SpotifyAlbum.toCollection() = Collection(
        id = PREFIX + id, kind = "Album", title = name,
        subtitle = listOfNotNull(artists.joinToString(", ") { it.name }.takeIf { it.isNotBlank() }, year(releaseDate)?.toString()).joinToString(" · "),
        cover = image(images), year = year(releaseDate),
    )

    fun SpotifyArtist.toCollection() = Collection(id = PREFIX + id, kind = "Artist", title = name, subtitle = "Исполнитель", cover = image(images))

    fun SpotifyPlaylist.toCollection() = Collection(
        id = PREFIX + id, kind = "Playlist", title = name,
        subtitle = listOfNotNull(owner?.displayName, tracks?.total?.takeIf { it > 0 }?.let { "$it треков" }).joinToString(" · "),
        cover = image(images),
    )

    private fun usable(t: SpotifyTrack) = t.id.isNotBlank() && !t.isLocal

    // ── catalog ──

    suspend fun search(query: String): SearchResults? {
        val r = Spotify.search(query, listOf("track", "album", "artist", "playlist"), limit = 20).getOrElse { Log.w("Spotify", "search failed", it); return null }
        val tracks = r.tracks?.items.orEmpty().filter(::usable).map { it.toTrack() }
        val artists = r.artists?.items.orEmpty().filter { it.id.isNotBlank() }.map { it.toCollection() }
        if (tracks.isEmpty() && artists.isEmpty() && r.albums?.items.isNullOrEmpty()) return null
        return SearchResults(
            top = tracks.firstOrNull(),
            tracks = tracks,
            albums = r.albums?.items.orEmpty().filter { it.id.isNotBlank() }.map { it.toCollection() },
            artists = artists,
            playlists = r.playlists?.items.orEmpty().filter { it.id.isNotBlank() }.map { it.toCollection() },
        )
    }

    suspend fun album(id: String): CollectionPage? {
        val a = Spotify.album(id.removePrefix(PREFIX)).getOrElse { Log.w("Spotify", "album $id", it); return null }
        val simple = SpotifySimpleAlbum(id = a.id, name = a.name, images = a.images, releaseDate = a.releaseDate, albumType = a.albumType, artists = a.artists)
        val tracks = a.tracks?.items.orEmpty().filter(::usable).map { it.toTrack(simple) }
        val head = a.toCollection().let { c -> c.copy(cover = image(a.images, 600) ?: c.cover) }
        val more = a.artists.firstOrNull()?.id?.let { artistId ->
            Spotify.artistDiscography(artistId).getOrNull()?.let { d -> (d.albums + d.singles).filter { it.id != a.id }.take(12).map { it.toCollection() } }
        }.orEmpty()
        return CollectionPage(head, tracks, if (more.isNotEmpty()) "Ещё у ${a.artists.first().name}" else null, more)
    }

    suspend fun artist(id: String): CollectionPage? = coroutineScope {
        val bare = id.removePrefix(PREFIX)
        val info = async { Spotify.artist(bare).getOrNull() }
        val top = async { Spotify.artistTopTracks(bare).getOrNull()?.tracks.orEmpty() }
        val disco = async { Spotify.artistDiscography(bare).getOrNull() }
        // An id Spotify doesn't know comes back as an artist without a name: that's "not found".
        val a = info.await()?.takeIf { it.name.isNotBlank() } ?: return@coroutineScope null
        val d = disco.await()
        fun group(x: com.metrolist.spotify.models.SpotifyAlbum, fallback: String) = when (x.albumType?.lowercase()) {
            "single", "ep" -> "single"; "compilation" -> "compilation"; "album" -> "album"; else -> fallback
        }
        val grouped = d?.albums.orEmpty().map { it to group(it, "album") } + d?.singles.orEmpty().map { it to group(it, "single") } +
            d?.compilations.orEmpty().map { it to group(it, "compilation") } + listOfNotNull(d?.latest).map { it to group(it, "album") }
        // On the artist's own page the artist's name on every card says nothing: year and kind instead.
        val releases = grouped.distinctBy { it.first.id }.sortedByDescending { it.first.releaseDate.orEmpty() }.map { (x, g) ->
            x.toCollection().copy(group = g, subtitle = listOfNotNull(year(x.releaseDate)?.toString(), when (g) { "single" -> "Сингл"; "compilation" -> "Сборник"; else -> "Альбом" }).joinToString(" · "))
        }
        val head = a.toCollection().copy(
            subtitle = a.monthlyListeners?.let { "%,d слушателей в месяц".format(it).replace(',', ' ') } ?: "Исполнитель",
            cover = image(a.images, 600),
        )
        CollectionPage(head, top.await().filter(::usable).take(10).map { it.toTrack() }, "Дискография", releases)
    }

    suspend fun track(id: String): Track? = Spotify.getTrack(id.removePrefix(PREFIX).removePrefix("mfb:")).getOrNull()?.takeIf(::usable)?.toTrack()

    suspend fun relatedArtists(id: String): List<SpotifyArtist> = Spotify.artistRelatedArtists(id.removePrefix(PREFIX)).getOrNull().orEmpty()

    suspend fun topTracks(id: String): List<Track> = Spotify.artistTopTracks(id.removePrefix(PREFIX)).getOrNull()?.tracks.orEmpty().filter(::usable).map { it.toTrack() }

    suspend fun playlist(id: String): CollectionPage? {
        val bare = id.removePrefix(PREFIX)
        val meta = Spotify.playlist(bare).getOrElse { Log.w("Spotify", "playlist $id", it); return null }
        val tracks = mutableListOf<Track>()
        var offset = 0
        while (offset < 2000) {
            val page = Spotify.playlistTracks(bare, limit = 100, offset = offset).getOrElse { Log.w("Spotify", "playlist page $offset", it); break }
            tracks += page.items.mapNotNull { it.track }.filter(::usable).map { it.toTrack() }
            offset += 100
            if (page.items.isEmpty() || offset >= page.total) break
        }
        // The page header counts the tracks itself: only the owner goes in its subtitle.
        val head = meta.toCollection().let { c -> c.copy(subtitle = meta.owner?.displayName.orEmpty(), cover = image(meta.images, 600) ?: c.cover ?: tracks.firstOrNull()?.cover) }
        return CollectionPage(head, tracks, description = meta.description?.takeIf { it.isNotBlank() }?.replace(Regex("<[^>]+>"), ""))
    }

    /** All the account's liked tracks with when they were liked, newest first (up to 5000). */
    suspend fun likedTracks(): List<Pair<Track, Long>>? {
        if (!linked) return null
        val out = mutableListOf<Pair<Track, Long>>()
        var offset = 0
        while (offset < 5000) {
            val page = Spotify.likedSongs(limit = 50, offset = offset).getOrElse { e -> Log.w("Spotify", "liked page $offset", e); return null }
            out += page.items.filter { usable(it.track) }.map { s ->
                s.track.toTrack() to (s.addedAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L)
            }
            offset += 50
            if (page.items.isEmpty() || offset >= page.total) break
        }
        return out
    }

    suspend fun loadLibrary() {
        if (!linked) return
        val all = mutableListOf<SpotifyLibraryEntry>()
        var offset = 0
        while (offset < 1000) {
            val page = Spotify.myLibrary(filter = null, limit = 50, offset = offset).getOrElse { Log.w("Spotify", "library page $offset", it); break }
            all += page.items
            offset += 50
            if (page.items.isEmpty() || offset >= page.total) break
        }
        if (all.isEmpty()) return
        val cols = all.mapNotNull { e ->
            val id = e.uri.substringAfterLast(':').ifBlank { e.id }
            when (e.kind) {
                SpotifyLibraryEntry.Kind.PLAYLIST -> Collection(PREFIX + id, "Playlist", e.name, listOfNotNull("Плейлист", e.creator).joinToString(" · "), e.imageUrl)
                SpotifyLibraryEntry.Kind.ALBUM -> Collection(PREFIX + id, "Album", e.name, listOfNotNull(if (e.albumType == "single") "Сингл" else "Альбом", e.creator).joinToString(" · "), e.imageUrl)
                SpotifyLibraryEntry.Kind.ARTIST -> Collection(PREFIX + id, "Artist", e.name, "Исполнитель", e.imageUrl)
                else -> null
            }
        }
        pinned = all.filter { it.pinned }.map { it.uri.substringAfterLast(':') }.toSet()
        _library.value = cols
        writeCached(cols)
        Log.i("Spotify", "library: ${cols.size} items")
    }

    /** Library items the account pinned in Spotify itself. */
    @Volatile var pinned: Set<String> = emptySet()
        private set

    /** The account's library album/artist saved or not — kept in step with saving here. */
    fun setSaved(c: Collection, saved: Boolean) {
        if (!linked || !c.id.startsWith(PREFIX)) return
        val bare = c.id.removePrefix(PREFIX)
        ioScope.launch {
            val r = when (c.kind) {
                "Album" -> if (saved) Spotify.saveAlbum(bare) else Spotify.removeAlbum(bare)
                "Artist" -> Spotify.setFollowingArtist(bare, saved)
                else -> return@launch
            }
            r.onFailure { Log.w("Spotify", "couldn't ${if (saved) "save" else "remove"} ${c.id}", it) }
            _library.value = if (saved) listOf(c) + _library.value.filterNot { it.id == c.id } else _library.value.filterNot { it.id == c.id }
            writeCached(_library.value)
        }
    }

    /** Endless continuation of a catalog track: top tracks of artists close to its artist. */
    suspend fun radio(seed: Track): List<Track> = coroutineScope {
        val artistId = seed.artists.firstOrNull()?.id?.takeIf { it.startsWith(PREFIX) } ?: return@coroutineScope emptyList()
        val related = relatedArtists(artistId).filter { it.id.isNotBlank() }.shuffled().take(8)
        val lists = (listOf(async { topTracks(artistId).shuffled().take(3) }) + related.map { a -> async { topTracks(PREFIX + a.id).shuffled().take(3) } }).awaitAll()
        interleave(lists).filter { it.id != seed.id }
    }

    fun <T> interleave(lists: List<List<T>>): List<T> {
        val queues = lists.map { ArrayDeque(it) }
        val out = ArrayList<T>()
        while (queues.any { it.isNotEmpty() }) queues.forEach { q -> q.removeFirstOrNull()?.let(out::add) }
        return out
    }

    private val cacheFile get() = File(Paths.root, "spotify_library.json")

    private fun readCached(): List<Collection> = runCatching {
        cacheFile.takeIf { it.exists() }?.readText()?.let { appJson.decodeFromString(ListSerializer(Collection.serializer()), it) }
    }.getOrNull().orEmpty()

    private fun writeCached(list: List<Collection>) {
        runCatching { cacheFile.writeText(appJson.encodeToString(ListSerializer(Collection.serializer()), list)) }
    }
}
