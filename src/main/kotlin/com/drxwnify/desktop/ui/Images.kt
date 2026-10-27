package com.drxwnify.desktop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.drxwnify.desktop.data.Log
import com.drxwnify.desktop.data.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Covers: memory LRU + disk cache in %APPDATA%\Drxwnify\cache\covers, decoded with Skia. */
object Images {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val memory = object : LinkedHashMap<String, ImageBitmap>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 400
    }
    private val colors = HashMap<String, Color>()
    private val gate = Semaphore(6)

    fun cached(url: String): ImageBitmap? = synchronized(memory) { memory[url] }

    suspend fun load(url: String, maxSide: Int = 600): ImageBitmap? {
        cached(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            gate.withPermit {
                cached(url)?.let { return@withPermit it }
                runCatching {
                    val file = if (url.startsWith("file:")) File(java.net.URI(url)) else File(Paths.covers, sha1(url))
                    val bytes = if (file.exists()) file.readBytes() else {
                        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                            if (!r.isSuccessful) error("HTTP ${r.code}")
                            r.body!!.bytes()
                        }.also { file.writeBytes(it) }
                    }
                    val img = Image.makeFromEncoded(bytes)
                    val scaled = if (img.width > maxSide || img.height > maxSide) scale(img, maxSide) else img
                    val bmp = scaled.toComposeImageBitmap()
                    synchronized(memory) { memory[url] = bmp }
                    bmp
                }.onFailure { Log.d("Images", "cover failed: ${it.message}") }.getOrNull()
            }
        }
    }

    private fun scale(img: Image, side: Int): Image {
        val k = side.toFloat() / maxOf(img.width, img.height)
        val w = (img.width * k).toInt().coerceAtLeast(1)
        val h = (img.height * k).toInt().coerceAtLeast(1)
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.drawImageRect(img, Rect.makeWH(w.toFloat(), h.toFloat()))
        return surface.makeImageSnapshot()
    }

    /** The cover's dominant tone, softened, for tinting the page header. */
    suspend fun tone(url: String): Color? {
        synchronized(colors) { colors[url] }?.let { return it }
        val bmp = load(url) ?: return null
        return withContext(Dispatchers.Default) {
            val px = IntArray(bmp.width * bmp.height)
            bmp.readPixels(px)
            var r = 0L; var g = 0L; var b = 0L; var n = 0L
            var i = 0
            while (i < px.size) {
                val p = px[i]
                val pr = (p shr 16) and 0xff; val pg = (p shr 8) and 0xff; val pb = p and 0xff
                val mx = maxOf(pr, pg, pb); val mn = minOf(pr, pg, pb)
                val w = 1 + (mx - mn) / 16 // saturated pixels count more
                r += pr * w; g += pg * w; b += pb * w; n += w
                i += 7
            }
            if (n == 0L) return@withContext null
            val c = Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
            synchronized(colors) { colors[url] = c }
            c
        }
    }

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
