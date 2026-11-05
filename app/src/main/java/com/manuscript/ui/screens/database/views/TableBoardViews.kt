package com.manuscript.ui.screens.database.views

import android.content.ClipData
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manuscript.core.model.GroupBy
import com.manuscript.core.model.NFile
import com.manuscript.core.model.NPage
import com.manuscript.core.model.NUser
import com.manuscript.core.model.NodeKind
import com.manuscript.core.model.PropType
import com.manuscript.core.model.PropValue
import com.manuscript.core.model.PropertySchema
import com.manuscript.core.model.SelectOption
import com.manuscript.ui.components.NotionIcon
import com.manuscript.ui.components.NotionImage
import com.manuscript.ui.components.OptionChip
import com.manuscript.ui.components.StatusDot
import com.manuscript.ui.components.UserAvatar
import com.manuscript.ui.screens.database.DbUi
import com.manuscript.ui.screens.database.RowGroup
import com.manuscript.ui.screens.database.groupRows
import com.manuscript.ui.screens.database.value
import com.manuscript.ui.screens.properties.PropertyValueView
import com.manuscript.ui.screens.properties.isEmpty
import com.manuscript.ui.screens.properties.propertyIcon
import com.manuscript.ui.theme.LocalHaptics
import com.manuscript.ui.theme.LocalNotionPalette
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter

@Stable
class ViewActions(
    val open: (NPage) -> Unit,
    val update: (NPage, PropertySchema, PropValue) -> Unit,
    val edit: (NPage, PropertySchema) -> Unit,
    val create: (Map<String, PropValue>) -> Unit,
    val rowMenu: (NPage) -> Unit,
    val columnMenu: (PropertySchema) -> Unit,
    val loadMore: () -> Unit,
    val editable: Boolean,
    val message: (String) -> Unit,
)

fun defaultWidth(type: PropType): Dp = when (type) {
    PropType.TITLE -> 280.dp
    PropType.RICH_TEXT -> 240.dp
    PropType.NUMBER, PropType.UNIQUE_ID -> 120.dp
    PropType.CHECKBOX -> 96.dp
    PropType.DATE, PropType.CREATED_TIME, PropType.LAST_EDITED_TIME -> 180.dp
    PropType.MULTI_SELECT, PropType.RELATION, PropType.FILES -> 220.dp
    PropType.PEOPLE, PropType.CREATED_BY, PropType.LAST_EDITED_BY -> 190.dp
    else -> 170.dp
}

fun DbUi.columnWidth(prop: PropertySchema): Dp = widthOf(prop)?.let { (it * 0.9f).dp.coerceIn(80.dp, 480.dp) } ?: defaultWidth(prop.type)

/** Values that place a new row into [group]. */
fun groupDefaults(group: GroupBy?, target: RowGroup, ds: com.manuscript.core.model.NDataSource): Map<String, PropValue> {
    if (group == null || target.empty) return emptyMap()
    val prop = ds.prop(group.propertyId) ?: return emptyMap()
    val value: PropValue = when (prop.type) {
        PropType.SELECT -> PropValue.Select(prop.id, target.option ?: SelectOption(name = target.label))
        PropType.STATUS -> {
            val option = target.option ?: prop.statusGroups.firstOrNull { it.id == target.key }?.optionIds?.firstOrNull()
                ?.let { id -> prop.options.firstOrNull { it.id == id } }
            PropValue.Status(prop.id, option ?: return emptyMap())
        }
        PropType.MULTI_SELECT -> PropValue.MultiSelect(prop.id, listOf(target.option ?: SelectOption(name = target.label)))
        PropType.CHECKBOX -> PropValue.Checkbox(prop.id, target.key == "true")
        PropType.PEOPLE -> PropValue.People(prop.id, listOfNotNull(target.user))
        else -> return emptyMap()
    }
    return mapOf(prop.name to value)
}

@Composable
fun LoadMoreOnEnd(listState: androidx.compose.foundation.lazy.LazyListState, enabled: Boolean, onLoadMore: () -> Unit) {
    LaunchedEffect(listState, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .filter { it >= listState.layoutInfo.totalItemsCount - 5 }
            .collectLatest { onLoadMore() }
    }
}

@Composable
fun TableView(state: DbUi, actions: ViewActions, users: Map<String, NUser>, modifier: Modifier = Modifier) {
    val ds = state.source ?: return
    val session = com.manuscript.ui.LocalSession.current
    val props = state.visibleProps()
    val title = props.firstOrNull { it.type == PropType.TITLE } ?: ds.titleProperty ?: return
    val others = props.filter { it.id != title.id }
    val hScroll = rememberScrollState()
    val group = state.effectiveGroup
    val groups = remember(state.rows, group, ds, users) { group?.let { groupRows(state.rows, it, ds, users) { id -> session.cache.displayTitle(id) } } }
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
    val listState = rememberLazyListState()
    LoadMoreOnEnd(listState, state.cursor != null) { actions.loadMore() }
    val titleWidth = state.columnWidth(title).coerceAtMost(300.dp)
    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 160.dp)) {
        stickyHeader(key = "header") { TableHeader(state, title, others, titleWidth, hScroll, actions) }
        if (groups == null) {
            items(state.rows, key = { it.id }) { row -> TableRow(state, row, title, others, titleWidth, hScroll, actions, Modifier.animateItem()) }
            if (actions.editable) item(key = "new") { NewRowButton { actions.create(emptyMap()) } }
        } else groups.forEach { g ->
            item(key = "g-" + g.key) {
                GroupHeader(g, collapsed = g.key in collapsed, onToggle = { collapsed = if (g.key in collapsed) collapsed - g.key else collapsed + g.key })
            }
            if (g.key !in collapsed) {
                items(g.rows, key = { g.key + "/" + it.id }) { row -> TableRow(state, row, title, others, titleWidth, hScroll, actions, Modifier.animateItem()) }
                if (actions.editable) item(key = "new-" + g.key) { NewRowButton { actions.create(groupDefaults(group, g, ds)) } }
            }
        }
        if (state.loadingMore) item(key = "more") { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator() } }
        item(key = "count") {
            Text(
                "${state.rows.size}${if (state.cursor != null) "+" else ""} ${if (state.rows.size == 1) "entry" else "entries"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun TableHeader(state: DbUi, title: PropertySchema, others: List<PropertySchema>, titleWidth: Dp, hScroll: ScrollState, actions: ViewActions) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                HeaderCell(title, titleWidth) { actions.columnMenu(title) }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.horizontalScroll(hScroll), verticalAlignment = Alignment.CenterVertically) {
                    others.forEach { p ->
                        HeaderCell(p, state.columnWidth(p)) { actions.columnMenu(p) }
                        VerticalDivider(Modifier.height(44.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun HeaderCell(prop: PropertySchema, width: Dp, onClick: () -> Unit) {
    Row(
        Modifier.width(width).fillMaxHeight().combinedClickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(propertyIcon(prop.type), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(prop.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TableRow(
    state: DbUi,
    row: NPage,
    title: PropertySchema,
    others: List<PropertySchema>,
    titleWidth: Dp,
    hScroll: ScrollState,
    actions: ViewActions,
    modifier: Modifier,
) {
    val haptics = LocalHaptics.current
    Column(modifier) {
        Row(Modifier.heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .width(titleWidth)
                    .heightIn(min = 46.dp)
                    .combinedClickable(onClick = { actions.open(row) }, onLongClick = { haptics.longPress(); actions.rowMenu(row) })
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NotionIcon(row.icon, 20.dp, kind = NodeKind.PAGE, showFallback = false)
                if (row.icon != null) Spacer(Modifier.width(8.dp))
                Text(row.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            VerticalDivider(Modifier.height(46.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.horizontalScroll(hScroll), verticalAlignment = Alignment.CenterVertically) {
                others.forEach { p ->
                    val value = row.value(p)
                    Box(
                        Modifier
                            .width(state.columnWidth(p))
                            .heightIn(min = 46.dp)
                            .combinedClickable(
                                enabled = actions.editable || !p.type.editable,
                                onClick = {
                                    if (p.type == PropType.CHECKBOX && actions.editable) {
                                        haptics.toggle(!((value as? PropValue.Checkbox)?.checked ?: false))
                                        actions.update(row, p, PropValue.Checkbox(p.id, !((value as? PropValue.Checkbox)?.checked ?: false)))
                                    } else actions.edit(row, p)
                                },
                                onLongClick = { actions.rowMenu(row) },
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value != null && !value.isEmpty()) PropertyValueView(value, p)
                    }
                    VerticalDivider(Modifier.height(46.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

@Composable
fun GroupHeader(group: RowGroup, collapsed: Boolean, onToggle: () -> Unit) {
    val rotation = if (collapsed) 0f else 90f
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ChevronRight, null, Modifier.rotate(rotation))
        Spacer(Modifier.width(6.dp))
        GroupLabel(group)
        Spacer(Modifier.width(8.dp))
        Text("${group.rows.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun GroupLabel(group: RowGroup) {
    when {
        group.option != null -> OptionChip(group.option)
        group.user != null -> Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(group.user, 22.dp)
            Spacer(Modifier.width(6.dp))
            Text(group.label, style = MaterialTheme.typography.titleSmall)
        }
        group.hue != com.manuscript.core.model.Hue.DEFAULT -> Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(group.hue, size = 10.dp)
            Spacer(Modifier.width(8.dp))
            Text(group.label, style = MaterialTheme.typography.titleSmall)
        }
        else -> Text(group.label, style = MaterialTheme.typography.titleSmall, color = if (group.empty) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun NewRowButton(label: String = "New", onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(horizontal = 4.dp)) {
        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

/** The image shown on top of cards, chosen by the view's cover setting. */
fun coverFor(page: NPage, type: String?, propertyId: String?, ds: com.manuscript.core.model.NDataSource): NFile? = when (type) {
    "property" -> ds.prop(propertyId)?.let { p -> (page.value(p) as? PropValue.Files)?.files?.firstOrNull() }
    null, "page_cover", "page_content", "page_content_first" -> page.cover
    else -> page.cover
}

@Composable
fun RowCard(
    row: NPage,
    props: List<PropertySchema>,
    cover: NFile?,
    coverHeight: Dp,
    coverFit: Boolean,
    actions: ViewActions,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val haptics = LocalHaptics.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = modifier.combinedClickable(onClick = { actions.open(row) }, onLongClick = { haptics.longPress(); actions.rowMenu(row) }),
    ) {
        Column {
            if (cover != null && cover.url.isNotBlank()) {
                NotionImage(
                    cover.url,
                    Modifier.fillMaxWidth().height(coverHeight).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentScale = if (coverFit) ContentScale.Fit else ContentScale.Crop,
                )
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (row.icon != null) {
                        NotionIcon(row.icon, 20.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(row.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (!compact) props.forEach { p ->
                    val v = row.value(p)
                    if (v != null && !v.isEmpty()) PropertyValueView(v, p, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun BoardView(state: DbUi, actions: ViewActions, users: Map<String, NUser>, modifier: Modifier = Modifier) {
    val ds = state.source ?: return
    val session = com.manuscript.ui.LocalSession.current
    val palette = LocalNotionPalette.current
    val haptics = LocalHaptics.current
    val group = state.effectiveGroup ?: remember(ds) {
        ds.properties.firstOrNull { it.type == PropType.STATUS }?.let { GroupBy("status", it.id, groupBy = "option") }
            ?: ds.properties.firstOrNull { it.type == PropType.SELECT }?.let { GroupBy("select", it.id) }
    }
    if (group == null) {
        Text("Group this board by a select, status, person or checkbox property.", modifier = Modifier.padding(24.dp))
        return
    }
    val groupProp = ds.prop(group.propertyId)
    val groups = remember(state.rows, group, ds, users) { groupRows(state.rows, group, ds, users) { id -> session.cache.displayTitle(id) } }
    val cardProps = state.visibleProps().filter { it.type != PropType.TITLE && it.id != group.propertyId }
    val config = state.config
    var hovered by remember { mutableStateOf<String?>(null) }

    fun move(pageId: String, fromKey: String?, target: RowGroup) {
        val page = state.rows.firstOrNull { it.id == pageId } ?: return
        val prop = groupProp ?: return
        if (fromKey == target.key) return
        val current = page.value(prop)
        val value: PropValue? = when (prop.type) {
            PropType.SELECT -> PropValue.Select(prop.id, if (target.empty) null else target.option ?: SelectOption(name = target.label))
            PropType.STATUS -> {
                val option = if (target.empty) null else target.option ?: prop.statusGroups.firstOrNull { it.id == target.key }
                    ?.optionIds?.firstOrNull()?.let { id -> prop.options.firstOrNull { it.id == id } }
                PropValue.Status(prop.id, option)
            }
            PropType.MULTI_SELECT -> {
                val existing = (current as? PropValue.MultiSelect)?.options ?: emptyList()
                val without = existing.filterNot { it.name == fromKey }
                PropValue.MultiSelect(prop.id, if (target.empty) without else (without + (target.option ?: SelectOption(name = target.label))).distinctBy { it.name })
            }
            PropType.CHECKBOX -> PropValue.Checkbox(prop.id, target.key == "true")
            PropType.PEOPLE -> {
                val existing = (current as? PropValue.People)?.users ?: emptyList()
                PropValue.People(prop.id, if (target.empty) emptyList() else (existing.filterNot { it.id == fromKey } + listOfNotNull(target.user)).distinctBy { it.id })
            }
            else -> null
        }
        if (value == null) {
            actions.message("Moving cards isn't supported for ${prop.type.label} groups")
            return
        }
        haptics.confirm()
        actions.update(page, prop, value)
    }

    LazyRow(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(groups, key = { it.key }) { g ->
            val tint = if (g.hue != com.manuscript.core.model.Hue.DEFAULT) palette.background(g.hue) else MaterialTheme.colorScheme.surfaceContainerLow
            val background by animateColorAsState(if (hovered == g.key) MaterialTheme.colorScheme.primaryContainer else tint, label = "column")
            val target = remember(g, state.rows) {
                object : DragAndDropTarget {
                    override fun onDrop(event: DragAndDropEvent): Boolean {
                        hovered = null
                        val data = event.toAndroidDragEvent().localState as? String ?: event.toAndroidDragEvent().clipData?.getItemAt(0)?.text?.toString() ?: return false
                        val (pageId, from) = data.split('|', limit = 2).let { it[0] to it.getOrNull(1) }
                        move(pageId, from, g)
                        return true
                    }
                    override fun onEntered(event: DragAndDropEvent) { hovered = g.key; haptics.segment() }
                    override fun onExited(event: DragAndDropEvent) { if (hovered == g.key) hovered = null }
                    override fun onEnded(event: DragAndDropEvent) { hovered = null }
                }
            }
            Column(
                Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(26.dp))
                    .background(background)
                    .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target)
                    .padding(8.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GroupLabel(g)
                    Spacer(Modifier.width(8.dp))
                    Text("${g.rows.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (actions.editable) IconButton(onClick = { actions.create(groupDefaults(group, g, ds)) }) { Icon(Icons.Rounded.Add, "Add to ${g.label}") }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(g.rows, key = { g.key + "/" + it.id }) { row ->
                        val cover = coverFor(row, config.coverType, config.coverPropertyId, ds)
                        RowCard(
                            row, cardProps, cover,
                            coverHeight = when (config.coverSize) { "small" -> 100.dp; "large" -> 200.dp; else -> 140.dp },
                            coverFit = config.coverAspect == "contain",
                            actions = actions,
                            compact = config.cardLayout == "compact",
                            modifier = Modifier.fillMaxWidth().animateItem().dragAndDropSource { _ ->
                                if (!actions.editable) null
                                else DragAndDropTransferData(ClipData.newPlainText("page", "${row.id}|${g.key}"), localState = "${row.id}|${g.key}")
                            },
                        )
                    }
                    if (g.rows.isEmpty()) item {
                        Box(
                            Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(18.dp))
                                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text("Drop cards here", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}