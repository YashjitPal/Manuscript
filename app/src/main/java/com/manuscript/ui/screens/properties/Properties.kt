package com.manuscript.ui.screens.properties

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.ArrowDropDownCircle
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manuscript.core.json.obj
import com.manuscript.core.model.FormulaValue
import com.manuscript.core.model.NDate
import com.manuscript.core.model.NFile
import com.manuscript.core.model.NUser
import com.manuscript.core.model.NodeKind
import com.manuscript.core.model.PropType
import com.manuscript.core.model.PropValue
import com.manuscript.core.model.PropertySchema
import com.manuscript.core.model.RollupValue
import com.manuscript.core.model.SelectOption
import com.manuscript.core.model.plainText
import com.manuscript.core.model.toRef
import com.manuscript.core.util.Fmt
import com.manuscript.ui.LocalSession
import com.manuscript.ui.components.LinkHandler
import com.manuscript.ui.components.LocalLinkHandler
import com.manuscript.ui.components.LocalMessenger
import com.manuscript.ui.components.NotionIcon
import com.manuscript.ui.components.OptionChip
import com.manuscript.ui.components.RichTextView
import com.manuscript.ui.components.StatusDot
import com.manuscript.ui.components.UserAvatar
import com.manuscript.ui.screens.page.blocks.RichEditDialog
import com.manuscript.ui.theme.LocalHaptics
import com.manuscript.ui.theme.LocalNotionPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

fun propertyIcon(type: PropType): ImageVector = when (type) {
    PropType.TITLE -> Icons.AutoMirrored.Rounded.ShortText
    PropType.RICH_TEXT -> Icons.AutoMirrored.Rounded.Notes
    PropType.NUMBER -> Icons.Rounded.Numbers
    PropType.SELECT -> Icons.Rounded.ArrowDropDownCircle
    PropType.MULTI_SELECT -> Icons.Rounded.List
    PropType.STATUS -> Icons.Rounded.TaskAlt
    PropType.DATE -> Icons.Rounded.CalendarMonth
    PropType.PEOPLE -> Icons.Rounded.PersonOutline
    PropType.FILES -> Icons.Rounded.AttachFile
    PropType.CHECKBOX -> Icons.Rounded.CheckBox
    PropType.URL -> Icons.Rounded.Link
    PropType.EMAIL -> Icons.Rounded.AlternateEmail
    PropType.PHONE_NUMBER -> Icons.Rounded.Phone
    PropType.FORMULA -> Icons.Rounded.Functions
    PropType.RELATION -> Icons.AutoMirrored.Rounded.CallMade
    PropType.ROLLUP -> Icons.Rounded.Search
    PropType.CREATED_TIME, PropType.LAST_EDITED_TIME, PropType.LAST_VISITED_TIME -> Icons.Rounded.AccessTime
    PropType.CREATED_BY, PropType.LAST_EDITED_BY -> Icons.Rounded.Person
    PropType.UNIQUE_ID -> Icons.Rounded.Tag
    PropType.VERIFICATION -> Icons.Rounded.Verified
    PropType.BUTTON -> Icons.Rounded.SmartButton
    PropType.PLACE, PropType.LOCATION -> Icons.Rounded.Place
    PropType.UNKNOWN -> Icons.Rounded.Loop
}

fun PropValue.isEmpty(): Boolean = when (this) {
    is PropValue.Title -> rich.isEmpty()
    is PropValue.Text -> rich.isEmpty()
    is PropValue.Number -> value == null
    is PropValue.Select -> option == null
    is PropValue.MultiSelect -> options.isEmpty()
    is PropValue.Status -> option == null
    is PropValue.DateV -> date == null
    is PropValue.People -> users.isEmpty()
    is PropValue.Files -> files.isEmpty()
    is PropValue.Checkbox -> false
    is PropValue.Url -> url.isNullOrBlank()
    is PropValue.Email -> email.isNullOrBlank()
    is PropValue.Phone -> phone.isNullOrBlank()
    is PropValue.Relation -> ids.isEmpty()
    is PropValue.Formula -> when (val f = value) {
        is FormulaValue.Str -> f.value.isNullOrBlank()
        is FormulaValue.Num -> f.value == null
        is FormulaValue.Bool -> false
        is FormulaValue.DateV -> f.value == null
        FormulaValue.Unsupported -> true
    }
    is PropValue.Rollup -> false
    is PropValue.UniqueId -> number == null
    is PropValue.Place -> lat == null && name == null
    else -> false
}

/** A plain-text rendering of a value, used for sorting, sharing and accessibility. */
fun PropValue.displayText(schema: PropertySchema?, users: Map<String, NUser> = emptyMap()): String = when (this) {
    is PropValue.Title -> rich.plainText()
    is PropValue.Text -> rich.plainText()
    is PropValue.Number -> Fmt.number(value, schema?.numberFormat)
    is PropValue.Select -> option?.name ?: ""
    is PropValue.MultiSelect -> options.joinToString(", ") { it.name }
    is PropValue.Status -> option?.name ?: ""
    is PropValue.DateV -> Fmt.date(date)
    is PropValue.People -> this.users.joinToString(", ") { (users[it.id] ?: it).displayName }
    is PropValue.Files -> files.joinToString(", ") { it.displayName }
    is PropValue.Checkbox -> if (checked) "Yes" else "No"
    is PropValue.Url -> url ?: ""
    is PropValue.Email -> email ?: ""
    is PropValue.Phone -> phone ?: ""
    is PropValue.Formula -> when (val f = value) {
        is FormulaValue.Str -> f.value ?: ""
        is FormulaValue.Num -> Fmt.number(f.value, schema?.numberFormat)
        is FormulaValue.Bool -> if (f.value == true) "Yes" else "No"
        is FormulaValue.DateV -> Fmt.date(f.value)
        FormulaValue.Unsupported -> ""
    }
    is PropValue.Relation -> "${ids.size} linked"
    is PropValue.Rollup -> when (val r = value) {
        is RollupValue.Num -> Fmt.number(r.value, schema?.numberFormat)
        is RollupValue.DateV -> Fmt.date(r.value)
        is RollupValue.Items -> r.items.joinToString(", ") { it.displayText(null, users) }
        is RollupValue.Unsupported -> ""
    }
    is PropValue.CreatedTime -> Fmt.dateTime(time)
    is PropValue.LastEditedTime -> Fmt.dateTime(time)
    is PropValue.CreatedBy -> (users[user?.id] ?: user)?.displayName ?: ""
    is PropValue.LastEditedBy -> (users[user?.id] ?: user)?.displayName ?: ""
    is PropValue.UniqueId -> display
    is PropValue.Verification -> state?.replaceFirstChar { it.uppercase() } ?: "Unverified"
    is PropValue.Place -> name ?: address ?: ""
    is PropValue.Button -> ""
    is PropValue.Unknown -> ""
}

/** Compact, read-only rendering of a property value for cells, cards and rows. */
@Composable
fun PropertyValueView(
    value: PropValue?,
    schema: PropertySchema?,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    singleLine: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val session = LocalSession.current
    val users by session.cache.users.collectAsState()
    val palette = LocalNotionPalette.current
    val links: LinkHandler = LocalLinkHandler.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (value == null) return
    when (value) {
        is PropValue.Title -> RichTextView(value.rich, style.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), modifier, maxLines = if (singleLine) 1 else Int.MAX_VALUE)
        is PropValue.Text -> RichTextView(value.rich, style, modifier, maxLines = if (singleLine) 1 else 6)
        is PropValue.Select -> value.option?.let { OptionChip(it, modifier, small = true) }
        is PropValue.Status -> value.option?.let { opt ->
            val group = schema?.groupOf(opt)
            OptionChip(opt, modifier, small = true, leading = { StatusDot(group?.color ?: opt.color, size = 7.dp) })
        }
        is PropValue.MultiSelect -> if (singleLine) Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            value.options.take(3).forEach { OptionChip(it, small = true) }
            if (value.options.size > 3) Text("+${value.options.size - 3}", style = MaterialTheme.typography.labelMedium, color = muted)
        } else FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            value.options.forEach { OptionChip(it, small = true) }
        }
        is PropValue.People -> Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            value.users.take(if (singleLine) 3 else 20).forEach { u ->
                val user = users[u.id] ?: u
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(user, 20.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(user.displayName, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        is PropValue.CreatedBy, is PropValue.LastEditedBy -> {
            val u = (value as? PropValue.CreatedBy)?.user ?: (value as PropValue.LastEditedBy).user
            val user = users[u?.id] ?: u
            if (user != null) Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(user, 20.dp)
                Spacer(Modifier.width(4.dp))
                Text(user.displayName, style = style, maxLines = 1)
            }
        }
        is PropValue.Checkbox -> Icon(
            if (value.checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
            if (value.checked) "Checked" else "Unchecked",
            modifier.size(22.dp),
            tint = if (value.checked) MaterialTheme.colorScheme.primary else muted,
        )
        is PropValue.Url -> value.url?.let { url ->
            Text(url.removePrefix("https://").removePrefix("http://"), style = style.copy(textDecoration = TextDecoration.Underline), color = MaterialTheme.colorScheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier.clickable { links.openUrl(url) })
        }
        is PropValue.Email -> value.email?.let { e ->
            Text(e, style = style.copy(textDecoration = TextDecoration.Underline), color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = modifier.clickable { links.openUrl("mailto:$e") })
        }
        is PropValue.Phone -> value.phone?.let { p ->
            Text(p, style = style.copy(textDecoration = TextDecoration.Underline), color = MaterialTheme.colorScheme.primary, maxLines = 1,
                modifier = modifier.clickable { links.openUrl("tel:$p") })
        }
        is PropValue.Files -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            value.files.take(if (singleLine) 2 else 20).forEach { f ->
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.clickable { links.openUrl(f.url) }) {
                    Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AttachFile, null, Modifier.size(14.dp))
                        Text(f.displayName, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp).widthIn(max = 160.dp))
                    }
                }
            }
        }
        is PropValue.Relation -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            value.ids.take(if (singleLine) 2 else 30).forEach { id ->
                val page = session.cache.page(id)
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { links.openPage(id) }.padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NotionIcon(page?.icon, 16.dp, kind = NodeKind.PAGE)
                    Spacer(Modifier.width(4.dp))
                    Text(page?.title?.ifBlank { "Untitled" } ?: "Linked page", style = style.copy(textDecoration = TextDecoration.Underline), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (singleLine && value.ids.size > 2) Text("+${value.ids.size - 2}", style = MaterialTheme.typography.labelMedium, color = muted)
        }
        is PropValue.Verification -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Verified, null, Modifier.size(18.dp), tint = if (value.state == "verified") palette.accent(com.manuscript.core.model.Hue.BLUE) else muted)
            Spacer(Modifier.width(4.dp))
            Text(value.displayText(schema), style = style)
        }
        is PropValue.Button -> Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier) {
            Text(schema?.name ?: "Button", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }
        else -> {
            val text = value.displayText(schema, users)
            if (text.isNotEmpty()) Text(text, style = style, maxLines = if (singleLine) 1 else 4, overflow = TextOverflow.Ellipsis, modifier = modifier)
        }
    }
}

/** A labelled property row used in page headers. */
@Composable
fun PropertyRow(schema: PropertySchema, value: PropValue?, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Row(Modifier.width(150.dp).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(propertyIcon(schema.type), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(schema.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            if (value == null || value.isEmpty()) {
                Text(if (onClick != null) "Empty" else "—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
            } else PropertyValueView(value, schema, singleLine = false)
        }
    }
}

/** Builds an empty value of the right kind for a schema property. */
fun emptyValueFor(schema: PropertySchema): PropValue = when (schema.type) {
    PropType.TITLE -> PropValue.Title(schema.id, emptyList())
    PropType.RICH_TEXT -> PropValue.Text(schema.id, emptyList())
    PropType.NUMBER -> PropValue.Number(schema.id, null)
    PropType.SELECT -> PropValue.Select(schema.id, null)
    PropType.MULTI_SELECT -> PropValue.MultiSelect(schema.id, emptyList())
    PropType.STATUS -> PropValue.Status(schema.id, null)
    PropType.DATE -> PropValue.DateV(schema.id, null)
    PropType.PEOPLE -> PropValue.People(schema.id, emptyList())
    PropType.FILES -> PropValue.Files(schema.id, emptyList())
    PropType.CHECKBOX -> PropValue.Checkbox(schema.id, false)
    PropType.URL -> PropValue.Url(schema.id, null)
    PropType.EMAIL -> PropValue.Email(schema.id, null)
    PropType.PHONE_NUMBER -> PropValue.Phone(schema.id, null)
    PropType.RELATION -> PropValue.Relation(schema.id, emptyList(), false)
    PropType.VERIFICATION -> PropValue.Verification(schema.id, null, null, null)
    else -> PropValue.Unknown(schema.id, schema.rawType, obj())
}

/** Opens the right editor for a property; checkboxes toggle immediately. */
@Composable
fun PropertyEditor(
    schema: PropertySchema,
    value: PropValue?,
    onDismiss: () -> Unit,
    onSave: (PropValue) -> Unit,
) {
    val current = value ?: emptyValueFor(schema)
    when (schema.type) {
        PropType.TITLE, PropType.RICH_TEXT -> RichEditDialog(
            title = schema.name,
            initial = (current as? PropValue.Title)?.rich ?: (current as? PropValue.Text)?.rich ?: emptyList(),
            onDismiss = onDismiss,
        ) { rich -> onSave(if (schema.type == PropType.TITLE) PropValue.Title(schema.id, rich) else PropValue.Text(schema.id, rich)) }
        PropType.NUMBER, PropType.URL, PropType.EMAIL, PropType.PHONE_NUMBER -> SimpleTextEditor(schema, current, onDismiss, onSave)
        PropType.SELECT, PropType.STATUS, PropType.MULTI_SELECT -> OptionEditor(schema, current, onDismiss, onSave)
        PropType.DATE -> DateEditor(schema, (current as? PropValue.DateV)?.date, onDismiss) { onSave(PropValue.DateV(schema.id, it)) }
        PropType.PEOPLE -> PeopleEditor(schema, (current as? PropValue.People)?.users ?: emptyList(), onDismiss) { onSave(PropValue.People(schema.id, it)) }
        PropType.FILES -> FilesEditor(schema, (current as? PropValue.Files)?.files ?: emptyList(), onDismiss) { onSave(PropValue.Files(schema.id, it)) }
        PropType.RELATION -> RelationEditor(schema, (current as? PropValue.Relation)?.ids ?: emptyList(), onDismiss) { onSave(PropValue.Relation(schema.id, it, false)) }
        PropType.CHECKBOX -> LaunchedEffect(Unit) {
            onSave(PropValue.Checkbox(schema.id, !((current as? PropValue.Checkbox)?.checked ?: false)))
            onDismiss()
        }
        PropType.VERIFICATION -> VerificationEditor(schema, current as? PropValue.Verification, onDismiss, onSave)
        else -> ReadOnlyInfo(schema, current, onDismiss)
    }
}

@Composable
private fun SimpleTextEditor(schema: PropertySchema, value: PropValue, onDismiss: () -> Unit, onSave: (PropValue) -> Unit) {
    val initial = when (value) {
        is PropValue.Number -> value.value?.let { Fmt.plainNumber(it) } ?: ""
        is PropValue.Url -> value.url ?: ""
        is PropValue.Email -> value.email ?: ""
        is PropValue.Phone -> value.phone ?: ""
        else -> ""
    }
    var text by remember { mutableStateOf(initial) }
    val keyboard = when (schema.type) {
        PropType.NUMBER -> KeyboardType.Decimal
        PropType.URL -> KeyboardType.Uri
        PropType.EMAIL -> KeyboardType.Email
        else -> KeyboardType.Phone
    }
    val valid = schema.type != PropType.NUMBER || text.isBlank() || text.replace(",", "").toDoubleOrNull() != null
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(propertyIcon(schema.type), null) },
        title = { Text(schema.name) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = !valid,
                supportingText = if (schema.type == PropType.NUMBER && schema.numberFormat != null && valid && text.isNotBlank()) {
                    { Text(Fmt.number(text.replace(",", "").toDoubleOrNull(), schema.numberFormat)) }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val v = text.trim()
                onSave(
                    when (schema.type) {
                        PropType.NUMBER -> PropValue.Number(schema.id, v.replace(",", "").toDoubleOrNull())
                        PropType.URL -> PropValue.Url(schema.id, v.ifBlank { null })
                        PropType.EMAIL -> PropValue.Email(schema.id, v.ifBlank { null })
                        else -> PropValue.Phone(schema.id, v.ifBlank { null })
                    },
                )
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun OptionEditor(schema: PropertySchema, value: PropValue, onDismiss: () -> Unit, onSave: (PropValue) -> Unit) {
    val multi = schema.type == PropType.MULTI_SELECT
    val haptics = LocalHaptics.current
    var query by remember { mutableStateOf("") }
    var selected by remember {
        mutableStateOf(
            when (value) {
                is PropValue.MultiSelect -> value.options.map { it.name }.toSet()
                is PropValue.Select -> setOfNotNull(value.option?.name)
                is PropValue.Status -> setOfNotNull(value.option?.name)
                else -> emptySet()
            },
        )
    }
    val options = schema.options
    val filtered = options.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val canCreate = schema.type != PropType.STATUS && query.isNotBlank() && options.none { it.name.equals(query.trim(), ignoreCase = true) }
    fun commit(names: Set<String>) {
        val chosen = names.map { n -> options.firstOrNull { it.name == n } ?: SelectOption(name = n) }
        onSave(
            when (schema.type) {
                PropType.MULTI_SELECT -> PropValue.MultiSelect(schema.id, chosen)
                PropType.STATUS -> PropValue.Status(schema.id, chosen.firstOrNull())
                else -> PropValue.Select(schema.id, chosen.firstOrNull())
            },
        )
    }
    ModalBottomSheet(onDismissRequest = { if (multi) commit(selected); onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.imePadding().padding(bottom = 16.dp)) {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(propertyIcon(schema.type), null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(schema.name, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
                if (selected.isNotEmpty()) TextButton(onClick = { selected = emptySet(); if (!multi) { commit(emptySet()); onDismiss() } }) { Text("Clear") }
            }
            if (multi && selected.isNotEmpty()) {
                FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    selected.forEach { name ->
                        OptionChip(options.firstOrNull { it.name == name } ?: SelectOption(name = name), Modifier.clickable { selected = selected - name })
                    }
                }
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(if (schema.type == PropType.STATUS) "Search options" else "Search or create an option") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                if (canCreate) item {
                    ListItem(
                        headlineContent = { Text("Create “${query.trim()}”") },
                        leadingContent = { Icon(Icons.Rounded.Add, null) },
                        modifier = Modifier.clickable {
                            haptics.confirm()
                            val name = query.trim()
                            query = ""
                            if (multi) selected = selected + name else { commit(setOf(name)); onDismiss() }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                val grouped = if (schema.type == PropType.STATUS && schema.statusGroups.isNotEmpty()) {
                    schema.statusGroups.map { g -> g.name to filtered.filter { it.id in g.optionIds } } +
                        listOf("Other" to filtered.filter { o -> schema.statusGroups.none { o.id in it.optionIds } })
                } else listOf("" to filtered)
                grouped.forEach { (group, list) ->
                    if (list.isEmpty()) return@forEach
                    if (group.isNotEmpty()) item {
                        Text(group, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp))
                    }
                    items(list, key = { group + (it.id ?: it.name) }) { option ->
                        val isSelected = option.name in selected
                        ListItem(
                            headlineContent = { OptionChip(option) },
                            trailingContent = {
                                if (multi) Checkbox(checked = isSelected, onCheckedChange = null)
                                else if (isSelected) Icon(Icons.Rounded.TaskAlt, null, tint = MaterialTheme.colorScheme.primary)
                            },
                            supportingContent = option.description?.let { { Text(it) } },
                            modifier = Modifier.clickable {
                                haptics.tick()
                                if (multi) selected = if (isSelected) selected - option.name else selected + option.name
                                else { commit(setOf(option.name)); onDismiss() }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
            if (multi) {
                Button(
                    onClick = { commit(selected); onDismiss() },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Done") }
            }
        }
    }
}

private fun millisOf(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
fun DateEditor(schema: PropertySchema?, value: NDate?, onDismiss: () -> Unit, onSave: (NDate?) -> Unit) {
    var range by remember { mutableStateOf(value?.end != null) }
    var includeTime by remember { mutableStateOf(value?.hasTime == true) }
    var pickingTime by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val start = value?.startDateTime(zone)
    val end = value?.endDateTime(zone)
    var time by remember { mutableStateOf(start?.toLocalTime() ?: LocalTime.of(9, 0)) }
    val single = rememberDatePickerState(initialSelectedDateMillis = start?.toLocalDate()?.let(::millisOf) ?: millisOf(LocalDate.now()))
    val ranged = rememberDateRangePickerState(
        initialSelectedStartDateMillis = start?.toLocalDate()?.let(::millisOf),
        initialSelectedEndDateMillis = end?.toLocalDate()?.let(::millisOf),
    )
    fun format(date: LocalDate): String =
        if (includeTime) LocalDateTime.of(date, time).atZone(zone).toOffsetDateTime().toString() else date.toString()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val result = if (range) {
                    val s = ranged.selectedStartDateMillis?.let(::dateOf)
                    val e = ranged.selectedEndDateMillis?.let(::dateOf)
                    s?.let { NDate(format(it), e?.let(::format)) }
                } else single.selectedDateMillis?.let { NDate(format(dateOf(it))) }
                onSave(result)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(null); onDismiss() }) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    ) {
        Column {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("End date", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Switch(checked = range, onCheckedChange = { range = it })
            }
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Include time", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (includeTime) TextButton(onClick = { pickingTime = true }) { Text(time.toString()) }
                Switch(checked = includeTime, onCheckedChange = { includeTime = it; if (it) pickingTime = true })
            }
            if (range) DateRangePicker(ranged, Modifier.heightIn(max = 480.dp), title = null, showModeToggle = false)
            else DatePicker(single, title = null, showModeToggle = true)
        }
    }
    if (pickingTime) {
        val timeState = rememberTimePickerState(time.hour, time.minute)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pickingTime = false },
            confirmButton = { TextButton(onClick = { time = LocalTime.of(timeState.hour, timeState.minute); pickingTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text("Cancel") } },
            text = { TimePicker(timeState) },
        )
    }
}

@Composable
private fun PeopleEditor(schema: PropertySchema, value: List<NUser>, onDismiss: () -> Unit, onSave: (List<NUser>) -> Unit) {
    val session = LocalSession.current
    var selected by remember { mutableStateOf(value.map { it.id }.toSet()) }
    var query by remember { mutableStateOf("") }
    val all by produceState(session.users.users.value.values.toList()) {
        this.value = session.users.loadAll().ifEmpty { session.users.users.value.values.toList() }
    }
    val users = (all + value).distinctBy { it.id }.filter { !it.isGroup && (it.displayName.contains(query, true) || (it.email ?: "").contains(query, true)) }
        .sortedWith(compareBy({ it.isBot }, { it.displayName.lowercase() }))
    ModalBottomSheet(onDismissRequest = { onSave(users.filter { it.id in selected } + value.filter { it.id in selected && users.none { u -> u.id == it.id } }); onDismiss() }) {
        Column(Modifier.imePadding().padding(bottom = 16.dp)) {
            Text(schema.name, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(horizontal = 24.dp))
            TextField(
                value = query, onValueChange = { query = it }, placeholder = { Text("Search people") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true, shape = MaterialTheme.shapes.extraLarge,
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            if (session.users.listAvailable.collectAsState().value == false) {
                Text(
                    "Personal tokens can't list workspace members, so only people seen in your pages appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(users, key = { it.id }) { user ->
                    val checked = user.id in selected
                    ListItem(
                        headlineContent = { Text(user.displayName) },
                        supportingContent = (user.email ?: if (user.isBot) "Integration" else null)?.let { { Text(it) } },
                        leadingContent = { UserAvatar(user, 36.dp) },
                        trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                        modifier = Modifier.clickable { selected = if (checked) selected - user.id else selected + user.id },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            Button(
                onClick = { onSave(users.filter { it.id in selected }); onDismiss() },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) { Text("Done") }
        }
    }
}

@Composable
private fun FilesEditor(schema: PropertySchema, value: List<NFile>, onDismiss: () -> Unit, onSave: (List<NFile>) -> Unit) {
    val session = LocalSession.current
    val messenger = LocalMessenger.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf(value) }
    var uploading by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            uploading = true
            scope.launch {
                runCatching { session.uploads.upload(uri) }
                    .onSuccess { files = files + NFile("", it.filename, uploadId = it.uploadId) }
                    .onFailure { messenger.error(it) }
                uploading = false
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.imePadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(schema.name, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(horizontal = 8.dp))
            files.forEachIndexed { i, f ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AttachFile, null)
                        Spacer(Modifier.width(10.dp))
                        Text(f.displayName, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { files = files.filterIndexed { j, _ -> j != i } }) { Icon(Icons.Rounded.Close, "Remove") }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(link, { link = it }, placeholder = { Text("Paste a link") }, singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.large)
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { if (link.isNotBlank()) { files = files + NFile(link.trim()); link = "" } }, shapes = ButtonDefaults.shapes()) { Text("Add") }
            }
            FilledTonalButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !uploading, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                if (uploading) LoadingIndicator(Modifier.size(24.dp)) else {
                    Icon(Icons.Rounded.Upload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Upload a file")
                }
            }
            Button(onClick = { onSave(files); onDismiss() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }
}

@Composable
private fun RelationEditor(schema: PropertySchema, value: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    val session = LocalSession.current
    var selected by remember { mutableStateOf(value) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<com.manuscript.core.model.NPage>?>(null) }
    val targetId = schema.relation?.dataSourceId ?: schema.relation?.databaseId
    LaunchedEffect(query, targetId) {
        if (targetId == null) { results = emptyList(); return@LaunchedEffect }
        delay(if (query.isBlank()) 0 else 250)
        results = runCatching {
            val resolvedDsId = schema.relation?.dataSourceId
                ?: session.databases.resolve(targetId).second.firstOrNull()?.id
                ?: return@runCatching emptyList()
            val target = session.cache.storedDataSource(resolvedDsId) ?: session.databases.dataSource(resolvedDsId)
            val titleName = target.titleProperty?.name ?: "Name"
            val filter = if (query.isBlank()) null else obj("property" to titleName, "title" to obj("contains" to query.trim()))
            session.databases.query(resolvedDsId, filter, null, pageSize = 50).items
        }.getOrDefault(emptyList())
    }
    ModalBottomSheet(onDismissRequest = { onSave(selected); onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.imePadding().padding(bottom = 16.dp)) {
            Text(schema.name, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(horizontal = 24.dp))
            Text("${selected.size} linked", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
            TextField(
                value = query, onValueChange = { query = it }, placeholder = { Text("Search pages") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true, shape = MaterialTheme.shapes.extraLarge,
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            LazyColumn(Modifier.heightIn(min = 200.dp, max = 480.dp)) {
                if (results == null) item { LoadingIndicator(Modifier.padding(24.dp)) }
                val linked = selected.mapNotNull { id -> session.cache.page(id) }.filter { results?.none { r -> r.id == it.id } != false }
                items((linked + (results ?: emptyList())).distinctBy { it.id }, key = { it.id }) { page ->
                    val checked = page.id in selected
                    ListItem(
                        headlineContent = { Text(page.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { NotionIcon(page.icon, 24.dp) },
                        trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                        modifier = Modifier.clickable { selected = if (checked) selected - page.id else selected + page.id },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            Button(onClick = { onSave(selected); onDismiss() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("Done") }
        }
    }
}

@Composable
private fun VerificationEditor(schema: PropertySchema, value: PropValue.Verification?, onDismiss: () -> Unit, onSave: (PropValue) -> Unit) {
    val verified = value?.state == "verified"
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Verified, null) },
        title = { Text(schema.name) },
        text = { Text(if (verified) "This page is verified. Remove verification?" else "Mark this page as verified?") },
        confirmButton = {
            TextButton(onClick = {
                onSave(PropValue.Verification(schema.id, if (verified) "unverified" else "verified", null, null))
                onDismiss()
            }) { Text(if (verified) "Unverify" else "Verify") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ReadOnlyInfo(schema: PropertySchema, value: PropValue, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(propertyIcon(schema.type), null) },
        title = { Text(schema.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PropertyValueView(value, schema, singleLine = false)
                HorizontalDivider()
                Text(
                    when (schema.type) {
                        PropType.FORMULA -> "Formula: ${schema.formula ?: ""}"
                        PropType.ROLLUP -> "Rollup of ${schema.rollup?.rollupPropertyName ?: "a property"} via ${schema.rollup?.relationPropertyName ?: "a relation"} (${schema.rollup?.function?.replace('_', ' ')})"
                        else -> "${schema.type.label} values are calculated by Notion and can't be edited."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}