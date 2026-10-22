package com.drxwnify.desktop.ui.screens

import com.drxwnify.desktop.ui.Screen
import com.drxwnify.desktop.ui.Nav
import com.drxwnify.desktop.ui.Chip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.drxwnify.desktop.ui.clickHand
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drxwnify.desktop.catalog.YouTubeCatalog
import com.drxwnify.desktop.data.Downloads
import com.drxwnify.desktop.data.Library
import com.drxwnify.desktop.model.CollectionPage
import com.drxwnify.desktop.model.Track
import com.drxwnify.desktop.model.asDuration
import com.drxwnify.desktop.playback.EngineState
import com.drxwnify.desktop.playback.Player
import com.drxwnify.desktop.ui.IconBtn
import com.drxwnify.desktop.ui.Mono
import com.drxwnify.desktop.ui.PillButton
import com.drxwnify.desktop.ui.PlayButton
import com.drxwnify.desktop.ui.Spacer
import com.drxwnify.desktop.ui.Ti
import com.drxwnify.desktop.ui.Txt
import com.drxwnify.desktop.ui.theme

@Composable
fun AlbumScreen(id: String) = RemotePage(id, "альбом") { com.drxwnify.desktop.catalog.Catalog.album(id) }

@Composable
fun RemotePlaylistScreen(id: String) = RemotePage(id, "плейлист") { com.drxwnify.desktop.catalog.Catalog.playlist(id) }

@Composable
fun ArtistScreen(id: String) = RemotePage(id, "исполнитель", round = true) { com.drxwnify.desktop.catalog.Catalog.artist(id) }

@Composable
private fun RemotePage(id: String, kind: String, round: Boolean = false, load: suspend () -> CollectionPage?) {
    var page by remember(id) { mutableStateOf<CollectionPage?>(null) }
    var failed by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) {
        page = load()
        failed = page == null
        Library.touch("$kind:$id")
    }
    Page {
        val p = page
        if (p == null) {
            item { if (failed) Empty(Ti.alertTriangle, "Не открылось", "Попробуй ещё раз чуть позже.") else Loading() }
            return@Page
        }
        item {
            val artistName = if (kind == "исполнитель") p.head.title else p.tracks.firstOrNull()?.artists?.firstOrNull()?.name
            TracksHeader(p.head.cover, kind, p.head.title, subtitleOf(p), p.tracks, round, p.head,
                genres = artistName?.let { it to (if (kind == "альбом") p.head.title else null) })
        }
        if (kind == "исполнитель" && p.tracks.isNotEmpty()) item { SectionTitle("Популярные треки") }
        else item { Spacer(h = 10.dp) }
        p.tracks.forEachIndexed { i, tr ->
            item(key = "t$i${tr.id}") { TrackRow(tr, i, { Player.play(p.tracks, i, p.head.title) }, showAlbum = kind != "альбом", showCover = kind != "альбом", list = p.tracks) }
        }
        if (p.extra.isNotEmpty()) item {
            if (kind == "исполнитель") Releases(p.head, p.extra)
            else { SectionTitle(p.extraTitle ?: "Ещё"); CardRow(p.extra) }
        }
        p.description?.takeIf { it.isNotBlank() }?.let { d ->
            item { SectionTitle("Об исполнителе"); Txt(d, size = 13.sp, color = theme.dim, maxLines = 8, lineHeight = 20.sp) }
        }
    }
}

private fun subtitleOf(p: CollectionPage): String {
    val total = p.tracks.sumOf { it.durationSec ?: 0 }
    return listOfNotNull(
        p.head.subtitle.takeIf { it.isNotBlank() },
        if (p.tracks.isNotEmpty()) "${p.tracks.size} ${plural(p.tracks.size, "трек", "трека", "треков")}" else null,
        if (total > 0) "${total / 60} мин" else null,
    ).joinToString(" · ")
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10; val m100 = n % 100
    return when { m10 == 1 && m100 != 11 -> one; m10 in 2..4 && m100 !in 12..14 -> few; else -> many }
}

@Composable
private fun TracksHeader(
    cover: String?, kind: String, title: String, subtitle: String, tracks: List<Track>, round: Boolean, head: com.drxwnify.desktop.model.Collection?,
    /** Whose genres to show: the artist, and the album when it is an album page. */
    genres: Pair<String, String?>? = null,
    art: (@Composable (Modifier) -> Unit)? = null,
) {
    val tags by androidx.compose.runtime.produceState(emptyList<String>(), genres) {
        value = genres?.let { (artist, album) ->
            runCatching { if (album != null) com.drxwnify.desktop.catalog.Genres.ofAlbum(artist, album) else com.drxwnify.desktop.catalog.Genres.of(artist) }.getOrDefault(emptyList())
        }.orEmpty().take(4)
    }
    val now by Player.now.collectAsState()
    val state by Player.engine.state.collectAsState()
    val lib by Library.state.collectAsState()
    val spotifyLib by com.drxwnify.desktop.catalog.SpotifyCatalog.library.collectAsState()
    val playingHere = tracks.any { it.id == now.track?.id } && state == EngineState.Playing
    PageHeader(cover, kind, title, subtitle, round, art, tags) {
        PlayButton(playingHere, { if (playingHere) Player.toggle() else Player.play(tracks, 0, title) }, 56.dp)
        IconBtn(Ti.arrowsShuffle, { Player.shuffle.value = true; Player.play(tracks, (tracks.indices).random(), title) }, size = 42.dp, filled = true, tooltip = "Перемешать")
        if (head != null) {
            val saved = lib.savedAlbums.any { it.id == head.id } || lib.followedArtists.any { it.id == head.id } || spotifyLib.any { it.id == head.id }
            IconBtn(if (saved) Ti.heartFilled else Ti.heart, { Library.toggleSaved(head) }, size = 42.dp, filled = true, active = saved,
                tooltip = if (head.kind == "Artist") (if (saved) "Отписаться" else "Подписаться") else (if (saved) "Убрать из медиатеки" else "В медиатеку"))
        }
        if (tracks.isNotEmpty() && kind != "исполнитель") DownloadAll(tracks)
        head?.let { com.drxwnify.desktop.catalog.AppLinks.forCollection(it) }?.let { text ->
            IconBtn(Ti.share, { com.drxwnify.desktop.catalog.AppLinks.copy(text); Toast.show("Ссылка скопирована — её откроет и телефон") }, size = 42.dp, filled = true, tooltip = "Поделиться")
        }
    }
}

/** "Download everything" with how far it got: a ring while it works, a tick once every track is saved, the count beside it. */
@Composable
private fun DownloadAll(tracks: List<Track>) {
    val t = theme
    val done by Downloads.done.collectAsState()
    val active by Downloads.active.collectAsState()
    val ids = remember(tracks) { tracks.map { it.id }.toSet() }
    val have = done.done.count { it.track.id in ids }
    val running = active.values.filter { it.track.id in ids }
    val failed = running.count { it.error != null }
    val working = running.count { it.error == null }
    val total = ids.size
    val all = have >= total
    val progress = ((have + running.filter { it.error == null }.sumOf { it.progress.toDouble() }) / total).toFloat().coerceIn(0f, 1f)
    Box(contentAlignment = Alignment.Center) {
        IconBtn(if (all) Ti.circleCheck else Ti.download, {
            if (all) Toast.show("Всё уже скачано") else { Downloads.enqueue(tracks); Toast.show("Скачиваю ${total - have} ${plural(total - have, "трек", "трека", "треков")}") }
        }, size = 42.dp, filled = true, active = all, tooltip = if (all) "Скачано всё" else "Скачать всё")
        if (working > 0) androidx.compose.foundation.Canvas(Modifier.size(42.dp)) {
            val sw = 2.5.dp.toPx()
            drawArc(t.accent, -90f, 360f * progress, false, androidx.compose.ui.geometry.Offset(sw / 2, sw / 2),
                androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw), style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
    }
    if (have > 0 || running.isNotEmpty()) {
        Column {
            Mono("скачано $have из $total" + if (working > 0) " · качаю $working" else "", size = 12.sp, color = if (all) t.accent else t.dim)
            if (failed > 0) Txt("не вышло: $failed — повторить", size = 12.sp, color = t.accent2,
                modifier = Modifier.clickHand { Downloads.enqueue(running.filter { it.error != null }.map { it.track }) })
        }
    }
}

private val releaseGroups = listOf("all" to "Все", "album" to "Альбомы", "single" to "Синглы и EP", "compilation" to "Сборники")

/** An artist's releases: a filter (albums / singles and EPs / compilations), one row of cards, "Показать всё". */
@Composable
private fun Releases(artist: com.drxwnify.desktop.model.Collection, releases: List<com.drxwnify.desktop.model.Collection>) {
    var group by remember(artist.id) { mutableStateOf("all") }
    val groups = releaseGroups.filter { (k, _) -> k == "all" || releases.any { it.group == k } }
    SectionTitle("Дискография", action = "Показать всё", onAction = { Nav.go(Screen.Discography(artist.id, artist.title)) })
    if (groups.size > 2) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            groups.forEach { (k, v) -> Chip(v, group == k, { group = k }) }
        }
    }
    CardRow(releases.filter { group == "all" || it.group == group })
}

/** Every release of an artist with its tracks, newest first (Spotify's "Показать всё"). */
@Composable
fun DiscographyScreen(artistId: String, artistName: String) {
    var releases by remember(artistId) { mutableStateOf<List<com.drxwnify.desktop.model.Collection>?>(null) }
    var group by remember(artistId) { mutableStateOf("all") }
    LaunchedEffect(artistId) { releases = com.drxwnify.desktop.catalog.Catalog.artist(artistId)?.extra.orEmpty() }
    Page {
        val all = releases
        item {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                com.drxwnify.desktop.ui.Heading(artistName, Modifier.weight(1f), size = 30.sp)
                releaseGroups.filter { (k, _) -> k == "all" || all.orEmpty().any { it.group == k } }.forEach { (k, v) ->
                    Spacer(w = 6.dp); Chip(v, group == k, { group = k })
                }
            }
        }
        if (all == null) { item { Loading() }; return@Page }
        all.filter { group == "all" || it.group == group }.forEach { c -> item(key = c.id) { ReleaseBlock(c) } }
    }
}

@Composable
private fun ReleaseBlock(c: com.drxwnify.desktop.model.Collection) {
    val t = theme
    val page by androidx.compose.runtime.produceState<com.drxwnify.desktop.model.CollectionPage?>(null, c.id) { value = com.drxwnify.desktop.catalog.Catalog.open(c) }
    val now by Player.now.collectAsState()
    val state by Player.engine.state.collectAsState()
    val tracks = page?.tracks.orEmpty()
    val playingHere = tracks.any { it.id == now.track?.id } && state == EngineState.Playing
    Column(Modifier.padding(top = 34.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.drxwnify.desktop.ui.ThemedArt(c.cover, Modifier.size(136.dp).clickHand { Nav.go(Screen.Album(c.id)) }, seed = c.id.hashCode())
            Spacer(w = 22.dp)
            Column(Modifier.weight(1f)) {
                com.drxwnify.desktop.ui.LinkText(c.title, { Nav.go(Screen.Album(c.id)) }, size = 26.sp, weight = t.headingWeight, family = t.heading)
                Spacer(h = 6.dp)
                Txt(listOfNotNull(c.subtitle.takeIf { it.isNotBlank() }, if (tracks.isNotEmpty()) "${tracks.size} ${plural(tracks.size, "трек", "трека", "треков")}" else null).joinToString(" · "), size = 13.sp, color = t.dim)
                Spacer(h = 12.dp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayButton(playingHere, { if (playingHere) Player.toggle() else if (tracks.isNotEmpty()) Player.play(tracks, 0, c.title) }, 44.dp)
                    val lib by Library.state.collectAsState()
                    val saved = lib.savedAlbums.any { it.id == c.id }
                    IconBtn(if (saved) Ti.heartFilled else Ti.heart, { Library.toggleSaved(c) }, size = 38.dp, filled = true, active = saved, tooltip = if (saved) "Убрать из медиатеки" else "В медиатеку")
                }
            }
        }
        Spacer(h = 14.dp)
        if (page == null) Mono("загружаю треки…", size = 12.sp)
        tracks.forEachIndexed { i, tr -> TrackRow(tr, i, { Player.play(tracks, i, c.title) }, showAlbum = false, showCover = false, list = tracks) }
    }
}

@Composable
fun LikedScreen() {
    val lib by Library.state.collectAsState()
    LaunchedEffect(Unit) { Library.touch("liked") }
    Page {
        item { TracksHeader(null, "плейлист", "Любимые треки", "${lib.liked.size} ${plural(lib.liked.size, "трек", "трека", "треков")}", lib.liked, false, null) { HeartArt(it) } }
        if (lib.liked.isEmpty()) item { Empty(Ti.heart, "Пока пусто", "Жми ♡ у трека — он появится здесь. С телефона любимые подтянутся после связки.") }
        lib.liked.forEachIndexed { i, tr -> item(key = tr.id) { TrackRow(tr, i, { Player.play(lib.liked, i, "Любимые треки") }, list = lib.liked) } }
    }
}

@Composable
fun LocalPlaylistScreen(id: String) {
    val lib by Library.state.collectAsState()
    val p = lib.playlists.firstOrNull { it.id == id }
    LaunchedEffect(id) { Library.touch("playlist:$id") }
    Page {
        if (p == null) { item { Empty(Ti.playlist, "Плейлист удалён", "") }; return@Page }
        item { TracksHeader(p.cover, "плейлист", p.name, "${p.tracks.size} ${plural(p.tracks.size, "трек", "трека", "треков")}", p.tracks, false, null, art = if (p.cover == null) ({ m -> MosaicArt(p.tracks.map { it.cover }, m) }) else null) }
        if (p.tracks.isEmpty()) item { Empty(Ti.playlist, "Пустой плейлист", "Правый клик по любому треку → «В плейлист».") }
        p.tracks.forEachIndexed { i, tr ->
            item(key = tr.id) {
                TrackRow(tr, i, { Player.play(p.tracks, i, p.name) }, extraMenu = listOf(ContextMenuItem("Убрать из плейлиста") { Library.removeFromPlaylist(p.id, tr.id) }), list = p.tracks)
            }
        }
    }
}

@Composable
fun HistoryScreen() {
    val lib by Library.state.collectAsState()
    val tracks = lib.history.map { it.track }.distinctBy { it.id }
    Page {
        item { SectionTitle("История", "что играло на этом компьютере") }
        if (tracks.isEmpty()) item { Empty(Ti.history, "Пока ничего", "Включи что-нибудь.") }
        tracks.forEachIndexed { i, tr ->
            item(key = tr.id) {
                TrackRow(tr, i, { Player.play(tracks, i, "История") }, list = tracks, extraMenu = listOf(ContextMenuItem("Убрать из истории") { Library.removeFromHistory(tr.id); Toast.show("Убрано из истории") }))
            }
        }
    }
}

@Composable
fun DownloadsScreen() {
    val t = theme
    val done by Downloads.done.collectAsState()
    val active by Downloads.active.collectAsState()
    val tracks = done.done.map { it.track }
    Page {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    com.drxwnify.desktop.ui.Heading("Скачанные треки", size = 30.sp)
                    Spacer(h = 4.dp)
                    Mono("${done.done.size} ${plural(done.done.size, "файл", "файла", "файлов")} · ${Downloads.folder.absolutePath}")
                }
                PillButton("Открыть папку", Ti.folderOpen, onClick = { runCatching { java.awt.Desktop.getDesktop().open(Downloads.folder) } })
            }
        }
        if (active.isNotEmpty()) {
            item { SectionTitle("Сейчас", action = if (active.values.any { it.error != null }) "Убрать ошибки" else null, onAction = { Downloads.clearFailed() }) }
            active.values.forEach { task ->
                item(key = "a" + task.track.id) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.drxwnify.desktop.ui.Cover(task.track.cover, Modifier.padding(end = 12.dp).size(40.dp))
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Txt(task.track.title, size = 14.sp)
                            if (task.error != null) Mono(task.error, color = t.accent2) else com.drxwnify.desktop.ui.SeekBar(task.progress, {}, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        if (tracks.isEmpty() && active.isEmpty()) item { Empty(Ti.download, "Пока ничего не скачано", "Скачивай треки, альбомы и плейлисты — они будут играть без интернета.") }
        else if (tracks.isNotEmpty()) item { SectionTitle("Готово") }
        tracks.forEachIndexed { i, tr -> item(key = tr.id) { TrackRow(tr, i, { Player.play(tracks, i, "Скачанные") }, list = tracks) } }
    }
}

