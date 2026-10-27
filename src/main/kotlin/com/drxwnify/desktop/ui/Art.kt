package com.drxwnify.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drxwnify.desktop.model.msAsDuration
import com.drxwnify.desktop.playback.EngineState
import com.drxwnify.desktop.playback.Player
import com.drxwnify.desktop.ui.theme.Fonts
import com.drxwnify.desktop.ui.theme.Look
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** The shape a theme gives a small square of art (list rows, the library, the dice cell). */
@Composable
fun artShape(round: Boolean = false, small: Boolean = false): Shape {
    val t = theme
    if (round) return if (t.look == Look.Terminal) RectangleShape else CircleShape
    return when (t.look) {
        Look.Terminal -> RectangleShape
        Look.Neon -> RoundedCornerShape(if (small) 4.dp else 8.dp)
        Look.Island -> RoundedCornerShape(if (small) 10.dp else 18.dp)
        Look.Atlas -> if (small) RoundedCornerShape(2.dp) else CircleShape
        Look.Rite -> RectangleShape
        Look.Quilt -> RoundedCornerShape(if (small) 9.dp else 6.dp)
        else -> RoundedCornerShape(if (small) 8.dp else t.coverRadius)
    }
}

/**
 * A cover dressed the theme's way: a framed square in Gruvbox, a neon frame in Ретровейв, a polaroid with
 * tape in Cozy, a planet on its orbit in Обсерватория. [overlay] sits over the picture, clipped to it.
 * [seed] makes the polaroid tilt and the moon's place differ from cover to cover.
 */
@Composable
fun ThemedArt(url: String?, modifier: Modifier = Modifier, seed: Int = 0, caption: String? = null, overlay: @Composable BoxScope.() -> Unit = {}) {
    val t = theme
    when (t.look) {
        // Satanic: the cover in a mount — a thin frame, a margin, a red tick in the corner
        Look.Rite -> Box(modifier.border(1.dp, t.panelBorder).padding(6.dp)) {
            Cover(url, Modifier.fillMaxSize(), shape = RectangleShape)
            Box(Modifier.fillMaxSize(), content = overlay)
            Box(Modifier.align(Alignment.BottomStart).offset(y = 4.dp).size(16.dp, 2.dp).background(t.accent))
        }
        Look.Terminal -> Box(modifier.border(2.dp, t.panelBorder).padding(2.dp)) {
            Cover(url, Modifier.fillMaxSize(), shape = RectangleShape)
            Box(Modifier.fillMaxSize(), content = overlay)
        }
        Look.Neon -> {
            val outer = RoundedCornerShape(8.dp)
            val inner = RoundedCornerShape(6.dp)
            Box(
                modifier.shadow(12.dp, outer, ambientColor = t.accent, spotColor = t.accent)
                    .clip(outer).background(Brush.linearGradient(listOf(t.accent, t.accent2))).padding(2.dp),
            ) {
                Cover(url, Modifier.fillMaxSize(), shape = inner)
                Box(Modifier.fillMaxSize().clip(inner), content = overlay)
            }
        }
        Look.Quilt -> {
            val tilt = ((abs(seed) % 7) - 3) * 0.7f
            val paper = if (t.night) Color(0xFFF2E8D5) else Color(0xFFF8F0E3)
            Box(modifier) {
                Column(
                    Modifier.fillMaxSize().padding(4.dp).graphicsLayer { rotationZ = tilt }
                        .shadow(5.dp, RoundedCornerShape(3.dp)).background(paper, RoundedCornerShape(3.dp))
                        .padding(start = 6.dp, end = 6.dp, top = 6.dp),
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Cover(url, Modifier.fillMaxSize(), shape = RoundedCornerShape(2.dp))
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)), content = overlay)
                    }
                    Box(Modifier.fillMaxWidth().height(if (caption != null) 34.dp else 14.dp), contentAlignment = Alignment.Center) {
                        if (caption != null) Txt(caption, size = 19.sp, family = Fonts.caveat, color = Color(0xFF5A4636), maxLines = 1)
                    }
                }
                // a strip of washi tape holding it to the wall
                Box(
                    Modifier.align(Alignment.TopCenter).size(38.dp, 12.dp).graphicsLayer { rotationZ = -5f + tilt }
                        .background(t.accent2.copy(alpha = 0.6f)),
                )
            }
        }
        Look.Atlas -> Box(
            modifier.drawBehind {
                val r = size.minDimension / 2 - 1.dp.toPx()
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(t.accent2.copy(alpha = 0.45f), r, c, style = Stroke(1.dp.toPx()))
                val a = (abs(seed) % 360) * PI / 180
                val moon = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
                drawCircle(t.bg, 4.5.dp.toPx(), moon)
                drawCircle(t.accent, 3.dp.toPx(), moon)
            },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize(0.8f).border(1.dp, t.accent2.copy(alpha = 0.6f), CircleShape).padding(2.dp)) {
                Cover(url, Modifier.fillMaxSize(), shape = CircleShape)
                Box(Modifier.fillMaxSize().clip(CircleShape), content = overlay)
            }
        }
        Look.Blossom -> {
            val outer = RoundedCornerShape(t.coverRadius + 4.dp)
            val inner = RoundedCornerShape(t.coverRadius)
            Box(modifier.border(1.5.dp, t.accent.copy(alpha = 0.55f), outer).padding(4.dp)) {
                Cover(url, Modifier.fillMaxSize(), shape = inner)
                Box(Modifier.fillMaxSize().clip(inner), content = overlay)
            }
        }
        Look.Island -> {
            val s = RoundedCornerShape(18.dp)
            Box(modifier.shadow(14.dp, s, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))) {
                Cover(url, Modifier.fillMaxSize(), shape = s)
                Box(Modifier.fillMaxSize().clip(s), content = overlay)
            }
        }
        else -> Box(modifier) {
            val s = RoundedCornerShape(t.coverRadius)
            Cover(url, Modifier.fillMaxSize(), shape = s)
            Box(Modifier.fillMaxSize().clip(s), content = overlay)
        }
    }
}

/** Глубина: the cover's reflection on the glass below it, fading out. */
@Composable
fun Reflection(url: String?, height: Dp = 44.dp) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(height).clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent)), blendMode = BlendMode.DstIn)
            },
    ) {
        Cover(
            url,
            Modifier.wrapContentSize(Alignment.TopCenter, unbounded = true).size(maxWidth).padding(top = 4.dp).graphicsLayer { scaleY = -1f },
            shape = RoundedCornerShape(18.dp),
        )
    }
}

/** Обсерватория: the cover as a planet inside an astrolabe; the gold arc is how much of the track has played. */
@Composable
fun Astrolabe(url: String?, modifier: Modifier, overlay: @Composable BoxScope.() -> Unit = {}) {
    val t = theme
    val pos by Player.engine.position.collectAsState()
    val dur by Player.engine.duration.collectAsState()
    val p = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2 - 6.dp.toPx()
            val gold = t.accent2
            drawCircle(gold.copy(alpha = 0.5f), r, c, style = Stroke(1.dp.toPx()))
            drawCircle(gold.copy(alpha = 0.25f), r - 12.dp.toPx(), c, style = Stroke(1.dp.toPx()))
            // degree ticks, a longer one every 30°
            for (i in 0 until 72) {
                val a = i * 5 * PI / 180
                val long = i % 6 == 0
                val r0 = r - (if (long) 9.dp else 5.dp).toPx()
                drawLine(
                    gold.copy(alpha = if (long) 0.8f else 0.4f),
                    Offset(c.x + r0 * cos(a).toFloat(), c.y + r0 * sin(a).toFloat()),
                    Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()),
                    1.dp.toPx(),
                )
            }
            val arcR = r + 3.dp.toPx()
            drawArc(
                t.accent, -90f, 360f * p, false, Offset(c.x - arcR, c.y - arcR), Size(arcR * 2, arcR * 2),
                style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
            )
            val a = (-90 + 360 * p) * PI / 180
            val head = Offset(c.x + arcR * cos(a).toFloat(), c.y + arcR * sin(a).toFloat())
            drawCircle(t.accent.copy(alpha = 0.3f), 7.dp.toPx(), head)
            drawCircle(t.accent, 3.5.dp.toPx(), head)
        }
        Box(Modifier.fillMaxSize(0.74f).border(1.dp, t.accent2.copy(alpha = 0.6f), CircleShape).padding(3.dp)) {
            Cover(url, Modifier.fillMaxSize(), shape = CircleShape)
            Box(Modifier.fillMaxSize().clip(CircleShape), content = overlay)
        }
    }
}

/** Ретровейв: a little LCD under the cover — time in a pixel face and a live equaliser. */
@Composable
fun NeonLcd() {
    val t = theme
    val pos by Player.engine.position.collectAsState()
    val dur by Player.engine.duration.collectAsState()
    val levels by Player.engine.levels.collectAsState()
    val state by Player.engine.state.collectAsState()
    Row(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = if (t.night) 0.45f else 0.75f))
            .border(1.dp, t.accent.copy(alpha = 0.45f), RoundedCornerShape(6.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(pos.msAsDuration(), size = 26.sp, family = Fonts.vt323, color = t.accent,
            modifier = Modifier.drawBehind { drawCircle(Brush.radialGradient(listOf(t.accent.copy(alpha = 0.25f), Color.Transparent)), size.maxDimension * 0.7f) })
        Txt(" / ${if (dur > 0) dur.msAsDuration() else "-:--"}", size = 18.sp, family = Fonts.vt323, color = t.accent2.copy(alpha = 0.8f))
        Spacer(w = 12.dp)
        Canvas(Modifier.weight(1f).height(26.dp)) {
            val n = 16
            val gap = 2.dp.toPx()
            val w = (size.width - gap * (n - 1)) / n
            val seg = 3.dp.toPx()
            for (i in 0 until n) {
                val v = if (state == EngineState.Playing) levels[(i * levels.size / n).coerceAtMost(levels.size - 1)].coerceIn(0.05f, 1f) else 0.05f
                val cells = (v * size.height / (seg + 1)).toInt().coerceAtLeast(1)
                for (k in 0 until cells) {
                    val y = size.height - (k + 1) * (seg + 1)
                    val f = k * (seg + 1) / size.height
                    drawRect(lerpColor(t.accent2, t.accent, f), Offset(i * (w + gap), y), Size(w, seg))
                }
            }
        }
    }
}

private fun lerpColor(a: Color, b: Color, f: Float) = Color(
    a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f, a.blue + (b.blue - a.blue) * f, 1f,
)

