package com.manuscript.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.manuscript.core.data.PaletteStyle
import com.manuscript.core.model.Hue
import com.manuscript.core.model.NColor
import com.materialkolor.blend.Blend
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFidelity
import com.materialkolor.scheme.SchemeFruitSalad
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeRainbow
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant

/** Seed colors offered in the theme picker. */
val SeedColors: List<Pair<String, Color>> = listOf(
    "Iris" to Color(0xFF5B5BD6),
    "Ocean" to Color(0xFF0B6BCB),
    "Lagoon" to Color(0xFF00897B),
    "Fern" to Color(0xFF3F8F3A),
    "Citrus" to Color(0xFFB58A00),
    "Ember" to Color(0xFFE8590C),
    "Rose" to Color(0xFFD6336C),
    "Orchid" to Color(0xFF9C36B5),
    "Cocoa" to Color(0xFF8D6E63),
    "Graphite" to Color(0xFF5F6B7A),
)

fun generateScheme(seed: Color, style: PaletteStyle, dark: Boolean, contrast: Double): ColorScheme {
    val hct = Hct.fromInt(seed.toArgb())
    val spec = ColorSpec.SpecVersion.SPEC_2025
    val s: DynamicScheme = when (style) {
        PaletteStyle.EXPRESSIVE -> SchemeExpressive(hct, dark, contrast, spec)
        PaletteStyle.TONAL_SPOT -> SchemeTonalSpot(hct, dark, contrast, spec)
        PaletteStyle.VIBRANT -> SchemeVibrant(hct, dark, contrast, spec)
        PaletteStyle.FIDELITY -> SchemeFidelity(hct, dark, contrast, spec)
        PaletteStyle.CONTENT -> SchemeContent(hct, dark, contrast, spec)
        PaletteStyle.RAINBOW -> SchemeRainbow(hct, dark, contrast, spec)
        PaletteStyle.FRUIT_SALAD -> SchemeFruitSalad(hct, dark, contrast, spec)
        PaletteStyle.MONOCHROME -> SchemeMonochrome(hct, dark, contrast, spec)
        PaletteStyle.NEUTRAL -> SchemeNeutral(hct, dark, contrast, spec)
    }
    fun c(argb: Int) = Color(argb)
    return if (dark) darkColorScheme(
        primary = c(s.primary), onPrimary = c(s.onPrimary), primaryContainer = c(s.primaryContainer), onPrimaryContainer = c(s.onPrimaryContainer),
        inversePrimary = c(s.inversePrimary), secondary = c(s.secondary), onSecondary = c(s.onSecondary),
        secondaryContainer = c(s.secondaryContainer), onSecondaryContainer = c(s.onSecondaryContainer), tertiary = c(s.tertiary),
        onTertiary = c(s.onTertiary), tertiaryContainer = c(s.tertiaryContainer), onTertiaryContainer = c(s.onTertiaryContainer),
        background = c(s.background), onBackground = c(s.onBackground), surface = c(s.surface), onSurface = c(s.onSurface),
        surfaceVariant = c(s.surfaceVariant), onSurfaceVariant = c(s.onSurfaceVariant), surfaceTint = c(s.surfaceTint),
        inverseSurface = c(s.inverseSurface), inverseOnSurface = c(s.inverseOnSurface), error = c(s.error), onError = c(s.onError),
        errorContainer = c(s.errorContainer), onErrorContainer = c(s.onErrorContainer), outline = c(s.outline),
        outlineVariant = c(s.outlineVariant), scrim = c(s.scrim), surfaceBright = c(s.surfaceBright), surfaceContainer = c(s.surfaceContainer),
        surfaceContainerHigh = c(s.surfaceContainerHigh), surfaceContainerHighest = c(s.surfaceContainerHighest),
        surfaceContainerLow = c(s.surfaceContainerLow), surfaceContainerLowest = c(s.surfaceContainerLowest), surfaceDim = c(s.surfaceDim),
        primaryFixed = c(s.primaryFixed), primaryFixedDim = c(s.primaryFixedDim), onPrimaryFixed = c(s.onPrimaryFixed),
        onPrimaryFixedVariant = c(s.onPrimaryFixedVariant), secondaryFixed = c(s.secondaryFixed), secondaryFixedDim = c(s.secondaryFixedDim),
        onSecondaryFixed = c(s.onSecondaryFixed), onSecondaryFixedVariant = c(s.onSecondaryFixedVariant),
    ) else lightColorScheme(
        primary = c(s.primary), onPrimary = c(s.onPrimary), primaryContainer = c(s.primaryContainer), onPrimaryContainer = c(s.onPrimaryContainer),
        inversePrimary = c(s.inversePrimary), secondary = c(s.secondary), onSecondary = c(s.onSecondary),
        secondaryContainer = c(s.secondaryContainer), onSecondaryContainer = c(s.onSecondaryContainer), tertiary = c(s.tertiary),
        onTertiary = c(s.onTertiary), tertiaryContainer = c(s.tertiaryContainer), onTertiaryContainer = c(s.onTertiaryContainer),
        background = c(s.background), onBackground = c(s.onBackground), surface = c(s.surface), onSurface = c(s.onSurface),
        surfaceVariant = c(s.surfaceVariant), onSurfaceVariant = c(s.onSurfaceVariant), surfaceTint = c(s.surfaceTint),
        inverseSurface = c(s.inverseSurface), inverseOnSurface = c(s.inverseOnSurface), error = c(s.error), onError = c(s.onError),
        errorContainer = c(s.errorContainer), onErrorContainer = c(s.onErrorContainer), outline = c(s.outline),
        outlineVariant = c(s.outlineVariant), scrim = c(s.scrim), surfaceBright = c(s.surfaceBright), surfaceContainer = c(s.surfaceContainer),
        surfaceContainerHigh = c(s.surfaceContainerHigh), surfaceContainerHighest = c(s.surfaceContainerHighest),
        surfaceContainerLow = c(s.surfaceContainerLow), surfaceContainerLowest = c(s.surfaceContainerLowest), surfaceDim = c(s.surfaceDim),
        primaryFixed = c(s.primaryFixed), primaryFixedDim = c(s.primaryFixedDim), onPrimaryFixed = c(s.onPrimaryFixed),
        onPrimaryFixedVariant = c(s.onPrimaryFixedVariant), secondaryFixed = c(s.secondaryFixed), secondaryFixedDim = c(s.secondaryFixedDim),
        onSecondaryFixed = c(s.onSecondaryFixed), onSecondaryFixedVariant = c(s.onSecondaryFixedVariant),
    )
}

fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0D),
    surfaceContainer = Color(0xFF111114),
    surfaceContainerHigh = Color(0xFF18181C),
    surfaceContainerHighest = Color(0xFF202025),
    surfaceDim = Color.Black,
)

private val lightText = mapOf(
    Hue.GRAY to 0xFF787774, Hue.LIGHT_GRAY to 0xFFA3A29E, Hue.BROWN to 0xFF9F6B53, Hue.ORANGE to 0xFFD9730D, Hue.YELLOW to 0xFFCB912F,
    Hue.GREEN to 0xFF448361, Hue.BLUE to 0xFF337EA9, Hue.PURPLE to 0xFF9065B0, Hue.PINK to 0xFFC14C8A, Hue.RED to 0xFFD44C47, Hue.TEAL to 0xFF2A8C8C,
)
private val darkText = mapOf(
    Hue.GRAY to 0xFF9B9B9B, Hue.LIGHT_GRAY to 0xFF7F7F7F, Hue.BROWN to 0xFFBA856F, Hue.ORANGE to 0xFFC77D48, Hue.YELLOW to 0xFFCA9849,
    Hue.GREEN to 0xFF529E72, Hue.BLUE to 0xFF379AD3, Hue.PURPLE to 0xFF9D68D3, Hue.PINK to 0xFFD15796, Hue.RED to 0xFFDF5452, Hue.TEAL to 0xFF4FB3B3,
)
private val lightBackground = mapOf(
    Hue.GRAY to 0xFFF1F1EF, Hue.LIGHT_GRAY to 0xFFF6F6F4, Hue.BROWN to 0xFFF4EEEE, Hue.ORANGE to 0xFFFBECDD, Hue.YELLOW to 0xFFFBF3DB,
    Hue.GREEN to 0xFFEDF3EC, Hue.BLUE to 0xFFE7F3F8, Hue.PURPLE to 0xFFF6F3F9, Hue.PINK to 0xFFFAF1F5, Hue.RED to 0xFFFDEBEC, Hue.TEAL to 0xFFE6F4F3,
)
private val darkBackground = mapOf(
    Hue.GRAY to 0xFF2F2F2F, Hue.LIGHT_GRAY to 0xFF282828, Hue.BROWN to 0xFF4A3228, Hue.ORANGE to 0xFF5C3B23, Hue.YELLOW to 0xFF564328,
    Hue.GREEN to 0xFF243D30, Hue.BLUE to 0xFF143A4E, Hue.PURPLE to 0xFF3C2D49, Hue.PINK to 0xFF4E2C3C, Hue.RED to 0xFF522E2A, Hue.TEAL to 0xFF173C3C,
)
private val lightOption = mapOf(
    Hue.DEFAULT to 0xFFE9E8E5, Hue.GRAY to 0xFFE3E2E0, Hue.LIGHT_GRAY to 0xFFEDEDEB, Hue.BROWN to 0xFFEEE0DA, Hue.ORANGE to 0xFFFADEC9,
    Hue.YELLOW to 0xFFFDECC8, Hue.GREEN to 0xFFDBEDDB, Hue.BLUE to 0xFFD3E5EF, Hue.PURPLE to 0xFFE8DEEE, Hue.PINK to 0xFFF5E0E9,
    Hue.RED to 0xFFFFE2DD, Hue.TEAL to 0xFFD2ECEA,
)
private val darkOption = mapOf(
    Hue.DEFAULT to 0xFF373737, Hue.GRAY to 0xFF5A5A5A, Hue.LIGHT_GRAY to 0xFF454545, Hue.BROWN to 0xFF603B2C, Hue.ORANGE to 0xFF854C1D,
    Hue.YELLOW to 0xFF89632A, Hue.GREEN to 0xFF2B593F, Hue.BLUE to 0xFF28456C, Hue.PURPLE to 0xFF492F64, Hue.PINK to 0xFF69314C,
    Hue.RED to 0xFF6E3630, Hue.TEAL to 0xFF1F5652,
)

/** Notion's color palette, harmonized toward the current theme so it feels native. */
@Immutable
class NotionPalette(
    private val text: Map<Hue, Color>,
    private val background: Map<Hue, Color>,
    private val option: Map<Hue, Color>,
    val optionContent: Color,
    val defaultText: Color,
    val dark: Boolean,
) {
    fun text(hue: Hue): Color = text[hue] ?: defaultText
    fun background(hue: Hue): Color = background[hue] ?: Color.Transparent
    fun option(hue: Hue): Color = option[hue] ?: option.getValue(Hue.DEFAULT)

    fun textColor(color: NColor): Color =
        if (color.isDefault || color.background) Color.Unspecified else text(color.hue)

    fun backgroundColor(color: NColor): Color =
        if (color.background && !color.isDefault) background(color.hue) else Color.Unspecified

    /** Accent used for icons, chart series and avatars. */
    fun accent(hue: Hue): Color = text[hue] ?: defaultText

    companion object {
        fun create(dark: Boolean, scheme: ColorScheme): NotionPalette {
            val source = scheme.primary.toArgb()
            fun harmonize(map: Map<Hue, Long>, amount: Float): Map<Hue, Color> = map.mapValues { (_, v) ->
                val original = Color(v)
                val harmonized = Color(Blend.harmonize(original.toArgb(), source))
                lerp(original, harmonized, amount)
            }
            return NotionPalette(
                text = harmonize(if (dark) darkText else lightText, 0.6f),
                background = harmonize(if (dark) darkBackground else lightBackground, 0.5f).mapValues { (_, c) ->
                    lerp(c, scheme.surface, if (dark) 0.15f else 0.1f)
                },
                option = harmonize(if (dark) darkOption else lightOption, 0.45f),
                optionContent = if (dark) Color(0xFFEDEDED) else Color(0xFF32302C),
                defaultText = scheme.onSurface,
                dark = dark,
            )
        }
    }
}

/** Chart series colors for the "colorful" theme, drawn from Notion hues. */
val ChartHues = listOf(Hue.BLUE, Hue.ORANGE, Hue.GREEN, Hue.PURPLE, Hue.PINK, Hue.YELLOW, Hue.RED, Hue.TEAL, Hue.BROWN, Hue.GRAY)
