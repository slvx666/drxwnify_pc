package com.drxwnify.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.drxwnify.desktop.catalog.YouTubeCatalog
import com.drxwnify.desktop.data.Log
import com.drxwnify.desktop.data.Prefs
import com.drxwnify.desktop.playback.AudioEngine
import com.drxwnify.desktop.playback.Player
import com.drxwnify.desktop.ui.App
import com.drxwnify.desktop.ui.screens.Backup
import com.drxwnify.desktop.ui.screens.HomeFeed
import java.awt.Dimension
import kotlin.concurrent.thread

fun main() {
    System.setProperty("skiko.renderApi", System.getProperty("skiko.renderApi") ?: "DIRECT3D")
    Thread.setDefaultUncaughtExceptionHandler { t, e -> Log.e("Crash", "uncaught on ${t.name}", e) }
    if (System.getenv("DRXW_SCRIPT") == null && !SingleInstance.claim()) return
    Backup.applyPending()
    Log.i("App", "Drxwnify desktop starting")
    thread(name = "ffmpeg-warmup", isDaemon = true) { AudioEngine.warmUp() }
    YouTubeCatalog.init()
    com.drxwnify.desktop.catalog.SpotifyCatalog.init()
    com.metrolist.innertube.YouTube.locale = com.metrolist.innertube.models.YouTubeLocale(Prefs.settings.value.contentRegion, "ru")
    Player.init()
    com.drxwnify.desktop.playback.VkSource.init()
    com.drxwnify.desktop.data.RemoteConfig.init()
    Hotkeys.init()
    com.drxwnify.desktop.friends.Friends.init()
    HomeFeed.load()

    application {
        val s = Prefs.settings.value
        // Test runs with DRXW_SCREEN=<n>: the window opens on that monitor and never takes focus (the user may be in a game on another one).
        val testScreen = TestRun.screen
        val state = rememberWindowState(
            size = testScreen?.let { DpSize(it.width.dp, (it.height - 40).dp) } ?: DpSize(s.windowW.dp, s.windowH.dp),
            position = testScreen?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition(Alignment.Center),
            placement = if (testScreen == null && s.windowMaximized) WindowPlacement.Maximized else WindowPlacement.Floating,
        )
        val playing by Player.engine.state.collectAsState()
        val nowTrack by Player.now.collectAsState()
        Tray(
            icon = painterResource("icon.png"),
            tooltip = nowTrack.track?.let { "Drxwnify — ${it.title} · ${it.artistLine}" } ?: "Drxwnify",
            menu = {
                Item(if (playing == com.drxwnify.desktop.playback.EngineState.Playing) "Пауза" else "Играть", onClick = { Player.toggle() })
                Item("Следующий трек", onClick = { Player.next() })
                Item("Предыдущий трек", onClick = { Player.previous() })
                Separator()
                Item("Закрыть Drxwnify", onClick = ::exitApplication)
            },
        )
        Window(
            onCloseRequest = ::exitApplication,
            state = state,
            title = "Drxwnify",
            undecorated = true,
            focusable = testScreen == null,
            alwaysOnTop = testScreen != null,
            icon = painterResource("icon.png"),
        ) {
            LaunchedEffect(Unit) { DebugScript.run(window) }
            LaunchedEffect(Unit) { com.drxwnify.desktop.playback.MediaOverlay.start(window) }
            LaunchedEffect(Unit) {
                snapshotFlow { state.isMinimized }.collect { m ->
                    com.drxwnify.desktop.ui.AnimationGate.minimized = m
                    Player.engine.visualsWanted = !m
                }
            }
            DisposableEffect(Unit) {
                val l = object : java.awt.event.WindowFocusListener {
                    override fun windowGainedFocus(e: java.awt.event.WindowEvent?) { com.drxwnify.desktop.ui.AnimationGate.focused = true }
                    override fun windowLostFocus(e: java.awt.event.WindowEvent?) { com.drxwnify.desktop.ui.AnimationGate.focused = false }
                }
                window.addWindowFocusListener(l)
                onDispose { window.removeWindowFocusListener(l) }
            }
            LaunchedEffect(Unit) {
                SingleInstance.showRequests.collect { n ->
                    if (n > 0) { window.isMinimized = false; window.toFront(); window.requestFocus() }
                }
            }
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(1040, 660)
                snapshotFlow { state.size }.collect { sz ->
                    if (state.placement == WindowPlacement.Floating && !TestRun.active) Prefs.settings.update { it.copy(windowW = sz.width.value.toInt(), windowH = sz.height.value.toInt()) }
                }
            }
            val settings by Prefs.settings.state.collectAsState()
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density * settings.uiScale, base.fontScale)) {
                App(state)
            }
        }
    }
}
