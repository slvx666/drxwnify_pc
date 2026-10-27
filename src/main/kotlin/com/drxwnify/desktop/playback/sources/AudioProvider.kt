/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 * Desktop copy of the phone's resolver/AudioProvider.kt (the Android-only parts left out).
 */

package com.drxwnify.desktop.playback.sources

import com.metrolist.spotify.models.SpotifySimpleAlbum
import com.metrolist.spotify.models.SpotifySimpleArtist
import com.metrolist.spotify.models.SpotifyTrack
import com.metrolist.spotify.models.SpotifyExternalIds

/**
 * Where the AUDIO of a catalog track can come from. Metadata and library always belong to the catalog
 * (Spotify / Yandex Music); a provider only ever supplies a stream for a track the catalog described.
 *
 * The selection priority is the user's order ([ResolverPreferences.order], editable in Settings →
 * Music sources); [DEFAULT_ORDER] puts SoundCloud first.
 */
enum class AudioProviderId {
    YOUTUBE,

    /** Retired: its community servers are gone. Kept only so stored rows/orders still parse. */
    QOBUZ,
    VK,
    SOUNDCLOUD,
    BANDCAMP,
    AUDIUS,
    SOULSEEK,

    /** Lossless FLAC from public mirrors listed in the remote config. */
    LOSSLESS,
    ;

    /**
     * What this service usually delivers, kbps (lossless = 1411): what "best quality" compares when
     * several services have the exact track. A match can state its own ([ProviderMatch.qualityKbps]).
     */
    val typicalKbps: Int
        get() = when (this) {
            YOUTUBE -> 160 // Opus ~160 without Premium
            QOBUZ -> 1411
            VK -> 320
            SOUNDCLOUD -> 160
            BANDCAMP -> 128
            // Its free streams are 128 kbps MP3. Counted as 320 it "beat" YouTube on sound, won
            // the race, then often failed to stream (~20 s lost per track), and the race waited
            // for it even with an exact YouTube match in hand.
            AUDIUS -> 128
            SOULSEEK -> 320
            LOSSLESS -> 1411
        }

    companion object {
        val DEFAULT_ORDER = listOf(SOUNDCLOUD, YOUTUBE, VK, LOSSLESS, BANDCAMP, AUDIUS, SOULSEEK)

        /** Parses a stored "A,B,C" order; unknown names are dropped, missing providers appended. */
        fun parseOrder(value: String?): List<AudioProviderId> {
            val parsed = value.orEmpty().split(',')
                .mapNotNull { name -> entries.firstOrNull { it.name == name.trim() } }
                .filter { it != QOBUZ }
                .distinct()
            // Soulseek is the slow last resort: a stored order never moves it ahead of new providers.
            return parsed + DEFAULT_ORDER.filter { it !in parsed }
        }
    }
}

/** What providers search for: the catalog's own description of the track. */
data class AudioQuery(
    val catalogId: String?,
    val title: String,
    val artists: List<String>,
    val album: String?,
    val durationMs: Long,
    val isrc: String?,
    /** Provider track ids known to be unplayable for this track (e.g. an age-restricted YouTube video). */
    val excludedTrackIds: Set<String> = emptySet(),
) {
    val primaryArtist: String get() = artists.firstOrNull().orEmpty()

    /** Stable key for caches that should not depend on the media id form. */
    val cacheKey: String
        get() = catalogId ?: "${title.lowercase()}|${primaryArtist.lowercase()}|${durationMs / 1000}"

    fun toSpotifyTrack(): SpotifyTrack = SpotifyTrack(
        id = catalogId.orEmpty(),
        name = title,
        artists = artists.map { SpotifySimpleArtist(name = it) },
        album = album?.let { SpotifySimpleAlbum(name = it) },
        durationMs = durationMs.toInt(),
        externalIds = isrc?.let { SpotifyExternalIds(isrc = it) },
    )

    companion object {
        fun from(track: SpotifyTrack) = AudioQuery(
            catalogId = track.id.takeIf { it.isNotBlank() },
            title = track.name,
            artists = track.artists.map { it.name }.filter { it.isNotBlank() },
            album = track.album?.name?.takeIf { it.isNotBlank() },
            durationMs = track.durationMs.toLong(),
            isrc = track.isrc?.takeIf { it.isNotBlank() },
        )
    }
}

/** A provider-side track that cleared the strict title/artist/duration gates. */
data class ProviderMatch(
    val provider: AudioProviderId,
    /** Provider-specific id that is enough to fetch a fresh stream later (survives restarts). */
    val trackId: String,
    val title: String,
    val artist: String,
    val durationMs: Long?,
    val confidence: Double,
    val thumbnailUrl: String? = null,
    /** This file's own quality when the service tells it per file (Soulseek), kbps; lossless = 1411. */
    val qualityKbps: Int? = null,
) {
    val expectedKbps: Int get() = qualityKbps ?: provider.typicalKbps
}

/** A playable stream. [uri] may be http(s) or an [HlsConcatDataSource] `meldhls://` uri. */
data class AudioStream(
    val uri: String,
    val mimeType: String,
    val codecs: String,
    val bitrate: Int,
    val sampleRate: Int?,
    val expiresAtMs: Long,
    val contentLength: Long? = null,
)

interface AudioProvider {
    val id: AudioProviderId

    /** Upper bound for one search, so a dead/blocked service can never stall resolution. */
    val searchTimeoutMs: Long

    /** False when the provider is enabled but cannot work yet (e.g. VK without a login). */
    fun isReady(): Boolean = true

    suspend fun search(query: AudioQuery): ProviderMatch?

    /**
     * Free-text search for the experimental "search the sources directly" mode: there is no catalog
     * track to compare against, so results come back in the provider's own order, ungated. Providers
     * that can't (or shouldn't) answer such a search return an empty list.
     */
    suspend fun searchFree(text: String, limit: Int = 15): List<ProviderMatch> = emptyList()

    /**
     * A fresh stream for a previously found match, or null if it can no longer be played. The YouTube
     * provider returns null: YouTube streams are resolved by the player's native pipeline.
     */
    suspend fun stream(query: AudioQuery, match: ProviderMatch): AudioStream?
}
