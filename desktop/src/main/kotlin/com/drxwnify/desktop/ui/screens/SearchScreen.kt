package com.drxwnify.desktop.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drxwnify.desktop.catalog.YouTubeCatalog
import com.drxwnify.desktop.data.Log
import com.drxwnify.desktop.model.SearchResults
import com.drxwnify.desktop.playback.Player
import com.drxwnify.desktop.ui.Chip
import com.drxwnify.desktop.ui.Cover
import com.drxwnify.desktop.ui.Mono
import com.drxwnify.desktop.ui.Nav
import com.drxwnify.desktop.ui.PlayButton
import com.drxwnify.desktop.ui.Spacer
import com.drxwnify.desktop.ui.Ti
import com.drxwnify.desktop.ui.Txt
import com.drxwnify.desktop.ui.hand
import com.drxwnify.desktop.ui.rememberHover
import com.drxwnify.desktop.ui.theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen() {
    val t = theme
    val query = Nav.searchText.trim()
    var results by remember { mutableStateOf<SearchResults?>(null) }
    var loading by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("all") }
    LaunchedEffect(query) {
        if (query.isBlank()) { results = null; return@LaunchedEffect }
        // A pasted album/playlist/artist link opens its page right away.
        com.drxwnify.desktop.catalog.AppLinks.parse(query)?.takeIf { it.kind != com.drxwnify.desktop.catalog.AppLinks.Kind.TRACK }?.let { target ->
            val id = com.drxwnify.desktop.catalog.AppLinks.appId(target)
            Nav.go(when (target.kind) {
                com.drxwnify.desktop.catalog.AppLinks.Kind.ALBUM -> com.drxwnify.desktop.ui.Screen.Album(id)
                com.drxwnify.desktop.catalog.AppLinks.Kind.ARTIST -> com.drxwnify.desktop.ui.Screen.Artist(id)
                else -> com.drxwnify.desktop.ui.Screen.RemotePlaylist(id)
            })
            return@LaunchedEffect
        }
        delay(320)
        loading = true
        results = runCatching {
            com.drxwnify.desktop.catalog.Catalog.search(query)
        }.onFailure { Log.w("Search", "failed", it) }.getOrNull()
        loading = false
    }
    Page {
        item {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "Всё", "tracks" to "Треки", "albums" to "Альбомы", "artists" to "Исполнители", "playlists" to "Плейлисты").forEach { (k, v) ->
                    Chip(v, filter == k, { filter = k })
                }
            }
        }
        val r = results
        if (query.isBlank()) {
            item { Empty(Ti.search, "Что ищем?", "Трек, исполнитель, альбом — или вставь ссылку Spotify, VK, YouTube.") }
            return@Page
        }
        if (r == null) { item { Loading(if (loading) "Ищу «$query»…" else "") }; return@Page }
        if (r.tracks.isEmpty() && r.albums.isEmpty() && r.artists.isEmpty()) {
            item { Empty(Ti.search, "Ничего не нашлось", "Попробуй написать по-другому.") }
            return@Page
        }
        if (filter == "all") {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                    r.top?.let { top ->
                        Column(Modifier.weight(0.42f)) {
                            SectionTitle("Лучший результат")
                            TopResult(top.title, top.artistLine, top.cover) { Player.play(r.tracks, 0, "Поиск: $query") }
                        }
                        Spacer(w = 18.dp)
                    }
                    Column(Modifier.weight(0.58f)) {
                        SectionTitle("Треки")
                        r.tracks.take(4).forEachIndexed { i, tr -> TrackRow(tr, null, { Player.play(r.tracks, i, "Поиск: $query") }, showAlbum = false) }
                    }
                }
            }
            if (r.artists.isNotEmpty()) item { SectionTitle("Исполнители"); CardRow(r.artists.take(12)) }
            if (r.albums.isNotEmpty()) item { SectionTitle("Альбомы"); CardRow(r.albums.take(12), onPlay = { c -> com.drxwnify.desktop.data.ioScope.launch { playCollection(c) } }) }
            if (r.playlists.isNotEmpty()) item { SectionTitle("Плейлисты"); CardRow(r.playlists.take(12)) }
        } else {
            when (filter) {
                "tracks" -> r.tracks.forEachIndexed { i, tr -> item(key = tr.id) { TrackRow(tr, i, { Player.play(r.tracks, i, "Поиск: $query") }, list = r.tracks) } }
                "albums" -> item { Spacer(h = 12.dp); CardGrid(r.albums) }
                "artists" -> item { Spacer(h = 12.dp); CardGrid(r.artists) }
                "playlists" -> item { Spacer(h = 12.dp); CardGrid(r.playlists) }
            }
        }
    }
}

@Composable
private fun TopResult(title: String, subtitle: String, cover: String?, onPlay: () -> Unit) {
    val t = theme
    val (src, hovered) = rememberHover()
    val bg by animateColorAsState(if (hovered) t.fillHover else t.fill, tween(180))
    Box(
        Modifier.fillMaxWidth().height(232.dp).clip(RoundedCornerShape(t.panelRadius)).background(bg).hoverable(src).hand()
            .clickable(interactionSource = src, indication = null, onClick = onPlay).padding(20.dp),
    ) {
        Column {
            Cover(cover, Modifier.size(96.dp))
            Spacer(h = 16.dp)
            Txt(title, size = 26.sp, weight = t.headingWeight, family = t.heading, maxLines = 1)
            Spacer(h = 4.dp)
            Row(verticalAlignment = Alignment.CenterVertically) { Txt(subtitle, size = 13.sp, color = t.dim); Spacer(w = 8.dp); Mono("трек") }
        }
        if (hovered) Box(Modifier.align(Alignment.BottomEnd)) { PlayButton(false, onPlay, 48.dp) }
    }
}
