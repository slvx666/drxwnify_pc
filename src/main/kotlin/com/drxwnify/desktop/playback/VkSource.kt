package com.drxwnify.desktop.playback

import com.drxwnify.desktop.data.Log
import com.drxwnify.desktop.data.Prefs
import com.drxwnify.desktop.data.Store
import com.drxwnify.desktop.data.ioScope
import com.drxwnify.desktop.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@Serializable
data class SharedVkDay(val day: Int = -1, val tracks: List<String> = emptyList())

/**
 * VK Music the way the phone uses it: the official client's token, API 5.116 and its User-Agent;
 * HLS addresses turned into the MP3 next to them (FFmpeg plays the HLS itself when there isn't one).
 * Without the user's own token the shared demo account from the site's config stands in, with its
 * daily limit, exactly as on the phone.
 */
object VkSource {
    private const val API = "5.116"
    private const val UA = "VKAndroidApp/5.52-4543 (Android 5.1.1; SDK 22; x86_64; unknown Android SDK built for x86_64; en; 320x240)"
    private val CONFIG_URLS = listOf("https://drxwnify.pages.dev/config.json", "https://raw.githubusercontent.com/slvx666/DRXWNIFY/master/website/config.json")
    private val M3U8_TO_MP3 = Regex("/[0-9a-f]+(/audios)?/([0-9a-f]+)/index\\.m3u8")

    private val http = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build()
    private val gate = Mutex()
    private var lastCall = 0L

    @Volatile private var shared: Pair<String, Int>? = null
    private val sharedDay = Store("vk_shared.json", SharedVkDay.serializer()) { SharedVkDay() }

    fun init() {
        ioScope.launch {
            for (u in CONFIG_URLS) {
                val ok = runCatching {
                    http.newCall(Request.Builder().url(u).build()).execute().use { r ->
                        val o = JSONObject(r.body!!.string()).optJSONObject("vkShared") ?: return@use false
                        val tok = o.optString("token").takeIf { it.isNotBlank() } ?: return@use false
                        shared = tok to o.optInt("dailyTracks", 40)
                        Log.i("VK", "shared demo account from $u: ${shared?.second} tracks a day")
                        true
                    }
                }.getOrDefault(false)
                if (ok) break
            }
        }
    }

    private fun ownToken() = Prefs.accounts.value.vkToken?.takeIf { it.isNotBlank() }
    fun token(): String? = ownToken() ?: shared?.first
    val usingShared: Boolean get() = ownToken() == null && shared != null
    fun available(): Boolean = (token() != null && "vk" !in Prefs.settings.value.disabledSources).also { if (!it) Log.d("VK", "not available (token=${token() != null})") }

    /** The shared account's tracks for today: false once they're used up (the same track counts once a day). */
    private fun allow(id: String): Boolean {
        if (!usingShared) return true
        val limit = shared?.second ?: return true
        val today = (System.currentTimeMillis() / 86_400_000L).toInt()
        val cur = sharedDay.value.let { if (it.day == today) it else SharedVkDay(today) }
        if (id in cur.tracks) return true
        if (cur.tracks.size >= limit) return false
        sharedDay.update { cur.copy(tracks = cur.tracks + id) }
        return true
    }

    private suspend fun call(method: String, params: Map<String, String>): JSONObject? = gate.withLock {
        val wait = lastCall + 350 - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        lastCall = System.currentTimeMillis()
        val tok = token() ?: return null
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder().apply { params.forEach { (k, v) -> add(k, v) }; add("access_token", tok); add("v", API) }.build()
            http.newCall(Request.Builder().url("https://api.vk.com/method/$method").header("User-Agent", UA).post(body).build()).execute().use { r ->
                val root = JSONObject(r.body?.string() ?: return@use null)
                root.optJSONObject("error")?.let { e -> Log.w("VK", "$method: ${e.optInt("error_code")} ${e.optString("error_msg")}"); return@use null }
                root
            }
        }
    }

    /** [url] — the MP3 next to the HLS playlist when there is one; [raw] — the address VK gave. */
    private data class Hit(val id: String, val title: String, val artist: String, val dur: Int, val url: String?, val raw: String?)

    private fun parse(o: JSONObject): Hit? {
        val id = o.optLong("id").takeIf { it != 0L } ?: return null
        val owner = o.optLong("owner_id").takeIf { it != 0L } ?: return null
        val key = o.optString("access_key").takeIf { it.isNotBlank() }
        val sub = o.optString("subtitle").takeIf { it.isNotBlank() }
        val title = o.optString("title").let { t -> if (sub != null && !t.contains(sub, true)) "$t ($sub)" else t }
        val raw = o.optString("url").takeIf { it.isNotBlank() }
        val url = raw?.let { u -> if (u.contains("index.m3u8")) M3U8_TO_MP3.replace(u) { m -> "${m.groupValues[1]}/${m.groupValues[2]}.mp3" } else u }
        return Hit(listOfNotNull("${owner}_$id", key).joinToString("_"), title, o.optString("artist"), o.optInt("duration"), url, raw)
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("\\(.*?\\)|\\[.*?]|feat\\.?.*|ft\\.?.*"), " ").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** How alike two names are: share of words of [want] found in [got]. */
    private fun likeness(want: String, got: String): Double {
        val w = norm(want).split(' ').filter { it.isNotBlank() }
        if (w.isEmpty()) return 0.0
        val g = norm(got)
        return w.count { g.contains(it) }.toDouble() / w.size
    }

    /** VK uploads that could be [track], best first — for "Другая версия". */
    suspend fun candidates(track: Track): List<Candidate> {
        if (!available()) return emptyList()
        val artist = track.artists.firstOrNull()?.name.orEmpty()
        val root = call("audio.search", mapOf("q" to "$artist ${track.title}".trim(), "count" to "30", "auto_complete" to "1", "sort" to "2")) ?: return emptyList()
        val arr = root.optJSONObject("response")?.optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::parse) }
            .map { h -> Candidate(h.id, h.title, h.artist, h.dur, likeness(track.title, h.title) * 0.6 + likeness(artist, h.artist) * 0.4) }
            .sortedByDescending { it.score }
    }

    data class Candidate(val id: String, val title: String, val artist: String, val durationSec: Int, val score: Double)

    /**
     * The best VK upload of [track]: same title and artist, the closest length. [stillWanted] turns false
     * once YouTube has started instead (VK was too slow): then nothing is taken from the shared account's day.
     */
    suspend fun resolve(track: Track, stillWanted: () -> Boolean = { true }): Resolved? {
        if (!available()) return null
        // The upload the phone already picked for this track: no search, the very same file.
        com.drxwnify.desktop.data.Matches.of(track)?.vk?.let { known ->
            val (r, gone) = byId(track, known, stillWanted)
            if (r != null) return r
            // Gone from VK: forget it. Only unreachable right now: keep it for later, look for another upload.
            if (gone) com.drxwnify.desktop.data.Matches.forgetVk(track)
        }
        val artist = track.artists.firstOrNull()?.name.orEmpty()
        val root = call("audio.search", mapOf("q" to "$artist ${track.title}".trim(), "count" to "30", "auto_complete" to "1", "sort" to "2")) ?: return null
        val arr = root.optJSONObject("response")?.optJSONArray("items") ?: return null
        val hits = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::parse) }
        val want = track.durationSec
        val scored = hits.map { h ->
            val t = likeness(track.title, h.title)
            val a = if (artist.isBlank()) 1.0 else maxOf(likeness(artist, h.artist), likeness(artist, h.title) * 0.6)
            val d = if (want == null || h.dur == 0) 0.5 else (1.0 - abs(h.dur - want) / 8.0).coerceIn(0.0, 1.0)
            h to (t * 0.45 + a * 0.35 + d * 0.2)
        }
        Log.d("VK", "'${track.title}': ${hits.size} results, best ${scored.maxByOrNull { it.second }?.let { "%.2f %s — %s %ds".format(it.second, it.first.artist, it.first.title, it.first.dur) }}")
        val good = scored.filter { it.second >= 0.72 && (want == null || it.first.dur == 0 || abs(it.first.dur - want) <= 7) }
            .sortedByDescending { it.second }.map { it.first }
            // Uploads on a CDN host that didn't answer lately go last (the phone's likelyDown).
            .sortedBy { CdnHosts.likelyDown(it.url ?: it.raw) }
        for (best in good.take(3)) {
            val url = playable(best) ?: continue
            if (!stillWanted()) return null
            if (!allow(best.id)) { Log.i("VK", "shared account: today's tracks are used up"); return null }
            Log.d("Resolve", "${track.title}: VK ${best.artist} — ${best.title} (${best.dur}s)")
            com.drxwnify.desktop.data.Matches.rememberVk(track, best.id)
            return Resolved(Source(url, mapOf("User-Agent" to UA), "${track.artistLine} — ${track.title}", cacheKey = track.id), "MP3", "VK")
        }
        return null
    }

    /**
     * An address of [hit] that answers right now: the MP3, else the HLS playlist (FFmpeg plays it);
     * a search result's address that is gone (404) is asked for again by id. Null when the CDN is
     * unreachable — the caller moves on to YouTube instead of hanging on it.
     */
    private suspend fun playable(hit: Hit, refetched: Boolean = false): String? {
        val tries = listOfNotNull(hit.url, hit.raw).distinct()
        var gone = tries.isEmpty()
        for (u in tries) {
            when (CdnHosts.check(u, UA)) {
                CdnHosts.Probe.OK -> return u
                CdnHosts.Probe.GONE -> gone = true
                CdnHosts.Probe.UNREACHABLE -> Unit
            }
        }
        if (!gone || refetched) return null
        val fresh = call("audio.getById", mapOf("audios" to hit.id))?.optJSONArray("response")?.optJSONObject(0)?.let(::parse) ?: return null
        return playable(fresh, refetched = true)
    }

    /** The phone's chosen upload by id; the flag says it no longer exists (rather than being unreachable). */
    private suspend fun byId(track: Track, vkId: String, stillWanted: () -> Boolean): Pair<Resolved?, Boolean> {
        val root = call("audio.getById", mapOf("audios" to vkId)) ?: return null to false
        val hit = root.optJSONArray("response")?.optJSONObject(0)?.let(::parse) ?: return null to true
        val url = playable(hit, refetched = true) ?: return null to false
        if (!stillWanted() || !allow(vkId)) return null to false
        Log.d("Resolve", "${track.title}: VK (phone's match) ${hit.artist} — ${hit.title} [${if (url.contains(".m3u8")) "HLS" else "MP3"}]")
        return Resolved(Source(url, mapOf("User-Agent" to UA), "${track.artistLine} — ${track.title}", cacheKey = track.id), "MP3", "VK") to false
    }
}
