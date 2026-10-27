package com.drxwnify.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed interface Screen {
    data object Home : Screen
    data class Search(val query: String) : Screen
    data class Album(val id: String) : Screen
    data class Artist(val id: String) : Screen
    data class RemotePlaylist(val id: String) : Screen
    data object Liked : Screen
    data class LocalPlaylist(val id: String) : Screen
    data object Friends : Screen
    data object Downloads : Screen
    data object History : Screen
    /** "Показать всё" of an artist's releases: each with its tracks, as Spotify lays it out. */
    data class Discography(val artistId: String, val artistName: String) : Screen
    /** drxwnslvt's album reviews (for him and the profiles he marked). */
    data object Reviews : Screen
}

enum class RightPane { NowPlaying, Queue, Lyrics }

/** Where the app is, with back/forward like a browser. */
object Nav {
    private val back = ArrayDeque<Screen>()
    private val forward = ArrayDeque<Screen>()
    var current by mutableStateOf<Screen>(Screen.Home); private set
    var canBack by mutableStateOf(false); private set
    var canForward by mutableStateOf(false); private set
    /** +1 forward, -1 back: which way the page transition slides. */
    var direction by mutableStateOf(1); private set

    var settingsOpen by mutableStateOf(false)
    var settingsSection by mutableStateOf("appearance")
    var right by mutableStateOf(RightPane.NowPlaying)
    var searchText by mutableStateOf("")

    fun go(s: Screen) {
        if (s == current) return
        if (s !is Screen.Search || current !is Screen.Search) back.addLast(current)
        forward.clear()
        direction = 1
        current = s
        settingsOpen = false
        if (s !is Screen.Search) searchText = ""
        update()
    }

    fun back() {
        if (settingsOpen) { settingsOpen = false; return }
        val s = back.removeLastOrNull() ?: return
        forward.addLast(current)
        direction = -1
        current = s
        if (s !is Screen.Search) searchText = ""
        update()
    }

    fun forward() {
        val s = forward.removeLastOrNull() ?: return
        back.addLast(current)
        direction = 1
        current = s
        update()
    }

    fun home() { settingsOpen = false; searchText = ""; go(Screen.Home) }

    fun openSettings(section: String = "appearance") {
        settingsSection = section
        settingsOpen = true
    }

    private fun update() {
        com.drxwnify.desktop.ui.screens.Selection.clear()
        canBack = back.isNotEmpty()
        canForward = forward.isNotEmpty()
    }
}
