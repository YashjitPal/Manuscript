package com.manuscript.ui.screens.database

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manuscript.core.data.ChangeEvent
import com.manuscript.core.data.CreateParent
import com.manuscript.core.data.Session
import com.manuscript.core.json.FilterJson
import com.manuscript.core.json.Write
import com.manuscript.core.json.obj
import com.manuscript.core.model.FilterNode
import com.manuscript.core.model.GroupBy
import com.manuscript.core.model.NDataSource
import com.manuscript.core.model.NDatabase
import com.manuscript.core.model.NIcon
import com.manuscript.core.model.NPage
import com.manuscript.core.model.NTemplate
import com.manuscript.core.model.NView
import com.manuscript.core.model.PropType
import com.manuscript.core.model.PropValue
import com.manuscript.core.model.PropertySchema
import com.manuscript.core.model.SelectOption
import com.manuscript.core.model.SortSpec
import com.manuscript.core.model.ViewConfig
import com.manuscript.core.model.ViewProp
import com.manuscript.core.model.ViewType
import com.manuscript.core.model.toRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class DbUi(
    val database: NDatabase? = null,
    val sources: List<NDataSource> = emptyList(),
    val source: NDataSource? = null,
    val views: List<NView> = emptyList(),
    val view: NView? = null,
    val rows: List<NPage> = emptyList(),
    val loading: Boolean = true,
    val loadingRows: Boolean = false,
    val loadingMore: Boolean = false,
    val cursor: String? = null,
    val incomplete: Boolean = false,
    val error: Throwable? = null,
    val search: String = "",
    val localFilter: FilterNode? = null,
    val filterOverridden: Boolean = false,
    val localSorts: List<SortSpec>? = null,
    val localGroup: GroupBy? = null,
    val groupOverridden: Boolean = false,
    val localProps: List<ViewProp>? = null,
    val activeQuick: Set<String> = emptySet(),
    val templates: List<NTemplate> = emptyList(),
) {
    val effectiveFilter: FilterNode? get() = if (filterOverridden) localFilter else FilterJson.parse(view?.filter)
    val effectiveSorts: List<SortSpec> get() = localSorts ?: view?.sorts ?: emptyList()
    val effectiveGroup: GroupBy? get() = if (groupOverridden) localGroup else view?.config?.groupBy
    val hasLocalChanges: Boolean get() = filterOverridden || localSorts != null || groupOverridden || localProps != null
    val type: ViewType get() = view?.type ?: ViewType.TABLE
    val config: ViewConfig get() = view?.config ?: ViewConfig()

    /** Visible properties in order for the active view. */
    fun visibleProps(): List<PropertySchema> {
        val ds = source ?: return emptyList()
        val configured = localProps ?: config.properties
        if (configured.isEmpty()) {
            return (listOfNotNull(ds.titleProperty) + ds.properties.filter { it.type != PropType.TITLE })
        }
        val ordered = configured.filter { it.visible }.mapNotNull { ds.prop(it.propertyId) }
        val title = ds.titleProperty
        return if (title != null && ordered.none { it.id == title.id } && type == ViewType.TABLE) listOf(title) + ordered else ordered
    }

    fun widthOf(prop: PropertySchema): Int? = (localProps ?: config.properties).firstOrNull { it.propertyId == prop.id || it.propertyId == prop.name }?.width
}

sealed interface DbEvent {
    data class Opened(val page: NPage) : DbEvent
    data class Message(val text: String) : DbEvent
}

class DatabaseViewModel(
    private val session: Session,
    private val databaseId: String,
    private val dataSourceHint: String?,
    private val viewHint: String?,
) : ViewModel() {
    private val _state = MutableStateFlow(DbUi())
    val state: StateFlow<DbUi> = _state.asStateFlow()
    private val _events = MutableSharedFlow<DbEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<DbEvent> = _events
    val users = session.cache.users
    private var queryJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            session.events.collect { e ->
                val ds = _state.value.source?.id
                when {
                    e is ChangeEvent.RowsChanged && e.dataSourceId == ds -> query(silent = true)
                    e is ChangeEvent.SchemaChanged && e.dataSourceId == ds -> refreshSchema()
                    e is ChangeEvent.PageTrashed -> _state.update { s -> s.copy(rows = s.rows.filterNot { it.id == e.id }) }
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val cachedDb = session.cache.storedDatabase(databaseId)
                if (cachedDb != null) _state.update { it.copy(database = cachedDb) }
                val prefetchedViews = async { session.databases.views(databaseId) }
                val (db, sources) = session.databases.resolve(databaseId, dataSourceHint)
                val source = sources.firstOrNull { it.id == dataSourceHint } ?: sources.firstOrNull()
                val fetchedViews = if (db.id == databaseId) prefetchedViews.await() else {
                    prefetchedViews.cancel()
                    session.databases.views(db.id)
                }
                val allViews = fetchedViews.filter { it.dashboardViewId == null }
                val views = allViews.filter { it.dataSourceId == null || it.dataSourceId == source?.id }.ifEmpty { allViews }
                    .ifEmpty { listOfNotNull(source?.let { syntheticView(db, it) }) }
                val view = views.firstOrNull { it.id == viewHint } ?: views.firstOrNull()
                _state.update { it.copy(database = db, sources = sources, source = source, views = views, view = view, loading = false) }
                session.workspace.recordVisit(db.toRef().copy(kind = com.manuscript.core.model.NodeKind.DATABASE))
                query()
                source?.let { s -> launch { _state.update { it.copy(templates = session.databases.templates(s.id)) } } }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                _state.update { it.copy(loading = false, error = e) }
            }
        }
    }

    private fun syntheticView(db: NDatabase, ds: NDataSource) = NView(
        id = "local-${ds.id}", databaseId = db.id, dataSourceId = ds.id, name = "Table", type = ViewType.TABLE,
    )

    private suspend fun refreshSchema() {
        val id = _state.value.source?.id ?: return
        runCatching { session.databases.dataSource(id) }.onSuccess { ds ->
            _state.update { s -> s.copy(source = ds, sources = s.sources.map { if (it.id == ds.id) ds else it }) }
        }
    }

    private fun searchFilter(s: DbUi): JsonObject? {
        val title = s.source?.titleProperty ?: return null
        if (s.search.isBlank()) return null
        return obj("property" to title.id, "title" to obj("contains" to s.search.trim()))
    }

    private fun quickFilters(s: DbUi): List<JsonObject> {
        val quick = s.view?.quickFilters ?: return emptyList()
        return s.activeQuick.mapNotNull { key ->
            val condition = quick[key] as? JsonObject ?: return@mapNotNull null
            if (condition.isEmpty()) return@mapNotNull null
            val prop = s.source?.prop(key) ?: return@mapNotNull null
            JsonObject(mapOf("property" to JsonPrimitive(prop.id)) + condition)
        }
    }

    fun combinedFilter(s: DbUi = _state.value): JsonObject? {
        val parts = listOfNotNull(Write.filter(s.effectiveFilter), searchFilter(s)) + quickFilters(s)
        return when (parts.size) {
            0 -> null
            1 -> parts.first()
            else -> obj("and" to JsonArray(parts))
        }
    }

    fun query(silent: Boolean = false) {
        val s = _state.value
        val ds = s.source ?: return
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            if (!silent) _state.update { it.copy(loadingRows = true) }
            runCatching {
                session.databases.query(ds.id, combinedFilter(s), Write.sorts(s.effectiveSorts).takeIf { s.effectiveSorts.isNotEmpty() }, pageSize = 100)
            }.onSuccess { page ->
                val people = page.items.flatMap { p -> p.properties.values.flatMap { v -> (v as? PropValue.People)?.users?.map { it.id } ?: emptyList() } }
                session.users.ensure(people + page.items.mapNotNull { it.lastEditedBy })
                _state.update { it.copy(rows = page.items, cursor = page.nextCursor.takeIf { page.hasMore }, incomplete = page.incomplete, loadingRows = false, error = null) }
                resolveRelations(page.items)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _state.update { it.copy(loadingRows = false, error = if (it.rows.isEmpty()) e else null) }
                _events.emit(DbEvent.Message("Couldn't load rows: ${e.message}"))
            }
        }
    }

    private fun resolveRelations(rows: List<NPage>) {
        val missing = rows.flatMap { p -> p.properties.values.flatMap { v -> (v as? PropValue.Relation)?.ids ?: emptyList() } }
            .distinct().filter { session.cache.page(it) == null }.take(40)
        if (missing.isEmpty()) return
        viewModelScope.launch {
            missing.chunked(4).forEach { chunk -> chunk.map { id -> launch { runCatching { session.pages.page(id) } } }.forEach { it.join() } }
            _state.update { it.copy(rows = it.rows.toList()) }
        }
    }

    fun loadMore() {
        val s = _state.value
        val cursor = s.cursor ?: return
        val ds = s.source ?: return
        if (s.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            runCatching { session.databases.query(ds.id, combinedFilter(s), Write.sorts(s.effectiveSorts).takeIf { s.effectiveSorts.isNotEmpty() }, cursor) }
                .onSuccess { page ->
                    val people = page.items.flatMap { p -> p.properties.values.flatMap { v -> (v as? PropValue.People)?.users?.map { it.id } ?: emptyList() } }
                    session.users.ensure(people + page.items.mapNotNull { it.lastEditedBy })
                    _state.update { it.copy(rows = (it.rows + page.items).distinctBy { r -> r.id }, cursor = page.nextCursor.takeIf { page.hasMore }, loadingMore = false) }
                    resolveRelations(page.items)
                }
                .onFailure { _state.update { it.copy(loadingMore = false) } }
        }
    }

    fun selectView(view: NView) {
        _state.update { it.copy(view = view, localFilter = null, filterOverridden = false, localSorts = null, localGroup = null, groupOverridden = false, localProps = null, activeQuick = emptySet()) }
        if (view.dataSourceId != null && view.dataSourceId != _state.value.source?.id) {
            _state.value.sources.firstOrNull { it.id == view.dataSourceId }?.let { ds -> _state.update { it.copy(source = ds) } }
        }
        query()
    }

    fun selectSource(source: NDataSource) {
        _state.update { s ->
            val views = s.views.filter { it.dataSourceId == source.id }
            s.copy(
                source = source,
                view = views.firstOrNull() ?: s.database?.let { syntheticView(it, source) },
                localFilter = null,
                filterOverridden = false,
                localSorts = null,
                localGroup = null,
                groupOverridden = false,
                localProps = null,
                activeQuick = emptySet(),
            )
        }
        query()
        viewModelScope.launch { _state.update { it.copy(templates = session.databases.templates(source.id)) } }
    }

    private var searchJob: Job? = null
    fun search(text: String) {
        _state.update { it.copy(search = text) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { delay(300); query() }
    }

    fun setFilter(node: FilterNode?) {
        _state.update { it.copy(localFilter = node, filterOverridden = true) }
        query()
    }

    fun setSorts(sorts: List<SortSpec>) {
        _state.update { it.copy(localSorts = sorts) }
        query()
    }

    fun setGroup(group: GroupBy?) = _state.update { it.copy(localGroup = group, groupOverridden = true) }

    fun setProps(props: List<ViewProp>) = _state.update { it.copy(localProps = props) }

    fun toggleQuick(key: String) {
        _state.update { s -> s.copy(activeQuick = if (key in s.activeQuick) s.activeQuick - key else s.activeQuick + key) }
        query()
    }

    fun resetLocal() {
        _state.update { it.copy(localFilter = null, filterOverridden = false, localSorts = null, localGroup = null, groupOverridden = false, localProps = null) }
        query()
    }

    /** Saves the temporary filter, sorts, grouping and visible properties into the Notion view. */
    fun saveToView() {
        val s = _state.value
        val view = s.view ?: return
        if (view.id.startsWith("local-")) return
        viewModelScope.launch {
            runCatching {
                val raw = view.config.raw
                var config: JsonObject? = null
                if (s.groupOverridden || s.localProps != null) {
                    val base = (raw ?: obj("type" to view.type.api)).toMutableMap()
                    base["type"] = JsonPrimitive(view.type.api)
                    if (s.localProps != null) base["properties"] = JsonArray(s.localProps.map {
                        obj("property_id" to it.propertyId, "visible" to it.visible, "width" to (it.width ?: com.manuscript.core.json.Skip))
                    })
                    if (s.groupOverridden) base["group_by"] = s.localGroup?.let(::groupJson) ?: kotlinx.serialization.json.JsonNull
                    config = JsonObject(base)
                }
                session.databases.updateView(
                    view.id,
                    filter = if (s.filterOverridden) Write.filter(s.localFilter) else null,
                    clearFilter = s.filterOverridden && s.localFilter == null,
                    sorts = s.localSorts?.let { Write.sorts(it) },
                    configuration = config,
                )
            }.onSuccess { updated ->
                _state.update { st -> st.copy(view = updated, views = st.views.map { if (it.id == updated.id) updated else it }) }
                resetLocal()
                _events.emit(DbEvent.Message("View saved"))
            }.onFailure { _events.emit(DbEvent.Message("Couldn't save view: ${it.message}")) }
        }
    }

    private fun groupJson(g: GroupBy): JsonObject = obj(
        "type" to g.type,
        "property_id" to g.propertyId,
        "group_by" to (g.groupBy ?: com.manuscript.core.json.Skip),
        "sort" to obj("type" to g.sort),
        "hide_empty_groups" to g.hideEmpty,
    )

    fun createView(name: String, type: ViewType, configure: (NDataSource) -> JsonObject?) {
        val s = _state.value
        val db = s.database ?: return
        val ds = s.source ?: return
        viewModelScope.launch {
            runCatching { session.databases.createView(db.id, ds.id, name.ifBlank { type.label }, type, configure(ds)) }
                .onSuccess { v -> _state.update { it.copy(views = it.views + v) }; selectView(v) }
                .onFailure { _events.emit(DbEvent.Message("Couldn't create view: ${it.message}")) }
        }
    }

    fun renameView(view: NView, name: String) {
        viewModelScope.launch {
            runCatching { session.databases.updateView(view.id, name = name) }
                .onSuccess { v -> _state.update { s -> s.copy(views = s.views.map { if (it.id == v.id) v else it }, view = if (s.view?.id == v.id) v else s.view) } }
                .onFailure { _events.emit(DbEvent.Message("Couldn't rename view: ${it.message}")) }
        }
    }

    fun deleteView(view: NView) {
        viewModelScope.launch {
            runCatching { session.databases.deleteView(view.id) }
                .onSuccess {
                    _state.update { s -> s.copy(views = s.views.filterNot { it.id == view.id }) }
                    _state.value.views.firstOrNull()?.let(::selectView)
                }
                .onFailure { _events.emit(DbEvent.Message("Couldn't delete view: ${it.message}")) }
        }
    }

    /** Default values so a new row matches the active filter and the board column it was added to. */
    private fun defaultsFor(extra: Map<String, PropValue>): Map<String, PropValue> {
        val s = _state.value
        val ds = s.source ?: return extra
        val out = LinkedHashMap<String, PropValue>()
        fun apply(node: FilterNode?) {
            when (node) {
                is FilterNode.Group -> if (node.and) node.children.forEach(::apply)
                is FilterNode.Cond -> {
                    val prop = ds.prop(node.property) ?: return
                    val value = (node.value as? JsonPrimitive)?.content
                    when {
                        node.op == "equals" && prop.type == PropType.SELECT && value != null -> out[prop.name] = PropValue.Select(prop.id, SelectOption(name = value))
                        node.op == "equals" && prop.type == PropType.STATUS && value != null -> out[prop.name] = PropValue.Status(prop.id, SelectOption(name = value))
                        node.op == "contains" && prop.type == PropType.MULTI_SELECT && value != null -> out[prop.name] = PropValue.MultiSelect(prop.id, listOf(SelectOption(name = value)))
                        node.op == "equals" && prop.type == PropType.CHECKBOX -> out[prop.name] = PropValue.Checkbox(prop.id, (node.value as? JsonPrimitive)?.content == "true")
                        node.op == "contains" && prop.type == PropType.PEOPLE && value != null -> {
                            val id = if (value == "me") session.account.userId else value
                            if (id != null) out[prop.name] = PropValue.People(prop.id, listOf(com.manuscript.core.model.NUser(id)))
                        }
                    }
                }
                else -> Unit
            }
        }
        apply(s.effectiveFilter)
        out.putAll(extra)
        return out
    }

    fun createRow(title: String = "", extra: Map<String, PropValue> = emptyMap(), template: NTemplate? = null, open: Boolean = true) {
        val s = _state.value
        val ds = s.source ?: return
        viewModelScope.launch {
            runCatching {
                val useDefault = template == null && s.templates.any { it.isDefault }
                session.pages.createPage(
                    CreateParent.DataSource(ds.id),
                    title,
                    properties = defaultsFor(extra),
                    titleKey = ds.titleProperty?.name ?: "Name",
                    templateId = template?.id,
                    useDefaultTemplate = useDefault,
                )
            }.onSuccess { page ->
                _state.update { it.copy(rows = it.rows + page) }
                if (open) _events.emit(DbEvent.Opened(page))
            }.onFailure { _events.emit(DbEvent.Message("Couldn't create entry: ${it.message}")) }
        }
    }

    fun updateRow(page: NPage, name: String, value: PropValue) {
        _state.update { s -> s.copy(rows = s.rows.map { if (it.id == page.id) it.copy(properties = it.properties + (name to value)) else it }) }
        viewModelScope.launch {
            runCatching { session.databases.updateRow(page.id, mapOf(name to value)) }
                .onSuccess { updated -> _state.update { s -> s.copy(rows = s.rows.map { if (it.id == updated.id) updated else it }) } }
                .onFailure {
                    _events.emit(DbEvent.Message("Couldn't update: ${it.message}"))
                    query(silent = true)
                }
        }
    }

    fun trashRow(page: NPage) {
        _state.update { s -> s.copy(rows = s.rows.filterNot { it.id == page.id }) }
        viewModelScope.launch {
            runCatching { session.pages.trash(page.id) }.onFailure { _events.emit(DbEvent.Message("Couldn't delete: ${it.message}")); query(true) }
        }
    }

    fun duplicateRow(page: NPage) {
        viewModelScope.launch {
            runCatching {
                val blocks = session.pages.loadBlocks(page.id)
                session.pages.duplicate(page, blocks)
            }.onSuccess { copy -> _state.update { it.copy(rows = it.rows + copy) } }
                .onFailure { _events.emit(DbEvent.Message("Couldn't duplicate: ${it.message}")) }
        }
    }

    // Schema

    fun addProperty(schema: PropertySchema) = schemaOp { session.databases.addProperty(it, schema) }
    fun updateProperty(original: PropertySchema, updated: PropertySchema) = schemaOp { session.databases.updateProperty(it, original, updated) }
    fun deleteProperty(schema: PropertySchema) = schemaOp { session.databases.deleteProperty(it, schema) }

    private fun schemaOp(block: suspend (String) -> NDataSource) {
        val ds = _state.value.source ?: return
        viewModelScope.launch {
            runCatching { block(ds.id) }
                .onSuccess { updated ->
                    _state.update { s -> s.copy(source = updated, sources = s.sources.map { if (it.id == updated.id) updated else it }) }
                    query(silent = true)
                }
                .onFailure { _events.emit(DbEvent.Message("Couldn't update schema: ${it.message}")) }
        }
    }

    fun renameDatabase(title: String) {
        val db = _state.value.database ?: return
        _state.update { it.copy(database = db.copy(title = listOf(com.manuscript.core.model.RichText(title)))) }
        viewModelScope.launch { runCatching { session.databases.updateDatabase(db.id, title = title) } }
    }

    fun setDescription(text: String) {
        val db = _state.value.database ?: return
        viewModelScope.launch {
            runCatching { session.databases.updateDatabase(db.id, description = listOf(com.manuscript.core.model.RichText(text))) }
                .onSuccess { d -> _state.update { it.copy(database = d) } }
        }
    }

    fun setIcon(icon: NIcon?) {
        val db = _state.value.database ?: return
        _state.update { it.copy(database = db.copy(icon = icon)) }
        viewModelScope.launch { runCatching { session.databases.updateDatabase(db.id, icon = icon, clearIcon = icon == null) } }
    }

    fun setLocked(locked: Boolean) {
        val db = _state.value.database ?: return
        _state.update { it.copy(database = db.copy(isLocked = locked)) }
        viewModelScope.launch { runCatching { session.databases.updateDatabase(db.id, locked = locked) } }
    }

    fun setInline(inline: Boolean) {
        val db = _state.value.database ?: return
        _state.update { it.copy(database = db.copy(isInline = inline)) }
        viewModelScope.launch { runCatching { session.databases.updateDatabase(db.id, inline = inline) } }
    }

    fun addSource(title: String) {
        val db = _state.value.database ?: return
        viewModelScope.launch {
            runCatching { session.databases.addDataSource(db.id, title) }
                .onSuccess { ds -> _state.update { it.copy(sources = it.sources + ds) }; selectSource(ds) }
                .onFailure { _events.emit(DbEvent.Message("Couldn't add data source: ${it.message}")) }
        }
    }

    fun trashDatabase(onDone: () -> Unit) {
        val db = _state.value.database ?: return
        viewModelScope.launch {
            runCatching { session.databases.updateDatabase(db.id, trash = true) }
                .onSuccess { onDone() }
                .onFailure { _events.emit(DbEvent.Message("Couldn't delete: ${it.message}")) }
        }
    }
}
