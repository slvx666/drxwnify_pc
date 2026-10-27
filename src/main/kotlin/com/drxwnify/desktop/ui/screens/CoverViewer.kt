package com.drxwnify.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drxwnify.desktop.data.Log
import com.drxwnify.desktop.data.ioScope
import com.drxwnify.desktop.ui.Images
import com.drxwnify.desktop.ui.Mono
import com.drxwnify.desktop.ui.PillButton
import com.drxwnify.desktop.ui.Spacer
import com.drxwnify.desktop.ui.Ti
import com.drxwnify.desktop.ui.Txt
import com.drxwnify.desktop.ui.theme
import kotlinx.coroutines.launch
import okhttp3.Request
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** A cover full size over the app, as on the phone: look at it, save it, copy it. */
object CoverViewer {
    data class Shown(val url: String, val title: String, val original: String)

    var shown by mutableStateOf<Shown?>(null)
        private set

    fun show(url: String?, title: String) {
        if (url != null) shown = Shown(largest(url), title, url)
    }

    fun close() { shown = null }

    /** The biggest version the service has of the same picture. */
    fun largest(url: String): String = when {
        // Spotify: 300 px "1e02" / 64 px "4851" → 640 px "b273".
        url.contains("i.scdn.co/image/ab67616d") -> url.replace(Regex("ab67616d0000[0-9a-f]{4}"), "ab67616d0000b273")
        url.contains("i.scdn.co/image/ab6761610000") -> url.replace(Regex("ab6761610000[0-9a-f]{4}"), "ab6761610000e5eb")
        Regex("=w\\d+-h\\d+").containsMatchIn(url) -> url.replace(Regex("=w\\d+-h\\d+[^&/]*$"), "=w1200-h1200-l90-rj")
        url.contains("i.ytimg.com") -> url.replace(Regex("/(default|mqdefault|hqdefault|sddefault)\\.jpg"), "/maxresdefault.jpg")
        else -> url
    }

    private fun bytes(url: String): ByteArray =
        if (url.startsWith("file:")) File(java.net.URI(url)).readBytes()
        else Images.http.newCall(Request.Builder().url(url).build()).execute().use { r -> if (!r.isSuccessful) error("HTTP ${r.code}"); r.body!!.bytes() }

    fun save(s: Shown) {
        val name = s.title.replace(Regex("""[\\/:*?"<>|]"""), " ").trim().ifBlank { "cover" }.take(80)
        val pictures = File(System.getProperty("user.home"), "Pictures").takeIf { it.isDirectory } ?: File(System.getProperty("user.home"))
        val chooser = JFileChooser(pictures).apply {
            dialogTitle = "Сохранить обложку"
            selectedFile = File(pictures, "$name.jpg")
            fileFilter = FileNameExtensionFilter("Изображение", "jpg", "jpeg", "png", "webp")
        }
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return
        ioScope.launch {
            runCatching { chooser.selectedFile.writeBytes(runCatching { bytes(s.url) }.getOrElse { bytes(s.original) }) }
                .onSuccess { Toast.show("Обложка сохранена") }
                .onFailure { Log.w("Cover", "save failed", it); Toast.show("Не получилось сохранить обложку") }
        }
    }

    fun copy(s: Shown) {
        ioScope.launch {
            runCatching {
                val img = ImageIO.read(runCatching { bytes(s.url) }.getOrElse { bytes(s.original) }.inputStream()) ?: error("not an image")
                Toolkit.getDefaultToolkit().systemClipboard.setContents(object : Transferable {
                    override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
                    override fun isDataFlavorSupported(f: DataFlavor) = f == DataFlavor.imageFlavor
                    override fun getTransferData(f: DataFlavor): Any = img
                }, null)
            }.onSuccess { Toast.show("Обложка скопирована") }
                .onFailure { Log.w("Cover", "copy failed", it); Toast.show("Не получилось скопировать") }
        }
    }
}

@Composable
fun CoverViewerOverlay() {
    val t = theme
    val shown = CoverViewer.shown
    var last by remember { mutableStateOf<CoverViewer.Shown?>(null) }
    if (shown != null) last = shown
    AnimatedVisibility(shown != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
        val s = last ?: return@AnimatedVisibility
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f))
                .clickable(remember { MutableInteractionSource() }, null) { CoverViewer.close() },
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints {
                val side = minOf(maxWidth * 0.8f, maxHeight * 0.72f)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // The biggest size may not exist (YouTube's maxres): then the one we had.
                    val bmp by produceState<ImageBitmap?>(Images.cached(s.url), s.url) { value = Images.load(s.url, 1400) ?: Images.load(s.original, 1400) }
                    Box(
                        Modifier.size(side).clip(RoundedCornerShape(t.coverRadius + 6.dp)).background(t.fill)
                            .clickable(remember { MutableInteractionSource() }, null) { },
                        contentAlignment = Alignment.Center,
                    ) {
                        bmp?.let { Image(it, s.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Spinner()
                    }
                    Spacer(h = 16.dp)
                    Txt(s.title, size = 15.sp, weight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    bmp?.let { Mono("${it.width} × ${it.height}", size = 11.sp, color = Color.White.copy(alpha = 0.55f)) }
                    Spacer(h = 14.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Сохранить…", Ti.photoDown, primary = true, onClick = { CoverViewer.save(s) })
                        PillButton("Копировать", Ti.copy, onClick = { CoverViewer.copy(s) })
                        PillButton("Закрыть", Ti.x, onClick = { CoverViewer.close() })
                    }
                }
            }
        }
    }
}
