package systems.lupine.sheaf.ui.components.markdown

import android.text.style.ForegroundColorSpan
import java.util.regex.Pattern

/**
 * Tiny regex-based code-block highlighter.
 *
 * The "right" answer here is Prism4j via Markwon's syntax-highlight
 * artifact, but Prism4j ships its grammars through an annotation
 * processor (`prism4j-bundler`) which would force KAPT into the build
 * just for this one feature. Hand-rolled tokenisers cost ~30 lines per
 * language and cover the cases that show up in our markdown surfaces
 * (member bios, journal entries, board messages, scratchpads): the few
 * commonly-used languages plus a sensible fallback that just monospaces
 * unknown blocks.
 *
 * Per-language token rules are applied in declaration order. First
 * match at a position wins; subsequent rules overlap-skip past it.
 * That means specific patterns (triple-quoted strings, block comments)
 * need to come before more general ones (single-quoted strings, line
 * comments) so the longer match takes the prefix. Each rule maps to
 * one of [SyntaxColors]'s slot colours so the same plugin can recolour
 * for light vs dark mode just by passing a different [SyntaxColors].
 */
internal object CodeSyntaxHighlighter {

    /** Highlight [text] starting at absolute [offset] in the host span
     *  container. [applySpan] decouples us from Spannable vs Markwon's
     *  SpannableBuilder — caller adapts both. No-op when [language]
     *  doesn't match any registered grammar. */
    fun highlight(
        offset: Int,
        text: String,
        language: String?,
        colors: SyntaxColors,
        applySpan: (span: Any, start: Int, end: Int) -> Unit,
    ) {
        if (language.isNullOrBlank()) return
        val rules = grammarFor(language) ?: return
        applyRules(offset, text, rules, colors, applySpan)
    }

    /** Normalise common aliases ("kt" -> kotlin etc.) and look up the
     *  rule set. Anything not in the map renders without highlighting. */
    private fun grammarFor(language: String): List<TokenRule>? =
        when (language.trim().lowercase()) {
            "kotlin", "kt" -> KotlinRules
            "java" -> JavaRules
            "python", "py", "py3" -> PythonRules
            "javascript", "js", "node" -> JavaScriptRules
            "typescript", "ts" -> JavaScriptRules  // close enough for our scope
            "json", "json5" -> JsonRules
            "yaml", "yml" -> YamlRules
            "bash", "sh", "shell", "zsh" -> BashRules
            "sql", "psql", "postgres" -> SqlRules
            "html", "xml" -> HtmlRules
            "css", "scss" -> CssRules
            else -> null
        }

    private fun applyRules(
        offset: Int,
        text: String,
        rules: List<TokenRule>,
        colors: SyntaxColors,
        applySpan: (span: Any, start: Int, end: Int) -> Unit,
    ) {
        // Two-pass overlay: mark which characters are already painted so a
        // later rule (e.g. "fun" inside a string) can't recolour them.
        val painted = BooleanArray(text.length)
        for (rule in rules) {
            val matcher = rule.pattern.matcher(text)
            while (matcher.find()) {
                val s = matcher.start()
                val e = matcher.end()
                if (s == e) continue
                var clash = false
                for (i in s until e) {
                    if (painted[i]) { clash = true; break }
                }
                if (clash) continue
                val color = rule.slot.colorIn(colors)
                applySpan(ForegroundColorSpan(color), offset + s, offset + e)
                for (i in s until e) painted[i] = true
            }
        }
    }
}

/** Single tokeniser rule: regex + which colour slot to paint matches in. */
internal data class TokenRule(val pattern: Pattern, val slot: SyntaxSlot)

/**
 * Logical colour slots. Concrete RGB values come from [SyntaxColors] at
 * render time so the same rule set works for both light and dark themes.
 */
internal enum class SyntaxSlot {
    COMMENT, KEYWORD, STRING, NUMBER, FUNCTION, TYPE, ATTRIBUTE, OPERATOR;

    fun colorIn(colors: SyntaxColors): Int = when (this) {
        COMMENT -> colors.comment
        KEYWORD -> colors.keyword
        STRING -> colors.string
        NUMBER -> colors.number
        FUNCTION -> colors.function
        TYPE -> colors.type
        ATTRIBUTE -> colors.attribute
        OPERATOR -> colors.operator
    }
}

/** Resolved per-slot ARGB ints. Built fresh from `ColorScheme` each composition. */
internal data class SyntaxColors(
    val comment: Int,
    val keyword: Int,
    val string: Int,
    val number: Int,
    val function: Int,
    val type: Int,
    val attribute: Int,
    val operator: Int,
)

// ── Per-language token rule sets ──────────────────────────────────────────
//
// Order matters: longer / more-specific patterns first so they take the
// prefix before a shorter / more-general rule grabs it.

private fun p(regex: String, slot: SyntaxSlot): TokenRule = TokenRule(Pattern.compile(regex), slot)

// Common regex pieces, hoisted to avoid duplication across languages.
private const val TripleDoubleString = """\"{3}[\s\S]*?\"{3}"""
private const val TripleSingleString = """'{3}[\s\S]*?'{3}"""
private const val SingleQuoteString  = """'(?:\\.|[^'\\\n])*'"""
private const val DoubleQuoteString  = """\"(?:\\.|[^\"\\\n])*\""""
private const val BacktickString     = """`(?:\\.|[^`\\\n])*`"""
private const val LineCommentSlash   = """//[^\n]*"""
private const val LineCommentHash    = """#[^\n]*"""
private const val BlockCommentSlash  = """/\*[\s\S]*?\*/"""
private const val Number             = """\b\d+(?:\.\d+)?(?:[eE][+-]?\d+)?[fFLljBn]?\b"""
private const val HexNumber          = """\b0[xX][0-9a-fA-F]+[Ll]?\b"""

private val KotlinRules: List<TokenRule> = listOf(
    p(BlockCommentSlash, SyntaxSlot.COMMENT),
    p(LineCommentSlash, SyntaxSlot.COMMENT),
    p(TripleDoubleString, SyntaxSlot.STRING),
    p(DoubleQuoteString, SyntaxSlot.STRING),
    p(SingleQuoteString, SyntaxSlot.STRING),
    p("""\b(fun|val|var|class|object|interface|enum|sealed|data|abstract|open|override|private|protected|internal|public|companion|inner|inline|noinline|crossinline|reified|suspend|tailrec|operator|infix|external|annotation|lateinit|const|init|by|where|in|out|return|throw|try|catch|finally|if|else|when|for|while|do|break|continue|package|import|typealias|null|true|false|this|super|is|as)\b""", SyntaxSlot.KEYWORD),
    p("""\b(String|Int|Long|Short|Byte|Char|Float|Double|Boolean|Any|Unit|Nothing|List|Map|Set|Array|MutableList|MutableMap|MutableSet|Pair|Triple|Result|Sequence|Flow|Job|Deferred|CoroutineScope|StateFlow|MutableStateFlow|SharedFlow|MutableSharedFlow)\b""", SyntaxSlot.TYPE),
    p(HexNumber, SyntaxSlot.NUMBER),
    p(Number, SyntaxSlot.NUMBER),
    p("""@[A-Za-z_][A-Za-z0-9_]*""", SyntaxSlot.ATTRIBUTE),
    p("""\b[a-z_][A-Za-z0-9_]*(?=\s*\()""", SyntaxSlot.FUNCTION),
)

private val JavaRules: List<TokenRule> = listOf(
    p(BlockCommentSlash, SyntaxSlot.COMMENT),
    p(LineCommentSlash, SyntaxSlot.COMMENT),
    p(DoubleQuoteString, SyntaxSlot.STRING),
    p(SingleQuoteString, SyntaxSlot.STRING),
    p("""\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while|true|false|null|var|yield|record|sealed|permits)\b""", SyntaxSlot.KEYWORD),
    p("""\b(String|Integer|Long|Short|Byte|Character|Float|Double|Boolean|Object|Number|List|Map|Set|ArrayList|HashMap|HashSet|LinkedList|LinkedHashMap|TreeMap|Optional|Stream)\b""", SyntaxSlot.TYPE),
    p(HexNumber, SyntaxSlot.NUMBER),
    p(Number, SyntaxSlot.NUMBER),
    p("""@[A-Za-z_][A-Za-z0-9_]*""", SyntaxSlot.ATTRIBUTE),
    p("""\b[a-z_][A-Za-z0-9_]*(?=\s*\()""", SyntaxSlot.FUNCTION),
)

private val PythonRules: List<TokenRule> = listOf(
    p(TripleDoubleString, SyntaxSlot.STRING),
    p(TripleSingleString, SyntaxSlot.STRING),
    p(LineCommentHash, SyntaxSlot.COMMENT),
    p(DoubleQuoteString, SyntaxSlot.STRING),
    p(SingleQuoteString, SyntaxSlot.STRING),
    p("""\b(False|None|True|and|as|assert|async|await|break|class|continue|def|del|elif|else|except|finally|for|from|global|if|import|in|is|lambda|nonlocal|not|or|pass|raise|return|try|while|with|yield|match|case)\b""", SyntaxSlot.KEYWORD),
    p("""\b(str|int|float|bool|bytes|bytearray|complex|dict|list|tuple|set|frozenset|object|type|range|enumerate|map|filter|zip|sorted|reversed|len|abs|min|max|sum|any|all|print|input)\b""", SyntaxSlot.TYPE),
    p(HexNumber, SyntaxSlot.NUMBER),
    p(Number, SyntaxSlot.NUMBER),
    p("""@[A-Za-z_][A-Za-z0-9_.]*""", SyntaxSlot.ATTRIBUTE),
    p("""\b[a-z_][A-Za-z0-9_]*(?=\s*\()""", SyntaxSlot.FUNCTION),
)

private val JavaScriptRules: List<TokenRule> = listOf(
    p(BlockCommentSlash, SyntaxSlot.COMMENT),
    p(LineCommentSlash, SyntaxSlot.COMMENT),
    p(BacktickString, SyntaxSlot.STRING),
    p(DoubleQuoteString, SyntaxSlot.STRING),
    p(SingleQuoteString, SyntaxSlot.STRING),
    p("""\b(break|case|catch|class|const|continue|debugger|default|delete|do|else|enum|export|extends|false|finally|for|function|if|implements|import|in|instanceof|interface|let|new|null|of|package|private|protected|public|return|static|super|switch|this|throw|true|try|typeof|var|void|while|with|yield|async|await|from|as)\b""", SyntaxSlot.KEYWORD),
    p("""\b(Array|Object|String|Number|Boolean|Symbol|BigInt|Promise|Map|Set|WeakMap|WeakSet|Date|RegExp|Error|JSON|Math|console|window|document|undefined)\b""", SyntaxSlot.TYPE),
    p(HexNumber, SyntaxSlot.NUMBER),
    p(Number, SyntaxSlot.NUMBER),
    p("""\b[a-z_${'$'}][A-Za-z0-9_${'$'}]*(?=\s*\()""", SyntaxSlot.FUNCTION),
)

private val JsonRules: List<TokenRule> = listOf(
    p(BlockCommentSlash, SyntaxSlot.COMMENT),
    p(LineCommentSlash, SyntaxSlot.COMMENT),
    // Object keys (quoted, followed by ":") take the attribute slot so
    // they read as labels rather than values.
    p("""\"(?:\\.|[^\"\\\n])*\"(?=\s*:)""", SyntaxSlot.ATTRIBUTE),
    p(DoubleQuoteString, SyntaxSlot.STRING),
    p("""\b(true|false|null)\b""", SyntaxSlot.KEYWORD),
    p(Number, SyntaxSlot.NUMBER),
)

private val YamlRules: List<TokenRule> = listOf(
    p(LineCommentHash, SyntaxSlot.COMMENT),
    p("""(?m)^\s*[A-Za-z_][\w-]*(?=\s*:)""", SyntaxSlot.ATTRIBUTE),
    p(DoubleQuoteString, SyntaxSlot.STRING),
    p(SingleQuoteString, SyntaxSlot.STRING),
    p("""\b(true|false|null|yes|no|on|off)\b""", SyntaxSlot.KEYWORD),
    p(Number, SyntaxSlot.NUMBER),
)

private val BashRules: List<TokenRule> = listOf(
    p(LineCommentHash, SyntaxSlot.COMMENT),
    p(DoubleQuoteString, SyntaxSlot.STRING),
    p(SingleQuoteString, SyntaxSlot.STRING),
    p("""\b(if|then|elif|else|fi|case|esac|for|in|do|done|while|until|function|return|break|continue|local|export|readonly|declare|typeset|unset|shift|eval|exec|exit|trap|set|source|cd|pwd|true|false)\b""", SyntaxSlot.KEYWORD),
    p("""\${'$'}\{[^}]+\}|\${'$'}[A-Za-z_][A-Za-z0-9_]*|\${'$'}\d+""", SyntaxSlot.ATTRIBUTE),
    p(Number, SyntaxSlot.NUMBER),
)

private val SqlRules: List<TokenRule> = listOf(
    p("""--[^\n]*""", SyntaxSlot.COMMENT),
    p(BlockCommentSlash, SyntaxSlot.COMMENT),
    p(DoubleQuoteString, SyntaxSlot.STRING),
    p(SingleQuoteString, SyntaxSlot.STRING),
    // SQL keywords are case-insensitive in the wild but we render the
    // user's input verbatim — the regex uses (?i) so SELECT / select / Select
    // all paint the same.
    p("""(?i)\b(select|from|where|group|by|order|having|limit|offset|insert|into|values|update|set|delete|create|table|drop|alter|index|view|trigger|primary|key|foreign|references|unique|not|null|default|with|as|union|intersect|except|join|inner|left|right|full|outer|on|case|when|then|else|end|and|or|in|like|between|is|asc|desc|distinct|all|exists|cast|true|false|begin|commit|rollback|transaction)\b""", SyntaxSlot.KEYWORD),
    p(Number, SyntaxSlot.NUMBER),
)

private val HtmlRules: List<TokenRule> = listOf(
    p("""<!--[\s\S]*?-->""", SyntaxSlot.COMMENT),
    p(DoubleQuoteString, SyntaxSlot.STRING),
    p(SingleQuoteString, SyntaxSlot.STRING),
    p("""</?\s*[A-Za-z][\w-]*""", SyntaxSlot.KEYWORD),
    p("""\b[a-z][\w-]*(?=\s*=)""", SyntaxSlot.ATTRIBUTE),
)

private val CssRules: List<TokenRule> = listOf(
    p(BlockCommentSlash, SyntaxSlot.COMMENT),
    p(DoubleQuoteString, SyntaxSlot.STRING),
    p(SingleQuoteString, SyntaxSlot.STRING),
    p("""[A-Za-z-]+(?=\s*:)""", SyntaxSlot.ATTRIBUTE),
    p("""[.#:][A-Za-z][\w-]*""", SyntaxSlot.FUNCTION),
    p("""\b\d+(?:\.\d+)?(?:px|em|rem|%|vh|vw|s|ms|deg)?\b""", SyntaxSlot.NUMBER),
    p("""#[0-9a-fA-F]{3,8}\b""", SyntaxSlot.NUMBER),
)
