package com.drxwnify.desktop.catalog

import com.drxwnify.desktop.data.Log
import com.drxwnify.desktop.data.Paths
import com.drxwnify.desktop.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * drxwnslvt's album reviews, as the phone's NightReviews reads them: the Telegram channel's site builds
 * `data/reviews.json` from the posts and `api/live` carries fresh posts and edits until the next build.
 * Only the facts are taken (artist, album, year, rating, genres, post date), never the review text.
 */
object NightReviews {
    data class Review(
        val id: String,
        val messageId: Long,
        /** "yyyy-MM-dd". */
        val postDate: String,
        val artist: String,
        val album: String,
        val year: Int?,
        val rating: Double,
        val genres: List<String>,
        val country: String,
        val label: String,
        val popularity: Int,
        val coverUrl: String?,
    )

    data class State(
        val reviews: List<Review> = emptyList(),
        val loading: Boolean = false,
        val newIds: Set<String> = emptySet(),
        val error: Boolean = false,
    )

    @Serializable
    private data class Saved(val seen: List<String>? = null, val albums: Map<String, String> = emptyMap())

    private const val BASE = "https://tgzip.drxwnslvt.workers.dev/"
    private const val DATA_URL = BASE + "data/reviews.json"
    private const val LIVE_URL = BASE + "api/live"

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private val saved = Store("night_reviews_state.json", Saved.serializer()) { Saved() }
    private val cacheFile by lazy { File(Paths.root, "night_reviews.json") }
    private val client by lazy { OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build() }
    private val lock = Mutex()

    /** What was fetched before at once, then the site again — every time the list is opened. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        lock.withLock {
            if (_state.value.reviews.isEmpty() && cacheFile.exists()) {
                runCatching { parse(cacheFile.readText()) }.getOrNull()?.let { _state.value = _state.value.copy(reviews = it) }
            }
            _state.value = _state.value.copy(loading = true, error = false)
            val built = fetch(DATA_URL)
            val live = fetch(LIVE_URL)
            val text = built?.let { b -> live?.let { l -> runCatching { mergeLive(b, l) }.getOrNull() } ?: b }
            val parsed = text?.let { runCatching { parse(it) }.getOrNull() }
            if (parsed == null) {
                _state.value = _state.value.copy(loading = false, error = _state.value.reviews.isEmpty())
                return@withLock
            }
            runCatching { cacheFile.writeText(text) }
            val ids = parsed.map { it.id }.toSet()
            // The first time nothing counts as new; afterwards whatever wasn't there before.
            val fresh = saved.value.seen?.let { ids - it.toSet() }.orEmpty()
            saved.update { it.copy(seen = ids.toList()) }
            _state.value = State(parsed, loading = false, newIds = _state.value.newIds + fresh)
        }
    }

    private fun fetch(url: String): String? = runCatching {
        client.newCall(Request.Builder().url("$url?t=${System.currentTimeMillis()}").header("Cache-Control", "no-cache").build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            r.body?.string() ?: error("empty")
        }
    }.onFailure { Log.w("Reviews", "fetch $url failed", it) }.getOrNull()

    /** The site's own merge: a live post replaces the built one, keeping the build's cover and stats. */
    private fun mergeLive(builtText: String, liveText: String): String {
        val base = JSONObject(builtText)
        val live = JSONObject(liveText).optJSONArray("reviews") ?: return builtText
        val exported = base.optLong("exportedTs")
        val list = base.optJSONArray("reviews") ?: JSONArray()
        val indexById = HashMap<String, Int>()
        for (i in 0 until list.length()) list.optJSONObject(i)?.optString("id")?.let { indexById[it] = i }
        val kept = listOf("c", "l", "pop", "img", "pal", "mg", "ls", "pc", "tn", "dur")
        for (i in 0 until live.length()) {
            val l = live.optJSONObject(i) ?: continue
            if (exported > 0 && l.optLong("ed") <= exported) continue
            val id = l.optString("id")
            val at = indexById[id]
            if (at != null) {
                val old = list.getJSONObject(at)
                val merged = JSONObject(l.toString())
                kept.forEach { k -> if (old.has(k) && !old.isNull(k) && old.opt(k).toString().let { v -> v.isNotEmpty() && v != "0" }) merged.put(k, old.get(k)) }
                list.put(at, merged)
            } else {
                list.put(l)
                indexById[id] = list.length() - 1
            }
        }
        return base.put("reviews", list).toString()
    }

    private fun parse(text: String): List<Review> {
        val array = JSONObject(text).getJSONArray("reviews")
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val artist = o.optString("ar").trim()
            val album = o.optString("al").trim()
            if (artist.isEmpty() || album.isEmpty()) return@mapNotNull null
            Review(
                id = o.optString("id").ifBlank { "$artist|$album" },
                messageId = o.optLong("mid"),
                postDate = o.optString("d"),
                artist = artist,
                album = album,
                year = o.optInt("y").takeIf { it > 0 },
                rating = o.optDouble("r", 0.0).takeUnless { it.isNaN() } ?: 0.0,
                genres = o.optJSONArray("g")?.let { g -> (0 until g.length()).map { g.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
                country = o.optString("c").trim(),
                label = o.optString("l").takeUnless { it == "[no label]" }.orEmpty(),
                popularity = o.optInt("pop"),
                // the build gives "data/covers/…", the live feed "/cover/85": a doubled slash is a 404
                coverUrl = o.optString("img").takeIf { it.isNotBlank() }?.let { if (it.startsWith("http")) it else BASE + it.trimStart('/') },
            )
        }
    }

    /** The catalog album of [review], by artist and title; remembered so it opens at once next time. */
    suspend fun findAlbum(review: Review): String? = withContext(Dispatchers.IO) {
        saved.value.albums[review.id]?.let { return@withContext it }
        val wantTitle = normalize(review.album)
        val wantArtist = normalize(review.artist)
        for (q in listOf("${review.artist} ${review.album}", review.album)) {
            val albums = runCatching { Catalog.search(q).albums }.getOrDefault(emptyList())
            val best = albums.map { a ->
                val title = normalize(a.title)
                val artists = normalize(a.subtitle.substringBefore(" · "))
                val artistOk = artists.contains(wantArtist) || wantArtist.contains(artists.ifBlank { "\u0000" })
                val score = when {
                    title == wantTitle -> 3
                    title.startsWith(wantTitle) || wantTitle.startsWith(title) -> 2
                    title.contains(wantTitle) || wantTitle.contains(title) -> 1
                    else -> 0
                }
                Triple(a, if (artistOk) score else 0, review.year == null || a.year == review.year)
            }.filter { it.second > 0 }.sortedWith(compareByDescending<Triple<*, Int, Boolean>> { it.second }.thenByDescending { it.third }).firstOrNull()?.first
            if (best != null) {
                saved.update { it.copy(albums = it.albums + (review.id to best.id)) }
                return@withContext best.id
            }
        }
        null
    }

    private fun normalize(text: String): String =
        java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[(\\[].*?(deluxe|remaster|edition|version|expanded)[^)\\]]*[)\\]]"), " ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}
