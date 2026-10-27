package com.drxwnify.desktop.playback

import com.drxwnify.desktop.data.Log
import com.drxwnify.desktop.data.Prefs
import com.drxwnify.desktop.model.Track
import com.metrolist.innertube.NewPipeExtractor
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YouTubeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

data class Resolved(val source: Source, val quality: String, val provider: String)

/** The phone's sources, in the user's order; which are switched on (here, or for everyone by the site's config). */
object Sources {
    val ALL = listOf("soundcloud", "youtube", "vk", "lossless", "bandcamp", "audius", "soulseek")

    /** Usual quality of each, kbps (lossless = 1411): what "best sound" compares, as on the phone. */
    val KBPS = mapOf("lossless" to 1411, "vk" to 320, "youtube" to 160, "soundcloud" to 160, "bandcamp" to 128, "audius" to 128)

    fun order(): List<String> {
        val saved = Prefs.settings.value.sources.filter { it in ALL }.distinct()
        return saved + ALL.filter { it !in saved }
    }

    fun enabled(id: String) = id == "youtube" ||
        (id !in Prefs.settings.value.disabledSources && id !in com.drxwnify.desktop.data.RemoteConfig.config.value.disabledSources)
}

/**
 * Finds a playable stream for a track the phone's way: every source that is on looks at once (VK,
 * YouTube, SoundCloud, lossless mirrors, Bandcamp, Audius); within a short budget the best-sounding
 * answer wins, the user's order breaking ties.
 */
object StreamResolver {
    private val soundCloudId = com.drxwnify.desktop.data.Store("soundcloud.json", SoundCloudKey.serializer()) { SoundCloudKey() }

    @kotlinx.serialization.Serializable
    private data class SoundCloudKey(val clientId: String? = null)

    private val extra: Map<String, com.drxwnify.desktop.playback.sources.AudioProvider> by lazy {
        mapOf(
            "soundcloud" to com.drxwnify.desktop.playback.sources.SoundCloudAudioProvider(
                loadClientId = { soundCloudId.value.clientId }, saveClientId = { id -> soundCloudId.update { it.copy(clientId = id) } },
            ),
            "lossless" to com.drxwnify.desktop.playback.sources.LosslessMirrorProvider(),
            "bandcamp" to com.drxwnify.desktop.playback.sources.BandcampAudioProvider(),
            "audius" to com.drxwnify.desktop.playback.sources.AudiusAudioProvider(),
        )
    }

    private class Racer(val id: String, val kbps: Int, val job: kotlinx.coroutines.Deferred<Resolved?>)
    private class Hit(val resolved: Resolved, val until: Long)
    private val cache = ConcurrentHashMap<String, Hit>()
    /** The YouTube stream found alongside VK: a VK stream that breaks switches to it at once. */
    private val ytCache = ConcurrentHashMap<String, Hit>()

    /** How long VK may take before YouTube (already found by then) starts instead: a slow CDN must not hold the start up. */
    private fun vkBudgetMs(patient: Boolean) = if (patient) 8000L else if (Prefs.settings.value.pickMode == "speed") 700L else 2000L

    // Audio itags: Opus 160/70/50 kbps in WebM, AAC 128/48 kbps in MP4.
    private fun order(): List<Int> = when (Prefs.settings.value.quality) {
        "saver" -> listOf(250, 249, 139, 251, 140)
        "balanced" -> listOf(140, 251, 250, 139)
        else -> listOf(251, 140, 250, 249, 139)
    }

    private fun label(itag: Int) = when (itag) {
        251 -> "Opus 160"; 250 -> "Opus 70"; 249 -> "Opus 50"; 140 -> "AAC 128"; 139 -> "AAC 48"; else -> "itag $itag"
    }

    /**
     * [skipVk]: VK failed for this track just now (its CDN dropped mid-play) — go straight to YouTube.
     * [patient]: for a file (download, send): VK's better MP3 is worth waiting for, the start time doesn't matter.
     */
    suspend fun resolve(track: Track, skipVk: Boolean = false, patient: Boolean = false): Resolved = withContext(Dispatchers.IO) {
        com.drxwnify.desktop.data.Downloads.fileFor(track.id)?.let { f ->
            val q = com.drxwnify.desktop.data.Downloads.done.value.done.firstOrNull { it.track.id == track.id }?.quality ?: f.extension
            return@withContext Resolved(Source(f.absolutePath, label = f.name), q, "Скачано")
        }
        // Played before and saved whole: starts at once, seeks locally, works offline.
        AudioCache.lookup(track.id)?.let { (f, meta) ->
            return@withContext Resolved(Source(f.absolutePath, label = "${track.artistLine} — ${track.title}"), meta.quality, meta.provider)
        }
        cache[track.id]?.takeIf { it.until > System.currentTimeMillis() && !(skipVk && it.resolved.provider == "VK") }?.let { return@withContext it.resolved }
        if (skipVk) ytCache[track.id]?.takeIf { it.until > System.currentTimeMillis() }?.let { return@withContext it.resolved }
        val chosen = com.drxwnify.desktop.data.Matches.of(track)
        // Picked by hand ("Другая версия") or saving traffic: YouTube as it is.
        if (Prefs.settings.value.quality == "saver" || chosen?.chosen == "yt") return@withContext youtube(track, chosen).also { cache[track.id] = Hit(it, System.currentTimeMillis() + 4 * 3600_000) }
        val order = Sources.order()
        // Not children of this call: a source stuck on a dead server must not keep it waiting.
        val wanted = java.util.concurrent.atomic.AtomicBoolean(true)
        val racers = buildList {
            add(Racer("youtube", 160, com.drxwnify.desktop.data.ioScope.async { runCatching { youtube(track, chosen) }.onFailure { Log.d("Resolve", "YouTube: ${it.message}") }.getOrNull() }))
            if (!skipVk && Sources.enabled("vk") && VkSource.available()) {
                add(Racer("vk", 320, com.drxwnify.desktop.data.ioScope.async { runCatching { VkSource.resolve(track) { wanted.get() } }.onFailure { Log.w("Resolve", "VK failed", it) }.getOrNull() }))
            }
            extra.forEach { (id, p) ->
                if (Sources.enabled(id) && p.isReady()) add(Racer(id, Sources.KBPS[id] ?: 128, com.drxwnify.desktop.data.ioScope.async { fromProvider(id, p, track) }))
            }
        }
        val budget = vkBudgetMs(patient)
        val started = System.currentTimeMillis()
        var picked: Pair<Racer, Resolved>? = null
        while (true) {
            val done = racers.filter { it.job.isCompleted }
            val got = done.mapNotNull { r -> r.job.await()?.let { r to it } }
            val allDone = done.size == racers.size
            // Nothing still looking could sound better than what is in hand: no reason to wait.
            val bestInHand = got.maxOfOrNull { it.first.kbps } ?: 0
            val bestPending = racers.filter { !it.job.isCompleted }.maxOfOrNull { it.kbps } ?: 0
            if (got.isNotEmpty() && (allDone || bestInHand >= bestPending || System.currentTimeMillis() - started >= budget)) {
                picked = got.sortedWith(compareByDescending<Pair<Racer, Resolved>> { it.first.kbps }.thenBy { order.indexOf(it.first.id) }).first()
                break
            }
            if (allDone) break
            kotlinx.coroutines.delay(40)
        }
        val (winner, r) = picked ?: error("ни один источник не нашёл этот трек")
        Log.d("Resolve", "${track.title}: ${winner.id} won in ${System.currentTimeMillis() - started} ms (answered: ${racers.filter { it.job.isCompleted }.joinToString { it.id }})")
        if (winner.id != "vk") wanted.set(false)
        cache[track.id] = Hit(r, System.currentTimeMillis() + if (winner.id == "vk") 50 * 60_000 else 4 * 3600_000)
        // A VK stream that breaks goes on from YouTube at once: keep YouTube's answer at hand.
        if (winner.id != "youtube") racers.firstOrNull { it.id == "youtube" }?.let { y ->
            com.drxwnify.desktop.data.ioScope.launch { y.job.await()?.let { ytCache[track.id] = Hit(it, System.currentTimeMillis() + 4 * 3600_000) } }
        }
        r
    }

    /** One of the phone's providers: its own search with the strict title/artist/length gates, then a fresh stream. */
    private suspend fun fromProvider(id: String, p: com.drxwnify.desktop.playback.sources.AudioProvider, track: Track): Resolved? = runCatching {
        val q = com.drxwnify.desktop.playback.sources.AudioQuery(
            catalogId = track.id, title = track.title, artists = track.artists.map { it.name }.filter { it.isNotBlank() },
            album = track.album?.title, durationMs = (track.durationSec ?: 0) * 1000L, isrc = null,
        )
        val match = withTimeoutOrNull(p.searchTimeoutMs) { p.search(q) } ?: return null
        val s = withTimeoutOrNull(10_000) { p.stream(q, match) } ?: return null
        val quality = when {
            id == "lossless" || s.mimeType.contains("flac") -> "FLAC"
            s.codecs.contains("mp3") || s.mimeType == "audio/mpeg" -> "MP3 ${s.bitrate / 1000}"
            s.codecs.contains("opus") -> "Opus ${s.bitrate / 1000}"
            else -> "AAC ${s.bitrate / 1000}"
        }
        val name = when (id) { "soundcloud" -> "SoundCloud"; "lossless" -> "FLAC"; "bandcamp" -> "Bandcamp"; "audius" -> "Audius"; else -> id }
        Resolved(Source(s.uri, emptyMap(), "${track.artistLine} — ${track.title}", cacheKey = track.id), quality, name)
    }.onFailure { Log.d("Resolve", "$id: ${it.message}") }.getOrNull()

    private suspend fun youtube(track: Track, chosen: com.drxwnify.desktop.data.Match?): Resolved {
        val videoId = chosen?.yt?.takeIf { chosen.chosen == "yt" } ?: track.youtubeId ?: chosen?.yt
            ?: findOnYouTube(track)?.also { com.drxwnify.desktop.data.Matches.rememberYt(track, it) }
            ?: error("нет этого трека на YouTube")
        val want = order()
        // The phone's order: VISIONOS serves whole files with pre-signed URLs; ANDROID_VR next.
        for (client in listOf(YouTubeClient.VISIONOS, YouTubeClient.ANDROID_VR_1_65_10, YouTubeClient.ANDROID_VR_1_43_32)) {
            val resp = YouTube.player(videoId, null, client).getOrNull() ?: continue
            if (resp.playabilityStatus.status != "OK") { Log.d("Resolve", "${client.clientName}: ${resp.playabilityStatus.status}"); continue }
            val formats = resp.streamingData?.adaptiveFormats.orEmpty().filter { it.url != null && it.mimeType.startsWith("audio/") }
            val f = want.firstNotNullOfOrNull { tag -> formats.firstOrNull { it.itag == tag } } ?: formats.maxByOrNull { it.bitrate } ?: continue
            val r = Resolved(
                Source(f.url!!, mapOf("User-Agent" to client.userAgent), "${track.artistLine} — ${track.title}", cacheKey = track.id),
                label(f.itag), "YouTube",
            )
            Log.d("Resolve", "${track.title}: ${client.clientName} ${label(f.itag)}")
            return r
        }
        // Last resort: the muxed 360p stream NewPipe still gets (AAC ~96 kbps inside).
        val streams = NewPipeExtractor.newPipePlayer(videoId).toMap()
        Log.d("Resolve", "${track.title}: newpipe itags ${streams.keys}")
        val itag = (want + 18).firstOrNull { it in streams } ?: error("YouTube не отдал звук")
        Log.d("Resolve", "${track.title}: YouTube ${label(itag)}")
        return Resolved(Source(streams.getValue(itag), emptyMap(), "${track.artistLine} — ${track.title}"), if (itag == 18) "AAC 96" else label(itag), "YouTube")
    }

    fun forget(id: String) { cache.remove(id) }

    /** Catalog tracks (Spotify/Yandex ids) are played from their YouTube match, as on the phone. */
    private suspend fun findOnYouTube(track: Track): String? {
        val q = "${track.artists.firstOrNull()?.name.orEmpty()} ${track.title}".trim()
        val items = YouTube.search(q, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items.orEmpty().filterIsInstance<SongItem>()
        val want = track.durationSec
        return items.firstOrNull { want == null || it.duration == null || kotlin.math.abs(it.duration!! - want) <= 6 }?.id
            ?: items.firstOrNull()?.id
    }
}
