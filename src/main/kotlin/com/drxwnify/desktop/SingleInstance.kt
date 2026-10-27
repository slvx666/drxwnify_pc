package com.drxwnify.desktop

import com.drxwnify.desktop.data.Log
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** One Drxwnify at a time: a second launch only brings the first window to the front. */
object SingleInstance {
    private const val PORT = 47833
    /** Bumped when another launch asks to be shown. */
    val showRequests = MutableStateFlow(0)
    private var server: ServerSocket? = null

    /** True when this is the only instance; false when another one was told to show itself. */
    fun claim(): Boolean {
        return try {
            val s = ServerSocket(PORT, 4, InetAddress.getLoopbackAddress())
            server = s
            thread(name = "single-instance", isDaemon = true) {
                while (!s.isClosed) runCatching {
                    s.accept().use { c -> if (c.getInputStream().bufferedReader().readLine() == "show") showRequests.value++ }
                }
            }
            true
        } catch (e: Exception) {
            runCatching { Socket(InetAddress.getLoopbackAddress(), PORT).use { it.getOutputStream().write("show\n".toByteArray()) } }
                .onFailure { Log.w("App", "another instance holds the port but didn't answer", it) }
            false
        }
    }
}
