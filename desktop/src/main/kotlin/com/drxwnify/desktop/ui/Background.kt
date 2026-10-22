package com.drxwnify.desktop.ui

import kotlinx.coroutines.delay
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.drxwnify.desktop.ui.theme.Backdrop
import com.drxwnify.desktop.ui.theme.DrxTheme
import com.drxwnify.desktop.ui.theme.Fonts
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Mouse position over the window, for the backgrounds that react to it. */
object Pointer { @Volatile var x = -9999f; @Volatile var y = -9999f }

@Composable
fun ThemeBackground(t: DrxTheme, glyphs: Boolean, motion: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(t.bg)) {
        when (t.backdrop) {
            Backdrop.Tategaki -> {
                Mist(t, motion)
                if (glyphs) GlyphField(t, motion, react = false)
                Grain(if (t.night) 0.05f else 0.03f)
                if (t.night) Vignette()
            }
            Backdrop.Gruvbox -> Box(Modifier.fillMaxSize().background(t.bg2))
            Backdrop.Retrowave -> { Mist(t, motion); Retro(t, motion); if (t.night) Vignette(); Scanlines(t) }
            Backdrop.Ash -> { Mist(t, motion); if (glyphs) Ash(t, motion); Grain(0.05f); Vignette() }
            Backdrop.Mono -> { Mist(t, motion); Grain(0.04f) }
            Backdrop.Depth -> { Depth(t, motion); Grain(0.035f) }
            Backdrop.Sakura -> { Mist(t, motion); if (glyphs) SakuraNightSky(t, motion) }
            Backdrop.Cozy -> { CozyRoom(t, motion, glyphs); Grain(0.04f) }
            Backdrop.Observatory -> { StarAtlas(t, motion, glyphs); Grain(0.03f) }
        }
    }
}

@Composable
private fun Mist(t: DrxTheme, motion: Boolean) {
    val clock by rememberClock(motion)
    val k = if (motion) pingPong(clock, 42f) else 0.3f
    Canvas(Modifier.fillMaxSize()) {
        fun blob(c: Color, cx: Float, cy: Float, r: Float, a: Float) {
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
        }
        val w = size.width; val h = size.height
        blob(t.mist[0], w * (0.08f + 0.06f * k), h * (0.05f + 0.05f * k), max(w, h) * 0.42f, 0.55f)
        blob(t.mist[1], w * (0.92f - 0.07f * k), h * (0.45f + 0.08f * k), max(w, h) * 0.38f, 0.45f)
        blob(t.mist[2], w * (0.45f + 0.08f * k), h * (1.02f - 0.05f * k), max(w, h) * 0.40f, 0.42f)
    }
}

@Composable
private fun Vignette() = Canvas(Modifier.fillMaxSize()) {
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)), center = Offset(size.width / 2, size.height * 0.4f), radius = max(size.width, size.height) * 0.75f))
}

@Composable
private fun Grain(alpha: Float = 0.05f) {
    val shader = remember { org.jetbrains.skia.Shader.makeFractalNoise(0.9f, 0.9f, 2, 7f) }
    val paint = remember(alpha) {
        Paint().apply {
            this.shader = shader
            this.alpha = (alpha * 255).toInt()
            blendMode = org.jetbrains.skia.BlendMode.OVERLAY
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        drawIntoCanvas { it.nativeCanvas.drawRect(org.jetbrains.skia.Rect.makeWH(size.width, size.height), paint) }
    }
}

/** Faint katakana, kanji and digits drifting up; slow Perlin noise tints patches of them pink. */
@Composable
private fun GlyphField(t: DrxTheme, motion: Boolean, react: Boolean) {
    val chars = remember { "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワン音夜静光風雨空月星波0123456789".map { it.toString() } }
    val field = remember { GlyphState(chars) }
    var tick by remember { mutableLongStateOf(0L) }
    val run = motion && !AnimationGate.minimized
    val frameMs = AnimationGate.frameMs
    LaunchedEffect(run, frameMs) {
        // By real elapsed time with sub-pixel glyphs, so 30 steps a second still drift smoothly.
        // Nothing moves while the window is minimised (it used to redraw every frame regardless).
        if (!run) return@LaunchedEffect
        var last = System.nanoTime()
        while (true) {
            delay(frameMs)
            val now = System.nanoTime()
            field.step(min(0.1f, (now - last) / 1_000_000_000f))
            last = now
            tick = now
        }
    }
    val font = remember {
        Font(Fonts.skiaJp, 13f).apply {
            // Sub-pixel placement: whole-pixel snapping made slow glyphs hop a pixel at a time.
            isSubpixel = true
            hinting = org.jetbrains.skia.FontHinting.NONE
            edging = org.jetbrains.skia.FontEdging.ANTI_ALIAS
        }
    }
    val paint = remember { Paint().apply { isAntiAlias = true } }
    val base = t.glyph.toArgb()
    val accent = t.glyphAccent
    Canvas(Modifier.fillMaxSize()) {
        tick.let { }
        field.ensure(size)
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            for (i in 0 until field.n) {
                val x = field.x[i]
                val y = field.y[i]
                var k = field.noiseAt(x, y)
                if (react) {
                    val dx = x - Pointer.x; val dy = y - Pointer.y
                    val d = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (d < 160) k = max(k, 1f - d / 160f)
                }
                val a = field.a[i]
                paint.color = if (k > 0f) {
                    accent.copy(alpha = (a + k * if (react) 0.30f else 0.075f).coerceAtMost(1f)).toArgb()
                } else (base and 0x00FFFFFF) or ((a * 255).toInt() shl 24)
                nc.drawString(field.c[i], x, y, font, paint)
            }
        }
    }
}

private class GlyphState(val chars: List<String>) {
    var n = 0
    var x = FloatArray(0); var y = FloatArray(0); var a = FloatArray(0); var v = FloatArray(0); var ph = FloatArray(0)
    var c = arrayOf<String>()
    var time = 0f
    private var w = 0f; private var h = 0f
    private val rnd = Random(7)
    private val perm = IntArray(512).also { p ->
        val base = (0 until 256).shuffled(Random(11)); for (i in 0 until 512) p[i] = base[i and 255]
    }

    fun ensure(s: Size) {
        if (s.width == w && s.height == h && n > 0) return
        w = s.width; h = s.height
        val step = 28f
        val cols = (w / step).toInt() + 1; val rows = (h / step).toInt() + 2
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
        for (r in 0 until rows) for (col in 0 until cols) if (rnd.nextFloat() < 0.45f) { xs += col * step + step / 2; ys += r * step + step / 2 }
        n = xs.size
        x = xs.toFloatArray(); y = ys.toFloatArray()
        a = FloatArray(n) { 0.02f + rnd.nextFloat() * 0.024f }
        v = FloatArray(n) { 0.12f + rnd.nextFloat() * 0.22f }
        ph = FloatArray(n) { rnd.nextFloat() * 6.28f }
        c = Array(n) { chars[rnd.nextInt(chars.size)] }
    }

    /** [dt] in seconds; glyphs rise 3.6–10 px/s, the noise drifts slowly. */
    fun step(dt: Float) {
        time += 0.075f * dt
        val span = (h / 28f + 2).toInt() * 28f
        for (i in 0 until n) {
            y[i] -= v[i] * 30f * dt
            if (y[i] < -14) { y[i] += span; c[i] = chars[rnd.nextInt(chars.size)] }
        }
    }

    fun noiseAt(px: Float, py: Float): Float {
        val n = noise(px / 260f + time, py / 260f - time * 0.6f) * 0.5f + noise(px / 120f - time * 0.4f, py / 120f) * 0.25f
        return ((n - 0.18f) / 0.3f).coerceIn(0f, 1f)
    }

    private fun fade(t: Float) = t * t * t * (t * (t * 6 - 15) + 10)
    private fun grad(hash: Int, x: Float, y: Float): Float {
        val u = if (hash and 1 == 0) x else -x
        val v = if (hash and 2 == 0) y else -y
        return if (hash and 4 == 0) u + v else (u + v) * 0.7f
    }
    private fun noise(xIn: Float, yIn: Float): Float {
        val fx = floor(xIn); val fy = floor(yIn)
        val xi = fx.toInt() and 255; val yi = fy.toInt() and 255
        val xf = xIn - fx; val yf = yIn - fy
        val u = fade(xf); val v = fade(yf)
        val aa = perm[perm[xi] + yi]; val ab = perm[perm[xi] + yi + 1]
        val ba = perm[perm[xi + 1] + yi]; val bb = perm[perm[xi + 1] + yi + 1]
        val x1 = grad(aa, xf, yf) + u * (grad(ba, xf - 1, yf) - grad(aa, xf, yf))
        val x2 = grad(ab, xf, yf - 1) + u * (grad(bb, xf - 1, yf - 1) - grad(ab, xf, yf - 1))
        return x1 + v * (x2 - x1)
    }
}

@Composable
private fun Retro(t: DrxTheme, motion: Boolean) {
    val clock by rememberClock(motion)
    val phase = if (motion) (clock / 1.6f) % 1f else 0f
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val horizon = h * 0.72f
        // the sun, cut by stripes, low on the right
        val r = h * 0.13f
        val cx = w * 0.80f; val cy = horizon - r * 0.30f
        drawCircle(Brush.radialGradient(listOf(t.accent.copy(alpha = 0.18f), Color.Transparent), Offset(cx, cy), r * 2.2f), r * 2.2f, Offset(cx, cy))
        drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFE56B), Color(0xFFFF9D3D), t.accent), cy - r, cy + r), r, Offset(cx, cy), alpha = 0.78f)
        var sy = cy + r * 0.05f
        var gap = 3f
        while (sy < cy + r) { drawRect(t.bg, Offset(cx - r, sy), Size(r * 2, gap)); sy += gap + 9f; gap += 1.6f }
        drawRect(t.bg, Offset(0f, horizon), Size(w, h - horizon))
        // perspective grid
        val lines = 14
        for (i in 0..lines) {
            val z = ((i + phase) / lines)
            val yy = horizon + (h - horizon) * z * z
            drawLine(t.accent.copy(alpha = 0.10f + 0.35f * z), Offset(0f, yy), Offset(w, yy), 1.5f)
        }
        val vx = w * 0.5f
        for (i in -20..20) {
            val xb = vx + i * w * 0.09f
            drawLine(t.accent.copy(alpha = 0.22f), Offset(vx + i * 6f, horizon), Offset(xb, h), 1.3f)
        }
        drawRect(Brush.verticalGradient(listOf(t.bg, Color.Transparent), horizon, horizon + 40f), Offset(0f, horizon), Size(w, 40f))
    }
}

@Composable
private fun Depth(t: DrxTheme, motion: Boolean) {
    val clock by rememberClock(motion)
    val q = (clock / 22f) % 1f
    val k = if (motion) pingPong(clock, 14f) else 0.5f
    val bubbles = remember { List(26) { Triple(Random.nextFloat(), Random.nextFloat(), 3f + Random.nextFloat() * 8f) } }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(
            if (t.dusk) listOf(Color(0xFF0E4A5C), Color(0xFF0B3B4B), Color(0xFF082C39), Color(0xFF061F29))
            else if (t.night) listOf(Color(0xFF06303F), Color(0xFF042233), Color(0xFF02131D), Color(0xFF010B11))
            else listOf(Color(0xFFEFFCFB), Color(0xFFCDEFF0), Color(0xFFA9DDE2), Color(0xFF8FCFD6)),
        ))
        // light rays from the surface
        for (i in 0 until 6) {
            val x0 = w * (i / 6f) + w * 0.06f * k
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(x0, 0f); lineTo(x0 + 70f, 0f); lineTo(x0 + 70f + h * 0.35f, h * 0.8f); lineTo(x0 + h * 0.35f, h * 0.8f); close()
            }
            drawPath(path, Brush.verticalGradient(listOf(t.accent.copy(alpha = 0.06f), Color.Transparent), 0f, h * 0.8f))
        }
        // caustics near the surface
        for (i in 0 until 9) {
            val cx = (w * (i / 9f) + 140f * sin(k * 6.28f + i)) % w
            val cy = 40f + 50f * sin(k * 3.1f + i * 1.7f)
            drawCircle(Brush.radialGradient(listOf(t.accent.copy(alpha = 0.07f), Color.Transparent), Offset(cx + 60f, cy + 17f), 90f), 90f, Offset(cx + 60f, cy + 17f))
        }
        // bubbles
        bubbles.forEach { (bx, by, r) ->
            val yy = ((by - (if (motion) q else 0f) * (0.6f + r / 20f)) % 1f + 1f) % 1f * h
            val xx = bx * w + sin(yy / 40f) * 6f
            drawCircle(t.glyph.copy(alpha = 0.25f), r, Offset(xx, yy), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
        }
    }
}
