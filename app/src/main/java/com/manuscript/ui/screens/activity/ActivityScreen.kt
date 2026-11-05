package com.manuscript.ui.screens.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.manuscript.core.data.Session
import com.manuscript.core.model.NodeKind
import com.manuscript.core.model.NodeRef
import com.manuscript.core.model.parseInstant
import com.manuscript.core.util.Fmt
import com.manuscript.ui.LocalSession
import com.manuscript.ui.components.EmptyState
import com.manuscript.ui.components.ErrorState
import com.manuscript.ui.components.NotionIcon
import com.manuscript.ui.components.SkeletonBlock
import com.manuscript.ui.components.UserAvatar
import com.manuscript.ui.navigation.LocalNavigator
import com.manuscript.ui.open
import com.manuscript.ui.sessionViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

class ActivityViewModel(private val session: Session) : ViewModel() {
    data class UiState(
        val items: List<NodeRef> = emptyList(),
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val loadingMore: Boolean = false,
        val cursor: String? = null,
        val error: Throwable? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    val users = session.cache.users

    init {
        load()
    }

    fun load(refresh: Boolean = false) {
        _state.update { it.copy(loading = it.items.isEmpty(), refreshing = refresh, error = null) }
        viewModelScope.launch {
            runCatching { session.workspace.recentlyEdited(pageSize = 50) }
                .onSuccess { page ->
                    session.users.ensure(page.items.mapNotNull { it.lastEditedBy })
                    _state.value = UiState(items = page.items.filter { !it.inTrash }, loading = false, refreshing = false, cursor = page.nextCursor.takeIf { page.hasMore })
                }
                .onFailure { e -> _state.update { it.copy(loading = false, refreshing = false, error = e) } }
        }
    }

    fun more() {
        val s = _state.value
        val cursor = s.cursor ?: return
        if (s.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            runCatching { session.workspace.recentlyEdited(cursor, pageSize = 50) }
                .onSuccess { page ->
                    session.users.ensure(page.items.mapNotNull { it.lastEditedBy })
                    _state.update { it.copy(items = it.items + page.items.filter { r -> !r.inTrash }, cursor = page.nextCursor.takeIf { page.hasMore }, loadingMore = false) }
                }
                .onFailure { _state.update { it.copy(loadingMore = false) } }
        }
    }
}

@Composable
fun ActivityScreen() {
    val vm = sessionViewModel { ActivityViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val users by vm.users.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val me = LocalSession.current.account.userId
    var mineOnly by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .filter { it >= listState.layoutInfo.totalItemsCount - 6 }
            .collectLatest { vm.more() }
    }
    val visible = if (mineOnly && me != null) state.items.filter { it.lastEditedBy == me } else state.items
    val zone = ZoneId.systemDefault()
    val groups = visible.groupBy { parseInstant(it.lastEditedTime)?.atZone(zone)?.toLocalDate() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        Text("Updates", style = MaterialTheme.typography.displaySmallEmphasized, modifier = Modifier.padding(start = 24.dp, top = 16.dp))
        Text(
            "Everything recently edited in your workspace",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !mineOnly, onClick = { mineOnly = false }, label = { Text("Everyone") }, shape = MaterialTheme.shapes.large)
            if (me != null) FilterChip(selected = mineOnly, onClick = { mineOnly = true }, label = { Text("Edited by me") }, shape = MaterialTheme.shapes.large)
        }
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(true) }, modifier = Modifier.fillMaxSize()) {
            when {
                state.loading -> SkeletonBlock(Modifier.padding(24.dp), lines = 8)
                state.error != null && state.items.isEmpty() -> ErrorState(state.error!!, onRetry = { vm.load() })
                visible.isEmpty() -> EmptyState(Icons.Rounded.Bolt, "No updates", "Edits made in Notion will show up here.", polygon = MaterialShapes.Burst)
                else -> LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 120.dp)) {
                    groups.forEach { (date, refs) ->
                        item(key = "h-$date") {
                            Text(
                                date?.let { Fmt.friendlyDay(it) } ?: "Earlier",
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(refs, key = { it.id }) { ref ->
                            val editor = users[ref.lastEditedBy]
                            Surface(onClick = { navigator.open(ref) }, color = MaterialTheme.colorScheme.surface) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                                    Box(contentAlignment = Alignment.BottomEnd) {
                                        UserAvatar(editor, 40.dp)
                                        Box(
                                            Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                                            contentAlignment = Alignment.Center,
                                        ) { NotionIcon(ref.icon, 16.dp, kind = ref.kind) }
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(ref.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            listOfNotNull(
                                                editor?.displayName?.let { "Edited by $it" } ?: "Edited",
                                                if (ref.kind == NodeKind.PAGE) null else "Database",
                                                Fmt.relative(ref.lastEditedTime),
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (state.loadingMore) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator() } }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
