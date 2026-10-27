package com.drxwnify.desktop

import com.drxwnify.desktop.data.Log
import com.drxwnify.desktop.data.Prefs
import com.drxwnify.desktop.playback.Player
import com.drxwnify.desktop.ui.Nav
import com.drxwnify.desktop.ui.RightPane
import com.drxwnify.desktop.ui.Screen
import com.drxwnify.desktop.ui.screens.HomeFeed
import kotlinx.coroutines.delay
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import javax.imageio.ImageIO

/**
 * Developer harness: DRXW_SCRIPT="wait:8000;shot:home.png;search:sewerslvt;wait:4000;shot:s.png"
 * drives the app and saves screenshots of the window (DRXW_SHOTS = folder). Off unless set.
 */
/** A scripted test run (DRXW_SCRIPT); [screen]: the monitor it was told to use (DRXW_SCREEN), out of the user's way. */
object TestRun {
    val active = System.getenv("DRXW_SCRIPT") != null
    val screen: java.awt.Rectangle? = System.getenv("DRXW_SCREEN")?.toIntOrNull()?.let { n ->
        runCatching { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.getOrNull(n)?.defaultConfiguration?.bounds }.getOrNull()
    }
}

object DebugScript {
    suspend fun run(window: java.awt.Window) {
        val script = System.getenv("DRXW_SCRIPT") ?: return
        val dir = File(System.getenv("DRXW_SHOTS") ?: ".").apply { mkdirs() }
        // Test runs keep the window above everything, so a screenshot never catches another app.
        window.isAlwaysOnTop = true
        if (TestRun.screen == null) window.toFront()
        for (step in script.split(';').map { it.trim() }.filter { it.isNotEmpty() }) {
            val (cmd, arg) = step.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            Log.i("Script", step)
            runCatching {
                when (cmd) {
                    "wait" -> delay(arg.toLong())
                    "shot" -> {
                        if (TestRun.screen == null) window.toFront()
                        delay(250)
                        if (!window.isShowing || (window as? java.awt.Frame)?.state == java.awt.Frame.ICONIFIED) { Log.w("Script", "window hidden — no screenshot"); return@runCatching }
                        val b = window.bounds
                        val img = Robot().createScreenCapture(Rectangle(b.x, b.y, b.width, b.height))
                        ImageIO.write(img, "png", File(dir, arg))
                    }
                    "search" -> { Nav.searchText = arg; Nav.go(Screen.Search(arg)) }
                    "home" -> Nav.home()
                    "theme" -> Prefs.settings.update { it.copy(theme = arg) }
                    "day" -> Prefs.settings.update { it.copy(themeDay = arg != "off") }
                    "settings" -> Nav.openSettings(arg)
                    "close" -> Nav.settingsOpen = false
                    "playrec" -> HomeFeed.rows.value?.firstOrNull { it.tracks.isNotEmpty() }?.tracks?.let { Player.play(it, arg.toIntOrNull() ?: 0, "Рекомендации") }
                    "playsearch" -> {
                        val r = com.drxwnify.desktop.catalog.Catalog.search(arg)
                        Player.play(r.tracks, 0, "Поиск")
                    }
                    "pane" -> Nav.right = RightPane.valueOf(arg)
                    "liked" -> Nav.go(Screen.Liked)
                    "downloads" -> Nav.go(Screen.Downloads)
                    "collapse" -> Prefs.settings.update { it.copy(leftCollapsed = arg != "off") }
                    "menu" -> com.drxwnify.desktop.ui.AccountMenuState.open = arg != "off"
                    "like" -> Player.now.value.track?.let { com.drxwnify.desktop.data.Library.toggleLike(it) }
                    "seek" -> Player.seek(arg.toLong())
                    "toggle" -> Player.toggle()
                    "vol" -> Player.setVolume(arg.toFloat())
                    "reviews" -> Nav.go(Screen.Reviews)
                    "discography" -> arg.split(',', limit = 2).let { (id, name) -> Nav.go(Screen.Discography(id, name)) }
                    "downloadliked" -> arg.split('-').map { it.trim().toInt() }.let { (from, to) -> com.drxwnify.desktop.data.Downloads.enqueue(com.drxwnify.desktop.data.Library.value.liked.subList(from, to + 1)) }
                    "dlstatus" -> {
                        val a = com.drxwnify.desktop.data.Downloads.active.value
                        Log.i("Script", "downloads: done=${com.drxwnify.desktop.data.Downloads.done.value.done.size} active=${a.values.count { it.error == null }} failed=${a.values.filter { it.error != null }.joinToString { "${it.track.title}: ${it.error}" }}")
                    }
                    "mute" -> { Player.toggleMute(); Log.i("Script", "volume=${Prefs.settings.value.volume} last=${Prefs.settings.value.lastVolume}") }
                    "trace" -> {
                        val seen = mutableListOf<Long>()
                        repeat(arg.toInt() / 15) { seen += Player.engine.position.value; delay(15) }
                        Log.i("Script", "positions: ${seen.joinToString()}")
                    }
                    "pickliked" -> arg.split(',').forEach { com.drxwnify.desktop.ui.screens.Selection.toggle(com.drxwnify.desktop.data.Library.value.liked, it.trim().toInt()) }
                    "rclick" -> if (TestRun.screen != null || !window.isActive) Log.w("Script", "window not in front: rclick skipped") else java.awt.Robot().let { r ->
                        val (x, y) = arg.split(',').map { it.trim().toInt() }
                        val back = java.awt.MouseInfo.getPointerInfo().location
                        val b = window.bounds
                        r.mouseMove(b.x + x, b.y + y); delay(80)
                        r.mousePress(java.awt.event.InputEvent.BUTTON3_DOWN_MASK); r.mouseRelease(java.awt.event.InputEvent.BUTTON3_DOWN_MASK)
                        delay(200); r.mouseMove(back.x, back.y)
                    }
                    "key" -> if (TestRun.screen != null || !window.isActive) Log.w("Script", "window not in front: key skipped") else java.awt.Robot().let { r ->
                        // "ctrl+alt+shift+32": press the modifiers, the key, let go in reverse
                        val parts = arg.split('+')
                        val codes = parts.dropLast(1).map { when (it) { "ctrl" -> java.awt.event.KeyEvent.VK_CONTROL; "alt" -> java.awt.event.KeyEvent.VK_ALT; else -> java.awt.event.KeyEvent.VK_SHIFT } } + parts.last().toInt()
                        codes.forEach { r.keyPress(it); delay(30) }
                        codes.asReversed().forEach { r.keyRelease(it); delay(30) }
                    }
                    "sharecheck" -> {
                        // Sends the picked tracks as files, reads the clipboard back, then puts the user's clipboard back as it was.
                        val cb = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                        val keep = listOf(java.awt.datatransfer.DataFlavor.javaFileListFlavor, java.awt.datatransfer.DataFlavor.imageFlavor, java.awt.datatransfer.DataFlavor.stringFlavor)
                            .mapNotNull { f -> runCatching { f to cb.getData(f) }.getOrNull() }.toMap()
                        com.drxwnify.desktop.ui.screens.ShareFiles.send(com.drxwnify.desktop.ui.screens.Selection.tracks)
                        delay(500)
                        var waited = 0
                        while (com.drxwnify.desktop.ui.screens.ShareFiles.busy && waited < 90_000) { delay(250); waited += 250 }
                        @Suppress("UNCHECKED_CAST")
                        val files = runCatching { cb.getData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as List<File> }.getOrNull()
                        Log.i("Script", "clipboard files: ${files?.joinToString { "${it.name} (${it.length() / 1024} KB)" }}")
                        if (keep.isNotEmpty()) cb.setContents(object : java.awt.datatransfer.Transferable {
                            override fun getTransferDataFlavors() = keep.keys.toTypedArray()
                            override fun isDataFlavorSupported(f: java.awt.datatransfer.DataFlavor) = f in keep
                            override fun getTransferData(f: java.awt.datatransfer.DataFlavor): Any = keep[f] ?: throw java.awt.datatransfer.UnsupportedFlavorException(f)
                        }, null)
                        Log.i("Script", "clipboard put back: ${keep.keys.joinToString { it.humanPresentableName }}")
                    }
                    "size" -> arg.split('x').map { it.trim().toInt() }.let { (w, h) -> window.setBounds(0, 0, w, h) }
                    "stall" -> Player.debugPlayAs(arg, "VK")
                    "remux" -> kotlin.concurrent.thread(isDaemon = true) {
                        val t0 = System.currentTimeMillis()
                        runCatching { com.drxwnify.desktop.data.Remux.run(arg, emptyMap(), dir, "remux-test-${System.nanoTime()}", emptyMap()) { } }
                            .onSuccess { Log.i("Script", "remux ok in ${System.currentTimeMillis() - t0} ms: ${it.file.name} ${it.file.length() / 1024} KB") }
                            .onFailure { Log.i("Script", "remux failed in ${System.currentTimeMillis() - t0} ms: ${it.message}") }
                    }
                    "album" -> Nav.go(Screen.Album(arg))
                    "artist" -> Nav.go(Screen.Artist(arg))
                    "log" -> Log.i("Script", "state=${Player.engine.state.value} pos=${Player.engine.position.value} dur=${Player.engine.duration.value} now=${Player.now.value} err=${Player.engine.lastError}")
                    "friends" -> Nav.go(Screen.Friends)
                    "download" -> Player.now.value.track?.let { com.drxwnify.desktop.data.Downloads.enqueue(it) }
                    "albumof" -> Player.now.value.track?.album?.id?.let { Nav.go(Screen.Album(it)) }
                    "artistof" -> Player.now.value.track?.artists?.firstOrNull()?.id?.let { Nav.go(Screen.Artist(it)) }
                    "cover" -> Player.now.value.track?.let { com.drxwnify.desktop.ui.screens.CoverViewer.show(it.cover, it.title) }
                    "uncover" -> com.drxwnify.desktop.ui.screens.CoverViewer.close()
                    "dialog" -> Player.now.value.track?.let { tr ->
                        when (arg) {
                            "versions" -> com.drxwnify.desktop.ui.screens.showVersions(tr)
                            "info" -> com.drxwnify.desktop.ui.screens.showTrackInfo(tr)
                            "add" -> com.drxwnify.desktop.ui.screens.showAddToPlaylist(listOf(tr))
                            "sleep" -> com.drxwnify.desktop.ui.screens.showSleepTimer()
                            else -> com.drxwnify.desktop.ui.screens.Dialogs.close()
                        }
                    }
                    "right" -> Prefs.settings.update { it.copy(rightHidden = arg == "off") }
                    // drag:x1,y1,x2,y2 inside the window (real mouse, window is on top in test runs)
                    "drag" -> {
                        val (x1, y1, x2, y2) = arg.split(',').map { it.trim().toInt() }
                        val b = window.bounds
                        val r = Robot()
                        r.mouseMove(b.x + x1, b.y + y1); delay(120)
                        r.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK); delay(120)
                        for (k in 1..20) { r.mouseMove(b.x + x1 + (x2 - x1) * k / 20, b.y + y1 + (y2 - y1) * k / 20); delay(16) }
                        delay(120); r.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                    }
                    "resetpanels" -> com.drxwnify.desktop.ui.resetPanels()
                    "panels" -> Log.i("Script", "panels: ${Prefs.settings.value.let { "left=${it.leftWidth} collapsed=${it.leftCollapsed} right=${it.rightWidth} hidden=${it.rightHidden}" }} window=${window.width}x${window.height}")
                    "importphone" -> com.drxwnify.desktop.ui.screens.Backup.importFile(File(arg))
                    "playliked" -> com.drxwnify.desktop.data.Library.value.liked.let { if (it.isNotEmpty()) Player.play(it, arg.toIntOrNull() ?: 0, "Любимые треки") }
                    "playlist" -> Nav.go(Screen.LocalPlaylist(arg))
                    "remote" -> Nav.go(Screen.RemotePlaylist(arg))
                    "refreshhome" -> com.drxwnify.desktop.ui.screens.HomeFeed.load(force = true)
                    "scroll" -> if (TestRun.screen != null) Log.w("Script", "quiet run: no scrolling") else java.awt.Robot().let { r -> val back = java.awt.MouseInfo.getPointerInfo().location; val b = window.bounds; r.mouseMove(b.x + b.width / 2, b.y + b.height / 2); r.mouseWheel(arg.toInt()); delay(120); r.mouseMove(back.x, back.y) }
                    "findname" -> Log.i("Script", "found: " + com.drxwnify.desktop.friends.Friends.searchByName(arg).joinToString { it.name })
                    "exit" -> kotlin.system.exitProcess(0)
                }
            }.onFailure { Log.w("Script", "step '$step' failed", it) }
        }
    }
}
