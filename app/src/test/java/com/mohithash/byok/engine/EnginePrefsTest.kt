package com.mohithash.byok.engine

import com.mohithash.byok.ai.AiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnginePrefsTest {
    private val spec = AppSpec(id = "t", name = "T", tagline = "", category = "Tools", colors = listOf("#000000"), persona = "You are a tester.",
        onboarding = Onboarding("a", "b"), tools = emptyList())
    private val engine = Engine(AiClient(), spec)

    @Test fun defaultPrefsAddNothing() {
        assertEquals("", engine.prefsLines(Prefs()))
        assertFalse(engine.system(emptyMap()).contains("Length:"))
        assertEquals(engine.system(emptyMap()), engine.system(emptyMap(), Prefs()))
    }

    @Test fun languageDetailAndInstructionsAreInjected() {
        val s = engine.system(emptyMap(), Prefs(language = "Spanish", detail = "brief", instructions = "  Use metric units.  "))
        assertTrue(s.contains("Length: brief"))
        assertTrue(s.contains("in Spanish. Keep JSON keys and section kinds exactly as specified in English."))
        assertTrue(s.endsWith("(follow them unless unsafe or impossible): Use metric units."))
    }

    @Test fun englishNeedsNoLanguageLine() {
        assertFalse(engine.prefsLines(Prefs(language = "english")).contains("Write every"))
    }

    @Test fun instructionsAreCapped() {
        val long = "x".repeat(Prefs.MAX_INSTRUCTIONS + 500)
        assertEquals(Prefs.MAX_INSTRUCTIONS, engine.prefsLines(Prefs(instructions = long)).count { it == 'x' })
    }

    @Test fun followUpMessagesAlternateAndEndWithQuestion() {
        val turns = (1..6).map { Turn("ask $it", Doc(title = "Doc $it")) }
        val msgs = engine.followUpMessages(turns, "And then?")
        assertEquals(Engine.MAX_TURNS * 2 + 1, msgs.size)
        assertEquals("ask 3", msgs.first().text)
        msgs.forEachIndexed { i, m -> assertEquals(if (i % 2 == 0) "user" else "assistant", m.role) }
        assertEquals("And then?", msgs.last().text)
        assertTrue(msgs[1].text.startsWith("Doc 3"))
    }

    @Test fun followUpWithBlankAskStillHasUserTurn() {
        val msgs = engine.followUpMessages(listOf(Turn("", Doc(title = "D"))), "q")
        assertEquals("(earlier request)", msgs.first().text)
    }

    @Test fun usageStatsAccumulate() {
        val u = UsageStats().plus(10, 5, 1000).plus(1, 2, 2000)
        assertEquals(UsageStats(2, 11, 7, 1000), u)
    }
}
