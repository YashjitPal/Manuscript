package com.manuscript.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.manuscript.core.json.NotionJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class PaletteStyle(val label: String) {
    EXPRESSIVE("Expressive"),
    TONAL_SPOT("Tonal spot"),
    VIBRANT("Vibrant"),
    FIDELITY("Fidelity"),
    CONTENT("Content"),
    RAINBOW("Rainbow"),
    FRUIT_SALAD("Fruit salad"),
    MONOCHROME("Monochrome"),
    NEUTRAL("Neutral"),
}

@Serializable
enum class ReadingFont(val label: String) { SANS("Default"), SERIF("Serif"), MONO("Mono") }

@Serializable
enum class ReadingWidth(val label: String, val maxDp: Int) {
    NARROW("Narrow", 620),
    COMFORTABLE("Comfortable", 760),
    WIDE("Wide", 980),
    FULL("Full width", 100_000),
}

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = false,
    val dynamicColor: Boolean = false,
    val seed: Long = 0xFF5B5BD6,
    val paletteStyle: PaletteStyle = PaletteStyle.EXPRESSIVE,
    val contrast: Double = 0.0,
    val readingFont: ReadingFont = ReadingFont.SANS,
    val textScale: Float = 1f,
    val roundness: Float = 100f,
    val readingWidth: ReadingWidth = ReadingWidth.COMFORTABLE,
    val haptics: Boolean = true,
    val reduceMotion: Boolean = false,
    val sidePeek: Boolean = true,
    val showCovers: Boolean = true,
    val wrapCode: Boolean = false,
    val confirmDelete: Boolean = true,
    val smallText: Boolean = false,
    val openLinksInApp: Boolean = true,
    val showOutline: Boolean = true,
    val syncOnCellular: Boolean = true,
    val onboardingDone: Boolean = false,
)

class SettingsStore(private val context: Context) {
    private val key = stringPreferencesKey("settings")

    val settings: Flow<AppSettings> = context.appDataStore.data.map { prefs ->
        prefs[key]?.let { runCatching { NotionJson.decodeFromString(AppSettings.serializer(), it) }.getOrNull() }
            ?: AppSettings()
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.appDataStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { NotionJson.decodeFromString(AppSettings.serializer(), it) }.getOrNull() }
                ?: AppSettings()
            prefs[key] = NotionJson.encodeToString(AppSettings.serializer(), transform(current))
        }
    }
}
