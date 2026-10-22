package com.drxwnify.desktop.data

import com.drxwnify.desktop.friends.Friend
import com.drxwnify.desktop.friends.Friends
import com.drxwnify.desktop.model.AlbumRef
import com.drxwnify.desktop.model.ArtistRef
import com.drxwnify.desktop.model.Collection
import com.drxwnify.desktop.model.Track
import org.json.JSONArray
import org.w3c.dom.Element
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The phone's own backup (Настройки → Резервная копия → .backup): a zip of its DataStore settings
 * (protobuf), its Room database song.db, its shared_prefs XML files and playlist covers. Everything
 * that makes sense on the PC is moved over: library, history, the audio matches, accounts, friends.
 */
object PhoneBackup {
    data class Result(
        val liked: Int,
        val playlists: Int,
        val history: Int,
        val albums: Int,
        val artists: Int,
        val matches: Int,
        val friends: Int?,
        val accounts: List<String>,
    )

    /** True for a phone backup (it carries song.db), false for anything else. */
    fun isPhoneBackup(file: File): Boolean = runCatching { ZipFile(file).use { it.getEntry("song.db") != null } }.getOrDefault(false)

    fun import(file: File): Result = ZipFile(file).use { zip ->
        val prefs = zip.getEntry("settings.preferences_pb")?.let { e -> zip.getInputStream(e).use { readPreferences(it.readBytes()) } }.orEmpty()
        fun xml(name: String) = zip.getEntry("shared_prefs/$name.xml")?.let { e -> zip.getInputStream(e).use { readSharedPrefs(it.readBytes()) } }.orEmpty()

        val db = File.createTempFile("drxw-phone", ".db", Paths.root)
        try {
            zip.getInputStream(zip.getEntry("song.db") ?: error("в копии нет song.db")).use { input -> db.outputStream().use { input.copyTo(it) } }
            Class.forName("org.sqlite.JDBC")
            val lib = DriverManager.getConnection("jdbc:sqlite:" + db.absolutePath.replace('\\', '/')).use { c ->
                readLibrary(c, zip, xml("library_pins"), xml("library_recents"))
            }
            Library.importPhone(lib.data)
            Matches.putAll(lib.matches)
            val accounts = importAccounts(prefs)
            importSettings(prefs)
            (prefs["spotify_gql_hashes_json"] as? String)?.takeIf { it.isNotBlank() }?.let { File(Paths.root, "spotify-hashes.json").writeText(it) }
            val friends = importFriends(xml("friends"))
            Log.i("PhoneBackup", "imported: ${lib.data.liked.size} liked, ${lib.data.playlists.size} playlists, ${lib.data.history.size} plays, ${lib.matches.size} matches, accounts=$accounts, friends=$friends")
            Result(
                liked = lib.data.liked.size, playlists = lib.data.playlists.size, history = lib.data.history.size,
                albums = lib.data.savedAlbums.size, artists = lib.data.followedArtists.size, matches = lib.matches.size,
                friends = friends, accounts = accounts,
            )
        } finally {
            db.delete()
        }
    }

    // ── song.db ──

    private class Imported(val data: LibraryData, val matches: Map<String, Match>)

    private fun readLibrary(c: Connection, zip: ZipFile, pins: Map<String, Any>, recents: Map<String, Any>): Imported {
        fun <T> rows(sql: String, vararg args: Any?, map: (java.sql.ResultSet) -> T): List<T> = c.prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setObject(i + 1, a) }
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(map(rs)) } }
        }
        fun artistId(id: String, channel: String?, spotify: String?) = when {
            !spotify.isNullOrBlank() -> "spotify:$spotify"
            id.startsWith("UC") -> id
            channel?.startsWith("UC") == true -> channel
            else -> null
        }

        val songArtists = HashMap<String, MutableList<ArtistRef>>()
        rows("SELECT m.songId, a.id, a.name, a.channelId, a.spotifyId FROM song_artist_map m JOIN artist a ON a.id = m.artistId ORDER BY m.songId, m.position") { r ->
            songArtists.getOrPut(r.getString(1)) { mutableListOf() } += ArtistRef(r.getString(3), artistId(r.getString(2), r.getString(4), r.getString(5)))
        }
        val albumTitles = rows("SELECT id, title FROM album") { it.getString(1) to it.getString(2) }.toMap()
        val songs = HashMap<String, Track>()
        rows("SELECT id, title, duration, thumbnailUrl, albumId, albumName, explicit FROM song") { r ->
            val id = r.getString(1)
            val albumId = r.getString(5)
            val albumName = r.getString(6)?.takeIf { it.isNotBlank() } ?: albumId?.let { albumTitles[it] }
            Track(
                id = id,
                title = r.getString(2),
                artists = songArtists[id].orEmpty(),
                album = if (albumId != null || albumName != null) AlbumRef(albumName.orEmpty(), albumId) else null,
                durationSec = r.getInt(3).takeIf { it > 0 },
                cover = r.getString(4)?.takeIf { it.startsWith("http") },
                explicit = r.getInt(7) != 0,
            )
        }.forEach { songs[it.id] = it }

        val likedDates = rows("SELECT id, likedDate FROM song WHERE liked = 1 ORDER BY likedDate DESC") { it.getString(1) to it.getLong(2) }
        val liked = likedDates.mapNotNull { songs[it.first] }

        val coversDir = File(Paths.root, "playlist-covers").apply { mkdirs() }
        val playlists = rows("SELECT id, name, thumbnailUrl, createdAt FROM playlist ORDER BY createdAt DESC") { r ->
            Triple(r.getString(1), r.getString(2), r.getString(3)) to r.getLong(4)
        }.map { (p, created) ->
            val (id, name, thumb) = p
            val tracks = rows("SELECT songId FROM playlist_song_map WHERE playlistId = ? ORDER BY position", id) { it.getString(1) }.mapNotNull { songs[it] }
            val cover = when {
                thumb == null -> null
                thumb.startsWith("http") -> thumb
                else -> zip.getEntry("covers/" + thumb.substringAfterLast('/'))?.let { e ->
                    val out = File(coversDir, e.name.substringAfterLast('/'))
                    zip.getInputStream(e).use { input -> out.outputStream().use { input.copyTo(it) } }
                    out.toURI().toString()
                }
            }
            Playlist(id = "ph-$id", name = name, tracks = tracks, cover = cover, created = created.takeIf { it > 0 } ?: System.currentTimeMillis())
        }

        val history = rows("SELECT songId, timestamp FROM event ORDER BY timestamp DESC LIMIT 2000") { it.getString(1) to it.getLong(2) }
            .mapNotNull { (id, at) -> songs[id]?.let { Played(it, at) } }

        val albumArtists = HashMap<String, MutableList<String>>()
        rows("SELECT m.albumId, a.name FROM album_artist_map m JOIN artist a ON a.id = m.artistId ORDER BY m.albumId, m.`order`") { r ->
            albumArtists.getOrPut(r.getString(1)) { mutableListOf() } += r.getString(2)
        }
        val albums = rows("SELECT id, title, year, thumbnailUrl FROM album WHERE bookmarkedAt IS NOT NULL OR inLibrary IS NOT NULL ORDER BY COALESCE(bookmarkedAt, inLibrary) DESC") { r ->
            val year = r.getInt(3).takeIf { it > 0 }
            Collection(
                id = r.getString(1), kind = "Album", title = r.getString(2),
                subtitle = listOfNotNull(albumArtists[r.getString(1)]?.joinToString(", "), year?.toString()).joinToString(" · "),
                cover = r.getString(4), year = year,
            )
        }
        val artists = rows("SELECT id, name, thumbnailUrl, channelId, spotifyId FROM artist WHERE bookmarkedAt IS NOT NULL ORDER BY bookmarkedAt DESC") { r ->
            artistId(r.getString(1), r.getString(4), r.getString(5))?.let { Collection(id = it, kind = "Artist", title = r.getString(2), subtitle = "Исполнитель", cover = r.getString(3)) }
        }.filterNotNull().distinctBy { it.title.lowercase() }

        // Matches only for the tracks this library holds — the phone's tables also cache every search.
        val keys = songs.keys.map { Matches.keyOf(it) }.toSet()
        val matches = HashMap<String, Match>()
        rows("SELECT catalogId, provider, providerTrackId FROM audio_fallback_match WHERE selected = 1 AND provider IN ('VK', 'YOUTUBE')") { r ->
            Triple(r.getString(1), r.getString(2), r.getString(3))
        }.forEach { (cat, provider, pid) ->
            if (cat !in keys) return@forEach
            val m = matches[cat] ?: Match()
            matches[cat] = if (provider == "VK") m.copy(vk = pid) else m.copy(yt = pid)
        }
        rows("SELECT spotifyId, youtubeId FROM spotify_match") { it.getString(1) to it.getString(2) }.forEach { (sid, yt) ->
            if (sid !in keys || yt.isNullOrBlank()) return@forEach
            val m = matches[sid] ?: Match()
            if (m.yt == null) matches[sid] = m.copy(yt = yt)
        }

        val pinned = (pins["pinned"] as? String)?.let { s -> runCatching { JSONArray(s).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrNull() }
            .orEmpty().mapNotNull(::libraryKey)
        val recent = recents.entries.mapNotNull { (k, v) -> libraryKey(k)?.let { it to ((v as? Long) ?: 0L) } }
            .sortedByDescending { it.second }.map { it.first }

        return Imported(
            LibraryData(
                liked = liked, playlists = playlists, savedAlbums = albums, followedArtists = artists,
                history = history, recentKeys = recent, pinnedKeys = pinned,
                likedAt = likedDates.filter { it.second > 0 }.toMap(),
            ),
            matches,
        )
    }

    /** The phone's library keys ("meld:playlist:X", "spotify:album:X", "album:X") in this app's form. */
    private fun libraryKey(phone: String): String? {
        val parts = phone.split(':')
        return when {
            phone.startsWith("meld:playlist:") -> "playlist:ph-" + phone.removePrefix("meld:playlist:")
            parts.size == 3 && parts[0] == "spotify" -> kindKey(parts[1], parts[2])
            parts.size == 2 -> kindKey(parts[0], parts[1])
            else -> null
        }
    }

    private val spotifyId = Regex("^[A-Za-z0-9]{22}$")

    private fun kindKey(kind: String, id: String): String? {
        if (kind == "playlist" && id.startsWith("LP")) return "playlist:ph-$id"
        val ref = if (spotifyId.matches(id)) "spotify:$id" else id
        return when (kind) {
            "album" -> "альбом:$ref"
            "playlist" -> "плейлист:$ref"
            "artist" -> "исполнитель:$ref"
            "collection" -> "liked"
            else -> null
        }
    }

    // ── accounts & settings ──

    private fun importAccounts(p: Map<String, Any>): List<String> {
        fun s(k: String) = (p[k] as? String)?.takeIf { it.isNotBlank() }
        val found = mutableListOf<String>()
        Prefs.accounts.update { a ->
            var n = a
            s("spotifySpDc")?.let { dc ->
                found += "Spotify"
                n = n.copy(
                    spotifySpDc = dc, spotifySpKey = s("spotifySpKey"), spotifyAccessToken = s("spotifyAccessToken"),
                    spotifyTokenExpiry = (p["spotifyTokenExpiry"] as? Long) ?: 0L,
                    spotifyName = s("spotifyUsername") ?: n.spotifyName, spotifyUserId = s("spotifyUserId") ?: n.spotifyUserId,
                )
            }
            s("vkAccessToken")?.let { found += "VK"; n = n.copy(vkToken = it, vkUserId = s("vkUserId")) }
            s("yandexAccessToken")?.let { found += "Яндекс Музыка"; n = n.copy(yandexToken = it, yandexUid = s("yandexUid"), yandexName = s("yandexUsername")) }
            s("lastfmSession")?.let {
                found += "Last.fm"
                n = n.copy(lastfmSession = it, lastfmUser = s("lastfmUsername"), lastfmScrobble = (p["lastfmScrobblingEnable"] as? Boolean) ?: n.lastfmScrobble)
            }
            s("discordToken")?.let { found += "Discord"; n = n.copy(discordToken = it) }
            s("soulseekUsername")?.let { u -> s("soulseekPassword")?.let { pw -> found += "Soulseek"; n = n.copy(soulseekUser = u, soulseekPassword = pw) } }
            n
        }
        return found
    }

    private fun importSettings(p: Map<String, Any>) = Prefs.settings.update { s ->
        var n = s
        (p["audioNormalization"] as? Boolean)?.let { n = n.copy(normalize = it) }
        (p["contentCountry"] as? String)?.takeIf { it.length == 2 }?.let { n = n.copy(contentRegion = it.uppercase()) }
        (p["primaryMetadataSource"] as? String)?.lowercase()?.takeIf { it in listOf("spotify", "yandex", "youtube") }?.let { n = n.copy(catalog = it) }
        (p["spotifySyncLikes"] as? Boolean)?.let { n = n.copy(spotifySyncLikes = it) }
        (p["stereoBalance"] as? Float)?.takeIf { it in -1f..1f }?.let { n = n.copy(balance = it) }
        n
    }

    // ── friends ──

    private fun importFriends(x: Map<String, Any>): Int? {
        val sk = (x["sk"] as? String)?.takeIf { it.length == 64 } ?: return null
        val list = (x["friends"] as? String)?.let { s ->
            runCatching {
                JSONArray(s).let { a ->
                    (0 until a.length()).map { i -> a.getJSONObject(i).let { o -> Friend(o.getString("p"), o.optString("n"), o.optLong("t", System.currentTimeMillis())) } }
                }
            }.getOrNull()
        }.orEmpty()
        @Suppress("UNCHECKED_CAST")
        val dismissed = (x["dismissed"] as? Set<String>)?.toList().orEmpty()
        val ok = Friends.importFromPhone(sk, (x["name"] as? String).orEmpty(), list, dismissed, (x["devAdded"] as? Boolean) ?: false, (x["iAmVerified"] as? Boolean) ?: false)
        return if (ok) list.size else null
    }

    // ── formats ──

    /** Android shared_prefs XML: <string>, <long>, <int>, <boolean>, <float>, <set>. */
    private fun readSharedPrefs(bytes: ByteArray): Map<String, Any> = runCatching {
        val f = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isExpandEntityReferences = false
        }
        val root = f.newDocumentBuilder().parse(bytes.inputStream()).documentElement
        val out = HashMap<String, Any>()
        val nodes = root.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            val name = e.getAttribute("name").takeIf { it.isNotEmpty() } ?: continue
            val v = e.getAttribute("value")
            out[name] = when (e.tagName) {
                "string" -> e.textContent
                "long" -> v.toLongOrNull() ?: continue
                "int" -> v.toIntOrNull() ?: continue
                "boolean" -> v == "true"
                "float" -> v.toFloatOrNull() ?: continue
                "set" -> e.getElementsByTagName("string").let { l -> (0 until l.length).map { l.item(it).textContent }.toSet() }
                else -> continue
            }
        }
        out
    }.onFailure { Log.w("PhoneBackup", "unreadable shared prefs", it) }.getOrDefault(emptyMap())

    /** Jetpack DataStore Preferences (protobuf PreferenceMap: map<string, Value>). */
    fun readPreferences(bytes: ByteArray): Map<String, Any> {
        val out = HashMap<String, Any>()
        for ((field, _, entry) in pbFields(bytes)) {
            if (field != 1 || entry !is ByteArray) continue
            var key: String? = null
            var value: Any? = null
            for ((f, _, v) in pbFields(entry)) {
                if (f == 1 && v is ByteArray) key = String(v, Charsets.UTF_8)
                if (f == 2 && v is ByteArray) value = pbValue(v)
            }
            if (key != null && value != null) out[key] = value
        }
        return out
    }

    private fun pbValue(bytes: ByteArray): Any? {
        for ((f, _, v) in pbFields(bytes)) {
            return when (f) {
                1 -> (v as Long) != 0L
                2 -> Float.fromBits((v as Long).toInt())
                3 -> (v as Long).toInt()
                4 -> v as Long
                5 -> String(v as ByteArray, Charsets.UTF_8)
                6 -> pbFields(v as ByteArray).mapNotNull { (sf, _, sv) -> if (sf == 1 && sv is ByteArray) String(sv, Charsets.UTF_8) else null }.toSet()
                7 -> Double.fromBits(v as Long)
                8 -> v as ByteArray
                else -> null
            }
        }
        return null
    }

    /** (field number, wire type, Long for varint/fixed or ByteArray for length-delimited). */
    private fun pbFields(b: ByteArray): List<Triple<Int, Int, Any>> {
        val out = ArrayList<Triple<Int, Int, Any>>()
        var i = 0
        fun varint(): Long {
            var r = 0L
            var shift = 0
            while (true) {
                val c = b[i++].toInt() and 0xff
                r = r or ((c and 0x7f).toLong() shl shift)
                if (c < 0x80) return r
                shift += 7
            }
        }
        fun fixed(n: Int): Long {
            var r = 0L
            for (k in 0 until n) r = r or ((b[i + k].toLong() and 0xff) shl (8 * k))
            i += n
            return r
        }
        while (i < b.size) {
            val tag = varint()
            val field = (tag ushr 3).toInt()
            val value: Any = when (val type = (tag and 7).toInt()) {
                0 -> varint()
                1 -> fixed(8)
                5 -> fixed(4)
                2 -> { val len = varint().toInt(); b.copyOfRange(i, i + len).also { i += len } }
                else -> error("protobuf wire type $type")
            }
            out += Triple(field, (tag and 7).toInt(), value)
        }
        return out
    }
}
