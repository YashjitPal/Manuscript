package com.manuscript.ui.screens.database

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manuscript.core.model.FilterNode
import com.manuscript.core.model.NDatabase
import com.manuscript.core.model.NPage
import com.manuscript.core.model.NView
import com.manuscript.core.model.NodeKind
import com.manuscript.core.model.PropType
import com.manuscript.core.model.PropertySchema
import com.manuscript.core.model.SortSpec
import com.manuscript.core.model.ViewProp
import com.manuscript.core.model.ViewType
import com.manuscript.core.model.plainText
import com.manuscript.core.util.NotionIds
import com.manuscript.ui.components.ErrorState
import com.manuscript.ui.components.LocalMessenger
import com.manuscript.ui.components.NotionIcon
import com.manuscript.ui.components.SkeletonBlock
import com.manuscript.ui.navigation.LocalNavigator
import com.manuscript.ui.navigation.DatabaseRoute
import com.manuscript.ui.navigation.PageRoute
import com.manuscript.ui.screens.database.views.BoardView
import com.manuscript.ui.screens.database.views.CalendarView
import com.manuscript.ui.screens.database.views.ChartView
import com.manuscript.ui.screens.database.views.DashboardView
import com.manuscript.ui.screens.database.views.FormView
import com.manuscript.ui.screens.database.views.GalleryView
import com.manuscript.ui.screens.database.views.ListView
import com.manuscript.ui.screens.database.views.MapView
import com.manuscript.ui.screens.database.views.TableView
import com.manuscript.ui.screens.database.views.TimelineView
import com.manuscript.ui.screens.database.views.ViewActions
import com.manuscript.ui.screens.page.SwitchRow
import com.manuscript.ui.screens.pickers.IconPickerSheet
import com.manuscript.ui.screens.properties.PropertyEditor
import com.manuscript.ui.sessionViewModel
import com.manuscript.ui.theme.LocalHaptics
import com.manuscript.ui.util.copyText
import com.manuscript.ui.util.openUrl

@Composable
fun DatabaseScreen(route: DatabaseRoute) {
    val vm = sessionViewModel(key = "db-" + route.id + (route.dataSourceId ?: "")) { DatabaseViewModel(it, route.id, route.dataSourceId, route.viewId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val users by vm.users.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val messenger = LocalMessenger.current
    val haptics = LocalHaptics.current
    val context = LocalContext.current

    var filterSheet by remember { mutableStateOf(false) }
    var sortSheet by remember { mutableStateOf(false) }
    var groupSheet by remember { mutableStateOf(false) }
    var propsSheet by remember { mutableStateOf(false) }
    var newView by remember { mutableStateOf(false) }
    var editingSchema by remember { mutableStateOf<PropertySchema?>(null) }
    var newProperty by remember { mutableStateOf(false) }
    var rowMenu by remember { mutableStateOf<NPage?>(null) }
    var columnMenu by remember { mutableStateOf<PropertySchema?>(null) }
    var editingCell by remember { mutableStateOf<Pair<NPage, PropertySchema>?>(null) }
    var dbMenu by remember { mutableStateOf(false) }
    var iconPicker by remember { mutableStateOf(false) }
    var viewMenu by remember { mutableStateOf<NView?>(null) }
    var searching by remember { mutableStateOf(false) }
    var newMenu by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is DbEvent.Opened -> navigator.navigate(PageRoute(e.page.id))
                is DbEvent.Message -> messenger.show(e.text)
            }
        }
    }

    val db = state.database
    val ds = state.source
    val editable = db?.isLocked != true
    val actions = remember(vm, editable) {
        ViewActions(
            open = { navigator.navigate(PageRoute(it.id)) },
            update = { page, prop, value -> vm.updateRow(page, prop.name, value) },
            edit = { page, prop -> editingCell = page to prop },
            create = { extra -> vm.createRow(extra = extra) },
            rowMenu = { rowMenu = it },
            columnMenu = { columnMenu = it },
            loadMore = vm::loadMore,
            editable = editable,
            message = { messenger.show(it) },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NotionIcon(db?.icon ?: ds?.icon, 24.dp, kind = NodeKind.DATABASE)
                        Spacer(Modifier.width(10.dp))
                        Text(db?.titleText?.ifBlank { "Untitled" } ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLargeEmphasized)
                    }
                },
                navigationIcon = { if (navigator.backStack.size > 1) IconButton(onClick = { navigator.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { vm.query() }) { Icon(Icons.Rounded.Refresh, "Refresh") }
                    IconButton(onClick = { dbMenu = true }) { Icon(Icons.Rounded.MoreVert, "Database options") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                db == null && state.error != null -> ErrorState(state.error!!, onRetry = { vm.load() })
                db == null || state.loading -> SkeletonBlock(Modifier.padding(24.dp), lines = 8)
                else -> {
                    val description = db.description.plainText()
                    if (description.isNotBlank()) {
                        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    if (state.sources.size > 1) {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.sources.forEach { s ->
                                FilterChip(selected = s.id == ds?.id, onClick = { vm.selectSource(s) }, label = { Text(s.titleText.ifBlank { "Untitled" }) },
                                    leadingIcon = { NotionIcon(s.icon, 18.dp, kind = NodeKind.DATA_SOURCE) })
                            }
                        }
                    }
                    ViewTabs(state.views, state.view, onSelect = { haptics.segment(); vm.selectView(it) }, onLongPress = { viewMenu = it }, onAdd = { newView = true }, canAdd = editable && ds != null)
                    Toolbar(
                        searching = searching,
                        search = state.search,
                        onSearch = vm::search,
                        onToggleSearch = { searching = !searching; if (!searching) vm.search("") },
                        filterCount = when (val f = state.effectiveFilter) { null -> 0; is FilterNode.Group -> f.children.size; else -> 1 },
                        sortCount = state.effectiveSorts.size,
                        showGroup = state.type == ViewType.TABLE || state.type == ViewType.BOARD,
                        onFilter = { filterSheet = true },
                        onSort = { sortSheet = true },
                        onGroup = { groupSheet = true },
                        onProps = { propsSheet = true },
                        canCreate = editable && state.type != ViewType.FORM,
                        onNew = { vm.createRow() },
                        templates = state.templates,
                        newMenu = newMenu,
                        onNewMenu = { newMenu = it },
                        onTemplate = { t -> newMenu = false; vm.createRow(template = t) },
                    )
                    val quick = state.view?.quickFilters
                    if (quick != null && quick.isNotEmpty() && ds != null) {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            quick.keys.forEach { key ->
                                FilterChip(selected = key in state.activeQuick, onClick = { vm.toggleQuick(key) }, label = { Text(ds.prop(key)?.name ?: key) })
                            }
                        }
                    }
                    AnimatedVisibility(state.hasLocalChanges && state.view?.id?.startsWith("local-") == false) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Unsaved view changes", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                TextButton(onClick = vm::resetLocal) { Text("Reset") }
                                TextButton(onClick = vm::saveToView, enabled = editable) { Text("Save for everyone") }
                            }
                        }
                    }
                    if (state.loadingRows) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    AnimatedContent(state.view?.id to state.type, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "view", modifier = Modifier.weight(1f)) { (_, type) ->
                        when (type) {
                            ViewType.TABLE -> TableView(state, actions, users)
                            ViewType.BOARD -> BoardView(state, actions, users)
                            ViewType.LIST -> ListView(state, actions)
                            ViewType.GALLERY -> GalleryView(state, actions)
                            ViewType.CALENDAR -> CalendarView(state, actions)
                            ViewType.TIMELINE -> TimelineView(state, actions)
                            ViewType.CHART -> androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                                item { ChartView(state, users, Modifier.widthIn(max = 960.dp)) }
                            }
                            ViewType.FORM -> FormView(state, actions, onSubmit = { values -> vm.createRow(extra = values, open = false) })
                            ViewType.MAP -> MapView(state, actions)
                            ViewType.DASHBOARD -> DashboardView(state, users, onOpen = actions.open)
                        }
                    }
                }
            }
        }
    }

    if (filterSheet && ds != null) FilterSheet(ds, state.effectiveFilter, users, onDismiss = { filterSheet = false }, onApply = vm::setFilter)
    if (sortSheet && ds != null) SortSheet(ds, state.effectiveSorts, onDismiss = { sortSheet = false }, onApply = vm::setSorts)
    if (groupSheet && ds != null) GroupSheet(ds, state.effectiveGroup, required = state.type == ViewType.BOARD, onDismiss = { groupSheet = false }, onApply = vm::setGroup)
    if (propsSheet && ds != null) PropertiesSheet(ds, state.visibleProps(), onDismiss = { propsSheet = false }, onApply = vm::setProps, onNewProperty = { propsSheet = false; newProperty = true })
    if (newView && ds != null) NewViewSheet(ds, onDismiss = { newView = false }) { name, type -> vm.createView(name, type) { defaultConfig(type, it) } }
    if ((newProperty || editingSchema != null) && ds != null) PropertySchemaSheet(
        ds = ds,
        original = editingSchema,
        allSources = state.sources,
        onDismiss = { newProperty = false; editingSchema = null },
        onSave = { updated -> editingSchema?.let { vm.updateProperty(it, updated) } ?: vm.addProperty(updated) },
        onDelete = vm::deleteProperty,
    )
    rowMenu?.let { page ->
        RowMenuSheet(
            page,
            onDismiss = { rowMenu = null },
            onOpen = { navigator.navigate(PageRoute(page.id)) },
            onCopyLink = { context.copyText(page.url ?: NotionIds.webUrl(page.id)); messenger.show("Link copied") },
            onDuplicate = { vm.duplicateRow(page) },
            onDelete = {
                vm.trashRow(page)
                messenger.show("Moved to trash")
            },
        )
    }
    columnMenu?.let { prop ->
        val currentProps = state.localProps ?: state.config.properties.ifEmpty {
            state.source?.properties?.map { ViewProp(it.id, it.name, true) } ?: emptyList()
        }
        ColumnMenuSheet(
            prop,
            canGroup = state.type == ViewType.TABLE || state.type == ViewType.BOARD,
            onDismiss = { columnMenu = null },
            onSort = { asc -> vm.setSorts(listOf(SortSpec(property = prop.name, ascending = asc))) },
            onFilter = { filterSheet = true },
            onGroup = { groupSheet = true },
            onHide = {
                vm.setProps(currentProps.map { vp ->
                    if (vp.propertyId == prop.id || vp.propertyId == prop.name) vp.copy(visible = false) else vp
                })
            },
            onEdit = { editingSchema = prop },
        )
    }
    editingCell?.let { (page, prop) ->
        if (prop.type == PropType.TITLE) {
            editingCell = null
            navigator.navigate(PageRoute(page.id))
        } else PropertyEditor(prop, page.properties[prop.name], onDismiss = { editingCell = null }) { value -> vm.updateRow(page, prop.name, value) }
    }
    viewMenu?.let { view ->
        ViewMenuSheet(view, onDismiss = { viewMenu = null }, onRename = { vm.renameView(view, it) }, onDelete = { vm.deleteView(view) }, canEdit = editable && !view.id.startsWith("local-"))
    }
    if (iconPicker) IconPickerSheet(onDismiss = { iconPicker = false }, onPick = vm::setIcon)
    if (dbMenu && db != null) DatabaseMenuSheet(
        db = db,
        onDismiss = { dbMenu = false },
        onRename = vm::renameDatabase,
        onDescription = vm::setDescription,
        onIcon = { iconPicker = true },
        onLock = vm::setLocked,
        onInline = vm::setInline,
        onAddSource = { vm.addSource("New data source") },
        onNewProperty = { newProperty = true },
        onCopyLink = { context.copyText(db.url ?: NotionIds.webUrl(db.id)); messenger.show("Link copied") },
        onOpenInNotion = { db.url?.let { context.openUrl(it) } },
        onTrash = { vm.trashDatabase { navigator.back(); messenger.show("Database moved to trash") } },
    )
}

@Composable
private fun ViewTabs(views: List<NView>, selected: NView?, onSelect: (NView) -> Unit, onLongPress: (NView) -> Unit, onAdd: () -> Unit, canAdd: Boolean) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        items(views, key = { it.id }) { view ->
            val isSelected = view.id == selected?.id
            Surface(
                shape = RoundedCornerShape(if (isSelected) 22.dp else 14.dp),
                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(22.dp)).combinedClickable(onClick = { onSelect(view) }, onLongClick = { onLongPress(view) }),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(viewIcon(view.type), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(view.name, style = if (isSelected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
        if (canAdd) item {
            IconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, "New view") }
        }
    }
}

@Composable
private fun Toolbar(
    searching: Boolean,
    search: String,
    onSearch: (String) -> Unit,
    onToggleSearch: () -> Unit,
    filterCount: Int,
    sortCount: Int,
    showGroup: Boolean,
    onFilter: () -> Unit,
    onSort: () -> Unit,
    onGroup: () -> Unit,
    onProps: () -> Unit,
    canCreate: Boolean,
    onNew: () -> Unit,
    templates: List<com.manuscript.core.model.NTemplate>,
    newMenu: Boolean,
    onNewMenu: (Boolean) -> Unit,
    onTemplate: (com.manuscript.core.model.NTemplate) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(searching, label = "search", modifier = Modifier.weight(1f)) { open ->
            if (open) TextField(
                value = search,
                onValueChange = onSearch,
                placeholder = { Text("Search entries") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { IconButton(onClick = onToggleSearch) { Icon(Icons.Rounded.Close, "Close search") } },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth(),
            ) else Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleSearch) { Icon(Icons.Rounded.Search, "Search") }
                CountIcon(Icons.Rounded.FilterList, "Filter", filterCount, onFilter)
                CountIcon(Icons.AutoMirrored.Rounded.Sort, "Sort", sortCount, onSort)
                if (showGroup) IconButton(onClick = onGroup) { Icon(Icons.Rounded.ViewAgenda, "Group") }
                IconButton(onClick = onProps) { Icon(Icons.Rounded.Tune, "Properties") }
            }
        }
        if (canCreate) {
            Box {
                SplitButtonLayout(
                    leadingButton = {
                        SplitButtonDefaults.LeadingButton(onClick = onNew) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New")
                        }
                    },
                    trailingButton = {
                        val rotation by animateFloatAsState(if (newMenu) 180f else 0f, label = "chevron")
                        SplitButtonDefaults.TrailingButton(checked = newMenu, onCheckedChange = onNewMenu) {
                            Icon(Icons.Rounded.ExpandMore, "More options", Modifier.size(18.dp).rotate(rotation))
                        }
                    },
                )
                DropdownMenu(newMenu, onDismissRequest = { onNewMenu(false) }) {
                    DropdownMenuItem(text = { Text("Empty entry") }, leadingIcon = { Icon(Icons.Rounded.Add, null) }, onClick = { onNewMenu(false); onNew() })
                    if (templates.isNotEmpty()) HorizontalDivider()
                    templates.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.name + if (t.isDefault) " (default)" else "") },
                            leadingIcon = { Icon(Icons.Rounded.LibraryAdd, null) },
                            onClick = { onTemplate(t) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, count: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (count > 0) Badge { Text("$count") } }) {
            Icon(icon, label, tint = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ViewMenuSheet(view: NView, canEdit: Boolean, onDismiss: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(view.name) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(viewIcon(view.type), null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("${view.type.label} view", style = MaterialTheme.typography.titleLargeEmphasized)
            }
            if (canEdit) {
                TextField(
                    value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") },
                    shape = RoundedCornerShape(20.dp),
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
                Row {
                    TextButton(onClick = { onDelete(); onDismiss() }) {
                        Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text("Delete view", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.Button(onClick = { onRename(name); onDismiss() }, shapes = ButtonDefaults.shapes()) { Text("Save") }
                }
            } else Text("This view can't be edited here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun DatabaseMenuSheet(
    db: NDatabase,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onDescription: (String) -> Unit,
    onIcon: () -> Unit,
    onLock: (Boolean) -> Unit,
    onInline: (Boolean) -> Unit,
    onAddSource: () -> Unit,
    onNewProperty: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenInNotion: () -> Unit,
    onTrash: () -> Unit,
) {
    var title by remember { mutableStateOf(db.titleText) }
    var description by remember { mutableStateOf(db.description.plainText()) }
    ModalBottomSheet(onDismissRequest = { if (title != db.titleText) onRename(title); if (description != db.description.plainText()) onDescription(description); onDismiss() }) {
        androidx.compose.foundation.lazy.LazyColumn {
            item {
                Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).combinedClickable(onClick = onIcon), contentAlignment = Alignment.Center) {
                        NotionIcon(db.icon, 36.dp, kind = NodeKind.DATABASE)
                    }
                    Spacer(Modifier.width(12.dp))
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        textStyle = MaterialTheme.typography.headlineSmallEmphasized.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                TextField(
                    value = description, onValueChange = { description = it }, placeholder = { Text("Add a description") },
                    shape = RoundedCornerShape(20.dp),
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            item { SwitchRow("Lock database", "Prevent changes to views and properties", db.isLocked) { onLock(it) } }
            if (db.parent is com.manuscript.core.model.Parent.Page) item { SwitchRow("Show inline", "Display the database inside its page", db.isInline) { onInline(it) } }
            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            val actions = listOf(
                Triple("Change icon", Icons.Rounded.EmojiEmotions, onIcon),
                Triple("New property", Icons.Rounded.ViewColumn, onNewProperty),
                Triple("Add data source", Icons.Rounded.TableChart, onAddSource),
                Triple("Copy link", Icons.Rounded.ContentCopy, onCopyLink),
                Triple("Open in Notion", Icons.AutoMirrored.Rounded.OpenInNew, onOpenInNotion),
            )
            actions.forEach { (label, icon, action) ->
                item {
                    ListItem(headlineContent = { Text(label) }, leadingContent = { Icon(icon, null) }, modifier = Modifier.combinedClickable(onClick = { onDismiss(); action() }),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                }
            }
            item {
                ListItem(
                    headlineContent = { Text("Move to trash", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.combinedClickable(onClick = { onDismiss(); onTrash() }),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }
}
