package com.mohithash.byok.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow

/**
 * The inline Markdown models write inside JSON strings: `**bold**`, `*italic*` / `_italic_`, `***both***`, `` `code` ``,
 * `~~strike~~`, `[label](https://…)`, `## heading` lines and `* item` / `- item` bullets.
 *
 * A small CommonMark-style delimiter parser: markers that don't pair up stay literal, `snake_case_words` and maths like
 * `2*3*4` are left alone, and no input makes it throw. Pure Kotlin (no Compose types) so it is unit-testable;
 * [toAnnotatedString] / [InlineMarkdownText] draw the result.
 */
object InlineMarkdown {
    enum class Style { Bold, Italic, Strike, Code, Link }

    /** [style] applies to `text[start until end]`; [url] is set for [Style.Link]. */
    data class Span(val start: Int, val end: Int, val style: Style, val url: String = "")

    /** Text with the markers removed, and the styles they stood for. */
    data class Parsed(val text: String, val spans: List<Span>)

    /** [src] without inline Markdown markers. */
    fun plain(src: String): String = parse(src).text

    fun parse(src: String): Parsed {
        if (src.none { it in SPECIAL }) return Parsed(src, emptyList())
        val toks = tokenize(src)
        val matches = pairDelimiters(toks)
        return render(toks, matches)
    }

    private const val SPECIAL = "*_`~[\\#-+"
    private const val ESCAPABLE = "*_`~[]#"
    private val SAFE_URL = Regex("^(https?://|mailto:)[^\\s<>\"]+$", RegexOption.IGNORE_CASE)

    private sealed class Tok
    private class Lit(val s: String) : Tok()
    private class Code(val s: String) : Tok()
    private class Link(val label: Parsed, val url: String) : Tok()
    private class Heading(val open: Boolean) : Tok()
    private class Delim(val ch: Char, val len: Int, val canOpen: Boolean, val canClose: Boolean, val block: Int) : Tok() {
        var left = len
        val opens = ArrayList<Match>(1)
        val closes = ArrayList<Match>(1)
    }
    private class Match(val style: Style) { var start = -1; var end = -1 }

    private fun tokenize(s: String): List<Tok> {
        val toks = ArrayList<Tok>()
        val lit = StringBuilder()
        fun flush() { if (lit.isNotEmpty()) { toks += Lit(lit.toString()); lit.setLength(0) } }
        val n = s.length
        var i = 0
        var lineStart = true
        var heading = false
        var block = 0 // emphasis never pairs across headings, list items or blank lines
        while (i < n) {
            if (lineStart) {
                lineStart = false
                var j = i
                while (j < n && (s[j] == ' ' || s[j] == '\t')) j++
                if (j + 1 < n && s[j] in "*-+" && (s[j + 1] == ' ' || s[j + 1] == '\t')) {
                    lit.append(s, i, j).append("• ")
                    i = j + 2
                    while (i < n && (s[i] == ' ' || s[i] == '\t')) i++
                    block++
                    continue
                }
                if (j < n && s[j] == '#') {
                    var h = j
                    while (h < n && s[h] == '#') h++
                    if (h - j in 2..6 && h < n && s[h] == ' ') {
                        lit.append(s, i, j); flush()
                        toks += Heading(true); heading = true; block++
                        i = h
                        while (i < n && s[i] == ' ') i++
                        continue
                    }
                }
            }
            val c = s[i]
            when {
                c == '\n' -> {
                    if (heading) { flush(); toks += Heading(false); heading = false; block++ }
                    lit.append('\n'); i++; lineStart = true
                    var j = i
                    while (j < n && (s[j] == ' ' || s[j] == '\t' || s[j] == '\r')) j++
                    if (j >= n || s[j] == '\n') block++
                }
                c == '\\' && i + 1 < n && s[i + 1] in ESCAPABLE -> { lit.append(s[i + 1]); i += 2 }
                c == '`' -> {
                    val k = runLength(s, i, '`')
                    val close = findBacktickRun(s, i + k, k)
                    if (close < 0) { lit.append(s, i, i + k); i += k } else {
                        var code = s.substring(i + k, close).replace('\n', ' ')
                        if (code.length >= 2 && code.startsWith(' ') && code.endsWith(' ') && code.isNotBlank()) code = code.substring(1, code.length - 1)
                        flush(); toks += Code(code); i = close + k
                    }
                }
                c == '[' -> {
                    val link = linkAt(s, i)
                    if (link == null) { lit.append(c); i++ } else {
                        flush(); toks += Link(parse(link.first), link.second); i = link.third
                    }
                }
                c == '*' || c == '_' || c == '~' -> {
                    val k = runLength(s, i, c)
                    val d = delimiter(s, i, k, c, block)
                    if (d == null) lit.append(s, i, i + k) else { flush(); toks += d }
                    i += k
                }
                else -> { lit.append(c); i++ }
            }
        }
        flush()
        if (heading) toks += Heading(false)
        return toks
    }

    private fun runLength(s: String, from: Int, c: Char): Int { var j = from; while (j < s.length && s[j] == c) j++; return j - from }

    /** Index of the next run of exactly [k] backticks at or after [from], or -1. */
    private fun findBacktickRun(s: String, from: Int, k: Int): Int {
        var j = from
        while (j < s.length) {
            if (s[j] == '`') { val m = runLength(s, j, '`'); if (m == k) return j; j += m } else j++
        }
        return -1
    }

    /** `[label](url)` starting at [i]: (label, url, index after it) when the url is http(s) or mailto. */
    private fun linkAt(s: String, i: Int): Triple<String, String, Int>? {
        val rb = s.indexOf(']', i + 1)
        if (rb < 0 || rb + 1 >= s.length || s[rb + 1] != '(') return null
        val label = s.substring(i + 1, rb)
        if (label.isBlank() || '[' in label || '\n' in label) return null
        var depth = 0
        var j = rb + 2
        while (j < s.length) {
            val c = s[j]
            if (c == '(') depth++ else if (c == ')') { if (depth == 0) break; depth-- } else if (c == '\n' || c == ' ') return null
            j++
        }
        if (j >= s.length) return null
        val url = s.substring(rb + 2, j)
        return if (SAFE_URL.matches(url)) Triple(label, url, j + 1) else null
    }

    private fun isWs(cp: Int) = Character.isWhitespace(cp) || Character.isSpaceChar(cp)
    private fun isPunct(cp: Int): Boolean {
        if (cp < 128) return (cp in 33..47) || (cp in 58..64) || (cp in 91..96) || (cp in 123..126)
        return when (Character.getType(cp).toByte()) {
            Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION, Character.END_PUNCTUATION,
            Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION, Character.OTHER_PUNCTUATION,
            Character.MATH_SYMBOL, Character.CURRENCY_SYMBOL, Character.MODIFIER_SYMBOL, Character.OTHER_SYMBOL -> true
            else -> false
        }
    }

    /** A delimiter run of [k] × [c] at [i], or null when it can neither open nor close (so it is plain text). */
    private fun delimiter(s: String, i: Int, k: Int, c: Char, block: Int): Delim? {
        if (c == '~' && k != 2) return null
        val prev = if (i > 0) Character.codePointBefore(s, i) else -1
        val next = if (i + k < s.length) Character.codePointAt(s, i + k) else -1
        val prevWs = prev < 0 || isWs(prev); val nextWs = next < 0 || isWs(next)
        val prevP = prev >= 0 && isPunct(prev); val nextP = next >= 0 && isPunct(next)
        val leftFlank = !nextWs && (!nextP || prevWs || prevP)
        val rightFlank = !prevWs && (!prevP || nextWs || nextP)
        var open: Boolean; var close: Boolean
        when (c) {
            '_' -> { open = leftFlank && (!rightFlank || prevP); close = rightFlank && (!leftFlank || nextP) }
            else -> { open = leftFlank; close = rightFlank }
        }
        if (c == '*' && prev >= 0 && next >= 0 && Character.isLetterOrDigit(prev) && Character.isLetterOrDigit(next) &&
            (k == 1 || Character.isDigit(prev) || Character.isDigit(next))) { open = false; close = false } // 2*3*4, x*y, 2**8
        return if (open || close) Delim(c, k, open, close, block) else null
    }

    /** CommonMark's "process emphasis": pairs closers with the nearest compatible opener. */
    private fun pairDelimiters(toks: List<Tok>): List<Match> {
        val stack = toks.filterIsInstance<Delim>().toMutableList()
        val matches = ArrayList<Match>()
        // CommonMark's openers_bottom: once a closer of some kind found no opener, later closers of that kind stop
        // searching there, so a long run of unmatched markers stays linear instead of quadratic.
        val bottoms = HashMap<Int, Delim>()
        var ci = 0
        while (ci < stack.size) {
            val closer = stack[ci]
            if (!closer.canClose || closer.left == 0) { ci++; continue }
            val kind = (closer.ch.code shl 3) or ((closer.len % 3) shl 1) or (if (closer.canOpen) 1 else 0)
            val bottom = bottoms[kind]
            var oi = ci - 1
            while (oi >= 0 && stack[oi] !== bottom) {
                val o = stack[oi]
                if (o.ch == closer.ch && o.canOpen && o.left > 0 && o.block == closer.block) {
                    val ruleOf3 = (o.canClose || closer.canOpen) && (o.len + closer.len) % 3 == 0 && !(o.len % 3 == 0 && closer.len % 3 == 0)
                    val tilde = o.ch != '~' || (o.left >= 2 && closer.left >= 2)
                    if (!ruleOf3 && tilde) break
                }
                oi--
            }
            if (oi < 0 || stack[oi] === bottom) {
                if (ci > 0) bottoms[kind] = stack[ci - 1] else bottoms.remove(kind)
                if (closer.canOpen) ci++ else stack.removeAt(ci) // a closer that can't open is plain text from here on
                continue
            }
            val o = stack[oi]
            val use = if (o.ch == '~' || (o.left >= 2 && closer.left >= 2)) 2 else 1
            val m = Match(when { o.ch == '~' -> Style.Strike; use == 2 -> Style.Bold; else -> Style.Italic })
            matches += m; o.opens += m; closer.closes += m
            o.left -= use; closer.left -= use
            for (k in ci - 1 downTo oi + 1) stack.removeAt(k) // delimiters inside the pair are now literal
            ci = oi + 1
            if (o.left == 0) { stack.removeAt(oi); ci-- }
            if (closer.left == 0) stack.removeAt(ci)
        }
        return matches
    }

    private fun render(toks: List<Tok>, matches: List<Match>): Parsed {
        val out = StringBuilder()
        val spans = ArrayList<Span>()
        var headingStart = -1
        for (t in toks) when (t) {
            is Lit -> out.append(t.s)
            is Code -> { val st = out.length; out.append(t.s); if (out.length > st) spans += Span(st, out.length, Style.Code) }
            is Link -> {
                val st = out.length; out.append(t.label.text)
                t.label.spans.forEach { spans += it.copy(start = it.start + st, end = it.end + st) }
                if (out.length > st) spans += Span(st, out.length, Style.Link, t.url)
            }
            is Heading -> if (t.open) headingStart = out.length else {
                if (headingStart in 0 until out.length) spans += Span(headingStart, out.length, Style.Bold)
                headingStart = -1
            }
            is Delim -> {
                // A run closes from its left and opens from its right; what's left over in the middle stays literal.
                t.closes.forEach { it.end = out.length }
                repeat(t.left) { out.append(t.ch) }
                t.opens.asReversed().forEach { it.start = out.length }
            }
        }
        matches.forEach { if (it.start >= 0 && it.end > it.start) spans += Span(it.start, it.end, it.style) }
        return Parsed(out.toString(), spans.sortedWith(compareBy({ it.start }, { -it.end })))
    }
}

/** Draws parsed inline Markdown. [codeBackground] tints `code`; links use [linkColor] and open in the browser. */
fun InlineMarkdown.Parsed.toAnnotatedString(codeBackground: Color = Color.Unspecified, linkColor: Color = Color.Unspecified): AnnotatedString {
    if (spans.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        spans.forEach { sp ->
            when (sp.style) {
                InlineMarkdown.Style.Bold -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), sp.start, sp.end)
                InlineMarkdown.Style.Italic -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), sp.start, sp.end)
                InlineMarkdown.Style.Strike -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), sp.start, sp.end)
                InlineMarkdown.Style.Code -> addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground), sp.start, sp.end)
                InlineMarkdown.Style.Link -> addLink(
                    LinkAnnotation.Url(sp.url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))), sp.start, sp.end,
                )
            }
        }
    }
}

/** [text] with inline Markdown rendered, parsed once per text and colour. */
@Composable
fun rememberInlineMarkdown(text: String, color: Color = Color.Unspecified): AnnotatedString {
    val content = color.takeOrElse { LocalContentColor.current }
    val link = if (color.isSpecified) color else MaterialTheme.colorScheme.primary
    return remember(text, content, link) { InlineMarkdown.parse(text).toAnnotatedString(content.copy(alpha = 0.12f), link) }
}

/** A [Text] that renders inline Markdown (bold, italic, code, strike, links). */
@Composable
fun InlineMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textDecoration: TextDecoration? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val resolved = color.takeOrElse { style.color }
    Text(rememberInlineMarkdown(text, resolved), modifier, color = resolved, style = style, textDecoration = textDecoration, maxLines = maxLines, overflow = overflow)
}
