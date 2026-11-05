package com.manuscript.core.demo

import com.manuscript.core.json.NotionJson
import com.manuscript.core.json.Parse
import com.manuscript.core.json.a
import com.manuscript.core.json.b
import com.manuscript.core.json.i
import com.manuscript.core.json.o
import com.manuscript.core.json.obj
import com.manuscript.core.json.objs
import com.manuscript.core.json.s
import com.manuscript.core.model.NBlock
import com.manuscript.core.util.MarkdownExport
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.File
import kotlin.random.Random

private class DemoError(val status: Int, val code: String, message: String) : Exception(message)

/** Serves the Notion REST API from [DemoStore] so the demo account works fully offline. */
class DemoInterceptor(private val store: DemoStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        Thread.sleep(Random.nextLong(40, 140))
        val (code, body) = try {
            200 to store.sync { route(request) }
        } catch (e: DemoError) {
            e.status to obj("object" to "error", "status" to e.status, "code" to e.code, "message" to (e.message ?: ""))
        } catch (e: Exception) {
            400 to obj("object" to "error", "status" to 400, "code" to "validation_error", "message" to (e.message ?: "Invalid request"))
        }
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Error")
            .body(body.toString().toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun notFound(id: String?): Nothing = throw DemoError(404, "object_not_found", "Could not find object with ID: $id")

    private fun DemoStore.route(request: Request): JsonObject {
        val seg = request.url.pathSegments.drop(1)
        val method = request.method
        val q = { name: String -> request.url.queryParameter(name) }
        val body: JsonObject by lazy { readJson(request) }
        return when {
            seg == listOf("users", "me") -> users[DemoStore.ME]!!
            seg == listOf("users") -> list(users.values.toList())
            seg.size == 2 && seg[0] == "users" -> users[seg[1]] ?: notFound(seg[1])
            seg == listOf("search") -> search(body)
            seg == listOf("pages") && method == "POST" -> createPage(body)
            seg.size == 2 && seg[0] == "pages" && method == "GET" -> pages[seg[1]] ?: notFound(seg[1])
            seg.size == 2 && seg[0] == "pages" && method == "PATCH" -> updatePage(seg[1], body)
            seg.size == 3 && seg[0] == "pages" && seg[2] == "move" -> movePage(seg[1], body)
            seg.size == 3 && seg[0] == "pages" && seg[2] == "markdown" && method == "GET" ->
                obj("object" to "page_markdown", "id" to seg[1], "markdown" to markdownOf(seg[1]))
            seg.size == 3 && seg[0] == "pages" && seg[2] == "markdown" -> appendMarkdown(seg[1], body)
            seg.size == 4 && seg[0] == "pages" && seg[2] == "properties" -> pageProperty(seg[1], seg[3])
            seg.size == 2 && seg[0] == "blocks" && method == "GET" -> blockWithTitle(seg[1])
            seg.size == 2 && seg[0] == "blocks" && method == "PATCH" -> updateBlock(seg[1], body)
            seg.size == 2 && seg[0] == "blocks" && method == "DELETE" -> deleteBlock(seg[1])
            seg.size == 3 && seg[0] == "blocks" && seg[2] == "children" && method == "GET" ->
                pageOf(children[seg[1]].orEmpty().mapNotNull { blocks[it] }.filter { it.b("in_trash") != true }.map { withLiveTitle(it) }, q("start_cursor"), q("page_size"))
            seg.size == 3 && seg[0] == "blocks" && seg[2] == "children" -> appendChildren(seg[1], body)
            seg == listOf("databases") -> createDatabase(body)
            seg.size == 2 && seg[0] == "databases" && method == "GET" -> databases[seg[1]] ?: notFound(seg[1])
            seg.size == 2 && seg[0] == "databases" -> updateDatabase(seg[1], body)
            seg == listOf("data_sources") -> createDataSource(body)
            seg.size == 2 && seg[0] == "data_sources" && method == "GET" -> dataSources[seg[1]] ?: notFound(seg[1])
            seg.size == 2 && seg[0] == "data_sources" -> updateDataSource(seg[1], body)
            seg.size == 3 && seg[0] == "data_sources" && seg[2] == "query" -> query(seg[1], body)
            seg.size == 3 && seg[0] == "data_sources" && seg[2] == "templates" -> obj(
                "templates" to JsonArray(listOf(obj("id" to DemoStore.id("template-${seg[1]}"), "name" to "Default", "is_default" to true))),
                "has_more" to false, "next_cursor" to JsonNull,
            )
            seg == listOf("views") && method == "GET" -> list(views.values.filter { v ->
                (q("database_id") == null || v.o("parent")?.s("database_id") == q("database_id")) &&
                    (q("data_source_id") == null || v.s("data_source_id") == q("data_source_id"))
            }.map { obj("object" to "view", "id" to it.s("id")) })
            seg == listOf("views") -> createView(body)
            seg.size == 2 && seg[0] == "views" && method == "GET" -> views[seg[1]] ?: notFound(seg[1])
            seg.size == 2 && seg[0] == "views" && method == "PATCH" -> updateView(seg[1], body)
            seg.size == 2 && seg[0] == "views" && method == "DELETE" -> (views.remove(seg[1]) ?: notFound(seg[1])).let { obj("object" to "view", "id" to seg[1], "deleted" to true) }
            seg == listOf("comments") && method == "GET" -> list(comments.filter {
                val p = it.o("parent")
                p?.s("page_id") == q("block_id") || p?.s("block_id") == q("block_id")
            })
            seg == listOf("comments") -> createComment(body)
            seg.size == 2 && seg[0] == "comments" && method == "GET" -> comments.firstOrNull { it.s("id") == seg[1] } ?: notFound(seg[1])
            seg.size == 2 && seg[0] == "comments" && method == "PATCH" -> updateComment(seg[1], body)
            seg.size == 2 && seg[0] == "comments" && method == "DELETE" -> {
                comments.removeAll { it.s("id") == seg[1] }
                obj("object" to "comment", "id" to seg[1], "deleted" to true)
            }
            seg == listOf("file_uploads") && method == "POST" -> createUpload(body)
            seg.size == 3 && seg[0] == "file_uploads" && seg[2] == "send" -> sendUpload(seg[1], request)
            seg.size == 3 && seg[0] == "file_uploads" && seg[2] == "complete" -> completeUpload(seg[1])
            seg.size == 2 && seg[0] == "file_uploads" -> uploads[seg[1]] ?: notFound(seg[1])
            seg == listOf("custom_emojis") -> list(emptyList())
            else -> throw DemoError(400, "invalid_request_url", "Unsupported in demo: $method /${seg.joinToString("/")}")
        }
    }

    private fun readJson(request: Request): JsonObject {
        val body = request.body ?: return JsonObject(emptyMap())
        if (body is MultipartBody) return JsonObject(emptyMap())
        val buffer = Buffer()
        body.writeTo(buffer)
        val text = buffer.readUtf8()
        return if (text.isBlank()) JsonObject(emptyMap()) else NotionJson.parseToJsonElement(text) as JsonObject
    }

    private fun list(items: List<JsonObject>) = obj("object" to "list", "results" to JsonArray(items), "next_cursor" to JsonNull, "has_more" to false)

    private fun pageOf(items: List<JsonObject>, cursor: String?, size: String?): JsonObject {
        val start = cursor?.toIntOrNull() ?: 0
        val count = size?.toIntOrNull()?.coerceIn(1, 100) ?: 100
        val slice = items.drop(start).take(count)
        val more = start + count < items.size
        return obj(
            "object" to "list", "results" to JsonArray(slice),
            "next_cursor" to if (more) JsonPrimitive((start + count).toString()) else JsonNull, "has_more" to more,
        )
    }

    // Rich text normalisation (requests omit plain_text and full annotations)

    private fun DemoStore.normalizeRich(el: JsonElement?): JsonArray = JsonArray((el as? JsonArray)?.objs()?.map { item ->
        val type = item.s("type") ?: if (item.containsKey("mention")) "mention" else if (item.containsKey("equation")) "equation" else "text"
        val ann = item.o("annotations") ?: JsonObject(emptyMap())
        val annotations = obj(
            "bold" to (ann.b("bold") ?: false), "italic" to (ann.b("italic") ?: false), "strikethrough" to (ann.b("strikethrough") ?: false),
            "underline" to (ann.b("underline") ?: false), "code" to (ann.b("code") ?: false), "color" to (ann.s("color") ?: "default"),
        )
        when (type) {
            "mention" -> {
                val m = item.o("mention") ?: JsonObject(emptyMap())
                val plain = when (m.s("type")) {
                    "page" -> m.o("page")?.s("id")?.let { pages[it]?.let(::titleOf) } ?: "Untitled"
                    "database" -> m.o("database")?.s("id")?.let { databases[it]?.a("title")?.objs()?.joinToString("") { t -> t.s("plain_text") ?: "" } } ?: "Database"
                    "user" -> "@" + (m.o("user")?.s("id")?.let { users[it]?.s("name") } ?: "Someone")
                    "date" -> m.o("date")?.s("start") ?: ""
                    "template_mention" -> "@" + (m.o("template_mention")?.let { it.s("template_mention_date") ?: it.s("template_mention_user") } ?: "")
                    else -> item.s("plain_text") ?: ""
                }
                val mention = if (m.s("type") == "user") obj("type" to "user", "user" to (users[m.o("user")?.s("id")] ?: m.o("user") ?: obj())) else m
                obj("type" to "mention", "mention" to mention, "annotations" to annotations, "plain_text" to plain, "href" to JsonNull)
            }
            "equation" -> {
                val expr = item.o("equation")?.s("expression") ?: ""
                obj("type" to "equation", "equation" to obj("expression" to expr), "annotations" to annotations, "plain_text" to expr, "href" to JsonNull)
            }
            else -> {
                val text = item.o("text") ?: JsonObject(emptyMap())
                val content = text.s("content") ?: item.s("plain_text") ?: ""
                val link = text.o("link")?.s("url")
                obj(
                    "type" to "text", "text" to obj("content" to content, "link" to (link?.let { obj("url" to it) } ?: JsonNull)),
                    "annotations" to annotations, "plain_text" to content, "href" to (link ?: JsonNull),
                )
            }
        }
    } ?: emptyList())

    private fun DemoStore.resolveUploads(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> if (el.s("type") == "file_upload" && el.o("file_upload") != null) {
            val id = el.o("file_upload")?.s("id")
            val url = id?.let { uploadFiles[it] } ?: "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=1200&q=80"
            JsonObject(el.filterKeys { it != "file_upload" && it != "type" } + mapOf(
                "type" to JsonPrimitive("file"),
                "file" to obj("url" to url, "expiry_time" to java.time.Instant.now().plusSeconds(3600).toString()),
            ))
        } else JsonObject(el.mapValues { resolveUploads(it.value) })
        is JsonArray -> JsonArray(el.map { resolveUploads(it) })
        else -> el
    }

    private fun DemoStore.normalizeBody(type: String, body: JsonObject): JsonObject {
        val resolved = resolveUploads(body) as JsonObject
        val out = resolved.toMutableMap()
        out.remove("children")
        listOf("rich_text", "caption").forEach { key -> if (out.containsKey(key)) out[key] = normalizeRich(out[key]) }
        if (type == "table_row") out["cells"] = JsonArray((out["cells"] as? JsonArray)?.map { normalizeRich(it) } ?: emptyList())
        if (type in setOf("paragraph", "bulleted_list_item", "numbered_list_item", "toggle", "quote", "heading_1", "heading_2", "heading_3", "heading_4", "to_do", "callout") && !out.containsKey("color")) {
            out["color"] = JsonPrimitive("default")
        }
        if (type.startsWith("heading") && !out.containsKey("is_toggleable")) out["is_toggleable"] = JsonPrimitive(false)
        if (type == "to_do" && !out.containsKey("checked")) out["checked"] = JsonPrimitive(false)
        if (type == "code") {
            if (!out.containsKey("caption")) out["caption"] = JsonArray(emptyList())
            if (!out.containsKey("language")) out["language"] = JsonPrimitive("plain text")
        }
        if (type in setOf("image", "video", "audio", "pdf", "file", "bookmark", "embed") && !out.containsKey("caption")) out["caption"] = JsonArray(emptyList())
        if (type == "callout" && !out.containsKey("icon")) out["icon"] = obj("type" to "emoji", "emoji" to "💡")
        return JsonObject(out)
    }

    private fun DemoStore.createBlocks(parentId: String, parentIsPage: Boolean, items: List<JsonObject>, after: String?, atStart: Boolean): List<JsonObject> {
        val parentJson = if (parentIsPage) obj("type" to "page_id", "page_id" to parentId) else obj("type" to "block_id", "block_id" to parentId)
        val time = nowIso()
        val created = items.map { item ->
            val type = item.s("type") ?: item.keys.first { it != "object" }
            val rawBody = item.o(type) ?: JsonObject(emptyMap())
            val kids = (rawBody.a("children") ?: item.a("children"))?.objs() ?: emptyList()
            val id = newId()
            blocks[id] = obj(
                "object" to "block", "id" to id, "parent" to parentJson, "created_time" to time, "last_edited_time" to time,
                "created_by" to obj("object" to "user", "id" to DemoStore.ME), "last_edited_by" to obj("object" to "user", "id" to DemoStore.ME),
                "has_children" to kids.isNotEmpty(), "in_trash" to false, "type" to type, type to normalizeBody(type, rawBody),
            )
            if (kids.isNotEmpty()) createBlocks(id, false, kids, null, false)
            id
        }
        val list = children.getOrPut(parentId) { mutableListOf() }
        val index = when {
            after != null -> list.indexOf(after).let { if (it < 0) list.size else it + 1 }
            atStart -> 0
            else -> list.size
        }
        list.addAll(index, created)
        blocks[parentId]?.let { blocks[parentId] = JsonObject(it + ("has_children" to JsonPrimitive(true))) }
        touch(parentId)
        return created.map { blocks[it]!! }
    }

    private fun DemoStore.touch(id: String) {
        var current: String? = id
        var guard = 0
        while (current != null && guard++ < 20) {
            pages[current]?.let {
                pages[current!!] = JsonObject(it + mapOf("last_edited_time" to JsonPrimitive(nowIso()), "last_edited_by" to obj("object" to "user", "id" to DemoStore.ME)))
                return
            }
            current = blocks[current]?.o("parent")?.let { it.s("page_id") ?: it.s("block_id") }
        }
    }

    private fun DemoStore.appendChildren(parentId: String, body: JsonObject): JsonObject {
        if (pages[parentId] == null && blocks[parentId] == null) notFound(parentId)
        val position = body.o("position")
        val after = position?.takeIf { it.s("type") == "after_block" }?.o("after_block")?.s("id") ?: body.s("after")
        val created = createBlocks(parentId, pages.containsKey(parentId), body.a("children")?.objs() ?: emptyList(), after, position?.s("type") == "start")
        return obj("object" to "list", "results" to JsonArray(created), "next_cursor" to JsonNull, "has_more" to false)
    }

    private fun DemoStore.withLiveTitle(block: JsonObject): JsonObject = when (block.s("type")) {
        "child_page" -> pages[block.s("id")]?.let { JsonObject(block + ("child_page" to obj("title" to titleOf(it)))) } ?: block
        "child_database" -> databases[block.s("id")]?.let { db ->
            JsonObject(block + ("child_database" to obj("title" to (db.a("title")?.objs()?.joinToString("") { it.s("plain_text") ?: "" } ?: ""))))
        } ?: block
        else -> block
    }

    private fun DemoStore.blockWithTitle(id: String): JsonObject = blocks[id]?.let { withLiveTitle(it) } ?: notFound(id)

    private fun DemoStore.updateBlock(id: String, body: JsonObject): JsonObject {
        val block = blocks[id] ?: notFound(id)
        val type = block.s("type")!!
        var updated = block
        body.b("in_trash")?.let { trash ->
            updated = JsonObject(updated + ("in_trash" to JsonPrimitive(trash)))
            if (trash) children.values.forEach { it.remove(id) }
        }
        body.o(type)?.let { patch ->
            val merged = JsonObject((block.o(type) ?: JsonObject(emptyMap())) + normalizeBody(type, patch))
            updated = JsonObject(updated + mapOf(type to merged, "last_edited_time" to JsonPrimitive(nowIso())))
        }
        blocks[id] = updated
        updated.o("parent")?.let { touch(it.s("page_id") ?: it.s("block_id") ?: id) }
        return updated
    }

    private fun DemoStore.deleteBlock(id: String): JsonObject {
        val block = blocks[id] ?: pages[id]?.let { page ->
            pages[id] = JsonObject(page + ("in_trash" to JsonPrimitive(true)))
            children.values.forEach { it.remove(id) }
            return pages[id]!!
        } ?: notFound(id)
        children.values.forEach { it.remove(id) }
        val trashed = JsonObject(block + ("in_trash" to JsonPrimitive(true)))
        blocks[id] = trashed
        return trashed
    }

    // Pages

    private fun DemoStore.search(body: JsonObject): JsonObject {
        val query = body.s("query")?.lowercase()?.trim().orEmpty()
        val filter = body.o("filter")
        val type = filter?.s("value")
        val trash = filter?.b("in_trash") == true
        val ascending = body.o("sort")?.s("direction") == "ascending"
        val pageHits = if (type == null || type == "page") pages.values.filter {
            (it.b("in_trash") == true) == trash && titleOf(it).lowercase().contains(query)
        } else emptyList()
        val sourceHits = if (type == null || type == "data_source" || type == "database") dataSources.values.filter {
            (it.b("in_trash") == true) == trash && (it.a("title")?.objs()?.joinToString("") { t -> t.s("plain_text") ?: "" } ?: "").lowercase().contains(query)
        } else emptyList()
        val all = (pageHits + sourceHits).sortedBy { it.s("last_edited_time") }.let { if (ascending) it else it.reversed() }
        return pageOf(all, body.s("start_cursor"), body.i("page_size")?.toString())
    }

    private fun DemoStore.schemaFor(dataSourceId: String?): Map<String, JsonObject> =
        dataSourceId?.let { dataSources[it] }?.o("properties")?.mapValues { it.value as JsonObject } ?: emptyMap()

    private fun DemoStore.propertyValue(schema: JsonObject, value: JsonObject, dataSourceId: String?): JsonObject {
        val type = schema.s("type")!!
        val pid = schema.s("id")!!
        fun option(o: JsonObject?, optionsType: String): JsonElement {
            if (o == null) return JsonNull
            val options = schema.o(optionsType)?.a("options")?.objs() ?: emptyList()
            val found = options.firstOrNull { it.s("id") == o.s("id") || it.s("name") == o.s("name") }
            if (found != null) return obj("id" to found.s("id"), "name" to found.s("name"), "color" to found.s("color"))
            val name = o.s("name") ?: return JsonNull
            val created = obj("id" to "opt-" + name.hashCode(), "name" to name, "color" to (o.s("color") ?: "default"))
            if (dataSourceId != null) addOption(dataSourceId, schema.s("name")!!, optionsType, created)
            return created
        }
        val v: JsonElement = when (type) {
            "title", "rich_text" -> normalizeRich(value[type])
            "select", "status" -> option(value[type] as? JsonObject, type)
            "multi_select" -> JsonArray((value[type] as? JsonArray)?.objs()?.map { option(it, type) } ?: emptyList())
            "people" -> JsonArray((value[type] as? JsonArray)?.objs()?.mapNotNull { users[it.s("id")] ?: it } ?: emptyList())
            "files" -> resolveUploads(value[type] ?: JsonArray(emptyList()))
            "relation" -> value[type] ?: JsonArray(emptyList())
            else -> value[type] ?: JsonNull
        }
        return JsonObject(mapOf("id" to JsonPrimitive(pid), "type" to JsonPrimitive(type), type to v) +
            if (type == "relation") mapOf("has_more" to JsonPrimitive(false)) else emptyMap())
    }

    private fun DemoStore.addOption(dataSourceId: String, propName: String, type: String, option: JsonObject) {
        val ds = dataSources[dataSourceId] ?: return
        val props = ds.o("properties")!!.toMutableMap()
        val prop = props[propName] as? JsonObject ?: return
        val config = prop.o(type) ?: JsonObject(emptyMap())
        val options = (config.a("options")?.objs() ?: emptyList()) + option
        props[propName] = JsonObject(prop + (type to JsonObject(config + ("options" to JsonArray(options)))))
        dataSources[dataSourceId] = JsonObject(ds + ("properties" to JsonObject(props)))
    }

    private fun DemoStore.emptyValue(schema: JsonObject, rowNumber: Int): JsonObject {
        val type = schema.s("type")!!
        val v: JsonElement = when (type) {
            "title", "rich_text", "multi_select", "people", "files", "relation" -> JsonArray(emptyList())
            "checkbox" -> JsonPrimitive(false)
            "status" -> schema.o("status")?.a("options")?.objs()?.firstOrNull()?.let { obj("id" to it.s("id"), "name" to it.s("name"), "color" to it.s("color")) } ?: JsonNull
            "created_time", "last_edited_time" -> JsonPrimitive(nowIso())
            "created_by", "last_edited_by" -> users[DemoStore.ME]!!
            "unique_id" -> obj("prefix" to (schema.o("unique_id")?.s("prefix") ?: JsonNull), "number" to rowNumber)
            "formula" -> obj("type" to "number", "number" to JsonNull)
            else -> JsonNull
        }
        return JsonObject(mapOf("id" to JsonPrimitive(schema.s("id")!!), "type" to JsonPrimitive(type), type to v) +
            if (type == "relation") mapOf("has_more" to JsonPrimitive(false)) else emptyMap())
    }

    private fun DemoStore.createPage(body: JsonObject): JsonObject {
        val parent = body.o("parent") ?: throw DemoError(400, "validation_error", "parent is required")
        val id = newId()
        val time = nowIso()
        val dsId = parent.s("data_source_id") ?: parent.s("database_id")?.let { db -> databases[db]?.a("data_sources")?.objs()?.firstOrNull()?.s("id") }
        val parentJson = when {
            dsId != null -> obj("type" to "data_source_id", "data_source_id" to dsId, "database_id" to (dataSources[dsId]?.o("parent")?.s("database_id") ?: ""))
            parent.s("page_id") != null -> obj("type" to "page_id", "page_id" to parent.s("page_id"))
            else -> obj("type" to "workspace", "workspace" to true)
        }
        val requested = body.o("properties") ?: JsonObject(emptyMap())
        val properties = LinkedHashMap<String, JsonElement>()
        if (dsId != null) {
            val schema = schemaFor(dsId)
            val rowNumber = pages.values.count { it.o("parent")?.s("data_source_id") == dsId } + 101
            schema.forEach { (name, s) -> properties[name] = emptyValue(s, rowNumber) }
            requested.forEach { (key, value) ->
                val entry = schema.entries.firstOrNull { it.key == key || it.value.s("id") == key } ?: return@forEach
                if (value is JsonObject) properties[entry.key] = propertyValue(entry.value, value, dsId)
            }
        } else {
            val title = (requested["title"] as? JsonObject) ?: requested.values.firstOrNull() as? JsonObject
            properties["title"] = obj("id" to "title", "type" to "title", "title" to normalizeRich(title?.get("title")))
        }
        val page = obj(
            "object" to "page", "id" to id, "created_time" to time, "last_edited_time" to time,
            "created_by" to obj("object" to "user", "id" to DemoStore.ME), "last_edited_by" to obj("object" to "user", "id" to DemoStore.ME),
            "cover" to (body["cover"]?.let { resolveUploads(it) } ?: JsonNull), "icon" to (body["icon"]?.let { resolveUploads(it) } ?: JsonNull),
            "parent" to parentJson, "in_trash" to false, "is_archived" to false, "is_locked" to false,
            "properties" to JsonObject(properties), "url" to "https://app.notion.com/p/${id.replace("-", "")}", "public_url" to JsonNull,
        )
        pages[id] = page
        parent.s("page_id")?.let { pid ->
            val after = body.o("position")?.takeIf { it.s("type") == "after_block" }?.o("after_block")?.s("id")
            addBlocks(pid, true, listOf(DemoStore.B("child_page", obj("title" to titleOf(page)), id = id)), after = after, atStart = body.o("position")?.s("type") == "page_start")
        }
        val content = (body.a("children") ?: body.a("content"))?.objs()
        if (content != null) createBlocks(id, true, content, null, false)
        body.s("markdown")?.let { createBlocks(id, true, markdownToBlocks(it), null, false) }
        if (body.o("template") != null && body.o("template")?.s("type") != "none") {
            createBlocks(id, true, listOf(
                obj("type" to "callout", "callout" to obj("rich_text" to JsonArray(listOf(obj("text" to obj("content" to "Created from a template")))), "icon" to obj("type" to "emoji", "emoji" to "📋"))),
                obj("type" to "heading_2", "heading_2" to obj("rich_text" to JsonArray(listOf(obj("text" to obj("content" to "Notes")))))),
                obj("type" to "to_do", "to_do" to obj("rich_text" to JsonArray(listOf(obj("text" to obj("content" to "First step")))))),
            ), null, false)
        }
        return pages[id]!!
    }

    private fun DemoStore.updatePage(id: String, body: JsonObject): JsonObject {
        val page = pages[id] ?: notFound(id)
        val map = page.toMutableMap()
        body["icon"]?.let { map["icon"] = resolveUploads(it) }
        body["cover"]?.let { map["cover"] = resolveUploads(it) }
        body.b("in_trash")?.let { trash ->
            map["in_trash"] = JsonPrimitive(trash)
            if (trash) children.values.forEach { it.remove(id) }
            else page.o("parent")?.s("page_id")?.let { pid ->
                if (children[pid]?.contains(id) != true) addBlocks(pid, true, listOf(DemoStore.B("child_page", obj("title" to titleOf(page)), id = id)))
            }
        }
        body.b("is_locked")?.let { map["is_locked"] = JsonPrimitive(it) }
        body.o("properties")?.let { requested ->
            val dsId = page.o("parent")?.s("data_source_id")
            val props = (page.o("properties") ?: JsonObject(emptyMap())).toMutableMap()
            requested.forEach { (key, value) ->
                val value = value as? JsonObject ?: return@forEach
                if (dsId == null) {
                    props["title"] = obj("id" to "title", "type" to "title", "title" to normalizeRich(value["title"]))
                } else {
                    val schema = schemaFor(dsId).entries.firstOrNull { it.key == key || it.value.s("id") == key } ?: return@forEach
                    props[schema.key] = propertyValue(schema.value, value, dsId)
                }
            }
            map["properties"] = JsonObject(props)
        }
        if (body.o("template") != null) {
            if (body.b("erase_content") == true) children[id]?.clear()
            createBlocks(id, true, listOf(obj("type" to "paragraph", "paragraph" to obj("rich_text" to JsonArray(listOf(obj("text" to obj("content" to "Template applied ✨")))))) ), null, false)
        }
        map["last_edited_time"] = JsonPrimitive(nowIso())
        map["last_edited_by"] = obj("object" to "user", "id" to DemoStore.ME)
        pages[id] = JsonObject(map)
        return pages[id]!!
    }

    private fun DemoStore.movePage(id: String, body: JsonObject): JsonObject {
        val page = pages[id] ?: notFound(id)
        val target = body.o("parent") ?: throw DemoError(400, "validation_error", "parent is required")
        children.values.forEach { it.remove(id) }
        val newParent = target.s("page_id")?.let { obj("type" to "page_id", "page_id" to it) }
            ?: target.s("data_source_id")?.let { obj("type" to "data_source_id", "data_source_id" to it, "database_id" to (dataSources[it]?.o("parent")?.s("database_id") ?: "")) }
            ?: throw DemoError(400, "validation_error", "Invalid parent")
        pages[id] = JsonObject(page + mapOf("parent" to newParent, "last_edited_time" to JsonPrimitive(nowIso())))
        target.s("page_id")?.let { addBlocks(it, true, listOf(DemoStore.B("child_page", obj("title" to titleOf(page)), id = id))) }
        return pages[id]!!
    }

    private fun DemoStore.pageProperty(pageId: String, propertyId: String): JsonObject {
        val page = pages[pageId] ?: notFound(pageId)
        val decoded = java.net.URLDecoder.decode(propertyId, "UTF-8")
        val prop = page.o("properties")?.values?.mapNotNull { it as? JsonObject }?.firstOrNull { it.s("id") == decoded }
            ?: notFound(propertyId)
        return if (prop.s("type") == "relation") {
            list(prop.a("relation")?.objs()?.map { obj("object" to "property_item", "id" to decoded, "type" to "relation", "relation" to it) } ?: emptyList())
        } else JsonObject(prop + ("object" to JsonPrimitive("property_item")))
    }

    private fun DemoStore.tree(id: String): List<NBlock> = children[id].orEmpty().mapNotNull { bid ->
        blocks[bid]?.takeIf { it.b("in_trash") != true }?.let { raw -> Parse.block(withLiveTitle(raw)).copy(children = tree(bid)) }
    }

    private fun DemoStore.markdownOf(id: String): String {
        val page = pages[id] ?: notFound(id)
        return MarkdownExport.blocks(tree(id)).ifBlank { "" }.let { if (it.isBlank()) titleOf(page) else it }
    }

    private fun DemoStore.markdownToBlocks(markdown: String): List<JsonObject> = markdown.lines().filter { it.isNotBlank() }.map { line ->
        val trimmed = line.trim()
        fun block(type: String, text: String, extra: Map<String, JsonElement> = emptyMap()) =
            obj("type" to type, type to JsonObject(mapOf("rich_text" to JsonArray(listOf(obj("text" to obj("content" to text))))) + extra))
        when {
            trimmed.startsWith("#### ") -> block("heading_4", trimmed.removePrefix("#### "))
            trimmed.startsWith("### ") -> block("heading_3", trimmed.removePrefix("### "))
            trimmed.startsWith("## ") -> block("heading_2", trimmed.removePrefix("## "))
            trimmed.startsWith("# ") -> block("heading_1", trimmed.removePrefix("# "))
            trimmed.startsWith("- [ ] ") -> block("to_do", trimmed.removePrefix("- [ ] "))
            trimmed.startsWith("- [x] ") -> block("to_do", trimmed.removePrefix("- [x] "), mapOf("checked" to JsonPrimitive(true)))
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> block("bulleted_list_item", trimmed.drop(2))
            Regex("^\\d+\\. ").containsMatchIn(trimmed) -> block("numbered_list_item", trimmed.substringAfter(". "))
            trimmed.startsWith("> ") -> block("quote", trimmed.removePrefix("> "))
            trimmed == "---" -> obj("type" to "divider", "divider" to obj())
            else -> block("paragraph", trimmed)
        }
    }

    private fun DemoStore.appendMarkdown(id: String, body: JsonObject): JsonObject {
        val content = body.o("insert_content")?.s("content") ?: body.o("replace_content")?.s("new_str") ?: ""
        if (body.o("replace_content") != null) children[id]?.clear()
        val atStart = body.o("insert_content")?.o("position")?.s("type") == "start"
        createBlocks(id, true, markdownToBlocks(content), null, atStart)
        return obj("object" to "page_markdown", "id" to id, "markdown" to markdownOf(id))
    }

    // Databases & data sources

    private fun DemoStore.createDatabase(body: JsonObject): JsonObject {
        val dbId = newId()
        val dsId = newId()
        val time = nowIso()
        val parent = body.o("parent") ?: obj("type" to "workspace", "workspace" to true)
        val title = normalizeRich(body["title"])
        val requested = body.o("initial_data_source")?.o("properties") ?: obj("Name" to obj("title" to obj()))
        val props = LinkedHashMap<String, JsonElement>()
        requested.forEach { (name, config) ->
            val c = config as? JsonObject ?: return@forEach
            val type = c.keys.firstOrNull { it != "name" && it != "description" && it != "type" } ?: return@forEach
            props[name] = obj("id" to if (type == "title") "title" else newId().take(4), "name" to name, "type" to type, type to withOptionIds(c.o(type) ?: obj()))
        }
        if (props.values.none { (it as JsonObject).s("type") == "title" }) props["Name"] = obj("id" to "title", "name" to "Name", "type" to "title", "title" to obj())
        databases[dbId] = obj(
            "object" to "database", "id" to dbId, "title" to title, "description" to JsonArray(emptyList()), "parent" to parent,
            "is_inline" to (body.b("is_inline") ?: false), "is_locked" to false, "in_trash" to false,
            "data_sources" to JsonArray(listOf(obj("id" to dsId, "name" to (title.objs().joinToString("") { it.s("plain_text") ?: "" }.ifBlank { "Untitled" })))),
            "icon" to (body["icon"] ?: JsonNull), "cover" to JsonNull, "url" to "https://app.notion.com/p/${dbId.replace("-", "")}",
            "public_url" to JsonNull, "created_time" to time, "last_edited_time" to time, "database_type" to JsonNull,
        )
        dataSources[dsId] = obj(
            "object" to "data_source", "id" to dsId, "title" to title, "description" to JsonArray(emptyList()),
            "parent" to obj("type" to "database_id", "database_id" to dbId), "database_parent" to parent,
            "is_inline" to (body.b("is_inline") ?: false), "in_trash" to false, "icon" to (body["icon"] ?: JsonNull), "cover" to JsonNull,
            "created_time" to time, "last_edited_time" to time, "database_type" to JsonNull,
            "created_by" to obj("object" to "user", "id" to DemoStore.ME), "last_edited_by" to obj("object" to "user", "id" to DemoStore.ME),
            "url" to "https://app.notion.com/p/${dsId.replace("-", "")}", "public_url" to JsonNull, "properties" to JsonObject(props),
        )
        val viewId = newId()
        views[viewId] = obj(
            "object" to "view", "id" to viewId, "parent" to obj("type" to "database_id", "database_id" to dbId), "name" to "Default view",
            "type" to "table", "data_source_id" to dsId, "filter" to JsonNull, "sorts" to JsonNull, "quick_filters" to JsonNull,
            "configuration" to obj("type" to "table"), "created_time" to time, "last_edited_time" to time, "url" to "",
        )
        parent.s("page_id")?.let { pid -> addBlocks(pid, true, listOf(DemoStore.B("child_database", obj("title" to title.objs().joinToString("") { it.s("plain_text") ?: "" }), id = dbId))) }
        return databases[dbId]!!
    }

    private fun DemoStore.withOptionIds(config: JsonObject): JsonObject {
        val options = config.a("options")?.objs() ?: return config
        return JsonObject(config + ("options" to JsonArray(options.map { o ->
            JsonObject(o + mapOf("id" to JsonPrimitive(o.s("id") ?: ("opt-" + (o.s("name") ?: "").hashCode())), "color" to JsonPrimitive(o.s("color") ?: "default")))
        })))
    }

    private fun DemoStore.updateDatabase(id: String, body: JsonObject): JsonObject {
        val db = databases[id] ?: notFound(id)
        val map = db.toMutableMap()
        body["title"]?.let { map["title"] = normalizeRich(it) }
        body["description"]?.let { map["description"] = normalizeRich(it) }
        body["icon"]?.let { map["icon"] = resolveUploads(it) }
        body["cover"]?.let { map["cover"] = resolveUploads(it) }
        body.b("is_inline")?.let { map["is_inline"] = JsonPrimitive(it) }
        body.b("is_locked")?.let { map["is_locked"] = JsonPrimitive(it) }
        body.b("in_trash")?.let { trash ->
            map["in_trash"] = JsonPrimitive(trash)
            db.a("data_sources")?.objs()?.mapNotNull { it.s("id") }?.forEach { dsId ->
                dataSources[dsId]?.let { dataSources[dsId] = JsonObject(it + ("in_trash" to JsonPrimitive(trash))) }
            }
            if (trash) children.values.forEach { it.remove(id) }
        }
        map["last_edited_time"] = JsonPrimitive(nowIso())
        databases[id] = JsonObject(map)
        db.a("data_sources")?.objs()?.mapNotNull { it.s("id") }?.forEach { dsId ->
            dataSources[dsId]?.let { ds ->
                dataSources[dsId] = JsonObject(ds + listOfNotNull(
                    body["icon"]?.let { "icon" to resolveUploads(it) },
                    body["description"]?.let { "description" to normalizeRich(it) },
                ).toMap())
            }
        }
        return databases[id]!!
    }

    private fun DemoStore.createDataSource(body: JsonObject): JsonObject {
        val dbId = body.o("parent")?.s("database_id") ?: throw DemoError(400, "validation_error", "parent.database_id is required")
        val db = databases[dbId] ?: notFound(dbId)
        val dsId = newId()
        val title = normalizeRich(body["title"])
        val first = db.a("data_sources")?.objs()?.firstOrNull()?.s("id")?.let { dataSources[it] }
        dataSources[dsId] = obj(
            "object" to "data_source", "id" to dsId, "title" to title, "description" to JsonArray(emptyList()),
            "parent" to obj("type" to "database_id", "database_id" to dbId), "database_parent" to (db.o("parent") ?: JsonNull),
            "is_inline" to (db.b("is_inline") ?: false), "in_trash" to false, "icon" to JsonNull, "cover" to JsonNull,
            "created_time" to nowIso(), "last_edited_time" to nowIso(), "database_type" to JsonNull,
            "created_by" to obj("object" to "user", "id" to DemoStore.ME), "last_edited_by" to obj("object" to "user", "id" to DemoStore.ME),
            "url" to "", "public_url" to JsonNull,
            "properties" to obj("Name" to obj("id" to "title", "name" to "Name", "type" to "title", "title" to obj())),
        )
        val refs = (db.a("data_sources")?.objs() ?: emptyList()) + obj("id" to dsId, "name" to title.objs().joinToString("") { it.s("plain_text") ?: "" })
        databases[dbId] = JsonObject(db + ("data_sources" to JsonArray(refs)))
        val viewId = newId()
        views[viewId] = obj(
            "object" to "view", "id" to viewId, "parent" to obj("type" to "database_id", "database_id" to dbId),
            "name" to (title.objs().joinToString("") { it.s("plain_text") ?: "" }.ifBlank { "Table" }), "type" to "table", "data_source_id" to dsId,
            "filter" to JsonNull, "sorts" to JsonNull, "quick_filters" to JsonNull, "configuration" to obj("type" to "table"),
            "created_time" to nowIso(), "last_edited_time" to nowIso(), "url" to "",
        )
        if (first == null) Unit
        return dataSources[dsId]!!
    }

    private fun DemoStore.updateDataSource(id: String, body: JsonObject): JsonObject {
        val ds = dataSources[id] ?: notFound(id)
        val map = ds.toMutableMap()
        body["title"]?.let { title ->
            map["title"] = normalizeRich(title)
            val dbId = ds.o("parent")?.s("database_id")
            databases[dbId]?.let { db ->
                val refs = db.a("data_sources")?.objs()?.map { r ->
                    if (r.s("id") == id) obj("id" to id, "name" to normalizeRich(title).objs().joinToString("") { it.s("plain_text") ?: "" }) else r
                }
                if (refs != null) databases[dbId!!] = JsonObject(db + ("data_sources" to JsonArray(refs)))
            }
        }
        body["icon"]?.let { map["icon"] = resolveUploads(it) }
        body.b("in_trash")?.let { map["in_trash"] = JsonPrimitive(it) }
        body.o("properties")?.let { changes ->
            val props = LinkedHashMap((ds.o("properties") ?: JsonObject(emptyMap())).mapValues { it.value as JsonObject })
            changes.forEach { (key, change) ->
                val existing = props.entries.firstOrNull { it.key == key || it.value.s("id") == key }
                if (change is JsonNull) {
                    existing?.let { props.remove(it.key) }
                    return@forEach
                }
                val c = change as? JsonObject ?: return@forEach
                val newType = c.keys.firstOrNull { it !in setOf("name", "description", "type") }
                if (existing == null) {
                    val type = newType ?: return@forEach
                    val name = c.s("name") ?: key
                    props[name] = obj("id" to newId().take(4), "name" to name, "type" to type, type to withOptionIds(c.o(type) ?: obj()))
                } else {
                    var prop = existing.value
                    if (newType != null) {
                        val config = withOptionIds(JsonObject((if (prop.s("type") == newType) prop.o(newType) ?: obj() else obj()) + (c.o(newType) ?: obj())))
                        prop = JsonObject(prop.filterKeys { it == "id" || it == "name" || it == "description" } + mapOf("type" to JsonPrimitive(newType), newType to config))
                    }
                    c.s("description")?.let { prop = JsonObject(prop + ("description" to JsonPrimitive(it))) }
                    val newName = c.s("name")
                    if (newName != null && newName != existing.key) {
                        val rebuilt = LinkedHashMap<String, JsonObject>()
                        props.forEach { (k, v) -> if (k == existing.key) rebuilt[newName] = JsonObject(prop + ("name" to JsonPrimitive(newName))) else rebuilt[k] = v }
                        props.clear(); props.putAll(rebuilt)
                        renameInRows(id, existing.key, newName)
                    } else props[existing.key] = prop
                }
            }
            map["properties"] = JsonObject(props)
        }
        map["last_edited_time"] = JsonPrimitive(nowIso())
        dataSources[id] = JsonObject(map)
        return dataSources[id]!!
    }

    private fun DemoStore.renameInRows(dsId: String, from: String, to: String) {
        pages.entries.filter { it.value.o("parent")?.s("data_source_id") == dsId }.forEach { (pid, page) ->
            val props = page.o("properties") ?: return@forEach
            val rebuilt = LinkedHashMap<String, JsonElement>()
            props.forEach { (k, v) -> rebuilt[if (k == from) to else k] = v }
            pages[pid] = JsonObject(page + ("properties" to JsonObject(rebuilt)))
        }
    }

    private fun DemoStore.query(id: String, body: JsonObject): JsonObject {
        val ds = dataSources[id] ?: notFound(id)
        val schema = ds.o("properties")!!
        val rows = pages.values.filter { it.o("parent")?.s("data_source_id") == id && it.b("in_trash") != true }.map { row ->
            val props = row.o("properties")!!
            val filled = LinkedHashMap<String, JsonElement>()
            schema.forEach { (name, s) -> filled[name] = props[name] ?: emptyValue(s as JsonObject, 0) }
            JsonObject(row + ("properties" to JsonObject(filled)))
        }
        val filtered = rows.filter { matches(it, body.o("filter")) }
        val sorts = body.a("sorts")?.objs() ?: emptyList()
        val sorted = if (sorts.isEmpty()) filtered else filtered.sortedWith { a, b ->
            for (sort in sorts) {
                val c = compareKeys(sortKey(a, sort), sortKey(b, sort))
                if (c != 0) return@sortedWith if (sort.s("direction") == "descending") -c else c
            }
            0
        }
        return pageOf(sorted, body.s("start_cursor"), body.i("page_size")?.toString())
    }

    private fun compareKeys(a: Comparable<*>?, b: Comparable<*>?): Int = when {
        a == null && b == null -> 0
        a == null -> 1
        b == null -> -1
        a is Number && b is Number -> a.toDouble().compareTo(b.toDouble())
        else -> a.toString().compareTo(b.toString())
    }

    // Views

    private fun DemoStore.createView(body: JsonObject): JsonObject {
        val id = newId()
        val dbId = body.s("database_id") ?: throw DemoError(400, "validation_error", "database_id is required")
        views[id] = obj(
            "object" to "view", "id" to id, "parent" to obj("type" to "database_id", "database_id" to dbId),
            "name" to (body.s("name") ?: "View"), "type" to (body.s("type") ?: "table"), "data_source_id" to body.s("data_source_id"),
            "filter" to (body["filter"] ?: JsonNull), "sorts" to (body["sorts"] ?: JsonNull), "quick_filters" to (body["quick_filters"] ?: JsonNull),
            "configuration" to (body["configuration"] ?: obj("type" to (body.s("type") ?: "table"))),
            "created_time" to nowIso(), "last_edited_time" to nowIso(), "url" to "",
        )
        return views[id]!!
    }

    private fun DemoStore.updateView(id: String, body: JsonObject): JsonObject {
        val view = views[id] ?: notFound(id)
        val map = view.toMutableMap()
        listOf("name", "filter", "sorts", "quick_filters").forEach { key -> body[key]?.let { map[key] = it } }
        body.o("configuration")?.let { config ->
            map["configuration"] = JsonObject((view.o("configuration") ?: JsonObject(emptyMap())) + config)
        }
        map["last_edited_time"] = JsonPrimitive(nowIso())
        views[id] = JsonObject(map)
        return views[id]!!
    }

    // Comments

    private fun DemoStore.createComment(body: JsonObject): JsonObject {
        val discussion = body.s("discussion_id")
        val parent = body.o("parent") ?: discussion?.let { d -> comments.firstOrNull { it.s("discussion_id") == d }?.o("parent") }
            ?: throw DemoError(400, "validation_error", "parent or discussion_id is required")
        val rich = body["rich_text"]?.let { normalizeRich(it) }
            ?: normalizeRich(JsonArray(listOf(obj("text" to obj("content" to (body.s("markdown") ?: ""))))))
        val comment = obj(
            "object" to "comment", "id" to newId(), "discussion_id" to (discussion ?: newId()),
            "parent" to (if (parent.s("page_id") != null) obj("type" to "page_id", "page_id" to parent.s("page_id")) else obj("type" to "block_id", "block_id" to parent.s("block_id"))),
            "created_time" to nowIso(), "last_edited_time" to nowIso(), "created_by" to obj("object" to "user", "id" to DemoStore.ME),
            "rich_text" to rich, "display_name" to obj("type" to "user", "resolved_name" to "Ada Lovelace"),
            "attachments" to JsonArray(body.a("attachments")?.objs()?.mapNotNull { a ->
                a.s("file_upload_id")?.let { uploadFiles[it] }?.let { url -> obj("category" to "image", "file" to obj("url" to url, "expiry_time" to nowIso())) }
            } ?: emptyList()),
        )
        comments += comment
        return comment
    }

    private fun DemoStore.updateComment(id: String, body: JsonObject): JsonObject {
        val index = comments.indexOfFirst { it.s("id") == id }.takeIf { it >= 0 } ?: notFound(id)
        val rich = body["rich_text"]?.let { normalizeRich(it) } ?: comments[index].a("rich_text")!!
        comments[index] = JsonObject(comments[index] + mapOf("rich_text" to rich, "last_edited_time" to JsonPrimitive(nowIso())))
        return comments[index]
    }

    // File uploads

    private fun DemoStore.createUpload(body: JsonObject): JsonObject {
        val id = newId()
        val external = body.s("external_url")
        uploads[id] = obj(
            "object" to "file_upload", "id" to id, "created_time" to nowIso(), "last_edited_time" to nowIso(),
            "expiry_time" to java.time.Instant.now().plusSeconds(3600).toString(), "in_trash" to false,
            "status" to if (external != null) "uploaded" else "pending", "filename" to (body.s("filename") ?: "file"),
            "content_type" to (body.s("content_type") ?: JsonNull), "content_length" to JsonNull,
            "upload_url" to "https://api.notion.com/v1/file_uploads/$id/send",
        )
        if (external != null) uploadFiles[id] = external
        return uploads[id]!!
    }

    private fun DemoStore.sendUpload(id: String, request: Request): JsonObject {
        val upload = uploads[id] ?: notFound(id)
        val part = (request.body as? MultipartBody)?.parts?.firstOrNull { p ->
            p.headers?.get("Content-Disposition")?.contains("name=\"file\"") == true
        }
        val buffer = Buffer()
        part?.body?.writeTo(buffer)
        val name = upload.s("filename") ?: "file"
        val dir = File(System.getProperty("java.io.tmpdir") ?: "/tmp", "demo-uploads").apply { mkdirs() }
        val file = File(dir, "$id-$name")
        file.appendBytes(buffer.readByteArray())
        uploadFiles[id] = file.toURI().toString()
        uploads[id] = JsonObject(upload + mapOf("status" to JsonPrimitive("uploaded"), "content_length" to JsonPrimitive(file.length())))
        return uploads[id]!!
    }

    private fun DemoStore.completeUpload(id: String): JsonObject {
        val upload = uploads[id] ?: notFound(id)
        uploads[id] = JsonObject(upload + ("status" to JsonPrimitive("uploaded")))
        return uploads[id]!!
    }
}
