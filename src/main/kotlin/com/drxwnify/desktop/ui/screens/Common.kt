@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.drxwnify.desktop.ui.screens

import com.drxwnify.desktop.ui.Chip
import androidx.compose.foundation.border
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.isPrimaryPressed
import com.drxwnify.desktop.ui.LinkText
import com.drxwnify.desktop.ui.ArtistLinks
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import com.drxwnify.desktop.ui.artShape
import androidx.compose.ui.graphics.Brush
import com.drxwnify.desktop.ui.Label
import com.drxwnify.desktop.ui.ThemedArt
import com.drxwnify.desktop.ui.theme.Fonts
import com.drxwnify.desktop.ui.theme.Look
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drxwnify.desktop.data.Downloads
import com.drxwnify.desktop.data.Library
import com.drxwnify.desktop.model.Collection
import com.drxwnify.desktop.model.Track
import com.drxwnify.desktop.model.asDuration
import com.drxwnify.desktop.playback.EngineState
import com.drxwnify.desktop.playback.Player
import com.drxwnify.desktop.ui.Cover
import com.drxwnify.desktop.ui.EqBars
import com.drxwnify.desktop.ui.HoverRow
import com.drxwnify.desktop.ui.Ico
import com.drxwnify.desktop.ui.Mono
import com.drxwnify.desktop.ui.Nav
import com.drxwnify.desktop.ui.Screen
import com.drxwnify.desktop.ui.Spacer
import com.drxwnify.desktop.ui.Ti
import com.drxwnify.desktop.ui.Tooltip
import com.drxwnify.desktop.ui.clickHand
import com.drxwnify.desktop.ui.Txt
import com.drxwnify.desktop.ui.hand
import com.drxwnify.desktop.ui.rememberHover
import com.drxwnify.desktop.ui.theme

/** A scrolling page with a slim scrollbar that only shows while you scroll or hover. */
@Composable
fun Page(state: LazyListState = rememberLazyListState(), padding: PaddingValues = PaddingValues(start = 10.dp, end = 18.dp, top = 6.dp, bottom = 32.dp), content: LazyListScope.() -> Unit) {
    val t = theme
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = padding, content = content)
        VerticalScrollbar(
            rememberScrollbarAdapter(state),
            Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            style = ScrollbarStyle(
                minimalHeight = 40.dp, thickness = 6.dp, shape = RoundedCornerShape(3.dp), hoverDurationMillis = 250,
                unhoverColor = t.text.copy(alpha = 0.08f), hoverColor = t.text.copy(alpha = 0.22f),
            ),
        )
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val t = theme
    Row(Modifier.fillMaxWidth().padding(top = 34.dp, bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            when (t.look) {
                Look.Terminal -> Txt(":: ${title.lowercase()}", size = 17.sp, weight = FontWeight.Bold, color = t.accent)
                Look.Neon -> Txt(title.uppercase(), size = 22.sp, family = t.heading, letterSpacing = 1.5.sp, brush = Brush.horizontalGradient(listOf(t.accent, t.accent2)))
                Look.Blossom -> Row(verticalAlignment = Alignment.Bottom) {
                    Txt(title, size = 27.sp, weight = FontWeight.SemiBold, family = t.heading)
                    Spacer(w = 10.dp)
                    Txt(sectionWord(title), size = 13.sp, family = Fonts.jpSerif, weight = FontWeight.SemiBold, color = t.accent.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 5.dp))
                }
                Look.Atlas -> Txt(title.uppercase(), size = 20.sp, family = t.heading, letterSpacing = 3.sp)
                Look.Rite -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp, 2.dp).background(t.accent))
                    Spacer(w = 12.dp)
                    Txt(title.uppercase(), size = 19.sp, weight = FontWeight.Bold, family = t.heading, letterSpacing = 3.sp)
                }
                else -> Txt(title, size = 23.sp, weight = t.headingWeight, family = t.heading, letterSpacing = t.headingTracking.sp)
            }
            if (subtitle != null) {
                Spacer(h = 3.dp)
                when (t.look) {
                    // Cozy: a handwritten note under the heading, a little crooked
                    Look.Quilt -> Txt(subtitle, size = 18.sp, family = Fonts.caveat, color = t.accent,
                        modifier = Modifier.graphicsLayer { rotationZ = -1.5f; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) })
                    Look.Atlas -> Txt(subtitle, size = 13.sp, family = Fonts.spectral, italic = true, color = t.dim)
                    Look.Blossom -> Txt(subtitle, size = 14.sp, family = Fonts.cormorant, italic = true, color = t.dim)
                    else -> Mono(subtitle, size = 12.sp)
                }
            }
        }
        trailing?.invoke()
        if (action != null && onAction != null) LinkText(action, onAction, size = 13.sp, weight = FontWeight.Bold, color = t.mute)
    }
}

/** Сакура: a quiet Japanese word beside a section's heading. */
private fun sectionWord(title: String): String {
    val s = title.lowercase()
    return when {
        listOf("рекоменд", "для тебя", "подбор", "микс", "радио").any { it in s } -> "おすすめ"
        listOf("нов", "релиз").any { it in s } -> "新譜"
        listOf("медиатек", "плейлист", "альбом", "сохран").any { it in s } -> "書庫"
        listOf("недав", "истор", "слуша", "снова").any { it in s } -> "再生"
        listOf("исполн", "артист").any { it in s } -> "声"
        listOf("ноч", "вечер").any { it in s } -> "夜"
        else -> "花"
    }
}

fun openCollection(c: Collection) {
    when (c.kind) {
        "Album" -> Nav.go(Screen.Album(c.id))
        "Artist" -> Nav.go(Screen.Artist(c.id))
        else -> Nav.go(Screen.RemotePlaylist(c.id))
    }
}

/** A card: cover, title, subtitle; hover lifts it and shows a play button. */
@Composable
fun CollectionCard(c: Collection, width: Dp?, modifier: Modifier = Modifier, onPlay: (() -> Unit)? = null) {
    ContextMenuArea(items = { collectionMenu(c) }) { CollectionCardBody(c, width, modifier, onPlay) }
}

fun collectionMenu(c: Collection): List<ContextMenuItem> = listOfNotNull(
    ContextMenuItem("Открыть") { openCollection(c) },
    ContextMenuItem(if (Library.isSaved(c)) (if (c.kind == "Artist") "Отписаться" else "Убрать из медиатеки") else (if (c.kind == "Artist") "Подписаться" else "В медиатеку")) { Library.toggleSaved(c) },
    com.drxwnify.desktop.catalog.AppLinks.forCollection(c)?.let { text ->
        ContextMenuItem("Поделиться (скопировать ссылку)") { com.drxwnify.desktop.catalog.AppLinks.copy(text); Toast.show("Ссылка скопирована") }
    },
    c.cover?.let { ContextMenuItem("Открыть обложку") { CoverViewer.show(it, c.title) } },
)

@Composable
private fun CollectionCardBody(c: Collection, width: Dp?, modifier: Modifier, onPlay: (() -> Unit)?) {
    val t = theme
    val (src, hovered) = rememberHover()
    val lift by animateFloatAsState(if (hovered) -4f else 0f, tween(180))
    val bg by animateColorAsState(if (hovered) t.fill else Color.Transparent, tween(180))
    Column(
        (if (width != null) modifier.width(width) else modifier).graphicsLayer { translationY = lift }
            .clip(RoundedCornerShape(t.coverRadius + 4.dp)).background(bg).hoverable(src).hand()
            .clickable(interactionSource = src, indication = null) { openCollection(c) }.padding(8.dp),
    ) {
        Box {
            Cover(c.cover, Modifier.fillMaxWidth().aspectRatio(1f), shape = if (c.kind == "Artist") CircleShape else RoundedCornerShape(t.coverRadius))
            androidx.compose.animation.AnimatedVisibility(hovered && onPlay != null, Modifier.align(Alignment.BottomEnd).padding(8.dp), enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(t.accent).clickable { onPlay?.invoke() },
                    contentAlignment = Alignment.Center,
                ) { Ico(Ti.playerPlayFilled, size = 18.sp, color = t.onAccent) }
            }
        }
        Spacer(h = 10.dp)
        Txt(c.title, size = 15.sp, weight = FontWeight.Bold)
        if (c.subtitle.isNotBlank()) { Spacer(h = 2.dp); Mono(c.subtitle, size = 12.sp) }
    }
}

@Composable
fun CardRow(items: List<Collection>, onPlay: ((Collection) -> Unit)? = null) {
    FittedRow { w -> items(items, key = { it.kind + it.id }) { c -> CollectionCard(c, w, onPlay = onPlay?.let { { it(c) } }) } }
}

/** A row of cards that never ends in half a card: the card width is stretched so whole cards fill it. */
@Composable
private fun FittedRow(minCard: Dp = 176.dp, content: androidx.compose.foundation.lazy.LazyListScope.(Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val inset = 8.dp
        val full = maxWidth + inset * 2
        val n = maxOf(1, (full / minCard).toInt())
        val w = full / n
        val state = androidx.compose.foundation.lazy.rememberLazyListState()
        val (src, hovered) = rememberHover()
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        Box(Modifier.requiredWidth(full).hoverable(src)) {
            LazyRow(
                Modifier.fillMaxWidth(), state = state,
                flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(state),
            ) { content(w) }
            // centred on the covers (square, inside the cards' 8 dp padding)
            val y = inset + (w - inset * 2) / 2 - 20.dp
            RowArrow(Ti.chevronLeft, hovered && state.canScrollBackward, Modifier.align(Alignment.TopStart).padding(start = inset + 6.dp, top = y)) {
                scope.launch { state.animateScrollToItem((state.firstVisibleItemIndex - n).coerceAtLeast(0)) }
            }
            RowArrow(Ti.chevronRight, hovered && state.canScrollForward, Modifier.align(Alignment.TopEnd).padding(end = inset + 6.dp, top = y)) {
                scope.launch { state.animateScrollToItem(state.firstVisibleItemIndex + n) }
            }
        }
    }
}

/** A see-through round arrow over a row of cards; it pages the row by a screenful. */
@Composable
private fun RowArrow(glyph: String, visible: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val t = theme
    androidx.compose.animation.AnimatedVisibility(visible, modifier, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
        val (src, hovered) = rememberHover()
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(t.menu.copy(alpha = if (hovered) 0.95f else 0.75f))
                .border(1.dp, t.panelBorder, CircleShape).hoverable(src).hand()
                .clickable(interactionSource = src, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Ico(glyph, size = 20.sp, color = t.text) }
    }
}

/** Genre tags under a page's title (Last.fm / MusicBrainz, as on the phone); a click searches the genre. */
@Composable
fun GenreTags(tags: List<String>) {
    if (tags.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { g -> Chip(g, false, { Nav.searchText = g; Nav.go(Screen.Search(g)) }) }
    }
}

/** Tracks as cards in a row (the home's "Похоже на …" rows): a click plays the row from that track. */
@Composable
fun TrackCardRow(tracks: List<Track>, context: String) {
    FittedRow { w -> itemsIndexed(tracks, key = { _, t -> t.id }) { i, tr -> TrackCard(tr, w) { Player.play(tracks, i, context) } } }
}

@Composable
fun TrackCard(track: Track, width: Dp, onPlay: () -> Unit) {
    val t = theme
    val (src, hovered) = rememberHover()
    val lift by animateFloatAsState(if (hovered) -4f else 0f, tween(180))
    val bg by animateColorAsState(if (hovered) t.fill else Color.Transparent, tween(180))
    ContextMenuArea(items = { trackMenu(track) }) {
        Column(
            Modifier.width(width).graphicsLayer { translationY = lift }
                .clip(RoundedCornerShape(t.coverRadius + 4.dp)).background(bg).hoverable(src).hand()
                .clickable(interactionSource = src, indication = null, onClick = onPlay).padding(8.dp),
        ) {
            Box {
                Cover(track.cover, Modifier.fillMaxWidth().aspectRatio(1f))
                androidx.compose.animation.AnimatedVisibility(hovered, Modifier.align(Alignment.BottomEnd).padding(8.dp), enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(t.accent), contentAlignment = Alignment.Center) {
                        Ico(Ti.playerPlayFilled, size = 18.sp, color = t.onAccent)
                    }
                }
            }
            Spacer(h = 10.dp)
            Txt(track.title, size = 15.sp, weight = FontWeight.Bold)
            Spacer(h = 2.dp)
            Mono(track.artistLine, size = 12.sp)
        }
    }
}

/** Cards that fill the width: as many per row as fit. */
@Composable
fun CardGrid(items: List<Collection>, minWidth: Dp = 190.dp, rows: Int = Int.MAX_VALUE) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = maxOf(2, (maxWidth / minWidth).toInt())
        Column {
            items.chunked(cols).take(rows).forEach { line ->
                Row(Modifier.fillMaxWidth()) {
                    // the weight goes on a box around the card: inside its context-menu wrapper it did nothing,
                    // and the first card took the whole row
                    line.forEach { c -> Box(Modifier.weight(1f)) { CollectionCard(c, null, Modifier.fillMaxWidth()) } }
                    repeat(cols - line.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** One track in a list. Double click plays; right click opens the menu. */
@Composable
fun TrackRow(
    track: Track,
    index: Int?,
    onPlay: () -> Unit,
    showAlbum: Boolean = true,
    showCover: Boolean = true,
    extraMenu: List<ContextMenuItem> = emptyList(),
    /** The list this row is in: lets it be picked along with others (Ctrl / Shift + click). */
    list: List<Track>? = null,
) {
    val t = theme
    val now by Player.now.collectAsState()
    val state by Player.engine.state.collectAsState()
    val lib by Library.state.collectAsState()
    val downloads by Downloads.done.collectAsState()
    val active by Downloads.active.collectAsState()
    val isCurrent = now.track?.id == track.id
    val liked = lib.liked.any { it.id == track.id }
    val downloaded = downloads.done.any { it.track.id == track.id }
    val downloading = active[track.id]
    val pos = list?.let { l -> index?.takeIf { l.getOrNull(it)?.id == track.id } ?: l.indexOfFirst { it.id == track.id } } ?: -1
    val picking = list != null && Selection.isIn(list)
    val picked = picking && track.id in Selection.ids
    if (list != null) androidx.compose.runtime.SideEffect { Selection.onScreen = list }
    // Picked neighbours: the highlight runs on as one block, square where two picked rows meet.
    fun lit(i: Int) = list?.getOrNull(i)?.let { it.id in Selection.ids && picking || it.id == now.track?.id } == true
    val joinUp = (picked || isCurrent) && list != null && pos > 0 && lit(pos - 1)
    val joinDown = (picked || isCurrent) && list != null && pos >= 0 && lit(pos + 1)
    val r = 10.dp
    val rowShape = RoundedCornerShape(if (joinUp) 0.dp else r, if (joinUp) 0.dp else r, if (joinDown) 0.dp else r, if (joinDown) 0.dp else r)
    var mods by remember { mutableStateOf(androidx.compose.ui.input.pointer.PointerKeyboardModifiers(0)) }
    ContextMenuArea(items = {
        listOfNotNull(list?.takeIf { pos >= 0 }?.let { l -> ContextMenuItem(if (picked) "Убрать из выбранных" else "Выбрать") { Selection.toggle(l, pos) } }) + trackMenu(track) + extraMenu
    }) {
        HoverRow(
            onClick = {},
            onDoubleClick = onPlay, active = isCurrent || picked, shape = rowShape,
            // Picking happens on the press itself: through the click it waited out the double-click time.
            modifier = Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 2.dp)
                .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press, androidx.compose.ui.input.pointer.PointerEventPass.Initial) { e ->
                    mods = e.keyboardModifiers
                    if (list == null || pos < 0 || !e.buttons.isPrimaryPressed) return@onPointerEvent
                    when {
                        mods.isShiftPressed -> { Selection.extend(list, pos); e.changes.forEach { it.consume() } }
                        mods.isCtrlPressed -> { Selection.toggle(list, pos); e.changes.forEach { it.consume() } }
                        picking -> Selection.toggle(list, pos)
                    }
                },
        ) { hovered ->
            Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                when {
                    picking -> SelectBox(picked)
                    isCurrent && state == EngineState.Playing && !hovered -> EqBars()
                    hovered -> Box(Modifier.hand().clickable(onClick = onPlay)) { Ico(Ti.playerPlayFilled, size = 15.sp, color = t.text) }
                    index != null -> Mono("${index + 1}", size = 12.sp, color = if (isCurrent) t.accent else t.mute)
                }
            }
            if (showCover) { Cover(track.cover, Modifier.size(46.dp), shape = artShape(small = true)); Spacer(w = 14.dp) }
            Column(Modifier.weight(1f)) {
                Txt(track.title, size = 15.sp, weight = FontWeight.SemiBold, color = if (isCurrent) t.accent else t.text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (track.explicit) { Box(Modifier.clip(RoundedCornerShape(3.dp)).background(t.fillHover).padding(horizontal = 4.dp)) { Txt("E", size = 9.sp, weight = FontWeight.Bold, color = t.dim) }; Spacer(w = 6.dp) }
                    ArtistLinks(track.artists, size = 13.sp)
                }
            }
            if (showAlbum && track.album != null) {
                Box(Modifier.weight(0.7f)) { LinkText(track.album.title, track.album.id?.let { id -> { Nav.go(Screen.Album(id)) } }, size = 13.sp, color = t.mute) }
            }
            Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                when {
                    downloading != null && downloading.error == null -> Mono("${(downloading.progress * 100).toInt()}%", size = 10.sp, color = t.accent)
                    downloaded -> Ico(Ti.circleArrowDownFilled, size = 15.sp, color = t.accent)
                }
            }
            Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
                if (liked || hovered) Box(Modifier.hand().clickable { Library.toggleLike(track) }) {
                    Ico(if (liked) Ti.heartFilled else Ti.heart, size = 16.sp, color = if (liked) t.accent else t.mute)
                }
            }
            Mono(track.durationSec?.asDuration() ?: "", size = 12.sp, modifier = Modifier.width(46.dp))
        }
    }
}

fun trackMenu(track: Track): List<ContextMenuItem> = listOfNotNull(
    ContextMenuItem("Играть дальше") { Player.addNext(track) },
    ContextMenuItem("Добавить в очередь") { Player.addToQueue(track) },
    ContextMenuItem("Радио по треку") { if (Player.now.value.track?.id == track.id) Player.radioAfterCurrent() else Player.radio(track) },
    ContextMenuItem("Отправить файлом") { ShareFiles.send(listOf(track)) },
    com.drxwnify.desktop.catalog.AppLinks.forTrack(track)?.let { text ->
        ContextMenuItem("Поделиться (скопировать ссылку)") { com.drxwnify.desktop.catalog.AppLinks.copy(text); Toast.show("Ссылка скопирована") }
    },
    track.cover?.let { ContextMenuItem("Открыть обложку") { CoverViewer.show(it, "${track.artistLine} — ${track.title}") } },
    ContextMenuItem("Добавить в плейлист…") { showAddToPlaylist(listOf(track)) },
    ContextMenuItem("Другая версия…") { showVersions(track) },
    ContextMenuItem("О треке") { showTrackInfo(track) },
    Downloads.fileFor(track.id)?.let { f -> ContextMenuItem("Показать файл в папке") { revealFile(f) } },
    ContextMenuItem(if (Library.isLiked(track.id)) "Убрать из любимых" else "В любимые") { Library.toggleLike(track) },
    ContextMenuItem(if (Downloads.isDownloaded(track.id)) "Удалить файл" else "Скачать") {
        if (Downloads.isDownloaded(track.id)) Downloads.remove(track.id) else Downloads.enqueue(track)
    },
) + listOfNotNull(
        track.artists.firstOrNull()?.id?.let { id -> ContextMenuItem("К исполнителю") { Nav.go(Screen.Artist(id)) } },
        track.album?.id?.let { id -> ContextMenuItem("К альбому") { Nav.go(Screen.Album(id)) } },
    )

/** The header of an album / playlist / artist page with a soft glow from the cover's colour. */
@Composable
fun PageHeader(
    cover: String?,
    kind: String,
    title: String,
    subtitle: String,
    round: Boolean = false,
    art: (@Composable (Modifier) -> Unit)? = null,
    tags: List<String> = emptyList(),
    actions: @Composable () -> Unit,
) {
    val t = theme
    val tone by androidx.compose.runtime.produceState<Color?>(null, cover) { value = cover?.let { com.drxwnify.desktop.ui.Images.tone(it) } }
    val glow by animateColorAsState(tone?.copy(alpha = 0.32f) ?: Color.Transparent, tween(600))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(t.panelRadius)).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(glow, Color.Transparent)))) {
        Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 26.dp, bottom = 18.dp), verticalAlignment = Alignment.Bottom) {
            val artShape = if (round) CircleShape else if (t.look == Look.Terminal) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(t.coverRadius + 4.dp)
            val artMod = Modifier.size(196.dp).graphicsLayer { shadowElevation = 24f; shape = artShape; clip = true }
            if (art != null) art(artMod)
            else Tooltip("Открыть обложку") {
                if (round || t.look == Look.Glass) Cover(cover, artMod.clickHand { CoverViewer.show(cover, title) }, shape = artShape)
                else ThemedArt(cover, Modifier.size(196.dp).clickHand { CoverViewer.show(cover, title) }, seed = title.hashCode())
            }
            Spacer(w = 26.dp)
            Column(Modifier.weight(1f)) {
                Label(if (t.look == Look.Terminal) "[${kind.lowercase()}]" else kind.uppercase(), size = 11.sp, color = if (t.look == Look.Terminal) t.accent else t.dim)
                Spacer(h = 8.dp)
                Txt(if (t.headingUpper) title.uppercase() else title, size = if (title.length > 28) 34.sp else if (t.look == Look.Terminal) 34.sp else 46.sp,
                    weight = t.headingWeight, family = t.heading, maxLines = 2, letterSpacing = (if (t.headingUpper) t.headingTracking * 0.6f else -1f).sp, lineHeight = 48.sp,
                    brush = if (t.look == Look.Neon) Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.White, t.accent, t.accent2)) else null)
                Spacer(h = 10.dp)
                Txt(subtitle, size = 13.sp, color = t.dim, maxLines = 2)
                if (tags.isNotEmpty()) { Spacer(h = 10.dp); GenreTags(tags) }
                Spacer(h = 18.dp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { actions() }
            }
        }
    }
}

@Composable
fun Loading(text: String = "Загружаю…") {
    val t = theme
    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spinner()
            Spacer(h = 14.dp)
            Mono(text, size = 12.sp)
        }
    }
}

@Composable
fun Spinner(size: Dp = 26.dp) {
    val t = theme
    val tr = androidx.compose.animation.core.rememberInfiniteTransition()
    val a by tr.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(tween(900, easing = androidx.compose.animation.core.LinearEasing)))
    Box(Modifier.size(size).graphicsLayer { rotationZ = a }) { Ico(Ti.loader2, size = (size.value * 0.9f).sp, color = t.accent) }
}

@Composable
fun Empty(glyph: String, title: String, text: String, action: (@Composable () -> Unit)? = null) {
    val t = theme
    Column(Modifier.fillMaxWidth().padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Ico(glyph, size = 38.sp, color = t.mute)
        Spacer(h = 14.dp)
        Txt(title, size = 18.sp, weight = FontWeight.Bold)
        Spacer(h = 6.dp)
        Txt(text, size = 13.sp, color = t.dim, maxLines = 3, align = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(h = 18.dp); action() }
    }
}

@Composable
fun ColumnScope.Gap(h: Dp) = Spacer(h = h)

/** Liked tracks: the heart on the accent gradient. */
@Composable
fun HeartArt(m: Modifier) {
    val t = theme
    Box(m.background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(t.accent, t.accent2))), contentAlignment = Alignment.Center) {
        Ico(Ti.heartFilled, size = 72.sp, color = t.onAccent)
    }
}

/** A playlist without its own picture: the first four covers in a grid. */
@Composable
fun MosaicArt(covers: List<String?>, m: Modifier) {
    val t = theme
    val c = covers.filterNotNull().distinct()
    if (c.size < 4) { if (c.isEmpty()) Box(m.background(t.fill), contentAlignment = Alignment.Center) { Ico(Ti.playlist, size = 56.sp, color = t.mute) } else Cover(c.first(), m); return }
    Column(m) {
        for (r in 0..1) Row(Modifier.weight(1f)) { for (k in 0..1) Cover(c[r * 2 + k], Modifier.weight(1f).fillMaxHeight(), radius = 0.dp) }
    }
}
