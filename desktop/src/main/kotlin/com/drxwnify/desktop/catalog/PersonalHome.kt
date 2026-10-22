package com.drxwnify.desktop.catalog

import com.drxwnify.desktop.catalog.SpotifyCatalog.toCollection
import com.drxwnify.desktop.catalog.SpotifyCatalog.toTrack
import com.drxwnify.desktop.data.Library
import com.drxwnify.desktop.data.Log
import com.drxwnify.desktop.friends.Friends
import com.drxwnify.desktop.model.Collection
import com.drxwnify.desktop.model.HomeRow
import com.drxwnify.desktop.model.Track
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.models.SpotifyAlbum
import com.metrolist.spotify.models.SpotifyHomeFeedItem
import com.metrolist.spotify.models.SpotifyImage
import com.metrolist.spotify.models.SpotifyPlaylist
import com.metrolist.spotify.models.SpotifyPlaylistOwner
import com.metrolist.spotify.models.SpotifyPlaylistTracksRef
import com.metrolist.spotify.models.SpotifySimpleAlbum
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.random.Random

/**
 * The phone's home, rebuilt from the same signals: what is liked, followed and played (the phone's
 * history comes over with its backup) and the Spotify account. Rows in the phone's order:
 * Рекомендации → Новинки твоих исполнителей → Похоже на … → Spotify's own rows → Альбомы, до которых
 * не дошли руки → Глубже в любимых → Что слушают друзья → Вспомни любимое → Твои миксы.
 * Each row is handed over as soon as it is ready ([publish]), the rest keep coming.
 */
object PersonalHome {
    private const val FORGOTTEN_AFTER_DAYS = 60L
    private const val MIN_LOVE = 1.0
    private const val FOR_YOU = 35

    private data class Favorite(val name: String, val id: String?, val score: Double)

    private class Taste(
        val favorites: List<Favorite>,
        val known: Set<String>,
        val recentlyHeard: Set<String>,
        val albumTracks: Map<String, Set<String>>,
        val cyrillicShare: Double,
    )

    /** Slots in the page's order; a row fills its slot when it is ready. */
    private enum class Slot { ForYou, NewReleases, Similar, Spotify, Unheard, DeepCuts, Friends, Forgotten, Mixes }

    suspend fun build(publish: (List<HomeRow>) -> Unit, force: Boolean): Boolean = coroutineScope {
        val slots = ConcurrentHashMap<Slot, List<HomeRow>>()
        fun put(slot: Slot, rows: List<HomeRow>) {
            slots[slot] = rows.filter { it.tracks.isNotEmpty() || it.collections.isNotEmpty() }
            publish(Slot.entries.flatMap { slots[it].orEmpty() })
        }
        val random = Random(if (force) Random.nextLong() else LocalDate.now().toEpochDay())
        val gate = Semaphore(6)
        val memo = Memo(gate, this)

        val taste = taste()
        val favorites = resolveIds(taste.favorites, gate)
        Log.i("Home", "favourites: ${favorites.take(12).joinToString { it.name }}")

        val spotifyHome = async { section("spotify home") { Spotify.home(sectionItemsLimit = 20, language = "ru").getOrThrow() } }
        val jobs = listOf(
            launch { section("for you") { put(Slot.ForYou, listOf(HomeRow("Рекомендации", "без названий — нажми наугад", tracks = forYou(taste, favorites, spotifyHome, memo, random)))) } },
            launch {
                if (!SpotifyCatalog.linked) return@launch
                section("new releases") {
                    val albums = Spotify.newReleases(limit = 50).recoverCatching { Spotify.newReleases(limit = 20).getOrThrow() }.getOrThrow().albums?.items.orEmpty()
                    put(Slot.NewReleases, listOf(HomeRow("Новинки твоих исполнителей", collections = albums.filter { it.id.isNotBlank() }.map { it.toCollection() })))
                }
            },
            launch { section("similar") { put(Slot.Similar, similar(favorites, taste, memo, random)) } },
            launch {
                section("spotify rows") {
                    val feed = spotifyHome.await() ?: return@section
                    val names = favorites.map { it.name.lowercase() }
                    val mixes = LinkedHashMap<String, Collection>()
                    val rows = feed.sections.mapNotNull { s ->
                        val spotifyMade = s.items.filterIsInstance<SpotifyHomeFeedItem.Playlist>().filter { it.ownerName.equals("Spotify", true) }
                        spotifyMade.filter { p -> isOwnMix(p.name, names) }.forEach { mixes.putIfAbsent(it.id, it.toPlaylist().toCollection()) }
                        if (isRemovedSection(s.title)) return@mapNotNull null
                        val title = s.title!!.trim()
                        val items = s.items.filterNot { it is SpotifyHomeFeedItem.Playlist && it.ownerName.equals("Spotify", true) }.mapNotNull { it.toCollection() }
                        if (items.size >= 3) HomeRow(title, collections = items) else null
                    }
                    put(Slot.Spotify, rows)
                    if (mixes.isNotEmpty()) put(Slot.Mixes, listOf(HomeRow("Твои миксы", "собраны Spotify по твоей истории", collections = mixes.values.toList())))
                }
            },
            launch { section("unheard") { put(Slot.Unheard, listOf(HomeRow("Альбомы, до которых не дошли руки", "Любимые исполнители — не слушал или бросил после пары треков", collections = unheardAlbums(favorites, taste, memo, random)))) } },
            launch { section("deep cuts") { put(Slot.DeepCuts, listOf(HomeRow("Глубже в любимых", "Треки из альбомов, до которых ты не дошёл", tracks = deepCuts(favorites, taste, memo, random)))) } },
            launch { section("friends") { put(Slot.Friends, listOf(HomeRow("Что слушают друзья", tracks = friendsListening()))) } },
            launch { section("forgotten") { put(Slot.Forgotten, listOf(HomeRow("Вспомни любимое", "Лайкнул давно и с тех пор не слушал", tracks = forgotten(taste, random)))) } },
        )
        jobs.forEach { it.join() }
        slots.values.any { it.isNotEmpty() }
    }

    private suspend fun <T> section(name: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Log.w("Home", "'$name' failed", e)
        null
    }

    // ── taste ──

    private fun taste(): Taste {
        val lib = Library.value
        val love = HashMap<String, Double>()
        val names = HashMap<String, String>()
        val ids = HashMap<String, String>()
        val known = HashSet<String>()
        val recent = HashSet<String>()
        val albumTracks = HashMap<String, MutableSet<String>>()
        var cyrillic = 0
        var total = 0
        fun note(name: String, id: String?): String? {
            val n = name.trim().takeIf { it.isNotBlank() } ?: return null
            val key = n.lowercase()
            names.putIfAbsent(key, n)
            id?.takeIf { it.startsWith(SpotifyCatalog.PREFIX) }?.let { ids.putIfAbsent(key, it) }
            return key
        }
        fun sample(t: Track) {
            total++
            if (hasCyrillic(t.title) || hasCyrillic(t.artistLine)) cyrillic++
        }

        val now = System.currentTimeMillis()
        val plays = HashMap<String, Double>()
        val perTrack = HashMap<String, Int>()
        val artistOfTrack = HashMap<String, String>()
        lib.history.forEach { p ->
            val artist = p.track.artists.firstOrNull() ?: return@forEach
            val key = note(artist.name, artist.id) ?: return@forEach
            val ageDays = (now - p.at) / 86_400_000.0
            val tk = trackKey(artist.name, p.track.title)
            known += tk
            sample(p.track)
            if (ageDays <= FORGOTTEN_AFTER_DAYS) recent += tk
            p.track.album?.title?.takeIf { it.isNotBlank() }?.let { albumTracks.getOrPut(albumKey(artist.name, it)) { HashSet() } += tk }
            if (ageDays > 240) return@forEach
            plays.merge(key, exp(-ageDays / 90.0), Double::plus)
            perTrack.merge(tk, 1, Int::plus)
            artistOfTrack[tk] = key
        }
        val repeats = HashMap<String, Double>()
        perTrack.forEach { (tk, n) -> if (n >= 3) artistOfTrack[tk]?.let { repeats.merge(it, 0.6, Double::plus) } }
        repeats.forEach { (k, r) -> love.merge(k, minOf(1.2, r), Double::plus) }
        plays.forEach { (k, p) -> love.merge(k, minOf(0.5, p * 0.02), Double::plus) }

        lib.liked.forEach { t ->
            val artist = t.artists.firstOrNull() ?: return@forEach
            val key = note(artist.name, artist.id) ?: return@forEach
            known += trackKey(artist.name, t.title)
            sample(t)
            love.merge(key, 1.0, Double::plus)
        }
        (lib.followedArtists + SpotifyCatalog.library.value.filter { it.kind == "Artist" }).forEach { a ->
            val key = note(a.title, a.id) ?: return@forEach
            love.merge(key, 4.0, Double::plus)
        }
        val favorites = love.entries.filter { it.value >= MIN_LOVE }.sortedByDescending { it.value }.take(40)
            .map { (k, s) -> Favorite(names.getValue(k), ids[k], s) }
        return Taste(favorites, known, recent, albumTracks, if (total == 0) 1.0 else cyrillic.toDouble() / total)
    }

    /** Spotify ids for the strongest favourites known only by name (YouTube or local tracks). */
    private suspend fun resolveIds(favorites: List<Favorite>, gate: Semaphore): List<Favorite> = coroutineScope {
        favorites.mapIndexed { i, f ->
            async {
                if (f.id != null || i >= 20) return@async f
                val hit = gate.withPermit { Spotify.search(f.name, listOf("artist"), limit = 3).getOrNull() }
                    ?.artists?.items?.firstOrNull { it.name.equals(f.name, ignoreCase = true) && it.id.isNotBlank() }
                f.copy(id = hit?.let { SpotifyCatalog.PREFIX + it.id })
            }
        }.awaitAll()
    }

    private fun pick(favorites: List<Favorite>, count: Int, pool: Int, random: Random): List<Favorite> {
        val left = favorites.filter { it.id != null }.take(pool).toMutableList()
        val out = ArrayList<Favorite>()
        while (out.size < count && left.isNotEmpty()) {
            var r = random.nextDouble(left.sumOf { it.score })
            val i = left.indexOfFirst { r -= it.score; r <= 0 }.let { if (it < 0) left.lastIndex else it }
            out += left.removeAt(i)
        }
        return out
    }

    /** One run's catalog answers, shared by the rows (an artist is often asked for by several). */
    private class Memo(val gate: Semaphore, private val scope: kotlinx.coroutines.CoroutineScope) {
        private val top = ConcurrentHashMap<String, Deferred<List<Track>>>()
        private val related = ConcurrentHashMap<String, Deferred<List<com.metrolist.spotify.models.SpotifyArtist>>>()
        private val disco = ConcurrentHashMap<String, Deferred<List<SpotifyAlbum>>>()

        suspend fun topTracks(id: String) = top.getOrPut(id) { scope.async { gate.withPermit { SpotifyCatalog.topTracks(id) } } }.await()

        suspend fun related(id: String) = related.getOrPut(id) { scope.async { gate.withPermit { SpotifyCatalog.relatedArtists(id) } } }.await()

        suspend fun releases(id: String) = disco.getOrPut(id) {
            scope.async {
                gate.withPermit {
                    Spotify.artistDiscography(id.removePrefix(SpotifyCatalog.PREFIX)).getOrNull()
                        ?.let { d -> (listOfNotNull(d.latest) + d.albums + d.singles).distinctBy { it.id } }.orEmpty()
                }
            }
        }.await()
    }

    // ── rows ──

    private suspend fun forYou(taste: Taste, favorites: List<Favorite>, home: Deferred<com.metrolist.spotify.models.SpotifyHomeFeed?>, memo: Memo, random: Random): List<Track> = coroutineScope {
        val mixes = if (SpotifyCatalog.linked) {
            home.await()?.sections.orEmpty().flatMap { it.items }.filterIsInstance<SpotifyHomeFeedItem.Playlist>()
                .filter { p -> p.madeForUsername != null || MIX_NAME.containsMatchIn(p.name) }
                .distinctBy { it.id }
                .sortedBy { p -> MIX_PRIORITY.indexOfFirst { it.containsMatchIn(p.name) }.let { if (it < 0) 99 else it } }
                .take(6)
                .map { p -> async { memo.gate.withPermit { Spotify.playlistTracks(p.id, limit = 50).getOrNull() }?.items.orEmpty().mapNotNull { it.track }.filter { it.id.isNotBlank() && !it.isLocal }.map { it.toTrack() }.shuffled(random) } }
                .awaitAll().let { SpotifyCatalog.interleave(it) }
        } else emptyList()
        val seeds = pick(favorites, 8, pool = 20, random)
        val grown = seeds.map { seed ->
            async {
                memo.related(seed.id!!).filter { it.id.isNotBlank() }.take(20).shuffled(random).take(4)
                    .flatMap { a -> memo.topTracks(SpotifyCatalog.PREFIX + a.id).shuffled(random).take(3) }
            }
        }.awaitAll().let { SpotifyCatalog.interleave(it) }
        diversify(filter(mixes + grown, taste)).take(FOR_YOU)
    }

    private suspend fun similar(favorites: List<Favorite>, taste: Taste, memo: Memo, random: Random): List<HomeRow> = coroutineScope {
        pick(favorites, 2, pool = 12, random).map { seed ->
            async {
                val related = memo.related(seed.id!!).filter { it.id.isNotBlank() && !it.name.equals(seed.name, true) }
                    .take(10).shuffled(random).take(7)
                HomeRow("Похоже на ${seed.name}", tracks = tracksOf(related.map { SpotifyCatalog.PREFIX + it.id }, taste, memo, random))
            }
        }.awaitAll().filter { it.tracks.size >= 4 }
    }

    private suspend fun tracksOf(ids: List<String>, taste: Taste, memo: Memo, random: Random): List<Track> = coroutineScope {
        ids.map { id ->
            async { memo.topTracks(id).filter { trackKey(it.artists.firstOrNull()?.name.orEmpty(), it.title) !in taste.known }.take(5).shuffled(random).take(2) }
        }.awaitAll().let { SpotifyCatalog.interleave(it) }.take(14)
    }

    private suspend fun unheardAlbums(favorites: List<Favorite>, taste: Taste, memo: Memo, random: Random): List<Collection> = coroutineScope {
        pick(favorites, 10, pool = 20, random).map { f ->
            async {
                memo.releases(f.id!!).filter { a ->
                    val full = a.albumType.equals("album", true) || a.albumType.equals("ep", true) || a.totalTracks >= 4
                    full && (taste.albumTracks[albumKey(f.name, a.name)]?.size ?: 0) < 3
                }.shuffled(random).take(2).map { a -> a.copy(artists = a.artists.ifEmpty { listOf(com.metrolist.spotify.models.SpotifySimpleArtist(name = f.name)) }).toCollection() }
            }
        }.awaitAll().let { SpotifyCatalog.interleave(it) }.distinctBy { it.id }.take(16)
    }

    private suspend fun deepCuts(favorites: List<Favorite>, taste: Taste, memo: Memo, random: Random): List<Track> = coroutineScope {
        pick(favorites, 5, pool = 15, random).map { seed ->
            async {
                val hits = memo.topTracks(seed.id!!).mapTo(HashSet()) { it.id }
                memo.releases(seed.id).filter { it.totalTracks >= 4 || it.albumType.equals("album", true) }.shuffled(random).take(2).flatMap { release ->
                    val album = memo.gate.withPermit { Spotify.album(release.id).getOrNull() } ?: return@flatMap emptyList()
                    val cover = SpotifySimpleAlbum(id = album.id, name = album.name, images = album.images.ifEmpty { release.images }, releaseDate = album.releaseDate, albumType = album.albumType, artists = album.artists)
                    album.tracks?.items.orEmpty().filter { it.id.isNotBlank() && !it.isLocal }.map { it.toTrack(cover) }
                        .filter { it.id !in hits && trackKey(it.artists.firstOrNull()?.name ?: seed.name, it.title) !in taste.known }
                        .shuffled(random).take(2)
                }
            }
        }.awaitAll().let { SpotifyCatalog.interleave(it) }.take(14)
    }

    private fun friendsListening(): List<Track> {
        val data = Friends.data.value
        val mine = Friends.friends.value.map { it.pubkey }.toSet()
        val live = data.filterKeys { it in mine }.values.mapNotNull { d -> d.now?.takeIf { it.live }?.track?.toTrack() }
        val lately = data.filterKeys { it in mine }.values.flatMap { d -> d.history.orEmpty().take(4).map { it.toTrack() } }
        return (live + lately).distinctBy { it.id }.take(16)
    }

    private fun forgotten(taste: Taste, random: Random): List<Track> {
        val lib = Library.value
        val longAgo = System.currentTimeMillis() - FORGOTTEN_AFTER_DAYS * 86_400_000L
        return lib.liked.filter { t -> (lib.likedAt[t.id] ?: 0L) in 1 until longAgo && trackKey(t.artists.firstOrNull()?.name.orEmpty(), t.title) !in taste.recentlyHeard }
            .shuffled(random).distinctBy { it.artists.firstOrNull()?.name?.lowercase() }.take(16)
    }

    // ── filtering ──

    private fun filter(tracks: List<Track>, taste: Taste): List<Track> = tracks.filter { t ->
        trackKey(t.artists.firstOrNull()?.name.orEmpty(), t.title) !in taste.known &&
            (taste.cyrillicShare >= 0.15 || !(hasCyrillic(t.title) || hasCyrillic(t.artistLine)))
    }.distinctBy { it.id }

    private fun diversify(tracks: List<Track>): List<Track> {
        val per = HashMap<String, Int>()
        val kept = tracks.filter { t ->
            val k = t.artists.firstOrNull()?.name?.lowercase().orEmpty()
            val n = per[k] ?: 0
            if (n >= 2) false else { per[k] = n + 1; true }
        }
        return SpotifyCatalog.interleave(kept.groupBy { it.artists.firstOrNull()?.name?.lowercase().orEmpty() }.values.toList())
    }

    private fun trackKey(artist: String, title: String) =
        artist.lowercase().filter { it.isLetterOrDigit() } + "|" + title.lowercase().substringBefore(" (").substringBefore(" - ").filter { it.isLetterOrDigit() }

    private fun albumKey(artist: String, album: String) =
        artist.lowercase().filter { it.isLetterOrDigit() } + "|" + album.lowercase().substringBefore(" (").filter { it.isLetterOrDigit() }

    private fun hasCyrillic(text: String) = text.any { it in 'Ѐ'..'ӿ' }

    private fun SpotifyHomeFeedItem.Playlist.toPlaylist() = SpotifyPlaylist(
        id = id, name = name, description = description,
        images = imageUrl?.let { listOf(SpotifyImage(url = it)) }.orEmpty(),
        owner = ownerName?.let { SpotifyPlaylistOwner(displayName = it) },
        tracks = SpotifyPlaylistTracksRef(total = totalCount), uri = uri,
    )

    private fun SpotifyHomeFeedItem.toCollection(): Collection? = when (this) {
        is SpotifyHomeFeedItem.Playlist -> toPlaylist().toCollection()
        is SpotifyHomeFeedItem.Album -> Collection(SpotifyCatalog.PREFIX + id, "Album", name, artists.joinToString(", ") { it.name }, imageUrl)
        is SpotifyHomeFeedItem.Artist -> Collection(SpotifyCatalog.PREFIX + id, "Artist", name, "Исполнитель", imageUrl)
    }.takeIf { it.id != SpotifyCatalog.PREFIX }

    // The phone's SpotifyHomeFilter: Spotify's rows that repeat the app's own ones (or history) go.
    private val REMOVED_SECTIONS = listOf(
        "songs you like", "artists you follow", "favou?rite artists", "^more like", "recently played", "jump back in",
        "альбом.*(нрав|любим)", "(нрав|любим).*альбом", "подписан", """любим\S* исполнител""", "похож", "недавно",
        "^(слушать )?снова", "friday", "пятнич",
    ).map { Regex(it) }

    private fun isRemovedSection(title: String?): Boolean {
        val t = title?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return true
        return REMOVED_SECTIONS.any { it.containsMatchIn(t) }
    }

    private val PERSONAL_MIX = Regex(
        "daily mix|discover weekly|release radar|on repeat|repeat rewind|daylist|time capsule|blend|микс дня|открытия недели|радар новинок|на повторе|капсула времени",
        RegexOption.IGNORE_CASE,
    )
    private val SUFFIX_MIX = Regex("""^(.+?)\s+(mix|микс)$""", RegexOption.IGNORE_CASE)
    private val PREFIX_MIX = Regex("""^(mix|микс)\s*[:\-–—]?\s+(.+)$""", RegexOption.IGNORE_CASE)

    /** A personal mix, or one named after one of the listener's artists ("Sewerslvt Mix"); editorial ones go. */
    private fun isOwnMix(name: String, artists: List<String>): Boolean {
        val n = name.trim()
        if (PERSONAL_MIX.containsMatchIn(n)) return true
        val subject = (SUFFIX_MIX.find(n)?.groupValues?.get(1) ?: PREFIX_MIX.find(n)?.groupValues?.get(2))
            ?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return false
        return artists.any { key -> key.isNotEmpty() && (key == subject || (key.length >= 4 && subject.length >= 4 && (subject in key || key in subject))) }
    }

    private val MIX_NAME = Regex("Discover Weekly|Daily Mix|Release Radar|Mix|daylist|Открытия недели|Микс|Радар новинок", RegexOption.IGNORE_CASE)
    private val MIX_PRIORITY = listOf(
        Regex("Discover Weekly|Открытия недели", RegexOption.IGNORE_CASE),
        Regex("Daily Mix|Микс дня", RegexOption.IGNORE_CASE),
        Regex("Release Radar|Радар новинок", RegexOption.IGNORE_CASE),
        Regex("Mix|Микс", RegexOption.IGNORE_CASE),
    )
}
