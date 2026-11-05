package com.manuscript.core.util

import com.manuscript.core.model.BlockType
import com.manuscript.core.model.Mention
import com.manuscript.core.model.NBlock
import com.manuscript.core.model.NIcon
import com.manuscript.core.model.RichText

/** Converts a block tree into GitHub-flavoured Markdown for export and sharing. */
object MarkdownExport {

    fun page(title: String, icon: NIcon?, blocks: List<NBlock>): String = buildString {
        val emoji = (icon as? NIcon.Emoji)?.emoji?.let { "$it " } ?: ""
        appendLine("# $emoji$title".trimEnd())
        appendLine()
        append(blocks(blocks))
    }.trimEnd() + "\n"

    fun blocks(blocks: List<NBlock>, indent: String = ""): String = buildString {
        var number = 0
        blocks.forEach { b ->
            number = if (b.type == BlockType.NUMBERED) number + 1 else 0
            append(block(b, indent, number))
        }
    }

    private fun block(b: NBlock, indent: String, number: Int): String = buildString {
        val text = rich(b.rich)
        val childIndent = "$indent    "
        when (b.type) {
            BlockType.PARAGRAPH -> { appendLine("$indent$text"); appendLine() }
            BlockType.HEADING_1 -> { appendLine("$indent# $text"); appendLine() }
            BlockType.HEADING_2 -> { appendLine("$indent## $text"); appendLine() }
            BlockType.HEADING_3 -> { appendLine("$indent### $text"); appendLine() }
            BlockType.HEADING_4 -> { appendLine("$indent#### $text"); appendLine() }
            BlockType.BULLETED, BlockType.TOGGLE -> appendLine("$indent- $text")
            BlockType.NUMBERED -> appendLine("$indent${b.listStart?.takeIf { number == 1 } ?: number}. $text")
            BlockType.TO_DO -> appendLine("$indent- [${if (b.checked) "x" else " "}] $text")
            BlockType.QUOTE -> { appendLine("$indent> $text"); appendLine() }
            BlockType.CALLOUT -> {
                val emoji = (b.icon as? NIcon.Emoji)?.emoji?.let { "$it " } ?: ""
                appendLine("$indent> $emoji$text"); appendLine()
            }
            BlockType.CODE -> {
                appendLine("$indent```${b.language?.takeIf { it != "plain text" } ?: ""}")
                b.plainText.lines().forEach { appendLine("$indent$it") }
                appendLine("$indent```"); appendLine()
            }
            BlockType.EQUATION -> { appendLine("$indent$$${b.expression}$$"); appendLine() }
            BlockType.DIVIDER -> { appendLine("$indent---"); appendLine() }
            BlockType.IMAGE -> { appendLine("$indent![${rich(b.caption)}](${b.file?.url ?: b.url ?: ""})"); appendLine() }
            BlockType.VIDEO, BlockType.AUDIO, BlockType.PDF, BlockType.FILE ->
                { appendLine("$indent[${b.file?.displayName ?: b.type.label}](${b.file?.url ?: b.url ?: ""})"); appendLine() }
            BlockType.BOOKMARK, BlockType.EMBED, BlockType.LINK_PREVIEW ->
                { appendLine("$indent[${rich(b.caption).ifBlank { b.url ?: "" }}](${b.url ?: ""})"); appendLine() }
            BlockType.CHILD_PAGE, BlockType.CHILD_DATABASE ->
                { appendLine("$indent[${b.title?.ifBlank { "Untitled" } ?: "Untitled"}](${NotionIds.webUrl(b.id)})"); appendLine() }
            BlockType.LINK_TO_PAGE -> (b.linkPageId ?: b.linkDatabaseId)?.let {
                appendLine("$indent[Linked page](${NotionIds.webUrl(it)})"); appendLine()
            }
            BlockType.TABLE -> {
                val rows = b.children.filter { it.type == BlockType.TABLE_ROW }
                val width = b.tableWidth.coerceAtLeast(1)
                rows.forEachIndexed { i, row ->
                    val cells = (0 until width).map { c -> row.cells.getOrNull(c)?.let(::rich)?.replace("|", "\\|")?.replace("\n", " ") ?: "" }
                    appendLine("$indent| ${cells.joinToString(" | ")} |")
                    if (i == 0) appendLine("$indent|${" --- |".repeat(width)}")
                }
                appendLine()
            }
            BlockType.COLUMN_LIST, BlockType.COLUMN, BlockType.SYNCED_BLOCK, BlockType.TAB -> append(blocks(b.children, indent))
            BlockType.TABLE_OF_CONTENTS, BlockType.BREADCRUMB, BlockType.TABLE_ROW -> Unit
            else -> if (text.isNotBlank()) { appendLine("$indent$text"); appendLine() }
        }
        val nested = b.children.isNotEmpty() && b.type !in setOf(
            BlockType.TABLE, BlockType.COLUMN_LIST, BlockType.COLUMN, BlockType.SYNCED_BLOCK, BlockType.TAB,
        )
        if (nested) append(blocks(b.children, if (b.type.isListItem) childIndent else indent))
        if (b.type.isListItem && !nested) Unit
    }

    fun rich(items: List<RichText>): String = items.joinToString("") { rt ->
        var s = when {
            rt.equation != null -> "$${rt.equation}$"
            rt.mention is Mention.Page || rt.mention is Mention.Database -> {
                val id = (rt.mention as? Mention.Page)?.id ?: (rt.mention as Mention.Database).id
                "[${rt.text}](${NotionIds.webUrl(id)})"
            }
            else -> rt.text
        }
        if (rt.equation == null && rt.mention == null && s.isNotBlank()) {
            val a = rt.annotations
            if (a.code) s = "`$s`"
            if (a.bold) s = "**$s**"
            if (a.italic) s = "*$s*"
            if (a.strikethrough) s = "~~$s~~"
            rt.link?.let { s = "[$s]($it)" }
        }
        s
    }
}
