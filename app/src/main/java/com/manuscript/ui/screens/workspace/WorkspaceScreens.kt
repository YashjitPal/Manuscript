package com.manuscript.ui.screens.workspace

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manuscript.core.model.NUser
import com.manuscript.core.model.NodeKind
import com.manuscript.core.model.NodeRef
import com.manuscript.core.util.Fmt
import com.manuscript.ui.LocalSession
import com.manuscript.ui.components.EmptyState
import com.manuscript.ui.components.ErrorState
import com.manuscript.ui.components.LocalMessenger
import com.manuscript.ui.components.NotionIcon
import com.manuscript.ui.components.SkeletonBlock
import com.manuscript.ui.components.UserAvatar
import com.manuscript.ui.navigation.LocalNavigator
import com.manuscript.ui.open
import com.manuscript.ui.theme.LocalHaptics
import com.manuscript.ui.util.openUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
private fun WorkspaceScaffold(title: String, content: @Composable (PaddingValues) -> Unit) {
    val navigator = LocalNavigator.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                navigationIcon = { if (navigator.backStack.size > 1) IconButton(onClick = { navigator.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                scrollBehavior = scroll,
            )
        },
        content = content,
    )
}

@Composable
fun TrashScreen() {
    val session = LocalSession.current
    val navigator = LocalNavigator.current
    val messenger = LocalMessenger.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val items = remember { mutableStateListOf<NodeRef>() }
    var cursor by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var generation by remember { mutableStateOf(0) }
    val list = rememberLazyListState()

    suspend fun load(reset: Boolean) {
        loading = true
        error = null
        try {
            val page = session.workspace.search(query, cursor = if (reset) null else cursor, inTrash = true)
            if (reset) items.clear()
            items += page.items.filter { it.inTrash }.filter { new -> items.none { it.id == new.id } }
            cursor = page.nextCursor
            hasMore = page.hasMore
        } catch (t: Throwable) {
            error = t
        } finally {
            loading = false
        }
    }

    LaunchedEffect(query, generation) {
        delay(if (query.isEmpty()) 0 else 300)
        load(reset = true)
    }
    val nearEnd by remember { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= items.size - 3 } }
    LaunchedEffect(nearEnd, hasMore, loading) { if (nearEnd && hasMore && !loading) load(reset = false) }

    fun restore(ref: NodeRef) {
        haptics.confirm()
        items.remove(ref)
        scope.launch {
            runCatching {
                if (ref.kind == NodeKind.PAGE) session.pages.restore(ref.id)
                else session.databases.updateDatabase(ref.databaseId ?: ref.id, trash = false)
            }.onSuccess {
                messenger.show("Restored “${ref.title.ifBlank { "Untitled" }}”", action = "Open") { navigator.open(ref.copy(inTrash = false)) }
            }.onFailure {
                messenger.error(it)
                generation++
            }
        }
    }

    WorkspaceScaffold("Trash") { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).wrapContentWidth().widthIn(max = 760.dp),
            state = list,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        ) {
            item {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search pages in trash") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                    singleLine = true,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }
            when {
                error != null && items.isEmpty() -> item { ErrorState(error!!, onRetry = { generation++ }) }
                loading && items.isEmpty() -> item { SkeletonBlock(Modifier.padding(8.dp), lines = 6) }
                items.isEmpty() -> item {
                    EmptyState(
                        Icons.Rounded.DeleteOutline,
                        if (query.isBlank()) "Trash is empty" else "Nothing found",
                        if (query.isBlank()) "Pages you delete show up here so you can bring them back." else "No deleted pages match “$query”.",
                        polygon = MaterialShapes.Pill,
                    )
                }
                else -> itemsIndexed(items, key = { _, it -> it.id }) { index, ref ->
                    SegmentedListItem(
                        onClick = { navigator.open(ref) },
                        shapes = ListItemDefaults.segmentedShapes(index, items.size),
                        leadingContent = { NotionIcon(ref.icon, 28.dp, kind = ref.kind) },
                        supportingContent = { Text("Edited ${Fmt.relative(ref.lastEditedTime)}", maxLines = 1) },
                        trailingContent = {
                            FilledTonalIconButton(onClick = { restore(ref) }) { Icon(Icons.Rounded.RestoreFromTrash, "Restore") }
                        },
                        modifier = Modifier.animateItem().padding(bottom = ListItemDefaults.SegmentedGap),
                    ) { Text(ref.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            if (loading && items.isNotEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
        }
    }
}

@Composable
fun FavoritesScreen() {
    val session = LocalSession.current
    val navigator = LocalNavigator.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val stored by session.workspace.favorites.collectAsState(initial = null)
    val items = remember { mutableStateListOf<NodeRef>() }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(stored) {
        val latest = stored ?: return@LaunchedEffect
        if (!dragging) {
            items.clear()
            items.addAll(latest)
        }
    }
    val list = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(list) { from, to ->
        val fromIndex = items.indexOfFirst { it.id == from.key }
        val toIndex = items.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            items.add(toIndex, items.removeAt(fromIndex))
            haptics.segment()
        }
    }

    WorkspaceScaffold("Favorites") { padding ->
        if (stored != null && items.isEmpty()) {
            EmptyState(
                Icons.Rounded.Star,
                "No favorites yet",
                "Tap the star on any page or database to keep it one tap away.",
                modifier = Modifier.padding(padding),
                polygon = MaterialShapes.Sunny,
            )
            return@WorkspaceScaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).wrapContentWidth().widthIn(max = 760.dp),
            state = list,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { index, ref ->
                ReorderableItem(reorder, key = ref.id) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "lift")
                    SegmentedListItem(
                        onClick = { navigator.open(ref) },
                        shapes = ListItemDefaults.segmentedShapes(index, items.size),
                        leadingContent = { NotionIcon(ref.icon, 28.dp, kind = ref.kind) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = {
                                    haptics.click()
                                    scope.launch { session.workspace.setFavorite(ref, false) }
                                }) { Icon(Icons.Rounded.Star, "Remove from favorites", tint = MaterialTheme.colorScheme.primary) }
                                Icon(
                                    Icons.Rounded.DragHandle,
                                    "Reorder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .draggableHandle(
                                            onDragStarted = { dragging = true; haptics.longPress() },
                                            onDragStopped = {
                                                dragging = false
                                                scope.launch { session.workspace.reorderFavorites(items.toList()) }
                                            },
                                        ),
                                )
                            }
                        },
                        modifier = Modifier.shadow(elevation, ListItemDefaults.segmentedShapes(index, items.size).shape),
                    ) { Text(ref.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
fun PeopleScreen() {
    val session = LocalSession.current
    val context = LocalContext.current
    val users by session.users.users.collectAsState()
    val available by session.users.listAvailable.collectAsState()
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        runCatching { session.users.loadAll() }
        loading = false
    }
    val people = users.values.filter { it.isPerson && !it.isGroup }.sortedBy { it.displayName.lowercase() }
    val bots = users.values.filter { it.isBot }.sortedBy { it.displayName.lowercase() }

    WorkspaceScaffold("People") { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).wrapContentWidth().widthIn(max = 760.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        ) {
            if (available == false) item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "This connection can't list workspace members, so only people you've come across in pages are shown.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            if (loading && users.isEmpty()) item { SkeletonBlock(Modifier.padding(8.dp), lines = 6) }
            else if (people.isEmpty() && bots.isEmpty()) item {
                EmptyState(Icons.Rounded.Group, "No people yet", "Members appear here as you browse pages they've edited.", polygon = MaterialShapes.Clover8Leaf)
            }
            peopleSection("Members", people) { user -> user.email?.let { context.openUrl("mailto:$it", inApp = false) } }
            peopleSection("Integrations", bots, null)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.peopleSection(title: String, users: List<NUser>, onEmail: ((NUser) -> Unit)?) {
    if (users.isEmpty()) return
    item {
        Text("$title · ${users.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 8.dp))
    }
    itemsIndexed(users, key = { _, it -> title + it.id }) { index, user ->
        SegmentedListItem(
            onClick = { if (onEmail != null && user.email != null) onEmail(user) },
            shapes = ListItemDefaults.segmentedShapes(index, users.size),
            leadingContent = { UserAvatar(user, 44.dp, shape = if (user.isBot) RoundedCornerShape(12.dp) else CircleShape) },
            supportingContent = (user.email ?: user.ownerUser?.displayName?.let { "Owned by $it" })?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            trailingContent = {
                if (user.isBot) Icon(Icons.Rounded.SmartToy, "Integration", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                else if (user.email != null && onEmail != null) Icon(Icons.Rounded.Mail, "Email", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
        ) { Text(user.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    item { Spacer(Modifier.size(4.dp)) }
}
