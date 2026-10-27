package com.drxwnify.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drxwnify.desktop.data.Downloads
import com.drxwnify.desktop.data.Library
import com.drxwnify.desktop.data.Log
import com.drxwnify.desktop.data.Matches
import com.drxwnify.desktop.data.ioScope
import com.drxwnify.desktop.model.Track
import com.drxwnify.desktop.model.asDuration
import com.drxwnify.desktop.playback.AudioCache
import com.drxwnify.desktop.playback.Player
import com.drxwnify.desktop.playback.StreamResolver
import com.drxwnify.desktop.playback.VkSource
import com.drxwnify.desktop.ui.Cover
import com.drxwnify.desktop.ui.Hairline
import com.drxwnify.desktop.ui.HoverRow
import com.drxwnify.desktop.ui.Ico
import com.drxwnify.desktop.ui.IconBtn
import com.drxwnify.desktop.ui.Mono
import com.drxwnify.desktop.ui.PillButton
import com.drxwnify.desktop.ui.Spacer
import com.drxwnify.desktop.ui.Ti
import com.drxwnify.desktop.ui.Txt
import com.drxwnify.desktop.ui.theme
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.launch

/** One dialog at a time over the app (Esc or a click outside closes it). */
object Dialogs {
    var current by mutableStateOf<(@Composable ColumnScope.() -> Unit)?>(null)
        private set
    var width by mutableStateOf(440.dp)
        private set

    fun show(width: Dp = 440.dp, content: @Composable ColumnScope.() -> Unit) { this.width = width; current = content }
    fun close() { current = null }
}

@Composable
fun DialogHost() {
    val t = theme
    val content = Dialogs.current
    var last by remember { mutableStateOf(content) }
    if (content != null) last = content
    AnimatedVisibility(content != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(130))) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                .clickable(remember { MutableInteractionSource() }, null) { Dialogs.close() },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(content != null, enter = scaleIn(tween(200, easing = FastOutSlowInEasing), initialScale = 0.95f), exit = scaleOut(tween(120), targetScale = 0.97f)) {
                Column(
                    Modifier.width(Dialogs.width).heightIn(max = 640.dp).clip(RoundedCornerShape(18.dp))
                        .background(t.menu).border(1.dp, t.panelBorder, RoundedCornerShape(18.dp))
                        .clickable(remember { MutableInteractionSource() }, null) { }
                        .padding(20.dp),
                ) { last?.invoke(this) }
            }
        }
    }
}

@Composable
private fun DialogTitle(title: String, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(title, size = 18.sp, weight = FontWeight.ExtraBold)
            subtitle?.let { Mono(it, size = 11.sp) }
        }
        IconBtn(Ti.x, { Dialogs.close() }, size = 30.dp, iconSize = 15.sp)
    }
    Spacer(h = 14.dp)
}

// ── Добавить в плейлист ──

fun showAddToPlaylist(tracks: List<Track>) = Dialogs.show {
    val t = theme
    val lib by Library.state.collectAsState()
    var name by remember { mutableStateOf("") }
    DialogTitle("Добавить в плейлист", if (tracks.size == 1) "${tracks[0].artistLine} — ${tracks[0].title}" else "${tracks.size} ${plural(tracks.size, "трек", "трека", "треков")}")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(t.fill).border(1.dp, t.panelBorder, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (name.isEmpty()) Txt("Новый плейлист…", size = 13.sp, color = t.mute)
            BasicTextField(name, { name = it }, singleLine = true, textStyle = TextStyle(color = t.text, fontSize = 13.sp, fontFamily = t.body), cursorBrush = SolidColor(t.accent), modifier = Modifier.fillMaxWidth())
        }
        Spacer(w = 10.dp)
        PillButton("Создать", Ti.plus, primary = true, onClick = {
            val p = Library.createPlaylist(name.trim().ifBlank { "Новый плейлист ${lib.playlists.size + 1}" })
            tracks.forEach { Library.addToPlaylist(p.id, it) }
            Toast.show("Создан «${p.name}»"); Dialogs.close()
        })
    }
    Spacer(h = 12.dp)
    Column(Modifier.verticalScroll(rememberScrollState())) {
        lib.playlists.forEach { p ->
            val has = tracks.all { tr -> p.tracks.any { it.id == tr.id } }
            HoverRow(onClick = {
                if (has) { Toast.show("Уже в «${p.name}»"); return@HoverRow }
                tracks.forEach { Library.addToPlaylist(p.id, it) }
                Toast.show("Добавлено в «${p.name}»"); Dialogs.close()
            }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) { _ ->
                Cover(p.cover ?: p.tracks.firstOrNull()?.cover, Modifier.size(40.dp), radius = 7.dp)
                Spacer(w = 12.dp)
                Column(Modifier.weight(1f)) {
                    Txt(p.name, size = 14.sp, weight = FontWeight.SemiBold)
                    Mono("${p.tracks.size} ${plural(p.tracks.size, "трек", "трека", "треков")}", size = 11.sp)
                }
                if (has) Ico(Ti.check, size = 16.sp, color = t.accent)
            }
        }
        if (lib.playlists.isEmpty()) Txt("Своих плейлистов пока нет — создай первый выше.", size = 13.sp, color = t.dim)
    }
}

// ── О треке ──

fun showTrackInfo(track: Track) = Dialogs.show(480.dp) {
    val t = theme
    val now by Player.now.collectAsState()
    val bitrate by Player.engine.bitrate.collectAsState()
    val playing = now.track?.id == track.id
    val match = Matches.of(track)
    val file = Downloads.fileFor(track.id)
    val cached = AudioCache.lookup(track.id)
    DialogTitle("О треке")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Cover(track.cover, Modifier.size(64.dp), radius = 10.dp)
        Spacer(w = 14.dp)
        Column {
            Txt(track.title, size = 16.sp, weight = FontWeight.Bold, maxLines = 2)
            Txt(track.artistLine, size = 13.sp, color = t.dim)
        }
    }
    Spacer(h = 14.dp)
    Hairline()
    Spacer(h = 10.dp)
    fun row(k: String, v: String?) = k to v
    val rows = listOfNotNull(
        row("Альбом", track.album?.title?.takeIf { it.isNotBlank() }),
        row("Длительность", track.durationSec?.asDuration()),
        row("Каталог", when { track.id.startsWith("mfb:") || track.id.startsWith("spotify:") -> "Spotify"; track.youtubeId != null -> "YouTube Music"; else -> null }),
        row("Сейчас играет из", if (playing) listOfNotNull(now.provider, now.quality, bitrate.takeIf { it > 0 }?.let { "$it кбит/с" }).joinToString(" · ") else null),
        row("Версия выбрана вручную", when (match?.chosen) { "vk" -> "VK"; "yt" -> "YouTube"; else -> null }),
        row("VK", match?.vk?.split('_')?.take(2)?.joinToString("_")?.let { "audio$it" }),
        row("YouTube", (match?.yt ?: track.youtubeId)?.let { "youtu.be/$it" }),
        row("Файл", file?.absolutePath),
        row("В кэше", cached?.let { "${it.first.length() / 1024 / 1024} МБ · ${it.second.provider} ${it.second.quality}" }),
        row("ID", track.id),
    ).filter { it.second != null }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        rows.forEach { (k, v) ->
            Row {
                Txt(k, size = 12.sp, color = t.mute, modifier = Modifier.width(150.dp))
                Txt(v!!, size = 12.sp, maxLines = 2, modifier = Modifier.weight(1f))
            }
        }
    }
    Spacer(h = 16.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("Другая версия", Ti.arrowsRightLeft, onClick = { showVersions(track) })
        file?.let { f -> PillButton("Показать файл", Ti.folderOpen, onClick = { revealFile(f) }) }
        com.drxwnify.desktop.catalog.AppLinks.forTrack(track)?.let { text ->
            PillButton("Ссылка", Ti.share, onClick = { com.drxwnify.desktop.catalog.AppLinks.copy(text); Toast.show("Ссылка скопирована") })
        }
    }
}

/** Opens Explorer with the file selected. */
fun revealFile(f: java.io.File) {
    runCatching { ProcessBuilder("explorer.exe", "/select,", f.absolutePath).start() }
        .onFailure { runCatching { java.awt.Desktop.getDesktop().open(f.parentFile) } }
}

// ── Другая версия ──

private data class Version(val provider: String, val id: String, val title: String, val artist: String, val durationSec: Int?)

/** The phone's "track version": every upload that could be this track, VK and YouTube; the pick sticks. */
fun showVersions(track: Track) = Dialogs.show(520.dp) {
    val t = theme
    var list by remember(track.id) { mutableStateOf<List<Version>?>(null) }
    LaunchedEffect(track.id) {
        val q = "${track.artists.firstOrNull()?.name.orEmpty()} ${track.title}".trim()
        val vk = runCatching { VkSource.candidates(track).take(12).map { Version("VK", it.id, it.title, it.artist, it.durationSec.takeIf { d -> d > 0 }) } }.getOrDefault(emptyList())
        val yt = runCatching {
            (YouTube.search(q, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items.orEmpty() + YouTube.search(q, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()?.items.orEmpty())
                .filterIsInstance<SongItem>().distinctBy { it.id }.take(12)
                .map { Version("YouTube", it.id, it.title, it.artists.joinToString(", ") { a -> a.name }, it.duration) }
        }.getOrDefault(emptyList())
        list = vk + yt
    }
    val match = Matches.of(track)
    DialogTitle("Другая версия", "${track.artistLine} — ${track.title}${track.durationSec?.let { " · " + it.asDuration() } ?: ""}")
    Txt("Если подобралась не та запись (ремикс, лайв, чужая песня) — выбери нужную. Выбор запомнится.", size = 12.sp, color = t.dim, maxLines = 3)
    Spacer(h = 10.dp)
    val l = list
    if (l == null) { Loading("Ищу записи…"); return@show }
    if (l.isEmpty()) { Txt("Ничего не нашлось.", size = 13.sp, color = t.dim); return@show }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        l.forEach { v ->
            // VK ids are "owner_audio_accesskey"; the key differs per token, the audio doesn't.
            fun audio(id: String?) = id?.split('_')?.take(2)?.joinToString("_")
            val current = (v.provider == "VK" && audio(match?.vk) == audio(v.id)) || (v.provider == "YouTube" && (match?.yt ?: track.youtubeId) == v.id)
            val off = track.durationSec?.let { want -> v.durationSec?.let { kotlin.math.abs(it - want) } }
            HoverRow(onClick = {
                if (v.provider == "VK") Matches.choose(track, vk = v.id) else Matches.choose(track, yt = v.id)
                StreamResolver.forget(track.id)
                AudioCache.forget(track.id)
                Dialogs.close()
                Toast.show("Теперь играет эта версия")
                if (Player.now.value.track?.id == track.id) Player.playAt(Player.index.value, Player.engine.position.value)
            }, active = current, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) { _ ->
                Box(Modifier.width(64.dp)) { Mono(v.provider, size = 10.sp, color = if (v.provider == "VK") t.accent else t.dim) }
                Column(Modifier.weight(1f)) {
                    Txt(v.title, size = 13.sp, weight = FontWeight.SemiBold, maxLines = 1)
                    Txt(v.artist, size = 11.sp, color = t.dim, maxLines = 1)
                }
                Mono(v.durationSec?.asDuration() ?: "", size = 11.sp, color = if (off != null && off > 7) t.accent2 else t.mute)
                if (current) { Spacer(w = 8.dp); Ico(Ti.check, size = 15.sp, color = t.accent) }
            }
        }
    }
}

// ── Таймер сна ──

fun showSleepTimer() = Dialogs.show(360.dp) {
    val t = theme
    val at by Player.sleepAt.collectAsState()
    val afterTrack by Player.sleepAfterTrack.collectAsState()
    DialogTitle("Таймер сна", at?.let { "остановится в " + java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalTime().withNano(0).toString().take(5) }
        ?: if (afterTrack) "остановится после этого трека" else "музыка плавно затихнет и встанет на паузу")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(5, 15, 30, 45, 60, 90).forEach { m ->
            HoverRow(onClick = { Player.sleepIn(m); Toast.show("Пауза через $m мин"); Dialogs.close() }, modifier = Modifier.fillMaxWidth()) { _ ->
                Ico(Ti.moon, size = 15.sp, color = t.dim); Spacer(w = 12.dp); Txt("Через $m мин", size = 14.sp)
            }
        }
        HoverRow(onClick = { Player.sleepAfterThisTrack(); Toast.show("Остановлю после этого трека"); Dialogs.close() }, active = afterTrack, modifier = Modifier.fillMaxWidth()) { _ ->
            Ico(Ti.playerTrackNext, size = 15.sp, color = t.dim); Spacer(w = 12.dp); Txt("Когда закончится трек", size = 14.sp)
        }
        if (at != null || afterTrack) HoverRow(onClick = { Player.cancelSleep(); Toast.show("Таймер выключен"); Dialogs.close() }, modifier = Modifier.fillMaxWidth()) { _ ->
            Ico(Ti.x, size = 15.sp, color = t.accent2); Spacer(w = 12.dp); Txt("Выключить таймер", size = 14.sp, color = t.accent2)
        }
    }
}
