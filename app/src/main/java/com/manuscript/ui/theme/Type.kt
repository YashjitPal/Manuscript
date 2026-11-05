package com.manuscript.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.manuscript.R
import com.manuscript.core.data.ReadingFont

private val weights = listOf(300, 400, 500, 600, 700, 800, 900)

/** Google Sans Flex, instantiated at each weight with the user's roundness (ROND axis). */
fun googleSansFlex(roundness: Float, width: Float = 100f): FontFamily = FontFamily(
    weights.flatMap { w ->
        listOf(FontStyle.Normal, FontStyle.Italic).map { style ->
            Font(
                resId = R.font.google_sans_flex,
                weight = FontWeight(w),
                style = style,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(w),
                    FontVariation.width(width),
                    FontVariation.slant(if (style == FontStyle.Italic) -10f else 0f),
                    FontVariation.Setting("ROND", roundness),
                    FontVariation.Setting("GRAD", 0f),
                ),
            )
        }
    },
)

val NewsreaderFamily: FontFamily = FontFamily(
    listOf(400, 500, 600, 700, 800).flatMap { w ->
        listOf(
            Font(R.font.newsreader, FontWeight(w), FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(w))),
            Font(R.font.newsreader_italic, FontWeight(w), FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(w))),
        )
    },
)

val GoogleSansCodeFamily: FontFamily = FontFamily(
    listOf(300, 400, 500, 600, 700, 800).flatMap { w ->
        listOf(
            Font(R.font.google_sans_code, FontWeight(w), FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(w))),
            Font(R.font.google_sans_code_italic, FontWeight(w), FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(w))),
        )
    },
)

private val trimmed = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(family: FontFamily, size: Int, line: Int, weight: Int, tracking: TextUnit = 0.sp) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking,
    lineHeightStyle = trimmed,
)

fun manuscriptTypography(roundness: Float): Typography {
    val base = googleSansFlex(roundness)
    val display = googleSansFlex(roundness, width = 104f)
    return Typography(
        displayLarge = style(display, 57, 64, 450, (-0.25).sp),
        displayMedium = style(display, 45, 52, 450),
        displaySmall = style(display, 36, 44, 450),
        headlineLarge = style(display, 32, 40, 500),
        headlineMedium = style(display, 28, 36, 500),
        headlineSmall = style(base, 24, 32, 500),
        titleLarge = style(base, 22, 28, 500),
        titleMedium = style(base, 16, 24, 600, 0.1.sp),
        titleSmall = style(base, 14, 20, 600, 0.1.sp),
        bodyLarge = style(base, 16, 24, 400, 0.1.sp),
        bodyMedium = style(base, 14, 20, 400, 0.15.sp),
        bodySmall = style(base, 12, 16, 400, 0.3.sp),
        labelLarge = style(base, 14, 20, 600, 0.1.sp),
        labelMedium = style(base, 12, 16, 600, 0.4.sp),
        labelSmall = style(base, 11, 16, 600, 0.4.sp),
        displayLargeEmphasized = style(display, 57, 64, 800, (-0.5).sp),
        displayMediumEmphasized = style(display, 45, 52, 800, (-0.25).sp),
        displaySmallEmphasized = style(display, 36, 44, 800),
        headlineLargeEmphasized = style(display, 32, 40, 800),
        headlineMediumEmphasized = style(display, 28, 36, 800),
        headlineSmallEmphasized = style(base, 24, 32, 750),
        titleLargeEmphasized = style(base, 22, 28, 750),
        titleMediumEmphasized = style(base, 16, 24, 750, 0.1.sp),
        titleSmallEmphasized = style(base, 14, 20, 750, 0.1.sp),
        bodyLargeEmphasized = style(base, 16, 24, 600, 0.1.sp),
        bodyMediumEmphasized = style(base, 14, 20, 600, 0.15.sp),
        bodySmallEmphasized = style(base, 12, 16, 600, 0.3.sp),
        labelLargeEmphasized = style(base, 14, 20, 750, 0.1.sp),
        labelMediumEmphasized = style(base, 12, 16, 750, 0.4.sp),
        labelSmallEmphasized = style(base, 11, 16, 750, 0.4.sp),
    )
}

/** Text styles for page content, driven by the reading font, size and "small text" settings. */
@Immutable
data class ReadingStyles(
    val family: FontFamily,
    val pageTitle: TextStyle,
    val h1: TextStyle,
    val h2: TextStyle,
    val h3: TextStyle,
    val h4: TextStyle,
    val body: TextStyle,
    val quote: TextStyle,
    val code: TextStyle,
    val caption: TextStyle,
    val small: TextStyle,
)

fun readingStyles(font: ReadingFont, roundness: Float, scale: Float, smallText: Boolean): ReadingStyles {
    val ui = googleSansFlex(roundness)
    val family = when (font) {
        ReadingFont.SANS -> ui
        ReadingFont.SERIF -> NewsreaderFamily
        ReadingFont.MONO -> GoogleSansCodeFamily
    }
    val titleFamily = if (font == ReadingFont.SANS) googleSansFlex(roundness, width = 104f) else family
    val s = scale * if (smallText) 0.9f else 1f
    val bodySize = (if (font == ReadingFont.SERIF) 17.5f else 16.5f) * s
    fun t(fam: FontFamily, size: Float, line: Float, weight: Int, tracking: Float = 0f) = TextStyle(
        fontFamily = fam,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = FontWeight(weight),
        letterSpacing = tracking.em,
        lineHeightStyle = trimmed,
    )
    return ReadingStyles(
        family = family,
        pageTitle = t(titleFamily, 40f * s, 48f * s, 800, -0.02f),
        h1 = t(titleFamily, 30f * s, 38f * s, 750, -0.01f),
        h2 = t(titleFamily, 24f * s, 32f * s, 700, -0.005f),
        h3 = t(family, 20f * s, 28f * s, 700),
        h4 = t(family, 17.5f * s, 25f * s, 700),
        body = t(family, bodySize, bodySize * 1.6f, if (font == ReadingFont.SERIF) 420 else 400),
        quote = t(family, bodySize * 1.06f, bodySize * 1.65f, 450),
        code = t(GoogleSansCodeFamily, 14f * s, 22f * s, 400),
        caption = t(ui, 13f * s, 18f * s, 450),
        small = t(ui, 13f * s, 18f * s, 500),
    )
}
