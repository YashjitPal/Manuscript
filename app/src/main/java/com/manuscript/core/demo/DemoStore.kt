package com.manuscript.core.demo

import com.manuscript.core.json.a
import com.manuscript.core.json.b
import com.manuscript.core.json.d
import com.manuscript.core.json.o
import com.manuscript.core.json.obj
import com.manuscript.core.json.objs
import com.manuscript.core.json.s
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/** An in-memory Notion workspace that powers the demo account. */
class DemoStore {
    companion object {
        const val ME = "6a1e0f2c-0d3f-4f43-9a0a-1a2b3c4d5e01"
        const val GRACE = "6a1e0f2c-0d3f-4f43-9a0a-1a2b3c4d5e02"
        const val ALAN = "6a1e0f2c-0d3f-4f43-9a0a-1a2b3c4d5e03"
        const val KATHERINE = "6a1e0f2c-0d3f-4f43-9a0a-1a2b3c4d5e04"
        const val BOT = "6a1e0f2c-0d3f-4f43-9a0a-1a2b3c4d5e05"
        fun id(seed: String): String = UUID.nameUUIDFromBytes("manuscript-demo:$seed".toByteArray()).toString()
        private const val IMG = "https://images.unsplash.com/"
    }

    val users = LinkedHashMap<String, JsonObject>()
    val pages = LinkedHashMap<String, JsonObject>()
    val blocks = HashMap<String, JsonObject>()
    val children = HashMap<String, MutableList<String>>()
    val databases = LinkedHashMap<String, JsonObject>()
    val dataSources = LinkedHashMap<String, JsonObject>()
    val views = LinkedHashMap<String, JsonObject>()
    val comments = mutableListOf<JsonObject>()
    val uploads = HashMap<String, JsonObject>()
    val uploadFiles = HashMap<String, String>()
    private val lock = Any()
    private val now = Instant.now()

    fun <T> sync(block: DemoStore.() -> T): T = synchronized(lock) { block() }

    fun ago(hours: Long): String = now.minus(hours, ChronoUnit.HOURS).toString()
    fun day(offset: Long): String = LocalDate.now().plusDays(offset).toString()
    private fun userRef(id: String) = obj("object" to "user", "id" to id)

    // Rich text builders

    fun t(
        text: String,
        bold: Boolean = false,
        italic: Boolean = false,
        code: Boolean = false,
        strike: Boolean = false,
        underline: Boolean = false,
        color: String = "default",
        link: String? = null,
    ): JsonObject = obj(
        "type" to "text",
        "text" to obj("content" to text, "link" to (link?.let { obj("url" to it) } ?: JsonNull)),
        "annotations" to obj("bold" to bold, "italic" to italic, "strikethrough" to strike, "underline" to underline, "code" to code, "color" to color),
        "plain_text" to text,
        "href" to (link ?: JsonNull),
    )

    private val plainAnn = obj("bold" to false, "italic" to false, "strikethrough" to false, "underline" to false, "code" to false, "color" to "default")

    fun mentionUser(id: String) = obj(
        "type" to "mention", "mention" to obj("type" to "user", "user" to (users[id] ?: userRef(id))),
        "annotations" to plainAnn, "plain_text" to "@" + (users[id]?.s("name") ?: "Someone"), "href" to JsonNull,
    )

    fun mentionPage(id: String, title: String) = obj(
        "type" to "mention", "mention" to obj("type" to "page", "page" to obj("id" to id)),
        "annotations" to plainAnn, "plain_text" to title, "href" to "https://app.notion.com/p/${id.replace("-", "")}",
    )

    fun mentionDate(start: String, end: String? = null) = obj(
        "type" to "mention", "mention" to obj("type" to "date", "date" to obj("start" to start, "end" to (end ?: JsonNull), "time_zone" to JsonNull)),
        "annotations" to plainAnn, "plain_text" to start, "href" to JsonNull,
    )

    fun eq(expr: String) = obj(
        "type" to "equation", "equation" to obj("expression" to expr), "annotations" to plainAnn, "plain_text" to expr, "href" to JsonNull,
    )

    fun rich(vararg parts: Any): JsonArray = JsonArray(parts.map { if (it is String) t(it) else it as JsonObject })

    // Block builders

    class B(val type: String, val body: JsonObject, val kids: List<B> = emptyList(), val id: String? = null)

    private fun textBody(r: JsonArray, color: String = "default", extra: Map<String, JsonElement> = emptyMap()) =
        JsonObject(mapOf("rich_text" to r, "color" to JsonPrimitive(color)) + extra)

    fun p(vararg parts: Any, color: String = "default", kids: List<B> = emptyList()) = B("paragraph", textBody(rich(*parts), color), kids)
    fun h1(text: String, toggle: Boolean = false, kids: List<B> = emptyList()) = B("heading_1", textBody(rich(text), extra = mapOf("is_toggleable" to JsonPrimitive(toggle))), kids)
    fun h2(text: String, toggle: Boolean = false, kids: List<B> = emptyList()) = B("heading_2", textBody(rich(text), extra = mapOf("is_toggleable" to JsonPrimitive(toggle))), kids)
    fun h3(text: String, toggle: Boolean = false, kids: List<B> = emptyList()) = B("heading_3", textBody(rich(text), extra = mapOf("is_toggleable" to JsonPrimitive(toggle))), kids)
    fun bullet(vararg parts: Any, kids: List<B> = emptyList()) = B("bulleted_list_item", textBody(rich(*parts)), kids)
    fun num(vararg parts: Any, kids: List<B> = emptyList()) = B("numbered_list_item", textBody(rich(*parts)), kids)
    fun todo(text: String, checked: Boolean = false) = B("to_do", textBody(rich(text), extra = mapOf("checked" to JsonPrimitive(checked))))
    fun toggle(text: String, kids: List<B>) = B("toggle", textBody(rich(t(text, bold = true))), kids)
    fun quote(vararg parts: Any) = B("quote", textBody(rich(*parts)))
    fun callout(emoji: String, vararg parts: Any, color: String = "gray_background") =
        B("callout", textBody(rich(*parts), color, mapOf("icon" to obj("type" to "emoji", "emoji" to emoji))))
    fun code(lang: String, src: String, caption: String = "") =
        B("code", obj("rich_text" to rich(src), "language" to lang, "caption" to if (caption.isEmpty()) JsonArray(emptyList()) else rich(caption)))
    fun equation(expr: String) = B("equation", obj("expression" to expr))
    fun divider() = B("divider", obj())
    fun toc() = B("table_of_contents", obj("color" to "default"))
    fun image(url: String, caption: String = "") =
        B("image", obj("type" to "external", "external" to obj("url" to url), "caption" to if (caption.isEmpty()) JsonArray(emptyList()) else rich(t(caption, italic = true))))
    fun bookmark(url: String, caption: String = "") = B("bookmark", obj("url" to url, "caption" to if (caption.isEmpty()) JsonArray(emptyList()) else rich(caption)))
    fun table(header: Boolean, rows: List<List<String>>) = B(
        "table",
        obj("table_width" to (rows.firstOrNull()?.size ?: 1), "has_column_header" to header, "has_row_header" to false),
        rows.map { row -> B("table_row", obj("cells" to JsonArray(row.map { rich(it) }))) },
    )
    fun columns(vararg cols: List<B>) = B("column_list", obj(), cols.map { B("column", obj(), it) })
    fun linkTo(pageId: String) = B("link_to_page", obj("type" to "page_id", "page_id" to pageId))
    fun tabs(vararg tabs: Pair<String, List<B>>) = B("tab", obj(), tabs.map { (label, content) ->
        B("paragraph", textBody(rich(label), extra = mapOf("icon" to obj("type" to "emoji", "emoji" to "📌"))), content)
    })

    // Object creation

    fun blockJson(id: String, b: B, parent: JsonObject, hasChildren: Boolean, time: String) = JsonObject(
        mapOf(
            "object" to JsonPrimitive("block"), "id" to JsonPrimitive(id), "parent" to parent,
            "created_time" to JsonPrimitive(time), "last_edited_time" to JsonPrimitive(time),
            "created_by" to userRef(ME), "last_edited_by" to userRef(ME),
            "has_children" to JsonPrimitive(hasChildren), "in_trash" to JsonPrimitive(false),
            "type" to JsonPrimitive(b.type), b.type to b.body,
        ),
    )

    fun addBlocks(parentId: String, parentIsPage: Boolean, list: List<B>, time: String = ago(30), after: String? = null, atStart: Boolean = false): List<String> {
        val parentJson = if (parentIsPage) obj("type" to "page_id", "page_id" to parentId) else obj("type" to "block_id", "block_id" to parentId)
        val ids = list.map { b ->
            val id = b.id ?: UUID.randomUUID().toString()
            blocks[id] = blockJson(id, b, parentJson, b.kids.isNotEmpty(), time)
            if (b.kids.isNotEmpty()) addBlocks(id, false, b.kids, time)
            id
        }
        val existing = children.getOrPut(parentId) { mutableListOf() }
        val index = when {
            after != null -> existing.indexOf(after).let { if (it < 0) existing.size else it + 1 }
            atStart -> 0
            else -> existing.size
        }
        existing.addAll(index, ids)
        blocks[parentId]?.let { blocks[parentId] = JsonObject(it + ("has_children" to JsonPrimitive(true))) }
        return ids
    }

    fun pageJson(
        id: String,
        title: String,
        parent: JsonObject,
        icon: JsonElement = JsonNull,
        cover: String? = null,
        props: Map<String, JsonObject> = emptyMap(),
        titleKey: String = "title",
        created: String = ago(200),
        edited: String = ago(5),
        editor: String = ME,
        trash: Boolean = false,
    ) = obj(
        "object" to "page", "id" to id, "created_time" to created, "last_edited_time" to edited,
        "created_by" to userRef(ME), "last_edited_by" to userRef(editor),
        "cover" to (cover?.let { obj("type" to "external", "external" to obj("url" to it)) } ?: JsonNull),
        "icon" to icon, "parent" to parent, "in_trash" to trash, "is_archived" to false, "is_locked" to false,
        "properties" to JsonObject(
            mapOf(titleKey to obj("id" to "title", "type" to "title", "title" to rich(title))) + props,
        ),
        "url" to "https://app.notion.com/p/${id.replace("-", "")}", "public_url" to JsonNull,
    )

    fun emoji(e: String) = obj("type" to "emoji", "emoji" to e)
    fun nativeIcon(name: String, color: String) = obj("type" to "icon", "icon" to obj("name" to name, "color" to color))

    fun addPage(page: JsonObject, content: List<B> = emptyList()): String {
        val id = page.s("id")!!
        pages[id] = page
        if (content.isNotEmpty()) addBlocks(id, true, content, page.s("created_time") ?: ago(48))
        val parent = page.o("parent")
        if (parent?.s("type") == "page_id") {
            val parentId = parent.s("page_id")!!
            addBlocks(parentId, true, listOf(B("child_page", obj("title" to (titleOf(page))), id = id)))
        }
        return id
    }

    fun titleOf(page: JsonObject): String = page.o("properties")?.values?.mapNotNull { it as? JsonObject }
        ?.firstOrNull { it.s("type") == "title" }?.a("title")?.objs()?.joinToString("") { it.s("plain_text") ?: "" } ?: ""

    // Property value builders for rows

    fun pTitle(text: String) = obj("id" to "title", "type" to "title", "title" to rich(text))
    fun pText(id: String, text: String) = obj("id" to id, "type" to "rich_text", "rich_text" to if (text.isEmpty()) JsonArray(emptyList()) else rich(text))
    fun pNum(id: String, v: Double?) = obj("id" to id, "type" to "number", "number" to (v ?: JsonNull))
    fun pSelect(id: String, name: String?, color: String = "default") =
        obj("id" to id, "type" to "select", "select" to (name?.let { obj("id" to id + it.hashCode(), "name" to it, "color" to color) } ?: JsonNull))
    fun pStatus(id: String, name: String, color: String) = obj("id" to id, "type" to "status", "status" to obj("id" to "st-${name.hashCode()}", "name" to name, "color" to color))
    fun pMulti(id: String, vararg opts: Pair<String, String>) =
        obj("id" to id, "type" to "multi_select", "multi_select" to JsonArray(opts.map { (n, c) -> obj("id" to id + n.hashCode(), "name" to n, "color" to c) }))
    fun pDate(id: String, start: String?, end: String? = null) =
        obj("id" to id, "type" to "date", "date" to (start?.let { obj("start" to it, "end" to (end ?: JsonNull), "time_zone" to JsonNull) } ?: JsonNull))
    fun pPeople(id: String, vararg ids: String) = obj("id" to id, "type" to "people", "people" to JsonArray(ids.mapNotNull { users[it] }))
    fun pCheck(id: String, v: Boolean) = obj("id" to id, "type" to "checkbox", "checkbox" to v)
    fun pUrl(id: String, v: String?) = obj("id" to id, "type" to "url", "url" to (v ?: JsonNull))
    fun pFiles(id: String, vararg urls: String) =
        obj("id" to id, "type" to "files", "files" to JsonArray(urls.map { obj("name" to it.substringAfterLast('/'), "type" to "external", "external" to obj("url" to it)) }))
    fun pUnique(id: String, prefix: String, n: Int) = obj("id" to id, "type" to "unique_id", "unique_id" to obj("prefix" to prefix, "number" to n))
    fun pFormulaNum(id: String, v: Double) = obj("id" to id, "type" to "formula", "formula" to obj("type" to "number", "number" to v))
    fun pRelation(id: String, vararg ids: String) = obj("id" to id, "type" to "relation", "relation" to JsonArray(ids.map { obj("id" to it) }), "has_more" to false)
    fun pCreated(id: String, time: String) = obj("id" to id, "type" to "created_time", "created_time" to time)

    fun option(name: String, color: String) = obj("id" to "opt-" + name.hashCode(), "name" to name, "color" to color, "description" to JsonNull)

    init {
        seed()
    }

    private fun seed() {
        fun user(id: String, name: String, img: Int, email: String) {
            users[id] = obj(
                "object" to "user", "id" to id, "type" to "person", "name" to name,
                "avatar_url" to "https://i.pravatar.cc/240?img=$img",
                "person" to obj("email" to email, "email_verified" to true),
            )
        }
        user(ME, "Ada Lovelace", 47, "ada@manuscript.app")
        user(GRACE, "Grace Hopper", 45, "grace@manuscript.app")
        user(ALAN, "Alan Turing", 12, "alan@manuscript.app")
        user(KATHERINE, "Katherine Johnson", 32, "katherine@manuscript.app")
        users[BOT] = obj(
            "object" to "user", "id" to BOT, "type" to "bot", "name" to "Manuscript", "avatar_url" to JsonNull,
            "bot" to obj("owner" to obj("type" to "workspace", "workspace" to true), "workspace_name" to "Manuscript Studio",
                "workspace_id" to id("workspace"), "workspace_limits" to obj("max_file_upload_size_in_bytes" to 5242880)),
        )

        val ws = obj("type" to "workspace", "workspace" to true)
        val welcome = id("welcome")
        val roadmapPage = id("roadmap-page")
        val readingPage = id("reading-page")
        val wiki = id("wiki")
        val meetings = id("meetings")
        val journalPage = id("journal-page")

        // Engineering wiki and children
        addPage(pageJson(wiki, "Engineering Wiki", ws, nativeIcon("book", "blue"), "${IMG}photo-1451187580459-43490279c0fa?w=1600&q=80", edited = ago(20), editor = ALAN), listOf(
            callout("🧭", "Everything the team needs to ship great software. Start with ", t("Architecture", bold = true), " and work your way down."),
            h2("Principles"),
            num(t("Clarity over cleverness. ", bold = true), "Code is read far more often than it's written."),
            num(t("Small, reversible steps. ", bold = true), "Ship behind flags and measure."),
            num(t("Own the outcome. ", bold = true), "Ship it, watch it, fix it."),
        ))
        addPage(pageJson(id("architecture"), "Architecture", obj("type" to "page_id", "page_id" to wiki), emoji("🏛️"), edited = ago(70), editor = ALAN), listOf(
            h2("System overview"),
            p("Manuscript talks to the ", t("Notion API", code = true), " through a single resilient HTTP layer with retries and rate-limit handling."),
            image("${IMG}photo-1558494949-ef010cbdcc31?w=1600&q=80", "Our data center, lovingly over-provisioned."),
            h3("Layers"),
            bullet(t("Presentation", bold = true), " — Jetpack Compose with Material 3 Expressive", kids = listOf(bullet("Adaptive layouts for phones, foldables, tablets and Chromebooks"))),
            bullet(t("Domain", bold = true), " — immutable models and pure functions"),
            bullet(t("Data", bold = true), " — repositories, an observable cache and Room"),
            code("kotlin", "class PageRepository(private val api: NotionApi) {\n    suspend fun page(id: String): NPage = api.page(id)\n}", "PageRepository.kt"),
        ))
        addPage(pageJson(id("onboarding"), "Onboarding checklist", obj("type" to "page_id", "page_id" to wiki), emoji("🎒"), edited = ago(120), editor = GRACE), listOf(
            p("Welcome aboard! Work through this list during your first week."),
            todo("Get laptop and accounts set up", true),
            todo("Read the Architecture overview", true),
            todo("Pair with a teammate on a bug fix"),
            todo("Ship your first change to production"),
            toggle("Helpful links", listOf(bullet(t("Style guide", link = "https://m3.material.io")), bullet(t("Notion API docs", link = "https://developers.notion.com")))),
        ))
        addPage(pageJson(id("guidelines"), "Coding guidelines", obj("type" to "page_id", "page_id" to wiki), emoji("📐"), edited = ago(300), editor = KATHERINE), listOf(
            h2("Kotlin"),
            p("Prefer ", t("val", code = true), " over ", t("var", code = true), " and immutable collections."),
            code("kotlin", "fun greet(name: String): String = \"Hello, \$name!\""),
            h2("Reviews"),
            quote("A good review is a conversation, not a verdict."),
        ))

        // Meeting notes
        addPage(pageJson(meetings, "Meeting Notes", ws, nativeIcon("calendar", "orange"), edited = ago(9), editor = GRACE), listOf(
            p("Notes from team rituals. Newest first."),
        ))
        listOf(
            Triple("Weekly sync", 9L, GRACE), Triple("Design review: Search", 50L, ME), Triple("Quarterly planning", 170L, KATHERINE),
        ).forEach { (title, hours, who) ->
            addPage(pageJson(id("meet-$title"), title, obj("type" to "page_id", "page_id" to meetings), emoji("🗒️"), edited = ago(hours), editor = who), listOf(
                p(t("Attendees: ", bold = true), mentionUser(ME), " ", mentionUser(GRACE), " ", mentionUser(ALAN)),
                p(t("Date: ", bold = true), mentionDate(day(-hours / 24))),
                h3("Agenda"),
                bullet("Wins from last week"), bullet("Blockers"), bullet("Next steps"),
                h3("Action items"),
                todo("Follow up with design", hours > 40), todo("Update the roadmap"),
            ))
        }

        // Product roadmap database
        val roadmapDb = id("roadmap-db")
        val roadmapDs = id("roadmap-ds")
        addPage(pageJson(roadmapPage, "Product Roadmap", ws, emoji("🚀"), "${IMG}photo-1557672172-298e090bd0f1?w=1600&q=80", edited = ago(2), editor = GRACE), listOf(
            callout("🎯", "Our north star for the quarter: ", t("make Manuscript the most beautiful way to use Notion on Android.", bold = true), color = "blue_background"),
            B("child_database", obj("title" to "Roadmap"), id = roadmapDb),
        ))
        val statusOptions = JsonArray(listOf(option("Not started", "default"), option("In progress", "blue"), option("In review", "purple"), option("Done", "green")))
        databases[roadmapDb] = obj(
            "object" to "database", "id" to roadmapDb, "title" to rich("Roadmap"), "description" to rich("Everything we're building, at a glance."),
            "parent" to obj("type" to "page_id", "page_id" to roadmapPage), "is_inline" to true, "is_locked" to false, "in_trash" to false,
            "data_sources" to JsonArray(listOf(obj("id" to roadmapDs, "name" to "Roadmap"))), "icon" to emoji("🗺️"), "cover" to JsonNull,
            "url" to "https://app.notion.com/p/${roadmapDb.replace("-", "")}", "public_url" to JsonNull,
            "created_time" to ago(400), "last_edited_time" to ago(2), "database_type" to JsonNull,
        )
        dataSources[roadmapDs] = obj(
            "object" to "data_source", "id" to roadmapDs, "title" to rich("Roadmap"), "description" to rich("Everything we're building, at a glance."),
            "parent" to obj("type" to "database_id", "database_id" to roadmapDb),
            "database_parent" to obj("type" to "page_id", "page_id" to roadmapPage),
            "is_inline" to true, "in_trash" to false, "icon" to emoji("🗺️"), "cover" to JsonNull,
            "created_time" to ago(400), "last_edited_time" to ago(2), "database_type" to JsonNull,
            "created_by" to userRef(ME), "last_edited_by" to userRef(GRACE),
            "url" to "https://app.notion.com/p/${roadmapDs.replace("-", "")}", "public_url" to JsonNull,
            "properties" to obj(
                "Name" to obj("id" to "title", "name" to "Name", "type" to "title", "title" to obj()),
                "Status" to obj("id" to "stat", "name" to "Status", "type" to "status", "status" to obj("options" to statusOptions, "groups" to JsonArray(listOf(
                    obj("id" to "g1", "name" to "To-do", "color" to "gray", "option_ids" to JsonArray(listOf(JsonPrimitive("opt-" + "Not started".hashCode())))),
                    obj("id" to "g2", "name" to "In progress", "color" to "blue", "option_ids" to JsonArray(listOf(JsonPrimitive("opt-" + "In progress".hashCode()), JsonPrimitive("opt-" + "In review".hashCode())))),
                    obj("id" to "g3", "name" to "Complete", "color" to "green", "option_ids" to JsonArray(listOf(JsonPrimitive("opt-" + "Done".hashCode())))),
                )))),
                "Priority" to obj("id" to "prio", "name" to "Priority", "type" to "select", "select" to obj("options" to JsonArray(listOf(option("P0", "red"), option("P1", "orange"), option("P2", "yellow"), option("P3", "gray"))))),
                "Owner" to obj("id" to "ownr", "name" to "Owner", "type" to "people", "people" to obj()),
                "Due" to obj("id" to "due", "name" to "Due", "type" to "date", "date" to obj()),
                "Effort" to obj("id" to "eff", "name" to "Effort", "type" to "number", "number" to obj("format" to "number")),
                "Tags" to obj("id" to "tags", "name" to "Tags", "type" to "multi_select", "multi_select" to obj("options" to JsonArray(listOf(
                    option("Design", "pink"), option("Android", "green"), option("API", "blue"), option("Performance", "orange"), option("Research", "purple"),
                )))),
                "Shipped" to obj("id" to "shp", "name" to "Shipped", "type" to "checkbox", "checkbox" to obj()),
                "Spec" to obj("id" to "spec", "name" to "Spec", "type" to "url", "url" to obj()),
                "Impact" to obj("id" to "impc", "name" to "Impact", "type" to "formula", "formula" to obj("expression" to "prop(\"Effort\") * 10")),
                "ID" to obj("id" to "uid", "name" to "ID", "type" to "unique_id", "unique_id" to obj("prefix" to "MS")),
                "Created" to obj("id" to "crt", "name" to "Created", "type" to "created_time", "created_time" to obj()),
            ),
        )
        data class Task(val title: String, val status: String, val statusColor: String, val prio: String, val prioColor: String, val owner: String,
                        val start: Long, val end: Long, val effort: Double, val tags: List<Pair<String, String>>, val emoji: String, val cover: String?)
        val tasks = listOf(
            Task("Material 3 Expressive redesign", "In progress", "blue", "P0", "red", ME, -6, 8, 8.0, listOf("Design" to "pink", "Android" to "green"), "🎨", "${IMG}photo-1558591710-4b4a1ae0f04d?w=1200&q=80"),
            Task("Offline reading mode", "In review", "purple", "P1", "orange", ALAN, -12, 2, 5.0, listOf("Android" to "green", "Performance" to "orange"), "📴", "${IMG}photo-1517842645767-c639042777db?w=1200&q=80"),
            Task("Database views API support", "Done", "green", "P0", "red", GRACE, -30, -10, 13.0, listOf("API" to "blue"), "🗂️", "${IMG}photo-1497366216548-37526070297c?w=1200&q=80"),
            Task("Chromebook keyboard shortcuts", "Not started", "default", "P2", "yellow", KATHERINE, 10, 18, 3.0, listOf("Android" to "green"), "⌨️", null),
            Task("Rich text editor", "In progress", "blue", "P0", "red", ME, -3, 12, 13.0, listOf("Android" to "green", "Design" to "pink"), "✍️", "${IMG}photo-1455390582262-044cdead277a?w=1200&q=80"),
            Task("Home screen widgets", "Not started", "default", "P2", "yellow", GRACE, 14, 24, 5.0, listOf("Android" to "green"), "🧩", null),
            Task("File uploads", "Done", "green", "P1", "orange", ALAN, -40, -25, 8.0, listOf("API" to "blue"), "📎", null),
            Task("Search ranking research", "In review", "purple", "P3", "gray", KATHERINE, -8, 4, 2.0, listOf("Research" to "purple"), "🔎", "${IMG}photo-1451187580459-43490279c0fa?w=1200&q=80"),
            Task("Startup performance", "Not started", "default", "P1", "orange", ME, 5, 15, 5.0, listOf("Performance" to "orange"), "⚡", null),
        )
        tasks.forEachIndexed { i, task ->
            val rowId = id("task-$i")
            pages[rowId] = pageJson(
                rowId, task.title, obj("type" to "data_source_id", "data_source_id" to roadmapDs, "database_id" to roadmapDb),
                emoji(task.emoji), task.cover, titleKey = "Name", edited = ago((i * 7 + 3).toLong()), editor = task.owner,
                props = mapOf(
                    "Status" to pStatus("stat", task.status, task.statusColor),
                    "Priority" to pSelect("prio", task.prio, task.prioColor),
                    "Owner" to pPeople("ownr", task.owner),
                    "Due" to pDate("due", day(task.start), day(task.end)),
                    "Effort" to pNum("eff", task.effort),
                    "Tags" to pMulti("tags", *task.tags.toTypedArray()),
                    "Shipped" to pCheck("shp", task.status == "Done"),
                    "Spec" to pUrl("spec", if (i % 2 == 0) "https://m3.material.io/blog/building-with-m3-expressive" else null),
                    "Impact" to pFormulaNum("impc", task.effort * 10),
                    "ID" to pUnique("uid", "MS", 100 + i),
                    "Created" to pCreated("crt", ago(400L - i * 20)),
                ),
            )
            addBlocks(rowId, true, listOf(
                h2("Goal"),
                p("Describe what success looks like for ", t(task.title, bold = true), "."),
                h3("Tasks"),
                todo("Write the spec", true), todo("Build a prototype", task.status != "Not started"), todo("Ship it", task.status == "Done"),
            ))
        }
        fun view(key: String, name: String, type: String, config: JsonObject, sorts: JsonArray? = null, filter: JsonObject? = null, ds: String = roadmapDs, db: String = roadmapDb) {
            val vid = id("view-$key")
            views[vid] = obj(
                "object" to "view", "id" to vid, "parent" to obj("type" to "database_id", "database_id" to db), "name" to name, "type" to type,
                "data_source_id" to ds, "filter" to (filter ?: JsonNull), "sorts" to (sorts ?: JsonNull), "quick_filters" to JsonNull,
                "configuration" to config, "created_time" to ago(300), "last_edited_time" to ago(30),
                "url" to "https://app.notion.com/p/${db.replace("-", "")}?v=${vid.replace("-", "")}",
            )
        }
        fun props(vararg ids: String) = JsonArray(ids.map { obj("property_id" to it, "visible" to true) })
        view("rm-table", "All work", "table", obj("type" to "table", "properties" to props("title", "stat", "prio", "ownr", "due", "tags", "eff", "uid"), "wrap_cells" to false, "frozen_column_index" to 1),
            sorts = JsonArray(listOf(obj("property" to "Priority", "direction" to "ascending"))))
        view("rm-board", "Board", "board", obj("type" to "board", "group_by" to obj("type" to "status", "property_id" to "stat", "group_by" to "option", "sort" to obj("type" to "manual")),
            "properties" to props("prio", "ownr", "tags"), "cover" to obj("type" to "page_cover"), "cover_size" to "medium", "card_layout" to "list"))
        view("rm-timeline", "Timeline", "timeline", obj("type" to "timeline", "date_property_id" to "due", "properties" to props("ownr", "stat"),
            "preference" to obj("zoom_level" to "week"), "show_table" to true, "table_properties" to props("title", "stat")))
        view("rm-calendar", "Calendar", "calendar", obj("type" to "calendar", "date_property_id" to "due", "properties" to props("stat"), "view_range" to "month", "show_weekends" to true))
        view("rm-gallery", "Gallery", "gallery", obj("type" to "gallery", "properties" to props("stat", "ownr"), "cover" to obj("type" to "page_cover"), "cover_size" to "large", "cover_aspect" to "cover"))
        view("rm-mine", "My work", "list", obj("type" to "list", "properties" to props("stat", "due", "prio")),
            filter = obj("property" to "Owner", "people" to obj("contains" to ME)))
        view("rm-chart", "Effort by status", "chart", obj("type" to "chart", "chart_type" to "donut",
            "x_axis" to obj("type" to "status", "property_id" to "stat", "group_by" to "option", "sort" to obj("type" to "manual")),
            "y_axis" to obj("aggregator" to "sum", "property_id" to "eff"), "donut_labels" to "name_and_value", "legend_position" to "side", "show_data_labels" to true))
        view("rm-columns", "Work by priority", "chart", obj("type" to "chart", "chart_type" to "column",
            "x_axis" to obj("type" to "select", "property_id" to "prio", "sort" to obj("type" to "ascending")),
            "y_axis" to obj("aggregator" to "count"), "show_data_labels" to true, "grid_lines" to "horizontal", "color_theme" to "colorful"))

        // Reading list database (full page)
        val readingDb = id("reading-db")
        val readingDs = id("reading-ds")
        databases[readingDb] = obj(
            "object" to "database", "id" to readingDb, "title" to rich("Reading List"), "description" to rich("Books worth your time."),
            "parent" to obj("type" to "workspace", "workspace" to true), "is_inline" to false, "is_locked" to false, "in_trash" to false,
            "data_sources" to JsonArray(listOf(obj("id" to readingDs, "name" to "Books"))), "icon" to emoji("📚"),
            "cover" to obj("type" to "external", "external" to obj("url" to "${IMG}photo-1481627834876-b7833e8f5570?w=1600&q=80")),
            "url" to "https://app.notion.com/p/${readingDb.replace("-", "")}", "public_url" to JsonNull,
            "created_time" to ago(900), "last_edited_time" to ago(26), "database_type" to JsonNull,
        )
        dataSources[readingDs] = obj(
            "object" to "data_source", "id" to readingDs, "title" to rich("Reading List"), "description" to rich("Books worth your time."),
            "parent" to obj("type" to "database_id", "database_id" to readingDb), "database_parent" to obj("type" to "workspace", "workspace" to true),
            "is_inline" to false, "in_trash" to false, "icon" to emoji("📚"),
            "cover" to obj("type" to "external", "external" to obj("url" to "${IMG}photo-1481627834876-b7833e8f5570?w=1600&q=80")),
            "created_time" to ago(900), "last_edited_time" to ago(26), "database_type" to JsonNull,
            "created_by" to userRef(ME), "last_edited_by" to userRef(ME),
            "url" to "https://app.notion.com/p/${readingDs.replace("-", "")}", "public_url" to JsonNull,
            "properties" to obj(
                "Title" to obj("id" to "title", "name" to "Title", "type" to "title", "title" to obj()),
                "Author" to obj("id" to "auth", "name" to "Author", "type" to "rich_text", "rich_text" to obj()),
                "Status" to obj("id" to "rstat", "name" to "Status", "type" to "select", "select" to obj("options" to JsonArray(listOf(option("To read", "gray"), option("Reading", "blue"), option("Finished", "green"))))),
                "Rating" to obj("id" to "rate", "name" to "Rating", "type" to "select", "select" to obj("options" to JsonArray(listOf(option("★★★★★", "yellow"), option("★★★★", "orange"), option("★★★", "brown"))))),
                "Genre" to obj("id" to "genre", "name" to "Genre", "type" to "multi_select", "multi_select" to obj("options" to JsonArray(listOf(option("Sci-fi", "purple"), option("Non-fiction", "blue"), option("Engineering", "green"), option("Psychology", "pink"))))),
                "Finished" to obj("id" to "fin", "name" to "Finished", "type" to "date", "date" to obj()),
                "Pages" to obj("id" to "pgs", "name" to "Pages", "type" to "number", "number" to obj("format" to "number_with_commas")),
            ),
        )
        data class Book(val title: String, val author: String, val isbn: String, val status: String, val statusColor: String, val rating: String?, val genres: List<Pair<String, String>>, val pages: Double, val finished: Long?)
        listOf(
            Book("Dune", "Frank Herbert", "9780441172719", "Finished", "green", "★★★★★", listOf("Sci-fi" to "purple"), 688.0, -120),
            Book("Project Hail Mary", "Andy Weir", "9780593135204", "Reading", "blue", null, listOf("Sci-fi" to "purple"), 496.0, null),
            Book("The Pragmatic Programmer", "Hunt & Thomas", "9780135957059", "Finished", "green", "★★★★", listOf("Engineering" to "green"), 352.0, -60),
            Book("Thinking, Fast and Slow", "Daniel Kahneman", "9780374533557", "To read", "gray", null, listOf("Psychology" to "pink", "Non-fiction" to "blue"), 512.0, null),
            Book("Designing Data-Intensive Applications", "Martin Kleppmann", "9781449373320", "Reading", "blue", null, listOf("Engineering" to "green"), 616.0, null),
            Book("Sapiens", "Yuval Noah Harari", "9780062316097", "Finished", "green", "★★★★", listOf("Non-fiction" to "blue"), 464.0, -200),
            Book("The Left Hand of Darkness", "Ursula K. Le Guin", "9780441478125", "To read", "gray", null, listOf("Sci-fi" to "purple"), 304.0, null),
            Book("Atomic Habits", "James Clear", "9780735211292", "Finished", "green", "★★★", listOf("Psychology" to "pink"), 320.0, -15),
        ).forEachIndexed { i, book ->
            val rowId = id("book-$i")
            val cover = "https://covers.openlibrary.org/b/isbn/${book.isbn}-L.jpg"
            pages[rowId] = pageJson(
                rowId, book.title, obj("type" to "data_source_id", "data_source_id" to readingDs, "database_id" to readingDb),
                emoji("📖"), cover, titleKey = "Title", edited = ago((26 + i * 30).toLong()),
                props = mapOf(
                    "Author" to pText("auth", book.author),
                    "Status" to pSelect("rstat", book.status, book.statusColor),
                    "Rating" to pSelect("rate", book.rating, "yellow"),
                    "Genre" to pMulti("genre", *book.genres.toTypedArray()),
                    "Finished" to pDate("fin", book.finished?.let { day(it) }),
                    "Pages" to pNum("pgs", book.pages),
                ),
            )
            addBlocks(rowId, true, listOf(h2("Highlights"), quote("Notes and favourite passages go here."), h2("Thoughts"), p("What did I learn?")))
        }
        view("rl-gallery", "Shelf", "gallery", obj("type" to "gallery", "properties" to props("auth", "rstat", "rate"), "cover" to obj("type" to "page_cover"), "cover_size" to "medium", "cover_aspect" to "contain"), ds = readingDs, db = readingDb)
        view("rl-table", "All books", "table", obj("type" to "table", "properties" to props("title", "auth", "rstat", "rate", "genre", "pgs", "fin")), ds = readingDs, db = readingDb)
        view("rl-board", "By status", "board", obj("type" to "board", "group_by" to obj("type" to "select", "property_id" to "rstat", "sort" to obj("type" to "manual")), "properties" to props("auth", "rate")), ds = readingDs, db = readingDb)
        view("rl-chart", "Pages by genre", "chart", obj("type" to "chart", "chart_type" to "bar", "x_axis" to obj("type" to "multi_select", "property_id" to "genre", "sort" to obj("type" to "descending")), "y_axis" to obj("aggregator" to "sum", "property_id" to "pgs"), "show_data_labels" to true), ds = readingDs, db = readingDb)

        // Journal database (calendar)
        val journalDb = id("journal-db")
        val journalDs = id("journal-ds")
        addPage(pageJson(journalPage, "Journal", ws, nativeIcon("sun", "yellow"), "${IMG}photo-1470770841072-f978cf4d019e?w=1600&q=80", edited = ago(14)), listOf(
            p("A few lines a day. ", t("Consistency beats intensity.", italic = true)),
            B("child_database", obj("title" to "Entries"), id = journalDb),
        ))
        databases[journalDb] = obj(
            "object" to "database", "id" to journalDb, "title" to rich("Entries"), "description" to JsonArray(emptyList()),
            "parent" to obj("type" to "page_id", "page_id" to journalPage), "is_inline" to true, "is_locked" to false, "in_trash" to false,
            "data_sources" to JsonArray(listOf(obj("id" to journalDs, "name" to "Entries"))), "icon" to emoji("📓"), "cover" to JsonNull,
            "url" to "https://app.notion.com/p/${journalDb.replace("-", "")}", "public_url" to JsonNull,
            "created_time" to ago(800), "last_edited_time" to ago(14), "database_type" to JsonNull,
        )
        dataSources[journalDs] = obj(
            "object" to "data_source", "id" to journalDs, "title" to rich("Entries"), "description" to JsonArray(emptyList()),
            "parent" to obj("type" to "database_id", "database_id" to journalDb), "database_parent" to obj("type" to "page_id", "page_id" to journalPage),
            "is_inline" to true, "in_trash" to false, "icon" to emoji("📓"), "cover" to JsonNull,
            "created_time" to ago(800), "last_edited_time" to ago(14), "database_type" to JsonNull,
            "created_by" to userRef(ME), "last_edited_by" to userRef(ME),
            "url" to "https://app.notion.com/p/${journalDs.replace("-", "")}", "public_url" to JsonNull,
            "properties" to obj(
                "Entry" to obj("id" to "title", "name" to "Entry", "type" to "title", "title" to obj()),
                "Date" to obj("id" to "jdate", "name" to "Date", "type" to "date", "date" to obj()),
                "Mood" to obj("id" to "mood", "name" to "Mood", "type" to "select", "select" to obj("options" to JsonArray(listOf(option("Great", "green"), option("Good", "blue"), option("Okay", "yellow"), option("Rough", "red"))))),
                "Energy" to obj("id" to "enrg", "name" to "Energy", "type" to "number", "number" to obj("format" to "percent")),
            ),
        )
        val moods = listOf("Great" to "green", "Good" to "blue", "Okay" to "yellow", "Good" to "blue", "Rough" to "red", "Great" to "green")
        listOf("Shipped the new editor", "Long walk by the river", "Deep work day", "Team offsite", "Too many meetings", "Read 100 pages of Dune").forEachIndexed { i, title ->
            val rowId = id("journal-$i")
            pages[rowId] = pageJson(
                rowId, title, obj("type" to "data_source_id", "data_source_id" to journalDs, "database_id" to journalDb), emoji(listOf("✨", "🌿", "🧠", "🏕️", "😵‍💫", "📖")[i]),
                titleKey = "Entry", edited = ago((i * 30 + 14).toLong()),
                props = mapOf(
                    "Date" to pDate("jdate", day(-(i * 3).toLong())),
                    "Mood" to pSelect("mood", moods[i].first, moods[i].second),
                    "Energy" to pNum("enrg", listOf(0.9, 0.7, 0.8, 0.95, 0.4, 0.75)[i]),
                ),
            )
            addBlocks(rowId, true, listOf(p("Today I…")))
        }
        view("j-cal", "Calendar", "calendar", obj("type" to "calendar", "date_property_id" to "jdate", "properties" to props("mood"), "view_range" to "month"), ds = journalDs, db = journalDb)
        view("j-list", "All entries", "list", obj("type" to "list", "properties" to props("jdate", "mood")), sorts = JsonArray(listOf(obj("property" to "Date", "direction" to "descending"))), ds = journalDs, db = journalDb)

        // Welcome page — showcases every block type
        addPage(
            pageJson(welcome, "Welcome to Manuscript", ws, emoji("👋"), "${IMG}photo-1506905925346-21bda4d32df4?w=1600&q=80", edited = ago(1)),
            listOf(
                callout("✨", "This is a demo workspace. Everything here works offline — edit, create, filter and explore freely.", color = "purple_background"),
                toc(),
                h1("Writing beautifully"),
                p("Text can be ", t("bold", bold = true), ", ", t("italic", italic = true), ", ", t("underlined", underline = true), ", ",
                    t("struck through", strike = true), ", ", t("inline code", code = true), ", or ", t("colorful", color = "purple"), ". ",
                    "Highlight things ", t("like this", color = "yellow_background"), " and add ", t("links", link = "https://developers.notion.com"), "."),
                p("Mention people like ", mentionUser(GRACE), ", pages like ", mentionPage(roadmapPage, "Product Roadmap"), ", dates like ",
                    mentionDate(day(3)), ", and write math inline: ", eq("E = mc^2"), "."),
                quote("Simplicity is prerequisite for reliability. — Edsger W. Dijkstra"),
                h2("Lists"),
                bullet("Bulleted lists", kids = listOf(bullet("can nest", kids = listOf(bullet("as deep as you need"))))),
                num("Numbered lists"), num("count for you"), num("automatically"),
                todo("Try checking this box", false), todo("Things you've already done", true),
                toggle("Toggles hide details until you need them", listOf(p("Surprise! 🎉 Toggles can hold any content, even ", t("other blocks", bold = true), "."))),
                h2("Toggle heading", toggle = true, kids = listOf(p("Headings can be toggles too."))),
                h2("Code & math"),
                code("kotlin", "@Composable\nfun Greeting(name: String) {\n    Text(\n        text = \"Hello, \$name!\",\n        style = MaterialTheme.typography.displayLargeEmphasized,\n    )\n}", "A Compose function"),
                equation("\\int_0^\\infty e^{-x^2} dx = \\frac{\\sqrt{\\pi}}{2}"),
                h2("Media & embeds"),
                image("${IMG}photo-1519681393784-d120267933ba?w=1600&q=80", "Images open in a zoomable viewer."),
                bookmark("https://m3.material.io/blog/building-with-m3-expressive", "Building with M3 Expressive"),
                h2("Tables & columns"),
                table(true, listOf(listOf("Feature", "Phone", "Chromebook"), listOf("Sidebar", "Drawer", "Always visible"), listOf("Side peek", "Full screen", "Split pane"), listOf("Shortcuts", "—", "⌘ / Ctrl"))),
                columns(
                    listOf(h3("Left column"), p("Columns stack on phones and sit side by side on larger screens.")),
                    listOf(h3("Right column"), callout("📐", "Resize the window to see the layout adapt.", color = "blue_background")),
                ),
                tabs("Overview" to listOf(p("Tabs organise content into sections.")), "Details" to listOf(p("Each tab holds its own blocks."))),
                divider(),
                h3("Keep exploring"),
                linkTo(roadmapPage),
                linkTo(wiki),
            ),
        )
        comments += obj(
            "object" to "comment", "id" to id("comment-1"), "discussion_id" to id("discussion-1"),
            "parent" to obj("type" to "page_id", "page_id" to welcome), "created_time" to ago(20), "last_edited_time" to ago(20),
            "created_by" to userRef(GRACE), "rich_text" to rich("Love how clean this looks on my Chromebook! 😍"),
            "display_name" to obj("type" to "user", "resolved_name" to "Grace Hopper"), "attachments" to JsonArray(emptyList()),
        )
        comments += obj(
            "object" to "comment", "id" to id("comment-2"), "discussion_id" to id("discussion-1"),
            "parent" to obj("type" to "page_id", "page_id" to welcome), "created_time" to ago(18), "last_edited_time" to ago(18),
            "created_by" to userRef(ALAN), "rich_text" to rich("Agreed. The side peek is a game changer."),
            "display_name" to obj("type" to "user", "resolved_name" to "Alan Turing"), "attachments" to JsonArray(emptyList()),
        )

        // Something in the trash
        pages[id("old-draft")] = pageJson(id("old-draft"), "Old launch draft", ws, emoji("🗑️"), edited = ago(500), trash = true)
        addBlocks(id("old-draft"), true, listOf(p("This page was deleted. Restore it from the trash.")))
    }

    // Queries

    fun propertyText(prop: JsonObject?): String = when (prop?.s("type")) {
        "title", "rich_text" -> prop.a(prop.s("type")!!)?.objs()?.joinToString("") { it.s("plain_text") ?: "" } ?: ""
        "select", "status" -> prop.o(prop.s("type")!!)?.s("name") ?: ""
        "url", "email", "phone_number" -> prop.s(prop.s("type")!!) ?: ""
        "multi_select" -> prop.a("multi_select")?.objs()?.joinToString(",") { it.s("name") ?: "" } ?: ""
        else -> ""
    }

    private fun propertyByKey(page: JsonObject, key: String): JsonObject? {
        val props = page.o("properties") ?: return null
        (props[key] as? JsonObject)?.let { return it }
        return props.values.mapNotNull { it as? JsonObject }.firstOrNull { it.s("id") == key }
    }

    fun matches(page: JsonObject, filter: JsonObject?): Boolean {
        if (filter == null) return true
        filter.a("and")?.let { list -> return list.objs().all { matches(page, it) } }
        filter.a("or")?.let { list -> return list.objs().any { matches(page, it) } }
        filter.s("timestamp")?.let { ts ->
            val cond = filter.o(ts) ?: return true
            return dateMatches(page.s(ts)?.take(10), cond)
        }
        val key = filter.s("property") ?: return true
        val prop = propertyByKey(page, key)
        val (kind, condEl) = filter.entries.firstOrNull { it.key != "property" && it.key != "type" } ?: return true
        val cond = condEl as? JsonObject ?: return true
        return when (kind) {
            "title", "rich_text", "url", "email", "phone_number" -> textMatches(propertyText(prop), cond)
            "number" -> numberMatches(prop?.d("number"), cond)
            "checkbox" -> cond.b("equals")?.let { (prop?.b("checkbox") == true) == it } ?: cond.b("does_not_equal")?.let { (prop?.b("checkbox") == true) != it } ?: true
            "select", "status" -> {
                val value = prop?.o(kind)?.s("name")
                when {
                    cond.has("equals") -> valuesOf(cond["equals"]).contains(value)
                    cond.has("does_not_equal") -> !valuesOf(cond["does_not_equal"]).contains(value)
                    cond.b("is_empty") == true -> value == null
                    cond.b("is_not_empty") == true -> value != null
                    else -> true
                }
            }
            "multi_select" -> {
                val names = prop?.a("multi_select")?.objs()?.mapNotNull { it.s("name") } ?: emptyList()
                when {
                    cond.has("contains") -> valuesOf(cond["contains"]).any { it in names }
                    cond.has("does_not_contain") -> valuesOf(cond["does_not_contain"]).none { it in names }
                    cond.b("is_empty") == true -> names.isEmpty()
                    cond.b("is_not_empty") == true -> names.isNotEmpty()
                    else -> true
                }
            }
            "people", "created_by", "last_edited_by" -> {
                val ids = prop?.a("people")?.objs()?.mapNotNull { it.s("id") } ?: listOfNotNull(prop?.o(kind)?.s("id"))
                val target = cond.s("contains")?.let { if (it == "me") ME else it } ?: cond.s("does_not_contain")?.let { if (it == "me") ME else it }
                when {
                    cond.has("contains") -> target in ids
                    cond.has("does_not_contain") -> target !in ids
                    cond.b("is_empty") == true -> ids.isEmpty()
                    cond.b("is_not_empty") == true -> ids.isNotEmpty()
                    else -> true
                }
            }
            "relation" -> {
                val ids = prop?.a("relation")?.objs()?.mapNotNull { it.s("id") } ?: emptyList()
                when {
                    cond.has("contains") -> cond.s("contains") in ids
                    cond.has("does_not_contain") -> cond.s("does_not_contain") !in ids
                    cond.b("is_empty") == true -> ids.isEmpty()
                    cond.b("is_not_empty") == true -> ids.isNotEmpty()
                    else -> true
                }
            }
            "date" -> dateMatches(prop?.o("date")?.s("start")?.take(10), cond)
            "files" -> {
                val n = prop?.a("files")?.size ?: 0
                if (cond.b("is_empty") == true) n == 0 else n > 0
            }
            "formula" -> {
                val f = prop?.o("formula")
                val (sub, inner) = cond.entries.firstOrNull() ?: return true
                val subCond = inner as? JsonObject ?: return true
                when (sub) {
                    "number" -> numberMatches(f?.d("number"), subCond)
                    "string" -> textMatches(f?.s("string") ?: "", subCond)
                    "checkbox" -> subCond.b("equals")?.let { f?.b("boolean") == it } ?: true
                    "date" -> dateMatches(f?.o("date")?.s("start")?.take(10), subCond)
                    else -> true
                }
            }
            "unique_id" -> numberMatches(prop?.o("unique_id")?.d("number"), cond)
            else -> true
        }
    }

    private fun JsonObject.has(key: String) = containsKey(key)

    private fun valuesOf(el: JsonElement?): List<String> = when (el) {
        is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.content }
        is JsonPrimitive -> listOf(el.content)
        else -> emptyList()
    }

    private fun textMatches(value: String, cond: JsonObject): Boolean {
        val v = value.lowercase()
        cond.s("equals")?.let { return v == it.lowercase() }
        cond.s("does_not_equal")?.let { return v != it.lowercase() }
        cond.s("contains")?.let { return v.contains(it.lowercase()) }
        cond.s("does_not_contain")?.let { return !v.contains(it.lowercase()) }
        cond.s("starts_with")?.let { return v.startsWith(it.lowercase()) }
        cond.s("ends_with")?.let { return v.endsWith(it.lowercase()) }
        if (cond.b("is_empty") == true) return v.isEmpty()
        if (cond.b("is_not_empty") == true) return v.isNotEmpty()
        return true
    }

    private fun numberMatches(value: Double?, cond: JsonObject): Boolean {
        if (cond.b("is_empty") == true) return value == null
        if (cond.b("is_not_empty") == true) return value != null
        val n = value ?: return false
        cond.d("equals")?.let { return n == it }
        cond.d("does_not_equal")?.let { return n != it }
        cond.d("greater_than")?.let { return n > it }
        cond.d("less_than")?.let { return n < it }
        cond.d("greater_than_or_equal_to")?.let { return n >= it }
        cond.d("less_than_or_equal_to")?.let { return n <= it }
        return true
    }

    private fun relative(value: String?): LocalDate? = when (value) {
        null -> null
        "today" -> LocalDate.now()
        "tomorrow" -> LocalDate.now().plusDays(1)
        "yesterday" -> LocalDate.now().minusDays(1)
        "one_week_ago" -> LocalDate.now().minusWeeks(1)
        "one_week_from_now" -> LocalDate.now().plusWeeks(1)
        "one_month_ago" -> LocalDate.now().minusMonths(1)
        "one_month_from_now" -> LocalDate.now().plusMonths(1)
        else -> runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
    }

    private fun dateMatches(value: String?, cond: JsonObject): Boolean {
        if (cond.b("is_empty") == true) return value == null
        if (cond.b("is_not_empty") == true) return value != null
        val date = value?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
        val today = LocalDate.now()
        relative(cond.s("equals"))?.let { return date == it }
        relative(cond.s("before"))?.let { return date < it }
        relative(cond.s("after"))?.let { return date > it }
        relative(cond.s("on_or_before"))?.let { return date <= it }
        relative(cond.s("on_or_after"))?.let { return date >= it }
        return when {
            cond.containsKey("past_week") -> date in today.minusWeeks(1)..today
            cond.containsKey("past_month") -> date in today.minusMonths(1)..today
            cond.containsKey("past_year") -> date in today.minusYears(1)..today
            cond.containsKey("next_week") -> date in today..today.plusWeeks(1)
            cond.containsKey("next_month") -> date in today..today.plusMonths(1)
            cond.containsKey("next_year") -> date in today..today.plusYears(1)
            cond.containsKey("this_week") -> date in today.minusDays(today.dayOfWeek.value - 1L)..today.plusDays(7L - today.dayOfWeek.value)
            else -> true
        }
    }

    fun sortKey(page: JsonObject, sort: JsonObject): Comparable<*>? {
        sort.s("timestamp")?.let { return page.s(it) }
        val prop = propertyByKey(page, sort.s("property") ?: return null) ?: return null
        return when (prop.s("type")) {
            "number" -> prop.d("number")
            "date" -> prop.o("date")?.s("start")
            "checkbox" -> if (prop.b("checkbox") == true) 1 else 0
            "unique_id" -> prop.o("unique_id")?.d("number")
            "created_time" -> prop.s("created_time")
            "formula" -> prop.o("formula")?.d("number") ?: prop.o("formula")?.s("string")
            "select", "status" -> {
                val name = prop.o(prop.s("type")!!)?.s("name")
                val ds = page.o("parent")?.s("data_source_id")?.let { dataSources[it] }
                val options = ds?.o("properties")?.values?.mapNotNull { it as? JsonObject }
                    ?.firstOrNull { it.s("id") == prop.s("id") }?.o(prop.s("type")!!)?.a("options")?.objs()?.mapNotNull { it.s("name") }
                options?.indexOf(name)?.takeIf { it >= 0 } ?: name
            }
            else -> propertyText(prop).lowercase().ifEmpty { null }
        }
    }

    fun newId(): String = UUID.randomUUID().toString()
    fun nowIso(): String = Instant.now().toString()
}
