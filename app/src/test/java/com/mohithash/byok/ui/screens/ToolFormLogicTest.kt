package com.mohithash.byok.ui.screens

import com.mohithash.byok.engine.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolFormLogicTest {
    private val pantry = Field("pantry", "What's in your kitchen", "longtext", required = true)
    private val mood = Field("mood", "Mood", "chips", options = listOf("Quick", "Comfort", "Light"), default = "Quick")
    private val time = Field("time", "Max minutes", "number")
    private val photo = Field("photo", "Photo", "photo")
    private val diet = Field("diet", "Diet", "chips", options = listOf("Vegan", "Yes, a smaller amount", "Keto"), multi = true)
    private val spicy = Field("spicy", "Spicy", "toggle")

    @Test fun seededLaysValuesOverDefaultsAndDropsUnknownKeys() {
        val v = ToolFormLogic.seeded(listOf(pantry, mood, time), mapOf("pantry" to "eggs", "time" to "2o.5.1", "other" to "x"))
        assertEquals(mapOf("pantry" to "eggs", "mood" to "Quick", "time" to "2.51"), v)
        assertEquals(ToolFormLogic.defaults(listOf(pantry, mood)), ToolFormLogic.seeded(listOf(pantry, mood), null))
    }

    @Test fun seededKeepsDeliberateBlanksAndFixesStaleChoices() {
        val v = ToolFormLogic.seeded(listOf(mood, diet, spicy), mapOf("mood" to "", "diet" to "Vegan, Paleo, Yes, a smaller amount", "spicy" to "true"))
        assertEquals("", v["mood"])
        assertEquals("Vegan, Yes, a smaller amount", v["diet"])
        assertEquals("", v["spicy"])
        assertEquals("Quick", ToolFormLogic.seeded(listOf(mood), mapOf("mood" to "Gone"))["mood"])
    }

    @Test fun readinessNeedsRequiredFieldsAndAtLeastOneValue() {
        val fields = listOf(pantry, mood)
        assertFalse(ToolFormLogic.ready(fields, mapOf("mood" to "Quick"), hasPhoto = false))
        assertTrue(ToolFormLogic.ready(fields, mapOf("pantry" to "eggs"), hasPhoto = false))
        assertEquals(listOf(pantry), ToolFormLogic.missing(fields, mapOf("pantry" to "  "), hasPhoto = false))
        // Nothing required: something must still be filled in.
        assertFalse(ToolFormLogic.ready(listOf(time, photo), emptyMap(), hasPhoto = false))
        assertTrue(ToolFormLogic.ready(listOf(time, photo), emptyMap(), hasPhoto = true))
        assertTrue(ToolFormLogic.ready(emptyList(), emptyMap(), hasPhoto = false))
        val needPhoto = photo.copy(required = true)
        assertFalse(ToolFormLogic.ready(listOf(needPhoto, time), mapOf("time" to "3"), hasPhoto = false))
    }

    @Test fun dirtyWhenAnythingDiffersFromDefaults() {
        val fields = listOf(pantry, mood, photo)
        assertFalse(ToolFormLogic.isDirty(fields, ToolFormLogic.defaults(fields), hasPhoto = false))
        assertTrue(ToolFormLogic.isDirty(fields, ToolFormLogic.defaults(fields), hasPhoto = true))
        assertTrue(ToolFormLogic.isDirty(fields, ToolFormLogic.defaults(fields) + ("mood" to ""), hasPhoto = false))
    }

    @Test fun numbersKeepDigitsOneSeparatorAndLeadingMinus() {
        assertEquals("12.5", ToolFormLogic.sanitizeNumber("12.5"))
        assertEquals("12.55", ToolFormLogic.sanitizeNumber("12.5.5"))
        assertEquals("3,5", ToolFormLogic.sanitizeNumber("3,5"))
        assertEquals("35.1", ToolFormLogic.sanitizeNumber("3,5.1"))
        assertEquals("1250.50", ToolFormLogic.sanitizeNumber("1,250.50"))
        assertEquals("1250,5", ToolFormLogic.sanitizeNumber("1.250,5"))
        assertEquals("-4", ToolFormLogic.sanitizeNumber("-4"))
        assertEquals("-4", ToolFormLogic.sanitizeNumber("−4"))
        assertEquals("45", ToolFormLogic.sanitizeNumber("4-5"))
        assertEquals("30", ToolFormLogic.sanitizeNumber("30 min"))
        assertEquals("", ToolFormLogic.sanitizeNumber("abc"))
        assertEquals(".", ToolFormLogic.sanitizeNumber(".."))
    }

    @Test fun spokenTextIsAppendedWithOneSpace() {
        assertEquals("eggs", ToolFormLogic.appendSpoken("", " eggs "))
        assertEquals("rice eggs", ToolFormLogic.appendSpoken("rice", "eggs"))
        assertEquals("rice eggs", ToolFormLogic.appendSpoken("rice ", "eggs"))
        assertEquals("rice\neggs", ToolFormLogic.appendSpoken("rice\n", "eggs"))
        assertEquals("rice", ToolFormLogic.appendSpoken("rice", "  "))
    }

    @Test fun requiredLabelsGetAStar() {
        assertEquals("What's in your kitchen *", ToolFormLogic.label(pantry))
        assertEquals("Mood", ToolFormLogic.label(mood))
    }

    @Test fun chipSelectionUnderstandsOptionsContainingCommas() {
        assertEquals(listOf("Yes, a smaller amount", "Keto"), ToolFormLogic.chipSelection(diet, "Yes, a smaller amount, Keto"))
        assertEquals(listOf("Yes, a smaller amount"), ToolFormLogic.chipSelection(diet.copy(multi = false), "Yes, a smaller amount"))
        assertEquals(emptyList<String>(), ToolFormLogic.chipSelection(diet, " "))
    }

    @Test fun toggleChipAddsRemovesAndKeepsOptionOrder() {
        assertEquals("Vegan, Keto", ToolFormLogic.toggleChip(diet, "Keto", "Vegan"))
        assertEquals("Keto", ToolFormLogic.toggleChip(diet, "Vegan, Keto", "Vegan"))
        assertEquals("Vegan, Yes, a smaller amount", ToolFormLogic.toggleChip(diet, "Vegan", "Yes, a smaller amount"))
        assertEquals("Comfort", ToolFormLogic.toggleChip(mood, "Quick", "Comfort"))
        assertEquals("", ToolFormLogic.toggleChip(mood, "Quick", "Quick"))
    }

    @Test fun keyAndSettingsErrorsOfferSettings() {
        assertTrue(ToolFormLogic.pointsToSettings("Add your API key in Settings first."))
        assertTrue(ToolFormLogic.pointsToSettings("Your API key was rejected (invalid x-api-key). Check it in Settings."))
        assertTrue(ToolFormLogic.pointsToSettings("This key isn't allowed to do that (forbidden)."))
        assertFalse(ToolFormLogic.pointsToSettings("Network error: timeout"))
        assertFalse(ToolFormLogic.pointsToSettings("The model declined this request."))
    }
}
