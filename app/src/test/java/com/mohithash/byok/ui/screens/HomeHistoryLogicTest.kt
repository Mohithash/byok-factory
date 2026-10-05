package com.mohithash.byok.ui.screens

import com.mohithash.byok.data.ResultRow
import com.mohithash.byok.engine.Field
import com.mohithash.byok.engine.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class HomeHistoryLogicTest {
    private val zone = ZoneId.of("Europe/London")
    /** Wednesday 14 October 2026. */
    private val today = LocalDate.of(2026, 10, 14)
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) = LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()
    private fun group(ms: Long, first: DayOfWeek = DayOfWeek.MONDAY) = historyDateGroup(ms, today, zone, first)

    private fun row(id: Long, created: Long, tool: String = "cook", title: String = "Result $id", fav: Boolean = false, note: String = "", summary: String = "", rootId: Long = 0) =
        ResultRow(id = id, toolId = tool, toolTitle = tool.replaceFirstChar { it.uppercase() }, emoji = "🍳", title = title, inputSummary = summary, json = "{}",
            favorite = fav, createdAt = created, note = note, rootId = rootId)

    /* ── date groups ── */

    @Test fun dateGroupsAroundToday() {
        assertEquals(HistoryDateGroup.TODAY, group(at(2026, 10, 14, 0, 0)))
        assertEquals(HistoryDateGroup.TODAY, group(at(2026, 10, 14, 23, 59)))
        assertEquals(HistoryDateGroup.TODAY, group(at(2026, 10, 20))) // future (clock skew) counts as today
        assertEquals(HistoryDateGroup.YESTERDAY, group(at(2026, 10, 13, 23, 59)))
        assertEquals(HistoryDateGroup.THIS_WEEK, group(at(2026, 10, 12))) // Monday, start of this week
        assertEquals(HistoryDateGroup.THIS_MONTH, group(at(2026, 10, 11))) // Sunday, last week
        assertEquals(HistoryDateGroup.THIS_MONTH, group(at(2026, 10, 1, 0, 0)))
        assertEquals(HistoryDateGroup.OLDER, group(at(2026, 9, 30, 23, 59)))
        assertEquals(HistoryDateGroup.OLDER, group(at(2025, 10, 14)))
    }

    @Test fun weekStartFollowsLocale() {
        // With Sunday-first weeks, Sunday 11 Oct is this week.
        assertEquals(HistoryDateGroup.THIS_WEEK, group(at(2026, 10, 11), DayOfWeek.SUNDAY))
        assertEquals(HistoryDateGroup.THIS_MONTH, group(at(2026, 10, 10), DayOfWeek.SUNDAY))
    }

    @Test fun weekSpanningMonthsSkipsThisMonth() {
        // Friday 2 Oct: the week began Monday 28 Sep, so September days in it are "This week", earlier ones "Older".
        val fri = LocalDate.of(2026, 10, 2)
        assertEquals(HistoryDateGroup.YESTERDAY, historyDateGroup(at(2026, 10, 1), fri, zone))
        assertEquals(HistoryDateGroup.THIS_WEEK, historyDateGroup(at(2026, 9, 28), fri, zone))
        assertEquals(HistoryDateGroup.OLDER, historyDateGroup(at(2026, 9, 27), fri, zone))
    }

    @Test fun dayBoundaryUsesTheGivenZone() {
        // 23:30 UTC on the 13th is 00:30 on the 14th in London (BST).
        val ms = LocalDateTime.of(2026, 10, 13, 23, 30).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals(HistoryDateGroup.TODAY, historyDateGroup(ms, today, zone))
        assertEquals(HistoryDateGroup.YESTERDAY, historyDateGroup(ms, today, ZoneId.of("UTC")))
    }

    @Test fun groupsAreMonotonicOverAMonthOfDays() {
        // Walking back day by day never returns to a newer bucket, so sorted lists get one header per bucket.
        for (start in 0..40L) {
            val d = today.minusDays(start)
            var last = HistoryDateGroup.TODAY
            for (back in 0..60L) {
                val g = historyDateGroup(at(d.minusDays(back).year, d.minusDays(back).monthValue, d.minusDays(back).dayOfMonth), d, zone)
                assertTrue("$d -$back: $g after $last", g.ordinal >= last.ordinal); last = g
            }
        }
    }

    @Test fun sectionsAreConsecutiveRuns() {
        val s = historySections(listOf(1, 2, 3, 10, 11, 20)) { it / 10 }
        assertEquals(listOf(0 to listOf(1, 2, 3), 1 to listOf(10, 11), 2 to listOf(20)), s)
        assertTrue(historySections(emptyList<Int>()) { it }.isEmpty())
    }

    @Test fun timeLabelDependsOnGroup() {
        val ms = at(2026, 10, 14, 15, 5)
        assertTrue(historyTimeLabel(ms, HistoryDateGroup.TODAY, today, zone, Locale.UK).contains("15:05"))
        assertTrue(historyTimeLabel(at(2026, 10, 12, 9, 30), HistoryDateGroup.THIS_WEEK, today, zone, Locale.UK).startsWith("Mon"))
        assertEquals("3 Oct", historyTimeLabel(at(2026, 10, 3), HistoryDateGroup.THIS_MONTH, today, zone, Locale.UK))
        assertEquals("3 Oct 2025", historyTimeLabel(at(2025, 10, 3), HistoryDateGroup.OLDER, today, zone, Locale.UK))
    }

    /* ── search, filter, sort ── */

    @Test fun queryWordsSplitAndLowercase() {
        assertEquals(listOf("pasta", "quick"), historyQueryWords("  Pasta\tQUICK  "))
        assertTrue(historyQueryWords("   ").isEmpty())
    }

    @Test fun everyWordMustMatchAcrossFieldsAndDocText() {
        val rows = listOf(
            row(1, at(2026, 10, 1), title = "Tomato pasta", note = "Kids loved it"),
            row(2, at(2026, 10, 2), title = "Fried rice", summary = "leftover rice, eggs"),
            row(3, at(2026, 10, 3), title = "Soup"),
        )
        val docs = mapOf(1L to "tomato pasta\nboil water", 2L to "fried rice\nwok", 3L to "soup\nuse leftover pasta")
        fun search(q: String) = historyFilter(rows, HISTORY_FILTER_ALL, historyQueryWords(q), true) { docs.getValue(it.id) }.map { it.id }
        assertEquals(listOf(3L, 1L), search("pasta"))          // title of 1, doc text of 3
        assertEquals(listOf(1L), search("pasta KIDS"))          // note
        assertEquals(listOf(2L), search("eggs rice"))           // input summary + title
        assertEquals(listOf(2L), search("wok"))                 // doc text only
        assertEquals(listOf(3L, 2L, 1L), search("cook"))       // tool title
        assertTrue(search("pasta wok").isEmpty())               // words must all match the same result
    }

    @Test fun docTextIsOnlyReadWhenSearching() {
        val rows = listOf(row(1, 1), row(2, 2))
        var reads = 0
        historyFilter(rows, HISTORY_FILTER_ALL, emptyList(), true) { reads++; "" }
        assertEquals(0, reads)
    }

    @Test fun filtersByFavouriteAndTool() {
        val rows = listOf(row(1, 1, tool = "cook", fav = true), row(2, 2, tool = "scan"), row(3, 3, tool = "cook"))
        assertEquals(listOf(1L), historyFilter(rows, HISTORY_FILTER_FAVOURITES, emptyList(), true) { "" }.map { it.id })
        assertEquals(listOf(3L, 1L), historyFilter(rows, "cook", emptyList(), true) { "" }.map { it.id })
        assertTrue(historyFilter(rows, "gone", emptyList(), true) { "" }.isEmpty())
    }

    @Test fun sortsByTimeIgnoringFavouriteFirstOrder() {
        // DAO order: favourites first, then newest.
        val rows = listOf(row(1, 100, fav = true), row(3, 300), row(2, 200), row(4, 200))
        assertEquals(listOf(3L, 4L, 2L, 1L), historyFilter(rows, HISTORY_FILTER_ALL, emptyList(), true) { "" }.map { it.id })
        assertEquals(listOf(1L, 2L, 4L, 3L), historyFilter(rows, HISTORY_FILTER_ALL, emptyList(), false) { "" }.map { it.id })
    }

    @Test fun toolChipsInSpecOrderThenRemovedTools() {
        val tools = listOf(Tool("scan", "Scan my fridge", emoji = "📷", prompt = "p"), Tool("cook", "What can I cook?", emoji = "🍳", prompt = "p"), Tool("plan", "Plan", prompt = "p"))
        val rows = listOf(row(1, 1, tool = "cook"), row(2, 2, tool = "old"), row(3, 3, tool = "scan"), row(4, 4, tool = "cook"))
        assertEquals(listOf(HistoryToolChip("scan", "📷", "Scan my fridge"), HistoryToolChip("cook", "🍳", "What can I cook?"), HistoryToolChip("old", "🍳", "Old")),
            historyToolChips(rows, tools))
        assertTrue(historyToolChips(emptyList(), tools).isEmpty())
    }

    @Test fun noMatchTextFollowsSearchAndFilter() {
        assertEquals("Nothing saved matches “soup”.", historyNoMatchText(" soup ", HISTORY_FILTER_ALL))
        assertEquals("Nothing saved matches “soup” with this filter.", historyNoMatchText("soup", "cook"))
        assertTrue(historyNoMatchText("", HISTORY_FILTER_FAVOURITES).startsWith("No favourites yet"))
        assertEquals("No saved results from this tool yet.", historyNoMatchText("", "cook"))
    }

    @Test fun countLabelPlural() {
        assertEquals("1 saved result", historyCountLabel(1))
        assertEquals("0 saved results", historyCountLabel(0))
        assertEquals("12 saved results", historyCountLabel(12))
    }

    /* ── home ── */

    private fun tool(vararg types: String) = Tool("t", "T", prompt = "p", inputs = types.mapIndexed { i, ty -> Field("k$i", "L$i", ty) })

    @Test fun sharedFitMirrorsUseIncoming() {
        assertEquals(HomeSharedFit.TEXT, homeSharedFit(tool("chips", "longtext"), hasText = true, hasPhoto = false))
        assertEquals(HomeSharedFit.TEXT, homeSharedFit(tool("text"), hasText = true, hasPhoto = true))
        assertEquals(HomeSharedFit.PHOTO, homeSharedFit(tool("photo", "number"), hasText = true, hasPhoto = true))
        assertEquals(HomeSharedFit.TEXT_AND_PHOTO, homeSharedFit(tool("photo", "text"), hasText = true, hasPhoto = true))
        assertEquals(HomeSharedFit.NONE, homeSharedFit(tool("photo"), hasText = true, hasPhoto = false))
        assertEquals(HomeSharedFit.NONE, homeSharedFit(tool(), hasText = true, hasPhoto = true))
        assertEquals(HomeSharedFit.NONE, homeSharedFit(tool("number", "chips", "toggle"), hasText = true, hasPhoto = false))
        assertFalse(HomeSharedFit.NONE.receives); assertTrue(HomeSharedFit.PHOTO.receives)
    }

    @Test fun sharedPreviewCollapsesAndTruncates() {
        assertEquals("Hello world", homeSharedPreview("  Hello\n\n  world \t"))
        assertEquals("a".repeat(160), homeSharedPreview("a".repeat(160)))
        assertEquals("a".repeat(160) + "…", homeSharedPreview("a".repeat(161)))
        assertEquals("abc…", homeSharedPreview("abc defgh", max = 4))
        // Never cut an emoji (surrogate pair) in half.
        val p = homeSharedPreview("abc😀def", max = 4)
        assertEquals("abc…", p)
    }

    @Test fun checklistBadgeOnlyWithChecklists() {
        assertNull(homeChecklistBadge(0 to 0))
        assertEquals("2/5 ✓", homeChecklistBadge(2 to 5))
    }

    @Test fun followUpsAreMarked() {
        assertEquals("Soup", homeRowTitle(row(1, 1, title = "Soup")))
        assertEquals("↳ Soup", homeRowTitle(row(2, 1, title = "Soup", rootId = 1)))
    }

    /* ── onboarding ── */

    @Test fun languagePrefIsBlankForEnglish() {
        assertEquals("", onboardingLanguagePref("English"))
        assertEquals("", onboardingLanguagePref(" "))
        assertEquals("Spanish", onboardingLanguagePref("Spanish"))
    }

    @Test fun requiredProfileFieldsBlockUntilFilled() {
        val fields = listOf(Field("name", "Name"), Field("child", "Child's age", required = true), Field("pic", "Photo", "photo", required = true))
        assertEquals(listOf("child"), onboardingMissing(fields, mapOf("name" to "Sam", "child" to " ")).map { it.key })
        assertTrue(onboardingMissing(fields, mapOf("child" to "3")).isEmpty())
    }
}
