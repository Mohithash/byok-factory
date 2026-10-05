package com.mohithash.byok.ai

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/** Drives [AiClient] against a local HTTP server: request shape, parsing, retries, errors and cancellation. */
class AiClientHttpTest {
    private data class Seen(val path: String, val headers: Map<String, String>, val body: String)
    private data class Reply(val code: Int, val body: String, val headers: Map<String, String> = emptyMap(), val delayMs: Long = 0)

    private lateinit var server: HttpServer
    private val seen = ConcurrentLinkedQueue<Seen>()
    private val replies = ConcurrentLinkedQueue<Reply>()
    private val hits = AtomicInteger()
    private val client = AiClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val schema = Schema.obj("title" to Schema.str)

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            hits.incrementAndGet()
            val body = ex.requestBody.readBytes().decodeToString()
            seen += Seen(ex.requestURI.toString(), ex.requestHeaders.entries.associate { it.key.lowercase() to it.value.joinToString(",") }, body)
            val r = replies.poll() ?: Reply(500, """{"error":{"message":"no canned reply"}}""")
            if (r.delayMs > 0) Thread.sleep(r.delayMs)
            r.headers.forEach { (k, v) -> ex.responseHeaders.add(k, v) }
            val bytes = r.body.toByteArray()
            runCatching { ex.sendResponseHeaders(r.code, bytes.size.toLong()); ex.responseBody.use { it.write(bytes) } }
        }
        server.executor = java.util.concurrent.Executors.newCachedThreadPool()
        server.start()
    }

    @After fun stop() = server.stop(0)

    private val url get() = "http://127.0.0.1:${server.address.port}"
    private fun claude(model: String = "") = AiSettings(AiProvider.ANTHROPIC, "sk-test", model, url)
    private fun anthropicOk(text: String, stop: String = "end_turn") =
        """{"model":"claude-opus-5-5","stop_reason":"$stop","content":[{"type":"text","text":${kotlinx.serialization.json.JsonPrimitive(text)}}],"usage":{"input_tokens":12,"output_tokens":7,"cache_read_input_tokens":3}}"""
    private fun body(i: Int = 0): JsonObject = json.parseToJsonElement(seen.toList()[i].body).jsonObject

    @Test fun anthropicRequestShapeAndUsage() = runBlocking {
        replies += Reply(200, anthropicOk("""{"title":"Hi"}"""))
        var usage: AiUsage? = null
        client.onUsage = { usage = it }
        val r = client.chatFull(claude(), "sys", listOf(ChatMsg("user", "hello", "AAAA")), schema, 16000)
        assertEquals("""{"title":"Hi"}""", r.text)
        assertEquals(AiUsage(15, 7), usage)
        val s = seen.single()
        assertEquals("/v1/messages", s.path)
        assertEquals("sk-test", s.headers["x-api-key"])
        assertEquals("2023-06-01", s.headers["anthropic-version"])
        assertNull("custom base URL → no fallback beta", s.headers["anthropic-beta"])
        val b = body()
        assertEquals("claude-opus-5-5", b["model"]!!.jsonPrimitive.content)
        assertEquals("16000", b["max_tokens"]!!.jsonPrimitive.content)
        assertEquals("low", b["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertEquals("json_schema", b["output_config"]!!.jsonObject["format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertFalse(b.containsKey("fallbacks"))
        val content = b["messages"].toString()
        assertTrue(content.contains("\"type\":\"image\"") && content.contains("\"media_type\":\"image/jpeg\""))
    }

    @Test fun haikuGetsNoEffort() = runBlocking {
        replies += Reply(200, anthropicOk("ok"))
        client.chat(claude("claude-haiku-4-5"), "sys", listOf(ChatMsg("user", "x")))
        assertFalse(body().containsKey("output_config"))
    }

    @Test fun effortRejectionIsRememberedAndRetried() = runBlocking {
        replies += Reply(400, """{"error":{"type":"invalid_request_error","message":"output_config.effort: not supported on this model"}}""")
        replies += Reply(200, anthropicOk("ok"))
        replies += Reply(200, anthropicOk("again"))
        assertEquals("ok", client.chat(claude("claude-future-1"), "sys", listOf(ChatMsg("user", "x")), schema))
        assertFalse(body(1)["output_config"]!!.jsonObject.containsKey("effort"))
        client.chat(claude("claude-future-1"), "sys", listOf(ChatMsg("user", "x")), schema)
        assertFalse(body(2)["output_config"]!!.jsonObject.containsKey("effort"))
    }

    @Test fun overloadedThenOkIsRetried() = runBlocking {
        replies += Reply(529, """{"error":{"type":"overloaded_error","message":"Overloaded"}}""", mapOf("retry-after" to "0"))
        replies += Reply(429, """{"error":{"type":"rate_limit_error","message":"slow down"}}""", mapOf("retry-after" to "0"))
        replies += Reply(200, anthropicOk("fine"))
        assertEquals("fine", client.chat(claude(), "sys", listOf(ChatMsg("user", "x"))))
        assertEquals(3, hits.get())
    }

    @Test fun retriesGiveUpAfterThreeAttempts() = runBlocking {
        repeat(5) { replies += Reply(503, """{"error":{"message":"down"}}""", mapOf("retry-after" to "0")) }
        try { client.chat(claude(), "sys", listOf(ChatMsg("user", "x"))); fail() } catch (e: AiClient.AiException) {
            assertEquals(503, e.status); assertTrue(e.message!!.contains("Provider error 503"))
        }
        assertEquals(3, hits.get())
    }

    @Test fun badKeyIsNotRetriedAndExplained() = runBlocking {
        replies += Reply(401, """{"error":{"type":"authentication_error","message":"invalid x-api-key"}}""")
        try { client.chat(claude(), "sys", listOf(ChatMsg("user", "x"))); fail() } catch (e: AiClient.AiException) {
            assertTrue(e.message!!.contains("API key was rejected")); assertTrue(e.message!!.contains("invalid x-api-key"))
        }
        assertEquals(1, hits.get())
    }

    @Test fun refusalAndTruncationAreReported() = runBlocking {
        replies += Reply(200, """{"stop_reason":"refusal","stop_details":{"type":"refusal","category":"cyber","explanation":"Not this one."},"content":[]}""")
        try { client.chat(claude(), "sys", listOf(ChatMsg("user", "x")), schema); fail() } catch (e: AiClient.AiException) {
            assertEquals("The model declined this request. Not this one.", e.message)
        }
        replies += Reply(200, anthropicOk("""{"title":"cut""", stop = "max_tokens"))
        try { client.chat(claude(), "sys", listOf(ChatMsg("user", "x")), schema); fail() } catch (e: AiClient.AiException) {
            assertEquals(AiClient.TOO_LONG, e.message)
        }
    }

    @Test fun openAiCompatibleShapeCapsMaxTokens() = runBlocking {
        replies += Reply(200, """{"model":"gpt-4o-mini","choices":[{"finish_reason":"stop","message":{"content":"{\"title\":\"x\"}"}}],"usage":{"prompt_tokens":4,"completion_tokens":2}}""")
        var usage: AiUsage? = null
        client.onUsage = { usage = it }
        val s = AiSettings(AiProvider.OPENAI_COMPAT, "k", "", url)
        assertEquals("""{"title":"x"}""", client.chat(s, "sys", listOf(ChatMsg("user", "x")), schema, 16000))
        assertEquals(AiUsage(4, 2), usage)
        assertEquals("/v1/chat/completions", seen.single().path)
        assertEquals("Bearer k", seen.single().headers["authorization"])
        assertEquals("8000", body()["max_tokens"]!!.jsonPrimitive.content)
        assertEquals("json_object", body()["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test fun listModelsReadsIds() = runBlocking {
        replies += Reply(200, """{"data":[{"id":"claude-opus-5-5","display_name":"Claude Opus 5.5"},{"id":"claude-haiku-4-5"}],"has_more":false}""")
        assertEquals(listOf("claude-opus-5-5", "claude-haiku-4-5"), client.listModels(claude()))
        assertTrue(seen.single().path.startsWith("/v1/models"))
    }

    @Test fun missingKeyFailsFast() = runBlocking {
        try { client.chat(AiSettings(baseUrl = url), "s", listOf(ChatMsg("user", "x"))); fail() } catch (e: AiClient.AiException) {
            assertTrue(e.message!!.contains("API key"))
        }
        assertEquals(0, hits.get())
    }

    @Test fun cancellingAbortsTheRequestPromptly() = runBlocking {
        replies += Reply(200, anthropicOk("late"), delayMs = 10_000)
        val started = System.currentTimeMillis()
        val call = async { client.chat(claude(), "sys", listOf(ChatMsg("user", "x"))) }
        delay(300)
        call.cancel()
        try { call.await(); fail("should be cancelled") } catch (_: CancellationException) {}
        assertTrue("cancel took ${System.currentTimeMillis() - started}ms", System.currentTimeMillis() - started < 3_000)
    }

    @Test fun usageIsCountedForBilledFailures() = runBlocking {
        var usage: AiUsage? = null
        client.onUsage = { usage = it }
        replies += Reply(200, """{"stop_reason":"refusal","content":[],"usage":{"input_tokens":30,"output_tokens":4}}""")
        try { client.chat(claude(), "sys", listOf(ChatMsg("user", "x")), schema); fail() } catch (_: AiClient.AiException) {}
        assertEquals(AiUsage(30, 4), usage)
    }

    @Test fun slowAnswerTimesOutWithoutResending() = runBlocking {
        val impatient = AiClient(generationTimeoutMs = 400)
        replies += Reply(200, anthropicOk("late"), delayMs = 2_000)
        replies += Reply(200, anthropicOk("would be a second, billed attempt"))
        try { impatient.chat(claude(), "sys", listOf(ChatMsg("user", "x"))); fail() } catch (e: AiClient.AiException) {
            assertTrue(e.message!!.contains("took too long"))
        }
        assertEquals(1, hits.get())
    }

    @Test fun gatewayAskingForMaxCompletionTokensIsRetriedOnce() = runBlocking {
        replies += Reply(400, """{"error":{"message":"Unsupported parameter: 'max_tokens' is not supported with this model. Use 'max_completion_tokens' instead."}}""")
        replies += Reply(200, """{"choices":[{"finish_reason":"stop","message":{"content":"ok"}}]}""")
        val s = AiSettings(AiProvider.OPENAI_COMPAT, "k", "o4-mini", url)
        assertEquals("ok", client.chat(s, "sys", listOf(ChatMsg("user", "x"))))
        assertFalse(body(1).containsKey("max_tokens"))
        assertEquals("4096", body(1)["max_completion_tokens"]!!.jsonPrimitive.content)
    }

    @Test fun baseUrlRoots() {
        fun root(u: String) = AiSettings(AiProvider.OPENAI_COMPAT, "k", "", u).openAiRoot
        assertEquals("https://api.openai.com/v1", root(""))
        assertEquals("https://openrouter.ai/api/v1", root("https://openrouter.ai/api/v1"))
        assertEquals("https://openrouter.ai/api/v1", root("https://openrouter.ai/api/v1/"))
        assertEquals("https://api.groq.com/openai/v1", root("https://api.groq.com/openai"))
        assertEquals("http://localhost:11434/v1", root("http://localhost:11434"))
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai", root("https://generativelanguage.googleapis.com/v1beta/openai/"))
        assertEquals("https://api.anthropic.com", AiSettings(baseUrl = "https://api.anthropic.com/v1").effectiveBaseUrl)
        assertTrue(AiSettings(AiProvider.OPENAI_COMPAT).isOpenAi)
        assertFalse(AiSettings(AiProvider.OPENAI_COMPAT, baseUrl = "https://api.groq.com/openai/v1").isOpenAi)
    }

    @Test fun defaultModelIsCurrentOpus() {
        assertEquals("claude-opus-5-5", AiSettings().effectiveModel)
        assertEquals("https://api.anthropic.com", AiSettings(baseUrl = "https://api.anthropic.com/").effectiveBaseUrl)
    }
}
