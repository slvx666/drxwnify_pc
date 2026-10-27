package com.drxwnify.desktop.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Typeface

object Fonts {
    private fun bytes(name: String): ByteArray =
        Fonts::class.java.classLoader.getResourceAsStream("fonts/$name")!!.use { it.readBytes() }

    private fun font(name: String, weight: FontWeight) = Font("drxw-$name", bytes(name), weight)

    val manrope = FontFamily(
        font("Manrope-Regular.ttf", FontWeight.Normal),
        font("Manrope-Medium.ttf", FontWeight.Medium),
        font("Manrope-SemiBold.ttf", FontWeight.SemiBold),
        font("Manrope-Bold.ttf", FontWeight.Bold),
        font("Manrope-ExtraBold.ttf", FontWeight.ExtraBold),
    )
    val mono = FontFamily(
        font("JetBrainsMono-Regular.ttf", FontWeight.Normal),
        font("JetBrainsMono-Medium.ttf", FontWeight.Medium),
        font("JetBrainsMono-Bold.ttf", FontWeight.Bold),
    )
    val jpSans = FontFamily(
        font("NotoSansJP-Light.ttf", FontWeight.Light),
        font("NotoSansJP-Regular.ttf", FontWeight.Normal),
    )
    val jpSerif = FontFamily(font("NotoSerifJP-SemiBold.ttf", FontWeight.SemiBold))
    val russo = FontFamily(font("RussoOne-Regular.ttf", FontWeight.Normal))
    val icons = FontFamily(font("tabler-icons.ttf", FontWeight.Normal))
    /** Satanic: a high-contrast book face, set in spaced capitals like a metal label's sleeve notes. */
    val playfair = FontFamily(font("PlayfairDisplay-SemiBold.ttf", FontWeight.SemiBold), font("PlayfairDisplay-Bold.ttf", FontWeight.Bold))

    // Faces of the themes (Latin + Cyrillic subsets).
    val cormorant = FontFamily(
        font("CormorantGaramond-SemiBold.ttf", FontWeight.SemiBold),
        font("CormorantGaramond-Bold.ttf", FontWeight.Bold),
        Font("drxw-CormorantGaramond-SemiBoldItalic.ttf", bytes("CormorantGaramond-SemiBoldItalic.ttf"), FontWeight.SemiBold, FontStyle.Italic),
    )
    val comfortaa = FontFamily(font("Comfortaa-Bold.ttf", FontWeight.Bold))
    val nunito = FontFamily(
        font("Nunito-SemiBold.ttf", FontWeight.SemiBold),
        font("Nunito-Bold.ttf", FontWeight.Bold),
        font("Nunito-ExtraBold.ttf", FontWeight.ExtraBold),
    )
    val caveat = FontFamily(font("Caveat-Bold.ttf", FontWeight.Bold))
    val forum = FontFamily(font("Forum-Regular.ttf", FontWeight.Normal))
    val spectral = FontFamily(
        font("Spectral-Regular.ttf", FontWeight.Normal),
        font("Spectral-SemiBold.ttf", FontWeight.SemiBold),
        Font("drxw-Spectral-Italic.ttf", bytes("Spectral-Italic.ttf"), FontWeight.Normal, FontStyle.Italic),
    )
    /** Latin and digits only: the tape-deck display of Ретровейв. */
    val vt323 = FontFamily(font("VT323-Regular.ttf", FontWeight.Normal))

    /** Raw Skia typefaces for the background canvases (thousands of glyphs a frame). */
    val skiaJp: Typeface by lazy { FontMgr.default.makeFromData(Data.makeFromBytes(bytes("NotoSansJP-Regular.ttf")))!! }
    val skiaMono: Typeface by lazy { FontMgr.default.makeFromData(Data.makeFromBytes(bytes("JetBrainsMono-Regular.ttf")))!! }
}
