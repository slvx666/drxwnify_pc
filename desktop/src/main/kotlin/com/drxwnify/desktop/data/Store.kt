package com.drxwnify.desktop.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

val appJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
    explicitNulls = false
}

val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** A JSON file behind a StateFlow: read once, written (debounced, atomically) on every change. */
@OptIn(FlowPreview::class)
class Store<T>(name: String, private val serializer: KSerializer<T>, default: () -> T) {
    private val file = File(Paths.root, name)
    private val flow = MutableStateFlow(load() ?: default())
    val state: StateFlow<T> = flow
    val value: T get() = flow.value

    init {
        ioScope.launch { flow.drop(1).debounce(400).collect { save(it) } }
        Runtime.getRuntime().addShutdownHook(Thread { save(flow.value) })
    }

    fun update(change: (T) -> T) {
        flow.value = change(flow.value)
    }

    private fun load(): T? = runCatching {
        if (file.exists()) appJson.decodeFromString(serializer, file.readText()) else null
    }.onFailure { Log.w("Store", "couldn't read ${file.name}", it) }.getOrNull()

    @Synchronized
    private fun save(value: T) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(appJson.encodeToString(serializer, value))
            if (!tmp.renameTo(file)) {
                file.delete(); tmp.renameTo(file)
            }
        }.onFailure { Log.w("Store", "couldn't save ${file.name}", it) }
    }
}
