package com.manuscript.ui.screens.page

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manuscript.core.data.ChangeEvent
import com.manuscript.core.data.CreateParent
import com.manuscript.core.data.Session
import com.manuscript.core.model.BlockType
import com.manuscript.core.model.NBlock
import com.manuscript.core.model.NColor
import com.manuscript.core.model.NDataSource
import com.manuscript.core.model.NFile
import com.manuscript.core.model.NIcon
import com.manuscript.core.model.NPage
import com.manuscript.core.model.NTemplate
import com.manuscript.core.model.NodeRef
import com.manuscript.core.model.Parent
import com.manuscript.core.model.PropValue
import com.manuscript.core.model.RichText
import com.manuscript.core.model.plainText
import com.manuscript.core.model.toRef
import com.manuscript.core.model.toRich
import com.manuscript.core.net.NotionException
import com.manuscript.core.net.isOffline
import com.manuscript.core.util.MarkdownExport
import com.manuscript.ui.screens.page.editor.RichEditState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

@Immutable
data class FlatRow(
    val key: String,
    val block: NBlock,
    val depth: Int,
    val number: Int,
    val parentId: String?,
    val inSynced: Boolean,
)

sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data object Saved : SaveState
    data class Failed(val message: String) : SaveState
}

data class PageUi(
    val page: NPage? = null,
    val blocks: List<NBlock> = emptyList(),
    val rows: List<FlatRow> = emptyList(),
    val loading: Boolean = true,
    val nestedLoading: Boolean = false,
    val error: Throwable? = null,
    val offline: Boolean = false,
    val dataSource: NDataSource? = null,
    val save: SaveState = SaveState.Idle,
    val editingKey: String? = null,
    val collapsed: Set<String> = emptySet(),
    val openToggles: Set<String> = emptySet(),
    val templates: List<NTemplate> = emptyList(),
)

data class FocusTarget(val key: String, val cursor: Int, val stamp: Long = System.nanoTime())

sealed interface PageEvent {
    data class OpenDatabase(val id: String) : PageEvent
    data object Closed : PageEvent
    data class Message(val text: String) : PageEvent
    data class Opened(val pageId: String) : PageEvent
}

class PageViewModel(private val session: Session, val pageId: String) : ViewModel() {
    private val _state = MutableStateFlow(PageUi())
    val state: StateFlow<PageUi> = _state.asStateFlow()
    private val _events = MutableSharedFlow<PageEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PageEvent> = _events

    val isFavorite = session.workspace.isFavorite(pageId).stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val users = session.cache.users

    var editState by mutableStateOf<RichEditState?>(null)
        private set
    private val _focus = MutableStateFlow<FocusTarget?>(null)
    val focus: StateFlow<FocusTarget?> = _focus.asStateFlow()

    private val tempToReal = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val realToKey = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val textJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private var titleJob: Job? = null
    private val ops = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val pendingCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val pending: Int get() = pendingCount.get()
    private var scrolledInitialBlock = false

    init {
        session.scope.launch { for (op in ops) runOp(op) }
        load()
        viewModelScope.launch {
            session.events.collect { e ->
                if (e is ChangeEvent.ChildrenChanged && e.parentId == pageId && pending == 0 && _state.value.editingKey == null) {
                    refreshBlocks()
                }
            }
        }
    }

    // Loading

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.page == null, error = null) }
            if (!refresh) {
                session.cache.storedPage(pageId)?.let { p -> _state.update { it.copy(page = p) } }
                session.pages.cachedBlocks(pageId)?.let { cached ->
                    if (_state.value.blocks.isEmpty()) setTree(cached, loading = false, offline = false)
                }
            }
            val blocks = launch { refreshBlocks() }
            val page = try {
                session.pages.page(pageId)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                if (e is NotionException.Api && e.code == "validation_error" && e.apiMessage.contains("database", ignoreCase = true)) {
                    blocks.cancel()
                    _events.emit(PageEvent.OpenDatabase(pageId))
                    return@launch
                }
                _state.update { it.copy(loading = false, error = if (it.page == null) e else null, offline = e.isOffline) }
                return@launch
            }
            _state.update { it.copy(page = page, offline = false) }
            session.workspace.recordVisit(page.toRef())
            session.users.ensure(listOf(page.createdBy, page.lastEditedBy))
            _events.emit(PageEvent.Opened(page.id))
            page.dataSourceId?.let { dsId ->
                launch {
                    val ds = session.cache.storedDataSource(dsId) ?: runCatching { session.databases.dataSource(dsId) }.getOrNull()
                    _state.update { it.copy(dataSource = ds) }
                    val templates = session.databases.templates(dsId)
                    _state.update { it.copy(templates = templates) }
                }
            }
        }
    }

    private suspend fun refreshBlocks() {
        if (pending > 0 || _state.value.editingKey != null) return
        _state.update { it.copy(nestedLoading = true) }
        runCatching {
            session.pages.loadBlocks(pageId) { top ->
                if (_state.value.blocks.isEmpty()) setTree(top, loading = false, offline = false)
            }
        }.onSuccess { tree ->
            if (pending == 0 && _state.value.editingKey == null) setTree(tree, loading = false, offline = false)
            val allBlocks = tree.flatMap { it.walk().toList() }
            val people = allBlocks.flatMap { b -> b.rich.mapNotNull { (it.mention as? com.manuscript.core.model.Mention.User)?.id } }
            session.users.ensure(people + allBlocks.mapNotNull { it.lastEditedBy })
        }.onFailure { e ->
            if (e is CancellationException) throw e
            _state.update { it.copy(loading = false, offline = e.isOffline || it.offline, error = if (it.blocks.isEmpty() && it.page == null) e else null) }
        }
        _state.update { it.copy(nestedLoading = false, loading = false) }
    }

    fun refresh() {
        commitEditing()
        _state.update { it.copy(editingKey = null) }
        editState = null
        load(refresh = true)
    }

    // Tree helpers

    private fun keyOf(id: String): String = realToKey[id] ?: id
    fun keyFor(id: String): String = keyOf(id)
    private fun idOf(key: String): String = tempToReal[key] ?: key
    private fun newTempId() = "tmp-" + UUID.randomUUID().toString()

    private fun setTree(blocks: List<NBlock>, loading: Boolean = _state.value.loading, offline: Boolean = _state.value.offline) {
        _state.update { it.copy(blocks = blocks, rows = flatten(blocks, it.openToggles), loading = loading, offline = offline) }
    }

    private fun mutateTree(transform: (List<NBlock>) -> List<NBlock>) {
        _state.update { s ->
            val next = transform(s.blocks)
            s.copy(blocks = next, rows = flatten(next, s.openToggles))
        }
    }

    private fun flatten(blocks: List<NBlock>, open: Set<String>): List<FlatRow> {
        val out = ArrayList<FlatRow>(blocks.size * 2)
        fun visit(list: List<NBlock>, depth: Int, parent: String?, synced: Boolean) {
            var number = 0
            list.forEach { b ->
                number = if (b.type == BlockType.NUMBERED) number + 1 else 0
                val start = if (b.type == BlockType.NUMBERED && number == 1) (b.listStart ?: 1) else null
                if (start != null) number = start
                val key = keyOf(b.id)
                out += FlatRow(key, b, depth, number, parent, synced)
                val nested = when (b.type) {
                    BlockType.TOGGLE -> key in open
                    BlockType.HEADING_1, BlockType.HEADING_2, BlockType.HEADING_3, BlockType.HEADING_4 -> b.toggleable && key in open
                    BlockType.PARAGRAPH, BlockType.BULLETED, BlockType.NUMBERED, BlockType.TO_DO -> true
                    BlockType.SYNCED_BLOCK -> true
                    BlockType.TEMPLATE -> true
                    else -> false
                }
                if (nested && b.children.isNotEmpty() && depth < 10) {
                    val childDepth = if (b.type == BlockType.SYNCED_BLOCK) depth else depth + 1
                    visit(b.children, childDepth, b.id, synced || b.type == BlockType.SYNCED_BLOCK)
                }
            }
        }
        visit(blocks, 0, null, false)
        return out
    }

    data class Located(val block: NBlock, val parentId: String?, val index: Int, val siblings: List<NBlock>)

    private fun locate(id: String, list: List<NBlock> = _state.value.blocks, parent: String? = null): Located? {
        list.forEachIndexed { i, b ->
            if (b.id == id) return Located(b, parent, i, list)
            locate(id, b.children, b.id)?.let { return it }
        }
        return null
    }

    fun blockByKey(key: String): NBlock? = locate(idOf(key))?.block

    fun locateByKey(key: String): Located? = locate(idOf(key))

    fun parentKeyOf(key: String): String? = blockByKey(key)?.let { locate(it.id)?.parentId }?.let(::keyOf)

    fun realIdOf(key: String): String = idOf(key)

    private fun updateBlock(id: String, list: List<NBlock>, transform: (NBlock) -> NBlock): List<NBlock> = list.map { b ->
        when {
            b.id == id -> transform(b)
            b.children.isNotEmpty() -> b.copy(children = updateBlock(id, b.children, transform))
            else -> b
        }
    }

    private fun removeBlock(id: String, list: List<NBlock>): List<NBlock> =
        list.filter { it.id != id }.map { if (it.children.isNotEmpty()) it.copy(children = removeBlock(id, it.children)) else it }

    private fun insertBlock(parentId: String?, afterId: String?, block: NBlock, list: List<NBlock>, atStart: Boolean = false): List<NBlock> {
        if (parentId == null) return insertInto(list, afterId, block, atStart)
        return list.map { b ->
            when {
                b.id == parentId -> b.copy(children = insertInto(b.children, afterId, block, atStart), hasChildren = true)
                b.children.isNotEmpty() -> b.copy(children = insertBlock(parentId, afterId, block, b.children, atStart))
                else -> b
            }
        }
    }

    private fun insertInto(list: List<NBlock>, afterId: String?, block: NBlock, atStart: Boolean): List<NBlock> {
        val out = list.toMutableList()
        val index = when {
            afterId != null -> out.indexOfFirst { it.id == afterId }.let { if (it < 0) out.size else it + 1 }
            atStart -> 0
            else -> out.size
        }
        out.add(index, block)
        return out
    }

    // Sync queue

    private fun enqueue(op: suspend () -> Unit) {
        pendingCount.incrementAndGet()
        _state.update { it.copy(save = SaveState.Saving) }
        ops.trySend {
            try {
                op()
                _state.update { if (pendingCount.get() <= 1) it.copy(save = SaveState.Saved, offline = false) else it }
            } finally {
                val left = pendingCount.decrementAndGet()
                if (left == 0) _state.update { if (it.save is SaveState.Failed) it else it.copy(save = SaveState.Saved) }
            }
        }
    }

    private suspend fun runOp(op: suspend () -> Unit) {
        var attempt = 0
        while (true) {
            try {
                op()
                return
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                if (e.isOffline && attempt < 5) {
                    _state.update { it.copy(save = SaveState.Failed("Offline — retrying…")) }
                    delay(2000L * (attempt + 1))
                    attempt++
                    continue
                }
                _state.update { it.copy(save = SaveState.Failed(e.message ?: "Couldn't save")) }
                _events.tryEmit(PageEvent.Message("Couldn't save a change: ${e.message ?: "unknown error"}"))
                return
            }
        }
    }

    // Editing

    fun startEditing(key: String, cursor: Int = -1) {
        val p = _state.value.page
        if (p?.isLocked == true || p?.inTrash == true) return
        if (_state.value.editingKey == key) {
            _focus.value = FocusTarget(key, cursor.takeIf { it >= 0 } ?: editState?.selection?.start ?: 0)
            return
        }
        commitEditing()
        val block = blockByKey(key) ?: return
        val rich = if (block.type == BlockType.TABLE_ROW) emptyList() else block.rich
        editState = RichEditState(rich, cursor)
        _state.update { it.copy(editingKey = key) }
        _focus.value = FocusTarget(key, if (cursor < 0) rich.plainText().length else cursor)
    }

    fun stopEditing() {
        commitEditing()
        editState = null
        _state.update { it.copy(editingKey = null) }
    }

    fun consumeFocus() {
        _focus.value = null
    }

    /** Moves editing to the previous (-1) or next (+1) text block. */
    fun focusAdjacent(key: String, direction: Int) {
        val rows = _state.value.rows
        val index = rows.indexOfFirst { it.key == key }
        if (index < 0) return
        val range = if (direction < 0) (index - 1 downTo 0) else (index + 1 until rows.size)
        val target = range.map { rows[it] }.firstOrNull { it.block.type.isText } ?: return
        startEditing(target.key, if (direction < 0) target.block.plainText.length else 0)
    }

    fun wordCount(): Int = _state.value.blocks.flatMap { it.walk().toList() }
        .sumOf { b -> (b.plainText + " " + b.cells.flatten().plainText()).split(Regex("\\s+")).count { it.isNotBlank() } }

    /** Keys of blocks whose text contains [query], in reading order. */
    fun find(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        return _state.value.rows.filter { row ->
            row.block.plainText.contains(query, ignoreCase = true) || row.block.cells.any { it.plainText().contains(query, ignoreCase = true) }
        }.map { it.key }
    }

    private fun commitEditing() {
        val key = _state.value.editingKey ?: return
        val edit = editState ?: return
        val rich = edit.toRichText()
        val block = blockByKey(key) ?: return
        if (block.rich != rich) {
            mutateTree { updateBlock(block.id, it) { b -> b.copy(rich = rich) } }
            scheduleSave(key, immediate = true)
        }
    }

    fun onTextChanged() {
        val key = _state.value.editingKey ?: return
        val edit = editState ?: return
        val block = blockByKey(key) ?: return
        val rich = edit.toRichText()
        if (block.rich == rich) return
        mutateTree { updateBlock(block.id, it) { b -> b.copy(rich = rich) } }
        scheduleSave(key)
    }

    private fun scheduleSave(key: String, immediate: Boolean = false) {
        textJobs[key]?.cancel()
        textJobs[key] = viewModelScope.launch {
            if (!immediate) delay(700)
            textJobs.remove(key)
            enqueue {
                val current = blockByKey(key) ?: return@enqueue
                val id = idOf(key)
                if (id.startsWith("tmp-")) return@enqueue
                session.pages.updateBlock(current.copy(id = id))
            }
        }
    }

    /** Creates [block] after [afterKey] (or at the start/end of [parentKey]) locally and in Notion. */
    fun insertBlock(block: NBlock, afterKey: String?, parentKey: String?, edit: Boolean = true, atStart: Boolean = false): String {
        val tempId = newTempId()
        val local = block.copy(id = tempId, pending = true)
        val parentId = parentKey?.let(::idOf)
        val afterId = afterKey?.let(::idOf)
        mutateTree { insertBlock(parentId, afterId, local, it, atStart) }
        if (edit && block.type.isText) {
            commitEditing()
            editState = RichEditState(block.rich, 0)
            _state.update { it.copy(editingKey = tempId) }
            _focus.value = FocusTarget(tempId, 0)
        }
        enqueue {
            val realParent = parentKey?.let(::idOf)?.takeUnless { it.startsWith("tmp-") } ?: pageId
            val realAfter = afterKey?.let(::idOf)?.takeUnless { it.startsWith("tmp-") }
            val latest = blockByKey(tempId) ?: return@enqueue
            val created = session.pages.appendBlocks(realParent, listOf(latest.copy(id = "", pending = false)), realAfter, atStart && realAfter == null).firstOrNull()
                ?: return@enqueue
            tempToReal[tempId] = created.id
            realToKey[created.id] = tempId
            mutateTree { updateBlock(tempId, it) { b -> created.copy(rich = b.rich, children = if (created.children.isNotEmpty()) created.children else b.children) } }
            val current = blockByKey(tempId)
            if (current != null && current.rich != created.rich && current.type.isText) session.pages.updateBlock(current.copy(id = created.id))
        }
        return tempId
    }

    fun split(key: String, right: List<RichText>) {
        val block = blockByKey(key) ?: return
        val located = locate(block.id) ?: return
        val leftEmpty = editState?.content.isNullOrEmpty()
        if (leftEmpty && right.isEmpty() && block.type.isListItem && block.type != BlockType.TOGGLE) {
            turnInto(key, BlockType.PARAGRAPH)
            return
        }
        commitEditing()
        val newType = when (block.type) {
            BlockType.BULLETED, BlockType.NUMBERED, BlockType.TO_DO, BlockType.TOGGLE -> block.type
            else -> BlockType.PARAGRAPH
        }
        if (leftEmpty && right.isNotEmpty() && !block.type.isListItem) {
            val prevKey = located.siblings.getOrNull(located.index - 1)?.id?.let(::keyOf)
            insertBlock(
                NBlock("", BlockType.PARAGRAPH),
                afterKey = prevKey,
                parentKey = located.parentId?.let(::keyOf),
                edit = false,
                atStart = prevKey == null,
            )
            editState?.placeCursor(0)
            return
        }
        insertBlock(NBlock("", newType, rich = right), afterKey = key, parentKey = located.parentId?.let(::keyOf))
        editState?.placeCursor(0)
    }

    fun backspaceAtStart(key: String) {
        val block = blockByKey(key) ?: return
        if (block.type != BlockType.PARAGRAPH && block.type.isConvertibleText) {
            turnInto(key, BlockType.PARAGRAPH)
            return
        }
        val rows = _state.value.rows
        val index = rows.indexOfFirst { it.key == key }
        val row = rows.getOrNull(index) ?: return
        if (row.depth > 0 && !row.inSynced && locate(block.id)?.parentId != null) {
            val parent = locate(block.id)?.parentId?.let { locate(it)?.block }
            if (parent != null && parent.type != BlockType.COLUMN && parent.type != BlockType.SYNCED_BLOCK) {
                outdent(key)
                return
            }
        }
        val previous = rows.subList(0, index).lastOrNull { it.block.type.isText && it.block.type != BlockType.CODE }
        val content = editState?.toRichText() ?: block.rich
        if (content.isEmpty()) {
            deleteBlock(key)
            previous?.let { startEditing(it.key, it.block.plainText.length) }
            return
        }
        if (previous == null || block.children.isNotEmpty()) return
        val joinAt = previous.block.plainText.length
        val merged = previous.block.rich + content
        editState = null
        _state.update { it.copy(editingKey = null) }
        mutateTree { tree -> updateBlock(previous.block.id, removeBlock(block.id, tree)) { it.copy(rich = merged) } }
        scheduleSave(previous.key, immediate = true)
        enqueue {
            val id = idOf(key)
            if (!id.startsWith("tmp-")) session.pages.deleteBlock(id)
        }
        startEditing(previous.key, joinAt)
    }

    fun deleteBlock(key: String) {
        val block = blockByKey(key) ?: return
        if (_state.value.editingKey == key) {
            editState = null
            _state.update { it.copy(editingKey = null) }
        }
        mutateTree { removeBlock(block.id, it) }
        textJobs.remove(key)?.cancel()
        enqueue {
            val id = idOf(key)
            if (!id.startsWith("tmp-")) session.pages.deleteBlock(id)
        }
    }

    /** Replaces a block with one of another type. The API can't change a type, so it's recreated in place. */
    fun turnInto(key: String, type: BlockType, configure: (NBlock) -> NBlock = { it }) {
        val editing = _state.value.editingKey == key
        if (editing) commitEditing()
        val block = blockByKey(key) ?: return
        if (block.type == type && configure(block) == block) return
        val located = locate(block.id) ?: return
        val converted = configure(
            block.copy(
                type = type,
                rawType = type.api,
                checked = if (type == BlockType.TO_DO) block.checked else false,
                toggleable = if (type.isHeading) block.toggleable else false,
                language = if (type == BlockType.CODE) block.language ?: "plain text" else null,
                icon = if (type == BlockType.CALLOUT) block.icon ?: NIcon.Emoji("💡") else null,
                color = if (type.supportsColor) block.color else NColor.Default,
                rich = if (type.isText) block.rich else emptyList(),
                children = if (type.isText || type == BlockType.TOGGLE) block.children else emptyList(),
            ),
        )
        mutateTree { updateBlock(block.id, it) { converted } }
        if (editing) {
            if (type.isText) editState = RichEditState(converted.rich, editState?.selection?.start ?: 0)
            else {
                editState = null
                _state.update { it.copy(editingKey = null) }
            }
            if (type.isText) _focus.value = FocusTarget(key, editState?.selection?.start ?: 0)
        }
        if (type == BlockType.TOGGLE || (type.isHeading && converted.toggleable)) _state.update { s ->
            val open = s.openToggles + key
            s.copy(openToggles = open, rows = flatten(s.blocks, open))
        }
        recreate(key, located.parentId?.let(::keyOf), afterKey = located.siblings.getOrNull(located.index - 1)?.id?.let(::keyOf))
    }

    private fun recreate(key: String, parentKey: String?, afterKey: String?, atStart: Boolean = afterKey == null) {
        enqueue {
            val oldId = idOf(key)
            val latest = blockByKey(key) ?: return@enqueue
            val parent = parentKey?.let(::idOf) ?: pageId
            val after = afterKey?.let(::idOf)?.takeUnless { it.startsWith("tmp-") }
            val created = session.pages.appendBlocks(parent, listOf(latest.copy(id = "")), after, atStart = after == null && atStart).firstOrNull()
                ?: return@enqueue
            if (!oldId.startsWith("tmp-") && oldId != created.id) runCatching { session.pages.deleteBlock(oldId) }
            tempToReal[key] = created.id
            realToKey[created.id] = key
            mutateTree { updateBlock(oldId, it) { b -> created.copy(rich = b.rich, children = created.children.ifEmpty { b.children }) } }
        }
    }

    fun indent(key: String) {
        commitEditing()
        val block = blockByKey(key) ?: return
        val located = locate(block.id) ?: return
        val previous = located.siblings.getOrNull(located.index - 1) ?: return
        if (!previous.acceptsChildren) return
        mutateTree { tree -> insertBlock(previous.id, previous.children.lastOrNull()?.id, block, removeBlock(block.id, tree)) }
        if (previous.type == BlockType.TOGGLE || previous.type.isHeading) _state.update { s ->
            val open = s.openToggles + keyOf(previous.id)
            s.copy(openToggles = open, rows = flatten(s.blocks, open))
        }
        recreate(key, keyOf(previous.id), previous.children.lastOrNull()?.id?.let(::keyOf))
        refocus(key)
    }

    fun outdent(key: String) {
        commitEditing()
        val block = blockByKey(key) ?: return
        val located = locate(block.id) ?: return
        val parentId = located.parentId ?: return
        val parent = locate(parentId) ?: return
        mutateTree { tree -> insertBlock(parent.parentId, parentId, block, removeBlock(block.id, tree)) }
        recreate(key, parent.parentId?.let(::keyOf), keyOf(parentId))
        refocus(key)
    }

    fun move(key: String, up: Boolean) {
        commitEditing()
        val block = blockByKey(key) ?: return
        val located = locate(block.id) ?: return
        val target = if (up) located.index - 1 else located.index + 1
        if (target !in located.siblings.indices) return
        val afterId = if (up) located.siblings.getOrNull(located.index - 2)?.id else located.siblings[target].id
        mutateTree { tree -> insertBlock(located.parentId, afterId, block, removeBlock(block.id, tree), atStart = afterId == null) }
        recreate(key, located.parentId?.let(::keyOf), afterId?.let(::keyOf), atStart = afterId == null)
        refocus(key)
    }

    private fun refocus(key: String) {
        if (_state.value.editingKey == key) {
            editState?.let { editState = RichEditState(it.toRichText(), it.selection.start) }
            _focus.value = FocusTarget(key, editState?.selection?.start ?: 0)
        }
    }

    private fun cloneBlockTree(block: NBlock): NBlock =
        block.copy(id = newTempId(), children = block.children.map(::cloneBlockTree))

    fun duplicate(key: String) {
        commitEditing()
        val block = blockByKey(key) ?: return
        val located = locate(block.id) ?: return
        insertBlock(block.copy(children = block.children.map(::cloneBlockTree)), afterKey = key, parentKey = located.parentId?.let(::keyOf), edit = false)
    }

    private fun patch(key: String, transform: (NBlock) -> NBlock) {
        val block = blockByKey(key) ?: return
        mutateTree { updateBlock(block.id, it, transform) }
        enqueue {
            val id = idOf(key)
            val latest = blockByKey(key) ?: return@enqueue
            if (!id.startsWith("tmp-")) session.pages.updateBlock(latest.copy(id = id))
        }
    }

    fun setChecked(key: String, checked: Boolean) = patch(key) { it.copy(checked = checked) }
    fun setColor(key: String, color: NColor) = patch(key) { it.copy(color = color) }
    fun setLanguage(key: String, language: String) = patch(key) { it.copy(language = language) }
    fun setCalloutIcon(key: String, icon: NIcon?) = patch(key) { it.copy(icon = icon) }
    fun setCaption(key: String, caption: List<RichText>) = patch(key) { it.copy(caption = caption) }
    fun setEquation(key: String, expression: String) = patch(key) { it.copy(expression = expression) }
    fun setUrl(key: String, url: String) = patch(key) { it.copy(url = url, file = it.file?.copy(url = url)) }
    fun setToggleable(key: String, toggleable: Boolean) {
        patch(key) { it.copy(toggleable = toggleable) }
        if (toggleable) _state.update { s ->
            val open = s.openToggles + key
            s.copy(openToggles = open, rows = flatten(s.blocks, open))
        }
    }
    fun setTableHeaders(key: String, column: Boolean, row: Boolean) = patch(key) { it.copy(hasColumnHeader = column, hasRowHeader = row) }
    fun setCodeText(key: String, text: String) = patch(key) { it.copy(rich = text.toRich()) }

    fun setCell(rowKey: String, column: Int, rich: List<RichText>) = patch(rowKey) { row ->
        val cells = row.cells.toMutableList()
        while (cells.size <= column) cells += emptyList<RichText>()
        cells[column] = rich
        row.copy(cells = cells)
    }

    fun addTableRow(tableKey: String) {
        val table = blockByKey(tableKey) ?: return
        val row = NBlock("", BlockType.TABLE_ROW, cells = List(table.tableWidth) { emptyList() })
        insertBlock(row, afterKey = table.children.lastOrNull()?.id?.let(::keyOf), parentKey = tableKey, edit = false)
    }

    fun toggleOpen(key: String) {
        _state.update { s ->
            val open = if (key in s.openToggles) s.openToggles - key else s.openToggles + key
            s.copy(openToggles = open, rows = flatten(s.blocks, open))
        }
        val block = blockByKey(key) ?: return
        if (block.hasChildren && block.children.isEmpty() && key in _state.value.openToggles) loadChildren(key)
    }

    fun setAllToggles(open: Boolean) {
        _state.update { s ->
            val keys = if (open) s.blocks.flatMap { it.walk().toList() }
                .filter { it.type == BlockType.TOGGLE || (it.type.isHeading && it.toggleable) }.map { keyOf(it.id) }.toSet() else emptySet()
            s.copy(openToggles = keys, rows = flatten(s.blocks, keys))
        }
    }

    fun loadChildren(key: String) {
        val id = idOf(key)
        viewModelScope.launch {
            runCatching { session.pages.loadChildren(id) }.onSuccess { kids ->
                mutateTree { updateBlock(id, it) { b -> b.copy(children = kids) } }
            }
        }
    }

    fun appendParagraph(edit: Boolean = true): String {
        val last = _state.value.blocks.lastOrNull()
        if (last != null && last.type == BlockType.PARAGRAPH && last.rich.isEmpty() && last.children.isEmpty()) {
            startEditing(keyOf(last.id), 0)
            return keyOf(last.id)
        }
        return insertBlock(NBlock("", BlockType.PARAGRAPH), afterKey = last?.id?.let(::keyOf), parentKey = null, edit = edit)
    }

    /** Uploads a local file and inserts a media block after [afterKey]. */
    fun uploadMedia(uri: Uri, type: BlockType, afterKey: String?, replaceKey: String? = null) {
        viewModelScope.launch {
            runCatching { session.uploads.upload(uri) }
                .onSuccess { up ->
                    val file = NFile("", up.filename, uploadId = up.uploadId)
                    val blockType = when {
                        type != BlockType.FILE -> type
                        up.contentType?.startsWith("image/") == true -> BlockType.IMAGE
                        up.contentType?.startsWith("video/") == true -> BlockType.VIDEO
                        up.contentType?.startsWith("audio/") == true -> BlockType.AUDIO
                        up.contentType == "application/pdf" -> BlockType.PDF
                        else -> BlockType.FILE
                    }
                    val block = NBlock("", blockType, file = file, url = uri.toString())
                    if (replaceKey != null) {
                        val located = blockByKey(replaceKey)?.let { locate(it.id) }
                        insertBlock(block, afterKey = replaceKey, parentKey = located?.parentId?.let(::keyOf), edit = false)
                        deleteBlock(replaceKey)
                    } else {
                        val parent = afterKey?.let { blockByKey(it) }?.let { locate(it.id)?.parentId }?.let(::keyOf)
                        insertBlock(block, afterKey, parentKey = parent, edit = false)
                    }
                }
                .onFailure { _events.emit(PageEvent.Message("Upload failed: ${it.message}")) }
        }
    }

    fun insertExternal(type: BlockType, url: String, afterKey: String?, replaceKey: String? = null) {
        val block = when (type) {
            BlockType.BOOKMARK, BlockType.EMBED -> NBlock("", type, url = url)
            else -> NBlock("", type, file = NFile(url), url = url)
        }
        val parent = (replaceKey ?: afterKey)?.let { blockByKey(it) }?.let { locate(it.id)?.parentId }?.let(::keyOf)
        insertBlock(block, afterKey = replaceKey ?: afterKey, parentKey = parent, edit = false)
        replaceKey?.let { deleteBlock(it) }
    }

    // Page-level actions

    fun rename(title: String) {
        val page = _state.value.page ?: return
        val key = page.titleEntry?.key ?: "title"
        val rich = title.toRich()
        val updatedPage = page.copy(properties = page.properties + (key to PropValue.Title(page.titleEntry?.value?.id ?: "title", rich)))
        _state.update { s -> s.copy(page = updatedPage) }
        session.cache.putPage(updatedPage)
        titleJob?.cancel()
        titleJob = viewModelScope.launch {
            delay(600)
            enqueue { session.pages.rename(pageId, key, rich) }
        }
    }

    fun setIcon(icon: NIcon?) {
        _state.update { s -> s.copy(page = s.page?.copy(icon = icon)) }
        enqueue { session.pages.setIcon(pageId, icon).also { p -> _state.update { it.copy(page = it.page?.copy(icon = p.icon)) } } }
    }

    fun setCover(cover: NFile?) {
        _state.update { s -> s.copy(page = s.page?.copy(cover = if (cover == null) null else cover.takeIf { it.url.isNotBlank() } ?: s.page.cover)) }
        enqueue { session.pages.setCover(pageId, cover).also { p -> _state.update { it.copy(page = it.page?.copy(cover = p.cover)) } } }
    }

    fun uploadCover(uri: Uri) {
        viewModelScope.launch {
            runCatching { session.uploads.upload(uri) }
                .onSuccess { up -> setCover(NFile("", up.filename, uploadId = up.uploadId)) }
                .onFailure { _events.emit(PageEvent.Message("Upload failed: ${it.message}")) }
        }
    }

    fun uploadIcon(uri: Uri) {
        viewModelScope.launch {
            runCatching { session.uploads.upload(uri) }
                .onSuccess { up -> setIcon(NIcon.Upload(up.uploadId)) }
                .onFailure { _events.emit(PageEvent.Message("Upload failed: ${it.message}")) }
        }
    }

    fun setLocked(locked: Boolean) {
        if (locked) stopEditing()
        _state.update { s -> s.copy(page = s.page?.copy(isLocked = locked)) }
        enqueue { session.pages.setLocked(pageId, locked) }
    }

    fun updateProperty(name: String, value: PropValue) {
        val page = _state.value.page ?: return
        _state.update { s -> s.copy(page = page.copy(properties = page.properties + (name to value))) }
        enqueue {
            val updated = session.pages.updateProperties(pageId, mapOf(name to value))
            _state.update { it.copy(page = updated) }
            updated.dataSourceId?.let { session.emit(ChangeEvent.RowsChanged(it)) }
        }
    }

    fun toggleFavorite() {
        val page = _state.value.page ?: return
        viewModelScope.launch { session.workspace.setFavorite(page.toRef(), !isFavorite.value) }
    }

    fun trash() {
        stopEditing()
        viewModelScope.launch {
            runCatching { session.pages.trash(pageId) }
                .onSuccess { _events.emit(PageEvent.Closed) }
                .onFailure { _events.emit(PageEvent.Message("Couldn't move to trash: ${it.message}")) }
        }
    }

    fun restore() {
        viewModelScope.launch {
            runCatching { session.pages.restore(pageId) }
                .onSuccess { p -> _state.update { it.copy(page = p) } }
                .onFailure { _events.emit(PageEvent.Message("Couldn't restore: ${it.message}")) }
        }
    }

    suspend fun duplicate(): NPage? {
        commitEditing()
        val page = _state.value.page ?: return null
        return runCatching { session.pages.duplicate(page, _state.value.blocks) }
            .onFailure { _events.emit(PageEvent.Message("Couldn't duplicate: ${it.message}")) }.getOrNull()
    }

    fun moveTo(destination: NodeRef) {
        viewModelScope.launch {
            runCatching {
                if (destination.kind == com.manuscript.core.model.NodeKind.PAGE) session.pages.move(pageId, toPage = destination.id)
                else {
                    val dsId = if (destination.kind == com.manuscript.core.model.NodeKind.DATA_SOURCE) destination.id
                    else session.databases.database(destination.id).dataSources.firstOrNull()?.id ?: error("Database has no data source")
                    session.pages.move(pageId, toDataSource = dsId)
                }
            }.onSuccess { p ->
                _state.update { it.copy(page = p) }
                _events.emit(PageEvent.Message("Moved to ${destination.title.ifBlank { "Untitled" }}"))
            }.onFailure { _events.emit(PageEvent.Message("Couldn't move: ${it.message}")) }
        }
    }

    fun applyTemplate(template: NTemplate?, erase: Boolean) {
        stopEditing()
        viewModelScope.launch {
            runCatching { session.pages.applyTemplate(pageId, template?.id, erase) }
                .onSuccess { delay(800); load(refresh = true) }
                .onFailure { _events.emit(PageEvent.Message("Couldn't apply template: ${it.message}")) }
        }
    }

    suspend fun markdown(): String {
        commitEditing()
        val page = _state.value.page
        return runCatching { session.pages.markdown(pageId) }.getOrNull()?.takeIf { it.isNotBlank() }
            ?.let { "# ${page?.title ?: ""}\n\n$it" }
            ?: MarkdownExport.page(page?.title ?: "Untitled", page?.icon, _state.value.blocks)
    }

    fun createSubpage(title: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { session.pages.createPage(CreateParent.Page(pageId), title) }
                .onSuccess { p ->
                    val block = NBlock(p.id, BlockType.CHILD_PAGE, title = title, parent = Parent.Page(pageId))
                    mutateTree { it + block }
                    onCreated(p.id)
                }
                .onFailure { _events.emit(PageEvent.Message("Couldn't create page: ${it.message}")) }
        }
    }

    fun parentChain(): List<NodeRef> {
        val chain = mutableListOf<NodeRef>()
        var parent = _state.value.page?.parent
        var guard = 0
        while (parent != null && guard++ < 8) {
            val id = parent.parentId ?: break
            val p = session.cache.page(id)
            val ds = session.cache.dataSource(id) ?: (parent as? Parent.DataSource)?.let { session.cache.dataSource(it.id) }
            val db = session.cache.database(id)
            when {
                p != null -> { chain += p.toRef(); parent = p.parent }
                ds != null -> { chain += ds.toRef(); parent = ds.databaseParent }
                db != null -> { chain += com.manuscript.core.model.NodeRef(db.id, com.manuscript.core.model.NodeKind.DATABASE, db.titleText, db.icon); parent = db.parent }
                else -> break
            }
        }
        return chain.reversed()
    }

    override fun onCleared() {
        commitEditing()
        val dirty = textJobs.keys.toList()
        textJobs.values.forEach { it.cancel() }
        textJobs.clear()
        dirty.forEach { key ->
            val block = blockByKey(key) ?: return@forEach
            val id = idOf(key)
            if (!id.startsWith("tmp-")) ops.trySend { session.pages.updateBlock(block.copy(id = id)) }
        }
        titleJob?.let { job ->
            if (job.isActive) {
                job.cancel()
                val page = _state.value.page
                val key = page?.titleEntry?.key
                if (page != null && key != null) ops.trySend { session.pages.rename(pageId, key, page.titleRich) }
            }
        }
        ops.close()
        super.onCleared()
    }
}
