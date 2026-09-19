package com.mohithash.byok.engine

import com.mohithash.byok.ai.AiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineRenderTest {
    private val spec = AppSpec(
        id = "t", name = "T", tagline = "", category = "Tools", colors = listOf("#000000"), persona = "You are a tester.",
        onboarding = Onboarding("a", "b"), profile = listOf(Field("diet", "Diet")),
        tools = emptyList(),
    )
    private val engine = Engine(AiClient(), spec)
    private val photoTool = Tool(
        id = "scan", title = "Scan", prompt = "Identify the plant in {photo}. Notes: {notes}.",
        inputs = listOf(Field("photo", "Photo", type = "photo", required = true), Field("notes", "Notes")),
    )

    @Test fun photoPlaceholderRefersToAttachmentWhenImagePresent() {
        val p = engine.render(photoTool, mapOf("notes" to "yellow leaves"), emptyMap(), hasImage = true)
        assertEquals("Identify the plant in the attached photo. Notes: yellow leaves.", p)
        assertFalse(p.contains("(not given)"))
    }

    @Test fun photoPlaceholderSaysNoPhotoWhenImageAbsent() {
        val p = engine.render(photoTool, mapOf("notes" to ""), emptyMap(), hasImage = false)
        assertEquals("Identify the plant in (no photo attached). Notes: (not given).", p)
    }

    @Test fun photoFieldIsNeverAppendedAsOrphanText() {
        // A prompt that never mentions the photo at all (the documented convention) must not gain a "Photo:" line.
        val tool = Tool(id = "s", title = "S", prompt = "Describe what you see.", inputs = listOf(Field("photo", "Photo", type = "photo")))
        val p = engine.render(tool, mapOf("photo" to ""), emptyMap(), hasImage = true)
        assertEquals("Describe what you see.", p)
    }

    @Test fun unreferencedTextInputIsAppended() {
        val tool = Tool(id = "s", title = "S", prompt = "Plan a week.", inputs = listOf(Field("budget", "Budget")), shape = "table")
        val p = engine.render(tool, mapOf("budget" to "50"), emptyMap())
        assertTrue(p.startsWith("Plan a week.\n\nBudget: 50"))
        assertTrue(p.endsWith("Preferred sections: table"))
    }

    @Test fun profilePlaceholderUsesLabels() {
        val tool = Tool(id = "s", title = "S", prompt = "For {profile}.", inputs = emptyList())
        assertEquals("For Diet: vegan.", engine.render(tool, emptyMap(), mapOf("diet" to "vegan")))
        assertEquals("For not provided.", engine.render(tool, emptyMap(), emptyMap()))
    }
}
