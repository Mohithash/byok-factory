package com.mohithash.byok.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechChunksTest {
    private fun words(s: String) = s.split(Regex("\\s+")).filter { it.isNotEmpty() }

    @Test fun shortTextIsOneChunk() {
        assertEquals(listOf("Hello there. How are you?"), DocSpeaker.chunks("Hello there. How are you?", 4000))
        assertEquals(emptyList<String>(), DocSpeaker.chunks("   \n ", 4000))
    }

    @Test fun breaksAfterSentences() {
        val text = "One two three. Four five six! Seven eight nine? Ten."
        val parts = DocSpeaker.chunks(text, 20)
        assertEquals(listOf("One two three.", "Four five six!", "Seven eight nine?", "Ten."), parts)
    }

    @Test fun packsSentencesUpToTheLimit() {
        val text = (1..200).joinToString(" ") { "Sentence number $it is here." }
        val parts = DocSpeaker.chunks(text, 500)
        assertTrue(parts.all { it.length <= 500 })
        assertTrue(parts.size in 12..20)
        assertTrue(parts.all { it.endsWith(".") })
        assertEquals(words(text), parts.flatMap(::words))
    }

    @Test fun longSentencesSplitAtSpacesAndHugeWordsAnywhere() {
        val long = (1..100).joinToString(" ") { "word$it" }
        val parts = DocSpeaker.chunks(long, 50)
        assertTrue(parts.all { it.length <= 50 })
        assertEquals(words(long), parts.flatMap(::words))
        val huge = "x".repeat(130)
        val hp = DocSpeaker.chunks(huge, 50)
        assertEquals(listOf(50, 50, 30), hp.map { it.length })
    }

    @Test fun newlinesAreBreaks() {
        assertEquals(listOf("Title", "Line two"), DocSpeaker.chunks("Title\nLine two", 9))
    }

    @Test fun answerLanguageMapsToVoiceLocale() {
        org.junit.Assert.assertNull(ttsLocale(""))
        org.junit.Assert.assertNull(ttsLocale("English"))
        org.junit.Assert.assertEquals("es", ttsLocale("Spanish")!!.language)
        org.junit.Assert.assertEquals("zh-TW", ttsLocale("Chinese (Traditional)")!!.toLanguageTag())
        com.mohithash.byok.engine.Prefs.LANGUAGES.filter { it != "English" }.forEach { org.junit.Assert.assertNotNull(it, ttsLocale(it)) }
    }
}
