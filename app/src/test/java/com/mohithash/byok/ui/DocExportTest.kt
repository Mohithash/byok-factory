package com.mohithash.byok.ui

import com.mohithash.byok.engine.Card
import com.mohithash.byok.engine.Doc
import com.mohithash.byok.engine.KV
import com.mohithash.byok.engine.Section
import com.mohithash.byok.engine.checklistProgress
import com.mohithash.byok.engine.toSpeech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocExportTest {
    private val doc = Doc(
        title = "**Weekly** plan: A/B?",
        summary = "Eat *well* & <rest>.",
        sections = listOf(
            Section(kind = "text", heading = "Intro", text = "Use `olive oil` and see [notes](https://ex.com/?a=1&b=2)."),
            Section(kind = "checklist", heading = "Buy", items = listOf("**Eggs**", "Rice")),
            Section(kind = "cards", cards = listOf(Card("**Soup**", "Hot _and_ quick", "20 min"))),
            Section(kind = "kv", kv = listOf(KV("**Cost**", "£5"))),
            Section(kind = "table", rows = listOf(listOf("Item", "Qty"), listOf("*Milk*", "1"))),
        ),
        tags = listOf("quick"),
    )

    @Test fun fileNamesAreSafe() {
        assertEquals("Weekly plan A B.md", docExportFileName(doc.title, "md"))
        assertEquals("result.md", docExportFileName("  ***  ", "md"))
        assertEquals("result.pdf", docExportFileName("", "pdf"))
        assertEquals("Line one two", docExportBaseName("Line one\ntwo"))
        assertTrue(docExportBaseName("x".repeat(200)).length <= 60)
    }

    @Test fun withoutInlineMarkdownKeepsStructure() {
        val p = doc.withoutInlineMarkdown()
        assertEquals("Weekly plan: A/B?", p.title)
        assertEquals("Eat well & <rest>.", p.summary)
        assertEquals(listOf("Eggs", "Rice"), p.sections[1].items)
        assertEquals("Soup", p.sections[2].cards[0].title)
        assertEquals("Hot and quick", p.sections[2].cards[0].body)
        assertEquals("Cost", p.sections[3].kv[0].k)
        assertEquals("Milk", p.sections[4].rows[1][0])
        assertEquals(doc.checklistProgress(setOf("1:0")), p.checklistProgress(setOf("1:0")))
        assertFalse(p.toSpeech().contains("*"))
    }

    @Test fun sectionCopyUsesOnlyThatSectionsTicks() {
        val p = doc.withoutInlineMarkdown()
        assertEquals("BUY\n☑ Eggs\n☐ Rice", p.sectionPlainText(1, setOf("1:0", "0:1")))
        assertEquals("", p.sectionPlainText(99, emptySet()))
    }

    @Test fun printHtmlRendersInlineMarkdown() {
        val html = doc.toPrintHtml("Pantry <Pal>", "#D9481F", setOf("1:1"))
        assertTrue(html.contains("<title>Weekly plan: A/B?</title>"))
        assertTrue(html.contains("Eat <em>well</em> &amp; &lt;rest&gt;."))
        assertTrue(html.contains("<code>olive oil</code>"))
        assertTrue(html.contains("<a href=\"https://ex.com/?a=1&amp;b=2\">notes</a>"))
        assertTrue(html.contains("<strong>Eggs</strong>"))
        assertTrue(html.contains("☑ Rice"))
        assertTrue(html.contains("<strong><strong>Soup</strong></strong>"))
        assertTrue(html.contains("<td><em>Milk</em></td>"))
        assertTrue(html.contains("Pantry &lt;Pal&gt;"))
        assertFalse(html.contains("**"))
        assertFalse(html.any { it in ''..'' })
    }

    @Test fun markersInModelOutputCannotInjectTags() {
        val evil = Doc(title = "t", summary = "javascript:xhi")
        val html = evil.toPrintHtml("App", "#000000")
        assertFalse(html.contains("<a href"))
        assertTrue(html.contains("javascript:xhi"))
    }

    @Test fun markersNestProperly() {
        assertEquals("x y", htmlMarkers("***x*** y").let { s ->
            // Bold and italic over the same range: either order is fine as long as they nest.
            if (s.startsWith("")) s.replace("", "").replace("", "") else s
        })
        assertEquals("plain", htmlMarkers("plain"))
    }

    @Test fun hexColour() {
        assertEquals("#336699", docHexColor(0xFF336699.toInt()))
        assertEquals("#000000", docHexColor(0xFF000000.toInt()))
    }
}
