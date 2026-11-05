package com.manuscript.core.model

import androidx.compose.runtime.Immutable

enum class BlockType(val api: String, val label: String) {
    PARAGRAPH("paragraph", "Text"),
    HEADING_1("heading_1", "Heading 1"),
    HEADING_2("heading_2", "Heading 2"),
    HEADING_3("heading_3", "Heading 3"),
    HEADING_4("heading_4", "Heading 4"),
    BULLETED("bulleted_list_item", "Bulleted list"),
    NUMBERED("numbered_list_item", "Numbered list"),
    TO_DO("to_do", "To-do list"),
    TOGGLE("toggle", "Toggle list"),
    QUOTE("quote", "Quote"),
    CALLOUT("callout", "Callout"),
    CODE("code", "Code"),
    EQUATION("equation", "Block equation"),
    DIVIDER("divider", "Divider"),
    IMAGE("image", "Image"),
    VIDEO("video", "Video"),
    AUDIO("audio", "Audio"),
    PDF("pdf", "PDF"),
    FILE("file", "File"),
    BOOKMARK("bookmark", "Web bookmark"),
    EMBED("embed", "Embed"),
    LINK_PREVIEW("link_preview", "Link preview"),
    CHILD_PAGE("child_page", "Page"),
    CHILD_DATABASE("child_database", "Database"),
    LINK_TO_PAGE("link_to_page", "Link to page"),
    TABLE("table", "Table"),
    TABLE_ROW("table_row", "Table row"),
    COLUMN_LIST("column_list", "Columns"),
    COLUMN("column", "Column"),
    SYNCED_BLOCK("synced_block", "Synced block"),
    TEMPLATE("template", "Template button"),
    TABLE_OF_CONTENTS("table_of_contents", "Table of contents"),
    BREADCRUMB("breadcrumb", "Breadcrumb"),
    TAB("tab", "Tabs"),
    MEETING_NOTES("meeting_notes", "Meeting notes"),
    UNSUPPORTED("unsupported", "Unsupported"),
    UNKNOWN("unknown", "Unknown");

    val isHeading: Boolean get() = this == HEADING_1 || this == HEADING_2 || this == HEADING_3 || this == HEADING_4
    val isListItem: Boolean get() = this == BULLETED || this == NUMBERED || this == TO_DO || this == TOGGLE
    val isMedia: Boolean get() = this == IMAGE || this == VIDEO || this == AUDIO || this == PDF || this == FILE

    /** Blocks whose main content is an editable rich_text array. */
    val isText: Boolean
        get() = this == PARAGRAPH || isHeading || isListItem || this == QUOTE || this == CALLOUT || this == CODE

    /** Text blocks that can be converted into one another without losing data. */
    val isConvertibleText: Boolean get() = isText && this != CODE

    val supportsColor: Boolean get() = isConvertibleText || this == TABLE_OF_CONTENTS

    companion object {
        fun of(api: String?): BlockType = entries.firstOrNull { it.api == api }
            ?: when (api) {
                "transcription" -> MEETING_NOTES
                null -> UNKNOWN
                else -> UNSUPPORTED
            }
    }
}

@Immutable
data class MeetingInfo(
    val status: String? = null,
    val summaryBlockId: String? = null,
    val notesBlockId: String? = null,
    val transcriptBlockId: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val attendees: List<String> = emptyList(),
)

@Immutable
data class NBlock(
    val id: String,
    val type: BlockType,
    val rawType: String = type.api,
    val parent: Parent = Parent.Unknown,
    val hasChildren: Boolean = false,
    val inTrash: Boolean = false,
    val createdTime: String? = null,
    val lastEditedTime: String? = null,
    val createdBy: String? = null,
    val lastEditedBy: String? = null,
    val rich: List<RichText> = emptyList(),
    val color: NColor = NColor.Default,
    val checked: Boolean = false,
    val toggleable: Boolean = false,
    val icon: NIcon? = null,
    val language: String? = null,
    val caption: List<RichText> = emptyList(),
    val file: NFile? = null,
    val url: String? = null,
    val expression: String? = null,
    val title: String? = null,
    val tableWidth: Int = 0,
    val hasColumnHeader: Boolean = false,
    val hasRowHeader: Boolean = false,
    val cells: List<List<RichText>> = emptyList(),
    val widthRatio: Float? = null,
    val syncedFrom: String? = null,
    val linkPageId: String? = null,
    val linkDatabaseId: String? = null,
    val listStart: Int? = null,
    val listFormat: String? = null,
    val meeting: MeetingInfo? = null,
    val unsupportedType: String? = null,
    val children: List<NBlock> = emptyList(),
    val pending: Boolean = false,
) {
    val plainText: String get() = rich.plainText()

    /** Whether nested blocks are allowed under this block when appending children. */
    val acceptsChildren: Boolean
        get() = type == BlockType.PARAGRAPH || type.isListItem || type == BlockType.QUOTE || type == BlockType.CALLOUT ||
            (type.isHeading && toggleable) || type == BlockType.SYNCED_BLOCK || type == BlockType.COLUMN ||
            type == BlockType.TEMPLATE

    fun walk(): Sequence<NBlock> = sequence {
        yield(this@NBlock)
        children.forEach { yieldAll(it.walk()) }
    }
}

fun List<NBlock>.walkAll(): Sequence<NBlock> = asSequence().flatMap { it.walk() }

/** Supported code languages, as accepted by the Notion API. */
val CodeLanguages = listOf(
    "plain text", "abap", "abc", "agda", "arduino", "ascii art", "assembly", "bash", "basic", "bnf", "c", "c#", "c++",
    "clojure", "coffeescript", "coq", "css", "dart", "dhall", "diff", "docker", "ebnf", "elixir", "elm", "erlang", "f#",
    "flow", "fortran", "gherkin", "glsl", "go", "graphql", "groovy", "haskell", "hcl", "html", "idris", "java",
    "javascript", "json", "julia", "kotlin", "latex", "less", "lisp", "livescript", "llvm ir", "lua", "makefile",
    "markdown", "markup", "matlab", "mathematica", "mermaid", "nix", "notion formula", "objective-c", "ocaml", "pascal",
    "perl", "php", "powershell", "prolog", "protobuf", "purescript", "python", "r", "racket", "reason", "ruby", "rust",
    "sass", "scala", "scheme", "scss", "shell", "smalltalk", "solidity", "sql", "swift", "toml", "typescript", "vb.net",
    "verilog", "vhdl", "visual basic", "webassembly", "xml", "yaml", "java/c/c++/c#",
)
