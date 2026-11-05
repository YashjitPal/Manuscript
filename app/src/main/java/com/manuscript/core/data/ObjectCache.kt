package com.manuscript.core.data

import com.manuscript.core.data.db.CacheDao
import com.manuscript.core.data.db.ObjectEntity
import com.manuscript.core.json.Parse
import com.manuscript.core.json.encode
import com.manuscript.core.json.parseObject
import com.manuscript.core.json.s
import com.manuscript.core.model.NDataSource
import com.manuscript.core.model.NDatabase
import com.manuscript.core.model.NPage
import com.manuscript.core.model.NUser
import com.manuscript.core.model.PropValue
import com.manuscript.core.model.plainText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * In-memory, observable cache of Notion objects backed by Room. Every object returned by the API
 * passes through [accept], so all screens observing an ID see the latest version.
 */
class ObjectCache(
    private val dao: CacheDao,
    private val accountId: String,
    scope: CoroutineScope,
) {
    private val _pages = MutableStateFlow<Map<String, NPage>>(emptyMap())
    private val _dataSources = MutableStateFlow<Map<String, NDataSource>>(emptyMap())
    private val _databases = MutableStateFlow<Map<String, NDatabase>>(emptyMap())
    private val _users = MutableStateFlow<Map<String, NUser>>(emptyMap())
    private val pending = Channel<ObjectEntity>(Channel.UNLIMITED)

    val pages: StateFlow<Map<String, NPage>> = _pages.asStateFlow()
    val dataSources: StateFlow<Map<String, NDataSource>> = _dataSources.asStateFlow()
    val databases: StateFlow<Map<String, NDatabase>> = _databases.asStateFlow()
    val users: StateFlow<Map<String, NUser>> = _users.asStateFlow()

    init {
        scope.launch {
            val batch = mutableListOf<ObjectEntity>()
            while (true) {
                batch += pending.receive()
                delay(250)
                while (true) batch += pending.tryReceive().getOrNull() ?: break
                runCatching { dao.putObjects(batch.associateBy { it.id }.values.toList()) }
                batch.clear()
            }
        }
    }

    /** Receives a raw API object of any kind, updates memory and schedules persistence. */
    fun accept(raw: JsonObject) {
        when (raw.s("object")) {
            "page" -> {
                val page = Parse.page(raw)
                putPage(page)
                persist(page.id, "page", raw, page.title, page.parent.parentId, page.lastEditedTime)
            }
            "data_source" -> {
                val ds = Parse.dataSource(raw)
                _dataSources.update { it + (ds.id to ds) }
                persist(ds.id, "data_source", raw, ds.titleText, ds.databaseId, ds.lastEditedTime)
            }
            "database" -> {
                val db = Parse.database(raw)
                _databases.update { it + (db.id to db) }
                persist(db.id, "database", raw, db.titleText, db.parent.parentId, db.lastEditedTime)
            }
        }
    }

    fun putPage(page: NPage) {
        _pages.update { it + (page.id to page) }
        val people = page.properties.values.flatMap { v ->
            when (v) {
                is PropValue.People -> v.users
                is PropValue.CreatedBy -> listOfNotNull(v.user)
                is PropValue.LastEditedBy -> listOfNotNull(v.user)
                else -> emptyList()
            }
        }
        if (people.isNotEmpty()) putUsers(people)
    }
    fun putDataSource(ds: NDataSource) = _dataSources.update { it + (ds.id to ds) }
    fun putDatabase(db: NDatabase) = _databases.update { it + (db.id to db) }
    fun putUsers(users: Collection<NUser>) = _users.update { map -> map + users.filter { it.name != null || it.avatarUrl != null }.associateBy { it.id } }

    fun removePage(id: String) = _pages.update { it - id }

    fun page(id: String): NPage? = _pages.value[id]
    fun dataSource(id: String): NDataSource? = _dataSources.value[id]
    fun database(id: String): NDatabase? = _databases.value[id]
    fun user(id: String?): NUser? = id?.let { _users.value[it] }

    fun pageFlow(id: String): Flow<NPage?> = _pages.map { it[id] }.distinctUntilChanged()
    fun dataSourceFlow(id: String): Flow<NDataSource?> = _dataSources.map { it[id] }.distinctUntilChanged()
    fun databaseFlow(id: String): Flow<NDatabase?> = _databases.map { it[id] }.distinctUntilChanged()

    suspend fun storedPage(id: String): NPage? = page(id) ?: dao.getObject(accountId, id)
        ?.takeIf { it.kind == "page" }?.let { runCatching { Parse.page(parseObject(it.json)) }.getOrNull() }
        ?.also(::putPage)

    suspend fun storedDataSource(id: String): NDataSource? = dataSource(id) ?: dao.getObject(accountId, id)
        ?.takeIf { it.kind == "data_source" }?.let { runCatching { Parse.dataSource(parseObject(it.json)) }.getOrNull() }
        ?.also(::putDataSource)

    suspend fun storedDatabase(id: String): NDatabase? = database(id) ?: dao.getObject(accountId, id)
        ?.takeIf { it.kind == "database" }?.let { runCatching { Parse.database(parseObject(it.json)) }.getOrNull() }
        ?.also(::putDatabase)

    suspend fun storedPages(limit: Int = 300): List<NPage> =
        dao.objectsOfKind(accountId, "page", limit).mapNotNull { runCatching { Parse.page(parseObject(it.json)) }.getOrNull() }
            .onEach(::putPage)

    suspend fun storedDataSources(limit: Int = 200): List<NDataSource> =
        dao.objectsOfKind(accountId, "data_source", limit).mapNotNull { runCatching { Parse.dataSource(parseObject(it.json)) }.getOrNull() }
            .onEach(::putDataSource)

    suspend fun offlineSearch(query: String): List<ObjectEntity> = dao.searchTitles(accountId, query)

    private fun persist(id: String, kind: String, raw: JsonObject, title: String, parentId: String?, lastEdited: String?) {
        pending.trySend(
            ObjectEntity(accountId, id, kind, raw.encode(), title, parentId, lastEdited, System.currentTimeMillis()),
        )
    }

    fun displayTitle(id: String): String? =
        page(id)?.title ?: dataSource(id)?.titleText ?: database(id)?.title?.plainText()
}
