package com.manuscript.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.manuscript.core.data.ChangeEvent
import com.manuscript.core.data.Session
import com.manuscript.core.model.NodeKind
import com.manuscript.core.model.NodeRef
import com.manuscript.core.util.Fmt
import com.manuscript.ui.LocalSession
import com.manuscript.ui.LocalWideLayout
import com.manuscript.ui.components.ErrorState
import com.manuscript.ui.components.NotionIcon
import com.manuscript.ui.components.NotionImage
import com.manuscript.ui.components.SectionHeader
import com.manuscript.ui.components.ShapeBadge
import com.manuscript.ui.components.SkeletonBlock
import com.manuscript.ui.components.UserAvatar
import com.manuscript.ui.components.EmptyState
import com.manuscript.ui.navigation.AccountsRoute
import com.manuscript.ui.navigation.DatabaseRoute
import com.manuscript.ui.navigation.LocalNavigator
import com.manuscript.ui.navigation.PageRoute
import com.manuscript.ui.navigation.SettingsRoute
import com.manuscript.ui.navigation.Tab
import com.manuscript.ui.open
import com.manuscript.ui.screens.pickers.NewDatabaseSheet
import com.manuscript.ui.screens.pickers.NewPageSheet
import com.manuscript.ui.sessionViewModel
import com.manuscript.ui.theme.LocalHaptics
import com.manuscript.ui.theme.LocalNotionPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HomeViewModel(private val session: Session) : ViewModel() {
    data class UiState(
        val edited: List<NodeRef> = emptyList(),
        val databases: List<NodeRef> = emptyList(),
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val error: Throwable? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    val recents = session.workspace.recents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favorites = session.workspace.favorites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val users = session.cache.users

    init {
        load()
        viewModelScope.launch {
            session.events.collect { e ->
                if (e is ChangeEvent.PageCreated || e is ChangeEvent.PageTrashed || e is ChangeEvent.PageRestored) load(silent = true)
            }
        }
    }

    fun load(refresh: Boolean = false, silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _state.update { it.copy(refreshing = refresh, error = null) }
            try {
                coroutineScope {
                    val edited = async { session.workspace.recentlyEdited(pageSize = 30) }
                    val sources = async { runCatching { session.workspace.search("", objectType = "data_source", newestFirst = true).items }.getOrDefault(emptyList()) }
                    val e = edited.await().items.filter { !it.inTrash }
                    session.users.ensure(e.mapNotNull { it.lastEditedBy })
                    _state.update { it.copy(edited = e, databases = sources.await(), loading = false, refreshing = false, error = null) }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.update { it.copy(loading = false, refreshing = false, error = if (it.edited.isEmpty()) t else null) }
            }
        }
    }
}

@Composable
fun HomeScreen() {
    val vm = sessionViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val users by vm.users.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val wide = LocalWideLayout.current
    var newPage by remember { mutableStateOf(false) }
    var quickNote by remember { mutableStateOf(false) }
    var newDatabase by remember { mutableStateOf(false) }
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHaptics.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        floatingActionButton = {
            if (!wide) {
                FloatingActionButtonMenu(
                    expanded = fabExpanded,
                    button = {
                        ToggleFloatingActionButton(checked = fabExpanded, onCheckedChange = { fabExpanded = it; haptics.click() }) {
                            val icon by remember { derivedStateOf { if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add } }
                            Icon(rememberVectorPainter(icon), "Create", Modifier.animateIcon({ checkedProgress }))
                        }
                    },
                ) {
                    FloatingActionButtonMenuItem(onClick = { fabExpanded = false; newPage = true }, text = { Text("New page") }, icon = { Icon(Icons.AutoMirrored.Rounded.NoteAdd, null) })
                    FloatingActionButtonMenuItem(onClick = { fabExpanded = false; quickNote = true }, text = { Text("Quick note") }, icon = { Icon(Icons.Rounded.EditNote, null) })
                    FloatingActionButtonMenuItem(onClick = { fabExpanded = false; newDatabase = true }, text = { Text("New database") }, icon = { Icon(Icons.Rounded.TableChart, null) })
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { vm.load(refresh = true) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val twoColumn = maxWidth >= 900.dp
                LazyColumn(
                    modifier = Modifier.widthIn(max = 1180.dp).fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp),
                ) {
                    item(key = "header") { HomeHeader() }
                    item(key = "actions") {
                        QuickActions(
                            onNewPage = { newPage = true },
                            onQuickNote = { quickNote = true },
                            onNewDatabase = { newDatabase = true },
                            onSearch = { navigator.select(Tab.SEARCH) },
                        )
                    }
                    if (recents.isNotEmpty()) {
                        item(key = "recents-h") { SectionHeader("Jump back in") }
                        item(key = "recents") { RecentCarousel(recents.take(12)) { navigator.open(it) } }
                    }
                    when {
                        state.loading && state.edited.isEmpty() -> item(key = "loading") {
                            SkeletonBlock(Modifier.padding(24.dp), lines = 6)
                        }
                        state.error != null -> item(key = "error") { ErrorState(state.error!!, onRetry = { vm.load() }) }
                        state.edited.isEmpty() && recents.isEmpty() -> item(key = "empty") {
                            EmptyState(
                                icon = Icons.Rounded.WavingHand,
                                title = "Nothing here yet",
                                message = "Share pages with your connection in Notion, or create your first page.",
                            )
                        }
                        twoColumn -> item(key = "columns") {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                                Column(Modifier.weight(1.4f)) {
                                    SectionHeader("Recently edited")
                                    EditedList(state.edited.take(12), users) { navigator.open(it) }
                                }
                                Column(Modifier.weight(1f)) {
                                    if (favorites.isNotEmpty()) {
                                        SectionHeader("Favorites")
                                        FavoritesGrid(favorites) { navigator.open(it) }
                                    }
                                    if (state.databases.isNotEmpty()) {
                                        SectionHeader("Databases")
                                        DatabaseGrid(state.databases.take(8)) { navigator.open(it) }
                                    }
                                }
                            }
                        }
                        else -> {
                            if (favorites.isNotEmpty()) {
                                item(key = "fav-h") { SectionHeader("Favorites") }
                                item(key = "fav") { FavoritesGrid(favorites) { navigator.open(it) } }
                            }
                            item(key = "edited-h") { SectionHeader("Recently edited") }
                            item(key = "edited") { EditedList(state.edited.take(10), users) { navigator.open(it) } }
                            if (state.databases.isNotEmpty()) {
                                item(key = "db-h") { SectionHeader("Databases") }
                                item(key = "db") { DatabaseRow(state.databases) { navigator.open(it) } }
                            }
                        }
                    }
                }
            }
        }
    }

    if (newPage || quickNote) {
        NewPageSheet(
            onDismiss = { newPage = false; quickNote = false },
            onCreated = { page -> newPage = false; quickNote = false; navigator.navigate(PageRoute(page.id)) },
            quickNote = quickNote,
        )
    }
    if (newDatabase) {
        NewDatabaseSheet(
            onDismiss = { newDatabase = false },
            onCreated = { db -> newDatabase = false; navigator.navigate(DatabaseRoute(db.id)) },
        )
    }
}

@Composable
private fun HomeHeader() {
    val session = LocalSession.current
    val navigator = LocalNavigator.current
    val account = session.account
    val wide = LocalWideLayout.current
    val firstName = account.userName?.substringBefore(' ')?.takeIf { it.isNotBlank() } ?: "there"
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!wide) {
                Surface(
                    onClick = { navigator.navigate(AccountsRoute) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(
                            com.manuscript.core.model.NUser(account.userId ?: account.id, account.userName, account.userAvatar, "person"),
                            32.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(account.displayWorkspace, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
                        Icon(Icons.Rounded.ExpandMore, null, Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (!wide) {
                IconButton(onClick = { navigator.navigate(SettingsRoute) }) { Icon(Icons.Rounded.Settings, "Settings") }
            }
        }
        Spacer(Modifier.height(if (wide) 24.dp else 28.dp))
        Text(Fmt.greeting() + ",", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(firstName, style = MaterialTheme.typography.displayMediumEmphasized, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(
            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun QuickActions(onNewPage: () -> Unit, onQuickNote: () -> Unit, onNewDatabase: () -> Unit, onSearch: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ActionTile("New page", Icons.AutoMirrored.Rounded.NoteAdd, scheme.primaryContainer, scheme.onPrimaryContainer, MaterialShapes.Cookie9Sided, onNewPage) }
        item { ActionTile("Quick note", Icons.Rounded.EditNote, scheme.tertiaryContainer, scheme.onTertiaryContainer, MaterialShapes.Clover4Leaf, onQuickNote) }
        item { ActionTile("Database", Icons.Rounded.TableChart, scheme.secondaryContainer, scheme.onSecondaryContainer, MaterialShapes.Square, onNewDatabase) }
        item { ActionTile("Search", Icons.Rounded.Search, scheme.surfaceContainerHighest, scheme.onSurface, MaterialShapes.Sunny, onSearch) }
    }
}

@Composable
private fun ActionTile(
    label: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    polygon: androidx.graphics.shapes.RoundedPolygon,
    onClick: () -> Unit,
) {
    val haptics = LocalHaptics.current
    Surface(
        onClick = { haptics.click(); onClick() },
        shape = RoundedCornerShape(28.dp),
        color = container,
        contentColor = content,
        modifier = Modifier.width(132.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            ShapeBadge(icon, polygon = polygon, size = 44.dp, container = content.copy(alpha = 0.14f), content = content)
            Spacer(Modifier.height(18.dp))
            Text(label, style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 1)
        }
    }
}

@Composable
private fun RecentCarousel(items: List<NodeRef>, onOpen: (NodeRef) -> Unit) {
    val palette = LocalNotionPalette.current
    val state = rememberCarouselState { items.size }
    HorizontalMultiBrowseCarousel(
        state = state,
        preferredItemWidth = 220.dp,
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth().height(196.dp).padding(vertical = 4.dp),
    ) { index ->
        val ref = items[index]
        val hues = com.manuscript.ui.theme.ChartHues
        val hue = hues[(ref.id.hashCode() and Int.MAX_VALUE) % hues.size]
        Box(
            Modifier
                .fillMaxSize()
                .maskClip(MaterialTheme.shapes.extraLarge)
                .clickable { onOpen(ref) },
        ) {
            val cover = ref.cover
            if (cover != null) {
                NotionImage(cover.url, Modifier.fillMaxSize())
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(palette.option(hue), palette.background(hue))),
                    ),
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.62f)),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                ) { NotionIcon(ref.icon, 26.dp, kind = ref.kind, fallbackTint = Color.DarkGray) }
                Spacer(Modifier.height(8.dp))
                Text(
                    ref.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FavoritesGrid(items: List<NodeRef>, onOpen: (NodeRef) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { ref ->
            Surface(
                onClick = { onOpen(ref) },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    NotionIcon(ref.icon, 22.dp, kind = ref.kind)
                    Spacer(Modifier.width(8.dp))
                    Text(ref.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Star, null, Modifier.size(14.dp), tint = LocalNotionPalette.current.accent(com.manuscript.core.model.Hue.YELLOW))
                }
            }
        }
    }
}

@Composable
private fun EditedList(items: List<NodeRef>, users: Map<String, com.manuscript.core.model.NUser>, onOpen: (NodeRef) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        items.forEachIndexed { index, ref ->
            val editor = users[ref.lastEditedBy]
            SegmentedListItem(
                onClick = { onOpen(ref) },
                shapes = ListItemDefaults.segmentedShapes(index, items.size),
                leadingContent = {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) { NotionIcon(ref.icon, 26.dp, kind = ref.kind) }
                },
                supportingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (editor != null) {
                            UserAvatar(editor, 16.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            listOfNotNull(
                                if (ref.kind == NodeKind.PAGE) null else "Database",
                                ref.lastEditedTime?.let { "Edited " + Fmt.relative(it).lowercase() },
                                editor?.displayName?.substringBefore(' '),
                            ).joinToString(" · "),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                trailingContent = { Icon(Icons.Rounded.History, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline) },
            ) {
                Text(ref.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun DatabaseCard(ref: NodeRef, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier.size(48.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { NotionIcon(ref.icon, 28.dp, kind = NodeKind.DATABASE, fallbackTint = MaterialTheme.colorScheme.onSecondaryContainer) }
            Spacer(Modifier.height(14.dp))
            Text(ref.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                ref.lastEditedTime?.let { "Updated " + Fmt.relative(it).lowercase() } ?: "Database",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DatabaseRow(items: List<NodeRef>, onOpen: (NodeRef) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.id }) { ref -> DatabaseCard(ref, Modifier.width(172.dp)) { onOpen(ref) } }
    }
}

@Composable
private fun DatabaseGrid(items: List<NodeRef>, onOpen: (NodeRef) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { ref -> DatabaseCard(ref, Modifier.weight(1f).aspectRatio(1.25f)) { onOpen(ref) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}