package com.drxwnify.desktop.data

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    // Внешний вид
    val theme: String = "tategaki",
    /** Day version of the theme (light); off — the night one. A separate switch for every theme. */
    val themeDay: Boolean = false,
    val backgroundGlyphs: Boolean = true,
    val backgroundMotion: Boolean = true,
    val smoothAnimations: Boolean = true,
    val uiScale: Float = 1f,
    val language: String = "ru",
    /** Hotkey per action id ("ctrl+alt+shift+76"); missing — the default, "" — off. */
    val hotkeys: Map<String, String> = emptyMap(),
    /** Hotkeys work in any app (registered with Windows), not only in this window. */
    val globalHotkeys: Boolean = true,

    // Плеер и звук
    val quality: String = "max", // max | balanced | saver
    val pickMode: String = "quality", // quality | accuracy | speed
    val crossfadeSec: Int = 0,
    val normalize: Boolean = true,
    val skipSilence: Boolean = false,
    val endlessQueue: Boolean = true,
    val outputDevice: String? = null,
    val volume: Float = 0.8f,
    /** The last volume that could be heard: what unmuting goes back to. */
    val lastVolume: Float = 0.5f,
    /** -1 … 1, left/right. */
    val balance: Float = 0f,
    /** The phone's sources in the user's order (ties in sound quality go to the earlier one). */
    val sources: List<String> = listOf("soundcloud", "youtube", "vk", "lossless", "bandcamp", "audius", "soulseek"),
    val disabledSources: List<String> = emptyList(),

    // Контент
    val catalog: String = "spotify", // spotify | yandex | youtube
    /** Likes go to the Spotify account and come back from it (the phone's "sync likes"). */
    val spotifySyncLikes: Boolean = true,
    val contentRegion: String = "RU",
    val lyricsProviders: List<String> = listOf("lrclib", "betterlyrics", "kugou"),
    val translateLyrics: Boolean = false,

    // Загрузки и хранилище
    val downloadDir: String? = null,
    val downloadFormat: String = "original", // original | m4a | flac
    val coverCacheMb: Int = 300,

    // Приватность
    val shareListening: Boolean = true,
    val pauseHistory: Boolean = false,

    // Окно
    val leftWidth: Float = 270f,
    val leftCollapsed: Boolean = false,
    val rightWidth: Float = 310f,
    val rightHidden: Boolean = false,
    val windowW: Int = 1360,
    val windowH: Int = 860,
    val windowMaximized: Boolean = false,
)

/** Accounts live apart from the settings: never shown, never exported with them. */
@Serializable
data class Accounts(
    val spotifySpDc: String? = null,
    val spotifySpKey: String? = null,
    val spotifyAccessToken: String? = null,
    val spotifyTokenExpiry: Long = 0,
    val spotifyName: String? = null,
    val spotifyUserId: String? = null,
    val vkToken: String? = null,
    val vkName: String? = null,
    val vkUserId: String? = null,
    val yandexToken: String? = null,
    val yandexUid: String? = null,
    val yandexName: String? = null,
    val lastfmSession: String? = null,
    val lastfmUser: String? = null,
    val lastfmScrobble: Boolean = true,
    val discordToken: String? = null,
    val soulseekUser: String? = null,
    val soulseekPassword: String? = null,
    val soulseekEnabled: Boolean = true,
    /** Nostr secret key (hex) — the same identity as on the phone after "войти по ключу". */
    val nostrSecret: String? = null,
)

object Prefs {
    val settings = Store("settings.json", AppSettings.serializer()) { AppSettings() }
    val accounts = Store("accounts.json", Accounts.serializer()) { Accounts() }
}
