package com.drxwnify.desktop.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The site's config.json, as the phone's RemoteConfig reads it: lossless mirrors to try and sources
 * switched off for everyone. The last copy is kept, so it works offline and before the site answers.
 */
object RemoteConfig {
    data class Config(val losslessMirrors: List<String> = emptyList(), val disabledSources: Set<String> = emptySet())

    private val URLS = listOf("https://drxwnify.pages.dev/config.json", "https://raw.githubusercontent.com/slvx666/DRXWNIFY/master/website/config.json")
    private val _config = MutableStateFlow(Config())
    val config: StateFlow<Config> = _config
    private val cache by lazy { File(Paths.root, "remote_config.json") }
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()

    fun init() {
        runCatching { if (cache.exists()) parse(cache.readText())?.let { _config.value = it } }
        ioScope.launch {
            for (u in URLS) {
                val text = runCatching { http.newCall(Request.Builder().url("$u?t=${System.currentTimeMillis()}").build()).execute().use { r -> if (r.isSuccessful) r.body?.string() else null } }.getOrNull() ?: continue
                val parsed = parse(text) ?: continue
                _config.value = parsed
                runCatching { cache.writeText(text) }
                Log.i("Config", "loaded from $u: ${parsed.losslessMirrors.size} lossless mirrors")
                break
            }
        }
    }

    private fun parse(text: String): Config? = runCatching {
        val root = JSONObject(text)
        val mirrors = root.optJSONArray("losslessMirrors")?.let { a ->
            (0 until a.length()).mapNotNull { a.optString(it).trim().trimEnd('/').takeIf { u -> u.startsWith("https://") } }
        }.orEmpty()
        val disabled = root.optJSONArray("disabledSources")?.let { a -> (0 until a.length()).map { a.optString(it).lowercase() }.toSet() }.orEmpty()
        Config(mirrors, disabled)
    }.getOrNull()
}
