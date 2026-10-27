package com.drxwnify.desktop

import com.drxwnify.desktop.data.Downloads
import com.drxwnify.desktop.data.Library
import com.drxwnify.desktop.data.Log
import com.drxwnify.desktop.data.Prefs
import com.drxwnify.desktop.data.ioScope
import com.drxwnify.desktop.playback.Player
import com.drxwnify.desktop.ui.screens.Toast
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.awt.event.KeyEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

/** A key with modifiers. [mods]: [CTRL] | [ALT] | [SHIFT]; [key]: an AWT key code (the same as Windows' for letters, digits, arrows, F-keys). */
data class Combo(val mods: Int, val key: Int) {
    val label: String
        get() = buildList {
            if (mods and CTRL != 0) add("Ctrl")
            if (mods and ALT != 0) add("Alt")
            if (mods and SHIFT != 0) add("Shift")
            add(keyName(key))
        }.joinToString(" + ")

    fun encode() = buildList {
        if (mods and CTRL != 0) add("ctrl")
        if (mods and ALT != 0) add("alt")
        if (mods and SHIFT != 0) add("shift")
        add(key.toString())
    }.joinToString("+")

    companion object {
        const val CTRL = 1
        const val ALT = 2
        const val SHIFT = 4

        fun decode(s: String): Combo? {
            val parts = s.split('+')
            val key = parts.lastOrNull()?.toIntOrNull() ?: return null
            var mods = 0
            parts.dropLast(1).forEach { p -> mods = mods or when (p) { "ctrl" -> CTRL; "alt" -> ALT; "shift" -> SHIFT; else -> 0 } }
            return Combo(mods, key)
        }

        fun of(e: KeyEvent): Combo = Combo(
            (if (e.isControlDown) CTRL else 0) or (if (e.isAltDown) ALT else 0) or (if (e.isShiftDown) SHIFT else 0),
            e.keyCode,
        )

        fun keyName(code: Int): String = when (code) {
            KeyEvent.VK_SPACE -> "Пробел"
            KeyEvent.VK_LEFT -> "←"
            KeyEvent.VK_RIGHT -> "→"
            KeyEvent.VK_UP -> "↑"
            KeyEvent.VK_DOWN -> "↓"
            KeyEvent.VK_ENTER -> "Enter"
            else -> KeyEvent.getKeyText(code)
        }

        fun isModifier(code: Int) = code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_ALT || code == KeyEvent.VK_SHIFT ||
            code == KeyEvent.VK_ALT_GRAPH || code == KeyEvent.VK_WINDOWS || code == KeyEvent.VK_META
    }
}

/**
 * Actions on keys that work anywhere in Windows (in a game, with the window minimised): registered with
 * the system, so nothing else sees the combination. Defaults are three-modifier combinations nobody
 * presses by accident. With [com.drxwnify.desktop.data.AppSettings.globalHotkeys] off they work only
 * while this window has focus.
 */
enum class HotkeyAction(val id: String, val label: String, val default: Combo) {
    PlayPause("playPause", "Пауза / играть", Combo(Combo.CTRL or Combo.ALT or Combo.SHIFT, KeyEvent.VK_SPACE)),
    Next("next", "Следующий трек", Combo(Combo.CTRL or Combo.ALT or Combo.SHIFT, KeyEvent.VK_RIGHT)),
    Previous("previous", "Предыдущий трек", Combo(Combo.CTRL or Combo.ALT or Combo.SHIFT, KeyEvent.VK_LEFT)),
    Like("like", "В любимые / убрать из любимых", Combo(Combo.CTRL or Combo.ALT or Combo.SHIFT, KeyEvent.VK_L)),
    Download("download", "Скачать текущий трек", Combo(Combo.CTRL or Combo.ALT or Combo.SHIFT, KeyEvent.VK_D)),
    Radio("radio", "Радио от текущего трека", Combo(Combo.CTRL or Combo.ALT or Combo.SHIFT, KeyEvent.VK_R)),
}

object Hotkeys {
    /** The combination set for [a] now (null — switched off). */
    fun comboOf(a: HotkeyAction): Combo? {
        val saved = Prefs.settings.value.hotkeys[a.id] ?: return a.default
        return if (saved.isEmpty()) null else Combo.decode(saved)
    }

    fun set(a: HotkeyAction, c: Combo?) {
        Prefs.settings.update { s ->
            // One combination does one thing: whoever had it before loses it.
            val cleared = if (c == null) s.hotkeys else HotkeyAction.entries.filter { it != a && comboOf(it) == c }.fold(s.hotkeys) { m, o -> m + (o.id to "") }
            s.copy(hotkeys = cleared + (a.id to (c?.encode() ?: "")))
        }
    }

    fun reset(a: HotkeyAction) = Prefs.settings.update { it.copy(hotkeys = it.hotkeys - a.id) }

    /** While a new combination is being typed in the settings, the registered ones let the keys through. */
    @Volatile var recording = false
        set(v) { field = v; if (v) GlobalHotkeys.stop() else apply() }

    fun init() {
        // A test run must not grab the keys of the app the user is busy with.
        if (TestRun.active && System.getenv("DRXW_HOTKEYS") == null) return
        ioScope.launch {
            Prefs.settings.state.map { it.hotkeys to it.globalHotkeys }.distinctUntilChanged().collect { if (!recording) apply() }
        }
    }

    private fun apply() {
        val s = Prefs.settings.value
        if (!s.globalHotkeys) { GlobalHotkeys.stop(); return }
        GlobalHotkeys.apply(HotkeyAction.entries.mapNotNull { a -> comboOf(a)?.let { a to it } }.toMap())
    }

    /** The window's own key handling: actions that aren't registered system-wide are caught here. */
    fun handleInWindow(e: KeyEvent): Boolean {
        if (recording || e.id != KeyEvent.KEY_PRESSED || Combo.isModifier(e.keyCode)) return false
        val c = Combo.of(e)
        if (c.mods == 0) return false
        val a = HotkeyAction.entries.firstOrNull { comboOf(it) == c } ?: return false
        if (Prefs.settings.value.globalHotkeys && a.id in GlobalHotkeys.registered.value) return false
        perform(a)
        return true
    }

    fun perform(a: HotkeyAction) {
        Log.i("Hotkeys", a.id)
        val track = Player.now.value.track
        when (a) {
            HotkeyAction.PlayPause -> Player.toggle()
            HotkeyAction.Next -> Player.next()
            HotkeyAction.Previous -> Player.previous()
            HotkeyAction.Like -> track?.let { Library.toggleLike(it); Toast.show(if (Library.isLiked(it.id)) "В любимых: ${it.title}" else "Убрано из любимых: ${it.title}") }
            HotkeyAction.Download -> track?.let {
                if (Downloads.isDownloaded(it.id)) Toast.show("Уже скачан: ${it.title}") else { Downloads.enqueue(it); Toast.show("Скачиваю: ${it.title}") }
            }
            HotkeyAction.Radio -> track?.let {
                Toast.show("Собираю радио от «${it.title}»…", key = "radio", durationMs = 30_000)
                Player.radioAfterCurrent { n -> Toast.show(if (n > 0) "Радио: $n треков после этого" else "Для радио ничего не нашлось", key = "radio") }
            }
        }
    }
}

/** RegisterHotKey on a thread of its own; WM_HOTKEY comes to that thread's message queue. */
object GlobalHotkeys {
    private const val WM_HOTKEY = 0x0312
    private const val WM_QUIT = 0x0012
    private const val MOD_ALT = 0x1
    private const val MOD_CONTROL = 0x2
    private const val MOD_SHIFT = 0x4
    private const val MOD_NOREPEAT = 0x4000

    private val windows = System.getProperty("os.name").startsWith("Windows")
    private var worker: Thread? = null
    @Volatile private var threadId = 0

    private val _registered = MutableStateFlow<Set<String>>(emptySet())
    /** Action ids registered system-wide right now. */
    val registered: StateFlow<Set<String>> = _registered
    private val _taken = MutableStateFlow<Set<String>>(emptySet())
    /** Action ids whose combination another program holds. */
    val taken: StateFlow<Set<String>> = _taken

    @Synchronized
    fun apply(bindings: Map<HotkeyAction, Combo>) {
        stop()
        if (!windows || bindings.isEmpty()) return
        val ready = CountDownLatch(1)
        worker = Thread({
            val ids = mutableMapOf<Int, HotkeyAction>()
            runCatching {
                threadId = Kernel32.INSTANCE.GetCurrentThreadId()
                val bad = mutableSetOf<String>()
                bindings.entries.forEachIndexed { i, (a, c) ->
                    val mods = (if (c.mods and Combo.CTRL != 0) MOD_CONTROL else 0) or (if (c.mods and Combo.ALT != 0) MOD_ALT else 0) or
                        (if (c.mods and Combo.SHIFT != 0) MOD_SHIFT else 0) or MOD_NOREPEAT
                    if (User32.INSTANCE.RegisterHotKey(null, i + 1, mods, c.key)) ids[i + 1] = a else bad += a.id
                }
                _registered.value = ids.values.map { it.id }.toSet()
                _taken.value = bad
                if (bad.isNotEmpty()) Log.w("Hotkeys", "taken by another program: $bad")
                Log.d("Hotkeys", "registered ${ids.size}: ${ids.values.joinToString { "${it.id}=${bindings[it]?.label}" }}")
            }.onFailure { Log.w("Hotkeys", "can't register", it) }
            ready.countDown()
            val msg = WinUser.MSG()
            while (User32.INSTANCE.GetMessage(msg, null, 0, 0) > 0) {
                if (msg.message == WM_HOTKEY) ids[msg.wParam.toInt()]?.let { a -> SwingUtilities.invokeLater { Hotkeys.perform(a) } }
            }
            ids.keys.forEach { User32.INSTANCE.UnregisterHotKey(null, it) }
        }, "drxw-hotkeys").apply { isDaemon = true; start() }
        ready.await(2, TimeUnit.SECONDS)
    }

    @Synchronized
    fun stop() {
        val w = worker ?: return
        User32.INSTANCE.PostThreadMessage(threadId, WM_QUIT, WinDef.WPARAM(0), WinDef.LPARAM(0))
        w.join(1000)
        worker = null
        _registered.value = emptySet()
        _taken.value = emptySet()
    }
}
