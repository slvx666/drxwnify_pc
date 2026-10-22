package com.drxwnify.desktop.ui

import androidx.compose.ui.input.pointer.isForwardPressed
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import com.drxwnify.desktop.ui.theme.Look
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.drxwnify.desktop.data.Prefs
import com.drxwnify.desktop.friends.Friends
import com.drxwnify.desktop.ui.screens.AccountMenu
import com.drxwnify.desktop.ui.screens.AlbumScreen
import com.drxwnify.desktop.ui.screens.ArtistScreen
import com.drxwnify.desktop.ui.screens.DownloadsScreen
import com.drxwnify.desktop.ui.screens.FriendsScreen
import com.drxwnify.desktop.ui.screens.HistoryScreen
import com.drxwnify.desktop.ui.screens.HomeScreen
import com.drxwnify.desktop.ui.screens.LibraryPanel
import com.drxwnify.desktop.ui.screens.LikedScreen
import com.drxwnify.desktop.ui.screens.LocalPlaylistScreen
import com.drxwnify.desktop.ui.screens.PlayerBar
import com.drxwnify.desktop.ui.screens.RemotePlaylistScreen
import com.drxwnify.desktop.ui.screens.RightPanel
import com.drxwnify.desktop.ui.screens.SearchScreen
import com.drxwnify.desktop.ui.screens.SettingsOverlay
import com.drxwnify.desktop.ui.screens.Toasts
import com.drxwnify.desktop.ui.theme.Fonts
import com.drxwnify.desktop.ui.theme.Themes
import com.drxwnify.desktop.playback.Player
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.awt.Cursor
import java.awt.MouseInfo

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FrameWindowScope.App(windowState: WindowState) {
    val settings by Prefs.settings.state.collectAsState()
    val t = Themes.byId(settings.theme, settings.themeDay)
    ProvideTheme(t) {
        val haze = rememberHazeState()
        CompositionLocalProvider(LocalHaze provides haze, androidx.compose.foundation.LocalContextMenuRepresentation provides ThemedContextMenu) {
            Box(
                Modifier.fillMaxSize()
                    .onPointerEvent(PointerEventType.Move) { e -> e.changes.firstOrNull()?.position?.let { Pointer.x = it.x; Pointer.y = it.y } }
                    .onPointerEvent(PointerEventType.Exit) { Pointer.x = -9999f; Pointer.y = -9999f }
                    // the mouse's side buttons: back and forward, as in a browser
                    .onPointerEvent(PointerEventType.Press) { e ->
                        if (e.buttons.isBackPressed) Nav.back() else if (e.buttons.isForwardPressed) Nav.forward()
                    }
                    .onPreviewKeyEvent { e -> Keys.handle(e) }
                    .onKeyEvent { e -> Keys.unhandled(e) },
            ) {
                ThemeBackground(t, settings.backgroundGlyphs, settings.backgroundMotion, Modifier.hazeSource(haze))
                Column(Modifier.fillMaxSize()) {
                    TopBar(windowState)
                    Body(Modifier.weight(1f).padding(top = if (t.look == com.drxwnify.desktop.ui.theme.Look.Terminal) 10.dp else 0.dp))
                    PlayerBar(Modifier.fillMaxWidth().height(100.dp).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp))
                }
                SettingsOverlay()
                AccountMenu()
                com.drxwnify.desktop.ui.screens.DialogHost()
                com.drxwnify.desktop.ui.screens.CoverViewerOverlay()
                Toasts()
                if (windowState.placement == WindowPlacement.Floating) ResizeEdges()
            }
        }
    }
}

@Composable
private fun Body(modifier: Modifier) {
    val settings by Prefs.settings.state.collectAsState()
    var leftLive by remember { mutableFloatStateOf(settings.leftWidth) }
    var rightLive by remember { mutableFloatStateOf(settings.rightWidth) }
    var draggingL by remember { mutableStateOf(false) }
    var draggingR by remember { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(settings.leftCollapsed) }
    var hiddenR by remember { mutableStateOf(settings.rightHidden) }
    LaunchedEffect(settings.leftCollapsed) { if (!draggingL) collapsed = settings.leftCollapsed }
    LaunchedEffect(settings.rightHidden) { if (!draggingR) hiddenR = settings.rightHidden }
    // "Вернуть как было" in the settings changes the saved widths: the panels follow.
    LaunchedEffect(settings.leftWidth) { if (!draggingL) leftLive = settings.leftWidth }
    LaunchedEffect(settings.rightWidth) { if (!draggingR) rightLive = settings.rightWidth }
    val lw by animateDpAsState(if (collapsed) 76.dp else leftLive.dp, if (draggingL) snap() else tween(260, easing = FastOutSlowInEasing))
    val rw by animateDpAsState(if (hiddenR) 0.dp else rightLive.dp, if (draggingR) snap() else tween(260, easing = FastOutSlowInEasing))

    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        LibraryPanel(collapsed, Modifier.width(lw).fillMaxHeight()) {
            collapsed = !collapsed
            Prefs.settings.update { it.copy(leftCollapsed = collapsed) }
        }
        // Widths follow the whole drag from where it started (a collapsed/hidden panel starts at its
        // small width): adding up only each move's delta never got a closed panel open again.
        var startL by remember { mutableFloatStateOf(0f) }
        var startR by remember { mutableFloatStateOf(0f) }
        DragHandle(
            onStart = { draggingL = true; startL = if (collapsed) 76f else leftLive },
            onDrag = { total ->
                val nw = startL + total
                if (nw < 150f) collapsed = true else { collapsed = false; leftLive = nw.coerceIn(220f, 460f) }
            },
            onEnd = {
                draggingL = false
                Prefs.settings.update { it.copy(leftWidth = leftLive, leftCollapsed = collapsed) }
            },
            onReset = {
                collapsed = false; leftLive = DEFAULT_LEFT
                Prefs.settings.update { it.copy(leftWidth = DEFAULT_LEFT, leftCollapsed = false) }
            },
            tooltip = "Потяни, чтобы изменить ширину · двойной щелчок — как было",
        )
        MainArea(Modifier.weight(1f).fillMaxHeight())
        DragHandle(
            onStart = { draggingR = true; startR = if (hiddenR) 0f else rightLive },
            onDrag = { total ->
                val nw = startR - total
                if (nw < 200f) hiddenR = true else { hiddenR = false; rightLive = nw.coerceIn(270f, 480f) }
            },
            onEnd = {
                draggingR = false
                if (rightLive < 270f) rightLive = DEFAULT_RIGHT
                Prefs.settings.update { it.copy(rightWidth = rightLive, rightHidden = hiddenR) }
            },
            onReset = {
                hiddenR = false; rightLive = DEFAULT_RIGHT
                Prefs.settings.update { it.copy(rightWidth = DEFAULT_RIGHT, rightHidden = false) }
            },
            tooltip = "Потяни, чтобы изменить ширину · двойной щелчок — как было",
        )
        if (rw > 1.dp) RightPanel(Modifier.width(rw).fillMaxHeight())
    }
}

const val DEFAULT_LEFT = 270f
const val DEFAULT_RIGHT = 310f

/** Puts both side panels back to how they were at first (also in Настройки → Внешний вид). */
fun resetPanels() = Prefs.settings.update { it.copy(leftWidth = DEFAULT_LEFT, leftCollapsed = false, rightWidth = DEFAULT_RIGHT, rightHidden = false) }

@Composable
private fun DragHandle(onStart: () -> Unit, onDrag: (Float) -> Unit, onEnd: () -> Unit, onReset: () -> Unit, tooltip: String) {
    val t = theme
    var hovered by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(false) }
    val line by animateColorAsState(if (hovered || active) t.accent.copy(alpha = 0.7f) else Color.Transparent, tween(150))
    Tooltip(tooltip) {
        Box(
            Modifier.width(10.dp).fillMaxHeight()
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            if (e.type == PointerEventType.Enter) hovered = true
                            if (e.type == PointerEventType.Exit) hovered = false
                        }
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onReset() }) }
                .pointerInput(Unit) {
                    var total = 0f
                    detectDragGestures(
                        onDragStart = { total = 0f; active = true; onStart() },
                        onDragEnd = { active = false; onEnd() },
                        onDragCancel = { active = false; onEnd() },
                    ) { change, drag -> change.consume(); total += drag.x / density; onDrag(total) }
                },
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.width(2.dp).fillMaxHeight(0.6f).clip(RoundedCornerShape(2.dp)).background(line)) }
    }
}

@Composable
private fun MainArea(modifier: Modifier) {
    val screen = Nav.current
    val smooth = Prefs.settings.state.collectAsState().value.smoothAnimations
    val t = theme
    Box(modifier) {
        if (t.look == Look.Terminal) Panel(Modifier.fillMaxSize(), title = screenTitle(screen), focused = true) { MainContent(screen, smooth) }
        else Box(Modifier.fillMaxSize()) { MainContent(screen, smooth) }
        com.drxwnify.desktop.ui.screens.SelectionBar(Modifier.align(Alignment.BottomCenter))
    }
}

private fun screenTitle(s: Screen) = when (s) {
    Screen.Home -> "главная"
    is Screen.Search -> "поиск"
    is Screen.Album -> "альбом"
    is Screen.Artist -> "исполнитель"
    is Screen.RemotePlaylist, is Screen.LocalPlaylist -> "плейлист"
    Screen.Liked -> "любимые"
    Screen.Friends -> "друзья"
    Screen.Downloads -> "загрузки"
    Screen.History -> "история"
    is Screen.Discography -> "дискография"
    Screen.Reviews -> "drxwnslvt's"
}

@Composable
private fun MainContent(screen: Screen, smooth: Boolean) {
    run {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val dir = Nav.direction
                if (!smooth) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                else (fadeIn(tween(220)) + slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 18 * dir })
                    .togetherWith(fadeOut(tween(140)) + slideOutHorizontally(tween(200)) { -it / 24 * dir })
            },
            contentKey = { if (it is Screen.Search) "search" else it },
        ) { s ->
            when (s) {
                Screen.Home -> HomeScreen()
                is Screen.Search -> SearchScreen()
                is Screen.Album -> AlbumScreen(s.id)
                is Screen.Artist -> ArtistScreen(s.id)
                is Screen.RemotePlaylist -> RemotePlaylistScreen(s.id)
                Screen.Liked -> LikedScreen()
                is Screen.LocalPlaylist -> LocalPlaylistScreen(s.id)
                Screen.Friends -> FriendsScreen()
                Screen.Downloads -> DownloadsScreen()
                Screen.History -> HistoryScreen()
                is Screen.Discography -> com.drxwnify.desktop.ui.screens.DiscographyScreen(s.artistId, s.artistName)
                Screen.Reviews -> com.drxwnify.desktop.ui.screens.ReviewsScreen()
            }
        }
    }
}

object SearchFocus { val requester = FocusRequester() }

@Composable
private fun FrameWindowScope.TopBar(windowState: WindowState) {
    val t = theme
    if (t.look == Look.Terminal) { Polybar(windowState); return }
    WindowDraggableArea(
        Modifier.fillMaxWidth().height(64.dp).pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { windowState.toggleMaximize() })
        },
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                Logo()
                Spacer(w = 18.dp)
                IconBtn(Ti.chevronLeft, { Nav.back() }, size = 34.dp, tint = if (Nav.canBack) t.dim else t.mute.copy(alpha = 0.5f), filled = true, tooltip = "Назад")
                Spacer(w = 6.dp)
                IconBtn(Ti.chevronRight, { Nav.forward() }, size = 34.dp, tint = if (Nav.canForward) t.dim else t.mute.copy(alpha = 0.5f), filled = true, tooltip = "Вперёд")
            }
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                val homeActive = Nav.current == Screen.Home && !Nav.settingsOpen
                IconBtn(if (homeActive) Ti.homeFilled else Ti.home, { Nav.home() }, size = 46.dp, iconSize = 21.sp, tint = if (homeActive) t.text else t.dim, filled = t.look != Look.Atlas, active = homeActive && t.look == Look.Atlas, tooltip = "Главная")
                Spacer(w = 8.dp)
                SearchField()
            }
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                val friendsOnline by Friends.onlineCount.collectAsState()
                IconBtn(Ti.users, { Nav.go(Screen.Friends) }, dot = friendsOnline > 0, active = Nav.current == Screen.Friends, tooltip = "Друзья")
                IconBtn(Ti.puzzle, { Nav.openSettings("integrations") }, tooltip = "Интеграции")
                IconBtn(Ti.download, { Nav.go(Screen.Downloads) }, active = Nav.current == Screen.Downloads, tooltip = "Скачанные треки")
                Spacer(w = 6.dp)
                PhonePill()
                Spacer(w = 10.dp)
                Avatar()
                Spacer(w = 14.dp)
                WindowButtons(windowState)
            }
        }
    }
}

/** The app's sign, 水 («вода»: Drxwnify — утонуть), drawn the way each theme draws things. */
const val BRAND_GLYPH = "水"

@Composable
private fun Logo() {
    val t = theme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.hand().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { Nav.home() }) {
        when (t.look) {
            Look.Neon -> {
                Txt(BRAND_GLYPH, size = 24.sp, family = Fonts.jpSerif, weight = FontWeight.SemiBold, color = t.accent,
                    modifier = Modifier.drawBehind { drawCircle(Brush.radialGradient(listOf(t.accent.copy(alpha = 0.35f), Color.Transparent)), size.minDimension) })
                Spacer(w = 10.dp)
                Txt("DRXWNIFY", size = 19.sp, family = Fonts.russo, letterSpacing = 2.sp,
                    brush = Brush.verticalGradient(if (t.night) listOf(Color.White, Color(0xFFFFD1F0), t.accent, Color(0xFF7B2CBF)) else listOf(Color(0xFF7B2CBF), t.accent, Color(0xFFFF9A3D))))
            }
            Look.Island -> {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f).compositeOver(t.accent), t.accent2, t.bg2))), contentAlignment = Alignment.Center) {
                    Txt(BRAND_GLYPH, size = 17.sp, family = Fonts.jpSerif, weight = FontWeight.SemiBold, color = t.onAccent)
                }
                Spacer(w = 10.dp)
                Txt("drxwnify", size = 17.sp, weight = FontWeight.Bold)
            }
            Look.Blossom -> {
                Box(Modifier.size(32.dp).graphicsLayer { rotationZ = -4f }.clip(RoundedCornerShape(6.dp)).background(Color(0xFFC8374F)), contentAlignment = Alignment.Center) {
                    Txt(BRAND_GLYPH, size = 18.sp, family = Fonts.jpSerif, weight = FontWeight.SemiBold, color = Color(0xFFFFF0F3))
                }
                Spacer(w = 10.dp)
                Txt("drxwnify", size = 23.sp, family = Fonts.cormorant, weight = FontWeight.SemiBold, italic = true)
            }
            Look.Quilt -> {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(t.accent), contentAlignment = Alignment.Center) {
                    Txt(BRAND_GLYPH, size = 18.sp, family = Fonts.jpSerif, weight = FontWeight.SemiBold, color = t.onAccent)
                }
                Spacer(w = 10.dp)
                Txt("drxwnify", size = 18.sp, family = Fonts.comfortaa, weight = FontWeight.Bold)
            }
            Look.Rite -> {
                Box(Modifier.size(32.dp).border(1.dp, t.accent), contentAlignment = Alignment.Center) {
                    Txt(BRAND_GLYPH, size = 17.sp, family = Fonts.jpSerif, weight = FontWeight.SemiBold, color = t.text)
                }
                Spacer(w = 12.dp)
                Txt("DRXWNIFY", size = 17.sp, family = Fonts.playfair, weight = FontWeight.Bold, letterSpacing = 5.sp)
            }
            Look.Atlas -> {
                Box(Modifier.size(36.dp).border(1.dp, t.accent2, CircleShape).padding(4.dp).border(1.dp, t.accent2.copy(alpha = 0.45f), CircleShape), contentAlignment = Alignment.Center) {
                    Txt(BRAND_GLYPH, size = 16.sp, family = Fonts.jpSerif, weight = FontWeight.SemiBold, color = t.accent)
                }
                Spacer(w = 12.dp)
                Txt("DRXWNIFY", size = 19.sp, family = Fonts.forum, letterSpacing = 5.sp)
            }
            else -> {
                Txt(BRAND_GLYPH, size = 24.sp, family = Fonts.jpSerif, weight = FontWeight.SemiBold, color = t.accent)
                Spacer(w = 9.dp)
                Txt("drxwnify", size = 16.sp, weight = FontWeight.ExtraBold, family = Fonts.manrope)
            }
        }
    }
}

@Composable
private fun SearchField() {
    val t = theme
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) t.accent.copy(alpha = 0.7f) else if (t.look == Look.Neon) t.accent.copy(alpha = 0.45f) else t.panelBorder, tween(180))
    val w by animateDpAsState(if (focused) 460.dp else 400.dp, tween(260, easing = FastOutSlowInEasing))
    val shape = RoundedCornerShape(when (t.look) { Look.Neon -> 8.dp; Look.Quilt -> 16.dp; Look.Atlas -> 0.dp; Look.Rite -> 2.dp; else -> 99.dp })
    val frame = when (t.look) {
        // Обсерватория: just a gold line under the words, like a field on an old form
        Look.Atlas -> Modifier.drawBehind { drawLine(border, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1.dp.toPx()) }
        Look.Quilt -> Modifier.clip(shape).background(t.fill).drawBehind {
            val i = 5.dp.toPx()
            drawRoundRect(t.panelBorder, Offset(i, i), Size(size.width - 2 * i, size.height - 2 * i), CornerRadius(11.dp.toPx()),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))))
        }
        else -> Modifier.clip(shape).background(t.fill).border(1.dp, border, shape)
    }
    Row(
        Modifier.width(w).height(46.dp).then(frame).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (t.look == Look.Atlas) Txt("ИСКАТЬ", size = 13.sp, family = Fonts.forum, letterSpacing = 3.sp, color = t.accent)
        else Ico(Ti.search, size = 19.sp, color = if (focused) t.text else if (t.look == Look.Neon) t.accent else t.dim)
        Spacer(w = 12.dp)
        Box(Modifier.weight(1f)) {
            if (Nav.searchText.isEmpty()) Txt(
                when (t.look) { Look.Quilt -> "Что поставим сегодня?"; Look.Atlas -> "трек, альбом, исполнитель или ссылка"; else -> "Что хочешь включить?" },
                size = if (t.look == Look.Atlas) 15.sp else 14.sp, color = t.mute, italic = t.look == Look.Atlas,
            )
            BasicTextField(
                value = Nav.searchText,
                onValueChange = { v ->
                    Nav.searchText = v
                    if (v.isNotBlank()) { if (Nav.current !is Screen.Search) Nav.go(Screen.Search(v)) }
                },
                singleLine = true,
                textStyle = TextStyle(color = t.text, fontSize = 14.sp, fontFamily = t.body),
                cursorBrush = SolidColor(t.accent),
                modifier = Modifier.fillMaxWidth().focusRequester(SearchFocus.requester).onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { Nav.searchText = ""; true } else false
                    },
            )
        }
        if (Nav.searchText.isNotEmpty()) IconBtn(Ti.x, { Nav.searchText = "" }, size = 26.dp, iconSize = 15.sp)
        else Mono("Ctrl K", size = 11.sp)
        Spacer(w = 10.dp)
        Box(Modifier.width(1.dp).height(22.dp).background(t.panelBorder))
        Spacer(w = 6.dp)
        IconBtn(Ti.link, { Nav.go(Screen.Search(Nav.searchText)); SearchFocus.requester.requestFocus() }, size = 30.dp, iconSize = 17.sp, tooltip = "Вставь ссылку Spotify, VK или YouTube — найду")
    }
}

/** Gruvbox: the top of a tiling desktop — workspaces, a rofi-like "run" box, coloured modules. */
@Composable
private fun FrameWindowScope.Polybar(windowState: WindowState) {
    val t = theme
    val friendsOnline by Friends.onlineCount.collectAsState()
    val active by com.drxwnify.desktop.data.Downloads.active.collectAsState()
    val settings by Prefs.settings.state.collectAsState()
    val clock by androidx.compose.runtime.produceState(java.time.LocalTime.now().withSecond(0).withNano(0)) {
        while (true) { kotlinx.coroutines.delay(15_000); value = java.time.LocalTime.now().withSecond(0).withNano(0) }
    }
    WindowDraggableArea(Modifier.fillMaxWidth().height(40.dp).background(t.panel).pointerInput(Unit) { detectTapGestures(onDoubleTap = { windowState.toggleMaximize() }) }) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.fillMaxHeight().background(t.accent).clickHand { Nav.home() }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Txt(BRAND_GLYPH, size = 17.sp, family = Fonts.jpSerif, weight = FontWeight.SemiBold, color = t.onAccent)
            }
            listOf<Triple<String, Boolean, () -> Unit>>(
                Triple("1 главная", Nav.current == Screen.Home) { Nav.home() },
                Triple("2 поиск", Nav.current is Screen.Search) { Nav.go(Screen.Search(Nav.searchText)); SearchFocus.requester.requestFocus() },
                Triple("3 друзья", Nav.current == Screen.Friends) { Nav.go(Screen.Friends) },
                Triple("4 загрузки", Nav.current == Screen.Downloads) { Nav.go(Screen.Downloads) },
            ).forEach { (label, on, go) ->
                Box(Modifier.fillMaxHeight().background(if (on) t.fillHover else Color.Transparent).clickHand(go).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Txt(label, size = 12.sp, weight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) t.accent else t.dim)
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(Modifier.width(460.dp).height(30.dp).border(2.dp, t.panelBorder).background(t.bg2), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.fillMaxHeight().background(t.accent).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) { Txt("run", size = 12.sp, weight = FontWeight.Bold, color = t.onAccent) }
                    Spacer(w = 10.dp)
                    Box(Modifier.weight(1f)) {
                        if (Nav.searchText.isEmpty()) Txt("что хочешь включить… трек, альбом, ссылка", size = 12.sp, color = t.mute)
                        BasicTextField(
                            value = Nav.searchText,
                            onValueChange = { v -> Nav.searchText = v; if (v.isNotBlank() && Nav.current !is Screen.Search) Nav.go(Screen.Search(v)) },
                            singleLine = true,
                            textStyle = TextStyle(color = t.text, fontSize = 13.sp, fontFamily = t.body),
                            cursorBrush = SolidColor(t.text),
                            modifier = Modifier.fillMaxWidth().focusRequester(SearchFocus.requester)
                                .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { Nav.searchText = ""; true } else false },
                        )
                    }
                    Txt("C-k", size = 11.sp, color = t.mute, modifier = Modifier.padding(horizontal = 10.dp))
                }
            }
            val modules = listOf<Triple<String, Color, () -> Unit>>(
                Triple(if (com.drxwnify.desktop.playback.VkSource.available()) "VK ●" else "VK ○", t.sample[0]) { Nav.openSettings("integrations") },
                Triple("↓ ${active.size}", t.sample[1]) { Nav.go(Screen.Downloads) },
                Triple("друзья${if (friendsOnline > 0) " $friendsOnline ●" else ""}", t.sample[2]) { Nav.go(Screen.Friends) },
                Triple("vol ${(settings.volume * 100).toInt()}%", t.sample[3]) { Player.toggleMute() },
                Triple("%02d:%02d".format(clock.hour, clock.minute), t.accent) { },
            )
            modules.forEach { (label, color, go) ->
                Box(Modifier.fillMaxHeight().background(color).clickHand(go).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Txt(label, size = 12.sp, weight = FontWeight.Bold, color = t.onAccent)
                }
            }
            Spacer(w = 8.dp)
            Avatar()
            Spacer(w = 6.dp)
            WindowButtons(windowState)
        }
    }
}

@Composable
private fun PhonePill() {
    val t = theme
    val paired by com.drxwnify.desktop.sync.PhoneSync.state.collectAsState()
    val (src, hovered) = rememberHover()
    val bg by animateColorAsState(if (hovered) t.fillHover else t.fill, tween(150))
    Tooltip(if (paired.paired) "Синхронизировано с ${paired.deviceName}" else "Связать с телефоном") {
        Row(
            Modifier.height(34.dp).clip(RoundedCornerShape(99.dp)).background(bg).hoverable(src).hand()
                .clickable(interactionSource = src, indication = null) { Nav.openSettings("sync") }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ico(Ti.deviceMobile, size = 16.sp, color = if (paired.paired) t.accent else t.mute)
            Spacer(w = 7.dp)
            Txt(if (paired.paired) paired.deviceName else "Телефон", size = 12.sp, weight = FontWeight.Bold, color = if (paired.paired) t.dim else t.mute)
            if (paired.paired) { Spacer(w = 8.dp); Dot(if (paired.online) t.ok else t.mute) }
        }
    }
}

object AccountMenuState { var open by mutableStateOf(false) }

@Composable
private fun Avatar() {
    val t = theme
    val me by Friends.me.collectAsState()
    val (src, hovered) = rememberHover()
    val ring by animateColorAsState(if (hovered || AccountMenuState.open) t.accent else Color.Transparent, tween(150))
    val avatarShape = if (t.look == Look.Terminal) androidx.compose.ui.graphics.RectangleShape else CircleShape
    Box(
        Modifier.size(36.dp).clip(avatarShape).border(2.dp, ring, avatarShape).padding(3.dp).clip(avatarShape)
            .background(t.fillHover).hoverable(src).hand()
            .clickable(interactionSource = src, indication = null) { AccountMenuState.open = !AccountMenuState.open },
        contentAlignment = Alignment.Center,
    ) {
        com.drxwnify.desktop.ui.screens.AvatarPic(me?.picture, me?.name ?: "D", 30.dp)
    }
}

@Composable
private fun FrameWindowScope.WindowButtons(state: WindowState) {
    val t = theme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        WinBtn(Ti.minus) { window.isMinimized = true }
        WinBtn(if (state.placement == WindowPlacement.Maximized) Ti.copy else Ti.square) { state.toggleMaximize() }
        WinBtn(Ti.x, danger = true) { window.dispatchEvent(java.awt.event.WindowEvent(window, java.awt.event.WindowEvent.WINDOW_CLOSING)) }
    }
}

@Composable
private fun WinBtn(glyph: String, danger: Boolean = false, onClick: () -> Unit) {
    val t = theme
    val (src, hovered) = rememberHover()
    val bg by animateColorAsState(if (hovered) (if (danger) Color(0xFFC4314B) else t.fillHover) else Color.Transparent, tween(120))
    Box(
        Modifier.size(width = 40.dp, height = 32.dp).clip(RoundedCornerShape(8.dp)).background(bg).hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ico(glyph, size = 15.sp, color = if (hovered) t.text else t.mute) }
}

fun WindowState.toggleMaximize() {
    placement = if (placement == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized
    if (!com.drxwnify.desktop.TestRun.active) Prefs.settings.update { it.copy(windowMaximized = placement == WindowPlacement.Maximized) }
}

/** Undecorated windows have no system borders: these strips resize the window. */
@Composable
private fun FrameWindowScope.ResizeEdges() {
    val e = 6.dp
    Box(Modifier.fillMaxSize()) {
        Edge(Modifier.align(Alignment.CenterStart).width(e).fillMaxHeight(), Cursor.W_RESIZE_CURSOR, l = true)
        Edge(Modifier.align(Alignment.CenterEnd).width(e).fillMaxHeight(), Cursor.E_RESIZE_CURSOR, r = true)
        Edge(Modifier.align(Alignment.TopCenter).height(e).fillMaxWidth(), Cursor.N_RESIZE_CURSOR, tp = true)
        Edge(Modifier.align(Alignment.BottomCenter).height(e).fillMaxWidth(), Cursor.S_RESIZE_CURSOR, b = true)
        Edge(Modifier.align(Alignment.BottomEnd).size(14.dp), Cursor.SE_RESIZE_CURSOR, r = true, b = true)
        Edge(Modifier.align(Alignment.BottomStart).size(14.dp), Cursor.SW_RESIZE_CURSOR, l = true, b = true)
        Edge(Modifier.align(Alignment.TopEnd).size(10.dp), Cursor.NE_RESIZE_CURSOR, r = true, tp = true)
        Edge(Modifier.align(Alignment.TopStart).size(10.dp), Cursor.NW_RESIZE_CURSOR, l = true, tp = true)
    }
}

@Composable
private fun FrameWindowScope.Edge(modifier: Modifier, cursor: Int, l: Boolean = false, r: Boolean = false, tp: Boolean = false, b: Boolean = false) {
    Box(
        modifier.pointerHoverIcon(PointerIcon(Cursor(cursor))).pointerInput(Unit) {
            var start = java.awt.Point(); var bounds = java.awt.Rectangle()
            detectDragGestures(
                onDragStart = { start = MouseInfo.getPointerInfo().location; bounds = window.bounds },
            ) { change, _ ->
                change.consume()
                val p = MouseInfo.getPointerInfo().location
                val dx = p.x - start.x; val dy = p.y - start.y
                val min = window.minimumSize
                var x = bounds.x; var y = bounds.y; var w = bounds.width; var h = bounds.height
                if (r) w = maxOf(min.width, bounds.width + dx)
                if (b) h = maxOf(min.height, bounds.height + dy)
                if (l) { w = maxOf(min.width, bounds.width - dx); x = bounds.x + bounds.width - w }
                if (tp) { h = maxOf(min.height, bounds.height - dy); y = bounds.y + bounds.height - h }
                window.setBounds(x, y, w, h)
            }
        },
    )
}

/** Shortcuts: Space, Ctrl+K, Ctrl+←/→, Ctrl+↑/↓, Alt+←. */
object Keys {
    var typing = false
    fun handle(e: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (com.drxwnify.desktop.Hotkeys.recording) return false
        (e.nativeKeyEvent as? java.awt.event.KeyEvent)?.let { if (com.drxwnify.desktop.Hotkeys.handleInWindow(it)) return true }
        if (e.type != KeyEventType.KeyDown) return false
        val ctrl = e.isCtrlPressed
        when {
            ctrl && e.key == Key.K -> { SearchFocus.requester.requestFocus(); return true }
            ctrl && e.key == Key.DirectionRight -> { Player.next(); return true }
            ctrl && e.key == Key.DirectionLeft -> { Player.previous(); return true }
            ctrl && e.key == Key.DirectionUp -> { Player.setVolume((Player.engine.volume + 0.05f).coerceAtMost(1f)); return true }
            ctrl && e.key == Key.DirectionDown -> { Player.setVolume((Player.engine.volume - 0.05f).coerceAtLeast(0f)); return true }
            e.isAltPressed && e.key == Key.DirectionLeft -> { Nav.back(); return true }
            e.key == Key.Escape && com.drxwnify.desktop.ui.screens.Dialogs.current != null && com.drxwnify.desktop.ui.screens.CoverViewer.shown == null -> { com.drxwnify.desktop.ui.screens.Dialogs.close(); return true }
            e.key == Key.Escape && com.drxwnify.desktop.ui.screens.CoverViewer.shown != null -> { com.drxwnify.desktop.ui.screens.CoverViewer.close(); return true }
            e.key == Key.Escape && (Nav.settingsOpen || AccountMenuState.open) -> { Nav.settingsOpen = false; AccountMenuState.open = false; return true }
            e.key == Key.Escape && com.drxwnify.desktop.ui.screens.Selection.active -> { com.drxwnify.desktop.ui.screens.Selection.clear(); return true }
        }
        return false
    }

    /** Keys nothing on the page took (a text field keeps its spaces): Space pauses and plays. */
    fun unhandled(e: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (com.drxwnify.desktop.Hotkeys.recording || e.type != KeyEventType.KeyDown) return false
        if (e.key == Key.Spacebar && !e.isCtrlPressed && !e.isAltPressed) { Player.toggle(); return true }
        if (e.key == Key.A && e.isCtrlPressed) {
            com.drxwnify.desktop.ui.screens.Selection.onScreen?.takeIf { it.isNotEmpty() }?.let { com.drxwnify.desktop.ui.screens.Selection.selectAll(it); return true }
        }
        return false
    }
}

private val androidx.compose.ui.input.key.KeyEvent.isCtrlPressed get() = this.isCtrlPressedCompat()
private fun androidx.compose.ui.input.key.KeyEvent.isCtrlPressedCompat(): Boolean =
    (nativeKeyEvent as? java.awt.event.KeyEvent)?.isControlDown ?: false
private val androidx.compose.ui.input.key.KeyEvent.isAltPressed get() =
    (nativeKeyEvent as? java.awt.event.KeyEvent)?.isAltDown ?: false
