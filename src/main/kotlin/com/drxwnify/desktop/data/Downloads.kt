package com.drxwnify.desktop.data

import com.drxwnify.desktop.model.Track
import com.drxwnify.desktop.playback.StreamResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class Downloaded(val track: Track, val path: String, val quality: String, val at: Long)

@Serializable
data class DownloadsData(val done: List<Downloaded> = emptyList())

data class Task(val track: Track, val progress: Float = 0f, val error: String? = null)

/** Saves tracks as files: the stream is copied into .opus / .m4a without re-encoding, with tags. */
object Downloads {
    private val store = Store("downloads.json", DownloadsData.serializer()) { DownloadsData() }
    val done = store.state
    private val _active = MutableStateFlow<Map<String, Task>>(emptyMap())
    val active: StateFlow<Map<String, Task>> = _active
    private val gate = Semaphore(2)

    val folder: File get() = Prefs.settings.value.downloadDir?.let(::File)?.apply { mkdirs() } ?: Paths.defaultDownloads

    fun fileFor(id: String): File? = store.value.done.firstOrNull { it.track.id == id }?.path?.let(::File)?.takeIf { it.exists() }
    fun isDownloaded(id: String) = fileFor(id) != null

    fun enqueue(tracks: List<Track>) = tracks.forEach(::enqueue)

    fun enqueue(track: Track) {
        // A task that failed before is tried again (it used to sit there and block the track for good).
        if (isDownloaded(track.id) || _active.value[track.id]?.let { it.error == null } == true) return
        _active.value = _active.value + (track.id to Task(track))
        ioScope.launch {
            gate.withPermit {
                runCatching { save(track) }
                    .onSuccess { _active.value = _active.value - track.id }
                    .onFailure { e ->
                        Log.w("Downloads", "${track.title} failed", e)
                        _active.value = _active.value + (track.id to Task(track, error = e.message ?: "ошибка"))
                    }
            }
        }
    }

    fun remove(id: String) {
        store.value.done.firstOrNull { it.track.id == id }?.let { File(it.path).delete() }
        store.update { it.copy(done = it.done.filterNot { d -> d.track.id == id }) }
    }

    fun clearFailed() { _active.value = _active.value.filterValues { it.error == null } }

    private fun saveHttp(r: com.drxwnify.desktop.playback.Resolved, dest: File, progress: (Float) -> Unit) {
        val tmp = File(dest.parentFile, dest.name + ".part")
        val req = okhttp3.Request.Builder().url(r.source.url).apply { r.source.headers.forEach { (k, v) -> header(k, v) } }.build()
        try {
            com.drxwnify.desktop.ui.Images.http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body!!
                val total = body.contentLength().coerceAtLeast(1)
                var done = 0L; var last = 0L
                body.byteStream().use { input -> tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        val now = System.currentTimeMillis()
                        if (now - last > 250) { last = now; progress((done.toFloat() / total).coerceIn(0f, 1f)) }
                    }
                } }
            }
        } catch (e: Throwable) { tmp.delete(); throw e }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) error("не удалось сохранить файл")
    }

    /** [track] as a tagged audio file in [dir], and the quality it came in. */
    private suspend fun write(track: Track, dir: File, progress: (Float) -> Unit): Pair<File, String> {
        val r = StreamResolver.resolve(track, patient = true)
        val name0 = "${track.artistLine} — ${track.title}".replace(Regex("[\\/:*?\"<>|]"), "_").take(150)
        // VK serves a plain MP3: saved as it is.
        if (r.provider == "VK" && !r.source.url.contains(".m3u8")) { val f = File(dir, "$name0.mp3"); saveHttp(r, f, progress); return f to r.quality }
        val tags = buildMap { put("title", track.title); put("artist", track.artistLine); track.album?.title?.let { put("album", it) } }
        return Remux.run(r.source.url, r.source.headers, dir, name0, tags, progress = progress).file to r.quality
    }

    private suspend fun save(track: Track) {
        val (dest, quality) = write(track, folder) { p -> _active.value = _active.value + (track.id to Task(track, p)) }
        store.update { it.copy(done = listOf(Downloaded(track, dest.absolutePath, quality, System.currentTimeMillis())) + it.done.filterNot { d -> d.track.id == track.id }) }
        Log.i("Downloads", "saved ${dest.name}")
    }

    /** A file of [track] to hand to another app: the downloaded one, else one written into [dir] (not kept as a download). */
    suspend fun export(track: Track, dir: File): File = fileFor(track.id) ?: write(track, dir) { }.first
}
