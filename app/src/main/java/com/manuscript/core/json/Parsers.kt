package com.manuscript.core.json

import com.manuscript.core.model.Aggregation
import com.manuscript.core.model.Annotations
import com.manuscript.core.model.BlockType
import com.manuscript.core.model.ChartConfig
import com.manuscript.core.model.DashboardWidget
import com.manuscript.core.model.DataSourceRef
import com.manuscript.core.model.FormulaValue
import com.manuscript.core.model.GroupBy
import com.manuscript.core.model.Hue
import com.manuscript.core.model.MeetingInfo
import com.manuscript.core.model.Mention
import com.manuscript.core.model.NBlock
import com.manuscript.core.model.NColor
import com.manuscript.core.model.NComment
import com.manuscript.core.model.NCustomEmoji
import com.manuscript.core.model.NDataSource
import com.manuscript.core.model.NDatabase
import com.manuscript.core.model.NDate
import com.manuscript.core.model.NFile
import com.manuscript.core.model.NFileUpload
import com.manuscript.core.model.NIcon
import com.manuscript.core.model.NPage
import com.manuscript.core.model.NTemplate
import com.manuscript.core.model.NUser
import com.manuscript.core.model.NView
import com.manuscript.core.model.Parent
import com.manuscript.core.model.PropType
import com.manuscript.core.model.PropValue
import com.manuscript.core.model.PropertySchema
import com.manuscript.core.model.RelationConfig
import com.manuscript.core.model.RichText
import com.manuscript.core.model.RollupConfig
import com.manuscript.core.model.RollupValue
import com.manuscript.core.model.SelectOption
import com.manuscript.core.model.SortSpec
import com.manuscript.core.model.StatusGroup
import com.manuscript.core.model.ViewConfig
import com.manuscript.core.model.ViewProp
import com.manuscript.core.model.ViewType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

object Parse {

    fun richText(arr: JsonArray?): List<RichText> = arr?.objs()?.map(::richTextItem) ?: emptyList()

    fun richTextItem(o: JsonObject): RichText {
        val ann = o.o("annotations")?.let(::annotations) ?: Annotations.None
        val plain = o.s("plain_text")
        return when (o.s("type")) {
            "mention" -> {
                val m = o.o("mention") ?: JsonObject(emptyMap())
                RichText(text = plain ?: "", annotations = ann, link = o.s("href"), mention = mention(m))
            }
            "equation" -> {
                val expr = o.o("equation")?.s("expression") ?: plain ?: ""
                RichText(text = plain ?: expr, annotations = ann, equation = expr)
            }
            else -> {
                val t = o.o("text")
                RichText(
                    text = plain ?: t?.s("content") ?: "",
                    annotations = ann,
                    link = t?.o("link")?.s("url") ?: o.s("href"),
                )
            }
        }
    }

    fun annotations(o: JsonObject) = Annotations(
        bold = o.b("bold") == true,
        italic = o.b("italic") == true,
        strikethrough = o.b("strikethrough") == true,
        underline = o.b("underline") == true,
        code = o.b("code") == true,
        color = NColor.parse(o.s("color")),
    )

    fun mention(m: JsonObject): Mention {
        val type = m.s("type") ?: ""
        val body = m.o(type)
        return when (type) {
            "user" -> {
                val u = body?.let(::user)
                Mention.User(u?.id ?: "", u?.name, u?.avatarUrl)
            }
            "page" -> Mention.Page(body?.s("id") ?: "")
            "database" -> Mention.Database(body?.s("id") ?: "")
            "date" -> body?.let(::date)?.let { Mention.Date(it) } ?: Mention.Unknown(type, m)
            "link_preview" -> Mention.LinkPreview(body?.s("url") ?: "")
            "link_mention" -> Mention.LinkMention(
                href = body?.s("href") ?: "",
                title = body?.s("title"),
                iconUrl = body?.s("icon_url"),
                provider = body?.s("link_provider"),
                thumbnailUrl = body?.s("thumbnail_url"),
            )
            "template_mention" -> {
                when (body?.s("type")) {
                    "template_mention_date" -> Mention.TemplateDate(body.s("template_mention_date") ?: "today")
                    "template_mention_user" -> Mention.TemplateUser(body.s("template_mention_user") ?: "me")
                    else -> Mention.Unknown(type, m)
                }
            }
            "custom_emoji" -> Mention.CustomEmoji(body?.s("id") ?: "", body?.s("name"), body?.s("url"))
            else -> Mention.Unknown(type, m)
        }
    }

    fun date(o: JsonObject): NDate? = o.s("start")?.let { NDate(it, o.s("end"), o.s("time_zone")) }

    fun icon(o: JsonObject?): NIcon? {
        if (o == null) return null
        return when (o.s("type")) {
            "emoji" -> o.s("emoji")?.let { NIcon.Emoji(it) }
            "external" -> o.o("external")?.s("url")?.let { url ->
                nativeIconFromUrl(url) ?: NIcon.External(url)
            }
            "file" -> o.o("file")?.let { f -> f.s("url")?.let { NIcon.Hosted(it, f.s("expiry_time")) } }
            "custom_emoji" -> o.o("custom_emoji")?.let { NIcon.CustomEmoji(it.s("id") ?: "", it.s("name"), it.s("url")) }
            "icon" -> o.o("icon")?.let { NIcon.Native(it.s("name") ?: "document", it.s("color") ?: "gray") }
            else -> null
        }
    }

    private fun nativeIconFromUrl(url: String): NIcon.Native? {
        val match = Regex("""notion\.so/icons/([a-z0-9-]+)_([a-z]+)\.svg""").find(url) ?: return null
        return NIcon.Native(match.groupValues[1].replace('-', '_'), match.groupValues[2])
    }

    fun file(o: JsonObject?): NFile? {
        if (o == null) return null
        val name = o.s("name")
        return when (o.s("type")) {
            "external" -> o.o("external")?.s("url")?.let { NFile(it, name) }
            "file" -> o.o("file")?.let { f -> f.s("url")?.let { NFile(it, name, hosted = true, expiry = f.s("expiry_time")) } }
            "file_upload" -> o.o("file_upload")?.s("id")?.let { NFile("", name, uploadId = it) }
            else -> null
        }
    }

    fun parent(o: JsonObject?): Parent {
        if (o == null) return Parent.Unknown
        return when (o.s("type")) {
            "workspace" -> Parent.Workspace
            "page_id" -> Parent.Page(o.s("page_id") ?: "")
            "database_id" -> Parent.Database(o.s("database_id") ?: "")
            "data_source_id" -> Parent.DataSource(o.s("data_source_id") ?: "", o.s("database_id"))
            "block_id" -> Parent.Block(o.s("block_id") ?: "")
            "agent_id" -> Parent.Agent(o.s("agent_id") ?: "")
            else -> Parent.Unknown
        }
    }

    fun user(o: JsonObject): NUser {
        val type = o.s("type") ?: if (o.s("object") == "group") "group" else null
        val bot = o.o("bot")
        val owner = bot?.o("owner")?.takeIf { it.s("type") == "user" }?.o("user")?.let(::user)
        return NUser(
            id = o.s("id") ?: "",
            name = o.s("name"),
            avatarUrl = o.s("avatar_url"),
            type = type,
            email = o.o("person")?.s("email"),
            workspaceName = bot?.s("workspace_name"),
            workspaceId = bot?.s("workspace_id"),
            isGroup = o.s("object") == "group",
            maxUploadBytes = bot?.o("workspace_limits")?.l("max_file_upload_size_in_bytes"),
            ownerUser = owner,
        )
    }

    fun selectOption(o: JsonObject) = SelectOption(
        id = o.s("id"),
        name = o.s("name") ?: "",
        color = Hue.of(o.s("color")),
        description = o.s("description"),
    )

    fun page(o: JsonObject): NPage {
        val props = LinkedHashMap<String, PropValue>()
        o.o("properties")?.forEach { (name, v) -> (v as? JsonObject)?.let { props[name] = propValue(it) } }
        return NPage(
            id = o.s("id") ?: "",
            parent = parent(o.o("parent")),
            properties = props,
            icon = icon(o.o("icon")),
            cover = file(o.o("cover")),
            url = o.s("url"),
            publicUrl = o.s("public_url"),
            createdTime = o.s("created_time"),
            lastEditedTime = o.s("last_edited_time"),
            createdBy = o.o("created_by")?.s("id"),
            lastEditedBy = o.o("last_edited_by")?.s("id"),
            inTrash = o.b("in_trash") ?: o.b("archived") ?: false,
            isArchived = o.b("is_archived") ?: false,
            isLocked = o.b("is_locked") ?: false,
        )
    }

    fun propValue(o: JsonObject): PropValue {
        val id = o.s("id") ?: ""
        val type = o.s("type") ?: ""
        return when (type) {
            "title" -> PropValue.Title(id, richText(o.a("title")))
            "rich_text" -> PropValue.Text(id, richText(o.a("rich_text")))
            "number" -> PropValue.Number(id, o.d("number"))
            "select" -> PropValue.Select(id, o.o("select")?.let(::selectOption))
            "multi_select" -> PropValue.MultiSelect(id, o.a("multi_select")?.objs()?.map(::selectOption) ?: emptyList())
            "status" -> PropValue.Status(id, o.o("status")?.let(::selectOption))
            "date" -> PropValue.DateV(id, o.o("date")?.let(::date))
            "people" -> PropValue.People(id, o.a("people")?.objs()?.map(::user) ?: emptyList())
            "files" -> PropValue.Files(id, o.a("files")?.objs()?.mapNotNull(::file) ?: emptyList())
            "checkbox" -> PropValue.Checkbox(id, o.b("checkbox") == true)
            "url" -> PropValue.Url(id, o.s("url"))
            "email" -> PropValue.Email(id, o.s("email"))
            "phone_number" -> PropValue.Phone(id, o.s("phone_number"))
            "formula" -> PropValue.Formula(id, formula(o.o("formula")))
            "relation" -> PropValue.Relation(
                id, o.a("relation")?.objs()?.mapNotNull { it.s("id") } ?: emptyList(), o.b("has_more") == true,
            )
            "rollup" -> PropValue.Rollup(id, rollup(o.o("rollup")))
            "created_time" -> PropValue.CreatedTime(id, o.s("created_time"))
            "last_edited_time" -> PropValue.LastEditedTime(id, o.s("last_edited_time"))
            "created_by" -> PropValue.CreatedBy(id, o.o("created_by")?.let(::user))
            "last_edited_by" -> PropValue.LastEditedBy(id, o.o("last_edited_by")?.let(::user))
            "unique_id" -> o.o("unique_id").let { PropValue.UniqueId(id, it?.s("prefix"), it?.l("number")) }
            "verification" -> o.o("verification").let { v ->
                PropValue.Verification(id, v?.s("state"), v?.o("date")?.let(::date), v?.o("verified_by")?.let(::user))
            }
            "button" -> PropValue.Button(id)
            "place" -> o.o("place").let { p -> PropValue.Place(id, p?.d("lat"), p?.d("lon"), p?.s("name"), p?.s("address")) }
            else -> PropValue.Unknown(id, type, o)
        }
    }

    private fun formula(o: JsonObject?): FormulaValue = when (o?.s("type")) {
        "string" -> FormulaValue.Str(o.s("string"))
        "number" -> FormulaValue.Num(o.d("number"))
        "boolean" -> FormulaValue.Bool(o.b("boolean"))
        "date" -> FormulaValue.DateV(o.o("date")?.let(::date))
        else -> FormulaValue.Unsupported
    }

    private fun rollup(o: JsonObject?): RollupValue {
        val fn = o?.s("function")
        return when (o?.s("type")) {
            "number" -> RollupValue.Num(o.d("number"), fn)
            "date" -> RollupValue.DateV(o.o("date")?.let(::date), fn)
            "array" -> RollupValue.Items(o.a("array")?.objs()?.map(::propValue) ?: emptyList(), fn)
            else -> RollupValue.Unsupported(fn)
        }
    }

    fun propertySchema(name: String, o: JsonObject): PropertySchema {
        val rawType = o.s("type") ?: "unknown"
        val type = PropType.of(rawType)
        val body = o.o(rawType)
        return PropertySchema(
            id = o.s("id") ?: name,
            name = o.s("name") ?: name,
            type = type,
            rawType = rawType,
            description = o.s("description"),
            numberFormat = body?.s("format"),
            options = body?.a("options")?.objs()?.map(::selectOption) ?: emptyList(),
            statusGroups = body?.a("groups")?.objs()?.map { g ->
                StatusGroup(
                    id = g.s("id") ?: "",
                    name = g.s("name") ?: "",
                    color = Hue.of(g.s("color")),
                    optionIds = g.a("option_ids")?.mapNotNull { it.asString() } ?: emptyList(),
                )
            } ?: emptyList(),
            formula = body?.s("expression"),
            relation = if (type == PropType.RELATION && body != null) RelationConfig(
                databaseId = body.s("database_id"),
                dataSourceId = body.s("data_source_id"),
                dual = body.s("type") == "dual_property",
                syncedPropertyId = body.o("dual_property")?.s("synced_property_id"),
                syncedPropertyName = body.o("dual_property")?.s("synced_property_name"),
            ) else null,
            rollup = if (type == PropType.ROLLUP && body != null) RollupConfig(
                function = body.s("function") ?: "show_original",
                relationPropertyName = body.s("relation_property_name"),
                relationPropertyId = body.s("relation_property_id"),
                rollupPropertyName = body.s("rollup_property_name"),
                rollupPropertyId = body.s("rollup_property_id"),
            ) else null,
            uniquePrefix = if (type == PropType.UNIQUE_ID) body?.s("prefix") else null,
        )
    }

    fun database(o: JsonObject) = NDatabase(
        id = o.s("id") ?: "",
        title = richText(o.a("title")),
        description = richText(o.a("description")),
        icon = icon(o.o("icon")),
        cover = file(o.o("cover")),
        parent = parent(o.o("parent")),
        url = o.s("url"),
        publicUrl = o.s("public_url"),
        isInline = o.b("is_inline") == true,
        inTrash = o.b("in_trash") ?: o.b("archived") ?: false,
        isLocked = o.b("is_locked") == true,
        dataSources = o.a("data_sources")?.objs()?.map { DataSourceRef(it.s("id") ?: "", it.s("name") ?: "") } ?: emptyList(),
        databaseType = o.s("database_type"),
        createdTime = o.s("created_time"),
        lastEditedTime = o.s("last_edited_time"),
    )

    fun dataSource(o: JsonObject): NDataSource {
        val props = o.o("properties")?.mapNotNull { (name, v) -> (v as? JsonObject)?.let { propertySchema(name, it) } }
            ?: emptyList()
        return NDataSource(
            id = o.s("id") ?: "",
            title = richText(o.a("title")),
            description = richText(o.a("description")),
            icon = icon(o.o("icon")),
            cover = file(o.o("cover")),
            parent = parent(o.o("parent")),
            databaseParent = o.o("database_parent")?.let(::parent),
            isInline = o.b("is_inline") == true,
            inTrash = o.b("in_trash") ?: o.b("archived") ?: false,
            properties = props,
            url = o.s("url"),
            publicUrl = o.s("public_url"),
            databaseType = o.s("database_type"),
            createdTime = o.s("created_time"),
            lastEditedTime = o.s("last_edited_time"),
            lastEditedBy = o.o("last_edited_by")?.s("id"),
        )
    }

    fun block(o: JsonObject): NBlock {
        val rawType = o.s("type") ?: "unknown"
        val type = BlockType.of(rawType)
        val body = o.o(rawType) ?: JsonObject(emptyMap())
        val base = NBlock(
            id = o.s("id") ?: "",
            type = type,
            rawType = rawType,
            parent = parent(o.o("parent")),
            hasChildren = o.b("has_children") == true,
            inTrash = o.b("in_trash") ?: o.b("archived") ?: false,
            createdTime = o.s("created_time"),
            lastEditedTime = o.s("last_edited_time"),
            createdBy = o.o("created_by")?.s("id"),
            lastEditedBy = o.o("last_edited_by")?.s("id"),
            rich = richText(body.a("rich_text")),
            color = NColor.parse(body.s("color")),
            icon = icon(body.o("icon")),
            caption = richText(body.a("caption")),
        )
        val children = o.a("_children")?.objs()?.map(::block) ?: emptyList()
        val withBody = when (type) {
            BlockType.HEADING_1, BlockType.HEADING_2, BlockType.HEADING_3, BlockType.HEADING_4 ->
                base.copy(toggleable = body.b("is_toggleable") == true)
            BlockType.TO_DO -> base.copy(checked = body.b("checked") == true)
            BlockType.NUMBERED -> base.copy(listStart = body.i("list_start_index"), listFormat = body.s("list_format"))
            BlockType.CODE -> base.copy(language = body.s("language") ?: "plain text")
            BlockType.EQUATION -> base.copy(expression = body.s("expression") ?: "")
            BlockType.IMAGE, BlockType.VIDEO, BlockType.AUDIO, BlockType.PDF, BlockType.FILE ->
                base.copy(file = file(body), url = file(body)?.url)
            BlockType.BOOKMARK, BlockType.EMBED, BlockType.LINK_PREVIEW ->
                base.copy(url = body.s("url") ?: file(body)?.url, file = file(body))
            BlockType.CHILD_PAGE, BlockType.CHILD_DATABASE -> base.copy(title = body.s("title") ?: "")
            BlockType.LINK_TO_PAGE -> base.copy(
                linkPageId = body.s("page_id"),
                linkDatabaseId = body.s("database_id"),
            )
            BlockType.TABLE -> base.copy(
                tableWidth = body.i("table_width") ?: 1,
                hasColumnHeader = body.b("has_column_header") == true,
                hasRowHeader = body.b("has_row_header") == true,
            )
            BlockType.TABLE_ROW -> base.copy(
                cells = body.a("cells")?.map { cell -> richText(cell.asArr()) } ?: emptyList(),
            )
            BlockType.COLUMN -> base.copy(widthRatio = body.d("width_ratio")?.toFloat())
            BlockType.SYNCED_BLOCK -> base.copy(syncedFrom = body.o("synced_from")?.s("block_id"))
            BlockType.MEETING_NOTES -> base.copy(
                meeting = MeetingInfo(
                    status = body.s("status"),
                    summaryBlockId = body.o("children")?.s("summary_block_id"),
                    notesBlockId = body.o("children")?.s("notes_block_id"),
                    transcriptBlockId = body.o("children")?.s("transcript_block_id"),
                    startTime = body.o("calendar_event")?.s("start_time"),
                    endTime = body.o("calendar_event")?.s("end_time"),
                    attendees = body.o("calendar_event")?.a("attendees")?.mapNotNull { it.asString() } ?: emptyList(),
                ),
                title = body.s("title"),
            )
                BlockType.UNSUPPORTED -> base.copy(unsupportedType = body.s("block_type") ?: rawType)
            else -> base
        }
        return withBody.copy(children = children)
    }

    fun comment(o: JsonObject): NComment {
        val parent = o.o("parent")
        return NComment(
            id = o.s("id") ?: "",
            discussionId = o.s("discussion_id") ?: "",
            parentPageId = parent?.s("page_id"),
            parentBlockId = parent?.s("block_id"),
            createdTime = o.s("created_time"),
            lastEditedTime = o.s("last_edited_time"),
            createdBy = o.o("created_by")?.s("id"),
            rich = richText(o.a("rich_text")),
            displayName = o.o("display_name")?.s("resolved_name"),
            attachments = o.a("attachments")?.objs()?.mapNotNull { a ->
                a.o("file")?.s("url")?.let { NFile(it, a.s("category"), hosted = true) }
            } ?: emptyList(),
        )
    }

    fun fileUpload(o: JsonObject) = NFileUpload(
        id = o.s("id") ?: "",
        status = o.s("status") ?: "pending",
        filename = o.s("filename"),
        contentType = o.s("content_type"),
        uploadUrl = o.s("upload_url"),
        expiry = o.s("expiry_time"),
    )

    fun template(o: JsonObject) = NTemplate(o.s("id") ?: "", o.s("name") ?: "Untitled", o.b("is_default") == true)

    fun customEmoji(o: JsonObject) = NCustomEmoji(o.s("id") ?: "", o.s("name") ?: "", o.s("url") ?: "")

    fun sorts(arr: JsonArray?): List<SortSpec> = arr?.objs()?.map {
        SortSpec(property = it.s("property"), timestamp = it.s("timestamp"), ascending = it.s("direction") != "descending")
    } ?: emptyList()

    fun groupBy(o: JsonObject?): GroupBy? {
        if (o == null) return null
        val pid = o.s("property_id") ?: return null
        return GroupBy(
            type = o.s("type") ?: "select",
            propertyId = pid,
            propertyName = o.s("property_name"),
            groupBy = o.s("group_by") ?: o.o("group_by")?.s("group_by"),
            sort = o.o("sort")?.s("type") ?: "manual",
            hideEmpty = o.b("hide_empty_groups") == true,
            startDayOfWeek = o.i("start_day_of_week"),
            rangeStart = o.d("range_start"),
            rangeEnd = o.d("range_end"),
            rangeSize = o.d("range_size"),
        )
    }

    private fun aggregation(o: JsonObject?): Aggregation? =
        o?.s("aggregator")?.let { Aggregation(it, o.s("property_id")) }

    fun viewConfig(o: JsonObject?): ViewConfig {
        if (o == null) return ViewConfig()
        val props = o.a("properties")?.objs()?.map {
            ViewProp(
                propertyId = it.s("property_id") ?: "",
                name = it.s("property_name"),
                visible = it.b("visible") ?: true,
                width = it.i("width"),
                wrap = it.b("wrap"),
                statusShowAs = it.s("status_show_as"),
                dateFormat = it.s("date_format"),
                timeFormat = it.s("time_format"),
            )
        } ?: emptyList()
        val chart = if (o.s("type") == "chart") ChartConfig(
            chartType = o.s("chart_type") ?: "column",
            xAxis = groupBy(o.o("x_axis")),
            yAxis = aggregation(o.o("y_axis")),
            value = aggregation(o.o("value")),
            xAxisPropertyId = o.s("x_axis_property_id"),
            yAxisPropertyId = o.s("y_axis_property_id"),
            sort = o.s("sort"),
            colorTheme = o.s("color_theme"),
            height = o.s("height"),
            hideEmptyGroups = o.b("hide_empty_groups") == true,
            legendPosition = o.s("legend_position"),
            showDataLabels = o.b("show_data_labels") == true,
            cumulative = o.b("cumulative") == true,
            smoothLine = o.b("smooth_line") == true,
            hideLineFillArea = o.b("hide_line_fill_area") == true,
            groupStyle = o.s("group_style"),
            donutLabels = o.s("donut_labels"),
            stackBy = groupBy(o.o("stack_by")),
            caption = o.s("caption"),
            yAxisMin = o.d("y_axis_min"),
            yAxisMax = o.d("y_axis_max"),
            colorByValue = o.b("color_by_value") == true,
        ) else null
        val dashboard = o.a("rows")?.objs()?.flatMapIndexed { rowIndex, row ->
            row.a("widgets")?.objs()?.mapNotNull { w ->
                w.s("view_id")?.let { DashboardWidget(w.s("id") ?: it, it, w.i("width") ?: 12, w.i("row_index") ?: rowIndex) }
            } ?: emptyList()
        } ?: emptyList()
        return ViewConfig(
            properties = props,
            groupBy = groupBy(o.o("group_by")),
            subGroupBy = groupBy(o.o("sub_group_by")),
            coverType = o.o("cover")?.s("type"),
            coverPropertyId = o.o("cover")?.s("property_id"),
            coverSize = o.s("cover_size"),
            coverAspect = o.s("cover_aspect"),
            cardLayout = o.s("card_layout"),
            datePropertyId = o.s("date_property_id"),
            endDatePropertyId = o.s("end_date_property_id"),
            viewRange = o.s("view_range"),
            showWeekends = o.b("show_weekends"),
            wrapCells = o.b("wrap_cells"),
            frozenColumns = o.i("frozen_column_index"),
            showVerticalLines = o.b("show_vertical_lines"),
            zoom = o.o("preference")?.s("zoom_level"),
            showTable = o.b("show_table"),
            mapBy = o.s("map_by"),
            chart = chart,
            dashboard = dashboard,
            formClosed = o.b("is_form_closed"),
            raw = o,
        )
    }

    fun view(o: JsonObject) = NView(
        id = o.s("id") ?: "",
        databaseId = o.o("parent")?.s("database_id"),
        dataSourceId = o.s("data_source_id"),
        name = o.s("name")?.ifBlank { null } ?: "Untitled view",
        type = ViewType.of(o.s("type")),
        filter = o.o("filter"),
        sorts = sorts(o.a("sorts")),
        quickFilters = o.o("quick_filters"),
        config = viewConfig(o.o("configuration")),
        url = o.s("url"),
        dashboardViewId = o.s("dashboard_view_id"),
    )
}
