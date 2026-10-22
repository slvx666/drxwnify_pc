package com.drxwnify.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import com.drxwnify.desktop.ui.theme.Look
import com.drxwnify.desktop.ui.artShape
import com.drxwnify.desktop.ui.ThemedArt
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drxwnify.desktop.catalog.YouTubeCatalog
import com.drxwnify.desktop.data.Log
import com.drxwnify.desktop.data.ioScope
import com.drxwnify.desktop.model.HomeRow
import com.drxwnify.desktop.model.Track
import com.drxwnify.desktop.playback.Player
import com.drxwnify.desktop.ui.Cover
import com.drxwnify.desktop.ui.Ico
import com.drxwnify.desktop.ui.Mono
import com.drxwnify.desktop.ui.Spacer
import com.drxwnify.desktop.ui.Ti
import com.drxwnify.desktop.ui.Tooltip
import com.drxwnify.desktop.ui.hand
import com.drxwnify.desktop.ui.rememberHover
import com.drxwnify.desktop.ui.theme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@kotlinx.serialization.Serializable
private data class SavedHome(val rows: List<HomeRow>, val builtAt: Long)

/**
 * The home feed. The last one is kept on disk and shown at once; a new one is built behind it when
 * it is older than a few hours (or on «Обновить», row by row as each is ready).
 */
object HomeFeed {
    private const val TTL_MS = 6 * 3600_000L
    private val file get() = java.io.File(com.drxwnify.desktop.data.Paths.root, "home_feed.json")
    val rows = MutableStateFlow<List<HomeRow>?>(null)
    val failed = MutableStateFlow(false)
    val building = MutableStateFlow(false)
    /** Rows are being added to the page one by one. */
    val growing = MutableStateFlow(false)
    private var builtAt = 0L

    fun load(force: Boolean = false) {
        if (rows.value == null) runCatching {
            file.takeIf { it.exists() }?.readText()?.let { com.drxwnify.desktop.data.appJson.decodeFromString(SavedHome.serializer(), it) }
        }.getOrNull()?.takeIf { it.rows.isNotEmpty() }?.let { rows.value = it.rows; builtAt = it.builtAt }
        if (building.value || (!force && rows.value != null && System.currentTimeMillis() - builtAt < TTL_MS)) return
        building.value = true
        failed.value = false
        // Without anything saved (or on «Обновить») rows appear as they come; else the new page replaces the old one whole.
        val live = force || rows.value == null
        growing.value = live
        ioScope.launch {
            var personal: List<HomeRow> = emptyList()
            runCatching {
                val lib = com.drxwnify.desktop.data.Library.value
                if (lib.history.isNotEmpty() || lib.liked.isNotEmpty() || com.drxwnify.desktop.catalog.SpotifyCatalog.linked) {
                    com.drxwnify.desktop.catalog.PersonalHome.build({ r -> personal = r; if (live) rows.value = r }, force)
                }
            }.onFailure { Log.w("Home", "personal rows failed", it) }
            // An empty taste (a fresh install) or a short page: YouTube Music's own home fills it up.
            val tail = if (personal.size < 4) runCatching { YouTubeCatalog.home() }.onFailure { Log.w("Home", "YouTube home failed", it) }.getOrDefault(emptyList()) else emptyList()
            val all = personal + tail.filter { t -> personal.none { it.title == t.title } }.let { if (personal.any { it.tracks.isNotEmpty() }) it.filter { r -> r.tracks.isEmpty() } else it }
            if (all.isNotEmpty()) {
                rows.value = all
                builtAt = System.currentTimeMillis()
                runCatching { file.writeText(com.drxwnify.desktop.data.appJson.encodeToString(SavedHome.serializer(), SavedHome(all, builtAt))) }
            } else {
                failed.value = true
                if (rows.value == null) rows.value = emptyList()
            }
            building.value = false
            growing.value = false
        }
    }
}

@Composable
fun HomeScreen() {
    LaunchedEffect(Unit) { HomeFeed.load() }
    val rows by HomeFeed.rows.collectAsState()
    val failed by HomeFeed.failed.collectAsState()
    val building by HomeFeed.building.collectAsState()
    val growing by HomeFeed.growing.collectAsState()
    Page {
        val list = rows
        if (list == null) { item { Loading("Собираю главную…") }; return@Page }
        if (list.isEmpty() && failed) {
            item {
                Empty(Ti.cloud, "Не получилось загрузить главную", "Проверь интернет или VPN — и попробуй ещё раз.") {
                    com.drxwnify.desktop.ui.PillButton("Повторить", Ti.refresh, primary = true, onClick = { HomeFeed.load(force = true) })
                }
            }
            return@Page
        }
        list.forEachIndexed { i, row ->
            item(key = "row$i") {
                if (row.tracks.isNotEmpty() && i == 0) {
                    SectionTitle(row.title, row.subtitle, if (building) "Обновляю…" else "Обновить", { HomeFeed.load(force = true) })
                    RecommendationGrid(row.tracks)
                } else if (row.tracks.isNotEmpty()) {
                    SectionTitle(row.title, row.subtitle)
                    TrackCardRow(row.tracks, row.title)
                } else if (row.collections.isNotEmpty()) {
                    if (i == 0) SectionTitle(row.title, row.subtitle, if (building) "Обновляю…" else "Обновить", { HomeFeed.load(force = true) })
                    else SectionTitle(row.title, row.subtitle)
                    CardRow(row.collections, onPlay = { c -> ioScope.launch { playCollection(c) } })
                }
            }
        }
        if (growing) item(key = "more") { Loading("Собираю остальные полки…") }
    }
}

suspend fun playCollection(c: com.drxwnify.desktop.model.Collection) {
    Player.preparing(c.title, c.subtitle, c.cover)
    val page = com.drxwnify.desktop.catalog.Catalog.open(c) ?: run { Player.engine.stop(); return }
    if (page.tracks.isNotEmpty()) Player.play(page.tracks, 0, c.title)
}

/** Covers only, no titles: two rows that fill the width; the last cell starts an endless radio. */
@Composable
private fun RecommendationGrid(tracks: List<Track>) {
    val t = theme
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 16.dp
        val cols = maxOf(4, ((maxWidth + gap) / (150.dp + gap)).toInt())
        val shown = tracks.take(cols * 2 - 1)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (r in 0 until 2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (c in 0 until cols) {
                        val i = r * cols + c
                        Box(Modifier.weight(1f)) {
                            if (i < shown.size) RecCover(shown[i]) { Player.play(shown, i, "Рекомендации") }
                            else if (i == cols * 2 - 1) DiceCell { tracks.randomOrNull()?.let(Player::radio) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecCover(track: Track, onClick: () -> Unit) {
    val (src, hovered) = rememberHover()
    val scale by animateFloatAsState(if (hovered) 1.035f else 1f, tween(180))
    val dim by animateFloatAsState(if (hovered) 0.22f else 0f, tween(180))
    Tooltip("${track.title} — ${track.artistLine}") {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer { scaleX = scale; scaleY = scale }
                .hoverable(src).hand().clickable(interactionSource = src, indication = null, onClick = onClick),
        ) {
            ThemedArt(track.cover, Modifier.fillMaxSize(), seed = track.id.hashCode()) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)), contentAlignment = Alignment.Center) {
                    if (hovered) Ico(Ti.playerPlayFilled, size = 26.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun DiceCell(onClick: () -> Unit) {
    val t = theme
    val (src, hovered) = rememberHover()
    val bg by animateColorAsState(if (hovered) t.accent.copy(alpha = 0.16f) else t.accent.copy(alpha = 0.08f), tween(180))
    val rot by animateFloatAsState(if (hovered) 18f else 0f, tween(260))
    val shape = artShape()
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).padding(if (t.look == Look.Atlas || t.look == Look.Quilt) 8.dp else 0.dp).clip(shape).background(bg)
            .border(if (t.look == Look.Terminal) 2.dp else 1.dp, t.accent.copy(alpha = if (t.look == Look.Terminal) 0.8f else 0.35f), shape)
            .hoverable(src).hand().clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.graphicsLayer { rotationZ = rot }) { Ico(Ti.dice5, size = 30.sp, color = t.accent) }
            Spacer(h = 6.dp)
            Mono("∞ радио", size = 11.sp, color = t.accent)
        }
    }
}
