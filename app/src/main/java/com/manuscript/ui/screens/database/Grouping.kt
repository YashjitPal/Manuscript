package com.manuscript.ui.screens.database

import androidx.compose.runtime.Immutable
import com.manuscript.core.model.FormulaValue
import com.manuscript.core.model.GroupBy
import com.manuscript.core.model.Hue
import com.manuscript.core.model.NDataSource
import com.manuscript.core.model.NPage
import com.manuscript.core.model.NUser
import com.manuscript.core.model.PropType
import com.manuscript.core.model.PropValue
import com.manuscript.core.model.PropertySchema
import com.manuscript.core.model.RollupValue
import com.manuscript.core.model.SelectOption
import com.manuscript.core.model.parseInstant
import com.manuscript.core.model.plainText
import com.manuscript.core.util.Fmt
import com.manuscript.ui.screens.properties.displayText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@Immutable
data class RowGroup(
    val key: String,
    val label: String,
    val hue: Hue = Hue.DEFAULT,
    val option: SelectOption? = null,
    val user: NUser? = null,
    val rows: List<NPage> = emptyList(),
    val empty: Boolean = false,
)

private val zone: ZoneId get() = ZoneId.systemDefault()

fun NPage.value(prop: PropertySchema): PropValue? = properties[prop.name] ?: properties.values.firstOrNull { it.id == prop.id }

fun NPage.dateOf(prop: PropertySchema): LocalDate? = when (val v = value(prop)) {
    is PropValue.DateV -> v.date?.startDate()
    is PropValue.CreatedTime -> parseInstant(v.time)?.atZone(zone)?.toLocalDate()
    is PropValue.LastEditedTime -> parseInstant(v.time)?.atZone(zone)?.toLocalDate()
    is PropValue.Formula -> (v.value as? FormulaValue.DateV)?.value?.startDate()
    is PropValue.Rollup -> (v.value as? RollupValue.DateV)?.value?.startDate()
    else -> null
}

fun NPage.endDateOf(prop: PropertySchema): LocalDate? = when (val v = value(prop)) {
    is PropValue.DateV -> v.date?.endDate() ?: v.date?.startDate()
    else -> dateOf(prop)
}

fun NPage.numberOf(prop: PropertySchema): Double? = when (val v = value(prop)) {
    is PropValue.Number -> v.value
    is PropValue.Formula -> (v.value as? FormulaValue.Num)?.value
    is PropValue.Rollup -> (v.value as? RollupValue.Num)?.value
    is PropValue.UniqueId -> v.number?.toDouble()
    is PropValue.Checkbox -> if (v.checked) 1.0 else 0.0
    else -> null
}

private fun dateBucket(date: LocalDate, mode: String?, startOfWeek: Int?): Pair<String, String> {
    val today = LocalDate.now()
    return when (mode) {
        "day" -> date.toString() to Fmt.friendlyDay(date)
        "week" -> {
            val first = if (startOfWeek == 0) DayOfWeek.SUNDAY else DayOfWeek.MONDAY
            val start = date.with(TemporalAdjusters.previousOrSame(first))
            start.toString() to "Week of ${start.format(DateTimeFormatter.ofPattern("MMM d"))}"
        }
        "month" -> date.withDayOfMonth(1).toString() to date.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        "year" -> date.year.toString() to date.year.toString()
        else -> {
            val days = ChronoUnit.DAYS.between(today, date)
            when {
                days == 0L -> "0" to "Today"
                days == 1L -> "1" to "Tomorrow"
                days == -1L -> "-1" to "Yesterday"
                days in 2..7 -> "7" to "Next 7 days"
                days in -7..-2 -> "-7" to "Last 7 days"
                days in 8..30 -> "30" to "Next 30 days"
                days in -30..-8 -> "-30" to "Last 30 days"
                days > 30 -> "future" to "Later"
                else -> "past" to "Older"
            }
        }
    }
}

/** Splits rows into groups for boards, grouped tables and chart axes. */
fun groupRows(
    rows: List<NPage>,
    group: GroupBy,
    ds: NDataSource,
    users: Map<String, NUser>,
    pageTitle: (String) -> String? = { null },
): List<RowGroup> {
    val prop = ds.prop(group.propertyId) ?: return listOf(RowGroup("all", "All", rows = rows))
    val buckets = LinkedHashMap<String, RowGroup>()
    val noValue = "__none__"
    fun add(key: String, make: () -> RowGroup, row: NPage) {
        val g = buckets[key] ?: make()
        buckets[key] = g.copy(rows = g.rows + row)
    }
    // Seed option-based groups in schema order so empty columns still show.
    when (prop.type) {
        PropType.SELECT, PropType.MULTI_SELECT -> prop.options.forEach { o -> buckets[o.name] = RowGroup(o.name, o.name, o.color, option = o) }
        PropType.STATUS -> if (group.groupBy == "group" && prop.statusGroups.isNotEmpty()) {
            prop.statusGroups.forEach { g -> buckets[g.id] = RowGroup(g.id, g.name, g.color) }
        } else prop.options.forEach { o -> buckets[o.name] = RowGroup(o.name, o.name, o.color, option = o) }
        PropType.CHECKBOX -> {
            buckets["true"] = RowGroup("true", "Checked", Hue.GREEN)
            buckets["false"] = RowGroup("false", "Unchecked", Hue.GRAY)
        }
        else -> Unit
    }
    rows.forEach { row ->
        when (val v = row.value(prop)) {
            is PropValue.Select -> v.option?.let { o -> add(o.name, { RowGroup(o.name, o.name, o.color, option = o) }, row) }
                ?: add(noValue, { RowGroup(noValue, "No ${prop.name}", empty = true) }, row)
            is PropValue.Status -> {
                val o = v.option
                if (o == null) add(noValue, { RowGroup(noValue, "No ${prop.name}", empty = true) }, row)
                else if (group.groupBy == "group" && prop.statusGroups.isNotEmpty()) {
                    val g = prop.groupOf(o) ?: prop.statusGroups.firstOrNull { it.name.equals(o.name, true) }
                    if (g != null) add(g.id, { RowGroup(g.id, g.name, g.color) }, row) else add(o.name, { RowGroup(o.name, o.name, o.color, option = o) }, row)
                } else add(o.name, { RowGroup(o.name, o.name, o.color, option = o) }, row)
            }
            is PropValue.MultiSelect -> if (v.options.isEmpty()) add(noValue, { RowGroup(noValue, "No ${prop.name}", empty = true) }, row)
            else v.options.forEach { o -> add(o.name, { RowGroup(o.name, o.name, o.color, option = o) }, row) }
            is PropValue.People -> if (v.users.isEmpty()) add(noValue, { RowGroup(noValue, "No ${prop.name}", empty = true) }, row)
            else v.users.forEach { u -> val user = users[u.id] ?: u; add(u.id, { RowGroup(u.id, user.displayName, user = user) }, row) }
            is PropValue.CreatedBy, is PropValue.LastEditedBy -> {
                val u = (v as? PropValue.CreatedBy)?.user ?: (v as PropValue.LastEditedBy).user
                if (u == null) add(noValue, { RowGroup(noValue, "Unknown", empty = true) }, row)
                else { val user = users[u.id] ?: u; add(u.id, { RowGroup(u.id, user.displayName, user = user) }, row) }
            }
            is PropValue.Checkbox -> add(v.checked.toString(), { RowGroup(v.checked.toString(), if (v.checked) "Checked" else "Unchecked") }, row)
            is PropValue.DateV, is PropValue.CreatedTime, is PropValue.LastEditedTime -> {
                val d = row.dateOf(prop)
                if (d == null) add(noValue, { RowGroup(noValue, "No date", empty = true) }, row)
                else { val (k, label) = dateBucket(d, group.groupBy, group.startDayOfWeek); add(k, { RowGroup(k, label) }, row) }
            }
            is PropValue.Number -> {
                val n = v.value
                if (n == null) add(noValue, { RowGroup(noValue, "No value", empty = true) }, row)
                else {
                    val size = group.rangeSize ?: 0.0
                    if (size > 0) {
                        val start = (group.rangeStart ?: 0.0) + Math.floor((n - (group.rangeStart ?: 0.0)) / size) * size
                        val key = Fmt.plainNumber(start)
                        add(key, { RowGroup(key, "${Fmt.plainNumber(start)} – ${Fmt.plainNumber(start + size)}") }, row)
                    } else add(Fmt.plainNumber(n), { RowGroup(Fmt.plainNumber(n), Fmt.number(n, prop.numberFormat)) }, row)
                }
            }
            is PropValue.Relation -> if (v.ids.isEmpty()) add(noValue, { RowGroup(noValue, "No ${prop.name}", empty = true) }, row)
            else v.ids.forEach { id -> add(id, { RowGroup(id, pageTitle(id)?.ifBlank { "Untitled" } ?: "Linked page") }, row) }
            is PropValue.Title, is PropValue.Text, is PropValue.Url, is PropValue.Email, is PropValue.Phone -> {
                val text = when (v) {
                    is PropValue.Title -> v.rich.plainText()
                    is PropValue.Text -> v.rich.plainText()
                    is PropValue.Url -> v.url ?: ""
                    is PropValue.Email -> v.email ?: ""
                    is PropValue.Phone -> v.phone ?: ""
                    else -> ""
                }.trim()
                val key = if (group.groupBy == "alphabet_prefix") text.take(1).uppercase() else text
                if (key.isEmpty()) add(noValue, { RowGroup(noValue, "Empty", empty = true) }, row) else add(key, { RowGroup(key, key) }, row)
            }
            is PropValue.Formula -> {
                val text = when (val f = v.value) {
                    is FormulaValue.Str -> f.value ?: ""
                    is FormulaValue.Num -> Fmt.plainNumber(f.value ?: 0.0)
                    is FormulaValue.Bool -> if (f.value == true) "True" else "False"
                    is FormulaValue.DateV -> f.value?.start ?: ""
                    FormulaValue.Unsupported -> ""
                }
                if (text.isEmpty()) add(noValue, { RowGroup(noValue, "Empty", empty = true) }, row) else add(text, { RowGroup(text, text) }, row)
            }
            else -> add(noValue, { RowGroup(noValue, "No value", empty = true) }, row)
        }
    }
    var result = buckets.values.toList()
    val none = result.firstOrNull { it.key == noValue }
    result = result.filter { it.key != noValue }
    result = when (group.sort) {
        "ascending" -> result.sortedBy { it.label.lowercase() }
        "descending" -> result.sortedByDescending { it.label.lowercase() }
        else -> result
    }
    if (prop.type == PropType.DATE || prop.type == PropType.CREATED_TIME || prop.type == PropType.LAST_EDITED_TIME) {
        fun dateRank(key: String): Long = when (key) {
            "past" -> -1000L
            "-30" -> -30L
            "-7" -> -7L
            "-1" -> -1L
            "0" -> 0L
            "1" -> 1L
            "7" -> 7L
            "30" -> 30L
            "future" -> 1000L
            else -> runCatching { LocalDate.parse(key).toEpochDay() }.getOrElse { key.toLongOrNull() ?: 0L }
        }
        result = if (group.sort == "descending") result.sortedByDescending { dateRank(it.key) } else result.sortedBy { dateRank(it.key) }
    }
    if (none != null) result = listOf(none) + result
    if (group.hideEmpty) result = result.filter { it.rows.isNotEmpty() }
    return result
}

/** Computes a chart or summary aggregation over rows. */
fun aggregate(rows: List<NPage>, aggregator: String, prop: PropertySchema?): Double {
    if (aggregator == "count" || prop == null) return rows.size.toDouble()
    val values = rows.mapNotNull { it.numberOf(prop) }
    fun isEmpty(row: NPage): Boolean = when (val v = row.value(prop)) {
        null -> true
        is PropValue.Title -> v.rich.isEmpty()
        is PropValue.Text -> v.rich.isEmpty()
        is PropValue.Number -> v.value == null
        is PropValue.Select -> v.option == null
        is PropValue.Status -> v.option == null
        is PropValue.MultiSelect -> v.options.isEmpty()
        is PropValue.DateV -> v.date == null
        is PropValue.People -> v.users.isEmpty()
        is PropValue.Relation -> v.ids.isEmpty()
        is PropValue.Files -> v.files.isEmpty()
        is PropValue.Checkbox -> !v.checked
        else -> false
    }
    val total = rows.size.toDouble()
    if (rows.isEmpty()) return 0.0
    return when (aggregator) {
        "count_values" -> rows.count { !isEmpty(it) }.toDouble()
        "unique" -> rows.mapNotNull { r -> r.value(prop)?.takeUnless { isEmpty(r) }?.displayText(prop) }.distinct().size.toDouble()
        "empty" -> rows.count(::isEmpty).toDouble()
        "not_empty" -> rows.count { !isEmpty(it) }.toDouble()
        "percent_empty" -> rows.count(::isEmpty) / total * 100
        "percent_not_empty" -> rows.count { !isEmpty(it) } / total * 100
        "sum" -> values.sum()
        "average" -> if (values.isEmpty()) 0.0 else values.average()
        "median" -> values.sorted().let { s -> if (s.isEmpty()) 0.0 else if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2 }
        "min" -> values.minOrNull() ?: 0.0
        "max" -> values.maxOrNull() ?: 0.0
        "range" -> if (values.isEmpty()) 0.0 else values.max() - values.min()
        "checked" -> rows.count { (it.value(prop) as? PropValue.Checkbox)?.checked == true }.toDouble()
        "unchecked" -> rows.count { (it.value(prop) as? PropValue.Checkbox)?.checked == false }.toDouble()
        "percent_checked" -> rows.count { (it.value(prop) as? PropValue.Checkbox)?.checked == true } / total * 100
        "percent_unchecked" -> rows.count { (it.value(prop) as? PropValue.Checkbox)?.checked == false } / total * 100
        "earliest_date", "latest_date", "date_range" -> {
            val dates = rows.mapNotNull { it.dateOf(prop) }.map { it.toEpochDay().toDouble() }
            when (aggregator) {
                "earliest_date" -> dates.minOrNull() ?: 0.0
                "latest_date" -> dates.maxOrNull() ?: 0.0
                else -> if (dates.isEmpty()) 0.0 else dates.max() - dates.min()
            }
        }
        else -> rows.size.toDouble()
    }
}

fun aggregatorLabel(aggregator: String): String = aggregator.replace('_', ' ').replaceFirstChar { it.uppercase() }
