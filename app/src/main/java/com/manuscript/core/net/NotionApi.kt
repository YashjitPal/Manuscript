package com.manuscript.core.net

import com.manuscript.core.json.Parse
import com.manuscript.core.json.Skip
import com.manuscript.core.json.a
import com.manuscript.core.json.b
import com.manuscript.core.json.o
import com.manuscript.core.json.obj
import com.manuscript.core.json.objs
import com.manuscript.core.json.s
import com.manuscript.core.model.NBlock
import com.manuscript.core.model.NComment
import com.manuscript.core.model.NCustomEmoji
import com.manuscript.core.model.NDataSource
import com.manuscript.core.model.NDatabase
import com.manuscript.core.model.NFileUpload
import com.manuscript.core.model.NPage
import com.manuscript.core.model.NTemplate
import com.manuscript.core.model.NUser
import com.manuscript.core.model.NView
import com.manuscript.core.model.Paged
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** A search hit: either a page or a data source. */
sealed interface SearchResult {
    data class PageHit(val page: NPage) : SearchResult
    data class DataSourceHit(val dataSource: NDataSource) : SearchResult
}

class NotionApi(
    private val http: NotionHttp,
    /** Receives every page, database and data source object returned by the API. */
    private val sink: (JsonObject) -> Unit = {},
) {
    private fun JsonObject.sunk(): JsonObject = also(sink)

    // Users

    suspend fun me(): NUser = Parse.user(http.get("/v1/users/me"))

    suspend fun user(id: String): NUser = Parse.user(http.get("/v1/users/$id"))

    suspend fun users(cursor: String? = null, pageSize: Int = 100): Paged<NUser> =
        http.get("/v1/users", mapOf("start_cursor" to cursor, "page_size" to pageSize.toString()))
            .paged { Parse.user(it) }

    // Search

    suspend fun search(
        query: String = "",
        objectType: String? = null,
        newestFirst: Boolean? = true,
        inTrash: Boolean = false,
        cursor: String? = null,
        pageSize: Int = 50,
    ): Paged<SearchResult> {
        val filter: Any = when {
            objectType != null -> obj("property" to "object", "value" to objectType, "in_trash" to (if (inTrash) true else Skip))
            inTrash -> obj("in_trash" to true)
            else -> Skip
        }
        val body = obj(
            "query" to query.ifBlank { Skip },
            "filter" to filter,
            "sort" to when (newestFirst) {
                null -> Skip
                else -> obj("timestamp" to "last_edited_time", "direction" to if (newestFirst) "descending" else "ascending")
            },
            "start_cursor" to (cursor ?: Skip),
            "page_size" to pageSize,
        )
        return http.post("/v1/search", body).paged { o ->
            sink(o)
            when (o.s("object")) {
                "page" -> SearchResult.PageHit(Parse.page(o))
                "data_source", "database" -> SearchResult.DataSourceHit(Parse.dataSource(o))
                else -> null
            }
        }
    }

    // Pages

    suspend fun page(id: String): NPage = Parse.page(http.get("/v1/pages/$id").sunk())

    suspend fun pagePropertyItems(pageId: String, propertyId: String, cursor: String? = null): Paged<JsonObject> {
        val rawId = runCatching { java.net.URLDecoder.decode(propertyId, "UTF-8") }.getOrDefault(propertyId)
        val encoded = java.net.URLEncoder.encode(rawId, "UTF-8")
        return http.get(
            "/v1/pages/$pageId/properties/$encoded",
            mapOf("start_cursor" to cursor, "page_size" to "100"),
        ).let { o ->
            if (o.s("object") == "list") Paged(o.a("results")?.objs() ?: emptyList(), o.s("next_cursor"), o.b("has_more") == true)
            else Paged(listOf(o))
        }
    }

    suspend fun createPage(body: JsonObject): NPage = Parse.page(http.post("/v1/pages", body).sunk())

    suspend fun updatePage(id: String, body: JsonObject): NPage = Parse.page(http.patch("/v1/pages/$id", body).sunk())

    suspend fun movePage(id: String, parent: JsonObject): NPage =
        Parse.page(http.post("/v1/pages/$id/move", obj("parent" to parent)).sunk())

    suspend fun pageMarkdown(id: String): String = http.get("/v1/pages/$id/markdown").s("markdown") ?: ""

    suspend fun appendMarkdown(id: String, markdown: String, atStart: Boolean = false): JsonObject =
        http.patch(
            "/v1/pages/$id/markdown",
            obj(
                "type" to "insert_content",
                "insert_content" to obj(
                    "content" to markdown,
                    "position" to obj("type" to if (atStart) "start" else "end"),
                ),
            ),
        )

    // Blocks

    suspend fun block(id: String): NBlock = Parse.block(http.get("/v1/blocks/$id"))

    suspend fun childrenRaw(id: String, cursor: String? = null, pageSize: Int = 100): Paged<JsonObject> =
        http.get("/v1/blocks/$id/children", mapOf("start_cursor" to cursor, "page_size" to pageSize.toString()))
            .paged { it }

    suspend fun appendChildren(parentId: String, children: List<JsonObject>, afterBlockId: String? = null, atStart: Boolean = false): List<JsonObject> {
        val position: Any = when {
            afterBlockId != null -> obj("type" to "after_block", "after_block" to obj("id" to afterBlockId))
            atStart -> obj("type" to "start")
            else -> Skip
        }
        val result = http.patch(
            "/v1/blocks/$parentId/children",
            obj("children" to JsonArray(children), "position" to position),
        )
        return result.a("results")?.objs() ?: emptyList()
    }

    suspend fun updateBlock(id: String, body: JsonObject): JsonObject = http.patch("/v1/blocks/$id", body)

    suspend fun deleteBlock(id: String): JsonObject = http.delete("/v1/blocks/$id")

    // Databases and data sources

    suspend fun database(id: String): NDatabase = Parse.database(http.get("/v1/databases/$id").sunk())

    suspend fun createDatabase(body: JsonObject): NDatabase = Parse.database(http.post("/v1/databases", body).sunk())

    suspend fun updateDatabase(id: String, body: JsonObject): NDatabase =
        Parse.database(http.patch("/v1/databases/$id", body).sunk())

    suspend fun dataSource(id: String): NDataSource = Parse.dataSource(http.get("/v1/data_sources/$id").sunk())

    suspend fun createDataSource(body: JsonObject): NDataSource = Parse.dataSource(http.post("/v1/data_sources", body).sunk())

    suspend fun updateDataSource(id: String, body: JsonObject): NDataSource =
        Parse.dataSource(http.patch("/v1/data_sources/$id", body).sunk())

    suspend fun queryDataSource(
        id: String,
        filter: JsonObject? = null,
        sorts: JsonArray? = null,
        cursor: String? = null,
        pageSize: Int = 100,
        archived: Boolean = false,
    ): Paged<NPage> {
        val body = obj(
            "filter" to (filter ?: Skip),
            "sorts" to (sorts?.takeIf { it.isNotEmpty() } ?: Skip),
            "start_cursor" to (cursor ?: Skip),
            "page_size" to pageSize,
            "is_archived" to (if (archived) true else Skip),
            "result_type" to "page",
        )
        val result = http.post("/v1/data_sources/$id/query", body)
        val paged = result.paged { o -> if (o.s("object") == "page") Parse.page(o.sunk()) else null }
        return paged.copy(incomplete = result.o("request_status")?.s("type") == "incomplete")
    }

    suspend fun templates(dataSourceId: String): List<NTemplate> {
        val out = mutableListOf<NTemplate>()
        var cursor: String? = null
        do {
            val page = http.get(
                "/v1/data_sources/$dataSourceId/templates",
                mapOf("start_cursor" to cursor, "page_size" to "100"),
            )
            out += page.a("templates")?.objs()?.map(Parse::template) ?: emptyList()
            cursor = page.s("next_cursor")
        } while (page.b("has_more") == true && cursor != null)
        return out
    }

    // Views

    suspend fun viewIds(databaseId: String? = null, dataSourceId: String? = null): List<String> {
        val out = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page = http.get(
                "/v1/views",
                mapOf("database_id" to databaseId, "data_source_id" to dataSourceId, "start_cursor" to cursor, "page_size" to "100"),
            )
            out += page.a("results")?.objs()?.mapNotNull { it.s("id") } ?: emptyList()
            cursor = page.s("next_cursor")
        } while (page.b("has_more") == true && cursor != null)
        return out
    }

    suspend fun view(id: String): NView = Parse.view(http.get("/v1/views/$id"))

    suspend fun createView(body: JsonObject): NView = Parse.view(http.post("/v1/views", body))

    suspend fun updateView(id: String, body: JsonObject): NView = Parse.view(http.patch("/v1/views/$id", body))

    suspend fun deleteView(id: String) {
        http.delete("/v1/views/$id")
    }

    // Comments

    suspend fun comments(blockId: String, cursor: String? = null): Paged<NComment> =
        http.get("/v1/comments", mapOf("block_id" to blockId, "start_cursor" to cursor, "page_size" to "100"))
            .paged { Parse.comment(it) }

    suspend fun createComment(body: JsonObject): NComment = Parse.comment(http.post("/v1/comments", body))

    suspend fun updateComment(id: String, body: JsonObject): NComment = Parse.comment(http.patch("/v1/comments/$id", body))

    suspend fun deleteComment(id: String) {
        http.delete("/v1/comments/$id")
    }

    // File uploads

    suspend fun createFileUpload(
        filename: String,
        contentType: String?,
        parts: Int? = null,
        externalUrl: String? = null,
    ): NFileUpload {
        val mode = when {
            externalUrl != null -> "external_url"
            parts != null && parts > 1 -> "multi_part"
            else -> "single_part"
        }
        val body = obj(
            "mode" to mode,
            "filename" to filename,
            "content_type" to (contentType ?: Skip),
            "number_of_parts" to (if (mode == "multi_part") parts else Skip),
            "external_url" to (externalUrl ?: Skip),
        )
        return Parse.fileUpload(http.post("/v1/file_uploads", body))
    }

    suspend fun sendFileUpload(
        id: String,
        filename: String,
        contentType: String?,
        bytes: ByteArray,
        partNumber: Int? = null,
    ): NFileUpload {
        val fileBody: RequestBody = bytes.toRequestBody(contentType?.toMediaTypeOrNull())
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", filename, fileBody)
            .apply { if (partNumber != null) addFormDataPart("part_number", partNumber.toString()) }
            .build()
        return Parse.fileUpload(http.send("/v1/file_uploads/$id/send", multipart))
    }

    suspend fun completeFileUpload(id: String): NFileUpload =
        Parse.fileUpload(http.post("/v1/file_uploads/$id/complete"))

    suspend fun fileUpload(id: String): NFileUpload = Parse.fileUpload(http.get("/v1/file_uploads/$id"))

    // Custom emojis

    suspend fun customEmojis(cursor: String? = null): Paged<NCustomEmoji> =
        http.get("/v1/custom_emojis", mapOf("start_cursor" to cursor, "page_size" to "100"))
            .paged { Parse.customEmoji(it) }

    private inline fun <T> JsonObject.paged(map: (JsonObject) -> T?): Paged<T> = Paged(
        items = a("results")?.objs()?.mapNotNull(map) ?: emptyList(),
        nextCursor = s("next_cursor"),
        hasMore = b("has_more") == true,
    )
}

/** Raw POST helper used by OAuth (Basic auth, no bearer token). */
fun JsonElement.toJsonBody(): RequestBody = toString().toRequestBody("application/json".toMediaTypeOrNull())
