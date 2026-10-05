package com.mohithash.byok.ui.screens

import com.mohithash.byok.ai.AiProvider
import com.mohithash.byok.ai.AiSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AiSettingsCardTest {
    private val claude = AiProvider.ANTHROPIC
    private val oai = AiProvider.OPENAI_COMPAT

    @Test fun normalizeTrimsAndCleansBaseUrl() {
        val s = normalizedAiSettings(AiSettings(oai, "  sk-1 \n", " gpt-4o ", " https://api.groq.com/openai/v1/ "))
        assertEquals(AiSettings(oai, "sk-1", "gpt-4o", "https://api.groq.com/openai"), s)
        assertEquals("http://10.0.0.2:11434", normalizedAiSettings(AiSettings(oai, baseUrl = "http://10.0.0.2:11434/V1")).baseUrl)
        assertEquals("https://proxy.example.com/anthropic", normalizedAiSettings(AiSettings(claude, baseUrl = "https://proxy.example.com/anthropic//")).baseUrl)
    }

    @Test fun normalizeBlanksTheProviderDefaultBaseUrl() {
        assertEquals("", normalizedAiSettings(AiSettings(claude, baseUrl = "https://api.anthropic.com/")).baseUrl)
        assertEquals("", normalizedAiSettings(AiSettings(oai, baseUrl = "https://api.openai.com/v1")).baseUrl)
        // The other provider's default is a real choice, not a default.
        assertEquals("https://api.openai.com", normalizedAiSettings(AiSettings(claude, baseUrl = "https://api.openai.com")).baseUrl)
    }

    @Test fun switchingProviderKeepsKeyAndCustomValues() {
        val custom = AiSettings(claude, "key", "my-finetune", "https://gateway.example.com")
        assertEquals(AiSettings(oai, "key", "my-finetune", "https://gateway.example.com"), switchAiProvider(custom, oai))
    }

    @Test fun switchingProviderClearsTheOldDefaults() {
        assertEquals(AiSettings(oai, "key", "", ""), switchAiProvider(AiSettings(claude, "key", claude.defaultModel, claude.defaultBaseUrl + "/"), oai))
        assertEquals(AiSettings(oai, "key", "", ""), switchAiProvider(AiSettings(claude, "key", claude.suggestedModels.last(), ""), oai))
        assertEquals(AiSettings(claude, "key", "", ""), switchAiProvider(AiSettings(oai, "key", "gpt-4o", "https://api.openai.com/v1"), claude))
    }

    @Test fun switchingToTheSameProviderIsANoOp() {
        val s = AiSettings(claude, "k", claude.defaultModel)
        assertSame(s, switchAiProvider(s, claude))
    }

    @Test fun modelOptionsNarrowOnlyWhileTyping() {
        val all = listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-4-5")
        assertEquals(all, aiModelOptions(all, ""))
        assertEquals(all, aiModelOptions(all, "claude-sonnet-5-5"))
        assertEquals(listOf("claude-sonnet-5-5"), aiModelOptions(all, "SONNET"))
        assertEquals(emptyList<String>(), aiModelOptions(all, "gpt"))
    }

    @Test fun chatModelsDropNonChatModelsAndDuplicates() {
        val ids = listOf("text-embedding-3-small", "gpt-4o", " gpt-4o ", "whisper-1", "tts-1", "dall-e-3", "GPT-4o-mini", "", "omni-moderation-latest")
        assertEquals(listOf("gpt-4o", "GPT-4o-mini"), aiChatModels(ids, oai))
        // Anthropic order (newest first) is kept.
        assertEquals(listOf("claude-z", "claude-a"), aiChatModels(listOf("claude-z", "claude-a"), claude))
        // If everything looks like a non-chat model, show it all rather than nothing.
        assertEquals(listOf("embed-a"), aiChatModels(listOf("embed-a"), oai))
    }

    @Test fun keyHints() {
        assertNull(aiKeyHint(AiSettings(claude, "")))
        assertNull(aiKeyHint(AiSettings(claude, "sk-ant-api03-abc")))
        assertNotNull(aiKeyHint(AiSettings(claude, "sk-proj-abc")))
        assertNull(aiKeyHint(AiSettings(claude, "anything", baseUrl = "https://gateway.example.com")))
        assertNotNull(aiKeyHint(AiSettings(oai, "sk-ant-api03-abc")))
        assertNull(aiKeyHint(AiSettings(oai, "gsk_abc")))
        assertNotNull(aiKeyHint(AiSettings(oai, "sk-ab c")))
    }

    @Test fun baseUrlValidation() {
        assertNull(aiBaseUrlError(""))
        assertNull(aiBaseUrlError(" https://openrouter.ai/api "))
        assertNull(aiBaseUrlError("http://192.168.1.5:1234"))
        assertNotNull(aiBaseUrlError("openrouter.ai/api"))
        assertNotNull(aiBaseUrlError("ftp://example.com"))
        assertNotNull(aiBaseUrlError("https://"))
        assertNotNull(aiBaseUrlError("https://exa mple.com"))
    }

    @Test fun modelCountWording() {
        assertEquals("1 model", aiModelCount(1))
        assertEquals("12 models", aiModelCount(12))
    }
}
