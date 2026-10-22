package com.drxwnify.desktop.playback

import com.drxwnify.desktop.data.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * The phone's CdnHosts: some VK CDN hosts (cs9-*.vkuseraudio.net) don't answer on some networks/VPNs,
 * and FFmpeg then waits and retries for half a minute. A quick probe before playing says whether the
 * address works; a host that didn't answer is skipped for ten minutes.
 */
object CdnHosts {
    enum class Probe { OK, UNREACHABLE, GONE }

    private const val DOWN_MS = 10 * 60_000L
    private val down = ConcurrentHashMap<String, Long>()
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true).build()

    private fun host(url: String) = runCatching { URI(url).host }.getOrNull()

    /** A stream from [url] stopped giving sound: its host is skipped for a while, as after a failed probe. */
    fun markDown(url: String) {
        val h = host(url) ?: return
        down[h] = System.currentTimeMillis() + DOWN_MS
        Log.w("CDN", "$h stalled: skipped for 10 min")
    }

    fun likelyDown(url: String?): Boolean = url?.let(::host)?.let { (down[it] ?: 0L) > System.currentTimeMillis() } ?: false

    fun check(url: String, userAgent: String): Probe {
        val h = host(url) ?: return Probe.UNREACHABLE
        if (likelyDown(url)) return Probe.UNREACHABLE
        val started = System.currentTimeMillis()
        return runCatching {
            http.newCall(Request.Builder().url(url).header("User-Agent", userAgent).header("Range", "bytes=0-1").build()).execute().use { r ->
                when (r.code) {
                    in 200..299 -> Probe.OK
                    403, 404, 410 -> Probe.GONE
                    else -> Probe.UNREACHABLE
                }
            }
        }.getOrElse { e ->
            Log.w("CDN", "$h didn't answer in ${System.currentTimeMillis() - started} ms (${e.javaClass.simpleName}): skipped for 10 min")
            down[h] = System.currentTimeMillis() + DOWN_MS
            Probe.UNREACHABLE
        }
    }
}
