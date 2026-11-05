package com.manuscript.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.manuscript.core.data.AppSettings
import com.manuscript.core.data.ThemeMode

val LocalNotionPalette = staticCompositionLocalOf<NotionPalette> { error("No NotionPalette provided") }
val LocalReading = staticCompositionLocalOf<ReadingStyles> { error("No ReadingStyles provided") }
val LocalAppSettings = staticCompositionLocalOf { AppSettings() }
val LocalHaptics = staticCompositionLocalOf<Haptics> { Haptics(null, false) }
val LocalIsDark = staticCompositionLocalOf { false }

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
    largeIncreased = RoundedCornerShape(26.dp),
    extraLargeIncreased = RoundedCornerShape(36.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)

@Immutable
class Haptics(private val view: View?, private val enabled: Boolean) {
    private fun perform(constant: Int) {
        if (enabled) view?.performHapticFeedback(constant)
    }

    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)
    fun click() = perform(HapticFeedbackConstants.VIRTUAL_KEY)
    fun longPress() = perform(HapticFeedbackConstants.LONG_PRESS)
    fun confirm() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    fun reject() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    fun toggle(on: Boolean) = perform(
        when {
            Build.VERSION.SDK_INT >= 34 -> if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            else -> HapticFeedbackConstants.VIRTUAL_KEY
        },
    )
    fun segment() = perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
}

@Composable
fun isAppInDarkTheme(settings: AppSettings): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun ManuscriptTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme(settings)
    val context = LocalContext.current
    val scheme = remember(dark, settings.dynamicColor, settings.seed, settings.paletteStyle, settings.contrast, settings.pureBlack) {
        val base = if (settings.dynamicColor && Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else generateScheme(Color(settings.seed), settings.paletteStyle, dark, settings.contrast)
        if (dark && settings.pureBlack) base.pureBlack() else base
    }
    val animatedScheme = animateScheme(scheme)
    val typography = remember(settings.roundness) { manuscriptTypography(settings.roundness) }
    val reading = remember(settings.readingFont, settings.roundness, settings.textScale, settings.smallText) {
        readingStyles(settings.readingFont, settings.roundness, settings.textScale, settings.smallText)
    }
    val palette = remember(scheme, dark) { NotionPalette.create(dark, scheme) }
    val motion = if (settings.reduceMotion) MotionScheme.standard() else MotionScheme.expressive()
    val view = LocalView.current
    val haptics = remember(view, settings.haptics) { Haptics(view, settings.haptics) }

    MaterialExpressiveTheme(colorScheme = animatedScheme, motionScheme = motion, shapes = ExpressiveShapes, typography = typography) {
        CompositionLocalProvider(
            LocalNotionPalette provides palette,
            LocalReading provides reading,
            LocalAppSettings provides settings,
            LocalHaptics provides haptics,
            LocalIsDark provides dark,
            content = content,
        )
    }
}

@Composable
private fun animateScheme(target: ColorScheme): ColorScheme {
    val spec = tween<Color>(durationMillis = 450)
    @Composable
    fun a(color: Color) = animateColorAsState(color, spec, label = "scheme").value
    return target.copy(
        primary = a(target.primary),
        onPrimary = a(target.onPrimary),
        primaryContainer = a(target.primaryContainer),
        onPrimaryContainer = a(target.onPrimaryContainer),
        inversePrimary = a(target.inversePrimary),
        secondary = a(target.secondary),
        onSecondary = a(target.onSecondary),
        secondaryContainer = a(target.secondaryContainer),
        onSecondaryContainer = a(target.onSecondaryContainer),
        tertiary = a(target.tertiary),
        onTertiary = a(target.onTertiary),
        tertiaryContainer = a(target.tertiaryContainer),
        onTertiaryContainer = a(target.onTertiaryContainer),
        background = a(target.background),
        onBackground = a(target.onBackground),
        surface = a(target.surface),
        onSurface = a(target.onSurface),
        surfaceVariant = a(target.surfaceVariant),
        onSurfaceVariant = a(target.onSurfaceVariant),
        surfaceTint = a(target.surfaceTint),
        inverseSurface = a(target.inverseSurface),
        inverseOnSurface = a(target.inverseOnSurface),
        outline = a(target.outline),
        outlineVariant = a(target.outlineVariant),
        surfaceBright = a(target.surfaceBright),
        surfaceDim = a(target.surfaceDim),
        surfaceContainer = a(target.surfaceContainer),
        surfaceContainerHigh = a(target.surfaceContainerHigh),
        surfaceContainerHighest = a(target.surfaceContainerHighest),
        surfaceContainerLow = a(target.surfaceContainerLow),
        surfaceContainerLowest = a(target.surfaceContainerLowest),
    )
}
