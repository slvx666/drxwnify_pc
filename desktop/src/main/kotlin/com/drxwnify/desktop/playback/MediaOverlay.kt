package com.drxwnify.desktop.playback

import com.drxwnify.desktop.data.Log
import com.drxwnify.desktop.data.ioScope
import com.sun.jna.Callback
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * The track in Windows' own media controls (the volume flyout, the lock screen, media keys): what the
 * phone gets from its media session. WinRT's SystemMediaTransportControls reached through JNA — the
 * interop factory gives the controls of our window; the buttons come back through a small COM object.
 * Everything runs on one MTA thread. Any failure just leaves the overlay off.
 */
object MediaOverlay {
    private interface Combase : StdCallLibrary {
        fun RoInitialize(type: Int): Int
        fun WindowsCreateString(src: WString, length: Int, out: PointerByReference): Int
        fun WindowsDeleteString(h: Pointer?): Int
        fun RoGetActivationFactory(classId: Pointer, iid: Guid.GUID, factory: PointerByReference): Int
    }

    private val thread = Executors.newSingleThreadExecutor { r -> Thread(r, "drxw-media-overlay").apply { isDaemon = true } }
    private var combase: Combase? = null
    private var smtc: Pointer? = null
    private var updater: Pointer? = null
    private var music: Pointer? = null
    private var streamRefs: Pointer? = null
    private var uris: Pointer? = null
    @Volatile var active = false
        private set

    // ISystemMediaTransportControls (after IInspectable's 6 slots)
    private const val PUT_STATUS = 7
    private const val GET_UPDATER = 8
    private const val PUT_ENABLED = 11
    private const val PUT_PLAY = 13
    private const val PUT_PAUSE = 17
    private const val PUT_PREVIOUS = 25
    private const val PUT_NEXT = 27
    private const val ADD_BUTTON = 32

    // ISystemMediaTransportControlsDisplayUpdater
    private const val PUT_TYPE = 7
    private const val PUT_THUMB = 11
    private const val GET_MUSIC = 12
    private const val UPDATE = 17

    // IMusicDisplayProperties
    private const val PUT_TITLE = 7
    private const val PUT_ALBUM_ARTIST = 9
    private const val PUT_ARTIST = 11

    private val IID_INTEROP = Guid.GUID.fromString("{ddb0472d-c911-4a1f-86d9-dc3d71a95f5a}").apply { write() }
    private val IID_SMTC = Guid.GUID.fromString("{99fa3ff4-1742-42a6-902e-087d41f965ec}").apply { write() }
    private val IID_STREAM_REF_STATICS = Guid.GUID.fromString("{857309dc-3fbf-4e7d-986f-ef3b1a07a964}").apply { write() }
    private val IID_URI_FACTORY = Guid.GUID.fromString("{44a9796f-723e-4fdf-a218-033e75b0c084}").apply { write() }

    /** Hooks the overlay to the app's window and follows the player from then on. */
    fun start(window: java.awt.Window) {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        thread.execute {
            runCatching { open(window) }.onFailure { Log.w("Overlay", "Windows media controls unavailable", it) }
        }
        ioScope.launch {
            combine(Player.now, Player.engine.state) { n, s -> n.track to s }.distinctUntilChanged().collect { (track, s) ->
                thread.execute { runCatching { show(track, s) }.onFailure { Log.w("Overlay", "update failed", it) } }
            }
        }
    }

    private fun open(window: java.awt.Window) {
        val cb = Native.load("combase", Combase::class.java)
        combase = cb
        cb.RoInitialize(1) // multithreaded
        val hwnd = Native.getWindowPointer(window) ?: error("no window handle")
        val interop = factory("Windows.Media.SystemMediaTransportControls", IID_INTEROP)
        val out = PointerByReference()
        check(call(interop, 6, hwnd, IID_SMTC.pointer, out) == 0) { "GetForWindow failed" }
        val s = out.value
        smtc = s
        call(s, PUT_ENABLED, 1); call(s, PUT_PLAY, 1); call(s, PUT_PAUSE, 1); call(s, PUT_NEXT, 1); call(s, PUT_PREVIOUS, 1)
        val u = PointerByReference().also { check(call(s, GET_UPDATER, it) == 0) }.value
        updater = u
        call(u, PUT_TYPE, 1) // music
        music = PointerByReference().also { check(call(u, GET_MUSIC, it) == 0) }.value
        streamRefs = runCatching { factory("Windows.Storage.Streams.RandomAccessStreamReference", IID_STREAM_REF_STATICS) }.getOrNull()
        uris = runCatching { factory("Windows.Foundation.Uri", IID_URI_FACTORY) }.getOrNull()
        val token = LongByReference()
        val hr = call(s, ADD_BUTTON, ButtonHandler.pointer, token)
        if (hr != 0) Log.w("Overlay", "buttons not hooked: 0x%08x".format(hr))
        active = true
        Log.d("Overlay", "Windows media controls on")
    }

    private fun show(track: com.drxwnify.desktop.model.Track?, s: EngineState) {
        val sm = smtc ?: return
        val u = updater ?: return
        val m = music ?: return
        if (track == null || track.id.startsWith("pending:") && s == EngineState.Idle) {
            call(sm, PUT_STATUS, 0) // closed
            return
        }
        withString(track.title) { call(m, PUT_TITLE, it) }
        withString(track.artistLine) { call(m, PUT_ARTIST, it) }
        withString(track.artistLine) { call(m, PUT_ALBUM_ARTIST, it) }
        val cover = track.cover?.takeIf { it.startsWith("http") }
        val sr = streamRefs
        val uf = uris
        if (cover != null && sr != null && uf != null) {
            withString(cover) { h ->
                val uri = PointerByReference()
                if (call(uf, 6, h, uri) == 0) {
                    val ref = PointerByReference()
                    if (call(sr, 7, uri.value, ref) == 0) { call(u, PUT_THUMB, ref.value); release(ref.value) }
                    release(uri.value)
                }
            }
        }
        val upd = call(u, UPDATE)
        val status = when (s) { EngineState.Playing -> 3; EngineState.Paused -> 4; EngineState.Loading -> 1; else -> 2 }
        val hr = call(sm, PUT_STATUS, status)
        Log.d("Overlay", "${track.title}: $s → status $status (update 0x%08x, status 0x%08x)".format(upd, hr))
    }

    private fun factory(className: String, iid: Guid.GUID): Pointer {
        var result: Pointer? = null
        withString(className) { h ->
            val out = PointerByReference()
            val hr = combase!!.RoGetActivationFactory(h, iid, out)
            check(hr == 0) { "no factory for $className: 0x%08x".format(hr) }
            result = out.value
        }
        return result!!
    }

    private inline fun withString(text: String, use: (Pointer) -> Unit) {
        val out = PointerByReference()
        check(combase!!.WindowsCreateString(WString(text), text.length, out) == 0)
        try { use(out.value) } finally { combase!!.WindowsDeleteString(out.value) }
    }

    /** Calls slot [index] of a COM/WinRT object's vtable; returns the HRESULT. */
    private fun call(obj: Pointer, index: Int, vararg args: Any?): Int {
        val vtbl = obj.getPointer(0)
        val fn = Function.getFunction(vtbl.getPointer(index.toLong() * Native.POINTER_SIZE))
        return fn.invokeInt(arrayOf(obj, *args))
    }

    private fun release(obj: Pointer?) { if (obj != null) call(obj, 2) }

    /**
     * The ButtonPressed handler: a COM object of our own (IUnknown + Invoke). Its memory is a pointer to
     * a table of four callbacks; it lives as long as the app, so AddRef/Release only count.
     */
    private object ButtonHandler {
        private const val E_NOINTERFACE = 0x80004002.toInt()
        private val IID_UNKNOWN = Guid.GUID.fromString("{00000000-0000-0000-c000-000000000046}").apply { write() }
        private val IID_AGILE = Guid.GUID.fromString("{94ea2b94-e9cc-49e0-c0ff-ee64ca8f5b90}").apply { write() }
        private val IID_HANDLER = Guid.GUID.fromString("{0557e996-7b23-5bae-aa81-ea0d671143a4}").apply { write() }

        interface QueryInterface : Callback { fun invoke(self: Pointer, riid: Pointer, ppv: Pointer): Int }
        interface Counter : Callback { fun invoke(self: Pointer): Int }
        interface Invoke : Callback { fun invoke(self: Pointer, sender: Pointer?, args: Pointer?): Int }

        private var refs = 1
        private val qi = object : QueryInterface {
            override fun invoke(self: Pointer, riid: Pointer, ppv: Pointer): Int {
                val asked = Guid.GUID(riid)
                if (asked == IID_UNKNOWN || asked == IID_AGILE || asked == IID_HANDLER) { ppv.setPointer(0, self); refs++; return 0 }
                ppv.setPointer(0, Pointer.NULL)
                return E_NOINTERFACE
            }
        }
        private val addRef = object : Counter { override fun invoke(self: Pointer): Int = ++refs }
        private val release = object : Counter { override fun invoke(self: Pointer): Int = (--refs).coerceAtLeast(1) }
        private val onInvoke = object : Invoke {
            override fun invoke(self: Pointer, sender: Pointer?, args: Pointer?): Int {
                if (args == null) return 0
                val button = IntByReference()
                if (call(args, 6, button) != 0) return 0
                javax.swing.SwingUtilities.invokeLater {
                    val playing = Player.engine.state.value == EngineState.Playing
                    when (button.value) {
                        0 -> if (!playing) Player.toggle()   // play
                        1, 2 -> if (playing) Player.toggle() // pause, stop
                        6 -> Player.next()
                        7 -> Player.previous()
                    }
                }
                return 0
            }
        }
        private val vtable = Memory(4L * Native.POINTER_SIZE).apply {
            setPointer(0, com.sun.jna.CallbackReference.getFunctionPointer(qi))
            setPointer(1L * Native.POINTER_SIZE, com.sun.jna.CallbackReference.getFunctionPointer(addRef))
            setPointer(2L * Native.POINTER_SIZE, com.sun.jna.CallbackReference.getFunctionPointer(release))
            setPointer(3L * Native.POINTER_SIZE, com.sun.jna.CallbackReference.getFunctionPointer(onInvoke))
        }
        val pointer: Pointer = Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, vtable) }
    }
}
