package com.drxwnify.desktop.ui

import androidx.compose.ui.text.withLink
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlin.math.abs
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.drxwnify.desktop.ui.theme.Look
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drxwnify.desktop.ui.theme.DrxTheme
import com.drxwnify.desktop.ui.theme.Fonts
import com.drxwnify.desktop.ui.theme.LocalTheme
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

val LocalHaze = staticCompositionLocalOf<HazeState?> { null }

val theme: DrxTheme @Composable get() = LocalTheme.current

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = theme.text,
    family: FontFamily = theme.body,
    maxLines: Int = 1,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    align: androidx.compose.ui.text.style.TextAlign? = null,
    brush: Brush? = null,
    italic: Boolean = false,
) {
    val base = TextStyle(
        fontSize = size, fontWeight = weight, color = color, fontFamily = family,
        letterSpacing = letterSpacing, lineHeight = lineHeight,
        fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
        textAlign = align ?: androidx.compose.ui.text.style.TextAlign.Unspecified,
    )
    BasicText(
        text = text,
        modifier = modifier,
        style = if (brush != null) base.merge(TextStyle(brush = brush)) else base,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Heading(text: String, modifier: Modifier = Modifier, size: TextUnit = 20.sp) =
    Txt(text, modifier, size = size, weight = theme.headingWeight, family = theme.heading, letterSpacing = (-0.4).sp)

@Composable
fun Mono(text: String, modifier: Modifier = Modifier, size: TextUnit = 11.sp, color: Color = theme.mute) =
    Txt(text, modifier, size = size, color = color, family = theme.mono)

/** A small label in the theme's label face (caps with tracking where the theme sets them so). */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, size: TextUnit = 12.sp, color: Color = theme.dim, weight: FontWeight = FontWeight.SemiBold, tracking: TextUnit = 1.4.sp) {
    val t = theme
    val oneWeight = t.label == com.drxwnify.desktop.ui.theme.Fonts.russo || t.label == com.drxwnify.desktop.ui.theme.Fonts.forum
    Txt(if (t.labelUpper) text.uppercase() else text, modifier, size = size, color = color, family = t.label, weight = if (oneWeight) FontWeight.Normal else weight,
        letterSpacing = if (t.labelUpper) tracking else TextUnit.Unspecified)
}

@Composable
fun Ico(glyph: String, modifier: Modifier = Modifier, size: TextUnit = 18.sp, color: Color = theme.dim) {
    BasicText(glyph, modifier, style = TextStyle(fontFamily = Fonts.icons, fontSize = size, color = color, lineHeight = size))
}

fun Modifier.hand() = pointerHoverIcon(PointerIcon.Hand)

@Composable
fun Modifier.clickHand(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    return this.hand().clickable(interactionSource = src, indication = null, onClick = onClick)
}

@Composable
fun rememberHover(): Pair<MutableInteractionSource, Boolean> {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    return src to hovered
}

/** Round icon button with an animated hover fill. */
@Composable
fun IconBtn(
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: TextUnit = 18.sp,
    tint: Color = theme.dim,
    active: Boolean = false,
    dot: Boolean = false,
    tooltip: String? = null,
    filled: Boolean = false,
) {
    val t = theme
    val (src, hovered) = rememberHover()
    val bg by animateColorAsState(
        when { filled -> t.fill; hovered -> t.fillHover; else -> Color.Transparent }, tween(160),
    )
    val fg by animateColorAsState(if (active) t.accent else if (hovered) t.text else tint, tween(160))
    val content = @Composable {
        val shape = if (t.look == Look.Terminal) RectangleShape else CircleShape
        Box(
            modifier.size(size).clip(shape).background(bg)
                .hoverable(src).hand().clickable(interactionSource = src, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Ico(glyph, size = iconSize, color = fg)
            if (dot) Box(
                Modifier.align(Alignment.TopEnd).offset((-size.value * 0.18f).dp, (size.value * 0.18f).dp)
                    .size(8.dp).clip(CircleShape).background(t.bg).padding(1.5.dp).clip(CircleShape).background(t.ok),
            )
        }
    }
    if (tooltip != null) Tooltip(tooltip) { content() } else content()
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Tooltip(text: String, content: @Composable () -> Unit) {
    val t = theme
    TooltipArea(
        tooltip = {
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(t.menu)
                    .border(1.dp, t.panelBorder, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
            ) { Txt(text, size = 12.sp, color = t.text, weight = FontWeight.SemiBold) }
        },
        delayMillis = 450,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 18.dp)),
    ) { content() }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = theme
    val (src, hovered) = rememberHover()
    val outlined = t.look == Look.Atlas || t.look == Look.Neon || t.look == Look.Terminal || t.look == Look.Rite
    val idle = if (outlined) Color.Transparent else t.fill
    val bg by animateColorAsState(if (selected) t.accent else if (hovered) t.fillHover else idle, tween(180))
    val fg by animateColorAsState(if (selected) t.onAccent else t.dim, tween(180))
    val shape = RoundedCornerShape(t.chipRadius)
    Box(
        modifier.clip(shape).background(bg).then(if (outlined && !selected) Modifier.border(1.dp, t.panelBorder, shape) else Modifier)
            .hoverable(src).hand()
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = if (t.labelUpper) 7.dp else 11.dp, vertical = 7.dp),
    ) { Label(text, size = if (t.labelUpper) 10.5.sp else 12.sp, color = fg, tracking = 0.6.sp) }
}

/**
 * Panels, each theme its own way: frosted glass (with a neon glow in Ретровейв, as floating
 * islands in Глубина), a tiled window with a title bar (Gruvbox), stitched fabric (Cozy), a gold
 * frame with corner ornaments (Обсерватория). [title] shows only where windows have titles.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    radius: Dp = theme.panelRadius,
    title: String? = null,
    focused: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val t = theme
    val shape = RoundedCornerShape(radius)
    val haze = LocalHaze.current
    var m = when (t.look) {
        Look.Neon -> modifier.shadow(20.dp, shape, ambientColor = t.accent.copy(alpha = 0.3f), spotColor = t.accent.copy(alpha = 0.4f))
        Look.Island -> modifier.shadow(28.dp, shape, ambientColor = Color.Black.copy(alpha = if (t.night) 0.6f else 0.2f), spotColor = Color.Black.copy(alpha = if (t.night) 0.6f else 0.2f))
        Look.Quilt -> modifier.shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
        else -> modifier
    }.clip(shape)
    m = if (t.glass && haze != null) {
        m.hazeBlur(
            HazeInput.Sources(haze),
            HazeBlurStyle {
                blurRadius(t.blur)
                backgroundColor(t.bg)
                noiseFactor(0.06f)
                colorEffects(listOf(HazeColorEffect.tint(t.bg.copy(alpha = 0.58f)), HazeColorEffect.tint(t.panel)))
            },
            dev.chrisbanes.haze.HazePerformanceMode.Balanced,
        )
    } else m.background(t.panel)
    if (t.look != Look.Quilt) m = m.border(if (t.look == Look.Terminal) 2.dp else 1.dp, if (t.look == Look.Terminal && focused) t.accent else t.panelBorder, shape)
    Box(
        m.drawBehind {
            when (t.look) {
                Look.Quilt -> {
                    // the stitch, a little inside the edge
                    val inset = 7.dp.toPx()
                    drawRoundRect(
                        t.panelBorder, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2),
                        CornerRadius((radius.toPx() - inset).coerceAtLeast(0f)),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))),
                    )
                }
                Look.Atlas -> {
                    // gold corner ornaments, like the frame of an old map
                    val i = 5.dp.toPx(); val l = 12.dp.toPx(); val sw = 1.5.dp.toPx(); val c = t.accent2
                    val w = size.width; val h = size.height
                    drawLine(c, Offset(i, i), Offset(i + l, i), sw); drawLine(c, Offset(i, i), Offset(i, i + l), sw)
                    drawLine(c, Offset(w - i, i), Offset(w - i - l, i), sw); drawLine(c, Offset(w - i, i), Offset(w - i, i + l), sw)
                    drawLine(c, Offset(i, h - i), Offset(i + l, h - i), sw); drawLine(c, Offset(i, h - i), Offset(i, h - i - l), sw)
                    drawLine(c, Offset(w - i, h - i), Offset(w - i - l, h - i), sw); drawLine(c, Offset(w - i, h - i), Offset(w - i, h - i - l), sw)
                }
                Look.Terminal -> Unit
                // Satanic: a short red rule in the top-left corner, like a mark on a sleeve
                Look.Rite -> drawRect(t.accent, Offset(0f, 0f), Size(28.dp.toPx(), 2.dp.toPx()))
                else -> drawLine(
                    // A thin light along the top edge: the glass catching light.
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = if (t.night) 0.10f else 0.3f), Color.Transparent)),
                    Offset(size.width * 0.08f, 0.6f), Offset(size.width * 0.92f, 0.6f), 1f,
                )
            }
        },
    ) {
        if (t.look == Look.Terminal && title != null) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().height(24.dp).background(if (focused) t.accent else t.fill).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Txt("─ $title ─", size = 12.sp, weight = if (focused) FontWeight.Bold else FontWeight.Normal, color = if (focused) t.onAccent else t.dim) }
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            }
        } else content()
    }
}

private fun Color.compositeOverBg(bg: Color): Color =
    Color(red * alpha + bg.red * (1 - alpha), green * alpha + bg.green * (1 - alpha), blue * alpha + bg.blue * (1 - alpha), 0.55f)

/** Album art with a soft fade-in; a quiet gradient until it arrives. */
@Composable
fun Cover(url: String?, modifier: Modifier = Modifier, radius: Dp = theme.coverRadius, shape: Shape = RoundedCornerShape(radius)) {
    val t = theme
    val bmp by produceState<ImageBitmap?>(url?.let { Images.cached(it) }, url) {
        // The same slot shows another track's cover (a list scrolled, the queue moved on): start from it.
        value = url?.let { Images.cached(it) }
        if (url != null && value == null) value = Images.load(url)
    }
    val alpha by animateFloatAsState(if (bmp != null) 1f else 0f, tween(260))
    Box(
        modifier.clip(shape).background(Brush.linearGradient(listOf(t.fillHover, t.fill))),
    ) {
        bmp?.let {
            Image(it, null, Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }, contentScale = ContentScale.Crop)
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    val t = theme
    Box(modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(t.panelBorder.copy(alpha = 0.9f), Color.Transparent))))
}

@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val t = theme
    val x by animateDpAsState(if (checked) 19.dp else 3.dp, tween(180))
    val bg by animateColorAsState(if (checked) t.accent else t.fillHover, tween(180))
    val knob by animateColorAsState(if (checked) t.onAccent else t.dim, tween(180))
    Box(
        Modifier.width(40.dp).height(22.dp).clip(RoundedCornerShape(99.dp)).background(bg).hand()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onChange(!checked) },
    ) { Box(Modifier.offset(x, 3.dp).size(16.dp).clip(CircleShape).background(knob)) }
}

/** A thin bar you can click or drag: progress and volume. */
/**
 * A slider that follows the mouse from the first press (no drag threshold). [live]: [onSeek] on every
 * move (volume); otherwise only on release (track position), with [onPreview] meanwhile so the time
 * next to it can show where the drop will land.
 */
@Composable
fun SeekBar(
    value: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = theme.accent,
    track: Color = theme.fillHover,
    onRelease: ((Float) -> Unit)? = null,
    live: Boolean = false,
    onPreview: ((Float?) -> Unit)? = null,
    /** The track's progress bar: the theme's own knob (petal, star…) and fill. */
    ornate: Boolean = false,
) {
    var w by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    // Just released: hold the new spot until the player reports it, so the bar doesn't jump back.
    var landed by remember { mutableStateOf<Float?>(null) }
    var landedAt by remember { mutableStateOf(0L) }
    // Let go once the player reports the new spot, but not in the first 300 ms: a late report of the old one can't pull the knob back.
    LaunchedEffect(value, landed) {
        val l = landed ?: return@LaunchedEffect
        if (abs(value - l) < 0.02f) { (300 - (System.currentTimeMillis() - landedAt)).takeIf { it > 0 }?.let { delay(it) }; landed = null }
    }
    LaunchedEffect(landed) { if (landed != null) { delay(1500); landed = null } }
    val (src, hovered) = rememberHover()
    val h by animateDpAsState(if (hovered || dragging != null) 5.dp else 3.dp, tween(140))
    val shown = (dragging ?: landed ?: value).coerceIn(0f, 1f)
    val t = theme
    val seek by rememberUpdatedState(onSeek)
    val release by rememberUpdatedState(onRelease)
    val preview by rememberUpdatedState(onPreview)
    val isLive by rememberUpdatedState(live)
    Box(
        modifier.height(16.dp).onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }.hoverable(src).hand()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    var v = (down.position.x / w).coerceIn(0f, 1f)
                    dragging = v; preview?.invoke(v); if (isLive) seek(v)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        v = (change.position.x / w).coerceIn(0f, 1f)
                        dragging = v; preview?.invoke(v); if (isLive) seek(v)
                    }
                    if (!isLive) seek(v)
                    release?.invoke(v)
                    landed = if (isLive) null else v
                    landedAt = System.currentTimeMillis()
                    dragging = null; preview?.invoke(null)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val active = hovered || dragging != null
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            val cy = size.height / 2
            val th = h.toPx()
            val r = if (t.look == Look.Terminal) 0f else th / 2
            val kx = size.width * shown
            if (t.look == Look.Quilt && ornate) {
                drawLine(track, Offset(0f, cy), Offset(size.width, cy), th * 0.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))
            } else drawRoundRect(track, Offset(0f, cy - th / 2), Size(size.width, th), CornerRadius(r))
            val fill: Brush = when {
                !ornate -> SolidColor(color)
                t.look == Look.Neon -> Brush.horizontalGradient(listOf(t.accent, t.accent2), 0f, kx.coerceAtLeast(1f))
                t.look == Look.Island || t.look == Look.Blossom -> Brush.horizontalGradient(listOf(t.accent2, t.accent), 0f, kx.coerceAtLeast(1f))
                else -> SolidColor(color)
            }
            if (ornate && t.look == Look.Neon) drawRoundRect(t.accent.copy(alpha = 0.25f), Offset(0f, cy - th * 1.5f), Size(kx, th * 3), CornerRadius(th * 1.5f))
            drawRoundRect(fill, Offset(0f, cy - th / 2), Size(kx, th), CornerRadius(r))
            when {
                ornate && t.look == Look.Blossom -> rotate(35f, Offset(kx, cy)) {
                    drawOval(t.accent, Offset(kx - 4.5.dp.toPx(), cy - 7.dp.toPx()), Size(9.dp.toPx(), 14.dp.toPx()))
                }
                ornate && t.look == Look.Atlas -> {
                    val o = 7.dp.toPx(); val i = 2.dp.toPx()
                    val star = Path().apply {
                        moveTo(kx, cy - o); lineTo(kx + i, cy - i); lineTo(kx + o, cy); lineTo(kx + i, cy + i)
                        lineTo(kx, cy + o); lineTo(kx - i, cy + i); lineTo(kx - o, cy); lineTo(kx - i, cy - i); close()
                    }
                    drawPath(star, t.accent)
                }
                ornate && t.look == Look.Quilt -> {
                    drawCircle(t.panel, 8.dp.toPx(), Offset(kx, cy))
                    drawCircle(t.accent2, 6.dp.toPx(), Offset(kx, cy))
                }
                active && t.look == Look.Terminal -> drawRect(t.text, Offset(kx - 2.dp.toPx(), cy - 6.dp.toPx()), Size(4.dp.toPx(), 12.dp.toPx()))
                ornate && t.look == Look.Rite -> rotate(45f, Offset(kx, cy)) {
                    val d = 9.dp.toPx()
                    drawRect(t.accent, Offset(kx - d / 2, cy - d / 2), Size(d, d))
                }
                active -> drawCircle(if (ornate && t.look == Look.Neon) Color.White else t.text, 6.dp.toPx(), Offset(kx, cy))
            }
        }
    }
}

/** Text that leads somewhere: underlined under the mouse (Spotify's links), with a hand cursor. */
@Composable
fun LinkText(
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = theme.text,
    family: FontFamily = theme.body,
    maxLines: Int = 1,
) {
    if (onClick == null) { Txt(text, modifier, size = size, weight = weight, color = color, family = family, maxLines = maxLines); return }
    Links(listOf(text to onClick), modifier, size, weight, color, family, maxLines)
}

/** Several links in one line ("Skeler, Devilish Trio"): each its own target, the line cut with "…" as one text. */
@Composable
fun Links(
    parts: List<Pair<String, (() -> Unit)?>>,
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = theme.text,
    family: FontFamily = theme.body,
    maxLines: Int = 1,
    separator: String = ", ",
) {
    val t = theme
    val hover = androidx.compose.ui.text.TextLinkStyles(
        hoveredStyle = androidx.compose.ui.text.SpanStyle(color = t.text, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
    )
    val text = androidx.compose.ui.text.buildAnnotatedString {
        parts.forEachIndexed { i, (label, go) ->
            if (i > 0) append(separator)
            if (go == null) append(label)
            else withLink(androidx.compose.ui.text.LinkAnnotation.Clickable("l$i", hover) { go() }) { append(label) }
        }
    }
    BasicText(
        text, modifier,
        style = TextStyle(fontSize = size, fontWeight = weight, color = color, fontFamily = family),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

/** A track's (or album's) artists, each a link to its own page. */
@Composable
fun ArtistLinks(artists: List<com.drxwnify.desktop.model.ArtistRef>, modifier: Modifier = Modifier, size: TextUnit = 13.sp, color: Color = theme.dim, family: FontFamily = theme.body) =
    Links(artists.map { a -> a.name to a.id?.let { id -> { Nav.go(Screen.Artist(id)) } } }, modifier, size = size, color = color, family = family)

/** A row that lights up under the mouse. */
@Composable
fun HoverRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    radius: Dp = 10.dp,
    /** Overrides [radius]: picked rows next to each other join into one block without notches. */
    shape: Shape? = null,
    onDoubleClick: (() -> Unit)? = null,
    onRightClick: ((Offset) -> Unit)? = null,
    content: @Composable RowScope.(hovered: Boolean) -> Unit,
) {
    val t = theme
    val (src, hovered) = rememberHover()
    val bg by animateColorAsState(if (active) t.fill else if (hovered) t.fill.copy(alpha = t.fill.alpha * 0.8f) else Color.Transparent, tween(140))
    Row(
        modifier.clip(shape ?: RoundedCornerShape(radius)).background(bg).hoverable(src).hand()
            .pointerInput(onClick, onDoubleClick) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = onDoubleClick?.let { { _: Offset -> it() } },
                )
            }
            .then(if (onRightClick != null) Modifier.onRightClick(onRightClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) { content(hovered) }
}

fun Modifier.onRightClick(action: (Offset) -> Unit): Modifier = pointerInput(action) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent()
            if (e.type == androidx.compose.ui.input.pointer.PointerEventType.Press && e.buttons.isSecondaryPressed) {
                action(e.changes.first().position)
            }
        }
    }
}

@Composable
fun PillButton(text: String, glyph: String? = null, primary: Boolean = false, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = theme
    val (src, hovered) = rememberHover()
    val outlined = !primary && (t.look == Look.Atlas || t.look == Look.Neon || t.look == Look.Rite)
    val bg by animateColorAsState(if (primary) (if (hovered) t.accent.copy(alpha = 0.88f) else t.accent) else if (hovered) t.fillHover else if (outlined) Color.Transparent else t.fill, tween(150))
    val scale by animateFloatAsState(if (hovered && t.look != Look.Terminal) 1.03f else 1f, tween(150))
    val shape = RoundedCornerShape(t.chipRadius)
    Row(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape).background(bg)
            .then(if (outlined || (t.look == Look.Terminal && !primary)) Modifier.border(1.dp, t.panelBorder, shape) else Modifier)
            .hoverable(src).hand().clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) { Ico(glyph, size = 16.sp, color = if (primary) t.onAccent else t.dim); androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp)) }
        Label(text, size = if (t.labelUpper) 12.sp else 13.sp, weight = FontWeight.Bold, color = if (primary) t.onAccent else t.text)
    }
}

/** Big round play button with a gentle hover lift. */
@Composable
fun PlayButton(playing: Boolean, onClick: () -> Unit, size: Dp = 52.dp, loading: Boolean = false) {
    val t = theme
    val (src, hovered) = rememberHover()
    val scale by animateFloatAsState(if (hovered) 1.06f else 1f, tween(160))
    val shape = when (t.look) {
        Look.Terminal -> RectangleShape
        Look.Quilt -> RoundedCornerShape(size * 0.34f)
        Look.Rite -> RoundedCornerShape(2.dp)
        else -> CircleShape
    }
    val fill: Brush = when (t.look) {
        Look.Neon -> Brush.linearGradient(listOf(t.accent, Color(0xFFFF9A3D)))
        Look.Island, Look.Atlas -> Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f).compositeOver(t.accent), t.accent, t.accent2))
        Look.Blossom -> Brush.linearGradient(listOf(t.accent, t.accent2))
        else -> SolidColor(t.accent)
    }
    val glow = when (t.look) {
        Look.Neon -> Modifier.shadow(16.dp, shape, ambientColor = t.accent, spotColor = t.accent)
        Look.Island, Look.Blossom -> Modifier.shadow(12.dp, shape, ambientColor = t.accent.copy(alpha = 0.5f), spotColor = t.accent.copy(alpha = 0.5f))
        else -> Modifier
    }
    Box(
        Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }.then(glow).clip(shape).background(fill)
            .then(if (t.look == Look.Atlas) Modifier.border(1.dp, t.text.copy(alpha = 0.4f), shape) else Modifier)
            .hoverable(src).hand().clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Looking for the stream / opening it: a small spinner takes the icon's place (a ring round a square button read as a smile).
        Crossfade(if (loading) 2 else if (playing) 1 else 0, animationSpec = tween(160)) { k ->
            if (k == 2) {
                val spin by androidx.compose.animation.core.rememberInfiniteTransition().animateFloat(
                    0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.LinearEasing)),
                )
                androidx.compose.foundation.Canvas(Modifier.size(size * 0.4f)) {
                    val sw = 2.5.dp.toPx()
                    drawArc(t.onAccent, spin, 260f, false, Offset(sw / 2, sw / 2), Size(this.size.width - sw, this.size.height - sw),
                        style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
            } else Ico(if (k == 1) Ti.playerPauseFilled else Ti.playerPlayFilled, size = (size.value * 0.42f).sp, color = t.onAccent)
        }
    }
}

@Composable
fun Spacer(w: Dp = 0.dp, h: Dp = 0.dp) = androidx.compose.foundation.layout.Spacer(Modifier.width(w).height(h))

@Composable
fun Hanko(text: String = "高\n音", size: Dp = 26.dp) {
    val t = theme
    Box(
        Modifier.size(size).graphicsLayer { rotationZ = -6f }.border(1.5.dp, t.accent2, RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center,
    ) { Txt(text, size = (size.value * 0.36f).sp, color = t.accent2, family = Fonts.jpSerif, weight = FontWeight.SemiBold, maxLines = 2, lineHeight = (size.value * 0.38f).sp) }
}

@Composable
fun ProvideTheme(theme: DrxTheme, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalTheme provides theme, content = content)

@Composable
fun Dot(color: Color, size: Dp = 7.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

@Composable
fun EqBars(color: Color = theme.accent) {
    val tr = androidx.compose.animation.core.rememberInfiniteTransition()
    val a by tr.animateFloat(0.2f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(420), androidx.compose.animation.core.RepeatMode.Reverse))
    val b by tr.animateFloat(1f, 0.25f, androidx.compose.animation.core.infiniteRepeatable(tween(520), androidx.compose.animation.core.RepeatMode.Reverse))
    val c by tr.animateFloat(0.4f, 0.9f, androidx.compose.animation.core.infiniteRepeatable(tween(360), androidx.compose.animation.core.RepeatMode.Reverse))
    Row(Modifier.height(12.dp), verticalAlignment = Alignment.Bottom) {
        listOf(a, b, c).forEach { v ->
            Box(Modifier.width(2.5.dp).fillMaxHeight(v).clip(RoundedCornerShape(1.dp)).background(color))
            Spacer(w = 2.dp)
        }
    }
}

fun Modifier.roundedFill(color: Color, radius: Dp) = drawBehind { drawRoundRect(color, cornerRadius = CornerRadius(radius.toPx())) }
