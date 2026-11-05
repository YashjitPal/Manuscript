package com.manuscript.ui.screens.pickers

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manuscript.core.data.AccountKind
import com.manuscript.core.data.CreateParent
import com.manuscript.core.model.BlockType
import com.manuscript.core.model.Hue
import com.manuscript.core.model.NBlock
import com.manuscript.core.model.NDatabase
import com.manuscript.core.model.NIcon
import com.manuscript.core.model.NPage
import com.manuscript.core.model.NodeKind
import com.manuscript.core.model.NodeRef
import com.manuscript.core.model.PropType
import com.manuscript.core.model.PropertySchema
import com.manuscript.core.model.SelectOption
import com.manuscript.core.model.toRich
import com.manuscript.ui.LocalSession
import com.manuscript.ui.components.LocalMessenger
import com.manuscript.ui.components.NotionIcon
import com.manuscript.ui.components.ShapeBadge
import com.manuscript.ui.theme.LocalHaptics
import kotlinx.coroutines.launch

val QuickEmoji = listOf("📄", "📝", "✨", "🚀", "💡", "📚", "🎯", "🧠", "🗓️", "✅", "🎨", "🔥", "🌱", "📌", "🧭", "💬")

/** Whether the account may create private pages at the workspace root. */
@Composable
fun canCreateAtRoot(): Boolean {
    val account = LocalSession.current.account
    return account.kind == AccountKind.DEMO || account.kind == AccountKind.OAUTH || account.isPersonal
}

@Composable
private fun DestinationRow(destination: NodeRef?, rootLabel: String?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Add to", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            if (destination != null) {
                NotionIcon(destination.icon, 20.dp, kind = destination.kind)
                Spacer(Modifier.width(8.dp))
                Text(destination.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            } else {
                Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(rootLabel ?: "Choose a page", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            Icon(Icons.Rounded.ExpandMore, null)
        }
    }
}

@Composable
private fun EmojiRow(selected: String?, onSelect: (String?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        QuickEmoji.forEach { e ->
            val isSelected = e == selected
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(if (isSelected) 22.dp else 14.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onSelect(if (isSelected) null else e) },
                contentAlignment = Alignment.Center,
            ) { Text(e, style = MaterialTheme.typography.titleLarge) }
        }
    }
}

/** Creates a page (or a quick note) under a chosen page, database or the private workspace root. */
@Composable
fun NewPageSheet(
    onDismiss: () -> Unit,
    onCreated: (NPage) -> Unit,
    initialParent: NodeRef? = null,
    quickNote: Boolean = false,
    initialTitle: String = "",
    initialBody: String = "",
) {
    val session = LocalSession.current
    val messenger = LocalMessenger.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val rootAllowed = canCreateAtRoot()
    val recents by session.workspace.recents.collectAsState(emptyList())
    var title by remember { mutableStateOf(initialTitle) }
    var body by remember { mutableStateOf(initialBody) }
    var emoji by remember { mutableStateOf<String?>(if (quickNote) "📝" else null) }
    var destination by remember { mutableStateOf(initialParent) }
    var picking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) {
        if (destination == null && !rootAllowed) destination = recents.firstOrNull { it.kind == NodeKind.PAGE }
        runCatching { focus.requestFocus() }
    }

    fun create() {
        if (busy) return
        val dest = destination
        if (dest == null && !rootAllowed) {
            picking = true
            return
        }
        busy = true
        scope.launch {
            runCatching {
                val children = body.lines().filter { it.isNotBlank() }.map { line ->
                    when {
                        line.startsWith("- [ ] ") -> NBlock("", BlockType.TO_DO, rich = line.removePrefix("- [ ] ").toRich())
                        line.startsWith("- ") -> NBlock("", BlockType.BULLETED, rich = line.removePrefix("- ").toRich())
                        else -> NBlock("", BlockType.PARAGRAPH, rich = line.toRich())
                    }
                }
                when {
                    dest == null -> session.pages.createPage(CreateParent.Workspace, title, emoji?.let { NIcon.Emoji(it) }, children = children)
                    dest.kind == NodeKind.PAGE -> session.pages.createPage(CreateParent.Page(dest.id), title, emoji?.let { NIcon.Emoji(it) }, children = children)
                    else -> {
                        val dsId = if (dest.kind == NodeKind.DATA_SOURCE) dest.id
                        else session.databases.database(dest.id).dataSources.firstOrNull()?.id ?: error("Database has no data source")
                        val ds = session.cache.dataSource(dsId) ?: session.databases.dataSource(dsId)
                        session.pages.createPage(
                            CreateParent.DataSource(dsId), title, emoji?.let { NIcon.Emoji(it) }, children = children,
                            titleKey = ds.titleProperty?.name ?: "Name",
                        )
                    }
                }
            }.onSuccess {
                haptics.confirm()
                onCreated(it)
            }.onFailure {
                busy = false
                messenger.error(it)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (quickNote) "Quick note" else "New page", style = MaterialTheme.typography.headlineSmallEmphasized)
            EmojiRow(emoji) { emoji = it; haptics.tick() }
            TextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Untitled", style = MaterialTheme.typography.headlineMediumEmphasized) },
                textStyle = MaterialTheme.typography.headlineMediumEmphasized,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (quickNote) {
                TextField(
                    value = body,
                    onValueChange = { body = it },
                    placeholder = { Text("Write something… Lines starting with - become bullets, - [ ] become to-dos.") },
                    minLines = 4,
                    shape = MaterialTheme.shapes.large,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                )
            }
            DestinationRow(destination, if (rootAllowed) "Private" else null) { picking = true }
            Button(
                onClick = ::create,
                enabled = !busy,
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
            ) {
                if (busy) LoadingIndicator(Modifier.size(30.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text("Create", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
            }
        }
    }

    if (picking) {
        PagePickerSheet(
            title = "Add to…",
            onDismiss = { picking = false },
            onPick = { destination = it; picking = false },
            workspaceOption = if (rootAllowed) "Private" else null,
            onPickWorkspace = { destination = null; picking = false },
        )
    }
}

private data class DbTemplate(val name: String, val description: String, val icon: ImageVector, val emoji: String, val properties: List<PropertySchema>)

private fun prop(name: String, type: PropType, options: List<SelectOption> = emptyList(), format: String? = null) =
    PropertySchema(id = "", name = name, type = type, options = options, numberFormat = format)

private val dbTemplates = listOf(
    DbTemplate("Blank", "Just a title column", Icons.Rounded.TableChart, "🗂️", listOf(prop("Name", PropType.TITLE))),
    DbTemplate("Tasks", "Status, assignee, due date and priority", Icons.Rounded.Checklist, "✅", listOf(
        prop("Task", PropType.TITLE),
        prop("Status", PropType.STATUS),
        prop("Assignee", PropType.PEOPLE),
        prop("Due", PropType.DATE),
        prop("Priority", PropType.SELECT, listOf(SelectOption(name = "High", color = Hue.RED), SelectOption(name = "Medium", color = Hue.YELLOW), SelectOption(name = "Low", color = Hue.GRAY))),
    )),
    DbTemplate("Projects", "Owner, timeline and progress", Icons.Rounded.RocketLaunch, "🚀", listOf(
        prop("Project", PropType.TITLE),
        prop("Status", PropType.STATUS),
        prop("Owner", PropType.PEOPLE),
        prop("Dates", PropType.DATE),
        prop("Progress", PropType.NUMBER, format = "percent"),
        prop("Tags", PropType.MULTI_SELECT),
    )),
    DbTemplate("Reading list", "Author, status, rating", Icons.Rounded.MenuBook, "📚", listOf(
        prop("Title", PropType.TITLE),
        prop("Author", PropType.RICH_TEXT),
        prop("Status", PropType.SELECT, listOf(SelectOption(name = "To read", color = Hue.GRAY), SelectOption(name = "Reading", color = Hue.BLUE), SelectOption(name = "Finished", color = Hue.GREEN))),
        prop("Rating", PropType.SELECT, listOf(SelectOption(name = "★★★★★", color = Hue.YELLOW), SelectOption(name = "★★★★", color = Hue.ORANGE), SelectOption(name = "★★★", color = Hue.BROWN))),
        prop("Finished", PropType.DATE),
    )),
    DbTemplate("Notes", "Tags and creation time", Icons.AutoMirrored.Rounded.Notes, "🗒️", listOf(
        prop("Name", PropType.TITLE),
        prop("Tags", PropType.MULTI_SELECT),
        prop("Created", PropType.CREATED_TIME),
    )),
)

@Composable
fun NewDatabaseSheet(
    onDismiss: () -> Unit,
    onCreated: (NDatabase) -> Unit,
    initialParent: NodeRef? = null,
) {
    val session = LocalSession.current
    val messenger = LocalMessenger.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val rootAllowed = canCreateAtRoot()
    val recents by session.workspace.recents.collectAsState(emptyList())
    var title by remember { mutableStateOf("") }
    var template by remember { mutableStateOf(dbTemplates[1]) }
    var destination by remember { mutableStateOf(initialParent) }
    var inline by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) {
        if (destination == null && !rootAllowed) destination = recents.firstOrNull { it.kind == NodeKind.PAGE }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("New database", style = MaterialTheme.typography.headlineSmallEmphasized)
            TextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text(template.name.takeIf { it != "Blank" } ?: "Untitled database", style = MaterialTheme.typography.headlineMediumEmphasized) },
                textStyle = MaterialTheme.typography.headlineMediumEmphasized,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Start from", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                dbTemplates.forEachIndexed { index, t ->
                    SegmentedListItem(
                        selected = template == t,
                        onClick = { template = t; haptics.segment() },
                        shapes = ListItemDefaults.segmentedShapes(index, dbTemplates.size),
                        leadingContent = {
                            ShapeBadge(
                                t.icon, size = 40.dp,
                                container = if (template == t) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                content = if (template == t) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        },
                        supportingContent = { Text(t.description) },
                    ) { Text(t.name) }
                }
            }
            DestinationRow(destination, if (rootAllowed) "Private" else null) { picking = true }
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Inline", style = MaterialTheme.typography.titleSmall)
                        Text("Show the database inside the page", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = inline, onCheckedChange = { inline = it; haptics.toggle(it) }, enabled = destination != null)
                }
            }
            Button(
                onClick = {
                    if (destination == null && !rootAllowed) { picking = true; return@Button }
                    busy = true
                    scope.launch {
                        runCatching {
                            session.databases.createDatabase(
                                parentPageId = destination?.id,
                                title = title.ifBlank { template.name.takeIf { it != "Blank" } ?: "Untitled" },
                                properties = template.properties,
                                inline = inline && destination != null,
                                icon = NIcon.Emoji(template.emoji),
                            )
                        }.onSuccess { haptics.confirm(); onCreated(it) }
                            .onFailure { busy = false; messenger.error(it) }
                    }
                },
                enabled = !busy,
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
            ) {
                if (busy) LoadingIndicator(Modifier.size(30.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text("Create database", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
            }
        }
    }
    if (picking) {
        PagePickerSheet(
            title = "Add to…",
            onDismiss = { picking = false },
            onPick = { destination = it; picking = false },
            allowDatabases = false,
            workspaceOption = if (rootAllowed) "Private" else null,
            onPickWorkspace = { destination = null; picking = false },
        )
    }
}