package com.drxwnify.desktop.data

import java.io.File

/** Where the app keeps its things: %APPDATA%\Drxwnify (settings, library, caches). */
object Paths {
    val root: File by lazy {
        val base = System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".config")
        File(base, "Drxwnify").apply { mkdirs() }
    }
    val cache: File by lazy { File(root, "cache").apply { mkdirs() } }
    val covers: File by lazy { File(cache, "covers").apply { mkdirs() } }
    val defaultDownloads: File by lazy {
        // Test runs keep their files out of the user's music folder.
        (System.getenv("DRXW_DOWNLOADS")?.let(::File) ?: File(System.getProperty("user.home"), "Music/Drxwnify")).apply { mkdirs() }
    }
}
