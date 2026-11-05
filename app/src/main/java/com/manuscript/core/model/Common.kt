package com.manuscript.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class Hue(val api: String, val label: String) {
    DEFAULT("default", "Default"),
    GRAY("gray", "Gray"),
    LIGHT_GRAY("lightgray", "Light gray"),
    BROWN("brown", "Brown"),
    ORANGE("orange", "Orange"),
    YELLOW("yellow", "Yellow"),
    GREEN("green", "Green"),
    BLUE("blue", "Blue"),
    PURPLE("purple", "Purple"),
    PINK("pink", "Pink"),
    RED("red", "Red"),
    TEAL("teal", "Teal");

    companion object {
        fun of(api: String?): Hue = entries.firstOrNull { it.api == api } ?: DEFAULT

        /** Colors Notion accepts for select, multi-select and status options. */
        val optionHues = listOf(DEFAULT, GRAY, BROWN, ORANGE, YELLOW, GREEN, BLUE, PURPLE, PINK, RED)

        /** Colors Notion accepts for native icons. */
        val iconHues = listOf(GRAY, LIGHT_GRAY, BROWN, YELLOW, ORANGE, GREEN, BLUE, PURPLE, PINK, RED)
    }
}

@Immutable
data class NColor(val hue: Hue = Hue.DEFAULT, val background: Boolean = false) {
    val api: String get() = if (background && hue != Hue.DEFAULT) "${hue.api}_background" else hue.api
    val isDefault: Boolean get() = hue == Hue.DEFAULT

    companion object {
        val Default = NColor()
        fun parse(api: String?): NColor = when {
            api == null -> Default
            api.endsWith("_background") -> NColor(Hue.of(api.removeSuffix("_background")), true)
            else -> NColor(Hue.of(api), false)
        }
    }
}

@Immutable
sealed interface NIcon {
    data class Emoji(val emoji: String) : NIcon
    data class External(val url: String) : NIcon
    data class Hosted(val url: String, val expiry: String? = null) : NIcon
    data class CustomEmoji(val id: String, val name: String? = null, val url: String? = null) : NIcon
    data class Native(val name: String, val color: String = "gray") : NIcon {
        val url: String get() = "https://www.notion.so/icons/${name.lowercase().replace('_', '-').replace(' ', '-')}_$color.svg"
    }
    data class Upload(val fileUploadId: String) : NIcon
}

@Immutable
data class NFile(
    val url: String,
    val name: String? = null,
    val hosted: Boolean = false,
    val expiry: String? = null,
    val uploadId: String? = null,
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: url.substringBefore('?').substringAfterLast('/').let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
                .ifBlank { "File" }
    val extension: String get() = displayName.substringAfterLast('.', "").lowercase()
}

@Immutable
sealed interface Parent {
    data object Workspace : Parent
    data class Page(val id: String) : Parent
    data class Database(val id: String) : Parent
    data class DataSource(val id: String, val databaseId: String?) : Parent
    data class Block(val id: String) : Parent
    data class Agent(val id: String) : Parent
    data object Unknown : Parent

    val parentId: String?
        get() = when (this) {
            is Page -> id
            is Database -> id
            is DataSource -> id
            is Block -> id
            is Agent -> id
            else -> null
        }
}

@Immutable
data class NDate(val start: String, val end: String? = null, val timeZone: String? = null) {
    val hasTime: Boolean get() = start.length > 10
    val isRange: Boolean get() = end != null

    fun startDate(): LocalDate? = parseDate(start)
    fun endDate(): LocalDate? = end?.let(::parseDate)
    fun startDateTime(zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime? = parseDateTime(start, zone)
    fun endDateTime(zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime? = end?.let { parseDateTime(it, zone) }

    companion object {
        fun parseDate(value: String): LocalDate? = runCatching {
            if (value.length <= 10) LocalDate.parse(value) else OffsetDateTime.parse(value).toLocalDate()
        }.getOrNull() ?: runCatching { LocalDateTime.parse(value).toLocalDate() }.getOrNull()

        fun parseDateTime(value: String, zone: ZoneId): ZonedDateTime? = runCatching {
            if (value.length <= 10) LocalDate.parse(value).atStartOfDay(zone)
            else OffsetDateTime.parse(value).atZoneSameInstant(zone)
        }.getOrNull() ?: runCatching { LocalDateTime.parse(value).atZone(zone) }.getOrNull()
    }
}

fun parseInstant(value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() }

@Immutable
data class NUser(
    val id: String,
    val name: String? = null,
    val avatarUrl: String? = null,
    val type: String? = null,
    val email: String? = null,
    val workspaceName: String? = null,
    val workspaceId: String? = null,
    val isGroup: Boolean = false,
    val maxUploadBytes: Long? = null,
    val ownerUser: NUser? = null,
) {
    val isBot: Boolean get() = type == "bot"
    val isPerson: Boolean get() = type == "person"
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: email ?: if (isBot) "Integration" else "Unknown user"
    val initials: String
        get() = displayName.split(' ', '.', '_', '-').filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
}

data class Paged<T>(
    val items: List<T>,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val incomplete: Boolean = false,
)

@Immutable
data class Annotations(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikethrough: Boolean = false,
    val underline: Boolean = false,
    val code: Boolean = false,
    val color: NColor = NColor.Default,
) {
    val isPlain: Boolean get() = this == None

    companion object {
        val None = Annotations()
    }
}

@Immutable
sealed interface Mention {
    data class User(val id: String, val name: String? = null, val avatarUrl: String? = null) : Mention
    data class Page(val id: String) : Mention
    data class Database(val id: String) : Mention
    data class Date(val date: NDate) : Mention
    data class LinkPreview(val url: String) : Mention
    data class LinkMention(
        val href: String,
        val title: String? = null,
        val iconUrl: String? = null,
        val provider: String? = null,
        val thumbnailUrl: String? = null,
    ) : Mention
    data class TemplateDate(val value: String) : Mention
    data class TemplateUser(val value: String) : Mention
    data class CustomEmoji(val id: String, val name: String? = null, val url: String? = null) : Mention
    data class Unknown(val type: String, val raw: JsonObject) : Mention
}

@Immutable
data class RichText(
    val text: String,
    val annotations: Annotations = Annotations.None,
    val link: String? = null,
    val mention: Mention? = null,
    val equation: String? = null,
) {
    val isMention: Boolean get() = mention != null
    val isEquation: Boolean get() = equation != null

    companion object {
        fun plain(text: String) = RichText(text)
    }
}

fun List<RichText>.plainText(): String = joinToString("") { it.text }
fun String.toRich(): List<RichText> = if (isEmpty()) emptyList() else listOf(RichText(this))
