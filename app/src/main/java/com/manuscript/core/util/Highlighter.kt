package com.manuscript.core.util

/** A small, fast, regex-based syntax highlighter for code blocks. */
object CodeHighlighter {

    enum class Kind { KEYWORD, STRING, COMMENT, NUMBER, TYPE, FUNCTION, ANNOTATION, CONSTANT, TAG }

    data class Token(val start: Int, val end: Int, val kind: Kind)

    private val cLike = setOf(
        "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "return", "try", "catch", "finally",
        "throw", "new", "class", "interface", "enum", "extends", "implements", "import", "package", "public", "private",
        "protected", "static", "final", "abstract", "const", "void", "this", "super", "null", "true", "false", "typeof",
        "instanceof", "in", "of", "as", "is", "struct", "union", "sizeof", "goto", "volatile", "extern", "inline", "namespace",
        "using", "virtual", "override", "template", "typename", "operator", "delete", "async", "await", "yield", "static_cast",
    )

    private val keywords: Map<String, Set<String>> = mapOf(
        "kotlin" to setOf(
            "fun", "val", "var", "class", "object", "interface", "data", "sealed", "enum", "if", "else", "when", "for", "while",
            "do", "return", "break", "continue", "try", "catch", "finally", "throw", "import", "package", "private", "public",
            "internal", "protected", "override", "open", "abstract", "companion", "suspend", "inline", "reified", "in", "out",
            "is", "as", "null", "true", "false", "this", "super", "typealias", "lateinit", "by", "init", "constructor", "where",
            "operator", "infix", "tailrec", "vararg", "crossinline", "noinline", "const", "annotation", "value",
        ),
        "java" to cLike + setOf("boolean", "int", "long", "double", "float", "char", "byte", "short", "synchronized", "throws", "record", "var"),
        "javascript" to cLike + setOf("function", "let", "var", "const", "export", "from", "undefined", "debugger", "with", "get", "set", "static"),
        "typescript" to cLike + setOf("function", "let", "var", "const", "export", "from", "undefined", "type", "readonly", "keyof", "declare", "any", "never", "unknown", "number", "string", "boolean", "infer"),
        "python" to setOf(
            "def", "class", "if", "elif", "else", "for", "while", "return", "import", "from", "as", "try", "except", "finally",
            "raise", "with", "lambda", "yield", "pass", "break", "continue", "in", "is", "not", "and", "or", "None", "True",
            "False", "global", "nonlocal", "assert", "del", "async", "await", "self", "match", "case",
        ),
        "swift" to cLike + setOf("func", "let", "var", "guard", "protocol", "extension", "init", "deinit", "self", "Self", "nil", "inout", "mutating", "where", "some", "any", "weak", "unowned"),
        "go" to setOf("func", "package", "import", "var", "const", "type", "struct", "interface", "map", "chan", "go", "defer", "select", "if", "else", "for", "range", "return", "switch", "case", "default", "break", "continue", "nil", "true", "false", "fallthrough"),
        "rust" to setOf("fn", "let", "mut", "struct", "enum", "impl", "trait", "pub", "use", "mod", "crate", "self", "Self", "match", "if", "else", "loop", "while", "for", "in", "return", "break", "continue", "as", "ref", "move", "async", "await", "dyn", "where", "unsafe", "true", "false", "const", "static", "type"),
        "c" to cLike + setOf("int", "char", "float", "double", "long", "short", "unsigned", "signed", "auto", "register", "typedef"),
        "c++" to cLike + setOf("int", "char", "float", "double", "long", "short", "unsigned", "auto", "bool", "nullptr", "constexpr", "noexcept", "friend", "mutable", "explicit"),
        "c#" to cLike + setOf("int", "string", "bool", "var", "get", "set", "readonly", "sealed", "partial", "foreach", "lock", "out", "ref", "params", "base", "event", "delegate"),
        "ruby" to setOf("def", "end", "class", "module", "if", "elsif", "else", "unless", "while", "until", "for", "in", "do", "return", "yield", "begin", "rescue", "ensure", "raise", "self", "nil", "true", "false", "and", "or", "not", "then", "require", "attr_accessor"),
        "php" to cLike + setOf("function", "echo", "foreach", "elseif", "endif", "array", "fn", "match", "use", "trait"),
        "sql" to setOf("select", "from", "where", "and", "or", "not", "insert", "into", "values", "update", "set", "delete", "create", "table", "drop", "alter", "join", "left", "right", "inner", "outer", "on", "group", "by", "order", "having", "limit", "offset", "as", "distinct", "union", "all", "null", "is", "in", "like", "between", "case", "when", "then", "else", "end", "primary", "key", "foreign", "references", "index", "with", "asc", "desc", "count", "sum", "avg", "min", "max"),
        "bash" to setOf("if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac", "function", "return", "in", "export", "local", "echo", "exit", "source", "alias", "unset", "readonly", "shift"),
        "dart" to cLike + setOf("final", "var", "dynamic", "late", "required", "factory", "mixin", "with", "get", "set", "Future", "Stream"),
        "lua" to setOf("function", "local", "if", "then", "elseif", "else", "end", "for", "while", "do", "repeat", "until", "return", "break", "in", "and", "or", "not", "nil", "true", "false"),
        "scala" to setOf("def", "val", "var", "class", "object", "trait", "extends", "with", "if", "else", "match", "case", "for", "yield", "while", "return", "import", "package", "new", "this", "null", "true", "false", "implicit", "lazy", "sealed", "override"),
        "haskell" to setOf("module", "import", "where", "let", "in", "case", "of", "if", "then", "else", "data", "type", "newtype", "class", "instance", "deriving", "do"),
        "elixir" to setOf("def", "defp", "defmodule", "do", "end", "if", "else", "case", "cond", "fn", "when", "with", "import", "alias", "use", "true", "false", "nil"),
        "yaml" to setOf("true", "false", "null", "yes", "no"),
        "json" to setOf("true", "false", "null"),
        "css" to setOf("important", "media", "import", "keyframes", "from", "to"),
    )

    private val aliases = mapOf(
        "shell" to "bash", "powershell" to "bash", "docker" to "bash", "makefile" to "bash", "js" to "javascript",
        "flow" to "javascript", "java/c/c++/c#" to "java", "objective-c" to "c", "scss" to "css", "sass" to "css",
        "less" to "css", "groovy" to "java", "solidity" to "javascript", "graphql" to "javascript", "toml" to "yaml",
        "r" to "python", "julia" to "python", "matlab" to "python", "vb.net" to "c#", "visual basic" to "c#",
    )

    private fun lineComment(lang: String): String? = when (lang) {
        "python", "bash", "ruby", "yaml", "elixir", "r", "perl", "coffeescript", "nix", "julia" -> "#"
        "sql", "lua", "haskell", "elm", "ada" -> "--"
        "json", "html", "xml", "markdown", "css" -> null
        "latex", "erlang", "matlab" -> "%"
        else -> "//"
    }

    private val numberRegex = Regex("""\b(0x[0-9a-fA-F_]+|\d[\d_]*(\.\d+)?([eE][+-]?\d+)?[fFLuU]?)\b""")
    private val identRegex = Regex("""[A-Za-z_][A-Za-z0-9_]*""")

    fun highlight(code: String, language: String?): List<Token> {
        val raw = language?.lowercase() ?: "plain text"
        if (raw == "plain text" || raw == "ascii art" || code.length > 60_000) return emptyList()
        val lang = aliases[raw] ?: raw
        val tokens = mutableListOf<Token>()
        val occupied = BooleanArray(code.length)
        fun mark(start: Int, end: Int, kind: Kind) {
            if (start >= end || start < 0 || end > code.length) return
            for (i in start until end) if (occupied[i]) return
            for (i in start until end) occupied[i] = true
            tokens += Token(start, end, kind)
        }
        // Block comments and strings first so keywords inside them are ignored.
        if (lang in setOf("html", "xml", "markup", "markdown")) {
            Regex("""<!--[\s\S]*?-->""").findAll(code).forEach { mark(it.range.first, it.range.last + 1, Kind.COMMENT) }
            Regex("""</?[A-Za-z][\w:-]*""").findAll(code).forEach { mark(it.range.first, it.range.last + 1, Kind.TAG) }
            Regex("""\s([\w:-]+)=""").findAll(code).forEach { m -> m.groups[1]?.range?.let { mark(it.first, it.last + 1, Kind.ANNOTATION) } }
        } else if (lineComment(lang) != "#" || lang == "python") {
            Regex("""/\*[\s\S]*?\*/""").findAll(code).forEach { mark(it.range.first, it.range.last + 1, Kind.COMMENT) }
        }
        if (lang == "python") Regex("\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''").findAll(code).forEach { mark(it.range.first, it.range.last + 1, Kind.STRING) }
        lineComment(lang)?.let { marker ->
            Regex(Regex.escape(marker) + ".*").findAll(code).forEach { mark(it.range.first, it.range.last + 1, Kind.COMMENT) }
        }
        Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'|`(?:\\\\.|[^`\\\\])*`").findAll(code).forEach {
            mark(it.range.first, it.range.last + 1, Kind.STRING)
        }
        Regex("""@[A-Za-z_][\w.]*""").findAll(code).forEach { mark(it.range.first, it.range.last + 1, Kind.ANNOTATION) }
        numberRegex.findAll(code).forEach { mark(it.range.first, it.range.last + 1, Kind.NUMBER) }
        val words = keywords[lang] ?: keywords["javascript"]!!
        val caseInsensitive = lang == "sql"
        identRegex.findAll(code).forEach { m ->
            val word = m.value
            val start = m.range.first
            val end = m.range.last + 1
            when {
                (if (caseInsensitive) word.lowercase() else word) in words -> mark(start, end, Kind.KEYWORD)
                word.length > 1 && word.all { it.isUpperCase() || it == '_' || it.isDigit() } && word.any { it.isLetter() } -> mark(start, end, Kind.CONSTANT)
                word[0].isUpperCase() -> mark(start, end, Kind.TYPE)
                end < code.length && code[end] == '(' -> mark(start, end, Kind.FUNCTION)
            }
        }
        if (lang == "css") Regex("""[\w-]+(?=\s*:)""").findAll(code).forEach { mark(it.range.first, it.range.last + 1, Kind.ANNOTATION) }
        if (lang == "json") Regex("\"(?:\\\\.|[^\"\\\\])*\"(?=\\s*:)").findAll(code).forEach { mark(it.range.first, it.range.last + 1, Kind.TAG) }
        return tokens.sortedBy { it.start }
    }
}
