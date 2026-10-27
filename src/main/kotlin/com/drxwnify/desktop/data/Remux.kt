package com.drxwnify.desktop.data

import org.bytedeco.ffmpeg.avcodec.AVCodec
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avformat.AVIOContext
import org.bytedeco.ffmpeg.avformat.AVIOInterruptCB
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avformat
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacpp.Pointer
import org.bytedeco.javacpp.PointerPointer
import java.io.File

/**
 * Copies the audio of a stream (HTTP, HLS, a file) into a proper audio file without re-encoding:
 * Opus → .opus (Ogg), AAC → .m4a, MP3 → .mp3, FLAC → .flac, with title/artist/album tags.
 */
object Remux {
    data class Out(val file: File, val ext: String)

    /** No data from the stream for this long: it's given up on (FFmpeg alone would wait for ever). */
    private const val STALL_MS = 15_000L

    fun run(url: String, headers: Map<String, String>, folder: File, baseName: String, tags: Map<String, String>, cancelled: () -> Boolean = { false }, progress: (Float) -> Unit): Out {
        val inCtx = avformat.avformat_alloc_context()
        // FFmpeg asks this while it waits on the network: a cancel or a stall stops it there and then.
        var lastData = System.currentTimeMillis()
        var interrupted = false
        val interrupt = object : AVIOInterruptCB.Callback_Pointer() {
            override fun call(opaque: Pointer?): Int {
                if (cancelled() || System.currentTimeMillis() - lastData > STALL_MS) interrupted = true
                return if (interrupted) 1 else 0
            }
        }
        inCtx.interrupt_callback().callback(interrupt)
        val opts = AVDictionary(null)
        avutil.av_dict_set(opts, "user_agent", headers["User-Agent"] ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", 0)
        val extra = headers.filterKeys { it != "User-Agent" }
        if (extra.isNotEmpty()) avutil.av_dict_set(opts, "headers", extra.entries.joinToString("") { "${it.key}: ${it.value}\r\n" }, 0)
        avutil.av_dict_set(opts, "reconnect", "1", 0)
        avutil.av_dict_set(opts, "reconnect_streamed", "1", 0)
        if (avformat.avformat_open_input(inCtx, url, null, opts) < 0) {
            interrupt.close()
            error(if (interrupted && !cancelled()) "поток не отвечает" else "не открылся поток")
        }
        lastData = System.currentTimeMillis()
        var outCtx: AVFormatContext? = null
        // FFmpeg is happiest with an ASCII path; the real name is given after.
        val tmp = File(folder, "drxw-${System.nanoTime()}.part")
        try {
            check(avformat.avformat_find_stream_info(inCtx, null as PointerPointer<*>?) >= 0) { "поток не читается" }
            val ai = avformat.av_find_best_stream(inCtx, avutil.AVMEDIA_TYPE_AUDIO, -1, -1, null as AVCodec?, 0)
            check(ai >= 0) { "в потоке нет звука" }
            val inSt = inCtx.streams(ai)
            val (ext, muxer) = when (inSt.codecpar().codec_id()) {
                avcodec.AV_CODEC_ID_OPUS -> "opus" to "ogg"
                avcodec.AV_CODEC_ID_VORBIS -> "ogg" to "ogg"
                avcodec.AV_CODEC_ID_AAC -> "m4a" to "ipod"
                avcodec.AV_CODEC_ID_MP3 -> "mp3" to "mp3"
                avcodec.AV_CODEC_ID_FLAC -> "flac" to "flac"
                avcodec.AV_CODEC_ID_ALAC -> "m4a" to "ipod"
                else -> "mka" to "matroska"
            }
            val oc = AVFormatContext(null)
            check(avformat.avformat_alloc_output_context2(oc, null, muxer, tmp.absolutePath) >= 0) { "нет формата $muxer" }
            outCtx = oc
            val outSt = avformat.avformat_new_stream(oc, null)
            check(avcodec.avcodec_parameters_copy(outSt.codecpar(), inSt.codecpar()) >= 0)
            outSt.codecpar().codec_tag(0)
            outSt.time_base(inSt.time_base())
            val md = AVDictionary(null)
            tags.forEach { (k, v) -> avutil.av_dict_set(md, k, v, 0) }
            oc.metadata(md)
            val pb = AVIOContext(null)
            check(avformat.avio_open(pb, tmp.absolutePath, avformat.AVIO_FLAG_WRITE) >= 0) { "не открылся файл" }
            oc.pb(pb)
            check(avformat.avformat_write_header(oc, null as AVDictionary?) >= 0) { "не записался заголовок" }
            val pkt = avcodec.av_packet_alloc()
            val total = inCtx.duration().takeIf { it > 0 } ?: 1L
            val tb = inSt.time_base()
            var last = 0L
            var reachedUs = 0L
            var ret: Int
            while (avformat.av_read_frame(inCtx, pkt).also { ret = it } >= 0) {
                lastData = System.currentTimeMillis()
                if (cancelled()) { avcodec.av_packet_unref(pkt); avcodec.av_packet_free(pkt); error("cancelled") }
                if (pkt.stream_index() == ai) {
                    val pts = pkt.pts()
                    if (pts != avutil.AV_NOPTS_VALUE) reachedUs = (pts * 1_000_000.0 * tb.num() / tb.den()).toLong()
                    avcodec.av_packet_rescale_ts(pkt, inSt.time_base(), outSt.time_base())
                    pkt.stream_index(0); pkt.pos(-1)
                    avformat.av_interleaved_write_frame(oc, pkt)
                    val now = System.currentTimeMillis()
                    if (now - last > 250 && pts != avutil.AV_NOPTS_VALUE) {
                        last = now
                        progress((pts * 1_000_000.0 * tb.num() / tb.den() / total).toFloat().coerceIn(0f, 1f))
                    }
                }
                avcodec.av_packet_unref(pkt)
            }
            // A stream that stopped short is not a whole track: don't save it as one.
            if (interrupted) { avcodec.av_packet_free(pkt); error(if (cancelled()) "cancelled" else "поток не отвечает") }
            if (ret != avutil.AVERROR_EOF() && total > 1 && reachedUs < total * 0.97) { avcodec.av_packet_free(pkt); error("поток оборвался на ${reachedUs / 1_000_000} с") }
            avformat.av_write_trailer(oc)
            avcodec.av_packet_free(pkt)
            avformat.avio_closep(oc.pb())
            val dest = File(folder, "$baseName.$ext")
            if (dest.exists()) dest.delete()
            check(tmp.renameTo(dest)) { "не удалось сохранить файл" }
            return Out(dest, ext)
        } catch (e: Throwable) {
            outCtx?.pb()?.let { runCatching { avformat.avio_closep(outCtx.pb()) } }
            tmp.delete()
            throw e
        } finally {
            outCtx?.let { avformat.avformat_free_context(it) }
            avformat.avformat_close_input(inCtx)
            interrupt.close()
        }
    }
}
