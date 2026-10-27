package com.drxwnify.desktop.playback

import com.drxwnify.desktop.catalog.YouTubeCatalog
import com.drxwnify.desktop.data.Library
import com.drxwnify.desktop.data.Log
import com.drxwnify.desktop.data.Prefs
import com.drxwnify.desktop.data.Store
import com.drxwnify.desktop.data.ioScope
import com.drxwnify.desktop.model.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

enum class RepeatMode { Off, All, One }

/** A stream's quality with the bitrate first: "MP3 320" → "320 kbps · mp3". */
fun qualityLabel(q: String?): String? {
    val s = q?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val m = Regex("^(.*?)\\s*(\\d{2,4})$").find(s) ?: return s.lowercase()
    val (format, kbps) = m.destructured
    return "$kbps kbps · ${format.trim().lowercase()}"
}

@Serializable
data class SavedQueue(val tracks: List<Track> = emptyList(), val index: Int = 0, val positionMs: Long = 0)

data class NowPlaying(
    val track: Track? = null,
    val quality: String? = null,
    val provider: String? = null,
    val error: String? = null,
)

/** The queue and what's playing — one per app. */
object Player {
    val engine = AudioEngine()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue
    private val _index = MutableStateFlow(-1)
    val index: StateFlow<Int> = _index
    private val _now = MutableStateFlow(NowPlaying())
    val now: StateFlow<NowPlaying> = _now
    val shuffle = MutableStateFlow(false)
    val repeat = MutableStateFlow(RepeatMode.Off)
    /** Where the queue came from, shown above the cover ("Играет из …"). */
    val context = MutableStateFlow<String?>(null)

    private val saved = Store("queue.json", SavedQueue.serializer()) { SavedQueue() }
    private var loadJob: Job? = null
    private var fillingRadio = false

    fun init() {
        // Scripted test runs (DRXW_SCRIPT) play silently; the saved volume stays as it was.
        engine.volume = if (System.getenv("DRXW_SCRIPT") != null) 0f else Prefs.settings.value.volume
        engine.balance = Prefs.settings.value.balance
        engine.outputDevice = Prefs.settings.value.outputDevice
        engine.onEnded = { ioScope.launch { onTrackEnded() } }
        engine.onError = { at -> ioScope.launch { onStreamFailed(at) } }
        val s = saved.value
        if (s.tracks.isNotEmpty()) {
            _queue.value = s.tracks
            _index.value = s.index.coerceIn(0, s.tracks.lastIndex)
            _now.value = NowPlaying(s.tracks[_index.value])
            restorePosition = s.positionMs
        }
        ioScope.launch {
            while (true) {
                delay(3000)
                val q = _queue.value
                if (q.isNotEmpty()) saved.update { SavedQueue(q.take(500), _index.value, engine.position.value) }
            }
        }
    }

    private var restorePosition = 0L

    fun play(tracks: List<Track>, start: Int = 0, from: String? = null) {
        if (tracks.isEmpty()) return
        val list = if (shuffle.value) shuffled(tracks, start) else tracks
        _queue.value = list
        context.value = from
        playAt(if (shuffle.value) 0 else start.coerceIn(0, list.lastIndex))
    }

    private fun shuffled(tracks: List<Track>, start: Int): List<Track> {
        val first = tracks[start.coerceIn(0, tracks.lastIndex)]
        return listOf(first) + (tracks - first).shuffled()
    }

    fun playAt(i: Int, startMs: Long = 0) {
        val q = _queue.value
        if (i !in q.indices) return
        _index.value = i
        val t = q[i]
        _now.value = NowPlaying(t)
        loadJob?.cancel()
        // The old sound stops now and the new track shows as loading while its source is looked for.
        engine.stop()
        engine.preparing()
        val asked = System.currentTimeMillis()
        loadJob = ioScope.launch {
            runCatching { StreamResolver.resolve(t) }
                .onSuccess { r ->
                    if (_index.value != i) return@launch
                    Log.d("Player", "${t.title}: source found in ${System.currentTimeMillis() - asked} ms (${r.provider} ${r.quality})")
                    _now.value = NowPlaying(t, r.quality, r.provider)
                    engine.load(r.source, startMs, play = true)
                    cacheWhole(t, r)
                    if (r.quality == "MP3") launch {
                        repeat(120) {
                            val kbps = engine.bitrate.value
                            if (kbps > 0 && _index.value == i) { _now.value = _now.value.copy(quality = "MP3 $kbps"); return@launch }
                            delay(250)
                        }
                    }
                    Library.played(t)
                    maybeExtend()
                }
                .onFailure { e ->
                    // Another track (or the same one again) was asked for meanwhile: not an error.
                    if (e is kotlinx.coroutines.CancellationException) return@launch
                    Log.w("Player", "can't play ${t.title}", e)
                    engine.stop()
                    _now.value = NowPlaying(t, error = e.message ?: "не получилось включить")
                    delay(1500)
                    if (_index.value == i && i < _queue.value.lastIndex) playAt(i + 1)
                }
        }
    }

    /** Developer harness only (DebugScript "stall:<url>"): plays [url] for the current track as if [provider] gave it. */
    internal fun debugPlayAs(url: String, provider: String) {
        val t = _now.value.track ?: return
        loadJob?.cancel()
        _now.value = NowPlaying(t, "MP3", provider)
        engine.load(Source(url, label = "${t.artistLine} — ${t.title} (test)"), 0, play = true)
    }

    /** Saves the stream whole alongside playing it; the engine seeks in the copy once it's there. */
    private fun cacheWhole(t: Track, r: Resolved) {
        val key = r.source.cacheKey ?: return
        AudioCache.prefetch(t.id, r.source, AudioCache.Meta(r.quality, r.provider)) { f -> engine.attachLocal(key, f) }
    }

    /**
     * A VK stream that broke or hung (its CDN stopped answering) goes on from YouTube at the same spot,
     * and that CDN host is passed over for a while. Any other stream: the error is shown, not a spinner.
     */
    private fun onStreamFailed(at: Long) {
        val i = _index.value
        val t = _queue.value.getOrNull(i) ?: return
        if (_now.value.track?.id != t.id) return
        if (_now.value.provider != "VK") {
            _now.value = _now.value.copy(error = engine.lastError ?: "поток оборвался")
            return
        }
        Log.w("Player", "VK stream of ${t.title} broke at ${at / 1000}s — switching to YouTube")
        engine.source?.url?.let { CdnHosts.markDown(it) }
        StreamResolver.forget(t.id)
        loadJob?.cancel()
        loadJob = ioScope.launch {
            runCatching { StreamResolver.resolve(t, skipVk = true) }
                .onSuccess { r -> if (_index.value == i) { _now.value = NowPlaying(t, r.quality, r.provider); engine.load(r.source, at, play = true); cacheWhole(t, r) } }
                .onFailure { e -> if (e !is kotlinx.coroutines.CancellationException) _now.value = NowPlaying(t, error = e.message ?: "не получилось включить") }
        }
    }

    fun toggle() {
        when (engine.state.value) {
            EngineState.Playing -> engine.pause()
            EngineState.Paused -> engine.resume()
            EngineState.Idle, EngineState.Ended, EngineState.Error -> if (_index.value >= 0) playAt(_index.value, restorePosition).also { restorePosition = 0 }
            EngineState.Loading -> Unit
        }
    }

    fun next() {
        val q = _queue.value
        when {
            _index.value < q.lastIndex -> playAt(_index.value + 1)
            repeat.value == RepeatMode.All && q.isNotEmpty() -> playAt(0)
        }
    }

    fun previous() {
        if (engine.position.value > 4000 || _index.value <= 0) engine.seek(0) else playAt(_index.value - 1)
    }

    fun seek(ms: Long) = engine.seek(ms)

    fun setVolume(v: Float) {
        // test runs stay silent whatever the saved volume says
        engine.volume = if (com.drxwnify.desktop.TestRun.active) 0f else v
        Prefs.settings.update { it.copy(volume = v, lastVolume = if (v >= 0.01f) v else it.lastVolume) }
    }

    /** Sound off, or back to how loud it was before (not to some fixed level). */
    fun toggleMute() {
        val s = Prefs.settings.value
        if (s.volume < 0.01f) setVolume(s.lastVolume.coerceIn(0.01f, 1f))
        else { Prefs.settings.update { it.copy(lastVolume = it.volume) }; setVolume(0f) }
    }

    fun addNext(t: Track) {
        val q = _queue.value.toMutableList()
        val at = (_index.value + 1).coerceAtMost(q.size)
        q.add(at, t)
        _queue.value = q
        if (_index.value < 0) playAt(0)
    }

    fun addToQueue(t: Track) {
        _queue.value = _queue.value + t
        if (_index.value < 0) playAt(0)
    }

    fun removeAt(i: Int) {
        if (i == _index.value) return
        val q = _queue.value.toMutableList()
        if (i !in q.indices) return
        q.removeAt(i)
        _queue.value = q
        if (i < _index.value) _index.value -= 1
    }

    fun move(from: Int, to: Int) {
        val q = _queue.value.toMutableList()
        if (from !in q.indices || to !in q.indices) return
        val t = q.removeAt(from); q.add(to, t)
        _queue.value = q
        val cur = _index.value
        _index.value = when {
            from == cur -> to
            from < cur && to >= cur -> cur - 1
            from > cur && to <= cur -> cur + 1
            else -> cur
        }
    }

    fun toggleShuffle() {
        shuffle.value = !shuffle.value
        val q = _queue.value
        val i = _index.value
        if (shuffle.value && i >= 0 && q.size > 2) {
            _queue.value = q.take(i + 1) + q.drop(i + 1).shuffled()
        }
    }

    fun cycleRepeat() {
        repeat.value = when (repeat.value) { RepeatMode.Off -> RepeatMode.All; RepeatMode.All -> RepeatMode.One; RepeatMode.One -> RepeatMode.Off }
    }

    /** "∞ радио": an endless queue from one track. */
    fun radio(seed: Track) {
        play(listOf(seed), 0, "Радио: ${seed.title}")
    }

    /**
     * Radio from the playing track without cutting it off: once the list is built it takes the place of
     * what was queued after the current track. Built too late (this track ended meanwhile) — it comes
     * after the track playing by then. [onReady] gets how many tracks were lined up (0 — none found).
     */
    fun radioAfterCurrent(onReady: (Int) -> Unit = {}) {
        val seed = _now.value.track ?: return
        ioScope.launch {
            val more = runCatching { com.drxwnify.desktop.catalog.Catalog.radio(seed) }.onFailure { Log.w("Player", "radio failed", it) }
                .getOrDefault(emptyList()).filter { it.id != seed.id }.distinctBy { it.id }.take(30)
            if (more.isNotEmpty()) {
                val i = _index.value.coerceAtLeast(0)
                _queue.value = _queue.value.take(i + 1) + more
                context.value = "Радио: ${seed.title}"
            }
            onReady(more.size)
        }
    }

    /** An album or playlist is being opened to play: the old sound stops and it shows as loading at once. */
    fun preparing(title: String, subtitle: String, cover: String?) {
        loadJob?.cancel()
        engine.stop()
        engine.preparing()
        _now.value = NowPlaying(Track(id = "pending:$title", title = title, artists = listOf(com.drxwnify.desktop.model.ArtistRef(subtitle)), cover = cover))
    }

    fun setBalance(v: Float) {
        engine.balance = v
        Prefs.settings.update { it.copy(balance = v) }
    }

    // ── sleep timer (the phone's: minutes with a fade-out, or "when this track ends") ──

    /** When playback stops (epoch ms); null — no timer. */
    val sleepAt = MutableStateFlow<Long?>(null)
    val sleepAfterTrack = MutableStateFlow(false)
    private var sleepJob: Job? = null

    fun sleepIn(minutes: Int) {
        cancelSleep()
        val at = System.currentTimeMillis() + minutes * 60_000L
        sleepAt.value = at
        sleepJob = ioScope.launch {
            while (true) {
                val left = at - System.currentTimeMillis()
                if (left <= 0) break
                // The last 20 seconds fade out, as on the phone.
                engine.fade = if (left < 20_000) left / 20_000f else 1f
                delay(if (left < 20_000) 100 else 1000)
            }
            engine.pause()
            engine.fade = 1f
            sleepAt.value = null
            Log.i("Player", "sleep timer: paused")
        }
    }

    fun sleepAfterThisTrack() { cancelSleep(); sleepAfterTrack.value = true }

    fun cancelSleep() {
        sleepJob?.cancel(); sleepJob = null
        engine.fade = 1f
        sleepAt.value = null
        sleepAfterTrack.value = false
    }

    private suspend fun onTrackEnded() {
        if (sleepAfterTrack.value) { sleepAfterTrack.value = false; Log.i("Player", "sleep timer: stopped after the track"); return }
        if (repeat.value == RepeatMode.One) { playAt(_index.value); return }
        if (_index.value < _queue.value.lastIndex) playAt(_index.value + 1)
        else if (repeat.value == RepeatMode.All) playAt(0)
        else if (Prefs.settings.value.endlessQueue) { extendNow(); if (_index.value < _queue.value.lastIndex) playAt(_index.value + 1) }
    }

    private fun maybeExtend() {
        if (!Prefs.settings.value.endlessQueue) return
        if (_queue.value.size - _index.value > 3) return
        ioScope.launch { extendNow() }
    }

    private suspend fun extendNow() {
        if (fillingRadio) return
        val seed = _queue.value.getOrNull(_index.value) ?: return
        fillingRadio = true
        try {
            val have = _queue.value.map { it.id }.toSet()
            val more = com.drxwnify.desktop.catalog.Catalog.radio(seed).filter { it.id !in have }.take(25)
            if (more.isNotEmpty()) _queue.value = _queue.value + more
        } catch (e: Exception) {
            Log.w("Player", "radio failed", e)
        } finally { fillingRadio = false }
    }
}
