package com.manuscript.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Immutable
data class SelectOption(
    val id: String? = null,
    val name: String,
    val color: Hue = Hue.DEFAULT,
    val description: String? = null,
)

@Immutable
sealed interface FormulaValue {
    data class Str(val value: String?) : FormulaValue
    data class Num(val value: Double?) : FormulaValue
    data class Bool(val value: Boolean?) : FormulaValue
    data class DateV(val value: NDate?) : FormulaValue
    data object Unsupported : FormulaValue
}

@Immutable
sealed interface RollupValue {
    val function: String?
    data class Num(val value: Double?, override val function: String?) : RollupValue
    data class DateV(val value: NDate?, override val function: String?) : RollupValue
    data class Items(val items: List<PropValue>, override val function: String?) : RollupValue
    data class Unsupported(override val function: String?) : RollupValue
}

@Immutable
sealed interface PropValue {
    val id: String

    data class Title(override val id: String, val rich: List<RichText>) : PropValue
    data class Text(override val id: String, val rich: List<RichText>) : PropValue
    data class Number(override val id: String, val value: Double?) : PropValue
    data class Select(override val id: String, val option: SelectOption?) : PropValue
    data class MultiSelect(override val id: String, val options: List<SelectOption>) : PropValue
    data class Status(override val id: String, val option: SelectOption?) : PropValue
    data class DateV(override val id: String, val date: NDate?) : PropValue
    data class People(override val id: String, val users: List<NUser>) : PropValue
    data class Files(override val id: String, val files: List<NFile>) : PropValue
    data class Checkbox(override val id: String, val checked: Boolean) : PropValue
    data class Url(override val id: String, val url: String?) : PropValue
    data class Email(override val id: String, val email: String?) : PropValue
    data class Phone(override val id: String, val phone: String?) : PropValue
    data class Formula(override val id: String, val value: FormulaValue) : PropValue
    data class Relation(override val id: String, val ids: List<String>, val hasMore: Boolean) : PropValue
    data class Rollup(override val id: String, val value: RollupValue) : PropValue
    data class CreatedTime(override val id: String, val time: String?) : PropValue
    data class LastEditedTime(override val id: String, val time: String?) : PropValue
    data class CreatedBy(override val id: String, val user: NUser?) : PropValue
    data class LastEditedBy(override val id: String, val user: NUser?) : PropValue
    data class UniqueId(override val id: String, val prefix: String?, val number: Long?) : PropValue {
        val display: String get() = number?.let { if (prefix.isNullOrBlank()) "$it" else "$prefix-$it" } ?: ""
    }
    data class Verification(override val id: String, val state: String?, val date: NDate?, val verifiedBy: NUser?) : PropValue
    data class Button(override val id: String) : PropValue
    data class Place(override val id: String, val lat: Double?, val lon: Double?, val name: String?, val address: String?) : PropValue
    data class Unknown(override val id: String, val type: String, val raw: JsonObject) : PropValue
}

@Immutable
data class NPage(
    val id: String,
    val parent: Parent,
    val properties: Map<String, PropValue>,
    val icon: NIcon? = null,
    val cover: NFile? = null,
    val url: String? = null,
    val publicUrl: String? = null,
    val createdTime: String? = null,
    val lastEditedTime: String? = null,
    val createdBy: String? = null,
    val lastEditedBy: String? = null,
    val inTrash: Boolean = false,
    val isArchived: Boolean = false,
    val isLocked: Boolean = false,
) {
    val titleEntry: Map.Entry<String, PropValue.Title>?
        get() = properties.entries.firstOrNull { it.value is PropValue.Title }
            ?.let { @Suppress("UNCHECKED_CAST") (it as Map.Entry<String, PropValue.Title>) }
    val titleRich: List<RichText> get() = titleEntry?.value?.rich ?: emptyList()
    val title: String get() = titleRich.plainText()
    val isDatabaseRow: Boolean get() = parent is Parent.DataSource || parent is Parent.Database
    val dataSourceId: String?
        get() = when (val p = parent) {
            is Parent.DataSource -> p.id
            is Parent.Database -> p.id
            else -> null
        }
}

enum class PropType(val api: String, val label: String, val editable: Boolean = true) {
    TITLE("title", "Title"),
    RICH_TEXT("rich_text", "Text"),
    NUMBER("number", "Number"),
    SELECT("select", "Select"),
    MULTI_SELECT("multi_select", "Multi-select"),
    STATUS("status", "Status"),
    DATE("date", "Date"),
    PEOPLE("people", "Person"),
    FILES("files", "Files & media"),
    CHECKBOX("checkbox", "Checkbox"),
    URL("url", "URL"),
    EMAIL("email", "Email"),
    PHONE_NUMBER("phone_number", "Phone"),
    FORMULA("formula", "Formula", false),
    RELATION("relation", "Relation"),
    ROLLUP("rollup", "Rollup", false),
    CREATED_TIME("created_time", "Created time", false),
    CREATED_BY("created_by", "Created by", false),
    LAST_EDITED_TIME("last_edited_time", "Last edited time", false),
    LAST_EDITED_BY("last_edited_by", "Last edited by", false),
    UNIQUE_ID("unique_id", "ID", false),
    VERIFICATION("verification", "Verification"),
    BUTTON("button", "Button", false),
    PLACE("place", "Place", false),
    LOCATION("location", "Location", false),
    LAST_VISITED_TIME("last_visited_time", "Last visited", false),
    UNKNOWN("unknown", "Unsupported", false);

    companion object {
        fun of(api: String?): PropType = entries.firstOrNull { it.api == api } ?: UNKNOWN

        /** Types a user can add to a schema from the app. */
        val creatable = listOf(
            RICH_TEXT, NUMBER, SELECT, MULTI_SELECT, STATUS, DATE, PEOPLE, FILES, CHECKBOX, URL, EMAIL,
            PHONE_NUMBER, FORMULA, RELATION, ROLLUP, CREATED_TIME, CREATED_BY, LAST_EDITED_TIME, LAST_EDITED_BY,
            UNIQUE_ID, BUTTON, PLACE,
        )
    }
}

@Immutable
data class StatusGroup(val id: String, val name: String, val color: Hue, val optionIds: List<String>)

@Immutable
data class RelationConfig(
    val databaseId: String?,
    val dataSourceId: String?,
    val dual: Boolean,
    val syncedPropertyId: String? = null,
    val syncedPropertyName: String? = null,
)

@Immutable
data class RollupConfig(
    val function: String,
    val relationPropertyName: String?,
    val relationPropertyId: String?,
    val rollupPropertyName: String?,
    val rollupPropertyId: String?,
)

@Immutable
data class PropertySchema(
    val id: String,
    val name: String,
    val type: PropType,
    val rawType: String = type.api,
    val description: String? = null,
    val numberFormat: String? = null,
    val options: List<SelectOption> = emptyList(),
    val statusGroups: List<StatusGroup> = emptyList(),
    val formula: String? = null,
    val relation: RelationConfig? = null,
    val rollup: RollupConfig? = null,
    val uniquePrefix: String? = null,
) {
    fun groupOf(option: SelectOption?): StatusGroup? =
        option?.id?.let { oid -> statusGroups.firstOrNull { oid in it.optionIds } }
}

@Immutable
data class DataSourceRef(val id: String, val name: String)

@Immutable
data class NDatabase(
    val id: String,
    val title: List<RichText>,
    val description: List<RichText> = emptyList(),
    val icon: NIcon? = null,
    val cover: NFile? = null,
    val parent: Parent = Parent.Unknown,
    val url: String? = null,
    val publicUrl: String? = null,
    val isInline: Boolean = false,
    val inTrash: Boolean = false,
    val isLocked: Boolean = false,
    val dataSources: List<DataSourceRef> = emptyList(),
    val databaseType: String? = null,
    val createdTime: String? = null,
    val lastEditedTime: String? = null,
) {
    val titleText: String get() = title.plainText()
}

@Immutable
data class NDataSource(
    val id: String,
    val title: List<RichText>,
    val description: List<RichText> = emptyList(),
    val icon: NIcon? = null,
    val cover: NFile? = null,
    val parent: Parent = Parent.Unknown,
    val databaseParent: Parent? = null,
    val isInline: Boolean = false,
    val inTrash: Boolean = false,
    val properties: List<PropertySchema> = emptyList(),
    val url: String? = null,
    val publicUrl: String? = null,
    val databaseType: String? = null,
    val createdTime: String? = null,
    val lastEditedTime: String? = null,
    val lastEditedBy: String? = null,
) {
    val titleText: String get() = title.plainText()
    val databaseId: String?
        get() = when (val p = parent) {
            is Parent.Database -> p.id
            is Parent.DataSource -> p.databaseId
            else -> null
        }
    val titleProperty: PropertySchema? get() = properties.firstOrNull { it.type == PropType.TITLE }

    fun prop(idOrName: String?): PropertySchema? = idOrName?.let { key ->
        properties.firstOrNull { it.id == key } ?: properties.firstOrNull { it.name == key }
            ?: runCatching { java.net.URLDecoder.decode(key, "UTF-8") }.getOrNull()
                ?.let { decoded -> properties.firstOrNull { it.id == decoded || it.name == decoded } }
    }
}

enum class ViewType(val api: String, val label: String) {
    TABLE("table", "Table"),
    BOARD("board", "Board"),
    LIST("list", "List"),
    CALENDAR("calendar", "Calendar"),
    TIMELINE("timeline", "Timeline"),
    GALLERY("gallery", "Gallery"),
    FORM("form", "Form"),
    CHART("chart", "Chart"),
    MAP("map", "Map"),
    DASHBOARD("dashboard", "Dashboard");

    companion object {
        fun of(api: String?): ViewType = entries.firstOrNull { it.api == api } ?: TABLE
    }
}

@Immutable
data class ViewProp(
    val propertyId: String,
    val name: String? = null,
    val visible: Boolean = true,
    val width: Int? = null,
    val wrap: Boolean? = null,
    val statusShowAs: String? = null,
    val dateFormat: String? = null,
    val timeFormat: String? = null,
)

@Immutable
data class GroupBy(
    val type: String,
    val propertyId: String,
    val propertyName: String? = null,
    val groupBy: String? = null,
    val sort: String = "manual",
    val hideEmpty: Boolean = false,
    val startDayOfWeek: Int? = null,
    val rangeStart: Double? = null,
    val rangeEnd: Double? = null,
    val rangeSize: Double? = null,
)

@Immutable
data class Aggregation(val aggregator: String, val propertyId: String? = null)

@Immutable
data class ChartConfig(
    val chartType: String = "column",
    val xAxis: GroupBy? = null,
    val yAxis: Aggregation? = null,
    val value: Aggregation? = null,
    val xAxisPropertyId: String? = null,
    val yAxisPropertyId: String? = null,
    val sort: String? = null,
    val colorTheme: String? = null,
    val height: String? = null,
    val hideEmptyGroups: Boolean = false,
    val legendPosition: String? = null,
    val showDataLabels: Boolean = false,
    val cumulative: Boolean = false,
    val smoothLine: Boolean = false,
    val hideLineFillArea: Boolean = false,
    val groupStyle: String? = null,
    val donutLabels: String? = null,
    val stackBy: GroupBy? = null,
    val caption: String? = null,
    val yAxisMin: Double? = null,
    val yAxisMax: Double? = null,
    val colorByValue: Boolean = false,
)

@Immutable
data class DashboardWidget(val id: String, val viewId: String, val width: Int, val rowIndex: Int)

@Immutable
data class ViewConfig(
    val properties: List<ViewProp> = emptyList(),
    val groupBy: GroupBy? = null,
    val subGroupBy: GroupBy? = null,
    val coverType: String? = null,
    val coverPropertyId: String? = null,
    val coverSize: String? = null,
    val coverAspect: String? = null,
    val cardLayout: String? = null,
    val datePropertyId: String? = null,
    val endDatePropertyId: String? = null,
    val viewRange: String? = null,
    val showWeekends: Boolean? = null,
    val wrapCells: Boolean? = null,
    val frozenColumns: Int? = null,
    val showVerticalLines: Boolean? = null,
    val zoom: String? = null,
    val showTable: Boolean? = null,
    val mapBy: String? = null,
    val chart: ChartConfig? = null,
    val dashboard: List<DashboardWidget> = emptyList(),
    val formClosed: Boolean? = null,
    val raw: JsonObject? = null,
)

@Immutable
data class SortSpec(val property: String? = null, val timestamp: String? = null, val ascending: Boolean = true)

@Immutable
data class NView(
    val id: String,
    val databaseId: String?,
    val dataSourceId: String?,
    val name: String,
    val type: ViewType,
    val filter: JsonObject? = null,
    val sorts: List<SortSpec> = emptyList(),
    val quickFilters: JsonObject? = null,
    val config: ViewConfig = ViewConfig(),
    val url: String? = null,
    val dashboardViewId: String? = null,
)

/** Filter tree used by the filter builder. Converts to and from Notion's filter JSON. */
@Immutable
sealed interface FilterNode {
    data class Group(val and: Boolean, val children: List<FilterNode>) : FilterNode
    data class Cond(
        val property: String,
        val kind: String,
        val op: String,
        val value: JsonElement,
        val sub: String? = null,
    ) : FilterNode
    data class Timestamp(val timestamp: String, val op: String, val value: JsonElement) : FilterNode
    data class Raw(val json: JsonObject) : FilterNode
}

@Immutable
data class NTemplate(val id: String, val name: String, val isDefault: Boolean)

@Immutable
data class NCustomEmoji(val id: String, val name: String, val url: String)

@Immutable
data class NFileUpload(
    val id: String,
    val status: String,
    val filename: String? = null,
    val contentType: String? = null,
    val uploadUrl: String? = null,
    val expiry: String? = null,
)

@Immutable
data class NComment(
    val id: String,
    val discussionId: String,
    val parentPageId: String? = null,
    val parentBlockId: String? = null,
    val createdTime: String? = null,
    val lastEditedTime: String? = null,
    val createdBy: String? = null,
    val rich: List<RichText> = emptyList(),
    val displayName: String? = null,
    val attachments: List<NFile> = emptyList(),
)

/** A lightweight row used in lists, the sidebar tree and search results. */
@Immutable
data class NodeRef(
    val id: String,
    val kind: NodeKind,
    val title: String,
    val icon: NIcon? = null,
    val cover: NFile? = null,
    val parent: Parent = Parent.Unknown,
    val lastEditedTime: String? = null,
    val lastEditedBy: String? = null,
    val createdTime: String? = null,
    val url: String? = null,
    val inTrash: Boolean = false,
    val databaseId: String? = null,
)

enum class NodeKind { PAGE, DATABASE, DATA_SOURCE }

fun NPage.toRef() = NodeRef(
    id = id, kind = NodeKind.PAGE, title = title, icon = icon, cover = cover, parent = parent,
    lastEditedTime = lastEditedTime, lastEditedBy = lastEditedBy, createdTime = createdTime, url = url, inTrash = inTrash,
)

fun NDataSource.toRef() = NodeRef(
    id = id, kind = NodeKind.DATA_SOURCE, title = titleText, icon = icon, cover = cover,
    parent = databaseParent ?: parent, lastEditedTime = lastEditedTime, lastEditedBy = lastEditedBy,
    createdTime = createdTime, url = url, inTrash = inTrash, databaseId = databaseId,
)

fun NDatabase.toRef() = NodeRef(
    id = id, kind = NodeKind.DATABASE, title = titleText, icon = icon, cover = cover, parent = parent,
    lastEditedTime = lastEditedTime, createdTime = createdTime, url = url, inTrash = inTrash, databaseId = id,
)
