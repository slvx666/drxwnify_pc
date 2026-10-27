package com.drxwnify.desktop.ui.screens

import com.drxwnify.desktop.data.Log
import com.drxwnify.desktop.data.Paths
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.launch

/** Settings + library in one .drxw file (a zip), like the phone's backups. */
object Backup {
    private val files = listOf("settings.json", "library.json", "queue.json", "downloads.json")

    fun export() {
        val chooser = JFileChooser().apply {
            dialogTitle = "Сохранить копию Drxwnify"
            selectedFile = File(System.getProperty("user.home"), "Drxwnify-${java.time.LocalDate.now()}.drxw")
            fileFilter = FileNameExtensionFilter("Копия Drxwnify", "drxw")
        }
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return
        runCatching {
            ZipOutputStream(chooser.selectedFile.outputStream()).use { zip ->
                files.map { File(Paths.root, it) }.filter { it.exists() }.forEach { f ->
                    zip.putNextEntry(ZipEntry(f.name)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
        }.onSuccess { Toast.show("Копия сохранена") }.onFailure { Log.w("Backup", "export failed", it); Toast.show("Не получилось сохранить копию") }
    }

    /** A copy from the phone (.backup) or from a PC (.drxw). */
    fun import() {
        val chooser = JFileChooser(File(System.getProperty("user.home"), "Downloads").takeIf { it.isDirectory }).apply {
            dialogTitle = "Восстановить из копии"
            fileFilter = FileNameExtensionFilter("Копия Drxwnify (телефон .backup, ПК .drxw)", "backup", "drxw")
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
        importFile(chooser.selectedFile)
    }

    fun importFile(file: File) {
        if (com.drxwnify.desktop.data.PhoneBackup.isPhoneBackup(file)) {
            Toast.show("Переношу копию телефона…")
            com.drxwnify.desktop.data.ioScope.launch {
                runCatching { com.drxwnify.desktop.data.PhoneBackup.import(file) }
                    .onSuccess { r ->
                        val parts = listOfNotNull(
                            "${r.liked} ${plural(r.liked, "лайк", "лайка", "лайков")}",
                            "${r.playlists} ${plural(r.playlists, "плейлист", "плейлиста", "плейлистов")}",
                            r.friends?.let { "друзья: $it" },
                            r.accounts.takeIf { it.isNotEmpty() }?.joinToString(", "),
                        )
                        Toast.show("Готово: " + parts.joinToString(" · "))
                        com.drxwnify.desktop.catalog.SpotifyCatalog.accountChanged()
                        HomeFeed.load(force = true)
                    }
                    .onFailure { Log.w("Backup", "phone import failed", it); Toast.show("Копию телефона не получилось прочитать") }
            }
            return
        }
        runCatching {
            ZipInputStream(file.inputStream()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.name in files) File(Paths.root, e.name + ".restore").outputStream().use { zip.copyTo(it) }
                }
            }
        }.onSuccess { Toast.show("Готово — копия применится при следующем запуске") }
            .onFailure { Log.w("Backup", "import failed", it); Toast.show("Файл копии не читается") }
    }

    /** Called at start, before anything reads the files. */
    fun applyPending() {
        files.forEach { name ->
            val r = File(Paths.root, "$name.restore")
            if (r.exists()) { File(Paths.root, name).delete(); r.renameTo(File(Paths.root, name)) }
        }
    }
}
