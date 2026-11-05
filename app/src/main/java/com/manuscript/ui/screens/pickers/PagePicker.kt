package com.manuscript.ui.screens.pickers

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manuscript.core.model.NodeKind
import com.manuscript.core.model.NodeRef
import com.manuscript.core.util.Fmt
import com.manuscript.ui.LocalSession
import com.manuscript.ui.components.NotionIcon
import com.manuscript.ui.components.ShapeBadge
import kotlinx.coroutines.delay

/** Searches pages and databases to pick a destination, link target or relation. */
@Composable
fun PagePickerSheet(
    title: String,
    onDismiss: () -> Unit,
    onPick: (NodeRef) -> Unit,
    allowPages: Boolean = true,
    allowDatabases: Boolean = true,
    workspaceOption: String? = null,
    onPickWorkspace: () -> Unit = {},
    excludeIds: Set<String> = emptySet(),
) {
    val session = LocalSession.current
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<NodeKind?>(if (!allowDatabases) NodeKind.PAGE else if (!allowPages) NodeKind.DATA_SOURCE else null) }
    var results by remember { mutableStateOf<List<NodeRef>?>(null) }
    var loading by remember { mutableStateOf(false) }
    val recents by session.workspace.recents.collectAsState(emptyList())
    val focus = remember { FocusRequester() }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(query, kind) {
        loading = true
        if (query.isNotBlank()) delay(280)
        val type = when (kind) {
            NodeKind.PAGE -> "page"
            NodeKind.DATA_SOURCE, NodeKind.DATABASE -> "data_source"
            null -> null
        }
        results = runCatching { session.workspace.search(query, type, newestFirst = if (query.isBlank()) true else null).items }
            .getOrElse { session.workspace.offlineSearch(query) }
            .filter { it.id !in excludeIds && !it.inTrash }
            .filter { (allowPages || it.kind != NodeKind.PAGE) && (allowDatabases || it.kind == NodeKind.PAGE) }
        loading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(12.dp))
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search pages and databases") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                shape = MaterialTheme.shapes.extraLarge,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
            )
            if (allowPages && allowDatabases) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null to "All", NodeKind.PAGE to "Pages", NodeKind.DATA_SOURCE to "Databases").forEach { (k, label) ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            label = { Text(label) },
                            shape = MaterialTheme.shapes.large,
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 560.dp)) {
                if (workspaceOption != null && query.isBlank()) {
                    item {
                        ListItem(
                            headlineContent = { Text(workspaceOption) },
                            supportingContent = { Text("Only visible to you") },
                            leadingContent = { ShapeBadge(Icons.Rounded.Lock, size = 36.dp) },
                            modifier = Modifier.clickable { onPickWorkspace() },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
                val shown = results ?: emptyList()
                val recentShown = if (query.isBlank()) recents.filter { r -> r.id !in excludeIds && shown.none { it.id == r.id } && (kind == null || r.kind == kind || (kind == NodeKind.DATA_SOURCE && r.kind == NodeKind.DATABASE)) }.take(5) else emptyList()
                if (recentShown.isNotEmpty()) {
                    item { PickerLabel("Recent") }
                    items(recentShown, key = { "r-" + it.id }) { ref -> PickerRow(ref) { onPick(ref) } }
                }
                if (shown.isNotEmpty()) {
                    item { PickerLabel(if (query.isBlank()) "Recently edited" else "Results") }
                    items(shown, key = { it.id }) { ref -> PickerRow(ref) { onPick(ref) } }
                } else if (!loading && query.isNotBlank()) {
                    item {
                        Text(
                            "No matches for “$query”",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                if (loading && results == null) {
                    item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() } }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun PickerLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
fun PickerRow(ref: NodeRef, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(ref.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val kind = if (ref.kind == NodeKind.PAGE) "Page" else "Database"
            Text(listOfNotNull(kind, ref.lastEditedTime?.let { "Edited " + Fmt.relative(it).lowercase() }).joinToString(" · "))
        },
        leadingContent = { NotionIcon(ref.icon, 28.dp, kind = ref.kind) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}