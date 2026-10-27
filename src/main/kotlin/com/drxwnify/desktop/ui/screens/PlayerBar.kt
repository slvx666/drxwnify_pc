package com.drxwnify.desktop.ui.screens

import com.drxwnify.desktop.ui.ArtistLinks
import com.drxwnify.desktop.ui.LinkText
import androidx.compose.foundation.shape.CircleShape
import com.drxwnify.desktop.ui.artShape
import com.drxwnify.desktop.ui.theme.Look
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drxwnify.desktop.data.Library
import com.drxwnify.desktop.data.Prefs
import com.drxwnify.desktop.model.msAsDuration
import com.drxwnify.desktop.playback.EngineState
import com.drxwnify.desktop.playback.Player
import com.drxwnify.desktop.playback.RepeatMode
import com.drxwnify.desktop.ui.Cover
import com.drxwnify.desktop.ui.Hanko
import com.drxwnify.desktop.ui.IconBtn
import com.drxwnify.desktop.ui.Mono
import com.drxwnify.desktop.ui.Nav
import com.drxwnify.desktop.ui.Panel
import com.drxwnify.desktop.ui.PlayButton
import com.drxwnify.desktop.ui.RightPane
import com.drxwnify.desktop.ui.Screen
import com.drxwnify.desktop.ui.SeekBar
import com.drxwnify.desktop.ui.Spacer
import com.drxwnify.desktop.ui.Ti
import com.drxwnify.desktop.ui.Tooltip
import com.drxwnify.desktop.ui.Txt
import com.drxwnify.desktop.ui.clickHand
import com.drxwnify.desktop.ui.theme

@Composable
fun PlayerBar(modifier: Modifier) {
    val t = theme
    val now by Player.now.collectAsState()
    val state by Player.engine.state.collectAsState()
    val pos by Player.engine.position.collectAsState()
    val dur by Player.engine.duration.collectAsState()
    val shuffle by Player.shuffle.collectAsState()
    val repeat by Player.repeat.collectAsState()
    val lib by Library.state.collectAsState()
    val settings by Prefs.settings.state.collectAsState()
    val track = now.track

    // Глубина: a capsule floating over the scene; Gruvbox: a plain tiled window.
    val island = t.look == Look.Island
    val term = t.look == Look.Terminal
    val timeSize = if (t.look == Look.Neon) 15.sp else 11.sp
    Panel(if (island) modifier.padding(horizontal = 56.dp) else modifier, radius = if (island) 40.dp else if (term) 0.dp else t.panelRadius + 2.dp) {
        Row(Modifier.fillMaxSize().padding(horizontal = if (island) 22.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // left: what's playing
            Row(Modifier.weight(0.3f), verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(track, transitionSpec = {
                    (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 3 }) togetherWith (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 3 })
                }, contentKey = { it?.id }) { tr ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (tr != null) {
                            Cover(tr.cover, Modifier.size(if (island) 52.dp else 56.dp).clickHand { Nav.right = RightPane.NowPlaying; Prefs.settings.update { it.copy(rightHidden = false) } },
                                shape = if (island) CircleShape else artShape(round = t.look == Look.Atlas, small = true))
                            Spacer(w = 12.dp)
                            Column(Modifier.width(190.dp)) {
                                if (term) Txt(
                                    when (state) { EngineState.Playing -> "[playing]"; EngineState.Loading -> "[loading]"; EngineState.Paused -> "[paused]"; else -> "[stopped]" },
                                    size = 11.sp, weight = FontWeight.Bold, color = t.accent,
                                )
                                LinkText(tr.title, tr.album?.id?.let { id -> { Nav.go(Screen.Album(id)) } }, size = 14.5.sp, weight = FontWeight.Bold)
                                ArtistLinks(tr.artists, size = 12.5.sp)
                            }
                            // "pending:" — an album still being opened: nothing to like yet
                            if (!tr.id.startsWith("pending:")) {
                                val liked = lib.liked.any { it.id == tr.id }
                                IconBtn(if (liked) Ti.heartFilled else Ti.heart, { Library.toggleLike(tr) }, active = liked, tint = if (liked) t.accent else t.mute, size = 32.dp, iconSize = 17.sp)
                            }
                        }
                    }
                }
            }
            // centre: controls and progress
            Column(Modifier.weight(0.4f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBtn(Ti.arrowsShuffle, { Player.toggleShuffle() }, active = shuffle, size = 32.dp, iconSize = 17.sp, tooltip = "Перемешать")
                    IconBtn(Ti.playerSkipBackFilled, { Player.previous() }, size = 34.dp, iconSize = 18.sp, tint = t.text, tooltip = "Назад")
                    PlayButton(state == EngineState.Playing || state == EngineState.Loading, { Player.toggle() }, 42.dp, loading = state == EngineState.Loading)
                    IconBtn(Ti.playerSkipForwardFilled, { Player.next() }, size = 34.dp, iconSize = 18.sp, tint = t.text, tooltip = "Дальше")
                    IconBtn(if (repeat == RepeatMode.One) Ti.repeatOnce else Ti.repeat, { Player.cycleRepeat() }, active = repeat != RepeatMode.Off, size = 32.dp, iconSize = 17.sp,
                        tooltip = when (repeat) { RepeatMode.Off -> "Повтор выключен"; RepeatMode.All -> "Повтор очереди"; RepeatMode.One -> "Повтор трека" })
                }
                Spacer(h = 4.dp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val total = if (dur > 0) dur else (track?.durationSec ?: 0) * 1000L
                    var aiming by remember { mutableStateOf<Float?>(null) }
                    Mono((aiming?.let { (it * total).toLong() } ?: pos).msAsDuration(), size = timeSize, color = if (aiming != null || t.look == Look.Neon) t.accent else t.mute, modifier = Modifier.width(42.dp))
                    SeekBar(if (total > 0) pos.toFloat() / total else 0f, { v -> if (total > 0) Player.seek((v * total).toLong()) }, Modifier.weight(1f), onPreview = { aiming = it }, ornate = true)
                    Mono(if (total > 0) total.msAsDuration() else "-:--", size = timeSize, modifier = Modifier.padding(start = 10.dp).width(36.dp))
                }
            }
            // right: quality, panes, volume
            Row(Modifier.weight(0.3f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                now.quality?.let { q ->
                    Tooltip("${now.provider} · $q") {
                        if (q.contains("FLAC", true) || q.contains("Hi", true)) Hanko() else Mono(com.drxwnify.desktop.playback.qualityLabel(q) ?: "", size = 11.sp, color = t.mute)
                    }
                    Spacer(w = 10.dp)
                }
                Levels()
                Spacer(w = 6.dp)
                val sleepAt by Player.sleepAt.collectAsState()
                val sleepAfter by Player.sleepAfterTrack.collectAsState()
                IconBtn(Ti.moon, { showSleepTimer() }, active = sleepAt != null || sleepAfter, size = 32.dp, iconSize = 16.sp,
                    tooltip = if (sleepAt != null || sleepAfter) "Таймер сна включён" else "Таймер сна")
                IconBtn(Ti.microphone2, { togglePane(RightPane.Lyrics) }, active = Nav.right == RightPane.Lyrics && !settings.rightHidden, size = 32.dp, iconSize = 17.sp, tooltip = "Текст")
                IconBtn(Ti.list, { togglePane(RightPane.Queue) }, active = Nav.right == RightPane.Queue && !settings.rightHidden, size = 32.dp, iconSize = 17.sp, tooltip = "Очередь")
                IconBtn(Ti.layoutSidebarRightExpand, { togglePane(RightPane.NowPlaying) }, active = Nav.right == RightPane.NowPlaying && !settings.rightHidden, size = 32.dp, iconSize = 17.sp, tooltip = "Сейчас играет")
                Spacer(w = 4.dp)
                IconBtn(if (settings.volume < 0.01f) Ti.volume3 else if (settings.volume < 0.5f) Ti.volume2 else Ti.volume, { Player.toggleMute() }, size = 32.dp, iconSize = 17.sp)
                SeekBar(settings.volume, { Player.setVolume(it) }, Modifier.width(96.dp), color = t.text, live = true)
            }
        }
    }
}

private fun togglePane(p: RightPane) {
    val hidden = Prefs.settings.value.rightHidden
    if (!hidden && Nav.right == p) Prefs.settings.update { it.copy(rightHidden = true) }
    else { Nav.right = p; if (hidden) Prefs.settings.update { it.copy(rightHidden = false) } }
}

/** Tiny live spectrum next to the quality mark. */
@Composable
private fun Levels() {
    val t = theme
    val levels by Player.engine.levels.collectAsState()
    val state by Player.engine.state.collectAsState()
    Canvas(Modifier.width(34.dp).height(18.dp)) {
        if (state != EngineState.Playing) return@Canvas
        val n = 10
        val w = size.width / n
        for (i in 0 until n) {
            val v = if (state == EngineState.Playing) levels[i * 3].coerceIn(0.06f, 1f) else 0.06f
            val h = size.height * v
            drawRoundRect(t.accent.copy(alpha = 0.35f + 0.5f * v), Offset(i * w + 1, size.height - h), Size(w - 2, h), CornerRadius(1.5f))
        }
    }
}
