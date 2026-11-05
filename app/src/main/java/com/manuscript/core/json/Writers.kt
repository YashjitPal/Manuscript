package com.manuscript.core.json

import com.manuscript.core.model.Annotations
import com.manuscript.core.model.BlockType
import com.manuscript.core.model.FilterNode
import com.manuscript.core.model.Mention
import com.manuscript.core.model.NBlock
import com.manuscript.core.model.NDate
import com.manuscript.core.model.NFile
import com.manuscript.core.model.NIcon
import com.manuscript.core.model.PropType
import com.manuscript.core.model.PropValue
import com.manuscript.core.model.PropertySchema
import com.manuscript.core.model.RichText
import com.manuscript.core.model.SelectOption
import com.manuscript.core.model.SortSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object Write {

    private const val MAX_TEXT = 2000

    fun annotations(a: Annotations) = obj(
        "bold" to a.bold,
        "italic" to a.italic,
        "strikethrough" to a.strikethrough,
        "underline" to a.underline,
        "code" to a.code,
        "color" to a.color.api,
    )

    fun richText(items: List<RichText>, cache: Boolean = false): JsonArray =
        JsonArray(items.flatMap { richTextItems(it, cache) })

    private fun richTextItems(rt: RichText, cache: Boolean): List<JsonObject> {
        val ann = annotations(rt.annotations)
        val extra: Map<String, Any?> = if (cache) mapOf("plain_text" to rt.text, "href" to (rt.link ?: JsonNull)) else emptyMap()
        rt.equation?.let {
            return listOf(JsonObject(obj("type" to "equation", "equation" to obj("expression" to it), "annotations" to ann) + extra.mapValues { e -> e.value.toJson() }))
        }
        rt.mention?.let { m ->
            val body = mentionBody(m) ?: if (cache) cachedMentionBody(m) else null
            if (body != null) {
                return listOf(JsonObject(obj("type" to "mention", "mention" to body, "annotations" to ann) + extra.mapValues { e -> e.value.toJson() }))
            }
        }
        val link = rt.link ?: when (val m = rt.mention) {
            is Mention.LinkPreview -> m.url
            is Mention.LinkMention -> m.href
            else -> null
        }
        if (rt.text.isEmpty()) return emptyList()
        return rt.text.chunked(MAX_TEXT).map { chunk ->
            val item = obj(
                "type" to "text",
                "text" to obj("content" to chunk, "link" to (link?.let { obj("url" to it) } ?: JsonNull)),
                "annotations" to ann,
            )
            if (cache) JsonObject(item + mapOf("plain_text" to JsonPrimitive(chunk), "href" to (link?.let(::JsonPrimitive) ?: JsonNull))) else item
        }
    }

    /** Response-shaped bodies for mentions the API won't accept on write, used when caching. */
    private fun cachedMentionBody(m: Mention): JsonObject? = when (m) {
        is Mention.LinkPreview -> obj("type" to "link_preview", "link_preview" to obj("url" to m.url))
        is Mention.LinkMention -> obj(
            "type" to "link_mention",
            "link_mention" to obj(
                "href" to m.href,
                "title" to (m.title ?: Skip),
                "icon_url" to (m.iconUrl ?: Skip),
                "link_provider" to (m.provider ?: Skip),
                "thumbnail_url" to (m.thumbnailUrl ?: Skip),
            ),
        )
        is Mention.Unknown -> m.raw
        else -> null
    }

    private fun mentionBody(m: Mention): JsonObject? = when (m) {
        is Mention.User -> obj("type" to "user", "user" to obj("id" to m.id))
        is Mention.Page -> obj("type" to "page", "page" to obj("id" to m.id))
        is Mention.Database -> obj("type" to "database", "database" to obj("id" to m.id))
        is Mention.Date -> obj("type" to "date", "date" to date(m.date))
        is Mention.TemplateDate -> obj(
            "type" to "template_mention",
            "template_mention" to obj("type" to "template_mention_date", "template_mention_date" to m.value),
        )
        is Mention.TemplateUser -> obj(
            "type" to "template_mention",
            "template_mention" to obj("type" to "template_mention_user", "template_mention_user" to m.value),
        )
        is Mention.CustomEmoji -> obj("type" to "custom_emoji", "custom_emoji" to obj("id" to m.id))
        is Mention.LinkPreview, is Mention.LinkMention, is Mention.Unknown -> null
    }

    fun date(d: NDate): JsonObject = obj(
        "start" to d.start,
        "end" to (d.end ?: JsonNull),
        "time_zone" to (d.timeZone ?: Skip),
    )

    fun icon(icon: NIcon?): JsonElement = when (icon) {
        null -> JsonNull
        is NIcon.Emoji -> obj("type" to "emoji", "emoji" to icon.emoji)
        is NIcon.External -> obj("type" to "external", "external" to obj("url" to icon.url))
        is NIcon.Hosted -> obj("type" to "external", "external" to obj("url" to icon.url))
        is NIcon.CustomEmoji -> obj("type" to "custom_emoji", "custom_emoji" to obj("id" to icon.id))
        is NIcon.Native -> obj("type" to "icon", "icon" to obj("name" to icon.name, "color" to icon.color))
        is NIcon.Upload -> obj("type" to "file_upload", "file_upload" to obj("id" to icon.fileUploadId))
    }

    fun cover(file: NFile?): JsonElement = when {
        file == null -> JsonNull
        file.uploadId != null -> obj("type" to "file_upload", "file_upload" to obj("id" to file.uploadId))
        else -> obj("type" to "external", "external" to obj("url" to file.url))
    }

    /** A file reference for media blocks or files properties. */
    fun fileRef(file: NFile, includeName: Boolean = true): JsonObject {
        val name = if (includeName) (file.name ?: Skip) else Skip
        return when {
            file.uploadId != null -> obj("type" to "file_upload", "file_upload" to obj("id" to file.uploadId), "name" to name)
            file.hosted -> obj("type" to "file", "file" to obj("url" to file.url), "name" to name)
            else -> obj("type" to "external", "external" to obj("url" to file.url), "name" to name)
        }
    }

    fun option(o: SelectOption, includeColor: Boolean = false): JsonObject = when {
        o.id != null && !includeColor -> obj("id" to o.id)
        else -> obj("name" to o.name, "color" to (if (includeColor) o.color.api else Skip))
    }

    /** Request body for a single page property value. */
    fun propValue(v: PropValue): JsonObject? = when (v) {
        is PropValue.Title -> obj("title" to richText(v.rich))
        is PropValue.Text -> obj("rich_text" to richText(v.rich))
        is PropValue.Number -> obj("number" to (v.value ?: JsonNull))
        is PropValue.Select -> obj("select" to (v.option?.let { option(it) } ?: JsonNull))
        is PropValue.MultiSelect -> obj("multi_select" to JsonArray(v.options.map { option(it) }))
        is PropValue.Status -> obj("status" to (v.option?.let { option(it) } ?: JsonNull))
        is PropValue.DateV -> obj("date" to (v.date?.let(::date) ?: JsonNull))
        is PropValue.People -> obj("people" to JsonArray(v.users.map { obj("id" to it.id) }))
        is PropValue.Files -> obj("files" to JsonArray(v.files.map { fileRef(it) }))
        is PropValue.Checkbox -> obj("checkbox" to v.checked)
        is PropValue.Url -> obj("url" to (v.url?.ifBlank { null } ?: JsonNull))
        is PropValue.Email -> obj("email" to (v.email?.ifBlank { null } ?: JsonNull))
        is PropValue.Phone -> obj("phone_number" to (v.phone?.ifBlank { null } ?: JsonNull))
        is PropValue.Relation -> obj("relation" to JsonArray(v.ids.map { obj("id" to it) }))
        is PropValue.Verification -> obj(
            "verification" to obj(
                "state" to (if (v.state == "verified") "verified" else "unverified"),
                "date" to (v.date?.let(::date) ?: Skip),
            ),
        )
        else -> null
    }

    fun properties(values: Map<String, PropValue>): JsonObject =
        JsonObject(values.mapNotNull { (k, v) -> propValue(v)?.let { k to it } }.toMap())

    /** Request body describing a block to create (with up to two levels of nested children). */
    fun blockCreate(b: NBlock, depth: Int = 0): JsonObject {
        val body = blockBody(b, forCreate = true).toMutableMap()
        val nestedChildren = when (b.type) {
            BlockType.TABLE, BlockType.COLUMN_LIST, BlockType.TAB -> b.children
            else -> if (depth < 2) b.children.filter { it.type != BlockType.CHILD_PAGE && it.type != BlockType.CHILD_DATABASE } else emptyList()
        }
        if (nestedChildren.isNotEmpty()) {
            body["children"] = JsonArray(nestedChildren.map { blockCreate(it, depth + 1) })
        }
        return obj("object" to "block", "type" to b.type.api, b.type.api to JsonObject(body))
    }

    /** Request body for PATCH /v1/blocks/{id}. */
    fun blockUpdate(b: NBlock): JsonObject = obj(b.type.api to blockBody(b, forCreate = false))

    private fun blockBody(b: NBlock, forCreate: Boolean): JsonObject = when (b.type) {
        BlockType.PARAGRAPH, BlockType.QUOTE, BlockType.BULLETED, BlockType.TOGGLE ->
            obj("rich_text" to richText(b.rich), "color" to b.color.api, "icon" to (if (b.icon != null && b.type == BlockType.PARAGRAPH) icon(b.icon) else Skip))
        BlockType.NUMBERED -> obj(
            "rich_text" to richText(b.rich),
            "color" to b.color.api,
            "list_start_index" to (b.listStart ?: Skip),
            "list_format" to (b.listFormat ?: Skip),
        )
        BlockType.HEADING_1, BlockType.HEADING_2, BlockType.HEADING_3, BlockType.HEADING_4 ->
            obj("rich_text" to richText(b.rich), "color" to b.color.api, "is_toggleable" to b.toggleable)
        BlockType.TO_DO -> obj("rich_text" to richText(b.rich), "checked" to b.checked, "color" to b.color.api)
        BlockType.CALLOUT -> obj(
            "rich_text" to richText(b.rich),
            "color" to b.color.api,
            "icon" to (b.icon?.let(::icon) ?: Skip),
        )
        BlockType.CODE -> obj(
            "rich_text" to richText(b.rich),
            "language" to (b.language ?: "plain text"),
            "caption" to richText(b.caption),
        )
        BlockType.EQUATION -> obj("expression" to (b.expression ?: ""))
        BlockType.DIVIDER, BlockType.BREADCRUMB, BlockType.COLUMN_LIST, BlockType.TAB -> obj()
        BlockType.TABLE_OF_CONTENTS -> obj("color" to b.color.api)
        BlockType.IMAGE, BlockType.VIDEO, BlockType.AUDIO, BlockType.PDF, BlockType.FILE -> {
            val f = b.file ?: NFile(b.url ?: "")
            JsonObject(fileRef(f, includeName = b.type == BlockType.FILE) + ("caption" to richText(b.caption)))
        }
        BlockType.BOOKMARK, BlockType.LINK_PREVIEW -> obj("url" to (b.url ?: ""), "caption" to richText(b.caption))
        BlockType.EMBED -> if (b.file?.uploadId != null) {
            obj("file_upload" to obj("id" to b.file.uploadId), "caption" to richText(b.caption))
        } else obj("url" to (b.url ?: ""), "caption" to richText(b.caption))
        BlockType.LINK_TO_PAGE -> when {
            b.linkPageId != null -> obj("type" to "page_id", "page_id" to b.linkPageId)
            else -> obj("type" to "database_id", "database_id" to (b.linkDatabaseId ?: ""))
        }
        BlockType.TABLE -> if (forCreate) obj(
            "table_width" to b.tableWidth.coerceAtLeast(1),
            "has_column_header" to b.hasColumnHeader,
            "has_row_header" to b.hasRowHeader,
        ) else obj("has_column_header" to b.hasColumnHeader, "has_row_header" to b.hasRowHeader)
        BlockType.TABLE_ROW -> obj("cells" to JsonArray(b.cells.map { richText(it) }))
        BlockType.COLUMN -> obj("width_ratio" to (b.widthRatio ?: Skip))
        BlockType.SYNCED_BLOCK -> obj("synced_from" to (b.syncedFrom?.let { obj("type" to "block_id", "block_id" to it) } ?: JsonNull))
        BlockType.TEMPLATE -> obj("rich_text" to richText(b.rich))
        else -> obj()
    }

    /** Property configuration for creating or changing a data source property. */
    fun propertyConfig(p: PropertySchema, includeName: Boolean): JsonObject {
        val config: JsonObject = when (p.type) {
            PropType.NUMBER -> obj("format" to (p.numberFormat ?: "number"))
            PropType.SELECT, PropType.MULTI_SELECT -> obj("options" to JsonArray(p.options.map { optionConfig(it) }))
            PropType.STATUS -> if (p.options.isEmpty()) obj() else obj("options" to JsonArray(p.options.map { optionConfig(it) }))
            PropType.FORMULA -> obj("expression" to (p.formula ?: ""))
            PropType.RELATION -> {
                val r = p.relation
                if (r?.dual == true) obj("data_source_id" to (r.dataSourceId ?: ""), "type" to "dual_property", "dual_property" to obj())
                else obj("data_source_id" to (r?.dataSourceId ?: ""), "type" to "single_property", "single_property" to obj())
            }
            PropType.ROLLUP -> obj(
                "function" to (p.rollup?.function ?: "show_original"),
                "relation_property_name" to (p.rollup?.relationPropertyName ?: Skip),
                "relation_property_id" to (p.rollup?.relationPropertyId ?: Skip),
                "rollup_property_name" to (p.rollup?.rollupPropertyName ?: Skip),
                "rollup_property_id" to (p.rollup?.rollupPropertyId ?: Skip),
            )
            PropType.UNIQUE_ID -> obj("prefix" to (p.uniquePrefix?.ifBlank { null } ?: JsonNull))
            else -> obj()
        }
        return obj(
            "name" to (if (includeName) p.name else Skip),
            "description" to (p.description ?: Skip),
            p.type.api to config,
        )
    }

    private fun optionConfig(o: SelectOption): JsonObject = obj(
        "id" to (o.id ?: Skip),
        "name" to o.name,
        "color" to o.color.api,
        "description" to (o.description ?: Skip),
    )

    fun sorts(sorts: List<SortSpec>): JsonArray = JsonArray(sorts.map {
        if (it.timestamp != null) obj("timestamp" to it.timestamp, "direction" to if (it.ascending) "ascending" else "descending")
        else obj("property" to (it.property ?: ""), "direction" to if (it.ascending) "ascending" else "descending")
    })

    fun filter(node: FilterNode?): JsonObject? = when (node) {
        null -> null
        is FilterNode.Raw -> node.json
        is FilterNode.Group -> {
            val children = node.children.mapNotNull { filter(it) }
            if (children.isEmpty()) null else obj((if (node.and) "and" else "or") to JsonArray(children))
        }
        is FilterNode.Timestamp -> obj(
            "timestamp" to node.timestamp,
            node.timestamp to obj(node.op to node.value),
        )
        is FilterNode.Cond -> {
            val condition = obj(node.op to node.value)
            if (node.sub != null) obj("property" to node.property, node.kind to obj(node.sub to condition))
            else obj("property" to node.property, node.kind to condition)
        }
    }
}

object FilterJson {
    fun parse(json: JsonObject?): FilterNode? {
        if (json == null) return null
        json.a("and")?.let { arr -> return FilterNode.Group(true, arr.objs().mapNotNull(::parse)) }
        json.a("or")?.let { arr -> return FilterNode.Group(false, arr.objs().mapNotNull(::parse)) }
        json.s("timestamp")?.let { ts ->
            val inner = json.o(ts) ?: return FilterNode.Raw(json)
            val (op, value) = inner.entries.firstOrNull() ?: return FilterNode.Raw(json)
            return FilterNode.Timestamp(ts, op, value)
        }
        val property = json.s("property") ?: return FilterNode.Raw(json)
        val kindEntry = json.entries.firstOrNull { it.key != "property" && it.key != "type" } ?: return FilterNode.Raw(json)
        val inner = kindEntry.value as? JsonObject ?: return FilterNode.Raw(json)
        return when (kindEntry.key) {
            "formula" -> {
                val (sub, subInner) = inner.entries.firstOrNull() ?: return FilterNode.Raw(json)
                val condition = subInner as? JsonObject ?: return FilterNode.Raw(json)
                val (op, value) = condition.entries.firstOrNull() ?: return FilterNode.Raw(json)
                FilterNode.Cond(property, "formula", op, value, sub)
            }
            "rollup" -> FilterNode.Raw(json)
            else -> {
                val (op, value) = inner.entries.firstOrNull() ?: return FilterNode.Raw(json)
                FilterNode.Cond(property, kindEntry.key, op, value)
            }
        }
    }

    /** Operators that need no value. */
    val valueless = setOf(
        "is_empty", "is_not_empty", "this_week", "past_week", "past_month", "past_year", "next_week", "next_month", "next_year",
    )

    fun emptyValue(op: String): JsonElement = if (op in setOf("this_week", "past_week", "past_month", "past_year", "next_week", "next_month", "next_year")) {
        JsonObject(emptyMap())
    } else JsonPrimitive(true)
}
