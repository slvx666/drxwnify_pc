package com.drxwnify.desktop.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Backdrop { Tategaki, Gruvbox, Retrowave, Depth, Sakura, Cozy, Observatory, Ash, Mono }

/**
 * How a theme draws its parts — not just colours: panels, buttons, the play button, covers on the
 * home page, the art in «Сейчас играет», section titles, the top bar.
 */
enum class Look {
    /** Татэгаки: frosted glass, soft corners. */
    Glass,
    /** Gruvbox: tiled windows with title bars, polybar on top, square everything. */
    Terminal,
    /** Ретровейв: glass with neon edges and glow, chrome titles, a tape-deck display. */
    Neon,
    /** Глубина: floating islands with big corners, a capsule player, the cover's reflection. */
    Island,
    /** Сакура: dark glass in pink, serif titles with small Japanese words, petal knobs. */
    Blossom,
    /** Cozy: stitched fabric panels, polaroids on tape, handwritten notes. */
    Quilt,
    /** Обсерватория: an old star atlas — gold frames, planets on orbits, an astrolabe. */
    Atlas,
    /** Satanic: black, bone white, dried-blood red; sharp corners, mounted covers, spaced serif capitals. */
    Rite,
}

@Immutable
data class DrxTheme(
    val id: String,
    val name: String,
    val note: String,
    val look: Look,
    val night: Boolean,
    /** The day version: the night one a little lighter (dark all the same, like Telegram's), not a light theme. */
    val dusk: Boolean = false,
    val bg: Color,
    val bg2: Color,
    /** Panel fill over the blurred background (or the panel's own colour without glass). */
    val panel: Color,
    val panelBorder: Color,
    /** Fill of things inside panels: chips, inputs, hover. */
    val fill: Color,
    val fillHover: Color,
    /** Opaque surface for menus, tooltips, dialogs and toasts. */
    val menu: Color,
    val text: Color,
    val dim: Color,
    val mute: Color,
    val accent: Color,
    val accent2: Color,
    val onAccent: Color,
    val ok: Color,
    val glass: Boolean,
    val blur: Dp,
    val panelRadius: Dp,
    val coverRadius: Dp,
    val chipRadius: Dp,
    val body: FontFamily,
    val heading: FontFamily,
    val headingWeight: FontWeight,
    val headingUpper: Boolean = false,
    /** Letter spacing of headings, sp. */
    val headingTracking: Float = -0.4f,
    val mono: FontFamily,
    /** Small labels (chips, captions): some looks set them in caps with their own face. */
    val label: FontFamily,
    val labelUpper: Boolean = false,
    /** Decorative face: Japanese words, handwriting, italic notes. */
    val decor: FontFamily,
    val backdrop: Backdrop,
    /** Mist blobs behind everything (3 colours). */
    val mist: List<Color>,
    val glyph: Color,
    val glyphAccent: Color,
    /** Colours for the theme card and for coloured blocks (Gruvbox's polybar modules). */
    val sample: List<Color>,
)

/** A theme in its night and day versions; the switch in the settings picks one. */
data class ThemeSpec(val id: String, val name: String, val note: String, val night: DrxTheme, val day: DrxTheme) {
    fun of(day: Boolean) = if (day) this.day else night
}

object Themes {
    private val tategakiNight = DrxTheme(
        id = "tategaki", name = "Татэгаки", note = "Основная: тихий ночной фон, плывущие знаки, стекло", look = Look.Glass, night = true,
        bg = Color(0xFF09080A), bg2 = Color(0xFF110D10),
        panel = Color(0x0FFFFFFF), panelBorder = Color(0x14FFFFFF), fill = Color(0x0DFFFFFF), fillHover = Color(0x17FFFFFF), menu = Color(0xF5181418),
        text = Color(0xFFF3EAED), dim = Color(0xFFAB9FA5), mute = Color(0xFF8E8389),
        accent = Color(0xFFF4A7BB), accent2 = Color(0xFFE0566F), onAccent = Color(0xFF2A1220), ok = Color(0xFF7FD6A0),
        glass = true, blur = 28.dp, panelRadius = 20.dp, coverRadius = 12.dp, chipRadius = 99.dp,
        body = Fonts.manrope, heading = Fonts.manrope, headingWeight = FontWeight.ExtraBold, mono = Fonts.mono, label = Fonts.manrope, decor = Fonts.jpSerif,
        backdrop = Backdrop.Tategaki,
        mist = listOf(Color(0xFF3D1428), Color(0xFF2A1530), Color(0xFF3A2219)),
        glyph = Color(0xFFC8C0C4), glyphAccent = Color(0xFFF4A7BB),
        sample = listOf(Color(0xFFF4A7BB), Color(0xFFE0566F), Color(0xFF3D1428)),
    )
    private val tategakiDay = tategakiNight.copy(
        dusk = true,
        bg = Color(0xFF1D181B), bg2 = Color(0xFF262024),
        panel = Color(0x14FFFFFF), panelBorder = Color(0x1CFFFFFF), fill = Color(0x12FFFFFF), fillHover = Color(0x1FFFFFFF), menu = Color(0xF52A2328),
        text = Color(0xFFF5ECEF), dim = Color(0xFFB8ACB2), mute = Color(0xFF9A8E94),
        mist = listOf(Color(0xFF552038), Color(0xFF3C2245), Color(0xFF4E3022)),
    )

    private val gruvboxNight = DrxTheme(
        id = "gruvbox", name = "Gruvbox", note = "Linux: окна-плитки, polybar, ncmpcpp, моноширинный шрифт", look = Look.Terminal, night = true,
        bg = Color(0xFF1D2021), bg2 = Color(0xFF1D2021),
        panel = Color(0xFF282828), panelBorder = Color(0xFF504945), fill = Color(0xFF3C3836), fillHover = Color(0xFF504945), menu = Color(0xFF282828),
        text = Color(0xFFEBDBB2), dim = Color(0xFFA89984), mute = Color(0xFF928374),
        accent = Color(0xFFFABD2F), accent2 = Color(0xFFFE8019), onAccent = Color(0xFF282828), ok = Color(0xFFB8BB26),
        glass = false, blur = 0.dp, panelRadius = 0.dp, coverRadius = 0.dp, chipRadius = 0.dp,
        body = Fonts.mono, heading = Fonts.mono, headingWeight = FontWeight.Bold, headingTracking = 0f, mono = Fonts.mono, label = Fonts.mono, decor = Fonts.mono,
        backdrop = Backdrop.Gruvbox,
        mist = List(3) { Color.Transparent },
        glyph = Color(0xFF504945), glyphAccent = Color(0xFFFE8019),
        // polybar modules: green, aqua, blue, orange, purple; then the red of hearts
        sample = listOf(Color(0xFFB8BB26), Color(0xFF8EC07C), Color(0xFF83A598), Color(0xFFFE8019), Color(0xFFD3869B), Color(0xFFFB4934)),
    )
    // gruvbox's own "dark soft" palette
    private val gruvboxDay = gruvboxNight.copy(
        dusk = true,
        bg = Color(0xFF32302F), bg2 = Color(0xFF32302F),
        panel = Color(0xFF3C3836), panelBorder = Color(0xFF665C54), fill = Color(0xFF504945), fillHover = Color(0xFF665C54), menu = Color(0xFF3C3836),
        text = Color(0xFFEBDBB2), dim = Color(0xFFBDAE93), mute = Color(0xFFA89984),
        glyph = Color(0xFF665C54),
    )

    private val retroNight = DrxTheme(
        id = "retrowave", name = "Ретровейв", note = "Закат 80-х: солнце, бегущая сетка, неон и хром", look = Look.Neon, night = true,
        bg = Color(0xFF0B0217), bg2 = Color(0xFF14062A),
        panel = Color(0x8C12042A), panelBorder = Color(0x55FF2A6D), fill = Color(0x1AFFFFFF), fillHover = Color(0x26FFFFFF), menu = Color(0xF7140628),
        text = Color(0xFFF4E9FF), dim = Color(0xFFCDB8EA), mute = Color(0xFFA991CC),
        accent = Color(0xFFFF2A6D), accent2 = Color(0xFF05D9E8), onAccent = Color(0xFFFFFFFF), ok = Color(0xFF05D9E8),
        glass = true, blur = 22.dp, panelRadius = 6.dp, coverRadius = 4.dp, chipRadius = 4.dp,
        body = Fonts.manrope, heading = Fonts.russo, headingWeight = FontWeight.Normal, headingUpper = true, headingTracking = 1.5f,
        mono = Fonts.vt323, label = Fonts.russo, labelUpper = true, decor = Fonts.vt323,
        backdrop = Backdrop.Retrowave,
        mist = listOf(Color(0xFF2A0845), Color(0xFF5C0F5E), Color(0xFF12031F)),
        glyph = Color(0xFFFF2A6D), glyphAccent = Color(0xFF05D9E8),
        sample = listOf(Color(0xFFFF2A6D), Color(0xFF05D9E8), Color(0xFFFFE56B)),
    )
    private val retroDay = retroNight.copy(
        dusk = true,
        bg = Color(0xFF1E0B33), bg2 = Color(0xFF2A1048),
        panel = Color(0x8C24104A), panelBorder = Color(0x66FF2A6D), fill = Color(0x1FFFFFFF), fillHover = Color(0x2BFFFFFF), menu = Color(0xF7261040),
        mist = listOf(Color(0xFF451070), Color(0xFF7A1A70), Color(0xFF241040)),
    )

    private val depthNight = DrxTheme(
        id = "depth", name = "Глубина", note = "Под водой: лучи сверху, пузырьки, панели-острова", look = Look.Island, night = true,
        bg = Color(0xFF021219), bg2 = Color(0xFF06303F),
        panel = Color(0x0FFFFFFF), panelBorder = Color(0x1AFFFFFF), fill = Color(0x10FFFFFF), fillHover = Color(0x1CFFFFFF), menu = Color(0xF5062A36),
        text = Color(0xFFDFF6F5), dim = Color(0xFF9CC3C8), mute = Color(0xFF7FA6AD),
        accent = Color(0xFF7FF0E6), accent2 = Color(0xFF3FB8C8), onAccent = Color(0xFF02202A), ok = Color(0xFF7FF0E6),
        glass = true, blur = 30.dp, panelRadius = 28.dp, coverRadius = 18.dp, chipRadius = 99.dp,
        body = Fonts.manrope, heading = Fonts.manrope, headingWeight = FontWeight.Bold, mono = Fonts.mono, label = Fonts.manrope, decor = Fonts.manrope,
        backdrop = Backdrop.Depth,
        mist = listOf(Color(0xFF0C4A5A), Color(0xFF06303F), Color(0xFF0A2533)),
        glyph = Color(0xFFAAF6EE), glyphAccent = Color(0xFF7FF0E6),
        sample = listOf(Color(0xFF7FF0E6), Color(0xFF3FB8C8), Color(0xFF06303F)),
    )
    private val depthDay = depthNight.copy(
        dusk = true,
        bg = Color(0xFF0B2E3A), bg2 = Color(0xFF12495A),
        panel = Color(0x14FFFFFF), panelBorder = Color(0x22FFFFFF), fill = Color(0x14FFFFFF), fillHover = Color(0x22FFFFFF), menu = Color(0xF50E3F4D),
        mist = listOf(Color(0xFF16677A), Color(0xFF0E4A5C), Color(0xFF123848)),
    )

    private val sakuraNight = DrxTheme(
        id = "sakura", name = "Сакура", note = "Ночная сакура: луна, ветка, лепестки кружат на фоне", look = Look.Blossom, night = true,
        bg = Color(0xFF130E1C), bg2 = Color(0xFF0E0A13),
        panel = Color(0x9E140D1A), panelBorder = Color(0x26F2AFC2), fill = Color(0x14F2AFC2), fillHover = Color(0x24F2AFC2), menu = Color(0xF71B1222),
        text = Color(0xFFF4E8EE), dim = Color(0xFFB9A7B3), mute = Color(0xFF948491),
        accent = Color(0xFFF2AFC2), accent2 = Color(0xFFD97A9C), onAccent = Color(0xFF3A1626), ok = Color(0xFF8FD6A8),
        glass = true, blur = 24.dp, panelRadius = 18.dp, coverRadius = 14.dp, chipRadius = 99.dp,
        body = Fonts.manrope, heading = Fonts.cormorant, headingWeight = FontWeight.Bold, headingTracking = -0.2f, mono = Fonts.mono, label = Fonts.manrope, decor = Fonts.jpSerif,
        backdrop = Backdrop.Sakura,
        mist = listOf(Color(0xFF3A1A2E), Color(0xFF2A1A3A), Color(0xFF4A1E36)),
        glyph = Color(0xFFE9A9BD), glyphAccent = Color(0xFFF3C6D3),
        sample = listOf(Color(0xFFF2AFC2), Color(0xFFC9CFEA), Color(0xFFB04A6F)),
    )
    private val sakuraDay = sakuraNight.copy(
        dusk = true,
        bg = Color(0xFF2A1F33), bg2 = Color(0xFF22192A),
        panel = Color(0x9E2A1D33), panelBorder = Color(0x33F2AFC2), fill = Color(0x1AF2AFC2), fillHover = Color(0x2BF2AFC2), menu = Color(0xF7322540),
        mist = listOf(Color(0xFF5A2A48), Color(0xFF40305A), Color(0xFF6A2E50)),
    )

    private val cozyNight = DrxTheme(
        id = "cozy", name = "Cozy", note = "Тёплый вечер: гирлянда, вязаные швы, полароиды", look = Look.Quilt, night = true,
        bg = Color(0xFF1C1410), bg2 = Color(0xFF241A15),
        panel = Color(0xFF2A1F19), panelBorder = Color(0x47F2B36B), fill = Color(0xFF3A2C23), fillHover = Color(0xFF46362B), menu = Color(0xFF2E2219),
        text = Color(0xFFF5EBDD), dim = Color(0xFFC9B6A3), mute = Color(0xFFAA9583),
        accent = Color(0xFFF2B36B), accent2 = Color(0xFFD9825B), onAccent = Color(0xFF2A1F19), ok = Color(0xFFA8C3A0),
        glass = false, blur = 0.dp, panelRadius = 24.dp, coverRadius = 16.dp, chipRadius = 12.dp,
        body = Fonts.nunito, heading = Fonts.comfortaa, headingWeight = FontWeight.Bold, headingTracking = 0f, mono = Fonts.nunito, label = Fonts.nunito, decor = Fonts.caveat,
        backdrop = Backdrop.Cozy,
        mist = listOf(Color(0xFF6B4320), Color(0xFF5A2E1C), Color(0xFF3A2A1C)),
        glyph = Color(0xFFFFD08A), glyphAccent = Color(0xFFF2B36B),
        sample = listOf(Color(0xFFF2B36B), Color(0xFFD9825B), Color(0xFFA8C3A0)),
    )
    private val cozyDay = cozyNight.copy(
        dusk = true,
        bg = Color(0xFF33261E), bg2 = Color(0xFF3C2D24),
        panel = Color(0xFF3F2F26), panelBorder = Color(0x55F2B36B), fill = Color(0xFF4E3C30), fillHover = Color(0xFF5A4637), menu = Color(0xFF45352B),
        mist = listOf(Color(0xFF8A5A2C), Color(0xFF6E3C26), Color(0xFF4E3826)),
    )

    private val atlasNight = DrxTheme(
        id = "observatory", name = "Обсерватория", note = "Старинный звёздный атлас: золото, планеты, астролябия", look = Look.Atlas, night = true,
        bg = Color(0xFF0E1834), bg2 = Color(0xFF0A1022),
        panel = Color(0xC70A1022), panelBorder = Color(0x52C9A86A), fill = Color(0x14C9A86A), fillHover = Color(0x26C9A86A), menu = Color(0xFA0C1430),
        text = Color(0xFFEDE4D3), dim = Color(0xFFBDB29D), mute = Color(0xFF9A927F),
        accent = Color(0xFFD9BC82), accent2 = Color(0xFFC9A86A), onAccent = Color(0xFF0B1226), ok = Color(0xFF9FD3A8),
        glass = true, blur = 16.dp, panelRadius = 4.dp, coverRadius = 2.dp, chipRadius = 99.dp,
        body = Fonts.spectral, heading = Fonts.forum, headingWeight = FontWeight.Normal, headingUpper = true, headingTracking = 4f,
        mono = Fonts.spectral, label = Fonts.forum, labelUpper = true, decor = Fonts.spectral,
        backdrop = Backdrop.Observatory,
        mist = listOf(Color(0xFF1B2C5C), Color(0xFF14204A), Color(0xFF0E1834)),
        glyph = Color(0xFFEDE4D3), glyphAccent = Color(0xFFF0DDB0),
        sample = listOf(Color(0xFFD9BC82), Color(0xFF7F95C2), Color(0xFF0E1834)),
    )
    private val atlasDay = atlasNight.copy(
        dusk = true,
        bg = Color(0xFF1C2A55), bg2 = Color(0xFF152044),
        panel = Color(0xC7152044), panelBorder = Color(0x66C9A86A), fill = Color(0x1AC9A86A), fillHover = Color(0x2BC9A86A), menu = Color(0xFA1A2850),
        mist = listOf(Color(0xFF2C4280), Color(0xFF22346A), Color(0xFF1C2A55)),
    )

    private val satanicNight = DrxTheme(
        id = "satanic", name = "Satanic", note = "Чёрный, кость и запёкшаяся кровь: острые углы, паспарту, пепел", look = Look.Rite, night = true,
        bg = Color(0xFF080707), bg2 = Color(0xFF0E0B0B),
        panel = Color(0xFF0F0D0D), panelBorder = Color(0xFF2A2424), fill = Color(0x14FFFFFF), fillHover = Color(0x22FFFFFF), menu = Color(0xFF141111),
        text = Color(0xFFEDE6E3), dim = Color(0xFFA39C99), mute = Color(0xFF7E7774),
        accent = Color(0xFFB3141B), accent2 = Color(0xFF8E8A88), onAccent = Color(0xFFF2ECE9), ok = Color(0xFF9AA89A),
        glass = false, blur = 0.dp, panelRadius = 2.dp, coverRadius = 0.dp, chipRadius = 2.dp,
        body = Fonts.manrope, heading = Fonts.playfair, headingWeight = FontWeight.Bold, headingUpper = true, headingTracking = 2.5f,
        mono = Fonts.mono, label = Fonts.manrope, labelUpper = true, decor = Fonts.playfair,
        backdrop = Backdrop.Ash,
        mist = listOf(Color(0xFF2A0606), Color(0xFF140808), Color(0xFF1A0A0A)),
        glyph = Color(0xFF8E8A88), glyphAccent = Color(0xFFB3141B),
        sample = listOf(Color(0xFFB3141B), Color(0xFFEDE6E3), Color(0xFF2A2424)),
    )
    private val satanicDay = satanicNight.copy(
        dusk = true,
        bg = Color(0xFF1A1616), bg2 = Color(0xFF221C1C),
        panel = Color(0xFF1F1A1A), panelBorder = Color(0xFF3A3232), fill = Color(0x17FFFFFF), fillHover = Color(0x26FFFFFF), menu = Color(0xFF241E1E),
        mist = listOf(Color(0xFF3A0A0A), Color(0xFF221010), Color(0xFF2A1212)),
    )

    private val monoNight = DrxTheme(
        id = "mono", name = "Монохром", note = "Чёрное и белое, без украшений", look = Look.Glass, night = true,
        bg = Color(0xFF0A0A0A), bg2 = Color(0xFF111111),
        panel = Color(0x0FFFFFFF), panelBorder = Color(0x18FFFFFF), fill = Color(0x0FFFFFFF), fillHover = Color(0x1AFFFFFF), menu = Color(0xF5171717),
        text = Color(0xFFEDEDED), dim = Color(0xFFA3A3A3), mute = Color(0xFF7D7D7D),
        accent = Color(0xFFF2F2F2), accent2 = Color(0xFF9E9E9E), onAccent = Color(0xFF0A0A0A), ok = Color(0xFFD0D0D0),
        glass = true, blur = 26.dp, panelRadius = 16.dp, coverRadius = 10.dp, chipRadius = 99.dp,
        body = Fonts.manrope, heading = Fonts.manrope, headingWeight = FontWeight.ExtraBold, mono = Fonts.mono, label = Fonts.manrope, decor = Fonts.manrope,
        backdrop = Backdrop.Mono,
        mist = listOf(Color(0xFF1E1E1E), Color(0xFF161616), Color(0xFF202020)),
        glyph = Color(0xFF9E9E9E), glyphAccent = Color(0xFFF2F2F2),
        sample = listOf(Color(0xFFF2F2F2), Color(0xFF9E9E9E), Color(0xFF3A3A3A)),
    )
    private val monoDay = monoNight.copy(
        dusk = true,
        bg = Color(0xFF1C1C1C), bg2 = Color(0xFF242424),
        panel = Color(0x14FFFFFF), panelBorder = Color(0x1FFFFFFF), fill = Color(0x14FFFFFF), fillHover = Color(0x22FFFFFF), menu = Color(0xF5282828),
        mist = listOf(Color(0xFF2E2E2E), Color(0xFF262626), Color(0xFF303030)),
    )

    val specs = listOf(
        ThemeSpec("tategaki", tategakiNight.name, tategakiNight.note, tategakiNight, tategakiDay),
        ThemeSpec("gruvbox", gruvboxNight.name, gruvboxNight.note, gruvboxNight, gruvboxDay),
        ThemeSpec("retrowave", retroNight.name, retroNight.note, retroNight, retroDay),
        ThemeSpec("depth", depthNight.name, depthNight.note, depthNight, depthDay),
        ThemeSpec("sakura", sakuraNight.name, sakuraNight.note, sakuraNight, sakuraDay),
        ThemeSpec("cozy", cozyNight.name, cozyNight.note, cozyNight, cozyDay),
        ThemeSpec("observatory", atlasNight.name, atlasNight.note, atlasNight, atlasDay),
        ThemeSpec("satanic", satanicNight.name, satanicNight.note, satanicNight, satanicDay),
        ThemeSpec("mono", monoNight.name, monoNight.note, monoNight, monoDay),
    )

    /** "itjapan" was folded into Gruvbox/Tategaki: an old setting falls back to the main theme. */
    fun spec(id: String?) = specs.firstOrNull { it.id == id } ?: specs.first()
    fun byId(id: String?, day: Boolean = false) = spec(id).of(day)
}

val LocalTheme = staticCompositionLocalOf { Themes.byId("tategaki") }
