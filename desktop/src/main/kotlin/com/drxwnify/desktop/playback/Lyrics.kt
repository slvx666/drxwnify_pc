package com.drxwnify.desktop.playback

import com.drxwnify.desktop.model.Track
import com.metrolist.lrclib.LrcLib
import java.util.concurrent.ConcurrentHashMap

data class LyricLine(val ms: Long, val text: String)
data class Lyrics(val synced: Boolean, val lines: List<LyricLine>)

/** Lyrics from LRCLIB (synced when possible), cached per track. */
object LyricsService {
    private val cache = ConcurrentHashMap<String, Lyrics>()
    private val misses = ConcurrentHashMap.newKeySet<String>()

    suspend fun get(track: Track): Lyrics? {
        cache[track.id]?.let { return it }
        if (track.id in misses) return null
        val text = LrcLib.getLyrics(track.title, track.artists.firstOrNull()?.name ?: "", track.durationSec ?: -1, track.album?.title).getOrNull()
        val parsed = text?.let(::parse)
        if (parsed == null || parsed.lines.isEmpty()) { misses += track.id; return null }
        cache[track.id] = parsed
        return parsed
    }

    private val tag = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?]""")

    fun parse(text: String): Lyrics {
        val synced = mutableListOf<LyricLine>()
        val plain = mutableListOf<LyricLine>()
        text.lines().forEach { raw ->
            val stamps = tag.findAll(raw).toList()
            val words = raw.replace(tag, "").trim()
            if (stamps.isEmpty()) { if (words.isNotBlank()) plain += LyricLine(-1, words); return@forEach }
            stamps.forEach { m ->
                val min = m.groupValues[1].toLong(); val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3].let { if (it.isEmpty()) 0L else it.padEnd(3, '0').take(3).toLong() }
                synced += LyricLine(min * 60_000 + sec * 1000 + frac, words)
            }
        }
        return if (synced.isNotEmpty()) Lyrics(true, synced.sortedBy { it.ms }) else Lyrics(false, plain)
    }
}
