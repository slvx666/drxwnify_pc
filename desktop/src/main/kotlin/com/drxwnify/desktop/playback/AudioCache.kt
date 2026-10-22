package com.drxwnify.desktop.playback

import com.drxwnify.desktop.data.Log
import com.drxwnify.desktop.data.Paths
import com.drxwnify.desktop.data.appJson
import kotlinx.serialization.Serializable
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * What plays is also saved whole in the background (like the phone's song cache): once it is on
 * disk a seek is a local one (instant, instead of a new request to the CDN), and the next play of
 * the track starts at once and works offline. Oldest files go past [LIMIT].
 */
object AudioCache {
    private const val LIMIT = 600L * 1024 * 1024

    @Serializable
    data class Meta(val quality: String, val provider: String)

    private val dir: File by lazy { File(Paths.cache, "audio").apply { mkdirs() } }
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).followRedirects(true).build()
    @Volatile private var running: Call? = null
    @Volatile private var hlsGeneration = 0
    private const val CHUNK = 2L * 1024 * 1024

    private fun name(trackId: String) = MessageDigest.getInstance("SHA-1").digest(trackId.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)

    fun lookup(trackId: String): Pair<File, Meta>? {
        val f = File(dir, name(trackId) + ".audio")
        val m = File(dir, name(trackId) + ".json")
        if (!f.exists() || f.length() < 32_000 || !m.exists()) return null
        val meta = runCatching { appJson.decodeFromString(Meta.serializer(), m.readText()) }.getOrNull() ?: return null
        f.setLastModified(System.currentTimeMillis())
        return f to meta
    }

    /** Saves [source] for [trackId]; [onReady] gets the file when it is complete. One at a time. */
    fun prefetch(trackId: String, source: Source, meta: Meta, onReady: (File) -> Unit) {
        if (!source.url.startsWith("http")) return
        lookup(trackId)?.let { onReady(it.first); return }
        running?.cancel()
        val generation = ++hlsGeneration
        // VK's newer HLS (no MP3 next to it): its segments are joined into one file by FFmpeg.
        if (source.url.contains(".m3u8")) {
            thread(name = "audio-cache-hls", isDaemon = true) {
                val started = System.currentTimeMillis()
                runCatching {
                    val out = com.drxwnify.desktop.data.Remux.run(source.url, source.headers, dir, name(trackId) + "-hls", emptyMap(), cancelled = { hlsGeneration != generation }) { }
                    val f = File(dir, name(trackId) + ".audio")
                    f.delete()
                    if (!out.file.renameTo(f)) error("rename failed")
                    File(dir, name(trackId) + ".json").writeText(appJson.encodeToString(Meta.serializer(), meta))
                    Log.d("Cache", "${source.label.take(40)}: HLS → ${f.length() / 1024} KB in ${System.currentTimeMillis() - started} ms")
                    trim()
                    onReady(f)
                }.onFailure { e -> if (hlsGeneration == generation) Log.d("Cache", "${source.label.take(40)} (HLS) not cached: ${e.message}") }
            }
            return
        }
        thread(name = "audio-cache", isDaemon = true) {
            val tmp = File(dir, name(trackId) + ".part")
            val started = System.currentTimeMillis()
            Log.d("Cache", "saving ${source.label.take(40)}")
            runCatching {
                // In 2 MB ranges: YouTube slows a single whole-file request down to about playback speed.
                tmp.outputStream().use { out ->
                    var from = 0L
                    var total = -1L
                    while (total < 0 || from < total) {
                        if (hlsGeneration != generation) error("cancelled")
                        val call = http.newCall(Request.Builder().url(source.url).apply { source.headers.forEach { (k, v) -> header(k, v) } }
                            .header("Range", "bytes=$from-${from + CHUNK - 1}").build())
                        running = call
                        val got = call.execute().use { r ->
                            if (!r.isSuccessful) error("HTTP ${r.code}")
                            if (r.body?.contentType()?.type == "text") error("not audio")
                            total = if (r.code == 206) r.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L else -2L
                            r.body!!.byteStream().use { it.copyTo(out, 64 * 1024) }
                        }
                        from += got
                        // 200 (range ignored: the whole file came) or an unknown size: done.
                        if (total == -2L || (total == -1L) || got == 0L) break
                    }
                }
                val f = File(dir, name(trackId) + ".audio")
                f.delete()
                if (!tmp.renameTo(f)) error("rename failed")
                File(dir, name(trackId) + ".json").writeText(appJson.encodeToString(Meta.serializer(), meta))
                Log.d("Cache", "${source.label.take(40)}: ${f.length() / 1024} KB in ${System.currentTimeMillis() - started} ms")
                trim()
                onReady(f)
            }.onFailure { e ->
                tmp.delete()
                if (hlsGeneration == generation) Log.d("Cache", "${source.label.take(40)} not cached: ${e.message}")
            }
        }
    }

    fun forget(trackId: String) {
        File(dir, name(trackId) + ".audio").delete()
        File(dir, name(trackId) + ".json").delete()
    }

    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() { running?.cancel(); hlsGeneration++; dir.listFiles()?.forEach { it.delete() } }

    private fun trim() {
        val files = dir.listFiles { f -> f.name.endsWith(".audio") }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= LIMIT) break
            total -= f.length()
            f.delete()
            File(dir, f.nameWithoutExtension + ".json").delete()
        }
    }
}
