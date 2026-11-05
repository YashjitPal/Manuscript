package com.manuscript.ui.screens.library

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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manuscript.core.model.NodeRef
import com.manuscript.ui.LocalSession
import com.manuscript.ui.components.NotionIcon
import com.manuscript.ui.components.SectionHeader
import com.manuscript.ui.components.SkeletonBlock
import com.manuscript.ui.navigation.DatabaseRoute
import com.manuscript.ui.navigation.LocalNavigator
import com.manuscript.ui.navigation.PageRoute
import com.manuscript.ui.navigation.PeopleRoute
import com.manuscript.ui.navigation.SettingsRoute
import com.manuscript.ui.navigation.TrashRoute
import com.manuscript.ui.open
import com.manuscript.ui.screens.pickers.NewDatabaseSheet
import com.manuscript.ui.screens.pickers.NewPageSheet
import com.manuscript.ui.theme.LocalHaptics
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun LibraryScreen() {
    val session = LocalSession.current
    val tree = LocalTree.current
    val navigator = LocalNavigator.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val roots by tree.roots.collectAsState()
    val loading by tree.rootsLoading.collectAsState()
    val storedFavorites by session.workspace.favorites.collectAsState(emptyList())
    var favorites by remember { mutableStateOf(storedFavorites) }
    LaunchedEffect(storedFavorites) { favorites = storedFavorites }
    var addParent by remember { mutableStateOf<NodeRef?>(null) }
    var addRoot by remember { mutableStateOf(false) }
    var newDatabase by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<NodeRef?>(null) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
        val toKey = to.key as? String ?: return@rememberReorderableLazyListState
        if (!fromKey.startsWith("fav-") || !toKey.startsWith("fav-")) return@rememberReorderableLazyListState
        val list = favorites.toMutableList()
        val fromIndex = list.indexOfFirst { "fav-" + it.id == fromKey }
        val toIndex = list.indexOfFirst { "fav-" + it.id == toKey }
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
        list.add(toIndex, list.removeAt(fromIndex))
        favorites = list
        haptics.segment()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item(key = "title") {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Library", style = MaterialTheme.typography.displaySmallEmphasized, modifier = Modifier.weight(1f))
                IconButton(onClick = { navigator.navigate(SettingsRoute) }) { Icon(Icons.Rounded.Settings, "Settings") }
            }
        }
        item(key = "shortcuts") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AssistChip(onClick = { addRoot = true }, label = { Text("New page") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(AssistChipDefaults.IconSize)) }, shape = MaterialTheme.shapes.large)
                }
                item {
                    AssistChip(onClick = { newDatabase = true }, label = { Text("New database") }, leadingIcon = { Icon(Icons.Rounded.TableChart, null, Modifier.size(AssistChipDefaults.IconSize)) }, shape = MaterialTheme.shapes.large)
                }
                item {
                    AssistChip(onClick = { navigator.navigate(PeopleRoute) }, label = { Text("People") }, leadingIcon = { Icon(Icons.Rounded.Group, null, Modifier.size(AssistChipDefaults.IconSize)) }, shape = MaterialTheme.shapes.large)
                }
                item {
                    AssistChip(onClick = { navigator.navigate(TrashRoute) }, label = { Text("Trash") }, leadingIcon = { Icon(Icons.Rounded.Delete, null, Modifier.size(AssistChipDefaults.IconSize)) }, shape = MaterialTheme.shapes.large)
                }
            }
        }
        if (favorites.isNotEmpty()) {
            item(key = "fav-header") { SectionHeader("Favorites") }
            items(favorites, key = { "fav-" + it.id }) { ref ->
                ReorderableItem(reorder, key = "fav-" + ref.id) { dragging ->
                    Surface(
                        onClick = { navigator.open(ref) },
                        color = if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.large,
                        shadowElevation = if (dragging) 6.dp else 0.dp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            NotionIcon(ref.icon, 22.dp, kind = ref.kind)
                            Spacer(Modifier.width(12.dp))
                            Text(ref.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = {},
                                modifier = Modifier.draggableHandle(
                                    onDragStarted = { haptics.longPress() },
                                    onDragStopped = { scope.launch { session.workspace.reorderFavorites(favorites) } },
                                ),
                            ) { Icon(Icons.Rounded.DragIndicator, "Reorder", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        } else {
            item(key = "fav-empty") {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.StarOutline, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("Star pages to pin them here", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item(key = "ws-header") {
            SectionHeader(session.account.displayWorkspace, trailing = {
                if (loading) LoadingIndicator(Modifier.size(28.dp))
                else IconButton(onClick = { tree.refresh() }) { Icon(Icons.Rounded.Refresh, "Refresh") }
            })
        }
        val rootList = roots
        if (rootList == null) {
            item(key = "ws-loading") { SkeletonBlock(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), lines = 6) }
        } else if (rootList.isEmpty() && !loading) {
            item(key = "ws-empty") {
                Text(
                    "No pages yet. Share pages with your connection in Notion, or create one here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        } else {
            treeItems(
                rows = tree.rows(rootList),
                selectedId = (navigator.current as? PageRoute)?.id ?: (navigator.current as? DatabaseRoute)?.id,
                onOpen = { navigator.open(it) },
                onToggle = { tree.toggle(it) },
                onAdd = { addParent = it },
                onLongPress = { menuFor = it },
            )
        }
        item { Box(Modifier.size(1.dp)) }
    }

    if (addRoot || addParent != null) {
        NewPageSheet(
            onDismiss = { addRoot = false; addParent = null },
            onCreated = { page ->
                addParent?.let { tree.expand(it) }
                addRoot = false
                addParent = null
                navigator.navigate(PageRoute(page.id))
            },
            initialParent = addParent,
        )
    }
    if (newDatabase) {
        NewDatabaseSheet(onDismiss = { newDatabase = false }, onCreated = { db -> newDatabase = false; navigator.navigate(DatabaseRoute(db.id)) })
    }
    menuFor?.let { ref ->
        com.manuscript.ui.shell.NodeMenuSheet(ref, onDismiss = { menuFor = null }, onAddInside = { addParent = ref })
    }
}
