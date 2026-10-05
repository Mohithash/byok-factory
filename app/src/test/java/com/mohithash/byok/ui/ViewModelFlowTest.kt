package com.mohithash.byok.ui

import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mohithash.byok.App
import com.mohithash.byok.ai.AiSettings
import com.mohithash.byok.engine.Prefs
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue

/** The real view-model + engine + Room, talking to a fake Claude endpoint. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ViewModelFlowTest {
    private lateinit var server: HttpServer
    private val bodies = ConcurrentLinkedQueue<String>()
    private val replies = ConcurrentLinkedQueue<Pair<Long, String>>()
    private lateinit var app: App
    private lateinit var vm: AppViewModel

    private fun docReply(title: String) = """{"model":"claude-opus-5-5","stop_reason":"end_turn","usage":{"input_tokens":100,"output_tokens":50},"content":[{"type":"text","text":${JsonPrimitive("""{"title":"$title","summary":"s","sections":[{"kind":"checklist","heading":"Do","text":"","items":["a","b"],"cards":[],"rows":[],"kv":[]}],"tags":["t"],"followups":["more?"]}""")}}]}"""

    @Before fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            bodies += ex.requestBody.readBytes().decodeToString()
            val (delay, body) = replies.poll() ?: (0L to """{"error":{"message":"none"}}""")
            if (delay > 0) Thread.sleep(delay)
            val b = body.toByteArray()
            runCatching { ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.use { it.write(b) } }
        }
        server.executor = java.util.concurrent.Executors.newCachedThreadPool()
        server.start()
        app = ApplicationProvider.getApplicationContext()
        runBlocking { app.db.results().deleteAll() }
        vm = AppViewModel(app)
        vm.saveAi(AiSettings(apiKey = "sk-test", baseUrl = "http://127.0.0.1:${server.address.port}"))
    }

    @After fun tearDown() { server.stop(0) }

    /** Lets viewModelScope (main looper) and Room/IO threads make progress until [done]. */
    private fun waitFor(timeoutMs: Long = 15_000, done: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!done()) {
            check(System.currentTimeMillis() < end) { "timed out; job=${vm.job.value}" }
            shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun simpleTool() = vm.spec.tools.first { t -> t.inputs.none { it.type == "photo" && it.required } }

    @Test fun runFollowUpRegenerateBackupRestore() = runBlocking {
        val tool = simpleTool()
        val inputs = tool.inputs.filter { it.type != "photo" }.associate { it.key to "x-${it.key}" }
        vm.savePrefs(Prefs(language = "French", detail = "brief"))

        replies += 0L to docReply("First")
        vm.run(tool, inputs, null)
        waitFor { vm.job.value is Job.Done || vm.job.value is Job.Failed }
        assertEquals(Job.Done(Unit), vm.job.value)
        val first = vm.current.value!!
        assertEquals("First", first.title)
        assertEquals(inputs, vm.inputs(first))
        val req1 = Json.parseToJsonElement(bodies.last()).jsonObject
        assertTrue(req1["system"]!!.jsonPrimitive.content.contains("in French."))
        assertTrue(req1["system"]!!.jsonPrimitive.content.contains("Length: brief"))
        assertEquals(1, vm.usage.value.requests); assertEquals(150L, vm.usage.value.inputTokens + vm.usage.value.outputTokens)

        replies += 0L to docReply("Second")
        vm.followUp("  Make it vegan  ")
        waitFor { vm.current.value?.title == "Second" || vm.job.value is Job.Failed }
        val second = vm.current.value!!
        assertEquals(first.id, second.rootId)
        assertEquals("Make it vegan", vm.question(second))
        val msgs = Json.parseToJsonElement(bodies.last()).jsonObject["messages"]!!.jsonArray
        assertEquals(listOf("user", "assistant", "user"), msgs.map { it.jsonObject["role"]!!.jsonPrimitive.content })
        assertTrue(msgs[1].toString().contains("First"))

        replies += 0L to docReply("Second again")
        vm.regenerate()
        waitFor { vm.current.value?.title == "Second again" || vm.job.value is Job.Failed }
        assertEquals(first.id, vm.current.value!!.rootId)
        assertEquals(3, app.db.results().threadOnce(first.id).size)

        vm.toggleTick(vm.current.value!!, "0:1"); waitFor { vm.current.value!!.ticks == "0:1" }
        val file = File(app.cacheDir, "backup.json")
        file.writeText(vm.backupJson())
        vm.deleteAll(); waitFor { runBlocking { app.db.results().allOnce().isEmpty() } }
        assertEquals(3, vm.importBackup(Uri.fromFile(file)))
        val restored = app.db.results().allOnce()
        val root = restored.single { !it.isFollowUp }
        assertEquals(2, restored.count { it.rootId == root.id })
        assertEquals("0:1", restored.single { it.title == "Second again" }.ticks)
        assertEquals(0, vm.importBackup(Uri.fromFile(file)))
    }

    @Test fun cancelStopsAndSavesNothing() = runBlocking {
        val tool = simpleTool()
        replies += 5_000L to docReply("Too late")
        vm.run(tool, tool.inputs.associate { it.key to "x" }, null)
        waitFor { bodies.isNotEmpty() }
        vm.cancel()
        assertEquals(Job.Idle, vm.job.value)
        Thread.sleep(300); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Job.Idle, vm.job.value)
        assertNull(vm.current.value)
        assertEquals(0, app.db.results().allOnce().size)
    }

    @Test fun deleteAndUndo() = runBlocking {
        val tool = simpleTool()
        replies += 0L to docReply("Keep me")
        vm.run(tool, tool.inputs.associate { it.key to "x" }, null)
        waitFor { vm.job.value is Job.Done || vm.job.value is Job.Failed }
        val r = vm.current.value!!
        vm.delete(r); waitFor { runBlocking { app.db.results().byId(r.id) == null } }
        assertTrue(vm.undoDelete())
        waitFor { runBlocking { app.db.results().byId(r.id) != null } }
        assertEquals(false, vm.undoDelete())
    }

    @Test fun sharedTextSeedsFirstTextField() {
        val tool = vm.spec.tools.first { t -> t.inputs.any { it.type == "longtext" || it.type == "text" } }
        vm.receive(Incoming(text = "hello from another app"))
        vm.useIncoming(tool)
        assertNull(vm.incoming.value)
        val seed = vm.consumeSeed()!!
        assertEquals(listOf("hello from another app"), seed.values.values.toList())
        assertNull(vm.consumeSeed())
        assertEquals(tool, vm.tool.value)
    }
}
