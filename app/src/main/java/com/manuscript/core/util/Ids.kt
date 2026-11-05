package com.manuscript.core.util

import android.net.Uri

object NotionIds {
    private val hex32 = Regex("([0-9a-fA-F]{32})")
    private val uuid = Regex("([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})")

    fun normalize(raw: String): String {
        val compact = raw.replace("-", "").lowercase()
        if (compact.length != 32) return raw
        return buildString {
            append(compact, 0, 8); append('-')
            append(compact, 8, 12); append('-')
            append(compact, 12, 16); append('-')
            append(compact, 16, 20); append('-')
            append(compact, 20, 32)
        }
    }

    fun same(a: String?, b: String?): Boolean =
        a != null && b != null && a.replace("-", "").equals(b.replace("-", ""), ignoreCase = true)

    data class Link(val id: String, val viewId: String? = null, val blockId: String? = null)

    /** Extracts the record ID from Notion links such as notion.so, app.notion.com/p/… and notion.site. */
    fun parseLink(url: String): Link? {
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return idFrom(url)?.let { Link(it) }
        val isNotion = host.endsWith("notion.so") || host.endsWith("notion.com") || host.endsWith("notion.site")
        if (!isNotion && uri.scheme != "notion") return null
        val path = uri.path ?: ""
        val id = path.split('/').asReversed().firstNotNullOfOrNull { idFrom(it) } ?: return null
        val view = uri.getQueryParameter("v")?.let { idFrom(it) }
        val block = uri.fragment?.let { idFrom(it) }
        return Link(id, view, block)
    }

    fun idFrom(text: String): String? {
        uuid.findAll(text).lastOrNull()?.let { return it.value.lowercase() }
        hex32.findAll(text).lastOrNull()?.let { return normalize(it.value) }
        return null
    }

    fun webUrl(id: String): String = "https://app.notion.com/p/${id.replace("-", "")}"
}
