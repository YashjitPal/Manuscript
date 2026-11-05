package com.manuscript.ui.components

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.manuscript.core.model.Annotations
import com.manuscript.core.model.Hue
import com.manuscript.core.model.Mention
import com.manuscript.core.model.NIcon
import com.manuscript.core.model.NodeKind
import com.manuscript.core.model.RichText
import com.manuscript.core.util.Fmt
import com.manuscript.ui.theme.GoogleSansCodeFamily
import com.manuscript.ui.theme.LocalNotionPalette
import com.manuscript.ui.theme.NewsreaderFamily
import com.manuscript.ui.theme.NotionPalette

/** Actions for links and mentions inside rich text. Provided once near the root of the app. */
@Immutable
class LinkHandler(
    val openPage: (String) -> Unit = {},
    val openDatabase: (String) -> Unit = {},
    val openUrl: (String) -> Unit = {},
    val openUser: (String) -> Unit = {},
    val iconFor: (String) -> NIcon? = { null },
)

val LocalLinkHandler = staticCompositionLocalOf { LinkHandler() }

@Immutable
data class RichColors(
    val link: Color,
    val mention: Color,
    val muted: Color,
    val codeText: Color,
    val codeBackground: Color,
)

@Composable
fun richColors(): RichColors {
    val scheme = MaterialTheme.colorScheme
    val palette = LocalNotionPalette.current
    return RichColors(
        link = scheme.primary,
        mention = scheme.onSurface,
        muted = scheme.onSurfaceVariant,
        codeText = palette.accent(Hue.RED),
        codeBackground = scheme.surfaceContainerHighest,
    )
}

fun Annotations.toSpanStyle(palette: NotionPalette, colors: RichColors): SpanStyle {
    val decorations = buildList {
        if (underline) add(TextDecoration.Underline)
        if (strikethrough) add(TextDecoration.LineThrough)
    }
    val text = palette.textColor(color)
    val bg = palette.backgroundColor(color)
    return SpanStyle(
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
        color = when {
            code && text == Color.Unspecified -> colors.codeText
            else -> text
        },
        background = when {
            bg != Color.Unspecified -> bg
            code -> colors.codeBackground
            else -> Color.Unspecified
        },
        fontFamily = if (code) GoogleSansCodeFamily else null,
        fontSize = if (code) 0.88.em else androidx.compose.ui.unit.TextUnit.Unspecified,
    )
}

/** Builds the styled text for a rich text array, plus inline content for icons and custom emoji. */
fun buildRich(
    rich: List<RichText>,
    palette: NotionPalette,
    colors: RichColors,
    links: LinkHandler,
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val inline = LinkedHashMap<String, InlineTextContent>()
    val text = buildAnnotatedString {
        rich.forEachIndexed { index, rt ->
            val style = rt.annotations.toSpanStyle(palette, colors)
            when {
                rt.equation != null -> withStyle(style.merge(SpanStyle(fontFamily = NewsreaderFamily, fontStyle = FontStyle.Italic))) {
                    append(latexToUnicode(rt.equation))
                }
                rt.mention != null -> appendMention(rt, rt.mention, index, style, palette, colors, links, inline)
                rt.link != null -> {
                    val url = rt.link
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = url,
                            styles = TextLinkStyles(
                                style = SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline),
                                pressedStyle = SpanStyle(background = colors.link.copy(alpha = 0.16f)),
                            ),
                            linkInteractionListener = { links.openUrl(url) },
                        ),
                    ) { withStyle(style) { append(rt.text) } }
                }
                else -> withStyle(style) { append(rt.text) }
            }
        }
    }
    return text to inline
}

private fun AnnotatedString.Builder.appendMention(
    rt: RichText,
    mention: Mention,
    index: Int,
    style: SpanStyle,
    palette: NotionPalette,
    colors: RichColors,
    links: LinkHandler,
    inline: MutableMap<String, InlineTextContent>,
) {
    val pressed = SpanStyle(background = colors.link.copy(alpha = 0.14f))
    fun clickable(tag: String, spanStyle: SpanStyle, action: () -> Unit, block: AnnotatedString.Builder.() -> Unit) {
        withLink(LinkAnnotation.Clickable(tag, TextLinkStyles(style = spanStyle, pressedStyle = pressed), { action() })) { block() }
    }
    when (mention) {
        is Mention.Page, is Mention.Database -> {
            val id = (mention as? Mention.Page)?.id ?: (mention as Mention.Database).id
            val isDb = mention is Mention.Database
            val key = "icon:$index:$id"
            val icon = links.iconFor(id)
            inline[key] = InlineTextContent(Placeholder(1.25.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
                NotionIcon(icon, 16.dp, kind = if (isDb) NodeKind.DATABASE else NodeKind.PAGE, fallbackTint = colors.muted)
            }
            clickable(id, style.merge(SpanStyle(fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline, color = colors.mention)), {
                if (isDb) links.openDatabase(id) else links.openPage(id)
            }) {
                appendInlineContent(key, "▢")
                append(rt.text.ifBlank { "Untitled" })
            }
        }
        is Mention.User -> clickable("user:${mention.id}", style.merge(SpanStyle(color = colors.link, fontWeight = FontWeight.SemiBold)), {
            links.openUser(mention.id)
        }) { append(if (rt.text.startsWith("@")) rt.text else "@${mention.name ?: rt.text}") }
        is Mention.Date -> withStyle(style.merge(SpanStyle(color = colors.muted, fontWeight = FontWeight.Medium))) {
            append("@" + Fmt.date(mention.date))
        }
        is Mention.TemplateDate -> withStyle(style.merge(SpanStyle(color = colors.muted))) {
            append(if (mention.value == "now") "@Now" else "@Today")
        }
        is Mention.TemplateUser -> withStyle(style.merge(SpanStyle(color = colors.link))) { append("@Me") }
        is Mention.LinkPreview -> clickable(mention.url, style.merge(SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline)), {
            links.openUrl(mention.url)
        }) { append(rt.text.ifBlank { mention.url }) }
        is Mention.LinkMention -> clickable(mention.href, style.merge(SpanStyle(color = colors.link, fontWeight = FontWeight.Medium)), {
            links.openUrl(mention.href)
        }) {
            mention.iconUrl?.let { url ->
                val key = "favicon:$index"
                inline[key] = InlineTextContent(Placeholder(1.2.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
                    NotionIcon(NIcon.External(url), 14.dp)
                }
                appendInlineContent(key, "▢")
            }
            append(mention.title ?: rt.text.ifBlank { mention.href })
        }
        is Mention.CustomEmoji -> {
            val key = "emoji:$index"
            inline[key] = InlineTextContent(Placeholder(1.2.em, 1.2.em, PlaceholderVerticalAlign.TextCenter)) {
                NotionIcon(NIcon.CustomEmoji(mention.id, mention.name, mention.url), 18.dp)
            }
            appendInlineContent(key, ":${mention.name}:")
        }
        is Mention.Unknown -> withStyle(style) { append(rt.text) }
    }
}

@Composable
fun RichTextView(
    rich: List<RichText>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    placeholder: String? = null,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val palette = LocalNotionPalette.current
    val colors = richColors()
    val links = LocalLinkHandler.current
    val (text, inline) = remember(rich, palette, colors, links) { buildRich(rich, palette, colors, links) }
    if (text.isEmpty() && placeholder != null) {
        Text(placeholder, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = modifier, maxLines = maxLines)
    } else {
        Text(
            text = text,
            style = style,
            color = color,
            modifier = modifier,
            maxLines = maxLines,
            overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
            inlineContent = inline,
            onTextLayout = onTextLayout,
        )
    }
}

private val latexSymbols = linkedMapOf(
    "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ", "\\epsilon" to "ε", "\\varepsilon" to "ε", "\\zeta" to "ζ",
    "\\eta" to "η", "\\theta" to "θ", "\\iota" to "ι", "\\kappa" to "κ", "\\lambda" to "λ", "\\mu" to "μ", "\\nu" to "ν", "\\xi" to "ξ",
    "\\pi" to "π", "\\rho" to "ρ", "\\sigma" to "σ", "\\tau" to "τ", "\\phi" to "φ", "\\varphi" to "φ", "\\chi" to "χ", "\\psi" to "ψ",
    "\\omega" to "ω", "\\Gamma" to "Γ", "\\Delta" to "Δ", "\\Theta" to "Θ", "\\Lambda" to "Λ", "\\Pi" to "Π", "\\Sigma" to "Σ",
    "\\Phi" to "Φ", "\\Psi" to "Ψ", "\\Omega" to "Ω", "\\infty" to "∞", "\\int" to "∫", "\\sum" to "∑", "\\prod" to "∏",
    "\\partial" to "∂", "\\nabla" to "∇", "\\cdot" to "·", "\\times" to "×", "\\div" to "÷", "\\pm" to "±", "\\mp" to "∓",
    "\\leq" to "≤", "\\le" to "≤", "\\geq" to "≥", "\\ge" to "≥", "\\neq" to "≠", "\\ne" to "≠", "\\approx" to "≈", "\\equiv" to "≡",
    "\\sim" to "∼", "\\propto" to "∝", "\\rightarrow" to "→", "\\to" to "→", "\\leftarrow" to "←", "\\Rightarrow" to "⇒",
    "\\Leftrightarrow" to "⇔", "\\in" to "∈", "\\notin" to "∉", "\\subset" to "⊂", "\\subseteq" to "⊆", "\\cup" to "∪",
    "\\cap" to "∩", "\\forall" to "∀", "\\exists" to "∃", "\\emptyset" to "∅", "\\neg" to "¬", "\\land" to "∧", "\\lor" to "∨",
    "\\sqrt" to "√", "\\degree" to "°", "\\circ" to "∘", "\\ldots" to "…", "\\dots" to "…", "\\cdots" to "⋯", "\\hbar" to "ℏ",
    "\\ell" to "ℓ", "\\Re" to "ℜ", "\\Im" to "ℑ", "\\angle" to "∠", "\\perp" to "⊥", "\\parallel" to "∥", "\\quad" to "  ",
    "\\," to " ", "\\;" to " ", "\\!" to "", "\\left" to "", "\\right" to "", "\\mathrm" to "", "\\text" to "", "\\mathbf" to "",
)
private val superscripts = mapOf('0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷',
    '8' to '⁸', '9' to '⁹', '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾', 'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'y' to 'ʸ')
private val subscripts = mapOf('0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅', '6' to '₆', '7' to '₇',
    '8' to '₈', '9' to '₉', '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎', 'a' to 'ₐ', 'e' to 'ₑ', 'i' to 'ᵢ',
    'j' to 'ⱼ', 'n' to 'ₙ', 'x' to 'ₓ', 'k' to 'ₖ', 'm' to 'ₘ', 't' to 'ₜ')

/** A lightweight LaTeX-to-Unicode conversion for inline equations. */
fun latexToUnicode(latex: String): String {
    var s = latex
    s = Regex("""\\frac\{([^{}]*)\}\{([^{}]*)\}""").replace(s) { "${it.groupValues[1]}⁄${it.groupValues[2]}" }
    latexSymbols.entries.sortedByDescending { it.key.length }.forEach { (k, v) -> s = s.replace(k, v) }
    s = Regex("""\^\{([^{}]*)\}""").replace(s) { m -> m.groupValues[1].map { superscripts[it] ?: it }.joinToString("") }
    s = Regex("""\^(.)""").replace(s) { m -> (superscripts[m.groupValues[1][0]] ?: m.groupValues[1][0]).toString() }
    s = Regex("""_\{([^{}]*)\}""").replace(s) { m -> m.groupValues[1].map { subscripts[it] ?: it }.joinToString("") }
    s = Regex("""_(.)""").replace(s) { m -> (subscripts[m.groupValues[1][0]] ?: m.groupValues[1][0]).toString() }
    return s.replace("{", "").replace("}", "")
}
