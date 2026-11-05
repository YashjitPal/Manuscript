package com.manuscript.ui.screens.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.manuscript.core.data.Session
import com.manuscript.core.model.NodeKind
import com.manuscript.core.model.NodeRef
import com.manuscript.core.model.Parent
import com.manuscript.core.net.isOffline
import com.manuscript.core.util.Fmt
import com.manuscript.ui.components.EmptyState
import com.manuscript.ui.components.ErrorState
import com.manuscript.ui.components.NotionIcon
import com.manuscript.ui.components.SectionHeader
import com.manuscript.ui.navigation.LocalNavigator
import com.manuscript.ui.open
import com.manuscript.ui.sessionViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchSort(val label: String, val newestFirst: Boolean?) {
    RELEVANCE("Best match", null),
    NEWEST("Recently edited", true),
    OLDEST("Oldest edited", false),
}

class SearchViewModel(private val session: Session) : ViewModel() {
    data class UiState(
        val results: List<NodeRef> = emptyList(),
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val nextCursor: String? = null,
        val offline: Boolean = false,
        val error: Throwable? = null,
        val searched: Boolean = false,
    )

    val query = MutableStateFlow("")
    val kind = MutableStateFlow<NodeKind?>(null)
    val sort = MutableStateFlow(SearchSort.RELEVANCE)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    val history = session.workspace.searchHistory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recents = session.workspace.recents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            combine(query.debounce(260).distinctUntilChanged(), kind, sort) { q, k, s -> Triple(q.trim(), k, s) }
                .collectLatest { (q, k, s) -> search(q, k, s) }
        }
    }

    private fun type(k: NodeKind?) = when (k) {
        NodeKind.PAGE -> "page"
        NodeKind.DATA_SOURCE, NodeKind.DATABASE -> "data_source"
        null -> null
    }

    private fun search(q: String, k: NodeKind?, s: SearchSort) {
        viewModelScope.launch {
            if (q.isEmpty()) {
                _state.value = UiState()
                return@launch
            }
            _state.update { it.copy(loading = true, error = null) }
            try {
                val page = session.workspace.search(q, type(k), newestFirst = s.newestFirst)
                _state.value = UiState(results = page.items.filter { !it.inTrash }, nextCursor = page.nextCursor.takeIf { page.hasMore }, searched = true)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (t.isOffline) {
                    val local = session.workspace.offlineSearch(q).filter { k == null || it.kind == k || (k == NodeKind.DATA_SOURCE && it.kind == NodeKind.DATABASE) }
                    _state.value = UiState(results = local, offline = true, searched = true)
                } else _state.value = UiState(error = t, searched = true)
            }
        }
    }

    fun retry() = search(query.value.trim(), kind.value, sort.value)

    fun loadMore() {
        val s = _state.value
        val cursor = s.nextCursor ?: return
        if (s.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            runCatching { session.workspace.search(query.value.trim(), type(kind.value), cursor, sort.value.newestFirst) }
                .onSuccess { page ->
                    _state.update { it.copy(results = it.results + page.items.filter { r -> !r.inTrash }, nextCursor = page.nextCursor.takeIf { page.hasMore }, loadingMore = false) }
                }
                .onFailure { _state.update { it.copy(loadingMore = false) } }
        }
    }

    fun remember(q: String) = viewModelScope.launch { session.workspace.rememberSearch(q) }
    fun forget(q: String) = viewModelScope.launch { session.workspace.forgetSearch(q) }
    fun clearHistory() = viewModelScope.launch { session.workspace.clearSearches() }

    fun parentTitle(ref: NodeRef): String? = when (val p = ref.parent) {
        Parent.Workspace -> "Workspace"
        is Parent.Page -> session.cache.displayTitle(p.id)
        is Parent.DataSource -> session.cache.displayTitle(p.id)
        is Parent.Database -> session.cache.displayTitle(p.id)
        else -> null
    }
}

@Composable
fun SearchScreen() {
    val vm = sessionViewModel { SearchViewModel(it) }
    val navigator = LocalNavigator.current
    val query by vm.query.collectAsStateWithLifecycle()
    val kind by vm.kind.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(listState, state.nextCursor) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .filter { it >= listState.layoutInfo.totalItemsCount - 4 }
            .collectLatest { vm.loadMore() }
    }

    fun openResult(ref: NodeRef) {
        if (query.isNotBlank()) vm.remember(query.trim())
        keyboard?.hide()
        navigator.open(ref)
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        Text(
            "Search",
            style = MaterialTheme.typography.displaySmallEmphasized,
            modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 12.dp),
        )
        TextField(
            value = query,
            onValueChange = { vm.query.value = it },
            placeholder = { Text("Search your workspace") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Rounded.Close, "Clear") }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.remember(query.trim()); keyboard?.hide() }),
            shape = CircleShape,
            textStyle = MaterialTheme.typography.titleMedium,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
        )
        ResponsiveSearchFilterRow(
            kind = kind,
            onKindChange = { vm.kind.value = it },
            sort = sort,
            onSortChange = { vm.sort.value = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        )
        AnimatedVisibility(state.offline) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudOff, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Offline — showing pages saved on this device", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            when {
                query.isBlank() -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    if (history.isNotEmpty()) {
                        item {
                            SectionHeader("Recent searches", trailing = { TextButton(onClick = { vm.clearHistory() }) { Text("Clear") } })
                        }
                        item {
                            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                history.forEach { h ->
                                    InputChip(
                                        selected = false,
                                        onClick = { vm.query.value = h },
                                        label = { Text(h) },
                                        leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(InputChipDefaults.IconSize)) },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Rounded.Close,
                                                "Remove $h",
                                                Modifier.size(InputChipDefaults.IconSize).clip(CircleShape).clickable { vm.forget(h) },
                                            )
                                        },
                                        shape = MaterialTheme.shapes.large,
                                    )
                                }
                            }
                        }
                    }
                    if (recents.isNotEmpty()) {
                        item { SectionHeader("Recently visited") }
                        itemsIndexed(recents.take(12), key = { _, r -> "recent-" + r.id }) { index, ref ->
                            ResultRow(ref, null, null, index, minOf(12, recents.size)) { openResult(ref) }
                        }
                    }
                    if (history.isEmpty() && recents.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Rounded.ManageSearch,
                                title = "Find anything",
                                message = "Search titles across every page and database you can access.",
                                polygon = MaterialShapes.Sunny,
                            )
                        }
                    }
                }
                state.error != null -> ErrorState(state.error!!, onRetry = { vm.retry() })
                state.loading && state.results.isEmpty() -> Box(Modifier.fillMaxSize().padding(top = 64.dp), contentAlignment = Alignment.TopCenter) {
                    LoadingIndicator(Modifier.size(56.dp))
                }
                state.searched && state.results.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "No results",
                    message = "Nothing matches “${query.trim()}”. Notion search matches titles of pages shared with your connection.",
                    polygon = MaterialShapes.Ghostish,
                )
                else -> LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)) {
                    itemsIndexed(state.results, key = { _, r -> r.id }) { index, ref ->
                        Box(Modifier.padding(bottom = ListItemDefaults.SegmentedGap)) {
                            ResultRow(ref, query.trim(), vm.parentTitle(ref), index, state.results.size, inset = false) { openResult(ref) }
                        }
                    }
                    if (state.loadingMore) {
                        item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator() } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(ref: NodeRef, highlight: String?, parent: String?, index: Int, count: Int, inset: Boolean = true, onClick: () -> Unit) {
    val title = ref.title.ifBlank { "Untitled" }
    val highlighted = remember(title, highlight) {
        buildAnnotatedString {
            val q = highlight?.takeIf { it.isNotBlank() }
            val start = q?.let { title.indexOf(it, ignoreCase = true) } ?: -1
            if (q == null || start < 0) append(title)
            else {
                append(title.substring(0, start))
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(title.substring(start, start + q.length)) }
                append(title.substring(start + q.length))
            }
        }
    }
    Box(if (inset) Modifier.padding(horizontal = 16.dp, vertical = ListItemDefaults.SegmentedGap / 2) else Modifier) {
        SegmentedListItem(
            onClick = onClick,
            shapes = ListItemDefaults.segmentedShapes(index, count),
            leadingContent = { NotionIcon(ref.icon, 28.dp, kind = ref.kind) },
            supportingContent = {
                Text(
                    listOfNotNull(
                        parent?.let { "in $it" },
                        if (ref.kind == NodeKind.PAGE) null else "Database",
                        ref.lastEditedTime?.let { Fmt.relative(it) },
                    ).joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        ) { Text(highlighted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

private enum class SortDisplayMode {
    FULL,
    SHORT,
    ICON_ONLY,
}

@Composable
private fun SearchSortControl(
    mode: SortDisplayMode,
    sort: SearchSort,
    onSortChange: (SearchSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        when (mode) {
            SortDisplayMode.FULL -> {
                AssistChip(
                    onClick = { expanded = true },
                    label = { Text(sort.label, maxLines = 1, softWrap = false) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp)) },
                    shape = MaterialTheme.shapes.large,
                    colors = if (sort != SearchSort.RELEVANCE) {
                        AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.primary,
                            leadingIconContentColor = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        AssistChipDefaults.assistChipColors()
                    },
                )
            }
            SortDisplayMode.SHORT -> {
                AssistChip(
                    onClick = { expanded = true },
                    label = { Text("Sort", maxLines = 1, softWrap = false) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp)) },
                    shape = MaterialTheme.shapes.large,
                    colors = if (sort != SearchSort.RELEVANCE) {
                        AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.primary,
                            leadingIconContentColor = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        AssistChipDefaults.assistChipColors()
                    },
                )
            }
            SortDisplayMode.ICON_ONLY -> {
                IconButton(
                    onClick = { expanded = true },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = "Sort (${sort.label})",
                        modifier = Modifier.size(20.dp),
                        tint = if (sort != SearchSort.RELEVANCE) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SearchSort.entries.forEach { s ->
                val selected = s == sort
                DropdownMenuItem(
                    text = {
                        Text(
                            text = s.label,
                            style = if (selected) {
                                MaterialTheme.typography.labelLargeEmphasized
                            } else {
                                MaterialTheme.typography.labelLarge
                            },
                        )
                    },
                    onClick = {
                        onSortChange(s)
                        expanded = false
                    },
                    trailingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun ResponsiveSearchFilterRow(
    kind: NodeKind?,
    onKindChange: (NodeKind?) -> Unit,
    sort: SearchSort,
    onSortChange: (SearchSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val maxWidth = constraints.maxWidth
        val spacingPx = 8.dp.roundToPx()

        // 1. Measure the category chips: All, Pages, Databases
        val chipsPlaceables = subcompose("chips") {
            listOf(
                null to "All",
                NodeKind.PAGE to "Pages",
                NodeKind.DATA_SOURCE to "Databases",
            ).forEach { (k, label) ->
                FilterChip(
                    selected = kind == k,
                    onClick = { onKindChange(k) },
                    label = { Text(label, maxLines = 1, softWrap = false) },
                    shape = MaterialTheme.shapes.large,
                )
            }
        }.map { it.measure(Constraints()) }

        val totalChipsWidth = chipsPlaceables.sumOf { it.width } +
            (chipsPlaceables.size - 1).coerceAtLeast(0) * spacingPx
        val availableForSort = maxWidth - totalChipsWidth - spacingPx

        // 2. Responsively determine the Sort display mode based on available space
        val mode = run {
            // Level 1: Full label (e.g. "Best match", "Recently edited")
            val fullMeasureWidth = subcompose("measure_full") {
                AssistChip(
                    onClick = {},
                    label = { Text(sort.label, maxLines = 1, softWrap = false) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp)) },
                    shape = MaterialTheme.shapes.large,
                )
            }.first().measure(Constraints()).width

            if (fullMeasureWidth <= availableForSort) {
                SortDisplayMode.FULL
            } else {
                // Level 2: "Sort" text
                val shortMeasureWidth = subcompose("measure_short") {
                    AssistChip(
                        onClick = {},
                        label = { Text("Sort", maxLines = 1, softWrap = false) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp)) },
                        shape = MaterialTheme.shapes.large,
                    )
                }.first().measure(Constraints()).width

                if (shortMeasureWidth <= availableForSort) {
                    SortDisplayMode.SHORT
                } else {
                    // Level 3: Just the sort icon without the pill
                    SortDisplayMode.ICON_ONLY
                }
            }
        }

        // 3. Subcompose active sort control with DropdownMenu
        val sortPlaceables = subcompose("sort_active") {
            SearchSortControl(
                mode = mode,
                sort = sort,
                onSortChange = onSortChange,
            )
        }.map { it.measure(Constraints()) }

        val sortPlaceable = sortPlaceables.first()
        val layoutHeight = maxOf(
            chipsPlaceables.maxOfOrNull { it.height } ?: 0,
            sortPlaceable.height,
        )

        layout(maxWidth, layoutHeight) {
            var currentX = 0
            chipsPlaceables.forEach { chip ->
                val y = (layoutHeight - chip.height) / 2
                chip.placeRelative(currentX, y)
                currentX += chip.width + spacingPx
            }

            val sortX = maxOf(currentX, maxWidth - sortPlaceable.width)
            val sortY = (layoutHeight - sortPlaceable.height) / 2
            sortPlaceable.placeRelative(sortX, sortY)
        }
    }
}