package com.drxwnify.desktop.friends

import com.drxwnify.desktop.data.Library
import com.drxwnify.desktop.data.Log
import com.drxwnify.desktop.data.Prefs
import com.drxwnify.desktop.data.Store
import com.drxwnify.desktop.data.ioScope
import com.drxwnify.desktop.model.ArtistRef
import com.drxwnify.desktop.model.Track
import com.drxwnify.desktop.playback.EngineState
import com.drxwnify.desktop.playback.Player
import com.metrolist.music.friends.Nostr
import com.metrolist.music.friends.NostrEvent
import com.metrolist.music.friends.RelayPool
import com.metrolist.music.friends.Secp256k1
import com.metrolist.music.friends.hexToBytes
import com.metrolist.music.friends.toHex
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject

data class Me(val pubkey: String, val name: String, val picture: String?, val imported: Boolean) {
    val code: String get() = Nostr.bech32Encode("npub", pubkey.hexToBytes())
}

data class TrackRef(
    val title: String, val artist: String, val catalogId: String?, val mediaId: String?,
    val cover: String?, val durationMs: Long = 0, val at: Long = 0,
) {
    /** A track the desktop player can play: YouTube and catalog ("mfb:") ids directly, anything else by search. */
    fun toTrack() = Track(
        id = mediaId?.takeIf { !it.contains(':') || it.startsWith("mfb:") }
            ?: catalogId?.takeIf { Regex("^[A-Za-z0-9]{22}$").matches(it) }?.let { "mfb:$it" }
            ?: "ref:${catalogId ?: "$artist—$title"}",
        title = title, artists = artist.split(", ").filter { it.isNotBlank() }.map { ArtistRef(it) },
        durationSec = (durationMs / 1000).toInt().takeIf { it > 0 }, cover = cover,
    )
}

data class NowPlay(val track: TrackRef, val positionMs: Long, val playing: Boolean, val at: Long, val listenAlong: Boolean) {
    fun positionNow(): Long = if (playing) positionMs + (System.currentTimeMillis() - at).coerceAtLeast(0) else positionMs
    /** Playing and heard from in the last few minutes. */
    val live: Boolean get() = playing && System.currentTimeMillis() - at < 15 * 60_000
}

data class SharedPlaylist(val name: String, val total: Int, val tracks: List<TrackRef>)
data class SharedLikes(val total: Int, val source: String?, val tracks: List<TrackRef>)

data class FriendData(
    val name: String? = null,
    val avatar: String? = null,
    val now: NowPlay? = null,
    val history: List<TrackRef>? = null,
    val playlists: List<SharedPlaylist>? = null,
    val likes: SharedLikes? = null,
    val stamps: Map<String, Long> = emptyMap(),
)

@Serializable
data class Friend(val pubkey: String, val name: String, val addedAt: Long)

data class Request(val pubkey: String, val name: String, val at: Long)

data class Found(val pubkey: String, val name: String, val avatar: String?)

@Serializable
data class FriendsData(
    val name: String = "",
    /** The key came from the phone: the phone owns profile/likes/history; this PC only says what it plays. */
    val imported: Boolean = false,
    val friends: List<Friend> = emptyList(),
    val dismissed: List<String> = emptyList(),
    val devAdded: Boolean = false,
    /** The developer marked this profile: drxwnslvt's reviews are open (the phone's "iAmVerified"). */
    val verified: Boolean = false,
    val helloSentFor: String? = null,
    val libraryAt: Long = 0,
)

/**
 * Friends over Nostr, compatible with the phone's FriendsHub: same keys, relays, event kinds and
 * fields ("drxw:profile", "drxw:now", …, friends-only data NIP-04-encrypted per friend).
 */
object Friends {
    const val DEVELOPER_PUBKEY = "e114d6d2c422913fbb0c5b525cf927015aaecdfbea72fadd5c1dd2693f1c7f74"
    const val DEVELOPER_NAME = "drxwnslvt"
    const val INVITE_BASE = "https://drxwnify.pages.dev/add/"
    private const val D = "drxw:"
    private val DATA_FIELDS = listOf("now", "history", "playlists", "stats", "likes", "portrait")

    private val store = Store("friends.json", FriendsData.serializer()) { FriendsData() }
    private val _me = MutableStateFlow<Me?>(null)
    val me: StateFlow<Me?> = _me
    val friends = MutableStateFlow<List<Friend>>(emptyList())
    private val _data = MutableStateFlow<Map<String, FriendData>>(emptyMap())
    val data: StateFlow<Map<String, FriendData>> = _data
    private val _requests = MutableStateFlow<List<Request>>(emptyList())
    val requests: StateFlow<List<Request>> = _requests
    private val _listeningTo = MutableStateFlow<String?>(null)
    val listeningTo: StateFlow<String?> = _listeningTo
    private val _online = MutableStateFlow(0)
    val onlineCount: StateFlow<Int> = _online

    @Volatile private var sk: ByteArray? = null
    private val liveSubs = mutableListOf<String>()

    fun init() {
        friends.value = store.value.friends
        Prefs.accounts.value.nostrSecret?.let { hex ->
            runCatching { load(hex.hexToBytes()) }.onFailure { Log.w("Friends", "bad stored key", it) }
        }
        // What plays here goes to friends (debounced), like the phone's playerChanged().
        ioScope.launch {
            combine(Player.now, Player.engine.state) { n, s -> n.track to (s == EngineState.Playing) }
                .distinctUntilChanged()
                .collect { (t, playing) -> if (t != null) nowChanged(t, playing) }
        }
        ioScope.launch {
            while (true) {
                _online.value = _data.value.filterKeys { k -> friends.value.any { it.pubkey == k } }.values.count { it.now?.live == true }
                delay(20_000)
            }
        }
    }

    private fun load(key: ByteArray) {
        sk = key
        val pub = Secp256k1.publicKey(key).toHex()
        _me.value = Me(pub, store.value.name, null, store.value.imported)
        openLive()
        if (!store.value.imported) {
            ensureDeveloperFriend()
            ioScope.launch { delay(4000); publishProfile(); publishLibrary() }
        }
    }

    // ── profile ──

    fun createProfile(name: String) {
        val key = Secp256k1.newPrivateKey()
        store.update { it.copy(name = name.trim(), imported = false) }
        Prefs.accounts.update { it.copy(nostrSecret = key.toHex()) }
        load(key)
    }

    /** The phone's "ключ восстановления" (nsec), or a 64-char hex key. False when it isn't one. */
    fun importKey(text: String): Boolean {
        val t = text.trim()
        val key = when {
            t.startsWith("nsec1") -> Nostr.bech32Decode(t)?.takeIf { it.first == "nsec" && it.second.size == 32 }?.second
            Regex("^[0-9a-fA-F]{64}$").matches(t) -> t.hexToBytes()
            else -> null
        } ?: return false
        val pub = runCatching { Secp256k1.publicKey(key).toHex() }.getOrNull() ?: return false
        closeLive()
        _data.value = emptyMap()
        store.update { it.copy(name = if (pub == DEVELOPER_PUBKEY) DEVELOPER_NAME else "", imported = true) }
        Prefs.accounts.update { it.copy(nostrSecret = key.toHex()) }
        load(key)
        return true
    }

    /**
     * The phone's whole friends state from its backup: its key (so this PC is the same person), its
     * name and friend list. Like a pasted key it counts as imported — the phone keeps owning the profile.
     */
    fun importFromPhone(skHex: String, name: String, phoneFriends: List<Friend>, dismissed: List<String>, devAdded: Boolean, verified: Boolean = false): Boolean {
        val key = runCatching { skHex.trim().hexToBytes() }.getOrNull()?.takeIf { it.size == 32 } ?: return false
        val pub = runCatching { Secp256k1.publicKey(key).toHex() }.getOrNull() ?: return false
        val sameIdentity = _me.value?.pubkey == pub
        val list = if (sameIdentity) phoneFriends + friends.value.filter { f -> phoneFriends.none { it.pubkey == f.pubkey } } else phoneFriends
        closeLive()
        _data.value = emptyMap()
        friends.value = list
        store.update {
            it.copy(
                name = name.ifBlank { if (pub == DEVELOPER_PUBKEY) DEVELOPER_NAME else it.name },
                imported = true,
                verified = verified,
                friends = list,
                dismissed = (it.dismissed + dismissed).distinct(),
                devAdded = it.devAdded || devAdded,
            )
        }
        Prefs.accounts.update { it.copy(nostrSecret = key.toHex()) }
        load(key)
        return true
    }

    fun logout() {
        closeLive()
        sk = null
        _me.value = null
        _data.value = emptyMap()
        Prefs.accounts.update { it.copy(nostrSecret = null) }
    }

    fun rename(name: String) {
        store.update { it.copy(name = name.trim()) }
        _me.value = _me.value?.copy(name = name.trim())
        if (!store.value.imported) ioScope.launch { publishProfile() }
    }

    fun inviteLink(): String? = _me.value?.let { INVITE_BASE + it.code + if (it.name.isNotBlank()) "?n=" + java.net.URLEncoder.encode(it.name, "UTF-8") else "" }

    // ── friends ──

    fun parseCode(code: String): String? {
        val text = code.trim()
        Regex("npub1[02-9ac-hj-np-z]{58}").find(text.lowercase())?.value?.let { npub ->
            val (hrp, bytes) = Nostr.bech32Decode(npub) ?: return null
            if (hrp == "npub" && Secp256k1.isValidPublicKey(bytes)) return bytes.toHex()
        }
        if (Regex("^[0-9a-fA-F]{64}$").matches(text) && Secp256k1.isValidPublicKey(text.hexToBytes())) return text.lowercase()
        return null
    }

    fun addFriend(pubkey: String, name: String = "") {
        val me = _me.value ?: return
        if (pubkey == me.pubkey || friends.value.any { it.pubkey == pubkey }) return
        val known = name.ifBlank { _data.value[pubkey]?.name ?: _requests.value.firstOrNull { it.pubkey == pubkey }?.name.orEmpty() }
        setFriends(friends.value + Friend(pubkey, known, System.currentTimeMillis()))
        _requests.value = _requests.value.filterNot { it.pubkey == pubkey }
        ioScope.launch {
            publishTo(pubkey, "hello", JSONObject().put("name", me.name).toString())
            lastNow?.let { publishNow(it) }
            restartLive()
        }
    }

    fun removeFriend(pubkey: String) {
        setFriends(friends.value.filterNot { it.pubkey == pubkey })
        if (_listeningTo.value == pubkey) stopListenAlong()
        ioScope.launch { publishTo(pubkey, "now", ""); restartLive() }
    }

    fun dismiss(pubkey: String) {
        _requests.value = _requests.value.filterNot { it.pubkey == pubkey }
        store.update { it.copy(dismissed = it.dismissed + pubkey) }
    }

    private fun setFriends(list: List<Friend>) {
        friends.value = list
        store.update { it.copy(friends = list) }
    }

    private fun ensureDeveloperFriend() {
        val me = _me.value ?: return
        if (me.pubkey == DEVELOPER_PUBKEY || store.value.devAdded) return
        store.update { it.copy(devAdded = true) }
        if (friends.value.none { it.pubkey == DEVELOPER_PUBKEY }) setFriends(friends.value + Friend(DEVELOPER_PUBKEY, DEVELOPER_NAME, System.currentTimeMillis()))
        if (store.value.helloSentFor != me.pubkey) ioScope.launch {
            delay(8000)
            publishTo(DEVELOPER_PUBKEY, "hello", JSONObject().put("name", me.name).toString())
            store.update { it.copy(helloSentFor = me.pubkey) }
        }
    }

    suspend fun searchByName(query: String): List<Found> {
        val needle = query.trim().lowercase()
        if (needle.length < 2) return emptyList()
        val mine = _me.value?.pubkey
        val found = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Found>>()
        val filter = JSONObject().put("kinds", JSONArray().put(Nostr.KIND_APP_DATA)).put("#d", JSONArray().put(D + "profile"))
            .put("#t", JSONArray().put("drxwnify")).put("limit", 500)
        val id = RelayPool.subscribe(listOf(filter)) { e ->
            val o = runCatching { JSONObject(e.content) }.getOrNull() ?: return@subscribe
            val name = o.optString("name").trim()
            val allowed = o.optBoolean("find", false) || e.pubkey == DEVELOPER_PUBKEY
            if (!allowed || name.isBlank() || e.pubkey == mine || needle !in name.lowercase()) return@subscribe
            val prev = found[e.pubkey]
            if (prev == null || prev.first < e.createdAt) found[e.pubkey] = e.createdAt to Found(e.pubkey, name, o.optString("pic").takeIf { it.isNotBlank() })
        }
        delay(4000)
        RelayPool.close(id)
        return found.values.map { it.second }.sortedBy { !it.name.lowercase().startsWith(needle) }.take(20)
    }

    // ── live ──

    @Synchronized
    private fun openLive() {
        val me = _me.value?.pubkey ?: return
        val keys = (friends.value.map { it.pubkey } + DEVELOPER_PUBKEY + me).distinct()
        val dValues = JSONArray().put(D + "profile")
        DATA_FIELDS.forEach { dValues.put(D + it); dValues.put("$D$it:$me") }
        dValues.put("${D}verified:$me")
        liveSubs += RelayPool.subscribe(listOf(JSONObject().put("kinds", JSONArray().put(Nostr.KIND_APP_DATA)).put("authors", JSONArray(keys)).put("#d", dValues)), ::onEvent)
        liveSubs += RelayPool.subscribe(listOf(JSONObject().put("kinds", JSONArray().put(Nostr.KIND_APP_DATA)).put("#p", JSONArray().put(me))
            .put("since", System.currentTimeMillis() / 1000 - 60L * 24 * 3600)), ::onEvent)
    }

    @Volatile private var verifiedAt = 0L

    /** drxwnslvt's reviews are open here: the developer himself, or a profile he marked. */
    fun reviewsAccess(): Boolean = _me.value?.pubkey == DEVELOPER_PUBKEY || store.value.verified

    /** For the library to follow: changes when the identity or the mark changes. */
    val accessState get() = store.state

    @Synchronized
    private fun closeLive() { liveSubs.forEach(RelayPool::close); liveSubs.clear() }

    @Synchronized
    private fun restartLive() { closeLive(); openLive() }

    private fun onEvent(event: NostrEvent) {
        val me = _me.value?.pubkey ?: return
        val key = sk ?: return
        val d = event.tag("d") ?: return
        if (!d.startsWith(D)) return
        val parts = d.removePrefix(D).split(':')
        val field = parts[0]
        val personal = parts.getOrNull(1)
        if (personal != null && personal != me) return
        if (field == "verified") {
            // only the developer's word counts; the newest one (a mark, then maybe its removal)
            if (event.pubkey != DEVELOPER_PUBKEY || event.createdAt < verifiedAt) return
            verifiedAt = event.createdAt
            val on = event.content.isNotEmpty() && Nostr.decrypt(key, DEVELOPER_PUBKEY, event.content)
                ?.let { runCatching { JSONObject(it).optInt("v") == 1 }.getOrDefault(false) } == true
            store.update { it.copy(verified = on) }
            return
        }
        val content = if (personal != null && event.content.isNotEmpty()) Nostr.decrypt(key, event.pubkey, event.content) ?: return else event.content
        val author = event.pubkey
        if (field == "hello") {
            val isFriend = friends.value.any { it.pubkey == author }
            if (content.isEmpty() || isFriend || author in store.value.dismissed) return
            val name = runCatching { JSONObject(content).optString("name") }.getOrDefault("")
            if (_requests.value.none { it.pubkey == author }) _requests.value = _requests.value + Request(author, name, event.createdAt * 1000)
            return
        }
        if (friends.value.none { it.pubkey == author } && author != DEVELOPER_PUBKEY && author != me) return
        synchronized(this) {
            val cur = _data.value[author] ?: FriendData()
            if ((cur.stamps[field] ?: 0L) >= event.createdAt) return
            val json = content.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
            val upd = when (field) {
                "profile" -> cur.copy(name = json?.optString("name")?.takeIf { it.isNotBlank() } ?: cur.name, avatar = json?.optString("pic")?.takeIf { it.isNotBlank() } ?: cur.avatar)
                "now" -> cur.copy(now = json?.let(::nowFrom))
                "history" -> cur.copy(history = json?.optJSONArray("tracks")?.let(::refsFrom))
                "playlists" -> cur.copy(playlists = json?.optJSONArray("playlists")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { SharedPlaylist(it.optString("n"), it.optInt("total"), it.optJSONArray("tracks")?.let(::refsFrom).orEmpty()) } } })
                "likes" -> cur.copy(likes = content.takeIf { it.isNotBlank() }?.let(::unpackLikes))
                else -> return
            }.copy(stamps = cur.stamps + (field to event.createdAt))
            _data.value = _data.value + (author to upd)
            if (author == me && field == "profile") _me.value = _me.value?.copy(name = upd.name ?: _me.value?.name.orEmpty(), picture = upd.avatar)
            if (field == "profile" && upd.name != null) friends.value.firstOrNull { it.pubkey == author && it.name != upd.name }?.let { f ->
                setFriends(friends.value.map { if (it.pubkey == f.pubkey) it.copy(name = upd.name) else it })
            }
            if (field == "now" && _listeningTo.value == author) upd.now?.let { follow(it) }
        }
    }

    // ── publishing ──

    private fun publishTo(friend: String, field: String, content: String) {
        val key = sk ?: return
        val enc = if (content.isEmpty()) "" else Nostr.encrypt(key, friend, content)
        RelayPool.publish(Nostr.sign(key, Nostr.KIND_APP_DATA, listOf(listOf("d", "$D$field:$friend"), listOf("p", friend)), enc))
    }

    private fun publishPublic(field: String, content: String) {
        val key = sk ?: return
        RelayPool.publish(Nostr.sign(key, Nostr.KIND_APP_DATA, listOf(listOf("d", D + field), listOf("t", "drxwnify")), content))
    }

    private fun toFriends(field: String, content: String) = friends.value.take(40).forEach { publishTo(it.pubkey, field, content) }

    private fun publishProfile() {
        val me = _me.value ?: return
        if (store.value.imported) return
        publishPublic("profile", JSONObject().put("name", me.name).put("find", false).toString())
    }

    /** History, playlists and likes of this PC — only for a profile made here (the phone owns an imported one). */
    private fun publishLibrary() {
        if (store.value.imported || sk == null) return
        val lib = Library.value
        val history = lib.history.distinctBy { it.track.id }.take(50).map { refOf(it.track, it.at) }
        toFriends("history", JSONObject().put("tracks", JSONArray(history.map(::refJson))).toString())
        val pls = lib.playlists.take(10).map { p -> JSONObject().put("n", p.name).put("total", p.tracks.size).put("tracks", JSONArray(p.tracks.take(50).map { refJson(refOf(it, 0)) })) }
        toFriends("playlists", JSONObject().put("playlists", JSONArray(pls)).toString())
        if (lib.liked.isNotEmpty()) toFriends("likes", packLikes(SharedLikes(lib.liked.size, null, lib.liked.take(500).map { refOf(it, 0) })))
        store.update { it.copy(libraryAt = System.currentTimeMillis()) }
    }

    @Volatile private var lastNow: NowPlay? = null
    private var nowJob: Job? = null

    private fun nowChanged(t: Track, playing: Boolean) {
        // Scripted test runs (DRXW_SCRIPT) never tell friends what plays.
        if (sk == null || !Prefs.settings.value.shareListening || System.getenv("DRXW_SCRIPT") != null) return
        nowJob?.cancel()
        nowJob = ioScope.launch {
            delay(1500)
            val now = NowPlay(refOf(t, 0), Player.engine.position.value, playing, System.currentTimeMillis(), true)
            lastNow = now
            publishNow(now)
        }
    }

    private fun publishNow(n: NowPlay) = toFriends("now", JSONObject().put("track", refJson(n.track)).put("pos", n.positionMs).put("playing", n.playing).put("at", n.at).put("along", n.listenAlong).toString())

    // ── listen along ──

    fun startListenAlong(pubkey: String) {
        _listeningTo.value = pubkey
        _data.value[pubkey]?.now?.let { follow(it, force = true) }
    }

    fun stopListenAlong() { _listeningTo.value = null; lastFollowed = null }

    private var lastFollowed: String? = null

    private fun follow(now: NowPlay, force: Boolean = false) {
        val key = now.track.mediaId ?: "${now.track.artist}—${now.track.title}"
        ioScope.launch {
            if (force || key != lastFollowed) {
                lastFollowed = key
                val track = resolveRef(now.track) ?: return@launch
                Player.play(listOf(track), 0, "Вместе с ${_data.value[_listeningTo.value]?.name ?: "другом"}")
                delay(2500)
                Player.seek(now.positionNow())
            } else if (kotlin.math.abs(Player.engine.position.value - now.positionNow()) > 5000) Player.seek(now.positionNow())
            if (!now.playing) Player.engine.pause()
        }
    }

    /** A friend's track as something playable here: a YouTube id as is, otherwise searched. */
    suspend fun resolveRef(r: TrackRef): Track? {
        val t = r.toTrack()
        if (t.youtubeId != null || t.id.startsWith("mfb:")) return t
        val hit = com.drxwnify.desktop.catalog.Catalog.search("${r.artist} ${r.title}").tracks.firstOrNull() ?: return null
        return hit.copy(cover = r.cover ?: hit.cover)
    }

    // ── json ──

    private fun refOf(t: Track, at: Long) = TrackRef(t.title.take(120), t.artistLine.take(120), t.id.takeIf { it.startsWith("mfb:") }?.removePrefix("mfb:"), t.id.takeIf { !it.startsWith("ref:") }, t.cover, (t.durationSec ?: 0) * 1000L, at)

    private fun refJson(r: TrackRef) = JSONObject().apply {
        put("t", r.title); put("a", r.artist); r.catalogId?.let { put("c", it) }; r.mediaId?.let { put("m", it) }; r.cover?.let { put("i", it) }
        if (r.durationMs > 0) put("d", r.durationMs); if (r.at > 0) put("at", r.at)
    }

    private fun refFrom(o: JSONObject) = TrackRef(o.optString("t"), o.optString("a"), o.optString("c").ifBlank { null }, o.optString("m").ifBlank { null }, o.optString("i").ifBlank { null }, o.optLong("d"), o.optLong("at"))
    private fun refsFrom(a: JSONArray) = (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::refFrom) }
    private fun nowFrom(o: JSONObject) = o.optJSONObject("track")?.let { NowPlay(refFrom(it), o.optLong("pos"), o.optBoolean("playing"), o.optLong("at"), o.optBoolean("along", true)) }

    private fun packLikes(l: SharedLikes): String {
        val prefixes = mutableListOf<String>(); val index = HashMap<String, Int>()
        val rows = JSONArray()
        l.tracks.forEach { t ->
            val cover = t.cover; val cut = cover?.lastIndexOf('/') ?: -1
            val (i, rest) = if (cover != null && cut > 0) index.getOrPut(cover.substring(0, cut + 1)) { prefixes += cover.substring(0, cut + 1); prefixes.size - 1 } to cover.substring(cut + 1) else -1 to ""
            rows.put(JSONArray().put(t.title).put(t.artist).put(t.catalogId.orEmpty()).put((t.durationMs / 1000).toInt()).put(i).put(rest).put(t.mediaId.orEmpty()))
        }
        val json = JSONObject().put("v", 1).put("total", l.total).put("src", l.source.orEmpty()).put("p", JSONArray(prefixes)).put("t", rows).toString()
        val bytes = java.io.ByteArrayOutputStream().also { out -> java.util.zip.GZIPOutputStream(out).use { it.write(json.toByteArray()) } }.toByteArray()
        return "gz1:" + java.util.Base64.getEncoder().encodeToString(bytes)
    }

    private fun unpackLikes(content: String): SharedLikes? = runCatching {
        val text = if (content.startsWith("gz1:")) java.util.zip.GZIPInputStream(java.util.Base64.getDecoder().decode(content.removePrefix("gz1:")).inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8) else content
        val o = JSONObject(text)
        val prefixes = o.optJSONArray("p")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        val rows = o.optJSONArray("t") ?: JSONArray()
        val tracks = (0 until rows.length()).mapNotNull { i ->
            val r = rows.optJSONArray(i) ?: return@mapNotNull null
            TrackRef(r.optString(0), r.optString(1), r.optString(2).ifBlank { null }, r.optString(6).ifBlank { null }, prefixes.getOrNull(r.optInt(4, -1))?.let { it + r.optString(5) }, r.optInt(3) * 1000L)
        }
        SharedLikes(o.optInt("total", tracks.size), o.optString("src").ifBlank { null }, tracks)
    }.getOrNull()
}
