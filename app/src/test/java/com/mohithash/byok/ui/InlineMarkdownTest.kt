package com.mohithash.byok.ui

import com.mohithash.byok.ui.InlineMarkdown.Span
import com.mohithash.byok.ui.InlineMarkdown.Style.Bold
import com.mohithash.byok.ui.InlineMarkdown.Style.Code
import com.mohithash.byok.ui.InlineMarkdown.Style.Italic
import com.mohithash.byok.ui.InlineMarkdown.Style.Link
import com.mohithash.byok.ui.InlineMarkdown.Style.Strike
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class InlineMarkdownTest {
    private fun p(s: String) = InlineMarkdown.parse(s)
    private fun literal(s: String) { val r = p(s); assertEquals(s, r.text); assertEquals(emptyList<Span>(), r.spans) }
    private fun styled(r: InlineMarkdown.Parsed, style: InlineMarkdown.Style) = r.spans.filter { it.style == style }.map { r.text.substring(it.start, it.end) }

    @Test fun plainTextIsUntouched() {
        literal("Just a sentence, with punctuation! (and parens) 100% sure.")
        literal("")
    }

    @Test fun boldItalicAndCode() {
        val r = p("Use **two** eggs, *not* _three_, and `salt`.")
        assertEquals("Use two eggs, not three, and salt.", r.text)
        assertEquals(listOf("two"), styled(r, Bold))
        assertEquals(listOf("not", "three"), styled(r, Italic))
        assertEquals(listOf("salt"), styled(r, Code))
    }

    @Test fun underscoreBoldAndStrike() {
        val r = p("__Tip:__ ~~old~~ new")
        assertEquals("Tip: old new", r.text)
        assertEquals(listOf("Tip:"), styled(r, Bold))
        assertEquals(listOf("old"), styled(r, Strike))
    }

    @Test fun tripleIsBoldAndItalic() {
        val r = p("***Both*** here")
        assertEquals("Both here", r.text)
        assertEquals(listOf("Both"), styled(r, Bold))
        assertEquals(listOf("Both"), styled(r, Italic))
    }

    @Test fun nestedEmphasis() {
        val r = p("**bold with *italic* inside**")
        assertEquals("bold with italic inside", r.text)
        assertEquals(listOf("bold with italic inside"), styled(r, Bold))
        assertEquals(listOf("italic"), styled(r, Italic))
        val r2 = p("*a **b** c*")
        assertEquals("a b c", r2.text)
        assertEquals(listOf("a b c"), styled(r2, Italic))
        assertEquals(listOf("b"), styled(r2, Bold))
    }

    @Test fun unclosedMarkersStayLiteral() {
        literal("**unclosed bold")
        literal("an *open italic")
        literal("a `tick")
        literal("closing only** here")
        val r = p("**bold** and **dangling")
        assertEquals("bold and **dangling", r.text)
        assertEquals(listOf("bold"), styled(r, Bold))
        val r2 = p("*a**")
        assertEquals("a*", r2.text)
        assertEquals(listOf("a"), styled(r2, Italic))
    }

    @Test fun asteriskBulletsBecomeBulletsNotItalic() {
        val r = p("* first\n* second *with* style")
        assertEquals("• first\n• second with style", r.text)
        assertEquals(listOf("with"), styled(r, Italic))
        assertEquals("• one\n  • two", p("- one\n  + two").text)
        // Emphasis never pairs across list items.
        assertTrue(p("* a*\n* b*").spans.isEmpty())
    }

    @Test fun mathIsNotItalic() {
        literal("2*3*4 = 24")
        literal("2 * 3 * 4 = 24")
        literal("area = l*w*h")
        literal("2**8 is 256")
        literal("5 * x")
    }

    @Test fun snakeCaseIsNotItalic() {
        literal("set max_tokens_per_request and snake_case_words")
        literal("file_name_v2.txt")
        val r = p("_real italic_ next to snake_case")
        assertEquals("real italic next to snake_case", r.text)
        assertEquals(listOf("real italic"), styled(r, Italic))
    }

    @Test fun spacedDelimitersAreLiteral() {
        literal("a ** b ** c")
        literal("rated 5* by **")
        literal("****")
    }

    @Test fun codeSpansAreLiteralInside() {
        val r = p("Run `a*b*c` and ``x ` y``")
        assertEquals("Run a*b*c and x ` y", r.text)
        assertEquals(listOf("a*b*c", "x ` y"), styled(r, Code))
        assertTrue(styled(r, Italic).isEmpty())
    }

    @Test fun escapesAreLiteral() {
        val r = p("\\*not italic\\* and C:\\Users\\me")
        assertEquals("*not italic* and C:\\Users\\me", r.text)
        assertTrue(r.spans.isEmpty())
    }

    @Test fun safeLinksOnly() {
        val r = p("See [the guide](https://example.com/a_(b)?q=1) now")
        assertEquals("See the guide now", r.text)
        val link = r.spans.single { it.style == Link }
        assertEquals("the guide", r.text.substring(link.start, link.end))
        assertEquals("https://example.com/a_(b)?q=1", link.url)
        literal("[click](javascript:alert(1))")
        literal("[x](not a url)")
        literal("[no link] here")
        val r2 = p("[**Bold** link](https://a.b)")
        assertEquals("Bold link", r2.text)
        assertEquals(listOf("Bold"), styled(r2, Bold))
    }

    @Test fun headingLinesBecomeBold() {
        val r = p("## Prep\nChop **onions**.\n# not a heading")
        assertEquals("Prep\nChop onions.\n# not a heading", r.text)
        assertEquals(listOf("Prep", "onions"), styled(r, Bold))
    }

    @Test fun emphasisDoesNotCrossBlankLines() {
        literal("**start\n\nend**")
        val r = p("**a\nb**")
        assertEquals(listOf("a\nb"), styled(r, Bold))
    }

    @Test fun nonLatinText() {
        val r = p("这是**重要**的")
        assertEquals("这是重要的", r.text)
        assertEquals(listOf("重要"), styled(r, Bold))
        val r2 = p("**🍳 Cook** now")
        assertEquals(listOf("🍳 Cook"), styled(r2, Bold))
    }

    @Test fun pathologicalInputStaysFast() {
        val inputs = listOf("a* ".repeat(20000), "*a ".repeat(20000), "*a **b _c ".repeat(8000), "**a* ".repeat(15000) + "b**", "`".repeat(5000) + "x" + "``".repeat(3000))
        val started = System.nanoTime()
        inputs.forEach { InlineMarkdown.parse(it) }
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue("took $ms ms", ms < 3000)
    }

    @Test fun neverCrashesAndSpansAreInBounds() {
        val alphabet = "*_`~[]()\\#-+ ab1\n•éé🍳".toList()
        val rnd = Random(42)
        repeat(4000) {
            val s = buildString { repeat(rnd.nextInt(0, 40)) { append(alphabet[rnd.nextInt(alphabet.size)]) } }
            val r = InlineMarkdown.parse(s)
            r.spans.forEach { sp -> assertTrue("$s → $sp", sp.start in 0 until sp.end && sp.end <= r.text.length) }
            assertTrue(s, r.text.length <= s.length + 1)
        }
    }
}
