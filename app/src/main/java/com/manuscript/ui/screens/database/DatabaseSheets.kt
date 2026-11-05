package com.manuscript.ui.screens.database

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.ViewKanban
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manuscript.core.json.FilterJson
import com.manuscript.core.json.obj
import com.manuscript.core.model.FilterNode
import com.manuscript.core.model.GroupBy
import com.manuscript.core.model.Hue
import com.manuscript.core.model.NDataSource
import com.manuscript.core.model.NDate
import com.manuscript.core.model.NPage
import com.manuscript.core.model.NUser
import com.manuscript.core.model.NodeKind
import com.manuscript.core.model.PropType
import com.manuscript.core.model.PropertySchema
import com.manuscript.core.model.RelationConfig
import com.manuscript.core.model.RollupConfig
import com.manuscript.core.model.SelectOption
import com.manuscript.core.model.SortSpec
import com.manuscript.core.model.ViewProp
import com.manuscript.core.model.ViewType
import com.manuscript.core.util.Fmt
import com.manuscript.ui.components.OptionChip
import com.manuscript.ui.components.UserAvatar
import com.manuscript.ui.screens.pickers.PagePickerSheet
import com.manuscript.ui.screens.properties.DateEditor
import com.manuscript.ui.screens.properties.propertyIcon
import com.manuscript.ui.theme.LocalHaptics
import com.manuscript.ui.theme.LocalNotionPalette
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** The filter key Notion expects for a property type. */
fun filterKind(type: PropType): String? = when (type) {
    PropType.TITLE -> "title"
    PropType.RICH_TEXT -> "rich_text"
    PropType.URL -> "url"
    PropType.EMAIL -> "email"
    PropType.PHONE_NUMBER -> "phone_number"
    PropType.NUMBER -> "number"
    PropType.CHECKBOX -> "checkbox"
    PropType.SELECT -> "select"
    PropType.MULTI_SELECT -> "multi_select"
    PropType.STATUS -> "status"
    PropType.DATE -> "date"
    PropType.PEOPLE -> "people"
    PropType.FILES -> "files"
    PropType.RELATION -> "relation"
    PropType.FORMULA -> "formula"
    PropType.CREATED_TIME -> "created_time"
    PropType.LAST_EDITED_TIME -> "last_edited_time"
    PropType.CREATED_BY -> "created_by"
    PropType.LAST_EDITED_BY -> "last_edited_by"
    PropType.UNIQUE_ID -> "unique_id"
    else -> null
}

private val textOps = listOf("contains", "does_not_contain", "equals", "does_not_equal", "starts_with", "ends_with", "is_empty", "is_not_empty")
private val numberOps = listOf("equals", "does_not_equal", "greater_than", "less_than", "greater_than_or_equal_to", "less_than_or_equal_to", "is_empty", "is_not_empty")
private val dateOps = listOf("equals", "before", "after", "on_or_before", "on_or_after", "this_week", "past_week", "past_month", "past_year", "next_week", "next_month", "next_year", "is_empty", "is_not_empty")

fun operatorsFor(kind: String, sub: String? = null): List<String> = when (kind) {
    "title", "rich_text", "url", "email", "phone_number" -> textOps
    "number" -> numberOps
    "unique_id" -> numberOps.filter { !it.startsWith("is_") }
    "checkbox" -> listOf("equals", "does_not_equal")
    "select", "status" -> listOf("equals", "does_not_equal", "is_empty", "is_not_empty")
    "multi_select", "people", "created_by", "last_edited_by", "relation" -> listOf("contains", "does_not_contain", "is_empty", "is_not_empty")
    "date", "created_time", "last_edited_time" -> dateOps
    "files" -> listOf("is_empty", "is_not_empty")
    "formula" -> when (sub) {
        "number" -> numberOps
        "checkbox" -> listOf("equals", "does_not_equal")
        "date" -> dateOps
        else -> textOps
    }
    else -> listOf("is_empty", "is_not_empty")
}

fun opLabel(op: String): String = when (op) {
    "equals" -> "is"
    "does_not_equal" -> "is not"
    "contains" -> "contains"
    "does_not_contain" -> "does not contain"
    "starts_with" -> "starts with"
    "ends_with" -> "ends with"
    "is_empty" -> "is empty"
    "is_not_empty" -> "is not empty"
    "greater_than" -> ">"
    "less_than" -> "<"
    "greater_than_or_equal_to" -> "≥"
    "less_than_or_equal_to" -> "≤"
    "before" -> "is before"
    "after" -> "is after"
    "on_or_before" -> "is on or before"
    "on_or_after" -> "is on or after"
    else -> op.replace('_', ' ')
}

private val relativeDates = listOf("today" to "Today", "tomorrow" to "Tomorrow", "yesterday" to "Yesterday", "one_week_ago" to "One week ago",
    "one_week_from_now" to "One week from now", "one_month_ago" to "One month ago", "one_month_from_now" to "One month from now")

fun describe(node: FilterNode, ds: NDataSource): String = when (node) {
    is FilterNode.Cond -> {
        val name = ds.prop(node.property)?.name ?: node.property
        val value = when (val v = node.value) {
            is JsonArray -> v.joinToString(", ") { (it as? JsonPrimitive)?.contentOrNull ?: "" }
            is JsonPrimitive -> if (node.op in FilterJson.valueless) "" else v.contentOrNull ?: ""
            else -> ""
        }
        "$name ${opLabel(node.op)} ${relativeDates.firstOrNull { it.first == value }?.second ?: value}".trim()
    }
    is FilterNode.Timestamp -> "${if (node.timestamp == "created_time") "Created" else "Edited"} ${opLabel(node.op)}"
    is FilterNode.Group -> node.children.joinToString(if (node.and) " and " else " or ") { describe(it, ds) }
    is FilterNode.Raw -> "Advanced filter"
}

@Composable
fun FilterSheet(ds: NDataSource, current: FilterNode?, users: Map<String, NUser>, onDismiss: () -> Unit, onApply: (FilterNode?) -> Unit) {
    val initial = when (current) {
        null -> emptyList()
        is FilterNode.Group -> current.children
        else -> listOf(current)
    }
    var and by remember { mutableStateOf((current as? FilterNode.Group)?.and ?: true) }
    var conditions by remember { mutableStateOf(initial) }
    var adding by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.imePadding().padding(bottom = 16.dp)) {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FilterList, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Filters", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
                if (conditions.isNotEmpty()) TextButton(onClick = { conditions = emptyList() }) { Text("Clear all") }
            }
            if (conditions.size > 1) {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    ToggleButton(checked = and, onCheckedChange = { and = true }, shapes = ButtonGroupDefaults.connectedLeadingButtonShapes()) { Text("Match all") }
                    ToggleButton(checked = !and, onCheckedChange = { and = false }, shapes = ButtonGroupDefaults.connectedTrailingButtonShapes()) { Text("Match any") }
                }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                itemsIndexed(conditions) { index, node ->
                    ConditionCard(
                        node, ds, users,
                        onChange = { updated -> conditions = conditions.toMutableList().also { it[index] = updated } },
                        onRemove = { conditions = conditions.filterIndexed { i, _ -> i != index } },
                    )
                }
                item {
                    Box {
                        TextButton(onClick = { adding = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                            Icon(Icons.Rounded.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add a filter")
                        }
                        DropdownMenu(adding, onDismissRequest = { adding = false }) {
                            ds.properties.filter { filterKind(it.type) != null }.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.name) },
                                    leadingIcon = { Icon(propertyIcon(p.type), null) },
                                    onClick = {
                                        adding = false
                                        val kind = filterKind(p.type)!!
                                        val sub = if (kind == "formula") "string" else null
                                        val op = operatorsFor(kind, sub).first()
                                        conditions = conditions + FilterNode.Cond(p.id, kind, op, defaultValue(kind, op), sub)
                                    },
                                )
                            }
                            HorizontalDivider()
                            listOf("created_time" to "Created time", "last_edited_time" to "Last edited time").forEach { (ts, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    adding = false
                                    conditions = conditions + FilterNode.Timestamp(ts, "past_week", JsonObject(emptyMap()))
                                })
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = {
                    onApply(when (conditions.size) { 0 -> null; 1 -> conditions.first(); else -> FilterNode.Group(and, conditions) })
                    onDismiss()
                }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Apply") }
            }
        }
    }
}

fun defaultValue(kind: String, op: String): JsonElement = when {
    op in FilterJson.valueless -> FilterJson.emptyValue(op)
    kind == "checkbox" -> JsonPrimitive(true)
    kind == "date" || kind == "created_time" || kind == "last_edited_time" -> JsonPrimitive("today")
    else -> JsonPrimitive("")
}

@Composable
private fun ConditionCard(node: FilterNode, ds: NDataSource, users: Map<String, NUser>, onChange: (FilterNode) -> Unit, onRemove: () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (node) {
                is FilterNode.Raw, is FilterNode.Group -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(describe(node, ds), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Remove") }
                }
                is FilterNode.Timestamp -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (node.timestamp == "created_time") "Created time" else "Last edited time", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Remove") }
                    }
                    OperatorPicker(dateOps, node.op) { op -> onChange(node.copy(op = op, value = defaultValue("date", op))) }
                    if (node.op !in FilterJson.valueless) DateValue(node.value) { onChange(node.copy(value = it)) }
                }
                is FilterNode.Cond -> {
                    val prop = ds.prop(node.property)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        prop?.let { Icon(propertyIcon(it.type), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Spacer(Modifier.width(8.dp))
                        Text(prop?.name ?: node.property, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Remove") }
                    }
                    if (node.kind == "formula") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("string", "number", "checkbox", "date").forEach { sub ->
                                FilterChip(selected = node.sub == sub, onClick = {
                                    val op = operatorsFor("formula", sub).first()
                                    onChange(node.copy(sub = sub, op = op, value = defaultValue(sub, op)))
                                }, label = { Text(sub.replaceFirstChar { it.uppercase() }) })
                            }
                        }
                    }
                    OperatorPicker(operatorsFor(node.kind, node.sub), node.op) { op ->
                        val value = if (op in FilterJson.valueless) FilterJson.emptyValue(op) else if (node.op in FilterJson.valueless) defaultValue(node.sub ?: node.kind, op) else node.value
                        onChange(node.copy(op = op, value = value))
                    }
                    if (node.op !in FilterJson.valueless && prop != null) {
                        ValueEditor(node, prop, users) { onChange(node.copy(value = it)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OperatorPicker(ops: List<String>, current: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text(opLabel(current)) }, trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp)) }, shape = MaterialTheme.shapes.large)
        DropdownMenu(open, onDismissRequest = { open = false }) {
            ops.forEach { op -> DropdownMenuItem(text = { Text(opLabel(op)) }, onClick = { open = false; onPick(op) }) }
        }
    }
}

@Composable
private fun DateValue(value: JsonElement, onChange: (JsonElement) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val text = (value as? JsonPrimitive)?.contentOrNull ?: ""
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        relativeDates.forEach { (key, label) -> FilterChip(selected = text == key, onClick = { onChange(JsonPrimitive(key)) }, label = { Text(label) }) }
        FilterChip(
            selected = relativeDates.none { it.first == text } && text.isNotBlank(),
            onClick = { picking = true },
            label = { Text(if (relativeDates.none { it.first == text } && text.isNotBlank()) Fmt.date(NDate(text)) else "Exact date…") },
        )
    }
    if (picking) DateEditor(null, NDate(java.time.LocalDate.now().toString()), onDismiss = { picking = false }) { d ->
        d?.let { onChange(JsonPrimitive(it.start.take(10))) }
    }
}

@Composable
private fun ValueEditor(node: FilterNode.Cond, prop: PropertySchema, users: Map<String, NUser>, onChange: (JsonElement) -> Unit) {
    val kind = node.sub ?: node.kind
    val current = node.value
    when (kind) {
        "checkbox" -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Checked", modifier = Modifier.weight(1f))
            Switch(checked = (current as? JsonPrimitive)?.booleanOrNull ?: true, onCheckedChange = { onChange(JsonPrimitive(it)) })
        }
        "date", "created_time", "last_edited_time" -> DateValue(current, onChange)
        "select", "status", "multi_select" -> {
            val selected = when (current) {
                is JsonArray -> current.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
                is JsonPrimitive -> setOfNotNull(current.contentOrNull?.takeIf { it.isNotBlank() })
                else -> emptySet()
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                prop.options.forEach { o ->
                    val on = o.name in selected
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .border(2.dp, if (on) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable {
                                val next = if (on) selected - o.name else selected + o.name
                                onChange(if (next.size == 1) JsonPrimitive(next.first()) else JsonArray(next.map { JsonPrimitive(it) }))
                            }
                            .padding(2.dp),
                    ) { OptionChip(o) }
                }
            }
        }
        "people", "created_by", "last_edited_by" -> {
            val selected = (current as? JsonPrimitive)?.contentOrNull
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InputChip(selected = selected == "me", onClick = { onChange(JsonPrimitive("me")) }, label = { Text("Me") })
                users.values.filter { !it.isBot }.forEach { u ->
                    InputChip(selected = selected == u.id, onClick = { onChange(JsonPrimitive(u.id)) }, label = { Text(u.displayName) }, avatar = { UserAvatar(u, 20.dp) })
                }
            }
        }
        "relation" -> {
            var picking by remember { mutableStateOf(false) }
            val id = (current as? JsonPrimitive)?.contentOrNull
            AssistChip(onClick = { picking = true }, label = { Text(id?.takeIf { it.isNotBlank() }?.let { "Linked page selected" } ?: "Choose a page…") }, leadingIcon = { Icon(Icons.Rounded.Link, null) })
            if (picking) PagePickerSheet("Contains page", onDismiss = { picking = false }, onPick = { onChange(JsonPrimitive(it.id)); picking = false }, allowDatabases = false)
        }
        "number", "unique_id" -> {
            var text by remember(node) { mutableStateOf((current as? JsonPrimitive)?.contentOrNull ?: "") }
            OutlinedTextField(text, { text = it; it.toDoubleOrNull()?.let { n -> onChange(JsonPrimitive(n)) } }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large)
        }
        else -> {
            var text by remember(node.property, node.op) { mutableStateOf((current as? JsonPrimitive)?.contentOrNull ?: "") }
            OutlinedTextField(text, { text = it; onChange(JsonPrimitive(it)) }, singleLine = true, placeholder = { Text("Value") }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large)
        }
    }
}

@Composable
fun SortSheet(ds: NDataSource, current: List<SortSpec>, onDismiss: () -> Unit, onApply: (List<SortSpec>) -> Unit) {
    var sorts by remember { mutableStateOf(current) }
    var adding by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.Sort, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Sort", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
                if (sorts.isNotEmpty()) TextButton(onClick = { sorts = emptyList() }) { Text("Clear") }
            }
            sorts.forEachIndexed { i, s ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            s.timestamp?.let { if (it == "created_time") "Created time" else "Last edited time" } ?: ds.prop(s.property)?.name ?: s.property ?: "",
                            style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        FilterChip(selected = s.ascending, onClick = { sorts = sorts.toMutableList().also { it[i] = s.copy(ascending = !s.ascending) } },
                            label = { Text(if (s.ascending) "Ascending" else "Descending") },
                            leadingIcon = { Icon(if (s.ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, null, Modifier.size(16.dp)) })
                        IconButton(onClick = { if (i > 0) sorts = sorts.toMutableList().also { it.add(i - 1, it.removeAt(i)) } }, enabled = i > 0) { Icon(Icons.Rounded.ArrowUpward, "Move up") }
                        IconButton(onClick = { sorts = sorts.filterIndexed { j, _ -> j != i } }) { Icon(Icons.Rounded.Close, "Remove") }
                    }
                }
            }
            Box {
                TextButton(onClick = { adding = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add a sort")
                }
                DropdownMenu(adding, onDismissRequest = { adding = false }) {
                    ds.properties.forEach { p ->
                        DropdownMenuItem(text = { Text(p.name) }, leadingIcon = { Icon(propertyIcon(p.type), null) }, onClick = { adding = false; sorts = sorts + SortSpec(property = p.name) })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Created time") }, onClick = { adding = false; sorts = sorts + SortSpec(timestamp = "created_time") })
                    DropdownMenuItem(text = { Text("Last edited time") }, onClick = { adding = false; sorts = sorts + SortSpec(timestamp = "last_edited_time", ascending = false) })
                }
            }
            Button(onClick = { onApply(sorts); onDismiss() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("Apply") }
        }
    }
}

@Composable
fun PropertiesSheet(ds: NDataSource, visible: List<PropertySchema>, onDismiss: () -> Unit, onApply: (List<ViewProp>) -> Unit, onNewProperty: () -> Unit) {
    var order by remember { mutableStateOf((visible + ds.properties.filter { p -> visible.none { it.id == p.id } }).distinctBy { it.id }) }
    var shown by remember { mutableStateOf(visible.map { it.id }.toSet()) }
    fun commit() = onApply(order.map { ViewProp(it.id, it.name, it.id in shown || it.type == PropType.TITLE) })
    ModalBottomSheet(onDismissRequest = { commit(); onDismiss() }) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Properties", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
                TextButton(onClick = { shown = order.map { it.id }.toSet() }) { Text("Show all") }
            }
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                itemsIndexed(order, key = { _, p -> p.id }) { i, p ->
                    ListItem(
                        headlineContent = { Text(p.name) },
                        leadingContent = { Icon(propertyIcon(p.type), null) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { if (i > 0) order = order.toMutableList().also { it.add(i - 1, it.removeAt(i)) } }, enabled = i > 0) { Icon(Icons.Rounded.ArrowUpward, "Move up") }
                                Switch(checked = p.id in shown || p.type == PropType.TITLE, enabled = p.type != PropType.TITLE,
                                    onCheckedChange = { shown = if (it) shown + p.id else shown - p.id })
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            TextButton(onClick = { commit(); onNewProperty() }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("New property")
            }
        }
    }
}

private val groupable = setOf(PropType.SELECT, PropType.MULTI_SELECT, PropType.STATUS, PropType.PEOPLE, PropType.CHECKBOX, PropType.DATE, PropType.CREATED_TIME,
    PropType.LAST_EDITED_TIME, PropType.CREATED_BY, PropType.LAST_EDITED_BY, PropType.TITLE, PropType.RICH_TEXT, PropType.NUMBER, PropType.URL, PropType.FORMULA, PropType.RELATION)

fun groupTypeFor(type: PropType): String = when (type) {
    PropType.SELECT, PropType.MULTI_SELECT -> type.api
    PropType.STATUS -> "status"
    PropType.PEOPLE -> "person"
    PropType.CREATED_BY -> "created_by"
    PropType.LAST_EDITED_BY -> "last_edited_by"
    PropType.DATE, PropType.CREATED_TIME, PropType.LAST_EDITED_TIME -> type.api
    PropType.TITLE, PropType.RICH_TEXT, PropType.URL, PropType.EMAIL, PropType.PHONE_NUMBER -> "text"
    PropType.NUMBER -> "number"
    PropType.CHECKBOX -> "checkbox"
    PropType.RELATION -> "relation"
    PropType.FORMULA -> "formula"
    else -> type.api
}

@Composable
fun GroupSheet(ds: NDataSource, current: GroupBy?, required: Boolean, onDismiss: () -> Unit, onApply: (GroupBy?) -> Unit) {
    var group by remember { mutableStateOf(current) }
    val prop = ds.prop(group?.propertyId)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Group by", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(horizontal = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!required) FilterChip(selected = group == null, onClick = { group = null }, label = { Text("None") })
                ds.properties.filter { it.type in groupable }.forEach { p ->
                    FilterChip(
                        selected = group?.propertyId == p.id,
                        onClick = {
                            group = GroupBy(
                                groupTypeFor(p.type), p.id, p.name,
                                groupBy = when (p.type) {
                                    PropType.STATUS -> "option"
                                    PropType.DATE, PropType.CREATED_TIME, PropType.LAST_EDITED_TIME -> "relative"
                                    PropType.TITLE, PropType.RICH_TEXT, PropType.URL -> "exact"
                                    else -> null
                                },
                            )
                        },
                        label = { Text(p.name) },
                        leadingIcon = { Icon(propertyIcon(p.type), null, Modifier.size(16.dp)) },
                    )
                }
            }
            val g = group
            if (g != null && prop != null) {
                when (prop.type) {
                    PropType.STATUS -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = g.groupBy != "group", onClick = { group = g.copy(groupBy = "option") }, label = { Text("By option") })
                        FilterChip(selected = g.groupBy == "group", onClick = { group = g.copy(groupBy = "group") }, label = { Text("By group") })
                    }
                    PropType.DATE, PropType.CREATED_TIME, PropType.LAST_EDITED_TIME -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("relative", "day", "week", "month", "year").forEach { m ->
                            FilterChip(selected = g.groupBy == m, onClick = { group = g.copy(groupBy = m) }, label = { Text(m.replaceFirstChar { it.uppercase() }) })
                        }
                    }
                    PropType.TITLE, PropType.RICH_TEXT, PropType.URL -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = g.groupBy != "alphabet_prefix", onClick = { group = g.copy(groupBy = "exact") }, label = { Text("Exact") })
                        FilterChip(selected = g.groupBy == "alphabet_prefix", onClick = { group = g.copy(groupBy = "alphabet_prefix") }, label = { Text("First letter") })
                    }
                    else -> Unit
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("manual" to "Manual", "ascending" to "A → Z", "descending" to "Z → A").forEach { (s, label) ->
                        FilterChip(selected = g.sort == s, onClick = { group = g.copy(sort = s) }, label = { Text(label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hide empty groups", modifier = Modifier.weight(1f))
                    Switch(checked = g.hideEmpty, onCheckedChange = { group = g.copy(hideEmpty = it) })
                }
            }
            Button(onClick = { onApply(group); onDismiss() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Apply") }
        }
    }
}

/** Creates or edits a data source property. */
@Composable
fun PropertySchemaSheet(
    ds: NDataSource,
    original: PropertySchema?,
    allSources: List<NDataSource>,
    onDismiss: () -> Unit,
    onSave: (PropertySchema) -> Unit,
    onDelete: (PropertySchema) -> Unit,
) {
    val haptics = LocalHaptics.current
    var name by remember { mutableStateOf(original?.name ?: "") }
    var type by remember { mutableStateOf(original?.type ?: PropType.RICH_TEXT) }
    var description by remember { mutableStateOf(original?.description ?: "") }
    var format by remember { mutableStateOf(original?.numberFormat ?: "number") }
    var options by remember { mutableStateOf(original?.options ?: emptyList()) }
    var formula by remember { mutableStateOf(original?.formula ?: "") }
    var relation by remember { mutableStateOf(original?.relation) }
    var rollup by remember { mutableStateOf(original?.rollup) }
    var prefix by remember { mutableStateOf(original?.uniquePrefix ?: "") }
    var newOption by remember { mutableStateOf("") }
    var pickingRelation by remember { mutableStateOf(false) }
    var formatMenu by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.imePadding().fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
            item {
                Text(if (original == null) "New property" else "Edit property", style = MaterialTheme.typography.titleLargeEmphasized)
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large)
                Spacer(Modifier.size(12.dp))
                Text("Type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (if (original?.type == PropType.TITLE) listOf(PropType.TITLE) else PropType.creatable).forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t; haptics.segment() }, label = { Text(t.label) }, leadingIcon = { Icon(propertyIcon(t), null, Modifier.size(16.dp)) })
                    }
                }
                Spacer(Modifier.size(14.dp))
            }
            when (type) {
                PropType.NUMBER -> item {
                    Box {
                        AssistChip(onClick = { formatMenu = true }, label = { Text("Format: " + (Fmt.numberFormats.firstOrNull { it.first == format }?.second ?: format)) }, trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, null) })
                        DropdownMenu(formatMenu, onDismissRequest = { formatMenu = false }) {
                            Fmt.numberFormats.forEach { (key, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { format = key; formatMenu = false }) }
                        }
                    }
                }
                PropType.SELECT, PropType.MULTI_SELECT, PropType.STATUS -> {
                    item { Text("Options", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                    itemsIndexed(options, key = { i, o -> (o.id ?: "") + i }) { i, option ->
                        OptionEditorRow(
                            option,
                            onRename = { n -> options = options.toMutableList().also { it[i] = option.copy(name = n) } },
                            onColor = { h -> options = options.toMutableList().also { it[i] = option.copy(color = h) } },
                            onDelete = { options = options.filterIndexed { j, _ -> j != i } },
                            onUp = { if (i > 0) options = options.toMutableList().also { it.add(i - 1, it.removeAt(i)) } },
                        )
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                            OutlinedTextField(newOption, { newOption = it }, placeholder = { Text("Add an option") }, singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.large)
                            IconButton(onClick = {
                                if (newOption.isNotBlank()) {
                                    options = options + SelectOption(name = newOption.trim(), color = Hue.optionHues[(options.size + 2) % Hue.optionHues.size])
                                    newOption = ""
                                }
                            }) { Icon(Icons.Rounded.Add, "Add option") }
                        }
                        if (type == PropType.STATUS && original == null && options.isEmpty()) {
                            Text("Leave empty to use Not started, In progress and Done.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                PropType.FORMULA -> item {
                    OutlinedTextField(formula, { formula = it }, label = { Text("Formula") }, placeholder = { Text("prop(\"Price\") * prop(\"Quantity\")") },
                        textStyle = com.manuscript.ui.theme.LocalReading.current.code, minLines = 3, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large)
                }
                PropType.RELATION -> item {
                    val target = allSources.firstOrNull { it.id == relation?.dataSourceId }
                    AssistChip(
                        onClick = { pickingRelation = true },
                        label = { Text(target?.titleText?.let { "Related to $it" } ?: relation?.dataSourceId?.let { "Related database selected" } ?: "Choose a database…") },
                        leadingIcon = { Icon(Icons.Rounded.TableChart, null) },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Show on related database", modifier = Modifier.weight(1f))
                        Switch(checked = relation?.dual == true, onCheckedChange = { relation = (relation ?: RelationConfig(null, null, false)).copy(dual = it) })
                    }
                }
                PropType.ROLLUP -> item {
                    val relations = ds.properties.filter { it.type == PropType.RELATION }
                    Text("Relation", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        relations.forEach { r ->
                            FilterChip(selected = rollup?.relationPropertyName == r.name, onClick = {
                                rollup = (rollup ?: RollupConfig("show_original", null, null, null, null)).copy(relationPropertyName = r.name, relationPropertyId = r.id)
                            }, label = { Text(r.name) })
                        }
                    }
                    OutlinedTextField(rollup?.rollupPropertyName ?: "", { rollup = (rollup ?: RollupConfig("show_original", null, null, null, null)).copy(rollupPropertyName = it) },
                        label = { Text("Property to roll up") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Calculate", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("show_original", "count", "count_values", "unique", "sum", "average", "median", "min", "max", "percent_checked", "earliest_date", "latest_date").forEach { fn ->
                            FilterChip(selected = (rollup?.function ?: "show_original") == fn, onClick = { rollup = (rollup ?: RollupConfig(fn, null, null, null, null)).copy(function = fn) },
                                label = { Text(fn.replace('_', ' ')) })
                        }
                    }
                }
                PropType.UNIQUE_ID -> item {
                    OutlinedTextField(prefix, { prefix = it.uppercase() }, label = { Text("Prefix (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large)
                }
                else -> Unit
            }
            item {
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large)
                Spacer(Modifier.size(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (original != null && original.type != PropType.TITLE) {
                        OutlinedButton(onClick = { onDelete(original); onDismiss() }, shapes = ButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(6.dp))
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        enabled = name.isNotBlank() && (type != PropType.RELATION || relation?.dataSourceId != null),
                        onClick = {
                            onSave(
                                PropertySchema(
                                    id = original?.id ?: "", name = name.trim(), type = type, description = description.ifBlank { null },
                                    numberFormat = format, options = options, formula = formula, relation = relation, rollup = rollup, uniquePrefix = prefix,
                                ),
                            )
                            onDismiss()
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) { Text("Save") }
                }
                Spacer(Modifier.size(24.dp))
            }
        }
    }
    if (pickingRelation) PagePickerSheet(
        title = "Relate to…",
        onDismiss = { pickingRelation = false },
        allowPages = false,
        onPick = { ref ->
            pickingRelation = false
            val dsId = if (ref.kind == NodeKind.DATA_SOURCE) ref.id else allSources.firstOrNull { it.databaseId == (ref.databaseId ?: ref.id) }?.id ?: ref.id
            relation = RelationConfig(ref.databaseId ?: ref.id, dsId, relation?.dual ?: false)
        },
    )
}

@Composable
private fun OptionEditorRow(option: SelectOption, onRename: (String) -> Unit, onColor: (Hue) -> Unit, onDelete: () -> Unit, onUp: () -> Unit) {
    val palette = LocalNotionPalette.current
    var colors by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box {
            Box(Modifier.size(28.dp).clip(CircleShape).background(palette.option(option.color)).clickable { colors = true })
            DropdownMenu(colors, onDismissRequest = { colors = false }) {
                Hue.optionHues.forEach { h ->
                    DropdownMenuItem(text = { Text(h.label) }, leadingIcon = { Box(Modifier.size(18.dp).clip(CircleShape).background(palette.option(h))) }, onClick = { onColor(h); colors = false })
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        var text by remember(option.id, option.name) { mutableStateOf(option.name) }
        OutlinedTextField(text, { text = it; onRename(it) }, singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.large)
        IconButton(onClick = onUp) { Icon(Icons.Rounded.ArrowUpward, "Move up") }
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.Close, "Delete option") }
    }
}

/** Builds a sensible initial configuration for a new view of [type]. */
fun defaultConfig(type: ViewType, ds: NDataSource): JsonObject? {
    val status = ds.properties.firstOrNull { it.type == PropType.STATUS } ?: ds.properties.firstOrNull { it.type == PropType.SELECT }
    val date = ds.properties.firstOrNull { it.type == PropType.DATE }
    return when (type) {
        ViewType.BOARD -> status?.let { obj("type" to "board", "group_by" to obj("type" to groupTypeFor(it.type), "property_id" to it.id, "sort" to obj("type" to "manual")).let { g -> if (it.type == PropType.STATUS) JsonObject(g + ("group_by" to JsonPrimitive("option"))) else g }) }
        ViewType.CALENDAR -> date?.let { obj("type" to "calendar", "date_property_id" to it.id) }
        ViewType.TIMELINE -> date?.let { obj("type" to "timeline", "date_property_id" to it.id) }
        ViewType.GALLERY -> obj("type" to "gallery", "cover" to obj("type" to "page_cover"))
        ViewType.CHART -> status?.let { obj("type" to "chart", "chart_type" to "column", "x_axis" to obj("type" to groupTypeFor(it.type), "property_id" to it.id, "sort" to obj("type" to "manual")).let { g -> if (it.type == PropType.STATUS) JsonObject(g + ("group_by" to JsonPrimitive("option"))) else g }, "y_axis" to obj("aggregator" to "count")) }
            ?: obj("type" to "chart", "chart_type" to "number", "value" to obj("aggregator" to "count"))
        else -> obj("type" to type.api)
    }
}

@Composable
fun NewViewSheet(ds: NDataSource, onDismiss: () -> Unit, onCreate: (String, ViewType) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ViewType.TABLE) }
    val needsDate = (type == ViewType.CALENDAR || type == ViewType.TIMELINE) && ds.properties.none { it.type == PropType.DATE }
    val needsGroup = type == ViewType.BOARD && ds.properties.none { it.type == PropType.STATUS || it.type == PropType.SELECT }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("New view", style = MaterialTheme.typography.titleLargeEmphasized)
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, placeholder = { Text(type.label) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ViewType.TABLE, ViewType.BOARD, ViewType.LIST, ViewType.GALLERY, ViewType.CALENDAR, ViewType.TIMELINE, ViewType.CHART, ViewType.FORM).forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) }, leadingIcon = { Icon(viewIcon(t), null, Modifier.size(18.dp)) })
                }
            }
            if (needsDate) Text("Add a date property first to create this view.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (needsGroup) Text("Add a select or status property first to create a board.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onCreate(name, type); onDismiss() }, enabled = !needsDate && !needsGroup, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Create view") }
        }
    }
}

@Composable
fun RowMenuSheet(page: NPage, onDismiss: () -> Unit, onOpen: () -> Unit, onCopyLink: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(page.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(horizontal = 24.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        listOf(
            Triple("Open", Icons.AutoMirrored.Rounded.OpenInNew, onOpen),
            Triple("Copy link", Icons.Rounded.Link, onCopyLink),
            Triple("Duplicate", Icons.Rounded.ContentCopy, onDuplicate),
        ).forEach { (label, icon, action) ->
            ListItem(headlineContent = { Text(label) }, leadingContent = { Icon(icon, null) }, modifier = Modifier.clickable { onDismiss(); action() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        }
        ListItem(
            headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            leadingContent = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            modifier = Modifier.clickable { onDismiss(); onDelete() },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        Spacer(Modifier.size(24.dp))
    }
}

@Composable
fun ColumnMenuSheet(
    prop: PropertySchema,
    canGroup: Boolean,
    onDismiss: () -> Unit,
    onSort: (Boolean) -> Unit,
    onFilter: () -> Unit,
    onGroup: () -> Unit,
    onHide: () -> Unit,
    onEdit: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(propertyIcon(prop.type), null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(prop.name, style = MaterialTheme.typography.titleLargeEmphasized)
        }
        listOfNotNull(
            Triple("Sort ascending", Icons.Rounded.ArrowUpward) { onSort(true) },
            Triple("Sort descending", Icons.Rounded.ArrowDownward) { onSort(false) },
            Triple("Filter", Icons.Rounded.FilterList, onFilter),
            if (canGroup) Triple("Group by", Icons.Rounded.ViewAgenda, onGroup) else null,
            if (prop.type != PropType.TITLE) Triple("Hide in view", Icons.Rounded.VisibilityOff, onHide) else null,
            Triple("Edit property", Icons.Rounded.Edit, onEdit),
        ).forEach { (label, icon, action) ->
            ListItem(headlineContent = { Text(label) }, leadingContent = { Icon(icon, null) }, modifier = Modifier.clickable { onDismiss(); action() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        }
        Spacer(Modifier.size(24.dp))
    }
}

fun viewIcon(type: ViewType): ImageVector = when (type) {
    ViewType.TABLE -> Icons.Rounded.TableChart
    ViewType.BOARD -> Icons.Rounded.ViewKanban
    ViewType.LIST -> Icons.AutoMirrored.Rounded.List
    ViewType.GALLERY -> Icons.Rounded.GridView
    ViewType.CALENDAR -> Icons.Rounded.CalendarMonth
    ViewType.TIMELINE -> Icons.Rounded.Timeline
    ViewType.CHART -> Icons.Rounded.BarChart
    ViewType.FORM -> Icons.AutoMirrored.Rounded.Assignment
    ViewType.MAP -> Icons.Rounded.Map
    ViewType.DASHBOARD -> Icons.Rounded.Dashboard
}
