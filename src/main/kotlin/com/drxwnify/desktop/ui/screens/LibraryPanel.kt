package com.drxwnify.desktop.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.drxwnify.desktop.ui.Label
import com.drxwnify.desktop.ui.artShape
import com.drxwnify.desktop.ui.theme.Look
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drxwnify.desktop.data.Downloads
import com.drxwnify.desktop.data.Library
import com.drxwnify.desktop.playback.EngineState
import com.drxwnify.desktop.playback.Player
import com.drxwnify.desktop.ui.Chip
import com.drxwnify.desktop.ui.Cover
import com.drxwnify.desktop.ui.EqBars
import com.drxwnify.desktop.ui.HoverRow
import com.drxwnify.desktop.ui.Ico
import com.drxwnify.desktop.ui.IconBtn
import com.drxwnify.desktop.ui.Mono
import com.drxwnify.desktop.ui.Nav
import com.drxwnify.desktop.ui.Panel
import com.drxwnify.desktop.ui.Screen
import com.drxwnify.desktop.ui.Spacer
import com.drxwnify.desktop.ui.Ti
import com.drxwnify.desktop.ui.Tooltip
import com.drxwnify.desktop.ui.Txt
import com.drxwnify.desktop.ui.clickHand
import com.drxwnify.desktop.ui.theme

private data class LibEntry(
    val key: String,
    val kind: String, // playlist | album | artist | special
    val title: String,
    val subtitle: String,
    val cover: String?,
    val glyph: String? = null,
    val round: Boolean = false,
    val screen: Screen,
    val context: String,
)

@Composable
fun LibraryPanel(collapsed: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val t = theme
    val lib by Library.state.collectAsState()
    val downloads by Downloads.done.collectAsState()
    val now by Player.now.collectAsState()
    val state by Player.engine.state.collectAsState()
    val ctx by Player.context.collectAsState()
    val spotifyLib by com.drxwnify.desktop.catalog.SpotifyCatalog.library.collectAsState()
    val me by com.drxwnify.desktop.friends.Friends.me.collectAsState()
    val friendsStore by com.drxwnify.desktop.friends.Friends.accessState.collectAsState()
    val reviewsOpen = remember(me, friendsStore) { com.drxwnify.desktop.friends.Friends.reviewsAccess() }
    val spotifyPinned = remember(spotifyLib) { com.drxwnify.desktop.catalog.SpotifyCatalog.pinned.flatMap { id -> listOf("альбом:spotify:$id", "плейлист:spotify:$id", "исполнитель:spotify:$id") } }
    var filter by remember { mutableStateOf<String?>(null) }
    var alpha by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val all = buildList {
        add(LibEntry("liked", "special", "Любимые треки", "${lib.liked.size} ${plural(lib.liked.size, "трек", "трека", "треков")}", null, Ti.heartFilled, screen = Screen.Liked, context = "Любимые треки"))
        lib.playlists.forEach { add(LibEntry("playlist:${it.id}", "playlist", it.name, "Плейлист · ${it.tracks.size}", it.cover ?: it.tracks.firstOrNull()?.cover, screen = Screen.LocalPlaylist(it.id), context = it.name)) }
        lib.savedAlbums.forEach { c ->
            val k = if (c.kind == "Album") "альбом" else "плейлист"
            add(LibEntry("$k:${c.id}", if (c.kind == "Album") "album" else "playlist", c.title, (if (c.kind == "Album") "Альбом" else "Плейлист") + (c.subtitle.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), c.cover,
                screen = if (c.kind == "Album") Screen.Album(c.id) else Screen.RemotePlaylist(c.id), context = c.title))
        }
        lib.followedArtists.forEach { c -> add(LibEntry("исполнитель:${c.id}", "artist", c.title, "Исполнитель", c.cover, round = true, screen = Screen.Artist(c.id), context = c.title)) }
        // The Spotify account's own library, in Spotify's order, as the phone shows it.
        spotifyLib.forEach { c ->
            when (c.kind) {
                "Album" -> add(LibEntry("альбом:${c.id}", "album", c.title, c.subtitle, c.cover, screen = Screen.Album(c.id), context = c.title))
                "Artist" -> add(LibEntry("исполнитель:${c.id}", "artist", c.title, "Исполнитель", c.cover, round = true, screen = Screen.Artist(c.id), context = c.title))
                else -> add(LibEntry("плейлист:${c.id}", "playlist", c.title, c.subtitle, c.cover, screen = Screen.RemotePlaylist(c.id), context = c.title))
            }
        }
        add(LibEntry("history", "special", "История", "что играло", null, Ti.history, screen = Screen.History, context = "История"))
    }.distinctBy { it.key }
    val pinnedKeys = lib.pinnedKeys + spotifyPinned
    val recent = lib.recentKeys
    val sorted = all
        .filter { e -> when (filter) { "playlists" -> e.kind == "playlist"; "albums" -> e.kind == "album"; "artists" -> e.kind == "artist"; else -> true } || (filter != null && e.key == "liked" && filter == "playlists") }
        .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
        .let { list ->
            val pinned = list.filter { it.key in pinnedKeys }
            // Liked, downloads and drxwnslvt's stay at the top in their own order, whatever the sort.
            val special = list.filter { it.kind == "special" && it.key != "history" && it.key !in pinnedKeys }
            val rest = list.filterNot { it.key in pinnedKeys || it in special }
            pinned + special + if (alpha) rest.sortedBy { it.title.lowercase() } else rest.sortedBy { e -> recent.indexOf(e.key).let { if (it < 0) Int.MAX_VALUE else it } }
        }

    Panel(modifier, title = if (collapsed) null else "медиатека", focused = false) {
        Column(Modifier.fillMaxSize().padding(vertical = 12.dp, horizontal = if (collapsed) 0.dp else 8.dp)) {
            if (!collapsed) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    when (t.look) {
                        Look.Terminal -> Txt("~/library", size = 13.sp, color = t.dim, modifier = Modifier.weight(1f))
                        Look.Neon, Look.Atlas -> Label("Медиатека", size = 14.sp, color = t.text, modifier = Modifier.weight(1f))
                        Look.Blossom -> Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                            Txt("Медиатека", size = 20.sp, weight = FontWeight.SemiBold, family = t.heading)
                            Spacer(w = 8.dp)
                            Txt("書庫", size = 12.sp, family = com.drxwnify.desktop.ui.theme.Fonts.jpSerif, weight = FontWeight.SemiBold, color = t.accent.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 3.dp))
                        }
                        else -> Txt("Медиатека", size = 15.sp, weight = FontWeight.ExtraBold, family = t.heading, modifier = Modifier.weight(1f))
                    }
                    IconBtn(Ti.plus, { val p = Library.createPlaylist("Новый плейлист ${lib.playlists.size + 1}"); Nav.go(Screen.LocalPlaylist(p.id)) }, size = 32.dp, tooltip = "Создать плейлист")
                    IconBtn(Ti.layoutSidebarLeftCollapse, onToggle, size = 32.dp, tooltip = "Свернуть")
                }
                // capitals with tracking (Ретровейв, Обсерватория) are wider: let the chips scroll instead of clipping
                Row(Modifier.padding(start = 4.dp, bottom = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("playlists" to "Плейлисты", "albums" to "Альбомы", "artists" to "Артисты").forEach { (k, v) ->
                        Chip(v, filter == k, { filter = if (filter == k) null else k })
                    }
                    // next to the filters, as on the phone: these open their own pages
                    Chip("Скачанные", Nav.current == Screen.Downloads, { Nav.go(Screen.Downloads) })
                    if (reviewsOpen) Chip("drxwnslvt's", Nav.current == Screen.Reviews, { Nav.go(Screen.Reviews) })
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (searching) {
                        Row(
                            Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(8.dp)).background(t.fill).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Ico(Ti.search, size = 14.sp, color = t.mute)
                            Spacer(w = 6.dp)
                            BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = t.text, fontSize = 12.sp, fontFamily = t.body), cursorBrush = SolidColor(t.accent), modifier = Modifier.weight(1f))
                            IconBtn(Ti.x, { searching = false; query = "" }, size = 22.dp, iconSize = 12.sp)
                        }
                    } else {
                        IconBtn(Ti.search, { searching = true }, size = 30.dp, iconSize = 15.sp, tooltip = "Искать в медиатеке")
                        Box(Modifier.weight(1f))
                    }
                    Spacer(w = 8.dp)
                    Row(Modifier.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt(if (alpha) "По алфавиту" else "Недавние", size = 13.sp, color = t.dim,
                            modifier = Modifier.clickHand { alpha = !alpha })
                        Spacer(w = 4.dp)
                        Ico(Ti.arrowsSort, size = 14.sp, color = t.mute)
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconBtn(Ti.layoutSidebarLeftExpand, onToggle, size = 40.dp, tooltip = "Развернуть медиатеку")
                    IconBtn(Ti.plus, { val p = Library.createPlaylist("Новый плейлист ${lib.playlists.size + 1}"); Nav.go(Screen.LocalPlaylist(p.id)) }, size = 40.dp, tooltip = "Создать плейлист")
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = if (collapsed) Alignment.CenterHorizontally else Alignment.Start) {
                items(sorted, key = { it.key }) { e ->
                    val isOpen = Nav.current == e.screen && !Nav.settingsOpen
                    val isPlaying = ctx == e.context && state == EngineState.Playing
                    val pinned = e.key in lib.pinnedKeys
                    ContextMenuArea(items = {
                        listOfNotNull(
                            ContextMenuItem(if (pinned) "Открепить" else "Закрепить") { Library.togglePin(e.key) },
                            if (e.kind == "playlist" && e.key.startsWith("playlist:")) ContextMenuItem("Удалить плейлист") { Library.deletePlaylist(e.key.removePrefix("playlist:")) } else null,
                        )
                    }) {
                        if (collapsed) {
                            Tooltip(e.title) {
                                Box(Modifier.padding(vertical = 4.dp)) {
                                    EntryArt(e, 48, isOpen) { Nav.go(e.screen) }
                                }
                            }
                        } else {
                            HoverRow(onClick = { Nav.go(e.screen) }, active = isOpen, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                Box(Modifier.padding(7.dp)) { EntryArt(e, 52, false, null) }
                                Spacer(w = 6.dp)
                                Column(Modifier.weight(1f)) {
                                    Txt(e.title, size = 16.sp, weight = FontWeight.SemiBold, color = if (isPlaying) t.accent else t.text)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (pinned) { Ico(Ti.pinFilled, size = 11.sp, color = t.accent); Spacer(w = 4.dp) }
                                        if (t.look == Look.Terminal) {
                                            Mono(when (e.kind) { "album" -> "[A]"; "artist" -> "[R]"; "playlist" -> "[P]"; else -> "[*]" }, size = 11.sp, color = t.sample.getOrElse(when (e.kind) { "album" -> 1; "artist" -> 2; else -> 0 }) { t.accent })
                                            Spacer(w = 5.dp)
                                        }
                                        Mono(e.subtitle, size = 13.sp)
                                    }
                                }
                                if (isPlaying) { EqBars(); Spacer(w = 10.dp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryArt(e: LibEntry, size: Int, outlined: Boolean, onClick: (() -> Unit)?) {
    val t = theme
    val shape = artShape(round = e.round, small = true)
    val ring by animateColorAsState(if (outlined) t.accent else androidx.compose.ui.graphics.Color.Transparent, tween(180))
    // Gruvbox frames every item; Обсерватория rings it in gold
    val frame = when (t.look) {
        Look.Terminal -> Modifier.border(1.dp, t.panelBorder, shape).padding(1.dp)
        Look.Atlas -> Modifier.border(1.dp, t.accent2.copy(alpha = 0.55f), shape).padding(2.dp)
        else -> Modifier
    }
    val m = Modifier.size(size.dp).then(if (onClick != null) Modifier.clickHand(onClick) else Modifier)
        .border(2.dp, ring, shape).padding(if (outlined) 3.dp else 0.dp).then(frame)
    if (e.glyph != null) {
        Box(
            m.clip(shape).background(
                if (e.key == "liked") androidx.compose.ui.graphics.Brush.linearGradient(listOf(t.accent, t.accent2))
                else androidx.compose.ui.graphics.Brush.linearGradient(listOf(t.fillHover, t.fill)),
            ),
            contentAlignment = Alignment.Center,
        ) { Ico(e.glyph, size = (size * 0.42f).sp, color = if (e.key == "liked") t.onAccent else t.dim) }
    } else if (e.cover != null) Cover(e.cover, m, shape = shape)
    else Box(m.clip(shape).background(t.fill), contentAlignment = Alignment.Center) {
        Txt(e.title.take(1).uppercase(), size = (size * 0.4f).sp, weight = FontWeight.ExtraBold, color = t.dim)
    }
}
