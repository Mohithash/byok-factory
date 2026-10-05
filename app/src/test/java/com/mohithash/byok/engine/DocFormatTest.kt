package com.mohithash.byok.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocFormatTest {
    private val doc = Doc(
        title = "Plan", summary = "A <quick> plan.",
        sections = listOf(
            Section(kind = "steps", heading = "Do", items = listOf("Boil", "Eat")),
            Section(kind = "checklist", heading = "Buy", items = listOf("Eggs", "Rice")),
            Section(kind = "table", heading = "Costs", rows = listOf(listOf("Item", "£"), listOf("Eggs|large", "2"))),
            Section(kind = "callout", text = "Hot pan."),
        ),
        tags = listOf("quick meals"),
    )

    @Test fun plainTextMarksTickedChecklistItems() {
        val t = doc.toPlainText(setOf("1:0"))
        assertTrue(t.contains("1. Boil\n2. Eat"))
        assertTrue(t.contains("☑ Eggs\n☐ Rice"))
        assertTrue(t.contains("#quickmeals"))
    }

    @Test fun markdownHasTableHeaderAndEscapedPipes() {
        val md = doc.toMarkdown(setOf("1:1"))
        assertTrue(md.startsWith("# Plan\n\nA <quick> plan.\n"))
        assertTrue(md.contains("| Item | £ |\n| --- | --- |\n| Eggs\\|large | 2 |"))
        assertTrue(md.contains("- [ ] Eggs\n- [x] Rice"))
        assertTrue(md.contains("> **Note:** Hot pan."))
    }

    @Test fun calloutLabelSurvivesMultiLineAndItemCallouts() {
        val d = Doc(title = "T", sections = listOf(Section(kind = "callout", text = "First\nSecond"), Section(kind = "callout", items = listOf("a", "b"))))
        val md = d.toMarkdown()
        assertTrue(md.contains("> **Note:** First\n> Second"))
        assertTrue(md.contains("> **Note:** a b"))
    }

    @Test fun exportsKeepFieldsOutsideTheKind() {
        val d = Doc(title = "T", sections = listOf(
            Section(kind = "table", heading = "Mix", text = "Intro", items = listOf("loose item"), rows = listOf(listOf("A"), listOf("1")), kv = listOf(KV("k", "v"))),
            Section(kind = "steps", items = listOf("go"), cards = listOf(Card("Card", "body"))),
            Section(kind = "callout", text = "Careful", items = listOf("extra")),
        ))
        val md = d.toMarkdown(); val html = d.toHtml("App")
        listOf("Intro", "loose item", "| A |", "**k:** v", "**Card**", "> **Note:** Careful", "- extra").forEach { assertTrue(it, md.contains(it)) }
        listOf("<p>Intro</p>", "<li>loose item</li>", "<th>A</th>", "<th>k</th><td>v</td>", "<strong>Card</strong>", "<div class=\"note\">Careful</div>", "<li>extra</li>").forEach { assertTrue(it, html.contains(it)) }
    }

    @Test fun htmlEscapesUserText() {
        val html = doc.toHtml("Pantry <Pal>", "#D9481F")
        assertTrue(html.contains("A &lt;quick&gt; plan."))
        assertTrue(html.contains("Pantry &lt;Pal&gt;"))
        assertFalse(html.contains("<quick>"))
        assertTrue(html.contains("<th>Item</th>"))
    }

    @Test fun speechReadsStepsInOrder() {
        val s = doc.toSpeech()
        assertTrue(s.startsWith("Plan. A <quick> plan."))
        assertTrue(s.contains("Step 1. Boil. Step 2. Eat."))
        assertFalse(s.contains(" | "))
        assertTrue(s.contains("Eggs|large, 2."))
    }

    @Test fun checklistProgressCountsOnlyChecklists() {
        assertEquals(1 to 2, doc.checklistProgress(setOf("1:0", "0:0")))
        assertEquals(0 to 0, Doc().checklistProgress(emptySet()))
    }
}
