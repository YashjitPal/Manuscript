package com.manuscript.ui.screens.page

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.insert
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.automirrored.rounded.Toc
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manuscript.core.data.ReadingFont
import com.manuscript.core.model.BlockType
import com.manuscript.core.model.NBlock
import com.manuscript.core.model.NColor
import com.manuscript.core.model.NIcon
import com.manuscript.core.model.NPage
import com.manuscript.core.model.NodeKind
import com.manuscript.core.model.PropType
import com.manuscript.core.model.PropertySchema
import com.manuscript.core.model.RichText
import com.manuscript.core.model.plainText
import com.manuscript.core.model.toRef
import com.manuscript.core.model.toRich
import com.manuscript.core.util.Fmt
import com.manuscript.core.util.NotionIds
import com.manuscript.ui.LocalContainer
import com.manuscript.ui.LocalSession
import com.manuscript.ui.LocalWideLayout
import com.manuscript.ui.components.ErrorState
import com.manuscript.ui.components.LocalLinkHandler
import com.manuscript.ui.components.LocalMessenger
import com.manuscript.ui.components.NotionIcon
import com.manuscript.ui.components.PageCover
import com.manuscript.ui.components.SkeletonBlock
import com.manuscript.ui.components.SkeletonLine
import com.manuscript.ui.components.UserAvatar
import com.manuscript.ui.navigation.DatabaseRoute
import com.manuscript.ui.navigation.LocalNavigator
import com.manuscript.ui.navigation.MediaRoute
import com.manuscript.ui.navigation.PageRoute
import com.manuscript.ui.screens.page.blocks.BlockRow
import com.manuscript.ui.screens.page.blocks.BlockScope
import com.manuscript.ui.screens.page.blocks.TextEditDialog
import com.manuscript.ui.screens.page.editor.Attrs
import com.manuscript.ui.screens.page.editor.EditorCallbacks
import com.manuscript.ui.screens.page.editor.MenuKind
import com.manuscript.ui.screens.pickers.CoverPickerSheet
import com.manuscript.ui.screens.pickers.IconPickerSheet
import com.manuscript.ui.screens.pickers.NewDatabaseSheet
import com.manuscript.ui.screens.pickers.PagePickerSheet
import com.manuscript.ui.screens.properties.PropertyEditor
import com.manuscript.ui.screens.properties.PropertyRow
import com.manuscript.ui.sessionViewModel
import com.manuscript.ui.theme.LocalAppSettings
import com.manuscript.ui.theme.LocalHaptics
import com.manuscript.ui.theme.LocalReading
import com.manuscript.ui.util.copyText
import com.manuscript.ui.util.openUrl
import com.manuscript.ui.util.shareFile
import com.manuscript.ui.util.shareText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private data class MenuState(val kind: MenuKind, val key: String, val start: Int, val query: String)
private data class MediaRequest(val type: BlockType, val key: String?, val replace: Boolean)
private data class CommentsTarget(val id: String, val isBlock: Boolean, val title: String)

/** Readable, centered column for page content. */
fun Modifier.readingColumn(maxWidth: Dp, horizontal: Dp): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = maxWidth).padding(horizontal = horizontal)

@Composable
fun PageScreen(route: PageRoute) {
    val session = LocalSession.current
    val container = LocalContainer.current
    val navigator = LocalNavigator.current
    val wide = LocalWideLayout.current
    val settings = LocalAppSettings.current
    val messenger = LocalMessenger.current
    val links = LocalLinkHandler.current
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val reading = LocalReading.current
    val vm = sessionViewModel(key = "page-" + route.id) { PageViewModel(it, route.id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val favorite by vm.isFavorite.collectAsStateWithLifecycle()
    val users by vm.users.collectAsStateWithLifecycle()
    val listState = rememberLazyListState(cacheWindow = LazyLayoutCacheWindow(aheadFraction = 1.5f, behindFraction = 0.5f))
    val scope = rememberCoroutineScope()

    var blockMenuKey by remember { mutableStateOf<String?>(null) }
    var pageMenu by remember { mutableStateOf(false) }
    var iconPicker by remember { mutableStateOf(false) }
    var coverPicker by remember { mutableStateOf(false) }
    var calloutIconFor by remember { mutableStateOf<String?>(null) }
    var textColorSheet by remember { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf(false) }
    var turnIntoFor by remember { mutableStateOf<String?>(null) }
    var comments by remember { mutableStateOf<CommentsTarget?>(null) }
    var menu by remember { mutableStateOf<MenuState?>(null) }
    var moving by remember { mutableStateOf(false) }
    var linkPageFor by remember { mutableStateOf<String?>(null) }
    var media by remember { mutableStateOf<MediaRequest?>(null) }
    var urlFor by remember { mutableStateOf<Pair<BlockType, String?>?>(null) }
    var newDatabase by remember { mutableStateOf(false) }
    var inlineEquationFor by remember { mutableStateOf<MenuState?>(null) }
    var findOpen by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findIndex by remember { mutableIntStateOf(0) }
    var highlightKey by remember { mutableStateOf<String?>(null) }
    var outlineSheet by remember { mutableStateOf(false) }
    var insertSheet by remember { mutableStateOf(false) }
    var editingProperty by remember { mutableStateOf<PropertySchema?>(null) }
    var templatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is PageEvent.OpenDatabase -> navigator.replace(DatabaseRoute(e.id))
                PageEvent.Closed -> {
                    messenger.show("Moved to trash", "Undo") { scope.launch { runCatching { session.pages.restore(route.id) } } }
                    navigator.back()
                }
                is PageEvent.Message -> messenger.show(e.text)
                is PageEvent.Opened -> Unit
            }
        }
    }

    val page = state.page
    val editable = page != null && !page.isLocked && !page.inTrash
    val headerItems = 2
    fun scrollToKey(key: String) {
        val index = state.rows.indexOfFirst { it.key == key }
        if (index >= 0) scope.launch {
            listState.animateScrollToItem(index + headerItems, -200)
            highlightKey = key
            delay(1400)
            if (highlightKey == key) highlightKey = null
        }
    }
    LaunchedEffect(route.blockId, state.rows.isNotEmpty()) {
        val targetBlockId = route.blockId ?: return@LaunchedEffect
        if (state.rows.isNotEmpty()) {
            val match = state.rows.firstOrNull { NotionIds.same(it.block.id, targetBlockId) || NotionIds.same(vm.realIdOf(it.key), targetBlockId) }
            if (match != null) scrollToKey(match.key)
        }
    }

    val callbacksFor: (String) -> EditorCallbacks = remember(vm) {
        { key ->
            object : EditorCallbacks {
                override fun onSplit(right: List<RichText>) {
                    vm.split(key, right)
                    vm.state.value.editingKey?.let(::scrollToKey)
                }
                override fun onBackspaceAtStart() = vm.backspaceAtStart(key)
                override fun onShortcut(type: BlockType) = vm.turnInto(key, type)
                override fun onFocusPrevious() = vm.focusAdjacent(key, -1)
                override fun onFocusNext() = vm.focusAdjacent(key, 1)
                override fun onIndent() = vm.indent(key)
                override fun onOutdent() = vm.outdent(key)
                override fun onMenu(kind: MenuKind?, start: Int, query: String) {
                    menu = kind?.let { MenuState(it, key, start, query) }
                }
                override fun onChanged() = vm.onTextChanged()
                override fun onDone() {
                    menu = null
                    vm.stopEditing()
                }
                override fun onLinkRequest() {
                    linkDialog = true
                }
                override fun onFocusLost() = Unit
            }
        }
    }

    val blockScope = remember(vm, editable, wide) {
        BlockScope(
            vm = vm,
            editable = editable,
            wide = wide,
            callbacksFor = callbacksFor,
            onOpenPage = { navigator.navigate(PageRoute(it)) },
            onOpenDatabase = { navigator.navigate(DatabaseRoute(it)) },
            onOpenMedia = { url, kind, name -> navigator.navigate(MediaRoute(url, kind, name)) },
            onOpenUrl = { links.openUrl(it) },
            onBlockMenu = { blockMenuKey = it },
            onScrollTo = { scrollToKey(it) },
            onComment = { id -> comments = CommentsTarget(id, true, "Block") },
            pageIcon = { id -> session.cache.page(id)?.icon ?: session.cache.database(id)?.icon },
            pageTitle = { id -> session.cache.displayTitle(id) },
        )
    }

    fun applySlash(item: SlashItem) {
        val m = menu ?: return
        val edit = vm.editState ?: return
        edit.deleteRange(m.start, edit.selection.start)
        vm.onTextChanged()
        menu = null
        val key = m.key
        val empty = edit.content.isEmpty()
        val parent = vm.parentKeyOf(key)
        fun place(block: NBlock) {
            if (empty) {
                vm.insertBlock(block, afterKey = key, parentKey = parent, edit = block.type.isText)
                vm.deleteBlock(key)
            } else vm.insertBlock(block, afterKey = key, parentKey = parent, edit = block.type.isText)
        }
        when {
            item.id.startsWith("toggle_heading_") -> {
                val type = BlockType.of("heading_" + item.id.last())
                if (empty) vm.turnInto(key, type) { it.copy(toggleable = true) } else place(NBlock("", type, toggleable = true))
            }
            item.id in listOf("paragraph", "heading_1", "heading_2", "heading_3", "heading_4", "to_do", "bulleted_list_item", "numbered_list_item", "toggle", "quote", "callout", "code") -> {
                val type = BlockType.of(item.id)
                if (empty) vm.turnInto(key, type) else place(NBlock("", type, icon = if (type == BlockType.CALLOUT) NIcon.Emoji("💡") else null))
            }
            item.id == "divider" -> place(NBlock("", BlockType.DIVIDER))
            item.id == "table_of_contents" -> place(NBlock("", BlockType.TABLE_OF_CONTENTS))
            item.id == "breadcrumb" -> place(NBlock("", BlockType.BREADCRUMB))
            item.id == "equation" -> place(NBlock("", BlockType.EQUATION, expression = "e^{i\\pi} + 1 = 0"))
            item.id == "table" -> place(
                NBlock("", BlockType.TABLE, tableWidth = 3, hasColumnHeader = true,
                    children = List(3) { NBlock("", BlockType.TABLE_ROW, cells = List(3) { emptyList() }) }),
            )
            item.id.startsWith("columns_") -> {
                val n = item.id.last().digitToInt()
                place(NBlock("", BlockType.COLUMN_LIST, children = List(n) { NBlock("", BlockType.COLUMN, children = listOf(NBlock("", BlockType.PARAGRAPH))) }))
            }
            item.id == "tabs" -> place(
                NBlock("", BlockType.TAB, children = listOf("Tab 1", "Tab 2").map {
                    NBlock("", BlockType.PARAGRAPH, rich = it.toRich(), children = listOf(NBlock("", BlockType.PARAGRAPH)))
                }),
            )
            item.id == "page" -> vm.createSubpage("") { id -> navigator.navigate(PageRoute(id)) }
            item.id == "link_to_page" -> linkPageFor = key
            item.id == "database" -> newDatabase = true
            item.id in listOf("image", "video", "audio", "file", "pdf") -> media = MediaRequest(BlockType.of(item.id), key, empty)
            item.id == "bookmark" || item.id == "embed" -> urlFor = BlockType.of(item.id) to key
            item.id == "mention_person" || item.id == "mention_page" -> {
                val at = edit.selection.start
                edit.textState.edit { insert(at + 1, "@") }
            }
            item.id == "date" -> {
                val (display, attrs) = mentionFor(MentionChoice.Date("Today", com.manuscript.core.model.NDate(java.time.LocalDate.now().toString())))
                edit.insertAtomic(edit.selection.start, edit.selection.start, display, attrs)
                vm.onTextChanged()
            }
            item.id == "inline_equation" -> inlineEquationFor = MenuState(MenuKind.SLASH, key, edit.selection.start, "")
            item.id.startsWith("color:") -> vm.setColor(key, NColor(com.manuscript.core.model.Hue.of(item.id.substringAfter(':'))))
            item.id.startsWith("background:") -> vm.setColor(key, NColor(com.manuscript.core.model.Hue.of(item.id.substringAfter(':')), true))
            item.id == "action:duplicate" -> vm.duplicate(key)
            item.id == "action:delete" -> vm.deleteBlock(key)
            item.id == "action:comment" -> comments = CommentsTarget(vm.realIdOf(key), true, "Block")
        }
        haptics.confirm()
    }

    fun applyMention(choice: MentionChoice) {
        val m = menu ?: return
        val edit = vm.editState ?: return
        val (display, attrs) = mentionFor(choice)
        edit.insertAtomic(m.start, edit.selection.start, display, attrs)
        vm.onTextChanged()
        menu = null
        haptics.tick()
    }

    val readingProgress by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total <= 1) 0f else {
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                if (last >= total - 1 && !listState.canScrollForward) 1f else (listState.firstVisibleItemIndex.toFloat() / (total - info.visibleItemsInfo.size).coerceAtLeast(1))
            }
        }
    }
    val pastTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 380 } }
    val toolbarBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(exitDirection = FloatingToolbarExitDirection.Bottom)
    val editing = state.editingKey != null && vm.editState != null
    val hasHeadings = remember(state.blocks) { state.blocks.any { b -> b.walk().any { it.type.isHeading } } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sidePanel = wide && maxWidth >= 1100.dp
        val showOutline = sidePanel && settings.showOutline && comments == null && hasHeadings
        Row(Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.weight(1f).onPreviewKeyEvent { e ->
                    val primary = e.isCtrlPressed || e.isMetaPressed
                    if (e.type != KeyEventType.KeyDown || !primary) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.F -> { findOpen = true; true }
                        Key.D -> { vm.toggleFavorite(); true }
                        Key.S -> { vm.stopEditing(); messenger.show("Saved"); true }
                        else -> false
                    }
                },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    Column {
                        TopAppBar(
                            title = {
                                AnimatedVisibility(pastTitle && page != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        NotionIcon(page?.icon, 22.dp, showFallback = false)
                                        if (page?.icon != null) Spacer(Modifier.width(8.dp))
                                        Text(page?.title?.ifBlank { "Untitled" } ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            },
                            navigationIcon = {
                                if (navigator.backStack.size > 1) IconButton(onClick = { vm.stopEditing(); navigator.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                            },
                            actions = {
                                SaveIndicator(state.save, state.offline)
                                if (editing) {
                                    TextButton(onClick = { menu = null; vm.stopEditing() }, shapes = ButtonDefaults.shapes()) { Text("Done") }
                                } else {
                                    IconButton(onClick = { haptics.toggle(!favorite); vm.toggleFavorite() }) {
                                        Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline, if (favorite) "Remove from favorites" else "Add to favorites",
                                            tint = if (favorite) com.manuscript.ui.theme.LocalNotionPalette.current.accent(com.manuscript.core.model.Hue.YELLOW) else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { comments = if (comments == null && page != null) CommentsTarget(page.id, false, page.title) else null }) { Icon(Icons.Rounded.Forum, "Comments") }
                                    IconButton(onClick = { pageMenu = true }) { Icon(Icons.Rounded.MoreVert, "Page options") }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surface),
                        )
                        AnimatedVisibility(readingProgress in 0.01f..0.995f) {
                            LinearWavyProgressIndicator(progress = { readingProgress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                        }
                        AnimatedVisibility(findOpen) {
                            val matches = remember(findQuery, state.rows) { vm.find(findQuery) }
                            LaunchedEffect(findQuery) { findIndex = 0; matches.firstOrNull()?.let { scrollToKey(it) } }
                            FindBar(
                                query = findQuery,
                                onQuery = { findQuery = it },
                                count = matches.size,
                                index = findIndex,
                                onNext = { if (matches.isNotEmpty()) { findIndex = (findIndex + 1) % matches.size; scrollToKey(matches[findIndex]) } },
                                onPrevious = { if (matches.isNotEmpty()) { findIndex = (findIndex - 1 + matches.size) % matches.size; scrollToKey(matches[findIndex]) } },
                                onClose = { findOpen = false; findQuery = "" },
                            )
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
                    when {
                        page == null && state.error != null -> ErrorState(state.error!!, onRetry = { vm.load() })
                        page == null -> Column(Modifier.readingColumn(settings.readingWidth.maxDp.dp, 24.dp).padding(top = 48.dp)) {
                            SkeletonLine(0.6f, 36.dp)
                            Spacer(Modifier.height(32.dp))
                            SkeletonBlock(lines = 8)
                        }
                        else -> {
                            val maxWidth = settings.readingWidth.maxDp.dp
                            val horizontal = if (wide) 48.dp else 22.dp
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize().nestedScroll(toolbarBehavior),
                                contentPadding = PaddingValues(bottom = 200.dp),
                            ) {
                                item(key = "header") {
                                    PageHeader(
                                        page = page,
                                        editable = editable,
                                        coverOffset = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else 0 },
                                        maxWidth = maxWidth,
                                        horizontal = horizontal,
                                        onIcon = { iconPicker = true },
                                        onCover = { coverPicker = true },
                                        onComment = { comments = CommentsTarget(page.id, false, page.title) },
                                        onRename = vm::rename,
                                        onEnter = {
                                            val newKey = vm.insertBlock(NBlock("", BlockType.PARAGRAPH), afterKey = null, parentKey = null, atStart = true)
                                            scrollToKey(newKey)
                                        },
                                        lastEdited = page.lastEditedTime,
                                        editor = users[page.lastEditedBy],
                                        showCovers = settings.showCovers,
                                    )
                                }
                                item(key = "properties") {
                                    val ds = state.dataSource
                                    if (ds != null) {
                                        PropertiesPanel(
                                            page = page,
                                            schema = ds.properties.filter { it.type != PropType.TITLE },
                                            editable = editable,
                                            modifier = Modifier.readingColumn(maxWidth, horizontal - 6.dp),
                                        ) { editingProperty = it }
                                    }
                                }
                                if (state.rows.isEmpty() && !state.loading && !state.nestedLoading) {
                                    item(key = "empty") {
                                        Text(
                                            if (editable) "Tap here to start writing, or press + to add a block." else "This page is empty.",
                                            style = reading.body,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.readingColumn(maxWidth, horizontal).clip(RoundedCornerShape(12.dp))
                                                .clickable(enabled = editable) { vm.appendParagraph() }.padding(vertical = 8.dp),
                                        )
                                    }
                                } else if (state.rows.isEmpty()) {
                                    item(key = "loading") { SkeletonBlock(Modifier.readingColumn(maxWidth, horizontal).padding(top = 8.dp), lines = 6) }
                                }
                                items(state.rows, key = { it.key }) { row ->
                                    val flash by animateColorAsState(
                                        if (highlightKey == row.key) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent,
                                        tween(400), label = "flash",
                                    )
                                    Box(Modifier.readingColumn(maxWidth, horizontal - 6.dp).clip(RoundedCornerShape(10.dp)).background(flash).padding(horizontal = 6.dp).animateItem()) {
                                        BlockRow(row, blockScope, state.editingKey)
                                    }
                                }
                                if (state.nestedLoading && state.rows.isNotEmpty()) {
                                    item(key = "nested") { LinearWavyProgressIndicator(Modifier.readingColumn(maxWidth, horizontal).padding(vertical = 12.dp)) }
                                }
                                if (editable && state.rows.isNotEmpty()) {
                                    item(key = "tail") {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(220.dp)
                                                .clickable(interactionSource = null, indication = null) {
                                                    val newKey = vm.appendParagraph()
                                                    scrollToKey(newKey)
                                                },
                                        ) {
                                            val lastBlock = state.blocks.lastOrNull()
                                            val showHint = state.editingKey == null && (lastBlock == null || !lastBlock.type.isText || lastBlock.rich.isNotEmpty())
                                            if (showHint) {
                                                Text(
                                                    "Tap here to continue writing…",
                                                    style = reading.body,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                                    modifier = Modifier.readingColumn(maxWidth, horizontal).padding(top = 12.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom overlays: menus + toolbars
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp, start = 12.dp, end = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val m = menu
                        AnimatedVisibility(m != null && editing, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut()) {
                            if (m != null) {
                                if (m.kind == MenuKind.SLASH) SlashMenu(m.query, ::applySlash, onDismiss = { menu = null })
                                else MentionMenu(m.query, ::applyMention)
                            }
                        }
                        val edit = vm.editState
                        if (editing && edit != null) {
                            val key = state.editingKey!!
                            FormattingToolbar(
                                edit = edit,
                                onTurnInto = { turnIntoFor = key },
                                onColor = { textColorSheet = true },
                                onLink = { linkDialog = true },
                                onMention = {
                                    val at = edit.selection.start
                                    edit.textState.edit { insert(at + 1, if (at == 0 || edit.content.getOrNull(at - 1)?.isWhitespace() == true) "@" else " @") }
                                },
                                onIndent = { vm.indent(key) },
                                onOutdent = { vm.outdent(key) },
                                onNewLineBelow = {
                                    val parent = vm.parentKeyOf(key)
                                    val newKey = vm.insertBlock(NBlock("", BlockType.PARAGRAPH), afterKey = key, parentKey = parent)
                                    scrollToKey(newKey)
                                },
                                onComment = { comments = CommentsTarget(vm.realIdOf(key), true, "Block") },
                                onDone = { menu = null; vm.stopEditing() },
                                onChanged = { vm.onTextChanged() },
                            )
                        }
                    }
                    if (page != null && !findOpen && !(editing && vm.editState != null)) {
                        // Insets go on the toolbar itself so hiding on scroll slides it past the system navigation bar.
                        HorizontalFloatingToolbar(
                            expanded = true,
                            scrollBehavior = toolbarBehavior,
                            floatingActionButton = {
                                FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = { haptics.click(); if (editable) insertSheet = true else messenger.show(if (page.isLocked) "This page is locked" else "You can't edit this page") }) {
                                    Icon(Icons.Rounded.Add, "Add a block")
                                }
                            },
                            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
                            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
                        ) {
                            IconButton(
                                onClick = {
                                    if (sidePanel && hasHeadings) {
                                        comments = null
                                        scope.launch { container.settingsStore.update { it.copy(showOutline = !it.showOutline) } }
                                    } else {
                                        outlineSheet = true
                                    }
                                },
                            ) { Icon(Icons.AutoMirrored.Rounded.Toc, "Outline") }
                            IconButton(onClick = { findOpen = true }) { Icon(Icons.Rounded.Search, "Find in page") }
                            IconButton(onClick = { comments = CommentsTarget(page.id, false, page.title) }) { Icon(Icons.Rounded.AddComment, "Comment") }
                            IconButton(onClick = { context.shareText(page.url ?: NotionIds.webUrl(route.id), page.title) }) { Icon(Icons.Rounded.Share, "Share") }
                        }
                    }
                }
            }
            if (showOutline) {
                OutlinePanel(state.blocks, vm, onJump = { scrollToKey(it) }, modifier = Modifier.width(260.dp).fillMaxHeight())
            }
            val target = comments
            if (target != null && sidePanel) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(380.dp).fillMaxHeight()) {
                    CommentsPanel(target.id, target.isBlock, target.title, onClose = { comments = null })
                }
            }
        }
        val target = comments
        if (target != null && !sidePanel) {
            ModalBottomSheet(onDismissRequest = { comments = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                CommentsPanel(target.id, target.isBlock, target.title, onClose = { comments = null }, modifier = Modifier.height(620.dp))
            }
        }
    }

    // Sheets and dialogs

    if (iconPicker) IconPickerSheet(onDismiss = { iconPicker = false }, onPick = vm::setIcon, onUpload = vm::uploadIcon)
    calloutIconFor?.let { key -> IconPickerSheet(onDismiss = { calloutIconFor = null }, onPick = { vm.setCalloutIcon(key, it ?: NIcon.Emoji("💡")) }, onUpload = null, title = "Callout icon") }
    if (coverPicker) CoverPickerSheet(onDismiss = { coverPicker = false }, onPick = vm::setCover, onUpload = vm::uploadCover)
    if (textColorSheet) vm.editState?.let { edit ->
        ColorSheet(current = edit.currentAttrs().annotations.color, onDismiss = { textColorSheet = false }) { color -> edit.setColor(color); vm.onTextChanged() }
    }
    if (linkDialog) vm.editState?.let { edit ->
        LinkDialog(initial = edit.currentAttrs().link, onDismiss = { linkDialog = false }) { url -> edit.setLink(url); vm.onTextChanged() }
    }
    turnIntoFor?.let { key ->
        val block = vm.blockByKey(key)
        if (block != null) TurnIntoSheet(block.type, onDismiss = { turnIntoFor = null }) { vm.turnInto(key, it) }
    }
    inlineEquationFor?.let { m ->
        TextEditDialog("Inline equation", "", "LaTeX", monospace = true, onDismiss = { inlineEquationFor = null }) { expr ->
            vm.editState?.let { edit ->
                edit.insertAtomic(m.start, m.start, expr, Attrs(equation = expr))
                vm.onTextChanged()
            }
            inlineEquationFor = null
        }
    }
    blockMenuKey?.let { key ->
        val block = vm.blockByKey(key)
        if (block == null) blockMenuKey = null else BlockMenuSheet(
            block = block,
            onDismiss = { blockMenuKey = null },
            onTurnInto = { vm.turnInto(key, it) },
            onColor = { vm.setColor(key, it) },
            onDuplicate = { vm.duplicate(key) },
            onMoveUp = { vm.move(key, true) },
            onMoveDown = { vm.move(key, false) },
            onIndent = { vm.indent(key) },
            onOutdent = { vm.outdent(key) },
            onCopy = { context.copyText(block.plainText); messenger.show("Copied") },
            onCopyLink = {
                context.copyText((page?.url ?: NotionIds.webUrl(route.id)) + "#" + vm.realIdOf(key).replace("-", ""))
                messenger.show("Link copied")
            },
            onComment = { comments = CommentsTarget(vm.realIdOf(key), true, block.type.label) },
            onDelete = { vm.deleteBlock(key) },
            onInsertAbove = {
                val loc = vm.locateByKey(key)
                val prevKey = loc?.siblings?.getOrNull(loc.index - 1)?.id?.let(vm::keyFor)
                val newKey = vm.insertBlock(NBlock("", BlockType.PARAGRAPH), afterKey = prevKey, parentKey = loc?.parentId?.let(vm::keyFor), atStart = prevKey == null)
                scrollToKey(newKey)
            },
            onInsertBelow = {
                val loc = vm.locateByKey(key)
                val newKey = vm.insertBlock(NBlock("", BlockType.PARAGRAPH), afterKey = key, parentKey = loc?.parentId?.let(vm::keyFor))
                scrollToKey(newKey)
            },
            extra = { BlockExtras(block, key, vm, onCalloutIcon = { calloutIconFor = key; blockMenuKey = null }, onUrl = { urlFor = block.type to key; blockMenuKey = null }) },
        )
    }
    editingProperty?.let { schema ->
        PropertyEditor(schema, page?.properties?.get(schema.name), onDismiss = { editingProperty = null }) { value -> vm.updateProperty(schema.name, value) }
    }
    if (moving) PagePickerSheet(
        title = "Move to…",
        onDismiss = { moving = false },
        onPick = { dest -> moving = false; vm.moveTo(dest) },
        excludeIds = setOf(route.id),
    )
    linkPageFor?.let { key ->
        PagePickerSheet(title = "Link to page", onDismiss = { linkPageFor = null }, onPick = { ref ->
            linkPageFor = null
            val block = if (ref.kind == NodeKind.PAGE) NBlock("", BlockType.LINK_TO_PAGE, linkPageId = ref.id)
            else NBlock("", BlockType.LINK_TO_PAGE, linkDatabaseId = ref.databaseId ?: ref.id)
            vm.insertBlock(block, afterKey = key, parentKey = vm.parentKeyOf(key), edit = false)
        })
    }
    if (newDatabase && page != null) NewDatabaseSheet(
        onDismiss = { newDatabase = false },
        onCreated = { newDatabase = false; vm.refresh() },
        initialParent = page.toRef(),
    )
    media?.let { req -> MediaSheet(req, onDismiss = { media = null }, onUpload = { uri ->
        vm.uploadMedia(uri, req.type, afterKey = req.key, replaceKey = if (req.replace) req.key else null)
        media = null
    }, onLink = { url ->
        vm.insertExternal(req.type, url, afterKey = req.key, replaceKey = if (req.replace) req.key else null)
        media = null
    }) }
    urlFor?.let { (type, key) ->
        val existing = key?.let { vm.blockByKey(it) }?.takeIf { it.type == type }
        TextEditDialog(type.label, existing?.url ?: "", "URL", onDismiss = { urlFor = null }) { url ->
            if (existing != null) vm.setUrl(key, url) else vm.insertExternal(type, url.trim(), afterKey = key)
            urlFor = null
        }
    }
    if (insertSheet) InsertBlockSheet(onDismiss = { insertSheet = false }) { item ->
        insertSheet = false
        val anchor = state.editingKey ?: state.rows.lastOrNull { it.depth == 0 }?.key
        val parent = anchor?.let(vm::parentKeyOf)
        when {
            item.id == "paragraph" -> {
                val newKey = if (state.editingKey != null) {
                    vm.insertBlock(NBlock("", BlockType.PARAGRAPH), afterKey = anchor, parentKey = parent)
                } else {
                    vm.appendParagraph(edit = true)
                }
                scrollToKey(newKey)
            }
            item.id.startsWith("toggle_heading_") -> {
                val type = BlockType.of("heading_" + item.id.last())
                val newKey = vm.insertBlock(NBlock("", type, toggleable = true), afterKey = anchor, parentKey = parent)
                scrollToKey(newKey)
            }
            item.id in listOf("heading_1", "heading_2", "heading_3", "heading_4", "to_do", "bulleted_list_item", "numbered_list_item", "toggle", "quote", "callout", "code") -> {
                val type = BlockType.of(item.id)
                val newKey = vm.insertBlock(NBlock("", type, icon = if (type == BlockType.CALLOUT) NIcon.Emoji("💡") else null), afterKey = anchor, parentKey = parent)
                scrollToKey(newKey)
            }
            item.id == "divider" -> vm.insertBlock(NBlock("", BlockType.DIVIDER), afterKey = anchor, parentKey = parent, edit = false)
            item.id == "table_of_contents" -> vm.insertBlock(NBlock("", BlockType.TABLE_OF_CONTENTS), afterKey = anchor, parentKey = parent, edit = false)
            item.id == "breadcrumb" -> vm.insertBlock(NBlock("", BlockType.BREADCRUMB), afterKey = anchor, parentKey = parent, edit = false)
            item.id == "equation" -> vm.insertBlock(NBlock("", BlockType.EQUATION, expression = "e^{i\\pi} + 1 = 0"), afterKey = anchor, parentKey = parent, edit = false)
            item.id == "table" -> vm.insertBlock(
                NBlock("", BlockType.TABLE, tableWidth = 3, hasColumnHeader = true,
                    children = List(3) { NBlock("", BlockType.TABLE_ROW, cells = List(3) { emptyList() }) }),
                afterKey = anchor, parentKey = parent, edit = false,
            )
            item.id.startsWith("columns_") -> {
                val n = item.id.last().digitToInt()
                vm.insertBlock(
                    NBlock("", BlockType.COLUMN_LIST, children = List(n) { NBlock("", BlockType.COLUMN, children = listOf(NBlock("", BlockType.PARAGRAPH))) }),
                    afterKey = anchor, parentKey = parent, edit = false,
                )
            }
            item.id == "tabs" -> vm.insertBlock(
                NBlock("", BlockType.TAB, children = listOf("Tab 1", "Tab 2").map {
                    NBlock("", BlockType.PARAGRAPH, rich = it.toRich(), children = listOf(NBlock("", BlockType.PARAGRAPH)))
                }),
                afterKey = anchor, parentKey = parent, edit = false,
            )
            item.id in listOf("image", "video", "audio", "file", "pdf") -> media = MediaRequest(BlockType.of(item.id), anchor, false)
            item.id == "bookmark" || item.id == "embed" -> urlFor = BlockType.of(item.id) to anchor
            item.id == "page" -> vm.createSubpage("") { id -> navigator.navigate(PageRoute(id)) }
            item.id == "link_to_page" -> linkPageFor = anchor ?: ""
            item.id == "database" -> newDatabase = true
        }
    }
    if (outlineSheet) ModalBottomSheet(onDismissRequest = { outlineSheet = false }) {
        OutlinePanel(state.blocks, vm, onJump = { outlineSheet = false; scrollToKey(it) }, modifier = Modifier.fillMaxWidth().height(480.dp))
    }
    if (templatePicker) TemplatePickerSheet(state.templates, onDismiss = { templatePicker = false }) { template, erase ->
        templatePicker = false
        vm.applyTemplate(template, erase)
    }
    if (pageMenu && page != null) PageMenuSheet(
        page = page,
        favorite = favorite,
        words = remember(state.blocks) { vm.wordCount() },
        createdBy = users[page.createdBy],
        editedBy = users[page.lastEditedBy],
        hasTemplates = state.templates.isNotEmpty(),
        onDismiss = { pageMenu = false },
        onFavorite = vm::toggleFavorite,
        onLock = vm::setLocked,
        onIcon = { iconPicker = true },
        onCover = { coverPicker = true },
        onCopyLink = { context.copyText(page.url ?: NotionIds.webUrl(route.id)); messenger.show("Link copied") },
        onShareMarkdown = { scope.launch { context.shareText(vm.markdown(), page.title) } },
        onExport = {
            scope.launch {
                val md = vm.markdown()
                val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                val file = File(dir, (page.title.ifBlank { "Untitled" }.replace(Regex("[^\\w\\s-]"), "").trim().ifBlank { "page" }) + ".md")
                file.writeText(md)
                context.shareFile(file, "text/markdown", page.title)
            }
        },
        onDuplicate = { scope.launch { vm.duplicate()?.let { navigator.navigate(PageRoute(it.id)); messenger.show("Duplicated") } } },
        onMove = { moving = true },
        onTemplate = { templatePicker = true },
        onExpandAll = { vm.setAllToggles(true) },
        onCollapseAll = { vm.setAllToggles(false) },
        onFind = { findOpen = true },
        onOpenInNotion = { context.openUrl(page.url ?: NotionIds.webUrl(route.id)) },
        onRefresh = { vm.refresh() },
        onTrash = vm::trash,
        onRestore = vm::restore,
        onFont = { font -> scope.launch { container.settingsStore.update { it.copy(readingFont = font) } } },
        onSmallText = { small -> scope.launch { container.settingsStore.update { it.copy(smallText = small) } } },
    )
}

@Composable
private fun SaveIndicator(save: SaveState, offline: Boolean) {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        when {
            save is SaveState.Saving -> LoadingIndicator(Modifier.size(26.dp))
            save is SaveState.Failed || offline -> Icon(Icons.Rounded.CloudOff, if (offline) "Offline" else save.let { (it as? SaveState.Failed)?.message }, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            save is SaveState.Saved -> {
                var visible by remember(save) { mutableStateOf(true) }
                LaunchedEffect(save) { delay(1600); visible = false }
                AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
                    Icon(Icons.Rounded.CheckCircle, "Saved", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun PageHeader(
    page: NPage,
    editable: Boolean,
    coverOffset: () -> Int,
    maxWidth: Dp,
    horizontal: Dp,
    onIcon: () -> Unit,
    onCover: () -> Unit,
    onComment: () -> Unit,
    onRename: (String) -> Unit,
    onEnter: () -> Unit,
    lastEdited: String?,
    editor: com.manuscript.core.model.NUser?,
    showCovers: Boolean,
) {
    val reading = LocalReading.current
    val wide = LocalWideLayout.current
    val cover = page.cover?.takeIf { showCovers && it.url.isNotBlank() }
    Column(Modifier.fillMaxWidth()) {
        if (cover != null) {
            Box(Modifier.fillMaxWidth().height(if (wide) 300.dp else 220.dp).clip(RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp))) {
                PageCover(cover, Modifier.fillMaxSize().graphicsLayer { translationY = coverOffset() * 0.45f }.clickable(enabled = editable, onClick = onCover))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.7f to Color.Transparent, 1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))))
            }
        } else Spacer(Modifier.height(if (page.icon != null) 24.dp else 32.dp))
        Column(Modifier.readingColumn(maxWidth, horizontal)) {
            if (page.icon != null) {
                Box(
                    Modifier
                        .offset(y = if (cover != null) (-44).dp else 0.dp)
                        .size(88.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(enabled = editable, onClick = onIcon),
                    contentAlignment = Alignment.CenterStart,
                ) { NotionIcon(page.icon, 76.dp) }
            }
            val lift = if (cover != null && page.icon != null) (-32).dp else 0.dp
            Column(Modifier.offset(y = lift)) {
                if (editable && (page.icon == null || cover == null)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        if (page.icon == null) AssistChip(onClick = onIcon, label = { Text("Add icon", maxLines = 1) }, leadingIcon = { Icon(Icons.Rounded.EmojiEmotions, null, Modifier.size(AssistChipDefaults.IconSize)) }, shape = MaterialTheme.shapes.large)
                        if (cover == null) AssistChip(onClick = onCover, label = { Text("Add cover", maxLines = 1) }, leadingIcon = { Icon(Icons.Rounded.Image, null, Modifier.size(AssistChipDefaults.IconSize)) }, shape = MaterialTheme.shapes.large)
                        AssistChip(onClick = onComment, label = { Text("Comment", maxLines = 1) }, leadingIcon = { Icon(Icons.Rounded.AddComment, null, Modifier.size(AssistChipDefaults.IconSize)) }, shape = MaterialTheme.shapes.large)
                    }
                }
                var title by remember(page.id) { mutableStateOf(page.title) }
                var focused by remember { mutableStateOf(false) }
                LaunchedEffect(page.title) { if (!focused) title = page.title }
                if (editable) {
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it.replace("\n", ""); onRename(title) },
                        textStyle = reading.pageTitle.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { onEnter() }),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                        decorationBox = { inner ->
                            Box {
                                if (title.isEmpty()) Text("Untitled", style = reading.pageTitle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                inner()
                            }
                        },
                    )
                } else {
                    Text(page.title.ifBlank { "Untitled" }, style = reading.pageTitle)
                }
                Row(Modifier.padding(top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (page.isLocked) {
                        Icon(Icons.Rounded.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text("Locked · ", style = reading.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (page.inTrash) Text("In trash · ", style = reading.small, color = MaterialTheme.colorScheme.error)
                    if (editor != null) {
                        UserAvatar(editor, 18.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        listOfNotNull(editor?.displayName?.let { "Edited by $it" } ?: "Edited", lastEdited?.let { Fmt.relative(it).lowercase() }).joinToString(" "),
                        style = reading.small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PropertiesPanel(page: NPage, schema: List<PropertySchema>, editable: Boolean, modifier: Modifier, onEdit: (PropertySchema) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val visible = if (expanded || schema.size <= 7) schema else schema.take(6)
    Column(modifier.padding(bottom = 12.dp)) {
        visible.forEach { prop ->
            PropertyRow(prop, page.properties[prop.name], onClick = if (editable && prop.type.editable || !prop.type.editable) ({ onEdit(prop) }) else null)
        }
        if (schema.size > 7) {
            TextButton(onClick = { expanded = !expanded }) {
                Icon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.ExpandMore, null)
                Spacer(Modifier.width(6.dp))
                Text(if (expanded) "Show fewer properties" else "${schema.size - 6} more properties")
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun OutlinePanel(blocks: List<NBlock>, vm: PageViewModel, onJump: (String) -> Unit, modifier: Modifier) {
    val headings = remember(blocks) { blocks.flatMap { it.walk().toList() }.filter { it.type.isHeading } }
    Column(modifier.padding(top = 16.dp, end = 12.dp)) {
        Text("On this page", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        if (headings.isEmpty()) {
            Text(
                "Add headings (H1, H2, H3) to see an outline of this page.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        } else LazyColumn {
            items(headings, key = { it.id }) { h ->
                val level = when (h.type) { BlockType.HEADING_1 -> 0; BlockType.HEADING_2 -> 1; BlockType.HEADING_3 -> 2; else -> 3 }
                Text(
                    h.plainText.ifBlank { "Untitled" },
                    style = if (level == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                    color = if (level == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onJump(vm.keyFor(h.id)) }
                        .padding(start = 16.dp + (level * 14).dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun FindBar(query: String, onQuery: (String) -> Unit, count: Int, index: Int, onNext: () -> Unit, onPrevious: () -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text("Find in page") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onNext() }),
            modifier = Modifier.weight(1f),
        )
        Text(if (query.isBlank()) "" else if (count == 0) "0/0" else "${index + 1}/$count", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp))
        IconButton(onClick = onPrevious, enabled = count > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Previous") }
        IconButton(onClick = onNext, enabled = count > 0) { Icon(Icons.Rounded.KeyboardArrowDown, "Next") }
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
    }
}

@Composable
private fun TurnIntoSheet(current: BlockType, onDismiss: () -> Unit, onPick: (BlockType) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Turn into", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(horizontal = 24.dp))
        FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TurnIntoTypes.forEach { (type, icon) ->
                ToggleButton(
                    checked = type == current,
                    onCheckedChange = { onPick(type); onDismiss() },
                    shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                ) {
                    Icon(icon, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(type.label)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun InsertBlockSheet(onDismiss: () -> Unit, onPick: (SlashItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.imePadding()) {
            Text("Add a block", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(horizontal = 24.dp))
            TextField(
                value = query, onValueChange = { query = it }, placeholder = { Text("Search blocks") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true, shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            val items = remember(query) { filterSlash(query).filter { it.section != "Color" && it.section != "Actions" && it.section != "Inline" } }
            LazyColumn(Modifier.height(480.dp)) {
                items(items, key = { it.id }) { item ->
                    ListItem(
                        headlineContent = { Text(item.label) },
                        supportingContent = { Text(item.description) },
                        leadingContent = {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                                Icon(item.icon, null)
                            }
                        },
                        modifier = Modifier.clickable { onPick(item) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaSheet(request: MediaRequest, onDismiss: () -> Unit, onUpload: (Uri) -> Unit, onLink: (String) -> Unit) {
    var link by remember { mutableStateOf("") }
    val visual = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onUpload) }
    val document = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onUpload) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(request.type.label, style = MaterialTheme.typography.titleLargeEmphasized)
            FilledTonalButton(
                onClick = {
                    when (request.type) {
                        BlockType.IMAGE -> visual.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        BlockType.VIDEO -> visual.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        BlockType.AUDIO -> document.launch(arrayOf("audio/*"))
                        BlockType.PDF -> document.launch(arrayOf("application/pdf"))
                        else -> document.launch(arrayOf("*/*"))
                    }
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Rounded.Upload, null)
                Spacer(Modifier.width(8.dp))
                Text("Upload from device")
            }
            Text("or embed a link", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(link, { link = it }, placeholder = { Text("https://") }, singleLine = true, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onLink(link.trim()) }, enabled = link.startsWith("http"), shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Embed link") }
        }
    }
}

@Composable
private fun TemplatePickerSheet(templates: List<com.manuscript.core.model.NTemplate>, onDismiss: () -> Unit, onPick: (com.manuscript.core.model.NTemplate?, Boolean) -> Unit) {
    var erase by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Apply a template", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(horizontal = 24.dp))
        SwitchRow("Replace existing content", "Erase the page before applying", erase) { erase = it }
        templates.forEach { t ->
            ListItem(
                headlineContent = { Text(t.name) },
                supportingContent = if (t.isDefault) ({ Text("Default template") }) else null,
                leadingContent = { Icon(Icons.Rounded.Description, null) },
                modifier = Modifier.clickable { onPick(t, erase) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BlockExtras(block: NBlock, key: String, vm: PageViewModel, onCalloutIcon: () -> Unit, onUrl: () -> Unit) {
    when {
        block.type == BlockType.CALLOUT -> ListItem(
            headlineContent = { Text("Change icon") },
            leadingContent = { NotionIcon(block.icon, 24.dp) },
            modifier = Modifier.clickable(onClick = onCalloutIcon),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        block.type.isHeading -> SwitchRow("Toggle heading", "Collapse the content under this heading", block.toggleable) { vm.setToggleable(key, it) }
        block.type == BlockType.TABLE -> Column {
            SwitchRow("Header row", null, block.hasColumnHeader) { vm.setTableHeaders(key, it, block.hasRowHeader) }
            SwitchRow("Header column", null, block.hasRowHeader) { vm.setTableHeaders(key, block.hasColumnHeader, it) }
        }
        block.type == BlockType.BOOKMARK || block.type == BlockType.EMBED -> ListItem(
            headlineContent = { Text("Edit link") },
            leadingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
            modifier = Modifier.clickable(onClick = onUrl),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        else -> Unit
    }
}

@Composable
private fun PageMenuSheet(
    page: NPage,
    favorite: Boolean,
    words: Int,
    createdBy: com.manuscript.core.model.NUser?,
    editedBy: com.manuscript.core.model.NUser?,
    hasTemplates: Boolean,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onLock: (Boolean) -> Unit,
    onIcon: () -> Unit,
    onCover: () -> Unit,
    onCopyLink: () -> Unit,
    onShareMarkdown: () -> Unit,
    onExport: () -> Unit,
    onDuplicate: () -> Unit,
    onMove: () -> Unit,
    onTemplate: () -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    onFind: () -> Unit,
    onOpenInNotion: () -> Unit,
    onRefresh: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onFont: (ReadingFont) -> Unit,
    onSmallText: (Boolean) -> Unit,
) {
    val settings = LocalAppSettings.current
    val haptics = LocalHaptics.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    NotionIcon(page.icon, 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(page.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    ReadingFont.entries.forEachIndexed { i, font ->
                        ToggleButton(
                            checked = settings.readingFont == font,
                            onCheckedChange = { haptics.segment(); onFont(font) },
                            shapes = when (i) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                ReadingFont.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                "Ag",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = when (font) {
                                        ReadingFont.SANS -> MaterialTheme.typography.titleMedium.fontFamily
                                        ReadingFont.SERIF -> com.manuscript.ui.theme.NewsreaderFamily
                                        ReadingFont.MONO -> com.manuscript.ui.theme.GoogleSansCodeFamily
                                    },
                                ),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(font.label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            item { SwitchRow("Favorite", null, favorite) { onFavorite() } }
            item { SwitchRow("Small text", null, settings.smallText) { onSmallText(it) } }
            item { SwitchRow("Lock page", "Prevent edits to content and properties", page.isLocked) { onLock(it) } }
            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            val actions = listOfNotNull(
                Triple("Change icon", Icons.Rounded.EmojiEmotions, onIcon),
                Triple(if (page.cover == null) "Add cover" else "Change cover", Icons.Rounded.AddPhotoAlternate, onCover),
                Triple("Copy link", Icons.Rounded.ContentCopy, onCopyLink),
                Triple("Share as text", Icons.Rounded.Share, onShareMarkdown),
                Triple("Export Markdown", Icons.Rounded.Description, onExport),
                Triple("Duplicate", Icons.Rounded.ViewAgenda, onDuplicate),
                Triple("Move to…", Icons.AutoMirrored.Rounded.DriveFileMove, onMove),
                if (hasTemplates) Triple("Apply template", Icons.Rounded.Description, onTemplate) else null,
                Triple("Expand all toggles", Icons.Rounded.UnfoldMore, onExpandAll),
                Triple("Collapse all toggles", Icons.Rounded.UnfoldLess, onCollapseAll),
                Triple("Find in page", Icons.Rounded.Search, onFind),
                Triple("Open in Notion", Icons.AutoMirrored.Rounded.OpenInNew, onOpenInNotion),
                Triple("Refresh", Icons.Rounded.Refresh, onRefresh),
            )
            items(actions, key = { it.first }) { (label, icon, action) ->
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = { Icon(icon, null) },
                    modifier = Modifier.clickable { onDismiss(); action() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            item {
                if (page.inTrash) ListItem(
                    headlineContent = { Text("Restore from trash") },
                    leadingContent = { Icon(Icons.Rounded.RestoreFromTrash, null) },
                    modifier = Modifier.clickable { onDismiss(); onRestore() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                ) else ListItem(
                    headlineContent = { Text("Move to trash", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { onDismiss(); haptics.reject(); onTrash() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            item {
                Text(
                    listOfNotNull(
                        "$words words · ${maxOf(1, words / 230)} min read",
                        page.createdTime?.let { "Created ${Fmt.dateTime(it)}" + (createdBy?.let { u -> " by ${u.displayName}" } ?: "") },
                        page.lastEditedTime?.let { "Edited ${Fmt.dateTime(it)}" + (editedBy?.let { u -> " by ${u.displayName}" } ?: "") },
                    ).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }
}
