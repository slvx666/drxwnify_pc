package com.drxwnify.desktop.data

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** A plain text log next to the settings (%APPDATA%\Drxwnify\drxwnify.log), trimmed on start. */
object Log {
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val file: File by lazy {
        File(Paths.root, "drxwnify.log").also { f ->
            if (f.length() > 4_000_000) f.writeText("")
        }
    }

    @Synchronized
    fun write(level: Char, tag: String?, message: String?, error: Throwable?) {
        if (level == 'V') return
        val line = buildString {
            append(LocalDateTime.now().format(time)).append(' ').append(level).append(' ')
            if (tag != null) append(tag).append(": ")
            if (message != null) append(message)
            if (error != null) append(" | ").append(error.javaClass.simpleName).append(": ").append(error.message)
            // Crashes carry where they happened (our frames first), not just the message.
            if (error != null && level == 'E') {
                val frames = error.stackTrace
                (frames.filter { it.className.startsWith("com.drxwnify") }.take(8).ifEmpty { frames.take(8).toList() })
                    .forEach { append("\n    at ").append(it) }
                error.cause?.let { append("\n  caused by ").append(it.javaClass.simpleName).append(": ").append(it.message) }
            }
        }
        if (level == 'E' || level == 'W') System.err.println(line) else println(line)
        runCatching { file.appendText(line + "\n") }
    }

    fun d(tag: String, message: String) = write('D', tag, message, null)
    fun i(tag: String, message: String) = write('I', tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = write('W', tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = write('E', tag, message, error)
}
