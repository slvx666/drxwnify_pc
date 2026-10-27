package com.drxwnify.desktop.catalog

import com.drxwnify.desktop.model.Collection
import com.drxwnify.desktop.model.Track
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * The phone's AppLinks: `https://drxwnify.pages.dev/<album|playlist|track|artist>/<id>?n=<title>&by=<artist>`.
 * Ids are Spotify's (bare) or YouTube's with "yt:"; title and artist ride along so a receiver whose
 * catalog can't open the id still finds it. The site shows a page; the phone app opens it directly.
 */
object AppLinks {
    const val SITE = "https://drxwnify.pages.dev"
    private const val YT = "yt:"

    enum class Kind(val path: String) { ALBUM("album"), PLAYLIST("playlist"), TRACK("track"), ARTIST("artist") }

    data class Target(val kind: Kind, val id: String, val title: String?, val by: String?)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun link(kind: Kind, id: String, title: String?, by: String?): String {
        val query = listOfNotNull(
            title?.takeIf { it.isNotBlank() }?.let { "n=" + enc(it.take(80)) },
            by?.takeIf { it.isNotBlank() }?.let { "by=" + enc(it.take(60)) },
        )
        return "$SITE/${kind.path}/" + enc(id) + if (query.isEmpty()) "" else "?" + query.joinToString("&")
    }

    /** This app's id → the link's id: "spotify:X" / "mfb:X" → X, a YouTube id → "yt:<id>"; null for local things. */
    private fun linkId(id: String): String? = when {
        id.startsWith(SpotifyCatalog.PREFIX) -> id.removePrefix(SpotifyCatalog.PREFIX)
        id.startsWith("mfb:") -> id.removePrefix("mfb:")
        id.contains(':') || id.startsWith("ph-") || id.startsWith("pl") && id.length < 16 -> null
        else -> YT + id
    }

    fun forTrack(t: Track): String? = linkId(t.id)?.let { text(Kind.TRACK, it, t.title, t.artistLine) }

    fun forCollection(c: Collection): String? {
        val kind = when (c.kind) { "Album" -> Kind.ALBUM; "Artist" -> Kind.ARTIST; else -> Kind.PLAYLIST }
        val by = c.subtitle.substringBefore(" · ").takeIf { kind == Kind.ALBUM && it.isNotBlank() }
        return linkId(c.id)?.let { text(kind, it, c.title, by) }
    }

    private fun text(kind: Kind, id: String, title: String?, by: String?) = listOfNotNull(
        title?.takeIf { it.isNotBlank() }?.let { t -> if (!by.isNullOrBlank()) "$by — $t" else t },
        link(kind, id, title, by),
    ).joinToString("\n")

    /** Copies the share text; false when there is nothing to share (a local playlist, a local file). */
    fun copy(text: String?): Boolean {
        text ?: return false
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        return true
    }

    /** A Drxwnify link (site or drxwnify://) or an open.spotify.com one pasted into the search. */
    fun parse(text: String): Target? {
        val raw = Regex("""(https?://\S+|drxwnify://\S+)""").find(text.trim())?.value ?: return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val (kindName, id) = when {
            uri.host == "drxwnify.pages.dev" -> (segments.getOrNull(0) ?: return null) to (segments.getOrNull(1) ?: return null)
            uri.scheme == "drxwnify" -> (uri.host ?: return null) to (segments.getOrNull(0) ?: return null)
            uri.host == "open.spotify.com" -> segments.dropWhile { it.startsWith("intl-") }.let { (it.getOrNull(0) ?: return null) to (it.getOrNull(1) ?: return null) }
            else -> return null
        }
        val kind = Kind.entries.firstOrNull { it.path == kindName } ?: return null
        val q = uri.rawQuery.orEmpty().split('&').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to URLDecoder.decode(it[1], "UTF-8") } }.toMap()
        return Target(kind, URLDecoder.decode(id, "UTF-8"), q["n"], q["by"])
    }

    /** The link's id in this app's form: Spotify ids get "spotify:", "yt:" ids lose the prefix. */
    fun appId(t: Target): String = if (t.id.startsWith(YT)) t.id.removePrefix(YT) else SpotifyCatalog.PREFIX + t.id
}
