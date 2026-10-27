package com.drxwnify.desktop.playback

import com.drxwnify.desktop.data.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.ffmpeg.avutil.LogCallback
import org.bytedeco.javacpp.BytePointer
import java.nio.ShortBuffer
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

enum class EngineState { Idle, Loading, Playing, Paused, Ended, Error }

/** FFmpeg's own warnings and errors, into our log (JavaCV would hand them to an SLF4J that isn't there). */
private object FfmpegLog : LogCallback() {
    private var last = ""
    private var lastAt = 0L

    @Synchronized
    override fun call(level: Int, msg: BytePointer) {
        if (level > avutil.AV_LOG_WARNING) return
        val text = msg.string.trim()
        // FFmpeg just reconnects when a CDN drops its kept-alive connection: not worth a line each time.
        if ("keepalive request failed" in text) return
        val now = System.currentTimeMillis()
        if (text.isEmpty() || (text == last && now - lastAt < 5000)) return
        last = text; lastAt = now
        Log.w("FFmpeg", text)
    }
}

/** [cacheKey]: the track this stream is, so a whole copy can be saved alongside (see [AudioCache]). */
data class Source(val url: String, val headers: Map<String, String> = emptyMap(), val label: String = "", val cacheKey: String? = null)

/**
 * Decodes with FFmpeg (any codec/container the phone plays, HLS with AES-128 included) and
 * plays 16-bit stereo PCM through Java Sound. One decoding thread per loaded source.
 */
class AudioEngine {
    private val rate = 48000
    private val format = AudioFormat(rate.toFloat(), 16, 2, true, false)

    private val _state = MutableStateFlow(EngineState.Idle)
    val state: StateFlow<EngineState> = _state
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration
    /** 32 bands, 0..1, for the little level meters. */
    private val _levels = MutableStateFlow(FloatArray(32))
    val levels: StateFlow<FloatArray> = _levels
    var lastError: String? = null; private set
    /** The stream's own bitrate in kbps once FFmpeg has opened it (0 = unknown). */
    val bitrate = MutableStateFlow(0)

    @Volatile var volume: Float = 0.8f
    /** Stereo balance, -1 (left only) … 0 … 1 (right only), as the phone's. */
    @Volatile var balance: Float = 0f
    /** Extra gain 0…1 the sleep timer lowers to fade out. */
    @Volatile var fade: Float = 1f
    @Volatile var outputDevice: String? = null
    /** The window can be seen: worth computing the spectrum for the level meters. */
    @Volatile var visualsWanted = true
    var onEnded: (() -> Unit)? = null
    /** Playback broke (the stream stopped answering); gets the position it got to. */
    var onError: ((Long) -> Unit)? = null

    @Volatile private var session: Session? = null
    private val lock = Object()
    /** The stream being played (what [onError] is about). */
    val source: Source? get() = session?.source

    init {
        // FFmpeg can wait on a stalled CDN with no end (an HLS segment that never comes): if a network
        // stream gives no sound for [STALL_MS], opening included, it's dropped instead of spinning forever.
        Thread({
            while (true) {
                Thread.sleep(1000)
                val s = session ?: continue
                if (s.cancelled || s.paused || s.onLocal || !s.source.url.startsWith("http")) continue
                if (System.currentTimeMillis() - s.alive > STALL_MS) s.hung()
            }
        }, "drxw-audio-watchdog").apply { isDaemon = true }.start()
    }

    companion object {
        private const val STALL_MS = 12_000L

        fun warmUp() {
            runCatching {
                avutil.av_log_set_level(avutil.AV_LOG_WARNING)
                avutil.setLogCallback(FfmpegLog)
                org.bytedeco.javacpp.Loader.load(org.bytedeco.ffmpeg.global.avformat::class.java)
            }.onFailure { Log.e("Audio", "FFmpeg didn't load", it) }
        }

        fun outputDevices(): List<String> = AudioSystem.getMixerInfo()
            .filter { info -> AudioSystem.getMixer(info).sourceLineInfo.any { it.lineClass == SourceDataLine::class.java } }
            .map { it.name }
            .filterNot { it.startsWith("Port ") }
    }

    fun load(source: Source, startMs: Long = 0, play: Boolean = true) {
        session?.cancel()
        _state.value = EngineState.Loading
        _position.value = startMs
        _duration.value = 0
        bitrate.value = 0
        lastError = null
        val s = Session(source, startMs, play)
        session = s
        Thread(s, "drxw-audio").apply { isDaemon = true; priority = Thread.MAX_PRIORITY - 1 }.start()
    }

    fun pause() { session?.paused = true; if (_state.value == EngineState.Playing) _state.value = EngineState.Paused }
    fun resume() {
        val s = session ?: return
        s.alive = System.currentTimeMillis()
        s.paused = false
        synchronized(lock) { lock.notifyAll() }
        if (_state.value == EngineState.Paused) _state.value = EngineState.Playing
    }
    fun seek(ms: Long) {
        val s = session ?: return
        s.alive = System.currentTimeMillis()
        s.seekTo = ms.coerceAtLeast(0)
        _position.value = s.seekTo
        synchronized(lock) { lock.notifyAll() }
    }
    fun stop() { session?.cancel(); session = null; _state.value = EngineState.Idle }

    /** A track was asked for and its source is being looked for: shown as loading before anything opens. */
    fun preparing() { _position.value = 0; _duration.value = 0; bitrate.value = 0; _state.value = EngineState.Loading }

    /** The whole of the playing stream is now on disk: seeks (and a broken connection) go to it. */
    fun attachLocal(cacheKey: String, file: java.io.File) {
        session?.takeIf { it.source.cacheKey == cacheKey }?.localFile = file
    }

    private inner class Session(val source: Source, startMs: Long, play: Boolean) : Runnable {
        @Volatile var cancelled = false
        @Volatile var paused = !play
        @Volatile var seekTo = if (startMs > 0) startMs else -1L
        @Volatile var localFile: java.io.File? = null
        @Volatile var onLocal = false
        /** When the stream last gave sound (or was asked for some): the watchdog's clock. */
        @Volatile var alive = System.currentTimeMillis()
        private val loadedAt = System.currentTimeMillis()
        private var framesSinceOpen = 0
        private var sounded = false
        @Volatile private var opened = false
        private var line: SourceDataLine? = null

        /** No sound for too long: let FFmpeg's thread go (it quits once it returns) and say so. */
        fun hung() {
            val at = _position.value
            Log.w("Audio", "${source.label.take(40)}: no sound for ${STALL_MS / 1000} s while ${if (opened) "playing" else "opening"}, dropping the stream")
            cancel()
            val local = localFile
            if (local != null) { load(Source(local.absolutePath, label = source.label), at, play = true); return }
            lastError = "поток не отвечает"
            _state.value = EngineState.Error
            onError?.invoke(at)
        }

        private fun openLocal(file: java.io.File, atMs: Long): FFmpegFrameGrabber {
            val ng = FFmpegFrameGrabber(file.absolutePath)
            ng.sampleFormat = avutil.AV_SAMPLE_FMT_S16
            ng.sampleRate = rate
            ng.audioChannels = 2
            ng.startUnsafe(false)
            if (atMs > 0) ng.setAudioTimestamp(atMs * 1000)
            return ng
        }

        fun cancel() {
            cancelled = true
            synchronized(lock) { lock.notifyAll() }
            runCatching { line?.stop(); line?.flush() }
        }

        override fun run() {
            var g = FFmpegFrameGrabber(source.url)
            try {
                g.format = null
                g.setOption("reconnect", "1")
                g.setOption("reconnect_streamed", "1")
                g.setOption("reconnect_on_network_error", "1")
                g.setOption("reconnect_delay_max", "2")
                g.setOption("rw_timeout", "8000000")
                g.setOption("user_agent", source.headers["User-Agent"] ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0 Safari/537.36")
                val extra = source.headers.filterKeys { it != "User-Agent" }
                if (extra.isNotEmpty()) g.setOption("headers", extra.entries.joinToString("") { "${it.key}: ${it.value}\r\n" })
                g.sampleFormat = avutil.AV_SAMPLE_FMT_S16
                g.sampleRate = rate
                g.audioChannels = 2
                val opening = System.currentTimeMillis()
                // Not start(): it holds one lock for every grabber in the app, so a stream stuck opening
                // (a stalled CDN) kept every other track — even a file on disk — from starting.
                g.startUnsafe(false)
                if (cancelled) { Log.d("Audio", "${source.label.take(40)}: opened after ${System.currentTimeMillis() - opening} ms, no longer wanted"); return }
                opened = true; alive = System.currentTimeMillis()
                Log.d("Audio", "${source.label.take(40)}: opened in ${System.currentTimeMillis() - opening} ms${if (source.url.contains(".m3u8")) " (HLS)" else ""}")
                _duration.value = max(0L, g.lengthInTime / 1000)
                bitrate.value = (g.audioBitrate / 1000).takeIf { it in 8..5000 } ?: 0
                val l = openLine()
                line = l
                var base = 0L
                var frameBase = 0L
                if (seekTo >= 0) { g.setAudioTimestamp(seekTo * 1000); base = seekTo; seekTo = -1 }
                _state.value = if (paused) EngineState.Paused else EngineState.Playing
                if (!paused) l.start()
                var bytes = ByteArray(0)
                var lastLevels = 0L
                var lastPosition = 0L
                while (!cancelled) {
                    if (paused) {
                        l.stop()
                        synchronized(lock) { while (paused && !cancelled && seekTo < 0) lock.wait(250) }
                        if (cancelled) break
                        alive = System.currentTimeMillis()
                        if (!paused) { l.start(); _state.value = EngineState.Playing }
                    }
                    if (seekTo >= 0) {
                        val to = seekTo; seekTo = -1
                        l.stop(); l.flush()
                        alive = System.currentTimeMillis()
                        val t0 = System.currentTimeMillis()
                        val local = localFile
                        if (local != null && !onLocal) {
                            // The saved copy is complete: from now on seeks are local and instant.
                            runCatching { openLocal(local, to) }
                                .onSuccess { ng -> runCatching { g.stop(); g.release() }; g = ng; onLocal = true }
                                .onFailure { Log.w("Audio", "saved copy unusable", it); runCatching { g.setAudioTimestamp(to * 1000) } }
                        } else {
                            runCatching { g.setAudioTimestamp(to * 1000) }.onFailure { Log.w("Audio", "seek failed", it) }
                        }
                        Log.d("Audio", "seek to ${to / 1000}s took ${System.currentTimeMillis() - t0} ms${if (onLocal) " (local)" else ""}")
                        base = to; frameBase = l.longFramePosition
                        if (!paused) l.start()
                    }
                    val frame = try {
                        g.grabSamples()
                    } catch (e: Exception) {
                        val local = localFile
                        if (onLocal || local == null) throw e
                        // The connection broke but the copy is saved: play on from it.
                        Log.w("Audio", "stream broke at ${_position.value / 1000}s, going on from the saved copy")
                        val at = _position.value
                        runCatching { g.stop(); g.release() }
                        g = openLocal(local, at); onLocal = true; base = at; frameBase = l.longFramePosition
                        continue
                    }
                    if (frame == null) {
                        val local = localFile
                        val at = _position.value
                        // A stream that "ends" well before its length was cut off: the saved copy finishes it.
                        if (!onLocal && local != null && _duration.value > 0 && at < _duration.value - 3000) {
                            Log.w("Audio", "stream ended early at ${at / 1000}s of ${_duration.value / 1000}s, going on from the saved copy")
                            runCatching { g.stop(); g.release() }
                            g = openLocal(local, at); onLocal = true; base = at; frameBase = l.longFramePosition
                            continue
                        }
                        break
                    }
                    alive = System.currentTimeMillis()
                    val buffers = frame.samples ?: continue
                    val n = interleave(buffers, bytes.size).also { if (it.size != bytes.size) bytes = it }
                    val count = fill(buffers, bytes)
                    if (count == 0) continue
                    if (!sounded) { sounded = true; Log.d("Audio", "${source.label.take(40)}: first sound ${System.currentTimeMillis() - loadedAt} ms after load") }
                    // HLS reports its bitrate only once some audio is decoded.
                    if (bitrate.value == 0 && ++framesSinceOpen % 40 == 0) (g.audioBitrate / 1000).takeIf { it in 8..5000 }?.let { bitrate.value = it }
                    var off = 0
                    while (off < count && !cancelled && seekTo < 0) {
                        off += l.write(bytes, off, count - off)
                        // A seek asked for while this write was blocked: the bar already shows the new spot,
                        // the old one must not flash back on it for a frame.
                        if (seekTo >= 0) break
                        // ten times a second is smooth for the bar and the clock; every write redrew the player ~50 times
                        val t = System.nanoTime()
                        if (t - lastPosition > 100_000_000) { lastPosition = t; _position.value = base + (l.longFramePosition - frameBase) * 1000 / rate }
                        if (paused) break
                    }
                    val now = System.nanoTime()
                    // The FFT ran on every chunk (~50 a second) for meters drawn 30 times; now 20, and none while minimised.
                    if (visualsWanted) {
                        ringIn(bytes, count)
                        if (now - lastLevels > 50_000_000) { lastLevels = now; _levels.value = bands() }
                    }
                }
                if (!cancelled) {
                    l.drain()
                    _position.value = _duration.value
                    _state.value = EngineState.Ended
                    onEnded?.invoke()
                }
            } catch (t: Throwable) {
                if (!cancelled) {
                    lastError = t.message ?: t.javaClass.simpleName
                    Log.e("Audio", "playback of ${source.label} failed", t)
                    _state.value = EngineState.Error
                    onError?.invoke(_position.value)
                }
            } finally {
                runCatching { line?.close() }
                runCatching { g.stop(); g.release() }
            }
        }

        private fun openLine(): SourceDataLine {
            val info = DataLine.Info(SourceDataLine::class.java, format)
            val mixer = outputDevice?.let { name -> AudioSystem.getMixerInfo().firstOrNull { it.name == name } }
            val l = (if (mixer != null) AudioSystem.getMixer(mixer).getLine(info) else AudioSystem.getLine(info)) as SourceDataLine
            l.open(format, rate * 4 / 5) // ~200 ms
            return l
        }

        private fun interleave(buffers: Array<java.nio.Buffer>, have: Int): ByteArray {
            val first = buffers[0] as? ShortBuffer ?: return ByteArray(have)
            val need = if (buffers.size > 1) first.limit() * buffers.size * 2 else first.limit() * 2
            return if (need > have) ByteArray(need) else ByteArray(have)
        }

        /** Writes the frame as little-endian interleaved stereo with the volume applied. */
        private fun fill(buffers: Array<java.nio.Buffer>, out: ByteArray): Int {
            val gain = volume.coerceIn(0f, 1f).toDouble().pow(1.7).toFloat() * fade.coerceIn(0f, 1f)
            val bal = balance.coerceIn(-1f, 1f)
            val gainL = gain * (if (bal > 0) 1f - bal else 1f)
            val gainR = gain * (if (bal < 0) 1f + bal else 1f)
            val planar = buffers.size > 1
            val a = buffers[0] as? ShortBuffer ?: return 0
            if (!planar) {
                val n = a.limit()
                for (i in 0 until n) {
                    val v = (a.get(i) * if (i and 1 == 0) gainL else gainR).toInt().coerceIn(-32768, 32767)
                    out[i * 2] = v.toByte(); out[i * 2 + 1] = (v shr 8).toByte()
                }
                return n * 2
            }
            val b = buffers[1] as ShortBuffer
            val n = a.limit()
            for (i in 0 until n) {
                val l = (a.get(i) * gainL).toInt().coerceIn(-32768, 32767)
                val r = (b.get(i) * gainR).toInt().coerceIn(-32768, 32767)
                val o = i * 4
                out[o] = l.toByte(); out[o + 1] = (l shr 8).toByte()
                out[o + 2] = r.toByte(); out[o + 3] = (r shr 8).toByte()
            }
            return n * 4
        }

        private val ring = ByteArray(4096 * 2)
        private var ringFill = 0
        private val re = DoubleArray(1024)
        private val im = DoubleArray(1024)
        private val smooth = FloatArray(32)

        /** Keeps the last 1024 stereo frames (4 KB) of what was played: Opus frames are only 20 ms. */
        private fun ringIn(input: ByteArray, count: Int) {
            val take = minOf(count, 4096)
            if (ringFill + take > 4096) { val keep = 4096 - take; System.arraycopy(ring, ringFill - keep, ring, 0, keep); ringFill = keep }
            System.arraycopy(input, count - take, ring, ringFill, take); ringFill += take
        }

        /** 32 log-spaced bands (40 Hz – 20 kHz) from a 1024-point FFT of the mono mix. */
        private fun bands(): FloatArray {
            val buf = ring
            val n = 1024
            if (ringFill < 4096) return smooth.copyOf()
            for (i in 0 until n) {
                val o = i * 4
                val l = (buf[o + 1].toInt() shl 8) or (buf[o].toInt() and 0xff)
                val r = (buf[o + 3].toInt() shl 8) or (buf[o + 2].toInt() and 0xff)
                val w = 0.5 - 0.5 * kotlin.math.cos(2 * Math.PI * i / (n - 1))
                re[i] = (l + r) / 65536.0 * w; im[i] = 0.0
            }
            fft(re, im)
            for (b in 0 until 32) {
                val f0 = 40.0 * (20000.0 / 40.0).pow(b / 32.0)
                val f1 = 40.0 * (20000.0 / 40.0).pow((b + 1) / 32.0)
                val k0 = max(1, (f0 * n / rate).toInt())
                val k1 = max(k0 + 1, (f1 * n / rate).toInt())
                var acc = 0.0
                for (k in k0 until min(k1, n / 2)) acc = max(acc, sqrt(re[k] * re[k] + im[k] * im[k]))
                val db = 20 * kotlin.math.log10(acc + 1e-9)
                val v = ((db + 60) / 60).toFloat().coerceIn(0f, 1f)
                smooth[b] = if (v > smooth[b]) v else smooth[b] * 0.82f + v * 0.18f
            }
            return smooth.copyOf()
        }

        private fun fft(x: DoubleArray, y: DoubleArray) {
            val n = x.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
                j = j xor bit
                if (i < j) { var t = x[i]; x[i] = x[j]; x[j] = t; t = y[i]; y[i] = y[j]; y[j] = t }
            }
            var len = 2
            while (len <= n) {
                val ang = -2 * Math.PI / len
                val wr = kotlin.math.cos(ang); val wi = kotlin.math.sin(ang)
                var i = 0
                while (i < n) {
                    var cr = 1.0; var ci = 0.0
                    for (k in 0 until len / 2) {
                        val ur = x[i + k]; val ui = y[i + k]
                        val vr = x[i + k + len / 2] * cr - y[i + k + len / 2] * ci
                        val vi = x[i + k + len / 2] * ci + y[i + k + len / 2] * cr
                        x[i + k] = ur + vr; y[i + k] = ui + vi
                        x[i + k + len / 2] = ur - vr; y[i + k + len / 2] = ui - vi
                        val nr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nr
                    }
                    i += len
                }
                len = len shl 1
            }
        }
    }
}
