package com.manuscript.core.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.manuscript.core.data.db.AppDatabase
import com.manuscript.core.data.db.ChildrenEntity
import com.manuscript.core.data.db.FavoriteEntity
import com.manuscript.core.data.db.KvEntity
import com.manuscript.core.data.db.RecentEntity
import com.manuscript.core.data.db.SearchEntity
import com.manuscript.core.json.Parse
import com.manuscript.core.json.Write
import com.manuscript.core.json.a
import com.manuscript.core.json.b
import com.manuscript.core.json.encode
import com.manuscript.core.json.obj
import com.manuscript.core.json.objs
import com.manuscript.core.json.parseObject
import com.manuscript.core.json.s
import com.manuscript.core.model.BlockType
import com.manuscript.core.model.NBlock
import com.manuscript.core.model.NComment
import com.manuscript.core.model.NDataSource
import com.manuscript.core.model.NDatabase
import com.manuscript.core.model.NFile
import com.manuscript.core.model.NIcon
import com.manuscript.core.model.NPage
import com.manuscript.core.model.NTemplate
import com.manuscript.core.model.NUser
import com.manuscript.core.model.NView
import com.manuscript.core.model.NodeKind
import com.manuscript.core.model.NodeRef
import com.manuscript.core.model.Paged
import com.manuscript.core.model.Parent
import com.manuscript.core.model.PropValue
import com.manuscript.core.model.PropertySchema
import com.manuscript.core.model.RichText
import com.manuscript.core.model.ViewType
import com.manuscript.core.model.toRef
import com.manuscript.core.net.NotionApi
import com.manuscript.core.net.NotionException
import com.manuscript.core.net.SearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

sealed interface ChangeEvent {
    data class PageCreated(val page: NPage) : ChangeEvent
    data class PageTrashed(val id: String) : ChangeEvent
    data class PageRestored(val id: String) : ChangeEvent
    data class PageMoved(val id: String) : ChangeEvent
    data class ChildrenChanged(val parentId: String) : ChangeEvent
    data class RowsChanged(val dataSourceId: String) : ChangeEvent
    data class SchemaChanged(val dataSourceId: String) : ChangeEvent
}

fun iconToJson(icon: NIcon?): String? = icon?.let { Write.icon(it).encode() }
fun iconFromJson(json: String?): NIcon? = json?.let { runCatching { Parse.icon(parseObject(it)) }.getOrNull() }

class UserRepository(
    private val api: NotionApi,
    private val cache: ObjectCache,
    private val scope: CoroutineScope,
) {
    private val inflight = mutableSetOf<String>()
    private val mutex = Mutex()
    private val _listAvailable = MutableStateFlow<Boolean?>(null)
    val listAvailable: StateFlow<Boolean?> = _listAvailable.asStateFlow()
    val users: StateFlow<Map<String, NUser>> get() = cache.users

    suspend fun loadAll(): List<NUser> = try {
        val all = mutableListOf<NUser>()
        var cursor: String? = null
        do {
            val page = api.users(cursor)
            all += page.items
            cursor = page.nextCursor
        } while (page.hasMore && cursor != null)
        cache.putUsers(all)
        _listAvailable.value = true
        all
    } catch (e: NotionException.Api) {
        _listAvailable.value = false
        cache.users.value.values.toList()
    }

    /** Fetches any users that aren't cached yet, in the background. */
    fun ensure(ids: Collection<String?>) {
        val missing = ids.filterNotNull().filter { it.isNotBlank() && cache.user(it) == null }.toSet()
        if (missing.isEmpty()) return
        scope.launch {
                val toFetch = mutex.withLock { missing.filter { it !in inflight }.also { inflight += it } }
                toFetch.chunked(4).forEach { chunk ->
                    try {
                        val fetched = coroutineScope {
                            chunk.map { id -> async { runCatching { api.user(id) }.getOrNull() } }.awaitAll()
                        }
                        cache.putUsers(fetched.filterNotNull())
                    } finally {
                        mutex.withLock { inflight.removeAll(chunk.toSet()) }
                    }
                }
        }
    }

    fun user(id: String?): NUser? = cache.user(id)
}

class WorkspaceRepository(
    private val api: NotionApi,
    private val cache: ObjectCache,
    private val db: AppDatabase,
    private val accountId: String,
    private val events: MutableSharedFlow<ChangeEvent>,
) {
    private val _roots = MutableStateFlow<List<NodeRef>?>(null)
    val roots: StateFlow<List<NodeRef>?> = _roots.asStateFlow()
    private val _rootsLoading = MutableStateFlow(false)
    val rootsLoading: StateFlow<Boolean> = _rootsLoading.asStateFlow()
    private val treeCache = mutableMapOf<String, List<NodeRef>>()

    suspend fun loadCachedRoots() {
        if (_roots.value != null) return
        val pages = cache.storedPages()
        val sources = cache.storedDataSources()
        if (pages.isNotEmpty() || sources.isNotEmpty()) _roots.value = computeRoots(pages, sources)
    }

    suspend fun refreshRoots(maxPages: Int = 8) {
        if (_rootsLoading.value) return
        _rootsLoading.value = true
        try {
            val pages = mutableListOf<NPage>()
            val sources = mutableListOf<NDataSource>()
            var cursor: String? = null
            var n = 0
            do {
                val result = api.search(cursor = cursor, pageSize = 100, newestFirst = true)
                result.items.forEach {
                    when (it) {
                        is SearchResult.PageHit -> pages += it.page
                        is SearchResult.DataSourceHit -> sources += it.dataSource
                    }
                }
                cursor = result.nextCursor
                n++
                _roots.value = computeRoots(pages, sources)
            } while (result.hasMore && cursor != null && n < maxPages)
        } finally {
            _rootsLoading.value = false
        }
    }

    private fun computeRoots(pages: List<NPage>, sources: List<NDataSource>): List<NodeRef> {
        val known = buildSet {
            pages.forEach { add(it.id) }
            sources.forEach { add(it.id); it.databaseId?.let(::add) }
        }
        val rootPages = pages.filter { p ->
            !p.inTrash && !p.isDatabaseRow && when (val parent = p.parent) {
                Parent.Workspace -> true
                is Parent.Page -> parent.id !in known
                else -> false
            }
        }
        val rootSources = sources.filter { ds ->
            !ds.inTrash && when (val dp = ds.databaseParent) {
                Parent.Workspace, null -> true
                is Parent.Page -> dp.id !in known
                else -> false
            }
        }.distinctBy { it.databaseId ?: it.id }
        return (rootPages.map { it.toRef() } + rootSources.map { it.toRef() })
            .sortedWith(compareBy({ it.parent != Parent.Workspace }, { it.title.lowercase() }))
    }

    fun cachedTreeChildren(id: String): List<NodeRef>? = treeCache[id]

    /** Child pages and databases shown when a sidebar node is expanded. */
    suspend fun treeChildren(ref: NodeRef): List<NodeRef> {
        if (ref.kind != NodeKind.PAGE) return emptyList()
        val blocks = allChildren(ref.id)
        val nested = coroutineScope {
            blocks.filter { it.s("type") == "column_list" }.mapNotNull { list ->
                val listId = list.s("id") ?: return@mapNotNull null
                async {
                    allChildren(listId).flatMap { col ->
                        col.s("id")?.let { allChildren(it) } ?: emptyList()
                    }
                }
            }.awaitAll().flatten()
        }
        val result = (blocks + nested).mapNotNull { raw ->
            val id = raw.s("id") ?: return@mapNotNull null
            when (raw.s("type")) {
                "child_page" -> {
                    val cached = cache.page(id)
                    NodeRef(id, NodeKind.PAGE, raw.o2("child_page")?.s("title") ?: cached?.title ?: "", icon = cached?.icon,
                        parent = Parent.Page(ref.id), lastEditedTime = raw.s("last_edited_time"))
                }
                "child_database" -> {
                    val db = cache.database(id)
                    val ds = cache.dataSources.value.values.firstOrNull { it.databaseId == id }
                    NodeRef(id, NodeKind.DATABASE, raw.o2("child_database")?.s("title") ?: db?.titleText ?: "",
                        icon = db?.icon ?: ds?.icon, parent = Parent.Page(ref.id), databaseId = id,
                        lastEditedTime = raw.s("last_edited_time"))
                }
                else -> null
            }
        }
        treeCache[ref.id] = result
        db.cache().putKv(KvEntity(accountId, "tree:${ref.id}", JsonArray(result.map { it.id.toJsonPrimitive() }).encode()))
        return result
    }

    private fun JsonObject.o2(key: String) = this[key] as? JsonObject
    private fun String.toJsonPrimitive() = kotlinx.serialization.json.JsonPrimitive(this)

    private suspend fun allChildren(id: String): List<JsonObject> {
        val all = mutableListOf<JsonObject>()
        var cursor: String? = null
        do {
            val page = api.childrenRaw(id, cursor)
            all += page.items
            cursor = page.nextCursor
        } while (page.hasMore && cursor != null)
        return all
    }

    suspend fun search(
        query: String,
        objectType: String? = null,
        cursor: String? = null,
        newestFirst: Boolean? = null,
        inTrash: Boolean = false,
    ): Paged<NodeRef> {
        val result = api.search(query, objectType, newestFirst, inTrash, cursor, pageSize = 30)
        return Paged(result.items.map {
            when (it) {
                is SearchResult.PageHit -> it.page.toRef()
                is SearchResult.DataSourceHit -> it.dataSource.toRef()
            }
        }, result.nextCursor, result.hasMore)
    }

    suspend fun offlineSearch(query: String): List<NodeRef> = cache.offlineSearch(query).mapNotNull { e ->
        runCatching {
            val raw = parseObject(e.json)
            when (e.kind) {
                "page" -> Parse.page(raw).toRef()
                "data_source" -> Parse.dataSource(raw).toRef()
                else -> null
            }
        }.getOrNull()
    }

    suspend fun recentlyEdited(cursor: String? = null, pageSize: Int = 30): Paged<NodeRef> {
        val result = api.search(newestFirst = true, cursor = cursor, pageSize = pageSize)
        return Paged(result.items.map {
            when (it) {
                is SearchResult.PageHit -> it.page.toRef()
                is SearchResult.DataSourceHit -> it.dataSource.toRef()
            }
        }, result.nextCursor, result.hasMore)
    }

    val recents: Flow<List<NodeRef>> = db.recents().observe(accountId).map { list ->
        list.map { e ->
            NodeRef(e.id, NodeKind.valueOf(e.kind), e.title, iconFromJson(e.iconJson),
                cover = e.coverUrl?.let { NFile(it) }, lastEditedTime = null)
        }
    }

    suspend fun recordVisit(ref: NodeRef) {
        val dao = db.recents()
        val existing = dao.get(accountId, ref.id)
        dao.put(
            RecentEntity(accountId, ref.id, ref.kind.name, ref.title, iconToJson(ref.icon), ref.cover?.url,
                System.currentTimeMillis(), (existing?.visits ?: 0) + 1),
        )
        db.favorites().refresh(accountId, ref.id, ref.title, iconToJson(ref.icon))
    }

    suspend fun removeRecent(id: String) = db.recents().delete(accountId, id)
    suspend fun clearRecents() = db.recents().clear(accountId)

    val favorites: Flow<List<NodeRef>> = db.favorites().observe(accountId).map { list ->
        list.map { NodeRef(it.id, NodeKind.valueOf(it.kind), it.title, iconFromJson(it.iconJson)) }
    }

    fun isFavorite(id: String): Flow<Boolean> = db.favorites().observeIsFavorite(accountId, id).map { it > 0 }

    suspend fun setFavorite(ref: NodeRef, favorite: Boolean) {
        val dao = db.favorites()
        if (favorite) {
            dao.put(FavoriteEntity(accountId, ref.id, ref.kind.name, ref.title, iconToJson(ref.icon),
                dao.maxPosition(accountId) + 1, System.currentTimeMillis()))
        } else dao.delete(accountId, ref.id)
    }

    suspend fun reorderFavorites(ordered: List<NodeRef>) {
        db.favorites().putAll(ordered.mapIndexed { i, ref ->
            FavoriteEntity(accountId, ref.id, ref.kind.name, ref.title, iconToJson(ref.icon), i, System.currentTimeMillis())
        })
    }

    val searchHistory: Flow<List<String>> = db.searches().observe(accountId).map { list -> list.map { it.query } }
    suspend fun rememberSearch(query: String) {
        if (query.isNotBlank()) db.searches().put(SearchEntity(accountId, query.trim(), System.currentTimeMillis()))
    }
    suspend fun forgetSearch(query: String) = db.searches().delete(accountId, query)
    suspend fun clearSearches() = db.searches().clear(accountId)

    suspend fun cacheStats(): Pair<Int, Long> = db.cache().cachedPageCount(accountId) to db.cache().cachedBytes(accountId)

    suspend fun clearCache() {
        db.cache().clearChildren(accountId)
        db.cache().clearObjects(accountId)
        treeCache.clear()
    }

    suspend fun kv(key: String): String? = db.cache().getKv(accountId, key)
    suspend fun putKv(key: String, value: String) = db.cache().putKv(KvEntity(accountId, key, value))
    fun observeKv(key: String): Flow<String?> = db.cache().observeKv(accountId, key)

    suspend fun emit(event: ChangeEvent) = events.emit(event)
}

sealed interface CreateParent {
    data class Page(val id: String) : CreateParent
    data class DataSource(val id: String) : CreateParent
    data object Workspace : CreateParent
}

class PageRepository(
    private val api: NotionApi,
    private val cache: ObjectCache,
    private val db: AppDatabase,
    private val accountId: String,
    private val events: MutableSharedFlow<ChangeEvent>,
) {
    private val semaphore = Semaphore(4)

    suspend fun page(id: String): NPage = api.page(id)

    suspend fun cachedBlocks(pageId: String): List<NBlock>? = db.cache().getChildren(accountId, pageId)?.let { e ->
        runCatching { (com.manuscript.core.json.NotionJson.parseToJsonElement(e.json) as JsonArray).objs().map(Parse::block) }.getOrNull()
    }

    /** Loads the full block tree, reporting the top level first so the page can render immediately. */
    suspend fun loadBlocks(pageId: String, onTopLevel: suspend (List<NBlock>) -> Unit = {}): List<NBlock> {
        val top = rawChildren(pageId)
        onTopLevel(top.map(Parse::block))
        val tree = withContext(Dispatchers.IO) { expand(top, 0) }
        db.cache().putChildren(ChildrenEntity(accountId, pageId, JsonArray(tree).encode(), System.currentTimeMillis()))
        return tree.map(Parse::block)
    }

    /** Loads the nested children of a single block. */
    suspend fun loadChildren(blockId: String): List<NBlock> = expand(rawChildren(blockId), 1).map(Parse::block)

    private suspend fun rawChildren(id: String): List<JsonObject> {
        val all = mutableListOf<JsonObject>()
        var cursor: String? = null
        do {
            val page = semaphore.withPermit { api.childrenRaw(id, cursor) }
            all += page.items
            cursor = page.nextCursor
        } while (page.hasMore && cursor != null)
        return all
    }

    private suspend fun expand(blocks: List<JsonObject>, depth: Int): List<JsonObject> = coroutineScope {
        blocks.map { raw ->
            async {
                val type = raw.s("type")
                val id = raw.s("id")
                if (raw.b("has_children") == true && id != null && type != "child_page" && type != "child_database" && depth < 12) {
                    var children = runCatching { rawChildren(id) }.getOrDefault(emptyList())
                    if (children.isEmpty() && type == "synced_block") {
                        val source = (raw["synced_block"] as? JsonObject)?.let { (it["synced_from"] as? JsonObject)?.s("block_id") }
                        if (source != null) children = runCatching { rawChildren(source) }.getOrDefault(emptyList())
                    }
                    JsonObject(raw + ("_children" to JsonArray(expand(children, depth + 1))))
                } else raw
            }
        }.awaitAll()
    }

    suspend fun updateBlock(block: NBlock): NBlock = Parse.block(api.updateBlock(block.id, Write.blockUpdate(block)))

    suspend fun deleteBlock(id: String) {
        api.deleteBlock(id)
    }

    /** Appends blocks (with their nested children) and returns the created top-level blocks in order. */
    suspend fun appendBlocks(parentId: String, blocks: List<NBlock>, afterId: String? = null, atStart: Boolean = false): List<NBlock> {
        val created = mutableListOf<NBlock>()
        var after = afterId
        var start = atStart && afterId == null
        for (chunk in blocks.chunked(100)) {
            val payload = chunk.map { b -> if (b.type.needsInlineChildren) Write.blockCreate(b) else Write.blockCreate(b.copy(children = emptyList())) }
            val results = api.appendChildren(parentId, payload, after, start).map(Parse::block)
            start = false
            results.zip(chunk).forEach { (made, src) ->
                var finalBlock = made
                if (!src.type.needsInlineChildren && src.children.isNotEmpty() && made.acceptsChildren) {
                    val kids = appendBlocks(made.id, src.children)
                    finalBlock = made.copy(children = kids, hasChildren = true)
                } else if (src.type.needsInlineChildren) {
                    finalBlock = made.copy(children = src.children)
                }
                created += finalBlock
            }
            after = results.lastOrNull()?.id ?: after
        }
        events.emit(ChangeEvent.ChildrenChanged(parentId))
        return created
    }

    private val BlockType.needsInlineChildren: Boolean
        get() = this == BlockType.TABLE || this == BlockType.COLUMN_LIST || this == BlockType.TAB

    suspend fun updateProperties(pageId: String, values: Map<String, PropValue>): NPage =
        api.updatePage(pageId, obj("properties" to Write.properties(values)))

    suspend fun rename(pageId: String, titleKey: String, title: List<RichText>): NPage =
        api.updatePage(pageId, obj("properties" to obj(titleKey to obj("title" to Write.richText(title)))))

    suspend fun setIcon(pageId: String, icon: NIcon?): NPage = api.updatePage(pageId, obj("icon" to Write.icon(icon)))

    suspend fun setCover(pageId: String, cover: NFile?): NPage = api.updatePage(pageId, obj("cover" to Write.cover(cover)))

    suspend fun setLocked(pageId: String, locked: Boolean): NPage = api.updatePage(pageId, obj("is_locked" to locked))

    suspend fun trash(pageId: String): NPage = api.updatePage(pageId, obj("in_trash" to true)).also {
        cache.removePage(pageId)
        events.emit(ChangeEvent.PageTrashed(pageId))
    }

    suspend fun restore(pageId: String): NPage = api.updatePage(pageId, obj("in_trash" to false)).also {
        events.emit(ChangeEvent.PageRestored(pageId))
    }

    suspend fun move(pageId: String, toPage: String? = null, toDataSource: String? = null): NPage {
        val parent = if (toDataSource != null) obj("type" to "data_source_id", "data_source_id" to toDataSource)
        else obj("type" to "page_id", "page_id" to (toPage ?: error("A destination is required")))
        return api.movePage(pageId, parent).also { events.emit(ChangeEvent.PageMoved(pageId)) }
    }

    suspend fun applyTemplate(pageId: String, templateId: String?, eraseContent: Boolean): NPage = api.updatePage(
        pageId,
        obj(
            "template" to if (templateId != null) obj("type" to "template_id", "template_id" to templateId) else obj("type" to "default"),
            "erase_content" to eraseContent,
        ),
    )

    suspend fun createPage(
        parent: CreateParent,
        title: String,
        icon: NIcon? = null,
        cover: NFile? = null,
        children: List<NBlock> = emptyList(),
        markdown: String? = null,
        properties: Map<String, PropValue> = emptyMap(),
        titleKey: String = "title",
        templateId: String? = null,
        useDefaultTemplate: Boolean = false,
        afterBlockId: String? = null,
    ): NPage {
        val props = LinkedHashMap<String, JsonObject>()
        props[titleKey] = obj("title" to Write.richText(com.manuscript.core.model.RichText.plain(title).let { if (title.isEmpty()) emptyList() else listOf(it) }))
        properties.forEach { (k, v) -> Write.propValue(v)?.let { props[k] = it } }
        val parentJson = when (parent) {
            is CreateParent.Page -> obj("type" to "page_id", "page_id" to parent.id)
            is CreateParent.DataSource -> obj("type" to "data_source_id", "data_source_id" to parent.id)
            CreateParent.Workspace -> obj("type" to "workspace", "workspace" to true)
        }
        val template = when {
            templateId != null -> obj("type" to "template_id", "template_id" to templateId, "timezone" to java.time.ZoneId.systemDefault().id)
            useDefaultTemplate -> obj("type" to "default", "timezone" to java.time.ZoneId.systemDefault().id)
            else -> com.manuscript.core.json.Skip
        }
        val firstChildren = children.take(100)
        val body = obj(
            "parent" to parentJson,
            "properties" to JsonObject(props),
            "icon" to (icon?.let(Write::icon) ?: com.manuscript.core.json.Skip),
            "cover" to (cover?.let(Write::cover) ?: com.manuscript.core.json.Skip),
            "children" to if (firstChildren.isNotEmpty() && templateId == null && !useDefaultTemplate && markdown == null)
                JsonArray(firstChildren.map { Write.blockCreate(it.copy(children = if (it.type.needsInlineChildren) it.children else emptyList())) })
            else com.manuscript.core.json.Skip,
            "markdown" to (markdown ?: com.manuscript.core.json.Skip),
            "template" to template,
            "position" to (afterBlockId?.let { obj("type" to "after_block", "after_block" to obj("id" to it)) } ?: com.manuscript.core.json.Skip),
        )
        val page = api.createPage(body)
        if (children.isNotEmpty() && templateId == null && !useDefaultTemplate && markdown == null) {
            val nestedNeeded = firstChildren.any { !it.type.needsInlineChildren && it.children.isNotEmpty() } || children.size > 100
            if (nestedNeeded) {
                val created = rawChildren(page.id)
                created.map(Parse::block).zip(firstChildren).forEach { (made, src) ->
                    if (!src.type.needsInlineChildren && src.children.isNotEmpty() && made.acceptsChildren) appendBlocks(made.id, src.children)
                }
                if (children.size > 100) appendBlocks(page.id, children.drop(100))
            }
        }
        events.emit(ChangeEvent.PageCreated(page))
        when (parent) {
            is CreateParent.Page -> events.emit(ChangeEvent.ChildrenChanged(parent.id))
            is CreateParent.DataSource -> events.emit(ChangeEvent.RowsChanged(parent.id))
            CreateParent.Workspace -> Unit
        }
        return page
    }

    suspend fun duplicate(page: NPage, blocks: List<NBlock>, titleSuffix: String = " (copy)"): NPage {
        val parent = when (val p = page.parent) {
            is Parent.Page -> CreateParent.Page(p.id)
            is Parent.DataSource -> CreateParent.DataSource(p.id)
            is Parent.Database -> {
                val dsId = cache.database(p.id)?.dataSources?.firstOrNull()?.id ?: p.id
                CreateParent.DataSource(dsId)
            }
            else -> CreateParent.Workspace
        }
        val copyable = blocks.filter { it.type != BlockType.CHILD_PAGE && it.type != BlockType.CHILD_DATABASE && it.type != BlockType.UNSUPPORTED }
        val props = if (parent is CreateParent.DataSource) page.properties.filterValues { it !is PropValue.Title } else emptyMap()
        return createPage(
            parent = parent,
            title = page.title + titleSuffix,
            icon = page.icon?.takeUnless { it is NIcon.Hosted },
            cover = page.cover?.takeUnless { it.hosted },
            children = copyable,
            properties = props,
            titleKey = page.titleEntry?.key ?: "title",
        )
    }

    suspend fun markdown(pageId: String): String = api.pageMarkdown(pageId)

    suspend fun appendMarkdown(pageId: String, markdown: String) {
        api.appendMarkdown(pageId, markdown)
        events.emit(ChangeEvent.ChildrenChanged(pageId))
    }
}

class DatabaseRepository(
    private val api: NotionApi,
    private val cache: ObjectCache,
    private val events: MutableSharedFlow<ChangeEvent>,
) {
    suspend fun database(id: String): NDatabase = api.database(id)
    suspend fun dataSource(id: String): NDataSource = api.dataSource(id)

    /** Accepts either a database ID or a data source ID and returns both objects. */
    suspend fun resolve(id: String, dataSourceHint: String? = null): Pair<NDatabase, List<NDataSource>> {
        val database = runCatching { api.database(id) }.getOrElse { first ->
            val ds = runCatching { api.dataSource(dataSourceHint ?: id) }.getOrNull() ?: throw first
            api.database(ds.databaseId ?: throw first)
        }
        val sources = coroutineScope {
            database.dataSources.map { ref -> async { runCatching { api.dataSource(ref.id) }.getOrNull() } }.awaitAll().filterNotNull()
        }
        return database to sources
    }

    suspend fun views(databaseId: String): List<NView> {
        val ids = runCatching { api.viewIds(databaseId = databaseId) }.getOrDefault(emptyList())
        val semaphore = Semaphore(4)
        return coroutineScope {
            ids.map { id -> async { semaphore.withPermit { runCatching { api.view(id) }.getOrNull() } } }.awaitAll().filterNotNull()
        }
    }

    suspend fun view(id: String): NView = api.view(id)

    suspend fun query(
        dataSourceId: String,
        filter: JsonObject?,
        sorts: JsonArray?,
        cursor: String? = null,
        pageSize: Int = 100,
    ): Paged<NPage> = api.queryDataSource(dataSourceId, filter, sorts, cursor, pageSize)

    suspend fun templates(dataSourceId: String): List<NTemplate> = runCatching { api.templates(dataSourceId) }.getOrDefault(emptyList())

    suspend fun updateRow(pageId: String, values: Map<String, PropValue>): NPage =
        api.updatePage(pageId, obj("properties" to Write.properties(values)))

    suspend fun updateSchema(dataSourceId: String, properties: JsonObject): NDataSource =
        api.updateDataSource(dataSourceId, obj("properties" to properties)).also {
            events.emit(ChangeEvent.SchemaChanged(dataSourceId))
        }

    suspend fun addProperty(dataSourceId: String, schema: PropertySchema): NDataSource =
        updateSchema(dataSourceId, obj(schema.name to Write.propertyConfig(schema, includeName = false)))

    suspend fun updateProperty(dataSourceId: String, original: PropertySchema, updated: PropertySchema): NDataSource =
        updateSchema(dataSourceId, obj(original.id to Write.propertyConfig(updated, includeName = updated.name != original.name)))

    suspend fun renameProperty(dataSourceId: String, original: PropertySchema, name: String): NDataSource =
        updateSchema(dataSourceId, obj(original.id to obj("name" to name)))

    suspend fun deleteProperty(dataSourceId: String, property: PropertySchema): NDataSource =
        updateSchema(dataSourceId, obj(property.id to JsonNull))

    suspend fun renameDataSource(dataSourceId: String, title: String): NDataSource =
        api.updateDataSource(dataSourceId, obj("title" to Write.richText(listOf(RichText.plain(title)))))

    suspend fun addDataSource(databaseId: String, title: String): NDataSource = api.createDataSource(
        obj(
            "parent" to obj("type" to "database_id", "database_id" to databaseId),
            "title" to Write.richText(listOf(RichText.plain(title))),
            "properties" to obj("Name" to obj("title" to obj())),
        ),
    )

    suspend fun updateDatabase(
        id: String,
        title: String? = null,
        description: List<RichText>? = null,
        icon: NIcon? = null,
        clearIcon: Boolean = false,
        cover: NFile? = null,
        clearCover: Boolean = false,
        inline: Boolean? = null,
        locked: Boolean? = null,
        trash: Boolean? = null,
    ): NDatabase {
        val body = obj(
            "title" to (title?.let { Write.richText(listOf(RichText.plain(it))) } ?: com.manuscript.core.json.Skip),
            "description" to (description?.let { Write.richText(it) } ?: com.manuscript.core.json.Skip),
            "icon" to if (clearIcon) JsonNull else (icon?.let(Write::icon) ?: com.manuscript.core.json.Skip),
            "cover" to if (clearCover) JsonNull else (cover?.let(Write::cover) ?: com.manuscript.core.json.Skip),
            "is_inline" to (inline ?: com.manuscript.core.json.Skip),
            "is_locked" to (locked ?: com.manuscript.core.json.Skip),
            "in_trash" to (trash ?: com.manuscript.core.json.Skip),
        )
        return api.updateDatabase(id, body)
    }

    suspend fun createDatabase(
        parentPageId: String?,
        title: String,
        properties: List<PropertySchema>,
        inline: Boolean,
        icon: NIcon? = null,
        databaseType: String? = null,
    ): NDatabase {
        val parent = parentPageId?.let { obj("type" to "page_id", "page_id" to it) } ?: obj("type" to "workspace", "workspace" to true)
        val propsJson = JsonObject(properties.associate { it.name to Write.propertyConfig(it, includeName = false) })
        val body = obj(
            "parent" to parent,
            "title" to Write.richText(listOf(RichText.plain(title))),
            "is_inline" to inline,
            "icon" to (icon?.let(Write::icon) ?: com.manuscript.core.json.Skip),
            "initial_data_source" to if (databaseType == null) obj("properties" to propsJson) else com.manuscript.core.json.Skip,
            "database_type" to (databaseType ?: com.manuscript.core.json.Skip),
        )
        return api.createDatabase(body).also { db ->
            parentPageId?.let { events.emit(ChangeEvent.ChildrenChanged(it)) }
        }
    }

    suspend fun createView(
        databaseId: String,
        dataSourceId: String,
        name: String,
        type: ViewType,
        configuration: JsonObject? = null,
        filter: JsonObject? = null,
        sorts: JsonArray? = null,
    ): NView = api.createView(
        obj(
            "database_id" to databaseId,
            "data_source_id" to dataSourceId,
            "name" to name,
            "type" to type.api,
            "configuration" to (configuration ?: com.manuscript.core.json.Skip),
            "filter" to (filter ?: com.manuscript.core.json.Skip),
            "sorts" to (sorts ?: com.manuscript.core.json.Skip),
        ),
    )

    suspend fun updateView(
        viewId: String,
        name: String? = null,
        filter: JsonObject? = null,
        clearFilter: Boolean = false,
        sorts: JsonArray? = null,
        configuration: JsonObject? = null,
    ): NView = api.updateView(
        viewId,
        obj(
            "name" to (name ?: com.manuscript.core.json.Skip),
            "filter" to if (clearFilter) JsonNull else (filter ?: com.manuscript.core.json.Skip),
            "sorts" to (sorts ?: com.manuscript.core.json.Skip),
            "configuration" to (configuration ?: com.manuscript.core.json.Skip),
        ),
    )

    suspend fun deleteView(viewId: String) = api.deleteView(viewId)

    suspend fun relationItems(pageId: String, propertyId: String): List<String> {
        val ids = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page = api.pagePropertyItems(pageId, propertyId, cursor)
            page.items.forEach { item -> (item["relation"] as? JsonObject)?.s("id")?.let(ids::add) }
            cursor = page.nextCursor
        } while (page.hasMore && cursor != null)
        return ids
    }

    suspend fun emitRowsChanged(dataSourceId: String) = events.emit(ChangeEvent.RowsChanged(dataSourceId))
}

class CommentRepository(private val api: NotionApi) {
    suspend fun list(blockId: String): List<NComment> {
        val all = mutableListOf<NComment>()
        var cursor: String? = null
        do {
            val page = api.comments(blockId, cursor)
            all += page.items
            cursor = page.nextCursor
        } while (page.hasMore && cursor != null)
        return all
    }

    suspend fun create(
        rich: List<RichText>,
        pageId: String? = null,
        blockId: String? = null,
        discussionId: String? = null,
        attachments: List<String> = emptyList(),
    ): NComment {
        val parent: Any = when {
            discussionId != null -> com.manuscript.core.json.Skip
            blockId != null -> obj("type" to "block_id", "block_id" to blockId)
            else -> obj("type" to "page_id", "page_id" to (pageId ?: error("A page or block is required")))
        }
        return api.createComment(
            obj(
                "parent" to parent,
                "discussion_id" to (discussionId ?: com.manuscript.core.json.Skip),
                "rich_text" to Write.richText(rich),
                "attachments" to if (attachments.isEmpty()) com.manuscript.core.json.Skip
                else JsonArray(attachments.map { obj("type" to "file_upload", "file_upload_id" to it) }),
            ),
        )
    }

    suspend fun update(id: String, rich: List<RichText>): NComment = api.updateComment(id, obj("rich_text" to Write.richText(rich)))
    suspend fun delete(id: String) = api.deleteComment(id)
}

data class UploadedFile(val uploadId: String, val filename: String, val contentType: String?, val size: Long)

class UploadRepository(private val api: NotionApi, private val context: Context, private val maxBytes: Long?) {
    private val singlePartLimit = 20L * 1024 * 1024
    private val partSize = 10 * 1024 * 1024

    suspend fun upload(uri: Uri, onProgress: (Float) -> Unit = {}): UploadedFile = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = "upload"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        val type = resolver.getType(uri) ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', ""))
        if (maxBytes != null && size > maxBytes) {
            throw IllegalStateException("This file is larger than your workspace's upload limit")
        }
        if (size in 0..singlePartLimit) {
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Couldn't read the file")
            uploadBytes(bytes, name, type).also { onProgress(1f) }
        } else {
            val parts = if (size > 0) ((size + partSize - 1) / partSize).toInt() else 1
            val upload = api.createFileUpload(name, type, parts = parts)
            resolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(partSize)
                var part = 1
                while (true) {
                    var read = 0
                    while (read < partSize) {
                        val n = input.read(buffer, read, partSize - read)
                        if (n < 0) break
                        read += n
                    }
                    if (read == 0) break
                    api.sendFileUpload(upload.id, name, type, buffer.copyOf(read), partNumber = part)
                    onProgress(part.toFloat() / parts)
                    part++
                    if (read < partSize) break
                }
            } ?: error("Couldn't read the file")
            api.completeFileUpload(upload.id)
            UploadedFile(upload.id, name, type, size)
        }
    }

    suspend fun uploadBytes(bytes: ByteArray, filename: String, contentType: String?): UploadedFile {
        val upload = api.createFileUpload(filename, contentType)
        api.sendFileUpload(upload.id, filename, contentType, bytes)
        return UploadedFile(upload.id, filename, contentType, bytes.size.toLong())
    }

    suspend fun importUrl(url: String, filename: String): UploadedFile {
        val upload = api.createFileUpload(filename, null, externalUrl = url)
        return UploadedFile(upload.id, filename, upload.contentType, 0)
    }
}
