package com.drxwnify.desktop.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.drxwnify.desktop.ui.theme.DrxTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Seconds since the backdrop appeared, ticking every frame while [motion] is on. */
@Composable
fun rememberClock(motion: Boolean): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val run = motion && !AnimationGate.minimized
    val frameMs = AnimationGate.frameMs
    LaunchedEffect(run, frameMs) {
        if (!run) return@LaunchedEffect
        var last = System.nanoTime()
        while (true) {
            delay(frameMs)
            val now = System.nanoTime()
            time.floatValue += min(0.1f, (now - last) / 1_000_000_000f)
            last = now
        }
    }
    return time
}

/**
 * How often the backgrounds may move: 30 frames a second while the window is in use, 12 behind another
 * window (a game), none while it is minimised. They used to redraw at the screen's full rate — 144 Hz
 * and more — the glass blur over them included, even with nobody looking.
 */
object AnimationGate {
    var minimized by mutableStateOf(false)
    var focused by mutableStateOf(true)
    val frameMs: Long get() = if (focused) 33L else 83L
}

/** 0 → 1 → 0 over [period] seconds of [time]: a slow back-and-forth for drifting things. */
fun pingPong(time: Float, period: Float): Float {
    val x = (time / period) % 2f
    return if (x < 1f) x else 2f - x
}

private fun posMod(a: Float, m: Float) = ((a % m) + m) % m

/** Ретровейв: faint CRT lines over everything. */
@Composable
fun Scanlines(t: DrxTheme) = Canvas(Modifier.fillMaxSize()) {
    val c = if (t.night) Color.White.copy(alpha = 0.022f) else Color.Black.copy(alpha = 0.02f)
    var y = 0f
    while (y < size.height) { drawLine(c, Offset(0f, y), Offset(size.width, y), 1f); y += 3f }
}

// ── Сакура ──

private class Petal(val x0: Float, val y0: Float, val speed: Float, val size: Float, val depth: Float, val phase: Float, val pink: Boolean)

/**
 * Night sakura: a small moon with a slow halo and a blossoming sprig in the empty part of the top bar,
 * swaying a little in the wind, and petals drifting down far behind the panels (smaller and paler the
 * further away). By day: morning haze instead of the moon.
 */
@Composable
fun SakuraNightSky(t: DrxTheme, motion: Boolean) {
    val clock by rememberClock(motion)
    val petals = remember {
        val r = Random(9)
        List(42) {
            val depth = r.nextFloat()
            Petal(r.nextFloat(), r.nextFloat(), 14f + 30f * depth, 4f + 8f * depth, depth, r.nextFloat() * 6.28f, r.nextFloat() > 0.35f)
        }
    }
    // A sprig hanging into the empty stretch of the top bar (between the arrows and the home button),
    // local coordinates 0…190 × 0…58; the moon rises behind it at night.
    val branch = remember {
        listOf(
            Path().apply { moveTo(196f, -6f); cubicTo(150f, 10f, 120f, 4f, 88f, 22f); cubicTo(60f, 36f, 34f, 34f, 8f, 52f) } to 4f,
            Path().apply { moveTo(122f, 10f); cubicTo(114f, 24f, 120f, 36f, 110f, 50f) } to 2.5f,
            Path().apply { moveTo(54f, 32f); cubicTo(56f, 20f, 66f, 12f, 70f, 2f) } to 2f,
        )
    }
    val blossoms = remember {
        listOf(168f to 6f, 140f to 10f, 104f to 16f, 116f to 34f, 110f to 50f, 78f to 26f, 70f to 4f, 46f to 36f, 24f to 44f, 8f to 52f)
    }
    val wood = if (t.night) Color(0xFF3A2433) else Color(0xFF5B3A3A)
    val petalA = if (t.night) Color(0xFFE9A9BD) else Color(0xFFF6B8C8)
    val petalB = if (t.night) Color(0xFFF3C6D3) else Color(0xFFF9D3DD)
    val core = if (t.night) Color(0xFFB04A6F) else Color(0xFFD9627E)
    Canvas(Modifier.fillMaxSize()) {
        val time = clock
        val w = size.width; val h = size.height
        val d = density
        // the sprig only where the top bar leaves room for it (wide windows)
        val sprigX = 250f * d
        val roomy = w / d >= 1180f
        if (t.night) {
            val mc = if (roomy) Offset(sprigX + 128f * d, 30f * d) else Offset(w * 0.3f, 30f * d)
            val halo = 0.16f + 0.04f * sin(time * 0.5f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFDCD6F0).copy(alpha = halo), Color.Transparent), mc, 120f * d), 120f * d, mc)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFF6F1EA), Color(0xFFDCD6E6), Color(0xFFB9B4CC)), mc - Offset(5f * d, 6f * d), 26f * d), 19f * d, mc)
        } else {
            val sc = Offset(w * 0.68f, h * 0.06f)
            drawCircle(Brush.radialGradient(listOf(Color(0x66FFE6EE), Color.Transparent), sc, 460f), 460f, sc)
        }
        // petals far behind everything
        petals.forEach { p ->
            val y = posMod(p.y0 * (h + 60f) + p.speed * time, h + 60f) - 30f
            val x = posMod(p.x0 * (w + 60f) + sin(time * 0.6f + p.phase) * 40f * p.depth - time * 10f * p.depth, w + 60f) - 30f
            val a = (if (t.night) 0.16f else 0.32f) + 0.38f * p.depth
            rotate(p.phase * 57f + time * (40f + 70f * p.depth), Offset(x, y)) {
                drawOval((if (p.pink) petalA else petalB).copy(alpha = a), Offset(x - p.size * 0.35f, y - p.size * 0.5f), Size(p.size * 0.7f, p.size))
            }
        }
        if (roomy) {
            val sway = if (motion) sin(time * 0.35f) * 1.2f else 0f
            rotate(sway, Offset(sprigX + 196f * d, 0f)) {
                translate(left = sprigX, top = 0f) {
                    scale(d, d, Offset.Zero) {
                        branch.forEach { (path, width) -> drawPath(path, wood.copy(alpha = 0.85f), style = Stroke(width, cap = StrokeCap.Round)) }
                        blossoms.forEachIndexed { i, (bx, by) ->
                            val r = 8f * (0.75f + (i % 4) * 0.1f)
                            val spin = i * 37f + (if (motion) sin(time * 0.8f + i) * 6f else 0f)
                            blossom(Offset(bx, by), r, spin, petalA, petalB, core, if (t.night) 0.8f else 0.95f)
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.blossom(c: Offset, r: Float, spin: Float, a: Color, b: Color, core: Color, alpha: Float) {
    for (k in 0 until 5) {
        rotate(spin + k * 72f, c) {
            drawOval((if (k % 2 == 0) a else b).copy(alpha = alpha), Offset(c.x - r * 0.32f, c.y - r), Size(r * 0.64f, r))
        }
    }
    drawCircle(core.copy(alpha = alpha), r * 0.22f, c)
}

// ── Cozy ──

/**
 * A warm room: the lamp's glow breathing slowly, a knitted weave, a string of fairy lights along the
 * top whose bulbs twinkle each at its own pace, dust floating in the light. By day the lights are
 * off and a beam of sun comes in from the window instead.
 */
@Composable
fun CozyRoom(t: DrxTheme, motion: Boolean, decor: Boolean = true) {
    val clock by rememberClock(motion)
    val bulbColors = listOf(Color(0xFFFFD08A), Color(0xFFF2B36B), Color(0xFFFFE6B8), Color(0xFFE9A06A))
    val dust = remember { val r = Random(4); List(26) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) } }
    Canvas(Modifier.fillMaxSize()) {
        val time = clock
        val w = size.width; val h = size.height
        val breathe = 1f + 0.12f * sin(time * 0.4f)
        if (t.night) {
            drawCircle(Brush.radialGradient(listOf(t.accent.copy(alpha = 0.20f * breathe), Color.Transparent), Offset(w * 0.06f, h * 0.02f), 760f), 760f, Offset(w * 0.06f, h * 0.02f))
            drawCircle(Brush.radialGradient(listOf(t.accent2.copy(alpha = 0.14f), Color.Transparent), Offset(w * 0.94f, h), 620f), 620f, Offset(w * 0.94f, h))
        } else {
            val beam = Path().apply { moveTo(-80f, -40f); lineTo(w * 0.32f, -40f); lineTo(w * 0.62f, h * 1.1f); lineTo(w * 0.2f, h * 1.1f); close() }
            drawPath(beam, Brush.linearGradient(listOf(Color(0x66FFF3D6), Color(0x00FFF3D6)), Offset(0f, 0f), Offset(w * 0.5f, h)))
        }
        // knitted weave
        val weave = (if (t.night) Color(0xFFFFE6C8) else Color(0xFF8A5A2B)).copy(alpha = 0.022f)
        var d = -h
        while (d < w) {
            drawLine(weave, Offset(d, 0f), Offset(d + h, h), 2f)
            drawLine(weave, Offset(d + h, 0f), Offset(d, h), 2f)
            d += 9f
        }
        if (!decor) return@Canvas
        // dust in the light
        dust.forEach { (dx, dy, k) ->
            val x = w * 0.05f + dx * w * 0.45f + sin(time * 0.15f + k * 9f) * 30f
            val y = posMod(dy * h - time * (4f + 6f * k), h)
            drawCircle((if (t.night) Color(0xFFFFE2B0) else Color(0xFFFFFFFF)).copy(alpha = 0.10f + 0.18f * k), 1.2f + 1.6f * k, Offset(x, y))
        }
        // fairy lights along the top
        val wire = Path()
        var x = 0f
        var first = true
        while (x <= w + 10f) {
            val y = lightY(x, w)
            if (first) { wire.moveTo(x, y); first = false } else wire.lineTo(x, y)
            x += 12f
        }
        drawPath(wire, if (t.night) Color(0xFF5A4535) else Color(0xFF9C8268), style = Stroke(1.5f))
        var i = 0
        x = 28f
        while (x < w) {
            val y = lightY(x, w) + 4f
            val c = bulbColors[i % bulbColors.size]
            val tw = if (t.night) 0.55f + 0.45f * sin(time * (1.1f + (i * 37 % 7) * 0.17f) + i * 1.9f) else 0.35f
            if (t.night) drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.45f * tw), Color.Transparent), Offset(x, y + 4f), 16f), 16f, Offset(x, y + 4f))
            drawOval(c.copy(alpha = if (t.night) 0.35f + 0.6f * tw else 0.6f), Offset(x - 3.5f, y), Size(7f, 10f))
            x += 56f; i++
        }
    }
}

private fun lightY(x: Float, w: Float): Float {
    val span = 360f
    val f = posMod(x, span) / span
    // a shallow sag, so the bulbs stay above the top bar's buttons
    return 3f + sin(f * PI.toFloat()) * 7f
}

// ── Обсерватория ──

/**
 * An old star atlas: the celestial grid turning very slowly around a pole off the screen, stars
 * twinkling, two constellations with their lines, now and then a shooting star. By day the same
 * on parchment, in sepia ink.
 */
@Composable
fun StarAtlas(t: DrxTheme, motion: Boolean, decor: Boolean = true) {
    val clock by rememberClock(motion)
    val stars = remember {
        val r = Random(21)
        List(170) { floatArrayOf(r.nextFloat(), r.nextFloat(), if (r.nextFloat() > 0.9f) 2.4f else 0.9f + r.nextFloat() * 1.1f, r.nextFloat(), r.nextFloat() * 6.28f, if (r.nextFloat() > 0.82f) 1f else 0f) }
    }
    val lyra = listOf(0.36f to 0.79f, 0.40f to 0.74f, 0.45f to 0.77f, 0.49f to 0.71f, 0.53f to 0.74f, 0.515f to 0.82f, 0.45f to 0.77f)
    val crown = listOf(0.69f to 0.045f, 0.725f to 0.065f, 0.755f to 0.035f, 0.79f to 0.055f)
    Canvas(Modifier.fillMaxSize()) {
        val time = clock
        val w = size.width; val h = size.height
        val ink = if (t.night) Color(0xFFC9A86A) else Color(0xFF6B5427)
        if (t.dusk) {
            drawRect(Brush.radialGradient(listOf(Color(0xFF2A3D75), Color(0xFF1C2A55), Color(0xFF131D3D)), Offset(w * 0.5f, h * 0.3f), max(w, h) * 0.8f))
        } else if (t.night) {
            drawRect(Brush.radialGradient(listOf(Color(0xFF16244E), Color(0xFF0E1834), Color(0xFF080D1D)), Offset(w * 0.5f, h * 0.3f), max(w, h) * 0.8f))
        } else {
            drawRect(Brush.radialGradient(listOf(Color(0xFFF7F0DF), Color(0xFFEDE2C9), Color(0xFFD9C8A4)), Offset(w * 0.5f, h * 0.4f), max(w, h) * 0.85f))
        }
        // celestial grid around a pole above the top-left corner
        val pole = Offset(-160f, -260f)
        val grid = ink.copy(alpha = if (t.night) 0.07f else 0.12f)
        var radius = 520f
        while (radius < 1700f) { drawCircle(grid, radius, pole, style = Stroke(1f)); radius += 240f }
        val turn = time * 0.4f
        for (k in 0 until 12) {
            val a = (k * 15f + turn - 20f) * PI.toFloat() / 180f
            drawLine(grid, pole, Offset(pole.x + cos(a) * 2600f, pole.y + sin(a) * 2600f), 1f)
        }
        if (!decor) return@Canvas
        // stars
        stars.forEach { s ->
            val tw = 0.6f + 0.4f * sin(time * (0.6f + s[3] * 1.6f) + s[4])
            val base = 0.25f + s[3] * 0.6f
            val c = if (t.night) (if (s[5] > 0f) Color(0xFFF0DDB0) else Color(0xFFEDE4D3)) else Color(0xFF1C2747)
            val o = Offset(s[0] * w, s[1] * h)
            if (s[2] > 2f && t.night) drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.5f * tw), Color.Transparent), o, 8f), 8f, o)
            drawCircle(c.copy(alpha = (base * tw * if (t.night) 1f else 0.45f).coerceAtMost(1f)), s[2] * (if (t.night) 1f else 0.8f), o)
        }
        // constellations
        val line = ink.copy(alpha = 0.32f + 0.08f * sin(time * 0.5f))
        listOf(lyra, crown).forEach { pts ->
            for (k in 1 until pts.size) drawLine(line, Offset(pts[k - 1].first * w, pts[k - 1].second * h), Offset(pts[k].first * w, pts[k].second * h), 1f)
            pts.forEach { (px, py) -> drawCircle(if (t.night) Color(0xFFF0DDB0) else Color(0xFF6B5427), 2.2f, Offset(px * w, py * h)) }
        }
        // a shooting star every 14 s (night only)
        if (t.night && motion) {
            val cycle = posMod(time, 14f)
            if (cycle < 1.2f) {
                val p = cycle / 1.2f
                val start = Offset(w * 0.25f, h * 0.06f)
                val head = Offset(start.x + p * w * 0.22f, start.y + p * h * 0.10f)
                drawLine(Brush.linearGradient(listOf(Color.Transparent, Color(0xFFF0DDB0).copy(alpha = 0.8f * (1f - p))), head - Offset(90f, 40f), head), head - Offset(90f, 40f), head, 1.6f)
            }
        }
    }
}

// ── Satanic ──

private class Flake(val x0: Float, val y0: Float, val speed: Float, val size: Float, val drift: Float, val phase: Float)

/**
 * Ash rising slowly through the dark, a few flakes catching a dim red light, and an ember glow along
 * the bottom edge breathing very slowly. No symbols: the mood is in the colours and the slowness.
 */
@Composable
fun Ash(t: DrxTheme, motion: Boolean) {
    val clock by rememberClock(motion)
    val flakes = remember {
        val r = Random(13)
        List(60) { Flake(r.nextFloat(), r.nextFloat(), 6f + 14f * r.nextFloat(), 1f + 2.4f * r.nextFloat(), 8f + 20f * r.nextFloat(), r.nextFloat() * 6.28f) }
    }
    Canvas(Modifier.fillMaxSize()) {
        val time = clock
        val w = size.width; val h = size.height
        val breathe = 0.5f + 0.5f * sin(time * 0.25f)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, t.accent.copy(alpha = 0.05f + 0.05f * breathe)), startY = h * 0.55f, endY = h))
        flakes.forEachIndexed { i, f ->
            val y = h + 20f - posMod(f.y0 * (h + 40f) + f.speed * time, h + 40f)
            val x = posMod(f.x0 * w + sin(time * 0.3f + f.phase) * f.drift, w)
            val nearBottom = (y / h).coerceIn(0f, 1f)
            val ember = i % 9 == 0
            val c = if (ember) t.accent.copy(alpha = 0.25f + 0.35f * nearBottom) else Color(0xFFB8B0AC).copy(alpha = 0.05f + 0.12f * nearBottom)
            drawCircle(c, f.size, Offset(x, y))
        }
    }
}

