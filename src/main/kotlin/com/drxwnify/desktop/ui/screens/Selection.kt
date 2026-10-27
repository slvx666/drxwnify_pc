package com.drxwnify.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drxwnify.desktop.data.Downloads
import com.drxwnify.desktop.data.Library
import com.drxwnify.desktop.data.Log
import com.drxwnify.desktop.data.Paths
import com.drxwnify.desktop.data.ioScope
import com.drxwnify.desktop.model.Track
import com.drxwnify.desktop.playback.Player
import com.drxwnify.desktop.ui.Ico
import com.drxwnify.desktop.ui.IconBtn
import com.drxwnify.desktop.ui.Panel
import com.drxwnify.desktop.ui.PillButton
import com.drxwnify.desktop.ui.Spacer
import com.drxwnify.desktop.ui.Ti
import com.drxwnify.desktop.ui.Txt
import com.drxwnify.desktop.ui.theme
import com.drxwnify.desktop.ui.theme.Look
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File

/**
 * Several tracks of one list picked at once, as on the phone: Ctrl+click picks one, Shift+click a run of them,
 * a plain click adds or removes while something is picked; Esc or going to another page drops the choice.
 */
object Selection {
    var list by mutableStateOf<List<Track>>(emptyList()); private set
    var ids by mutableStateOf<Set<String>>(emptySet()); private set
    private var anchor = -1

    val active: Boolean get() = ids.isNotEmpty()
    val tracks: List<Track> get() = list.filter { it.id in ids }.distinctBy { it.id }

    fun isIn(list: List<Track>) = active && this.list == list

    fun toggle(list: List<Track>, i: Int) {
        if (i !in list.indices) return
        if (this.list != list) { this.list = list; ids = emptySet() }
        val id = list[i].id
        ids = if (id in ids) ids - id else ids + id
        anchor = i
    }

    fun extend(list: List<Track>, i: Int) {
        if (this.list != list || anchor !in list.indices) return toggle(list, i)
        ids = ids + (minOf(anchor, i)..maxOf(anchor, i)).map { list[it].id }
    }

    fun all() { ids = list.map { it.id }.toSet() }

    /** The track list on screen now (the rows report it): what Ctrl+A picks. */
    var onScreen: List<Track>? = null

    fun selectAll(list: List<Track>) { this.list = list; ids = list.map { it.id }.toSet(); anchor = -1 }

    fun clear() { ids = emptySet(); anchor = -1 }
}

/** The square a picked row shows instead of its number. */
@Composable
fun SelectBox(checked: Boolean) {
    val t = theme
    val shape = if (t.look == Look.Terminal) RectangleShape else RoundedCornerShape(5.dp)
    Box(
        Modifier.size(18.dp).clip(shape).background(if (checked) t.accent else Color.Transparent)
            .border(1.5.dp, if (checked) t.accent else t.mute, shape),
        contentAlignment = Alignment.Center,
    ) { if (checked) Ico(Ti.check, size = 13.sp, color = t.onAccent) }
}

/** What can be done with the picked tracks: floats over the bottom of the page while something is picked. */
@Composable
fun SelectionBar(modifier: Modifier) {
    val t = theme
    AnimatedVisibility(
        Selection.active, modifier,
        enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 2 },
        exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 2 },
    ) {
        val picked = Selection.tracks
        val n = picked.size
        Panel(Modifier.padding(bottom = 16.dp), radius = if (t.look == Look.Terminal) 0.dp else 18.dp) {
            Row(Modifier.height(58.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Txt("Выбрано $n", size = 15.sp, weight = FontWeight.Bold)
                Spacer(w = 10.dp)
                IconBtn(Ti.playerPlayFilled, { Player.play(picked, 0, "Выбранные треки"); Selection.clear() }, size = 38.dp, iconSize = 18.sp, tint = t.text, tooltip = "Играть")
                IconBtn(Ti.playerTrackNext, { picked.asReversed().forEach(Player::addNext); Toast.show("Сыграют следующими: $n"); Selection.clear() }, size = 38.dp, iconSize = 18.sp, tint = t.text, tooltip = "Играть дальше")
                IconBtn(Ti.list, { picked.forEach(Player::addToQueue); Toast.show("В очереди ещё $n"); Selection.clear() }, size = 38.dp, iconSize = 18.sp, tint = t.text, tooltip = "В конец очереди")
                IconBtn(Ti.playlistAdd, { showAddToPlaylist(picked) }, size = 38.dp, iconSize = 18.sp, tint = t.text, tooltip = "В плейлист")
                IconBtn(Ti.heart, {
                    val add = picked.filterNot { Library.isLiked(it.id) }
                    add.forEach(Library::toggleLike)
                    Toast.show(if (add.isEmpty()) "Они уже в любимых" else "В любимых ещё ${add.size}")
                }, size = 38.dp, iconSize = 18.sp, tint = t.text, tooltip = "В любимые")
                IconBtn(Ti.download, { Downloads.enqueue(picked); Toast.show("Скачиваю $n"); Selection.clear() }, size = 38.dp, iconSize = 18.sp, tint = t.text, tooltip = "Скачать")
                Spacer(w = 6.dp)
                PillButton("Отправить файлами", Ti.send, primary = true, onClick = { ShareFiles.send(picked) })
                Spacer(w = 6.dp)
                IconBtn(Ti.circleCheck, { Selection.all() }, size = 38.dp, iconSize = 18.sp, tooltip = "Выбрать все")
                IconBtn(Ti.x, { Selection.clear() }, size = 38.dp, iconSize = 18.sp, tooltip = "Снять выбор (Esc)")
            }
        }
    }
}

/**
 * The phone's "send as files": the tracks become audio files (downloaded ones as they are, the rest
 * written to a temporary folder with tags) and go onto the clipboard as files, to paste into a chat.
 */
object ShareFiles {
    @Volatile var busy = false
        private set

    fun send(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        if (busy) { Toast.show("Ещё готовлю прошлые файлы"); return }
        busy = true
        ioScope.launch {
            try {
                val dir = File(Paths.cache, "share")
                dir.deleteRecursively(); dir.mkdirs()
                val files = mutableListOf<File>()
                var failed = 0
                tracks.forEachIndexed { i, t ->
                    Toast.show(if (tracks.size > 1) "Готовлю файлы: ${i + 1} из ${tracks.size}" else "Готовлю файл…", key = "share", durationMs = 120_000)
                    runCatching { Downloads.export(t, dir) }
                        .onSuccess { files += it }
                        .onFailure { e -> failed++; Log.w("Share", "${t.title} not exported", e) }
                }
                if (files.isEmpty()) { Toast.show("Не получилось подготовить файлы", key = "share"); return@launch }
                Toolkit.getDefaultToolkit().systemClipboard.setContents(FileList(files), null)
                val what = if (files.size == 1) "Файл в буфере" else "${files.size} ${com.drxwnify.desktop.ui.screens.plural(files.size, "файл", "файла", "файлов")} в буфере"
                Toast.show("$what — вставь в чат через Ctrl+V" + if (failed > 0) " (не вышло: $failed)" else "", key = "share", durationMs = 5000)
            } finally {
                busy = false
            }
        }
    }

    private class FileList(private val files: List<File>) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.javaFileListFlavor
        override fun getTransferData(flavor: DataFlavor): Any = if (flavor == DataFlavor.javaFileListFlavor) files else throw UnsupportedFlavorException(flavor)
    }
}
