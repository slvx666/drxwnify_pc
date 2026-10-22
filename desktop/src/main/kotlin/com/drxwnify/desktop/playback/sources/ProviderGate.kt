/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 * Desktop copy of the phone's ProviderGate (resolver/providers/YouTubeAudioProvider.kt).
 */

package com.drxwnify.desktop.playback.sources

import com.metrolist.spotify.SpotifyMapper

/** Strict gate shared by the non-YouTube providers: wrong audio is worse than no audio. */
internal object ProviderGate {
    /**
     * Max length gap for user-upload services (SoundCloud/VK). Re-uploads there are often edits,
     * sped-up or DJ versions, so they get a much tighter bound than the general 30 s gate.
     */
    const val UPLOAD_DURATION_TOLERANCE_S = 8

    fun select(query: AudioQuery, candidates: List<SpotifyMapper.Candidate>): SpotifyMapper.MatchResult.Matched? =
        SpotifyMapper.selectBestMatch(
            spotifyTitle = query.title,
            spotifyPrimaryArtist = query.primaryArtist,
            spotifyArtistsAll = query.artists.joinToString(", "),
            spotifyDurationMs = query.durationMs.toInt(),
            candidates = candidates,
            loose = false,
        ) as? SpotifyMapper.MatchResult.Matched

    /**
     * Every candidate that clears the strict gates, best first (up to [limit]), dropping uploads whose
     * length differs by more than [maxDurationGapS] when both lengths are known. Lets a provider fall
     * through to its next-best upload when the best one turns out not to be streamable.
     */
    fun ranked(
        query: AudioQuery,
        candidates: List<SpotifyMapper.Candidate>,
        maxDurationGapS: Int = UPLOAD_DURATION_TOLERANCE_S,
        limit: Int = 3,
    ): List<SpotifyMapper.MatchResult.Matched> {
        val wantedS = (query.durationMs / 1000).toInt()
        var pool = candidates.filter { c ->
            val d = c.durationSec
            c.id !in query.excludedTrackIds &&
                (wantedS <= 0 || d == null || kotlin.math.abs(d - wantedS) <= maxDurationGapS)
        }
        val result = mutableListOf<SpotifyMapper.MatchResult.Matched>()
        while (result.size < limit && pool.isNotEmpty()) {
            val chosen = select(query, pool) ?: break
            result += chosen
            pool = pool.filter { it.id != chosen.id }
        }
        return result
    }

    /** "Artist Title" search text, the form every provider's search ranks best. */
    fun searchText(query: AudioQuery): String =
        listOf(query.primaryArtist, SpotifyMapper.spotifyTitleCore(query.title))
            .filter { it.isNotBlank() }
            .joinToString(" ")
}
