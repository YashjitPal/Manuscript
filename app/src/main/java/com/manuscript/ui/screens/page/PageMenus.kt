package com.manuscript.ui.screens.page

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.rounded.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.Toc
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manuscript.core.model.BlockType
import com.manuscript.core.model.Hue
import com.manuscript.core.model.Mention
import com.manuscript.core.model.NBlock
import com.manuscript.core.model.NColor
import com.manuscript.core.model.NDate
import com.manuscript.core.model.NodeKind
import com.manuscript.core.model.NodeRef
import com.manuscript.core.model.NUser
import com.manuscript.ui.LocalSession
import com.manuscript.ui.components.NotionIcon
import com.manuscript.ui.components.UserAvatar
import com.manuscript.ui.screens.page.editor.Attrs
import com.manuscript.ui.screens.page.editor.Mark
import com.manuscript.ui.screens.page.editor.RichEditState
import com.manuscript.ui.theme.LocalHaptics
import com.manuscript.ui.theme.LocalNotionPalette
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.OffsetDateTime

@Immutable
data class SlashItem(
    val id: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
    val section: String,
    val keywords: String = "",
)

val SlashItems: List<SlashItem> = listOf(
    SlashItem("paragraph", "Text", "Just start writing with plain text", Icons.Rounded.TextFields, "Basic blocks", "plain paragraph"),
    SlashItem("heading_1", "Heading 1", "Big section heading", Icons.Rounded.Title, "Basic blocks", "h1 title"),
    SlashItem("heading_2", "Heading 2", "Medium section heading", Icons.Rounded.Title, "Basic blocks", "h2 subtitle"),
    SlashItem("heading_3", "Heading 3", "Small section heading", Icons.Rounded.Title, "Basic blocks", "h3"),
    SlashItem("heading_4", "Heading 4", "Smallest section heading", Icons.Rounded.Title, "Basic blocks", "h4"),
    SlashItem("to_do", "To-do list", "Track tasks with a checklist", Icons.Rounded.CheckBox, "Basic blocks", "todo checkbox task"),
    SlashItem("bulleted_list_item", "Bulleted list", "Create a simple bulleted list", Icons.AutoMirrored.Rounded.FormatListBulleted, "Basic blocks", "bullet unordered"),
    SlashItem("numbered_list_item", "Numbered list", "Create a list with numbering", Icons.Rounded.FormatListNumbered, "Basic blocks", "ordered number"),
    SlashItem("toggle", "Toggle list", "Hide and show content inside", Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Basic blocks", "collapse expand"),
    SlashItem("toggle_heading_1", "Toggle heading 1", "Collapsible big heading", Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Basic blocks", "toggle h1"),
    SlashItem("toggle_heading_2", "Toggle heading 2", "Collapsible medium heading", Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Basic blocks", "toggle h2"),
    SlashItem("toggle_heading_3", "Toggle heading 3", "Collapsible small heading", Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Basic blocks", "toggle h3"),
    SlashItem("quote", "Quote", "Capture a quote", Icons.Rounded.FormatQuote, "Basic blocks", "blockquote"),
    SlashItem("callout", "Callout", "Make writing stand out", Icons.Rounded.Lightbulb, "Basic blocks", "note info warning"),
    SlashItem("divider", "Divider", "Visually divide blocks", Icons.Rounded.HorizontalRule, "Basic blocks", "line separator hr"),
    SlashItem("code", "Code", "Capture a code snippet", Icons.Rounded.Code, "Basic blocks", "snippet programming"),
    SlashItem("equation", "Block equation", "Display a standalone equation", Icons.Rounded.Functions, "Basic blocks", "math latex katex"),
    SlashItem("table", "Table", "Add a simple table", Icons.Rounded.GridOn, "Basic blocks", "grid rows"),
    SlashItem("columns_2", "2 columns", "Create two side-by-side columns", Icons.Rounded.ViewColumn, "Layout", "columns layout"),
    SlashItem("columns_3", "3 columns", "Create three columns", Icons.Rounded.ViewColumn, "Layout", "columns layout"),
    SlashItem("tabs", "Tabs", "Organize content into tabs", Icons.Rounded.Tab, "Layout", "tab sections"),
    SlashItem("table_of_contents", "Table of contents", "Show an outline of the page", Icons.AutoMirrored.Rounded.Toc, "Layout", "toc outline"),
    SlashItem("breadcrumb", "Breadcrumb", "Show where this page lives", Icons.Rounded.LinearScale, "Layout", "path location"),
    SlashItem("page", "Page", "Embed a sub-page inside this page", Icons.AutoMirrored.Rounded.Article, "Pages", "subpage new page"),
    SlashItem("link_to_page", "Link to page", "Link to an existing page", Icons.Rounded.Link, "Pages", "link reference"),
    SlashItem("database", "Database – inline", "Add a database to this page", Icons.Rounded.TableChart, "Pages", "table board database"),
    SlashItem("image", "Image", "Upload or embed with a link", Icons.Rounded.Image, "Media", "photo picture"),
    SlashItem("video", "Video", "Upload or embed a video", Icons.Rounded.Videocam, "Media", "youtube movie"),
    SlashItem("audio", "Audio", "Upload or embed audio", Icons.Rounded.Audiotrack, "Media", "sound music podcast"),
    SlashItem("file", "File", "Upload or link a file", Icons.Rounded.AttachFile, "Media", "attachment document"),
    SlashItem("pdf", "PDF", "Embed a PDF", Icons.Rounded.PictureAsPdf, "Media", "document"),
    SlashItem("bookmark", "Web bookmark", "Save a link as a visual card", Icons.Rounded.Bookmark, "Media", "url link card"),
    SlashItem("embed", "Embed", "Embed a web page", Icons.Rounded.Public, "Media", "iframe website"),
    SlashItem("mention_person", "Mention a person", "Ping someone", Icons.Rounded.AlternateEmail, "Inline", "people user @"),
    SlashItem("mention_page", "Mention a page", "Link to a page inline", Icons.AutoMirrored.Rounded.Article, "Inline", "page link @"),
    SlashItem("date", "Date or reminder", "Insert today's date", Icons.Rounded.Event, "Inline", "today date time"),
    SlashItem("inline_equation", "Inline equation", "Insert math in a line of text", Icons.Rounded.Functions, "Inline", "math latex"),
) + Hue.optionHues.filter { it != Hue.DEFAULT }.flatMap { hue ->
    listOf(
        SlashItem("color:${hue.api}", hue.label, "${hue.label} text", Icons.Rounded.FormatColorText, "Color", "color text ${hue.api}"),
        SlashItem("background:${hue.api}", "${hue.label} background", "${hue.label} highlight", Icons.Rounded.FontDownload, "Color", "color background highlight ${hue.api}"),
    )
} + listOf(
    SlashItem("color:default", "Default color", "Remove color", Icons.Rounded.FormatColorText, "Color", "color default"),
    SlashItem("action:duplicate", "Duplicate", "Copy this block", Icons.Rounded.ContentCopy, "Actions", "copy"),
    SlashItem("action:comment", "Comment", "Discuss this block", Icons.Rounded.AddComment, "Actions", "discussion"),
    SlashItem("action:delete", "Delete", "Remove this block", Icons.Rounded.Delete, "Actions", "remove trash"),
)

fun filterSlash(query: String): List<SlashItem> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return SlashItems.filter { it.section != "Color" && it.section != "Actions" }
    return SlashItems.filter { it.label.lowercase().contains(q) || it.keywords.contains(q) || it.id.contains(q) }
}

@Composable
fun SlashMenu(query: String, onSelect: (SlashItem) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val items = remember(query) { filterSlash(query) }
    LaunchedEffect(items.isEmpty()) { if (items.isEmpty() && query.length > 2) { delay(1200); onDismiss() } }
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        modifier = modifier.widthIn(max = 420.dp).fillMaxWidth(),
    ) {
        if (items.isEmpty()) {
            Text("No results", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        } else LazyColumn(Modifier.heightIn(max = 300.dp).padding(vertical = 8.dp)) {
            var section = ""
            items.forEach { item ->
                if (item.section != section) {
                    section = item.section
                    val label = section
                    item(key = "s-$label") {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 2.dp))
                    }
                }
                item(key = item.id) { SlashRow(item) { onSelect(item) } }
            }
        }
    }
}

@Composable
private fun SlashRow(item: SlashItem, onClick: () -> Unit) {
    val palette = LocalNotionPalette.current
    val colorHue = item.id.substringAfter(":", "").takeIf { item.section == "Color" }?.let { Hue.of(it) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    when {
                        colorHue != null && item.id.startsWith("background") -> palette.background(colorHue)
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (colorHue != null && item.id.startsWith("color")) Text("A", style = MaterialTheme.typography.titleMediumEmphasized, color = palette.text(colorHue).takeIf { colorHue != Hue.DEFAULT } ?: MaterialTheme.colorScheme.onSurface)
            else Icon(item.icon, null, tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.label, style = MaterialTheme.typography.titleSmall)
            Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

sealed interface MentionChoice {
    data class Person(val user: NUser) : MentionChoice
    data class Page(val ref: NodeRef) : MentionChoice
    data class Date(val label: String, val date: NDate) : MentionChoice
}

@Composable
fun MentionMenu(query: String, onSelect: (MentionChoice) -> Unit, modifier: Modifier = Modifier) {
    val session = LocalSession.current
    val users by session.cache.users.collectAsState()
    var pages by remember { mutableStateOf<List<NodeRef>>(emptyList()) }
    LaunchedEffect(Unit) { if (session.canListUsers) runCatching { session.users.loadAll() } }
    LaunchedEffect(query) {
        delay(220)
        pages = runCatching { session.workspace.search(query, newestFirst = if (query.isBlank()) true else null).items.take(8) }.getOrDefault(emptyList())
    }
    val q = query.trim().lowercase()
    val today = LocalDate.now()
    val dates = listOf(
        MentionChoice.Date("Today", NDate(today.toString())),
        MentionChoice.Date("Tomorrow", NDate(today.plusDays(1).toString())),
        MentionChoice.Date("Yesterday", NDate(today.minusDays(1).toString())),
        MentionChoice.Date("Now", NDate(OffsetDateTime.now().withNano(0).toString())),
    ).filter { q.isEmpty() || it.label.lowercase().startsWith(q) }
    val people = users.values.filter { !it.isBot && (q.isEmpty() || it.displayName.lowercase().contains(q)) }.take(6)
    Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 8.dp, modifier = modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        LazyColumn(Modifier.heightIn(max = 300.dp).padding(vertical = 8.dp)) {
            if (people.isEmpty() && pages.isEmpty() && dates.isEmpty()) {
                item {
                    Text(
                        "No matches",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
            if (people.isNotEmpty()) {
                item { MenuLabel("People") }
                items(people, key = { "u" + it.id }) { u ->
                    MenuRow({ UserAvatar(u, 28.dp) }, u.displayName, u.email) { onSelect(MentionChoice.Person(u)) }
                }
            }
            if (pages.isNotEmpty()) {
                item { MenuLabel("Pages") }
                items(pages, key = { "p" + it.id }) { p ->
                    MenuRow({ NotionIcon(p.icon, 24.dp, kind = p.kind) }, p.title.ifBlank { "Untitled" }, null) { onSelect(MentionChoice.Page(p)) }
                }
            }
            if (dates.isNotEmpty()) {
                item { MenuLabel("Date") }
                items(dates, key = { "d" + it.label }) { d ->
                    MenuRow({ Icon(Icons.Rounded.Event, null) }, d.label, null) { onSelect(d) }
                }
            }
        }
    }
}

@Composable
private fun MenuLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 2.dp))
}

@Composable
private fun MenuRow(leading: @Composable () -> Unit, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
    }
}

fun mentionFor(choice: MentionChoice): Pair<String, Attrs> = when (choice) {
    is MentionChoice.Person -> "@" + choice.user.displayName to Attrs(mention = Mention.User(choice.user.id, choice.user.name, choice.user.avatarUrl))
    is MentionChoice.Page -> choice.ref.title.ifBlank { "Untitled" } to Attrs(
        mention = if (choice.ref.kind == NodeKind.PAGE) Mention.Page(choice.ref.id) else Mention.Database(choice.ref.databaseId ?: choice.ref.id),
    )
    is MentionChoice.Date -> "@" + choice.label to Attrs(mention = Mention.Date(choice.date))
}

/** The expressive floating toolbar shown while editing text. */
@Composable
fun FormattingToolbar(
    edit: RichEditState,
    onTurnInto: () -> Unit,
    onColor: () -> Unit,
    onLink: () -> Unit,
    onMention: () -> Unit,
    onIndent: () -> Unit,
    onOutdent: () -> Unit,
    onNewLineBelow: () -> Unit,
    onComment: () -> Unit,
    onDone: () -> Unit,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val attrs = edit.currentAttrs()
    HorizontalFloatingToolbar(
        expanded = true,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = { haptics.confirm(); onDone() }) { Icon(Icons.Rounded.Check, "Done") }
        },
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        modifier = modifier,
    ) {
        Row(Modifier.widthIn(max = 520.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { haptics.click(); onNewLineBelow() }) { Icon(Icons.Rounded.Add, "Insert line below") }
            IconButton(onClick = onTurnInto) { Icon(Icons.Rounded.SwapHoriz, "Turn into") }
            Mark.entries.forEach { mark ->
                val on = when (mark) {
                    Mark.BOLD -> attrs.annotations.bold
                    Mark.ITALIC -> attrs.annotations.italic
                    Mark.UNDERLINE -> attrs.annotations.underline
                    Mark.STRIKE -> attrs.annotations.strikethrough
                    Mark.CODE -> attrs.annotations.code
                }
                IconToggleButton(checked = on, onCheckedChange = { haptics.tick(); edit.toggle(mark); onChanged() }) {
                    Icon(
                        when (mark) {
                            Mark.BOLD -> Icons.Rounded.FormatBold
                            Mark.ITALIC -> Icons.Rounded.FormatItalic
                            Mark.UNDERLINE -> Icons.Rounded.FormatUnderlined
                            Mark.STRIKE -> Icons.Rounded.FormatStrikethrough
                            Mark.CODE -> Icons.Rounded.Code
                        },
                        mark.name.lowercase().replaceFirstChar { it.uppercase() },
                    )
                }
            }
            IconButton(onClick = onLink) { Icon(Icons.Rounded.Link, "Link") }
            IconButton(onClick = onColor) { Icon(Icons.Rounded.FormatColorText, "Color") }
            IconButton(onClick = onMention) { Icon(Icons.Rounded.AlternateEmail, "Mention") }
            VerticalDivider(Modifier.heightIn(max = 24.dp).padding(horizontal = 4.dp))
            IconButton(onClick = onOutdent) { Icon(Icons.AutoMirrored.Rounded.FormatIndentDecrease, "Outdent") }
            IconButton(onClick = onIndent) { Icon(Icons.AutoMirrored.Rounded.FormatIndentIncrease, "Indent") }
            IconButton(onClick = onComment) { Icon(Icons.Rounded.AddComment, "Comment") }
        }
    }
}

@Composable
fun ColorSheet(current: NColor?, onDismiss: () -> Unit, onPick: (NColor) -> Unit) {
    val palette = LocalNotionPalette.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Text color", style = MaterialTheme.typography.titleMediumEmphasized)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Hue.optionHues.forEach { hue ->
                    val selected = current == NColor(hue, false)
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickable { onPick(NColor(hue, false)); onDismiss() },
                        contentAlignment = Alignment.Center,
                    ) { Text("A", style = MaterialTheme.typography.titleLargeEmphasized, color = if (hue == Hue.DEFAULT) MaterialTheme.colorScheme.onSurface else palette.text(hue)) }
                }
            }
            Text("Background", style = MaterialTheme.typography.titleMediumEmphasized)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Hue.optionHues.forEach { hue ->
                    val selected = current == NColor(hue, true)
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (hue == Hue.DEFAULT) MaterialTheme.colorScheme.surfaceContainerHigh else palette.background(hue))
                            .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                            .clickable { onPick(NColor(hue, hue != Hue.DEFAULT)); onDismiss() },
                    )
                }
            }
        }
    }
}

@Composable
fun LinkDialog(initial: String?, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var url by remember { mutableStateOf(initial ?: "") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Link, null) },
        title = { Text("Link") },
        text = { OutlinedTextField(url, { url = it }, placeholder = { Text("https://") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onSave(url.trim().ifBlank { null }); onDismiss() }) { Text("Apply") } },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onSave(null); onDismiss() }) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

val TurnIntoTypes = listOf(
    BlockType.PARAGRAPH to Icons.Rounded.TextFields,
    BlockType.HEADING_1 to Icons.Rounded.Title,
    BlockType.HEADING_2 to Icons.Rounded.Title,
    BlockType.HEADING_3 to Icons.Rounded.Title,
    BlockType.HEADING_4 to Icons.Rounded.Title,
    BlockType.TO_DO to Icons.Rounded.CheckBox,
    BlockType.BULLETED to Icons.AutoMirrored.Rounded.FormatListBulleted,
    BlockType.NUMBERED to Icons.Rounded.FormatListNumbered,
    BlockType.TOGGLE to Icons.AutoMirrored.Rounded.KeyboardArrowRight,
    BlockType.QUOTE to Icons.Rounded.FormatQuote,
    BlockType.CALLOUT to Icons.Rounded.Lightbulb,
    BlockType.CODE to Icons.Rounded.Code,
)

/** Long-press menu for a block. */
@Composable
fun BlockMenuSheet(
    block: NBlock,
    onDismiss: () -> Unit,
    onTurnInto: (BlockType) -> Unit,
    onColor: (NColor) -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onIndent: () -> Unit,
    onOutdent: () -> Unit,
    onCopy: () -> Unit,
    onCopyLink: () -> Unit,
    onComment: () -> Unit,
    onDelete: () -> Unit,
    onInsertAbove: () -> Unit = {},
    onInsertBelow: () -> Unit = {},
    extra: @Composable () -> Unit = {},
) {
    val palette = LocalNotionPalette.current
    val haptics = LocalHaptics.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
        LazyColumn(Modifier.fillMaxWidth().imePadding()) {
            item {
                Text(block.type.label, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            }
            if (block.type.isConvertibleText) item {
                Text("Turn into", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TurnIntoTypes.forEach { (type, icon) ->
                        val selected = type == block.type
                        Surface(
                            onClick = { haptics.segment(); onTurnInto(type); onDismiss() },
                            shape = RoundedCornerShape(if (selected) 24.dp else 16.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Column(Modifier.padding(12.dp).width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(icon, null)
                                Text(type.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (block.type.supportsColor) item {
                Text("Color", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Hue.optionHues.forEach { hue ->
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(2.dp, if (block.color == NColor(hue)) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                .clickable { onColor(NColor(hue)); onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) { Text("A", fontWeight = FontWeight.Bold, color = if (hue == Hue.DEFAULT) MaterialTheme.colorScheme.onSurface else palette.text(hue)) }
                    }
                    Hue.optionHues.filter { it != Hue.DEFAULT }.forEach { hue ->
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(palette.background(hue))
                                .border(2.dp, if (block.color == NColor(hue, true)) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                .clickable { onColor(NColor(hue, true)); onDismiss() },
                        )
                    }
                }
            }
            item { extra() }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            val actions = listOfNotNull(
                Triple("Insert line above", Icons.Rounded.Add, onInsertAbove),
                Triple("Insert line below", Icons.Rounded.Add, onInsertBelow),
                Triple("Comment", Icons.Rounded.AddComment, onComment),
                Triple("Duplicate", Icons.Rounded.ContentCopy, onDuplicate),
                Triple("Move up", Icons.Rounded.ArrowUpward, onMoveUp),
                Triple("Move down", Icons.Rounded.ArrowDownward, onMoveDown),
                Triple("Indent", Icons.AutoMirrored.Rounded.FormatIndentIncrease, onIndent),
                Triple("Outdent", Icons.AutoMirrored.Rounded.FormatIndentDecrease, onOutdent),
                if (block.type.isText) Triple("Copy text", Icons.AutoMirrored.Rounded.Notes, onCopy) else null,
                Triple("Copy link to block", Icons.Rounded.Link, onCopyLink),
            )
            items(actions, key = { it.first }) { (label, icon, action) ->
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = { Icon(icon, null) },
                    modifier = Modifier.clickable { action(); onDismiss() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { haptics.reject(); onDelete(); onDismiss() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}