package com.drxwnify.desktop.ui.screens

import com.drxwnify.desktop.ui.ArtistLinks
import com.drxwnify.desktop.ui.LinkText
import com.drxwnify.desktop.ui.artShape
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import com.drxwnify.desktop.ui.Label
import com.drxwnify.desktop.ui.NeonLcd
import com.drxwnify.desktop.ui.Reflection
import com.drxwnify.desktop.ui.Astrolabe
import com.drxwnify.desktop.ui.ThemedArt
import com.drxwnify.desktop.ui.theme.Look
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drxwnify.desktop.data.Library
import com.drxwnify.desktop.model.Track
import com.drxwnify.desktop.model.asDuration
import com.drxwnify.desktop.playback.Lyrics
import com.drxwnify.desktop.playback.LyricsService
import com.drxwnify.desktop.playback.Player
import com.drxwnify.desktop.ui.Cover
import com.drxwnify.desktop.ui.Hairline
import com.drxwnify.desktop.ui.HoverRow
import com.drxwnify.desktop.ui.Ico
import com.drxwnify.desktop.ui.IconBtn
import com.drxwnify.desktop.ui.Mono
import com.drxwnify.desktop.ui.Nav
import com.drxwnify.desktop.ui.Panel
import com.drxwnify.desktop.ui.RightPane
import com.drxwnify.desktop.ui.Screen
import com.drxwnify.desktop.ui.Spacer
import com.drxwnify.desktop.ui.Ti
import com.drxwnify.desktop.ui.Txt
import com.drxwnify.desktop.ui.clickHand
import com.drxwnify.desktop.ui.theme

@Composable
fun RightPanel(modifier: Modifier) {
    val title = when (Nav.right) { RightPane.NowPlaying -> "сейчас играет"; RightPane.Queue -> "очередь"; RightPane.Lyrics -> "текст" }
    Panel(modifier, title = title) {
        AnimatedContent(Nav.right, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) }) { pane ->
            when (pane) {
                RightPane.NowPlaying -> NowPlayingPane()
                RightPane.Queue -> QueuePane()
                RightPane.Lyrics -> LyricsPane()
            }
        }
    }
}

@Composable
private fun PaneHeader(title: String, sub: String? = null) {
    val t = theme
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            when (t.look) {
                // the window's title bar already says it
                Look.Terminal -> Unit
                Look.Neon, Look.Atlas -> Label(title, size = 13.sp, color = t.text)
                Look.Blossom -> Txt(title, size = 19.sp, weight = FontWeight.SemiBold, family = t.heading)
                else -> Txt(title, size = 15.5.sp, weight = FontWeight.ExtraBold, family = t.heading)
            }
            if (sub != null) Mono(sub, size = 12.sp)
        }
        IconBtn(Ti.x, { com.drxwnify.desktop.data.Prefs.settings.update { it.copy(rightHidden = true) } }, size = 30.dp, iconSize = 15.sp, tooltip = "Скрыть панель")
    }
}

/** Gruvbox: what's playing as `key : value` lines, the way a terminal player prints it. */
@Composable
private fun TermFacts(track: Track, now: com.drxwnify.desktop.playback.NowPlaying) {
    val t = theme
    val state by Player.engine.state.collectAsState()
    Column {
        listOf(
            "artist" to track.artistLine,
            "album" to (track.album?.title?.takeIf { it.isNotBlank() } ?: "—"),
            "source" to (now.provider?.lowercase()?.let { p -> listOfNotNull(p, com.drxwnify.desktop.playback.qualityLabel(now.quality)).joinToString(" · ") }
                ?: if (now.error != null) "нет: ${now.error}" else if (state == com.drxwnify.desktop.playback.EngineState.Loading) "ищу…" else "—"),
            "state" to state.name.lowercase(),
        ).forEach { (k, v) ->
            Row {
                Mono(k.padEnd(7) + ":", color = t.accent)
                Spacer(w = 6.dp)
                Mono(v, color = t.text)
            }
        }
    }
}

@Composable
private fun NowPlayingPane() {
    val t = theme
    val now by Player.now.collectAsState()
    val queue by Player.queue.collectAsState()
    val index by Player.index.collectAsState()
    val ctx by Player.context.collectAsState()
    val lib by Library.state.collectAsState()
    val track = now.track
    Column(Modifier.fillMaxSize()) {
        PaneHeader("Сейчас играет", ctx?.let { "из «$it»" })
        if (track == null) {
            Empty(Ti.music, "Тишина", "Выбери что-нибудь на главной — оно появится здесь.")
            return@Column
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            AnimatedContent(track.cover, transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(250)) }) { c ->
                androidx.compose.foundation.ContextMenuArea(items = { trackMenu(track) }) {
                    val open = Modifier.clickHand { CoverViewer.show(c, "${track.artistLine} — ${track.title}") }
                    val box = Modifier.fillMaxWidth().aspectRatio(if (t.look == Look.Quilt) 0.86f else 1f).padding(top = 6.dp)
                    Column {
                        when (t.look) {
                            Look.Atlas -> Astrolabe(c, box.then(open))
                            Look.Quilt -> ThemedArt(c, box.then(open), seed = track.id.hashCode(), caption = track.title)
                            Look.Glass -> Cover(c, box.then(open))
                            else -> ThemedArt(c, box.then(open), seed = track.id.hashCode())
                        }
                        if (t.look == Look.Island) Reflection(c)
                        if (t.look == Look.Neon) { Spacer(h = 12.dp); NeonLcd() }
                    }
                }
            }
            Spacer(h = 16.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt(track.title, size = 21.sp, weight = t.headingWeight, family = t.heading, letterSpacing = (-0.4).sp, maxLines = 2)
                    ArtistLinks(track.artists, size = 14.sp)
                }
                com.drxwnify.desktop.catalog.AppLinks.forTrack(track)?.let { text ->
                    IconBtn(Ti.share, { com.drxwnify.desktop.catalog.AppLinks.copy(text); Toast.show("Ссылка скопирована") }, tint = t.dim, tooltip = "Поделиться")
                }
                if (!track.id.startsWith("pending:")) {
                    val liked = lib.liked.any { it.id == track.id }
                    IconBtn(if (liked) Ti.heartFilled else Ti.heart, { Library.toggleLike(track) }, active = liked, tint = if (liked) t.accent else t.dim)
                }
            }
            Spacer(h = 6.dp)
            if (t.look == Look.Terminal) TermFacts(track, now) else Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    now.error != null -> Mono("не играет: ${now.error}", size = 12.sp, color = t.accent2)
                    now.provider != null -> Mono(listOfNotNull(now.provider?.lowercase(), com.drxwnify.desktop.playback.qualityLabel(now.quality)).joinToString(" · "), size = 12.sp)
                    Player.engine.state.value == com.drxwnify.desktop.playback.EngineState.Loading -> Mono("ищу источник…", size = 12.sp)
                    else -> Mono("нажми ▶, чтобы продолжить", size = 12.sp)
                }
            }
            val next = queue.getOrNull(index + 1)
            if (next != null) {
                Spacer(h = 18.dp); Hairline(); Spacer(h = 14.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("ДАЛЕЕ", size = 12.sp, weight = FontWeight.ExtraBold, color = t.mute, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
                    LinkText("Очередь", { Nav.right = RightPane.Queue }, size = 13.sp, weight = FontWeight.SemiBold, color = t.dim)
                }
                Spacer(h = 10.dp)
                HoverRow({ Player.playAt(index + 1) }, Modifier.fillMaxWidth()) {
                    Cover(next.cover, Modifier.padding(4.dp).size(48.dp), shape = artShape(small = true))
                    Spacer(w = 8.dp)
                    Column(Modifier.weight(1f)) { Txt(next.title, size = 14.5.sp, weight = FontWeight.Bold); Txt(next.artistLine, size = 13.sp, color = t.mute) }
                    Ico(Ti.playerSkipForward, size = 15.sp, color = t.mute)
                    Spacer(w = 8.dp)
                }
            }
            val lyrics by produceState<Lyrics?>(null, track.id) { value = LyricsService.get(track) }
            lyrics?.let { l ->
                Spacer(h = 18.dp); Hairline(); Spacer(h = 14.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("ТЕКСТ", size = 11.sp, weight = FontWeight.ExtraBold, color = t.mute, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
                    Txt("Весь текст", size = 12.sp, weight = FontWeight.SemiBold, color = t.dim, modifier = Modifier.clickHand { Nav.right = RightPane.Lyrics })
                }
                Spacer(h = 10.dp)
                val pos by Player.engine.position.collectAsState()
                val cur = if (l.synced) l.lines.indexOfLast { it.ms <= pos }.coerceAtLeast(0) else 0
                l.lines.drop(cur).take(3).forEachIndexed { i, line ->
                    Txt(line.text.ifBlank { "♪" }, size = if (i == 0) 15.sp else 13.sp, weight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (i == 0) t.accent else t.mute, maxLines = 2, lineHeight = 20.sp)
                    Spacer(h = 4.dp)
                }
            }
            Spacer(h = 20.dp)
        }
    }
}

@Composable
private fun QueuePane() {
    val t = theme
    val queue by Player.queue.collectAsState()
    val index by Player.index.collectAsState()
    val state = rememberLazyListState()
    LaunchedEffect(Unit) { if (index > 0) state.scrollToItem((index - 1).coerceAtLeast(0)) }
    Column(Modifier.fillMaxSize()) {
        PaneHeader("Очередь", "${queue.size} ${plural(queue.size, "трек", "трека", "треков")}")
        if (queue.isEmpty()) { Empty(Ti.list, "Очередь пуста", "Правый клик по треку → «Играть дальше»."); return@Column }
        // Drag a row up or down to move it; the rows it passes make way at once.
        var dragging by remember { mutableStateOf(-1) }
        var dragY by remember { mutableStateOf(0f) }
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 8.dp).pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { at ->
                        dragging = state.layoutInfo.visibleItemsInfo.firstOrNull { at.y.toInt() in it.offset..(it.offset + it.size) }?.index ?: -1
                        dragY = 0f
                    },
                    onDrag = { change, amount ->
                        if (dragging < 0) return@detectDragGestures
                        change.consume()
                        dragY += amount.y
                        val items = state.layoutInfo.visibleItemsInfo
                        val cur = items.firstOrNull { it.index == dragging } ?: return@detectDragGestures
                        val middle = cur.offset + dragY + cur.size / 2f
                        val over = items.firstOrNull { it.index != dragging && middle.toInt() in it.offset..(it.offset + it.size) }
                        if (over != null) {
                            Player.move(dragging, over.index)
                            dragY += cur.offset - over.offset
                            dragging = over.index
                        }
                    },
                    onDragEnd = { dragging = -1; dragY = 0f },
                    onDragCancel = { dragging = -1; dragY = 0f },
                )
            },
            state = state,
        ) {
            itemsIndexed(queue, key = { i, tr -> "$i:${tr.id}" }) { i, tr ->
                val past = i < index
                val lifted = i == dragging
                ContextMenuArea(items = {
                    listOf(
                        ContextMenuItem("Играть") { Player.playAt(i) },
                        ContextMenuItem("Убрать из очереди") { Player.removeAt(i) },
                        ContextMenuItem("Поднять выше") { Player.move(i, (i - 1).coerceAtLeast(0)) },
                        ContextMenuItem("Опустить ниже") { Player.move(i, (i + 1).coerceAtMost(queue.lastIndex)) },
                    )
                }) {
                    HoverRow(
                        { Player.playAt(i) },
                        Modifier.fillMaxWidth().padding(vertical = 1.dp).zIndex(if (lifted) 1f else 0f)
                            .graphicsLayer { if (lifted) { translationY = dragY; shadowElevation = 16f } }
                            .then(if (lifted) Modifier.background(t.menu, RoundedCornerShape(10.dp)) else Modifier),
                        active = i == index || lifted,
                    ) {
                        Cover(tr.cover, Modifier.padding(5.dp).size(44.dp), shape = artShape(small = true))
                        Spacer(w = 6.dp)
                        Column(Modifier.weight(1f)) {
                            Txt(tr.title, size = 14.5.sp, weight = FontWeight.SemiBold, color = if (i == index) t.accent else if (past) t.mute else t.text)
                            Txt(tr.artistLine, size = 12.5.sp, color = t.mute)
                        }
                        Mono(tr.durationSec?.asDuration() ?: "", size = 12.sp)
                        Spacer(w = 8.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsPane() {
    val t = theme
    val now by Player.now.collectAsState()
    val pos by Player.engine.position.collectAsState()
    val track = now.track
    val lyrics by produceState<Lyrics?>(null, track?.id) { value = track?.let { LyricsService.get(it) } }
    val state = rememberLazyListState()
    val l = lyrics
    val cur = if (l != null && l.synced) l.lines.indexOfLast { it.ms <= pos } else -1
    LaunchedEffect(cur) { if (cur > 2) state.animateScrollToItem((cur - 2).coerceAtLeast(0)) }
    Column(Modifier.fillMaxSize()) {
        PaneHeader("Текст", if (l?.synced == true) "синхронизирован · LRCLIB" else if (l != null) "LRCLIB" else null)
        if (track == null) { Empty(Ti.microphone2, "Ничего не играет", ""); return@Column }
        if (l == null) { Empty(Ti.microphone2, "Текста нет", "Для этого трека текст не нашёлся."); return@Column }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 18.dp), state = state) {
            itemsIndexed(l.lines) { i, line ->
                val active = i == cur
                val color by animateColorAsState(if (active) t.accent else if (i < cur) t.dim else t.mute, tween(300))
                val scale by animateFloatAsState(if (active) 1f else 0.94f, tween(300))
                Txt(
                    line.text.ifBlank { "♪" }, size = (19 * scale).sp, weight = FontWeight.ExtraBold, color = color, maxLines = 4, lineHeight = 26.sp,
                    modifier = Modifier.padding(vertical = 7.dp).clickHand { if (line.ms >= 0) Player.seek(line.ms) },
                )
            }
        }
    }
}
